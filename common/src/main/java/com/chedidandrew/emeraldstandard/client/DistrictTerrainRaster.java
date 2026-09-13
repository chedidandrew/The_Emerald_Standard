package com.chedidandrew.emeraldstandard.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded session-only terrain cache. Zero samples mean unknown, never permission to load chunks. */
public final class DistrictTerrainRaster {
    public static final int WIDTH = 130, HEIGHT = 82, SAMPLE_BUDGET = 512, CACHE_LIMIT = 32768;
    @FunctionalInterface public interface Source { int color(int x, int z); }
    private record Key(int x, int z, int step) {}
    private record Tile(int color, long checked) {}
    private final Map<Key, Tile> cache = new LinkedHashMap<>(CACHE_LIMIT, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Tile> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private int originX, originZ, step, cursor;
    private long lastTick = Long.MIN_VALUE;
    private boolean dirty;

    public void view(DistrictMapViewport view) {
        double spacing = Math.max(DistrictMapViewport.WIDTH / view.scale() / (WIDTH - 2),
                DistrictMapViewport.HEIGHT / view.scale() / (HEIGHT - 2));
        int nextStep = 1;
        while (nextStep < spacing && nextStep < 1 << 28) nextStep *= 2;
        int nextX = aligned(view.worldX(DistrictMapViewport.X), nextStep);
        int nextZ = aligned(view.worldZ(DistrictMapViewport.Y), nextStep);
        if (step == nextStep && originX == nextX && originZ == nextZ) return;
        step = nextStep; originX = nextX; originZ = nextZ; cursor = 0;
        for (int i = 0; i < pixels.length; i++) {
            var key = key(i); var tile = cache.get(key);
            pixels[i] = tile == null || tile.color == 0 ? unknown(i) : tile.color;
        }
        dirty = true;
    }
    private static int aligned(double value, int step) {
        // A panned view may extend beyond the world. Keep arithmetic representable.
        return (int) (Math.floor(Math.clamp(value, -1_000_000_000, 1_000_000_000) / step) * step);
    }
    public int update(long tick, Source source) {
        if (step == 0 || tick == lastTick) return 0;
        lastTick = tick;
        int reads = 0, visited = 0;
        long deadline = System.nanoTime() + 2_000_000;
        while (visited++ < pixels.length && reads < SAMPLE_BUDGET && System.nanoTime() < deadline) {
            int i = cursor; cursor = (cursor + 1) % pixels.length;
            Key key = key(i); Tile old = cache.get(key);
            long age = old == null ? Long.MAX_VALUE : tick - old.checked;
            if (old != null && age >= 0 && age < (old.color == 0 ? 20 : 100)) continue;
            int color = 0;
            if (Math.abs((long) key.x) < 30_000_000 && Math.abs((long) key.z) < 30_000_000) {
                color = source.color(key.x, key.z); reads++;
            }
            // Preserve last-seen terrain when its chunk leaves the client's loaded area.
            if (color == 0 && old != null) color = old.color;
            cache.put(key, new Tile(color, tick));
            int pixel = color == 0 ? unknown(i) : color;
            if (pixels[i] != pixel) { pixels[i] = pixel; dirty = true; }
        }
        return reads;
    }
    private Key key(int i) {
        return new Key((int) Math.clamp((long) originX + (long) (i % WIDTH) * step, Integer.MIN_VALUE, Integer.MAX_VALUE),
                (int) Math.clamp((long) originZ + (long) (i / WIDTH) * step, Integer.MIN_VALUE, Integer.MAX_VALUE), step);
    }
    private static int unknown(int i) { return ((i % WIDTH / 4 + i / WIDTH / 4) & 1) == 0 ? 0xFF2B342C : 0xFF343D33; }
    public boolean consumeDirty() { boolean value = dirty; dirty = false; return value; }
    public int pixel(int x, int y) { return pixels[y * WIDTH + x]; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int step() { return step; }
    public int cachedTiles() { return cache.size(); }
}
