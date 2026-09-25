package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Caller must supply an already loaded chunk. At most 32 transparent surface blocks are skipped. */
public final class DistrictTerrainColors {
    public static int sample(BlockGetter level, LevelChunk chunk, int x, int z) {
        int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int minY = level.getMinY();
        if (height < minY) return 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, height, z);
        for (int n = 0; n < 32 && pos.getY() >= minY; n++, pos.move(0, -1, 0)) {
            var state = chunk.getBlockState(pos);
            var color = state.getMapColor(level, pos);
            if (color == MapColor.NONE) continue;
            // Neighbor height from this same loaded chunk; never cross a chunk boundary.
            int north = (z & 15) > 0 ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) : height;
            var shade = height > north ? MapColor.Brightness.HIGH
                    : height < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            return color.calculateARGBColor(shade);
        }
        return 0;
    }
    private DistrictTerrainColors() {}
}
