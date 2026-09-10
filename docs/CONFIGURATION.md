# World configuration

The Emerald Standard creates `the_emerald_standard-config.properties` in each world's `data` directory on the first server start. Configuration is world-local; changing one world does not change another.

Edit the file while the server is stopped, or edit it and run `/emerald config reload`. A reload validates the complete file before applying anything. If a value is malformed, outside its supported range, or uses an unknown key, the reload is rejected and every previously active setting remains in effect. The error reports the file path and offending key. This strict behavior prevents a misspelled safety or performance setting from being silently ignored.

## Settings

In beta.4, **Handbook > Settings** edits these same 27 keys in an open owning single-player
world. Fabric's optional Mod Menu Configure action and NeoForge's config action open the same
screen. Apply validates the whole draft before atomic replacement; Done discards unapplied
world edits. Remote clients and title-screen sessions cannot edit world files. Reader text size
is a separate client-local preference and saves immediately. See [reader/config validation](reviews/2026-09-09-beta4-final-validation.md).

| Key | Default | Accepted values | Effect |
| --- | ---: | --- | --- |
| `village_banks.enabled` | `true` | `true`, `false` | Enables discovery-based Village Bank generation and Banker maintenance. Existing player accounts remain available. |
| `village_banks.scan_interval_ticks` | `200` | `20`–`12000` | Delay between discovery scans and nearby Banker-only Bank recovery checks. |
| `village_banks.region_size` | `256` | `128`–`2048` | Legacy spatial-key size. Existing persisted bank identities remain authoritative. |
| `banker.restriction_radius` | `5` | `2`–`32` | Home radius assigned to managed Banker villagers. |
| `transactions.cooldown_ticks` | `5` | `0`–`200` | Server-side delay between accepted dashboard actions from one player. |
| `onboarding.join_hint_enabled` | `true` | `true`, `false` | Enables the one-time per-player-per-world Starter Handbook and discovery message. The book is placed in inventory but never auto-opened. |
| `market.events_enabled` | `true` | `true`, `false` | Enables future rare market events and their asset or commodity shocks. Disabling it does not erase historical news. |
| `economic_clock.offline_progression_enabled` | `true` | `true`, `false` | Allows trusted wall-clock time to advance the economy while the world is closed. Game-time progression remains active when disabled. |
| `economic_clock.max_offline_days` | `25000` | `1`–`25000` | Maximum wall-clock economic days credited from one observed gap. Lower values provide stronger clock-jump protection. |
| `village_prosperity.simulation_enabled` | `true` | `true`, `false` | Advances abstract settlement economies. |
| `village_prosperity.visual_progression_enabled` | `true` | `true`, `false` | Allows queued structures and settlers to materialize in loaded chunks. |
| `village_prosperity.market_integration_enabled` | `true` | `true`, `false` | Allows eligible settlement fundamentals to influence market sectors. |
| `village_prosperity.automatic_recovery_enabled` | `true` | `true`, `false` | Allows eligible non-player-caused extinction to recover after its cooldown. |
| `village_prosperity.scan_interval_ticks` | `400` | `40`–`24000` | Delay between loaded-player settlement census scans. |
| `village_prosperity.development_radius` | `256` | `48`–`512` | Horizontal X/Z distance from a player within which a known village may be considered for physical development. |
| `village_prosperity.construction_interval_ticks` | `10` | Fixed `10` | Compatibility key; normalized to one pass per half-second at 20 TPS. |
| `village_prosperity.construction_blocks_per_tick` | `1` | Fixed `1` | One block operation **per site**, including new-site clearance, not a global allowance. |
| `village_prosperity.settler_spawn_interval_ticks` | `600` | `200`–`24000` | Delay between physical settler placement attempts. |
| `village_prosperity.donations_enabled` | `true` | `true`, `false` | Enables the Prosperity Fund as a whole. The fund also requires settlement simulation. |
| `village_prosperity.endowments_enabled` | `true` | `true`, `false` | Allows new protected-principal Endowment contributions. |
| `village_prosperity.project_sponsorship_enabled` | `true` | `true`, `false` | Allows contributions tied to the active economic project. |
| `village_prosperity.targeted_donations_enabled` | `true` | `true`, `false` | Allows a Direct Grant or Endowment to select a purpose other than General. |
| `village_prosperity.donor_recognition_enabled` | `true` | `true`, `false` | Shows non-financial donor titles. It does not alter persisted contributions. |
| `village_prosperity.fast_track_capital_enabled` | `true` | `true`, `false` | Lets player-origin liquid capital atomically cover one valid project's exact input gap and remaining labor outside the routine spending cap. Passive Endowment payout and emergency reserves remain rate-limited. |
| `village_prosperity.endowment_annual_payout_bps` | `400` | `0`–`10000` | Annual Endowment payout in basis points; `400` is 4 percent. Principal remains protected. |
| `village_prosperity.minimum_emergency_reserve_percent` | `20` | `0`–`90` | Share of ordinary grant funds reserved for emergencies. |
| `village_prosperity.max_monthly_treasury_spending` | `24` | `1`–`1000000` | Maximum routine automatic Fund spending per 30 economic days, in emeralds. Fast-track capital is instead bounded by one selected project's exact unmet requirements. |

