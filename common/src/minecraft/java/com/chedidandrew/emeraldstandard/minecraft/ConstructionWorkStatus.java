package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Only physically observed obstructions accumulate loaded time. Never persists world references. */
final class ConstructionWorkStatus {
    private record Key(UUID village,long project) { }
    private record Wait(ServerLevel level,BlockPos pos,BlockState obstruction,long origin,BlockState occupiedAfter) { }
    private static final Map<Key,Wait> WAITING = new HashMap<>();
    static void reset() { WAITING.clear(); }
    static void blocked(ServerLevel level, UUID village, long project, BlockPos pos, long origin) {
        WAITING.put(new Key(village,project),new Wait(level,pos.immutable(),level.getBlockState(pos),origin,null));
    }
    static void occupied(ServerLevel level,EconomyService economy,UUID village,long project,
            BlockPos pos,long origin,BlockState after) {
        economy.observeConstructionObstruction(village,project,0);
        WAITING.put(new Key(village,project),new Wait(level,pos.immutable(),level.getBlockState(pos),origin,after));
    }
    static boolean waiting(UUID village,long project) { return WAITING.containsKey(new Key(village,project)); }
    static boolean waitingForEntities(UUID village,long project) {
        Wait wait = WAITING.get(new Key(village,project));
        return wait != null && wait.occupiedAfter != null;
    }
    static void progressed(EconomyService economy,UUID village,long project) {
        if (WAITING.remove(new Key(village,project)) != null) economy.observeConstructionObstruction(village,project,0);
    }
    static void tick(MinecraftServer server,EconomyService economy) {
        if (server.overworld().getGameTime()%20 != 0) return;
        for (var entry : List.copyOf(WAITING.entrySet())) {
            var key=entry.getKey(); var wait=entry.getValue();
            var snapshot=economy.developmentVillageSnapshot(key.village);
            var p=snapshot==null?null:snapshot.village().projects.stream().filter(v->v.projectId==key.project).findFirst().orElse(null);
            if (p==null || p.originPos!=wait.origin || p.materializedComplete || p.manualRepairRequired) { WAITING.remove(key); continue; }
            if ((!economy.forcedVillageDevelopment() && !VillageConstructionPolicy.eligible(snapshot.village(),p))
                    || !wait.level.hasChunk(wait.pos.getX()>>4,wait.pos.getZ()>>4)) continue;
            if (!wait.level.getBlockState(wait.pos).equals(wait.obstruction)) { progressed(economy,key.village,key.project); continue; }
            if (wait.occupiedAfter != null) {
                if (VillageConstructionOccupancy.mayChange(wait.level,wait.pos,wait.obstruction,wait.occupiedAfter))
                    progressed(economy,key.village,key.project);
                continue; // A visitor is never an obstruction that retires a founding home.
            }
            economy.observeConstructionObstruction(key.village,key.project,20);
            if (p.obstructionLoadedTicks >= 5_980 && economy.recoverObstructedFoundingHome(key.village,key.project,wait.level.getGameTime())) {
                WAITING.remove(key);
                ConstructionDiagnostics.record(key.village+"/"+key.project,"founding_home_relocated",0,p.totalBlocks,
                        wait.level.getGameTime(),"Old partial lot protected; unused budget transferred, consumed materials paid again");
            }
        }
    }
}
