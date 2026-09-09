package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.minecraft.BankerProfessionSupport;
import com.chedidandrew.emeraldstandard.minecraft.EmeraldHandbook;
import com.chedidandrew.emeraldstandard.minecraft.HandbookReaderItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Fabric registration for the Exchange Desk POI and Banker profession. */
public final class BankerProfessionFabric {
    private static final Block EXCHANGE_DESK = BankerProfessionSupport.createExchangeDeskBlock();
    private static final Item EXCHANGE_DESK_ITEM = new BlockItem(
            EXCHANGE_DESK, BankerProfessionSupport.createExchangeDeskItemProperties());
    private static final Item HANDBOOK = new HandbookReaderItem();
    private static boolean registered;
    private BankerProfessionFabric() { }
    public static synchronized void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.BLOCK, BankerProfessionSupport.EXCHANGE_DESK_ID, EXCHANGE_DESK);
        Registry.register(BuiltInRegistries.ITEM, BankerProfessionSupport.EXCHANGE_DESK_ID, EXCHANGE_DESK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, EmeraldHandbook.HANDBOOK_ID, HANDBOOK);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(EXCHANGE_DESK_ITEM));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(HANDBOOK));
        PoiHelper.register(BankerProfessionSupport.BANKER_POI_ID, 1, 1, EXCHANGE_DESK);
        Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, BankerProfessionSupport.BANKER_ID,
                BankerProfessionSupport.createBankerProfession());
        registered = true;
    }
}
