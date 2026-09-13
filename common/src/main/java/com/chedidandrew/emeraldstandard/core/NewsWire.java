package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.*;

/** Bounded, server-authored reporting. Articles NEVER feed back into prices or balances. */
public final class NewsWire {
    public static final int LIMIT = 256;
    public static final List<String> OUTLETS = List.of("The Emerald Ledger", "The Redstone Wire",
            "The Nether Post", "The Overworld Observer", "The Daily Gravel");
    public enum Kind { MARKET, FOLLOW_UP, ROUNDUP, FOOD_REMOVED, CROPS, DAMAGE, DONATION, REPLANTED, VIOLENCE, FOOD_RETURNED }
    public record Article(long id, long day, Kind kind, String family, String outlet, String village,
            String actor, int quantity, String headline, String detail) {
        public Article(long day, Kind kind, String family, String outlet, String village,
                String actor, int quantity, String headline, String detail) {
            this(0,day,kind,family,outlet,village,actor,quantity,headline,detail);
        }
        public Article withId(long value) { return new Article(value,day,kind,family,outlet,village,actor,quantity,headline,detail); }
        public Article {
            Objects.requireNonNull(kind);
            if (id < 0 || day < 0 || quantity < 0 || !OUTLETS.contains(outlet)) throw new IllegalArgumentException("Invalid news");
            for (String s : List.of(family, village, actor, headline, detail))
                if (s.length() > 2000 || s.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid news text");
        }
        public String text() {
            return outlet + " | Day " + day + "\n" + (village.isEmpty() ? "World-market dispatch" : "Local report")
                    + "\n\n" + headline + "\n\n" + detail;
        }
    }
    private NewsWire() {}
    public static void append(EconomyState state, Article article) {
        NewsEditorial.append(state, article);
    }
    public static void day(EconomyState state, EconomyEngine.MarketEvent event, Map<String, Double> before) {
        long day = state.economicDay;
        if (event != EconomyEngine.MarketEvent.NONE) {
            String family = event.name();
            String headline = choose(state, family, eventHeadlines(event));
            String explanation = event.detail() + " This is a simulated off-screen trade event, not a report of damage to your village.";
            String publisher = NewsEditorial.outlet(state, family);
            append(state, new Article(day, Kind.MARKET, family, publisher, "", "", 0,
                    headline, explanation + "\n\n" + moves(state, before)
                    + "\n\n" + NewsEditorial.voice(state, publisher)
                    + "\nPrice moves include ordinary trading and other conditions; this event is not the only influence."));
            NewsEditorial.trackMarket(state, state.news.getLast(), before);
        }
        NewsEditorial.followups(state);
        if (event == EconomyEngine.MarketEvent.NONE && (day % 2 == 0 || state.news.isEmpty())) {
            String direction = state.prices.get("VILX") >= before.get("VILX") ? "UP" : "DOWN";
            String family = "ROUNDUP_" + direction;
            append(state, new Article(day, Kind.ROUNDUP, family, outlet(state, family), "", "", 0,
                    choose(state, family, direction.equals("UP") ? UP : DOWN),
                    "Market close: " + state.regime.name().toLowerCase(Locale.ROOT) + ".\n\n" + moves(state, before)
                    + "\n\n" + NewsEditorial.voice(state, NewsEditorial.outlet(state, family))
                    + "\nEditorial commentary explains uncertainty; it is not an extra price-changing event."));
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
    public static boolean player(EconomyState state, Kind kind, UUID villageId, UUID playerId, String actor, int quantity) {
        var village = state.existingVillage(villageId);
        if (!state.editor.playerReports || village == null || playerId == null || quantity <= 0 || kind.ordinal() < Kind.FOOD_REMOVED.ordinal()) return false;
        String name = cleanName(actor);
        String place = villageId.toString();
        String family = "PLAYER_" + kind + "_" + playerId;
        // A bounded daily developing report per actor/place/category; stable ID survives updates.
        for (int i=state.news.size()-1;i>=0;i--) {
            Article old=state.news.get(i);
            if (state.economicDay-old.day > 0) break;
            if (old.kind==kind && old.village.equals(place) && old.family.equals(family)) {
                int total=(int)Math.min(Integer.MAX_VALUE,(long)old.quantity+quantity);
                state.news.remove(i);
                append(state, playerArticle(state,kind,family,place,name,total,old.headline).withId(old.id));
                return true;
            }
        }
        append(state,playerArticle(state,kind,family,place,name,quantity,choose(state,family,playerHeadlines(kind))));
        return true;
    }
    private static Article playerArticle(EconomyState state, Kind kind, String family, String place,
            String actor, int quantity, String headline) {
        var v=state.existingVillage(UUID.fromString(place));
        String action=switch(kind) {
            case FOOD_REMOVED -> "food items taken from a confirmed village container";
            case FOOD_RETURNED -> "food items put into a confirmed village container";
            case CROPS -> "village crop blocks removed and not replanted during the observation window";
            case DAMAGE -> "confirmed village structure blocks removed";
            case DONATION -> "emeralds contributed to the village fund";
            case REPLANTED -> "village crop positions replanted";
            case VIOLENCE -> "villager deaths attributed to this player";
            default -> throw new IllegalArgumentException();
        };
        String development=NewsEditorial.localDevelopment(state,kind,place,quantity);
        return new Article(state.economicDay,kind,family,"The Overworld Observer",place,actor,quantity,headline,
                "Recorded player: " + (actor.isBlank() ? "identity unavailable" : actor) + ".\n"
                + "District near X " + unpackX(v.centerPos) + ", Z " + unpackZ(v.centerPos) + ".\n"
                + "Confirmed total: " + quantity + " " + action + ".\n\n"
                + development + "This bulletin reports observed actions, not intent or permission between players. "
                + "Headlines are satire. No automatic fine, guard hostility, or global market shock is imposed.");
    }
    private static int unpackX(long p) { return (int)(p >> 38); }
    private static int unpackZ(long p) { return (int)(p << 26 >> 38); }
    public static String cleanName(String value) {
        String cleaned=value==null?"":value.replaceAll("[^A-Za-z0-9_ .-]","");
        return cleaned.substring(0,Math.min(32,cleaned.length()));
    }
    private static String outlet(EconomyState state,String family) {
        return OUTLETS.get((int)(InvestmentGrowth.unit(state.seed,state.economicDay,family+"outlet")*OUTLETS.size()));
    }
    private static String choose(EconomyState state,String family,List<String> choices) {
        choices=state.editor.templates.getOrDefault(family.startsWith("PLAYER_") ? family.substring(0,family.lastIndexOf('_')) : family, choices);
        int start=(int)(InvestmentGrowth.unit(state.seed,state.economicDay,family)*choices.size());
        for(int j=0;j<choices.size();j++) {
            String candidate=choices.get((start+j)%choices.size());
            if(state.news.stream().noneMatch(a -> state.economicDay-a.day < 90 && a.headline.equals(candidate))) return candidate;
        }
        // Exhausted finite pool: use least recently published wording, not an unbounded archive.
        for(Article old:state.news) if(choices.contains(old.headline)) return old.headline;
        return choices.get(start);
    }
    public static void write(EconomyState state, Properties p) {
        NewsEditorial.write(state,p);
        p.setProperty("news.count",Integer.toString(state.news.size()));
        for(int i=0;i<state.news.size();i++) {
            Article a=state.news.get(i); String k="news."+i+".";
            p.setProperty(k+"id",""+a.id); p.setProperty(k+"day",""+a.day); p.setProperty(k+"kind",a.kind.name());
            p.setProperty(k+"family",a.family); p.setProperty(k+"outlet",a.outlet);
            p.setProperty(k+"village",a.village); p.setProperty(k+"actor",a.actor);
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
                    Integer.parseInt(required(p,k+"quantity")),required(p,k+"headline"),required(p,k+"detail"));
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
                "Families await reopened routes after off-screen depot blast","Freight company retires slogan Nothing Can Stop Us",
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
                "Nether trade resumes with complimentary safety disclaimer","Supply returns; shortage consultant extends holiday",
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
    private static final List<String> COMMENTARY=List.of(
        "The Ledger reminds readers that a positive long-run growth target is not a guaranteed return.",
        "The Wire's technology desk notes that a useful invention and a profitable investment are different claims.",
        "The Nether Post warns that scarce supplies can still become cheaper when demand weakens.",
        "The Observer asks readers to distinguish local village facts from off-screen market dispatches.",
        "The Gravel's opinion desk recommends checking facts before checking how loudly someone says them.",
        "Treasury funds have interest-rate exposure; lower volatility does not mean zero risk.",
        "An index spreads company-specific exposure but cannot make a market-wide slump disappear.",
        "Commodity holdings track prices, not a promise of physical delivery or interest.",
        "Editorial: the forecast contains uncertainty, despite the unusually confident font.",
        "Today's movements do not establish tomorrow's direction. The crystal ball remains in technical support.",
        "Company shares and the commodities they use are different investments with different risks.",
        "Trading spreads still apply. Enthusiasm is not accepted as payment.");
    private static List<String> playerHeadlines(Kind k) {
        return switch(k) {
            case FOOD_REMOVED -> List.of("Carrot caper: village stores meet aggressive inventory management",
                "Bread reserves vanish; storage department requests a word","Village pantry reports unscheduled withdrawals",
                "Food-store raider leaves villagers with plenty of shelf space","Potato reserves selected for private redistribution",
                "Pantry emptied faster than committee can form");
            case CROPS -> List.of("Local farm reclassified as dirt after unscheduled renovation",
                "Crop losses leave farmers surveying a very open plan","Fields cleared; replanting remains conspicuously absent",
                "Agricultural redevelopment appears to have skipped agriculture");
            case DAMAGE -> List.of("Self-appointed urban planner removes wall; residents question qualifications",
                "Village architecture acquires unrequested ventilation","Demolition work begins without matching construction work",
                "Property report: there used to be more building here");
            case DONATION -> List.of("Village fund receives help; residents welcome practical optimism",
                "Benefactor invests in community instead of another speculative hole","Relief fund grows; accountants briefly smile",
                "Local generosity outperforms committee's expectations");
            case REPLANTED -> List.of("Seeds return to fields; farmers cautiously put down complaint forms",
                "Replanting effort brings green shoots of actual recovery","Village fields receive a second chance",
                "Harvest story ends with seeds instead of bare dirt");
            case VIOLENCE -> List.of("Village mourns after confirmed player attack",
                "Residents call for calm after player-caused loss","Community counts the cost of violence",
                "Local tragedy leaves an empty place at the gathering square");
            case FOOD_RETURNED -> List.of("Bread returns to shelves; sandwich emergency downgraded",
                "Village pantry restocked; shelves thank local contributor",
                "Food delivery brings practical relief to village stores","Potatoes return from their unscheduled travels");
            default -> throw new IllegalArgumentException("Not player news");
        };
    }
}
