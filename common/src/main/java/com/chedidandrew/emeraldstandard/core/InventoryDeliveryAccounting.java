package com.chedidandrew.emeraldstandard.core;

/**
 * Derives authoritative inventory delivery from observed item counts.
 *
 * <p>Minecraft may consume the temporary {@code ItemStack} passed to an insertion attempt even
 * when no item entered a creative player's full inventory. Transaction code therefore must not
 * infer delivery from that temporary stack's remainder.</p>
 */
public final class InventoryDeliveryAccounting {
    private InventoryDeliveryAccounting() {
    }

    public static int observedInserted(int requested, int countBefore, int countAfter) {
        if (requested <= 0) {
            return 0;
        }
        long observedIncrease = (long) countAfter - countBefore;
        return (int) Math.min(requested, Math.max(0L, observedIncrease));
    }

    public static int undelivered(int requested, int countBefore, int countAfter) {
        return Math.max(0, requested - observedInserted(requested, countBefore, countAfter));
    }
}
