package com.chedidandrew.emeraldstandard.minecraft;

/** Verifies full-width menu values survive Minecraft's signed-short packet boundary. */
public final class ContainerDataPackingRegressionTest {
    private ContainerDataPackingRegressionTest() {
    }

    public static void main(String[] args) {
        int[] intValues = {
            Integer.MIN_VALUE,
            -100_000_000,
            -65_536,
            -32_769,
            -32_768,
            -1,
            0,
            1,
            32_767,
            32_768,
            65_535,
            65_536,
            1_000_000,
            10_000_000,
            100_000_000,
            Integer.MAX_VALUE
        };
        for (int value : intValues) {
            require(roundTripInt(value) == value,
                    "Signed-short packet round trip changed int " + value);
        }
        int generated = 0x6D2B79F5;
        for (int index = 0; index < 10_000; index++) {
            generated = generated * 1_664_525 + 1_013_904_223;
            require(roundTripInt(generated) == generated,
                    "Signed-short packet round trip changed generated int " + generated);
        }

        long[] longValues = {
            Long.MIN_VALUE,
            -100_000_000L,
            -1L,
            0L,
            1L,
            1_000_000L,
            10_000_000L,
            100_000_000L,
            4_294_967_295L,
            Long.MAX_VALUE
        };
        for (long value : longValues) {
            int low = roundTripInt((int) value);
            int high = roundTripInt((int) (value >>> Integer.SIZE));
            long reconstructed = ((long) high << Integer.SIZE) | Integer.toUnsignedLong(low);
            require(reconstructed == value,
                    "Signed-short packet round trip changed long " + value);
        }

        require(roundTripInt(100_000_000) == 100_000_000,
                "A 100-emerald micro-unit balance was truncated");
        require(roundTripInt(1_000_000_000) == 1_000_000_000,
                "A large Fund draft was truncated");
        require(ContainerDataPacking.wireSlotCount(0) == 0,
                "Zero logical slots did not stay empty");
        require(ContainerDataPacking.wireSlotCount(16_384) == 32_768,
                "Maximum safe packed slot count was rejected");
        require(ContainerDataPacking.logicalIndex(0) == 0
                        && ContainerDataPacking.limbIndex(0) == 0
                        && ContainerDataPacking.logicalIndex(1) == 0
                        && ContainerDataPacking.limbIndex(1) == 1
                        && ContainerDataPacking.logicalIndex(32_767) == 16_383
                        && ContainerDataPacking.limbIndex(32_767) == 1,
                "Wire-to-logical slot mapping changed");
        requireThrows(() -> ContainerDataPacking.wireSlotCount(-1),
                "Negative logical slot count was accepted");
        requireThrows(() -> ContainerDataPacking.wireSlotCount(16_385),
                "A packed slot id outside the signed-short range was accepted");
        requireThrows(() -> ContainerDataPacking.encodeLimb(0, -1),
                "Negative limb index was accepted");
        requireThrows(() -> ContainerDataPacking.encodeLimb(0, 2),
                "Oversized limb index was accepted");
        requireThrows(() -> ContainerDataPacking.logicalIndex(-1),
                "Negative wire index was accepted");
        requireThrows(() -> ContainerDataPacking.limbIndex(-1),
                "Negative wire limb index was accepted");

        System.out.println("PASS signed-short ContainerData packing regression tests");
    }

    private static int roundTripInt(int value) {
        int lowOnClient = packetRoundTrip(ContainerDataPacking.encodeLimb(value, 0));
        int highOnClient = packetRoundTrip(ContainerDataPacking.encodeLimb(value, 1));
        return ContainerDataPacking.decodeInt(lowOnClient, highOnClient);
    }

    private static int packetRoundTrip(int serverValue) {
        return (short) serverValue;
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IndexOutOfBoundsException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
