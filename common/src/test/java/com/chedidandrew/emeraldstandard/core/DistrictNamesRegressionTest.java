package com.chedidandrew.emeraldstandard.core;

import java.util.*;

public final class DistrictNamesRegressionTest {
    public static void main(String[] args) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            UUID id = new UUID(i * 7717L, i * 1234567L);
            int code = DistrictNames.code(id);
            check(code > 0 && code <= DistrictNames.COUNT, "bounded positive name code");
            String name = DistrictNames.name(code);
            check(name.matches("[A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+"), "readable generated name");
            check(code == DistrictNames.code(UUID.fromString(id.toString())), "reload stability");
            names.add(name);
        }
        check(names.size() > 9800, "broad name variety");
        for (int code = 1; code <= DistrictNames.COUNT; code++) DistrictNames.name(code);
        check(DistrictNames.name(1).equals("Amber Ashbank Commons"), "frozen vocabulary");
        var state = new EconomyState();
        UUID id = UUID.fromString("eceb55c3-af62-45fe-a30a-2fe9b07ae3a6");
        var village = state.village(id);
        village.centerPos = 70;
        var first = VillageDistrictMap.collect(state, id, null);
        state.village(new UUID(33, 44));
        village.centerPos = 96L << 38 | 70;
        var second = VillageDistrictMap.collect(state, id, null);
        check(first.markers().getFirst().district() == second.markers().getFirst().district(),
                "neighbor discovery and village movement do not rename district");
        int[] wire = VillageDistrictMap.encode(second, 1);
        var decoded = VillageDistrictMap.decode(i -> wire[i]);
        check(decoded != null && decoded.markers().equals(second.markers()), "name codes survive wire roundtrip");
        System.out.println("PASS stable district names and variety");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
