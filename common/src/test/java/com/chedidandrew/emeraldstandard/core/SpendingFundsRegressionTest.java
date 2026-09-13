package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

public final class SpendingFundsRegressionTest {
    public static void main(String[] args) throws Exception {
        long unit = EconomyState.MICRO;
        require(SpendingFunds.plan(0, 320, 100).inventoryEmeralds() == 100, "inventory-only gift");
        require(SpendingFunds.plan(120 * unit, 320, 100).inventoryEmeralds() == 0, "bank first");
        require(SpendingFunds.plan(40 * unit, 320, 100).inventoryEmeralds() == 60, "mixed gift");
        var fraction = SpendingFunds.plan(unit / 4, 1, 1);
        require(fraction.inventoryEmeralds() == 1 && fraction.bankCashAfterMicro() == unit / 4,
                "fractional change conserved");
        require(SpendingFunds.plan(0, 99, 100) == null, "no partial exact amount");
        require(SpendingFunds.plan(0, 320, 0) == null && SpendingFunds.plan(0, 320, -1) == null,
                "reject zero and negative");
        require(SpendingFunds.plan(Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE) == null,
                "reject oversized amount");
        require(SpendingFunds.availableMicro(Long.MAX_VALUE, Integer.MAX_VALUE) == Long.MAX_VALUE,
                "available overflow saturates");
        for (long cash = 0; cash < 110 * unit; cash += unit / 7) {
            for (int items = 0; items <= 120; items += 3) {
                var payment = SpendingFunds.plan(cash, items, 100);
                if (payment == null) {
                    require(cash + items * unit < 100 * unit, "only reject insufficient funds");
                } else {
                    require(cash + items * unit == payment.bankCashAfterMicro()
                                    + (items - payment.inventoryEmeralds()) * unit + 100 * unit,
                            "conservation across fractional mixed payments");
                }
            }
        }
        UUID player = UUID.randomUUID();
        var root = Files.createTempDirectory("tes-receipt-regression-");
        EconomyService service = new EconomyService();
        service.startWithSeed(root, 81L, 0L, 0L);
        var transaction = service.prepareInventoryCredit(player, EconomyState.InventoryTransactionKind.DEPOSIT,
                "emerald", 8, 20, 8 * unit, true);
        require(transaction != null && transaction.receiptTracked && transaction.copy().receiptTracked, "receipt copy");
        var reloaded = new EconomyService();
        reloaded.startWithSeed(root, 81L, 0L, 0L);
        transaction = reloaded.pendingInventoryTransaction(player);
        require(transaction.receiptTracked, "receipt protocol survives restart");
        require(InventoryReceipt.correction(transaction, 0) == 0, "crash before item removal");
        require(InventoryReceipt.correction(transaction, -8) == 8, "prepared removal restores exactly once");
        require(InventoryReceipt.correction(transaction, -3) == 3, "partial rollback receipt");
        var tags = Set.of("unrelated", InventoryReceipt.encode(transaction.transactionId, -8));
        require(InventoryReceipt.applied(tags, transaction.transactionId) == -8, "receipt decodes");
        require(InventoryReceipt.applied(tags, UUID.randomUUID()) == 0, "other transaction is not this one");
        require(reloaded.commitPreparedInventoryCredit(player, transaction.transactionId), "commit");
        transaction = reloaded.pendingInventoryTransaction(player);
        require(InventoryReceipt.correction(transaction, -8) == 0, "committed removal never repeats");
        var committed = transaction;
        rejects(() -> InventoryReceipt.correction(committed, 0), "missing committed receipt fails closed");
        require(!reloaded.commitPreparedInventoryCredit(player, transaction.transactionId), "duplicate credit rejected");
        require(reloaded.completeInventoryTransaction(player, transaction.transactionId), "clear once");
        require(!reloaded.completeInventoryTransaction(player, transaction.transactionId), "duplicate clear rejected");
        var withdrawal = reloaded.beginInventoryWithdrawal(player, 8, 0, true);
        require(InventoryReceipt.correction(withdrawal, 0) == 8, "undelivered withdrawal refunds cash");
        require(InventoryReceipt.correction(withdrawal, 3) == 5, "partial delivery refunds remainder");
        require(InventoryReceipt.correction(withdrawal, 8) == 0, "delivered withdrawal never redelivers");
        require(reloaded.reducePendingWithdrawal(player, withdrawal.transactionId, 5), "refund");
        withdrawal = reloaded.pendingInventoryTransaction(player);
        require(InventoryReceipt.correction(withdrawal, 3) == 0, "refunded journal does not refund twice");
        require(reloaded.snapshot().account(player).cashMicro == 5 * unit, "cash preserved");
        var legacy = withdrawal.copy(); legacy.receiptTracked = false;
        rejects(() -> InventoryReceipt.correction(legacy, 0), "legacy ambiguity retained not guessed");
        require(reloaded.completeInventoryTransaction(player, withdrawal.transactionId), "settle partial withdrawal");
        // Force a bank-save failure without touching any real world. The already settled top-up
        // must remain cash, and no investment may appear after a failed second-stage mutation.
        var pathField = EconomyService.class.getDeclaredField("path");
        pathField.setAccessible(true);
        Object originalPath = pathField.get(reloaded);
        var blockedPath = Files.createDirectory(root.resolve("blocked-save-target"));
        pathField.set(reloaded, blockedPath);
        long cashBeforeFailure = reloaded.snapshot().account(player).cashMicro;
        require(!reloaded.buy(player, "VILX", 2), "failed persistence rejects investment");
        require(reloaded.snapshot().account(player).cashMicro == cashBeforeFailure
                        && reloaded.snapshot().account(player).shares.getOrDefault("VILX", 0.0) == 0,
                "failed second stage neither deletes cash nor creates shares");
        pathField.set(reloaded, originalPath);
        System.out.println("PASS combined funds conservation, fractional change, overflow, receipts, restart, replay and save failure");
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
    private static void require(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
}
