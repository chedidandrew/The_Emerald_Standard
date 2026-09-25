# Construction fence material pass — beta.35

The approved wooden posts and stepped hazard-stripe geometry are unchanged. Two shared,
opaque 16 × 16 PNGs add a little pixel grain to the yellow caps, charcoal bases and rails.
The rail colors still come from the original model's segments, not a new stripe drawing.
No additional quads, overlays, animation, render hooks or block entities are introduced.

## Source and reproduction

Created with **built-in image generation**, September 13, 2026.
Generated originals: [yellow-master.png](yellow-master.png), [black-master.png](black-master.png).
These are source swatches, not the images shipped in the texture atlas.

Run `scripts/prepare-construction-fence-textures.ps1` from the repository to import the
masters by center-sampled nearest-neighbor reduction. This is a mechanical resize:
no repainting, recoloring or procedural replacement of the generated design.
The output is fully opaque and uses the native 16-pixel Minecraft material density.

Production assets:

- `common/src/main/resources/assets/the_emerald_standard/textures/block/construction_fence_yellow.png`
- `common/src/main/resources/assets/the_emerald_standard/textures/block/construction_fence_black.png`

Both block models and the inventory model use these identical texture references.
The stem continues to use `minecraft:block/stripped_spruce_log`, including resource-pack overrides.
Minecraft applies the existing model-coordinate UVs and normal mipmapping.

## Exact prompts

### Yellow material

Use case: stylized-concept. Asset type: seamless Minecraft block texture, production albedo tile. Create ONLY one square flat texture filling the entire image edge to edge. Yellow-painted construction fence surface with subtle deliberate pixel clusters and short irregular horizontal grain/scuff marks, matching vanilla Minecraft pixel-art density. Design on an EXACT 16 by 16 logical pixel grid then enlarge with hard square nearest-neighbor pixels to fill the output; 256 large square flat-color cells total, no fine detail within cells. Warm safety yellow dominant average #F0AF15; limited palette around #D49A12, #E2A311, #EAAA13, #F0AF15, #F7B81C, #FFC329. Mostly midtone with small distributed darker/lighter patches, texture visible even cropped to any 4x2 pixel area. The texture is painted material, not a picture of a fence. No black warning stripes, no wood brown exposed chips, no bolts, no borders, no text, no labels, no checker grid lines. Uniform ambient albedo, no gradient lighting, no directional shadows, no bevel, no 3D. Crisp traditional Minecraft material texture, restrained contrast and no photorealistic noise.

### Black material

Use case: stylized-concept. Asset type: seamless Minecraft block texture, production albedo tile. Create ONLY one square flat texture filling the entire image edge to edge. Near-black painted construction-fence base material with subtle deliberate pixel clusters and short irregular grain/scuff marks, matching vanilla Minecraft pixel-art density. Design on an EXACT 16 by 16 logical pixel grid then enlarge with hard square nearest-neighbor pixels to fill the output; 256 large square flat-color cells total, no fine detail within cells. Remain visibly BLACK, not midgray: dominant #101114 with limited palette #090A0C, #0C0D10, #101114, #17181B, #1F2023, rare #26272A. Distributed slightly lighter charcoal marks make the surface readable, subtle dark variations. Texture should show in any cropped 5x2 pixel area. Material only, NOT a picture of a base or fence. No yellow, stripes, exposed brown wood, bolts, borders, text, labels, grid lines. Uniform ambient albedo, no gradient lighting, no directional shadows, no bevel, no 3D. Crisp traditional Minecraft texture, restrained and no photorealistic noise.

## Validation

See [beta.35 validation](../../docs/reviews/2026-09-13-beta35-fence-textures.md).
Native Minecraft screenshots verify the atlas textures on all connection states and the item;
they do not certify the user's external shaders or resource packs.
