package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Discovers loaded villages and adds one detailed bank with a persistent Banker nearby.
 *
 * <p>Generation happens when a player first loads an Overworld village region, so it works in
 * existing worlds without replacing vanilla village pools or requiring a new world.</p>
 */
public final class VillageBankManager {
    private static final int BANK_WIDTH = 13;
    private static final int BANK_DEPTH = 11;
    private static final int BANK_HEIGHT = 9;
    private static final int BANK_PLOT_MIN_X = -1;
    private static final int BANK_PLOT_MAX_X = BANK_WIDTH;
    private static final int BANK_PLOT_MIN_Z = -4;
    private static final int BANK_PLOT_MAX_Z = BANK_DEPTH;
    private static final int BANK_APPROACH_ARRIVAL_Z = -5;
    private static final int BANK_ENTRANCE_HALF_WIDTH = 1;
    private static final int BANK_STRUCTURE_VERSION = 2;
    private static final long FALLBACK_BANK_RETRY_INTERVAL_TICKS = 2_400L;
    private static final long BANK_UPGRADE_RETRY_INTERVAL_TICKS = 2_400L;
    private static final Map<Long, Long> LAST_FALLBACK_BANK_RETRY_TICK = new HashMap<>();
    private static final Map<Long, Long> LAST_BANK_UPGRADE_RETRY_TICK = new HashMap<>();

    private VillageBankManager() {
    }

    /** Clears world-session-only retry pacing between integrated or dedicated server instances. */
    public static void resetRuntimeState() {
        LAST_FALLBACK_BANK_RETRY_TICK.clear();
        LAST_BANK_UPGRADE_RETRY_TICK.clear();
    }

    public static void tick(MinecraftServer server, EconomyService economy) {
        EmeraldConfig config = EmeraldConfig.current();
        if (!config.villageBanksEnabled()) {
            return;
        }
        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        if (gameTime % config.villageScanIntervalTicks() != 0L) {
            return;
        }

        Set<Long> processedBanks = new HashSet<>();
        VillageBankPlacementPolicy.UpgradeAttemptGate bankUpgradeGate =
                new VillageBankPlacementPolicy.UpgradeAttemptGate();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level || !level.isVillage(player.blockPosition())) {
                continue;
            }
            BlockPos playerPosition = player.blockPosition();
            EconomyService.VillageSnapshot village = economy.nearestVillageSnapshot(
                    "minecraft:overworld", playerPosition.asLong(), 128.0);
            if (village == null
                    && (config.villageProsperitySimulationEnabled()
                            || config.villageVisualProgressionEnabled())) {
                // Prosperity discovery owns the stable settlement center and identity. Its
                // default census is slower than the bank scan, so wait instead of permanently
                // keying a new bank from whichever player's position happened to be seen first.
                continue;
            }
            UUID villageId = village == null ? null : village.village().villageId;
            // The player's location is only a discovery probe. Once the prosperity system has a
            // stable settlement identity, its persisted center owns keying and site selection.
            BlockPos villagePosition = village == null
                    ? playerPosition
                    : BlockPos.of(village.village().centerPos);
            long bankKey = bankKeyForVillage(
                    economy,
                    "minecraft:overworld",
                    villagePosition,
                    villageId,
                    config.villageRegionSize());
            if (!processedBanks.add(bankKey)) {
                continue;
            }
            if (economy.hasGeneratedBankRegion(bankKey)) {
                Long packedAnchor = economy.generatedBankAnchor(bankKey);
                BlockPos anchor = packedAnchor == null
                        ? villagePosition
                        : BlockPos.of(packedAnchor);
                if (packedAnchor == null) {
                    economy.markGeneratedBankRegion(bankKey, anchor.asLong());
                }
                if (villageId != null) {
                    economy.associateBankRegionWithVillage(bankKey, villageId, anchor.asLong());
                }
                if (packedAnchor != null
                        && (!isLoaded(level, anchor) || !isLoaded(level, anchor.north()))) {
                    continue;
                }
                boolean intactCounter = packedAnchor != null
                        && BankerProfessionSupport.isBankWorkstation(
                                level.getBlockState(anchor.north()));
                boolean survivingCounterFrame = packedAnchor != null
                        && hasBankCounterFrame(level, anchor);
                if (intactCounter && survivingCounterFrame) {
                    maintainManagedBank(
                            level,
                            economy,
                            anchor,
                            villageId,
                            bankKey,
                            bankUpgradeGate);
                }
                boolean bankSignaturePresent = intactCounter || survivingCounterFrame;
                boolean persistedFallback = economy.isFallbackBankRegion(bankKey);
                Long previousRetry = LAST_FALLBACK_BANK_RETRY_TICK.get(bankKey);
                if (packedAnchor != null
                        && VillageBankPlacementPolicy.shouldRetryPersistedFallback(
                                persistedFallback)
                        && VillageBankPlacementPolicy.retryDue(
                                gameTime,
                                previousRetry,
                                FALLBACK_BANK_RETRY_INTERVAL_TICKS)) {
                    LAST_FALLBACK_BANK_RETRY_TICK.put(bankKey, gameTime);
                    BankBuildAttempt retry = attemptBankBuild(
                            level, anchor, villageId, bankKey);
                    if (retry.build().built()
                            && persistBuiltBank(level, economy, villageId, bankKey, retry)) {
                        LAST_FALLBACK_BANK_RETRY_TICK.remove(bankKey);
                        continue;
                    }
                }
                // Old saves do not retain per-block bank ownership. Without explicit fallback
                // provenance, a missing signature is never rebuilt: it could be a damaged or
                // crash-interrupted Bank rather than a Banker-only fallback.
                ensureBanker(
                        level,
                        bankSignaturePresent || persistedFallback ? anchor : villagePosition,
                        bankSignaturePresent,
                        bankKey,
                        economy);
                continue;
            }

