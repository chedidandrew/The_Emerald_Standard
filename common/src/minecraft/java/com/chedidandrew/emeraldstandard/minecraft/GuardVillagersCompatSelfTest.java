package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;

/** Opt-in isolated smoke fixture: exercises real external entity types when their JAR is supplied. */
final class GuardVillagersCompatSelfTest {
    static void verify(ServerLevel level) {
        boolean present = GuardVillagersCompat.available();
        require(present == Boolean.getBoolean("the_emerald_standard.expectGuards"),
                "Unexpected Guard Villagers test environment");
        Villager ordinary = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        require(!GuardVillagersCompat.eligible(ordinary), "vanilla villagers are not guards");
        if (!present) {
            ordinary.discard();
            System.out.println("PASS optional Guard Villagers absent: no registry dependency"); return;
        }
        var economy = new EconomyService();
        try {
            economy.start(java.nio.file.Files.createTempDirectory("tes-guard-compat-"), 81, 0);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        BlockPos a = new BlockPos(200, 80, 200), b = a.east(60);
        // Fixture-only chunk preparation. Production observation never calls getChunk.
        for (int x = (a.getX() - 48) >> 4; x <= (b.getX() + 48) >> 4; x++)
            for (int z = (a.getZ() - 48) >> 4; z <= (a.getZ() + 48) >> 4; z++) level.getChunk(x, z);
        var va = village(economy, a, 81); var vb = village(economy, b, 82);
        require(!va.villageId.equals(vb.villageId), "separate neighboring fixture districts");
        List<Mob> guards = new ArrayList<>();
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new LinkedHashMap<>();
        try {
            for (int x = 196; x <= 270; x++) for (int z = 195; z <= 205; z++) for (int y = 79; y <= 83; y++) {
                BlockPos pos = new BlockPos(x, y, z); before.put(pos, level.getBlockState(pos));
                level.setBlock(pos, (y == 79 ? net.minecraft.world.level.block.Blocks.STONE
                        : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 18);
            }
            for (int i = 0; i < 8; i++) {
                Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(GuardVillagersCompat.GUARD_ID)
                        .create(level, EntitySpawnReason.COMMAND);
                require(entity instanceof Mob, "registered guard is a Mob");
                Mob guard = (Mob) entity; guards.add(guard);
                guard.setPos(a.getX() + 2 + i, a.getY(), a.getZ());
                require(level.addFreshEntity(guard), "spawn fixture guard");
                require(GuardVillagersCompat.eligible(guard), "actual guard recognized");
            }
            guards.get(6).setNoAi(true);
            guards.get(7).setPos(a.getX() + 31, a.getY(), a.getZ()); // In range of both, closer to B.
            var config = EmeraldConfig.defaults();
            GuardVillagersCompat.observe(level, economy, va, config);
            GuardVillagersCompat.observe(level, economy, vb, config);
            require(economy.villageSnapshot(va.villageId).village().observedGuards == 6, "A gets six eligible guards");
            require(economy.villageSnapshot(vb.villageId).village().observedGuards == 1, "overlap belongs only to nearer B");
            require(economy.villageSnapshot(va.villageId).village().guardSafetyBonus == 12, "default cap");
            Mob trapped = guards.get(4);
            require(GuardVillagersCompat.canPatrol(level, trapped), "open ground is patrol-capable");
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int y = 0; y < 3; y++)
                level.setBlock(trapped.blockPosition().relative(direction).above(y),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 18);
            require(!GuardVillagersCompat.canPatrol(level, trapped), "sealed guard cell earns no security");
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int y = 0; y < 3; y++)
                level.setBlock(trapped.blockPosition().relative(direction).above(y),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
            var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GuardTarget"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            guards.get(0).setTarget(player);
            require(guards.get(0).getTarget() == player, "real guard accepts the hostile-player fixture");
            require(!GuardVillagersCompat.eligible(guards.get(0)), "hostile-to-player guard excluded");
            guards.get(1).discard();
            GuardVillagersCompat.observe(level, economy, va, config);
            require(economy.villageSnapshot(va.villageId).village().guardSafetyBonus == 8, "hostile and removed guards stop contributing");
            guards.get(2).setPos(b.getX()-3, b.getY(), b.getZ());
            GuardVillagersCompat.observe(level, economy, vb, config);
            require(economy.villageSnapshot(va.villageId).village().observedGuards == 3
                    && economy.villageSnapshot(vb.villageId).village().observedGuards == 2,
                    "transfer evicts stale source-district credit without rescanning it");
            int population = economy.villageSnapshot(va.villageId).village().population;
            Properties off = new Properties(); off.setProperty(EmeraldConfig.GUARDS_ENABLED_KEY, "false");
            try { config = EmeraldConfig.parse(off); } catch (Exception error) { throw new IllegalStateException(error); }
            GuardVillagersCompat.observe(level, economy, va, config);
            require(economy.villageSnapshot(va.villageId).village().guardSafetyBonus == 0, "disabled adapter");
            require(economy.villageSnapshot(va.villageId).village().population == population, "guards are not residents");
            BlockPos distant = new BlockPos(20000000, 80, 20000000);
            var unloaded = village(economy, distant, 83);
            economy.observeVillageGuards(unloaded.villageId, 2, 2, 12);
            GuardVillagersCompat.observe(level, economy, unloaded, EmeraldConfig.defaults());
            require(economy.villageSnapshot(unloaded.villageId).village().guardSafetyBonus == 4
                    && !level.hasChunk(distant.getX() >> 4, distant.getZ() >> 4), "unknown chunks retained, never forced");
            System.out.println("PASS optional Guard Villagers present: real guards, cap, no-AI/hostile/removal, overlap, toggle, unloaded chunks");
        } finally {
            guards.forEach(Entity::discard); ordinary.discard();
            before.forEach((pos, state) -> level.setBlock(pos, state, 18));
        }
    }
    private static EconomyState.VillageRecord village(EconomyService economy, BlockPos pos, long region) {
        return economy.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", pos.asLong(), region, 0, 5, 8, 0, false, List.of())).village();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
