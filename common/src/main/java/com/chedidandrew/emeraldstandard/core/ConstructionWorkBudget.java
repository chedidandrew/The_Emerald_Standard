package com.chedidandrew.emeraldstandard.core;

/**
 * Server-thread admission control, not a timer that interrupts a world edit.
 * Reserved allowances include ordinary work and sleep catch-up. Unused reservations
 * expire next pulse; they are not banked into an unbounded burst.
 */
public final class ConstructionWorkBudget {
    public static final int MAX_OPERATIONS = 64, MAX_JOBS = 4, MAX_JOB_OPERATIONS = 16;
    public static final long NANOS = 4_000_000L;
    public enum Lane { BANK, VILLAGE }
    private long tick = Long.MIN_VALUE, pulse, deadline;
    private final long[] requested = {-100, -100};
    private final boolean[] entered = new boolean[2];
    private Lane preferred = Lane.BANK;
    private int remaining, jobs;

    public void reset() {
        tick = Long.MIN_VALUE; pulse = 0; deadline = 0;
        requested[0] = requested[1] = -100;
        entered[0] = entered[1] = false;
        preferred = Lane.BANK; remaining = jobs = 0;
    }

    public void begin(long gameTick, boolean constructionPulse) {
        if (gameTick == tick) return;
        if (gameTick < tick) reset();
        tick = gameTick;
        if (constructionPulse) pulse++;
        deadline = 0; entered[0] = entered[1] = false;
        remaining = constructionPulse ? MAX_OPERATIONS : 0; jobs = 0;
    }

    public boolean enter(Lane lane, long now) {
        int i = lane.ordinal(), other = 1 - i;
        requested[i] = pulse;
        if (entered[i]) return hasTime(now);
        if (remaining == 0 || (deadline != 0 && !hasTime(now))) return false;
        // Reserve first turn for the other active family. Rotate by construction pulses/
        // actual admissions, never game-tick parity (which aliases at slow block rates).
        if (deadline == 0 && lane != preferred && pulse - requested[other] <= 2) return false;
        if (deadline == 0) {
            deadline = now + NANOS;
            preferred = Lane.values()[other];
        }
        entered[i] = true;
        return true;
    }

    public int claim(int requestedOperations, long now) {
        // One admitted job may finish preflight after the deadline. It gets one bounded batch,
        // otherwise a cold template could lose every turn forever. Later jobs must yield.
        if (deadline == 0 || (jobs > 0 && !hasTime(now)) || jobs >= MAX_JOBS || requestedOperations <= 0) return 0;
        int allowed = Math.min(Math.min(requestedOperations, MAX_JOB_OPERATIONS), remaining);
        if (allowed > 0) { remaining -= allowed; jobs++; }
        return allowed;
    }
    public Lane preferred() { return preferred; }
    public boolean hasTime(long now) { return deadline != 0 && now < deadline; }
}
