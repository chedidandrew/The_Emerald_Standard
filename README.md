# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight villager banking, investing, commodity-exchange, and settlement-economy mod for **Minecraft 26.2**, with Fabric and NeoForge builds.

> Current source status: **unreleased `0.4.0-beta.3` development candidate**. The latest public download remains the older [`0.4.0-beta.2` prerelease](https://github.com/chedidandrew/The_Emerald_Standard/releases/tag/v0.4.0-beta.2), which does not contain the Unreleased beta.3 features documented below. Automated and hands-on evidence for beta.3 must be recorded against its exact candidate commit; the manual matrix is still `Not run`. Normal gameplay is centered on Banker villagers, Village Banks, the graphical bank dashboard, and the optional Village Prosperity System. Commands are reserved for administrators and diagnostics.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** Villager business lending can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Quick start

1. Back up the world before installing or upgrading this beta.
2. To test this source candidate, build or obtain exactly one `0.4.0-beta.3` playable JAR from the same exact commit, then install that same Fabric or NeoForge JAR on the server and every connecting client. Never install both loader JARs, do not use a `-sources.jar`, and do not mix builds from different commits. The linked public `0.4.0-beta.2` artifacts are older and do not include the Unreleased features described by this README.
3. Enter a loaded Overworld village. The mod searches periodically for a safe Village Bank site and supplies a Banker even when terrain prevents a structure.
4. Right-click the Banker or an Exchange Desk to open the eight-page dashboard.
5. On **Account**, choose an amount and deposit physical emeralds into bank cash. Use **Savings** for a safe liquid return, or choose a CD, villager lending position, commodity exchange, or market asset according to its displayed risk.

With `onboarding.join_hint_enabled=true`, each player receives one **Starter Handbook** in each world alongside the one-time first-join discovery hint. The handbook is placed in the player's inventory without opening itself, so joining is never interrupted. Delivery is recorded only after the inventory accepts the book; if the inventory is full, nothing is dropped and the mod retries on that player's next join.

The handbook uses colored headings, compact diagrams and recipe layouts, hover explanations, and clickable contents and navigation links. It covers the first deposit, every dashboard page, safe and risky products, commodity exchange, Village Prosperity and safety, the Fund, physical construction, economic time, operator debugging, transaction recovery, troubleshooting, crafting, and a finance glossary. A lost copy can be replaced with the shapeless **Book + Emerald** handbook recipe; obtaining either ingredient reveals that recipe. The first successful Banker visit still awards the advancement **The Emerald Standard** and gives a short deposit and risk explanation. Normal play and discovery remain command-free.

## Banking and investing

When a player discovers a loaded Overworld village, The Emerald Standard can establish a detailed civic **Village Bank and Exchange** nearby. The larger biome-aware building has a sealed stepped roof, sheltered entrance, storage and service furnishings, one dedicated Exchange Desk workstation, and a persistent villager with the registered Banker profession. New banks first search 48 deterministic sites out to 82 blocks from the stable village center. If that produces a Banker-only village, a persisted recovery search expands to 96 sites out to 128 blocks while any player remains within 192 horizontal blocks. Proven-safe lots wholly outside vanilla village POI influence rank ahead of interior lots, then nearest-first; equal sites favor the Bank's fixed north-facing entrance looking back toward the village. The search uses only already-loaded chunks, so walking around the outskirts naturally reveals more candidates without force-loading terrain. Banks accept dry natural lots within the four-block foundation tolerance, level their floor above the highest sampled surface, bridge only the resulting shallow gaps with biome-matched foundations, and use a three-block-wide landing and stair approach that descends to natural grade when the entrance is raised. All three stair lanes are sampled independently and must agree on one safe row plan, preventing a cross-slope from leaving part of the staircase floating. Their tall green panes are authored with the connection axis of their wall. Every block is preflighted through the cooperative protection API and verified before generation is recorded. Replaceable plants and snow may be cleared, but solid construction, block entities, protected placements, paths, and farmland are rejected rather than overwritten. If no site is safe yet, the mod uses an untouched unemployed adult villager or spawns a persistent fallback Banker; the same scoped villager is moved and bound to the Exchange Desk after a later Bank succeeds. Established villagers with professions, XP, trades, custom names, or other player investment are never repurposed.

New settlements receive stable per-village bank identities even when two villages share the same legacy 256-block region. Existing bank-region associations and anchors remain authoritative for upgraded worlds. Village Bank generation is intentionally Overworld-only in this beta; Village Prosperity records may exist in other dimensions.

Format 17 introduced save-ordered, fail-closed generated-Banker lifecycle changes, which format 18 retains. A death becomes replacement authority only after a later server tick observes the entity actually removed and an entity-inclusive Minecraft save succeeds. Infection, cure, and terminal conversion first persist an exact root/source/target transaction before the new entity can be stored; after an entity-inclusive save of every active level, a typed durable outcome atomically commits the successor, the saved immediate source, the live root, or retirement. A merely loaded successor after restart cannot prove that a remote predecessor was removed, so an unresolved `PREPARED` transaction remains locked instead of risking duplicate Bankers. Exact lineage markers aid loaded-entity recovery but are not the sole authority. Unresolved infection-to-cure chains retain the original root and immediate source, a successor's real death retires that lineage, and ambiguous successors fail closed. **Known beta limitation:** an abrupt crash inside a short cross-file lifecycle boundary—such as after Minecraft saves a conversion/removal but before economy state records its durable phase, including a canonical-death handoff—can leave that Bank locked until an exact participant produces a later positive lifecycle event; if none does, restore the pre-crash world backup. This conservative liveness gap is not the intended stable-release behavior. Format 15 added a frozen, versioned entrance approach for every physical prosperity building. New lots are accepted only when the road-facing entrance can reach the sampled grade with a continuous one-to-four-step descent; completed projects from older saves receive the same guarded one-time planning pass. Supports, headroom, step count, and placement cursor are persisted independently from the building and road, and an obstruction or protection veto waives the unfinished approach instead of overwriting or repeatedly repairing player changes. Format 14 added the equivalent bounded one-time center-surface migration for older modular roads. Its frozen target and cursor are independent of ordinary road construction progress, and every successful scan advance is saved immediately so completed coordinates are never treated as an ongoing repair service. Format 13 added pending replacement state, bounded append-only histories of retired project lots and Bank anchors, and one canonical managed-Banker UUID plus its last durably assigned Bank anchor per generated Bank, while retaining independent authored Bank structure versions: version 6 is current for new and safely relocated replacement Banks, while intact version-2, version-3, version-4, and version-5 Banks remain frozen compatibility structures and are neither reshaped nor retro-lit automatically. A matching unversioned 13x11 Bank from an earlier save can receive the same terrain-aware entrance once: the complete addition must be loaded, unobstructed, dry, and protection-approved, and its recognized bank workstation (Exchange Desk or scoped legacy lectern), counter, doorway, threshold, and roof signature must survive. Completion is recorded only after placement succeeds and Minecraft's synchronous chunk-save barrier finishes; `/save-off` defers the retrofit. This ordering narrows the crash window but remains subject to the documented cross-file and Minecraft chunk-write limits. Existing green panes can have their connection state corrected without recreating a missing or differently colored pane.

Right-click a Banker or any Exchange Desk to open the dashboard. Exchange Desks are craftable, face the player when placed, and appear under Creative inventory's Functional Blocks tab and search; an unscoped desk or naturally employed Banker uses the nearest managed settlement in the same dimension within 160 blocks for its Village and Fund pages, while the player's financial account remains globally available. If an active generated Bank is partially modified, its scoped Banker is suspended but its surviving Exchange Desk remains available as unscoped personal banking instead of becoming inert. A normally broken desk drops itself. If one is lost, craft its affordable replacement with an emerald above a leather-book-leather row and any three planks on the bottom row; acquiring either an emerald or book reveals the recipe. Replacing the only missing workstation restores managed operation when the rest of the Bank is intact. Managed Bankers keep their registered profession and generated-desk job-site memory across ordinary work cycles and reloads. Lectern counters from older worlds remain valid only at their persisted Overworld bank locations, and workstations at retired Bank coordinates remain inert.

For lifecycle authority, zero health during vanilla's revivable death animation is not enough: the entity must be actually removed before the following entity-inclusive save can authorize replacement.

The dashboard has eight pages:

- **Account:** net worth, inventory emeralds, bank cash, savings, total contributions, realized and unrealized performance, current economic day and market regime, and personal net-worth history.
- **Market:** a previous/next carousel for VILX plus eight Minecraft-themed businesses, with holdings, allocation, average purchase price, cost basis, chart, market bulletin, buy, and sell actions for the selected investment.
- **Banking:** separate Transfers, CDs, and Villager Loans views. Transfers explicitly route Inventory -> Bank Cash, Bank Cash -> Inventory, Bank Cash -> Savings, or Savings -> Bank Cash; term-product views show source and projected balances for up to eight independently selectable positions of each type.
- **Trade:** diamonds, gold, netherite materials, valuable ores, and blocks converted into bank cash at dynamic commodity prices. Vanilla item and emerald icons make the conversion direction visible, and all 18 supported resource forms derive chart history from the same canonical commodity inputs and conversion formula as their live quote.
- **Village:** local population, housing, prosperity, safety, supplies, production, development tier, current project, backlog, lifecycle, incidents, and restoration status.
- **Fund:** voluntary Direct Grants, protected-principal Endowments, and Project Sponsorships for the associated settlement.
- **Activity:** lifetime bank inflow and withdrawals plus a mouse-wheel and arrow-scrollable view of the player's complete retained transaction ledger, newest first, with cycling category filters.
- **News:** one deterministic local headline, fact-based article, Minecraft item visual, and wrapped prosperity/safety next steps derived only from the synchronized settlement snapshot—never external AI or invented events. Disabled simulation or visual construction is reported as paused instead of inventing activity.

Transactions and Prosperity Fund contributions accept an exact typed whole-emerald amount from `1` through `1,000,000`, with Apply, Cancel, Enter, and `All` controls. The server validates every applied amount again and caps it against the live source; the Fund's applied contribution amount remains server-owned. Risky, destructive, and irreversible actions use a time-limited two-step confirmation owned by the server rather than trusted client state.

The selected amount and each action's source, destination, and projected balances are shown in the page or its tooltip. Supporting explanations wrap within compact hover tooltips, and the dashboard grows or shrinks within safe bounds to use the current logical window. Custom labels retain Minecraft's native glyph size to match button and amount-entry text while their anchors and available widths follow the responsive panel. Account balances expose exact values on hover, and disabled or state-dependent controls explain whether a CD is mature or early and why a Fund purpose is fixed. Savings must first transfer to Bank Cash before it can be withdrawn to inventory or invested.

Portfolio accounting persists share cost basis, average purchase prices, total contributions and withdrawals, realized and unrealized gains, allocation, a bounded transaction ledger, and personal net-worth history. Market, commodity, and personal histories retain up to five economic years and can be viewed as 30 days, 90 days, one year, or all retained history.

## Investments

- `VILX` Villager Exchange Index
- `RSDN` Redstone Dynamics
- `DPMN` Deepdelve Mining
- `NSPC` Nether Spice Company
- `ENDR` Ender Freight and Logistics
- `GLDH` Golden Harvest Cooperative
- `POTN` Potionworks Laboratories
- `IRNG` Iron Golem Security
- `MCRT` Minecart Transit

The global simulation includes expansion, bull, boom, stagnation, recession, crash, and recovery regimes. Rare events create company- and commodity-specific shocks when enabled. By default, prices, rates, lending outcomes, commodities, and settlement fundamentals continue to evolve while the world is closed; a world can disable trusted wall-clock progression without pausing ordinary server-tick or commandable Overworld-clock progression. Forward `/time` jumps are recognized, overlapping clock movement is counted once, and a backward clock change only rebases the observation point. The last observed Overworld-clock value is persisted, so a command jump saved by Minecraft before the next economy tick is recovered on restart.

## Village Prosperity System

The 0.4 beta expands visible settlement progression to **10 curated village projects** with need-driven priorities, immutable authored Blueprint V2 architecture, biome-aware materials, a stable village-wide visual character, and bounded sector effects. Threatened villages can prioritize defenses, food-poor villages can prioritize storage, crowded villages can prioritize housing, and mature villages can grow into markets, smithies, inns, and an Exchange Hall.

Village Prosperity connects the market to persistent local settlements without turning villagers into expensive autonomous agents. Once a village is known, its compact economic record can advance while every player is elsewhere and through trusted offline catch-up on the next server start. This data-only work does **not** force-load chunks, pathfind villagers, mine real ores, simulate raids while offline, or place structures during startup.

Each managed village receives a stable identity and tracks:

- Population and observed residents
- Housing and food supply
- Material supply and treasury
- Prosperity and safety
- Farming, mining, trade, redstone, alchemy, transport, and security output
- Development tier
- Resident status and incident history
- Development projects and physical construction progress

The curated project catalog contains **Cottage, House, Village Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall**. The Village page distinguishes economic Planning from physical Building. Approved physical projects enter the bounded visual queue immediately, establish their supports and first visible work, then reveal more authored blocks as planning advances. The project counts economically only when planning reaches 100 percent, and its final block is withheld until then. Building advances only while a player is within the same-dimension horizontal activation radius and the required chunks are already loaded. That radius defaults to the recommended 256 blocks, can be configured from 48 through 512, ignores Y separation, and never force-loads chunks.

Each village persists one of four architectural characters and a biome-derived material dialect so its projects read as one settlement. New format-18 approvals use `blueprint_v2`: the active catalog contains 52 immutable revision-4 masters, selected for the requested project type without recombining floorplans, roofs, frontages, or interiors. Every role has at least five compact, established-village, and grand alternatives; Cottages and Houses each have six masters. The scale mix is nine small, 18 medium, 14 large, and 11 landmark plans, ranging from tiny roadside buildings through longhouses, split-wing homes, taverns, wharf stores, stilt granaries, corner forges, drift mines, market lanes, and gatehouses to orchard estates, tower courts, courtyard inns, warehouse basilicas, silo complexes, foundries, quarries, bazaars, citadels, and a monumental bourse. A deterministic tier-aware shuffle bag favors compact forms in young settlements, introduces large buildings at tier 2, strongly favors large or landmark plans at higher tiers, and avoids an immediate repeat whenever another eligible template exists. Three semantic material palettes and three bounded dressing kits vary the surface treatment without replacing that structural identity; mirroring occurs only when that specific template revision declares it safe. The selected template ID and revision (and therefore its scale), palette, dressing, mirror state, rotation, and canonical plan hash are save-stable so a reload cannot reroll or silently reinterpret the building.

After a safe lot and stable road anchor are chosen, the building rotates to face that connection. Its architecture identity, rotation, shallow foundation supports, terrain-matched entrance descent, and current visual stage remain stable across reloads. A finished building receives its entrance approach before the potentially much longer public road: the planner follows the frozen center route around immediate turns, supports each additional stair from below, preserves two blocks of headroom, and rejects a new lot unless the complete approach is safe. Town- and city-stage additions are append-only and preflighted before their persisted target stage changes, so a later tier decline never removes earlier work. The three-block-wide road has a dirt-path center with deterministic gravel accents plus coarse-dirt shoulders through its turns and endpoints, with its own persisted cursor and completion state. It advances only through loaded chunks after the building is complete, adopts existing TES-style path cells, and skips unsafe, protected, occupied, or non-terrain cells without changing the building's bounds, completion, integrity, or economic authority. New roads never plan coarse dirt in the center. Older modular roads keep their exact frozen construction plan until complete, then a separate versioned pass scans every historically coarse center coordinate once without changing the ordinary road total or cursor. Dirt path and gravel are accepted, only surviving coarse dirt is eligible for replacement, and protected, occupied, changed, unsafe, or unloaded cells are preserved or deferred under the existing guards.

The beta hardening rules include:

- Recovery with visual progression enabled queues real settlers first. An empty village does not resume production or market influence until those settlers actually materialize and are observed.
- Threatened or Devastated settlements with living survivors can enter Recovering after the seven-day stabilization window once safety and prosperity meet the minimums, giving them a bounded route back to growth.
- Simulation-only mode can recover abstractly because no physical representation is requested.
- Physical settler spawning requires real available beds. With visuals enabled, ordinary population growth creates committed settlers that count in the economic simulation immediately and remain queued until matching villager entities can materialize and be observed. Empty-village recovery remains the safety exception described above: it waits for real settlers before restarting production.
- Long-absent residents move from Active to Away and eventually Emigrated instead of remaining productive forever.
- Zombie villagers are tracked as Infected using persisted village tags plus nearest-resident reconciliation. Infection suspends that resident from productive population, and a cured villager observed near the infection site is reconciled back into the settlement.
- Functional development tiers can fall after collapse even though completed physical buildings remain.
- Villages pay maintenance, lose a small amount of stored food to spoilage, and experience rare local positive and negative shocks, so prosperity is not a one-way ladder.
- Development structures inspect 64 deterministic candidates out to 84 blocks, use a conservative natural-ground whitelist, accept at most four blocks of terrain variation, place their floor above the highest sampled surface, and bridge only bounded air gaps with deterministic foundation columns. Solid construction, block entities, paths, farmland, and protection-vetoed placements are never overwritten. Replaceable vegetation or snow can be cleared. Best-effort roads may pave only dirt-like or sandy ground; stone-family and snow blocks are skipped. Without a registered protection guard, Minecraft still cannot identify player-placed dirt, grass, podzol, mycelium, or sand as player-owned. Unsafe candidates are discarded before reservation so the search can try another lot. Once a reservation is persisted, an ordinary later obstruction or unloaded boundary retains that exact site and retries with backoff. The small project cursor/chunk crash window remains documented below because forcing a global Minecraft chunk save for every two-block pulse would stall normal play.
- Every new modular lot must also have a loaded, dry, protection-approved entrance route that can descend continuously to its frozen road line within the same four-block terrain limit. The approach is saved before its first placement and built immediately after the structure completes. Older completed modular structures are inspected once; an occupied or modified route is preserved and permanently waived rather than cleared, repaired, or retried as an ownership guess.
- Village Bank and prosperity construction expose `VillageDevelopmentProtection.register(PlacementGuard)` for claim/protection integrations. Every guard is consulted before placement and exceptions deny the placement; a claim mod must register a guard for its rules to participate.
- Obstructed projects retry with persistent exponential backoff. Exact project bounds prevent overlap, unstarted projects may relocate, and a partially built persisted recipe keeps its site for safe continuation after restart.
- Legacy template expansions and modular town/city stages preserve every already-materialized placement as an immutable prefix. Every appended structural position must be empty or replaceable, free of block entities, and accepted by the protection hook before the upgrade suspends benefits or changes its persisted bounds. Modular roads are non-authoritative, three-block-wide public infrastructure: safe dirt-like or sandy cells are paved, existing TES-style paths are adopted, building envelopes are excluded, and protected, occupied, stone-family, snow, or other non-soil cells become harmless gaps without disabling the building. Without a registered claim guard, Minecraft cannot distinguish player-placed dirt, grass, podzol, mycelium, or sand from matching natural terrain.
- Curated structures use role-specific footprints and rooflines, continuous roofs, clear residential entrances, and a limited set of theme-appropriate vanilla utility and job-site blocks; newly generated Village Banks author only their single Exchange Desk job site. Every observable Blueprint stage and every current Bank biome palette must pass a conservative authored-plan projection of block light at least 7, without skylight, on roof-covered Zombie-spawn-valid floors with two clear standing cells. This is not a recorded live-engine measurement or a guarantee against every mob's special spawning rules. A shared read-only attachment gate also rejects standing lights without center-bearing floor support, wall lights without sturdy backing, and hanging lanterns whose complete vertical chain does not terminate at a sturdy ceiling; this admission check never repairs a fixture removed later by a player.
- Active villager professions provide small sector-specific output bonuses capped at 12 percent. Nearby residents show bounded work particles, looks, and arm swings while construction advances; these are visual cues, not autonomous custom AI.
- Economic completion activates a project's housing and production effects even while its physical template is still queued. A low-frequency integrity audit suspends every damaged structure without repairing or overwriting the player's changes. Ordinary partial damage remains unsafe until the authored blocks are restored. Severe demolition—at least 12 authored mismatches and at least 30 percent of the structure—retires the old site and queues the same project at a different safe lot; the edited remains are left untouched. Active and retired project bounds are shared across settlement and Bank searches, and a reservation joins the same-pulse exclusion set immediately. Safe append-only design-stage upgrades still use the guarded construction queue.
- Construction activity can issue an occasional low-speed, one-shot navigation request to at most two nearby suitable residents, in addition to bounded looks, arm swings, and particles. Entity and construction-theatre searches keep their local 48-block cap even if the development radius is raised; a new settler's assigned home radius is separately capped at 32 blocks. These rules do not install a persistent AI goal or control economic progress.
- Physical census and construction are dimension-aware, select by horizontal distance from loaded players, consider at most 16 eligible villages per construction pass, share a rotating global block budget, and operate only in already-loaded chunks. Offline catch-up batch size adapts to the number of stored settlements and accounts.
- Settler placement checks food, beds, fluids, support, and entity collision. The pending-settler queue changes only after a later authoritative census observes the arrival.
- Village centers prefer a nearby bell when one exists. Persisted villager tags are also preferred when resolving an already-known settlement, reducing identity drift and accidental merging as villagers move around. Once a settlement is known, Bank keying and plot search use that persisted stable center rather than the discovering player's moving position.
- Player-caused projectile deaths are attributed through projectile ownership when Minecraft exposes the owner.
- The first player-caused casualty captures the village's exact pre-damage state and market contribution as a persistent counterfactual. That no-player-damage branch advances and is re-priced on each enabled simulation day, and genuine non-player casualties are applied to it. Repeated player hits do not recapture or rebase it; the damage cooldown and full recovery are both required before the live village replaces it.
- The Village dashboard reports the cause and age of the latest local incident when restoration status is not displayed.
- The persisted bank key scopes Banker replacement and routes the Village dashboard plus Prosperity Fund and restoration contributions to that exact associated settlement.

## World configuration

The first server start creates `the_emerald_standard-config.properties` in the world's `data` directory.

The full [configuration reference](docs/CONFIGURATION.md) lists every setting, default, accepted range, interaction, and safe reload behavior. Unknown keys and invalid values are rejected as a whole, so a failed `/emerald config reload` leaves the previous configuration active. The one-time per-player onboarding package—Starter Handbook plus discovery message—is controlled by the existing `onboarding.join_hint_enabled` setting; market events and trusted offline progression have independent world controls.

Village Prosperity can be configured independently:

```properties
onboarding.join_hint_enabled=true
market.events_enabled=true
economic_clock.offline_progression_enabled=true
economic_clock.max_offline_days=25000
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

Typical combinations:

| Simulation | Visual progression | Result |
|---|---|---|
| On | On | Full local economy plus visible development |
| On | Off | Local economies progress without placing prosperity structures or settlers |
| Off | On | Loaded villages may finish visual development without affecting the simulated market |
| Off | Off | Original banking, investing, and global economy only |

`market_integration_enabled` independently controls whether village fundamentals influence assets and commodities. `automatic_recovery_enabled` independently controls automatic recovery from recoverable extinction. Fund switches can disable contributions, endowments, sponsorships, targeted purposes, visible donor titles, or fast-track capital separately. The default endowment payout is 4 percent annually; endowment principal itself is never spent. Non-restoration Direct Grants reserve 20 percent for emergencies by default. Routine Fund releases and passive Endowment payout use the configured monthly cap. Player-origin liquid Direct Grant capital and dedicated Project Sponsorships may instead atomically cover the exact input gap and remaining labor for one active project. They leave every surplus emerald in the Fund, never consume the emergency reserve, and never bypass paced, protected block placement.

Construction is deliberately bounded. An approved physical project breaks ground immediately and reveals more of its authored structure as labor advances; its final block remains withheld until economic completion. Defaults are two blocks every ten server ticks, one active visual project per settlement, the recommended 256-block horizontal activation radius, and at most 16 eligible villages considered per pass. Larger values are intended only for servers that already keep the relevant village chunks loaded: matching the radius never force-loads a chunk. The wider activation range also does not widen 48-block entity/theatre searches or the separate 32-block settler-home cap.

Administrators can inspect or reload configuration without restarting:

```text
/emerald config show
/emerald config reload
```

## Reliability and persistence

- One unified economic-time accumulator advances from the largest forward wall-clock, server-tick, or Overworld-clock delta, recognizing `/time` jumps without double-counting overlapping clocks.
- Up to 1,825 economic days of investment, commodity, and personal net-worth history are persisted.
- Save format 18 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.
- Unsupported future formats stop loading instead of silently falling back to stale backups.
- Corrupt current-format saves can recover from a validated backup.
- Inventory-linked deposits, withdrawals, and exchanges use a durable recovery journal.
- A completed item transfer checkpoints only the affected player's synchronized NBT file and verifies the complete persisted inventory before releasing its journal.
- Whole-economy replacement saves cache the SHA-256 identity of the last validated generation, avoiding a redundant full parse only when the old file's exact bytes are unchanged.
- Overflow recovery remains journal-protected instead of dropping recoverable value into the world.
- Player accounts are world-level and are never owned by one Banker or one village.
- Share cost basis, realized performance, contributions, a 256-entry transaction ledger, up to eight CDs, and up to eight villager-lending positions are world-persistent.
- Village-owned Prosperity Fund balances, protected endowment principal, project sponsorships, bounded contribution history, and donor recognition are persistent and never become player debt.
- Village identities, residents, incidents, full pre-player-damage village counterfactuals, projects, exact active and retired project bounds, relocation state, village architectural character and biome dialect, project architecture schema, legacy modular recipe fields, Blueprint template ID and revision, semantic palette, dressing kit, mirror state, rotation, canonical plan-hash version and hash, frozen visual stage, stable road anchor, independent building/road progress, one-time road-surface migration version/target/cursor, frozen entrance-approach version/size/cursor/completion, construction retry state, active and retired Bank anchors, one canonical Banker UUID and last assigned anchor per generated Bank, explicit Banker-only fallback provenance, authored Bank structure versions, and lifecycle state are persistent.
- A configurable transaction cooldown protects servers from repeated button or packet spam.

The 0.4 line first upgraded beta.4 format-7 worlds to format 8 for the expanded project catalog. Beta.2 advanced those worlds to format 9 for portfolio analytics, multiple term positions, commodity and personal history, Prosperity Funds, and donor records. Format 10 added modular village architecture, format 11 explicit Banker-only fallback provenance, format 12 authored structure versions for anchored non-fallback Banks, format 13 relocation state plus bounded retired-site histories and canonical Banker UUIDs, format 14 independent one-time modular-road center resurfacing, format 15 frozen entrance approaches, format 16 generated-Banker death tombstones, format 17 typed conversion transactions plus entity-removal-first death authority, and the current format 18 adds immutable Blueprint V2 identity and plan hashes. Each retired-site history retains at most 16 entries; after that safety bound is reached, the mod leaves further damage unsafe instead of forgetting an old player-edited site. Projects read from format 9 or earlier remain `legacy_v1`, and projects already saved under formats 10 through 17 remain `modular_v1`; neither group is rerolled, repositioned, or converted. Only projects approved after the world is running format 18 use `blueprint_v2`. Within that schema, revision 4 is active for new approvals, while every shipped revision-1, revision-2, and revision-3 descriptor and authored plan remains available to reproduce saved projects at their original envelopes and canonical hashes; they are never silently upgraded or returned to the new-project shuffle bag. Formats 10 and earlier never infer retryable fallback status from an ordinary Bank anchor, preventing ambiguous legacy or crash-interrupted markers from creating a duplicate structure. Banks read from format 11 or earlier deliberately begin without a trusted structure version; only a successful signature-guarded in-world upgrade records version 2. Format-14 modular projects migrate with an explicitly pending one-time entrance check; legacy, abstract, and unreserved projects do not invent physical work. Format-15 input cannot invent a confirmed Banker death. Format-16 tombstones predate the entity-removal save barrier and are deliberately discarded during migration rather than trusted as duplicate-creating replacement authority; this safe choice can require manual recovery of a genuinely dead pre-release Banker. Format-17 input receives empty Blueprint fields rather than guessed metadata, preserving every existing construction cursor and historical recipe exactly. Older holdings still receive an explicitly inferred opening cost basis because their historical executions were not recorded. Older builds reject format 18 instead of silently discarding its fields or falling back to stale data. Keep a pre-upgrade world backup if you may need to downgrade.

Materialized projects and versioned generated Banks are audited gradually. Partial edits make the structure unsafe and suspend its authored benefits or scoped Banker without repairing it or creating a duplicate; an Exchange Desk surviving at the active Bank still opens unscoped personal banking. Restoring every authored block lets a later audit reactivate managed operation. Severe demolition retires the old bounds or anchor and searches elsewhere, while old blocks and old Exchange Desks remain untouched and non-operational. A Bank replacement reuses the one persisted canonical Banker when that entity is available and never creates another merely because it is unloaded. Append-only template upgrades remain guarded and never overwrite solid player blocks, block entities, protected placements, or unloaded chunks. Economy markers/cursors and Minecraft chunk saves are still not cross-file atomic. A crash can leave a non-provenanced Bank marker without its structure, or a project cursor ahead of a chunk write; the latter is conservatively treated as damage rather than auto-repaired because the mod cannot prove a player did not remove the block. Fallback Banker access remains available for an ambiguous Bank, but the structure is not automatically rebuilt. Player financial data remains world-level and intact.

## One-command debug flight recorder

Mod testing does not require memorizing a diagnostic command tree. An operator can run:

```text
/emerald debug
```

The command enables a full five-minute capture of the testing player's banking actions, market changes, watched village state, construction, settlers, persistence state, validation warnings, and performance sampling. Running the same command again stops early. `/emerald debug <1-15>` selects a duration, and `/emerald debug mark` adds an optional numbered moment marker. Only the operator who started a capture may mark or stop it.

The capture is written incrementally for crash resilience and then packaged under the world's `data/the_emerald_standard_debug` directory as a shareable `TES-debug-*.zip`. Reports exclude the private economy seed, world seed, chat, server address, resident UUIDs, and unrelated player or settlement data. Timing fields distinguish sampling, active recorder ticks, writes, snapshots, and full-state copies instead of presenting overlapping measurements as a subsystem profile. Interrupted captures are packaged automatically on the next server start.

## Administrator commands

Normal gameplay does not require commands. The `/emerald` tree requires permission level 2 and is intended for administration, diagnostics, configuration, and recovery.

```text
/emerald open
/emerald market
/emerald commodities
/emerald portfolio
/emerald recover
/emerald config show|reload
/emerald deposit <emeralds>
/emerald withdraw <emeralds>
/emerald savings deposit|withdraw <emeralds>
/emerald buy <ticker> <emeralds>
/emerald sell <ticker> <shares>
/emerald cd open <emeralds> <30|90|180|365>
/emerald cd close <position-id>
/emerald loan fund <emeralds> <30|90|180|365>
/emerald loan collect <position-id>
/emerald exchange <resource> <count>
```

## Installation

### Fabric

Install Minecraft 26.2, Fabric Loader 0.19.3 or newer, Fabric API 0.158.0+26.2 or newer, Java 25, and the Fabric JAR from a verified workflow artifact or prerelease.

### NeoForge

Install Minecraft 26.2, NeoForge 26.2.0.72 or newer, Java 25, and the NeoForge JAR from a verified workflow artifact or prerelease.

The mod must be installed on the server and on every connecting client because it adds a custom graphical menu.
Use the same exact build on the server and every client; the Banker menu's synchronized slot layout is part of the network protocol.

## Building

Minecraft 26.2 requires Java 25. Each loader project includes a pinned Gradle 9.5.1 wrapper.

```bash
bash scripts/run-common-tests.sh
bash fabric/gradlew --no-daemon -p fabric build
bash neoforge/gradlew --no-daemon -p neoforge build
```

## Verification

GitHub Actions runs the common economy, persistence, and Village Prosperity regression suites, builds and inspects both packaged JARs, launches both dedicated-server environments, and launches both clients under a virtual display to verify initialization and screen registration.

Hands-on visual, transaction, terrain, raid, recovery, and multiplayer checks remain part of the beta test plan. Record exact-commit results with the repository's [manual beta test form](https://github.com/chedidandrew/The_Emerald_Standard/issues/new?template=manual_beta_test.yml); a failure or ambiguous result should include the ZIP from `/emerald debug`.

## Documentation

- [Debug flight recorder](docs/DEBUGGING.md)
- [World configuration](docs/CONFIGURATION.md)
- [Village Prosperity System](docs/VILLAGE_PROSPERITY.md)
- [Banker GUI and village banks](docs/GUI_AND_VILLAGE_BANKS.md)
- [Economy model](docs/ECONOMY.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Inventory transaction recovery](docs/TRANSACTION_RECOVERY.md)
- [Testing and publication gate](docs/TESTING.md)
- [Structure Gallery review world](docs/STRUCTURE_GALLERY.md)
- [Mod / vanilla village comparison world](docs/VILLAGE_COMPARISON_GALLERY.md)
- [Release procedure and checksum gate](docs/RELEASING.md)
- [Build status](release/BUILD_STATUS.md)
- [Change history](CHANGELOG.md)

## License

MIT. See [LICENSE](LICENSE).
