package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import java.util.List;
import java.util.UUID;

/** Measures actual tier-one authored solids, rather than the old nominal block estimate. */
final class ConstructionPacingSelfTest {
    static void verify() {
        for (var type : List.of(ProjectType.COTTAGE, ProjectType.WAREHOUSE, ProjectType.MINE_ENTRANCE,
                ProjectType.GRANARY, ProjectType.GUARD_POST)) {
            long minimum = Long.MAX_VALUE, maximum = 0;
            for (int i = 0; i < 12; i++) {
                var id = new UUID(79, i);
                var character = VillageArchitecture.character(id);
                var selection = VillageArchitecture.chooseBlueprint(id, 1, type, character, 1, List.of());
                var plan = AuthoredVillageStructures.plan(type, selection.templateId(), selection.templateRevision(),
                        selection.paletteId(), selection.dressingId(), character,
                        VillageArchitecture.BiomeDialect.PLAINS, selection.seed());
                long solids = java.util.stream.Stream.of(plan.base(), plan.stageOne(), plan.stageTwo())
                        .flatMap(List::stream).filter(cell -> !cell.state().isAir()).count();
                minimum = Math.min(minimum, solids); maximum = Math.max(maximum, solids);
            }
            System.out.printf("PACING tier-one %s: %d-%d authored solids; %.2f-%.2f days at 2 blocks/sec before terrain/path work%n",
                    type, minimum, maximum, minimum / 2400.0, maximum / 2400.0);
        }
    }
}
