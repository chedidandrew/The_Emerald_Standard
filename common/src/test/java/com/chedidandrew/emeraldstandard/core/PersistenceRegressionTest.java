package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/** Save, reload, maturity, rollback, clock, and no-debt checks. */
public final class PersistenceRegressionTest {
    private static final UUID PLAYER = UUID.fromString(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private PersistenceRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("emerald-standard-test-");
        try {
            testMaturitiesAndReload(root.resolve("maturity"));
            testBackupRecovery(root.resolve("backup"));
            testRollback(root.resolve("rollback"));
            testNoDebt(root.resolve("no-debt"));
            testBackwardClock(root.resolve("clock"));
            System.out.println("PASS PersistenceRegressionTest");
        } finally {
            deleteTree(root);
        }
    }

    private static void testMaturitiesAndReload(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 42L, 10_000L, 0L);
        require(service.deposit(PLAYER, 1_000L), "Initial deposit failed");
        require(service.openCd(PLAYER, 100L, 90), "CD open failed");
        double lockedRate = service.snapshot().account(PLAYER).cdAnnualRate;

        require(service.tick(90L * 24_000L), "CD maturity tick failed");
        EconomyState matured = service.snapshot();
        EconomyState.Account account = matured.account(PLAYER);
        long maturityValue = account.cdValueMicro;
        require(maturityValue > 100L * EconomyState.MICRO, "CD earned no interest");
        require(account.cdAnnualRate == lockedRate, "CD rate changed after opening");

        require(service.tick(120L * 24_000L), "Post-maturity tick failed");
        require(service.snapshot().account(PLAYER).cdValueMicro == maturityValue,
                "Matured CD continued accruing");
        require(service.closeCd(PLAYER).matured(), "Mature CD did not close");

        require(service.fundLoan(PLAYER, 200L, 180), "Loan funding failed");
        require(service.tick(300L * 24_000L), "Loan maturity tick failed");
        EconomyState.Account loan = service.snapshot().account(PLAYER);
        require(loan.loanResolved, "Mature loan was not resolved");
        require(loan.loanValueMicro >= 0L, "Loan created player debt");
        require(service.collectLoan(PLAYER).collected(), "Mature loan did not collect");

        EconomyState beforeReload = service.snapshot();
        EconomyService reloaded = new EconomyService();
        reloaded.startWithSeed(
                directory,
                999L,
                beforeReload.lastWallClockMs,
                beforeReload.lastGameTicks);
        EconomyState afterReload = reloaded.snapshot();
        require(afterReload.seed == beforeReload.seed, "Reload changed private seed");
        require(afterReload.economicDay == beforeReload.economicDay, "Reload changed day");
        require(afterReload.prices.equals(beforeReload.prices), "Reload changed prices");
        require(afterReload.account(PLAYER).cashMicro
                        == beforeReload.account(PLAYER).cashMicro,
                "Reload changed cash");
    }

    private static void testBackupRecovery(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 123L, 50_000L, 0L);
        require(service.deposit(PLAYER, 10L), "First deposit failed");
        require(service.deposit(PLAYER, 5L), "Second deposit failed");
        Path main = directory.resolve("the_emerald_standard.properties");
        Path backup = directory.resolve("the_emerald_standard.properties.bak");
        require(Files.exists(backup), "Backup was not created");
        Files.writeString(main, "seed=not-a-number\n");
        EconomyState recovered = EconomyState.load(main, 999L, 50_000L, 0L);
        require(recovered.account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Backup recovery lost committed data");

        EconomyService restarted = new EconomyService();
        restarted.startWithSeed(directory, 999L, 50_000L, 0L);
        require(restarted.snapshot().account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Restart after backup recovery lost committed data");
        EconomyState backupAfterRecovery = EconomyState.load(backup, 999L, 50_000L, 0L);
        require(backupAfterRecovery.account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Corrupt primary replaced the known-good backup");
    }

    private static void testRollback(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 456L, 70_000L, 0L);
        require(service.deposit(PLAYER, 25L), "Baseline deposit failed");
        long before = service.snapshot().account(PLAYER).cashMicro;
        Path save = directory.resolve("the_emerald_standard.properties");
        Files.delete(save);
        Files.createDirectory(save);
        require(!service.deposit(PLAYER, 5L), "Deposit succeeded without persistence");
        require(service.snapshot().account(PLAYER).cashMicro == before,
                "Failed save did not roll back memory");
    }

    private static void testNoDebt(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 789L, 90_000L, 0L);
        require(service.withdraw(PLAYER, 1L) == 0L, "Empty account withdrew");
        require(!service.moveSavings(PLAYER, 1L, true), "Empty account funded savings");
        require(!service.openCd(PLAYER, 1L, 90), "Empty account opened CD");
        require(!service.fundLoan(PLAYER, 1L, 90), "Empty account funded loan");
        require(!service.buy(PLAYER, "VILX", 1L), "Empty account bought shares");
        EconomyState.Account account = service.snapshot().account(PLAYER);
        require(account.cashMicro >= 0L
                        && account.savingsMicro >= 0L
                        && account.cdValueMicro >= 0L
                        && account.loanValueMicro >= 0L,
                "Account entered debt");
    }

    private static void testBackwardClock(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        long original = 10L * EconomyService.MILLIS_PER_MINECRAFT_DAY;
        service.startWithSeed(directory, 111L, original, 0L);
        long day = service.snapshot().economicDay;
        EconomyService reloaded = new EconomyService();
        reloaded.startWithSeed(directory, 222L, original / 2L, 0L);
        require(reloaded.snapshot().economicDay == day, "Backward clock advanced economy");
        require(reloaded.snapshot().lastWallClockMs >= original,
                "Backward clock lowered trusted timestamp");
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
                    // Best-effort cleanup.
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
