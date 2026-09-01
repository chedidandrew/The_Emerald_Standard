# Inventory transaction recovery

## Why a journal is required

The bank account is stored in `the_emerald_standard.properties`, while Minecraft saves a player's inventory in separate player data. A process can stop after one file is committed but before the other file is written. Without reconciliation, that timing window can duplicate or destroy emeralds and exchanged resources.

Alpha.2 uses a durable journal that survives restart and records enough information to repair either side.

## Deposit and resource exchange

1. Save a `PREPARED` journal with the original inventory count and intended bank credit.
2. Remove the items from the live player inventory.
3. Save the bank credit and move the journal to `BANK_COMMITTED`.
4. Synchronously flush online player data through vanilla's public `saveAll()` path.
5. Clear the journal.

Recovery behavior:

- `PREPARED` with unchanged inventory: clear the intent with no bank credit.
- `PREPARED` with items already saved as removed: restore the missing items, flush player data, and clear the intent.
- `BANK_COMMITTED` with the old inventory restored: remove only the transaction quantity, flush player data, and clear the journal.
- `BANK_COMMITTED` with the new inventory already saved: clear the journal without another item change.

## Withdrawal

1. Debit bank cash and save a `BANK_COMMITTED` withdrawal journal.
2. Insert as many emeralds as fit in the inventory.
3. Refund any undelivered amount to bank cash and reduce the journal to the delivered quantity.
4. Synchronously flush online player data through vanilla's public `saveAll()` path.
5. Clear the journal.

Recovery behavior:

- If the bank debit survived but the delivered emeralds did not, restore only the missing transaction quantity.
- If the inventory already contains the expected delivered quantity, clear the journal without adding more.
- A full inventory leaves undelivered emeralds in bank cash.

## Idempotence and blocking

Reconciliation may safely run on login, logout, `/emerald recover`, or before another bank command. The journal is retained until both sides are confirmed. Normal account mutations are blocked while a pending inventory transaction exists, preventing a second financial action from obscuring recovery.

The recovery comparison is intentionally capped by the original transaction quantity. It never removes or grants more than the journaled amount.

## Why all online players are flushed

Minecraft exposes a public all-player save operation, while the single-player save method is protected. Alpha.2 therefore flushes all online player data before clearing a transaction journal. This is heavier than an ideal per-player write, but it closes the inventory persistence window using supported public APIs and keeps Fabric and NeoForge behavior identical.

A later server-scale persistence layer can replace this flush with player-attached bank data or a loader-specific safe bridge without changing the journal semantics.

## Operational limits

- Maximum inventory-linked transaction: 100,000 items
- Maximum one pending transaction per player
- Unknown journal item identifiers are retained for administrator investigation instead of being silently discarded
- If the player data flush fails, the journal remains active and the next login or bank command retries reconciliation

## Remaining limitation

A persistent player-data storage failure can leave a journal active while the player remains online. The quantity cap and pre-transaction count keep recovery bounded, but large production servers should treat persistent player-save failures as a storage fault and resolve them before allowing continued play. A later server-focused release can add an administrator quarantine or disconnect policy for that condition.
