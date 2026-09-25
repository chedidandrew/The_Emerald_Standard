package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

/** Commodity listings share quotes without changing stocks, inventory, or legacy holdings. */
public final class CommodityInvestmentRegressionTest {
    public static void main(String[] args) throws Exception {
        var listings = EconomyEngine.ASSETS.stream().filter(EconomyEngine.Asset::isCommodity).toList();
        require(listings.stream().map(EconomyEngine.Asset::ticker).toList().equals(
                List.of("GOLD", "IRON", "COAL", "DIAM", "COPR", "RDST", "LAPS", "NETH")), "Commodity catalog");
        require(EconomyEngine.ASSETS.getFirst().type() == EconomyEngine.AssetType.INDEX
                && EconomyEngine.ASSETS.get(9).type() == EconomyEngine.AssetType.FUND
                && EconomyEngine.ASSETS.get(10).type() == EconomyEngine.AssetType.STOCK, "Investment labels");
        require(listings.stream().noneMatch(a -> StockIndex.CONSTITUENTS.contains(a.ticker())), "Commodities entered VILX");
        testQuotes();
        testMigration();
        testTransactions();
        System.out.println("PASS commodity investments: quotes, categories, migration, rounding, invalid requests and restart");
    }

    private static void testQuotes() throws Exception {
        var state = EconomyState.fresh(909, 0, 0);
        for (int day = 0; day < 730; day++) {
            for (var a : EconomyEngine.ASSETS) if (a.isCommodity()) {
                require(state.prices.get(a.ticker()).equals(state.commodityPrices.get(a.commodityId())),
                        "Investment differs from underlying: " + a.ticker());
                require(state.priceHistory.get(a.ticker()).equals(state.commodityHistory.get(a.commodityId())),
                        "Quote history differs: " + a.ticker());
                require(state.prices.get(a.ticker()) > 0 && Double.isFinite(state.prices.get(a.ticker())), "Invalid quote");
            }
            state.advanceOneDay();
        }
        state.validate();
        require(!state.priceHistory.get("IRON").equals(state.priceHistory.get("COPR")), "Cloned commodity prices");
        var gold = EconomyEngine.COMMODITIES.stream().filter(c -> c.id().equals("gold")).findFirst().orElseThrow();
        var iron = EconomyEngine.COMMODITIES.stream().filter(c -> c.id().equals("iron")).findFirst().orElseThrow();
        require(next(gold, EconomyEngine.Regime.CRASH) > next(gold, EconomyEngine.Regime.BOOM), "Gold is not defensive");
        require(next(iron, EconomyEngine.Regime.CRASH) < next(iron, EconomyEngine.Regime.BOOM), "Iron is not cyclical");
        require(EconomyEngine.eventCommodityReturn(EconomyEngine.MarketEvent.REDSTONE_REVOLUTION, "redstone") > 0
                && EconomyEngine.eventCommodityReturn(EconomyEngine.MarketEvent.DEEPVEIN_DISCOVERY, "lapis") < 0,
                "Commodity-specific events");
    }

    private static double next(EconomyEngine.Commodity c, EconomyEngine.Regime regime) {
        return EconomyEngine.nextCommodityPrice(c, c.anchorPrice(), regime, 909, 1);
    }

