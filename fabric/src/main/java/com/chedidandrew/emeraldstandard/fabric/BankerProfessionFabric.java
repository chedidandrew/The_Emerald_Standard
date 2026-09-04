package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.minecraft.BankerProfessionSupport;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Fabric registration for the Exchange Desk POI and Banker profession. */
public final class BankerProfessionFabric {
    private static final Block EXCHANGE_DESK =
            BankerProfessionSupport.createExchangeDeskBlock();
    private static boolean registered;

    private BankerProfessionFabric() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        Registry.register(
                BuiltInRegistries.BLOCK,
                BankerProfessionSupport.EXCHANGE_DESK_ID,
                EXCHANGE_DESK);
        Registry.register(
                BuiltInRegistries.ITEM,
                BankerProfessionSupport.EXCHANGE_DESK_ID,
                new BlockItem(
                        EXCHANGE_DESK,
                        BankerProfessionSupport.createExchangeDeskItemProperties()));
        PoiHelper.register(
                BankerProfessionSupport.BANKER_POI_ID,
                1,
                1,
                EXCHANGE_DESK);
        Registry.register(
                BuiltInRegistries.VILLAGER_PROFESSION,
                BankerProfessionSupport.BANKER_ID,
                BankerProfessionSupport.createBankerProfession());
        registered = true;
    }
}
