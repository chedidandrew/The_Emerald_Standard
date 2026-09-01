package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.minecraft.EmeraldCommands;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(EmeraldStandardNeoForge.MOD_ID)
public final class EmeraldStandardNeoForge {
    public static final String MOD_ID = "the_emerald_standard";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EconomyService ECONOMY = new EconomyService();

    public EmeraldStandardNeoForge() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            var server = event.getServer();
            ECONOMY.start(
                    server.getWorldPath(LevelResource.DATA),
                    server.overworld().getSeed(),
                    server.overworld().getGameTime());
            LOGGER.info("The Emerald Standard economy started");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not start The Emerald Standard economy", exception);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (!ECONOMY.saveNow()) {
            LOGGER.error("Could not save The Emerald Standard economy: {}",
                    ECONOMY.lastError());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!ECONOMY.tick(event.getServer().overworld().getGameTime())) {
            LOGGER.error("Could not advance The Emerald Standard economy: {}",
                    ECONOMY.lastError());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EmeraldCommands.register(event.getDispatcher(), ECONOMY);
    }
}
