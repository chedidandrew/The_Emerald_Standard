package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Revision-three furnishing: connected work sequences with clear approaches and warm light. */
final class AuthoredInteriorRefinements {
    private static final Set<String> ROOM_FINISH_TARGETS = Set.of(
            "cottage_courtyard_03", "cottage_bay_04", "cottage_orchardstead_06",
            "house_cross_01", "house_dormer_02", "house_splitwing_05", "house_towercourt_06",
            "inn_gallery_01", "inn_coachhouse_02", "inn_tavern_04",
            "warehouse_gabled_03", "warehouse_crane_02", "warehouse_basilica_05",
            "smithy_corner_04", "smithy_foundry_05");
    private static final Set<String> FROZEN_MASTERS = Set.of(
            "cottage_garden_02", "cottage_courtyard_03", "house_arcade_03",
            "house_towercourt_06", "inn_coachhouse_02", "inn_courtyard_05",
            "warehouse_basilica_05", "market_bazaar_05", "guard_bastion_02",
            "guard_gatehouse_04", "exchange_hall_01", "exchange_branch_03", "exchange_loggia_04");

    private AuthoredInteriorRefinements() { }

    static void apply(Builder b, Metadata m, Materials p, String id) {
        if (FROZEN_MASTERS.contains(id)) {
            if (ROOM_FINISH_TARGETS.contains(id)) {
                finishReviewedRooms(b, m, p, id);
            }
            return;
        }
        switch (m.type) {
            case SMITHY -> {
                workFloor(b, m, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILES);
                installWorkflow(b, m, p, Workshop.FORGE, 1);
            }
            case MINE_ENTRANCE -> {
                workFloor(b, m, Blocks.COBBLESTONE, Blocks.STONE_BRICKS);
                installWorkflow(b, m, p, Workshop.WINCH, 1);
                timberShoring(b, m, p);
            }
            case GRANARY -> {
                installWorkflow(b, m, p, Workshop.GRAIN, 1);
                // Raised storage bays must have their own work destination and light.
                if (id.equals("granary_stilt_04") || id.equals("granary_silocomplex_05")) {
                    for (int y = 3; y < m.height - 3; y++) {
                        if (installWorkflow(b, m, p, Workshop.GRAIN, y)) {
                            break;
                        }
                    }
                }
            }
            case MARKET_SQUARE -> enrichMarketCounters(b, m, p);
            case EXCHANGE_HALL -> {
                archiveWall(b, m, p);
                civicFloor(b, m, Blocks.DYED_TERRACOTTA.green(), Blocks.SMOOTH_STONE);
            }
            case GUARD_POST -> {
                archiveWall(b, m, p);
                civicFloor(b, m, Blocks.DYED_TERRACOTTA.red(), Blocks.STONE_BRICKS);
            }
            case INN -> {
                if (id.equals("inn_tavern_04") || id.equals("inn_gallery_01")) {
                    installWorkflow(b, m, p, Workshop.BAR, 1);
                }
            }
            case HOUSE -> {
                if (id.equals("house_splitwing_05")) {
                    gableWindow(b, m, p);
                }
            }
            default -> { }
        }
        if (ROOM_FINISH_TARGETS.contains(id)) {
            finishReviewedRooms(b, m, p, id);
        }
    }

    private enum Workshop { FORGE, WINCH, GRAIN, BAR }

