# Development protection and background recovery validation

Date: 2026-09-10. Minecraft 26.2, Java 25. Unreleased 0.4.0-beta.5, economy format 25.
Base commit: `f8b8435dcbb1acea5f3df7dd2755adc1656a5528`.
This pass is local working-tree work; no commit, GitHub push, release or user-world installation
was performed. Existing structure recipes remain unchanged.

The later [construction loot and occupancy follow-up](2026-09-10-construction-loot-occupancy-validation.md)
supersedes this report's JAR hashes and the two unresolved edge cases listed at the end. This report
remains the historical format-25 validation record.

## Changes under test

- Native dimension-local, inclusive full-height no-build areas, owner/operator removal and
  ordinary-player limits. Actual command parsing exercised on both loaders.
- Successful native player BlockItem placement capture, next-tick state verification, break
  evidence pruning, connected solid clusters, old timber frame recognition, torch/flower exemptions.
- Five-minute loaded obstruction threshold, funded one-shot founding-home replacement, retained
  partial lot and unused escrow accounting; unrelated city expansion is not indefinitely gated.
- Shared project/Bank village eligibility; waiting deliveries; exact mod-owned route cleanup
  that leaves unrelated villager navigation intact.
- Loaded entity membership index, rotating food surveys and partial-chunk census merge.
- Forced CRC32C district full/delta journals, changed-field removals, previous-snapshot replay,
  torn-tail recovery, corruption rejection and distinct checkpoint generations.

## Executed checks

All of the following passed on the final source:

1. `bash scripts/run-common-tests.sh`: complete core/resource/wiring suite, including the new
   `DevelopmentSafetyRegressionTest`. Covers journal replay before checkpoint, backup recovery,
   smaller delta frames, identical-state checkpoint epochs, paid one-shot relocation, owner checks,
   inclusive/reversed/dimension boundaries and preserving unobserved food chunks.
2. Fabric `gradlew --no-daemon build` (2m 17s): complete authored catalog, immutable older recipe,
   Bank, packet and reader checks.
3. NeoForge `gradlew --no-daemon build` (2m 14s): loader compilation/tests, catalog, packet and
   reader checks. The new break observer uses NeoForge 26.2's `BreakBlockEvent`.
4. `bash scripts/smoke-server.sh fabric`: dedicated-server PASS. Isolated world/log directory
   `build/server-smoke/fabric-run.9rp42U`; integration completion at 18:03:16 local time.
5. `bash scripts/smoke-server.sh neoforge`: dedicated-server PASS. Isolated directory
   `build/server-smoke/neoforge-run.V3cBDR`; integration completion at 18:03:41 local time.
6. Both `verify-built-jar.sh` loader checks; explicit ZIP inspection also confirmed the new
   mixin JSON/class, no-build commands and land store are packaged in each runtime JAR.
7. `git diff --check`: no whitespace errors.

Live fixtures additionally covered native placement evidence, actual no-build commands, old raw-log
frame refusal, worker waiting/foreign-route cleanup, representative real-villager walking,
progressive wooded-hillside project/Bank construction, restart, storage protection, concurrent
site pacing, food sources and the existing Banker integration.

The smoke harness stops only its uniquely tagged disposable server after the explicit PASS.
Gradle can consequently log a killed process/daemon failure after success. Windows OSHI/Perflib
system-information warnings and JOML deprecation warnings were present; the opt-in synchronous
integration suite also produces startup tick-delay warnings. These are not measured gameplay TPS.
NeoForge fixture stdout is captured in `build/server-smoke/neoforge.log`; its per-world log records
the overall integration completion. Build/smoke output directories are intentionally untracked.

## Runtime JARs

```text
fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar
SHA-256 9326c939037d9ad5733f71f91f11f211f0f2a24a0afa52ffc234109519ec1c77

neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar
SHA-256 730c90c19860cc2ee2e2bafacf83db12818ed1991983209525166d52d1cef465
```

## Limits and handoff

Back up the entire world before upgrading; format 25 is not safe to downgrade in place. Include
the economy journal and `data/emerald-development-land.journal`, not just the main properties file.
See [development protection](../DEVELOPMENT_PROTECTION.md) for command examples and behavior.

No third-party claim adapter was added because the user does not use a claim mod. Zones protect
against TES development only. Old natural-looking dirt/vegetation, indirect block changes and
non-native placement tools remain imperfectly attributable. Food caches are last-known samples,
not a live unloaded-world census. Extended huge-city TPS, every template's walking paths and
compatibility with arbitrary villager-AI mods remain unverified. Minecraft chunk and economy
writes are still separate durability boundaries. This pass does not claim to resolve unrelated
pending-Bank loot recreation or entity-occupancy edge cases.
