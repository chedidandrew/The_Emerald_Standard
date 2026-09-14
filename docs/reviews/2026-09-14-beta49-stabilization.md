# beta.49 stabilization: review and two-district captures

This is an implementation and automated-validation record, not a human-play certificate.
The baseline was beta.48, `6b1c656b76a9e60ee64f4ccf9268560938f3c74b`.
Candidate version: **0.4.0-beta.49**. Economy format remains **40**.
Use the installed artifact's source fingerprint and its exact-commit Actions run; a green
default-branch badge or an earlier candidate does not certify this candidate.

## Evidence and decisions

- The supplied forced-on/off captures covered the same two developed districts. Forced-on
  averaged about 180.7 ms/tick; forced-off averaged about 27.8 ms/tick. The latter included
  long gaps between ticks, so its raw 15.6 TPS was not proof of continuously slow simulation.
  The older captures did not attribute CPU time to individual mods.
- Many connector records were unfinished even though the screenshots showed substantial
  paving. An unfinished connectivity record is not evidence that all visible paths are absent.
- The displayed 100 accommodation capacity was not 100 verified vacant beds. The later
  capture had 61 observed residents/beds. Immigration still requires real, safe, available
  homes; no fictional residents or permission to overwrite occupied cells were added.
- The latest rated review correctly recognized existing shared budgets, residential variety
  and vanilla integration. Those already-implemented systems were not replaced or retuned.

## Implemented

### Diagnostics

`/emerald debug` records capture-local inclusive subsystem time for village work, Banks,
scans, plot searches, template preparation, food, housing and walkways. Nested scopes
overlap: their times must not be added together or called CPU attribution. Counts distinguish
cache hits, misses, expiry and changed terrain. Scheduler records retain selection and actual
successful batch times separately, with at most 256 recent jobs and 128 timing/counter keys.
These measurements do not scan additional world blocks and are disabled outside captures.

Organic-site candidate counts now use the actual search order instead of the obsolete
64-position legacy list. Road reports include admission, visited tick, node count, target
survey progress and retry state. Housing reports distinguish theoretical accommodation,
cached bed heads, incomplete/unloaded scans and home commitments; arrival rejection counters
explain occupied, missing, claimed, unavailable or unsafe homes.

Whole-tick MSPT and raw observed TPS remain authoritative measurements of their stated
quantities. Gaps over one second are additionally reported beside a heuristic gap-adjusted
TPS value. A gap is not silently labelled a pause or counted as a long tick.

`/emerald debug profile` is an explicit owner-only add-on to a normal capture: up to 60 seconds
of JVM execution/native samples, sampled allocations, GC and long lock/park events. A 16-MiB
retention target bounds repository chunks, not a strict final-file size promise. The resulting
JFR is packaged with the ZIP; it includes thread/class/method information across installed
mods and adds overhead. Environment, property, file-content and network-payload events are
not requested. Review files before sharing. Normal diagnostics continue if JFR is unavailable.
The command does not change forced-development settings or load additional chunks.

### Runtime corrections

- Material-only road lookups no longer generate a complete authored building. A native
  equivalence check covers all five dialects and three palettes. An early local diagnostic
  measured roughly 454 ms for 15 full geometries versus 0.05 ms for their material lookups.
  This is a micro-measurement, **not** a measured whole-server speedup or a tick-time promise.
- Authored templates are reused during normal construction, not just forced construction.
  Relative geometry is reused across candidate plots using bounded 32-entry caches. Rotation,
  mirroring, dialect, materials, design seed and revision remain part of their identities.
  Frozen-hash mismatches still reject construction. Terrain, occupancy, ownership, inventories
  and foundation checks remain live; no cached geometry grants permission to build.
- Admitted walkway searches receive turns before more pending history is admitted. Searches
  still relevant to nearby players survive long queues; inactive distant searches can expire.
  Forced mode can request a road turn every two ticks instead of twenty, under the existing
  global admission and placement limits. Search length, node and simultaneous-job limits remain.
- A real path-cache defect was found while testing villager access: Minecraft invalidates
  cached path types through block-update notifications only in block-ticking chunks. TES can
  build in loaded, non-ticking chunks. The existing block-revision hook now invalidates the
  changed position's path type too. It neither force-loads chunks nor changes building designs.

