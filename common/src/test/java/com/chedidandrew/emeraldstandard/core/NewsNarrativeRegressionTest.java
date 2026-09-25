package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.client.*;
import java.nio.file.*;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

public final class NewsNarrativeRegressionTest {
    private static void story(NewsWire.Article a) {
        String text=a.text(),body=text.split("\n",6)[5];
        int words=body.trim().split("\\s+").length;
        require(words>=80&&words<=650,"developed article length "+a.kind()+"/"+a.family()+": "+words);
        require(body.split("\n\n").length>=5,"paragraph structure");
        String lower=text.toLowerCase(Locale.ROOT);
        for(String forbidden:List.of("headlines are satire","opinion / satire","this bulletin reports","simulated",
                "off-screen","not proof","not a count","no automatic fine","editorial commentary","@{actor}"))
            require(!lower.contains(forbidden),"immersion: "+forbidden);
        require(text.length()<10000&&a.detail().length()<=6000,"bounded body and transport");
        var entry=new NewsReader.Entry(a.id(),NewsEditorial.section(a),text,a.day(),70,NewsIllustration.forArticle(a));
        require(NewsReader.Entry.parse(entry.wire()).equals(entry),"art-aware envelope");
    }
    public static void main(String[] args) throws Exception {
        var s=EconomyState.fresh(411,0,0);
        var prices=Map.copyOf(s.prices);
        var openings=new HashSet<String>(); var paragraphCounts=new HashSet<Integer>();
        for (int day=0;day<64;day++) {
            s.economicDay=day;
            String body=NewsNarrative.market(s,"NETHER_SUPPLY_CRISIS",NewsWire.OUTLETS.getFirst(),"Cargo delayed","NETH: +2.34% today");
            require(body.equals(NewsNarrative.market(s,"NETHER_SUPPLY_CRISIS",NewsWire.OUTLETS.getFirst(),"Cargo delayed","NETH: +2.34% today")),"deterministic dispatch");
            require(body.contains("NETH: +2.34% today")&&!body.contains("routes have reopened"),"composition preserves quotation and event");
            openings.add(body.split("\n\n")[0]); paragraphCounts.add(body.split("\n\n").length);
            story(new NewsWire.Article(1000+day,day,NewsWire.Kind.MARKET,"NETHER_SUPPLY_CRISIS",NewsWire.OUTLETS.getFirst(),"","",0,"Cargo delayed",body));
        }
        require(openings.size()>=3&&paragraphCounts.size()>=3,"full articles vary openings and length, not only headlines");
        s.economicDay=0;
        for(var event:EconomyEngine.MarketEvent.values())if(event!=EconomyEngine.MarketEvent.NONE) {
            NewsWire.day(s,event,prices);
            story(s.news.getLast());
        }
        for(String family:List.of("ROUNDUP_UP","ROUNDUP_DOWN")) {
            String body=NewsNarrative.roundup(s,family,NewsWire.OUTLETS.getFirst(),"VILX: +0.00% today");
            story(new NewsWire.Article(999,0,NewsWire.Kind.ROUNDUP,family,NewsWire.OUTLETS.getFirst(),"","",0,"A close",body));
        }
        UUID village=UUID.fromString("10000000-0000-0000-0000-000000000001"),player=UUID.randomUUID();
        var v=s.village(village);v.centerPos=((long)-1484<<38)|((long)1180<<12)|71;v.foodSupply=25;
        for(var kind:NewsWire.Kind.values())if(NewsWire.isPlayer(kind)) {
            NewsWire.player(s,kind,village,player,"NamedResident",1);
            var a=s.news.getLast();story(a);
            String anonymous=new NewsEditorial.Policy(true,true,true).text(a);
            require(!anonymous.contains("to An ")&&!anonymous.contains("by An "),"grammatical anonymous attribution");
            require(!anonymous.contains("NamedResident")&&!anonymous.contains("-1484")&&!anonymous.contains("1180")
                    &&!anonymous.contains(player.toString())&&!anonymous.contains(village.toString()),"privacy across narrative");
            require(a.text().contains("NamedResident")&&a.text().contains("-1484"),"explicit disclosure");
            require(new NewsEditorial.Policy(false,false,false).text(a)==null,"hidden local");
            require(NewsIllustration.forArticle(a)==(kind==NewsWire.Kind.VIOLENCE?NewsIllustration.MEMORIAL:NewsIllustration.COMMUNITY),"appropriate art");
        }
        s.economicDay=2;v.foodSupply=30;NewsEditorial.followups(s);
        for(var a:s.news)if(a.kind()==NewsWire.Kind.FOLLOW_UP)story(a);
        s.economicDay=7;v.foodSupply=20;NewsEditorial.followups(s);
        for(var a:s.news)if(a.day()==7)story(a);
        require(s.prices.equals(prices),"prose cannot alter money");
        for(var kind:List.of(NewsWire.Kind.DONATION,NewsWire.Kind.VIOLENCE,NewsWire.Kind.FOOD_REMOVED)) {
            Set<String> variants=new HashSet<>();
            for(int day=0;day<32;day++)variants.add(NewsNarrative.local(kind,8,411,day,"fixed","From a local village.",""));
            require(variants.size()==4,"deterministic local variety");
        }
        var legacy=new NewsWire.Article(900,1,NewsWire.Kind.VIOLENCE,"PLAYER_VIOLENCE_x",NewsWire.OUTLETS.get(3),
                village.toString(),"NamedResident",2,"Community counts the cost of violence",
                "Recorded player: NamedResident.\nDistrict near X -1484, Z 1180.\nConfirmed total: 2 villager deaths.\n\n"
                +"This bulletin reports observed actions, not intent or permission between players. Headlines are satire. No automatic fine is imposed.");
        story(legacy);
        String privateLegacy=new NewsEditorial.Policy(true,true,true).text(legacy);
        require(privateLegacy.contains("2 residents")&&!privateLegacy.contains("NamedResident")&&!privateLegacy.contains("1180"),"legacy narrative privacy");
        require(legacy.detail().contains("Headlines are satire"),"rendering must not mutate saved article");
        String oldMarket="Portal shipments delayed. This is a simulated off-screen trade event, not a report of damage to your village.\n\nVILX: -2.00% today\n"
                +"This measures the price path, not proof that a supply disruption has ended or the event alone caused the move.";
        var old=new NewsWire.Article(901,1,NewsWire.Kind.MARKET,"NETHER_SUPPLY_CRISIS",NewsWire.OUTLETS.get(2),"","",0,"Freight delayed",oldMarket);
        story(old);
        require(old.text().contains("VILX: -2.00% today")&&!old.text().contains("off-screen")&&!old.text().contains("not proof"),"legacy quotes retained without boilerplate");

        var archive=new ArrayList<NewsReader.Entry>();
        for(int i=0;i<256;i++)archive.add(new NewsReader.Entry(i+1,NewsEditorial.Section.MARKETS,
                "Ledger | Day 1\nWorld\n\nLong article\n\n"+"x".repeat(6000),1,70,NewsIllustration.TRADE));
        var reader=new NewsReader();reader.receive(archive,1);
        require(reader.edition().size()==256&&NewsReader.Entry.parse(archive.getLast().wire()).equals(archive.getLast()),"full long archive");
        require(NewsReader.Entry.parse("1|MARKETS|1|20\nOld").illustration()==NewsIllustration.MARKETS,"old envelope");
        boolean rejected=false;
        try{NewsReader.Entry.parse("1|MARKETS|1|20|../../secret\nBad");}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"illustration allowlist");
        Path root=args.length==0?Path.of("."):Path.of(args[0]);
        for(var art:NewsIllustration.values()) {
            var file=root.resolve("common/src/main/resources/assets/the_emerald_standard/"+art.texture());
            var image=javax.imageio.ImageIO.read(file.toFile());
            require(image!=null&&image.getWidth()>=512&&Math.abs(image.getWidth()/(double)image.getHeight()-2)<0.02,"landscape illustration "+art);
        }
        System.out.println("PASS NewsNarrativeRegressionTest: developed stories, tone, variety, legacy/privacy, art and 256 long reports");
    }
}
