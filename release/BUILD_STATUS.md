# Build status for 0.3.0-beta.4

## Release scope

Beta.4 adds the one-command debug flight recorder used for hands-on reproduction and support.

The implementation includes:

- `/emerald debug` start/stop toggle with a five-minute default
- Optional 1 to 15 minute duration, marker, and explicit stop subcommands
- Incremental crash-resilient JSON Lines logging
- Sanitized ZIP report packaging and bounded report retention
- Market, portfolio, village, construction, casualty, settler, GUI-action, validation, and performance capture
- Automatic packaging of interrupted sessions on the next server start
- Fabric and NeoForge lifecycle integration
- Updated README, changelog, architecture, testing, debugging, issue-reporting, and release documentation

## Verified source

Source commit: `8881fe957cf5c19d940002921697e39969059d44`

Pull request: `#5`

Full verification workflow run: `33791785017`

Publication workflow run: `33792321436`

## Automated verification

The exact release source passed:

- Common economy, persistence, and Village Prosperity regression suites
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup
- NeoForge dedicated-server startup
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration
- Fatal-log checks and artifact upload
- Prerelease metadata validation
- Byte-for-byte post-publication release-asset verification

## Publication status

- GitHub prerelease `v0.3.0-beta.4`: PUBLISHED
- Fabric playable and source JARs: INCLUDED
- NeoForge playable and source JARs: INCLUDED
- Combined SHA-256 checksum file: INCLUDED
- Artifact provenance: `release/ARTIFACTS-8881fe95.md`

## Manual beta validation

Use `/emerald debug` during real GUI, market, village, construction, raid, recovery, and multiplayer tests. Share the resulting ZIP together with any marker numbers and a short description of what appeared wrong.

Automated client bootstrap proves loading and screen registration, but it does not replace hands-on testing of subjective visual quality, every terrain layout, all mod combinations, or long multiplayer sessions.
