package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import java.util.function.BiFunction;

/** Bounded display-only packet: real dated observations, never client-generated quotes. */
public final class MarketDisplay {
    public static final int[] RANGES={1,30,90,365,1095,1825,3650,Integer.MAX_VALUE};
    public static final String[] LABELS={"1 Day","30 Days","90 Days","1 Year","3 Years","5 Years","10 Years","All"};
    public static final int POINTS=81;
    public record Quote(String ticker,double price,double daily,boolean known) {}
    public record Curve(long firstDay,long lastDay,List<Double> left,List<Double> right,List<Double> positions) {
        public Curve {left=List.copyOf(left);right=List.copyOf(right);positions=List.copyOf(positions);}
        public Curve(long first,long last,List<Double> a,List<Double> b){this(first,last,a,b,uniform(a.size()));}
    }
    public record Series(long firstDay,long lastDay,List<Double> values,List<Double> positions,double reference,
                         long availableDay,int availableSlot) {
        public Series {values=List.copyOf(values);positions=List.copyOf(positions);}
    }
    public record Snapshot(long day,int slot,boolean yesterday,String selected,String left,String right,
                           List<Quote> quotes,List<Curve> curves,List<Series> series) {
        public Snapshot {quotes=List.copyOf(quotes);curves=List.copyOf(curves);series=List.copyOf(series);}
        public Quote quote(String ticker){return quotes.stream().filter(q->q.ticker().equals(ticker)).findFirst().orElse(null);}
        public List<String> pages() {
            List<String> result=new ArrayList<>();
            var header=new StringBuilder(day+"|"+slot+"|"+yesterday+"|"+selected+"|"+left+"|"+right);
            for(var q:quotes)header.append("\n").append(q.ticker()).append("|").append(q.price()).append("|").append(q.daily()).append("|").append(q.known());
            result.add(header.toString());
            for(var c:curves)result.add(c.firstDay()+"|"+c.lastDay()+"\n"+encode(c.left())+"\n"+encode(c.right())+"\n"+encode(c.positions()));
            for(var s:series)result.add(s.firstDay()+"|"+s.lastDay()+"|"+s.reference()+"|"+s.availableDay()+"|"+s.availableSlot()
                    +"\n"+encode(s.values())+"\n"+encode(s.positions()));
            return List.copyOf(result);
        }
    }
    public static Snapshot build(EconomyState state,String selected,String left,String right,boolean yesterday) {
        var live=state.liveMarket;
        return build(state.economicDay,live.slot,yesterday,selected,left,right,state.prices,live.open,
                live.today.get(left).getFirst().slot()==0,
                (key,range)->live.history(state,key,range,yesterday));
    }
    /** Daily-only fixture/legacy producer. Does not invent an intraday tape. */
    public static Snapshot build(long day,Map<String,Double> prices,Map<String,List<Double>> histories,String left,String right) {
        Map<String,Double> opens=new HashMap<>();
        prices.forEach((key,value)->{var h=histories.getOrDefault(key,List.of());opens.put(key,h.size()>1?h.get(h.size()-2):value);});
        return build(day,0,false,left,left,right,prices,opens,day>0,(key,range)->{
            if(range==1)return List.of(new LiveMarket.Point(day,0,prices.get(key)));
            var h=histories.getOrDefault(key,List.of());var result=new ArrayList<LiveMarket.Point>();
            int count=Math.min(h.size(),range==Integer.MAX_VALUE?range:range+1);
            for(int i=h.size()-count;i<h.size();i++)result.add(new LiveMarket.Point(Math.max(0,day-h.size()+i+1),0,h.get(i)));
            return result;
        });
    }
    private static Snapshot build(long day,int slot,boolean yesterday,String selected,String left,String right,
                                  Map<String,Double> prices,Map<String,Double> opens,boolean fullDay,
                                  BiFunction<String,Integer,List<LiveMarket.Point>> history) {
        if(!known(left)||!known(right)||!LiveMarket.KEYS.contains(selected))throw new IllegalArgumentException("Unknown investment");
        var quotes=new ArrayList<Quote>();
        for(var a:EconomyEngine.ASSETS)quotes.add(new Quote(a.ticker(),prices.get(a.ticker()),100*(prices.get(a.ticker())/opens.get(a.ticker())-1),fullDay));
        var curves=new ArrayList<Curve>();var series=new ArrayList<Series>();
        for(int range:RANGES) {
            var a=history.apply(left,range);var b=history.apply(right,range);
            // Intersection by exact date/slot; unequal listing dates or archives cannot be silently shifted.
            var sharedA=new ArrayList<LiveMarket.Point>();var sharedB=new ArrayList<LiveMarket.Point>();
            int i=0,j=0;
            while(i<a.size()&&j<b.size()){
                var x=a.get(i);var y=b.get(j);int order=compareTime(x,y);
                if(order==0){sharedA.add(x);sharedB.add(y);i++;j++;}else if(order<0)i++;else j++;
            }
            long first=range==1?Math.max(0,day-(yesterday?1:0)):sharedA.isEmpty()?day:sharedA.getFirst().day();
            long last=range==1?first+1:day+(slot>0?1:0);
            var x=new ArrayList<Double>();var y=new ArrayList<Double>();var positions=new ArrayList<Double>();
            for(int offset:sample(sharedA.size())) {
                x.add(offset==0?100.0:100*sharedA.get(offset).value()/sharedA.getFirst().value());
                y.add(offset==0?100.0:100*sharedB.get(offset).value()/sharedB.getFirst().value());
                positions.add(position(sharedA.get(offset),first,last));
            }
            curves.add(new Curve(first,last,x,y,positions));
            var h=history.apply(selected,range);var values=new ArrayList<Double>();var times=new ArrayList<Double>();
            first=range==1?Math.max(0,day-(yesterday?1:0)):h.isEmpty()?day:h.getFirst().day();
            last=range==1?first+1:day+(slot>0?1:0);
            for(int offset:sample(h.size())){values.add(h.get(offset).value());times.add(position(h.get(offset),first,last));}
            double reference=h.isEmpty()?0:h.getFirst().value();
            series.add(new Series(first,last,values,times,reference,h.isEmpty()?day:h.getFirst().day(),h.isEmpty()?0:h.getFirst().slot()));
        }
        return new Snapshot(day,slot,yesterday,selected,left,right,quotes,curves,series);
    }
    private static int compareTime(LiveMarket.Point a,LiveMarket.Point b){
        // An end-of-day slot 80 and the next day's slot zero are the same instant.
        long ad=a.day()+(a.slot()==LiveMarket.SLOTS?1:0),bd=b.day()+(b.slot()==LiveMarket.SLOTS?1:0);
        int result=Long.compare(ad,bd);return result!=0?result:Integer.compare(a.slot()%LiveMarket.SLOTS,b.slot()%LiveMarket.SLOTS);
    }
    private static double position(LiveMarket.Point p,long first,long last){return Math.max(0,Math.min(1,p.distanceFrom(first)/Math.max(1,last-first)));}
    private static List<Integer> sample(int count){
        var result=new ArrayList<Integer>();int size=Math.min(POINTS,count);
        for(int i=0;i<size;i++)result.add(size==1?0:(int)((long)i*(count-1)/(size-1)));return result;
    }
    private static List<Double> uniform(int count){var result=new ArrayList<Double>();for(int i=0;i<count;i++)result.add(count==1?0:i/(double)(count-1));return result;}
    private static String encode(List<Double> values){return String.join(",",values.stream().map(Object::toString).toList());}
    public static Snapshot parse(List<String> pages) {
        if(pages.size()!=1+2*RANGES.length||pages.stream().anyMatch(s->s.length()>16000))throw new IllegalArgumentException("Market document size");
        String[] lines=pages.getFirst().split("\n",-1),h=lines[0].split("\\|",-1);
        if(h.length!=6)throw new IllegalArgumentException("Market header");
        long day=Long.parseLong(h[0]);int slot=Integer.parseInt(h[1]);boolean yesterday=bool(h[2]);
        if(day<0||day==Long.MAX_VALUE||slot<0||slot>=LiveMarket.SLOTS||!LiveMarket.KEYS.contains(h[3])||!known(h[4])||!known(h[5])
                ||lines.length!=EconomyEngine.ASSETS.size()+1)throw new IllegalArgumentException("Catalog/clock");
        var quotes=new ArrayList<Quote>();Set<String> tickers=new HashSet<>();
        for(int i=1;i<lines.length;i++) {
            String[] q=lines[i].split("\\|",-1);
            if(q.length!=4||!known(q[0])||!tickers.add(q[0]))throw new IllegalArgumentException("Quote");
            double price=number(q[1]),daily=number(q[2]);if(price<=0||daily< -100)throw new IllegalArgumentException("Quote bounds");
            quotes.add(new Quote(q[0],price,daily,bool(q[3])));
        }
        var curves=new ArrayList<Curve>();var series=new ArrayList<Series>();
        for(int i=0;i<RANGES.length;i++){
            var c=pages.get(i+1).split("\n",-1);if(c.length!=4)throw new IllegalArgumentException("Curve");
            var d=c[0].split("\\|",-1);if(d.length!=2)throw new IllegalArgumentException("Curve dates");
            long first=Long.parseLong(d[0]),last=Long.parseLong(d[1]);
            var a=decode(c[1],false);var b=decode(c[2],false);var positions=decode(c[3],true);
            dates(first,last,day);checkPositions(positions,a.size());
            if(a.size()!=b.size()||(!a.isEmpty()&&(a.getFirst()!=100.0||b.getFirst()!=100.0)))throw new IllegalArgumentException("Normalization");
            curves.add(new Curve(first,last,a,b,positions));
            var s=pages.get(i+1+RANGES.length).split("\n",-1);if(s.length!=3)throw new IllegalArgumentException("Series");
            d=s[0].split("\\|",-1);if(d.length!=5)throw new IllegalArgumentException("Series dates");
            first=Long.parseLong(d[0]);last=Long.parseLong(d[1]);double reference=number(d[2]);long available=Long.parseLong(d[3]);int startSlot=Integer.parseInt(d[4]);
            var v=decode(s[1],false);var p=decode(s[2],true);dates(first,last,day);checkPositions(p,v.size());
            if(available<first||available>last||startSlot<0||startSlot>LiveMarket.SLOTS||reference<0
                    ||!v.isEmpty()&&reference!=v.getFirst())throw new IllegalArgumentException("Series baseline");
            series.add(new Series(first,last,v,p,reference,available,startSlot));
        }
        return new Snapshot(day,slot,yesterday,h[3],h[4],h[5],quotes,curves,series);
    }
    private static void dates(long first,long last,long day){if(first<0||first>last||last>day+1)throw new IllegalArgumentException("Dates");}
    private static void checkPositions(List<Double> p,int size){if(p.size()!=size)throw new IllegalArgumentException("Point count");double old=-1;for(double x:p){if(x<0||x>1||x<=old)throw new IllegalArgumentException("Time order");old=x;}}
    private static List<Double> decode(String s,boolean time) {
        if(s.isEmpty())return List.of();var cells=s.split(",",-1);if(cells.length>POINTS)throw new IllegalArgumentException("Point limit");
        var values=Arrays.stream(cells).map(MarketDisplay::number).toList();
        if(values.stream().anyMatch(d->time?d<0:d<=0))throw new IllegalArgumentException("Curve value");return values;
    }
    private static boolean bool(String s){if(!s.equals("true")&&!s.equals("false"))throw new IllegalArgumentException("Boolean");return Boolean.parseBoolean(s);}
    private static double number(String s){double v=Double.parseDouble(s);if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite");return v;}
    private static boolean known(String t){return EconomyEngine.ASSETS.stream().anyMatch(a->a.ticker().equals(t));}
    private MarketDisplay(){}
}
