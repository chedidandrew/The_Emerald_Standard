package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;

/** Survey-only canopy ownership. Never flood through a forest or authorize world writes. */
final class AffectedTreeCanopy {
    static final int REACH=32, NEARBY_REACH=7, MAX_LEAVES=8192;
    static Set<BlockPos> nearbyLogs(ServerLevel level, VillageSitePreparation.Survey survey, BlockPos leaf) {
        var distance=new HashMap<BlockPos,Integer>();var queue=new ArrayDeque<BlockPos>();
        distance.put(leaf,0);queue.add(leaf);Set<BlockPos> logs=new LinkedHashSet<>();int nearest=NEARBY_REACH;
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();int d=distance.get(at);
            if(d>=nearest)continue;
            for(var face:Direction.values()) {
                var next=at.relative(face);
                if(!survey.loaded(next))continue;
                var state=level.getBlockState(next);
                if(VillageSitePreparation.naturalLog(state)) {nearest=d+1;logs.add(next);}
                else if(VillageSitePreparation.naturalLeaves(state)&&distance.putIfAbsent(next,d+1)==null)queue.add(next);
            }
            if(distance.size()>MAX_LEAVES)return Set.of();
        }
        // Only trunks touching the clipped foliage are affected, not every tree in a radius.
        return Set.copyOf(logs);
    }
    static Set<BlockPos> owned(ServerLevel level,VillageSitePreparation.Survey survey,Set<BlockPos> logs) {
        var distance=new HashMap<BlockPos,Integer>();var queue=new ArrayDeque<BlockPos>();
        logs.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(at->{distance.put(at,0);queue.add(at);});
        var rivals=new HashMap<BlockPos,Integer>();var rivalQueue=new ArrayDeque<BlockPos>();
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();int d=distance.get(at);
            for(var face:Direction.values()) {
                var next=at.relative(face);
                if(!survey.loaded(next))return null; // Do not hand over a partial unloaded canopy.
                var state=level.getBlockState(next);
                if(VillageSitePreparation.naturalLog(state)&&!logs.contains(next)) {
                    if(rivals.putIfAbsent(next,0)==null)rivalQueue.add(next);
                } else if(d<REACH&&VillageSitePreparation.naturalLeaves(state)
                        &&distance.putIfAbsent(next,d+1)==null)queue.add(next);
            }
            if(distance.size()>MAX_LEAVES+logs.size())return null;
        }
        // Ties belong to the untouched neighbor. Shared canopies must not drag another tree
        // into the clearing plan merely because its leaves touch an affected crown.
        while(!rivalQueue.isEmpty()) {
            var at=rivalQueue.removeFirst();int d=rivals.get(at);
            if(d>=REACH)continue;
            for(var face:Direction.values()) {
                var next=at.relative(face);
                if(distance.containsKey(next)&&!logs.contains(next)&&rivals.putIfAbsent(next,d+1)==null)rivalQueue.add(next);
            }
        }
        Set<BlockPos> result=new LinkedHashSet<>(logs);
        for(var entry:distance.entrySet()) {
            var at=entry.getKey();if(logs.contains(at)||rivals.getOrDefault(at,Integer.MAX_VALUE)<=entry.getValue())continue;
            if(entry.getValue()==REACH)for(var face:Direction.values()) {
                var next=at.relative(face);
                if(VillageSitePreparation.naturalLeaves(level.getBlockState(next))&&!distance.containsKey(next))return null;
            }
            result.add(at);
            var cap=at.above();
            if(!survey.loaded(cap))return null;
            if(level.getBlockState(cap).is(Blocks.SNOW)||level.getBlockState(cap).is(Blocks.SNOW_BLOCK))result.add(cap);
            // Hanging natural vines lose their only bearing when this crown is removed.
            var below=at.below();
            for(int n=0;n<32;n++,below=below.below()) {
                if(!survey.loaded(below))return null;
                var state=level.getBlockState(below);
                if(!(state.getBlock() instanceof GrowingPlantBlock||state.getBlock() instanceof VineBlock)
                        ||!VillageSitePreparation.vegetation(state))break;
                result.add(below);
            }
            if(!survey.loaded(below))return null;
            var tail=level.getBlockState(below);
            if((tail.getBlock() instanceof GrowingPlantBlock||tail.getBlock() instanceof VineBlock)
                    &&VillageSitePreparation.vegetation(tail))return null;
        }
        return Set.copyOf(result);
    }
    private AffectedTreeCanopy() { }
}
