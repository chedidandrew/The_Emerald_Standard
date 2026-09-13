package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exercises production road-light placement in the explicitly disposable server-smoke world. */
final class WalkwayLightingSelfTest {
    static void geometry() {
        for (var character : VillageArchitecture.Character.values()) for (var dialect : VillageArchitecture.BiomeDialect.values()) {
            var materials = AuthoredVillageStructures.walkwayMaterials(character, dialect);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var plan = AuthoredVillageStructures.walkwayLamp(materials, facing);
                require(plan.size() == 10, "yard-lamp shape changed");
                var lamp = plan.getLast();
                require(lamp.state().is(Blocks.LANTERN) && lamp.state().getValue(LanternBlock.HANGING)
                        && lamp.state().getLightEmission() == 15, "hanging real light must be last");
                require(lamp.x() == facing.getStepX() && lamp.z() == facing.getStepZ(), "arm faces away from walkway");
                require(plan.stream().anyMatch(c -> c.y() == 3 && c.x() == 0 && c.z() == 0
                        && c.state().is(materials.roofStairs()) && c.state().getValue(StairBlock.FACING) == facing),
                        "knee brace rotation or palette");
                require(plan.stream().anyMatch(c -> c.y() == 4 && c.x() == 0 && c.z() == 0
                        && c.state().is(materials.timber())), "village timber palette");
                int chain = -1, arm = -1;
                for (int i = 0; i < plan.size(); i++) {
                    var c = plan.get(i);
                    if (c.state().is(Blocks.IRON_CHAIN)) chain = i;
                    if (c.x() == facing.getStepX() && c.z() == facing.getStepZ() && c.y() == 4) arm = i;
                }
                require(arm >= 0 && arm < chain && chain < plan.size() - 1, "supports before chain/lantern");
                require(plan.stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).distinct().count() == 10,
                        "overlapping lamp cells");
            }
        }
        List<BlockPos> line = java.util.stream.IntStream.range(0, 31).mapToObj(i -> new BlockPos(i, 0, 0)).toList();
        require(WalkwayLighting.stations(line).equals(List.of(6, 16, 26)), "bounded road spacing");
        require(WalkwayLighting.stations(line.subList(0, 8)).isEmpty(), "short entrances use yard lighting");
        require(WalkwayLighting.tangent(line, 6) == Direction.EAST, "route tangent");
        List<BlockPos> reverse = new ArrayList<>(line); Collections.reverse(reverse);
        require(WalkwayLighting.tangent(reverse, 6) == Direction.WEST, "reversed route");
        List<BlockPos> bend = new ArrayList<>();
        for (int i = 0; i <= 10; i++) bend.add(new BlockPos(i, 0, 0));
        for (int i = 1; i <= 20; i++) bend.add(new BlockPos(10, 0, i));
        require(WalkwayLighting.tangent(bend, 6) == Direction.EAST
                && WalkwayLighting.tangent(bend, 16) == Direction.SOUTH, "bend tangents");
        var negative = new WalkwayLightingLedger(Map.of(), List.of(new BlockPos(-17, 64, -17).asLong()));
        require(negative.nearby(new BlockPos(-16, 64, -16))
                && !negative.nearby(new BlockPos(-8, 64, -17)), "negative-coordinate receipt boundaries");
    }

    static void verify(ServerLevel level) {
        geometry();
        BlockPos origin = new BlockPos(1248, level.getMaxY() - 24, 1248);
        UUID village = UUID.fromString("89928068-98ce-42ce-91de-98204e8e1991");
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        var materials = AuthoredVillageStructures.walkwayMaterials(
                VillageArchitecture.Character.RUSTIC, VillageArchitecture.BiomeDialect.PLAINS);
        var route = java.util.stream.IntStream.range(0, 31).mapToObj(x -> origin.offset(x, 0, 0)).toList();
        var ledger = WalkwayLightingLedger.get(level);
        var old = WalkwayLightingLedger.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        net.minecraft.world.entity.Entity animal = null;
        try {
            for (int x = -3; x <= 35; x++) for (int z = -7; z <= 16; z++) for (int y = -1; y <= 7; y++) {
                BlockPos p = origin.offset(x, y, z); level.getChunk(p);
                before.put(p, level.getBlockState(p));
                level.setBlock(p, y < 0 ? Blocks.STONE.defaultBlockState() : y == 0
                        ? (z >= -1 && z <= 1 && x >= 0 && x <= 30 ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK).defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            require(WalkwayLighting.acquire(level, 1000) && !WalkwayLighting.acquire(level, 1000)
                    && !WalkwayLighting.acquire(level, 1019) && WalkwayLighting.acquire(level, 1020),
                    "global per-dimension work cap");
            String key = WalkwayLightingLedger.key(village, 1, origin.asLong());
            int writes = 0;
            for (int step = 0; step < 50 && !ledger.done(key); step++) {
                int count = WalkwayLighting.advance(level, village, 1, origin.asLong(), route, materials, List.of(), List.of(), 2);
                require(count >= 0 && count <= 2, "write budget exceeded"); writes += count;
                if (step == 2) {
                    var decoded = WalkwayLightingLedger.CODEC.parse(NbtOps.INSTANCE,
                            WalkwayLightingLedger.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow()).getOrThrow();
                    require(decoded.jobs.equals(ledger.jobs) && decoded.sites.equals(ledger.sites), "partial receipt reload");
                    level.getDataStorage().set(WalkwayLightingLedger.TYPE, decoded); ledger = decoded;
                }
            }
            require(ledger.done(key) && writes == 30, "three complete incremental lamps on old finished path: writes="
                    + writes + ", job=" + ledger.jobs.get(key) + ", sites=" + ledger.sites
                    + ", surfaceY=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            origin.getX() + 6, origin.getZ())
                    + ", surface=" + level.getBlockState(new BlockPos(origin.getX() + 6,
                            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    origin.getX() + 6, origin.getZ()) - 1, origin.getZ())));
            List<BlockPos> lamps = before.keySet().stream().filter(p -> level.getBlockState(p).is(Blocks.LANTERN)).toList();
            require(lamps.size() == 3, "missing street light");
            for (BlockPos p : lamps) {
                require(Math.abs(p.getZ() - origin.getZ()) == 2, "light does not face inward from roadside");
                require(level.getBlockState(p).canSurvive(level, p), "unsupported hanging lantern");
            }
            for (BlockPos p : route) require(level.getBlockState(p).is(Blocks.DIRT_PATH)
                    && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir(), "walkway obstruction");
            require(level.getBlockState(origin.offset(6, 0, 3)).is(Blocks.GRASS_BLOCK), "terrain excavated for lamp");
            level.destroyBlock(lamps.getFirst(), false);
            var decoded = WalkwayLightingLedger.CODEC.parse(NbtOps.INSTANCE,
                    WalkwayLightingLedger.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow()).getOrThrow();
            level.getDataStorage().set(WalkwayLightingLedger.TYPE, decoded); ledger = decoded;
            require(WalkwayLighting.advance(level, village, 1, origin.asLong(), route, materials, List.of(), List.of(), 2) == 0
                    && level.getBlockState(lamps.getFirst()).isAir(), "removed light regenerated after reload");
            for (int i = 0; i < 6; i++)
                require(WalkwayLighting.advance(level, village, 2, origin.asLong(), route, materials, List.of(), List.of(), 2) == 0,
                        "shared route duplicated lighting");
            require(ledger.sites.size() == 3, "parallel route ignored permanent spacing receipts");
            var alternateRoute = java.util.stream.IntStream.range(0, 9).mapToObj(x -> origin.offset(x, 0, 8)).toList();
            for (BlockPos p : alternateRoute) level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos blocked = origin.offset(4, 1, 11);
            level.setBlock(blocked, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            String alternateKey = WalkwayLightingLedger.key(village, 4, origin.asLong());
            // First (south) verge is obstructed. Test from a fresh receipt index so an unrelated
            // earlier fixture's nearby pole doesn't hide the opposite-side behavior.
            var alternateLedger = new WalkwayLightingLedger();
            level.getDataStorage().set(WalkwayLightingLedger.TYPE, alternateLedger);
            WalkwayLighting.advance(level, village, 4, origin.asLong(), alternateRoute, materials, List.of(), List.of(), 2);
            require(alternateLedger.jobs.get(alternateKey).pending().size() == 10
                    && alternateLedger.sites.stream().allMatch(p -> BlockPos.of(p).getZ() < origin.getZ() + 8)
                    && level.getBlockState(blocked).is(Blocks.OAK_PLANKS), "opposite verge was not selected safely");
            level.getDataStorage().set(WalkwayLightingLedger.TYPE, ledger);
            level.setBlock(blocked, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            BlockPos foot = origin.offset(5, 1, 12);
            var plan = AuthoredVillageStructures.walkwayLamp(materials, Direction.NORTH);
            var search = WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(), List.of());
            require(!search.waiting() && search.pieces().size() == 10, "clear site rejected");
            var crossing = new ArrayList<>(route); crossing.add(foot.below());
            require(WalkwayLighting.preflight(level, village, 3, foot, plan, crossing, List.of(), List.of()).pieces().isEmpty(),
                    "lamp intersected a path bend or branch");
            BlockState ground = level.getBlockState(foot.below());
            level.setBlock(foot.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            require(WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(), List.of()).pieces().isEmpty(),
                    "unsupported footing accepted");
            level.setBlock(foot.below(), ground, Block.UPDATE_ALL);
            level.setBlock(foot, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            var chest = (ChestBlockEntity)level.getBlockEntity(foot);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            require(WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(), List.of()).pieces().isEmpty()
                    && chest.getItem(0).getCount() == 3, "inventory overwritten");
            chest.clearContent(); level.setBlock(foot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            try (var ignored = VillageDevelopmentProtection.register(context -> false)) {
                require(WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(), List.of()).pieces().isEmpty(),
                        "claim veto ignored");
            }
            animal = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            require(animal != null, "cow fixture"); animal.setPos(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
            level.addFreshEntity(animal);
            require(WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(), List.of()).waiting(),
                    "occupied site was overwritten");
            animal.discard(); animal = null;
            level.setBlock(foot, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            require(WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(), List.of()).pieces().isEmpty(),
                    "water overwritten");
            level.setBlock(foot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            require(WalkwayLighting.preflight(level, village, 3, origin.offset(5, 1, 0), plan, route, List.of(), List.of())
                    .pieces().isEmpty(), "road center accepted");
            var lot = new com.chedidandrew.emeraldstandard.core.EconomyService.VillageProjectLot(
                    foot.offset(-1, -1, -1).asLong(), foot.offset(1, 5, 1).asLong());
            require(WalkwayLighting.preflight(level, village, 3, foot, plan, route, List.of(lot), List.of()).pieces().isEmpty(),
                    "reserved building lot ignored");
            BlockPos unloaded = new BlockPos(8000000, foot.getY(), 8000000);
            require(!level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4), "unloaded fixture");
            require(WalkwayLighting.preflight(level, village, 3, unloaded, plan, route, List.of(), List.of()).waiting()
                    && !level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4), "lamp forced a chunk load");

            String pendingKey = WalkwayLightingLedger.key(village, 3, origin.asLong());
            ledger.record(pendingKey, new WalkwayLightingLedger.Job(0, search.pieces(), 0, false));
            require(WalkwayLighting.placePending(level, village, 3, pendingKey, ledger.jobs.get(pendingKey),
                    List.of(), List.of(), 2) == 2, "foundation-first progress");
            BlockPos first = BlockPos.of(search.pieces().getFirst().position());
            level.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            require(WalkwayLighting.placePending(level, village, 3, pendingKey, ledger.jobs.get(pendingKey),
                    List.of(), List.of(), 2) == 0 && ledger.jobs.get(pendingKey).pending().isEmpty(),
                    "modified lamp support rebuilt or floating arm continued");
            System.out.println("PASS walkway lighting: 80 palette/orientation plans, old-path backfill, budgets, reload, no regeneration,"
                    + " shared roads, clear passage, property, claims, entities, water, lots, unloaded chunks and broken supports");
        } catch (Exception ex) { throw new IllegalStateException("Walkway lighting regression", ex); }
        finally {
            if (animal != null) animal.discard();
            level.getDataStorage().set(WalkwayLightingLedger.TYPE, WalkwayLightingLedger.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow());
            before.forEach((p, state) -> level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
}
