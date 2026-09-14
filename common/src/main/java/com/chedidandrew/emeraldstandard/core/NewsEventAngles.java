package com.chedidandrew.emeraldstandard.core;

/** Event-specific desk observations, not additional incidents or claims of physical recovery. */
final class NewsEventAngles {
    static String reaction(String family, int variant) {
        boolean first = variant == 0;
        return switch (family) {
            case "NETHER_SUPPLY_CRISIS" -> first
                    ? "A crate of Nether goods has two prices: the one on the label and the trouble of obtaining another. In a squeeze, the second can dominate the conversation. An empty shelf cannot be restocked with a persuasive explanation, however attractively the explanation is wrapped."
                    : "For a small buyer, scarcity is less a theory than a choice about which purchase can wait. The freight trade can debate margins all afternoon; a workshop missing one ingredient may find that its entire afternoon is already waiting beside the portal.";
            case "PORTAL_REOPENING" -> first
                    ? "An open portal improves an itinerary immediately. It does less for a crate still at the wrong end of a warehouse. The useful work now lies between permission to travel and an actual delivery, a distance measured in preparations as much as blocks."
                    : "Relief at an open route deserves a place in the accounts, but not the whole page. Buyers still need a price and carriers still need a load. The gate has finished its part of the argument; the cargo has yet to finish its journey.";
            case "REDSTONE_REVOLUTION", "COPPER_GRID_BUILDOUT" -> first
                    ? "A buyer of new equipment has a wonderfully impolite question available: what does it save? A glittering row of lamps can answer several questions about lamps while leaving that one untouched. The strongest demonstration is work completed after the demonstrator has gone home."
                    : "Better wiring can change a workshop without changing the size of its doorway. That is an attractive proposition until the wiring occupies the doorway. Useful improvements must fit around the people expected to use them, not merely inside the inventor's explanation.";
            case "GOLDEN_HARVEST", "FISHERY_RECOVERY" -> first
                    ? "The kitchen is the least ceremonial destination in the supply chain and arguably the most important. An improved food outlook still needs storage, transport and a buyer. A fine account of abundance will not keep the rain off a sack or freshness inside a fish."
                    : "Plenty makes room for a different kind of bargaining. Producers want a fair return for their work; households want tomorrow's meal to cost less than yesterday's worry. Between them stands the ordinary business of getting something perishable somewhere useful before it ceases to be either.";
            case "CREEPER_CATASTROPHE", "REGIONAL_REBUILDING" -> first
                    ? "The price of replacement materials is visible on a board. The loss of a familiar route is harder to list. Restoring passage means more than finding a profitable order: it means making an ordinary journey ordinary again, for people who did not choose to lose it."
                    : "Rebuilding begins with decisions that rarely make grand headlines: which stretch must be safe first, which supplies can arrive, which journey cannot wait. The work has value beyond the businesses paid to do it. A community needs a usable road, not simply a larger bill.";
            case "RAIL_DISRUPTION", "END_EXPEDITION_BOOM" -> first
                    ? "There is a considerable difference between a route drawn across a map and a load carried across the world. The first fits neatly on a desk. The second needs provisions, usable ground and a return journey that has not been omitted to improve the estimate."
                    : "Freight has a habit of collecting costs between departure and arrival. An attractive quotation may cover the journey beautifully until somebody asks about the journey back. The useful question for a merchant is not merely whether cargo can move, but whether the whole trip makes sense.";
            case "VILLAGER_CREDIT_SCARE", "BANK_STRESS_TEST" -> first
                    ? "A cautious lender studies the space between a promise and the earnings meant to support it. That space is often decorated with confident language. Removing the decoration leaves a plainer question: what money will actually be available when the repayment falls due?"
                    : "Confidence is useful at a lending desk, but it is a poor substitute for a cash book. The same proposal can look splendid in a speech and rather cramped on a repayment schedule. The arithmetic has never shown much willingness to applaud.";
            case "DEEPVEIN_DISCOVERY", "COAL_SURPLUS" -> first
                    ? "Supply beneath the ground and supply beside a furnace are separated by a working day, and sometimes many. The mining trade must account for picks, handling and transport before a promising quantity becomes a useful load. A merchant cannot shovel an estimate."
                    : "Cheap material is not automatically cheap work. Hauling, sorting and finding a buyer still occupy the day. A larger supply can delight the purchaser while leaving the producer with a more demanding calculation, particularly when the minecart insists on being filled before it will carry anything.";
            case "ENCHANTING_FESTIVAL", "POTION_RECALL" -> first
                    ? "Specialist goods ask buyers to trust more than a bright label. Ingredients, preparation and the result all matter. The impressive part of a demonstration is not the flourish at the beginning but whether the promised effect remains impressive after the bottle or book has changed hands."
                    : "A complicated recipe creates several opportunities for a simple disappointment. The trade's lasting value lies in dependable results, not elaborate descriptions of the vessel. Packaging may improve a shelf considerably while doing very little for the person who needs what is inside.";
            case "LUXURY_DEMAND_SLUMP" -> first
                    ? "A gemstone can wait on a shelf rather more comfortably than its seller can wait for a customer. When buyers postpone luxuries, the shop's display changes less than its accounts. Beauty remains present; the question is how long the business can afford to admire it."
                    : "Households can defer an ornament more easily than a meal. That gives luxury sellers an awkward rival: everything else a customer could do with the same emeralds. A finer display may attract attention without persuading the purse to follow.";
            default -> first
                    ? "At the counter, the useful question is what this change makes possible and what it makes harder. A quotation travels quickly. Orders, materials and the work behind them must still find their way through the rest of the day."
                    : "Buyers and sellers read the same announcement from opposite sides of an account. Neither can settle the next order with the announcement alone. Price, supply and the work of delivery will have to meet somewhere less comfortable than a headline.";
        };
    }
    private NewsEventAngles() { }
}
