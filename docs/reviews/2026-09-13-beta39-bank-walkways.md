# beta.39 Bank walkway validation

## Request and cause

Banks had a local authored north-facing entrance approach, but the separate walkway and lighting
queues enumerated only completed village projects. A Bank's handover was stored outside that list.
Simply adding its anchor would also have failed: the general 20-block Bank exclusion blocked
new paving between the front steps and the outside terrain.

## Implementation

- Add Bank-specific jobs to the same rotating connection and lighting queues. IDs use a reserved
  negative source value and the saved Bank anchor, separate from ordinary positive project IDs.
  Do not add a new per-dimension work allowance or rewrite historical building plans.
- Include existing completed Banks belonging to the active village, excluding Banker-only fallback
  records, unfinished replacement plans, unavailable entrance chunks and missing/turned entrance steps.
  Use the same configured horizontal development radius; normal visual-development and pause rules apply.
- Start on the actual front stair. Replace only the originating Bank's coarse exclusion with its
  courtyard/building exclusions and a narrow entrance corridor. Preserve other Bank setbacks and
  all other project lots. Keep the full setback for lamps.
- Join a completed building only when its connector is already finished, otherwise seek real dirt
  paths near the original village center. Do not count the Bank's own forecourt as the destination.
- A 132-cell route test exposed waste in the shared center-area search. Survey actual loaded road
  targets in at most 32 columns per pulse with a 2ms soft deadline before A*. Keep the 8,192-node
  cap, 384-block endpoint-distance limit, 512-cell route limit and existing footprint margin.
  This is a bounded search, not a guarantee for every possible landscape.
- Reuse land detours, bridge survey/funding/reservations, frozen route progress, occupancy checks,
  protection guards, village-style lighting and one-shot editing receipts.
- Handle Banks completed before the first saved expansion style: use the Bank biome and stable
  village architectural character until an established village style is available.
- Add Bank connection progress to debug captures, without new system-level player messages.

## Handbook review

Updated the guided Terrain and Bank Access chapters and the compact Terrain page. Existing
HandbookChapters routing and EmeraldHandbook pages already expose these sections. Updated
HandbookMechanicsRegressionTest and HandbookResourceRegressionTest to cover both wording and
the shared Bank/project lighting wiring. Existing bridge costs, settings, safety explanations
and recipes remain accurate; no new item or recipe is introduced.

## Verification

- PASS complete common regression suite, including handbook resources/mechanics, Bank placement,
  shared-budget scheduling, bridge persistence and loader-version parity.
- PASS focused Fabric and NeoForge native suites with
  `-Dthe_emerald_standard.bankWalkwaySmokeOnly=true`: complete progressive 1,204-operation Bank;
  its 132-center-cell walkway routed from front steps around the building to a real village road;
  original-village ownership, observer/radius gates, courtyard exclusions, two-write shared pacing,
  partial-codec reload, obstacle preservation, lamps and no regeneration.
- PASS existing native walkway tests (detours, claims, storage, occupancy, grades, unloaded chunks,
  route alterations and restart), lighting tests across 80 palette/orientation plans, and bridge
  tests including 34 designs, native navigation, shared crossings, funding/reload and safety.
- PASS `git diff --check`.
- PASS complete Fabric build (4m20s) and NeoForge build (3m56s): authored catalog,
  Bank geometry, menu packet, fence models and reader settings checks.
- PASS current-source fingerprint, packaged version, required report class and cross-loader parity.

### Candidate identity

Both loaders: `0.4.0-beta.39`.

Source SHA-256:
`738b27891d208d961b483fdc4ac3bceaa30337928cde619d7c3e01210bfbbec1`

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.39.jar`
  (16,106,719 bytes).
  SHA-256: `119175951949dc9e556bc863a02b7e8e8c37ac9af37406a52514d958d7d52c02`.
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.39.jar`
  (16,095,200 bytes).
  SHA-256: `7aa82c594ac75401618babcb4f34e9972df8713faaec5c6ef35c5d00e9109611`.

Native run logs: `build/server-smoke/fabric.log` and `build/server-smoke/neoforge.log`
(these paths are replaced by later smoke runs). Built locally; not installed, committed or pushed.

The native smoke worlds deliberately execute fixtures synchronously during startup. Their
"Can't keep up" notice measures that fixture setup/work, not ordinary-gameplay tick performance.

The earlier beta.38 broader native integration run stopped in the starter-cottage workstation
navigation fixture. That is outside this request and is not certified fixed by focused Bank
and walkway tests. No human shader/gameplay inspection has been performed for this candidate.
No existing user world or installed profile has been changed.
