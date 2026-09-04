# 0.4.0-beta.1 Recovery Audit

## Why recovery was required

The draft `milestone-95` branch was 38 commits ahead of the last verified main branch and mixed intended gameplay changes with generated payload files, self-modifying GitHub Actions workflows, transformation scripts, stale failure evidence, and an API call that did not compile on Minecraft 26.2.

## Preserved gameplay work

- Ten compatible Village Prosperity project identifiers
- Need-driven project planning
- Biome-aware physical templates
- Bounded production and upkeep effects
- First-Banker onboarding
- Qualitative local village outlook
- Project-catalog and large-world regression coverage
- Honest manual test matrix

## Repairs applied

- Replaced the unavailable `ServerPlayer.displayClientMessage` call with the supported server system-message API.
- Removed a duplicate Inn coordinate where a crafting table overwrote a bed head and blocked materialization.
- Replaced accidental barrel, lectern, and cartography-table job sites in non-industrial prosperity templates.
- Removed the Exchange Hall emerald block and added validation that no prosperity template generates emerald, diamond, gold, or netherite blocks that could be converted into bank capital.
- Advanced persistence to format 8 so beta.4 readers reject the expanded project catalog before attempting project deserialization or stale-backup recovery.
- Updated the debug flight-recorder version label to `0.4.0-beta.1`.
- Added live template uniqueness and unintended-workstation validation to the dedicated-server integration smoke test.
- Reused the same broad village-fundamental score for both market math and the qualitative Village-page indicator.
- Moved the Village support button so the new outlook line does not overlap it.
- Restored the standard read-only build workflow and integrated scale testing without branch-mutating automation.
- Repaired contradictory README, changelog, Village Prosperity, and release documentation.

## Deliberately excluded

- `.github/milestone-payload/*`
- One-shot apply, export, inspect, patch, sanitize, and finalizer workflows
- Repository-transform scripts
- Generated CI status documents describing superseded failed commits
- Unmerged portfolio, custom Banker, reconciliation, and other side-branch experiments

Those experimental branches remain available for later review, but none is silently included in this recovered candidate.
