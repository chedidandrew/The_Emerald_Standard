package com.chedidandrew.emeraldstandard.minecraft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Regression coverage for bounded/fair village materialization policy. */
public final class VillageMaterializationPolicyRegressionTest {
    private VillageMaterializationPolicyRegressionTest() {
    }

    public static void main(String[] args) {
        testPerPassCapAndRotation();
        testAuditRotationDoesNotAliasItsGate();
        testSettlerPacingAndHomeRadius();
        testIncompleteSiteClassification();
        testExpandingDeterministicSiteSearch();
        testProgressiveConstructionTarget();
        testAppendOnlyTrailTargetCompatibility();
        testTrailMaterialRoles();
        testCenterTrailSurfaceRetrofitSafety();
        testConservativeLegacyBounds();
        testIntegrityThresholds();
        System.out.println("PASS village materialization policy regression");
    }

    private static void testPerPassCapAndRotation() {
        require(VillageMaterializationPolicy.villagesToProcess(-1) == 0,
                "Negative nearby count was not sanitized");
        require(VillageMaterializationPolicy.villagesToProcess(8) == 8,
                "A small nearby set was unexpectedly truncated");
        require(VillageMaterializationPolicy.villagesToProcess(16) == 16,
                "The exact per-pass cap was not accepted");
        require(VillageMaterializationPolicy.villagesToProcess(40) == 16,
                "A large nearby set escaped the per-pass cap");
        require(VillageMaterializationPolicy.villagesToProcess(40, 3) == 3,
                "A later dimension escaped the shared pass capacity");
        require(VillageMaterializationPolicy.villagesToProcess(40, 0) == 0,
                "An exhausted shared pass capacity processed another dimension");

        int villageCount = 20;
        Set<Integer> firstPass = new HashSet<>();
        for (int step = 0;
                step < VillageMaterializationPolicy.villagesToProcess(villageCount);
                step++) {
            firstPass.add(VillageMaterializationPolicy.rotatingIndex(18, step, villageCount));
        }
        require(firstPass.size() == 16 && firstPass.contains(18) && firstPass.contains(0),
                "Rotating pass did not wrap once without duplicates");

        int[] visits = new int[villageCount];
        for (int pulse = 0; pulse < villageCount; pulse++) {
            for (int step = 0;
                    step < VillageMaterializationPolicy.villagesToProcess(villageCount);
                    step++) {
                visits[VillageMaterializationPolicy.rotatingIndex(pulse, step, villageCount)]++;
            }
        }
        for (int index = 0; index < visits.length; index++) {
            require(visits[index] == VillageMaterializationPolicy.MAX_NEARBY_VILLAGES_PER_PASS,
                    "Village " + index + " did not receive an equal rotating share");
        }
    }

    private static void testSettlerPacingAndHomeRadius() {
        require(VillageMaterializationPolicy.settlerHomeRadius(48) == 16,
                "Small development radius did not preserve its proportional home radius");
        require(VillageMaterializationPolicy.settlerHomeRadius(96) == 32,
                "Legacy radius did not reach the safe home-radius cap");
        require(VillageMaterializationPolicy.settlerHomeRadius(192) == 32,
                "Large activation radius leaked into villager home navigation");
        require(VillageMaterializationPolicy.settlerHomeRadius(-1) == 8,
                "Invalid radius did not retain the minimum home radius");

        require(VillageMaterializationPolicy.settlerAttemptDue(100L, null, 1_200L),
                "First settler attempt was throttled");
        require(!VillageMaterializationPolicy.settlerAttemptDue(1_299L, 100L, 1_200L),
                "Failed settler attempt retried before its interval");
        require(VillageMaterializationPolicy.settlerAttemptDue(1_300L, 100L, 1_200L),
                "Settler attempt did not resume at its interval boundary");
        require(VillageMaterializationPolicy.settlerAttemptDue(50L, 100L, 1_200L),
                "Clock rollback left settler attempts permanently throttled");
    }

