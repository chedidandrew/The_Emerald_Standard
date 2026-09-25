package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.ConstructionRecoveryWindow;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

final class ConstructionRecovery {
    private static final Map<ServerLevel, Map<String, ConstructionRecoveryWindow>> LEVELS = new WeakHashMap<>();
    static ConstructionRecoveryWindow site(ServerLevel level, String job) {
        var sites = LEVELS.computeIfAbsent(level, unused -> new LinkedHashMap<>(32, .75f, true));
        var result = sites.computeIfAbsent(job, unused -> new ConstructionRecoveryWindow());
        if (sites.size() > 2048) sites.remove(sites.keySet().iterator().next());
        return result;
    }
    static void finish(ServerLevel level, String job) {
        var sites = LEVELS.get(level);
        if (sites != null) sites.remove(job);
    }
    static boolean survives(ServerLevel level, BlockPos target, BlockState state) {
        // Native attachment survival may query an adjacent column. Do not let that check
        // load the neighboring chunk when an unfinished site lies on a chunk boundary.
        for(int x=(target.getX()-1)>>4;x<=(target.getX()+1)>>4;x++)
            for(int z=(target.getZ()-1)>>4;z<=(target.getZ()+1)>>4;z++)
                if(level.getChunkSource().getChunkNow(x,z)==null) return false;
        return state.canSurvive(level, target)
                && (!(state.getBlock() instanceof FallingBlock)
                    || !FallingBlock.isFree(level.getBlockState(target.below())));
    }
}
