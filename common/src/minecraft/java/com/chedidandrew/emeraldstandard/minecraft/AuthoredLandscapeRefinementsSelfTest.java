package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

/** Exact, no-world revision-four prop tests. Old plans and unknown/player cells are not repaired. */
final class AuthoredLandscapeRefinementsSelfTest {
    private AuthoredLandscapeRefinementsSelfTest() {
    }

    static void run() {
        Materials p = materials(BiomeDialect.PLAINS);
        for (int revision : new int[] {1, 2, 3}) {
            Builder old = sample(revision);
            List<Cell> before = old.values();
            AuthoredLandscapeRefinements.refineStage(old, metadata(revision, ProjectType.HOUSE),
                    p, "house_cross_01", List.of());
            AuthoredLandscapeRefinements.finishPlanting(old, metadata(revision, ProjectType.HOUSE),
                    p, "house_cross_01", List.of());
            require(before.equals(old.values()), "Historical revision " + revision + " changed");
        }
        Builder current = sample(4);
        Set<BlockPos> positions = current.positions();
        AuthoredLandscapeRefinements.refineStage(current, metadata(4, ProjectType.HOUSE),
                p, "house_cross_01", List.of());
        require(current.positions().equals(positions), "Contact/cargo replacement expanded footprint");
        require(type(current, -3, 2, 3) == SlabType.BOTTOM,
                "Isolated upper-half cap still floats above visible fence post");
        require(type(current, -3, 2, 5) == SlabType.DOUBLE,
                "Shared cap no longer bears the block resting above it");
        require(current.values().stream().noneMatch(c -> c.state().is(Blocks.TARGET)),
                "Non-guard structure retained target decoration");
        require(type(current, -3, 0, 7) == SlabType.DOUBLE,
                "Fence still floats above a lower-half pedestal");
        List<Cell> once = current.values();
        AuthoredLandscapeRefinements.refineStage(current, metadata(4, ProjectType.HOUSE),
                p, "house_cross_01", List.of());
        require(once.equals(current.values()), "Stage finishing is not deterministic/idempotent");

        Cell previousFence = new Cell(-3, 1, 3, Blocks.OAK_FENCE.defaultBlockState(), Phase.FRAME);
        Builder later = builder(4, Set.of(new BlockPos(-3, 1, 3)));
        later.put(Phase.DECOR, -3, 2, 3, upper());
        AuthoredLandscapeRefinements.refineStage(later, metadata(4, ProjectType.HOUSE),
                p, "house_cross_01", List.of(previousFence));
        require(type(later, -3, 2, 3) == SlabType.BOTTOM
                        && later.values().size() == 1,
                "Prior-stage support was ignored or mutated");
        guardTraining(p);
        planting();
        fixtureBearings(p);
        renderedContactSamples();
        System.out.println("PASS revision-4 landscape contacts, sparse role targets, bounded "
                + "biome planting, prior-stage support and frozen revisions 1-3");
    }

