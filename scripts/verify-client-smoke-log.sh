#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 3 || ( "$1" != fabric && "$1" != neoforge ) ]]; then
    echo "Usage: $0 <fabric|neoforge> <log> <exit-status>" >&2
    exit 2
fi
LOADER="$1"
LOG_FILE="$2"
status="$3"
[[ -f "$LOG_FILE" ]] || exit 1

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

if ! grep -Fq "Emerald Handbook page layout verified for 61 pages" "$LOG_FILE"; then
    echo "$LOADER client did not verify every localized handbook page" >&2
    cat "$LOG_FILE" >&2
    exit 1
fi

if ! grep -Fq "Stopping!" "$LOG_FILE"; then
    echo "$LOADER client did not reach the controlled smoke-test shutdown" >&2
    cat "$LOG_FILE" >&2
    exit 1
fi

for marker in \
    "The Emerald Standard standard cursor platform probe passed" \
    "The Emerald Standard reader navigation and persistence checks passed" \
    "The Emerald Standard animated recipe render, variant, hover and layout checks passed" \
    "The Emerald Standard reader and settings screen smoke checks passed" \
    "The Emerald Standard settings all-page reset and speed editor checks passed" \
    "The Emerald Standard dashboard render and text-fit checks passed" \
    "The Emerald Standard construction crew render and animation checks passed"; do
    if [[ $(grep -Fc "$marker" "$LOG_FILE") -ne 2 ]]; then
        echo "$LOADER missing successful first-launch/restart evidence: $marker" >&2
        cat "$LOG_FILE" >&2
        exit 1
    fi
done
if [[ "${TES_TEST_MODMENU:-absent}" == present ]] \
    && [[ $(grep -Fc "The Emerald Standard Mod Menu configuration factory verified" "$LOG_FILE") -ne 2 ]]; then
    echo "Mod Menu configuration was not verified in both processes" >&2
    exit 1
fi
if [[ "${TES_TEST_MODMENU:-absent}" == absent ]] \
    && [[ $(grep -Fc "The Emerald Standard optional Mod Menu absence verified" "$LOG_FILE") -ne 2 ]]; then
    echo "Optional Mod Menu absence was not verified in both processes" >&2
    exit 1
fi
if [[ "$LOADER" == neoforge ]] \
    && [[ $(grep -Fc "The Emerald Standard NeoForge configuration factory verified" "$LOG_FILE") -ne 2 ]]; then
    echo "NeoForge configuration was not verified in both processes" >&2
    exit 1
fi

if [[ $status -ne 0 ]]; then
    echo "$LOADER client did not exit cleanly after the smoke marker (status $status)" >&2
    cat "$LOG_FILE" >&2
    exit 1
fi

echo "PASS $LOADER strict client log validation"
