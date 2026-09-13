package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.BankConstruction;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Real placement/loot/entity regressions in the opt-in disposable smoke world. */
final class ConstructionSafetySelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin = new BlockPos(824, level.getMaxY() - 32, 824);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        List<LivingEntity> actors = new ArrayList<>();
        ServerPlayer player = null;
        ItemEntity item = null;
        try {
            for (int x = -2; x <= 12; x++) for (int z = -2; z <= 3; z++) {
                level.getChunk(origin.offset(x, 0, z)); // Fixture setup only.
                for (int y = -1; y <= 3; y++) set(level, before, origin.offset(x, y, z),
                        y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            var dir = Files.createTempDirectory("tes-bank-loot-runtime-");
            var economy = new EconomyService(); economy.start(dir, 891, 0);
            BlockPos chestPos = origin;
            BlockPos blocked = origin.east(3);
            var plan = new BankConstruction(origin.asLong(), origin.asLong(), null, 8, List.of(
                    new BankConstruction.Cell(chestPos.asLong(), "minecraft:air", "minecraft:chest"),
                    new BankConstruction.Cell(blocked.asLong(), "minecraft:air", "minecraft:stone")));
            set(level, before, blocked, Blocks.CHEST.defaultBlockState());
            require(economy.reserveBankConstruction(879, plan), "loot Bank reserved");
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.bankTag(879,plan.origin()));
            require(VillageBankManager.advanceBankConstruction(level, economy, 879, plan) == 1,
                    "new Bank chest placed");
            var storage = storage(level, chestPos);
            require(storage.getLootTable() == null && storage.isEmpty()
                    && economy.pendingBankConstructionsSnapshot().get(879L).handledStorage().contains(chestPos.asLong()),
                    "construction storage is empty until handover, with saved ownership");
            require(!storage.canOpen(null)&&!storage.canPlaceItem(0,new ItemStack(Items.APPLE))
                    &&net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level,chestPos)==null,
                    "construction storage rejects menus and automation without consuming supplied items");
            require(Block.getDrops(level.getBlockState(chestPos),level,chestPos,storage).isEmpty(),
                    "construction chest block has no loot drops");
            require(!net.minecraft.world.level.block.piston.PistonBaseBlock.isPushable(
                    level.getBlockState(chestPos),level,chestPos,net.minecraft.core.Direction.EAST,true,net.minecraft.core.Direction.EAST),
                    "construction cells cannot be exported with pistons");
            storage.getItem(0); // Resolve the loot as opening the chest would.
            storage.clearContent();
            require(VillageBankManager.advanceBankConstruction(level, economy, 879, plan) == 0
                    && storage.getLootTable() == null && storage.isEmpty(), "emptied chest cannot refill");
            set(level, before, chestPos, Blocks.AIR.defaultBlockState());
            require(VillageBankManager.advanceBankConstruction(level, economy, 879, plan) == 1
                    && storage(level,chestPos).getLootTable()==null && storage(level,chestPos).isEmpty(),
                    "broken construction chest is repaired empty while another cell is blocked");
            var restarted = new EconomyService(); restarted.start(dir, 891, 0); economy = restarted;
            require(VillageBankManager.advanceBankConstruction(level, economy, 879, plan) == 0
                    && storage(level,chestPos).getLootTable()==null, "restart and stale caller preserve empty pending storage");
            set(level, before, chestPos, Blocks.CHEST.defaultBlockState());
            storage(level, chestPos).setItem(0, new ItemStack(Items.APPLE, 3));
            require(VillageBankManager.advanceBankConstruction(level, economy, 879, plan) == 0
                    && storage(level, chestPos).getLootTable() == null
                    && storage(level, chestPos).getItem(0).getCount() == 3, "replacement player storage keeps its own contents");

            BlockPos legacyPos = origin.east(6);
            var legacy = new BankConstruction(legacyPos.asLong(), legacyPos.asLong(), null, 8, List.of(
                    new BankConstruction.Cell(legacyPos.asLong(), "minecraft:air", "minecraft:barrel"),
                    new BankConstruction.Cell(blocked.asLong(), "minecraft:air", "minecraft:stone")), Set.of(), true);
            require(economy.reserveBankConstruction(880, legacy), "legacy Bank fixture reserved");
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.bankTag(880,legacy.origin()));
            require(VillageBankManager.advanceBankConstruction(level, economy, 880, legacy) == 1
                    && storage(level, legacyPos).getLootTable() == null && storage(level, legacyPos).isEmpty(),
                    "legacy unknown storage finishes empty, without a fresh loot roll");
            set(level, before, legacyPos, Blocks.AIR.defaultBlockState());
            require(VillageBankManager.advanceBankConstruction(level, economy, 880, legacy) == 1
                    && storage(level,legacyPos).getLootTable()==null, "legacy issued storage repairs without another loot roll");

            // Keep target cells away from earlier fixtures' tape and in the tracked fixture chunk.
            BlockPos work = origin.east(2).south(2);
            require(level.getBlockState(work).isAir() && level.getBlockState(work.east(2)).isAir(),
                    "occupancy fixture starts clear of other worksite fences");
            LivingEntity cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            LivingEntity villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            require(cow != null && villager != null, "living fixture factories");
            actors.addAll(List.of(cow, villager));
            for (LivingEntity actor : actors) {
                actor.setPos(work.getX() + .5, work.getY(), work.getZ() + .5);
                require(level.addFreshEntity(actor), "living fixture added");
                require(!VillageConstructionOccupancy.mayChange(level, work, Blocks.AIR.defaultBlockState(),
                        Blocks.STONE.defaultBlockState()), "wall waits for " + actor.getType());
                require(!VillageConstructionOccupancy.mayChange(level, work.below(), Blocks.STONE.defaultBlockState(),
                        Blocks.AIR.defaultBlockState()), "excavation keeps occupied footing");
                require(!VillageConstructionOccupancy.mayChange(level, work.below(), Blocks.STONE.defaultBlockState(),
                        Blocks.DIRT_PATH.defaultBlockState()), "path grading cannot lower occupied footing");
                require(VillageConstructionOccupancy.mayChange(level, work.below(), Blocks.STONE.defaultBlockState(),
                        Blocks.STONE_BRICKS.defaultBlockState()), "same-height floor replacement remains permitted");
                require(VillageConstructionOccupancy.mayChange(level, work.east(2), Blocks.AIR.defaultBlockState(),
                        Blocks.STONE.defaultBlockState()), "adjacent safe cells remain buildable");
                actor.setPos(work.getX() + 9, work.getY(), work.getZ());
            }
            var occupiedPlan = new BankConstruction(work.asLong(), work.asLong(), null, 8, List.of(
                    new BankConstruction.Cell(work.asLong(), "minecraft:air", "minecraft:stone"),
                    new BankConstruction.Cell(work.east(2).asLong(), "minecraft:air", "minecraft:stone")));
            require(economy.reserveBankConstruction(881, occupiedPlan), "occupied Bank reserved");
            // Complete fixture chunk/setup work before installing the unconnected test player.
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.bankTag(881,occupiedPlan.origin()));
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "WorksiteFixture"),
                    ClientInformation.createDefault());
            player.setPos(work.getX() + .5, work.getY(), work.getZ() + .5);
            level.players().add(player);
            require(!VillageConstructionOccupancy.mayChange(level, work, Blocks.AIR.defaultBlockState(),
                    Blocks.STONE.defaultBlockState()), "player occupancy is protected during tracking transitions");
            require(!VillageConstructionOccupancy.mayChange(level, work, Blocks.AIR.defaultBlockState(),
                    Blocks.WATER.defaultBlockState()), "water cannot be placed into an occupied cell");
            require(VillageBankManager.advanceBankConstruction(level, economy, 881, occupiedPlan) == 1
                    && level.getBlockState(work).isAir() && level.getBlockState(work.east(2)).is(Blocks.STONE),
                    "Bank defers occupied cell and works on another safe cell: occupied="+level.getBlockState(work)
                            +", adjacent="+level.getBlockState(work.east(2))+", player="+player.position()
                            +", alive="+player.isAlive()+", tracked="+level.players().contains(player));
            require(VillageBankManager.advanceBankConstruction(level, economy, 881, occupiedPlan) == 0
                    && economy.pendingBankConstructionsSnapshot().containsKey(881L), "occupied site retains its reservation");
            player.setPos(work.getX() + 12, work.getY(), work.getZ());
            item = new ItemEntity(level, work.getX() + .5, work.getY(), work.getZ() + .5, new ItemStack(Items.STICK));
            require(level.addFreshEntity(item), "dropped item fixture added");
            require(VillageBankManager.advanceBankConstruction(level, economy, 881, occupiedPlan) == 1
                    && level.getBlockState(work).is(Blocks.STONE), "Bank resumes after player moves; item drops do not stall it");
            // Handover releases exact receipts; no future regeneration after the saved Bank authority.
            require(VillageBankManager.advanceBankConstruction(level,economy,881,occupiedPlan)==0
                    &&economy.hasGeneratedBankRegion(881),"safe Bank handover");
            require(!ConstructionOwnership.owned(level,work,Blocks.STONE.defaultBlockState()),
                    "completed blocks immediately lose construction protection");
            System.out.println("PASS ConstructionSafetySelfTest: empty locked storage, no drops, repairs, legacy contents, handover, player/villager/animal occupancy, footing and safe neighboring work");
        } catch (Exception ex) { throw new IllegalStateException("Construction safety runtime regression failed", ex); }
        finally {
            VillageConstructionActivitySelfTest.cleanupCrews(level);
            if (player != null) level.players().remove(player);
            if (item != null) item.discard();
            actors.forEach(LivingEntity::discard);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }

    private static RandomizableContainerBlockEntity storage(ServerLevel level, BlockPos pos) {
        require(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity, "storage block entity exists");
        return (RandomizableContainerBlockEntity) level.getBlockEntity(pos);
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        before.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
