package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Horizontal site authority. Old conflicting saves remain loadable so they can be recovered. */
public final class ProjectLotSafety {
    private ProjectLotSafety() { }
    public static boolean overlaps(long min, long max, long otherMin, long otherMax, int margin) {
        return (long)x(min) <= (long)x(otherMax) + margin && (long)x(max) + margin >= x(otherMin)
                && (long)z(min) <= (long)z(otherMax) + margin && (long)z(max) + margin >= z(otherMin);
    }
    private static int x(long pos) { return (int)(pos >> 38); }
    private static int z(long pos) { return (int)(pos << 26 >> 38); }

    /** Deterministic winner: completed buildings first, otherwise oldest project in the district. */
    public static boolean losesTo(EconomyState.VillageProject project, EconomyState.VillageProject other) {
        return !project.abstractOnly && !other.abstractOnly && project.originPos != 0 && other.originPos != 0 && project.projectId != other.projectId
                && !project.materializedComplete && (other.materializedComplete || other.projectId < project.projectId)
                && overlaps(project.boundsMinPos, project.boundsMaxPos, other.boundsMinPos, other.boundsMaxPos, 0);
    }

    /** Bounded diagnostic warnings, not a load-time invariant which would strand old worlds. */
    public static List<String> warnings(EconomyState.VillageRecord village) {
        List<String> warnings = new ArrayList<>();
        var projects = village.projects.stream().filter(p -> p.originPos != 0 && !p.abstractOnly).toList();
        for (int i = 0; i < projects.size(); i++) for (int j = i + 1; j < projects.size(); j++) {
            var a = projects.get(i); var b = projects.get(j);
            if (overlaps(a.boundsMinPos, a.boundsMaxPos, b.boundsMinPos, b.boundsMaxPos, 0)) {
                warnings.add("Village " + village.villageId + " overlapping projects " + a.projectId + " and " + b.projectId
                        + "; completed=" + a.materializedComplete + "/" + b.materializedComplete);
                if (warnings.size() >= 64) return List.copyOf(warnings);
            }
        }
        return List.copyOf(warnings);
    }
}
