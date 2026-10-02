# beta.57: optional vanilla-only village expansion

## Follow-up: duplicated district hover name

Fixed the map-center tooltip repeating its district name on both the heading and resident
detail line. Districts now show the name once, followed by residents and center coordinates.
Legacy housing/coverage details remain; Bank/building tooltips still retain their owning
district, status and project progress. No map data, identities or world state changed.

Both loader packages and native reader/settings checks passed with production header-component
assertions covering organic districts, legacy housing counts, negative coordinates and Bank/site
ownership. Added a localized client-side regression assertion. Common map/handbook regressions
and `git diff --check` passed. No fresh rendered client screenshot was captured.

Handbook accuracy review: clarified the guided district-map hover explanation and the compact
growth page, with regression coverage for both. Recipes, creative entries and diagrams are
unchanged. Updated beta.57 JARs locally; no installed profile or GitHub state changed.

## Scope and behavior

Added world-local `village_prosperity.vanilla_only_buildings`, default `false`, exposed as
**Village: vanilla buildings only** in Handbook > Settings. Apply and config reload update
the service's runtime policy for existing/new villages and transaction copies. This is not
a persistent economy-format migration or a change to Minecraft world generation.

Normal need-based approvals, Fund fast-track approvals, forced development and legacy district
starter plans share the selection policy. Matching validated Minecraft plans are selected without
the mixed-catalog probability roll. Unsupported TES-only needs are skipped before funding.
Missing cottages can select matching larger homes; unavailable/uncertain catalogs cannot silently
select TES, change styles, consume a project serial, debit approval inputs or commit an empty
district draft. Town briefings explain unavailable matching catalogs.

TES Banks and infrastructure remain. Existing buildings and already-approved projects preserve
their frozen plans regardless of toggles or resource availability. Actual vanilla beds determine
capacity; imported economic benefits still require physical completion, including when visual
progression is disabled. Completed vanilla food/trade facilities and workshops provide alternative
early-tier infrastructure checks without inventing Warehouse/Mine benefits. Tier 4/5 thresholds,
immigration requirements, protection rules and construction budgets remain unchanged.

## Validation

- Full common regression suite passed, including new five-style approval, funding, forced growth,
  tier, missing-catalog, unknown-style, reload/copy, immutable-plan and legacy-district cases.
- Fabric and NeoForge full builds passed; final packages and native reader/settings verification
  were refreshed after the last district-commit guards.
- Fabric dedicated-server vanilla-construction smoke passed in a disposable world. The runtime
  catalog check exercised housing/food/craft/trade selection for all five styles without a TES
  probability fallback; existing native template construction, foundations, survival, saved
  progress, empty containers and entrance/bed walking fixtures also passed.
- `git diff --check` passed. No user world, installed mod JAR or external GitHub state changed.

The exhaustive opt-in server fixture performs substantial synchronous test work and emits a
"Can't keep up" warning. It is not a benchmark of ordinary vanilla-only gameplay; the smoke
runner intentionally terminates its disposable server after the success marker.

## Handbook accuracy review

Added a guided Building projects section explaining the setting, world-local Apply/reload,
Bank/infrastructure exceptions, ordinary/Fund/forced coverage, style restrictions, frozen plans,
physical benefits and unavailable-catalog waits. Updated the growth chapter's exact tier 2/3
requirements. Added compact/lectern page 69 without renumbering prior pages. Regression checks
cover both forms and the new chapter routing. Settings hover help and reset/atomic-edit tests
include the new boolean. Existing recipes, diagrams and creative entries are unchanged: no new
item/block or recipe was introduced.

Limitations: no village-overhaul imports or guessed legacy/unknown source styles; no automatic
replacement/removal of previously approved TES buildings; no claim of a fresh player-driven
visual playthrough or sustained multi-village performance measurement.
