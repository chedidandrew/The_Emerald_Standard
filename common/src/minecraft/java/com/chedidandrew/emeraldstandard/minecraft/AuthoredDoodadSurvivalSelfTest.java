package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;

/** No-world survival regression for authored shrubs and decorative rails, not a repair routine. */
final class AuthoredDoodadSurvivalSelfTest {
    private AuthoredDoodadSurvivalSelfTest() {
    }

    static void run() {
        exactGardenRepairScope();
        // Standalone Bootstrap does not bind datapack tags. Resolve the bundled vanilla tag
        // resource used by AzaleaBlock.mayPlaceOn instead of accepting guessed soil materials.
        Set<String> substrates = blockTag("minecraft:supports_azalea", new HashSet<>());
        require(substrates.contains("minecraft:moss_block")
                        && !substrates.contains("minecraft:stone_bricks"),
                "Exact bundled vanilla azalea substrate tag was not resolved");
        rejectBrokenSamples(substrates);
        int shrubs = 0;
        int rails = 0;
        for (StructureGalleryPlan.Entry entry : StructureGalleryPlan.entries()) {
            var plan = AuthoredVillageStructures.plan(entry.type(), entry.templateId(),
                    entry.templateRevision(), entry.paletteId(), entry.dressingId(),
                    entry.character(), entry.dialect(), entry.blueprintSignature());
            Map<BlockPos, BlockState> cells = new HashMap<>();
            List<List<Cell>> stages = List.of(plan.base(), plan.stageOne(), plan.stageTwo());
            int[] counts = {0, 0};
            for (int stage = 0; stage <= entry.visualStage(); stage++) {
                stages.get(stage).forEach(cell -> cells.put(
                        new BlockPos(cell.x(), cell.y(), cell.z()), cell.state()));
                counts = audit(entry.templateId() + "/gallery-" + entry.index()
                        + "/stage-" + stage, cells, substrates);
            }
            shrubs += counts[0];
            rails += counts[1];
        }
        // This opt-in only allows the pure bank blueprint accessor in this disposable JVM;
        // it never launches a server, creates a world, or builds a gallery.
        String property = StructureGallery.ENABLE_PROPERTY;
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "true");
            for (StructureGalleryPlan.BankEntry entry : StructureGalleryPlan.bankEntries()) {
                Map<BlockPos, BlockState> cells = new HashMap<>();
                VillageBankManager.galleryBankBlueprint(BlockPos.ZERO, entry.dialect())
                        .forEach(cell -> cells.put(cell.position(), cell.state()));
                int[] counts = audit("bank/" + entry.dialect().id(), cells, substrates);
                shrubs += counts[0];
                rails += counts[1];
            }
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
        System.out.println("PASS exact doodad survival: "
                + StructureGalleryPlan.totalStructureCount() + " current gallery fixtures; "
                + shrubs + " shrubs on vanilla-supported substrates, " + rails
                + " rigidly supported rails; cumulative stages and frozen-revision scope");
    }

    private static void exactGardenRepairScope() {
        Materials p = new Materials(VillageArchitecture.BiomeDialect.PLAINS,
                Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB,
                Blocks.OAK_FENCE, Blocks.CHISELED_STONE_BRICKS, Blocks.OAK_DOOR,
                Blocks.STONE_BRICK_STAIRS, Blocks.BRICKS);
        for (int revision : new int[] {1, 2, 3, 4}) {
            Builder stage = garden(revision, Set.of());
            List<Cell> before = stage.values();
            AuthoredDoodadRefinements.ensureGardenShrubSubstrates(stage, p, 5, 9);
            if (revision != 3) {
                require(before.equals(stage.values()), "Soil refinement changed frozen revision " + revision);
                continue;
            }
            for (Cell cell : before) {
                Cell after = stage.cellAt(new BlockPos(cell.x(), cell.y(), cell.z()));
                if (cell.y() == 0 && (cell.x() == 2 || cell.x() == 4)) {
                    require(after.state().is(Blocks.MOSS_BLOCK)
                                    && after.phase() == Phase.FOUNDATION,
                            "Live shrub did not receive its exact moss planting tile");
                } else {
                    require(cell.equals(after), "Soil refinement changed a border, plant or furniture cell");
                }
            }
            require(before.size() == stage.values().size(), "Soil refinement changed the motif footprint");
        }
        Builder inherited = garden(3, Set.of(new BlockPos(2, 0, 9)));
        AuthoredDoodadRefinements.ensureGardenShrubSubstrates(inherited, p, 5, 9);
        require(inherited.cellAt(new BlockPos(2, 0, 9)) == null
                        && inherited.isOccupied(new BlockPos(2, 0, 9)),
                "Soil refinement overwrote a previous-stage or protected planting base");
        for (Phase phase : new Phase[] {Phase.FIXTURE, Phase.FRAME}) {
            Builder unrelated = garden(3, Set.of());
            unrelated.force(phase, 2, 0, 9, Blocks.STONE_BRICKS.defaultBlockState());
            Cell protectedCell = unrelated.cellAt(new BlockPos(2, 0, 9));
            AuthoredDoodadRefinements.ensureGardenShrubSubstrates(unrelated, p, 5, 9);
            require(protectedCell.equals(unrelated.cellAt(new BlockPos(2, 0, 9))),
                    "Soil refinement rewrote an unrelated structural or fixture base");
        }
    }

    private static Builder garden(int revision, Set<BlockPos> blocked) {
        Builder stage = new Builder(blocked);
        stage.templateRevision = revision;
        for (int x = 2; x <= 5; x++) {
            stage.putIfFree(Phase.FOUNDATION, x, 0, 9,
                    (x == 2 || x == 5 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS)
                            .defaultBlockState());
        }
        stage.put(Phase.DECOR, 2, 1, 9, Blocks.AZALEA.defaultBlockState());
        stage.put(Phase.DECOR, 3, 1, 9, Blocks.COMPOSTER.defaultBlockState());
        stage.put(Phase.DECOR, 4, 1, 9, Blocks.FLOWERING_AZALEA.defaultBlockState());
        stage.put(Phase.DECOR, 5, 1, 9, Blocks.BARREL.defaultBlockState());
        return stage;
    }

    private static int[] audit(String id, Map<BlockPos, BlockState> cells, Set<String> substrates) {
        BlockGetter world = blockGetter(cells);
        int shrubs = 0;
        int rails = 0;
        for (var entry : cells.entrySet()) {
            BlockPos position = entry.getKey();
            BlockState state = entry.getValue();
            if (state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA)) {
                String support = BuiltInRegistries.BLOCK.getKey(
                        world.getBlockState(position.below()).getBlock()).toString();
                require(substrates.contains(support), id + " unsupported shrub at " + position
                        + " on " + support);
                shrubs++;
            } else if (state.is(Blocks.RAIL)) {
                require(Block.canSupportRigidBlock(world, position.below()),
                        id + " rail has no rigid base at " + position);
                Direction rising = switch (state.getValue(RailBlock.SHAPE)) {
                    case ASCENDING_EAST -> Direction.EAST;
                    case ASCENDING_WEST -> Direction.WEST;
                    case ASCENDING_NORTH -> Direction.NORTH;
                    case ASCENDING_SOUTH -> Direction.SOUTH;
                    default -> null;
                };
                require(rising == null || Block.canSupportRigidBlock(world, position.relative(rising)),
                        id + " ascending rail has no rigid high-side support at " + position);
                // Placement can auto-shape a straight decorative rail toward a neighbor above.
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos upper = position.relative(direction).above();
                    require(!world.getBlockState(upper).is(Blocks.RAIL)
                                    || Block.canSupportRigidBlock(world, upper.below()),
                            id + " auto-ascending rail has no high-side support at " + position);
                }
                rails++;
            }
        }
        return new int[] {shrubs, rails};
    }

    private static void rejectBrokenSamples(Set<String> substrates) {
        Map<BlockPos, BlockState> broken = new HashMap<>();
        broken.put(BlockPos.ZERO, Blocks.STONE_BRICKS.defaultBlockState());
        broken.put(BlockPos.ZERO.above(), Blocks.AZALEA.defaultBlockState());
        expectRejected(broken, substrates, "shrub on masonry");
        broken.put(BlockPos.ZERO.above(), Blocks.RAIL.defaultBlockState()
                .setValue(RailBlock.SHAPE, RailShape.ASCENDING_EAST));
        expectRejected(broken, substrates, "ascending rail without high-side support");
        broken.put(BlockPos.ZERO.above(), Blocks.RAIL.defaultBlockState());
        broken.remove(BlockPos.ZERO);
        expectRejected(broken, substrates, "rail without a base");
    }

    private static void expectRejected(Map<BlockPos, BlockState> cells, Set<String> substrates, String name) {
        boolean rejected = false;
        try {
            audit("deliberately broken " + name, cells, substrates);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected, "Survival test accepted " + name);
    }

    private static BlockGetter blockGetter(Map<BlockPos, BlockState> cells) {
        return new BlockGetter() {
            public BlockState getBlockState(BlockPos position) {
                return cells.getOrDefault(position, Blocks.AIR.defaultBlockState());
            }
            public FluidState getFluidState(BlockPos position) {
                return getBlockState(position).getFluidState();
            }
            public BlockEntity getBlockEntity(BlockPos position) {
                return null;
            }
            public int getHeight() {
                return 512;
            }
            public int getMinY() {
                return -64;
            }
        };
    }

    private static Set<String> blockTag(String id, Set<String> visiting) {
        require(visiting.add(id), "Cyclic bundled block tag: " + id);
        String[] parts = id.split(":", 2);
        String path = "/data/" + parts[0] + "/tags/block/" + parts[1] + ".json";
        Set<String> result = new HashSet<>();
        try (InputStream input = Blocks.class.getResourceAsStream(path)) {
            require(input != null, "Missing exact bundled vanilla block tag " + path);
            var document = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement value : document.getAsJsonArray("values")) {
                String child = value.isJsonPrimitive() ? value.getAsString()
                        : value.getAsJsonObject().get("id").getAsString();
                if (child.startsWith("#")) {
                    result.addAll(blockTag(child.substring(1), visiting));
                } else {
                    result.add(child);
                }
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read exact bundled block tag " + path, failure);
        } finally {
            visiting.remove(id);
        }
        return Set.copyOf(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
