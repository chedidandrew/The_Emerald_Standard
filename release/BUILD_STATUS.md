# Build status for 0.2.0-alpha.3

## Candidate scope

This candidate closes the highest-priority issues found during the alpha.2 review:

- Established or customized villagers are never repurposed as Bankers.
- Bankers are associated with one persisted village region.
- Legacy unscoped Bankers migrate without allowing nearby banks to share one villager.
- Bank lecterns open the dashboard even when the resident Banker is temporarily unavailable.
- Village banks use biome-aware plains, desert, savanna, snowy, and taiga palettes.
- Player disconnects clear transient transaction cooldown entries.
- Static dashboard actions, status messages, confirmations, and risk labels use translation keys.
- CI validates packaged JAR contents, runs live Banker invariants, and launches both clients under a virtual display.
- Issue templates cover crashes, world-generation problems, and economic-balance feedback.

## Automated verification

The implementation must pass the repository workflow on the exact pushed commit:

- Common regression suite
- Fabric build
- NeoForge build
- Fabric packaged-JAR validation
- NeoForge packaged-JAR validation
- Fabric dedicated-server startup
- NeoForge dedicated-server startup
- Live Banker invariant test on both loaders
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration
- Artifact uploads and fatal-log checks

The final workflow run and artifact provenance will be recorded after the release branch is green.

## Manual publication gate

Automated client bootstrap cannot judge visual layout or execute real human interactions. Before a formal public GitHub, Modrinth, or CurseForge prerelease, complete the manual checklist in `docs/TESTING.md`, including both loaders, multiplayer, journal recovery, and village-biome appearance.
