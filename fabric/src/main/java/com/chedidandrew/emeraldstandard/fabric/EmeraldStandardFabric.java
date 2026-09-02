package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.minecraft.BankerAccess;
import com.chedidandrew.emeraldstandard.minecraft.BankerIntegrationSelfTest;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenu;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenus;
import com.chedidandrew.emeraldstandard.minecraft.BankTransactionCoordinator;
import com.chedidandrew.emeraldstandard.minecraft.BankingOperations;
import com.chedidandrew.emeraldstandard.minecraft.EmeraldCommands;
import com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig;
import com.chedidandrew.emeraldstandard.minecraft.VillageBankManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmeraldStandardFabric implements ModInitializer {
    public static final String MOD_ID = "the_emerald_standard";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final EconomyService ECONOMY = new EconomyService();

    @Override
    public void onInitialize() {
        MenuType<BankerMenu> bankerMenu = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(MOD_ID, "banker"),
                new MenuType<>(BankerMenu::new, FeatureFlagSet.of()));
        BankerMenus.setType(bankerMenu);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                EmeraldConfig.load(server.getWorldPath(LevelResource.DATA));
                ECONOMY.start(
                        server.getWorldPath(LevelResource.DATA),
                        server.overworld().getSeed(),
                        server.overworld().getGameTime());
                LOGGER.info(
                        "The Emerald Standard economy started with {} catch-up day(s) remaining",
                        ECONOMY.catchUpDaysRemaining());
                if (Boolean.getBoolean("the_emerald_standard.integrationSmoke")) {
                    BankerIntegrationSelfTest.run(server.overworld());
                    LOGGER.info("The Emerald Standard Banker integration self-test passed");
                }
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
            VillageBankManager.tick(server, ECONOMY);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                recover(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            recover(handler.player);
            BankingOperations.forgetPlayer(handler.player.getUUID());
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer
                    && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                var accessPoint = VillageBankManager.bankAccessPoint(
                        serverLevel, hitResult.getBlockPos(), ECONOMY);
                if (accessPoint != null) {
                    BankerAccess.openAt(serverPlayer, ECONOMY, accessPoint);
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || !BankerAccess.isBanker(entity)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                BankerAccess.open(serverPlayer, ECONOMY, entity);
            }
            return InteractionResult.SUCCESS;
        });

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
