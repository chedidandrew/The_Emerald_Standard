# Architecture and persistence

## Loader separation

The pure economy lives in `common/src/main/java`, while shared Minecraft-facing commands, inventory helpers, and transaction reconciliation live in `common/src/minecraft/java`. Fabric and NeoForge compile both source sets and provide only loader lifecycle hooks, player connection hooks, command registration, metadata, and build configuration.

## World state

The economy is global to a Minecraft world or dedicated server. Market prices and regimes are shared by all players. Accounts and pending inventory transactions are keyed by player UUID.

The economy seed is generated from secure random data on first creation, mixed with the world seed and creation time, then stored in the save. It is not derived solely from the visible world seed, so players cannot calculate every future market outcome from a shared seed.

## Economic clock

The save stores both the last observed clocks and partial progress:

- `lastWallClockMs`
- `lastGameTicks`
- `pendingWallClockMs`
- `pendingGameTicks`

A short session no longer discards the unfinished fraction of a Minecraft day. Wall and game progress accumulate independently. The larger whole-day count advances the economy, and equivalent whole days are consumed from both sources so ordinary online play is not counted twice.

Startup advances at most 2,000 economic days before the server opens. Remaining catch-up is processed in 250-day tick batches. The trusted backlog is capped at 25,000 days, and player banking is paused until the backlog reaches zero.

## Currency precision

One emerald equals 1,000,000 micro-emeralds. Cash, savings, CDs, villager loans, transaction deltas, and commodity proceeds use integer micro-emerald balances. Shares remain fractional doubles in this alpha and are validated as finite and nonnegative.

## Persistent data format

The current format is version 3. It includes:

- Market and commodity prices
- Economic regime and day
- Last observed and pending partial clock progress
- Player accounts
- CD and villager-lending state
- Pending inventory transactions

Formats 1 and 2 are migrated when loaded. Saves with a future format number are rejected so an older build cannot silently damage newer data.

## Save process

Writes use this sequence:

1. Validate the complete state and no-debt invariants.
2. Serialize to a temporary file.
3. Flush the temporary file contents.
4. Preserve a validated previous save as `.bak`.
5. Atomically replace the primary file when supported.
6. Best-effort flush the parent directory metadata.

If a primary save cannot be parsed, the backup is loaded. A corrupt or future-format primary never replaces a known-good backup.

Bank account mutations remain synchronous and durable. Routine economic clock and price progress is marked dirty and saved in 30-second batches. Failed automatic saves keep deterministic progress in memory and retry with exponential backoff rather than writing every server tick.

## Inventory transaction journal

Minecraft player inventories and The Emerald Standard bank are stored in different files. Deposits, withdrawals, and resource exchanges therefore use a durable two-stage journal:

- `PREPARED`: intent is durable, but bank cash is unchanged.
- `BANK_COMMITTED`: the bank debit or credit is durable, and player inventory still requires a confirmed save.

The journal stores the player, transaction ID, item, quantity, pre-transaction inventory count, bank delta, stage, economic day, and wall-clock time. Only one inventory-linked transaction may be active per player.

After the inventory change and bank commit, online player data is synchronously flushed through vanilla's public `saveAll()` path. The journal is cleared only after that flush succeeds. If the server or process stops at any point, login or logout reconciliation compares the saved inventory against the journal and completes or rolls back the missing side idempotently.

The public API does not expose the protected single-player save method, so alpha.2 deliberately favors correctness over scale by flushing all online players at transaction completion. A later server-scale storage layer can replace this broader flush without changing the journal state machine.

See [TRANSACTION_RECOVERY.md](TRANSACTION_RECOVERY.md) for the full state machine.

## Validation

The save validator rejects:

- Negative balances or product values
- Missing market or commodity prices
- Unknown or non-finite holdings
- Inconsistent active or inactive CD fields
- Inconsistent active or inactive villager-lending fields
- Loans resolved before maturity
- Invalid default recovery states
- Oversized, malformed, or mathematically inconsistent inventory journals
- Unsupported future save formats

## Current alpha limits

- One CD and one villager business lending position per player
- One pending inventory-linked transaction per player
- Command interface instead of the Banker GUI
- No cost-basis history or chart storage yet
- No data-pack commodity definitions yet
- Share quantities use floating-point values pending a fixed-point holdings migration
- Synchronous account mutations still rewrite the world bank file, and inventory-linked transactions currently flush all online player data. Both are suitable for alpha correctness but will need partitioning or an append-only ledger for large public servers
