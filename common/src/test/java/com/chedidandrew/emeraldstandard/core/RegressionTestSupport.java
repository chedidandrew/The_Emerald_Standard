package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;

final class RegressionTestSupport {
    static final UUID PLAYER = UUID.fromString(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private RegressionTestSupport() {
    }

    static Properties baseProperties(int format) {
        EconomyState fresh = EconomyState.fresh(77L, 0L, 0L);
        Properties properties = new Properties();
        properties.setProperty("format", Integer.toString(format));
        properties.setProperty("seed", Long.toString(fresh.seed));
        properties.setProperty("day", Long.toString(fresh.economicDay));
        properties.setProperty("wall", "0");
        properties.setProperty("ticks", "0");
        properties.setProperty("regime", fresh.regime.name());
        if (format >= 3) {
            properties.setProperty("pending.wall_ms", "0");
            properties.setProperty("pending.game_ticks", "0");
        }
        fresh.prices.forEach((ticker, price) ->
                properties.setProperty("price." + ticker, Double.toString(price)));
        fresh.commodityPrices.forEach((commodity, price) ->
                properties.setProperty("commodity." + commodity, Double.toString(price)));
        return properties;
    }

    static void writeProperties(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        try (var output = Files.newOutputStream(path)) {
            properties.store(output, "regression test");
        }
    }

    static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    static void requireValidationFailure(EconomyState state, String message) throws Exception {
        boolean failed = false;
        try {
            state.validate();
        } catch (IOException expected) {
            failed = true;
        }
        require(failed, message);
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort test cleanup.
                }
            });
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
