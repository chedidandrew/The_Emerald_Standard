# The Emerald Standard 0.4.0-beta.2

> Candidate release notes. `0.4.0-beta.2` has not been tagged or published. The automated gate passed candidate implementation commit `49e1e7cfb4df5d68970162b2da66170d1f6b7efd`; hands-on testing is not claimed.

This beta completes the investment-accounting, multi-position banking, village-funding, physical-integrity, and presentation work planned after beta.1.

## Portfolio and banking

- Stock holdings now retain cost basis and show average purchase price, allocation, and realized and unrealized performance.
- Total external contributions and withdrawals, a bounded transaction ledger, and personal net-worth history persist with each account.
- Asset, commodity, and personal histories retain up to 1,825 economic days. The dashboard offers 30-day, 90-day, one-year, and all-history views.
- A compact Log page shows lifetime deposits and withdrawals plus the five newest entries from the persistent transaction ledger; stock trades include their ticker.
- Players can hold and select up to eight independent CDs and eight independent villager-lending positions. Each position keeps its own term, rate, maturity, value, and outcome state.
- Players still cannot borrow, carry a negative balance, or enter debt. A lending default can lose only the amount voluntarily funded.
- Inventory-linked transactions checkpoint only the affected player's synchronized NBT, require a matching persisted inventory, and then durably remove the committed journal before gameplay resumes.
- Whole-economy replacement saves reuse an exact-byte SHA-256 validation result only when the previous primary is unchanged, cutting replacement time by roughly two thirds in a before/after mature-state benchmark without trusting externally altered bytes.

## Village Prosperity Fund

- The sixth dashboard page manages voluntary Direct Grants, Endowments, and Project Sponsorships for the associated settlement.
- General, Housing, Food, Infrastructure, Security, Trade, and Restoration purposes direct bounded local inputs; contributions never write market returns directly.
- Project Sponsorship follows the displayed active economically unfinished project and derives its purpose from that project type. Unused value rolls into the same purpose when the economic project completes.
- Endowment principal is protected. The default 4 percent annual payout, not the principal, becomes spendable over time.
- Ordinary grants reserve a configurable share for emergencies, and a configurable monthly ceiling limits automatic Fund spending.
- Spending debits only value that a bounded village input can accept, retaining the remainder when an input is saturated.
- Amounts are assembled through a server-owned additive draft: `+1`, `+5`, `+10`, `+25`, `+100`, `All`, and `Clear`.
- The irreversible contribution uses a second matching server-confirmed click. Donor totals and titles are recognition only and grant no financial advantage.

## Villages and presentation

- A registered Banker profession now uses the craftable Exchange Desk workstation and acquirable POI on Fabric and NeoForge. Naturally employed Bankers and player-placed desks open the dashboard, while upgraded lectern counters remain usable at their persisted banks.
- Visual-mode project benefits wait for verified physical materialization. A low-frequency audit suspends benefits when an authored block is missing, and safe repair runs through the normal no-force-load, protection-aware queue.
- Active construction may give at most two suitable residents an occasional low-speed route to a safe exterior waypoint plus bounded looks, swings, and particles. These cues do not install persistent AI or decide economic progress.
- Nearby-village lookup uses a rebuildable per-dimension spatial index with exact distance checks. Regression measurements cover query, save, and load behavior at 100, 500, and 1,000 villages and accounts.
- Unstarted projects inspect 20 bounded, deterministic lot candidates instead of 12, giving rough villages more placement opportunities without force-loading chunks or relaxing terrain, overlap, bank-distance, or protection checks.

## Diagnostics and safety

- The operator who starts `/emerald debug` owns its mark, toggle, and stop controls.
- A capture follows one watched settlement and excludes unrelated accounts and settlement events, resident UUIDs, the private economy seed, world seed, chat, and server address.
- Performance output labels sampling, active recorder ticks, JSONL writes, snapshots, and full-state copies separately; overlapping figures are not presented as subsystem timings.
- Sell-all, CD closure, villager-lending funding, and Prosperity Fund contributions use time-limited server-owned confirmation state.
- A Fund confirmation is valid only while its amount, type, effective purpose, village lifecycle, village identity, and sponsored project remain unchanged. Catch-up and unresolved inventory recovery block direct Fund contributions as well as ordinary transactions.
- Zero-proceeds stock dust sales are rejected without changing holdings, basis, cash, or the activity ledger; exhausted Fund counters likewise reject before a donor is debited.
- Banker dashboard data is packed into signed 16-bit wire limbs and reassembled losslessly, so full-width balances, holdings, histories, position IDs, activity amounts, and Fund drafts match the authoritative server state.
- VILX progressively damps only trailing-year upside above 50 percent toward an 80 percent soft guardrail. The 250-seed, 75-year corpus retains 9.50 percent mean CAGR, 27.4 percent negative years, and a -59.6 percent downside extreme while reducing its +126.8 percent calendar-year upside outlier to +76.4 percent.

## Onboarding and configuration

- Each player receives one persistent, configurable discovery hint on their first join. Their first successful Banker visit awards a one-time advancement and gives a concise deposit and risk explanation.
- `onboarding.join_hint_enabled=false` suppresses the join hint without changing gameplay or erasing player data.
- Worlds may independently disable rare market events or trusted wall-clock progression and may lower the maximum credited offline gap without disabling ordinary game-time progression.
- Configuration reload reports the exact world-local file and validates the complete edit before applying it. A malformed boolean, non-integer, out-of-range value, or unknown key rejects the whole reload while the previous settings remain active.
- `docs/CONFIGURATION.md` records every default, accepted range, interaction, and large-server tuning control.

## Build and release integrity

- Both Gradle 9.5.1 wrappers pin the official binary-distribution SHA-256 digest, and the common gate verifies each wrapper JAR against Gradle's published checksum.
- Packaged-JAR checks require exact binary and sources filenames, embedded loader identity and version, manifest version, required shared/client sources, resources, and valid language JSON.
- Each loader artifact receives a machine-generated `SHA256SUMS`. The exact-commit staging script verifies both downloaded CI artifacts and creates the combined public checksum and release manifest without rebuilding them.
- A structured GitHub issue form records loader, exact commit, procedure, observed values, duplication/loss checks, and diagnostic evidence for hands-on beta tests.

## Save compatibility

Beta.2 advances persistence to format 9. Existing format-7 and format-8 worlds migrate forward. Legacy scalar term products become identified positions, and holdings that predate execution history receive an explicitly inferred migration-day basis. Older builds intentionally reject format 9 instead of silently discarding new account or village data. Keep a pre-upgrade backup if downgrade may be needed.

The Banker synchronization layout changed during unreleased beta.2 development. Persistence compatibility is unchanged, but servers and clients must use the same exact beta.2 build; replace both sides together rather than mixing an earlier candidate with the repaired build.

## Verification boundary

GitHub Actions workflow [`33894198970`](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/33894198970) passed the common regression suites, both loader builds, packaged-JAR inspection, both dedicated-server startup checks, and both client bootstrap checks for earlier candidate implementation commit `49e1e7cfb4df5d68970162b2da66170d1f6b7efd`. All subsequent changes, including GUI, onboarding, configuration, packet synchronization, and release-integrity work, require a new successful exact-commit `main` workflow before publication. Every hands-on row in `docs/MANUAL_TEST_MATRIX-0.4.md` remains `Not run`; no tag or public prerelease has been created.
