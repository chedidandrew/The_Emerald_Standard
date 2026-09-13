# Expanded handbook validation

Date: 2026-09-11. Minecraft 26.2, mod 0.4.0-beta.5, Fabric and NeoForge.

## Scope

Replace the reader's tiny-book summaries with dedicated long-form localization:
16 chapters, 55 sections, 8,184 words. Keep paragraphs and meaningful headings instead
of numbered quick-start fragments. Existing handbook items open the updated content;
the 47 compact legacy/lectern pages remain separate and unchanged by this rewrite.

Coverage includes every Fund purpose and type, actual routine conversions, default
reserve and spending limits, project-family matching, worked gifts, Town stocks and
outputs, score weights, tier thresholds, housing-census limitations, renewable food
sources, lighting, district growth and upkeep. Banking/product, construction, recovery
and glossary chapters were expanded too. No economy, payment or growth rules changed.

Choosing a search result jumps to the matching section, preferring its heading.
Animated crafting previews remain and include explanatory prose below their cards.

## Evidence and checks

- Read the current EconomyState Fund spending/contribution code, EconomyService,
  VillageProsperityEngine, VillageExpansion, VillageFoodSupply, physical census/food
  survey code, settings defaults and product rules before authoring the descriptions.
- Full common suite passed: `build/expanded-handbook-common.log`.
- New resource test verifies all 55 long-form section titles/bodies, all seven purposes,
  paragraph structure, minimum coverage, supported translation formats, no duplicate
  language keys, and continued legacy resources.
- New mechanics test executes all seven routine Fund conversions and checks the
  corresponding handbook values, growth supply thresholds and restoration target.
  It explicitly proves Security does not directly add Safety and Housing does not
  instantly add capacity.
- Both focused final client runs exited successfully:
  `build/expanded-handbook-client-fabric-final.log` and
  `build/expanded-handbook-client-neoforge-final.log`.
- Native-font checks cover every chapter's wrapping and end-of-scroll reachability
  at GUI scales 2 and 4 with 80% and 120% reader text. Existing navigation, preference,
  search, recipe-card and pause-button checks also ran; all 47 legacy pages fit.
- A Security search returns the Fund chapter and scrolls directly inside it.
  Fourteen screenshots per loader cover introductions, Town, Fund and search landing
  at the tested scales. Visual inspection confirmed readable paragraph spacing and
  no text/footer overlap in the inspected views.
- Both assemblies, packaged-JAR checks and `git diff --check` passed.

Screenshots are in
`build/client-smoke/expanded-handbook-{fabric,neoforge}-final/screenshots/tes-reader-ci/`.
The initial runs used the same paths without `-final`; the final runs include the
additional explanation of the default 0.8 E/day routine spending limit.

## Limits

This is targeted handbook verification, not a claim that the entire broad client
smoke suite is green. The previously reported native-cursor recipe-hover animation
assertion was not rerun or repaired in this change. Recipe generation and pause-button
checks passed. Existing host OSHI/Perflib and development Realms-authentication
warnings are still present; the feature checks and clients completed successfully.

No real user world or installed profile was changed. UI tests ran in isolated game
directories without loading a world. No commit or GitHub push was performed.
Existing unrelated working-tree changes were preserved and are included in these
candidate JARs; this report does not re-certify every prior feature.

## Playable JARs

Back up the world and replace the old TES JAR with only the matching loader artifact.
Do not install a sources JAR or two TES versions together.

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`
  SHA-256: `ee33c1729017c61e7b1eee444685ea8f7ad9e6c1b5d027213dc649b2be4b8eb1`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`
  SHA-256: `0f34a3be852704797cae09716acb7f3b138d90783c134d6d58eefac4bebe4762`
