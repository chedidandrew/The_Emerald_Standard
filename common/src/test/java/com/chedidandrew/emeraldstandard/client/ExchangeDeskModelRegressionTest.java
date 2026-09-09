package com.chedidandrew.emeraldstandard.client;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps the Exchange Desk visually sealed at floor level without restoring block-face culling. */
public final class ExchangeDeskModelRegressionTest {
    private ExchangeDeskModelRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0
                ? Path.of("").toAbsolutePath()
                : Path.of(args[0]).toAbsolutePath();
        String model = Files.readString(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/models/block/exchange_desk.json"));
        String compact = model.replaceAll("\\s+", "");

        String toeKick = element(compact, "toe_kick");
        check(toeKick.contains("\"from\":[0,0,0]"),
                "Exchange Desk toe-kick must begin at the full block origin");
        check(toeKick.contains("\"to\":[16,1,16]"),
                "Exchange Desk toe-kick must seal the complete one-pixel floor footprint");
        check(toeKick.contains("\"down\":{\"texture\":\"#bottom\"}"),
                "Exchange Desk toe-kick must have an opaque underside");
        for (String side : new String[] {"north", "south", "west", "east"}) {
            check(toeKick.contains("\"" + side + "\":{\"texture\":"),
                    "Exchange Desk toe-kick is missing its " + side + " face");
        }

        String plinth = element(compact, "plinth");
        check(plinth.contains("\"from\":[1,1,1]")
                        && plinth.contains("\"to\":[15,2,15]"),
                "Exchange Desk upper plinth must remain inset above the sealed toe-kick");
        System.out.println("PASS Exchange Desk model has a sealed full-footprint toe-kick");
    }

    private static String element(String model, String name) {
        String marker = "\"name\":\"" + name + "\"";
        int start = model.indexOf(marker);
        check(start >= 0, "Exchange Desk model is missing element " + name);
        int next = model.indexOf("\"name\":", start + marker.length());
        return next < 0 ? model.substring(start) : model.substring(start, next);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
