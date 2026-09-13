# beta.7 edge-case hardening — 2026-09-11

## Scope and candidate

Implemented the approved dashboard/funding/build-verification fixes and checked adjacent
failure paths. Candidate: **0.4.0-beta.7**, Minecraft 26.2, Fabric and NeoForge.
Save format remains **32**. The menu data layout changed: use matching beta.7 clients
and servers; an unchanged save format does not imply network compatibility.

No installed mod, real profile or user world was changed. Native checks used disposable
test worlds. No release was published, and older candidate JARs were retained.

## Fixed issues

- **Restoration with targeting disabled.** Preview, confirmation readiness and payment
  now share one read-only eligibility decision. An ordinary grant to an abandoned/extinct
  village is routed to Restoration before optional targeting restrictions. Global donation
  and village-simulation switches still block gifts; other targeted gifts remain restricted.
- **Mid-text editing destroys the suffix.** Search and amount inputs retain their native
  editor object through refreshes, preserving cursor, selection direction and horizontal
  scroll. Explicit Clear/Cancel resets still take effect.
- **Completed simulated plans crowd out unfinished work.** Town excludes economically
  complete abstract-only projects, prioritizes repairs/relocation and active construction
  before site searches, and states how many additional projects are omitted. A pending
  relocation is described as waiting for a replacement, not building at the old origin.
- **Tiny/large holdings display incorrectly.** Actual positive ownership has its own flag,
  independent of rounded money. Owned dust remains in holdings-only results and comparison
  shows <0.01 E. Holdings use 64-bit cents; a 50-million-E position no longer caps at the
  previous 32-bit display ceiling.
- **Two stale builds can approve each other.** The candidate checker hashes current source
  and compares each JAR against it, then checks cross-loader parity and source stability.
  Gradle and PowerShell share a manifest with normalized, ordinal path ordering.

Additional adjacent hardening:

- Missing/disabled sponsorship is rejected before inventory top-up, so an ineligible
  request cannot move emeralds merely to discover there is no project.
- A new report request or accepted gift clears the previous document while replacement
  is pending; an earlier receipt cannot masquerade as the newly accepted gift.
- Requested and periodic reports share one five-tick minimum refresh interval.
  Reports remain bounded and read-only.

## Verification performed

- All **81 common regression entry points** passed, plus loader-version and pinned-wrapper
  checks: `build/beta7-common.log`.
- Full Fabric and NeoForge builds passed, including authored-structure checks:
  `build/beta7-fabric-build.log`, `build/beta7-neoforge-build.log`.
- Final assemblies, menu packet-codec and reader/settings checks passed on both loaders
  after the final production edit: `build/beta7-fabric-final.log`,
  `build/beta7-neoforge-final.log`.
- Native dedicated-server checks passed on both loaders:
  `build/beta7-fabric-server.log`, `build/beta7-neoforge-server.log`.
  Added real-player inventory tests cover Restoration with targeting off, global-off
  rejection, shared preview eligibility and pre-top-up rejection. Existing checks cover
  spending routes, restart/replay, stale menus, full inventories and read-only reports.
- Native clients on both loaders passed at **GUI scales 2 and 4**:
  `build/beta7-fabric-client.log`, `build/beta7-neoforge-client.log`.
  Tests exercise middle insertion, selection replacement/paste and backspace across widget
  rebuilds for both search and amount fields. Tiny/large holdings fixtures passed.
  Fabric scale-4 and NeoForge scale-2 comparison screenshots were visually checked.
- The native packet test round-trips separate ownership flags, zero-cent owned dust and
  5,000,000,000-cent holdings, including the new section boundaries.
- `scripts/test-candidate-verification.ps1` accepts a fresh isolated fixture pair and
  rejects two equally stale artifacts after a source change.
- `scripts/verify-candidate.ps1` verified both actual playable JARs against current source.
- `git diff --check` passed.

The last report-wording edit was followed by final assemblies and the NeoForge native
server/client checks; it does not alter model or payment behavior.

## Handbook review

Updated the long-form market, village, fund and numeric/payment guidance, plus the compact
browser-help, Town-report and Fund-receipt pages. The compact/lectern book remains
**57 pages**. All 57 pages passed native layout checks on both loaders, and long-form
real-font wrapping, chapter ends, topic search and 80%/120% reader settings passed.
Regression checks assert the new guidance. The four recipe definitions are unchanged and
remain covered by recipe/native tests. Spawn eggs remain creative-only.

## Remaining limits

This is a tested development candidate, not proof that every possible mod interaction or
multiplayer exploit has been eliminated. No multi-hour real multiplayer run or real-world
migration playtest was performed.

The earlier [model-only soak](2026-09-11-five-priorities.md) still identifies significant
large-state save latency: at 1,000 districts, mature whole-state save p95 reached about
2.64 seconds. That benchmark was not rerun for this patch. Synchronous financial durability
was not weakened. Incremental/sharded persistence needs a separate design, migration and
crash-recovery review; the human loaded-world/autosave/travel matrix remains outstanding.

The exhaustive synchronous startup catalog checks remain expensive (roughly 48–52 seconds
in these runs). They are not live-server MSPT measurements. Windows Perflib/OSHI counter
warnings and Gradle deprecation warnings were distinguished from test failures. The server
harness deliberately terminates after its positive integration marker; a subsequent Gradle
daemon-disappeared message is cleanup, not the pass criterion.

## Final artifact identity

Shared production-source SHA-256:

`a0144e4f0952dccc84d8bd8dbdce624ae49577300cdea18523db6e3ce1537d51`

Playable JAR SHA-256:

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.7.jar`
  `2FFBD086192226395DC8F049D012FAE7EED8C3755CC7033AC4CF339EBCA86ADF`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.7.jar`
  `F67AFD39B7A32A5F0513E2DA44869103B40F4A3A810CB3F9C0BFF7A4B5FA0754`

Use one loader's playable JAR, not its sources JAR, with a consistent whole-world backup.
