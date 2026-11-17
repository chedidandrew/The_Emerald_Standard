# Terrain-aware village development

## Current behavior — beta.77

Delayed route surveys do not hold exclusive lot reservations. Every destination is rechecked against current lot metadata before commit, and the synchronized durable service refuses overlapping active or retired lots. Existing conflicting unfinished projects retire their old envelope and search elsewhere without deleting any old blocks; completed buildings take precedence, then the earlier project. Normal replacement needs resources for the consumed fraction, while forced mode waives those inputs. Completed-completed collisions are reported, never destructively repaired.

Preparation reports a separate actual-cell cursor, including partial batches and the exact loaded/blocked position. This count is a hint: every replay still checks actual blocks and can correct the hint after an edit or crash. Natural grass/dirt transitions do not obstruct preparation. A clear dry path/gravel/coarse-dirt surface can satisfy optional paving read-only; different structural materials, inventories, fluids, claims and missing footing are not waived. Occupied work cells never gain write permission through a timeout.

New approved biome buildings use a stable approximately 10% outdoor-ensemble choice; most start with the core-only fit. Essential farms, services and public streets/lamps are not rare. Decorated candidates try the full design and smaller outdoor fits before core-only fallback, while core candidates widen the frontier on each failed sweep. Existing frozen reservations retain their selected geometry.

Optional exterior ground columns are measured from the actual loaded dry terrain, not a uniform parcel pad. Paths, planting and fences follow bounded stepped grades. Connected tents, carts, wells, wood piles and other rigid assemblies move together; unsuitable grades decline that decor fit. The main building remains rigid. Offsets are frozen with the plan/hash in format 45 and reused on restart, not recalculated from a world already under construction. Existing builds and immutable approved geometry resources are unchanged. This is bounded fitting (four-block offsets and one-block adjacent steps), not unrestricted cliff terraforming.

Forced mode skips known occupied lot envelopes before native surveys and accepts the first fully safe orientation. It admits up to 32 candidate centers per visit within a shared eight-ms healthy-tick window (one ms under lag), alternates search-first visits with road/finishing visits, starts with two frontier parcels and widens after each failed sweep, capped at 32. Unplaced search retries take 2–20 ticks rather than 20–100. Optional lot buffers shrink from four blocks to two, including grading; the blanket 18-block Bank setback is omitted. Actual Bank/project lots, player records, protection, existing built blocks, storage, occupancy, loaded chunks, safe entrances and bounded foundations still veto work. Normal-mode preferences, rare decor frequency, frozen designs and bridge-first crossing safety remain. Searching and saving are cooperative bounded operations, not a promise that every lot is found within one tick. Debug differentiates surveyed candidate acceptance from an authoritative reservation rejection.

Native rail slopes/curves satisfy their original rail intent when supported and fluid-safe; this fixes the captured mine entrance retry without waiving missing rails or foreign blocks. Organic cross-water expansion approves a destination and its crossing before construction; see [Village footbridges](VILLAGE_FOOTBRIDGES.md). Diagnostics distinguish dry-land shortage, crossing required, steep terrain, unloaded chunks, occupied/protected lots and exact cell failures.

## Historical foundation and grading design

The following describes the original beta.5–25 foundation/road design. Later behavior and bridge support above supersede its historical no-bridge and format/pacing descriptions. It applies to new sites, not bulk reshaping completed structures.

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
Fresh exterior retaining courses use regular stone instead of polished andesite when the
building's terrain palette would supply polished andesite (beta79). This substitution is
confined to the outside lot border; it does not recolor authored building/interior cells,
walkway backing or other retaining palettes. All placement guards still apply. Previously
frozen receipts and completed walls keep their recorded material, with no live-world sweep.
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

## Raised entrance staircases (beta80)

Fresh reviewed TES structures and Banks attach required access to their actual outside
standing exit, including mirrored and rotated forms. Raised entrances descend straight
in three-block-wide rows, with matching non-falling backing and a grounded bottom row.
Road bends do not define the flight. Flat entrances receive no extra step or apron.
Access is saved with terrain preparation before writes and cannot be waived into a
disconnected staircase. Full-width obstructions reject the lot; player blocks, storage,
claims, fluids and unloaded chunks retain their protections. Exact-vanilla imports keep
their blueprint and receive only fresh exterior access. Older saved receipts stay frozen.
## Bounded construction recovery sweeps (beta82)

During a site's existing timed support/native-order fallback, builders recheck the earliest
pending cell and resume a transient rotating scan of later approved work. The scan keeps the
4,096-inspection cap, shared time deadline and block allowance. A separate scan hint cannot
advance the saved completed prefix past a required pending cell. It survives eligible short
episode expiry, but resets on real prefix progress, hard waits and inactivity (including the
normal interval between expired episodes). Restart starts normally; immutable plans and
handover verification are unchanged. Support-wait diagnostics include the recovery mode and
next scan position. This addresses bounded-scan starvation, not genuinely unsafe obstructions.
