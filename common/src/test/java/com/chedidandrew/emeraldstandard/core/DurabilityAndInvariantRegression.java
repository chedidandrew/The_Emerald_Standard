package com.chedidandrew.emeraldstandard.core;

import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.PLAYER;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.deleteTree;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.readProperties;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.require;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.requireValidationFailure;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.writeProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class DurabilityAndInvariantRegression {
    private DurabilityAndInvariantRegression() {
    }

    static void run(Path root) throws Exception {
        testTradingSpread(root.resolve("spread"));
        testBackupRecovery(root.resolve("backup"));
        testEmptyPrimaryUsesBackup(root.resolve("empty-primary"));
        testChecksumCorruptionUsesBackup(root.resolve("checksum"));
        testMutationRollback(root.resolve("rollback"));
        testAutomaticSaveBackoff(root.resolve("backoff"));
        testNoDebtAndCaps(root.resolve("no-debt"));
        testSaturatedVillageCounters(root.resolve("village-counters"));
        testStructuralInvariantValidation();
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
        require(service.deposit(PLAYER, 2L),
                "A valid in-memory state could not replace a corrupt primary");
        EconomyState stillGood = EconomyState.load(backup, 999L, 50_000L, 0L);
        require(stillGood.account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Corrupt primary replaced known-good backup");
        EconomyState replacement = EconomyState.load(main, 999L, 50_000L, 0L);
        require(replacement.account(PLAYER).cashMicro == 17L * EconomyState.MICRO,
                "Replacement save lost state after rejecting a changed primary fingerprint");
    }

    private static void testEmptyPrimaryUsesBackup(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 124L, 0L, 0L);
        require(service.deposit(PLAYER, 10L), "Empty-primary first deposit failed");
        require(service.deposit(PLAYER, 5L), "Empty-primary backup creation failed");
        Path main = directory.resolve("the_emerald_standard.properties");
        Files.writeString(main, "");
        EconomyState recovered = EconomyState.load(main, 999L, 0L, 0L);
        require(recovered.existingAccount(PLAYER) != null,
                "Empty primary was accepted as a fresh economy");
        require(recovered.account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Empty primary ignored the valid backup");
    }

    private static void testChecksumCorruptionUsesBackup(Path directory) throws Exception {
        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 125L, 0L, 0L);
        require(service.deposit(PLAYER, 10L), "Checksum first deposit failed");
        require(service.deposit(PLAYER, 5L), "Checksum backup creation failed");
        Path main = directory.resolve("the_emerald_standard.properties");
        Properties properties = readProperties(main);
        properties.setProperty(
                "account." + PLAYER + ".cash",
                Long.toString(999_999L * EconomyState.MICRO));
        writeProperties(main, properties);
        EconomyState recovered = EconomyState.load(main, 999L, 0L, 0L);
        require(recovered.account(PLAYER).cashMicro < 999_999L * EconomyState.MICRO,
                "Checksum did not detect a modified balance");
        require(recovered.account(PLAYER).cashMicro >= 10L * EconomyState.MICRO,
                "Checksum recovery did not use the valid backup");
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

    private static void testStructuralInvariantValidation() throws Exception {
        EconomyState futureHistory = EconomyState.fresh(790L, 0L, 0L);
        futureHistory.economicDay = 5L;
        EconomyState.PortfolioValuePoint point = new EconomyState.PortfolioValuePoint();
        point.day = 6L;
        point.valueMicro = EconomyState.MICRO;
        futureHistory.account(PLAYER).netWorthHistory.add(point);
        requireValidationFailure(futureHistory,
                "Future-dated net-worth history passed validation");

        EconomyState nullHolding = EconomyState.fresh(791L, 0L, 0L);
        nullHolding.account(PLAYER).shares.put("VILX", null);
        requireValidationFailure(nullHolding,
                "Null share holding did not fail validation cleanly");

        EconomyState overcommittedVillage = EconomyState.fresh(792L, 0L, 0L);
        EconomyState.VillageRecord village = overcommittedVillage.village(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000792"));
        village.population = VillageProsperityEngine.MAX_ABSTRACT_POPULATION;
        village.pendingSettlers = 1;
        requireValidationFailure(overcommittedVillage,
                "Village population plus pending settlers exceeded the simulation cap");

        EconomyState staleProjectSerial = EconomyState.fresh(793L, 0L, 0L);
        EconomyState.VillageRecord projectVillage = staleProjectSerial.village(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000793"));
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = 1L;
        project.totalBlocks = project.type.nominalBlocks();
        projectVillage.projects.add(project);
        requireValidationFailure(staleProjectSerial,
                "Village project serial was allowed to trail persisted project ids");

        EconomyState duplicateTermId = EconomyState.fresh(794L, 0L, 0L);
        duplicateTermId.economicDay = 5L;
        EconomyState.Account account = duplicateTermId.account(PLAYER);
        EconomyState.CdPosition cd = new EconomyState.CdPosition();
        cd.positionId = 1L;
        cd.principalMicro = EconomyState.MICRO;
        cd.valueMicro = EconomyState.MICRO;
        cd.maturityDay = 30L;
        cd.annualRate = 0.04;
        account.cdPositions.put(cd.positionId, cd);
        EconomyState.LoanPosition loan = new EconomyState.LoanPosition();
        loan.positionId = 1L;
        loan.principalMicro = EconomyState.MICRO;
        loan.valueMicro = EconomyState.MICRO;
        loan.maturityDay = 30L;
        loan.serial = 1L;
        loan.annualRate = 0.05;
        account.loanPositions.put(loan.positionId, loan);
        requireValidationFailure(duplicateTermId,
                "CD and lending positions shared a supposedly stable identifier");

        EconomyState nullBankRegion = EconomyState.fresh(795L, 0L, 0L);
        nullBankRegion.generatedBankRegions.add(null);
        requireValidationFailure(nullBankRegion,
                "Null generated-bank region passed validation");

        EconomyState nullBankAnchor = EconomyState.fresh(798L, 0L, 0L);
        nullBankAnchor.generatedBankRegions.add(1L);
        nullBankAnchor.generatedBankAnchors.put(1L, null);
        requireValidationFailure(nullBankAnchor,
                "Null generated-bank anchor passed validation");

        EconomyState exhaustedClock = EconomyState.fresh(796L, 0L, 0L);
        exhaustedClock.economicDay = Long.MAX_VALUE;
        boolean refusedAdvance = false;
        try {
            exhaustedClock.advanceOneDay();
        } catch (IllegalStateException expected) {
            refusedAdvance = true;
        }
        require(refusedAdvance && exhaustedClock.economicDay == Long.MAX_VALUE,
                "Economic-day overflow wrapped or partially advanced state");
    }

    private static void testSaturatedVillageCounters(Path directory) throws Exception {
        java.util.UUID villageId = java.util.UUID.fromString(
                "00000000-0000-0000-0000-000000000797");
        EconomyState state = EconomyState.fresh(797L, 0L, 0L);
        EconomyState.VillageRecord village = state.village(villageId);
        village.population = 1;
        village.observedPopulation = 1;
        village.housingCapacity = 4;
        village.hostileCasualties = Integer.MAX_VALUE;
        village.collapseCount = Integer.MAX_VALUE;
        state.save(directory.resolve("the_emerald_standard.properties"));

        EconomyService service = new EconomyService();
        service.startWithSeed(directory, 999L, 0L, 0L);
        require(service.recordVillagerDeath(
                        villageId,
                        null,
                        "minecraft:farmer",
                        1L,
                        VillageProsperityEngine.IncidentCause.HOSTILE,
                        null),
                "A saturated diagnostic counter blocked a real village casualty");
        EconomyState.VillageRecord recorded = service.villageSnapshot(villageId).village();
        require(recorded.population == 0
                        && recorded.hostileCasualties == Integer.MAX_VALUE
                        && recorded.collapseCount == Integer.MAX_VALUE,
                "Saturated village counters wrapped while recording a casualty");
    }
}
