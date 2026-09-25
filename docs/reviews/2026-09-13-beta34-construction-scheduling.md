# beta.34 — construction mode switching

## Cause and scope

Both loaders invoked the project manager before the Bank manager. The first project claim consumes
all 16 operations in the reduced debug budget; even without that throttle, project work can exhaust
the shared four-millisecond deadline. Banks only tried on even ticks and therefore could receive
zero work indefinitely. The old budget regression fixture assumed Banks ran first, so it did not
exercise the real loader ordering.

The shared loader dispatcher now synchronizes the debug flag before either manager, then gives Banks
first access on their even work ticks and projects first access on odd ticks. Both managers still run
exactly once each tick, including lifecycle and discovery work. The existing 128/16-operation,
two-site, 4/1-ms cooperative limits are unchanged; this is fairness, not unbounded instant placement.
One safe operation can exceed the deadline; the budget is cooperative, not preemptive.

Related fixes: select among nearby, loaded, due Bank sites with an independent round-robin cursor;
clear transient Bank retry delays on mode changes and recheck real safety conditions; avoid spending
project grants on empty dimensions. Frozen plans, storage receipts and completion authority remain
unchanged. Actual obstacles still wait; switching modes cannot overwrite them or force-load terrain.

## Handbook review

Updated the guided Building projects section and its compact counterpart. Explained enabling debug
mid-build, retained progress, ordinary pace/eligibility after disabling, the separate Bank toggle,
nearby/loaded requirements, protected storage and backup/no-undo limitations. Also corrected its stale
claim that detailed search counters appear in the ordinary Town report; these are in /emerald debug.
Reviewed chapter routing, legacy page count and recipes: no item, recipe, chapter or format changes.
Added handbook mechanics assertions and retained native compact/long-form layout checks.

## Validation

Tests cover deterministic reproduction of the old starvation,
the actual queue dispatcher under reduced and expired budgets, both-loader wiring, multiple sites,
normal callback ordering and an empty priority queue. The native disposable-world fixture drives
real manager ticks through normal partial construction, a fence wait, forced enable, Bank-disable,
two active Banks plus a distant unloaded Bank and a player chest obstruction, mode reversal,
completion and restart. Existing full authored Bank, support-recovery, occupancy, protection,
handover/loot and construction catch-up suites remain enabled.

No live save/profile is modified, no repository push is performed, and no general server-performance
claim is inferred from synchronous test-world startup timings.
## Final results

- Full common suite: PASS (build/beta34-common-tests-final.log), including mode scheduling,
  both-loader wiring, normal pacing/catch-up, stored construction authority and handbook assertions.
- Full Fabric build: PASS, 2m 53s; full NeoForge build: PASS, 2m 56s. Both were reassembled after
  restoring compact retry guidance; final package logs are build/beta34-*-package.log.
- Both final dedicated-server smoke runs: PASS (build/beta34-*-server-final.log), including the
  actual Bank mode-switch fixture and all existing native construction/safety/ownership tests.
- Both native handbook client checks: PASS (build/beta34-*-client.log). Verified all 61 compact
  pages, all long-form chapter ends, search/navigation and GUI scales 2/4 at 80/120% reader text.
  Visually inspected the NeoForge Building projects page at scale 4, 120% text. Screenshots are
  under build/beta34-*-handbook/screenshots/tes-reader-ci.
- Candidate verifier: PASS current source/version and cross-loader identity. Verified both binaries
  include the shared dispatcher, budget scheduler and native Bank mode-switch fixture.
- git diff --check: PASS.

Initial verification caught a dropped compact retry reminder, restored before final tests.
An initial compile command named a nonexistent split-client task; compileJava is the correct task
for this repository's combined source set and passed. OSHI Windows counter warnings and synchronous
test-world startup overload messages are retained in logs; they do not establish live play MSPT.
Smoke scripts terminate their own disposable servers after successful markers, so underlying Gradle
logs can end with termination messages even when the wrapper and assertions passed.

Version: 0.4.0-beta.34.
Shared source SHA-256:
4eea42b8275245e7f54cf13b8c0566d485ef917e3ce09913c08b73b5ef156d07

Fabric binary SHA-256:
9b364e21d14b745c63e646723b172d20913ab5a55535ad69b75cc357d76a0ca7

NeoForge binary SHA-256:
bde57e5b482a1f923053226461a148e535e66e16ca2d167479a1b933a1c096cb

Playable JARs are in each loader's build/libs directory. Previous candidates remain untouched.
