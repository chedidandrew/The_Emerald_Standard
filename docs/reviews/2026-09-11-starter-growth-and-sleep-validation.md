# Starter growth and sleeping validation — 2026-09-11

## Behavior

- Fix the default day-zero incident date being mistaken for a real incident. A genuine day-zero raid still receives its cooldown.
- Healthy starter villages use two bounded economic work shifts and limited local construction supplies. Their first projects become ready much sooner, with tapering through eight lifetime approvals and across larger cities. The last supported project keeps its tapered labor until completion.
- Local supplies are separate from player balances, inventory, donation receipts and Fund principal. Full project costs and existing refunds are unchanged. No save-format migration or ledger reset is required.
- New ordinary development admission waits at two unfinished non-repair-required sites per district. Existing projects are not removed; debug-forced development is unchanged. Early arrivals remain capped at one per day and require housing/food/safety and the existing safe spawn path.
- The economic clock already includes skipped nights. Recently active physical sites now get session-only extra work opportunities at wake-up: eight extra cells per site, 32 shared per pulse, round-robin, and a soft 3 ms deadline. No new construction pulses or force-loaded chunks. Credits are capped to 12,000 skipped ticks and bounded to 2,048 tracked sites.
- Credits are opportunities, not guaranteed placements. Blocked/unused grants are discarded. Credit expires after inactivity and is not replayed after restart. Economic and physical saved progress are unchanged.

## Checks run

- Full loader-neutral regression suite passed, including economy/market calibration, persistence, Fund/payment conservation, village lifecycle, growth/expansion, protection, settings and handbook resources.
- New `VillageGrowthPacingRegressionTest`: two economically completed projects on the first day in healthy 4/8/18-resident fixtures on ordinary and Peaceful difficulty; no third physical admission; no subsidy accumulation behind a full backlog; later admission after completion; taper and eighth-project completion; food/safety/upkeep/pause/founding gates; real incident cooldown.
- Clock coverage: a 13,000-to-24,000 bedtime jump completes exactly one economic day, with no wall/game/daylight double count. Physical credit excludes the ordinary elapsed tick. Tests cover duplicate managers/claims, two daylight changes in one tick, huge forward changes, backwards time, rate changes, disabling/reset, no inheritance by new sites, 100-site fairness and the 2,048-site memory bound. The final same-tick guard was recompiled and its focused regression passed after the full-suite run.
- Fabric and NeoForge dedicated-server smoke tests passed in disposable worlds. Both placed nine actual Bank cells in a waking pulse (one ordinary plus eight extra) through the production placement path. Existing loot-receipt, restart, protected-storage, terrain, player/villager/animal occupancy and walking checks passed.
- The wooded hillside Bank fixture required 1,204 planned cells. Tier-one authored solid counts sampled across 12 identities per type: Cottage 573–843; Warehouse 721–843; Mine Entrance 498–678; Granary 770–883; Guard Post 747–1,136. At 2 blocks/sec these solids represent about 0.21–0.47 Minecraft days per site, before terrain and approach work. This supports the target cadence, but is not a guarantee for every terrain or template.
- Fabric handbook-only client smoke passed all chapter-end/search/wrapping checks at 80/120 reader size and GUI scales 2/4. Longer strings also exposed a recursive regex limit in the resource regression parser; possessive quoted-string matching fixed it without changing production rendering.
- Both loader assemblies, packaged JAR verification and `git diff --check` passed. Windows OSHI/performance-counter warnings in the isolated launches were non-fatal.

No prolonged full-modpack village-growth soak or real multiplayer sleeping session was run. Night behavior was exercised with the same clock jump used by sleep and with actual Bank block placement. No personal world or installed mod JAR was changed, and nothing was committed or pushed.

## Playable JARs

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar` — SHA-256 `e6d3f8fdc17a54beaf079ce8dc115a0b9791e2f3fa98c20c406b88756aed65f3`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar` — SHA-256 `8e40088653932162e76c8a1908f11d7e3017160dc8ad76b345bbc38ffa94b6a6`

Install only the JAR for the matching loader, replacing its older copy. Test on a backup: newly generated buildings remain in the world.
