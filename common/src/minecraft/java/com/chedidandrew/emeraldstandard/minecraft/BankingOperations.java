package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Shared, server-authoritative banking actions used by the graphical Banker menu. */
public final class BankingOperations {
    private static final Map<UUID, Long> LAST_ACTION_TICK = new HashMap<>();
    static final int READY = 0;
    static final int DEPOSITED = 1;
    static final int WITHDREW = 2;
    static final int SAVED = 3;
    static final int UNSAVED = 4;
    static final int BOUGHT = 5;
    static final int SOLD = 6;
    static final int CD_OPENED = 7;
    static final int CD_CLOSED = 8;
    static final int LENDING_FUNDED = 9;
    static final int LENDING_COLLECTED = 10;
    static final int EXCHANGED = 11;
    static final int RECOVERED = 12;
    static final int RECOVERY_PENDING = 13;

    static final int BUSY = -1;
    static final int INSUFFICIENT = -2;
    static final int INVENTORY_FULL = -3;
    static final int PRODUCT_ACTIVE = -4;
    static final int NOT_READY = -5;
    static final int PERSISTENCE_FAILED = -6;
    static final int UNSUPPORTED = -7;

    private BankingOperations() {
    }

    /** Clears short-lived per-player cooldown state when a player leaves the server. */
    public static void forgetPlayer(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    static int recover(ServerPlayer player, EconomyService economy) {
        BankTransactionCoordinator.RecoveryResult result =
                BankTransactionCoordinator.reconcile(player, economy);
        if (!result.found()) {
            return READY;
        }
        return result.recovered() ? RECOVERED : PERSISTENCE_FAILED;
    }

    static int deposit(ServerPlayer player, EconomyService economy, int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        int inventoryBefore = BankInventory.countItems(player, Items.EMERALD);
        int amount = cappedInventoryAmount(requested, inventoryBefore);
        if (amount <= 0) {
            return INSUFFICIENT;
        }

        EconomyState.PendingInventoryTransaction transaction =
                economy.prepareInventoryCredit(
                        player.getUUID(),
                        EconomyState.InventoryTransactionKind.DEPOSIT,
                        "emerald",
                        amount,
                        inventoryBefore,
                        amount * EconomyState.MICRO);
        if (transaction == null) {
            return PERSISTENCE_FAILED;
        }
        if (!BankInventory.removeItems(player, Items.EMERALD, amount)) {
            economy.cancelPreparedInventoryTransaction(
                    player.getUUID(), transaction.transactionId);
            return INSUFFICIENT;
        }
        if (!economy.commitPreparedInventoryCredit(
                player.getUUID(), transaction.transactionId)) {
            int remainder = BankInventory.restoreItems(player, Items.EMERALD, amount);
            if (remainder == 0) {
                economy.cancelPreparedInventoryTransaction(
                        player.getUUID(), transaction.transactionId);
                return PERSISTENCE_FAILED;
            }
            return RECOVERY_PENDING;
        }
        return BankTransactionCoordinator.savePlayerAndComplete(
                        player, economy, transaction.transactionId)
                ? DEPOSITED
                : RECOVERY_PENDING;
    }

    static int withdraw(ServerPlayer player, EconomyService economy, int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        long available = economy.portfolioSnapshot(player.getUUID()).account().cashMicro
                / EconomyState.MICRO;
        int amount = cappedInventoryAmount(requested, available);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        int inventoryBefore = BankInventory.countItems(player, Items.EMERALD);
        EconomyState.PendingInventoryTransaction transaction =
                economy.beginInventoryWithdrawal(player.getUUID(), amount, inventoryBefore);
        if (transaction == null) {
            return INSUFFICIENT;
        }

        int remainder = BankInventory.insertItems(player, Items.EMERALD, amount);
        int delivered = amount - remainder;
        if (remainder > 0
                && !economy.reducePendingWithdrawal(
                        player.getUUID(), transaction.transactionId, remainder)) {
            return RECOVERY_PENDING;
        }

        EconomyState.PendingInventoryTransaction adjusted =
                economy.pendingInventoryTransaction(player.getUUID());
        if (adjusted == null) {
            return delivered > 0 ? WITHDREW : INVENTORY_FULL;
        }
        if (!BankTransactionCoordinator.savePlayerAndComplete(
                player, economy, adjusted.transactionId)) {
            return RECOVERY_PENDING;
        }
        return delivered > 0 ? WITHDREW : INVENTORY_FULL;
    }

    static int moveSavings(
            ServerPlayer player,
            EconomyService economy,
            int requested,
            boolean intoSavings) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        long availableMicro = intoSavings ? account.cashMicro : account.savingsMicro;
        int amount = cappedFinancialAmount(requested, availableMicro / EconomyState.MICRO);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        if (!economy.moveSavings(player.getUUID(), amount, intoSavings)) {
            return PERSISTENCE_FAILED;
        }
        return intoSavings ? SAVED : UNSAVED;
    }

