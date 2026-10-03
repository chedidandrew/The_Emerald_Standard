# Architecture review revision 6 — exterior roof enclosure

Review branch: `codex/biome-architecture-preview`. **No merge into main without explicit
approval.** The user asked to focus on exterior roof gaps and leave the interiors alone.
This remains an opt-in art-review catalog, not a new production template revision.

## Cause and correction

Raised middle ranges, split roofs, offset wings and courtyard ranges could start one or
two courses above the room's wall top. Revision 5 closed only the overall parent outline,
using a fixed height. It did not close each higher component's own perimeter. The result
was the visible sky band under a apparently floating gable in the supplied screenshots.
The earlier uniqueness and furnishing checks did not test this enclosure condition.

Every component now declares its actual wall top and eaves base. An explicit masonry/
timber curb closes the intervening attic band around that component, including internal
roof valleys and the borders of glazed courts. Composition is finalized against those
declared join cells: a lower roof's half slab or a roof-light cut cannot leave a new air
band in a required join. Full glass remains an acceptable closed skylight/window cell.
This does not fill arbitrary air, block an open veranda, remove architectural variety,
or treat intentional glazed roof lights as defects. No world blocks are repaired in place.

The fix does not lower all roofs into one common shape. All 52 distinctive exterior
programs and normalized geometry checks remain. Plains keeps its exact legacy/oak-roof
review copy; the Banks keep their existing separate regional designs.

## Interior preservation

Revision-5 SHA-256 snapshots were captured before the roof edit for every catalog cell
at Y 0–4, grouped by biome and ordered by design/position/state. All five snapshots still
match, including all low floors, walls, beds, tables, chairs, rugs, cabinets and lamps.
The roof helper starts strictly above each component's declared wall top, not at a hard-
coded furnishing height. Tests exercise wall tops 4, 5 and 6 without changing any existing
occupied-storey cell. Plains' upper floors and furnishings are also covered by the full
exact native-source copy comparison. This is not an interior redesign.

## Review world and navigation

Fresh dedicated profile: `build/biome-preview-catalog-r6-01`. All earlier signed review
worlds remain intact; never repaint a revision-5 save with revision-6 plans. Every TES
building retains its entrance identification sign and its vanilla reference comparison.
No files are installed in a Modrinth profile or a survival save.

```powershell
./scripts/open-village-comparison.ps1 -ArchitecturePreview -FullCatalog -GameDirectory ./build/biome-preview-catalog-r6-01
```

Use `/emerald comparison visit <number>`: Plains 1–53, Desert 54–106, Savanna 107–159,
Taiga 160–212, Snowy 213–265. Banks are the last number of each section. The ready-world
hook starts at Taiga #160. The local index groups are listed in the
[full revision-5 review guide](BIOME_ARCHITECTURE_PREVIEW_REVISION_5.md).

## Validation

Native catalog admission covers all 265 plans. The four redesigned styles have **13,620
declared roof-join cells**, checked using actual Minecraft collision shapes rather than
block names or non-air occupancy. All required joins must be full, closed cells.
An additional 120 cases cover all ten roof forms, four regional styles and three wall
heights. Removing a required join or substituting a half slab must fail; replacing it with
full glass must pass. Existing furnishing support, access, lighting, seats, snow bearings,
palette restrictions, moss limits and exterior uniqueness checks remain in place.

The full Fabric build, final native 120-case admission and assembly, filtered NeoForge
loader test (one test; zero failures/errors), common suite (105 Java entrypoints plus
loader/version and wrapper checks), and diff checks passed. The live client placed
**265/265 pairs — 530 actual structures**, checking native fragile-block survival,
completed its index/signature and moved the reviewer to Taiga #160. It is left open
without capture/export flags. Visual approval remains with the user; geometry checks
do not establish subjective architectural quality.

Handbook accuracy review: checked the long-form Building projects chapter routing, English
building-catalog reader text and compact project/catalog pages against production. They
remain accurate: production generation, costs, materialization, services, recipes and saved
revisions have not changed. Internal art-review roof joins are not advertised as a released
feature, so unrelated handbook prose is not rewritten.
