# Automatic city expansion

Unreleased beta.5 adds open-ended cities built from bounded districts. This is a
local source candidate, not a published release or a claim of unlimited performance.
Back up the world before upgrading: economy saves now use **format 24**, including food observations, progressive Bank plans and frozen project terrain work.

## What happens automatically

Existing and new settlements default to **Automatic**. No expansion directions or
player-drawn no-build zones are required. When an established town can afford it,
the server looks for a nearby, connected, loaded district site, reserves the first
home, and commits its funding and identity together before touching world blocks.
Four settlers queue for the new district; they become real villagers only when a
physical home, actual beds, food and a safe spawn are available. A funded founding
crew can build that first home even though the district has no residents yet.

Each district keeps the existing tier-5, 64-economic-resident and 12-project bounds
(including up to six housing projects). The **city has no fixed district-count cap**.
New districts inherit the city's architecture character and biome dialect and use
the existing detailed templates, needs-based projects and modest one-shot storage loot.
District organic construction labor is four times the otherwise equivalent rate.
Other Easy/Normal/Hard district production/population rules remain the shared baseline.

### Starting balance

| Requirement | Easy / Normal / Hard | Peaceful |
|---|---:|---:|
| Parent town tier | 3+ | 3+ |
| Sustained prosperity | 70+ for 3 economic days | 65+ for 2 economic days |
| Sustained safety | 45+ | 45+ |
| Time since last charter | 6 economic days | 3 economic days |
| New district endowment | 200 food, 400 materials, 80 treasury | Same |
| Supplies left in parent | 5 food units per committed resident, 40 materials, 20 treasury minimum | Same |

The newest founding district must finish its first physical home before another is
chartered. A projected maintenance reserve is also required. These are modest
starting gates, not a requirement for a perfect tier-5/100-prosperity city.
Economic days use the existing configured economic clock; the default is about
20 real minutes. This is not a guarantee of a new district every cooldown.

## Sustainable growth and upkeep

For a city with N districts, each district pays additional daily administration:

`0.065 * (N - 1)^1.18` treasury on Easy/Normal/Hard, or
`0.035 * (N - 1)^1.18` on Peaceful.

Existing building upkeep and food consumption remain. Thus total city administration
eventually grows faster than the output of simply adding more equally sized districts.
Districts share municipal surplus through the parent treasury, keeping local reserves.
Sharing conserves supplies and rotates priority by economic day; it never touches
player account balances or pulls money directly from earmarked donation/endowment pools.
Donations first follow the existing Fund spending rules.

After three consecutive unpaid-upkeep days in any district, new city charters wait.
The underfunded district also stops approving extra projects/settlers and loses
prosperity while the shortfall lasts. Already-paid projects can finish. Existing
buildings are **not demolished**, accounts never become negative, and clearing the
shortfall allows recovery. There is no permanent failure/city-size lock.

## How to help manually

Villages produce abstract food and materials automatically. Nearby growing crops and living
farm animals now increase that food production; harvesting fields or removing livestock
reduces the bonus. Plant, replant and breed animals to help without spending emeralds. See
[farms and livestock](VILLAGE_FOOD_SOURCES.md) for supported sources, range and balance.
Filling a normal chest
with bread is **not** a food donation mechanism. In **Banker > Fund**, donate emeralds:

- **Food** converts spending into food reserves.
- **Infrastructure / Housing** provides materials and development.
- **General / Trade** supports municipal treasury and development/trade.
- **Security** supplies materials and development for defenses; it is not an instant
  safety-score purchase. Guard projects and avoiding actual hostile casualties help.

Illuminate streets and entrances with torches, lanterns or other block-light sources.
Every loaded census samples a distributed 7x7 outdoor grid within 18 blocks of the
district center. Dry sampled surfaces with block light at least 4 count as covered.
Daylight does not count. After the normal incident stabilization window, full recent
coverage adds at most **0.20 safety per economic day**, on top of normal recovery.
The observation fades to zero over seven economic days without a new loaded census.
Stacking torches at one coordinate cannot exceed full coverage or the bonus cap.
The dashboard shows sampled outdoor coverage, not a guarantee that every interior
or every spawnable surface is safe.

