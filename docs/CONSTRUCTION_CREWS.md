# Construction caution fences and visiting builders

## Automatic presentation

Fabric and NeoForge register `the_emerald_standard:construction_fence` and
`the_emerald_standard:builder`. These accompany active village projects and progressive Banks;
they do not alter authored finished-building templates or require a player to manage builders.
The dedicated mobs are not ordinary villagers: they do not trade, breed, occupy housing or count
toward district population/food. One worker is appropriate for footprints below 90 blocks, two
below 220, three below 450, and four for larger lots. Crews arrive at most one per site per four
seconds, after the currently placeable perimeter is established. Normal construction now waits
for that fence-preparation stage before placing structural blocks. For village projects, required
terrain clearing/leveling comes first so it cannot erase the new perimeter. Once prepared,
construction uses its configured allowance (default two block operations per second per site),
whether or not a crew can reach the site. No extra resource consumption is introduced.
The forced-development debug accelerator still omits new presentation and bypasses this gate;
Bank generation also bypasses it when visual progression is disabled.

Workers use Minecraft's native villager skin, biome clothing and toolsmith leather-apron textures.
There is no modern hard hat or reflective safety vest. All seven vanilla biome styles are supported;
the loaded worksite selects an outfit once using vanilla villager-type biome rules. The choice is
synced to clients and saved in entity NBT. Older workers without a choice adopt their loaded site
style; unassigned Creative builders adopt their spawn biome. Modded biomes use vanilla's fallback.
The apron is cosmetic, not a trading profession or resident job. Native resource packs can restyle
these textures without a second custom skin atlas. Biome clothes render before the apron, matching
vanilla's overlay order. Arms articulate independently while walking and hammering; the iron head's
long axis now runs fore-and-aft along the striking plane, not across the worker's body.
Work animations stop for waiting/paused work. Visitors
leave when their assignment ends. Paused, disabled and repair-waiting sites keep their
assignment ledger and existing fence receipts, stop animations and add no new fences or crews.
After ten seconds of sustained inactivity, loaded automatic crews depart. Brief interruptions
retain the crew. Departing UUIDs keep their slots until removal, so quick resume and unload/reload
cannot create overlapping replacements. Active jobs can admit new unseen arrivals once slots clear.

## Fence placement and restoration

- Real fence collision and connected yellow/black caution rails, not display-only blocks.
- A perimeter three blocks outside the reserved footprint, with three-wide gaps on all sides
  and entrance-aligned corridors. Unsafe columns may be skipped; terrain is not flattened just
  for tape. Neighboring active building footprints are also kept clear.
- At most sixteen new segments per site every four seconds, only in loaded columns with dry,
  naturally suitable footing. Existing protection/no-build checks and living-entity occupancy
  checks still apply. No force-loading for fence placement or arrival sight-line checks.
  Reaching the batch limit, unloaded perimeter/neighbor chunks, an occupied cell or a failed
  placement leaves preparation pending; structural work waits and retries. Gates, unsafe
  footing, protected cells and overlapping active footprints are deliberate exclusions, not
  reasons to stall forever. Prioritizing placement does not turn stepped terrain into a sealed wall.
- At most sixteen loaded sites receive presentation work per four-second pass, rotating
  fairly. The census uses cached unfinished-site metadata, not deep copies of every village,
  resident/fund history or all historical buildings. Real construction retains its separate budget.
- Displacement is limited to air, short grass, ferns, dead bushes, small single-block flowers,
  snow layers, and complete vanilla tall-grass/large-fern pairs. Trees, other tall plants,
  crops, farmland, storage and built structures are not
  excavated to make a prettier perimeter. This cannot determine who originally planted a flower;
  explicit no-build areas remain the ownership guarantee.
- Each placed segment saves the original block state and its worksite in a native block entity.
  Completion removes only the surviving owned fence and restores that state if it can survive
  on the current ground. This is exact block-state restoration, not newly randomized vegetation.
  Tall plants save both halves and check protection on both cells before placement. Restoration
  requires the upper cell to remain air; a player block above the fence is never overwritten,
  and an incomplete/invalid receipt does not create half a plant.
