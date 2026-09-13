package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.*;

/** Editorial state is bounded and never participates in economic pricing. */
public final class NewsEditorial {
    public enum Section { MARKETS, LOCAL, PLAYERS, COMMUNITY }
    public record Story(long source, long day, String family, String headline, String outlet,
                        String village, String ticker, double baseline, int stage) {}
    public static final class Editor {
        public long nextId=1;
        public boolean playerReports=true;
        public final Map<Long,Story> stories=new LinkedHashMap<>();
        public Map<String,List<String>> templates=Map.of();
        public Editor copy() {
            var e=new Editor();e.nextId=nextId;e.playerReports=playerReports;
            e.stories.putAll(stories);e.templates=templates;return e;
        }
    }
    public record Policy(boolean publicPlayers, boolean anonymous, boolean approximate) {
        public int flags() { return (publicPlayers?1:0)|(anonymous?2:0)|(approximate?4:0); }
        public String text(NewsWire.Article a) {
            if(!a.village().isEmpty()&&!publicPlayers) return null;
            return NewsNarrative.text(a,anonymous,approximate);
        }
    }
    private NewsEditorial() {}
    public static Section section(NewsWire.Article a) {
        if(a.village().isEmpty()) return Section.MARKETS;
        return switch(a.kind()) {
            case DONATION, REPLANTED, FOOD_RETURNED -> Section.COMMUNITY;
            case FOLLOW_UP -> Section.LOCAL;
            default -> Section.PLAYERS;
        };
    }
    public static void append(EconomyState s,NewsWire.Article a) {
        if(a.id()==0) {
            if(s.editor.nextId==Long.MAX_VALUE) throw new IllegalStateException("News ID space exhausted");
            a=a.withId(s.editor.nextId++);
        }
        s.news.add(a);
        // Categories borrow unused capacity, but a flood cannot consume another category's reserve.
        int[] quotas={112,32,64,48};
        while(s.news.size()>NewsWire.LIMIT) {
            int[] counts=new int[4];for(var n:s.news)counts[section(n).ordinal()]++;
            int over=0;for(int i=1;i<4;i++)if(counts[i]-quotas[i]>counts[over]-quotas[over])over=i;
            final int victim=over;
            for(int i=0;i<s.news.size();i++)if(section(s.news.get(i)).ordinal()==victim){s.news.remove(i);break;}
        }
    }
    public static String outlet(EconomyState s,String family) {
        if(family.contains("REDSTONE")||family.contains("COPPER")||family.contains("ENCHANT"))return NewsWire.OUTLETS.get(1);
        if(family.contains("NETHER")||family.contains("PORTAL")||family.contains("RAIL")||family.contains("END_"))return NewsWire.OUTLETS.get(2);
        if(family.contains("CREEPER")||family.contains("REBUILD")||family.contains("HARVEST")||family.contains("FISH"))return NewsWire.OUTLETS.get(3);
        if(family.startsWith("ROUNDUP"))return NewsWire.OUTLETS.get((int)(InvestmentGrowth.unit(s.seed,s.economicDay,"editor")*5));
        return NewsWire.OUTLETS.getFirst();
    }
    public static String voice(EconomyState s,String outlet) {
        int i=NewsWire.OUTLETS.indexOf(outlet);
        List<String> defaults=switch(i) {
            default -> List.of("The Ledger's interest is in what reaches the account after the excitement has passed. Sales, costs and the price paid for a holding make less colorful company than a grand announcement. They are, however, remarkably persistent guests.",
                    "An emerald can carry only one side of a transaction at a time. While one desk calls the day's price an opportunity, another is content to accept it and move on. The ledger has room for both signatures, but not for both to own the same coin.",
                    "The ledger closes without an opinion on anyone's confidence. It has entered the price, the quantity and the emeralds that changed hands. In a business full of extravagant claims, the bookkeeping remains an exceptionally difficult audience.");
            case 1 -> List.of("At the Wire, the useful question begins after the demonstration: who will keep the machine running when its inventor goes home? A lever can start a remarkable afternoon. A working workshop has to survive rather more of them.",
                    "The technology trade has never lacked a promising diagram. Its more difficult business is turning the diagram into something worth carrying home. Somewhere between those stages, a very confident sales pitch usually meets its first maintenance bill.",
                    "A prototype can be persuaded to look wonderful for an afternoon. A useful machine has the harder assignment of working on an ordinary morning, when the inventor is elsewhere and nobody has brought a congratulatory banner.");
            case 2 -> List.of("Freight has an admirable indifference to speeches. It still needs a route, a load and someone prepared to take it to the other end. The Post continues to follow the part of commerce that has to leave the counter.",
                    "A cargo's journey does not end when the order is signed. There are crossings, handling costs and the persistent difficulty of being in the wrong place with something urgently wanted elsewhere. That is where the trade desk keeps its attention.",
                    "At either end of a freight route, time has a price. Someone is waiting for the goods, and someone else would like the cart back. Between them lies the part of the invoice that refuses to be improved by a more elegant signature.");
            case 3 -> List.of("Behind a busy trade are the quieter tasks that make a settlement livable. A delivery matters most when it reaches someone with a use for it. The village end of a supply route is where fine commercial promises become either useful things or another wait.",
                    "The Observer keeps one eye on the ordinary work beneath the day's bigger account. Growing, carrying and making rarely receive the grandest descriptions, but a community can live on their results. It cannot live for very long on the description.",
                    "A village meets a changing market at its doors, fields and workbenches. The distant quotation becomes a nearby cost, a delivery or a chance to sell. That is where an impressive number finally has to explain what it is good for.");
            case 4 -> List.of("The day's explanations are arriving with the usual confidence. They are particularly clear about what has already happened, a subject on which expertise remains impressively abundant. Tomorrow will provide the small inconvenience of something that has not.",
                    "There is nothing quite like a closing bell to make an uncertain morning look inevitable. The Gravel advises its ink supply to prepare for another round of excellent explanations, all delivered from the exceptionally comfortable vantage point of afterward.",
                    "The Gravel has reserved a comfortable chair for certainty. Thus far, certainty has preferred to arrive after the prices, wearing an expression that suggests it was here all along. Its account of the morning grows more impressive with every retelling.");
        };
        var choices=s.editor.templates.getOrDefault("VOICE_"+i,defaults);
        return choices.get((int)(InvestmentGrowth.unit(s.seed,s.economicDay,"voice"+i)*choices.size()));
    }
    public static Map<String,List<String>> validateTemplates(Map<String,List<String>> input) {
        if(input.size()>64)throw new IllegalArgumentException("At most 64 template groups");
        var out=new TreeMap<String,List<String>>();
        for(var entry:input.entrySet()) {
            String key=entry.getKey();
            boolean known=key.matches("VOICE_[0-4]")||key.equals("ROUNDUP_UP")||key.equals("ROUNDUP_DOWN");
            for(var e:EconomyEngine.MarketEvent.values())known|=e!=EconomyEngine.MarketEvent.NONE&&key.equals(e.name());
            for(var k:NewsWire.Kind.values())known|=key.equals("PLAYER_"+k.name());
            if(!known||entry.getValue()==null||entry.getValue().isEmpty()||entry.getValue().size()>32)
                throw new IllegalArgumentException("Invalid template group: "+key);
            for(String value:entry.getValue())if(value==null||value.isBlank()||value.length()>300
                    ||value.chars().anyMatch(c->Character.isISOControl(c)||c==0x00a7))
                throw new IllegalArgumentException("Templates must be plain single-line text, at most 300 characters");
            out.put(key,List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }
    private static void track(EconomyState s,Story story) {
        s.editor.stories.put(story.source(),story);
        while(s.editor.stories.size()>64) {
            // Protect market follow-ups from a flood of village observations.
            Long oldest=s.editor.stories.values().stream().filter(v->!v.village().isEmpty())
                    .map(Story::source).findFirst().orElse(s.editor.stories.keySet().iterator().next());
            s.editor.stories.remove(oldest);
        }
    }
    public static void trackMarket(EconomyState s,NewsWire.Article a,Map<String,Double> before) {
        String ticker=switch(a.family()) {
            case "REDSTONE_REVOLUTION" -> "RDST";
            case "NETHER_SUPPLY_CRISIS","PORTAL_REOPENING" -> "NETH";
            case "COPPER_GRID_BUILDOUT" -> "COPR";
            case "COAL_SURPLUS" -> "COAL";
            case "ENCHANTING_FESTIVAL" -> "LAPS";
            case "DEEPVEIN_DISCOVERY","LUXURY_DEMAND_SLUMP" -> "DIAM";
            default -> "VILX";
        };
        track(s,new Story(a.id(),a.day(),a.family(),a.headline(),a.outlet(),"",ticker,before.get(ticker),0));
    }
    public static String localDevelopment(EconomyState s,NewsWire.Kind kind,String village,int quantity) {
        var previous=s.editor.stories.values().stream().filter(v->v.village().equals(village)).findFirst().orElse(null);
        if(kind==NewsWire.Kind.FOOD_REMOVED||kind==NewsWire.Kind.CROPS||kind==NewsWire.Kind.DAMAGE) {
            if(previous==null) {
                var v=s.existingVillage(UUID.fromString(village));
                // Independent stable story ID: a daily bulletin can be updated without allocating a new article.
                track(s,new Story(s.editor.nextId++,s.economicDay,kind.name(),"Village activity follow-up",
                        NewsWire.OUTLETS.get(3),village,"FOOD",v.foodSupply,0));
            }
        } else if(previous!=null&&(kind==NewsWire.Kind.FOOD_RETURNED||kind==NewsWire.Kind.REPLANTED)) {
            return "This follows the village report of Day "+previous.day()
                    +". The new "+(kind==NewsWire.Kind.REPLANTED?"planting":"delivery")
                    +" gives that earlier account another chapter: practical help has now joined the story. "
                    +"The work of keeping the village supplied continues around it.";
        }
        return "";
    }
    public static void followups(EconomyState s) {
        for(Story story:List.copyOf(s.editor.stories.values())) {
            long age=s.economicDay-story.day();
            if(age<2||story.stage()==1&&age<7)continue;
            String detail,headline=story.headline();
            if(story.village().isEmpty()) {
                double change=100*(s.prices.get(story.ticker())/story.baseline()-1);
                detail=NewsNarrative.marketFollowup(s,story,change);
            } else {
                if(!s.editor.playerReports){s.editor.stories.remove(story.source());continue;}
                var v=s.existingVillage(UUID.fromString(story.village()));
                if(v==null){s.editor.stories.remove(story.source());continue;}
                double delta=v.foodSupply-story.baseline();
                headline=delta>1?"Village food estimate improves after earlier activity report":"Village follow-up: checking the food outlook";
                detail=NewsNarrative.localFollowup(story,v.foodSupply,delta,s.economicDay);
            }
            append(s,new NewsWire.Article(s.economicDay,NewsWire.Kind.FOLLOW_UP,story.family(),story.outlet(),
                    story.village(),"",0,"Follow-up: "+headline,detail));
            if(age>=7)s.editor.stories.remove(story.source());
            else s.editor.stories.put(story.source(),new Story(story.source(),story.day(),story.family(),story.headline(),
                    story.outlet(),story.village(),story.ticker(),story.baseline(),1));
        }
    }
    public static void write(EconomyState s,Properties p) {
        p.setProperty("news.next_id",""+s.editor.nextId);p.setProperty("news.stories",""+s.editor.stories.size());
        int i=0;for(var v:s.editor.stories.values()) {
            String k="news.story."+(i++)+".";
            p.setProperty(k+"id",""+v.source());p.setProperty(k+"day",""+v.day());p.setProperty(k+"family",v.family());
            p.setProperty(k+"headline",v.headline());p.setProperty(k+"outlet",v.outlet());p.setProperty(k+"village",v.village());
            p.setProperty(k+"ticker",v.ticker());p.setProperty(k+"baseline",""+v.baseline());p.setProperty(k+"stage",""+v.stage());
        }
    }
    private static String need(Properties p,String k){return Objects.requireNonNull(p.getProperty(k),k);}
    public static void read(EconomyState s,Properties p) throws IOException {
        if(Integer.parseInt(p.getProperty("format"))<32) {
            s.editor.nextId=s.news.size()+1;
            // Older articles lack pre-event baselines. Never fabricate them during migration.
            return;
        }
        s.editor.nextId=Long.parseLong(need(p,"news.next_id"));
        int count=Integer.parseInt(need(p,"news.stories"));if(count<0||count>64)throw new IOException("Story limit");
        for(int i=0;i<count;i++) {
            String k="news.story."+i+".";
            var v=new Story(Long.parseLong(need(p,k+"id")),Long.parseLong(need(p,k+"day")),need(p,k+"family"),
                    need(p,k+"headline"),need(p,k+"outlet"),need(p,k+"village"),need(p,k+"ticker"),
                    Double.parseDouble(need(p,k+"baseline")),Integer.parseInt(need(p,k+"stage")));
            if(s.editor.stories.put(v.source(),v)!=null)throw new IOException("Duplicate story");
        }
        validate(s);
    }
    public static void validate(EconomyState s) throws IOException {
        if(s.editor.nextId<1||s.editor.stories.size()>64)throw new IOException("Invalid news editor");
        Set<Long> ids=new HashSet<>();
        for(var a:s.news)if(a.id()<1||a.id()>=s.editor.nextId||!ids.add(a.id()))throw new IOException("Invalid article ID");
        for(var v:s.editor.stories.values())if(v.source()<1||v.source()>=s.editor.nextId||v.day()<0||v.day()>s.economicDay
                ||!Double.isFinite(v.baseline())||v.baseline()<0||v.stage()<0||v.stage()>1
                ||!NewsWire.OUTLETS.contains(v.outlet())||v.headline().length()>2000||v.family().length()>100
                ||v.village().isEmpty()&&(!s.prices.containsKey(v.ticker())||v.baseline()<=0)
                ||!v.village().isEmpty()&&!v.village().matches("[0-9a-f-]{36}"))throw new IOException("Invalid developing story");
    }
}
