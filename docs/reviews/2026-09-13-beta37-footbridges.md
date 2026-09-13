# Beta.37 village footbridge validation

## Scope

Implements Andrew's accepted bridge plan: compare crossings with land detours, variable-length village-themed three-wide spans, outside rails, inward-facing matching street lamps, safe stair approaches, natural footing, a boat opening, shared crossings, Infrastructure costs, persistent progress and bounded loaded-only work. Ocean/suspension bridges remain outside this first version.

The source candidate also includes the previously completed beta.32–36 newsroom, immersion, development scheduler, construction-fence texture and roadside bench changes. Their individual review records remain in this directory. The two remote beta.31 source-review documentation commits were fast-forwarded and preserved before this candidate was committed.

## Architecture and guarantees

- A bridge is an A* route edge, not a separate road to an arbitrary shore. Nothing is reserved or funded until a complete onward route is frozen.
- New candidates along the same river reach are sampled rather than repeatedly surveying adjacent copies. Search remains capped at 24 crossing attempts and 8,192 nodes; snapshots and work are sliced.
- All selected crossings reserve together. Unfunded spans can queue while one or two crews work. Separate channels around a sufficiently wide island are not mistaken for parallel crossings.
- Frozen plans contain exact block states, materials, footing/headroom assertions and progress. Persistent one-shot economy receipts prevent double payment after restart or mode changes.
- Saved plans checkpoint before journaled funding. That one-time saved-data flush uses Minecraft's existing save path; individual saves and block updates are not preemptible by the soft work deadline.
- Required piers retain water in their waterlogged support cells. The central opening has no piers or low arch details. Work does not drain water, overwrite storage or load chunks.
- Both natural district ownership and placement protections are rechecked. Shared work retains the original project identity. Protected or occupied cells defer work; changed cells are not overwritten.
- Completed bridges are not regenerated or harvested-material farms. Intact crossings remain usable with bridge planning disabled.

## Tests observed

- Full common regression suite: PASS (`build/beta37-common-tests.log`), including size-based cost, insufficient inputs, one-shot debit, forced-to-normal transition, copying, journal restart, neighboring ownership and handbook coverage.
- Full Fabric dedicated-server suite: PASS (`build/beta37-fabric-full-server.log`).
- Full NeoForge dedicated-server suite: PASS (`build/beta37-neoforge-full-server.log`).
- Final focused native bridge suites, after survey deduplication and saved-ledger checks: PASS on both loaders (`build/beta37-fabric-bridges-final.log`, `build/beta37-neoforge-bridges-final.log`).
- 34 design fixtures cover all four directions and water lengths 2, 3, 7, 8, 13, 24, 47 and 48, plus masonry/desert and unequal banks. Verify three-wide clear passage, uphill stair state, lanterns and the navigation opening.
- Native placement covers occupants, saved-ledger codec reload, two-write limits, debug-to-normal transition, one payment, full handover and preserved player edits.
- Edge cases cover a two-block bank rise, rejected cliffs/deep required piers/lava/ice, preserved underwater storage, protected shores, atomic rejection and a successful one-crew two-crossing island queue.
- Integration tests complete the onward road, reload both saved ledgers, reuse a bridge in reverse with bridge planning disabled, choose a short land detour, and reject a protected destination without paying for a bridge to nowhere.
- The final simple-river integration fixture completes in 204 Fabric / 203 NeoForge work calls at its test allowance. This is a bounded fixture observation, not a server throughput benchmark or a promise of wall-clock completion in a busy village.
- A native villager path explicitly crosses the middle deck, followed by real AI/navigation/physics traversal of the stairs and deck: 158 steps and roughly 34.36 blocks displacement. No midway teleport.
- Fabric and NeoForge client handbook checks: PASS (`build/beta37-fabric-client.log`, `build/beta37-neoforge-client.log`). All 63 compact pages fit; all 16 guided chapters/69 sections, topic search and chapter ends pass at 80/120% reader text and GUI scales 2/4.
- Settings persistence/reset validation initially caught the stale 34-key assertion; it now asserts all 38 keys and the four bridge defaults. Every setting's valid edits, invalid mixed drafts, disk reload, reset and stale-world rejection are exercised. Focused reader settings check: PASS.
- Initial checks caught and corrected compact-page overflow and an unavailable item-atlas lantern icon. The compact bridge page now uses the existing oak-plank block sprite.
- Existing server smoke scripts deliberately stop their isolated Java process after the success marker. Post-marker exit 143/daemon termination messages are harness cleanup, not bridge failures. The synchronous exhaustive fixture startup is not a live MSPT benchmark.
- Native tests ran only in disposable smoke worlds, not Andrew's installed profile or saves.

## Handbook review

Updated the guided Building projects chapter with a substantial footbridge section and corrected the earlier water-routing advice. Appended compact Footbridges and Bridge work pages without moving existing links. Settings explain bounds, saved-plan behavior and queued crews. Funding examples, natural foundations, loading, player edits, boat clearance, no automatic rebuilding and `/emerald debug` discovery agree with the implementation. No new item or recipe is introduced.

## Candidate packaging

- Full Fabric build: PASS, 4m 30s (`build/beta37-fabric-build-final.log`).
- Full NeoForge build: PASS, 4m 10s (`build/beta37-neoforge-build-final.log`).
- Candidate verifier: PASS current-source fingerprint, beta.37 version, required bridge runtime and cross-loader parity.
- Whitespace/patch check: PASS. Final source changes after the native bridge suites were EOF normalization and the settings test's explicit 38-key/default assertions; bridge runtime behavior was unchanged.
- Source SHA-256: `053f08ff8fff23ff17b57bbb56f1ebb087d3b65689bfccd5de08acbca89be5ef`.
- Fabric JAR SHA-256: `3d2c558f6723ea381fddd743795bfb64bb6bd4b885f12276c428f4125f83508e`.
- NeoForge JAR SHA-256: `94f799527e13d1b3befa6d22a0800012e56fce0234370d06377b033875febc91`.

Playable binaries are `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.37.jar` and `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.37.jar`. Earlier candidates remain untouched. These are locally verified development artifacts, not a certification of a later GitHub Actions run.

## Remaining validation limits

Native collision, material states, navigation, persistence and control-flow tests pass. This is not a human architectural/shader review, a physical boat-driving playthrough, a power-loss filesystem fault test, or a stress benchmark in the user's full modpack. The conservative first version defers cramped banks, ice, waterfalls, overly deep required supports and large oceans. Completed player-damaged bridges need manual repair; automatic replacement is intentionally not implemented.
