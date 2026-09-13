package com.chedidandrew.emeraldstandard.core;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Session-only, bounded work opportunities for daylight skipped while sites were active. */
public final class ConstructionCatchUp {
    public static final int MAX_SITES = 2048;
    public static final int MAX_EXTRA_PER_SITE = 8;
    public static final int MAX_EXTRA_PER_PULSE = 32;
    public static final long MAX_SKIPPED_TICKS = 12_000;
    private static final long RECENT_TICKS = 40;
    private final Map<String, Site> sites = new LinkedHashMap<>();
    private long game = Long.MIN_VALUE;
    private long daylight;
    private int rate;
    private long rotation;
    private long lastPulse = Long.MIN_VALUE;
    private boolean enabled;

    private static final class Site {
        long seen;
        long claimed = Long.MIN_VALUE;
        int credit;
        int grant;
    }

    public void reset() {
        sites.clear(); game = Long.MIN_VALUE; daylight = 0; rate = 0; rotation = 0;
        lastPulse = Long.MIN_VALUE; enabled = false;
    }

    /** Called once per server tick; duplicate calls from the two managers are harmless. */
    public void observe(long gameTick, long dayTick, int blocksPerSecond, boolean active, boolean pulse) {
        int safeRate = Math.max(1, Math.min(200, blocksPerSecond));
        if (gameTick == game && dayTick == daylight && safeRate == rate && active == enabled) return;
        long skipped = 0;
        if (game != Long.MIN_VALUE && active && enabled && safeRate == rate
                && gameTick >= game && dayTick >= daylight) {
            long elapsed = gameTick - game;
            long dayElapsed = dayTick - daylight;
            if (elapsed >= 0 && dayElapsed >= 0 && dayElapsed > elapsed)
                skipped = Math.min(MAX_SKIPPED_TICKS, dayElapsed - elapsed);
        } else sites.clear(); // New world, disabled mode, rate change or a backwards clock.
        if (skipped > 0) {
            int maximum = (int) (MAX_SKIPPED_TICKS * safeRate / 20);
            int earned = (int) (skipped * safeRate / 20);
            for (Site site : sites.values()) {
                if (game - site.seen <= RECENT_TICKS)
                    site.credit = Math.min(maximum, site.credit + earned);
            }
        }
        game = gameTick; daylight = dayTick; rate = safeRate; enabled = active;
        if (!active) { sites.clear(); return; }
        if (!pulse || gameTick == lastPulse) return;
        lastPulse = gameTick;
        sites.values().removeIf(site -> gameTick - site.seen > 1200);
        for (Site site : sites.values()) site.grant = 0;
        if (sites.isEmpty()) return;
        // Select BEFORE managers iterate, so banks and later villages cannot be starved by
        // whichever manager is called first. Only four sites can receive an eight-block boost.
        var ordered = sites.values().toArray(Site[]::new);
        int start = (int) Math.floorMod(rotation++, ordered.length);
        int remaining = MAX_EXTRA_PER_PULSE;
        for (int i = 0; i < ordered.length && remaining > 0; i++) {
            Site site = ordered[(start + i) % ordered.length];
            if (gameTick - site.seen > RECENT_TICKS || site.credit <= 0) continue;
            site.grant = Math.min(Math.min(site.credit, MAX_EXTRA_PER_SITE), remaining);
            remaining -= site.grant;
        }
    }

    /** A deferred/protected cell consumes its opportunity, never gains permission to overwrite. */
    public int claim(String key, long gameTick) {
        if (!enabled || gameTick != game) return 0;
        Site site = sites.get(key);
        if (site == null) {
            if (sites.size() >= MAX_SITES) {
                Iterator<String> oldest = sites.keySet().iterator(); oldest.next(); oldest.remove();
            }
            site = new Site(); sites.put(key, site);
        }
        site.seen = gameTick;
        if (site.claimed == gameTick) return 0;
        site.claimed = gameTick;
        int granted = site.grant;
        site.credit -= granted; site.grant = 0;
        return granted;
    }

    public int trackedSites() { return sites.size(); }
    public int credit(String key) { Site site = sites.get(key); return site == null ? 0 : site.credit; }
}
