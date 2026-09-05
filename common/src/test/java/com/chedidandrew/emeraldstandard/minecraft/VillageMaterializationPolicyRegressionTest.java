package com.chedidandrew.emeraldstandard.minecraft;

import java.util.HashSet;
import java.util.Set;

/** Regression coverage for bounded/fair village materialization policy. */
public final class VillageMaterializationPolicyRegressionTest {
    private VillageMaterializationPolicyRegressionTest() {
    }

    public static void main(String[] args) {
        testPerPassCapAndRotation();
        testSettlerPacingAndHomeRadius();
        testIncompleteSiteClassification();
        testConservativeLegacyBounds();
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
