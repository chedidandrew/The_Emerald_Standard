# The Emerald Standard 0.4.0-beta.1

This beta is the first major 9.5-milestone gameplay pass. It focuses on making the existing Village Prosperity simulation substantially more visible in normal Minecraft play.

## Highlights

- Ten curated physical village project types, up from three.
- Need-driven development priorities instead of one fixed project order.
- New Market Square, Smithy, Granary, Guard Post, House, Inn, and Exchange Hall templates.
- Relevant production bonuses remain bounded and feed the existing market-fundamentals system.
- Village dashboard now gives a simple local economic-impact signal.
- New regression test protects project count, costs, and bounded template estimates.

## Compatibility

- Minecraft 26.2.
- Fabric and NeoForge.
- Existing beta.4 project identifiers are preserved for world compatibility.
- No player loans, debt, or negative balances were introduced.

## Still required before stable 1.0

The repository's manual test matrix remains authoritative for GUI scales, difficult terrain, claim-mod integrations, multiplayer concurrency, crash windows, long-session play, and very large stored-village counts. Automated CI can validate code paths and startup, but it cannot replace those hands-on checks.


## Recovery hardening

The initial experimental milestone branch contained generated payloads, self-modifying workflows, and a Minecraft 26.2 compile error. The release candidate was rebuilt from the last verified beta.4 main branch and includes only reviewed production source, tests, and documentation. It also fixes a duplicate Inn placement and removes accidental villager-workstation blocks from non-industrial templates.