- A player-broken fence stays broken and is not rebuilt. A replacement chest/block is never
  overwritten. If a player removes the supporting ground, cleanup does not create a floating
  flower. Intentionally broken/replaced segments do not later restore vegetation over player edits.
- Unloaded segments clean up when their chunks reload and the active-site census is available.
  Automatic fences have no drops. The Survival recipe (four sticks, one yellow dye and one
  black dye) makes four manual fences; manual fences drop themselves when broken.
  Manually placed fences have no automatic job receipt and stay until removed.

## Arrival, assignments and departure

The mod's Creative tab provides separate Banker and Builder spawn eggs, with no Survival egg
recipes. Egg-spawned/summoned builders with no worksite persist and wander without placing
blocks, claiming crew slots, counting as residents or leaving when another site's job ends.
Banker eggs create an unscoped Banker-profession villager for personal banking, never a
canonical Bank assignment. They reject spawner configuration because vanilla spawners retain
the villager entity type, not the Banker profession.

The server samples loaded, safe approach positions 20-44 blocks from a work spot, at least 16
blocks from every nearby non-spectator player. It checks a broad viewing cone and obstruction
ray, and requires a real reachable navigation path before adding a worker. If all candidates
are visible, blocked or unavailable, it waits; the building itself is not stalled.

Workers use stable exterior work stations at least three blocks apart, facing their nearest
wall/foundation rather than a shared origin. Stations no longer rotate every ten seconds, and
workers do not enter planned rooms or future wall cells to act out work. Useful paths are retained;
failed/completed paths and sustained lack of progress permit bounded replanning. A failed target
is temporarily avoided when choosing another reachable station. If none is available, the visitor
leaves instead of repeatedly circling. Active workers hammer once they reach their station, with
individual animation phases. Work sounds remain quiet and paced. Real building progress is independent. After completion they head away and disappear only when unseen
and sufficiently distant; watched workers are not teleported away. A stranded unseen worker may
retire and let its active assignment try again. Visibility is best effort: the server knows the
player's heading, not exact third-person camera placement, FOV or freecam mods.

Native entity NBT retains assignment/destination/departure state. A dimension-local SavedData
ledger retains worker UUIDs, fence positions and completed fence-preparation state, so ordinary unload/reload does not spawn a new
crew over an unloaded one or recreate deliberately broken tape. Block entities independently
retain restoration receipts. An unregistered returning worker is sent away if the saved crew
already fills the site's quota. Pausing and resuming does not erase unloaded worker identities.
Legacy unfinished jobs without the new readiness field finish their remaining eligible perimeter
before further structural work; their saved removed-fence positions remain respected. A finished
preparation pass is not continually reopened to refill intentional player gaps or newly unclaimed land.
These files have Minecraft's ordinary chunk/entity/save durability,
not an atomic cross-file transaction; keep complete world backups. An entity lost independently
of its saved ledger may leave an empty crew slot until the project ends.

## Compatibility and test scope

Install matching updated client/server jars; the new entities, block and block entity need the
mod on both sides. Back up the entire world, not just economy data. No user test save is migrated
or edited by the automated validation. Older tagged display props and construction villager tags
are retired without deleting real villagers or unrelated entities. The prior material-display
delivery/scaffold effect is superseded by these crews and fences.

The opt-in dedicated-server smoke suite uses real placed blocks, block-entity and entity NBT,
SavedData codecs, a real walking worker, protected/replaced cells, player view vectors, paused
animation, sustained-pause departure/resume, stable multi-worker stations, hammer duty cycle,
bounded path refresh, changed-station recovery and departure cleanup. Fence-first tests cover the actual Bank and normal project
placement paths, bounded/occupied preparation, readiness reload and two-block plant restoration.
Client smoke uses the production fence/entity renderers and
asserts changing hammer-arm transforms. These are disposable fixtures and model previews, not
proof of flawless routing around every player-modified or modded terrain arrangement.
