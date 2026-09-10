package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.server.MinecraftServer;

/** Lag reduces optional surveys, never a site's independent two-block-per-second allowance. */
final class BackgroundSurveyBudget {
    private static MinecraftServer current;
    private static long previousNanos;
    private static double tickMillis=50;
    static void tick(MinecraftServer server) {
        long now=System.nanoTime();
        if(current==server && previousNanos>0) tickMillis=.95*tickMillis+.05*((now-previousNanos)/1_000_000.0);
        else { current=server; tickMillis=50; }
        previousNanos=now;
    }
    static int cells(MinecraftServer server,int normal) {
        return current==server && tickMillis>65?Math.max(256,normal/4):normal;
    }
}
