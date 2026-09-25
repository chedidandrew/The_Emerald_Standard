package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class VillageExpansionRegressionTest {
    private static final UUID ROOT = new UUID(124, 578);
    public static void main(String[] args) throws Exception {
        testGatesAndFounding();
        testCityScaleAndSupplies();
        testServiceAndPersistence();
        testLightingAndPause();
        System.out.println("PASS VillageExpansionRegressionTest");
    }

    private static EconomyState state() {
        var s = EconomyState.fresh(1234, 0, 0);
        s.economicDay = 100; s.liveMarket=LiveMarket.adopt(s);
        var v = s.village(ROOT);
        v.dimensionKey = "minecraft:overworld";
        v.centerPos = pack(0, 64, 0);
        v.population = v.observedPopulation = 18;
        v.housingCapacity = v.observedHousingCapacity = 24;
        v.foodSupply = v.materialSupply = 2_000;
        v.treasury = 600;
        v.developmentTier = 3;
        v.prosperity = 85;
        v.safety = 80;
        v.expansionHealthyDays = 3;
        v.lastCensusDay = v.lastSimulatedDay = 100;
        return s;
    }
    private static void testGatesAndFounding() {
        var s = state(); var v = s.villages.get(ROOT);
        require(VillageExpansion.reason(v, 100, false) == VillageExpansion.Reason.READY, "automatic default ready");
        v.expansionHealthyDays = 2;
        require(VillageExpansion.reason(v, 100, false) == VillageExpansion.Reason.MATURING, "sustained health");
        require(VillageExpansion.reason(v, 100, true) == VillageExpansion.Reason.READY, "Peaceful easier");
        v.expansionHealthyDays = 3; v.expansionMode = VillageExpansion.Mode.APPROVAL;
        require(VillageExpansion.reason(v, 100, false) == VillageExpansion.Reason.APPROVAL, "approval gate");
        v.expansionApproved = true;
        var child = VillageExpansion.draft(v, pack(112, 64, 0), s.seed, 100, true);
        require(child.population == 0 && child.pendingSettlers == 4 && child.projects.size() == 1, "physical founding queue");
        require(child.projects.getFirst().type == VillageProsperityEngine.ProjectType.COTTAGE, "starter home");
        v.safety = 45;
        require(VillageExpansion.draft(v, pack(112, 64, 0), s.seed, 100, true)
                .projects.getFirst().type == VillageProsperityEngine.ProjectType.COTTAGE, "low-safety founders still build housing first");
        v.safety = 80;
        double progress = child.projects.getFirst().economicProgress;
        VillageProsperityEngine.advanceOneDay(child, s.seed, 101, true, true, false);
        require(child.population == 0 && child.projects.getFirst().economicProgress > progress, "builders can found before residents spawn");
        v.lastExpansionDay = 97;
        require(VillageExpansion.reason(v, 100, false) == VillageExpansion.Reason.STABILIZING, "normal cooldown");
        require(VillageExpansion.reason(v, 100, true) == VillageExpansion.Reason.READY, "Peaceful cooldown");
        v.lastExpansionDay = 0; v.foodSupply = 200;
        require(VillageExpansion.reason(v, 100, false) == VillageExpansion.Reason.FOOD, "existing food reserve");
    }
    private static void testCityScaleAndSupplies() throws Exception {
        var s = state(); var root = s.villages.get(ROOT);
        for (int i = 0; i < 130; i++) {
            var child = VillageExpansion.draft(root, pack((i + 1) * 112, 64, 0), s.seed, 100, false);
            s.villages.put(child.villageId, child);
            root.expansionSerial++;
        }
        VillageExpansion.prepareDay(s);
        require(root.cityDistrictCount == 131, "city exceeds old single village caps without a city hard cap");
        for (var v : s.villages.values()) require(v.population <= 64 && v.projects.size() <= 12, "per-district safety bounds");
        double large = VillageExpansion.overhead(root, false);
        root.cityDistrictCount = 20;
        require(large > VillageExpansion.overhead(root, false) * 6.5, "superlinear per-district upkeep");
        VillageExpansion.prepareDay(s);
        var child = s.villages.values().stream().filter(v -> v.cityId != null).findFirst().orElseThrow();
        child.foodSupply = 0; child.materialSupply = 0; child.treasury = 0;
        double totalFood = s.villages.values().stream().mapToDouble(v -> v.foodSupply).sum();
        VillageExpansion.shareSupplies(s, false);
        require(child.foodSupply > 0, "city supplies reach a new district");
        require(Math.abs(totalFood - s.villages.values().stream().mapToDouble(v -> v.foodSupply).sum()) < .0001, "sharing conserves resources");
        for (int day = 0; day < 3; day++) { child.treasury = 0; VillageExpansion.finishDay(child, false); }
        VillageExpansion.prepareDay(s);
        require(VillageExpansion.reason(root, 100, false) == VillageExpansion.Reason.UPKEEP, "shortfalls taper expansion");
        int projects = child.projects.size();
        require(projects > 0, "shortfalls do not demolish");
        s.validate();
    }
    private static EconomyState.VillageRecord planned(EconomyService service) {
        var v = service.draftVillageDistrict(ROOT, pack(112, 64, 0));
        require(v != null, "draft ready");
        v.architectureDialect = "plains";
        var p = v.projects.getFirst();
        p.originPos = pack(108, 64, 0);
        p.boundsMinPos = pack(106, 60, -2);
        p.boundsMaxPos = pack(124, 78, 20);
        p.trailAnchorSet = true; p.trailAnchorPos = v.centerPos; p.trailTotalBlocks = 12;
        p.designPlanHash = "a".repeat(64);
        p.sitePreparationComplete = false;
        return v;
    }
    private static void testServiceAndPersistence() throws Exception {
        Path dir = Files.createTempDirectory("tes-city-regression-");
        Path save = dir.resolve("the_emerald_standard.properties");
        var initial = state(); initial.save(save);
        var service = new EconomyService(); service.startWithSeed(dir, 0, 0, 1234);
        var draft = planned(service);
        double treasury = service.villageSnapshot(ROOT).village().treasury;
        require(service.commitVillageDistrict(ROOT, 0, draft), "durable charter: " + service.lastError());
        require(!service.commitVillageDistrict(ROOT, 0, draft), "duplicate charter rejected");
        require(service.villageSnapshot(ROOT).village().treasury == treasury - 80, "charter charged once");
        require(service.setVillageExpansionMode(draft.villageId, VillageExpansion.Mode.PAUSED), "child controls city");
        require(service.villageSnapshot(draft.villageId).village().expansionMode == VillageExpansion.Mode.PAUSED, "pause inherited immediately");
        var reloaded = new EconomyService(); reloaded.startWithSeed(dir, 0, 0, 1234);
        require(reloaded.expansionStatus(ROOT).districts() == 2 && reloaded.expansionStatus(ROOT).mode() == VillageExpansion.Mode.PAUSED, "restart retains city and mode");
        require(!reloaded.villageSnapshot(draft.villageId).village().projects.getFirst().sitePreparationComplete, "preparation survives restart");

        // A failed save must not spend money, consume an approval, or register a phantom district.
        Path failureDir = Files.createTempDirectory("tes-city-save-failure-");
        initial.save(failureDir.resolve("the_emerald_standard.properties"));
        var failure = new EconomyService(); failure.startWithSeed(failureDir, 0, 0, 1234);
        var failedDraft = planned(failure);
        Path oldDir = failureDir.resolveSibling(failureDir.getFileName() + "-original");
        Files.move(failureDir, oldDir);
        Files.writeString(failureDir, "test-only blocking file");
        require(!failure.commitVillageDistrict(ROOT, 0, failedDraft), "IO failure reported");
        require(failure.expansionStatus(ROOT).districts() == 1 && failure.expansionStatus(ROOT).serial() == 0
                && failure.villageSnapshot(ROOT).village().treasury == treasury, "IO rollback is atomic");
        Files.delete(failureDir); Files.move(oldDir, failureDir);

        // Missing fields in old format default to Automatic without retroactive clearing.
        Path legacy = Files.createTempDirectory("tes-city-legacy-").resolve("the_emerald_standard.properties");
        initial.save(legacy);
        var properties = new java.util.Properties();
        try (var input = Files.newInputStream(legacy)) { properties.load(input); }
        properties.setProperty("format", "18");
        properties.keySet().removeIf(key -> key.toString().contains("expansion_") || key.toString().contains("district_founding")
                || key.toString().contains("lighting_") || key.toString().contains("city_id"));
        var checksum = EconomyPersistence.class.getDeclaredMethod("checksum", java.util.Properties.class);
        checksum.setAccessible(true);
        properties.setProperty("checksum", (String) checksum.invoke(null, properties));
        try (var output = Files.newOutputStream(legacy)) { properties.store(output, "Legacy migration fixture"); }
        var migrated = EconomyState.load(legacy, 1234, 0, 0);
        require(migrated.villages.get(ROOT).expansionMode == VillageExpansion.Mode.AUTOMATIC, "legacy default automatic");
    }
    private static void testLightingAndPause() {
        var s = state(); var dark = s.villages.get(ROOT); var lit = dark.copy();
        lit.lightingCoveragePercent = 100; lit.lastLightingDay = 100;
        VillageProsperityEngine.advanceOneDay(dark, s.seed, 101, true, true, false);
        VillageProsperityEngine.advanceOneDay(lit, s.seed, 101, true, true, false);
        require(lit.safety > dark.safety && lit.safety - dark.safety <= .20, "bounded light safety benefit");
        lit = dark.copy(); lit.lightingCoveragePercent = 100; lit.lastLightingDay = 90;
        var staleControl = dark.copy();
        VillageProsperityEngine.advanceOneDay(staleControl, s.seed, 102, true, true, false);
        VillageProsperityEngine.advanceOneDay(lit, s.seed, 102, true, true, false);
        require(lit.safety == staleControl.safety, "stale light coverage cannot farm safety offline");
        var child = VillageExpansion.draft(dark, pack(112, 64, 0), s.seed, 102, true);
        child.expansionMode = VillageExpansion.Mode.PAUSED;
        double progress = child.projects.getFirst().economicProgress;
        VillageProsperityEngine.advanceOneDay(child, s.seed, 103, true, true, false);
        VillageProsperityEngine.advanceVisualOnlyPulse(child, s.seed, 104);
        require(child.projects.getFirst().economicProgress == progress, "pause stops both progression paths");
    }
    private static long pack(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
