# Inventory transaction recovery

## Why a journal is required

The bank account is stored in `the_emerald_standard.properties`, while Minecraft saves a player's inventory in separate player data. A process can stop after one file is committed but before the other file is written. Without reconciliation, that timing window can duplicate or destroy emeralds and exchanged resources.

The mod uses a durable journal that survives restart and records enough information to repair either side.

## Deposit and resource exchange

1. Save a `PREPARED` journal with the original inventory count and intended bank credit.
2. Remove the items from the live player inventory.
3. Save the bank credit and move the journal to `BANK_COMMITTED`.
4. Serialize the affected player through Minecraft's normal root `saveWithoutId` NBT path, write
   only that player's `.dat`, require the checked safe replacement to succeed, and read back an
   identical `Inventory` payload.
5. Synchronously persist removal of the `BANK_COMMITTED` journal before returning success and
   allowing later inventory changes.

Recovery behavior:

- `PREPARED` with unchanged inventory: clear the intent with no bank credit.
- `PREPARED` with items already saved as removed: restore the missing items, flush player data, and clear the intent.
- `BANK_COMMITTED` with the old inventory restored: remove only the transaction quantity, flush player data, and clear the journal.
- `BANK_COMMITTED` with the new inventory already saved: clear the journal without another item change.

## Withdrawal

1. Debit bank cash and save a `BANK_COMMITTED` withdrawal journal.
2. Insert as many emeralds as fit in the inventory.
3. Refund any undelivered amount to bank cash and reduce the journal to the delivered quantity.
4. Perform the same checked, target-player-only save and persisted-inventory comparison.
5. Synchronously persist removal of the committed journal before returning success.

Recovery behavior:

- If the bank debit survived but the delivered emeralds did not, restore only the missing transaction quantity.
- If the inventory already contains the expected delivered quantity, clear the journal without adding more.
- A full inventory leaves undelivered emeralds in bank cash.

If any rollback or committed recovery cannot fit all protected items, the mod inserts only what fits, saves that partial restoration, and retains the journal for the exact remainder. It never drops recovery items into the world where despawning, lava, other players, or chunk unloading could destroy the guarantee. Free inventory space and interact again or run `/emerald recover` to continue.

## Idempotence and blocking

Reconciliation may safely run on login, logout, `/emerald recover`, or before another bank command. The journal is retained until both sides are confirmed. Normal account mutations are blocked while a pending inventory transaction exists, preventing a second financial action from obscuring recovery.

GUI financial actions also use a short configurable game-tick cooldown to absorb duplicate button packets and click spam. This is independent from the journal and does not change balances by itself.

The recovery comparison is intentionally capped by the original transaction quantity. It never removes or grants more than the journaled amount.

## Why the transaction checkpoint writes one player directly

Minecraft's public all-player save is both unnecessarily broad and unable to report a failed
player-data write: the 26.2 player storage method logs and suppresses write exceptions. The
transaction checkpoint therefore mirrors that version's player-data algorithm with public APIs:
`saveWithoutId` supplies the same root NBT, including the current `DataVersion` and loader-added
entity attachments; `NbtIo.writeCompressed` synchronizes the temporary file; and
`Util.safeReplaceOrMoveFile` reports whether the `.dat`/`.dat_old` rotation succeeded. Reading the
new file back and comparing its complete `Inventory` tag is the final condition for clearing the
journal. The replaced target file is forced again, and the containing directory is forced where
the filesystem provider supports directory channels. Journal removal is then committed to the
economy file before the operation succeeds. If the server stops in the narrow interval between the
verified player save and journal cleanup, gameplay has not resumed and login recovery can safely
reconcile the still-current expected count.

This extra durability checkpoint intentionally saves player NBT only. Statistics, advancements,
and loader-level notifications remain owned by Minecraft's normal autosave/logout lifecycle; they
are not part of the bank's cross-file inventory invariant. The implementation and its live
integration check—including a real temporary `.dat`/`.dat_old` rotation and readback—is pinned to
Minecraft 26.2 and must be reviewed if that root serialization path changes. Filesystems that do
not support opening a directory channel retain the synchronized file writes and checked replacement
but cannot receive the additional best-effort directory force.

## Whole-state replacement saves

An economy mutation still validates, serializes, synchronizes, and atomically replaces the complete
economy file before it is acknowledged. After a successful load or write, the in-memory state keeps
a SHA-256 fingerprint of that exact primary generation. A later replacement hashes the old primary
and skips a redundant full deserialize/validation pass only when the bytes match exactly; any
external change uses the strict validation path before the old file may replace the backup.

## Operational limits

- Maximum inventory-linked transaction: 100,000 items
- Maximum one pending transaction per player
- Unknown journal item identifiers are retained for administrator investigation instead of being silently discarded
- If the player data flush fails, the journal remains active and the next login or bank command retries reconciliation

## Remaining limitation

A persistent player-data storage failure can leave a journal active while the player remains online. The quantity cap and pre-transaction count keep recovery bounded, but large production servers should treat persistent player-save failures as a storage fault and resolve them before allowing continued play. A later server-focused release can add an administrator quarantine or disconnect policy for that condition.
