package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.minecraft.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class EmeraldCreativeNeoForge {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,BankerProfessionSupport.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,BankerProfessionSupport.MOD_ID);
    static void register(IEventBus bus) {
        ITEMS.register("newspaper",NewspaperItem::new);
        var banker=ITEMS.register("banker_spawn_egg",EmeraldCreativeContent::createBankerEgg);
        var builder=ITEMS.register("builder_spawn_egg",EmeraldCreativeContent::createBuilderEgg);
        TABS.register("creative",() -> EmeraldCreativeContent.createTab(CreativeModeTab.builder()));
        ITEMS.register(bus); TABS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
                event.accept(banker.get()); event.accept(builder.get());
            }
        });
    }
}
