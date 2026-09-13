package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.client.MarketDisplay;
import java.nio.file.*;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

public final class LiveMarketRegressionTest {
    public static void main(String[] args)throws Exception{
        clocks(); jitter(); replay(); archive(); scale(); malformed();
        System.out.println("PASS live market: 15-second quotes, real charts, time-command monotonicity, sleep, replay, migration, ten-year/archive bounds and variance scaling");
    }
    private static void clocks()throws Exception{
        Path root=Files.createTempDirectory("tes-live-clock-");
        try{
            var e=new EconomyService();e.startWithSeed(root,919,0,0,0);
            var opening=e.marketSnapshot().prices();require(e.deposit(RegressionTestSupport.PLAYER,1000),"initial deposit");
            require(e.tickAt(299,299,0)&&e.marketSnapshot().prices().equals(opening),"quote moved before its slot");
            require(e.tickAt(300,300,0)&&!e.marketSnapshot().prices().equals(opening),"no real 15-second quote");
            var live=e.marketDisplay("RSDN","VILX","RSDN",false);
            require(live.slot()==1&&live.series().getFirst().values().size()==2,"live tape missing");
            require(live.series().getFirst().positions().getLast()==1.0/80,"morning stretched across full day");
            require(MarketDisplay.parse(live.pages()).equals(live),"live packet roundtrip");
            require(e.buy(RegressionTestSupport.PLAYER,"RSDN",10),"live quote not tradable");
            require(e.timeCommandAt(300,300,23*24000L+18000,0),"forward to day 23 midnight");
            while(e.catchUpDaysRemaining()>0)require(e.tickAt(300,23*24000L+18000,0),"catchup");
            require(e.snapshot().economicDay==23&&e.snapshot().liveMarket.slot==60,"forward phase");
            require(e.timeCommandAt(300,23*24000L+18000,0,0),"backward command");
            require(e.snapshot().economicDay==24&&e.snapshot().liveMarket.slot==0,"midnight reset must reach next dawn");
            var dawn=e.marketSnapshot().prices();
            require(e.timeCommandAt(300,0,0,0)&&e.snapshot().economicDay==24&&e.marketSnapshot().prices().equals(dawn),"same command minted time");
            require(e.timeCommandAt(300,0,24000,0,true)&&e.snapshot().economicDay==24
                    &&e.marketSnapshot().prices().equals(dawn),"repeated same-phase named marker minted a day");
            // Vanilla's named marker moved the sky date; rebasing it passively must stay neutral.
            require(e.tickAt(300,0,0),"marker baseline reset");
            require(e.timeCommandAt(300,0,13000,0)&&e.timeCommandAt(300,13000,0,0),"same-tick commands");
            require(e.snapshot().economicDay==25&&e.snapshot().liveMarket.slot==0,"missed same-tick sequence");
            require(e.tickAt(13000,12700,0),"ordinary time");
            require(e.tickAt(13001,24000,0),"sleep");
            require(e.snapshot().economicDay==26,"sleep didn't advance skipped time");
            long day=e.snapshot().economicDay;var prices=e.marketSnapshot().prices();
            require(e.tickAt(13001,0,0)&&e.snapshot().economicDay==day&&e.marketSnapshot().prices().equals(prices),"passive restored clock manufactured a day");
            require(e.saveNowAt(13001,0,0),"save");
            var loaded=new EconomyService();loaded.startWithSeed(root,0,0,13001,0);
            require(loaded.marketSnapshot().prices().equals(prices)&&loaded.snapshot().economicDay==day,"restart rerolled prices");
            require(loaded.timeCommandAt(13001,0,Integer.MAX_VALUE,0),"large command rejected");
            require(loaded.catchUpDaysRemaining()>0&&!loaded.buy(RegressionTestSupport.PLAYER,"RSDN",1),"trading stale catch-up quote allowed");
            require(loaded.snapshot().economicDay>=day,"huge jump rolled back");
        }finally{RegressionTestSupport.deleteTree(root);}
    }
    private static void jitter()throws Exception{
        Path root=Files.createTempDirectory("tes-live-jitter-");
        try{
            var e=new EconomyService();e.startWithSeed(root,71,0,0,0);
            require(e.tickAt(300,300,15037),"wall-clock jitter observation");
            require(e.timeCommandAt(300,300,23*24000L+18000,15037),"jitter command");
            while(e.catchUpDaysRemaining()>0)require(e.tickAt(300,23*24000L+18000,15037),"jitter catchup");
            require(e.snapshot().economicDay==23&&e.marketSlot()==60,"milliseconds of jitter minted a day");
            require(e.timeCommandAt(300,23*24000L+18000,0,15037)
                    &&e.economicDay()==24&&e.marketSlot()==0,"jitter reset skipped two dawns");
        }finally{RegressionTestSupport.deleteTree(root);}
    }
    private static void replay()throws Exception{
        Path root=Files.createTempDirectory("tes-live-replay-");
        try{
            var fine=EconomyState.fresh(45,0,0);var batch=fine.copy();
            fine.advanceMarketToSlot(37,false,true);fine.pendingEconomicMillis=37*LiveMarket.SLOT_MILLIS;
            var path=root.resolve("state.properties");fine.save(path);
            var restored=EconomyState.load(path,0,0,0);
            for(int i=38;i<80;i++){fine.advanceMarketToSlot(i,false,true);restored.advanceMarketToSlot(i,false,true);}
            fine.advanceOneDay();restored.advanceOneDay();batch.advanceOneDay();
            require(fine.prices.equals(restored.prices)&&fine.prices.equals(batch.prices),"batch/reload diverged");
            require(fine.liveMarket.yesterday.get("RSDN").size()==81,"completed session not retained");
            require(MarketDisplay.parse(MarketDisplay.build(fine,"RSDN","VILX","RSDN",true).pages()).yesterday(),"yesterday packet");
            var old=RegressionTestSupport.readProperties(path);old.setProperty("format","35");
            old.keySet().removeIf(k->k.toString().startsWith("live."));RegressionTestSupport.refreshChecksum(old);
            var legacy=root.resolve("legacy.properties");RegressionTestSupport.writeProperties(legacy,old);
            var migrated=EconomyState.load(legacy,0,0,0);
            require(migrated.liveMarket.slot==37&&migrated.liveMarket.today.get("RSDN").size()==1,"legacy backfilled tape");
            require(migrated.prices.equals(EconomyState.load(path,0,0,0).prices),"migration changed quote");
            require(!MarketDisplay.build(migrated,"RSDN","VILX","RSDN",false).quote("RSDN").known(),"partial migration advertised full-day change");
        }finally{RegressionTestSupport.deleteTree(root);}
    }
    private static void archive()throws Exception{
        var s=EconomyState.fresh(13,0,0);
        // Exercise the storage algorithm without a multi-century financial simulation.
        for(String key:LiveMarket.KEYS)for(int day=0;day<100000;day++)s.liveMarket.archive(key,day,100+day/1000.0);
        require(s.liveMarket.archive.values().stream().allMatch(v->v.size()<=512&&v.getFirst().day()==0),"unbounded archive or lost origin");
        require(EconomyState.HISTORY_DAYS==3651,"ten-year close coverage");
        s.economicDay=104000;
        for(String ticker:s.priceHistory.keySet())s.priceHistory.put(ticker,new ArrayList<>(Collections.nCopies(3651,s.prices.get(ticker))));
        for(String key:s.commodityHistory.keySet())s.commodityHistory.put(key,new ArrayList<>(Collections.nCopies(3651,s.commodityPrices.get(key))));
        var archived=s.liveMarket;
        s.liveMarket=LiveMarket.adopt(s);
        for(String key:LiveMarket.KEYS){
            s.liveMarket.archive.put(key,archived.archive.get(key));
            s.liveMarket.archiveStride.put(key,archived.archiveStride.get(key));
        }
        Path root=Files.createTempDirectory("tes-live-archive-");
        try{
            var file=root.resolve("state.properties");s.save(file);
            var loaded=EconomyState.load(file,0,0,0);
            require(loaded.liveMarket.archive.equals(s.liveMarket.archive)
                    &&loaded.liveMarket.archiveStride.equals(s.liveMarket.archiveStride),"archive lost on restart");
            var view=MarketDisplay.build(loaded,"RSDN","VILX","RSDN",false);
            require(MarketDisplay.parse(view.pages()).equals(view),"long archive packet");
            require(view.series().get(7).availableDay()==0,"All lost real origin");
        }finally{RegressionTestSupport.deleteTree(root);}
        require(Arrays.equals(MarketDisplay.RANGES,new int[]{1,30,90,365,1095,1825,3650,Integer.MAX_VALUE}),"range order");
    }
    private static void scale(){
        double fineSum=0,dailySum=0,fineSquares=0,dailySquares=0;int n=20000;
        for(int i=0;i<n;i++){
            long day=i+1;double daily=Math.log1p(EconomyEngine.marketReturn(EconomyEngine.Regime.EXPANSION,773,day));
            double fine=0;for(int slot=1;slot<=80;slot++)fine+=Math.log1p(EconomyEngine.marketReturn(EconomyEngine.Regime.EXPANSION,773,day,1.0/80,slot));
            fineSum+=fine;dailySum+=daily;fineSquares+=fine*fine;dailySquares+=daily*daily;
        }
        double varianceRatio=(fineSquares/n-Math.pow(fineSum/n,2))/(dailySquares/n-Math.pow(dailySum/n,2));
        require(varianceRatio>.8&&varianceRatio<1.2,"intraday multiplied daily volatility: "+varianceRatio);
        require(Math.abs(fineSum/n-dailySum/n)<.00025,"intraday multiplied growth");
        System.out.printf(Locale.ROOT,"Live calibration: daily log-variance ratio %.4f; mean difference %.7f%n",varianceRatio,fineSum/n-dailySum/n);
    }
    private static void malformed()throws Exception{
        var s=EconomyState.fresh(1,0,0);s.liveMarket.today.get("RSDN").clear();
        try{s.validate();throw new AssertionError("missing tape accepted");}catch(java.io.IOException expected){}
        s=EconomyState.fresh(1,0,0);s.liveMarket.open.put("RSDN",999.0);
        try{s.validate();throw new AssertionError("false daily baseline accepted");}catch(java.io.IOException expected){}
        s=EconomyState.fresh(1,0,0);s.advanceMarketToSlot(3,false,true);s.liveMarket.today.get("RSDN").remove(1);
        try{s.validate();throw new AssertionError("missing middle observation accepted");}catch(java.io.IOException expected){}
        s=EconomyState.fresh(1,0,0);s.liveMarket.slot=81;
        try{s.validate();throw new AssertionError("future slot accepted");}catch(java.io.IOException expected){}
    }
}
