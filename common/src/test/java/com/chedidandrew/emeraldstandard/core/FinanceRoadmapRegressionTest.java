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
        testFastTrackCapitalIsNeedBounded();
        testFastTrackLaborIsAtomicAndSourceAware();
        testFastTrackSourceSeparationAndRoutineCap();
        testFundInputCapacityAndSpendableAccounting();
        testProjectSponsorshipTargetingAndRollover();
        testManualSponsorshipHonorsVisualMode();
        testDonationRoutingAndRecognition();
        testVillageFundRejectsAccountingOverflow();
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

            int transactionsBeforeDustSale = bought.transactions().size();
            require(!service.sell(player, ticker, 1.0e-12),
                    "Sub-micro sale was accepted without any proceeds");
            PortfolioAnalytics.PortfolioSnapshot afterDustSale =
                    service.portfolioAnalyticsSnapshot(player);
            require(afterDustSale.positions().get(ticker).shares() == position.shares()
                            && afterDustSale.positions().get(ticker).costBasisMicro()
                                    == position.costBasisMicro()
                            && afterDustSale.transactions().size() == transactionsBeforeDustSale,
                    "Rejected sub-micro sale changed the holding or its accounting history");

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

    private static void testVillageFundRejectsAccountingOverflow() throws Exception {
        Path root = Files.createTempDirectory("emerald-fund-overflow-");
        try {
            UUID donor = UUID.fromString("00000000-0000-0000-0000-000000000919");
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000920");
            EconomyState state = EconomyState.fresh(919L, 0L, 0L);
            state.account(donor).cashMicro = 2L * EconomyState.MICRO;
            EconomyState.VillageRecord village = state.village(villageId);
            village.prosperityFund.lifetimeReceivedMicro = Long.MAX_VALUE;
            state.save(root.resolve("the_emerald_standard.properties"));

            EconomyService service = new EconomyService();
            service.startWithSeed(root, 999L, 0L, 0L);
            EconomyService.VillageFundContributionResult contribution =
                    service.contributeToVillageFund(
                            donor,
                            villageId,
                            1L,
                            EconomyState.ProsperityFundType.DIRECT_GRANT,
                            EconomyState.DonationPurpose.GENERAL);
            require(!contribution.contributed(),
                    "Contribution was debited after a Fund counter reached capacity");
            require(service.snapshot().account(donor).cashMicro
                            == 2L * EconomyState.MICRO
                            && service.villageFundSnapshot(villageId)
                                    .spendableTotalMicro() == 0L,
                    "Rejected overflowing contribution changed a financial balance");
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
            EconomyState persisted = EconomyState.load(
                    root.resolve("the_emerald_standard.properties"), 1L, 0L, 0L);
            require(persisted.villages.get(villageId).prosperityFund.fastTrackSpendableMicro.get(
                            EconomyState.DonationPurpose.RESTORATION)
                            == 500L * EconomyState.MICRO,
                    "Direct Grant liquid capital did not retain fast-track provenance");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testFastTrackCapitalIsNeedBounded() throws Exception {
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-00000000090a");
        EconomyState accelerated = EconomyState.fresh(910L, 0L, 0L);
        EconomyState.VillageRecord village = accelerated.village(villageId);
        initializeVillage(village);
        village.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        village.safety = 0.0;
        village.materialSupply = 87.38888848687043;
        village.treasury = 18.61154850817976;
        village.developmentPoints = 8.786538391958349;
        long contribution = 16_264L * EconomyState.MICRO;
        village.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        village.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        village.prosperityFund.fastTrackProvenanceKnown = true;
        village.prosperityFund.emergencyReserveMicro = 4_068_600_000L;
        village.prosperityFund.lifetimeReceivedMicro = 20_343L * EconomyState.MICRO;
        village.prosperityFund.lifetimeSpentMicro = 10_400_000L;

        long routineDailyCap = 800_000L;
        long spentBefore = village.prosperityFund.lifetimeSpentMicro;
        long reserveBefore = village.prosperityFund.emergencyReserveMicro;
        accelerated.advanceOneDay(
                true,
                false,
                false,
                true,
                true,
                0.04,
                0.20,
                routineDailyCap,
                true,
                false);

        require(village.projects.size() == 1
                        && village.projects.getFirst().type
                                == VillageProsperityEngine.ProjectType.GUARD_POST
                        && village.projects.getFirst().approvedDay == accelerated.economicDay,
                "fast-track capital did not approve the exact needed Guard Post on the same day");
        long spent = village.prosperityFund.lifetimeSpentMicro - spentBefore;
        require(spent > VillageProsperityEngine.projectLaborCostMicro(
                                VillageProsperityEngine.ProjectType.GUARD_POST)
                        + routineDailyCap
                        && spent < 500L * EconomyState.MICRO,
                "exact project capital and labor remained trapped behind the routine daily cap");
        require(village.prosperityFund.spendableTotalMicro()
                        > contribution - 500L * EconomyState.MICRO,
                "fast-track capital drained money beyond one project's immediate needs");
        require(village.prosperityFund.emergencyReserveMicro == reserveBefore
                        && village.prosperityFund.spentTodayMicro == routineDailyCap,
                "fast-track capital consumed the emergency reserve or erased routine relief");
        require(village.projects.getFirst().economicComplete
                        && village.projects.getFirst().completedDay == accelerated.economicDay
                        && village.projects.getFirst().abstractOnly
                        && !village.projects.getFirst().materializedComplete,
                "fast-track labor did not finish economics while preserving guarded construction");

        EconomyState.VillageRecord surplusInputs = EconomyState.fresh(911L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-00000000090b"));
        initializeVillage(surplusInputs);
        surplusInputs.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        surplusInputs.safety = 0.0;
        surplusInputs.materialSupply = 500.0;
        surplusInputs.treasury = 100.0;
        surplusInputs.developmentPoints = 1.0;
        surplusInputs.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 100L * EconomyState.MICRO);
        surplusInputs.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 100L * EconomyState.MICRO);
        long surplusSpent = VillageProsperityEngine.fastTrackNextProjectFromFund(
                surplusInputs, 911L, 1L);
        require(surplusSpent > 0L
                        && surplusInputs.projects.size() == 1
                        && Math.abs(surplusInputs.materialSupply - 335.0) < 1.0e-9
                        && Math.abs(surplusInputs.treasury - 74.0) < 1.0e-9,
                "capital top-up destroyed project inputs that were already above the requirement");

        EconomyState passiveOnly = EconomyState.fresh(910L, 0L, 0L);
        EconomyState.VillageRecord passiveVillage = passiveOnly.village(villageId);
        initializeVillage(passiveVillage);
        passiveVillage.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        passiveVillage.safety = 0.0;
        passiveVillage.materialSupply = 87.4;
        passiveVillage.treasury = 18.6;
        passiveVillage.developmentPoints = 8.8;
        passiveVillage.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        passiveVillage.prosperityFund.fastTrackProvenanceKnown = true;
        passiveOnly.advanceOneDay(
                true,
                false,
                false,
                true,
                true,
                0.04,
                0.20,
                routineDailyCap,
                true,
                false);
        require(passiveVillage.projects.isEmpty()
                        && passiveVillage.prosperityFund.lifetimeSpentMicro == routineDailyCap,
                "passive or Endowment-derived funds bypassed the routine spending limit");

        EconomyState simulationOff = EconomyState.fresh(912L, 0L, 0L);
        EconomyState.VillageRecord pausedVillage = simulationOff.village(villageId);
        initializeVillage(pausedVillage);
        pausedVillage.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        pausedVillage.safety = 0.0;
        pausedVillage.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        pausedVillage.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        simulationOff.advanceOneDay(
                false,
                false,
                false,
                true,
                true,
                0.04,
                0.20,
                routineDailyCap,
                true,
                false);
        require(pausedVillage.projects.isEmpty()
                        && pausedVillage.prosperityFund.lifetimeSpentMicro == routineDailyCap,
                "fast-track capital planned a project while village simulation was disabled");

        EconomyState.VillageRecord insufficient = EconomyState.fresh(913L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-00000000090c"));
        initializeVillage(insufficient);
        insufficient.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        insufficient.safety = 0.0;
        insufficient.materialSupply = 80.0;
        insufficient.treasury = 10.0;
        insufficient.developmentPoints = 2.0;
        insufficient.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 10L * EconomyState.MICRO);
        insufficient.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 10L * EconomyState.MICRO);
        require(VillageProsperityEngine.fastTrackNextProjectFromFund(
                                insufficient, 913L, 1L) == 0L
                        && insufficient.projects.isEmpty()
                        && insufficient.prosperityFund.spendableTotalMicro()
                                == 10L * EconomyState.MICRO
                        && insufficient.materialSupply == 80.0
                        && insufficient.treasury == 10.0
                        && insufficient.developmentPoints == 2.0,
                "an underfunded fast-track attempt consumed money or village inputs");

        EconomyState.VillageRecord starving = EconomyState.fresh(914L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-00000000090d"));
        initializeVillage(starving);
        starving.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        starving.safety = 35.0;
        starving.foodSupply = 0.0;
        starving.materialSupply = 0.0;
        starving.treasury = 0.0;
        starving.developmentPoints = 0.0;
        starving.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.FOOD, 1_000L * EconomyState.MICRO);
        starving.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.FOOD, 1_000L * EconomyState.MICRO);
        require(VillageProsperityEngine.fastTrackNextProjectFromFund(
                                starving, 914L, 1L) == 0L
                        && starving.projects.isEmpty()
                        && starving.prosperityFund.spendableTotalMicro()
                                == 1_000L * EconomyState.MICRO,
                "acute food relief was diverted into Granary capital");

        EconomyState.VillageRecord zeroPopulation = EconomyState.fresh(915L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-00000000090e"));
        zeroPopulation.lifecycle = VillageProsperityEngine.Lifecycle.ACTIVE;
        zeroPopulation.housingCapacity = 1;
        zeroPopulation.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        zeroPopulation.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        require(VillageProsperityEngine.fastTrackNextProjectFromFund(
                                zeroPopulation, 915L, 1L) == 0L
                        && zeroPopulation.projects.isEmpty(),
                "a zero-population village planned a fast-track project");

        EconomyState.VillageRecord abandoned = EconomyState.fresh(916L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-00000000090f"));
        initializeVillage(abandoned);
        abandoned.lifecycle = VillageProsperityEngine.Lifecycle.ABANDONED;
        abandoned.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        abandoned.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, contribution);
        require(VillageProsperityEngine.fastTrackNextProjectFromFund(abandoned, 916L, 1L) == 0L
                        && abandoned.projects.isEmpty(),
                "a populated abandoned village bypassed restoration with a new project");

        testLegacyFastTrackProvenanceMigration();
    }

    private static void testFastTrackLaborIsAtomicAndSourceAware() {
        EconomyState.VillageRecord funded = EconomyState.fresh(918L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-000000000911"));
        initializeVillage(funded);
        EconomyState.VillageProject active = project(
                1L, VillageProsperityEngine.ProjectType.GUARD_POST, false);
        active.economicProgress = 0.5;
        funded.projects.add(active);
        funded.projectSerial = 1L;

        long needed = VillageProsperityEngine.remainingProjectLaborCostMicro(active);
        long sponsorship = 20L * EconomyState.MICRO;
        long purposeCapital = 30L * EconomyState.MICRO;
        long generalCapital = needed - sponsorship - purposeCapital;
        long passiveSecurity = 40L * EconomyState.MICRO;
        long passiveGeneral = 60L * EconomyState.MICRO;
        EconomyState.ProsperityFund fund = funded.prosperityFund;
        fund.projectSponsorshipMicro.put(active.projectId, sponsorship);
        fund.spendableMicro.put(
                EconomyState.DonationPurpose.SECURITY, purposeCapital + passiveSecurity);
        fund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.SECURITY, purposeCapital);
        fund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, generalCapital + passiveGeneral);
        fund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, generalCapital);
        fund.fastTrackProvenanceKnown = true;
        fund.lastSpendingDay = 2L;
        fund.spentTodayMicro = 7L * EconomyState.MICRO;
        fund.lifetimeSpentMicro = 11L * EconomyState.MICRO;
        long lifetimeBefore = fund.lifetimeSpentMicro;

        long spent = VillageProsperityEngine.fastTrackActiveProjectLaborFromFund(
                funded, 2L, false);
        require(spent == needed
                        && active.economicComplete
                        && active.economicProgress == 1.0
                        && active.completedDay == 2L
                        && active.abstractOnly
                        && !active.materializedComplete,
                "fully funded labor did not complete exactly one economic project");
        require(!fund.projectSponsorshipMicro.containsKey(active.projectId)
                        && fund.spendableMicro.get(EconomyState.DonationPurpose.SECURITY)
                                == passiveSecurity
                        && fund.spendableMicro.get(EconomyState.DonationPurpose.GENERAL)
                                == passiveGeneral
                        && fund.fastTrackSpendableMicro.getOrDefault(
                                        EconomyState.DonationPurpose.SECURITY, 0L)
                                == 0L
                        && fund.fastTrackSpendableMicro.getOrDefault(
                                        EconomyState.DonationPurpose.GENERAL, 0L)
                                == 0L,
                "labor fast-track did not debit sponsorship, purpose, and General in order");
        require(fund.lifetimeSpentMicro == lifetimeBefore + needed
                        && fund.spentTodayMicro == 7L * EconomyState.MICRO,
                "labor fast-track consumed the routine daily allowance");

        EconomyState.VillageRecord underfunded = EconomyState.fresh(919L, 0L, 0L)
                .village(UUID.fromString("00000000-0000-0000-0000-000000000912"));
        initializeVillage(underfunded);
        EconomyState.VillageProject waiting = project(
                1L, VillageProsperityEngine.ProjectType.COTTAGE, false);
        waiting.economicProgress = 0.25;
        underfunded.projects.add(waiting);
        underfunded.projectSerial = 1L;
        long remaining = VillageProsperityEngine.remainingProjectLaborCostMicro(waiting);
        long housingCapital = 30L * EconomyState.MICRO;
        long almostEnoughGeneral = remaining - sponsorship - housingCapital - 1L;
        EconomyState.ProsperityFund underfundedFund = underfunded.prosperityFund;
        underfundedFund.projectSponsorshipMicro.put(waiting.projectId, sponsorship);
        underfundedFund.spendableMicro.put(
                EconomyState.DonationPurpose.HOUSING,
                housingCapital + 50L * EconomyState.MICRO);
        underfundedFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.HOUSING, housingCapital);
        underfundedFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL,
                almostEnoughGeneral + 60L * EconomyState.MICRO);
        underfundedFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, almostEnoughGeneral);
        underfundedFund.fastTrackProvenanceKnown = true;

        require(VillageProsperityEngine.fastTrackActiveProjectLaborFromFund(
                                underfunded, 3L, true) == 0L
                        && waiting.economicProgress == 0.25
                        && !waiting.economicComplete
                        && underfundedFund.projectSponsorshipMicro.get(waiting.projectId)
                                == sponsorship
                        && underfundedFund.fastTrackSpendableMicro.get(
                                        EconomyState.DonationPurpose.HOUSING)
                                == housingCapital
                        && underfundedFund.fastTrackSpendableMicro.get(
                                        EconomyState.DonationPurpose.GENERAL)
                                == almostEnoughGeneral
                        && underfundedFund.lifetimeSpentMicro == 0L,
                "underfunded labor used passive money or partially drained eligible sources");
    }

    private static void testLegacyFastTrackProvenanceMigration() throws Exception {
        Path root = Files.createTempDirectory("emerald-fast-track-migration-");
        try {
            UUID villageId = UUID.fromString("73655260-963c-bea6-3fec-e14abc6f8985");
            UUID donorId = UUID.fromString("ae6cd2eb-90e0-375b-babc-c41851a58e79");
            EconomyState state = EconomyState.fresh(916L, 0L, 0L);
            state.economicDay = 14L;
            state.liveMarket=LiveMarket.adopt(state); // Explicit synthetic fixture date.
            EconomyState.VillageRecord village = state.village(villageId);
            initializeVillage(village);
            village.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
            village.safety = 0.0;
            village.materialSupply = 87.38888848687043;
            village.treasury = 18.61154850817976;
            village.developmentPoints = 8.786538391958349;
            village.prosperityFund.spendableMicro.put(
                    EconomyState.DonationPurpose.GENERAL, 16_264L * EconomyState.MICRO);
            village.prosperityFund.emergencyReserveMicro = 4_068_600_000L;
            village.prosperityFund.lifetimeReceivedMicro = 20_343L * EconomyState.MICRO;
            village.prosperityFund.lifetimeSpentMicro = 10_400_000L;
            EconomyState.FundContribution contribution = new EconomyState.FundContribution();
            contribution.day = 1L;
            contribution.donorId = donorId;
            contribution.type = EconomyState.ProsperityFundType.DIRECT_GRANT;
            contribution.purpose = EconomyState.DonationPurpose.GENERAL;
            contribution.amountMicro = 20_343L * EconomyState.MICRO;
            village.prosperityFund.contributions.add(contribution);

            Path save = root.resolve("the_emerald_standard.properties");
            state.save(save);
            Properties properties = RegressionTestSupport.readProperties(save);
            String fundPrefix = "village." + villageId + ".fund.";
            properties.stringPropertyNames().stream()
                    .filter(key -> key.startsWith(fundPrefix + "fast_track"))
                    .toList()
                    .forEach(properties::remove);
            RegressionTestSupport.refreshChecksum(properties);
            RegressionTestSupport.writeProperties(save, properties);

            EconomyState migrated = EconomyState.load(save, 1L, 0L, 0L);
            EconomyState.VillageRecord migratedVillage = migrated.villages.get(villageId);
            require(migratedVillage.prosperityFund.fastTrackProvenanceKnown
                            && migratedVillage.prosperityFund.fastTrackSpendableMicro.get(
                                    EconomyState.DonationPurpose.GENERAL)
                                    == 16_264L * EconomyState.MICRO,
                    "legacy Fund migration did not recover the provable retained Direct Grant");
            migrated.validate();
            migrated.save(save);
            EconomyState reloaded = EconomyState.load(save, 2L, 0L, 0L);
            require(reloaded.villages.get(villageId).prosperityFund.fastTrackSpendableMicro.get(
                            EconomyState.DonationPurpose.GENERAL)
                            == 16_264L * EconomyState.MICRO,
                    "persisted provenance was lost or reconstructed twice after reload");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testFastTrackSourceSeparationAndRoutineCap() {
        EconomyState state = EconomyState.fresh(917L, 0L, 0L);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000910");
        EconomyState.VillageRecord village = state.village(villageId);
        initializeVillage(village);
        village.prosperityFund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 120L * EconomyState.MICRO);
        village.prosperityFund.fastTrackSpendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 20L * EconomyState.MICRO);
        long principal = 36_500L * EconomyState.MICRO;
        village.prosperityFund.endowmentPrincipalMicro.put(
                EconomyState.DonationPurpose.GENERAL, principal);

        long routineCap = 5L * EconomyState.MICRO;
        state.advanceOneDay(
                false,
                false,
                false,
                true,
                true,
                0.04,
                0.0,
                routineCap,
                true,
                false);
        require(village.prosperityFund.spentTodayMicro == routineCap
                        && village.prosperityFund.lifetimeSpentMicro == routineCap,
                "routine spending did not retain its independent exact daily cap");
        require(village.prosperityFund.fastTrackSpendableMicro.get(
                                EconomyState.DonationPurpose.GENERAL)
                        == 20L * EconomyState.MICRO,
                "routine spending consumed player capital before passive proceeds");
        require(village.prosperityFund.endowmentPrincipalTotalMicro() == principal
                        && village.prosperityFund.spendableTotalMicro()
                                > 115L * EconomyState.MICRO,
                "Endowment payout gained fast-track provenance or principal was spent");
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
            manualVillage.prosperityFund.fastTrackSpendableMicro.put(
                    EconomyState.DonationPurpose.FOOD, 5L * EconomyState.MICRO);
            manualVillage.prosperityFund.spendableMicro.put(
                    EconomyState.DonationPurpose.GENERAL, 10L * EconomyState.MICRO);
            manualVillage.prosperityFund.fastTrackSpendableMicro.put(
                    EconomyState.DonationPurpose.GENERAL, 10L * EconomyState.MICRO);
            manualVillage.prosperityFund.fastTrackProvenanceKnown = true;
            manual.save(root.resolve("the_emerald_standard.properties"));
            EconomyService service = new EconomyService();
            service.configureProsperityFund(new EconomyService.ProsperityFundPolicy(
                    true, 0.04, 0.20, 2L * EconomyState.MICRO, true));
            service.startWithSeed(root, 1L, 0L, 0L);
            EconomyService.VillageFundSpendingResult result = service.spendVillageFund(
                    villageId,
                    EconomyState.DonationPurpose.FOOD,
                    5L * EconomyState.MICRO,
                    0L);
            require(result.spent() && result.amountMicro() == EconomyState.MICRO,
                    "manual spending did not use the shared input-capacity limit");
            EconomyService.VillageFundSpendingResult second = service.spendVillageFund(
                    villageId,
                    EconomyState.DonationPurpose.GENERAL,
                    5L * EconomyState.MICRO,
                    0L);
            EconomyService.VillageFundSpendingResult exhausted = service.spendVillageFund(
                    villageId,
                    EconomyState.DonationPurpose.GENERAL,
                    EconomyState.MICRO,
                    0L);
            require(second.spent()
                            && second.amountMicro() == EconomyState.MICRO
                            && !exhausted.spent(),
                    "manual Fund spending did not exhaust the configured same-day cap exactly");
            EconomyService.VillageFundSnapshot snapshot = service.villageFundSnapshot(villageId);
            require(snapshot.spendableTotalMicro() == 13L * EconomyState.MICRO
                            && snapshot.lifetimeSpentMicro() == 2L * EconomyState.MICRO,
                    "manual Fund accounting diverged from automatic spending");
            EconomyState persisted = EconomyState.load(
                    root.resolve("the_emerald_standard.properties"), 1L, 0L, 0L);
            require(persisted.villages.get(villageId).prosperityFund.fastTrackSpendableMicro.get(
                                    EconomyState.DonationPurpose.FOOD)
                            == 4L * EconomyState.MICRO
                            && persisted.villages.get(villageId)
                                    .prosperityFund.fastTrackSpendableMicro.get(
                                            EconomyState.DonationPurpose.GENERAL)
                                    == 9L * EconomyState.MICRO,
                    "manual routine spending did not preserve fast-track provenance on save");
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
            EconomyService.VillageFundSpendingResult manualLabor = service.spendVillageFund(
                    villageId,
                    EconomyState.DonationPurpose.FOOD,
                    EconomyState.MICRO,
                    2L);
            require(manualLabor.spent() && manualLabor.amountMicro() == EconomyState.MICRO,
                    "manual project sponsorship did not purchase project labor");

            EconomyState funded = EconomyState.load(
                    root.resolve("the_emerald_standard.properties"), 1L, 0L, 0L);
            EconomyState.VillageRecord fundedVillage = funded.villages.get(villageId);
            EconomyState.VillageProject fundedProject = fundedVillage.projects.get(1);
            require(fundedVillage.prosperityFund.projectSponsorshipMicro.get(2L)
                            == 9L * EconomyState.MICRO
                            && fundedProject.economicProgress > 0.0
                            && fundedVillage.materialSupply == 0.0,
                    "manual sponsorship changed inputs instead of persisted labor progress");
            double materialsBefore = fundedVillage.materialSupply;
            double progressBefore = fundedProject.economicProgress;
            funded.advanceOneDay(
                    false, false, false, true, true, 0.04, 0.20,
                    EconomyState.MICRO);
            require(fundedVillage.prosperityFund.projectSponsorshipMicro.get(2L)
                            == 8L * EconomyState.MICRO
                            && fundedProject.economicProgress > progressBefore
                            && fundedVillage.materialSupply == materialsBefore,
                    "routine sponsorship did not advance project labor exclusively");

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
                                    == 7L * EconomyState.MICRO
                            && fundedVillage.prosperityFund.fastTrackSpendableMicro.get(
                                    EconomyState.DonationPurpose.FOOD)
                                    == 7L * EconomyState.MICRO,
                    "completed-project sponsorship did not roll into its derived purpose");
            require(Math.abs(fundedVillage.materialSupply - materialsAtCompletion) < 1.0e-9
                            && fundedVillage.foodSupply > foodAtCompletion,
                    "completed sponsorship kept draining as project infrastructure");
            funded.save(root.resolve("the_emerald_standard.properties"));
            EconomyState rolloverReloaded = EconomyState.load(
                    root.resolve("the_emerald_standard.properties"), 1L, 0L, 0L);
            require(rolloverReloaded.villages.get(villageId)
                            .prosperityFund.fastTrackSpendableMicro.get(
                                    EconomyState.DonationPurpose.FOOD)
                            == 7L * EconomyState.MICRO,
                    "rolled sponsorship fast-track provenance did not survive reload");
        } finally {
            RegressionTestSupport.deleteTree(root);
        }
    }

    private static void testManualSponsorshipHonorsVisualMode() throws Exception {
        Path root = Files.createTempDirectory("emerald-manual-project-labor-");
        try {
            UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000000913");
            UUID donor = UUID.fromString("00000000-0000-0000-0000-000000000914");
            EconomyState fixture = EconomyState.fresh(920L, 0L, 0L);
            EconomyState.VillageRecord village = fixture.village(villageId);
            initializeVillage(village);
            EconomyState.VillageProject active = project(
                    1L, VillageProsperityEngine.ProjectType.COTTAGE, false);
            active.economicProgress = 0.99;
            village.projects.add(active);
            village.projectSerial = 1L;
            long remainingLabor =
                    VillageProsperityEngine.remainingProjectLaborCostMicro(active);
            fixture.account(donor).cashMicro = 2L * EconomyState.MICRO;
            fixture.save(root.resolve("the_emerald_standard.properties"));

            EconomyService service = new EconomyService();
            service.configureVillageProsperity(true, false);
            service.startWithSeed(root, 1L, 0L, 0L);
            require(service.contributeToVillageFund(
                            donor,
                            villageId,
                            2L,
                            EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP,
                            EconomyState.DonationPurpose.HOUSING)
                            .contributed(),
                    "manual-labor sponsorship contribution failed");
            EconomyService.VillageFundSpendingResult result = service.spendVillageFund(
                    villageId,
                    EconomyState.DonationPurpose.HOUSING,
                    2L * EconomyState.MICRO,
                    active.projectId);
            require(result.spent() && result.amountMicro() == remainingLabor,
                    "manual sponsorship did not stop at the exact remaining labor cost");

            EconomyState persisted = EconomyState.load(
                    root.resolve("the_emerald_standard.properties"), 1L, 0L, 0L);
            EconomyState.VillageProject completed =
                    persisted.villages.get(villageId).projects.getFirst();
            require(completed.economicComplete
                            && completed.economicProgress == 1.0
                            && completed.abstractOnly
                            && !completed.materializedComplete,
                    "manual sponsorship ignored disabled visual progression on completion");
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
