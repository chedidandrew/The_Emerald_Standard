package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.minecraft.BankerProfessionSupport;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge registration for the Exchange Desk POI and Banker profession. */
public final class BankerProfessionNeoForge {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, BankerProfessionSupport.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, BankerProfessionSupport.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE,
                    BankerProfessionSupport.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION,
                    BankerProfessionSupport.MOD_ID);

    public static final DeferredHolder<Block, Block> EXCHANGE_DESK = BLOCKS.register(
            "exchange_desk", BankerProfessionSupport::createExchangeDeskBlock);
    public static final DeferredHolder<Item, Item> EXCHANGE_DESK_ITEM = ITEMS.register(
            "exchange_desk", () -> new BlockItem(
                    EXCHANGE_DESK.get(),
                    BankerProfessionSupport.createExchangeDeskItemProperties()));
    public static final DeferredHolder<PoiType, PoiType> BANKER_POI = POI_TYPES.register(
            "banker_poi", () -> BankerProfessionSupport.createBankerPoi(EXCHANGE_DESK.get()));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BANKER =
            PROFESSIONS.register("banker", BankerProfessionSupport::createBankerProfession);

    private BankerProfessionNeoForge() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
        modEventBus.addListener(BankerProfessionNeoForge::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(EXCHANGE_DESK_ITEM.get());
        }
    }
}
