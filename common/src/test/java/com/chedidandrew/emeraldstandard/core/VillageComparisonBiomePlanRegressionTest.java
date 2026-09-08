package com.chedidandrew.emeraldstandard.core;

import java.util.HashSet;
import java.util.Set;

public final class VillageComparisonBiomePlanRegressionTest {
    public static void main(String[] args) {
        verify(1, -64, 1, 110, 319, 94);
        verify(-219, -512, -173, -4, 511, -8);
        verify(-1, -1, -1, 1, 1, 1);
        verify(0, 0, 0, 0, 0, 0);
        verify(32, -2048, 32, 35, 2047, 35);
        for (int offset = -7; offset <= 7; offset++) {
            verify(offset, offset, offset, offset + 32, offset + 64, offset + 32);
        }
        try {
            VillageComparisonBiomePlan.slices(1, 0, 0, 0, 0, 0);
            throw new AssertionError("Reversed bounds accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        System.out.println("PASS comparison biome slices: exact quart coverage and <=24,389 command volume");
    }

    private static void verify(int x1, int y1, int z1, int x2, int y2, int z2) {
        Set<String> covered = new HashSet<>();
        var slices = VillageComparisonBiomePlan.slices(x1, y1, z1, x2, y2, z2);
        for (var slice : slices) {
            require(slice.commandVolume() > 0 && slice.commandVolume() <= 24_389,
                    "Command can exceed the untouched vanilla volume limit");
            for (int coordinate : new int[] {slice.minX(), slice.minY(), slice.minZ(),
                    slice.maxX(), slice.maxY(), slice.maxZ()}) {
                require(Math.floorMod(coordinate, 4) == 0, "Endpoint is not a biome-quart origin");
            }
            for (int x = slice.minX() / 4; x <= slice.maxX() / 4; x++) {
                for (int y = slice.minY() / 4; y <= slice.maxY() / 4; y++) {
                    for (int z = slice.minZ() / 4; z <= slice.maxZ() / 4; z++) {
                        require(x >= Math.floorDiv(x1, 4) && x <= Math.floorDiv(x2, 4)
                                        && y >= Math.floorDiv(y1, 4) && y <= Math.floorDiv(y2, 4)
                                        && z >= Math.floorDiv(z1, 4) && z <= Math.floorDiv(z2, 4),
                                "Slice escapes requested biome quarts");
                        require(covered.add(x + ":" + y + ":" + z), "Overlapping slices");
                    }
                }
            }
        }
        long expected = (long) (Math.floorDiv(x2, 4) - Math.floorDiv(x1, 4) + 1)
                * (Math.floorDiv(y2, 4) - Math.floorDiv(y1, 4) + 1)
                * (Math.floorDiv(z2, 4) - Math.floorDiv(z1, 4) + 1);
        require(covered.size() == expected, "Gap in requested biome coverage");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
