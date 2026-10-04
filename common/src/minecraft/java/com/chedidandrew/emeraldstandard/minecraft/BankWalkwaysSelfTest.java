package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Run after the full Bank's handover: exercise the real shared queue, not a fabricated road request. */
final class BankWalkwaysSelfTest {
    static void verify(ServerLevel level, EconomyService economy, BlockPos origin) throws Exception {
        var previous = WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE, WalkwayConnectionLedger.get(level)).getOrThrow();
        var previousLights = WalkwayLightingLedger.CODEC.encodeStart(NbtOps.INSTANCE, WalkwayLightingLedger.get(level)).getOrThrow();
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        UUID id = economy.villageIdForBankRegion(777);
        var village = economy.developmentVillageSnapshot(id).village();
        BlockPos center = BlockPos.of(village.centerPos);
        int version=economy.generatedBankStructureVersion(777);
        var style=BankStyleLedger.get(level).style(origin.asLong());
        var approved=version>=13?ApprovedBankStructures.plan(style,version):null;
        var bank = new BankWalkways.Bank(777, economy.generatedBankAnchor(777),version,style);
        BlockState entranceFloor=level.getBlockState(bank.start());
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "BankPathFixture"), ClientInformation.createDefault());
        List<Long> banks = List.copyOf(economy.generatedBankAnchorsSnapshot().values());
        try {
            VillageConstructionActivitySelfTest.cleanupCrews(level); // Simulate the post-handover perimeter cleanup pulse.
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE, new WalkwayConnectionLedger());
            level.getDataStorage().set(WalkwayLightingLedger.TYPE, new WalkwayLightingLedger());
            require(BankWalkways.candidates(level, economy, village).isEmpty(), "Bank path needs a nearby observer");
            player.setPos(center.getX() + .5, center.getY() + 20, center.getZ() + .5);
            level.players().add(player);
            require(BankWalkways.candidates(level, economy, village).equals(List.of(bank)), "completed saved Bank enters queue");
            var foreign = village.copy(); foreign.villageId = UUID.randomUUID();
            require(BankWalkways.candidates(level, economy, foreign).isEmpty(), "Bank must not connect a neighboring village");
            require(!bank.job(id).equals(WalkwayConnectionLedger.key(id, 1, bank.anchor())), "Bank/project receipts are distinct");
            player.setPos(center.getX() + EmeraldConfig.current().villageDevelopmentRadius() + 1.5, center.getY(), center.getZ() + .5);
            require(BankWalkways.candidates(level, economy, village).isEmpty(), "Bank walkway honors development radius");
            player.setPos(center.getX() + .5, center.getY() + 20, center.getZ() + .5);
            // Keep the real Bank intact. Provide flat supported land around it and a real road
            // near its original village, on the far side of the building from the front door.
            int minX=approved==null?-4:approved.minX()-3, maxX=approved==null?17:approved.maxX()+3;
            int minZ=approved==null?-8:approved.minZ()-1, maxZ=approved==null?15:approved.maxZ()+3;
            for (int x = Math.min(-24,minX-10); x <= Math.max(72,center.getX()-origin.getX()+10); x++)
                for (int z = Math.min(-30,minZ-10); z <= Math.max(108,center.getZ()-origin.getZ()+10); z++) {
                BlockPos column = origin.offset(x, 0, z); level.getChunk(column);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    for (int y = -2; y <= 6; y++) before.putIfAbsent(column.above(y), level.getBlockState(column.above(y)));
                    if (level.getBlockState(column.below(2)).isAir() && level.getBlockState(column.below()).is(Blocks.GRASS_BLOCK))
                        set(level, before, column.below(2), Blocks.STONE.defaultBlockState());
                    continue;
                }
                for (int y = -2; y <= 6; y++) set(level, before, column.above(y),
                        (y == -2 ? Blocks.STONE : y == -1 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState());
            }
            BlockPos road = new BlockPos(center.getX(), origin.getY() - 1, center.getZ());
            for (int x = -8; x <= 8; x++) set(level, before, road.east(x), Blocks.DIRT_PATH.defaultBlockState());
            BlockPos obstacle = approved==null?origin.offset(6,0,-9):bank.start().north(4).above();
            for (int y = 0; y < 3; y++) set(level, before, obstacle.above(y), Blocks.OAK_LOG.defaultBlockState());
            var request = BankWalkways.request(level, village, bank, List.of(), banks);
            require(request.streetGoal() && request.start().equals(bank.start()), "path starts at authored entrance-yard exit");
            require(!request.banks().contains(bank.anchor()), "own broad buffer would block the entrance");
            require(WalkwayLighting.excluded(approved==null?origin.offset(2,0,-3):origin.offset(approved.minX(),0,approved.maxZ()), request.lots(), request.banks()),
                    "benches and forecourt remain excluded");
            require(!WalkwayLighting.excluded(bank.start().north(3), request.lots(), request.banks()),
                    "entrance has a continuous paving corridor");
            var desert = village.copy(); desert.architectureDialect = VillageArchitecture.BiomeDialect.DESERT.id();
            require(BankWalkways.request(level, desert, bank, List.of(), banks).desert()==(approved==null||style==VillageArchitecture.BiomeDialect.DESERT),
                    "approved Bank retains its saved palette; legacy Bank follows village");
            for(var dialect:VillageArchitecture.BiomeDialect.values()) {
                var themed=village.copy(); themed.architectureDialect=dialect.id();
                require(BankWalkways.request(level,themed,bank,List.of(),banks).style().equals(
                        WalkwayStyle.forDialect(approved==null?dialect.id():style.id())),"Bank road must use saved family: "+dialect);
            }

            boolean reloaded = false;
            int writes = 0;
            for (int pulse = 0; pulse < 5000 && !WalkwayConnectionLedger.get(level).job(bank.job(id)).done(); pulse++) {
                long tick = 20000 + pulse * 20L;
                int n = VillageProsperityManager.materializeOneWalkwayConnection(
                        level, economy, village, List.of(), banks, tick, 2);
                require(n >= 0 && n <= 2, "shared paving allowance");
                writes += n;
                require(VillageProsperityManager.materializeOneWalkwayConnection(
                        level, economy, village, List.of(), banks, tick, 2) == 0, "no second job in the same dimension pulse");
                var job = WalkwayConnectionLedger.get(level).job(bank.job(id));
                if (!reloaded && job.cursor() > 5 && !job.plan().isEmpty()) {
                    var decoded = WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,
                            WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE, WalkwayConnectionLedger.get(level)).getOrThrow()).getOrThrow();
                    require(decoded.job(bank.job(id)).equals(job), "Bank road saves exact partial progress");
                    level.getDataStorage().set(WalkwayConnectionLedger.TYPE, decoded);
                    reloaded = true;
                }
            }
            var ledger = WalkwayConnectionLedger.get(level);
            require(ledger.job(bank.job(id)).done(), "Bank connector stalled: " + ledger.job(bank.job(id)).reason());
            var route = ledger.route(bank.job(id));
            require(reloaded && writes > 5 && route.getFirst().equals(bank.start()), "real progressive Bank road");
            require(route.getLast().getZ() == road.getZ(), "Bank forecourt is not mistaken for the village road");
            for (int i = 1; i < route.size(); i++) {
                BlockPos a = route.get(i - 1), b = route.get(i);
                require(Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) == 1
                        && Math.abs(a.getY() - b.getY()) <= 1, "continuous route around Bank");
            }
            require(level.getBlockState(obstacle).is(Blocks.OAK_LOG), "tree shortcut was not cleared");
            require(level.getBlockState(bank.start()).equals(entranceFloor)
                    ||(approved!=null&&VillageProsperityManager.isNaturalProjectGround(entranceFloor)
                    &&WalkwayConnections.road(level,bank.start(),level.getBlockState(bank.start()))),
                    "authored entrance footing preserved or natural yard exit paved");
            require(VillageBankManager.isManagedBankOperational(level,economy,777),
                    "safe paved yard exit must not disable the Bank: "+VillageBankManager.bankOperationProblem(level,economy,777L));
            for (int pulse = 0; pulse < 1600 && !WalkwayLightingLedger.get(level).done(bank.job(id)); pulse++) {
                int n = VillageProsperityManager.materializeOneWalkwayLamp(
                        level, economy, village, List.of(), banks, 160000 + pulse * 20L, 2);
                require(n >= 0 && n <= 2, "shared lamp allowance");
            }
            require(WalkwayLightingLedger.get(level).done(bank.job(id)), "Bank path lighting finishes");
            require(!WalkwayLightingLedger.get(level).sites.isEmpty(), "Bank path receives lamps outside its courtyard");
            BlockPos supplied = BlockPos.of(ledger.job(bank.job(id)).supplied().iterator().next());
            set(level, before, supplied, Blocks.AIR.defaultBlockState());
            require(VillageProsperityManager.materializeOneWalkwayConnection(
                    level, economy, village, List.of(), banks, 200000, 2) == 0
                    && level.getBlockState(supplied).isAir(), "completed road is not regenerated");
            System.out.println("PASS BankWalkwaysSelfTest: saved Bank backfill, real front stair, village road, courtyard protection, "
                    + "tree detour, radius/ownership, shared budget, reload, lamps and no regeneration; " + route.size() + " center cells");
        } finally {
            level.players().remove(player); player.discard();
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE, WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE, previous).getOrThrow());
            level.getDataStorage().set(WalkwayLightingLedger.TYPE, WalkwayLightingLedger.CODEC.parse(NbtOps.INSTANCE, previousLights).getOrThrow());
        }
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        before.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
