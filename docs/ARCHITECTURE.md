# Architecture and persistence

## Loader separation

The loader-neutral economy and Village Prosperity simulation live in `common/src/main/java`. Shared Minecraft-facing menus, commands, inventory helpers, Banker access, village-bank generation, settlement observation, construction, and transaction reconciliation live in `common/src/minecraft/java`. Shared client screens live in `common/src/client/java`.

Fabric and NeoForge provide lifecycle hooks, menu registration, interaction and villager-death events, player connection hooks, metadata, and build configuration. Both loaders call the same economy and settlement services.

## Player interaction

Banker villagers are persistent vanilla villagers carrying The Emerald Standard Banker tags. Right-clicking a Banker or generated bank lectern opens a custom `BankerMenu`. All financial and village-support actions execute server-side against `EconomyService`; the client never owns balances, prices, settlement state, or transaction outcomes.

The five dashboard pages are Overview, Market, Banking, Exchange, and Village. The menu synchronizes bounded account, market, and local-village snapshots rather than exposing the complete world-account map or private economy seed.

## Village banks and stable settlement identity

`VillageBankManager` remains responsible for the compact Village Bank and Exchange. `VillageProsperityManager` separately observes and materializes the local settlement economy.

A settlement receives a persistent UUID. Resolution uses, in order:

1. A persisted village identity already carried by observed residents when its dimension and location remain plausible.
2. An existing persisted bank-region association when it still matches the settlement and dimension.
3. A nearby existing settlement within a deliberately tighter proximity threshold.
4. A deterministic new identity.

The observed settlement center prefers the nearest loaded village bell and falls back to the villager cluster. Once a record is created, its persisted center remains the stable reference point. A player's location only triggers Bank discovery; after settlement resolution, Bank keying and deterministic plot selection use that stable center.

Village Banks remain an Overworld-only gameplay surface in beta.3. Existing coarse grid keys and their anchors remain valid for upgraded saves. If a new stable settlement shares an already-owned legacy grid, it receives a deterministic per-village bank key rather than borrowing or duplicating the other settlement's bank. That scoped key is carried through Banker replacement, menu lookup, and support/restoration mutation so proximity cannot silently redirect an action to the wrong nearby village.

## Village Prosperity split

Village Prosperity deliberately separates abstract simulation from physical materialization.

### Abstract simulation

Runs only against compact persisted village records. It can advance while the world is closed and tracks population, housing, supplies, treasury, prosperity, safety, industry output, lifecycle, incidents, residents, and projects. It never loads chunks, runs villager AI, mines blocks, creates raids, or places structures.

### Physical materialization

Runs only in already-loaded chunks with nearby players, across the server's loaded dimensions. It performs censuses, tracks actual resident state, materializes approved Cottage, Warehouse, and Mine Entrance projects using one rotating global block budget, and spawns pending settlers only when real housing, food, spawn-space, and safety requirements are met.

With visual progression enabled, a simulated population increase becomes a pending settler instead of productive population. Productive population increases only after the real villager exists and is observed by a later census. This prevents invisible residents from producing resources or influencing the market.

## Village configuration modes

