package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Small world-local configuration with conservative bounds and atomic replacement. */
public final class EmeraldConfig {
    private static final String FILE_NAME = "the_emerald_standard-config.properties";
    private static final Set<String> KNOWN_KEYS = Set.of(
            "village_banks.enabled",
            "village_banks.scan_interval_ticks",
            "village_banks.region_size",
            "banker.restriction_radius",
            "transactions.cooldown_ticks",
            "onboarding.join_hint_enabled",
            "village_prosperity.simulation_enabled",
            "village_prosperity.visual_progression_enabled",
            "village_prosperity.market_integration_enabled",
            "village_prosperity.automatic_recovery_enabled",
            "village_prosperity.scan_interval_ticks",
            "village_prosperity.development_radius",
            "village_prosperity.construction_interval_ticks",
            "village_prosperity.construction_blocks_per_tick",
            "village_prosperity.settler_spawn_interval_ticks",
            "village_prosperity.donations_enabled",
            "village_prosperity.endowments_enabled",
            "village_prosperity.project_sponsorship_enabled",
            "village_prosperity.targeted_donations_enabled",
            "village_prosperity.donor_recognition_enabled",
            "village_prosperity.endowment_annual_payout_bps",
            "village_prosperity.minimum_emergency_reserve_percent",
            "village_prosperity.max_monthly_treasury_spending");
    private static volatile EmeraldConfig current = defaults();
    private static volatile Path currentPath;

    private final boolean villageBanksEnabled;
    private final int villageScanIntervalTicks;
    private final int villageRegionSize;
    private final int bankerRestrictionRadius;
    private final int transactionCooldownTicks;
    private final boolean onboardingJoinHintEnabled;

    private final boolean villageProsperitySimulationEnabled;
    private final boolean villageVisualProgressionEnabled;
    private final boolean villageMarketIntegrationEnabled;
    private final boolean villageAutomaticRecoveryEnabled;
    private final int villageProsperityScanIntervalTicks;
    private final int villageDevelopmentRadius;
    private final int villageConstructionIntervalTicks;
    private final int villageConstructionBlocksPerTick;
    private final int villageSettlerSpawnIntervalTicks;

    private final boolean prosperityFundEnabled;
    private final boolean prosperityFundEndowmentsEnabled;
    private final boolean prosperityFundProjectSponsorshipEnabled;
    private final boolean prosperityFundTargetedDonationsEnabled;
    private final boolean prosperityFundDonorRecognitionEnabled;
    private final int prosperityFundEndowmentAnnualPayoutBps;
    private final int prosperityFundMinimumEmergencyReservePercent;
    private final int prosperityFundMaximumMonthlySpending;

