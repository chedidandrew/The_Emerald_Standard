package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loader-neutral decisions used by Village Bank site selection and fallback recovery. */
public final class VillageBankPlacementPolicy {
    private static final List<SiteOffset> STANDARD_SITE_OFFSETS = List.of(
            new SiteOffset(28, 0), new SiteOffset(-28, 0),
            new SiteOffset(0, 28), new SiteOffset(0, -28),
            new SiteOffset(34, 20), new SiteOffset(-34, 20),
            new SiteOffset(34, -20), new SiteOffset(-34, -20),
            new SiteOffset(20, 34), new SiteOffset(-20, 34),
            new SiteOffset(20, -34), new SiteOffset(-20, -34),
            new SiteOffset(42, 0), new SiteOffset(-42, 0),
            new SiteOffset(0, 42), new SiteOffset(0, -42),
            new SiteOffset(46, 28), new SiteOffset(-46, 28),
            new SiteOffset(46, -28), new SiteOffset(-46, -28),
            new SiteOffset(28, 46), new SiteOffset(-28, 46),
            new SiteOffset(28, -46), new SiteOffset(-28, -46),
            new SiteOffset(58, 0), new SiteOffset(-58, 0),
            new SiteOffset(0, 58), new SiteOffset(0, -58),
            new SiteOffset(56, 36), new SiteOffset(-56, 36),
            new SiteOffset(56, -36), new SiteOffset(-56, -36),
            new SiteOffset(36, 56), new SiteOffset(-36, 56),
            new SiteOffset(36, -56), new SiteOffset(-36, -56),
            new SiteOffset(72, 18), new SiteOffset(-72, 18),
            new SiteOffset(72, -18), new SiteOffset(-72, -18),
            new SiteOffset(18, 72), new SiteOffset(-18, 72),
            new SiteOffset(18, -72), new SiteOffset(-18, -72),
            new SiteOffset(82, 0), new SiteOffset(-82, 0),
            new SiteOffset(0, 82), new SiteOffset(0, -82));

    /**
     * Extra perimeter sites used only after a safe ordinary lot was unavailable. They stay within
     * normal client view distances while reaching beyond Minecraft's broad POI village boundary.
     */
    private static final List<SiteOffset> RECOVERY_SITE_OFFSETS = List.of(
            new SiteOffset(96, 0), new SiteOffset(-96, 0),
            new SiteOffset(0, 96), new SiteOffset(0, -96),
            new SiteOffset(88, 36), new SiteOffset(-88, 36),
            new SiteOffset(88, -36), new SiteOffset(-88, -36),
            new SiteOffset(36, 88), new SiteOffset(-36, 88),
            new SiteOffset(36, -88), new SiteOffset(-36, -88),
            new SiteOffset(68, 68), new SiteOffset(-68, 68),
            new SiteOffset(68, -68), new SiteOffset(-68, -68),
            new SiteOffset(112, 0), new SiteOffset(-112, 0),
            new SiteOffset(0, 112), new SiteOffset(0, -112),
            new SiteOffset(104, 44), new SiteOffset(-104, 44),
            new SiteOffset(104, -44), new SiteOffset(-104, -44),
            new SiteOffset(44, 104), new SiteOffset(-44, 104),
            new SiteOffset(44, -104), new SiteOffset(-44, -104),
            new SiteOffset(80, 80), new SiteOffset(-80, 80),
            new SiteOffset(80, -80), new SiteOffset(-80, -80),
            new SiteOffset(128, 0), new SiteOffset(-128, 0),
            new SiteOffset(0, 128), new SiteOffset(0, -128),
            new SiteOffset(120, 48), new SiteOffset(-120, 48),
            new SiteOffset(120, -48), new SiteOffset(-120, -48),
            new SiteOffset(48, 120), new SiteOffset(-48, 120),
            new SiteOffset(48, -120), new SiteOffset(-48, -120),
            new SiteOffset(90, 90), new SiteOffset(-90, 90),
            new SiteOffset(90, -90), new SiteOffset(-90, -90));

    private VillageBankPlacementPolicy() {
    }

    /** Returns the bounded deterministic lot search, with an outer recovery ring when requested. */
    public static List<SiteOffset> candidateSiteOffsets(boolean recovery) {
        if (!recovery) {
            return STANDARD_SITE_OFFSETS;
        }
        List<SiteOffset> result = new ArrayList<>(
                STANDARD_SITE_OFFSETS.size() + RECOVERY_SITE_OFFSETS.size());
        result.addAll(STANDARD_SITE_OFFSETS);
        result.addAll(RECOVERY_SITE_OFFSETS);
        return List.copyOf(result);
    }

