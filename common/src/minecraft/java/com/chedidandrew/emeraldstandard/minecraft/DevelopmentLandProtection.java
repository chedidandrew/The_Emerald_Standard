package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.DevelopmentLand;
import java.io.IOException;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread ownership evidence. Ordinary lighting/vegetation does not reserve land. */
public final class DevelopmentLandProtection {
    private static MinecraftServer server;
    private static DevelopmentLand land;
    private static boolean failed;
    private record Observation(ServerLevel level, BlockPos pos, BlockState state) { }
    private static final Map<String, Observation> PENDING = new LinkedHashMap<>();
    private DevelopmentLandProtection() { }
    public static void start(MinecraftServer current) throws IOException {
        server = current; PENDING.clear(); failed = false;
        land = new DevelopmentLand(current.getWorldPath(LevelResource.DATA));
    }
    public static DevelopmentLand land(MinecraftServer current) {
        if (server != current || land == null || failed) throw new IllegalStateException("Development protection is unavailable; construction is suspended");
        return land;
    }
    public static boolean excludes(ServerLevel level, BlockPos minimum, BlockPos maximum) {
        if (server != level.getServer() || land == null) return false; // isolated test worlds
        return failed || land.excluded(level.dimension().identifier().toString(),minimum.getX(),minimum.getZ(),maximum.getX(),maximum.getZ());
    }
    public static void placed(ServerLevel level, BlockPos pos, BlockState state) {
        if (server != level.getServer() || land == null || !meaningful(state)) return;
        PENDING.put(level.dimension().identifier()+":"+pos.asLong(),new Observation(level,pos.immutable(),state));
    }
    public static void removed(ServerLevel level,BlockPos pos) {
        if(server==level.getServer() && land!=null)
            PENDING.put(level.dimension().identifier()+":"+pos.asLong(),new Observation(level,pos.immutable(),null));
    }
    public static void tick(MinecraftServer current) {
        if (server != current || land == null || failed || PENDING.isEmpty()) return;
        try {
            for (Observation o : PENDING.values()) {
                if (!o.level.hasChunk(o.pos.getX()>>4,o.pos.getZ()>>4)) continue;
                BlockState actual=o.level.getBlockState(o.pos);
                if(o.state==null) {
                    if(!meaningful(actual)) land.observe(o.level.dimension().identifier().toString(),o.pos.asLong(),"");
                } else if(actual.equals(o.state)) land.observe(o.level.dimension().identifier().toString(),o.pos.asLong(),BlockStateParser.serialize(o.state));
            }
            PENDING.clear();
        } catch (IOException error) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("the_emerald_standard").error("Could not save player-build protection; suspending development",error);
        }
    }
    static boolean meaningful(BlockState state) {
        return !state.isAir() && !VillageSitePreparation.vegetation(state) && !VillageSitePreparation.torch(state)
                && (state.hasBlockEntity() || state.isSolidRender());
    }
    static boolean playerBuild(ServerLevel level, BlockPos pos) {
        if (server != level.getServer() || land == null) return false;
        if (failed) return true;
        // A connected local cluster of recorded solid placements, not a lone temporary marker.
        Map<BlockPos,String> nearby = new HashMap<>();
        land.nearby(level.dimension().identifier().toString(),pos.getX(),pos.getZ(),4).forEach((packed,state)-> {
            BlockPos p = BlockPos.of(packed);
            if (Math.abs(p.getX()-pos.getX()) <= 4 && Math.abs(p.getZ()-pos.getZ()) <= 4
                    && Math.abs(p.getY()-pos.getY()) <= 4 && level.hasChunk(p.getX()>>4,p.getZ()>>4)
                    && BlockStateParser.serialize(level.getBlockState(p)).equals(state)) nearby.put(p,state);
        });
        for (BlockPos p : nearby.keySet()) {
            if (p.distManhattan(pos) > 3) continue;
            Set<BlockPos> visited = new HashSet<>(); ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(p);
            while (!queue.isEmpty() && visited.size() < 4) {
                BlockPos next = queue.removeFirst(); if (!nearby.containsKey(next) || !visited.add(next)) continue;
                for (var direction : net.minecraft.core.Direction.values()) queue.add(next.relative(direction));
            }
            if (visited.size() >= 4) return true;
        }
        return false;
    }

    /** High-confidence old timber doorway: an aligned beam supported by two upright posts. */
    static boolean oldTimberFrame(ServerLevel level,BlockPos near) {
        for(var axis:List.of(net.minecraft.core.Direction.Axis.X,net.minecraft.core.Direction.Axis.Z))
            for(int a=-3;a<=1;a++) for(int h=0;h<=3;h++) {
                BlockPos start=axis==net.minecraft.core.Direction.Axis.X?near.offset(a,h,0):near.offset(0,h,a);
                boolean beam=true;
                for(int i=0;i<3;i++) {
                    BlockPos pos=axis==net.minecraft.core.Direction.Axis.X?start.offset(i,0,0):start.offset(0,0,i);
                    if(!pillar(level,pos,axis)) { beam=false; break; }
                }
                if(!beam) continue;
                BlockPos end=axis==net.minecraft.core.Direction.Axis.X?start.east(2):start.south(2);
                if(pillar(level,start.below(),net.minecraft.core.Direction.Axis.Y)
                        && pillar(level,start.below(2),net.minecraft.core.Direction.Axis.Y)
                        && pillar(level,end.below(),net.minecraft.core.Direction.Axis.Y)
                        && pillar(level,end.below(2),net.minecraft.core.Direction.Axis.Y)) return true;
            }
        return false;
    }
    private static boolean pillar(ServerLevel level,BlockPos pos,net.minecraft.core.Direction.Axis axis) {
        if(!level.hasChunk(pos.getX()>>4,pos.getZ()>>4)) return false;
        BlockState state=level.getBlockState(pos);
        return VillageSitePreparation.naturalLog(state) && state.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                && state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)==axis;
    }
}
