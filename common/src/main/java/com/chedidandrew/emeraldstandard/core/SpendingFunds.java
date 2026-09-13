package com.chedidandrew.emeraldstandard.core;

/** Integer-only bank-first payment planning. Physical emeralds are never fractionally destroyed. */
public final class SpendingFunds {
    private SpendingFunds() {}

    public static long availableMicro(long cashMicro, int inventoryEmeralds) {
        long cash = Math.max(0L, cashMicro);
        long inventory = Math.max(0, inventoryEmeralds) * EconomyState.MICRO;
        return inventory > Long.MAX_VALUE - cash ? Long.MAX_VALUE : cash + inventory;
    }

    public static Payment plan(long cashMicro, int inventoryEmeralds, int wholeEmeralds) {
        if (cashMicro < 0L || inventoryEmeralds < 0 || wholeEmeralds <= 0
                || wholeEmeralds > EconomyService.MAX_WHOLE_EMERALD_TRANSACTION) return null;
        long cost = wholeEmeralds * EconomyState.MICRO;
        long shortage = Math.max(0L, cost - cashMicro);
        int fromInventory = shortage == 0L ? 0 : (int) ((shortage - 1L) / EconomyState.MICRO + 1L);
        if (fromInventory > inventoryEmeralds
                || fromInventory > EconomyService.MAX_INVENTORY_ITEM_TRANSACTION) return null;
        long topUp = fromInventory * EconomyState.MICRO;
        if (topUp > Long.MAX_VALUE - cashMicro) return null;
        return new Payment(wholeEmeralds, fromInventory, cashMicro + topUp - cost);
    }

    public record Payment(int amount, int inventoryEmeralds, long bankCashAfterMicro) {}
}