            BankBuildAttempt attempt = attemptBankBuild(level, villagePosition, villageId, bankKey);
            if (attempt.build().built()) {
                persistBuiltBank(level, economy, villageId, bankKey, attempt);
            } else {
                if (VillageBankPlacementPolicy.shouldPersistFallback(
                        attempt.searchComplete(), attempt.hadCandidates())) {
                    establishFallbackBanker(
                            level, villagePosition, bankKey, villageId, economy);
                } else {
                    // An incomplete scan or a failed protected write is transient. Preserve access
                    // now, but do not convert either case into a permanent fallback marker.
                    ensureBanker(level, villagePosition, false, bankKey, economy);
                }
            }
        }
    }

    private static BankBuildAttempt attemptBankBuild(
            ServerLevel level, BlockPos villagePosition, UUID villageId, long bankKey) {
        BankPlotSearch plotSearch = findBankPlots(level, villagePosition, bankKey);
        for (BlockPos candidate : plotSearch.candidates()) {
            BankBuildResult build = buildBank(level, candidate, villageId, bankKey);
            if (build.built()) {
                return new BankBuildAttempt(
                        candidate, build, plotSearch.complete(), true);
            }
        }
        return new BankBuildAttempt(
                null,
                BankBuildResult.failed(),
                plotSearch.complete(),
                !plotSearch.candidates().isEmpty());
    }

    private static boolean persistBuiltBank(
            ServerLevel level,
            EconomyService economy,
            UUID villageId,
            long bankKey,
            BankBuildAttempt attempt) {
        if (attempt.origin() == null || !attempt.build().built()) {
            return false;
        }
        BlockPos bankerPosition = attempt.origin().offset(
                BANK_WIDTH / 2, 1, BANK_DEPTH - 2);
        if (!economy.markGeneratedBankRegion(
                bankKey,
                bankerPosition.asLong(),
                villageId,
                BANK_STRUCTURE_VERSION)) {
            rollbackBank(level, attempt.build().placements());
            return false;
        }
        ensureBanker(level, bankerPosition, true, bankKey, economy);
        return true;
    }

    private static boolean establishFallbackBanker(
            ServerLevel level,
            BlockPos villagePosition,
            long bankKey,
            UUID villageId,
            EconomyService economy) {
        boolean completed = ensureBanker(level, villagePosition, false, bankKey, economy)
                && economy.markFallbackBankRegion(
                        bankKey, villagePosition.asLong(), villageId);
        return completed;
    }

    private static boolean ensureBanker(
            ServerLevel level,
            BlockPos bankerAnchor,
            boolean generatedStructure,
            long regionKey,
            EconomyService economy) {
        BlockPos counter = bankerAnchor.north();
        BlockPos exchangeDeskPosition = generatedStructure
                        && isLoaded(level, counter)
                        && BankerProfessionSupport.isExchangeDesk(level.getBlockState(counter))
                ? counter
                : null;
        AABB search = new AABB(bankerAnchor).inflate(48.0, 20.0, 48.0);
        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                search,
                villager -> villager.isAlive() && !villager.isBaby());
        Villager existing = villagers.stream()
                .filter(villager -> BankerAccess.isBankerForRegion(villager, regionKey))
                .min(Comparator.comparingDouble(villager ->
                        villager.distanceToSqr(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY() + 0.5,
                                bankerAnchor.getZ() + 0.5)))
                .orElse(null);
        if (existing != null) {
            BankerAccess.markBanker(existing, regionKey, exchangeDeskPosition);
            existing.setHomeTo(bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
            return true;
        }

        // An extinct or deliberately abandoned village keeps account access through its bank
        // lectern, but it does not receive a free replacement Banker until recovery begins.
        if (!economy.allowBankerReplacementForRegion(regionKey, bankerAnchor.asLong())) {
            return true;
        }

        // Adopt one legacy unscoped Banker before creating a replacement. This migrates
        // alpha saves while still preventing two nearby banks from sharing the same villager.
        Villager legacyBanker = villagers.stream()
                .filter(BankerAccess::isLegacyUnscopedBanker)
                .min(Comparator.comparingDouble(villager ->
                        villager.distanceToSqr(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY() + 0.5,
                                bankerAnchor.getZ() + 0.5)))
                .orElse(null);
        if (legacyBanker != null
                && BankerAccess.markBanker(legacyBanker, regionKey, exchangeDeskPosition)) {
            legacyBanker.setHomeTo(
                    bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
            return true;
        }

        // Every replacement first prefers an untouched unemployed adult. Established villagers,
        // including traded librarians, are never converted or reset.
        Villager candidate = villagers.stream()
                .filter(BankerAccess::isEligibleBankerCandidate)
                .min(Comparator.comparingDouble(villager ->
                        villager.distanceToSqr(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY() + 0.5,
                                bankerAnchor.getZ() + 0.5)))
                .orElse(null);
        if (candidate != null
                && BankerAccess.markBanker(candidate, regionKey, exchangeDeskPosition)) {
            candidate.setHomeTo(
                    bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
            return true;
        }

        BlockPos spawnPosition = bankerAnchor;
        if (!generatedStructure) {
            int y = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    bankerAnchor.getX(),
                    bankerAnchor.getZ());
            spawnPosition = new BlockPos(bankerAnchor.getX(), y, bankerAnchor.getZ());
        }
        return spawnBanker(
                level, spawnPosition, generatedStructure, regionKey, exchangeDeskPosition);
    }

    /** Returns a dashboard access point for generated counters and player-placed Exchange Desks. */
    public static BlockPos bankAccessPoint(
            ServerLevel level, BlockPos clicked, EconomyService economy) {
        BlockState clickedState = level.getBlockState(clicked);
        if (!BankerProfessionSupport.isBankWorkstation(clickedState)) {
            return null;
        }
        if (level == level.getServer().overworld()) {
            for (long packedAnchor : economy.generatedBankAnchorsSnapshot().values()) {
                BlockPos anchor = BlockPos.of(packedAnchor);
                if (anchor.north().equals(clicked)) {
                    return anchor;
                }
            }
        }
        // A crafted Exchange Desk is valid personal-bank access. Its position also scopes the
        // optional Fund and Village pages to the nearest managed village; an arbitrary lectern
        // remains inert unless it belongs to a persisted legacy bank.
        return BankerProfessionSupport.isExchangeDesk(clickedState) ? clicked : null;
    }

    /**
     * Resolves a persistent bank key without assigning two known villages to one legacy grid bank.
     * Existing alpha-era associations always win. A second stable village in the same grid region
     * receives a deterministic UUID-derived key instead of generating a duplicate for the first.
     */
    public static long bankKeyForVillage(
            EconomyService economy,
            String dimensionKey,
            BlockPos center,
            UUID preferredVillageId,
            int regionSize) {
        long legacyKey = regionKey(center, regionSize);
        UUID villageId = preferredVillageId;
        if (villageId == null) {
            EconomyService.VillageSnapshot nearby = economy.nearestVillageSnapshot(
                    dimensionKey, center.asLong(), 48.0);
            villageId = nearby == null ? null : nearby.village().villageId;
        }
        if (villageId == null) {
            return legacyKey;
        }

        UUID stableVillageId = villageId;
        Long existing = economy.generatedBankAnchorsSnapshot().keySet().stream()
                .filter(key -> stableVillageId.equals(economy.villageIdForBankRegion(key)))
                .min(Long::compareUnsigned)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        if (!economy.hasGeneratedBankRegion(legacyKey)) {
            return legacyKey;
        }
        UUID legacyVillage = economy.villageIdForBankRegion(legacyKey);
        if (legacyVillage == null || legacyVillage.equals(villageId)) {
            return legacyKey;
        }

        long identity = villageId.getMostSignificantBits()
                ^ Long.rotateLeft(villageId.getLeastSignificantBits(), 23)
                ^ 0x42414E4B5F4B4559L;
        // mix64 is a permutation and the odd increment walks the full long domain. The persisted
        // region set is finite, so this always reaches an unused key without falling back to a
        // legacy key that belongs to a different village.
        for (long attempt = 0L; ; attempt++) {
            long candidate = mix64(identity + attempt * 0x9E3779B97F4A7C15L);
            if (!economy.hasGeneratedBankRegion(candidate)
                    || villageId.equals(economy.villageIdForBankRegion(candidate))) {
                return candidate;
            }
        }
    }

    private static BankPlotSearch findBankPlots(
            ServerLevel level,
            BlockPos villagePosition,
            long regionKey) {
        int[][] offsets = {
                {28, 0}, {-28, 0}, {0, 28}, {0, -28},
                {34, 20}, {-34, 20}, {34, -20}, {-34, -20},
                {42, 0}, {-42, 0}, {0, 42}, {0, -42}
        };
        List<BlockPos> candidates = new ArrayList<>();
        boolean complete = true;
        int rotation = Math.floorMod((int) (regionKey ^ (regionKey >>> 32)), offsets.length);
        for (int step = 0; step < offsets.length; step++) {
            int[] offset = offsets[(step + rotation) % offsets.length];
            int centerX = villagePosition.getX() + offset[0];
            int centerZ = villagePosition.getZ() + offset[1];
            if (!isBankPlotAreaLoaded(level, centerX, centerZ)) {
                complete = false;
                continue;
            }
            BlockPos origin = safeOrigin(level, centerX, centerZ);
            if (origin != null) {
                candidates.add(origin);
            }
        }
        return new BankPlotSearch(List.copyOf(candidates), complete);
    }

    private static boolean isBankPlotAreaLoaded(
            ServerLevel level, int centerX, int centerZ) {
        int originX = centerX - BANK_WIDTH / 2;
        int originZ = centerZ - BANK_DEPTH / 2;
        int minimumX = originX + BANK_PLOT_MIN_X;
        int maximumX = originX + BANK_PLOT_MAX_X;
        int minimumZ = originZ + BANK_APPROACH_ARRIVAL_Z;
        int maximumZ = originZ + BANK_PLOT_MAX_Z;
        for (int chunkX = Math.floorDiv(minimumX, 16);
                chunkX <= Math.floorDiv(maximumX, 16);
                chunkX++) {
            for (int chunkZ = Math.floorDiv(minimumZ, 16);
                    chunkZ <= Math.floorDiv(maximumZ, 16);
                    chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockPos safeOrigin(ServerLevel level, int centerX, int centerZ) {
        int originX = centerX - BANK_WIDTH / 2;
        int originZ = centerZ - BANK_DEPTH / 2;
        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        for (int x = originX + BANK_PLOT_MIN_X; x <= originX + BANK_PLOT_MAX_X; x++) {
            for (int z = originZ + BANK_PLOT_MIN_Z; z <= originZ + BANK_PLOT_MAX_Z; z++) {
                if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                    return null;
                }
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                BlockState groundState = level.getBlockState(ground);
                if (groundState.isAir()
                        || level.getBlockEntity(ground) != null
                        || !level.getFluidState(ground).isEmpty()
                        || !isNaturalBankGround(groundState)) {
                    return null;
                }
                minimumSurface = Math.min(minimumSurface, surface);
                maximumSurface = Math.max(maximumSurface, surface);
            }
        }
        if (!TerrainFoundationPlan.supportsTerrainRange(
                minimumSurface,
                maximumSurface,
                TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            return null;
        }

        // Level the floor at the highest sampled surface and bridge only small natural drops with
        // authored foundations. Paths, farmland, player floors, containers, and structures remain
        // invalid ground and are never adopted into the bank.
        BlockPos origin = new BlockPos(
                originX,
                maximumSurface,
                originZ);
        List<List<Integer>> entranceProfiles = sampleNaturalEntranceProfiles(level, origin);
        if (entranceProfiles == null
                || VillageBankPlacementPolicy.planWideEntranceSteps(
                                entranceProfiles, TerrainFoundationPlan.MAX_TERRAIN_DROP)
                        .isEmpty()) {
            return null;
        }
        for (int x = BANK_PLOT_MIN_X; x <= BANK_PLOT_MAX_X; x++) {
            for (int z = BANK_PLOT_MIN_Z; z <= BANK_PLOT_MAX_Z; z++) {
                for (int y = 0; y <= BANK_HEIGHT; y++) {
                    BlockPos position = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(position);
                    if (!VillageBankPlacementPolicy.acceptsVolumeCell(
                            state.isAir(),
                            state.canBeReplaced(),
                            level.getBlockEntity(position) != null)) {
                        return null;
                    }
                }
            }
        }
        return origin;
    }

    /** Samples each lane independently so a cross-slope cannot leave part of a wide stair afloat. */
    private static List<List<Integer>> sampleNaturalEntranceProfiles(
            ServerLevel level, BlockPos origin) {
        List<List<Integer>> profiles = new ArrayList<>(BANK_ENTRANCE_HALF_WIDTH * 2 + 1);
        for (int lane = -BANK_ENTRANCE_HALF_WIDTH;
                lane <= BANK_ENTRANCE_HALF_WIDTH;
                lane++) {
            profiles.add(new ArrayList<>(TerrainFoundationPlan.MAX_TERRAIN_DROP + 1));
        }
        int centerX = BANK_WIDTH / 2;
        for (int z = -3; z >= BANK_APPROACH_ARRIVAL_Z; z--) {
            int laneIndex = 0;
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;
                if (!level.hasChunk(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16))) {
                    return null;
                }
                int surface = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
                BlockPos ground = new BlockPos(worldX, surface - 1, worldZ);
                BlockState groundState = level.getBlockState(ground);
                if (groundState.isAir()
                        || level.getBlockEntity(ground) != null
                        || !level.getFluidState(ground).isEmpty()
                        || !isNaturalBankGround(groundState)) {
                    return null;
                }
                int offset = surface - origin.getY();
                if (offset < -TerrainFoundationPlan.MAX_TERRAIN_DROP || offset > 0) {
                    return null;
                }
                profiles.get(laneIndex++).add(offset);
            }
        }
        return profiles.stream().map(List::copyOf).toList();
    }

    static boolean isNaturalBankGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isLoaded(ServerLevel level, BlockPos position) {
        return level.hasChunk(
                Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }

    /** Recognizes legacy barrels or the new non-job-site cabinetry without placing either. */
    private static boolean hasBankCounterFrame(ServerLevel level, BlockPos bankerAnchor) {
        BlockPos counter = bankerAnchor.north();
        if (!isLoaded(level, counter.west()) || !isLoaded(level, counter.east())) {
            return false;
        }
        BlockState west = level.getBlockState(counter.west());
        BlockState east = level.getBlockState(counter.east());
        return (west.is(Blocks.BARREL) && east.is(Blocks.BARREL))
                || (west.is(Blocks.CHISELED_BOOKSHELF)
                        && east.is(Blocks.CHISELED_BOOKSHELF));
    }

    /** Maintains pane shapes and performs the guarded, one-time entrance upgrade for old Banks. */
    private static void maintainManagedBank(
            ServerLevel level,
            EconomyService economy,
            BlockPos bankerAnchor,
            UUID villageId,
            long bankKey,
            VillageBankPlacementPolicy.UpgradeAttemptGate upgradeGate) {
        BlockPos origin = bankerAnchor.offset(
                -BANK_WIDTH / 2, -1, -(BANK_DEPTH - 2));
        BankPalette palette = paletteFor(level, origin);
        if (!hasManagedBankSignature(level, origin, bankerAnchor, palette)) {
            return;
        }

        normalizeManagedBankPanes(level, origin, villageId, bankKey);
        if (economy.generatedBankStructureVersion(bankKey) >= BANK_STRUCTURE_VERSION) {
            LAST_BANK_UPGRADE_RETRY_TICK.remove(bankKey);
            return;
        }
        if (upgradeGate.claimed()
                || economy.isFallbackBankRegion(bankKey)
                || !hasLegacyEntranceSignature(level, origin, palette)) {
            return;
        }

        List<List<Integer>> profiles = sampleExistingEntranceProfiles(level, origin, palette);
        Optional<List<VillageBankPlacementPolicy.EntranceStep>> steps = profiles == null
                ? Optional.empty()
                : VillageBankPlacementPolicy.planWideEntranceSteps(
                        profiles, TerrainFoundationPlan.MAX_TERRAIN_DROP);
        if (steps.isEmpty()) {
            return;
        }
        List<BankPlacement> upgrade = bankEntranceUpgradePlan(
                level, origin, palette, steps.orElseThrow());
        if (upgrade == null) {
            return;
        }

        if (level.noSave()) {
            return;
        }
        long gameTime = level.getGameTime();
        Long previousAttempt = LAST_BANK_UPGRADE_RETRY_TICK.get(bankKey);
        if (!VillageBankPlacementPolicy.retryDue(
                gameTime, previousAttempt, BANK_UPGRADE_RETRY_INTERVAL_TICKS)) {
            return;
        }
        List<BankMutation> placed = new ArrayList<>();
        for (BankPlacement placement : upgrade) {
            BlockState existing = level.getBlockState(placement.position());
            if (existing.equals(placement.state())) {
                continue;
            }
            if (!isLoaded(level, placement.position())
                    || (!existing.isAir() && !existing.canBeReplaced())
                    || level.getBlockEntity(placement.position()) != null
                    || !level.getFluidState(placement.position()).isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            bankKey,
                            placement.position(),
                            existing,
                            placement.state())) {
                return;
            }
        }
        if (!upgradeGate.tryClaim(true)) {
            return;
        }
        LAST_BANK_UPGRADE_RETRY_TICK.put(bankKey, gameTime);
        for (BankPlacement placement : upgrade) {
            BlockState original = level.getBlockState(placement.position());
            if (original.equals(placement.state())) {
                continue;
            }
            boolean changed = level.setBlock(placement.position(), placement.state(), 3);
            BlockState applied = level.getBlockState(placement.position());
            if (!changed || !applied.equals(placement.state())) {
                if (isOwnedBankPlacement(applied, placement.state())) {
                    placed.add(new BankMutation(placement, original));
                }
                rollbackBank(level, placed);
                return;
            }
            placed.add(new BankMutation(placement, original));
        }
        if (!flushBankUpgradeChunks(level)) {
            rollbackBank(level, placed);
            return;
        }
        if (!economy.completeBankStructureUpgrade(
                bankKey,
                bankerAnchor.asLong(),
                BANK_STRUCTURE_VERSION)) {
            rollbackBank(level, placed);
        } else {
            LAST_BANK_UPGRADE_RETRY_TICK.remove(bankKey);
        }
    }

    /**
     * Waits for Minecraft's synchronous chunk-save barrier before the economy file records its
     * version marker. The remaining cross-file failure limit is documented with the save format.
     */
    private static boolean flushBankUpgradeChunks(ServerLevel level) {
        if (level.noSave()) {
            return false;
        }
        try {
            level.getChunkSource().save(true);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean hasManagedBankSignature(
            ServerLevel level,
            BlockPos origin,
            BlockPos bankerAnchor,
            BankPalette palette) {
        BlockPos roofLantern = origin.offset(BANK_WIDTH / 2, 6, BANK_DEPTH / 2);
        BlockPos roofMount = roofLantern.above();
        BlockPos lowerDoorPosition = origin.offset(BANK_WIDTH / 2, 1, 0);
        BlockPos upperDoorPosition = lowerDoorPosition.above();
        BlockPos threshold = lowerDoorPosition.below();
        if (!isLoaded(level, roofLantern)
                || !isLoaded(level, roofMount)
                || !isLoaded(level, lowerDoorPosition)
                || !isLoaded(level, upperDoorPosition)
                || !isLoaded(level, threshold)
                || !BankerProfessionSupport.isBankWorkstation(
                        level.getBlockState(bankerAnchor.north()))
                || !hasBankCounterFrame(level, bankerAnchor)
                || !level.getBlockState(roofMount).is(palette.corner())
                || !level.getBlockState(roofLantern).is(Blocks.LANTERN)
                || !level.getBlockState(roofLantern).getValue(LanternBlock.HANGING)
                || !level.getBlockState(threshold).is(palette.foundation())) {
            return false;
        }
        BlockState lowerDoor = level.getBlockState(lowerDoorPosition);
        BlockState upperDoor = level.getBlockState(upperDoorPosition);
        return lowerDoor.is(palette.door())
                && lowerDoor.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && lowerDoor.getValue(DoorBlock.FACING) == Direction.NORTH
                && upperDoor.is(palette.door())
                && upperDoor.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && upperDoor.getValue(DoorBlock.FACING) == Direction.NORTH;
    }

    private static boolean hasLegacyEntranceSignature(
            ServerLevel level, BlockPos origin, BankPalette palette) {
        BlockPos topStair = origin.offset(BANK_WIDTH / 2, 0, -2);
        if (!isLoaded(level, topStair)) {
            return false;
        }
        BlockState state = level.getBlockState(topStair);
        return state.is(palette.stairs())
                && state.getValue(StairBlock.FACING) == Direction.SOUTH;
    }

    /** Finds the original natural surface below any crash-interrupted authored approach cells. */
    private static List<List<Integer>> sampleExistingEntranceProfiles(
            ServerLevel level, BlockPos origin, BankPalette palette) {
        List<List<Integer>> profiles = new ArrayList<>(BANK_ENTRANCE_HALF_WIDTH * 2 + 1);
        for (int lane = -BANK_ENTRANCE_HALF_WIDTH;
                lane <= BANK_ENTRANCE_HALF_WIDTH;
                lane++) {
            profiles.add(new ArrayList<>(TerrainFoundationPlan.MAX_TERRAIN_DROP + 1));
        }
        int centerX = BANK_WIDTH / 2;
        for (int z = -3; z >= BANK_APPROACH_ARRIVAL_Z; z--) {
            int laneIndex = 0;
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                Integer offset = findExistingNaturalSurfaceOffset(
                        level, origin.offset(x, 0, z), palette);
                if (offset == null) {
                    return null;
                }
                profiles.get(laneIndex++).add(offset);
            }
        }
        return profiles.stream().map(List::copyOf).toList();
    }

    private static Integer findExistingNaturalSurfaceOffset(
            ServerLevel level, BlockPos column, BankPalette palette) {
        for (int y = 0; y >= -TerrainFoundationPlan.MAX_TERRAIN_DROP - 1; y--) {
            BlockPos position = column.offset(0, y, 0);
            if (!isLoaded(level, position)
                    || level.getBlockEntity(position) != null
                    || !level.getFluidState(position).isEmpty()) {
                return null;
            }
            BlockState state = level.getBlockState(position);
            if (isNaturalBankGround(state)) {
                return y + 1;
            }
            if (state.isAir()
                    || state.canBeReplaced()
                    || state.is(palette.stairs())
                    || state.is(palette.foundation())) {
                continue;
            }
            return null;
        }
        return null;
    }

    private static List<BankPlacement> bankEntranceUpgradePlan(
            ServerLevel level,
            BlockPos origin,
            BankPalette palette,
            List<VillageBankPlacementPolicy.EntranceStep> steps) {
        List<BankPlacement> result = new ArrayList<>();
        int centerX = BANK_WIDTH / 2;
        BlockState foundation = palette.foundation().defaultBlockState();
        BlockState stair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            result.add(new BankPlacement(origin.offset(x, 0, -1), foundation));
            for (VillageBankPlacementPolicy.EntranceStep step : steps) {
                result.add(new BankPlacement(
                        origin.offset(x, step.yOffset(), step.zOffset()), stair));
            }
            for (int y = 1; y <= 2; y++) {
                BlockPos headroom = origin.offset(x, y, -1);
                if (!isLoaded(level, headroom)
                        || level.getBlockEntity(headroom) != null
                        || !level.getFluidState(headroom).isEmpty()) {
                    return null;
                }
                BlockState current = level.getBlockState(headroom);
                if (!current.isAir() && !current.canBeReplaced()) {
                    return null;
                }
            }
        }
        if (!hasEntranceHeadroom(level, result)) {
            return null;
        }

        List<TerrainFoundationPlan.Cell> authored = result.stream()
                .map(placement -> placement.position().subtract(origin))
                .map(relative -> new TerrainFoundationPlan.Cell(
                        relative.getX(), relative.getY(), relative.getZ()))
                .toList();
        Set<BlockPos> groundedColumns = new HashSet<>();
        for (TerrainFoundationPlan.Cell support : TerrainFoundationPlan.appendSupportCells(
                authored, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            BlockPos column = new BlockPos(support.x(), 0, support.z());
            if (groundedColumns.contains(column)) {
                continue;
            }
            BlockPos target = origin.offset(support.x(), support.y(), support.z());
            if (!isLoaded(level, target)
                    || level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()) {
                return null;
            }
            BlockState current = level.getBlockState(target);
            if (isNaturalBankGround(current)) {
                groundedColumns.add(column);
                continue;
            }
            if (!current.equals(foundation)
                    && !current.isAir()
                    && !current.canBeReplaced()) {
                return null;
            }
            result.add(new BankPlacement(target, foundation));
        }

        // Preserve the previous shallow-foundation repair as part of this one-shot upgrade. A
        // claimed or player-replaced cell is skipped instead of being treated as Bank ownership.
        for (int x : new int[] {2, centerX - 2, centerX + 2}) {
            BlockPos target = origin.offset(x, 0, -1);
            BlockPos detail = target.above();
            if (!isLoaded(level, target)
                    || !isLoaded(level, detail)
                    || (x == 2 && !isLoaded(level, detail.above()))) {
                continue;
            }
            boolean detailMatches = x == 2
                    ? level.getBlockState(detail).is(palette.accent())
                            && level.getBlockState(detail.above()).is(Blocks.BELL)
                    : level.getBlockState(detail).is(palette.corner());
            if (!detailMatches) {
                continue;
            }
            BlockState current = level.getBlockState(target);
            if (current.equals(foundation)) {
                continue;
            }
            if ((current.isAir() || current.canBeReplaced())
                    && level.getBlockEntity(target) == null
                    && level.getFluidState(target).isEmpty()) {
                result.add(new BankPlacement(target, foundation));
            }
        }
        return List.copyOf(result);
    }

    private static boolean hasEntranceHeadroom(
            ServerLevel level, List<BankPlacement> entrance) {
        for (BankPlacement placement : entrance) {
            if (!(placement.state().getBlock() instanceof StairBlock)) {
                continue;
            }
            for (int offset = 1; offset <= 2; offset++) {
                BlockPos position = placement.position().above(offset);
                if (!isLoaded(level, position)
                        || level.getBlockEntity(position) != null
                        || !level.getFluidState(position).isEmpty()) {
                    return false;
                }
                BlockState state = level.getBlockState(position);
                if (!state.isAir() && !state.canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void normalizeManagedBankPanes(
            ServerLevel level, BlockPos origin, UUID villageId, long bankKey) {
        for (int y = 2; y <= 3; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    if (isBankWindowCell(x, y, z)) {
                        normalizeBankPaneState(
                                level, origin.offset(x, y, z), villageId, bankKey);
                    }
                }
            }
        }
        normalizeBankPaneState(
                level,
                origin.offset(BANK_WIDTH / 2, 3, 0),
                villageId,
                bankKey);
    }

    /** Recomputes one surviving authored pane without recreating or replacing player blocks. */
    static boolean normalizeBankPaneState(
            ServerLevel level, BlockPos position, UUID villageId, long bankKey) {
        if (!isLoaded(level, position)
                || !isLoaded(level, position.north())
                || !isLoaded(level, position.east())
                || !isLoaded(level, position.south())
                || !isLoaded(level, position.west())) {
            return false;
        }
        BlockState current = level.getBlockState(position);
        if (!current.is(Blocks.STAINED_GLASS_PANE.green())) {
            return false;
        }
        BlockState updated = Block.updateFromNeighbourShapes(current, level, position);
        if (updated.equals(current)) {
            return true;
        }
        if (!VillageDevelopmentProtection.mayPlace(
                level, villageId, bankKey, position, current, updated)) {
            return false;
        }
        return level.setBlock(
                        position,
                        updated,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                && level.getBlockState(position).equals(updated);
    }

    private static BankBuildResult buildBank(
            ServerLevel level, BlockPos origin, UUID villageId, long bankKey) {
        BankPalette palette = paletteFor(level, origin);
        List<BankPlacement> plan = terrainSupportedBankPlan(level, origin, palette);
        if (plan == null) {
            return BankBuildResult.failed();
        }
        for (BankPlacement placement : plan) {
            if (!isLoaded(level, placement.position())) {
                return BankBuildResult.failed();
            }
            BlockState existing = level.getBlockState(placement.position());
            if ((!existing.isAir() && !existing.canBeReplaced())
                    || level.getBlockEntity(placement.position()) != null
                    || !VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            bankKey,
                            placement.position(),
                            existing,
                            placement.state())) {
                return BankBuildResult.failed();
            }
        }

        List<BankMutation> placed = new ArrayList<>(plan.size());
        for (BankPlacement placement : plan) {
            BlockState original = level.getBlockState(placement.position());
            boolean changed = level.setBlock(placement.position(), placement.state(), 3);
            BlockState applied = level.getBlockState(placement.position());
            if (!changed || !applied.equals(placement.state())) {
                // Some integrations can report a failed/cancelled placement after mutating the
                // world. Include that authored block in rollback when it did appear.
                if (isOwnedBankPlacement(applied, placement.state())) {
                    placed.add(new BankMutation(placement, original));
                }
                rollbackBank(level, placed);
                return BankBuildResult.failed();
            }
            placed.add(new BankMutation(placement, original));
        }
        return new BankBuildResult(true, List.copyOf(placed));
    }

    /** Adds only the air/replaceable foundation cells needed to bridge a shallow natural lot. */
    private static List<BankPlacement> terrainSupportedBankPlan(
            ServerLevel level, BlockPos origin, BankPalette palette) {
        List<BankPlacement> base = bankPlan(origin, palette);
        List<List<Integer>> profiles = sampleNaturalEntranceProfiles(level, origin);
        Optional<List<VillageBankPlacementPolicy.EntranceStep>> steps = profiles == null
                ? Optional.empty()
                : VillageBankPlacementPolicy.planWideEntranceSteps(
                        profiles, TerrainFoundationPlan.MAX_TERRAIN_DROP);
        if (steps.isEmpty()) {
            return null;
        }
        List<BankPlacement> result = new ArrayList<>(base);
        BlockState stair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        int centerX = BANK_WIDTH / 2;
        for (VillageBankPlacementPolicy.EntranceStep step : steps.orElseThrow()) {
            if (step.zOffset() == -2) {
                continue;
            }
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                result.add(new BankPlacement(
                        origin.offset(x, step.yOffset(), step.zOffset()), stair));
            }
        }
        if (!hasEntranceHeadroom(level, result)) {
            return null;
        }
        List<TerrainFoundationPlan.Cell> authored = result.stream()
                .map(placement -> placement.position().subtract(origin))
                .map(relative -> new TerrainFoundationPlan.Cell(
                        relative.getX(), relative.getY(), relative.getZ()))
                .toList();
        Set<BlockPos> groundedColumns = new HashSet<>();
        for (TerrainFoundationPlan.Cell support : TerrainFoundationPlan.appendSupportCells(
                authored, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            BlockPos column = new BlockPos(support.x(), 0, support.z());
            if (groundedColumns.contains(column)) {
                continue;
            }
            BlockPos target = origin.offset(support.x(), support.y(), support.z());
            if (!isLoaded(level, target)
                    || level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()) {
                return null;
            }
            BlockState current = level.getBlockState(target);
            if (isNaturalBankGround(current)) {
                // The first natural block in a column is already a sound footing. Do not tunnel
                // through it merely because the deterministic suffix has additional depth cells.
                groundedColumns.add(column);
                continue;
            }
            if (!current.isAir() && !current.canBeReplaced()) {
                return null;
            }
            result.add(new BankPlacement(target, palette.foundation().defaultBlockState()));
        }
        return List.copyOf(result);
    }

    private static List<BankPlacement> bankPlan(BlockPos origin, BankPalette palette) {
        List<BankPlacement> placements = new ArrayList<>();
        for (int x = 0; x < BANK_WIDTH; x++) {
            for (int z = 0; z < BANK_DEPTH; z++) {
                placements.add(new BankPlacement(
                        origin.offset(x, 0, z),
                        (x == 0 || x == BANK_WIDTH - 1 || z == 0 || z == BANK_DEPTH - 1
                                        ? palette.foundation()
                                        : palette.floor())
                                .defaultBlockState()));
            }
        }

        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    boolean edge = x == 0 || x == BANK_WIDTH - 1
                            || z == 0 || z == BANK_DEPTH - 1;
                    if (!edge) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == BANK_WIDTH - 1)
                            && (z == 0 || z == BANK_DEPTH - 1);
                    boolean entrance = z == 0
                            && x == BANK_WIDTH / 2
                            && y <= 3;
                    boolean window = isBankWindowCell(x, y, z);
                    if (entrance) {
                        continue;
                    }
                    Block wallBlock = corner
                            ? palette.corner()
                            : y == 1 || y == 4 ? palette.accent() : palette.wall();
                    placements.add(new BankPlacement(
                            origin.offset(x, y, z),
                            window
                                    ? connectedBankPaneState(x == 0 || x == BANK_WIDTH - 1)
                                    : wallBlock.defaultBlockState()));
                }
            }
        }

        // Full-block stepped rows make a continuous weather shell without fragile stair rotations
        // or the half-block seams created by vertically offset bottom slabs.
        for (int z = -1; z <= BANK_DEPTH; z++) {
            int distanceFromEave = Math.min(z + 1, BANK_DEPTH - z);
            int roofY = 5 + (distanceFromEave + 1) / 2;
            for (int x = -1; x <= BANK_WIDTH; x++) {
                placements.add(new BankPlacement(
                        origin.offset(x, roofY, z), palette.roofDeck().defaultBlockState()));
            }
            if (z >= 0 && z < BANK_DEPTH) {
                for (int y = 5; y < roofY; y++) {
                    placements.add(new BankPlacement(
                            origin.offset(0, y, z), palette.wall().defaultBlockState()));
                    placements.add(new BankPlacement(
                            origin.offset(BANK_WIDTH - 1, y, z),
                            palette.wall().defaultBlockState()));
                }
            }
        }
        // Close the one-block facade/back gap beneath the first raised roof rows. The end walls
        // were already filled by the gable loop above, so only the interior x range belongs here.
        for (int x = 1; x < BANK_WIDTH - 1; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 5, 0), palette.wall().defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(x, 5, BANK_DEPTH - 1),
                    palette.wall().defaultBlockState()));
        }
        // A sheltered entrance, public bell, and illuminated lobby make the building legible from
        // the village path. All details remain inside the plot volume preflighted by safeOrigin.
        for (int x : new int[] {BANK_WIDTH / 2 - 2, BANK_WIDTH / 2 + 2}) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, -1), palette.foundation().defaultBlockState()));
            for (int y = 1; y <= 3; y++) {
                placements.add(new BankPlacement(
                        origin.offset(x, y, -1), palette.corner().defaultBlockState()));
            }
        }
        for (int x = BANK_WIDTH / 2 - 3; x <= BANK_WIDTH / 2 + 3; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 4, -1), palette.roof().defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(2, 0, -1), palette.foundation().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, -1), palette.accent().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 2, -1), Blocks.BELL.defaultBlockState()));

        BlockState lowerDoor = palette.door().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 1, 0), lowerDoor));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 2, 0),
                lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 3, 0),
                connectedBankPaneState(false)));
        BlockState entranceStair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = BANK_WIDTH / 2 - BANK_ENTRANCE_HALF_WIDTH;
                x <= BANK_WIDTH / 2 + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, -1), palette.foundation().defaultBlockState()));
            placements.add(new BankPlacement(origin.offset(x, 0, -2), entranceStair));
        }

        int counterZ = BANK_DEPTH - 3;
        for (int x = 2; x <= BANK_WIDTH - 3; x++) {
            BlockState counterState;
            if (x == BANK_WIDTH / 2) {
                counterState = BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState();
            } else if ((x & 1) == 0) {
                counterState = Blocks.CHEST.defaultBlockState();
            } else {
                counterState = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            }
            placements.add(new BankPlacement(
                    origin.offset(x, 1, counterZ), counterState));
        }
        placements.add(new BankPlacement(
                origin.offset(2, 1, counterZ - 1),
                Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 1, counterZ - 1),
                Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 1, counterZ), Blocks.ENDER_CHEST.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, counterZ),
                Blocks.CRAFTING_TABLE.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, 3), palette.fence().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 1, 3), palette.fence().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 2, 3), Blocks.LANTERN.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 2, 3), Blocks.LANTERN.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 7, BANK_DEPTH / 2),
                palette.corner().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 6, BANK_DEPTH / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
        return List.copyOf(placements);
    }

    private static boolean isBankWindowCell(int x, int y, int z) {
        return (y == 2 || y == 3)
                && ((z == 0 && (x == 2 || x == BANK_WIDTH - 3))
                        || (z == BANK_DEPTH - 1
                                && (x == 3
                                        || x == BANK_WIDTH / 2
                                        || x == BANK_WIDTH - 4))
                        || ((x == 0 || x == BANK_WIDTH - 1)
                                && (z == 3 || z == BANK_DEPTH - 4)));
    }

    private static BlockState connectedBankPaneState(boolean sideWall) {
        BlockState state = Blocks.STAINED_GLASS_PANE.green().defaultBlockState();
        return sideWall
                ? state.setValue(CrossCollisionBlock.NORTH, true)
                        .setValue(CrossCollisionBlock.SOUTH, true)
                : state.setValue(CrossCollisionBlock.EAST, true)
                        .setValue(CrossCollisionBlock.WEST, true);
    }

    /** Validates the complete bank blueprint during both loader smoke tests. */
    static void validateBankTemplate(ServerLevel level) {
        BlockPos origin = new BlockPos(0, 64, 0);
        BankPalette palette = paletteFor(level, origin);
        List<BankPlacement> plan = bankPlan(origin, palette);
        if (plan.isEmpty() || plan.size() > 700) {
            throw new IllegalStateException("Invalid Village Bank template size: " + plan.size());
        }
        Set<BlockPos> occupied = new HashSet<>();
        Map<BlockPos, BlockState> authored = new HashMap<>();
        int desks = 0;
        int storage = 0;
        int lights = 0;
        int jobSites = 0;
        int barrels = 0;
        int doorHalves = 0;
        int entranceStairs = 0;
        int panes = 0;
        for (BankPlacement placement : plan) {
            BlockPos relative = placement.position().subtract(origin);
            if (!occupied.add(relative)) {
                throw new IllegalStateException(
                        "Duplicate Village Bank placement at " + relative);
            }
            if (relative.getX() < -1
                    || relative.getX() > BANK_WIDTH
                    || relative.getY() < 0
                    || relative.getY() > BANK_HEIGHT
                    || relative.getZ() < BANK_PLOT_MIN_Z
                    || relative.getZ() > BANK_DEPTH) {
                throw new IllegalStateException(
                        "Village Bank placement escaped its preflight volume at " + relative);
            }
            authored.put(relative, placement.state());
            if (BankerProfessionSupport.isExchangeDesk(placement.state())) {
                desks++;
            }
            if (placement.state().is(Blocks.BARREL)) {
                barrels++;
            }
            if (placement.state().is(Blocks.CHEST)
                    || placement.state().is(Blocks.ENDER_CHEST)
                    || placement.state().is(Blocks.CHISELED_BOOKSHELF)) {
                storage++;
            }
            if (placement.state().is(Blocks.LANTERN)) {
                lights++;
            }
            if (placement.state().is(palette.door())) {
                doorHalves++;
            }
            if (placement.state().is(palette.stairs())) {
                entranceStairs++;
            }
            if (placement.state().is(Blocks.STAINED_GLASS_PANE.green())) {
                panes++;
                boolean sideWall = relative.getX() == 0
                        || relative.getX() == BANK_WIDTH - 1;
                boolean correctConnections = sideWall
                        ? placement.state().getValue(CrossCollisionBlock.NORTH)
                                && placement.state().getValue(CrossCollisionBlock.SOUTH)
                                && !placement.state().getValue(CrossCollisionBlock.EAST)
                                && !placement.state().getValue(CrossCollisionBlock.WEST)
                        : placement.state().getValue(CrossCollisionBlock.EAST)
                                && placement.state().getValue(CrossCollisionBlock.WEST)
                                && !placement.state().getValue(CrossCollisionBlock.NORTH)
                                && !placement.state().getValue(CrossCollisionBlock.SOUTH);
                if (!correctConnections) {
                    throw new IllegalStateException(
                            "Village Bank pane lost its authored wall connections at " + relative);
                }
            }
            if (PoiTypes.forState(placement.state())
                    .map(holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE))
                    .orElse(false)) {
                jobSites++;
            }
        }
        BlockPos bankerAnchor = origin.offset(BANK_WIDTH / 2, 1, BANK_DEPTH - 2);
        BlockPos counter = bankerAnchor.north();
        BlockState counterState = plan.stream()
                .filter(placement -> placement.position().equals(counter))
                .map(BankPlacement::state)
                .findFirst()
                .orElse(Blocks.AIR.defaultBlockState());
        if (desks != 1
                || !BankerProfessionSupport.isExchangeDesk(counterState)
                || storage < 9
                || lights < 3
                || barrels != 0
                || jobSites != 1
                || doorHalves != 2
                || entranceStairs != 3
                || panes != 19) {
            throw new IllegalStateException(
                    "Village Bank blueprint lost its entrance, desk-only job-site, storage, or lighting contract");
        }
        BlockPos entrance = new BlockPos(BANK_WIDTH / 2, 1, 0);
        BlockState lowerDoor = authored.get(entrance);
        BlockState upperDoor = authored.get(entrance.above());
        BlockState stair = authored.get(new BlockPos(BANK_WIDTH / 2, 0, -2));
        if (lowerDoor == null
                || !lowerDoor.is(palette.door())
                || lowerDoor.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || upperDoor == null
                || !upperDoor.is(palette.door())
                || upperDoor.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || stair == null
                || !stair.is(palette.stairs())
                || stair.getValue(StairBlock.FACING) != Direction.SOUTH) {
            throw new IllegalStateException("Village Bank entrance lost its door or terrain stair");
        }
        for (int x = BANK_WIDTH / 2 - BANK_ENTRANCE_HALF_WIDTH;
                x <= BANK_WIDTH / 2 + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            BlockState landing = authored.get(new BlockPos(x, 0, -1));
            BlockState approach = authored.get(new BlockPos(x, 0, -2));
            if (landing == null
                    || !landing.is(palette.foundation())
                    || approach == null
                    || !approach.is(palette.stairs())
                    || approach.getValue(StairBlock.FACING) != Direction.SOUTH) {
                throw new IllegalStateException(
                        "Village Bank entrance lost its three-wide landing or top stair at x=" + x);
            }
        }
        List<TerrainFoundationPlan.Cell> authoredCells = authored.keySet().stream()
                .map(position -> new TerrainFoundationPlan.Cell(
                        position.getX(), position.getY(), position.getZ()))
                .toList();
        if (!TerrainFoundationPlan.appendSupportCells(authoredCells, 0).isEmpty()) {
            throw new IllegalStateException(
                    "Village Bank blueprint contains an outdoor detail without a y=0 footing");
        }
        for (int x : new int[] {
                2, BANK_WIDTH / 2 - 2, BANK_WIDTH / 2 + 2
        }) {
            BlockState footing = authored.get(new BlockPos(x, 0, -1));
            if (footing == null || !footing.is(palette.foundation())) {
                throw new IllegalStateException(
                        "Village Bank porch or bell lost its foundation at x=" + x);
            }
        }
        for (int z = -1; z <= BANK_DEPTH; z++) {
            int distanceFromEave = Math.min(z + 1, BANK_DEPTH - z);
            int roofY = 5 + (distanceFromEave + 1) / 2;
            for (int x = -1; x <= BANK_WIDTH; x++) {
                BlockState roofState = authored.get(new BlockPos(x, roofY, z));
                if (roofState == null || !roofState.is(palette.roofDeck())) {
                    throw new IllegalStateException(
                            "Village Bank roof is not continuous at "
                                    + new BlockPos(x, roofY, z));
                }
            }
        }
        for (int x = 0; x < BANK_WIDTH; x++) {
            if (!authored.containsKey(new BlockPos(x, 5, 0))
                    || !authored.containsKey(new BlockPos(x, 5, BANK_DEPTH - 1))) {
                throw new IllegalStateException(
                        "Village Bank facade or rear wall is open below the roof at x=" + x);
            }
        }
    }

    private static void rollbackBank(ServerLevel level, List<BankMutation> placements) {
        for (int index = placements.size() - 1; index >= 0; index--) {
            BankMutation mutation = placements.get(index);
            BankPlacement placement = mutation.placement();
            if (isOwnedBankPlacement(
                    level.getBlockState(placement.position()), placement.state())) {
                level.setBlock(placement.position(), mutation.original(), 3);
            }
        }
    }

    static boolean isOwnedBankPlacement(BlockState current, BlockState planned) {
        return current != null && planned != null && current.is(planned.getBlock());
    }

    private static BankPalette paletteFor(ServerLevel level, BlockPos origin) {
        var biome = level.getBiome(origin);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return new BankPalette(
                    Blocks.SMOOTH_SANDSTONE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.SANDSTONE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.SANDSTONE_SLAB,
                    Blocks.SANDSTONE,
                    Blocks.ACACIA_FENCE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.SANDSTONE_STAIRS);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_SLAB,
                    Blocks.ACACIA_PLANKS,
                    Blocks.ACACIA_FENCE,
                    Blocks.SMOOTH_STONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)
                || biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_SLAB,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.SPRUCE_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
        }
        return new BankPalette(
                Blocks.STONE_BRICKS,
                Blocks.OAK_PLANKS,
                Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG,
                Blocks.DARK_OAK_SLAB,
                Blocks.DARK_OAK_PLANKS,
                Blocks.OAK_FENCE,
                Blocks.CHISELED_STONE_BRICKS,
                Blocks.OAK_DOOR,
                Blocks.STONE_BRICK_STAIRS);
    }

    private record BankPalette(
            Block foundation,
            Block floor,
            Block wall,
            Block corner,
            Block roof,
            Block roofDeck,
            Block fence,
            Block accent,
            Block door,
            Block stairs) {
    }

    private static boolean spawnBanker(
            ServerLevel level,
            BlockPos position,
            boolean generatedStructure,
            long regionKey,
            BlockPos exchangeDeskPosition) {
        Villager banker = EntityTypes.VILLAGER.create(
                level,
                generatedStructure
                        ? EntitySpawnReason.STRUCTURE
                        : EntitySpawnReason.NATURAL);
        if (banker == null) {
            return false;
        }
        banker.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        if (!BankerAccess.markBanker(banker, regionKey, exchangeDeskPosition)) {
            banker.discard();
            return false;
        }
        banker.setHomeTo(position, EmeraldConfig.current().bankerRestrictionRadius());
        banker.setCustomName(Component.translatable("entity.the_emerald_standard.banker"));
        return level.addFreshEntity(banker);
    }

    private static long regionKey(BlockPos position, int regionSize) {
        int regionX = Math.floorDiv(position.getX(), regionSize);
        int regionZ = Math.floorDiv(position.getZ(), regionSize);
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record BankPlacement(BlockPos position, BlockState state) {
    }

    private record BankMutation(BankPlacement placement, BlockState original) {
    }

    private record BankPlotSearch(List<BlockPos> candidates, boolean complete) {
    }

    private record BankBuildAttempt(
            BlockPos origin,
            BankBuildResult build,
            boolean searchComplete,
            boolean hadCandidates) {
    }

    private record BankBuildResult(boolean built, List<BankMutation> placements) {
        private static BankBuildResult failed() {
            return new BankBuildResult(false, List.of());
        }
    }
}
