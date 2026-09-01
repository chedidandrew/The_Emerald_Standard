# Build status for 0.2.0-alpha.1

## Locally verified common core

- Java compilation: PASS
- Existing market calibration and no-debt model: unchanged
- Unified mixed-clock regression: PASS
- Format 1, 2, and 3 migration regression: PASS
- Current-format checksum corruption recovery: PASS
- Empty-primary backup recovery: PASS
- Future-format primary rejection with an older backup present: PASS
- Chart-history persistence: PASS
- Generated-bank region persistence: PASS

## Feature implementation

- Casual four-page Banker GUI: IMPLEMENTED
- Interactive persistent market charts: IMPLEMENTED
- GUI deposits, withdrawals, savings, investing, CDs, lending, exchange, and recovery: IMPLEMENTED
- Automatic village-bank placement: IMPLEMENTED
- Natural fallback Banker in villages: IMPLEMENTED
- Normal-player command dependency removed: IMPLEMENTED
- `/emerald` restricted to administrators: IMPLEMENTED

## CI verification pending

The feature branch must pass:

- Common regression job
- Fabric 26.2 Gradle build
- NeoForge 26.2 Gradle build
- Fabric dedicated-server launch smoke test
- NeoForge dedicated-server launch smoke test
- Fabric artifact upload
- NeoForge artifact upload

## Manual publication gate

- Fabric client launch and GUI test: PENDING
- NeoForge client launch and GUI test: PENDING
- Packaged dedicated-server launch test: PENDING
- Village bank visual review across village biomes: PENDING
- Two-player GUI and account-isolation test: PENDING
- Full journal recovery test using real player inventories: PENDING

No formal public release should be created until CI is green and both client builds complete the checklist in `docs/TESTING.md`.
