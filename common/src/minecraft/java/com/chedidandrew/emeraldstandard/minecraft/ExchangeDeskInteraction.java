package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;

/** Run before desk access checks; vanilla retains placement permissions and stack consumption. */
public final class ExchangeDeskInteraction {
    public static boolean placingBlock(Player player) {
        return player.isSecondaryUseActive() && (player.getMainHandItem().getItem() instanceof BlockItem
                || player.getOffhandItem().getItem() instanceof BlockItem);
    }
    private ExchangeDeskInteraction() {}
}
