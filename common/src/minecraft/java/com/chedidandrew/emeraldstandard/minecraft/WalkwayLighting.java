package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import static com.chedidandrew.emeraldstandard.minecraft.WalkwayLightingLedger.*;

/** Optional, one-shot, loaded-only public road lighting, separate from immutable building plans. */
final class WalkwayLighting {
    static final int SPACING = 10;
    private WalkwayLighting() {}

    static List<Integer> stations(List<BlockPos> route) {
        if (route.size() < 9 || route.size() > WalkwayConnections.MAX_LENGTH) return List.of();
        List<Integer> result = new ArrayList<>();
        for (int i = 6; i < route.size() - 4; i += SPACING) result.add(i);
        if (result.isEmpty()) result.add(route.size() / 2);
        return List.copyOf(result);
    }

    /** One candidate or two writes per second per dimension, regardless of city/project count. */
    static boolean acquire(ServerLevel level, long tick) {
        var ledger = get(level);
        if (ledger.lastWorkTick != Long.MIN_VALUE && tick >= ledger.lastWorkTick
                && tick - ledger.lastWorkTick < 20) return false;
        ledger.lastWorkTick = tick;
        return true;
    }

    static int advance(ServerLevel level, UUID village, long project, long origin,
            List<BlockPos> route, AuthoredVillageStructures.Materials materials,
            List<EconomyService.VillageProjectLot> lots, List<Long> banks, int allowance) {
        return advance(level,village,project,key(village,project,origin),route,materials,lots,banks,allowance);
    }

    static int advance(ServerLevel level, UUID village, long project, String key,
            List<BlockPos> route, AuthoredVillageStructures.Materials materials,
            List<EconomyService.VillageProjectLot> lots, List<Long> banks, int allowance) {
        if (allowance <= 0) return 0;
        var ledger = get(level);
        Job job = ledger.jobs.getOrDefault(key, Job.fresh());
        if (job.done()) return 0;
        List<Integer> stations = stations(route);
        if (job.pending().isEmpty()) {
            if (job.station() >= stations.size()) {
                ledger.record(key, new Job(job.station(), List.of(), 0, true)); return 0;
            }
            int index = stations.get(job.station());
            BlockPos center = surface(level, route.get(index));
            if (center == null) return 0;
            if (!road(level.getBlockState(center)) || !clear(level.getBlockState(center.above()))
                    || !clear(level.getBlockState(center.above(2)))) {
                ledger.record(key, job.next()); return 0;
            }
            // Existing yard lamps or player lighting already doing the job need no duplicate.
            if (level.getBrightness(LightLayer.BLOCK, center.above()) >= 8) {
                ledger.record(key, job.next()); return 0;
            }
            Direction tangent = tangent(route, index);
            Direction first = (job.station() & 1) == 0 ? tangent.getClockWise() : tangent.getCounterClockWise();
            boolean wait = false;
            for (Direction outward : List.of(first, first.getOpposite())) {
                BlockPos foot = surface(level, center.relative(outward, 3));
                if (foot == null) { wait = true; continue; }
                if (Math.abs(foot.getY() - center.getY()) > 1 || ledger.nearby(foot.above())) continue;
                foot = foot.above(); // Build onto the ground, never excavate or replace its surface.
                var plan = AuthoredVillageStructures.walkwayLamp(materials, outward.getOpposite());
                var search = preflight(level, village, project, foot, plan, route, lots, banks);
                if (search.waiting()) { wait = true; continue; }
                if (search.pieces().isEmpty()) continue;
                // Save both reservation and exact before/after states before the first block.
                ledger.reserve(foot);
                ledger.record(key, new Job(job.station(), search.pieces(), 0, false));
                return 0;
            }
            if (!wait) ledger.record(key, job.next());
            return 0;
        }
        return placePending(level, village, project, key, job, lots, banks, Math.min(2, allowance));
    }

    static Direction tangent(List<BlockPos> route, int index) {
        BlockPos a = route.get(Math.max(0, index - 1)), b = route.get(Math.min(route.size() - 1, index + 1));
        int dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    record Search(List<Piece> pieces, boolean waiting) {}
    static Search preflight(ServerLevel level, UUID village, long project, BlockPos foot,
            List<AuthoredVillageStructures.Cell> plan, List<BlockPos> route,
            List<EconomyService.VillageProjectLot> lots, List<Long> banks) {
        if (plan.isEmpty() || plan.size() > 16) return new Search(List.of(), false);
        List<Piece> pieces = new ArrayList<>();
        for (var cell : plan) {
            BlockPos pos = foot.offset(cell.x(), cell.y(), cell.z());
            if (!loadedAround(level, pos)) return new Search(List.of(), true);
            if (!level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() - 2
                    || excluded(pos, lots, banks) || ConstructionOwnership.reserved(level, pos)
                    || route.stream().anyMatch(p -> Math.abs(p.getX() - pos.getX()) <= 1
                            && Math.abs(p.getZ() - pos.getZ()) <= 1))
                return new Search(List.of(), false);
            BlockState before = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null || !before.getFluidState().isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(level, village, project, pos, before, cell.state()))
                return new Search(List.of(), false);
            if (!clear(before)) return new Search(List.of(), false);
            if (cell.y() == 0 && !naturalBase(level, pos.below(), level.getBlockState(pos.below())))
                return new Search(List.of(), false);
            // Keep every occupied column outside other physical road branches too.
            BlockPos ground = new BlockPos(pos.getX(), foot.getY() - 1, pos.getZ());
            if (road(level.getBlockState(ground))) return new Search(List.of(), false);
            if (!VillageConstructionOccupancy.mayChange(level, pos, before, cell.state()))
                return new Search(List.of(), true);
            pieces.add(new Piece(pos.asLong(), before, cell.state()));
        }
        return new Search(List.copyOf(pieces), false);
    }

