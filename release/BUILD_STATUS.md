# Build status for 0.3.0-beta.3

## Publication status

- Source commit: ddcb27fe658b7d76dabf6db9b7e5a8c47e110b1d
- Full verification workflow run: 33709943085
- GitHub prerelease: PUBLISHED as v0.3.0-beta.3
- Fabric binary and source JARs: PUBLISHED FROM VERIFIED ARTIFACTS
- NeoForge binary and source JARs: PUBLISHED FROM VERIFIED ARTIFACTS

Exact workflow artifact IDs and digests plus release JAR SHA-256 checksums are recorded in release/ARTIFACTS-ddcb27fe.md. The release assets were downloaded from the successful full build for this exact source commit and were not rebuilt by the publisher.

## Save compatibility

Beta.3 writes save format 7. Beta.1 and beta.2 format-6 worlds upgrade automatically, but those older builds reject a world after beta.3 saves it. Restore a pre-upgrade backup if a downgrade is required.

## Verified gate

The exact source passed the loader-neutral economy, persistence, and Village Prosperity regression suites; both loader builds and packaged-JAR inspections; both dedicated-server startup checks; both client bootstrap and screen-registration checks; fatal-log checks; and artifact upload.

## Known durability boundaries

- Project materialization trusts persisted prefix/completion progress and does not repair already-counted blocks removed by a player or lost through chunk rollback.
- Economy-file bank markers and Minecraft chunk saves are not atomic together. A crash in that window falls back to an eligible Banker rather than rebuilding blocks whose ownership is unknown.

## Remaining beta validation

Automated checks do not certify subjective structure appearance, every GUI scale, unregistered third-party claim systems, unusual modded terrain, long multiplayer construction sessions, or very large persistent worlds. Village Banks remain Overworld-only. This release remains a prerelease while wider hands-on testing continues.
