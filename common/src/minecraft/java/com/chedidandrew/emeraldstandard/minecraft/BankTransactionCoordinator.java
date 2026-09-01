package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Coordinates the durable bank journal with Minecraft player-data saves.
 *
 * <p>Bank and inventory data are stored in different files. A journal entry remains durable until
 * online player data is synchronously flushed, allowing login recovery to complete or roll back an
 * interrupted transaction without duplicating or destroying items.</p>
 */
public final class BankTransactionCoordinator {
    private static final String PREFIX = "[Emerald Standard] ";

    private BankTransactionCoordinator() {
    }

    /** Flushes online player data and then clears a BANK_COMMITTED journal record. */
    public static boolean savePlayerAndComplete(
            ServerPlayer player,
            EconomyService economy,
            UUID transactionId) {
        if (!savePlayer(player)) {
            return false;
        }
        return economy.completeInventoryTransaction(player.getUUID(), transactionId);
    }

    /**
     * Reconciles an interrupted inventory transaction. Safe to call on login, logout, or before a
     * bank command. The operation is idempotent while the journal record remains present.
     */
    public static RecoveryResult reconcile(ServerPlayer player, EconomyService economy) {
        EconomyState.PendingInventoryTransaction transaction =
                economy.pendingInventoryTransaction(player.getUUID());
        if (transaction == null) {
            return RecoveryResult.none();
        }

        Item item = BankInventory.itemForJournalKey(transaction.itemKey);
        if (item == null) {
            return RecoveryResult.failed(
                    "Unknown journal item " + transaction.itemKey + "; transaction retained");
        }

        int current = BankInventory.countItems(player, item);
        int corrected = 0;
        if (transaction.stage == EconomyState.InventoryTransactionStage.PREPARED) {
            int missing = Math.min(
                    transaction.itemCount,
                    Math.max(0, transaction.inventoryCountBefore - current));
            if (missing > 0) {
                BankInventory.giveOrDrop(player, item, missing);
                corrected = missing;
            }
            if (!savePlayer(player)) {
                return RecoveryResult.failed(
                        "Could not save player data while rolling back a prepared transaction");
            }
            if (!economy.cancelPreparedInventoryTransaction(
                    player.getUUID(), transaction.transactionId)) {
                return RecoveryResult.failed(
                        "Inventory was restored, but the prepared journal could not be cleared");
            }
            notifyPlayer(player,
                    "Recovered an interrupted bank transaction. "
                            + corrected + " item(s) were restored and no bank credit was applied.");
            return RecoveryResult.recovered(transaction.transactionId, corrected, true);
        }

        int expected = transaction.expectedInventoryCount();
        if (transaction.inventoryDelta() < 0) {
            int excess = Math.min(transaction.itemCount, Math.max(0, current - expected));
            if (excess > 0) {
                if (!BankInventory.removeItems(player, item, excess)) {
                    return RecoveryResult.failed(
                            "Could not remove rolled-back deposit items during recovery");
                }
                corrected = excess;
            }
        } else {
            int missing = Math.min(transaction.itemCount, Math.max(0, expected - current));
            if (missing > 0) {
                BankInventory.giveOrDrop(player, item, missing);
                corrected = missing;
            }
        }

        if (!savePlayer(player)) {
            return RecoveryResult.failed(
                    "Could not save player data while completing a committed transaction");
        }
        if (!economy.completeInventoryTransaction(
                player.getUUID(), transaction.transactionId)) {
            return RecoveryResult.failed(
                    "Inventory was reconciled, but the committed journal could not be cleared");
        }

        notifyPlayer(player,
                "Recovered an interrupted bank transaction. "
                        + corrected + " item(s) required reconciliation.");
        return RecoveryResult.recovered(transaction.transactionId, corrected, false);
    }

    private static boolean savePlayer(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        try {
            // Vanilla exposes a public all-player flush, while the single-player save method is
            // protected. The journal keeps this correct, and a future server-scale storage layer
            // can replace the broader flush without changing transaction semantics.
            server.getPlayerList().saveAll();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void notifyPlayer(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(PREFIX + message));
    }

    public record RecoveryResult(
            boolean found,
            boolean recovered,
            boolean rolledBack,
            UUID transactionId,
            int correctedItems,
            String error) {
        static RecoveryResult none() {
            return new RecoveryResult(false, true, false, null, 0, "");
        }

        static RecoveryResult recovered(UUID transactionId, int correctedItems, boolean rolledBack) {
            return new RecoveryResult(
                    true, true, rolledBack, transactionId, correctedItems, "");
        }

        static RecoveryResult failed(String error) {
            return new RecoveryResult(true, false, false, null, 0, error);
        }
    }
}
