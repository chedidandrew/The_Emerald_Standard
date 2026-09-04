package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;

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
