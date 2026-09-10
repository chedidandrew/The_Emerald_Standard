package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;

public final class VillageFoodSupplyRegressionTest {
    public static void main(String[] args) throws Exception {
        var state = EconomyState.fresh(7123, 0, 0);
        state.economicDay = 100;
        UUID id = new UUID(83, 92);
        var village = state.village(id);
        village.population = village.observedPopulation = 12;
        village.housingCapacity = village.observedHousingCapacity = 16;
        village.foodSupply = 100; village.safety = 80; village.prosperity = 70;
        village.lastCensusDay = village.lastSimulatedDay = 100;
        require(VillageFoodSupply.bonus(village, 101) == 0, "old villages retain baseline without observations");
        var bare = village.copy();
        VillageFoodSupply.observe(village, 64, 12, 100);
        require(Math.abs(VillageFoodSupply.bonus(village, 101) - .65) < 1e-9, "64 mature crops plus 12 adults = 65 percent");
        var copy = village.copy();
        require(VillageFoodSupply.bonus(copy, 101) == VillageFoodSupply.bonus(village, 101), "snapshot copy retains sources");
        VillageProsperityEngine.advanceOneDay(bare, state.seed, 101, true, true, false);
        VillageProsperityEngine.advanceOneDay(copy, state.seed, 101, true, true, false);
        require(Math.abs(copy.agricultureOutput / bare.agricultureOutput - 1.65) < 1e-9
                && copy.foodSupply > bare.foodSupply, "bonus affects real daily agriculture and reserves");
        var peaceful = village.copy(); var peacefulBare = village.copy();
        VillageFoodSupply.observe(peacefulBare, 0, 0, 100);
        VillageProsperityEngine.advanceOneDay(peacefulBare, state.seed, 101, true, true, true);
        VillageProsperityEngine.advanceOneDay(peaceful, state.seed, 101, true, true, true);
        require(Math.abs(peaceful.agricultureOutput / peacefulBare.agricultureOutput - 1.65) < 1e-9, "stacks predictably with Peaceful");
        double previous = 0;
        for (int count : new int[] {1, 8, 32, 64, 128, 1024, 1_000_000}) {
            VillageFoodSupply.observe(copy, count, count, 100);
            double bonus = VillageFoodSupply.bonus(copy, 101);
            require(bonus > previous && bonus < 1.5, "more sources help with a finite ceiling"); previous = bonus;
        }
        require(VillageFoodSupply.bonus(village, 104) < .65 && VillageFoodSupply.bonus(village, 108) == 0,
                "unobserved sources expire rather than pay forever");
        VillageFoodSupply.observe(copy, 16, 3, 100);
        require(VillageFoodSupply.bonus(copy, 101) < .65, "harvesting and fewer animals reduce bonus");
        VillageFoodSupply.observe(copy, 0, 0, 100);
        require(VillageFoodSupply.bonus(copy, 101) == 0, "empty fields and pens remove bonus");
        var dir = Files.createTempDirectory("tes-food-sources-");
        var file = dir.resolve("the_emerald_standard.properties");
        state.villageMarketShadows.put(id, VillageProsperityEngine.captureMarketShadow(village, 100, 14));
        state.save(file);
        var loaded = EconomyState.load(file, state.seed, 0, 0).villages.get(id);
        require(loaded.observedCropUnits == 64 && loaded.observedLivestockUnits == 12
                && loaded.lastFoodSourcesDay == 100, "food census round-trips in save");
        var service = new EconomyService(); service.startWithSeed(dir, 0, 0, state.seed);
        require(!service.observeVillageFoodSources(id, Double.NaN, 3)
                && !service.observeVillageFoodSources(id, -1, 0), "invalid observation rejected");
        require(service.observeVillageFoodSources(id, 0, 0), "fresh empty observation accepted");
        require(VillageFoodSupply.bonus(service.villageSnapshot(id).village(), 101) == 0, "service replaces counts instead of accumulating");
        var shadow = service.snapshot().villageMarketShadows.get(id).counterfactualVillage;
        require(shadow.observedCropUnits == 0 && shadow.observedLivestockUnits == 0
                && shadow.lastFoodSourcesDay == 100, "market counterfactual shares the same physical food observation");
        Properties legacy = new Properties();
        try (var in = Files.newInputStream(file)) { legacy.load(in); }
        legacy.setProperty("format", "19");
        legacy.keySet().removeIf(key -> key.toString().contains("food_sources."));
        var checksum = EconomyPersistence.class.getDeclaredMethod("checksum", Properties.class);
        checksum.setAccessible(true); legacy.setProperty("checksum", (String) checksum.invoke(null, legacy));
        try (var out = Files.newOutputStream(file)) { legacy.store(out, "Format 19 compatibility fixture"); }
        require(VillageFoodSupply.bonus(EconomyState.load(file, state.seed, 0, 0).villages.get(id), 101) == 0,
                "format 19 migrates with no invented food bonus");
        System.out.println("PASS VillageFoodSupplyRegressionTest");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
