package com.chedidandrew.emeraldstandard.core;

/** Debug-only placement preferences. None of these waive world/protection admission checks. */
public final class ForcedSiteSearchPolicy {
    private ForcedSiteSearchPolicy() { }
    public static int candidates(boolean forced) { return forced ? 32 : 1; }
    /** Keep a two-block buffer, including frozen grading bounds, not just visible walls. */
    public static int lotPadding(boolean forced) { return forced ? 2 : 4; }
    public static boolean bankSetback(boolean forced) { return !forced; }
    public static int frontierDepth(EconomyState.VillageRecord v, EconomyState.VillageProject p, boolean forced) {
        return forced ? Math.min(ArchitectureSiteFit.MAX_FRONTIER_DEPTH,
                2 + Math.min(ArchitectureSiteFit.MAX_FRONTIER_DEPTH - 2, Math.max(0, p.materializationFailures)))
                : ArchitectureSiteFit.searchDepth(v,p);
    }
}
