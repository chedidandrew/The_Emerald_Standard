package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;

public final class TerrainRoadPlanRegressionTest {
    public static void main(String[] args) {
        require(TerrainRoadPlan.grade(List.of(64, 64, 64), 64).orElseThrow().equals(List.of(64, 64, 64)), "flat");
        require(TerrainRoadPlan.grade(List.of(64, 62, 64, 65, 65), 64).isPresent(), "shallow crater graded");
        require(TerrainRoadPlan.grade(List.of(64, 80), 64).isEmpty(), "cliff rejected");
        require(TerrainRoadPlan.grade(List.of(), 64).isEmpty(), "empty rejected");
        for (int seed = 0; seed < 100; seed++) {
            Random random = new Random(seed);
            List<Integer> ground = new ArrayList<>(); int y = 64;
            for (int i = 0; i < 64; i++) { y += random.nextInt(3) - 1; ground.add(y); }
            List<Integer> grade = TerrainRoadPlan.grade(ground, ground.getFirst()).orElseThrow();
            require(grade.getFirst().equals(ground.getFirst()) && grade.getLast().equals(ground.getLast()), "endpoints");
            for (int i = 0; i < grade.size(); i++) {
                require(Math.abs(grade.get(i) - ground.get(i)) <= 2, "bounded earthwork");
                if (i > 0) require(Math.abs(grade.get(i) - grade.get(i - 1)) <= 1, "walkable grade");
                if (i > 0 && i + 1 < grade.size()) require(!(grade.get(i) < grade.get(i - 1)
                        && grade.get(i) < grade.get(i + 1)), "flat turning landing");
            }
        }
        System.out.println("PASS TerrainRoadPlanRegressionTest: grades, endpoints, cliffs, 100 seeded profiles");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
