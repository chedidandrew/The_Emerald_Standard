# Build status for 0.4.0-beta.1

## Repository status

- Baseline: verified `0.3.0-beta.4` main branch
- Recovery pull request: `#8`, merged
- Final merge commit: `9a7522e35c71db0d214cfdd586cfc68a42e08150`
- Final executable recovery commit before squash: `d22ecefe2e4f4be19bcc7869d1255dc505cbfeea`
- Persistent format: 8
- Manual test matrix: `docs/MANUAL_TEST_MATRIX-0.4.md`, not yet completed
- Public `0.4.0-beta.1` prerelease: not published

## Verified workflows

- Final recovery preflight: `33833217344`, successful
- Final pull-request verification: `33833361744`, successful
- Final merged-main verification: `33833613687`, successful

All seven standard jobs passed for the merged source:

- Loader-neutral economy, persistence, Village Prosperity, project-catalog, migration, milestone, and 100/500/1,000-village scale regressions
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup and live template integration checks
- NeoForge dedicated-server startup and live template integration checks
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration

## Recovery and hardening included

- Rebuilt the intended 10-project Village Prosperity update from the verified beta.4 baseline
- Corrected the Minecraft 26.2 first-visit message API
- Fixed the duplicate Inn placement that could block construction
- Removed accidental villager job sites from non-industrial prosperity templates
- Removed generated bankable currency blocks and added live validation against emerald, diamond, gold, and netherite blocks
- Added duplicate-template, project-catalog, format-7 to format-8 migration, and large-world regression coverage
- Synchronized the Village dashboard outlook with the market's broad village-fundamental calculation
- Corrected GUI control overlap and debug-report version metadata
- Excluded generated payloads, repository-transform scripts, self-modifying workflows, and stale CI output from `main`
- Closed the broken experimental pull request `#6` without merging it

## Publication boundary

The recovered source is build-verified and merged. It remains an unreleased beta candidate until the hands-on Fabric and NeoForge test matrix is completed. Automated startup cannot certify subjective structure appearance, every GUI scale, unusual terrain, claim-mod integration, multiplayer concurrency, or long-session world behavior.