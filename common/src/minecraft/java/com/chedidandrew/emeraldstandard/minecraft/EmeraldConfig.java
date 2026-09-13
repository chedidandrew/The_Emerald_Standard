package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Small world-local configuration with conservative bounds and atomic replacement. */
public final class EmeraldConfig {
    public static final String FORCED_DEVELOPMENT_KEY = "village_prosperity.forced_instant_development";
    public static final String GUARDS_ENABLED_KEY = "compat.guard_villagers.enabled";
    public static final String GUARDS_POINTS_KEY = "compat.guard_villagers.safety_per_guard";
    public static final String GUARDS_CAP_KEY = "compat.guard_villagers.maximum_safety_bonus";
    static final int DEFAULT_VILLAGE_DEVELOPMENT_RADIUS = 256;
    static final int MIN_VILLAGE_DEVELOPMENT_RADIUS = 48;
    static final int MAX_VILLAGE_DEVELOPMENT_RADIUS = 512;
    private static final String FILE_NAME = "the_emerald_standard-config.properties";
    private static final Set<String> KNOWN_KEYS = Set.of(
            FORCED_DEVELOPMENT_KEY,
GUARDS_ENABLED_KEY, GUARDS_POINTS_KEY, GUARDS_CAP_KEY,
            "news.public_player_reports", "news.anonymous_players", "news.approximate_locations", "news.explicit_property_only",
            "village_banks.enabled", "village_banks.scan_interval_ticks", "village_banks.region_size",
            "banker.restriction_radius", "transactions.cooldown_ticks", "onboarding.join_hint_enabled",
            "market.events_enabled", "economic_clock.offline_progression_enabled", "economic_clock.max_offline_days",
            "village_prosperity.simulation_enabled", "village_prosperity.visual_progression_enabled",
            "village_prosperity.market_integration_enabled", "village_prosperity.automatic_recovery_enabled",
            "village_prosperity.scan_interval_ticks", "village_prosperity.development_radius",
            "village_prosperity.construction_interval_ticks", "village_prosperity.construction_blocks_per_tick",
            "village_prosperity.construction_blocks_per_second",
            "village_prosperity.settler_spawn_interval_ticks", "village_prosperity.donations_enabled",
            "village_prosperity.endowments_enabled", "village_prosperity.project_sponsorship_enabled",
            "village_prosperity.targeted_donations_enabled", "village_prosperity.donor_recognition_enabled",
            "village_prosperity.fast_track_capital_enabled", "village_prosperity.endowment_annual_payout_bps",
            "village_prosperity.minimum_emergency_reserve_percent", "village_prosperity.max_monthly_treasury_spending");
    private static volatile EmeraldConfig current = defaults();
    private static volatile Path currentPath;
    private static volatile EconomyService appliedEconomy;

    private final boolean newsPublic, newsAnonymous, newsApproximate, newsExplicit;
    private final boolean villageBanksEnabled;
    private final boolean guardVillagersEnabled;
    private final int guardSafetyPerGuard, guardMaximumSafetyBonus;
    private final boolean forcedVillageDevelopment;
    private final int villageScanIntervalTicks;
    private final int villageRegionSize;
    private final int bankerRestrictionRadius;
    private final int transactionCooldownTicks;
    private final boolean onboardingJoinHintEnabled;
    private final boolean marketEventsEnabled;
    private final boolean offlineProgressionEnabled;
    private final int maximumOfflineDays;
    private final boolean villageProsperitySimulationEnabled;
    private final boolean villageVisualProgressionEnabled;
    private final boolean villageMarketIntegrationEnabled;
    private final boolean villageAutomaticRecoveryEnabled;
    private final int villageProsperityScanIntervalTicks;
    private final int villageDevelopmentRadius;
    private final int villageConstructionBlocksPerSecond;
    private final int villageSettlerSpawnIntervalTicks;
    private final boolean prosperityFundEnabled;
    private final boolean prosperityFundEndowmentsEnabled;
    private final boolean prosperityFundProjectSponsorshipEnabled;
    private final boolean prosperityFundTargetedDonationsEnabled;
    private final boolean prosperityFundDonorRecognitionEnabled;
    private final boolean prosperityFundFastTrackCapitalEnabled;
    private final int prosperityFundEndowmentAnnualPayoutBps;
    private final int prosperityFundMinimumEmergencyReservePercent;
    private final int prosperityFundMaximumMonthlySpending;

