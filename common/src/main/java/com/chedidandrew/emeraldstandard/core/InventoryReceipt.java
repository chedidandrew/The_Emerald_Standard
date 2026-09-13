package com.chedidandrew.emeraldstandard.core;

import java.util.Set;
import java.util.UUID;

/** A bounded vanilla player tag, saved in the same NBT checkpoint as the inventory it describes. */
public final class InventoryReceipt {
    public static final String PREFIX = "tes_bank_receipt:";
    private InventoryReceipt() {}

    public static String encode(UUID transactionId, int appliedDelta) {
        return PREFIX + transactionId + ":" + appliedDelta;
    }

    /** No matching receipt means this transaction has not modified the saved inventory. */
    public static int applied(Set<String> tags, UUID transactionId) {
        String prefix = PREFIX + transactionId + ":";
        Integer result = null;
        for (String tag : tags) {
            if (!tag.startsWith(prefix)) continue;
            if (result != null) throw new IllegalStateException("Conflicting inventory receipts");
            result = Integer.valueOf(tag.substring(prefix.length()));
        }
        return result == null ? 0 : result;
    }

    public static int correction(EconomyState.PendingInventoryTransaction transaction, int applied) {
        int count = transaction.itemCount;
        if (!transaction.receiptTracked) {
            throw new IllegalStateException("Legacy journal has no reliable inventory receipt; retained for manual recovery");
        }
        if (transaction.kind == EconomyState.InventoryTransactionKind.WITHDRAWAL) {
            if (applied < 0 || applied > count) throw new IllegalStateException("Invalid withdrawal receipt");
            return count - applied; // Refund undelivered items to cash, never deliver twice.
        }
        if (applied > 0 || applied < -count) throw new IllegalStateException("Invalid deposit receipt");
        if (transaction.stage == EconomyState.InventoryTransactionStage.PREPARED) return -applied;
        // Credit is committed only AFTER the complete removal receipt reaches disk.
        if (applied != -count) throw new IllegalStateException("Committed credit lacks its removal checkpoint; retained");
        return 0;
    }
}
