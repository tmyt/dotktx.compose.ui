#!/usr/bin/env bash
# Compile DotKtX.Compose.UI.AppKit, its samples and the smoke test (the Compose runtime is a separate DLL
# from ../dotktx.compose). Requires macOS with the `macos` workload of the .NET SDK on PATH.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
mkdir -p "$REPO/artifacts/logs"
for project in samples/counter:Counter samples/gallery:Gallery tests/smoke:Smoke; do
  dir="${project%%:*}"
  name="${project##*:}"
  log="$REPO/artifacts/logs/appkit-$(basename "$dir").build.log"
  if dotnet build "$REPO/appkit/$dir/$name.ktproj" -c Release --nologo -m:1 "$@" >"$log" 2>&1; then
    echo "BUILT appkit/$dir"
  else
    grep -E "error|Error" "$log" | sort -u | head -60
    echo "FAILED appkit/$dir (full log: $log)"
    exit 1
  fi
done
