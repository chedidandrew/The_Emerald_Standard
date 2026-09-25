package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.*;

/** Server-only v1 intraday tape. Each slot is an actual quote, never an interpolated animation. */
public final class LiveMarket {
    public static final int SLOTS=80;
    public static final long SLOT_MILLIS=EconomyService.MILLIS_PER_MINECRAFT_DAY/SLOTS;
    public static final int ARCHIVE_LIMIT=512;
    public static final List<String> KEYS;
    static {
        var keys=new ArrayList<String>();
        EconomyEngine.ASSETS.forEach(a->keys.add(a.ticker()));
        EconomyEngine.COMMODITIES.forEach(c->keys.add("~"+c.id()));
        KEYS=List.copyOf(keys);
    }
    public record Point(long day,int slot,double value) {
        public double distanceFrom(long firstDay) { return (day-firstDay)+slot/(double)SLOTS; }
    }
    public int slot;
    public long sinceDay;
    public int sinceSlot;
    public EconomyEngine.Regime previousRegime;
    public EconomyEngine.MarketEvent closingEvent=EconomyEngine.MarketEvent.NONE;
    public final Map<String,Double> open=new LinkedHashMap<>();
    public final Map<String,List<Point>> today=new LinkedHashMap<>(),yesterday=new LinkedHashMap<>(),archive=new LinkedHashMap<>();
    public final Map<String,Long> archiveStride=new LinkedHashMap<>();

