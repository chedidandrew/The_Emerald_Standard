package com.chedidandrew.emeraldstandard.core;

import java.util.UUID;

/** Stable generated names from the already-persisted village identity; never consumes economy RNG.
 * The vocabulary and mixing formula are a save compatibility contract: do not reorder them. */
public final class DistrictNames {
    private static final String[] QUALIFIERS = ("Amber Bright Copper Dawn Emerald Fair Golden Grand "
            + "Green Hidden High Iron Jade Little Long Lower Misty New North Old Quiet Red "
            + "Silver South Star Stone Sunny Upper West White Wild Clear").split(" ");
    private static final String[] ROOTS = ("Ash Alder Apple Birch Bramble Briar Brook Cedar "
            + "Clover Copper Crow Elm Fern Flint Fox Hazel Heather Holly Ivy Juniper "
            + "Laurel Maple Meadow Moss Oak Pine Reed Rose Rowan Thorn Willow Wren").split(" ");
    private static final String[] ENDINGS = ("bank barrow beck bend borough bridge brook burg "
            + "bury crest dale dell den field ford gate glen grove haven heath "
            + "hill hollow hurst keep mead mere mill ridge stead vale ward well").split(" ");
    private static final String[] QUARTERS = ("Commons Court Crossing Gardens Green Heights Hollow Lane "
            + "Market Meadow Park Quarter Reach Square Terrace Walk").split(" ");
    public static final int COUNT = 32 * 32 * 32 * 16;

    private DistrictNames() {}

    public static int code(UUID village) {
        long value = village.getMostSignificantBits() ^ Long.rotateLeft(village.getLeastSignificantBits(), 23);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return (int)((value ^ (value >>> 31)) & (COUNT - 1)) + 1;
    }

    public static String name(int code) {
        int n = Math.floorMod(code - 1L, COUNT);
        return QUALIFIERS[n & 31] + " " + ROOTS[(n >>> 5) & 31]
                + ENDINGS[(n >>> 10) & 31] + " " + QUARTERS[(n >>> 15) & 15];
    }
}
