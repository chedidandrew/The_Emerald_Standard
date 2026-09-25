package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig;
import java.nio.file.Files;
import java.util.*;

public final class VillageGuardSecurityRegressionTest {
    public static void main(String[] args) throws Exception {
        casualtyPersistence(); legacyShadowRecovery(); uuidOwnership();
        var v = new EconomyState.VillageRecord(); v.villageId = UUID.randomUUID(); v.safety = 45;
        VillageGuardSecurity.observe(v, 3, 2, 12, 0);
        check(v.guardSafetyBonus == 6 && VillageGuardSecurity.effectiveSafety(v) == 51, "three guards");
        for (int i = 0; i < 100; i++) VillageGuardSecurity.observe(v, 64, 2, 12, 0);
        check(v.safety == 45 && v.guardSafetyBonus == 12, "no repeat-scan accumulation");
        v.safety = 95; check(VillageGuardSecurity.effectiveSafety(v) == 100, "score cap");
        VillageGuardSecurity.refresh(v, 7); check(v.guardSafetyBonus == 0, "stale observation expires");
        VillageGuardSecurity.observe(v, -3, 2, 12, 9); check(v.guardSafetyBonus == 0, "negative count");
        VillageGuardSecurity.observe(v, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 9);
        check(v.guardSafetyBonus == 30 && v.observedGuards == 64, "bounded inputs/overflow");
        check(v.copy().guardSafetyBonus == 30, "rollback/snapshot preserves runtime observation");
        var config = EmeraldConfig.defaults();
        check(config.guardVillagersEnabled() && config.guardSafetyPerGuard() == 2
                && config.guardMaximumSafetyBonus() == 12, "optional defaults");
        var dir = Files.createTempDirectory("tes-guard-security-");
        var service = new EconomyService(); service.startWithSeed(dir, 12, 0, 0);
        var village = service.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", 70, 1, 0, 5, 6, 0, false, List.of())).village();
        UUID id = village.villageId;
        service.observeVillageGuards(id, 6, 2, 12);
        check(service.villageSnapshot(id).village().guardSafetyBonus == 12, "service observation");
        check(service.saveNowAt(0, 0), "save");
        var restart = new EconomyService(); restart.startWithSeed(dir, 12, 0, 0);
        check(restart.villageSnapshot(id).village().guardSafetyBonus == 0, "reload/mod removal leaves no stale bonus");
        Properties off = new Properties(); off.setProperty(EmeraldConfig.GUARDS_ENABLED_KEY, "false");
        EmeraldConfig.parse(off).applyTo(service);
        check(service.villageSnapshot(id).village().guardSafetyBonus == 0, "disable immediately clears bonus");
        // Same base Safety, but only the defended village meets the recovery safety threshold.
        v.safety = 40; v.population = 5; v.prosperity = 70; v.lifecycle = VillageProsperityEngine.Lifecycle.RECOVERING;
        v.foodSupply = 400; v.housingCapacity = 8; v.recoveryEligibleDay = 0;
        VillageGuardSecurity.observe(v, 6, 2, 12, 0);
        var without = v.copy(); VillageGuardSecurity.observe(without, 0, 2, 12, 0);
        VillageProsperityEngine.advanceOneDay(v, 1, 0);
        VillageProsperityEngine.advanceOneDay(without, 1, 0);
        check(v.prosperity > without.prosperity, "effective Safety participates in production/prosperity");
        System.out.println("PASS optional guard security: caps, decay, no scan stacking, settings, reload, actual simulation");
    }
    private static void casualtyPersistence() throws Exception {
        var dir = Files.createTempDirectory("tes-guard-casualty-");
        var service = new EconomyService(); service.startWithSeed(dir, 12, 0, 0);
        var v = service.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", 70, 77, 0, 8, 10, 0, false, List.of())).village();
        service.observeVillageGuards(v.villageId, 3, 2, 12);
        check(service.recordVillagerDeath(v.villageId, UUID.randomUUID(), "minecraft:farmer", 70,
                VillageProsperityEngine.IncidentCause.PLAYER, UUID.randomUUID()), "guarded casualty saved");
        check(service.saveNowAt(0, 0), "both checkpoints contain guarded casualty");
        var restarted = new EconomyService(); restarted.startWithSeed(dir, 12, 0, 0);
        check(restarted.snapshot().villageMarketShadows.containsKey(v.villageId), "shadow survives reload");
        restarted.snapshot().validate();
        service.observeVillageGuards(v.villageId, 1, 2, 12);
        check(service.snapshot().villageMarketShadows.get(v.villageId).counterfactualVillage.guardSafetyBonus == 2,
                "counterfactual tracks live guard changes");
        service.clearVillageGuardObservations(); service.snapshot().validate();
        check(service.snapshot().villageMarketShadows.get(v.villageId).counterfactualVillage.guardSafetyBonus == 0,
                "disabling clears shadow too");
    }
    private static void legacyShadowRecovery() throws Exception {
        var state = EconomyState.fresh(73, 0, 0); state.economicDay = 5; state.liveMarket=LiveMarket.adopt(state);
        UUID id = new UUID(48, 81), player = new UUID(18, 91);
        var v = state.village(id); v.population = v.observedPopulation = 8;
        v.housingCapacity = v.observedHousingCapacity = 10; v.safety = 65;
        state.account(player).cashMicro = 123456789;
        // Include a partially decayed bonus, not just whole-number initial observations.
        VillageGuardSecurity.observe(v, 3, 2, 12, 3); VillageGuardSecurity.refresh(v, 5);
        var shadow = VillageProsperityEngine.captureMarketShadow(v, 5, 14);
        state.villageMarketShadows.put(id, shadow);
        var file = Files.createTempDirectory("tes-guard-legacy-").resolve("economy.properties");
        state.save(file);
        Properties legacy = new Properties();
        try (var in = Files.newInputStream(file)) { legacy.load(in); }
        legacy.setProperty("format", "28");
        // Re-create the exact old serialization mistake (guard scores, zero serialized guards).
        for (String field : List.of("weight", "broad", "mining", "agriculture", "trade", "redstone", "alchemy", "transport", "security"))
            legacy.setProperty("market.shadow." + id + "." + field,
                    Double.toString(EconomyState.VillageMarketShadow.class.getField(field).getDouble(shadow)));
        writeFixture(file, legacy);
        var loaded = EconomyState.load(file, 73, 0, 0);
        check(loaded.account(player).cashMicro == 123456789 && loaded.villageMarketShadows.size() == 1,
                "format 28 repaired without losing money or shadow");
        loaded.validate();
        legacy.setProperty("market.shadow." + id + ".mining", "9.87654321");
        writeFixture(file, legacy);
        try { EconomyState.load(file, 73, 0, 0); throw new AssertionError("unrelated corruption accepted"); }
        catch (java.io.IOException expected) { check(expected.getMessage().contains("shadow"), "strict shadow rejection"); }
    }
    private static void writeFixture(java.nio.file.Path file, Properties properties) throws Exception {
        var checksum = EconomyPersistence.class.getDeclaredMethod("checksum", Properties.class);
        checksum.setAccessible(true); properties.setProperty("checksum", (String)checksum.invoke(null, properties));
        try (var out = Files.newOutputStream(file)) { properties.store(out, "isolated legacy guard fixture"); }
    }
    private static void uuidOwnership() {
        var census = new VillageGuardCensus();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), guard = UUID.randomUUID(), neighbor = UUID.randomUUID();
        Map<UUID, VillageGuardCensus.Observation> observed = new HashMap<>();
        census.observe(a, List.of(guard, neighbor, guard), 2, 12, 0, observed::put);
        check(observed.get(a).guards().size() == 2, "duplicate UUID not double counted");
        census.observe(b, List.of(guard), 2, 12, 3, observed::put);
        check(observed.get(a).guards().equals(Set.of(neighbor)) && observed.get(a).day() == 0,
                "moving guard removes old credit without renewing remaining stale guard");
        census.observe(b, List.of(guard), 2, 12, 8, observed::put);
        check(observed.get(a).guards().isEmpty(), "stale ownership index expires");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
