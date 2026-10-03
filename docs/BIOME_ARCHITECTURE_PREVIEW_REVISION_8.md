# Architecture review revision 8 — log roofs, rooms and compact buildings

Branch: `codex/biome-architecture-preview`. No merge to main without the user's explicit
approval. This is the opt-in architecture review catalog, not a migration of buildings
or approved projects in ordinary worlds.

## Taiga direction

The supplied `minecraft_village_blueprints.zip` was inspected as visual reference data,
including the Taiga small/medium houses and compact examples from all five village
families. Its Taiga examples show bark-on horizontal spruce-log roof courses and exposed
end grain, with substantial cobblestone walls and small stepped arrivals.

Taiga review roofs and timber canopies now use spruce logs instead of spruce stair,
slab or plank roofing. Turned roof ranges use the corresponding horizontal log axis.
Cobblestone is the primary wall and perimeter-foundation material; timber posts,
shutters and interior floorboards remain. Existing moss accents stay deliberately
sparse, at no more than three blocks per plan. Snowy, Savanna, Desert and Plains retain
their own roof languages rather than adopting Taiga's roof material.

## Rooms and upper levels

Room composition now adds three-block-high partitions, lintels and paired internal doors
where the existing furniture and service approaches permit them. Residential sleeping
quarters and service-building stores/offices are separated from arrival/public areas.
No required bed or work-station approach may be sacrificed to manufacture a room.
Compact one-room huts and open markets deliberately remain compact/open; every building
is not forced to become a multi-storey mansion.

Suitable existing roof envelopes receive a carried attic floor, a backed ladder and an
internal hatch. Nine of the 22 new compact designs per style have an explicit second
storey, with sleeping rooms or offices/stores upstairs. There are **363 added room
partitions and 94 added accessible upper levels** across the review catalog. Existing
native Plains upper levels are retained and are not counted as newly added levels.

Upper routes require a roof overhead, clear headroom and a continuous ladder. New floor
plates must be structurally connected to ground-floor members. Existing roof curbs,
chimneys and perimeter materials are retained, not overwritten by the attic plate.
Roof-attached lighting is composed for the rooms after their partitions/floors exist.
These are authored review-plan changes, never runtime clearing of a player's building.

## Compact range and staged tiers

There are **22 new compact designs per style, 110 in total**: two each for Cottages,
Houses, Inns, Warehouses, Granaries, Smithies, Mine Entrances, Markets, Guard Posts,
Exchange Halls and Banks. Core footprints run from 5×7 through 9×11. Small buildings
have actual reduced bed capacities and purpose-built fittings, not a squeezed copy
of a large hall. Role silhouettes include loading porches, turned grain-barn gables,
forge chimneys, survey hoods, open market awnings, watch turrets and framed bank entries.
Desert versions retain pale terraces/parapets rather than orange timber gables.

The review selection policy uses displayed city tiers 1–5. Footprint sizes unlock as:

- Tier 1: tiny (up to 49 core cells) and small (up to 121).
- Tier 2: medium (up to 225) joins the available choices.
- Tier 3: large (up to 399) joins them.
- Tier 4: very large (up to 575) joins them.
- Tier 5: landmark-sized designs join them; every smaller choice remains eligible.

Every role/style has at least two compact tier-1 choices. Selection is deterministic
from a project seed and retains a nonzero chance for smaller buildings at higher tiers.
Signs display each design's size and first eligible tier.

**This policy is staged, not connected to normal village-growth approvals yet.** Review
approval and the subsequent production integration must handle new saved design IDs,
service metadata, construction admission and the internal development-tier mapping.
The ordinary released selector and frozen designs remain unchanged while the user
reviews the architecture. Do not present these as live tier changes in a survival world.

## In-game review

Fresh profile: `build/biome-preview-catalog-r8-01`. Earlier review saves are preserved.
No Modrinth profile or survival save was replaced. This catalog contains **375 TES
designs with 375 actual vanilla counterparts**, all identified by signs.

```powershell
./scripts/open-village-comparison.ps1 -ArchitecturePreview -FullCatalog -GameDirectory ./build/biome-preview-catalog-r8-01
```

Use `/emerald comparison visit <number>`:

| Region | All designs | New compact designs |
| --- | --- | --- |
| Plains | 1–75 | 54–75 |
| Desert | 76–150 | 129–150 |
| Savanna | 151–225 | 204–225 |
| Taiga | 226–300 | 279–300 |
| Snowy | 301–375 | 354–375 |

The world opens at **Taiga #279**, the tiny Cotter's hut. Nearby #280 is the garden
cottage, #282 the loft home, #283–284 the two compact inns, and #299–300 the compact
banks. The first 53 designs in each region retain the earlier master order; the 22
compact designs follow it. Normal gameplay generation and main remain untouched.

## Validation and handbook review

Fabric native admission and the final filtered NeoForge loader test passed. Native
admission covers all 375 plans, distinct compact exterior silhouettes,
deterministic tier eligibility, actual bed counts, lighting, furniture approaches,
roof joins, grounded fixtures and sparse moss. The census admits **858 doors with
zero blocked sides**. Negative cases reject missing ladder rungs, blocked hatches,
low upper headroom and disconnected floor islands, alongside the existing chair,
doorway and roof-gap tests. Original Plains source copies remain independently
checked before the separately authorized room/doorway changes. The revision-7
interior hashes are intentionally retired, not replaced with newly blessed hashes.

Fabric production `check`/`build` passed its frozen-template and Bank-version gates.
The common suite passed all 105 Java entrypoints plus loader-version parity and
wrapper checksums. The separate fresh-world helper copied only disposable fixture
generator metadata, never players or chunks. The live client completed **375/375
pairs, 750 structures**, including fragile-block survival and the final world signature.
It remains open for the user's visual review, without screenshot/export flags.

Handbook accuracy review: inspected `HandbookChapters` Building projects routing,
the English long-form and compact building-catalog guidance, and the lectern catalog
page. Production generation, saved projects, services, costs, recipes and protections
are unchanged, so those explanations remain accurate. The unreleased review catalog
and staged size policy are documented here, not advertised as ordinary gameplay.
