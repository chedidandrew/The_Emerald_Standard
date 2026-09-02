package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Regression coverage for the beta Village Prosperity System. */
public final class VillageProsperityRegressionTest {
    private VillageProsperityRegressionTest() {}

    public static void main(String[] args) throws Exception {
        testSimulationAndProjects();
        testIndependentToggleBehavior();
        testMarketInfluenceBound();
        testLifecycleAndRestoration();
        testPersistenceAndStableIdentity();
        testRecoveryWaitsForPhysicalSettlers();
        testMarketIntegrationToggle();
        testResidentEmigration();
        testPhysicalPopulationRequiresMaterializedSettlers();
        testInfectionAndCureReconciliation();
        testPreferredStableIdentity();
        testFunctionalTierCanDecline();
        System.out.println("PASS VillageProsperityRegressionTest");
    }

    private static void testSimulationAndProjects() {
        EconomyState state = EconomyState.fresh(7_654_321L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 8, 12);
        village.foodSupply = 400.0;
        village.materialSupply = 1_500.0;
        village.treasury = 500.0;
        village.developmentPoints = 200.0;
        for (int day = 0; day < 2_000; day++) state.advanceOneDay(true);
        require(village.lastSimulatedDay == state.economicDay, "Village did not catch up daily");
        require(village.prosperity >= 0.0 && village.prosperity <= 100.0, "Prosperity left bounds");
        require(village.safety >= 0.0 && village.safety <= 100.0, "Safety left bounds");
        require(!village.projects.isEmpty(), "No development project was approved");
        require(village.projects.stream().anyMatch(project -> project.economicComplete), "No development project completed economically");
        require(village.developmentTier >= 1, "Village never advanced beyond hamlet tier");
    }

    private static void testIndependentToggleBehavior() {
        EconomyState state = EconomyState.fresh(99L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 6, 8);
        double food = village.foodSupply;
        long simulated = village.lastSimulatedDay;
        state.advanceOneDay(false);
        require(village.foodSupply == food, "Simulation-off mode changed abstract resources");
        require(village.lastSimulatedDay == simulated, "Simulation-off mode advanced the abstract village clock");
        village.observedPopulation = 6;
        village.materialSupply = 1_000.0;
        village.treasury = 500.0;
        village.developmentPoints = 200.0;
        VillageProsperityEngine.advanceVisualOnlyPulse(village, state.seed, state.economicDay);
        require(village.lastSimulatedDay == state.economicDay, "Visual-only mode did not process a loaded pulse");
    }

