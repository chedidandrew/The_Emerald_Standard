# Chimney silhouette review

This review pass introduced authored template revision 5 and gallery content revision 13.
Bank version 7 was unchanged by this pass. Later refinements are documented in the subsequent review guides.

## What changes

Eligible plain integrated stacks retain their solid lower masonry and roof penetration. Above
the local roof or last attached detail, one full collar supports two narrow wall-block courses.
Excess height is removed. Brick stacks use Brick Walls; Desert sandstone stacks use Sandstone
Walls. The pass never grows a chimney higher than its original top.

The sculpted `cottage_hearth_01` chimney is deliberately excluded. Capped chimneys, integrated
industrial crowns and other deliberately authored chimney types are not generic utility stacks
and retain their designs. No existing player building or old approved project is remodeled.

The balanced/prosperous production comparison finds changes in nine masters, across five biomes:

| Plains pair | Template |
| --- | --- |
| 4 | `cottage_bay_04` |
| 7 | `house_cross_01` |
| 11 | `house_splitwing_05` |
| 12 | `house_towercourt_06` |
| 13 | `inn_gallery_01` |
| 14 | `inn_coachhouse_02` |
| 17 | `inn_courtyard_05` |
| 24 | `granary_windmill_02` |
| 29 | `smithy_hammerhall_02` |

Pair 1 shows the preserved hearth cottage. Add 53, 106, 159 or 212 to a Plains pair number
for its Desert, Savanna, Taiga or Snowy equivalent.

## Isolated world and reopening

The new local profile is `fabric/run/chimney-review-26.2`; the save is
`saves/TES_Village_Comparison` within that profile. The earlier comparison profile and original
blueprint gallery remain untouched. This fresh world contains newly generated production
structures beside vanilla reference templates; it does not import existing chunks or player edits.

From the repository root:

```powershell
.\scripts\open-village-comparison.ps1 -GameDirectory .\fabric\run\chimney-review-26.2
```

Useful in-game commands:

```text
/emerald comparison visit 29
/emerald comparison visit 12
/emerald comparison visit 1
```

The current launcher must be used with this new profile. A completed older comparison save
correctly refuses a changed catalog signature instead of silently rebuilding itself.

## Verification scope

The exact production comparison covers all 52 masters in five dialects. It checks that only
chimney cells change, garden/furnishing stages stay identical for the same dressing seed,
and the sculpted hearth remains unchanged. Tests also cover local rather than distant roof
height, reserved positions, caps, idempotence and frozen revisions. Complete catalog admission
retains geometry, connectivity, circulation and conservative lighting checks.

The fresh world successfully generated all 550 comparison structures on 8 September 2026.
Fabric build/check, NeoForge build/check and the full common regression suite passed.
The current catalog contact census reports zero isolated cells and zero unanchored assemblies.

The planned screenshot sample was the nine changed Plains masters, the preserved cottage, and
four Desert/Snowy examples (14 pairs, three exterior views each). Capture stopped after the first
three images when its clean-1920x1080 viewport check failed. That partial batch is not final
visual evidence; the world was left open with HUD/FOV restored for the user's hands-on review.
No new numerical Carol review or universal aesthetic certification is claimed. The existing
[comparison guide](VILLAGE_COMPARISON_GALLERY.md) explains the exhibition limitations.
