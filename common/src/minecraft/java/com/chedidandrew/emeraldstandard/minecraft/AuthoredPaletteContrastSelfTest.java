package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Semantic palette contract: roof families contrast with walls in every approved palette. */
final class AuthoredPaletteContrastSelfTest {
    private AuthoredPaletteContrastSelfTest() { }

    static void run() {
        for (var dialect : VillageArchitecture.BiomeDialect.values()) {
            for (var palette : VillageArchitecture.requireBlueprint("cottage_hearth_01", 4).paletteIds()) {
                var plan = AuthoredVillageStructures.plan(ProjectType.COTTAGE, "cottage_hearth_01", 4,
                        palette, VillageArchitecture.DRESSING_RESTRAINED,
                        VillageArchitecture.Character.RUSTIC, dialect);
                Block expectedStair = switch (dialect) {
                    case DESERT -> Blocks.ACACIA_STAIRS;
                    case TAIGA -> Blocks.DEEPSLATE_TILE_STAIRS;
                    default -> Blocks.DARK_OAK_STAIRS;
                };
                Block expectedSlab = switch (dialect) {
                    case DESERT -> Blocks.ACACIA_SLAB;
                    case TAIGA -> Blocks.DEEPSLATE_TILE_SLAB;
                    default -> Blocks.DARK_OAK_SLAB;
                };
                if (plan.materials().roofStairs() != expectedStair
                        || plan.materials().roofSlab() != expectedSlab) {
                    throw new IllegalStateException("Complementary roof palette lost: " + dialect + "/" + palette);
                }
                if (plan.base().stream().noneMatch(c -> c.state().is(expectedStair))) {
                    throw new IllegalStateException("Roof palette was not used by production plan: " + dialect);
                }
            }
        }
        System.out.println("PASS contrasting production roofs (five dialects x three palettes)");
    }
}
