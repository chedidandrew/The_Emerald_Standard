package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;

/** Shared creative catalog. Registry discovery automatically includes future mod items. */
public final class EmeraldCreativeContent {
    public static final Identifier TAB_ID = id("creative");
    public static final Identifier BANKER_EGG_ID = id("banker_spawn_egg");
    public static final Identifier BUILDER_EGG_ID = id("builder_spawn_egg");
    private static final List<String> FIRST = List.of("handbook", "exchange_desk", "construction_fence",
            "banker_spawn_egg", "builder_spawn_egg");
    private EmeraldCreativeContent() {}
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BankerProfessionSupport.MOD_ID, path);
    }
    private static Item.Properties eggProperties(Identifier id, String help) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id))
                .component(DataComponents.LORE,new ItemLore(List.of(Component.translatable(help))));
    }
    public static Item createBuilderEgg() {
        return new SpawnEggItem(eggProperties(BUILDER_EGG_ID,"item.the_emerald_standard.builder_spawn_egg.help")
                .spawnEgg(ConstructionContent.builder));
    }
    public static Item createBankerEgg() {
        CompoundTag data = new CompoundTag(), profession = new CompoundTag();
        profession.putString("type","minecraft:plains");
        profession.putString("profession",BankerProfessionSupport.BANKER_ID.toString());
        profession.putInt("level",1);
        data.put("VillagerData",profession);
        data.putInt("Xp",1); // Prevent the vanilla reset-profession behavior when no job site exists yet.
        data.putBoolean("VillagerDataFinalized",true);
        data.putBoolean("PersistenceRequired",true);
        // No managed Banker tag, region key, resident identity or financial authority is copied.
        return new BankerSpawnEggItem(eggProperties(BANKER_EGG_ID,"item.the_emerald_standard.banker_spawn_egg.help")
                .spawnEgg(EntityTypes.VILLAGER)
                .component(DataComponents.ENTITY_DATA,TypedEntityData.of(EntityTypes.VILLAGER,data)));
    }
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(BankerProfessionSupport.MOD_ID)) items.add(item);
        });
        items.sort(Comparator.<Item>comparingInt(item -> {
            int index=FIRST.indexOf(BuiltInRegistries.ITEM.getKey(item).getPath());
            return index<0 ? FIRST.size() : index;
        }).thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return List.copyOf(items);
    }
    public static CreativeModeTab createTab(CreativeModeTab.Builder builder) {
        return builder.title(Component.translatable("itemGroup.the_emerald_standard"))
                .icon(() -> new ItemStack(BankerProfessionSupport.exchangeDeskOrLectern()))
                .displayItems((parameters, output) -> items().forEach(output::accept)).build();
    }
}
