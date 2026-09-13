package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

public final class StockIndexRegressionTest {
    public static void main(String[] args) throws Exception {
        directions(); trajectories(); splits(); persistence(); transactions();
        System.out.println("PASS VILX: twelve-stock capitalization, directions, targets, news, splits, migration, reload and transactions");
    }

    private static void close(double a, double b, String message) {
        require(Math.abs(a-b) <= 1e-9*Math.max(1.0,Math.abs(b)), message+": "+a+" != "+b);
    }

    private static void directions() throws Exception {
        var s=EconomyState.fresh(73,0,0);
        require(StockIndex.CONSTITUENTS.size()==12,"twelve companies required");
        require(!StockIndex.CONSTITUENTS.contains("TREA") && !StockIndex.CONSTITUENTS.contains("VCIX")
                && !StockIndex.CONSTITUENTS.contains("GOLD"),"non-company in stock basket");
        for(double w:StockIndex.weights(s).values())close(w,1.0/12,"equal starting capitalization");
        s.prices.put("TREA",400.0);s.prices.put("GOLD",400.0);s.prices.put("VCIX",400.0);
        StockIndex.reprice(s);close(s.prices.get("VILX"),100,"non-company prices moved VILX");
        for(String ticker:StockIndex.CONSTITUENTS)s.prices.put(ticker,90.0);
        StockIndex.reprice(s);close(s.prices.get("VILX"),90,"all down must be down");
        for(String ticker:StockIndex.CONSTITUENTS)s.prices.put(ticker,110.0);
        StockIndex.reprice(s);close(s.prices.get("VILX"),110,"all up must be up");
        // The old eight-stock basket omitted every specialist; each must now contribute.
        for(String ticker:List.of("AURM","BRCK","FISH","VENT")) {
            var specialist=EconomyState.fresh(73,0,0);
            specialist.prices.put(ticker,220.0);StockIndex.reprice(specialist);
            close(specialist.prices.get("VILX"),110,"specialist omitted: "+ticker);
        }
        for(String ticker:StockIndex.CONSTITUENTS)s.prices.put(ticker,90.0);
        s.prices.put("RSDN",3000.0);StockIndex.reprice(s);
        require(s.prices.get("VILX")>300,"independent upside cap remains");
        double old=s.prices.get("VILX");
        for(String ticker:StockIndex.CONSTITUENTS)s.prices.put(ticker,s.prices.get(ticker)*.90);
        s.prices.put("RSDN",3300.0);StockIndex.reprice(s);
        require(s.prices.get("VILX")>old,"large company cannot outweigh eleven declining companies");
        var weights=StockIndex.weights(s);
        s.account(RegressionTestSupport.PLAYER).shares.put("RSDN",1e8);
        s.account(RegressionTestSupport.PLAYER).shares.put("VILX",1e8);
        require(weights.equals(StockIndex.weights(s)),"player holdings changed market caps");
        for(var event:EconomyEngine.MarketEvent.values())
            require(EconomyEngine.eventAssetReturn(event,"VILX")==0,"independent VILX news shock");
        try {
            EconomyEngine.assetReturn(EconomyEngine.ASSETS.getFirst(),.1,1,1);
            throw new AssertionError("independent index generator still available");
        } catch(IllegalArgumentException expected) {}
    }

    private static void trajectories() throws Exception {
        for(int seed=0;seed<12;seed++) {
            var s=EconomyState.fresh(seed,0,0);
            var outstanding=new LinkedHashMap<>(s.stockIndexShares);
            for(int day=0;day<730;day++) {
                var prices=new HashMap<>(s.prices);var weights=StockIndex.weights(s);
                s.advanceOneDay();
                double factor=weights.entrySet().stream().mapToDouble(e->
                        e.getValue()*s.prices.get(e.getKey())/prices.get(e.getKey())).sum();
                close(s.prices.get("VILX")/prices.get("VILX"),factor,"actual daily basket, with news");
                close(StockIndex.weights(s).values().stream().mapToDouble(Double::doubleValue).sum(),1,"weight sum");
                double expected=StockIndex.weights(s).entrySet().stream().mapToDouble(e->
                        e.getValue()*InvestmentGrowth.target(s.seed,s.economicDay,e.getKey())).sum();
                close(StockIndex.growthTarget(s,s.economicDay),expected,"inherited target");
                require(expected>=.01-1e-12 && expected<=.15+1e-12,"fundamental target out of range");
                require(outstanding.equals(s.stockIndexShares),"daily rebalancing changed simulated float");
            }
            s.validate();
        }
    }

