# Low-profile smithy roof review

This local follow-up implements the user's edited smithy reference. Authored revision 9 is
active, Bank version 8 is unchanged, and gallery content is 17.

## Design

- Flatten the courtyard smithy's two workshop roofs by one block, replacing their narrow
  ridges with broad four-block-wide slab caps and retaining the stepped lower shoulders.
- Wrap the joined roof footprint with a projecting half-slab fascia, including outside
  corners and the courtyard returns. Plains uses Dark Oak Slabs; the other dialects use
  their corresponding complementary roof palette.
- Retain the horizontal timber gable panels, masonry walls, forge collector, slim chimney
  pots, existing lights, interior headroom, workstations and yard furnishing stages.
- Add two small front-post wall-torch sconces for the extra floor row sheltered by the eaves.
  Later-stage rear paving can receive one additional rear-wall sconce beneath the new eave.
  The existing light sources are not moved or removed.

This is a fixed, supported roof composition for `smithy_courtyard_01`, not a random detail
pass over other templates. Existing saved revision-1 through revision-8 projects retain
their original plans, and older review worlds are never rebuilt or overwritten.

## Isolated review world

Profile: `fabric/run/smithy-eaves-review-26.2`

Save within the profile: `saves/TES_Village_Comparison`

The fresh world completed all 550 comparison structures on 8 September 2026 at 21:05 local
time and was left open for hands-on review.

Only flat-world seed/settings metadata was copied to this new profile. No previous chunks,
player data, economy data or gallery completion markers were imported.

From the repository root:

```powershell
.\scripts\open-village-comparison.ps1 -GameDirectory .\fabric\run\smithy-eaves-review-26.2
```

Use `/emerald comparison visit 28` for the Plains smithy. Its equivalents are 81 (Desert),
134 (Savanna), 187 (Taiga) and 240 (Snowy). Compare against the previous
`chimney-completion-review-26.2` world for the original revision-8 pitched profile.

## Verification scope

The exact revision-8-to-9 test compares all 52 masters across five dialects. It requires
51 unrelated masters and their furnishing stages to remain identical. Every original smithy
furnishing is retained; only a specifically bounded rear-eave sconce may be added in a later
stage. Smithy base changes must stay in the roof envelope, apart from the two front sconces.
Existing chimney cells and light sources cannot change. Checks also verify the broad low
cap, removed high ridge, slab border corners and inner returns.

Both loader catalog checks retain complete attachment, enclosure, circulation and conservative
no-skylight lighting gates. The new covered edges must reach the existing comfort threshold
of block light 7; no lighting requirement was relaxed for the wider overhang.

Automated tests do not constitute a screenshot-based aesthetic rating. Manual inspection
of the generated review world is separate from the automated verification described here.

Final verification on 8 September 2026: Fabric `build check`, NeoForge `build check` and the
full common regression suite passed. The loader-aware test reported zero failures/errors.
Catalog contact checks reported zero isolated cells and zero unanchored assemblies.
