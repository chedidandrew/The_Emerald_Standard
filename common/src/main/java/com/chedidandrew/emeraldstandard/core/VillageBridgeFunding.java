package com.chedidandrew.emeraldstandard.core;

/** Size-scaled Infrastructure inputs. No withdrawals from players or protected Fund principal. */
public final class VillageBridgeFunding {
    public static final int MAX_RECEIPTS = 512;
    private VillageBridgeFunding() {}
    public record Cost(double materials, double treasury) {}
    public static Cost cost(int waterLength, int operations) {
        if (waterLength < 2 || waterLength > 64 || operations < 1 || operations > 8192)
            throw new IllegalArgumentException("Invalid bridge size");
        return new Cost(Math.ceil(operations / 16.0), 4 + Math.ceil(waterLength / 4.0));
    }
    public static boolean pay(EconomyState.VillageRecord village, String receipt,
            int length, int operations, boolean forced) {
        java.util.UUID.fromString(receipt);
        if (village.bridgeFundingReceipts.contains(receipt)) return true;
        if (village.bridgeFundingReceipts.size() >= MAX_RECEIPTS) return false;
        Cost cost = cost(length, operations);
        if (!forced && (village.materialSupply < cost.materials || village.treasury < cost.treasury)) return false;
        if (!forced) {
            village.materialSupply -= cost.materials;
            village.treasury -= cost.treasury;
        }
        village.bridgeFundingReceipts.add(receipt);
        return true;
    }
}
