# Build status for 0.3.0-beta.4

## Candidate scope

Beta.4 adds the one-command debug flight recorder used for hands-on reproduction and support.

The implementation includes:

- `/emerald debug` start/stop toggle with a five-minute default
- Optional duration, marker, and explicit stop subcommands
- Incremental crash-resilient JSON Lines logging
- Sanitized ZIP report packaging and rotation
- Market, portfolio, village, construction, casualty, settler, GUI-action, validation, and performance capture
- Interrupted-session recovery on the next server start
- Fabric and NeoForge lifecycle integration
- Updated README, changelog, architecture, testing, debugging, and release documentation

## Verification gate

The exact candidate commit must pass:

- Common economy, persistence, and Village Prosperity regression suites
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup
- NeoForge dedicated-server startup
- Fabric client bootstrap
- NeoForge client bootstrap
- Fatal-log checks and artifact upload

## Manual beta validation

Use `/emerald debug` during real GUI, market, village, construction, raid, recovery, and multiplayer tests. Share the resulting ZIP together with marker numbers and a short description of what appeared wrong.
