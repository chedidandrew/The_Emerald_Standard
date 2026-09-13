# 0.4 Manual Beta Test Matrix

This document is the human-play evidence gate for The Emerald Standard 0.4 and later stable releases. Automated CI validates deterministic logic, persistence, loader compilation, packaged JAR contents, and client/server startup. It does not claim that a screen feels clear, terrain placement looks natural, or multiplayer play remains enjoyable for hours.

## Evidence rules

- Test both Fabric and NeoForge on Minecraft 26.2.
- Record the exact mod commit, loader version, world origin, test date, and tester.
- Start `/emerald debug` before safely reproducing a failure or ambiguous result, then attach its ZIP.
- Never mark a row passed from source review alone.
- Retest all financial safety rows after any persistence, menu, transaction, or migration change.
- A stable release requires every Critical row to pass on both loaders.

## Build identity

| Field | Fabric | NeoForge |
| --- | --- | --- |
| Commit | Unverified | Unverified |
| Mod version | 0.4.0-beta.15 development candidate | 0.4.0-beta.15 development candidate |
| Source fingerprint | Record report footer / debug capture | Record report footer / debug capture |
| Loader version | Unverified | Unverified |
| Tester | Unverified | Unverified |
| Date | Unverified | Unverified |

## Live market and clock commands (beta.15)

Automated/native fixture results are in [the candidate review](reviews/2026-09-12-beta15-live-market.md).
The following extended human-play checks remain separate; do not infer a pass from those fixtures.

| Test | Fabric | NeoForge | Required evidence |
| --- | --- | --- | --- |
| Two real clients observe the same live quote and execution price (allowing screen latency and spread) | Not run | Not run | Both screenshots, order receipt and debug ZIP |
| Today grows across a normal session; Yesterday remains the completed prior session | Not run | Not run | Session-start/end screenshots |
| Day 23 midnight reset to zero advances to economic Day 24 dawn; repeat causes no extra day | Not run | Not run | Clock/market before and after commands |
| Named day/night, numeric add, sleep and rapid command-block sequences never rewind the economy | Not run | Not run | Command sequence and debug ZIP |
| Ten-year and All views clearly distinguish retained/sampled history and new-listing limits | Not run | Not run | Copied long-running test world, screenshots |
| Migration/restart preserves cash, holdings, current quote and partial session | Not run | Not run | Backup, before/after balances and history |

## Critical financial and persistence checks

