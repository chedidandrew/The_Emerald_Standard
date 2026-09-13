package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.client.*;
import java.nio.file.*;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

/** Controlled executions for the review's reproduced defects and a readable 30-edition corpus. */
public final class NewsroomRegressionTest {
    private static final UUID V=UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static void main(String[] args)throws Exception {
        rotation();voices();incidents();continuations();civic();reader();corpus(args.length==0?Path.of("."):Path.of(args[0]));
        System.out.println("PASS NewsroomRegressionTest: pool exhaustion, five voices, exact incidents, changed sequels, civic evidence, privacy, edition diversity and 30 issues");
    }
    static void rotation() {
        for(int size:List.of(1,2,4,12,32)) {
            var s=EconomyState.fresh(112,0,0);var titles=new ArrayList<String>();
            for(int i=0;i<size;i++)titles.add("Choice "+i);
            var sequence=new ArrayList<String>();
            for(int i=0;i<size*4;i++) {
                s.economicDay=i;String chosen=NewsWire.choose(s,"TEST",titles);sequence.add(chosen);
                NewsWire.append(s,new NewsWire.Article(i,NewsWire.Kind.ROUNDUP,"TEST",NewsWire.OUTLETS.getFirst(),"","",0,chosen,""));
                if(size>1&&i>0)require(!chosen.equals(sequence.get(i-1)),"immediate repeat after exhausting "+size);
            }
            for(int i=0;i<4;i++)require(new HashSet<>(sequence.subList(i*size,(i+1)*size)).size()==size,"not true latest-use rotation");
        }
        var s=EconomyState.fresh(112,0,0);var headlines=new ArrayList<String>();
        for(int i=1;i<=36;i++) {
            s.economicDay=2*i;var before=Map.copyOf(s.prices);s.prices.replaceAll((k,v)->v*1.01);
            NewsWire.day(s,EconomyEngine.MarketEvent.NONE,before);headlines.add(s.news.getLast().headline());
        }
        for(int i=0;i<3;i++)require(new HashSet<>(headlines.subList(i*12,(i+1)*12)).size()==12,"24-roundup review failure");
    }
    static void voices() {
        var s=EconomyState.fresh(11,0,0);
        for(String publisher:NewsWire.OUTLETS)for(String direction:List.of("UP","DOWN")) {
            String body=NewsNarrative.roundup(s,"ROUNDUP_"+direction,publisher,"VILX: +1.00% today");
            for(String headline:List.of("46 villagers","three buckets")) {
                String specific=NewsColumns.column(s,publisher,headline);
                require(specific.startsWith(NewsColumns.byline(publisher)),"headline-specific column borrowed another publisher");
            }
            require(body.contains(NewsColumns.byline(publisher)),"roundup's body voice differs from masthead");
            for(String other:NewsWire.OUTLETS)if(!other.equals(publisher))
                require(!body.contains(NewsColumns.byline(other)),"foreign columnist in article");
        }
        for(int i=0;i<5;i++) {
            s.editor.templates=NewsEditorial.validateTemplates(Map.of("VOICE_"+i,List.of("CUSTOM_"+i)));
            String body=NewsNarrative.roundup(s,"ROUNDUP_UP",NewsWire.OUTLETS.get(i),"VILX: +1.00% today");
            require(body.contains("CUSTOM_"+i),"publisher-specific resource override");
        }
        s.editor.templates=Map.of();
        Set<String> voices=new HashSet<>();
        for(int i=0;i<20;i++) {
            s.economicDay=i;String c=NewsColumns.column(s,NewsWire.OUTLETS.get(i%5),"");voices.add(c);
            NewsWire.append(s,new NewsWire.Article(i,NewsWire.Kind.ROUNDUP,"ROUNDUP_UP",NewsWire.OUTLETS.get(i%5),"","",0,"Roundup",c));
        }
        require(voices.size()==20,"semantic column rotation");
    }
    static void incidents()throws Exception {
        var s=EconomyState.fresh(77,0,0);var v=s.village(V);v.foodSupply=50;UUID player=UUID.randomUUID();
        s.economicDay=1;
        NewsWire.player(s,NewsWire.Kind.DAMAGE,V,player,"Builder",4,"project:3:42");
        long damage=s.news.getLast().id();
        s.economicDay=3;v.foodSupply=99;NewsEditorial.followups(s);
        require(s.news.size()==1&&s.news.getFirst().id()==damage,"damage became an unrelated food follow-up");
        NewsWire.player(s,NewsWire.Kind.CROPS,V,player,"Farmer",2,"crop:91");long crop=s.news.getLast().id();
        NewsWire.player(s,NewsWire.Kind.REPLANTED,V,player,"Farmer",1,"crop:92");
        require(s.news.getLast().sourceId()==0,"unrelated planting linked by village only");
        NewsWire.player(s,NewsWire.Kind.REPLANTED,V,player,"Farmer",1,"crop:91");
        require(s.news.getLast().sourceId()==crop&&s.news.getLast().text().contains("same crop position"),"same crop continuation lost");
        NewsWire.player(s,NewsWire.Kind.FOOD_REMOVED,V,player,"Carrier",9,"store:81");long food=s.news.getLast().id();
        NewsWire.player(s,NewsWire.Kind.FOOD_RETURNED,V,player,"Carrier",8,"store:82");
        require(s.news.getLast().sourceId()==0,"different stores conflated");
        NewsWire.player(s,NewsWire.Kind.FOOD_RETURNED,V,UUID.randomUUID(),"Helper",8,"store:81");
        require(s.news.getLast().sourceId()==food,"same store delivery missing");
        require(!s.news.getLast().text().contains("repaired"),"restocking claimed a repair");
        var p=new Properties();p.setProperty("format","33");NewsWire.write(s,p);
        var r=EconomyState.fresh(77,0,0);r.economicDay=s.economicDay;NewsWire.read(r,p);
        require(r.news.equals(s.news)&&r.editor.nextId==s.editor.nextId,"incident IDs/subjects not durable");
        String visible=new NewsEditorial.Policy(true,true,true).text(s.news.getLast());
        require(!visible.contains("Helper")&&!visible.contains("store:81")&&!visible.contains(V.toString()),"private incident key leaked");
    }
    static void continuations()throws Exception {
        var s=EconomyState.fresh(11,0,0);s.economicDay=1;var before=Map.copyOf(s.prices);
        NewsWire.day(s,EconomyEngine.MarketEvent.NETHER_SUPPLY_CRISIS,before);var source=s.news.getLast();
        s.economicDay=3;int count=s.news.size();NewsEditorial.followups(s);
        require(s.news.size()==count,"timer alone manufactured a development");
        s.prices.put("NETH",before.get("NETH")*1.08);NewsEditorial.followups(s);
        require(s.news.getLast().sourceId()==source.id(),"market continuation source missing");
        require(s.news.getLast().text().contains(source.headline())&&s.news.getLast().detail().contains("+8.00"),"subject/delta missing");
        count=s.news.size();s.economicDay=8;NewsEditorial.followups(s);
        require(s.news.size()==count,"unchanged weekly comparison repeated");
        var p=new Properties();p.setProperty("format","33");NewsWire.write(s,p);
        var r=EconomyState.fresh(11,0,0);r.economicDay=s.economicDay;r.prices.putAll(s.prices);NewsWire.read(r,p);
        NewsEditorial.followups(r);require(r.news.size()==count,"reload forgot last published comparison");
        r.economicDay=9;r.prices.put("NETH",before.get("NETH")*1.02);NewsEditorial.followups(r);
        require(r.news.getLast().detail().contains("-6.00 percentage points"),"since-last change wrong");
        require(!r.news.getLast().detail().contains("routes have reopened"),"quote recovery invented cargo recovery");
        r.economicDay=30;NewsEditorial.followups(r);require(r.editor.stories.isEmpty(),"unresolved stories never expired");
    }
    static EconomyState.VillageProject project(long id) {
        var p=new EconomyState.VillageProject();p.projectId=id;p.originPos=123L+id;p.totalBlocks=100;p.constructionStarted=false;
        p.type=VillageProsperityEngine.ProjectType.HOUSE;return p;
    }
    static void civic()throws Exception {
        var s=EconomyState.fresh(55,0,0);var v=s.village(V);var p=project(1);v.projects.add(p);
        NewsCivic.day(s);require(s.news.isEmpty(),"invented historical project event on first observation");
        s.economicDay=1;p.constructionStarted=true;p.materializedBlocks=1;NewsCivic.day(s);
        require(s.news.getLast().family().equals("CIVIC_START"),"actual start missing");long first=s.news.getLast().id();
        p.materializedBlocks=60;s.economicDay=2;NewsCivic.day(s);
        require(s.news.getLast().family().equals("CIVIC_HALFWAY")&&s.news.getLast().sourceId()==first,"progress lacks continuity");
        s.economicDay=3;p.economicComplete=true;NewsCivic.day(s);
        require(!s.news.getLast().family().equals("CIVIC_OPEN"),"paid labor declared a completed house");
        p.materializedBlocks=100;p.materializedComplete=true;s.economicDay=4;NewsCivic.day(s);
        require(s.news.getLast().family().equals("CIVIC_OPEN"),"physical opening missing");
        require(new NewsEditorial.Policy(false,true,true).text(s.news.getLast())!=null,"player privacy suppressed civic news");
        p.manualRepairRequired=true;s.economicDay=5;NewsCivic.day(s);
        require(s.news.getLast().family().equals("CIVIC_REPAIR"),"verified repair need missing");
        int n=s.news.size();s.economicDay=6;v.foodSupply=100;NewsCivic.day(s);
        require(s.news.size()==n,"food changed building repair status");
        p.manualRepairRequired=false;s.economicDay=7;NewsCivic.day(s);
        require(s.news.getLast().family().equals("CIVIC_RESTORED"),"verified restored template missing");
        var props=new Properties();props.setProperty("format","33");NewsWire.write(s,props);
        var r=s.copy();r.news.clear();r.editor=new NewsEditorial.Editor();NewsWire.read(r,props);
        NewsCivic.day(r);require(r.news.equals(s.news),"restart duplicated milestones");
        var a=project(2);a.abstractOnly=true;v.projects.add(a);s.economicDay=8;NewsCivic.day(s);
        require(s.news.size()==n+1,"abstract project reported as real building");
    }
    static void reader() {
        var entries=new ArrayList<NewsReader.Entry>();
        for(int i=1;i<=4;i++)entries.add(new NewsReader.Entry(i,NewsEditorial.Section.MARKETS,
                "The Nether Post | Day 10\nWorld\n\nFreight "+i+"\n\nBody",10,70,NewsIllustration.TRADE,"NETHER_SUPPLY_CRISIS",i==1?0:1,false,false));
        entries.add(new NewsReader.Entry(5,NewsEditorial.Section.LOCAL,"The Overworld Observer | Day 10\nLocal\n\nHouse opens\n\nBody",
                10,65,NewsIllustration.COMMUNITY,"CIVIC_OPEN",0,true,false));
        entries.add(new NewsReader.Entry(6,NewsEditorial.Section.COMMUNITY,"The Daily Gravel | Day 10\nColumn\n\nAdvice\n\nBody",
                10,10,NewsIllustration.MARKETS,"FEATURE_4",0,false,true));
        entries.add(new NewsReader.Entry(7,NewsEditorial.Section.COMMUNITY,"The Overworld Observer | Day 10\nLocal\n\nGift\n\nBody",
                10,45,NewsIllustration.COMMUNITY,"DONATION",0,true,false));
        var ranked=NewsReader.ranked(entries);
        require(ranked.getFirst().id()==5,"local meaningful opening lost to remote market repetition");
        require(ranked.subList(0,3).stream().map(NewsReader.Entry::topic).distinct().count()==3,"front page repetitive");
        require(ranked.get(4).feature(),"no light feature slot");
        require(new HashSet<>(ranked).equals(new HashSet<>(entries)),"ranking hid archive stories");
        for(var e:entries)require(NewsReader.Entry.parse(e.wire()).equals(e),"editorial metadata roundtrip");
        var e=new NewsReader.Entry(20,NewsEditorial.Section.MARKETS,"Ledger | Day 1\nWorld\n\nTitle\n\nStory\n\nFrom the notebook\nVILX: +1%");
        require(NewsReader.storyText(e).endsWith("Story")&&NewsReader.facts(e).equals("VILX: +1%"),"notebook partition");
    }
    static void corpus(Path root)throws Exception {
        var s=EconomyState.fresh(112,0,0);var v=s.village(V);v.centerPos=1234;var p=project(1);v.projects.add(p);
        NewsCivic.day(s);var out=new StringBuilder("# Thirty controlled newspaper editions\n\nNot a player-world capture.\n\n");
        var events=Arrays.stream(EconomyEngine.MarketEvent.values()).filter(e->e!=EconomyEngine.MarketEvent.NONE).toList();
        Set<String> bylines=new HashSet<>(),features=new HashSet<>();
        for(int day=1;day<=30;day++) {
            s.economicDay=day;var before=Map.copyOf(s.prices);
            s.prices.replaceAll((k,value)->value*(dayEven(s.economicDay)?1.035:.96));
            if(day==2){p.constructionStarted=true;p.materializedBlocks=1;}if(day==4)p.materializedBlocks=60;
            if(day==6){p.economicComplete=true;p.materializedBlocks=100;p.materializedComplete=true;}
            NewsWire.day(s,day<=17?events.get(day-1):EconomyEngine.MarketEvent.NONE,before);
            out.append("## Edition ").append(day).append("\n\n");
            for(var a:s.news)if(a.day()==day) {
                String text=a.text();out.append(text).append("\n\n---\n\n");bylines.add(NewsColumns.byline(a.outlet()));
                if(a.kind()==NewsWire.Kind.FEATURE)features.add(a.headline());
                for(String bad:List.of("satire","simulated","this bulletin reports","@{actor}","safety cooldown"))
                    require(!text.toLowerCase(Locale.ROOT).contains(bad),"immersion regression: "+bad);
                require(a.detail().length()<=6000&&text.length()<=10000,"unbounded story");
            }
        }
        require(bylines.size()==5&&features.size()==15,"30 editions lack outlet/feature variety");
        var dir=root.resolve("build/reports");Files.createDirectories(dir);
        Files.writeString(dir.resolve("newsroom-30-editions.md"),out);
    }
    private static boolean dayEven(long day){return day%2==0;}
}
