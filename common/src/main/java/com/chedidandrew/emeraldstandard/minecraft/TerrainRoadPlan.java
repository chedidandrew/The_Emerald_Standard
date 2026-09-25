package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;

/** Bounded least-earthwork grading of an ordered, cardinal road; heights are walking surfaces. */
public final class TerrainRoadPlan {
    private TerrainRoadPlan() { }

    public static Optional<List<Integer>> grade(List<Integer> ground, int arrivalHeight) {
        if (ground == null || ground.isEmpty() || ground.size() > 256 || ground.stream().anyMatch(Objects::isNull))
            return Optional.empty();
        int n = ground.size(), infinity = 1_000_000;
        int[][] cost = new int[n][5], previous = new int[n][5];
        for (int[] row : cost) Arrays.fill(row, infinity);
        for (int s = 0; s < 5; s++) {
            int y = ground.getFirst() + s - 2;
            if (y == arrivalHeight) cost[0][s] = Math.abs(s - 2);
        }
        for (int i = 1; i < n; i++) for (int s = 0; s < 5; s++) {
            int y = ground.get(i) + s - 2;
            if (i == n - 1 && s != 2) continue; // Meet existing terrain at the road anchor.
            for (int p = 0; p < 5; p++) {
                int delta = Math.abs(y - (ground.get(i - 1) + p - 2));
                int candidate = cost[i - 1][p] + Math.abs(s - 2) * 4 + delta;
                if (delta <= 1 && candidate < cost[i][s]) {
                    cost[i][s] = candidate; previous[i][s] = p;
                }
            }
        }
        int state = 0;
        for (int s = 1; s < 5; s++) if (cost[n - 1][s] < cost[n - 1][state]) state = s;
        if (cost[n - 1][state] >= infinity) return Optional.empty();
        Integer[] heights = new Integer[n];
        for (int i = n - 1; i >= 0; i--) {
            heights[i] = ground.get(i) + state - 2; state = previous[i][state];
        }
        // A one-cell V cannot carry a stair facing both ways. Fill it to make a flat landing.
        for (int i = 1; i < n - 1; i++) {
            if (heights[i] < heights[i - 1] && heights[i] < heights[i + 1]) {
                heights[i]++;
                if (heights[i] > ground.get(i) + 2) return Optional.empty();
            }
        }
        return Optional.of(List.of(heights));
    }
}
