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
| `village_prosperity.simulation_enabled` | `true` | `true`, `false` | Advances abstract settlement economies. |
| `village_prosperity.visual_progression_enabled` | `true` | `true`, `false` | Allows queued structures and settlers to materialize in loaded chunks. |
| `village_prosperity.market_integration_enabled` | `true` | `true`, `false` | Allows eligible settlement fundamentals to influence market sectors. |
| `village_prosperity.automatic_recovery_enabled` | `true` | `true`, `false` | Allows eligible non-player-caused extinction to recover after its cooldown. |
| `village_prosperity.scan_interval_ticks` | `400` | `40`–`24000` | Delay between loaded-player settlement census scans. |
| `village_prosperity.development_radius` | `96` | `48`–`192` | Maximum local radius considered for village development. |
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

## Mode combinations

| Simulation | Visual progression | Result |
| --- | --- | --- |
| On | On | Full local economy plus loaded-chunk physical development. |
| On | Off | Local economies progress without placing prosperity structures or settlers. |
| Off | On | Existing queued physical work may finish while abstract settlement production remains paused. |
| Off | Off | Global banking, investing, and commodities only. |

`market_integration_enabled` and `automatic_recovery_enabled` are independent switches, but neither advances abstract settlements while simulation is disabled. Disabling the Fund or a contribution subtype prevents new contributions of that type; it does not erase existing village-owned balances, Endowment principal, history, or donor records.

## Operational guidance

- Back up the world before changing several simulation settings or moving between mod versions.
- Increase scan intervals or reduce the construction budget first if a large server needs less background work.
- Use `/emerald config show` to see the exact active values and file location.
- Use `/emerald config reload` after an edit. A success message lists the newly active values; an error confirms that the prior configuration is still active.
- Configuration does not change market calibration, player holdings, economic time, or save-format compatibility.
