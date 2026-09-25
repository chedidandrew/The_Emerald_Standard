package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;

public final class VillageFoodSupplyRegressionTest {
    public static void main(String[] args) throws Exception {
        partialChunkFreshness();
        var state = EconomyState.fresh(7123, 0, 0);
        state.economicDay = 100; state.liveMarket=LiveMarket.adopt(state);
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
    private static void partialChunkFreshness() throws Exception {
        var dir = Files.createTempDirectory("tes-partial-food-age-");
        var service = new EconomyService(); service.startWithSeed(dir, 8, 0, 0);
        var v = service.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", 70, 77, 0, 8, 10, 0, false, java.util.List.of())).village();
        UUID id = v.villageId;
        service.observeVillageFoodChunks(id, java.util.Map.of(
                1L, new VillageFoodSupply.ChunkObservation(100, 4),
                2L, new VillageFoodSupply.ChunkObservation(0, 0)));
        var field = EconomyService.class.getDeclaredField("state"); field.setAccessible(true);
        var live = (EconomyState)field.get(service); live.economicDay = 4; live.liveMarket=LiveMarket.adopt(live);
        service.observeVillageFoodChunks(id, java.util.Map.of(2L, new VillageFoodSupply.ChunkObservation(0, 0)));
        var partial = service.villageSnapshot(id).village();
        require(partial.foodChunks.get(1L).day() == 0 && partial.foodChunks.get(2L).day() == 4,
                "empty neighbor does not renew old farm");
        require(VillageFoodSupply.bonus(partial, 4) > 0, "unknown farms decay, not immediately disappear");
        require(service.saveNowAt(0, 0), "partial observations save");
        var restart = new EconomyService(); restart.startWithSeed(dir, 8, 0, 0);
        require(restart.villageSnapshot(id).village().foodChunks.get(1L).day() == 0, "per-chunk ages survive reload");
        live.economicDay = 100; live.liveMarket=LiveMarket.adopt(live);
        service.observeVillageFoodChunks(id, java.util.Map.of(2L, new VillageFoodSupply.ChunkObservation(0, 0)));
        require(VillageFoodSupply.bonus(service.villageSnapshot(id).village(), 100) == 0,
                "fresh loaded chunk cannot resurrect long-unloaded farm");
        service.observeVillageFoodChunks(id, java.util.Map.of(2L, new VillageFoodSupply.ChunkObservation(100, 2, 0)));
        require(VillageFoodSupply.bonus(service.villageSnapshot(id).village(), 100) == 0,
                "delayed scan cannot replace a newer observation");
        // Old two-value samples inherit their original aggregate day once, not the new scan day.
        var legacy = new EconomyState.VillageRecord(); legacy.lastFoodSourcesDay = 0;
        legacy.foodChunks.put(1L, new VillageFoodSupply.ChunkObservation(100, 4));
        VillageFoodSupply.merge(legacy, java.util.Map.of(2L, new VillageFoodSupply.ChunkObservation(0, 0)), 100);
        require(VillageFoodSupply.bonus(legacy, 100) == 0, "legacy chunk migration does not invent freshness");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
