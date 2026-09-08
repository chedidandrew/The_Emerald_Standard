package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Canonical-Banker death retry, fail-closed, and restart checks. */
public final class BankerDeathRetryQueueRegressionTest {
    private static final long FIRST_REGION = 0x1133557799BBDDFFL;
    private static final long SECOND_REGION = 0x22446688AACCEE00L;
    private static final UUID FIRST_BANKER = UUID.fromString(
            "00000000-0000-0000-0000-00000000d001");
    private static final UUID SECOND_BANKER = UUID.fromString(
            "00000000-0000-0000-0000-00000000d002");

    private BankerDeathRetryQueueRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testBoundedRoundRobinQueue();
        testTransientSaveFailureRetriesDurably();
        testDurableDeathAuthorizesReplacementAfterRestart();
        testRestartRemainsConservativeAfterFailedDeathSave();
        System.out.println("PASS BankerDeathRetryQueueRegressionTest");
    }

    private static void testBoundedRoundRobinQueue() {
        BankerDeathRetryQueue reentrant = new BankerDeathRetryQueue(1);
        require(reentrant.offer(FIRST_REGION, FIRST_BANKER),
                "Reentrant fixture was not queued");
        require(reentrant.retry((regionKey, bankerId) -> {
                    require(!reentrant.offer(regionKey, SECOND_BANKER),
                            "Resolver reentrancy replaced exact pending death identity");
                    return false;
                }) == 0
                        && reentrant.remove(FIRST_REGION, FIRST_BANKER),
                "Reentrant resolver overwrote or dropped the original death proof");

        BankerDeathRetryQueue queue = new BankerDeathRetryQueue(1);
        require(queue.offer(FIRST_REGION, FIRST_BANKER),
                "First canonical death was not queued");
        require(queue.offer(FIRST_REGION, FIRST_BANKER) && queue.size() == 1,
                "Repeated delivery of one death was not idempotent");
        require(!queue.offer(FIRST_REGION, SECOND_BANKER),
                "A different identity replaced unresolved death proof");
        require(queue.offer(SECOND_REGION, SECOND_BANKER),
                "Second canonical death was not queued");
        for (int index = 0; index < 300; index++) {
            require(queue.offer(10_000L + index, new UUID(7L, index)),
                    "A canonical death was dropped during a prolonged save outage");
        }
        require(queue.size() == 302,
                "Death retry state was not deduplicated exactly once per region");

        List<Long> attempted = new ArrayList<>();
        require(queue.retry((regionKey, bankerId) -> {
                    attempted.add(regionKey);
                    return false;
                }) == 0
                        && attempted.equals(List.of(FIRST_REGION))
                        && queue.containsRegion(FIRST_REGION),
                "A failed retry was lost or exceeded the per-pass work bound");
        require(queue.retry((regionKey, bankerId) -> {
                    attempted.add(regionKey);
                    return true;
                }) == 1
                        && attempted.equals(List.of(FIRST_REGION, SECOND_REGION))
                        && !queue.containsRegion(SECOND_REGION)
                        && queue.containsRegion(FIRST_REGION),
                "A persistent first failure starved a later death transition");
        require(!queue.remove(FIRST_REGION, SECOND_BANKER)
                        && queue.remove(FIRST_REGION, FIRST_BANKER)
                        && queue.size() == 300,
                "Exact-identity removal did not preserve unresolved proof");
        queue.clear();
    }

    private static void testTransientSaveFailureRetriesDurably() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-death-retry-");
        Path directory = root.resolve("world-data");
        Path heldDirectory = null;
        try {
            EconomyService service = createCanonicalBanker(
                    directory, FIRST_REGION, 0x1020304050607080L, FIRST_BANKER);
            BankerDeathRetryQueue queue = new BankerDeathRetryQueue(2);
            heldDirectory = blockSaveDirectory(directory);

            require(!service.recordGeneratedBankerDeath(FIRST_REGION, FIRST_BANKER)
                            && !service.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER)
                            && FIRST_BANKER.equals(service.generatedBankerId(FIRST_REGION)),
                    "A failed death save exposed replacement authority or lost canonical ownership");
            require(queue.offer(FIRST_REGION, FIRST_BANKER)
                            && queue.containsRegion(FIRST_REGION),
                    "Failed death persistence was not retained for retry");
            require(queue.retry((regionKey, bankerId) ->
                            service.recordGeneratedBankerDeath(regionKey, bankerId)) == 0
                            && queue.containsRegion(FIRST_REGION)
                            && !service.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER)
                            && FIRST_BANKER.equals(service.generatedBankerId(FIRST_REGION)),
                    "A still-failing retry was dropped or exposed replacement authority");

            restoreSaveDirectory(directory, heldDirectory);
            heldDirectory = null;
            require(queue.retry((regionKey, bankerId) -> {
                        UUID current = service.generatedBankerId(regionKey);
                        return current == null
                                || !current.equals(bankerId)
                                || service.recordGeneratedBankerDeath(regionKey, bankerId);
                    }) == 1
                            && queue.size() == 0
                            && service.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER)
                            && FIRST_BANKER.equals(service.generatedBankerId(FIRST_REGION)),
                    "Recovered storage did not durably authorize replacement");

            require(service.rememberGeneratedBanker(FIRST_REGION, SECOND_BANKER)
                            && SECOND_BANKER.equals(service.generatedBankerId(FIRST_REGION))
                            && !service.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER),
                    "A durable death tombstone did not atomically install its replacement");

            EconomyService reload = new EconomyService();
            reload.start(directory, 91L, 0L);
            require(SECOND_BANKER.equals(reload.generatedBankerId(FIRST_REGION))
                            && !reload.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER),
                    "Successful canonical replacement was not durable across restart");
        } finally {
            if (heldDirectory != null) {
                restoreSaveDirectory(directory, heldDirectory);
            }
            deleteTree(root);
        }
    }

    private static void testDurableDeathAuthorizesReplacementAfterRestart()
            throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-death-durable-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonicalBanker(
                    directory, FIRST_REGION, 0x30405060708090A0L, FIRST_BANKER);
            require(service.recordGeneratedBankerDeath(FIRST_REGION, FIRST_BANKER)
                            && service.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER)
                            && FIRST_BANKER.equals(service.generatedBankerId(FIRST_REGION)),
                    "A confirmed death did not preserve canonical ownership behind a tombstone");

            EconomyService reload = new EconomyService();
            reload.start(directory, 93L, 0L);
            require(reload.generatedBankerDeathPending(FIRST_REGION, FIRST_BANKER)
                            && FIRST_BANKER.equals(reload.generatedBankerId(FIRST_REGION)),
                    "Restart lost the durable replacement authority");
            require(reload.rememberGeneratedBanker(FIRST_REGION, SECOND_BANKER)
                            && SECOND_BANKER.equals(reload.generatedBankerId(FIRST_REGION))
                            && !reload.generatedBankerDeathPending(
                                    FIRST_REGION, FIRST_BANKER),
                    "Restarted service could not atomically replace the confirmed-dead Banker");
        } finally {
            deleteTree(root);
        }
    }

    private static void testRestartRemainsConservativeAfterFailedDeathSave()
            throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-death-restart-");
        Path directory = root.resolve("world-data");
        Path heldDirectory = null;
        try {
            EconomyService service = createCanonicalBanker(
                    directory, SECOND_REGION, 0x2030405060708090L, SECOND_BANKER);
            BankerDeathRetryQueue queue = new BankerDeathRetryQueue(2);
            heldDirectory = blockSaveDirectory(directory);
            require(!service.recordGeneratedBankerDeath(SECOND_REGION, SECOND_BANKER)
                            && queue.offer(SECOND_REGION, SECOND_BANKER),
                    "Failed death transition could not establish runtime proof");

            // Server stop/start clears only session evidence. The failed save must leave the old
            // canonical UUID durable so an ambiguous missing entity cannot produce a duplicate.
            queue.clear();
            restoreSaveDirectory(directory, heldDirectory);
            heldDirectory = null;
            EconomyService reload = new EconomyService();
            reload.start(directory, 92L, 0L);
            require(queue.size() == 0
                            && SECOND_BANKER.equals(
                                    reload.generatedBankerId(SECOND_REGION))
                            && !reload.generatedBankerDeathPending(
                                    SECOND_REGION, SECOND_BANKER),
                    "Restart exposed replacement authority after an unpersisted death");
        } finally {
            if (heldDirectory != null) {
                restoreSaveDirectory(directory, heldDirectory);
            }
            deleteTree(root);
        }
    }

    private static EconomyService createCanonicalBanker(
            Path directory, long regionKey, long anchor, UUID bankerId) throws Exception {
        EconomyService service = new EconomyService();
        service.start(directory, 90L, 0L);
        require(service.markGeneratedBankRegion(regionKey, anchor)
                        && service.rememberGeneratedBanker(regionKey, bankerId),
                "Could not create the canonical-Banker fixture");
        return service;
    }

    private static Path blockSaveDirectory(Path directory) throws IOException {
        Path heldDirectory = directory.resolveSibling(
                directory.getFileName() + "-held");
        Files.move(directory, heldDirectory);
        Files.writeString(directory, "deliberately not a directory");
        return heldDirectory;
    }

    private static void restoreSaveDirectory(Path directory, Path heldDirectory)
            throws IOException {
        Files.deleteIfExists(directory);
        Files.move(heldDirectory, directory);
    }

    private static void deleteTree(Path root) throws IOException {
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
