package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** Raises only low, horizontal domestic ceiling ties, never furniture or exterior wall belts. */
final class AuthoredCeilingClearanceRefinements {
    private AuthoredCeilingClearanceRefinements() { }

    static void refine(Builder b, Metadata m, Materials p) {
        if (m.templateRevision < 6 || (m.type != ProjectType.HOUSE && m.type != ProjectType.COTTAGE
                && m.type != ProjectType.INN)) return;
        for (Cell cell : b.values()) {
            if (cell.y() != 3 || cell.phase() != Phase.FRAME || !cell.state().is(p.timber())
                    || !cell.state().hasProperty(RotatedPillarBlock.AXIS)) continue;
            if (cell.x() <= 0 || cell.x() >= m.width - 1
                    || cell.z() <= 0 || cell.z() >= m.depth - 1) continue;
            Direction.Axis axis = cell.state().getValue(RotatedPillarBlock.AXIS);
            if (axis == Direction.Axis.Y) continue;
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            Direction across = axis == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
            // Both faces must look into the interior, not outdoors or into a supporting wall.
            if (!interiorSide(b, pos.relative(across))
                    || !interiorSide(b, pos.relative(across.getOpposite()))) continue;
            Cell underneath = b.cellAt(pos.below());
            if (underneath != null && !underneath.state().isAir()) continue;
            Cell upper = b.cellAt(pos.above());
            if (upper != null && !upper.state().isAir()
                    && !((upper.phase() == Phase.SHELL || upper.phase() == Phase.FRAME)
                    && (upper.state().is(p.wall()) || upper.state().is(p.timber())))) continue;
            if (m.reservedAir.contains(pos.above()) || m.accessTargets.contains(pos.above())) continue;
            b.remove(pos.getX(), 3, pos.getZ());
            b.force(Phase.FRAME, pos.getX(), 4, pos.getZ(), cell.state());
        }
    }

    private static boolean interiorSide(Builder b, BlockPos pos) {
        Cell floor = b.cellAt(new BlockPos(pos.getX(), 0, pos.getZ()));
        Cell head = b.cellAt(pos.below());
        return floor != null && floor.phase() == Phase.FOUNDATION
                && (head == null || head.state().isAir());
    }
}
