# Walkway street lamps

Completed managed building paths receive a separate, optional lighting pass, including
paths completed before beta.24. Both Blueprint V2 and modular projects are eligible.
The saved trail route must have finished; invalid/manual-repair/abstract projects are
excluded. Existing building and paving orders remain unchanged.

The implementation reuses the refined yard lamp from AuthoredVillageStructures:
masonry socket and foot, matching fence post, stair knee, timber cap, slab arm,
iron chain and ordinary warm lantern. The owning project's saved materials are used.
For legacy modular buildings, the village character and biome determine the palette.
The arm is explicitly rotated toward the path, not randomly toward the surrounding yard.

Candidate stations are roughly ten centerline blocks apart, set back from entrances.
Routes shorter than nine cells keep their existing yard lighting. Sides alternate and
the opposite side is tried when necessary. Posts stand three blocks from the route
center, with the lantern arm one block inward. The complete lamp footprint stays out
of the three-wide walkway and other physical path branches. Posts are built on top of
natural ground, without replacing the ground surface. Both feet need solid support.

An already bright path station (block light at least 8) needs no extra lamp. A spatial
receipt index also prevents lamps within eight horizontal blocks of other supplied
posts at similar elevation, even after a lamp is removed. These are best-effort
lighting intervals, not a complete spawn-proofing guarantee.

## Safety, pacing and persistence

- Maximum one station inspection or up to two block writes per second per dimension,
  using the available development allowance. No whole-city unbounded world scan.
- Work runs only in the existing nearby, enabled village development loop. Ordinary
  paused development stays paused; explicit forced development keeps its normal override.
- Inspect the entire small plan and neighboring chunks before reserving it. Never
  request missing chunks. Try another candidate/project on later round-robin pulses.
- Respect development protection hooks, tracked player builds, no-build zones, project
  lots, Bank buffers, construction ownership, storage, fluids and entity collision.
- Place foundations and supporting members before the chain and hanging lantern.
  Missing/edited earlier supports stop that lamp rather than creating a floating arm.
- Per-dimension `the_emerald_standard:walkway_lighting` SavedData stores route progress,
  exact pending before/after states and reserved post locations. Reload resumes work.
- Supplied lamps are one-shot furniture with normal drops, not auto-repair resources.
  Removing one does not schedule a replacement. Mid-build edits abandon the rest of
  that optional lamp, preserving already placed blocks and player property.
- No inventory withdrawal or personal-account debit; no economy save-version change.

The guided terrain chapter and compact planning page explain discovery, appearance,
loaded-only backfill, placement limits and the no-regeneration behavior.