    static int buy(
            ServerPlayer player,
            EconomyService economy,
            String ticker,
            int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        long cash = economy.portfolioSnapshot(player.getUUID()).account().cashMicro
                / EconomyState.MICRO;
        int amount = cappedFinancialAmount(requested, cash);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        return economy.buy(player.getUUID(), ticker, amount) ? BOUGHT : PERSISTENCE_FAILED;
    }

    static int sellFraction(
            ServerPlayer player,
            EconomyService economy,
            String ticker,
            double fraction) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        double held = economy.portfolioSnapshot(player.getUUID()).account().shares
                .getOrDefault(ticker, 0.0);
        if (!Double.isFinite(held) || held <= 0.0) {
            return INSUFFICIENT;
        }
        double shares = fraction >= 0.999999 ? held : held * fraction;
        return economy.sell(player.getUUID(), ticker, shares) ? SOLD : PERSISTENCE_FAILED;
    }

    static int openCd(
            ServerPlayer player,
            EconomyService economy,
            int requested,
            int termDays) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        if (account.hasCd()) {
            return PRODUCT_ACTIVE;
        }
        int amount = cappedFinancialAmount(requested, account.cashMicro / EconomyState.MICRO);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        return economy.openCd(player.getUUID(), amount, termDays)
                ? CD_OPENED
                : PERSISTENCE_FAILED;
    }

    static int closeCd(ServerPlayer player, EconomyService economy) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyService.CdCloseResult result = economy.closeCd(player.getUUID());
        return result.closed() ? CD_CLOSED : NOT_READY;
    }

    static int fundLending(
            ServerPlayer player,
            EconomyService economy,
            int requested,
            int termDays) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        if (account.hasLoan()) {
            return PRODUCT_ACTIVE;
        }
        int amount = cappedFinancialAmount(requested, account.cashMicro / EconomyState.MICRO);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        return economy.fundLoan(player.getUUID(), amount, termDays)
                ? LENDING_FUNDED
                : PERSISTENCE_FAILED;
    }

    static int collectLending(ServerPlayer player, EconomyService economy) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyService.LoanCollectionResult result = economy.collectLoan(player.getUUID());
        return result.collected() ? LENDING_COLLECTED : NOT_READY;
    }

    static int exchange(
            ServerPlayer player,
            EconomyService economy,
            BankInventory.ExchangeResource resource,
            int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        if (resource == null) {
            return UNSUPPORTED;
        }
        int inventoryBefore = BankInventory.countItems(player, resource.item());
        int amount = cappedInventoryAmount(requested, inventoryBefore);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        long proceeds = economy.quoteResourceValueMicro(resource.quoteId(), amount);
        if (proceeds <= 0L) {
            return UNSUPPORTED;
        }

        EconomyState.PendingInventoryTransaction transaction =
                economy.prepareInventoryCredit(
                        player.getUUID(),
                        EconomyState.InventoryTransactionKind.EXCHANGE,
                        resource.journalKey(),
                        amount,
                        inventoryBefore,
                        proceeds);
        if (transaction == null) {
            return PERSISTENCE_FAILED;
        }
        if (!BankInventory.removeItems(player, resource.item(), amount)) {
            economy.cancelPreparedInventoryTransaction(
                    player.getUUID(), transaction.transactionId);
            return INSUFFICIENT;
        }
        if (!economy.commitPreparedInventoryCredit(
                player.getUUID(), transaction.transactionId)) {
            int remainder = BankInventory.restoreItems(player, resource.item(), amount);
            if (remainder == 0) {
                economy.cancelPreparedInventoryTransaction(
                        player.getUUID(), transaction.transactionId);
                return PERSISTENCE_FAILED;
            }
            return RECOVERY_PENDING;
        }
        return BankTransactionCoordinator.savePlayerAndComplete(
                        player, economy, transaction.transactionId)
                ? EXCHANGED
                : RECOVERY_PENDING;
    }

    private static int prepare(ServerPlayer player, EconomyService economy) {
        BankTransactionCoordinator.RecoveryResult recovery =
                BankTransactionCoordinator.reconcile(player, economy);
        if (recovery.found() && !recovery.recovered()) {
            return PERSISTENCE_FAILED;
        }
        if (!economy.transactionBlockReason(player.getUUID()).isBlank()) {
            return BUSY;
        }
        int cooldown = EmeraldConfig.current().transactionCooldownTicks();
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.get(player.getUUID());
        if (cooldown > 0
                && previous != null
                && now >= previous
                && now - previous < cooldown) {
            return BUSY;
        }
        LAST_ACTION_TICK.put(player.getUUID(), now);
        return READY;
    }

    private static int cappedInventoryAmount(int requested, long available) {
        if (requested <= 0 || available <= 0L) {
            return 0;
        }
        return (int) Math.min(
                Math.min((long) requested, available),
                EconomyService.MAX_INVENTORY_ITEM_TRANSACTION);
    }

    private static int cappedFinancialAmount(int requested, long available) {
        if (requested <= 0 || available <= 0L) {
            return 0;
        }
        return (int) Math.min(
                Math.min((long) requested, available),
                EconomyService.MAX_WHOLE_EMERALD_TRANSACTION);
    }
}
