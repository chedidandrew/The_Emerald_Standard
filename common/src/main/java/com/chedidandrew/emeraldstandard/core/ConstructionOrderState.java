package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Version-one sequence boundaries. Completed prefixes and appended upgrades never reshuffle. */
public final class ConstructionOrderState {
    private ConstructionOrderState() { }
    public static boolean valid(List<Integer> cuts, int total) {
        if (cuts == null || cuts.size() == 1 || cuts.size() > 64) return false;
        int prior = -1;
        for (Integer cut : cuts) {
            if (cut == null || cut < 0 || cut <= prior || cut > total) return false;
            prior = cut;
        }
        return true;
    }
    public static List<Integer> extend(List<Integer> cuts, int completed, int total) {
        if (!valid(cuts,total) || completed < 0 || completed > total)
            throw new IllegalArgumentException("Invalid construction sequence boundaries");
        if (completed == total || !cuts.isEmpty() && cuts.getLast() == total) return List.copyOf(cuts);
        var next = new ArrayList<>(cuts);
        if (next.isEmpty()) next.add(completed);
        else if (completed < next.getLast()) throw new IllegalArgumentException("Unfinished sequence cannot expand");
        next.add(total);
        if (!valid(next,total)) throw new IllegalArgumentException("Too many construction sequence segments");
        return List.copyOf(next);
    }
    public static String encode(List<Integer> cuts) {
        return cuts.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    public static List<Integer> decode(String text) {
        if (text == null || text.length() > 768) throw new IllegalArgumentException("Missing or oversized construction order");
        if (text.isEmpty()) return List.of();
        var cuts = Arrays.stream(text.split(",",-1)).map(Integer::valueOf).toList();
        if (!valid(cuts,Integer.MAX_VALUE)) throw new IllegalArgumentException("Invalid construction order");
        return cuts;
    }
}