    /** Adopt existing quotes at migration (also used by explicit synthetic test fixtures). */
    public static LiveMarket adopt(EconomyState state) {
        var live=new LiveMarket();
        live.slot=state.pendingEconomicMillis>=EconomyService.MILLIS_PER_MINECRAFT_DAY?0:(int)(state.pendingEconomicMillis/SLOT_MILLIS);
        live.sinceDay=state.economicDay;live.sinceSlot=live.slot;live.previousRegime=state.regime;
        for(String key:KEYS) {
            live.today.put(key,new ArrayList<>());live.yesterday.put(key,new ArrayList<>());
            live.archive.put(key,new ArrayList<>());live.archiveStride.put(key,1L);
        }
        live.open.putAll(quotes(state));live.record(state);
        return live;
    }
    static Map<String,Double> quotes(EconomyState state) {
        Map<String,Double> result=new LinkedHashMap<>(state.prices);
        state.commodityPrices.forEach((key,value)->result.put("~"+key,value));return result;
    }
    public LiveMarket copy() {
        var result=new LiveMarket();result.slot=slot;result.sinceDay=sinceDay;result.sinceSlot=sinceSlot;
        result.previousRegime=previousRegime;result.closingEvent=closingEvent;result.open.putAll(open);
        today.forEach((k,v)->result.today.put(k,new ArrayList<>(v)));
        yesterday.forEach((k,v)->result.yesterday.put(k,new ArrayList<>(v)));
        archive.forEach((k,v)->result.archive.put(k,new ArrayList<>(v)));result.archiveStride.putAll(archiveStride);
        return result;
    }
    void record(EconomyState state) {
        for(var e:quotes(state).entrySet()) today.get(e.getKey()).add(new Point(state.economicDay,slot,e.getValue()));
    }
    void close(EconomyState state) {
        for(String key:KEYS) {yesterday.put(key,new ArrayList<>(today.get(key)));today.get(key).clear();}
        slot=0;closingEvent=EconomyEngine.MarketEvent.NONE;previousRegime=state.regime;
        open.clear();open.putAll(quotes(state));record(state);
    }
    void split(String ticker,double divisor) {
        open.computeIfPresent(ticker,(k,v)->v/divisor);
        for(var map:List.of(today,yesterday,archive)) {
            var values=map.get(ticker);if(values!=null)values.replaceAll(p->new Point(p.day,p.slot,p.value/divisor));
        }
    }
    void archive(String key,long day,double value) {
        var values=archive.get(key);long stride=archiveStride.get(key);
        if(!values.isEmpty() && day%stride!=0) return;
        values.add(new Point(day,0,value));
        if(values.size()>ARCHIVE_LIMIT) {
            final long larger=Math.min(Long.MAX_VALUE/2,stride)*2;
            Point first=values.getFirst(),last=values.getLast();
            values.removeIf(p->p!=first&&p!=last&&p.day%larger!=0);
            archiveStride.put(key,larger);
        }
    }
    /** Daily dates remain real dates; older archives are sampled closes, not invented prices. */
    public List<Point> history(EconomyState state,String key,int days,boolean previous) {
        if(days==1)return List.copyOf((previous?yesterday:today).getOrDefault(key,List.of()));
        var result=new ArrayList<Point>();
        if(days==Integer.MAX_VALUE)result.addAll(archive.getOrDefault(key,List.of()));
        var daily=key.startsWith("~")?state.commodityHistory.get(key.substring(1)):state.priceHistory.get(key);
        if(daily!=null) {
            long first=state.economicDay-daily.size()+1;
            long cutoff=days==Integer.MAX_VALUE?0:Math.max(0,state.economicDay-days);
            for(int i=0;i<daily.size();i++)if(first+i>=cutoff)result.add(new Point(first+i,0,daily.get(i)));
        }
        var live=today.get(key);
        if(live!=null&&!live.isEmpty()&&live.getLast().slot>0)result.add(live.getLast());
        return List.copyOf(result);
    }
    public double dailyChange(String ticker,double price) {return 100*(price/open.get(ticker)-1);}
    void write(Properties p) {
        p.setProperty("live.slot",Integer.toString(slot));p.setProperty("live.since_day",Long.toString(sinceDay));
        p.setProperty("live.since_slot",Integer.toString(sinceSlot));p.setProperty("live.previous_regime",previousRegime.name());
        p.setProperty("live.event",closingEvent.name());
        for(String key:KEYS) {
            p.setProperty("live.open."+key,Double.toString(open.get(key)));
            p.setProperty("live.today."+key,encode(today.get(key)));p.setProperty("live.yesterday."+key,encode(yesterday.get(key)));
            p.setProperty("live.archive."+key,encode(archive.get(key)));
            p.setProperty("live.stride."+key,Long.toString(archiveStride.get(key)));
        }
    }
    static LiveMarket read(Properties p) {
        var live=new LiveMarket();live.slot=Integer.parseInt(required(p,"live.slot"));
        live.sinceDay=Long.parseLong(required(p,"live.since_day"));live.sinceSlot=Integer.parseInt(required(p,"live.since_slot"));
        live.previousRegime=EconomyEngine.Regime.valueOf(required(p,"live.previous_regime"));
        live.closingEvent=EconomyEngine.MarketEvent.valueOf(required(p,"live.event"));
        for(String key:KEYS) {
            live.open.put(key,Double.parseDouble(required(p,"live.open."+key)));
            live.today.put(key,decode(required(p,"live.today."+key),SLOTS+1));
            live.yesterday.put(key,decode(required(p,"live.yesterday."+key),SLOTS+1));
            live.archive.put(key,decode(required(p,"live.archive."+key),ARCHIVE_LIMIT));
            live.archiveStride.put(key,Long.parseLong(required(p,"live.stride."+key)));
        }
        return live;
    }
    private static String required(Properties p,String key) {return Objects.requireNonNull(p.getProperty(key),"Missing "+key);}
    private static String encode(List<Point> points) {
        return String.join(";",points.stream().map(v->v.day+","+v.slot+","+v.value).toList());
    }
    private static List<Point> decode(String text,int limit) {
        if(text.length()>limit*80)throw new IllegalArgumentException("Oversized live history");
        var result=new ArrayList<Point>();if(text.isEmpty())return result;
        var rows=text.split(";",-1);if(rows.length>limit)throw new IllegalArgumentException("Too many live points");
        for(String row:rows) {var v=row.split(",",-1);if(v.length!=3)throw new IllegalArgumentException("Invalid live point");
            result.add(new Point(Long.parseLong(v[0]),Integer.parseInt(v[1]),Double.parseDouble(v[2])));}
        return result;
    }
    void validate(EconomyState state) throws IOException {
        if(slot<0||slot>=SLOTS||sinceDay<0||sinceDay>state.economicDay||sinceSlot<0||sinceSlot>=SLOTS
                ||previousRegime==null||closingEvent!=EconomyEngine.MarketEvent.NONE)throw new IOException("Invalid live session");
        var prices=quotes(state);
        for(String key:KEYS) {
            if(!positive(open.get(key))||archiveStride.getOrDefault(key,0L)<=0)throw new IOException("Invalid live baseline");
            validatePoints(today.get(key),state.economicDay,SLOTS+1);
            validatePoints(yesterday.get(key),state.economicDay,SLOTS+1);
            validatePoints(archive.get(key),state.economicDay,ARCHIVE_LIMIT);
            var current=today.get(key);
            if(current.isEmpty()||current.getLast().day!=state.economicDay||current.getLast().slot!=slot
                    ||Double.compare(current.getLast().value,prices.get(key))!=0)throw new IOException("Quote/tape mismatch: "+key);
            int start=state.economicDay==sinceDay?sinceSlot:0;
            if(current.size()!=slot-start+1||current.getFirst().slot!=start
                    ||Double.compare(current.getFirst().value,open.get(key))!=0)throw new IOException("Invalid session opening");
            for(int i=0;i<current.size();i++)if(current.get(i).day!=state.economicDay
                    ||current.get(i).slot!=start+i)throw new IOException("Non-contiguous current tape");
            var prior=yesterday.get(key);
            if(state.economicDay==sinceDay) {
                if(!prior.isEmpty())throw new IOException("Invented previous session");
            } else {
                int priorStart=state.economicDay-1==sinceDay?sinceSlot:0;
                if(prior.size()!=SLOTS-priorStart+1)throw new IOException("Incomplete previous session");
                for(int i=0;i<prior.size();i++)if(prior.get(i).day!=state.economicDay-1
                        ||prior.get(i).slot!=priorStart+i)throw new IOException("Wrong previous session");
                if(Double.compare(prior.getLast().value,open.get(key))!=0)throw new IOException("Discontinuous opening");
            }
            var daily=key.startsWith("~")?state.commodityHistory.get(key.substring(1)):state.priceHistory.get(key);
            long firstDaily=state.economicDay-daily.size()+1;
            for(var point:archive.get(key))if(point.slot!=0||point.day>=firstDaily)throw new IOException("Overlapping archive");
        }
    }
    private static void validatePoints(List<Point> points,long day,int limit) throws IOException {
        if(points==null||points.size()>limit)throw new IOException("Invalid live history size");
        Point previous=null;
        for(var p:points) {
            if(p.day<0||p.day>day||p.slot<0||p.slot>SLOTS||!positive(p.value)
                    ||previous!=null&&(p.day<previous.day||p.day==previous.day&&p.slot<=previous.slot))throw new IOException("Invalid live history point");
            previous=p;
        }
    }
    private static boolean positive(Double d) {return d!=null&&Double.isFinite(d)&&d>0;}
}
