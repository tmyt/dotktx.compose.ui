#!/usr/bin/env bash
# Build the WinUI analyzers (a K2 compiler plugin jar) with the Kotlin compiler that DotKt's kotc embeds,
# so the plugin always matches the compiler that loads it.
# Output: artifacts/winui.analyzers/DotKtX.Compose.UI.WinUI.Analyzers.jar
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
SDK_VERSION="$(sed -n 's/.*"DotKt.Sdk": *"\([^"]*\)".*/\1/p' "$REPO/global.json")"
LIB="$HOME/.nuget/packages/dotkt.toolchain/$SDK_VERSION/tools/kotc/lib"
COMPILER="$(ls "$LIB"/kotlin-compiler-embeddable-*.jar)"
STDLIB="$(ls "$LIB"/kotlin-stdlib-[0-9]*.jar)"
SRC="$REPO/winui.analyzers/src/main"
OUT="$REPO/artifacts/winui.analyzers"
rm -rf "${OUT:?}"
mkdir -p "$OUT/classes"
java -cp "$LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 \
  -opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi \
  -cp "$COMPILER:$STDLIB" -d "$OUT/classes" "$SRC/kotlin"
# The Kotlin LSP loads a compiler plugin that is not bundled with it only when the manifest declares
# the IDE builds it is compatible with.
printf 'Kotlin-Compiler-Plugin-Since-Build: 251\n' >"$OUT/manifest.txt"
jar cfm "$OUT/DotKtX.Compose.UI.WinUI.Analyzers.jar" "$OUT/manifest.txt" -C "$OUT/classes" . -C "$SRC/resources" .
echo "BUILT $OUT/DotKtX.Compose.UI.WinUI.Analyzers.jar"
