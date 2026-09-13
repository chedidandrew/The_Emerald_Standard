package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

/** Positive targets are not a return floor; reporting is durable, bounded and non-financial. */
public final class NewsGrowthRegressionTest {
    public static void main(String[] args) throws Exception {
        int[] bands=new int[4];
        for(int i=0;i<100000;i++) {
            double v=InvestmentGrowth.draw((i+.5)/100000);
            require(v>=.01&&v<=.15,"target bounds");
            bands[v<.04?0:v<.08?1:v<.12?2:3]++;
        }
        require(Arrays.equals(bands,new int[]{70000,23000,6000,1000}),"weighted target bands");
        for(String symbol:List.of("VILX","RSDN","VENT","FISH","coal","gold","redstone")) {
            double last=InvestmentGrowth.target(32,0,symbol);
            for(int day=1;day<10000;day++) {
                double v=InvestmentGrowth.target(32,day,symbol);
                require(v>=.01&&v<=.15&&Math.abs(v-last)<.0012,"smooth positive target "+symbol);
                last=v;
            }
        }
        var s=EconomyState.fresh(934,0,0);
        int losingYears=0;
        for(int year=0;year<20;year++) {
            double before=s.prices.get("VILX");
            for(int d=0;d<365;d++) s.advanceOneDay();
            if(s.prices.get("VILX")<before) losingYears++;
        }
        require(losingYears>0,"positive bias guaranteed a profit");
        require(s.news.size()==NewsWire.LIMIT,"bounded archive");
        s.validate();
        UUID village=UUID.randomUUID(),player=RegressionTestSupport.PLAYER;
        s.village(village);
        var quotes=Map.copyOf(s.prices);var physical=Map.copyOf(s.commodityPrices);
        var account=s.account(player);account.cashMicro=123456789;
        require(NewsWire.player(s,NewsWire.Kind.FOOD_REMOVED,village,player,"A\nFake",8),"player news");
        require(NewsWire.player(s,NewsWire.Kind.FOOD_REMOVED,village,player,"Renamed",9),"renamed news");
        var last=s.news.getLast();
        require(last.quantity()==17&&last.actor().equals("Renamed"),"UUID aggregation, not display name");
        require(last.detail().contains("not intent")&&last.detail().contains("17 food items"),"facts and uncertainty");
        require(!NewsWire.player(s,NewsWire.Kind.DAMAGE,village,player,"X",0),"empty report");
        require(!NewsWire.player(s,NewsWire.Kind.DAMAGE,UUID.randomUUID(),player,"X",9),"unknown village");
        require(s.prices.equals(quotes)&&s.commodityPrices.equals(physical)&&account.cashMicro==123456789,"news changed money");
        var pricesBefore=new LinkedHashMap<>(s.prices);
        NewsWire.day(s,EconomyEngine.MarketEvent.NETHER_SUPPLY_CRISIS,pricesBefore);
        var event=s.news.getLast();
        require(event.kind()==NewsWire.Kind.MARKET&&event.detail().contains("off-screen"),"event is not invented local destruction");
        require(s.prices.equals(quotes),"publishing an event changed price twice");
        var quiet=s.copy(); var noisy=s.copy();
        for(int day=0;day<1500;day++) {
            // Evict the entire news archive; price/event state must be independent of the paper.
            for(int i=0;i<256;i++) NewsWire.append(noisy,new NewsWire.Article(noisy.economicDay,
                NewsWire.Kind.ROUNDUP,"fixture",NewsWire.OUTLETS.getFirst(),"","",0,"fixture","read-only"));
            quiet.advanceOneDay();noisy.advanceOneDay();
            require(quiet.prices.equals(noisy.prices)&&quiet.eventCooldowns.equals(noisy.eventCooldowns),
                "archive eviction manipulated markets");
        }
        Path root=Files.createTempDirectory("tes-news-growth-");
        try {
            Path path=root.resolve("economy.properties");s.save(path);
            var restored=EconomyState.load(path,0,0,0);
            require(restored.news.equals(s.news)&&restored.commodityReferences.equals(s.commodityReferences),"archive/reference roundtrip");
            for(int i=0;i<400;i++){s.advanceOneDay();restored.advanceOneDay();}
            require(s.prices.equals(restored.prices)&&s.news.equals(restored.news),"restart rerolls world or stories");
            s.save(path);
            var old=RegressionTestSupport.readProperties(path);old.setProperty("format","30");
            old.keySet().removeIf(k->k.toString().startsWith("commodity.reference.")||k.toString().startsWith("news."));
            RegressionTestSupport.refreshChecksum(old);RegressionTestSupport.writeProperties(path,old);
            var upgraded=EconomyState.load(path,0,0,0);
            for(var a:EconomyEngine.ASSETS)if(!a.ticker().equals("VCIX"))
                require(upgraded.prices.get(a.ticker()).equals(s.prices.get(a.ticker()))
                        &&upgraded.priceHistory.get(a.ticker()).equals(s.priceHistory.get(a.ticker())),"migration rewrote actual history");
            require(upgraded.priceHistory.get("VCIX").equals(List.of(100.0)),"legacy VCIX history invented");
            require(upgraded.account(player).shares.equals(s.account(player).shares)
                &&upgraded.account(player).cashMicro==s.account(player).cashMicro,"migration changed holdings");
            require(upgraded.commodityReferences.equals(upgraded.commodityPrices)&&upgraded.news.isEmpty(),"legacy reference not current quotes");
            upgraded.save(path);
            var corrupt=RegressionTestSupport.readProperties(path);corrupt.setProperty("news.count","257");
            RegressionTestSupport.refreshChecksum(corrupt);RegressionTestSupport.writeProperties(root.resolve("bad.properties"),corrupt);
            boolean rejected=false;
            try {EconomyState.load(root.resolve("bad.properties"),0,0,0);}catch(java.io.IOException expected){rejected=true;}
            require(rejected,"oversized archive accepted");
        } finally {RegressionTestSupport.deleteTree(root);}
        System.out.println("PASS NewsGrowthRegressionTest: weighted targets, losses, UUID aggregation, no money mutation, archive and migration");
    }
}
