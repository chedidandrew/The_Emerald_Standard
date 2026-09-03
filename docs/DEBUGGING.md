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

## Captured systems

- Economic day, regime, event, asset prices, daily changes, commodities, savings and CD rates
- Initiating tester's bank cash, savings, holdings, CD, lending, net worth, and pending recovery journal
- Nearest village identity, lifecycle, population, housing, settlers, resources, outputs, tier, incidents, and development project
- Village census, casualty, settler-spawn, project-site, progress, blocked-placement, and completion events
- Banker GUI actions with requested amount and server result code
- Periodic invariant validation and capture-performance statistics

## Report files

Completed reports are written to:

```text
<world>/data/the_emerald_standard_debug/TES-debug-<timestamp>-<id>.zip
```

Each ZIP contains an incremental `timeline.jsonl`, summary, validation report, environment details, sanitized market/player/village snapshots, performance summary, optional marker files, and a copy of The Emerald Standard world configuration.

The timeline flushes after every event. If Minecraft or the server process crashes, the active directory remains and is automatically packaged as `INCOMPLETE-CRASH` the next time the server starts.

## Privacy

The report excludes the private economy seed, world seed, chat, server address, authentication information, and every unrelated player's account. It includes only the tester who started the capture and the nearby settlement relevant to the reproduction.

## Limits

- Default duration: 5 minutes
- Maximum requested duration: 15 minutes
- Maximum timeline size: 25 MiB
- Maximum events: 50,000
- Retained reports: newest 5
- Sampling interval: 1 second
- Full state snapshot interval: 30 seconds

When debug mode is inactive, no debug strings, snapshots, files, or visual effects are produced beyond a constant-time inactive-session lookup.
