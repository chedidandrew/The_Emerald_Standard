package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Delivers the optional, persistent first-join handbook and discovery hint. */
public final class PlayerOnboarding {
    public static final String JOIN_HINT_TAG = "the_emerald_standard_join_hint";
    public static final String HANDBOOK_V1_TAG = "the_emerald_standard_handbook_v1";

    private PlayerOnboarding() {
    }

    public static void onJoin(ServerPlayer player) {
        if (!EmeraldConfig.current().onboardingJoinHintEnabled()) {
            return;
        }
        sendDiscoveryHint(player);
        deliverHandbook(player);
    }

    private static void sendDiscoveryHint(ServerPlayer player) {
        if (!player.entityTags().contains(JOIN_HINT_TAG) && player.addTag(JOIN_HINT_TAG)) {
            player.sendSystemMessage(Component.translatable(
                    "message.the_emerald_standard.join_hint"));
        }
    }

    private static void deliverHandbook(ServerPlayer player) {
        if (player.entityTags().contains(HANDBOOK_V1_TAG)) {
            return;
        }

        if (player.getInventory().contains(EmeraldHandbook::isHandbook)) {
            player.addTag(HANDBOOK_V1_TAG);
            return;
        }

        var handbook = EmeraldHandbook.createStack();
        if (!handbook.isEmpty()) {
            player.getInventory().add(handbook);
        }
        if (player.getInventory().contains(EmeraldHandbook::isHandbook)) {
            // Mark only after verified delivery. At the extremely rare 1,024-tag limit,
            // a later join may duplicate the book rather than permanently miss it.
            player.addTag(HANDBOOK_V1_TAG);
            player.sendSystemMessage(Component.translatable(
                    "message.the_emerald_standard.handbook_received"));
            return;
        }

        player.sendSystemMessage(Component.translatable(
                "message.the_emerald_standard.handbook_inventory_full"));
    }
}
