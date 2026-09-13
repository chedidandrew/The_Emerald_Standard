package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import java.util.regex.Pattern;

/** Pure, deterministic newsroom prose. Never draws the economic RNG or mutates world state. */
final class NewsNarrative {
    private static int variant(long seed,long day,String key,int n) {
        return (int)(InvestmentGrowth.unit(seed,day,"news-prose:"+key)*n);
    }
    private static String paragraphs(String... parts) {
        return String.join("\n\n",Arrays.stream(parts).filter(p->p!=null&&!p.isBlank()).toList());
    }
    static String market(EconomyState s,String family,String publisher,String quotes) {
        var scene=NewsStoryCatalog.scene(family);
        int v=variant(s.seed,s.economicDay,family,3);
        String opening=switch(v) {
            case 1 -> scene.detail();
            case 2 -> scene.angle();
            default -> scene.opening();
        };
        String second=v==0?scene.detail():scene.opening();
        String third=v==2?scene.detail():scene.angle();
        return paragraphs(opening,second,quotes,third,NewsEditorial.voice(s,publisher),scene.outlook());
    }
    static String roundup(EconomyState s,String family,String publisher,String quotes) {
        boolean up=family.endsWith("UP");
        int v=variant(s.seed,s.economicDay,family,3);
        String[] leads=up?new String[]{
            "The closing bell has left the broad village index higher, giving the trading floor a reason to look pleased with itself. There is a peculiar speed to a good day's explanations: an uncertain purchase in the morning can become a carefully considered strategy by supper.",
            "Green has returned to the broad index, and with it the considerable pleasure of having bought something before it became dearer. The day's advance gives shareholders a better closing account. It also gives yesterday's hesitant decisions a chance to acquire much more distinguished biographies.",
            "A stronger close has put some distance between the village index and its previous mark. For a market so devoted to looking ahead, it has an impressive appetite for celebrating what has just happened. The bell has barely finished before the success begins collecting explanations."
        }:new String[]{
            "The broad village index has finished lower, leaving the trading desks with less to celebrate and rather more arithmetic. A falling close has a way of shortening conversations about genius. The same holdings that inspired expansive plans yesterday now invite a closer look at the bill.",
            "The closing account is lighter at the broad index. Falling prices make a crowded trading floor feel strangely private: everyone has a particular purchase they would prefer not to discuss. By the bell, the day's business has supplied more questions than comfortable answers.",
            "Red has taken the day's broad-index close, interrupting the agreeable notion that a portfolio can improve merely by being left under an optimistic description. The decline is entered now. Tomorrow's conversation starts from a lower mark, however affectionately anyone remembers the previous one."
        };
        return paragraphs(leads[v],quotes,
            "The spread between the strongest and weakest quotations is where the day's smaller stories live. A broad index can conceal a busy argument among its parts: one trade finds buyers while another searches for them. The closing sheet puts those differences beside one another, without giving either side the last word.",
            up?"An advance is welcome to an existing holder and a higher asking price to the next buyer. That difference keeps the market from becoming a simple celebration. Every purchase still needs someone willing to sell, and the seller is not obliged to share the buyer's enthusiasm."
              :"A decline is an uncomfortable reckoning for an existing holder and a different asking price for anyone arriving now. That difference keeps the floor in motion. The word bargain travels quickly through a falling market; deciding where it belongs takes rather longer.",
            NewsEditorial.voice(s,publisher),
            "Beyond the quotation board, supplies still have to reach their destinations and businesses still have to earn their next emerald. The next session will bring another set of prices to those ordinary tasks. For tonight, the ledger closes on these figures, and even the most energetic explanation must wait for fresh ink.");
    }
    static String local(NewsWire.Kind kind,int quantity,long seed,long day,String key,String location,String development) {
        String[] p=NewsLocalStories.passages(kind);
        int v=variant(seed,day,key,4);
        String beat=switch(kind) {
            case VIOLENCE -> "The measure of a village is not only how many roofs it can raise. It is also the place it makes for the lives beneath them. A loss reaches into that quieter measure, where another building or a fuller treasury cannot supply what is missing.";
            case DONATION -> "A settlement grows through many small commitments as well as its larger projects. The useful gift arrives among them, helping turn available resources into choices. The ledger may record the contribution in a single line, but the work ahead will occupy rather more space.";
            case DAMAGE -> "A finished structure gathers ordinary life around it almost invisibly. Paths lead to its door, supplies find a place inside, and a familiar shape becomes part of the village. Changes to that shape matter most at the point where someone tries to use it again.";
            case CROPS,REPLANTED -> "There is no shortcut between a field's beginning and its useful end. Light, time and a succession of small attentions do the work that a grand announcement cannot. The village's next harvest starts here, close enough to the soil to be overlooked by anyone admiring only the skyline.";
            default -> "Food is one of the village's least patient necessities. A splendid roof cannot be eaten, and a full purse still needs somewhere to buy supper. Keeping stores useful ties the smallest errand to the larger life of the settlement, one contribution or withdrawal at a time.";
        };
        return paragraphs(location,NewsLocalStories.facts(kind,quantity),p[v%2],development,
                p[2+(v/2)],beat,p[3-(v/2)],p[4+(v%2)]);
    }
    static String marketFollowup(EconomyState s,NewsEditorial.Story story,double change) {
        boolean late=s.economicDay-story.day()>=7;
        String movement=change < -1?"below":change>1?"above":"close to";
        String facts=String.format(Locale.ROOT,
                "%s returns to the story first carried on Day %d. %s now stands %s its pre-event price: %+.2f%% against the close before that report.",
                story.outlet(),story.day(),story.ticker(),movement,change);
        return paragraphs(facts,
            late?"A week gives a dramatic headline time to encounter the slower business beneath it. Orders have to be priced, holdings reconsidered and expectations brought back to the counter. The original dispatch now sits beside a longer trail of quotations, a less theatrical but increasingly useful account of what followed."
                :"Two days on, the first reaction is no longer the whole story. Traders who met the original dispatch with urgency now have a few more quotations to consider. The price board has continued to move while the ink on the earlier edition has dried.",
            change>1?"The higher quotation leaves holders of this position with an improvement against the earlier mark. For a buyer arriving today, the same movement means paying more. That familiar disagreement between those already inside a trade and those approaching it is still doing plenty of business."
              :change < -1?"The lower quotation has taken some value from the earlier mark. Holders have a less comfortable comparison in front of them, while prospective buyers face a cheaper entry. The two groups can study the same figures and discover very different reasons to linger at the counter."
              :"The quotation remains near the earlier mark. A narrow difference can seem an uneventful ending after an emphatic headline, but a market is under no obligation to provide a dramatic second act. For now, the comparison is quieter than the story that started it.",
            NewsStoryCatalog.scene(story.family()).outlook(),
            NewsEditorial.voice(s,story.outlet()),
            late?"The week ends with this comparison in the ledger. There will be more trading, and the original event will share the board with newer concerns. Its place in the paper is now a continuing chapter rather than a fresh alarm at the top of the page."
                :"The next edition will find the story a little further along. Between now and then, the interesting work belongs to buyers, sellers and the ordinary demands behind their orders. The quotation above is where this chapter closes, not where the whole affair must end.");
    }
    static String localFollowup(NewsEditorial.Story story,double supply,double delta,long day) {
        return paragraphs(String.format(Locale.ROOT,
            "Returning to the village after the report of Day %d, the food outlook stands at %.1f, a change of %+.1f points since that edition.",story.day(),supply,delta),
            delta>1?"The improved outlook gives this return visit a more encouraging starting point. Food sits close to the center of every village's future: it shapes the confidence with which a settlement can attend to other work. More room in that account is welcome, even while the next harvest and the next meal keep their appointments."
                :delta < -1?"The weaker outlook puts food back among the village's pressing concerns. Other ambitions are difficult to pursue on an uncertain supper. A settlement can have plans for taller roofs and busier streets while still depending on the much older business of bringing something useful back from its fields."
                :"The outlook has changed little since the earlier edition. There is no dramatic turn to announce, only the continuing balance between producing food and needing it. In a village, an uneventful account can still represent a great deal of work carried out close to the ground.",
            "Food arrives through several ordinary doors: a working field, a useful delivery, a store kept ready for its next visitor. It leaves through an equally ordinary demand for meals. Keeping those movements in balance is a daily task, not an occasion that ends when the newspaper moves to another headline.",
            "The earlier report remains part of the village's recent story. Today's outlook adds another chapter beside it. The most useful changes will be the ones that last through the next round of work, when plans have to meet weather, growing time and the simple appetite of another day.",
            day-story.day()>=7?"A week after the first report, the village continues beyond the edges of that single event. The next season will be built from smaller errands and longer commitments alike. Much of that effort will never get a headline, although the settlement depends on it more than on the headline it does get."
                :"This early return finds the village between one edition and the next, with its food account still developing. Planting and provisioning are not especially showy occupations. They are, however, very good reasons for a community to keep looking toward tomorrow.");
    }
    /** Render legacy copies without changing their stored facts, IDs, dates or pre-event baselines. */
    static String text(NewsWire.Article a,boolean anonymous,boolean approximate) {
        String body=a.detail();
        if(a.kind().ordinal()>=NewsWire.Kind.FOOD_REMOVED.ordinal() && !body.contains("@{actor}")) {
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
        return a.outlet()+" | Day "+a.day()+"\n"+(a.village().isEmpty()?"World-market dispatch":"Local report")
                +"\n\n"+a.headline().replace("off-screen ","")+"\n\n"+body;
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
