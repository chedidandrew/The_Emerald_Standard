package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.client.NewsReader;
import java.nio.file.*;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

public final class NewsEditorialRegressionTest {
    public static void main(String[] args) throws Exception {
        var s=EconomyState.fresh(53,0,0);UUID village=UUID.randomUUID(),player=UUID.randomUUID();
        var v=s.village(village);v.centerPos=((long)1484<<38)|((long)1180<<12)|71;v.foodSupply=25;
        NewsWire.player(s,NewsWire.Kind.CROPS,village,player,"OriginalName",8);
        long id=s.news.getLast().id();
        NewsWire.player(s,NewsWire.Kind.CROPS,village,player,"Renamed",2);
        require(s.news.size()==1&&s.news.getLast().id()==id&&s.news.getLast().quantity()==10,"daily stable ID aggregation");
        String anonymized=new NewsEditorial.Policy(true,true,true).text(s.news.getLast());
        require(!anonymized.contains("Renamed")&&!anonymized.contains("1484")&&!anonymized.contains("1180"),"server privacy");
        require(new NewsEditorial.Policy(false,false,false).text(s.news.getLast())==null,"public reports disabled");
        NewsWire.player(s,NewsWire.Kind.REPLANTED,village,UUID.randomUUID(),"Gardener",3);
        require(s.news.getLast().detail().contains("Developing story")&&s.news.getLast().detail().contains("not proof"),"factual local sequel");
        s.economicDay=1;NewsWire.player(s,NewsWire.Kind.CROPS,village,player,"Renamed",1);
        require(s.news.getLast().id()!=id,"new daily bulletin");
        NewsWire.day(s,EconomyEngine.MarketEvent.NETHER_SUPPLY_CRISIS,Map.copyOf(s.prices));
        long market=s.news.getLast().id();
        for(int i=0;i<600;i++)NewsWire.player(s,NewsWire.Kind.DONATION,village,UUID.randomUUID(),"Donor",64);
        require(s.news.size()==256&&s.news.stream().anyMatch(a->a.id()==market),"reserved market history");
        s.news.clear();s.economicDay=3;NewsEditorial.followups(s);
        require(s.news.stream().anyMatch(a->a.kind()==NewsWire.Kind.FOLLOW_UP&&a.village().isEmpty()),"eviction cannot cancel scheduled follow-up");
        require(s.news.stream().anyMatch(a->a.detail().contains("pre-event")),"follow-up measures since event, not just today");
        s.economicDay=8;v.foodSupply=40;NewsEditorial.followups(s);
        require(s.news.stream().anyMatch(a->a.detail().contains("Simulated food supply")&&a.detail().contains("not a count")),"local food observation not invented repairs");
        require(s.editor.stories.isEmpty(),"developing state expires");
        s.liveMarket=LiveMarket.adopt(s); // News fixture deliberately advances only its editorial date.
        s.validate();

        var reader=new NewsReader();
        var one=new NewsReader.Entry(100,NewsEditorial.Section.MARKETS,"Ledger | Day 1\nWorld\n\nOld headline\n\nBody");
        var two=new NewsReader.Entry(101,NewsEditorial.Section.COMMUNITY,"Observer | Day 2\nLocal\n\nNew headline\n\nBody");
        reader.receive(List.of(one),1);reader.markRead(one);
        reader.receive(List.of(two,one),1);
        require(reader.edition().equals(List.of(one))&&reader.hasUpdates(),"incoming reports interrupt reading");
        reader.accept();require(reader.edition().size()==2&&reader.unreadCount()==1,"unread accepted edition");
        reader.receive(List.of(),0);require(reader.edition().isEmpty()&&!reader.hasUpdates(),"privacy change must not wait for consent");
        require(NewsReader.Entry.parse(one.wire()).equals(one),"entry envelope");
        List<NewsReader.Entry> all=new ArrayList<>();
        for(int i=0;i<256;i++)all.add(new NewsReader.Entry(i+1,NewsEditorial.Section.MARKETS,"Searchable report "+i));
        reader.receive(all,1);require(reader.edition().size()==256&&reader.edition().getLast().text().contains("255"),"entire retained archive accessible");
        boolean invalid=false;try{NewsEditorial.validateTemplates(Map.of("NO_SUCH_EVENT",List.of("bad")));}catch(IllegalArgumentException e){invalid=true;}
        require(invalid,"unknown template key");
        invalid=false;try{NewsEditorial.validateTemplates(Map.of("VOICE_1",List.of("bad\ntext")));}catch(IllegalArgumentException e){invalid=true;}
        require(invalid,"template control characters");
        s.editor.templates=NewsEditorial.validateTemplates(Map.of("VOICE_1",List.of("Technology desk: custom plain text")));
        require(NewsEditorial.voice(s,NewsWire.OUTLETS.get(1)).contains("custom plain text"),"data-driven outlet voice");
        var money=Map.copyOf(s.prices);NewsEditorial.followups(s);require(s.prices.equals(money),"reporting never reprices");

        Path root=Files.createTempDirectory("tes-editor-");
        try {
            var path=root.resolve("economy.properties");s.save(path);
            var service=new EconomyService();service.configurePlayerNews(false);
            service.start(root.resolve("disabled"),42,0,0);
            require(!service.snapshot().editor.playerReports,"pre-start privacy setting lost on world load");
            var restored=EconomyState.load(path,0,0,0);
            require(restored.news.equals(s.news)&&restored.editor.nextId==s.editor.nextId,"stable IDs saved");
            var legacy=RegressionTestSupport.readProperties(path);legacy.setProperty("format","31");
            legacy.keySet().removeIf(k->k.toString().equals("news.next_id")||k.toString().equals("news.stories")
                    ||k.toString().startsWith("news.story.")||k.toString().matches("news.\\d+.id"));
            RegressionTestSupport.refreshChecksum(legacy);RegressionTestSupport.writeProperties(path,legacy);
            var upgraded=EconomyState.load(path,0,0,0);
            require(upgraded.news.size()==s.news.size()&&upgraded.news.getFirst().headline().equals(s.news.getFirst().headline()),"format31 text preserved");
            require(upgraded.editor.nextId==upgraded.news.size()+1&&upgraded.editor.stories.isEmpty(),"migration does not invent baseline");
        } finally {RegressionTestSupport.deleteTree(root);}
        System.out.println("PASS NewsEditorialRegressionTest: stable editions, privacy, reserved history, factual follow-ups, templates and format31 migration");
    }
}
