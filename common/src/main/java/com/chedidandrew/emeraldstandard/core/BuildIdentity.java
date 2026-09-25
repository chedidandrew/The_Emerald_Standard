package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.Properties;

/** Artifact provenance, never a save-format or network-compatibility claim. */
public final class BuildIdentity {
    private static final Properties INFO = read();
    private BuildIdentity() {}
    private static Properties read() {
        Properties p = new Properties();
        try (var input = BuildIdentity.class.getResourceAsStream("/tes-build.properties")) {
            if (input != null) p.load(input);
        } catch (IOException | IllegalArgumentException ignored) { p.clear(); }
        return p;
    }
    public static String version() { return INFO.getProperty("version", "development"); }
    public static String fingerprint() { return INFO.getProperty("sourceSha256", "unavailable"); }
    public static String display() {
        String hash = fingerprint();
        return version() + " / " + hash.substring(0, Math.min(12, hash.length()));
    }
}
