package com.chedidandrew.emeraldstandard.core;

/** Positive fundamentals, not promised investor returns. Pure seeded paths survive reloads. */
public final class InvestmentGrowth {
    private InvestmentGrowth() {}
    public static double draw(double quantile) {
        double q = Math.max(0, Math.min(Math.nextDown(1.0), quantile));
        if (q < .70) return .01 + .03 * q / .70;
        if (q < .93) return .04 + .04 * (q - .70) / .23;
        if (q < .99) return .08 + .04 * (q - .93) / .06;
        return .12 + .03 * (q - .99) / .01;
    }
    public static double target(long seed, long day, String symbol) {
        long period = Math.floorDiv(Math.max(0, day), 181);
        double t = Math.floorMod(Math.max(0, day), 181) / 181.0;
        t = t * t * (3 - 2 * t);
        double power = switch (symbol) {
            case "coal", "diamond" -> 1.5;
            case "copper", "redstone", "RSDN", "VENT" -> .82;
            case "lapis", "netherite", "GLDH", "IRNG" -> 1.15;
            default -> 1.0;
        };
        double a = draw(StrictMath.pow(unit(seed, period, symbol), power));
        double b = draw(StrictMath.pow(unit(seed, period + 1, symbol), power));
        return a + (b - a) * t;
    }
    public static double turbulence(long seed, long day) {
        // Multi-week volatility clusters; neither saving nor opening a screen samples RNG.
        return unit(seed ^ 0x54555242554CL, day / 21, "weather") > .90 ? 1.8 : 1.0;
    }
    static double unit(long seed, long period, String symbol) {
        long x = seed ^ (period * 0x9e3779b97f4a7c15L) ^ symbol.hashCode();
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return ((x ^ (x >>> 31)) >>> 11) * 0x1.0p-53;
    }
}