    /** Second review: small, wall-backed work groups fit real rooms without a freestanding gantry. */
    private static void finishReviewedRooms(Builder b, Metadata m, Materials p, String id) {
        List<BlockPos> rooms = m.interiorSamples.stream()
                .sorted(Comparator.<BlockPos>comparingInt(pos -> pos.getY())
                        .thenComparingInt(pos -> pos.getZ()).thenComparingInt(pos -> pos.getX()))
                .toList();
        int rugs = 0;
        Set<BlockPos> matCells = new HashSet<>();
        for (BlockPos room : rooms) {
            if (rugs == 2) {
                break;
            }
            if (inlaidRoomMat(b, m, room,
                    m.type == ProjectType.WAREHOUSE || m.type == ProjectType.SMITHY, matCells)) {
                rugs++;
            }
        }
        int wanted = id.equals("cottage_bay_04") ? 1 : 2;
        int installed = 0;
        List<BlockPos> furnishings = new ArrayList<>();
        // Visit each sampled room before returning to an earlier one. This keeps both groups
        // from collecting in one back corner and also allows a genuine upper-level room.
        for (int round = 0; round < 2 && installed < wanted; round++) {
            for (BlockPos room : rooms) {
                if (installed == wanted) {
                    break;
                }
                List<BlockPos> candidates = new ArrayList<>();
                for (int z = 1; z < m.depth - 1; z++) {
                    for (int x = 1; x < m.width - 1; x++) {
                        candidates.add(new BlockPos(x, room.getY(), z));
                    }
                }
                candidates.sort(Comparator.<BlockPos>comparingInt(pos -> pos.distManhattan(room))
                        .thenComparingInt(pos -> pos.getZ()).thenComparingInt(pos -> pos.getX()));
                boolean placed = false;
                for (BlockPos origin : candidates) {
                    if (origin.distManhattan(room) > 7 || furnishings.stream().anyMatch(
                            prior -> prior.getY() == origin.getY() && prior.distManhattan(origin) < 5)) {
                        continue;
                    }
                    for (Direction facing : new Direction[] {
                            Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST}) {
                        Direction along = facing.getClockWise();
                        if (!wallBacked(b, origin.relative(facing.getOpposite()))
                                || !wallBacked(b, origin.relative(along).relative(facing.getOpposite()))) {
                            continue;
                        }
                        Map<BlockPos, BlockState> group = compactRoomGroup(m, p, id, installed, facing);
                        Map<BlockPos, BlockState> rotated = new LinkedHashMap<>();
                        group.forEach((local, state) -> rotated.put(origin.offset(
                                along.getStepX() * local.getX() + facing.getStepX() * local.getZ(),
                                local.getY(),
                                along.getStepZ() * local.getX() + facing.getStepZ() * local.getZ()), state));
                        if (placeScene(b, m, rotated)) {
                            furnishings.add(origin);
                            installed++;
                            placed = true;
                            break;
                        }
                    }
                    if (placed) {
                        break;
                    }
                }
            }
        }
    }

