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
    'com/chedidandrew/emeraldstandard/client/BankerScreenScale.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerMenu.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerAmountSelection.class'
    'com/chedidandrew/emeraldstandard/minecraft/TerrainFoundationPlan.class'
    'com/chedidandrew/emeraldstandard/minecraft/VillageEntranceApproachPlan.class'
    'com/chedidandrew/emeraldstandard/minecraft/VillageMaterializationPolicy.class'
    'com/chedidandrew/emeraldstandard/minecraft/VillageStructureProgression.class'
    'com/chedidandrew/emeraldstandard/core/StructureGalleryPlan.class'
    'com/chedidandrew/emeraldstandard/core/VillageComparisonBiomePlan.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredChimneyRefinements.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredCeilingClearanceRefinements.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredSmithyRoofRefinements.class'
    'com/chedidandrew/emeraldstandard/core/RoofGeometryValidator.class'
    'com/chedidandrew/emeraldstandard/core/WholeBuildingDetailDensityValidator.class'
    'com/chedidandrew/emeraldstandard/core/WholeBuildingDistinctivenessValidator.class'
    'com/chedidandrew/emeraldstandard/core/WholeBuildingPresentationValidator.class'
    'com/chedidandrew/emeraldstandard/core/WholeBuildingRoleReadabilityValidator.class'
    'com/chedidandrew/emeraldstandard/core/WholeBuildingValidator.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredVillageStructures.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredLandscapeRefinements.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredStructuralContactRefinements.class'
    'com/chedidandrew/emeraldstandard/minecraft/AuthoredWorkshopContactRefinements.class'
    'com/chedidandrew/emeraldstandard/minecraft/VillageComparisonGallery.class'
    'com/chedidandrew/emeraldstandard/client/VillageComparisonCaptureSupport.class'
    'com/chedidandrew/emeraldstandard/minecraft/StructureGallery.class'
    'com/chedidandrew/emeraldstandard/minecraft/StructureGalleryBlock.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerMenuPacketCodecSelfTest.class'
    'com/chedidandrew/emeraldstandard/minecraft/ContainerDataPacking.class'
    'com/chedidandrew/emeraldstandard/minecraft/ShortPackedContainerData.class'
    'com/chedidandrew/emeraldstandard/minecraft/FundConfirmationFingerprint.class'
    'com/chedidandrew/emeraldstandard/minecraft/BankerProfessionSupport.class'
    'com/chedidandrew/emeraldstandard/minecraft/EmeraldHandbook.class'
    'com/chedidandrew/emeraldstandard/minecraft/HandbookReaderItem.class'
    'com/chedidandrew/emeraldstandard/client/HandbookScreen.class'
    'com/chedidandrew/emeraldstandard/client/EmeraldSettingsScreen.class'
    'com/chedidandrew/emeraldstandard/client/ReaderPreferences.class'
    'com/chedidandrew/emeraldstandard/minecraft/ExchangeDeskBlock.class'
    'com/chedidandrew/emeraldstandard/minecraft/PlayerOnboarding.class'
    'com/chedidandrew/emeraldstandard/client/BankerScreen.class'
    'assets/the_emerald_standard/lang/en_us.json'
    'assets/the_emerald_standard/blockstates/exchange_desk.json'
    'assets/the_emerald_standard/items/exchange_desk.json'
    'assets/the_emerald_standard/items/handbook.json'
    'assets/the_emerald_standard/models/block/exchange_desk.json'
    'assets/the_emerald_standard/models/item/exchange_desk.json'
    'assets/the_emerald_standard/textures/block/exchange_desk_front.png'
    'assets/the_emerald_standard/textures/block/exchange_desk_side.png'
    'assets/the_emerald_standard/textures/block/exchange_desk_top.png'
    'assets/the_emerald_standard/textures/entity/villager/profession/banker.png'
    'assets/the_emerald_standard/textures/entity/zombie_villager/profession/banker.png'
    'data/minecraft/tags/block/mineable/axe.json'
    'data/minecraft/tags/point_of_interest_type/acquirable_job_site.json'
    'data/the_emerald_standard/loot_table/blocks/exchange_desk.json'
    'data/the_emerald_standard/recipe/exchange_desk.json'
    'data/the_emerald_standard/recipe/handbook.json'
    'data/the_emerald_standard/advancement/recipes/misc/exchange_desk.json'
    'data/the_emerald_standard/advancement/recipes/misc/handbook.json'
    'data/the_emerald_standard/advancement/first_banker.json'
)
if [[ "$LOADER" == "fabric" ]]; then
    required+=(
        'fabric.mod.json'
        'com/chedidandrew/emeraldstandard/fabric/EmeraldStandardFabric.class'
        'com/chedidandrew/emeraldstandard/fabric/EmeraldStandardFabricClient.class'
        'com/chedidandrew/emeraldstandard/fabric/EmeraldModMenu.class'
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
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/client/BankerScreenScale.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing responsive Banker screen sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/BankerAmountSelection.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing exact-amount selection sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/TerrainFoundationPlan.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing terrain-foundation planning sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/VillageEntranceApproachPlan.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing village entrance-approach planning sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/VillageMaterializationPolicy.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing village-materialization policy sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/VillageStructureProgression.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing village-structure progression sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/StructureGalleryPlan.java' \
        <<<"$source_listing" \
        || ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/RoofGeometryValidator.java' \
        <<<"$source_listing" \
        || ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/WholeBuildingDetailDensityValidator.java' \
        <<<"$source_listing" \
        || ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/WholeBuildingPresentationValidator.java' \
        <<<"$source_listing" \
        || ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/WholeBuildingRoleReadabilityValidator.java' \
        <<<"$source_listing" \
        || ! grep -Fxq 'com/chedidandrew/emeraldstandard/core/WholeBuildingValidator.java' \
        <<<"$source_listing" \
        || ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/StructureGallery.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing structure-gallery or authored-quality sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/FundConfirmationFingerprint.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing Fund confirmation sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/ContainerDataPacking.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing ContainerData packing sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/BankerMenuPacketCodecSelfTest.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing Banker menu packet-codec self-test sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/ShortPackedContainerData.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing packed ContainerData adapter sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/PlayerOnboarding.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing onboarding Java sources" >&2
    exit 1
fi
if ! grep -Fxq 'com/chedidandrew/emeraldstandard/minecraft/EmeraldHandbook.java' \
        <<<"$source_listing"; then
    echo "$sources_file is missing Emerald Handbook sources" >&2
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

if ! unzip -p "$jar_file" data/the_emerald_standard/advancement/first_banker.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); assert d["criteria"]["opened_banker"]["trigger"] == "minecraft:impossible"'; then
    echo "$jar_file contains an invalid First Banker advancement" >&2
    exit 1
