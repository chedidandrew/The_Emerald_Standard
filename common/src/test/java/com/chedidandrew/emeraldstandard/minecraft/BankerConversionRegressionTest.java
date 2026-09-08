package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/** Pure queue and durable-state regressions for generated-Banker UUID conversions. */
public final class BankerConversionRegressionTest {
    private static final long FIRST_REGION = 0x123456789ABCDEFL;
    private static final long SECOND_REGION = 0x23456789ABCDEF0L;
    private static final long FIRST_ANCHOR = 0x1020304050607080L;
    private static final UUID A = UUID.fromString(
            "00000000-0000-0000-0000-00000000c001");
    private static final UUID B = UUID.fromString(
            "00000000-0000-0000-0000-00000000c002");
    private static final UUID C = UUID.fromString(
            "00000000-0000-0000-0000-00000000c003");
    private static final UUID D = UUID.fromString(
            "00000000-0000-0000-0000-00000000c004");

    private BankerConversionRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testQueueIdentityChainingAndReentrancy();
        testPreparedTargetCommitClearsDeathAcrossRestart();
        testChainedSourceResolutionIsTypedAndDurable();
        testDurablePhaseRebaseAndTransitionGraph();
        testTerminalTargetCannotBecomeCanonical();
        testPreparedStateGloballyBlocksReplacement();
        testSaveFailuresRetainPriorAuthority();
        testCrossRegionIdentityCollisionsFailClosed();
        System.out.println("PASS BankerConversionRegressionTest");
    }

    private static void testQueueIdentityChainingAndReentrancy() {
        BankerConversionRetryQueue queue = new BankerConversionRetryQueue(1);
        require(queue.offer(FIRST_REGION, A, B, true),
                "Initial A-to-B conversion was not queued");
        require(queue.offer(FIRST_REGION, A, B, true) && queue.size() == 1,
                "Repeated conversion delivery was not idempotent");
        require(!queue.offer(FIRST_REGION, A, C, true),
                "A second exact successor replaced unresolved lineage");
        require(queue.chain(FIRST_REGION, B, C, true),
                "A-to-B followed by B-to-C did not chain");
        BankerConversionRetryQueue.Handoff chained = queue.handoffForRegion(FIRST_REGION);
        require(chained != null
                        && A.equals(chained.previousId())
                        && B.equals(chained.sourceId())
                        && C.equals(chained.convertedId()),
                "Chaining lost the durable A root, immediate B source, or final C target");

        int resolved = queue.retry((region, handoff) -> {
            require(!queue.offer(region, A, D, true),
                    "Reentrant save work entered a second same-region handoff");
            return false;
        });
        require(resolved == 0 && chained.equals(queue.handoffForRegion(FIRST_REGION)),
                "Reentrant retry overwrote or dropped the exact queued handoff");
    }

    private static void testPreparedTargetCommitClearsDeathAcrossRestart() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-target-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            require(service.recordGeneratedBankerDeath(FIRST_REGION, A),
                    "Could not create the NeoForge-order death tombstone");
            EconomyService.GeneratedBankerConversion prepared =
                    prepare(service, FIRST_REGION, A, A, B, true);
            require(service.hasPendingGeneratedBankerConversion(FIRST_REGION)
                            && !service.rememberGeneratedBanker(FIRST_REGION, C),
                    "Prepared conversion did not globally block replacement");

            EconomyService forgedService = service;
            EconomyService.GeneratedBankerConversion forged = withPhase(
                    prepared, EconomyState.BankerConversionPhase.TARGET_DURABLE);
            require(!forgedService.commitGeneratedBankerConversion(forged).accepted()
                            && A.equals(service.generatedBankerId(FIRST_REGION)),
                    "A caller-forged durable phase committed an unsaved target");

            EconomyService reload = new EconomyService();
            reload.start(directory, 201L, 0L);
            EconomyService.GeneratedBankerConversion reloaded =
                    reload.pendingGeneratedBankerConversion(FIRST_REGION);
            require(reloaded != null
                            && reloaded.phase() == EconomyState.BankerConversionPhase.PREPARED
                            && reload.generatedBankerDeathPending(FIRST_REGION, A),
                    "Prepared tuple or matching death did not survive restart");
            EconomyService.BankerConversionResult durable =
                    reload.markGeneratedBankerConversionEntityDurable(reloaded);
            require(durable.accepted()
                            && durable.conversion() != null
                            && reload.commitGeneratedBankerConversion(
                                    durable.conversion()).accepted()
                            && B.equals(reload.generatedBankerId(FIRST_REGION))
                            && reload.pendingGeneratedBankerConversion(FIRST_REGION) == null
                            && !reload.generatedBankerDeathPending(FIRST_REGION, A)
                            && Long.valueOf(FIRST_ANCHOR).equals(
                                    reload.generatedBankerAssignedAnchor(FIRST_REGION)),
                    "Durable target commit did not atomically transfer ownership and clear death");
        } finally {
            deleteTree(root);
        }
    }

    private static void testChainedSourceResolutionIsTypedAndDurable() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-source-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            prepare(service, FIRST_REGION, A, A, B, true);
            EconomyService.GeneratedBankerConversion chained =
                    prepare(service, FIRST_REGION, A, B, C, true);
            require(A.equals(chained.rootCanonicalId())
                            && B.equals(chained.immediateSourceId())
                            && C.equals(chained.targetId())
                            && chained.phase() == EconomyState.BankerConversionPhase.PREPARED,
                    "Durable chain did not retain A/B/C identities");
            require(!service.commitGeneratedBankerConversionSource(chained).accepted(),
                    "PREPARED chain committed its source without a typed world-save phase");

            EconomyService.BankerConversionResult sourceDurable =
                    service.markGeneratedBankerConversionSourceDurable(chained);
            require(sourceDurable.accepted() && sourceDurable.conversion() != null,
                    "Could not persist SOURCE_DURABLE resolution");
            EconomyService reload = new EconomyService();
            reload.start(directory, 202L, 0L);
            EconomyService.GeneratedBankerConversion resolution =
                    reload.pendingGeneratedBankerConversion(FIRST_REGION);
            require(resolution != null
                            && resolution.phase()
                                    == EconomyState.BankerConversionPhase.SOURCE_DURABLE
                            && reload.commitGeneratedBankerConversionSource(
                                    resolution).accepted()
                            && B.equals(reload.generatedBankerId(FIRST_REGION)),
                    "Restart did not finish the exact durable source fallback");
        } finally {
            deleteTree(root);
        }
    }

    private static void testTerminalTargetCannotBecomeCanonical() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-terminal-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            EconomyService.GeneratedBankerConversion terminal =
                    prepare(service, FIRST_REGION, A, A, B, false);
            EconomyService.BankerConversionResult durable =
                    service.markGeneratedBankerConversionEntityDurable(terminal);
            require(durable.accepted() && durable.conversion() != null,
                    "Terminal target durability was not persisted");
            require(!service.commitGeneratedBankerConversion(
                            durable.conversion()).accepted()
                            && A.equals(service.generatedBankerId(FIRST_REGION))
                            && service.retireGeneratedBankerConversion(
                                    durable.conversion()).accepted()
                            && service.generatedBankerDeathPending(FIRST_REGION, A),
                    "Terminal conversion became canonical or failed to retire its root");
        } finally {
            deleteTree(root);
        }
    }

    private static void testDurablePhaseRebaseAndTransitionGraph() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-rebase-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            EconomyService.GeneratedBankerConversion first =
                    prepare(service, FIRST_REGION, A, A, B, true);
            require(!service.prepareGeneratedBankerConversion(
                            FIRST_REGION,
                            A,
                            A,
                            C,
                            "minecraft:overworld",
                            42L,
                            true).accepted(),
                    "PREPARED conversion incorrectly rebased from a possibly rolled-back root");

            EconomyService.BankerConversionResult sourceDurable =
                    service.markGeneratedBankerConversionSourceDurable(
                            prepare(service, FIRST_REGION, A, B, C, true));
            require(sourceDurable.accepted() && sourceDurable.conversion() != null,
                    "Could not establish SOURCE_DURABLE rebase fixture");
            EconomyService.GeneratedBankerConversion rebased =
                    prepare(service, FIRST_REGION, A, B, D, true);
            require(A.equals(rebased.rootCanonicalId())
                            && B.equals(rebased.immediateSourceId())
                            && D.equals(rebased.targetId())
                            && rebased.phase() == EconomyState.BankerConversionPhase.PREPARED,
                    "SOURCE_DURABLE conversion did not safely rebase from its saved source");

            EconomyService.BankerConversionResult targetDurable =
                    service.markGeneratedBankerConversionEntityDurable(rebased);
            require(targetDurable.accepted() && targetDurable.conversion() != null,
                    "Could not establish TARGET_DURABLE transition fixture");
            require(!service.markGeneratedBankerConversionSourceDurable(
                                    targetDurable.conversion()).accepted()
                            && !service.markGeneratedBankerConversionRootDurable(
                                    targetDurable.conversion()).accepted(),
                    "Typed durable conversion phase was downgraded to a conflicting outcome");
            EconomyService.BankerConversionResult retirement =
                    service.markGeneratedBankerConversionRetirementDurable(
                            targetDurable.conversion());
            require(retirement.accepted()
                            && retirement.conversion() != null
                            && retirement.conversion().phase()
                                    == EconomyState.BankerConversionPhase.RETIRE_DURABLE,
                    "TARGET_DURABLE target death could not advance to RETIRE_DURABLE");
        } finally {
            deleteTree(root);
        }
    }

    private static void testPreparedStateGloballyBlocksReplacement() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-gate-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            prepare(service, FIRST_REGION, A, A, B, true);
            require(service.recordGeneratedBankerDeath(FIRST_REGION, A)
                            && service.generatedBankerDeathPending(FIRST_REGION, A)
                            && !service.rememberGeneratedBanker(FIRST_REGION, C)
                            && !service.forgetGeneratedBanker(FIRST_REGION, A)
                            && !service.confirmGeneratedBankerAlive(FIRST_REGION, A),
                    "Pending conversion let death/replacement APIs bypass global authority");
            EconomyService reload = new EconomyService();
            reload.start(directory, 203L, 0L);
            require(reload.pendingGeneratedBankerConversion(FIRST_REGION) != null
                            && !reload.rememberGeneratedBanker(FIRST_REGION, C),
                    "Unloaded prepared target stopped gating replacement after restart");
        } finally {
            deleteTree(root);
        }
    }

    private static void testSaveFailuresRetainPriorAuthority() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-failure-");
        Path directory = root.resolve("world-data");
        Path heldDirectory = null;
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            require(service.recordGeneratedBankerDeath(FIRST_REGION, A),
                    "Could not create durable pre-conversion death");
            heldDirectory = blockSaveDirectory(directory);
            EconomyService.BankerConversionResult failedPrepare =
                    service.prepareGeneratedBankerConversion(
                            FIRST_REGION, A, A, B, "minecraft:overworld", 42L, true);
            require(failedPrepare.status() == EconomyService.BankerConversionStatus.RETRY_IO
                            && service.pendingGeneratedBankerConversion(FIRST_REGION) == null
                            && A.equals(service.generatedBankerId(FIRST_REGION))
                            && service.generatedBankerDeathPending(FIRST_REGION, A),
                    "Failed PREPARE exposed target or consumed prior authority");
            restoreSaveDirectory(directory, heldDirectory);
            heldDirectory = null;

            EconomyService.GeneratedBankerConversion prepared =
                    prepare(service, FIRST_REGION, A, A, B, true);
            EconomyService.BankerConversionResult durable =
                    service.markGeneratedBankerConversionEntityDurable(prepared);
            require(durable.accepted() && durable.conversion() != null,
                    "Could not create durable target fixture");
            heldDirectory = blockSaveDirectory(directory);
            require(service.commitGeneratedBankerConversion(
                                    durable.conversion()).status()
                            == EconomyService.BankerConversionStatus.RETRY_IO
                            && A.equals(service.generatedBankerId(FIRST_REGION))
                            && service.pendingGeneratedBankerConversion(FIRST_REGION) != null
                            && service.generatedBankerDeathPending(FIRST_REGION, A),
                    "Failed COMMIT lost root, prepared tuple, or death authority");
            restoreSaveDirectory(directory, heldDirectory);
            heldDirectory = null;
            require(service.commitGeneratedBankerConversion(
                            service.pendingGeneratedBankerConversion(FIRST_REGION)).accepted()
                            && B.equals(service.generatedBankerId(FIRST_REGION)),
                    "Durable conversion did not recover after storage returned");
        } finally {
            if (heldDirectory != null) {
                restoreSaveDirectory(directory, heldDirectory);
            }
            deleteTree(root);
        }
    }

    private static void testCrossRegionIdentityCollisionsFailClosed() throws Exception {
        Path root = Files.createTempDirectory("emerald-banker-conversion-collision-");
        Path directory = root.resolve("world-data");
        try {
            EconomyService service = createCanonical(directory, FIRST_REGION, A);
            require(service.markGeneratedBankRegion(SECOND_REGION, FIRST_ANCHOR + 1)
                            && service.rememberGeneratedBanker(SECOND_REGION, D),
                    "Could not create second canonical fixture");
            prepare(service, FIRST_REGION, A, A, B, true);
            require(!service.prepareGeneratedBankerConversion(
                                    SECOND_REGION,
                                    D,
                                    D,
                                    B,
                                    "minecraft:overworld",
                                    43L,
                                    true).accepted()
                            && !service.prepareGeneratedBankerConversion(
                                    SECOND_REGION,
                                    D,
                                    D,
                                    A,
                                    "minecraft:overworld",
                                    44L,
                                    true).accepted(),
                    "One UUID entered two canonical/pending Bank lineages");
        } finally {
            deleteTree(root);
        }
    }

    private static EconomyService.GeneratedBankerConversion prepare(
            EconomyService service,
            long region,
            UUID root,
            UUID source,
            UUID target,
            boolean continuing) {
        EconomyService.BankerConversionResult result =
                service.prepareGeneratedBankerConversion(
                        region,
                        root,
                        source,
                        target,
                        "minecraft:overworld",
                        41L,
                        continuing);
        require(result.accepted() && result.conversion() != null,
                "Could not durably prepare Banker conversion: " + result.status());
        return result.conversion();
    }

    private static EconomyService.GeneratedBankerConversion withPhase(
            EconomyService.GeneratedBankerConversion conversion,
            EconomyState.BankerConversionPhase phase) {
        return new EconomyService.GeneratedBankerConversion(
                conversion.regionKey(),
                conversion.rootCanonicalId(),
                conversion.immediateSourceId(),
                conversion.targetId(),
                conversion.targetDimension(),
                conversion.targetPos(),
                phase,
                conversion.disposition());
    }

    private static EconomyService createCanonical(
            Path directory, long regionKey, UUID bankerId) throws Exception {
        EconomyService service = new EconomyService();
        service.start(directory, 200L, 0L);
        require(service.markGeneratedBankRegion(regionKey, FIRST_ANCHOR)
                        && service.rememberGeneratedBanker(regionKey, bankerId),
                "Could not create canonical Banker fixture");
        return service;
    }

    private static Path blockSaveDirectory(Path directory) throws IOException {
        Path heldDirectory = directory.resolveSibling(directory.getFileName() + "-held");
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
