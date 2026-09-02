# Build status for 0.3.0-beta.3

## Scope

This beta completes the remaining hardening work identified after the Village Prosperity beta.2 audit:

- Cooperative construction placement vetoes for claim and protection integrations
- Blocked-project retry backoff
- Nearby-only settlement snapshots during physical development
- Settlement-count-aware economic catch-up batches
- Improved player-caused casualty attribution
- Lightweight visual resident work cues without authoritative custom AI
- Fabric and NeoForge main-hand interaction parity
- Updated regression, architecture, testing, changelog, and release documentation

## Verification gate

The release workflow must use artifacts from a successful main-branch run of `.github/workflows/build.yml` for the exact tagged source commit. That run must pass:

- Loader-neutral economy, persistence, and Village Prosperity regression suites
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric and NeoForge dedicated-server startup
- Fabric and NeoForge client bootstrap and screen registration
- Fatal-log checks and artifact upload

## Remaining beta scope

Automated verification does not replace broad hands-on testing across land-claim mods, modded terrain, unusual village layouts, GUI scales, long multiplayer construction sessions, and very large persistent settlement counts. The release remains a prerelease until those environments receive wider player testing.
