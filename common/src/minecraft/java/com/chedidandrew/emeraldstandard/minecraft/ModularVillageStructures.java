package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Immutable modular-v1 building grammar.
 *
 * <p>Randomness selects among curated masses, roofs, frontages, and grouped program details. It
 * never places arbitrary individual blocks. The returned order is persistent world data: changing
 * an existing method requires a new design schema.</p>
 */
final class ModularVillageStructures {
    private ModularVillageStructures() {
    }

    static Layers plan(
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.Recipe recipe,
            VillageArchitecture.Character character,
            VillageArchitecture.BiomeDialect dialect,
            int width,
            int depth,
            int height) {
        Materials materials = materials(character, dialect);
        Canvas base = new Canvas(Set.of());
        if (type == VillageProsperityEngine.ProjectType.MARKET_SQUARE) {
            buildMarket(base, materials, character, recipe, width, depth);
        } else if (type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE) {
            buildMine(base, materials, character, recipe, width, depth);
        } else {
            buildEnclosed(base, materials, character, type, recipe, width, depth, height);
        }

        Canvas stageOne = new Canvas(base.positions());
        appendWorkyard(stageOne, materials, type, recipe, width, depth);

        Set<BlockPos> stageTwoBlocked = new HashSet<>(base.positions());
        stageTwoBlocked.addAll(stageOne.positions());
        Canvas stageTwo = new Canvas(stageTwoBlocked);
        appendLandmark(stageTwo, base, materials, type, recipe, width, depth, height);
        return new Layers(base.values(), stageOne.values(), stageTwo.values(), materials);
    }

