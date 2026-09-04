package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Discovers loaded villages and adds one compact bank with a persistent Banker nearby.
 *
 * <p>Generation happens when a player first loads an Overworld village region, so it works in
 * existing worlds without replacing vanilla village pools or requiring a new world.</p>
 */
public final class VillageBankManager {
    private static final int BANK_WIDTH = 11;
    private static final int BANK_DEPTH = 9;
    private static final int BANK_HEIGHT = 6;

    private VillageBankManager() {
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
                // Old saves do not retain per-block bank ownership. If a counter is gone, never
                // recreate a drop-bearing block that could be farmed. A surviving barrel frame is
                // enough to restore the scoped Banker at the known bank; otherwise use the village.
                ensureBanker(
                        level,
                        intactCounter || survivingCounterFrame ? anchor : villagePosition,
                        intactCounter || survivingCounterFrame,
                        bankKey,
                        economy);
                continue;
            }

            BankPlotSearch plotSearch = findBankPlots(level, villagePosition, bankKey);
            List<BlockPos> bankOrigins = plotSearch.candidates();
            boolean completed;
            if (!bankOrigins.isEmpty()) {
                BankBuildResult build = BankBuildResult.failed();
                BlockPos bankOrigin = null;
                for (BlockPos candidate : bankOrigins) {
                    build = buildBank(level, candidate, villageId, bankKey);
                    if (build.built()) {
                        bankOrigin = candidate;
                        break;
                    }
                }
                if (!build.built()) {
                    // Protection or a transient placement failure must not remove bank access.
                    // Leave the region unmarked so a later scan can still build once a site is
                    // accepted, but ensure a non-destructive Banker exists in the meantime.
                    ensureBanker(level, villagePosition, false, bankKey, economy);
                    continue;
                }
                BlockPos bankerPosition = bankOrigin.offset(
                        BANK_WIDTH / 2, 1, BANK_DEPTH - 2);
                completed = economy.markGeneratedBankRegion(
                        bankKey, bankerPosition.asLong());
                if (completed) {
                    if (villageId != null) {
                        economy.associateBankRegionWithVillage(
                                bankKey, villageId, bankerPosition.asLong());
                    }
                    ensureBanker(level, bankerPosition, true, bankKey, economy);
                } else {
                    rollbackBank(level, build.placements());
                }
            } else {
                if (plotSearch.complete()) {
                    establishFallbackBanker(
                            level, villagePosition, bankKey, villageId, economy);
                } else {
                    // A low view distance can leave every sampled plot partly unloaded. Preserve
                    // access now, but do not make that transient result a permanent fallback.
                    ensureBanker(level, villagePosition, false, bankKey, economy);
                }
            }
        }
    }

    private static boolean establishFallbackBanker(
            ServerLevel level,
            BlockPos villagePosition,
            long bankKey,
            UUID villageId,
            EconomyService economy) {
        boolean completed = ensureBanker(level, villagePosition, false, bankKey, economy)
                && economy.markGeneratedBankRegion(bankKey, villagePosition.asLong());
        if (completed && villageId != null) {
            economy.associateBankRegionWithVillage(
                    bankKey, villageId, villagePosition.asLong());
        }
        return completed;
    }

    private static boolean ensureBanker(
            ServerLevel level,
            BlockPos bankerAnchor,
            boolean generatedStructure,
            long regionKey,
            EconomyService economy) {
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
            BankerAccess.markBanker(existing, regionKey);
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
        if (legacyBanker != null && BankerAccess.markBanker(legacyBanker, regionKey)) {
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
        if (candidate != null && BankerAccess.markBanker(candidate, regionKey)) {
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
        return spawnBanker(level, spawnPosition, generatedStructure, regionKey);
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
        int minimumX = centerX - BANK_WIDTH / 2 - 1;
        int maximumX = centerX + BANK_WIDTH / 2 + 1;
        int minimumZ = centerZ - BANK_DEPTH / 2 - 1;
        int maximumZ = centerZ + BANK_DEPTH / 2 + 1;
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
        int surfaceHeight = Integer.MIN_VALUE;
        for (int x = centerX - BANK_WIDTH / 2 - 1;
                x <= centerX + BANK_WIDTH / 2 + 1;
                x++) {
            for (int z = centerZ - BANK_DEPTH / 2 - 1;
                    z <= centerZ + BANK_DEPTH / 2 + 1;
                    z++) {
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
                if (surfaceHeight == Integer.MIN_VALUE) {
                    surfaceHeight = surface;
                } else if (surface != surfaceHeight) {
                    return null;
                }
            }
        }

        // Place the floor in air above natural terrain. Never replace the ground layer, a path,
        // farmland, player flooring, a container, or another structure's roof.
        BlockPos origin = new BlockPos(
                centerX - BANK_WIDTH / 2,
                surfaceHeight,
                centerZ - BANK_DEPTH / 2);
        for (int x = -1; x <= BANK_WIDTH; x++) {
            for (int z = -1; z <= BANK_DEPTH; z++) {
                for (int y = 0; y <= BANK_HEIGHT; y++) {
                    BlockPos position = origin.offset(x, y, z);
                    if (level.getBlockEntity(position) != null
                            || !level.getBlockState(position).isAir()) {
                        return null;
                    }
                }
            }
        }
        return origin;
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

    /** Recognizes only the authored barrel pair around a generated counter, without placing it. */
    private static boolean hasBankCounterFrame(ServerLevel level, BlockPos bankerAnchor) {
        BlockPos counter = bankerAnchor.north();
        return isLoaded(level, counter.west())
                && isLoaded(level, counter.east())
                && level.getBlockState(counter.west()).is(Blocks.BARREL)
                && level.getBlockState(counter.east()).is(Blocks.BARREL);
    }

    private static BankBuildResult buildBank(
            ServerLevel level, BlockPos origin, UUID villageId, long bankKey) {
        List<BankPlacement> plan = bankPlan(origin, paletteFor(level, origin));
        for (BankPlacement placement : plan) {
            if (!isLoaded(level, placement.position())) {
                return BankBuildResult.failed();
            }
            BlockState existing = level.getBlockState(placement.position());
            if (!existing.isAir()
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

        List<BankPlacement> placed = new ArrayList<>(plan.size());
        for (BankPlacement placement : plan) {
            boolean changed = level.setBlock(placement.position(), placement.state(), 3);
            BlockState applied = level.getBlockState(placement.position());
            if (!changed || !applied.equals(placement.state())) {
                // Some integrations can report a failed/cancelled placement after mutating the
                // world. Include that authored block in rollback when it did appear.
                if (isOwnedBankPlacement(applied, placement.state())) {
                    placed.add(placement);
                }
                rollbackBank(level, placed);
                return BankBuildResult.failed();
            }
            placed.add(placement);
        }
        return new BankBuildResult(true, List.copyOf(placed));
    }

    private static List<BankPlacement> bankPlan(BlockPos origin, BankPalette palette) {
        List<BankPlacement> placements = new ArrayList<>();
        for (int x = 0; x < BANK_WIDTH; x++) {
            for (int z = 0; z < BANK_DEPTH; z++) {
                placements.add(new BankPlacement(
                        origin.offset(x, 0, z), palette.floor().defaultBlockState()));
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
                            && (x == BANK_WIDTH / 2 || x == BANK_WIDTH / 2 + 1)
                            && y <= 2;
                    boolean window = y == 2
                            && ((z == 0 && (x == 2 || x == BANK_WIDTH - 3))
                                    || ((x == 0 || x == BANK_WIDTH - 1)
                                            && (z == 2 || z == BANK_DEPTH - 3)));
                    if (entrance) {
                        continue;
                    }
                    placements.add(new BankPlacement(
                            origin.offset(x, y, z),
                            window
                                    ? Blocks.STAINED_GLASS_PANE.green().defaultBlockState()
                                    : (corner ? palette.corner() : palette.wall())
                                            .defaultBlockState()));
                }
            }
        }

        for (int x = -1; x <= BANK_WIDTH; x++) {
            for (int z = -1; z <= BANK_DEPTH; z++) {
                placements.add(new BankPlacement(
                        origin.offset(x, 5, z), palette.roof().defaultBlockState()));
            }
        }

        int counterZ = BANK_DEPTH - 3;
        for (int x = 2; x <= BANK_WIDTH - 3; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 1, counterZ),
                    x == BANK_WIDTH / 2
                            ? BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState()
                            : Blocks.BARREL.defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(1, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, 3), palette.fence().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 1, 3), palette.fence().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 2, 3), Blocks.LANTERN.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 2, 3), Blocks.LANTERN.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 4, BANK_DEPTH / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
        return List.copyOf(placements);
    }

    private static void rollbackBank(ServerLevel level, List<BankPlacement> placements) {
        for (int index = placements.size() - 1; index >= 0; index--) {
            BankPlacement placement = placements.get(index);
            if (isOwnedBankPlacement(
                    level.getBlockState(placement.position()), placement.state())) {
                level.setBlock(placement.position(), Blocks.AIR.defaultBlockState(), 3);
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
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.SANDSTONE_SLAB,
                    Blocks.ACACIA_FENCE);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_SLAB,
                    Blocks.ACACIA_FENCE);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)
                || biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_SLAB,
                    Blocks.SPRUCE_FENCE);
        }
        return new BankPalette(
                Blocks.STONE_BRICKS,
                Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG,
                Blocks.DARK_OAK_SLAB,
                Blocks.OAK_FENCE);
    }

    private record BankPalette(Block floor, Block wall, Block corner, Block roof, Block fence) {
    }

    private static boolean spawnBanker(
            ServerLevel level, BlockPos position, boolean generatedStructure, long regionKey) {
        Villager banker = EntityTypes.VILLAGER.create(
                level,
                generatedStructure
                        ? EntitySpawnReason.STRUCTURE
                        : EntitySpawnReason.NATURAL);
        if (banker == null) {
            return false;
        }
        banker.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        if (!BankerAccess.markBanker(banker, regionKey)) {
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

    private record BankPlotSearch(List<BlockPos> candidates, boolean complete) {
    }

    private record BankBuildResult(boolean built, List<BankPlacement> placements) {
        private static BankBuildResult failed() {
            return new BankBuildResult(false, List.of());
        }
    }
}