    private static void testAuditRotationDoesNotAliasItsGate() {
        long auditPulses = 240L;
        for (int candidates : new int[] {2, 3, 4, 5, 6, 8, 10}) {
            Set<Integer> visited = new HashSet<>();
            for (int ordinal = 0; ordinal < candidates; ordinal++) {
                visited.add(VillageMaterializationPolicy.rotatingAuditIndex(
                        ordinal * auditPulses, auditPulses, candidates));
            }
            require(visited.size() == candidates,
                    "Audit gate aliased project selection for candidate count " + candidates);
        }
    }

    private static void testIncompleteSiteClassification() {
        require(VillageMaterializationPolicy.completedSiteSearch(true, true)
                        == VillageMaterializationPolicy.SiteAvailability.AVAILABLE,
                "A found site was overridden by an earlier unloaded candidate");
        require(VillageMaterializationPolicy.completedSiteSearch(false, true)
                        == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED,
                "Incomplete search was incorrectly classified as unsafe");
        require(VillageMaterializationPolicy.completedSiteSearch(false, false)
                        == VillageMaterializationPolicy.SiteAvailability.UNSAFE,
                "Fully inspected rejected sites did not produce an unsafe result");
    }

    private static void testExpandingDeterministicSiteSearch() {
        List<VillageMaterializationPolicy.SiteOffset> initial =
                VillageMaterializationPolicy.projectSiteOffsets(0);
        List<VillageMaterializationPolicy.SiteOffset> expanded =
                VillageMaterializationPolicy.projectSiteOffsets(2);
        require(initial.size() == 64,
                "The compatible first-pass site search no longer has its original 64 candidates");
        require(expanded.size() > initial.size()
                        && expanded.subList(0, initial.size()).equals(initial),
                "A retry did not append deterministic mountain/forest escape rings");
        require(expanded.equals(VillageMaterializationPolicy.projectSiteOffsets(2))
                        && new HashSet<>(expanded).size() == expanded.size(),
                "Expanded project-site offsets are nondeterministic or duplicated");
        require(expanded.stream().anyMatch(offset ->
                        Math.abs(offset.x()) > 84 || Math.abs(offset.z()) > 84),
                "The project search can still never escape its original fixed frontier");
        require(VillageMaterializationPolicy.projectSiteOffsets(Integer.MAX_VALUE).size()
                        == VillageMaterializationPolicy.projectSiteOffsets(8).size(),
                "Repeated failures made one site sweep unbounded");
    }

    private static void testProgressiveConstructionTarget() {
        require(VillageMaterializationPolicy.constructionTargetBlocks(
                        0.0, false, 500, 124) == 124,
                "Groundbreaking did not expose its terrain and visible authored prefix");
        require(VillageMaterializationPolicy.constructionTargetBlocks(
                        0.25, false, 500, 124) == 217,
                "Economic progress did not proportionally unlock physical construction");
        require(VillageMaterializationPolicy.constructionTargetBlocks(
                        1.0, false, 500, 124) == 499,
                "An economically incomplete project was not held one block short");
        require(VillageMaterializationPolicy.constructionTargetBlocks(
                        0.25, true, 500, 124) == 500,
                "Economic completion did not release the final authored block");
        require(VillageMaterializationPolicy.constructionTargetBlocks(
                        Double.NaN, false, 1, 10) == 0,
                "Invalid progress completed a one-block project before economic completion");
    }

    private static void testAppendOnlyTrailTargetCompatibility() {
        require(VillageMaterializationPolicy.compatibleTrailTarget(7, 20, 20),
                "An unchanged frozen trail target became incompatible");
        require(VillageMaterializationPolicy.compatibleTrailTarget(20, 20, 54),
                "A completed narrow road could not append its wider shoulder suffix");
        require(!VillageMaterializationPolicy.compatibleTrailTarget(21, 20, 54),
                "A cursor beyond its persisted prefix was accepted");
        require(!VillageMaterializationPolicy.compatibleTrailTarget(7, 20, 19),
                "A regenerated road was allowed to shrink its persisted plan");
        require(!VillageMaterializationPolicy.compatibleTrailTarget(0, 0, 12),
                "An unfrozen road target was treated as a compatible retrofit");
    }

