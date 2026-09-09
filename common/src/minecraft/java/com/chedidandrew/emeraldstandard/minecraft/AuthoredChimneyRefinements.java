package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** New-plan-only local roofline sizing; never repairs or modifies a placed building. */
final class AuthoredChimneyRefinements {
    private AuthoredChimneyRefinements() { }

    static void refine(Builder base, Metadata metadata, Materials p) {
        if (base.templateRevision < 5 || metadata.templateRevision < 5) return;
        for (BlockPos column : metadata.integratedChimneys.stream()
                .sorted(Comparator.<BlockPos>comparingInt(pos -> pos.getX())
                        .thenComparingInt(pos -> pos.getZ())).toList()) {
            refineColumn(base, metadata, p, column);
            if (base.templateRevision >= 8 && metadata.templateRevision >= 8) {
                finishShortColumn(base, metadata, p, column);
            }
        }
    }

    /** Complete the short/capped cases that could not admit the old two-course collar rule. */
    private static void finishShortColumn(Builder base, Metadata metadata, Materials p, BlockPos column) {
        Cell top = base.values().stream()
                .filter(c -> c.x() == column.getX() && c.z() == column.getZ())
                .max(Comparator.comparingInt(Cell::y)).orElse(null);
        if (top == null) return;
        // Already-slim tips and multi-block architectural caps are not plain brick stacks.
        if (top.state().getBlock() instanceof net.minecraft.world.level.block.WallBlock) return;
        int topY = top.y();
        if (!top.state().is(p.chimney())) {
            if (!(top.state().getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                    && !top.state().is(p.accent())) return;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Cell side = base.cellAt(new BlockPos(column.getX(), topY, column.getZ()).relative(direction));
                if (side != null && !side.state().isAir()) return;
            }
            topY--;
        }
        int bottom = topY;
        int bearing = -1;
        int roof = -1;
        for (int y = topY; y >= 1; y--) {
            BlockPos pos = column.above(y);
            Cell cell = base.cellAt(pos);
            if (cell == null || !cell.state().is(p.chimney())) break;
            bottom = y;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Cell side = base.cellAt(pos.relative(direction));
                if (side == null || side.state().isAir()) continue;
                bearing = Math.max(bearing, y);
                if (side.state().is(p.roofSlab()) || side.state().is(p.roofStairs())) roof = Math.max(roof, y);
            }
        }
        if (roof < 0 || bearing >= topY) return;
        int exposedStart = Math.max(bottom, bearing + 1);
        int exposed = topY - exposedStart + 1;
        // Retain the lower half as full masonry; even a one-course exposed tip becomes a wall.
        int firstWall = exposedStart + exposed / 2;
        for (int y = firstWall; y <= topY; y++) {
            BlockPos pos = column.above(y);
            if (metadata.reservedAir.contains(pos) || metadata.accessTargets.contains(pos)) return;
        }
        for (int y = firstWall; y <= topY; y++) {
            BlockPos pos = column.above(y);
            Cell existing = base.cellAt(pos);
            base.force(existing.phase(), pos.getX(), y, pos.getZ(),
                    (p.chimney() == Blocks.CUT_SANDSTONE ? Blocks.SANDSTONE_WALL : Blocks.BRICK_WALL)
                            .defaultBlockState());
        }
    }

    private static void refineColumn(Builder base, Metadata metadata, Materials p, BlockPos column) {
        Cell top = base.values().stream()
                .filter(c -> c.x() == column.getX() && c.z() == column.getZ())
                .max(Comparator.comparingInt(Cell::y)).orElse(null);
        // A contrasting cap or an authored multi-column crown is deliberate architecture.
        // Only the plain full-block stacks created by addIntegratedChimney are candidates.
        if (top == null || !top.state().is(p.chimney())) return;
        int localRoof = -1;
        int lastSideBearing = -1;
        int bottom = top.y();
        for (int y = top.y(); y >= 1; y--) {
            BlockPos pos = column.above(y);
            Cell cell = base.cellAt(pos);
            if (cell == null || !cell.state().is(p.chimney())) break;
            bottom = y;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Cell neighbor = base.cellAt(pos.relative(direction));
                if (neighbor == null || neighbor.state().isAir()) continue;
                lastSideBearing = Math.max(lastSideBearing, y);
                if (neighbor.state().is(p.roofStairs()) || neighbor.state().is(p.roofSlab())) {
                    localRoof = Math.max(localRoof, y);
                }
            }
        }
        if (localRoof < 0) return; // Never guess a roofline from the distant building peak.
        int collar = Math.max(localRoof, lastSideBearing) + 1;
        int tip = Math.min(top.y(), collar + 2);
        if (collar < bottom || tip - collar < 2) return;
        for (int y = collar + 1; y <= top.y(); y++) {
            BlockPos pos = column.above(y);
            Cell existing = base.cellAt(pos);
            if (existing == null || !existing.state().is(p.chimney())
                    || metadata.reservedAir.contains(pos) || metadata.accessTargets.contains(pos)) return;
        }
        for (int y = collar + 1; y <= top.y(); y++) {
            BlockPos pos = column.above(y);
            if (y > tip) {
                base.remove(pos.getX(), y, pos.getZ());
            } else {
                Cell existing = base.cellAt(pos);
                // Standalone wall posts have a smaller cross section with direct vertical contact.
                base.force(existing.phase(), pos.getX(), y, pos.getZ(),
                        (p.chimney() == Blocks.CUT_SANDSTONE ? Blocks.SANDSTONE_WALL : Blocks.BRICK_WALL)
                                .defaultBlockState());
            }
        }
    }
}
