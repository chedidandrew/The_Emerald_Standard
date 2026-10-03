package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** Review-only chair geometry. Stair FACING points to the high back, not the seat opening. */
final class PreviewSeatingAudit {
    static Map<BlockPos,Direction> targets(Map<BlockPos,BlockState> cells,Set<BlockPos> candidates) {
        Map<BlockPos,Direction> result=new LinkedHashMap<>();
        for(BlockPos chair:candidates) {
            BlockState seat=cells.get(chair);
            if(seat==null||!(seat.getBlock() instanceof StairBlock)||seat.getValue(StairBlock.HALF)!=Half.BOTTOM) continue;
            List<Direction> adjacent=new ArrayList<>();
            for(Direction direction:Direction.Plane.HORIZONTAL)
                if(table(cells,chair.relative(direction))) adjacent.add(direction);
            // Never guess between two different tables, or turn architectural/entrance stairs.
            if(adjacent.size()==1) result.put(chair,adjacent.getFirst());
        }
        return Map.copyOf(result);
    }
    static boolean table(Map<BlockPos,BlockState> cells,BlockPos position) {
        BlockState base=cells.get(position),top=cells.get(position.above());
        if(base==null) return false;
        if(base.is(Blocks.LECTERN)||base.is(Blocks.CARTOGRAPHY_TABLE)) return true;
        return top!=null&&((top.getBlock() instanceof SlabBlock&&base.getBlock() instanceof FenceBlock)
                ||(top.getBlock() instanceof BasePressurePlateBlock&&(base.getBlock() instanceof FenceBlock
                ||base.isFaceSturdy(EmptyBlockGetter.INSTANCE,position,Direction.UP))));
    }
    static Map<BlockPos,BlockState> corrected(Map<BlockPos,BlockState> source,Set<BlockPos> candidates) {
        Map<BlockPos,BlockState> result=new LinkedHashMap<>(source);
        targets(source,candidates).forEach((chair,towardTable)->result.put(chair,
                source.get(chair).setValue(StairBlock.FACING,towardTable.getOpposite())));
        return Map.copyOf(result);
    }
    static int validate(Map<BlockPos,BlockState> cells,Set<BlockPos> candidates) {
        var targets=targets(cells,candidates);
        targets.forEach((chair,towardTable)-> {
            if(cells.get(chair).getValue(StairBlock.FACING)!=towardTable.getOpposite())
                throw new IllegalStateException("Chair faces away from its table at "+chair+"; table is "+towardTable);
        });
        return targets.size();
    }
    static Set<BlockPos> lowStairs(Map<BlockPos,BlockState> cells) {
        Set<BlockPos> result=new HashSet<>();
        cells.forEach((pos,state)-> { if(pos.getY()==1&&state.getBlock() instanceof StairBlock) result.add(pos); });
        return Set.copyOf(result);
    }
}
