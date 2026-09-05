package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.ResetProfession;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Small live-Minecraft invariant check used only by the automated server smoke workflow. */
public final class BankerIntegrationSelfTest {
    private BankerIntegrationSelfTest() {
    }

    public static void run(ServerLevel level) {
        BankerMenuPacketCodecSelfTest.verify();
        verifyInventoryPersistenceGuard();
        require(BankerProfessionSupport.exchangeDeskOrLectern() != Blocks.LECTERN,
                "The Exchange Desk block was not registered before server startup");
        require(BankerProfessionSupport.registeredBanker().isPresent(),
                "The Banker profession was not registered before server startup");
        verifyExchangeDeskGeometry(level);
        var exchangeDeskItem = BuiltInRegistries.ITEM.get(
                        BankerProfessionSupport.EXCHANGE_DESK_ITEM_KEY)
                .orElseThrow(() -> new IllegalStateException(
                        "The Exchange Desk item was not registered before server startup"))
                .value();
        CreativeModeTabs.tryRebuildTabContents(
                level.getServer().getWorldData().enabledFeatures(),
                false,
                level.registryAccess());
        ItemStack exchangeDeskStack = new ItemStack(exchangeDeskItem);
        require(BuiltInRegistries.CREATIVE_MODE_TAB
                        .get(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                        .orElseThrow()
                        .value()
                        .contains(exchangeDeskStack),
                "The Exchange Desk was absent from the Functional Blocks creative tab");
        require(CreativeModeTabs.searchTab().contains(exchangeDeskStack),
                "The Exchange Desk was absent from creative inventory search");
        require(BankerProfessionSupport.isBankWorkstation(
                        BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState())
                        && BankerProfessionSupport.isBankWorkstation(
                                Blocks.LECTERN.defaultBlockState()),
                "Custom or legacy bank workstation recognition failed");
        require(PoiTypes.hasPoi(
                        BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState()),
                "The Exchange Desk block states were not mapped to the Banker POI");
        require(BuiltInRegistries.POINT_OF_INTEREST_TYPE
                        .get(BankerProfessionSupport.BANKER_POI_KEY)
                        .orElseThrow()
                        .is(PoiTypeTags.ACQUIRABLE_JOB_SITE),
                "The Banker POI was not an acquirable villager job site");
        require(level.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(
                                "the_emerald_standard", "first_banker"))
                        != null,
                "The First Banker advancement was not loaded");

        Villager naturalBanker = create(level);
        naturalBanker.setVillagerData(naturalBanker.getVillagerData().withProfession(
                BankerProfessionSupport.registeredBanker().orElseThrow()));
        require(BankerAccess.isBanker(naturalBanker),
                "A villager that naturally claimed the Banker profession was not interactive");
        require(BankerAccess.bankRegionKey(naturalBanker) == null,
                "An unscoped natural Banker invented a generated-bank identity");

        Villager unemployed = create(level);
        require(BankerAccess.isEligibleUnemployedVillager(unemployed),
                "A fresh unemployed adult was not eligible");
        require(!BankerAccess.repairManagedBankerForInteraction(unemployed)
                        && unemployed.getVillagerData().profession().is(VillagerProfession.NONE)
                        && unemployed.getVillagerXp() == 0,
                "The interaction migration modified an untagged normal villager");
        require(BankerAccess.markBanker(unemployed, 123L),
                "A fresh unemployed adult could not become a Banker");
        require(BankerAccess.isBankerForRegion(unemployed, 123L),
                "Banker region identity was not applied");
        require(Long.valueOf(123L).equals(BankerAccess.bankRegionKey(unemployed)),
                "Banker region identity could not be decoded for menu routing");
        require(!BankerAccess.isBankerForRegion(unemployed, 124L),
                "Banker was associated with the wrong region");
        require(BankerProfessionSupport.isRegisteredBanker(
                        unemployed.getVillagerData().profession()),
                "A newly assigned Banker did not receive the registered profession");
        require(unemployed.getVillagerXp() == 1,
                "A managed Banker did not lock its zero-trade profession");
        require(!ResetProfession.create().tryStart(
                        level, unemployed, level.getGameTime())
                        && BankerProfessionSupport.isRegisteredBanker(
                                unemployed.getVillagerData().profession()),
                "Vanilla immediately reset a managed Banker's profession");
        verifyManagedBankerWorkstation(level);

        Villager unlockedProfession = create(level);
        unlockedProfession.setVillagerData(
                unlockedProfession.getVillagerData().withProfession(
                        BankerProfessionSupport.registeredBanker().orElseThrow()));
        require(ResetProfession.create().tryStart(
                        level, unlockedProfession, level.getGameTime())
                        && unlockedProfession.getVillagerData().profession()
                                .is(VillagerProfession.NONE),
                "The profession-reset regression fixture no longer exercises vanilla behavior");

        Villager interactionMigrated = create(level);
        require(BankerAccess.markBanker(interactionMigrated, 127L),
                "Could not prepare the interaction-migration fixture");
        interactionMigrated.setVillagerXp(0);
        interactionMigrated.setVillagerData(interactionMigrated.getVillagerData()
                .withProfession(BuiltInRegistries.VILLAGER_PROFESSION
                        .get(VillagerProfession.NONE)
                        .orElseThrow()));
        require(BankerAccess.repairManagedBankerForInteraction(interactionMigrated),
                "Direct interaction did not migrate an old reset managed Banker");
        require(BankerProfessionSupport.isRegisteredBanker(
                        interactionMigrated.getVillagerData().profession())
                        && interactionMigrated.getVillagerXp() == 1
                        && Long.valueOf(127L).equals(
                                BankerAccess.bankRegionKey(interactionMigrated)),
                "Interaction migration did not lock the Banker or preserve its region scope");
        require(!ResetProfession.create().tryStart(
                        level, interactionMigrated, level.getGameTime()),
                "Vanilla reset the Banker after direct-interaction migration");

        Villager unscopedLibrarian = create(level);
        unscopedLibrarian.addTag(BankerAccess.BANKER_TAG);
        unscopedLibrarian.setVillagerData(
                unscopedLibrarian.getVillagerData().withProfession(
                        BuiltInRegistries.VILLAGER_PROFESSION
                                .get(VillagerProfession.LIBRARIAN)
                                .orElseThrow()));
        require(BankerAccess.repairManagedBankerForInteraction(unscopedLibrarian)
                        && BankerProfessionSupport.isRegisteredBanker(
                                unscopedLibrarian.getVillagerData().profession())
                        && unscopedLibrarian.getVillagerXp() == 1
                        && BankerAccess.bankRegionKey(unscopedLibrarian) == null,
                "Interaction migration changed an unscoped legacy Banker's identity");

        Villager legacy = create(level);
        var librarian = BuiltInRegistries.VILLAGER_PROFESSION
                .get(VillagerProfession.LIBRARIAN)
                .orElseThrow();
        legacy.setVillagerData(
                legacy.getVillagerData().withProfession(librarian).withLevel(4));
        legacy.setVillagerXp(150);
        legacy.addTag(BankerAccess.BANKER_TAG);
        require(BankerAccess.markBanker(legacy, 125L),
                "A tagged legacy Banker could not be migrated");
        require(BankerProfessionSupport.isRegisteredBanker(
                        legacy.getVillagerData().profession())
                        && legacy.getVillagerData().level() == 4
                        && legacy.getVillagerXp() == 150,
                "Legacy Banker migration reset profession progress");

        Villager established = create(level);
        var farmer = BuiltInRegistries.VILLAGER_PROFESSION
                .get(VillagerProfession.FARMER)
                .orElseThrow();
        established.setVillagerData(
                established.getVillagerData().withProfession(farmer).withLevel(2));
        established.setVillagerXp(10);
        require(VillageProsperityManager.professionId(
                                established.getVillagerData().profession())
                        .equals("minecraft:farmer"),
                "A registered villager profession did not resolve to its resource ID");
        require(!BankerAccess.isEligibleUnemployedVillager(established),
                "An established farmer was considered eligible");
        require(!BankerAccess.markBanker(established, 123L),
                "An established farmer was repurposed as a Banker");
        established.addTag(BankerAccess.BANKER_TAG);
        require(!BankerAccess.repairManagedBankerForInteraction(established)
                        && established.getVillagerData().profession().is(VillagerProfession.FARMER)
                        && established.getVillagerXp() == 10,
                "Interaction migration overwrote an established non-legacy profession");
        established.removeTag(BankerAccess.BANKER_TAG);

        require(VillageBankManager.isNaturalBankGround(
                                Blocks.GRASS_BLOCK.defaultBlockState())
                        && VillageProsperityManager.isNaturalProjectGround(
                                Blocks.GRASS_BLOCK.defaultBlockState()),
                "Ordinary village grass was rejected as safe natural ground");
        require(!VillageBankManager.isNaturalBankGround(
                                Blocks.DIRT_PATH.defaultBlockState())
                        && !VillageProsperityManager.isNaturalProjectGround(
                                Blocks.FARMLAND.defaultBlockState())
                        && !VillageBankManager.isNaturalBankGround(
                                Blocks.SNOW.defaultBlockState())
                        && !VillageProsperityManager.isNaturalProjectGround(
                                Blocks.MUD.defaultBlockState()),
                "A path, farmland, snow layer, or short support block was accepted as ground");
        require(VillageProsperityManager.isSafeTemplateUpgradeTarget(
                        Blocks.AIR.defaultBlockState(), false, true),
                "An empty protected-safe template suffix position was rejected");
        require(!VillageProsperityManager.isSafeTemplateUpgradeTarget(
                        Blocks.CHEST.defaultBlockState(), true, true),
                "A player block entity could be adopted as authored template storage");
        require(!VillageProsperityManager.isSafeTemplateUpgradeTarget(
                        BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState(),
                        false,
                        true),
                "A player workstation could be adopted as an authored template suffix");
        require(!VillageProsperityManager.isSafeTemplateUpgradeTarget(
                        Blocks.AIR.defaultBlockState(), false, false),
                "A protection-vetoed template suffix position was accepted");
        require(VillageBankManager.isOwnedBankPlacement(
                        Blocks.LANTERN.defaultBlockState()
                                .setValue(LanternBlock.HANGING, true),
                        Blocks.LANTERN.defaultBlockState()),
                "Rollback ownership rejected a neighbor-updated state of the planned block");
        require(!VillageBankManager.isOwnedBankPlacement(
                        Blocks.TORCH.defaultBlockState(),
                        Blocks.LANTERN.defaultBlockState()),
                "Rollback ownership accepted a different replacement block");
        verifyProtectionGuards(level);
        VillageBankManager.validateBankTemplate(level);
        VillageProsperityManager.validateProjectTemplates(level);

        Villager named = create(level);
        named.setCustomName(Component.literal("Keep Me"));
        require(!BankerAccess.isEligibleUnemployedVillager(named),
                "A custom-named villager was considered eligible");

        unemployed.discard();
        naturalBanker.discard();
        legacy.discard();
        established.discard();
        named.discard();
        unlockedProfession.discard();
        interactionMigrated.discard();
        unscopedLibrarian.discard();
    }

