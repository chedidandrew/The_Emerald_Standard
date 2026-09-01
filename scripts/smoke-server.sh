#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "fabric" && "$1" != "neoforge" ) ]]; then
    echo "Usage: $0 <fabric|neoforge>" >&2
    exit 2
fi

LOADER="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/$LOADER/run"
LOG_DIR="$ROOT/build/server-smoke"
LOG_FILE="$LOG_DIR/$LOADER.log"

rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR" "$LOG_DIR"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"

command=(gradle --no-daemon -p "$ROOT/$LOADER" runServer)
if [[ "$LOADER" == "fabric" ]]; then
    command+=(--args=--nogui)
fi

setsid "${command[@]}" > "$LOG_FILE" 2>&1 &
process_group="$!"

cleanup() {
    kill -TERM -- "-$process_group" 2>/dev/null || true
    sleep 1
    kill -KILL -- "-$process_group" 2>/dev/null || true
    wait "$process_group" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 360); do
    if grep -Fq "The Emerald Standard economy started" "$LOG_FILE" \
            && grep -Eq 'Done \([^)]*s\)!' "$LOG_FILE"; then
        echo "PASS $LOADER dedicated-server smoke test"
        tail -n 80 "$LOG_FILE"
        exit 0
    fi
    if ! kill -0 "$process_group" 2>/dev/null; then
        echo "$LOADER server process exited before startup completed" >&2
        cat "$LOG_FILE" >&2
        exit 1
    fi
    sleep 1
done

echo "$LOADER server did not finish startup within 360 seconds" >&2
cat "$LOG_FILE" >&2
exit 1
