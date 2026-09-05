package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loader-neutral decisions used by Village Bank site selection and fallback recovery. */
public final class VillageBankPlacementPolicy {
    private VillageBankPlacementPolicy() {
    }

    /**
     * A bank lot may clear replaceable plants and snow, but never solid blocks or block entities.
     */
    public static boolean acceptsVolumeCell(
            boolean air, boolean replaceable, boolean hasBlockEntity) {
        return !hasBlockEntity && (air || replaceable);
    }

    /**
     * Plans a north-facing stair approach from the Bank floor to sampled natural terrain.
     *
     * <p>The fixed top stair is at relative {@code (y=0,z=-2)}. Samples begin in the next
     * outward cell ({@code z=-3}) and continue north, and each value is that cell's natural
     * surface Y relative to the Bank origin. A surface at the current stair level or one block
     * above it meets the stair's outer half-height edge safely. Lower terrain adds one descending
     * stair; terrain that is too deep, rises by more than one block after a descent, or runs out
     * before meeting the approach is rejected. The caller remains responsible for loaded-chunk,
     * natural-ground, collision, fluid, block-entity, and protection checks.</p>
     */
    public static Optional<List<EntranceStep>> planEntranceSteps(
            List<Integer> sampledSurfaceOffsets, int maximumDrop) {
        if (maximumDrop < 0 || maximumDrop > 16) {
            throw new IllegalArgumentException("Invalid entrance drop " + maximumDrop);
        }
        if (sampledSurfaceOffsets == null || sampledSurfaceOffsets.isEmpty()) {
            return Optional.empty();
        }

        List<EntranceStep> steps = new ArrayList<>();
        int currentLevel = 0;
        int currentZ = -2;
        steps.add(new EntranceStep(currentLevel, currentZ));
        for (Integer sampledSurface : sampledSurfaceOffsets) {
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
            currentZ--;
            steps.add(new EntranceStep(currentLevel, currentZ));
        }
        return Optional.empty();
    }

    /**
     * Plans a three-block-wide entrance without allowing any stair lane to float above terrain.
     *
     * <p>Each inner list contains the natural surface samples for one lane, ordered outward from
     * the Bank. All three lanes must independently produce the same valid stair sequence. This
     * permits small terrain differences that still meet the same stair row, while rejecting a
     * cross-slope that would require different stair heights or lengths across the entrance.</p>
     */
    public static Optional<List<EntranceStep>> planWideEntranceSteps(
            List<List<Integer>> sampledSurfaceOffsetsByLane, int maximumDrop) {
        if (sampledSurfaceOffsetsByLane == null || sampledSurfaceOffsetsByLane.size() != 3) {
            return Optional.empty();
        }

        Optional<List<EntranceStep>> commonPlan = planEntranceSteps(
                sampledSurfaceOffsetsByLane.get(0), maximumDrop);
        if (commonPlan.isEmpty()) {
            return Optional.empty();
        }
        for (int lane = 1; lane < sampledSurfaceOffsetsByLane.size(); lane++) {
            Optional<List<EntranceStep>> lanePlan = planEntranceSteps(
                    sampledSurfaceOffsetsByLane.get(lane), maximumDrop);
            if (lanePlan.isEmpty() || !lanePlan.get().equals(commonPlan.get())) {
                return Optional.empty();
            }
        }
        return commonPlan;
    }

    /** Only an explicitly persisted Banker-only fallback may start another structure build. */
    public static boolean shouldRetryPersistedFallback(boolean explicitlyPersisted) {
        return explicitlyPersisted;
    }

    /** Limits repeated terrain scans while a player remains in a village with no suitable lot. */
    public static boolean retryDue(long gameTime, Long previousAttempt, long intervalTicks) {
        if (previousAttempt == null || gameTime < previousAttempt) {
            return true;
        }
        return gameTime - previousAttempt >= Math.max(1L, intervalTicks);
    }

    /** Only a complete search with no candidate is a true terrain fallback. */
    public static boolean shouldPersistFallback(
            boolean searchComplete, boolean hadCandidates) {
        return searchComplete && !hadCandidates;
    }

    /** Relative authored stair position; the Bank center X is supplied by the world planner. */
    public record EntranceStep(int yOffset, int zOffset) {
    }

    /** Per-scan gate that permits at most one expensive legacy Bank upgrade attempt. */
    public static final class UpgradeAttemptGate {
        private boolean claimed;

        /** An ineligible Bank does not consume the opportunity needed by a later Bank. */
        public boolean tryClaim(boolean eligible) {
            if (!eligible || claimed) {
                return false;
            }
            claimed = true;
            return true;
        }

        public boolean claimed() {
            return claimed;
        }
    }
}
