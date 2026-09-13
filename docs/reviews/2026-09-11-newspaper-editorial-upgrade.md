# Newspaper editorial upgrade validation — 2026-09-11

## Scope

Implemented the accepted newspaper improvements: a front page with published market summary,
clickable headlines and sections; stable article IDs and frozen reading editions; full retained
archive search and visit-local unread stars; distinct outlet coverage and commentary; factual
developing-story follow-ups; multiplayer privacy controls and explicit community-property
designation; category reserves and independently tracked developing stories; validated,
reloadable data-pack wording; and a folded-paper item model using existing Minecraft textures.

No live Minecraft profile, installed mod or user save was changed. The candidate remains
0.4.0-beta.5 for Minecraft 26.2. Existing unrelated worktree changes were preserved.

## Verification

- All 78 common regression entry points passed (`build/news-v2-common.log`). This includes
  the full economic regression suite and new editorial, privacy, migration, archive-flood,
  stable-edition, full-archive-search and developing-story coverage.
- Fabric and NeoForge builds, reader/settings checks and Banker menu packet checks passed
  (`build/news-v2-build-fabric.log`, `build/news-v2-build-neoforge.log`).
- Native client checks passed on both loaders at GUI scales 2 and 4, including actual reader
  controls, incoming-edition acceptance, search beyond the old 64-report batch, all 54 compact
  handbook pages and four animated recipes (`build/news-v2-client-*.log`).
- Final dedicated-server smoke tests passed on both loaders
  (`build/news-v2-final-server-fabric.log`, `build/news-v2-final-server-neoforge.log`).
  These exercised actual container interactions, readonly archive packets, player evidence,
  native SavedData persistence, and community-property add/remove commands. Oversized
  selections were rejected; removing a designation restored automatic property exclusion.
- A pre-start configuration regression verifies that disabling public player reports remains
  effective when loading a save and during startup catch-up.
- Archive transmission tests cover all 256 retained reports and coherent four-document
  editions; partial updates cannot produce a mixed edition. Privacy changes invalidate an
  older reading snapshot rather than waiting for the player to accept new stories.
- Format-31 migration assigns stable IDs without rewriting article text or inventing event
  baselines. Format 32 preserves existing financial values and recorded history.
- Packaged JAR verification passed for both binaries and sources, including the new classes,
  model and default wording resource. `git diff --check` passed.

The native startup harness performs expensive structure-template admission tests and can log
tick-behind warnings during that test. NeoForge also logged Windows OS metrics inspection
warnings. Both final harnesses nevertheless reached their explicit integration PASS markers;
this is not a long-duration multiplayer performance benchmark.

## Handbook and visual review

Updated long-form newspaper/player-report guidance and the compact/lectern text to explain
the implemented controls, evidence limits, privacy, designation commands and reloadable
wording. Reviewed animated recipes against the unchanged Paper + Ink Sac newspaper recipe.
The handbook retains 64 long-form sections, 54 compact pages and four animated recipes;
settings documentation and coverage now reflect 34 world settings.

Native screenshots are under `build/news-v2-client-fabric/screenshots/tes-reader-ci/` and
the corresponding NeoForge directory. The Fabric scale-4 front page and newspaper recipe
were visually inspected: section controls, published quote, clickable headlines and footer
fit, and the new green-masthead newspaper model is visible in the recipe output.

## Limits and handoff

- Unread state is local to the reader visit, not persisted across sessions.
- The archive remains bounded at 256 articles, with up to 64 developing-story records.
- Wording is finite authored content with repeat avoidance, not infinitely unique AI output.
- Follow-ups describe observed prices or simulated village food estimates; neither proves
  physical repairs, resolved shortages, guilt or player intent.
- Automatic ownership evidence has limits. Explicit reporting designation is operator-only
  metadata and does not grant construction permission or alter blocks.
- Server privacy filtering cannot retract information somebody already saw or recorded.
- Back up the whole world before testing. Use matching client/server builds. Do not downgrade
  a save after its format-32 migration.

## Artifact SHA-256

| Artifact | SHA-256 |
| --- | --- |
| Fabric binary | `a2cda1a83966864b56485a21b8ea189f93b860d140b7ddbdfd3aea64def24fe5` |
| NeoForge binary | `75601793dc4a8cfb6c2b9a5a5584a4210f3bad318e2ac41f3290dda50712a364` |
| Fabric sources | `41951f599c25945e429e6e0879bf27b113105a2de156de6ff9f4fe0929d633e6` |
| NeoForge sources | `89c836ce7da12f82b09f673ba4f424f2c3b2c9ae5852ebc3fc97b0ce945cefc9` |
