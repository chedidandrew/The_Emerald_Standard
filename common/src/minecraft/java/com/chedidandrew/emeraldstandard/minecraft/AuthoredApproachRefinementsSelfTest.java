package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredApproachRefinements.Outcome;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Focused production-state tests; called only from the no-world catalog self-test. */
final class AuthoredApproachRefinementsSelfTest {
    private AuthoredApproachRefinementsSelfTest() {
    }

    static void run() {
        Materials materials = new Materials(VillageArchitecture.BiomeDialect.PLAINS,
                Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB,
                Blocks.OAK_FENCE, Blocks.CHISELED_STONE_BRICKS, Blocks.OAK_DOOR,
                Blocks.STONE_BRICK_STAIRS, Blocks.BRICKS);
        for (int outermost : new int[] {-2, -4, -6, -7}) {
            Metadata metadata = metadata(3);
            Builder stage = strip(outermost, -1, 3, Set.of());
            List<Cell> previous = stage.values();
            Outcome result = AuthoredApproachRefinements.finish(stage, metadata, materials);
            require(result == (outermost == -7
                            ? Outcome.FINISHED_WITHIN_ENVELOPE : Outcome.FINISHED),
                    "Clear approach did not finish at " + outermost);
            int stairZ = Math.max(-7, outermost - 1);
            for (int x = 4; x <= 6; x++) {
                Cell stair = stage.cellAt(new BlockPos(x, 0, stairZ));
                require(stair != null && stair.state().is(materials.entryStairs())
                                && stair.state().getValue(StairBlock.FACING) == Direction.SOUTH
                                && stair.state().getValue(StairBlock.HALF) == Half.BOTTOM,
                        "Approach lost its complete supported ascending row");
            }
            for (int z = stairZ + 1; z < 0; z++) {
                require(stage.cellAt(new BlockPos(5, 0, z)).state().is(Blocks.DIRT_PATH),
                        "Approach dirt-path center was lost");
                for (int x : new int[] {4, 6}) {
                    require(stage.cellAt(new BlockPos(x, 0, z)).state()
                                    .is(materials.foundation()),
                            "Approach side paver was not finished masonry");
                }
            }
            require(stage.values().stream().allMatch(cell -> cell.z() >= -7 && cell.y() == 0),
                    "Approach escaped its original front/vertical envelope");
            require(previous.stream().filter(cell -> cell.x() == 5 && cell.z() > stairZ)
                            .allMatch(cell -> cell.equals(stage.cellAt(
                                    new BlockPos(cell.x(), cell.y(), cell.z())))),
                    "Approach rewrote retained dirt-center states");
        }

        for (int y = 0; y <= 2; y++) {
            for (int x = 4; x <= 6; x++) {
                Builder stage = strip(-4, -2, 3, Set.of(new BlockPos(x, y, -5)));
                List<Cell> previous = stage.values();
                require(AuthoredApproachRefinements.finish(stage, metadata(3), materials)
                                == Outcome.OBSTRUCTED && previous.equals(stage.values()),
                        "Occupied lane/headroom must skip the whole transition atomically");
            }
        }
        Builder obstructedStrip = strip(-4, -2, 3, Set.of(new BlockPos(4, 2, -3)));
        List<Cell> previous = obstructedStrip.values();
        require(AuthoredApproachRefinements.finish(obstructedStrip, metadata(3), materials)
                        == Outcome.OBSTRUCTED && previous.equals(obstructedStrip.values()),
                "Side-lane standing clearance must be preserved along the entire strip");

        Builder deepPorch = strip(-5, -3, 3, Set.of(new BlockPos(5, 0, -2)));
        require(AuthoredApproachRefinements.finish(deepPorch, metadata(3), materials)
                        == Outcome.FINISHED
                        && deepPorch.isOccupied(new BlockPos(5, 0, -2))
                        && deepPorch.cellAt(new BlockPos(5, 0, -6)).state()
                                .is(materials.entryStairs()),
                "Actual path strip must locate the transition beyond a deeper existing porch");

        Builder tooShort = strip(-7, -7, 3, Set.of());
        previous = tooShort.values();
        require(AuthoredApproachRefinements.finish(tooShort, metadata(3), materials)
                        == Outcome.NO_ROOM_WITHIN_ENVELOPE && previous.equals(tooShort.values()),
                "Envelope fallback must leave at least one whole dirt-centered path row");
        Builder incomplete = strip(-4, -2, 3, Set.of());
        incomplete.remove(4, 0, -4);
        previous = incomplete.values();
        require(AuthoredApproachRefinements.finish(incomplete, metadata(3), materials)
                        == Outcome.NO_COMPLETE_STRIP && previous.equals(incomplete.values()),
                "Incomplete three-wide strip must not acquire a partial stair transition");
        for (int revision : new int[] {1, 2, 10}) {
            Builder frozen = strip(-4, -2, revision, Set.of());
            previous = frozen.values();
            require(AuthoredApproachRefinements.finish(frozen, metadata(revision), materials)
                            == Outcome.UNCHANGED_REVISION && previous.equals(frozen.values()),
                    "Approach helper must not alter historical or unknown revisions");
        }
        System.out.println("PASS revision-3 raised approach tests (depth, bounds, three lanes, "
                + "headroom, obstruction and frozen revisions)");
    }

    static void reportCanonicalCoverage() {
        int finished = 0;
        List<String> skipped = new ArrayList<>();
        for (VillageProsperityEngine.ProjectType type : VillageProsperityEngine.ProjectType.values()) {
            for (VillageArchitecture.BlueprintDescriptor descriptor : VillageArchitecture.blueprints(type)) {
                if (descriptor.templateRevision() != AuthoredVillageStructures.LATEST_TEMPLATE_REVISION) {
                    continue;
                }
                var blueprint = AuthoredVillageStructures.plan(type, descriptor.templateId(), descriptor.templateRevision(),
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_RESTRAINED,
                        VillageArchitecture.Character.RUSTIC, VillageArchitecture.BiomeDialect.PLAINS);
                int center = blueprint.width() / 2;
                boolean hasThreeWideStep = blueprint.stageOne().stream()
                        .filter(cell -> cell.x() == center && cell.y() == 0 && cell.z() < 0
                                && cell.state().is(blueprint.materials().entryStairs()))
                        .anyMatch(centerStair -> {
                            for (int x : new int[] {center - 1, center + 1}) {
                                if (blueprint.stageOne().stream().noneMatch(cell ->
                                        cell.x() == x && cell.y() == 0 && cell.z() == centerStair.z()
                                                && cell.state().equals(centerStair.state()))) {
                                    return false;
                                }
                            }
                            return true;
                        });
                if (hasThreeWideStep) {
                    finished++;
                } else {
                    skipped.add(descriptor.templateId());
                }
            }
        }
        System.out.println("Raised approach canonical coverage: " + finished + "/52; "
                + "safely unchanged optional approaches: " + skipped);
    }

    private static Builder strip(int minZ, int maxZ, int revision, Set<BlockPos> blocked) {
        Builder stage = new Builder(blocked);
        stage.templateRevision = revision;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = 4; x <= 6; x++) {
                stage.put(Phase.FOUNDATION, x, 0, z, Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        return stage;
    }

    private static Metadata metadata(int revision) {
        Metadata metadata = new Metadata();
        metadata.width = 11;
        metadata.templateRevision = revision;
        return metadata;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