| Test | Fabric | NeoForge | Required evidence |
| --- | --- | --- | --- |
| Fresh-world bank discovery without commands | Not run | Not run | Screenshot, world seed, debug ZIP |
| Deposit and withdrawal preserve exact value | Not run | Not run | Before/after inventory and account values |
| Banker packet sync preserves exact small, large, and signed values | Not run | Not run | Display/server comparison at 1, 10, 100, and 32,768 emerald boundaries plus a realized or unrealized loss |
| Full inventory withdrawal recovery | Not run | Not run | Debug ZIP and reconnect result |
| Disconnect during deposit preparation | Not run | Not run | Debug ZIP and exact recovered value |
| Disconnect after bank commit | Not run | Not run | Debug ZIP and exact recovered value |
| Simulated player-data write failure retains the journal until a verified reconnect recovery | Not run | Not run | Test-world-only write failure, server log, restored write access, and exact recovered value |
| Buy, partial sell, and sell-all accounting | Not run | Not run | Transaction sequence and final balance |
| Cost basis, average price, allocation, realized/unrealized gain, contributions, and ledger | Not run | Not run | Hand calculation and before/after screenshots |
| Savings deposit and withdrawal | Not run | Not run | Before/after values |
| CD open, maturity, and early close rules | Not run | Not run | Economic days and payouts |
| Eight independent CDs and position-specific closure | Not run | Not run | All position IDs, terms, and unaffected balances |
| Villager lending repayment and default | Not run | Not run | Both outcomes and no negative player balance |
| Eight independent lending positions and position-specific collection | Not run | Not run | All position IDs, outcomes, and unaffected balances |
| Resource exchange quote and inventory mutation | Not run | Not run | Quote, count, and balance |
| All Fund types and purposes, exact typed amount controls, and server confirmation | Not run | Not run | Funding ledger, packets, and village-owned balances |
| Endowment principal, payout, emergency reserve, and spending cap | Not run | Not run | Multi-day balance trace and configuration |
| Village donation and restoration | Not run | Not run | Funding result, donor recognition, and village state |
| Death, reconnect, and server restart preserve account | Not run | Not run | Three checkpoints |
| Upgrade from 0.3.0-beta.3 | Not run | Not run | Backup hash, migration log, account comparison |
| Upgrade from 0.3.0-beta.4 | Not run | Not run | Backup hash, migration log, account comparison |
| Upgrade from format 8 to format 9 | Not run | Not run | Position migration, inferred basis flag, histories, and Fund defaults |
| Upgrade a format-9-or-earlier architecture fixture | Not run | Not run | Existing unstarted, partial, and completed projects remain `legacy_v1` at the same origin, bounds, order, and progress; no reroll or conversion; a project approved only after the current format is active persists as `blueprint_v2` |
| Upgrade from format 10 to format 11 | Not run | Not run | Existing Bank anchors remain non-retryable; only newly persisted Banker-only fallbacks carry retry provenance; no Bank duplicates |
| Upgrade from formats 11/12 to format 13 | Not run | Not run | Authored Bank versions remain guarded; format-12 input cannot invent retired sites or canonical Banker ownership; format-13 relocation state, all retired lots/anchors, and canonical Banker UUIDs survive restart |
| Upgrade from format 13 to format 14 | Not run | Not run | Eligible physical modular roads alone become migration-version 0; the frozen center-surface target/cursor survive restart without changing ordinary road progress; legacy, abstract, and unreserved projects remain complete |
| Upgrade from format 14 to format 15 | Not run | Not run | Eligible completed modular projects alone receive one pending entrance check; frozen step/placement totals and the independent cursor survive restart; spoofed future fields are ignored; legacy, abstract, and unreserved projects remain ineligible and unchanged |
| Upgrade from format 15 to format 16 | Not run | Not run | Earlier saves begin with no death authority; spoofed tombstone fields in format-15 input are ignored; a format-16 save is rejected by a format-15 build instead of losing replacement authority |
| Upgrade from format 16 to format 17 | Not run | Not run | Pre-barrier format-16 tombstones are not trusted as replacement authority; canonical ownership remains fail-closed, typed conversion state begins empty, and a format-17 save is rejected by older builds |
| Upgrade from format 17 to format 18 | Not run | Not run | Every existing `legacy_v1` and `modular_v1` project retains its exact design, origin, bounds, block order, and cursor; Blueprint fields remain empty rather than inferred; only a newly approved project receives a complete `blueprint_v2` identity and plan hash; a format-18 save is rejected by older builds |
| Rejected corrupt or future-format world switch remains inert | Not run | Not run | Startup error, rejected file's exact bytes unchanged after shutdown/save attempt, prior world's save unchanged, and no old-world account snapshot exposed under the rejected path |
| Canonical Banker death is durable and fail-closed | Not run | Not run | Matching Villager and Zombie Villager deaths on each loader enter Bank lifecycle before casualty accounting; a simulated economy-save failure permits no replacement, a successful retry/full save persists the UUID-bound tombstone, restart permits exactly one replacement only after its entity/chunk save, and a surviving exact canonical clears stale authority |
| No route creates player debt or negative balance | Not run | Not run | Debug validation report |

## GUI and onboarding checks

