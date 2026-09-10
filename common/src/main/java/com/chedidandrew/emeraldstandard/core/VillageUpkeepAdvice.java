package com.chedidandrew.emeraldstandard.core;

/** Actionable local help, without exposing construction administration to players. */
public final class VillageUpkeepAdvice {
    public enum Advice { WAIT, FOOD, MATERIALS, TREASURY, HOUSING, SAFETY, LIGHTING, THRIVING, SETTINGS }
    private VillageUpkeepAdvice() { }
    public static Advice choose(VillageExpansion.Reason reason, int population, int housing,
            double food, double safety, int lighting) {
        return switch (reason) {
            case PAUSED, APPROVAL, DISABLED -> Advice.SETTINGS;
            case FOOD -> Advice.FOOD;
            case MATERIALS -> Advice.MATERIALS;
            case UPKEEP, TREASURY -> Advice.TREASURY;
            case CONSTRUCTION, STABILIZING, CATCHING_UP -> Advice.WAIT;
            default -> food < Math.max(40, population * 5.0) ? Advice.FOOD
                    : housing <= population ? Advice.HOUSING
                    : safety < 45 && lighting < 40 ? Advice.LIGHTING
                    : safety < 45 ? Advice.SAFETY
                    : reason == VillageExpansion.Reason.READY ? Advice.THRIVING : Advice.WAIT;
        };
    }
}