    private static void testCenterTrailSurfaceRetrofitSafety() {
        require(VillageMaterializationPolicy.trailSurfaceSatisfied(false, true, false),
                "An ordinary trail cell stopped adopting an existing TES surface");
        require(!VillageMaterializationPolicy.trailSurfaceSatisfied(true, true, false),
                "A coarse-dirt center cell incorrectly satisfied the dirt-path retrofit");
        require(VillageMaterializationPolicy.trailSurfaceSatisfied(true, true, true),
                "A dirt-path or gravel center cell did not satisfy the retrofit");

        require(VillageMaterializationPolicy.mayApplyTrailSurface(false, true, false, false),
                "Initial trail construction stopped accepting approved natural ground");
        require(VillageMaterializationPolicy.mayApplyTrailSurface(true, false, true, true),
                "The center retrofit could not replace its legacy coarse-dirt surface");
        require(!VillageMaterializationPolicy.mayApplyTrailSurface(true, false, true, false),
                "The center retrofit removed a gravel accent from the route");
        require(!VillageMaterializationPolicy.mayApplyTrailSurface(true, false, false, true),
                "The center retrofit trusted an unrecognized surface classification");
        require(!VillageMaterializationPolicy.mayApplyTrailSurface(true, true, false, false),
                "The center retrofit inferred ownership from untouched natural ground");
        require(!VillageMaterializationPolicy.mayApplyTrailSurface(true, false, false, false),
                "The center retrofit could overwrite an occupied or non-trail player-built cell");
    }

    private static void testTrailMaterialRoles() {
        require(VillageMaterializationPolicy.frozenTrailSurface(false, false, 0L)
                                == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                        && VillageMaterializationPolicy.frozenTrailSurface(false, false, 1L)
                                == VillageMaterializationPolicy.TrailSurface.DIRT_PATH
                        && VillageMaterializationPolicy.frozenTrailSurface(false, false, 7L)
                                == VillageMaterializationPolicy.TrailSurface.GRAVEL
                        && VillageMaterializationPolicy.frozenTrailSurface(false, false, 11L)
                                == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                        && VillageMaterializationPolicy.frozenTrailSurface(true, false, 0L)
                                == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                        && VillageMaterializationPolicy.frozenTrailSurface(true, false, 1L)
                                == VillageMaterializationPolicy.TrailSurface.GRAVEL
                        && VillageMaterializationPolicy.frozenTrailSurface(true, false, 5L)
                                == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT,
                "The save-stable historical trail material formula changed");
        boolean sawTemperatePath = false;
        boolean sawTemperateGravel = false;
        boolean sawDesertPath = false;
        boolean sawDesertGravel = false;
        boolean sawFrozenTemperateCoarse = false;
        boolean sawFrozenDesertCoarse = false;
        for (long detail = -100L; detail <= 100L; detail++) {
            VillageMaterializationPolicy.TrailSurface temperate =
                    VillageMaterializationPolicy.plannedTrailSurface(false, false, detail);
            VillageMaterializationPolicy.TrailSurface desert =
                    VillageMaterializationPolicy.plannedTrailSurface(true, false, detail);
            VillageMaterializationPolicy.TrailSurface frozenTemperate =
                    VillageMaterializationPolicy.frozenTrailSurface(false, false, detail);
            VillageMaterializationPolicy.TrailSurface frozenDesert =
                    VillageMaterializationPolicy.frozenTrailSurface(true, false, detail);
            require(temperate != VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                            && desert != VillageMaterializationPolicy.TrailSurface.COARSE_DIRT,
                    "A center-lane material plan still emitted coarse dirt");
            if (VillageMaterializationPolicy.legacyCenterSurfaceNeedsRetrofit(false, detail)) {
                require(frozenTemperate == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                                && temperate == VillageMaterializationPolicy.TrailSurface.DIRT_PATH,
                        "A legacy temperate coarse-dirt accent was not replaced with dirt path");
                sawFrozenTemperateCoarse = true;
            } else if (frozenTemperate == VillageMaterializationPolicy.TrailSurface.GRAVEL) {
                require(temperate == VillageMaterializationPolicy.TrailSurface.GRAVEL,
                        "A temperate gravel accent was removed by the material retrofit");
            }
            if (VillageMaterializationPolicy.legacyCenterSurfaceNeedsRetrofit(true, detail)) {
                require(frozenDesert == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                                && desert == VillageMaterializationPolicy.TrailSurface.DIRT_PATH,
                        "A legacy desert coarse-dirt accent was not replaced with dirt path");
                sawFrozenDesertCoarse = true;
            } else if (frozenDesert == VillageMaterializationPolicy.TrailSurface.GRAVEL) {
                require(desert == VillageMaterializationPolicy.TrailSurface.GRAVEL,
                        "A desert gravel accent was removed by the material retrofit");
            }
            sawTemperatePath |= temperate == VillageMaterializationPolicy.TrailSurface.DIRT_PATH;
            sawTemperateGravel |= temperate == VillageMaterializationPolicy.TrailSurface.GRAVEL;
            sawDesertPath |= desert == VillageMaterializationPolicy.TrailSurface.DIRT_PATH;
            sawDesertGravel |= desert == VillageMaterializationPolicy.TrailSurface.GRAVEL;
            require(VillageMaterializationPolicy.frozenTrailSurface(false, true, detail)
                            == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT
                            && VillageMaterializationPolicy.frozenTrailSurface(true, true, detail)
                                    == VillageMaterializationPolicy.TrailSurface.COARSE_DIRT,
                    "A frozen shoulder material changed in-place");
        }
        require(sawTemperatePath
                        && sawTemperateGravel
                        && sawDesertPath
                        && sawDesertGravel
                        && sawFrozenTemperateCoarse
                        && sawFrozenDesertCoarse,
                "A center lane lost its deterministic dirt-path or gravel variation");
    }

