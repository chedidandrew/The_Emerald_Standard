package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
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

    /** Durably saves this player's inventory and then clears a BANK_COMMITTED journal record. */
    public static boolean savePlayerAndComplete(
            ServerPlayer player,
            EconomyService economy,
            UUID transactionId) {
        if (!savePlayer(player)) {
            return false;
        }
        return economy.completeInventoryTransactionAfterVerifiedPlayerSave(
                player.getUUID(), transactionId);
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
                int remainder = BankInventory.restoreItems(player, item, missing);
                corrected = missing - remainder;
                if (remainder > 0) {
                    if (!savePlayer(player)) {
                        return RecoveryResult.failed(
                                "Could not save player data after a partial inventory restoration");
                    }
                    return RecoveryResult.failed(
                            remainder + " item(s) remain protected by the journal; free inventory space and recover again");
                }
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
                int remainder = BankInventory.restoreItems(player, item, missing);
                corrected = missing - remainder;
                if (remainder > 0) {
                    if (!savePlayer(player)) {
                        return RecoveryResult.failed(
                                "Could not save player data after a partial inventory restoration");
                    }
                    return RecoveryResult.failed(
                            remainder + " item(s) remain protected by the journal; free inventory space and recover again");
                }
            }
        }

        if (!savePlayer(player)) {
            return RecoveryResult.failed(
                    "Could not save player data while completing a committed transaction");
        }
        if (!economy.completeInventoryTransactionAfterVerifiedPlayerSave(
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
                && Objects.equals(expected.get("Inventory"), persisted.get("Inventory"));
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
