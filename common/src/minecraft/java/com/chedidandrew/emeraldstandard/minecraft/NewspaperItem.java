package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Reusable news access, with no money, inventory transfer or banking authority. */
public final class NewspaperItem extends Item {
    public static final Identifier ID=Identifier.fromNamespaceAndPath("the_emerald_standard","newspaper");
    public NewspaperItem() { super(new Properties().setId(ResourceKey.create(Registries.ITEM,ID)).stacksTo(1)); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if(player instanceof ServerPlayer serverPlayer) NewsRuntime.openItem(serverPlayer,hand);
        return InteractionResult.SUCCESS;
    }
    @Override public int getUseDuration(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return Integer.MAX_VALUE;
    }
    @Override public void onUseTick(Level level, net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.world.item.ItemStack stack, int remaining) {
        // Menu-owned use uses vanilla synchronized hand flags, not persistent item components.
        if(!level.isClientSide() && (!(entity instanceof Player p)
                || !(p.containerMenu instanceof NewspaperMenu menu) || !menu.ownsReadingUse(p,stack)))
            entity.stopUsingItem();
    }
}
