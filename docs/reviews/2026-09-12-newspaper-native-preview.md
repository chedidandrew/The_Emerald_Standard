# Newspaper native-render preview correction — 2026-09-12

The earlier beta.19 "ACTUAL ITEM MODELS" preview was misleading at large size.
Minecraft 26.2 GuiRenderer.prepareItemElements allocates cached GUI item slots at
16 times the GUI scale in framebuffer pixels. Scaling the GuiGraphicsExtractor
pose sixfold enlarged that already-rasterized icon; it did not render the model
at six times the resolution. At GUI scale 2 the 128px artwork was sampled into a
32px icon before enlargement. This discarded the village picture and article detail.

NewspaperModelPreview now resolves the existing packaged item models with
ItemModelResolver and submits ItemDisplayEntityRenderState to the native
picture-in-picture entity renderer at the displayed dimensions. No raw texture
or concept illustration is substituted for the model. The ordinary g.item call
remains in the small inventory slots for an honest comparison.

These are direct Minecraft model-renderer studio captures, not first-person
world screenshots. They do not include the user's shader/resource pack.
The packaged 128px textures, generated-item geometry, handheld transforms,
open/close lifecycle, recipes, item data and production GUI remain unchanged.
This change corrects the opt-in visual validation tool, not inventory resolution.
Inventory-size fine detail inevitably reduces at normal icon sizes.

## Validation and handbook accuracy review

- Fabric native client run passed at GUI scales 2 and 4, including newspaper
  navigation, edition changes, native model resolution, recipes and handbook checks.
- All 61 compact pages passed actual-font line limits; long-form wrapping, chapter
  ends, 80/120-percent text and search checks passed.
- Handbook appearance guidance was reviewed against the unchanged production
  artwork/lifecycle. No player-facing instruction changed, so no unrelated book
  text was edited for this diagnostic-only correction.
- The appearance regression rejects returning to sixfold small-atlas enlargement
  or replacing the native model preview with a concept-image blit.
- Scale-2 output was visually inspected for curl, paper band, village illustration,
  printed columns, layered edge and ordinary inventory-sized icons.

Captures are copied unchanged from the Minecraft screenshot output:
- art/newspaper/native-preview-2026-09-12/model-scale-2.png
- art/newspaper/native-preview-2026-09-12/model-scale-4.png

No image-generation tool, raster retouching, new texture, release/version bump,
packaged JAR rebuild, game installation or user-world modification was performed.
The existing beta.20 JARs remain the previous candidate; their fingerprints must
not be claimed to match these additional diagnostic-only source changes.
