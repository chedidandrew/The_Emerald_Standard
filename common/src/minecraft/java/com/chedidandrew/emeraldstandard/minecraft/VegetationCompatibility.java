package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Classification is evidence, never permission to modify a world cell. */
public final class VegetationCompatibility {
    static final TagKey<Block> GROUND_COVER=tag("ground_cover"), SHRUBS=tag("shrubs"),
            TREE_LOGS=tag("tree_logs"), TREE_LEAVES=tag("tree_leaves"),
            NATURAL_GROUND=tag("natural_ground"), NEVER_CLEAR=tag("never_clear");
    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("the_emerald_standard","vegetation/"+path));
    }
    static boolean forbidden(BlockState s) {
        return s.is(NEVER_CLEAR)||s.hasBlockEntity()||!s.getFluidState().isEmpty()
                ||s.getBlock() instanceof CropBlock||s.getBlock() instanceof FlowerPotBlock
                ||s.is(Blocks.FARMLAND)
                ||s.hasProperty(LeavesBlock.PERSISTENT)&&s.getValue(LeavesBlock.PERSISTENT);
    }
    static boolean leaves(BlockState s) {
        return !forbidden(s) && (s.is(BlockTags.LEAVES)||s.is(TREE_LEAVES))
                && s.hasProperty(LeavesBlock.PERSISTENT)&&!s.getValue(LeavesBlock.PERSISTENT);
    }
    static boolean log(BlockState s) {
        return !forbidden(s)&&(s.is(BlockTags.LOGS)||s.is(TREE_LOGS))
                && !BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("stripped_");
    }
    static boolean groundCover(BlockState s) {
        if(forbidden(s)||log(s)||s.is(BlockTags.LEAVES)||s.is(TREE_LEAVES))return false;
        // Plant inheritance is stronger evidence than render/collision shape: mature modded
        // weeds can have solid collision without becoming a crafted building block. Never use
        // namespace, replaceability or an empty shape alone as permission to erase mod content.
        if(nonVanilla(s)&&plantBlock(s.getBlock()))return true;
        if(s.isSolidRender())return false;
        return s.is(GROUND_COVER)||s.is(BlockTags.FLOWERS)||s.getBlock() instanceof SaplingBlock
                ||s.is(Blocks.SHORT_GRASS)||s.is(Blocks.TALL_GRASS)||s.is(Blocks.FERN)||s.is(Blocks.LARGE_FERN)
                ||s.is(Blocks.DEAD_BUSH)||s.is(Blocks.BROWN_MUSHROOM)||s.is(Blocks.RED_MUSHROOM)
                ||s.is(Blocks.SNOW)||s.is(Blocks.MOSS_CARPET)||s.is(Blocks.LEAF_LITTER)
                ||s.is(Blocks.SHORT_DRY_GRASS)||s.is(Blocks.TALL_DRY_GRASS)||s.is(Blocks.BAMBOO_SAPLING);
    }
    private static boolean plantBlock(Block block) {
        return block instanceof VegetationBlock||block instanceof GrowingPlantBlock
                ||block instanceof VineBlock||block instanceof CactusBlock
                ||block instanceof SugarCaneBlock||block instanceof BambooStalkBlock;
    }
    /** Fresh plant growth may replace an earlier surveyed plant, never its soil or a wall. */
    static boolean moddedPlant(BlockState s) {
        return nonVanilla(s)&&!forbidden(s)
                &&(groundCover(s)||shrub(s));
    }
    private static boolean nonVanilla(BlockState s) {
        var id=BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id!=null&&!id.getNamespace().equals("minecraft");
    }
    static boolean shrub(BlockState s) {
        return !forbidden(s)&&!log(s)&&!s.is(BlockTags.LEAVES)&&!s.is(TREE_LEAVES)
                &&(s.is(SHRUBS)||s.is(Blocks.AZALEA)||s.is(Blocks.FLOWERING_AZALEA)
                ||s.is(Blocks.SWEET_BERRY_BUSH)||s.is(Blocks.BAMBOO)||s.is(Blocks.SUGAR_CANE)
                ||s.is(Blocks.CACTUS)||s.is(Blocks.VINE)||s.is(Blocks.BUSH)||s.is(Blocks.FIREFLY_BUSH));
    }
    static boolean plant(BlockState s) { return groundCover(s)||shrub(s)||leaves(s); }
    static boolean open(BlockState s) { return !forbidden(s)&&(s.isAir()||groundCover(s)); }
    static boolean naturalGround(BlockState s) {
        return !forbidden(s)&&(s.is(NATURAL_GROUND)||s.is(BlockTags.DIRT)
                ||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.PODZOL)||s.is(Blocks.MYCELIUM)
                ||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)||s.is(Blocks.STONE)||s.is(Blocks.ANDESITE)
                ||s.is(Blocks.DIORITE)||s.is(Blocks.GRANITE)||s.is(Blocks.GRAVEL)||s.is(Blocks.DEEPSLATE)
                ||s.is(Blocks.TUFF)||s.is(Blocks.CALCITE)||s.is(Blocks.SNOW_BLOCK)
                ||s.is(Blocks.COAL_ORE)||s.is(Blocks.DEEPSLATE_COAL_ORE)
                ||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES));
    }
    public static void resourcesReloaded(net.minecraft.server.MinecraftServer server) {
        SiteSurveyRejections.reset();
        WalkwayConnections.resourcesReloaded(server);
    }
    private VegetationCompatibility() { }
}
