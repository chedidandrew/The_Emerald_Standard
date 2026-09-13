package com.chedidandrew.emeraldstandard.minecraft;

/** Cooperative server-wide debug budget, independent of the number of cities/sites. */
public final class ForcedDevelopmentWorkBudget {
    /** Independent pools must not share a tick-derived cursor (parity can starve a whole pool). */
    public static final class Rotation {
        private final java.util.Map<String, Long> cursors = new java.util.HashMap<>();
        public int next(String pool, int size) {
            if (size <= 0) throw new IllegalArgumentException("Empty scheduling pool");
            long cursor = cursors.getOrDefault(pool,0L);
            cursors.put(pool,cursor == Long.MAX_VALUE ? 0 : cursor+1);
            return Math.floorMod(cursor,size);
        }
        public void reset() { cursors.clear(); }
    }
    private Object server;
    private long tick = Long.MIN_VALUE, deadline;
    private int remaining, sites;
    public void begin(Object server, long tick, long now, boolean lagging) {
        if (this.server == server && this.tick == tick) return;
        this.server = server; this.tick = tick;
        remaining = lagging ? 16 : 128;
        sites = 2;
        deadline = now + (lagging ? 1_000_000L : 4_000_000L);
    }
    public int claim(long now) {
        if (sites <= 0 || !hasTime(now)) return 0;
        sites--;
        int grant = Math.min(64, remaining);
        remaining -= grant;
        return grant;
    }
    public boolean hasTime(long now) { return now < deadline; }
    public void reset() { server = null; tick = Long.MIN_VALUE; remaining = sites = 0; }
}
