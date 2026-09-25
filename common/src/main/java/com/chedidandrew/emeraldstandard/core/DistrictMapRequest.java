package com.chedidandrew.emeraldstandard.core;

/** Ordered, read-only viewport requests over the existing menu-button transport. */
public final class DistrictMapRequest {
    // Disjoint from actions and exact-amount buttons. Every value fits the native positive VarInt.
    private static final int X = 0x40000000, Z = 0x44000000, WIDTH = 0x48000000;
    private static final int MASK = 0x03ffffff;
    private int x, z, stage;

    public static boolean matches(int button) {
        int kind = button & ~MASK;
        return kind == X || kind == Z || kind == WIDTH;
    }

    public static int[] encode(VillageDistrictMap.View view) {
        return new int[] { X | (view.centerX() + VillageDistrictMap.WORLD_LIMIT),
                Z | (view.centerZ() + VillageDistrictMap.WORLD_LIMIT), WIDTH | view.halfWidth() };
    }

    /** Only a complete X, Z, width sequence publishes a view. Partial/invalid requests do nothing. */
    public VillageDistrictMap.View accept(int button) {
        int kind = button & ~MASK, value = button & MASK;
        if (kind == X) {
            stage = value <= 2 * VillageDistrictMap.WORLD_LIMIT ? 1 : 0;
            x = value - VillageDistrictMap.WORLD_LIMIT;
        } else if (kind == Z && stage == 1 && value <= 2 * VillageDistrictMap.WORLD_LIMIT) {
            z = value - VillageDistrictMap.WORLD_LIMIT; stage = 2;
        } else if (kind == WIDTH && stage == 2) {
            stage = 0;
            if (value >= 16 && value <= VillageDistrictMap.WORLD_LIMIT)
                return new VillageDistrictMap.View(x, z, value);
        } else stage = 0;
        return null;
    }

    public void reset() { stage = 0; }
}
