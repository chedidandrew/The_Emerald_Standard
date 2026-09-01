#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/common-tests"

rm -rf "$BUILD"
mkdir -p "$BUILD"

mapfile -t MAIN_SOURCES < <(find "$ROOT/common/src/main/java" -name '*.java' -print | sort)
mapfile -t TEST_SOURCES < <(find "$ROOT/common/src/test/java" -name '*.java' -print | sort)

javac --release 21 -d "$BUILD" "${MAIN_SOURCES[@]}"
javac --release 21 -cp "$BUILD" -d "$BUILD" "${TEST_SOURCES[@]}"

java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.EconomyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.PersistenceRegressionTest

fabric_version="$(grep '^mod_version=' "$ROOT/fabric/gradle.properties" | cut -d= -f2-)"
neo_version="$(grep '^mod_version=' "$ROOT/neoforge/gradle.properties" | cut -d= -f2-)"
if [[ -z "$fabric_version" || "$fabric_version" != "$neo_version" ]]; then
    echo "Loader version mismatch: Fabric='$fabric_version' NeoForge='$neo_version'" >&2
    exit 1
fi

echo "PASS loader version parity: $fabric_version"
