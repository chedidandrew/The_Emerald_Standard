package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Verifies backward-compatible market and economic-clock world configuration. */
public final class EmeraldConfigRegressionTest {
    private static final String FILE_NAME = "the_emerald_standard-config.properties";

    private EmeraldConfigRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        require(EmeraldConfig.current().marketEventsEnabled()
                        && EmeraldConfig.current().offlineProgressionEnabled()
                        && EmeraldConfig.current().maximumOfflineDays()
                                == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS,
                "Pre-load configuration defaults did not preserve legacy behavior");
        Path directory = Files.createTempDirectory("emerald-config-regression");
        EmeraldConfig defaults = EmeraldConfig.load(directory);
        require(defaults.marketEventsEnabled()
                        && defaults.offlineProgressionEnabled()
                        && defaults.maximumOfflineDays()
                                == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS,
                "New world did not receive protected market and clock defaults");

        Path path = directory.resolve(FILE_NAME);
        Properties properties = read(path);
        require(properties.containsKey("market.events_enabled")
                        && properties.containsKey("economic_clock.offline_progression_enabled")
                        && properties.containsKey("economic_clock.max_offline_days"),
                "Generated world configuration omitted market or clock controls");

        properties.remove("market.events_enabled");
        properties.remove("economic_clock.offline_progression_enabled");
        properties.remove("economic_clock.max_offline_days");
        write(path, properties);
        EmeraldConfig legacy = EmeraldConfig.load(directory);
        require(legacy.marketEventsEnabled()
                        && legacy.offlineProgressionEnabled()
                        && legacy.maximumOfflineDays()
                                == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS,
                "Existing configuration without new keys did not retain legacy behavior");

        properties.setProperty("market.events_enabled", "false");
        properties.setProperty("economic_clock.offline_progression_enabled", "false");
        properties.setProperty("economic_clock.max_offline_days", "3");
        write(path, properties);
        EmeraldConfig configured = EmeraldConfig.load(directory);
        require(!configured.marketEventsEnabled()
                        && !configured.offlineProgressionEnabled()
                        && configured.maximumOfflineDays() == 3,
                "Custom market or clock configuration was not loaded");
        EconomyService service = new EconomyService();
        configured.applyTo(service);
        require(!service.marketEventsEnabled()
                        && !service.offlineProgressionEnabled()
                        && service.maximumOfflineDays() == 3L,
                "World configuration was not applied to the economy service");
        require(configured.summary().contains("market events=false")
                        && configured.summary().contains("offline progression=false")
                        && configured.summary().contains("max offline=3 days")
                        && configured.summary().contains("prosperity simulation=true"),
                "Configuration summary misreported market, clock, or adjacent settings");

        properties.setProperty("economic_clock.max_offline_days", "0");
        write(path, properties);
        requireThrows(() -> EmeraldConfig.load(directory),
                "Invalid maximum offline days were accepted");
        require(EmeraldConfig.current() == configured,
                "Rejected configuration partially replaced the active settings");

        properties.setProperty(
                "economic_clock.max_offline_days",
                Long.toString(EconomyService.MAX_TRUSTED_CATCH_UP_DAYS + 1L));
        write(path, properties);
        requireThrows(() -> EmeraldConfig.load(directory),
                "Maximum offline days above the absolute cap were accepted");
        require(EmeraldConfig.current() == configured,
                "Upper-bound rejection partially replaced the active settings");

        System.out.println("PASS market and economic-clock configuration regression tests");
    }

    private static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void write(Path path, Properties properties) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "configuration regression fixture");
        }
    }

    private static void requireThrows(IoRunnable action, String message) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws Exception;
    }
}
