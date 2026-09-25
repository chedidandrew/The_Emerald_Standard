# Beta.5 growth, lighting and loot validation

Local candidate on `codex/modular-village-architecture`, based on `2928c46`.
Not committed, pushed, published or installed into an existing player world by this pass.
Minecraft 26.2, Java 25; Fabric and NeoForge use `0.4.0-beta.5`.

## Scope

- Automatic open-ended cities through independently bounded districts, paid starter
  homes, real queued settlers, short sustained-health/cooldown gates and rising upkeep.
- Conservative municipal reserve sharing, atomic charter funding/save rollback, durable
  modes and optional owner/operator approval or pause. No directions/no-build-zone UI.
- New-site preparation tolerates ordinary torches and contextual natural tree remnants;
  shallow foundations still bridge small craters. Crafted blocks, storage and protection
  vetoes remain guarded. It is a heuristic, not perfect player-ownership detection.
- Distributed outdoor block-light coverage improves safety recovery, with a capped
  bonus and stale-observation decay. This is not an all-surfaces spawn-proof certificate.
- Peaceful growth boosts and ten low-value role-appropriate, one-shot chest/barrel loot
  tables. Existing containers are not refilled or backfilled.
- Economy format 19 migration; saved architecture is not regenerated. Back up before
  upgrading, and do not downgrade an upgraded save with an older binary.

The full behavior, costs and manual help are described in
[CITY_EXPANSION.md](../CITY_EXPANSION.md) and
[PEACEFUL_GROWTH_AND_LOOT.md](../PEACEFUL_GROWTH_AND_LOOT.md).

## Completed automated checks

- Final `fabric/gradlew build` and `neoforge/gradlew build`, including authored-catalog
  geometry/access/lighting validation, saved-revision preservation, menu packet codecs
  and reader/settings checks. Both completed successfully.
- Both packaged-JAR verification gates passed, including the new expansion/preparation
  classes and ten loot tables; per-loader `build/libs/SHA256SUMS` was refreshed.
- Complete common regression suite, including new district scaling, supply conservation,
  deficit tapering, save rollback, legacy defaults, mode/pause, physical founding and
  lighting-cap/freshness tests.
- Eight paired 1,200-economic-day Peaceful/baseline growth simulations. All Peaceful
  fixtures reached tier 5 and exactly 100 prosperity. These are data simulations, not
  measured in-game completion times or claims of large-city entity performance.
- Fabric and NeoForge opt-in dedicated-server smoke tests, in disposable worlds under
  `build/server-smoke/`, including all ten loot tables, 1,000 weighted rolls per loader
  and native deferred/opened container NBT round trips.
- Real-Minecraft site-policy checks: torches accepted; chest, furnace, crafting table
  and house walls rejected; shallow crater bridged; raw tree remnants distinguished
  from horizontal framing/persistent leaves; cooperative protection veto honored.
- End-to-end founding fixture on both loaders: durable charter, actual economic labor,
  production lot preparation and house materialization, real villager spawning after
  beds exist, and successful completed-project reload. This covers the construction
  lifecycle; it is not an extended survival test of automatic frontier search/pacing.

Two initial attempts at the new end-to-end fixture failed because the server-only
observer had no network connection (teleport/particle delivery). Fixture setup now
positions it directly, within development range but outside particle broadcast range.
Both loaders then passed. Production player networking was not changed.

## Remaining human review

- Visual inspection of the new City expansion page at different GUI scales, and a
  logged-in multiplayer owner/operator-versus-visitor permission check.
- Long-running automatic frontier selection in real terrain, player donations,
  prosperity/upkeep balance and maximum comfortable city size on target hardware.
- Lighting dashboard feedback in a real played village, not just isolated policy tests.

Existing explicit world config values remain untouched. New config defaults use eight
construction blocks per ten-tick pass and 600 ticks between settler attempts. Large
cities still consume entity ticks, memory and full-state save time; there is no fixed
city district-count cap and no promise of physically infinite growth.
