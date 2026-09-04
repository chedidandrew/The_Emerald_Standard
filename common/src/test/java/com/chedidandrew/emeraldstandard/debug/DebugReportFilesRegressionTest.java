package com.chedidandrew.emeraldstandard.debug;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Regression checks for debug report packaging, recovery, privacy, ownership, and limits. */
public final class DebugReportFilesRegressionTest {
    private DebugReportFilesRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("tes-debug-report-test-");
        try {
            verifyZipAndCrashRecovery(root);
            verifyRetention(root);
            verifyConfigPrivacy(root);
            verifyLimitsAndScope();
            verifyRuntimeVersionOverride();
            System.out.println("PASS DebugReportFilesRegressionTest");
        } finally {
            DebugReportFiles.deleteTree(root);
        }
    }

    private static void verifyZipAndCrashRecovery(Path root) throws Exception {
        Path source = Files.createDirectories(root.resolve("zip-source"));
        Files.writeString(source.resolve("timeline.jsonl"), "{\"event\":\"started\"}\n");
        Files.createDirectories(source.resolve("nested"));
        Files.writeString(source.resolve("nested/marker.txt"), "marker\n");
        Path direct = root.resolve("TES-debug-direct.zip");
        DebugReportFiles.zipDirectory(source, direct);
        require(zipEntries(direct).equals(List.of("nested/marker.txt", "timeline.jsonl")),
                "ZIP entries must be relative, normalized, and deterministic");

        Path active = Files.createDirectories(root.resolve(".active-RECOVERY"));
        Files.writeString(active.resolve("timeline.jsonl"), "{\"event\":\"last-flush\"}\n");
        List<Path> recovered = DebugReportFiles.recoverInterruptedCaptures(root);
        require(recovered.size() == 1, "one interrupted capture must be recovered");
        require(!Files.exists(active), "successfully recovered active folder must be removed");
        try (ZipFile zip = new ZipFile(recovered.get(0).toFile())) {
            require(zip.getEntry("timeline.jsonl") != null,
                    "recovered report must retain the incremental timeline");
            require(zip.getEntry("INCOMPLETE-CRASH.txt") != null,
                    "recovered report must be visibly marked incomplete");
        }
    }

    private static void verifyRetention(Path root) throws Exception {
        Path retentionRoot = Files.createDirectories(root.resolve("retention"));
        for (int index = 0; index < 7; index++) {
            Path report = retentionRoot.resolve(
                    String.format("TES-debug-retention-%02d.zip", index));
            Files.write(report, new byte[] {(byte) index});
            Files.setLastModifiedTime(report, FileTime.fromMillis(10_000L + index));
        }
        Path unrelated = retentionRoot.resolve("unrelated.zip");
        Files.write(unrelated, new byte[] {1});
        DebugReportFiles.rotateReports(retentionRoot);
        try (var stream = Files.list(retentionRoot)) {
            long retained = stream
                    .filter(path -> path.getFileName().toString().startsWith("TES-debug-retention-"))
                    .count();
            require(retained == DebugReportFiles.RETAINED_REPORTS,
                    "rotation must retain exactly the newest configured report count");
        }
        require(Files.exists(retentionRoot.resolve("TES-debug-retention-06.zip")),
                "rotation must retain the newest report");
        require(!Files.exists(retentionRoot.resolve("TES-debug-retention-00.zip")),
                "rotation must delete the oldest report");
        require(Files.exists(unrelated), "rotation must not delete unrelated ZIP files");
    }

    private static void verifyConfigPrivacy(Path root) throws Exception {
        Path source = root.resolve("config-source.properties");
        Properties properties = new Properties();
        properties.setProperty("village_banks.enabled", "true");
        properties.setProperty("server.authentication_token", "must-not-leak");
        try (var output = Files.newOutputStream(source)) {
            properties.store(output, "test");
        }
        Path sanitized = root.resolve("config-sanitized.properties");
        DebugReportFiles.copyAllowedProperties(
                source, sanitized, Set.of("village_banks.enabled"));
        Properties copied = new Properties();
        try (InputStream input = Files.newInputStream(sanitized)) {
            copied.load(input);
        }
        require("true".equals(copied.getProperty("village_banks.enabled")),
                "allowlisted public setting must remain");
        require(!copied.containsKey("server.authentication_token"),
                "non-allowlisted future secrets must be excluded");
        require(!Files.readString(sanitized).contains("must-not-leak"),
                "secret values must not appear anywhere in sanitized output");
    }

    private static void verifyLimitsAndScope() {
        require(!DebugReportFiles.atTimelineLimit(
                        DebugReportFiles.MAX_EVENTS - 1L,
                        DebugReportFiles.MAX_TIMELINE_BYTES - 1L),
                "values immediately below both limits must remain writable");
        require(DebugReportFiles.atTimelineLimit(DebugReportFiles.MAX_EVENTS, 0L),
                "event limit must be inclusive");
        require(DebugReportFiles.atTimelineLimit(0L, DebugReportFiles.MAX_TIMELINE_BYTES),
                "byte limit must be inclusive");
        require(DebugReportFiles.wouldExceedTimelineLimit(
                        0L, DebugReportFiles.MAX_TIMELINE_BYTES - 2L, 3L),
                "a write crossing the byte ceiling must be rejected");

        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID village = UUID.randomUUID();
        require(DebugCapturePolicy.isOwner(owner, owner), "capture owner must be authorized");
        require(!DebugCapturePolicy.isOwner(owner, other),
                "another operator must not control the capture");
        require(DebugCapturePolicy.isWatchedVillage(village, village),
                "the watched village must be included");
        require(!DebugCapturePolicy.isWatchedVillage(village, UUID.randomUUID()),
                "an unrelated village must be excluded");
        require(DebugCapturePolicy.isResponsibleTester(owner, owner),
                "the tester's responsibility may be reported as a boolean");
        require(!DebugCapturePolicy.isResponsibleTester(owner, other),
                "another player's identity must not be attributed or exposed");
    }

    private static void verifyRuntimeVersionOverride() {
        String key = "the_emerald_standard.version";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "test-version");
            require("test-version".equals(DebugReportFiles.runtimeVersion(
                            DebugReportFilesRegressionTest.class)),
                    "explicit development version metadata must be honored");
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static List<String> zipEntries(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.stream().map(entry -> entry.getName()).sorted().toList();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
