# Architecture and persistence

## Loader separation

The pure economy lives in `common/src/main/java`, while shared Minecraft-facing commands and inventory transactions live in `common/src/minecraft/java`. Fabric and NeoForge compile both source sets and provide only loader lifecycle hooks, command registration, metadata, and build configuration.

## World state

The economy is global to a Minecraft world or dedicated server. Market prices and regimes are shared by all players. Accounts are keyed by player UUID.

The economy seed is generated from secure random data on first creation, mixed with the world seed and creation time, then stored in the save. It is not derived solely from the visible world seed, so players cannot calculate every future market outcome from a shared seed.

## Currency precision

One emerald equals 1,000,000 micro-emeralds. Cash, savings, CDs, and villager loans use integer micro-emerald balances. Shares remain fractional doubles in this alpha and are validated as finite and nonnegative.

## Persistence

State is stored in `the_emerald_standard.properties` inside the world's data directory. Writes use this sequence:

1. Validate the complete state and no-debt invariants.
2. Serialize to a temporary file.
3. Flush the file contents.
4. Preserve the previous save as `.bak`.
5. Atomically replace the primary file when supported.

If a primary save cannot be parsed, the backup is loaded. Every account mutation snapshots the previous state and restores it when validation or persistence fails.

The current format is version 2 and includes migration support for the original prototype format.

## Inventory transactions

Deposits and resource exchanges remove items only after validating inventory quantities. If the bank-side save fails, removed items are returned to inventory or safely dropped at the player.

Withdrawals save the bank debit first, insert legal-size item stacks, and credit any uninserted remainder back to the bank. If even that recovery save fails, the remainder is safely dropped rather than destroyed.

## Current alpha limits

- One CD and one villager business loan per player
- Command interface instead of the Banker GUI
- No cost-basis history or chart storage yet
- No data-pack commodity definitions yet
- Share quantities use floating-point values pending a fixed-point holdings migration
