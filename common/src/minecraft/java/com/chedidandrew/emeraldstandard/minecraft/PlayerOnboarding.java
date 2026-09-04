package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Sends the optional, persistent first-join discovery hint without repeating chat messages. */
public final class PlayerOnboarding {
    public static final String JOIN_HINT_TAG = "the_emerald_standard_join_hint";

    private PlayerOnboarding() {
    }

    public static void onJoin(ServerPlayer player) {
        if (!EmeraldConfig.current().onboardingJoinHintEnabled()
                || player.entityTags().contains(JOIN_HINT_TAG)
                || !player.addTag(JOIN_HINT_TAG)) {
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.the_emerald_standard.join_hint"));
    }
}
