package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Native cue API plus the real two-manager normal/debug scheduler; isolated disposable world only. */
final class DistrictGrowthSelfTest {
    static void verify(ServerLevel level) {
        ConstructionWorkCue.reset();
        ConstructionWorkCue.placed("timber", Blocks.OAK_PLANKS.defaultBlockState(), 50, 100, 10);
        ConstructionWorkCue.placed("masonry", Blocks.STONE.defaultBlockState(), 96, 100, 10);
        var timber = ConstructionWorkCue.recent("timber", 10);
        var masonry = ConstructionWorkCue.recent("masonry", 10);
        require(timber != null && masonry != null && !timber.hit().equals(masonry.hit()), "materials sound identical");
        require(!timber.finishing() && masonry.finishing(), "finishing cue threshold");
        require(ConstructionWorkCue.recent("timber", 1211) == null
                && ConstructionWorkCue.recent("timber", 9) == null, "stale cue");
        ConstructionWorkCue.reset();
        require(ConstructionWorkCue.recent("masonry", 10) == null, "world-session cue leak");
        ForcedDevelopmentSchedulingSelfTest.verify(level);
        System.out.println("PASS district growth native: timber/masonry cues, finishing threshold/expiry/reset, normal shared-budget Bank fairness and debug toggles");
    }
    private static void require(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
    }
}