    /**
     * Scores a proven-safe lot. Fully outside village POI influence wins first, then the nearest
     * such lot; equal-distance sites prefer the fixed north-facing entrance looking back toward a
     * village to its north. The final region-scoped tie makes equal sites stable but varied.
     */
    public static CandidatePriority candidatePriority(
            boolean outsideVillage, int deltaX, int deltaZ, long regionKey) {
        long x = deltaX;
        long z = deltaZ;
        int entranceAlignmentPenalty = deltaZ > 0 ? 0 : (deltaZ == 0 ? 1 : 2);
        long packedOffset = (x << 32) ^ (deltaZ & 0xFFFFFFFFL);
        return new CandidatePriority(
                outsideVillage ? 0 : 1,
                x * x + z * z,
                entranceAlignmentPenalty,
                mix64(packedOffset ^ regionKey));
    }

    /** A known fallback remains active while a player loads its surrounding village outskirts. */
    public static boolean recoveryActive(
            int anchorX, int anchorZ, int playerX, int playerZ, int radius) {
        if (radius < 1) {
            return false;
        }
        long dx = (long) playerX - anchorX;
        long dz = (long) playerZ - anchorZ;
        long limit = radius;
        return dx * dx + dz * dz <= limit * limit;
    }

    /** First discovery needs vanilla proof; a persisted settlement may retry from its outskirts. */
    public static boolean shouldProbeVillage(
            boolean insideVanillaVillage, boolean knownVillageInRecoveryRange) {
        return insideVanillaVillage || knownVillageInRecoveryRange;
    }

    /**
     * A bank lot may clear dry replaceable plants and snow, but never fluid, solid blocks, or
     * block entities.
     */
    public static boolean acceptsVolumeCell(
            boolean air,
            boolean replaceable,
            boolean fluidEmpty,
            boolean hasBlockEntity) {
        return fluidEmpty && !hasBlockEntity && (air || replaceable);
    }

    /** Natural dry growth may occupy outdoor headroom without making a completed Bank unsafe. */
    public static boolean preservesOutdoorClearance(
            boolean air,
            boolean replaceable,
            boolean fluidEmpty,
            boolean hasBlockEntity) {
        return acceptsVolumeCell(air, replaceable, fluidEmpty, hasBlockEntity);
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

    /** Limits repeated terrain scans while players remain near a Banker-only village. */
    public static boolean retryDue(long gameTime, Long previousAttempt, long intervalTicks) {
        if (previousAttempt == null || gameTime < previousAttempt) {
            return true;
        }
        return gameTime - previousAttempt >= Math.max(1L, intervalTicks);
    }

    /**
     * A scan with no proven candidate may persist an explicitly retryable Banker-only state.
     * Search completeness no longer makes that state final: unloaded outer lots are reconsidered
     * as their chunks become available. A candidate that failed during placement remains transient
     * because the caller cannot broaden ownership assumptions after a protected or failed write.
     */
    public static boolean shouldPersistFallback(
            boolean ignoredSearchComplete, boolean hadCandidates) {
        return !hadCandidates;
    }

    /** Relative authored stair position; the Bank center X is supplied by the world planner. */
    public record EntranceStep(int yOffset, int zOffset) {
    }

    /** Horizontal candidate center relative to the persisted village center. */
    public record SiteOffset(int x, int z) {
    }

    /** Sortable quality key calculated only after the world preflight proves a lot safe. */
    public record CandidatePriority(
            int villageOverlapPenalty,
            long distanceSquared,
            int entranceAlignmentPenalty,
            long deterministicTieBreaker)
            implements Comparable<CandidatePriority> {
        @Override
        public int compareTo(CandidatePriority other) {
            int compared = Integer.compare(villageOverlapPenalty, other.villageOverlapPenalty);
            if (compared != 0) {
                return compared;
            }
            compared = Long.compare(distanceSquared, other.distanceSquared);
            if (compared != 0) {
                return compared;
            }
            compared = Integer.compare(entranceAlignmentPenalty, other.entranceAlignmentPenalty);
            return compared != 0
                    ? compared
                    : Long.compareUnsigned(deterministicTieBreaker,
                            other.deterministicTieBreaker);
        }
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
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
