# Beta81 — captured stalls and integrated shutdown

## Evidence

The user's TES-debug-20261007-182103-7BA0443F capture ran beta78. Project 16 is an
imported `minecraft:village/snowy/houses/snowy_tannery_1`, rotated once, with a frozen
648-operation plan and order cuts `[0,648]`. Cursor 67/68 repeatedly requested
`minecraft:short_grass[snow_layers=0]` at (1956,85,1276/1277). The actual block was air
and native survival was false. Imported cells carry STRUCTURE roles, making this
living decoration a required completion dependency.

The same capture's project 2 warehouse waited at terrain cursor 383/955. An approved
vine-to-air removal position had become one-layer snow. It was not a protected
placement, but the old before-state match did not recognize this natural change.

The pasted log's final stop exception is a chat ArrayList ConcurrentModificationException:
IntegratedServer.stopServer -> teardownPublishedState -> updateCommandsAllowedForOtherPlayers
-> LocalPlayer.refreshChatAbilities -> ChatComponent.setVisibleMessageFilter/refreshTrimmedMessages.
Native 26.2 bytecode confirms that this server path directly invokes a client UI operation.

Earlier repeated entity-section ConcurrentModificationExceptions have a different
stack involving PersistentEntitySectionManager and C2ME TheChunkSystem callbacks.
There are no TES frames identifying their cause. TES loaded/unloaded listeners were
reviewed; no blanket suppression or speculative third-party entity-map mutation was added.
Those errors remain a separate modpack issue needing isolation.

## Scope

Runtime best-effort classification leaves canonical roles, operation ordering, persisted
cuts, totals, bounds and plan hashes intact. It covers nonproductive, nonemissive grass,
ferns and tagged vegetation flowers, excluding safety fixtures and forbidden vegetation.
Supported plants are still attempted. Unsupported/unsafe plants skip safely; later loss
does not revoke completion. Structural and productive cells retain guarded authority.

Natural plant-to-snow/soft-plant replay only applies to an already approved removal cell.
Existing position-specific player-placement, block entity, fluid and claim guards remain.
No arbitrary new clearing area or permission to overwrite changed masonry/soil was added.

The client-only LocalPlayer mixin defers off-thread refreshes using the client executor,
never blocks a server waiting for client shutdown, and checks player identity before
dispatch. Vanilla client-thread behavior is unchanged. Dedicated servers do not load it.

## Handbook accuracy review

Update compact construction safety within its eight-line reader budget, plus guided
terrain explanations of flora, unchanged older plans and approved natural regrowth.
Building projects already routes these sections; no chapter rerouting is needed.
Review EmeraldHandbook, HandbookChapters, HandbookRecipes/HandbookScreen and all four
server Survival recipe definitions. Recipes and creative-only spawn eggs are unchanged.
Native reader bounds and handbook regressions must pass before release.

## Validation

Fixtures use disposable server worlds and a copied test-owned integrated world, never
the user's profile, capture world or personal saves. The actual modpack was not modified.

The native tannery fixture uses the actual imported plan and captured 67/648 cursor,
with deliberate non-survivable plant substrates. It verifies normal and instant modes,
unchanged required geometry and frozen plan identity, saved completion, harmless later
flora loss, and durable demotion after a required wall is removed. Read-only durability
assertions use EconomyState.load rather than starting competing service writers against
the same test directory; a real restart abandons the previous fixture service.

The terrain replay checks partial cursor persistence, foreign masonry/soil/crop/fluid
rejection, claim-vetoed snow regrowth, resumed approved clearing and saved completion.
The existing native ConstructionSafetySelfTest still covers locked empty storage,
no drops, support repairs, player/villager/animal occupancy and neighboring work.

The integrated client fixture holds the client thread while the server queues 256 chat
refreshes, proving that the handoff is nonblocking, then verifies delivery on the client
thread. It closes the actual test world, discards a stale callback and repeats after
reopening. This exercises the native chat path and real integrated-server shutdown,
without C2ME or the user's broader modpack installed in the test launcher.

## Final execution results — October 7, 2026

- PASS all shared regressions, handbook resource/mechanics checks, flora/cosmetic and
  async shutdown wiring, loader parity and pinned wrapper checksums
  (`build/beta81-common.log`).
- PASS Fabric and NeoForge captured 67/648 tannery recovery in normal and instant modes,
  unsupported flora, unchanged saved geometry/hash, read-only durability assertions,
  completed flora-loss audit and durable required-wall damage demotion. Both also pass
  captured natural vine-to-snow clearing, partial cursor/reload, foreign-edit and claim
  safety, plus ConstructionSafetySelfTest
  (`build/beta81-fabric-flora.log`, `build/beta81-neoforge-flora.log`).
- PASS fresh native snowy tannery construction from an empty site on both loaders,
  rotated three times, 648 operations and 75 required foundation/access operations,
  native survival, one-shot storage supplies and saved completion
  (`build/beta81-fabric-tannery-fresh.log`, `build/beta81-neoforge-tannery-fresh.log`).
- PASS real integrated world opening/closing twice per loader, each with 256 forced
  server-thread chat refreshes and a stale disconnected-player callback. No reported
  chat ConcurrentModificationException or exception stopping the integrated server
  (`build/beta81-client-shutdown.log`, `build/beta81-neoforge-client-shutdown.log`).
- PASS compact construction page native book bounds, guided real-font wrapping, every
  chapter end, 80/120 text sizing, topic search and process-restarted preferences on
  Fabric and NeoForge (`build/beta81-client-summary.log`,
  `build/beta81-neoforge-client-summary.log`). Initial font checks caught an extra
  wrapped compact line; the final shorter wording fits without increasing reader limits.
- PASS both loader assemblies and current-source parity
  (`build/beta81-fabric-assemble.log`, `build/beta81-neoforge-assemble.log`,
  `build/beta81-candidate.log`). Both architecture JAR audits verify all 12,375 native
  assets and production adapters against immutable SHA-256 catalogs
  (`build/beta81-fabric-assets.log`, `build/beta81-neoforge-assets.log`).
- PASS staged binary/source pair copy equality, loader metadata, required client mixin,
  packaged contents and generated SHA256SUMS (`build/beta81-release-verification.log`,
  `build/beta81-fabric-jar.log`, `build/beta81-neoforge-jar.log`).
- PASS `git diff --check`; no installed profile or live save was changed.

Final source SHA-256:
`620a267a7f0a8b3114dde1a9da229194ac7ff839cd35955ffe69542954af50b9`.

Release binaries at `build/release-beta81`:

- Fabric SHA-256: `08f0cae7b736ab35db0c2dbd8879fc292ca1491f2481f820e7e703536d5cd4f0`.
- NeoForge SHA-256: `a3a0e9b85bda2751aafd398847667eb611e2bb4f82fc27873ae7d07cd45d52df`.

These are opt-in development launches. Dedicated smoke scripts deliberately terminate
their uniquely marked Java process tree after the PASS marker, so their trailing Gradle
termination/daemon-disappeared output is not an assertion failure. Native client logs
retain this computer's pre-existing OSHI/performance-counter warnings and development
account certificate HTTP 401 errors; no warnings/errors were suppressed. The user's
broader modpack entity-section exception has not been reproduced or fixed here.
