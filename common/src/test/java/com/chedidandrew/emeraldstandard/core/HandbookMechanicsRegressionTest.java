package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** The handbook's numerical examples must still match the simulation they teach. */
public final class HandbookMechanicsRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String language = Files.readString(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        verify(EconomyState.DonationPurpose.GENERAL, 0, 0, .50, .25, 0, language, "0.50 Treasury and 0.25");
        verify(EconomyState.DonationPurpose.FOOD, .75, 0, 0, 0, 0, language, "0.75 simulated Food");
        verify(EconomyState.DonationPurpose.HOUSING, 0, .50, 0, .40, 0, language, "0.50 Materials and 0.40");
        verify(EconomyState.DonationPurpose.INFRASTRUCTURE, 0, .50, 0, .40, 0, language, "0.50 Materials and 0.40");
        verify(EconomyState.DonationPurpose.SECURITY, 0, .35, 0, .35, 0, language, "0.35 Materials and 0.35");
        verify(EconomyState.DonationPurpose.TRADE, 0, 0, .65, .15, 0, language, "0.65 Treasury and 0.15");
        verify(EconomyState.DonationPurpose.RESTORATION, 0, 0, 0, 0, 1, language, "1 E to the recorded restoration");
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
