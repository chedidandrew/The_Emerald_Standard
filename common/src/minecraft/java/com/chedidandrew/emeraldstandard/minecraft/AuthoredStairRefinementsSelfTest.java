package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Enumerates the whole current catalog and proves only reviewed slopes change from v9. */
final class AuthoredStairRefinementsSelfTest {
    static void run() {
        int checked = 0, corrected = 0;
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 9,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                var now = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 10,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                require(old.stageOne().equals(now.stageOne()) && old.stageTwo().equals(now.stageTwo()),
                        "Furnishing stages changed: " + descriptor.templateId());
                require(old.base().size() == now.base().size(), "Roof fix changed the footprint");
                int changes = 0;
                for (int i = 0; i < now.base().size(); i++) {
                    var a = old.base().get(i); var b = now.base().get(i);
                    Direction expected = AuthoredStairRefinements.expectedDirection(descriptor.templateId(), a);
                    require(a.x() == b.x() && a.y() == b.y() && a.z() == b.z() && a.phase() == b.phase(),
                            "Roof fix reordered/relocated a cell");
                    require(b.state().equals(expected == null ? a.state() : a.state().setValue(StairBlock.FACING, expected)),
                            "Unexpected edit: " + descriptor.templateId() + "/" + b);
                    if (!a.equals(b)) changes++;
                }
                boolean targeted = descriptor.templateId().equals("mine_adit_03") || descriptor.templateId().equals("warehouse_wharf_04");
                require(targeted ? changes > 20 : changes == 0, "Wrong slope scope: " + descriptor.templateId());
                corrected += changes;
                for (var stage : List.of(now.base(), now.stageOne(), now.stageTwo())) {
                    for (var cell : stage) {
                        var state = cell.state();
                        if (!(state.getBlock() instanceof StairBlock)) continue;
                        checked++;
                        // Full rotation/mirror matrix applies equally to uphill roofs and the
                        // deliberately opposite-facing seat backs and inverted decorative knees.
                        for (Rotation rotation : Rotation.values()) {
                            for (Mirror mirror : Mirror.values()) {
                                var transformed = state.mirror(mirror).rotate(rotation);
                                Direction mirrored = mirror.mirror(state.getValue(StairBlock.FACING));
                                require(transformed.getValue(StairBlock.FACING) == rotation.rotate(mirrored),
                                        "Stair transform changed direction incorrectly");
                                require(transformed.getValue(StairBlock.HALF) == state.getValue(StairBlock.HALF),
                                        "Stair transform inverted furniture/roof");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("PASS catalog stair audit: 52 masters x 5 dialects, " + checked
                + " stairs, full rotation/mirror matrix; " + corrected + " reviewed slope corrections; all furniture unchanged");
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new IllegalStateException(message);
    }
}
