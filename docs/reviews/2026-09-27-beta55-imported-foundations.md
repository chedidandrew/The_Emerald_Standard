# Beta.55: imported foundations and entrance access

## Scope and behavior

Imported vanilla plans previously bypassed TES foundation generation. Their normalized
floor level also differs from the generic approach assumption, and the jigsaw axis need
not align with a recessed or lowered doorway. This change leaves the imported template,
hash, block order and existing construction cursor unchanged.

- A separate persisted, snapshot-checked terrain plan supplies bounded foundations,
  landing clearance and correctly elevated/facing approach stairs.
- Survey actual at/below-grade bearing columns, not roofs and foliage. Preserve native
  cellar openings. New landing supports cannot silently terminate in an authored void.
- Follow native porch steps to real doors; retain gates, half-slab floors and paired
  inverted-stair arches. Keep new access inside the reserved template/entrance envelope.
- New sites must pass the foundation/access survey. Required work is outside optional
  decoration timeout waivers and is checked again before construction completion.
- Completed imported projects receive one separately saved additive repair. Changed
  footings, protected terrain, inventories, fluids and incompatible blocks defer that
  repair. No house rebuild, template replacement, container refill or ongoing regeneration.
- Both normal and forced development use the same protections and saved plan. No forced
  chunk loading. Individual writes remain budgeted; unloaded work waits without becoming
  a permanent obstruction. Interrupted unfinished work can replay unchanged approved
  before-states if its saved cursor got ahead of chunk writes.
- Save format 43 adds only the terrain plan, cursor, completion and failure fields.
  Older saves default to unsurveyed; relocation clears the annex with the old reservation.

## Validation

- Common regression suite: pass, including saved imported-plan/cursor round trips,
  handbook assertions, loader version parity and existing development protections.
- Fabric and NeoForge `build`: pass, including their existing authored-structure,
  attachment, menu, reader, construction-fence and vanilla-resource gates. Final Fabric
  `assemble` also passed after the test-only lighting synchronization change.
- Final focused dedicated-server scripts: exit 0 / PASS on both Fabric and NeoForge.
- Focused dedicated-server suite: 168 vanilla catalog structures at their assigned
  rotations, plus 20 raised small-house construction/legacy-repair cases (five styles
  times four rotations). Uses production placement, persisted restart, mixed normal/
  forced pulses, survival checks, empty-container checks and actual villager navigation.
- Additional terrain fixture: 16 rotated 1–4-block slope/recessed-landing cases and 20
  real five-style rotated preflight plans. Checks staircase facing/elevation, no roof
  pillars, frozen-plan serialization, exact chest-obstruction rejection and refusal of
  excessive six-block drops.
- Removed the old native test's two manually supplied landing blocks. Visitors now start
  on exterior ground, so the generated access must provide the connection itself.
- The full NeoForge catalog initially exposed stale crop-lighting results when the same
  chunks were synchronously reused inside one fixture tick. The farm passed alone; two
  full runs failed before a test-only lighting barrier was added. The full run then
  passed. No crop-survival rules were weakened. The fixture now waits (with a bounded
  timeout) for pending light tasks before each production construction pulse.

Logs are local build artifacts: `build/terrain-common.log`,
`build/terrain-native-focused.log`, `build/terrain-neoforge-focused.log`,
`build/terrain-fabric-build.log`, and `build/terrain-neoforge-build.log`.

## Handbook and limitations

Reviewed and updated both guided Projects text and the compact vanilla-design pages in
`en_us.json`; regression assertions cover bounded foundations, joined entrances,
deferred repairs, immutable plans and the absence of timeout waivers. Recipes, creative
catalog entries and economy balancing are unchanged.

This is dedicated-server/geometry validation, not a new in-game visual playthrough.
No user save or installed Modrinth profile was modified. Deep ravines and obstructed or
player-altered sites can still be rejected/deferred intentionally. A deferred repair is
not reported as a successful path connection. The district-name, desert-path-material
and logo requests remain on hold at the user's direction.
