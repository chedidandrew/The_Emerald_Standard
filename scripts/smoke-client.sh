#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "fabric" && "$1" != "neoforge" ) ]]; then
    echo "Usage: $0 <fabric|neoforge>" >&2
    exit 2
fi

LOADER="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/build/client-smoke"
LOG_FILE="$LOG_DIR/$LOADER.log"

mkdir -p "$LOG_DIR"
# Never reuse or delete the developer's ordinary loader run directory: it may contain a manual
# test world. Each smoke launch gets an isolated game directory under disposable build output.
RUN_DIR="$(mktemp -d "$LOG_DIR/$LOADER-run.XXXXXX")"
: > "$LOG_FILE"

status=0
# Two independent processes sharing only this disposable profile verify restart persistence.
for restart in false true; do
set +e
ALSOFT_DRIVERS="null" \
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dthe_emerald_standard.clientSmoke=true -Dthe_emerald_standard.clientSmokeRestart=$restart" \
    timeout 240s xvfb-run -a \
    bash "$ROOT/$LOADER/gradlew" --no-daemon -p "$ROOT/$LOADER" \
    -I "$ROOT/scripts/smoke-client.init.gradle" \
    -PtesSmokeGameDir="$RUN_DIR" \
    runClient \
    >> "$LOG_FILE" 2>&1
status=$?
set -e
if [[ $status -ne 0 ]]; then break; fi
done

bash "$ROOT/scripts/verify-client-smoke-log.sh" "$LOADER" "$LOG_FILE" "$status"

echo "PASS $LOADER client bootstrap smoke test"
tail -n 100 "$LOG_FILE"
