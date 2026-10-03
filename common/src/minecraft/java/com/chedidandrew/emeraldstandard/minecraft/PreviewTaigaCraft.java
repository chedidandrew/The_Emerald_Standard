package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Bark-on, horizontal roof courses. Not a production palette migration. */
final class PreviewTaigaCraft {
    static void apply(BiomeArchitecturePreview.Builder b) {
        if(b.s.style()!=BiomeDialect.TAIGA) return;
        b.cells.replaceAll((pos,state)-> {
            if(pos.getY()<4||(!state.is(Blocks.SPRUCE_PLANKS)&&!state.is(Blocks.SPRUCE_STAIRS)
                    &&!state.is(Blocks.SPRUCE_SLAB))) return state;
            Direction.Axis axis=state.hasProperty(StairBlock.FACING)
                    &&state.getValue(StairBlock.FACING).getAxis()==Direction.Axis.Z?Direction.Axis.X:Direction.Axis.Z;
            return Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,axis);
        });
    }
}
