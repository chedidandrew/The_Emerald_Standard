package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;

/** Exercises the real loader dispatcher and Bank tick, not just direct placement calls. */
final class ForcedDevelopmentSchedulingSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin = new BlockPos(3104, level.getMaxY() - 40, 3104);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        ServerPlayer observer = null;
        long originalTime = level.getGameTime();
        ServerLevelData clock = (ServerLevelData) level.getLevelData();
        EmeraldConfig saved = EmeraldConfig.current();
        try {
            var configField = EmeraldConfig.class.getDeclaredField("current");
            configField.setAccessible(true);
            var dir = Files.createTempDirectory("tes-bank-mode-switch-");
            var economy = new EconomyService(); economy.start(dir, 771, 0);
            var properties = new Properties();
            properties.putAll(saved.values());
            properties.setProperty("village_banks.enabled", "true");
            properties.setProperty("village_prosperity.construction_blocks_per_second", "2");
            properties.setProperty("village_prosperity.simulation_enabled", "false");
            properties.setProperty("village_prosperity.visual_progression_enabled", "false");
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY, "false");
            var normal = EmeraldConfig.parse(properties);
            configField.set(null, normal);
            observer = new ServerPlayer(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "BankModeFixture"), ClientInformation.createDefault());
            observer.setPos(origin.getX() - 24, origin.getY(), origin.getZ());
            level.players().add(observer);

            for (int site = 0; site < 2; site++) {
                BlockPos start = origin.east(site * 48);
                List<BankConstruction.Cell> cells = new ArrayList<>();
                for (int x = 0; x < 8; x++) for (int z = 0; z < 6; z++) {
                    BlockPos pos = start.offset(x, 0, z);
                    level.getChunk(pos);
                    set(level, before, pos.below(), Blocks.DIRT.defaultBlockState());
                    set(level, before, pos, Blocks.AIR.defaultBlockState());
                    cells.add(new BankConstruction.Cell(pos.asLong(), "minecraft:air", "minecraft:stone"));
                }
                require(economy.reserveBankConstruction(9100 + site,
                        new BankConstruction(start.asLong(), start.above().asLong(), null, 11, cells)), "reserve Bank");
            }
            BlockPos remote = origin.east(8192);
            require(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                    "remote Bank fixture starts unloaded");
            require(economy.reserveBankConstruction(9102, new BankConstruction(remote.asLong(),
                    remote.above().asLong(), null, 11, List.of(
                    new BankConstruction.Cell(remote.asLong(), "minecraft:air", "minecraft:stone")))), "reserve remote Bank");
            VillageBankManager.resetRuntimeState();
            VillageBankManager.beginServerSession(level.getServer(), economy);
            VillageProsperityManager.resetRuntimeState();
            // Normal work first; the two existing sites must already be partially constructed.
            clock.setGameTime(1010);
            VillageDevelopmentRuntime.tick(level.getServer(), economy);
            require(stones(level, origin) > 0 && stones(level, origin.east(48)) > 0, "normal partial Banks");
            int partial = stones(level, origin);

            // A fence wait immediately before changing mode must not survive as a 200-tick hold.
            properties.setProperty("village_prosperity.visual_progression_enabled", "true");
            configField.set(null, EmeraldConfig.parse(properties));
            clock.setGameTime(1020); VillageDevelopmentRuntime.tick(level.getServer(), economy);
            require(ConstructionDiagnostics.preparingFence("bank:9100"), "normal fence wait captured");
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY, "true");
            configField.set(null, EmeraldConfig.parse(properties));
            clock.setGameTime(1022); VillageDevelopmentRuntime.tick(level.getServer(), economy);
            require(economy.forcedVillageDevelopment() && stones(level, origin) > partial,
                    "enabling debug clears old fence wait before first Bank-first tick");

            // Keep one site genuinely blocked. Other Banks must continue and no storage may be replaced.
            BlockPos blocked = origin.east(48).offset(7, 0, 5);
            set(level, before, blocked, Blocks.CHEST.defaultBlockState());
            int firstCount = stones(level, origin), secondCount = stones(level, origin.east(48));
            properties.setProperty("village_banks.enabled", "false");
            configField.set(null, EmeraldConfig.parse(properties));
            clock.setGameTime(1024); VillageDevelopmentRuntime.tick(level.getServer(), economy);
            require(stones(level, origin) == firstCount && stones(level, origin.east(48)) == secondCount,
                    "debug must respect disabled Bank construction");
            properties.setProperty("village_banks.enabled", "true");
            configField.set(null, EmeraldConfig.parse(properties));
            for (int tick = 1025; tick < 1090; tick++) {
                clock.setGameTime(tick);
                VillageDevelopmentRuntime.tick(level.getServer(), economy);
            }
            require(economy.hasGeneratedBankRegion(9100), "already-started Bank completes with debug enabled");
            require(economy.pendingBankConstructionsSnapshot().containsKey(9101L)
                    && level.getBlockState(blocked).is(Blocks.CHEST), "debug preserves player storage");
            require(economy.pendingBankConstructionsSnapshot().containsKey(9102L)
                    && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                    "debug does not load remote Bank chunks");

            // Disabling and re-enabling must retain the same frozen geometry and storage receipts.
            var frozen = economy.pendingBankConstructionsSnapshot().get(9101L);
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY, "false");
            properties.setProperty("village_prosperity.visual_progression_enabled", "false");
            configField.set(null, EmeraldConfig.parse(properties));
            clock.setGameTime(1090); VillageDevelopmentRuntime.tick(level.getServer(), economy);
            require(!economy.forcedVillageDevelopment()
                    && frozen.equals(economy.pendingBankConstructionsSnapshot().get(9101L)),
                    "disable preserves blocked plan");
            set(level, before, blocked, Blocks.AIR.defaultBlockState());
            clock.setGameTime(1290); VillageDevelopmentRuntime.tick(level.getServer(), economy);
            require(stones(level, origin.east(48)) == 48, "ordinary construction resumes after obstacle removed");
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY, "true");
            configField.set(null, EmeraldConfig.parse(properties));
            for (int tick = 1292; tick < 1300; tick++) {
                clock.setGameTime(tick); VillageDevelopmentRuntime.tick(level.getServer(), economy);
            }
            require(economy.hasGeneratedBankRegion(9101), "re-enabled debug hands over remaining Bank");
            var restart = new EconomyService(); restart.start(dir, 771, 0);
            require(restart.hasGeneratedBankRegion(9100) && restart.hasGeneratedBankRegion(9101)
                    && restart.pendingBankConstructionsSnapshot().containsKey(9102L), "restart preserves completions and pending work");
            System.out.println("PASS Bank mode switching: real dispatcher, partial Banks, fence wait, concurrent/remote/blocked sites, disable/re-enable and restart");
        } catch (Exception ex) {
            throw new IllegalStateException("Bank mode-switch scheduling fixture", ex);
        } finally {
            try {
                var field = EmeraldConfig.class.getDeclaredField("current"); field.setAccessible(true); field.set(null, saved);
            } catch (Exception ex) { throw new IllegalStateException(ex); }
            clock.setGameTime(originalTime);
            if (observer != null) { level.players().remove(observer); observer.discard(); }
            level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(100),
                    v -> BankerAccess.isBankerForRegion(v, 9100) || BankerAccess.isBankerForRegion(v, 9101)).forEach(Villager::discard);
            VillageConstructionActivitySelfTest.cleanupCrews(level);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            VillageBankManager.resetRuntimeState(); VillageProsperityManager.resetRuntimeState(); ConstructionDiagnostics.reset();
        }
    }
    private static int stones(ServerLevel level, BlockPos origin) {
        int count = 0;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 6; z++)
            if (level.getBlockState(origin.offset(x, 0, z)).is(Blocks.STONE)) count++;
        return count;
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        before.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
