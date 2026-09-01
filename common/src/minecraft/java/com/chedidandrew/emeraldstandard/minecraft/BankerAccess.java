package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/** Identifies Banker villagers and opens the graphical banking dashboard. */
public final class BankerAccess {
    public static final String BANKER_TAG = "the_emerald_standard_banker";

    private BankerAccess() {
    }

    public static boolean isBanker(Entity entity) {
        return entity instanceof Villager && entity.entityTags().contains(BANKER_TAG);
    }

    public static void markBanker(Villager villager) {
        villager.addTag(BANKER_TAG);
        villager.setCustomName(Component.translatable("entity.the_emerald_standard.banker"));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
    }

    public static boolean open(ServerPlayer player, EconomyService economy) {
        BankTransactionCoordinator.reconcile(player, economy);
        return player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new BankerMenu(containerId, inventory, economy, player),
                        Component.translatable("gui.the_emerald_standard.banker.title")))
                .isPresent();
    }
}
