package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;

/** Tiny cosmetic observations from successful placement, never consulted by construction safety. */
final class ConstructionWorkCue {
    record Cue(SoundEvent hit, boolean finishing, long tick) {}
    private static final Map<String, Cue> RECENT = new LinkedHashMap<>();
    static void reset() { RECENT.clear(); }
    static void placed(String job, BlockState state, int done, int total, long tick) {
        if (state.isAir()) return;
        RECENT.put(job, new Cue(state.getSoundType().getHitSound(), total > 0 && 100L * done >= 95L * total, tick));
        if (RECENT.size() > 2048) RECENT.remove(RECENT.keySet().iterator().next());
    }
    static Cue recent(String job, long tick) {
        var cue = RECENT.get(job);
        return cue != null && tick >= cue.tick && tick - cue.tick <= 1200 ? cue : null;
    }
}
