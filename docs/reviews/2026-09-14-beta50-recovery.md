# beta.50: per-build recovery, bounded road retries and Bank identity

Baseline: beta.49, `15f6eeb0c91cda5ab3f99d1ccd907255a0a5dad2`.
This is an implementation and automated-validation record, not a human-play certificate.

## Evidence

The supplied D044A9C2 capture had healthy tick times (about 10.39 ms average,
35.27 ms maximum), but repeated support waits at Mine 648/959 and Inn 2335/3749.
Rebuilding their consumed prefixes did not advance the same missing lamp-arm dependency.
Disposable fixtures reproduce the saved revision-10 desert `mine_adit_03` and
`inn_coachhouse_02` designs, exact cursor/total and local stalled cell. They do not
load or change the player's save.

## Recovery contract

- State belongs to one dimension, construction identity and handover stage. It is bounded
  to 2,048 transient entries per loaded dimension; eviction/restart means a fresh safe wait.
- Roughly 100 eligible game ticks without a higher verified prefix open a supports-first
  pass across phases. After 20 more eligible ticks, a temporary native-order pass can
  bypass TES ordering. The episode expires after 220 eligible ticks in total.
- Actual forward progress resets recovery. Rewinding and replaying an old prefix does not.
  Real obstructions cancel the override; inactive recovery windows expire. Another site
  never inherits the override, and later stalls earn their own attempt.
- Native survival/gravity checks, placement ownership, native occupancy, inventories,
  fluids, protection, economic admission and loaded-chunk gates remain authoritative.
  Optional never-supplied dressing keeps its existing omission policy. Essential unsafe
  or genuinely obstructed cells still cannot be falsely handed over.

## Walkways

Three unsuccessful surveys/invalidated plans defer a connection. Later attempts can join
the endpoints of another verified completed connection in the same village and bounded
survey area. Existing best-effort narrow shoulders remain in use. Partial supplied paving
is retained, and a disconnected path is never marked connected.

Deferred jobs do not hold active-search slots. Reviews are spaced by at least 100 ticks
and compare loaded chunk identities/revisions and connection/request topology, without
loading chunks or scanning blocks. Changed terrain, chunk reloads, requests or completed
connections allow a new attempt. Occupancy/unloaded waits on a frozen plan retain that
plan; they are not permission to overwrite anything. Protection-only changes from an
external integration may need a terrain/chunk/request change before deferred work wakes.

## Bank persistence

New automatic reservations use the saved village dialect (plains, desert, savanna, taiga,
snowy), falling back to the village center's biome when an old identity has no dialect.
An unknown village uses its plot biome. The chosen style and all after-states are frozen.
Existing completed Banks without a style receipt continue using their historical palette.
There is no repaint or replacement of player edits.

Economy format **41** prevents older readers from accepting the new Bank-plan trailer.
The BankConstruction trailer version 2 adds a validated dialect string; version 1 and
trailer-less plans still load with an empty style and unchanged cells/loot receipts.
`the_emerald_standard:bank_styles` retains style by Bank origin in each dimension.
It is refreshed from durable pending plans and flushed by the existing ownership
handover save barrier before the pending plan is consumed. Back up the entire world
before upgrading; do not downgrade an upgraded save in place.

## Handbook and validation

Guided construction safety, terrain and Bank-access sections explain the behavior.
Existing compact guidance is preserved; three additional compact pages cover site
recovery, road deferrals and Bank styles. Chapter routing, compact page counts, recipes,
icons and help were reviewed; no recipes or survival item access changed.

Automated coverage includes per-site timing/isolation/expiry and replay semantics;
both exact stalled designs in forced and normal modes; three-failure deferral,
unchanged-world quiescence, changed-terrain reopening and alternate network endpoints;
five-dialect selection and persistence; old-style absence and safe default fallback.
Use the exact commit's CI result for the full common, loader, server and real-font client
checks. Historical green runs are not a substitute for the candidate run.
