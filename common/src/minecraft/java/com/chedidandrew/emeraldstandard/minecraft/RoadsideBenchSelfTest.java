package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.SitePreparationPlan;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Uses real roadside plans on the construction fixture's flat patch, without editing its terrain. */
final class RoadsideBenchSelfTest {
    /** Standalone disposable-server fixture for checking roadside and Bank terrace seats together. */
    static void verify(ServerLevel level) throws Exception {
        BlockPos origin = new BlockPos(2300, level.getMaxY() - 35, 2300);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) {
                level.getChunk(origin.offset(x, 0, z));
                for (int y = -3; y <= 3; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    before.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, (y < -1 ? Blocks.STONE
                            : y == -1 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), 18);
                }
            }
            verify(level, origin, new UUID(0, 41));
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, 18));
        }
    }

    static void verify(ServerLevel level, BlockPos origin, UUID village) throws Exception {
        int seats = 0, gardens = 0;
        // Banks use an eight-cell approach; project streets can run longer.
        for (int length : new int[] {8, 14})
            for (Direction travel : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
                for (int side : new int[] {1, -1}) for (long project = 0; project < 3; project++) {
                    List<BlockPos> route = new ArrayList<>();
                    for (int i = 0; i < length; i++) route.add(origin.relative(travel, i));
                    var road = VillageTerrainFinishing.road(level, route, origin.getY(), Set.of(),
                            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
                            village, project);
                    require(road != null, "Flat road must be available");
                    BlockPos junction = route.get(length / 2);
                    int sx = -travel.getStepZ() * side, sz = travel.getStepX() * side;
                    BlockPos center = junction.offset(sx * 3, 0, sz * 3);
                    // Occupy the preferred pocket's center to exercise the actual fallback-side branch.
                    Set<Long> reserved = side == 1 ? Set.of()
                            : Set.of(junction.offset(-sx * 3, 0, -sz * 3).asLong());
                    var pocket = VillageTerrainFinishing.pocket(level, route, Set.of(), reserved, road, village, project);
                    require(!pocket.isEmpty(), "Expected pocket on " + travel + "/" + side);
                    require(pocket.equals(VillageTerrainFinishing.pocket(
                            level, route, Set.of(), reserved, road, village, project)), "Stable pocket recipe");
                    int style = VillageNeighborhoodPlan.pocketStyle(village, project);
                    require(pocket.get(center.asLong()).after().equals("minecraft:air"), "Center stays open");
                    require(pocket.get(center.below().asLong()).after().equals(
                            style == 0 ? "minecraft:stone_bricks" : "minecraft:gravel"), "Paving unchanged");
                    int count = 0;
                    for (var cell : pocket.values()) {
                        BlockPos pos = BlockPos.of(cell.position());
                        require(!reserved.contains(cell.position()), "Pocket crossed reserved space");
                        require(cell.before().equals(BlockStateParser.serialize(level.getBlockState(pos))),
                                "Planning changed live terrain");
                        BlockState state = VillageTerrainFinishing.state(level, cell.after());
                        if (!(state.getBlock() instanceof StairBlock)) continue;
                        count++;
                        int outward = (pos.getX() - center.getX()) * sx + (pos.getZ() - center.getZ()) * sz;
                        int along = (pos.getX() - center.getX()) * travel.getStepX()
                                + (pos.getZ() - center.getZ()) * travel.getStepZ();
                        require(outward == 2 && Math.abs(along) <= 1 && pos.getY() == origin.getY(),
                                "Three seats stay at the original outer edge");
                        require(state.is(Blocks.SPRUCE_STAIRS), "Original bench material");
                        Direction back = state.getValue(StairBlock.FACING);
                        require(back.getStepX() * sx + back.getStepZ() * sz == 1,
                                "Reported backwards bench: high back points toward the paved center");
                        require(occupied(state, back, .75) && !occupied(state, back.getOpposite(), .75)
                                && occupied(state, back.getOpposite(), .25),
                                "Native stair collision must expose a low seat toward the paving");
                        var floor = VillageTerrainFinishing.state(level, pocket.get(pos.below().asLong()).after());
                        require(floor.isFaceSturdy(EmptyBlockGetter.INSTANCE, pos.below(), Direction.UP),
                                "Seating keeps solid support");
                        require(VillageTerrainFinishing.satisfied(level, state, cell.after()), "Replay accepts correct seat");
                        require(!VillageTerrainFinishing.satisfied(level,
                                state.setValue(StairBlock.FACING, back.getOpposite()), cell.after()),
                                "Replay must not accept the backwards seat as complete");
                        seats++;
                    }
                    require(count == (style == 1 ? 0 : 3), "Only the two seating motifs have three stair seats");
                    if (style == 1) gardens++;
                    var frozen = new SitePreparationPlan(new ArrayList<>(pocket.values()));
                    require(SitePreparationPlan.decode(frozen.encode()).equals(frozen),
                            "Seat direction survives frozen-plan reload");
                    // Old plans remain byte-authoritative; do not silently turn player-modified furniture.
                    var legacyCells = frozen.cells().stream().map(cell -> {
                        BlockState state = VillageTerrainFinishing.state(level, cell.after());
                        return state.getBlock() instanceof StairBlock ? new SitePreparationPlan.Cell(
                                cell.position(), cell.before(), BlockStateParser.serialize(state.setValue(
                                        StairBlock.FACING, state.getValue(StairBlock.FACING).getOpposite()))) : cell;
                    }).toList();
                    var legacy = new SitePreparationPlan(legacyCells);
                    require(SitePreparationPlan.decode(legacy.encode()).equals(legacy), "Old frozen plans stay unchanged");
                    try (var claim = VillageDevelopmentProtection.register(context -> false)) {
                        require(VillageTerrainFinishing.pocket(level, route, Set.of(), reserved, road, village, project).isEmpty(),
                                "Claims still veto optional pockets");
                    }
                }
        require(seats == 96 && gardens == 16, "Complete bank/project, direction, side and style coverage");
        System.out.println("PASS roadside benches: 48 real pocket plans, 96 inward-opening native seats, "
                + "16 gardens, both road lengths, all directions/sides, reservations, claims and frozen reload");
    }

    private static boolean occupied(BlockState state, Direction side, double y) {
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs().stream()
                .anyMatch(box -> box.contains(.5 + side.getStepX() * .25, y, .5 + side.getStepZ() * .25));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
