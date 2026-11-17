# Beta 62: construction fence connections

## Diagnosis and implementation

Construction writes deliberately defer native neighbor-shape updates while multi-block structures are incomplete. The ordinary final pass covered plain glass panes and iron bars only, and only STRUCTURE-role cells. It omitted fences, walls, gates, stained panes and supplied COSMETIC yard fences. Banks used deferred-shape writes without an equivalent pre-handover connection pass. Block identity was sufficient for completion, allowing disconnected fence states to be commissioned.

The shared normalizer now recognizes native FenceBlock, FenceGateBlock, WallBlock and IronBarsBlock subclasses and applies Block.updateFromNeighbourShapes to the surviving current state. It never writes new blocks or forces connection flags. Both ordinary and Bank handover wait when an existing connector cannot safely settle. Ordinary construction includes supplied optional decorations but does not adopt unowned optional fences. Completed-prefix audits and approved-Bank maintenance refresh surviving matching block types using the original frozen layout; no missing/replaced cells are rebuilt. Bank maintenance resolves the saved style and exact approved version, including reduced-yard versions. Gallery normalization uses the same connector family coverage.

The expanded approved-Bank fixture caught a second issue: a stone-brick wall under another wall retained LOW arms while its final upper neighbor required TALL arms. Live connection passes now process highest cells first, resolving the shape above before each lower wall. The failing logs are retained as beta62-fabric-approved-first-failed.log and beta62-fabric-wall-order-failed.log. The resulting top-down pass passed the same fixtures without changing their designs or weakening the assertions.

## Safety and compatibility

- Native compatibility rules preserve real gaps, wood/nether-fence separation and gate-axis rules. They do not add rails through empty blocks.
- Current states are normalized rather than reset: waterlogging, gate facing/open/powered state and other unrelated properties remain intact.
- Every production connection write retains loaded-column, shared protection and living-occupant collision checks. No chunk is force-loaded and no player, villager or animal is moved by the shape refresh.
- During construction, optional yard connectors require supplied ownership. After irreversible handover, receipts are released; maintenance uses the persisted completed template prefix, matching block type and recorded player-placement protection. Older unrecorded same-type player edits remain inherently ambiguous, just as with existing pane maintenance.
- Missing/replaced connectors, inventories, original architecture coordinates, construction funding and frozen plans remain unchanged. No new loot, recipes or save-format changes are introduced. Bridge placement already uses native UPDATE_ALL and is unchanged.
- Old unfinished approved/imported plans gain connection finishing in either normal or forced mode without rerolling their geometry. Existing completed ordinary connectors refresh when their nearby loaded audit runs with normal development active (forced development off; staggered, not immediately for every building). Forced mode deliberately skips the existing completed-project audit. Completed approved Banks use their normal active maintenance; historical pre-v13 Bank pane maintenance remains unchanged.

## Coverage

ConstructionConnectionsSelfTest exercises 13 native fence families in four horizontal directions; gate-axis and in-wall behavior; plain/colored panes, bars and walls; waterlogging; native gaps/incompatibilities; idempotence; protected and recorded same-type player fences; removed/replaced blocks; player collision and safe retry; no force-loading; stacked wall ordering; supplied versus unowned optional yard cells; completed-yard receipt release; and real Bank commission deferral/retry.

VanillaConstructionSelfTest checks finalized native connection states for all 166 imported layouts plus twenty raised-house rotation fixtures. BankConstructionSelfTest checks the full progressive hillside Bank. ApprovedArchitectureRuntimeSelfTest checks ordinary buildings in all five styles, saved revision 12, all three reduced-yard fits, current Banks, reduced-yard Banks and preserved v13-v15 Banks. It also deliberately disconnects a completed Bank connector after reload, checks protected deferral, repairs it through production maintenance, and confirms removed/replaced blocks stay unchanged. Existing storage/occupant/terrain/repair tests remain enabled.

## Handbook review

The long projects chapter explains pre-opening connection finishing, native gap rules, loaded/protected/occupied waits and non-regenerating completed maintenance. The compact planning/building page adds final fence joins, open gaps and edit protection. HandbookMechanicsRegressionTest requires both explanations. Handbook chapter routing and lectern localization already include these pages, so no artificial chapter or recipe changes were needed. All recipes and previously documented loot/storage policies remain unchanged.

## Validation results and deliverables

- Full loader-neutral common regression suite passed, including both handbook forms and loader parity.
- Fabric dedicated-server connection/native construction/safety smoke passed: beta62-fabric-connections.log.
- NeoForge dedicated-server connection/native construction/safety smoke passed: beta62-neoforge-connections.log.
- Both dedicated-server approved architecture suites passed: beta62-fabric-approved.log and beta62-neoforge-approved.log. The twenty approved building/Bank samples include reload maintenance, reduced yards and legacy versions; the suite also exercises completion/support/terrain recovery.
- Full Fabric build passed in 6m 18s; full NeoForge build passed in 5m 1s. Their native-source, old/new architecture, supported attachments, roof/interior, model, packet and client-setting checks passed.
- Both binary JARs match current canonical source identity and all 1,875 exact architecture payload hashes (750 archived/current full designs plus 1,125 reduced-yard fits). Packaged JAR verification passed for both loaders; no approved geometry assets were remodeled by beta 62.
- Source SHA-256: 8d674cfe8c901b820ec4df7d49f2c299021dce105c2b530b880fa10fdbe92aa4.
- Fabric binary SHA-256: 54b16085e27dc2d1fa238572a68b1734351bef7e253b4e5680109f26ebb5ed28.
- NeoForge binary SHA-256: 272c95f03c425bc3606d5b54f939109a58980175ab14fc8d5bca736465d2742b.

Release binaries, source JARs and SHA256SUMS are staged under build/release-beta62/fabric and build/release-beta62/neoforge. No Minecraft profile was modified, and no commit, push or merge was performed. The Windows OSHI/Perflib diagnostic is an existing environment warning. The smoke harness intentionally terminates its uniquely marked test server after the integration pass; the resulting Gradle daemon-disappeared footer is harness shutdown, not an assertion failure. Failed pre-fix wall-order logs remain preserved.
