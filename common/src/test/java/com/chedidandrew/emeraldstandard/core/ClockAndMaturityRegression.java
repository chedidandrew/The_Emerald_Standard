package com.chedidandrew.emeraldstandard.core;

import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.PLAYER;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;

import java.nio.file.Path;

final class ClockAndMaturityRegression {
    private ClockAndMaturityRegression() {
    }

    static void run(Path root) throws Exception {
        testMaturities(root.resolve("maturity"));
        testPartialGameDay(root.resolve("partial-game"));
        testPartialWallDay(root.resolve("partial-wall"));
        testMixedClockDoesNotDoubleCount(root.resolve("mixed-clock"));
        testOfflineProgressionPolicy(root.resolve("offline-policy"));
        testConfiguredOfflineLimit(root.resolve("configured-catch-up"));
        testBoundedCatchUp(root.resolve("catch-up"));
        testBackwardClock(root.resolve("clock"));
    }

    private static void testMaturities(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 42L, 10_000L, 0L);
        require(service.deposit(PLAYER, 1_000L), "Initial deposit failed");
        require(service.openCd(PLAYER, 100L, 90), "CD open failed");
        double rate = service.snapshot().account(PLAYER).cdAnnualRate;
        require(service.tickAt(90L * EconomyService.TICKS_PER_MINECRAFT_DAY, 10_000L),
                "CD maturity tick failed");
        long maturedValue = service.snapshot().account(PLAYER).cdValueMicro;
        require(maturedValue > 100L * EconomyState.MICRO, "CD earned no interest");
        require(service.snapshot().account(PLAYER).cdAnnualRate == rate, "CD rate changed");
        require(service.tickAt(120L * EconomyService.TICKS_PER_MINECRAFT_DAY, 10_000L),
                "Post-maturity tick failed");
        require(service.snapshot().account(PLAYER).cdValueMicro == maturedValue,
                "Mature CD kept accruing");
        require(service.closeCd(PLAYER).matured(), "Mature CD did not close");

