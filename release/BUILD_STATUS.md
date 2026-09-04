# Build status for 0.4.0-beta.1

## Candidate status

- Baseline: verified `0.3.0-beta.4` main branch
- Candidate branch: `fix/recover-0.4.0-beta.1`
- Recovered source commit: `0d43fe2d52fe96769ca2520229eeff4d9ebbd560`
- Recovery preflight workflow: `33832134481`, successful
- Standard pull-request verification workflow: `33832278785`, successful
- Manual test matrix: `docs/MANUAL_TEST_MATRIX-0.4.md`, not yet completed

## Automated results

All seven standard jobs passed for the recovered candidate source:

- Loader-neutral economy, persistence, Village Prosperity, project-catalog, milestone, and large-world scale regressions
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup and live integration smoke test
- NeoForge dedicated-server startup and live integration smoke test
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration

The live server smoke test now enumerates every prosperity template and rejects duplicate coordinates or unintended barrel, lectern, and cartography-table job sites.

## Publication boundary

The candidate is eligible to merge as a recovered public beta after the documentation-only verification commit passes. The manual matrix remains required before a stable 1.0 release.