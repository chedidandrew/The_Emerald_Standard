package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;

/** Small live-Minecraft invariant check used only by the automated server smoke workflow. */
public final class BankerIntegrationSelfTest {
    private BankerIntegrationSelfTest() {
    }

    public static void run(ServerLevel level) {
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

        Villager named = create(level);
        named.setCustomName(Component.literal("Keep Me"));
        require(!BankerAccess.isEligibleUnemployedVillager(named),
                "A custom-named villager was considered eligible");

        unemployed.discard();
        established.discard();
        named.discard();
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
