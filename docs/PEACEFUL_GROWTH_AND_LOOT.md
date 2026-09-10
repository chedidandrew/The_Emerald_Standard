# Peaceful village growth and structure loot

Introduced in the unreleased `0.4.0-beta.5` source candidate for Fabric and NeoForge on Minecraft 26.2.
Subsequent city-expansion and farm-food work advances economy saves to format 21 with persistent progressive Bank plans; saved architecture revisions remain unchanged.

## Automatic Peaceful growth

Use Minecraft's normal Peaceful difficulty setting. No new mod setting or command is required.
The server's world difficulty is authoritative; this is not a separate per-player perk. Switching
away from Peaceful restores the normal rates on the next server tick without deleting prior gains.

| Village simulation component | Peaceful behavior |
|---|---|
| Abstract sector production | 1.75x the otherwise equivalent output |
| Food consumption | 65% of normal (35% lower); spoilage still applies |
| Development points | 3.5x generation at otherwise equal village conditions |
| Project workforce | 3.5x workforce input; existing minimum work and extra development spending still apply |
| Eligible population growth | 20x normal daily chance, approximately 2.8–6.4% per economic day; at most one new settler per day |
| Prosperity | Target +12, capped at 100; approach rate 0.16 instead of 0.08; snaps from 99.5+ to 100 only when the target is 100 |
| Safety recovery | Base +0.75 instead of +0.035 per economic day, plus existing security contribution, after the incident stabilization window |
| Local random setbacks | No harvest/trade disruption rolls; positive material discoveries remain |

This helps settlements become tier-5 Regional Centers and sustain full prosperity. It is not instant
completion: food, housing, project costs, safety and lifecycle requirements remain. Actual injuries,
demolition and starvation still matter. Disabled simulation stays disabled; the separate visual-only
pulse keeps its previous behavior. Physical construction uses independent two-blocks-per-second site allowances,
collision/protection checks and loaded-chunk requirements. Settlers still wait for valid physical
homes and census reconciliation. Easier simulation does not mean immediate block placement.

Easy, Normal and Hard keep their previous shared baseline. Financial accounts do not receive a
difficulty-based payout. Village market effects remain capped, and the counterfactual villages used
to isolate player-caused economic damage receive the same difficulty profile as real villages.

The newer [farm and livestock bonus](VILLAGE_FOOD_SOURCES.md) applies on all difficulties and
stacks with this Peaceful profile. It changes agriculture output, not physical chest loot.

Rates use the configured economic clock (normally one Minecraft day, about 20 real minutes), not one
server tick. Trusted offline catch-up, when enabled, uses the difficulty selected when the world is
loaded; there is no stored history of difficulty changes during the offline interval.

## Building-appropriate chest and barrel loot

Newly placed storage in normal village projects and successfully committed Village Banks receives a
standard deferred Minecraft loot table. Each container rolls **2–4 weighted entries** in small stacks.
Repeated entries are possible, and tools are ordinary unenchanted stone tools. There is no direct
emerald loot, diamond/netherite equipment, enchanted gear or high-value vault treasure.

| Building role / table | Possible contents |
|---|---|
| Cottage and House / `residence` | Bread, apples, wheat seeds, sticks, string, flower pot |
| Inn / `inn` | Bread, baked potatoes, cooked cod, bowls, candles |
| Warehouse / `warehouse` | A few oak planks, sticks, string, leather, coal |
| Mine Entrance / `mine` | Torches, coal, cobblestone, raw copper, stone pickaxe |
| Market Square / `market` | Apples, carrots, bread, paper, leather |
| Smithy / `smithy` | Coal, iron nuggets, copper ingots, occasional 1–2 iron ingots, stone axe |
| Granary / `granary` | Wheat, wheat seeds, carrots, potatoes, beetroot seeds |
| Guard Post / `guard_post` | Arrows, bread, torches, leather, stone sword |
| Exchange Hall / `exchange_hall` | Paper, books, ink sacs, feathers, candles |
| Village Bank / `bank` | Paper, ink sacs, feathers, books, candles |

The table IDs are `the_emerald_standard:chests/village/<table>`. Data packs can override the bundled
files at `data/the_emerald_standard/loot_table/chests/village/<table>.json` using Minecraft's normal
loot-table behavior. This feature does not inject loot into vanilla village buildings.

### One-shot placement and compatibility

- Only newly created ordinary/trapped chests and barrels are eligible. Ender chests, furnaces and
  other inventories are excluded. Decorative barrels in a new project use that project's role.
- Assignment happens only after a new protection-approved block is successfully placed; the
  existing-cell path, audits, repairs, maintenance and entrance retrofits do not assign loot.
- Existing contents and already assigned deferred tables are preserved. Old empty containers,
  including those in existing test worlds, are deliberately not backfilled.
- Minecraft rolls contents on first access and saves the resulting inventory. Taking items does
  not refill the container. Restarting, changing difficulty or raising the village tier does not
  refill it; a later genuinely new annex container can receive its own initial loot.
- Progressive Banks attach initial loot only when construction creates a fresh container after
  the Bank plan is durably reserved. Matching existing/player containers are never seeded during
  resume or completion. Native chunk saving stores the table/seed alongside the container.
  No recurring backfill is used to guess whether a chest was looted.
- Architectural gallery previews remain geometry demonstrations, not production loot-spawn tests.

## Can a village grow forever?

The latest source candidate supports **open-ended cities through additional districts**.
Each district retains tier 5, 64 simulated residents (including queued settlers), 12
prosperity projects and at most six housing projects. The city has no fixed district-count
cap, but increasing upkeep, reserves, real housing and safe loaded land naturally limit its
growth. See [Automatic city expansion](CITY_EXPANSION.md) for defaults, donations, lighting,
controls and performance limits. This supersedes the earlier finite-per-village answer.

## Verification scope

- Eight deterministic 1,200-day paired growth simulations: Peaceful reached tier 5 and exactly 100
  prosperity in every fixture, with 40–44 residents and 11 projects versus 7–13 baseline residents.
  These are accelerated data-only tests, not a promised real-game completion time.
- Regression checks retain housing/population/project bounds, physical settler queuing, baseline
  overload equivalence, switching back to normal and consistent market-shadow difficulty.
- Isolated dedicated-server tests on both loaders load all ten tables, roll 100 seeds per table,
  check the exact role whitelist and modest quantities, and round-trip deferred and opened
  chest/barrel inventories through Minecraft NBT. Taken loot remains empty after reload.
- Existing saved worlds are not edited by these tests. Live long-term village gameplay and a
  human balance review remain distinct from automated simulation and server integration checks.
