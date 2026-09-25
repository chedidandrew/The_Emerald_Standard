package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.InventoryReceipt;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates the durable bank journal with Minecraft player-data saves.
 *
 * <p>Bank and inventory data are stored in different files. A journal entry remains durable until
 * the affected player's synchronized NBT checkpoint is written and read back, allowing login
 * recovery to complete or roll back an interrupted transaction without duplicating or destroying
 * items.</p>
 */
public final class BankTransactionCoordinator {
    private static final String PREFIX = "[Emerald Standard] ";
    private static final Logger LOGGER = LoggerFactory.getLogger("the_emerald_standard_transactions");

    private BankTransactionCoordinator() {
    }

    /** Transfer ordinary inventory items to cash using a removal checkpoint BEFORE bank credit. */
    static boolean creditInventory(ServerPlayer player, EconomyService economy,
            EconomyState.InventoryTransactionKind kind, String key, int amount, long proceeds) {
        if (!hasReceiptSpace(player)) return false;
        Item item = BankInventory.itemForJournalKey(key);
        if (item == null) return false;
        EconomyState.PendingInventoryTransaction transaction = economy.prepareInventoryCredit(
                player.getUUID(), kind, key, amount, BankInventory.countItems(player, item), proceeds, true);
        if (transaction == null) return false;
        try {
            setReceipt(player, transaction.transactionId, 0);
            if (!BankInventory.removeItems(player, item, amount)) {
                reconcile(player, economy);
                return false;
            }
            setReceipt(player, transaction.transactionId, -amount);
            if (!savePlayer(player)) {
                suspend(player, "Could not save the inventory removal; your transaction is protected by recovery.");
                return false;
            }
            if (!economy.commitPreparedInventoryCredit(player.getUUID(), transaction.transactionId)) {
                reconcile(player, economy);
                return false;
            }
            if (!economy.completeInventoryTransactionAfterVerifiedPlayerSave(
                    player.getUUID(), transaction.transactionId)) {
                suspend(player, "Could not finalize the deposit; your bank credit is protected by recovery.");
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Inventory credit interrupted for {}; journal retained", player.getUUID(), exception);
            suspend(player, "Inventory transfer interrupted; reconnect to recover it.");
            return false;
        }
    }

    /** Returns delivered count, or -1 if a durable recovery is still required. */
    static int withdrawInventory(ServerPlayer player, EconomyService economy, int amount) {
        if (!hasReceiptSpace(player)) return -1;
        EconomyState.PendingInventoryTransaction transaction = economy.beginInventoryWithdrawal(
                player.getUUID(), amount, BankInventory.countItems(player,
                        net.minecraft.world.item.Items.EMERALD), true);
        if (transaction == null) return -1;
        try {
            setReceipt(player, transaction.transactionId, 0);
            int remaining = BankInventory.insertItems(player, net.minecraft.world.item.Items.EMERALD, amount);
            int delivered = amount - remaining;
            setReceipt(player, transaction.transactionId, delivered);
            // Recovery checkpoints delivered items first, refunds only the undelivered remainder,
            // then clears the journal. It never guesses from the player's current item count.
            return reconcile(player, economy).recovered() ? delivered : -1;
        } catch (RuntimeException exception) {
            LOGGER.error("Withdrawal interrupted for {}; journal retained", player.getUUID(), exception);
            suspend(player, "Withdrawal interrupted; reconnect to recover it.");
            return -1;
        }
    }

    private static boolean hasReceiptSpace(ServerPlayer player) {
        return player.entityTags().stream().filter(tag -> !tag.startsWith(InventoryReceipt.PREFIX)).count() < 1024;
    }

    private static void setReceipt(ServerPlayer player, UUID transactionId, int applied) {
        for (String tag : java.util.Set.copyOf(player.entityTags())) {
            if (tag.startsWith(InventoryReceipt.PREFIX)) player.removeTag(tag);
        }
        if (!player.addTag(InventoryReceipt.encode(transactionId, applied))) {
            throw new IllegalStateException("No space for inventory transaction receipt");
        }
    }

    /** Saves inventory + receipt together before clearing the bank journal. */
    public static boolean savePlayerAndComplete(ServerPlayer player, EconomyService economy, UUID transactionId) {
        if (!savePlayer(player)) return false;
        return economy.completeInventoryTransactionAfterVerifiedPlayerSave(player.getUUID(), transactionId);
    }

    public static RecoveryResult reconcile(ServerPlayer player, EconomyService economy) {
        RecoveryResult result;
        try {
            result = reconcileChecked(player, economy);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not reconcile inventory transaction for {}", player.getUUID(), exception);
            result = RecoveryResult.failed(exception.getMessage());
        }
        // No gameplay (death, inventory moves, dimension respawn) with an unresolved receipt.
        if (result.found() && !result.recovered()) suspend(player, result.error());
        return result;
    }

    private static RecoveryResult reconcileChecked(ServerPlayer player, EconomyService economy) {
        EconomyState.PendingInventoryTransaction transaction = economy.pendingInventoryTransaction(player.getUUID());
        if (transaction == null) return RecoveryResult.none();
        Item item = BankInventory.itemForJournalKey(transaction.itemKey);
        if (item == null) return RecoveryResult.failed("Unknown journal item; transaction retained");
        int applied = InventoryReceipt.applied(player.entityTags(), transaction.transactionId);
        int correction = InventoryReceipt.correction(transaction, applied);
        if (transaction.stage == EconomyState.InventoryTransactionStage.PREPARED) {
            int remainder = correction == 0 ? 0 : BankInventory.restoreItems(player, item, correction);
            int restored = correction - remainder;
            setReceipt(player, transaction.transactionId, applied + restored);
            if (!savePlayer(player)) return RecoveryResult.failed("Could not checkpoint the restored inventory");
            if (remainder > 0) {
                // The receipt still records the removed remainder; never clear it or drop items.
                return RecoveryResult.failed("Inventory restoration has no room; transaction retained for recovery");
            }
            if (!economy.cancelPreparedInventoryTransaction(player.getUUID(), transaction.transactionId)) {
                return RecoveryResult.failed("Inventory restored; journal cleanup still pending");
            }
            return RecoveryResult.recovered(transaction.transactionId, restored, true);
        }
        if (!savePlayer(player)) return RecoveryResult.failed("Could not checkpoint inventory delivery");
        if (transaction.kind == EconomyState.InventoryTransactionKind.WITHDRAWAL && correction > 0) {
            if (!economy.reducePendingWithdrawal(player.getUUID(), transaction.transactionId, correction)) {
                return RecoveryResult.failed("Undelivered withdrawal refund still pending");
            }
            if (economy.pendingInventoryTransaction(player.getUUID()) == null) {
                return RecoveryResult.recovered(transaction.transactionId, 0, false);
            }
        }
        if (!economy.completeInventoryTransactionAfterVerifiedPlayerSave(player.getUUID(), transaction.transactionId)) {
            return RecoveryResult.failed("Inventory checkpoint saved; journal cleanup still pending");
        }
        return RecoveryResult.recovered(transaction.transactionId, 0, false);
    }

    private static void suspend(ServerPlayer player, String reason) {
        LOGGER.error("Banking paused for {}: {}", player.getUUID(), reason);
        if (player.connection != null && player.connection.isAcceptingMessages()) {
            player.connection.disconnect(Component.literal(PREFIX + reason
                    + "\nNo further spending was performed. Reconnect to recover; contact the server owner if it persists."));
        }
    }


    static boolean checkpointPlayer(ServerPlayer player) { return savePlayer(player); }

    private static boolean savePlayer(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        CompoundTag expected;
        try {
            expected = serializedPlayer(player);
        } catch (Exception exception) {
            LOGGER.error(
                    "Could not serialize player {} for a bank checkpoint; the journal remains active",
                    player.getUUID(),
                    exception);
            return false;
        }
        return writeAndVerifyPlayerData(
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR),
                player.getStringUUID(),
                expected);
    }