Integers must be written without decimal points. Boolean values are case-insensitive, but must be `true` or `false`. Blank or omitted known settings use their documented defaults.

## Starter Handbook onboarding

The existing `onboarding.join_hint_enabled` key controls the complete first-join onboarding package; no second handbook-specific setting is required. When enabled, each player is eligible once in each world for both the discovery message and a Starter Handbook. The book uses colored visual cues, diagrams, recipe layouts, hover explanations, and clickable contents and page navigation to explain the dashboard, finance terms, Village Prosperity, construction, recovery, and troubleshooting. It is added to inventory without opening a screen.

Successful inventory delivery completes onboarding for that player in that world. A full inventory does not drop the handbook or consume the one-time delivery: the player remains eligible and the server retries on the next join. Disabling the setting suppresses the automatic handbook and discovery message without removing existing copies or the independent shapeless Book plus Emerald replacement recipe. If the setting is later enabled for a player who has not completed onboarding, the next eligible join can deliver it.

`village_prosperity.development_radius` is an activation distance for physical work, not a simulation, chunk-loading, or AI distance. Known village economies continue data-only advancement when players are elsewhere and during trusted offline catch-up. Approved physical projects may break ground before their labor phase is complete, but block placement remains proportional, paced, protected, and loaded-chunk-only; the last block is withheld until labor completes. For blocks, censuses, audits, and settlers, the player and village must be in the same dimension, vertical Y separation is ignored, and the relevant chunks must already be loaded. Every eligible construction site receives its own one-block allowance per ten server ticks, and increasing the radius never force-loads chunks. Local entity and construction-theatre searches remain capped at 48 blocks; settler home assignment is a separate limit capped at 32 blocks.

New configuration files use `256`. Existing worlds keep any explicit value already stored in their
world-local configuration—including the former `96` default—until an operator changes it and runs
`/emerald config reload` or restarts the world.

`256` is the recommended general-purpose value. Raising it toward `512` only broadens which already-loaded villages may receive physical work; it does not increase view distance or keep distant chunks active. Prefer the default instead of a very large radius unless another server mechanism already keeps the intended chunks loaded and profiling shows sufficient headroom.

## Mode combinations

| Simulation | Visual progression | Result |
| --- | --- | --- |
| On | On | Full local economy plus loaded-chunk physical development. |
| On | Off | Local economies progress without placing prosperity structures or settlers. |
| Off | On | Existing queued physical work may finish while abstract settlement production remains paused. |
| Off | Off | Global banking, investing, and commodities only. |

`market_integration_enabled` and `automatic_recovery_enabled` are independent switches, but neither advances abstract settlements while simulation is disabled. Disabling the Fund or a contribution subtype prevents new contributions of that type; it does not erase existing village-owned balances, Endowment principal, history, or donor records.

Disabling offline progression ignores wall-clock time observed after the setting takes effect; it does not pause ordinary game-time advancement or remove catch-up already queued. `max_offline_days` limits each newly observed wall-clock gap and cannot be raised above the built-in 25,000-day absolute backlog safety limit. Disabling market events prevents new exceptional event shocks while leaving the calibrated regime cycle, ordinary volatility, and negative years intact.

Fast-track capital is the recommended default for player-directed growth. It does not empty a large Fund balance into global resource ceilings: each economic day can prepare and finish the labor for at most the one project the village has selected. A dedicated Project Sponsorship is consumed first, followed by eligible matching-purpose Direct Grant capital and General capital as the flexible fallback. The full remaining cost must be available or nothing is debited. Endowment payout remains in the capped routine channel, and the emergency reserve remains protected for routine crisis relief. Acute hunger must be stabilized before any project can use the fast lane. Approval still consumes the normal inputs, only one unfinished project is allowed, and physical construction remains paced and protected. Set `fast_track_capital_enabled=false` to restore fully throttled Fund releases.

## Operational guidance

- Back up the world before changing several simulation settings or moving between mod versions.
- Increase scan intervals, reduce the construction budget, or lower the development radius if a large server needs less background work. The fixed 16-village-per-pass ceiling still applies at every radius.
- Use `/emerald config show` to see the exact active values and file location.
- Use `/emerald config reload` after an edit. A success message lists the newly active values; an error confirms that the prior configuration is still active.
- These controls affect future simulation only. They do not rewrite market history, player holdings, or the save format.
