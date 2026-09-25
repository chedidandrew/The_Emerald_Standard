package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Spawner;

/** Banker is a villager profession, not a separate entity type a vanilla spawner can retain. */
final class BankerSpawnEggItem extends SpawnEggItem {
    BankerSpawnEggItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof Spawner) {
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                player.sendSystemMessage(Component.translatable("message.the_emerald_standard.banker_egg_spawner"));
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
}
