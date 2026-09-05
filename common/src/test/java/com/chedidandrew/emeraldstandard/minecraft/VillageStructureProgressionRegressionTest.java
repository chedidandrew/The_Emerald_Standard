package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Loader-neutral determinism and compatibility checks for physical village progression. */
public final class VillageStructureProgressionRegressionTest {
    private VillageStructureProgressionRegressionTest() {
    }

    public static void main(String[] args) {
        testStableVariety();
        testMonotonicVisualStages();
        testConnectedDeterministicTrail();
        System.out.println("PASS village structure progression regression tests");
    }

    private static void testStableVariety() {
        UUID village = UUID.fromString("7f71f6d6-ceae-acf7-1de3-ed5c81be0041");
        Set<Integer> variants = new HashSet<>();
        for (long projectId = 1L; projectId <= 64L; projectId++) {
            int first = VillageStructureProgression.variant(
                    village, projectId, VillageProsperityEngine.ProjectType.COTTAGE);
            int second = VillageStructureProgression.variant(
                    village, projectId, VillageProsperityEngine.ProjectType.COTTAGE);
            require(first == second && first >= 0
                            && first < VillageStructureProgression.VARIANT_COUNT,
                    "Structure variant was unstable or out of range");
            variants.add(first);
        }
        require(variants.size() == VillageStructureProgression.VARIANT_COUNT,
                "Deterministic structure presets collapsed to repetitive clones");
    }

    private static void testMonotonicVisualStages() {
        require(VillageStructureProgression.desiredVisualStage(0) == 0
                        && VillageStructureProgression.desiredVisualStage(1) == 0
                        && VillageStructureProgression.desiredVisualStage(2) == 1
                        && VillageStructureProgression.desiredVisualStage(3) == 1
                        && VillageStructureProgression.desiredVisualStage(4) == 2
                        && VillageStructureProgression.desiredVisualStage(5) == 2,
                "Village tiers no longer map to the intended visual stages");
        require(VillageStructureProgression.targetVisualStage(0, 100, 100, 120, 140) == 0
                        && VillageStructureProgression.targetVisualStage(2, 100, 100, 120, 140) == 1
                        && VillageStructureProgression.targetVisualStage(4, 100, 100, 120, 140) == 2
                        && VillageStructureProgression.targetVisualStage(0, 120, 100, 120, 140) == 1
                        && VillageStructureProgression.targetVisualStage(0, 140, 100, 120, 140) == 2,
                "A completed structure upgrade regressed with its village tier");
        boolean rejected = false;
        try {
            VillageStructureProgression.targetVisualStage(2, 100, 120, 110, 140);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Non-append-only visual stages were accepted");
    }

    private static void testConnectedDeterministicTrail() {
        List<VillageStructureProgression.TrailCell> first =
                VillageStructureProgression.trail(0, 0, 23, -17, 99123L, 64);
        List<VillageStructureProgression.TrailCell> repeated =
                VillageStructureProgression.trail(0, 0, 23, -17, 99123L, 64);
        List<VillageStructureProgression.TrailCell> alternate =
                VillageStructureProgression.trail(0, 0, 23, -17, 99124L, 64);
        require(first.equals(repeated) && !first.equals(alternate),
                "Trail routing was not save-deterministic or varied");

        List<VillageStructureProgression.TrailCell> primary = first.stream()
                .filter(cell -> !cell.shoulder())
                .toList();
        require(primary.getFirst().x() == 0 && primary.getFirst().z() == 0
                        && primary.getLast().x() == 23 && primary.getLast().z() == -17,
                "Trail did not join its building and branch anchor");
        require(primary.size() == 41,
                "Trail stopped being a shortest connected route");
        Set<String> positions = new HashSet<>();
        for (int index = 0; index < primary.size(); index++) {
            VillageStructureProgression.TrailCell cell = primary.get(index);
            require(positions.add(cell.x() + ":" + cell.z()),
                    "Trail revisited a cell and created an unstable loop");
            if (index > 0) {
                VillageStructureProgression.TrailCell previous = primary.get(index - 1);
                require(Math.abs(previous.x() - cell.x())
                                + Math.abs(previous.z() - cell.z()) == 1,
                        "Trail contains a disconnected step");
            }
        }
        for (VillageStructureProgression.TrailCell shoulder : first) {
            if (!shoulder.shoulder()) {
                continue;
            }
            require(primary.stream().anyMatch(cell -> Math.abs(cell.x() - shoulder.x())
                            + Math.abs(cell.z() - shoulder.z()) == 1),
                    "Trail shoulder detached from its route");
        }
        require(VillageStructureProgression.trail(0, 0, 100, 100, 1L, 64).isEmpty(),
                "An unbounded trail escaped its planning limit");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
