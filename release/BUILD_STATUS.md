# Build status for 0.2.0-alpha.1

## Verified common core

- Java compilation: PASS
- Existing market calibration and no-debt model: PASS and unchanged
- Unified mixed-clock regression: PASS
- Format 1, 2, and 3 migration regression: PASS
- Current-format checksum corruption recovery: PASS
- Empty-primary backup recovery: PASS
- Future-format primary rejection with an older backup present: PASS
- Chart-history persistence: PASS
- Generated-bank region persistence: PASS

## Implemented player experience

- Casual four-page Banker GUI: IMPLEMENTED
- Interactive persistent market charts: IMPLEMENTED
- GUI deposits, withdrawals, savings, investing, CDs, lending, exchange, and recovery: IMPLEMENTED
- Automatic village-bank placement on safe plots: IMPLEMENTED
- Persistent Banker inside generated banks: IMPLEMENTED
- Natural fallback Banker in loaded villages: IMPLEMENTED
- Normal-player command dependency removed: IMPLEMENTED
- `/emerald` restricted to administrators: IMPLEMENTED

## Verified CI and dedicated-server startup

GitHub Actions run `33466544807` built and launched source commit `5379f4c9890fb121a33b3b3a1938fb0b5f7abba0` using Java 25.

- Common regression job: PASS
- Fabric 26.2 Gradle build: PASS
- NeoForge 26.2 Gradle build: PASS
- Fabric artifact upload: PASS
- NeoForge artifact upload: PASS
- Fabric dedicated-server development launch: PASS
- NeoForge dedicated-server development launch: PASS
- Fabric mod startup message observed: PASS
- NeoForge mod startup message observed: PASS
- Minecraft server-ready message observed for both loaders: PASS

Artifact IDs, ZIP digests, and exact JAR checksums are recorded in `release/ARTIFACTS-5379f4c9.md`.

## Manual publication gate

- Fabric client launch and GUI visual test: PENDING
- NeoForge client launch and GUI visual test: PENDING
- Packaged dedicated-server launch test outside the development environment: PENDING
- Village bank visual review across village biomes and difficult terrain: PENDING
- Two-player GUI and account-isolation test: PENDING
- Full journal recovery test using real player inventories: PENDING

The economy, persistence, GUI source, loader builds, and automated dedicated-server startup checks are green. No formal public release should be created until both client builds complete the checklist in `docs/TESTING.md`.