    private EmeraldConfig(
            boolean forcedVillageDevelopment, boolean villageBanksEnabled, int villageScanIntervalTicks, int villageRegionSize,
            int bankerRestrictionRadius, int transactionCooldownTicks, boolean onboardingJoinHintEnabled,
            boolean marketEventsEnabled, boolean offlineProgressionEnabled, int maximumOfflineDays,
            boolean villageProsperitySimulationEnabled, boolean villageVisualProgressionEnabled,
            boolean villageMarketIntegrationEnabled, boolean villageAutomaticRecoveryEnabled,
            int villageProsperityScanIntervalTicks, int villageDevelopmentRadius,
            int villageConstructionBlocksPerSecond,
            int villageSettlerSpawnIntervalTicks, boolean prosperityFundEnabled,
            boolean prosperityFundEndowmentsEnabled, boolean prosperityFundProjectSponsorshipEnabled,
            boolean prosperityFundTargetedDonationsEnabled, boolean prosperityFundDonorRecognitionEnabled,
            boolean prosperityFundFastTrackCapitalEnabled, int prosperityFundEndowmentAnnualPayoutBps,
            int prosperityFundMinimumEmergencyReservePercent, int prosperityFundMaximumMonthlySpending,
boolean guardVillagersEnabled, int guardSafetyPerGuard, int guardMaximumSafetyBonus,
            boolean newsPublic, boolean newsAnonymous, boolean newsApproximate, boolean newsExplicit) {
        this.newsPublic=newsPublic;this.newsAnonymous=newsAnonymous;this.newsApproximate=newsApproximate;this.newsExplicit=newsExplicit;
        this.guardVillagersEnabled = guardVillagersEnabled;
        this.guardSafetyPerGuard = guardSafetyPerGuard;
        this.guardMaximumSafetyBonus = guardMaximumSafetyBonus;
        this.villageBanksEnabled = villageBanksEnabled;
        this.forcedVillageDevelopment = forcedVillageDevelopment;
        this.villageScanIntervalTicks = villageScanIntervalTicks;
        this.villageRegionSize = villageRegionSize;
        this.bankerRestrictionRadius = bankerRestrictionRadius;
        this.transactionCooldownTicks = transactionCooldownTicks;
        this.onboardingJoinHintEnabled = onboardingJoinHintEnabled;
        this.marketEventsEnabled = marketEventsEnabled;
        this.offlineProgressionEnabled = offlineProgressionEnabled;
        this.maximumOfflineDays = maximumOfflineDays;
        this.villageProsperitySimulationEnabled = villageProsperitySimulationEnabled;
        this.villageVisualProgressionEnabled = villageVisualProgressionEnabled;
        this.villageMarketIntegrationEnabled = villageMarketIntegrationEnabled;
        this.villageAutomaticRecoveryEnabled = villageAutomaticRecoveryEnabled;
        this.villageProsperityScanIntervalTicks = villageProsperityScanIntervalTicks;
        this.villageDevelopmentRadius = villageDevelopmentRadius;
        this.villageConstructionBlocksPerSecond = villageConstructionBlocksPerSecond;
        this.villageSettlerSpawnIntervalTicks = villageSettlerSpawnIntervalTicks;
        this.prosperityFundEnabled = prosperityFundEnabled;
        this.prosperityFundEndowmentsEnabled = prosperityFundEndowmentsEnabled;
        this.prosperityFundProjectSponsorshipEnabled = prosperityFundProjectSponsorshipEnabled;
        this.prosperityFundTargetedDonationsEnabled = prosperityFundTargetedDonationsEnabled;
        this.prosperityFundDonorRecognitionEnabled = prosperityFundDonorRecognitionEnabled;
        this.prosperityFundFastTrackCapitalEnabled = prosperityFundFastTrackCapitalEnabled;
        this.prosperityFundEndowmentAnnualPayoutBps = prosperityFundEndowmentAnnualPayoutBps;
        this.prosperityFundMinimumEmergencyReservePercent = prosperityFundMinimumEmergencyReservePercent;
        this.prosperityFundMaximumMonthlySpending = prosperityFundMaximumMonthlySpending;
    }

