package com.chedidandrew.emeraldstandard.minecraft;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Versioned ground treatments, independent of a building's palette or a crossed biome. */
final class WalkwayStyle {
    private WalkwayStyle() {}

    static String forDialect(String dialect) {
        return switch (dialect) {
            case "desert", "savanna", "taiga", "snowy" -> dialect + "_v1";
            default -> "plains_v1";
        };
    }

    static BlockState surface(String style, UUID village, BlockPos pos, boolean shoulder) {
        // One village-wide weathering pattern: shared intersections agree, including negative
        // coordinates. Never use project IDs or mutable random state to select paving.
        long seed = village.getMostSignificantBits() ^ village.getLeastSignificantBits();
        long hash = seed ^ (long)pos.getX() * 0x9E3779B97F4A7C15L ^ (long)pos.getZ() * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        int detail = (int)(hash >>> 60);
        Block block = switch (style) {
            case "desert_v1" -> shoulder ? Blocks.CUT_SANDSTONE
                    : detail < 4 ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE;
            case "savanna_v1" -> shoulder ? (detail < 4 ? Blocks.TERRACOTTA : Blocks.COARSE_DIRT)
                    : detail < 3 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH;
            case "taiga_v1" -> shoulder ? (detail < 6 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE)
                    : detail < 5 ? Blocks.COARSE_DIRT : Blocks.GRAVEL;
            case "snowy_v1" -> shoulder ? Blocks.STONE_BRICKS
                    : detail < 4 ? Blocks.COBBLESTONE : Blocks.ANDESITE;
            case "plains_v1" -> shoulder ? (detail < 4 ? Blocks.COBBLESTONE : Blocks.GRAVEL)
                    : detail < 2 ? Blocks.GRAVEL : Blocks.DIRT_PATH;
            default -> throw new IllegalArgumentException("Unknown saved walkway style: " + style);
        };
        return block.defaultBlockState();
    }
}
