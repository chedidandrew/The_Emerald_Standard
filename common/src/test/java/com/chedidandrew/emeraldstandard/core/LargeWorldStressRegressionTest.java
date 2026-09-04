package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic scale guard for the loader-neutral village simulation.
 *
 * <p>This is not a substitute for a real dedicated-server profile. It catches accidental
 * super-linear work, unbounded project growth, non-finite values, and large-world regressions
 * before the loader builds begin.</p>
 */
public final class LargeWorldStressRegressionTest {
    private static final int DAYS = 365;
    private static final long MAX_TOTAL_MILLIS = 30_000L;

    private LargeWorldStressRegressionTest() {
    }

    public static void main(String[] args) {
        long suiteStart = System.nanoTime();
        runScenario(100);
        runScenario(500);
        runScenario(1_000);
        long totalMillis = elapsedMillis(suiteStart);
        if (totalMillis > MAX_TOTAL_MILLIS) {
            throw new AssertionError(
                    "Large-world simulation exceeded the generous CI ceiling: "
                            + totalMillis + " ms > " + MAX_TOTAL_MILLIS + " ms");
        }
        System.out.println("PASS large-world scale guard in " + totalMillis + " ms");
    }

    private static void runScenario(int villageCount) {
        long seed = 0x454D4552414C44L + villageCount;
        List<EconomyState.VillageRecord> villages = new ArrayList<>(villageCount);
        for (int index = 0; index < villageCount; index++) {
            EconomyState.VillageRecord village = new EconomyState.VillageRecord();
            village.villageId = new UUID(0x45534C0000000000L + index, 0x95A0000000000000L + index);
            village.dimensionKey = "minecraft:overworld";
            village.centerPos = index;
            village.population = 4 + index % 25;
            village.observedPopulation = village.population;
            village.housingCapacity = village.population + 8;
            village.foodSupply = 500.0 + index % 200;
            village.materialSupply = 500.0 + index % 300;
            village.treasury = 250.0 + index % 150;
            village.prosperity = 45.0 + index % 40;
            village.safety = 50.0 + index % 35;
            village.developmentPoints = 50.0;
            village.lifecycle = VillageProsperityEngine.Lifecycle.ACTIVE;
            villages.add(village);
        }

        long start = System.nanoTime();
        for (long day = 1; day <= DAYS; day++) {
            for (EconomyState.VillageRecord village : villages) {
                VillageProsperityEngine.advanceOneDay(village, seed, day, true, false);
            }
        }
        long millis = elapsedMillis(start);

        for (EconomyState.VillageRecord village : villages) {
            requireFinite(village.prosperity, "prosperity", village.villageId);
            requireFinite(village.safety, "safety", village.villageId);
            requireFinite(village.foodSupply, "food", village.villageId);
            requireFinite(village.materialSupply, "materials", village.villageId);
            requireFinite(village.treasury, "treasury", village.villageId);
            if (village.population < 0
                    || village.population > VillageProsperityEngine.MAX_ABSTRACT_POPULATION) {
                throw new AssertionError("Population escaped bounds for " + village.villageId);
            }
            if (village.projects.size() > VillageProsperityEngine.MAX_PROJECTS_PER_VILLAGE) {
                throw new AssertionError("Project backlog escaped bounds for " + village.villageId);
            }
        }

        long updates = (long) villageCount * DAYS;
        double updatesPerMillisecond = updates / (double) Math.max(1L, millis);
        System.out.printf(
                "SCALE villages=%d days=%d updates=%d elapsed_ms=%d updates_per_ms=%.2f%n",
                villageCount,
                DAYS,
                updates,
                millis,
                updatesPerMillisecond);
    }

    private static void requireFinite(double value, String field, UUID villageId) {
        if (!Double.isFinite(value)) {
            throw new AssertionError(field + " became non-finite for " + villageId);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
