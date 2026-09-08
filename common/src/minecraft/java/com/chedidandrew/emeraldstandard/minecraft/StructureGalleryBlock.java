package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One resolved block from a production structure blueprint used by the isolated review gallery. */
record StructureGalleryBlock(BlockPos position, BlockState state) {
    StructureGalleryBlock {
        if (position == null || state == null) {
            throw new IllegalArgumentException("Gallery blocks require a position and state");
        }
        position = position.immutable();
    }
}
