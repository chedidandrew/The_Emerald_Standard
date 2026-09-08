# Palette and landscape comparison — 8 September 2026

Carol inspected all **60 original Minecraft screenshots** in the final, uniform-grade
comparison batch. The sampled exteriors show clearer material separation, restrained
biome-specific planting and visibly connected roof/entry details. The artificial trenches
around the vanilla references are gone. This is a qualitative comparison, **not a new
numerical review**: no earlier scores or five-review limits were changed.

## Evidence and coverage

Content revision 12; production masters `@4`; standalone Bank `@7`; comparison schema 1;
catalog signature `a39b321dad906633`.

The local evidence is retained under the ignored profile directory:

```text
fabric/run/comparison-26.2/screenshots/
  tes-village-comparison-2026-09-08T22-24-47.515449600Z-fc208a26-9b9b-454d-81a5-d98b36128a16/
```

`complete.txt` records completion at `2026-09-08T22:28:08.404463300Z`. The independent
capture check confirmed exactly 20 selected pairs × three views, 60 decodable 1920 × 1080
PNGs, all manifest SHA-256 hashes matching, one player, Overworld and FOV 70. Carol opened
the originals, rather than assigning conclusions from contact sheets or code tests.

Each pair has a combined front comparison, a mod exterior and a vanilla exterior.
The mod is on the **right**, and vanilla on the **left**, when viewed from the front.

| District | Hearth cottage | Towercourt house | Large bourse | Bank |
| --- | ---: | ---: | ---: | ---: |
| Plains | 1 | 12 | 52 | 53 |
| Desert | 54 | 65 | 105 | 106 |
| Savanna | 107 | 118 | 158 | 159 |
| Taiga | 160 | 171 | 211 | 212 |
| Snowy | 213 | 224 | 264 | 265 |

These are `cottage_hearth_01`, `house_towercourt_06`, `exchange_bourse_05` and
`standalone_bank`, repeated across the five material dialects. The world contains all
265 mod/vanilla pairs plus five small vanilla context courts: 550 placed structures.
This screenshot sample does **not** visually cover every one of those structures.
The world's `comparison-index.md` records each actual vanilla template ID.

## Visual findings

- **Snowy:** cottage 213 and bourse 264 have useful pale wall/gable fields against dark
  timber, with gray stone entries readable against the snow. Small planted corners do
  not overwhelm the facade. Towercourt 224 remains deliberately timber-heavy; its pale
  window and tower infill helps, but it is not a predominantly white facade.
- **Desert:** cottage 54 and bourse 105 separate pale sandstone fields from warm acacia
  framing and roofs. Dry plants suit the setting. Towercourt 65 retains considerably
  more similar-toned warm wood across the roof and front than those two examples.
- **Savanna:** the mud-brick infill, acacia frame, dark roof and gray masonry are distinct
  enough to avoid an entirely orange building. The result still reads as a warm regional
  palette, rather than a neutral building with a few colored accents.
- **Taiga:** dark slate-colored roofs, warm timber and mossy foundations make a coherent
  contrast. Bank 212's stone portico is particularly easy to distinguish from its timber
  shell. The display ground is a separate weakness, noted below.
- **Plains:** roof/wall separation is clear, and the stone entry framing anchors the
  larger buildings. The towercourt still has broad tan wall/frame areas of similar value.
- **Banks:** all five sampled Banks have a recognizable layered entry, green motif,
  framed belfry and planted corners. Their visible lamps, roof details and stone brackets
  appear connected; no obvious floating assembly was seen in these front views.
- **Planting and access:** visible pots and low planters stay beside entries and corners.
  They do not visibly close the sampled front doorways. The central dirt-path approaches
  and flanking stone landings remain legible.

## Remaining limitations and future priorities

The towercourt's large roof mass and some broad timber wall fields still look restrained
rather than richly handcrafted. The large bourse gains rhythm from its bays and entry,
but scale alone is not craftsmanship. Repeated bench, planter and lamp arrangements,
chunky entry piers and some thin chimneys remain recognizable repeated details. These
observations are not a claim that every asset has reached the earlier 8+ target.

The **comparison-only Taiga ground** reads as a dense geometric podzol/grass checkerboard
in pairs 160, 171, 211 and 212. It distracts from the buildings. A future comparison-world
refresh should use quieter ground or larger irregular patches. This is not a defect in
the production building geometry. Rectangular sand/snow parcels and wide exhibition gaps
also make this a catalog, not a natural village scene.

The vanilla references retain their authored bottom layers. Their perimeters no longer
have the artificial trenches seen in the superseded batch; some templates still have
their own raised dirt/grass plinth or floor. Those blocks were not flattened or recolored
to make either side look better. Uniform display placement is not Minecraft's natural
terrain projection.

The comparison is useful for palette, silhouette and scale, but it is not an equal-size
or equal-block-budget contest. Vanilla libraries/cartographers are only functional
analogues for Banks and exchanges. Desert mod buildings also retain pitched roofs where
many actual vanilla references use flat roofs: a different architectural choice, not a
faithful reproduction of the vanilla vernacular.

All three capture poses are **daylight exterior views**. Combined views show whole
buildings, not close joinery; labels are best read during an in-world visit. This batch
does not certify interiors, rear elevations, night lighting, all walking routes, natural
slopes, every doodad or all 52 masters in every dialect. Production geometry/lighting
tests and the earlier bounded Carol review remain separate evidence.

See [comparison-world instructions](../VILLAGE_COMPARISON_GALLERY.md) and the preserved
[Carol review ledger](../CAROL_STRUCTURE_REVIEW.md) for those distinct scopes.
