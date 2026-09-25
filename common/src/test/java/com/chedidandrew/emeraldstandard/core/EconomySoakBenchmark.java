package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

/**
 * Opt-in measured model soak; no Minecraft world, ticks, entities or pathfinding.
 * java -cp build/common-tests ...EconomySoakBenchmark [days=1095] [districts=100,500,1000]
 * Fixture accounts and districts remain isolated; interrupted runs leave only temporary test data.
 */
public final class EconomySoakBenchmark {
    public static void main(String[] args) throws Exception {
        int days=args.length>0?Integer.parseInt(args[0]):1095;
        if(days<90||days>36500)throw new IllegalArgumentException("days must be 90..36500");
        String[] scales=(args.length>1?args[1]:"100,500,1000").split(",");
        System.out.printf(Locale.ROOT,"MODEL_SOAK java=%s days=%d seed=6011; timings are economic-day work, NOT MSPT%n",System.getProperty("java.version"),days);
        for(String raw:scales) {
            int districts=Integer.parseInt(raw);
            if(districts<1||districts>10000)throw new IllegalArgumentException("districts must be 1..10000");
            for(boolean physicalBacklog:new boolean[]{false,true})run(days,districts,physicalBacklog);
        }
    }
    private static void run(int days,int size,boolean physical) throws Exception {
        var state=fixture(size);long[] timings=new long[days];var saves=new ArrayList<Long>();var loads=new ArrayList<Long>();
        long bytes=0;int accounts=state.accounts.size();
        Path dir=Files.createTempDirectory("tes-model-soak-"),file=dir.resolve("economy.properties");
        try {
            for(int day=0;day<days;day++) {
                long start=System.nanoTime();advance(state,physical);timings[day]=System.nanoTime()-start;
                check(state.news.size()<=NewsWire.LIMIT,"news archive bound");
                for(var v:state.villages.values()) {
                    check(Double.isFinite(v.foodSupply)&&v.foodSupply>=0&&Double.isFinite(v.materialSupply)&&v.materialSupply>=0,"supplies");
                    check(Double.isFinite(v.treasury)&&v.treasury>=0,"treasury");
                }
                check(state.prices.values().stream().allMatch(p->Double.isFinite(p)&&p>0),"positive finite quotes");
                if((day+1)%90==0||day==days-1) {
                    start=System.nanoTime();state.save(file);saves.add(System.nanoTime()-start);bytes=Files.size(file);
                    start=System.nanoTime();var loaded=EconomyState.load(file,0,0,0);loads.add(System.nanoTime()-start);
                    check(loaded.economicDay==state.economicDay&&loaded.accounts.size()==accounts&&loaded.villages.size()==size,"restart counts");
                    check(loaded.prices.equals(state.prices)&&loaded.news.equals(state.news),"restart market/news");
                    for(var entry:state.accounts.entrySet()) {
                        var restored=loaded.accounts.get(entry.getKey());
                        check(restored.cashMicro==entry.getValue().cashMicro&&restored.savingsMicro==entry.getValue().savingsMicro,"restart money");
                    }
                    // Two independent continuations must agree; no rerolled growth or market path on reload.
                    var reference=state.copy();var candidate=loaded.copy();advance(reference,physical);advance(candidate,physical);
                    check(reference.prices.equals(candidate.prices),"restart deterministic future");
                    state=loaded;
                }
            }
            long projects=state.villages.values().stream().mapToLong(v->v.projects.size()).sum();
            long completed=state.villages.values().stream().flatMap(v->v.projects.stream()).filter(p->p.economicComplete).count();
            long active=state.villages.values().stream().filter(v->v.lifecycle==VillageProsperityEngine.Lifecycle.ACTIVE).count();
            System.out.printf(Locale.ROOT,"MODEL_SOAK districts=%d accounts=%d visual_backlog=%s days=%d day_p50_ms=%.3f day_p95_ms=%.3f day_p99_ms=%.3f day_max_ms=%.3f save_p95_ms=%.3f load_p95_ms=%.3f bytes=%d projects=%d economic_complete=%d active=%d%n",
                size,accounts,physical,days,percentile(timings,.5),percentile(timings,.95),percentile(timings,.99),percentile(timings,1),
                percentile(saves.stream().mapToLong(Long::longValue).toArray(),.95),percentile(loads.stream().mapToLong(Long::longValue).toArray(),.95),
                bytes,projects,completed,active);
        } finally {
            Files.deleteIfExists(file.resolveSibling(file.getFileName()+".tmp"));
            Files.deleteIfExists(file.resolveSibling(file.getFileName()+".bak"));
            Files.deleteIfExists(file);Files.deleteIfExists(dir);
        }
    }
    private static void advance(EconomyState state,boolean physical) {
        state.advanceOneDay(true,physical,true,true,true,.04,.2,64*EconomyState.MICRO,true,true,false);
    }
    private static EconomyState fixture(int size) {
        var s=EconomyState.fresh(6011,0,0);
        for(int i=0;i<size;i++){
            var v=new EconomyState.VillageRecord();v.villageId=new UUID(7,i+1);
            v.dimensionKey="minecraft:overworld";v.centerPos=pack(i%32*128,64,i/32*128);
            v.population=8+i%16;v.observedPopulation=v.population;v.housingCapacity=v.population+8;v.observedHousingCapacity=v.housingCapacity;
            v.foodSupply=v.population*24;v.materialSupply=v.population*12;v.treasury=100;v.developmentPoints=30;v.safety=70;
            s.villages.put(v.villageId,v);
        }
        for(int i=0;i<Math.min(32,Math.max(4,size/20));i++){
            var a=s.account(new UUID(8,i+1));a.cashMicro=500*EconomyState.MICRO;a.savingsMicro=100*EconomyState.MICRO;
            for(var asset:EconomyEngine.ASSETS)a.shares.put(asset.ticker(),2.0);
        }
        return s;
    }
    static double percentile(long[] data,double q){long[] sorted=data.clone();Arrays.sort(sorted);return sorted[Math.max(0,(int)Math.ceil(q*sorted.length)-1)]/1e6;}
    static long pack(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|((long)y&0xfffL);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
