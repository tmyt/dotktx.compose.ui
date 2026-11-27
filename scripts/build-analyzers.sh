#!/usr/bin/env bash
# Build the analyzers (a K2 compiler plugin jar) with the Kotlin compiler that DotKt's kotc embeds,
# so the plugin always matches the compiler that loads it.
# Output: artifacts/analyzers/DotKtX.Compose.UI.Analyzers.jar and kotlin-version.txt
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
SDK_VERSION="$(sed -n 's/.*"DotKt.Sdk": *"\([^"]*\)".*/\1/p' "$REPO/global.json")"
LIB="$HOME/.nuget/packages/dotkt.toolchain/$SDK_VERSION/tools/kotc/lib"
COMPILER="$(ls "$LIB"/kotlin-compiler-embeddable-*.jar)"
STDLIB="$(ls "$LIB"/kotlin-stdlib-[0-9]*.jar)"
SRC="$REPO/analyzers/src/main"
OUT="$REPO/artifacts/analyzers"
rm -rf "${OUT:?}"
mkdir -p "$OUT/classes"
java -cp "$LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 \
  -opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi \
  -cp "$COMPILER:$STDLIB" -d "$OUT/classes" "$SRC/kotlin"
# The Kotlin LSP loads a compiler plugin that is not bundled with it only when the manifest declares
# the IDE builds it is compatible with.
printf 'Kotlin-Compiler-Plugin-Since-Build: 251\n' >"$OUT/manifest.txt"
jar cfm "$OUT/DotKtX.Compose.UI.Analyzers.jar" "$OUT/manifest.txt" -C "$OUT/classes" . -C "$SRC/resources" .
# The package's targets compare this with the Kotlin version of the DotKt SDK that loads the plugin.
basename "$COMPILER" .jar | sed 's/^kotlin-compiler-embeddable-//' >"$OUT/kotlin-version.txt"
echo "BUILT $OUT/DotKtX.Compose.UI.Analyzers.jar (Kotlin $(cat "$OUT/kotlin-version.txt"))"