    static boolean writeAndVerifyPlayerData(
            Path playerDirectory, String fileName, CompoundTag expected) {
        Path temporary = null;
        try {
            Files.createDirectories(playerDirectory);
            Path playerData = playerDirectory.resolve(fileName + ".dat");
            Path oldPlayerData = playerDirectory.resolve(fileName + ".dat_old");
            temporary = Files.createTempFile(playerDirectory, fileName + "-", ".dat");
            NbtIo.writeCompressed(expected, temporary);
            if (!Util.safeReplaceOrMoveFile(
                    playerData, temporary, oldPlayerData, false)) {
                LOGGER.error(
                        "Could not replace player data for bank checkpoint {}; the journal remains active",
                        fileName);
                return false;
            }
            CompoundTag persisted = NbtIo.readCompressed(
                    playerData, NbtAccounter.unlimitedHeap());
            // Vanilla's PlayerDataStorage logs and swallows write failures. Mirror its one-player
            // safe replacement through the checked public primitive, then compare the transaction-
            // relevant payload before clearing the durable economy journal.
            if (!inventoryMatches(expected, persisted)) {
                LOGGER.error(
                        "Player inventory readback did not match bank checkpoint {}; the journal remains active",
                        fileName);
                return false;
            }
            forceFile(playerData);
            forceDirectoryBestEffort(playerDirectory);
            return true;
        } catch (Exception exception) {
            LOGGER.error(
                    "Could not complete player-data bank checkpoint {}; the journal remains active",
                    fileName,
                    exception);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // A failed cleanup never turns an otherwise verified player save into failure.
                }
            }
        }
    }

    private static void forceFile(Path path) throws Exception {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectoryBestEffort(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (Exception ignored) {
            // Some providers (notably Windows) cannot open directories as FileChannels. The
            // synchronized file writes and post-rename target-file force remain mandatory.
        }
    }

    private static CompoundTag serializedPlayer(ServerPlayer player) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(
                problems, player.registryAccess());
        player.saveWithoutId(output);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Player data serialization reported a problem");
        }
        return output.buildResult();
    }

    static boolean inventoryMatches(CompoundTag expected, CompoundTag persisted) {
        return expected != null
                && persisted != null
                && Objects.equals(expected.get("Inventory"), persisted.get("Inventory"))
                && Objects.equals(expected.get("Tags"), persisted.get("Tags"));
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
