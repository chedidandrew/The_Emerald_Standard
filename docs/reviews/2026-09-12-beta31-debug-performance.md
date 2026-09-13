# Beta.31: actual server timings and construction workload

## Requested behavior

/emerald debug now includes whole-server tick timings and global construction counts.
Start a capture, reproduce the workload, and run the same command again to package
the ZIP. Default duration remains five minutes; ownership, privacy, size and retention
policies are unchanged. No live-world actions or GitHub report uploads happen automatically.

## Timing boundary and report semantics

Minecraft 26.2 tallies its vanilla tick-time array before some loader END_SERVER_TICK
callbacks. TES performs construction in those callbacks, so reading that array alone
would omit important measured work. A shared MixinExtras wrapper measures the complete
tickServer invocation using System.nanoTime, calls the original exactly once and
records its result only while the same capture remains active. A tick straddling
capture start/stop is excluded. Exceptions are counted separately, not treated as
completed samples.

The wrapper follows the upstream [WrapMethod contract](https://github.com/LlamaLad7/MixinExtras/wiki/WrapMethod)
and is validated in both transformed native server runtimes. It includes tick work
and end callbacks, not the scheduled sleep between ticks, client rendering, or a
per-mod attribution of cost. Other profilers and unusual wrapper combinations have
not all been tested.

Capture-wide count, mean, maximum and strict over-50/100/250-ms counts are constant-space.
A 1,200-long ring supplies exact nearest-rank recent p50/p95/p99; it is copied/sorted
only at snapshots. Empty timings serialize as null. Observed throughput is completed
samples divided by monotonic elapsed capture time, not inverse MSPT. Freeze, sprint
and configured tick-rate context accompany samples. Pauses and boundary exclusions
affect throughput. These are measurements from the capture, not a stress-test grade.

Sample/snapshot/status deadlines use monotonic time. Approximately one performance
timeline event per real second is written while server ticks run. Large world-time
changes and sleeping cannot invent ticks or postpone sampling. Capture end timers
retain the existing wall-clock deadline.

performance-summary.json adds serverTicks, construction and sample-observed peak
counts; existing recorder-only timings stay compatible and explicitly labeled as
overlapping debug overhead. summary.txt also points out the actual captured values.
Flushed timeline.jsonl entries preserve paired timing/workload evidence after a crash.

## Construction scope and cost

A scalar saved-state census counts known villages, unfinished physical projects,
placed/unplaced/started/flagged projects, pending Banks, and separately completed and
abstract projects. It does not copy village histories/accounts, scan blocks, inspect
entities or load chunks. This traversal is linear in known project records and runs
only at diagnostic snapshot time; it is not constant-cost for unlimited cities.

A bounded five-real-second observation window reports working and waiting projects
and Banks plus phase counts. Recently placed batches remain working observations
even if they immediately return to an economic or obstruction wait; such sites may
appear in both counts. Completed/relocated jobs are excluded. Each observation and
progress map holds at most 2,048 sites; this is bounded recent evidence, not a complete
claim about every loaded site or a permanent census of active CPU work.

Global workload summaries contain counts/phase names only, not unrelated village,
resident or player identifiers. Forced-development and per-second construction speed
are now included in the existing public configuration allowlist.

## Handbook review

Expanded the guided recovery chapter with capture instructions, MSPT/TPS/percentile
interpretation, backlog versus recent activity, limits, and a normal-play baseline.
Updated the compact time/debug page. Existing chapter/recipe mappings were reviewed;
no items, recipes, finances, configuration values or world format changed.

## Validation

The focused native fixture records one real transformed tick and injects a 15-ms
delay into a recorder flush inside the loader's end callback. It checks that the
whole-tick timing includes that delay, rejects an unrelated capture identity, verifies
working/waiting/expired observations and opens the resulting ZIP to inspect the
performance summary and incremental timeline. It never runs outside opt-in disposable
smoke worlds.

Validation completed on 2026-09-12 with JDK 25.0.3+9:

- Full common regression suite passed, including numeric tick statistics and read-only
  construction counts. Handbook and debug regressions were rerun after the text correction.
- Fabric and NeoForge final Gradle builds passed, including the native authored-structure
  checks. Both dedicated-server smoke suites passed, including the transformed full-tick
  delay, capture identity, recent-progress expiry and ZIP/timeline fixture.
- The Fabric native handbook-only client passed all 61 compact pages and the expanded
  content/search/layout checks at GUI scales 2/4 and reader sizes 80%/120%. The first
  reader run caught a compact-page overflow; the shortened final wording passed.
- Updated the strict client-log validator and its negative fixtures from 60 to 61 pages;
  fixture tests passed. Host Windows OSHI/performance-counter warnings remain unrelated
  to the reader assertions. A full interactive multiplayer/modpack review is not claimed.
- Final playable/source JAR pairs passed packaged-content verification in an isolated
  fixture, preserving older local JARs. The new debug classes are required by that gate.
  Current-source and cross-loader identity checks passed; the negative test rejects two
  equally stale artifacts.
- Staged whitespace and source credential-pattern checks passed. Saves, raw captures,
  local continuation notes and generated build output are not part of the source push.

The final common source fingerprint is
`2fe9dac90810b2de9e1b46800959041f7cda4994d3e59428c6852dcfc8e7a2bd`.

Playable JAR SHA-256 values:

- Fabric: `ba78abfb1a66eecfdac6f96c31899f7e146fe606b4e8f6db8c2461c00f16d2cb`
- NeoForge: `9bb148d5e07c700761aef00d9593e0574dcc57021061b2b90a14ea2cbc3faf34`

These local validations do not certify a GitHub Actions run or grade the user's prior
stress test. Future captures will supply the tick-time and workload evidence needed
for that assessment.
