package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

/** Listing migration, deterministic pricing and measured differences rather than renamed clones. */
public final class InvestmentDiversityRegressionTest {
    public static void main(String[] args) throws Exception {
        require(EconomyEngine.ASSETS.stream().map(EconomyEngine.Asset::ticker).distinct().count() == 23,
                "Duplicate or missing listings");
        require(EconomyEngine.ASSETS.subList(0, 9).stream().map(EconomyEngine.Asset::ticker).toList()
                .equals(List.of("VILX", "RSDN", "DPMN", "NSPC", "ENDR", "GLDH", "POTN", "IRNG", "MCRT")),
                "Legacy listing order changed");
        testDrivers();
        testMeasuredDiversity();
        testMigrationAndRestart();
        testEveryListingTrades();
        System.out.println("PASS InvestmentDiversityRegressionTest");
    }

    private static void testEveryListingTrades() throws Exception {
        Path root = Files.createTempDirectory("tes-listing-trades-");
        try {
            var player = RegressionTestSupport.PLAYER;
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 190L, 0L, 0L);
            require(service.deposit(player, 2000), "Funding failed");
            for (var listing : EconomyEngine.ASSETS) {
                require(service.buy(player, listing.ticker(), 50), "Cannot buy " + listing.ticker());
                var position = service.portfolioAnalyticsSnapshot(player).positions().get(listing.ticker());
                require(position.costBasisMicro() == 50 * EconomyState.MICRO && position.shares() > 0,
                        "Wrong listing cost basis");
                require(service.sell(player, listing.ticker(), position.shares() / 4), "Quarter sale failed");
            }
            EconomyService restored = new EconomyService();
            restored.startWithSeed(root, 0, 0, 0);
            for (var listing : EconomyEngine.ASSETS) {
                var position = restored.portfolioAnalyticsSnapshot(player).positions().get(listing.ticker());
                require(Math.abs(position.costBasisMicro() - 37.5 * EconomyState.MICRO) <= 1,
                        "Quarter sale basis did not persist");
                require(restored.sell(player, listing.ticker(), position.shares()), "Full sale failed after restart");
            }
            require(restored.portfolioAnalyticsSnapshot(player).realizedGainMicro() < 0,
                    "Immediate round trips created free money instead of paying spread");
        } finally { RegressionTestSupport.deleteTree(root); }
    }

    private static EconomyEngine.Asset asset(String ticker) {
        return EconomyEngine.ASSETS.stream().filter(a -> a.ticker().equals(ticker)).findFirst().orElseThrow();
    }

    private static double priceReturn(String ticker, double market, long seed, long day,
            EconomyEngine.Regime previous, EconomyEngine.Regime current) {
        return EconomyEngine.assetReturn(asset(ticker), market, seed, day, 0, previous, current);
    }

    private static void testDrivers() {
        var boom = EconomyEngine.Regime.BOOM;
        var crash = EconomyEngine.Regime.CRASH;
        double rising = priceReturn("TREA", 0, 72, 50, crash, boom);
        double steady = priceReturn("TREA", 0, 72, 50, boom, boom);
        double falling = priceReturn("TREA", 0, 72, 50, boom, crash);
        require(rising < steady && falling > steady, "Treasury duration does not react to rate changes");
        require(priceReturn("BRCK", 0, 72, 50, boom, boom)
                > priceReturn("BRCK", 0, 72, 50, crash, crash), "Construction is not cyclical");
        require(priceReturn("AURM", -0.03, 72, 50, boom, boom)
                > priceReturn("AURM", 0.03, 72, 50, boom, boom), "Reserve business is not defensive");
        var scare = EconomyEngine.MarketEvent.VILLAGER_CREDIT_SCARE;
        require(EconomyEngine.eventAssetReturn(scare, "AURM") > 0
                && EconomyEngine.eventAssetReturn(scare, "BRCK") < 0, "Event responses still uniform");
        int successes = 0, failures = 0;
        var vent = asset("VENT");
        var plain = new EconomyEngine.Asset("VENT_NO_JUMPS", vent.name(), vent.sector(), 0, 0, 0);
        // Isolate the specialist cycle/jumps using zero-noise metadata and zero market.
        var fishOnly = new EconomyEngine.Asset("FISH", "", "", 0, 0, 0);
        double seasonalLog = 0;
        boolean seasonalUp = false, seasonalDown = false;
        double baseline = EconomyEngine.assetReturn(plain, 0, 72, 1);
        for (int day = 1; day <= 20_000; day++) {
            var jumpsOnly = new EconomyEngine.Asset("VENT", "", "", 0, 0, 0);
            double jump = EconomyEngine.assetReturn(jumpsOnly, 0, 72, day);
            if (jump > 0.07) successes++;
            if (jump < -0.08) failures++;
            // Remove this company's evolving fundamental drift before measuring its bounded seasonal cycle.
            double cycle = Math.log1p(EconomyEngine.assetReturn(fishOnly, 0, 72, day))
                    - Math.log1p(InvestmentGrowth.target(72,day,"FISH")) / EconomyEngine.DAYS_PER_YEAR;
            seasonalLog += cycle;
            seasonalUp |= cycle > 0;
            seasonalDown |= cycle < 0;
        }
        require(successes > 20 && failures > 15, "Expeditions lack both outcome tails");
        require(seasonalUp && seasonalDown && Math.abs(seasonalLog) <= 0.201,
                "Fishing cycle is one-sided or accumulates an unlimited subsidy");
        require(EconomyEngine.riskBand(asset("TREA")).equals("lower")
                && EconomyEngine.riskBand(asset("VENT")).equals("very_high"), "Risk labels ignore specialist risks");
    }

    private static void testMeasuredDiversity() {
        for (String ticker : List.of("TREA", "AURM", "BRCK", "FISH", "VENT")) {
            double x = 0, y = 0, xx = 0, yy = 0, xy = 0;
            int n = 0, down = 0;
            for (int seed = 0; seed < 12; seed++) {
                var regime = EconomyEngine.initialRegime(seed);
                for (int day = 1; day <= 3650; day++) {
                    var previous = regime;
                    regime = EconomyEngine.nextRegime(regime, seed, day);
                    double market = EconomyEngine.marketReturn(regime, seed, day);
                    double value = priceReturn(ticker, market, seed, day, previous, regime);
                    require(Double.isFinite(value) && value > -1, "Invalid return");
                    require(value == priceReturn(ticker, market, seed, day, previous, regime), "Nondeterministic return");
                    x += market; y += value; xx += market * market; yy += value * value; xy += market * value;
                    n++; if (value < 0) down++;
                }
            }
            double correlation = (xy - x * y / n) / Math.sqrt((xx - x * x / n) * (yy - y * y / n));
            double volatility = Math.sqrt((yy / n - y * y / n / n) * 365);
            require(down > n / 10, "Investment misleadingly never loses");
            if (ticker.equals("AURM")) require(correlation < -0.15, "Reserve fund still follows market");
            if (ticker.equals("FISH") || ticker.equals("VENT"))
                require(Math.abs(correlation) < 0.40, "Specialist too correlated: " + ticker);
            if (ticker.equals("BRCK")) require(correlation > 0.65 && volatility > 0.30, "Construction lacks cyclicality");
            if (ticker.equals("TREA")) require(volatility < 0.08, "Treasury unexpectedly volatile");
            System.out.printf("%s market correlation %.3f, annualized daily volatility %.1f%%%n", ticker, correlation, volatility * 100);
        }
    }

    private static void testMigrationAndRestart() throws Exception {
        Path root = Files.createTempDirectory("tes-investments-");
        try {
            Path save = root.resolve("economy.properties");
            EconomyState old = EconomyState.fresh(775, 0, 0);
            old.account(RegressionTestSupport.PLAYER).shares.put("VILX", 12.5);
            for (int i = 0; i < 150; i++) old.advanceOneDay();
            old.save(save);
            Properties properties = RegressionTestSupport.readProperties(save);
            properties.setProperty("format", "26");
            for (var listing : EconomyEngine.ASSETS) if (EconomyEngine.isSpecialist(listing.ticker())) {
                properties.remove("price." + listing.ticker());
                properties.remove("history." + listing.ticker());
            }
            RegressionTestSupport.refreshChecksum(properties);
            RegressionTestSupport.writeProperties(save, properties);
            EconomyState upgraded = EconomyState.load(save, 1, 0, 0);
            require(upgraded.economicDay == old.economicDay
                    && upgraded.prices.get("VILX").equals(old.prices.get("VILX"))
                    && upgraded.priceHistory.get("VILX").equals(old.priceHistory.get("VILX"))
                    && upgraded.account(RegressionTestSupport.PLAYER).shares.get("VILX") == 12.5,
                    "Upgrade changed the existing portfolio or history");
            for (var listing : EconomyEngine.ASSETS) if (EconomyEngine.isSpecialist(listing.ticker())) {
                require(upgraded.priceHistory.get(listing.ticker()).equals(List.of(100.0)), "Invented listing history");
                upgraded.account(RegressionTestSupport.PLAYER).shares.put(listing.ticker(), 3.25);
            }
            upgraded.save(save);
            EconomyState restored = EconomyState.load(save, 99, 0, 0);
            require(restored.account(RegressionTestSupport.PLAYER).shares.equals(
                    upgraded.account(RegressionTestSupport.PLAYER).shares), "New holdings did not persist");
            for (int i = 0; i < 800; i++) { upgraded.advanceOneDay(); restored.advanceOneDay(); }
            require(upgraded.prices.equals(restored.prices) && upgraded.priceHistory.equals(restored.priceHistory),
                    "Restart changed specialist outcomes");
            // Current-format missing fields remain corruption, never silently reset a holding's price.
            properties = RegressionTestSupport.readProperties(save);
            properties.remove("price.TREA");
            RegressionTestSupport.refreshChecksum(properties);
            Path broken = root.resolve("broken.properties");
            RegressionTestSupport.writeProperties(broken, properties);
            boolean rejected = false;
            try { EconomyState.load(broken, 1, 0, 0); } catch (java.io.IOException expected) { rejected = true; }
            require(rejected, "Current save missing listing price was silently repaired");
        } finally { RegressionTestSupport.deleteTree(root); }
    }
}
