package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig;

/** Complete human-readable help for every editable world setting. */
public final class SettingsHelp {
    private SettingsHelp() {}
    public static String description(String key) {
        String help = switch (key) {
            case "news.public_player_reports" -> "Publish observed player and local follow-up reports. Off stops new reports and hides retained local articles from readers; it does not delete the private server archive or change village consequences.";
            case "news.anonymous_players" -> "Remove player names from every local article sent to newspaper readers, including retained reports. This cannot erase information somebody already saw or recorded.";
            case "news.approximate_locations" -> "Withhold exact village coordinates in public local reports. On reports only a local village; Off includes the district center. This cannot erase previously seen locations.";
            case "news.explicit_property_only" -> "Only report block/container actions on community property explicitly designated by an operator with /emerald news property. Safer for shared or older worlds with ambiguous ownership. Fund gifts and confirmed villager deaths are independent.";
            case EmeraldConfig.GUARDS_ENABLED_KEY -> "Optional Guard Villagers integration for either supported loader edition. Nearby living, adult guards with AI can add temporary Safety; hostile guards are excluded. No guards means no bonus. Does not spawn, equip or control guards. Disabling clears the bonus.";
            case EmeraldConfig.GUARDS_POINTS_KEY -> "Temporary Safety points per eligible nearby guard, limited by the maximum bonus. Counts are refreshed with the loaded village census, never stacked per scan. Range: 0–10.";
            case EmeraldConfig.GUARDS_CAP_KEY -> "Maximum combined Guard Villagers Safety bonus per district; final Safety cannot exceed 100. No bonus is saved across a restart. Unobserved bonuses fade over seven economic days. Range: 0–30.";
            case EmeraldConfig.FORCED_DEVELOPMENT_KEY -> "DEBUG ONLY. Repeatedly funds and builds districts without food, safety, resources, upkeep or tier/approval requirements. Overrides ordinary simulation/visual pauses and construction speed. Work is rapid but budgeted, loaded-only and protected. Can permanently alter terrain, generate loot and increase lag/save size. Disabling does not remove buildings or undo changes. Confirmation required in this screen; Apply saves. Back up first.";
            case "village_banks.enabled" -> "Allows new village Banks, progressive Bank construction and Banker maintenance. Turning this off does not erase accounts.";
            case "village_banks.scan_interval_ticks" -> "Ticks between Bank discovery and recovery checks. Lower is more responsive but uses more server work. 20 ticks = 1 second. Range: 20–12000.";
            case "village_banks.region_size" -> "Legacy Bank identity grid size in blocks. Existing saved Bank identities remain authoritative; this is not a village growth limit. Range: 128–2048.";
            case "banker.restriction_radius" -> "Home radius in blocks for managed Bankers. Higher values let them wander farther from their Bank. Range: 2–32.";
            case "transactions.cooldown_ticks" -> "Minimum ticks between accepted dashboard actions per player. Lower permits faster actions; 0 removes this delay. 20 ticks = 1 second. Range: 0–200.";
            case "onboarding.join_hint_enabled" -> "Gives each new player a Starter Handbook and discovery hint once per world. Does not remove existing books or open them automatically.";
            case "market.events_enabled" -> "Enables future exceptional market events and price shocks. Turning this off keeps ordinary market movement and existing history.";
            case "economic_clock.offline_progression_enabled" -> "Lets trusted elapsed real time advance the economy while the world is closed. Turning this off does not pause normal in-game time or cancel catch-up already queued.";
            case "economic_clock.max_offline_days" -> "Maximum economic days credited from one offline time gap. Lower gives stronger protection against clock jumps. Range: 1–25000.";
            case "village_prosperity.simulation_enabled" -> "Advances village production, prosperity, upkeep and economic projects. Turning this off pauses this simulation and new Fund donations; already queued physical work can still finish if visual progression is on.";
            case "village_prosperity.visual_progression_enabled" -> "Places queued village buildings, paths and settlers in loaded chunks. Turning this off pauses their physical development, not the economy. Bank construction has its own Bank enabled switch.";
            case "village_prosperity.market_integration_enabled" -> "Lets eligible village fundamentals influence market sectors. Turning this off disconnects that influence without stopping the villages or global markets.";
            case "village_prosperity.automatic_recovery_enabled" -> "Allows eligible villages lost to non-player causes to recover after a cooldown. Does not bypass player-caused extinction protection.";
            case "village_prosperity.scan_interval_ticks" -> "Ticks between loaded-village census and expansion checks. Lower notices changes sooner but costs more server work. 20 ticks = 1 second. Range: 40–24000.";
            case "village_prosperity.development_radius" -> "Horizontal distance in blocks from a player for physical village development. Higher activates more nearby work; it does not load chunks or cap city size. Range: 48–512.";
            case "village_prosperity.construction_blocks_per_second" -> "Block operations per second for EACH active construction site, including Banks, at 20 server ticks per second. Each site gets its own allowance; clearing blocks uses it too. Higher is faster and heavier. Paused/unloaded sites do not bank unused work. Range: 1–100.";
            case "village_prosperity.settler_spawn_interval_ticks" -> "Ticks between attempts to place eligible new settlers. Lower brings ready settlers in sooner; housing and safety checks still apply. 20 ticks = 1 second. Range: 200–24000.";
            case "village_prosperity.donations_enabled" -> "Enables new Prosperity Fund contributions. Requires village simulation. Turning this off preserves existing balances, principal and donation records.";
            case "village_prosperity.endowments_enabled" -> "Allows new protected-principal Endowment contributions. Turning this off does not erase existing endowments.";
            case "village_prosperity.project_sponsorship_enabled" -> "Allows new donations tied to a village's active economic project. Turning this off preserves previous contributions.";
            case "village_prosperity.targeted_donations_enabled" -> "Allows Direct Grants and Endowments for a selected purpose such as Food or Infrastructure. Turning this off limits new contributions to General.";
            case "village_prosperity.donor_recognition_enabled" -> "Shows honorary donor titles. These are recognition only, not financial rewards. Turning this off preserves contribution records.";
            case "village_prosperity.fast_track_capital_enabled" -> "Lets enough player-donated capital cover a selected project's remaining resources and economic labor outside routine spending limits. Still requires valid approval and paced physical construction; it does not instantly spawn a building.";
            case "village_prosperity.endowment_annual_payout_bps" -> "Annual payout from Endowments in basis points: 100 = 1%, 400 = 4%. Higher releases more income; principal remains protected. Range: 0–10000.";
            case "village_prosperity.minimum_emergency_reserve_percent" -> "Percentage of ordinary grant funds held for emergencies. Higher keeps a larger safety reserve but leaves less for routine improvements. Range: 0–90.";
            case "village_prosperity.max_monthly_treasury_spending" -> "Routine automatic Fund spending cap in emeralds per 30 economic days, distributed as a daily allowance. Higher spends available funds faster. Fast-track player capital uses a separate project-sized limit. Range: 1–1000000.";
            default -> throw new IllegalArgumentException("Missing settings help: " + key);
        };
        return help + " Default: " + EmeraldConfig.defaults().values().get(key) + ".";
    }
}
