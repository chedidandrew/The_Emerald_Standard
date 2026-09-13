package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Exercises identity and operational checks against the full authored smoke-test Bank. */
final class BankVillageOwnershipSelfTest {
    static void verify(ServerLevel level, EconomyService economy, BlockPos origin) throws Exception {
        BlockPos anchor = BlockPos.of(economy.generatedBankAnchor(777));
        BlockPos bell = origin.offset(2, 2, -3);
        var owner = economy.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", bell.offset(40, 1, 96).asLong(), 777, anchor.asLong(),
                18, 16, 0, false, List.of()));
        UUID ownerId = owner.village().villageId;
        BlockPos unloaded = new BlockPos(20_000_000, origin.getY(), 20_000_000);
        require(!VillageProsperityManager.censusChunksLoaded(level, unloaded, 48)
                && !level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4),
                "unknown resident chunks defer census without loading terrain");
        require(economy.associateBankRegionWithVillage(777, ownerId, anchor.asLong()), "assigned village");
        var ghost = economy.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", bell.asLong(), 778, 0, 0, 0, 0, false, List.of()));
        require(!ghost.village().villageId.equals(ownerId), "fixture reproduces original distant ghost");
        require(VillageBankManager.isManagedBankOperational(level, economy, 777),
                "authored Bank operational: " + VillageBankManager.bankOperationProblem(level, economy, 777L));
        Map<BlockPos, BlockState> changed = new HashMap<>();
        try {
            for (int x = -1; x <= 13; x++) for (int z = -1; z <= 11; z++) for (int y = 4; y <= 11; y++) {
                BlockPos cell = origin.offset(x, y, z);
                BlockState state = level.getBlockState(cell);
                if (state.getBlock() instanceof StairBlock) {
                    changed.put(cell, state);
                    level.setBlock(cell, state.setValue(StairBlock.FACING,
                            state.getValue(StairBlock.FACING).getOpposite()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            require(changed.size() > 40, "test whole roof, not only one stair");
            require(VillageBankManager.isManagedBankOperational(level, economy, 777), "roof rotations cosmetic");
            BlockPos obstruction = origin.offset(6, 1, 3);
            changed.put(obstruction, level.getBlockState(obstruction));
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(!VillageBankManager.isManagedBankOperational(level, economy, 777), "blocked lobby restricts Banker");
            require(VillageBankManager.bankOperationProblem(level, economy, 777L).contains(obstruction.toShortString()),
                    "specific blocked-cell diagnostic");
            var access = VillageBankManager.bankDeskAccess(level, anchor.north(), economy);
            require(access.decision() == BankWorkstationAccessPolicy.Decision.PERSONAL_UNSAFE_BANK
                    && Long.valueOf(777).equals(access.bankRegionKey()), "fallback keeps original Bank identity");
            var player = new ServerPlayer(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "BankOwnerFixture"), ClientInformation.createDefault());
            player.setPos(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
            var menu = new BankerMenu(98, player.getInventory(), economy, player, access.accessPoint(), access.bankRegionKey());
            require(menu.villagePopulation() == 18, "unsafe distant Bank menu selects real village, not zero-person ghost");
            player.containerMenu = menu;
            var beforeMap = economy.villageSnapshot(ownerId).village();
            require(menu.clickMenuButton(player, BankerMenu.BUTTON_MAP_OPEN), "ordinary player can open read-only map");
            var map = menu.districtMap();
            require(map.markers().stream().anyMatch(m -> m.kind() == 0 && m.value() == 18 && m.status() == 4),
                    "map current center is original village, not the Bank bell");
            require(map.markers().stream().anyMatch(m -> m.kind() == 1), "map contains distant owned Bank");
            var revisionField = BankerMenu.class.getDeclaredField("mapRevision"); revisionField.setAccessible(true);
            int revision = revisionField.getInt(menu);
            for (int i = 0; i < 100; i++) menu.clickMenuButton(player, BankerMenu.BUTTON_MAP_NEXT);
            require(revisionField.getInt(menu) == revision, "map request flood is throttled");
            require(menu.clickMenuButton(player, BankerMenu.BUTTON_MAP_CLOSE), "close map");
            menu.broadcastChanges();
            require(revisionField.getInt(menu) == revision, "hidden map does not refresh");
            var afterMap = economy.villageSnapshot(ownerId).village();
            require(beforeMap.foodSupply == afterMap.foodSupply && beforeMap.treasury == afterMap.treasury
                    && beforeMap.projects.size() == afterMap.projects.size(), "map cannot spend or build");
            player.containerMenu = player.inventoryMenu;
            require(!menu.clickMenuButton(player, BankerMenu.BUTTON_MAP_OPEN), "stale menu map request rejected");
            level.setBlock(obstruction, changed.get(obstruction), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(VillageBankManager.isManagedBankOperational(level, economy, 777), "unblocking resumes service");
            BlockPos floor = origin.offset(6, 0, 3);
            changed.put(floor, level.getBlockState(floor));
            level.setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(!VillageBankManager.isManagedBankOperational(level, economy, 777)
                    && VillageBankManager.bankOperationProblem(level, economy, 777L).contains("floor"), "floor hole unsafe");
            level.setBlock(floor, changed.get(floor), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            var method = VillageProsperityManager.class.getDeclaredMethod("stableCenter",
                    ServerLevel.class, List.class, BlockPos.class, Set.class);
            method.setAccessible(true);
            var excluded = VillageBankManager.generatedBankBellRegions(economy);
            require(Long.valueOf(777).equals(excluded.get(bell.asLong())), "exact exterior Bank bell owned");
            require(method.invoke(null, level, List.of(), bell, excluded.keySet()) == null,
                    "Bank bells alone cannot establish a settlement");
            require(economy.reconcileEmptyBankBellVillage(ghost.village().villageId, 777, bell.asLong()),
                    "runtime proof repairs legacy empty record: " + economy.lastError());
            require(level.getBlockState(bell).is(Blocks.BELL), "repair never removes bell/building");
            BlockPos desk = anchor.north(); changed.put(desk, level.getBlockState(desk));
            level.setBlock(desk, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(!VillageBankManager.isManagedBankOperational(level, economy, 777), "missing desk restricts operation");
            require(economy.retiredBankAnchors(777).isEmpty(), "no relocation caused by one missing desk");
            level.setBlock(desk, changed.get(desk), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(ownerId.equals(economy.villageIdForBankRegion(
                    VillageBankManager.bankDeskAccess(level, desk, economy).bankRegionKey())), "replaced desk retains village");
        } finally {
            changed.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
        System.out.println("PASS BankVillageOwnershipSelfTest: distant desk fallback, cosmetic roof, functional damage, bell exclusion, reconciliation and replacement desk");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
