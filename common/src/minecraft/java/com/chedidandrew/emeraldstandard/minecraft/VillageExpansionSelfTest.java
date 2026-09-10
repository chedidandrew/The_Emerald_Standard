package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Real block-policy checks, only in the opt-in isolated server smoke world. */
final class VillageExpansionSelfTest {
    private VillageExpansionSelfTest() { }
    static void verify(ServerLevel level) {
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        // Explicit chunk loading is fixture setup only; production placement never does this.
        level.getChunk(0, 0);
        BlockPos floor = new BlockPos(8, level.getMaxY() - 24, 8);
        try {
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) for (int y = -3; y <= 0; y++)
                set(level, before, floor.offset(x, y, z), y == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState());
            require(safe(level, floor), "clean natural lot");
            for (int x = -2; x <= 2; x += 2) for (int z = -2; z <= 2; z += 2)
                set(level, before, floor.offset(x, 1, z), Blocks.TORCH.defaultBlockState());
            require(safe(level, floor), "torch-covered lot must remain available");
            BlockPos test = floor.above();
            set(level, before, test, Blocks.CHEST.defaultBlockState());
            require(!safe(level, floor) && !VillageSitePreparation.clearable(level, test), "storage preserved");
            set(level, before, test, Blocks.CRAFTING_TABLE.defaultBlockState());
            require(!safe(level, floor), "workstation preserved");
            set(level, before, test, Blocks.FURNACE.defaultBlockState());
            require(!safe(level, floor), "furnace preserved");
            set(level, before, test, Blocks.OAK_PLANKS.defaultBlockState());
            require(!safe(level, floor), "player house wall preserved");
            set(level, before, test, Blocks.AIR.defaultBlockState());
            set(level, before, floor, Blocks.AIR.defaultBlockState());
            set(level, before, floor.below(), Blocks.AIR.defaultBlockState());
            require(safe(level, floor), "shallow mined/blast crater is bridged");
            set(level, before, floor, Blocks.GRASS_BLOCK.defaultBlockState());
            set(level, before, floor.below(), Blocks.STONE.defaultBlockState());
            BlockPos trunk = floor.above(4), canopy = floor.above(6);
            set(level, before, trunk, Blocks.OAK_LOG.defaultBlockState());
            set(level, before, canopy, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
            require(VillageSitePreparation.clearable(level, trunk), "floating natural tree remnant recognized");
            require(safe(level, floor), "floating tree does not move the foundation into the air");
            set(level, before, trunk.east(), Blocks.CRAFTING_TABLE.defaultBlockState());
            require(!VillageSitePreparation.clearable(level, trunk), "built workstation cluster defeats tree heuristic");
            set(level, before, trunk.east(), Blocks.AIR.defaultBlockState());
            set(level, before, trunk, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            require(!VillageSitePreparation.clearable(level, trunk), "horizontal timber framing preserved");
            set(level, before, canopy, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            require(!VillageSitePreparation.clearable(level, canopy), "player-placed leaf landscaping preserved");
            try (var veto = VillageDevelopmentProtection.register(context -> false)) {
                require(!VillageDevelopmentProtection.mayPlace(level, null, 1, test,
                        Blocks.TORCH.defaultBlockState(), Blocks.AIR.defaultBlockState()), "claim veto remains authoritative");
            }
            System.out.println("PASS VillageExpansionSelfTest: torches, storage, workstations, craters, tree remnants, claims");
            verifyRoughTerrain(level);
            VillageTerrainFinishingSelfTest.verify(level);
            VillageConstructionActivitySelfTest.verify(level);
            verifyFirstDistrict(level);
        } catch (Exception exception) {
            throw new IllegalStateException("Village expansion runtime check failed", exception);
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static void verifyRoughTerrain(ServerLevel level) throws Exception {
        BlockPos floor = new BlockPos(1000, level.getMaxY() - 80, 1000);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                level.getChunk(floor.offset(x, 0, z)); // Disposable fixture only.
                for (int y = -5; y <= 8; y++) set(level, before, floor.offset(x, y, z),
                        (y <= 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
            BlockPos trunk = floor.above();
            for (int y = 0; y < 25; y++) set(level, before, trunk.above(y), Blocks.SPRUCE_LOG.defaultBlockState());
            BlockPos branch = trunk.above(23).east();
            set(level, before, branch, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            set(level, before, branch.above(), Blocks.SPRUCE_LEAVES.defaultBlockState());
            set(level, before, floor.offset(2, 1, 0), Blocks.SWEET_BERRY_BUSH.defaultBlockState());
            set(level, before, floor.offset(-2, 1, 0), Blocks.BUSH.defaultBlockState());
            set(level, before, floor.offset(1, 1, 2), Blocks.AZALEA.defaultBlockState());
            require(safe(level, floor), "tall spruce, horizontal branch and natural bushes do not reject a lot");
            var survey = new VillageSitePreparation.Survey(level);
            var frozen = survey.freeze(List.of(trunk), floor.getY() + 1, null, 9);
            require(frozen != null && frozen.cells().size() == 26
                    && frozen.cells().stream().anyMatch(c -> c.position() == branch.asLong()),
                    "connected tree outside the small footprint is frozen, avoiding floating branches");
            require(VillageSitePreparation.matchesRemoval(Blocks.SPRUCE_LEAVES.defaultBlockState()
                            .setValue(LeavesBlock.DISTANCE, 7), "minecraft:spruce_leaves[distance=1,persistent=false,waterlogged=false]"),
                    "leaf-distance changes must not stall frozen removal");
            require(!VillageSitePreparation.matchesRemoval(Blocks.SPRUCE_LEAVES.defaultBlockState()
                            .setValue(LeavesBlock.PERSISTENT, true), "minecraft:spruce_leaves[distance=1,persistent=false,waterlogged=false]"),
                    "new persistent player landscaping cannot replace frozen natural leaves");
            set(level, before, branch.east(), Blocks.CHEST.defaultBlockState());
            require(!VillageSitePreparation.clearable(level, trunk), "treehouse storage veto propagates through whole trunk");
            set(level, before, branch.east(), Blocks.AIR.defaultBlockState());
            for (var cell : frozen.cells()) set(level, before, BlockPos.of(cell.position()), Blocks.AIR.defaultBlockState());
            set(level, before, branch.above(), Blocks.AIR.defaultBlockState());
            for (int x = -3; x <= 3; x++) for (int z = -4; z <= 3; z++)
                for (int y = 1; y <= x + 3; y++) set(level, before, floor.offset(x, y, z), Blocks.STONE.defaultBlockState());
            require(safe(level, floor), "six-block rough hillside accepted through cut/fill");
            var hillSurvey = new VillageSitePreparation.Survey(level);
            BlockPos cut = floor.offset(3, 5, 0);
            require(hillSurvey.excavatable(cut, floor.getY() + 4), "bounded natural excavation accepted");
            try (var veto = VillageDevelopmentProtection.register(context -> false)) {
                require(hillSurvey.freeze(List.of(cut), floor.getY() + 4, null, 9) == null,
                        "claim veto applies to terrain cutting");
            }
            set(level, before, cut.east(), Blocks.WATER.defaultBlockState());
            require(!new VillageSitePreparation.Survey(level).excavatable(cut, floor.getY() + 4),
                    "excavation cannot breach adjacent water");
            set(level, before, cut.east(), Blocks.AIR.defaultBlockState());
            for (int y = 7; y <= 10; y++) set(level, before, floor.offset(3, y, 0), Blocks.STONE.defaultBlockState());
            require(!safe(level, floor), "ten-block cliff still rejected rather than left unsupported");
            BlockPos unloaded = new BlockPos(1_000_000, floor.getY(), 1_000_000);
            require(!level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4), "unloaded fixture frontier");
            require(new VillageSitePreparation.Survey(level).surface(unloaded.getX(), unloaded.getZ()) == null
                    && !VillageSitePreparation.clearable(level, unloaded)
                    && !level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4), "terrain survey never force loads frontier");
            System.out.println("PASS VillageExpansionSelfTest: tall branched trees, bushes, frozen whole trunks, treehouse veto, six-block hillside, water and cliff guards");
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static void verifyFirstDistrict(ServerLevel level) throws Exception {
        // A separate, high-altitude fixture in this disposable smoke world exercises actual
        // production construction. It never loads or edits a player's development/test save.
        BlockPos origin = new BlockPos(0, level.getMaxY() - 48, 0);
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) level.getChunk(x, z);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        ServerPlayer observer = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "CitySmoke"), ClientInformation.createDefault());
        // Observe within development range but outside particle broadcast range. This is a
        // server-only fixture observer, not a logged-in network client.
        observer.setPos(200.5, origin.getY() + 1, 8.5);
        UUID childId = null;
        try {
            for (int x = -15; x <= 40; x++) for (int z = -15; z <= 40; z++) {
                for (int y = -4; y <= 30; y++) set(level, before, origin.offset(x, y, z),
                        y < 0 ? (y == -1 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState()
                                : Blocks.AIR.defaultBlockState());
            }
            set(level, before, origin.offset(4, 0, 4), Blocks.TORCH.defaultBlockState());
            for (int y = 0; y <= 18; y++) set(level, before, origin.offset(4, y, 4), Blocks.BIRCH_LOG.defaultBlockState());
            set(level, before, origin.offset(4, 19, 4), Blocks.BIRCH_LEAVES.defaultBlockState());
            for (int x = 6; x <= 8; x++) for (int z = 3; z <= 7; z++)
                set(level, before, origin.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
            var state = EconomyState.fresh(1234, System.currentTimeMillis(), 0);
            state.economicDay = 100;
            UUID rootId = new UUID(124, 578);
            var root = state.village(rootId);
            root.dimensionKey = "minecraft:overworld";
            root.centerPos = origin.offset(256, 0, 0).asLong();
            root.population = root.observedPopulation = 18;
            root.housingCapacity = root.observedHousingCapacity = 24;
            root.foodSupply = root.materialSupply = 2_000;
            root.treasury = 600; root.developmentTier = 3;
            root.prosperity = 85; root.safety = 80; root.expansionHealthyDays = 3;
            root.lastCensusDay = root.lastSimulatedDay = 100;
            root.architectureDialect = "plains";
            var directory = Files.createTempDirectory("tes-city-runtime-");
            state.save(directory.resolve("the_emerald_standard.properties"));
            var economy = new EconomyService();
            economy.configureEconomicClock(false, 30);
            economy.configureVillageProsperity(true, true);
            economy.start(directory, 1234, 0);
            var draft = economy.draftVillageDistrict(rootId, origin.offset(5, 0, 5).asLong());
            require(draft != null, "runtime charter ready");
            childId = draft.villageId;
            var project = draft.projects.getFirst();
            verifyRotationFallback(level, economy, draft.copy(), origin);
            project.trailAnchorSet = true; project.trailAnchorPos = draft.centerPos;
            project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            project.entranceApproachComplete = true;
            List<?> template = (List<?>) invoke("projectTemplate", level, origin, draft, project);
            Object bounds = invoke("bounds", origin, template);
            project.boundsMinPos = ((BlockPos) accessor(bounds, "minimum")).asLong();
            project.boundsMaxPos = ((BlockPos) accessor(bounds, "maximum")).asLong();
            project.designPlanHash = (String) invoke("blueprintPlanHash", draft, project,
                    invoke("blueprintPlacementPlan", level, origin, draft, project));
            project.originPos = origin.asLong(); project.totalBlocks = template.size();
            project.trailTotalBlocks = ((List<?>) invoke("managedProjectTrail", origin, draft, project)).size();
            project.trailCenterSurfaceVersion = EconomyState.TRAIL_CENTER_SURFACE_VERSION;
            project.sitePreparationComplete = false;
            project.constructionStarted = false;
            project.sitePreparationPlan = (com.chedidandrew.emeraldstandard.core.SitePreparationPlan)
                    invoke("prepareProjectSitePlan", level, origin, draft, project, template);
            require(project.sitePreparationPlan != null && !project.sitePreparationPlan.cells().isEmpty(),
                    "starter house freezes tree and hillside preparation");
            require(economy.commitVillageDistrict(rootId, 0, draft), "runtime charter persisted: " + economy.lastError());
            var firstRemoval = project.sitePreparationPlan.cells().getFirst();
            BlockPos firstPos = BlockPos.of(firstRemoval.position());
            BlockState initial = level.getBlockState(firstPos);
            set(level, before, firstPos, Blocks.CHEST.defaultBlockState());
            require((int) invoke("prepareNewSite", level, economy, draft, project, 1) == 0
                    && level.getBlockState(firstPos).is(Blocks.CHEST), "new storage cannot be excavated by a frozen plan");
            set(level, before, firstPos, initial);
            try (var veto = VillageDevelopmentProtection.register(context -> false)) {
                require((int) invoke("prepareNewSite", level, economy, draft, project, 1) == 0,
                        "claim registered after reservation stops clearing");
            }
            require((int) invoke("prepareNewSite", level, economy, draft, project, 1) == 1,
                    "one preparation block per pulse");
            require(economy.villageSnapshot(childId).village().projects.getFirst().constructionStarted,
                    "start evidence persisted before terrain write");
            var prepRestart = new EconomyService(); prepRestart.configureEconomicClock(false, 30);
            prepRestart.configureVillageProsperity(true, true); prepRestart.start(directory, 1234, 0);
            economy = prepRestart;
            require(economy.villageSnapshot(childId).village().projects.getFirst().sitePreparationPlan.equals(project.sitePreparationPlan),
                    "partially cleared project resumes exact frozen work after restart");
            // Advance economic labor only; no synthetic villagers or completed structures.
            for (int day = 1; day <= 120; day++) {
                require(economy.tick(day * 24_000L), "economic labor tick");
                if (economy.villageSnapshot(childId).village().projects.getFirst().economicComplete) break;
            }
            var beforeBuild = economy.villageSnapshot(childId).village();
            require(beforeBuild.population == 0 && beforeBuild.projects.getFirst().economicComplete,
                    "funded crew completes economic work before residents exist");
            var budgetType = Class.forName(VillageProsperityManager.class.getName() + "$MaterializationBudget");
            var constructor = budgetType.getDeclaredConstructor(int.class, int.class);
            constructor.setAccessible(true);
            level.players().add(observer);
            for (int pulse = 0; pulse < 20_000; pulse++) {
                invoke("materializeDevelopment", level, economy, EmeraldConfig.current(), pulse * 10L,
                        constructor.newInstance(1, Integer.MAX_VALUE));
                if (economy.villageSnapshot(childId).village().projects.getFirst().materializedComplete) break;
            }
            var built = economy.villageSnapshot(childId).village();
            var home = built.projects.getFirst();
            require(home.sitePreparationComplete && home.materializedComplete,
                    "real starter home completed: " + home.materializedBlocks + "/" + home.totalBlocks
                            + ", prep=" + home.sitePreparationCursor + ", error=" + economy.lastError());
            VillageWalkingSelfTest.building(level, BlockPos.of(home.boundsMinPos), BlockPos.of(home.boundsMaxPos), "starter cottage");
            invoke("spawnPendingSettler", level, economy, built, EmeraldConfig.current(), 100_000L);
            UUID expected = childId;
            require(!level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(48),
                    villager -> expected.equals(VillageProsperityManager.villageId(villager))).isEmpty(),
                    "actual settler arrives only after real home and bed");
            require(economy.saveNow(), "constructed district saves");
            var restarted = new EconomyService(); restarted.configureEconomicClock(false, 30);
            restarted.start(directory, 1234, 120 * 24_000L);
            require(restarted.villageSnapshot(childId).village().projects.getFirst().materializedComplete,
                    "constructed district survives restart");
            System.out.println("PASS VillageExpansionSelfTest: durable charter -> funded labor -> prepared lot -> real home -> actual settler -> restart");
        } finally {
            level.players().remove(observer);
            UUID cleanupId = childId;
            level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(48),
                    villager -> cleanupId != null && cleanupId.equals(VillageProsperityManager.villageId(villager)))
                    .forEach(Villager::discard);
            before.forEach((pos, block) -> level.setBlock(pos, block, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static Object invoke(String name, Object... args) throws Exception {
        for (var method : VillageProsperityManager.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(null, args);
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static void verifyRotationFallback(ServerLevel level, EconomyService economy,
            EconomyState.VillageRecord draft, BlockPos fixture) throws Exception {
        var project = draft.projects.getFirst();
        var offsets = VillageNeighborhoodPlan.offsets(project.materializationFailures, draft.villageId);
        int start = Math.floorMod((int) (project.projectId ^ draft.villageId.hashCode()), offsets.size());
        project.siteSearchCursor = offsets.size() - 1; // Exactly one remaining position; rotation must rescue it.
        var offset = offsets.get((start + project.siteSearchCursor) % offsets.size());
        draft.centerPos = fixture.offset(5 - offset.x(), 0, 5 - offset.z()).asLong();
        var doors = new java.util.concurrent.atomic.AtomicInteger();
        try (var veto = VillageDevelopmentProtection.register(context ->
                !(context.proposed().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                        || doors.getAndIncrement() > 0)) {
            var result = invoke("findProjectOrigin", level, economy, draft, project, List.of(), List.of());
            require(accessor(result, "availability") == VillageMaterializationPolicy.SiteAvailability.AVAILABLE
                            && doors.get() > 1 && accessor(result, "preparation") != null,
                    "another orientation rescues the same site after its first entrance is vetoed");
        }
        System.out.println("PASS VillageExpansionSelfTest: production site search rotates a vetoed entrance at the same position");
    }
    private static Object accessor(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        if (!before.containsKey(pos)) {
            require(level.getBlockEntity(pos) == null, "fixture must not replace existing storage");
            before.put(pos.immutable(), level.getBlockState(pos));
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static boolean safe(ServerLevel level, BlockPos floor) throws Exception {
        Class<?> size = Class.forName(VillageProsperityManager.class.getName() + "$StructureSize");
        var constructor = size.getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        var method = VillageProsperityManager.class.getDeclaredMethod("safeOrigin",
                ServerLevel.class, int.class, int.class, size, List.class);
        method.setAccessible(true);
        Object result = method.invoke(null, level, floor.getX(), floor.getZ(), constructor.newInstance(5, 5, 10), List.of());
        var availability = result.getClass().getDeclaredMethod("availability");
        availability.setAccessible(true);
        return availability.invoke(result) == VillageMaterializationPolicy.SiteAvailability.AVAILABLE;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
