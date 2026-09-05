# World configuration

The Emerald Standard creates `the_emerald_standard-config.properties` in each world's `data` directory on the first server start. Configuration is world-local; changing one world does not change another.

Edit the file while the server is stopped, or edit it and run `/emerald config reload`. A reload validates the complete file before applying anything. If a value is malformed, outside its supported range, or uses an unknown key, the reload is rejected and every previously active setting remains in effect. The error reports the file path and offending key. This strict behavior prevents a misspelled safety or performance setting from being silently ignored.

## Settings

| Key | Default | Accepted values | Effect |
| --- | ---: | --- | --- |
| `village_banks.enabled` | `true` | `true`, `false` | Enables discovery-based Village Bank generation and Banker maintenance. Existing player accounts remain available. |
| `village_banks.scan_interval_ticks` | `200` | `20`–`12000` | Delay between loaded-player Village Bank scans. |
| `village_banks.region_size` | `256` | `128`–`2048` | Legacy spatial-key size. Existing persisted bank identities remain authoritative. |
| `banker.restriction_radius` | `5` | `2`–`32` | Home radius assigned to managed Banker villagers. |
| `transactions.cooldown_ticks` | `5` | `0`–`200` | Server-side delay between accepted dashboard actions from one player. |
| `onboarding.join_hint_enabled` | `true` | `true`, `false` | Sends each player one persistent discovery hint on their first join after the mod is installed. |
| `market.events_enabled` | `true` | `true`, `false` | Enables future rare market events and their asset or commodity shocks. Disabling it does not erase historical news. |
| `economic_clock.offline_progression_enabled` | `true` | `true`, `false` | Allows trusted wall-clock time to advance the economy while the world is closed. Game-time progression remains active when disabled. |
| `economic_clock.max_offline_days` | `25000` | `1`–`25000` | Maximum wall-clock economic days credited from one observed gap. Lower values provide stronger clock-jump protection. |
| `village_prosperity.simulation_enabled` | `true` | `true`, `false` | Advances abstract settlement economies. |
| `village_prosperity.visual_progression_enabled` | `true` | `true`, `false` | Allows queued structures and settlers to materialize in loaded chunks. |
| `village_prosperity.market_integration_enabled` | `true` | `true`, `false` | Allows eligible settlement fundamentals to influence market sectors. |
| `village_prosperity.automatic_recovery_enabled` | `true` | `true`, `false` | Allows eligible non-player-caused extinction to recover after its cooldown. |
| `village_prosperity.scan_interval_ticks` | `400` | `40`–`24000` | Delay between loaded-player settlement census scans. |
| `village_prosperity.development_radius` | `256` | `48`–`512` | Horizontal X/Z distance from a player within which a known village may be considered for physical development. |
| `village_prosperity.construction_interval_ticks` | `10` | `1`–`200` | Delay between bounded construction passes. |
| `village_prosperity.construction_blocks_per_tick` | `2` | `1`–`64` | Global block budget used by one construction pass. |
| `village_prosperity.settler_spawn_interval_ticks` | `1200` | `200`–`24000` | Delay between physical settler placement attempts. |
| `village_prosperity.donations_enabled` | `true` | `true`, `false` | Enables the Prosperity Fund as a whole. The fund also requires settlement simulation. |
| `village_prosperity.endowments_enabled` | `true` | `true`, `false` | Allows new protected-principal Endowment contributions. |
| `village_prosperity.project_sponsorship_enabled` | `true` | `true`, `false` | Allows contributions tied to the active economic project. |
| `village_prosperity.targeted_donations_enabled` | `true` | `true`, `false` | Allows a Direct Grant or Endowment to select a purpose other than General. |
| `village_prosperity.donor_recognition_enabled` | `true` | `true`, `false` | Shows non-financial donor titles. It does not alter persisted contributions. |
| `village_prosperity.endowment_annual_payout_bps` | `400` | `0`–`10000` | Annual Endowment payout in basis points; `400` is 4 percent. Principal remains protected. |
| `village_prosperity.minimum_emergency_reserve_percent` | `20` | `0`–`90` | Share of ordinary grant funds reserved for emergencies. |
| `village_prosperity.max_monthly_treasury_spending` | `24` | `1`–`1000000` | Maximum automatic Fund spending per 30 economic days, in emeralds. |

Integers must be written without decimal points. Boolean values are case-insensitive, but must be `true` or `false`. Blank or omitted known settings use their documented defaults.

`village_prosperity.development_radius` is an activation distance for physical work, not a simulation, chunk-loading, or AI distance. Known village economies continue data-only advancement when players are elsewhere and during trusted offline catch-up. For blocks, censuses, audits, and settlers, the player and village must be in the same dimension, vertical Y separation is ignored, and the relevant chunks must already be loaded. At most 16 eligible villages are considered in one construction pass, and increasing the radius never force-loads chunks. Local entity and construction-theatre searches remain capped at 48 blocks; settler home assignment is a separate limit capped at 32 blocks.

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

## Operational guidance

- Back up the world before changing several simulation settings or moving between mod versions.
- Increase scan intervals, reduce the construction budget, or lower the development radius if a large server needs less background work. The fixed 16-village-per-pass ceiling still applies at every radius.
- Use `/emerald config show` to see the exact active values and file location.
- Use `/emerald config reload` after an edit. A success message lists the newly active values; an error confirms that the prior configuration is still active.
- These controls affect future simulation only. They do not rewrite market history, player holdings, or the save format.
