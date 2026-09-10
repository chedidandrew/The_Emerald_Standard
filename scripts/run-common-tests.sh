#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/common-tests"

rm -rf "$BUILD"
mkdir -p "$BUILD"

mapfile -t MAIN_SOURCES < <(find "$ROOT/common/src/main/java" -name '*.java' -print | sort)
mapfile -t TEST_SOURCES < <(find "$ROOT/common/src/test/java" -name '*.java' -print | sort)

javac --release 21 -d "$BUILD" "${MAIN_SOURCES[@]}"
javac --release 21 -cp "$BUILD" -d "$BUILD" \
    "$ROOT/common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/EmeraldConfig.java"
javac --release 21 -cp "$BUILD" -d "$BUILD" "${TEST_SOURCES[@]}"

java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.EconomyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.PersistenceRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageProsperityRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageExpansionRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageBackgroundRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageFoodSupplyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.BankConstructionRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.TerrainRoadPlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageDashboardPolicyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageArchitectureRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.BlueprintTierSelectionRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.RoofGeometryValidatorRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidatorRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidatorRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidatorRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidatorRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.WholeBuildingValidatorRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.ProjectCatalogCompatibilityRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.GalleryCameraProjectionRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.GalleryCaptureIsolationPlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.StructureGalleryPlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.StructureGalleryReviewPlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageComparisonBiomePlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.Milestone95RegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.LargeWorldStressRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.FinanceRoadmapRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.ScalingAndSpatialIndexRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.debug.DebugReportFilesRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.client.BankerScreenLayoutRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.client.BankerScreenScaleRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.client.BankerTextureUvRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.client.ExchangeDeskModelRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.client.ExchangeDeskRecipeRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.client.HandbookResourceRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureCosmeticLifecycleRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureDistinctivenessWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureDetailWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureIndustryCraftWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureDetailDensityWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructurePresentationWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureGeometrySafetyWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureLightingWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureRoleReadabilityWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureDoodadWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructureRoofGeometryWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.BankWorkstationAccessPolicyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.BankerAmountSelectionRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.BankerConversionRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.BankerDeathRetryQueueRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.BankerLifecycleWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.SmokeServerHarnessRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.CatalogAdmissionPerformanceWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.ContainerDataPackingRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.EmeraldConfigRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.FundConfirmationFingerprintRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.TerrainFoundationPlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.TrailGroundWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.StructureGalleryWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageComparisonGalleryWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageEntranceApproachPlanRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageBankPlacementPolicyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageBankStructureVersionCompatibilityWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageMaterializationPolicyRegressionTest
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageRuntimeStructureSafetyWiringRegressionTest "$ROOT"
java -cp "$BUILD" com.chedidandrew.emeraldstandard.minecraft.VillageStructureProgressionRegressionTest

fabric_version="$(grep '^mod_version=' "$ROOT/fabric/gradle.properties" | cut -d= -f2-)"
neo_version="$(grep '^mod_version=' "$ROOT/neoforge/gradle.properties" | cut -d= -f2-)"
if [[ -z "$fabric_version" || "$fabric_version" != "$neo_version" ]]; then
    echo "Loader version mismatch: Fabric='$fabric_version' NeoForge='$neo_version'" >&2
    exit 1
fi

echo "PASS loader version parity: $fabric_version"

expected_distribution_sha='bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f'
expected_wrapper_sha='497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7'
for loader in fabric neoforge; do
    wrapper_properties="$ROOT/$loader/gradle/wrapper/gradle-wrapper.properties"
    configured_distribution_sha="$(grep '^distributionSha256Sum=' "$wrapper_properties" | cut -d= -f2-)"
    if [[ "$configured_distribution_sha" != "$expected_distribution_sha" ]]; then
        echo "$loader Gradle distribution checksum is missing or unexpected" >&2
        exit 1
    fi
    actual_wrapper_sha="$(sha256sum "$ROOT/$loader/gradle/wrapper/gradle-wrapper.jar" | cut -d' ' -f1)"
    if [[ "$actual_wrapper_sha" != "$expected_wrapper_sha" ]]; then
        echo "$loader Gradle wrapper JAR checksum is unexpected: $actual_wrapper_sha" >&2
        exit 1
    fi
done

echo "PASS pinned Gradle distribution and wrapper checksums"
