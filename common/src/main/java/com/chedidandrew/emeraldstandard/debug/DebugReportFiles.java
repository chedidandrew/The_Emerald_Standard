package com.chedidandrew.emeraldstandard.debug;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Loader-neutral, regression-testable file policy for diagnostic reports. */
public final class DebugReportFiles {
    public static final int MAX_EVENTS = 50_000;
    public static final long MAX_TIMELINE_BYTES = 25L * 1024L * 1024L;
    public static final int RETAINED_REPORTS = 5;

    private static final String ACTIVE_PREFIX = ".active-";

    private DebugReportFiles() {
    }

    public static boolean atTimelineLimit(long eventCount, long timelineBytes) {
        return eventCount >= MAX_EVENTS || timelineBytes >= MAX_TIMELINE_BYTES;
    }

    public static boolean wouldExceedTimelineLimit(
            long eventCount, long timelineBytes, long additionalBytes) {
        if (eventCount >= MAX_EVENTS || additionalBytes < 0L) {
            return true;
        }
        return timelineBytes > MAX_TIMELINE_BYTES - additionalBytes;
    }

    /** Packages crash-interrupted active folders and returns the recovered report paths. */
    public static List<Path> recoverInterruptedCaptures(Path root) throws IOException {
        List<Path> recovered = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return recovered;
        }
        List<Path> activeDirectories;
        try (var stream = Files.list(root)) {
            activeDirectories = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(ACTIVE_PREFIX))
                    .sorted()
                    .toList();
        }
        for (Path directory : activeDirectories) {
            String id = directory.getFileName().toString().substring(ACTIVE_PREFIX.length());
            Files.writeString(
                    directory.resolve("INCOMPLETE-CRASH.txt"),
                    "This capture was interrupted before a normal stop, most likely by a crash or forced process exit.\n"
                            + "The incremental timeline remains valid up to the last flushed event.\n");
            Path destination = root.resolve("TES-debug-" + id + "-INCOMPLETE-CRASH.zip");
            zipDirectory(directory, destination);
            deleteTree(directory);
            recovered.add(destination);
        }
        return List.copyOf(recovered);
    }

    public static void rotateReports(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> reports;
        try (var stream = Files.list(root)) {
            reports = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("TES-debug-"))
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(DebugReportFiles::lastModified).reversed())
                    .toList();
        }
        for (int index = RETAINED_REPORTS; index < reports.size(); index++) {
            Files.deleteIfExists(reports.get(index));
        }
    }

    public static void zipDirectory(Path directory, Path destination) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary));
                    var stream = Files.walk(directory)) {
                for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                    String name = directory.relativize(path).toString().replace('\\', '/');
                    output.putNextEntry(new ZipEntry(name));
                    Files.copy(path, output);
                    output.closeEntry();
                }
            }
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                    throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Copies only explicitly public configuration keys, protecting future secret settings. */
    public static void copyAllowedProperties(
            Path source, Path destination, Set<String> allowedKeys) throws IOException {
        Properties sourceProperties = new Properties();
        try (InputStream input = Files.newInputStream(source)) {
            sourceProperties.load(input);
        }
        Properties sanitized = new Properties();
        allowedKeys.stream()
                .sorted()
                .filter(sourceProperties::containsKey)
                .forEach(key -> sanitized.setProperty(key, sourceProperties.getProperty(key)));
        try (OutputStream output = Files.newOutputStream(destination)) {
            sanitized.store(output, "Sanitized The Emerald Standard world configuration");
        }
    }

    /** Uses build metadata instead of a source-code version literal. */
    public static String runtimeVersion(Class<?> anchor) {
        String override = System.getProperty("the_emerald_standard.version", "").trim();
        if (!override.isEmpty()) {
            return override;
        }
        Package anchorPackage = anchor == null ? null : anchor.getPackage();
        String implementationVersion = anchorPackage == null
                ? null
                : anchorPackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "development"
                : implementationVersion.trim();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }
}
