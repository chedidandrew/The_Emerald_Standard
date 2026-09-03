package com.chedidandrew.emeraldstandard.core;

/** Focused guards for the 0.4 visible-village progression expansion. */
public final class Milestone95RegressionTest {
    private Milestone95RegressionTest() {}

    public static void main(String[] args) {
        if (VillageProsperityEngine.ProjectType.values().length != 10) {
            throw new AssertionError("Expected ten curated village project types");
        }
        for (VillageProsperityEngine.ProjectType type : VillageProsperityEngine.ProjectType.values()) {
            if (!(type.materialCost() > 0.0) || !(type.treasuryCost() >= 0.0)) {
                throw new AssertionError("Invalid project economy for " + type);
            }
            if (type.nominalBlocks() <= 0 || type.nominalBlocks() > 500) {
                throw new AssertionError("Unbounded project template estimate for " + type);
            }
        }
        System.out.println("PASS milestone 0.4 project catalog invariants");
    }
}
