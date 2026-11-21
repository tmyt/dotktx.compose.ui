#!/usr/bin/env bash
# Build the counter sample against the packages (winui/tests/package-smoke) and run its UI Automation
# test on Windows. Pack first: ../dotktx.compose/scripts/pack.sh, then scripts/pack.sh.
# DotKtX.Compose.UI.* and DotKtX.AndroidX.* are restored into a fresh cache, so an older copy of the
# same version is never tested; other packages are reused read-only from the global cache.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$REPO"
CACHE="$(mktemp -d "${TMPDIR:-/tmp}/dotktx-compose-ui-packages.XXXXXX")"
trap 'rm -rf "${CACHE:?}"' EXIT
mkdir -p "$CACHE/fallback" "$CACHE/packages"
GLOBAL_PACKAGES="$(DOTNET_CLI_UI_LANGUAGE=en dotnet nuget locals global-packages --list | sed -n 's/^global-packages: //p')"
for directory in "$GLOBAL_PACKAGES"/*; do
  name="$(basename "$directory")"
  case "$name" in dotktx.compose.ui.*|dotktx.androidx.*) continue ;; esac
  ln -s "$directory" "$CACHE/fallback/$name"
done
export NUGET_HTTP_CACHE_PATH="$CACHE/http"
export RestorePackagesPath="$CACHE/packages"
export RestoreFallbackFolders="$CACHE/fallback"
project="$REPO/winui/tests/package-smoke/Counter.ktproj"
mkdir -p "$REPO/artifacts/logs"
log="$REPO/artifacts/logs/package-smoke.build.log"
# PRI generation is a Windows-only step; the Windows App SDK task fails on Linux paths.
if ! dotnet build "$project" --configuration Release --nologo -t:Rebuild -p:AppxGeneratePriEnabled=false >"$log" 2>&1; then
  grep -E "error|warning NU" "$log" | sort -u | head -40; echo "FAILED (full log: $log)"; exit 1
fi
echo "BUILT package-smoke"
bash "$REPO/scripts/uia.sh" counter "$REPO/artifacts/bin/winui/tests/package-smoke/Release/net10.0-windows10.0.19041.0/win-x64"
echo "PASS package-smoke"
