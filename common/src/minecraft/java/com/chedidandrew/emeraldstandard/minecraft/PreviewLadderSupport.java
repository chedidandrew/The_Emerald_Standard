package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Review-only ladder bearings. Touching a pane is not a usable attachment face. */
final class PreviewLadderSupport {
    static boolean sturdy(Map<BlockPos,BlockState> cells,BlockPos at,Direction face) {
        BlockState support=cells.get(at);
        return support!=null&&support.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,face);
    }
    static Set<BlockPos> frameGlazedBearings(Plan p,Map<BlockPos,BlockState> cells) {
        Set<BlockPos> changed=new HashSet<>();
        for(var entry:Map.copyOf(cells).entrySet()) if(entry.getValue().getBlock() instanceof LadderBlock) {
            Direction face=entry.getValue().getValue(LadderBlock.FACING);
            BlockPos backing=entry.getKey().relative(face.getOpposite());
            if(!sturdy(cells,backing,face)&&PreviewWindowLighting.glass(cells.get(backing))) {
                // A solid regional mullion retains the neighboring window and climb location.
                // Never repair arbitrary air, doors, workstations or missing shell geometry.
                cells.put(backing,PreviewDoorwayGlazing.jamb(p.sample()).defaultBlockState());
                changed.add(backing);
            }
        }
        validate(p.sample().id(),cells);
        return Set.copyOf(changed);
    }
    static void validate(String id,Map<BlockPos,BlockState> cells) {
        for(var entry:cells.entrySet()) if(entry.getValue().getBlock() instanceof LadderBlock) {
            Direction face=entry.getValue().getValue(LadderBlock.FACING);
            BlockPos backing=entry.getKey().relative(face.getOpposite());
            if(!sturdy(cells,backing,face)) throw new IllegalStateException(
                    "Unsupported review ladder "+id+" at "+entry.getKey()+" backing="+backing+" "+cells.get(backing));
        }
    }
}
