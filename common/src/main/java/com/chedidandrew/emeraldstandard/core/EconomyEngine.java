package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Pure deterministic economy model shared by Fabric and NeoForge. */
public final class EconomyEngine {
    public static final int DAYS_PER_YEAR = 365;
    private static final double TRADING_DAYS = 365.0;

    public enum Regime {
        EXPANSION(0.105, 0.14), BULL(0.18, 0.18), BOOM(0.27, 0.28),
        STAGNATION(0.015, 0.10), RECESSION(-0.07, 0.23), CRASH(-0.28, 0.42), RECOVERY(0.30, 0.28);
        public final double annualReturn, annualVolatility;
        Regime(double r, double v) { annualReturn = r; annualVolatility = v; }
    }

    public record Asset(String ticker, String name, double beta, double alpha, double idioVol) {}
    public static final List<Asset> ASSETS = List.of(
        new Asset("VILX", "Villager Exchange Index", 1.00, 0.000, 0.04),
        new Asset("RSDN", "Redstone Dynamics", 1.35, 0.025, 0.24),
        new Asset("DPMN", "Deepdelve Mining", 1.18, 0.010, 0.27),
        new Asset("NSPC", "Nether Spice Company", 1.28, 0.020, 0.31),
        new Asset("ENDR", "Ender Freight & Logistics", 1.12, 0.012, 0.22),
        new Asset("GLDH", "Golden Harvest Cooperative", 0.68, -0.010, 0.14),
        new Asset("POTN", "Potionworks Laboratories", 0.96, 0.010, 0.21),
        new Asset("IRNG", "Iron Golem Security", 0.76, -0.005, 0.15),
        new Asset("MCRT", "Minecart Transit", 0.88, 0.000, 0.17)
    );

    private EconomyEngine() {}

    public static Regime initialRegime(long seed) {
        return unit(seed ^ 0x454D4552414C44L) < 0.72 ? Regime.EXPANSION : Regime.STAGNATION;
    }

    public static Regime nextRegime(Regime current, long seed, long day) {
        double u = unit(mix(seed, day, 0x524547494D45L));
        return switch (current) {
            case EXPANSION -> pick(u, new Regime[]{Regime.EXPANSION, Regime.BULL, Regime.STAGNATION, Regime.RECESSION}, new double[]{.925,.035,.030,.010});
            case BULL -> pick(u, new Regime[]{Regime.BULL, Regime.EXPANSION, Regime.BOOM, Regime.RECESSION}, new double[]{.925,.035,.030,.010});
            case BOOM -> pick(u, new Regime[]{Regime.BOOM, Regime.BULL, Regime.CRASH}, new double[]{.91,.07,.02});
            case STAGNATION -> pick(u, new Regime[]{Regime.STAGNATION, Regime.EXPANSION, Regime.RECESSION}, new double[]{.94,.045,.015});
            case RECESSION -> pick(u, new Regime[]{Regime.RECESSION, Regime.RECOVERY, Regime.CRASH, Regime.STAGNATION}, new double[]{.86,.10,.01,.03});
            case CRASH -> pick(u, new Regime[]{Regime.CRASH, Regime.RECESSION, Regime.RECOVERY}, new double[]{.70,.12,.18});
            case RECOVERY -> pick(u, new Regime[]{Regime.RECOVERY, Regime.EXPANSION, Regime.BULL}, new double[]{.84,.12,.04});
        };
    }

    public static double marketReturn(Regime regime, long seed, long day) {
        double mu = Math.log1p(regime.annualReturn) / TRADING_DAYS + Math.log1p(0.19) / TRADING_DAYS;
        double sigma = regime.annualVolatility / Math.sqrt(TRADING_DAYS);
        double shock = gaussian(mix(seed, day, 0x4D41524B4554L));
        // Rare fat tails. Crash regimes naturally amplify these further.
        double rare = unit(mix(seed, day, 0x53484F434BL));
        if (rare < 0.0012) shock -= 3.5 + 2.5 * unit(mix(seed, day, 91));
        else if (rare > 0.9992) shock += 3.0 + 2.0 * unit(mix(seed, day, 92));
        return Math.exp(mu - 0.5 * sigma * sigma + sigma * shock) - 1.0;
    }

    public static double assetReturn(Asset asset, double marketReturn, long seed, long day) {
        if (asset.ticker.equals("VILX")) return marketReturn;
        double alphaDaily = Math.log1p(asset.alpha) / TRADING_DAYS;
        double idio = asset.idioVol / Math.sqrt(TRADING_DAYS) * gaussian(mix(seed, day, asset.ticker.hashCode()));
        return clamp(asset.beta * marketReturn + alphaDaily + idio, -0.80, 0.80);
    }

    public static double savingsAnnualRate(Regime r) {
        return switch (r) { case BOOM -> .045; case BULL -> .038; case RECESSION, CRASH -> .018; case STAGNATION -> .026; case RECOVERY -> .030; default -> .032; };
    }
    public static double cdAnnualRate(Regime r) { return savingsAnnualRate(r) + .020; }
    public static double villagerLoanAnnualYield(Regime r) {
        return switch (r) { case CRASH -> .20; case RECESSION -> .16; case BOOM -> .12; default -> .105; };
    }

    public static double compoundDaily(double principal, double annualRate) {
        return principal * (Math.pow(1.0 + annualRate, 1.0 / TRADING_DAYS) - 1.0);
    }

    private static Regime pick(double u, Regime[] r, double[] p) {
        double c = 0; for (int i=0;i<r.length;i++) { c += p[i]; if (u < c) return r[i]; } return r[r.length-1];
    }
    private static double gaussian(long x) {
        double u1 = Math.max(1e-12, unit(x)); double u2 = unit(x ^ 0x9E3779B97F4A7C15L);
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
    private static long mix(long seed, long day, long salt) {
        long z = seed ^ (day * 0x9E3779B97F4A7C15L) ^ salt;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; return z ^ (z >>> 31);
    }
    private static double unit(long x) { return ((x >>> 11) * 0x1.0p-53); }
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }
}
