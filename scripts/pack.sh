#!/usr/bin/env bash
# Rebuild and pack DotKtX.Compose.UI.Annotations and DotKtX.Compose.UI.WinUI into artifacts/packages.
# The WinUI dependency DotKtX.AndroidX.Compose.Runtime is packed by ../dotktx.compose/scripts/pack.sh.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$REPO"
OUT="$REPO/artifacts/packages"
rm -f "$OUT"/DotKtX.Compose.UI.*.nupkg
for project in \
  "$REPO/annotations/src/DotKtX.Compose.UI.Annotations/DotKtX.Compose.UI.Annotations.ktproj" \
  "$REPO/winui/src/DotKtX.Compose.UI.WinUI/DotKtX.Compose.UI.WinUI.ktproj"; do
  # Rebuild so that a package never carries a stale assembly. PRI generation is a Windows-only step.
  dotnet build "$project" --configuration Release -t:Rebuild -m:1 -p:AppxGeneratePriEnabled=false "$@"
  dotnet pack "$project" --configuration Release --no-build --no-restore -m:1 -p:AppxGeneratePriEnabled=false -o "$OUT"
done
ls "$OUT"/DotKtX.Compose.UI.*.nupkg
