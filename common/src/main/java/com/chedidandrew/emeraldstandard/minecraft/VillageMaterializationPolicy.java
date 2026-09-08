package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Loader-neutral limits and calculations for bounded physical village development.
 *
 * <p>Keeping these rules independent from Minecraft world access makes the fairness, pacing, and
 * legacy-boundary contracts directly regression-testable.</p>
 */
public final class VillageMaterializationPolicy {
    public static final int MAX_NEARBY_VILLAGES_PER_PASS = 16;
    public static final int MAX_SETTLER_HOME_RADIUS = 32;
    /** Visible authored cells placed after terrain supports when a project breaks ground. */
    public static final int GROUNDBREAKING_VISIBLE_BLOCKS = 24;
    private static final int MINIMUM_RELOCATION_MISMATCHES = 12;
    private static final int RELOCATION_PERCENT = 30;
    private static final int SITE_EXPANSION_RING_LIMIT = 8;
    private static final int SITE_EXPANSION_RADIUS_STEP = 24;
    private static final List<SiteOffset> INITIAL_PROJECT_SITE_OFFSETS = List.of(
            new SiteOffset(30, 0), new SiteOffset(-30, 0),
            new SiteOffset(0, 30), new SiteOffset(0, -30),
            new SiteOffset(30, 18), new SiteOffset(-30, 18),
            new SiteOffset(30, -18), new SiteOffset(-30, -18),
            new SiteOffset(38, 38), new SiteOffset(-38, 38),
            new SiteOffset(38, -38), new SiteOffset(-38, -38),
            new SiteOffset(48, 0), new SiteOffset(-48, 0),
            new SiteOffset(0, 48), new SiteOffset(0, -48),
            new SiteOffset(52, 26), new SiteOffset(-52, 26),
            new SiteOffset(52, -26), new SiteOffset(-52, -26),
            new SiteOffset(26, 52), new SiteOffset(-26, 52),
            new SiteOffset(26, -52), new SiteOffset(-26, -52),
            new SiteOffset(60, 0), new SiteOffset(-60, 0),
            new SiteOffset(0, 60), new SiteOffset(0, -60),
            new SiteOffset(58, 34), new SiteOffset(-58, 34),
            new SiteOffset(58, -34), new SiteOffset(-58, -34),
            new SiteOffset(34, 58), new SiteOffset(-34, 58),
            new SiteOffset(34, -58), new SiteOffset(-34, -58),
            new SiteOffset(70, 18), new SiteOffset(-70, 18),
            new SiteOffset(70, -18), new SiteOffset(-70, -18),
            new SiteOffset(18, 70), new SiteOffset(-18, 70),
            new SiteOffset(18, -70), new SiteOffset(-18, -70),
            new SiteOffset(72, 48), new SiteOffset(-72, 48),
            new SiteOffset(72, -48), new SiteOffset(-72, -48),
            new SiteOffset(48, 72), new SiteOffset(-48, 72),
            new SiteOffset(48, -72), new SiteOffset(-48, -72),
            new SiteOffset(84, 0), new SiteOffset(-84, 0),
            new SiteOffset(0, 84), new SiteOffset(0, -84),
            new SiteOffset(78, 60), new SiteOffset(-78, 60),
            new SiteOffset(78, -60), new SiteOffset(-78, -60),
            new SiteOffset(60, 78), new SiteOffset(-60, 78),
            new SiteOffset(60, -78), new SiteOffset(-60, -78));

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

