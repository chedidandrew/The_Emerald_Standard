package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.WallBlock;

/** Protects all non-smithy plans and the courtyard's existing work areas from roof edits. */
final class AuthoredSmithyRoofSelfTest {
    private AuthoredSmithyRoofSelfTest() { }

    static void run() {
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 6,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                var now = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 7,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                require(old.stageOne().equals(now.stageOne()) && old.stageTwo().equals(now.stageTwo()),
                        "Dressing changed: " + descriptor.templateId());
                if (!descriptor.templateId().equals("smithy_courtyard_01")) {
                    require(old.base().equals(now.base()), "Unrelated master changed: " + descriptor.templateId());
                    continue;
                }
                Map<BlockPos, Cell> before = index(old.base()), after = index(now.base());
                var positions = new HashSet<>(before.keySet());
                positions.addAll(after.keySet());
                int changes = 0;
                for (var pos : positions) {
                    if (java.util.Objects.equals(before.get(pos), after.get(pos))) continue;
                    changes++;
                    require(pos.getY() >= 5 && pos.getY() <= 10 && pos.getX() >= 0
                            && pos.getX() <= 16 && pos.getZ() >= 3 && pos.getZ() <= 12,
                            "Roof edit escaped upper envelope: " + pos);
                }
                require(changes > 100, "Smithy still has plain canopy sheets");
                for (int x : new int[] {13, 15}) {
                    require(after.get(new BlockPos(x, 9, 10)).state().getBlock() instanceof WallBlock,
                            "Missing slim twin chimney pots");
                    require(!after.containsKey(new BlockPos(x, 10, 10)), "Tall old flue remains");
                }
                require(after.containsKey(new BlockPos(2, 8, 5))
                        && after.containsKey(new BlockPos(13, 8, 7)), "Missing pitched wing ridge");
            }
        }
        System.out.println("PASS courtyard smithy roof: five dialects; 51 other masters and all furnishing stages unchanged");
        verifyLowProfileEaves();
    }

    private static void verifyLowProfileEaves() {
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 8,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                var now = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 9,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                if (!descriptor.templateId().equals("smithy_courtyard_01")) {
                    require(old.base().equals(now.base()), "Eave refinement changed another master: " + descriptor.templateId());
                    require(old.stageOne().equals(now.stageOne()) && old.stageTwo().equals(now.stageTwo()),
                            "Unrelated furnishing stage changed: " + descriptor.templateId());
                    continue;
                }
                requireRetainedStage(old.stageOne(), now.stageOne());
                requireRetainedStage(old.stageTwo(), now.stageTwo());
                Map<BlockPos, Cell> before = index(old.base()), after = index(now.base());
                var positions = new HashSet<>(before.keySet());
                positions.addAll(after.keySet());
                int changed = 0;
                for (BlockPos pos : positions) {
                    if (java.util.Objects.equals(before.get(pos), after.get(pos))) continue;
                    changed++;
                    boolean newSconce = pos.equals(new BlockPos(0, 3, 2)) || pos.equals(new BlockPos(16, 3, 5));
                    if (newSconce) {
                        require(!before.containsKey(pos) && after.get(pos).state().is(net.minecraft.world.level.block.Blocks.WALL_TORCH),
                                "Exterior sconce replaced an existing fixture: " + pos);
                        continue;
                    }
                    require(pos.getY() >= 5 && pos.getY() <= 8 && pos.getX() >= -1
                                    && pos.getX() <= 17 && pos.getZ() >= 2 && pos.getZ() <= 13,
                            "Low roof edit escaped its envelope: " + pos);
                    Cell prior = before.get(pos);
                    require(prior == null || (!prior.state().is(old.materials().chimney())
                                    && !(prior.state().getBlock() instanceof WallBlock)
                                    && prior.state().getLightEmission() == 0),
                            "Eave pass changed chimney or light: " + pos);
                }
                require(changed > 100, "Roof cap and eaves were not emitted");
                for (int[] sample : new int[][] {{-1, 2}, {17, 13}, {6, 4}, {10, 7}, {8, 8}}) {
                    Cell eave = after.get(new BlockPos(sample[0], 5, sample[1]));
                    require(eave != null && eave.state().is(now.materials().roofSlab())
                                    && eave.state().getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                                    == net.minecraft.world.level.block.state.properties.SlabType.TOP,
                            "Missing continuous top-slab fascia: " + dialect + '/' + java.util.Arrays.toString(sample));
                }
                for (int x = 1; x <= 4; x++) {
                    require(after.get(new BlockPos(x, 7, 5)).state().is(now.materials().roofSlab()),
                            "Missing broad low roof cap");
                    require(!after.containsKey(new BlockPos(x, 8, 5)), "Old tall ridge remains");
                }
            }
        }
        System.out.println("PASS low smithy roof and continuous slab eaves: five dialects; 51 other masters unchanged; existing interiors, chimneys and furnishings retained, bounded eave sconces added");
    }

    private static void requireRetainedStage(java.util.List<Cell> before, java.util.List<Cell> after) {
        require(after.containsAll(before), "Eave lighting moved or removed an existing furnishing");
        for (Cell added : after) {
            if (before.contains(added)) continue;
            // Later-stage rear paving creates one additional covered floor. Its normal lighting
            // pass may add this wall-mounted sconce, but cannot rearrange any old furnishing.
            require(added.x() == 7 && added.y() == 3 && added.z() == 13
                            && added.state().is(net.minecraft.world.level.block.Blocks.WALL_TORCH),
                    "Unexpected later-stage addition: " + added);
        }
    }

    private static Map<BlockPos, Cell> index(java.util.List<Cell> cells) {
        return cells.stream().collect(Collectors.toMap(c -> new BlockPos(c.x(), c.y(), c.z()), Function.identity()));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
