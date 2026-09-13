# Village Prosperity System

The Village Prosperity System connects The Emerald Standard's global market to persistent local Minecraft settlements while preserving the mod's lightweight identity.

The key architectural rule is simple: **offline progression changes data, not chunks or entities**. Once discovered, a village's economy advances regardless of player distance and through trusted offline catch-up on the next server start. Physical village growth materializes gradually only when a player in the same dimension is within the configured horizontal X/Z activation radius and the relevant chunks are already loaded. The recommended default is 256 blocks, configurable from 48 through 512; Y separation does not affect eligibility, every eligible site has an independent construction allowance, and the mod never force-loads chunks. Prosperity observation and materialization are dimension-aware; the separate Village Bank structure remains Overworld-only in the 0.4 beta.

## Configuration

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
village_prosperity.development_radius=256
village_prosperity.donations_enabled=true
village_prosperity.endowments_enabled=true
village_prosperity.project_sponsorship_enabled=true
village_prosperity.targeted_donations_enabled=true
village_prosperity.donor_recognition_enabled=true
village_prosperity.fast_track_capital_enabled=true
village_prosperity.endowment_annual_payout_bps=400
village_prosperity.minimum_emergency_reserve_percent=20
village_prosperity.max_monthly_treasury_spending=24
```

The simulation settings are independent.

- `simulation_enabled`: advances population, supplies, output, prosperity, safety, projects, and lifecycle data.
- `visual_progression_enabled`: allows loaded villages to materialize approved structures and reconcile physical settlers.
- `market_integration_enabled`: allows eligible villages to contribute their capped fundamental factor to assets and commodities.
- `automatic_recovery_enabled`: allows recoverable extinct villages to enter the recovery process after their cooldown.

`development_radius` controls only same-dimension horizontal activation for physical work on known settlements. It accepts 48–512 blocks, ignores vertical distance, and does not limit their data-only economic simulation, load chunks, or expand villager AI. Each eligible site's physical work has an independent one-block-per-ten-tick allowance. Construction-theatre searches retain local limits. Resident census and housing surveys instead follow district ownership, developed coverage and registered homes; settlers receive a specific claimed HOME bed. Keep the recommended 256 default unless the server already loads village chunks farther away; a larger activation radius is not a view-distance or force-loading setting.

Turning visual progression off never removes structures that already exist. Turning market integration off leaves the local village simulation intact but makes the global market ignore settlement fundamentals.

The Fund settings independently enable gifts, protected-principal endowments, current-project sponsorship, targeted purposes, and visible donor titles. The remaining settings select the endowment's annual payout rate in basis points, the fraction of ordinary grants held for emergencies, and the monthly ceiling for moving village-owned Fund value into local economic inputs.

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
- Unverified (including unseen residents retained from older Away records)

An unseen Active or legacy Away resident becomes Unverified on the next census. Absence never implies emigration or a casualty. UUID records and their population commitment survive unloaded chunks and long offline absences. Confirmed death, infection and a loaded villager's confirmed home transfer provide separate evidence; merely crossing a district boundary does not change ownership. No deficit-based replacement settlers are created.

Housing surveys cover exclusive natural-village parcels and associated homes at every world height; legacy districts retain their developed survey rectangles. Work is round-robin and budgeted to 2,048 inspected cells per tick (reduced under load); empty/non-bed sections are skipped. Only complete loaded chunk observations replace saved bed-head lists. Missing chunks retain prior evidence. New villages use parcel ownership, with nearest original center and UUID tie-breaking; legacy overlapping surveys retain registered-home priority. Bed cache limits are 4,096 positions/chunks, with 1,024 resident identities per district. Real observed populations may exceed the simulation bound (512 for new natural villages, 64 for legacy districts) without increasing simulated production indefinitely.

Immigration accumulates fractional progress once per economic day. At perfect conditions, an 8-resident district earns about 1.18 arrivals/day and a 48-resident district about 3.18; Peaceful multiplies by 1.15, capped at four approvals/day. Recovery and upkeep strain slow progress. Lifecycle, Safety 45+, housing and enough food for the proposed committed population still gate growth. At most eight settlers wait for physical placement, and a full queue or ineligible district cannot bank a multi-day burst.

Each physical arrival needs a surveyed, intact, unoccupied and unclaimed bed, loaded blocks, solid safe footing and a path from its nearby landing to that bed. A living monster within 12 blocks blocks that landing only when an unobstructed collision ray connects it to the landing; walls and underground terrain can shelter it. No arrival check force-loads chunks. Attempts share the configured default 600-tick cadence. A pending identity and bed are journaled before insertion; definite insertion failure returns the approval, while an ambiguous crash leaves the claim Unverified, not automatically respawned. This conservatively prevents replay duplicates but cannot provide a distributed atomic transaction with Minecraft's separate entity save.

Town's progress report and /emerald debug expose queued and unverified residents, saved immigration fraction, physical bed survey and the latest arrival status. The combined city total includes all associated districts; the headline distinguishes actual/known commitments from the economic production cap.

Loaded zombie villagers are recorded as Infected when a persisted village tag, an already-known resident match, or a nearby known resident identifies the conversion. Infection removes that resident from productive population without recording a death. Repeated zombie observations are idempotent. When a living villager later appears near the infection location, the stale infected record is reconciled to the cured resident and productive population can recover; this reconciliation does not assume Minecraft preserved the entity UUID.

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

### Peaceful difficulty

With abstract simulation enabled, the authoritative world difficulty selects an automatic easier
growth profile on Peaceful. It increases production and development, reduces food use, accelerates
eligible settlers and recovery, and makes full 100 prosperity easier to attain. Easy/Normal/Hard
retain the previous baseline. It does not grant player money, remove project costs, ignore damage,
bypass housing or placement safety, or enable a disabled simulation. Difficulty is refreshed before
startup catch-up and each server tick; catch-up uses the current difficulty, not historical settings.
See [exact tuning and structure loot](PEACEFUL_GROWTH_AND_LOOT.md).

| Tier | Name |
|---:|---|
| 0 | Hamlet |
| 1 | Village |
| 2 | Growing Village |
| 3 | Town |
| 4 | Prosperous Town |
| 5 | Regional Center |

A village's **functional tier can now rise or fall** as population and prosperity change. Completed physical structures are never automatically removed when the functional tier falls.

### One natural village, one growing territory

New natural villages retain one district and one Bank while connected, non-overlapping
16-block parcels extend their territory. They support **512 simulated residents** and
**512 project records**, without the old six-home restriction. Population-scaled Granaries,
Warehouses, Markets and Guard Posts support larger settlements. Need-driven selection may
stop before those ceilings. Legacy districts retain the 64-resident/12-project bounds.
Tier remains 0–5: tier 5 requires 28 residents, 75 prosperity and six operational projects.

Population needs spare functional housing, adequate food and safety. Resource production finances
need-driven projects, which add housing or services and can lift the tier. Approved buildings and
settlers materialize only near players in already-loaded chunks. The development radius controls
when this work runs. Expansion now reserves connected infill/frontier land for that same
natural village, subject to economic needs and safe loaded land; it does not create child districts. See
[city expansion and lighting](CITY_EXPANSION.md) for controls and balance. Player construction and ordinary
Minecraft breeding are separate; the mod does not delete excess villagers to enforce an entity cap.

## Development projects

The 0.4 beta includes ten physical project types:

- Cottage
- House
- Village Inn
- Warehouse
- Mine Entrance
- Market Square
- Smithy
- Granary
- Guard Post
- Exchange Hall

Projects require population, resources, treasury, prosperity, safety, and development points. Offline or distant simulation may complete their economic phase, which immediately grants the project's housing and production effects. Its authored blocks remain a separate, bounded visual backlog until the village is nearby and loaded. A physically completed project that later fails its integrity audit loses economic authority. Partial damage remains suspended until the complete authored structure is restored; severe demolition retires the old bounds and suspends benefits until the same economic project is fully rebuilt at a different safe lot. Simple distance, unloaded chunks, or an unfinished initial visual queue do not suspend it.

### Construction safety

The materializer preserves real builds while allowing routine new-site preparation:

- No forced chunk loading
- Natural villages search up to 128 rotating infill candidates then 128 adjacent frontier candidates per sweep, extending connected territory only on reservation. Legacy sites retain their bounded expanding-ring search. Due projects rotate between pulses.
- No placement when a required chunk is unloaded. An unreserved frontier is backed off before a later expanded sweep, while an existing reservation is retained and delayed rather than discarded on incomplete world information
- No replacement of block entities
- New reservations may clear ordinary torches and recognizable natural tree remnants in a bounded,
  resumable pass. Shallow mining/blast damage can use existing foundation bridging. No direction
  settings or player-drawn no-build zones are required; existing buildings are never retro-cleared.
- No replacement of solid construction or protection-claimed blocks
- No replacement of the existing terrain surface
- The project floor is levelled in air above the highest sampled surface on a conservatively whitelisted natural lot with at most four blocks of height variation. Preflight transforms the authoritative base first and checks every ground-contact column, including rotated or mirrored annexes outside the nominal descriptor
- A deterministic append-only foundation suffix bridges only bounded air gaps, stops at loaded sound natural ground, and supplies missing footings beneath the lowest authored ground-contact cells
- Replaceable plants and snow may be cleared. Public trail cells may pave only dirt-like or sandy ground; stone-family and snow blocks are skipped. Vanilla does not identify who placed dirt, grass, podzol, mycelium, or sand, so a claim integration must register a protection guard when player-placed natural terrain must be preserved
- A failed `setBlock` result is treated as blocked instead of being counted as successful construction
- The resulting block state must match the authored placement before progress is recorded
- Village Banks and projects preflight `VillageDevelopmentProtection.register(PlacementGuard)` callbacks; vetoes and guard exceptions fail closed
- A site verified blocked before the first physical placement is released so a later pass can choose another safe lot after a persistent exponential delay
- A partially materialized deterministic template keeps its exact persisted bounds and resumes in place after the retry delay; when an append-only blueprint upgrade passes preflight, its expanded bounds are persisted in the same reconciliation update
- A low-frequency audit verifies one completed authored structure at a time. Doors and workstations remain structural; nonessential base decoration is cosmetic. Authored reserved air, entrance standing space, access targets, and vertical-access dismounts must stay clear. Blocking one suspends benefits without removing or overwriting the player's block, and access-only obstruction does not count toward severe demolition
- Each cumulative Blueprint V2 stage is admitted with skylight excluded. Every authored roof-covered floor whose state can support a Zombie spawn and whose two standing cells are clear must receive block light 7 or greater; supported pendants and wall fixtures provide natural coverage instead of hidden light blocks
- A required light emitter and its full support closure remain structural even when split across the base, town, or city layers. Admission checks each cumulative stage with Minecraft 26.2 attachment semantics: standing lanterns and torches need a `SupportType.CENTER`-bearing floor face, every hanging lantern's contiguous vertical chain must terminate at a `SupportType.CENTER`-bearing ceiling face, and wall lights need sturdy backing. The same read-only attachment gate covers every current Bank biome plan. Hanging chains and their terminal beam, standing-light supports, and wall-light supports are preserved without changing the canonical plan hash. Breaking any required fixture or support makes the completed project unsafe and manual-restoration-required; the mod never replaces it
- Partial damage never regenerates missing blocks or creates another building, preventing collectible furnishings from becoming renewable drops. Restoring the complete authored plan lets a later audit reactivate the project
- Severe demolition requires at least 12 mismatches and at least 30 percent of authored structure cells. The mod records the old bounds, clears only the project's active reservation, and searches for a replacement elsewhere; it never removes, repairs, or overwrites the old site
- Retired bounds are append-only and excluded from all later project and Bank searches with a safety margin. Each project retains at most 16 lots; at that cap, further damage stays unsafe instead of forgetting an earlier player-edited footprint
- Development lots use exact bounding-box overlap checks and cannot overlap the Village Bank anchor
- Unsafe terrain, water, steep sites, and occupied air volumes are rejected

Templates use a small, theme-appropriate set of vanilla utility and job-site blocks: residences support household crafting or farming, warehouses support crafting and cloth/stone work, markets offer several trading professions, and industrial or finance landmarks provide their expected facilities. This gives ordinary villager AI useful destinations without granting those blocks custom economic authority. The Exchange Hall contains exactly one Exchange Desk. Barrels, lecterns, cartography tables, and emerald, diamond, gold, or netherite blocks are prohibited from every prosperity template.

The richer blueprints are append-only relative to the original beta templates, so an older completed project retains its authored prefix. Before suspending its benefits, the integrity audit requires every added structural position to be loaded, empty or replaceable, free of a block entity, and accepted by the protection hook. A safe upgrade enters the normal guarded repair queue; any occupied or vetoed structural suffix leaves the older project operational and untouched. Matching block type alone never proves ownership. Covered templates have continuous roof planes with attached fascia, and residential entrance paths remain clear.

Each project receives one of three deterministic presets derived from the stable village identity, project ID, and type. Development tiers 2-3 append the town detail layer, and tiers 4-5 append the city layer; persisted layer sizes make the progression monotonic, so a later tier decline never removes an installed upgrade. Every project also plans a bounded, slightly irregular three-block-wide trail from its entrance to the nearest earlier economic project or a stable hub near the village edge. Before a new lot is reserved, the first center-route cells must support a continuous terrain-matched entrance: zero to four additional inward-facing stairs, bottom-up supports, and two blocks of headroom are preflighted and frozen independently from both the building and road. The approach begins as soon as the building completes. Format-14 and older completed modular projects receive the format-15 approach check once; occupied or protected retrofit cells are preserved and waived permanently rather than overwritten or repaired. The road center uses dirt path with deterministic gravel accents, and coarse dirt remains on both shoulders; all three lanes cover straight segments, turns, and endpoints while excluding the building envelope. Trails are shared, non-authoritative infrastructure: matching TES-style paths are adopted, and unsafe, occupied, or protected cells are skipped without suspending project benefits. Older modular routes retain their exact frozen construction selector until complete. A separate format-14 migration then freezes the exact list size of historically coarse center coordinates and advances its own cursor without changing the road total or construction cursor. It accepts dirt path or gravel as complete, writes only over coarse dirt, saves every successful advance immediately, and never runs again after its version completes. Initial paving uses a narrow dirt-like and sandy-ground whitelist; stone, andesite, diorite, granite, snow blocks, and every other non-soil block are skipped. Minecraft has no placement provenance for natural-looking dirt, grass, podzol, mycelium, sand, or the legacy coarse-dirt retrofit surface, so claim integrations must register a guard when those player-placed cells need protection.

Cottages, Houses, and Inns include real beds. Physical settler reconciliation requires actual available beds, keeping visible population tied to usable village housing.

Normal construction defaults to two blocks per second per active site at 20 TPS, configurable from 1–100. The per-site pace is distinct from globally bounded catch-up/debug budgets. Terrain preparation and the temporary perimeter precede structural work; unsafe or occupied work cells can pause a site.

While blocks are successfully advancing, at most two nearby residents within the fixed local 48-block entity search range periodically receive one low-speed navigation request toward a safe exterior waypoint, look toward the site, swing an arm, and emit a small project-appropriate particle. Profession matching affects which villagers are preferred. These are bounded visual cues only; they do not install a persistent villager goal, inherit the wider development radius, force chunks, or become an authority for project completion. Arriving settlers receive a specific claimed HOME bed; resident census coverage is separate from worker-theatre range.

## Population reconciliation

When visual progression is enabled, a simulated birth or migration creates a committed settler. Committed settlers count toward bounded economic population immediately, while the pending count durably records how many villager entities still need to appear. A later loaded-world census transfers an observed arrival from pending to physical population without changing that committed total or counting it twice. This lets the economy keep growing at any player distance while the visible village converges gradually after chunks are naturally loaded.

Recovery behaves differently depending on configuration:

- **Simulation + visuals:** recovery approval queues two settlers. An arrival is counted only when its durable identity is claimed for physical insertion; the next census confirms the UUID. No missing-resident replacement queue is inferred.
- **Simulation only:** there is intentionally no physical population to wait for, so a recoverable settlement can resume abstractly.
- **Automatic recovery off:** extinct settlements remain extinct until players or existing villagers restore them through other gameplay.

Physical arrivals follow the bed/landing and durable identity checks described under Resident states above. The saved queue is consumed once at claim time, not again by a later census. A villager added by a player or breeding is an external resident, not evidence that a queued settler has materialized.

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

Immediately before the first player-caused casualty changes an eligible village, the 0.4 beta preserves its exact state and current market contribution. The copy becomes a full no-player-damage counterfactual: on each enabled simulation day it advances under ordinary abstract village simulation and recalculates its market eligibility, contribution, and aggregation weight. Genuine non-player casualties are also applied to this branch and re-priced, while the player-caused damage is omitted.

Repeated player hits do not recapture or rebase the counterfactual, although they can extend the cooldown. Market aggregation uses it until that cooldown has elapsed and the live village has fully recovered. The model is village-local and does not freeze other settlements; live-only changes outside the counterfactual path do not rewrite it before release.

Environmental traps that Minecraft does not attribute to an attacker remain environmental incidents. They are deliberately given conservative market effects.

### Banker death

A Banker is only an interface to world-level accounts. Losing a Banker or an entire village never deletes player cash, savings, holdings, CDs, or villager-lending positions. Banker replacement is suppressed while the associated village is Extinct or Abandoned.

Each generated Bank retains one canonical Banker UUID. A null lookup or an entity in an unloaded chunk is not evidence of death and never authorizes a duplicate. Both loader death hooks stage an actual Villager or Zombie Villager death before general village-casualty accounting. On a later tick, the entity must be actually removed and its level must complete an entity-inclusive save before format 17 records a UUID-bound tombstone. Storage failure leaves replacement locked and retries in-session; shutdown drains pending removal barriers before clearing runtime state. After restart, only a matching format-17 tombstone created under this ordering authorizes one replacement. Minecraft saves the selected replacement entity before the economy atomically swaps the canonical UUID and consumes the tombstone. If rollback instead leaves the exact canonical entity alive, observing it cancels stale authority.

Zombification is a live conversion, not replacement authority, but Minecraft 26.2 gives the Zombie Villager a new UUID and gives a cured Villager another. Each loader persists an exact root/source/target `PREPARED` transaction before insertion. Startup synchronously filters the currently loaded entities into a stable marker snapshot; only processing of that filtered snapshot is bounded across ticks, and entity-load hooks handle later chunks. The durable transaction, not a generic region tag, gates all replacement. A typed target/source/root/retirement phase is written only after an entity-inclusive save of every active level and a post-save recheck finds the same exact participant state with no duplicate UUID across dimensions. Delayed target/source/root commits require that selected entity to be live. A restart cannot promote `PREPARED` from target presence alone because a predecessor may have rolled back in an unloaded chunk; it remains safely locked until positive lifecycle proof is available. An unresolved A-to-B infection followed by B-to-C cure retains A as root, B as immediate source, and C as target. Terminal Witch conversion retires the Banker lineage without making the Witch interactive. Format 17 is intentionally downgrade-sensitive: an older build rejects it as a future format rather than silently discarding lifecycle state. Format-16 tombstones are distrusted during migration because they predate the entity-removal save barrier.

Zero health during vanilla's revivable death animation is not removal proof. The entity must be actually removed before the following entity-inclusive save may create replacement authority; a zero-health entity saved during shutdown is restaged when it loads again.

## Village Prosperity Fund and restoration

Players may voluntarily and irreversibly transfer bank cash to the associated settlement through a separate Fund page. A contribution is a gift to a village-owned balance, not a player loan, debt, guaranteed investment return, or withdrawable account. The server owns and live-bounds the applied exact amount and requires a second matching contribution action inside its confirmation window.

Three contribution types are available when enabled:

- **Direct Grant:** enters bounded spendable value for the chosen purpose; non-restoration gifts place the configured share into an emergency reserve.
- **Endowment:** protects principal permanently and releases only a configurable annual payout, 4 percent by default.
- **Project Sponsorship:** follows the settlement's current unfinished project and funds its labor.

Purposes are General, Housing, Food, Infrastructure, Security, Trade, and Restoration. An abandoned or extinct village redirects a Direct Grant to Restoration. Ordinary Fund spending changes local inputs only and never writes market returns directly. The emergency reserve is available only for restoration, an acute food shortage, or low safety. A configured monthly treasury ceiling is converted into the per-day limit for routine releases.

Fast-track capital is enabled by default. Once the village has selected a valid project, player-origin capital may close its exact material, treasury, and development shortfall and buy its exact remaining labor without waiting behind the routine cap. Dedicated sponsorship is consumed first, followed by matching-purpose Direct Grant capital and then flexible General capital. The labor purchase is atomic: if the complete remainder is unavailable, no fast-track debit occurs and normal capped progress continues. Passive Endowment payout stays capped, the emergency reserve remains available only through routine crisis relief, and acute hunger must be stabilized before any project can use fast-track funding. The release cannot pre-fund a second project; surplus emeralds remain village-owned, and physical work still follows the loaded-chunk, paced-placement, and player-protection rules. Approved projects break ground progressively, but their final block remains gated on completed labor. Disable `fast_track_capital_enabled` for the fully throttled behavior.

Lifetime donor totals and titles are world-persistent recognition only. They grant no yield, economic advantage, permission, or ownership right. Abandoned villages still require the restoration threshold and a valid future recovery window before automatic recovery can resume. Player balances can never become negative.

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
- No forced chunks, including villages that pass the activation-radius check
- Periodic loaded-world census only
- Dimension-aware scans restricted to loaded server levels
- Horizontal physical-development activation: 256 blocks by default, configurable from 48 through 512, with Y ignored
- Every eligible site considered each construction pulse; one authored block operation per site
- Local entity and construction-theatre searches remain capped at 48 blocks; settler home assignment remains capped at 32 blocks
- One compact persistent record per known village
- Small incident and resident history limits
- Bounded project queue
- Fixed one-operation allowance per site every ten ticks (2/second at 20 TPS)
- Independent site allowances across loaded dimensions and nearby settlements
- Persistent exponential retry gates for obstructed sites
- Catch-up batch size adjusted for stored account and settlement counts
- Cached village fundamentals for snapshot lists
- A rebuildable in-memory spatial index with 64-block X/Z cells and per-dimension buckets for nearby and nearest-village lookups
- Exact three-dimensional distance filtering, deterministic tie behavior, and bounded fallback for general nearby and nearest-village lookups
- Physical-development candidates filtered separately by current dimension and horizontal distance to player positions
- No real resource mining for simulated output

Measured regressions exercise query correctness plus save and load at 100, 500, and 1,000 villages and accounts. The spatial index is rebuilt from authoritative records on load and updated after successful village observation; it is not stored separately.

For very large public servers, future storage partitioning may still be warranted, but single-player and ordinary multiplayer remain the primary beta target. Normal mutations still synchronously serialize the complete world economy, so persistence cost remains linear even though nearby lookup is indexed.


## 0.4 visible development catalog

The physical layer now uses ten intentionally small, deterministic project templates. The abstract layer authorizes, funds, and economically activates completed projects anywhere; their visual templates may trail behind in a bounded queue. A loaded village may materialize a bounded number of blocks only when it is one of at most 16 villages selected for the pass and lies within the configured horizontal activation radius of a same-dimension player.

| Need | Project | Primary visible/economic role |
| --- | --- | --- |
| Housing | Cottage, House, Village Inn | Adds housing and supports larger settlements |
| Storage | Warehouse | Improves trade and transport capacity |
| Production | Mine Entrance, Smithy, Granary | Improves mining, processing, or agriculture |
| Commerce | Market Square | Improves local trade and transport |
| Safety | Guard Post | Improves security output and recovery resilience |
| Finance | Exchange Hall | Late-tier civic finance landmark with bounded trade/transport benefit |

Project selection is need-driven. Safety emergencies can prioritize a Guard Post, low food reserves can prioritize a Granary, housing pressure can prioritize housing, and high-tier prosperous villages can eventually build an Exchange Hall. This keeps the same village from following an identical scripted build order every world.

The market link remains intentionally bounded and informational. The GUI reports whether local conditions are weak, neutral, positive, or strong, but it never exposes a formula that lets the player guarantee a future market return.
