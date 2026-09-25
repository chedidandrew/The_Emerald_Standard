# Vegetation compatibility (beta.53)

TES uses shared classification, not namespace/name guessing or replaceability alone.
Recognition never overrides inventory, persistent-leaf, crop, player-placement or land protection.

## Datapack extension

Add entries to JSON files under
`data/the_emerald_standard/tags/block/vegetation/` in a datapack appropriate for your
Minecraft version. Keep `"replace": false` to extend existing rules.

Example `ground_cover.json`:

```json
{"replace":false,"values":[{"id":"example:meadow_clover","required":false}]}
```

Tags:

- `ground_cover`: low flowers, grasses and small plants. Used by roads and bridges too.
  Full solid-render blocks, fluids, crops, containers, logs and leaves cannot become ground cover.
- `shrubs`: larger plants cleared only by building-site preparation, not public path surveys.
- `tree_logs`: candidates for the bounded connected-tree survey, never unconditional clearing.
- `tree_leaves`: canopy evidence; leaves must expose a false persistent property.
- `natural_ground`: additional dry terrain candidates for foundation work. This does not
  make every material eligible for public-road paving or bypass terrain/ownership tests.
- `never_clear`: priority veto, including when another compatibility tag also lists the block.

Wilder Flowers `wilderflowers:clovers` and its `wildflowers` tag are optional built-in entries.
Potted plants and decorative garlands are not automatically admitted. No mod is required.

Use `/reload` after installing/changing a datapack. Successful reload clears transient
negative survey evidence and deferred route attempts. Saved building/bridge plans and existing
paving are not redesigned. Write-time protections still apply to previously approved plans.

## Trees and ownership

Tree surveys require connected eligible logs, vertical axis evidence, natural canopy and
non-crafted surroundings. Timber doorway frames and recorded player placements veto removal.
Limits remain 2,048 connected logs, 32 blocks horizontally and 64 vertically from the inspected
trunk, with loaded surroundings. Larger/ambiguous trees stay untouched; these tags do not
provide an unsafe size-limit override.

TES now records individual successful player block placements, including plants; torches
remain excluded from this evidence. Pending writes are protected immediately. Same-block
growth states, tall plant upper halves and recorded saplings becoming trunks retain protection.
Old unrecorded landscaping, growth far from the original position, custom mod transformations,
bone-meal spread and external editing tools may have no reliable provenance. Protect valuable
gardens with a development exclusion area or a compatible claim integration.

## Diagnostics and work bounds

Block IDs/states and rejection coordinates appear in debug survey detail, not normal news.
Unrecognized blocks are obstacles, not candidates for a timer-based removal fallback.
The existing bounded site rejection cache is supplemented by 2,048 negative column entries
per active dimension, shared across building templates/rotations. Entries observe loaded-chunk
identity and nearby block revisions, expire after 1,200 ticks, and clear on resource reload.
These caches may postpone a survey; they never grant placement permission.

Native test runs can supply `TES_TEST_VEGETATION_JAR` to the Fabric smoke harness to load a
local Wilder Flowers JAR in its disposable server. This does not install it in a player profile.
