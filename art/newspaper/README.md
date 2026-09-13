# Newspaper asset masters and prompts

## Current assets: beta.21

See [CONCEPT_FIDELITY.md](CONCEPT_FIDELITY.md) for the supplied-concept correction,
selected alpha master and prompts. Production uses `rolled-master.png` and
`pocket-concept-master.png`, uniformly sampled to 256px RGBA. The broad page replaces
the older narrow open sprite. Earlier preview and 128px notes below are historical.
The native-renderer preview path is retained; do not enlarge a cached inventory icon.
Updated unretouched Minecraft captures are in `native-preview-beta21/model-scale-2.png`
and `model-scale-4.png`, with close-ups above ordinary inventory icons.

## Actual model previews (2026-09-12 correction)

See `native-preview-2026-09-12/model-scale-2.png` and `model-scale-4.png` for
unchanged Minecraft screenshots from the direct native item-model renderer.
The older enlarged GUI-item preview upscaled an already-small cached icon and
lost detail; do not use it to judge the full-resolution model. The corrected
captures also retain ordinary inventory-sized icons below the closeups. They
are studio model renders, not screenshots from the user's shader-pack world.
Artwork and gameplay behavior are unchanged. See the native-preview validation
record under `docs/reviews/2026-09-12-newspaper-native-preview.md`.


The approved C (Rolled edition) and D (Pocket gazette) concepts were retrieved from the earlier task visuals. Built-in image generation was used, followed by explicit transparent-background extraction passes. The first exports had baked checkerboards and were discarded as production assets. Direct file-reference input failed because of the Windows filesystem helper; the displayed reference previews were used instead. No CLI/API fallback was used.

Final masters: `rolled-master.png` and `pocket-master.png`. Packaged textures are mechanical nearest-neighbor 128x128 reductions using `scripts/prepare-newspaper-texture.ps1`; no colors or original inventory data are altered by that process.

## Rolled prompt

Use case: precise-object-edit. Asset type: actual Minecraft item texture, isolated game sprite, not a concept board.
Input image is the approved C - ROLLED EDITION reference. Extract/recreate ONLY the large sprite at upper left as one faithful isolated sprite. Preserve its diagonal lower-left to upper-right rolled newspaper silhouette, visible concentric paper curl at lower left, folded opening at upper right, pale cool grey paper, grey center band, black printed columns, blocky highlights, subtle sheet edges and dark grey outline. Keep the original design and proportions, do not redesign it. Center it on a square canvas with approximately 6% transparent padding. Genuinely transparent background, no checkerboard baked in. Crisp detailed pixel art on a coherent 128x128 logical pixel grid upscaled cleanly, no blur or smooth vector edges. Need fine printed marks and paper shading preserved for close-up in-hand use. Only one object, no labels, no 'C', no scene, no hand, no inventory box, no external drop shadow. This is the texture used by the actual game item.

## Pocket prompt

Use case: precise-object-edit. Asset type: actual Minecraft item texture, isolated game sprite, not a concept board.
Input image is the approved D - POCKET GAZETTE reference. Extract/recreate ONLY the large sprite at upper left as one faithful isolated sprite. Preserve the narrow upright partly unfolded pocket-newspaper silhouette, slight lean, layered paper edges, folded upper-right corner, charcoal headline band, grayscale Minecraft village/tower illustration beneath it, and two columns of small black printed article strokes in the lower half. Keep original proportions and design, not an open two-page book. Use pale cool grey paper and black/dark grey ink, subtle crease and shading. No literal D label. The masthead can be abstract black typesetting. Center one object on square canvas with approximately 6% transparent padding above/below. Genuinely transparent background, no checkerboard baked in. Crisp detailed pixel art on a coherent 128x128 logical grid upscaled cleanly, no blur, no vector edges. Preserve fine print and village picture detail for close-up in-hand rendering. No labels, no surrounding scene, no hand, no inventory UI, no external shadow. This is the reading/open state of the same newspaper item.

## Transparency correction

Remove the entire checkerboard background completely and output actual transparent alpha PNG. Keep the selected newspaper sprite unchanged. No paper square, no white or grey opaque backdrop, no rendered checker pattern and no shadows outside the object. Preserve the outline and all printed detail.
