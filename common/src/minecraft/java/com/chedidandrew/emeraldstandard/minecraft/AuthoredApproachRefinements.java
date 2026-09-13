package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

/** A finished, three-wide arrival for new raised plans; never changes world terrain or old plans. */
final class AuthoredApproachRefinements {
    // Stay inside the existing authored front envelope and gallery plot separation.
    private static final int FRONT_LIMIT_Z = -7;

    private AuthoredApproachRefinements() {
    }

    /**
     * Finds the outermost exact path strip actually emitted by presentation stage one. Porches,
     * existing steps, side-yard scenes, and their reserved cells are never rewritten. A single
     * obstructed lane or headroom cell leaves the complete optional transition untouched.
     *
     * <p>At the front envelope the final path row becomes the step instead of extending the lot.
     * At least one complete dirt-centered row must remain behind that step. Runtime foundation
     * generation supplies the ordinary bounded support suffix below these y=0 cells; no new
     * terrain-replacement permission or repair behavior is introduced.</p>
     */
    static Outcome finish(Builder stage, Metadata metadata, Materials materials) {
        if (stage.templateRevision < 3 || stage.templateRevision > 10
                || metadata.templateRevision != stage.templateRevision) {
            return Outcome.UNCHANGED_REVISION;
        }
        int center = metadata.width / 2;
        int outermost = stage.values().stream()
                .filter(cell -> cell.x() == center && cell.y() == 0
                        && cell.z() >= FRONT_LIMIT_Z && cell.z() < 0
                        && isPath(cell))
                .mapToInt(Cell::z)
                .min()
                .orElse(0);
        if (outermost == 0 || !isPathRow(stage, center, outermost)) {
            return Outcome.NO_COMPLETE_STRIP;
        }

        int innermost = outermost;
        while (innermost + 1 < 0 && isPathRow(stage, center, innermost + 1)) {
            innermost++;
        }
        boolean replaceEnd = outermost == FRONT_LIMIT_Z;
        int stairZ = replaceEnd ? outermost : outermost - 1;
        int firstPaverZ = replaceEnd ? outermost + 1 : outermost;
        if (firstPaverZ > innermost) {
            return Outcome.NO_ROOM_WITHIN_ENVELOPE;
        }

        // Preflight every lane before touching any authored state. Check standing space above
        // the entire strip as well as the half-height ascent, not just the center-line camera.
        for (int z = stairZ; z <= innermost; z++) {
            for (int x = center - 1; x <= center + 1; x++) {
                BlockPos floor = new BlockPos(x, 0, z);
                if (metadata.reservedAir.contains(floor)
                        || metadata.accessTargets.contains(floor)
                        || (z == stairZ && !replaceEnd && stage.isOccupied(floor))) {
                    return Outcome.OBSTRUCTED;
                }
                for (int y = 1; y <= 2; y++) {
                    if (stage.isOccupied(new BlockPos(x, y, z))) {
                        return Outcome.OBSTRUCTED;
                    }
                }
            }
        }

        for (int z = firstPaverZ; z <= innermost; z++) {
            for (int x : new int[] {center - 1, center + 1}) {
                stage.force(Phase.FOUNDATION, x, 0, z,
                        materials.foundation().defaultBlockState());
            }
        }
        for (int x = center - 1; x <= center + 1; x++) {
            stage.force(Phase.FOUNDATION, x, 0, stairZ,
                    materials.entryStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        return replaceEnd ? Outcome.FINISHED_WITHIN_ENVELOPE : Outcome.FINISHED;
    }

    private static boolean isPathRow(Builder stage, int center, int z) {
        for (int x = center - 1; x <= center + 1; x++) {
            if (!isPath(stage.cellAt(new BlockPos(x, 0, z)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPath(Cell cell) {
        return cell != null && cell.phase() == Phase.FOUNDATION
                && cell.state().is(Blocks.DIRT_PATH);
    }

    enum Outcome {
        FINISHED,
        FINISHED_WITHIN_ENVELOPE,
        UNCHANGED_REVISION,
        NO_COMPLETE_STRIP,
        NO_ROOM_WITHIN_ENVELOPE,
        OBSTRUCTED
    }
}
