# Build status for 0.4.0-beta.2

> This is an exact-commit record for the last fully verified executable candidate. It does not certify later commits. Any post-`49e1e7cfb4df5d68970162b2da66170d1f6b7efd` code, resource, build, or wrapper change requires a new complete workflow before publication.

## Candidate status

- Source branch: `main`
- Candidate implementation commit: `49e1e7cfb4df5d68970162b2da66170d1f6b7efd`
- Verification workflow: `33894198970`, successful
- Fabric version: `0.4.0-beta.2`
- NeoForge version: `0.4.0-beta.2`
- Persistent format: 9
- Manual test matrix: `docs/MANUAL_TEST_MATRIX-0.4.md`; every row remains `Not run`
- Public `0.4.0-beta.2` prerelease: not published
- Release tag, release attachments, and published checksums: not created

All seven standard GitHub Actions jobs passed for the exact candidate implementation commit on 2026-09-04. This verification-record update changes documentation only and does not alter the executable candidate inputs. No hands-on result is attributed to the candidate.

## Candidate scope

- Persistent share cost basis, average purchase price, realized and unrealized performance, allocation, contribution and withdrawal totals, a bounded transaction ledger, and personal net-worth history
- Five economic years of asset, commodity, and personal history with 30-day, 90-day, one-year, and all-history views
- Up to eight independently identified and selectable CDs and eight villager business-lending positions per player
- A dedicated Prosperity Fund page with Direct Grants, protected-principal Endowments, Project Sponsorships, seven purposes, additive server-owned drafts, two-step server confirmation, emergency reserves, bounded spending, and donor recognition
- Physical housing and production benefits gated on verified project materialization, with low-frequency integrity demotion and guarded repair
- A true cross-loader Banker profession, craftable Exchange Desk block, acquirable POI, direct Banker/desk access, and scoped legacy lectern compatibility
- Bounded one-shot worker navigation and construction cues that do not become persistent AI or economic authority
- Owner-only debug capture controls, one watched-village boundary, unrelated-player and settlement filtering, resident-identity redaction, and separately labeled timing costs
- A rebuildable 64-block-cell, per-dimension village spatial index plus measured query/save/load regressions at 100, 500, and 1,000 villages and accounts

## Verified automated gate

Workflow [`33894198970`](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/33894198970) passed all standard jobs for candidate implementation commit `49e1e7cfb4df5d68970162b2da66170d1f6b7efd`:

- Loader-neutral economy, persistence, migration, finance, Village Prosperity, debug, and scaling regressions
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup and live integration checks
- NeoForge dedicated-server startup and live integration checks
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration

The retained workflow artifacts are:

- Fabric candidate JAR: `9945105054`
- NeoForge candidate JAR: `9945108119`
- Fabric server/client smoke logs: `9945146958`, `9945149086`
- NeoForge server/client smoke logs: `9945147279`, `9945157326`

Publication should reuse the verified candidate JAR artifacts rather than rebuilding another source state. Release checksums remain a publication-time requirement and have not been created.

## Compatibility and remaining boundaries

Format-7 and format-8 worlds migrate directly to format 9 for portfolio accounting, multi-position term products, commodity and personal history, Prosperity Funds, and donor records. Holdings without historical executions receive an explicitly inferred opening basis. Older builds reject format 9, so testing should begin from a world backup if downgrade may be needed.

Nearby village lookup is indexed, but ordinary mutations still synchronously serialize the complete world economy; very large persistent worlds retain linear save/load cost. Economy-file bank markers and Minecraft chunk saves are not cross-file atomic, so a narrow crash window can still require fallback Banker access instead of automatic bank reconstruction.

This remains an unreleased beta candidate. Automated verification cannot replace hands-on review of all seven pages and GUI scales, term-position selection, Fund controls, unusual terrain, project repair, claim integrations, multiplayer concurrency, long sessions, or subjective structure presentation.