fi

if ! unzip -p "$jar_file" data/the_emerald_standard/recipe/exchange_desk.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); assert d == {"type":"minecraft:crafting_shaped","category":"misc","pattern":[" E ","LBL","PPP"],"key":{"E":"minecraft:emerald","L":"minecraft:leather","B":"minecraft:book","P":"#minecraft:planks"},"result":{"id":"the_emerald_standard:exchange_desk","count":1},"show_notification":True}'; then
    echo "$jar_file contains an invalid Exchange Desk crafting recipe" >&2
    exit 1
fi

if ! unzip -p "$jar_file" data/the_emerald_standard/advancement/recipes/misc/exchange_desk.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); assert d["parent"] == "minecraft:recipes/root"; assert set(d["requirements"][0]) == {"has_the_recipe","has_emerald","has_book"}; assert d["rewards"]["recipes"] == ["the_emerald_standard:exchange_desk"]; assert d["criteria"]["has_emerald"]["trigger"] == "minecraft:inventory_changed"; assert d["criteria"]["has_book"]["trigger"] == "minecraft:inventory_changed"; assert d["criteria"]["has_the_recipe"]["conditions"]["recipe"] == "the_emerald_standard:exchange_desk"'; then
    echo "$jar_file contains an invalid Exchange Desk recipe-book advancement" >&2
    exit 1
