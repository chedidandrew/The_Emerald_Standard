package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent world economy and server-authoritative player accounts. */
public final class EconomyState {
    public static final int FORMAT_VERSION = 9;
    /** Five in-game years, shared by market, commodity, and personal history views. */
    public static final int HISTORY_DAYS = 1_825;
    public static final int MAX_PORTFOLIO_LEDGER_ENTRIES = 256;
    public static final int MAX_FUND_LEDGER_ENTRIES = 256;
    public static final int MAX_TERM_POSITIONS = 8;
    public static final long MICRO = 1_000_000L;
    public static final int MAX_PENDING_INVENTORY_ITEMS = 100_000;

    public long seed;
    public long economicDay;
    public long lastWallClockMs;
    public long lastGameTicks;
    public long pendingEconomicMillis;
    public EconomyEngine.Regime regime;
    public EconomyEngine.MarketEvent lastMarketEvent = EconomyEngine.MarketEvent.NONE;
    public long lastMarketEventDay;

    public final Map<String, Double> prices = new LinkedHashMap<>();
    public final Map<String, Double> commodityPrices = new LinkedHashMap<>();
    public final Map<String, List<Double>> priceHistory = new LinkedHashMap<>();
    public final Map<String, List<Double>> commodityHistory = new LinkedHashMap<>();
    public final Set<Long> generatedBankRegions = new HashSet<>();
    /** Packed BlockPos anchors for generated banks or fallback Banker gathering points. */
    public final Map<Long, Long> generatedBankAnchors = new HashMap<>();
    /** Stable village identities associated with legacy bank-region markers. */
    public final Map<Long, UUID> bankRegionVillageIds = new HashMap<>();
    /** Persistent abstract village economies and development backlogs. */
    public final Map<UUID, VillageRecord> villages = new LinkedHashMap<>();
    /** Pre-incident market contributions held while player-damaged villages recover. */
    public final Map<UUID, VillageMarketShadow> villageMarketShadows = new LinkedHashMap<>();
    public final Map<UUID, Account> accounts = new HashMap<>();
    /** Lifetime recognition is global to the world and never grants a financial benefit. */
    public final Map<UUID, DonorRecord> donors = new HashMap<>();
    public final Map<UUID, PendingInventoryTransaction> pendingInventoryTransactions =
            new HashMap<>();

    public enum InventoryTransactionKind {
        DEPOSIT,
        EXCHANGE,
        WITHDRAWAL
    }

    public enum InventoryTransactionStage {
        PREPARED,
        BANK_COMMITTED
    }

    /**
     * Durable bridge between Minecraft inventory state and the bank save.
     *
     * <p>The map is keyed by player UUID, so only one inventory-linked bank transaction can be
     * active for a player at a time. The transaction remains until the player's inventory has been
     * synchronously saved and the journal entry is cleared.</p>
     */
    public static final class PendingInventoryTransaction {
        public UUID transactionId;
        public UUID playerId;
        public InventoryTransactionKind kind;
        public InventoryTransactionStage stage;
        public String itemKey;
        public int itemCount;
        public int inventoryCountBefore;
        public long bankDeltaMicro;
        public long createdEconomicDay;
        public long createdWallClockMs;

        public int inventoryDelta() {
            return kind == InventoryTransactionKind.WITHDRAWAL ? itemCount : -itemCount;
        }