    private static void testConservativeLegacyBounds() {
        VillageMaterializationPolicy.RelativeBounds bounds =
                VillageMaterializationPolicy.conservativeProjectBounds(13, 9, 6, 3);
        require(bounds.minimumX() == -1 && bounds.maximumX() == 13,
                "Legacy bounds lost the guarded side columns");
        require(bounds.minimumY() == -3 && bounds.maximumY() == 6,
                "Legacy bounds lost terrain supports or roof height");
        require(bounds.minimumZ() == -2 && bounds.maximumZ() == 9,
                "Legacy bounds lost the entrance stair or rear wall");
        require(bounds.contains(-1, -3, -2) && bounds.contains(13, 6, 9),
                "Legacy bounds did not contain their inclusive corners");
        require(!bounds.contains(-2, 0, 0) && !bounds.contains(0, 0, 10),
                "Legacy bounds expanded beyond the declared conservative envelope");
    }

    private static void testIntegrityThresholds() {
        require(VillageMaterializationPolicy.assessIntegrity(300, 0)
                        == VillageMaterializationPolicy.IntegrityDecision.INTACT,
                "A complete structure was not intact");
        require(VillageMaterializationPolicy.assessIntegrity(300, 1)
                        == VillageMaterializationPolicy.IntegrityDecision.UNSAFE,
                "A single customized block incorrectly cloned a structure");
        require(VillageMaterializationPolicy.assessIntegrity(300, 89)
                        == VillageMaterializationPolicy.IntegrityDecision.UNSAFE,
                "Sub-threshold damage incorrectly requested relocation");
        require(VillageMaterializationPolicy.assessIntegrity(300, 90)
                        == VillageMaterializationPolicy.IntegrityDecision.RELOCATE,
                "Thirty-percent demolition did not request relocation");
        require(VillageMaterializationPolicy.assessIntegrity(20, 11)
                        == VillageMaterializationPolicy.IntegrityDecision.UNSAFE,
                "Small structures ignored the absolute anti-cloning floor");
        require(VillageMaterializationPolicy.assessIntegrity(20, 12)
                        == VillageMaterializationPolicy.IntegrityDecision.RELOCATE,
                "A mostly demolished small structure did not request relocation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