        require(service.fundLoan(PLAYER, 200L, 180), "Loan funding failed");
        require(service.tickAt(300L * EconomyService.TICKS_PER_MINECRAFT_DAY, 10_000L),
                "Loan maturity tick failed");
        EconomyState.Account loan = service.snapshot().account(PLAYER);
        require(loan.loanResolved && loan.loanValueMicro >= 0L,
                "Loan maturity failed or created debt");
        require(service.collectLoan(PLAYER).collected(), "Mature loan did not collect");
    }

    private static void testPartialGameDay(Path directory) throws Exception {
        EconomyService first = new EconomyService();
        first.startWithSeed(directory, 51L, 0L, 0L);
        require(first.tickAt(12_000L, 0L), "First half game day failed");
        require(first.snapshot().economicDay == 0L
                        && first.snapshot().pendingEconomicMillis
                                == EconomyService.MILLIS_PER_MINECRAFT_DAY / 2L,
                "Partial game day was not retained");
        require(first.saveNowAt(12_000L, 0L), "Partial game-day save failed");

        EconomyService second = new EconomyService();
        second.startWithSeed(directory, 999L, 0L, 12_000L);
        require(second.tickAt(24_000L, 0L), "Second half game day failed");
        require(second.snapshot().economicDay == 1L
                        && second.snapshot().pendingEconomicMillis == 0L,
                "Two half sessions did not create one economic day");
    }

    private static void testPartialWallDay(Path directory) throws Exception {
        EconomyService first = new EconomyService();
        first.startWithSeed(directory, 52L, 0L, 0L);
        long half = EconomyService.MILLIS_PER_MINECRAFT_DAY / 2L;
        require(first.tickAt(0L, half), "First wall half-day failed");
        require(first.saveNowAt(0L, half), "Wall remainder save failed");

        EconomyService second = new EconomyService();
        second.startWithSeed(directory, 999L, half, 0L);
        require(second.snapshot().pendingEconomicMillis == half,
                "Restart discarded partial wall time");
        require(second.tickAt(0L, EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Second wall half-day failed");
        require(second.snapshot().economicDay == 1L
                        && second.snapshot().pendingEconomicMillis == 0L,
                "Two wall half-days did not create one economic day");
    }

    private static void testMixedClockDoesNotDoubleCount(Path directory) throws Exception {
        long quarterWall = EconomyService.MILLIS_PER_MINECRAFT_DAY / 4L;
        long threeQuarterWall = quarterWall * 3L;

        EconomyService first = new EconomyService();
        first.startWithSeed(directory, 521L, 0L, 0L);
        require(first.tickAt(0L, threeQuarterWall), "Offline partial day failed");
        require(first.saveNowAt(0L, threeQuarterWall), "Mixed-clock baseline save failed");

        EconomyService second = new EconomyService();
        second.startWithSeed(directory, 999L, threeQuarterWall, 0L);
        require(second.tickAt(6_000L, EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Mixed clock first quarter failed");
        require(second.snapshot().economicDay == 1L,
                "Offline and online overlap did not create exactly one day");

        require(second.tickAt(24_000L,
                        EconomyService.MILLIS_PER_MINECRAFT_DAY + threeQuarterWall),
                "Mixed clock next three quarters failed");
        require(second.snapshot().economicDay == 1L
                        && second.snapshot().pendingEconomicMillis == threeQuarterWall,
                "Mixed clocks double-counted overlapping elapsed time");

        require(second.tickAt(30_000L, 2L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Mixed clock final quarter failed");
        require(second.snapshot().economicDay == 2L
                        && second.snapshot().pendingEconomicMillis == 0L,
                "Two real days did not become exactly two economic days");
    }

    private static void testOfflineProgressionPolicy(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.configureEconomicClock(false, 30L);
        service.startWithSeed(directory, 522L, 0L, 0L);
        require(service.tickAt(
                        0L, 10L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Disabled offline clock tick failed");
        require(service.snapshot().economicDay == 0L
                        && service.snapshot().pendingEconomicMillis == 0L,
                "Disabled offline progression advanced from wall time");

        require(service.tickAt(
                        EconomyService.TICKS_PER_MINECRAFT_DAY,
                        11L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Game-time clock tick failed while offline progression was disabled");
        require(service.snapshot().economicDay == 1L,
                "Disabling offline progression also disabled game-time progression");

        EconomyService reenabled = new EconomyService();
        reenabled.configureEconomicClock(false, 30L);
        reenabled.startWithSeed(directory.resolve("reenabled"), 524L, 0L, 0L);
        require(reenabled.tickAt(
                        0L, 100L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Disabled offline clock observation failed");
        reenabled.configureEconomicClock(true, 30L);
        require(reenabled.tickAt(
                        0L, 100L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Re-enabled offline clock baseline tick failed");
        require(reenabled.snapshot().economicDay == 0L,
                "Re-enabling offline progression retroactively credited ignored wall time");
        require(reenabled.tickAt(
                        0L, 101L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Re-enabled offline clock forward tick failed");
        require(reenabled.snapshot().economicDay == 1L,
                "Re-enabled offline progression did not credit new wall time");
    }

    private static void testConfiguredOfflineLimit(Path directory) throws Exception {
        EconomyService original = new EconomyService();
        original.startWithSeed(directory, 523L, 0L, 0L);
        require(original.saveNowAt(0L, 0L), "Configured catch-up baseline save failed");

        EconomyService reloaded = new EconomyService();
        reloaded.configureEconomicClock(true, 3L);
        reloaded.startWithSeed(
                directory,
                999L,
                100L * EconomyService.MILLIS_PER_MINECRAFT_DAY,
                0L);
        require(reloaded.snapshot().economicDay == 3L
                        && reloaded.catchUpDaysRemaining() == 0L,
                "Configured offline limit was not enforced");

        EconomyService partial = new EconomyService();
        partial.configureEconomicClock(true, 3L);
        partial.startWithSeed(directory.resolve("partial"), 525L, 0L, 0L);
        require(partial.tickAt(
                        0L, EconomyService.MILLIS_PER_MINECRAFT_DAY / 2L),
                "Partial clock setup failed");
        require(partial.tickAt(
                        0L, 100L * EconomyService.MILLIS_PER_MINECRAFT_DAY),
                "Partial clock capped gap failed");
        require(partial.snapshot().economicDay == 3L
                        && partial.snapshot().pendingEconomicMillis
                                == EconomyService.MILLIS_PER_MINECRAFT_DAY / 2L,
                "Configured offline limit discarded or bypassed a partial remainder");

        boolean priorOfflineEnabled = reloaded.offlineProgressionEnabled();
        long priorMaximumDays = reloaded.maximumOfflineDays();
        requireThrows(() -> reloaded.configureEconomicClock(true, 0L),
                "Zero maximum offline days were accepted");
        requireThrows(
                () -> reloaded.configureEconomicClock(
                        true, EconomyService.MAX_TRUSTED_CATCH_UP_DAYS + 1L),
                "Offline limit above the absolute safety cap was accepted");
        require(reloaded.offlineProgressionEnabled() == priorOfflineEnabled
                        && reloaded.maximumOfflineDays() == priorMaximumDays,
                "Rejected economic-clock configuration partially changed the policy");
    }

    private static void testBoundedCatchUp(Path directory) throws Exception {
        EconomyService original = new EconomyService();
        original.startWithSeed(directory, 53L, 0L, 0L);
        EconomyService.VillageSnapshot observed = original.observeVillage(
                new EconomyService.VillageObservation(
                        "minecraft:overworld",
                        1L,
                        0L,
                        0L,
                        4,
                        6,
                        0,
                        false,
                        java.util.List.of()));
        require(observed != null, "Catch-up village fixture failed");
        require(original.deposit(PLAYER, 10L), "Catch-up donor fixture failed");
        require(original.saveNowAt(0L, 0L), "Baseline catch-up save failed");

        long farFuture = (EconomyService.MAX_TRUSTED_CATCH_UP_DAYS + 5_000L)
                * EconomyService.MILLIS_PER_MINECRAFT_DAY;
        EconomyService reloaded = new EconomyService();
        reloaded.startWithSeed(directory, 999L, farFuture, 0L);
        require(reloaded.snapshot().economicDay == 2_000L,
                "Startup catch-up was not bounded");
        require(!reloaded.deposit(PLAYER, 1L),
                "Banking was allowed during incomplete catch-up");
        long cashBefore = reloaded.snapshot().account(PLAYER).cashMicro;
        EconomyService.VillageFundContributionResult contribution =
                reloaded.contributeToVillageFund(
                        PLAYER,
                        observed.village().villageId,
                        1L,
                        EconomyState.ProsperityFundType.DIRECT_GRANT,
                        EconomyState.DonationPurpose.GENERAL);
        require(!contribution.contributed(),
                "Prosperity Fund contribution bypassed incomplete catch-up");
        require(reloaded.snapshot().account(PLAYER).cashMicro == cashBefore
                        && reloaded.villageFundSnapshot(observed.village().villageId)
                                .lifetimeReceivedMicro() == 0L,
                "Rejected catch-up contribution changed persisted balances");
        int passes = 0;
        while (reloaded.catchUpDaysRemaining() > 0L && passes++ < 200) {
            require(reloaded.tickAt(0L, farFuture), "Background catch-up failed");
        }
        require(reloaded.catchUpDaysRemaining() == 0L, "Catch-up never completed");
        require(reloaded.snapshot().economicDay == EconomyService.MAX_TRUSTED_CATCH_UP_DAYS,
                "Trusted catch-up cap was not enforced");
    }

    private static void testBackwardClock(Path directory) throws Exception {
        long original = 10L * EconomyService.MILLIS_PER_MINECRAFT_DAY;
        EconomyService first = new EconomyService();
        first.startWithSeed(directory, 111L, original, 0L);
        EconomyService second = new EconomyService();
        second.startWithSeed(directory, 222L, original / 2L, 0L);
        require(second.snapshot().economicDay == first.snapshot().economicDay,
                "Backward clock advanced the economy");
        require(second.snapshot().lastWallClockMs >= original,
                "Backward clock lowered trusted time");
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
