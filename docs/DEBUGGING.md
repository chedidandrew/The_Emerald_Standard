# One-command debug flight recorder

The Emerald Standard includes an operator-only diagnostic recorder for reproducing market, banking, village, construction, and persistence issues without memorizing a command tree.

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
