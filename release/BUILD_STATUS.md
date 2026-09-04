# Build status for 0.4.0-beta.2

## Candidate status

- Source branch: `main`
- Candidate source commit: pending final beta.2 commit
- Fabric version: `0.4.0-beta.2`
- NeoForge version: `0.4.0-beta.2`
- Persistent format: 9
- Manual test matrix: `docs/MANUAL_TEST_MATRIX-0.4.md`; every row remains `Not run`
- Public `0.4.0-beta.2` prerelease: not published
- Release tag, artifacts, and checksums: not created

The last verified main-branch baseline predates the beta.2 changes. No successful workflow, server smoke, client bootstrap, artifact, or hands-on result is attributed to this candidate until it is produced from the exact final commit.

## Candidate scope

- Persistent share cost basis, average purchase price, realized and unrealized performance, allocation, contribution and withdrawal totals, a bounded transaction ledger, and personal net-worth history
- Five economic years of asset, commodity, and personal history with 30-day, 90-day, one-year, and all-history views
- Up to eight independently identified and selectable CDs and eight villager business-lending positions per player
- A dedicated Prosperity Fund page with Direct Grants, protected-principal Endowments, Project Sponsorships, seven purposes, additive server-owned drafts, two-step server confirmation, emergency reserves, bounded spending, and donor recognition
- Physical housing and production benefits gated on verified project materialization, with low-frequency integrity demotion and guarded repair
- A true cross-loader Banker profession, dedicated Exchange Desk block and POI, and legacy lectern compatibility
- Bounded one-shot worker navigation and construction cues that do not become persistent AI or economic authority
- Owner-only debug capture controls, one watched-village boundary, unrelated-player and settlement filtering, resident-identity redaction, and separately labeled timing costs
- A rebuildable 64-block-cell, per-dimension village spatial index plus measured query/save/load regressions at 100, 500, and 1,000 villages and accounts

## Required automated gate

The exact final candidate commit must still pass all standard GitHub Actions jobs:

- Loader-neutral economy, persistence, migration, finance, Village Prosperity, debug, and scaling regressions
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup and live integration checks
- NeoForge dedicated-server startup and live integration checks
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration

Workflow run IDs, commit identity, artifact IDs, and checksums must be recorded only after those jobs complete successfully. Publication must reuse the exact verified artifacts rather than rebuilding another source state.

## Compatibility and remaining boundaries

Format-7 and format-8 worlds migrate directly to format 9 for portfolio accounting, multi-position term products, commodity and personal history, Prosperity Funds, and donor records. Holdings without historical executions receive an explicitly inferred opening basis. Older builds reject format 9, so testing should begin from a world backup if downgrade may be needed.

Nearby village lookup is indexed, but ordinary mutations still synchronously serialize the complete world economy; very large persistent worlds retain linear save/load cost. Economy-file bank markers and Minecraft chunk saves are not cross-file atomic, so a narrow crash window can still require fallback Banker access instead of automatic bank reconstruction.

This remains an unreleased beta candidate. Automated verification cannot replace hands-on review of all six pages and GUI scales, term-position selection, Fund controls, unusual terrain, project repair, claim integrations, multiplayer concurrency, long sessions, or subjective structure presentation.
