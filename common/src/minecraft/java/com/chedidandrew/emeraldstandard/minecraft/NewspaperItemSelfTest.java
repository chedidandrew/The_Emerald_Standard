package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

/** Actual native use state and exact-source lifecycle, without altering item save data. */
public final class NewspaperItemSelfTest {
    public static void verify(ServerLevel level) {
        var item=BuiltInRegistries.ITEM.getValue(NewspaperItem.ID);
        require(item instanceof NewspaperItem,"registered newspaper");
        var p=new ServerPlayer(level.getServer(),level,
                new GameProfile(UUID.randomUUID(),"PaperReader"),ClientInformation.createDefault());
        for(var hand:InteractionHand.values()) {
            var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_NAME,Component.literal("My edition"));
            var original=stack.copy();var other=stack.copy();
            var opposite=hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
            p.setItemInHand(hand,stack);p.setItemInHand(opposite,other);
            var menu=new NewspaperMenu(43,p.getInventory(),null,false,hand);p.containerMenu=menu;
            require(menu.readingHand()==hand,"source hand preserved");
            menu.beginReading(p);
            require(p.isUsingItem()&&p.getUsedItemHand()==hand&&p.getUseItem()==stack,"unfold exact held source");
            require(!menu.ownsReadingUse(p,other),"identical second newspaper stays rolled");
            for(int i=0;i<20;i++) {((NewspaperItem)item).onUseTick(level,p,stack,Integer.MAX_VALUE-i);menu.broadcastChanges();}
            require(p.isUsingItem(),"reading survives released mouse while menu is open");
            require(ItemStack.matches(stack,original),"reading changed count or item components");
            menu.removed(p);menu.removed(p);
            require(!p.isUsingItem()&&menu.readingHand()==null&&ItemStack.matches(stack,original),"close rolls without mutation");

            var droppedMenu=new NewspaperMenu(44,p.getInventory(),null,false,hand);p.containerMenu=droppedMenu;
            droppedMenu.beginReading(p);p.setItemInHand(hand,ItemStack.EMPTY);droppedMenu.broadcastChanges();
            require(!p.isUsingItem()&&droppedMenu.readingHand()==null,"removed source clears open state");
            require(ItemStack.matches(stack,original),"dropped source changed");

            p.setItemInHand(hand,stack);
            var stale=new NewspaperMenu(45,p.getInventory(),null,false,hand);p.containerMenu=stale;stale.beginReading(p);
            p.containerMenu=p.inventoryMenu;
            ((NewspaperItem)item).onUseTick(level,p,stack,500);
            require(!p.isUsingItem(),"stale menu cannot keep newspaper open");stale.removed(p);
            var browser=new NewspaperMenu(46,p.getInventory(),null,true,hand);p.containerMenu=browser;browser.beginReading(p);
            require(!p.isUsingItem()&&browser.readingHand()==null,"desk browser cannot unfold inventory paper");
            browser.removed(p);
            var replacement=new NewspaperMenu(47,p.getInventory(),null,false,hand);p.containerMenu=replacement;
            replacement.beginReading(p);p.setItemInHand(hand,stack.copy());replacement.broadcastChanges();
            require(!p.isUsingItem(),"different equal-data copy cannot inherit reading state");
            replacement.removed(p);p.containerMenu=p.inventoryMenu;
        }
        require(item.getUseDuration(new ItemStack(item),p)>72_000,"long reading does not expire");
        System.out.println("PASS newspaper item: both hands, exact copy, close, stale menu, dropped/replaced source, unchanged data and browser isolation");
    }
    private static void require(boolean result,String message) {if(!result)throw new IllegalStateException(message);}
}