    /** Uses the audit ordinal, not the divisible gate pulse, so every project is visited. */
    public static int rotatingAuditIndex(
            long staggeredPulse, long auditPulses, int candidateCount) {
        if (auditPulses <= 0L || candidateCount <= 0) {
            throw new IllegalArgumentException("Audit interval and candidate count must be positive");
        }
        long auditOrdinal = Math.floorDiv(staggeredPulse, auditPulses);
        return (int) Math.floorMod(auditOrdinal, candidateCount);
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
     * Returns a deterministic, failure-expanded set of candidate village lots.
     *
     * <p>The first attempt preserves the original 64-site search exactly. Each persisted failed
     * sweep adds a square ring outside that envelope, allowing a village surrounded by forest,
     * mountains, claims, or earlier projects to keep searching. The cap keeps every sweep bounded;
     * unloaded candidates are handled by the caller's persisted retry gate rather than hot-looped
     * or force-loaded.</p>
     */
    public static List<SiteOffset> projectSiteOffsets(int priorFailures) {
        int expansionRings = Math.min(
                SITE_EXPANSION_RING_LIMIT, Math.max(0, priorFailures));
        LinkedHashSet<SiteOffset> offsets = new LinkedHashSet<>(INITIAL_PROJECT_SITE_OFFSETS);
        for (int ring = 1; ring <= expansionRings; ring++) {
            int radius = 84 + ring * SITE_EXPANSION_RADIUS_STEP;
            int half = radius / 2;
            int threeQuarter = radius * 3 / 4;
            appendRing(offsets, radius, 0);
            appendRing(offsets, radius, radius);
            appendRing(offsets, radius, half);
            appendRing(offsets, radius, threeQuarter);
        }
        return List.copyOf(new ArrayList<>(offsets));
    }

    private static void appendRing(
            LinkedHashSet<SiteOffset> offsets, int major, int minor) {
        if (minor == 0) {
            offsets.add(new SiteOffset(major, 0));
            offsets.add(new SiteOffset(-major, 0));
            offsets.add(new SiteOffset(0, major));
            offsets.add(new SiteOffset(0, -major));
            return;
        }
        offsets.add(new SiteOffset(major, minor));
        offsets.add(new SiteOffset(-major, minor));
        offsets.add(new SiteOffset(major, -minor));
        offsets.add(new SiteOffset(-major, -minor));
        if (major != minor) {
            offsets.add(new SiteOffset(minor, major));
            offsets.add(new SiteOffset(-minor, major));
            offsets.add(new SiteOffset(minor, -major));
            offsets.add(new SiteOffset(-minor, -major));
        }
    }

    /**
     * Caps physical construction to earned economic progress.
     *
     * <p>The groundbreaking prefix includes terrain supports and a small visible portion of the
     * authored structure. One final block remains withheld until economic completion so an
     * unfinished project can never become physically complete or economically operational.</p>
     */
    public static int constructionTargetBlocks(
            double economicProgress,
            boolean economicComplete,
            int totalBlocks,
            int groundbreakingPrefix) {
        int total = Math.max(0, totalBlocks);
        if (total == 0) {
            return 0;
        }
        if (economicComplete) {
            return total;
        }
        int incompleteMaximum = total - 1;
        if (incompleteMaximum <= 0) {
            return 0;
        }
        int start = Math.min(incompleteMaximum, Math.max(1, groundbreakingPrefix));
        double progress = Double.isFinite(economicProgress)
                ? Math.max(0.0, Math.min(1.0, economicProgress))
                : 0.0;
        int earnedAfterGroundbreaking = (int) Math.floor(
                progress * (incompleteMaximum - start));
        return Math.min(incompleteMaximum, start + earnedAfterGroundbreaking);
    }

    /**
     * Accepts only a deterministic append-only road-plan expansion.
     *
     * <p>The persisted cursor must remain inside the old frozen prefix. Equality represents the
     * ordinary unchanged plan; a smaller regenerated target is never adopted.</p>
     */
    public static boolean compatibleTrailTarget(
            int materializedBlocks, int persistedTotal, int plannedTotal) {
        return materializedBlocks >= 0
                && persistedTotal > 0
                && materializedBlocks <= persistedTotal
                && plannedTotal >= persistedTotal;
    }

    /**
     * Keeps ordinary trail adoption permissive while making a center retrofit lane-aware.
     *
     * <p>A legacy placement may adopt any recognized TES trail material. The independent,
     * versioned center-lane migration accepts dirt path and intentional gravel accents, so only an
     * old coarse-dirt center cell is revisited.</p>
     */
    public static boolean trailSurfaceSatisfied(
            boolean centerSurfaceRetrofit,
            boolean recognizedTrailSurface,
            boolean acceptedCenterSurface) {
        return centerSurfaceRetrofit ? acceptedCenterSurface : recognizedTrailSurface;
    }

    /**
     * Limits a one-time center-lane migration to its old coarse-dirt material.
     *
     * <p>Initial trail construction may pave approved natural ground. A later resurfacing pass can
     * replace only the legacy center material it is intended to remove, so ordinary skipped
     * terrain, gravel accents, and non-trail construction remain untouched. Vanilla cannot prove
     * whether coarse dirt was later placed by a player, so the caller still applies the registered
     * claim-protection hook as well as block-entity, fluid, clearance, and loaded-chunk checks.</p>
     */
    public static boolean mayApplyTrailSurface(
            boolean centerSurfaceRetrofit,
            boolean naturalGround,
            boolean recognizedTrailSurface,
            boolean legacyCenterSurface) {
        return centerSurfaceRetrofit
                ? recognizedTrailSurface && legacyCenterSurface
                : naturalGround || recognizedTrailSurface;
    }

    /** Returns the finished lane material after replacing old center coarse-dirt accents. */
    public static TrailSurface plannedTrailSurface(
            boolean desertPalette, boolean shoulder, long detail) {
        TrailSurface frozen = frozenTrailSurface(desertPalette, shoulder, detail);
        return !shoulder && frozen == TrailSurface.COARSE_DIRT
                ? TrailSurface.DIRT_PATH
                : frozen;
    }

    /**
     * Preserves the shipped material formula for cursor-indexed trail prefixes.
     *
     * <p>Do not change this ordering or its divisors. Visual material changes belong in an
     * separately versioned migration so persisted trail indices keep the exact same cells and
     * states.</p>
     */
    public static TrailSurface frozenTrailSurface(
            boolean desertPalette, boolean shoulder, long detail) {
        if (desertPalette) {
            return shoulder || Math.floorMod(detail, 5L) == 0L
                    ? TrailSurface.COARSE_DIRT
                    : TrailSurface.GRAVEL;
        }
        if (shoulder || Math.floorMod(detail, 11L) == 0L) {
            return TrailSurface.COARSE_DIRT;
        }
        return Math.floorMod(detail, 7L) == 0L
                ? TrailSurface.GRAVEL
                : TrailSurface.DIRT_PATH;
    }

    /** Identifies only coordinates whose frozen primary-lane plan formerly chose coarse dirt. */
    public static boolean legacyCenterSurfaceNeedsRetrofit(
            boolean desertPalette, long detail) {
        return frozenTrailSurface(desertPalette, false, detail) == TrailSurface.COARSE_DIRT;
    }

    /**
     * Separates ordinary player customization from destruction severe enough to justify a new lot.
     *
     * <p>Any authored mismatch suspends the building's economic benefit, but relocation requires
     * both a meaningful absolute loss and at least thirty percent of its structure cells. That
     * keeps a changed window, door, workstation, or decorated room from cloning a building while
     * still allowing a village to recover after demolition.</p>
     */
    public static IntegrityDecision assessIntegrity(
            int expectedStructureCells, int mismatchedStructureCells) {
        int expected = Math.max(0, expectedStructureCells);
        int mismatched = Math.max(0, Math.min(expected, mismatchedStructureCells));
        if (mismatched == 0) {
            return IntegrityDecision.INTACT;
        }
        int proportionalThreshold = (int) Math.ceil(
                expected * (RELOCATION_PERCENT / 100.0));
        int relocationThreshold = Math.max(
                MINIMUM_RELOCATION_MISMATCHES, proportionalThreshold);
        return mismatched >= relocationThreshold
                ? IntegrityDecision.RELOCATE
                : IntegrityDecision.UNSAFE;
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
        SEARCH_INCOMPLETE,
        INCOMPLETE_UNLOADED,
        UNSAFE
    }

    public enum IntegrityDecision {
        INTACT,
        UNSAFE,
        RELOCATE
    }

    public enum TrailSurface {
        DIRT_PATH,
        GRAVEL,
        COARSE_DIRT
    }

    public record SiteOffset(int x, int z) {
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
