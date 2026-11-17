# One-command debug flight recorder

Use `/tesstructure` while aiming at a TES building within 32 blocks, or `/tesstructure x y z`, to identify its saved template and revision before sending a screenshot. Ordinary players may use this read-only lookup; `/emerald structure` is the operator alias. Click the chat report to copy it. Projects include outdoor fit, rotation/mirror, origin, project number, progress and plan hash; Banks include their variant/version. Overlaps report every matching plan, including completed and stale construction. It does not load chunks, repair structures or infer naturally generated buildings from appearance. New revision-16 usability fixes do not automatically rewrite older saved geometry.

For a covered-path construction wait, pending-cell inspection distinguishes the authored block from its stable `constructionState`, includes `plannedCoveredPath` and shows the actual block above. A required TES mine grate can make an authored dirt path physically impossible in Minecraft; only an immutable required blueprint cover selects stable dirt/grass footing. This does not waive a missing floor or foreign obstruction, and vanilla-exact plans are unchanged. Check the capture's version/source identity before comparing releases; installing a newer JAR does not require recreating an otherwise valid unfinished lot.

The Emerald Standard includes an operator-only diagnostic recorder for reproducing market, banking, village, construction, and persistence issues without memorizing a command tree.

Beta66 captures include each project's saved minimum/maximum lot bounds and separate terrain-preparation cursor, total and completion flag. Terrain waits identify position, frozen before/after states, actual state, inventory/fluid evidence, unchanged-state check and protection result. Validation flags overlapping active project footprints even when the economy's load-time invariants pass; old conflicting worlds remain loadable for non-destructive recovery. `overlap_relocated` records a retired old lot and replacement search; `waiting_overlap_recovery` means consumed-share resources or retirement capacity prevented recovery. `waiting_terrain_chunks` and `waiting_for_entities` are distinct from a physical `terrain_wait`. `waiting_terrain_save` means all terrain cells have reconciled but disabled saving or a failed save barrier prevents committing completion. Final connector waits identify the unloaded neighbor or exact connection position, actual/expected native shape, protection and occupancy. A 100% placement cursor can legitimately wait on these final checks; it is not a completion receipt.

## Normal testing flow

```text
/emerald debug
```

The same command starts a full five-minute capture when idle and stops the active capture when run again. Optional commands are:

```text
/emerald debug 10
/emerald debug mark
/emerald debug stop
```

The default command is the only one a normal tester needs to remember.

### Generation evidence (beta.65)

Keep the affected district loaded during capture, then stop and attach the ZIP. Project data includes the frozen design, outdoor rarity/fit, actual terrain-column offsets, saved search cursor/frontier/retry, and bridge-first dependencies. Observed lot trials include coordinates, rotation and rejection histograms: not enough dry land, crossing required, terrain too steep, chunks not loaded, occupied/reserved lots, protected territory, entrance failure or template obstruction. The current crossing/safe-lot state takes precedence over older rejected candidates.

Reserved unfinished projects expose up to eight next cells with actual and expected states, world position, role, phase, native survival, block entity, fluid and replacement detail. Required crossing reports include progress and missing/changed/unloaded gate reasons. These are read-only observations, not a command to repair player alterations or force-load chunks. Histograms count inspected orientations, not unique land parcels or every possible location. Before-capture/session causes remain unknown. Work counters distinguish metadata footprint skips from native survey/cache work, helping separate planner delay from block-placement delay. Beta77 reports the mode-specific frontier and candidate count plus `searchPolicy` (forced mode, lot buffer and blanket Bank setback). `site_reservation_rejected` means the survey passed but the synchronized final approval failed; its bounds and policy are recorded separately from `site_reserved`. It is not evidence of a built or claimed lot.

A server has at most one active capture. The operator who starts it owns it: another operator cannot stop it, toggle it off, or add markers. The capture closes automatically if its owner disconnects.

## Captured systems

- Economic day, regime, event, asset prices, daily changes, commodities, savings and CD rates
- Initiating tester's bank cash, savings, holdings, cost basis, realized performance, multiple CD and lending positions, net worth, and pending recovery journal
- One watched village's identity, lifecycle, population, housing, settlers, resources, outputs, tier, incident counts, development project, and Prosperity Fund totals. The recorder locks onto the nearest village within 256 blocks when one is first found.
- Census, casualty, settler-spawn, project-site, progress, blocked-placement, and completion events only for that watched village
- Banker GUI actions with sequence, requested amount, named server result, success/recovery state, post-action balances, and the initiating tester's journal state
- Persistence health transitions, including dirty state, catch-up work, and the initiating tester's recovery-journal stage
- Periodic invariant validation and recorder-performance statistics. Timings separately cover sampling, active recorder ticks, JSONL writes, full snapshots, and full economy-state copies; these measurements intentionally overlap and are not Minecraft subsystem-profiler timings.

## Report files

Completed reports are written to:

```text
<world>/data/the_emerald_standard_debug/TES-debug-<timestamp>-<id>.zip
```

Each ZIP contains an incremental `timeline.jsonl`, summary, validation report, environment details, sanitized market/player/village snapshots, performance summary, optional marker files, and an allowlisted copy of The Emerald Standard world configuration. The recorded mod version comes from the built JAR metadata; an unpackaged development run reports `development` unless the development version property is supplied.

The timeline flushes after every event. If Minecraft or the server process crashes, the active directory remains and is automatically packaged as `INCOMPLETE-CRASH` the next time the server starts.

## Privacy

The report excludes the private economy seed, world seed, chat, server address, authentication information, non-allowlisted future configuration fields, and every unrelated player's account. Village event hooks are scoped to the single watched village. Resident and settler UUIDs are not written, and a responsible player is represented only by a boolean indicating whether that player was the initiating tester.

## Intentional boundaries

This is a server-side flight recorder, not a sampling profiler or screen recorder. It does not capture screenshots, chat, arbitrary entity state, other players' portfolios, or per-method timings inside Minecraft and the economy engine. Use `/emerald debug mark` to identify the moment of a visible client-side issue and share a separate screenshot if one is useful.

## Limits

- Default duration: 5 minutes
- Maximum requested duration: 15 minutes
- Maximum timeline size: 25 MiB
- Maximum events: 50,000
- Retained reports: newest 5
- Sampling interval: 1 second
- Full state snapshot interval: 30 seconds

The recorder stops at either timeline limit; an individual event that would cross the byte ceiling is not written.

When debug mode is inactive, no debug strings, snapshots, files, or visual effects are produced beyond a constant-time inactive-session lookup.
