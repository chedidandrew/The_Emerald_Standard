# Courtyard smithy roof review

This pass introduced authored revision 7 / gallery content 15, retaining the earlier ceiling and chimney improvements. The subsequent smithy-eaves review documents the lower-profile revision-9 roof.

`smithy_courtyard_01` now has two pitched workshop roofs of unequal length, inset timber gable-end framing and a lower rear connecting roof. Solid roof infill prevents gaps beneath the pitches. The existing masonry forge collector supports two short wall-block chimney pots, replacing the tall brick crown. The courtyard stays open.

The exact before/after test compares revision 6 with 7 across all 52 masters and five dialects. Only the smithy's upper roof envelope may change; its work areas and furnishing stages, and all 51 other masters, must remain identical. Older saved plans are not remodeled.

## Isolated review world

Profile: `fabric/run/smithy-review-26.2`.
Save within it: `saves/TES_Village_Comparison`.

Reopen from the repository root:

```powershell
.\scripts\open-village-comparison.ps1 -GameDirectory .\fabric\run\smithy-review-26.2
```

Use these in-game commands to review the corresponding mod/vanilla pairs:

| Village style | Command |
| --- | --- |
| Plains | `/emerald comparison visit 28` |
| Desert | `/emerald comparison visit 81` |
| Savanna | `/emerald comparison visit 134` |
| Taiga | `/emerald comparison visit 187` |
| Snowy | `/emerald comparison visit 240` |

The previous comparison, chimney and ceiling review profiles are retained untouched. No old chunks, player inventory or economy state are copied into this new world.

## Verification

The common regression suite and Fabric/NeoForge build/check passed on 8 September 2026. The exact before/after test confirms all 51 other masters and every furnishing stage stay identical across the five village styles. Full catalog checks include geometry, circulation, conservative block lighting and rendered support connectivity; no isolated cells or unanchored assemblies were reported. No new aesthetic score or completed screenshot review is claimed.

The fresh review world finished generating all 550 structures at 20:19:16 local time on 8 September 2026 and was left open. Its comparison signature is `5d834931e8883b0c`. No commit or push was made.
