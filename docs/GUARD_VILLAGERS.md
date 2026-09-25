# Optional Guard Villagers integration

Guard Villagers is **optional and not bundled**. TES recognizes the registered entity
`guardvillagers:guard` without importing any Guard Villagers classes, reading its private
data, injecting mixins into it or declaring a required dependency. Installing the mod by
itself does not grant a flat bonus.

## What the player gets

The default is **+2 temporary Safety per eligible guard, capped at +12 per district**.
Three guards supply +6 and six supply +12. Effective Safety (base + modifier) still caps
at 100. This is the score used by village production, prosperity, growth/recovery gates,
security project selection and emergency spending. It does not remove food, housing,
upkeep or physical-construction requirements. Guards are not residents or free housing.

Hover the Town summary for the last observed count and modifier. The handbook's Village
growth chapter explains this integration. Settings exposes:

| Setting | Default | Range |
| --- | --- | --- |
| `compat.guard_villagers.enabled` | true | true/false |
| `compat.guard_villagers.safety_per_guard` | 2 | 0–10 |
| `compat.guard_villagers.maximum_safety_bonus` | 12 | 0–30 |

Apply validates and clears cached observations; the next complete local census recalculates.
Reset includes all three controls. Remote server administrators can edit the world's
config and use `/emerald config reload`; clients cannot change a server's settings.
Absent mods or disabled integration produce no bonus.

## Eligibility, performance and stale information

- The existing loaded-village census observes living adult guards with AI within 48 blocks
  of the saved district center (also limited to 24 blocks vertically). Guards currently
  targeting players, villagers, iron golems or other guards are excluded.
- Each observed guard belongs to its nearest saved district in that dimension, using the
  existing spatial index. Overlapping survey areas do not independently assign it.
- A bounded, read-only local mobility probe rejects guards sealed in tiny cells and guards
  riding other entities. It checks at most 96 candidate cells per considered guard, without
  changing navigation. This is not proof of a route to every house, equipment quality, kills or successful defense.
  The external mod still controls combat, follow/patrol orders, spawning and equipment.
  A named vanilla mob cannot impersonate a registered guard.
- Observation runs on the existing census interval (normally 400 ticks / 20 seconds at
  20 TPS), not every tick. It queries a bounded local entity area, examines at most 64 candidate guards per district,
  and does not copy market snapshots per guard or force-load chunks. Mobility checks stay local.
- Partial/unloaded census coverage is **unknown**, not zero. The last bonus fades linearly
  over seven economic days without another observation. Movement/death is reflected at
  the next complete census, not instantly. UUID-based ownership removes the old district's
  credit when a new district observes the same guard, without refreshing the old district's other guards.
- Observations replace the old modifier instead of incrementing base Safety. Base Safety
  is still subject to ordinary growth and losses, and genuine indirect economic benefits
  are not rolled back if a guard later leaves.
- Guard observations are session-local, not written into economy saves. Restart begins
  at zero until a fresh census; removing Guard Villagers cannot leave permanent cached
  defense. TES does not delete third-party entities or attempt to repair their mod's data.

## Compatibility targets and test caveat

The downloaded Minecraft 26.2 artifacts used by the opt-in real-entity fixtures are:

- [Guard Villagers Fabric/Quilt](https://modrinth.com/mod/guard-villagers-(fabricquilt)):
  `guardvillagers-2.1.3-26.2.jar`.
- [Guard Villagers](https://modrinth.com/mod/guard-villagers):
  `guardvillagers-4.0.3-26.2.0.jar` on NeoForge; [upstream source](https://github.com/seymourimadeit/guardvillagers).

Both register `guardvillagers:guard`. Use the external release matching your Minecraft
version and loader. These checks do not certify arbitrary forks or every modpack.

NeoForge Guard Villagers 4.0.3 logs an **upstream recruitment-advancement data error**:
`guardvillagers:adventure/recruit_guard` references an unknown entity sub-predicate
`minecraft:type`. TES does not patch that file. The test harness normally rejects every
unexpected ERROR; only an explicit `TES_ALLOW_GUARD_ADVANCEMENT_ERROR=1` with an external
test JAR and NeoForge permits this exact known message while retaining it in the log and
reporting it. Every TES integration assertion must still pass.

To exercise the real external entities, set `TES_GUARD_COMPAT_JAR` to the matching
downloaded JAR and run `bash scripts/smoke-server.sh fabric` or `neoforge`. The harness
copies it only into a unique disposable server directory. Unset that variable to test
the absent-mod path. No external mod is added to compilation or normal runtime packaging.

## Local validation — 2026-09-11

The original candidate below was superseded after the cross-feature save audit. See
[edge-case fix validation](reviews/2026-09-11-edge-case-fixes.md) for the corrected build.
Format 29 writes consistent guard-free persisted market shadows. Format-28 recovery only
accepts an exact match to the old bounded guard-bonus formula; unrelated damaged scores
still fail validation. Player balances are not recalculated by that repair.

This is an unreleased beta.5 working-tree candidate, not a published release or certification
of the user's complete modpack. No installed mod or player save was changed.

| Check | Result |
| --- | --- |
| Common regression suite | PASS, 78 PASS result groups |
| Fabric dedicated server with real Guard Villagers 2.1.3 entities | PASS |
| NeoForge dedicated server with real Guard Villagers 4.0.3 entities | PASS with the known upstream advancement error retained |
| Both dedicated servers without Guard Villagers | PASS |
| Both loader builds, reader/settings validation and real menu packet codec | PASS |
| Fabric production GUI fixtures at GUI scales 2 and 4 | PASS; Town tooltips visually inspected, settings toggle/Reset and handbook real-font wrapping checked |
| Both packaged JAR checks; diff whitespace check | PASS |

The external-entity fixtures check default caps, inactive AI, hostile player targets,
removal, nearest-district overlap, disabled integration, unchanged resident count, and
retaining unknown/unloaded observations without loading distant chunks. Core checks cover
repeat scans without base-score stacking, decay, bounds, config defaults, immediate
clearing, restart without cached protection and effective Safety affecting the simulation.

Superseded candidate SHA-256 values (do not install these):

- Fabric: `5c114a4b6444f2bfb0df52e99545eaf6706a9362e2d473ecb6f7653227023a3c`
- NeoForge: `633c037e976d729b250ac3132053c43a97abda615de2cf57fa6f8764e67bc99c`

Install only the matching loader's playable JAR, replacing the old TES JAR. Do not
install both or a sources JAR. Back up the world before testing; use this same TES
build on server and connecting clients. External Guard Villagers installation and
its loader-specific requirements remain separate.