### Reliability and presentation

- The recipe test now delivers pointer motion through Minecraft's installed GLFW callback,
  including its normal queued processing. It tests actual animation and hover pause/resume.
- A failed first client launch cannot proceed into a misleading preference-restart failure.
  The real font renderer verifies every compact handbook page; the harness no longer hardcodes
  a stale total. Long index descriptions remain in tooltips, with genuinely fitting summaries
  in the narrow market panel.
- The native suite yields between fixtures instead of placing the entire exhaustive run in
  one startup tick. Its watchdog and outer timeout remain enabled. Isolated terrain and sleep
  fixtures now initialize the same shared construction admission used in production.
- The standalone economy check exercises the actual constituent-priced VILX for 100 years,
  checking invariants rather than forcing a positive return from an obsolete proxy model.
- Market stories use event-specific reactions and several complete article structures,
  including shorter desk reports. Openings and lengths vary; quote notebooks, attribution,
  incident subjects, saved articles and economic RNG remain intact.

## Validation and limits

Local implementation-pass checks passed: the complete common regression suite, strict
client-log fixtures, both full dedicated-server suites, and Fabric client first launch plus
a separate process restart. Native checks cover loaded non-ticking path-cache invalidation,
normal/forced construction, occupied ground, actual villager travel, actual settler arrival,
protected containers, frozen designs, restoration, news, funds and persistence. The added
24-connection mature-history fixture completes every connector under bounded admission.
Further focused checks cover cache key changes/hash rejection and deterministic dispatch
opening/length variation. Consult the exact-commit workflow for final packaged and all-loader
client results, including optional Mod Menu.

Local Windows development launches also logged unrelated OSHI counter/authentication warnings;
they are not hidden or treated as a fully clean headless-client log. The Linux CI validator
continues to reject unexpected errors. Automated fixture durations are not ordinary-gameplay
MSPT measurements: fixtures intentionally simulate substantial work and are not enabled in play.

The original player worlds and modpack installation were not modified. A long real-player,
multi-village/modpack session is still needed to quantify the improvement and verify visual
quality. For that comparison, keep location, loaded chunks and other settings consistent,
allow warm-up, record normal mode first, then forced mode. If slowdown remains, take an
additional profile capture rather than raising safety limits or assuming it is only hardware.
Do not call every historical obstruction fixed solely because this suite passes.

## Handbook review

### Final CI follow-up

The first complete candidate run exposed two additional harness limitations: the Fabric
render thread was still creating its graphics backend when the helper's 15-second per-action
timer expired, and NeoForge's final fixture still ran the entire authored catalog in one tick.
Startup now uses its existing 90-second overall readiness deadline (ordinary UI actions stay
at 15 seconds). Live catalog validation yields between each of the 52 masters, then performs
the same aggregate distinctiveness, modular and entrance checks. No validation combination
or failure gate was removed, and the server watchdog remains enabled. These internal-only
follow-ups do not change player behavior; both handbook forms were reviewed and remain accurate.

A local reproduction also showed why merely returning between fixtures was insufficient:
Minecraft's watchdog measures against the scheduled next-tick clock. Repeated multi-second
fixtures accumulate schedule debt even when each callback returns. Admission now waits for
ordinary catch-up ticks to retire that debt; it does not rewrite Minecraft's clock or suppress
the watchdog. The captured native stack and advancing fixture log established this distinction.
The disposable playerless test world also explicitly disables empty-server auto-pause so long
suites continue ticking. No real server configuration is changed.
The native creative-egg fixture now keeps its dispenser outlet in the same chunk as its
verified manual spawns: loading an adjacent chunk synchronously does not guarantee that its
entity section is immediately visible to queries. Egg consumption, identity, persistence,
spawner refusal and loot assertions remain unchanged.

Reviewed both guided and compact/lectern forms. Updated profiling instructions, measurement
limitations and privacy guidance, and explained varied dispatches/brief reports. The existing
safe-home, construction-budget and protection explanations remain accurate. Market risk and
full basket details remain available in the handbook and tooltip. Both handbook forms retain
real-font layout validation. The manual matrix identifies beta.49 but keeps unperformed human
checks explicitly **Not run**.
