package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;

/** Exact before/after admission: furnishings and higher ceilings must not drift. */
final class AuthoredCeilingClearanceSelfTest {
    private AuthoredCeilingClearanceSelfTest() { }

    static void run() {
        Set<String> changed = new java.util.TreeSet<>();
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 5,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                var now = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 6,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                Map<BlockPos, Cell> before = index(old.base()), after = index(now.base());
                Set<BlockPos> positions = new HashSet<>(before.keySet());
                positions.addAll(after.keySet());
                for (BlockPos pos : positions) {
                    Cell a = before.get(pos), b = after.get(pos);
                    if (java.util.Objects.equals(a, b)) continue;
                    changed.add(descriptor.templateId());
                    require(pos.getY() == 3 || pos.getY() == 4,
                            "Non-ceiling height changed: " + descriptor.templateId() + " " + a + " -> " + b);
                    require(a == null || ((a.phase() == Phase.FRAME || a.phase() == Phase.SHELL)
                            && (a.state().is(old.materials().timber()) || a.state().is(old.materials().wall()))),
                            "Non-ceiling original changed: " + descriptor.templateId() + " " + a);
                    require(b == null || (b.phase() == Phase.FRAME && b.state().is(now.materials().timber())),
                            "Unexpected replacement: " + descriptor.templateId() + " " + b);
                }
                require(old.stageOne().equals(now.stageOne()) && old.stageTwo().equals(now.stageTwo()),
                        "Furnishing/garden stages changed: " + descriptor.templateId());
                if (descriptor.templateId().equals("house_splitwing_05"))
                    require(before.equals(after), "Already-high splitwing interior changed");
                if (descriptor.templateId().equals("house_hall_04")) {
                    for (int x = 4; x <= 6; x++) {
                        require(!after.containsKey(new BlockPos(x, 3, 6)), "Hall tie remains too low");
                        Cell raised = after.get(new BlockPos(x, 4, 6));
                        require(raised != null && raised.state().is(now.materials().timber()),
                                "Hall tie not raised to fourth course");
                    }
                }
            }
        }
        require(changed.equals(Set.of("house_hall_04", "cottage_bay_04", "cottage_garden_02",
                "cottage_hearth_01")), "Unexpected low-ceiling scope: " + changed);
        System.out.println("PASS ceiling clearance: " + changed + "; all 52 masters x five dialects compared");
    }

    private static Map<BlockPos, Cell> index(java.util.List<Cell> cells) {
        return cells.stream().collect(Collectors.toMap(c -> new BlockPos(c.x(), c.y(), c.z()), Function.identity()));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
