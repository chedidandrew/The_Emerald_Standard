package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Negative evidence only: a cache hit can postpone a survey, never authorize construction. */
final class SiteSurveyRejections {
    static final int LIMIT = 2048;
    private static final Map<ServerLevel, Map<Object, Entry>> LEVELS = new IdentityHashMap<>();
    private record Stamp(LevelChunk chunk, long revision) {}
    private record Entry(long tick, List<Stamp> stamps, Object rejection) {}
    private SiteSurveyRejections() {}
    static void reset() { LEVELS.clear(); }

    static Object get(ServerLevel level, Object key, int x, int z, int radius, int lifetime) {
        var entries = LEVELS.get(level);
        if (entries == null) { DebugWork.count("plotCache.miss"); return null; }
        var entry = entries.get(key);
        if (entry == null) { DebugWork.count("plotCache.miss"); return null; }
        long age = level.getGameTime() - entry.tick;
        boolean expired = age < 0 || age >= lifetime;
        if (expired || !entry.stamps.equals(stamps(level, x, z, radius))) {
            DebugWork.count(expired ? "plotCache.expired" : "plotCache.worldChanged");
            entries.remove(key);
            return null;
        }
        DebugWork.count("plotCache.hit");
        return entry.rejection;
    }

    static <T> T remember(ServerLevel level, Object key, int x, int z, int radius, T rejection) {
        var entries = LEVELS.computeIfAbsent(level, unused -> new LinkedHashMap<>(32, .75f, true));
        entries.put(key, new Entry(level.getGameTime(), stamps(level, x, z, radius), rejection));
        if (entries.size() > LIMIT) {
            entries.remove(entries.keySet().iterator().next());
            DebugWork.count("plotCache.evicted");
        }
        return rejection;
    }

    private static List<Stamp> stamps(ServerLevel level, int x, int z, int radius) {
        var result = new ArrayList<Stamp>();
        for (int cx = (x-radius)>>4; cx <= (x+radius)>>4; cx++)
            for (int cz = (z-radius)>>4; cz <= (z+radius)>>4; cz++) {
                // Identity detects unloading/reloading; the counter detects all block-state writes.
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                result.add(new Stamp(chunk, chunk instanceof SurveyChunkRevision revision
                        ? revision.emeraldSurveyRevision() : 0));
            }
        return result;
    }
}
