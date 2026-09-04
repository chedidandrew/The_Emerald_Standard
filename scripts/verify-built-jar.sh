#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "fabric" && "$1" != "neoforge" ) ]]; then
    echo "Usage: $0 <fabric|neoforge>" >&2
    exit 2
fi

LOADER="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="$ROOT/$LOADER/build/libs"
PYTHON_BIN="${PYTHON:-python3}"

mapfile -t jars < <(find "$LIB_DIR" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' | sort)
if [[ ${#jars[@]} -ne 1 ]]; then
    echo "Expected exactly one playable $LOADER JAR, found ${#jars[@]}" >&2
    printf '%s\n' "${jars[@]}" >&2
    exit 1
fi

jar_file="${jars[0]}"
required=(
    'com/chedidandrew/emeraldstandard/core/EconomyService.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerMenu.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerProfessionSupport.class'
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

listing="$(jar tf "$jar_file")"
for entry in "${required[@]}"; do
    if ! grep -Fxq "$entry" <<<"$listing"; then
        echo "$jar_file is missing required entry $entry" >&2
        exit 1
    fi
done

if ! unzip -p "$jar_file" assets/the_emerald_standard/lang/en_us.json \
        | "$PYTHON_BIN" -m json.tool >/dev/null; then
    echo "$jar_file contains invalid English language JSON" >&2
    exit 1
fi

echo "PASS packaged $LOADER JAR verification: $(basename "$jar_file")"
