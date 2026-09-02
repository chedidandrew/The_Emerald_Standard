# Testing and publication gate

## Automated common regression suite

Run:

```bash
bash scripts/run-common-tests.sh
```

The suite verifies:

- Gaussian distribution and deterministic replay
- VILX and individual-company long-run calibration
- Deterministic market-event frequency, targeted impacts, and VILX constituent weights
- Regime persistence and crash behavior
- Villager-lending defaults and term-by-term expected returns
- Commodity movement and resource quotes
- CD and lending maturity
- Pure wall-clock, pure game-tick, and mixed-clock progression
- Bounded catch-up and banking lockout
- Save and reload equality
- Format migration and future-format rejection
- Required-field and checksum corruption recovery
- Market history, market events, bank regions, and Banker anchors
- Inventory journal lifecycle
- Backup preservation, rollback, save retry backoff, spread, caps, and no-debt invariants

## Automated loader verification

GitHub Actions must pass:

- Common economy regression tests
- Fabric 26.2 Gradle build
- NeoForge 26.2 Gradle build
- Packaged Fabric JAR content and language validation
- Packaged NeoForge JAR content and language validation
- Fabric dedicated-server development launch
- NeoForge dedicated-server development launch
- Live Banker invariant test on both servers
- Minecraft server-ready log detection
- Fabric client bootstrap under a virtual display
- NeoForge client bootstrap under a virtual display
- Client screen-registration log detection
- Artifact upload for both loaders and all smoke logs

The live Banker invariant test proves that an untouched unemployed adult can become a region-scoped Banker while an established farmer and a custom-named villager cannot be repurposed.

## Manual client checklist

Automated bootstrap confirms loading and registration, but it does not replace visual and hands-on testing.

### Fabric

- Launch Minecraft 26.2 with Fabric Loader and Fabric API.
- Open a new world and an existing world.
- Visit a village and confirm one bank or fallback Banker appears.
- Right-click the Banker and the bank lectern.
- Verify all four GUI pages.
- Test GUI scales and window sizes, especially the smallest supported scaled height.
- Confirm chart rendering, hover values, high/low labels, news, sectors, and tooltips.
- Test every amount preset.
- Confirm sell-all, early CD closure, and lending funding require a second click.
- Test deposit, withdrawal, savings, buying, selling, CD, lending, and exchange actions.
- Fill the inventory and test a withdrawal.
- Force recovery with insufficient inventory space and confirm nothing drops into the world.
- Close the game during prepared and committed journal states and verify exact-once recovery.
- Confirm an established villager is never converted into a Banker.

### NeoForge

Repeat the complete Fabric client checklist using NeoForge 26.2.

### Multiplayer

- Connect at least two players to one server.
- Confirm both see the same prices, news, and regime.
- Confirm accounts remain UUID-isolated.
- Confirm only permission-level-2 players can use `/emerald` commands.
- Confirm normal players can use Bankers and bank lecterns.
- Confirm nearby village regions do not share a Banker.
- Confirm one village region does not generate duplicate banks.
- Confirm a lost Banker is replaced at the persisted bank counter.
- Confirm the dashboard closes outside eight blocks.
- Edit each world configuration setting, reload it, and verify invalid values are rejected without replacing the active configuration.

### Village appearance

Review generated banks in plains, desert, savanna, snowy, and taiga villages, plus coastal, mountainous, terraced, cave-adjacent, and modded terrain.

## Publication gate

A public prerelease requires:

1. Green automated workflow results for the exact release-candidate commit.
2. Both packaged client JARs reaching the title screen and loading worlds.
3. The complete GUI transaction checklist passing on both loaders.
4. Visual review of all village-biome palettes.
5. Multiplayer account-isolation and journal-recovery checks passing.
6. Recorded artifact IDs and SHA-256 checksums for the chosen source commit.