| Test | Fabric | NeoForge | Required evidence |
| --- | --- | --- | --- |
| GUI scale Auto | Not run | Not run | Screenshot of every page |
| GUI scale Small | Not run | Not run | Screenshot of every page |
| GUI scale Normal | Not run | Not run | Screenshot of every page |
| GUI scale Large | Not run | Not run | Screenshot of every page |
| Keyboard navigation and Escape behavior | Not run | Not run | Notes and any blocked control |
| Color-independent gain/loss understanding | Not run | Not run | Tester description without relying on color |
| First join gives one discovery hint, never repeats it, and respects the config opt-out | Not run | Not run | Reconnect notes for enabled and disabled settings |
| First Banker visit explains safe and risky products without blocking the GUI | Not run | Not run | Screenshot and tester summary |
| First Banker visit awards the advancement exactly once | Not run | Not run | First-open toast plus reconnect and reopen notes |
| First-time player completes an investment in under one minute | Not run | Not run | Timed observation |
| Savings, CD, lending, VILX, and businesses are distinguishable | Not run | Not run | Tester explanation in their own words |
| Market previous/next carousel wraps and keeps the selected investment, chart, and trade actions in sync | Not run | Not run | Both wrap points plus one buy and sell per direction |
| Hover details are complete and do not overlap content | Not run | Not run | Exact Account balances; separate Village rows; early/mature CD and fixed/unavailable Fund-purpose states |
| Eight pages, chart range selectors, Trade item/emerald visuals, News guidance, Fund controls, and the scrollable Activity ledger remain readable | Not run | Not run | Screenshot of all Trade resources, each News state, each range, Fund state, and Activity at newest and oldest bounds |
| Activity filter cycles all six categories and pages each filtered result independently | Not run | Not run | Filter label, total/range, empty state, and older/newer bounds |
| Exact amount accepts 3,000, blocks invalid/unapplied text, supports Enter/Cancel/All, and re-caps stale balances | Not run | Not run | Before/after previews and resulting balances on Account, Banking, Market, and Exchange |
| Responsive dashboard remains centered with aligned hitboxes and tooltips in narrow and large windows | Not run | Not run | Screenshots and click/hover checks at both window extremes |
| Non-button labels visually match Button/EditBox glyph size throughout responsive scaling | Not run | Not run | Side-by-side title/body/button/input screenshots at 0.75x, 1.0x, and 1.4x panel fit |
| Exchange Desk is discoverable in Functional Blocks and Creative search | Not run | Not run | Tab and `desk`/`exchange` search screenshots |
| Server-owned confirmation expires and cancels on selection changes | Not run | Not run | Debug timeline and visible status |
| Empty states and unavailable actions are clear | Not run | Not run | Screenshots and notes |

## Configuration checks

| Test | Fabric | NeoForge | Required evidence |
| --- | --- | --- | --- |
| Config show reports the active file and complete active settings | Not run | Not run | Command output |
| A valid reload applies all settings together | Not run | Not run | Before/after command output |
| Invalid boolean, integer, range, and unknown key each reject the whole reload | Not run | Not run | Four errors plus unchanged active summary |
| Disabling a Fund subtype blocks new contributions without deleting existing Fund state | Not run | Not run | Before/after Fund balances and history |
| Market-event and economic-clock controls preserve their documented boundaries | Not run | Not run | No new event shocks while disabled; offline-off ignores wall time but not server/Overworld clocks; `/time add` advances the economic day once, backward `/time set` only rebases, and a low maximum clips a forward wall-clock gap |

## Village and physical-world checks

