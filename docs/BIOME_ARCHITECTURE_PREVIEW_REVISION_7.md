# Architecture review revision 7 — usable entrances and connected wings

Review branch: `codex/biome-architecture-preview`. **Do not merge into main without the
user's explicit approval.** These changes belong to the opt-in review catalog only;
production templates, saved construction projects and existing buildings are unchanged.

## Findings and corrections

The native census found 385 doors and 28 blocked doorway sides. Porch dressing reused
fixed planter/rail positions from the small prototypes on differently sized buildings.
Some front bays stopped before the original facade; rear apses met the archive shelving.
Side towers could be isolated by the porch end posts or Savanna forecourt rails. The old
furniture-target test did not require every secondary doorway to be reachable.

The final review-plan composition now reserves a two-block-high passage immediately
inside and outside every door, using native collision shapes and a player-width center
volume. Obstructing planter pedestals, plants, rail pieces and low awning slabs are removed
only from those lanes. Added front bays get an explicit passage through the host facade;
rear apses get a clear archive connection. A small supported forecourt continuation lets
the reviewer reach side-tower doors around the porch barriers. Beds, service targets,
lighting, supporting roofs and the regional architectural programs are retained.

This is a bounded authored-plan edit, not a runtime bulldozer. It never clears blocks in
an existing world, changes protected-area rules, or silently rewrites production designs.
Necessary shelving/seat pieces that occupied a connector may change; it is not a promise
that every interior cell is identical. The rest of the interior design is not redesigned.

The admitted catalog changes **335 route-scoped cells in 57 of 265 plans**. Revision-5/6
occupied-storey SHA-256 baselines are retained and still matched before doorway edits;
the doorway pass separately accounts for its exact changes. The Plains exact-source
comparison remains against that retained pre-doorway copy. Its single low entrance slab
is corrected only in the review copy. The earlier roof-enclosure and chair audits remain.

## Admission and review

Every lower door must have a matching upper half, and orphan upper halves fail admission.
Both approach cells must be clear; actual opened-door collision shapes must permit
crossing. All regional added doors must be reachable from the building's arrival point
over supported authored floors. This catches a disconnected room even when both immediate
doorway cells are empty. Plains' multi-level routes retain the native production admission;
every Plains door additionally receives the new collision and half-integrity checks.

The native regression suite includes eight direction/hinge opening positives and 57
rejection cases: logs, plants, fences, solid walls, low top-slab canopies, missing door
halves and a detached room. All 265 plans retain the existing bed-capacity, furniture
support/access, lighting, roof-join, snow-bearing, sparse-Taiga-moss and uniqueness gates.

Fresh review profile: `build/biome-preview-catalog-r7-01`. Earlier signed review worlds
are preserved; do not rebuild them with changed plans. Building identification signs
and vanilla reference counterparts remain. No Modrinth or survival-save installation.

```powershell
./scripts/open-village-comparison.ps1 -ArchitecturePreview -FullCatalog -GameDirectory ./build/biome-preview-catalog-r7-01
```

Use `/emerald comparison visit <number>`: Plains 1–53, Desert 54–106, Savanna 107–159,
Taiga 160–212, Snowy 213–265. The ready-world hook starts at Taiga #160. The local identity
groups are listed in the [revision-5 navigation guide](BIOME_ARCHITECTURE_PREVIEW_REVISION_5.md).

Handbook accuracy review: checked the long-form Building projects routing, English reader
building-catalog guidance and compact catalogue/project pages. Production generation,
recipes, project costs, protections, services and saved design revisions are unchanged;
those explanations remain accurate. The unreleased art-review doorway edits are not
advertised as a production feature, so unrelated handbook prose is not rewritten.

## Completed validation

Fabric native admission and the filtered NeoForge loader test passed: all 265 plans,
385 doors, zero blocked sides, the 57 doorway rejection/eight opening cases, retained
pre-doorway snapshots and all 120 roof-enclosure cases. NeoForge reports one test,
zero failures/errors. The common suite passed all 105 Java entrypoints plus loader
version parity and wrapper checksums. Fabric production `check`/`build` tasks completed;
final assembly passed. The separate fresh-world fixture helper initially lost its compiled
class during concurrent validation; recompiling that helper and rerunning only setup
succeeded without modifying an existing save.

The live Fabric client completed **265/265 pairs — 530 actual structures**, including
native fragile-block survival checks and the final index/signature. It reached the signed
Taiga #160 starting point and is left open without capture/export flags. For the corrected
Taiga arcade house entrance, use `/emerald comparison visit 168`. Visual approval remains
with the user. Main stays at `26b049b947bede6169d42afae5c7772209458cca`.
