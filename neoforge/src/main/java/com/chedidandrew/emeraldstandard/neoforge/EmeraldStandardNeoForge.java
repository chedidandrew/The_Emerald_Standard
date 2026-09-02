package com.chedidandrew.emeraldstandard.neoforge;

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
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(EmeraldStandardNeoForge.MOD_ID)
public final class EmeraldStandardNeoForge {
    public static final String MOD_ID = "the_emerald_standard";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EconomyService ECONOMY = new EconomyService();

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<BankerMenu>> BANKER_MENU =
            MENUS.register("banker", () -> {
                MenuType<BankerMenu> type =
                        new MenuType<>(BankerMenu::new, FeatureFlags.DEFAULT_FLAGS);
                BankerMenus.setType(type);
                return type;
            });

    public EmeraldStandardNeoForge(IEventBus modEventBus) {
        MENUS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            var server = event.getServer();
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
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        if (!ECONOMY.saveNow(server.overworld().getGameTime())) {
            LOGGER.error("Could not save The Emerald Standard economy: {}",
                    ECONOMY.lastError());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!ECONOMY.tick(event.getServer().overworld().getGameTime())) {
            LOGGER.error("Could not advance or save The Emerald Standard economy: {}",
                    ECONOMY.lastError());
        }
        VillageBankManager.tick(event.getServer(), ECONOMY);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recover(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recover(player);
            BankingOperations.forgetPlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var accessPoint = VillageBankManager.bankAccessPoint(
                player.serverLevel(), event.getPos(), ECONOMY);
        if (accessPoint == null) {
            return;
        }
        BankerAccess.openAt(player, ECONOMY, accessPoint);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!BankerAccess.isBanker(event.getTarget())) {
            return;
        }
        if (!event.getEntity().level().isClientSide()
                && event.getEntity() instanceof ServerPlayer player) {
            BankerAccess.open(player, ECONOMY, event.getTarget());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EmeraldCommands.register(event.getDispatcher(), ECONOMY);
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