    private static void verifyInventoryPersistenceGuard() {
        CompoundTag expected = playerDataWithInventoryCount(4);
        require(BankTransactionCoordinator.inventoryMatches(expected, expected.copy()),
                "Equivalent saved inventory payloads did not match");
        require(!BankTransactionCoordinator.inventoryMatches(
                        expected, playerDataWithInventoryCount(3)),
                "A changed saved inventory payload passed persistence verification");
        require(!BankTransactionCoordinator.inventoryMatches(expected, new CompoundTag()),
                "A missing saved inventory payload passed persistence verification");

        Path directory = null;
        try {
            directory = Files.createTempDirectory("emerald-standard-player-checkpoint-");
            Path playerData = directory.resolve("integration.dat");
            Path oldPlayerData = directory.resolve("integration.dat_old");
            CompoundTag original = playerDataWithInventoryCount(7);
            NbtIo.writeCompressed(original, playerData);
            require(BankTransactionCoordinator.writeAndVerifyPlayerData(
                            directory, "integration", expected),
                    "Target-player checkpoint did not complete");
            CompoundTag persisted = NbtIo.readCompressed(
                    playerData, NbtAccounter.unlimitedHeap());
            CompoundTag backup = NbtIo.readCompressed(
                    oldPlayerData, NbtAccounter.unlimitedHeap());
            require(BankTransactionCoordinator.inventoryMatches(expected, persisted),
                    "Target-player checkpoint readback lost the expected inventory");
            require(BankTransactionCoordinator.inventoryMatches(original, backup),
                    "Target-player checkpoint did not preserve the prior .dat_old inventory");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not verify the target-player persistence checkpoint", exception);
        } finally {
            deleteCheckpointFixture(directory);
        }
    }

