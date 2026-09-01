package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.minecraft.BankTransactionCoordinator;
import com.chedidandrew.emeraldstandard.minecraft.EmeraldCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmeraldStandardFabric implements ModInitializer {
    public static final String MOD_ID = "the_emerald_standard";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final EconomyService ECONOMY = new EconomyService();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ECONOMY.start(
                        server.getWorldPath(LevelResource.DATA),
                        server.overworld().getSeed(),
                        server.overworld().getGameTime());
                LOGGER.info(
                        "The Emerald Standard economy started with {} catch-up day(s) remaining",
                        ECONOMY.catchUpDaysRemaining());
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Could not start The Emerald Standard economy", exception);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (!ECONOMY.saveNow(server.overworld().getGameTime())) {
                LOGGER.error("Could not save The Emerald Standard economy: {}",
                        ECONOMY.lastError());
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ECONOMY.tick(server.overworld().getGameTime())) {
                LOGGER.error("Could not advance or save The Emerald Standard economy: {}",
                        ECONOMY.lastError());
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                recover(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                recover(handler.player));

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        EmeraldCommands.register(dispatcher, ECONOMY));
    }

    private static void recover(ServerPlayer player) {
        BankTransactionCoordinator.RecoveryResult result =
                BankTransactionCoordinator.reconcile(player, ECONOMY);
        if (result.found() && !result.recovered()) {
            LOGGER.error(
                    "Could not recover inventory transaction for {}: {}",
                    player.getGameProfile().name(),
                    result.error());
        }
    }
}