The following settings are independent:

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
```

`simulation_enabled` controls abstract local-economy advancement. `visual_progression_enabled` controls settlement materialization. `market_integration_enabled` controls whether eligible settlement fundamentals affect asset drift and commodity supply. `automatic_recovery_enabled` controls automatic recovery from eligible extinction.

## Construction safety

Prosperity projects are intentionally conservative:

- No forced chunks.
- Candidate lots must be flat and already loaded.
- The ground must match a small natural-ground whitelist.
- Mud and thin snow layers are not structural Bank support; full snow blocks remain eligible natural ground.
- The project floor is placed in air above the existing terrain surface.
- Block entities, solid player blocks, paths, farmland, and occupied air volumes are not replaced.
- Both prosperity projects and Village Banks consult `VillageDevelopmentProtection.register(PlacementGuard)` before placement. Integrations have no mandatory claim-mod dependency; a registered veto or thrown guard exception denies the placement.
- A block advances project progress only when Minecraft accepts the placement and the resulting state matches the authored template.
- Unsafe projects use persistent exponential retry backoff. A verified obstruction before the first placement releases the site so a later pass can choose a different lot. If the boundary is merely unloaded, the reservation is retained so blocks that may have been written before progress was journaled cannot be orphaned.
- Partially built projects retain their exact persisted bounds and deterministic template prefix, then retry in place without clearing or overwriting intervening player work.
- A failed Bank attempt remains unmarked and supplies a fallback Banker, allowing a later safe retry. A plot scan that is incomplete only because candidate chunks are unloaded is likewise not converted into a permanent fallback. Rollback matches authored block identity so neighbor-updated pane and fence states are included.

Cottages include real beds. Warehouses use chests instead of barrels so they do not unintentionally create fisherman workstations.

Active resident professions contribute bounded sector multipliers to abstract production. During nearby construction, at most two suitable villagers periodically look, swing, and emit particles near the project. These effects are deliberately non-authoritative theatre: they add no persistent pathfinding task and do not control economic progress.

Two cross-file durability limits remain explicit in beta.3. Project materialization trusts its persisted prefix and completion flag, so it does not reconcile or rebuild already-counted blocks after chunk rollback or player removal. Bank markers live in the economy save while bank blocks live in Minecraft chunks; those writes are not atomic together. A crash between them can leave a marker without a structure, in which case the mod uses an eligible fallback Banker and does not infer per-block ownership or auto-rebuild.

## Resident lifecycle

Resident records track Active, Away, Infected, Emigrated, and Dead states. Missing entities are never inferred dead.

- Active residents absent from repeated loaded censuses become Away after a grace period.
- Residents still absent after the longer emigration window become Emigrated and stop contributing to productive population.
- Zombie villagers are reconciled to nearby or tagged residents and recorded as Infected, suspending productive population without inventing a death.
- A living villager later observed near an infection can reconcile the stale infected record as a cure.
- Explicit villager death events create casualties and lifecycle incidents.

Direct player and player-owned projectile kills are marked player-caused when Minecraft exposes ownership. Environmental traps without attacker attribution remain environmental incidents.

Before the first player-caused casualty mutates an eligible settlement, the service persists both the exact live village state and its current market contribution. This becomes a no-player-damage counterfactual branch, not a static score: on each economic day with Village Prosperity simulation enabled, it runs the normal abstract village simulation without applying the player's damage, then recalculates its eligibility, aggregation weight, and sector contributions. Genuine non-player casualties observed while the branch is active are applied to the counterfactual and immediately re-priced. Further player hits can extend the cooldown but never recapture or rebase the branch.

Market aggregation uses the counterfactual result until the cooldown has elapsed, the live village is Active again, and its population has returned to at least the captured level. Other settlements continue normally. Live-only changes that are not part of the no-player-damage branch do not rewrite its history before release.

A Threatened or Devastated settlement with one or two living survivors can transition to Recovering after the seven-day stabilization window once safety and prosperity reach the recovery floor. This restores access to bounded population growth without applying the zero-population automatic-refugee rules.

## World state

The economy is global to one Minecraft world or dedicated server. Market prices and regimes are shared by all players. Player accounts and pending inventory transactions are keyed by UUID. Village records are keyed by their persistent settlement UUID.

The economy seed is generated from secure random data on first creation, mixed with world information, and stored privately. It is not predictable solely from the visible Minecraft world seed.

## Unified economic clock

The current save stores wall-clock time, game ticks, and one pending economic-millisecond accumulator. For each observation, the larger of elapsed trusted wall time and elapsed game time is added once. This supports offline catch-up while preventing overlapping online and wall time from being counted twice.

One economic day equals 1,200,000 milliseconds. Startup advances at most 2,000 economic days before the server opens. Remaining catch-up is processed in bounded batches and banking pauses until the market is current.

## Currency precision and no-debt invariant

One emerald equals 1,000,000 micro-emeralds. Cash, savings, CDs, villager lending, inventory-journal deltas, and commodity proceeds use integer micro-emerald balances. Shares remain validated finite nonnegative doubles in the current beta.

No player account contains a debt balance. Village support is a contribution, not borrowing, and a player can never owe more emeralds than were voluntarily committed.

## Persistent data format 8

Format 8 includes:

- Required magic identifier and explicit format number
- SHA-256 checksum over sorted state properties
- Global market, commodities, regime, event, and chart history
- Unified economic-clock state
- Bank regions and exact Banker anchors
- Player accounts, CDs, lending, holdings, and pending inventory transactions
- Stable village identities and bank associations
- Village lifecycle, resident records, incidents, resources, production, tier, restoration state, and pending settlers
- Per-village no-player-damage counterfactual state, its daily re-priced market contribution and weight, and its capture/release metadata
- Project approval, economic completion, site reservation, exact bounds, physical progress, retry deadline, and materialization-failure count

The 0.4 beta migrates beta.4 format-7 saves forward to format 8. The version boundary is required because the persistent Village Prosperity project catalog now contains identifiers that beta.4 does not understand. Beta.4 rejects format 8 as a future format without stale-backup fallback. Downgrading therefore requires restoring a pre-upgrade world backup. Unsupported formats newer than 8 are likewise rejected without overwriting them.

## Save process

Writes validate complete state and no-debt invariants, serialize mandatory fields, calculate the checksum, flush a temporary file, preserve a validated previous save as `.bak`, atomically replace the primary when supported, and best-effort flush parent directory metadata.

Inventory-linked deposits, withdrawals, and exchanges use durable `PREPARED` and `BANK_COMMITTED` journal stages because Minecraft player inventory and the bank are stored separately. See [TRANSACTION_RECOVERY.md](TRANSACTION_RECOVERY.md).

## Current beta scaling boundary

The simulation is intentionally lightweight for single-player and ordinary multiplayer, but very large public servers will eventually need storage partitioning. Account and village mutations still serialize the shared world economy file, and large offline catch-up still advances known villages day by day. Catch-up batches scale down with known account and settlement counts, and physical development requests only dimension-matching snapshots near loaded players instead of copying the full village registry. Snapshot lists reuse one village-fundamentals calculation instead of recalculating it for every settlement.


## Debug flight recorder

`DebugFlightRecorder` is a server-side, operator-triggered, time-bounded diagnostic layer. Loader tick hooks perform only a constant-time inactive lookup when no capture exists. During a capture, the recorder samples the authoritative `EconomyService`, the initiating tester's account, and the nearest settlement; instrumentation hooks add construction, census, casualty, settler, and GUI-action events.

The JSON Lines timeline is flushed after every event and packaged with sanitized snapshots at normal stop, timeout, disconnect, size limit, or server shutdown. Any `.active-*` directory left by a process crash is converted into an `INCOMPLETE-CRASH` report during the next initialization. The recorder never serializes the private economy seed or unrelated accounts.
