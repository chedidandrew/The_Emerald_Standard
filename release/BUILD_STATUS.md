# Build status for 0.2.0-alpha.2

## Locally verified on 2026-09-01

- Common Java 21 compilation: PASS
- Economy calibration, deterministic event, VILX weighting, lending, and no-debt regressions: PASS
- Format 1 through 4 migration into format 5: PASS
- Checksum, backup, future-format, clock, catch-up, journal, rollback, and retry regressions: PASS
- Market history, last-event, generated-bank region, and Banker-anchor persistence: PASS
- Fabric 26.2 Gradle build with Java 25: PASS
- NeoForge 26.2 Gradle build with Java 25: PASS
- Fabric 26.2 dedicated-server development startup: PASS
- NeoForge 26.2 dedicated-server development startup: PASS

## Implemented hardening and player experience

- Banker spawn point moved behind the counter, with persisted replacement anchors: IMPLEMENTED
- Vanilla librarian profession and configurable home restriction: IMPLEMENTED
- Same-scan region deduplication and safe chunk-before-height checks: IMPLEMENTED
- Server-authoritative interaction-distance validation: IMPLEMENTED
- Risky-action confirmations, rate/risk tooltips, and live control refresh: IMPLEMENTED
- Chart scale labels, hover inspection, sectors, news, and real market events: IMPLEMENTED
- Weighted VILX behavior and targeted company/commodity event shocks: IMPLEMENTED
- Journal-protected inventory overflow with no world item drops: IMPLEMENTED
- Account-local rollback snapshots and configurable transaction cooldown: IMPLEMENTED
- Strict world-local configuration with administrator show/reload commands: IMPLEMENTED

## GitHub verification

The `Build, test, and launch` workflow is the source of truth for the pushed commit. It runs the common regression suite, both loader builds, both dedicated-server development launches, startup-log checks, and artifact uploads. Exact artifact provenance should be recorded only for a release candidate chosen after that workflow passes.

## Manual publication gate

- Fabric client launch and GUI visual test: PENDING
- NeoForge client launch and GUI visual test: PENDING
- Packaged dedicated-server launch outside the development environment: PENDING
- Village bank visual review across village biomes and difficult terrain: PENDING
- Two-player GUI and account-isolation test: PENDING
- Full live-inventory journal recovery and no-drop overflow test: PENDING

No formal public release should be created until both client builds complete the checklist in `docs/TESTING.md`.
