package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Actual blocks and animals in the opt-in disposable server smoke world only. */
final class VillageFoodEnvironmentSelfTest {
    private VillageFoodEnvironmentSelfTest() { }

    static void verify(ServerLevel level) {
        level.getChunk(0, 0); // Fixture setup only; the production scanner never loads chunks.
        BlockPos center = new BlockPos(8, level.getMaxY() - 72, 8);
        var village = new EconomyState.VillageRecord();
        village.villageId = new UUID(83, 92); village.centerPos = center.asLong();
        var neighbor = new EconomyState.VillageRecord();
        neighbor.villageId = new UUID(84, 92); neighbor.centerPos = center.east(4).asLong();
        var neighborhoods = List.of(village, neighbor);
        double baseline = scan(level, village, List.of(village)).crops;
        double firstBaseline = scan(level, village, neighborhoods).crops;
        double secondBaseline = scan(level, neighbor, neighborhoods).crops;
        double animalBaseline = scan(level, village, List.of(village)).livestock();
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        List<Animal> animals = new ArrayList<>();
        try {
            CropBlock wheat = (CropBlock) Blocks.WHEAT;
            for (int x = -4; x < 4; x++) for (int z = -4; z < 4; z++) {
                set(level, before, center.offset(x, -1, z), Blocks.FARMLAND.defaultBlockState());
                set(level, before, center.offset(x, 0, z), wheat.getStateForAge(wheat.getMaxAge()));
            }
            require(close(scan(level, village, List.of(village)).crops - baseline, 64), "mature wheat field counted exactly");
            for (int x = -4; x < 4; x++) for (int z = -4; z < 4; z++)
                set(level, before, center.offset(x, 0, z), wheat.getStateForAge(0));
            require(close(scan(level, village, List.of(village)).crops - baseline, 16), "replanting earns a smaller growing-crop bonus");
            for (int x = -4; x < 4; x++) for (int z = -4; z < 4; z++)
                set(level, before, center.offset(x, 0, z), wheat.getStateForAge(wheat.getMaxAge()));
            double first = scan(level, village, neighborhoods).crops - firstBaseline;
            double second = scan(level, neighbor, neighborhoods).crops - secondBaseline;
            require(first > 0 && second > 0 && close(first + second, 64), "overlapping districts do not double-count one farm");

            BlockPos distant = center.offset(104, 40, 0);
            level.getChunk(distant); // Fixture only: a farm outside both old scan dimensions.
            var plot = new EconomyState.VillageProject();
            plot.originPos = center.east(90).asLong();
            plot.boundsMinPos = center.east(88).asLong(); plot.boundsMaxPos = center.east(96).asLong();
            village.projects.add(plot);
            require(!VillageFoodEnvironment.coverage(village).contains(distant), "unstarted reservations do not inflate coverage");
            plot.materializedBlocks = 1;
            require(VillageFoodEnvironment.coverage(village).contains(distant), "developed plot includes adjacent field margin");
            double expandedBaseline = scan(level, village, List.of(village)).crops;
            set(level, before, distant.below(), Blocks.FARMLAND.defaultBlockState());
            set(level, before, distant, wheat.getStateForAge(wheat.getMaxAge()));
            require(close(scan(level, village, List.of(village)).crops - expandedBaseline, 1), "full-height distant field is scanned");
            set(level, before, distant, Blocks.AIR.defaultBlockState()); village.projects.clear();

            // The distant-farm fixture loads another chunk and its generated animals.
            // Take the livestock baseline after all fixture chunk loads, not before them.
            animalBaseline = scan(level, village, List.of(village)).livestock();
            Animal cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            Animal secondCow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            Animal calf = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            Animal pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
            Animal wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
            require(cow != null && secondCow != null && calf != null && pig != null && wolf != null, "animal fixture factory");
            animals.addAll(List.of(cow, secondCow, calf, pig, wolf));
            calf.setAge(-24_000);
            for (Animal animal : animals) {
                animal.setPos(center.getX() + .5, center.getY() + 1, center.getZ() + .5);
                require(level.addFreshEntity(animal), "fixture animal added");
            }
            var living = scan(level, village, List.of(village));
            require(close(living.livestock() - animalBaseline, 3.25), "food livestock counted, babies discounted, pets excluded: " + (living.livestock() - animalBaseline));
            cow.setHealth(0);
            require(close(living.livestock() - animalBaseline, 2.25), "dead animals stop contributing");
            pig.setPos(center.getX() + 100, center.getY(), center.getZ());
            require(close(living.livestock() - animalBaseline, 1.25), "animals taken beyond district range stop contributing");
            for (int x = -4; x < 4; x++) for (int z = -4; z < 4; z++)
                set(level, before, center.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            secondCow.setHealth(0); calf.setHealth(0);
            var stripped = scan(level, village, List.of(village));
            require(close(stripped.crops, baseline) && close(stripped.livestock(), animalBaseline), "stripped field and removed livestock lose their bonus");
            require(VillageFoodEnvironment.cropUnits(Blocks.CARROTS.defaultBlockState()) > 0
                    && VillageFoodEnvironment.cropUnits(Blocks.POTATOES.defaultBlockState()) > 0
                    && VillageFoodEnvironment.cropUnits(Blocks.BEETROOTS.defaultBlockState()) > 0
                    && VillageFoodEnvironment.cropUnits(Blocks.CHEST.defaultBlockState()) == 0
                    && VillageFoodEnvironment.cropUnits(Blocks.HAY_BLOCK.defaultBlockState()) == 0,
                    "edible growing crops qualify, stored loot/hay do not");
            verifyUnknownChunks(level);
            System.out.println("PASS VillageFoodEnvironmentSelfTest: mature/young crops, harvest loss, livestock/death/range, pets, exclusive district ownership, unloaded census preservation/restart");
        } finally {
            animals.forEach(Animal::discard);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }

    private static void verifyUnknownChunks(ServerLevel level) {
        try {
            BlockPos center = new BlockPos(6008, level.getMaxY() - 48, 6008);
            level.getChunk(center); // Load only the fixture center, not its entire district.
            var state = EconomyState.fresh(835, System.currentTimeMillis(), 0);
            var village = state.village(new UUID(835, 836));
            village.centerPos = center.asLong(); village.dimensionKey = "minecraft:overworld";
            var scan = scan(level, village, List.of(village));
            Long unknown = null;
            for (int cx = scan.minChunkX; cx < scan.minChunkX + scan.chunksWide; cx++)
                for (int cz = scan.minChunkZ; cz < scan.minChunkZ + scan.chunksDeep; cz++)
                    if (!level.hasChunk(cx, cz)) unknown = ((long) cx << 32) | (cz & 0xffffffffL);
            require(unknown != null && !scan.completedChunks.containsKey(unknown),
                    "unloaded columns are unknown, not empty completed observations");
            long loaded = ((long) (center.getX() >> 4) << 32) | ((center.getZ() >> 4) & 0xffffffffL);
            require(scan.completedChunks.containsKey(loaded), "fully inspected loaded column is published");
            var dir = java.nio.file.Files.createTempDirectory("tes-food-unknown-runtime-");
            state.save(dir.resolve("the_emerald_standard.properties"));
            var economy = new com.chedidandrew.emeraldstandard.core.EconomyService(); economy.start(dir, 835, 0);
            require(economy.observeVillageFoodChunks(village.villageId, Map.of(unknown,
                    new com.chedidandrew.emeraldstandard.core.VillageFoodSupply.ChunkObservation(50, 2), loaded,
                    new com.chedidandrew.emeraldstandard.core.VillageFoodSupply.ChunkObservation(100, 3))), "seed prior census");
            require(economy.observeVillageFoodChunks(village.villageId, scan.completedChunks), "publish partial real census");
            double crops = 50 + scan.completedChunks.values().stream().mapToDouble(v -> v.crops()).sum();
            double livestock = 2 + scan.completedChunks.values().stream().mapToDouble(v -> v.livestock()).sum();
            var observed = economy.villageSnapshot(village.villageId).village();
            require(close(observed.observedCropUnits, crops) && close(observed.observedLivestockUnits, livestock),
                    "new loaded counts replace stale loaded counts; unknown crops and livestock remain");
            require(economy.saveNow(), "food census checkpoint saved");
            var restarted = new com.chedidandrew.emeraldstandard.core.EconomyService(); restarted.start(dir, 835, 0);
            observed = restarted.villageSnapshot(village.villageId).village();
            require(close(observed.observedCropUnits, crops) && observed.foodChunks.containsKey(unknown),
                    "partial census and retained unknown sources survive restart");
            require(!level.hasChunk((int) (unknown >> 32), (int) (long) unknown), "census never force-loads unknown chunk");
        } catch (java.io.IOException ex) { throw new IllegalStateException("Food census persistence fixture failed", ex); }
    }

    private static VillageFoodEnvironment.Scan scan(ServerLevel level, EconomyState.VillageRecord village,
            List<EconomyState.VillageRecord> neighbors) {
        var scan = new VillageFoodEnvironment.Scan(level, village, neighbors);
        int slices = 0;
        while (!scan.advance(512)) require(++slices < 1000, "bounded scan eventually finishes");
        return scan;
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        if (!before.containsKey(pos)) {
            require(level.getBlockEntity(pos) == null, "fixture must not replace existing storage");
            before.put(pos.immutable(), level.getBlockState(pos));
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static boolean close(double first, double second) { return Math.abs(first - second) < 1e-8; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
