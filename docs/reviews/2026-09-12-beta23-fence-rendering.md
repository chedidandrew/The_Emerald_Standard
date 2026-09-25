# Beta.23: construction-fence rendering

## Cause and correction

The screenshots' changing triangular patches are consistent with z-fighting.
The packaged models confirmed two competing surfaces, rather than an animated
texture: the wooden stem's top occupied the same y=16 plane as the yellow cap,
and its bottom shared the foot's y=0 plane. Black stripe cuboids overlapped the
yellow rail backing at their top, bottom and end surfaces. The item duplicated
this geometry.

- End the wooden stem at y=2 and y=14; remove its buried end faces.
- Replace layered stripe overlays with adjoining, solid black/yellow sections.
  Render the exterior only, retaining the existing stepped stripe pattern.
- End half-rails against the post instead of intersecting inside junctions.
  Rail thickness is uniformly one model unit (the original black-stripe thickness).
- Build the item from the same post and north/south geometry. Keep its display
  transform, texture palette and overall silhouette.
- The straight fence/item now uses 156 faces instead of 186. No texture images,
  render hooks, depth offsets or additional transparency layers are introduced.

Existing placed fences update with the model resources: no block replacement or
world migration is necessary. Recipe, collision, connections, block drops and
construction ownership are unchanged. Economy format remains 37.

## Handbook review

The guided fence-recipe page now identifies the cap, foot and striped rails,
including the matching item, corners and junctions. The compact page describes
striped rails and preserves the recipe and manual-versus-site-owned drop distinction.
No recipe, section or registered content was added. Handbook regressions require
both explanations and the fence-model verification gate in both loader builds.

## Validation

Before the correction, the new packaged-model test failed on overlapping coplanar
post/foot faces (`build/beta23-fence-before.log`). After correction the focused
Fabric check passed (`build/beta23-fence-after.log`).

The geometry gate checks all 16 connection masks, 64 neighboring assemblies,
same-facing coplanar overlap, intersecting volumes, item/block equivalence,
continuous stripe coverage, post endpoints and face budget. Both loader builds
run it as part of `check`.

- Common suite: all 90 test entrypoints passed (`build/beta23-common.log`).
- Both full loader builds passed (`build/beta23-fabric-build.log` and
  `build/beta23-neoforge-build.log`). After the preview-only harness correction,
  final assemble, model and reader/config verification tasks passed on both loaders
  (`build/beta23-*-package-final.log`).
- Native client checks passed on Fabric and NeoForge at GUI scales 2 and 4:
  all 16 block-state combinations, item rendering, two elevations and neighboring
  camera angles. Actual renderer captures were inspected: solid yellow cap tops,
  clean black foot undersides, continuous rails and no missing junction surfaces.
  Guided handbook wrapping and all 61 compact pages passed real-font checks.
- The first opt-in preview run exposed a title-screen test-harness issue: ordinary
  ItemStacks need world-bound components. The preview now uses the established
  display-only holder helper; global registries are not changed. Reusing that aborted
  fixture also retained non-default reader preferences; verification was repeated in
  fresh isolated client directories. These were fixture failures, not user-world runs.
- No installed Modrinth profile or player save has been changed. No dedicated-server
  gameplay rerun was needed for this resource-only production change. The user's
  exact shader/modpack combination and broader multiplayer gameplay are not certified.

## Final artifacts

The candidate verifier confirms both packaged versions and current-source parity.

Source SHA-256: `2f6add2180537866d58a76263f43266d54da0da467cb802b7ca1aece773ae6f0`

- Fabric: `20d487b0e75e488c3a494d345df54fb40f149858eb6a24bf204d84d579b6bbc6`
- NeoForge: `c1504af5f75bb68e161c75e76ebff35f21fe42eb1465706e5292834a31777a8a`

Successful final native logs: `build/beta23-fabric-client-final-pass.log` and
`build/beta23-neoforge-client-final-pass.log`. Screenshots are under each
`build/beta23-*-fences-final/screenshots/tes-reader-ci` directory. Both final runs
passed again after correcting the preview elevation caption.
