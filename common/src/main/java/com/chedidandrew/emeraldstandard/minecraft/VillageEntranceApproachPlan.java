package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loader-neutral planning for a generated village building's descending entrance approach. */
public final class VillageEntranceApproachPlan {
    private VillageEntranceApproachPlan() {
    }

    /**
     * Plans the additional stairs between a fixed top stair and a primary road.
     *
     * <p>Surface offsets are ordered road cells beginning immediately beyond the fixed top stair.
     * Each value is the natural walking-surface Y relative to the building origin. Terrain at the
     * current stair level, or one block above it, completes the approach. Lower terrain adds one
     * descending stair at that route index. Invalid, too-deep, abruptly rising, or incomplete
     * profiles are rejected.</p>
     *
     * @return the additional stairs only; the fixed top stair is not included
     */
    public static Optional<List<Step>> plan(
            List<Integer> orderedSurfaceOffsets, int maximumDrop) {
        if (maximumDrop < 0 || maximumDrop > 16) {
            throw new IllegalArgumentException("Invalid entrance drop " + maximumDrop);
        }
        if (orderedSurfaceOffsets == null || orderedSurfaceOffsets.isEmpty()) {
            return Optional.empty();
        }

        List<Step> steps = new ArrayList<>();
        int currentLevel = 0;
        for (int routeIndex = 0; routeIndex < orderedSurfaceOffsets.size(); routeIndex++) {
            Integer sampledSurface = orderedSurfaceOffsets.get(routeIndex);
            if (sampledSurface == null
                    || sampledSurface < -maximumDrop
                    || sampledSurface > 0) {
                return Optional.empty();
            }
            if (sampledSurface == currentLevel || sampledSurface == currentLevel + 1) {
                return Optional.of(List.copyOf(steps));
            }
            if (sampledSurface > currentLevel + 1 || currentLevel <= -maximumDrop) {
                return Optional.empty();
            }

            currentLevel--;
            steps.add(new Step(routeIndex, currentLevel));
        }
        return Optional.empty();
    }

    /** One additional stair at a zero-based primary-road index and relative Y level. */
    public record Step(int routeIndex, int yOffset) {
    }
}
