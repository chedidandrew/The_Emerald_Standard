# Beta.17 — Responsive site search and normal debug evidence

Date: 2026-09-12. Local candidate only; no installation, publication, commit or live-save edits.

## Read-only investigation

Inspected the user's Fabric 26.2 Modrinth profile and its `The Emerald Standard test` save.
The installed TES JAR was beta.13. The saved economy was format 33, economic day 81,
with tick counter 260276. District `e4d5e4f1-9958-0e58-9268-ffa2dcaee4e2` had:

- Inn project #7 (`inn_coachhouse_02`, blueprint revision 10): economic progress 100%,
  no origin/reservation, zero materialized blocks, six failed sweeps, cursor zero and
  retry deadline 274750. That leaves 14474 ticks, about **12 minutes 4 seconds at 20 TPS**.
- Its conservative `construction_started=true` legacy provenance was not cleared or
  treated as permission to discard a reserved/modified worksite.
- Another earlier Inn was also unfinished. The previous debug village summary only exposed
  the first incomplete project, so the added full project array matters for this case.
- The available `TES-debug-20260911-023027-5BBC501A.zip` predates project #7. Neither that
  capture nor the old properties file establishes why the actual candidate sites failed.

No world was opened in a game/server, no live chunks were loaded, and no profile file, live
config, installed JAR, player balance or world block was changed. Screenshots alone do not
justify removing buildings or clearing the surrounding field.

## Implementation

Unreserved search cooldowns now use 200, 400, then at most 600 game ticks (10/20/30 seconds
at normal speed), instead of reaching 24000 ticks. Old long search deadlines are clamped
when the district is next processed. The clamp persists, cannot slide the deadline forward
on repeated polls, and does not reset failures, candidate progress or construction provenance.
Repeated same-tick deferrals cannot accumulate a long unreserved search wait.

Reserved-site materialization backoff, manual-repair state, abstract-only projects, pause
rules, activation range, protection callbacks, storage checks, footprint reservations,
terrain/entrance limits and no-force-load policy remain intact. This is **not** a promise
to complete a search in 30 seconds: the original one-position-per-pulse budget, up to four
orientations per position and expanding bounded candidate rings still apply.

Fixed a missed progress checkpoint when a candidate reaches final terrain preparation but
cannot freeze a safe plan. That branch could repeatedly revisit the same candidate instead
of moving its saved cursor. Other unsuccessful orientations retain their existing checkpoints.

`/emerald debug` remains the same start/stop toggle. Without another subcommand it now includes
all projects, retry deadlines/remaining ticks, candidate cursor/count and bounded real search
evidence. Actual planner trials record unloaded footprints, terrain/height, blocked volume,
Bank spacing, entrance, template/protection, overlaps, preparation failure and accepted sites.
The full timeline captures trials while recording; session memory is bounded to 256 jobs and
16 recent trials per job, plus orientation-outcome counts for the current sweep. Session state
is cleared between worlds. Unknown earlier causes are explicitly unknown, not inferred.
Column-level diagnostics can use Y=0 as a marker; this does not assert an underground blocker.

Town > What next? Progress report replaces the generic exploration advice with actual search
progress, cooldown and last observed check. A report reads existing evidence; it does not
survey new chunks, approve sites or accelerate the simulation.

## Validation

- Full common regression suite passed before final handbook wrapping refinement: 88 Java
  entry points plus version/wrapper checks, `build/beta17-common-final.log` (exit 0).
- New pure regressions cover 10/20/30-second policy, overflow, legacy persisted waits,
  non-sliding and non-stacking deadlines, restart, reserved/manual-repair/abstract isolation,
  conservative provenance, bounded diagnostics and cross-world reset.
- The first common attempt exposed an invalid test fixture: a reserved job had a search
  cursor and the manual-repair fixture lacked a reservation. Corrected those fixture states;
  the focused test and full rerun passed. Production safety was not relaxed for the fixture.
- Fabric and NeoForge dedicated-server smoke tests passed, including actual native accepted
  and protection-rejected candidates, persisted last-candidate progress, terrain reason
  metadata and an all-project debug snapshot. Existing construction, Bank, inventory/fund,
  news and template-admission suites also passed. Logs: `build/beta17-fabric-server.log`
  and `build/beta17-neoforge-server.log`.
- The native handbook font gate caught compact page 24 wrapping to 16 lines. Shortened its
  summary without dropping the detailed long-form explanation. Fresh-profile client reruns
  passed on both loaders, including all 60 compact pages, 67 guided sections and report/browser
  checks at GUI scales 2 and 4. Logs: `build/beta17-fabric-client-b.log` and
  `build/beta17-neoforge-client-b.log`. The original failed logs remain available.
- Client runs emitted existing Windows OSHI/Perflib diagnostics. Actual mod assertion/success
  markers were checked independently of Gradle exit status; the failed font gate was fixed,
  not treated as an acceptable warning.

### Final candidate verification

The post-refinement full common suite passed (88 entry points, exit 0), including the new
handbook guidance assertions: `build/beta17-common-verified-b.log`. Both final Gradle
builds passed (exit 0), including native authored-structure/loader gates:
`build/beta17-fabric-build.log` and `build/beta17-neoforge-build.log`.
A report-screen screenshot was inspected for readable text and unobstructed scrolling controls.

All 154 Fabric and 153 NeoForge required packaged entries passed, including both new
site-search classes. Packaged version, current-source identity and cross-loader parity
passed via `scripts/verify-candidate.ps1`; `git diff --check` passed. Older candidates
were retained. The JAR check targeted the exact beta.17 filenames rather than deleting
older JARs to satisfy the verifier's clean-directory assumption.

Shared source SHA-256:
`ba21c6437980a80126e91e0a6c663119ea4664aac5898f4bfa680191186112a8`

| Candidate | JAR SHA-256 |
| --- | --- |
| `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.17.jar` | `F007F7DFE7118EC42988F1FD80E76F357A8D592A19F45E4B678CC198E992E238` |
| `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.17.jar` | `84A5AFCF062D67A68D4ECACAC5FF742E7973F79ADA39539A4BE26D8BD9E8E390` |

These checks do not certify the user's full modpack or claim that Inn #7 has now placed
successfully in their live world.

## Handbook review

Reviewed `HandbookChapters`, `EmeraldHandbook` and the two localized project explanations.
Updated guided prose and compact text together: Town report location, bounded cooldown vs.
full search duration, server-loaded vs. cached terrain, old deadline handling and the usual
two-toggle debug workflow. Added resource assertions to keep this guidance current. No new
chapter, compact page, recipe or item was needed; all existing 67 sections / 60 pages remain.
Economy save format stays 36; upgrading an older world should still follow normal backups.

## Next live check

After backing up and upgrading to the matching Fabric candidate, stand near the affected
district, run `/emerald debug`, keep that district active for about three minutes, then run
`/emerald debug` again and share the generated ZIP. This should capture actual rejected or
accepted candidates and distinguish search cooldown from activation/unloaded/terrain/overlap
constraints. The candidate has not been tested against the user's live Inn or full modpack.
