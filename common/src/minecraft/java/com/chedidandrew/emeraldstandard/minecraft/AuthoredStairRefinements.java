package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Reviewed v10-only slope corrections, never a heuristic repair of player-owned world blocks. */
final class AuthoredStairRefinements {
    private AuthoredStairRefinements() {}

    static void apply(Builder builder, String id) {
        for (Cell cell : builder.values()) {
            Direction uphill = expectedDirection(id, cell);
            if (uphill != null) builder.force(cell.phase(), cell.x(), cell.y(), cell.z(),
                    cell.state().setValue(StairBlock.FACING, uphill));
        }
    }

    static Direction expectedDirection(String id, Cell cell) {
        if (cell.phase() != Phase.ROOF || !(cell.state().getBlock() instanceof StairBlock)
                || cell.state().getValue(StairBlock.HALF) != Half.BOTTOM) return null;
        int x = cell.x(), y = cell.y(), z = cell.z();
        if (id.equals("mine_adit_03")) {
            if (x >= -1 && x <= 4 && z >= 4 && z <= 11 && y == 5 + Math.max(0, x) / 3)
                return Direction.EAST;
            if (x >= 6 && x <= 11 && z >= 6 && z <= 11 && y == 5 + Math.max(0, 10 - x) / 3)
                return Direction.WEST;
        }
        if (id.equals("warehouse_wharf_04") && x >= 6 && x <= 14
                && z >= 7 && z <= 12 && y == 5 + (14 - x) / 2)
            return Direction.WEST;
        return null;
    }
}
