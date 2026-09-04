package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Correctness and measured 100/500/1,000-record scale coverage for spatial queries and saves. */
public final class ScalingAndSpatialIndexRegressionTest {
    private static final int[] SCALES = {100, 500, 1_000};
    private static final int QUERY_COUNT = 2_000;
    private static final int MATURE_ACCOUNT_COUNT = 100;
    private static final int MATURE_HISTORY_DAYS = 365;
    private static final int MATURE_LEDGER_ENTRIES = 128;
    private static final long MAX_SUITE_MILLIS = 30_000L;

    private ScalingAndSpatialIndexRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        long suiteStart = System.nanoTime();
        verifyBoundaryTieUpdateAndRemoval();
        verifyServiceRebuildsPersistedIndex();
        for (int scale : SCALES) {
            runScale(scale);
        }
        runMatureStatePersistenceScale();
        long suiteMillis = elapsedMillis(suiteStart);
        require(suiteMillis <= MAX_SUITE_MILLIS,
                "Scaling suite exceeded generous CI ceiling: " + suiteMillis + " ms");
        System.out.println("PASS indexed query and persistence scale guard in "
                + suiteMillis + " ms");
    }

    private static void runScale(int scale) throws Exception {
        EconomyState state = populatedState(scale);
        VillageSpatialIndex index = new VillageSpatialIndex();
        long indexStart = System.nanoTime();
        index.rebuild(state.villages);
        long indexMillis = elapsedMillis(indexStart);
        require(index.size() == scale, "Index omitted villages at scale " + scale);

        Random random = new Random(0x454D4552414C44L + scale);
        List<Query> queries = new ArrayList<>(QUERY_COUNT);
        List<Long> loadedPositions = new ArrayList<>();
        for (int query = 0; query < QUERY_COUNT; query++) {
            String dimension = query % 7 == 0
                    ? "minecraft:the_nether"
                    : "minecraft:overworld";
            long position = pack(
                    random.nextInt(scale * 40 + 1) - scale * 20,
                    48 + random.nextInt(80),
                    random.nextInt(scale * 40 + 1) - scale * 20);
            double radius = 16.0 + random.nextInt(241);
            queries.add(new Query(dimension, position, radius));
            if (query < 64) {
                loadedPositions.add(position);
            }
        }
        for (int query = 0; query < Math.min(250, queries.size()); query++) {
            Query sample = queries.get(query);
            EconomyState.VillageRecord expected = bruteNearest(
                    state.villages, sample.dimension(), sample.position(), sample.radius());
            EconomyState.VillageRecord actual = index.nearest(
                    state.villages, sample.dimension(), sample.position(), sample.radius());
            require(id(expected).equals(id(actual)),
                    "Indexed nearest lookup diverged at scale " + scale);
        }

        long queryStart = System.nanoTime();
        int queryHits = 0;
        for (Query query : queries) {
            EconomyState.VillageRecord found = index.nearest(
                    state.villages, query.dimension(), query.position(), query.radius());
            if (found != null) {
                queryHits++;
            }
        }
        long queryMillis = elapsedMillis(queryStart);

        List<UUID> expectedNear = bruteNearAny(
                state.villages, "minecraft:overworld", loadedPositions, 128.0);
        List<UUID> actualNear = index.nearAny(
                        state.villages,
                        "minecraft:overworld",
                        loadedPositions,
                        128.0)
                .stream()
                .map(village -> village.villageId)
                .toList();
        require(expectedNear.equals(actualNear),
                "Indexed multi-origin lookup diverged at scale " + scale);

        Path directory = Files.createTempDirectory("emerald-standard-scale-");
        Path save = directory.resolve("the_emerald_standard.properties");
        long saveMillis;
        long loadMillis;
        long bytes;
        try {
            long saveStart = System.nanoTime();
            state.save(save);
            saveMillis = elapsedMillis(saveStart);
            bytes = Files.size(save);

            long loadStart = System.nanoTime();
            EconomyState loaded = EconomyState.load(save, -1L, 0L, 0L);
            loadMillis = elapsedMillis(loadStart);
            require(loaded.villages.size() == scale,
                    "Save/reload lost villages at scale " + scale);
            require(loaded.accounts.size() == scale,
                    "Save/reload lost accounts at scale " + scale);
            UUID sampleAccount = accountId(scale - 1);
            require(loaded.accounts.get(sampleAccount).cashMicro
                            == (long) scale * EconomyState.MICRO,
                    "Save/reload changed account data at scale " + scale);
        } finally {
            deleteIfExists(save.resolveSibling(save.getFileName() + ".tmp"));
            deleteIfExists(save.resolveSibling(save.getFileName() + ".bak"));
            deleteIfExists(save);
            deleteIfExists(directory);
        }

        System.out.printf(
                "SCALE records=%d index_ms=%d queries=%d hits=%d query_ms=%d save_ms=%d load_ms=%d bytes=%d%n",
                scale,
                indexMillis,
                QUERY_COUNT,
                queryHits,
                queryMillis,
                saveMillis,
                loadMillis,
                bytes);
    }

    private static EconomyState populatedState(int scale) {
        EconomyState state = EconomyState.fresh(0x95A00000L + scale, 10_000L, 20_000L);
        for (int index = 0; index < scale; index++) {
            UUID villageId = villageId(index);
            EconomyState.VillageRecord village = new EconomyState.VillageRecord();
            village.villageId = villageId;
            village.dimensionKey = index % 11 == 0
                    ? "minecraft:the_nether"
                    : "minecraft:overworld";
            int gridWidth = Math.max(10, (int) Math.ceil(Math.sqrt(scale)));
            int x = (index % gridWidth) * 96 - gridWidth * 48;
            int z = (index / gridWidth) * 96 - gridWidth * 48;
            village.centerPos = pack(x, 64 + index % 8, z);
            village.population = 4 + index % 24;
            village.observedPopulation = village.population;
            village.housingCapacity = village.population + 4;
            village.foodSupply = 200.0 + index % 100;
            village.materialSupply = 150.0 + index % 100;
            village.treasury = 50.0 + index % 50;
            state.villages.put(villageId, village);

            EconomyState.Account account = state.account(accountId(index));
            account.cashMicro = (long) (index + 1) * EconomyState.MICRO;
            account.savingsMicro = (long) (index % 100) * EconomyState.MICRO;
            account.shares.put("VILX", index / 10.0);
        }
        return state;
    }

    /** Exercises persistence at scale with realistic mature records, not empty account shells. */
    private static void runMatureStatePersistenceScale() throws Exception {
        EconomyState state = EconomyState.fresh(0x4D41545552454CL, 10_000L, 20_000L);
        state.economicDay = MATURE_HISTORY_DAYS;

        EconomyState.VillageRecord village = new EconomyState.VillageRecord();
        village.villageId = villageId(10_000);
        village.dimensionKey = "minecraft:overworld";
        village.centerPos = pack(160, 64, -160);
        village.lastSimulatedDay = state.economicDay;
        village.lastCensusDay = state.economicDay;
        village.population = 12;
        village.observedPopulation = 12;
        village.housingCapacity = 18;
        village.foodSupply = 800.0;
        village.materialSupply = 600.0;
        village.treasury = 300.0;
        village.developmentPoints = 120.0;
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = 1L;
        project.type = VillageProsperityEngine.ProjectType.WAREHOUSE;
        project.approvedDay = 300L;
        project.economicProgress = 0.75;
        project.totalBlocks = project.type.nominalBlocks();
        village.projects.add(project);
        village.projectSerial = project.projectId;
        state.villages.put(village.villageId, village);

        for (int accountIndex = 0; accountIndex < MATURE_ACCOUNT_COUNT; accountIndex++) {
            UUID id = accountId(10_000 + accountIndex);
            EconomyState.Account account = state.account(id);
            account.cashMicro = (1_000L + accountIndex) * EconomyState.MICRO;
            account.savingsMicro = (500L + accountIndex) * EconomyState.MICRO;
            account.totalContributionsMicro = 2_000L * EconomyState.MICRO;
            account.totalWithdrawalsMicro = 500L * EconomyState.MICRO;
            account.realizedGainMicro = (accountIndex - 50L) * EconomyState.MICRO;
            for (int assetIndex = 0; assetIndex < EconomyEngine.ASSETS.size(); assetIndex++) {
                String ticker = EconomyEngine.ASSETS.get(assetIndex).ticker();
                double shares = 1.0 + accountIndex * 0.01 + assetIndex * 0.10;
                account.shares.put(ticker, shares);
                account.shareCostBasisMicro.put(
                        ticker, (100L + assetIndex * 10L) * EconomyState.MICRO);
            }
            for (int positionIndex = 0;
                    positionIndex < EconomyState.MAX_TERM_POSITIONS;
                    positionIndex++) {
                EconomyState.CdPosition cd = new EconomyState.CdPosition();
                cd.positionId = positionIndex + 1L;
                cd.principalMicro = (20L + positionIndex) * EconomyState.MICRO;
                cd.valueMicro = cd.principalMicro + 250_000L;
                cd.openDay = 0L;
                cd.maturityDay = state.economicDay;
                cd.annualRate = 0.04;
                account.cdPositions.put(cd.positionId, cd);

                EconomyState.LoanPosition loan = new EconomyState.LoanPosition();
                loan.positionId = EconomyState.MAX_TERM_POSITIONS + positionIndex + 1L;
                loan.principalMicro = (30L + positionIndex) * EconomyState.MICRO;
                loan.valueMicro = loan.principalMicro + 500_000L;
                loan.openDay = 0L;
                loan.maturityDay = state.economicDay;
                loan.serial = positionIndex + 1L;
                loan.annualRate = 0.05;
                loan.recoveryRate = 1.0;
                loan.resolved = true;
                loan.outcome = EconomyEngine.LoanOutcome.REPAID;
                account.loanPositions.put(loan.positionId, loan);
            }
            account.nextTermPositionId = EconomyState.MAX_TERM_POSITIONS * 2L + 1L;
            account.loanSerial = EconomyState.MAX_TERM_POSITIONS;

            for (int ledgerIndex = 0;
                    ledgerIndex < MATURE_LEDGER_ENTRIES;
                    ledgerIndex++) {
                EconomyState.PortfolioTransaction transaction =
                        new EconomyState.PortfolioTransaction();
                transaction.day = state.economicDay - MATURE_LEDGER_ENTRIES + ledgerIndex + 1L;
                transaction.kind = ledgerIndex % 2 == 0
                        ? EconomyState.PortfolioTransactionKind.BUY
                        : EconomyState.PortfolioTransactionKind.SELL;
                transaction.symbol = EconomyEngine.ASSETS
                        .get(ledgerIndex % EconomyEngine.ASSETS.size()).ticker();
                transaction.quantity = 0.25 + ledgerIndex * 0.01;
                transaction.amountMicro = (ledgerIndex + 1L) * 10_000L;
                transaction.costBasisMicro = transaction.amountMicro;
                account.transactionLedger.add(transaction);
            }
            for (int day = 1; day <= MATURE_HISTORY_DAYS; day++) {
                EconomyState.PortfolioValuePoint point = new EconomyState.PortfolioValuePoint();
                point.day = day;
                point.valueMicro = (2_000L * EconomyState.MICRO) + day + accountIndex;
                account.netWorthHistory.add(point);
            }
        }

        EconomyState.ProsperityFund fund = village.prosperityFund;
        fund.spendableMicro.put(
                EconomyState.DonationPurpose.GENERAL, 100L * EconomyState.MICRO);
        fund.endowmentPrincipalMicro.put(
                EconomyState.DonationPurpose.FOOD, 100L * EconomyState.MICRO);
        fund.projectSponsorshipMicro.put(project.projectId, 56L * EconomyState.MICRO);
        fund.lifetimeReceivedMicro = EconomyState.MAX_FUND_LEDGER_ENTRIES
                * EconomyState.MICRO;
        for (int index = 0; index < EconomyState.MAX_FUND_LEDGER_ENTRIES; index++) {
            UUID donorId = accountId(10_000 + index % MATURE_ACCOUNT_COUNT);
            EconomyState.FundContribution contribution = new EconomyState.FundContribution();
            contribution.day = 110L + index;
            contribution.donorId = donorId;
            contribution.amountMicro = EconomyState.MICRO;
            if (index < 100) {
                contribution.type = EconomyState.ProsperityFundType.DIRECT_GRANT;
                contribution.purpose = EconomyState.DonationPurpose.GENERAL;
            } else if (index < 200) {
                contribution.type = EconomyState.ProsperityFundType.ENDOWMENT;
                contribution.purpose = EconomyState.DonationPurpose.FOOD;
            } else {
                contribution.type = EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP;
                contribution.purpose = EconomyState.DonationPurpose.INFRASTRUCTURE;
                contribution.projectId = project.projectId;
            }
            fund.contributions.add(contribution);
            fund.donorTotalsMicro.merge(donorId, EconomyState.MICRO, Long::sum);
            EconomyState.DonorRecord donor = state.donors.computeIfAbsent(
                    donorId, ignored -> new EconomyState.DonorRecord());
            donor.lifetimeContributionMicro += EconomyState.MICRO;
            donor.contributionCount++;
            donor.byTypeMicro.merge(contribution.type, EconomyState.MICRO, Long::sum);
            donor.byPurposeMicro.merge(contribution.purpose, EconomyState.MICRO, Long::sum);
        }

        Path directory = Files.createTempDirectory("emerald-standard-mature-scale-");
        Path save = directory.resolve("the_emerald_standard.properties");
        long saveMillis;
        long loadMillis;
        long bytes;
        try {
            long saveStart = System.nanoTime();
            state.save(save);
            saveMillis = elapsedMillis(saveStart);
            bytes = Files.size(save);

            long loadStart = System.nanoTime();
            EconomyState loaded = EconomyState.load(save, -1L, 10_000L, 20_000L);
            loadMillis = elapsedMillis(loadStart);
            EconomyState.Account sample = loaded.accounts.get(accountId(10_099));
            require(loaded.accounts.size() == MATURE_ACCOUNT_COUNT
                            && sample != null
                            && sample.shares.size() == EconomyEngine.ASSETS.size()
                            && sample.cdPositions.size() == EconomyState.MAX_TERM_POSITIONS
                            && sample.loanPositions.size() == EconomyState.MAX_TERM_POSITIONS
                            && sample.transactionLedger.size() == MATURE_LEDGER_ENTRIES
                            && sample.netWorthHistory.size() == MATURE_HISTORY_DAYS,
                    "Mature account data was lost or truncated during persistence");
            require(loaded.existingVillage(village.villageId).prosperityFund
                            .contributions.size() == EconomyState.MAX_FUND_LEDGER_ENTRIES
                            && loaded.donors.size() == MATURE_ACCOUNT_COUNT,
                    "Mature Prosperity Fund history or donor state was lost");
        } finally {
            deleteIfExists(save.resolveSibling(save.getFileName() + ".tmp"));
            deleteIfExists(save.resolveSibling(save.getFileName() + ".bak"));
            deleteIfExists(save);
            deleteIfExists(directory);
        }
        System.out.printf(
                "MATURE accounts=%d history=%d ledger=%d positions=%d fund_entries=%d save_ms=%d load_ms=%d bytes=%d%n",
                MATURE_ACCOUNT_COUNT,
                MATURE_HISTORY_DAYS,
                MATURE_LEDGER_ENTRIES,
                EconomyState.MAX_TERM_POSITIONS * 2,
                EconomyState.MAX_FUND_LEDGER_ENTRIES,
                saveMillis,
                loadMillis,
                bytes);
    }

    private static void verifyBoundaryTieUpdateAndRemoval() {
        Map<UUID, EconomyState.VillageRecord> villages = new LinkedHashMap<>();
        EconomyState.VillageRecord west = village(1, "minecraft:overworld", -1, 64, 0);
        EconomyState.VillageRecord east = village(2, "minecraft:overworld", 1, 64, 0);
        EconomyState.VillageRecord boundary = village(3, "minecraft:overworld", -65, 64, -65);
        villages.put(west.villageId, west);
        villages.put(east.villageId, east);
        villages.put(boundary.villageId, boundary);

        VillageSpatialIndex index = new VillageSpatialIndex();
        index.rebuild(villages);
        require(index.nearest(villages, "minecraft:overworld", pack(0, 64, 0), 2.0)
                        == east,
                "Equal-distance lookup did not retain legacy last-insertion tie behavior");
        require(index.nearest(villages, "minecraft:overworld", pack(-64, 64, -64), 2.0)
                        == boundary,
                "Negative cell boundary lookup failed");

        east.centerPos = pack(1_000, 64, 1_000);
        index.upsert(east);
        require(index.nearest(villages, "minecraft:overworld", pack(0, 64, 0), 2.0)
                        == west,
                "Moved village remained in its old spatial bucket");
        villages.remove(west.villageId);
        index.remove(west.villageId);
        require(index.nearest(villages, "minecraft:overworld", pack(0, 64, 0), 2.0)
                        == null,
                "Removed village remained queryable");
    }

    private static void verifyServiceRebuildsPersistedIndex() throws Exception {
        Path directory = Files.createTempDirectory("emerald-standard-index-reload-");
        Path save = directory.resolve("the_emerald_standard.properties");
        try {
            EconomyState state = EconomyState.fresh(95L, 0L, 0L);
            EconomyState.VillageRecord persisted =
                    village(95, "minecraft:overworld", 640, 70, -640);
            persisted.population = 6;
            persisted.observedPopulation = 6;
            persisted.housingCapacity = 10;
            state.villages.put(persisted.villageId, persisted);
            state.save(save);

            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 95L, 0L, 0L);
            EconomyService.VillageSnapshot found = service.nearestVillageSnapshot(
                    "minecraft:overworld", pack(650, 70, -650), 32.0);
            require(found != null && found.village().villageId.equals(persisted.villageId),
                    "Service did not rebuild its spatial index from persisted villages");
        } finally {
            deleteIfExists(save.resolveSibling(save.getFileName() + ".tmp"));
            deleteIfExists(save.resolveSibling(save.getFileName() + ".bak"));
            deleteIfExists(save);
            deleteIfExists(directory);
        }
    }

    private static EconomyState.VillageRecord bruteNearest(
            Map<UUID, EconomyState.VillageRecord> villages,
            String dimension,
            long position,
            double radius) {
        EconomyState.VillageRecord best = null;
        double bestDistance = radius * radius;
        for (EconomyState.VillageRecord village : villages.values()) {
            if (!dimension.equals(village.dimensionKey)) {
                continue;
            }
            double distance = distanceSquared(village.centerPos, position);
            if (distance <= bestDistance) {
                best = village;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static List<UUID> bruteNearAny(
            Map<UUID, EconomyState.VillageRecord> villages,
            String dimension,
            List<Long> positions,
            double radius) {
        double radiusSquared = radius * radius;
        List<UUID> result = new ArrayList<>();
        for (EconomyState.VillageRecord village : villages.values()) {
            if (!dimension.equals(village.dimensionKey)) {
                continue;
            }
            for (long position : positions) {
                if (distanceSquared(village.centerPos, position) <= radiusSquared) {
                    result.add(village.villageId);
                    break;
                }
            }
        }
        return result;
    }

    private static EconomyState.VillageRecord village(
            int id, String dimension, int x, int y, int z) {
        EconomyState.VillageRecord village = new EconomyState.VillageRecord();
        village.villageId = villageId(id);
        village.dimensionKey = dimension;
        village.centerPos = pack(x, y, z);
        return village;
    }

    private static String id(EconomyState.VillageRecord village) {
        return village == null ? "" : village.villageId.toString();
    }

    private static UUID villageId(int index) {
        return new UUID(0x56494C4C41474500L, index + 1L);
    }

    private static UUID accountId(int index) {
        return new UUID(0x4143434F554E5400L, index + 1L);
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    private static double distanceSquared(long first, long second) {
        double dx = (double) unpackX(first) - unpackX(second);
        double dy = (double) unpackY(first) - unpackY(second);
        double dz = (double) unpackZ(first) - unpackZ(second);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Query(String dimension, long position, double radius) {
    }
}
