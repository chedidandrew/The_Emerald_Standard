package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Recurring newspaper columnists, not witnesses or simulated entities. */
final class NewsColumns {
    static String byline(String outlet) {
        return switch(NewsWire.OUTLETS.indexOf(outlet)) {
            case 1 -> "Ada Lever";
            case 2 -> "Mara Basalt";
            case 3 -> "Mabel Field";
            case 4 -> "Flint Quill";
            default -> "Edwin Copper";
        };
    }
    static String column(EconomyState s,String outlet,String headline) {
        if(outlet.equals(NewsWire.OUTLETS.get(1))&&headline.contains("46 villagers"))return "Ada Lever's notebook: A machine requiring forty-six villagers is not necessarily a failure. It may be a village with excellent branding. Before ordering one, ask whether the forty-seventh villager is included in the maintenance agreement. The demonstration always looks more efficient when nobody counts the people behind the curtain.";
        if(outlet.equals(NewsWire.OUTLETS.getFirst())&&headline.contains("three buckets"))return "Edwin Copper's notebook: A vault containing three buckets has achieved liquidity in its most literal form. Unfortunately, depositors tend to request emeralds. I recommend asking a lender whether its reserves can be counted, rather than poured, before paying extra for the polished counter.";
        String[] choices=switch(NewsWire.OUTLETS.indexOf(outlet)) {
            case 1 -> new String[]{
                "Ada Lever's notebook: I remain enthusiastic about labor-saving machinery. In particular, the new designs save their inventors the labor of explaining why a door needs twelve pistons. The customer receives that job, together with a diagram whose most legible instruction is to purchase the next edition.",
                "Ada Lever's notebook: My test for a useful machine is simple: leave it alone long enough to make a sandwich. If it requires rescuing before the bread is cut, the machine has invented employment. There is certainly a market for that, although it is not the one on the box.",
                "Ada Lever's notebook: A blinking lamp proves that a lamp can blink. A second blinking lamp proves that the budget has grown. The difficult third stage is persuading the contraption to do something a person wanted before anyone showed them the lamps.",
                "Ada Lever's notebook: Every prototype deserves a fair trial. Mine usually begins with the question of how to turn it off. A device whose emergency stop requires another working device offers a particularly rich opportunity for the accessories department."
            };
            case 2 -> new String[]{
                "Mara Basalt's dispatch book: There are two prices for Nether freight: the quoted price and the one discovered after departure. The second includes conditions, exclusions and an intimate acquaintance with the ground. My expense account recognizes the first price only.",
                "Mara Basalt's dispatch book: I would welcome an express customs lane if it led somewhere different. A faster route to the same queue mainly rewards those collecting the fee. My luggage, having paid nothing, remains admirably immune to the distinction.",
                "Mara Basalt's dispatch book: Freight insurance is best read somewhere cool. Near lava, the clauses excluding heat, panic and anything regrettable become difficult to appreciate. The paper is quite flammable, a risk apparently covered only by a separate policy.",
                "Mara Basalt's dispatch book: The phrase door-to-door delivery leaves a useful amount unsaid about the space between the doors. That is where the bridges, weather and additional handling charges live. Correspondents live there too; our accommodation claims are less successful."
            };
            case 3 -> new String[]{
                "Mabel Field's notebook: I measure a proposal in potatoes. Can it grow them, store them, or help someone bring them home? This has shortened several impressive presentations. A potato is a demanding audience, but at least it can be eaten after the discussion.",
                "Mabel Field's notebook: The most useful village improvements have ordinary endings: a dry bed, a shorter walk, a cupboard that opens onto supper. Those are difficult subjects for a grand portrait. They are excellent reasons to leave room in the paper.",
                "Mabel Field's notebook: A road should be judged by the journey, not the length of its opening speech. Walk it with a full basket before commissioning the plaque. The basket will supply any missing observations about unnecessary turns.",
                "Mabel Field's notebook: I have yet to meet a roof that becomes more waterproof when its owner sounds confident. Shelter is refreshingly practical that way. The rain arrives without an appointment and carries out its inspection for free."
            };
            case 4 -> new String[]{
                "Flint Quill's column: EXCLUSIVE: tomorrow has declined to confirm our forecast. Its representatives cite a longstanding policy of not existing yet. We consider this evasive and have prepared a large headline for either outcome.",
                "Flint Quill's column: Readers demand certainty, and we intend to supply it just as soon as events have made their preferences clear. Until then, our expert panel consists of a compass and a very confident chicken. Only one has requested an appearance fee.",
                "Flint Quill's column: The Gravel stands firmly against unnecessary alarm, except where it improves circulation. Please remain calm while purchasing additional copies. Folded together, they make a surprisingly good shield against further advice.",
                "Flint Quill's column: We asked our crystal ball for an exclusive. It returned a reflection of the editor. This is the clearest evidence yet that important developments are centered on this newspaper, although the accounts department has disputed the research expense."
            };
            default -> new String[]{
                "Edwin Copper's notebook: A forecast is an admirable product: inexpensive to issue, difficult to return, and easily replaced by a newer model. Before paying for one, ask what the forecaster kept in the previous edition. A wealthy signature is not a warranty.",
                "Edwin Copper's notebook: The respectable investor reads the small print. The very respectable investor charges someone else to explain it. Somewhere in the arrangement an emerald changes hands, which is the part the Ledger can confirm without admiring anybody's cuffs.",
                "Edwin Copper's notebook: Confidence travels first class even when the underlying business walks. I prefer to meet them both at the destination. If only confidence arrives, it generally has an excellent explanation and would like another advance.",
                "Edwin Copper's notebook: Every fee is small when discussed separately. Together they have a remarkable talent for furnishing someone else's house. Investors should therefore inspect the total before congratulating themselves on the modest price of each individual inconvenience."
            };
        };
        int start=(int)(InvestmentGrowth.unit(s.seed,s.economicDay,"column:"+outlet)*choices.length);
        String best=choices[start];int oldest=Integer.MAX_VALUE;
        for(int n=0;n<choices.length;n++) {
            String c=choices[(start+n)%choices.length];int last=-1;
            for(int i=0;i<s.news.size();i++)if(s.news.get(i).detail().contains(c))last=i;
            if(last<oldest){best=c;oldest=last;}
        }
        return best;
    }
    static String closing(String outlet) {
        return switch(NewsWire.OUTLETS.indexOf(outlet)) {
            case 1 -> "The next demonstration should bring a working result. Extension leads are not a substitute.";
            case 2 -> "This dispatch has reached the press. Its travel expenses are still awaiting clearance.";
            case 3 -> "Tomorrow's village will need the same ordinary things. We intend to notice who gets them done.";
            case 4 -> "Further certainty will be available after the event, at the usual cover price.";
            default -> "The ledger closes here. Confidence may continue outside, at its own expense.";
        };
    }
    private NewsColumns() {}
}
