# Complete single-column chimney review

This follow-up introduced authored revision 8, Bank version 8 and gallery content 16.
Previous review profiles are preserved; the subsequent smithy-eaves pass uses revision 9.

## Scope

The revision-5 pass required enough exposed height for a full collar plus two wall courses.
It skipped shorter stacks and capped stacks. Revision 8 fills those gaps: the upper half of
an eligible exposed single-column stack becomes Brick Walls, with lower masonry retained.
Desert authored buildings use matching Sandstone Walls. Single-column caps stay supported;
wide architectural caps and the reviewed sculpted hearth cottage are preserved.

The foundry's two long one-column flues now have slim upper halves above their retained broad
furnace shoulders. Bank brick stacks retain the full roof mount, lower course and campfire,
with their two upper courses changed to Brick Walls. Non-brick Bank chimneys are unchanged.

Exact revision-7-to-8 comparisons find eight additional changed building masters across the
five dialects (75 wall-block replacements). Plains, Savanna and Taiga Banks each change two
brick courses. Existing lighting, furnishings, gardens and unrelated geometry remain identical.
The earlier shortened chimney, raised ceiling and pitched smithy improvements remain active.

| Plains comparison pair | Additional changed template |
| --- | --- |
| 3 | `cottage_courtyard_03` |
| 5 | `cottage_longhouse_05` |
| 6 | `cottage_orchardstead_06` |
| 8 | `house_dormer_02` |
| 9 | `house_arcade_03` |
| 15 | `inn_wayfarer_03` |
| 16 | `inn_tavern_04` |
| 32 | `smithy_foundry_05` |
| 53 | Bank |

Add 53, 106, 159 or 212 for Desert, Savanna, Taiga or Snowy equivalents. Pair 1 retains the
sculpted hearth chimney; pair 28 retains the previously reviewed pitched courtyard smithy.

## Review world

Profile: `fabric/run/chimney-completion-review-26.2`

Save within that profile: `saves/TES_Village_Comparison`

Only flat-world seed/settings metadata was copied into this fresh profile. No old chunks,
player data, economy data or gallery completion markers were copied. Older placed buildings
are not retroactively changed. The comparison gallery uses current production templates.

From the repository root:

```powershell
.\scripts\open-village-comparison.ps1 -GameDirectory .\fabric\run\chimney-completion-review-26.2
```

In game, use `/emerald comparison visit 3`, `/emerald comparison visit 32`, and
`/emerald comparison visit 53` for a short stack, foundry and Bank. The gallery contains
265 mod/vanilla pairs plus five vanilla context courts when generation completes.

Generation completed on 8 September 2026 at 20:42 local time: all 550 structures are ready.
The saved comparison manifest has signature `bf76b8e680d7bfef`, content 16, schema 1.

## Verification

- Fabric `build check`: passed, including exact catalog admission and Bank validation.
- NeoForge `build check`: passed; loader-aware catalog test has zero failures/errors.
- Full common regression suite: passed, including historical Bank plan routing.
- Synthetic checks: short and capped tips, retained masonry, protected positions and idempotence.
- Exact production delta: all 52 masters in five dialects, identical furnishing stages;
  only chimney-to-wall replacements in this revision. The exposed full-brick tip census
  admits only the explicitly preserved sculpted hearth.
- Existing contact checks: zero isolated cells and zero unanchored assemblies.

These are automated geometry, compatibility and lighting results, not a new Carol aesthetic
rating or a claim that screenshot review has been completed.