    private EmeraldConfig(
            boolean villageBanksEnabled,
            int villageScanIntervalTicks,
            int villageRegionSize,
            int bankerRestrictionRadius,
            int transactionCooldownTicks,
            boolean onboardingJoinHintEnabled,
            boolean villageProsperitySimulationEnabled,
            boolean villageVisualProgressionEnabled,
            boolean villageMarketIntegrationEnabled,
            boolean villageAutomaticRecoveryEnabled,
            int villageProsperityScanIntervalTicks,
            int villageDevelopmentRadius,
            int villageConstructionIntervalTicks,
            int villageConstructionBlocksPerTick,
            int villageSettlerSpawnIntervalTicks,
            boolean prosperityFundEnabled,
            boolean prosperityFundEndowmentsEnabled,
            boolean prosperityFundProjectSponsorshipEnabled,
            boolean prosperityFundTargetedDonationsEnabled,
            boolean prosperityFundDonorRecognitionEnabled,
            int prosperityFundEndowmentAnnualPayoutBps,
            int prosperityFundMinimumEmergencyReservePercent,
            int prosperityFundMaximumMonthlySpending) {
        this.villageBanksEnabled = villageBanksEnabled;
        this.villageScanIntervalTicks = villageScanIntervalTicks;
        this.villageRegionSize = villageRegionSize;
        this.bankerRestrictionRadius = bankerRestrictionRadius;
        this.transactionCooldownTicks = transactionCooldownTicks;
        this.onboardingJoinHintEnabled = onboardingJoinHintEnabled;
        this.villageProsperitySimulationEnabled = villageProsperitySimulationEnabled;
        this.villageVisualProgressionEnabled = villageVisualProgressionEnabled;
        this.villageMarketIntegrationEnabled = villageMarketIntegrationEnabled;
        this.villageAutomaticRecoveryEnabled = villageAutomaticRecoveryEnabled;
        this.villageProsperityScanIntervalTicks = villageProsperityScanIntervalTicks;
        this.villageDevelopmentRadius = villageDevelopmentRadius;
        this.villageConstructionIntervalTicks = villageConstructionIntervalTicks;
        this.villageConstructionBlocksPerTick = villageConstructionBlocksPerTick;
        this.villageSettlerSpawnIntervalTicks = villageSettlerSpawnIntervalTicks;
        this.prosperityFundEnabled = prosperityFundEnabled;
        this.prosperityFundEndowmentsEnabled = prosperityFundEndowmentsEnabled;
        this.prosperityFundProjectSponsorshipEnabled = prosperityFundProjectSponsorshipEnabled;
        this.prosperityFundTargetedDonationsEnabled = prosperityFundTargetedDonationsEnabled;
        this.prosperityFundDonorRecognitionEnabled = prosperityFundDonorRecognitionEnabled;
        this.prosperityFundEndowmentAnnualPayoutBps = prosperityFundEndowmentAnnualPayoutBps;
        this.prosperityFundMinimumEmergencyReservePercent =
                prosperityFundMinimumEmergencyReservePercent;
        this.prosperityFundMaximumMonthlySpending = prosperityFundMaximumMonthlySpending;
    }

    public static synchronized EmeraldConfig load(Path worldDataDirectory) throws IOException {
        Path candidatePath = worldDataDirectory.resolve(FILE_NAME);
        if (!Files.exists(candidatePath)) {
            writeDefaults(candidatePath);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(candidatePath)) {
            properties.load(input);
        }
        Set<String> unknownKeys = new TreeSet<>(properties.stringPropertyNames());
        unknownKeys.removeAll(KNOWN_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw new IOException("Unknown configuration key(s): "
                    + String.join(", ", unknownKeys));
        }
        EmeraldConfig candidate = new EmeraldConfig(
                bool(properties, "village_banks.enabled", true),
                bounded(properties, "village_banks.scan_interval_ticks", 200, 20, 12_000),
                bounded(properties, "village_banks.region_size", 256, 128, 2_048),
                bounded(properties, "banker.restriction_radius", 5, 2, 32),
                bounded(properties, "transactions.cooldown_ticks", 5, 0, 200),
                bool(properties, "onboarding.join_hint_enabled", true),
                bool(properties, "village_prosperity.simulation_enabled", true),
                bool(properties, "village_prosperity.visual_progression_enabled", true),
                bool(properties, "village_prosperity.market_integration_enabled", true),
                bool(properties, "village_prosperity.automatic_recovery_enabled", true),
                bounded(properties, "village_prosperity.scan_interval_ticks", 400, 40, 24_000),
                bounded(properties, "village_prosperity.development_radius", 96, 48, 192),
                bounded(properties, "village_prosperity.construction_interval_ticks", 10, 1, 200),
                bounded(properties, "village_prosperity.construction_blocks_per_tick", 2, 1, 64),
                bounded(properties, "village_prosperity.settler_spawn_interval_ticks", 1_200, 200, 24_000),
                bool(properties, "village_prosperity.donations_enabled", true),
                bool(properties, "village_prosperity.endowments_enabled", true),
                bool(properties, "village_prosperity.project_sponsorship_enabled", true),
                bool(properties, "village_prosperity.targeted_donations_enabled", true),
                bool(properties, "village_prosperity.donor_recognition_enabled", true),
                bounded(properties, "village_prosperity.endowment_annual_payout_bps", 400, 0, 10_000),
                bounded(properties, "village_prosperity.minimum_emergency_reserve_percent", 20, 0, 90),
                bounded(properties, "village_prosperity.max_monthly_treasury_spending", 24, 1, 1_000_000));
        currentPath = candidatePath;
        current = candidate;
        return candidate;
    }

