package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in disposable server fixtures, including production paced replay and natural terrain surveys. */
final class VillageTerrainFinishingSelfTest {
    static void verify(ServerLevel level) throws Exception {
        BlockPos origin = new BlockPos(1800, level.getMaxY() - 55, 1800);
        Map<BlockPos, BlockState> restore = new LinkedHashMap<>();
        try {
            for (int x = -4; x <= 25; x++) for (int z = -5; z <= 14; z++) {
                level.getChunk(origin.offset(x, 0, z)); // Explicit isolated fixture setup only.
                int height = z >= 5 ? 3 : x < 5 ? 0 : x < 9 ? x - 4 : x < 15 ? 4 : Math.max(0, 19 - x);
                if (x == 3 && z < 5) height = -2;
                for (int y = -6; y <= 10; y++) set(level, restore, origin.offset(x, y, z),
                        y < height - 1 ? Blocks.STONE.defaultBlockState() : y == height - 1
                                ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            set(level, restore, origin.offset(1, 0, 0), Blocks.TORCH.defaultBlockState());
            List<BlockPos> route = new ArrayList<>();
            for (int x = 0; x <= 23; x++) route.add(origin.offset(x, 0, 0));
            var masonry = Blocks.STONE_BRICKS.defaultBlockState();
            var stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
            var road = VillageTerrainFinishing.road(level, route, origin.getY(), Set.of(), masonry, stairs, null, 1);
            require(road != null && road.values().stream().anyMatch(c -> c.after().contains("stairs")), "sloped road has stairs");
            require(road.values().stream().anyMatch(c -> c.before().equals("minecraft:air") && c.after().equals("minecraft:stone_bricks")),
                    "crater receives non-falling supports");
            BlockPos edited = origin.offset(2, 0, 0);
            set(level, restore, edited, Blocks.CHEST.defaultBlockState());
            require(VillageTerrainFinishing.road(level, route, origin.getY(), Set.of(), masonry, stairs, null, 1) == null,
                    "stored items veto the connection, not the building");
            set(level, restore, edited, Blocks.AIR.defaultBlockState());
            try (var veto = VillageDevelopmentProtection.register(context -> false)) {
                require(VillageTerrainFinishing.road(level, route, origin.getY(), Set.of(), masonry, stairs, null, 1) == null,
                        "claims veto terrain road");
            }
            var plan = VillageTerrainFinishing.finish(level, new SitePreparationPlan(List.of()), origin.offset(10, 0, 9),
                    -2, 2, -2, 2, Set.of(), List.of(), origin.getY(), masonry, stairs, null, 1);
            require(plan.cells().size() == 48, "three-high, sixteen-column retaining border follows terrain");
            for (var cell : plan.cells()) require(!cell.after().equals("minecraft:air"), "wall is grounded solid masonry");
            var ordered = new ArrayList<>(road.values());
            ordered.sort(Comparator.<SitePreparationPlan.Cell>comparingInt(c -> c.after().equals("minecraft:air") ? 0 : 1)
                    .thenComparingInt(c -> c.after().equals("minecraft:air") ? -BlockPos.of(c.position()).getY()
                            : BlockPos.of(c.position()).getY()));
            var project = new EconomyState.VillageProject(); project.projectId = 1;
            project.sitePreparationPlan = new SitePreparationPlan(ordered);
            var village = new EconomyState.VillageRecord(); village.villageId = UUID.randomUUID();
            var economy = new EconomyService();
            var method = VillageProsperityManager.class.getDeclaredMethod("prepareNewSite", ServerLevel.class,
                    EconomyService.class, EconomyState.VillageRecord.class, EconomyState.VillageProject.class, int.class);
            method.setAccessible(true);
            int operations = 0;
            for (int pulse = 0; pulse < ordered.size() + 3; pulse++) {
                if (pulse == 5) project.sitePreparationPlan = SitePreparationPlan.decode(project.sitePreparationPlan.encode());
                int writes = (int) method.invoke(null, level, economy, village, project, 1);
                require(writes >= 0 && writes <= 1, "one terrain operation per ten-tick site pulse");
                operations += writes;
            }
            require(operations > 0, "production path replay did work");
            for (var cell : ordered) require(VillageTerrainFinishing.satisfied(level,
                    level.getBlockState(BlockPos.of(cell.position())), cell.after()), "restart-safe final cell " + cell);
            require((int) method.invoke(null, level, economy, village, project, 1) == 0, "completed terrain replay is idempotent");
            var walker = VillageWalkingSelfTest.walker(level, origin);
            try {
                VillageWalkingSelfTest.walk(level, walker, origin.offset(23, 0, 0), "graded neighboring street outbound");
                VillageWalkingSelfTest.walk(level, walker, origin, "graded neighboring street return");
            } finally { walker.discard(); }
            System.out.println("PASS VillageTerrainFinishingSelfTest: slopes, crater supports, retaining courses, claims, storage, paced restart");
        } finally {
            restore.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
        int candidates = 0, accepted = 0;
        // This world's real seed-generated terrain, not the synthetic high-altitude fixtures.
        for (int chunkX = 0; chunkX < 4; chunkX++) for (int chunkZ = 0; chunkZ < 4; chunkZ++)
            level.getChunk(chunkX, chunkZ); // Smoke harness only; never production force-loading.
        for (int x = 4; x < 48; x += 8) for (int z = 4; z < 48; z += 8) {
            var survey = new VillageSitePreparation.Survey(level);
            Integer y = survey.surface(x, z);
            if (y == null) continue;
            List<BlockPos> route = new ArrayList<>();
            for (int i = 0; i < 8; i++) route.add(new BlockPos(x + i, y, z));
            candidates++;
            if (VillageTerrainFinishing.road(level, route, y, Set.of(), Blocks.STONE_BRICKS.defaultBlockState(),
                    Blocks.STONE_BRICK_STAIRS.defaultBlockState(), null, 1) != null) accepted++;
        }
        System.out.println("PASS VillageTerrainFinishingSelfTest: natural seed=" + level.getSeed()
                + " dry route starts=" + candidates + " graded connections=" + accepted + " (read-only survey)");
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> restore, BlockPos pos, BlockState state) {
        restore.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
