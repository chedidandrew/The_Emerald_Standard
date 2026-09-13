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
            String text=a.text();
            if(!a.village().isEmpty()) {
                if(anonymous) text=text.replaceAll("Recorded player: [^\\n]*","Recorded player: Anonymous resident.");
                if(approximate) text=text.replaceAll("District near X -?\\d+, Z -?\\d+\\.", "Location: a local village (coordinates withheld).");
            }
            return text;
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
            case 1 -> List.of("Technology desk: adoption and useful output matter more than blinking lamps.",
                    "Technology desk: the prototype works. The business model still requires a lever.");
            case 2 -> List.of("Trade desk: watch delivery costs, available supply and routes, not just rarity.",
                    "Trade desk: cargo is moving through a world where the floor is occasionally lava.");
            case 3 -> List.of("Community desk: price changes do not tell us whether any particular village has enough food.",
                    "Community desk: behind each supply chain are people who would appreciate a functioning road.");
            case 4 -> List.of("OPINION / SATIRE: experts upgrade yesterday's guess to today's obvious conclusion.",
                    "OPINION / SATIRE: the crystal ball has been replaced by a louder crystal ball.");
            default -> List.of("Analysis: compare the measured move with the wider market; one headline is not a forecast.",
                    "Analysis: a positive growth target is not a promised return. Spreads and risk still apply.");
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
            return "Developing story: after the village activity report on Day "+previous.day()
                    +", this bulletin records "+quantity+(kind==NewsWire.Kind.REPLANTED?" replanted crop positions. ":" food items added. ")
                    +"This is observed help, not proof that every loss was repaired.\n\n";
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
                String trend=change < -1 ? "remains below its pre-event price" : change > 1 ? "stands above its pre-event price" : "is near its pre-event price";
                detail=String.format(Locale.ROOT,"Developing story from Day %d. %s %s (%+.2f%% since before the event).",
                        story.day(),story.ticker(),trend,change)
                        +"\nThis measures the price path, not proof that a supply disruption has ended or the event alone caused the move."
                        +"\n\n"+voice(s,story.outlet());
            } else {
                if(!s.editor.playerReports){s.editor.stories.remove(story.source());continue;}
                var v=s.existingVillage(UUID.fromString(story.village()));
                if(v==null){s.editor.stories.remove(story.source());continue;}
                double delta=v.foodSupply-story.baseline();
                headline=delta>1?"Village food estimate improves after earlier activity report":"Village follow-up: checking the food outlook";
                detail=String.format(Locale.ROOT,"Follow-up to village activity on Day %d. Simulated food supply: %.1f, change %+.1f since that report.",
                        story.day(),v.foodSupply,delta)
                        +"\nThis is the town's economic estimate, not a count of replanted crops or a claim that damage has been repaired."
                        +"\nOther residents, production and consumption also influence this estimate.";
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
