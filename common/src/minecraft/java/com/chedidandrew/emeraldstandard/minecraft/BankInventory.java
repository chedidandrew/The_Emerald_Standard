package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.InventoryDeliveryAccounting;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side inventory helpers for bank transactions and journal recovery. */
final class BankInventory {
    private BankInventory() {
    }

    static ExchangeResource exchangeResource(String resource) {
        return switch (resource) {
            case "diamond" -> new ExchangeResource(Items.DIAMOND, "diamond", "diamond");
            case "diamond_block" ->
                    new ExchangeResource(Items.DIAMOND_BLOCK, "diamond_block", "diamond_block");
            case "diamond_ore" ->
                    new ExchangeResource(Items.DIAMOND_ORE, "diamond_ore", "diamond_ore");
            case "deepslate_diamond_ore" -> new ExchangeResource(
                    Items.DEEPSLATE_DIAMOND_ORE,
                    "deepslate_diamond_ore",
                    "deepslate_diamond_ore");
            case "gold", "gold_ingot" ->
                    new ExchangeResource(Items.GOLD_INGOT, "gold_ingot", "gold_ingot");
            case "gold_ore" ->
                    new ExchangeResource(Items.GOLD_ORE, "gold_ore", "gold_ore");
            case "deepslate_gold_ore" -> new ExchangeResource(
                    Items.DEEPSLATE_GOLD_ORE,
                    "deepslate_gold_ore",
                    "deepslate_gold_ore");
            case "nether_gold_ore" -> new ExchangeResource(
                    Items.NETHER_GOLD_ORE,
                    "nether_gold_ore",
                    "nether_gold_ore");
            case "raw_gold" ->
                    new ExchangeResource(Items.RAW_GOLD, "raw_gold", "raw_gold");
            case "raw_gold_block" -> new ExchangeResource(
                    Items.RAW_GOLD_BLOCK,
                    "raw_gold_block",
                    "raw_gold_block");
            case "gold_block" ->
                    new ExchangeResource(Items.GOLD_BLOCK, "gold_block", "gold_block");
            case "ancient_debris" -> new ExchangeResource(
                    Items.ANCIENT_DEBRIS,
                    "ancient_debris",
                    "ancient_debris");
            case "netherite_scrap" -> new ExchangeResource(
                    Items.NETHERITE_SCRAP,
                    "netherite_scrap",
                    "netherite_scrap");
            case "netherite", "netherite_ingot" -> new ExchangeResource(
                    Items.NETHERITE_INGOT,
                    "netherite_ingot",
                    "netherite_ingot");
            case "netherite_block" -> new ExchangeResource(
                    Items.NETHERITE_BLOCK,
                    "netherite_block",
                    "netherite_block");
            case "emerald_block" -> new ExchangeResource(
                    Items.EMERALD_BLOCK,
                    "emerald_block",
                    "emerald_block");
            case "emerald_ore" -> new ExchangeResource(
                    Items.EMERALD_ORE,
                    "emerald_ore",
                    "emerald_ore");
            case "deepslate_emerald_ore" -> new ExchangeResource(
                    Items.DEEPSLATE_EMERALD_ORE,
                    "deepslate_emerald_ore",
                    "deepslate_emerald_ore");
            default -> null;
        };
    }

    static List<String> exchangeResourceNames() {
        return List.of(
                "diamond",
                "diamond_block",
                "diamond_ore",
                "deepslate_diamond_ore",
                "gold_ingot",
                "gold_ore",
                "deepslate_gold_ore",
                "nether_gold_ore",
                "raw_gold",
                "raw_gold_block",
                "gold_block",
                "ancient_debris",
                "netherite_scrap",
                "netherite_ingot",
                "netherite_block",
                "emerald_block",
                "emerald_ore",
                "deepslate_emerald_ore");
    }

    static Item itemForJournalKey(String itemKey) {
        if (itemKey == null) {
            return null;
        }
        if (itemKey.equals("emerald")) {
            return Items.EMERALD;
        }
        ExchangeResource resource = exchangeResource(itemKey);
        return resource == null ? null : resource.item();
    }

    static boolean removeItems(ServerPlayer player, Item item, int count) {
        if (count <= 0 || countItems(player, item) < count) {
            return false;
        }
        int remaining = count;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize() && remaining > 0;
                slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == item) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.containerMenu.broadcastChanges();
        return remaining == 0;
    }

    static int insertItems(ServerPlayer player, Item item, int count) {
        int remaining = Math.max(0, count);
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int requested = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(item, requested);
            int countBefore = countItems(player, item);
            player.getInventory().add(stack);
            int countAfter = countItems(player, item);
            int inserted = InventoryDeliveryAccounting.observedInserted(
                    requested, countBefore, countAfter);
            remaining -= inserted;
            if (inserted == 0) {
                break;
            }
        }
        player.containerMenu.broadcastChanges();
        return remaining;
    }

    /** Restores as many items as fit and returns the remainder; recovery never drops entities. */
    static int restoreItems(ServerPlayer player, Item item, int count) {
        return insertItems(player, item, count);
    }

    static int countItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    record ExchangeResource(Item item, String quoteId, String journalKey) {
    }
}
