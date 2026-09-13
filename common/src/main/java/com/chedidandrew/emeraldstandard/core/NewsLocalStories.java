package com.chedidandrew.emeraldstandard.core;

/** Local prose never guesses permission, motive, money loss, witnesses, casualties or repairs. */
final class NewsLocalStories {
    static String facts(NewsWire.Kind kind, int q) {
        return switch (kind) {
            case FOOD_REMOVED -> "@{actor} took " + q + (q==1?" food item":" food items") + " from village stores.";
            case FOOD_RETURNED -> "@{actor} added " + q + (q==1?" food item":" food items") + " to village stores.";
            case CROPS -> "At press time, " + q + (q==1?" crop position":" crop positions") + " cleared by @{actor} remained without a new planting.";
            case DAMAGE -> q + (q==1?" block was":" blocks were") + " removed from village structures by @{actor}.";
            case DONATION -> "@{actor} contributed " + q + (q==1?" emerald":" emeralds") + " to the village fund.";
            case REPLANTED -> "@{actor} replanted " + q + (q==1?" crop position":" crop positions") + " in the village fields.";
            case VIOLENCE -> "The village has lost " + q + (q==1?" resident. The death was":" residents. The deaths were") + " attributed to @{actor}.";
            default -> throw new IllegalArgumentException("Not local action news");
        };
    }
    static String[] passages(NewsWire.Kind kind) {
        return switch (kind) {
            case FOOD_REMOVED -> new String[] {
                "A pantry is one of the less glamorous places in which village life arranges its future. What sits on a shelf today may be needed by someone who arrives hungry tomorrow. Take something out, and the cupboard has a little less flexibility in answering that arrival.",
                "The cupboard's great weakness as a debating partner is its insistence on arithmetic. It cannot be impressed by a confident explanation, nor can it feed anyone with a particularly elegant empty space. Whatever the wider arrangements, a withdrawal is still a subtraction.",
                "The smallest part of a food store is often its margin for surprises. A late harvest, an extra meal or a visitor at the wrong hour can make that margin useful. The next supplies must pass through the same patient business of growing, gathering and carrying.",
                "Village food passes through a chain of ordinary work: planting, growing, gathering and carrying. The contents of a chest are the visible end of that effort. Returning food to circulation can be a quieter business than taking it out, but it matters just as much.",
                "A store works best when its next visitor finds something useful inside. For the present, the day's account has a withdrawal to enter, and the familiar work of keeping a village supplied continues beyond the lid.",
                "The next chapter may be written by a harvest, a delivery or a return to the same chest. Until then, the shelves offer their customary comment on the affair: exactly what is still there, and not one reassuring potato more."
            };
            case FOOD_RETURNED -> new String[] {
                "Food placed in a village store is a modest kind of news with an unusually practical ending. It leaves something behind that another day can use. The contribution does not need a ceremonial shovel, a committee portrait or a speech about future sandwiches.",
                "The store has received help in a form that shelves understand. Bread, vegetables and other provisions are less interested in a contributor's eloquence than in reaching the place where they are needed. This addition gives the village more to work with.",
                "Restocking is part of a larger rhythm of harvests and meals. A useful delivery can ease the pressure on that rhythm without bringing all the other work to a halt. Fields still need attention, and the next hungry visitor is unlikely to wait for a commemorative plaque.",
                "A cupboard can be both better supplied and still worth watching. The size of this delivery is clear; the needs it meets depend on the food already there and the people who will draw on it. Practical help is no less welcome for having a practical limit.",
                "The report closes with food in the stores and another contribution entered in the day's account. Much village work disappears into the routine it makes possible. This is one of the better reasons for a newspaper to stop and notice it.",
                "For now, there is more on the shelf than this contribution found there. The next part of the story belongs to the everyday business of making that food useful, a subject on which the village's cupboards remain admirably direct."
            };
            case CROPS -> new String[] {
                "A cleared field has an oddly convincing air of completion. There is room to walk, little to gather and a splendid view of the soil. Unfortunately, the part of agriculture that produces the next harvest has not been persuaded to admire the view.",
                "The gap between gathering a crop and starting its replacement is small enough to step across and large enough to matter. A plot with no new planting offers the next season very little to work with, however industrious the earlier visit may have looked.",
                "Along a planted row, the eye follows one green beginning after another. An unplanted position interrupts that rhythm with a square of soil. It asks for a small amount of work now, before the omission becomes part of a much less satisfying harvest later.",
                "Replanting rarely gets the most dramatic part of the harvest story. It happens close to the ground, with little noise and no finished produce to show off. Yet the next useful visit to a field depends on precisely that uncelebrated work.",
                "The next report will have something new to say when planting returns or the village's food outlook changes. In the meantime, the soil is available, the gap is visible, and agriculture awaits the stage that makes it agriculture again.",
                "Bare ground is wonderfully free of complications until a village asks it for lunch. Returning these positions to cultivation would give the story a different ending. At press time, that ending had not yet arrived."
            };
            case DAMAGE -> new String[] {
                "Buildings are easy to discuss as single things until part of one is removed. Then the conversation becomes more particular: which blocks are gone, what depended on them, and whether the space still serves the people who use it.",
                "A wall is a remarkably quiet public argument in favor of keeping the outside outside. Removing part of a structure can change that argument without producing a new plan for the room behind it. The result deserves a closer look than a block count alone can provide.",
                "A building's usefulness rests in its details: shelter from rain, a secure doorway and rooms that can still do their work. Stone and timber are patient materials, but a gap has no interest in waiting politely for the next spell of good weather.",
                "Village construction takes materials, time and a succession of decisions that are less visible than the finished building. An alteration reverses some of that work. Whether the next useful step is replacement or a considered new arrangement depends on what is left.",
                "The story remains with the affected structures. Their next chapter will be written in what can actually be used, repaired or rebuilt, not in the confidence with which anyone describes the empty space.",
                "For now the village's architecture has a subtraction to account for. A sound next step starts by looking at the place itself; the drawing of a door is still a poor substitute for a door that closes."
            };
            case DONATION -> new String[] {
                "The village fund has received something more useful than encouragement alone. An emerald contribution adds room for the settlement's work, even if the particular use of that room still has to be decided alongside other needs.",
                "Public generosity tends to make its least theatrical entrance through a ledger. A number changes, the arithmetic becomes a little less uncomfortable, and work that requires resources has more to draw upon. There are worse ways to improve a village.",
                "A contribution does not lay a foundation by itself. Materials, labor and suitable places still have to come together. What it can do is strengthen the means of arranging that work, a distinction best appreciated by anyone who has tried to build with a congratulatory letter.",
                "The fund serves a village with competing needs rather than a single empty shopping basket. Helping it gives those needs a little more financial room. The outcome will be seen in the choices and work that follow the gift.",
                "For this edition, the news is the contribution itself. The next chapter belongs to the village's use of its resources, where a quiet act of support can become something far more visible over time.",
                "The emeralds have reached the fund. Whatever comes next, today's help is more substantial than a proposal to establish a working group on the possibility of helping at some later date."
            };
            case REPLANTED -> new String[] {
                "New planting has returned to part of the village's fields. It is the beginning of a crop rather than the end of the work, but agriculture has a long history of depending on beginnings that fit in the palm of a hand.",
                "Seeds have an unfortunate disadvantage in public relations: they do not look like lunch. Their value lies in what they may become with time and the right conditions. Putting them back into the ground restores that possibility to the affected positions.",
                "The work leaves a visible difference without promising an instant harvest. Growing still takes time, and other parts of a field may need their own attention. The newly planted spots now have something to do besides display the soil.",
                "Replanting connects two visits that are often treated as separate errands. Someone gathers what the last crop produced; someone gives the next crop a chance to start. A field stays useful when both sides of that arrangement receive attention.",
                "The next chapter belongs to growth. For now, the contribution is rooted in the ground rather than waiting in a speech, and the field has gained a little more future than it had before.",
                "There will be no immediate banquet on the strength of a newly planted row. There may, however, be a more useful reason to return to it. That is the sort of modest prospect on which a great deal of village life is built."
            };
            case VIOLENCE -> new String[] {
                "A resident's death leaves more than a change in a village count. It removes a person from the ordinary exchanges that make a place familiar: work, trade and the simple presence of someone who was there before.",
                "This is a grave entry in the village's account. The scale of a settlement does not make the loss of its people a small matter, and an ordinary day does not become less important because no one thought to record it before it ended.",
                "A bell can carry across the roofs and still leave the hardest part unsaid. Familiar paths continue past the same doors; morning will return to the same square. What has changed belongs to the people who must walk those paths with someone missing.",
                "The practical life of a village and the lives within it are closely joined. Work is carried by people, not merely by the buildings around them. After a loss, what remains may look familiar while the community that uses it has changed.",
                "There is no quick repair for an absence. A roof can be patched and a field planted again, but neither answers the empty place in a community. The ordinary acts of keeping one another fed, sheltered and accompanied carry a different weight after a loss.",
                "This edition leaves the story with the loss, rather than an easy ending. A village's future still has to be lived by those who remain. That continuing work deserves patience, and the dead deserve more than a passing number."
            };
            default -> throw new IllegalArgumentException("Not local action news");
        };
    }
    private NewsLocalStories() {}
}
