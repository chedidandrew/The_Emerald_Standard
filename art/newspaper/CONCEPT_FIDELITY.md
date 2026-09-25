# Newspaper concept fidelity — beta.21

The first user screenshot is the old enlarged inventory-atlas preview, not a faithful
close-up of the packaged item. Keep the native model-renderer correction documented
in native-preview-2026-09-12. This revision also replaces the narrow open artwork
with the supplied broad-page design: masthead, folded corner, village tower/houses,
two print columns and layered edges. The rolled master is retained and both textures
are now 256px rather than 128px. Small inventory icons still lose subpixel detail.

## Selected assets and tooling

Built-in image generation, background-extraction edits; no CLI fallback. Selected
open master: pocket-concept-master.png. Existing rolled master: rolled-master.png.
Production assets: common/src/main/resources/assets/the_emerald_standard/textures/item/
newspaper.png and newspaper_open.png. The preparation script uses only uniform
nearest-neighbor scaling and transparent letterboxing, never independent width/height
scaling. Regressions compare every packaged sample with the selected master and
check the open silhouette aspect ratio. Old masters remain available.

The first two open exports contained opaque checkerboards and were rejected. The
selected background-extraction correction has a verified transparent alpha exterior.
An attempted further rolled extraction used the wrong recent-image target and was
rejected; the approved existing rolled master remains in use. Direct file-reference
editing was unavailable because the Windows filesystem helper failed. No generated
output was accepted merely because its preview resembled transparency.

## Prompts

Initial rolled extraction (not selected; existing rolled master retained):

Use case: background-extraction. Input image 1 is the exact edit target, approved rolled newspaper Minecraft sprite. Change ONLY the dark gray background to genuine transparent alpha. Preserve the newspaper EXACTLY: its diagonal angle, its broad proportions and every visible pixel-art feature, black spiral/curl at lower left, center gray paper band, black/gray printed marks, gray paper shading and upper-right opening. Do not redesign, redraw, narrow, stretch, reorient, simplify, sharpen away the shading or change the pixel-art style. Preserve the dark outline but remove external background/shadow. Single isolated rolled newspaper, centered on square transparent canvas with 4 percent padding; use uniform scaling only. Actual RGBA transparency, no baked checkerboard or solid background. This will be the actual game texture, not another concept variation. Need faithful detail in the source image retained.

Open extraction:

Use case: background-extraction. Input image 1 is the exact edit target: approved pocket gazette newspaper Minecraft sprite. Remove ONLY the dark gray background and export genuine alpha transparency. Keep the illustrated newspaper EXACTLY as shown, not a recreation. Preserve its BROAD page proportions, slight tilt, layered left and bottom paper edges, upper right folded corner, charcoal masthead with hollow light rectangle at left and three pale lines to right, grayscale village picture with central tower and houses, and both distinct columns of horizontal article lines. Do NOT make it narrower/taller, replace picture, change typography, add detail, simplify, restyle or change its colors. Only uniform scaling/centering if needed onto a square canvas, approximately 4% transparent padding top/bottom. Retain fine original pixel-art shading. The output must have real transparent pixels, NOT an opaque checkerboard. No scene, label, hand or shadow outside newspaper. Actual game texture to match this exact concept.

First alpha correction (rejected, still opaque):

Background-extraction correction ONLY: remove the entire gray-and-white checkerboard background from this newspaper image. Output actual transparent PNG alpha. The checkerboard pixels must be alpha=0, NOT painted white or gray. Keep the complete newspaper and its dark outline, layered paper, masthead, village image and two columns EXACTLY unchanged in size, proportions, placement, colors and detail. Do not repaint or redraw the newspaper. No external shadow. Only replace the checkerboard outside the newspaper with true transparent alpha.

Selected alpha correction:

Make this newspaper a clean transparent-background cutout PNG asset. The entire area outside the paper is empty/transparent (alpha channel), including the checker pattern. Preserve the newspaper itself, same broad shape and exact village illustration, masthead and article columns. Background removal only.
