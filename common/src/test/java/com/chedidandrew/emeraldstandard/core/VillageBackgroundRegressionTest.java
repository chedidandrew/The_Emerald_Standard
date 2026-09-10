package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.minecraft.*;
import java.util.*;

public final class VillageBackgroundRegressionTest {
    public static void main(String[] args) {
        var id = new UUID(14, 79);
        var first = VillageNeighborhoodPlan.offsets(0, id);
        require(first.equals(VillageNeighborhoodPlan.offsets(0, id)), "deterministic lots");
        require(!first.equals(VillageNeighborhoodPlan.offsets(0, new UUID(45, 22))), "village-specific neighborhoods");
        require(first.size() == VillageMaterializationPolicy.projectSiteOffsets(0).size(), "candidate count preserved");
        require(VillageNeighborhoodPlan.offsets(5, id).size() > first.size(), "failed searches still expand frontier");
        require(VillageNeighborhoodPlan.offsets(5, id).subList(0, first.size()).equals(first), "stable prefix on retries");
        Set<Integer> styles = new HashSet<>();
        for (int i = 0; i < 20; i++) styles.add(VillageNeighborhoodPlan.pocketStyle(id, i));
        require(styles.size() == 3, "square, garden and gathering spot styles");
        require(advice(VillageExpansion.Reason.FOOD) == VillageUpkeepAdvice.Advice.FOOD, "food advice");
        require(advice(VillageExpansion.Reason.MATERIALS) == VillageUpkeepAdvice.Advice.MATERIALS, "material advice");
        require(advice(VillageExpansion.Reason.UPKEEP) == VillageUpkeepAdvice.Advice.TREASURY, "upkeep advice");
        require(advice(VillageExpansion.Reason.CONSTRUCTION) == VillageUpkeepAdvice.Advice.WAIT, "automatic work needs no UI control");
        require(VillageUpkeepAdvice.choose(VillageExpansion.Reason.MATURING, 10, 12, 100, 30, 10)
                == VillageUpkeepAdvice.Advice.LIGHTING, "lighting advice for low safety");
        require(VillageUpkeepAdvice.choose(VillageExpansion.Reason.MATURING, 10, 10, 100, 70, 50)
                == VillageUpkeepAdvice.Advice.HOUSING, "housing advice");
        require(new EconomyState.VillageProject().constructionStarted, "legacy missing marker must be conservative");
        System.out.println("PASS VillageBackgroundRegressionTest: stable varied lots, expanded searches, upkeep advice, legacy start safety");
    }
    private static VillageUpkeepAdvice.Advice advice(VillageExpansion.Reason reason) {
        return VillageUpkeepAdvice.choose(reason, 10, 12, 100, 70, 50);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
