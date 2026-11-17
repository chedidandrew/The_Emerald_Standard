package com.chedidandrew.emeraldstandard.core;

/** Retry a complete surveyed sweep at each fit level; a new frontier starts with full decor. */
public final class ArchitectureSiteFit {
    public static final int LEVELS = 4;
    public static final int MAX_FRONTIER_DEPTH = 32;
    private ArchitectureSiteFit() { }
    /** Stable one-in-ten optional landscapes; retries and reloads never reroll the choice. */
    public static boolean decorated(EconomyState.VillageRecord village, EconomyState.VillageProject project) {
        long seed=project.designSeed ^ project.projectId ^ village.villageId.getMostSignificantBits()
                ^ village.villageId.getLeastSignificantBits();
        seed=(seed^(seed>>>30))*0xbf58476d1ce4e5b9L;
        seed=(seed^(seed>>>27))*0x94d049bb133111ebL;
        return Math.floorMod(seed^(seed>>>31),10)==0;
    }
    public static int searchLevel(EconomyState.VillageRecord v, EconomyState.VillageProject p) {
        return decorated(v,p) ? outdoorLevel(p.materializationFailures) : 3;
    }
    public static int searchDepth(EconomyState.VillageRecord v, EconomyState.VillageProject p) {
        return decorated(v,p) ? frontierDepth(p.materializationFailures)
                : 1+Math.min(MAX_FRONTIER_DEPTH-1,Math.max(0,p.materializationFailures));
    }
    public static int outdoorLevel(int failedSweeps) { return Math.floorMod(Math.max(0,failedSweeps),LEVELS); }
    public static int frontierDepth(int failedSweeps) {
        return 1 + Math.min(MAX_FRONTIER_DEPTH-1,Math.max(0,failedSweeps)/LEVELS);
    }
    public static String label(int level) {
        return switch(level) { case 0->"full landscaped lot";case 1->"smaller rear court";
            case 2->"front and side decor";case 3->"building and essential access";
            default->throw new IllegalArgumentException("Invalid outdoor fit level"); };
    }
}
