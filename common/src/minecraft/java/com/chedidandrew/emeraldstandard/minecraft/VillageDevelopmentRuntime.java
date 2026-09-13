package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import net.minecraft.server.MinecraftServer;

/** Shared loader entry point: keep mode changes and construction queue priority in sync. */
public final class VillageDevelopmentRuntime {
    private VillageDevelopmentRuntime() { }

    public static void tick(MinecraftServer server, EconomyService economy) {
        VanillaVillageBuildings.tick(server);
        boolean forced = EmeraldConfig.current().forcedVillageDevelopment();
        // The Bank can now run first, including the first tick after switching modes.
        economy.configureForcedVillageDevelopment(forced);
        if (!forced) {
            var config = EmeraldConfig.current();
            ConstructionTimeRuntime.tick(server, config);
            economy.configureVillageProsperity(config.villageProsperitySimulationEnabled(),
                    config.villageVisualProgressionEnabled(), config.villageMarketIntegrationEnabled(),
                    config.villageAutomaticRecoveryEnabled());
            ConstructionTimeRuntime.runQueues(() -> VillageProsperityManager.tick(server, economy),
                    () -> VillageBankManager.tick(server, economy));
            return;
        }
        ForcedDevelopmentWorkBudget.runQueues(forced, server.overworld().getGameTime(),
                () -> VillageProsperityManager.tick(server, economy),
                () -> VillageBankManager.tick(server, economy));
    }
}
