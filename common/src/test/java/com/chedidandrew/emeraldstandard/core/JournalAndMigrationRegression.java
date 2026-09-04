package com.chedidandrew.emeraldstandard.core;

import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.PLAYER;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.baseProperties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.readProperties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.refreshChecksum;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.requireValidationFailure;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.writeProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

final class JournalAndMigrationRegression {
    private JournalAndMigrationRegression() {
    }

    static void run(Path root) throws Exception {
        testJournalLifecycle(root.resolve("journal"));
        testJournalValidation();
        testLegacyMigration(root.resolve("legacy"));
        testFormatTwoMigration(root.resolve("format-two"));
        testFormatThreeClockMigration(root.resolve("format-three"));
        testFormatFourMigration(root.resolve("format-four"));
        testFormatFiveMigration(root.resolve("format-five"));
        testLegacyProjectMetadataDefaults(root.resolve("format-six-project-defaults"));
        testFormatSevenProjectCatalogMigration(root.resolve("format-seven-project-catalog"));
        testVillageMarketShadowPersistence(root.resolve("market-shadow"));
        testLegacySuppressedVillageWithoutShadow(
                root.resolve("format-six-suppression-without-shadow"));
        testMarketEventPersistence(root.resolve("market-event"));
        testFutureFormatRejectedWithoutBackup(root.resolve("future"));
        testFutureFormatNeverFallsBack(root.resolve("future-with-backup"));
        testHistoryAndBankRegionPersistence(root.resolve("history-bank"));
    }

    private static void testJournalLifecycle(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 54L, 0L, 0L);
        EconomyState.PendingInventoryTransaction prepared = service.prepareInventoryCredit(
                PLAYER,
                EconomyState.InventoryTransactionKind.DEPOSIT,
                "emerald",
                8,
                20,
                8L * EconomyState.MICRO);
        require(prepared != null, "Deposit journal preparation failed");
        require(service.snapshot().account(PLAYER).cashMicro == 0L,
                "Prepared deposit credited cash too early");
        require(!service.buy(PLAYER, "VILX", 1L),
                "Pending journal did not block account mutation");

        EconomyService reload = new EconomyService();
        reload.startWithSeed(directory, 999L, 0L, 0L);
        EconomyState.PendingInventoryTransaction restored =
                reload.pendingInventoryTransaction(PLAYER);
        require(restored != null
                        && restored.stage == EconomyState.InventoryTransactionStage.PREPARED,
                "Prepared journal did not survive reload");
        require(reload.commitPreparedInventoryCredit(PLAYER, restored.transactionId),
                "Prepared journal could not commit");
        require(reload.snapshot().account(PLAYER).cashMicro == 8L * EconomyState.MICRO,
                "Committed journal did not credit cash");
        require(reload.completeInventoryTransaction(PLAYER, restored.transactionId),
                "Committed journal did not clear");