    private static void testMigration() throws Exception {
        Path root = Files.createTempDirectory("tes-commodity-migration-");
        try {
            Path path = root.resolve("economy.properties");
            var old = EconomyState.fresh(909, 0, 0);
            old.account(RegressionTestSupport.PLAYER).cashMicro = 123_456_789L;
            old.account(RegressionTestSupport.PLAYER).shares.put("AURM", 3.25);
            for (int i = 0; i < 90; i++) old.advanceOneDay();
            old.save(path);
            Properties p = RegressionTestSupport.readProperties(path);
            p.setProperty("format", "29");
            for (var a : EconomyEngine.ASSETS) if (a.isCommodity()) {
                p.remove("price." + a.ticker()); p.remove("history." + a.ticker());
            }
            for (var c : EconomyEngine.COMMODITIES) if (EconomyEngine.isNewCommodity(c.id())) {
                p.remove("commodity." + c.id()); p.remove("commodity.history." + c.id());
            }
            RegressionTestSupport.refreshChecksum(p); RegressionTestSupport.writeProperties(path, p);
            var upgraded = EconomyState.load(path, 0, 0, 0);
            require(upgraded.economicDay == old.economicDay
                    && upgraded.account(RegressionTestSupport.PLAYER).cashMicro == 123_456_789L
                    && upgraded.account(RegressionTestSupport.PLAYER).shares.equals(old.account(RegressionTestSupport.PLAYER).shares),
                    "Upgrade changed clock or account");
            for (var a : EconomyEngine.ASSETS) {
                if (a.isCommodity()) {
                    require(upgraded.priceHistory.get(a.ticker()).equals(List.of(upgraded.commodityPrices.get(a.commodityId()))),
                            "Upgrade invented pre-listing history or reset an existing quote");
                } else if(!a.ticker().equals("VCIX")) require(upgraded.prices.get(a.ticker()).equals(old.prices.get(a.ticker()))
                        && upgraded.priceHistory.get(a.ticker()).equals(old.priceHistory.get(a.ticker())), "Legacy stock changed");
            }
            require(upgraded.commodityHistory.get("gold").equals(old.commodityHistory.get("gold")), "Trade history reset");
            require(EconomyEngine.resourceExchangeHistory("emerald_block", upgraded.commodityHistory).size() == 91,
                    "New commodity shortened existing fixed-price Trade chart");
            upgraded.save(path);
            var restored = EconomyState.load(path, 0, 0, 0);
            for (int i = 0; i < 90; i++) { upgraded.advanceOneDay(); restored.advanceOneDay(); }
            require(upgraded.prices.equals(restored.prices) && upgraded.priceHistory.equals(restored.priceHistory), "Restart rerolled quotes");

            Properties clean = RegressionTestSupport.readProperties(path);
            for (String key : List.of("price.GOLD", "history.GOLD", "commodity.iron", "commodity.history.iron")) {
                Properties broken = new Properties(); broken.putAll(clean); broken.remove(key);
                reject(root, broken);
            }
            Properties mismatch = new Properties(); mismatch.putAll(clean); mismatch.setProperty("price.GOLD", "123.0");
            reject(root, mismatch);
        } finally { RegressionTestSupport.deleteTree(root); }
    }

    private static void reject(Path root, Properties p) throws Exception {
        RegressionTestSupport.refreshChecksum(p);
        Path broken = root.resolve("broken.properties"); RegressionTestSupport.writeProperties(broken, p);
        boolean rejected = false;
        try { EconomyState.load(broken, 0, 0, 0); } catch (java.io.IOException expected) { rejected = true; }
        require(rejected, "Current format silently reset or accepted inconsistent commodity data");
    }

    private static void testTransactions() throws Exception {
        Path root = Files.createTempDirectory("tes-commodity-money-");
        try {
            var id = RegressionTestSupport.PLAYER;
            EconomyService service = new EconomyService(); service.startWithSeed(root, 991, 0, 0);
            require(service.deposit(id, 1000), "Initial cash");
            for (var a : EconomyEngine.ASSETS) if (a.isCommodity()) {
                require(!service.buy(id, a.ticker(), -1) && !service.buy(id, a.ticker(), Long.MAX_VALUE), "Invalid buy");
                require(service.buy(id, a.ticker(), 1), "Fractional commodity purchase");
                var position = service.portfolioAnalyticsSnapshot(id).positions().get(a.ticker());
                require(position.costBasisMicro() == EconomyState.MICRO && position.shares() > 0, "Unit basis");
                for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, position.shares() + 1}) {
                    require(!service.sell(id, a.ticker(), bad), "Invalid sale accepted");
                }
                require(service.sell(id, a.ticker(), position.shares() / 4), "Quarter unit sale");
                var reload = new EconomyService(); reload.startWithSeed(root, 0, 0, 0); service = reload;
                position = service.portfolioAnalyticsSnapshot(id).positions().get(a.ticker());
                require(Math.abs(position.costBasisMicro() - 750_000) <= 1, "Quarter sale basis after restart");
                require(service.sell(id, a.ticker(), position.shares()), "Full unit sale");
                require(!service.sell(id, a.ticker(), position.shares()), "Repeated sale duplicated cash");
            }
            require(service.portfolioAnalyticsSnapshot(id).realizedGainMicro() < 0, "Spread created free money");
        } finally { RegressionTestSupport.deleteTree(root); }
    }
}
