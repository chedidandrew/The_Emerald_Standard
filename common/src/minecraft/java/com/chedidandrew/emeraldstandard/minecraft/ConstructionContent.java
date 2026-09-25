package com.chedidandrew.emeraldstandard.minecraft;

import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/** Loader-neutral factories; holders are assigned during the loaders' normal registration phase. */
public final class ConstructionContent {
    public static final Identifier FENCE_ID = Identifier.fromNamespaceAndPath("the_emerald_standard", "construction_fence");
    public static final Identifier BUILDER_ID = Identifier.fromNamespaceAndPath("the_emerald_standard", "builder");
    public static ConstructionFenceBlock fence;
    public static BlockEntityType<ConstructionFenceBlock.Receipt> receipt;
    public static EntityType<ConstructionBuilder> builder;
    private ConstructionContent() { }
    public static ConstructionFenceBlock createFence() {
        return fence = new ConstructionFenceBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, FENCE_ID)).strength(.8F)
                .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK));
    }
    public static BlockEntityType<ConstructionFenceBlock.Receipt> createReceipt() {
        return receipt = new BlockEntityType<>(ConstructionFenceBlock.Receipt::new, Set.of(fence));
    }
    public static Item.Properties fenceItemProperties() {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, FENCE_ID)).useBlockDescriptionPrefix();
    }
    public static EntityType<ConstructionBuilder> createBuilder() {
        return builder = EntityType.Builder.of(ConstructionBuilder::new, MobCategory.CREATURE)
                .sized(.6F, 1.95F).clientTrackingRange(10).updateInterval(3).noLootTable()
                .build(ResourceKey.create(Registries.ENTITY_TYPE, BUILDER_ID));
    }
}
