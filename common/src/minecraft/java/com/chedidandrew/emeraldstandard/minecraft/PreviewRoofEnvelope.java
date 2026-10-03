package com.chedidandrew.emeraldstandard.minecraft;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.Builder;

/** Explicit weather enclosure below each roof range. Review geometry only, never world repair. */
final class PreviewRoofEnvelope {
    static void carry(Builder b,int x0,int x1,int z0,int z1,int wallTop,int roofBase) {
        // Do not extend into the occupied/furnished storey. Only close the specified attic band.
        for(int y=wallTop+1;y<roofBase;y++) for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++)
            if(x==x0||x==x1||z==z0||z==z1) {
                BlockPos pos=new BlockPos(x,y,z);
                BlockState material=(x==x0||x==x1)&&(z==z0||z==z1)?b.p.log().defaultBlockState():b.p.wall().defaultBlockState();
                b.roofJoins.putIfAbsent(pos,material);
                if(!closed(b.cells.get(pos),pos)) b.cells.put(pos,material);
            }
    }
    static void seal(Builder b) {
        // Composition can overwrite a higher range's curb with a lower slab or remove it while
        // drafting a skylight. Restore only declared roof join cells, not arbitrary empty space.
        b.roofJoins.forEach((pos,material)-> { if(!closed(b.cells.get(pos),pos)) b.cells.put(pos,material); });
        validate(b.cells,b.roofJoins.keySet());
    }
    static void validate(Map<BlockPos,BlockState> cells,Set<BlockPos> joins) {
        for(BlockPos pos:joins) if(!closed(cells.get(pos),pos))
            throw new IllegalStateException("Unsealed roof-to-wall join at "+pos);
    }
    private static boolean closed(BlockState state,BlockPos pos) {
        return state!=null&&Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,pos));
    }
}
