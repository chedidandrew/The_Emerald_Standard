#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "fabric" && "$1" != "neoforge" ) ]]; then
    echo "Usage: $0 <fabric|neoforge>" >&2
    exit 2
fi

LOADER="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/$LOADER/run"
LOG_DIR="$ROOT/build/client-smoke"
LOG_FILE="$LOG_DIR/$LOADER.log"

rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR" "$LOG_DIR"

set +e
ALSOFT_DRIVERS="null" \
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dthe_emerald_standard.clientSmoke=true" \
    timeout 240s xvfb-run -a \
    bash "$ROOT/$LOADER/gradlew" --no-daemon -p "$ROOT/$LOADER" runClient \
    > "$LOG_FILE" 2>&1
status=$?
set -e

# Minecraft logs two recoverable errors on some headless Linux runners when narrator or audio
# devices are unavailable. They do not prevent the client, resources, or mod screen registry from
# initializing. Any other ERROR/FATAL entry remains a hard failure.
unexpected_errors="$(
    grep -E '\[[^]]+/(ERROR|FATAL)\]' "$LOG_FILE" \
        | grep -Ev 'Error while loading the narrator|Error starting SoundSystem\. Turning off sounds & music' \
        || true
)"

if [[ -n "$unexpected_errors" ]] \
        || grep -Eq 'Exception in thread|A fatal error has been detected|ReportedException|Could not execute entrypoint|ModLoadingException|Mixin apply failed|NoClassDefFoundError|ClassNotFoundException|Crash report saved to' "$LOG_FILE"; then
    echo "$LOADER client logged an unexpected fatal startup error" >&2
    if [[ -n "$unexpected_errors" ]]; then
        printf '%s\n' "$unexpected_errors" >&2
    fi
    cat "$LOG_FILE" >&2
    exit 1
fi

if ! grep -Fq "The Emerald Standard client initialized" "$LOG_FILE"; then
    echo "$LOADER client never initialized The Emerald Standard" >&2
    cat "$LOG_FILE" >&2
    exit 1
fi

if ! grep -Fq "Stopping!" "$LOG_FILE"; then
    echo "$LOADER client did not reach the controlled smoke-test shutdown" >&2
    cat "$LOG_FILE" >&2
    exit 1
fi

if [[ $status -ne 0 ]]; then
    echo "$LOADER client did not exit cleanly after the smoke marker (status $status)" >&2
    cat "$LOG_FILE" >&2
    exit 1
fi

echo "PASS $LOADER client bootstrap smoke test"
tail -n 100 "$LOG_FILE"
