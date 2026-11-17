# Beta72: broader safe construction occupant relocation

## Evidence and scope

Read the user's TES-debug-20261005-203409-F7D8B3BE.zip without modifying the save. It was captured on beta69 with forced instant development enabled. Project 36, biome_plains_market_bazaar_05 revision 15, remained at 3158/5498 across 87 village observations over 90 seconds. Its required stone-brick cell at 296,-60,189 was air and repeatedly reported waiting_for_entities. The capture does not identify the obstructing entity, so a trapped animal is a plausible mechanism, not a proven species or exact entity replay.

The user authorized a broader safe movement fallback, not killing mobs or forcing construction through occupants. This is a live placement safety change, not a new geometry revision. Architecture revision 17 and all archived plans remain unchanged. Existing unfinished projects can use the recovery without migration, altered hashes or duplicate loot. No user world, live profile or installed mod was written.

## Implementation

VillageConstructionOccupancy retains nearby movement first. If it fails, sampled rings at 4,6,8,12,16 and 24 blocks check up to six blocks above/below the creature's current height, nearest distance and vertical offset first. This bounded search retries at most once per second per unchanged entity/cell/state wait, after the existing initial grace. Normal and forced building use the same safety checks; elapsed waiting never waives them. It is best-effort, not an exhaustive search of every terrain column or a guarantee that every occupant can be relocated.

Every destination needs loaded chunks for its complete bounding footprint, firm dry footing beneath the full body width, full collision clearance, no fluids or native hazard blocks, no other living occupant (including players in tracking transitions), no reserved construction cells, and valid world/world-border bounds. The search uses getChunkNow and never generates or loads terrain. A successful move stops mob navigation and clears falling motion/distance. Placement rechecks actual occupancy before proceeding.

Players and player-carrying vehicles remain unconditional vetoes. Ridden groups and leashed creatures are not separated. Fish, squid, dolphins and axolotls are excluded from dry-ground relocation. If no safe landing is found, construction waits rather than injuring occupants, excavating their footing or placing blocks through them. This fallback is called only on authorized construction paths, never by lot-admission surveys.

## Fixtures and review

Native fixtures retain initial grace, ordinary nearby clearance, final fence shape clearance, no-footing waits at 200/1000 ticks, fresh-cell wait isolation, read-only survey isolation and unconditional player protection. Added raised/sunken and farther/lower landings, actual block placement after clearance, motion reset, magma/cactus/campfire/powder-snow/water rejection, blocked body space, a broad iron golem with center-only support, an untracked player at a prospective landing, unloaded-chunk refusal, leash preservation, and four native aquatic exclusions.

An initial negative headroom fixture failed because its single ceiling block also provided a safe higher landing. It was corrected to block the full tested height rather than disabling valid rooftop destinations. Initial failing logs are retained as build/beta72-{fabric,neoforge}-stalls-initial-server.log. A pure regression initially expected the old compact phrase "Keep pets away!"; it now requires the new safe-movement phrase and bounded fallback limits while retaining no-build-through guidance.

Reviewed HandbookChapters, EmeraldHandbook page routing, HandbookRecipes, HandbookScreen and the four server recipes. No recipe, item, animation or creative-content change is needed. Guided Construction safety explains search bounds, pacing, full-body safety, loaded-only limits and protected/excluded occupants. The compact page says "Mobs: safe move." Resource/mechanics regressions cover the revised guidance. Native compact and long-reader wrapping/search checks pass, with no claim about unrelated pre-existing compact pages.

## Build verification

Both loader assemblies and shared pure-Java regressions pass. Final native client log build/beta72-client-construction-final.log confirms the changed compact page fits and the long reader checks pass. Both binary identities match canonical source SHA-256 e1c8f6733aad9ae54105b94fd8e1d21f1cc03dfd270528eb1c1b364d2565b903. All 7875 packaged architecture assets remain exact, including archived/current full and outdoor-fit forms; logs are build/beta72-{fabric,neoforge}-assets.log. No new export or geometry revision was necessary.

Fabric and NeoForge full native server suites each pass all 481 fixtures with the broader landing implementation, including saved construction, loot, ordinary/forced building, terrain, connections and Banks. Logs are build/beta72-{fabric,neoforge}-full.log and corresponding -full-server.log. The final additional aquatic exclusions were then tested against rebuilt release classes in fresh targeted runs on both loaders: build/beta72-{fabric,neoforge}-clearance-release.log. Both include the aquatic/leash safety success marker and final integration success marker, with harness exit zero. Test-workload catch-up warnings are not measurements of a live modpack. Gradle termination text following a positive smoke marker is the harness's intentional disposable-server shutdown, not ignored fixture failures.

Both staged binary/source pairs pass packaged verification, with SHA256SUMS alongside them. Fabric binary SHA-256: a65da50d91e37bacbd8b2a3930c0456be53da0761132dc0e9330a513e2ce0030. NeoForge binary SHA-256: e3e24d544137339f8dfa7aef92cb5a25e3838b9c2a97650315e4eb6a4925ce20. Final git diff --check passes. No claim is made that the specific unknown occupant from the user's capture has been moved in their live save; that requires testing the new release there.
