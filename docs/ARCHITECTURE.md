# Architecture and persistence

## Loader separation

The pure economy lives in `common/src/main/java`. Shared Minecraft-facing menus, commands, inventory helpers, Banker access, village-bank generation, and transaction reconciliation live in `common/src/minecraft/java`. Shared client screens live in `common/src/client/java`.

Fabric and NeoForge provide loader lifecycle hooks, menu registration, interaction events, player connection hooks, metadata, and build configuration.

## Player interaction

Banker villagers are vanilla librarian villagers carrying the persistent scoreboard tag `the_emerald_standard_banker`. Right-click interaction is intercepted server-side and opens a custom `BankerMenu`. The menu remains valid only while the player is alive and within eight blocks of the Banker interaction point.

The menu uses vanilla `ContainerData` synchronization and container-button packets. All financial actions execute on the server against `EconomyService`; the client screen never owns balances or decides transaction outcomes.

The screen receives bounded snapshots rather than the complete world-account map. Price history is sampled into 60 synchronized chart points from a maximum 180-day persisted history.

## Village banks

`VillageBankManager` scans loaded overworld villages at a configurable low frequency. One generated-bank marker and packed Banker anchor are stored per configurable region. Players in the same region are deduplicated during each scan.

The manager first attempts a safe automatic bank placement. If no suitable plot exists, it marks or spawns a Banker inside the village. Bankers receive a home restriction around the persisted anchor. This discovery-based approach supports existing worlds and avoids invasive vanilla jigsaw-pool replacement.

## World state

The economy is global to a Minecraft world or dedicated server. Market prices and regimes are shared by all players. Accounts and pending inventory transactions are keyed by player UUID.

The economy seed is generated from secure random data on first creation, mixed with the world seed and creation time, then stored in the save. It is not derived solely from the visible world seed.

## Unified economic clock

Format 5 stores:

- `lastWallClockMs`
- `lastGameTicks`
- `pendingEconomicMillis`

For each observation, elapsed wall time and elapsed game-tick time are converted to milliseconds. The larger interval is added once to the unified remainder. This allows offline catch-up and accelerated server time while preventing overlapping wall and game clocks from being counted twice.

One economic day equals 1,200,000 milliseconds, the normal duration of a Minecraft day.

Startup advances at most 2,000 economic days before the server opens. Remaining catch-up is processed in 250-day tick batches. The trusted backlog is capped at 25,000 days, and banking is paused until the backlog reaches zero.

## Currency precision

One emerald equals 1,000,000 micro-emeralds. Cash, savings, CDs, villager lending, transaction deltas, and commodity proceeds use integer micro-emerald balances. Shares remain fractional doubles in this alpha and are validated as finite and nonnegative.

## Persistent data format 5

The current format contains:

- Required magic identifier
- Explicit format number
- SHA-256 checksum over sorted state properties
- Market and commodity prices
- Up to 180 history points per investment
- Economic regime and day
- Last market event and its economic day
- Unified partial clock progress
- Generated village-bank regions and Banker anchors
- Player accounts
- CD and villager-lending state
- Pending inventory transactions

Formats 1, 2, 3, and 4 are migrated when loaded. Saves with a future format number are rejected without falling back to an older backup.

## World configuration

`the_emerald_standard-config.properties` is created in the world's `data` directory. Values are bounded and parsed strictly. It controls village-bank enablement, scan interval, region size, Banker restriction radius, and transaction cooldown. `/emerald config reload` atomically replaces the active in-memory settings after validation.

## Save process

Writes use this sequence:

1. Validate complete state and no-debt invariants.
2. Serialize required fields and calculate the SHA-256 checksum.
3. Write and flush a temporary file.
4. Preserve a validated previous save as `.bak`.
5. Atomically replace the primary when supported.
6. Best-effort flush parent directory metadata.

A missing format, invalid magic identifier, missing mandatory field, or checksum mismatch marks the primary as corrupt and permits backup recovery. A future format is not corruption and stops startup so newer data cannot be overwritten by an older mod build.

## Inventory transaction journal

Deposits, withdrawals, and resource exchanges use durable `PREPARED` and `BANK_COMMITTED` stages because Minecraft player inventory and the bank are stored separately. See [TRANSACTION_RECOVERY.md](TRANSACTION_RECOVERY.md).

## Current alpha limits

- One CD and one villager business lending position per player
- One pending inventory-linked transaction per player
- 180 days of chart history
- 60 synchronized chart samples per open Banker menu
- Runtime village-discovery generation rather than direct jigsaw-pool injection
- Share quantities use floating-point values pending a fixed-point holdings migration
- Synchronous account mutations snapshot only the affected account and transaction journal, but still rewrite the world bank file; very large public servers will eventually need partitioning or an append-only ledger
- Vanilla exposes only an all-online-player public save path, so completed inventory-linked transactions still flush all connected player data before their journal is cleared
