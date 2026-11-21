#!/usr/bin/env bash
# Rebuild and pack DotKtX.Compose.UI.WinUI into artifacts/packages. Its dependency
# DotKtX.AndroidX.Compose.Runtime is packed by ../dotktx.compose/scripts/pack.sh.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$REPO"
OUT="$REPO/artifacts/packages"
project="$REPO/winui/src/DotKtX.Compose.UI.WinUI/DotKtX.Compose.UI.WinUI.ktproj"
rm -f "$OUT"/DotKtX.Compose.UI.*.nupkg
# Rebuild so that the package never carries a stale assembly. PRI generation is a Windows-only step.
dotnet build "$project" --configuration Release -t:Rebuild -m:1 -p:AppxGeneratePriEnabled=false "$@"
dotnet pack "$project" --configuration Release --no-build --no-restore -m:1 -p:AppxGeneratePriEnabled=false -o "$OUT"
ls "$OUT"/DotKtX.Compose.UI.*.nupkg
