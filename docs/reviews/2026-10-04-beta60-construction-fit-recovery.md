# Beta 60: construction handover and outdoor-fit recovery

## Evidence and diagnosis

Read-only inspection of the player's beta-59 capture `TES-debug-20261004-165328-003AB0B9.zip` and its saved Flat test world found one pending Bank, version 17, at (320, -60, -2). Its frozen plan contained 846 cells. All 31 mismatches were expected grass blocks that had naturally become dirt; all other planned block IDs were present. The capture recorded no mod errors or warnings. This explains a finishing pass refusing handover despite the building appearing complete. It does not prove that every earlier capture or every other obstruction has this cause.

Bank completion now accepts dirt/grass transitions, as ordinary construction already did. Covered builder-supplied paths may settle into dirt only with a construction ownership receipt. Reservation alone is insufficient. Missing ground, foreign walls, containers, protected blocks and unloaded cells remain blocking. Rollback ownership matching remains strict. Pending Bank plans and the observed first waiting cell are now included in debug captures.

## Fit and territory policy

- New revision-13 ordinary designs attempt complete landscaping, a smaller rear court, front-and-side decoration, then the intact building with essential access. The 1,125 immutable fit assets are nested subsets of the 375 full designs; every building cell is retained. Omissions remove whole outdoor columns, avoiding floating fragments. The core-only fit retains a narrow supported approach rather than severing the street connection.
- A chosen ordinary fit, plan hash and reservation are saved atomically. Existing approvals, partially built structures, archived designs and their hashes are not resized or rewritten. New Banks similarly try the four fit levels across their existing safe candidate search, and freeze the selected geometry into version families 32–34, 48–50 or 64–66. Versions 13–18 retain their original geometry.
- Organic district search completes a bounded sweep at each fit level, then starts full landscaping again on a wider frontier. Saved cursors and rejection-cache keys distinguish fit levels. The maximum frontier is 32 sixteen-block parcels beyond held territory, subject to candidate/territory caps. It is not an unbounded loop.
- Search itself does not claim land. A safely accepted footprint and its connected ownership corridor can expand the district. Neighbor watershed boundaries, approvals/pause settings, loaded chunks, protected land, foreign storage, reserved lots and entrance/foundation safety remain mandatory. No chunks are force-loaded. Fully protected, occupied or unloaded terrain can still legitimately prevent placement.
- Wider progressive frontier search is for ordinary organic district projects. Bank search retains its existing bounded new/recovery candidate offsets; Banks gain outdoor-fit fallback, not unlimited outward expansion.
- Save format is 44. Test a world copy and keep its pre-upgrade backup; older JARs must not reinterpret a fitted approval.

## Validation

Both loader builds succeeded. The final language-only handbook restoration was packaged with subsequent assemble runs. Current-source and cross-loader identity verification passed, as did exact SHA-256 verification of all 1,875 packaged native assets (750 archived/current full designs and 1,125 fits). All full v12/v13 geometry remains unchanged.

Geometry validation checked every fit for nested-subset alignment, retained building cells/beds, native structural support, clear access and all 45 fitted Bank desk anchors. Core regression coverage includes fallback ordering, wider connected search, neighbor rejection, atomic fit selection, restart persistence and immutable reservations.

The complete `scripts/run-common-tests.sh` suite passed after the handbook and Bank routing assertions were brought up to date, including economy/persistence, territory/growth/scale, architecture, construction safety, handbook, bridge funding, loader version parity and pinned wrapper checks. `git diff --check` passed.

Disposable dedicated-server architecture fixtures passed on Fabric and NeoForge: all five biome styles, current ordinary buildings/Banks, archived Taiga geometry, all three reduced fit levels, progressive construction, rotation/mirroring, partial restart, physical villager doorway/bed/workstation/street travel, soil transitions and durable handover. Fabric's separate terrain/safety/ownership/debug smoke passed, including the new pending-Bank ZIP schema and protected foreign-block negative cases.

The first Fabric architecture smoke failed a physical walking assertion at the unchanged full Taiga v16 Bank: the villager stalled beside seating while crossing toward the doorway. Bank construction and operational checks had passed. A fresh complete run passed without changing geometry, navigation code, timeouts or assertions; NeoForge also passed. This is retained as a nondeterministic native-AI test failure, not concealed as a clean first-run pass. It is not evidence that normal villager navigation is guaranteed never to stall.

Preserved local logs: `build/server-smoke/beta60-fabric-architecture-failed.log`, `beta60-fabric-architecture.log`, `beta60-neoforge-architecture.log`, and `beta60-fabric-terrain-debug.log`. These are disposable fixture logs, not user-world writes.

Handbook review: updated both long architecture/construction chapters and compact lectern pages, including fit order, bounded search, loaded/protected terrain, frozen existing approvals, soil finishing, debug evidence and backup guidance. Restored the existing compact Taiga ridge guidance when its regression caught the omission. Updated the Bank routing wiring test to assert the new full-before-reduced surveyed reservation path, pending-plan retention and absence of world writes. No recipes or economic roles changed.

## Candidate identity and scope

Version: `0.4.0-beta.60`.

Canonical source SHA-256: `ce40c9a7943a436a051184fa9886024900440a496b96fdd9a75262fbe71b1e8b`.

Fabric JAR SHA-256: `b292b2225aa29b11f19e67986300bf355c85160d8189813d1b862e7a1e9bb288`.

NeoForge JAR SHA-256: `9059fd56a8ba3043e55e314101b454282afce0adfffe947a11879c6fd0f8f044`.

Work remains on `codex/biome-architecture-preview`; no main merge, push, PR or user-profile/save installation. Main and origin/main remain `26b049b947bede6169d42afae5c7772209458cca`.
