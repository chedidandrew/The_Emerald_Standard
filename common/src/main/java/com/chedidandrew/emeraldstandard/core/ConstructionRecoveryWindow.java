package com.chedidandrew.emeraldstandard.core;

/** One unfinished site's temporary ordering recovery. Never grants block-change authority. */
public final class ConstructionRecoveryWindow {
    public enum Mode { NORMAL, SUPPORTS_FIRST, NATIVE_ORDER }
    private int highWater = -1, eligibleTicks;
    private long lastTick = Long.MIN_VALUE;
    private boolean lastEligible;
    private int sweepPrefix = -1, sweepNext = -1;
    /** Temporary scan cursor, never the saved completed prefix or placement authority. */
    public int resumeAfter(int pending, int target) {
        if (sweepPrefix != pending) { sweepPrefix = pending; sweepNext = pending + 1; }
        return sweepNext > pending && sweepNext < target ? sweepNext : pending + 1;
    }
    public void scannedThrough(int pending, int next, int target) {
        sweepPrefix = pending;
        sweepNext = next >= target ? pending + 1 : Math.max(pending + 1, next);
    }
    private void resetSweep() { sweepPrefix = -1; sweepNext = -1; }
    public Mode mode() {
        return eligibleTicks < 100 ? Mode.NORMAL : eligibleTicks < 120 ? Mode.SUPPORTS_FIRST : Mode.NATIVE_ORDER;
    }
    public Mode mode(long tick) {
        if (lastTick != Long.MIN_VALUE && (tick < lastTick || tick-lastTick > 40)) {
            eligibleTicks=0; lastEligible=false;
            resetSweep();
        }
        return mode();
    }
    public void observe(long tick, int prefix, boolean eligible, boolean complete) {
        if (complete || prefix > highWater || tick < lastTick) {
            highWater = Math.max(highWater, prefix);
            eligibleTicks = 0;
            resetSweep();
        } else if (!eligible) {
            eligibleTicks = 0;
            resetSweep();
        } else if (lastEligible && lastTick != Long.MIN_VALUE) {
            eligibleTicks += (int)Math.min(20, Math.max(0, tick - lastTick));
            // Self-expiring episode: an unchanged stall must earn another attempt.
            if (eligibleTicks >= 220) eligibleTicks = 0;
        }
        lastTick = tick;
        lastEligible = eligible && !complete;
    }
}
