package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.ConstructionCatchUp;
import com.chedidandrew.emeraldstandard.core.ConstructionWorkBudget;
import net.minecraft.server.MinecraftServer;

/** Both physical builders share one night observer and one bounded extra-work allowance. */
final class ConstructionTimeRuntime {
    private static final ConstructionCatchUp CATCH_UP = new ConstructionCatchUp();
    private static final ConstructionWorkBudget WORK = new ConstructionWorkBudget();

    static void reset() { CATCH_UP.reset(); WORK.reset(); }

    static void tick(MinecraftServer server, EmeraldConfig config) {
        long game = server.overworld().getGameTime();
        observe(game, server.overworld().getOverworldClockTime(), config);
    }

    static void observe(long game, long daylight, EmeraldConfig config) {
        WORK.begin(game, !config.forcedVillageDevelopment() && config.constructionAllowance(game) > 0);
        CATCH_UP.observe(game, daylight, config.villageConstructionBlocksPerSecond(),
                !config.forcedVillageDevelopment()
                        && (config.villageBanksEnabled() || config.villageVisualProgressionEnabled()),
                config.constructionAllowance(game) > 0);
    }

    static int allowance(String site, long game, EmeraldConfig config) {
        int normal = config.constructionAllowance(game);
        if (normal <= 0) return 0;
        int extra = CATCH_UP.claim(site, game);
        return WORK.claim(normal + extra, System.nanoTime());
    }

    static void runQueues(Runnable villages, Runnable banks) {
        // Dispatch the preferred family first so two cheap queues can both work this pulse,
        // instead of needlessly denying the caller that happened to run first.
        if (WORK.preferred() == ConstructionWorkBudget.Lane.BANK) { banks.run(); villages.run(); }
        else { villages.run(); banks.run(); }
    }
    static boolean enter(ConstructionWorkBudget.Lane lane) { return WORK.enter(lane, System.nanoTime()); }
    static boolean hasTime() { return WORK.hasTime(System.nanoTime()); }
}
