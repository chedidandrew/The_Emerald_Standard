# Beta.24: village walkway lighting

## Implementation and handbook review

Walkway lighting is a separate finishing pass on completed managed project trails.
Older completed paths are eligible without changes to their existing construction
or trail cursors. It reuses the refined authored yard-lamp builder and the exact
Blueprint project's saved palette; modular legacy paths use village character/biome
materials. All four inward-facing orientations share the same canonical design.

Stations are normally ten route cells apart. The post is three blocks off the
centerline and the lamp arm extends one block back toward it. The opposite verge
is tried when blocked. The full footprint avoids the route's three-wide corridor,
physical path branches, reserved lots, Bank buffers, construction-owned cells,
storage, fluids and development-protection vetoes.

The finishing pass is paced to at most one candidate inspection or two block writes
per second per dimension. It uses loaded chunks and the existing nearby/enabled
development loop. It places onto supported terrain without replacing the ground.
Supplied lamps emit normal light (15), have normal drops and remain editable.
Saved per-dimension receipts preserve partial progress and prevent regeneration
after removal and duplicate posts on shared routes.

Guided terrain and compact planning handbook pages cover appearance, inward arms,
old-path backfill, spacing, safety limits and one-shot modification behavior.
No custom item or recipe is added. Economy format remains 37; the new
`walkway_lighting` SavedData ledger is separate.

## Verification

- All 90 common regression entrypoints passed (`build/beta24-common.log`).
- Production-geometry tests cover 80 village-character/biome/orientation combinations,
  palette references, knee-brace facing, chain support and lantern-last ordering.
- Disposable-world tests exercise old-path placement, two-write budgets, partial and
  completed receipt reload, ordinary removal without regeneration, shared-road
  deduplication, clear walkable space, unchanged ground, storage, protection vetoes,
  living occupants, water, unloaded neighbors and interrupted supports.
- The initial world test exposed a false player-edit detection on vanilla wall
  connection changes. Socket-wall connections now receive the same derived-state
  treatment as fences; removed/replaced supports still stop the optional lamp.
- Additional cases cover opposite-verge fallback, bends/branches, unsupported feet
  and negative-coordinate receipt indexing.

- Fabric and NeoForge full builds passed (`build/beta24-fabric-build.log` and
  `build/beta24-neoforge-build.log`).
- Both dedicated-server integration suites passed with the final source:
  `build/beta24-fabric-server-recheck.log` and
  `build/beta24-neoforge-server-final.log`. Lamp cases passed on both loaders.
- One Fabric run passed the lamp cases but failed a separate existing creative
  dispenser fixture with an empty entity query. The unchanged-source fresh rerun
  passed that fixture and the full suite. This intermittent test result is retained
  in `build/beta24-fabric-server-final.log`; it was not suppressed.
- Native Fabric client checks passed for 61 handbook pages, guided layout and
  the compact book at tested GUI scales (`build/beta24-fabric-client.log`).
- Candidate verification passed version, current-source and cross-loader parity.
  Native world tests verify lamp geometry and placement; no shader-specific
  nighttime visual inspection was performed in the user's profile.

## Candidate artifacts

Version: `0.4.0-beta.24`.

- Source SHA-256:
  `ca0e1a883ec7f9a4cec81862a952280a85ccdb6f355db318cf0965f7fe9ae5e5`.
- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.24.jar`
  SHA-256 `6494289c796323e466d416009358d335cafef23468f3fb9f111d861e4100b488`.
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.24.jar`
  SHA-256 `abf49df4de14f231992a25794dd9599afdcc9c214112ebae542ecdb829ae4784`.

The installed Modrinth profile and player saves have not been changed.
