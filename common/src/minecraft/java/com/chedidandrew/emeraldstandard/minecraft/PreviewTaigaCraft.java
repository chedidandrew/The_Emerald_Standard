package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Bark-on, horizontal roof courses. Not a production palette migration. */
final class PreviewTaigaCraft {
    /** Draft only the exposed end grain and eaves of an explicitly authored roof course. */
    static void course(BiomeArchitecturePreview.Builder b,Direction.Axis axis,
            int along,int first,int last,int y,int firstCourse,int lastCourse) {
        if(b.s.style()!=BiomeDialect.TAIGA) return;
        var log=Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,axis);
        for(int end:new int[]{first-1,last+1})
            b.roofEaves.put(axis==Direction.Axis.Z?new BlockPos(along,y,end):new BlockPos(end,y,along),log);
        // One horizontal block beyond the wall; retain the existing pitch and ridge elevation.
        if(along==firstCourse||along==lastCourse) {
            int edge=along==firstCourse?along-1:along+1;
            for(int run=first-1;run<=last+1;run++)
                b.roofEaves.put(axis==Direction.Axis.Z?new BlockPos(edge,y,run):new BlockPos(run,y,edge),log);
        }
    }
    static void apply(BiomeArchitecturePreview.Builder b) {
        if(b.s.style()!=BiomeDialect.TAIGA) return;
        b.cells.replaceAll((pos,state)-> {
            if(pos.getY()<4||(!state.is(Blocks.SPRUCE_PLANKS)&&!state.is(Blocks.SPRUCE_STAIRS)
                    &&!state.is(Blocks.SPRUCE_SLAB))) return state;
            Direction.Axis axis=state.hasProperty(StairBlock.FACING)
                    &&state.getValue(StairBlock.FACING).getAxis()==Direction.Axis.Z?Direction.Axis.X:Direction.Axis.Z;
            return Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,axis);
        });
        // Compose last, without replacing chimneys, higher roof ranges, doors or decorations.
        // Catalog shells already have three blocks of reserved padding; compact shells have two.
        int margin=b.s.id().startsWith("catalog_")?3:b.s.id().startsWith("compact_")?0:1;
        b.roofEaves.forEach((at,state)-> {
            if(at.getX()>=-margin&&at.getZ()>=-margin
                    &&at.getX()<b.s.width()+margin&&at.getZ()<b.s.depth()+margin)
                b.cells.putIfAbsent(at,state);
        });
    }
}
