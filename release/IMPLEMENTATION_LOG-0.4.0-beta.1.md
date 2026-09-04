# 0.4.0-beta.1 Implementation Log

## Objective

Turn the strong abstract economy and Village Prosperity simulation into a more visible, understandable Minecraft progression loop while preserving the mod's no-debt, command-light, multiplayer-safe identity.

## Repository baseline

- Starting main commit: `5187c6326545a1292b276947e7e1f3a980063745`
- Starting public version: `0.3.0-beta.4`
- Original experimental branch: `milestone-95` and draft pull request `#6`
- Recovered candidate branch: `fix/recover-0.4.0-beta.1`

## Source changes in this milestone

### Visible village progression

- Expanded the curated project catalog from three project types to ten.
- Added House, Village Inn, Market Square, Smithy, Granary, Guard Post, and Exchange Hall while retaining Cottage, Warehouse, and Mine Entrance identifiers.
- Added local-need project selection. Safety, food reserves, housing pressure, population, prosperity, and development tier influence what the village funds next.
- Added bounded deterministic templates for every project using biome-aware palettes.
- Preserved no forced chunk loading, loaded-lot validation, construction budgets, retry gates, project overlap protection, and cooperative claim/protection guards.
- Added bounded production relationships so relevant structures help agriculture, mining, trade, transport, or security without allowing villages to dominate market returns.

### Player clarity and onboarding

- Added a simple local economic-impact indicator to the Village dashboard without publishing the return formula.
- Added one-time first-Banker orientation text that distinguishes safe deposits from term products and risky investments and explicitly states that the player can never owe emeralds.
- Added subtle Banker interaction sound and first-visit particles.

### Verification and scale

- Added a deterministic loader-neutral stress regression for 100, 500, and 1,000 villages over 365 simulated economic days.
- Integrated the deterministic scale guard into the standard loader-neutral regression suite.
- Retained the normal read-only GitHub Actions workflow for common tests, both loader builds, package checks, both server smoke tests, and both client smoke tests.
- Added `docs/MANUAL_TEST_MATRIX-0.4.md` as the evidence gate for checks that automation cannot truthfully complete.

## Compatibility decisions

- Original project enum names and their ordering remain first so existing beta project records can continue parsing safely.
- The abstract village state remains authoritative. Physical structures are a bounded representation, not the financial source of truth.
- No borrowing, margin, negative balances, taxes, high-frequency trading, arbitrary villager mining, or forced chunk loading was introduced.
- Manual gameplay rows remain marked `Not run` until a person tests the exact Fabric and NeoForge candidate.

## Validation gate

The recovered milestone is eligible to merge only when the normal read-only `build.yml` workflow passes all seven jobs for the exact pull-request head. Generated payloads, transformation scripts, self-modifying workflows, and stale failure reports are deliberately excluded from the recovered branch.

## Deferred high-risk phases

These remain tracked in the 9.5 milestone issue and must receive their own migrations and cross-loader validation:

- Registered Banker profession and dedicated workstation/POI.
- Persistent cost basis, realized/unrealized gain, portfolio history, and transaction ledger.
- Multiple CDs and villager-lending positions.
- Donation allocation and expense history.
- Physical project and bank-marker reconciliation.
- Account/village storage partitioning and dedicated-server profiling.
- Full hands-on GUI, terrain, multiplayer, crash-window, and long-session evidence.
