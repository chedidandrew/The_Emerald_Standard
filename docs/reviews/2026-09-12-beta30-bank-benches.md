# Beta.30: Bank forecourt benches

## Diagnosis and change

The screenshots match the Village Bank's civic forecourt. The six low stair cells
at local x=0,1,3,9,11,12; y=1; z=-4 are deliberately authored seating in
appendBankV6Facades. They used NORTH-facing bottom stairs: their full-height backs
faced the front path, obscuring the seats.

Bank blueprint v11 changes only those six facings to SOUTH. Minecraft's native
collision shapes confirm a half-height seat on the north/path side and a high back
on the south/building side. Materials, placement order, supports, footprint, roof,
stairs used for circulation, planters and light sources are unchanged.

## Save and safety scope

New Bank reservations and new structure-gallery builds select v11. Frozen v10 plans
remain available for integrity inspection. In-progress construction retains its
saved cell stream and version. Completed Banks and player edits are never rewritten
by this change. No economic-format change, new recipe, item, loot or sitting mechanic.

## Handbook review

Updated the guided Bank-access chapter to identify the terrace benches, their
orientation, decorative purpose and the non-destructive existing-building policy.
Added handbook resource coverage. Reviewed the compact Bank-access troubleshooting
page, chapter index and recipes: none describes bench orientation or needs an
unrelated recipe/page change.

## Validation

- Native production Bank admission passes for all five biome palettes.
- Exact v10/v11 comparison: six facings change, every other cell and order retained.
- 360 native collision/rotation/mirror checks prove the seat opens toward the path.
- Nonzero-origin comparison proves world translation does not change targeting.
- A deliberately backwards single seat is rejected by production admission.
- Historical Bank checks retain the frozen v2-v10 contracts.
- Full common suite passes (build/beta30-common.log).
- Fabric and NeoForge build/check tasks pass, including complete authored-catalog
  validation and the production Bank admission tests (build/beta30-*-build.log).
- Isolated Fabric client handbook smoke passes: 61 compact pages plus long-form
  real-font wrapping, chapter ends, search and 80/120 text scales.
- git diff --check passes; both packaged candidates match current source.

Client startup reports host Windows performance-counter/OSHI diagnostic warnings and
the development-account Realms authorization warning; the renderer and handbook
checks complete successfully. No live test world or installed mod was changed.

Source SHA-256:
0ddf8099ac9e07bfc477ea0cf272cbb78b741f7b4f206d1e5b84b375e67c0797

Fabric JAR SHA-256:
1f565a5e38521839f641985bfeee2c1eeacedadd1c9f73ad127a5da777bf2c86

NeoForge JAR SHA-256:
d2e168a7219c27d9d7bf339de4668d46fe3fa6dd982298aeca57713593dc7327

This pass uses actual Minecraft block states and collision shapes; it does not claim
a new shader/modpack screenshot review or a live-world retrofit.
