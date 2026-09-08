package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;

/**
 * Authored revision-three roof corrections from Carol's accepted revision-nine pictures.
 * These edit the new in-memory plan before access, pocket and light admission. They are never a
 * repair pass over placed blocks, and the caller keeps the historic revision-one/two plans intact.
 */
final class AuthoredRoofRefinements {
    private AuthoredRoofRefinements() {
    }

    static void apply(Builder b, Metadata m, Materials p, String templateId) {
        switch (templateId) {
            case "cottage_hearth_01" -> hearthRoof(b, m, p);
            case "cottage_bay_04" -> pitchedDormerCap(b, m, p, 4, 6);
            case "cottage_longhouse_05" -> longhouseRoof(b, m, p);
            case "cottage_orchardstead_06" -> roofHatch(b, m, p, 9, 0, 6, Direction.NORTH, false);
            case "house_cross_01" -> crossingLanternCurb(b, p);
            case "house_dormer_02" -> {
                pitchedDormerCap(b, m, p, 3, 8);
                pitchedDormerCap(b, m, p, 7, 8);
                for (int z = -1; z <= 2; z++) {
                    b.force(Phase.ROOF, 5, 10, z, solidRoof(p));
                }
                dormerUpperLights(b, m, p);
            }
            case "inn_gallery_01" -> innArrivalGable(b, m, p);
            case "inn_wayfarer_03" -> wayfarerPorch(b, p);
            case "inn_tavern_04" -> tavernGableLights(b, m, p);
            case "warehouse_bay_01" -> enclosedMonitor(b, m, p);
            case "warehouse_crane_02" -> roofHatch(b, m, p, 10, 1, 8, Direction.NORTH, true);
            case "warehouse_gabled_03" -> compactMonitorCheeks(b, p);
            case "warehouse_wharf_04" -> wharfCraneBracing(b, p);
            case "granary_loft_01" -> roofHatch(b, m, p, 6, 9, 9, Direction.SOUTH, true);
            case "granary_windmill_02" -> balancedWindmillSails(b, p);
            case "granary_cruck_03" -> roofHatch(b, m, p, 5, 8, 7, Direction.SOUTH, true);
            case "smithy_lane_03" -> taperedForgeFlue(b, p);
            case "mine_winding_house_02" -> windingLoftLights(b, m, p);
            case "mine_quarry_05" -> quarryRidgeEnds(b, p);
            case "guard_watch_01" -> watchCorbels(b, p);
            default -> { }
        }
    }

    /** The winding hall's tall blind ends need a machinery-scale window, not a token roof lamp. */
    private static void windingLoftLights(Builder b, Metadata m, Materials p) {
        for (int z : new int[] {0, 16}) {
            framedGableGlazing(b, m, p, 5, 13, 7, 11, z, 9);
        }
    }

    private static void tavernGableLights(Builder b, Metadata m, Materials p) {
        for (int z : new int[] {0, 12}) {
            framedGableGlazing(b, m, p, 5, 9, 5, 8, z, 7);
        }
    }

