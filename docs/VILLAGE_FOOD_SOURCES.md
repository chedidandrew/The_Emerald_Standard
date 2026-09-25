# Farms, livestock and village food production

Unreleased beta.5 automatically rewards nearby growing food and living farm animals.
This works on both Fabric and NeoForge, on all difficulties, when Village Prosperity
simulation is enabled. There is no ownership registration, farm designation or donation
button needed: player-grown, villager-grown and naturally occurring sources all count.

## How to help a village

- Plant and maintain wheat, carrots, potatoes or beetroot near the village. More planted
  blocks help; mature plants contribute more than newly planted ones.
- Bring cows, mooshrooms, pigs, sheep, chickens or rabbits into nearby pens and breed them.
  Living adults contribute one livestock unit each; babies contribute one-quarter unit.
- Harvest and replant normally. Harvesting removes the mature crop contribution; replanting
  restores a smaller contribution that increases as plants grow. If villagers harvest and
  replant, the same rule applies. Keeping several fields at different growth stages helps.
- Killing animals or moving them outside the district's observation area removes their
  contribution on the next completed census. Removing all qualifying crops and livestock
  removes this bonus, not the village's pre-existing baseline production or stored reserves.

The mod observes food-producing capacity; it does **not** harvest your fields, kill animals,
take inventory items or generate physical food items. Villagers do not gain new animal-leading
or breeding AI from this feature. Existing farmer behavior remains unchanged. Emerald-funded
Food donations remain a separate, immediate way to support abstract village reserves.

## What counts

| Source | Food-production units |
|---|---:|
| Wheat, carrots, potatoes, beetroot | 0.25 per seedling, increasing to 1 per mature crop block |
| Melon or pumpkin block | 1 |
| Melon/pumpkin stem | 0.05 to 0.25 with age; attached stem 0.25 |
| Sweet berry bush or cocoa | 0 to 0.5 with age |
| Sugar cane | 0.15 per block |
| Adult cow, mooshroom, pig, sheep, chicken or rabbit | 1 livestock unit |
| Baby of a qualifying animal | 0.25 livestock units |

Flowers, ornamental foliage, empty farmland, hay bales, stored items, pets and other animal
types do not count. Modded crops and livestock are not currently in the supported list.
Minecraft does not reliably identify who placed a crop: a placed whole melon/pumpkin counts
like a naturally grown one. Carved pumpkins and jack o'lanterns do not count.

## Starting balance

At full observation freshness, the agriculture-production bonus is:

`crops / (crops + 96) + 0.5 * livestock / (livestock + 12)`

For example, **64 mature crop blocks plus 12 adult farm animals yield +65% food production**.
That means 1.65 times the otherwise equivalent agriculture output, not a flat food grant and
not a 65% prosperity increase. Bigger farms and herds keep helping with diminishing returns,
approaching a combined maximum of +150%. Food consumption and spoilage still apply. This
stacks with the existing Peaceful production boost; other sectors do not get this farm bonus.

The increased agriculture output feeds existing food reserves and shortage/prosperity/growth
rules on the next economic-day calculation. It helps sustainability but does not bypass
housing, upkeep, loaded terrain or expansion requirements.

## Range, updates and the dashboard

Coverage starts with the district center's 64-block X/Z neighborhood, then expands to the
complete rectangle enclosing its developed project plots and Bank, with a **24-block margin**
for nearby fields and pens. Groundbreaking and completed projects count; untouched reserved
lots do not enlarge coverage. Old projects without saved bounds use a local envelope around
their origin instead of interpreting missing bounds as coordinates at world origin.
Intervening land is included. There is **no vertical cutoff**:
hillside, tower and underground farms in that footprint qualify when their chunks are loaded.
In overlaps, each source belongs to the nearest eligible district center horizontally, with
UUID tie-breaking. A farm is never credited to two districts in the same census snapshot.

The normal loaded village census schedules a fresh scan. Its default interval is **400 ticks
(20 seconds at 20 TPS)**; existing world settings are preserved. Crop reads are spread across
ticks with a shared 4,096-inspection budget. Empty sections and sections without qualifying
crops are skipped. Duration depends on developed area, farm density and queued districts;
larger districts may take longer. No chunks are force-loaded.
Partial loaded coverage counts only observed sources, not an extrapolated farm size.

Open **Banker > Village > City expansion** to see the food-production bonus and weighted
crop/livestock units. These are production equivalents, not a literal animal headcount.
Changes appear after the completed scan, not instantly on each harvest or death event.

An observation stays fully effective through the following economic day, then fades over
seven economic days without a new observation. This prevents an abandoned/unloaded farm
from giving an eternal bonus, including during offline catch-up. Counts shown on the page
are the last observation; the displayed bonus includes this freshness reduction.

## Loot is separate

The existing ten loot tables are selected by **building role**, not randomly across all
buildings. Granaries provide food/seeds; markets mainly food with a few trading supplies;
smithies provide fuel and modest metal/tool supplies; warehouses and mine entrances provide
practical materials. There is not a separate grocery-store or utility-store template yet.
Every eligible new container in a given building uses that role's table, including decorative
barrels; contents are not individually specialized by room or shelf. See the complete
[building-to-loot mapping](PEACEFUL_GROWTH_AND_LOOT.md#building-appropriate-chest-and-barrel-loot).
Taking chest loot does not change the farm bonus. Existing/looted containers never refill.

## Saves and verification

Food observations were introduced in format 20; the current build writes **economy format 21**
to persist progressive Bank plans. Coverage is derived from saved project bounds and does not
need a new territory field. Format-19 and older saves start with
zero observed food bonus until surveyed; architecture and existing inventories are preserved.
Back up before upgrading and use that backup rather than downgrading an upgraded save.

Common regression tests cover production, diminishing returns, Peaceful stacking, empty
observations, freshness, validation and persistence/migration. Disposable Minecraft server
fixtures cover real crop states and animals, harvesting, deaths/range, excluded pets and
overlapping districts, full-height distant fields, and developed-versus-reserved plot coverage.
Existing player worlds are not used for these tests. Long-term balance,
large-city performance and the dashboard still need normal human gameplay review.
