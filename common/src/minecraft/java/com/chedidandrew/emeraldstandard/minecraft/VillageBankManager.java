package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Discovers loaded villages and adds one compact bank with a persistent Banker nearby.
 *
 * <p>Generation happens when a player first loads a village region, so it works in existing worlds
 * without replacing vanilla village pools or requiring a new world.</p>
 */
public final class VillageBankManager {
    private static final int SCAN_INTERVAL_TICKS = 200;
    private static final int REGION_SIZE = 256;
    private static final int BANK_WIDTH = 11;
    private static final int BANK_DEPTH = 9;
    private static final int BANK_HEIGHT = 6;

    private VillageBankManager() {
    }

    public static void tick(MinecraftServer server, EconomyService economy) {
        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        if (gameTime % SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level || !level.isVillage(player.blockPosition())) {
                continue;
            }
            BlockPos villagePosition = player.blockPosition();
            long regionKey = regionKey(villagePosition);
            if (economy.hasGeneratedBankRegion(regionKey)) {
                ensureBanker(level, villagePosition);
                continue;
            }

            BlockPos bankOrigin = findBankPlot(level, villagePosition, regionKey);
            boolean completed;
            if (bankOrigin != null) {
                buildBank(level, bankOrigin);
                completed = spawnBanker(
                        level,
                        bankOrigin.offset(BANK_WIDTH / 2, 1, BANK_DEPTH - 3));
            } else {
                completed = ensureBanker(level, villagePosition);
            }
            if (completed) {
                economy.markGeneratedBankRegion(regionKey);
            }
        }
    }

    private static boolean ensureBanker(ServerLevel level, BlockPos villagePosition) {
        AABB search = new AABB(villagePosition).inflate(72.0, 24.0, 72.0);
        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                search,
                villager -> villager.isAlive() && !villager.isBaby());
        if (villagers.stream().anyMatch(BankerAccess::isBanker)) {
            return true;
        }

        Villager candidate = villagers.stream()
                .min(Comparator.comparingDouble(villager ->
                        villager.distanceToSqr(
                                villagePosition.getX() + 0.5,
                                villagePosition.getY() + 0.5,
                                villagePosition.getZ() + 0.5)))
                .orElse(null);
        if (candidate != null) {
            BankerAccess.markBanker(candidate);
            return true;
        }

        int y = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                villagePosition.getX(),
                villagePosition.getZ());
        return spawnBanker(level, new BlockPos(villagePosition.getX(), y, villagePosition.getZ()));
    }

    private static BlockPos findBankPlot(
            ServerLevel level,
            BlockPos villagePosition,
            long regionKey) {
        int[][] offsets = {
                {28, 0}, {-28, 0}, {0, 28}, {0, -28},
                {34, 20}, {-34, 20}, {34, -20}, {-34, -20},
                {42, 0}, {-42, 0}, {0, 42}, {0, -42}
        };
        int rotation = Math.floorMod((int) (regionKey ^ (regionKey >>> 32)), offsets.length);
        for (int step = 0; step < offsets.length; step++) {
            int[] offset = offsets[(step + rotation) % offsets.length];
            int centerX = villagePosition.getX() + offset[0];
            int centerZ = villagePosition.getZ() + offset[1];
            BlockPos origin = safeOrigin(level, centerX, centerZ);
            if (origin != null) {
                return origin;
            }
        }
        return null;
    }

    private static BlockPos safeOrigin(ServerLevel level, int centerX, int centerZ) {
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (int x = centerX - BANK_WIDTH / 2 - 1;
                x <= centerX + BANK_WIDTH / 2 + 1;
                x++) {
            for (int z = centerZ - BANK_DEPTH / 2 - 1;
                    z <= centerZ + BANK_DEPTH / 2 + 1;
                    z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                if (!level.hasChunkAt(ground)
                        || level.getBlockState(ground).isAir()
                        || !level.getFluidState(ground).isEmpty()) {
                    return null;
                }
                minHeight = Math.min(minHeight, surface);
                maxHeight = Math.max(maxHeight, surface);
            }
        }
        if (maxHeight - minHeight > 1) {
            return null;
        }

        int floorY = maxHeight - 1;
        BlockPos origin = new BlockPos(
                centerX - BANK_WIDTH / 2,
                floorY,
                centerZ - BANK_DEPTH / 2);
        for (int x = -1; x <= BANK_WIDTH; x++) {
            for (int z = -1; z <= BANK_DEPTH; z++) {
                for (int y = 1; y <= BANK_HEIGHT + 1; y++) {
                    BlockPos position = origin.offset(x, y, z);
                    var state = level.getBlockState(position);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return null;
                    }
                }
            }
        }
        return origin;
    }

    private static void buildBank(ServerLevel level, BlockPos origin) {
        for (int x = 0; x < BANK_WIDTH; x++) {
            for (int z = 0; z < BANK_DEPTH; z++) {
                BlockPos floor = origin.offset(x, 0, z);
                level.setBlock(floor, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int depth = 1; depth <= 2; depth++) {
                    BlockPos support = floor.below(depth);
                    if (level.getBlockState(support).isAir()
                            || level.getBlockState(support).canBeReplaced()) {
                        level.setBlock(support, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                }
            }
        }

        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    boolean edge = x == 0 || x == BANK_WIDTH - 1 || z == 0 || z == BANK_DEPTH - 1;
                    if (!edge) {
                        level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
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
                        level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    } else if (window) {
                        level.setBlock(
                                origin.offset(x, y, z),
                                Blocks.STAINED_GLASS_PANE.green().defaultBlockState(),
                                3);
                    } else {
                        level.setBlock(
                                origin.offset(x, y, z),
                                (corner ? Blocks.STRIPPED_OAK_LOG : Blocks.OAK_PLANKS)
                                        .defaultBlockState(),
                                3);
                    }
                }
            }
        }

        for (int x = -1; x <= BANK_WIDTH; x++) {
            for (int z = -1; z <= BANK_DEPTH; z++) {
                level.setBlock(
                        origin.offset(x, 5, z),
                        Blocks.DARK_OAK_SLAB.defaultBlockState(),
                        3);
            }
        }

        int counterZ = BANK_DEPTH - 3;
        for (int x = 2; x <= BANK_WIDTH - 3; x++) {
            level.setBlock(
                    origin.offset(x, 1, counterZ),
                    x == BANK_WIDTH / 2
                            ? Blocks.LECTERN.defaultBlockState()
                            : Blocks.BARREL.defaultBlockState(),
                    3);
        }
        level.setBlock(origin.offset(1, 1, 1), Blocks.BOOKSHELF.defaultBlockState(), 3);
        level.setBlock(origin.offset(1, 2, 1), Blocks.BOOKSHELF.defaultBlockState(), 3);
        level.setBlock(
                origin.offset(BANK_WIDTH - 2, 1, 1),
                Blocks.BOOKSHELF.defaultBlockState(),
                3);
        level.setBlock(
                origin.offset(BANK_WIDTH - 2, 2, 1),
                Blocks.BOOKSHELF.defaultBlockState(),
                3);
        level.setBlock(origin.offset(2, 1, 3), Blocks.OAK_FENCE.defaultBlockState(), 3);
        level.setBlock(
                origin.offset(BANK_WIDTH - 3, 1, 3),
                Blocks.OAK_FENCE.defaultBlockState(),
                3);
        level.setBlock(origin.offset(2, 2, 3), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(
                origin.offset(BANK_WIDTH - 3, 2, 3),
                Blocks.LANTERN.defaultBlockState(),
                3);
        level.setBlock(
                origin.offset(BANK_WIDTH / 2, 4, BANK_DEPTH / 2),
                Blocks.LANTERN.defaultBlockState(),
                3);
    }

    private static boolean spawnBanker(ServerLevel level, BlockPos position) {
        Villager banker = EntityTypes.VILLAGER.create(level, EntitySpawnReason.NATURAL);
        if (banker == null) {
            return false;
        }
        banker.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        BankerAccess.markBanker(banker);
        banker.setCustomName(Component.translatable("entity.the_emerald_standard.banker"));
        return level.addFreshEntity(banker);
    }

    private static long regionKey(BlockPos position) {
        int regionX = Math.floorDiv(position.getX(), REGION_SIZE);
        int regionZ = Math.floorDiv(position.getZ(), REGION_SIZE);
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }
}
