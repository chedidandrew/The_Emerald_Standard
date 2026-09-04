#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/scale-tests"

rm -rf "$BUILD"
mkdir -p "$BUILD"

mapfile -t MAIN_SOURCES < <(find "$ROOT/common/src/main/java" -name '*.java' -print | sort)
mapfile -t TEST_SOURCES < <(find "$ROOT/common/src/test/java" -name '*.java' -print | sort)

javac --release 21 -d "$BUILD" "${MAIN_SOURCES[@]}"
javac --release 21 -cp "$BUILD" -d "$BUILD" "${TEST_SOURCES[@]}"

java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.LargeWorldStressRegressionTest
