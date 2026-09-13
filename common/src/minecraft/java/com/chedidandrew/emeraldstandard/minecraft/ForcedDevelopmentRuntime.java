package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.server.MinecraftServer;

/** One budget shared by progressive Banks and ordinary projects on the owning server thread. */
final class ForcedDevelopmentRuntime {
    private static final ForcedDevelopmentWorkBudget BUDGET = new ForcedDevelopmentWorkBudget();
    static int claim(MinecraftServer server) {
        BUDGET.begin(server, server.overworld().getGameTime(), System.nanoTime(), BackgroundSurveyBudget.lagging(server));
        return BUDGET.claim(System.nanoTime());
    }
    static boolean hasTime() { return BUDGET.hasTime(System.nanoTime()); }
    static void reset() { BUDGET.reset(); }
}
