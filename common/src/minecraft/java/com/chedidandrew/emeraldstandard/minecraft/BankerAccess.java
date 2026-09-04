package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Identifies Banker villagers and opens the graphical banking dashboard. */
public final class BankerAccess {
    public static final String BANKER_TAG = "the_emerald_standard_banker";
    public static final String FIRST_BANKER_VISIT_TAG = "the_emerald_standard_first_banker_visit";
    private static final String BANK_REGION_TAG_PREFIX = "the_emerald_standard_bank_";

    private BankerAccess() {
    }

    public static boolean isBanker(Entity entity) {
        return entity instanceof Villager && entity.entityTags().contains(BANKER_TAG);
    }

    public static boolean isBankerForRegion(Entity entity, long regionKey) {
        return isBanker(entity) && entity.entityTags().contains(regionTag(regionKey));
    }

    /** Returns the persistent bank key carried by a scoped Banker, or null for legacy access. */
    public static Long bankRegionKey(Entity entity) {
        if (!isBanker(entity)) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            if (!tag.startsWith(BANK_REGION_TAG_PREFIX)) {
                continue;
            }
            try {
                return Long.parseUnsignedLong(
                        tag.substring(BANK_REGION_TAG_PREFIX.length()), 36);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed third-party or manually edited tags.
            }
        }
        return null;
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
        return openAt(
                player,
                economy,
                banker == null ? null : banker.blockPosition(),
                bankRegionKey(banker));
    }

    public static boolean openAt(
            ServerPlayer player, EconomyService economy, BlockPos accessPoint) {
        Long regionKey = null;
        if (accessPoint != null) {
            long packedAccessPoint = accessPoint.asLong();
            regionKey = economy.generatedBankAnchorsSnapshot().entrySet().stream()
                    .filter(entry -> entry.getValue() == packedAccessPoint)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
        return openAt(player, economy, accessPoint, regionKey);
    }

    private static boolean openAt(
            ServerPlayer player,
            EconomyService economy,
            BlockPos accessPoint,
            Long regionKey) {
        BankTransactionCoordinator.reconcile(player, economy);
        boolean opened = player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new BankerMenu(
                                        containerId,
                                        inventory,
                                        economy,
                                        player,
                                        accessPoint,
                                        regionKey),
                        Component.translatable("gui.the_emerald_standard.banker.title")))
                .isPresent();
        if (!opened) {
            return false;
        }

        boolean firstVisit = !player.entityTags().contains(FIRST_BANKER_VISIT_TAG);
        if (firstVisit) {
            player.addTag(FIRST_BANKER_VISIT_TAG);
            player.sendSystemMessage(
                    Component.translatable("message.the_emerald_standard.first_banker_visit"));
        }
        if (player.level() instanceof ServerLevel level) {
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.VILLAGER_TRADE,
                    SoundSource.NEUTRAL,
                    0.65F,
                    firstVisit ? 1.15F : 1.0F);
            if (firstVisit) {
                level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        6,
                        0.35,
                        0.45,
                        0.35,
                        0.02);
            }
        }
        return true;
    }

    private static String regionTag(long regionKey) {
        return BANK_REGION_TAG_PREFIX + Long.toUnsignedString(regionKey, 36);
    }
}
