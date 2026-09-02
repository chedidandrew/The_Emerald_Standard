package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

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
        require(!BankerAccess.isBankerForRegion(unemployed, 124L),
                "Banker was associated with the wrong region");

        Villager established = create(level);
        var farmer = BuiltInRegistries.VILLAGER_PROFESSION
                .get(VillagerProfession.FARMER)
                .orElseThrow();
        established.setVillagerData(
                established.getVillagerData().withProfession(farmer).withLevel(2));
        established.setVillagerXp(10);
        require(!BankerAccess.isEligibleUnemployedVillager(established),
                "An established farmer was considered eligible");
        require(!BankerAccess.markBanker(established, 123L),
                "An established farmer was repurposed as a Banker");

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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
