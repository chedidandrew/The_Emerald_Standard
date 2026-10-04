package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Villagers are 1.95 blocks tall: a 1/16 rug immediately beside a two-block door catches them. */
final class NativeDoorwayClearance {
    static int setBackRugs(Map<BlockPos,BlockState> cells) {
        int removed=0;
        var doors=cells.entrySet().stream().filter(e->e.getValue().getBlock() instanceof DoorBlock
                &&e.getValue().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER).toList();
        for(var door:doors) {
            var facing=door.getValue().getValue(DoorBlock.FACING);
            for(var side:new Direction[]{facing,facing.getOpposite()}) {
                var at=door.getKey().relative(side);var state=cells.get(at);
                if(state!=null&&state.getBlock() instanceof CarpetBlock) {cells.remove(at);removed++;}
            }
        }
        return removed;
    }
}
