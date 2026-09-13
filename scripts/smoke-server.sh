#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "fabric" && "$1" != "neoforge" ) ]]; then
    echo "Usage: $0 <fabric|neoforge>" >&2
    exit 2
fi

LOADER="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/build/server-smoke"
LOG_FILE="$LOG_DIR/$LOADER.log"

mkdir -p "$LOG_DIR"
RUN_DIR="$(mktemp -d "$LOG_DIR/$LOADER-run.XXXXXX")"
SMOKE_ID="$LOADER-$(date +%s)-$$-$RANDOM"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
# Exhaustive opt-in catalog checks run synchronously during startup. Allow a bounded three minutes
# in this fresh smoke world under constrained CI/parallel review; ordinary server settings are untouched.
printf 'online-mode=false\nserver-port=0\nmax-tick-time=180000\n' > "$RUN_DIR/server.properties"
if [[ -n "${TES_GUARD_COMPAT_JAR:-}" ]]; then
    [[ -f "$TES_GUARD_COMPAT_JAR" ]] || { echo "Missing Guard Villagers test JAR" >&2; exit 1; }
    mkdir -p "$RUN_DIR/mods"
    cp -- "$TES_GUARD_COMPAT_JAR" "$RUN_DIR/mods/"
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dthe_emerald_standard.expectGuards=true"
fi

command=(
    bash "$ROOT/$LOADER/gradlew"
    --no-daemon
    -p "$ROOT/$LOADER"
    -I "$ROOT/scripts/smoke-server.init.gradle"
    -PtesSmokeGameDir="$RUN_DIR"
    runServer
)
if [[ "$LOADER" == "fabric" ]]; then
    command+=(--args=--nogui)
fi

if command -v setsid >/dev/null 2>&1; then
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dthe_emerald_standard.integrationSmoke=true -Dthe_emerald_standard.smokeId=$SMOKE_ID" \
        setsid "${command[@]}" > "$LOG_FILE" 2>&1 &
    process_id="$!"
    process_mode="group"
else
    # Git for Windows does not ship setsid. Keep the direct PID and let taskkill terminate its
    # Gradle/Java descendants after the startup marker so no dedicated server is orphaned.
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dthe_emerald_standard.integrationSmoke=true -Dthe_emerald_standard.smokeId=$SMOKE_ID" \
        "${command[@]}" > "$LOG_FILE" 2>&1 &
    process_id="$!"
    process_mode="tree"
fi

cleanup() {
    if [[ "$process_mode" == "group" ]]; then
        kill -TERM -- "-$process_id" 2>/dev/null || true
        sleep 1
        kill -KILL -- "-$process_id" 2>/dev/null || true
    elif command -v taskkill.exe >/dev/null 2>&1 && command -v jps >/dev/null 2>&1; then
        # MSYS background PIDs are not guaranteed to be Windows process IDs. Stop the shell
        # through MSYS, then locate only Java descendants carrying this run's unique marker.
        kill -TERM "$process_id" 2>/dev/null || true
        sleep 1
        while read -r java_pid _; do
            [[ -n "$java_pid" ]] || continue
            taskkill.exe //PID "$java_pid" //T //F >/dev/null 2>&1 || true
        done < <(jps -lv | grep -F -- "-Dthe_emerald_standard.smokeId=$SMOKE_ID" || true)
    else
        kill -TERM "$process_id" 2>/dev/null || true
    fi
    wait "$process_id" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 360); do
    unexpected_errors="$(
        grep -E '\[[^]]+/(ERROR|FATAL)\]' "$LOG_FILE" \
            | grep -Ev 'HkeyPerformanceDataUtil[^:]*:?[[:space:]]*Unable to locate English counter names' \
            || true
    )"
    # Opt-in exception for the verified upstream NeoForge 4.0.3 recruitment advancement bug.
    # Keep the original error in the log and report it below. No other external errors are ignored.
    if [[ "$LOADER" == "neoforge" && -n "${TES_GUARD_COMPAT_JAR:-}" && "${TES_ALLOW_GUARD_ADVANCEMENT_ERROR:-0}" == "1" ]]; then
        unexpected_errors="$(printf '%s\n' "$unexpected_errors" | grep -Fv "[minecraft/SimpleJsonResourceReloadListener]: Couldn't parse data file 'guardvillagers:adventure/recruit_guard' from 'guardvillagers:advancement/adventure/recruit_guard.json': DataResult.Error['Unknown registry key in ResourceKey[minecraft:root / minecraft:entity_sub_predicate_type]: minecraft:type;" || true)"
    fi
    if [[ -n "$unexpected_errors" ]] \
            || grep -Eq 'Exception in thread|A fatal error has been detected|Failed to start the minecraft server' "$LOG_FILE"; then
        echo "$LOADER server logged a fatal startup error" >&2
        if [[ -n "$unexpected_errors" ]]; then
            printf '%s\n' "$unexpected_errors" >&2
        fi
        cat "$LOG_FILE" >&2
        exit 1
    fi
    if grep -Fq "The Emerald Standard economy started" "$LOG_FILE" \
            && grep -Fq "The Emerald Standard Banker integration self-test passed" "$LOG_FILE" \
            && grep -Eq 'Done \([^)]*s\)!' "$LOG_FILE"; then
        echo "PASS $LOADER dedicated-server smoke test"
        if grep -Fq "Couldn't parse data file 'guardvillagers:adventure/recruit_guard'" "$LOG_FILE"; then
            echo "KNOWN UPSTREAM ERROR retained: Guard Villagers recruitment advancement; guard integration checks still required."
        fi
        tail -n 100 "$LOG_FILE"
        exit 0
    fi
    if ! kill -0 "$process_id" 2>/dev/null; then
        echo "$LOADER server process exited before startup completed" >&2
        cat "$LOG_FILE" >&2
        exit 1
    fi
    sleep 1
done

echo "$LOADER server did not finish startup within 360 seconds" >&2
cat "$LOG_FILE" >&2
exit 1