    private static void splits() throws Exception {
        for(boolean wholeBasket:List.of(false,true)) {
            var s=EconomyState.fresh(8,0,0);
            var account=s.account(RegressionTestSupport.PLAYER);
            for(String ticker:StockIndex.CONSTITUENTS) {
                if(wholeBasket || ticker.equals("RSDN"))s.prices.put(ticker,2_000_000.0);
                account.shares.put(ticker,2.0);account.shareCostBasisMicro.put(ticker,100L*EconomyState.MICRO);
            }
            account.shares.put("VILX",3.0);account.shareCostBasisMicro.put("VILX",200L*EconomyState.MICRO);
            StockIndex.reprice(s);
            s.liveMarket=LiveMarket.adopt(s); // Synthetic split fixture adopts its deliberately replaced quotes.
            var weights=StockIndex.weights(s);var before=new LinkedHashMap<>(s.prices);
            var values=new LinkedHashMap<String,Double>();
            account.shares.forEach((ticker,shares)->values.put(ticker,shares*s.prices.get(ticker)));
            var basis=new LinkedHashMap<>(account.shareCostBasisMicro);
            var normalizer=EconomyState.class.getDeclaredMethod("normalizeHighPrices",Map.class);
            normalizer.setAccessible(true);normalizer.invoke(s,before);
            close(StockIndex.value(s),s.prices.get("VILX"),"split broke divisor");
            for(var e:weights.entrySet())close(StockIndex.weights(s).get(e.getKey()),e.getValue(),"split changed weight");
            for(var e:values.entrySet())close(account.shares.get(e.getKey())*s.prices.get(e.getKey()),e.getValue(),"split changed owned value");
            require(basis.equals(account.shareCostBasisMicro),"split changed cost basis");
            close(s.prices.get("VILX"),before.get("VILX"),"split appears in daily news");
            close(s.stockIndexShares.get("RSDN"),1000,"stock split did not adjust company float");
            s.validate();
            var clone=s.copy();clone.advanceOneDay();s.advanceOneDay();
            require(s.prices.equals(clone.prices)&&s.stockIndexShares.equals(clone.stockIndexShares),"post-split replay diverged");
        }
        // A price floor on one company must use its actual quote, not its proposed raw return.
        var tiny=EconomyState.fresh(9,0,0);
        tiny.prices.put("RSDN",.000001);StockIndex.reprice(tiny);
        for(int i=0;i<50;i++){tiny.advanceOneDay();close(tiny.prices.get("VILX"),StockIndex.value(tiny),"floor basket diverged");}
    }

