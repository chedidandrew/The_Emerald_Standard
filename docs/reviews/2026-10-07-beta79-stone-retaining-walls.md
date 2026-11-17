# Beta79 exterior retaining walls

Fresh mod-generated exterior retaining courses now use regular `minecraft:stone` when
their terrain palette would otherwise supply `minecraft:polished_andesite`. The shared
finishing pass serves both village projects and Banks. Only its outside border courses
change: authored building/interior blocks, road backing, stairs and other retaining palettes
keep their materials. No architecture re-export or revision change is needed.

All guards still apply before a wall is frozen and again before a world write. Existing
frozen receipts, completed walls and player-placed blocks are not scanned or replaced.
The screenshot's existing wall is therefore not automatically recolored by this update.
No live world, installed profile, source history or offloaded directory junction was changed.

Handbook review: the guided Terrain section explains the exact outside-only scope and
saved-plan limits. The compact page includes stone walls while retaining earth slopes and
historical safety guidance. Native page-fit checks are required before packaging.
Recipes/animations and server definitions were reviewed: no item or recipe changes;
the four Survival recipes and creative-only eggs remain unchanged.

Native tests cover all 48 grounded retaining courses, preserved interior receipt, unchanged
road backing, claim veto, read-only surveying, old/new receipt encode/decode, slope access,
crater supports, storage, local natural grounding, loaded chunks, paced replay and restart.
Validation logs and packaged identities are recorded below after release verification.

## Completed validation

- All shared regressions, loader parity and pinned wrapper checks passed. Final handbook,
  terrain wiring and handbook mechanics checks passed again after compact wording was fitted.
- Fabric and NeoForge dedicated-server terrain suites passed the new outside-only material
  regression, grounding, natural-seed routes, actual villager traversal and restart/replay.
- Native client checks passed on both fresh start and restart: compact terrain page bounds,
  every guided chapter, font wrapping, topic search and saved preferences. The compact page
  remains eight lines; older-plan limits stay in the guided terrain section and other pages.
- Both assemblies succeeded; packaged versions and current-source fingerprints match.
  Each loader retains all 12,375 exact current/historical architecture assets.
- Staged binary/sources pairs passed metadata/class checks with SHA256SUMS.
  `git diff --check` passed. Release folder: `build/release-beta79` (through the existing
  C: junction to G:). No install, live-world conversion, commit or push was performed.

Source identity: `e1b741a8b3e6637ee6c4a783596be5828a9966b52f4f2a29477a25ce4a74d885`.

Binary SHA-256:

- Fabric: `2a691edcb3b5587f0d0f05e91f0d30534f03a4afda3791ac20e82082ad541db6`
- NeoForge: `d240d7eccc933d422905c1aa53bce523c95317227c33b3f85336dac9aa8839a5`

Evidence: `build/beta79-common.log`, `build/beta79-handbook-final.log`,
`build/beta79-client-summary.log`, `build/beta79-*-terrain.log`,
`build/beta79-*-assemble.log` and staged SHA256SUMS.
