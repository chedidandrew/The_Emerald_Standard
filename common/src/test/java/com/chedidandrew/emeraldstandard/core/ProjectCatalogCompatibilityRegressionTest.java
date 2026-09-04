package com.chedidandrew.emeraldstandard.core;

/** Protects persisted beta project identifiers while the visible catalog expands. */
public final class ProjectCatalogCompatibilityRegressionTest {
    private ProjectCatalogCompatibilityRegressionTest() {
    }

    public static void main(String[] args) {
        VillageProsperityEngine.ProjectType[] values =
                VillageProsperityEngine.ProjectType.values();
        if (values.length < 10) {
            throw new AssertionError("The 0.4 project catalog is incomplete: " + values.length);
        }
        if (values[0] != VillageProsperityEngine.ProjectType.COTTAGE
                || values[1] != VillageProsperityEngine.ProjectType.WAREHOUSE
                || values[2] != VillageProsperityEngine.ProjectType.MINE_ENTRANCE) {
            throw new AssertionError(
                    "Legacy project ordering changed; beta world compatibility is at risk");
        }
        for (String legacy : new String[] {"COTTAGE", "WAREHOUSE", "MINE_ENTRANCE"}) {
            if (!VillageProsperityEngine.ProjectType.valueOf(legacy).name().equals(legacy)) {
                throw new AssertionError("Legacy project identifier no longer round-trips: " + legacy);
            }
        }
        System.out.println("PASS project catalog compatibility invariants");
    }
}
