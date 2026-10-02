package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Exact plan-only cleanup, historical compatibility and original porch/fixture preservation. */
final class AuthoredEntranceCleanupSelfTest {
    static void run() {
        for (int revision : new int[] {10, 11, 12}) {
            Metadata metadata = new Metadata();
            metadata.width = 11; metadata.height = 9; metadata.templateRevision = revision;
            // An original porch in the preceding base stage must stay reserved.
            Builder stage = new Builder(Set.of(new BlockPos(5, 0, -1)));
            stage.templateRevision = revision;
            stage.put(Phase.FOUNDATION, 5, 0, -2, Blocks.DIRT_PATH.defaultBlockState());
            stage.put(Phase.FOUNDATION, 4, 0, -2, Blocks.STONE_BRICKS.defaultBlockState());
            stage.put(Phase.FOUNDATION, 5, 0, -3, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
            stage.put(Phase.FOUNDATION, 2, 0, -2, Blocks.STONE_BRICKS.defaultBlockState());
            stage.put(Phase.DECOR, 2, 1, -2, Blocks.LANTERN.defaultBlockState());
            stage.put(Phase.FOUNDATION, 5, 0, 12, Blocks.DIRT_PATH.defaultBlockState());
            stage.put(Phase.FOUNDATION, -4, 0, -2, Blocks.MOSS_BLOCK.defaultBlockState());
            var before = stage.values();
            AuthoredEntranceCleanup.removeOptionalApron(stage, metadata);
            if (revision != 11) {
                require(before.equals(stage.values()), "Historical or unknown plan changed");
            } else {
                require(!stage.contains(new BlockPos(5, 0, -2))
                                && !stage.contains(new BlockPos(4, 0, -2))
                                && !stage.contains(new BlockPos(5, 0, -3)),
                        "Bare path apron, masonry shoulder or extra step survived");
                require(stage.isOccupied(new BlockPos(5, 0, -1))
                                && stage.contains(new BlockPos(2, 0, -2))
                                && stage.contains(new BlockPos(2, 1, -2))
                                && stage.contains(new BlockPos(5, 0, 12))
                                && stage.contains(new BlockPos(-4, 0, -2)),
                        "Cleanup removed an original threshold, lamp footing or unrelated yard");
                var cleaned = stage.values();
                AuthoredEntranceCleanup.removeOptionalApron(stage, metadata);
                require(cleaned.equals(stage.values()), "Cleanup is not idempotent");
            }
        }
        int plans = 0, removed = 0;
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(),
                        10, VillageArchitecture.PALETTE_BALANCED,
                        VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect);
                var current = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(),
                        11, VillageArchitecture.PALETTE_BALANCED,
                        VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect);
                require(old.base().equals(current.base()), "Original shell/porch changed: " + descriptor.templateId());
                int center = current.width() / 2;
                for (var cell : current.stageOne()) {
                    if (cell.phase() != Phase.FOUNDATION || cell.y() != 0
                            || cell.z() < -7 || cell.z() >= 0
                            || Math.abs(cell.x() - center) > 3) continue;
                    require(java.util.stream.Stream.concat(current.base().stream(), current.stageOne().stream())
                                    .anyMatch(above -> above.x() == cell.x() && above.z() == cell.z()
                                            && above.y() > 0),
                            "Bare raised apron survived: " + descriptor.templateId() + "/" + dialect + ": " + cell);
                }
                for (var cell : old.stageOne()) {
                    if (cell.y() > 0) require(current.stageOne().contains(cell),
                            "An entrance lamp/yard fixture changed: " + descriptor.templateId() + ": " + cell);
                }
                removed += old.stageOne().size() - current.stageOne().size();
                plans++;
            }
        }
        require(plans == 260 && removed > 0, "Cleanup did not cover all masters and styles");
        System.out.println("PASS clean entrances: 52 masters x five styles; original porches/lamps retained; "
                + removed + " optional apron cells omitted; saved revision 10 remains available");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
