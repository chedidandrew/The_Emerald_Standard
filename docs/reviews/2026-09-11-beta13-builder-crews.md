# Beta 13: stable construction crews and pause departures

Date: 2026-09-11

## Behavior and scope

Automatic builders previously rotated stations every ten seconds, stopped well short of the
actual station, repeatedly restarted navigation and faced a shared worksite origin. Together,
those choices encouraged circling, crowded approaches and short hammering periods.

Workers now keep valid exterior stations at least three blocks apart and approach the actual
station before hammering. They face the nearby wall or foundation. Useful paths are retained;
failed paths and sustained lack of progress allow bounded replanning. Blocked stations trigger
a search for another reachable position, with a temporary failed-position cooldown. New arrivals
try different sides rather than letting an unreachable first station prevent the whole crew.
Hammer animations have per-worker phases; quiet work sounds remain paced.

A brief interruption stops hammering. After 200 loaded entity ticks (about ten seconds at
20 TPS) of sustained paused, disabled or blocked work, automatic crews head away. The site census
normally refreshes every four seconds, so status detection is not instantaneous. Departures
disappear only when sufficiently distant and unseen. Watching or following them can delay
departure. Departing and unloaded UUIDs retain their crew slots until actual removal, avoiding
duplicate crews during rapid resume or reload. Active sites can then admit unseen replacements.

Existing fence receipts, deliberate fence gaps and saved construction progress remain intact
during a pause. No new save fields are required; departure state already persists natively.
Creative-egg/summoned builders without a worksite are unchanged: they stay, do not automatically
work and do not count as residents. Eggs remain creative-only.

This change is presentation and worker navigation, not additional construction labor. Builder
presence or hammer speed does not alter material costs, block throughput, housing, population or
financial balances. Manual Repair, player-edit protection, physical occupancy rules and site
search remain unchanged. No live-world repair or save edit was performed.

## Verification

- All 84 common regression programs passed: `build/beta13-common.log`. The handbook resource
  regression was rerun successfully after shortening the compact crew page.
- Both native client crew checks passed, including the production renderer at GUI scales 2 and
  4, phased hammer-arm transforms, animation stopping and all 57 compact pages. Long-form
  real-font wrapping and chapter navigation passed at 80% and 120% text sizes:
  `build/beta13-fabric-build-client-final.log`, `build/beta13-neoforge-build-client-final.log`.
- Visual inspection of Fabric scale-2/frame-0 and NeoForge scale-4/frame-3 captures confirmed
  clear walking/hammer poses, intact gear and unclipped previews. These are production model
  previews, not a shader-pack gameplay certification.
- Final Fabric and NeoForge candidate builds passed, including native structure/packet gates:
  `build/beta13-fabric-candidate-build.log`, `build/beta13-neoforge-candidate-build.log`.
- Both full dedicated-server smoke tests passed in isolated worlds:
  `build/beta13-fabric-candidate-server.log`, `build/beta13-neoforge-candidate-server.log`.
  The native construction suite includes an unreachable first station on an isolated pillar
  while other sides remain reachable.
- Candidate versions, packaged production-source fingerprint and cross-loader parity passed.
- `git diff --check` passed.

Native coverage uses real entities, blocks, navigation and NBT: stable two-worker assignments
across former rotation boundaries, hammer duty cycle, bounded path requests, blocked-station
recovery, eight-direction gate approaches, short and sustained pauses, rapid resume, unloaded
identity retention, surplus/orphan limits, saved departure state, watched/nearby departures,
unseen removal and returning crew limits. Existing fence preparation, protection/restoration,
banking, inventory conservation, restart/replay, news and Banker integration checks remain.

The first client pass caught a one-line compact handbook overflow. The page was shortened and
both native checks passed afterward. Host OSHI/Perflib warnings and the disposable Fabric
profile's Realms authentication warning were non-fatal; success is based on explicit mod check
markers, not merely Gradle's process exit status.

## Handbook accuracy review

Reviewed and updated the long construction-crew section and compact Site crews page against
the actual station, animation, pause, departure, visibility and saved-identity behavior. The
long page explains the approximate grace period, watched-worker delay and safe-arrival limits.
The compact page retains the distinction between automatic crews and persistent egg builders.
Recipes, creative content and construction safety rules are unchanged. The construction guide,
README and changelog are aligned; historical review documents retain historical results.

## Limits and upgrade safety

Visibility is best effort: the server knows player position and heading, not exact third-person
or freecam placement. Routing is loaded-only and does not force chunks or teleport watched
builders. Crowded, blocked or modded terrain can delay arrival/departure, but cosmetic workers
do not gate the building itself. An entity lost independently of its existing saved ledger can
still hold an empty crew slot until the job ends; this change does not invent missing entities.

Economy format remains 34. Back up entire worlds and use matching client/server versions; do
not downgrade converted saves to older formats. This is a development candidate, not a claim
that every modpack or player-modified route has been tested.

## Built artifacts

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.13.jar`
  - SHA-256: `A15A8ED6D1710B388B44CFA39655D761F5C266C92E970783F38E9602A7703E21`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.13.jar`
  - SHA-256: `1C4942C79E70CD0AD9382A8093C6095E3E0EC5199BF19355E2DEA8D5F4DAE9D9`
- Production source SHA-256: `c52ca8ebbf9be2c9d30791e7ac74436f929405bb7ab2d14627962829e6bd5907`

The candidate was not installed. No installed mod, live-world save or user configuration was
changed. No commit, push or publication was performed.