    static int placePending(ServerLevel level, UUID village, long project, String key, Job job,
            List<EconomyService.VillageProjectLot> lots, List<Long> banks, int budget) {
        var ledger = get(level);
        if (job.pending().size() > 16 || job.placed() > job.pending().size()) {
            ledger.record(key, new Job(job.station(), List.of(), 0, true)); return 0;
        }
        int baseY = job.pending().stream().mapToInt(p -> BlockPos.of(p.position()).getY()).min().orElse(0);
        // Check the entire tiny reservation before continuing: don't erect an arm over a removed post.
        for (int i = 0; i < job.pending().size(); i++) {
            Piece piece = job.pending().get(i); BlockPos pos = BlockPos.of(piece.position());
            if (!loadedAround(level, pos)) return 0;
            BlockState current = level.getBlockState(pos);
            boolean matches = i < job.placed() ? samePlaced(current, piece.after())
                    : current.equals(piece.before()) || (clear(piece.before()) && current.isAir());
            if (!matches || (pos.getY() == baseY && !naturalBase(level, pos.below(), level.getBlockState(pos.below())))
                    || level.getBlockEntity(pos) != null || !current.getFluidState().isEmpty()
                    || excluded(pos, lots, banks) || ConstructionOwnership.reserved(level, pos)
                    || !VillageDevelopmentProtection.mayPlace(level, village, project, pos, current, piece.after())) {
                ledger.record(key, job.next()); return 0; // Adopt edits; never replace harvested blocks.
            }
        }
        int next = job.placed(), written = 0;
        while (next < job.pending().size() && written < budget) {
            Piece piece = job.pending().get(next); BlockPos pos = BlockPos.of(piece.position());
            BlockState current = level.getBlockState(pos);
            if (!VillageConstructionOccupancy.mayChange(level, pos, current, piece.after())) break;
            if (!piece.after().canSurvive(level, pos)) { ledger.record(key, job.next()); return written; }
            // The receipt advances before mutation. A failed optional write is never repaired.
            next++;
            ledger.record(key, new Job(job.station(), job.pending(), next, false));
            if (!level.setBlock(pos, piece.after(), Block.UPDATE_ALL)
                    || !level.getBlockState(pos).is(piece.after().getBlock())) {
                ledger.record(key, job.next()); return written;
            }
            written++;
        }
        if (next == job.pending().size()) ledger.record(key, job.next());
        return written;
    }

    private static boolean samePlaced(BlockState current, BlockState expected) {
        // Neighbor connection bits are derived by vanilla, not a player modification.
        if ((current.getBlock() instanceof FenceBlock || current.getBlock() instanceof WallBlock)
                && current.is(expected.getBlock()))
            return current.getFluidState().isEmpty();
        if (current.getBlock() instanceof StairBlock && current.is(expected.getBlock()))
            return current.setValue(StairBlock.SHAPE, expected.getValue(StairBlock.SHAPE)).equals(expected);
        return current.equals(expected);
    }
    static boolean excluded(BlockPos p, List<EconomyService.VillageProjectLot> lots, List<Long> banks) {
        for (var lot : lots) {
            BlockPos a = BlockPos.of(lot.boundsMinPos()), b = BlockPos.of(lot.boundsMaxPos());
            if (p.getX() >= Math.min(a.getX(), b.getX()) - 1 && p.getX() <= Math.max(a.getX(), b.getX()) + 1
                    && p.getZ() >= Math.min(a.getZ(), b.getZ()) - 1 && p.getZ() <= Math.max(a.getZ(), b.getZ()) + 1)
                return true;
        }
        for (long packed : banks) {
            BlockPos bank = BlockPos.of(packed);
            if (Math.abs((long)p.getX() - bank.getX()) <= 20 && Math.abs((long)p.getZ() - bank.getZ()) <= 20)
                return true;
        }
        return false;
    }
    private static boolean naturalBase(ServerLevel level, BlockPos p, BlockState state) {
        return VillageProsperityManager.isNaturalProjectGround(state) && state.isFaceSturdy(level, p, Direction.UP);
    }
    private static boolean road(BlockState state) {
        return state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.COARSE_DIRT);
    }
    private static boolean clear(BlockState state) {
        return state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SNOW);
    }
    private static BlockPos surface(ServerLevel level, BlockPos column) {
        if (!level.hasChunk(column.getX() >> 4, column.getZ() >> 4)) return null;
        return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ()) - 1, column.getZ());
    }
    private static boolean loadedAround(ServerLevel level, BlockPos pos) {
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }
}
