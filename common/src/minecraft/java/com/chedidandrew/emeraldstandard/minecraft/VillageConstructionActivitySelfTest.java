package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

final class VillageConstructionActivitySelfTest {
    static void verify(ServerLevel level) throws Exception {
        BlockPos origin = new BlockPos(2300, level.getMaxY() - 35, 2300);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        List<Entity> cleanup = new ArrayList<>();
        UUID village = UUID.randomUUID();
        String tag = VillageConstructionActivity.JOB + village + ":1";
        var site = new VillageConstructionActivity.Site(tag, village, 1, origin, origin);
        try {
            for (int x = -8; x <= 18; x++) for (int z = -6; z <= 14; z++) {
                level.getChunk(origin.offset(x, 0, z));
                for (int y = -3; y < 5; y++) {
                    BlockPos pos = origin.offset(x, y, z); before.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, (y < -1 ? Blocks.STONE : y == -1 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), 18);
                }
            }
            for (int i = 0; i < 3; i++) {
                Villager worker = VillageWalkingSelfTest.walker(level, origin.offset(-4, 0, i));
                worker.addTag("the_emerald_standard_village_" + village);
                level.addFreshEntity(worker); cleanup.add(worker);
            }
            var unrelated = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            unrelated.setPos(origin.getX() + 10, origin.getY(), origin.getZ());
            level.addFreshEntity(unrelated); cleanup.add(unrelated);
            VillageConstructionActivity.update(level, List.of(site));
            var workers = cleanup.stream().filter(e -> e.entityTags().contains(tag)).toList();
            require(workers.size() == 2, "two persistent workers, not every villager");
            VillageConstructionActivity.update(level, List.of(site));
            require(workers.stream().allMatch(e -> e.entityTags().contains(tag)), "same builders retained");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            workers.getFirst().saveWithoutId(saved);
            Villager reloaded = VillageWalkingSelfTest.walker(level, origin);
            reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            require(reloaded.entityTags().contains(tag), "assignment survives native entity serialization");
            reloaded.discard();
            List<Entity> props = new ArrayList<>();
            level.getAllEntities().forEach(e -> { if (e.entityTags().contains(tag) && e instanceof Display) props.add(e); });
            cleanup.addAll(props);
            require(props.size() == 3, "bounded supported scaffold and materials displays, no duplicates");
            Entity carrier = workers.getFirst();
            carrier.setPos(origin.getX() - 2.5, origin.getY(), origin.getZ() + 4.5);
            VillageConstructionActivity.update(level, List.of(site));
            List<Entity> loads = new ArrayList<>();
            level.getAllEntities().forEach(e -> { if (e.entityTags().contains("tes_carrier:" + carrier.getUUID())) loads.add(e); });
            require(loads.size() == 1, "one cosmetic carried load after reaching materials");
            cleanup.addAll(loads);
            var loadSave = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            loads.getFirst().saveWithoutId(loadSave);
            require(loadSave.buildResult().toString().contains("0.35")
                    && loadSave.buildResult().toString().contains("oak_planks"), "native delivery display has a small block transform");
            carrier.setPos(origin.getX() - 2.5, origin.getY(), origin.getZ() + 1.5);
            VillageConstructionActivity.update(level, List.of(site));
            require(loads.getFirst().isRemoved(), "delivery sets down its visual load, without item drops");
            BlockPos playerEdit = origin.offset(-2, 0, 4);
            level.setBlock(playerEdit, Blocks.CHEST.defaultBlockState(), 18);
            VillageConstructionActivity.update(level, List.of());
            require(props.stream().allMatch(Entity::isRemoved) && workers.stream().noneMatch(e -> e.entityTags().contains(tag)),
                    "completed site removes owned props and releases workers");
            require(!unrelated.isRemoved() && level.getBlockState(playerEdit).is(Blocks.CHEST), "cleanup preserves unrelated entity and player chest");
            level.setBlock(playerEdit, Blocks.AIR.defaultBlockState(), 18);
            List<BlockPos> road = new ArrayList<>();
            for (int x = 0; x < 14; x++) road.add(origin.offset(x, 0, 0));
            var roadPlan = VillageTerrainFinishing.road(level, road, origin.getY(), Set.of(),
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_STAIRS.defaultBlockState(), village, 1);
            require(roadPlan != null, "road beside optional pocket");
            for (int style = 0; style < 3; style++) {
                long project = style;
                var pocket = VillageTerrainFinishing.pocket(level, road, Set.of(), Set.of(), roadPlan, village, project);
                require(!pocket.isEmpty(), "optional neighborhood style " + style + " planned");
                require(pocket.equals(VillageTerrainFinishing.pocket(level, road, Set.of(), Set.of(), roadPlan, village, project)),
                        "pocket design deterministic");
                require(pocket.values().stream().filter(c -> !c.after().equals("minecraft:air"))
                        .allMatch(c -> BlockPos.of(c.position()).getY() < origin.getY()
                                || pocket.containsKey(BlockPos.of(c.position()).below().asLong())), "all decorations have a planned support");
            }
            try (var claim = VillageDevelopmentProtection.register(context -> false)) {
                require(VillageTerrainFinishing.pocket(level, road, Set.of(), Set.of(), roadPlan, village, 1).isEmpty(), "claimed pocket skipped");
            }
            System.out.println("PASS VillageConstructionActivitySelfTest: saved assignments, supported props, exact cleanup, three pocket styles, claims");
        } finally {
            cleanup.forEach(Entity::discard);
            before.forEach((pos, state) -> level.setBlock(pos, state, 18));
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
