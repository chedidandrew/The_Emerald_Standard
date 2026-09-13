package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.UUID;

public final class VillageGrowthPacingRegressionTest {
    public static void main(String[] args) throws Exception {
        for (boolean peaceful : new boolean[]{false, true}) for (int population : new int[]{4, 8, 18}) {
            var village = starter(population);
            VillageProsperityEngine.advanceOneDay(village, 79, 1, true, true, peaceful);
            require(village.lifecycle == VillageProsperityEngine.Lifecycle.ACTIVE,
                    "Day zero is not a real incident");
            require(completed(village) == 2, "First day should prepare two useful structures: " + population);
            require(village.projects.size() == 2, "Physical backlog limited to two sites");
            require(village.materialSupply >= 0 && village.treasury >= 0 && village.developmentPoints >= 0,
                    "Full project costs debited without negative supplies");
            require(village.prosperityFund.lifetimeReceivedMicro == 0, "Starter drive is not a player donation");
            double materials = village.materialSupply;
            VillageProsperityEngine.advanceOneDay(village, 79, 2, true, true, peaceful);
            require(village.projects.size() == 2 && village.materialSupply < materials + 40,
                    "Unloaded/blocked backlog must not accumulate a new construction subsidy");
            for (var project : village.projects) project.materializedComplete = true;
            VillageProsperityEngine.advanceOneDay(village, 79, 3, true, true, peaceful);
            require(village.projects.size() >= 3, "Finishing sites releases the next local project");
        }
        for (int gate = 0; gate < 6; gate++) {
            var village = starter(8);
            switch (gate) {
                case 0 -> village.foodSupply = 0;
                case 1 -> village.safety = 20;
                case 2 -> village.expansionMode = VillageExpansion.Mode.PAUSED;
                case 3 -> village.expansionUpkeepShortfalls = 1;
                case 4 -> village.cityUpkeepDeficit = true;
                case 5 -> village.districtFounding = true;
            }
            require(VillageStarterGrowth.momentum(village) == 0, "Starter gate " + gate);
        }
        var mature = starter(8);
        mature.projectSerial = 6;
        require(VillageStarterGrowth.momentum(mature) == 0.5, "Taper after initial projects");
        mature.cityDistrictCount = 4;
        require(VillageStarterGrowth.momentum(mature) == 0.25, "Large-city taper");
        mature.projectSerial = 8;
        require(VillageStarterGrowth.momentum(mature) == 0, "Lifetime serial ends boost even after removing projects");
        mature.cityDistrictCount = 1;
        var lastStarter = new EconomyState.VillageProject();
        lastStarter.projectId = 8; lastStarter.type = VillageProsperityEngine.ProjectType.COTTAGE;
        mature.projects.add(lastStarter);
        require(VillageStarterGrowth.momentum(mature) == 0.25, "Final starter job retains its tapered labor");
        VillageProsperityEngine.advanceOneDay(mature, 79, 1, true, true, false);
        VillageProsperityEngine.advanceOneDay(mature, 79, 2, true, true, false);
        require(lastStarter.economicComplete, "Eighth project must finish, not lose its boost mid-build");
        var attacked = starter(8);
        attacked.lastIncidentCause = VillageProsperityEngine.IncidentCause.RAID;
        VillageProsperityEngine.advanceOneDay(attacked, 79, 1, true, true, false);
        require(attacked.lifecycle == VillageProsperityEngine.Lifecycle.THREATENED,
                "A genuine day-zero attack still has its safety cooldown");
        testSleepingClock();
        testCatchUp();
        System.out.println("PASS VillageGrowthPacingRegressionTest (starter cadence, sleep, caps, conservation)");
    }

    private static void testSleepingClock() throws Exception {
        var service = new EconomyService();
        service.startWithSeed(Files.createTempDirectory("tes-sleep-clock-"), 79, 0, 0, 0);
        require(service.tickAt(13_000, 13_000, 650_000), "Daytime before bed");
        require(service.tickAt(13_001, 24_000, 650_050), "Night skip");
        require(service.snapshot().economicDay == 1 && service.snapshot().pendingEconomicMillis == 0,
                "Bed completes one day, not separate game/wall/night advances");
        require(service.tickAt(13_001, 24_000, 650_050) && service.snapshot().economicDay == 1,
                "Repeated callback cannot count the night twice");
    }

