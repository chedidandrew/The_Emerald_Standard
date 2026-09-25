# Five-priority upgrade validation — 2026-09-11

## Scope and candidate

Implemented the five accepted priorities: a server-authored Town progress report,
Fund allocation previews and accepted-payment receipts, a searchable/filterable investment
browser with local favorites and two-asset comparison, repeatable long-duration model
measurement, and aligned handbook/documentation/artifact identity. See
[the feature and limitation guide](../FIVE_PRIORITIES.md).

Candidate: **0.4.0-beta.6**, Minecraft 26.2, Fabric and NeoForge. Save format remains **32**.
No installed mod, real Minecraft profile or user world was changed. No release was published.
Existing unrelated changes in the worktree were preserved.

## Verification performed

- All **80 common regression entry points** passed, plus version/wrapper checks
  (`build/priority-common.log`). Coverage includes allocation rounding, malformed favorites,
  monetary conservation, replay/restart, bounded news and persistence.
- Full Fabric and NeoForge builds passed, including the existing authored-structure
  validation tasks (`build/priority-fabric-build.log`,
  `build/priority-neoforge-build.log`).
- Final assemblies, menu packet-codec and reader/settings checks passed on both loaders
  after the final not-ready-economy guard. Packaged versions, required report classes and
  matching source fingerprints passed `scripts/verify-candidate.ps1`.
- Native dedicated-server smoke checks passed on both loaders
  (`build/priority-fabric-server.log`, `build/priority-neoforge-server.log`).
  Actual menu interactions verify that reports and their hidden document cannot transfer
  items or mutate balances, rapid report changes eventually publish the newest mode,
  and accepted gifts show real payment sources/allocation. All 22 server quotes are covered.
  The later Fabric run also covers recent-diagnostic expiration and reset.
- Native clients on both loaders passed browser, comparison, report and handbook
  interaction/layout checks at **GUI scales 2 and 4**
  (`build/priority-client.log`, `build/priority-neoforge-client.log`).
  Screenshots are under `build/priority-client-459000b9a214445886ff85ce7ccc84d4/screenshots/tes-reader-ci`
  and `build/priority-neo-client-166cdb7648d0425cbe427dd2e04855bb/screenshots/tes-reader-ci`.
  Browser/comparison/report captures were visually inspected; final NeoForge comparison
  shows the full quote-risk caveat without overlapping controls.
- `git diff --check` passed.

These checks caught and corrected a legacy nine-asset menu-data bound, stale report-mode
transmission after rapid clicks, compact-book overflow, and a not-ready portfolio access.
Native test fixtures also needed default item components to render written books outside
a loaded world.

## Handbook accuracy review

Reviewed the actual Town reports, contribution allocation/payment path and investment
browser against both handbook forms. Updated guided long-form explanations in the
existing Market, village, Fund and recovery sections. Added three compact help pages:
browser, Town report and Fund receipt; **all 57 compact/lectern pages fit** in native checks.
Long-form reader scale checks also passed. Four existing animated recipes remain checked
against the server definitions; no crafting recipe or creative-only egg policy changed.

Corrected stale documentation about ownership fallback, construction pacing, the old
fixed-return calibration and durable inventory-to-cash recovery. The report does not
promise a settler, exact completion time, donor-specific spending attribution or profit.

## Model soak results and limits

All six scenarios completed **1,095 simulated economic days** each with save/reload every
90 days and deterministic continued market paths (`build/priority-soak.log`).
Times below are milliseconds on this machine, with other local builds running.

| Districts | Accounts | Physical backlog | Economic day p95 | Save p95 | Load p95 |
| --- | --- | --- | --- | --- | --- |
| 100 | 5 | No | 0.872 | 214.588 | 246.778 |
| 100 | 5 | Yes | 1.082 | 70.943 | 66.917 |
| 500 | 25 | No | 3.163 | 1425.665 | 1946.199 |
| 500 | 25 | Yes | 3.283 | 410.070 | 756.630 |
| 1000 | 32 | No | 6.203 | 2641.898 | 3267.508 |
| 1000 | 32 | Yes | 6.499 | 972.413 | 1387.267 |

This is **model-only**, not server MSPT or a multi-hour multiplayer certification.
It does not simulate chunks, lighting, entities, rendering or networking. Whole-state save
latency remains significant at large sizes; synchronous financial durability was not
weakened to disguise it. Multi-hour loaded-world/autosave/travel testing remains in the
human test matrix. Startup structure-catalog admission also remains expensive; the
dedicated-server harness's synchronous startup tests can emit a can't-keep-up warning.

The isolated server harness intentionally terminates after its positive integration marker;
a subsequent Gradle daemon-disappeared message is harness cleanup, not the pass criterion.
Known Windows Perflib/OSHI counter errors, offline development Realms warnings and Gradle/
Fabric deprecation warnings were distinguished from test failures.

## Final artifact identity

Shared production-source SHA-256:

`d2c7969456e28c5cc8f4b2ace4de0d786e191b598502ed1d73d23385b4dd2f64`

Playable artifact SHA-256:

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.6.jar`
  `F645B7591E4E893F72EAB9F97A8B690017D74684ECA6BA20B7E6A0A51B6D57FD`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.6.jar`
  `8D6272B9840B48018C3D754DA0AA7914127CF1BDD3F838EA3EEA1B03E5E5DE95`

The fingerprint identifies production inputs, not network compatibility with older
versions. Use a matching client/server candidate and a consistent whole-world backup.
