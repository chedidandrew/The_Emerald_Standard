# The Emerald Standard 0.4.0-beta.2

> Candidate release notes. `0.4.0-beta.2` has not been tagged or published. The automated gate passed candidate implementation commit `49e1e7cfb4df5d68970162b2da66170d1f6b7efd`; hands-on testing is not claimed.

This beta completes the investment-accounting, multi-position banking, village-funding, physical-integrity, and presentation work planned after beta.1.

## Portfolio and banking

- Stock holdings now retain cost basis and show average purchase price, allocation, and realized and unrealized performance.
- Total external contributions and withdrawals, a bounded transaction ledger, and personal net-worth history persist with each account.
- Asset, commodity, and personal histories retain up to 1,825 economic days. The dashboard offers 30-day, 90-day, one-year, and all-history views.
- Players can hold and select up to eight independent CDs and eight independent villager-lending positions. Each position keeps its own term, rate, maturity, value, and outcome state.
- Players still cannot borrow, carry a negative balance, or enter debt. A lending default can lose only the amount voluntarily funded.

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

## Diagnostics and safety

- The operator who starts `/emerald debug` owns its mark, toggle, and stop controls.
- A capture follows one watched settlement and excludes unrelated accounts and settlement events, resident UUIDs, the private economy seed, world seed, chat, and server address.
- Performance output labels sampling, active recorder ticks, JSONL writes, snapshots, and full-state copies separately; overlapping figures are not presented as subsystem timings.
- Sell-all, CD closure, villager-lending funding, and Prosperity Fund contributions use time-limited server-owned confirmation state.

## Save compatibility

Beta.2 advances persistence to format 9. Existing format-7 and format-8 worlds migrate forward. Legacy scalar term products become identified positions, and holdings that predate execution history receive an explicitly inferred migration-day basis. Older builds intentionally reject format 9 instead of silently discarding new account or village data. Keep a pre-upgrade backup if downgrade may be needed.

## Verification boundary

GitHub Actions workflow [`33894198970`](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/33894198970) passed the common regression suites, both loader builds, packaged-JAR inspection, both dedicated-server startup checks, and both client bootstrap checks for the exact candidate implementation commit. Every hands-on row in `docs/MANUAL_TEST_MATRIX-0.4.md` remains `Not run`; no tag or public prerelease has been created.
