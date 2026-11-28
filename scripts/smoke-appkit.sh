#!/usr/bin/env bash
# Run the AppKit smoke test (appkit/tests/smoke): an application that composes the library's composables,
# drives them from inside the process and exits with a non-zero code on the first failed check.
# Build first with scripts/build-appkit.sh. A hung run is killed after the timeout.
set -euo pipefail
REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
app="$REPO/artifacts/bin/appkit/tests/smoke/Release/net10.0-macos26.0/osx-arm64/Smoke.app/Contents/MacOS/Smoke"
log="$REPO/artifacts/logs/appkit-smoke.run.log"
mkdir -p "$REPO/artifacts/logs"
timeout_seconds="${SMOKE_TIMEOUT:-120}"
"$app" >"$log" 2>&1 &
pid=$!
for ((i = 0; i < timeout_seconds; i++)); do
  if ! kill -0 "$pid" 2>/dev/null; then break; fi
  sleep 1
done
if kill -0 "$pid" 2>/dev/null; then
  kill "$pid" 2>/dev/null || true
  echo "--- smoke output ($log)"; cat "$log"
  echo "FAILED: smoke test still running after ${timeout_seconds}s"
  exit 1
fi
status=0
wait "$pid" || status=$?
echo "--- smoke output ($log)"; cat "$log"
if [ "$status" -ne 0 ]; then echo "FAILED: smoke exit code $status"; exit "$status"; fi
echo "PASS appkit smoke"