    static void reportCurrentGallery() {
        int fixtures = 0;
        int targets = 0;
        int pots = 0;
        Set<String> plantedMasters = new HashSet<>();
        Set<String> additionalPlantingMasters = new HashSet<>();
        Set<String> allMasters = new HashSet<>();
        List<String> contactFindings = new ArrayList<>();
        List<String> assemblyFindings = new ArrayList<>();
        List<String> assemblyDetails = new ArrayList<>();
        for (StructureGalleryPlan.Entry entry : StructureGalleryPlan.entries()) {
            if (entry.templateRevision() < 4) {
                continue;
            }
            var blueprint = AuthoredVillageStructures.plan(entry.type(), entry.templateId(),
                    entry.templateRevision(), entry.paletteId(), entry.dressingId(),
                    entry.character(), entry.dialect(), entry.blueprintSignature());
            Map<BlockPos, BlockState> complete = new HashMap<>();
            Map<BlockPos, Cell> authored = new HashMap<>();
            List<List<Cell>> stages = List.of(blueprint.base(), blueprint.stageOne(), blueprint.stageTwo());
            for (int index = 0; index <= entry.visualStage(); index++) {
                for (Cell cell : stages.get(index)) {
                    complete.put(new BlockPos(cell.x(), cell.y(), cell.z()), cell.state());
                    authored.put(new BlockPos(cell.x(), cell.y(), cell.z()), cell);
                }
                for (var cell : complete.entrySet()) {
                    if (cell.getValue().getBlock() instanceof SlabBlock
                            && cell.getValue().getValue(SlabBlock.TYPE) == SlabType.TOP) {
                        require(!(complete.getOrDefault(cell.getKey().below(), Blocks.AIR.defaultBlockState())
                                        .getBlock() instanceof FenceBlock),
                                entry.templateId() + " retains a visible fence/cap gap at " + cell.getKey());
                    }
                }
                long count = complete.values().stream().filter(s -> s.is(Blocks.TARGET)).count();
                require(entry.type() == ProjectType.GUARD_POST ? count <= 1 : count == 0,
                        entry.templateId() + " has inappropriate/ubiquitous TARGET props: " + count);
                for (BlockPos floating : floatingDecorativeCells(authored, complete)) {
                    contactFindings.add(entry.index() + " " + entry.templateId() + " stage" + index
                            + " " + floating.toShortString() + " " + complete.get(floating)
                            + " BELOW=" + complete.get(floating.below())
                            + " ABOVE=" + complete.get(floating.above())
                            + " NORTH=" + complete.get(floating.north())
                            + " SOUTH=" + complete.get(floating.south())
                            + " EAST=" + complete.get(floating.east())
                            + " WEST=" + complete.get(floating.west()));
                }
                Set<BlockPos> unanchored = unanchoredAssemblies(complete, authored);
                if (!unanchored.isEmpty()) {
                    assemblyFindings.add(entry.index() + " " + entry.templateId() + " stage" + index
                            + " unanchored=" + unanchored.size() + " "
                            + unanchored.stream().sorted(java.util.Comparator.comparingInt((BlockPos pos) -> pos.getY())
                                    .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                                    .limit(12).map(BlockPos::toShortString).toList());
                    if (entry.dialect() == BiomeDialect.PLAINS && index == 0) {
                        for (BlockPos cell : unanchored) {
                            assemblyDetails.add(entry.index() + " " + entry.templateId() + " "
                                    + cell.toShortString() + " " + complete.get(cell)
                                    + " BELOW=" + complete.get(cell.below())
                                    + " ABOVE=" + complete.get(cell.above())
                                    + " NORTH=" + complete.get(cell.north())
                                    + " SOUTH=" + complete.get(cell.south())
                                    + " EAST=" + complete.get(cell.east())
                                    + " WEST=" + complete.get(cell.west()));
                        }
                    }
                }
            }
            LevelReader view = survivalView(complete);
            for (var cell : complete.entrySet()) {
                if (cell.getValue().getBlock() instanceof FlowerPotBlock) {
                    require(cell.getValue().canSurvive(view, cell.getKey()),
                            "Actual pot survival predicate rejected " + entry.templateId() + " " + cell.getKey());
                    pots++;
                }
            }
            allMasters.add(entry.templateId());
            if (complete.values().stream().anyMatch(AuthoredLandscapeRefinementsSelfTest::greenery)) {
                plantedMasters.add(entry.templateId());
            }
            // Added pairs occupy pre-existing support pads but absent upper cells in revision 3.
            var historical = AuthoredVillageStructures.plan(entry.type(), entry.templateId(), 3,
                    entry.paletteId(), entry.dressingId(), entry.character(), entry.dialect(),
                    entry.blueprintSignature());
            Set<BlockPos> oldPositions = new HashSet<>();
            for (List<Cell> oldStage : List.of(historical.base(), historical.stageOne(), historical.stageTwo())) {
                oldStage.forEach(c -> oldPositions.add(new BlockPos(c.x(), c.y(), c.z())));
            }
            if (complete.entrySet().stream().anyMatch(c -> greenery(c.getValue())
                    && !oldPositions.contains(c.getKey()))) {
                additionalPlantingMasters.add(entry.templateId());
            }
            targets += (int) complete.values().stream().filter(s -> s.is(Blocks.TARGET)).count();
            fixtures++;
        }
        require(fixtures > 0, "Current gallery did not exercise revision-four landscape plans");
        require(plantedMasters.equals(allMasters), "Some authored masters have no living/potted planting: "
                + allMasters.stream().filter(id -> !plantedMasters.contains(id)).toList());
        System.out.println("PASS revision-4 gallery prop census: " + fixtures
                + " fixtures; " + targets + " sparse guard-training targets; no upper-slab/fence gaps; "
                + pots + " actual pot survival checks; greenery " + plantedMasters.size() + "/"
                + allMasters.size() + " masters, new planting pads " + additionalPlantingMasters.size());
        System.out.println("Rendered decorative contact census: " + contactFindings.size()
                + " candidate isolated cells; first examples: "
                + contactFindings.stream().distinct().limit(6).toList());
        try {
            java.nio.file.Path report = java.nio.file.Path.of("build", "reports", "authored-landscape-contacts.txt");
            java.nio.file.Files.createDirectories(report.getParent());
            java.nio.file.Files.write(report, contactFindings);
            java.nio.file.Files.write(report.resolveSibling("authored-landscape-assemblies.txt"), assemblyFindings);
            java.nio.file.Files.write(report.resolveSibling("authored-landscape-assembly-details.txt"), assemblyDetails);
            System.out.println("Detailed rendered-contact diagnostic report: " + report.toAbsolutePath());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not write rendered-contact diagnostic report", failure);
        }
        System.out.println("Whole-assembly ground-contact census: " + assemblyFindings.size()
                + " snapshots with unanchored shape components; first examples: "
                + assemblyFindings.stream().limit(4).toList());
        require(contactFindings.isEmpty(), "Current authored fixtures retain physically isolated blocks: "
                + contactFindings.stream().limit(4).toList());
        require(assemblyFindings.isEmpty(), "Current authored fixtures retain unanchored block assemblies: "
                + assemblyFindings.stream().limit(4).toList());
    }

    private static void guardTraining(Materials p) {
        Builder guard = builder(4, Set.of());
        for (int z : new int[] {3, 6}) {
            guard.put(Phase.DECOR, -3, 1, z, Blocks.TARGET.defaultBlockState());
            guard.put(Phase.FRAME, -3, 2, z, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
            guard.put(Phase.FRAME, -3, 1, z - 1, Blocks.OAK_FENCE.defaultBlockState());
        }
        guard.put(Phase.DECOR, 5, 1, 4, Blocks.TARGET.defaultBlockState());
        Metadata metadata = metadata(4, ProjectType.GUARD_POST);
        AuthoredLandscapeRefinements.refineStage(guard, metadata, p, "guard_watch_01", List.of());
        require(guard.values().stream().filter(c -> c.state().is(Blocks.TARGET)).count() == 1,
                "Guard training should retain exactly one composed outdoor target");
        require(!guard.cellAt(new BlockPos(5, 1, 4)).state().is(Blocks.TARGET),
                "Generic indoor guard target should become stored training hay");
        Builder later = builder(4, guard.positions());
        later.put(Phase.DECOR, -3, 1, 9, Blocks.TARGET.defaultBlockState());
        later.put(Phase.FRAME, -3, 2, 9, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
        later.put(Phase.FRAME, -3, 1, 8, Blocks.OAK_FENCE.defaultBlockState());
        AuthoredLandscapeRefinements.refineStage(later, metadata, p, "guard_watch_01", guard.values());
        require(later.values().stream().noneMatch(c -> c.state().is(Blocks.TARGET)),
                "Later stage duplicated an existing training target");
    }

    private static void planting() {
        for (BiomeDialect dialect : BiomeDialect.values()) {
            Materials p = materials(dialect);
            Builder stage = pads(Set.of());
            AuthoredLandscapeRefinements.finishPlanting(stage, metadata(4, ProjectType.HOUSE),
                    p, "house_cross_01", List.of());
            require(stage.values().size() == 4, "Two safe pads did not get exactly one planted pair");
            for (Cell cell : stage.values()) {
                require(cell.y() <= 1 && cell.x() == -3 && cell.z() >= 3 && cell.z() <= 4,
                        "Planting escaped existing support-pad bounds");
                if (cell.state().getBlock() instanceof LeavesBlock) {
                    require(cell.state().getValue(LeavesBlock.PERSISTENT), "Decorative shrub will decay");
                    require(dialect != BiomeDialect.DESERT, "Desert received a lush hedge cluster");
                }
            }
            require(stage.values().stream().anyMatch(c -> c.state().getBlock() instanceof FlowerPotBlock),
                    "Planted group lost its small clay-pot component");
        }
        Builder blocked = pads(Set.of(new BlockPos(-3, 2, 4)));
        List<Cell> before = blocked.values();
        AuthoredLandscapeRefinements.finishPlanting(blocked, metadata(4, ProjectType.HOUSE),
                materials(BiomeDialect.PLAINS), "house_cross_01", List.of());
        require(before.equals(blocked.values()), "Blocked group was only partially placed");
        Builder access = pads(Set.of());
        Metadata metadata = metadata(4, ProjectType.HOUSE);
        metadata.accessTargets.add(new BlockPos(-2, 1, 3));
        AuthoredLandscapeRefinements.finishPlanting(access, metadata, materials(BiomeDialect.PLAINS),
                "house_cross_01", List.of());
        require(access.values().size() == 2, "Planting obstructed a workstation access route");
        Builder path = pads(Set.of());
        path.force(Phase.FOUNDATION, -3, 0, 3, Blocks.DIRT_PATH.defaultBlockState());
        AuthoredLandscapeRefinements.finishPlanting(path, metadata(4, ProjectType.HOUSE),
                materials(BiomeDialect.PLAINS), "house_cross_01", List.of());
        require(path.values().size() == 2, "Planting claimed a dirt path");
        Builder industrial = builder(4, Set.of());
        Metadata industrialMetadata = metadata(4, ProjectType.WAREHOUSE);
        industrialMetadata.height = 10;
        AuthoredLandscapeRefinements.finishPlanting(industrial, industrialMetadata,
                materials(BiomeDialect.TAIGA), "warehouse_gabled_03", List.of());
        require(industrial.values().size() == 4
                        && industrial.values().stream().allMatch(c -> c.x() == -2 && c.y() <= 1),
                "Unplanted industrial master did not get one bounded supported two-pot bed");
    }

    private static Builder sample(int revision) {
        Builder builder = builder(revision, Set.of());
        for (int z : new int[] {3, 5}) {
            builder.put(Phase.FRAME, -3, 1, z, Blocks.OAK_FENCE.defaultBlockState());
            builder.put(Phase.DECOR, -3, 2, z, upper());
        }
        builder.put(Phase.FRAME, -3, 3, 5, Blocks.OAK_PLANKS.defaultBlockState());
        builder.put(Phase.FOUNDATION, -3, 0, 7, Blocks.SPRUCE_SLAB.defaultBlockState());
        builder.put(Phase.FRAME, -3, 1, 7, Blocks.OAK_FENCE.defaultBlockState());
        builder.put(Phase.DECOR, 4, 1, 4, Blocks.TARGET.defaultBlockState());
        return builder;
    }

    private static void fixtureBearings(Materials p) {
        Builder stage = builder(4, Set.of());
        stage.put(Phase.FOUNDATION, -3, 0, 3, Blocks.DIRT_PATH.defaultBlockState());
        stage.put(Phase.FOUNDATION, -3, 0, 4, Blocks.DIRT_PATH.defaultBlockState());
        stage.put(Phase.DECOR, -3, 1, 3, Blocks.MOSS_CARPET.defaultBlockState());
        stage.put(Phase.FOUNDATION, 4, 0, 4, Blocks.SPRUCE_SLAB.defaultBlockState());
        stage.put(Phase.DECOR, 4, 1, 4, Blocks.FLOWER_POT.defaultBlockState());
        stage.put(Phase.FOUNDATION, 6, 0, 4, Blocks.STONE_BRICKS.defaultBlockState());
        stage.put(Phase.FIXTURE, 6, 1, 4, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(GrindstoneBlock.FACE, AttachFace.WALL));
        stage.put(Phase.FRAME, 8, 3, 4, Blocks.IRON_CHAIN.defaultBlockState());
        stage.put(Phase.FIXTURE, 8, 2, 4, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(GrindstoneBlock.FACE, AttachFace.WALL));
        stage.put(Phase.DECOR, 10, 3, 4, upper());
        stage.put(Phase.FRAME, 11, 3, 4, Blocks.OAK_FENCE.defaultBlockState());
        AuthoredLandscapeRefinements.refineStage(stage, metadata(4, ProjectType.WAREHOUSE),
                p, "warehouse_gabled_03", List.of());
        require(stage.cellAt(new BlockPos(-3, 0, 3)).state().is(Blocks.MOSS_BLOCK),
                "Moss carpet retained a 1/16 gap over dirt path");
        require(stage.cellAt(new BlockPos(-3, 0, 4)).state().is(Blocks.DIRT_PATH),
                "Unoccupied dirt walking lane was changed");
        require(type(stage, 4, 0, 4) == SlabType.DOUBLE,
                "Flower pot retained a half-block bearing gap");
        require(stage.cellAt(new BlockPos(6, 1, 4)).state().getValue(GrindstoneBlock.FACE) == AttachFace.FLOOR,
                "Floor-mounted work stone retained a wall-only bracket");
        require(stage.cellAt(new BlockPos(8, 2, 4)).state().getValue(GrindstoneBlock.FACE) == AttachFace.CEILING,
                "Chain-mounted counterweight retained a wall-only bracket");
        require(type(stage, 10, 3, 4) == SlabType.DOUBLE,
                "Rack shelf did not gain a full lateral fence-bearing face");

        Builder market = builder(4, Set.of());
        market.put(Phase.FIXTURE, 14, 1, 10, Blocks.CHEST.defaultBlockState());
        market.put(Phase.DECOR, 14, 2, 10, upper());
        market.put(Phase.FIXTURE, 14, 1, 16, Blocks.CHEST.defaultBlockState());
        market.put(Phase.DECOR, 14, 2, 16, upper());
        market.put(Phase.FRAME, 14, 3, 16, Blocks.OAK_PLANKS.defaultBlockState());
        AuthoredLandscapeRefinements.refineStage(market, metadata(4, ProjectType.MARKET_SQUARE),
                p, "market_bazaar_05", List.of());
        require(market.cellAt(new BlockPos(14, 2, 10)) == null
                        && market.cellAt(new BlockPos(14, 1, 10)).state().is(Blocks.CHEST),
                "Useless floating display cap was not removed without disturbing its chest");
        require(market.cellAt(new BlockPos(14, 2, 16)) != null,
                "Shared stall cap was removed");
        Builder crane = builder(4, Set.of());
        crane.put(Phase.FIXTURE, 10, 3, 7, Blocks.GRINDSTONE.defaultBlockState());
        crane.put(Phase.FRAME, 10, 4, 7, Blocks.IRON_CHAIN.defaultBlockState());
        crane.put(Phase.FRAME, 10, 5, 7, Blocks.IRON_CHAIN.defaultBlockState());
        AuthoredLandscapeRefinements.refineStage(crane, metadata(4, ProjectType.WAREHOUSE),
                p, "warehouse_crane_02", List.of());
        Map<BlockPos, BlockState> craneStates = new HashMap<>();
        crane.values().forEach(c -> craneStates.put(new BlockPos(c.x(), c.y(), c.z()), c.state()));
        require(crane.cellAt(new BlockPos(10, 4, 7)).state().is(p.timber())
                        && touchesNeighbor(new BlockPos(10, 3, 7), craneStates, survivalView(craneStates)),
                "Crane mounting prongs still miss the supporting yoke");
        var productionCrane = AuthoredVillageStructures.plan(ProjectType.WAREHOUSE, "warehouse_crane_02", 4,
                "balanced", "restrained", com.chedidandrew.emeraldstandard.core.VillageArchitecture.Character.RUSTIC,
                BiomeDialect.PLAINS, 0L);
        List<Cell> actualHanger = productionCrane.base().stream().filter(c -> c.x() == 10 && c.z() == 7
                && c.y() >= 3 && c.y() <= 7).toList();
        require(actualHanger.stream().anyMatch(c -> c.y() == 4 && c.state().is(productionCrane.materials().timber())),
                "Production crane yoke was omitted: " + actualHanger);
    }

    private static Builder pads(Set<BlockPos> blocked) {
        Builder stage = builder(4, blocked);
        stage.put(Phase.FOUNDATION, -3, 0, 3, Blocks.STONE_BRICKS.defaultBlockState());
        stage.put(Phase.FOUNDATION, -3, 0, 4, Blocks.STONE_BRICKS.defaultBlockState());
        return stage;
    }

    private static Builder builder(int revision, Set<BlockPos> blocked) {
        Builder builder = new Builder(blocked);
        builder.templateRevision = revision;
        return builder;
    }

    private static Metadata metadata(int revision, ProjectType type) {
        Metadata metadata = new Metadata();
        metadata.type = type;
        metadata.templateRevision = revision;
        metadata.width = 11;
        metadata.depth = 13;
        return metadata;
    }

    private static Materials materials(BiomeDialect dialect) {
        return new Materials(dialect, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB,
                Blocks.OAK_FENCE, Blocks.CHISELED_STONE_BRICKS, Blocks.OAK_DOOR,
                Blocks.STONE_BRICK_STAIRS, Blocks.BRICKS);
    }

    private static BlockState upper() {
        return Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static SlabType type(Builder builder, int x, int y, int z) {
        return builder.cellAt(new BlockPos(x, y, z)).state().getValue(SlabBlock.TYPE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean greenery(BlockState state) {
        return state.getBlock() instanceof LeavesBlock
                || state.getBlock() instanceof FlowerPotBlock && !state.is(Blocks.FLOWER_POT)
                || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA);
    }

    private static List<BlockPos> floatingDecorativeCells(Map<BlockPos, Cell> authored,
            Map<BlockPos, BlockState> complete) {
        List<BlockPos> isolated = new ArrayList<>();
        BlockGetter view = survivalView(complete);
        // Lantern selection shapes omit part of their modeled handle. Their exact native
        // standing/hanging/chain-terminal support graph is a stronger, separately enforced test.
        AuthoredLightFixtureSupportValidator.validate(complete, "landscape attachment census");
        for (var entry : authored.entrySet()) {
            Cell cell = entry.getValue();
            BlockState state = cell.state();
            if (cell.y() <= 0 || state.getBlock() instanceof LanternBlock
                    || state.getShape(view, entry.getKey()).isEmpty()) {
                continue;
            }
            if (!touchesNeighbor(entry.getKey(), complete, view)) {
                isolated.add(entry.getKey());
            }
        }
        return isolated;
    }

    private static boolean touchesNeighbor(BlockPos position, Map<BlockPos, BlockState> complete,
            BlockGetter view) {
        List<AABB> boxes = renderedState(position, complete, view).getShape(view, position).toAabbs();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = position.relative(direction);
            BlockState other = complete.get(neighbor);
            if (other == null) {
                continue;
            }
            if (shapeContact(boxes, renderedState(neighbor, complete, view)
                    .getShape(view, neighbor).toAabbs(), direction)) {
                return true;
            }
        }
        return false;
    }

    /** A floating pair can touch each other while neither is attached to the building/ground. */
    private static Set<BlockPos> unanchoredAssemblies(Map<BlockPos, BlockState> complete) {
        return unanchoredAssemblies(complete, Map.of());
    }

    private static Set<BlockPos> unanchoredAssemblies(Map<BlockPos, BlockState> complete,
            Map<BlockPos, Cell> authored) {
        BlockGetter view = survivalView(complete);
        Map<BlockPos, List<AABB>> shapes = new HashMap<>();
        complete.forEach((pos, state) -> {
            List<AABB> shape = renderedState(pos, complete, view).getShape(view, pos).toAabbs();
            if (!shape.isEmpty()) {
                shapes.put(pos, shape);
            }
        });
        Set<BlockPos> reached = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        shapes.keySet().stream().filter(pos -> pos.getY() <= 0).forEach(pos -> {
            reached.add(pos);
            queue.add(pos);
        });
        while (!queue.isEmpty()) {
            BlockPos position = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = position.relative(direction);
                if (reached.contains(next) || !shapes.containsKey(next)) {
                    continue;
                }
                if (shapeContact(shapes.get(position), shapes.get(next), direction)
                        || lanternAttachment(position, next, complete)
                        || lanternAttachment(next, position, complete)) {
                    reached.add(next);
                    queue.add(next);
                }
            }
            Cell current = authored.get(position);
            if (current != null && current.phase() == Phase.ROOF) {
                // Consecutive stepped roof tiles occupy diagonal cells and legitimately share
                // a rendered edge. Only actual nonempty edge continuity between authored roof
                // tiles qualifies here; decorative/frame props retain face-bearing requirements.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 2) {
                                continue;
                            }
                            BlockPos next = position.offset(dx, dy, dz);
                            Cell other = authored.get(next);
                            if (!reached.contains(next) && other != null && other.phase() == Phase.ROOF
                                    && shapes.containsKey(next)
                                    && (roofTile(current.state()) || roofTile(other.state()))
                                    && sharedRoofEdge(shapes.get(position), shapes.get(next), dx, dy, dz)) {
                                reached.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        }
        Set<BlockPos> unanchored = new HashSet<>(shapes.keySet());
        unanchored.removeAll(reached);
        return unanchored;
    }

    private static boolean roofTile(BlockState state) {
        return state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock;
    }

    private static boolean sharedRoofEdge(List<AABB> first, List<AABB> second, int dx, int dy, int dz) {
        for (AABB box : first) {
            for (AABB other : second) {
                AABB shifted = other.move(dx, dy, dz);
                int positiveAxes = 0;
                boolean separated = false;
                for (Direction.Axis axis : Direction.Axis.values()) {
                    double overlap = Math.min(box.max(axis), shifted.max(axis))
                            - Math.max(box.min(axis), shifted.min(axis));
                    if (overlap < -0.0001) {
                        separated = true;
                        break;
                    }
                    if (overlap > 0.0001) {
                        positiveAxes++;
                    }
                }
                if (!separated && positiveAxes >= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean lanternAttachment(BlockPos lantern, BlockPos support,
            Map<BlockPos, BlockState> complete) {
        BlockState state = complete.get(lantern);
        // Native support and chain terminals are checked separately before this graph traversal.
        return state != null && state.getBlock() instanceof LanternBlock
                && support.equals(state.getValue(LanternBlock.HANGING) ? lantern.above() : lantern.below());
    }

    private static boolean shapeContact(List<AABB> boxes, List<AABB> others, Direction direction) {
        for (AABB box : boxes) {
            for (AABB adjacent : others) {
                AABB translated = adjacent.move(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                Direction.Axis axis = direction.getAxis();
                boolean face = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? Math.abs(box.max(axis) - translated.min(axis)) < 0.0001
                        : Math.abs(box.min(axis) - translated.max(axis)) < 0.0001;
                if (!face) {
                    continue;
                }
                boolean overlaps = true;
                for (Direction.Axis transverse : Direction.Axis.values()) {
                    if (transverse != axis && Math.min(box.max(transverse), translated.max(transverse))
                            - Math.max(box.min(transverse), translated.min(transverse)) <= 0.0001) {
                        overlaps = false;
                    }
                }
                if (overlaps) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockState renderedState(BlockPos position, Map<BlockPos, BlockState> complete,
            BlockGetter view) {
        BlockState state = complete.get(position);
        if (!(state.getBlock() instanceof FenceBlock || state.getBlock() instanceof IronBarsBlock)) {
            return state;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPosition = position.relative(direction);
            BlockState neighbor = complete.getOrDefault(neighborPosition, Blocks.AIR.defaultBlockState());
            boolean sturdy = neighbor.isFaceSturdy(view, neighborPosition, direction.getOpposite());
            boolean attached = state.getBlock() instanceof FenceBlock fence
                    ? neighbor.getBlock() == state.getBlock()
                            || fence.connectsTo(neighbor, sturdy, direction.getOpposite())
                    : ((IronBarsBlock) state.getBlock()).attachsTo(neighbor, sturdy);
            // Use the native gate/sturdy-face predicates. Identical fence materials necessarily
            // connect, including in this headless bootstrap before data-pack tags are loaded.
            // Raw default arm states are not an accurate picture after neighbor normalization.
            state = state.setValue(switch (direction) {
                    case NORTH -> FenceBlock.NORTH;
                    case EAST -> FenceBlock.EAST;
                    case SOUTH -> FenceBlock.SOUTH;
                    case WEST -> FenceBlock.WEST;
                    default -> throw new IllegalArgumentException("Non-horizontal fence contact");
                }, attached);
        }
        return state;
    }

    private static void renderedContactSamples() {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        cells.put(BlockPos.ZERO, Blocks.OAK_FENCE.defaultBlockState());
        cells.put(BlockPos.ZERO.above(), upper());
        require(!touchesNeighbor(BlockPos.ZERO.above(), cells, survivalView(cells)),
                "Visible-shape contact incorrectly used the fence's extended collision box");
        cells.put(BlockPos.ZERO.above(), Blocks.SPRUCE_SLAB.defaultBlockState());
        require(touchesNeighbor(BlockPos.ZERO.above(), cells, survivalView(cells)),
                "Lowered cap does not visually contact the post");
        cells.clear();
        cells.put(BlockPos.ZERO, Blocks.SPRUCE_SLAB.defaultBlockState());
        cells.put(BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState());
        require(touchesNeighbor(BlockPos.ZERO, cells, survivalView(cells)),
                "A genuinely side-supported cantilever was rejected");
        cells.put(BlockPos.ZERO, Blocks.OAK_FENCE.defaultBlockState());
        cells.put(BlockPos.ZERO.east(), Blocks.OAK_FENCE.defaultBlockState());
        require(touchesNeighbor(BlockPos.ZERO, cells, survivalView(cells)),
                "A same-material connected railing arm was rejected");
        cells.clear();
        cells.put(new BlockPos(0, 3, 0), Blocks.OAK_PLANKS.defaultBlockState());
        cells.put(new BlockPos(1, 3, 0), Blocks.OAK_PLANKS.defaultBlockState());
        require(unanchoredAssemblies(cells).size() == 2,
                "A mutually touching but floating two-block assembly was admitted");
        for (int y = 0; y < 3; y++) {
            cells.put(new BlockPos(0, y, 0), Blocks.OAK_PLANKS.defaultBlockState());
        }
        require(unanchoredAssemblies(cells).isEmpty(),
                "A grounded post and side cantilever were rejected");
        cells.clear();
        cells.put(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState());
        cells.put(new BlockPos(0, 1, 0), Blocks.SPRUCE_STAIRS.defaultBlockState());
        cells.put(new BlockPos(1, 2, 0), Blocks.SPRUCE_STAIRS.defaultBlockState());
        Map<BlockPos, Cell> roof = new HashMap<>();
        cells.forEach((pos, state) -> roof.put(pos, new Cell(pos.getX(), pos.getY(), pos.getZ(),
                state, pos.getY() == 0 ? Phase.FOUNDATION : Phase.ROOF)));
        require(unanchoredAssemblies(cells, roof).isEmpty(),
                "Physically continuous stepped roof edge was rejected");
        require(unanchoredAssemblies(cells).contains(new BlockPos(1, 2, 0)),
                "Ordinary decorative stair received a roof-only edge-support waiver");
        require(!sharedRoofEdge(List.of(new AABB(0, 0, 0, 1, 0.5, 1)),
                        List.of(new AABB(0, 0, 0, 1, 1, 1)), 1, 1, 0),
                "A genuine half-block roof gap was mistaken for edge continuity");
    }

    /** Only exact block/fluid reads and interface defaults are allowed in these pure predicates. */
    private static LevelReader survivalView(Map<BlockPos, BlockState> cells) {
        return (LevelReader) java.lang.reflect.Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                new Class<?>[] {LevelReader.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getBlockState" -> cells.getOrDefault((BlockPos) args[0], Blocks.AIR.defaultBlockState());
                        case "getFluidState" -> cells.getOrDefault((BlockPos) args[0], Blocks.AIR.defaultBlockState())
                                .getFluidState();
                        case "getBlockEntity" -> null;
                        case "getHeight" -> 512;
                        case "getMinY" -> -64;
                        default -> {
                            if (method.isDefault()) {
                                yield java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                            }
                            throw new IllegalStateException("Unexpected world access in pure plant/contact test: "
                                    + method.getName());
                        }
                    };
                });
    }
}
