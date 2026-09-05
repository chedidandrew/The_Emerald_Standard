package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;

/** Regression coverage for the beta Village Prosperity System. */
public final class VillageProsperityRegressionTest {
    private VillageProsperityRegressionTest() {}

    public static void main(String[] args) throws Exception {
        testSimulationAndProjects();
        testIndependentToggleBehavior();
        testMarketInfluenceBound();
        testPlayerMarketShadowIsolationAndRelease();
        testMarketShadowResidentHistoryCap();
        testLifecycleAndRestoration();
        testPersistenceAndStableIdentity();
        testRecoveryWaitsForPhysicalSettlers();
        testMarketIntegrationToggle();
        testResidentEmigration();
        testPhysicalPopulationRequiresMaterializedSettlers();
        testInfectionAndCureReconciliation();
        testPreferredStableIdentity();
        testFunctionalTierCanDecline();
        testZeroPopulationDiscoveryIsAbandoned();
        testSurvivorsCanRecoverFromDevastation();
        testProfessionSpecializationIsBounded();
        testInfectedDeathDoesNotDoubleDecrement();
        testNearbyVillageSnapshots();
        testAdaptiveCatchUpBatch();
        testUnchangedBankAssociationDoesNotRewriteSave();
        testBankReplacementUsesPersistedAssociation();
        testNetWorthCannotOverflowLongAddition();
        testEpsilonOversellIsRejected();
        testProjectBoundsAndRetryRoundTrip();
        testQueuedProjectBenefitsLeadMaterialization();
        testSimulationOnlyProjectsRemainFunctional();
        testPendingSettlersCountExactlyOnce();
        testUniqueProjectsNeverDuplicate();
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

    private static void testPlayerMarketShadowIsolationAndRelease() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-market-shadow-");
        try {
            UUID attackedVillageId =
                    UUID.fromString("00000000-0000-0000-0000-000000000101");
            UUID naturalVillageId =
                    UUID.fromString("00000000-0000-0000-0000-000000000202");
            UUID firstAttackedResident =
                    UUID.fromString("00000000-0000-0000-0000-000000001101");
            UUID secondAttackedResident =
                    UUID.fromString("00000000-0000-0000-0000-000000001102");
            UUID naturallyKilledAttackedResident =
                    UUID.fromString("00000000-0000-0000-0000-000000001103");
            UUID naturalResident =
                    UUID.fromString("00000000-0000-0000-0000-000000002201");

            EconomyState seed = EconomyState.fresh(13_579L, 0L, 0L);
            EconomyState.VillageRecord attacked =
                    marketVillage(seed, attackedVillageId, pack(0, 64, 0), 8, 12);
            attacked.prosperity = 82.0;
            attacked.safety = 88.0;
            attacked.developmentTier = 3;
            attacked.miningOutput = 2.4;
            attacked.agricultureOutput = 5.6;
            attacked.tradeOutput = 1.6;
            attacked.redstoneOutput = 0.32;
            attacked.alchemyOutput = 0.20;
            attacked.transportOutput = 0.80;
            attacked.securityOutput = 0.80;
            addActiveResident(
                    attacked, firstAttackedResident, "minecraft:farmer", pack(1, 64, 0));
            addActiveResident(
                    attacked, secondAttackedResident, "minecraft:cleric", pack(2, 64, 0));
            addActiveResident(
                    attacked,
                    naturallyKilledAttackedResident,
                    "minecraft:armorer",
                    pack(3, 64, 0));

            EconomyState.VillageRecord natural =
                    marketVillage(seed, naturalVillageId, pack(400, 64, 0), 10, 14);
            natural.prosperity = 38.0;
            natural.safety = 52.0;
            natural.developmentTier = 1;
            natural.miningOutput = 0.90;
            natural.agricultureOutput = 3.0;
            natural.tradeOutput = 1.0;
            natural.redstoneOutput = 0.10;
            natural.alchemyOutput = 0.10;
            natural.transportOutput = 0.50;
            natural.securityOutput = 0.40;
            addActiveResident(
                    natural, naturalResident, "minecraft:toolsmith", pack(401, 64, 0));

            seed.save(root.resolve("the_emerald_standard.properties"));
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 99L, 0L, 0L);

            VillageProsperityEngine.VillageFundamentals before =
                    service.snapshot().villageFundamentals();
            require(before.eligibleVillages() == 2,
                    "Two-village market-shadow fixture did not start fully eligible");
            require(service.recordVillagerDeath(
                            attackedVillageId,
                            firstAttackedResident,
                            "minecraft:farmer",
                            pack(1, 64, 0),
                            VillageProsperityEngine.IncidentCause.PLAYER,
                            UUID.fromString("00000000-0000-0000-0000-000000009001")),
                    "First player casualty was not recorded");

            EconomyState afterFirstState = service.snapshot();
            VillageProsperityEngine.VillageFundamentals afterFirst =
                    afterFirstState.villageFundamentals();
            requireSameFundamentals(
                    before,
                    afterFirst,
                    "A player casualty changed the weighted market fundamentals");
            EconomyState.VillageMarketShadow firstShadow =
                    afterFirstState.villageMarketShadows.get(attackedVillageId);
            require(firstShadow != null
                            && firstShadow.present
                            && firstShadow.contributionEligible
                            && firstShadow.formulaVersion
                                    == VillageProsperityEngine.MARKET_SHADOW_FORMULA_VERSION
                            && firstShadow.counterfactualVillage != null
                            && firstShadow.recoveryPopulation == 8,
                    "Player casualty did not capture the pre-incident market contribution");

            require(service.recordVillagerDeath(
                            attackedVillageId,
                            secondAttackedResident,
                            "minecraft:cleric",
                            pack(2, 64, 0),
                            VillageProsperityEngine.IncidentCause.PLAYER,
                            UUID.fromString("00000000-0000-0000-0000-000000009002")),
                    "Repeated player casualty was not recorded");
            EconomyState afterRepeatState = service.snapshot();
            requireSameFundamentals(
                    before,
                    afterRepeatState.villageFundamentals(),
                    "A repeated player casualty changed the shadowed contribution");
            requireSameShadow(
                    firstShadow,
                    afterRepeatState.villageMarketShadows.get(attackedVillageId),
                    "A repeated player casualty recaptured the market shadow");

            EconomyState advancingShadowState = afterRepeatState.copy();
            VillageProsperityEngine.VillageFundamentals shadowBeforeDay =
                    singleVillageFundamentals(advancingShadowState, attackedVillageId);
            EconomyState.VillageMarketShadow shadowStateBeforeDay =
                    advancingShadowState.villageMarketShadows.get(attackedVillageId).copy();
            advancingShadowState.advanceOneDay(true, false, true, true);
            EconomyState.VillageMarketShadow shadowAfterDay =
                    advancingShadowState.villageMarketShadows.get(attackedVillageId);
            require(shadowAfterDay != null
                            && shadowAfterDay.counterfactualVillage.lastSimulatedDay
                                    == advancingShadowState.economicDay,
                    "An active market shadow did not advance its counterfactual village");
            require(!sameFundamentalsBits(
                            shadowBeforeDay,
                            singleVillageFundamentals(
                                    advancingShadowState, attackedVillageId)),
                    "An active market shadow held a static market contribution across a simulated day");
            require(!sameShadowContributionBits(shadowStateBeforeDay, shadowAfterDay),
                    "Daily counterfactual simulation did not re-price the active shadow");

            require(service.recordVillagerDeath(
                            attackedVillageId,
                            naturallyKilledAttackedResident,
                            "minecraft:armorer",
                            pack(3, 64, 0),
                            VillageProsperityEngine.IncidentCause.HOSTILE,
                            null),
                    "Hostile casualty in the already-shadowed village was not recorded");
            EconomyState afterShadowedHostileState = service.snapshot();
            EconomyState.VillageMarketShadow hostileShadow =
                    afterShadowedHostileState.villageMarketShadows.get(attackedVillageId);
            EconomyState.ResidentRecord counterfactualCasualty =
                    hostileShadow.counterfactualVillage.residents.get(
                            naturallyKilledAttackedResident);
            require(hostileShadow.contributionEligible
                            && hostileShadow.counterfactualVillage.population == 7
                            && hostileShadow.counterfactualVillage.hostileCasualties == 1
                            && counterfactualCasualty != null
                            && counterfactualCasualty.status
                                    == VillageProsperityEngine.ResidentStatus.DEAD,
                    "A genuine casualty was not applied to the shadow counterfactual");
            require(!sameFundamentalsBits(
                            afterRepeatState.villageFundamentals(),
                            afterShadowedHostileState.villageFundamentals()),
                    "A genuine casualty in a shadowed village did not change its market fundamentals");

            require(service.recordVillagerDeath(
                            naturalVillageId,
                            naturalResident,
                            "minecraft:toolsmith",
                            pack(401, 64, 0),
                            VillageProsperityEngine.IncidentCause.ENVIRONMENT,
                            null),
                    "Natural casualty in the unshadowed village was not recorded");
            EconomyState afterNaturalState = service.snapshot();
            require(!sameFundamentalsBits(
                            afterShadowedHostileState.villageFundamentals(),
                            afterNaturalState.villageFundamentals()),
                    "A natural casualty in an unshadowed village did not change fundamentals");
            require(!afterNaturalState.villageMarketShadows.containsKey(naturalVillageId),
                    "A natural casualty incorrectly created a market shadow");

            EconomyState releaseState = afterNaturalState.copy();
            EconomyState.VillageMarketShadow releaseShadow =
                    releaseState.villageMarketShadows.get(attackedVillageId);
            EconomyState.VillageRecord recovering =
                    releaseState.existingVillage(attackedVillageId);
            int recoveryPopulation = releaseShadow.recoveryPopulation;
            recovering.lifecycle = VillageProsperityEngine.Lifecycle.ACTIVE;
            recovering.population = recoveryPopulation;
            releaseState.economicDay = releaseShadow.minimumReleaseDay - 2L;
            releaseState.advanceOneDay(false, false, false, false);
            require(releaseState.villageMarketShadows.containsKey(attackedVillageId),
                    "Market shadow released before its cooldown elapsed");

            recovering.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
            releaseState.advanceOneDay(false, false, false, false);
            require(releaseState.villageMarketShadows.containsKey(attackedVillageId),
                    "Market shadow released before the village returned to Active");

            recovering.lifecycle = VillageProsperityEngine.Lifecycle.ACTIVE;
            recovering.population = recoveryPopulation - 1;
            releaseState.advanceOneDay(false, false, false, false);
            require(releaseState.villageMarketShadows.containsKey(attackedVillageId),
                    "Market shadow released before the population baseline recovered");

            recovering.population = recoveryPopulation;
            releaseState.advanceOneDay(false, false, false, false);
            require(!releaseState.villageMarketShadows.containsKey(attackedVillageId),
                    "Recovered Active village retained its expired market shadow");
        } finally {
            deleteTree(root);
        }
    }

    private static void testMarketShadowResidentHistoryCap() throws Exception {
        Path root = Files.createTempDirectory("emerald-market-shadow-resident-cap-");
        try {
            Path save = root.resolve("the_emerald_standard.properties");
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000303");
            UUID shadowOnlyResident =
                    UUID.fromString("00000000-0000-0000-0000-000000003301");
            UUID unmatchedInfection =
                    UUID.fromString("00000000-0000-0000-0000-000000003302");
            UUID existingInfection = new UUID(0x303L, 1L);

            EconomyState seed = EconomyState.fresh(30_303L, 0L, 0L);
            EconomyState.VillageRecord village =
                    marketVillage(seed, villageId, pack(800, 64, 800), 8, 12);
            for (int index = 0;
                    index < VillageProsperityEngine.RESIDENT_HISTORY_LIMIT - 1;
                    index++) {
                UUID residentId = new UUID(0x303L, index + 1L);
                EconomyState.ResidentRecord resident = new EconomyState.ResidentRecord();
                resident.residentId = residentId;
                resident.profession = "minecraft:none";
                resident.status = residentId.equals(existingInfection)
                        ? VillageProsperityEngine.ResidentStatus.INFECTED
                        : VillageProsperityEngine.ResidentStatus.EMIGRATED;
                resident.lastSeenDay = seed.economicDay;
                resident.lastKnownPos = pack(index * 32, 64, 0);
                village.residents.put(residentId, resident);
            }

            EconomyState.VillageMarketShadow shadow =
                    VillageProsperityEngine.captureMarketShadow(village, seed.economicDay, 60L);
            require(shadow != null && shadow.counterfactualVillage != null,
                    "Could not create resident-cap market-shadow fixture");
            EconomyState.ResidentRecord shadowOnly = new EconomyState.ResidentRecord();
            shadowOnly.residentId = shadowOnlyResident;
            shadowOnly.profession = "minecraft:none";
            shadowOnly.status = VillageProsperityEngine.ResidentStatus.EMIGRATED;
            shadowOnly.lastSeenDay = seed.economicDay;
            shadowOnly.lastKnownPos = pack(-800, 64, -800);
            shadow.counterfactualVillage.residents.put(shadowOnlyResident, shadowOnly);
            require(village.residents.size()
                            == VillageProsperityEngine.RESIDENT_HISTORY_LIMIT - 1
                            && shadow.counterfactualVillage.residents.size()
                                    == VillageProsperityEngine.RESIDENT_HISTORY_LIMIT,
                    "Resident-cap market-shadow fixture has the wrong history sizes");
            seed.villageMarketShadows.put(villageId, shadow);
            seed.save(save);

            EconomyService service = new EconomyService();
            service.startWithSeed(root, 99L, 0L, 0L);
            require(service.recordResidentStatus(
                            villageId,
                            unmatchedInfection,
                            "minecraft:farmer",
                            pack(4_000, 64, 4_000),
                            VillageProsperityEngine.ResidentStatus.INFECTED),
                    "Unmatched infection was rejected at the market-shadow resident cap");

            EconomyState after = service.snapshot();
            EconomyState.VillageMarketShadow afterShadow =
                    after.villageMarketShadows.get(villageId);
            require(after.existingVillage(villageId).residents.size()
                            == VillageProsperityEngine.RESIDENT_HISTORY_LIMIT,
                    "Live village did not remain at the resident-history cap");
            require(afterShadow != null
                            && afterShadow.counterfactualVillage != null
                            && afterShadow.counterfactualVillage.residents.size()
                                    == VillageProsperityEngine.RESIDENT_HISTORY_LIMIT,
                    "Counterfactual exceeded the resident-history cap after an unmatched infection");
            EconomyState.ResidentRecord counterfactualInfection =
                    afterShadow.counterfactualVillage.residents.get(unmatchedInfection);
            require(counterfactualInfection != null
                            && counterfactualInfection.status
                                    == VillageProsperityEngine.ResidentStatus.INFECTED,
                    "Counterfactual did not retain the unmatched infection while trimming history");
            require(afterShadow.counterfactualVillage.residents.containsKey(existingInfection),
                    "Counterfactual trimming discarded an existing infection dedupe record");

            int counterfactualPopulationBeforeRepeat =
                    afterShadow.counterfactualVillage.population;
            require(service.recordResidentStatus(
                            villageId,
                            existingInfection,
                            "minecraft:none",
                            pack(0, 64, 0),
                            VillageProsperityEngine.ResidentStatus.INFECTED),
                    "Repeated capped infection observation was rejected");
            EconomyState afterRepeat = service.snapshot();
            require(afterRepeat.villageMarketShadows.get(villageId)
                            .counterfactualVillage.population
                            == counterfactualPopulationBeforeRepeat,
                    "A retained capped infection was counted twice");
            afterRepeat.validate();
            require(service.saveNowAt(0L, 0L),
                    "Resident-capped counterfactual was not saveable: " + service.lastError());

            EconomyState reloaded = EconomyState.load(save, 100L, 0L, 0L);
            reloaded.validate();
            EconomyState.VillageMarketShadow reloadedShadow =
                    reloaded.villageMarketShadows.get(villageId);
            EconomyState.ResidentRecord reloadedInfection = reloadedShadow == null
                    || reloadedShadow.counterfactualVillage == null
                    ? null
                    : reloadedShadow.counterfactualVillage.residents.get(unmatchedInfection);
            require(reloadedShadow != null
                            && reloadedShadow.counterfactualVillage != null
                            && reloadedShadow.counterfactualVillage.residents.size()
                                    == VillageProsperityEngine.RESIDENT_HISTORY_LIMIT
                            && reloadedInfection != null
                            && reloadedInfection.status
                                    == VillageProsperityEngine.ResidentStatus.INFECTED,
                    "Resident-capped counterfactual did not round-trip through persistence");
        } finally {
            deleteTree(root);
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
            require(mutable.villageFundamentals().eligibleVillages() == 1,
                    "Player-damaged village lost its captured contribution before full recovery");
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
            require(after.village().lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED,
                    "Emigration left a zero-population village in a productive lifecycle");
            require(after.village().pendingSettlers == 0,
                    "Emigration incorrectly queued free replacement settlers");
            require(after.fundamentals().eligibleVillages() == 0,
                    "Emigration collapse continued influencing market fundamentals");
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
            require(service.markGeneratedBankRegion(101L, pack(0, 64, 0)),
                    "Could not seed the coarse bank-region identity test");
            EconomyService.VillageSnapshot first = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(0,64,0), 101L, 0L, 4, 6, 0, false, List.of()));
            EconomyService.VillageSnapshot second = service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(120,64,0), 202L, 0L, 4, 6, 0, false, List.of()));
            require(!first.village().villageId.equals(second.village().villageId),
                    "Separated villages unexpectedly shared identity");
            EconomyService.VillageSnapshot movedObservation = service.observeVillage(
                     second.village().villageId,
                     new EconomyService.VillageObservation(
                             "minecraft:overworld", pack(55,64,0), 101L, 0L, 4, 6, 0, false, List.of()));
            require(movedObservation.village().villageId.equals(second.village().villageId),
                    "Persisted resident tags did not win over a conflicting coarse bank region");
            require(service.villageIdForBankRegion(101L).equals(first.village().villageId),
                    "A preferred identity stole another village's existing bank association");
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

    private static void testZeroPopulationDiscoveryIsAbandoned() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-empty-discovery-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 321L, 0L, 0L);
            EconomyService.VillageSnapshot discovered = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", pack(0, 64, 0), 0L, 0L,
                            0, 4, 0, false, List.of()));
            require(discovered.village().lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED,
                    "An empty discovered village was marked active");
            require(discovered.fundamentals().eligibleVillages() == 0,
                    "An empty discovered village influenced market fundamentals");

            require(service.tickAt(20L * EconomyService.TICKS_PER_MINECRAFT_DAY, 0L),
                    "Could not advance empty-village clock");
            EconomyState.VillageRecord after = service.villageSnapshot(
                    discovered.village().villageId).village();
            require(after.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                            && after.population == 0
                            && after.pendingSettlers == 0,
                    "An untouched empty village invented recovery settlers");
        } finally { deleteTree(root); }
    }

    private static void testSurvivorsCanRecoverFromDevastation() {
        EconomyState state = EconomyState.fresh(432L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 1, 8);
        village.lifecycle = VillageProsperityEngine.Lifecycle.DEVASTATED;
        village.lastIncidentDay = 0L;
        village.lastIncidentCause = VillageProsperityEngine.IncidentCause.HOSTILE;
        village.safety = 70.0;
        village.prosperity = 60.0;
        village.foodSupply = 1_000.0;
        village.materialSupply = 1_000.0;
        village.treasury = 1_000.0;

        VillageProsperityEngine.advanceOneDay(village, state.seed, 8L, true, false);
        require(village.lifecycle == VillageProsperityEngine.Lifecycle.RECOVERING,
                "A safe village with living survivors remained permanently devastated");
        for (long day = 9L; day <= 5_000L && village.population == 1; day++) {
            VillageProsperityEngine.advanceOneDay(village, state.seed, day, true, false);
        }
        require(village.population > 1,
                "A recovering survivor village never regained population");
    }

    private static void testProfessionSpecializationIsBounded() {
        EconomyState state = EconomyState.fresh(456L, 0L, 0L);
        EconomyState.VillageRecord baseline = village(state, 8, 12);
        baseline.villageId = UUID.fromString("00000000-0000-0000-0000-000000000456");
        EconomyState.VillageRecord specialized = baseline.copy();
        for (int i = 0; i < 8; i++) {
            EconomyState.ResidentRecord farmer = new EconomyState.ResidentRecord();
            farmer.residentId = new UUID(0L, i + 1L);
            farmer.profession = "minecraft:farmer";
            farmer.status = VillageProsperityEngine.ResidentStatus.ACTIVE;
            specialized.residents.put(farmer.residentId, farmer);
        }
        VillageProsperityEngine.advanceOneDay(baseline, state.seed, 1L, false, false);
        VillageProsperityEngine.advanceOneDay(specialized, state.seed, 1L, false, false);
        require(specialized.agricultureOutput > baseline.agricultureOutput,
                "Farmer professions did not affect agriculture output");
        require(specialized.agricultureOutput <= baseline.agricultureOutput * 1.1200001,
                "Profession specialization exceeded its 12% sector cap");
        require(Double.doubleToLongBits(specialized.miningOutput)
                        == Double.doubleToLongBits(baseline.miningOutput),
                "Farmer specialization leaked into unrelated mining output");
    }

    private static void testInfectedDeathDoesNotDoubleDecrement() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-infected-death-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 654L, 0L, 0L);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            long firstPos = pack(3, 64, 3);
            EconomyService.VillageSnapshot observed = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", pack(4, 64, 3), 0L, 0L,
                            2, 4, 0, false,
                            List.of(
                                    new EconomyService.ResidentObservation(
                                            first, "minecraft:farmer", firstPos),
                                    new EconomyService.ResidentObservation(
                                            second, "minecraft:none", pack(5, 64, 3)))));
            UUID villageId = observed.village().villageId;
            UUID zombieId = UUID.randomUUID();
            require(service.recordResidentStatus(
                            villageId, zombieId, "minecraft:farmer", firstPos,
                            VillageProsperityEngine.ResidentStatus.INFECTED),
                    "Could not record infection before zombie death");
            require(service.villageSnapshot(villageId).village().population == 1,
                    "Infection did not remove exactly one productive resident");
            require(service.recordVillagerDeath(
                            villageId, zombieId, "minecraft:farmer", firstPos,
                            VillageProsperityEngine.IncidentCause.PLAYER, UUID.randomUUID()),
                    "Could not record infected villager death");
            EconomyState.VillageRecord after = service.villageSnapshot(villageId).village();
            require(after.population == 1,
                    "An infected villager death decremented population a second time");
            require(after.playerCasualties == 1,
                    "Infected villager death did not retain its incident attribution");
            require(after.residents.values().stream().anyMatch(
                            resident -> resident.status == VillageProsperityEngine.ResidentStatus.DEAD),
                    "Infected resident was not transitioned to dead");
        } finally { deleteTree(root); }
    }

    private static void testProjectBoundsAndRetryRoundTrip() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-project-retry-");
        try {
            EconomyState seed = EconomyState.fresh(765L, 0L, 0L);
            EconomyState.VillageRecord village = village(seed, 8, 12);
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = 1L;
            project.type = VillageProsperityEngine.ProjectType.COTTAGE;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            EconomyState.VillageProject unloadedProject = new EconomyState.VillageProject();
            unloadedProject.projectId = 2L;
            unloadedProject.type = VillageProsperityEngine.ProjectType.WAREHOUSE;
            unloadedProject.economicProgress = 1.0;
            unloadedProject.economicComplete = true;
            unloadedProject.totalBlocks = unloadedProject.type.nominalBlocks();
            unloadedProject.retryAfterGameTick = Long.MAX_VALUE;
            village.projectSerial = 2L;
            village.projects.add(project);
            village.projects.add(unloadedProject);
            Path save = root.resolve("the_emerald_standard.properties");
            seed.save(save);

            EconomyService service = new EconomyService();
            service.startWithSeed(root, 999L, 0L, 0L);
            long origin = pack(20, 64, 20);
            long boundsMin = pack(18, 63, 18);
            long boundsMax = pack(25, 70, 25);
            require(service.reserveVillageProjectSite(
                            village.villageId, 1L, origin, boundsMin, boundsMax, 180),
                    "Could not reserve bounded project site");
            require(service.updateVillageProjectMaterialization(
                            village.villageId, 1L, 3, false, false),
                    "Could not record deterministic template prefix");
            require(service.deferVillageProjectMaterialization(village.villageId, 1L, 1_000L),
                    "Could not defer obstructed partial project");
            EconomyState.VillageProject deferred = service.villageSnapshot(
                    village.villageId).village().projects.get(0);
            require(deferred.originPos == origin
                            && deferred.boundsMinPos == boundsMin
                            && deferred.boundsMaxPos == boundsMax,
                    "A partial project lost its restart-safe reservation bounds");
            require(deferred.retryAfterGameTick > 1_000L
                            && deferred.materializationFailures == 1,
                    "Project retry did not persist an exponential backoff state");
            require(service.villageSnapshot(village.villageId).village()
                            .nextVisualProject(deferred.retryAfterGameTick - 1L) == null,
                    "Project retry gate was ignored before its due tick");

            long unloadedOrigin = pack(40, 64, 40);
            long unloadedMinimum = pack(38, 63, 38);
            long unloadedMaximum = pack(47, 72, 47);
            require(service.reserveVillageProjectSite(
                            village.villageId,
                            2L,
                            unloadedOrigin,
                            unloadedMinimum,
                            unloadedMaximum,
                            unloadedProject.totalBlocks),
                    "Could not reserve unloaded-boundary project site");
            require(service.deferVillageProjectMaterialization(
                            village.villageId, 2L, 2_000L, true),
                    "Could not defer unloaded-boundary project");
            EconomyState.VillageProject unloadedDeferred = service.villageSnapshot(
                    village.villageId).village().projects.get(1);
            require(unloadedDeferred.originPos == unloadedOrigin
                            && unloadedDeferred.boundsMinPos == unloadedMinimum
                            && unloadedDeferred.boundsMaxPos == unloadedMaximum
                            && unloadedDeferred.materializedBlocks == 0
                            && unloadedDeferred.retryAfterGameTick > 2_000L,
                    "Unloaded zero-progress project discarded its crash-recovery reservation");

            EconomyService reloaded = new EconomyService();
            reloaded.startWithSeed(root, 111L, 0L, 0L);
            EconomyState.VillageProject loaded = reloaded.villageSnapshot(
                    village.villageId).village().projects.get(0);
            require(loaded.boundsMinPos == boundsMin
                            && loaded.boundsMaxPos == boundsMax
                            && loaded.retryAfterGameTick == deferred.retryAfterGameTick
                            && loaded.materializationFailures == 1,
                    "Project bounds/backoff did not survive restart");
            require(reloaded.villageSnapshot(village.villageId).village()
                            .nextVisualProject(loaded.retryAfterGameTick) != null,
                    "Deferred project did not become eligible on its due tick");
            EconomyState.VillageProject unloadedLoaded = reloaded.villageSnapshot(
                    village.villageId).village().projects.get(1);
            require(unloadedLoaded.originPos == unloadedOrigin
                            && unloadedLoaded.boundsMinPos == unloadedMinimum
                            && unloadedLoaded.boundsMaxPos == unloadedMaximum,
                    "Unloaded project reservation did not survive restart");
            require(reloaded.updateVillageProjectMaterialization(
                            village.villageId, 1L, loaded.totalBlocks, true, false),
                    "Could not complete a project before integrity reconciliation");
            require(reloaded.requireManualVillageProjectRepair(
                            village.villageId,
                            1L,
                            2,
                            loaded.totalBlocks,
                            boundsMin,
                            boundsMax),
                    "Could not flag a damaged completed project for in-world repair");
            EconomyState.VillageProject damaged = reloaded.villageSnapshot(
                    village.villageId).village().projects.get(0);
            EconomyState.VillageProject nextAfterDamage = reloaded.villageSnapshot(
                    village.villageId).village().nextVisualProject(Long.MAX_VALUE);
            require(!damaged.materializedComplete
                            && damaged.materializedBlocks == 2
                            && damaged.manualRepairRequired
                            && (nextAfterDamage == null
                                    || nextAfterDamage.projectId != damaged.projectId),
                    "Damaged project retained benefits or entered the item-regenerating queue");
            EconomyState persistedDamage = EconomyState.load(save, 333L, 0L, 0L);
            require(persistedDamage.existingVillage(village.villageId)
                            .projects.get(0).manualRepairRequired,
                    "Manual structure-repair state did not survive restart");
            require(reloaded.reconcileVillageProjectMaterializationAndBounds(
                            village.villageId,
                            1L,
                            loaded.totalBlocks,
                            loaded.totalBlocks,
                            true,
                            boundsMin,
                            boundsMax),
                    "Could not restore authority after an in-world structure repair");
            EconomyState.VillageProject restored = reloaded.villageSnapshot(
                    village.villageId).village().projects.get(0);
            EconomyState.VillageProject nextAfterRestore = reloaded.villageSnapshot(
                    village.villageId).village().nextVisualProject(Long.MAX_VALUE);
            require(restored.materializedComplete
                            && !restored.manualRepairRequired
                            && (nextAfterRestore == null
                                    || nextAfterRestore.projectId != restored.projectId),
                    "A restored structure retained its manual-repair gate");

            long expandedMinimum = pack(17, 62, 17);
            long expandedMaximum = pack(26, 74, 26);
            int expandedTotal = loaded.totalBlocks + 10;
            require(reloaded.reconcileVillageProjectMaterializationAndBounds(
                            village.villageId,
                            1L,
                            2,
                            expandedTotal,
                            false,
                            expandedMinimum,
                            expandedMaximum),
                    "Could not atomically reconcile expanded project bounds");
            EconomyState.VillageProject expanded = reloaded.villageSnapshot(
                    village.villageId).village().projects.get(0);
            require(expanded.totalBlocks == expandedTotal
                            && expanded.materializedBlocks == 2
                            && expanded.boundsMinPos == expandedMinimum
                            && expanded.boundsMaxPos == expandedMaximum,
                    "Expanded template progress and bounds were not reconciled together");

            long reversedMinimum = pack(27, 75, 27);
            long reversedMaximum = pack(16, 61, 16);
            require(!reloaded.reconcileVillageProjectMaterializationAndBounds(
                            village.villageId,
                            1L,
                            1,
                            expandedTotal + 1,
                            false,
                            reversedMinimum,
                            reversedMaximum),
                    "Malformed project bounds were accepted");
            EconomyState.VillageProject unchanged = reloaded.villageSnapshot(
                    village.villageId).village().projects.get(0);
            require(unchanged.totalBlocks == expandedTotal
                            && unchanged.materializedBlocks == 2
                            && unchanged.boundsMinPos == expandedMinimum
                            && unchanged.boundsMaxPos == expandedMaximum,
                    "Rejected bounds reconciliation partially mutated project state");

            EconomyService expandedReload = new EconomyService();
            expandedReload.startWithSeed(root, 222L, 0L, 0L);
            EconomyState.VillageProject persistedExpansion = expandedReload.villageSnapshot(
                    village.villageId).village().projects.get(0);
            require(persistedExpansion.totalBlocks == expandedTotal
                            && persistedExpansion.boundsMinPos == expandedMinimum
                            && persistedExpansion.boundsMaxPos == expandedMaximum,
                    "Expanded authoritative project bounds did not survive restart");
        } finally { deleteTree(root); }
    }

    private static void testQueuedProjectBenefitsLeadMaterialization() {
        EconomyState state = EconomyState.fresh(4_040L, 0L, 0L);
        EconomyState.VillageRecord baseline = village(state, 12, 18);
        baseline.villageId = UUID.fromString("00000000-0000-0000-0000-000000004040");
        baseline.foodSupply = 2_000.0;
        baseline.materialSupply = 2_000.0;
        baseline.treasury = 2_000.0;
        baseline.prosperity = 60.0;
        baseline.safety = 70.0;

        EconomyState.VillageRecord unbuilt = baseline.copy();
        EconomyState.VillageProject unbuiltWarehouse = completedProject(
                1L, VillageProsperityEngine.ProjectType.WAREHOUSE, false, false);
        unbuilt.projects.add(unbuiltWarehouse);

        EconomyState.VillageRecord built = baseline.copy();
        EconomyState.VillageProject builtWarehouse = completedProject(
                1L, VillageProsperityEngine.ProjectType.WAREHOUSE, true, false);
        built.projects.add(builtWarehouse);

        EconomyState.VillageRecord damaged = baseline.copy();
        EconomyState.VillageProject damagedWarehouse = completedProject(
                1L, VillageProsperityEngine.ProjectType.WAREHOUSE, false, false);
        damagedWarehouse.originPos = pack(4, 64, 4);
        damagedWarehouse.materializedBlocks = damagedWarehouse.totalBlocks - 1;
        damagedWarehouse.manualRepairRequired = true;
        damaged.projects.add(damagedWarehouse);

        VillageProsperityEngine.advanceOneDay(baseline, state.seed, 1L, false, true);
        VillageProsperityEngine.advanceOneDay(unbuilt, state.seed, 1L, false, true);
        VillageProsperityEngine.advanceOneDay(built, state.seed, 1L, false, true);
        VillageProsperityEngine.advanceOneDay(damaged, state.seed, 1L, false, true);
        require(unbuilt.tradeOutput > baseline.tradeOutput,
                "A queued economically complete Warehouse did not affect production");
        require(Double.doubleToLongBits(unbuilt.tradeOutput)
                        == Double.doubleToLongBits(built.tradeOutput),
                "Queued and materialized Warehouses had different economic authority");
        require(Double.doubleToLongBits(damaged.tradeOutput)
                        == Double.doubleToLongBits(baseline.tradeOutput),
                "A Warehouse awaiting manual repair retained its production benefit");
        require(VillageProsperityEngine.isProjectOperational(unbuiltWarehouse)
                        && VillageProsperityEngine.isProjectOperational(builtWarehouse)
                        && !VillageProsperityEngine.isProjectOperational(damagedWarehouse),
                "Project authority did not follow economic completion and manual-repair state");

        EconomyState.VillageRecord oldCompressedBacklog = baseline.copy();
        EconomyState.VillageProject oldAbstractWarehouse = completedProject(
                3L, VillageProsperityEngine.ProjectType.WAREHOUSE, false, true);
        oldCompressedBacklog.projects.add(oldAbstractWarehouse);
        VillageProsperityEngine.advanceOneDay(
                oldCompressedBacklog, state.seed, 1L, false, true);
        require(!oldAbstractWarehouse.abstractOnly
                        && VillageProsperityEngine.isProjectOperational(oldAbstractWarehouse),
                "Visual mode migration changed an economic backlog's authority");

        EconomyState.VillageRecord housing = baseline.copy();
        int originalHousing = housing.housingCapacity;
        housing.housingCapacity += VillageProsperityEngine.ProjectType.COTTAGE.housingGain();
        EconomyState.VillageProject cottage = completedProject(
                2L, VillageProsperityEngine.ProjectType.COTTAGE, false, false);
        housing.projects.add(cottage);
        require(VillageProsperityEngine.effectiveHousingCapacity(housing)
                        == originalHousing + VillageProsperityEngine.ProjectType.COTTAGE.housingGain(),
                "Queued economically complete Cottage did not grant abstract housing");
        cottage.manualRepairRequired = true;
        cottage.originPos = pack(8, 64, 8);
        require(VillageProsperityEngine.effectiveHousingCapacity(housing) == originalHousing,
                "A Cottage awaiting manual repair retained its housing benefit");
    }

    private static void testSimulationOnlyProjectsRemainFunctional() {
        EconomyState state = EconomyState.fresh(4_041L, 0L, 0L);
        EconomyState.VillageRecord abstractVillage = village(state, 12, 18);
        abstractVillage.villageId = UUID.fromString("00000000-0000-0000-0000-000000004041");
        abstractVillage.foodSupply = 2_000.0;
        abstractVillage.materialSupply = 2_000.0;
        abstractVillage.treasury = 2_000.0;
        abstractVillage.prosperity = 60.0;
        abstractVillage.safety = 70.0;
        EconomyState.VillageProject warehouse = completedProject(
                1L, VillageProsperityEngine.ProjectType.WAREHOUSE, false, false);
        abstractVillage.projects.add(warehouse);

        EconomyState.VillageRecord physicalEquivalent = abstractVillage.copy();
        physicalEquivalent.projects.get(0).materializedComplete = true;
        VillageProsperityEngine.advanceOneDay(abstractVillage, state.seed, 1L, false, false);
        VillageProsperityEngine.advanceOneDay(physicalEquivalent, state.seed, 1L, false, true);

        require(warehouse.abstractOnly && VillageProsperityEngine.isProjectOperational(warehouse),
                "Simulation-only project was left waiting for a block world");
        require(Double.doubleToLongBits(abstractVillage.tradeOutput)
                        == Double.doubleToLongBits(physicalEquivalent.tradeOutput),
                "Simulation-only project lost its calibrated production benefit");
    }

    private static void testPendingSettlersCountExactlyOnce() throws Exception {
        EconomyState state = EconomyState.fresh(4_042L, 0L, 0L);
        EconomyState.VillageRecord queued = village(state, 7, 30);
        queued.villageId = UUID.fromString("00000000-0000-0000-0000-000000004042");
        queued.observedPopulation = 7;
        queued.pendingSettlers = 5;
        queued.foodSupply = 2_000.0;
        queued.materialSupply = 2_000.0;
        queued.treasury = 2_000.0;
        queued.developmentPoints = 500.0;
        queued.prosperity = 70.0;
        queued.safety = 80.0;
        queued.projects.add(completedProject(
                1L, VillageProsperityEngine.ProjectType.WAREHOUSE, false, false));
        queued.projects.add(completedProject(
                2L, VillageProsperityEngine.ProjectType.MINE_ENTRANCE, false, false));
        EconomyState.VillageProject queuedProject = new EconomyState.VillageProject();
        queuedProject.projectId = 3L;
        queuedProject.type = VillageProsperityEngine.ProjectType.COTTAGE;
        queuedProject.totalBlocks = queuedProject.type.nominalBlocks();
        queued.projects.add(queuedProject);
        queued.projectSerial = 3L;

        EconomyState.VillageRecord censused = queued.copy();
        censused.population = 12;
        censused.observedPopulation = 12;
        censused.pendingSettlers = 0;
        EconomyState.VillageRecord residentsOnly = queued.copy();
        residentsOnly.pendingSettlers = 0;

        VillageProsperityEngine.advanceOneDay(queued, state.seed, 1L, false, true);
        VillageProsperityEngine.advanceOneDay(censused, state.seed, 1L, false, true);
        VillageProsperityEngine.advanceOneDay(residentsOnly, state.seed, 1L, false, true);

        require(VillageProsperityEngine.economicPopulation(queued) == 12
                        && VillageProsperityEngine.economicPopulation(censused) == 12,
                "Pending settlers were not included exactly once in economic population");
        require(Double.doubleToLongBits(queued.agricultureOutput)
                                == Double.doubleToLongBits(censused.agricultureOutput)
                        && Double.doubleToLongBits(queued.miningOutput)
                                == Double.doubleToLongBits(censused.miningOutput)
                        && Double.doubleToLongBits(queued.tradeOutput)
                                == Double.doubleToLongBits(censused.tradeOutput)
                        && Double.doubleToLongBits(queued.foodSupply)
                                == Double.doubleToLongBits(censused.foodSupply)
                        && Double.doubleToLongBits(queued.materialSupply)
                                == Double.doubleToLongBits(censused.materialSupply)
                        && Double.doubleToLongBits(queued.treasury)
                                == Double.doubleToLongBits(censused.treasury),
                "Queued and censused versions of the same population diverged economically");
        require(Double.doubleToLongBits(queued.projects.get(2).economicProgress)
                                == Double.doubleToLongBits(
                                        censused.projects.get(2).economicProgress)
                        && queued.projects.get(2).economicProgress
                                > residentsOnly.projects.get(2).economicProgress,
                "Pending settlers did not contribute exactly once to project workforce");
        require(queued.developmentTier == 3
                        && queued.developmentTier == censused.developmentTier
                        && residentsOnly.developmentTier < queued.developmentTier,
                "Pending settlers did not contribute to development-tier calculations");
        require(queued.agricultureOutput > residentsOnly.agricultureOutput,
                "Pending settlers did not contribute to ordinary village production");

        EconomyState.VillageRecord growthQueued = queued.copy();
        growthQueued.projects.remove(2);
        growthQueued.projectSerial = 2L;
        growthQueued.population = 7;
        growthQueued.observedPopulation = 7;
        growthQueued.pendingSettlers = 5;
        boolean grew = false;
        for (long day = 2L; day <= 10_000L; day++) {
            EconomyState.VillageRecord queuedAttempt = growthQueued.copy();
            EconomyState.VillageRecord censusedAttempt = growthQueued.copy();
            censusedAttempt.population = 12;
            censusedAttempt.observedPopulation = 12;
            censusedAttempt.pendingSettlers = 0;
            VillageProsperityEngine.advanceOneDay(
                    queuedAttempt, state.seed, day, false, true);
            VillageProsperityEngine.advanceOneDay(
                    censusedAttempt, state.seed, day, false, true);
            int queuedTotal = queuedAttempt.population + queuedAttempt.pendingSettlers;
            int censusedTotal = censusedAttempt.population + censusedAttempt.pendingSettlers;
            require(queuedTotal == censusedTotal,
                    "Pending settlers changed the deterministic population-growth decision");
            if (queuedTotal > 12) {
                require(queuedTotal == 13,
                        "One growth decision added more than one committed settler");
                grew = true;
                break;
            }
        }
        require(grew, "Pending-settler growth regression never reached a deterministic draw");

        EconomyState shadowState = EconomyState.fresh(4_045L, 0L, 0L);
        EconomyState.VillageRecord shadowedVillage = village(shadowState, 7, 30);
        shadowedVillage.pendingSettlers = 5;
        EconomyState.VillageMarketShadow committedShadow =
                VillageProsperityEngine.captureMarketShadow(shadowedVillage, 0L, 0L);
        require(committedShadow != null && committedShadow.recoveryPopulation == 12,
                "Market shadow did not capture committed economic population");
        shadowState.villageMarketShadows.put(shadowedVillage.villageId, committedShadow);
        shadowedVillage.pendingSettlers = 4;
        shadowState.advanceOneDay(false, false, false, false);
        require(shadowState.villageMarketShadows.containsKey(shadowedVillage.villageId),
                "Market shadow released before committed population recovered");
        shadowedVillage.pendingSettlers = 5;
        shadowState.advanceOneDay(false, false, false, false);
        require(!shadowState.villageMarketShadows.containsKey(shadowedVillage.villageId),
                "Market shadow ignored recovered queued settlers");

        EconomyState.VillageRecord capped = growthQueued.copy();
        capped.population = 63;
        capped.observedPopulation = 63;
        capped.pendingSettlers = 1;
        capped.housingCapacity = 100;
        VillageProsperityEngine.advanceOneDay(capped, state.seed, 10_001L, false, true);
        require(VillageProsperityEngine.economicPopulation(capped)
                                == VillageProsperityEngine.MAX_ABSTRACT_POPULATION
                        && capped.population + capped.pendingSettlers
                                == VillageProsperityEngine.MAX_ABSTRACT_POPULATION,
                "Economic population or queued growth exceeded the shared population cap");

        Path root = Files.createTempDirectory("emerald-village-pending-census-");
        try {
            EconomyState persisted = EconomyState.fresh(4_043L, 0L, 0L);
            EconomyState.VillageRecord awaitingCensus = village(persisted, 7, 30);
            awaitingCensus.pendingSettlers = 5;
            UUID villageId = awaitingCensus.villageId;
            persisted.save(root.resolve("the_emerald_standard.properties"));
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 4_043L, 0L, 0L);

            EconomyState.VillageRecord partial = service.observeVillage(
                    villageId,
                    new EconomyService.VillageObservation(
                            "minecraft:overworld",
                            awaitingCensus.centerPos,
                            0L,
                            0L,
                            8,
                            30,
                            0,
                            false,
                            List.of())).village();
            require(partial.population == 8
                            && partial.pendingSettlers == 4
                            && partial.population + partial.pendingSettlers == 12,
                    "A partial census counted a queued settler twice or lost one");

            EconomyState.VillageRecord complete = service.observeVillage(
                    villageId,
                    new EconomyService.VillageObservation(
                            "minecraft:overworld",
                            awaitingCensus.centerPos,
                            0L,
                            0L,
                            12,
                            30,
                            0,
                            false,
                            List.of())).village();
            require(complete.population == 12
                            && complete.pendingSettlers == 0
                            && VillageProsperityEngine.economicPopulation(complete) == 12,
                    "A complete census did not preserve committed population");
        } finally {
            deleteTree(root);
        }
    }

    private static void testUniqueProjectsNeverDuplicate() {
        EconomyState state = EconomyState.fresh(4_044L, 0L, 0L);
        EconomyState.VillageRecord village = village(state, 32, 80);
        village.foodSupply = 10_000.0;
        village.materialSupply = 10_000.0;
        village.treasury = 10_000.0;
        village.developmentPoints = 10_000.0;
        village.prosperity = 90.0;
        village.safety = 90.0;
        long projectId = 0L;
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            if (type == VillageProsperityEngine.ProjectType.COTTAGE
                    || type == VillageProsperityEngine.ProjectType.HOUSE
                    || type == VillageProsperityEngine.ProjectType.INN) {
                continue;
            }
            EconomyState.VillageProject project = completedProject(
                    ++projectId, type, true, false);
            if (type == VillageProsperityEngine.ProjectType.WAREHOUSE) {
                project.materializedComplete = false;
                project.materializedBlocks = project.totalBlocks - 1;
                project.originPos = pack(12, 64, 12);
                project.manualRepairRequired = true;
            }
            village.projects.add(project);
        }
        village.projectSerial = projectId;

        for (long day = 1L; day <= 2_000L; day++) {
            VillageProsperityEngine.advanceOneDay(village, state.seed, day, false, true);
        }
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            if (type == VillageProsperityEngine.ProjectType.COTTAGE
                    || type == VillageProsperityEngine.ProjectType.HOUSE
                    || type == VillageProsperityEngine.ProjectType.INN) {
                continue;
            }
            long count = village.projects.stream()
                    .filter(project -> project.type == type)
                    .count();
            require(count == 1L,
                    "Unique project approval duplicated " + type + " after completion or damage");
        }
    }

    private static EconomyState.VillageProject completedProject(
            long projectId,
            VillageProsperityEngine.ProjectType type,
            boolean materialized,
            boolean abstractOnly) {
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = projectId;
        project.type = type;
        project.economicProgress = 1.0;
        project.economicComplete = true;
        project.materializedBlocks = materialized ? type.nominalBlocks() : 0;
        project.totalBlocks = type.nominalBlocks();
        project.materializedComplete = materialized;
        project.abstractOnly = abstractOnly;
        return project;
    }

    private static void testNearbyVillageSnapshots() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-nearby-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 876L, 0L, 0L);
            EconomyService.VillageSnapshot near = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", pack(0, 64, 0), 0L, 0L,
                            4, 6, 0, false, List.of()));
            service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(1_000, 64, 0), 0L, 0L,
                    4, 6, 0, false, List.of()));
            service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:the_nether", pack(0, 64, 0), 0L, 0L,
                    4, 6, 0, false, List.of()));
            List<EconomyService.VillageSnapshot> nearby = service.villageSnapshotsNear(
                    "minecraft:overworld", List.of(pack(10, 64, 0)), 100.0);
            require(nearby.size() == 1
                            && nearby.get(0).village().villageId.equals(near.village().villageId),
                    "Nearby snapshot query copied unrelated or cross-dimension villages");
            require(service.villageSnapshotsNear(
                            "minecraft:overworld", List.of(), 100.0).isEmpty(),
                    "Empty proximity query returned village snapshots");
        } finally { deleteTree(root); }
    }

    private static void testAdaptiveCatchUpBatch() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-adaptive-catchup-");
        try {
            EconomyState seed = EconomyState.fresh(987L, 0L, 0L);
            for (int i = 0; i < 20; i++) {
                EconomyState.VillageRecord village = village(seed, 4, 6);
                village.centerPos = pack(i * 128, 64, 0);
                seed.account(new UUID(1L, i + 1L));
            }
            seed.save(root.resolve("the_emerald_standard.properties"));
            long future = 5_000L * EconomyService.MILLIS_PER_MINECRAFT_DAY;
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 123L, future, 0L);
            require(service.snapshot().economicDay > 0L
                            && service.snapshot().economicDay < 2_000L,
                    "Startup catch-up did not adapt to village/account workload");
            require(service.catchUpDaysRemaining() > 0L,
                    "Adaptive catch-up unexpectedly consumed the entire backlog");
        } finally { deleteTree(root); }
    }

    private static void testUnchangedBankAssociationDoesNotRewriteSave() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-bank-noop-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1_098L, 0L, 0L);
            long region = 42L;
            long anchor = pack(0, 64, 0);
            require(service.markGeneratedBankRegion(region, anchor),
                    "Could not register generated bank region");
            EconomyService.VillageSnapshot village = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld", anchor, region, anchor,
                            4, 6, 0, false, List.of()));
            require(service.associateBankRegionWithVillage(
                            region, village.village().villageId, anchor),
                    "Could not create bank-village association");
            Path save = root.resolve("the_emerald_standard.properties");
            FileTime sentinel = FileTime.fromMillis(1_234L);
            Files.setLastModifiedTime(save, sentinel);
            require(service.associateBankRegionWithVillage(
                            region, village.village().villageId, anchor),
                    "Unchanged bank-village association was rejected");
            require(Files.getLastModifiedTime(save).equals(sentinel),
                    "Unchanged bank-village association rewrote the entire save");
        } finally { deleteTree(root); }
    }

    private static void testBankReplacementUsesPersistedAssociation() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-bank-routing-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1_154L, 0L, 0L);
            long abandonedPosition = pack(0, 64, 0);
            long activePosition = pack(80, 64, 0);
            UUID abandoned = service.observeVillage(new EconomyService.VillageObservation(
                            "minecraft:overworld", abandonedPosition, 0L, 0L,
                            0, 4, 0, false, List.of()))
                    .village().villageId;
            UUID active = service.observeVillage(new EconomyService.VillageObservation(
                            "minecraft:overworld", activePosition, 0L, 0L,
                            4, 6, 0, false, List.of()))
                    .village().villageId;

            require(service.markGeneratedBankRegion(11L, activePosition)
                            && service.associateBankRegionWithVillage(
                                    11L, abandoned, activePosition),
                    "Could not route a test bank to its abandoned village");
            require(!service.allowBankerReplacementForRegion(11L, activePosition),
                    "Banker replacement used a nearer active village instead of its association");

            require(service.markGeneratedBankRegion(12L, abandonedPosition)
                            && service.associateBankRegionWithVillage(
                                    12L, active, abandonedPosition),
                    "Could not route a test bank to its active village");
            require(service.allowBankerReplacementForRegion(12L, abandonedPosition),
                    "Banker replacement used a nearer abandoned village instead of its association");
        } finally { deleteTree(root); }
    }

    private static void testNetWorthCannotOverflowLongAddition() {
        EconomyState state = EconomyState.fresh(1_209L, 0L, 0L);
        UUID player = UUID.randomUUID();
        EconomyState.Account account = state.account(player);
        account.cashMicro = Long.MAX_VALUE;
        account.savingsMicro = Long.MAX_VALUE;
        double expected = 2.0 * Long.MAX_VALUE / EconomyState.MICRO;
        double actual = state.netWorth(player);
        require(Double.isFinite(actual) && actual > 0.0,
                "Net worth overflowed valid long balances");
        require(Math.abs(actual - expected) <= Math.ulp(expected) * 2.0,
                "Net worth lost a balance while avoiding overflow");
    }

    private static void testEpsilonOversellIsRejected() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-oversell-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1_320L, 0L, 0L);
            UUID player = UUID.randomUUID();
            require(service.deposit(player, 20L) && service.buy(player, "VILX", 10L),
                    "Could not create holding for oversell regression");
            EconomyService.PortfolioSnapshot before = service.portfolioSnapshot(player);
            double held = before.account().shares.get("VILX");
            require(!service.sell(player, "VILX", held + 5.0e-10),
                    "Epsilon oversell was accepted");
            EconomyService.PortfolioSnapshot after = service.portfolioSnapshot(player);
            require(Double.doubleToLongBits(after.account().shares.get("VILX"))
                            == Double.doubleToLongBits(held)
                            && after.account().cashMicro == before.account().cashMicro,
                    "Rejected oversell mutated the account");
            require(service.sell(player, "VILX", held),
                    "Exact full-position sale was rejected");
            require(!service.portfolioSnapshot(player).account().shares.containsKey("VILX"),
                    "Exact full-position sale left a dust holding");
        } finally { deleteTree(root); }
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

    private static EconomyState.VillageRecord marketVillage(
            EconomyState state, UUID id, long center, int population, int housing) {
        EconomyState.VillageRecord village = state.village(id);
        village.dimensionKey = "minecraft:overworld";
        village.centerPos = center;
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
        village.lifecycle = VillageProsperityEngine.Lifecycle.ACTIVE;
        return village;
    }

    private static void addActiveResident(
            EconomyState.VillageRecord village, UUID id, String profession, long position) {
        EconomyState.ResidentRecord resident = new EconomyState.ResidentRecord();
        resident.residentId = id;
        resident.profession = profession;
        resident.status = VillageProsperityEngine.ResidentStatus.ACTIVE;
        resident.lastSeenDay = village.lastCensusDay;
        resident.lastKnownPos = position;
        village.residents.put(id, resident);
    }

    private static void requireSameFundamentals(
            VillageProsperityEngine.VillageFundamentals expected,
            VillageProsperityEngine.VillageFundamentals actual,
            String message) {
        require(sameFundamentalsBits(expected, actual), message);
    }

    private static boolean sameFundamentalsBits(
            VillageProsperityEngine.VillageFundamentals first,
            VillageProsperityEngine.VillageFundamentals second) {
        return first != null
                && second != null
                && first.eligibleVillages() == second.eligibleVillages()
                && Double.doubleToLongBits(first.broad()) == Double.doubleToLongBits(second.broad())
                && Double.doubleToLongBits(first.mining()) == Double.doubleToLongBits(second.mining())
                && Double.doubleToLongBits(first.agriculture())
                        == Double.doubleToLongBits(second.agriculture())
                && Double.doubleToLongBits(first.trade()) == Double.doubleToLongBits(second.trade())
                && Double.doubleToLongBits(first.redstone())
                        == Double.doubleToLongBits(second.redstone())
                && Double.doubleToLongBits(first.alchemy())
                        == Double.doubleToLongBits(second.alchemy())
                && Double.doubleToLongBits(first.transport())
                        == Double.doubleToLongBits(second.transport())
                && Double.doubleToLongBits(first.security())
                        == Double.doubleToLongBits(second.security());
    }

    private static VillageProsperityEngine.VillageFundamentals singleVillageFundamentals(
            EconomyState state, UUID villageId) {
        return VillageProsperityEngine.aggregateFundamentals(
                List.of(state.existingVillage(villageId)),
                state.villageMarketShadows,
                state.economicDay);
    }

    private static boolean sameShadowContributionBits(
            EconomyState.VillageMarketShadow first,
            EconomyState.VillageMarketShadow second) {
        return first != null
                && second != null
                && first.contributionEligible == second.contributionEligible
                && first.formulaVersion == second.formulaVersion
                && Double.doubleToLongBits(first.weight) == Double.doubleToLongBits(second.weight)
                && Double.doubleToLongBits(first.broad) == Double.doubleToLongBits(second.broad)
                && Double.doubleToLongBits(first.mining) == Double.doubleToLongBits(second.mining)
                && Double.doubleToLongBits(first.agriculture)
                        == Double.doubleToLongBits(second.agriculture)
                && Double.doubleToLongBits(first.trade) == Double.doubleToLongBits(second.trade)
                && Double.doubleToLongBits(first.redstone)
                        == Double.doubleToLongBits(second.redstone)
                && Double.doubleToLongBits(first.alchemy) == Double.doubleToLongBits(second.alchemy)
                && Double.doubleToLongBits(first.transport)
                        == Double.doubleToLongBits(second.transport)
                && Double.doubleToLongBits(first.security)
                        == Double.doubleToLongBits(second.security);
    }

    private static void requireSameShadow(
            EconomyState.VillageMarketShadow expected,
            EconomyState.VillageMarketShadow actual,
            String message) {
        require(expected != null
                        && actual != null
                        && expected.present == actual.present
                        && expected.contributionEligible == actual.contributionEligible
                        && expected.formulaVersion == actual.formulaVersion
                        && expected.capturedDay == actual.capturedDay
                        && expected.minimumReleaseDay == actual.minimumReleaseDay
                        && expected.recoveryPopulation == actual.recoveryPopulation
                        && Double.doubleToLongBits(expected.weight)
                                == Double.doubleToLongBits(actual.weight)
                        && Double.doubleToLongBits(expected.broad)
                                == Double.doubleToLongBits(actual.broad)
                        && Double.doubleToLongBits(expected.mining)
                                == Double.doubleToLongBits(actual.mining)
                        && Double.doubleToLongBits(expected.agriculture)
                                == Double.doubleToLongBits(actual.agriculture)
                        && Double.doubleToLongBits(expected.trade)
                                == Double.doubleToLongBits(actual.trade)
                        && Double.doubleToLongBits(expected.redstone)
                                == Double.doubleToLongBits(actual.redstone)
                        && Double.doubleToLongBits(expected.alchemy)
                                == Double.doubleToLongBits(actual.alchemy)
                        && Double.doubleToLongBits(expected.transport)
                                == Double.doubleToLongBits(actual.transport)
                        && Double.doubleToLongBits(expected.security)
                                == Double.doubleToLongBits(actual.security)
                        && sameCounterfactualVillage(
                                expected.counterfactualVillage,
                                actual.counterfactualVillage),
                message);
    }

    private static boolean sameCounterfactualVillage(
            EconomyState.VillageRecord expected,
            EconomyState.VillageRecord actual) {
        if (expected == null
                || actual == null
                || expected == actual
                || !java.util.Objects.equals(expected.villageId, actual.villageId)
                || !java.util.Objects.equals(expected.dimensionKey, actual.dimensionKey)
                || expected.centerPos != actual.centerPos
                || expected.lastSimulatedDay != actual.lastSimulatedDay
                || expected.lastIncidentDay != actual.lastIncidentDay
                || expected.marketSuppressedUntilDay != actual.marketSuppressedUntilDay
                || expected.lifecycle != actual.lifecycle
                || expected.lastIncidentCause != actual.lastIncidentCause
                || expected.population != actual.population
                || expected.observedPopulation != actual.observedPopulation
                || expected.housingCapacity != actual.housingCapacity
                || expected.hostileCasualties != actual.hostileCasualties
                || expected.playerCasualties != actual.playerCasualties
                || expected.environmentalCasualties != actual.environmentalCasualties
                || Double.doubleToLongBits(expected.foodSupply)
                        != Double.doubleToLongBits(actual.foodSupply)
                || Double.doubleToLongBits(expected.materialSupply)
                        != Double.doubleToLongBits(actual.materialSupply)
                || Double.doubleToLongBits(expected.treasury)
                        != Double.doubleToLongBits(actual.treasury)
                || Double.doubleToLongBits(expected.prosperity)
                        != Double.doubleToLongBits(actual.prosperity)
                || Double.doubleToLongBits(expected.safety)
                        != Double.doubleToLongBits(actual.safety)
                || Double.doubleToLongBits(expected.miningOutput)
                        != Double.doubleToLongBits(actual.miningOutput)
                || Double.doubleToLongBits(expected.agricultureOutput)
                        != Double.doubleToLongBits(actual.agricultureOutput)
                || Double.doubleToLongBits(expected.tradeOutput)
                        != Double.doubleToLongBits(actual.tradeOutput)
                || expected.residents.size() != actual.residents.size()) {
            return false;
        }
        for (var entry : expected.residents.entrySet()) {
            EconomyState.ResidentRecord expectedResident = entry.getValue();
            EconomyState.ResidentRecord actualResident = actual.residents.get(entry.getKey());
            if (expectedResident == null
                    || actualResident == null
                    || expectedResident == actualResident
                    || !java.util.Objects.equals(
                            expectedResident.residentId, actualResident.residentId)
                    || !java.util.Objects.equals(
                            expectedResident.profession, actualResident.profession)
                    || expectedResident.status != actualResident.status
                    || expectedResident.lastSeenDay != actualResident.lastSeenDay
                    || expectedResident.lastKnownPos != actualResident.lastKnownPos) {
                return false;
            }
        }
        return true;
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
