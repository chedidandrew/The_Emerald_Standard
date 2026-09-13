# beta.8 investment and confirmation edge cases — 2026-09-11

## Scope

Candidate **0.4.0-beta.8**, Minecraft 26.2, Fabric and NeoForge. This pass fixes
adjacent investment/payment boundary cases after beta.7. Save format stays **32**,
but the menu data layout changed: clients and servers must use matching beta.8 builds.

No installed mod, real profile or user world was changed. Native checks used disposable
worlds. Nothing was committed, pushed or published; older candidate JARs were retained.

## Reproduced and fixed

- **Money changes without a corresponding share change.** With a finite holding of
  2^60 shares, the old code accepted a one-emerald purchase and a one-share sale even
  though neither operation could change the floating-point holding. The probe retained
  exactly the same shares while cash increased by 98.75 E. It now rejects both operations
  with zero cash change. Execution checks reject unrepresentable/no-op changes and material
  rounding error; sales settle against the quantity actually removed. Inventory-funded
  purchases are checked before taking or depositing any physical emeralds.
- **A confirmation can change targets underneath the player.** CD closing is bound to
  the stable CD position ID, not its current list index. Sell All binds the asset, exact
  holding and quote; lending binds the amount and term. The server refreshes these values
  before comparing the confirmation. Changed targets/terms require another confirmation.
  Existing village-gift identity checks remain in place.
- **Tiny owned positions become impossible to sell in the UI.** Sell controls now use
  actual ownership rather than a rounded displayed share count. Positive dust is marked
  as less than the displayed precision instead of zero. Payable tiny holdings can be
  sold; sales that round to no payable cash are refused without discarding the holding.
  The smallest accounting unit remains 0.000001 E.
- **Current quotes truncate or cap.** Per-asset current quotes now travel as 64-bit
  micro-emeralds instead of 32-bit cents. Packet and native tests cover 0.000001 E and
  50,000,000 E quotes. The UI shows six decimals for positive sub-cent prices.

This is a guarded rejection policy at numeric limits, not a promise of arbitrary-precision
or unlimited-size investments. No arbitrary portfolio cap or automatic dust deletion was
introduced. Historical graph samples retain their existing separate rounding/encoding;
this patch changes current quote transport, not the historical chart format.

## Verification

- All **82 common regression entry points** passed, plus loader-version and pinned-wrapper
  checks: `build/beta8-common-final.log`. New execution tests cover the reproduced precision
  failure, payable/no-value dust, ordinary buy/quarter-sale cycles, spread and reload.
- NeoForge full build: `build/beta8-neoforge-build.log`. Final assembly plus native menu
  packet and reader/settings checks: `build/beta8-neoforge-final.log`.
- Both native dedicated-server suites passed:
  `build/beta8-fabric-server.log`, `build/beta8-neoforge-server.log`.
  New real-player tests cover replacement-CD identity, changed Sell All holdings, changed
  All-lending funds, exact quote ranges, payable dust, zero-value refusals and rejecting an
  unsafe purchase before inventory top-up. Existing inventory, replay/restart, construction,
  creative content, terrain/desk behavior and news checks also passed.
- Native GUI suites passed on both loaders at **GUI scales 2 and 4**:
  `build/beta8-fabric-client-verified.log`, `build/beta8-neoforge-client.log`.
  Checks include enabled tiny-holding sale controls and affordable sub-cent purchases,
  alongside search/amount editing and existing report/comparison coverage.
  Both loaders' scale-4 comparison screenshots were visually inspected.
- Native packet tests round-trip ownership, large holdings and the new low/high quote
  words, including adjacent data section boundaries.
- The isolated candidate-verifier regression accepted fresh fixture JARs and rejected
  two equally stale JARs after a source edit.

Early client checks caught compact-page overflow; the wording was shortened and all pages
were retested. A sub-cent purchase assertion initially retained an unrelated unapplied
amount-edit fixture; the fixture was corrected without bypassing the production draft guard.
Only the final positive native smoke markers are counted as passes.

## Handbook review

Updated the long-form market and amount/confirmation guidance and the compact/lectern
market and amount pages. The book remains **57 pages**; all pages passed native layout
checks on both loaders. Long-form real-font wrapping, chapter ends, topic search and
80%/120% reader settings passed. Resource regressions check the new guidance.

Animated recipes and the four server recipe definitions were reviewed and are unchanged;
existing recipe/native coverage remains active. Spawn eggs remain creative-only.

## Remaining limits

This does not certify every third-party mod interaction or multiplayer exploit. No
multi-hour real multiplayer/full-pack session or real-world migration playtest was performed.

The earlier [model-only soak](2026-09-11-five-priorities.md) still found mature whole-state
save p95 of about **2.64 seconds at 1,000 districts**. That measurement was not rerun for
beta.8. This patch does not resolve that scalability issue or weaken synchronous financial
durability. Incremental/sharded persistence needs separate migration and crash-recovery
work; the human loaded-world/autosave/travel matrix remains outstanding.

Opt-in synchronous startup catalog admission took roughly 56–66 seconds in the dedicated
tests. Startup can't-keep-up messages are not live-server MSPT benchmarks. The server
harness stops after its positive integration marker; later daemon cleanup is not the pass
criterion. Windows counter and Gradle deprecation warnings were distinguished from failures.

## Final packaging

The final full Fabric build passed, including authored structures, menu packet and reader
checks: `build/beta8-fabric-build.log`. Both playable JARs passed
`scripts/verify-candidate.ps1` against the current production source, matching versions
and cross-loader fingerprints. `git diff --check` passed. The retained beta.7 JAR hashes
still match their previous validation record.

Shared production-source SHA-256:

`da1b54012e1183d1b3bd0dabf46db1e03bdded995544def46a4f504f760cfc3e`

Playable JAR SHA-256:

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.8.jar`
  `ED47EF44CC32B37E9BF52360A00A26DA43FB966B67C7237267C3D90BFF5A4158`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.8.jar`
  `5974E1D63C74D3FBC6C016CC6C2D0DD11557CA6C3FE9C651F63C5C15DA0B1B97`

Use only the playable JAR for your loader, not its sources JAR. Back up the whole world
before replacing an installed build, and keep client/server versions matched.