    private static boolean wallBacked(Builder b, BlockPos pos) {
        Cell wall = b.cellAt(pos);
        return wall != null && (wall.phase() == Phase.SHELL || wall.phase() == Phase.FRAME)
                && wall.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos);
    }

    private static Map<BlockPos, BlockState> compactRoomGroup(
            Metadata m, Materials p, String id, int groupIndex, Direction facing) {
        Map<BlockPos, BlockState> group = new LinkedHashMap<>();
        if (m.type == ProjectType.SMITHY) {
            // A compact metalworking bench sits against an existing workshop wall. The cap
            // bears on the tool rack, while both work surfaces remain reachable from the aisle.
            at(group, 0, 0, 0, Blocks.SMITHING_TABLE.defaultBlockState());
            at(group, 1, 0, 0, Blocks.POLISHED_ANDESITE.defaultBlockState());
            at(group, 0, 1, 0, Blocks.IRON_BARS.defaultBlockState());
            at(group, 1, 1, 0, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
            at(group, 0, 2, 0, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        } else if (m.type == ProjectType.WAREHOUSE) {
            at(group, 0, 0, 0, Blocks.BARREL.defaultBlockState());
            at(group, 1, 0, 0, Blocks.NOTE_BLOCK.defaultBlockState());
            at(group, 0, 1, 0, Blocks.NOTE_BLOCK.defaultBlockState());
            at(group, 1, 0, 1, Blocks.BARREL.defaultBlockState());
            at(group, 0, 2, 0, p.roofSlab().defaultBlockState());
        } else if (m.type == ProjectType.INN
                || id.equals("cottage_orchardstead_06")) {
            // A counter and an adjacent work surface leave their fronts open; nothing caps a chest.
            at(group, 0, 0, 0, Blocks.BARREL.defaultBlockState());
            at(group, 1, 0, 0, id.equals("cottage_orchardstead_06")
                    ? Blocks.CRAFTING_TABLE.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
            at(group, 0, 1, 0, id.equals("cottage_orchardstead_06")
                    ? Blocks.MELON.defaultBlockState() : Blocks.POTTED_FERN.defaultBlockState());
            at(group, 1, 1, 0, id.equals("cottage_orchardstead_06")
                    ? Blocks.POTTED_FERN.defaultBlockState() : Blocks.CAKE.defaultBlockState());
            if (groupIndex == 0) {
                at(group, 0, 0, 1, p.roofSlab().defaultBlockState());
            }
        } else {
            at(group, 0, 0, 0, Blocks.BOOKSHELF.defaultBlockState());
            at(group, 0, 1, 0, Blocks.POTTED_FERN.defaultBlockState());
            at(group, 1, 0, 0, p.roofStairs().defaultBlockState()
                    .setValue(StairBlock.FACING, facing.getOpposite()));
        }
        return group;
    }

    /** A complete shallow inlay zones a room without adding collision or covering access cells. */
    private static boolean inlaidRoomMat(
            Builder b, Metadata m, BlockPos room, boolean industrial, Set<BlockPos> claimed) {
        int cx = Math.max(3, Math.min(m.width - 4, room.getX()));
        int cz = Math.max(2, Math.min(m.depth - 3, room.getZ()));
        int floorY = room.getY() - 1;
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(cx + dx, floorY, cz + dz);
                Cell floor = b.cellAt(pos);
                Cell above = b.cellAt(pos.above());
                if (claimed.contains(pos) || floor == null || floor.phase() != Phase.FOUNDATION
                        || !floor.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos)
                        || (above != null && (above.phase() == Phase.SHELL || above.phase() == Phase.FRAME))) {
                    return false;
                }
                cells.add(pos);
            }
        }
        claimed.addAll(cells);
        for (BlockPos pos : cells) {
            boolean edge = Math.abs(pos.getX() - cx) == 2 || Math.abs(pos.getZ() - cz) == 1;
            Block material = industrial ? (edge ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE)
                    : (edge ? Blocks.DYED_TERRACOTTA.red() : Blocks.WOOL.white());
            floorAt(b, pos.getX(), pos.getY(), pos.getZ(), material);
        }
        return true;
    }

    /** A bounded search fits a complete authored cluster; never drop only half of a machine. */
    private static boolean installWorkflow(Builder b, Metadata m, Materials p, Workshop kind, int y) {
        int[] rows = {m.depth - 3, m.depth - 4, m.depth / 2 + 1, 3, 2};
        for (int z : rows) {
            for (int x = 1; x <= m.width - 6; x++) {
                Map<BlockPos, BlockState> scene = workflow(p, kind, x, y, z);
                if (placeScene(b, m, scene)) {
                    // A material apron unifies the station with the work aisle, without changing
                    // floor height or removing existing rails, carpets or navigation targets.
                    for (int dx = -1; dx <= 5; dx++) {
                        for (int dz = -2; dz <= 0; dz++) {
                            floorAt(b, x + dx, y - 1, z + dz,
                                    kind == Workshop.FORGE ? Blocks.POLISHED_ANDESITE
                                            : kind == Workshop.BAR ? Blocks.DARK_OAK_PLANKS
                                            : p.floor());
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<BlockPos, BlockState> workflow(
            Materials p, Workshop kind, int x, int y, int z) {
        Map<BlockPos, BlockState> scene = new LinkedHashMap<>();
        BlockState timber = p.timber().defaultBlockState();
        BlockState lintel = timber.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState shelf = p.roofStairs().defaultBlockState()
                .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.SOUTH);
        for (int dx : new int[] {0, 4}) {
            for (int dy = 0; dy <= 3; dy++) {
                at(scene, x + dx, y + dy, z, kind == Workshop.FORGE
                        ? Blocks.STONE_BRICKS.defaultBlockState() : timber);
            }
        }
        for (int dx = 1; dx <= 3; dx++) {
            at(scene, x + dx, y + 3, z, kind == Workshop.FORGE
                    ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : lintel);
        }
        if (kind == Workshop.FORGE) {
            at(scene, x + 1, y, z, Blocks.BLAST_FURNACE.defaultBlockState());
            at(scene, x + 2, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            at(scene, x + 3, y, z, Blocks.SMITHING_TABLE.defaultBlockState());
            at(scene, x + 1, y + 1, z, Blocks.IRON_BARS.defaultBlockState());
            at(scene, x + 2, y + 1, z, Blocks.IRON_BARS.defaultBlockState());
            at(scene, x + 3, y + 1, z, Blocks.LANTERN.defaultBlockState());
            at(scene, x + 1, y + 2, z, Blocks.IRON_CHAIN.defaultBlockState());
            at(scene, x + 2, y + 2, z, Blocks.IRON_CHAIN.defaultBlockState());
        } else if (kind == Workshop.WINCH) {
            at(scene, x + 1, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            at(scene, x + 2, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            at(scene, x + 3, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            at(scene, x + 1, y + 1, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            at(scene, x + 2, y + 1, z, lintel);
            at(scene, x + 3, y + 1, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            at(scene, x + 2, y + 2, z, Blocks.IRON_CHAIN.defaultBlockState());
            at(scene, x, y + 2, z - 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.SOUTH));
            at(scene, x, y + 3, z - 1, Blocks.LANTERN.defaultBlockState());
        } else if (kind == Workshop.GRAIN) {
            at(scene, x + 1, y, z, Blocks.COMPOSTER.defaultBlockState());
            at(scene, x + 2, y, z, Blocks.HAY_BLOCK.defaultBlockState());
            at(scene, x + 3, y, z, Blocks.BARREL.defaultBlockState());
            at(scene, x + 1, y + 1, z, p.roofStairs().defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
            at(scene, x + 2, y + 1, z, Blocks.HAY_BLOCK.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            at(scene, x + 3, y + 1, z, Blocks.LANTERN.defaultBlockState());
        } else {
            for (int dx = 1; dx <= 3; dx++) {
                at(scene, x + dx, y, z, Blocks.BARREL.defaultBlockState());
                at(scene, x + dx, y + 2, z, shelf);
                at(scene, x + dx, y + 3, z, dx == 2 ? Blocks.BOOKSHELF.defaultBlockState() : lintel);
            }
            at(scene, x + 1, y + 1, z, Blocks.POTTED_RED_TULIP.defaultBlockState());
            at(scene, x + 2, y + 1, z, Blocks.FLOWER_POT.defaultBlockState());
            at(scene, x + 3, y + 1, z, Blocks.LANTERN.defaultBlockState());
        }
        return scene;
    }

    private static void archiveWall(Builder b, Metadata m, Materials p) {
        int placed = 0;
        for (int z = m.depth - 2; z >= 2 && placed < 2; z--) {
            for (int x = 1; x < m.width - 4 && placed < 2; x++) {
                Cell back = b.cellAt(new BlockPos(x + 1, 2, z + 1));
                if (back == null || !back.state().isCollisionShapeFullBlock(
                        EmptyBlockGetter.INSTANCE, new BlockPos(x + 1, 2, z + 1))) {
                    continue;
                }
                Map<BlockPos, BlockState> scene = new LinkedHashMap<>();
                for (int dx = 0; dx < 3; dx++) {
                    at(scene, x + dx, 1, z, Blocks.BOOKSHELF.defaultBlockState());
                    at(scene, x + dx, 2, z, dx == 1 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState()
                            : Blocks.BOOKSHELF.defaultBlockState());
                    at(scene, x + dx, 3, z, p.roofSlab().defaultBlockState());
                }
                if (placeScene(b, m, scene)) {
                    placed++;
                    x += 3;
                }
            }
        }
    }

    private static void civicFloor(Builder b, Metadata m, Block inset, Block border) {
        for (int z = 2; z < m.depth - 2; z++) {
            for (int x = 2; x < m.width - 2; x++) {
                if (Math.abs(x - m.width / 2) <= 1) {
                    floorAt(b, x, 0, z, x == m.width / 2 ? inset : border);
                }
            }
        }
    }

    private static void workFloor(Builder b, Metadata m, Block tile, Block edge) {
        for (int z = 1; z < m.depth - 1; z++) {
            for (int x = 1; x < m.width - 1; x++) {
                if (x <= 4 || x >= m.width - 5 || z >= m.depth - 5) {
                    floorAt(b, x, 0, z, (x % 5 == 0 || z % 5 == 0) ? edge : tile);
                }
            }
        }
    }

    private static void floorAt(Builder b, int x, int y, int z, Block material) {
        BlockPos pos = new BlockPos(x, y, z);
        Cell floor = b.cellAt(pos);
        if (floor != null && floor.phase() == Phase.FOUNDATION
                && floor.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos)) {
            b.force(floor.phase(), x, y, z, material.defaultBlockState());
        }
    }

    private static void enrichMarketCounters(Builder b, Metadata m, Materials p) {
        // Dress existing merchandise bays: each gets a recognizable product and shallow shelf,
        // rather than adding yet another freestanding column to the public arcade.
        List<Cell> wares = b.values().stream().filter(cell -> cell.y() == 2
                && (cell.state().is(Blocks.HAY_BLOCK) || cell.state().is(Blocks.WOOL.green())
                    || cell.state().is(Blocks.WOOL.red()) || cell.state().is(Blocks.TARGET)
                    || cell.state().is(Blocks.RAW_IRON_BLOCK))).toList();
        for (Cell ware : wares) {
            int x = ware.x(), z = ware.z();
            Block companion = ware.state().is(Blocks.HAY_BLOCK) ? Blocks.MELON
                    : ware.state().is(Blocks.TARGET) ? Blocks.FLETCHING_TABLE
                    : ware.state().is(Blocks.RAW_IRON_BLOCK) ? Blocks.SMITHING_TABLE
                    : Blocks.LOOM;
            for (int side : new int[] {-1, 1}) {
                Map<BlockPos, BlockState> scene = new LinkedHashMap<>();
                at(scene, x + side, 1, z, p.roofSlab().defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.TOP));
                at(scene, x + side, 2, z, companion.defaultBlockState());
                if (placeScene(b, m, scene)) {
                    break;
                }
            }
            // A capped stack and a colored textile/produce sign uses the existing bay structure.
            if (!b.isOccupied(new BlockPos(x, 3, z)) && !m.reservedAir.contains(new BlockPos(x, 3, z))) {
                b.put(Phase.DECOR, x, 3, z, p.roofSlab().defaultBlockState());
            }
        }
    }

    private static void timberShoring(Builder b, Metadata m, Materials p) {
        int cx = m.width / 2;
        for (int z : new int[] {m.depth / 2, m.depth - 3}) {
            Map<BlockPos, BlockState> scene = new LinkedHashMap<>();
            for (int dx : new int[] {-2, 2}) {
                for (int y = 1; y <= 3; y++) {
                    at(scene, cx + dx, y, z, p.timber().defaultBlockState());
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                at(scene, cx + dx, 4, z, p.timber().defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
            at(scene, cx, 3, z, Blocks.LANTERN.defaultBlockState()
                    .setValue(BlockStateProperties.HANGING, true));
            placeScene(b, m, scene);
        }
    }

    private static void gableWindow(Builder b, Metadata m, Materials p) {
        int cx = m.width / 2;
        for (int y = 5; y <= 6; y++) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                Cell current = b.cellAt(new BlockPos(x, y, 0));
                if (current != null && (current.phase() == Phase.SHELL || current.phase() == Phase.OPENING)) {
                    b.force(Phase.OPENING, x, y, 0, x == cx ? Blocks.GLASS.defaultBlockState()
                            : p.timber().defaultBlockState());
                }
            }
        }
    }

    private static boolean placeScene(Builder b, Metadata m, Map<BlockPos, BlockState> scene) {
        if (scene.isEmpty()) {
            return false;
        }
        int minY = scene.keySet().stream().mapToInt(BlockPos::getY).min().orElse(1);
        for (Map.Entry<BlockPos, BlockState> entry : scene.entrySet()) {
            BlockPos pos = entry.getKey();
            if (b.isOccupied(pos) || m.reservedAir.contains(pos) || m.accessTargets.contains(pos)
                    || pos.getY() >= m.height || pos.getX() < 1 || pos.getX() >= m.width - 1
                    || pos.getZ() < 1 || pos.getZ() >= m.depth - 1) {
                return false;
            }
            if (pos.getY() == minY) {
                Cell support = b.cellAt(pos.below());
                if (support == null || !support.state().isFaceSturdy(
                        EmptyBlockGetter.INSTANCE, pos.below(), Direction.UP)) {
                    return false;
                }
            }
        }
        if (!AuthoredVillageStructures.solidPropsPreserveInteractionRoutes(b, m, scene.keySet())) {
            return false;
        }
        scene.forEach((pos, state) -> {
            // Posts, headers and solid machine carcasses are genuine load-bearing members.
            // Retain furniture/light provenance for the nonstructural contents they carry.
            Phase phase = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos)
                    || state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock
                    ? Phase.FRAME : Phase.FIXTURE;
            b.put(phase, pos.getX(), pos.getY(), pos.getZ(), state);
        });
        return true;
    }

    private static void at(Map<BlockPos, BlockState> scene, int x, int y, int z, BlockState state) {
        scene.put(new BlockPos(x, y, z), state);
    }
}
