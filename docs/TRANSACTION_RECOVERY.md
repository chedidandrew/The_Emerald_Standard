# Inventory transaction recovery

## Why a journal is required

The bank account is stored in `the_emerald_standard.properties`, while Minecraft saves a player's inventory in separate player data. A process can stop after one file is committed but before the other file is written. Without reconciliation, that timing window can duplicate or destroy emeralds and exchanged resources.

The mod uses a durable journal that survives restart and records enough information to repair either side.

## Deposit and resource exchange

1. Persist a PREPARED journal with a unique transaction ID and intended bank credit.
2. Remove ordinary items, then save an ID/applied-delta receipt in the same player NBT as inventory.
3. Checkpoint and read back both inventory and receipt before committing bank credit.
4. Persist BANK_COMMITTED, then durably clear the journal before returning success.

A PREPARED receipt proves exactly which removed items need restoring. A committed credit
requires the matching removal receipt; contradictory or missing evidence is retained for
operator review. Current item totals are not proof that a later legitimate stack is duplicated.
Legacy pending journals without receipts fail closed rather than guessing from counts.

## Withdrawal

Persist the cash debit and withdrawal intent first. Measure actual inventory insertion, save
the delivery receipt and inventory together, then refund only the undelivered remainder and
clear the journal durably. Full inventory leaves undelivered value in Bank Cash. Creative
mode's insertion return value is not trusted without observing the actual item delta.

## Idempotence and blocking

Login, logout, recovery commands and banking entry points may reconcile pending work. A
transaction ID plus its recorded applied delta prevents replaying a committed delivery.
One pending journal is allowed per player. If a transfer cannot settle, the player is
disconnected to prevent death or ordinary inventory changes while recovery is unresolved.
Reconnect after transient faults; persistent disk or contradictory-receipt faults require
the server owner. Recovery does not drop protected items.

GUI cooldowns, current-menu/distance checks and exact-amount confirmation are additional
guards, not a replacement for the journal. Inventory top-ups settle before the requested
investment or gift. If that second operation fails, the top-up remains withdrawable Bank Cash;
it is neither lost nor automatically spent on restart.

## Why the transaction checkpoint writes one player directly

Minecraft's public all-player save is both unnecessarily broad and unable to report a failed
player-data write: the 26.2 player storage method logs and suppresses write exceptions. The
transaction checkpoint therefore mirrors that version's player-data algorithm with public APIs:
`saveWithoutId` supplies the same root NBT, including the current `DataVersion` and loader-added
entity attachments; `NbtIo.writeCompressed` synchronizes the temporary file; and
`Util.safeReplaceOrMoveFile` reports whether the `.dat`/`.dat_old` rotation succeeded. Reading the
new file back and comparing its complete `Inventory` and receipt-bearing `Tags` payloads is the final condition for clearing the
journal. The replaced target file is forced again, and the containing directory is forced where
the filesystem provider supports directory channels. Journal removal is then committed to the
economy file before the operation succeeds. If the server stops in the narrow interval between the
verified player save and journal cleanup, gameplay has not resumed and login recovery can safely
reconcile the matching transaction receipt.

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

## Remaining limitations

Persistent storage failure is an operational fault, not something a GUI can repair. Do not
edit receipts or restore only one side of a transfer. Restore a consistent whole-world backup.
Hostile mods, operator edits and disk corruption fall outside ordinary transaction guarantees.
Finish pending transfers before upgrading and retain a backup; never downgrade a newer save.
See [inventory-aware spending](UNIFIED_SPENDING.md) for user-facing behavior.