    private static void testMarketInfluenceBound() {
        EconomyState state = EconomyState.fresh(123L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 32, 40);
        village.prosperity = 100.0;
        village.safety = 100.0;
        village.developmentTier = 5;
        village.miningOutput = 100.0;
        village.agricultureOutput = 100.0;
        village.tradeOutput = 100.0;
        village.redstoneOutput = 100.0;
        village.alchemyOutput = 100.0;
        village.transportOutput = 100.0;
        village.securityOutput = 100.0;
        VillageProsperityEngine.VillageFundamentals fundamentals = state.villageFundamentals();
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            double drift = VillageProsperityEngine.assetAnnualDrift(asset.ticker(), fundamentals);
            require(drift >= -0.012 && drift <= 0.012, "Village market drift exceeded cap for " + asset.ticker());
        }
    }

    private static void testLifecycleAndRestoration() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-lifecycle-");
        try {
            EconomyService service = new EconomyService();
            service.configureVillageProsperity(true, true, true, true);
            service.startWithSeed(root, 44L, 0L, 0L);
            UUID resident = UUID.randomUUID();
            EconomyService.VillageSnapshot snapshot = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(100,64,100), 1L, 0L, 1, 4, 0, false,
                    List.of(new EconomyService.ResidentObservation(resident, "minecraft:farmer", pack(100,64,100)))));
            require(snapshot != null, "Village observation failed");
            UUID villageId = snapshot.village().villageId;
            require(service.recordVillagerDeath(villageId, resident, "minecraft:farmer", pack(100,64,100),
                    VillageProsperityEngine.IncidentCause.PLAYER, UUID.randomUUID()), "Player-caused death was not recorded");
            EconomyState.VillageRecord abandoned = service.villageSnapshot(villageId).village();
            require(abandoned.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED, "Player extinction did not abandon the village");
            require(abandoned.population == 0, "Extinct village retained population");
            require(!service.allowBankerReplacementAt(pack(100,64,100)), "Abandoned village received a free Banker replacement");
            UUID player = UUID.randomUUID();
            require(service.deposit(player, 30), "Could not fund test account");
            EconomyService.VillageFundingResult funded = service.fundVillage(player, villageId, 25);
            require(funded.funded() && funded.restorationActivated(), "Restoration target did not activate");
            EconomyState mutable = service.snapshot();
            EconomyState.VillageRecord restored = mutable.villages.get(villageId);
            for (int day = 0; day < 5; day++) mutable.advanceOneDay(true, true, true);
            require(restored.lifecycle == VillageProsperityEngine.Lifecycle.RECOVERING, "Funded abandoned village did not enter recovery");
            require(restored.population == 0 && restored.pendingSettlers >= 2,
                    "Recovery created productive abstract residents before physical settlers");
            require(mutable.villageFundamentals().eligibleVillages() == 0,
                    "Empty recovering village influenced market fundamentals");
        } finally { deleteTree(root); }
    }

    private static void testPersistenceAndStableIdentity() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-persistence-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1234L, 0L, 0L);
            EconomyService.VillageObservation observation = new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(20,70,-40), 5L, 0L, 7, 10, 0, false, List.of());
            EconomyService.VillageSnapshot first = service.observeVillage(observation);
            EconomyService.VillageSnapshot second = service.observeVillage(observation);
            require(first.village().villageId.equals(second.village().villageId), "Repeated observation changed stable village identity");
            UUID id = first.village().villageId;
            require(service.saveNowAt(0L,0L), "Could not save village state");
            EconomyService reloaded = new EconomyService();
            reloaded.startWithSeed(root, 9999L, 0L, 0L);
            EconomyService.VillageSnapshot loaded = reloaded.villageSnapshot(id);
            require(loaded != null, "Village record did not persist");
            require(loaded.village().population == 7, "Village population did not persist");
            require(loaded.village().housingCapacity >= 10, "Village housing did not persist");
        } finally { deleteTree(root); }
    }

    private static void testRecoveryWaitsForPhysicalSettlers() {
        EconomyState state = EconomyState.fresh(88L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 0, 4);
        village.lifecycle = VillageProsperityEngine.Lifecycle.EXTINCT;
        village.recoveryEligibleDay = 1L;
        state.advanceOneDay(true, true, true);
        require(village.population == 0 && village.pendingSettlers == 2,
                "Automatic recovery bypassed physical settler materialization");
        require(village.lifecycle == VillageProsperityEngine.Lifecycle.RECOVERING,
                "Eligible extinct village did not enter recovery queue");

        EconomyState simulationOnly = EconomyState.fresh(89L, 0L, 0L);
        EconomyState.VillageRecord simulationOnlyVillage = village(simulationOnly, 0, 4);
        simulationOnlyVillage.lifecycle = VillageProsperityEngine.Lifecycle.EXTINCT;
        simulationOnlyVillage.recoveryEligibleDay = 1L;
        simulationOnly.advanceOneDay(true, false, true, true);
        require(simulationOnlyVillage.population == 2 && simulationOnlyVillage.pendingSettlers == 0,
                "Simulation-only recovery incorrectly waited for physical settlers");

        EconomyState disabled = EconomyState.fresh(90L, 0L, 0L);
        EconomyState.VillageRecord disabledVillage = village(disabled, 0, 4);
        disabledVillage.lifecycle = VillageProsperityEngine.Lifecycle.EXTINCT;
        disabledVillage.recoveryEligibleDay = 1L;
        disabled.advanceOneDay(true, true, true, false);
        require(disabledVillage.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                        && disabledVillage.pendingSettlers == 0,
                "Automatic recovery toggle was ignored");
    }

    private static void testMarketIntegrationToggle() {
        EconomyState withVillage = EconomyState.fresh(12345L, 0L, 0L);
        EconomyState.VillageRecord village = village(withVillage, 24, 30);
        village.prosperity = 100;
        village.safety = 100;
        village.miningOutput = 100;
        village.agricultureOutput = 100;
        village.tradeOutput = 100;
        village.redstoneOutput = 100;
        village.alchemyOutput = 100;
        village.transportOutput = 100;
        village.securityOutput = 100;
        EconomyState baseline = EconomyState.fresh(12345L, 0L, 0L);
        withVillage.advanceOneDay(false, false, true);
        baseline.advanceOneDay(false, false, true);
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            require(Double.doubleToLongBits(withVillage.prices.get(asset.ticker()))
                            == Double.doubleToLongBits(baseline.prices.get(asset.ticker())),
                    "Disabled market integration changed " + asset.ticker());
        }
    }

    private static void testResidentEmigration() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-emigration-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 222L, 0L, 0L);
            UUID resident = UUID.randomUUID();
            EconomyService.VillageSnapshot snapshot = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(0,64,0), 7L, 0L, 1, 4, 0, false,
                    List.of(new EconomyService.ResidentObservation(resident, "minecraft:farmer", pack(0,64,0)))));
            require(service.tickAt(31L * 24_000L, 0L), "Could not advance emigration test clock");
            EconomyService.VillageSnapshot after = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(0,64,0), 7L, 0L, 0, 4, 0, false, List.of()));
            EconomyState.ResidentRecord record = after.village().residents.get(resident);
            require(record != null && record.status == VillageProsperityEngine.ResidentStatus.EMIGRATED,
                    "Long-absent resident did not emigrate");
            require(after.village().population == 0, "Emigrated resident remained in productive population");
        } finally { deleteTree(root); }
    }


    private static void testPhysicalPopulationRequiresMaterializedSettlers() {
        EconomyState state = EconomyState.fresh(5_555L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 6, 10);
        village.foodSupply = 2_000.0;
        village.prosperity = 90.0;
        village.safety = 90.0;
        int startingPopulation = village.population;
        for (int day = 1; day <= 10_000 && village.pendingSettlers == 0; day++) {
            VillageProsperityEngine.advanceOneDay(village, state.seed, day, true, true);
        }
        require(village.pendingSettlers > 0, "Physical mode never queued a settler");
        require(village.population == startingPopulation,
                "Physical mode increased abstract population before a real settler census");
    }

    private static void testInfectionAndCureReconciliation() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-infection-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 9876L, 0L, 0L);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            long firstPos = pack(4, 64, 4);
            long secondPos = pack(7, 64, 4);
            EconomyService.VillageSnapshot created = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", pack(5, 64, 4), 17L, 0L, 2, 4, 0, false,
                            List.of(
                                    new EconomyService.ResidentObservation(first, "minecraft:farmer", firstPos),
                                    new EconomyService.ResidentObservation(second, "minecraft:none", secondPos))));
            UUID villageId = created.village().villageId;
            require(service.recordResidentStatus(
                            villageId, UUID.randomUUID(), "minecraft:none", firstPos,
                            VillageProsperityEngine.ResidentStatus.INFECTED),
                    "Could not record villager infection");
            EconomyState.VillageRecord infected = service.villageSnapshot(villageId).village();
            require(infected.population == 1, "Infection did not suspend one productive resident");
            require(infected.residents.values().stream().filter(
                            resident -> resident.status == VillageProsperityEngine.ResidentStatus.INFECTED)
                    .count() == 1, "Infection was not reconciled to one resident record");

            require(service.recordResidentStatus(
                            villageId, UUID.randomUUID(), "minecraft:none", firstPos,
                            VillageProsperityEngine.ResidentStatus.INFECTED),
                    "Repeated infection observation failed");
            require(service.villageSnapshot(villageId).village().population == 1,
                    "Repeated zombie observations decremented population twice");

            UUID cured = UUID.randomUUID();
            service.observeVillage(
                    villageId,
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", pack(5, 64, 4), 17L, 0L, 2, 4, 0, false,
                            List.of(
                                    new EconomyService.ResidentObservation(cured, "minecraft:none", firstPos),
                                    new EconomyService.ResidentObservation(second, "minecraft:none", secondPos))));
            EconomyState.VillageRecord curedVillage = service.villageSnapshot(villageId).village();
            require(curedVillage.population == 2, "Cured resident did not restore productive population");
            require(curedVillage.residents.values().stream().noneMatch(
                            resident -> resident.status == VillageProsperityEngine.ResidentStatus.INFECTED),
                    "Cure reconciliation left a stale infected resident");
        } finally { deleteTree(root); }
    }

    private static void testPreferredStableIdentity() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-preferred-id-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 777L, 0L, 0L);
            EconomyService.VillageSnapshot first = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(0,64,0), 101L, 0L, 4, 6, 0, false, List.of()));
            EconomyService.VillageSnapshot second = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(120,64,0), 202L, 0L, 4, 6, 0, false, List.of()));
            require(!first.village().villageId.equals(second.village().villageId),
                    "Separated villages unexpectedly shared identity");
            EconomyService.VillageSnapshot movedObservation = service.observeVillage(
                    second.village().villageId,
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", pack(55,64,0), 303L, 0L, 4, 6, 0, false, List.of()));
            require(movedObservation.village().villageId.equals(second.village().villageId),
                    "Persisted resident tags did not win over a closer unrelated village");
        } finally { deleteTree(root); }
    }

    private static void testFunctionalTierCanDecline() {
        EconomyState state = EconomyState.fresh(222L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 0, 24);
        village.developmentTier = 5;
        for (int i = 0; i < 6; i++) {
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = i + 1;
            project.type = VillageProsperityEngine.ProjectType.COTTAGE;
            project.economicComplete = true;
            village.projects.add(project);
        }
        VillageProsperityEngine.advanceOneDay(village, state.seed, 1L, false, true);
        require(village.developmentTier == 0,
                "Extinct village retained a permanently elevated functional tier");
    }

    private static EconomyState.VillageRecord village(EconomyState state, int population, int housing) {
        UUID id = UUID.randomUUID();
        EconomyState.VillageRecord village = state.village(id);
        village.villageId = id;
        village.dimensionKey = "minecraft:overworld";
        village.centerPos = pack(0,64,0);
        village.discoveredDay = state.economicDay;
        village.lastSimulatedDay = state.economicDay;
        village.lastCensusDay = state.economicDay;
        village.population = population;
        village.observedPopulation = population;
        village.housingCapacity = housing;
        village.foodSupply = Math.max(0, population * 24.0);
        village.materialSupply = Math.max(24, population * 12.0);
        village.treasury = Math.max(8, population * 2.0);
        village.developmentPoints = Math.max(4, population);
        village.prosperity = 55.0;
        village.safety = 70.0;
        return village;
    }

    private static long pack(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static void deleteTree(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
