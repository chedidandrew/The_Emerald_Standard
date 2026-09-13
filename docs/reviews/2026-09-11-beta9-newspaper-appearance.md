# beta.9 newspaper appearance — 2026-09-11

## Change

Replaced the rectangular, green-masthead 3D newspaper model with a flat grey paper
silhouette and black text-like printed marks. The item uses `minecraft:item/generated`
and a dedicated **32x32 RGBA** texture (979 bytes), preserving normal item transforms.
The sprite was generated with the built-in image-generation tool, then mechanically
nearest-neighbor sized for the game with `scripts/prepare-newspaper-texture.ps1`.
No CLI image-generation API or Python image editing was used.

Final project asset:
`common/src/main/resources/assets/the_emerald_standard/textures/item/newspaper.png`

Preview:
`build/newspaper-art/newspaper-preview.png`

Original generated artwork retained at:
`C:/Users/Andrew/.codex/generated_images/01a07291-71d1-7f21-ad33-e3938929fe29/exec-e6c5db81-2bc8-43cf-a9fa-2273d1a3c09e.png`

The item ID, one-paper/one-ink-sac shapeless recipe, stack behavior, reuse and news
network behavior are unchanged. Save format remains 32; the beta.8 menu layout is
unchanged. Both loaders are packaged as **0.4.0-beta.9**. Prefer matching client/server
versions. No installed mod, user world, financial state or profile was changed.
Nothing was committed, pushed or published. Earlier JARs remain available.

## Handbook review

Updated the long newspaper chapter to identify grey paper with black printed marks,
and the compact newspaper page to identify grey printed paper. Removed the obsolete
green-masthead description. Reviewed the animated newspaper recipe and four server
recipe definitions; no recipe changes are needed. Creative-only spawn eggs remain unchanged.

## Verification

- Extended and ran `HandbookResourceRegressionTest`: exact generated-item model, asset
  path, 32px alpha texture, neutral palette, visible dark print/grey face/transparent
  silhouette and both handbook descriptions pass, along with the existing resource checks.
- Both native clients passed creative catalog, all four recipe previews and handbook
  checks at GUI scales **2 and 4**:
  `build/beta9-fabric-client.log`, `build/beta9-neoforge-client.log`.
  The existing success message says “three recipes”; the executed loop covers indices 0–3.
- All **57 compact pages** and long-form 80%/120% wrapping/topic checks passed.
- Visually inspected the actual Fabric scale-4 creative catalog and NeoForge scale-4
  newspaper recipe screenshots, plus the nearest-neighbor sprite preview.
- Both final assemblies, menu packet-codec and reader/settings checks passed:
  `build/beta9-fabric-final.log`, `build/beta9-neoforge-final.log`.
- Both playable JARs passed current-source fingerprint/cross-loader verification.
  Their embedded PNG hashes match the source texture.
- `git diff --check` passed.

This visual-only pass did not rerun the entire 82-program economy regression suite,
full structure build gates or dedicated-server suites. Those beta.8 results remain
historical evidence, not newly claimed beta.9 runs. No in-world held/dropped-item or
third-party resource-pack playtest was performed. Standard inherited item transforms
are used; native inventory and crafting renders were verified.

## Artifact identity

Shared source SHA-256:
`efd38061e92dbee3b64d7257cc49a019b7e3e84ed2087f93222138749d528ec3`

- Fabric JAR: `E39CF94D867BF4EABE4CEC08571AFC1C5128105EB8939CDC830FBEF343E8AFD5`
- NeoForge JAR: `9FFA938EC6CD13B095AC641C926E472DC2B1E9D6AACB177D959970341474956F`
- Texture PNG: `42D2B10CB5DF7CD99D9CC624B7B1499B35DF5515E92FBCFCD9C84CF411FEA355`

## Final generation prompt

Create a single Minecraft Village Newspaper inventory item sprite. It must look like the VANILLA MINECRAFT PAPER ITEM, but light neutral GRAY with tiny BLACK scribbles suggesting printed letters. Square transparent canvas with one flat diagonal sheet centered. Paper is oriented diagonally up toward the right, a small subtly folded irregular rhomboid with clean stepped pixel edges, light gray face, medium gray lower edge and light fold highlight. On the face add only 3 or 4 short broken rows of tiny black pixel marks following the sheet's diagonal, no readable words. Authentic extremely simple 16x16 logical pixel art enlarged in uniform square blocks, absolutely no antialiasing, no smooth curves, no gradients. Occupy about 13 of the 16 logical pixels in each dimension, retaining transparent margins. True transparent background, not a checkerboard. No green, no masthead, no thick frame, no upright rectangular newspaper, no other objects, no inventory UI, no ground or cast shadow. Just the small gray printed paper sprite ready for a Minecraft item texture.
