package com.chedidandrew.emeraldstandard.debug;

import com.chedidandrew.emeraldstandard.core.BankConstruction;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DebugPerformanceRegressionTest {
    public static void main(String[] args) throws Exception {
        ServerTickMetrics stats = new ServerTickMetrics(0);
        check(stats.snapshot(1).get("meanMspt") == null, "No samples must be unavailable, not zero lag");
        for (int ms = 1; ms <= 100; ms++) stats.record(ms * 1_000_000L, true);
        var report = stats.snapshot(5_000_000_000L);
        check(number(report, "meanMspt") == 50.5 && number(report, "maxMspt") == 100, "Exact mean/max");
        check(number(report, "ticksOver50Ms") == 50 && number(report, "ticksOver100Ms") == 0, "Strict slow thresholds");
        check(number(report, "observedTicksPerSecond") == 20, "TPS is measured elapsed time, not inverse MSPT");
        var window = (Map<?, ?>) report.get("recentWindow");
        check(number(window, "p50Mspt") == 50 && number(window, "p95Mspt") == 95
                && number(window, "p99Mspt") == 99, "Nearest-rank percentiles");
        stats.record(500_000_000L, true);
        stats.record(900_000_000L, false);
        stats.record(0, true);
        for (int i = 0; i < 50_000; i++) stats.record(2_000_000L, true);
        report = stats.snapshot(10_000_000_000L);
        window = (Map<?, ?>) report.get("recentWindow");
        check(number(window, "sampledTicks") == 1200 && number(window, "p99Mspt") == 2, "Bounded recent ring");
        check(number(report, "sampledTicks") == 50_101 && number(report, "maxMspt") == 500,
                "Whole-capture aggregate outlives recent window");
        check(number(report, "ticksOver250Ms") == 1 && number(report, "interruptedTicksExcluded") == 1
                && number(report, "invalidTicksExcluded") == 1, "Incomplete ticks must not become valid samples");

        var state = EconomyState.fresh(99, 0, 0);
        var village = state.village(new UUID(0, 7));
        var queued = new EconomyState.VillageProject();
        var placed = new EconomyState.VillageProject(); placed.originPos = 5; placed.constructionStarted = true;
        placed.blocked = true;
        var finished = new EconomyState.VillageProject(); finished.materializedComplete = true;
        var abstractProject = new EconomyState.VillageProject(); abstractProject.abstractOnly = true;
        village.projects.addAll(List.of(queued, placed, finished, abstractProject));
        state.pendingBankConstructions.put(7L, new BankConstruction(10, 11, village.villageId, 11,
                List.of(new BankConstruction.Cell(10, "minecraft:air", "minecraft:stone"))));
        var counts = ConstructionWorkload.snapshot(state);
        check(number(counts, "unfinishedPhysicalSitesIncludingBanks") == 3
                && number(counts, "pendingBankSites") == 1, "Banks and ordinary projects count separately");
        check(number(counts, "placedUnfinishedProjects") == 1 && number(counts, "unplacedUnfinishedProjects") == 1
                && number(counts, "abstractProjectsExcluded") == 1 && number(counts, "completedPhysicalProjects") == 1,
                "Completed/abstract sites cannot inflate active backlog");
        check(counts.equals(ConstructionWorkload.snapshot(state)) && village.projects.size() == 4,
                "Read-only repeatable census");
        check(!counts.toString().contains(village.villageId.toString()), "Global counts do not expose unrelated identities");

        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String nativeRoot = "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/";
        String recorder = Files.readString(root.resolve(nativeRoot + "DebugFlightRecorder.java"));
        String mixin = Files.readString(root.resolve(nativeRoot + "mixin/DebugServerTickMixin.java"));
        check(mixin.contains("@WrapMethod(method = \"tickServer\")") && mixin.contains("finally")
                && mixin.contains("original.call(hasTimeLeft)") && !mixin.contains("@Overwrite"),
                "Measure complete tick including end callbacks; preserve original execution");
        check(Files.readString(root.resolve("common/src/main/resources/emerald-standard.mixins.json"))
                .contains("DebugServerTickMixin"), "Timing hook must be registered");
        check(recorder.contains("sampleClock >= session.nextSampleAtNanos")
                && recorder.contains("server_workload") && recorder.contains("session == capture"),
                "Monotonic sampling, time-correlated events and boundary identity guard");
        String language = Files.readString(root.resolve("common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        check(language.contains("milliseconds per tick") && language.contains("recently working"),
                "Handbook explains timing versus construction backlog");
        System.out.println("PASS debug performance: exact aggregates, bounded percentiles, TPS, empty/boundary samples, read-only workload and wiring");
    }

    private static double number(Map<?, ?> map, String key) { return ((Number) map.get(key)).doubleValue(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
