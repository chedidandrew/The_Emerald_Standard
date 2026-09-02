# The Emerald Standard 0.2.0-alpha.3

This prerelease completes the first casual-player-focused public alpha for Minecraft 26.2 on Fabric and NeoForge.

## Highlights

- A four-page Banker dashboard for overview, market, banking, and commodity exchange actions.
- Interactive market charts, current prices, holdings, sectors, risk labels, news events, confirmations, and hover details.
- Village Bank and Exchange buildings that appear near discovered villages using biome-aware plains, desert, savanna, snowy, and taiga palettes.
- Banker villagers and generated bank lecterns that open the dashboard without normal-player commands.
- Safe fallback Banker selection. Existing professions, offers, XP, custom names, babies, and established villagers are never repurposed.
- Region-scoped Banker identity so nearby village banks cannot share or replace one another's villager.
- Persistent offline market progression, savings, locked-rate CDs, risky villager business lending, dynamic commodities, and fractional investments.
- No player borrowing, debt, negative balances, or obligations beyond voluntarily invested emeralds.
- Crash-recoverable inventory transactions and checksum-protected economy saves.

## Installation

### Fabric

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.158.0+26.2 or newer
- Java 25

Install the Fabric JAR in the client `mods` folder. Multiplayer servers and connecting clients should use the same mod version.

### NeoForge

- Minecraft 26.2
- NeoForge 26.2.0.72 or newer
- Java 25

Install the NeoForge JAR in the client `mods` folder. Multiplayer servers and connecting clients should use the same mod version.

Do not install both loader JARs together.

## Upgrading

The existing versioned economy save and migration path are preserved. Back up the world before replacing an earlier alpha, as is good practice for any prerelease mod.

## Verification

The exact source commit and release assets passed common regression tests, both loader builds, packaged-JAR inspection, Fabric and NeoForge dedicated-server startup, live Banker invariants, and Fabric and NeoForge client bootstrap under a virtual display.

This is still an alpha. Automated client bootstrap verifies that Minecraft, resources, menu registration, and client code initialize, but it cannot replace subjective visual review or long multiplayer playtesting. Report crashes, village-generation problems, and economy-balance feedback through the repository issue forms.
