package com.chedidandrew.emeraldstandard.debug;

import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scalar census only: no village/account copy, block reads, chunk loads or construction mutation. */
public final class ConstructionWorkload {
    private ConstructionWorkload() { }

    public static Map<String, Object> snapshot(EconomyState state) {
        long unfinished = 0, placed = 0, started = 0, searching = 0, abstractOnly = 0;
        long attention = 0, completed = 0;
        if (state != null) for (var village : state.villages.values()) {
            for (var project : village.projects) {
                if (project.abstractOnly) { abstractOnly++; continue; }
                if (project.materializedComplete) { completed++; continue; }
                unfinished++;
                if (project.originPos == 0) searching++;
                else placed++;
                if (project.constructionStarted || project.materializedBlocks > 0) started++;
                if (project.blocked || project.manualRepairRequired || project.relocationPending) attention++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("knownVillages", state == null ? 0 : state.villages.size());
        out.put("unfinishedPhysicalProjects", unfinished);
        out.put("placedUnfinishedProjects", placed);
        out.put("startedUnfinishedProjects", started);
        out.put("unplacedUnfinishedProjects", searching);
        out.put("flaggedUnfinishedProjects", attention);
        out.put("completedPhysicalProjects", completed);
        out.put("abstractProjectsExcluded", abstractOnly);
        int banks = state == null ? 0 : state.pendingBankConstructions.size();
        out.put("pendingBankSites", banks);
        out.put("unfinishedPhysicalSitesIncludingBanks", unfinished + banks);
        out.put("scope", "All saved villages and pending Banks, including unloaded or paused sites; counts are backlog, not proof of active placement");
        return out;
    }
}