    public static synchronized EmeraldConfig reload() throws IOException {
        if (currentPath == null) {
            throw new IOException("The Emerald Standard configuration has not been initialized");
        }
        return load(currentPath.getParent());
    }

    public static EmeraldConfig current() {
        return current;
    }

    public static String location() {
        Path path = currentPath;
        return path == null ? "not initialized" : path.toAbsolutePath().normalize().toString();
    }

    public boolean villageBanksEnabled() {
        return villageBanksEnabled;
    }

    public int villageScanIntervalTicks() {
        return villageScanIntervalTicks;
    }

    public int villageRegionSize() {
        return villageRegionSize;
    }

    public int bankerRestrictionRadius() {
        return bankerRestrictionRadius;
    }

    public int transactionCooldownTicks() {
        return transactionCooldownTicks;
    }

    public boolean onboardingJoinHintEnabled() {
        return onboardingJoinHintEnabled;
    }

    public boolean villageProsperitySimulationEnabled() {
        return villageProsperitySimulationEnabled;
    }

    public boolean villageVisualProgressionEnabled() {
        return villageVisualProgressionEnabled;
    }

    public boolean villageMarketIntegrationEnabled() {
        return villageMarketIntegrationEnabled;
    }

    public boolean villageAutomaticRecoveryEnabled() {
        return villageAutomaticRecoveryEnabled;
    }

    public int villageProsperityScanIntervalTicks() {
        return villageProsperityScanIntervalTicks;
    }

    public int villageDevelopmentRadius() {
        return villageDevelopmentRadius;
    }

    public int villageConstructionIntervalTicks() {
        return villageConstructionIntervalTicks;
    }

    public int villageConstructionBlocksPerTick() {
        return villageConstructionBlocksPerTick;
    }

    public int villageSettlerSpawnIntervalTicks() {
        return villageSettlerSpawnIntervalTicks;
    }

    public boolean prosperityFundEnabled() {
        return prosperityFundEnabled;
    }

    public boolean prosperityFundEndowmentsEnabled() {
        return prosperityFundEndowmentsEnabled;
    }

    public boolean prosperityFundProjectSponsorshipEnabled() {
        return prosperityFundProjectSponsorshipEnabled;
    }

    public boolean prosperityFundTargetedDonationsEnabled() {
        return prosperityFundTargetedDonationsEnabled;
    }

    public boolean prosperityFundDonorRecognitionEnabled() {
        return prosperityFundDonorRecognitionEnabled;
    }

    public double prosperityFundEndowmentAnnualPayoutRate() {
        return prosperityFundEndowmentAnnualPayoutBps / 10_000.0;
    }

    public double prosperityFundEmergencyReserveFraction() {
        return prosperityFundMinimumEmergencyReservePercent / 100.0;
    }

    public int prosperityFundMaximumMonthlySpending() {
        return prosperityFundMaximumMonthlySpending;
    }

    /** Applies every simulation option atomically to the shared economy service. */
    public void applyTo(EconomyService economy) {
        economy.configureVillageProsperity(
                villageProsperitySimulationEnabled,
                villageVisualProgressionEnabled,
                villageMarketIntegrationEnabled,
                villageAutomaticRecoveryEnabled);
        long dailyCapMicro = Math.max(
                1L,
                Math.round(prosperityFundMaximumMonthlySpending
                        * (double) EconomyState.MICRO / 30.0));
        economy.configureProsperityFund(new EconomyService.ProsperityFundPolicy(
                prosperityFundEnabled && villageProsperitySimulationEnabled,
                prosperityFundEndowmentAnnualPayoutRate(),
                prosperityFundEmergencyReserveFraction(),
                dailyCapMicro));
    }

