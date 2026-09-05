package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Determinism, diversity-deck, orientation, and persistence checks for modular-v1 recipes. */
public final class VillageArchitectureRegressionTest {
    private VillageArchitectureRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testStableVillageCharacter();
        testSilhouettesExhaustBeforeRepeating();
        testStableRecipeAndRotation();
        testArchitecturePersistence();
        testVisualStageCommitIsAtomic();
        testTrailSelectionRotatesWithoutCadenceAliasing();
        System.out.println("PASS modular village architecture regressions");
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
                    VillageProsperityEngine.ProjectType.WAREHOUSE,
                    List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.WAREHOUSE;
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designRotation = 3;
            project.designSignature = recipe.signature();
            project.designStage = 2;
            project.trailAnchorSet = true;
            project.trailAnchorPos = 0x44AAL;
            project.trailMaterializedBlocks = 7;
            project.trailTotalBlocks = 20;
            project.totalBlocks = project.type.nominalBlocks();
            village.projects.add(project);
            village.projectSerial = 1L;

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
                            && loadedProject.trailAnchorSet
                            && loadedProject.trailAnchorPos == 0x44AAL
                            && loadedProject.trailMaterializedBlocks == 7
                            && loadedProject.trailTotalBlocks == 20
                            && !loadedProject.trailMaterializedComplete,
                    "Modular project recipe did not round-trip exactly");
        } finally {
            RegressionTestSupport.deleteTree(directory);
        }
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
                    VillageProsperityEngine.ProjectType.COTTAGE,
                    List.of());
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.COTTAGE;
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
