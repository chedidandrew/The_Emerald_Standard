package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Regression coverage for format-9 portfolio accounting, term positions, and village funds. */
public final class FinanceRoadmapRegressionTest {
    private FinanceRoadmapRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testExactPortfolioAccountingAndPersistence();
        testMultipleTermPositions();
        testFormatEightMigration();
        testBoundedFundSpendingAndProtectedEndowment();
        testFundInputCapacityAndSpendableAccounting();
        testProjectSponsorshipTargetingAndRollover();
        testDonationRoutingAndRecognition();
        testPassiveInterestLedgerCoalescing();
        System.out.println("PASS finance and Prosperity Fund roadmap regression suite");
    }

    private static void testExactPortfolioAccountingAndPersistence() throws Exception {
        Path root = Files.createTempDirectory("emerald-portfolio-accounting-");
        try {
            UUID player = UUID.fromString("00000000-0000-0000-0000-000000000901");
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 901L, 0L, 0L);
            require(service.deposit(player, 1_000L), "deposit failed");
            String ticker = EconomyEngine.ASSETS.getFirst().ticker();
            require(service.buy(player, ticker, 200L), "buy failed");

            PortfolioAnalytics.PortfolioSnapshot bought =
                    service.portfolioAnalyticsSnapshot(player);
            PortfolioAnalytics.PositionSnapshot position = bought.positions().get(ticker);
            require(position != null && position.costBasisMicro() == 200L * EconomyState.MICRO,
                    "exact buy basis was not recorded");
            require(position.averagePurchasePrice() > 100.0,
                    "average purchase price omitted execution spread");
            require(bought.totalContributionsMicro() == 1_000L * EconomyState.MICRO,
                    "gross contributions were not tracked");

            require(service.sell(player, ticker, position.shares() / 2.0), "partial sale failed");
            PortfolioAnalytics.PortfolioSnapshot sold =
                    service.portfolioAnalyticsSnapshot(player);
            long remainingBasis = sold.positions().get(ticker).costBasisMicro();
            require(Math.abs(remainingBasis - 100L * EconomyState.MICRO) <= 1L,
                    "partial sale did not remove proportional basis");
            require(sold.realizedGainMicro() < 0L,
                    "round-trip execution spread was not represented in realized P&L");
            require(sold.transactions().stream().anyMatch(entry ->
                            entry.kind == EconomyState.PortfolioTransactionKind.BUY)
                    && sold.transactions().stream().anyMatch(entry ->
                            entry.kind == EconomyState.PortfolioTransactionKind.SELL),
                    "transaction ledger omitted an execution");

            EconomyService reloaded = new EconomyService();
            reloaded.startWithSeed(root, 999L, 0L, 0L);
            PortfolioAnalytics.PortfolioSnapshot afterReload =
                    reloaded.portfolioAnalyticsSnapshot(player);
            require(afterReload.realizedGainMicro() == sold.realizedGainMicro()
                            && afterReload.positions().get(ticker).costBasisMicro()
                                    == remainingBasis,
                    "portfolio accounting did not survive a restart");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testMultipleTermPositions() throws Exception {
        Path root = Files.createTempDirectory("emerald-multiple-products-");
        try {
            UUID player = UUID.fromString("00000000-0000-0000-0000-000000000902");
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 902L, 0L, 0L);
            require(service.deposit(player, 2_000L), "term-product funding failed");
            List<Long> cds = new ArrayList<>();
            List<Long> loans = new ArrayList<>();
            for (int index = 0; index < EconomyState.MAX_TERM_POSITIONS; index++) {
                cds.add(service.openCdPosition(player, 10L, index % 2 == 0 ? 30 : 90));
                loans.add(service.openLoanPosition(player, 10L, index % 2 == 0 ? 30 : 180));
            }
            require(cds.stream().allMatch(id -> id > 0L)
                            && loans.stream().allMatch(id -> id > 0L),
                    "concurrent term positions were rejected");
            require(service.openCdPosition(player, 10L, 30) == 0L
                            && service.openLoanPosition(player, 10L, 30) == 0L,
                    "term-position cap was not enforced");
            require(service.closeCd(player, cds.get(3)).closed(),
                    "stable-id CD close failed");
            PortfolioAnalytics.PortfolioSnapshot snapshot =
                    service.portfolioAnalyticsSnapshot(player);
            require(snapshot.cds().size() == EconomyState.MAX_TERM_POSITIONS - 1
                            && snapshot.loans().size() == EconomyState.MAX_TERM_POSITIONS,
                    "closing one position disturbed other positions");
            require(snapshot.cds().stream().noneMatch(position ->
                            position.positionId == cds.get(3)),
                    "closed CD remained in the position collection");
            EconomyService reloaded = new EconomyService();
            reloaded.startWithSeed(root, 123L, 0L, 0L);
            PortfolioAnalytics.PortfolioSnapshot afterReload =
                    reloaded.portfolioAnalyticsSnapshot(player);
            require(afterReload.cds().size() == EconomyState.MAX_TERM_POSITIONS - 1
                            && afterReload.loans().size() == EconomyState.MAX_TERM_POSITIONS,
                    "multiple term positions did not survive persistence");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testFormatEightMigration() throws Exception {
        Path root = Files.createTempDirectory("emerald-format-eight-finance-");
        try {
            Path save = root.resolve("the_emerald_standard.properties");
            UUID player = UUID.fromString("00000000-0000-0000-0000-000000000903");
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 903L, 0L, 0L);
            require(service.deposit(player, 1_000L)
                            && service.openCd(player, 100L, 90)
                            && service.fundLoan(player, 100L, 180)
                            && service.buy(player, EconomyEngine.ASSETS.getFirst().ticker(), 100L),
                    "migration fixture could not be created");

            Properties legacy = RegressionTestSupport.readProperties(save);
            legacy.setProperty("format", "8");
            legacy.keySet().removeIf(raw -> {
                String key = raw.toString();
                return key.startsWith("commodity.history.")
                        || key.startsWith("donor.")
                        || key.contains(".cdpos.")
                        || key.contains(".loanpos.")
                        || key.contains(".position.next")
                        || key.contains(".portfolio.")
                        || key.contains(".fund.");
            });
            RegressionTestSupport.refreshChecksum(legacy);
            RegressionTestSupport.writeProperties(save, legacy);

            EconomyState migrated = EconomyState.load(save, 1L, 0L, 0L);
            EconomyState.Account account = migrated.accounts.get(player);
            require(account != null && account.cdPositions.size() == 1
                            && account.loanPositions.size() == 1,
                    "legacy scalar term products were not migrated exactly once");
            require(account.costBasisInferred
                            && account.shareCostBasisMicro.containsKey(
                                    EconomyEngine.ASSETS.getFirst().ticker()),
                    "legacy holdings were presented with fabricated exact basis");
            require(migrated.commodityHistory.values().stream()
                            .allMatch(history -> history.size() == 1),
                    "legacy commodity history did not receive a safe initial observation");
            migrated.save(save);
            require(Integer.toString(EconomyState.FORMAT_VERSION).equals(
                            RegressionTestSupport.readProperties(save).getProperty("format")),
                    "legacy save was not upgraded to format 9");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testBoundedFundSpendingAndProtectedEndowment() throws Exception {
        EconomyState funded = EconomyState.fresh(904L, 0L, 0L);
        EconomyState control = EconomyState.fresh(904L, 0L, 0L);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000904");
        EconomyState.VillageRecord village = funded.village(villageId);
        village.population = 5;
        village.observedPopulation = 5;
        village.housingCapacity = 8;
        village.foodSupply = 20.0;
        village.safety = 60.0;
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = 1L;
        project.type = VillageProsperityEngine.ProjectType.COTTAGE;
        project.totalBlocks = project.type.nominalBlocks();
        village.projects.add(project);
        village.projectSerial = 1L;

        long principal = 1_000L * EconomyState.MICRO;
        village.prosperityFund.endowmentPrincipalMicro.put(
                EconomyState.DonationPurpose.GENERAL, principal);
        village.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.FOOD, 20L * EconomyState.MICRO);
        village.prosperityFund.projectSponsorshipMicro.put(
                1L, 10L * EconomyState.MICRO);
        double materialsBefore = village.materialSupply;
        double foodBefore = village.foodSupply;
        for (int day = 0; day < 8; day++) {
            funded.advanceOneDay(false, false, false, true, true, 0.04, 0.10,
                    5L * EconomyState.MICRO);
            control.advanceOneDay(false, false, false, true, true, 0.04, 0.10,
                    5L * EconomyState.MICRO);
        }
        require(village.prosperityFund.endowmentPrincipalTotalMicro() == principal,
                "endowment principal was spent");
        require(village.prosperityFund.lifetimeSpentMicro > 0L
                        && (village.materialSupply > materialsBefore
                                || village.foodSupply > foodBefore),
                "village-owned funds never reached ordinary village inputs");
        require(!project.materializedComplete && project.materializedBlocks == 0,
                "funding instantly materialized a physical project");
        require(funded.prices.equals(control.prices),
                "fund accounting directly changed market prices");
        require(funded.commodityHistory.values().stream().allMatch(history -> history.size() == 9),
                "long commodity history was not recorded");
        funded.validate();
    }

    private static void testDonationRoutingAndRecognition() throws Exception {
        Path root = Files.createTempDirectory("emerald-fund-routing-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 905L, 0L, 0L);
            UUID resident = UUID.fromString("00000000-0000-0000-0000-000000000905");
            EconomyService.VillageSnapshot observed = service.observeVillage(
                    new EconomyService.VillageObservation(
                            "minecraft:overworld",
                            1L,
                            5L,
                            0L,
                            1,
                            2,
                            0,
                            false,
                            List.of(new EconomyService.ResidentObservation(
                                    resident, "minecraft:farmer", 1L))));
            UUID villageId = observed.village().villageId;
            require(service.recordVillagerDeath(
                            villageId,
                            resident,
                            "minecraft:farmer",
                            1L,
                            VillageProsperityEngine.IncidentCause.PLAYER,
                            UUID.randomUUID()),
                    "abandoned-village fixture failed");
            UUID donor = UUID.fromString("00000000-0000-0000-0000-000000000906");
            require(service.deposit(donor, 600L), "donor funding failed");
            EconomyService.VillageFundContributionResult result =
                    service.contributeToVillageFund(
                            donor,
                            villageId,
                            500L,
                            EconomyState.ProsperityFundType.DIRECT_GRANT,
                            EconomyState.DonationPurpose.GENERAL);
            require(result.contributed()
                            && result.purpose() == EconomyState.DonationPurpose.RESTORATION,
                    "abandoned-village direct grant was not routed to restoration");
            require(result.donorTitle() == EconomyState.DonorTitle.VILLAGE_PATRON,
                    "donor recognition threshold was incorrect");
            EconomyService.VillageFundContributionResult endowment =
                    service.contributeToVillageFund(
                            donor,
                            villageId,
                            50L,
                            EconomyState.ProsperityFundType.ENDOWMENT,
                            EconomyState.DonationPurpose.GENERAL);
            require(endowment.contributed(), "endowment contribution failed");
            EconomyService reloaded = new EconomyService();
            reloaded.startWithSeed(root, 999L, 0L, 0L);
            require(reloaded.donorSnapshot(donor).title()
                            == EconomyState.DonorTitle.VILLAGE_PATRON,
                    "donor lifetime recognition did not persist");
            EconomyService.VillageFundSnapshot fund = reloaded.villageFundSnapshot(villageId);
            require(fund.lifetimeReceivedMicro() == 550L * EconomyState.MICRO
                            && fund.endowmentPrincipalMicro() == 50L * EconomyState.MICRO,
                    "village fund or protected endowment did not persist");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testFundInputCapacityAndSpendableAccounting() throws Exception {
        EconomyState automatic = EconomyState.fresh(906L, 0L, 0L);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000907");
        EconomyState.VillageRecord village = automatic.village(villageId);
        initializeVillage(village);
        village.foodSupply = 19_999.25;
        village.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.FOOD, 5L * EconomyState.MICRO);
        village.prosperityFund.emergencyReserveMicro = 3L * EconomyState.MICRO;
        village.prosperityFund.projectSponsorshipMicro.put(
                77L, 2L * EconomyState.MICRO);
        require(village.prosperityFund.spendableTotalMicro() == 5L * EconomyState.MICRO,
                "spendable total included protected or restricted balances");
        village.prosperityFund.projectSponsorshipMicro.clear();

        automatic.advanceOneDay(
                false, false, false, true, true, 0.04, 0.20,
                5L * EconomyState.MICRO);
        require(Math.abs(village.foodSupply - 20_000.0) < 1.0e-9,
                "automatic spending did not fill the remaining input capacity");
        require(village.prosperityFund.lifetimeSpentMicro == EconomyState.MICRO
                        && village.prosperityFund.spendableMicro.get(
                                EconomyState.DonationPurpose.FOOD)
                                == 4L * EconomyState.MICRO,
                "automatic spending consumed value beyond the destination capacity");
        automatic.advanceOneDay(
                false, false, false, true, true, 0.04, 0.20,
                5L * EconomyState.MICRO);
        require(village.prosperityFund.lifetimeSpentMicro == EconomyState.MICRO,
                "a saturated village input continued consuming Fund value");

        Path root = Files.createTempDirectory("emerald-manual-fund-capacity-");
        try {
            EconomyState manual = EconomyState.fresh(907L, 0L, 0L);
            EconomyState.VillageRecord manualVillage = manual.village(villageId);
            initializeVillage(manualVillage);
            manualVillage.foodSupply = 19_999.25;
            manualVillage.prosperityFund.spendableMicro.put(
                    EconomyState.DonationPurpose.FOOD, 5L * EconomyState.MICRO);
            manual.save(root.resolve("the_emerald_standard.properties"));
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1L, 0L, 0L);
            EconomyService.VillageFundSpendingResult result = service.spendVillageFund(
                    villageId,
                    EconomyState.DonationPurpose.FOOD,
                    5L * EconomyState.MICRO,
                    0L);
            require(result.spent() && result.amountMicro() == EconomyState.MICRO,
                    "manual spending did not use the shared input-capacity limit");
            EconomyService.VillageFundSnapshot snapshot = service.villageFundSnapshot(villageId);
            require(snapshot.spendableTotalMicro() == 4L * EconomyState.MICRO
                            && snapshot.lifetimeSpentMicro() == EconomyState.MICRO,
                    "manual Fund accounting diverged from automatic spending");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testProjectSponsorshipTargetingAndRollover() throws Exception {
        Path root = Files.createTempDirectory("emerald-project-sponsorship-");
        try {
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000908");
            UUID donor = UUID.fromString("00000000-0000-0000-0000-000000000909");
            EconomyState fixture = EconomyState.fresh(908L, 0L, 0L);
            EconomyState.VillageRecord village = fixture.village(villageId);
            initializeVillage(village);
            EconomyState.VillageProject completed = project(
                    1L, VillageProsperityEngine.ProjectType.COTTAGE, true);
            EconomyState.VillageProject active = project(
                    2L, VillageProsperityEngine.ProjectType.GRANARY, false);
            village.projects.add(completed);
            village.projects.add(active);
            village.projectSerial = 2L;
            fixture.account(donor).cashMicro = 100L * EconomyState.MICRO;
            fixture.save(root.resolve("the_emerald_standard.properties"));

            EconomyService service = new EconomyService();
            service.startWithSeed(root, 1L, 0L, 0L);
            EconomyService.VillageFundContributionResult contribution =
                    service.contributeToVillageFund(
                            donor,
                            villageId,
                            10L,
                            EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP,
                            EconomyState.DonationPurpose.SECURITY);
            require(contribution.contributed()
                            && contribution.projectId() == 2L
                            && contribution.purpose() == EconomyState.DonationPurpose.FOOD,
                    "sponsorship did not target and classify the active project");
            require(service.donorSnapshot(donor).byPurposeMicro().get(
                            EconomyState.DonationPurpose.FOOD)
                            == 10L * EconomyState.MICRO,
                    "sponsorship donor accounting retained the unrelated UI purpose");

            EconomyState funded = EconomyState.load(
                    root.resolve("the_emerald_standard.properties"), 1L, 0L, 0L);
            EconomyState.VillageRecord fundedVillage = funded.villages.get(villageId);
            EconomyState.VillageProject fundedProject = fundedVillage.projects.get(1);
            double materialsBefore = fundedVillage.materialSupply;
            funded.advanceOneDay(
                    false, false, false, true, true, 0.04, 0.20,
                    EconomyState.MICRO);
            require(fundedVillage.prosperityFund.projectSponsorshipMicro.get(2L)
                            == 9L * EconomyState.MICRO
                            && fundedVillage.materialSupply > materialsBefore,
                    "active sponsorship was not spent toward project inputs");

            fundedProject.economicComplete = true;
            fundedProject.economicProgress = 1.0;
            fundedProject.completedDay = funded.economicDay;
            double materialsAtCompletion = fundedVillage.materialSupply;
            double foodAtCompletion = fundedVillage.foodSupply;
            funded.advanceOneDay(
                    false, false, false, true, true, 0.04, 0.20,
                    EconomyState.MICRO);
            require(!fundedVillage.prosperityFund.projectSponsorshipMicro.containsKey(2L)
                            && fundedVillage.prosperityFund.spendableMicro.get(
                                    EconomyState.DonationPurpose.FOOD)
                                    == 8L * EconomyState.MICRO,
                    "completed-project sponsorship did not roll into its derived purpose");
            require(Math.abs(fundedVillage.materialSupply - materialsAtCompletion) < 1.0e-9
                            && fundedVillage.foodSupply > foodAtCompletion,
                    "completed sponsorship kept draining as project infrastructure");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testPassiveInterestLedgerCoalescing() throws Exception {
        Path root = Files.createTempDirectory("emerald-interest-ledger-");
        try {
            EconomyState state = EconomyState.fresh(909L, 0L, 0L);
            UUID player = UUID.fromString("00000000-0000-0000-0000-000000000910");
            EconomyState.Account account = state.account(player);
            account.savingsMicro = 1_000L * EconomyState.MICRO;
            PortfolioAnalytics.recordTransaction(
                    account,
                    0L,
                    EconomyState.PortfolioTransactionKind.BUY,
                    "VILX",
                    0L,
                    1.0,
                    100L * EconomyState.MICRO,
                    100L * EconomyState.MICRO,
                    0L);
            for (int day = 0; day < 400; day++) {
                state.advanceOneDay(
                        false, false, false, true, false, 0.04, 0.20,
                        EconomyState.MICRO);
            }
            require(account.transactionLedger.stream().anyMatch(entry ->
                            entry.kind == EconomyState.PortfolioTransactionKind.BUY),
                    "passive interest evicted an active portfolio event");
            List<EconomyState.PortfolioTransaction> interest = account.transactionLedger.stream()
                    .filter(entry -> entry.kind == EconomyState.PortfolioTransactionKind.INTEREST)
                    .toList();
            require(interest.size() == 1
                            && interest.getFirst().quantity == 400.0
                            && interest.getFirst().amountMicro == account.realizedGainMicro,
                    "passive interest was not coalesced into an honest aggregate");

            Path save = root.resolve("the_emerald_standard.properties");
            state.save(save);
            EconomyState reloaded = EconomyState.load(save, 1L, 0L, 0L);
            EconomyState.Account restored = reloaded.accounts.get(player);
            require(restored.transactionLedger.size() == 2
                            && restored.transactionLedger.stream().filter(entry ->
                                    entry.kind == EconomyState.PortfolioTransactionKind.INTEREST)
                                    .findFirst().orElseThrow().quantity == 400.0,
                    "coalesced interest did not persist faithfully");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void initializeVillage(EconomyState.VillageRecord village) {
        village.population = 5;
        village.observedPopulation = 5;
        village.housingCapacity = 8;
        village.foodSupply = 20.0;
        village.safety = 60.0;
    }

    private static EconomyState.VillageProject project(
            long id, VillageProsperityEngine.ProjectType type, boolean complete) {
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = id;
        project.type = type;
        project.approvedDay = 0L;
        project.completedDay = 0L;
        project.economicProgress = complete ? 1.0 : 0.0;
        project.economicComplete = complete;
        project.totalBlocks = type.nominalBlocks();
        return project;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
