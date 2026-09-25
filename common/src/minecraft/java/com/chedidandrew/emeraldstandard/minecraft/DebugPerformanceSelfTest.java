package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Runs only in the opt-in disposable server smoke world; exercises the real transformed method. */
final class DebugPerformanceSelfTest {
    @SuppressWarnings("unchecked")
    static void verify(ServerLevel level) {
        MinecraftServer server = level.getServer();
        BufferedWriter writer = null;
        Map<MinecraftServer, Object> sessions = null;
        Object ownedSession = null;
        try {
            var root = Files.createTempDirectory("tes-debug-performance-");
            var economy = new EconomyService();
            economy.start(root.resolve("economy"), 77, 0);
            var directory = Files.createDirectories(root.resolve("capture"));
            var timeline = directory.resolve("timeline.jsonl");
            AtomicBoolean delayed = new AtomicBoolean();
            writer = new BufferedWriter(Files.newBufferedWriter(timeline)) {
                @Override public void flush() throws IOException {
                    // Deliberately delay ONE recorder write in the loader's end callback.
                    // A vanilla pre-callback timing sample would miss this cost entirely.
                    if (delayed.compareAndSet(false, true)) {
                        long end = System.nanoTime() + 15_000_000L;
                        while (System.nanoTime() < end)
                            java.util.concurrent.locks.LockSupport.parkNanos(Math.max(1, end - System.nanoTime()));
                    }
                    super.flush();
                }
            };
            var type = Class.forName(DebugFlightRecorder.class.getName() + "$Session");
            var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var session = constructor.newInstance("PERFORMANCE-SMOKE-" + UUID.randomUUID(),
                    new UUID(0, 73), "PerformanceFixture", System.currentTimeMillis(),
                    System.currentTimeMillis() + 60_000, level.getGameTime(), directory, timeline, writer,
                    level.dimension().identifier().toString(), net.minecraft.core.BlockPos.ZERO, economy);
            var registry = DebugFlightRecorder.class.getDeclaredField("SESSIONS"); registry.setAccessible(true);
            sessions = (Map<MinecraftServer, Object>) registry.get(null);
            require(sessions.isEmpty(), "Smoke must not interrupt a real capture");
            sessions.put(server, session);
            ownedSession = session;
            var tick = MinecraftServer.class.getDeclaredMethod("tickServer", BooleanSupplier.class);
            tick.setAccessible(true);
            tick.invoke(server, (BooleanSupplier) () -> true);
            require(delayed.get(), "Native end callback did not sample the recorder");
            var timing = type.getDeclaredMethod("serverPerformance", MinecraftServer.class); timing.setAccessible(true);
            Map<String, Object> metrics = (Map<String, Object>) timing.invoke(session, server);
            require(((Number) metrics.get("sampledTicks")).longValue() == 1
                            && ((Number) metrics.get("maxMspt")).doubleValue() >= 15,
                    "Whole tick must include delayed loader-end recorder work: " + metrics);
            DebugFlightRecorder.completedServerTick(server, new Object(), 9_000_000_000L, true);
            require(((Number) ((Map<?, ?>) timing.invoke(session, server)).get("sampledTicks")).longValue() == 1,
                    "Wrong capture identity recorded a partial tick");
            ConstructionDiagnostics.record("bank:perf", "building", 1, 10, level.getGameTime(), "fixture");
            ConstructionDiagnostics.record("perf/1", "waiting_for_support", 1, 10, level.getGameTime(), "fixture");
            ConstructionDiagnostics.record("perf/2", "economic_work", 5, 10, level.getGameTime(), "fixture");
            ConstructionDiagnostics.progressed("perf/2");
            var workload = ConstructionDiagnostics.workloadSnapshot(System.nanoTime());
            require(((Number) workload.get("recentlyWorkingBanks")).intValue() >= 1
                    && ((Number) workload.get("recentlyWaitingProjects")).intValue() >= 1, "Working/waiting census");
            require(((Number) workload.get("recentlyWorkingProjects")).intValue() >= 1,
                    "Recent real progress must survive a subsequent budget/economic wait");
            require(((Number) ConstructionDiagnostics.workloadSnapshot(System.nanoTime() + 6_000_000_000L)
                    .get("recentlyWorkingSites")).intValue() == 0, "Stale observations counted as active");
            var finish = DebugFlightRecorder.class.getDeclaredMethod("finish",
                    MinecraftServer.class, EconomyService.class, type, String.class, boolean.class);
            finish.setAccessible(true);
            Object result = finish.invoke(null, server, economy, session, "performance_smoke", false);
            var reportMethod = result.getClass().getDeclaredMethod("report");
            var report = (java.nio.file.Path) reportMethod.invoke(result);
            require(report != null && Files.isRegularFile(report), "Capture not packaged: " + result);
            try (ZipFile zip = new ZipFile(report.toFile())) {
                var json = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("performance-summary.json"))
                        .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                require(json.getAsJsonObject("serverTicks").get("sampledTicks").getAsLong() == 1
                        && json.getAsJsonObject("construction").has("pendingBankSites")
                        && json.has("averageActiveRecorderTickMs"), "Packaged performance schema");
                String events = new String(zip.getInputStream(zip.getEntry("timeline.jsonl")).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                require(events.contains("server_workload") && events.contains("recentlyWorkingBanks"),
                        "Incremental time-correlated performance evidence missing");
            }
            require(DebugFlightRecorder.activeCapture(server) == null, "Finished recorder retained timing state");
            ConstructionDiagnostics.reset();
            System.out.println("PASS native debug performance: full transformed tick includes end-callback delay, workload freshness, ZIP/timeline and cleanup");
        } catch (Exception exception) {
            throw new IllegalStateException("Debug performance smoke failed", exception);
        } finally {
            if (sessions != null && ownedSession != null && sessions.get(server) == ownedSession) sessions.remove(server);
            if (writer != null) try { writer.close(); } catch (IOException ignored) { }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