    private static void deleteCheckpointFixture(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve("integration.dat"));
            Files.deleteIfExists(directory.resolve("integration.dat_old"));
            Files.deleteIfExists(directory);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not clean the target-player persistence fixture", exception);
        }
    }

    private static CompoundTag playerDataWithInventoryCount(int count) {
        CompoundTag stack = new CompoundTag();
        stack.putInt("count", count);
        ListTag inventory = new ListTag();
        inventory.add(stack);
        CompoundTag playerData = new CompoundTag();
        playerData.put("Inventory", inventory);
        return playerData;
    }

    private static Villager create(ServerLevel level) {
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) {
            throw new IllegalStateException("Could not create villager for integration smoke test");
        }
        return villager;
    }

    private static void verifyExchangeDeskGeometry(ServerLevel level) {
        Block exchangeDesk = BankerProfessionSupport.exchangeDeskOrLectern();
        require(exchangeDesk instanceof ExchangeDeskBlock,
                "The registered Exchange Desk did not use its directional block type");
        BlockState north = exchangeDesk.defaultBlockState();
        require(north.hasProperty(HorizontalDirectionalBlock.FACING)
                        && north.getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH,
                "Generated Exchange Desks no longer default to facing north");
        require(exchangeDesk.getStateDefinition().getPossibleStates().size() == 4,
                "The Exchange Desk did not expose exactly four horizontal facings");

        VoxelShape selection = north.getShape(level, BlockPos.ZERO);
        VoxelShape collision = north.getCollisionShape(level, BlockPos.ZERO);
        require(selection.max(Direction.Axis.Y) == ExchangeDeskBlock.MODEL_HEIGHT
                        && collision.max(Direction.Axis.Y) == ExchangeDeskBlock.MODEL_HEIGHT,
                "The Exchange Desk selection or collision shape exceeded its 13.5/16 model height");
        for (Direction facing : new Direction[] {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        }) {
            require(PoiTypes.hasPoi(north.setValue(HorizontalDirectionalBlock.FACING, facing)),
                    "An Exchange Desk facing was not mapped to the Banker POI: " + facing);
        }
    }

    private static void verifyManagedBankerWorkstation(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos deskPosition = findEmptyFixturePosition(level, spawn);
        BlockState previous = level.getBlockState(deskPosition);
        Villager banker = null;
        try {
            require(level.setBlockAndUpdate(
                            deskPosition,
                            BankerProfessionSupport.exchangeDeskOrLectern()
                                    .defaultBlockState()),
                    "The Banker workstation fixture could not place an Exchange Desk");
            banker = create(level);
            banker.teleportTo(
                    deskPosition.getX() + 0.5,
                    deskPosition.getY(),
                    deskPosition.getZ() + 1.5);
            require(BankerAccess.markBanker(banker, 126L, deskPosition),
                    "A managed Banker could not bind to its Exchange Desk");
            require(banker.getBrain()
                            .getMemory(MemoryModuleType.JOB_SITE)
                            .filter(position -> position.dimension().equals(level.dimension())
                                    && position.pos().equals(deskPosition))
                            .isPresent(),
                    "A managed Banker did not remember its Exchange Desk job site");
            require(level.getPoiManager().getCountInRange(
                            holder -> holder.is(BankerProfessionSupport.BANKER_POI_KEY),
                            deskPosition,
                            0,
                            PoiManager.Occupancy.IS_OCCUPIED) == 1L,
                    "A managed Banker did not reserve its Exchange Desk POI");
        } finally {
            if (banker != null) {
                banker.releasePoi(MemoryModuleType.JOB_SITE);
                banker.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
                banker.discard();
            }
            level.setBlockAndUpdate(deskPosition, previous);
        }
    }

    private static BlockPos findEmptyFixturePosition(ServerLevel level, BlockPos spawn) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (radius > 0
                            && Math.abs(xOffset) != radius
                            && Math.abs(zOffset) != radius) {
                        continue;
                    }
                    int x = spawn.getX() + xOffset;
                    int z = spawn.getZ() + zOffset;
                    int surfaceY = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    int firstCandidate = Math.max(
                            level.getMinY(), Math.min(level.getMaxY(), surfaceY + 1));
                    for (int y = firstCandidate; y <= level.getMaxY(); y++) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        if (level.getBlockState(candidate).isAir()) {
                            return candidate;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Could not find empty loaded space for the Banker workstation fixture");
    }

    private static void verifyProtectionGuards(ServerLevel level) {
        AutoCloseable denied = VillageDevelopmentProtection.register(context -> false);
        try {
            require(!VillageDevelopmentProtection.mayPlace(
                            level,
                            null,
                            1L,
                            BlockPos.ZERO,
                            Blocks.AIR.defaultBlockState(),
                            Blocks.STONE.defaultBlockState()),
                    "A registered protection veto allowed construction");
        } finally {
            close(denied);
        }

        AutoCloseable failed = VillageDevelopmentProtection.register(context -> {
            throw new IllegalStateException("expected guard failure");
        });
        try {
            require(!VillageDevelopmentProtection.mayPlace(
                            level,
                            null,
                            2L,
                            BlockPos.ZERO,
                            Blocks.AIR.defaultBlockState(),
                            Blocks.STONE.defaultBlockState()),
                    "A failed protection guard did not deny construction");
        } finally {
            close(failed);
        }
    }

    private static void close(AutoCloseable handle) {
        try {
            handle.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not unregister protection self-test guard", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
