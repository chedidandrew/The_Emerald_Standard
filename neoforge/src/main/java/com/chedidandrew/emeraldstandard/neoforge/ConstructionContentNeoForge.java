package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.minecraft.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

final class ConstructionContentNeoForge {
    private static final String MOD = "the_emerald_standard";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD);
    private static final DeferredRegister<BlockEntityType<?>> RECEIPTS = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD);
    static void register(IEventBus bus) {
        var fence = BLOCKS.register("construction_fence", ConstructionContent::createFence);
        var item = ITEMS.register("construction_fence", () -> new BlockItem(fence.get(), ConstructionContent.fenceItemProperties()));
        RECEIPTS.register("construction_fence", ConstructionContent::createReceipt);
        ENTITIES.register("builder", ConstructionContent::createBuilder);
        BLOCKS.register(bus); ITEMS.register(bus); RECEIPTS.register(bus); ENTITIES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(ConstructionContent.builder, ConstructionBuilder.attributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(item.get());
        });
    }
}
