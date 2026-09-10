# Terrain-aware village development

This unreleased beta.5 change applies to **new** construction sites. It does not reshape
completed structures, change saved architecture recipes, or bulk-edit existing worlds.

## Vegetation is site preparation

Banks and village projects share the same dry-vegetation survey. Ordinary torches, grass,
flowers, ferns, natural leaves, berry bushes, azaleas, bamboo, sugar cane, cactus, vines,
small mushrooms, snow layers and common newer bushes can be cleared inside the required lot.
Farmland/crop blocks, persistent player-placed leaves, inventories and crafted blocks are
not treated as general vegetation.

Tree recognition follows connected raw logs, including horizontal branches, looking for
an upright trunk and natural canopy (or an isolated floating upright remnant). Tall trees
are no longer limited to a canopy eight blocks above each trunk cell. Connected approved
trunks/branches intersecting the lot are included in the frozen clearance plan even where
they extend outside it, avoiding leftover floating timber. Leaf neighbor updates remain
enabled during clearance so unsupported natural canopy can decay normally.

Tree evidence is bounded to 2,048 connected logs, 32 blocks horizontally and 64 vertically
from the starting log. Unloaded evidence or a larger connected forest component declines
that candidate rather than force-loading chunks or clearing an unbounded forest. Nearby
crafted blocks and storage veto tree removal and excavation, including treehouse work areas.

## Hillside leveling and entrances

The median sampled ground height is clamped to keep every part of the required lot within
**four blocks of excavation and four blocks of foundation filling**. This allows up to eight
blocks of total surface variation where the ground and entrance checks also pass. Existing
four-block support geometry remains unchanged; filling stops at sound natural ground and
does not tunnel into caves. Floors and ground-contact annexes receive the same checks.

Only a bounded natural-ground column above the new floor may be cut. Suspended stone floors,
fluids, excavation immediately against water/lava, crafted neighboring structures and large
cliffs are rejected. No bridges across rivers or wholesale mountain removal are added.
Supported foundation courses and existing approach stairs meet lower ground. Safe, otherwise
unused border columns receive grounded retaining masonry up to the original hillside surface.
Courses step with the terrain; no random projecting decoration, extra tall posts or floating caps
are added. Authored cells and entrance-road columns take precedence over retaining work.

## Graded connections (format 23)

New managed projects also attempt to grade their primary road beyond the saved entrance stairs.
The route keeps its existing horizontal coordinates and meets its anchor's terrain (or the first
existing dirt-path/gravel/coarse-dirt road). Up to two blocks of cut/fill per road column smooth
small craters and slopes. Stairs face uphill, one-cell valleys become flat landings, raised
landings have non-falling masonry supports, and stairs have two clear blocks above them.
Flat ground uses gravel; shared existing roads are adopted unchanged.

Fresh Banks receive the same retaining treatment and an eight-cell north approach extension
beyond their existing wide staircase. This is a short approach, not a search for a distant road.
Existing authored three-wide stairs and building thresholds are unchanged.

Connections are bounded to 256 columns, loaded chunks and approved dry terrain. Storage, claims,
water, extreme grades or authored-cell conflicts cause the optional graded connection to be
omitted as a whole; they do not reject the building. The original best-effort trail system still
runs afterward. Consequently this is not a guarantee of a complete route across every river,
cliff or player obstruction, nor a road rerouting/bridge system. Side branches retain their
existing behavior. Retaining columns are independently best effort.

Managed village projects test all four orientations at each candidate position, preferring
level approaches. Their existing resumable position search still widens after failed sweeps.
Banks keep their north-facing saved blueprint, use the existing standard/recovery positions,
and choose a grade that accommodates their three-wide front approach. Authored doorsteps,
interiors, roofs and decoration recipes are unchanged.

## Persistence and protection

Project terrain work is stored in economy format **25** together with its reservation; Bank
terrain work is included in the Bank's existing frozen before/after plan. Clearing is top-down
and consumes the same per-site maximum of one block operation every ten ticks as building.
Vanilla neighbor updates, falling blocks and leaf decay are not extra authored placements.

Road surfaces, support blocks and retaining courses follow bottom-up and use that same budget.
Each cell now saves both its original and final state; legacy format-22 removal payloads remain
readable and do not acquire new terrain work. No authored architecture revision is changed.

Every mutation rechecks loaded chunks, the original block identity and the placement-guard
API. Already removed cells are skipped after restart. Age/leaf-distance changes in the same
approved vegetation are accepted; changed player walls and containers remain untouched.
Project clearance is flushed before its completion flag is saved. A blocked removal waits;
there is no new cancellation/terraforming UI. Legacy projects without a frozen clearance plan
retain the earlier vegetation-only preparation policy and cannot gain excavation authority.

Minecraft does not universally track who placed a block. These are contextual protections,
not perfect ownership recognition: isolated player-made raw logs or natural-material earthworks
can be indistinguishable from terrain. Registered claim/protection vetoes remain authoritative.
Back up before upgrading; do not open a format-25 economy save with an older binary.

The built-in [development protection](DEVELOPMENT_PROTECTION.md) layer now records meaningful
player placements and recognizes supported raw-log doorway frames. Explicit no-build areas are
the reliable option for older natural-material landscaping; no third-party claim mod is needed.

## Verification

- Common regressions cover cut/fill bounds, stable legacy supports and clearance persistence.
- Disposable real-server fixtures cover a 25-block branched spruce, bushes, connected treehouse
  storage vetoes, a six-block stone hillside, water/cliff rejection and unloaded frontier reads.
- A real position search rejects the first door orientation and succeeds at another orientation.
- A starter house clears a wooded/dirt site, survives a preparation restart, completes and houses
  an actual settler. Post-reservation storage and protection vetoes stop its clearing work.
- A complete Bank is surveyed and progressively built into a wooded four-block hillside;
  concurrent pacing, storage preservation, restart, loot and final neighbor stability are checked.

These are repeatable server fixtures, not a visual review of every natural biome or a guarantee
that every hillside has a suitable lot. Test on a copied survival world before publishing.
