package com.chedidandrew.emeraldstandard.minecraft;

/**
 * Loader-neutral limits and calculations for bounded physical village development.
 *
 * <p>Keeping these rules independent from Minecraft world access makes the fairness, pacing, and
 * legacy-boundary contracts directly regression-testable.</p>
 */
public final class VillageMaterializationPolicy {
    public static final int MAX_NEARBY_VILLAGES_PER_PASS = 16;
    public static final int MAX_SETTLER_HOME_RADIUS = 32;

    private VillageMaterializationPolicy() {
    }

    public static int villagesToProcess(int nearbyVillageCount) {
        return villagesToProcess(nearbyVillageCount, MAX_NEARBY_VILLAGES_PER_PASS);
    }

    public static int villagesToProcess(int nearbyVillageCount, int remainingPassCapacity) {
        return Math.min(
                Math.max(0, remainingPassCapacity),
                Math.min(MAX_NEARBY_VILLAGES_PER_PASS, Math.max(0, nearbyVillageCount)));
    }

    public static int rotatingIndex(int firstIndex, int step, int villageCount) {
        if (villageCount <= 0) {
            throw new IllegalArgumentException("villageCount must be positive");
        }
        return (int) Math.floorMod((long) firstIndex + step, villageCount);
    }

    public static int settlerHomeRadius(int developmentRadius) {
        return Math.min(MAX_SETTLER_HOME_RADIUS, Math.max(8, developmentRadius / 3));
    }

    /** Failed attempts are paced too; clock rollback starts a fresh attempt window safely. */
    public static boolean settlerAttemptDue(
            long currentGameTick, Long previousAttemptTick, long configuredIntervalTicks) {
        if (previousAttemptTick == null
                || previousAttemptTick < 0L
                || currentGameTick < previousAttemptTick) {
            return true;
        }
        long interval = Math.max(1L, configuredIntervalTicks);
        return currentGameTick - previousAttemptTick >= interval;
    }

    public static SiteAvailability completedSiteSearch(
            boolean siteFound, boolean sawUnloadedCandidate) {
        if (siteFound) {
            return SiteAvailability.AVAILABLE;
        }
        return sawUnloadedCandidate
                ? SiteAvailability.INCOMPLETE_UNLOADED
                : SiteAvailability.UNSAFE;
    }

    /**
     * Conservative, palette-independent envelope for projects whose old save lacks persisted
     * bounds. Trails are intentionally excluded because they are shared, non-exclusive cells.
     */
    public static RelativeBounds conservativeProjectBounds(
            int width, int depth, int height, int maximumTerrainDrop) {
        if (width < 0 || depth < 0 || height < 0 || maximumTerrainDrop < 0) {
            throw new IllegalArgumentException("Project dimensions and terrain drop must be non-negative");
        }
        return new RelativeBounds(-1, -maximumTerrainDrop, -2, width, height, depth);
    }

    public enum SiteAvailability {
        AVAILABLE,
        INCOMPLETE_UNLOADED,
        UNSAFE
    }

    public record RelativeBounds(
            int minimumX,
            int minimumY,
            int minimumZ,
            int maximumX,
            int maximumY,
            int maximumZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minimumX
                    && x <= maximumX
                    && y >= minimumY
                    && y <= maximumY
                    && z >= minimumZ
                    && z <= maximumZ;
        }
    }
}
