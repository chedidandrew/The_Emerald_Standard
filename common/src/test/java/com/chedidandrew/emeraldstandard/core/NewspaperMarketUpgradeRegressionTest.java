package com.chedidandrew.emeraldstandard.core;
import com.chedidandrew.emeraldstandard.client.*;
import java.nio.file.*;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

public final class NewspaperMarketUpgradeRegressionTest {
    public static void main(String[] args)throws Exception {
        basket();migration();display();edition();transactions();
        System.out.println("PASS newspaper/market upgrade: weighted basket, migration, coherent aligned charts, ranking");
    }
    private static void basket()throws Exception {
        for(int seed=0;seed<8;seed++) {
            var s=EconomyState.fresh(seed,0,0);
            require(s.commodityIndexWeights.size()==8&&s.commodityIndexWeights.values().stream().allMatch(w->w==0.125),"equal starting capital");
            for(int d=0;d<365;d++) {
                var before=new HashMap<>(s.prices);var weights=new HashMap<>(s.commodityIndexWeights);
                s.advanceOneDay();
                double factor=weights.entrySet().stream().mapToDouble(e->e.getValue()*s.prices.get(e.getKey())/before.get(e.getKey())).sum();
                require(Math.abs(s.prices.get("VCIX")/before.get("VCIX")-factor)<1e-10,"basket return diverged");
                for(var e:weights.entrySet())require(Math.abs(s.commodityIndexWeights.get(e.getKey())
                        -e.getValue()*s.prices.get(e.getKey())/before.get(e.getKey())/factor)<1e-10,"weight did not drift");
            }
            s.validate();
            require(s.copy().commodityIndexWeights.equals(s.commodityIndexWeights),"copy dropped basket");
        }
    }
    private static void migration()throws Exception {
        var root=Files.createTempDirectory("tes-newspaper-market-");
        try {
            Path path=root.resolve("economy.properties");
            var s=EconomyState.fresh(77,0,0);for(int i=0;i<60;i++)s.advanceOneDay();
            s.account(RegressionTestSupport.PLAYER).cashMicro=999999;
            s.account(RegressionTestSupport.PLAYER).shares.put("VILX",2.5);
            s.save(path);var restored=EconomyState.load(path,0,0,0);
            require(restored.commodityIndexWeights.equals(s.commodityIndexWeights),"weights not saved");
            for(int i=0;i<30;i++){s.advanceOneDay();restored.advanceOneDay();}
            require(s.prices.equals(restored.prices),"reload rerolled basket");
            var old=RegressionTestSupport.readProperties(path);old.setProperty("format","32");
            old.remove("price.VCIX");old.remove("history.VCIX");
            old.keySet().removeIf(k->k.toString().startsWith("index.commodity.weight."));
            RegressionTestSupport.refreshChecksum(old);Path legacy=root.resolve("legacy.properties");
            RegressionTestSupport.writeProperties(legacy,old);var migrated=EconomyState.load(legacy,0,0,0);
            require(migrated.priceHistory.get("VCIX").equals(List.of(100.0)),"invented pre-listing history");
            require(migrated.prices.get("VILX")==Double.parseDouble(old.getProperty("price.VILX")),"VILX value changed");
            require(migrated.account(RegressionTestSupport.PLAYER).shares.get("VILX")==2.5
                    &&migrated.account(RegressionTestSupport.PLAYER).cashMicro==999999,"account changed");
            var p=RegressionTestSupport.readProperties(path);p.remove("index.commodity.weight.GOLD");
            RegressionTestSupport.refreshChecksum(p);Path broken=root.resolve("broken.properties");
            RegressionTestSupport.writeProperties(broken,p);
            try{EconomyState.load(broken,0,0,0);throw new AssertionError("missing current basket silently reset");}
            catch(java.io.IOException expected){}
        }finally{RegressionTestSupport.deleteTree(root);}
    }
    private static void display() {
        var state=EconomyState.fresh(55,0,0);for(int i=0;i<100;i++)state.advanceOneDay();
        state.priceHistory.put("VCIX",new ArrayList<>(List.of(100.0,99.0)));
        state.prices.put("VCIX",99.0);
        var s=MarketDisplay.build(state.economicDay,state.prices,state.priceHistory,"VILX","VCIX");
        require(MarketDisplay.parse(s.pages()).equals(s),"coherent document roundtrip");
        for(var c:s.curves().subList(1,s.curves().size()))require(c.firstDay()==99&&c.lastDay()==100&&c.left().size()==2
                &&c.left().getFirst()==100&&c.right().getFirst()==100,"dates or normalization differ");
        require(Math.abs(s.quote("VCIX").daily()+1)<1e-9,"daily decline missing");
        var fresh=EconomyState.fresh(1,0,0);
        var empty=MarketDisplay.build(0,fresh.prices,fresh.priceHistory,"VILX","VCIX");
        require(!empty.quote("VCIX").known()&&empty.curves().getFirst().left().size()==1,"new listing fabricated a return");
        var malformed=new ArrayList<>(s.pages());malformed.set(1,"99|100\nNaN\n100");
        try{MarketDisplay.parse(malformed);throw new AssertionError("nonfinite curve accepted");}catch(IllegalArgumentException expected){}
        var longer=MarketDisplay.build(100,state.prices,state.priceHistory,"VILX","RSDN");
        require(longer.curves().get(1).firstDay()==70&&longer.curves().get(2).firstDay()==10,"30/90-day windows off by one");
    }
    private static void transactions()throws Exception {
        Path root=Files.createTempDirectory("tes-vcix-trades-");
        try {
            var id=RegressionTestSupport.PLAYER;
            var service=new EconomyService();service.startWithSeed(root,121,0,0);
            require(service.deposit(id,100),"deposit");
            require(service.buy(id,"VCIX",1),"fractional index purchase");
            var bought=service.portfolioAnalyticsSnapshot(id).positions().get("VCIX");
            require(bought.shares()>0&&bought.shares()<1&&bought.costBasisMicro()==EconomyState.MICRO,"fractional basis");
            var restart=new EconomyService();restart.startWithSeed(root,0,0,0);
            require(restart.portfolioAnalyticsSnapshot(id).positions().get("VCIX").shares()==bought.shares(),"index holding lost on reload");
            require(restart.sell(id,"VCIX",bought.shares()),"index exit");
            require(!restart.sell(id,"VCIX",bought.shares()),"replayed sale duplicated cash");
            require(restart.portfolioAnalyticsSnapshot(id).realizedGainMicro()<0,"index spread created money");
        }finally{RegressionTestSupport.deleteTree(root);}
    }
    private static void edition() {
        var crisis=new NewsReader.Entry(1,NewsEditorial.Section.LOCAL,"Outlet\nLocal\n\nCrisis",10,90);
        var roundup=new NewsReader.Entry(2,NewsEditorial.Section.MARKETS,"Outlet\nMarket\n\nClose",10,20);
        var newClose=new NewsReader.Entry(3,NewsEditorial.Section.MARKETS,"Outlet\nMarket\n\nNew close",30,20);
        require(NewsReader.ranked(List.of(roundup,crisis)).getFirst().id()==1,"lead ignores significance");
        require(NewsReader.ranked(List.of(crisis,newClose)).getFirst().id()==3,"ancient crisis crowds out news");
        require(NewsReader.Entry.parse(crisis.wire()).equals(crisis),"ranking metadata lost");
        var reader=new NewsReader();reader.receive(List.of(crisis),1);reader.markRead(crisis);
        reader.receive(List.of(newClose),1);require(reader.edition().equals(List.of(crisis)),"edition jumped");
        reader.receive(List.of(),0);require(reader.edition().isEmpty(),"privacy waited for acceptance");
    }
}
