# Build status for 0.4.0-beta.1

## Candidate status

- Baseline: verified `0.3.0-beta.4` main branch
- Candidate branch: `fix/recover-0.4.0-beta.1`
- Final executable source commit: `d22ecefe2e4f4be19bcc7869d1255dc505cbfeea`
- Persistent format: 8
- Final recovery preflight workflow: `33833217344`, successful
- Standard seven-job verification: running for this documentation-only head
- Manual test matrix: `docs/MANUAL_TEST_MATRIX-0.4.md`, not yet completed

## Final hardening included

- Safe recovery of the intended 10-project Village Prosperity update from the verified beta.4 baseline
- Minecraft 26.2 first-visit message API correction
- Duplicate-template and unintended-workstation validation
- Ban on generated emerald, diamond, gold, and netherite blocks
- Format-7 to format-8 migration coverage for the expanded project catalog
- Correct `0.4.0-beta.1` debug-report metadata
- Removal of generated payloads, repository-transform scripts, stale CI files, and self-modifying temporary workflows from the candidate

## Preflight results

The final executable source passed:

- Loader-neutral economy, persistence, Village Prosperity, project-catalog, milestone, and large-world scale regressions
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification

The normal read-only pull-request workflow must also pass both dedicated-server launches and both client bootstrap jobs for this exact documentation head before merge. This file will be updated after merge with the final main-branch workflow and release status.