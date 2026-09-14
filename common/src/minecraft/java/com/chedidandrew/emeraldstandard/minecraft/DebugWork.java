package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.debug.WorkMeasurements;

/** Server-thread capture context. Disabled scopes share one object and do not read the clock. */
final class DebugWork {
    private static final ThreadLocal<WorkMeasurements> CURRENT = new ThreadLocal<>();
    private static final Scope DISABLED = new Scope(null, "", 0);
    static void activate(WorkMeasurements measurements) { CURRENT.set(measurements); }
    static void deactivate() { CURRENT.remove(); }
    static void count(String name) {
        var measurements = CURRENT.get();
        if (measurements != null) measurements.count(name);
    }
    static Scope scope(String name) {
        var measurements = CURRENT.get();
        return measurements == null ? DISABLED : new Scope(measurements, name, System.nanoTime());
    }
    static void job(String key, long tick, String phase, boolean progress) {
        var measurements = CURRENT.get();
        if (measurements != null) measurements.job(key, tick, phase, progress);
    }
    static void run(String name, Runnable work) {
        try (var ignored = scope(name)) { work.run(); }
    }
    record Scope(WorkMeasurements measurements, String name, long start) implements AutoCloseable {
        @Override public void close() {
            if (measurements != null) measurements.record(name, System.nanoTime() - start);
        }
    }
}
