package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Replays the reported mine approach and market destination from frozen beta.33 design inputs. */
final class WalkwayEntranceSelfTest {
    static void verify(ServerLevel level) {
        var old = WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE, WalkwayConnectionLedger.get(level)).getOrThrow();
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE, new WalkwayConnectionLedger());
            var village = village();
            var mine = mine();
            BlockPos origin = new BlockPos(2112, level.getMaxY() - 32, 2112);
            for (int x = -4; x <= 17; x++) for (int z = -32; z <= 17; z++)
                for (int y = -2; y <= 14; y++) set(level, before, origin.offset(x, y, z),
                        (y < -1 ? Blocks.STONE : y == -1 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState());
            List<?> cells = (List<?>) ConstructionSupportRecoverySelfTest.invoke("projectTemplate", level, origin, village, mine);
            require(cells.size() == 765, "Exact reported mine operation count");
            for (Object cell : cells) set(level, before, target(origin, cell), state(cell));
            mine.originPos = origin.asLong();
            village.projects.add(mine);
            BlockPos start = VillageProsperityManager.projectEntrance(origin, mine).below();
            require(start.equals(origin.offset(5, -1, -2)), "Reported -200,-61,257 entrance offset");
            BlockPos rail = start.above(2);
            require(level.getBlockState(rail).getBlock() instanceof BaseRailBlock, "Reported mine rail still present");
            BlockState railBefore = level.getBlockState(rail), footBefore = level.getBlockState(rail.below());
            for (int z = -3; z >= -24; z--) set(level, before, origin.offset(5, -1, z), Blocks.DIRT_PATH.defaultBlockState());
            var lot = new EconomyService.VillageProjectLot(origin.offset(-1, -4, -3).asLong(), origin.offset(11, 12, 12).asLong());
            var request = new WalkwayConnections.Request(village.villageId, mine.projectId, mine.originPos,
                    start, origin.offset(5, -1, -24), false, Set.of(), List.of(lot), List.of(), false);
            BlockPos admitted = WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh());
            require(rail.below().equals(admitted), "Mine rail entrance rejected: footing=" + footBefore
                    + " lower=" + railBefore + " admitted=" + admitted);
            boolean reloaded = false;
            for (int i = 0; i < 500 && !WalkwayConnectionLedger.get(level).job(request.key()).done(); i++) {
                require(WalkwayConnections.advance(level, request, 1000 + i * 20L, 2) <= 2, "Bounded writes");
                var job = WalkwayConnectionLedger.get(level).job(request.key());
                if (!reloaded && !job.plan().isEmpty() && job.cursor() > 0) {
                    var copy = WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,
                            WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE, WalkwayConnectionLedger.get(level)).getOrThrow()).getOrThrow();
                    require(copy.job(request.key()).equals(job), "Rail path snapshot survives reload");
                    level.getDataStorage().set(WalkwayConnectionLedger.TYPE, copy);
                    reloaded = true;
                }
            }
            var finished = WalkwayConnectionLedger.get(level).job(request.key());
            require(finished.done() && reloaded, "Mine path stalled: " + finished.reason());
            require(level.getBlockState(rail).equals(railBefore) && level.getBlockState(rail.below()).equals(footBefore),
                    "Connector must never remove rail or replace its support");
            require(!finished.supplied().contains(rail.asLong()) && !finished.supplied().contains(rail.below().asLong()),
                    "Read-only rail crossing must not claim existing fixtures");
            set(level, before, rail.above(), Blocks.OAK_LOG.defaultBlockState());
            require(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh()) == null,
                    "Rail exception must not waive blocked headroom");
            set(level, before, rail.above(), Blocks.AIR.defaultBlockState());
            try (var claim = VillageDevelopmentProtection.register(c -> !c.position().equals(rail.below()))) {
                require(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh()) == null,
                        "Rail exception must not bypass claims");
            }
            try (var claim = VillageDevelopmentProtection.register(c -> !c.position().equals(rail))) {
                require(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh()) == null,
                        "Rail's own protected cell must still be checked");
            }
            for (Block track : List.of(Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL)) {
                for (Rotation rotation : Rotation.values()) {
                    BlockState rotated = track.defaultBlockState().rotate(rotation);
                    set(level, before, rail, rotated);
                    require(rail.below().equals(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh())),
                            "Retain dry collision-free tracks on existing support: " + rotated);
                }
            }
            set(level, before, rail, railBefore.setValue(BaseRailBlock.WATERLOGGED, true));
            require(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh()) == null,
                    "Flooded tracks cannot waive fluid safety");
            set(level, before, rail, Blocks.CHEST.defaultBlockState());
            require(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh()) == null,
                    "Inventory is not a walk-through fixture");
            set(level, before, rail, railBefore);
            set(level, before, rail.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            require(WalkwayConnections.surface(level, request, start, WalkwayConnectionLedger.Job.fresh()) == null,
                    "Do not change natural footing beneath an existing rail to create a route");
            set(level, before, rail.below(), footBefore);
            verifyDestinations(level);
            System.out.println("PASS reported walkway entrances: exact 765-cell mine, retained rail/support, bounded paving/reload, blocked headroom/claims, and connected market destinations");
        } catch (Exception ex) {
            throw new IllegalStateException("Reported mine/market walkway fixture", ex);
        } finally {
            before.forEach((p, s) -> level.setBlock(p, s, 18));
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE, WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow());
        }
    }

    private static void verifyDestinations(ServerLevel level) {
        var village = village();
        var mine = mine();
        var market = project(6, VillageProsperityEngine.ProjectType.MARKET_SQUARE,
                "market_crossroads_03", -48103632592956L, 3);
        market.designStage = 1; market.designSeed = 100098473682884909L; market.designDressingId = "lived_in";
        // The original older buildings and their exact origins/orientations determine the nearest target.
        village.projects.add(project(1, VillageProsperityEngine.ProjectType.COTTAGE, "cottage_garden_02", -61022894538812L, 2));
        village.projects.add(project(2, VillageProsperityEngine.ProjectType.WAREHOUSE, "warehouse_wharf_04", -74217033949244L, 1));
        village.projects.add(mine);
        village.projects.add(project(4, VillageProsperityEngine.ProjectType.HOUSE, "house_hall_04", -69544109326396L, 0));
        village.projects.add(project(5, VillageProsperityEngine.ProjectType.INN, "inn_wayfarer_03", -65420941180988L, 2));
        village.projects.add(market);
        var ledger = WalkwayConnectionLedger.get(level);
        for (var p : village.projects) {
            var done = p.projectId != 3 && p.projectId != 6;
            ledger.put(WalkwayConnectionLedger.key(village.villageId, p.projectId, p.originPos),
                    new WalkwayConnectionLedger.Job(List.of(), 0, 0, Set.of(), done, 0, done ? "Connected" : "Blocked"));
        }
        var request = VillageProsperityManager.walkwayRequest(level, village, market, List.of(), List.of());
        BlockPos blockedMine = VillageProsperityManager.projectEntrance(BlockPos.of(mine.originPos), mine).below();
        require(!request.streetGoal() && !request.destination().equals(blockedMine), "Market chose the disconnected mine as its destination");
        BlockPos marketStart = VillageProsperityManager.projectEntrance(BlockPos.of(market.originPos), market).below();
        require(!request.destination().equals(marketStart), "A connector cannot join itself");
        for (var p : village.projects) ledger.put(WalkwayConnectionLedger.key(village.villageId, p.projectId, p.originPos),
                WalkwayConnectionLedger.Job.fresh());
        require(VillageProsperityManager.walkwayRequest(level, village, market, List.of(), List.of()).streetGoal(),
                "No connected branch must fall back to real village roads, not an isolated building");
        ledger.put(WalkwayConnectionLedger.key(village.villageId, market.projectId, market.originPos),
                new WalkwayConnectionLedger.Job(List.of(), 0, 0, Set.of(), true, 0, "Connected"));
        require(VillageProsperityManager.walkwayRequest(level, village, mine, List.of(), List.of()).destination().equals(marketStart),
                "An older stranded mine can join a newer connected market");
        ledger.put(WalkwayConnectionLedger.key(village.villageId, mine.projectId, mine.originPos),
                new WalkwayConnectionLedger.Job(List.of(), 0, 0, Set.of(), true, 0, "Connected"));
        require(VillageProsperityManager.walkwayRequest(level, village, market, List.of(), List.of()).destination().equals(blockedMine),
                "The mine becomes a valid destination after its connector finishes");
    }

    private static EconomyState.VillageRecord village() {
        var v = new EconomyState.VillageRecord();
        v.villageId = UUID.fromString("785a5083-1196-329d-b859-ccee143cd16a");
        v.architectureCharacter = "rustic"; v.architectureDialect = "plains"; v.developmentTier = 3;
        v.centerPos = new BlockPos(-227, -54, 235).asLong();
        return v;
    }
    private static EconomyState.VillageProject mine() {
        var p = project(3, VillageProsperityEngine.ProjectType.MINE_ENTRANCE, "mine_adit_03", -56349969858620L, 0);
        p.designMirrored = true; p.designSeed = 4846542165548330479L;
        p.designSignature = 8271781144575856L; p.designDressingId = "restrained";
        return p;
    }
    private static EconomyState.VillageProject project(long id, VillageProsperityEngine.ProjectType type, String template, long origin, int rotation) {
        var p = new EconomyState.VillageProject(); p.projectId = id; p.type = type; p.originPos = origin;
        p.designSchema = "blueprint_v2"; p.designTemplateId = template; p.designTemplateRevision = 10;
        p.designPaletteId = "timber_forward"; p.designDressingId = "restrained"; p.designRotation = rotation;
        p.economicComplete = p.materializedComplete = p.trailAnchorSet = p.trailMaterializedComplete = true;
        p.economicProgress = 1; p.trailAnchorPos = BlockPos.of(origin).north(24).asLong();
        return p;
    }
    private static BlockPos target(BlockPos origin, Object cell) throws Exception {
        return origin.offset((int) ConstructionSupportRecoverySelfTest.field(cell, "dx"),
                (int) ConstructionSupportRecoverySelfTest.field(cell, "dy"), (int) ConstructionSupportRecoverySelfTest.field(cell, "dz"));
    }
    private static BlockState state(Object cell) throws Exception { return (BlockState) ConstructionSupportRecoverySelfTest.field(cell, "state"); }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        level.getChunk(pos); before.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, 18);
    }
    private static void require(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
}
