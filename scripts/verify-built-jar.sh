#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "fabric" && "$1" != "neoforge" ) ]]; then
    echo "Usage: $0 <fabric|neoforge>" >&2
    exit 2
fi

LOADER="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="$ROOT/$LOADER/build/libs"
VERSION="$(grep '^mod_version=' "$ROOT/$LOADER/gradle.properties" | cut -d= -f2-)"

if [[ -n "${PYTHON:-}" ]]; then
    PYTHON_COMMAND=("$PYTHON")
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
    PYTHON_COMMAND=(python3)
elif command -v py >/dev/null 2>&1 && py -3 -c 'import json' >/dev/null 2>&1; then
    PYTHON_COMMAND=(py -3)
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
    PYTHON_COMMAND=(python)
else
    echo "Python 3 is required to verify packaged metadata JSON" >&2
    exit 1
fi

if [[ -z "$VERSION" ]]; then
    echo "Could not determine $LOADER mod_version" >&2
    exit 1
fi

binary_name="the-emerald-standard-$LOADER-$VERSION.jar"
sources_name="the-emerald-standard-$LOADER-$VERSION-sources.jar"
jar_file="$LIB_DIR/$binary_name"
sources_file="$LIB_DIR/$sources_name"

mapfile -t jars < <(find "$LIB_DIR" -maxdepth 1 -type f -name '*.jar' | sort)
if [[ ${#jars[@]} -ne 2 || ! -f "$jar_file" || ! -f "$sources_file" ]]; then
    echo "Expected exactly $binary_name and $sources_name; found ${#jars[@]} JAR(s)" >&2
    printf '%s\n' "${jars[@]}" >&2
    exit 1
fi

required=(
    'com/chedidandrew/emeraldstandard/core/EconomyService.class'
    'com/chedidandrew/emeraldstandard/client/BankerScreenLayout.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerMenu.class'
    'com/chedidandrew/emeraldstandard/minecraft/FundConfirmationFingerprint.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerProfessionSupport.class'
    'com/chedidandrew/emeraldstandard/minecraft/PlayerOnboarding.class'
    'com/chedidandrew/emeraldstandard/client/BankerScreen.class'
    'assets/the_emerald_standard/lang/en_us.json'
    'assets/the_emerald_standard/blockstates/exchange_desk.json'
    'assets/the_emerald_standard/items/exchange_desk.json'
    'assets/the_emerald_standard/models/block/exchange_desk.json'
    'assets/the_emerald_standard/models/item/exchange_desk.json'
    'assets/the_emerald_standard/textures/entity/villager/profession/banker.png'
    'assets/the_emerald_standard/textures/entity/zombie_villager/profession/banker.png'
    'data/minecraft/tags/block/mineable/axe.json'
    'data/minecraft/tags/point_of_interest_type/acquirable_job_site.json'
    'data/the_emerald_standard/loot_table/blocks/exchange_desk.json'
    'data/the_emerald_standard/recipe/exchange_desk.json'
)
if [[ "$LOADER" == "fabric" ]]; then
    required+=(
        'fabric.mod.json'
        'com/chedidandrew/emeraldstandard/fabric/EmeraldStandardFabric.class'
        'com/chedidandrew/emeraldstandard/fabric/EmeraldStandardFabricClient.class'
        'com/chedidandrew/emeraldstandard/fabric/BankerProfessionFabric.class'
    )
else
    required+=(
        'META-INF/neoforge.mods.toml'
        'com/chedidandrew/emeraldstandard/neoforge/EmeraldStandardNeoForge.class'
        'com/chedidandrew/emeraldstandard/neoforge/EmeraldStandardNeoForgeClient.class'
        'com/chedidandrew/emeraldstandard/neoforge/BankerProfessionNeoForge.class'
    )
fi

source_listing="$(jar tf "$sources_file")"
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/EconomyService.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing shared Java sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/client/BankerScreen.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing client Java sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/client/BankerScreenLayout.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing Banker screen layout sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/FundConfirmationFingerprint.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing Fund confirmation sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/PlayerOnboarding.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing onboarding Java sources" >&2
    exit 1
fi

listing="$(jar tf "$jar_file")"
for entry in "${required[@]}"; do
    if ! grep -Fxq "$entry" <<<"$listing"; then
        echo "$jar_file is missing required entry $entry" >&2
        exit 1
    fi
done

if ! unzip -p "$jar_file" assets/the_emerald_standard/lang/en_us.json \
        | "${PYTHON_COMMAND[@]}" -m json.tool >/dev/null; then
    echo "$jar_file contains invalid English language JSON" >&2
    exit 1
fi

if ! unzip -p "$jar_file" META-INF/MANIFEST.MF \
        | tr -d '\r' \
        | grep -Fxq "Implementation-Version: $VERSION"; then
    echo "$jar_file manifest does not declare Implementation-Version $VERSION" >&2
    exit 1
fi

if [[ "$LOADER" == "fabric" ]]; then
    if ! unzip -p "$jar_file" fabric.mod.json \
            | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); expected=sys.argv[1]; assert d["id"] == "the_emerald_standard" and d["version"] == expected' "$VERSION"; then
        echo "$jar_file contains incorrect Fabric mod identity or version" >&2
        exit 1
    fi
else
    metadata="$(unzip -p "$jar_file" META-INF/neoforge.mods.toml | tr -d '\r')"
    if ! grep -Fxq 'modId="the_emerald_standard"' <<<"$metadata" \
            || ! grep -Fxq "version=\"$VERSION\"" <<<"$metadata"; then
        echo "$jar_file contains incorrect NeoForge mod identity or version" >&2
        exit 1
    fi
fi

(cd "$LIB_DIR" && sha256sum -b "$binary_name" "$sources_name" > SHA256SUMS)

echo "PASS packaged $LOADER JAR verification: $binary_name"
cat "$LIB_DIR/SHA256SUMS"
