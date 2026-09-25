# Creative catalog, spawn eggs and handbook synchronization

## Delivered behavior

- Dedicated native The Emerald Standard Creative tab, using the Exchange Desk icon.
  Includes all registered mod items, with preferred order handbook, desk, fence, Banker egg,
  Builder egg. Future mod-namespace items are automatically included.
- Separate creative-only Banker Villager and Builder Villager spawn eggs; both also appear
  under vanilla Spawn Eggs and creative search. No Survival egg recipes, per Andrew's choice.
- The Banker is a vanilla villager with the custom profession, not a new entity type.
  Its egg carries the profession and one XP to prevent jobless profession reset, but no
  managed tag, Bank region or canonical identity. It opens personal banking, not a Bank claim.
  It rejects vanilla spawner configuration without consumption because that path only stores
  an entity type, losing the profession. Native dispensers preserve its egg data.
- Unassigned Builders spawned manually persist and wander. They do not join an automatic
  construction crew or leave when another project ends. Existing assigned workers retain their
  work animation, size-based crew management, saved assignment and departure behavior.
- Craft four caution fences from four sticks, one yellow dye and one black dye, in rows
  SYS / SBS. A stick unlocks recipe discovery. Manual fences drop one item when broken;
  owned automatic fences and missing-receipt loot contexts yield no items.

## Handbook review

Added guided sections for fence crafting, Creative tools and construction crews. The crafting
chapter has all three usable-item recipe diagrams, reading server displays when known and
otherwise the bundled recipe data. The fence diagram accurately shows four output items.
Fixed-ingredient recipes are labeled accordingly rather than suggesting alternatives.

All 16 chapters now contain 59 sections. The 50-page compact/lectern fallback appends new pages
without moving existing links. Initial real-font validation caught two overlong summaries;
those were shortened without reducing the separate long-form guidance.

Root AGENTS.md now requires handbook accuracy review and same-change updates for every future
player-facing revision, including recipes, examples, limitations, and regression coverage.
This is a repository working rule, not a claim that prose can update itself automatically.

## Validation

- Complete common suite: build/creative-common-final.log, including recipe/localization,
  all section coverage, paragraph structure, egg restrictions and documented ingredient checks.
- Both loader assemblies, menu packet checks and reader/settings checks:
  build/creative-{fabric,neoforge}-build-final.log.
- Both fresh dedicated-server suites: build/creative-{fabric,neoforge}-server-final.log.
  New CreativeContentSelfTest rebuilds native tabs and checks catalog/search completeness,
  custom-block items, an egg for every custom mob type, actual Creative egg use, exact entity
  counts, Banker profession through AI/reload, no managed Bank claim, unassigned Builder
  persistence, rejection of active-site adoption, native dispensers, spawner refusal, the real
  crafting recipe, unlock advancement and manual/automatic/missing-receipt fence drops.
  Existing construction, ownership, inventory and finance fixtures also pass.
- Focused real-client checks on both loaders at GUI scales 2 and 4:
  build/creative-{fabric,neoforge}-client-final.log. All 50 compact pages fit; reader navigation,
  searching, paragraph wrapping, scroll reachability at 80/120 text, three recipe cards,
  ingredient/output identities, plank/shapeless cycling and pause controls pass.
- Native item-model preview screenshots and recipe diagrams were inspected for missing models,
  item labels, output count and layout. These use the same catalog as the native tab; they are
  **not** a screenshot of clicking the actual Creative inventory in a loaded player world.
  Native tab membership itself is verified in the server registry tests.
- Both packaged JAR checks and git diff --check pass.

Known host OSHI/Perflib, development Realms authentication, and NeoForge vanilla translation
rename warnings remain outside this feature. The opt-in exhaustive server startup fixtures
cause a catch-up warning and are intentionally terminated by their harness after the pass marker.
These results do not promise flawless navigation in every modpack or arbitrary edited terrain.
No user world or installed profile was modified; no commit or push was made. Existing unrelated
working-tree changes remain preserved and included in the candidate builds.

## Candidate binaries

Use only the matching loader JAR, replacing the prior TES version rather than installing both.
Back up worlds. The existing economy save format remains 30; this addition needs no further
economy migration.

- Fabric: fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar
  SHA-256: 8aacbbdd4aa62f4ac4a3d0918e933234e1d8b09f60d89390c3e892c5dba53db5
- NeoForge: neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar
  SHA-256: 8272afb0e7133ce2d7b0c29da032a2be9c5a1867fb84f6d8babed9384ca69a7f
