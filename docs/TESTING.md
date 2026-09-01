# Testing and publication gate

## Automated common regression suite

Run:

```bash
bash scripts/run-common-tests.sh
```

The suite verifies:

- Gaussian distribution and deterministic replay
- VILX and individual-company long-run calibration
- Regime persistence and crash behavior
- Villager-lending defaults and term-by-term expected returns
- Commodity movement and resource quotes
- CD and lending maturity
- Pure wall-clock and pure game-tick partial progress
- Mixed wall-clock and game-tick progress without double counting
- Bounded catch-up and banking lockout
- Save and reload equality
- Format 1, 2, and 3 migration
- Future-format rejection with and without a valid older backup
- Required-field and checksum corruption recovery
- Market history and village-bank region persistence
- Inventory journal lifecycle
- Backup preservation, rollback, save retry backoff, spread, caps, and no-debt invariants

## Automated loader verification

GitHub Actions must pass:

- Common economy regression tests
- Fabric 26.2 Gradle build
- NeoForge 26.2 Gradle build
- Fabric dedicated-server development launch
- NeoForge dedicated-server development launch
- Mod startup log detection
- Minecraft server-ready log detection
- Fabric and NeoForge artifact upload

Both loader builds compile the shared `BankerScreen`, menu, village-bank generator, interaction hooks, and translations.

## Manual client checklist

Before a public prerelease:

### Fabric

- Launch Minecraft 26.2 with Fabric Loader and Fabric API.
- Open a new world and an existing world.
- Visit a village and confirm one bank or fallback Banker appears.
- Right-click the Banker and verify all four GUI pages.
- Confirm chart rendering at several GUI scales and window sizes.
- Test every amount preset.
- Test deposit, withdrawal, savings, buying, selling, CD, lending, and exchange actions.
- Fill the inventory and test a withdrawal.
- Close the game during prepared and committed journal test states and verify recovery.

### NeoForge

Repeat the complete Fabric client checklist using NeoForge 26.2.

### Multiplayer

- Connect at least two players to one server.
- Confirm both see the same prices and regime.
- Confirm accounts remain UUID-isolated.
- Confirm only permission-level-2 players can use `/emerald` commands.
- Confirm normal players can use Banker villagers without command permission.
- Confirm one village region does not generate duplicate banks.
- Confirm a lost Banker is replaced when the village is revisited.

## Publication gate

Do not publish a formal GitHub release, Modrinth release, or CurseForge release until:

1. Both automated workflows are green.
2. Both packaged client JARs reach the title screen and load worlds.
3. The full GUI transaction checklist passes on both loaders.
4. Village bank placement is visually reviewed across plains, desert, savanna, snowy, and taiga villages.
5. Multiplayer and journal-recovery checks pass.
