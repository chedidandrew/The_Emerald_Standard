package com.chedidandrew.emeraldstandard.core;

/** Authored regional dispatch material. No world mutation, random draws or invented local incidents. */
final class NewsStoryCatalog {
    record Scene(String opening, String detail, String angle, String outlook) {}
    static Scene scene(String family) {
        return switch (family) {
            case "REDSTONE_REVOLUTION" -> new Scene(
                "The region's appetite for automation has grown faster than its patience for instruction manuals. Redstone businesses are drawing fresh attention as workshops look for ways to make familiar tasks run on unfamiliar amounts of wiring.",
                "At the heart of the excitement is an old village ambition: get the work done before the work gets to you. Pistons and circuits offer that possibility, although a machine with a lever on every side can still leave a remarkably large job for the person operating the levers.",
                "The interesting contest is no longer between a machine and a pair of hands. It is between a useful machine and a demonstration that requires its inventor to stand nearby, sweating. Buyers have good reason to inspect the second lever before applauding the first.",
                "The next chapter belongs to workshops that can turn orders into dependable equipment. For now, the redstone trade has the market's attention; keeping it will require something more durable than a lamp that blinks impressively.");
            case "NETHER_SUPPLY_CRISIS" -> new Scene(
                "Nether goods are commanding fresh attention as trade routes tighten. A journey that crosses fire and hostile ground was never a simple errand; the present squeeze has made every crate's arrival feel like an achievement in its own right.",
                "A shortage changes the conversation on both sides of a counter. Buyers ask when the next load will come. Sellers ask what replacing this load will cost. Somewhere between those questions sits a freight invoice with very little interest in reassuring either party.",
                "There are few inexpensive substitutes for a route that reaches the right place. A longer detour still needs supplies, protection and someone willing to explain why the return trip took so long. The Nether remains an excellent place to lose one's sense of proportion, followed by one's luggage.",
                "The immediate question is how much cargo can get through at a cost merchants will bear. Until deliveries become easier to arrange, the argument over scarcity will continue wherever a trader has something left to sell.");
            case "GOLDEN_HARVEST" -> new Scene(
                "A strong regional harvest has put agriculture back at the center of the day's business. There are seasons when the great economic question is whether there will be enough. This one has given the trade a more agreeable problem: making good use of what the fields provide.",
                "Abundance still has a journey ahead of it. Crops must be gathered, stored and moved before they become meals, while a field cannot be persuaded to pause its work until a warehouse finds another corner. A fine harvest is welcome news; a dry roof remains an excellent companion to it.",
                "The bread trade has the unusual advantage of selling optimism that can be eaten. Even so, farmers and merchants meet the same surplus from opposite ends of the scales. What looks like a crowded store to one can look like a bargaining opportunity to the other.",
                "Attention now turns from growing to handling the crop. The success of the season will be felt not only in the fields, but in the ordinary work of carrying food from a place with plenty to a table waiting for it.");
            case "END_EXPEDITION_BOOM" -> new Scene(
                "New expeditions are lifting demand for exotic freight. The frontier promises unusual cargo and unusually demanding journeys, giving outfitters a chance to sell the equipment that stands between an ambitious itinerary and a very short expedition.",
                "The End offers enormous horizons with a discouraging lack of floor. Supplies that seem excessive at the departure gate can look modest once the nearest familiar landmark is behind a portal. Shulker storage is useful here, although no box has yet solved the problem of putting courage in one.",
                "Every expedition creates business before it brings anything back. Transport, tools and provisions must be arranged while the eventual return is still a plan on paper. That gap between an order and an arrival is where the freight trade earns both its margin and its anxious expression.",
                "The next test is whether the rush of preparations becomes a dependable flow of cargo. Until then, opportunity stretches across the horizon, with the usual advice to watch where one steps.");
            case "CREEPER_CATASTROPHE" -> new Scene(
                "A regional transport disaster has disrupted trade and brought security needs into sharp focus. Damage along the route has made an ordinary delivery a harder undertaking, while attention turns to the work required to restore reliable passage.",
                "A broken route reaches beyond the place where the damage occurred. Cargo waits, replacement journeys take longer, and merchants have less certainty about the next arrival. Before the accounts can return to routine, the practical business of moving safely must come first.",
                "Reconstruction may bring orders to workshops, but an order book is a poor measure of what a community has lost. The useful questions are plain ones: what must be made safe, what materials are needed, and which parts of the route can carry traffic again?",
                "The road back will be measured in usable stretches of route and deliveries that reach their destinations. Today's figures capture the market's response. The harder story is the patient work that has to follow the first burst of attention.");
            case "VILLAGER_CREDIT_SCARE" -> new Scene(
                "Caution has spread among regional lenders, unsettling risk assets and changing the tone of the credit trade. Proposals that once travelled on a confident introduction now have to make their way through less accommodating arithmetic.",
                "Credit depends on the future arriving in sufficiently good condition to honor yesterday's promises. When that confidence weakens, an otherwise ordinary loan can acquire several extra questions. The questions themselves are cheap; the delay before an answer can be rather more expensive.",
                "The financial world's favorite safety device is often a reassuring name. A sounder test is whether the underlying business can keep earning when the easy assumptions stop helping. Vault doors remain impressive, but a well-polished hinge is not a repayment.",
                "The coming days will show how far caution travels through lending and investment. For the moment, the market has been reminded that a promise can be written in excellent handwriting and still require careful examination.");
            case "DEEPVEIN_DISCOVERY" -> new Scene(
                "A major ore discovery has changed expectations across the mining trade. What was yesterday a question about finding supply has become a question about bringing newly identified material out of the ground and into use.",
                "Geology has a habit of keeping its accounts in layers rather than ledgers. A rich discovery can widen the possibilities for mining, yet every promising vein still stands behind the work of digging, handling and transport. Finding a deposit and filling a merchant's chest are different stages of the same long job.",
                "The news reaches existing sellers with less uncomplicated delight. More potential supply can make a scarcity argument considerably harder to deliver with a straight face. Buyers, meanwhile, have discovered a sudden scholarly interest in the size of the hole.",
                "Attention now turns to the pace at which the discovery can become usable output. The rock has supplied the headline. Picks, carts and patient work will have to supply the rest of the story.");
            case "PORTAL_REOPENING" -> new Scene(
                "Nether trade routes have reopened after maintenance, bringing a welcome change to the freight outlook. An open route gives merchants another chance to arrange the journeys that a closed gate reduced to an increasingly familiar conversation.",
                "Reopening is the beginning of traffic, not the end of logistics. Cargo must be gathered, journeys scheduled and supplies replenished. Even a functioning portal cannot transport a shipment that is still being argued over in a warehouse.",
                "The trade's relief has a practical foundation: another usable route means more room to plan. It may also mean renewed competition for business that had grown accustomed to a captive queue. A toll booth is rarely sentimental about such developments.",
                "The next signs to watch are the pace of departures and the cost of getting goods through. For now the route is available again, and the trade can return to the difficult pleasure of having somewhere to go.");
            case "RAIL_DISRUPTION" -> new Scene(
                "A regional freight stoppage has raised delivery costs and put pressure on the trade that depends on the rails. Goods still need to travel, but the convenient route on yesterday's schedule is no longer a convenient assumption.",
                "Rail carries more than the cargo visible in a minecart. It carries a merchant's expectation that a promised arrival will leave enough time to make the next promise. Once that rhythm breaks, storage space and alternative journeys begin to matter rather more.",
                "A detour can be a solution without being a bargain. Extra handling and a less direct journey have to be paid for somewhere along the line. The timetable, an optimistic work even in better conditions, is now receiving a close literary reading.",
                "The useful turning point will be a return to dependable movement. Until then, the cost of the disruption will be felt in the arrangements made around it, one shipment and one revised plan at a time.");
            case "COPPER_GRID_BUILDOUT" -> new Scene(
                "A regional automation buildout is increasing demand for wiring and machinery. Copper has found itself in the pleasing position of being essential to a plan that cannot be completed merely by describing it enthusiastically.",
                "A working grid is a collection of small practical agreements: components that fit, supplies that arrive and connections that reach the places they are meant to reach. A grand design can be announced in a moment. Laying out its wiring is less impressed by speeches.",
                "Infrastructure orders reach beyond the material at the center of the headline. Tools, transport and workshop capacity must keep pace if the project is to turn from a drawing into something useful. The smallest missing part can become the largest subject at a meeting.",
                "The story ahead is about the passage from orders to installed equipment. Copper has a clear role in that work, while the market considers how much of today's demand can be sustained after the busiest preparations are over.");
            case "COAL_SURPLUS" -> new Scene(
                "Fuel production has outrun demand, leaving the coal trade with more supply to place. A commodity valued for keeping furnaces busy is now prompting a quieter question: how much fuel do buyers need right now?",
                "A surplus needs somewhere to wait. Holding stock ties up space and capital, while moving it requires a buyer with both a use for the material and a price in mind. A shed can accommodate a great deal of coal, but only a modest amount of wishful thinking.",
                "The same conditions meet different trades in different ways. Buyers can consider their next purchase with more choice; producers have to decide how readily to compete for it. Neither side is likely to begin the conversation with the number it expects to finish on.",
                "The next chapter depends on whether use catches up with output. Until it does, the coal business has ample fuel for its furnaces and an equally abundant supply of discussion about its stores.");
            case "ENCHANTING_FESTIVAL" -> new Scene(
                "A regional enchanting fair has increased demand for magical supplies. Lapis and related trades are enjoying a moment in which a gleaming promise also requires a very tangible collection of ingredients.",
                "Enchanting combines scholarship, materials and a willingness to accept that the desired result may not be the first result. That makes preparation a serious part of the business. A visitor can admire a table for free; putting it to useful work is a different transaction.",
                "The fair gives sellers a concentrated audience, while buyers must distinguish something they want to own from something they merely want to demonstrate to a friend. The most persuasive display is not always the most practical thing to carry home.",
                "Attention will turn to how much demand remains once the fair's busiest days have passed. For now, the trade is enjoying a crowded stage, with plenty of sparkle and the familiar necessity of counting the change.");
            case "LUXURY_DEMAND_SLUMP" -> new Scene(
                "Weaker luxury demand has reduced gemstone spending, putting pressure on a trade accustomed to making rarity sound irresistible. Diamonds remain difficult to find. Persuading a cautious buyer to part with emeralds is proving its own form of prospecting.",
                "Luxury goods compete for money that has other places to go. When customers hesitate, elaborate displays can turn into expensive ways to hold stock. A gemstone keeps its sparkle through a quiet afternoon; the merchant still has to reckon with the afternoon.",
                "The awkward point for the sales pitch is that scarce does not always mean urgently wanted. A buyer who has decided to wait can be remarkably unmoved by an explanation of how fortunate they would be to spend more.",
                "The trade now needs either renewed appetite or terms that make the next purchase easier to justify. Until one arrives, the distance between admiration and a completed sale remains the most important measurement in the shop.");
            case "FISHERY_RECOVERY" -> new Scene(
                "A fishery has restored supply after a seasonal closure, improving the outlook for the coastal trade. The return of available catch gives transporters and merchants something more useful to discuss than the length of the wait.",
                "Fish do not reward a leisurely supply chain. A successful return depends on handling and movement as well as the catch itself, with little room for cargo to develop an interest in sightseeing. The route from shore to buyer remains part of the business.",
                "Recovery can bring welcome activity without making every decision easy. Sellers need dependable buyers, buyers need dependable arrivals, and both would prefer the other to accept more of the uncertainty. The sea is unlikely to volunteer.",
                "The coming deliveries will show how smoothly the renewed supply finds its market. For the coastal trade, a return to ordinary work can be a substantial piece of good news, even when it arrives smelling unmistakably of fish.");
            case "POTION_RECALL" -> new Scene(
                "A batch recall has disrupted alchemy sales and brought product quality to the front of the counter. Potions ask buyers to place unusual confidence in a bottle; trouble with a batch makes that confidence harder to take for granted.",
                "A recall has practical work behind it. Stock must be checked and the affected trade has to account for goods it can no longer sell as expected. A brightly colored bottle is not much reassurance when the question concerns what is inside.",
                "The wider cost may lie in the next conversation with a customer. Restoring confidence is rarely as simple as producing a more confident label. In alchemy, as in accounting, it helps if the contents can survive a closer look.",
                "The trade's next task is to move beyond the interrupted sales toward dependable supply and quality. The market can react quickly to a recall. Trust is less obliging about keeping to the same timetable.");
            case "BANK_STRESS_TEST" -> new Scene(
                "Regional lenders have passed a stress test, easing concerns in the credit trade. In a business built around future payments, evidence that lenders can withstand pressure is more useful than another impressive description of a vault.",
                "The examination puts attention on resilience rather than the easy appearance of strength. A lender's work does not end when money leaves the counter; it continues through the uncertain business of bringing that money back.",
                "There is something almost radical about asking the accounts to support the confidence. Financial language can furnish a room beautifully, but it cannot stop a draft through a missing wall. This time, the trade has a firmer basis for its relief.",
                "The result offers reassurance as lending decisions continue. It is a chapter in the region's credit story rather than an end to it, and the next set of accounts will still deserve to be read before the celebratory ink dries.");
            default -> new Scene(
                "A regional rebuilding program has increased demand for construction materials. The work of restoring useful places begins with ordinary necessities: a safe plan, suitable supplies and the people able to put them together.",
                "Rebuilding changes the meaning of a delivery. Stone and timber are no longer simply stock on a merchant's list; they are the next stage of work waiting to happen. The route between a supplier and a site matters almost as much as the supplies themselves.",
                "A program can gather momentum in an order book before that momentum is visible in a finished building. Between the two lie foundations, fitting and the unglamorous checks that make the result usable. There is little comfort in a ceremonial ribbon tied around an unfinished doorway.",
                "The story will develop through the work that is actually completed. For now, material demand has strengthened, and the most useful measure of progress remains what the rebuilding effort can put back into service.");
        };
    }
    private NewsStoryCatalog() {}
}
