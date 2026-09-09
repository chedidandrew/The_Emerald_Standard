package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Regression coverage for deterministic terrain-matched village entrance approaches. */
public final class VillageEntranceApproachPlanRegressionTest {
    private VillageEntranceApproachPlanRegressionTest() {
    }

    public static void main(String[] args) {
        testFlatApproachNeedsNoAdditionalStairs();
        testDropsOneThroughFour();
        testReachableRiseCompletesApproach();
        testIncompleteProfilesAreRejected();
        testOverDepthProfilesAreRejected();
        testAbruptRiseIsRejected();
        testDeterministicImmutableResult();
        System.out.println("PASS village entrance approach planning regressions");
    }

    private static void testFlatApproachNeedsNoAdditionalStairs() {
        Optional<List<VillageEntranceApproachPlan.Step>> planned =
                VillageEntranceApproachPlan.plan(List.of(0), 4);
        require(planned.isPresent() && planned.orElseThrow().isEmpty(),
                "Flat terrain did not meet the fixed top stair without extra stairs");
    }

    private static void testDropsOneThroughFour() {
        for (int drop = 1; drop <= 4; drop++) {
            List<Integer> profile = new ArrayList<>();
            for (int index = 0; index <= drop; index++) {
                profile.add(-drop);
            }
            List<VillageEntranceApproachPlan.Step> expected = new ArrayList<>();
            for (int index = 0; index < drop; index++) {
                expected.add(new VillageEntranceApproachPlan.Step(index, -index - 1));
            }
            require(VillageEntranceApproachPlan.plan(profile, 4)
                            .equals(Optional.of(List.copyOf(expected))),
                    "A " + drop + "-block drop did not receive a continuous stair approach");
        }
    }

    private static void testReachableRiseCompletesApproach() {
        require(VillageEntranceApproachPlan.plan(List.of(-2, -2, -1), 4)
                        .equals(Optional.of(List.of(
                                new VillageEntranceApproachPlan.Step(0, -1),
                                new VillageEntranceApproachPlan.Step(1, -2)))),
                "Terrain one block above the current stair did not complete the approach");
    }

    private static void testIncompleteProfilesAreRejected() {
        require(VillageEntranceApproachPlan.plan(List.of(-2, -2), 4).isEmpty(),
                "A profile ending on its final new stair was accepted without reaching terrain");
        require(VillageEntranceApproachPlan.plan(List.of(), 4).isEmpty(),
                "An empty terrain profile was accepted");
        require(VillageEntranceApproachPlan.plan(null, 4).isEmpty(),
                "A null terrain profile was accepted");
        require(VillageEntranceApproachPlan.plan(Arrays.asList(-1, null), 4).isEmpty(),
                "A terrain profile containing a null sample was accepted");
    }

    private static void testOverDepthProfilesAreRejected() {
        require(VillageEntranceApproachPlan.plan(List.of(-5), 4).isEmpty(),
                "Terrain outside the maximum drop was accepted");
        require(VillageEntranceApproachPlan.plan(List.of(-4, -4, -4, -4, -5), 4)
                        .isEmpty(),
                "An approach was allowed to continue below its maximum drop");
    }

    private static void testAbruptRiseIsRejected() {
        require(VillageEntranceApproachPlan.plan(List.of(-3, -3, 0), 4).isEmpty(),
                "Terrain rising by more than one block above the current stair was accepted");
        require(VillageEntranceApproachPlan.plan(List.of(1), 4).isEmpty(),
                "Terrain above the building origin was accepted");
    }

    private static void testDeterministicImmutableResult() {
        List<Integer> profile = List.of(-3, -3, -3, -3);
        List<VillageEntranceApproachPlan.Step> first =
                VillageEntranceApproachPlan.plan(profile, 4).orElseThrow();
        List<VillageEntranceApproachPlan.Step> second =
                VillageEntranceApproachPlan.plan(profile, 4).orElseThrow();
        require(first.equals(second), "Identical terrain profiles produced different approaches");
        try {
            first.add(new VillageEntranceApproachPlan.Step(4, -4));
            throw new AssertionError("A planned entrance approach remained mutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: callers may safely persist and share the frozen plan.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
