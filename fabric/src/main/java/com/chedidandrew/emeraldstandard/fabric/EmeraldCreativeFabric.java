package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldCreativeContent;
import net.fabricmc.fabric.api.creativetab.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;

final class EmeraldCreativeFabric {
    static void register() {
        Registry.register(BuiltInRegistries.ITEM,com.chedidandrew.emeraldstandard.minecraft.NewspaperItem.ID,
                new com.chedidandrew.emeraldstandard.minecraft.NewspaperItem());
        var banker=Registry.register(BuiltInRegistries.ITEM,EmeraldCreativeContent.BANKER_EGG_ID,EmeraldCreativeContent.createBankerEgg());
        var builder=Registry.register(BuiltInRegistries.ITEM,EmeraldCreativeContent.BUILDER_EGG_ID,EmeraldCreativeContent.createBuilderEgg());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,EmeraldCreativeContent.TAB_ID,
                EmeraldCreativeContent.createTab(FabricCreativeModeTab.builder()));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
            output.accept(banker); output.accept(builder);
        });
    }
}
