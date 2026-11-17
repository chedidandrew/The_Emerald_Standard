package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** The handbook's numerical examples must still match the simulation they teach. */
public final class HandbookMechanicsRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String language = Files.readString(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        if(!language.contains("Lots: up to 32.") || !language.contains("Lot retry: 0.1-1s.")
                || !language.contains("No build overrides.") || !language.contains("eight-millisecond healthy-tick window")
                || !language.contains("actual Bank and other reserved footprints still block construction")
                || !language.contains("not guaranteed instant"))
            throw new AssertionError("Both handbook formats must explain accelerated safe lots and their limits");
        if (!language.contains("Fences: final joins.") || !language.contains("Gaps stay open.")
                || !language.contains("Before a building or Bank opens")
                || !language.contains("Missing or replaced blocks and recorded player placements are left alone."))
            throw new AssertionError("Both handbook forms must explain native connection finishing and safe existing-build maintenance");
        if (!language.contains("one quiet economic day of mourning")
                || !language.contains("1 quiet day.")
                || !language.contains("Mobs: safe move.")
                || !language.contains("24 blocks away and six blocks above or below")
                || !language.contains("Waiting never authorizes building through a creature")
                || !language.contains("Faster path batches."))
            throw new AssertionError("Both handbook forms must explain recovery, faster paving and creature risk");
        if (!language.contains("Completed Banks and other buildings")
                || !language.contains("Existing completed Banks can receive this trail too")
                || !language.contains("Paths detour."))
            throw new AssertionError("Detailed Bank walkway backfill and compact path behavior must remain documented");
        if (!language.contains("Automatic Bank construction uses Village: development radius")
                || !language.contains("256 horizontal blocks of the original village center")
                || !language.contains("Build range: 256\\nblocks by default.")
                || language.contains("Within 192 blocks"))
            throw new AssertionError("Guided and compact Bank help must explain the configured activation radius");
        if (!language.contains("Both continue their saved plans and share bounded construction time fairly")
                || !language.contains("resumes ordinary construction pace and eligibility rules")
                || !language.contains("Instant: Banks and\\nprojects continue."))
            throw new AssertionError("Handbook must explain live construction mode switching");
        if (!language.contains("Covered paths settle.")
                || !language.contains("Mine soil: stable.")
                || !language.contains("such as a mine grate")
                || !language.contains("stable construction state")
                || !language.contains("Builders keep that sound footing and continue")
                || !language.contains("Missing walls, required floors and obstructed entrances still need attention"))
            throw new AssertionError("Both handbook formats must explain settled yard paths without waiving required structure");
        if(!language.contains("Hitch caps: once.")||!language.contains("two timber cap extensions")
                ||!language.contains("removed cap extensions are not regenerated"))
            throw new AssertionError("Both handbook forms must document the safe one-shot historical hitch finish");
        if(!language.contains("Forced: lamp bursts.")||!language.contains("immediate support-first batches")
                ||!language.contains("Switching forced mode off restores normal lamp pacing"))
            throw new AssertionError("Both handbook forms must explain forced-only lamp acceleration and safe mode switching");
        verify(EconomyState.DonationPurpose.GENERAL, 0, 0, .50, .25, 0, language, "0.50 Treasury and 0.25");
        if(!language.contains("Player-placed gardens are now recorded and protected")
                ||!language.contains("clovers from Wilder Flowers")
                ||!language.contains("older unrecorded landscaping"))
            throw new AssertionError("Full reader vegetation compatibility, garden protection and provenance limitations must remain documented");
        if(!language.contains("Plants clear.")||!language.contains("mature colliding tumbleweeds")
                ||!language.contains("not permission to overwrite every modded block")
                ||!language.contains("different modded plant that regrows"))
            throw new AssertionError("Both readers must explain modded vegetation clearing, regrowth and protected non-plants");
        verify(EconomyState.DonationPurpose.FOOD, .75, 0, 0, 0, 0, language, "0.75 simulated Food");
        verify(EconomyState.DonationPurpose.HOUSING, 0, .50, 0, .40, 0, language, "0.50 Materials and 0.40");
        verify(EconomyState.DonationPurpose.INFRASTRUCTURE, 0, .50, 0, .40, 0, language, "0.50 Materials and 0.40");
        verify(EconomyState.DonationPurpose.SECURITY, 0, .35, 0, .35, 0, language, "0.35 Materials and 0.35");
        verify(EconomyState.DonationPurpose.TRADE, 0, 0, .65, .15, 0, language, "0.65 Treasury and 0.15");
        verify(EconomyState.DonationPurpose.RESTORATION, 0, 0, 0, 0, 1, language, "Every emerald applied to Restoration adds one emerald");
        near(VillageProsperityEngine.RESTORATION_EMERALD_TARGET, 25);
        near(VillageProsperityEngine.GROWTH_FOOD_PER_RESIDENT, 10);
        near(VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD, 45);
        near(VillageExpansion.CHARTER_FOOD, 200);
        near(VillageExpansion.CHARTER_MATERIALS, 400);
        near(VillageExpansion.CHARTER_TREASURY, 80);
        System.out.println("PASS handbook conversion examples and growth thresholds match the economy");
    }

    private static void verify(EconomyState.DonationPurpose purpose,
            double food, double materials, double treasury, double planning, double restoration,
            String language, String expectedText) {
        var village = new EconomyState.VillageRecord();
        village.foodSupply = village.materialSupply = village.treasury = 0;
        village.developmentPoints = village.restorationFund = 0;
        village.safety = 40;
        village.housingCapacity = 16;
        long spent = EconomyState.applyFundInputs(village, purpose, 10 * EconomyState.MICRO, 0);
        if (spent != 10 * EconomyState.MICRO) throw new AssertionError("Example spending failed: " + purpose);
        near(village.foodSupply, 10 * food);
        near(village.materialSupply, 10 * materials);
        near(village.treasury, 10 * treasury);
        near(village.developmentPoints, 10 * planning);
        near(village.restorationFund, 10 * restoration);
        near(village.safety, 40); // Security inputs are NOT an immediate Safety-point purchase.
        near(village.housingCapacity, 16); // Housing inputs do NOT instantly add capacity.
        String key = "\"guide.the_emerald_standard.handbook.fund_"
                + purpose.name().toLowerCase(java.util.Locale.ROOT) + ".body\":";
        String body = language.lines().filter(line -> line.contains(key)).findFirst().orElseThrow();
        if (!body.contains(expectedText)) throw new AssertionError("Missing numerical explanation: " + purpose);
    }

    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 0.000001)
            throw new AssertionError("Handbook example drifted: " + actual + " != " + expected);
    }
}
