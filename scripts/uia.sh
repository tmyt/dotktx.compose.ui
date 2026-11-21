#!/usr/bin/env bash
# Run a built sample on Windows from WSL and drive it with its UI Automation test.
# Usage: scripts/uia.sh counter|gallery|shell|interop [dir]   (build first with scripts/build.sh)
# [dir] is the directory of the sample's .exe; it defaults to the scripts/build.sh output.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
sample="${1:?usage: scripts/uia.sh counter|gallery|shell|interop}"
case "$sample" in
  counter) name=Counter ;;
  gallery) name=Gallery ;;
  shell) name=Shell ;;
  interop) name=Interop ;;
  pickers) name=Pickers ;;
  inputs) name=Inputs ;;
  *) echo "unknown sample: $sample" >&2; exit 2 ;;
esac
out="${2:-$REPO/artifacts/bin/winui/samples/$sample/Release/net10.0-windows10.0.19041.0/win-x64}"
log="$REPO/artifacts/logs/$sample.run.log"
mkdir -p "$REPO/artifacts/logs"
# Start the apphost, not `dotnet.exe <name>.dll`: only the apphost carries the application manifest (DPI awareness).
(cd "$out" && timeout 180 "./$name.exe" >"$log" 2>&1; echo "exit code $?" >>"$log") &
app=$!
status=0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(wslpath -w "$REPO/winui/tests/uia/$sample.ps1")" || status=$?
wait "$app"
echo "--- $sample output ($log)"
cat "$log"
grep -q '^exit code 0$' "$log" || status=1
exit "$status"
