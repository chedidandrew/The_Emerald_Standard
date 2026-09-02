package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Regression coverage for the beta Village Prosperity System. */
public final class VillageProsperityRegressionTest {
    private VillageProsperityRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testSimulationAndProjects();
        testIndependentToggleBehavior();
        testMarketInfluenceBound();
        testLifecycleAndRestoration();
        testPersistenceAndStableIdentity();
        System.out.println("PASS VillageProsperityRegressionTest");
    }

    private static void testSimulationAndProjects() {
        EconomyState state = EconomyState.fresh(7_654_321L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 8, 12);
        village.foodSupply = 400.0;
        village.materialSupply = 1_500.0;
        village.treasury = 500.0;
        village.developmentPoints = 200.0;
        for (int day = 0; day < 2_000; day++) {
            state.advanceOneDay(true);
        }
        require(village.lastSimulatedDay == state.economicDay, "Village did not catch up daily");
        require(village.prosperity >= 0.0 && village.prosperity <= 100.0,
                "Prosperity left bounds");
        require(village.safety >= 0.0 && village.safety <= 100.0,
                "Safety left bounds");
        require(!village.projects.isEmpty(), "No development project was approved");
        require(village.projects.stream().anyMatch(project -> project.economicComplete),
                "No development project completed economically");
        require(village.developmentTier >= 1, "Village never advanced beyond hamlet tier");
    }

    private static void testIndependentToggleBehavior() {
        EconomyState state = EconomyState.fresh(99L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 6, 8);
        double food = village.foodSupply;
        long simulated = village.lastSimulatedDay;
        state.advanceOneDay(false);
        require(village.foodSupply == food, "Simulation-off mode changed abstract resources");
        require(village.lastSimulatedDay == simulated,
                "Simulation-off mode advanced the abstract village clock");

        village.observedPopulation = 6;
        village.materialSupply = 1_000.0;
        village.treasury = 500.0;
        village.developmentPoints = 200.0;
        VillageProsperityEngine.advanceVisualOnlyPulse(village, state.seed, state.economicDay);
        require(village.lastSimulatedDay == state.economicDay,
                "Visual-only mode did not process a loaded pulse");
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
            require(drift >= -0.012 && drift <= 0.012,
                    "Village market drift exceeded cap for " + asset.ticker());
        }
    }

    private static void testLifecycleAndRestoration() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-lifecycle-");
        try {
            EconomyService service = new EconomyService();
            service.configureVillageProsperity(true, true);
            service.startWithSeed(root, 44L, 0L, 0L);
            UUID resident = UUID.randomUUID();
            EconomyService.VillageSnapshot snapshot = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld",
                            pack(100, 64, 100),
                            1L,
                            0L,
                            1,
                            4,
                            0,
                            false,
                            List.of(new EconomyService.ResidentObservation(
                                    resident, "minecraft:farmer", pack(100, 64, 100)))));
            require(snapshot != null, "Village observation failed");
            UUID villageId = snapshot.village().villageId;
            require(service.recordVillagerDeath(
                            villageId,
                            resident,
                            "minecraft:farmer",
                            pack(100, 64, 100),
                            VillageProsperityEngine.IncidentCause.PLAYER,
                            UUID.randomUUID()),
                    "Player-caused death was not recorded");
            EconomyState.VillageRecord abandoned = service.villageSnapshot(villageId).village();
            require(abandoned.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED,
                    "Player extinction did not abandon the village");
            require(abandoned.population == 0, "Extinct village retained population");
            require(!service.allowBankerReplacementAt(pack(100, 64, 100)),
                    "Abandoned village received a free Banker replacement");

            UUID player = UUID.randomUUID();
            require(service.deposit(player, 30), "Could not fund test account");
            EconomyService.VillageFundingResult funded = service.fundVillage(player, villageId, 25);
            require(funded.funded() && funded.restorationActivated(),
                    "Restoration target did not activate");
            EconomyState mutable = service.snapshot();
            EconomyState.VillageRecord restored = mutable.villages.get(villageId);
            for (int day = 0; day < 5; day++) {
                mutable.advanceOneDay(true);
            }
            require(restored.lifecycle == VillageProsperityEngine.Lifecycle.RECOVERING,
                    "Funded abandoned village did not enter recovery");
            require(restored.population == 2 && restored.pendingSettlers >= 2,
                    "Recovery did not queue two settlers");
        } finally {
            deleteTree(root);
        }
    }

    private static void testPersistenceAndStableIdentity() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-persistence-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1234L, 0L, 0L);
            EconomyService.VillageObservation observation = new EconomyService.VillageObservation(
                    "minecraft:overworld",
                    pack(20, 70, -40),
                    5L,
                    0L,
                    7,
                    10,
                    0,
                    false,
                    List.of());
            EconomyService.VillageSnapshot first = service.observeVillage(observation);
            EconomyService.VillageSnapshot second = service.observeVillage(observation);
            require(first.village().villageId.equals(second.village().villageId),
                    "Repeated observation changed stable village identity");
            UUID id = first.village().villageId;
            require(service.saveNowAt(0L, 0L), "Could not save village state");

            EconomyService reloaded = new EconomyService();
            reloaded.startWithSeed(root, 9999L, 0L, 0L);
            EconomyService.VillageSnapshot loaded = reloaded.villageSnapshot(id);
            require(loaded != null, "Village record did not persist");
            require(loaded.village().population == 7, "Village population did not persist");
            require(loaded.village().housingCapacity >= 10, "Village housing did not persist");
        } finally {
            deleteTree(root);
        }
    }

    private static EconomyState.VillageRecord village(
            EconomyState state, int population, int housing) {
        UUID id = UUID.randomUUID();
        EconomyState.VillageRecord village = state.village(id);
        village.villageId = id;
        village.dimensionKey = "minecraft:overworld";
        village.centerPos = pack(0, 64, 0);
        village.discoveredDay = state.economicDay;
        village.lastSimulatedDay = state.economicDay;
        village.lastCensusDay = state.economicDay;
        village.population = population;
        village.observedPopulation = population;
        village.housingCapacity = housing;
        village.foodSupply = population * 24.0;
        village.materialSupply = population * 12.0;
        village.treasury = population * 2.0;
        village.developmentPoints = population;
        village.prosperity = 55.0;
        village.safety = 70.0;
        return village;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFFL);
    }

    private static void deleteTree(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
