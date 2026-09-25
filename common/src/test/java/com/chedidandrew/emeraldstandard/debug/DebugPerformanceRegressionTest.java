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
        var measurements = new WorkMeasurements();
        measurements.record("plot", 2_000_000L); measurements.record("plot", 4_000_000L);
        measurements.count("cache.hit"); measurements.count("cache.hit");
        var measured = (Map<?,?>)((Map<?,?>)measurements.snapshot().get("timings")).get("plot");
        check(number(measured,"calls") == 2 && number(measured,"meanMs") == 3
                && number(measured,"maxMs") == 4, "Subsystem aggregates");
        for (int i=0;i<1000;i++) { measurements.record("scope"+i,1); measurements.count("counter"+i); }
        check(((Map<?,?>)measurements.snapshot().get("timings")).size() == WorkMeasurements.LIMIT
                && ((Map<?,?>)measurements.snapshot().get("counters")).size() == WorkMeasurements.LIMIT, "Bounded diagnostic keys");
        measurements.job("bank:1",10,"selected",false);
        measurements.job("bank:1",10,"placed_blocks",true);
        measurements.job("bank:1",20,"shared_budget_exhausted",false);
        var job=(Map<?,?>)((Map<?,?>)measurements.snapshot().get("jobs")).get("bank:1");
        check(number(job,"visits")==2&&number(job,"successfulBatches")==1
                &&number(job,"lastVisitedGameTick")==20&&number(job,"lastProgressGameTick")==10,
                "Budget-only selections never masquerade as physical progress");
        for(int i=0;i<1000;i++) measurements.job("job"+i,i,"selected",false);
        var jobs=(Map<?,?>)measurements.snapshot().get("jobs");
        check(jobs.size()==WorkMeasurements.JOB_LIMIT&&!jobs.containsKey("bank:1")&&jobs.containsKey("job999"),"Bounded recent job diagnostics");
        var gaps = new ServerTickMetrics(0);
        gaps.recordBoundary(1_000_000L, 11_000_000L, true);
        gaps.recordBoundary(61_000_000L, 71_000_000L, true);
        gaps.recordBoundary(12_071_000_000L, 12_081_000_000L, true);
        var gapReport = gaps.snapshot(12_100_000_000L);
        check(number(gapReport,"interTickGapsOverOneSecond") == 1
                && number(gapReport,"maxMspt") == 10 && number(gapReport,"ticksOver50Ms") == 0,
                "Pauses/gaps are not slow ticks");
        check(number(gapReport,"gapAdjustedTicksPerSecond") > number(gapReport,"observedTicksPerSecond"),
                "Raw throughput retained beside explicitly heuristic gap-adjusted throughput");
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
