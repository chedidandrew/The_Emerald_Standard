# Interior ceiling clearance review

This pass introduced authored revision 6 / gallery content 14, retaining the previous chimney improvements. Later review guides cover subsequent revisions.

Low horizontal domestic ceiling ties move from y=3 to y=4 above the authored y=0 floor. The narrow pass excludes external wall belts, vertical posts, occupied bearing cells, furniture, reserved upper cells and higher ceilings. It retains the original lights and checks full geometry, access, attachment support and conservative block lighting afterward. Existing worlds and saved revision-1 through revision-5 projects are not rewritten.

The before/after regression compares all 52 masters in five dialects with the same dressing seed. Only eligible y=3/y=4 structural cells may differ; furnishing/garden stages must remain identical. The split-wing house must remain exactly unchanged.

## Review world

New isolated profile: `fabric/run/ceiling-review-26.2`.
Save within it: `saves/TES_Village_Comparison`.
Previous comparison and chimney-review worlds are retained untouched.

From the repository root:

```powershell
.\scripts\open-village-comparison.ps1 -GameDirectory .\fabric\run\ceiling-review-26.2
```

| Plains comparison pair | Template | Review |
| --- | --- | --- |
| 10 | `house_hall_04` | Raised low hall tie from the screenshots |
| 11 | `house_splitwing_05` | Unchanged higher ceiling |
| 1 | `cottage_hearth_01` | Raised low interior tie; sculpted chimney retained |
| 2 | `cottage_garden_02` | Raised low interior tie |
| 4 | `cottage_bay_04` | Raised low interior tie; slim chimney retained |

Use `/emerald comparison visit 10`, for example. Add 53, 106, 159 or 212 to a Plains pair number to review the Desert, Savanna, Taiga or Snowy version. The gallery contains production templates alongside vanilla comparison buildings, not a naturally generated village.

## Verification on 8 September 2026

- Fabric build/check and the full common regression suite passed.
- Final NeoForge build/check passed, including the full production catalog test (one test, zero failures/errors, 134 seconds).
- The exact ceiling comparison passed for all 52 masters in all five dialects. The complete catalog also passed geometry, circulation, lighting and support checks, with zero isolated cells or unanchored assemblies.
- The new Minecraft profile opened successfully and began generating its 265 comparison pairs and five context courts. At the last handoff check it was paused after the first 40 pairs; resume the game to continue generation. No completed screenshot pass or new aesthetic rating is claimed.
- No commit or push was made. Earlier saves and the user's other-drive copy were not modified.
