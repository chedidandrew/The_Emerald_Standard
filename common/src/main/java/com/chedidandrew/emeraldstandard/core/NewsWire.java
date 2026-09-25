package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.*;

/** Bounded, server-authored reporting. Articles NEVER feed back into prices or balances. */
public final class NewsWire {
    public static final int LIMIT = 256;
    public static final List<String> OUTLETS = List.of("The Emerald Ledger", "The Redstone Wire",
            "The Nether Post", "The Overworld Observer", "The Daily Gravel");
    public enum Kind { MARKET, FOLLOW_UP, ROUNDUP, FOOD_REMOVED, CROPS, DAMAGE, DONATION, REPLANTED, VIOLENCE, FOOD_RETURNED, CIVIC, FEATURE }
    public record Article(long id, long day, Kind kind, String family, String outlet, String village,
            String actor, int quantity, String headline, String detail, String subject, long sourceId) {
        public Article(long id,long day,Kind kind,String family,String outlet,String village,String actor,int quantity,String headline,String detail) {
            this(id,day,kind,family,outlet,village,actor,quantity,headline,detail,"",0);
        }
        public Article(long day, Kind kind, String family, String outlet, String village,
                String actor, int quantity, String headline, String detail) {
            this(0,day,kind,family,outlet,village,actor,quantity,headline,detail,"",0);
        }
        public Article withId(long value) { return new Article(value,day,kind,family,outlet,village,actor,quantity,headline,detail,subject,sourceId); }
        public Article {
            Objects.requireNonNull(kind);
            if (subject==null||subject.length()>160||!subject.matches("[A-Za-z0-9:_-]*")||sourceId<0) throw new IllegalArgumentException("Invalid story link");
            if (id < 0 || day < 0 || quantity < 0 || !OUTLETS.contains(outlet)) throw new IllegalArgumentException("Invalid news");
            for (String s : List.of(family, village, actor, headline))
                if (s.length() > 2000 || s.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid news text");
            if(detail.length()>6000 || detail.indexOf('\0')>=0) throw new IllegalArgumentException("Invalid article body");
        }
        public String text() {
            return NewsNarrative.text(this,false,false);
        }
    }
    private NewsWire() {}
    public static void append(EconomyState state, Article article) {
        NewsEditorial.append(state, article);
    }
    public static void day(EconomyState state, EconomyEngine.MarketEvent event, Map<String, Double> before) {
        long day = state.economicDay;
        NewsCivic.day(state);
        NewsFeatures.day(state);
        NewsEditorial.followups(state);
        if (event != EconomyEngine.MarketEvent.NONE) {
            String family = event.name();
            String headline = choose(state, family, eventHeadlines(event));
            String publisher = NewsEditorial.outlet(state, family);
            append(state, new Article(day, Kind.MARKET, family, publisher, "", "", 0,
                    headline, NewsNarrative.market(state,family,publisher,headline,moves(state,before))));
            NewsEditorial.trackMarket(state, state.news.getLast(), before);
        }
        if (event == EconomyEngine.MarketEvent.NONE && (day % 2 == 0 || state.news.isEmpty())) {
            String direction = state.prices.get("VILX") >= before.get("VILX") ? "UP" : "DOWN";
            String family = "ROUNDUP_" + direction;
            String publisher=NewsEditorial.outlet(state,family);
            append(state, new Article(day, Kind.ROUNDUP, family, publisher, "", "", 0,
                    choose(state, family, direction.equals("UP") ? UP : DOWN),
                    NewsNarrative.roundup(state,family,publisher,moves(state,before))));
        }
    }
    private static String moves(EconomyState state, Map<String, Double> before) {
        var ordered = EconomyEngine.ASSETS.stream().sorted(Comparator.comparingDouble(
                (EconomyEngine.Asset a) -> change(state.prices.get(a.ticker()), before.get(a.ticker())))).toList();
        var low = ordered.getFirst(); var high = ordered.getLast();
        return quote("VILX", state, before) + "\nStrongest: " + quote(high.ticker(), state, before)
                + "\nWeakest: " + quote(low.ticker(), state, before);
    }
    private static double change(double now, double before) { return 100 * (now / before - 1); }
    private static String quote(String ticker, EconomyState state, Map<String,Double> before) {
        return String.format(Locale.ROOT,"%s: %+.2f%% today",ticker,change(state.prices.get(ticker),before.get(ticker)));
    }
    public static boolean isPlayer(Kind kind) {
        return switch(kind) {
            case FOOD_REMOVED,CROPS,DAMAGE,DONATION,REPLANTED,VIOLENCE,FOOD_RETURNED -> true;
            default -> false;
        };
    }
    public static boolean player(EconomyState state,Kind kind,UUID villageId,UUID playerId,String actor,int quantity) {
        return player(state,kind,villageId,playerId,actor,quantity,"");
    }
    public static boolean player(EconomyState state,Kind kind,UUID villageId,UUID playerId,String actor,int quantity,String subject) {
        var village=state.existingVillage(villageId);
        if(!state.editor.playerReports||village==null||playerId==null||quantity<=0||!isPlayer(kind))return false;
        if(subject==null||subject.length()>160||!subject.matches("[A-Za-z0-9:_-]*"))return false;
        String name=cleanName(actor),place=villageId.toString(),family="PLAYER_"+kind+"_"+playerId;
        Article previous=null;
        if(!subject.isEmpty()) {
            Kind earlier=kind==Kind.REPLANTED?Kind.CROPS:kind==Kind.FOOD_RETURNED?Kind.FOOD_REMOVED:null;
            for(int i=state.news.size()-1;i>=0;i--) {
                var a=state.news.get(i);
                if(a.kind==earlier&&a.village.equals(place)&&a.subject.equals(subject)) {previous=a;break;}
            }
        }
        String development=previous==null?"":"Returning to “"+previous.headline+"” (Day "+previous.day+"). "
                +(kind==Kind.REPLANTED?"New planting has now been observed at the same crop position. Other fields have their own stories."
                :"Food has now been added to the same store. This delivery does not establish where it came from or settle the earlier withdrawal.");
        for(int i=state.news.size()-1;i>=0;i--) {
            Article old=state.news.get(i);
            if(state.economicDay-old.day>0)continue;
            if(old.kind==kind&&old.village.equals(place)&&old.family.equals(family)&&old.subject.equals(subject)) {
                int total=(int)Math.min(Integer.MAX_VALUE,(long)old.quantity+quantity);
                state.news.remove(i);
                append(state,playerArticle(state,kind,family,place,name,total,old.headline,subject,
                        previous==null?old.sourceId:previous.id,development).withId(old.id));
                return true;
            }
        }
        append(state,playerArticle(state,kind,family,place,name,quantity,
                choose(state,family,playerHeadlines(kind)),subject,previous==null?0:previous.id,development));
        return true;
    }
    private static Article playerArticle(EconomyState state,Kind kind,String family,String place,
            String actor,int quantity,String headline,String subject,long sourceId,String development) {
        var v=state.existingVillage(UUID.fromString(place));
        return new Article(0,state.economicDay,kind,family,OUTLETS.get(3),place,actor,quantity,headline,
                NewsNarrative.local(kind,quantity,state.seed,state.economicDay,family,
                    location(v),development),subject,sourceId);
    }
    static String location(EconomyState.VillageRecord v) {
        return "District near X "+unpackX(v.centerPos)+", Z "+unpackZ(v.centerPos)+".";
    }
    private static int unpackX(long p) { return (int)(p >> 38); }
    private static int unpackZ(long p) { return (int)(p << 26 >> 38); }
    public static String cleanName(String value) {
        String cleaned=value==null?"":value.replaceAll("[^A-Za-z0-9_ .-]","");
        return cleaned.substring(0,Math.min(32,cleaned.length()));
    }
    static String choose(EconomyState state,String family,List<String> defaults) {
        String key=family.startsWith("PLAYER_")?family.substring(0,family.lastIndexOf('_')):family;
        var choices=state.editor.templates.getOrDefault(key,defaults);
        int start=(int)(InvestmentGrowth.unit(state.seed,state.economicDay,family)*choices.size());
        Map<String,Integer> latest=new HashMap<>();
        for(int i=0;i<state.news.size();i++)latest.put(state.news.get(i).headline(),i);
        String best=choices.get(start);int oldest=Integer.MAX_VALUE;
        for(int j=0;j<choices.size();j++) {
            String candidate=choices.get((start+j)%choices.size());
            int seen=latest.getOrDefault(candidate,-1);
            if(seen<oldest){best=candidate;oldest=seen;}
        }
        return best;
    }
    public static void write(EconomyState state, Properties p) {
        NewsEditorial.write(state,p);
        p.setProperty("news.count",Integer.toString(state.news.size()));
        for(int i=0;i<state.news.size();i++) {
            Article a=state.news.get(i); String k="news."+i+".";
            p.setProperty(k+"id",""+a.id); p.setProperty(k+"day",""+a.day); p.setProperty(k+"kind",a.kind.name());
            p.setProperty(k+"family",a.family); p.setProperty(k+"outlet",a.outlet);
            p.setProperty(k+"village",a.village); p.setProperty(k+"actor",a.actor);
            p.setProperty(k+"subject",a.subject);p.setProperty(k+"source",""+a.sourceId);
            p.setProperty(k+"quantity",""+a.quantity); p.setProperty(k+"headline",a.headline); p.setProperty(k+"detail",a.detail);
        }
    }
    public static void read(EconomyState state, Properties p) throws IOException {
        try {
            int count=Integer.parseInt(required(p,"news.count"));
            if(count<0||count>LIMIT) throw new IllegalArgumentException("News limit");
            for(int i=0;i<count;i++) {
                String k="news."+i+".";
                Article a=new Article(Integer.parseInt(p.getProperty("format"))>=32 ? Long.parseLong(required(p,k+"id")) : i+1,
                    Long.parseLong(required(p,k+"day")),Kind.valueOf(required(p,k+"kind")),
                    required(p,k+"family"),required(p,k+"outlet"),required(p,k+"village"),required(p,k+"actor"),
                    Integer.parseInt(required(p,k+"quantity")),required(p,k+"headline"),required(p,k+"detail"),p.getProperty(k+"subject",""),Integer.parseInt(p.getProperty("format"))>=32?Long.parseLong(p.getProperty(k+"source","0")):0);
                if(a.day>state.economicDay) throw new IllegalArgumentException("Future news");
                state.news.add(a);
            }
            NewsEditorial.read(state,p);
        } catch(RuntimeException e) { throw new IOException("Invalid newspaper archive",e); }
    }
    private static String required(Properties p,String k) { return Objects.requireNonNull(p.getProperty(k),k); }
    private static List<String> eventHeadlines(EconomyEngine.MarketEvent e) {
        return switch(e) {
            case REDSTONE_REVOLUTION -> List.of("Startup promises automated farming; quietly employs 46 villagers",
                "Redstone demand rises; engineer insists the smoke is a feature","Investors fund machine that opens door already open",
                "Automation boom reaches switch-flipping industry","Patent office buried under identical piston designs",
                "New redstone processor delivers twice the heat in half the space","Founder declares every company a redstone company",
                "Observers question whether blinking lamps count as productivity");
            case NETHER_SUPPLY_CRISIS -> List.of("Portal Authority introduces convenience fee for arriving alive",
                "Nether shipments delayed; lava refuses expedited handling","Supply chain described as resilient, then catches fire",
                "Trade delegation requests meeting somewhere less flammable","Netherite shortage prompts strategic stockpile of excuses",
                "Freight insurers discover clause excluding everything in the Nether","Customs queue now longer than the bridge",
                "Officials classify missing cargo as a thermal redistribution");
            case GOLDEN_HARVEST -> List.of("Bumper harvest forces farmers to confront unprecedented sandwich capacity",
                "Agriculture celebrates growth that is actually edible","Wheat output exceeds analysts' kneads",
                "Cooperative announces bread guidance above expectations","Excellent harvest undermines shortage consultant",
                "Village holds emergency meeting about surplus potatoes","Farmers credited with disruptive technology called rain",
                "Harvest success leaves pessimists clutching empty forecasts");
            case END_EXPEDITION_BOOM -> List.of("End expedition secures funding and a strongly worded return policy",
                "Freight demand soars; sky remains entirely unsupported","Investors back startup with no ground beneath it",
                "Exploration boom sends insurance clerks under their desks","Shulker logistics expands outside the box",
                "Expedition prospectus describes void as an emerging market","Potion orders rise as travelers read itinerary",
                "Frontier firm reports enormous addressable emptiness");
            case CREEPER_CATASTROPHE -> List.of("Trade-route blast disrupts deliveries; relief crews mobilize",
                "Reconstruction contracts rise after regional transport disaster","Security demand rises as merchants count the cost",
                "Families await reopened routes after regional depot blast","Freight company retires slogan Nothing Can Stop Us",
                "Safety inspector's warning recovered from filing cabinet","Officials promise review of review into neglected defenses",
                "Rebuilding effort draws volunteers and opportunistic consultants");
            case VILLAGER_CREDIT_SCARE -> List.of("Bank calls liquidity crisis temporary; vault contains three buckets",
                "Lenders tighten terms; borrowers discover fine print has more fine print","Credit committee downgrades confidence to Hmm",
                "Emergency meeting concludes another emergency meeting is necessary","Risk model surprised by the existence of risk",
                "Financial engineering department requests actual engineer","Markets question collateral consisting of optimistic speeches",
                "Reserve business attracts nervous investors and their nervous accountants");
            case DEEPVEIN_DISCOVERY -> List.of("New ore deposit found; scarcity brochure recalled",
                "Mining survey discovers supply beneath supply forecast","Diamond reserve estimates upgraded; exclusivity consultants protest",
                "Explorers strike rich vein and inconvenient price consequences","Ore discovery turns geological certainty into revised guidance",
                "Mine expansion promises jobs, materials, and an extremely large hole","New mineral field reshapes merchants' inventories",
                "Prospectors announce breakthrough; gemstone sellers prefer silence");
            case PORTAL_REOPENING -> List.of("Portal reopens; toll booth somehow already staffed",
                "Nether trade resumes with a fresh stack of freight orders","Supply returns; shortage consultant extends holiday",
                "Freight queues shorten; customs celebrates solving customs");
            case RAIL_DISRUPTION -> List.of("Rail stoppage delays freight; replacement minecart also delayed",
                "Transit firm promises on-time apology","Supply chain encounters literal missing link",
                "Minecart timetable reclassified as historical fiction");
            case COPPER_GRID_BUILDOUT -> List.of("Copper demand rises as villages discover wiring needs wires",
                "Automation project requires more copper and fewer presentations","Grid expansion powers hopes and several unnecessary lamps",
                "Infrastructure plan includes infrastructure, surprising analysts");
            case COAL_SURPLUS -> List.of("Coal glut leaves market with fuel for thought",
                "Fuel supply outruns demand; surplus committee seeks larger shed","Coal prices cool despite product remaining flammable",
                "Mine output exceeds forecasts and available storage");
            case ENCHANTING_FESTIVAL -> List.of("Enchanting fair opens; lapis sellers call it a magical opportunity",
                "Festival visitors pay extra for Unbreaking confidence","Regional fair boosts alchemy trade and questionable robes",
                "Enchanting queues grow; organizers promise Efficiency next year");
            case LUXURY_DEMAND_SLUMP -> List.of("Diamond demand weakens; exclusivity fails to pay rent",
                "Gemstone shoppers discover cheaper ways to sparkle","Luxury sales slump; campaign blames insufficient aspiration",
                "Diamond marketing office learns rare does not mean recession-proof");
            case FISHERY_RECOVERY -> List.of("Fishery returns after closure; supply finally catches up",
                "Coastal fleet resumes deliveries and terrible nautical jokes","Fish cooperative reports a net improvement",
                "Seasonal recovery brings work back to coastal crews");
            case POTION_RECALL -> List.of("Potion batch recalled after invisibility wears off during audit",
                "Alchemy firm withdraws product; fine print was not fire-resistant","Potion maker promises stronger quality checks, weaker excuses",
                "Recall disrupts sales; laboratory declines to drink its own guidance");
            case BANK_STRESS_TEST -> List.of("Lenders pass stress test; accountants remain visibly stressed",
                "Credit confidence improves following actual examination of vaults","Regional bank review finds collateral, to general relief",
                "Stress test complete; committee requests a restorative nap");
            case REGIONAL_REBUILDING -> List.of("Regional rebuilding program brings crews and materials together",
                "Homes rise again after hardship; community effort gains ground","Recovery contracts lift construction demand",
                "Reconstruction plan finally graduates from diagrams to bricks");
            default -> UP;
        };
    }
    private static final List<String> UP=List.of("Markets rise; every investor suddenly a long-term visionary",
        "Green arrows improve economic literacy across trading floor","Optimism returns wearing yesterday's disguise",
        "Analyst takes credit for movement predicted in both directions","Rally lifts spirits and several questionable business plans",
        "Trading desks celebrate; risk department schedules quiet lunch","Investors discover line can also go up",
        "Bullish session prompts emergency revision of pessimistic headlines","Shareholders demand champagne; receive suspicious stew",
        "Market advance described as obvious by people surprised yesterday","Confidence climbs; caution requests transfer",
        "Closing bell interrupted by enthusiastic calculator noises");
    private static final List<String> DOWN=List.of("Trader buys the dip; discovers ravine has another bottom",
        "Markets fall; long-term investors request shorter long term","Red arrows blamed on redstone interference",
        "Diversification seminar interrupted by everything falling together","Analysts revise forecast to include things that happened",
        "Portfolio described as temporarily archaeological","Investors seek safe haven beneath trading desk",
        "Shareholders reminded that diamond hands are not diamond armor","Closing bell rings; nobody volunteers to answer",
        "Risk department returns from lunch with an expression","Market correction declines to specify what it is correcting",
        "Losses described as unrealized; concern remains fully realized");
    private static List<String> playerHeadlines(Kind k) {
        return switch(k) {
            case FOOD_REMOVED -> List.of("Village pantry records outgoing food",
                "Pantry ledger discovers subtraction","Village pantry reports unscheduled withdrawals",
                "Food changes hands; village shelves request an inventory","Food stores: another entry in the outgoing column",
                "Village food stores report net withdrawals");
            case CROPS -> List.of("The harvest has left a question for the soil",
                "Crop losses leave farmers surveying a very open plan","Village fields await the next green beginning",
                "Agricultural redevelopment appears to have skipped agriculture");
            case DAMAGE -> List.of("Village architecture has a subtraction to account for",
                "Building report: not everything is where it was","Demolition work begins without matching construction work",
                "Property report: there used to be more building here");
            case DONATION -> List.of("Village fund receives help; residents welcome practical optimism",
                "Benefactor invests in community instead of another speculative hole","Relief fund grows; accountants briefly smile",
                "Local generosity outperforms committee's expectations");
            case REPLANTED -> List.of("Seeds return to fields; farmers cautiously put down complaint forms",
                "Replanting effort brings green shoots of actual recovery","Village fields receive a second chance",
                "Replanting gives the next harvest somewhere to start");
            case VIOLENCE -> List.of("Village mourns after a fatal encounter",
                "A village faces the absence of its own","Community counts the cost of violence",
                "Local tragedy leaves an empty place at the gathering square");
            case FOOD_RETURNED -> List.of("Village shelves welcome a food delivery",
                "Village pantry receives a contribution",
                "Food delivery brings practical relief to village stores","Village food stores record incoming supplies");
            default -> throw new IllegalArgumentException("Not player news");
        };
    }
}
