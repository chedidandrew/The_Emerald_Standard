# Build status for 0.2.0-alpha.3

## Release scope

This public alpha closes the highest-priority issues found during the alpha.2 review:

- Established, traded, experienced, customized, named, or otherwise configured villagers are never repurposed as Bankers.
- Bankers are associated with one persisted village region, and legacy unscoped Bankers migrate without losing their existing data.
- Bank lecterns open the dashboard even when the resident Banker is temporarily unavailable.
- Village banks use biome-aware plains, desert, savanna, snowy, and taiga palettes.
- Player disconnects clear transient transaction cooldown entries.
- Dashboard actions, status messages, confirmations, tooltips, and risk labels use translation keys.
- Issue forms cover crashes, world-generation problems, and economic-balance feedback.

## Verified source

Source commit: `14ea4adc5fafe909e23f5964c0c42746b2f7b665`

Pull request: `#1`

GitHub Actions run: `33585169623`

The exact merged source passed:

- Common economy and persistence regression suite
- Fabric 26.2 build
- NeoForge 26.2 build
- Fabric packaged-JAR validation
- NeoForge packaged-JAR validation
- Fabric dedicated-server startup
- NeoForge dedicated-server startup
- Live Banker eligibility and region-identity invariants on both loaders
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration
- Fatal-log checks and artifact uploads

Exact artifact IDs, ZIP digests, and JAR checksums are recorded in `release/ARTIFACTS-14ea4adc.md`.

## Publication status

- GitHub prerelease `v0.2.0-alpha.3`: PUBLISHED FROM VERIFIED ARTIFACTS
- Fabric and NeoForge source JARs: INCLUDED
- Combined SHA-256 checksum file: INCLUDED
- GitHub issue forms: INCLUDED
- Normal-player command dependency: REMOVED

## Alpha limitations

Automated client bootstrap cannot judge visual preference, every GUI scale, or long multiplayer behavior. The mod remains explicitly labeled alpha while broader player testing continues. The repository test checklist remains available for maintainers and contributors, but there are no known automated build, packaged-JAR, dedicated-server-startup, or client-bootstrap failures in this release candidate.
