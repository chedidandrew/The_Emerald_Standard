# Inventory-aware spending

The Exchange Desk combines Bank Cash with ordinary loose emeralds in the main inventory,
hotbar and offhand. Market purchases, Savings deposits, CDs, villager lending and all
Village Fund gift types use cash first and take only the shortfall from inventory.
Depositing is optional and still frees inventory space. Withdrawals and sale/maturity
proceeds remain Bank Cash operations. Savings never gets spent automatically.

Only whole emerald spending amounts are accepted. If Bank Cash contains a fraction, a
whole inventory emerald covers the shortfall and the change stays in Bank Cash. Named
or component-customized items, the mouse cursor, nested containers and emerald blocks
are not automatically consumed. Trade still explicitly exchanges supported blocks/ores.
Inventory items are not added to account Net Worth or its performance history.

Typed amounts are exact and rejected when no longer affordable. Presets retain their
existing up-to-available behavior. Fund drafts do not silently shrink after confirmation.
Server-side eligibility, distance/current-menu validation and cooldown checks remain
authoritative; client previews do not grant money.

## Recovery and limits

Inventory top-ups settle before the requested bank operation. If the latter fails or
the server stops between these steps, the completed top-up remains in Bank Cash.
It is not automatically spent on restart or blindly refunded into inventory.

New transfers save a transaction-ID/applied-delta receipt in the same player NBT file
as inventory, and compare both inventory and tags during checkpoint readback. Credits
are committed only after the removal checkpoint. Withdrawals checkpoint actual delivery
before refunding undelivered items. Creative-mode insertion is measured by observed
inventory changes, not the insertion method's claimed success. Recovery uses receipts,
not current item totals, and clearing a journal is itself durable.

An unresolved transfer fails closed and disconnects the player to prevent death or
inventory changes while recovery is pending. Reconnect after a transient save failure.
Persistent disk problems need the server owner. A missing/contradictory committed
receipt, or an old pending journal without receipts, is retained for manual review:
guessing from item counts could duplicate or remove legitimate items.

Back up the world before upgrading; finish pending transfers in the previous version
first. Do not downgrade with pending transactions. Restoring only player data or only
economy data, editing receipts with operator tools, hostile mods or storage corruption
are outside ordinary GUI transaction guarantees. Restore consistent whole-world backups.

Checks include integer conservation, fractional balances, overflow, persistence failure,
inventory-only purchases across five routes, full/partial creative delivery, custom
items, offhand items, menu replay, NBT reload, recovery replay and client GUI fixtures.
