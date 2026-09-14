package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import net.minecraft.server.MinecraftServer;

/** Shared loader entry point: keep mode changes and construction queue priority in sync. */
public final class VillageDevelopmentRuntime {
    private VillageDevelopmentRuntime() { }

    public static void tick(MinecraftServer server, EconomyService economy) {
        DebugWork.run("catalog", () -> VanillaVillageBuildings.tick(server));
        boolean forced = EmeraldConfig.current().forcedVillageDevelopment();
        // The Bank can now run first, including the first tick after switching modes.
        economy.configureForcedVillageDevelopment(forced);
        if (!forced) {
            var config = EmeraldConfig.current();
            ConstructionTimeRuntime.tick(server, config);
            economy.configureVillageProsperity(config.villageProsperitySimulationEnabled(),
                    config.villageVisualProgressionEnabled(), config.villageMarketIntegrationEnabled(),
                    config.villageAutomaticRecoveryEnabled());
            ConstructionTimeRuntime.runQueues(() -> DebugWork.run("village", () -> VillageProsperityManager.tick(server, economy)),
                    () -> DebugWork.run("bank", () -> VillageBankManager.tick(server, economy)));
            return;
        }
        ForcedDevelopmentWorkBudget.runQueues(forced, server.overworld().getGameTime(),
                () -> DebugWork.run("village", () -> VillageProsperityManager.tick(server, economy)),
                () -> DebugWork.run("bank", () -> VillageBankManager.tick(server, economy)));
    }
}
