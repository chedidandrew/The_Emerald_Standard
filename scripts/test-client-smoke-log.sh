#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -f "$TMP/good.log" "$TMP/test.log"; rmdir "$TMP"' EXIT
for pass in 1 2; do
    printf '%s\n' \
        '[00:00:00] [Render thread/INFO] The Emerald Standard client initialized' \
        'Emerald Handbook page layout verified for 61 pages' \
        'The Emerald Standard standard cursor platform probe passed' \
        'The Emerald Standard reader navigation and persistence checks passed' \
        'The Emerald Standard animated recipe render, variant, hover and layout checks passed' \
        'The Emerald Standard reader and settings screen smoke checks passed' \
        'The Emerald Standard settings all-page reset and speed editor checks passed' \
        'The Emerald Standard dashboard render and text-fit checks passed' \
        'The Emerald Standard construction crew render and animation checks passed' \
        'The Emerald Standard optional Mod Menu absence verified' \
        'The Emerald Standard Mod Menu configuration factory verified' \
        'The Emerald Standard NeoForge configuration factory verified' \
        'Stopping!' >> "$TMP/good.log"
done
for loader in fabric neoforge; do
    TES_TEST_MODMENU=absent bash "$ROOT/scripts/verify-client-smoke-log.sh" "$loader" "$TMP/good.log" 0
done
TES_TEST_MODMENU=present bash "$ROOT/scripts/verify-client-smoke-log.sh" fabric "$TMP/good.log" 0
reject() {
    if TES_TEST_MODMENU=present bash "$ROOT/scripts/verify-client-smoke-log.sh" fabric "$TMP/test.log" "${1:-0}" >/dev/null 2>&1; then
        echo 'FAIL strict client harness accepted a failing fixture' >&2
        exit 1
    fi
}
for error in \
    '[00:00:00] [Render thread/ERROR] (Minecraft) 65547: X11: Standard cursor shape unavailable' \
    '[00:00:00] [Render thread/ERROR] (Minecraft) ########## GL ERROR ##########' \
    '[00:00:00] [Render thread/ERROR] (Minecraft) another X11 error' \
    '[00:00:00] [Render thread/FATAL] rendering failure' \
    '[00:00:00] [Render thread/ERROR] handbook rendering failure' \
    'Exception in thread' 'A fatal error has been detected' 'ReportedException' \
    'Could not execute entrypoint' 'ModLoadingException' 'Mixin apply failed' \
    'NoClassDefFoundError' 'ClassNotFoundException' 'Crash report saved to'; do
    cp "$TMP/good.log" "$TMP/test.log"
    printf '%s\n' "$error" >> "$TMP/test.log"
    reject
done
for marker in 'client initialized' '61 pages' 'cursor platform probe' 'navigation and persistence' \
    'animated recipe render, variant, hover and layout checks passed' \
    'dashboard render and text-fit checks passed' \
    'construction crew render and animation checks passed' \
    'settings all-page reset and speed editor checks passed' \
    'reader and settings screen smoke checks passed' 'Mod Menu configuration factory' 'Stopping!'; do
    grep -v "$marker" "$TMP/good.log" > "$TMP/test.log"
    reject
done
head -n 9 "$TMP/good.log" > "$TMP/test.log"
reject
cp "$TMP/good.log" "$TMP/test.log"
reject 1
echo 'PASS strict smoke log fixtures: cursor/errors/crashes/missing checks/restart/nonzero exit rejected'
