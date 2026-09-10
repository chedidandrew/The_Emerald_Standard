package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;

/** Stable staggering for fresh lots; never moves reserved buildings or changes their recipes. */
public final class VillageNeighborhoodPlan {
    private VillageNeighborhoodPlan() { }
    public static List<VillageMaterializationPolicy.SiteOffset> offsets(int failures, UUID village) {
        var result = new ArrayList<VillageMaterializationPolicy.SiteOffset>();
        for (var offset : VillageMaterializationPolicy.projectSiteOffsets(failures)) {
            Random random = new Random(village.getLeastSignificantBits() ^ village.getMostSignificantBits()
                    ^ ((long) offset.x() << 32) ^ offset.z());
            result.add(new VillageMaterializationPolicy.SiteOffset(
                    offset.x() + random.nextInt(13) - 6, offset.z() + random.nextInt(13) - 6));
        }
        return List.copyOf(result);
    }
    public static int pocketStyle(UUID village, long project) {
        return Math.floorMod(Objects.hash(village, project), 3);
    }
}
