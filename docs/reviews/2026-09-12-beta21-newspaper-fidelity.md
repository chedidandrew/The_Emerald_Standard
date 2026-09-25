# beta.21 — newspaper concept fidelity

Date: 2026-09-12. Unreleased local candidate, not installed in the live game.

## Diagnosis and changes

The supplied "ACTUAL ITEM MODELS" screenshot enlarged a cached inventory icon.
At GUI scale 2 Minecraft had already sampled that icon at 32 framebuffer pixels,
so enlargement hid the village picture and print. The existing workspace correction
uses the native item-display renderer at the requested preview dimensions; it is
preserved in this candidate.

The older open master also had narrower proportions and different masthead details
than the newly supplied concept. The selected replacement preserves the broad
folded-page shape, hollow masthead mark, central tower and houses, layered edges,
folded corner and two article columns. The existing rolled master is retained.
Both production textures are now 256x256 RGBA instead of 128x128.

The preparation script now scales uniformly and letterboxes non-square input.
It refuses assets without a substantial transparent exterior. Appearance tests
verify the broad open silhouette and every nearest-neighbor sample against the
selected master, preventing accidental independent X/Y squeezing or use of a
different simplified source.

Built-in image-generation background extraction was used for the new open master.
Opaque/checkerboard attempts and an incorrectly targeted rolled attempt were rejected.
The complete prompts, accepted paths and rejected-attempt notes are in
art/newspaper/CONCEPT_FIDELITY.md. This is a close visual adaptation, not a claim
that generated cutouts reproduce every original source pixel.

The generated-item models, first-person transforms, synchronized rolled/open
selection, reader lifecycle, recipes, stack counts and saved item data are unchanged.
Tiny inventory icons still cannot reproduce every fine stroke from a large concept.
Resource packs and shader lighting can change appearance; no shader-pack override
or custom high-resolution inventory renderer was introduced.

## Validation

- All 89 common regression entrypoints and wrapper/version checks passed.
- Fabric native client smoke passed at GUI scales 2 and 4: newspaper models, reader
  navigation, edition/privacy behavior, recipe rendering and handbook checks.
- Long-form and compact newspaper guidance updated. All 61 compact pages passed
  real-font limits; long-form chapter ends, 80/120-percent text and search passed.
- Unretouched native-model screenshots saved at
  art/newspaper/native-preview-beta21/model-scale-2.png and model-scale-4.png.
  Scale-2 was visually inspected against the supplied concepts, including ordinary
  inventory-sized icons below the close-ups.
- Fabric and NeoForge full builds passed with packaged source fingerprint parity.
  These include native structure validation, packet and reader-settings checks.
- Dedicated-server gameplay suites were not rerun for this texture/documentation
  revision; the prior beta.20 coverage is not presented as a new beta.21 server run.
- No live Modrinth files, user worlds, items or inventories were modified.

These previews resolve the actual packaged Minecraft models. They are controlled
renderer captures, not first-person screenshots from the user's shader-pack world.
Development-client Realms authentication/Windows OSHI and upstream deprecation
warnings remain visible and did not fail the rendering assertions.

## Candidate identity

Version: 0.4.0-beta.21. Economy format remains 37.

Source SHA-256:
b0ffc085495096ccdd8bf13593aa994986d9e1af1ac0cac8678a0a39200fe666

Fabric SHA-256:
924662F321496E073995FAEB5315F5D9F8CDF316EB8549C56736DCFF4A0779C6

NeoForge SHA-256:
40365BF84F6DAE43BD0EC4E5D639D8DC45D8647DFE26AF08F21F9DA78C106EE4

Artifacts:
- fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.21.jar
- neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.21.jar
