package com.chedidandrew.emeraldstandard.core;

/** Short, non-accumulating retry windows for searches that have no reserved world site. */
public final class ProjectSiteRetry {
    public static final long MAX_WAIT_TICKS = 600L;
    private ProjectSiteRetry() {}

    public static boolean searching(EconomyState.VillageProject p) {
        return p.originPos == 0L && !p.materializedComplete && !p.manualRepairRequired && !p.abstractOnly;
    }

    public static long deadline(long now, int failures) {
        long delay = Math.min(MAX_WAIT_TICKS, 200L << Math.min(2, Math.max(0, failures - 1)));
        return add(Math.max(0L, now), delay);
    }

    /** Clamp once in saved state; never slide a deadline forward on each polling tick. */
    public static long boundedDeadline(long now, long saved) {
        return Math.min(Math.max(0L, saved), add(Math.max(0L, now), MAX_WAIT_TICKS));
    }

    public static long remaining(long now, long deadline) {
        return Math.max(0L, deadline - Math.max(0L, now));
    }

    private static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
}
