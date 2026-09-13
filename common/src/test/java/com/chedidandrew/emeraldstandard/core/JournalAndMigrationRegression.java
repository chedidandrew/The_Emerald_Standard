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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class JournalAndMigrationRegression {
    private JournalAndMigrationRegression() {
    }

    static void run(Path root) throws Exception {
        testJournalLifecycle(root.resolve("journal"));
        testVerifiedPlayerSaveDurablyClearsJournal(root.resolve("verified-journal-cleanup"));
        testVillageFundRespectsPendingJournal(root.resolve("fund-journal"));
        testJournalValidation();
        testLegacyMigration(root.resolve("legacy"));
        testFormatTwoMigration(root.resolve("format-two"));
        testFormatThreeClockMigration(root.resolve("format-three"));
        testFormatFourMigration(root.resolve("format-four"));
        testFormatFiveMigration(root.resolve("format-five"));
        testLegacyProjectMetadataDefaults(root.resolve("format-six-project-defaults"));
        testFormatNineArchitectureMigration(root.resolve("format-nine-architecture"));
        testFormatTenFallbackMigration(root.resolve("format-ten-bank-fallback"));
        testFormatElevenBankStructureMigration(
                root.resolve("format-eleven-bank-structure"));
        testFormatTwelveRelocationMigration(
                root.resolve("format-twelve-relocation"));
        testFormatSevenProjectCatalogMigration(root.resolve("format-seven-project-catalog"));
        testVillageMarketShadowPersistence(root.resolve("market-shadow"));
        testLegacySuppressedVillageWithoutShadow(
                root.resolve("format-six-suppression-without-shadow"));
        testMarketEventPersistence(root.resolve("market-event"));
        testFutureFormatRejectedWithoutBackup(root.resolve("future"));
        testFutureFormatNeverFallsBack(root.resolve("future-with-backup"));
        testHistoryAndBankRegionPersistence(root.resolve("history-bank"));
        testRetiredBankAnchorHistoryCap(root.resolve("retired-bank-cap"));
        testCanonicalBankerPersistenceAndInvariants(root.resolve("canonical-banker"));
        testDurableBankerDeathReplacement(root.resolve("banker-death-replacement"));
        testBankerDeathSaveFailureRecovery(root.resolve("banker-death-save-recovery"));
        testLegacyBankerDeathMigration(root.resolve("legacy-banker-death"));
        testBankerDeathValidation();
        testFormatThirteenBankerAssignmentInference(
                root.resolve("format-thirteen-banker-assignment"));
        testFormatThirteenTrailCenterSurfaceMigration(
                root.resolve("format-thirteen-trail-center-surface"));
        testFormatFourteenEntranceApproachMigration(
                root.resolve("format-fourteen-entrance-approach"));
        testVillageProjectLotExclusions(root.resolve("village-project-lot-exclusions"));
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
        EconomyState.Account afterDeposit = reload.snapshot().account(PLAYER);
        require(afterDeposit.cashMicro == 8L * EconomyState.MICRO,
                "Committed journal did not credit cash");
        EconomyState.PortfolioTransaction depositEntry = afterDeposit.transactionLedger.getLast();
        require(depositEntry.kind == EconomyState.PortfolioTransactionKind.CASH_IN
                        && depositEntry.symbol.equals("DEPOSIT")
                        && depositEntry.quantity == 8.0
                        && depositEntry.amountMicro == 8L * EconomyState.MICRO,
                "Deposit Activity lost its journal subtype, count, or amount");
        int ledgerSizeAfterDeposit = afterDeposit.transactionLedger.size();
        require(!reload.commitPreparedInventoryCredit(PLAYER, restored.transactionId)
                        && reload.snapshot().account(PLAYER).transactionLedger.size()
                                == ledgerSizeAfterDeposit,
                "A duplicate journal commit added duplicate Activity");
        require(reload.completeInventoryTransaction(PLAYER, restored.transactionId),
                "Committed journal did not clear");

        require(InventoryDeliveryAccounting.undelivered(64, 0, 0) == 64,
                "A full creative inventory treated a consumed temporary stack as delivery");
        require(InventoryDeliveryAccounting.observedInserted(32, 5, 15) == 10
                        && InventoryDeliveryAccounting.undelivered(32, 5, 15) == 22,
                "A partially full inventory did not retain its undelivered remainder");

        EconomyState.PendingInventoryTransaction withdrawal =
                reload.beginInventoryWithdrawal(PLAYER, 5, 3);
        require(withdrawal != null, "Withdrawal journal failed");
        require(reload.snapshot().account(PLAYER).cashMicro == 3L * EconomyState.MICRO,
                "Withdrawal did not debit bank cash");
        require(reload.reducePendingWithdrawal(PLAYER, withdrawal.transactionId, 2),
                "Undelivered withdrawal could not refund");
        require(reload.snapshot().account(PLAYER).cashMicro == 5L * EconomyState.MICRO,
                "Withdrawal refund was not returned to bank cash");
        require(reload.snapshot().account(PLAYER).totalWithdrawalsMicro
                        == 3L * EconomyState.MICRO,
                "Partial withdrawal Activity retained the undelivered amount in its total");
        require(reload.snapshot().account(PLAYER).transactionLedger.stream().anyMatch(
                        entry -> entry.kind == EconomyState.PortfolioTransactionKind.CASH_IN
                                && entry.symbol.equals("WITHDRAWAL_REFUND")),
                "Withdrawal refund lost its Activity subtype");
        EconomyState.PendingInventoryTransaction adjusted =
                reload.pendingInventoryTransaction(PLAYER);
        require(adjusted.itemCount == 3 && adjusted.expectedInventoryCount() == 6,
                "Adjusted withdrawal journal is inconsistent");
        require(reload.completeInventoryTransaction(PLAYER, adjusted.transactionId),
                "Withdrawal journal did not clear");

        EconomyState beforeFullInventoryWithdrawal = reload.snapshot();
        long netWorthBeforeFullInventoryWithdrawal = PortfolioAnalytics.netWorthMicro(
                beforeFullInventoryWithdrawal.account(PLAYER),
                beforeFullInventoryWithdrawal);
        long withdrawalsBeforeFullInventoryWithdrawal = beforeFullInventoryWithdrawal
                .account(PLAYER).totalWithdrawalsMicro;
        EconomyState.PendingInventoryTransaction fullInventoryWithdrawal =
                reload.beginInventoryWithdrawal(PLAYER, 5, 0);
        require(fullInventoryWithdrawal != null,
                "Full-inventory withdrawal journal could not start");
        int fullInventoryRemainder = InventoryDeliveryAccounting.undelivered(5, 0, 0);
        require(reload.reducePendingWithdrawal(
                        PLAYER, fullInventoryWithdrawal.transactionId, fullInventoryRemainder),
                "A completely undelivered withdrawal could not be refunded");
        EconomyState afterFullInventoryWithdrawal = reload.snapshot();
        require(afterFullInventoryWithdrawal.account(PLAYER).cashMicro
                                == beforeFullInventoryWithdrawal.account(PLAYER).cashMicro
                        && PortfolioAnalytics.netWorthMicro(
                                        afterFullInventoryWithdrawal.account(PLAYER),
                                        afterFullInventoryWithdrawal)
                                == netWorthBeforeFullInventoryWithdrawal,
                "A full inventory destroyed withdrawn Bank Cash or net worth");
        require(afterFullInventoryWithdrawal.account(PLAYER).totalWithdrawalsMicro
                                == withdrawalsBeforeFullInventoryWithdrawal
                        && reload.pendingInventoryTransaction(PLAYER) == null,
                "A fully refunded withdrawal left false Activity totals or a stale journal");

        EconomyState.PendingInventoryTransaction exchange = reload.prepareInventoryCredit(
                PLAYER,
                EconomyState.InventoryTransactionKind.EXCHANGE,
                "gold_ingot",
                3,
                7,
                6L * EconomyState.MICRO);
        require(exchange != null
                        && reload.commitPreparedInventoryCredit(PLAYER, exchange.transactionId),
                "Exchange journal could not commit");
        EconomyState.PortfolioTransaction exchangeEntry = reload.snapshot()
                .account(PLAYER).transactionLedger.getLast();
        require(exchangeEntry.kind == EconomyState.PortfolioTransactionKind.CASH_IN
                        && exchangeEntry.symbol.equals("EXCHANGE:gold_ingot")
                        && exchangeEntry.quantity == 3.0,
                "Exchange Activity lost its resource identity or item count");
        require(reload.completeInventoryTransaction(PLAYER, exchange.transactionId),
                "Exchange journal did not clear");
        EconomyService exchangeReload = new EconomyService();
        exchangeReload.startWithSeed(directory, 1000L, 0L, 0L);
        EconomyState.PortfolioTransaction persistedExchange = exchangeReload.snapshot()
                .account(PLAYER).transactionLedger.getLast();
        require(persistedExchange.symbol.equals("EXCHANGE:gold_ingot")
                        && persistedExchange.quantity == 3.0,
                "Exchange Activity metadata did not survive reload");
    }

    private static void testVerifiedPlayerSaveDurablyClearsJournal(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 55L, 0L, 0L);
        EconomyState.PendingInventoryTransaction prepared = service.prepareInventoryCredit(
                PLAYER,
                EconomyState.InventoryTransactionKind.DEPOSIT,
                "emerald",
                4,
                9,
                4L * EconomyState.MICRO);
        require(prepared != null
                        && service.commitPreparedInventoryCredit(
                                PLAYER, prepared.transactionId),
                "Verified-cleanup journal could not reach BANK_COMMITTED");
        require(service.completeInventoryTransactionAfterVerifiedPlayerSave(
                        PLAYER, prepared.transactionId),
                "Verified player save could not durably clear the journal");
        require(service.pendingInventoryTransaction(PLAYER) == null,
                "Durable cleanup kept blocking the live account");

        EconomyService reloaded = new EconomyService();
        reloaded.startWithSeed(directory, 999L, 0L, 0L);
        require(reloaded.pendingInventoryTransaction(PLAYER) == null
                        && reloaded.snapshot().account(PLAYER).cashMicro
                                == 4L * EconomyState.MICRO,
                "Verified journal cleanup did not survive an immediate restart");
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

    private static void testVillageFundRespectsPendingJournal(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 541L, 0L, 0L);
        EconomyService.VillageSnapshot observed = service.observeVillage(
                new EconomyService.VillageObservation(
                        "minecraft:overworld",
                        packBlockPos(8, 64, 8),
                        0L,
                        0L,
                        4,
                        6,
                        0,
                        false,
                        java.util.List.of()));
        require(observed != null, "Village fixture for journal guard failed");
        require(service.deposit(PLAYER, 10L), "Journal-guard donor funding failed");
        EconomyState.PendingInventoryTransaction pending = service.prepareInventoryCredit(
                PLAYER,
                EconomyState.InventoryTransactionKind.EXCHANGE,
                "diamond",
                1,
                1,
                12L * EconomyState.MICRO);
        require(pending != null, "Journal-guard transaction preparation failed");

        long cashBefore = service.snapshot().account(PLAYER).cashMicro;
        EconomyService.VillageFundContributionResult contribution =
                service.contributeToVillageFund(
                        PLAYER,
                        observed.village().villageId,
                        1L,
                        EconomyState.ProsperityFundType.DIRECT_GRANT,
                        EconomyState.DonationPurpose.GENERAL);
        require(!contribution.contributed(),
                "Prosperity Fund bypassed an unresolved inventory journal");
        require(service.snapshot().account(PLAYER).cashMicro == cashBefore
                        && service.villageFundSnapshot(observed.village().villageId)
                                .lifetimeReceivedMicro() == 0L,
                "Rejected journal-blocked contribution changed player or village funds");
        require(service.cancelPreparedInventoryTransaction(PLAYER, pending.transactionId),
                "Journal-guard fixture could not be cleared");
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

    private static void testFormatTenFallbackMigration(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(891L, 0L, 0L);
        long region = 0x5122334455667788L;
        long centerAnchor = 0x0502030405060708L;
        state.generatedBankRegions.add(region);
        state.generatedBankAnchors.put(region, centerAnchor);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000891");
        EconomyState.VillageRecord village = state.village(villageId);
        village.centerPos = centerAnchor;
        village.bankRegionKey = region;
        village.bankAnchorPos = centerAnchor;
        state.bankRegionVillageIds.put(region, villageId);
        state.save(save);

        Properties properties = readProperties(save);
        properties.setProperty("format", "10");
        properties.setProperty(
                "bank.fallback." + Long.toUnsignedString(region, 16), "true");
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.generatedBankRegions.contains(region)
                        && Long.valueOf(centerAnchor).equals(
                                migrated.generatedBankAnchors.get(region))
                        && migrated.villages.get(villageId).centerPos == centerAnchor,
                "Format 10 Bank marker or center anchor did not migrate");
        require(migrated.fallbackBankRegions.isEmpty(),
                "Format 10 center anchor was invented as retryable fallback provenance");
        migrated.save(save);
        Properties upgraded = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                        upgraded.getProperty("format"))
                        && upgraded.getProperty(
                                "bank.fallback." + Long.toUnsignedString(region, 16)) == null,
                "Format 10 ignored fallback data was promoted into format 11");
        require(EconomyState.load(save, 999L, 0L, 0L).fallbackBankRegions.isEmpty(),
                "Rewritten current-format save invented fallback provenance");
    }

    private static void testFormatElevenBankStructureMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(892L, 0L, 0L);
        long region = 0x6122334455667788L;
        long anchor = 0x0602030405060708L;
        state.generatedBankRegions.add(region);
        state.generatedBankAnchors.put(region, anchor);
        state.save(save);

        Properties properties = readProperties(save);
        properties.setProperty("format", "11");
        properties.setProperty(
                "bank.structure_version." + Long.toUnsignedString(region, 16), "7");
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.generatedBankRegions.contains(region)
                        && Long.valueOf(anchor).equals(
                                migrated.generatedBankAnchors.get(region)),
                "Format 11 Bank marker or anchor did not migrate");
        require(migrated.bankStructureVersions.isEmpty(),
                "Format 11 data invented trusted Bank structure provenance");
        migrated.save(save);
        Properties upgraded = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                        upgraded.getProperty("format"))
                        && upgraded.getProperty(
                                "bank.structure_version."
                                        + Long.toUnsignedString(region, 16)) == null,
                "Format 11 untrusted structure data was promoted into format 12");
    }

    private static void testFormatTwelveRelocationMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(893L, 0L, 0L);
        long region = 0x7122334455667788L;
        long anchor = packBlockPos(30, 65, 30);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000001313");
        state.generatedBankRegions.add(region);
        state.generatedBankAnchors.put(region, anchor);
        state.bankStructureVersions.put(region, 2);
        EconomyState.VillageRecord village = state.village(villageId);
        village.observedHousingCapacity = 10;
        village.housingCapacity = 14;
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = 1L;
        project.economicProgress = 1.0;
        project.economicComplete = true;
        project.originPos = packBlockPos(10, 64, 10);
        project.boundsMinPos = packBlockPos(8, 60, 8);
        project.boundsMaxPos = packBlockPos(18, 72, 18);
        project.totalBlocks = project.type.nominalBlocks();
        project.materializedBlocks = project.totalBlocks;
        project.materializedComplete = true;
        village.projectSerial = 1L;
        village.projects.add(project);
        state.save(save);

        Properties properties = readProperties(save);
        properties.setProperty("format", "12");
        String retiredBankKey =
                "bank.retired_anchors." + Long.toUnsignedString(region, 16);
        String canonicalBankerKey =
                "bank.banker." + Long.toUnsignedString(region, 16);
        String canonicalBankerAnchorKey =
                "bank.banker_anchor." + Long.toUnsignedString(region, 16);
        String projectPrefix = "village." + villageId + ".project.1.";
        properties.setProperty("village." + villageId + ".observed_housing", "777");
        properties.setProperty(
                retiredBankKey,
                packBlockPos(90, 65, 90) + "," + packBlockPos(120, 66, 120));
        properties.setProperty(
                canonicalBankerKey,
                "00000000-0000-0000-0000-000000001314");
        properties.setProperty(canonicalBankerAnchorKey, Long.toString(anchor));
        properties.setProperty(
                projectPrefix + "retired_bounds",
                packBlockPos(-30, 60, -30) + ":" + packBlockPos(-20, 72, -20)
                        + ";"
                        + packBlockPos(-60, 61, -60) + ":"
                        + packBlockPos(-50, 73, -50));
        properties.setProperty(projectPrefix + "relocation_pending", "true");
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        EconomyState.VillageProject migratedProject = migrated
                .existingVillage(villageId).projects.getFirst();
        require(migrated.retiredBankAnchors.isEmpty()
                        && migrated.bankRegionBankerIds.isEmpty()
                        && migrated.bankRegionBankerAnchors.isEmpty()
                        && migratedProject.retiredLots.isEmpty()
                        && !migratedProject.relocationPending
                        && migrated.existingVillage(villageId).observedHousingCapacity == 10
                        && VillageProsperityEngine.effectiveHousingCapacity(
                                migrated.existingVillage(villageId)) == 14,
                "Format 12 data invented trusted relocation provenance");
        migrated.save(save);
        Properties upgraded = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                        upgraded.getProperty("format"))
                        && upgraded.getProperty(retiredBankKey) == null
                        && upgraded.getProperty(canonicalBankerKey) == null
                        && upgraded.getProperty(canonicalBankerAnchorKey) == null
                        && upgraded.getProperty(projectPrefix + "retired_bounds") == null
                        && "false".equals(upgraded.getProperty(
                                projectPrefix + "relocation_pending"))
                        && "10".equals(upgraded.getProperty(
                                "village." + villageId + ".observed_housing")),
                "Format 12 untrusted relocation data was promoted into format 13");
    }

    private static void testLegacyProjectMetadataDefaults(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(90L, 0L, 0L);
        state.economicDay = 8L;
        state.liveMarket=LiveMarket.adopt(state);
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
        String villagePrefix = "village." + villageId + ".";
        String projectPrefix = "village." + villageId + ".project.1.";
        properties.remove(villagePrefix + "visual_project_selection_cursor");
        properties.remove(projectPrefix + "bounds_min");
        properties.remove(projectPrefix + "bounds_max");
        properties.remove(projectPrefix + "retry_after_tick");
        properties.remove(projectPrefix + "materialization_failures");
        properties.remove(projectPrefix + "site_search_cursor");
        properties.remove(projectPrefix + "site_search_saw_unloaded");
        refreshChecksum(properties);
        writeProperties(save, properties);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        EconomyState.VillageRecord migratedVillage = migrated.existingVillage(villageId);
        EconomyState.VillageProject migratedProject = migratedVillage.projects.getFirst();
        require(migratedProject.boundsMinPos == 0L
                        && migratedProject.boundsMaxPos == 0L
                        && migratedProject.retryAfterGameTick == 0L
                        && migratedProject.materializationFailures == 0
                        && migratedProject.siteSearchCursor == 0
                        && !migratedProject.siteSearchSawUnloadedCandidate
                        && migratedVillage.visualProjectSelectionCursor == 0L,
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
                "Format 7 project catalog did not load into the current format");

        EconomyState.VillageProject house = new EconomyState.VillageProject();
        house.projectId = 2L;
        house.type = VillageProsperityEngine.ProjectType.HOUSE;
        house.approvedDay = 0L;
        house.totalBlocks = house.type.nominalBlocks();
        migrated.existingVillage(villageId).projects.add(house);
        migrated.existingVillage(villageId).projectSerial = 2L;
        migrated.save(save);

        Properties upgraded = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION)
                        .equals(upgraded.getProperty("format")),
                "Format 7 save did not upgrade to the current format");
        EconomyState reloaded = EconomyState.load(save, 999L, 0L, 0L);
        require(reloaded.existingVillage(villageId).projects.stream()
                        .anyMatch(project -> project.type
                                == VillageProsperityEngine.ProjectType.HOUSE),
                "Expanded project identifier did not survive current-format reload");
    }

    private static void testFormatNineArchitectureMigration(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(910L, 0L, 0L);
        state.economicDay = 9L;
        state.liveMarket=LiveMarket.adopt(state); // Synthetic migration fixture.
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000009910");
        EconomyState.VillageRecord village = state.village(villageId);
        village.architectureCharacter = VillageArchitecture.Character.FORMAL.id();
        village.architectureDialect = VillageArchitecture.BiomeDialect.PLAINS.id();
        for (int index = 0; index < 3; index++) {
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = index + 1L;
            project.type = VillageProsperityEngine.ProjectType.COTTAGE;
            project.approvedDay = 1L;
            project.completedDay = 2L;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            if (index < 2) {
                project.originPos = packBlockPos(20 + index * 20, 64, 20);
                project.boundsMinPos = packBlockPos(19 + index * 20, 62, 19);
                project.boundsMaxPos = packBlockPos(30 + index * 20, 72, 30);
                project.materializedBlocks = index == 0 ? project.totalBlocks : 10;
                project.materializedComplete = index == 0;
            }
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId, project.projectId, project.type, java.util.List.of());
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designSignature = recipe.signature();
            project.designStage = 1;
            project.trailAnchorSet = project.originPos != 0L;
            project.trailAnchorPos = project.originPos == 0L
                    ? 0L
                    : packBlockPos(14, 64, 14);
            project.trailMaterializedBlocks = project.originPos == 0L ? 0 : index == 0 ? 20 : 4;
            project.trailTotalBlocks = project.originPos == 0L ? 0 : 20;
            project.trailMaterializedComplete = project.originPos != 0L && index == 0;
            village.projects.add(project);
        }
        village.projectSerial = 3L;
        state.save(save);

        Properties formatNine = readProperties(save);
        formatNine.setProperty("format", "9");
        formatNine.stringPropertyNames().stream()
                .filter(key -> key.contains(".design.") || key.contains(".architecture."))
                .toList()
                .forEach(formatNine::remove);
        refreshChecksum(formatNine);
        writeProperties(save, formatNine);

        EconomyState migrated = EconomyState.load(save, 0L, 0L, 0L);
        EconomyState.VillageRecord migratedVillage = migrated.existingVillage(villageId);
        require(migratedVillage.architectureCharacter.isBlank()
                        && migratedVillage.architectureDialect.isBlank(),
                "Format 9 migration invented village architectural DNA");
        require(migratedVillage.projects.stream().allMatch(project ->
                        VillageArchitecture.LEGACY_SCHEMA.equals(project.designSchema)
                                && project.designSeed == 0L
                                && project.designRotation == 0
                                && project.designStage == 0
                                && !project.trailAnchorSet
                                && project.trailAnchorPos == 0L
                                && project.trailMaterializedBlocks == 0
                                && project.trailTotalBlocks == 0
                                && !project.trailMaterializedComplete),
                "A format 9 project was silently promoted to the modular generator");
        require(migratedVillage.projects.get(0).materializedComplete
                        && migratedVillage.projects.get(1).materializedBlocks == 10
                        && migratedVillage.projects.get(2).originPos == 0L,
                "Format 9 completed, partial, or unbuilt project state changed during migration");
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
        state.liveMarket=LiveMarket.adopt(state); // Synthetic migration fixture.
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
        require(service.markGeneratedBankRegion(region, anchor, null, 2),
                "Versioned Bank region marker failed");
        long fallbackRegion = 0x22345678ABCDEF01L;
        long fallbackAnchor = 0x2020304050607080L;
        require(service.markFallbackBankRegion(fallbackRegion, fallbackAnchor),
                "Fallback bank marker failed");
        Properties fallbackProperties = readProperties(
                directory.resolve("the_emerald_standard.properties"));
        require("true".equals(fallbackProperties.getProperty(
                        "bank.fallback."
                                + Long.toUnsignedString(fallbackRegion, 16))),
                "Fallback marker was not written with its canonical unsigned key");
        require(fallbackProperties.getProperty(
                        "bank.fallback." + Long.toUnsignedString(region, 16)) == null,
                "Ordinary Bank marker unexpectedly wrote fallback provenance");
        require("2".equals(fallbackProperties.getProperty(
                        "bank.structure_version." + Long.toUnsignedString(region, 16))),
                "Bank structure version was not written with its canonical unsigned key");
        long legacyRegion = 0x32345678ABCDEF01L;
        require(service.markGeneratedBankRegion(legacyRegion),
                "Legacy bank marker fixture failed");
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
        require(reload.generatedBankStructureVersion(region) == 2,
                "Generated Bank structure version did not survive reload");
        require(reload.markGeneratedBankRegion(region, anchor, null, 1)
                        && reload.generatedBankStructureVersion(region) == 2,
                "A repeated Bank marker downgraded its authored structure version");
        long replacementAnchor = packBlockPos(-60, 67, 44);
        require(reload.retireGeneratedBankAnchor(region, anchor),
                "Destroyed authored Bank anchor was not retired");
        require(reload.markGeneratedBankRegion(region, replacementAnchor, null, 4)
                        && reload.retiredBankAnchors(region).equals(List.of(anchor))
                        && Long.valueOf(replacementAnchor).equals(
                                reload.generatedBankAnchor(region))
                        && reload.generatedBankStructureVersion(region) == 4,
                "Bank replacement did not preserve the old exclusion anchor or adopt v4");
        long secondReplacementAnchor = packBlockPos(96, 66, -84);
        require(reload.retireGeneratedBankAnchor(region, replacementAnchor)
                        && reload.markGeneratedBankRegion(
                                region, secondReplacementAnchor, null, 4)
                        && reload.retiredBankAnchors(region).equals(
                                List.of(anchor, replacementAnchor))
                        && reload.generatedBankStructureVersion(region) == 4,
                "A repeated Bank relocation forgot an earlier exclusion anchor or v4 identity");
        require(reload.isFallbackBankRegion(fallbackRegion)
                        && Long.valueOf(fallbackAnchor).equals(
                                reload.generatedBankAnchor(fallbackRegion)),
                "Explicit fallback bank provenance did not survive reload");
        require(!reload.isFallbackBankRegion(region)
                        && !reload.isFallbackBankRegion(legacyRegion),
                "Ordinary or legacy bank markers were invented as retryable fallbacks");

        long builtAnchor = 0x3020304050607080L;
        require(reload.markGeneratedBankRegion(fallbackRegion, builtAnchor, null, 4),
                "Completed Bank did not replace its fallback marker");
        require(!reload.isFallbackBankRegion(fallbackRegion)
                        && Long.valueOf(builtAnchor).equals(
                                reload.generatedBankAnchor(fallbackRegion))
                        && reload.generatedBankStructureVersion(fallbackRegion) == 4,
                "Completed Bank retained fallback provenance or lost its structure version");
        require(reload.markGeneratedBankRegion(legacyRegion, anchor)
                        && !reload.isFallbackBankRegion(legacyRegion),
                "Legacy anchor backfill was mistaken for an explicit fallback");
        require(reload.completeBankStructureUpgrade(legacyRegion, anchor, 2)
                        && reload.generatedBankStructureVersion(legacyRegion) == 2,
                "Legacy Bank structure upgrade was not durably recorded");
        Properties promotedProperties = readProperties(
                directory.resolve("the_emerald_standard.properties"));
        require(promotedProperties.getProperty(
                        "bank.fallback."
                                + Long.toUnsignedString(fallbackRegion, 16)) == null,
                "Completed Bank left a raw fallback marker in the save");

        EconomyService promotedReload = new EconomyService();
        promotedReload.startWithSeed(
                directory,
                999L,
                25L * EconomyService.MILLIS_PER_MINECRAFT_DAY,
                25L * EconomyService.TICKS_PER_MINECRAFT_DAY);
        require(!promotedReload.isFallbackBankRegion(fallbackRegion)
                        && Long.valueOf(builtAnchor).equals(
                                promotedReload.generatedBankAnchor(fallbackRegion))
                        && promotedReload.generatedBankStructureVersion(fallbackRegion) == 4
                        && promotedReload.generatedBankStructureVersion(legacyRegion) == 2
                        && promotedReload.retiredBankAnchors(region).equals(
                                List.of(anchor, replacementAnchor))
                        && Long.valueOf(secondReplacementAnchor).equals(
                                promotedReload.generatedBankAnchor(region)),
                "Fallback-to-Bank promotion did not survive reload");
    }

    private static void testRetiredBankAnchorHistoryCap(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 1_313L, 0L, 0L);
        long region = 0x41345678ABCDEF01L;
        long currentAnchor = packBlockPos(200, 65, 200);
        require(service.markGeneratedBankRegion(region, currentAnchor, null, 2),
                "Versioned Bank fixture could not be created");

        List<Long> expected = new ArrayList<>();
        for (int index = 0;
                index < EconomyState.MAX_RETIRED_BANK_ANCHORS_PER_REGION;
                index++) {
            require(service.retireGeneratedBankAnchor(region, currentAnchor),
                    "Bank retirement stopped before its bounded history was full");
            expected.add(currentAnchor);
            if (index == 0) {
                require(service.retireGeneratedBankAnchor(region, currentAnchor)
                                && service.retiredBankAnchors(region).size() == 1,
                        "Repeated retirement duplicated an existing Bank anchor");
            }
            require(service.retiredBankAnchors(region).equals(expected),
                    "Bank retirement history lost, reordered, or invented an anchor");
            currentAnchor = packBlockPos(232 + index * 11, 65 + index % 3, -210 - index * 9);
            require(service.markGeneratedBankRegion(region, currentAnchor, null, 2),
                    "Could not commit a replacement while filling Bank history");
        }

        require(!service.retireGeneratedBankAnchor(region, currentAnchor),
                "Bank retirement exceeded its bounded anchor history");
        require(service.retiredBankAnchors(region).equals(expected)
                        && Long.valueOf(currentAnchor).equals(
                                service.generatedBankAnchor(region)),
                "Rejected over-cap Bank retirement mutated the anchor or history");

        Properties persisted = readProperties(
                directory.resolve("the_emerald_standard.properties"));
        String historyKey =
                "bank.retired_anchors." + Long.toUnsignedString(region, 16);
        require(persisted.getProperty(historyKey) != null
                        && persisted.getProperty(historyKey).split(",", -1).length
                                == EconomyState.MAX_RETIRED_BANK_ANCHORS_PER_REGION,
                "Full Bank retirement history was not encoded in the save");

        EconomyService reload = new EconomyService();
        reload.startWithSeed(directory, 2_626L, 0L, 0L);
        require(reload.retiredBankAnchors(region).equals(expected)
                        && Long.valueOf(currentAnchor).equals(
                                reload.generatedBankAnchor(region)),
                "Full Bank retirement history did not survive restart");
    }

    private static void testCanonicalBankerPersistenceAndInvariants(Path directory)
            throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 1_515L, 0L, 0L);
        long firstRegion = 0x51345678ABCDEF01L;
        long secondRegion = 0x61345678ABCDEF01L;
        long firstAnchor = packBlockPos(320, 65, 320);
        long secondAnchor = packBlockPos(-320, 66, -320);
        long replacementAnchor = packBlockPos(392, 67, 280);
        UUID firstBanker = UUID.fromString("00000000-0000-0000-0000-000000001515");
        UUID secondBanker = UUID.fromString("00000000-0000-0000-0000-000000001516");
        UUID replacementBanker = UUID.fromString("00000000-0000-0000-0000-000000001517");

        require(service.markGeneratedBankRegion(firstRegion, firstAnchor, null, 2)
                        && service.markGeneratedBankRegion(
                                secondRegion, secondAnchor, null, 2),
                "Canonical Banker fixtures could not create generated regions");
        require(service.rememberGeneratedBanker(firstRegion, firstBanker)
                        && firstBanker.equals(service.generatedBankerId(firstRegion))
                        && Long.valueOf(firstAnchor).equals(
                                service.generatedBankerAssignedAnchor(firstRegion)),
                "Canonical Banker UUID and current assignment were not recorded together");
        Path save = directory.resolve("the_emerald_standard.properties");
        String firstBankerKey =
                "bank.banker." + Long.toUnsignedString(firstRegion, 16);
        String firstAssignmentKey =
                "bank.banker_anchor." + Long.toUnsignedString(firstRegion, 16);
        Properties firstPersisted = readProperties(save);
        require(firstBanker.toString().equals(firstPersisted.getProperty(firstBankerKey))
                        && Long.toString(firstAnchor).equals(
                                firstPersisted.getProperty(firstAssignmentKey)),
                "Canonical Banker creation left a partial identity or assignment on disk");
        require(service.rememberGeneratedBanker(firstRegion, firstBanker)
                        && firstBanker.equals(service.generatedBankerId(firstRegion))
                        && Long.valueOf(firstAnchor).equals(
                                service.generatedBankerAssignedAnchor(firstRegion)),
                "Remembering the same canonical Banker was not idempotent");
        require(!service.rememberGeneratedBanker(firstRegion, replacementBanker)
                        && firstBanker.equals(service.generatedBankerId(firstRegion)),
                "A replacement UUID displaced the canonical Banker for one region");
        require(!service.rememberGeneratedBanker(secondRegion, firstBanker)
                        && service.generatedBankerId(secondRegion) == null,
                "One Banker UUID was shared across generated regions");
        require(service.rememberGeneratedBanker(secondRegion, secondBanker)
                        && secondBanker.equals(service.generatedBankerId(secondRegion))
                        && Long.valueOf(secondAnchor).equals(
                                service.generatedBankerAssignedAnchor(secondRegion)),
                "A unique canonical Banker could not be recorded for the second region");

        Properties persisted = readProperties(save);
        require(firstBanker.toString().equals(persisted.getProperty(
                                "bank.banker."
                                        + Long.toUnsignedString(firstRegion, 16)))
                        && secondBanker.toString().equals(persisted.getProperty(
                                "bank.banker."
                                        + Long.toUnsignedString(secondRegion, 16))),
                "Canonical Banker UUIDs were not written with canonical unsigned keys");

        require(service.retireGeneratedBankAnchor(firstRegion, firstAnchor)
                        && service.markGeneratedBankRegion(
                                firstRegion, replacementAnchor, null, 4)
                        && Long.valueOf(replacementAnchor).equals(
                                service.generatedBankAnchor(firstRegion))
                        && service.generatedBankStructureVersion(firstRegion) == 4
                        && Long.valueOf(firstAnchor).equals(
                                service.generatedBankerAssignedAnchor(firstRegion)),
                "Bank relocation lost v4 identity or silently moved the canonical Banker's durable assignment");

        EconomyService reload = new EconomyService();
        reload.startWithSeed(directory, 3_030L, 0L, 0L);
        require(firstBanker.equals(reload.generatedBankerId(firstRegion))
                        && Long.valueOf(firstAnchor).equals(
                                reload.generatedBankerAssignedAnchor(firstRegion))
                        && secondBanker.equals(reload.generatedBankerId(secondRegion))
                        && Long.valueOf(secondAnchor).equals(
                                reload.generatedBankerAssignedAnchor(secondRegion)),
                "Canonical Banker identity or stale relocation assignment did not survive restart");
        require(reload.rememberGeneratedBanker(firstRegion, firstBanker)
                        && !reload.rememberGeneratedBanker(firstRegion, replacementBanker)
                        && !reload.rememberGeneratedBanker(secondRegion, firstBanker)
                        && firstBanker.equals(reload.generatedBankerId(firstRegion))
                        && secondBanker.equals(reload.generatedBankerId(secondRegion)),
                "Restart weakened canonical Banker idempotence or uniqueness");

        require(!reload.confirmGeneratedBankerAssignment(
                                firstRegion, replacementBanker, replacementAnchor)
                        && !reload.confirmGeneratedBankerAssignment(
                                firstRegion, firstBanker, firstAnchor)
                        && Long.valueOf(firstAnchor).equals(
                                reload.generatedBankerAssignedAnchor(firstRegion)),
                "A mismatched Banker UUID or obsolete Bank anchor changed the assignment");
        require(reload.confirmGeneratedBankerAssignment(
                                firstRegion, firstBanker, replacementAnchor)
                        && Long.valueOf(replacementAnchor).equals(
                                reload.generatedBankerAssignedAnchor(firstRegion)),
                "The matching canonical Banker could not confirm the current Bank assignment");

        EconomyService confirmedReload = new EconomyService();
        confirmedReload.startWithSeed(directory, 4_545L, 0L, 0L);
        require(firstBanker.equals(confirmedReload.generatedBankerId(firstRegion))
                        && Long.valueOf(replacementAnchor).equals(
                                confirmedReload.generatedBankerAssignedAnchor(firstRegion)),
                "Confirmed canonical Banker assignment did not survive restart");
        require(!confirmedReload.forgetGeneratedBanker(firstRegion, replacementBanker)
                        && firstBanker.equals(confirmedReload.generatedBankerId(firstRegion))
                        && Long.valueOf(replacementAnchor).equals(
                                confirmedReload.generatedBankerAssignedAnchor(firstRegion)),
                "A mismatched UUID cleared canonical Banker ownership");
        require(confirmedReload.forgetGeneratedBanker(firstRegion, firstBanker)
                        && confirmedReload.generatedBankerId(firstRegion) == null
                        && confirmedReload.generatedBankerAssignedAnchor(firstRegion) == null,
                "Forgetting the canonical Banker did not remove identity and assignment together");
        Properties forgotten = readProperties(save);
        require(forgotten.getProperty(firstBankerKey) == null
                        && forgotten.getProperty(firstAssignmentKey) == null,
                "Forgotten canonical Banker identity or assignment remained on disk");

        EconomyService forgottenReload = new EconomyService();
        forgottenReload.startWithSeed(directory, 6_060L, 0L, 0L);
        require(forgottenReload.generatedBankerId(firstRegion) == null
                        && forgottenReload.generatedBankerAssignedAnchor(firstRegion) == null
                        && secondBanker.equals(forgottenReload.generatedBankerId(secondRegion))
                        && Long.valueOf(secondAnchor).equals(
                                forgottenReload.generatedBankerAssignedAnchor(secondRegion)),
                "Canonical Banker removal or the unaffected region changed after restart");
    }

    private static void testDurableBankerDeathReplacement(Path directory) throws Exception {
        long region = 0x6A345678ABCDEF01L;
        long anchor = packBlockPos(412, 67, -284);
        UUID original = UUID.fromString("00000000-0000-0000-0000-000000001620");
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000001621");
        UUID wrong = UUID.fromString("00000000-0000-0000-0000-000000001622");
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 1_620L, 0L, 0L);
        require(service.markGeneratedBankRegion(region, anchor, null, 2)
                        && service.rememberGeneratedBanker(region, original),
                "Banker-death fixture could not establish canonical ownership");
        require(!service.recordGeneratedBankerDeath(region, wrong)
                        && !service.generatedBankerDeathPending(region, original)
                        && original.equals(service.generatedBankerId(region)),
                "A mismatched death observation changed canonical ownership");

        require(service.recordGeneratedBankerDeath(region, original)
                        && service.generatedBankerDeathPending(region, original)
                        && original.equals(service.generatedBankerId(region))
                        && !service.rememberGeneratedBanker(region, original),
                "A confirmed death did not preserve ownership behind a durable tombstone");
        String deathKey = "bank.banker_death." + Long.toUnsignedString(region, 16);
        Properties tombstoned = readProperties(
                directory.resolve("the_emerald_standard.properties"));
        require(original.toString().equals(tombstoned.getProperty(deathKey)),
                "Confirmed canonical Banker death was not encoded durably");

        EconomyService reload = new EconomyService();
        reload.startWithSeed(directory, 9_999L, 0L, 0L);
        require(reload.generatedBankerDeathPending(region, original)
                        && original.equals(reload.generatedBankerId(region))
                        && reload.rememberGeneratedBanker(region, replacement)
                        && replacement.equals(reload.generatedBankerId(region))
                        && !reload.generatedBankerDeathPending(region, original),
                "Restart did not permit one atomic replacement of the confirmed-dead Banker");
        require(!reload.rememberGeneratedBanker(region, wrong)
                        && replacement.equals(reload.generatedBankerId(region)),
                "A live replacement was displaced without a new death tombstone");

        Properties replaced = readProperties(
                directory.resolve("the_emerald_standard.properties"));
        require(replaced.getProperty(deathKey) == null
                        && replacement.toString().equals(replaced.getProperty(
                                "bank.banker." + Long.toUnsignedString(region, 16))),
                "Replacement did not atomically clear the old death tombstone");

        require(reload.recordGeneratedBankerDeath(region, replacement)
                        && reload.generatedBankerDeathPending(region, replacement)
                        && !reload.confirmGeneratedBankerAlive(region, wrong)
                        && reload.generatedBankerDeathPending(region, replacement)
                        && reload.confirmGeneratedBankerAlive(region, replacement)
                        && !reload.generatedBankerDeathPending(region, replacement),
                "Exact live-entity reconciliation did not safely cancel a stale tombstone");
        EconomyService aliveReload = new EconomyService();
        aliveReload.startWithSeed(directory, 10_000L, 0L, 0L);
        require(replacement.equals(aliveReload.generatedBankerId(region))
                        && !aliveReload.generatedBankerDeathPending(region, replacement),
                "Canceled Banker death tombstone returned after restart");
    }

    private static void testBankerDeathSaveFailureRecovery(Path directory) throws Exception {
        long region = 0x6B345678ABCDEF01L;
        long anchor = packBlockPos(-412, 68, 284);
        UUID original = UUID.fromString("00000000-0000-0000-0000-000000001623");
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000001624");
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 1_623L, 0L, 0L);
        require(service.markGeneratedBankRegion(region, anchor, null, 2)
                        && service.rememberGeneratedBanker(region, original),
                "Save-failure death fixture could not establish canonical ownership");

        Path heldDirectory = directory.resolveSibling(directory.getFileName() + "-held");
        Files.move(directory, heldDirectory);
        Files.writeString(directory, "deliberately not a directory");
        try {
            require(!service.recordGeneratedBankerDeath(region, original)
                            && original.equals(service.generatedBankerId(region))
                            && original.equals(
                                    service.snapshot().pendingBankerDeaths.get(region))
                            && !service.generatedBankerDeathPending(region, original)
                            && service.marketSnapshot().dirty()
                            && !service.rememberGeneratedBanker(region, replacement),
                    "A failed death save opened a duplicate window or discarded retry state");
        } finally {
            Files.deleteIfExists(directory);
            Files.move(heldDirectory, directory);
        }

        require(service.deposit(PLAYER, 1L)
                        && service.generatedBankerDeathPending(region, original),
                "A later successful full-state save did not make the retained death durable");
        EconomyService reload = new EconomyService();
        reload.startWithSeed(directory, 9_999L, 0L, 0L);
        require(reload.generatedBankerDeathPending(region, original)
                        && reload.rememberGeneratedBanker(region, replacement)
                        && replacement.equals(reload.generatedBankerId(region)),
                "Recovered storage or restart lost the pending Banker replacement");
    }

    private static void testLegacyBankerDeathMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        long region = 0x6C345678ABCDEF01L;
        long anchor = packBlockPos(88, 69, -88);
        UUID banker = UUID.fromString("00000000-0000-0000-0000-000000001625");
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 1_625L, 0L, 0L);
        require(service.markGeneratedBankRegion(region, anchor, null, 2)
                        && service.rememberGeneratedBanker(region, banker),
                "Format-15 Banker-death fixture could not be created");

        String deathKey = "bank.banker_death." + Long.toUnsignedString(region, 16);
        Properties legacy = readProperties(save);
        legacy.setProperty("format", "15");
        legacy.setProperty(deathKey, banker.toString());
        refreshChecksum(legacy);
        writeProperties(save, legacy);

        EconomyService migrated = new EconomyService();
        migrated.startWithSeed(directory, 9_999L, 0L, 0L);
        require(banker.equals(migrated.generatedBankerId(region))
                        && !migrated.generatedBankerDeathPending(region, banker),
                "Format 15 invented trusted Banker-death provenance");
        Properties upgraded = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                                upgraded.getProperty("format"))
                        && upgraded.getProperty(deathKey) == null,
                "Untrusted format-15 Banker-death data survived the format-16 rewrite");

        Properties formatSixteen = readProperties(save);
        formatSixteen.setProperty("format", "16");
        formatSixteen.setProperty(deathKey, banker.toString());
        refreshChecksum(formatSixteen);
        writeProperties(save, formatSixteen);
        EconomyService formatSixteenMigration = new EconomyService();
        formatSixteenMigration.startWithSeed(directory, 10_001L, 0L, 0L);
        require(banker.equals(formatSixteenMigration.generatedBankerId(region))
                        && !formatSixteenMigration.generatedBankerDeathPending(region, banker),
                "Format 16 pre-barrier tombstone was trusted as replacement authority");
        Properties formatSeventeen = readProperties(save);
        require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                                formatSeventeen.getProperty("format"))
                        && formatSeventeen.getProperty(deathKey) == null,
                "Untrusted format-16 Banker death survived the format-17 rewrite");
    }

    private static void testBankerDeathValidation() throws Exception {
        long region = 0x6D345678ABCDEF01L;
        UUID canonical = UUID.fromString("00000000-0000-0000-0000-000000001626");
        UUID different = UUID.fromString("00000000-0000-0000-0000-000000001627");
        EconomyState orphaned = EconomyState.fresh(1_626L, 0L, 0L);
        orphaned.pendingBankerDeaths.put(region, canonical);
        requireValidationFailure(orphaned,
                "A Banker-death tombstone without a generated region passed validation");

        EconomyState mismatched = EconomyState.fresh(1_627L, 0L, 0L);
        mismatched.generatedBankRegions.add(region);
        mismatched.generatedBankAnchors.put(region, packBlockPos(0, 64, 0));
        mismatched.bankRegionBankerIds.put(region, canonical);
        mismatched.bankRegionBankerAnchors.put(region, packBlockPos(0, 64, 0));
        mismatched.pendingBankerDeaths.put(region, different);
        requireValidationFailure(mismatched,
                "A tombstone for a different canonical Banker passed validation");
    }

    private static void testFormatThirteenBankerAssignmentInference(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(1_616L, 0L, 0L);
        long retiredRegion = 0x71345678ABCDEF01L;
        long currentRegion = 0x72345678ABCDEF01L;
        long oldestAnchor = packBlockPos(40, 64, 40);
        long newestRetiredAnchor = packBlockPos(80, 65, 80);
        long currentAnchor = packBlockPos(120, 66, 120);
        long onlyCurrentAnchor = packBlockPos(-120, 67, -120);
        UUID retiredBanker = UUID.fromString("00000000-0000-0000-0000-000000001618");
        UUID currentBanker = UUID.fromString("00000000-0000-0000-0000-000000001619");

        state.generatedBankRegions.add(retiredRegion);
        state.generatedBankAnchors.put(retiredRegion, currentAnchor);
        state.bankStructureVersions.put(retiredRegion, 2);
        state.retiredBankAnchors.put(
                retiredRegion, new ArrayList<>(List.of(oldestAnchor, newestRetiredAnchor)));
        state.bankRegionBankerIds.put(retiredRegion, retiredBanker);
        state.bankRegionBankerAnchors.put(retiredRegion, newestRetiredAnchor);
        state.generatedBankRegions.add(currentRegion);
        state.generatedBankAnchors.put(currentRegion, onlyCurrentAnchor);
        state.bankStructureVersions.put(currentRegion, 2);
        state.bankRegionBankerIds.put(currentRegion, currentBanker);
        state.bankRegionBankerAnchors.put(currentRegion, onlyCurrentAnchor);
        state.save(save);

        Properties earlyFormatThirteen = readProperties(save);
        earlyFormatThirteen.setProperty("format", "13");
        String retiredAssignmentKey =
                "bank.banker_anchor." + Long.toUnsignedString(retiredRegion, 16);
        String currentAssignmentKey =
                "bank.banker_anchor." + Long.toUnsignedString(currentRegion, 16);
        earlyFormatThirteen.remove(retiredAssignmentKey);
        earlyFormatThirteen.remove(currentAssignmentKey);
        refreshChecksum(earlyFormatThirteen);
        writeProperties(save, earlyFormatThirteen);

        EconomyService migrated = new EconomyService();
        migrated.startWithSeed(directory, 9_999L, 0L, 0L);
        require(Long.valueOf(newestRetiredAnchor).equals(
                                migrated.generatedBankerAssignedAnchor(retiredRegion))
                        && Long.valueOf(onlyCurrentAnchor).equals(
                                migrated.generatedBankerAssignedAnchor(currentRegion)),
                "Early format-13 Banker assignments did not infer newest retired then current anchors");
        Properties upgraded = readProperties(save);
        require(Long.toString(newestRetiredAnchor).equals(
                                upgraded.getProperty(retiredAssignmentKey))
                        && Long.toString(onlyCurrentAnchor).equals(
                                upgraded.getProperty(currentAssignmentKey)),
                "Inferred format-13 Banker assignments were not persisted on startup");
    }

    private static void testFormatThirteenTrailCenterSurfaceMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(1_626L, 0L, 0L);
        state.economicDay = 2L;
        state.liveMarket=LiveMarket.adopt(state); // Synthetic migration fixture.
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000001626");
        EconomyState.VillageRecord village = state.village(villageId);
        village.architectureCharacter = VillageArchitecture.Character.RUSTIC.id();
        village.architectureDialect = VillageArchitecture.BiomeDialect.PLAINS.id();

        for (int index = 0; index < 3; index++) {
            EconomyState.VillageProject project = new EconomyState.VillageProject();
            project.projectId = index + 1L;
            project.type = VillageProsperityEngine.ProjectType.GUARD_POST;
            project.approvedDay = 1L;
            project.completedDay = 2L;
            project.economicProgress = 1.0;
            project.economicComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                    villageId, project.projectId, project.type, List.of());
            project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
            project.designSeed = recipe.seed();
            project.designSilhouette = recipe.silhouette();
            project.designRoof = recipe.roof();
            project.designFrontage = recipe.frontage();
            project.designMirrored = recipe.mirrored();
            project.designSignature = recipe.signature();
            if (index == 0) {
                project.originPos = packBlockPos(20, 64, 20);
                project.boundsMinPos = packBlockPos(18, 60, 18);
                project.boundsMaxPos = packBlockPos(30, 76, 30);
                project.materializedBlocks = project.totalBlocks;
                project.materializedComplete = true;
                project.trailAnchorSet = true;
                project.trailAnchorPos = packBlockPos(14, 64, 20);
                // Preserve an inflated development-build total exactly. The new surface scan must
                // not reinterpret or clamp the old ordinary road's cursor contract.
                project.trailMaterializedBlocks = 131;
                project.trailTotalBlocks = 131;
                project.trailMaterializedComplete = true;
            } else if (index == 2) {
                project.abstractOnly = true;
            }
            village.projects.add(project);
        }

        EconomyState.VillageProject legacy = new EconomyState.VillageProject();
        legacy.projectId = 4L;
        legacy.type = VillageProsperityEngine.ProjectType.COTTAGE;
        legacy.approvedDay = 1L;
        legacy.completedDay = 2L;
        legacy.economicProgress = 1.0;
        legacy.economicComplete = true;
        legacy.originPos = packBlockPos(50, 64, 50);
        legacy.boundsMinPos = packBlockPos(48, 60, 48);
        legacy.boundsMaxPos = packBlockPos(60, 76, 60);
        legacy.totalBlocks = legacy.type.nominalBlocks();
        legacy.materializedBlocks = legacy.totalBlocks;
        legacy.materializedComplete = true;
        legacy.trailAnchorSet = true;
        legacy.trailAnchorPos = packBlockPos(44, 64, 50);
        legacy.trailMaterializedBlocks = 12;
        legacy.trailTotalBlocks = 12;
        legacy.trailMaterializedComplete = true;
        village.projects.add(legacy);
        village.projectSerial = 4L;
        state.save(save);

        Properties formatThirteen = readProperties(save);
        formatThirteen.setProperty("format", "13");
        formatThirteen.stringPropertyNames().stream()
                .filter(key -> key.contains(".trail.center_surface_"))
                .toList()
                .forEach(formatThirteen::remove);
        refreshChecksum(formatThirteen);
        writeProperties(save, formatThirteen);

        EconomyState migrated = EconomyState.load(save, 0L, 0L, 0L);
        List<EconomyState.VillageProject> projects =
                migrated.existingVillage(villageId).projects;
        EconomyState.VillageProject physical = projects.get(0);
        require(physical.trailCenterSurfaceVersion == 0
                        && physical.trailCenterSurfaceMigrationCursor == 0
                        && physical.trailCenterSurfaceMigrationTotalCells == 0
                        && physical.trailMaterializedBlocks == 131
                        && physical.trailTotalBlocks == 131
                        && physical.trailMaterializedComplete,
                "Format-13 physical modular road did not become independently pending");
        require(projects.get(1).trailCenterSurfaceVersion
                                == EconomyState.TRAIL_CENTER_SURFACE_VERSION
                        && projects.get(2).trailCenterSurfaceVersion
                                == EconomyState.TRAIL_CENTER_SURFACE_VERSION
                        && projects.get(3).trailCenterSurfaceVersion
                                == EconomyState.TRAIL_CENTER_SURFACE_VERSION,
                "Unreserved, abstract, or legacy projects received a world-surface migration");

        migrated.save(save);
        Properties upgraded = readProperties(save);
        String projectPrefix = "village." + villageId + ".project.1.";
        require("0".equals(upgraded.getProperty(
                                projectPrefix + "trail.center_surface_version"))
                        && "0".equals(upgraded.getProperty(
                                projectPrefix + "trail.center_surface_cursor"))
                        && "0".equals(upgraded.getProperty(
                                projectPrefix + "trail.center_surface_total_cells")),
                "Pending format-13 center-surface state did not persist in the current format");
        EconomyState reloaded = EconomyState.load(save, 0L, 0L, 0L);
        EconomyState.VillageProject pending =
                reloaded.existingVillage(villageId).projects.getFirst();
        require(pending.trailCenterSurfaceVersion == 0
                        && pending.trailCenterSurfaceMigrationCursor == 0
                        && pending.trailCenterSurfaceMigrationTotalCells == 0
                        && pending.trailTotalBlocks == 131,
                "Current-format reload lost pending migration state or changed the old road");
    }

    private static void testFormatFourteenEntranceApproachMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(1_627L, 0L, 0L);
        state.economicDay = 2L;
        state.liveMarket=LiveMarket.adopt(state); // Synthetic migration fixture.
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000001627");
        EconomyState.VillageRecord village = state.village(villageId);
        village.architectureCharacter = VillageArchitecture.Character.RUSTIC.id();
        village.architectureDialect = VillageArchitecture.BiomeDialect.PLAINS.id();

        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = 1L;
        project.type = VillageProsperityEngine.ProjectType.COTTAGE;
        project.approvedDay = 1L;
        project.completedDay = 2L;
        project.economicProgress = 1.0;
        project.economicComplete = true;
        project.originPos = packBlockPos(20, 64, 20);
        project.boundsMinPos = packBlockPos(18, 60, 18);
        project.boundsMaxPos = packBlockPos(30, 76, 30);
        project.totalBlocks = project.type.nominalBlocks();
        project.materializedBlocks = project.totalBlocks;
        project.materializedComplete = true;
        VillageArchitecture.Recipe recipe = VillageArchitecture.choose(
                villageId, project.projectId, project.type, List.of());
        project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
        project.designSeed = recipe.seed();
        project.designSilhouette = recipe.silhouette();
        project.designRoof = recipe.roof();
        project.designFrontage = recipe.frontage();
        project.designMirrored = recipe.mirrored();
        project.designSignature = recipe.signature();
        project.trailAnchorSet = true;
        project.trailAnchorPos = packBlockPos(14, 64, 20);
        project.trailMaterializedBlocks = 12;
        project.trailTotalBlocks = 12;
        project.trailMaterializedComplete = true;
        project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
        project.entranceApproachStepCount = 2;
        project.entranceApproachCursor = 3;
        project.entranceApproachTotalCells = 3;
        project.entranceApproachComplete = true;
        village.projects.add(project);
        village.projectSerial = 1L;
        state.save(save);

        Properties formatFourteen = readProperties(save);
        formatFourteen.setProperty("format", "14");
        // These keys did not exist in format 14. Leave their current-format values in place to
        // prove the old format cannot invent trusted approach provenance.
        refreshChecksum(formatFourteen);
        writeProperties(save, formatFourteen);

        EconomyState migrated = EconomyState.load(save, 0L, 0L, 0L);
        EconomyState.VillageProject pending =
                migrated.existingVillage(villageId).projects.getFirst();
        require(pending.entranceApproachVersion == 0
                        && pending.entranceApproachStepCount == 0
                        && pending.entranceApproachCursor == 0
                        && pending.entranceApproachTotalCells == 0
                        && !pending.entranceApproachComplete
                        && pending.trailMaterializedBlocks == 12
                        && pending.trailTotalBlocks == 12
                        && pending.trailMaterializedComplete,
                "Format-14 project invented trusted entrance-approach progress or changed its road");

        migrated.save(save);
        Properties upgraded = readProperties(save);
        String projectPrefix = "village." + villageId + ".project.1.";
        require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                                upgraded.getProperty("format"))
                        && "0".equals(upgraded.getProperty(
                                projectPrefix + "entrance.approach_version"))
                        && "0".equals(upgraded.getProperty(
                                projectPrefix + "entrance.approach_step_count"))
                        && "0".equals(upgraded.getProperty(
                                projectPrefix + "entrance.approach_cursor"))
                        && "0".equals(upgraded.getProperty(
                                projectPrefix + "entrance.approach_total_cells"))
                        && "false".equals(upgraded.getProperty(
                                projectPrefix + "entrance.approach_complete")),
                "Pending format-14 entrance approach did not persist safely in format 15");
        EconomyState reloaded = EconomyState.load(save, 0L, 0L, 0L);
        EconomyState.VillageProject reloadedPending =
                reloaded.existingVillage(villageId).projects.getFirst();
        require(reloadedPending.entranceApproachVersion == 0
                        && reloadedPending.entranceApproachStepCount == 0
                        && reloadedPending.entranceApproachCursor == 0
                        && reloadedPending.entranceApproachTotalCells == 0
                        && !reloadedPending.entranceApproachComplete,
                "Current-format reload lost the pending entrance-approach migration state");
    }

    private static void testVillageProjectLotExclusions(Path directory) throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(1_717L, 0L, 0L);
        UUID overworldVillage =
                UUID.fromString("00000000-0000-0000-0000-000000001720");
        UUID netherVillage =
                UUID.fromString("00000000-0000-0000-0000-000000001721");

        long activeMin = packBlockPos(10, 60, 10);
        long activeMax = packBlockPos(20, 72, 20);
        long sharedMin = packBlockPos(40, 61, 40);
        long sharedMax = packBlockPos(50, 73, 50);
        long retiredMin = packBlockPos(70, 62, 70);
        long retiredMax = packBlockPos(80, 74, 80);
        EconomyState.VillageRecord overworld = state.village(overworldVillage);
        overworld.dimensionKey = "minecraft:overworld";
        EconomyState.VillageProject first = reservedProject(1L, activeMin, activeMax);
        first.retiredLots.add(new EconomyState.RetiredProjectLot(sharedMin, sharedMax));
        EconomyState.VillageProject second = reservedProject(2L, sharedMin, sharedMax);
        second.retiredLots.add(new EconomyState.RetiredProjectLot(retiredMin, retiredMax));
        overworld.projects.add(first);
        overworld.projects.add(second);
        overworld.projectSerial = 2L;

        long netherActiveMin = packBlockPos(-80, 50, -80);
        long netherActiveMax = packBlockPos(-70, 65, -70);
        long netherRetiredMin = packBlockPos(-50, 51, -50);
        long netherRetiredMax = packBlockPos(-40, 66, -40);
        EconomyState.VillageRecord nether = state.village(netherVillage);
        nether.dimensionKey = "minecraft:the_nether";
        EconomyState.VillageProject netherProject =
                reservedProject(1L, netherActiveMin, netherActiveMax);
        netherProject.retiredLots.add(
                new EconomyState.RetiredProjectLot(netherRetiredMin, netherRetiredMax));
        nether.projects.add(netherProject);
        nether.projectSerial = 1L;
        state.save(save);

        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 8_888L, 0L, 0L);
        List<EconomyService.VillageProjectLot> overworldLots =
                service.villageProjectLotExclusions("minecraft:overworld");
        Set<EconomyService.VillageProjectLot> expectedOverworld = Set.of(
                new EconomyService.VillageProjectLot(activeMin, activeMax),
                new EconomyService.VillageProjectLot(sharedMin, sharedMax),
                new EconomyService.VillageProjectLot(retiredMin, retiredMax));
        require(overworldLots.size() == expectedOverworld.size()
                        && new HashSet<>(overworldLots).equals(expectedOverworld),
                "Project-lot exclusions lost active/retired bounds or retained duplicates");

        List<EconomyService.VillageProjectLot> netherLots =
                service.villageProjectLotExclusions("minecraft:the_nether");
        Set<EconomyService.VillageProjectLot> expectedNether = Set.of(
                new EconomyService.VillageProjectLot(netherActiveMin, netherActiveMax),
                new EconomyService.VillageProjectLot(netherRetiredMin, netherRetiredMax));
        require(netherLots.size() == expectedNether.size()
                        && new HashSet<>(netherLots).equals(expectedNether)
                        && netherLots.stream().noneMatch(expectedOverworld::contains)
                        && overworldLots.stream().noneMatch(expectedNether::contains),
                "Project-lot exclusions crossed dimension boundaries");

        boolean immutable = false;
        try {
            overworldLots.add(new EconomyService.VillageProjectLot(1L, 2L));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        require(immutable, "Project-lot exclusion snapshot exposed mutable list state");
    }

    private static EconomyState.VillageProject reservedProject(
            long projectId, long boundsMinPos, long boundsMaxPos) {
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = projectId;
        project.economicProgress = 1.0;
        project.economicComplete = true;
        project.originPos = boundsMinPos;
        project.boundsMinPos = boundsMinPos;
        project.boundsMaxPos = boundsMaxPos;
        project.totalBlocks = project.type.nominalBlocks();
        return project;
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
                || expected.observedHousingCapacity != actual.observedHousingCapacity
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
