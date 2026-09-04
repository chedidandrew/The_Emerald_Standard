#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    echo "Usage: $0 <40-character-commit> <fabric-artifact-dir> <neoforge-artifact-dir> <empty-output-dir>" >&2
    exit 2
fi

COMMIT="$1"
FABRIC_ARTIFACT="$2"
NEOFORGE_ARTIFACT="$3"
OUTPUT="$4"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ ! "$COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Commit must be a lowercase, full 40-character Git object id" >&2
    exit 2
fi

head_commit="$(git -C "$ROOT" rev-parse HEAD)"
if [[ "$head_commit" != "$COMMIT" ]]; then
    echo "Current source commit $head_commit does not match requested release commit $COMMIT" >&2
    exit 1
fi
if [[ -n "$(git -C "$ROOT" status --porcelain=v1 --untracked-files=all)" ]]; then
    echo "Release staging requires a clean worktree at the exact candidate commit" >&2
    exit 1
fi

fabric_version="$(grep '^mod_version=' "$ROOT/fabric/gradle.properties" | cut -d= -f2-)"
neoforge_version="$(grep '^mod_version=' "$ROOT/neoforge/gradle.properties" | cut -d= -f2-)"
if [[ -z "$fabric_version" || "$fabric_version" != "$neoforge_version" ]]; then
    echo "Loader version mismatch: Fabric='$fabric_version' NeoForge='$neoforge_version'" >&2
    exit 1
fi
VERSION="$fabric_version"

if [[ -L "$OUTPUT" ]]; then
    echo "Output directory must not be a symbolic link: $OUTPUT" >&2
    exit 1
elif [[ -e "$OUTPUT" ]]; then
    if [[ ! -d "$OUTPUT" || -n "$(find "$OUTPUT" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
        echo "Output directory must not exist or must be empty: $OUTPUT" >&2
        exit 1
    fi
fi

verify_artifact() {
    local loader="$1"
    local artifact="$2"
    local expected_artifact_name="the-emerald-standard-$loader-$COMMIT"
    local binary_name="the-emerald-standard-$loader-$VERSION.jar"
    local sources_name="the-emerald-standard-$loader-$VERSION-sources.jar"

    if [[ ! -d "$artifact"
            || -L "$artifact"
            || "$(basename "$artifact")" != "$expected_artifact_name" ]]; then
        echo "Expected downloaded artifact directory named $expected_artifact_name" >&2
        exit 1
    fi
    if [[ ! -f "$artifact/$binary_name" || -L "$artifact/$binary_name"
            || ! -f "$artifact/$sources_name" || -L "$artifact/$sources_name"
            || ! -f "$artifact/SHA256SUMS" || -L "$artifact/SHA256SUMS" ]]; then
        echo "$artifact does not contain the expected regular JARs and SHA256SUMS" >&2
        exit 1
    fi

    mapfile -t artifact_entries < <(find "$artifact" -mindepth 1 -maxdepth 1 -printf '%f\n' | LC_ALL=C sort)
    if [[ ${#artifact_entries[@]} -ne 3
            || "${artifact_entries[0]}" != "SHA256SUMS"
            || "${artifact_entries[1]}" != "$sources_name"
            || "${artifact_entries[2]}" != "$binary_name" ]]; then
        echo "$artifact contains an unexpected public artifact set" >&2
        printf '%s\n' "${artifact_entries[@]}" >&2
        exit 1
    fi

    local expected_manifest actual_manifest
    expected_manifest="$(
        cd "$artifact"
        sha256sum -b "$binary_name" "$sources_name"
    )"
    actual_manifest="$(tr -d '\r' < "$artifact/SHA256SUMS")"
    if [[ "$actual_manifest" != "$expected_manifest" ]]; then
        echo "$artifact/SHA256SUMS must contain exactly the expected binary and sources checksums" >&2
        exit 1
    fi
    (cd "$artifact" && sha256sum --check --strict SHA256SUMS)
}

verify_artifact fabric "$FABRIC_ARTIFACT"
verify_artifact neoforge "$NEOFORGE_ARTIFACT"

# Copy only after both loader artifacts have passed every check so a failed second
# artifact cannot leave a plausible-looking partial release set behind.
if [[ ! -e "$OUTPUT" ]]; then
    mkdir -p "$OUTPUT"
fi
if [[ -L "$OUTPUT" || ! -d "$OUTPUT"
        || -n "$(find "$OUTPUT" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo "Output directory changed while artifacts were being verified: $OUTPUT" >&2
    exit 1
fi
cp \
    "$FABRIC_ARTIFACT/the-emerald-standard-fabric-$VERSION.jar" \
    "$FABRIC_ARTIFACT/the-emerald-standard-fabric-$VERSION-sources.jar" \
    "$NEOFORGE_ARTIFACT/the-emerald-standard-neoforge-$VERSION.jar" \
    "$NEOFORGE_ARTIFACT/the-emerald-standard-neoforge-$VERSION-sources.jar" \
    "$OUTPUT/"

(
    cd "$OUTPUT"
    LC_ALL=C sha256sum -b ./*.jar | sed 's#\*\./#*#' > SHA256SUMS
)

{
    echo "The Emerald Standard $VERSION"
    echo "Source commit: $COMMIT"
    echo "Release files:"
    sed 's/^/  /' "$OUTPUT/SHA256SUMS"
} > "$OUTPUT/RELEASE_MANIFEST.txt"

echo "PASS staged release assets for $VERSION from exact commit $COMMIT"
cat "$OUTPUT/RELEASE_MANIFEST.txt"
