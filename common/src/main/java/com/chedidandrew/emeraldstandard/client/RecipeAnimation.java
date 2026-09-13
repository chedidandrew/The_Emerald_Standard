package com.chedidandrew.emeraldstandard.client;

/** UI-only time and slot ordering; independent of world ticks (the reader may pause the world). */
public final class RecipeAnimation {
    public static final long FRAME_MILLIS = 900;
    private long lastMillis = -1;
    private long elapsedMillis;

    public long frame(long nowMillis, boolean paused) {
        if (lastMillis >= 0 && !paused) elapsedMillis += Math.max(0, nowMillis - lastMillis);
        lastMillis = nowMillis;
        return elapsedMillis / FRAME_MILLIS;
    }

    /** Enumerates every distinct placement, without ever overlapping shapeless ingredients. */
    public static int shapelessSlot(int ingredient, int count, long frame) {
        if (count < 1 || count > 9 || ingredient < 0 || ingredient >= count)
            throw new IllegalArgumentException("Invalid crafting ingredient count/index");
        int permutations = 1;
        for (int i = 0; i < count; i++) permutations *= 9 - i;
        long rank = Math.floorMod(frame, permutations);
        int[] available = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        int remaining = 9;
        for (int i = 0; i <= ingredient; i++) {
            permutations /= remaining;
            int chosen = (int) (rank / permutations);
            rank %= permutations;
            int slot = available[chosen];
            if (i == ingredient) return slot;
            System.arraycopy(available, chosen + 1, available, chosen, remaining - chosen - 1);
            remaining--;
        }
        throw new AssertionError();
    }
}
