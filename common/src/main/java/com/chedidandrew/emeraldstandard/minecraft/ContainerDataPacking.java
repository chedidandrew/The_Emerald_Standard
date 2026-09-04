package com.chedidandrew.emeraldstandard.minecraft;

/**
 * Losslessly maps logical 32-bit menu values onto Minecraft's signed 16-bit
 * {@code ContainerData} wire values.
 */
public final class ContainerDataPacking {
    public static final int LIMBS_PER_INT = Integer.SIZE / Short.SIZE;
    private static final int MAX_WIRE_SLOTS = Short.MAX_VALUE + 1;

    private ContainerDataPacking() {
    }

    public static int wireSlotCount(int logicalSlotCount) {
        if (logicalSlotCount < 0) {
            throw new IllegalArgumentException("Logical slot count must be nonnegative");
        }
        int wireSlotCount = Math.multiplyExact(logicalSlotCount, LIMBS_PER_INT);
        if (wireSlotCount > MAX_WIRE_SLOTS) {
            throw new IllegalArgumentException(
                    "Packed ContainerData exceeds the signed-short slot id range");
        }
        return wireSlotCount;
    }

    public static int logicalIndex(int wireIndex) {
        if (wireIndex < 0) {
            throw new IndexOutOfBoundsException("Negative ContainerData wire index");
        }
        return wireIndex / LIMBS_PER_INT;
    }

    public static int limbIndex(int wireIndex) {
        if (wireIndex < 0) {
            throw new IndexOutOfBoundsException("Negative ContainerData wire index");
        }
        return wireIndex % LIMBS_PER_INT;
    }

    /** Returns the requested limb with the same sign extension produced by readShort(). */
    public static int encodeLimb(int value, int limbIndex) {
        if (limbIndex < 0 || limbIndex >= LIMBS_PER_INT) {
            throw new IndexOutOfBoundsException("Invalid 32-bit ContainerData limb");
        }
        return (short) (value >>> (limbIndex * Short.SIZE));
    }

    /** Reassembles two signed-short values without losing either 16-bit pattern. */
    public static int decodeInt(int lowLimb, int highLimb) {
        return (lowLimb & 0xFFFF) | ((highLimb & 0xFFFF) << Short.SIZE);
    }
}