## Controls and status

Open **Banker > Village > City expansion** to see district count, the current reason
for waiting, city administration cost, local outdoor light coverage and the food-source bonus.

- **Automatic** is the default, including migrated villages.
- **Approval required** lets the world owner/server operator approve one charter.
  Approval does not bypass resource, safety, loading or protection checks.
- **Paused** stops new expansion, physical construction and settler spawning.
  Ordinary upkeep, resource production and maintenance observations continue.

The owning single-player user and server operators can change these controls;
ordinary multiplayer visitors can inspect status but cannot pause someone else's city.
This check is server-side, not just a disabled client button.

## Automatic site protection

New Banks and projects tolerate ordinary torches, natural leaves, flowers, common bushes,
tall trunks and connected branches. New hillside lots may combine up to four blocks of
cutting and four of supported filling; deep shafts and cliffs still fail the support proof.
Storage, furnaces, crafting tables, stripped timber, standalone horizontal framing,
player-placed persistent leaves, fluids and protection vetoes remain excluded.

New reservations freeze exact preparation cells and their original states before changing
the world. Clearing consumes the same per-site two-operations-per-second allowance as
construction, rechecks loaded chunks and protection, and preserves changed non-vegetation
blocks. Natural age/leaf-distance changes do not stall the same approved plant. Ordinary
chests are never emptied or moved. Existing buildings and legacy reservations do not gain
retroactive excavation permission. See [terrain development](TERRAIN_DEVELOPMENT.md).

This is a contextual heuristic, **not perfect ownership detection**. Bare player-made
floating raw logs can be indistinguishable from tree debris. Deep shafts, lava,
large cliff drops and uncertain built sites are still skipped. Claim mods can use
the existing cooperative placement-guard API; this change does not invent a universal
integration for every third-party claim mod.

## Performance, saves and verification

- At most one charter per global census, with up to two eligible city attempts per
  dimension; deterministic search cursors revisit the connected frontier.
- No runtime forced chunk loading. Moving around the outskirts reveals more land.
- Each site places one block per ten ticks (two/second at 20 TPS), independent of other sites.
  Legacy speed keys normalize to this fixed rate. Settler attempts default to 600 ticks;
  other explicit settings remain unchanged. See [progressive construction](PROGRESSIVE_CONSTRUCTION.md).
- Bounded per-district records and build queues
  remain. Whole-economy persistence is still a full-state save, not an incremental
  database; sufficiently large cities can cost memory, save time and entity ticks.
- Format 19 introduced district links, modes, approvals, stable serials, cooldown/health
  history, upkeep shortfalls, lighting observations and new-lot preparation progress.
  Format 20 adds observed crops/livestock and their freshness date.
  Format 21 added frozen progressive Bank construction plans; format 22 adds project clearance plans;
  format 23 adds original/final terrain states for graded paths and retaining courses.
  Format 24 adds durable project-start evidence for safe background reservation recovery.
  Old architecture revisions are not rerolled. Older binaries must not open a format-23
  save; use the pre-upgrade backup to downgrade.

Automated checks cover city scaling beyond the old single-village caps, reserve
gates, supply conservation, upkeep tapering, founding crews, duplicate/failed-save
charters, old-format defaults, restart/pause behavior and bounded/stale lighting.
Opt-in isolated server checks exercise real torches, storage, workstations, house
walls, shallow craters, natural tree remnants, persistent leaf landscaping and claim
vetoes. A second isolated fixture commits a funded charter, advances economic labor,
prepares a torch-covered lot, builds the actual starter home through the production
materializer, spawns an actual villager after housing exists, and reloads the completed
project from disk. Extended survival play, subjective pacing and huge-city entity load still
need human gameplay review; no existing user world is modified by these checks.