    public String summary() {
        return String.format(
                Locale.ROOT,
                "village banks=%s, bank scan=%d ticks, bank region=%d blocks, banker radius=%d, "
                        + "transaction cooldown=%d ticks, first-join hint=%s, prosperity simulation=%s, visual progression=%s, "
                        + "market integration=%s, automatic recovery=%s, prosperity scan=%d ticks, "
                        + "development radius=%d, construction=%d block(s)/%d tick(s), "
                        + "settler interval=%d ticks, prosperity fund=%s, endowments=%s, "
                        + "project sponsorship=%s, targeted donations=%s, donor recognition=%s, "
                        + "endowment payout=%.2f%%, emergency reserve=%d%%, monthly spending cap=%d",
                villageBanksEnabled,
                villageScanIntervalTicks,
                villageRegionSize,
                bankerRestrictionRadius,
                transactionCooldownTicks,
                onboardingJoinHintEnabled,
                villageProsperitySimulationEnabled,
                villageVisualProgressionEnabled,
                villageMarketIntegrationEnabled,
                villageAutomaticRecoveryEnabled,
                villageProsperityScanIntervalTicks,
                villageDevelopmentRadius,
                villageConstructionBlocksPerTick,
                villageConstructionIntervalTicks,
                villageSettlerSpawnIntervalTicks,
                prosperityFundEnabled,
                prosperityFundEndowmentsEnabled,
                prosperityFundProjectSponsorshipEnabled,
                prosperityFundTargetedDonationsEnabled,
                prosperityFundDonorRecognitionEnabled,
                prosperityFundEndowmentAnnualPayoutBps / 100.0,
                prosperityFundMinimumEmergencyReservePercent,
                prosperityFundMaximumMonthlySpending);
    }

    private static EmeraldConfig defaults() {
        return new EmeraldConfig(
                true,
                200,
                256,
                5,
                5,
                true,
                true,
                true,
                true,
                true,
                400,
                96,
                10,
                2,
                1_200,
                true,
                true,
                true,
                true,
                true,
                400,
                20,
                24);
    }

    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("village_banks.enabled", "true");
        properties.setProperty("village_banks.scan_interval_ticks", "200");
        properties.setProperty("village_banks.region_size", "256");
        properties.setProperty("banker.restriction_radius", "5");
        properties.setProperty("transactions.cooldown_ticks", "5");
        properties.setProperty("onboarding.join_hint_enabled", "true");
        properties.setProperty("village_prosperity.simulation_enabled", "true");
        properties.setProperty("village_prosperity.visual_progression_enabled", "true");
        properties.setProperty("village_prosperity.market_integration_enabled", "true");
        properties.setProperty("village_prosperity.automatic_recovery_enabled", "true");
        properties.setProperty("village_prosperity.scan_interval_ticks", "400");
        properties.setProperty("village_prosperity.development_radius", "96");
        properties.setProperty("village_prosperity.construction_interval_ticks", "10");
        properties.setProperty("village_prosperity.construction_blocks_per_tick", "2");
        properties.setProperty("village_prosperity.settler_spawn_interval_ticks", "1200");
        properties.setProperty("village_prosperity.donations_enabled", "true");
        properties.setProperty("village_prosperity.endowments_enabled", "true");
        properties.setProperty("village_prosperity.project_sponsorship_enabled", "true");
        properties.setProperty("village_prosperity.targeted_donations_enabled", "true");
        properties.setProperty("village_prosperity.donor_recognition_enabled", "true");
        properties.setProperty("village_prosperity.endowment_annual_payout_bps", "400");
        properties.setProperty("village_prosperity.minimum_emergency_reserve_percent", "20");
        properties.setProperty("village_prosperity.max_monthly_treasury_spending", "24");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(
                    output,
                    "The Emerald Standard world configuration. See docs/CONFIGURATION.md for bounds.");
        }
        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean bool(
            Properties properties, String key, boolean fallback) throws IOException {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if (raw.trim().equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.trim().equalsIgnoreCase("false")) {
            return false;
        }
        throw new IOException("Configuration " + key + " must be true or false");
    }

    private static int bounded(
            Properties properties,
            String key,
            int fallback,
            int minimum,
            int maximum) throws IOException {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) {
                throw new IOException(
                        "Configuration " + key + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Configuration " + key + " is not an integer", exception);
        }
    }
}