    static Materials materials(
            VillageArchitecture.Character character,
            VillageArchitecture.BiomeDialect dialect) {
        Materials base = switch (dialect) {
            case DESERT -> new Materials(
                    dialect,
                    Blocks.SMOOTH_SANDSTONE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.SANDSTONE_STAIRS,
                    Blocks.SANDSTONE_SLAB,
                    Blocks.ACACIA_FENCE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.SANDSTONE_STAIRS);
            case SAVANNA -> new Materials(
                    dialect,
                    Blocks.STONE_BRICKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_STAIRS,
                    Blocks.ACACIA_SLAB,
                    Blocks.ACACIA_FENCE,
                    Blocks.SMOOTH_STONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
            case TAIGA -> new Materials(
                    dialect,
                    Blocks.COBBLESTONE,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB,
                    Blocks.SPRUCE_FENCE,
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_DOOR,
                    Blocks.COBBLESTONE_STAIRS);
            case SNOWY -> new Materials(
                    dialect,
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.DARK_OAK_STAIRS,
                    Blocks.DARK_OAK_SLAB,
                    Blocks.SPRUCE_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.SPRUCE_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
            case PLAINS -> new Materials(
                    dialect,
                    Blocks.STONE_BRICKS,
                    Blocks.OAK_PLANKS,
                    Blocks.STRIPPED_OAK_LOG,
                    Blocks.DARK_OAK_STAIRS,
                    Blocks.DARK_OAK_SLAB,
                    Blocks.OAK_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.OAK_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
        };
        return switch (character) {
            case AGRARIAN -> base.withAccent(dialect == VillageArchitecture.BiomeDialect.DESERT
                    ? Blocks.CHISELED_SANDSTONE
                    : Blocks.MOSSY_COBBLESTONE);
            case MERCANTILE -> base.withAccent(dialect == VillageArchitecture.BiomeDialect.DESERT
                    ? Blocks.SMOOTH_SANDSTONE
                    : Blocks.POLISHED_ANDESITE);
            case RUSTIC -> base.withFoundation(dialect == VillageArchitecture.BiomeDialect.DESERT
                    ? Blocks.SANDSTONE
                    : Blocks.COBBLESTONE);
            case FORMAL -> base.withAccent(dialect == VillageArchitecture.BiomeDialect.DESERT
                    ? Blocks.CUT_SANDSTONE
                    : Blocks.CHISELED_STONE_BRICKS);
        };
    }

    private static void buildEnclosed(
            Canvas canvas,
            Materials materials,
            VillageArchitecture.Character character,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth,
            int height) {
        int wallHeight = switch (type) {
            case COTTAGE -> 3;
            case GUARD_POST -> 5;
            case EXCHANGE_HALL, INN -> 4;
            default -> 4;
        };
        List<Mass> masses = masses(recipe, width, depth, wallHeight);
        for (int index = 0; index < masses.size(); index++) {
            int roof = index == 0 ? recipe.roof() : (recipe.roof() + 1) % 3;
            if (index == 0) {
                addMass(canvas, materials, masses.get(index), roof);
            } else {
                addJoinedMass(canvas, materials, masses.get(index), roof);
            }
        }
        openMassConnection(canvas, recipe, width, depth);
        addEntrance(canvas, materials, recipe, width);
        addFrontage(canvas, materials, recipe, width);
        addCharacterMotif(canvas, materials, character, recipe, width);
        addTypeExterior(canvas, materials, type, recipe, width, depth, height);
        addMirroredSideMarker(canvas, materials, recipe, width);
        addProgramInterior(canvas, materials, type, width, depth);
        if (type == VillageProsperityEngine.ProjectType.GUARD_POST) {
            appendGuardWatchDeck(canvas, materials, width, depth, height);
        }
    }

    private static List<Mass> masses(
            VillageArchitecture.Recipe recipe, int width, int depth, int wallHeight) {
        if (recipe.silhouette() == 0) {
            return List.of(new Mass(1, 1, width - 2, depth - 2, wallHeight));
        }
        if (recipe.silhouette() == 1) {
            int frontDepth = Math.min(5, depth - 2);
            int wingX = recipe.mirrored() ? 1 : width - 4;
            return List.of(
                    new Mass(1, 1, width - 2, frontDepth, wallHeight),
                    new Mass(
                            wingX,
                            frontDepth,
                            3,
                            Math.max(2, depth - frontDepth - 1),
                            wallHeight + 1));
        }
        // Keep the annex one cell inside the lot so the snowy/savanna two-block eave remains
        // inside the preflighted -1..width envelope in both mirror directions.
        int annexX = recipe.mirrored() ? 1 : width - 3;
        return List.of(
                new Mass(2, 1, width - 4, depth - 2, wallHeight + 1),
                new Mass(annexX, 3, 2, Math.max(3, depth - 5), Math.max(3, wallHeight - 1)));
    }

    /** Adds a separately authored mass with explicit union semantics at its shared wall/eave. */
    private static void addJoinedMass(
            Canvas canvas, Materials materials, Mass mass, int roofFamily) {
        Canvas addition = new Canvas(Set.of());
        addMass(addition, materials, mass, roofFamily);
        canvas.mergeIfAbsent(addition);
    }

    private static void openMassConnection(
            Canvas canvas, VillageArchitecture.Recipe recipe, int width, int depth) {
        if (recipe.silhouette() == 1) {
            int frontDepth = Math.min(5, depth - 2);
            int wingX = recipe.mirrored() ? 1 : width - 4;
            canvas.remove(wingX + 1, 1, frontDepth);
            canvas.remove(wingX + 1, 2, frontDepth);
        } else if (recipe.silhouette() == 2) {
            int sharedX = recipe.mirrored() ? 2 : width - 3;
            int connectorZ = Math.min(depth - 3, 4);
            canvas.remove(sharedX, 1, connectorZ);
            canvas.remove(sharedX, 2, connectorZ);
        }
    }

    private static void addMass(
            Canvas canvas, Materials materials, Mass mass, int roofFamily) {
        for (int x = mass.x; x < mass.x + mass.width; x++) {
            for (int z = mass.z; z < mass.z + mass.depth; z++) {
                canvas.put(x, 0, z, materials.foundation.defaultBlockState());
            }
        }
        for (int y = 1; y <= mass.wallHeight; y++) {
            for (int x = mass.x; x < mass.x + mass.width; x++) {
                for (int z = mass.z; z < mass.z + mass.depth; z++) {
                    boolean edge = x == mass.x
                            || x == mass.x + mass.width - 1
                            || z == mass.z
                            || z == mass.z + mass.depth - 1;
                    if (!edge) {
                        continue;
                    }
                    boolean corner = (x == mass.x || x == mass.x + mass.width - 1)
                            && (z == mass.z || z == mass.z + mass.depth - 1);
                    int cadence = x + z * 3;
                    boolean window = y == 2 && !corner && Math.floorMod(cadence, 4) == 1;
                    canvas.put(
                            x,
                            y,
                            z,
                            window
                                    ? Blocks.GLASS_PANE.defaultBlockState()
                                    : (corner ? materials.timber : materials.wall)
                                            .defaultBlockState());
                }
            }
        }
        addRoof(canvas, materials, mass, roofFamily);
    }

    private static void addRoof(
            Canvas canvas, Materials materials, Mass mass, int roofFamily) {
        if (materials.dialect == VillageArchitecture.BiomeDialect.DESERT) {
            addDesertRoof(canvas, materials, mass, roofFamily);
            return;
        }
        int eave = materials.dialect == VillageArchitecture.BiomeDialect.SAVANNA
                        || materials.dialect == VillageArchitecture.BiomeDialect.SNOWY
                ? 2
                : 1;
        if (roofFamily == 2) {
            addHipRoof(canvas, materials, mass, eave);
        } else {
            addGableRoof(canvas, materials, mass, roofFamily == 0, eave);
        }
    }

    private static void addGableRoof(
            Canvas canvas,
            Materials materials,
            Mass mass,
            boolean slopesAcrossX,
            int eave) {
        int span = slopesAcrossX ? mass.width : mass.depth;
        int runStart = slopesAcrossX ? mass.z - eave : mass.x - eave;
        int runEnd = slopesAcrossX
                ? mass.z + mass.depth - 1 + eave
                : mass.x + mass.width - 1 + eave;
        int lower = (slopesAcrossX ? mass.x : mass.z) - eave;
        int upper = (slopesAcrossX ? mass.x + mass.width - 1 : mass.z + mass.depth - 1) + eave;
        int roofY = mass.wallHeight + 1;
        for (int layer = 0; lower + layer <= upper - layer; layer++) {
            int first = lower + layer;
            int second = upper - layer;
            int y = roofY + layer;
            for (int run = runStart; run <= runEnd; run++) {
                if (first == second) {
                    putRoof(canvas, materials.roofSlab, slopesAcrossX, first, y, run, null);
                } else {
                    Direction firstFacing = slopesAcrossX ? Direction.EAST : Direction.SOUTH;
                    Direction secondFacing = slopesAcrossX ? Direction.WEST : Direction.NORTH;
                    putRoof(canvas, materials.roofStairs, slopesAcrossX, first, y, run, firstFacing);
                    putRoof(canvas, materials.roofStairs, slopesAcrossX, second, y, run, secondFacing);
                }
            }
            // Fill both gable ends so the roof reads as an intentional mass from every angle.
            if (layer > 0) {
                for (int across = first + 1; across < second; across++) {
                    if (slopesAcrossX) {
                        canvas.putIfAbsent(across, y, mass.z, materials.wall.defaultBlockState());
                        canvas.putIfAbsent(
                                across,
                                y,
                                mass.z + mass.depth - 1,
                                materials.wall.defaultBlockState());
                    } else {
                        canvas.putIfAbsent(mass.x, y, across, materials.wall.defaultBlockState());
                        canvas.putIfAbsent(
                                mass.x + mass.width - 1,
                                y,
                                across,
                                materials.wall.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void putRoof(
            Canvas canvas,
            Block roof,
            boolean slopesAcrossX,
            int across,
            int y,
            int run,
            Direction facing) {
        BlockState state = roof.defaultBlockState();
        if (facing != null) {
            state = state.setValue(StairBlock.FACING, facing);
        }
        if (slopesAcrossX) {
            canvas.put(across, y, run, state);
        } else {
            canvas.put(run, y, across, state);
        }
    }

    private static void addHipRoof(
            Canvas canvas, Materials materials, Mass mass, int eave) {
        int minX = mass.x - eave;
        int maxX = mass.x + mass.width - 1 + eave;
        int minZ = mass.z - eave;
        int maxZ = mass.z + mass.depth - 1 + eave;
        int y = mass.wallHeight + 1;
        while (minX <= maxX && minZ <= maxZ) {
            if (minX == maxX || minZ == maxZ) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        canvas.put(x, y, z, materials.roofSlab.defaultBlockState());
                    }
                }
                break;
            }
            for (int x = minX; x <= maxX; x++) {
                canvas.put(x, y, minZ, materials.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
                canvas.put(x, y, maxZ, materials.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH));
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                canvas.put(minX, y, z, materials.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
                canvas.put(maxX, y, z, materials.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
            minX++;
            maxX--;
            minZ++;
            maxZ--;
            y++;
        }
    }

    private static void addDesertRoof(
            Canvas canvas, Materials materials, Mass mass, int roofFamily) {
        int roofY = mass.wallHeight + 1;
        for (int x = mass.x - 1; x <= mass.x + mass.width; x++) {
            for (int z = mass.z - 1; z <= mass.z + mass.depth; z++) {
                canvas.put(x, roofY, z, materials.roofSlab.defaultBlockState());
                boolean parapet = x == mass.x - 1
                        || x == mass.x + mass.width
                        || z == mass.z - 1
                        || z == mass.z + mass.depth;
                if (parapet && Math.floorMod(x + z + roofFamily, 2) == 0) {
                    canvas.put(x, roofY + 1, z, materials.accent.defaultBlockState());
                }
            }
        }
        if (roofFamily == 0) {
            return;
        }
        // Narrow annexes use compact roof ornaments instead of forcing a pavilion through their
        // parapet. This keeps all roof families visually distinct on mine sheds and small wings.
        if (mass.width < 5 || mass.depth < 5) {
            int centerX = mass.x + mass.width / 2;
            int centerZ = mass.z + mass.depth / 2;
            if (roofFamily == 1) {
                canvas.put(centerX, roofY + 1, centerZ, materials.fence.defaultBlockState());
                canvas.put(centerX, roofY + 2, centerZ, materials.accent.defaultBlockState());
            } else {
                for (int x = mass.x; x < mass.x + mass.width; x++) {
                    canvas.put(x, roofY + 1, centerZ, materials.accent.defaultBlockState());
                }
            }
            return;
        }
        int pavilionX = roofFamily == 1 ? mass.x + 1 : mass.x + mass.width - 4;
        int pavilionZ = mass.z + Math.max(1, mass.depth / 2 - 1);
        for (int x : new int[] {pavilionX, pavilionX + 2}) {
            for (int z : new int[] {pavilionZ, pavilionZ + 2}) {
                canvas.put(x, roofY + 1, z, materials.fence.defaultBlockState());
                canvas.put(x, roofY + 2, z, materials.fence.defaultBlockState());
            }
        }
        for (int x = pavilionX - 1; x <= pavilionX + 3; x++) {
            for (int z = pavilionZ - 1; z <= pavilionZ + 3; z++) {
                canvas.put(x, roofY + 3, z, materials.roofSlab.defaultBlockState());
            }
        }
    }

    private static void addEntrance(
            Canvas canvas, Materials materials, VillageArchitecture.Recipe recipe, int width) {
        int center = width / 2;
        canvas.remove(center, 1, 1);
        canvas.remove(center, 2, 1);
        BlockState lower = materials.door.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        canvas.put(center, 1, 1, lower);
        canvas.put(center, 2, 1, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        canvas.put(center, 0, 0, materials.accent.defaultBlockState());
        canvas.put(
                center,
                0,
                -1,
                materials.entryStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
    }

    private static void addFrontage(
            Canvas canvas, Materials materials, VillageArchitecture.Recipe recipe, int width) {
        int center = width / 2;
        int left = Math.max(1, center - 2);
        int right = Math.min(width - 2, center + 2);
        if (recipe.frontage() == 1) {
            if (recipe.mirrored()) {
                left = Math.max(1, center - 3);
                right = center + 1;
            } else {
                left = center - 1;
                right = Math.min(width - 2, center + 3);
            }
        } else if (recipe.frontage() == 2) {
            left = Math.max(1, center - 3);
            right = Math.min(width - 2, center + 3);
        }
        for (int x : new int[] {left, right}) {
            canvas.put(x, 0, 0, materials.foundation.defaultBlockState());
            canvas.put(x, 1, 0, materials.fence.defaultBlockState());
            canvas.put(x, 2, 0, Blocks.LANTERN.defaultBlockState());
        }
        // Keep the porch below the main eave so it reads as a separate human-scale layer.
        int canopyY = 3;
        for (int x = left; x <= right; x++) {
            canvas.put(x, canopyY, 0, materials.roofSlab.defaultBlockState());
            if (recipe.frontage() == 2) {
                canvas.put(x, canopyY, -1, materials.roofSlab.defaultBlockState());
            }
        }
        if (recipe.frontage() == 2) {
            // Frame the broad canopy while preserving the actual entrance stair at center.
            canvas.put(center - 1, 0, -1, materials.accent.defaultBlockState());
            canvas.put(center + 1, 0, -1, materials.accent.defaultBlockState());
        }
    }

    private static void addCharacterMotif(
            Canvas canvas,
            Materials materials,
            VillageArchitecture.Character character,
            VillageArchitecture.Recipe recipe,
            int width) {
        int center = width / 2;
        switch (character) {
            case AGRARIAN -> {
                for (int x : new int[] {center - 3, center + 3}) {
                    canvas.put(x, 0, -1, materials.accent.defaultBlockState());
                    canvas.put(
                            x,
                            1,
                            -1,
                            (x < center ? Blocks.POTTED_FERN : Blocks.POTTED_DANDELION)
                                    .defaultBlockState());
                }
            }
            case MERCANTILE -> {
                for (int x = center - 2; x <= center + 2; x++) {
                    if (recipe.frontage() == 2) {
                        canvas.replace(x, 3, -1, Blocks.WOOL.green().defaultBlockState());
                    } else {
                        canvas.put(x, 3, -1, Blocks.WOOL.green().defaultBlockState());
                    }
                }
            }
            case RUSTIC -> {
                canvas.replace(center - 2, 1, 1, materials.timber.defaultBlockState());
                canvas.replace(center + 2, 1, 1, materials.timber.defaultBlockState());
            }
            case FORMAL -> {
                for (int x : new int[] {center - 1, center + 1}) {
                    canvas.replace(x, 1, 1, materials.accent.defaultBlockState());
                    canvas.replace(x, 2, 1, materials.accent.defaultBlockState());
                    canvas.replace(x, 3, 1, materials.accent.defaultBlockState());
                }
            }
        }
    }

    private static void addProgramInterior(
            Canvas canvas,
            Materials materials,
            VillageProsperityEngine.ProjectType type,
            int width,
            int depth) {
        if (type == VillageProsperityEngine.ProjectType.COTTAGE
                || type == VillageProsperityEngine.ProjectType.HOUSE
                || type == VillageProsperityEngine.ProjectType.INN) {
            int beds = type == VillageProsperityEngine.ProjectType.COTTAGE
                    ? 2
                    : type == VillageProsperityEngine.ProjectType.HOUSE ? 3 : 4;
            addBeds(canvas, width, depth, beds);
        }
        List<Block> blocks = switch (type) {
            case COTTAGE -> List.of(Blocks.COMPOSTER, Blocks.CRAFTING_TABLE, Blocks.CHEST);
            case HOUSE -> List.of(Blocks.LOOM, Blocks.CRAFTING_TABLE, Blocks.BOOKSHELF, Blocks.CHEST);
            case INN -> List.of(Blocks.SMOKER, Blocks.BREWING_STAND, Blocks.CAKE, Blocks.CHEST);
            case WAREHOUSE -> List.of(
                    Blocks.CRAFTING_TABLE, Blocks.LOOM, Blocks.STONECUTTER, Blocks.CHEST, Blocks.CHEST);
            case SMITHY -> List.of(
                    Blocks.ANVIL,
                    Blocks.BLAST_FURNACE,
                    Blocks.SMITHING_TABLE,
                    Blocks.GRINDSTONE,
                    Blocks.CAULDRON);
            case GRANARY -> List.of(
                    Blocks.COMPOSTER, Blocks.SMOKER, Blocks.HAY_BLOCK, Blocks.HAY_BLOCK, Blocks.CHEST);
            case GUARD_POST -> List.of(Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE, Blocks.CHEST);
            case EXCHANGE_HALL -> List.of(
                    BankerProfessionSupport.exchangeDeskOrLectern(),
                    Blocks.BOOKSHELF,
                    Blocks.ENDER_CHEST,
                    Blocks.BELL);
            default -> List.of(Blocks.CRAFTING_TABLE);
        };
        for (Block block : blocks) {
            placeFirstOpenInterior(canvas, width, depth, block.defaultBlockState());
        }
    }

    private static void addTypeExterior(
            Canvas canvas,
            Materials materials,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth,
            int height) {
        // Place service architecture opposite the optional annex so both silhouettes stay clear.
        int sideX = recipe.mirrored() ? width : -1;
        int centerZ = depth / 2;
        if (type == VillageProsperityEngine.ProjectType.WAREHOUSE) {
            // A deep loading hood and clear service platform distinguish storage at a glance.
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                canvas.put(sideX, 0, z, materials.foundation.defaultBlockState());
                canvas.put(sideX, 3, z, materials.roofSlab.defaultBlockState());
            }
            for (int z : new int[] {centerZ - 2, centerZ + 2}) {
                canvas.put(sideX, 1, z, materials.timber.defaultBlockState());
                canvas.put(sideX, 2, z, materials.timber.defaultBlockState());
            }
            canvas.put(
                    sideX,
                    2,
                    centerZ,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        } else if (type == VillageProsperityEngine.ProjectType.SMITHY) {
            Block chimney = materials.dialect == VillageArchitecture.BiomeDialect.DESERT
                    ? Blocks.CUT_SANDSTONE
                    : Blocks.BRICKS;
            int chimneyZ = Math.max(3, depth - 3);
            canvas.put(sideX, 0, chimneyZ, materials.foundation.defaultBlockState());
            for (int y = 1; y <= Math.min(height - 1, 7); y++) {
                // A roof eave may cross the stack; keeping that authored roof cell makes the
                // chimney read as passing cleanly through it.
                canvas.putIfAbsent(sideX, y, chimneyZ, chimney.defaultBlockState());
            }
        } else if (type == VillageProsperityEngine.ProjectType.GRANARY) {
            // A projecting grain chute with hay shoulders gives this trade building its own
            // silhouette rather than relying on hidden workstation differences.
            canvas.put(sideX, 0, centerZ, materials.foundation.defaultBlockState());
            for (int y = 1; y <= 4; y++) {
                canvas.put(sideX, y, centerZ, materials.timber.defaultBlockState());
            }
            for (int z : new int[] {centerZ - 1, centerZ + 1}) {
                canvas.put(sideX, 0, z, materials.foundation.defaultBlockState());
                canvas.put(sideX, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
                canvas.put(sideX, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
            }
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                if (z != centerZ) {
                    canvas.put(sideX, 4, z, materials.roofSlab.defaultBlockState());
                }
            }
        }
    }

    private static void addBeds(Canvas canvas, int width, int depth, int count) {
        BlockState foot = Blocks.BED.white().defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH);
        BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
        int placed = 0;
        for (int z = 2; z < depth - 2 && placed < count; z += 2) {
            for (int x = 1; x < width - 1 && placed < count; x += 2) {
                if (canvas.openFloor(x, z) && canvas.openFloor(x, z + 1)) {
                    canvas.put(x, 1, z, foot);
                    canvas.put(x, 1, z + 1, head);
                    placed++;
                }
            }
        }
    }

    private static void placeFirstOpenInterior(
            Canvas canvas, int width, int depth, BlockState state) {
        for (int z = depth - 2; z >= 2; z--) {
            for (int x = 1; x < width - 1; x++) {
                if (canvas.openFloor(x, z)) {
                    canvas.put(x, 1, z, state);
                    return;
                }
            }
        }
    }

    private static void appendGuardWatchDeck(
            Canvas canvas, Materials materials, int width, int depth, int height) {
        int minX = 2;
        int maxX = width - 3;
        int minZ = 2;
        int maxZ = depth - 3;
        int deckY = canvas.highestOverall() + 1;
        if (deckY < 2 || deckY + 1 > height) {
            return;
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {minZ, maxZ}) {
                int columnTop = canvas.highest(x, z);
                for (int y = columnTop + 1; y < deckY; y++) {
                    canvas.put(x, y, z, materials.timber.defaultBlockState());
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                canvas.put(x, deckY, z, materials.foundation.defaultBlockState());
            }
        }
        Block parapet = materials.dialect == VillageArchitecture.BiomeDialect.DESERT
                ? Blocks.SANDSTONE_WALL
                : Blocks.STONE_BRICK_WALL;
        for (int x = minX; x <= maxX; x += 2) {
            canvas.put(x, deckY + 1, minZ, parapet.defaultBlockState());
            canvas.put(x, deckY + 1, maxZ, parapet.defaultBlockState());
        }
        for (int z = minZ + 2; z < maxZ; z += 2) {
            canvas.put(minX, deckY + 1, z, parapet.defaultBlockState());
            canvas.put(maxX, deckY + 1, z, parapet.defaultBlockState());
        }
    }

    private static void buildMarket(
            Canvas canvas,
            Materials materials,
            VillageArchitecture.Character character,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth) {
        int pavingOffset = Math.floorMod(
                (int) VillageArchitecture.mix64(recipe.seed()), 4);
        Block pavingAccent = materials.accent == materials.foundation
                ? (materials.dialect == VillageArchitecture.BiomeDialect.DESERT
                        ? Blocks.CHISELED_SANDSTONE
                        : materials.wall)
                : materials.accent;
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                Block floor = Math.floorMod(x + z + pavingOffset, 4) == 0
                        ? pavingAccent
                        : materials.foundation;
                canvas.put(x, 0, z, floor.defaultBlockState());
            }
        }
        // Keep the north/south arrival aisle clear in every layout. The market should read as a
        // planned public place rather than a random field of kiosks.
        int[][] centers = switch (recipe.silhouette()) {
            case 1 -> new int[][] {{3, 3}, {9, 3}, {3, 6}, {9, 6}, {3, 9}, {9, 9}};
            case 2 -> new int[][] {{3, 3}, {9, 3}, {3, 9}, {9, 9}};
            default -> new int[][] {{3, 3}, {9, 3}, {3, 9}, {9, 9}};
        };
        for (int[] center : centers) {
            addMarketStall(canvas, materials, center[0], center[1], recipe.roof());
        }
        Block[] trades = {Blocks.COMPOSTER, Blocks.LOOM, Blocks.FLETCHING_TABLE};
        for (int index = 0; index < trades.length; index++) {
            int[] center = centers[index];
            canvas.replace(center[0], 1, center[1], trades[index].defaultBlockState());
        }
        addMarketBell(canvas, materials, recipe.silhouette(), width, depth);
        addMarketFrontage(canvas, materials, recipe.frontage(), width);
        addMarketCharacter(canvas, materials, character, width, depth);
        addMirroredSideMarker(canvas, materials, recipe, width);
        canvas.put(width / 2, 0, 0, materials.accent.defaultBlockState());
        canvas.put(width / 2, 0, -1, materials.entryStairs.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH));
    }

    private static void addMarketStall(
            Canvas canvas, Materials materials, int centerX, int centerZ, int roofFamily) {
        for (int x : new int[] {centerX - 1, centerX + 1}) {
            for (int z : new int[] {centerZ - 1, centerZ + 1}) {
                canvas.put(x, 1, z, materials.fence.defaultBlockState());
                canvas.put(x, 2, z, materials.fence.defaultBlockState());
            }
        }
        if (roofFamily == 2) {
            // A compact hipped canopy: a directional stair ring rises to a visible center cap.
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                canvas.put(x, 3, centerZ - 1, materials.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
                canvas.put(x, 3, centerZ + 1, materials.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH));
            }
            canvas.put(centerX - 1, 3, centerZ, materials.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            canvas.put(centerX + 1, 3, centerZ, materials.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
            canvas.put(centerX, 3, centerZ, materials.roofSlab.defaultBlockState());
            canvas.put(centerX, 4, centerZ, materials.accent.defaultBlockState());
        } else {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                    canvas.put(x, 3, z, materials.roofSlab.defaultBlockState());
                }
            }
        }
        if (roofFamily == 1) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                canvas.put(centerX, 4, z, materials.roofSlab.defaultBlockState());
            }
        }
        canvas.put(
                centerX,
                2,
                centerZ,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        canvas.put(centerX, 1, centerZ, (roofFamily == 1 ? Blocks.CHEST : Blocks.HAY_BLOCK)
                .defaultBlockState());
    }

    private static void addMarketBell(
            Canvas canvas, Materials materials, int silhouette, int width, int depth) {
        int center = width / 2;
        if (silhouette == 2) {
            // An open bell pavilion distinguishes the civic layout without blocking the two-high
            // pedestrian corridor through its center.
            for (int x : new int[] {center - 1, center + 1}) {
                for (int z : new int[] {depth / 2 - 1, depth / 2 + 1}) {
                    canvas.put(x, 1, z, materials.fence.defaultBlockState());
                    canvas.put(x, 2, z, materials.fence.defaultBlockState());
                    canvas.put(x, 3, z, materials.fence.defaultBlockState());
                }
            }
            for (int x = center - 1; x <= center + 1; x++) {
                for (int z = depth / 2 - 1; z <= depth / 2 + 1; z++) {
                    canvas.put(x, 4, z, materials.roofSlab.defaultBlockState());
                }
            }
            canvas.put(
                    center,
                    3,
                    depth / 2,
                    Blocks.BELL.defaultBlockState()
                            .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
            return;
        }
        int bellX = silhouette == 1 ? width - 2 : center;
        int bellZ = silhouette == 1 ? depth / 2 : depth - 2;
        canvas.put(bellX, 1, bellZ, materials.accent.defaultBlockState());
        canvas.put(bellX, 2, bellZ, Blocks.BELL.defaultBlockState());
    }

    private static void addMarketFrontage(
            Canvas canvas, Materials materials, int frontage, int width) {
        int center = width / 2;
        if (frontage == 0) {
            for (int x : new int[] {2, width - 3}) {
                canvas.put(x, 0, 0, materials.foundation.defaultBlockState());
                canvas.put(x, 1, 0, materials.fence.defaultBlockState());
                canvas.put(x, 2, 0, Blocks.LANTERN.defaultBlockState());
            }
            return;
        }
        int reach = frontage == 1 ? 2 : 3;
        for (int x : new int[] {center - reach, center + reach}) {
            canvas.put(x, 0, 0, materials.foundation.defaultBlockState());
            canvas.put(x, 1, 0, materials.fence.defaultBlockState());
            canvas.put(x, 2, 0, materials.fence.defaultBlockState());
            if (frontage == 2) {
                canvas.put(x, 0, -1, materials.foundation.defaultBlockState());
                canvas.put(x, 1, -1, materials.fence.defaultBlockState());
                canvas.put(x, 2, -1, materials.fence.defaultBlockState());
            }
        }
        for (int x = center - reach; x <= center + reach; x++) {
            canvas.put(x, 3, 0, materials.roofSlab.defaultBlockState());
            if (frontage == 2) {
                canvas.put(x, 3, -1, materials.roofSlab.defaultBlockState());
            }
        }
        if (frontage == 1) {
            canvas.put(
                    center,
                    2,
                    0,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        } else {
            for (int x : new int[] {center - 2, center + 2}) {
                canvas.put(
                        x,
                        2,
                        0,
                        Blocks.LANTERN.defaultBlockState()
                                .setValue(LanternBlock.HANGING, true));
            }
        }
    }

    private static void addMarketCharacter(
            Canvas canvas,
            Materials materials,
            VillageArchitecture.Character character,
            int width,
            int depth) {
        switch (character) {
            case AGRARIAN -> {
                for (int x : new int[] {1, width - 2}) {
                    canvas.replace(x, 0, 1, materials.accent.defaultBlockState());
                    canvas.put(x, 1, 1, Blocks.POTTED_DANDELION.defaultBlockState());
                }
            }
            case MERCANTILE -> {
                for (int x = 2; x <= 4; x++) {
                    canvas.replace(x, 3, 2, Blocks.WOOL.green().defaultBlockState());
                }
            }
            case RUSTIC -> {
                for (int z : new int[] {2, depth - 3}) {
                    canvas.put(1, 1, z, materials.timber.defaultBlockState());
                    canvas.put(width - 2, 1, z, materials.timber.defaultBlockState());
                }
            }
            case FORMAL -> {
                for (int x : new int[] {width / 2 - 1, width / 2 + 1}) {
                    canvas.put(x, 1, depth / 2, materials.accent.defaultBlockState());
                }
            }
        }
    }

    private static void addMirroredSideMarker(
            Canvas canvas,
            Materials materials,
            VillageArchitecture.Recipe recipe,
            int width) {
        int x = recipe.mirrored() ? width - 1 : 0;
        int z = 1;
        canvas.put(x, 0, z, materials.accent.defaultBlockState());
        canvas.put(x, 1, z, materials.fence.defaultBlockState());
        canvas.put(x, 2, z, Blocks.LANTERN.defaultBlockState());
    }

    private static void buildMine(
            Canvas canvas,
            Materials materials,
            VillageArchitecture.Character character,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth) {
        int center = width / 2;
        Block mineStone = switch (materials.dialect) {
            case DESERT -> Blocks.CUT_SANDSTONE;
            case SAVANNA -> Blocks.STONE_BRICKS;
            case TAIGA -> Blocks.COBBLESTONE;
            case SNOWY -> Blocks.STONE_BRICKS;
            case PLAINS -> Blocks.MOSSY_COBBLESTONE;
        };
        for (int z = 0; z < depth; z++) {
            for (int x = center - 2; x <= center + 2; x++) {
                // Cobble is the worn interior track bed; the exterior headhouse follows dialect.
                canvas.put(x, 0, z, Blocks.COBBLESTONE.defaultBlockState());
            }
            if (z < depth - 1) {
                canvas.put(center, 1, z, Blocks.RAIL.defaultBlockState());
            }
        }
        addMineHeadhouse(canvas, mineStone, center, depth);
        for (int z = 1; z < depth - 1; z += 2) {
            for (int x : new int[] {center - 2, center + 2}) {
                canvas.putIfAbsent(x, 1, z, materials.timber.defaultBlockState());
                canvas.putIfAbsent(x, 2, z, materials.timber.defaultBlockState());
                canvas.putIfAbsent(x, 3, z, materials.timber.defaultBlockState());
            }
            for (int x = center - 2; x <= center + 2; x++) {
                canvas.putIfAbsent(x, 4, z, mineStone.defaultBlockState());
            }
        }
        int shedX = recipe.mirrored() ? 1 : width - 4;
        addJoinedMass(canvas, materials, new Mass(shedX, 3, 3, 5, 3), recipe.roof());
        openMineShed(canvas, recipe.mirrored(), shedX, 5);
        if (recipe.silhouette() == 1) {
            int secondShedX = recipe.mirrored() ? width - 4 : 1;
            addJoinedMass(
                    canvas,
                    materials,
                    new Mass(secondShedX, 5, 3, 3, 4),
                    (recipe.roof() + 1) % 3);
            openMineShed(canvas, !recipe.mirrored(), secondShedX, 6);
        } else if (recipe.silhouette() == 2) {
            for (int x : new int[] {center - 3, center + 3}) {
                for (int y = 1; y <= 4; y++) {
                    canvas.putIfAbsent(x, y, 2, mineStone.defaultBlockState());
                }
            }
            for (int x = center - 3; x <= center + 3; x++) {
                canvas.putIfAbsent(x, 5, 2, materials.roofSlab.defaultBlockState());
            }
        }
        addMineFrontage(canvas, materials, recipe.frontage(), center, width);
        if (character == VillageArchitecture.Character.MERCANTILE) {
            int awningX = recipe.mirrored() ? 0 : width - 1;
            for (int z = 4; z <= 6; z++) {
                canvas.put(awningX, 3, z, Blocks.WOOL.green().defaultBlockState());
            }
        } else if (character == VillageArchitecture.Character.FORMAL) {
            canvas.put(center - 3, 1, 1, materials.accent.defaultBlockState());
            canvas.put(center + 3, 1, 1, materials.accent.defaultBlockState());
        }
        canvas.put(center, 0, -1, materials.entryStairs.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH));
        placeFirstOpenInterior(canvas, width, depth, Blocks.STONECUTTER.defaultBlockState());
        placeFirstOpenInterior(canvas, width, depth, Blocks.BLAST_FURNACE.defaultBlockState());
        placeFirstOpenInterior(canvas, width, depth, Blocks.CHEST.defaultBlockState());
    }

    private static void openMineShed(
            Canvas canvas, boolean shedOnLeft, int shedX, int doorwayZ) {
        int corridorFacingX = shedOnLeft ? shedX + 2 : shedX;
        canvas.remove(corridorFacingX, 1, doorwayZ);
        canvas.remove(corridorFacingX, 2, doorwayZ);
    }

    private static void addMineHeadhouse(
            Canvas canvas, Block mineStone, int center, int depth) {
        for (int z : new int[] {depth - 2, depth - 1}) {
            for (int x = center - 3; x <= center + 3; x++) {
                int top = 5 - Math.abs(center - x) / 2;
                for (int y = 1; y <= top; y++) {
                    boolean railOpening = z == depth - 2
                            && Math.abs(center - x) <= 1
                            && y <= 3;
                    if (!railOpening) {
                        Block face = z == depth - 1
                                        && Math.abs(center - x) <= 1
                                        && y <= 3
                                ? Blocks.DEEPSLATE
                                : mineStone;
                        canvas.put(x, y, z, face.defaultBlockState());
                    }
                }
            }
        }
        for (int x = center - 1; x <= center + 1; x++) {
            canvas.put(x, 4, depth - 2, mineStone.defaultBlockState());
        }
    }

    private static void addMineFrontage(
            Canvas canvas, Materials materials, int frontage, int center, int width) {
        if (frontage == 0) {
            for (int x : new int[] {center - 2, center + 2}) {
                canvas.replace(x, 0, 0, materials.foundation.defaultBlockState());
                canvas.put(x, 1, 0, materials.fence.defaultBlockState());
                canvas.put(x, 2, 0, Blocks.LANTERN.defaultBlockState());
            }
            return;
        }
        int reach = frontage == 1 ? 2 : 3;
        Block upright = frontage == 1 ? materials.timber : materials.accent;
        for (int x : new int[] {center - reach, center + reach}) {
            if (frontage == 1) {
                canvas.replace(x, 0, 0, materials.foundation.defaultBlockState());
            } else {
                canvas.put(x, 0, 0, materials.foundation.defaultBlockState());
            }
            for (int y = 1; y <= 3; y++) {
                canvas.put(x, y, 0, upright.defaultBlockState());
            }
        }
        for (int x = center - reach; x <= center + reach; x++) {
            canvas.put(x, 4, 0, materials.roofSlab.defaultBlockState());
        }
        for (int x : frontage == 1
                ? new int[] {center - 1, center + 1}
                : new int[] {center - 2, center + 2}) {
            canvas.put(
                    x,
                    3,
                    0,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    private static void appendWorkyard(
            Canvas stage,
            Materials materials,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth) {
        int x = recipe.mirrored() ? width - 1 : 0;
        for (int z = 2; z <= depth - 3; z++) {
            stage.put(x, 0, z, materials.foundation.defaultBlockState());
            stage.put(x, 3, z, materials.roofSlab.defaultBlockState());
        }
        for (int z : new int[] {2, depth - 3}) {
            stage.put(x, 1, z, materials.fence.defaultBlockState());
            stage.put(x, 2, z, materials.fence.defaultBlockState());
        }
        stage.replace(x, 2, 2, Blocks.LANTERN.defaultBlockState());
        Block feature = switch (type) {
            case COTTAGE, HOUSE, INN -> Blocks.FLOWER_POT;
            case WAREHOUSE -> Blocks.CHEST;
            case MINE_ENTRANCE, SMITHY -> Blocks.CAULDRON;
            case MARKET_SQUARE -> Blocks.HAY_BLOCK;
            case GRANARY -> Blocks.COMPOSTER;
            case GUARD_POST -> Blocks.IRON_BARS;
            case EXCHANGE_HALL -> Blocks.BOOKSHELF;
        };
        stage.put(x, 1, Math.max(3, depth / 2), feature.defaultBlockState());
        if (stage.values().isEmpty()) {
            stage.put(width / 2, 0, depth - 1, materials.accent.defaultBlockState());
            stage.put(width / 2, 1, depth - 1, Blocks.POTTED_FERN.defaultBlockState());
        }
    }

    private static void appendLandmark(
            Canvas stage,
            Canvas base,
            Materials materials,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth,
            int height) {
        int landmarkStart = stage.values().size();
        switch (type) {
            case COTTAGE, HOUSE, INN, SMITHY -> appendChimney(
                    stage, base, materials, recipe, width, depth, height);
            case WAREHOUSE -> appendWarehouseHoist(
                    stage, base, materials, recipe, width, depth, height);
            case MINE_ENTRANCE -> appendMineHeadframe(
                    stage, base, materials, width, depth, height);
            case MARKET_SQUARE -> appendMarketStandard(
                    stage, base, materials, recipe, width, depth, height);
            case GRANARY -> appendGranaryVent(
                    stage, base, materials, width, depth, height);
            case GUARD_POST -> appendGuardBeacon(
                    stage, base, materials, width, depth, height);
            case EXCHANGE_HALL -> appendExchangeCupola(
                    stage, base, materials, width, depth, height);
        }
        if (stage.values().size() == landmarkStart) {
            throw new IllegalStateException(
                    "Modular stage-two landmark could not be authored for "
                            + materials.dialect + " " + type + " recipe " + recipe.signature());
        }
        int rearX = width / 2;
        int rearZ = depth - 1;
        stage.put(rearX, 0, rearZ, materials.accent.defaultBlockState());
        stage.put(rearX, 1, rearZ, materials.fence.defaultBlockState());
        stage.put(rearX, 2, rearZ, Blocks.LANTERN.defaultBlockState());
        if (stage.values().isEmpty()) {
            stage.put(0, 0, 0, materials.accent.defaultBlockState());
        }
    }

    private static void appendChimney(
            Canvas stage,
            Canvas base,
            Materials materials,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth,
            int height) {
        int x = recipe.mirrored() ? 2 : width - 3;
        int z = Math.max(2, depth - 3);
        int highest = base.highest(x, z);
        Block chimney = materials.dialect == VillageArchitecture.BiomeDialect.DESERT
                ? Blocks.CUT_SANDSTONE
                : Blocks.BRICKS;
        appendColumn(stage, x, z, highest + 1, height, 2, chimney);
    }

    private static void appendWarehouseHoist(
            Canvas stage,
            Canvas base,
            Materials materials,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth,
            int height) {
        int x = recipe.mirrored() ? 2 : width - 3;
        int z = depth / 2;
        int start = base.highest(x, z) + 1;
        if (start < 1 || start > height) {
            return;
        }
        if (start + 1 > height) {
            stage.put(x, start, z, materials.fence.defaultBlockState());
            return;
        }
        stage.put(x, start, z, materials.timber.defaultBlockState());
        stage.put(x, start + 1, z, materials.timber.defaultBlockState());
        int arm = recipe.mirrored() ? 1 : -1;
        stage.put(x + arm, start + 1, z, materials.timber.defaultBlockState());
        stage.put(
                x + arm,
                start,
                z,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void appendMineHeadframe(
            Canvas stage,
            Canvas base,
            Materials materials,
            int width,
            int depth,
            int height) {
        int x = width / 2;
        int z = depth - 2;
        int start = base.highest(x, z) + 1;
        if (start < 1 || start > height) {
            return;
        }
        int available = height - start + 1;
        if (available == 1) {
            for (int dx = -1; dx <= 1; dx++) {
                stage.put(x + dx, start, z, materials.timber.defaultBlockState());
            }
            return;
        }
        if (available == 2) {
            stage.put(x, start, z, materials.timber.defaultBlockState());
            stage.put(x, start + 1, z, materials.timber.defaultBlockState());
            stage.put(x - 1, start + 1, z, materials.timber.defaultBlockState());
            stage.put(
                    x - 1,
                    start,
                    z,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            return;
        }
        for (int y = start; y <= start + 2; y++) {
            stage.put(x, y, z, materials.timber.defaultBlockState());
        }
        stage.put(x - 1, start + 2, z, materials.timber.defaultBlockState());
        stage.put(x + 1, start + 2, z, materials.timber.defaultBlockState());
        stage.put(
                x - 1,
                start + 1,
                z,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void appendMarketStandard(
            Canvas stage,
            Canvas base,
            Materials materials,
            VillageArchitecture.Recipe recipe,
            int width,
            int depth,
            int height) {
        int x = switch (recipe.silhouette()) {
            case 1 -> width - 2;
            default -> width / 2;
        };
        int z = switch (recipe.silhouette()) {
            case 0 -> depth - 2;
            case 1 -> depth / 2;
            default -> depth / 2;
        };
        int start = base.highest(x, z) + 1;
        if (start < 1 || start > height) {
            return;
        }
        int available = height - start + 1;
        if (available == 1) {
            stage.put(x, start, z, Blocks.LANTERN.defaultBlockState());
            return;
        }
        stage.put(x, start, z, materials.fence.defaultBlockState());
        if (available >= 3) {
            stage.put(x, start + 1, z, materials.fence.defaultBlockState());
            stage.put(x, start + 2, z, Blocks.LANTERN.defaultBlockState());
        } else {
            stage.put(x, start + 1, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    private static void appendGranaryVent(
            Canvas stage,
            Canvas base,
            Materials materials,
            int width,
            int depth,
            int height) {
        int x = width / 2;
        int z = Math.max(2, depth - 3);
        int start = base.highest(x, z) + 1;
        if (start < 1 || start > height) {
            return;
        }
        stage.put(x, start, z, materials.fence.defaultBlockState());
        if (start + 1 > height) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            stage.put(x + dx, start + 1, z, materials.roofSlab.defaultBlockState());
        }
        stage.put(x, start + 1, z - 1, materials.roofSlab.defaultBlockState());
        stage.put(x, start + 1, z + 1, materials.roofSlab.defaultBlockState());
    }

    private static void appendGuardBeacon(
            Canvas stage,
            Canvas base,
            Materials materials,
            int width,
            int depth,
            int height) {
        int x = width / 2;
        int z = depth / 2;
        int start = base.highest(x, z) + 1;
        if (start < 1 || start > height) {
            return;
        }
        int available = height - start + 1;
        if (available == 1) {
            stage.put(x, start, z, Blocks.LANTERN.defaultBlockState());
            return;
        }
        stage.put(x, start, z, materials.accent.defaultBlockState());
        if (available >= 3) {
            stage.put(x, start + 1, z, materials.fence.defaultBlockState());
            stage.put(x, start + 2, z, Blocks.LANTERN.defaultBlockState());
        } else {
            stage.put(x, start + 1, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    private static void appendExchangeCupola(
            Canvas stage,
            Canvas base,
            Materials materials,
            int width,
            int depth,
            int height) {
        int x = width / 2;
        int z = depth / 2;
        int start = base.highest(x, z) + 1;
        if (start < 1 || start > height) {
            return;
        }
        int available = height - start + 1;
        if (available == 1) {
            stage.put(x, start, z, Blocks.BELL.defaultBlockState());
            return;
        }
        if (available >= 3) {
            for (int dx = -1; dx <= 1; dx++) {
                stage.put(x + dx, start, z, materials.accent.defaultBlockState());
            }
        } else {
            stage.put(x, start, z, materials.accent.defaultBlockState());
        }
        if (available >= 3) {
            stage.put(x, start + 1, z, materials.fence.defaultBlockState());
            stage.put(x, start + 2, z, Blocks.LANTERN.defaultBlockState());
        } else {
            stage.put(x, start + 1, z, Blocks.BELL.defaultBlockState());
        }
    }

    private static void appendColumn(
            Canvas stage, int x, int z, int start, int height, int count, Block block) {
        if (start < 1) {
            return;
        }
        for (int offset = 0; offset < count && start + offset <= height; offset++) {
            stage.put(x, start + offset, z, block.defaultBlockState());
        }
    }

    record Cell(int x, int y, int z, BlockState state) {
    }

    record Layers(
            List<Cell> base,
            List<Cell> stageOne,
            List<Cell> stageTwo,
            Materials materials) {
    }

    record Materials(
            VillageArchitecture.BiomeDialect dialect,
            Block foundation,
            Block wall,
            Block timber,
            Block roofStairs,
            Block roofSlab,
            Block fence,
            Block accent,
            Block door,
            Block entryStairs) {
        private Materials withAccent(Block replacement) {
            return new Materials(
                    dialect,
                    foundation,
                    wall,
                    timber,
                    roofStairs,
                    roofSlab,
                    fence,
                    replacement,
                    door,
                    entryStairs);
        }

        private Materials withFoundation(Block replacement) {
            return new Materials(
                    dialect,
                    replacement,
                    wall,
                    timber,
                    roofStairs,
                    roofSlab,
                    fence,
                    accent,
                    door,
                    entryStairs);
        }
    }

    private record Mass(int x, int z, int width, int depth, int wallHeight) {
    }

    private static final class Canvas {
        private final Set<BlockPos> blocked;
        private final LinkedHashMap<BlockPos, Cell> cells = new LinkedHashMap<>();

        private Canvas(Set<BlockPos> blocked) {
            this.blocked = blocked == null ? Set.of() : Set.copyOf(blocked);
        }

        private void put(int x, int y, int z, BlockState state) {
            BlockPos position = new BlockPos(x, y, z);
            if (blocked.contains(position)) {
                return;
            }
            Cell previous = cells.get(position);
            if (previous != null && !previous.state().equals(state)) {
                throw new IllegalStateException(
                        "Unacknowledged modular-plan collision at "
                                + position
                                + ": "
                                + previous.state()
                                + " -> "
                                + state);
            }
            cells.putIfAbsent(position, new Cell(x, y, z, state));
        }

        private void replace(int x, int y, int z, BlockState state) {
            BlockPos position = new BlockPos(x, y, z);
            if (blocked.contains(position) || !cells.containsKey(position)) {
                throw new IllegalStateException(
                        "Modular plan tried to replace a missing cell at " + position);
            }
            cells.put(position, new Cell(x, y, z, state));
        }

        private void mergeIfAbsent(Canvas addition) {
            for (Cell cell : addition.values()) {
                putIfAbsent(cell.x(), cell.y(), cell.z(), cell.state());
            }
        }

        private void putIfAbsent(int x, int y, int z, BlockState state) {
            BlockPos position = new BlockPos(x, y, z);
            if (!blocked.contains(position)) {
                cells.putIfAbsent(position, new Cell(x, y, z, state));
            }
        }

        private void remove(int x, int y, int z) {
            cells.remove(new BlockPos(x, y, z));
        }

        private boolean openFloor(int x, int z) {
            return cells.containsKey(new BlockPos(x, 0, z))
                    && !blocked.contains(new BlockPos(x, 1, z))
                    && !cells.containsKey(new BlockPos(x, 1, z))
                    && !cells.containsKey(new BlockPos(x, 2, z));
        }

        private int highest(int x, int z) {
            int highest = -1;
            for (BlockPos position : cells.keySet()) {
                if (position.getX() == x && position.getZ() == z) {
                    highest = Math.max(highest, position.getY());
                }
            }
            return highest;
        }

        private int highestOverall() {
            int highest = -1;
            for (BlockPos position : cells.keySet()) {
                highest = Math.max(highest, position.getY());
            }
            return highest;
        }

        private Set<BlockPos> positions() {
            return Set.copyOf(cells.keySet());
        }

        private List<Cell> values() {
            return List.copyOf(cells.values());
        }
    }
}
