package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Identifies Banker villagers and opens the graphical banking dashboard. */
public final class BankerAccess {
    public static final String BANKER_TAG = "the_emerald_standard_banker";
    private static final String BANK_REGION_TAG_PREFIX = "the_emerald_standard_bank_";

    private BankerAccess() {
    }

    public static boolean isBanker(Entity entity) {
        return entity instanceof Villager && entity.entityTags().contains(BANKER_TAG);
    }

    public static boolean isBankerForRegion(Entity entity, long regionKey) {
        return isBanker(entity) && entity.entityTags().contains(regionTag(regionKey));
    }

    public static boolean isLegacyUnscopedBanker(Entity entity) {
        if (!isBanker(entity)) {
            return false;
        }
        return entity.entityTags().stream().noneMatch(tag -> tag.startsWith(BANK_REGION_TAG_PREFIX));
    }

    /**
     * Returns true only for an untouched adult villager that can safely become a Banker.
     * Existing professions, offers, XP, names, babies, and dead villagers are never repurposed.
     */
    public static boolean isEligibleUnemployedVillager(Villager villager) {
        return villager.isAlive()
                && !villager.isBaby()
                && !villager.hasCustomName()
                && villager.getVillagerXp() == 0
                && villager.getOffers().isEmpty()
                && villager.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    /**
     * Marks a newly spawned or untouched unemployed villager as the Banker for one village region.
     * Existing Banker tags are migrated to the supplied region id without resetting their data.
     */
    public static boolean markBanker(Villager villager, long regionKey) {
        boolean existingBanker = isBanker(villager);
        if (!existingBanker && !isEligibleUnemployedVillager(villager)) {
            return false;
        }

        villager.addTag(BANKER_TAG);
        for (String tag : List.copyOf(villager.entityTags())) {
            if (tag.startsWith(BANK_REGION_TAG_PREFIX)) {
                villager.removeTag(tag);
            }
        }
        villager.addTag(regionTag(regionKey));
        villager.setCustomName(Component.translatable("entity.the_emerald_standard.banker"));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();

        if (!existingBanker) {
            BuiltInRegistries.VILLAGER_PROFESSION.get(VillagerProfession.LIBRARIAN)
                    .ifPresent(profession -> villager.setVillagerData(
                            villager.getVillagerData().withProfession(profession).withLevel(1)));
            villager.setVillagerDataFinalized(true);
            if (villager.level() instanceof ServerLevel level) {
                villager.refreshBrain(level);
            }
        }
        return true;
    }

    /** Compatibility overload used only when no persistent village-region identity exists. */
    public static boolean markBanker(Villager villager) {
        return markBanker(villager, 0L);
    }

    public static boolean open(ServerPlayer player, EconomyService economy) {
        return openAt(player, economy, null);
    }

    public static boolean open(
            ServerPlayer player, EconomyService economy, Entity banker) {
        return openAt(player, economy, banker == null ? null : banker.blockPosition());
    }

    public static boolean openAt(
            ServerPlayer player, EconomyService economy, BlockPos accessPoint) {
        BankTransactionCoordinator.reconcile(player, economy);
        return player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new BankerMenu(
                                        containerId,
                                        inventory,
                                        economy,
                                        player,
                                        accessPoint),
                        Component.translatable("gui.the_emerald_standard.banker.title")))
                .isPresent();
    }

    private static String regionTag(long regionKey) {
        return BANK_REGION_TAG_PREFIX + Long.toUnsignedString(regionKey, 36);
    }
}
