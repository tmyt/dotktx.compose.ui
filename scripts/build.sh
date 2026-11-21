#!/usr/bin/env bash
# Compile DotKtX.Compose.UI.WinUI and the samples (the Compose runtime is a separate DLL from ../dotktx.compose).
# Compiling works on Linux/WSL; running the samples needs Windows.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
mkdir -p "$REPO/artifacts/logs"
for sample in counter:Counter gallery:Gallery shell:Shell interop:Interop pickers:Pickers inputs:Inputs commands:Commands; do
  dir="${sample%%:*}"
  name="${sample##*:}"
  log="$REPO/artifacts/logs/$dir.build.log"
  # PRI generation is a Windows-only step; the Windows App SDK task fails on Linux paths.
  if dotnet build "$REPO/winui/samples/$dir/$name.ktproj" -c Release --nologo -m:1 -p:AppxGeneratePriEnabled=false "$@" >"$log" 2>&1; then
    echo "BUILT $dir"
  else
    grep -E "error|Error" "$log" | sort -u | head -60
    echo "FAILED $dir (full log: $log)"
    exit 1
  fi
done
