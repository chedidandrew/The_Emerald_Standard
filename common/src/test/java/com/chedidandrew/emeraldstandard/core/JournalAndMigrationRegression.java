package com.chedidandrew.emeraldstandard.core;

import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.PLAYER;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.baseProperties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.readProperties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.requireValidationFailure;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.writeProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class JournalAndMigrationRegression {
    private JournalAndMigrationRegression() {
    }

    static void run(Path root) throws Exception {
        testJournalLifecycle(root.resolve("journal"));
        testJournalValidation();
        testLegacyMigration(root.resolve("legacy"));
        testFormatTwoMigration(root.resolve("format-two"));
        testFormatThreeClockMigration(root.resolve("format-three"));
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
                "Format 2 migration invented format 4 state");
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
        require(service.markGeneratedBankRegion(region), "Bank region marker failed");
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
    }
}
