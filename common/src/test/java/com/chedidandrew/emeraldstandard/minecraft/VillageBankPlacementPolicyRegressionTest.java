package com.chedidandrew.emeraldstandard.minecraft;

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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
