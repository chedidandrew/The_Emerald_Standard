# Vanilla village construction

TES expansion includes matching default Minecraft 26.2 buildings alongside its own
architecture. Plains, desert, savanna, taiga and snowy settlements keep the family
identified from their natural village, even when their district crosses a biome
boundary. The Bank and TES's specialized infrastructure remain TES designs.

## What can be built

The curated catalog admits 166 default templates: 151 buildings, farms and animal
enclosures, plus 15 occasional civic spaces. Housing, food, workshops and trade
projects choose appropriate compatible templates; recent use favors alternatives
before duplicates. Approximately half of eligible selections still use TES designs.
A town may choose one imported civic space after reaching tier two; it does not
create another district, Bank, or recurring production bonus.

Housing capacity uses the imported building's actual beds. Ordinary purpose-based
project costs still apply; these are not a block-by-block shopping list.
Containers start empty, crops start young, and no villagers, golems or animals
are supplied from template entity data. Existing settlement/recovery and farming
systems continue to control residents and output.

Road pieces, abandoned villages and standalone worldgen accessories are not
independent building projects. The plains meeting-point well that requires a
separate underground well-bottom connection remains excluded until that joint
foundation can be surveyed safely. Other town-center exits do not generate new
worldgen roads or extra houses; TES supplies the connecting walkway.

## Construction and safety

Templates become ordered TES construction operations, not instant worldgen
placements. Reservations, protected-site surveys, occupants, saved preparation,
shared work budgets, support ordering, final checks and walkway connections remain
in use. Shallow below-ground foundations and cellar air receive a protected
excavation survey; this does not authorize clearing chests or player structures.

The resolved block states are saved with the project before site construction.
Restarting, changing resources or switching construction speed does not reroll an
existing design. In-progress projects do not fetch fresh loot or spawn template
entities. Ordinary completed buildings remain editable.

## Scope and resource changes

This is vanilla integration only, not the optional modded-village expansion.
New imports require a recognized default natural-village family. Default structure
definitions and primary pools must still come from Minecraft's vanilla data pack.
Each template is checked against the exact audited 26.2 resource hash; a replacement
under the same `minecraft:` name is not silently imported.

TES reads Minecraft's resources at runtime; this repository contains metadata and
hashes, not copied Minecraft structure files. Admission processes one template per
server tick without scanning or force-loading terrain, then publishes the catalog.
Frozen plans remain independent of resource-pack changes. Missing block definitions
pause affected work with a debug diagnosis rather than substituting a different
building.

The guided Projects handbook and compact lectern pages document this behavior.
Technical template IDs, rejection reasons, plan hashes and operation counts belong
in debugging output, not ordinary town status.
