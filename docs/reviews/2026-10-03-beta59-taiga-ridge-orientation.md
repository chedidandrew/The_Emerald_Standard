# Beta 59: bark-facing Taiga roof ridges

Review branch: `codex/biome-architecture-preview`; no main-branch merge.

## Scope and implementation

The transverse gable generator builds a ridge along X, but its slab cap previously
inherited the generic spruce-log conversion's Z axis. This showed repeated end grain
along the long roof side. Only that Taiga cap course now uses spruce logs with axis X.
Longitudinal gables already use axis Z correctly and remain unchanged. Slopes, gable
ends, overhangs, walls, furnishings and all block coordinates remain unchanged.

The exported comparison finds 260 log-axis changes in 17 Taiga designs. All 300
non-Taiga assets have identical uncompressed hashes to beta 58. The other 58 Taiga
designs are unchanged. Access targets, circulation, dimensions, bed counts and
visual stages remain the same.

## Save compatibility

Beta-58 ordinary revision 12 and Bank versions 13–15 remain immutable and packaged.
New approvals use ordinary revision 13 and Bank versions 16–18. Catalog lookup,
geometry/cell/exit caches, physical housing counts and entrance handling are keyed
by the saved revision. No save schema, construction phase, economy rules or
protection rules changed. Existing buildings and already-approved projects are not
automatically rotated or rebuilt. Inspect new construction or regenerate a review
gallery to see the corrected ridge. Use a world copy for testing; reverting a JAR
does not undo construction in a save.

## Handbook accuracy review

Updated the guided regional-architecture chapter and its compact/lectern counterpart
to explain lengthwise ridge logs and bark on the long sides. Existing guidance about
frozen projects, world copies and rollback remains accurate. Added regression
coverage for both texts. No blocks/items or recipes were added or changed; the
animated recipe pages and server recipes therefore need no edits.

## Validation

- Exact comparison of all 375 current designs with the approved review geometry.
- Both archived revision 12 and current revision 13 pass native support, door,
  ladder, bed, circulation and supported-construction-sequence admission.
- Ridge-only delta assertion rejects changed positions, unrelated states or
  non-Taiga changes; rotation/mirror matrix preserves the longitudinal log axis.
- Core regression coverage preserves beta-58 descriptors, parcel dimensions and
  physical bed counts while selecting revision 13 for new approvals.
- Live smoke fixtures include new ordinary/Banks across all five styles and saved
  Taiga revision 12 plus all three beta-58 Bank versions, with partial restart,
  construction, operational desk, floor integrity and road/walking checks.

### Executed results

- Full common regression suite: PASS; current-source architecture selection and
  handbook-resource tests rerun after the descriptor-cache change: PASS.
- Fabric full build: PASS (`build/beta59-fabric-final.log`, 5m 12s).
- NeoForge full build: PASS (`build/beta59-neoforge-build.log`, 5m 16s); final
  assembly after the descriptor-cache change: PASS.
- Focused dedicated-server integration: PASS on both loaders, 20 yielding
  fixtures each (`build/beta59-{fabric,neoforge}-live.log`). New and archived
  Taiga construction retain exact spruce-log states through rotation and restart.
- Both packaged JARs match the current canonical source hash and all 750 native
  asset hashes (375 archived + 375 current); metadata/resource verification: PASS.
- Original v12 assets have no Git diff; local and remote main remain unchanged.

An initial Fabric verification overlapped with smoke-run recompilation and reported
a missing lazily loaded nested review class. The isolated full rerun passed; no
geometry/code workaround was applied for this tooling overlap. Disposable smoke
worlds only were used; the user's Minecraft profile and saves were not modified.

Canonical source SHA-256:
`6c5234039e2a3333ae615444be5f60c2b0342ebcc3929a644d5806d14418f1eb`

Fabric binary SHA-256:
`bdbc50d1f307ba4b1255d3ce487a11f43fd4167e740b35070a6c62cff7705775`

NeoForge binary SHA-256:
`3ef6f1649abcafe5b80c04a7342721cba2128fe338201463af8007e34fcf1f7f`

Test artifacts are staged under `build/release-beta59/{fabric,neoforge}/`; beta-58
artifacts were retained. These local test JARs are not a GitHub release or a merge.
