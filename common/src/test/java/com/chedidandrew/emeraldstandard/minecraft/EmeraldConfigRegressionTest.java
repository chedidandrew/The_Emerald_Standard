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
                                == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS
                        && EmeraldConfig.current().villageDevelopmentRadius()
                                == EmeraldConfig.DEFAULT_VILLAGE_DEVELOPMENT_RADIUS,
                "Pre-load configuration defaults did not preserve legacy behavior");
        Path directory = Files.createTempDirectory("emerald-config-regression");
        EmeraldConfig defaults = EmeraldConfig.load(directory);
        require(defaults.marketEventsEnabled()
                        && defaults.offlineProgressionEnabled()
                        && defaults.maximumOfflineDays()
                                == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS
                        && defaults.villageDevelopmentRadius()
                                == EmeraldConfig.DEFAULT_VILLAGE_DEVELOPMENT_RADIUS,
                "New world did not receive protected market and clock defaults");

        Path path = directory.resolve(FILE_NAME);
        Properties properties = read(path);
        require(properties.containsKey("market.events_enabled")
                        && properties.containsKey("economic_clock.offline_progression_enabled")
                        && properties.containsKey("economic_clock.max_offline_days")
                        && Integer.toString(EmeraldConfig.DEFAULT_VILLAGE_DEVELOPMENT_RADIUS)
                                .equals(properties.getProperty(
                                        "village_prosperity.development_radius")),
                "Generated world configuration omitted or misstated protected defaults");

        properties.remove("market.events_enabled");
        properties.remove("economic_clock.offline_progression_enabled");
        properties.remove("economic_clock.max_offline_days");
        properties.remove("village_prosperity.development_radius");
        write(path, properties);
        EmeraldConfig legacy = EmeraldConfig.load(directory);
        require(legacy.marketEventsEnabled()
                        && legacy.offlineProgressionEnabled()
                        && legacy.maximumOfflineDays()
                                == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS
                        && legacy.villageDevelopmentRadius()
                                == EmeraldConfig.DEFAULT_VILLAGE_DEVELOPMENT_RADIUS,
                "Existing configuration without new keys did not retain legacy behavior");

        properties.setProperty("market.events_enabled", "false");
        properties.setProperty("economic_clock.offline_progression_enabled", "false");
        properties.setProperty("economic_clock.max_offline_days", "3");
        properties.setProperty(
                "village_prosperity.development_radius",
                Integer.toString(EmeraldConfig.MIN_VILLAGE_DEVELOPMENT_RADIUS));
        write(path, properties);
        EmeraldConfig minimumRadius = EmeraldConfig.load(directory);
        require(!minimumRadius.marketEventsEnabled()
                        && !minimumRadius.offlineProgressionEnabled()
                        && minimumRadius.maximumOfflineDays() == 3
                        && minimumRadius.villageDevelopmentRadius()
                                == EmeraldConfig.MIN_VILLAGE_DEVELOPMENT_RADIUS,
                "Custom market or clock configuration was not loaded");

        properties.setProperty(
                "village_prosperity.development_radius",
                Integer.toString(EmeraldConfig.MAX_VILLAGE_DEVELOPMENT_RADIUS));
        write(path, properties);
        EmeraldConfig configured = EmeraldConfig.load(directory);
        require(configured.villageDevelopmentRadius()
                        == EmeraldConfig.MAX_VILLAGE_DEVELOPMENT_RADIUS,
                "Maximum village development radius was not accepted");
        EconomyService service = new EconomyService();
        configured.applyTo(service);
        require(!service.marketEventsEnabled()
                        && !service.offlineProgressionEnabled()
                        && service.maximumOfflineDays() == 3L,
                "World configuration was not applied to the economy service");
        require(configured.summary().contains("market events=false")
                        && configured.summary().contains("offline progression=false")
                        && configured.summary().contains("max offline=3 days")
                        && configured.summary().contains("prosperity simulation=true")
                        && configured.summary().contains("development radius=512"),
                "Configuration summary misreported market, clock, or adjacent settings");

        properties.setProperty(
                "village_prosperity.development_radius",
                Integer.toString(EmeraldConfig.MIN_VILLAGE_DEVELOPMENT_RADIUS - 1));
        write(path, properties);
        requireThrows(() -> EmeraldConfig.load(directory),
                "Village development radius below the minimum was accepted");
        require(EmeraldConfig.current() == configured,
                "Rejected lower development radius partially replaced the active settings");

        properties.setProperty(
                "village_prosperity.development_radius",
                Integer.toString(EmeraldConfig.MAX_VILLAGE_DEVELOPMENT_RADIUS + 1));
        write(path, properties);
        requireThrows(() -> EmeraldConfig.load(directory),
                "Village development radius above the maximum was accepted");
        require(EmeraldConfig.current() == configured,
                "Rejected upper development radius partially replaced the active settings");

        properties.setProperty(
                "village_prosperity.development_radius",
                Integer.toString(EmeraldConfig.MAX_VILLAGE_DEVELOPMENT_RADIUS));

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
