package com.chedidandrew.emeraldstandard.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Client-only file data; no Minecraft dependencies, world settings or network side effects. */
public final class ReaderPreferences {
    public static final int DEFAULT_PERCENT = 90;
    private ReaderPreferences() { }
    public static int normalize(int percent) {
        return Math.max(80, Math.min(120, Math.round(percent / 10.0f) * 10));
    }
    public static int load(Path path) throws IOException {
        if (!Files.exists(path)) return DEFAULT_PERCENT;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        try {
            return normalize(Integer.parseInt(properties.getProperty("handbook.text_percent", "90")));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid handbook.text_percent in client settings", exception);
        }
    }
    public static void save(Path path, int percent) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("handbook.text_percent", Integer.toString(normalize(percent)));
        Path temporary = Files.createTempFile(path.getParent(), "tes-reader-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "The Emerald Standard client reader preferences");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