    /** A full-block glazed insert retains the authored exterior seal, including unused lofts. */
    private static void framedGableGlazing(Builder b, Metadata m, Materials p,
            int minX, int maxX, int bottomY, int topY, int z, int mullionX) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = bottomY; y <= topY; y++) {
                BlockPos position = new BlockPos(x, y, z);
                if (!replaceableGableInfill(b.cellAt(position), p)
                        || m.reservedAir.contains(position) || m.accessTargets.contains(position)) {
                    return;
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = bottomY; y <= topY; y++) {
                boolean horizontalFrame = y == bottomY || y == topY;
                boolean frame = horizontalFrame || x == minX || x == maxX || x == mullionX;
                BlockState state = frame ? p.timber().defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS,
                                horizontalFrame ? Direction.Axis.X : Direction.Axis.Y)
                        : Blocks.GLASS.defaultBlockState();
                b.force(frame ? Phase.FRAME : Phase.OPENING, x, y, z, state);
            }
        }
    }

    private static void dormerUpperLights(Builder b, Metadata m, Materials p) {
        for (int x : new int[] {3, 7}) {
            BlockPos position = new BlockPos(x, 10, 0);
            Cell below = b.cellAt(position.below());
            if (replaceableGableInfill(b.cellAt(position), p)
                    && below != null && below.phase() == Phase.OPENING
                    && !m.reservedAir.contains(position) && !m.accessTargets.contains(position)) {
                b.force(Phase.OPENING, x, 10, 0, Blocks.GLASS.defaultBlockState());
            }
        }
    }

    private static boolean replaceableGableInfill(Cell cell, Materials p) {
        return cell != null && (cell.phase() == Phase.SHELL || cell.phase() == Phase.FRAME)
                && (cell.state().is(p.wall()) || cell.state().is(p.timber()));
    }

    /** Drop only the clipped cap's sideways ears, retaining the continuous roof and center ridge. */
    private static void quarryRidgeEnds(Builder b, Materials p) {
        BlockState clippedEar = p.roofSlab().defaultBlockState();
        for (int z : new int[] {11, 21}) {
            for (int x : new int[] {2, 4}) {
                Cell cell = b.cellAt(new BlockPos(x, 13, z));
                if (cell != null && cell.phase() == Phase.ROOF && cell.state().equals(clippedEar)
                        && !b.isOccupied(new BlockPos(x, 14, z))) {
                    b.remove(x, 13, z);
                }
            }
        }
    }

    private static void wayfarerPorch(Builder b, Materials p) {
        // Extend the existing two-deep porch by one course, with the posts outside the aisle.
        for (int x = 4; x <= 8; x++) {
            b.force(Phase.ROOF, x, 4, -2, solidRoof(p));
            b.putIfFree(Phase.ROOF, x, 4, -3, stair(p, Direction.SOUTH));
            b.force(Phase.FOUNDATION, x, 0, -2, p.floor().defaultBlockState());
        }
        for (int x : new int[] {4, 8}) {
            for (int y = 1; y <= 3; y++) {
                b.force(Phase.FRAME, x, y, -2, p.timber().defaultBlockState());
            }
            b.putIfFree(Phase.FRAME, x, 3, -3, p.roofStairs().defaultBlockState()
                    .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x = 5; x <= 7; x++) {
            b.putIfFree(Phase.FOUNDATION, x, 0, -3, p.entryStairs().defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
    }

    private static void wharfCraneBracing(Builder b, Materials p) {
        for (int z : new int[] {4, 6}) {
            b.putIfFree(Phase.FRAME, 13, 8, z, p.roofStairs().defaultBlockState()
                    .setValue(StairBlock.HALF, Half.TOP)
                    .setValue(StairBlock.FACING, z == 4 ? Direction.NORTH : Direction.SOUTH));
        }
        // A backed timber tie meets the crane's transverse beam without crossing the cart lane.
        for (int x = 5; x <= 12; x++) {
            b.putIfFree(Phase.FRAME, x, 9, 5, p.timber().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        for (int y = 5; y <= 8; y++) {
            b.putIfFree(Phase.FRAME, 5, y, 5, p.timber().defaultBlockState());
        }
    }

    private static void watchCorbels(Builder b, Materials p) {
        // Repeat stone brackets immediately under the occupied watch deck, never over its slits.
        for (int x : new int[] {2, 4, 6, 8}) {
            b.putIfFree(Phase.FRAME, x, 11, 1, p.entryStairs().defaultBlockState()
                    .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int z : new int[] {3, 5, 7}) {
            b.putIfFree(Phase.FRAME, 1, 11, z, p.entryStairs().defaultBlockState()
                    .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.EAST));
            b.putIfFree(Phase.FRAME, 9, 11, z, p.entryStairs().defaultBlockState()
                    .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.WEST));
        }
    }

    /** The shallow half of a saltbox needs solid treads between risers, not repeated stair teeth. */
    private static void hearthRoof(Builder b, Metadata m, Materials p) {
        int[] heights = {4, 5, 6, 7, 8, 8, 7, 7, 6, 6, 5, 5, 4};
        clearRoofDraft(b, p, -1, 11, 4, 10, -1, 10);
        for (int x = -1; x <= 11; x++) {
            int firstZ = x <= 9 ? -1 : 3;
            int lastZ = x < 6 ? 8 : 10;
            for (int z = firstZ; z <= lastZ; z++) {
                slopeCell(b, p, x, heights[x + 1], z, x <= 3 ? Direction.EAST : Direction.WEST,
                        x <= 3 ? x < 3 && heights[x + 2] == heights[x + 1]
                                : x < 11 && heights[x + 2] == heights[x + 1]);
            }
        }
        // Close only the existing exterior wall planes; the L-shaped occupied room stays intact.
        for (int x = 0; x <= 8; x++) {
            gableInfill(b, p, x, 0, 4, heights[x + 1]);
            if (x < 7) {
                gableInfill(b, p, x, 7, 4, heights[x + 1]);
            }
        }
        for (int x = 7; x <= 10; x++) {
            gableInfill(b, p, x, 9, 4, heights[x + 1]);
            if (x > 8) {
                gableInfill(b, p, x, 4, 4, heights[x + 1]);
            }
        }
        sealRaisedWallEdges(b, m, p, 4, heights,
                (x, z) -> (x <= 8 && z <= 7) || (x >= 7 && z >= 4));
        m.roofPeak = Math.max(m.roofPeak, 8);
    }

    private static void longhouseRoof(Builder b, Metadata m, Materials p) {
        int[] heights = {5, 6, 7, 8, 9, 9, 9, 8, 8, 8, 7, 7, 7, 6, 6, 5, 5};
        clearRoofDraft(b, p, -1, 15, 5, 11, -1, 9);
        for (int x = -1; x <= 15; x++) {
            for (int z = x <= 11 ? -1 : 2; z <= 9; z++) {
                slopeCell(b, p, x, heights[x + 1], z, x <= 3 ? Direction.EAST : Direction.WEST,
                        x > 3 && x < 15 && heights[x + 2] == heights[x + 1]);
            }
        }
        for (int x = 0; x <= 14; x++) {
            gableInfill(b, p, x, x <= 10 ? 0 : 3, 5, heights[x + 1]);
            gableInfill(b, p, x, 8, 5, heights[x + 1]);
        }
        sealRaisedWallEdges(b, m, p, 5, heights, (x, z) -> x <= 10 || z >= 3);
        m.roofPeak = Math.max(m.roofPeak, 9);
    }

    /** A raised roof must extend every exposed side wall, including re-entrant L-plan corners. */
    private static void sealRaisedWallEdges(Builder b, Metadata m, Materials p, int minY,
            int[] heights, java.util.function.BiPredicate<Integer, Integer> footprint) {
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (!footprint.test(x, z)) continue;
                boolean edge = x == 0 || z == 0 || x == m.width - 1 || z == m.depth - 1
                        || !footprint.test(x - 1, z) || !footprint.test(x + 1, z)
                        || !footprint.test(x, z - 1) || !footprint.test(x, z + 1);
                if (edge) gableInfill(b, p, x, z, minY, heights[x + 1]);
            }
        }
    }

    private static void slopeCell(
            Builder b, Materials p, int x, int y, int z, Direction facing, boolean tread) {
        Cell previous = b.cellAt(new BlockPos(x, y, z));
        if (previous != null && previous.state().is(p.chimney())) {
            return;
        }
        b.force(Phase.ROOF, x, y, z, tread ? solidRoof(p) : stair(p, facing));
    }

    private static void gableInfill(Builder b, Materials p, int x, int z, int bottom, int roofY) {
        for (int y = bottom; y < roofY; y++) {
            Cell old = b.cellAt(new BlockPos(x, y, z));
            if (old == null || roofDraftCell(old, p)) {
                b.force(Phase.SHELL, x, y, z, p.wall().defaultBlockState());
            }
        }
    }

    /** Replace the old five-wide flat board while retaining the grounded jambs and window. */
    private static void pitchedDormerCap(Builder b, Metadata m, Materials p, int cx, int baseY) {
        clearRoofDraft(b, p, cx - 2, cx + 2, baseY + 2, baseY + 4, -1, 2);
        for (int z = -1; z <= 2; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                int y = baseY + 4 - Math.abs(dx);
                b.force(Phase.ROOF, cx + dx, y, z, dx == 0
                        ? p.roofSlab().defaultBlockState()
                        : stair(p, dx < 0 ? Direction.EAST : Direction.WEST));
                if (z == 0 || z == 2) {
                    gableInfill(b, p, cx + dx, z, baseY + 2, y);
                }
            }
            // Full cheek bearings seal the half-height join to the surviving main slope.
            if (z > 0) {
                for (int x : new int[] {cx - 1, cx + 1}) {
                    b.force(Phase.FRAME, x, baseY + 1, z, p.timber().defaultBlockState());
                    b.force(Phase.FRAME, x, baseY + 2, z, p.timber().defaultBlockState());
                }
            }
        }
        m.roofPeak = Math.max(m.roofPeak, baseY + 4);
    }

    /** A clearly framed curb lets the crossing lantern sit on both intersecting roof planes. */
    private static void crossingLanternCurb(Builder b, Materials p) {
        for (int x = 5; x <= 9; x++) {
            for (int z = 4; z <= 9; z++) {
                if (x == 5 || x == 9 || z == 4 || z == 9) {
                    b.force(Phase.ROOF, x, 9, z, solidRoof(p));
                    b.force(Phase.ROOF, x, 10, z, p.roofSlab().defaultBlockState());
                }
            }
        }
        for (int x = 6; x <= 8; x++) {
            for (int z = 5; z <= 8; z++) {
                b.force(Phase.FRAME, x, 10, z, p.timber().defaultBlockState());
            }
        }
    }

    /** One dominant arrival gable bridges the court; the two lower lodging gables remain legible. */
    private static void innArrivalGable(Builder b, Metadata m, Materials p) {
        // Keep the higher rear-hip surface at the intersection. Its rising front and side
        // courses are part of the existing weather envelope, not stray front-gable trim.
        java.util.List<Cell> upperHip = b.values().stream()
                .filter(cell -> cell.x() >= 3 && cell.x() <= 13
                        && cell.z() >= 3 && cell.z() <= 7
                        && cell.y() > 12 - Math.abs(cell.x() - 8))
                .toList();
        clearRoofDraft(b, p, 3, 13, 7, 13, -1, 7);
        for (int x = 4; x <= 12; x++) {
            b.force(Phase.FRAME, x, 6, 0, p.timber().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        for (int z = -1; z <= 7; z++) {
            for (int x = 3; x <= 13; x++) {
                int y = 12 - Math.abs(x - 8);
                b.force(Phase.ROOF, x, y, z, x == 8
                        ? p.roofSlab().defaultBlockState()
                        : stair(p, x < 8 ? Direction.EAST : Direction.WEST));
                if (z == 0 || z == 7) {
                    gableInfill(b, p, x, z, 7, y);
                }
            }
        }
        // Central gable glazing is deliberately part of the wall, below the sealed roof cap.
        for (int x : new int[] {7, 9}) {
            b.force(Phase.OPENING, x, 8, 0, Blocks.GLASS_PANE.defaultBlockState());
            b.force(Phase.OPENING, x, 9, 0, Blocks.GLASS_PANE.defaultBlockState());
        }
        b.force(Phase.FRAME, 8, 8, 0, p.timber().defaultBlockState());
        b.force(Phase.FRAME, 8, 9, 0, p.timber().defaultBlockState());
        for (int x : new int[] {4, 7, 9, 12}) {
            for (int z = 1; z <= 3; z++) {
                gableInfill(b, p, x, z, 7, 12 - Math.abs(x - 8));
            }
        }
        for (int x : new int[] {5, 6, 10, 11}) {
            gableInfill(b, p, x, 4, 7, 12 - Math.abs(x - 8));
        }
        // The surviving lodging gables stand one course above the new arrival roof's edges.
        // Their shared valley needs a full bearing, including the front end-grain cheek.
        for (int x : new int[] {3, 13}) {
            for (int z = 0; z <= 7; z++) {
                b.force(Phase.ROOF, x, 8, z, solidRoof(p));
                b.force(Phase.ROOF, x, 9, z, solidRoof(p));
            }
        }
        for (Cell cell : upperHip) {
            b.force(cell.phase(), cell.x(), cell.y(), cell.z(), cell.state());
        }
        m.roofPeak = Math.max(m.roofPeak, 12);
    }

    private static void enclosedMonitor(Builder b, Metadata m, Materials p) {
        // The broad roof's repeated same-height stairs were separate upright fins. Keep only
        // the outer riser of each three-block tread, then frame the raised glazed monitor.
        for (int x = -1; x <= 17; x++) {
            if (x >= 7 && x <= 9) {
                continue;
            }
            int edge = Math.min(x + 1, 17 - x);
            int y = 6 + Math.max(0, edge) / 3;
            for (int z = -1; z <= 11; z++) {
                boolean tread = edge % 3 != 0;
                b.force(Phase.ROOF, x, y, z, tread ? solidRoof(p)
                        : stair(p, x < 8 ? Direction.EAST : Direction.WEST));
            }
            if (x >= 0 && x <= 16) {
                gableInfill(b, p, x, 0, 6, y);
                gableInfill(b, p, x, 10, 6, y);
            }
        }
        clearRoofDraft(b, p, 6, 10, 9, 11, -1, 11);
        for (int z = 0; z <= 10; z++) {
            for (int x : new int[] {7, 9}) {
                b.force(Phase.FRAME, x, 7, z, p.timber().defaultBlockState());
                b.force(z % 5 == 0 ? Phase.FRAME : Phase.OPENING, x, 8, z,
                        (z % 5 == 0 ? p.timber() : Blocks.GLASS_PANE).defaultBlockState());
            }
            b.force(Phase.ROOF, 6, 8, z, solidRoof(p));
            b.force(Phase.ROOF, 10, 8, z, solidRoof(p));
        }
        for (int z : new int[] {0, 10}) {
            b.force(Phase.FRAME, 8, 7, z, p.timber().defaultBlockState());
            b.force(Phase.OPENING, 8, 8, z, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int z = -1; z <= 11; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                int y = 11 - Math.abs(dx);
                b.force(Phase.ROOF, 8 + dx, y, z, dx == 0
                        ? p.roofSlab().defaultBlockState()
                        : stair(p, dx < 0 ? Direction.EAST : Direction.WEST));
                if (z == 0 || z == 10) {
                    gableInfill(b, p, 8 + dx, z, 9, y);
                }
            }
        }
        m.roofPeak = Math.max(m.roofPeak, 11);
    }

    private static void compactMonitorCheeks(Builder b, Materials p) {
        for (int x = 3; x <= 9; x++) {
            for (int z : new int[] {2, 6}) {
                b.force(Phase.FRAME, x, 6, z, p.timber().defaultBlockState());
            }
        }
        for (int x : new int[] {3, 9}) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 6; y <= 8; y++) {
                    b.force(Phase.FRAME, x, y, z, p.timber().defaultBlockState());
                }
            }
        }
        for (int x = 2; x <= 10; x++) {
            for (int z = 1; z <= 7; z++) {
                b.force(Phase.ROOF, x, 9, z, z == 1 ? stair(p, Direction.SOUTH)
                        : z == 7 ? stair(p, Direction.NORTH) : solidRoof(p));
            }
        }
    }

    /** A sealed roof hatch with timber cheeks and a pitched cap, kept within its master's envelope. */
    private static void roofHatch(
            Builder b, Metadata m, Materials p, int cx, int faceZ, int sillY,
            Direction outward, boolean loading) {
        int inward = -outward.getStepZ();
        int rearZ = faceZ + 2 * inward;
        for (int z = Math.min(faceZ, rearZ); z <= Math.max(faceZ, rearZ); z++) {
            for (int dx = -1; dx <= 1; dx++) {
                // Fill only the roof's new exposed cheeks; no new closed attic volume is created.
                for (int y = sillY; y <= sillY + 1; y++) {
                    b.force(Phase.FRAME, cx + dx, y, z, p.timber().defaultBlockState());
                }
            }
        }
        b.force(Phase.OPENING, cx, sillY + 1, faceZ,
                loading ? Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.FACING, outward)
                        .setValue(TrapDoorBlock.OPEN, true)
                        : Blocks.GLASS.defaultBlockState());
        for (int z = Math.min(faceZ, rearZ) - 1; z <= Math.max(faceZ, rearZ) + 1; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                int y = sillY + 4 - Math.abs(dx);
                b.force(Phase.ROOF, cx + dx, y, z, dx == 0
                        ? p.roofSlab().defaultBlockState()
                        : stair(p, dx < 0 ? Direction.EAST : Direction.WEST));
                if (z == faceZ || z == rearZ) {
                    gableInfill(b, p, cx + dx, z, sillY + 2, y);
                }
            }
        }
        // One short hoist nosing above a hatch establishes its loading function without adding
        // a second dangling chain in the entrance or altering the existing villager route.
        if (loading) {
            b.force(Phase.ROOF, cx, sillY + 1, faceZ + outward.getStepZ(), solidRoof(p));
            b.force(Phase.FRAME, cx, sillY + 2, faceZ + outward.getStepZ(),
                    p.timber().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        m.roofPeak = Math.max(m.roofPeak, sillY + 4);
    }

    /** A front-facing, balanced four-sail wheel replaces the edge-on pole and flat trapdoor comb. */
    private static void balancedWindmillSails(Builder b, Materials p) {
        for (int offset = -5; offset <= 5; offset++) {
            b.remove(17, 11 + offset, 9);
            b.remove(17, 11, 9 + offset);
        }
        for (int offset : new int[] {-5, -4, 4, 5}) {
            b.remove(18, 11 + offset, 9);
            b.remove(18, 11, 9 + offset);
        }
        for (int distance = 2; distance <= 5; distance++) {
            for (int side : new int[] {-1, 1}) {
                b.remove(18, 11 + side * distance, 8);
                b.remove(18, 11 + side * distance, 10);
                b.remove(18, 10, 9 + side * distance);
                b.remove(18, 12, 9 + side * distance);
            }
        }
        b.remove(18, 11, 9);
        b.remove(19, 11, 9);
        // The old axle replaced an original eave cell. Restore that weather seal on removal.
        b.force(Phase.ROOF, 17, 8, 9, stair(p, Direction.WEST));
        int cx = 8;
        int cy = 12;
        int planeZ = -3;
        for (int z = planeZ; z <= 0; z++) {
            b.force(Phase.FRAME, cx, cy, z, p.timber().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        for (int offset = -5; offset <= 5; offset++) {
            b.force(Phase.FRAME, cx + offset, cy, planeZ, p.timber().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            b.force(Phase.FRAME, cx, cy + offset, planeZ, p.timber().defaultBlockState());
        }
        for (int reach = 2; reach <= 5; reach++) {
            // Each cloth panel lies on the clockwise side of its spar, giving balanced rotation
            // and four clearly separate blades with open quadrants around the dark central hub.
            for (int[] blade : new int[][] {
                    {1, reach}, {reach, -1}, {-1, -reach}, {-reach, 1}}) {
                b.force(Phase.DECOR, cx + blade[0], cy + blade[1], planeZ,
                        Blocks.WOOL.white().defaultBlockState());
                b.force(Phase.DECOR, cx + blade[0], cy + blade[1], planeZ - 1,
                        Blocks.OAK_TRAPDOOR.defaultBlockState()
                                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                                .setValue(TrapDoorBlock.OPEN, true));
            }
        }
        b.force(Phase.FRAME, cx, cy, planeZ - 1, p.accent().defaultBlockState());
    }

    private static void taperedForgeFlue(Builder b, Materials p) {
        for (int x = 8; x <= 12; x++) {
            b.remove(x, 10, 8);
        }
        b.force(Phase.ROOF, 9, 8, 8, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST));
        b.force(Phase.ROOF, 11, 8, 8, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST));
        b.force(Phase.ROOF, 10, 9, 8, p.chimney().defaultBlockState());
        b.force(Phase.ROOF, 10, 10, 8, Blocks.STONE_BRICK_SLAB.defaultBlockState());
    }

    private static void clearRoofDraft(
            Builder b, Materials p, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (Cell cell : b.values()) {
            if (cell.x() >= minX && cell.x() <= maxX && cell.y() >= minY && cell.y() <= maxY
                    && cell.z() >= minZ && cell.z() <= maxZ && roofDraftCell(cell, p)) {
                b.remove(cell.x(), cell.y(), cell.z());
            }
        }
    }

    private static boolean roofDraftCell(Cell cell, Materials p) {
        return cell.state().is(p.roofStairs()) || cell.state().is(p.roofSlab())
                || (cell.phase() == Phase.SHELL && cell.state().is(p.wall()))
                || (cell.phase() == Phase.FRAME && cell.state().is(p.timber()));
    }

    private static BlockState solidRoof(Materials p) {
        return p.roofSlab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static BlockState stair(Materials p, Direction facing) {
        return p.roofStairs().defaultBlockState().setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }
}