    private static void testCatchUp() {
        var clock = new ConstructionCatchUp();
        clock.observe(13_000, 13_000, 2, true, true);
        require(clock.claim("bank", 13_000) == 0, "New site cannot inherit elapsed time");
        clock.observe(13_001, 24_000, 2, true, false);
        require(clock.credit("bank") == 1099, "Only skipped ticks, excluding the ordinary tick, earn credit");
        clock.observe(13_001, 24_000, 2, true, false);
        require(clock.credit("bank") == 1099, "Two managers cannot duplicate sleep credit");
        clock.observe(13_010, 24_009, 2, true, true);
        require(clock.claim("bank", 13_010) == 8 && clock.claim("bank", 13_010) == 0,
                "Per-site cap and duplicate-claim protection");
        require(clock.claim("new-home", 13_010) == 0, "Newly approved sites do not inherit old nights");
        clock.observe(13_020, 1_000_000, 2, true, true);
        require(clock.credit("bank") <= 1200, "Huge time command capped to one night's backlog");
        clock.observe(13_021, 10, 2, true, false);
        require(clock.trackedSites() == 0, "Backward daylight resets pending credits");
        clock.claim("bank", 13_021);
        clock.observe(13_022, 11, 4, true, true);
        require(clock.credit("bank") == 0, "Speed changes cannot multiply saved credit");
        clock.observe(13_023, 24_000, 4, false, false);
        require(clock.trackedSites() == 0, "Disabled/debug modes discard bonuses");
        clock.reset();
        clock.observe(100, 100, 2, true, true);
        for (int i = 0; i < 100; i++) clock.claim("site" + i, 100);
        clock.observe(101, 12_100, 2, true, false);
        var visited = new java.util.HashSet<String>();
        for (int pulse = 0; pulse < 100; pulse++) {
            long tick = 110 + 10L * pulse;
            clock.observe(tick, 12_109 + 10L * pulse, 2, true, true);
            int total = 0;
            for (int i = 0; i < 100; i++) {
                int amount = clock.claim("site" + i, tick);
                require(amount <= 8, "Per-site burst cap");
                total += amount;
                if (amount > 0) visited.add("site" + i);
            }
            require(total <= 32, "World-wide extra-work cap");
        }
        require(visited.size() == 100, "Round-robin bonus does not starve later villages/banks");
        clock.observe(5000, 20_000, 2, true, true);
        require(clock.trackedSites() == 0, "Old/unloaded sites expire");
        for (int i = 0; i < 10_000; i++) clock.claim("bounded" + i, 5000);
        require(clock.trackedSites() == ConstructionCatchUp.MAX_SITES, "Bounded memory for arbitrarily large worlds");
        clock.reset();
        clock.observe(50_000, 100_000, 2, true, true);
        require(clock.claim("bank", 50_000) == 0, "Restart never replays an old night");
        clock.reset();
        clock.observe(100, 100, 2, true, true);
        for (int i = 0; i < 10; i++) clock.claim("double" + i, 100);
        clock.observe(110, 12_100, 2, true, true);
        int sameTick = 0;
        for (int i = 0; i < 4; i++) sameTick += clock.claim("double" + i, 110);
        clock.observe(110, 24_100, 2, true, true); // Another mod changes daylight between managers.
        for (int i = 4; i < 10; i++) sameTick += clock.claim("double" + i, 110);
        require(sameTick <= 32, "A second time change in one tick cannot replenish the global bonus budget");
    }

    private static EconomyState.VillageRecord starter(int population) {
        var v = new EconomyState.VillageRecord();
        v.villageId = new UUID(0, population);
        v.population = population; v.observedPopulation = population;
        v.housingCapacity = population + 2; v.observedHousingCapacity = population + 2;
        v.foodSupply = population * 24; v.materialSupply = population * 12;
        v.treasury = Math.max(8, population * 2); v.developmentPoints = Math.max(4, population * 0.8);
        v.safety = 65;
        return v;
    }
    private static long completed(EconomyState.VillageRecord v) {
        return v.projects.stream().filter(p -> p.economicComplete).count();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
