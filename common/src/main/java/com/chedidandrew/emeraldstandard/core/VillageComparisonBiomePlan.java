package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.List;

/** Bounded, quart-aligned fillbiome commands for the opt-in comparison world only. */
public final class VillageComparisonBiomePlan {
    private static final int QUARTS_PER_SLICE = 8;

    private VillageComparisonBiomePlan() {
    }

    /** Inclusive block bounds. Cover each intersecting biome quart exactly once. */
    public static List<Slice> slices(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Comparison biome bounds must be nonempty");
        }
        List<Slice> result = new ArrayList<>();
        long lastX = Math.floorDiv(maxX, 4);
        long lastY = Math.floorDiv(maxY, 4);
        long lastZ = Math.floorDiv(maxZ, 4);
        for (long x = Math.floorDiv(minX, 4); x <= lastX; x += QUARTS_PER_SLICE) {
            for (long y = Math.floorDiv(minY, 4); y <= lastY; y += QUARTS_PER_SLICE) {
                for (long z = Math.floorDiv(minZ, 4); z <= lastZ; z += QUARTS_PER_SLICE) {
                    result.add(new Slice(block(x), block(y), block(z),
                            block(Math.min(lastX, x + QUARTS_PER_SLICE - 1)),
                            block(Math.min(lastY, y + QUARTS_PER_SLICE - 1)),
                            block(Math.min(lastZ, z + QUARTS_PER_SLICE - 1))));
                }
            }
        }
        return List.copyOf(result);
    }

    private static int block(long quart) {
        return Math.toIntExact(quart * 4);
    }

    public record Slice(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        /** Vanilla checks this block-space inclusive volume after quart quantization. */
        public long commandVolume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }
}
