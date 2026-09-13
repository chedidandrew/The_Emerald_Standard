package com.chedidandrew.emeraldstandard.core;

/** Player advice names an observed problem, never invents a reason from a stalled percentage. */
public final class ConstructionGuidance {
    private ConstructionGuidance() {}
    public static String advice(String phase) {
        return switch (phase == null ? "" : phase) {
            case "waiting_for_entities" -> "Someone is in the work area. Crews resume automatically once it is clear.";
            case "waiting_for_support" -> "Crews are checking the supports. They will try again; keep the work area clear.";
            case "preparing_fence" -> "Crews are securing the worksite fences. Work continues automatically.";
            case "waiting_for_chunks", "waiting_for_player" -> "Crews are waiting for your return. Stay near the site while they work.";
            case "protected" -> "Part of the site cannot be altered. Check the land's building permissions.";
            case "manual_repair" -> "Repairs need your attention. Inspect the damaged building; your belongings will be left alone.";
            case "repairing" -> "Crews are repairing unfinished work before continuing.";
            case "blocked", "retry_in_place" -> "The work area is obstructed. Crews will check again. Keep the entrance clear; stored belongings will not be moved.";
            default -> "";
        };
    }
    public static String project(EconomyState.VillageProject p, String phase) {
        if (p.manualRepairRequired) return advice("manual_repair");
        if (p.relocationPending) return "Surveyors are finding a replacement site. The previous building remains.";
        if (p.abstractOnly) return "Included in the village accounts; no building is planned here.";
        if (p.originPos == 0) return "Searching for a safe building site. Surveyors continue checking nearby plots; no action is needed.";
        boolean inspection = !p.materializedComplete && p.sitePreparationComplete
                && p.totalBlocks > 0 && p.materializedBlocks >= p.totalBlocks;
        String observed = advice(phase);
        if (!observed.isEmpty()) return (inspection ? "Final inspection pending. " : "") + observed;
        if (!p.sitePreparationComplete) return "Crews are preparing the ground and fences.";
        if (inspection)
            return "Construction finished; final inspection pending. Crews will recheck the site before opening.";
        if (p.blocked) return advice("blocked");
        return "Building: " + (p.totalBlocks <= 0 ? "preparing"
                : Math.min(100, (long)p.materializedBlocks * 100 / p.totalBlocks) + "%") + ".";
    }
}
