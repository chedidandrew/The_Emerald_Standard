package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.minecraft.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

final class ConstructionContentFabric {
    static void register() {
        Registry.register(BuiltInRegistries.BLOCK, ConstructionContent.FENCE_ID, ConstructionContent.createFence());
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ConstructionContent.FENCE_ID, ConstructionContent.createReceipt());
        var item = Registry.register(BuiltInRegistries.ITEM, ConstructionContent.FENCE_ID,
                new BlockItem(ConstructionContent.fence, ConstructionContent.fenceItemProperties()));
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ConstructionContent.BUILDER_ID, ConstructionContent.createBuilder());
        FabricDefaultAttributeRegistry.register(ConstructionContent.builder, ConstructionBuilder.attributes());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(item));
    }
}
