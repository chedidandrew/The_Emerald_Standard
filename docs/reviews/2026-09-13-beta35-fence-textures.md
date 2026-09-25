# beta.35 — construction fence material texture

## Scope

Replace the vanilla yellow/black concrete references on the construction fence with two dedicated
opaque 16 × 16 pixel-grain textures. Warm safety yellow and near-black charcoal remain the
dominant colors. The approved stripped-spruce posts, exact stepped hazard pattern, item display
transforms, UVs, cuboids, face coverage and all connection rules are unchanged.

The three model-file diffs change only yellow, black and particle texture references.
The beta.23 no-overlap geometry remains intact. No overlays, added quads, animation, tick work,
networking, world edits, new blocks or save-format changes. Existing placed fences acquire the
new appearance from the client resources; there is no regeneration/migration step.

The two production PNGs total 1,366 bytes on disk (818 yellow + 548 black), before archive compression.
Source swatches, exact prompts and a mechanical nearest-neighbor import script are retained in
[the art record](../../art/construction-fence/README.md). The masters are not shipped in the JAR.
Image design used built-in image generation; import downsamples without recoloring or repainting.

## Handbook review

Reviewed Construction fences in the guided handbook and its compact/lectern counterpart.
Added the pixel-grain appearance description and compact “Textured stripes” cue. Kept the exact
two-row recipe (Stick/Yellow Dye/Stick above Stick/Black Dye/Stick, four fences), connection and
collision advice, manual one-item drops and automatic site-owned no-drop/cleanup protections.
No crafting diagram, recipe, chapter routing, page count, creative-tab entry or control changed.

Resource regression checks match the wording, server recipe and every production pixel against
the saved master swatches. Native client checks retain all 61 compact pages and guided text fit.

## Validation

- Packaged-material/geometry test: PASS. Opaque 16px dimensions, warm yellow/near-black ranges,
  visible variation in cap/base/rail UV crops, identical block/item material aliases and unchanged wood.
  Existing checks cover all 16 connection masks, 64 neighboring combinations, exact stripe coverage,
  no overlapping solids/coplanar surfaces, matching item geometry and unchanged quad budget.
- Full common suite: PASS (`build/beta35-common-tests.log`), including handbook/recipe/import checks.
- Fabric native visual client: PASS (`build/beta35-fabric-client.log`), four views at GUI scales 2/4.
  Inspected actual Minecraft captures from below and above: visible cap/foot grain, unchanged
  silhouette, legible hazard pattern, matching item and junction materials; no missing-texture pixels.
  [Native preview](../../art/construction-fence/native-preview-beta35.png).
- Full Fabric build: PASS, 3m 13s (`build/beta35-fabric-build.log`).
- Full NeoForge build: PASS, 3m 15s (`build/beta35-neoforge-build.log`).
- NeoForge native visual client: PASS (`build/beta35-neoforge-client.log`), same material,
  geometry, 61 compact pages, long-form font checks and eight native captures. Inspected the
  all-16-connections capture; cap, rail and foot materials resolve correctly throughout.
- Candidate verifier: PASS current source fingerprint, version and cross-loader parity.
  Both playable JARs contain the exact production texture bytes and omit generated masters.
- Explicit model comparison against HEAD: PASS only the three material references differ in
  each model; all other parsed model data is identical.
- `git diff --check`: PASS.

## Candidate identity

Version: 0.4.0-beta.35.

Shared source SHA-256:
`28996e029f3626ac128f7208ad293ed343f5e1773cee042f79ece18664b099de`

Fabric binary SHA-256:
`1e855b141aec27eaad4fd553bf3cc33e874dc783343a871aa577abb89db4ef8c`

NeoForge binary SHA-256:
`ed4f73c57c448f83975a9e88b0ec72245f546784d88da8c24fdd775e0a295d81`

Playable candidates are `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.35.jar` and
`neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.35.jar`. Previous candidates remain.
Existing Gradle deprecation and vanilla/NeoForge translation-rename warnings remain in logs;
no fence asset warning or failed check occurred.

Tests use isolated scratch client directories; no existing world, modpack, options or live install
was changed. No GitHub push. Native previews use ordinary Minecraft rendering, not the user's
external shader/resource-pack combination; this pass makes no measured FPS claim.
