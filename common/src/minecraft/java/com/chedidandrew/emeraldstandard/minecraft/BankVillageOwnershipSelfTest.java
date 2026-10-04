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
        int version=economy.generatedBankStructureVersion(777);
        var approved=version>=13?ApprovedBankStructures.plan(BankStyleLedger.get(level).style(origin.asLong()),version):null;
        Optional<BlockPos> nativeBell=approved==null?Optional.of(origin.offset(2,2,-3)):approved.cells().entrySet().stream()
                .filter(e->e.getValue().is(Blocks.BELL)).map(e->origin.offset(e.getKey())).findFirst();
        // Not every reviewed Bank has a bell. Keep the legacy ghost-repair test on a
        // separate legacy Bank, rather than inventing a bell in the approved architecture.
        long bellRegion=nativeBell.isPresent()?777:779;
        BlockPos bell=nativeBell.orElse(origin.west(60).offset(2,2,-3));
        if(nativeBell.isEmpty()) {
            require(economy.markGeneratedBankRegion(bellRegion,origin.west(60).offset(6,1,9).asLong(),null,12),"legacy bell fixture");
        }
        var owner = economy.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", bell.offset(40, 1, 96).asLong(), 777, anchor.asLong(),
                18, 16, 0, false, List.of()));
        UUID ownerId = owner.village().villageId;
        BlockPos unloaded = new BlockPos(20_000_000, origin.getY(), 20_000_000);
        require(!VillageProsperityManager.censusChunksLoaded(level, unloaded, 48)
                && !level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4),
                "unknown resident chunks defer census without loading terrain");
        require(economy.associateBankRegionWithVillage(777, ownerId, anchor.asLong()), "assigned village");
        if(bellRegion!=777) require(economy.associateBankRegionWithVillage(bellRegion,ownerId,
                economy.generatedBankAnchor(bellRegion)),"legacy bell shares original village");
        var ghost = economy.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", bell.asLong(), 778, 0, 0, 0, 0, false, List.of()));
        require(!ghost.village().villageId.equals(ownerId), "fixture reproduces original distant ghost");
        require(VillageBankManager.isManagedBankOperational(level, economy, 777),
                "authored Bank operational: " + VillageBankManager.bankOperationProblem(level, economy, 777L));
        Map<BlockPos, BlockState> changed = new HashMap<>();
        try {
            if(nativeBell.isEmpty()) {
                level.getChunk(bell);
                changed.put(bell,level.getBlockState(bell));changed.put(bell.below(),level.getBlockState(bell.below()));
                level.setBlock(bell.below(),Blocks.COBBLESTONE.defaultBlockState(),Block.UPDATE_ALL);
                level.setBlock(bell,Blocks.BELL.defaultBlockState(),Block.UPDATE_ALL);
            }
            for (int x = approved==null?-1:approved.minX(); x <= (approved==null?13:approved.maxX()); x++)
                for (int z = approved==null?-1:approved.minZ(); z <= (approved==null?11:approved.maxZ()); z++)
                    for (int y = 4; y <= (approved==null?11:approved.height()+2); y++) {
                BlockPos cell = origin.offset(x, y, z);
                BlockState state = level.getBlockState(cell);
                if (state.getBlock() instanceof StairBlock) {
                    changed.put(cell, state);
                    level.setBlock(cell, state.setValue(StairBlock.FACING,
                            state.getValue(StairBlock.FACING).getOpposite()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            long expectedRoof=approved==null?41:approved.cells().entrySet().stream()
                    .filter(e->e.getKey().getY()>=4&&e.getValue().getBlock() instanceof StairBlock).count();
            require(changed.size() >= expectedRoof && changed.size()>1, "test whole authored roof, not only one stair");
            require(VillageBankManager.isManagedBankOperational(level, economy, 777), "roof rotations cosmetic");
            BlockPos relativeStanding=approved==null?new BlockPos(6,1,3):approved.air().stream()
                    .filter(p->p.getY()==1&&approved.air().contains(p.above())
                            &&approved.cells().containsKey(p.below())
                            &&approved.cells().get(p.below()).isFaceSturdy(level,origin.offset(p.below()),net.minecraft.core.Direction.UP))
                    .min(Comparator.comparingDouble(p->p.distSqr(new BlockPos(6,1,8)))).orElseThrow();
            BlockPos obstruction = origin.offset(relativeStanding);
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
            var target = new com.chedidandrew.emeraldstandard.core.VillageDistrictMap.View(-1000,500,64);
            for (int i = 0; i < 100; i++)
                for (int button : com.chedidandrew.emeraldstandard.core.DistrictMapRequest.encode(target))
                    require(menu.clickMenuButton(player,button),"valid map view accepted");
            require(revisionField.getInt(menu) == revision, "map request flood is throttled");
            var lastMapField=BankerMenu.class.getDeclaredField("lastMapTick");lastMapField.setAccessible(true);
            lastMapField.setLong(menu,level.getGameTime()-5);
            menu.broadcastChanges();
            require(menu.districtMap().view().equals(target),"last throttled viewport request eventually delivered");
            revision=revisionField.getInt(menu);
            require(menu.clickMenuButton(player, BankerMenu.BUTTON_MAP_CLOSE), "close map");
            for (int button : com.chedidandrew.emeraldstandard.core.DistrictMapRequest.encode(target))
                require(!menu.clickMenuButton(player,button),"closed map rejects viewport requests");
            menu.broadcastChanges();
            require(revisionField.getInt(menu) == revision, "hidden map does not refresh");
            var afterMap = economy.villageSnapshot(ownerId).village();
            require(beforeMap.foodSupply == afterMap.foodSupply && beforeMap.treasury == afterMap.treasury
                    && beforeMap.projects.size() == afterMap.projects.size(), "map cannot spend or build");
            player.containerMenu = player.inventoryMenu;
            require(!menu.clickMenuButton(player, BankerMenu.BUTTON_MAP_OPEN), "stale menu map request rejected");
            level.setBlock(obstruction, changed.get(obstruction), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(VillageBankManager.isManagedBankOperational(level, economy, 777), "unblocking resumes service");
            BlockPos floor = obstruction.below();
            changed.put(floor, level.getBlockState(floor));
            level.setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(!VillageBankManager.isManagedBankOperational(level, economy, 777)
                    && VillageBankManager.bankOperationProblem(level, economy, 777L).contains("floor"), "floor hole unsafe");
            level.setBlock(floor, changed.get(floor), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            var method = VillageProsperityManager.class.getDeclaredMethod("stableCenter",
                    ServerLevel.class, List.class, BlockPos.class, Set.class);
            method.setAccessible(true);
            var excluded = VillageBankManager.generatedBankBellRegions(level,economy);
            require(Long.valueOf(bellRegion).equals(excluded.get(bell.asLong())), "exact exterior Bank bell owned");
            if(approved!=null) require(approved.cells().entrySet().stream().filter(e->e.getValue().is(Blocks.BELL))
                    .allMatch(e->Long.valueOf(777).equals(excluded.get(origin.offset(e.getKey()).asLong()))),"all reviewed Bank bells excluded");
            require(method.invoke(null, level, List.of(), bell, excluded.keySet()) == null,
                    "Bank bells alone cannot establish a settlement");
            require(economy.reconcileEmptyBankBellVillage(ghost.village().villageId, bellRegion, bell.asLong()),
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
