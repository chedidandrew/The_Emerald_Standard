package com.chedidandrew.emeraldstandard.core;

import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.PLAYER;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.deleteTree;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

import java.nio.file.Files;
import java.nio.file.Path;

final class DurabilityAndInvariantRegression {
    private DurabilityAndInvariantRegression() {
    }

    static void run(Path root) throws Exception {
        testTradingSpread(root.resolve("spread"));
        testBackupRecovery(root.resolve("backup"));
        testMutationRollback(root.resolve("rollback"));
        testAutomaticSaveBackoff(root.resolve("backoff"));
        testNoDebtAndCaps(root.resolve("no-debt"));
    }

    private static void testTradingSpread(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 55L, 0L, 0L);
        require(service.deposit(PLAYER, 1_000L), "Spread deposit failed");
        require(service.buy(PLAYER, "VILX", 100L), "Spread purchase failed");
        double shares = service.snapshot().account(PLAYER).shares.get("VILX");
        require(service.sell(PLAYER, "VILX", shares), "Spread sale failed");
        long cash = service.snapshot().account(PLAYER).cashMicro;
        require(cash < 1_000L * EconomyState.MICRO
                        && cash > 999L * EconomyState.MICRO,
                "Round-trip spread is absent or much larger than configured");
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
        EconomyState stillGood = EconomyState.load(backup, 999L, 50_000L, 0L);
        require(stillGood.account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Corrupt primary replaced known-good backup");
    }

    private static void testMutationRollback(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 456L, 70_000L, 0L);
        require(service.deposit(PLAYER, 25L), "Baseline deposit failed");
        long before = service.snapshot().account(PLAYER).cashMicro;
        Path save = directory.resolve("the_emerald_standard.properties");
        Files.delete(save);
        Files.createDirectory(save);
        Files.writeString(save.resolve("block"), "x");
        require(!service.deposit(PLAYER, 5L), "Deposit succeeded without persistence");
        require(service.snapshot().account(PLAYER).cashMicro == before,
                "Failed save did not roll back account mutation");
    }

    private static void testAutomaticSaveBackoff(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 457L, 0L, 0L);
        Path save = directory.resolve("the_emerald_standard.properties");
        Files.delete(save);
        Files.createDirectory(save);
        Files.writeString(save.resolve("block"), "x");

        require(!service.tickAt(EconomyService.TICKS_PER_MINECRAFT_DAY, 30_000L),
                "Automatic save unexpectedly succeeded");
        require(service.snapshot().economicDay == 1L,
                "Failed automatic save rolled back deterministic progress");
        require(service.tickAt(EconomyService.TICKS_PER_MINECRAFT_DAY, 30_001L),
                "Save backoff retried immediately");

        deleteTree(save);
        require(service.tickAt(EconomyService.TICKS_PER_MINECRAFT_DAY, 32_000L),
                "Automatic save did not recover after retry delay");
        require(EconomyState.load(save, 999L, 32_000L,
                        EconomyService.TICKS_PER_MINECRAFT_DAY).economicDay == 1L,
                "Recovered automatic save lost market progress");
    }

    private static void testNoDebtAndCaps(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 789L, 90_000L, 0L);
        require(service.withdraw(PLAYER, 1L) == 0L, "Empty account withdrew");
        require(!service.moveSavings(PLAYER, 1L, true), "Empty account funded savings");
        require(!service.openCd(PLAYER, 1L, 90), "Empty account opened CD");
        require(!service.fundLoan(PLAYER, 1L, 90), "Empty account funded loan");
        require(!service.buy(PLAYER, "VILX", 1L), "Empty account bought shares");
        require(!service.deposit(PLAYER, EconomyService.MAX_WHOLE_EMERALD_TRANSACTION + 1L),
                "Oversized account transaction bypassed cap");
        EconomyState.Account account = service.snapshot().account(PLAYER);
        require(account.cashMicro >= 0L
                        && account.savingsMicro >= 0L
                        && account.cdValueMicro >= 0L
                        && account.loanValueMicro >= 0L,
                "Account entered debt");
    }
}
