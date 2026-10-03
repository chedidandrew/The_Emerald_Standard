# Full biome architecture review — revision 4

Review branch: `codex/biome-architecture-preview`. Not merged into `main` and not
selected by normal village generation. The user approved expanding the small revision-3
checkpoint into the full review catalog, but has not approved gameplay rollout.

## Scope

All 52 existing master design identities now have review plans in each of five regional
styles, plus a separate Bank per style: **265 TES buildings**, paired with 265 unchanged
bundled Minecraft structure references. The reference is a role/scale analogue where
Minecraft has no equivalent financial building, inn, warehouse or mine.

Plains retains the current native master geometry, interior furnishings, upper floors and
fixtures. Only roof-phase dark-oak pieces are copied to corresponding oak blocks, preserving
facing, stair shape, slab half and waterlogging. Already-oak/open-air masters need no recoloring.

The four redesigned styles use regional envelopes and purposeful furnishing programs:

- Desert: pale sandstone terraces, restrained carved bands and shaded verandas.
- Savanna: acacia, terracotta bands, low hipped roofs and open workshops/markets.
- Taiga: spruce gables, shutters and **regular cobblestone**. At most two low mossy accents
  per enclosed catalog building; the Bank retains two tiny weathered paving accents.
- Snowy: spruce, white wall bands, stepped snowy roofs and sheltered entrances.

Homes have defined kitchen, dining and sleeping areas. Inns have guest wings and larger
public taprooms. Stores have loading desks and organized barrel stacks; granaries have grain
storage and agricultural fixtures. Smithies have separate work ranges; mine exhibits have
carried headframes or capped shaft displays (not functioning underground excavations).
Markets use paired stalls. Guard buildings have a ladder-accessible watch platform. Exchange
halls have a teller boundary and records wall. Existing revision-3 Banks retain their richer
regional furnishings. Courtyard/garden designs receive planted roof lights; selected larger
designs receive carried rear masses, crane bays or mill sails.

These are art-review plans, not production recipes. Preview beds/fixtures do not change
economic capacity, approval costs, saved project revisions or migration behavior. Full rollout
still requires terrain/entrance, construction lifecycle, service access and migration tests.

## Visit the world

Every TES building has an entrance sign with its number, biome, role and design family;
the earlier plot/reference signs also remain. Use:

```text
/emerald comparison visit <number>
```

| Style | Design numbers | Bank |
| --- | --- | ---: |
| Plains | 1–52 | 53 |
| Desert | 54–105 | 106 |
| Savanna | 107–158 | 159 |
| Taiga | 160–211 | 212 |
| Snowy | 213–264 | 265 |

Within each style, subtract its first number minus one to obtain this local index:

| Local index | Building family |
| --- | --- |
| 1–6 | Cottage |
| 7–12 | House |
| 13–17 | Inn |
| 18–22 | Warehouse |
| 23–27 | Granary |
| 28–32 | Smithy |
| 33–37 | Mine entrance |
| 38–42 | Market square |
| 43–47 | Guard post |
| 48–52 | Exchange hall |
| 53 | Bank |

Each district is eight comparison pairs wide and seven rows deep. The save's
`comparison-index.md` lists every exact design ID and corresponding vanilla reference.
The ready-world hook starts the local reviewer at Taiga #160 and prints the navigation guide.
No screenshots or export/capture batch is required for this review.

From the repository root:

```powershell
./scripts/open-village-comparison.ps1 -ArchitecturePreview -FullCatalog -GameDirectory ./build/biome-preview-catalog-r4-02
```

This dedicated profile uses a fresh empty creative superflat world. Only disposable generator
metadata was used to initialize it; no existing player data or chunks were copied. Old review
worlds/captures are retained. Signature mismatches stop placement instead of repainting a save.
The launcher refuses a missing save and never installs anything into a normal modpack profile.

## Validation

Native admission checks every one of the 265 plans: deterministic output, distinct maps,
declared bounds, retained Plains source cells/properties, role-specific beds, reachable declared
ground-floor fixture approaches, supported low decorations, spawn-safe authored lighting,
Desert palette restrictions, final snow-layer bearings and sparse Taiga moss. The original 13-sample checkpoint still
passes, including negative tests for floating pots, top slabs and pressure plates.

The full Fabric build, final filtered NeoForge loader admission (one test, zero failures/errors),
native catalog admission and common regression suite (105 Java entrypoints plus loader/version
and wrapper checks) passed. Final wiring and launcher syntax checks also passed.
Live gallery placement separately checks native fragile-block
survival after connection normalization and preserves both originals and current save markers.
No shader matching or complete subjective visual approval is claimed; the world is for that review.
The final client placed **265/265 pairs (530 actual structures)**, wrote its completed index/signature
and moved the reviewer to Taiga #160. It is intentionally left open without capture/export flags.

The first scratch placement stopped at a composed Snowy roof whose porch had replaced a
snow-layer bearing. Optional snow dressing is now kept only on the final supporting roof;
required structure is not waived. Scratch profile `biome-preview-catalog-r4-01` is retained,
not repaired in place. The final fresh review profile is `biome-preview-catalog-r4-02`.

Handbook accuracy review: checked the long-form Building projects chapter routing, English
projects/building_catalog reader prose and compact project/catalog pages against production.
They remain accurate: generation selection and gameplay have not changed. No art-review-only
plans are advertised as live features. No recipes or registered content changed, so unrelated
Handbook prose was not rewritten.

**Awaiting the user's in-game review and explicit approval before merging into main.**
