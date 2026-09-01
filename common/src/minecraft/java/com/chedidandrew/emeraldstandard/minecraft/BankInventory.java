package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side inventory helpers for bank transactions. */
final class BankInventory {
    private BankInventory() {
    }

    static ExchangeResource exchangeResource(String resource) {
        return switch (resource) {
            case "diamond" -> new ExchangeResource(Items.DIAMOND, "diamond");
            case "diamond_block" -> new ExchangeResource(Items.DIAMOND_BLOCK, "diamond_block");
            case "diamond_ore" -> new ExchangeResource(Items.DIAMOND_ORE, "diamond_ore");
            case "deepslate_diamond_ore" ->
                    new ExchangeResource(Items.DEEPSLATE_DIAMOND_ORE, "deepslate_diamond_ore");
            case "gold", "gold_ingot" -> new ExchangeResource(Items.GOLD_INGOT, "gold_ingot");
            case "gold_ore" -> new ExchangeResource(Items.GOLD_ORE, "gold_ore");
            case "deepslate_gold_ore" ->
                    new ExchangeResource(Items.DEEPSLATE_GOLD_ORE, "deepslate_gold_ore");
            case "nether_gold_ore" ->
                    new ExchangeResource(Items.NETHER_GOLD_ORE, "nether_gold_ore");
            case "raw_gold" -> new ExchangeResource(Items.RAW_GOLD, "raw_gold");
            case "raw_gold_block" ->
                    new ExchangeResource(Items.RAW_GOLD_BLOCK, "raw_gold_block");
            case "gold_block" -> new ExchangeResource(Items.GOLD_BLOCK, "gold_block");
            case "ancient_debris" -> new ExchangeResource(Items.ANCIENT_DEBRIS, "ancient_debris");
            case "netherite_scrap" ->
                    new ExchangeResource(Items.NETHERITE_SCRAP, "netherite_scrap");
            case "netherite", "netherite_ingot" ->
                    new ExchangeResource(Items.NETHERITE_INGOT, "netherite_ingot");
            case "netherite_block" ->
                    new ExchangeResource(Items.NETHERITE_BLOCK, "netherite_block");
            case "emerald_block" ->
                    new ExchangeResource(Items.EMERALD_BLOCK, "emerald_block");
            case "emerald_ore" -> new ExchangeResource(Items.EMERALD_ORE, "emerald_ore");
            case "deepslate_emerald_ore" ->
                    new ExchangeResource(Items.DEEPSLATE_EMERALD_ORE, "deepslate_emerald_ore");
            default -> null;
        };
    }

    static boolean removeItems(ServerPlayer player, Item item, int count) {
        if (countItems(player, item) < count) {
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
        int remaining = count;
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int requested = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(item, requested);
            player.getInventory().add(stack);
            int inserted = requested - stack.getCount();
            remaining -= inserted;
            if (inserted == 0) {
                break;
            }
        }
        player.containerMenu.broadcastChanges();
        return remaining;
    }

    static void giveOrDrop(ServerPlayer player, Item item, int count) {
        int remaining = insertItems(player, item, count);
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int dropped = Math.min(maxStack, remaining);
            player.drop(new ItemStack(item, dropped), false);
            remaining -= dropped;
        }
        player.containerMenu.broadcastChanges();
    }

    private static int countItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    record ExchangeResource(Item item, String quoteId) {
    }
}