        public int expectedInventoryCount() {
            long expected = (long) inventoryCountBefore + inventoryDelta();
            return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, expected));
        }

        public boolean creditsBank() {
            return bankDeltaMicro > 0L;
        }

        public PendingInventoryTransaction copy() {
            PendingInventoryTransaction copy = new PendingInventoryTransaction();
            copy.transactionId = transactionId;
            copy.playerId = playerId;
            copy.kind = kind;
            copy.stage = stage;
            copy.itemKey = itemKey;
            copy.itemCount = itemCount;
            copy.inventoryCountBefore = inventoryCountBefore;
            copy.bankDeltaMicro = bankDeltaMicro;
            copy.createdEconomicDay = createdEconomicDay;
            copy.createdWallClockMs = createdWallClockMs;
            return copy;
        }
    }

    public enum PortfolioTransactionKind {
        CASH_IN,
        CASH_OUT,
        SAVINGS_DEPOSIT,
        SAVINGS_WITHDRAW,
        INTEREST,
        BUY,
        SELL,
        CD_OPEN,
        CD_CLOSE,
        LENDING_OPEN,
        LENDING_CLOSE,
        DIRECT_GRANT,
        ENDOWMENT,
        PROJECT_SPONSORSHIP
    }

    /** An authoritative, bounded accounting entry. */
    public static final class PortfolioTransaction {
        public long day;
        public PortfolioTransactionKind kind = PortfolioTransactionKind.CASH_IN;
        public String symbol = "";
        public long referenceId;
        public double quantity;
        public long amountMicro;
        public long costBasisMicro;
        public long realizedGainMicro;

        public PortfolioTransaction copy() {
            PortfolioTransaction copy = new PortfolioTransaction();
            copy.day = day;
            copy.kind = kind;
            copy.symbol = symbol;
            copy.referenceId = referenceId;
            copy.quantity = quantity;
            copy.amountMicro = amountMicro;
            copy.costBasisMicro = costBasisMicro;
            copy.realizedGainMicro = realizedGainMicro;
            return copy;
        }
    }

    public static final class PortfolioValuePoint {
        public long day;
        public long valueMicro;

        public PortfolioValuePoint copy() {
            PortfolioValuePoint copy = new PortfolioValuePoint();
            copy.day = day;
            copy.valueMicro = valueMicro;
            return copy;
        }
    }

    public static final class CdPosition {
        public long positionId;
        public long principalMicro;
        public long valueMicro;
        public long openDay;
        public long maturityDay;
        public double annualRate;

        public CdPosition copy() {
            CdPosition copy = new CdPosition();
            copy.positionId = positionId;
            copy.principalMicro = principalMicro;
            copy.valueMicro = valueMicro;
            copy.openDay = openDay;
            copy.maturityDay = maturityDay;
            copy.annualRate = annualRate;
            return copy;
        }
    }

    public static final class LoanPosition {
        public long positionId;
        public long principalMicro;
        public long valueMicro;
        public long openDay;
        public long maturityDay;
        public long serial;
        public double annualRate;
        public double stress;
        public double recoveryRate = 1.0;
        public boolean resolved;
        public EconomyEngine.LoanOutcome outcome = EconomyEngine.LoanOutcome.REPAID;

        public LoanPosition copy() {
            LoanPosition copy = new LoanPosition();
            copy.positionId = positionId;
            copy.principalMicro = principalMicro;
            copy.valueMicro = valueMicro;
            copy.openDay = openDay;
            copy.maturityDay = maturityDay;
            copy.serial = serial;
            copy.annualRate = annualRate;
            copy.stress = stress;
            copy.recoveryRate = recoveryRate;
            copy.resolved = resolved;
            copy.outcome = outcome;
            return copy;
        }
    }

    public static final class Account {
        public long cashMicro;
        public long savingsMicro;

        public long cdPrincipalMicro;
        public long cdValueMicro;
        public long cdOpenDay;
        public long cdMaturityDay;
        public double cdAnnualRate;

        public long loanPrincipalMicro;
        public long loanValueMicro;
        public long loanOpenDay;
        public long loanMaturityDay;
        public long loanSerial;
        public double loanAnnualRate;
        public double loanStress;
        public double loanRecoveryRate = 1.0;
        public boolean loanResolved;
        public EconomyEngine.LoanOutcome loanOutcome = EconomyEngine.LoanOutcome.REPAID;

        public final Map<String, Double> shares = new HashMap<>();

        /** Multiple positions are authoritative; the scalar fields above mirror the oldest one. */
        public final Map<Long, CdPosition> cdPositions = new LinkedHashMap<>();
        public final Map<Long, LoanPosition> loanPositions = new LinkedHashMap<>();
        public long nextTermPositionId = 1L;

        public final Map<String, Long> shareCostBasisMicro = new HashMap<>();
        public long realizedGainMicro;
        public long totalContributionsMicro;
        public long totalWithdrawalsMicro;
        public boolean costBasisInferred;
        public final List<PortfolioTransaction> transactionLedger = new ArrayList<>();
        public final List<PortfolioValuePoint> netWorthHistory = new ArrayList<>();

        public boolean hasCd() {
            return !cdPositions.isEmpty() || cdPrincipalMicro > 0L;
        }

        public boolean hasLoan() {
            return !loanPositions.isEmpty() || loanPrincipalMicro > 0L;
        }

        public long totalCdValueMicro() {
            if (cdPositions.isEmpty()) {
                return cdValueMicro;
            }
            long total = 0L;
            for (CdPosition position : cdPositions.values()) {
                total = saturatingAdd(total, position.valueMicro);
            }
            return total;
        }

        public long totalLoanValueMicro() {
            if (loanPositions.isEmpty()) {
                return loanValueMicro;
            }
            long total = 0L;
            for (LoanPosition position : loanPositions.values()) {
                total = saturatingAdd(total, position.valueMicro);
            }
            return total;
        }

        public Account copy() {
            Account copy = new Account();
            copy.cashMicro = cashMicro;
            copy.savingsMicro = savingsMicro;
            copy.cdPrincipalMicro = cdPrincipalMicro;
            copy.cdValueMicro = cdValueMicro;
            copy.cdOpenDay = cdOpenDay;
            copy.cdMaturityDay = cdMaturityDay;
            copy.cdAnnualRate = cdAnnualRate;
            copy.loanPrincipalMicro = loanPrincipalMicro;
            copy.loanValueMicro = loanValueMicro;
            copy.loanOpenDay = loanOpenDay;
            copy.loanMaturityDay = loanMaturityDay;
            copy.loanSerial = loanSerial;
            copy.loanAnnualRate = loanAnnualRate;
            copy.loanStress = loanStress;
            copy.loanRecoveryRate = loanRecoveryRate;
            copy.loanResolved = loanResolved;
            copy.loanOutcome = loanOutcome;
            copy.shares.putAll(shares);
            cdPositions.forEach((key, value) -> copy.cdPositions.put(key, value.copy()));
            loanPositions.forEach((key, value) -> copy.loanPositions.put(key, value.copy()));
            copy.nextTermPositionId = nextTermPositionId;
            copy.shareCostBasisMicro.putAll(shareCostBasisMicro);
            copy.realizedGainMicro = realizedGainMicro;
            copy.totalContributionsMicro = totalContributionsMicro;
            copy.totalWithdrawalsMicro = totalWithdrawalsMicro;
            copy.costBasisInferred = costBasisInferred;
            transactionLedger.forEach(entry -> copy.transactionLedger.add(entry.copy()));
            netWorthHistory.forEach(point -> copy.netWorthHistory.add(point.copy()));
            return copy;
        }
    }

    public enum ProsperityFundType {
        DIRECT_GRANT,
        ENDOWMENT,
        PROJECT_SPONSORSHIP
    }

    public enum DonationPurpose {
        GENERAL,
        HOUSING,
        FOOD,
        INFRASTRUCTURE,
        SECURITY,
        TRADE,
        RESTORATION
    }

    public enum DonorTitle {
        NONE,
        VILLAGE_SUPPORTER,
        VILLAGE_BENEFACTOR,
        VILLAGE_PATRON,
        EMERALD_STEWARD,
        FOUNDER_OF_PROSPERITY
    }

    public static final class DonorRecord {
        public long lifetimeContributionMicro;
        public int contributionCount;
        public final Map<ProsperityFundType, Long> byTypeMicro =
                new EnumMap<>(ProsperityFundType.class);
        public final Map<DonationPurpose, Long> byPurposeMicro =
                new EnumMap<>(DonationPurpose.class);

        public DonorTitle title() {
            long emeralds = lifetimeContributionMicro / MICRO;
            if (emeralds >= 10_000L) return DonorTitle.FOUNDER_OF_PROSPERITY;
            if (emeralds >= 2_000L) return DonorTitle.EMERALD_STEWARD;
            if (emeralds >= 500L) return DonorTitle.VILLAGE_PATRON;
            if (emeralds >= 100L) return DonorTitle.VILLAGE_BENEFACTOR;
            if (emeralds >= 10L) return DonorTitle.VILLAGE_SUPPORTER;
            return DonorTitle.NONE;
        }

        public DonorRecord copy() {
            DonorRecord copy = new DonorRecord();
            copy.lifetimeContributionMicro = lifetimeContributionMicro;
            copy.contributionCount = contributionCount;
            copy.byTypeMicro.putAll(byTypeMicro);
            copy.byPurposeMicro.putAll(byPurposeMicro);
            return copy;
        }
    }

    public static final class FundContribution {
        public long day;
        public UUID donorId;
        public ProsperityFundType type = ProsperityFundType.DIRECT_GRANT;
        public DonationPurpose purpose = DonationPurpose.GENERAL;
        public long projectId;
        public long amountMicro;

        public FundContribution copy() {
            FundContribution copy = new FundContribution();
            copy.day = day;
            copy.donorId = donorId;
            copy.type = type;
            copy.purpose = purpose;
            copy.projectId = projectId;
            copy.amountMicro = amountMicro;
            return copy;
        }
    }

    /** Village-owned balances. Endowment principal is deliberately separated from spendable cash. */
    public static final class ProsperityFund {
        public final Map<DonationPurpose, Long> spendableMicro =
                new EnumMap<>(DonationPurpose.class);
        public final Map<DonationPurpose, Long> endowmentPrincipalMicro =
                new EnumMap<>(DonationPurpose.class);
        public final Map<Long, Long> projectSponsorshipMicro = new LinkedHashMap<>();
        public final Map<UUID, Long> donorTotalsMicro = new LinkedHashMap<>();
        public final List<FundContribution> contributions = new ArrayList<>();
        public long emergencyReserveMicro;
        public long lifetimeReceivedMicro;
        public long lifetimeSpentMicro;
        public long lastSpendingDay = -1L;
        public long spentTodayMicro;

        public long endowmentPrincipalTotalMicro() {
            long total = 0L;
            for (long value : endowmentPrincipalMicro.values()) {
                total = saturatingAdd(total, value);
            }
            return total;
        }

        public long spendableTotalMicro() {
            long total = 0L;
            for (long value : spendableMicro.values()) {
                total = saturatingAdd(total, value);
            }
            return total;
        }

        public ProsperityFund copy() {
            ProsperityFund copy = new ProsperityFund();
            copy.spendableMicro.putAll(spendableMicro);
            copy.endowmentPrincipalMicro.putAll(endowmentPrincipalMicro);
            copy.projectSponsorshipMicro.putAll(projectSponsorshipMicro);
            copy.donorTotalsMicro.putAll(donorTotalsMicro);
            contributions.forEach(value -> copy.contributions.add(value.copy()));
            copy.emergencyReserveMicro = emergencyReserveMicro;
            copy.lifetimeReceivedMicro = lifetimeReceivedMicro;
            copy.lifetimeSpentMicro = lifetimeSpentMicro;
            copy.lastSpendingDay = lastSpendingDay;
            copy.spentTodayMicro = spentTodayMicro;
            return copy;
        }
    }

    public static final class ResidentRecord {
        public UUID residentId;
        public String profession = "minecraft:none";
        public VillageProsperityEngine.ResidentStatus status =
                VillageProsperityEngine.ResidentStatus.ACTIVE;
        public long lastSeenDay;
        public long lastKnownPos;

        public ResidentRecord copy() {
            ResidentRecord copy = new ResidentRecord();
            copy.residentId = residentId;
            copy.profession = profession;
            copy.status = status;
            copy.lastSeenDay = lastSeenDay;
            copy.lastKnownPos = lastKnownPos;
            return copy;
        }
    }

    public static final class VillageIncident {
        public long day;
        public VillageProsperityEngine.IncidentCause cause =
                VillageProsperityEngine.IncidentCause.UNKNOWN;
        public int casualties = 1;
        public UUID responsiblePlayer;
        public boolean marketEligible = true;

        public VillageIncident copy() {
            VillageIncident copy = new VillageIncident();
            copy.day = day;
            copy.cause = cause;
            copy.casualties = casualties;
            copy.responsiblePlayer = responsiblePlayer;
            copy.marketEligible = marketEligible;
            return copy;
        }
    }

    public static final class VillageProject {
        public long projectId;
        public VillageProsperityEngine.ProjectType type =
                VillageProsperityEngine.ProjectType.COTTAGE;
        public long approvedDay;
        public long completedDay;
        public double economicProgress;
        public boolean economicComplete;
        public long originPos;
        public int materializedBlocks;
        public int totalBlocks;
        public boolean materializedComplete;
        public boolean blocked;
        public boolean abstractOnly;
        /** Inclusive packed BlockPos bounds reserved by the authored physical template. */
        public long boundsMinPos;
        public long boundsMaxPos;
        /** Persistent retry gate used to avoid rescanning an unsafe site every server tick. */
        public long retryAfterGameTick;
        public int materializationFailures;

        public VillageProject copy() {
            VillageProject copy = new VillageProject();
            copy.projectId = projectId;
            copy.type = type;
            copy.approvedDay = approvedDay;
            copy.completedDay = completedDay;
            copy.economicProgress = economicProgress;
            copy.economicComplete = economicComplete;
            copy.originPos = originPos;
            copy.materializedBlocks = materializedBlocks;
            copy.totalBlocks = totalBlocks;
            copy.materializedComplete = materializedComplete;
            copy.blocked = blocked;
            copy.abstractOnly = abstractOnly;
            copy.boundsMinPos = boundsMinPos;
            copy.boundsMaxPos = boundsMaxPos;
            copy.retryAfterGameTick = retryAfterGameTick;
            copy.materializationFailures = materializationFailures;
            return copy;
        }
    }

    /**
     * Market counterfactual captured immediately before a player-caused casualty.
     *
     * <p>The full no-player-damage village state advances daily and refreshes the cached
     * contribution and weight, preventing an attack from changing either side of the weighted
     * average without indefinitely freezing a favorable observation. The shadow is released only
     * after the cooldown and a full population recovery.</p>
     */
    public static final class VillageMarketShadow {
        public boolean present;
        public boolean contributionEligible;
        public int formulaVersion;
        public long capturedDay;
        public long minimumReleaseDay;
        public int recoveryPopulation;
        public double weight = Double.NaN;
        public double broad = Double.NaN;
        public double mining = Double.NaN;
        public double agriculture = Double.NaN;
        public double trade = Double.NaN;
        public double redstone = Double.NaN;
        public double alchemy = Double.NaN;
        public double transport = Double.NaN;
        public double security = Double.NaN;
        /** Full no-player-damage state used to advance the market counterfactual. */
        public VillageRecord counterfactualVillage;

        public VillageMarketShadow copy() {
            VillageMarketShadow copy = new VillageMarketShadow();
            copy.present = present;
            copy.contributionEligible = contributionEligible;
            copy.formulaVersion = formulaVersion;
            copy.capturedDay = capturedDay;
            copy.minimumReleaseDay = minimumReleaseDay;
            copy.recoveryPopulation = recoveryPopulation;
            copy.weight = weight;
            copy.broad = broad;
            copy.mining = mining;
            copy.agriculture = agriculture;
            copy.trade = trade;
            copy.redstone = redstone;
            copy.alchemy = alchemy;
            copy.transport = transport;
            copy.security = security;
            copy.counterfactualVillage = counterfactualVillage == null
                    ? null
                    : counterfactualVillage.copy();
            return copy;
        }
    }

    /** Persistent economic and physical-development record for one stable village identity. */
    public static final class VillageRecord {
        public UUID villageId;
        public String dimensionKey = "minecraft:overworld";
        public long centerPos;
        public long bankRegionKey;
        public long bankAnchorPos;
        public long discoveredDay;
        public long lastSimulatedDay;
        public long lastCensusDay;
        public long lastIncidentDay;
        public long recoveryEligibleDay;
        public long abandonedSinceDay;
        public long lastCollapseDay;
        public long marketSuppressedUntilDay;
        public VillageProsperityEngine.Lifecycle lifecycle =
                VillageProsperityEngine.Lifecycle.ACTIVE;
        public VillageProsperityEngine.IncidentCause lastIncidentCause =
                VillageProsperityEngine.IncidentCause.NONE;
        public int population;
        public int observedPopulation;
        public int housingCapacity;
        public int pendingSettlers;
        public int developmentTier;
        public int collapseCount;
        public int hostileCasualties;
        public int playerCasualties;
        public int environmentalCasualties;
        public double foodSupply;
        public double materialSupply;
        public double treasury;
        public double prosperity = 50.0;
        public double safety = 60.0;
        public double agricultureOutput;
        public double miningOutput;
        public double tradeOutput;
        public double redstoneOutput;
        public double alchemyOutput;
        public double transportOutput;
        public double securityOutput;
        public double restorationFund;
        public double developmentPoints;
        public boolean restorationFunded;
        public long projectSerial;
        public final ProsperityFund prosperityFund = new ProsperityFund();
        public final Map<UUID, ResidentRecord> residents = new LinkedHashMap<>();
        public final List<VillageProject> projects = new ArrayList<>();
        public final List<VillageIncident> incidents = new ArrayList<>();

        public VillageProject nextVisualProject() {
            return nextVisualProject(Long.MAX_VALUE);
        }

        /** Returns the next project whose persisted placement retry delay has elapsed. */
        public VillageProject nextVisualProject(long currentGameTick) {
            return projects.stream()
                    .filter(project -> project.economicComplete
                            && !project.materializedComplete
                            && !project.abstractOnly
                            && project.retryAfterGameTick <= Math.max(0L, currentGameTick))
                    .findFirst()
                    .orElse(null);
        }

        public int visualBacklog() {
            return (int) projects.stream()
                    .filter(project -> project.economicComplete
                            && !project.materializedComplete
                            && !project.abstractOnly)
                    .count();
        }

        public VillageRecord copy() {
            VillageRecord copy = new VillageRecord();
            copy.villageId = villageId;
            copy.dimensionKey = dimensionKey;
            copy.centerPos = centerPos;
            copy.bankRegionKey = bankRegionKey;
            copy.bankAnchorPos = bankAnchorPos;
            copy.discoveredDay = discoveredDay;
            copy.lastSimulatedDay = lastSimulatedDay;
            copy.lastCensusDay = lastCensusDay;
            copy.lastIncidentDay = lastIncidentDay;
            copy.recoveryEligibleDay = recoveryEligibleDay;
            copy.abandonedSinceDay = abandonedSinceDay;
            copy.lastCollapseDay = lastCollapseDay;
            copy.marketSuppressedUntilDay = marketSuppressedUntilDay;
            copy.lifecycle = lifecycle;
            copy.lastIncidentCause = lastIncidentCause;
            copy.population = population;
            copy.observedPopulation = observedPopulation;
            copy.housingCapacity = housingCapacity;
            copy.pendingSettlers = pendingSettlers;
            copy.developmentTier = developmentTier;
            copy.collapseCount = collapseCount;
            copy.hostileCasualties = hostileCasualties;
            copy.playerCasualties = playerCasualties;
            copy.environmentalCasualties = environmentalCasualties;
            copy.foodSupply = foodSupply;
            copy.materialSupply = materialSupply;
            copy.treasury = treasury;
            copy.prosperity = prosperity;
            copy.safety = safety;
            copy.agricultureOutput = agricultureOutput;
            copy.miningOutput = miningOutput;
            copy.tradeOutput = tradeOutput;
            copy.redstoneOutput = redstoneOutput;
            copy.alchemyOutput = alchemyOutput;
            copy.transportOutput = transportOutput;
            copy.securityOutput = securityOutput;
            copy.restorationFund = restorationFund;
            copy.developmentPoints = developmentPoints;
            copy.restorationFunded = restorationFunded;
            copy.projectSerial = projectSerial;
            ProsperityFund fundCopy = prosperityFund.copy();
            copy.prosperityFund.spendableMicro.putAll(fundCopy.spendableMicro);
            copy.prosperityFund.endowmentPrincipalMicro.putAll(
                    fundCopy.endowmentPrincipalMicro);
            copy.prosperityFund.projectSponsorshipMicro.putAll(
                    fundCopy.projectSponsorshipMicro);
            copy.prosperityFund.donorTotalsMicro.putAll(fundCopy.donorTotalsMicro);
            copy.prosperityFund.contributions.addAll(fundCopy.contributions);
            copy.prosperityFund.emergencyReserveMicro = fundCopy.emergencyReserveMicro;
            copy.prosperityFund.lifetimeReceivedMicro = fundCopy.lifetimeReceivedMicro;
            copy.prosperityFund.lifetimeSpentMicro = fundCopy.lifetimeSpentMicro;
            copy.prosperityFund.lastSpendingDay = fundCopy.lastSpendingDay;
            copy.prosperityFund.spentTodayMicro = fundCopy.spentTodayMicro;
            residents.forEach((id, resident) -> copy.residents.put(id, resident.copy()));
            projects.forEach(project -> copy.projects.add(project.copy()));
            incidents.forEach(incident -> copy.incidents.add(incident.copy()));
            return copy;
        }
    }

    public static EconomyState fresh(long seed, long now, long gameTicks) {
        EconomyState state = new EconomyState();
        state.seed = seed;
        state.lastWallClockMs = Math.max(0L, now);
        state.lastGameTicks = Math.max(0L, gameTicks);
        state.regime = EconomyEngine.initialRegime(seed);
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            state.prices.put(asset.ticker(), 100.0);
            state.priceHistory.put(asset.ticker(), new ArrayList<>(List.of(100.0)));
        }
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            state.commodityPrices.put(commodity.id(), commodity.anchorPrice());
            state.commodityHistory.put(
                    commodity.id(), new ArrayList<>(List.of(commodity.anchorPrice())));
        }
        return state;
    }

    public static EconomyState load(Path path, long fallbackSeed, long now, long ticks)
            throws IOException {
        return EconomyPersistence.load(path, fallbackSeed, now, ticks);
    }

    public void save(Path path) throws IOException {
        EconomyPersistence.save(this, path);
    }

    public EconomyState copy() {
        EconomyState copy = new EconomyState();
        copy.seed = seed;
        copy.economicDay = economicDay;
        copy.lastWallClockMs = lastWallClockMs;
        copy.lastGameTicks = lastGameTicks;
        copy.pendingEconomicMillis = pendingEconomicMillis;
        copy.regime = regime;
        copy.lastMarketEvent = lastMarketEvent;
        copy.lastMarketEventDay = lastMarketEventDay;
        copy.prices.putAll(prices);
        copy.commodityPrices.putAll(commodityPrices);
        priceHistory.forEach((ticker, values) ->
                copy.priceHistory.put(ticker, new ArrayList<>(values)));
        commodityHistory.forEach((commodity, values) ->
                copy.commodityHistory.put(commodity, new ArrayList<>(values)));
        copy.generatedBankRegions.addAll(generatedBankRegions);
        copy.generatedBankAnchors.putAll(generatedBankAnchors);
        copy.bankRegionVillageIds.putAll(bankRegionVillageIds);
        for (Map.Entry<UUID, VillageRecord> entry : villages.entrySet()) {
            copy.villages.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<UUID, VillageMarketShadow> entry : villageMarketShadows.entrySet()) {
            copy.villageMarketShadows.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            copy.accounts.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<UUID, DonorRecord> entry : donors.entrySet()) {
            copy.donors.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<UUID, PendingInventoryTransaction> entry
                : pendingInventoryTransactions.entrySet()) {
            copy.pendingInventoryTransactions.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    public void advanceOneDay() {
        advanceOneDay(true);
    }

    public void advanceOneDay(boolean villageProsperitySimulationEnabled) {
        advanceOneDay(
                villageProsperitySimulationEnabled,
                villageProsperitySimulationEnabled,
                villageProsperitySimulationEnabled,
                true);
    }

    public void advanceOneDay(
            boolean villageProsperitySimulationEnabled,
            boolean villageMarketIntegrationEnabled,
            boolean villageAutomaticRecoveryEnabled) {
        advanceOneDay(
                villageProsperitySimulationEnabled,
                villageProsperitySimulationEnabled,
                villageMarketIntegrationEnabled,
                villageAutomaticRecoveryEnabled);
    }

    public void advanceOneDay(
            boolean villageProsperitySimulationEnabled,
            boolean villageVisualProgressionEnabled,
            boolean villageMarketIntegrationEnabled,
            boolean villageAutomaticRecoveryEnabled) {
        advanceOneDay(
                villageProsperitySimulationEnabled,
                villageVisualProgressionEnabled,
                villageMarketIntegrationEnabled,
                villageAutomaticRecoveryEnabled,
                true,
                0.04,
                0.10,
                64L * MICRO);
    }

    public void advanceOneDay(
            boolean villageProsperitySimulationEnabled,
            boolean villageVisualProgressionEnabled,
            boolean villageMarketIntegrationEnabled,
            boolean villageAutomaticRecoveryEnabled,
            boolean prosperityFundEnabled,
            double endowmentAnnualPayoutRate,
            double emergencyReserveFraction,
            long dailyFundSpendingCapMicro) {
        if (economicDay == Long.MAX_VALUE) {
            throw new IllegalStateException("Economic day range is exhausted");
        }
        economicDay++;
        if (villageProsperitySimulationEnabled) {
            for (VillageRecord village : villages.values()) {
                VillageProsperityEngine.advanceOneDay(
                        village,
                        seed,
                        economicDay,
                        villageAutomaticRecoveryEnabled,
                        villageVisualProgressionEnabled);
            }
            for (VillageMarketShadow shadow : villageMarketShadows.values()) {
                VillageProsperityEngine.advanceMarketShadow(
                        shadow,
                        seed,
                        economicDay,
                        villageAutomaticRecoveryEnabled);
            }
        }
        releaseRecoveredMarketShadows();
        VillageProsperityEngine.VillageFundamentals villageFundamentals =
                villageProsperitySimulationEnabled && villageMarketIntegrationEnabled
                        ? VillageProsperityEngine.aggregateFundamentals(
                                villages.values(), villageMarketShadows, economicDay)
                        : VillageProsperityEngine.VillageFundamentals.neutral();
        regime = EconomyEngine.nextRegime(regime, seed, economicDay);
        double marketReturn = EconomyEngine.marketReturn(regime, seed, economicDay);
        EconomyEngine.MarketEvent event = EconomyEngine.marketEvent(seed, economicDay, regime);
        if (event != EconomyEngine.MarketEvent.NONE) {
            lastMarketEvent = event;
            lastMarketEventDay = economicDay;
        }

        Map<String, Double> constituentReturns = new HashMap<>();
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            if (asset.ticker().equals("VILX")) {
                continue;
            }
            double current = prices.getOrDefault(asset.ticker(), 100.0);
            double baseReturn = EconomyEngine.assetReturn(
                    asset,
                    marketReturn,
                    seed,
                    economicDay,
                    VillageProsperityEngine.assetAnnualDrift(
                            asset.ticker(), villageFundamentals));
            double eventReturn = EconomyEngine.eventAssetReturn(event, asset.ticker());
            double realizedReturn = (1.0 + baseReturn) * (1.0 + eventReturn) - 1.0;
            double next = current * (1.0 + realizedReturn);
            prices.put(asset.ticker(), boundedPrice(next));
            constituentReturns.put(asset.ticker(), realizedReturn);
        }
        double constituentReturn = constituentReturns.entrySet().stream()
                .mapToDouble(entry -> EconomyEngine.vilxWeight(entry.getKey()) * entry.getValue())
                .sum();
        // VILX is rebalanced from its displayed constituents while the hidden broad-economy
        // factor supplies diversification that no eight-company sample can provide alone.
        double vilxBaseReturn = 0.85 * marketReturn
                + 0.15 * constituentReturn
                + EconomyEngine.eventAssetReturn(event, "VILX");
        double vilxVillageDaily = StrictMath.expm1(
                StrictMath.log1p(VillageProsperityEngine.assetAnnualDrift(
                                "VILX", villageFundamentals))
                        / EconomyEngine.DAYS_PER_YEAR);
        double vilxReturn = (1.0 + vilxBaseReturn) * (1.0 + vilxVillageDaily) - 1.0;
        double currentVilx = prices.getOrDefault("VILX", 100.0);
        prices.put("VILX", boundedPrice(currentVilx * (1.0 + vilxReturn)));
        normalizeHighPrices();
        recordCurrentPrices();

        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            double current = commodityPrices.getOrDefault(commodity.id(), commodity.anchorPrice());
            double next = EconomyEngine.nextCommodityPrice(
                    commodity,
                    current,
                    regime,
                    seed,
                    economicDay,
                    VillageProsperityEngine.commodityAnnualSupplyPressure(
                            commodity.id(), villageFundamentals));
            next *= 1.0 + EconomyEngine.eventCommodityReturn(event, commodity.id());
            commodityPrices.put(commodity.id(), boundedPrice(next));
        }
        recordCurrentCommodities();

        if (prosperityFundEnabled) {
            for (VillageRecord village : villages.values()) {
                accrueEndowmentPayout(
                        village.prosperityFund,
                        endowmentAnnualPayoutRate,
                        emergencyReserveFraction);
                spendProsperityFundAutomatically(
                        village, economicDay, dailyFundSpendingCapMicro);
            }
        }

        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            advanceAccount(entry.getKey(), entry.getValue());
            PortfolioAnalytics.recordNetWorth(entry.getValue(), this, economicDay);
        }
    }

    public VillageRecord village(UUID id) {
        return villages.computeIfAbsent(id, ignored -> {
            VillageRecord record = new VillageRecord();
            record.villageId = id;
            return record;
        });
    }

    public VillageRecord existingVillage(UUID id) {
        return villages.get(id);
    }

    public VillageProsperityEngine.VillageFundamentals villageFundamentals() {
        return VillageProsperityEngine.aggregateFundamentals(
                villages.values(), villageMarketShadows, economicDay);
    }

    private void releaseRecoveredMarketShadows() {
        villageMarketShadows.entrySet().removeIf(entry -> {
            VillageRecord village = villages.get(entry.getKey());
            VillageMarketShadow shadow = entry.getValue();
            return village != null
                    && shadow != null
                    && economicDay >= shadow.minimumReleaseDay
                    && economicDay >= village.marketSuppressedUntilDay
                    && village.lifecycle == VillageProsperityEngine.Lifecycle.ACTIVE
                    && village.population >= shadow.recoveryPopulation;
        });
    }

    public Account account(UUID id) {
        return accounts.computeIfAbsent(id, ignored -> new Account());
    }

    public Account existingAccount(UUID id) {
        return accounts.get(id);
    }

    public double netWorth(UUID id) {
        Account account = accounts.get(id);
        if (account == null) {
            return 0.0;
        }
        // Convert before adding. Summing four valid long balances first can wrap negative.
        ensurePositionCollections(account);
        double value = ((double) account.cashMicro
                + account.savingsMicro
                + account.totalCdValueMicro()
                + account.totalLoanValueMicro()) / MICRO;
        for (Map.Entry<String, Double> holding : account.shares.entrySet()) {
            value += holding.getValue() * prices.getOrDefault(holding.getKey(), 0.0);
        }
        return value;
    }

    public void validate() throws IOException {
        if (regime == null
                || lastMarketEvent == null
                || economicDay < 0L
                || lastWallClockMs < 0L
                || lastGameTicks < 0L
                || pendingEconomicMillis < 0L) {
            throw new IOException("Economy clock or regime is invalid");
        }
        if (lastMarketEventDay < 0L || lastMarketEventDay > economicDay) {
            throw new IOException("Economy market-event day is invalid");
        }
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            validatePrice("asset " + asset.ticker(), prices.get(asset.ticker()));
        }
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            validatePrice("commodity " + commodity.id(), commodityPrices.get(commodity.id()));
        }
        validateHistory();
        for (Long region : generatedBankRegions) {
            if (region == null) {
                throw new IOException("Generated bank region identifier is null");
            }
        }
        for (Map.Entry<Long, Long> entry : generatedBankAnchors.entrySet()) {
            Long region = entry.getKey();
            if (region == null
                    || entry.getValue() == null
                    || !generatedBankRegions.contains(region)) {
                throw new IOException("Bank anchor exists without a generated region marker");
            }
        }
        for (Map.Entry<Long, UUID> entry : bankRegionVillageIds.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || !generatedBankRegions.contains(entry.getKey())
                    || !villages.containsKey(entry.getValue())) {
                throw new IOException("Bank-region village association is invalid");
            }
        }
        for (Map.Entry<UUID, VillageRecord> entry : villages.entrySet()) {
            validateVillage(entry.getKey(), entry.getValue(), economicDay);
        }
        for (Map.Entry<UUID, VillageMarketShadow> entry : villageMarketShadows.entrySet()) {
            validateMarketShadow(entry.getKey(), entry.getValue(), economicDay);
        }
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            validateAccount(entry.getKey(), entry.getValue(), economicDay);
        }
        for (Map.Entry<UUID, DonorRecord> entry : donors.entrySet()) {
            validateDonor(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, PendingInventoryTransaction> entry
                : pendingInventoryTransactions.entrySet()) {
            validateTransaction(entry.getKey(), entry.getValue());
        }
    }

    private void advanceAccount(UUID accountId, Account account) {
        ensurePositionCollections(account);
        long savingsBefore = account.savingsMicro;
        account.savingsMicro = accrue(
                account.savingsMicro, EconomyEngine.savingsAnnualRate(regime));
        long savingsInterest = account.savingsMicro - savingsBefore;
        if (savingsInterest > 0L) {
            account.realizedGainMicro = saturatingAdd(
                    account.realizedGainMicro, savingsInterest);
            PortfolioAnalytics.recordTransaction(
                    account,
                    economicDay,
                    PortfolioTransactionKind.INTEREST,
                    "SAVINGS",
                    0L,
                    0.0,
                    savingsInterest,
                    0L,
                    savingsInterest);
        }

        for (CdPosition position : account.cdPositions.values()) {
            if (economicDay <= position.maturityDay) {
                position.valueMicro = accrue(position.valueMicro, position.annualRate);
            }
        }

        for (LoanPosition position : account.loanPositions.values()) {
            if (position.resolved) {
                continue;
            }
            if (economicDay <= position.maturityDay) {
                position.valueMicro = accrue(position.valueMicro, position.annualRate);
                position.stress += EconomyEngine.loanStressIncrement(regime);
            }
            if (economicDay >= position.maturityDay) {
                int termDays = safeTerm(position.openDay, position.maturityDay);
                EconomyEngine.LoanResolution resolution = EconomyEngine.resolveLoan(
                        seed,
                        accountId,
                        position.serial,
                        position.openDay,
                        termDays,
                        position.stress);
                position.valueMicro = scale(position.valueMicro, resolution.recoveryRate());
                position.recoveryRate = resolution.recoveryRate();
                position.outcome = resolution.outcome();
                position.resolved = true;
            }
        }
        syncLegacyProductViews(account);
    }

    /** Converts the pre-format-9 scalar term products into position collections exactly once. */
    static void ensurePositionCollections(Account account) {
        if (account.cdPositions.isEmpty() && account.cdPrincipalMicro > 0L) {
            CdPosition position = new CdPosition();
            position.positionId = nextPositionId(account);
            position.principalMicro = account.cdPrincipalMicro;
            position.valueMicro = account.cdValueMicro;
            position.openDay = account.cdOpenDay;
            position.maturityDay = account.cdMaturityDay;
            position.annualRate = account.cdAnnualRate;
            account.cdPositions.put(position.positionId, position);
        }
        if (account.loanPositions.isEmpty() && account.loanPrincipalMicro > 0L) {
            LoanPosition position = new LoanPosition();
            position.positionId = nextPositionId(account);
            position.principalMicro = account.loanPrincipalMicro;
            position.valueMicro = account.loanValueMicro;
            position.openDay = account.loanOpenDay;
            position.maturityDay = account.loanMaturityDay;
            position.serial = Math.max(1L, account.loanSerial);
            position.annualRate = account.loanAnnualRate;
            position.stress = account.loanStress;
            position.recoveryRate = account.loanRecoveryRate;
            position.resolved = account.loanResolved;
            position.outcome = account.loanOutcome;
            account.loanPositions.put(position.positionId, position);
        }
        syncLegacyProductViews(account);
    }

    static long nextPositionId(Account account) {
        long candidate = Math.max(1L, account.nextTermPositionId);
        while (account.cdPositions.containsKey(candidate)
                || account.loanPositions.containsKey(candidate)) {
            if (candidate == Long.MAX_VALUE) {
                throw new IllegalStateException("Term position identifier space exhausted");
            }
            candidate++;
        }
        account.nextTermPositionId = candidate == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : candidate + 1L;
        return candidate;
    }

    static void syncLegacyProductViews(Account account) {
        CdPosition cd = account.cdPositions.values().stream().findFirst().orElse(null);
        if (cd == null) {
            account.cdPrincipalMicro = 0L;
            account.cdValueMicro = 0L;
            account.cdOpenDay = 0L;
            account.cdMaturityDay = 0L;
            account.cdAnnualRate = 0.0;
        } else {
            account.cdPrincipalMicro = cd.principalMicro;
            account.cdValueMicro = cd.valueMicro;
            account.cdOpenDay = cd.openDay;
            account.cdMaturityDay = cd.maturityDay;
            account.cdAnnualRate = cd.annualRate;
        }
        LoanPosition loan = account.loanPositions.values().stream().findFirst().orElse(null);
        if (loan == null) {
            account.loanPrincipalMicro = 0L;
            account.loanValueMicro = 0L;
            account.loanOpenDay = 0L;
            account.loanMaturityDay = 0L;
            account.loanAnnualRate = 0.0;
            account.loanStress = 0.0;
            account.loanRecoveryRate = 1.0;
            account.loanResolved = false;
            account.loanOutcome = EconomyEngine.LoanOutcome.REPAID;
        } else {
            account.loanPrincipalMicro = loan.principalMicro;
            account.loanValueMicro = loan.valueMicro;
            account.loanOpenDay = loan.openDay;
            account.loanMaturityDay = loan.maturityDay;
            account.loanSerial = Math.max(account.loanSerial, loan.serial);
            account.loanAnnualRate = loan.annualRate;
            account.loanStress = loan.stress;
            account.loanRecoveryRate = loan.recoveryRate;
            account.loanResolved = loan.resolved;
            account.loanOutcome = loan.outcome;
        }
    }

    private static void accrueEndowmentPayout(
            ProsperityFund fund, double annualRate, double reserveFraction) {
        for (DonationPurpose purpose : DonationPurpose.values()) {
            long principal = fund.endowmentPrincipalMicro.getOrDefault(purpose, 0L);
            if (principal <= 0L) {
                continue;
            }
            long payout = Math.max(0L, Math.round(
                    EconomyEngine.compoundDaily(principal, annualRate)));
            if (payout <= 0L) {
                continue;
            }
            long reserve = Math.max(0L, Math.round(payout * reserveFraction));
            fund.emergencyReserveMicro = saturatingAdd(fund.emergencyReserveMicro, reserve);
            fund.spendableMicro.merge(
                    purpose, payout - reserve, EconomyState::saturatingAdd);
        }
    }

    private static void spendProsperityFundAutomatically(
            VillageRecord village, long day, long dailyCapMicro) {
        ProsperityFund fund = village.prosperityFund;
        if (fund.lastSpendingDay != day) {
            fund.lastSpendingDay = day;
            fund.spentTodayMicro = 0L;
        }
        rollCompletedProjectSponsorships(village);
        long remainingCap = Math.max(0L, dailyCapMicro - fund.spentTodayMicro);
        if (remainingCap <= 0L) return;

        for (Map.Entry<Long, Long> entry : new ArrayList<>(
                fund.projectSponsorshipMicro.entrySet())) {
            VillageProject project = village.projects.stream()
                    .filter(candidate -> candidate.projectId == entry.getKey())
                    .findFirst()
                    .orElse(null);
            if (project == null || project.economicComplete || entry.getValue() <= 0L) {
                continue;
            }
            long requested = Math.min(remainingCap, entry.getValue());
            long spent = applyProjectSponsorshipInputs(village, project, requested, day);
            if (spent <= 0L) {
                continue;
            }
            fund.projectSponsorshipMicro.compute(entry.getKey(), (ignored, balance) ->
                    balance == null || balance <= spent ? null : balance - spent);
            fund.spentTodayMicro = saturatingAdd(fund.spentTodayMicro, spent);
            fund.lifetimeSpentMicro = saturatingAdd(fund.lifetimeSpentMicro, spent);
            remainingCap -= spent;
            if (remainingCap <= 0L) return;
        }

        DonationPurpose[] priority = donationPriority(village);
        for (DonationPurpose purpose : priority) {
            long available = fund.spendableMicro.getOrDefault(purpose, 0L);
            if (available <= 0L) continue;
            long requested = Math.min(remainingCap, available);
            long spent = applyFundInputs(village, purpose, requested, day);
            if (spent <= 0L) continue;
            fund.spendableMicro.compute(purpose, (ignored, balance) ->
                    balance == null || balance <= spent ? null : balance - spent);
            fund.spentTodayMicro = saturatingAdd(fund.spentTodayMicro, spent);
            fund.lifetimeSpentMicro = saturatingAdd(fund.lifetimeSpentMicro, spent);
            remainingCap -= spent;
            if (remainingCap <= 0L) return;
        }

        boolean emergency = village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                || village.foodSupply < Math.max(2.0, village.population * 0.5)
                || village.safety < 25.0;
        if (emergency && fund.emergencyReserveMicro > 0L) {
            DonationPurpose purpose = village.lifecycle
                            == VillageProsperityEngine.Lifecycle.ABANDONED
                            || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                    ? DonationPurpose.RESTORATION
                    : village.foodSupply < Math.max(2.0, village.population * 0.5)
                            ? DonationPurpose.FOOD
                            : DonationPurpose.SECURITY;
            long requested = Math.min(remainingCap, fund.emergencyReserveMicro);
            long spent = applyFundInputs(village, purpose, requested, day);
            if (spent <= 0L) return;
            fund.emergencyReserveMicro -= spent;
            fund.spentTodayMicro = saturatingAdd(fund.spentTodayMicro, spent);
            fund.lifetimeSpentMicro = saturatingAdd(fund.lifetimeSpentMicro, spent);
        }
    }

    private static void rollCompletedProjectSponsorships(VillageRecord village) {
        ProsperityFund fund = village.prosperityFund;
        for (Map.Entry<Long, Long> entry : new ArrayList<>(
                fund.projectSponsorshipMicro.entrySet())) {
            long balance = Math.max(0L, entry.getValue());
            VillageProject project = village.projects.stream()
                    .filter(candidate -> candidate.projectId == entry.getKey())
                    .findFirst()
                    .orElse(null);
            if (project != null && !project.economicComplete) {
                continue;
            }
            fund.projectSponsorshipMicro.remove(entry.getKey());
            if (balance > 0L) {
                DonationPurpose purpose = project == null
                        ? recordedSponsorshipPurpose(fund, entry.getKey())
                        : donationPurposeForProject(project);
                fund.spendableMicro.merge(purpose, balance, EconomyState::saturatingAdd);
            }
        }
    }

    private static DonationPurpose recordedSponsorshipPurpose(
            ProsperityFund fund, long projectId) {
        for (int index = fund.contributions.size() - 1; index >= 0; index--) {
            FundContribution contribution = fund.contributions.get(index);
            if (contribution.type == ProsperityFundType.PROJECT_SPONSORSHIP
                    && contribution.projectId == projectId) {
                return contribution.purpose;
            }
        }
        return DonationPurpose.INFRASTRUCTURE;
    }

    private static DonationPurpose[] donationPriority(VillageRecord village) {
        if (village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT) {
            return new DonationPurpose[] {
                    DonationPurpose.RESTORATION,
                    DonationPurpose.GENERAL,
                    DonationPurpose.FOOD,
                    DonationPurpose.HOUSING,
                    DonationPurpose.INFRASTRUCTURE,
                    DonationPurpose.SECURITY,
                    DonationPurpose.TRADE
            };
        }
        return new DonationPurpose[] {
                DonationPurpose.FOOD,
                DonationPurpose.HOUSING,
                DonationPurpose.SECURITY,
                DonationPurpose.INFRASTRUCTURE,
                DonationPurpose.TRADE,
                DonationPurpose.GENERAL,
                DonationPurpose.RESTORATION
        };
    }

    static DonationPurpose donationPurposeForProject(VillageProject project) {
        if (project == null || project.type == null) {
            return DonationPurpose.INFRASTRUCTURE;
        }
        return switch (project.type) {
            case COTTAGE, HOUSE, INN -> DonationPurpose.HOUSING;
            case GRANARY -> DonationPurpose.FOOD;
            case GUARD_POST -> DonationPurpose.SECURITY;
            case MARKET_SQUARE, EXCHANGE_HALL -> DonationPurpose.TRADE;
            case WAREHOUSE, MINE_ENTRANCE, SMITHY -> DonationPurpose.INFRASTRUCTURE;
        };
    }

    static long applyProjectSponsorshipInputs(
            VillageRecord village, VillageProject project, long requestedMicro, long day) {
        if (project == null || project.economicComplete) {
            return 0L;
        }
        return applyFundInputs(
                village, DonationPurpose.INFRASTRUCTURE, requestedMicro, day);
    }

    static long applyFundInputs(
            VillageRecord village, DonationPurpose purpose, long requestedMicro, long day) {
        if (village == null || purpose == null || requestedMicro <= 0L) {
            return 0L;
        }
        long spentMicro = Math.min(requestedMicro, fundInputCapacityMicro(village, purpose));
        if (spentMicro <= 0L) {
            return 0L;
        }
        double emeralds = spentMicro / (double) MICRO;
        switch (purpose) {
            case GENERAL -> {
                village.treasury = Math.min(1_000_000.0, village.treasury + emeralds * 0.50);
                village.developmentPoints = Math.min(
                        1_000_000.0, village.developmentPoints + emeralds * 0.25);
            }
            case HOUSING, INFRASTRUCTURE -> {
                village.materialSupply = Math.min(
                        20_000.0, village.materialSupply + emeralds * 0.50);
                village.developmentPoints = Math.min(
                        1_000_000.0, village.developmentPoints + emeralds * 0.40);
            }
            case FOOD -> village.foodSupply = Math.min(
                    20_000.0, village.foodSupply + emeralds * 0.75);
            case SECURITY -> {
                village.materialSupply = Math.min(
                        20_000.0, village.materialSupply + emeralds * 0.35);
                village.developmentPoints = Math.min(
                        1_000_000.0, village.developmentPoints + emeralds * 0.35);
            }
            case TRADE -> {
                village.treasury = Math.min(1_000_000.0, village.treasury + emeralds * 0.65);
                village.developmentPoints = Math.min(
                        1_000_000.0, village.developmentPoints + emeralds * 0.15);
            }
            case RESTORATION -> {
                village.restorationFund = Math.min(
                        1_000_000.0, village.restorationFund + emeralds);
                if (village.restorationFund
                        >= VillageProsperityEngine.RESTORATION_EMERALD_TARGET) {
                    village.restorationFunded = true;
                    village.recoveryEligibleDay = Math.min(
                            village.recoveryEligibleDay, dayAfter(day, 3L));
                }
            }
        }
        return spentMicro;
    }

    private static long fundInputCapacityMicro(
            VillageRecord village, DonationPurpose purpose) {
        double capacityEmeralds = switch (purpose) {
            case GENERAL -> Math.max(
                    remainingInputCapacity(village.treasury, 1_000_000.0, 0.50),
                    remainingInputCapacity(village.developmentPoints, 1_000_000.0, 0.25));
            case HOUSING, INFRASTRUCTURE -> Math.max(
                    remainingInputCapacity(village.materialSupply, 20_000.0, 0.50),
                    remainingInputCapacity(village.developmentPoints, 1_000_000.0, 0.40));
            case FOOD -> remainingInputCapacity(village.foodSupply, 20_000.0, 0.75);
            case SECURITY -> Math.max(
                    remainingInputCapacity(village.materialSupply, 20_000.0, 0.35),
                    remainingInputCapacity(village.developmentPoints, 1_000_000.0, 0.35));
            case TRADE -> Math.max(
                    remainingInputCapacity(village.treasury, 1_000_000.0, 0.65),
                    remainingInputCapacity(village.developmentPoints, 1_000_000.0, 0.15));
            case RESTORATION -> remainingInputCapacity(
                    village.restorationFund, 1_000_000.0, 1.0);
        };
        if (!Double.isFinite(capacityEmeralds) || capacityEmeralds <= 0.0) {
            return 0L;
        }
        double capacityMicro = capacityEmeralds * MICRO;
        if (capacityMicro >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) StrictMath.floor(capacityMicro));
    }

    private static double remainingInputCapacity(
            double current, double maximum, double outputPerEmerald) {
        if (!Double.isFinite(current) || current >= maximum || outputPerEmerald <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, maximum - Math.max(0.0, current)) / outputPerEmerald;
    }

    private static long dayAfter(long day, long addition) {
        return day > Long.MAX_VALUE - addition ? Long.MAX_VALUE : day + addition;
    }

    private void normalizeHighPrices() {
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            double price = prices.get(asset.ticker());
            if (price <= 1_000_000.0) {
                continue;
            }
            prices.put(asset.ticker(), price / 1_000.0);
            List<Double> history = priceHistory.get(asset.ticker());
            if (history != null) {
                history.replaceAll(value -> value / 1_000.0);
            }
            for (Account account : accounts.values()) {
                double shares = account.shares.getOrDefault(asset.ticker(), 0.0);
                if (shares > 0.0) {
                    account.shares.put(asset.ticker(), shares * 1_000.0);
                }
            }
        }
    }

    private void recordCurrentPrices() {
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            List<Double> history = priceHistory.computeIfAbsent(
                    asset.ticker(), ignored -> new ArrayList<>());
            history.add(prices.get(asset.ticker()));
            while (history.size() > HISTORY_DAYS) {
                history.remove(0);
            }
        }
    }

    private void recordCurrentCommodities() {
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            List<Double> history = commodityHistory.computeIfAbsent(
                    commodity.id(), ignored -> new ArrayList<>());
            history.add(commodityPrices.get(commodity.id()));
            while (history.size() > HISTORY_DAYS) {
                history.remove(0);
            }
        }
    }

    private void validateHistory() throws IOException {
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            List<Double> history = priceHistory.get(asset.ticker());
            if (history == null || history.isEmpty() || history.size() > HISTORY_DAYS) {
                throw new IOException("Invalid price history for " + asset.ticker());
            }
            for (Double value : history) {
                validatePrice("history " + asset.ticker(), value);
            }
        }
        for (String ticker : priceHistory.keySet()) {
            if (!knownTicker(ticker)) {
                throw new IOException("Unknown price-history ticker " + ticker);
            }
        }
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            List<Double> history = commodityHistory.get(commodity.id());
            if (history == null || history.isEmpty() || history.size() > HISTORY_DAYS) {
                throw new IOException("Invalid commodity history for " + commodity.id());
            }
            for (Double value : history) {
                validatePrice("commodity history " + commodity.id(), value);
            }
        }
    }

    private static void validateVillage(UUID id, VillageRecord village, long economicDay)
            throws IOException {
        if (id == null
                || village == null
                || village.villageId == null
                || !id.equals(village.villageId)
                || village.dimensionKey == null
                || village.dimensionKey.isBlank()
                || village.lifecycle == null
                || village.lastIncidentCause == null
                || village.population < 0
                || village.population > VillageProsperityEngine.MAX_ABSTRACT_POPULATION
                || village.observedPopulation < 0
                || village.housingCapacity < 0
                || village.pendingSettlers < 0
                || village.pendingSettlers
                        > VillageProsperityEngine.MAX_ABSTRACT_POPULATION - village.population
                || village.developmentTier < 0
                || village.developmentTier > 5
                || village.discoveredDay < 0L
                || village.discoveredDay > economicDay
                || village.lastSimulatedDay < 0L
                || village.lastSimulatedDay > economicDay
                || village.lastCensusDay < 0L
                || village.lastCensusDay > economicDay
                || village.lastIncidentDay < 0L
                || village.lastIncidentDay > economicDay
                || village.recoveryEligibleDay < 0L
                || village.abandonedSinceDay < 0L
                || village.abandonedSinceDay > economicDay
                || village.lastCollapseDay < 0L
                || village.lastCollapseDay > economicDay
                || village.marketSuppressedUntilDay < 0L
                || village.projectSerial < 0L
                || village.collapseCount < 0
                || village.hostileCasualties < 0
                || village.playerCasualties < 0
                || village.environmentalCasualties < 0) {
            throw new IOException("Invalid village record " + id);
        }
        validateFiniteRange("village prosperity", village.prosperity, 0.0, 100.0);
        validateFiniteRange("village safety", village.safety, 0.0, 100.0);
        validateFiniteRange("village food", village.foodSupply, 0.0, 20_000.0);
        validateFiniteRange("village materials", village.materialSupply, 0.0, 20_000.0);
        validateFiniteRange("village treasury", village.treasury, 0.0, 1_000_000.0);
        validateFiniteRange("village restoration fund", village.restorationFund, 0.0, 1_000_000.0);
        validateFiniteRange("village development points", village.developmentPoints, 0.0, 1_000_000.0);
        double[] outputs = {
                village.agricultureOutput,
                village.miningOutput,
                village.tradeOutput,
                village.redstoneOutput,
                village.alchemyOutput,
                village.transportOutput,
                village.securityOutput
        };
        for (double output : outputs) {
            validateFiniteRange("village output", output, 0.0, 1_000_000.0);
        }
        if (village.residents.size() > VillageProsperityEngine.RESIDENT_HISTORY_LIMIT
                || village.projects.size() > VillageProsperityEngine.MAX_PROJECTS_PER_VILLAGE
                || village.incidents.size() > VillageProsperityEngine.INCIDENT_HISTORY_LIMIT) {
            throw new IOException("Village record exceeds bounded history limits " + id);
        }
        for (Map.Entry<UUID, ResidentRecord> residentEntry : village.residents.entrySet()) {
            ResidentRecord resident = residentEntry.getValue();
            if (residentEntry.getKey() == null
                    || resident == null
                    || resident.residentId == null
                    || !residentEntry.getKey().equals(resident.residentId)
                    || resident.profession == null
                    || resident.profession.isBlank()
                    || resident.status == null
                    || resident.lastSeenDay < 0L
                    || resident.lastSeenDay > economicDay) {
                throw new IOException("Invalid resident record in village " + id);
            }
        }
        long previousProject = 0L;
        for (VillageProject project : village.projects) {
            if (project == null
                    || project.type == null
                    || project.projectId <= previousProject
                    || project.approvedDay < 0L
                    || project.approvedDay > economicDay
                    || project.completedDay < 0L
                    || project.completedDay > economicDay
                    || !Double.isFinite(project.economicProgress)
                    || project.economicProgress < 0.0
                    || project.economicProgress > 1.0
                    || project.materializedBlocks < 0
                    || project.totalBlocks < 0
                    || project.materializedBlocks > project.totalBlocks
                    || project.retryAfterGameTick < 0L
                    || project.materializationFailures < 0
                    || (project.economicComplete && project.economicProgress < 1.0)
                    || (project.materializedComplete
                            && project.materializedBlocks < project.totalBlocks)
                    || !validProjectBounds(project)) {
                throw new IOException("Invalid village project in " + id);
            }
            previousProject = project.projectId;
        }
        if (village.projectSerial < previousProject) {
            throw new IOException("Village project serial trails its project history " + id);
        }
        for (VillageIncident incident : village.incidents) {
            if (incident == null
                    || incident.cause == null
                    || incident.day < 0L
                    || incident.day > economicDay
                    || incident.casualties <= 0) {
                throw new IOException("Invalid village incident in " + id);
            }
        }
        validateProsperityFund(id, village.prosperityFund, economicDay);
    }

    private void validateMarketShadow(
            UUID villageId, VillageMarketShadow shadow, long economicDay) throws IOException {
        if (villageId == null
                || shadow == null
                || !shadow.present
                || shadow.formulaVersion
                        != VillageProsperityEngine.MARKET_SHADOW_FORMULA_VERSION
                || !villages.containsKey(villageId)
                || shadow.capturedDay < 0L
                || shadow.capturedDay > economicDay
                || shadow.minimumReleaseDay < shadow.capturedDay
                || shadow.recoveryPopulation <= 0
                || shadow.recoveryPopulation > VillageProsperityEngine.MAX_ABSTRACT_POPULATION
                || shadow.counterfactualVillage == null
                || !villageId.equals(shadow.counterfactualVillage.villageId)
                || !Double.isFinite(shadow.weight)
                || shadow.weight < 1.0
                || shadow.weight > 6.0) {
            throw new IOException("Invalid village market shadow " + villageId);
        }
        validateVillage(villageId, shadow.counterfactualVillage, economicDay);
        if (!VillageProsperityEngine.isMarketShadowCurrent(shadow, economicDay)) {
            throw new IOException("Stale village market shadow " + villageId);
        }
        validateFiniteRange("market-shadow broad score", shadow.broad, -1.0, 1.0);
        validateFiniteRange("market-shadow mining score", shadow.mining, -1.0, 1.0);
        validateFiniteRange("market-shadow agriculture score", shadow.agriculture, -1.0, 1.0);
        validateFiniteRange("market-shadow trade score", shadow.trade, -1.0, 1.0);
        validateFiniteRange("market-shadow redstone score", shadow.redstone, -1.0, 1.0);
        validateFiniteRange("market-shadow alchemy score", shadow.alchemy, -1.0, 1.0);
        validateFiniteRange("market-shadow transport score", shadow.transport, -1.0, 1.0);
        validateFiniteRange("market-shadow security score", shadow.security, -1.0, 1.0);
    }

    private static void validateFiniteRange(
            String name, double value, double minimum, double maximum) throws IOException {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IOException("Invalid " + name);
        }
    }

    private static boolean validProjectBounds(VillageProject project) {
        if (project.boundsMinPos == 0L && project.boundsMaxPos == 0L) {
            // Legacy projects did not persist bounds.
            return true;
        }
        return unpackX(project.boundsMinPos) <= unpackX(project.boundsMaxPos)
                && unpackY(project.boundsMinPos) <= unpackY(project.boundsMaxPos)
                && unpackZ(project.boundsMinPos) <= unpackZ(project.boundsMaxPos);
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

    private static void validateAccount(UUID id, Account account, long economicDay)
            throws IOException {
        if (id == null || account == null) {
            throw new IOException("Null account for " + id);
        }
        ensurePositionCollections(account);
        validateBalance(id, "cash", account.cashMicro);
        validateBalance(id, "savings", account.savingsMicro);
        validateBalance(id, "CD principal", account.cdPrincipalMicro);
        validateBalance(id, "CD value", account.cdValueMicro);
        validateBalance(id, "loan principal", account.loanPrincipalMicro);
        validateBalance(id, "loan value", account.loanValueMicro);
        validateRate(id, "CD", account.cdAnnualRate);
        validateRate(id, "loan", account.loanAnnualRate);

        if (account.cdPositions.size() > MAX_TERM_POSITIONS
                || account.loanPositions.size() > MAX_TERM_POSITIONS
                || account.nextTermPositionId <= 0L) {
            throw new IOException("Account term-position limits are invalid for " + id);
        }
        for (Map.Entry<Long, CdPosition> entry : account.cdPositions.entrySet()) {
            CdPosition position = entry.getValue();
            if (entry.getKey() == null
                    || position == null
                    || entry.getKey() != position.positionId
                    || position.positionId <= 0L
                    || position.principalMicro <= 0L
                    || position.valueMicro < position.principalMicro
                    || position.openDay < 0L
                    || position.openDay > economicDay
                    || position.maturityDay <= position.openDay) {
                throw new IOException("Inconsistent CD position for " + id);
            }
            validateRate(id, "CD position", position.annualRate);
            if (position.annualRate <= 0.0) {
                throw new IOException("Inactive CD rate for " + id);
            }
        }
        Set<Long> positionIds = new HashSet<>(account.cdPositions.keySet());
        for (Map.Entry<Long, LoanPosition> entry : account.loanPositions.entrySet()) {
            if (entry.getKey() == null || !positionIds.add(entry.getKey())) {
                throw new IOException("Duplicate or null term position for " + id);
            }
            validateLoanPosition(id, entry.getKey(), entry.getValue(), economicDay);
        }

        for (Map.Entry<String, Double> holding : account.shares.entrySet()) {
            if (!knownTicker(holding.getKey())
                    || holding.getValue() == null
                    || !Double.isFinite(holding.getValue())
                    || holding.getValue() < 0.0) {
                throw new IOException("Invalid holding for " + id);
            }
        }
        for (Map.Entry<String, Long> basis : account.shareCostBasisMicro.entrySet()) {
            if (!knownTicker(basis.getKey()) || basis.getValue() == null || basis.getValue() < 0L) {
                throw new IOException("Invalid cost basis for " + id);
            }
        }
        if (account.totalContributionsMicro < 0L
                || account.totalWithdrawalsMicro < 0L
                || account.transactionLedger.size() > MAX_PORTFOLIO_LEDGER_ENTRIES
                || account.netWorthHistory.size() > HISTORY_DAYS) {
            throw new IOException("Invalid portfolio accounting for " + id);
        }
        long previousHistoryDay = -1L;
        for (PortfolioValuePoint point : account.netWorthHistory) {
            if (point == null
                    || point.day < 0L
                    || point.day > economicDay
                    || point.day < previousHistoryDay
                    || point.valueMicro < 0L) {
                throw new IOException("Invalid net-worth history for " + id);
            }
            previousHistoryDay = point.day;
        }
        for (PortfolioTransaction transaction : account.transactionLedger) {
            if (transaction == null
                    || transaction.kind == null
                    || transaction.symbol == null
                    || transaction.day < 0L
                    || transaction.day > economicDay
                    || !Double.isFinite(transaction.quantity)
                    || transaction.quantity < 0.0
                    || transaction.amountMicro < 0L
                    || transaction.costBasisMicro < 0L) {
                throw new IOException("Invalid portfolio transaction for " + id);
            }
        }
    }

    private static void validateLoanPosition(
            UUID id, long key, LoanPosition position, long economicDay) throws IOException {
        if (position == null
                || key != position.positionId
                || position.positionId <= 0L
                || position.principalMicro <= 0L
                || position.valueMicro < 0L
                || position.openDay < 0L
                || position.openDay > economicDay
                || position.maturityDay <= position.openDay
                || position.serial <= 0L
                || !Double.isFinite(position.stress)
                || position.stress < 0.0
                || !Double.isFinite(position.recoveryRate)
                || position.recoveryRate < 0.0
                || position.recoveryRate > 1.0
                || position.outcome == null) {
            throw new IOException("Inconsistent lending position for " + id);
        }
        validateRate(id, "lending position", position.annualRate);
        if (position.annualRate <= 0.0
                || (position.resolved && economicDay < position.maturityDay)
                || (!position.resolved && economicDay >= position.maturityDay)
                || (!position.resolved && position.valueMicro < position.principalMicro)
                || (!position.resolved
                        && (position.recoveryRate != 1.0
                                || position.outcome != EconomyEngine.LoanOutcome.REPAID))) {
            throw new IOException("Invalid lending state for " + id);
        }
        if (position.resolved) {
            boolean validOutcome = switch (position.outcome) {
                case REPAID -> position.recoveryRate == 1.0;
                case PARTIAL_DEFAULT -> position.recoveryRate > 0.0
                        && position.recoveryRate < 1.0;
                case FULL_DEFAULT -> position.recoveryRate == 0.0 && position.valueMicro == 0L;
            };
            if (!validOutcome) {
                throw new IOException("Invalid lending outcome for " + id);
            }
        }
    }

    private static void validateDonor(UUID id, DonorRecord donor) throws IOException {
        if (id == null
                || donor == null
                || donor.lifetimeContributionMicro < 0L
                || donor.contributionCount < 0
                || donor.byTypeMicro.values().stream().anyMatch(value -> value == null || value < 0L)
                || donor.byPurposeMicro.values().stream().anyMatch(value -> value == null || value < 0L)) {
            throw new IOException("Invalid donor record " + id);
        }
    }

    private static void validateProsperityFund(
            UUID villageId, ProsperityFund fund, long economicDay) throws IOException {
        if (fund == null
                || fund.emergencyReserveMicro < 0L
                || fund.lifetimeReceivedMicro < 0L
                || fund.lifetimeSpentMicro < 0L
                || fund.lastSpendingDay < -1L
                || fund.lastSpendingDay > economicDay
                || fund.spentTodayMicro < 0L
                || fund.spendableMicro.values().stream().anyMatch(value -> value == null || value < 0L)
                || fund.endowmentPrincipalMicro.values().stream().anyMatch(value -> value == null || value < 0L)
                || fund.projectSponsorshipMicro.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null
                                || entry.getKey() <= 0L
                                || entry.getValue() == null
                                || entry.getValue() < 0L)
                || fund.donorTotalsMicro.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null
                                || entry.getValue() == null
                                || entry.getValue() < 0L)
                || fund.contributions.size() > MAX_FUND_LEDGER_ENTRIES) {
            throw new IOException("Invalid prosperity fund for village " + villageId);
        }
        for (FundContribution contribution : fund.contributions) {
            if (contribution == null
                    || contribution.donorId == null
                    || contribution.type == null
                    || contribution.purpose == null
                    || contribution.day < 0L
                    || contribution.day > economicDay
                    || contribution.amountMicro <= 0L
                    || (contribution.type == ProsperityFundType.PROJECT_SPONSORSHIP
                            && contribution.projectId <= 0L)) {
                throw new IOException("Invalid prosperity-fund contribution for " + villageId);
            }
        }
    }

    private static void validateTransaction(
            UUID mapPlayerId,
            PendingInventoryTransaction transaction) throws IOException {
        if (transaction == null
                || transaction.transactionId == null
                || transaction.playerId == null
                || !transaction.playerId.equals(mapPlayerId)
                || transaction.kind == null
                || transaction.stage == null
                || transaction.itemKey == null
                || transaction.itemKey.isBlank()
                || transaction.itemCount <= 0
                || transaction.itemCount > MAX_PENDING_INVENTORY_ITEMS
                || transaction.inventoryCountBefore < 0
                || transaction.createdEconomicDay < 0L
                || transaction.createdWallClockMs < 0L) {
            throw new IOException("Invalid pending inventory transaction for " + mapPlayerId);
        }
        if (transaction.kind == InventoryTransactionKind.WITHDRAWAL) {
            long expectedDelta = safeMultiplyMicro(transaction.itemCount);
            if (transaction.stage != InventoryTransactionStage.BANK_COMMITTED
                    || transaction.bankDeltaMicro != -expectedDelta) {
                throw new IOException("Invalid pending withdrawal for " + mapPlayerId);
            }
            return;
        }
        if (transaction.bankDeltaMicro <= 0L) {
            throw new IOException("Invalid pending bank credit for " + mapPlayerId);
        }
        if (transaction.kind == InventoryTransactionKind.DEPOSIT
                && transaction.bankDeltaMicro != safeMultiplyMicro(transaction.itemCount)) {
            throw new IOException("Invalid pending emerald deposit for " + mapPlayerId);
        }
    }

    private static long safeMultiplyMicro(int count) throws IOException {
        if (count > Long.MAX_VALUE / MICRO) {
            throw new IOException("Inventory transaction is too large");
        }
        return count * MICRO;
    }

    private static boolean knownTicker(String ticker) {
        String normalized = ticker == null ? "" : ticker.toUpperCase(Locale.ROOT);
        return EconomyEngine.ASSETS.stream().anyMatch(asset -> asset.ticker().equals(normalized));
    }

    private static long accrue(long currentMicro, double annualRate) {
        if (currentMicro <= 0L) {
            return 0L;
        }
        long interest = Math.max(0L,
                Math.round(EconomyEngine.compoundDaily(currentMicro, annualRate)));
        return saturatingAdd(currentMicro, interest);
    }

    private static long scale(long value, double factor) {
        if (value <= 0L || factor <= 0.0) {
            return 0L;
        }
        double scaled = value * factor;
        return !Double.isFinite(scaled) || scaled >= Long.MAX_VALUE
                ? Long.MAX_VALUE
                : Math.max(0L, Math.round(scaled));
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE
                : left + right;
    }

    private static int safeTerm(long openDay, long maturityDay) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, maturityDay - openDay));
    }

    private static double boundedPrice(double value) {
        if (!Double.isFinite(value)) {
            return 1_000_000.0;
        }
        return Math.max(0.000001, Math.min(1_000_000_000.0, value));
    }

    private static void validatePrice(String name, Double value) throws IOException {
        if (value == null || !Double.isFinite(value) || value <= 0.0 || value > 1.0e12) {
            throw new IOException("Invalid " + name + " price");
        }
    }

    private static void validateBalance(UUID id, String name, long value) throws IOException {
        if (value < 0L) {
            throw new IOException("Negative " + name + " balance for " + id);
        }
    }

    private static void validateRate(UUID id, String name, double value) throws IOException {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IOException("Invalid " + name + " rate for " + id);
        }
    }
}