    private static void persistence() throws Exception {
        Path root=Files.createTempDirectory("tes-vilx-cap-");
        try {
            Path path=root.resolve("source.properties");
            var s=EconomyState.fresh(933,0,0);for(int d=0;d<250;d++)s.advanceOneDay();
            var account=s.account(RegressionTestSupport.PLAYER);
            account.cashMicro=913_000_000;account.shares.put("VILX",4.25);
            account.shareCostBasisMicro.put("VILX",415_000_000L);
            s.save(path);var restored=EconomyState.load(path,0,0,0);
            require(s.stockIndexShares.equals(restored.stockIndexShares),"float not persisted");
            close(s.stockIndexDivisor,restored.stockIndexDivisor,"divisor not persisted");
            for(int i=0;i<365;i++){s.advanceOneDay();restored.advanceOneDay();}
            require(s.prices.equals(restored.prices),"reload rerolled path");
            var p=RegressionTestSupport.readProperties(path);p.setProperty("format","33");
            p.keySet().removeIf(k->k.toString().startsWith("index.stock."));
            RegressionTestSupport.refreshChecksum(p);
            Path legacy=root.resolve("legacy.properties");RegressionTestSupport.writeProperties(legacy,p);
            var migrated=EconomyState.load(legacy,0,0,0);
            require(migrated.stockIndexStartedDay==250,"migration tracking date");
            close(migrated.prices.get("VILX"),Double.parseDouble(p.getProperty("price.VILX")),"migration price jumped");
            require(migrated.account(RegressionTestSupport.PLAYER).cashMicro==913_000_000
                    && migrated.account(RegressionTestSupport.PLAYER).shares.get("VILX")==4.25
                    && migrated.account(RegressionTestSupport.PLAYER).shareCostBasisMicro.get("VILX")==415_000_000L,"migration changed money/basis");
            require(migrated.priceHistory.get("VILX").equals(s.priceHistory.get("VILX").subList(0,251)),"migration rewrote history");
            for(double w:StockIndex.weights(migrated).values())close(w,1.0/12,"migration initial weight");
            var before=new HashMap<>(migrated.prices);migrated.advanceOneDay();
            double factor=StockIndex.CONSTITUENTS.stream().mapToDouble(t->migrated.prices.get(t)/before.get(t)/12).sum();
            close(migrated.prices.get("VILX")/before.get("VILX"),factor,"first migrated day wrong");
            Path converted=root.resolve("converted.properties");migrated.save(converted);
            var reloaded=EconomyState.load(converted,0,0,0);
            require(reloaded.stockIndexStartedDay==250 && reloaded.stockIndexShares.equals(migrated.stockIndexShares),"upgrade reran");
            for(String fault:List.of("missing","zero","negative","nan","infinite","divisor","divisorzero","date","extra")) {
                var bad=RegressionTestSupport.readProperties(converted);
                switch(fault) {
                    case "missing" -> bad.remove("index.stock.shares.RSDN");
                    case "zero" -> bad.setProperty("index.stock.shares.RSDN","0");
                    case "negative" -> bad.setProperty("index.stock.shares.RSDN","-1");
                    case "nan" -> bad.setProperty("index.stock.shares.RSDN","NaN");
                    case "infinite" -> bad.setProperty("index.stock.shares.RSDN","Infinity");
                    case "divisor" -> bad.setProperty("index.stock.divisor","123");
                    case "divisorzero" -> bad.setProperty("index.stock.divisor","0");
                    case "date" -> bad.setProperty("index.stock.started_day","999999");
                    case "extra" -> bad.setProperty("index.stock.shares.GOLD","1");
                }
                RegressionTestSupport.refreshChecksum(bad);
                Path invalid=root.resolve(fault+".properties");RegressionTestSupport.writeProperties(invalid,bad);
                try{EconomyState.load(invalid,0,0,0);throw new AssertionError("invalid basket silently reset: "+fault);}
                catch(java.io.IOException expected){}
            }
        } finally {RegressionTestSupport.deleteTree(root);}
    }

    private static void transactions() throws Exception {
        Path root=Files.createTempDirectory("tes-vilx-trades-");
        try {
            var id=RegressionTestSupport.PLAYER;var service=new EconomyService();
            service.startWithSeed(root,28,0,0);require(service.deposit(id,100),"deposit");
            require(service.buy(id,"VILX",1),"fractional index buy");
            var bought=service.portfolioAnalyticsSnapshot(id).positions().get("VILX");
            require(bought.shares()>0 && bought.shares()<1,"fractional units lost");
            var restart=new EconomyService();restart.startWithSeed(root,0,0,0);
            close(restart.portfolioAnalyticsSnapshot(id).positions().get("VILX").shares(),bought.shares(),"holding lost");
            require(restart.sell(id,"VILX",bought.shares()),"exit");
            require(!restart.sell(id,"VILX",bought.shares()),"duplicate exit");
            require(restart.portfolioAnalyticsSnapshot(id).realizedGainMicro()<0,"spread created money");
        } finally {RegressionTestSupport.deleteTree(root);}
    }
}
