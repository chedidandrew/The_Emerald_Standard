package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.ConstructionCatchUp;
import net.minecraft.server.MinecraftServer;

/** Both physical builders share one night observer and one bounded extra-work allowance. */
final class ConstructionTimeRuntime {
    private static final ConstructionCatchUp CATCH_UP = new ConstructionCatchUp();
    private static long pulseTick = Long.MIN_VALUE;
    private static long deadline;

    static void reset() { CATCH_UP.reset(); pulseTick = Long.MIN_VALUE; deadline = 0; }

    static void tick(MinecraftServer server, EmeraldConfig config) {
        long game = server.overworld().getGameTime();
        observe(game, server.overworld().getOverworldClockTime(), config);
    }

    static void observe(long game, long daylight, EmeraldConfig config) {
        if (game != pulseTick) { pulseTick = game; deadline = 0; }
        CATCH_UP.observe(game, daylight, config.villageConstructionBlocksPerSecond(),
                !config.forcedVillageDevelopment()
                        && (config.villageBanksEnabled() || config.villageVisualProgressionEnabled()),
                config.constructionAllowance(game) > 0);
    }

    static int allowance(String site, long game, EmeraldConfig config) {
        int normal = config.constructionAllowance(game);
        if (normal <= 0) return 0;
        int extra = CATCH_UP.claim(site, game);
        if (extra == 0) return normal;
        return normal + (deadline == 0 || System.nanoTime() < deadline ? extra : 0);
    }

    static boolean hasTime() {
        // Start after ordinary preflight/base placement, so expensive template validation
        // cannot consume every site's bonus before its first extra cell is attempted.
        if (deadline == 0) deadline = System.nanoTime() + 3_000_000L;
        return System.nanoTime() < deadline;
    }
}