        EconomyState.PendingInventoryTransaction withdrawal =
                reload.beginInventoryWithdrawal(PLAYER, 5, 3);
        require(withdrawal != null, "Withdrawal journal failed");
        require(reload.snapshot().account(PLAYER).cashMicro == 3L * EconomyState.MICRO,
                "Withdrawal did not debit bank cash");
        require(reload.reducePendingWithdrawal(PLAYER, withdrawal.transactionId, 2),
                "Undelivered withdrawal could not refund");
        require(reload.snapshot().account(PLAYER).cashMicro == 5L * EconomyState.MICRO,
                "Withdrawal refund was not returned to bank cash");
        EconomyState.PendingInventoryTransaction adjusted =
                reload.pendingInventoryTransaction(PLAYER);
        require(adjusted.itemCount == 3 && adjusted.expectedInventoryCount() == 6,
                "Adjusted withdrawal journal is inconsistent");
        require(reload.completeInventoryTransaction(PLAYER, adjusted.transactionId),
                "Withdrawal journal did not clear");
    }

    private static void testJournalValidation() throws Exception {
        EconomyState state = EconomyState.fresh(1L, 0L, 0L);
        EconomyState.PendingInventoryTransaction transaction =
                new EconomyState.PendingInventoryTransaction();
        transaction.transactionId = java.util.UUID.randomUUID();
        transaction.playerId = PLAYER;
        transaction.kind = EconomyState.InventoryTransactionKind.WITHDRAWAL;
        transaction.stage = EconomyState.InventoryTransactionStage.PREPARED;
        transaction.itemKey = "emerald";
        transaction.itemCount = 1;
        transaction.inventoryCountBefore = 0;
        transaction.bankDeltaMicro = -EconomyState.MICRO;
        state.pendingInventoryTransactions.put(PLAYER, transaction);
        requireValidationFailure(state, "Invalid prepared withdrawal passed validation");
    }

    private static void testLegacyMigration(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        Properties properties = baseProperties(1);
        properties.remove("format");
        String prefix = "acct." + PLAYER + ".";
        properties.setProperty(prefix + "cash", Long.toString(7L * EconomyState.MICRO));
        properties.setProperty(prefix + "savings", Long.toString(2L * EconomyState.MICRO));
        properties.setProperty(prefix + "share.VILX", "0.25");
        writeProperties(save, properties);
        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.account(PLAYER).cashMicro == 7L * EconomyState.MICRO,
                "Legacy cash did not migrate");
        require(migrated.account(PLAYER).shares.get("VILX") == 0.25,
                "Legacy shares did not migrate");
        require(migrated.priceHistory.get("VILX").size() == 1,
                "Legacy migration did not seed chart history");
    }

    private static void testFormatTwoMigration(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        Properties properties = baseProperties(2);
        String prefix = "account." + PLAYER + ".";
        properties.setProperty(prefix + "cash", Long.toString(9L * EconomyState.MICRO));
        properties.setProperty(prefix + "savings", "0");
        properties.setProperty(prefix + "cd.principal", "0");
        properties.setProperty(prefix + "cd.value", "0");
        properties.setProperty(prefix + "cd.open", "0");
        properties.setProperty(prefix + "cd.maturity", "0");
        properties.setProperty(prefix + "cd.rate", "0.0");
        properties.setProperty(prefix + "loan.principal", "0");
        properties.setProperty(prefix + "loan.value", "0");
        properties.setProperty(prefix + "loan.open", "0");
        properties.setProperty(prefix + "loan.maturity", "0");
        properties.setProperty(prefix + "loan.serial", "0");
        properties.setProperty(prefix + "loan.rate", "0.0");
        properties.setProperty(prefix + "loan.stress", "0.0");
        properties.setProperty(prefix + "loan.recovery", "1.0");
        properties.setProperty(prefix + "loan.resolved", "false");
        properties.setProperty(prefix + "loan.outcome", "REPAID");
        writeProperties(save, properties);
        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.account(PLAYER).cashMicro == 9L * EconomyState.MICRO,
                "Format 2 cash did not migrate");
        require(migrated.pendingInventoryTransactions.isEmpty()
                        && migrated.pendingEconomicMillis == 0L,
                "Format 2 migration invented newer state");
    }

    private static void testFormatThreeClockMigration(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        Properties properties = baseProperties(3);
        properties.setProperty(
                "pending.wall_ms",
                Long.toString(EconomyService.MILLIS_PER_MINECRAFT_DAY / 2L));
        properties.setProperty("pending.game_ticks", "6000");
        writeProperties(save, properties);
        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.pendingEconomicMillis
                        == EconomyService.MILLIS_PER_MINECRAFT_DAY / 2L,
                "Format 3 clock migration summed overlapping clocks");
    }

    private static void testFormatFourMigration(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 88L, 0L, 0L);
        require(service.deposit(PLAYER, 12L), "Format 4 fixture deposit failed");

        Path save = directory.resolve("the_emerald_standard.properties");
        Properties properties = readProperties(save);
        properties.setProperty("format", "4");
        properties.remove("event");
        properties.remove("event.day");
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("bank.anchor."))
                .toList()
                .forEach(properties::remove);
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.account(PLAYER).cashMicro == 12L * EconomyState.MICRO,
                "Format 4 account did not migrate");
        require(migrated.lastMarketEvent == EconomyEngine.MarketEvent.NONE
                        && migrated.lastMarketEventDay == 0L
                        && migrated.generatedBankAnchors.isEmpty(),
                "Format 4 migration invented format 5 state");
    }

    private static void testFormatFiveMigration(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(89L, 0L, 0L);
        state.account(PLAYER).cashMicro = 17L * EconomyState.MICRO;
        long region = 0x1122334455667788L;
        long anchor = 0x0102030405060708L;
        state.generatedBankRegions.add(region);
        state.generatedBankAnchors.put(region, anchor);
        state.save(save);

        Properties properties = readProperties(save);
        properties.setProperty("format", "5");
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("village.")
                        || key.startsWith("bank.village.")
                        || key.startsWith("market.shadow."))
                .toList()
                .forEach(properties::remove);
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.account(PLAYER).cashMicro == 17L * EconomyState.MICRO,
                "Format 5 account did not migrate");
        require(migrated.generatedBankRegions.contains(region)
                        && Long.valueOf(anchor).equals(migrated.generatedBankAnchors.get(region)),
                "Format 5 bank marker or anchor did not migrate");
        require(migrated.villages.isEmpty() && migrated.bankRegionVillageIds.isEmpty(),
                "Format 5 migration invented format 6 village state");
    }

    private static void testLegacyProjectMetadataDefaults(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(90L, 0L, 0L);
        state.economicDay = 8L;
        UUID villageId = UUID.fromString("58be3daf-6180-4e2b-bcca-f5dd03ef252c");
        EconomyState.VillageRecord village = state.village(villageId);
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = 1L;
        project.approvedDay = 4L;
        project.completedDay = 8L;
        project.economicProgress = 1.0;
        project.economicComplete = true;
        project.originPos = packBlockPos(10, 64, 20);
        project.boundsMinPos = packBlockPos(9, 64, 19);
        project.boundsMaxPos = packBlockPos(21, 70, 31);
        project.retryAfterGameTick = 12_345L;
        project.materializationFailures = 3;
        project.blocked = true;
        project.totalBlocks = project.type.nominalBlocks();
        village.projects.add(project);
        village.projectSerial = 1L;
        state.save(save);

        Properties properties = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION)
                        .equals(properties.getProperty("format")),
                "Project migration fixture was not written in the current format");
        properties.setProperty("format", "6");
        String projectPrefix = "village." + villageId + ".project.1.";
        properties.remove(projectPrefix + "bounds_min");
        properties.remove(projectPrefix + "bounds_max");
        properties.remove(projectPrefix + "retry_after_tick");
        properties.remove(projectPrefix + "materialization_failures");
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        EconomyState.VillageProject migratedProject =
                migrated.existingVillage(villageId).projects.getFirst();
        require(migratedProject.boundsMinPos == 0L
                        && migratedProject.boundsMaxPos == 0L
                        && migratedProject.retryAfterGameTick == 0L
                        && migratedProject.materializationFailures == 0,
                "Legacy format 6 project metadata did not default safely");
        require(migrated.existingVillage(villageId).nextVisualProject(0L) == migratedProject,
                "A legacy blocked project remained permanently ineligible after migration");
    }

    private static void testFormatSevenProjectCatalogMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(901L, 0L, 0L);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000009501");
        EconomyState.VillageRecord village = state.village(villageId);
        EconomyState.VillageProject cottage = new EconomyState.VillageProject();
        cottage.projectId = 1L;
        cottage.type = VillageProsperityEngine.ProjectType.COTTAGE;
        cottage.approvedDay = 0L;
        cottage.totalBlocks = cottage.type.nominalBlocks();
        village.projects.add(cottage);
        village.projectSerial = 1L;
        state.save(save);

        Properties formatSeven = readProperties(save);
        formatSeven.setProperty("format", "7");
        refreshChecksum(formatSeven);
        writeProperties(save, formatSeven);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.existingVillage(villageId) != null
                        && migrated.existingVillage(villageId).projects.size() == 1
                        && migrated.existingVillage(villageId).projects.getFirst().type
                                == VillageProsperityEngine.ProjectType.COTTAGE,
                "Format 7 project catalog did not load into format 8");

        EconomyState.VillageProject house = new EconomyState.VillageProject();
        house.projectId = 2L;
        house.type = VillageProsperityEngine.ProjectType.HOUSE;
        house.approvedDay = 0L;
        house.totalBlocks = house.type.nominalBlocks();
        migrated.existingVillage(villageId).projects.add(house);
        migrated.existingVillage(villageId).projectSerial = 2L;
        migrated.save(save);

        Properties upgraded = readProperties(save);
        require("8".equals(upgraded.getProperty("format")),
                "Format 7 save did not upgrade to format 8");
        EconomyState reloaded = EconomyState.load(save, 999L, 0L, 0L);
        require(reloaded.existingVillage(villageId).projects.stream()
                        .anyMatch(project -> project.type
                                == VillageProsperityEngine.ProjectType.HOUSE),
                "Expanded project identifier did not survive format 8 reload");
    }

    private static void testVillageMarketShadowPersistence(Path directory) throws Exception {
        UUID resident = UUID.fromString("00000000-0000-0000-0000-000000003301");
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 92L, 0L, 0L);
        EconomyService.VillageSnapshot observed = service.observeVillage(
                new EconomyService.VillageObservation(
                        "minecraft:overworld",
                        packBlockPos(30, 64, 30),
                        0L,
                        0L,
                        6,
                        8,
                        0,
                        false,
                        java.util.List.of(new EconomyService.ResidentObservation(
                                resident, "minecraft:farmer", packBlockPos(31, 64, 30)))));
        UUID villageId = observed.village().villageId;
        require(service.recordVillagerDeath(
                        villageId,
                        resident,
                        "minecraft:farmer",
                        packBlockPos(31, 64, 30),
                        VillageProsperityEngine.IncidentCause.PLAYER,
                        UUID.fromString("00000000-0000-0000-0000-000000003399")),
                "Could not create persistent player-casualty market shadow");

        EconomyState beforeReload = service.snapshot();
        EconomyState.VillageMarketShadow expected =
                beforeReload.villageMarketShadows.get(villageId);
        EconomyState.ResidentRecord liveResident =
                beforeReload.existingVillage(villageId).residents.get(resident);
        EconomyState.ResidentRecord counterfactualResident =
                expected == null || expected.counterfactualVillage == null
                        ? null
                        : expected.counterfactualVillage.residents.get(resident);
        require(expected != null
                        && expected.present
                        && expected.contributionEligible
                        && expected.formulaVersion
                                == VillageProsperityEngine.MARKET_SHADOW_FORMULA_VERSION
                        && liveResident != null
                        && liveResident.status == VillageProsperityEngine.ResidentStatus.DEAD
                        && counterfactualResident != null
                        && counterfactualResident.status
                                == VillageProsperityEngine.ResidentStatus.ACTIVE,
                "Player casualty did not create a market shadow before reload");

        Path save = directory.resolve("the_emerald_standard.properties");
        Properties savedProperties = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION)
                        .equals(savedProperties.getProperty("format")),
                "Market-shadow save was not written in the current format");
        EconomyState loaded = EconomyState.load(save, 999L, 0L, 0L);
        EconomyState.VillageMarketShadow actual = loaded.villageMarketShadows.get(villageId);
        requireSameShadow(expected, actual,
                "Village market shadow did not survive save/reload exactly");
        requireSameFundamentals(
                beforeReload.villageFundamentals(),
                loaded.villageFundamentals(),
                "Reloaded market shadow changed aggregate fundamentals");
    }

    private static void testLegacySuppressedVillageWithoutShadow(Path directory)
            throws Exception {
        UUID resident = UUID.fromString("00000000-0000-0000-0000-000000004401");
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 93L, 0L, 0L);
        EconomyService.VillageSnapshot observed = service.observeVillage(
                new EconomyService.VillageObservation(
                        "minecraft:overworld",
                        packBlockPos(40, 64, 40),
                        0L,
                        0L,
                        5,
                        7,
                        0,
                        false,
                        java.util.List.of(new EconomyService.ResidentObservation(
                                resident, "minecraft:toolsmith", packBlockPos(41, 64, 40)))));
        UUID villageId = observed.village().villageId;
        require(service.recordVillagerDeath(
                        villageId,
                        resident,
                        "minecraft:toolsmith",
                        packBlockPos(41, 64, 40),
                        VillageProsperityEngine.IncidentCause.PLAYER,
                        UUID.fromString("00000000-0000-0000-0000-000000004499")),
                "Could not create legacy suppression fixture");

        Path save = directory.resolve("the_emerald_standard.properties");
        Properties properties = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION)
                        .equals(properties.getProperty("format")),
                "Suppression migration fixture was not written in the current format");
        properties.setProperty("format", "6");
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("market.shadow."))
                .toList()
                .forEach(properties::remove);
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState legacy = EconomyState.load(save, 999L, 0L, 0L);
        EconomyState.VillageRecord suppressed = legacy.existingVillage(villageId);
        require(legacy.villageMarketShadows.isEmpty(),
                "Legacy format-6 suppression invented a market shadow");
        require(suppressed != null
                        && suppressed.playerCasualties == 1
                        && suppressed.marketSuppressedUntilDay == 60L,
                "Legacy format-6 fixture lost its persisted player suppression");
        long expiryDay = suppressed.marketSuppressedUntilDay;
        VillageProsperityEngine.VillageFundamentals beforeExpiry =
                VillageProsperityEngine.aggregateFundamentals(
                        legacy.villages.values(), legacy.villageMarketShadows, expiryDay - 1L);
        VillageProsperityEngine.VillageFundamentals atExpiry =
                VillageProsperityEngine.aggregateFundamentals(
                        legacy.villages.values(), legacy.villageMarketShadows, expiryDay);
        require(beforeExpiry.eligibleVillages() == 0,
                "Legacy suppressed village influenced fundamentals before expiry");
        require(atExpiry.eligibleVillages() == 1,
                "Legacy suppressed village remained excluded after expiry");
    }

    private static void testMarketEventPersistence(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(91L, 0L, 0L);
        state.economicDay = 42L;
        state.lastMarketEvent = EconomyEngine.MarketEvent.NETHER_SUPPLY_CRISIS;
        state.lastMarketEventDay = 40L;
        state.save(save);

        EconomyState loaded = EconomyState.load(save, 999L, 0L, 0L);
        require(loaded.lastMarketEvent == EconomyEngine.MarketEvent.NETHER_SUPPLY_CRISIS
                        && loaded.lastMarketEventDay == 40L,
                "Market event did not survive format 5 persistence");
    }

    private static void testFutureFormatRejectedWithoutBackup(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        Properties properties = baseProperties(EconomyState.FORMAT_VERSION + 1);
        writeProperties(save, properties);
        boolean rejected = false;
        try {
            EconomyState.load(save, 1L, 0L, 0L);
        } catch (IOException expected) {
            rejected = expected.getMessage().contains("newer than supported");
        }
        require(rejected, "Future save format was interpreted as current data");
    }

    private static void testFutureFormatNeverFallsBack(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 81L, 0L, 0L);
        require(service.deposit(PLAYER, 10L), "Future-format baseline deposit failed");
        require(service.deposit(PLAYER, 5L), "Future-format backup creation failed");
        Path save = directory.resolve("the_emerald_standard.properties");
        Properties future = readProperties(save);
        future.setProperty("format", Integer.toString(EconomyState.FORMAT_VERSION + 1));
        writeProperties(save, future);

        boolean rejected = false;
        try {
            EconomyState.load(save, 999L, 0L, 0L);
        } catch (IOException expected) {
            rejected = expected.getMessage().contains("newer than supported");
        }
        require(rejected, "Future primary silently fell back to a stale backup");
        require(readProperties(save).getProperty("format")
                        .equals(Integer.toString(EconomyState.FORMAT_VERSION + 1)),
                "Future primary was modified after rejection");
    }

    private static void testHistoryAndBankRegionPersistence(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 82L, 0L, 0L);
        for (int day = 1; day <= 25; day++) {
            require(service.tickAt(
                            day * EconomyService.TICKS_PER_MINECRAFT_DAY,
                            day * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                    "History advance failed");
        }
        long region = 0x12345678ABCDEF01L;
        long anchor = 0x1020304050607080L;
        require(service.markGeneratedBankRegion(region, anchor), "Bank region marker failed");
        require(service.saveNowAt(
                        25L * EconomyService.TICKS_PER_MINECRAFT_DAY,
                        25L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "History save failed");

        EconomyService reload = new EconomyService();
        reload.startWithSeed(
                directory,
                999L,
                25L * EconomyService.MILLIS_PER_MINECRAFT_DAY,
                25L * EconomyService.TICKS_PER_MINECRAFT_DAY);
        require(reload.marketSnapshot().priceHistory().get("VILX").size() == 26,
                "Chart history did not survive reload");
        require(reload.hasGeneratedBankRegion(region),
                "Generated bank region did not survive reload");
        require(Long.valueOf(anchor).equals(reload.generatedBankAnchor(region)),
                "Generated bank anchor did not survive reload");
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
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
                || expected.discoveredDay != actual.discoveredDay
                || expected.lastSimulatedDay != actual.lastSimulatedDay
                || expected.lastCensusDay != actual.lastCensusDay
                || expected.lastIncidentDay != actual.lastIncidentDay
                || expected.marketSuppressedUntilDay != actual.marketSuppressedUntilDay
                || expected.lifecycle != actual.lifecycle
                || expected.lastIncidentCause != actual.lastIncidentCause
                || expected.population != actual.population
                || expected.observedPopulation != actual.observedPopulation
                || expected.housingCapacity != actual.housingCapacity
                || expected.developmentTier != actual.developmentTier
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
                || Double.doubleToLongBits(expected.agricultureOutput)
                        != Double.doubleToLongBits(actual.agricultureOutput)
                || Double.doubleToLongBits(expected.miningOutput)
                        != Double.doubleToLongBits(actual.miningOutput)
                || Double.doubleToLongBits(expected.tradeOutput)
                        != Double.doubleToLongBits(actual.tradeOutput)
                || Double.doubleToLongBits(expected.redstoneOutput)
                        != Double.doubleToLongBits(actual.redstoneOutput)
                || Double.doubleToLongBits(expected.alchemyOutput)
                        != Double.doubleToLongBits(actual.alchemyOutput)
                || Double.doubleToLongBits(expected.transportOutput)
                        != Double.doubleToLongBits(actual.transportOutput)
                || Double.doubleToLongBits(expected.securityOutput)
                        != Double.doubleToLongBits(actual.securityOutput)
                || expected.residents.size() != actual.residents.size()
                || expected.projects.size() != actual.projects.size()
                || expected.incidents.size() != actual.incidents.size()) {
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

    private static void requireSameFundamentals(
            VillageProsperityEngine.VillageFundamentals expected,
            VillageProsperityEngine.VillageFundamentals actual,
            String message) {
        require(expected != null
                        && actual != null
                        && expected.eligibleVillages() == actual.eligibleVillages()
                        && Double.doubleToLongBits(expected.broad())
                                == Double.doubleToLongBits(actual.broad())
                        && Double.doubleToLongBits(expected.mining())
                                == Double.doubleToLongBits(actual.mining())
                        && Double.doubleToLongBits(expected.agriculture())
                                == Double.doubleToLongBits(actual.agriculture())
                        && Double.doubleToLongBits(expected.trade())
                                == Double.doubleToLongBits(actual.trade())
                        && Double.doubleToLongBits(expected.redstone())
                                == Double.doubleToLongBits(actual.redstone())
                        && Double.doubleToLongBits(expected.alchemy())
                                == Double.doubleToLongBits(actual.alchemy())
                        && Double.doubleToLongBits(expected.transport())
                                == Double.doubleToLongBits(actual.transport())
                        && Double.doubleToLongBits(expected.security())
                                == Double.doubleToLongBits(actual.security()),
                message);
    }
}
