package com.chedidandrew.emeraldstandard.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

/** Small world-local configuration with conservative bounds and atomic replacement. */
public final class EmeraldConfig {
    private static final String FILE_NAME = "the_emerald_standard-config.properties";
    private static volatile EmeraldConfig current = defaults();
    private static volatile Path currentPath;

    private final boolean villageBanksEnabled;
    private final int villageScanIntervalTicks;
    private final int villageRegionSize;
    private final int bankerRestrictionRadius;
    private final int transactionCooldownTicks;

    private final boolean villageProsperitySimulationEnabled;
    private final boolean villageVisualProgressionEnabled;
    private final int villageProsperityScanIntervalTicks;
    private final int villageDevelopmentRadius;
    private final int villageConstructionIntervalTicks;
    private final int villageConstructionBlocksPerTick;
    private final int villageSettlerSpawnIntervalTicks;

    private EmeraldConfig(
            boolean villageBanksEnabled,
            int villageScanIntervalTicks,
            int villageRegionSize,
            int bankerRestrictionRadius,
            int transactionCooldownTicks,
            boolean villageProsperitySimulationEnabled,
            boolean villageVisualProgressionEnabled,
            int villageProsperityScanIntervalTicks,
            int villageDevelopmentRadius,
            int villageConstructionIntervalTicks,
            int villageConstructionBlocksPerTick,
            int villageSettlerSpawnIntervalTicks) {
        this.villageBanksEnabled = villageBanksEnabled;
        this.villageScanIntervalTicks = villageScanIntervalTicks;
        this.villageRegionSize = villageRegionSize;
        this.bankerRestrictionRadius = bankerRestrictionRadius;
        this.transactionCooldownTicks = transactionCooldownTicks;
        this.villageProsperitySimulationEnabled = villageProsperitySimulationEnabled;
        this.villageVisualProgressionEnabled = villageVisualProgressionEnabled;
        this.villageProsperityScanIntervalTicks = villageProsperityScanIntervalTicks;
        this.villageDevelopmentRadius = villageDevelopmentRadius;
        this.villageConstructionIntervalTicks = villageConstructionIntervalTicks;
        this.villageConstructionBlocksPerTick = villageConstructionBlocksPerTick;
        this.villageSettlerSpawnIntervalTicks = villageSettlerSpawnIntervalTicks;
    }

    public static synchronized EmeraldConfig load(Path worldDataDirectory) throws IOException {
        currentPath = worldDataDirectory.resolve(FILE_NAME);
        if (!Files.exists(currentPath)) {
            writeDefaults(currentPath);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(currentPath)) {
            properties.load(input);
        }
        current = new EmeraldConfig(
                bool(properties, "village_banks.enabled", true),
                bounded(properties, "village_banks.scan_interval_ticks", 200, 20, 12_000),
                bounded(properties, "village_banks.region_size", 256, 128, 2_048),
                bounded(properties, "banker.restriction_radius", 5, 2, 32),
                bounded(properties, "transactions.cooldown_ticks", 5, 0, 200),
                bool(properties, "village_prosperity.simulation_enabled", true),
                bool(properties, "village_prosperity.visual_progression_enabled", true),
                bounded(properties, "village_prosperity.scan_interval_ticks", 400, 40, 24_000),
                bounded(properties, "village_prosperity.development_radius", 96, 48, 192),
                bounded(properties, "village_prosperity.construction_interval_ticks", 4, 1, 200),
                bounded(properties, "village_prosperity.construction_blocks_per_tick", 4, 1, 64),
                bounded(properties, "village_prosperity.settler_spawn_interval_ticks", 1_200, 200, 24_000));
        return current;
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

    public boolean villageProsperitySimulationEnabled() {
        return villageProsperitySimulationEnabled;
    }

    public boolean villageVisualProgressionEnabled() {
        return villageVisualProgressionEnabled;
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

    public String summary() {
        return String.format(
                Locale.ROOT,
                "village banks=%s, bank scan=%d ticks, bank region=%d blocks, banker radius=%d, "
                        + "transaction cooldown=%d ticks, prosperity simulation=%s, visual progression=%s, "
                        + "prosperity scan=%d ticks, development radius=%d, construction=%d block(s)/%d tick(s), "
                        + "settler interval=%d ticks",
                villageBanksEnabled,
                villageScanIntervalTicks,
                villageRegionSize,
                bankerRestrictionRadius,
                transactionCooldownTicks,
                villageProsperitySimulationEnabled,
                villageVisualProgressionEnabled,
                villageProsperityScanIntervalTicks,
                villageDevelopmentRadius,
                villageConstructionBlocksPerTick,
                villageConstructionIntervalTicks,
                villageSettlerSpawnIntervalTicks);
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
                400,
                96,
                4,
                4,
                1_200);
    }

    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("village_banks.enabled", "true");
        properties.setProperty("village_banks.scan_interval_ticks", "200");
        properties.setProperty("village_banks.region_size", "256");
        properties.setProperty("banker.restriction_radius", "5");
        properties.setProperty("transactions.cooldown_ticks", "5");
        properties.setProperty("village_prosperity.simulation_enabled", "true");
        properties.setProperty("village_prosperity.visual_progression_enabled", "true");
        properties.setProperty("village_prosperity.scan_interval_ticks", "400");
        properties.setProperty("village_prosperity.development_radius", "96");
        properties.setProperty("village_prosperity.construction_interval_ticks", "4");
        properties.setProperty("village_prosperity.construction_blocks_per_tick", "4");
        properties.setProperty("village_prosperity.settler_spawn_interval_ticks", "1200");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "The Emerald Standard world configuration");
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
