# Forced village development (debug)

In **Handbook → Settings**, enable **DEBUG: forced instant development**, read the
warning, choose **I understand**, then **Apply**. Off by default and world-local.
Cancel/Escape leave the toggle unchanged. Reset restores it to Off along with
all other defaults; Apply saves the reset. Confirm alone does not enable it.
Server administrators can explicitly opt in through
`village_prosperity.forced_instant_development=true` in the world configuration
and `/emerald config reload`. That administrative route logs a warning; it cannot
display a local single-player confirmation screen to a headless server.

## What it does

- Bypasses prosperity, safety, food, treasury/material costs, upkeep, tier/health
  waiting periods and Automatic/Approval/Paused growth modes. It also overrides
  the simulation/visual-progression toggles for this debug construction pass.
- Completes economic labor immediately, then constructs in **rapid bounded
  batches**, rather than placing an arbitrarily large building in one server tick.
  The normal configurable 2-blocks/second/site rate resumes when disabled.
- Creates varied authored projects within each natural village's single growing
  territory. It no longer charters artificial districts or adds a Bank per expansion.
  Two unfinished projects may coexist in a new district so one obstructed lot does not
  stop all independent work. Project records are capped at 512 per new district.
- Accelerates already-queued Banks too, when Bank generation is enabled.
- Does not mint player emeralds, fake prosperity/safety scores or synthesize
  residents. The debug accelerator does not spawn extra settlers or construction
  crews; old temporary worksite props use the normal ownership-aware cleanup.

## What still limits it

It only works near players, within the configured development radius, in **already
loaded chunks**. No force-loading or generation of distant terrain. Move outward to
keep the frontier loaded. Claimed/no-build areas, player structures and storage,
living occupants, liquids, safe foundations/entrances, the world border and frozen
blueprint compatibility still apply. "Safety" bypass means the village's economic
safety score, not permission to bury players or overwrite their house.

At most two accelerated work grants, 64 operations each, are available across the
server per tick. Lag reduces the shared allowance to 16. Batches check a cooperative
4 ms deadline (1 ms under lag); a validated first operation is allowed to prevent
starvation. Minecraft neighbor updates, terrain preflight and synchronous save
barriers are not preemptible, so this is **not a hard tick-time guarantee**.
Banks alternate ticks to leave ordinary projects time. Nearby districts rotate,
proximity queries use a spatial index, reserved/retired plots use their own spatial
index, and at most 32 accelerated blueprint templates are cached.

Bounded work grants drive project admission; the two-site backlog prevents queue floods.
Pending projects alternate, and a loaded outer site can work without loading the original
village center once its architectural palette is saved. Terrain candidates
try a rotating infill batch before a connected frontier batch. Only successfully
reserved lots expand the saved territory; neighboring natural villages retain exclusive
land. Missing optional decorations without valid support may be omitted; essential
structure still needs safe completion. The exact reported Cottage and Inn stalls have
native recovery fixtures.
No queued offline building or catch-up construction storm.

## Permanent effects

**Back up the world first.** Switching Off does not remove buildings/districts,
refund terrain or undo generated loot. Pending projects remain and may finish
under normal settings/eligibility. Restore a backup for a real rollback.

Indefinitely growing worlds cannot use constant disk space/RAM or guarantee a fixed
frame rate. More structures, chunks, ordinary entities and economic records still
cost storage, simulation time and checkpoint time. The bounded scheduler and indexes
reduce avoidable work; prolonged large-city soak testing remains necessary.
