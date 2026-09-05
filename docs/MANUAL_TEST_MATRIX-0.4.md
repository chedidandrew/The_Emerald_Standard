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
| Mod version | 0.4.0-beta.2 candidate | 0.4.0-beta.2 candidate |
| Loader version | Unverified | Unverified |
| Tester | Unverified | Unverified |
| Date | Unverified | Unverified |

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
| Hover details are complete and do not overlap content | Not run | Not run | Exact Overview balances; separate Village rows; early/mature CD and fixed/unavailable Fund-purpose states |
| Seven pages, chart range selectors, Fund controls, and the scrollable Activity ledger remain readable | Not run | Not run | Screenshot of each range, Fund state, and Activity at newest and oldest bounds |
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
| Market-event and offline-clock controls preserve their documented boundaries | Not run | Not run | No new event shocks while disabled; offline-off ignores wall time but not game time; low maximum clips a forward-clock gap |

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
| Chunk unload during construction | Not run | Not run | Reload result and debug ZIP |
| Two villages inside nearby scan regions | Not run | Not run | Correct identities and separate projects |
| New Village Bank architecture and furnishings | Not run | Not run | Sealed roof, sheltered entrance, interior/storage, exactly one Exchange Desk, no barrel or second job site |
| Cottage, House, Inn, Warehouse | Not run | Not run | Completed examples, clear residential entries, usable beds, and no duplicate placements |
| Template job sites match their documented theme and forbidden blocks are absent | Not run | Not run | Profession/POI census plus block inspection; exactly one desk in Exchange Hall |
| Mine Entrance, Smithy, Granary | Not run | Not run | Completed examples |
| Market Square, Guard Post, Exchange Hall | Not run | Not run | Completed examples |
| True Banker profession and Exchange Desk POI | Not run | Not run | Profession/POI inspection on both loaders |
| Banker appearance and desk memory survive work and reload | Not run | Not run | Full work-period video plus post-reload profession and JOB_SITE inspection |
| Exchange Desk model, facing, and collision agree | Not run | Not run | Four placement directions and 13.5/16-height selection/collision inspection |
| Legacy completed project receives only a safe append-only blueprint upgrade | Not run | Not run | Safe suffix expands bounds; occupied, block-entity, and vetoed suffixes leave old project operational and untouched |
| Shallow-lot foundations ground every bank/project exterior detail | Not run | Not run | Two-block terrain variation plus porch posts, bell accent, lantern fences, masonry flues, and no tunnelling below natural ground |
| Existing 13x11 bank support retrofit is signature-scoped and air-only | Not run | Not run | Expected three footings fill; altered signature or occupied footing remains untouched; second pass is idempotent |
| Project trails connect and branch naturally without becoming economic authority | Not run | Not run | First project reaches edge hub; later project reaches nearest earlier branch; protected and non-terrain obstructions are skipped; natural-block provenance boundary is documented |
| Three project presets and town/city visual stages are stable and monotonic | Not run | Not run | Distinct seeds, reload stability, tier 2/tier 4 additions, and no removal after tier decline |
| Banker and zombie Banker jackets match the tailored blueprint without stray UV pixels | Not run | Not run | Front/side/back screenshots plus head and hat-rim inspection for both entity types |
| Bounded construction worker movement | Not run | Not run | Video or timeline showing at most two workers and no idle task |
| Village extinction and funded restoration | Not run | Not run | Timeline and resident counts |
| Zombie infection and curing | Not run | Not run | Resident identity preserved |
| Pillager casualty attribution | Not run | Not run | Incident panel and debug ZIP |
| Player casualty counterfactual protection | Not run | Not run | Market contribution before/after |
| Project benefits wait for verified physical completion | Not run | Not run | Output/housing values before and after materialization |
| Player-removed project blocks | Not run | Not run | Benefit suspension, no item regeneration, and reactivation after manual restoration |
| Missing bank with existing marker | Not run | Not run | Fallback Banker access and confirmation that no bank blocks are auto-rebuilt |

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
