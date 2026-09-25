package com.chedidandrew.emeraldstandard.minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

/** Explicit opt-in JVM samples; does not request environment/property/file/network events. */
final class DebugJvmProfile implements AutoCloseable {
    private final Recording recording;
    private DebugJvmProfile(Recording recording) { this.recording = recording; }
    static DebugJvmProfile start(Path directory) throws IOException {
        if (!FlightRecorder.isAvailable()) throw new IOException("JVM Flight Recorder is unavailable");
        Recording recording = new Recording();
        try {
            recording.setName("TES diagnostic samples");
            recording.setMaxSize(16L * 1024 * 1024);
            recording.setMaxAge(Duration.ofSeconds(60));
            recording.setDuration(Duration.ofSeconds(60));
            recording.setDestination(directory.resolve("runtime-profile.jfr"));
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20));
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "100/s");
            recording.enable("jdk.GarbageCollection");
            recording.enable("jdk.GCHeapSummary");
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(20));
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(20));
            recording.start();
            return new DebugJvmProfile(recording);
        } catch (IOException | RuntimeException failure) {
            recording.close();
            throw failure;
        }
    }
    String state() { return recording.getState().name(); }
    @Override public void close() {
        if (recording.getState() == RecordingState.RUNNING) recording.stop();
        recording.close();
    }
}