fi

if ! unzip -p "$jar_file" assets/the_emerald_standard/items/handbook.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); assert d == {"model":{"type":"minecraft:model","model":"minecraft:item/written_book"}}'; then
    echo "$jar_file contains an invalid handbook item definition" >&2
    exit 1
fi

if ! unzip -p "$jar_file" data/the_emerald_standard/recipe/handbook.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); assert d == {"type":"minecraft:crafting_shapeless","category":"misc","ingredients":["minecraft:book","minecraft:emerald"],"result":{"id":"the_emerald_standard:handbook","count":1},"show_notification":True}'; then
    echo "$jar_file contains an invalid handbook replacement recipe" >&2
    exit 1
fi

if ! unzip -p "$jar_file" data/the_emerald_standard/advancement/recipes/misc/handbook.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); assert d["parent"] == "minecraft:recipes/root"; assert set(d["criteria"]) == {"has_the_recipe","has_emerald","has_book"}; assert set(d["requirements"][0]) == set(d["criteria"]); assert d["rewards"]["recipes"] == ["the_emerald_standard:handbook"]; assert d["criteria"]["has_emerald"]["trigger"] == "minecraft:inventory_changed"; assert d["criteria"]["has_emerald"]["conditions"]["items"] == [{"items":"minecraft:emerald"}]; assert d["criteria"]["has_book"]["trigger"] == "minecraft:inventory_changed"; assert d["criteria"]["has_book"]["conditions"]["items"] == [{"items":"minecraft:book"}]; assert d["criteria"]["has_the_recipe"]["trigger"] == "minecraft:recipe_unlocked"; assert d["criteria"]["has_the_recipe"]["conditions"]["recipe"] == "the_emerald_standard:handbook"'; then
    echo "$jar_file contains an invalid handbook recipe-book advancement" >&2
    exit 1
fi

if ! unzip -p "$jar_file" assets/the_emerald_standard/lang/en_us.json \
        | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); required={"item.the_emerald_standard.handbook","message.the_emerald_standard.handbook_received","message.the_emerald_standard.handbook_inventory_full","book.the_emerald_standard.handbook.cover.title","book.the_emerald_standard.handbook.cover.body","book.the_emerald_standard.handbook.open_contents","book.the_emerald_standard.handbook.nav.contents","book.the_emerald_standard.handbook.nav.previous","book.the_emerald_standard.handbook.nav.next","book.the_emerald_standard.handbook.nav.jump"}; assert required <= d.keys(); assert len([k for k in d if k.startswith("book.the_emerald_standard.handbook.")]) >= 90'; then
    echo "$jar_file is missing required English handbook text" >&2
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
            | "${PYTHON_COMMAND[@]}" -c 'import json,sys; d=json.load(sys.stdin); expected=sys.argv[1]; assert d["id"] == "the_emerald_standard" and d["version"] == expected; assert d["entrypoints"]["modmenu"] == ["com.chedidandrew.emeraldstandard.fabric.EmeraldModMenu"]; assert "modmenu" not in d.get("depends", {}); assert not any("modmenu" in j["file"].lower() for j in d.get("jars", []))' "$VERSION"; then
        echo "$jar_file contains incorrect Fabric identity, version, or optional Mod Menu metadata" >&2
        exit 1
    fi
    if grep -Eq '^com/terraformersmc/modmenu/|[Mm]od[Mm]enu.*\.jar$' <<<"$listing"; then
        echo "$jar_file must not bundle optional Mod Menu" >&2
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
