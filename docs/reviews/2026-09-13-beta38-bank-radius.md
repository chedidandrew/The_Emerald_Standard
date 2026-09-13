# Beta.38 Bank activation radius

## Requested behavior and implementation

Use the existing Village: development radius setting to start automatic Bank construction, not only to advance a previously reserved Bank.

- First census now finds natural village structure references in loaded chunks inside the configured horizontal circle. Standing within Minecraft's height-sensitive POI boundary is no longer required.
- Original natural centers and identities are retained. Simultaneous discovery resolves untagged residents to their own nearby natural start, rather than assigning both villages' residents to the first center visited.
- Bank surveys enumerate eligible known settlements rather than choosing only the nearest settlement to each player. Four rotating settlement candidates per Bank scan cap expensive plot work.
- Initial planning, pending physical work, perimeter preparation and explicit fallback recovery use the same setting and associated village center. Unassociated legacy Banks use their saved anchor.
- Default radius is unchanged at 256 blocks, with existing limits 48–512. Distance ignores height and includes the boundary. It is a circle, not a square.
- Loaded chunk metadata is read without chunk generation/tickets. Overlapping player searches share chunk probes; at the maximum setting a player contributes at most 65 x 65 chunk coordinates per periodic discovery pass, not per tick/block.
- Census availability, safe plots, player property/occupancy protection, saved plans and unique Bank ownership remain required. Returning to range does not promise an instantaneous retry.
- The separate 192-block search for a previously recorded Banker entity remains unchanged; it no longer doubles as a construction activation setting.

## Verification observed

- Full common regression suite: PASS, including handbook guidance, development-radius config bounds, Bank placement policy, persistence and forced-development budgets.
- Focused native Fabric server suite: PASS (`build/beta38-fabric-bank-radius.log`).
- Focused native NeoForge server suite: PASS (`build/beta38-neoforge-bank-radius.log`).
- Native cases: first census at 224/256 blocks outside POIs; stable natural centers; separate neighboring residents; 128/256/512 setting changes; exact boundary and one block outside; diagonals; altitude; overlapping observers; multiple villages; no players; legacy anchor; no forced chunk loading.
- Real progressive Bank construction, saved/reloaded plans, concurrent sites, debug mode switching, storage and occupancy safety: PASS on both loaders. The fence fixture now supplies a nearby observer because production perimeter preparation correctly uses the same activation gate.
- Full Fabric build: PASS (4m 30s), including catalog/Bank geometry, packet, model and reader/settings checks.
- Full NeoForge build: PASS (4m 15s).
- Packaged candidate verifier: PASS current-source identity, beta.38 version, required classes and cross-loader parity.
- Source SHA-256: `92acf8f8f7980e07924f09476cd81e27b54a7d0b93e4b941391689bbc3f8b8fc`.
- Fabric JAR SHA-256: `bd9b305ebad28d204cb7522443e4de418e9dcec9c1da862c6318538da8fe961f`.
- NeoForge JAR SHA-256: `40f0acf86b548b5dc4d42e8870811b532118c7b00686b547a3ba7431a7aef6ca`.

## Broader-suite limitation

The full native suite did **not** pass. Both loaders reached an existing cottage-navigation fixture and reported: `No accessible standing cell beside starter cottage workstation at BlockPos{x=8, y=272, z=6}`, in `VillageWalkingSelfTest.visit` via `VillageExpansionSelfTest.verifyFirstDistrict`. The failing full NeoForge log is retained at `build/beta38-neoforge-full-server.log`. Cottage geometry/navigation was not changed by this task, but this result is not claimed to be a proven pre-existing failure without a same-fixture baseline. It remains a separate investigation; the focused Bank suites are not presented as full-suite certification.

The first broad Fabric attempt also exposed the old no-observer fence fixture. That fixture setup was corrected, and real Bank construction now passes in the focused suites.

## Handbook review

Updated the guided Bank access chapter and compact Bank access page with the shared setting, original village center, horizontal distance, periodic discovery and loaded-terrain requirements. The setting tooltip explicitly includes automatic Bank construction and the same-center continuation rule. Regression coverage checks both handbook formats and rejects the obsolete fixed 192-block advice. No recipes or items changed.

No installed modpack/profile or user world was modified. This remains an unreleased development candidate; the full modpack gameplay/performance matrix is not certified.
