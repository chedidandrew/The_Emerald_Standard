package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Determinism, immutable catalog, migration, and persistence checks for village architecture. */
public final class VillageArchitectureRegressionTest {
    private VillageArchitectureRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testStableVillageCharacter();
        testBlueprintCatalogAndDeterministicShuffleBag();
        testBlueprintPersistenceAndFormatSeventeenIsolation();
        testBlueprintReservationFreezesPlanHashAtomically();
        testSilhouettesExhaustBeforeRepeating();
        testStableRecipeAndRotation();
        testQualityRetrofitCoverage();
        testArchitecturePersistence();
        testTrailCenterSurfaceMigrationIsIndependentAndDurable();
        testEntranceApproachStateIsVersionedAndDurable();
        testVisualStageCommitIsAtomic();
        testAppendOnlyUpgradeAllowsAnchorOutsideFootprint();
        testTrailSelectionRotatesWithoutCadenceAliasing();
        System.out.println("PASS village architecture regressions");
    }

    private static void testStableVillageCharacter() {
        Set<VillageArchitecture.Character> characters = new HashSet<>();
        for (long value = 1L; value <= 128L; value++) {
            UUID villageId = new UUID(0xA55AA55AA55AA55AL, value);
            VillageArchitecture.Character first = VillageArchitecture.character(villageId);
            require(first == VillageArchitecture.character(villageId),
                    "Village character was not deterministic");
            characters.add(first);
        }
        require(characters.size() == VillageArchitecture.Character.values().length,
                "Village character selection collapsed to fewer than four styles");
    }

    private static void testBlueprintCatalogAndDeterministicShuffleBag() {
        Set<String> catalogIdentities = new HashSet<>();
        int catalogSize = 0;
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            List<VillageArchitecture.BlueprintDescriptor> descriptors =
                    VillageArchitecture.blueprints(type);
            require(!descriptors.isEmpty(), "Blueprint catalog omitted " + type);
            for (VillageArchitecture.BlueprintDescriptor descriptor : descriptors) {
                catalogSize++;
                require(descriptor.type() == type
                                && descriptor.templateRevision() == 2
                                && descriptor.width() > 0
                                && descriptor.depth() > 0
                                && descriptor.height() > 0
                                && catalogIdentities.add(
                                        descriptor.templateId()
                                                + "@"
                                                + descriptor.templateRevision()),
                        "Blueprint descriptor is invalid or duplicated for " + type);
            }
        }
        require(catalogSize == 52,
                "Active gold-master blueprint catalog is not the frozen 52-entry set");

        // New approvals select active revision-2 masters.
        requireEnvelope("cottage_hearth_01", 2, 11, 10, 10, false);
        requireEnvelope("cottage_garden_02", 2, 13, 11, 10, true);
        requireEnvelope("house_cross_01", 2, 15, 13, 14, true);
        requireEnvelope("house_dormer_02", 2, 11, 15, 15, false);
        requireEnvelope("inn_gallery_01", 2, 17, 13, 15, false);
        requireEnvelope("warehouse_bay_01", 2, 17, 11, 11, true);
        requireEnvelope("granary_loft_01", 2, 13, 11, 17, true);
        requireEnvelope("smithy_courtyard_01", 2, 17, 13, 12, false);
        requireEnvelope("mine_headframe_01", 2, 15, 15, 15, true);
        requireEnvelope("market_cloister_01", 2, 17, 17, 12, true);
        requireEnvelope("guard_watch_01", 2, 11, 11, 18, true);
        requireEnvelope("exchange_hall_01", 2, 19, 15, 16, true);

        // The production expansion adds exactly two immutable choices to every economic role.
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.COTTAGE,
                "cottage_longhouse_05", 15, 9, 11,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.COTTAGE,
                "cottage_orchardstead_06", 19, 15, 14,
                VillageArchitecture.BlueprintScale.LARGE, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.HOUSE,
                "house_splitwing_05", 15, 13, 14,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.HOUSE,
                "house_towercourt_06", 21, 19, 20,
                VillageArchitecture.BlueprintScale.LANDMARK, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.INN,
                "inn_tavern_04", 15, 13, 13,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.INN,
                "inn_courtyard_05", 25, 21, 19,
                VillageArchitecture.BlueprintScale.LANDMARK, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.WAREHOUSE,
                "warehouse_wharf_04", 15, 13, 11,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.WAREHOUSE,
                "warehouse_basilica_05", 25, 17, 18,
                VillageArchitecture.BlueprintScale.LANDMARK, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.GRANARY,
                "granary_stilt_04", 13, 11, 15,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.GRANARY,
                "granary_silocomplex_05", 21, 17, 19,
                VillageArchitecture.BlueprintScale.LARGE, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.SMITHY,
                "smithy_corner_04", 15, 13, 12,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.SMITHY,
                "smithy_foundry_05", 23, 19, 19,
                VillageArchitecture.BlueprintScale.LANDMARK, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.MINE_ENTRANCE,
                "mine_drift_04", 15, 13, 13,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.MINE_ENTRANCE,
                "mine_quarry_05", 23, 21, 17,
                VillageArchitecture.BlueprintScale.LARGE, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.MARKET_SQUARE,
                "market_lane_04", 17, 13, 10,
                VillageArchitecture.BlueprintScale.MEDIUM, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.MARKET_SQUARE,
                "market_bazaar_05", 25, 23, 17,
                VillageArchitecture.BlueprintScale.LANDMARK, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.GUARD_POST,
                "guard_gatehouse_04", 15, 11, 16,
                VillageArchitecture.BlueprintScale.LARGE, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.GUARD_POST,
                "guard_citadel_05", 21, 19, 23,
                VillageArchitecture.BlueprintScale.LANDMARK, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                "exchange_loggia_04", 19, 15, 16,
                VillageArchitecture.BlueprintScale.LARGE, true);
        requireActiveEnvelope(VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                "exchange_bourse_05", 27, 21, 22,
                VillageArchitecture.BlueprintScale.LANDMARK, true);

        // Every shipped revision-1 envelope remains addressable for persisted in-progress plans.
        requireEnvelope("cottage_hearth_01", 1, 9, 9, 9, false);
        requireEnvelope("cottage_garden_02", 1, 9, 11, 9, true);
        requireEnvelope("house_cross_01", 1, 11, 11, 12, true);
        requireEnvelope("house_dormer_02", 1, 11, 11, 11, false);
        requireEnvelope("inn_gallery_01", 1, 13, 11, 12, false);
        requireEnvelope("warehouse_bay_01", 1, 13, 9, 10, true);
        requireEnvelope("granary_loft_01", 1, 11, 9, 11, true);
        requireEnvelope("smithy_courtyard_01", 1, 13, 9, 10, false);
        requireEnvelope("mine_headframe_01", 1, 11, 11, 10, true);
        requireEnvelope("market_cloister_01", 1, 15, 15, 8, true);
        requireEnvelope("guard_watch_01", 1, 9, 9, 14, true);
        requireEnvelope("exchange_hall_01", 1, 15, 11, 12, true);

        UUID villageId = UUID.fromString("957af584-cb15-4100-bda8-b3281174b4e1");
        List<VillageArchitecture.ExistingBlueprint> existing = new ArrayList<>();
        VillageArchitecture.BlueprintSelection first = VillageArchitecture.chooseBlueprint(
                villageId,
                1L,
                VillageProsperityEngine.ProjectType.COTTAGE,
                VillageArchitecture.Character.AGRARIAN,
                existing);
        existing.add(existingBlueprint(
                1L, VillageProsperityEngine.ProjectType.COTTAGE, first));
        VillageArchitecture.BlueprintSelection second = VillageArchitecture.chooseBlueprint(
                villageId,
                2L,
                VillageProsperityEngine.ProjectType.COTTAGE,
                VillageArchitecture.Character.AGRARIAN,
                existing);
        require(!first.templateId().equals(second.templateId()),
                "A Cottage blueprint repeated before its catalog was exhausted");
        require(second.equals(VillageArchitecture.chooseBlueprint(
                        villageId,
                        2L,
                        VillageProsperityEngine.ProjectType.COTTAGE,
                        VillageArchitecture.Character.AGRARIAN,
                        existing)),
                "The same project rerolled its Blueprint V2 selection");
        require(VillageArchitecture.isKnownBlueprintSelection(
                        VillageProsperityEngine.ProjectType.COTTAGE,
                        second.templateId(),
                        second.templateRevision(),
                        second.paletteId(),
                        second.dressingId(),
                        second.mirrored(),
                        second.signature()),
                "Blueprint selection signature was not canonical");
    }

    private static void requireEnvelope(
            String templateId,
            int templateRevision,
            int width,
            int depth,
            int height,
            boolean mirrorable) {
        VillageArchitecture.BlueprintDescriptor descriptor =
                VillageArchitecture.requireBlueprint(templateId, templateRevision);
        require(descriptor.width() == width
                        && descriptor.depth() == depth
                        && descriptor.height() == height
                        && descriptor.mirrorable() == mirrorable,
                templateId + "@" + templateRevision
                        + " lost its immutable site envelope or mirror contract");
    }

    private static void requireActiveEnvelope(
            VillageProsperityEngine.ProjectType type,
            String templateId,
            int width,
            int depth,
            int height,
            VillageArchitecture.BlueprintScale scale,
            boolean mirrorable) {
        VillageArchitecture.BlueprintDescriptor descriptor =
                VillageArchitecture.requireBlueprint(templateId, 2);
        require(descriptor.type() == type
                        && descriptor.width() == width
                        && descriptor.depth() == depth
                        && descriptor.height() == height
                        && descriptor.scale() == scale
                        && descriptor.mirrorable() == mirrorable
                        && VillageArchitecture.blueprints(type).contains(descriptor),
                templateId + "@2 lost its active role, scale, envelope, or mirror contract");
    }

    private static VillageArchitecture.ExistingBlueprint existingBlueprint(
            long projectId,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.BlueprintSelection selection) {
        return new VillageArchitecture.ExistingBlueprint(
                projectId,
                type,
                selection.templateId(),
                selection.templateRevision(),
                selection.paletteId(),
                selection.dressingId(),
                selection.mirrored(),
                selection.signature());
    }

    private static void testBlueprintPersistenceAndFormatSeventeenIsolation()
            throws Exception {
        Path directory = Files.createTempDirectory("emerald-blueprint-persistence-");
        try {
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-00000000b201");
            EconomyState state = EconomyState.fresh(2_201L, 0L, 0L);
            state.economicDay = 3L;
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.FORMAL.id();
            VillageArchitecture.BlueprintSelection selection =
                    VillageArchitecture.chooseBlueprint(
                            villageId,
                            1L,
                            VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                            VillageArchitecture.Character.FORMAL,
                            List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.EXCHANGE_HALL;
            project.approvedDay = 1L;
            project.totalBlocks = project.type.nominalBlocks();
            project.designSchema = VillageArchitecture.BLUEPRINT_SCHEMA;
            project.designSeed = selection.seed();
            project.designTemplateId = selection.templateId();
            project.designTemplateRevision = selection.templateRevision();
            project.designPaletteId = selection.paletteId();
            project.designDressingId = selection.dressingId();
            project.designMirrored = selection.mirrored();
            project.designSignature = selection.signature();
            project.designPlanHashVersion = VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION;
            project.designPlanHash = "ab".repeat(32);
            village.projects.add(project);
            village.projectSerial = 1L;

            EconomyState.VillageProject copied = project.copy();
            require(copied.designTemplateId.equals(project.designTemplateId)
                            && copied.designTemplateRevision == project.designTemplateRevision
                            && copied.designPaletteId.equals(project.designPaletteId)
                            && copied.designDressingId.equals(project.designDressingId)
                            && copied.designPlanHashVersion == project.designPlanHashVersion
                            && copied.designPlanHash.equals(project.designPlanHash),
                    "VillageProject.copy lost immutable Blueprint V2 identity");

            Path save = directory.resolve("the_emerald_standard.properties");
            state.save(save);
            EconomyState.VillageProject loaded = EconomyState.load(save, 0L, 0L, 0L)
                    .existingVillage(villageId)
                    .projects
                    .getFirst();
            require(loaded.designSchema.equals(VillageArchitecture.BLUEPRINT_SCHEMA)
                            && loaded.designSeed == selection.seed()
                            && loaded.designTemplateId.equals(selection.templateId())
                            && loaded.designTemplateRevision == selection.templateRevision()
                            && loaded.designPaletteId.equals(selection.paletteId())
                            && loaded.designDressingId.equals(selection.dressingId())
                            && loaded.designMirrored == selection.mirrored()
                            && loaded.designSignature == selection.signature()
                            && loaded.designPlanHashVersion
                                    == VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION
                            && loaded.designPlanHash.equals(project.designPlanHash),
                    "Blueprint V2 identity did not round-trip exactly");

            // A format-17 partial modular project is historical geometry. Loading it must not
            // assign a Blueprint V2 template or change its construction recipe/cursor.
            EconomyState modularState = EconomyState.fresh(2_202L, 0L, 0L);
            modularState.economicDay = 3L;
            EconomyState.VillageRecord modularVillage = modularState.village(villageId);
            modularVillage.architectureCharacter = VillageArchitecture.Character.RUSTIC.id();
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId,
                    1L,
                    VillageProsperityEngine.ProjectType.COTTAGE,
                    List.of());
            EconomyState.VillageProject partial = new EconomyState.VillageProject();
            partial.projectId = 1L;
            partial.type = VillageProsperityEngine.ProjectType.COTTAGE;
            partial.approvedDay = 1L;
            partial.economicProgress = 0.4;
            partial.totalBlocks = partial.type.nominalBlocks();
            partial.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            partial.designSeed = recipe.seed();
            partial.designSilhouette = recipe.silhouette();
            partial.designRoof = recipe.roof();
            partial.designFrontage = recipe.frontage();
            partial.designMirrored = recipe.mirrored();
            partial.designSignature = recipe.signature();
            modularVillage.projects.add(partial);
            modularVillage.projectSerial = 1L;
            Path oldSave = directory.resolve("format-17.properties");
            modularState.save(oldSave);
            java.util.Properties formatSeventeen =
                    RegressionTestSupport.readProperties(oldSave);
            formatSeventeen.setProperty("format", "17");
            formatSeventeen.stringPropertyNames().stream()
                    .filter(key -> key.contains(".design.template_")
                            || key.contains(".design.palette_id")
                            || key.contains(".design.dressing_id")
                            || key.contains(".design.plan_hash"))
                    .toList()
                    .forEach(formatSeventeen::remove);
            RegressionTestSupport.refreshChecksum(formatSeventeen);
            RegressionTestSupport.writeProperties(oldSave, formatSeventeen);
            EconomyState.VillageProject migrated = EconomyState.load(oldSave, 0L, 0L, 0L)
                    .existingVillage(villageId)
                    .projects
                    .getFirst();
            require(migrated.designSchema.equals(VillageArchitecture.MODULAR_SCHEMA)
                            && migrated.designSeed == recipe.seed()
                            && migrated.designSilhouette == recipe.silhouette()
                            && migrated.designRoof == recipe.roof()
                            && migrated.designFrontage == recipe.frontage()
                            && migrated.designMirrored == recipe.mirrored()
                            && migrated.designSignature == recipe.signature()
                            && migrated.economicProgress == 0.4
                            && migrated.materializedBlocks == 0
                            && migrated.designTemplateId.isEmpty()
                            && migrated.designTemplateRevision == 0
                            && migrated.designPaletteId.isEmpty()
                            && migrated.designDressingId.isEmpty()
                            && migrated.designPlanHashVersion == 0
                            && migrated.designPlanHash.isEmpty(),
                    "Format-17 migration rewrote an incomplete modular-v1 project");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void testBlueprintReservationFreezesPlanHashAtomically()
            throws Exception {
        Path directory = Files.createTempDirectory("emerald-blueprint-reservation-");
        try {
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-00000000b202");
            EconomyState state = EconomyState.fresh(2_203L, 0L, 0L);
            state.economicDay = 1L;
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.FORMAL.id();
            VillageArchitecture.BlueprintSelection selection =
                    VillageArchitecture.chooseBlueprint(
                            villageId,
                            1L,
                            VillageProsperityEngine.ProjectType.GUARD_POST,
                            VillageArchitecture.Character.FORMAL,
                            List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            project.approvedDay = 1L;
            project.completedDay = 1L;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            project.designSchema = VillageArchitecture.BLUEPRINT_SCHEMA;
            project.designSeed = selection.seed();
            project.designTemplateId = selection.templateId();
            project.designTemplateRevision = selection.templateRevision();
            project.designPaletteId = selection.paletteId();
            project.designDressingId = selection.dressingId();
            project.designMirrored = selection.mirrored();
            project.designSignature = selection.signature();
            project.designPlanHashVersion = VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION;
            village.projects.add(project);
            village.projectSerial = 1L;
            state.save(directory.resolve("the_emerald_standard.properties"));

            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 0L, 0L, 0L);
            long origin = packBlockPos(20, 64, 20);
            long minimum = packBlockPos(18, 60, 18);
            long maximum = packBlockPos(26, 74, 26);
            long trailAnchor = packBlockPos(14, 64, 20);
            require(!service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            VillageArchitecture.BiomeDialect.PLAINS.id(),
                            3,
                            0,
                            trailAnchor,
                            20,
                            0,
                            0),
                    "Legacy reservation overload admitted a Blueprint V2 project without a hash");
            require(service.villageSnapshot(villageId).village().projects.getFirst().originPos == 0L,
                    "Rejected hashless reservation partially persisted its origin");

            String planHash = "ab".repeat(32);
            require(!service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            VillageArchitecture.BiomeDialect.PLAINS.id(),
                            3,
                            0,
                            trailAnchor,
                            20,
                            0,
                            0,
                            planHash.toUpperCase()),
                    "Blueprint reservation accepted a non-canonical plan hash");
            require(service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            VillageArchitecture.BiomeDialect.PLAINS.id(),
                            3,
                            0,
                            trailAnchor,
                            20,
                            0,
                            0,
                            planHash),
                    "Blueprint reservation could not freeze its canonical plan hash");
            EconomyState.VillageProject reserved = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(reserved.originPos == origin && reserved.designPlanHash.equals(planHash),
                    "Blueprint origin and plan hash were not frozen atomically");

            require(service.deferVillageProjectMaterialization(villageId, 1L, 100L, false),
                    "Unstarted Blueprint V2 reservation could not be safely deferred");
            EconomyState.VillageProject deferred = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(deferred.originPos == 0L && deferred.designPlanHash.equals(planHash),
                    "Safe Blueprint V2 deferral discarded its immutable plan hash");
            require(!service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            VillageArchitecture.BiomeDialect.PLAINS.id(),
                            3,
                            0,
                            trailAnchor,
                            20,
                            0,
                            0,
                            "34".repeat(32)),
                    "A retried Blueprint V2 project accepted changed plan geometry");
            require(service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            VillageArchitecture.BiomeDialect.PLAINS.id(),
                            3,
                            0,
                            trailAnchor,
                            20,
                            0,
                            0,
                            planHash),
                    "A retried Blueprint V2 project rejected its original plan hash");
            require(service.releaseVillageProjectSite(villageId, 1L),
                    "Blueprint V2 reservation could not use the safe release lifecycle");
            require(service.villageSnapshot(villageId)
                            .village()
                            .projects
                            .getFirst()
                            .designPlanHash
                            .equals(planHash),
                    "Blueprint V2 release discarded its immutable plan hash");
            require(service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            VillageArchitecture.BiomeDialect.PLAINS.id(),
                            3,
                            0,
                            trailAnchor,
                            20,
                            0,
                            0,
                            planHash)
                            && service.updateVillageProjectMaterialization(
                                    villageId, 1L, 100, 100, true, false)
                            && service.updateVillageProjectTrailMaterialization(
                                    villageId, 1L, 5, 20, false),
                    "Blueprint V2 could not use managed construction or trail progress");
            long expandedMinimum = packBlockPos(17, 59, 17);
            long expandedMaximum = packBlockPos(27, 75, 27);
            require(service.commitVillageProjectVisualStageUpgrade(
                            villageId,
                            1L,
                            1,
                            100,
                            120,
                            expandedMinimum,
                            expandedMaximum),
                    "Blueprint V2 could not commit an append-only visual stage");
            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 120, 120, true, false),
                    "Blueprint V2 visual-stage suffix could not complete");
            require(!service.commitVillageProjectQualityUpgrade(
                            villageId,
                            1L,
                            1,
                            1,
                            120,
                            140,
                            expandedMinimum,
                            expandedMaximum),
                    "Blueprint V2 incorrectly entered the modular-only quality retrofit path");

            EconomyState.VillageProject reloaded = EconomyState.load(
                            directory.resolve("the_emerald_standard.properties"),
                            0L,
                            0L,
                            0L)
                    .existingVillage(villageId)
                    .projects
                    .getFirst();
            require(reloaded.designPlanHash.equals(planHash)
                            && reloaded.originPos == origin
                            && reloaded.designStage == 1,
                    "Reserved Blueprint V2 hash or stage did not survive restart");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void testSilhouettesExhaustBeforeRepeating() {
        UUID villageId = UUID.fromString("7f71f6d6-ceae-acf7-1de3-ed5c81be0041");
        List<VillageArchitecture.ExistingDesign> existing = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            Set<Integer> silhouettes = new HashSet<>();
            for (int index = 0; index < VillageArchitecture.SILHOUETTE_COUNT; index++) {
                long projectId = round * VillageArchitecture.SILHOUETTE_COUNT + index + 1L;
                VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                        villageId,
                        projectId,
                        VillageProsperityEngine.ProjectType.COTTAGE,
                        existing);
                silhouettes.add(recipe.silhouette());
                existing.add(new VillageArchitecture.ExistingDesign(
                        VillageProsperityEngine.ProjectType.COTTAGE,
                        recipe.silhouette(),
                        recipe.roof(),
                        recipe.frontage(),
                        recipe.mirrored(),
                        recipe.signature()));
            }
            require(silhouettes.size() == VillageArchitecture.SILHOUETTE_COUNT,
                    "A cottage silhouette repeated before the authored pool was exhausted");
        }
    }

    private static void testStableRecipeAndRotation() {
        UUID villageId = UUID.fromString("53cd5416-963d-4afb-b80a-2034b9e650c9");
        VillageArchitecture.Recipe first = VillageArchitecture.choose(
                villageId,
                17L,
                VillageProsperityEngine.ProjectType.SMITHY,
                List.of());
        VillageArchitecture.Recipe repeated = VillageArchitecture.choose(
                villageId,
                17L,
                VillageProsperityEngine.ProjectType.SMITHY,
                List.of());
        require(first.equals(repeated), "The same project rerolled its modular recipe");
        require(first.signature() == VillageArchitecture.signature(
                        VillageProsperityEngine.ProjectType.SMITHY,
                        first.silhouette(),
                        first.roof(),
                        first.frontage(),
                        first.mirrored()),
                "Saved recipe signature was not canonical");

        require(VillageArchitecture.rotationToward(20, 0, 0, 0) == 3,
                "An eastern lot did not face west toward the village");
        require(VillageArchitecture.rotationToward(-20, 0, 0, 0) == 1,
                "A western lot did not face east toward the village");
        require(VillageArchitecture.rotationToward(0, 20, 0, 0) == 0,
                "A southern lot did not face north toward the village");
        require(VillageArchitecture.rotationToward(0, -20, 0, 0) == 2,
                "A northern lot did not face south toward the village");
    }

    private static void testQualityRetrofitCoverage() {
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            boolean expected = type == VillageProsperityEngine.ProjectType.GUARD_POST
                    || type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
            require(VillageArchitecture.hasQualityRetrofit(type) == expected,
                    "Quality retrofit metadata coverage drifted for " + type);
        }
    }

    private static void testArchitecturePersistence() throws Exception {
        Path directory = Files.createTempDirectory("emerald-architecture-");
        try {
            EconomyState state = EconomyState.fresh(44L, 0L, 0L);
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000004410");
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.MERCANTILE.id();
            village.architectureDialect = VillageArchitecture.BiomeDialect.TAIGA.id();
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId,
                    1L,
                    VillageProsperityEngine.ProjectType.GUARD_POST,
                    List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designRotation = 3;
            project.designSignature = recipe.signature();
            project.designStage = 2;
            project.designQualityStage = 1;
            project.trailAnchorSet = true;
            project.trailAnchorPos = 0x44AAL;
            project.trailMaterializedBlocks = 7;
            project.trailTotalBlocks = 20;
            project.trailCenterSurfaceVersion = 0;
            project.trailCenterSurfaceMigrationCursor = 3;
            project.trailCenterSurfaceMigrationTotalCells = 5;
            project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            project.entranceApproachStepCount = 2;
            project.entranceApproachCursor = 3;
            project.entranceApproachTotalCells = 5;
            project.totalBlocks = project.type.nominalBlocks();
            village.projects.add(project);
            village.projectSerial = 1L;

            EconomyState.VillageProject copied = project.copy();
            require(copied.entranceApproachVersion == EconomyState.ENTRANCE_APPROACH_VERSION
                            && copied.entranceApproachStepCount == 2
                            && copied.entranceApproachCursor == 3
                            && copied.entranceApproachTotalCells == 5
                            && !copied.entranceApproachComplete,
                    "VillageProject.copy lost its frozen entrance-approach state");

            Path save = directory.resolve("the_emerald_standard.properties");
            state.save(save);
            EconomyState loaded = EconomyState.load(save, 0L, 0L, 0L);
            EconomyState.VillageRecord loadedVillage = loaded.existingVillage(villageId);
            EconomyState.VillageProject loadedProject = loadedVillage.projects.getFirst();
            require(loadedVillage.architectureCharacter.equals(village.architectureCharacter)
                            && loadedVillage.architectureDialect.equals(village.architectureDialect),
                    "Village architectural DNA did not round-trip");
            require(loadedProject.designSchema.equals(VillageArchitecture.MODULAR_SCHEMA)
                            && loadedProject.designSeed == recipe.seed()
                            && loadedProject.designSilhouette == recipe.silhouette()
                            && loadedProject.designRoof == recipe.roof()
                            && loadedProject.designFrontage == recipe.frontage()
                            && loadedProject.designMirrored == recipe.mirrored()
                            && loadedProject.designRotation == 3
                            && loadedProject.designSignature == recipe.signature()
                            && loadedProject.designStage == 2
                            && loadedProject.designQualityStage == 1
                            && loadedProject.trailAnchorSet
                            && loadedProject.trailAnchorPos == 0x44AAL
                            && loadedProject.trailMaterializedBlocks == 7
                            && loadedProject.trailTotalBlocks == 20
                            && loadedProject.trailCenterSurfaceVersion == 0
                            && loadedProject.trailCenterSurfaceMigrationCursor == 3
                            && loadedProject.trailCenterSurfaceMigrationTotalCells == 5
                            && loadedProject.entranceApproachVersion
                                    == EconomyState.ENTRANCE_APPROACH_VERSION
                            && loadedProject.entranceApproachStepCount == 2
                            && loadedProject.entranceApproachCursor == 3
                            && loadedProject.entranceApproachTotalCells == 5
                            && !loadedProject.entranceApproachComplete
                            && !loadedProject.trailMaterializedComplete,
                    "Modular project recipe did not round-trip exactly");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void testTrailCenterSurfaceMigrationIsIndependentAndDurable()
            throws Exception {
        Path directory = Files.createTempDirectory("emerald-trail-surface-");
        try {
            EconomyState state = EconomyState.fresh(4401L, 0L, 0L);
            state.economicDay = 2L;
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000004401");
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.RUSTIC.id();
            village.architectureDialect = VillageArchitecture.BiomeDialect.PLAINS.id();
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId,
                    1L,
                    VillageProsperityEngine.ProjectType.GUARD_POST,
                    List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            project.approvedDay = 1L;
            project.completedDay = 2L;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.originPos = packBlockPos(20, 64, 20);
            project.boundsMinPos = packBlockPos(18, 60, 18);
            project.boundsMaxPos = packBlockPos(30, 76, 30);
            project.materializedBlocks = 100;
            project.totalBlocks = 100;
            project.materializedComplete = true;
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designSignature = recipe.signature();
            project.trailAnchorSet = true;
            project.trailAnchorPos = packBlockPos(14, 64, 20);
            project.trailMaterializedBlocks = 19;
            project.trailTotalBlocks = 20;
            project.trailMaterializedComplete = false;
            project.trailCenterSurfaceVersion = 0;
            village.projects.add(project);
            EconomyState.VillageProject emptyMigration = project.copy();
            emptyMigration.projectId = 2L;
            emptyMigration.originPos = packBlockPos(50, 64, 50);
            emptyMigration.boundsMinPos = packBlockPos(48, 60, 48);
            emptyMigration.boundsMaxPos = packBlockPos(60, 76, 60);
            emptyMigration.trailAnchorPos = packBlockPos(44, 64, 50);
            emptyMigration.trailMaterializedBlocks = 20;
            emptyMigration.trailMaterializedComplete = true;
            village.projects.add(emptyMigration);
            village.projectSerial = 2L;
            Path save = directory.resolve("the_emerald_standard.properties");
            state.save(save);

            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 0L, 0L, 0L);
            require(service.initializeVillageProjectTrailCenterSurfaceMigration(
                            villageId, 2L, 0, 0)
                            && service.villageSnapshot(villageId)
                                            .village()
                                            .projects
                                            .get(1)
                                            .trailCenterSurfaceVersion
                                    == EconomyState.TRAIL_CENTER_SURFACE_VERSION,
                    "An empty deterministic migration did not complete safely");
            require(!service.initializeVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 5),
                    "Center resurfacing began before the frozen ordinary road completed");
            require(service.updateVillageProjectTrailMaterialization(
                            villageId, 1L, 20, 20, true),
                    "Could not finish the independent ordinary road cursor");
            require(!service.initializeVillageProjectTrailCenterSurfaceMigration(
                            villageId,
                            1L,
                            0,
                            EconomyState.MAX_TRAIL_CENTER_SURFACE_MIGRATION_CELLS + 1),
                    "An oversized center-surface scan was accepted");
            require(service.initializeVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 5),
                    "Could not freeze the center-surface migration target");
            require(!service.initializeVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 5),
                    "An active center-surface migration was initialized twice");
            require(!service.updateVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 0, 1, 4, false)
                            && !service.updateVillageProjectTrailCenterSurfaceMigration(
                                    villageId, 1L, 0, 1, 2, 5, false),
                    "A stale cursor or changed frozen migration total was accepted");
            require(service.updateVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 0, 2, 5, false),
                    "Could not advance the independent center-surface cursor");
            EconomyState.VillageProject active = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(active.trailCenterSurfaceVersion == 0
                            && active.trailCenterSurfaceMigrationCursor == 2
                            && active.trailCenterSurfaceMigrationTotalCells == 5
                            && active.trailMaterializedBlocks == 20
                            && active.trailTotalBlocks == 20
                            && active.trailMaterializedComplete,
                    "Center resurfacing changed or reused the ordinary road cursor");

            EconomyService restarted = new EconomyService();
            restarted.startWithSeed(directory, 0L, 0L, 0L);
            EconomyState.VillageProject resumed = restarted.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(resumed.trailCenterSurfaceVersion == 0
                            && resumed.trailCenterSurfaceMigrationCursor == 2
                            && resumed.trailCenterSurfaceMigrationTotalCells == 5,
                    "A restart did not resume the frozen center-surface scan exactly");
            require(restarted.updateVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 2, 5, 5, true),
                    "Could not complete the one-time center-surface migration");
            require(!restarted.updateVillageProjectTrailCenterSurfaceMigration(
                            villageId, 1L, 0, 0, 5, 5, true),
                    "A completed migration could be replayed as a repair pass");
            EconomyState.VillageProject complete = EconomyState.load(save, 0L, 0L, 0L)
                    .existingVillage(villageId)
                    .projects
                    .getFirst();
            require(complete.trailCenterSurfaceVersion
                                    == EconomyState.TRAIL_CENTER_SURFACE_VERSION
                            && complete.trailCenterSurfaceMigrationCursor == 0
                            && complete.trailCenterSurfaceMigrationTotalCells == 0
                            && complete.trailMaterializedBlocks == 20
                            && complete.trailTotalBlocks == 20,
                    "Migration completion was not durable or changed ordinary road progress");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void testEntranceApproachStateIsVersionedAndDurable()
            throws Exception {
        Path directory = Files.createTempDirectory("emerald-entrance-approach-");
        try {
            EconomyState state = EconomyState.fresh(4_402L, 0L, 0L);
            state.economicDay = 2L;
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000004402");
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.RUSTIC.id();
            village.architectureDialect = VillageArchitecture.BiomeDialect.PLAINS.id();

            VillageArchitecture.Recipe firstRecipe = VillageArchitecture.choose(
                    villageId,
                    1L,
                    VillageProsperityEngine.ProjectType.GUARD_POST,
                    List.of());
            EconomyState.VillageProject existing = new EconomyState.VillageProject();
            existing.projectId = 1L;
            existing.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            existing.approvedDay = 1L;
            existing.completedDay = 2L;
            existing.economicProgress = 1.0;
            existing.economicComplete = true;
            existing.originPos = packBlockPos(20, 64, 20);
            existing.boundsMinPos = packBlockPos(18, 60, 18);
            existing.boundsMaxPos = packBlockPos(30, 76, 30);
            existing.materializedBlocks = 100;
            existing.totalBlocks = 100;
            existing.materializedComplete = true;
            existing.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            existing.designSeed = firstRecipe.seed();
            existing.designSilhouette = firstRecipe.silhouette();
            existing.designRoof = firstRecipe.roof();
            existing.designFrontage = firstRecipe.frontage();
            existing.designMirrored = firstRecipe.mirrored();
            existing.designSignature = firstRecipe.signature();
            existing.trailAnchorSet = true;
            existing.trailAnchorPos = packBlockPos(14, 64, 20);
            existing.trailMaterializedBlocks = 7;
            existing.trailTotalBlocks = 20;
            existing.trailMaterializedComplete = false;
            existing.trailCenterSurfaceVersion = 0;
            village.projects.add(existing);

            VillageArchitecture.Recipe secondRecipe = VillageArchitecture.choose(
                    villageId,
                    2L,
                    VillageProsperityEngine.ProjectType.GUARD_POST,
                    List.of());
            EconomyState.VillageProject unreserved = new EconomyState.VillageProject();
            unreserved.projectId = 2L;
            unreserved.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            unreserved.approvedDay = 1L;
            unreserved.completedDay = 2L;
            unreserved.economicProgress = 1.0;
            unreserved.economicComplete = true;
            unreserved.totalBlocks = unreserved.type.nominalBlocks();
            unreserved.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            unreserved.designSeed = secondRecipe.seed();
            unreserved.designSilhouette = secondRecipe.silhouette();
            unreserved.designRoof = secondRecipe.roof();
            unreserved.designFrontage = secondRecipe.frontage();
            unreserved.designMirrored = secondRecipe.mirrored();
            unreserved.designSignature = secondRecipe.signature();
            village.projects.add(unreserved);
            village.projectSerial = 2L;

            EconomyState invalid = state.copy();
            EconomyState.VillageProject invalidApproach = invalid
                    .existingVillage(villageId)
                    .projects
                    .getFirst();
            invalidApproach.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            invalidApproach.entranceApproachStepCount = 2;
            invalidApproach.entranceApproachCursor = 4;
            invalidApproach.entranceApproachTotalCells = 4;
            invalidApproach.entranceApproachComplete = false;
            RegressionTestSupport.requireValidationFailure(
                    invalid,
                    "An approach whose cursor reached its total remained incomplete");

            Path save = directory.resolve("the_emerald_standard.properties");
            state.save(save);
            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 0L, 0L, 0L);

            require(!service.initializeVillageProjectEntranceApproach(
                            villageId,
                            1L,
                            0,
                            1,
                            EconomyState.MAX_ENTRANCE_APPROACH_CELLS + 1),
                    "An oversized entrance approach was accepted");
            require(service.initializeVillageProjectEntranceApproach(
                            villageId, 1L, 0, 3, 6),
                    "Could not freeze a pending legacy entrance approach");
            require(!service.initializeVillageProjectEntranceApproach(
                            villageId, 1L, 0, 3, 6),
                    "A frozen entrance approach was initialized twice");
            EconomyState.VillageProject initialized = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(initialized.entranceApproachVersion
                                    == EconomyState.ENTRANCE_APPROACH_VERSION
                            && initialized.entranceApproachStepCount == 3
                            && initialized.entranceApproachCursor == 0
                            && initialized.entranceApproachTotalCells == 6
                            && !initialized.entranceApproachComplete
                            && initialized.trailMaterializedBlocks == 7
                            && !initialized.trailMaterializedComplete
                            && initialized.trailCenterSurfaceVersion == 0,
                    "Entrance planning waited for or changed independent road work");
            require(!service.updateVillageProjectEntranceApproach(
                            villageId, 1L, 1, 1, 2, 6, false)
                            && !service.updateVillageProjectEntranceApproach(
                                    villageId, 1L, 1, 0, 2, 5, false)
                            && !service.updateVillageProjectEntranceApproach(
                                    villageId, 1L, 1, 0, 2, 6, true),
                    "A stale cursor, changed total, or premature completion was accepted");
            require(service.updateVillageProjectEntranceApproach(
                            villageId, 1L, 1, 0, 2, 6, false),
                    "Could not advance the frozen entrance approach");

            EconomyService restarted = new EconomyService();
            restarted.startWithSeed(directory, 0L, 0L, 0L);
            EconomyState.VillageProject resumed = restarted.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(resumed.entranceApproachCursor == 2
                            && resumed.entranceApproachTotalCells == 6
                            && !resumed.entranceApproachComplete,
                    "An entrance cursor advance was not persisted immediately");
            require(!restarted.waiveVillageProjectEntranceApproach(
                            villageId, 1L, 1, 0, 6),
                    "A stale entrance cursor waived unseen cells");
            require(restarted.waiveVillageProjectEntranceApproach(
                            villageId, 1L, 1, 2, 6),
                    "Could not safely waive an obstructed entrance suffix");
            require(!restarted.waiveVillageProjectEntranceApproach(
                            villageId, 1L, 1, 6, 6),
                    "A completed entrance approach could be replayed");
            EconomyState.VillageProject waived = EconomyState.load(save, 0L, 0L, 0L)
                    .existingVillage(villageId)
                    .projects
                    .getFirst();
            require(waived.entranceApproachVersion
                                    == EconomyState.ENTRANCE_APPROACH_VERSION
                            && waived.entranceApproachStepCount == 3
                            && waived.entranceApproachCursor == 6
                            && waived.entranceApproachTotalCells == 6
                            && waived.entranceApproachComplete,
                    "A safely waived entrance approach was not durable");

            long origin = packBlockPos(50, 64, 50);
            long minimum = packBlockPos(48, 60, 48);
            long maximum = packBlockPos(60, 76, 60);
            require(restarted.reserveVillageProjectSite(
                            villageId,
                            2L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            village.architectureDialect,
                            0,
                            0,
                            packBlockPos(44, 64, 50),
                            20),
                    "The backward-compatible flat-entrance reservation failed");
            EconomyState.VillageProject flat = restarted.villageSnapshot(villageId)
                    .village()
                    .projects
                    .get(1);
            require(flat.entranceApproachVersion == EconomyState.ENTRANCE_APPROACH_VERSION
                            && flat.entranceApproachStepCount == 0
                            && flat.entranceApproachCursor == 0
                            && flat.entranceApproachTotalCells == 0
                            && flat.entranceApproachComplete,
                    "The old reservation overload did not record a flat completed approach");
            require(restarted.releaseVillageProjectSite(villageId, 2L),
                    "Could not release an unstarted flat-entrance reservation");
            requireEntranceApproachReset(
                    restarted.villageSnapshot(villageId).village().projects.get(1),
                    "Site release retained a stale entrance plan");

            require(restarted.reserveVillageProjectSite(
                            villageId,
                            2L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            village.architectureDialect,
                            0,
                            0,
                            packBlockPos(44, 64, 50),
                            20,
                            3,
                            6),
                    "The versioned entrance reservation failed");
            EconomyState.VillageProject reserved = restarted.villageSnapshot(villageId)
                    .village()
                    .projects
                    .get(1);
            require(reserved.entranceApproachVersion
                                    == EconomyState.ENTRANCE_APPROACH_VERSION
                            && reserved.entranceApproachStepCount == 3
                            && reserved.entranceApproachCursor == 0
                            && reserved.entranceApproachTotalCells == 6
                            && !reserved.entranceApproachComplete,
                    "The versioned reservation did not freeze its approach plan");
            require(restarted.deferVillageProjectMaterialization(
                            villageId, 2L, 100L),
                    "Could not defer an unstarted entrance reservation");
            requireEntranceApproachReset(
                    restarted.villageSnapshot(villageId).village().projects.get(1),
                    "Unstarted deferral retained a stale entrance plan");

            require(restarted.reserveVillageProjectSite(
                            villageId,
                            2L,
                            origin,
                            minimum,
                            maximum,
                            100,
                            village.architectureDialect,
                            0,
                            0,
                            packBlockPos(44, 64, 50),
                            20,
                            3,
                            6)
                            && restarted.updateVillageProjectMaterialization(
                                    villageId, 2L, 100, 100, true, false),
                    "Could not complete a project before relocation reset coverage");
            require(restarted.relocateDestroyedVillageProject(villageId, 2L, 200L),
                    "Could not relocate a completed project with an entrance plan");
            requireEntranceApproachReset(
                    restarted.villageSnapshot(villageId).village().projects.get(1),
                    "Relocation retained the retired lot's entrance plan");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void requireEntranceApproachReset(
            EconomyState.VillageProject project, String message) {
        require(project.entranceApproachVersion == 0
                        && project.entranceApproachStepCount == 0
                        && project.entranceApproachCursor == 0
                        && project.entranceApproachTotalCells == 0
                        && !project.entranceApproachComplete,
                message);
    }

    private static void testVisualStageCommitIsAtomic() throws Exception {
        Path directory = Files.createTempDirectory("emerald-architecture-stage-");
        try {
            EconomyState state = EconomyState.fresh(45L, 0L, 0L);
            state.economicDay = 2L;
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000004511");
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.RUSTIC.id();
            village.architectureDialect = VillageArchitecture.BiomeDialect.SAVANNA.id();
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId,
                    1L,
                    VillageProsperityEngine.ProjectType.GUARD_POST,
                    List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            project.approvedDay = 1L;
            project.completedDay = 2L;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designSignature = recipe.signature();
            village.projectSerial = 1L;
            village.projects.add(project);
            state.save(directory.resolve("the_emerald_standard.properties"));

            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 0L, 0L, 0L);
            long origin = packBlockPos(20, 64, 20);
            long initialMinimum = packBlockPos(18, 60, 18);
            long initialMaximum = packBlockPos(30, 76, 30);
            require(service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            initialMinimum,
                            initialMaximum,
                            100,
                            village.architectureDialect,
                            1,
                            0,
                            packBlockPos(14, 64, 20),
                            20),
                    "Could not reserve a frozen modular baseline");
            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 100, 100, true, false),
                    "Could not complete the frozen modular baseline");
            require(!service.updateVillageProjectTrailMaterialization(
                            villageId, 1L, 1, 19, false),
                    "A road cursor changed its frozen route length");
            require(service.updateVillageProjectTrailMaterialization(
                            villageId, 1L, 7, 20, false),
                    "Could not advance the independent road cursor");
            EconomyState.VillageProject roadProgress = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(roadProgress.trailMaterializedBlocks == 7
                            && !roadProgress.trailMaterializedComplete
                            && roadProgress.materializedComplete,
                    "Road progress altered the building's authoritative completion state");
            require(service.updateVillageProjectTrailMaterialization(
                            villageId, 1L, 20, 20, true),
                    "Could not establish a completed narrow road for append-only retrofit");
            require(service.extendVillageProjectTrailTarget(villageId, 1L, 20, 54),
                    "A compatible three-wide road suffix could not extend the frozen target");
            require(!service.extendVillageProjectTrailTarget(villageId, 1L, 20, 60)
                            && !service.extendVillageProjectTrailTarget(villageId, 1L, 54, 40),
                    "A stale or shrinking road target replaced its persisted route");
            EconomyState.VillageProject widenedRoad = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(widenedRoad.trailMaterializedBlocks == 20
                            && widenedRoad.trailTotalBlocks == 54
                            && !widenedRoad.trailMaterializedComplete
                            && widenedRoad.materializedComplete,
                    "Extending a road target changed its frozen prefix or building authority");

            long upgradedMinimum = packBlockPos(17, 59, 17);
            long upgradedMaximum = packBlockPos(31, 78, 31);
            require(service.commitVillageProjectVisualStageUpgrade(
                            villageId,
                            1L,
                            1,
                            100,
                            120,
                            upgradedMinimum,
                            upgradedMaximum),
                    "Could not atomically commit a preflighted visual-stage suffix");
            EconomyState.VillageProject committed = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(committed.designStage == 1
                            && committed.totalBlocks == 120
                            && committed.materializedBlocks == 100
                            && !committed.materializedComplete,
                    "Visual stage and target length were not committed together");
            require(!service.updateVillageProjectMaterialization(
                            villageId, 1L, 101, 101, true, false),
                    "A partial suffix changed the committed template length");
            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 101, 120, true, false),
                    "Could not record partial suffix progress");
            require(!service.villageSnapshot(villageId)
                            .village()
                            .projects
                            .getFirst()
                            .materializedComplete,
                    "A partial suffix was incorrectly marked complete");

            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 120, 120, true, false),
                    "Could not finish the committed visual-stage suffix");
            long qualityMinimum = packBlockPos(16, 58, 16);
            long qualityMaximum = packBlockPos(32, 79, 32);
            require(service.commitVillageProjectQualityUpgrade(
                            villageId,
                            1L,
                            1,
                            1,
                            120,
                            140,
                            qualityMinimum,
                            qualityMaximum),
                    "A same-stage append-only quality retrofit was rejected");
            EconomyState.VillageProject qualityCommitted = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(qualityCommitted.designStage == 1
                            && qualityCommitted.designQualityStage == 1
                            && qualityCommitted.totalBlocks == 140
                            && qualityCommitted.materializedBlocks == 120
                            && !qualityCommitted.materializedComplete,
                    "Quality insertion stage and frozen suffix were not committed together");
            require(!service.commitVillageProjectQualityUpgrade(
                            villageId,
                            1L,
                            1,
                            1,
                            120,
                            150,
                            qualityMinimum,
                            qualityMaximum),
                    "A quality retrofit could be committed twice");

            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 140, 140, true, false),
                    "Could not finish the quality-retrofit suffix");
            require(service.commitVillageProjectVisualStageUpgrade(
                            villageId,
                            1L,
                            2,
                            140,
                            160,
                            qualityMinimum,
                            qualityMaximum),
                    "A later visual stage could not append after the quality suffix");
            EconomyState.VillageProject laterStage = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(laterStage.designStage == 2
                            && laterStage.designQualityStage == 1
                            && laterStage.materializedBlocks == 140
                            && laterStage.totalBlocks == 160,
                    "Later stage upgrade lost the durable quality insertion point");

            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 160, 160, true, false),
                    "Could not finish the post-quality visual stage");
            require(service.relocateDestroyedVillageProject(villageId, 1L, 600L),
                    "A destroyed quality-upgraded project could not retire its old lot");
            EconomyState.VillageProject retired = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(retired.originPos == 0L
                            && retired.designStage == 0
                            && retired.designQualityStage == 0
                            && retired.relocationPending,
                    "A replacement with no surviving prefix retained an incompatible stage");

            long replacementOrigin = packBlockPos(60, 64, 60);
            long replacementMinimum = packBlockPos(58, 60, 58);
            long replacementMaximum = packBlockPos(70, 76, 70);
            require(service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            replacementOrigin,
                            replacementMinimum,
                            replacementMaximum,
                            110,
                            village.architectureDialect,
                            0,
                            0,
                            packBlockPos(54, 64, 60),
                            24),
                    "A retired quality project could not reserve a stage-zero replacement");
            EconomyState.VillageProject replacement = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(replacement.designStage == 0
                            && replacement.designQualityStage == 0
                            && replacement.originPos == replacementOrigin,
                    "Stage-zero replacement lost its compatible quality insertion point");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void testAppendOnlyUpgradeAllowsAnchorOutsideFootprint() throws Exception {
        Path directory = Files.createTempDirectory("emerald-architecture-anchor-bounds-");
        try {
            EconomyState state = EconomyState.fresh(46L, 0L, 0L);
            state.economicDay = 2L;
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000004612");
            EconomyState.VillageRecord village = state.village(villageId);
            village.architectureCharacter = VillageArchitecture.Character.MERCANTILE.id();
            village.architectureDialect = VillageArchitecture.BiomeDialect.PLAINS.id();
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId,
                    1L,
                    VillageProsperityEngine.ProjectType.MINE_ENTRANCE,
                    List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
            project.approvedDay = 1L;
            project.completedDay = 2L;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designSignature = recipe.signature();
            village.projectSerial = 1L;
            village.projects.add(project);
            state.save(directory.resolve("the_emerald_standard.properties"));

            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 0L, 0L, 0L);
            long origin = packBlockPos(20, 64, 20);
            // A template anchor is not necessarily an authored block. Mirror the shipped Mine,
            // whose exact footprint begins one block beyond its origin on the local Z axis.
            long initialMinimum = packBlockPos(18, 60, 21);
            long initialMaximum = packBlockPos(30, 76, 30);
            require(service.reserveVillageProjectSite(
                            villageId,
                            1L,
                            origin,
                            initialMinimum,
                            initialMaximum,
                            100,
                            village.architectureDialect,
                            3,
                            0,
                            packBlockPos(14, 64, 20),
                            20),
                    "Could not reserve a Mine whose anchor sits outside its exact footprint");
            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 100, 100, true, false),
                    "Could not complete the anchor-offset Mine baseline");

            long qualityMinimum = packBlockPos(17, 59, 21);
            long qualityMaximum = packBlockPos(31, 78, 31);
            require(!service.commitVillageProjectQualityUpgrade(
                            villageId,
                            1L,
                            0,
                            0,
                            100,
                            120,
                            packBlockPos(19, 59, 21),
                            qualityMaximum),
                    "A quality suffix discarded part of the frozen Mine footprint");
            require(service.commitVillageProjectQualityUpgrade(
                            villageId,
                            1L,
                            0,
                            0,
                            100,
                            120,
                            qualityMinimum,
                            qualityMaximum),
                    "A quality suffix was rejected because the anchor is outside its footprint");
            require(service.updateVillageProjectMaterialization(
                            villageId, 1L, 120, 120, true, false),
                    "Could not finish the anchor-offset Mine quality suffix");

            long stageMinimum = packBlockPos(16, 58, 21);
            long stageMaximum = packBlockPos(32, 79, 32);
            require(!service.commitVillageProjectVisualStageUpgrade(
                            villageId,
                            1L,
                            1,
                            120,
                            140,
                            packBlockPos(18, 58, 21),
                            stageMaximum),
                    "A visual suffix discarded part of the frozen Mine footprint");
            require(service.commitVillageProjectVisualStageUpgrade(
                            villageId,
                            1L,
                            1,
                            120,
                            140,
                            stageMinimum,
                            stageMaximum),
                    "A visual suffix was rejected because the anchor is outside its footprint");
            EconomyState.VillageProject upgraded = service.villageSnapshot(villageId)
                    .village()
                    .projects
                    .getFirst();
            require(upgraded.designStage == 1
                            && upgraded.designQualityStage == 0
                            && upgraded.materializedBlocks == 120
                            && upgraded.totalBlocks == 140
                            && upgraded.boundsMinPos == stageMinimum
                            && upgraded.boundsMaxPos == stageMaximum,
                    "Anchor-offset append-only upgrades did not preserve their frozen prefix");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
    }

    private static void testTrailSelectionRotatesWithoutCadenceAliasing() {
        EconomyState.VillageRecord village = new EconomyState.VillageRecord();
        for (long projectId = 1L; projectId <= 4L; projectId++) {
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = projectId;
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.economicComplete = true;
            project.materializedComplete = true;
            project.originPos = projectId;
            project.trailAnchorSet = true;
            project.trailTotalBlocks = 20;
            village.projects.add(project);
        }

        Set<Long> visited = new HashSet<>();
        for (long cadenceOrdinal = 0L; cadenceOrdinal < 4L; cadenceOrdinal++) {
            visited.add(village.nextTrailProject(cadenceOrdinal).projectId);
        }
        require(visited.size() == 4,
                "An unloaded public trail could starve later completed projects");

        village.projects.get(1).trailMaterializedComplete = true;
        for (long cadenceOrdinal = 0L; cadenceOrdinal < 12L; cadenceOrdinal++) {
            require(village.nextTrailProject(cadenceOrdinal).projectId != 2L,
                    "Completed public trails remained in the rotating queue");
        }
        require(village.nextTrailProject(-1L) != null,
                "A negative staggered cadence ordinal broke deterministic trail selection");
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
