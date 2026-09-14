package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import java.util.regex.Pattern;

/** Pure, deterministic newsroom prose. Never draws the economic RNG or mutates world state. */
final class NewsNarrative {
    private static int variant(long seed,long day,String key,int n) {
        return (int)(InvestmentGrowth.unit(seed,day,"news-prose:"+key)*n);
    }
    static final String FACTS="From the notebook\n";
    static String paragraphs(String... parts) {
        return String.join("\n\n",Arrays.stream(parts).filter(p->p!=null&&!p.isBlank()).toList());
    }
    static String market(EconomyState s,String family,String publisher,String headline,String quotes) {
        var scene=NewsStoryCatalog.scene(family);
        int v=variant(s.seed,s.economicDay,family+":"+publisher,4);
        String reaction=NewsEventAngles.reaction(family,variant(s.seed,s.economicDay,family+":reaction",2));
        String voice=column(s,publisher,headline), notebook=FACTS+"World-market dispatch.\n"+quotes;
        // Mix developed dispatches, a counter-side opening and a shorter desk report.
        // All retain the same source event and exact quotation notebook.
        return switch(v) {
            case 0 -> paragraphs(scene.opening(),scene.detail(),reaction,voice,scene.outlook(),notebook);
            case 1 -> paragraphs(reaction,scene.opening(),voice,scene.angle(),notebook);
            case 2 -> paragraphs(scene.opening(),scene.angle(),reaction,voice,notebook);
            default -> paragraphs(scene.opening(),reaction,voice,notebook);
        };
    }
    private static String column(EconomyState s,String publisher,String headline) {
        String key="VOICE_"+NewsWire.OUTLETS.indexOf(publisher);
        return s.editor.templates.containsKey(key)?NewsEditorial.voice(s,publisher):NewsColumns.column(s,publisher,headline);
    }
    static String roundup(EconomyState s,String family,String publisher,String quotes) {
        boolean up=family.endsWith("UP");
        String direction=up?"higher":"lower";
        String lead=switch(NewsWire.OUTLETS.indexOf(publisher)) {
            case 1 -> "The broad index closed "+direction+". Even a trading floor needs to check the output against the sales pitch. A quotation board is wonderfully responsive equipment; unlike a useful machine, it can change everybody's mood without completing a single chore.";
            case 2 -> "The broad index ended "+direction+", adding another calculation to the freight desk's daily cargo. A merchant can revise a price in moments. Redirecting the actual load is a longer business, particularly when the convenient route is only convenient on paper.";
            case 3 -> "The broad index finished "+direction+". At the village end of that news are familiar errands: buying materials, selling a crop and trying to leave enough for supper. An impressive arrow becomes more interesting when somebody has to carry home what it costs.";
            case 4 -> "The broad index closed "+direction+", a development the Gravel is prepared to describe with tremendous certainty now that it has occurred. The important question is what happens next. We have reserved ample ink for an answer arriving shortly after the next closing bell.";
            default -> "The broad index closed "+direction+". This puts a new figure beside yesterday's confident explanations. A respectable account begins with that figure and works outward; an exceptionally confident explanation sometimes prefers to start with itself and work around the account.";
        };
        int layout=variant(s.seed,s.economicDay,family+":"+publisher+":layout",3);
        if(layout==1) lead="The closing bell leaves the broad index "+direction+" and tomorrow's business still unwritten. Sellers and buyers can agree on today's final figure while finding entirely different reasons to put it away. One side has a holding to value; the other has a price to consider.";
        else if(layout==2) lead="A "+(up?"dearer":"cheaper")+" entry, a "+(up?"stronger":"weaker")+" holding: the broad index ended "+direction+". These are two readings of the same quotation, separated by which side of the trade a reader occupies. The closing bell has settled today's number, though not the discussion around it.";
        return paragraphs(lead,column(s,publisher,""),
                up?"Existing holders have a better closing mark; new buyers face a dearer entry. The same price offers each side a different conversation."
                  :"Existing holders face a weaker mark; new buyers see a cheaper entry. Neither side gets tomorrow's result included in today's price.",
                layout==2?"":NewsColumns.closing(publisher),FACTS+"The Closing Bell\n"+quotes);
    }
    static String local(NewsWire.Kind kind,int quantity,long seed,long day,String key,String location,String development) {
        String[] p=NewsLocalStories.passages(kind);int v=variant(seed,day,key,4);
        return paragraphs(NewsLocalStories.facts(kind,quantity),p[v%2],development,p[2+(v/2)],p[4+(v%2)],
                FACTS+location+"\n"+NewsLocalStories.facts(kind,quantity));
    }
    static String marketFollowup(EconomyState s,NewsEditorial.Story story,double change) {
        String direction=change>story.lastChange()?"higher":"lower";
        String facts=String.format(Locale.ROOT,
                "%s: %+.2f%% against its pre-event close; %+.2f percentage points since the previous installment.",
                story.ticker(),change,change-story.lastChange());
        String earlier="“"+story.headline()+"” (Day "+story.day()+")";
        String movement=story.ticker()+" is now "+direction+" than at our previous comparison.";
        int desk=NewsWire.OUTLETS.indexOf(story.outlet());
        String opening=switch(desk) {
            case 1 -> "The progress chart has changed since "+earlier+". "+movement
                    +" It is a measurable result, although not one that can be demonstrated with a flashing lamp.";
            case 2 -> "A new quotation has reached the freight desk following "+earlier+". "+movement
                    +" The number travelled rather more easily than the goods in our dispatches.";
            case 3 -> "Our report "+earlier+" has another figure beside it. "+movement
                    +" For readers concerned with everyday supplies, the useful question is what the change will eventually mean at the counter.";
            case 4 -> "An exclusive development in the price column: "+movement
                    +" Readers may recall "+earlier+". We have returned to the account with a fresh figure and, for once, the old clipping.";
            default -> "The accounts have moved on from "+earlier+". "+movement
                    +" A careful investor will want both entries on the desk before deciding which confident explanation to buy.";
        };
        String meaning=switch(desk) {
            case 1 -> "Our subject remains "+subject(story.family())+". The quotation measures appetite for the trade, not the usefulness of a newly wired invention. Those tests deserve separate columns.";
            case 2 -> "This is still the account of "+subject(story.family())+". A revised price cannot stamp a cargo manifest or clear a route. The goods will need to make their own journey.";
            case 3 -> "The underlying story concerns "+subject(story.family())+". A market figure is worth knowing, but it cannot count the food in a cupboard or the people who need it.";
            case 4 -> "The subject is "+subject(story.family())+", despite our opinion desk's willingness to broaden the investigation to everything. The fresh evidence concerns the price, not a newly discovered disaster.";
            default -> "The subject remains "+subject(story.family())+". Today's quotation changes the valuation, not the facts of the earlier dispatch. A balance sheet and an explanation should still be examined separately.";
        };
        String closing=switch(desk) {
            case 1 -> "We shall keep the old chart. The next demonstration may otherwise claim to have invented this one.";
            case 2 -> "The earlier dispatch is filed beside today's quotation. Neither will be reimbursed as excess baggage.";
            case 3 -> "The old clipping stays in the file. Village errands will provide their own verdict in due course.";
            case 4 -> "Our headline department wanted a larger conclusion. It has been offered a larger pencil instead.";
            default -> "Readers are advised to keep the old account: certainty is much easier to sell when nobody keeps receipts.";
        };
        return paragraphs(opening,meaning,column(s,story.outlet(),""),closing,
                FACTS+facts+"\nEarlier report: Day "+story.day()+".");
    }
    private static String subject(String family) {
        return switch(family) {
            case "NETHER_SUPPLY_CRISIS","PORTAL_REOPENING" -> "the Nether freight routes";
            case "REDSTONE_REVOLUTION","COPPER_GRID_BUILDOUT" -> "the automation trade";
            case "GOLDEN_HARVEST","FISHERY_RECOVERY" -> "regional food supplies";
            case "CREEPER_CATASTROPHE","REGIONAL_REBUILDING" -> "regional transport and rebuilding";
            case "RAIL_DISRUPTION","END_EXPEDITION_BOOM" -> "freight and travel";
            case "VILLAGER_CREDIT_SCARE","BANK_STRESS_TEST" -> "regional lending";
            case "DEEPVEIN_DISCOVERY","COAL_SURPLUS" -> "mining and supply";
            case "ENCHANTING_FESTIVAL","POTION_RECALL" -> "enchanting and alchemy";
            case "LUXURY_DEMAND_SLUMP" -> "gemstone demand";
            default -> "the trade covered by the original dispatch";
        };
    }
    /** Render legacy copies without changing their stored facts, IDs, dates or pre-event baselines. */
    static String text(NewsWire.Article a,boolean anonymous,boolean approximate) {
        String body=a.detail();
        if(NewsWire.isPlayer(a.kind()) && !body.contains("@{actor}")) {
            var location=Pattern.compile("District near X -?\\d+, Z -?\\d+\\.").matcher(body);
            body=local(a.kind(),a.quantity(),a.id(),a.day(),a.family(),
                    location.find()?location.group():"From a local village.","");
        } else if(legacy(body)) {
            // Keep measured price/food facts; discard old boilerplate, never manufacture missing historical quotes.
            var retained=new ArrayList<String>();
            for(String line:body.split("\n")) {
                String clean=line.replace("Simulated food supply:","Food outlook:");
                clean=clean.replaceAll(" This is a simulated off-screen trade event[^\\n]*","");
                if(legacy(clean))continue;
                if(!clean.isBlank())retained.add(clean.replace("off-screen ",""));
            }
            body=legacyExpansion(a,String.join("\n\n",retained));
        }
        boolean sentenceStart=switch(a.kind()) {
            case FOOD_REMOVED,FOOD_RETURNED,DONATION,REPLANTED -> true;
            default -> false;
        };
        String actor=anonymous?(sentenceStart?"An unnamed resident":"an unnamed resident"):
                a.actor().isBlank()?(sentenceStart?"A local resident":"a local resident"):a.actor();
        body=body.replace("@{actor}",actor);
        if(!a.village().isEmpty()&&approximate)
            body=body.replaceAll("District near X -?\\d+, Z -?\\d+\\.","From a local village.");
        return a.outlet()+" | Day "+a.day()+"\n"+(a.kind()==NewsWire.Kind.FEATURE?"Letters, columns & notices":a.village().isEmpty()?"World-market dispatch":"Local report")
                +"\n\n"+a.headline().replace("off-screen ","")+"\n\n"+"By "+NewsColumns.byline(a.outlet())+"\n\n"+body;
    }
    private static String legacyExpansion(NewsWire.Article a,String facts) {
        if(a.kind()==NewsWire.Kind.MARKET) {
            try {
                var event=EconomyEngine.MarketEvent.valueOf(a.family());
                if(event!=EconomyEngine.MarketEvent.NONE) {
                    var scene=NewsStoryCatalog.scene(a.family());
                    return paragraphs(facts,scene.opening(),scene.detail(),scene.angle(),
                        "Beyond the first announcement lies the slower part of the trade. Goods must reach a buyer, costs must fit inside an account, and an attractive plan must find someone prepared to carry it out. The next developments will meet those tests at the counter rather than on the front page.",
                        scene.outlook());
                }
            } catch(IllegalArgumentException unknownFamily) { /* Preserve custom historic dispatches. */ }
        }
        return paragraphs(facts,
            "The earlier edition remains on the desk beside the present account. A headline catches a moment quickly; the ordinary business beneath it moves at a different pace. Orders, resources and the people who depend on them continue beyond the moment when the first report reaches the stands.",
            a.village().isEmpty()?"Between one quotation and the next, the same emerald can look like an opportunity to one trader and a useful exit to another. That difference gives the market its conversation. The figures carried in this edition mark where those competing expectations have brought the account."
                :"The village's food outlook belongs beside the everyday work of keeping a settlement supplied. Growing, carrying and putting something useful within reach are small tasks with a large place in that story. They continue while other concerns come and go at the top of the page.",
            "There is usually less ceremony at the useful end of a day's work than at its announcement. A load arrives, an order finds a buyer, or a field receives the attention it needs. The next part of the account takes shape through those smaller encounters, with little regard for an impressive headline.",
            "For now, these are the figures and events carried in this edition. The ledger has its place for them, and the next day's business has yet to fill its own page. What follows will have to make its way through the same practical world as everything that came before.");
    }
    private static boolean legacy(String text) {
        String s=text.toLowerCase(Locale.ROOT);
        return s.contains("satire")||s.contains("this bulletin reports")||s.contains("simulated off-screen")
            ||s.contains("not proof")||s.contains("this measures the price path")||s.contains("not a count of")
            ||s.contains("other residents, production and consumption")||s.contains("editorial commentary")
            ||s.contains("not the only influence")||s.contains("one headline is not a forecast")
            ||s.contains("growth target is not")||s.contains("price changes do not tell us");
    }
    private NewsNarrative() {}
}