| Test | Fabric | NeoForge | Required evidence |
| --- | --- | --- | --- |
| Plains bank and projects | Not run | Not run | Wide and close screenshots |
| Desert bank and projects | Not run | Not run | Wide and close screenshots |
| Savanna bank and projects | Not run | Not run | Wide and close screenshots |
| Taiga or snowy bank and projects | Not run | Not run | Wide and close screenshots |
| Sloped or uneven terrain | Not run | Not run | Before/after screenshots and debug ZIP |
| Water-edge or ravine-adjacent village | Not run | Not run | No unsafe placement evidence |
| Obstructed construction lot | Not run | Not run | Retry behavior and no overwritten blocks |
| Expanded bounded site search | Not run | Not run | 48 Bank candidates through 82 blocks; projects begin with 64 candidates through 84 blocks, then persistently expand to at most 256 through 276 after failed sweeps; unloaded outer candidates do not force-load chunks, retries back off, and a later due project still advances |
| Chunk unload during construction | Not run | Not run | Reload result and debug ZIP |
| Two villages inside nearby scan regions | Not run | Not run | Correct identities and separate projects |
| New Village Bank architecture and furnishings | Not run | Not run | Sealed roof, connected 19-pane window set, three-wide lane-agreed terrain-matched staircase with cross-slope rejection, sheltered entrance, interior/storage, exactly one Exchange Desk, no barrel or second job site |
| Version-2/3/4 Bank compatibility and version-5 replacement | Not run | Not run | After same-block pane normalization, repeated scans/reload add no v5 facade, forecourt, dormer, cupola, furnishing, or lighting cells to an intact v2, v3, or v4 Bank; severe demolition leaves the retired ruin untouched and creates a fully marked v5 replacement only at a different safe site |
| Cottage, House, Inn, Warehouse | Not run | Not run | Completed examples, clear residential entries, usable beds, and no duplicate placements |
| Template job sites match their documented theme and forbidden blocks are absent | Not run | Not run | Profession/POI census plus block inspection; exactly one desk in Exchange Hall |
| Mine Entrance, Smithy, Granary | Not run | Not run | Completed examples |
| Market Square, Guard Post, Exchange Hall | Not run | Not run | Completed examples |
| True Banker profession and Exchange Desk POI | Not run | Not run | Profession/POI inspection on both loaders |
| Banker appearance and desk memory survive work and reload | Not run | Not run | Full work-period video plus post-reload profession and JOB_SITE inspection |
| Exchange Desk model, facing, and collision agree | Not run | Not run | Four placement directions and 13.5/16-height selection/collision inspection |
| Legacy completed project receives only a safe append-only blueprint upgrade | Not run | Not run | Safe suffix expands bounds; occupied, block-entity, and vetoed suffixes leave old project operational and untouched |
| Terrain-tolerant foundations ground every bank/project exterior detail | Not run | Not run | Four-block terrain variation plus porch posts, bell accent, lantern fences, masonry flues, and rotated/mirrored out-of-descriptor annexes; every authoritative support reaches loaded natural ground with no tunnelling, including mountain, forest, and low-view-distance lots |
| Existing 13x11 bank support retrofit is signature-scoped and air-only | Not run | Not run | Expected three footings fill; altered signature or occupied footing remains untouched; second pass is idempotent |
| Project trails connect and branch naturally without becoming economic authority | Not run | Not run | First project reaches edge hub; later project reaches nearest earlier branch; protected and non-terrain obstructions are skipped; natural-block provenance boundary is documented |
| Village-wide architectural character and biome dialect remain coherent | Not run | Not run | Several project types in one village plus reload; shared palette/language with type-specific interiors, work areas, and landmarks |
| Isolated Blueprint V2 gallery auto-builds safely | Not run | Not run | Fresh exact-name Superflat save builds 260 gold-master views, 11 controlled comparisons, and five Banks before Quick Play joins; indices 1–276 navigate correctly; the dynamically sized plots do not overlap; a second launch is idempotent; ordinary world names never receive gallery writes or commands |
| Blueprint V2 breadth is visible and deterministic | Not run | Not run | All 52 whole-building gold masters reviewed across five biome dialects; every role has at least five compact, established, and grand forms; tiers 0–1 exclude large/landmark plans, tier 2 unlocks large plans, tiers 4–5 favor each role's largest eligible scale, and repeated approvals avoid immediate template repetition without rerolling after restart |
| Road-facing rotation is correct in all four directions | Not run | Not run | North/east/south/west lots face their frozen anchors; entrance stair, door, beds, rails, roofs, and access survive rotation and reload |
| Prosperity-building entrances reach terrain safely | Not run | Not run | All ten project types on flat and one-to-four-block drops; immediate-turn route, supports, two-block headroom, pre-reservation rejection of unsafe plans, format-14 modular one-time retrofit, obstruction waiver, and no repair after player changes |
| Base, town, and city visual stages are append-only and monotonic | Not run | Not run | Before/after block evidence at tier 2/tier 4, obstruction/protection veto leaves the earlier stage operational, reload stability, and no removal after tier decline |
| Three-wide prosperity-road construction and one-time resurfacing are independent and fair | Not run | Not run | Dirt-path/gravel center plus coarse-dirt shoulders cover segments/turns/endpoints outside building envelopes; old modular roads finish their frozen plan before a separate versioned scan visits every historical coarse-center coordinate; migration target/cursor survive restart, ordinary totals remain untouched, gravel remains, protected/occupied cells become gaps, unloaded cells wait, later routes advance, and building authority is unchanged |
| Partial structure damage remains player-authored | Not run | Not run | Project and versioned Bank become unsafe below the severe threshold; a blocked reserved aisle/access target, removed door, or removed workstation is never repaired or overwritten; cosmetic base decor is non-authoritative; access-only obstruction does not trigger relocation; no duplicate appears; scoped Banker suspends, active desk warns then opens personal access, Bank-only safe replaceable outdoor growth is tolerated, and full manual restoration reactivates managed access |
| Severe structure demolition relocates safely | Not run | Not run | At least 12 and 30% authored mismatches retire the old bounds/anchor; replacement uses another safe lot; ruins and player edits remain untouched; every retained site stays excluded across restart |
| Retired Bank access and Banker identity remain unique | Not run | Not run | Old generated desk cannot open as a personal desk; a loaded Banker moves to the replacement; a null lookup or unloaded canonical UUID creates no duplicate; only a durable matching death tombstone allows one replacement, whose entity/chunk save precedes the canonical UUID swap |
| Banker and zombie Banker jackets match the tailored blueprint without stray UV pixels | Not run | Not run | Front/side/back screenshots plus head and hat-rim inspection for both entity types |
| Bounded construction worker movement | Not run | Not run | Video or timeline showing at most two workers and no idle task |
| Village extinction and funded restoration | Not run | Not run | Timeline and resident counts |
| Zombie infection, Banker conversion, and curing | Not run | Not run | Each new conversion UUID is adopted only after exact predecessor-lineage and entity/chunk persistence; an unresolved infection-to-cure chain converges directly to the cured UUID, conversion-time source death is neutralized, no replacement appears, and an actual marked Zombie Villager death tombstones the original canonical identity exactly once |
| Pillager casualty attribution | Not run | Not run | Incident panel and debug ZIP |
| Player casualty counterfactual protection | Not run | Not run | Market contribution before/after |
| Project benefits wait for verified physical completion | Not run | Not run | Output/housing values before and after materialization |
| Player-removed project blocks | Not run | Not run | Benefit suspension, no item regeneration, and reactivation after manual restoration |
| Missing Bank with ordinary/non-provenanced marker | Not run | Not run | Fallback Banker access and confirmation that no Bank blocks are auto-rebuilt |
| Explicit format-11 Banker-only fallback becomes buildable | Not run | Not run | One paced Bank build, atomic marker/anchor/ownership promotion, provenance cleared, and no duplicate |

## Multiplayer and scale checks

| Test | Fabric | NeoForge | Required evidence |
| --- | --- | --- | --- |
| Two players bank simultaneously | Not run | Not run | Exact balances and debug ZIPs |
| Two players support one village simultaneously | Not run | Not run | Fund balances and separate donor records |
| Player disconnect during another player's transaction | Not run | Not run | Both account results |
| 100 stored villages and accounts | Not run | Not run | Indexed query and save/load percentiles |
| 500 stored villages and accounts | Not run | Not run | Indexed query and save/load percentiles |
| 1,000 stored villages and accounts | Not run | Not run | Indexed query and save/load percentiles |
| Large offline catch-up | Not run | Not run | Days advanced, elapsed time, validation report |
| Multi-hour multiplayer session | Not run | Not run | Session length, peak players, debug ZIP |
| Claim/protection integration | Not run | Not run | Protected blocks remain untouched |
| Debug owner/privacy/timing boundaries | Not run | Not run | Two-operator attempt and inspected sanitized ZIP |

## Release gate

A candidate may be called public beta when CI passes and every Critical automated invariant passes. It may be called stable only after this matrix contains real evidence for every Critical row and no unresolved issue can duplicate, destroy, or create financial value incorrectly.
