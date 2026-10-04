package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import net.minecraft.core.BlockPos;

/** Plan-only cleanup: never removes world blocks or changes a frozen older blueprint. */
final class AuthoredEntranceCleanup {
    private AuthoredEntranceCleanup() { }

    static void removeOptionalApron(Builder stage, Metadata metadata) {
        if (stage.templateRevision < 11
                || stage.templateRevision > AuthoredVillageStructures.LEGACY_TEMPLATE_REVISION
                || metadata.templateRevision != stage.templateRevision) return;
        int center = metadata.width / 2;
        // Only optional dressing owns these cells. Base porch/threshold cells are blocked in
        // this builder and cannot be removed. Keep every footing carrying a lamp or yard prop.
        for (var cell : stage.values()) {
            if (cell.phase() != Phase.FOUNDATION || cell.y() != 0
                    || cell.z() < -7 || cell.z() >= 0
                    || Math.abs(cell.x() - center) > 3) continue;
            boolean supportsFixture = false;
            for (int y = 1; y <= Math.max(6, metadata.height); y++) {
                BlockPos above = new BlockPos(cell.x(), y, cell.z());
                if (stage.contains(above) || (y <= 2 && stage.isOccupied(above))) {
                    supportsFixture = true;
                    break;
                }
            }
            if (!supportsFixture) stage.remove(cell.x(), cell.y(), cell.z());
        }
    }
}
