# Architecture review revision 5 — seating and exterior identities

Historical review checkpoint. See [revision 6](BIOME_ARCHITECTURE_PREVIEW_REVISION_6.md)
for the raised-roof enclosure fix. Keep this revision's signed save intact.

Review branch: `codex/biome-architecture-preview`. Normal generation, production
blueprints, costs and migrations are unchanged. **Do not merge into main without
the user's explicit approval.** This is the next in-game review, not a gameplay rollout.

## What changed

The revision-4 expansion relied too much on a common rectangular body and a regional
roof. Revision 5 gives each of the 52 master identities an explicit exterior program
in Desert, Savanna, Taiga and Snowy. Programs combine roof massing with attached
spaces: entrance bays, twin bays, verandas, arcades, offset loggias, glasshouses,
loading platforms, yards, gate towers, rear apses or turrets. These spaces have floors
and carried structural details; variety is not supplied by scattering random blocks.

Roof programs include longitudinal ridges, cross ridges, hipped forms, unequal split
wings, multiple narrow ranges, lean-to roofs, offset high/low wings, glazed courts and
crenellated watch roofs. Desert uses pale masonry terraces and stepped upper volumes
rather than orange triangular roofs. Savanna retains low acacia/terracotta forms;
Taiga uses spruce and predominantly regular cobblestone; Snowy uses sheltered timber
forms with supported snow dressing. The five Banks retain their separate regional
designs from the revision-3 checkpoint, rather than adopting a generic catalog shell.

Plains retains its native legacy footprints, interiors, upper floors and oak roof copy.
Its only new exception is the requested chair-direction correction. Production source
plans are not modified by the review copy.

Minecraft stair `FACING` describes the raised back. Preview benches now point that
back **away** from the table. A separate native-Plains audit corrects bottom-stair
FIXTURE/DECOR seats beside an unambiguous table or writing desk, including upper-floor
seats. It changes only their facing, not their block material, stair shape, half or
waterlogging. Architectural/roof stages are not seat candidates. Ambiguous adjacency
is not used to guess a new orientation. The native stair shape is tested in all four
directions, including deliberately backwards seats that must fail admission.

## Review world

This is still the complete **265-building TES catalog**, paired with 265 unchanged
Minecraft references. Role/scale analogues are used where vanilla has no corresponding
financial institution, warehouse or mine. Every TES entrance has a numbered
biome/role/design sign. The save's `comparison-index.md` lists the exact design IDs.

New dedicated profile: `build/biome-preview-catalog-r5-01`. Revision-4 worlds remain
intact. Never reopen an old signed world with new preview geometry to repaint it:
the signature guard deliberately rejects that mismatch.

```powershell
./scripts/open-village-comparison.ps1 -ArchitecturePreview -FullCatalog -GameDirectory ./build/biome-preview-catalog-r5-01
```

```text
/emerald comparison visit <number>
```

| Style | Catalog numbers | Bank |
| --- | --- | ---: |
| Plains | 1–52 | 53 |
| Desert | 54–105 | 106 |
| Savanna | 107–158 | 159 |
| Taiga | 160–211 | 212 |
| Snowy | 213–264 | 265 |

The ready hook starts at Taiga #160, where the repetition was reported. Within each
style the ordering is Cottage 1–6, House 7–12, Inn 13–17, Warehouse 18–22, Granary
23–27, Smithy 28–32, Mine 33–37, Market 38–42, Guard 43–47, Exchange 48–52, Bank 53.
The client opens for manual inspection without a screenshot/export batch.

## Validation and boundaries

Native admission checks all 265 plans for determinism, bounds, role beds, supported
low furnishings, declared approach reachability, lighting, final snow bearings,
Desert palette restrictions and sparse Taiga moss. In each redesigned style it also
requires 52 distinct exterior programs and 52 distinct normalized top-height/footprint
projections, ignoring palette and interior furnishings. This is a geometry regression
guard, **not** a claim of subjective visual approval. The seating audit identifies
588 unambiguous ground-floor table-facing seats across the catalog, plus the separately
checked native Plains fixture candidates. All other copied Plains cells must exactly
match their original state or the approved oak roof substitution.

The full Fabric build, native preview admission, filtered NeoForge loader test (one
test; zero failures/errors), and common suite (105 Java entrypoints plus loader/version
and wrapper checks) passed. Launcher syntax and diff checks also passed. The live client
placed **265/265 pairs — 530 actual structures**, completed its index/signature, and
moved the reviewer to Taiga #160. It remains open without capture/export flags.
Live placement separately checked fragile-block survival in the actual Minecraft world.
Production remains 52 revision-11 masters and version-12 Banks. A later approved rollout
would still need construction, terrain/entrance, costs, capacities and migration checks.

Handbook accuracy review: Building projects routing, English long-form building-catalog
reader text and the compact project/catalog pages remain accurate because production
generation and gameplay have not changed. No review-only program is advertised as a
live feature; no recipes or registrations changed. Unrelated prose is left alone.