    public static synchronized EmeraldConfig load(Path worldDataDirectory) throws IOException {
        Path candidatePath = worldDataDirectory.resolve(FILE_NAME);
        if (!Files.exists(candidatePath)) writeDefaults(candidatePath);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(candidatePath)) { properties.load(input); }
        EmeraldConfig candidate = parse(properties);
        currentPath = candidatePath;
        current = candidate;
        return candidate;
    }

    /** Pure validation: never changes the active world, file, or economy. */
    public static EmeraldConfig parse(Properties properties) throws IOException {
        Set<String> unknownKeys = new TreeSet<>(properties.stringPropertyNames());
        unknownKeys.removeAll(KNOWN_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw new IOException("Unknown configuration key(s): " + String.join(", ", unknownKeys));
        }
        return new EmeraldConfig(
                bool(properties, FORCED_DEVELOPMENT_KEY, false),
                bool(properties, "village_banks.enabled", true),
                bounded(properties, "village_banks.scan_interval_ticks", 200, 20, 12_000),
                bounded(properties, "village_banks.region_size", 256, 128, 2_048),
                bounded(properties, "banker.restriction_radius", 5, 2, 32),
                bounded(properties, "transactions.cooldown_ticks", 5, 0, 200),
                bool(properties, "onboarding.join_hint_enabled", true),
                bool(properties, "market.events_enabled", true),
                bool(properties, "economic_clock.offline_progression_enabled", true),
                bounded(properties, "economic_clock.max_offline_days",
                        (int) EconomyService.MAX_TRUSTED_CATCH_UP_DAYS, 1,
                        (int) EconomyService.MAX_TRUSTED_CATCH_UP_DAYS),
                bool(properties, "village_prosperity.simulation_enabled", true),
                bool(properties, "village_prosperity.visual_progression_enabled", true),
                bool(properties, "village_prosperity.market_integration_enabled", true),
                bool(properties, "village_prosperity.automatic_recovery_enabled", true),
                bounded(properties, "village_prosperity.scan_interval_ticks", 400, 40, 24_000),
                bounded(properties, "village_prosperity.development_radius",
                        DEFAULT_VILLAGE_DEVELOPMENT_RADIUS, MIN_VILLAGE_DEVELOPMENT_RADIUS,
                        MAX_VILLAGE_DEVELOPMENT_RADIUS),
                constructionRate(properties),
                bounded(properties, "village_prosperity.settler_spawn_interval_ticks", 600, 200, 24_000),
                bool(properties, "village_prosperity.donations_enabled", true),
                bool(properties, "village_prosperity.endowments_enabled", true),
                bool(properties, "village_prosperity.project_sponsorship_enabled", true),
                bool(properties, "village_prosperity.targeted_donations_enabled", true),
                bool(properties, "village_prosperity.donor_recognition_enabled", true),
                bool(properties, "village_prosperity.fast_track_capital_enabled", true),
                bounded(properties, "village_prosperity.endowment_annual_payout_bps", 400, 0, 10_000),
                bounded(properties, "village_prosperity.minimum_emergency_reserve_percent", 20, 0, 90),
                bounded(properties, "village_prosperity.max_monthly_treasury_spending", 24, 1, 1_000_000),
                bool(properties, GUARDS_ENABLED_KEY, true),
                bounded(properties, GUARDS_POINTS_KEY, 2, 0, 10),
bounded(properties, GUARDS_CAP_KEY, 12, 0, 30),
                bool(properties,"news.public_player_reports",true), bool(properties,"news.anonymous_players",false),
                bool(properties,"news.approximate_locations",true), bool(properties,"news.explicit_property_only",false));
    }

    /** Stable ordered values for the GUI; the returned map is an independent snapshot. */
    public Map<String, String> values() {
Map<String, String> values = new LinkedHashMap<>();
        values.put("news.public_player_reports",""+newsPublic);values.put("news.anonymous_players",""+newsAnonymous);
        values.put("news.approximate_locations",""+newsApproximate);values.put("news.explicit_property_only",""+newsExplicit);
        values.put(FORCED_DEVELOPMENT_KEY, String.valueOf(forcedVillageDevelopment));
        values.put(GUARDS_ENABLED_KEY, String.valueOf(guardVillagersEnabled));
        values.put(GUARDS_POINTS_KEY, String.valueOf(guardSafetyPerGuard));
        values.put(GUARDS_CAP_KEY, String.valueOf(guardMaximumSafetyBonus));
        values.put("village_banks.enabled", String.valueOf(villageBanksEnabled));
        values.put("village_banks.scan_interval_ticks", String.valueOf(villageScanIntervalTicks));
        values.put("village_banks.region_size", String.valueOf(villageRegionSize));
        values.put("banker.restriction_radius", String.valueOf(bankerRestrictionRadius));
        values.put("transactions.cooldown_ticks", String.valueOf(transactionCooldownTicks));
        values.put("onboarding.join_hint_enabled", String.valueOf(onboardingJoinHintEnabled));
        values.put("market.events_enabled", String.valueOf(marketEventsEnabled));
        values.put("economic_clock.offline_progression_enabled", String.valueOf(offlineProgressionEnabled));
        values.put("economic_clock.max_offline_days", String.valueOf(maximumOfflineDays));
        values.put("village_prosperity.simulation_enabled", String.valueOf(villageProsperitySimulationEnabled));
        values.put("village_prosperity.visual_progression_enabled", String.valueOf(villageVisualProgressionEnabled));
        values.put("village_prosperity.market_integration_enabled", String.valueOf(villageMarketIntegrationEnabled));
        values.put("village_prosperity.automatic_recovery_enabled", String.valueOf(villageAutomaticRecoveryEnabled));
        values.put("village_prosperity.scan_interval_ticks", String.valueOf(villageProsperityScanIntervalTicks));
        values.put("village_prosperity.development_radius", String.valueOf(villageDevelopmentRadius));
        values.put("village_prosperity.construction_blocks_per_second", String.valueOf(villageConstructionBlocksPerSecond));
        values.put("village_prosperity.settler_spawn_interval_ticks", String.valueOf(villageSettlerSpawnIntervalTicks));
        values.put("village_prosperity.donations_enabled", String.valueOf(prosperityFundEnabled));
        values.put("village_prosperity.endowments_enabled", String.valueOf(prosperityFundEndowmentsEnabled));
        values.put("village_prosperity.project_sponsorship_enabled", String.valueOf(prosperityFundProjectSponsorshipEnabled));
        values.put("village_prosperity.targeted_donations_enabled", String.valueOf(prosperityFundTargetedDonationsEnabled));
        values.put("village_prosperity.donor_recognition_enabled", String.valueOf(prosperityFundDonorRecognitionEnabled));
        values.put("village_prosperity.fast_track_capital_enabled", String.valueOf(prosperityFundFastTrackCapitalEnabled));
        values.put("village_prosperity.endowment_annual_payout_bps", String.valueOf(prosperityFundEndowmentAnnualPayoutBps));
        values.put("village_prosperity.minimum_emergency_reserve_percent", String.valueOf(prosperityFundMinimumEmergencyReservePercent));
        values.put("village_prosperity.max_monthly_treasury_spending", String.valueOf(prosperityFundMaximumMonthlySpending));
        return java.util.Collections.unmodifiableMap(values);
    }

    /**
     * Called only on the owning integrated server thread. Rejects stale GUI snapshots, external
     * file edits, and world changes before touching disk. Validation precedes atomic replacement.
     */
    public static synchronized EmeraldConfig update(
            Path worldDataDirectory, EmeraldConfig expected, Map<String, String> edits) throws IOException {
        Path target = worldDataDirectory.resolve(FILE_NAME).toAbsolutePath().normalize();
        if (current != expected || currentPath == null || appliedEconomy == null
                || !currentPath.toAbsolutePath().normalize().equals(target)) {
            throw new IOException("World or configuration changed. Reopen Settings before saving.");
        }
        Properties disk = new Properties();
        try (InputStream input = Files.newInputStream(target)) { disk.load(input); }
        if (!parse(disk).values().equals(expected.values())) {
            throw new IOException("The config file changed outside this screen. Reload it first.");
        }
        Properties proposed = new Properties();
        proposed.putAll(expected.values());
        for (var edit : edits.entrySet()) {
            if (!KNOWN_KEYS.contains(edit.getKey()) || edit.getValue() == null || edit.getValue().isBlank()) {
                throw new IOException("Unknown setting or empty value: " + edit.getKey());
            }
            proposed.setProperty(edit.getKey(), edit.getValue());
        }
        EmeraldConfig candidate = parse(proposed);
        Path temporary = Files.createTempFile(target.getParent(), FILE_NAME, ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                proposed.store(output, "The Emerald Standard world settings; edited in-game.");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            // Fail safely if atomic replacement is unavailable; never claim a partial save worked.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        candidate.applyTo(appliedEconomy);
        current = candidate;
        return candidate;
    }

    public static synchronized EmeraldConfig reload() throws IOException {
        if (currentPath == null) throw new IOException("The Emerald Standard configuration has not been initialized");
        return load(currentPath.getParent());
    }
    public static EmeraldConfig current() { return current; }
    public static String location() {
        Path path = currentPath;
        return path == null ? "not initialized" : path.toAbsolutePath().normalize().toString();
    }
    public com.chedidandrew.emeraldstandard.core.NewsEditorial.Policy newsPolicy() {
        return new com.chedidandrew.emeraldstandard.core.NewsEditorial.Policy(newsPublic,newsAnonymous,newsApproximate);
    }
    public boolean newsExplicitPropertyOnly() { return newsExplicit; }
    public boolean villageBanksEnabled() { return villageBanksEnabled; }
    public boolean guardVillagersEnabled() { return guardVillagersEnabled; }
    public int guardSafetyPerGuard() { return guardSafetyPerGuard; }
    public int guardMaximumSafetyBonus() { return guardMaximumSafetyBonus; }
    public boolean forcedVillageDevelopment() { return forcedVillageDevelopment; }
    public int villageScanIntervalTicks() { return villageScanIntervalTicks; }
    public int villageRegionSize() { return villageRegionSize; }
    public int bankerRestrictionRadius() { return bankerRestrictionRadius; }
    public int transactionCooldownTicks() { return transactionCooldownTicks; }
    public boolean onboardingJoinHintEnabled() { return onboardingJoinHintEnabled; }
    public boolean marketEventsEnabled() { return marketEventsEnabled; }
    public boolean offlineProgressionEnabled() { return offlineProgressionEnabled; }
    public int maximumOfflineDays() { return maximumOfflineDays; }
    public boolean villageProsperitySimulationEnabled() { return villageProsperitySimulationEnabled; }
    public boolean villageVisualProgressionEnabled() { return villageVisualProgressionEnabled; }
    public boolean villageMarketIntegrationEnabled() { return villageMarketIntegrationEnabled; }
    public boolean villageAutomaticRecoveryEnabled() { return villageAutomaticRecoveryEnabled; }
    public int villageProsperityScanIntervalTicks() { return villageProsperityScanIntervalTicks; }
    public int villageDevelopmentRadius() { return villageDevelopmentRadius; }
    public int villageConstructionBlocksPerSecond() { return villageConstructionBlocksPerSecond; }

    /** Independent normal pace for EACH site; skipped-night bonus work is budgeted separately. */
    public int constructionAllowance(long gameTime) {
        int phase = (int) Math.floorMod(gameTime - 1L, 20L);
        return ((phase + 1) * villageConstructionBlocksPerSecond) / 20
                - (phase * villageConstructionBlocksPerSecond) / 20;
    }
    public int villageSettlerSpawnIntervalTicks() { return villageSettlerSpawnIntervalTicks; }
    public boolean prosperityFundEnabled() { return prosperityFundEnabled; }
    public boolean prosperityFundEndowmentsEnabled() { return prosperityFundEndowmentsEnabled; }
    public boolean prosperityFundProjectSponsorshipEnabled() { return prosperityFundProjectSponsorshipEnabled; }
    public boolean prosperityFundTargetedDonationsEnabled() { return prosperityFundTargetedDonationsEnabled; }
    public boolean prosperityFundDonorRecognitionEnabled() { return prosperityFundDonorRecognitionEnabled; }
    public double prosperityFundEndowmentAnnualPayoutRate() { return prosperityFundEndowmentAnnualPayoutBps / 10_000.0; }
    public double prosperityFundEmergencyReserveFraction() { return prosperityFundMinimumEmergencyReservePercent / 100.0; }
    public int prosperityFundMaximumMonthlySpending() { return prosperityFundMaximumMonthlySpending; }
    public boolean prosperityFundFastTrackCapitalEnabled() { return prosperityFundFastTrackCapitalEnabled; }

    /** Applies every simulation option on the owning server thread. */
    public void applyTo(EconomyService economy) {
        // Changing options immediately drops old bonuses; the next loaded census recalculates.
        economy.clearVillageGuardObservations();
        appliedEconomy = java.util.Objects.requireNonNull(economy, "economy");
        if (forcedVillageDevelopment && !economy.forcedVillageDevelopment())
            System.getLogger("the_emerald_standard").log(System.Logger.Level.WARNING,
                    "DEBUG forced village development enabled: permanent world changes, no economic gates, no automatic undo. Back up this world. Work remains loaded/protected and budgeted.");
        economy.configureForcedVillageDevelopment(forcedVillageDevelopment);
        economy.configureMarketEvents(marketEventsEnabled);
        economy.configurePlayerNews(newsPublic);
        economy.configureEconomicClock(offlineProgressionEnabled, maximumOfflineDays);
        economy.configureVillageProsperity(villageProsperitySimulationEnabled, villageVisualProgressionEnabled,
                villageMarketIntegrationEnabled, villageAutomaticRecoveryEnabled);
        long dailyCapMicro = Math.max(1L, Math.round(prosperityFundMaximumMonthlySpending
                * (double) EconomyState.MICRO / 30.0));
        economy.configureProsperityFund(new EconomyService.ProsperityFundPolicy(
                prosperityFundEnabled && villageProsperitySimulationEnabled,
                prosperityFundEndowmentAnnualPayoutRate(), prosperityFundEmergencyReserveFraction(),
                dailyCapMicro, prosperityFundFastTrackCapitalEnabled));
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "village banks=%s, bank scan=%d ticks, bank region=%d blocks, banker radius=%d, "
                        + "transaction cooldown=%d ticks, first-join hint=%s, market events=%s, "
                        + "offline progression=%s, max offline=%d days, prosperity simulation=%s, visual progression=%s, "
                        + "market integration=%s, automatic recovery=%s, prosperity scan=%d ticks, "
                        + "development radius=%d, construction=%d block(s)/second/site at 20 TPS, "
                        + "settler interval=%d ticks, prosperity fund=%s, endowments=%s, "
                        + "project sponsorship=%s, targeted donations=%s, donor recognition=%s, "
                        + "fast-track capital=%s, endowment payout=%.2f%%, emergency reserve=%d%%, "
                        + "routine monthly spending cap=%d, DEBUG forced development=%s",
                villageBanksEnabled, villageScanIntervalTicks, villageRegionSize, bankerRestrictionRadius,
                transactionCooldownTicks, onboardingJoinHintEnabled, marketEventsEnabled, offlineProgressionEnabled,
                maximumOfflineDays, villageProsperitySimulationEnabled, villageVisualProgressionEnabled,
                villageMarketIntegrationEnabled, villageAutomaticRecoveryEnabled, villageProsperityScanIntervalTicks,
                villageDevelopmentRadius, villageConstructionBlocksPerSecond,
                villageSettlerSpawnIntervalTicks, prosperityFundEnabled, prosperityFundEndowmentsEnabled,
                prosperityFundProjectSponsorshipEnabled, prosperityFundTargetedDonationsEnabled,
                prosperityFundDonorRecognitionEnabled, prosperityFundFastTrackCapitalEnabled,
                prosperityFundEndowmentAnnualPayoutBps / 100.0, prosperityFundMinimumEmergencyReservePercent,
                prosperityFundMaximumMonthlySpending, forcedVillageDevelopment);
    }

    public static EmeraldConfig defaults() {
        return new EmeraldConfig(false, true, 200, 256, 5, 5, true, true, true,
                (int) EconomyService.MAX_TRUSTED_CATCH_UP_DAYS, true, true, true, true, 400,
                DEFAULT_VILLAGE_DEVELOPMENT_RADIUS, 2, 600,
                true, true, true, true, true, true, 400, 20, 24, true, 2, 12, true, false, true, false);
    }
    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.putAll(defaults().values());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "The Emerald Standard world configuration. See docs/CONFIGURATION.md for bounds.");
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static boolean bool(Properties properties, String key, boolean fallback) throws IOException {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        if (raw.trim().equalsIgnoreCase("true")) return true;
        if (raw.trim().equalsIgnoreCase("false")) return false;
        throw new IOException("Configuration " + key + " must be true or false");
    }
    private static int constructionRate(Properties properties) throws IOException {
        // Older releases displayed these keys but always enforced 2/s. Keep accepting them on
        // load, without suddenly speeding up an old world. The new explicit rate takes priority.
        bounded(properties, "village_prosperity.construction_interval_ticks", 10, 1, 200);
        bounded(properties, "village_prosperity.construction_blocks_per_tick", 1, 1, 64);
        return bounded(properties, "village_prosperity.construction_blocks_per_second", 2, 1, 100);
    }
    private static int bounded(Properties properties, String key, int fallback, int minimum, int maximum) throws IOException {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) {
                throw new IOException("Configuration " + key + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Configuration " + key + " is not an integer", exception);
        }
    }
}
