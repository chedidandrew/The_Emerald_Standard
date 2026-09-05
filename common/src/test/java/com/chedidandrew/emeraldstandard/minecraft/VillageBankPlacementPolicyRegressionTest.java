package com.chedidandrew.emeraldstandard.minecraft;

import java.util.List;

/** Regression coverage for natural-lot clearance and safe fallback Bank retries. */
public final class VillageBankPlacementPolicyRegressionTest {
    private VillageBankPlacementPolicyRegressionTest() {
    }

    public static void main(String[] args) {
        require(VillageBankPlacementPolicy.acceptsVolumeCell(true, false, false),
                "Air was rejected from a Bank lot");
        require(VillageBankPlacementPolicy.acceptsVolumeCell(false, true, false),
                "Replaceable vegetation was treated as a solid obstruction");
        require(!VillageBankPlacementPolicy.acceptsVolumeCell(false, false, false),
                "A solid block was accepted inside a Bank lot");
        require(!VillageBankPlacementPolicy.acceptsVolumeCell(true, true, true),
                "A block entity was accepted inside a Bank lot");

        testEntranceApproachPlanning();
        testUpgradeAttemptGate();

        require(VillageBankPlacementPolicy.shouldRetryPersistedFallback(true),
                "An explicitly persisted fallback Banker was not retryable");
        require(!VillageBankPlacementPolicy.shouldRetryPersistedFallback(false),
                "A legacy or interrupted Bank without fallback provenance was unsafe to retry");

        require(VillageBankPlacementPolicy.retryDue(1_000L, null, 2_400L),
                "The first fallback retry was throttled");
        require(!VillageBankPlacementPolicy.retryDue(3_399L, 1_000L, 2_400L),
                "A fallback retry escaped its pacing interval");
        require(VillageBankPlacementPolicy.retryDue(3_400L, 1_000L, 2_400L),
                "A fallback retry did not resume at its interval boundary");
        require(VillageBankPlacementPolicy.retryDue(50L, 1_000L, 2_400L),
                "A clock rollback left fallback retries permanently throttled");

        require(VillageBankPlacementPolicy.shouldPersistFallback(true, false),
                "A complete terrain search with no candidates did not persist fallback access");
        require(!VillageBankPlacementPolicy.shouldPersistFallback(false, false),
                "An incomplete chunk search was made permanently final");
        require(!VillageBankPlacementPolicy.shouldPersistFallback(false, true),
                "An incomplete protected-write attempt was made permanently final");
        require(!VillageBankPlacementPolicy.shouldPersistFallback(true, true),
                "A transient protected write failure was made permanently final");
        System.out.println("PASS village bank placement policy regression");
    }

    private static void testEntranceApproachPlanning() {
        var top = new VillageBankPlacementPolicy.EntranceStep(0, -2);
        var middle = new VillageBankPlacementPolicy.EntranceStep(-1, -3);
        var bottom = new VillageBankPlacementPolicy.EntranceStep(-2, -4);

        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(0), 2)
                        .orElseThrow()
                        .equals(List.of(top)),
                "A level Bank entrance did not retain its top stair");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-1, -1), 2)
                        .orElseThrow()
                        .equals(List.of(top, middle)),
                "A one-block terrain drop did not receive a continuous stair approach");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-2, -2, -2), 2)
                        .orElseThrow()
                        .equals(List.of(top, middle, bottom)),
                "A two-block terrain drop did not receive the bounded three-stair approach");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-2, 0), 2)
                        .orElseThrow()
                        .equals(List.of(top, middle)),
                "A reachable rising arrival cell was not joined to the preceding half-step");

        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-3, -3, -3), 2)
                        .isEmpty(),
                "Terrain deeper than the supported foundation range was accepted");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-2), 2).isEmpty(),
                "An approach that ended before reaching natural terrain was accepted");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-2, -2, 0), 2)
                        .isEmpty(),
                "A two-block rise beside the lowest stair was treated as walkable");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(1), 2).isEmpty(),
                "Terrain above the sampled Bank-floor maximum was accepted");
        require(VillageBankPlacementPolicy.planEntranceSteps(List.of(-2, -2, -2), 2)
                        .equals(VillageBankPlacementPolicy.planEntranceSteps(
                                List.of(-2, -2, -2), 2)),
                "Entrance planning was not deterministic");

        require(VillageBankPlacementPolicy.planWideEntranceSteps(
                        List.of(
                                List.of(-2, -2, -2),
                                List.of(-2, -2, -2),
                                List.of(-2, -2, -2)),
                        2)
                        .orElseThrow()
                        .equals(List.of(top, middle, bottom)),
                "Identical terrain lanes did not produce a common wide stair approach");
        require(VillageBankPlacementPolicy.planWideEntranceSteps(
                        List.of(
                                List.of(-1, -1),
                                List.of(-1, 0),
                                List.of(-1, -1, 0)),
                        2)
                        .orElseThrow()
                        .equals(List.of(top, middle)),
                "Terrain lanes reaching the same stair row were required to have identical samples");
        require(VillageBankPlacementPolicy.planWideEntranceSteps(
                        List.of(
                                List.of(0),
                                List.of(-2, -2, -2),
                                List.of(-2, -2, -2)),
                        2)
                        .isEmpty(),
                "A cross-slope that would leave two wide stair lanes floating was accepted");
    }

    private static void testUpgradeAttemptGate() {
        var gate = new VillageBankPlacementPolicy.UpgradeAttemptGate();
        require(!gate.tryClaim(false),
                "An ineligible Bank consumed the structure-upgrade attempt");
        require(!gate.claimed(),
                "An ineligible Bank closed the structure-upgrade gate");
        require(gate.tryClaim(true),
                "The first eligible Bank could not claim the structure-upgrade attempt");
        require(gate.claimed(),
                "A successful structure-upgrade claim was not retained");
        require(!gate.tryClaim(true),
                "A second Bank claimed another expensive upgrade in the same scan");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
