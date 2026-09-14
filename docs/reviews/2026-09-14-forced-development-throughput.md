# Forced development throughput and superflat construction

## Evidence and causes

Reviewed `TES-debug-20260914-013908-2FB69919.zip` without modifying the user's
world or installed profile. The beta.46 capture covers about 257 seconds at
19.995 TPS, with mean tick time 30.625 ms, a 374.55 ms maximum and a recent
118.43 ms p95. These are observations from the supplied capture, not a beta.48
performance benchmark.

- The medium house repeatedly repaired supplied dirt after it became grass:
  1,635 repair events, including the cell at -440, -61, -22. Natural soil
  transitions could keep final validation rewinding the construction cursor.
- The fisher cottage never obtained a site. A native thin-superflat fixture
  reproduced rejection: the surrounding-ground check treated bedrock beneath
  its shallow foundation as a crafted obstruction.
- Forced mode queued housing/food surveys but skipped their normal queue
  processing and settler arrival attempts. Finishing homes alone therefore did
  not reliably advance physical population.

## Implementation

- Treat dirt/grass transitions as satisfied supplied ground in construction
  and final inspection. Air, stone and other replacements are not waived.
- Recognize bedrock as natural backing around shallow excavation, never as a
  removable block. Frozen imported templates and their reservations stay intact.
- Forced search considers up to eight candidate centers per pulse, stopping
  between candidates when the shared cooperative time window expires. Normal
  search retains its one-center allowance. The existing shared 4 ms budget
  (1 ms when throttled) and Bank/project fairness remain; a native candidate is
  indivisible, so this is not a strict worst-case tick-time guarantee.
- Cache negative surveys only, capped at 2,048 entries per level. Chunk identity
  and block-write revision invalidate stale results without force-loading.
  Ground failures expire after 1,200 ticks; template/protection failures after
  100 ticks, also allowing protection-only changes to be reconsidered. Successful
  placement always passes fresh validation; a cache hit cannot grant permission.
- Forced retries use 20/40/80/100 ticks instead of ordinary search's 10-30 second
  waits. Existing partial-project delays are bounded when forced mode is enabled.
  Exhausted searches widen through up to four connected frontier rings while
  keeping owned infill first and retaining the existing candidate/territory caps.
- Drain the existing bounded housing/food queues in forced mode. Queue at most
  one new food-funded invitation, and attempt a real arrival at most once per
  20 ticks per selected village. Homes still need exclusive ownership, intact
  beds, safe access, clear landing space and threat checks. Failed insertion can
  release its unused claim; mode changes do not duplicate residents.
- Refresh tier eligibility as development and arrivals progress. No fake
  residents, free food, new tiers, unlimited territory or chunk tickets are added.
  Tier 5 remains the highest named tier; further growth follows existing limits.

## Validation

- Full common regression suite: PASS. Added checks cover retry bounds/toggles,
  expanded frontier ordering, invitation funding and safety, and growth from
  12 to 28 residents with tier-5 eligibility. Focused rerun also covers failed
  arrival claim release/replacement and the final handbook resources.
- Fabric and NeoForge full Gradle builds: PASS, including template/catalog,
  packet, settings and structure checks. Vanilla catalog validation: 166
  templates, zero failures.
- Both loaders' targeted dedicated-server smoke suites: PASS with
  `-Dthe_emerald_standard.forcedGrowthSmokeOnly=true`. Each constructs the
  926-operation fisher cottage and 1,092-operation medium house in all four
  rotations on thin soil above bedrock, with changing supplied soil, native
  survival checks and interrupted/resumed progress. Bedrock remains unchanged.
- Native checks also pass for rejection-cache invalidation/expiry/reset, safe
  housing and threats, one actual settler entity with a saved bed claim, occupied
  home waiting, and the real shared dispatcher with partial Banks, fence waits,
  competing/remote/blocked sites, mode toggles and restart.
- The smoke runner terminates its dedicated server after its success markers;
  Windows Gradle daemon termination messages after those markers are expected
  harness cleanup, not a failed assertion. Existing Windows performance-counter
  warnings remain. These disposable fixtures are not a sustained city benchmark.
- Fabric client tooltip smoke: PASS. All 64 compact handbook pages, guided
  wrapping/search at 80/120 text, and setting label/field/keyboard tooltip checks
  pass. A compact page overflow found during validation was corrected and rerun.
- `git diff --check`: PASS.

## Handbook and delivery scope

Updated guided construction-safety, districts and projects explanations plus
the compact projects page. Existing chapter routes already use these keys;
recipes and creative discovery are unaffected. Technical budgets/diagnostics
stay in help, handbook, documentation and debugging rather than newspaper copy.
Beta.48 also includes the separately reviewed 240-emerald Village Fund allowance
default and renamed setting; explicit saved values remain unchanged.

Validation used disposable worlds, not the user's save. Loaded land, safe homes,
food and server capacity still constrain how quickly a real village can grow.
