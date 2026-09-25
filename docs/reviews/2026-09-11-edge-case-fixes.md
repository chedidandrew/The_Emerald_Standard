# Cross-feature edge-case fixes — 2026-09-11

Unreleased 0.4.0-beta.5 working-tree candidate. These replace the earlier beta.5 guard
candidate; identical filenames do not imply identical contents. No installed mod JAR,
live world, Windows registry setting, Git commit or remote repository was changed.

## Corrected behavior

1. **Guard bonus + player-caused villager casualty + save/reload.** Previously the market
   counterfactual saved guard-adjusted cached scores but omitted the temporary guard
   observation. Reload rejected that mismatch as a stale market shadow, including both
   primary and backup after two checkpoints. Format 29 serializes a private guard-free
   shadow copy with matching recalculated scores. The live counterfactual still follows
   observed guard changes. Clearing the integration also clears its shadow modifier.
   Format-28 repair accepts only an exact match across every cached score to one of the
   old bounded bonus/decay combinations; other corruption remains a validation failure.
   Player balances, donations and physical buildings are not reset by this repair.
2. **Partial food scans reviving old farms.** Every completed chunk now carries its own
   persisted economic-day timestamp. Unobserved sources receive the existing one-day
   grace, then lose freshness over seven days; seeing an unrelated empty chunk cannot
   renew them. Expired samples are pruned on merge. Older scan results cannot replace newer
   samples, and old two-value entries retain their original aggregate observation date.
   Direct whole-area observations remain supported and replace their prior chunk cache.
3. **Guard double credit and tiny cages.** Session-local UUID ownership removes a guard
   from its old district when another district observes it, without renewing the old
   district's remaining stale observations. Ownership records expire too. Passengers and
   guards with no locally reachable patrol space are excluded. Mobility probing is read-only,
   limited to 96 candidate cells each and 64 candidate guards per district census, including
   failed probes. It neither changes third-party navigation nor force-loads terrain.
4. **Pause/resume crew duplication.** Paused, disabled and repair-waiting projects stay in
   the known-site census. Existing crew identities and fence receipts survive; no new
   fences or workers are added, and work animations stop. An unloaded worker retains its
   reserved slot. A surplus returning unregistered worker leaves instead of increasing
   the crew beyond its site quota. Completion retains the existing safe restoration rules.
5. **Recurring presentation cost.** The four-second presentation census uses cached
   unfinished-project metadata, not village/market/resident/fund deep copies or repeated
   historical-project traversal. Lightweight identity/count checks and mutation/day
   invalidation keep it current after admission, rollback, pause and completion. At most
   16 loaded sites receive presentation work per pass, rotating fairly. All known sites
   retain lifecycle visibility; this scheduling limit does not remove buildings or cap
   city size. The per-tick food scan existence check also avoids full snapshots.

## Verification

| Check | Result |
| --- | --- |
| Common regression suite | PASS — 78 result groups, including existing money/inventory and large-world scale guards |
| Guarded casualty followed by two checkpoints/restart | PASS |
| Original disposable failing audit save loaded with the fix | PASS |
| Legacy decayed guard shadow repair, unchanged account balance, unrelated corruption rejection | PASS |
| Partial/empty/late food scans, independent timestamps, restart and legacy sample age | PASS |
| UUID transfer, non-renewal of old guards, duplicate IDs and expiry | PASS |
| 10,000 historical-project presentation fixture, pause/completion/cache/admission | PASS |
| Fabric and NeoForge assemble, reader/settings, real menu packet codec | PASS |
| Both dedicated servers without Guard Villagers | PASS |
| Fabric with real Guard Villagers 2.1.3 / NeoForge with real Guard Villagers 4.0.3 | PASS; NeoForge upstream warning noted below |
| Real placed fences, NBT/ledger reload, walking/animation, pause/resume, unloaded slot, surplus worker | PASS on both loaders |
| Client guard GUI/settings and handbook real-font wrapping at GUI scales 2/4 and reader scales 80/120 | Assertions PASS; client log gate exception noted below |
| Packaged playable/source JAR checks and whitespace | PASS |

The latest client run completed normally and produced all four expected screenshots.
The strict client log checker returned failure because OSHI logged
`HkeyPerformanceDataUtil: Unable to locate English counter names in registry Perflib 009`.
This was the only ERROR; it did not prevent the production GUI assertions or normal client
shutdown. The log was not filtered to claim a clean check, and no Windows settings were changed.
The high-GUI-scale Town tooltip screenshot was visually inspected.

NeoForge Guard Villagers 4.0.3 still logs its existing upstream recruitment-advancement
error (`minecraft:type` entity sub-predicate). The dedicated harness required the
existing explicit, exact-message opt-in and retained/reported the error. TES does not
patch that external mod. See [Guard Villagers compatibility](../GUARD_VILLAGERS.md).

Logs: `build/guard-compat/edge-common.log`, `edge-{fabric,neoforge}-build.log`,
`edge-{fabric,neoforge}-{present,absent}.log`, and `edge-client.log`.
Screenshots: `build/client-smoke/guard-edge-fixes/screenshots/tes-reader-ci/`.

## Test artifacts and upgrade caution

**Back up the entire world before testing. Format 29 saves cannot be opened by older builds.**
Install only the matching loader's playable JAR, replacing the old TES JAR; do not install
a sources JAR or both loaders' JARs. Use matching builds on server and clients.

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`
  SHA-256: `1938ab2005134595efc8ca971989b2f55e20894ccd02bf3d035546dcb74beb6f`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`
  SHA-256: `8508ebfacd18397c586153ed60494b3fa198ad2831d30f1e9cd003f7ab02f7c1`

These tests do not certify the user's entire modpack or indefinitely lag-free growth.
Vanilla entity cost, chunk storage, economy history and total world size still grow.
The guard probe establishes local mobility, not combat effectiveness or access to every
house. Chunk/entity/crew ledgers retain Minecraft's ordinary cross-file save durability:
an independently lost entity can still leave a reserved cosmetic crew slot until completion.
No automatic respawn is guessed for that ambiguous case, because that could duplicate a
worker whose chunk has not loaded.
