# Village Prosperity System

The Village Prosperity System connects The Emerald Standard's global market to persistent local Minecraft settlements while preserving the mod's lightweight identity.

The key architectural rule is simple: **offline progression changes data, not chunks or entities**. Physical village growth materializes gradually only when players are nearby and the relevant chunks are already loaded. Prosperity observation and materialization are dimension-aware; the separate Village Bank structure remains Overworld-only in beta.3.

## Configuration

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
```

The four settings are independent.

- `simulation_enabled`: advances population, supplies, output, prosperity, safety, projects, and lifecycle data.
- `visual_progression_enabled`: allows loaded villages to materialize approved structures and reconcile physical settlers.
- `market_integration_enabled`: allows eligible villages to contribute their capped fundamental factor to assets and commodities.
- `automatic_recovery_enabled`: allows recoverable extinct villages to enter the recovery process after their cooldown.

Turning visual progression off never removes structures that already exist. Turning market integration off leaves the local village simulation intact but makes the global market ignore settlement fundamentals.

## Stable village identity

Each village receives a private UUID. The world-facing scanner prefers a nearby village bell as the persistent physical center when one is available, then falls back to the observed villager cluster. Existing bank-region mappings remain a compatibility aid, while proximity reuse is intentionally tighter than in beta.1 to reduce accidental merging of nearby villages. Once known, that persisted center—not the discovering player's changing position—drives Village Bank keying and plot selection.

Every managed villager receives a village tag. Resident records preserve profession, status, last-seen day, and last-known position.

A missing entity is never treated as a death. Explicit Minecraft death events are required for casualties.

## Resident states

Residents can be:

- Active
- Away
- Infected
- Emigrated
- Dead

An unseen Active resident becomes Away after three economic days of continued village observation. A resident still absent after thirty economic days becomes Emigrated and no longer counts toward productive abstract population.

Loaded zombie villagers are recorded as Infected when a village tag, preserved UUID, or nearby known resident identifies the conversion. Infection removes that resident from productive population without recording a death. Repeated zombie observations are idempotent. When a living villager later appears near the infection location, the stale infected record is reconciled to the cured resident and productive population can recover.

## Abstract village economy

Each village tracks bounded values for:

- Population
- Observed population
- Housing capacity
- Food supply
- Material supply
- Treasury
- Prosperity
- Safety
- Farming output
- Mining output
- Trade output
- Redstone output
- Alchemy output
- Transportation output
- Security output
- Development points

The system never mines real terrain for abstract production and never creates physical ores as an economic side effect.

Active resident professions add small bonuses only to relevant sectors: farmers support agriculture, smiths and masons support mining, clerics support alchemy, and other matching professions support trade, transport, redstone, or security. Each sector bonus is capped at 12 percent, and unknown or modded professions retain the calibrated baseline.

### Maintenance and local variation

Village economies are not guaranteed to climb forever. Daily simulation now includes:

- Food consumption
- Small storage spoilage
- Infrastructure treasury upkeep
- Material upkeep
- Rare local harvest shocks
- Rare local trade disruptions
- Rare positive material discoveries
- Prosperity penalties during severe food shortages

These local effects are deliberately modest. Actual casualties and safety remain more important than random local shocks.

## Development tiers

| Tier | Name |
|---:|---|
| 0 | Hamlet |
| 1 | Village |
| 2 | Growing Village |
| 3 | Town |
| 4 | Prosperous Town |
| 5 | Regional Center |

A village's **functional tier can now rise or fall** as population and prosperity change. Completed physical structures are never automatically removed when the functional tier falls.

## Development projects

Beta 3 includes three physical project types:

- Cottage
- Warehouse
- Mine Entrance

Projects require population, resources, treasury, prosperity, safety, and development points. Offline simulation may complete their economic phase, but physical construction stays in a bounded visual backlog.

### Construction safety

The materializer follows conservative rules:

- No forced chunk loading
- No placement when a required chunk is unloaded; the reservation is retained and given a retry delay rather than being discarded on incomplete world information
- No replacement of block entities
- No replacement of solid or protected blocks
- No replacement of the existing terrain surface
- The project floor is placed in air directly above a flat surface made from a conservative natural-ground whitelist
- A failed `setBlock` result is treated as blocked instead of being counted as successful construction
- The resulting block state must match the authored placement before progress is recorded
- Village Banks and projects preflight `VillageDevelopmentProtection.register(PlacementGuard)` callbacks; vetoes and guard exceptions fail closed
- A site verified blocked before the first physical placement is released so a later pass can choose another safe lot after a persistent exponential delay
- A partially materialized deterministic template keeps its exact persisted bounds and resumes in place after the retry delay
- Development lots use exact bounding-box overlap checks and cannot overlap the Village Bank anchor
- Unsafe terrain, water, steep sites, and occupied air volumes are rejected

Warehouses and cottages use chests rather than barrels so prosperity buildings do not unintentionally create fisherman workstations.

Cottages include four real beds. Physical settler reconciliation requires actual available beds, keeping visible population tied to usable village housing.

Default construction speed is intentionally slower than beta.1: two blocks every ten server ticks. Servers may tune the values in configuration.

While blocks are successfully advancing, at most two nearby residents periodically look toward the site, swing an arm, and emit a small project-appropriate particle. Profession matching affects which villagers are preferred. These are bounded visual cues only; they do not install custom villager AI, force pathfinding, or become an authority for project completion.

## Population reconciliation

When visual progression is enabled, physical villagers are authoritative for productive population growth. A simulated birth or migration event queues a settler, but it does not increase productive abstract population until a real villager is materialized and a census observes it. This also lets physical villages converge after visual progression is re-enabled without relying on the old eight-settler cap.

Recovery behaves differently depending on configuration:

- **Simulation + visuals:** recovery approval queues two settlers, but abstract productive population stays at zero until those entities actually spawn and the census observes them.
- **Simulation only:** there is intentionally no physical population to wait for, so a recoverable settlement can resume abstractly.
- **Automatic recovery off:** extinct settlements remain extinct until players or existing villagers restore them through other gameplay.

A physical settler is only spawned when the destination is loaded, the village has enough stored food, an available real bed, no nearby hostile blocks settlement, a supported dry collision-free spawn is found, and the spawn interval has elapsed. Spawning does not immediately consume the queue; a later loaded-world census confirms the real villager and performs that reconciliation once.

## Village lifecycle

A village can be:

- Active
- Threatened
- Devastated
- Extinct
- Recovering
- Abandoned

Casualties reduce population, safety, prosperity, and production. A settlement with no productive population normally contributes no live market fundamentals; an active no-player-damage counterfactual is the deliberate exception described below.

### Hostile destruction

Pillager, raid, zombie, and other hostile deaths can lead to recoverable extinction. Repeated collapses increase the recovery delay and eventually suspend automatic recovery.

A Threatened or Devastated village that retains one or two productive survivors can enter Recovering after the seven-day stabilization window once safety and prosperity meet the minimum floor. This is a bounded route back to ordinary growth, distinct from zero-population automatic recovery.

### Player-caused destruction

Direct player kills and player-owned projectile kills are attributed to the player when Minecraft exposes that ownership. A player-caused extinction becomes Abandoned and does not automatically replenish victims.

Immediately before the first player-caused casualty changes an eligible village, beta.3 persists its exact state and current market contribution. The copy becomes a full no-player-damage counterfactual: on each enabled simulation day it advances under ordinary abstract village simulation and recalculates its market eligibility, contribution, and aggregation weight. Genuine non-player casualties are also applied to this branch and re-priced, while the player-caused damage is omitted.

Repeated player hits do not recapture or rebase the counterfactual, although they can extend the cooldown. Market aggregation uses it until that cooldown has elapsed and the live village has fully recovered. The model is village-local and does not freeze other settlements; live-only changes outside the counterfactual path do not rewrite it before release.

Environmental traps that Minecraft does not attribute to an attacker remain environmental incidents. They are deliberately given conservative market effects.

### Banker death

A Banker is only an interface to world-level accounts. Losing a Banker or an entire village never deletes player cash, savings, holdings, CDs, or villager-lending positions. Banker replacement is suppressed while the associated village is Extinct or Abandoned.

## Village restoration

Players may contribute emeralds to local village support. A contribution is a development grant, not a loan, debt, or guaranteed investment return.

Abandoned villages require the restoration threshold and a valid future recovery window before automatic recovery can resume. Player balances can never become negative.

## Global market connection

Eligible villages provide a small capped fundamental factor to the broader simulation:

- Mining supports Deepdelve Mining and commodity supply
- Agriculture supports Golden Harvest Cooperative
- Trade supports Nether Spice and Ender Freight
- Transportation supports Minecart Transit
- Security supports Iron Golem Security
- Prosperity and specialized output support Redstone Dynamics and Potionworks

The annual asset effect remains capped at approximately plus or minus 1.2 percentage points so settlement activity cannot guarantee investment returns.

Player-caused abandoned villages and empty settlements are excluded from fundamentals.

## Performance model

Village Prosperity is designed around bounded work:

- No offline AI
- No forced chunks
- Periodic loaded-world census only
- Dimension-aware scans restricted to loaded server levels
- One compact persistent record per known village
- Small incident and resident history limits
- Bounded project queue
- Bounded global block-placement budget
- Fair rotation of that budget across loaded dimensions and nearby settlements
- Persistent exponential retry gates for obstructed sites
- Catch-up batch size adjusted for stored account and settlement counts
- Cached village fundamentals for snapshot lists
- Physical-development snapshots filtered to the current dimension and nearby player positions
- No real resource mining for simulated output

For very large public servers, future storage partitioning may still be warranted, but single-player and ordinary multiplayer remain the primary beta target.
