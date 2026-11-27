#!/usr/bin/env bash
# Compile analyzers/tests/diagnostics with the analyzers and compare the reported errors with the
# `// expect: NAME` comments in its sources. The project is expected to fail to compile. Warnings are
# not covered here (kotc prints none when there are errors); scripts/verify-packages.sh checks one.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
DIR="$REPO/analyzers/tests/diagnostics"
mkdir -p "$REPO/artifacts/logs"
log="$REPO/artifacts/logs/analyzers-test.build.log"
dotnet build "$DIR/Diagnostics.ktproj" -c Release --nologo -m:1 -p:AppxGeneratePriEnabled=false "$@" >"$log" 2>&1 || true
# Messages identify a diagnostic; kotc prints a hash instead of its name.
message() {
  case "$1" in
    SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT) echo "which holds at most one" ;;
    *) echo "unknown diagnostic in an expect comment: $1" >&2; exit 2 ;;
  esac
}
expected="$(mktemp)"; actual="$(mktemp)"; trap 'rm -f "$expected" "$actual"' EXIT
for file in "$DIR"/*.kt; do
  grep -n '// expect: ' "$file" | while IFS=: read -r line rest; do
    echo "$(basename "$file"):$line: $(message "${rest##*// expect: }")"
  done
done | sort -u >"$expected"
sed -n 's/^\(.*\/\)\{0,1\}\([A-Za-z]*\.kt\)(\([0-9]*\),[0-9]*): error [A-Z0-9]*: .*\(which holds at most one\).*$/\2:\3: \4/p' "$log" | sort -u >"$actual"
if [ ! -s "$actual" ] && ! grep -q "Cases.kt" "$log"; then
  grep -E "error" "$log" | sort -u | head -20; echo "FAILED: the compiler did not reach the test sources (full log: $log)"; exit 1
fi
if diff "$expected" "$actual" >/dev/null; then
  echo "PASS analyzers ($(wc -l <"$expected") diagnostics)"
else
  echo "expected (<) and reported (>) diagnostics differ:"; diff "$expected" "$actual" || true
  echo "FAILED (full log: $log)"; exit 1
fi
