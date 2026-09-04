package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
    public static final int FORMAT_VERSION = 8;
    public static final int HISTORY_DAYS = 180;
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

        public boolean hasCd() {
            return cdPrincipalMicro > 0L;
        }

        public boolean hasLoan() {
            return loanPrincipalMicro > 0L;
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

        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            advanceAccount(entry.getKey(), entry.getValue());
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
        double value = ((double) account.cashMicro
                + account.savingsMicro
                + account.cdValueMicro
                + account.loanValueMicro) / MICRO;
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
        for (Long region : generatedBankAnchors.keySet()) {
            if (region == null || !generatedBankRegions.contains(region)) {
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
        for (Map.Entry<UUID, PendingInventoryTransaction> entry
                : pendingInventoryTransactions.entrySet()) {
            validateTransaction(entry.getKey(), entry.getValue());
        }
    }

    private void advanceAccount(UUID accountId, Account account) {
        account.savingsMicro = accrue(
                account.savingsMicro, EconomyEngine.savingsAnnualRate(regime));

        if (account.hasCd() && economicDay <= account.cdMaturityDay) {
            account.cdValueMicro = accrue(account.cdValueMicro, account.cdAnnualRate);
        }

        if (!account.hasLoan() || account.loanResolved) {
            return;
        }
        if (economicDay <= account.loanMaturityDay) {
            account.loanValueMicro = accrue(account.loanValueMicro, account.loanAnnualRate);
            account.loanStress += EconomyEngine.loanStressIncrement(regime);
        }
        if (economicDay >= account.loanMaturityDay) {
            int termDays = safeTerm(account.loanOpenDay, account.loanMaturityDay);
            EconomyEngine.LoanResolution resolution = EconomyEngine.resolveLoan(
                    seed,
                    accountId,
                    account.loanSerial,
                    account.loanOpenDay,
                    termDays,
                    account.loanStress);
            account.loanValueMicro = scale(account.loanValueMicro, resolution.recoveryRate());
            account.loanRecoveryRate = resolution.recoveryRate();
            account.loanOutcome = resolution.outcome();
            account.loanResolved = true;
        }
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
    }

    private static void validateVillage(UUID id, VillageRecord village, long economicDay)
            throws IOException {
        if (village == null
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
                || village.developmentTier < 0
                || village.developmentTier > 5
                || village.discoveredDay < 0L
                || village.lastSimulatedDay < 0L
                || village.lastSimulatedDay > economicDay
                || village.lastCensusDay < 0L
                || village.lastIncidentDay < 0L
                || village.recoveryEligibleDay < 0L
                || village.lastCollapseDay < 0L
                || village.lastCollapseDay > economicDay
                || village.marketSuppressedUntilDay < 0L) {
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
        for (VillageIncident incident : village.incidents) {
            if (incident == null
                    || incident.cause == null
                    || incident.day < 0L
                    || incident.day > economicDay
                    || incident.casualties <= 0) {
                throw new IOException("Invalid village incident in " + id);
            }
        }
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
        if (account == null) {
            throw new IOException("Null account for " + id);
        }
        validateBalance(id, "cash", account.cashMicro);
        validateBalance(id, "savings", account.savingsMicro);
        validateBalance(id, "CD principal", account.cdPrincipalMicro);
        validateBalance(id, "CD value", account.cdValueMicro);
        validateBalance(id, "loan principal", account.loanPrincipalMicro);
        validateBalance(id, "loan value", account.loanValueMicro);
        validateRate(id, "CD", account.cdAnnualRate);
        validateRate(id, "loan", account.loanAnnualRate);

        if (account.hasCd()) {
            if (account.cdValueMicro < account.cdPrincipalMicro
                    || account.cdOpenDay < 0L
                    || account.cdMaturityDay <= account.cdOpenDay
                    || account.cdAnnualRate <= 0.0) {
                throw new IOException("Inconsistent active CD for " + id);
            }
        } else if (account.cdValueMicro != 0L
                || account.cdOpenDay != 0L
                || account.cdMaturityDay != 0L
                || account.cdAnnualRate != 0.0) {
            throw new IOException("Inactive CD contains residual state for " + id);
        }

        if (!Double.isFinite(account.loanStress) || account.loanStress < 0.0) {
            throw new IOException("Invalid loan stress for " + id);
        }
        if (!Double.isFinite(account.loanRecoveryRate)
                || account.loanRecoveryRate < 0.0
                || account.loanRecoveryRate > 1.0) {
            throw new IOException("Invalid loan recovery for " + id);
        }
        if (account.hasLoan()) {
            if (account.loanOpenDay < 0L
                    || account.loanMaturityDay <= account.loanOpenDay
                    || account.loanAnnualRate <= 0.0
                    || account.loanSerial <= 0L) {
                throw new IOException("Inconsistent active villager loan for " + id);
            }
            if (account.loanResolved && economicDay < account.loanMaturityDay) {
                throw new IOException("Villager loan resolved before maturity for " + id);
            }
            if (!account.loanResolved
                    && (account.loanRecoveryRate != 1.0
                            || account.loanOutcome != EconomyEngine.LoanOutcome.REPAID)) {
                throw new IOException("Unresolved villager loan has a default outcome for " + id);
            }
            if (account.loanResolved) {
                switch (account.loanOutcome) {
                    case REPAID -> {
                        if (account.loanRecoveryRate != 1.0) {
                            throw new IOException("Repaid loan has reduced recovery for " + id);
                        }
                    }
                    case PARTIAL_DEFAULT -> {
                        if (account.loanRecoveryRate <= 0.0
                                || account.loanRecoveryRate >= 1.0) {
                            throw new IOException("Partial default recovery is invalid for " + id);
                        }
                    }
                    case FULL_DEFAULT -> {
                        if (account.loanRecoveryRate != 0.0 || account.loanValueMicro != 0L) {
                            throw new IOException("Full default retained value for " + id);
                        }
                    }
                }
            }
        } else if (account.loanValueMicro != 0L
                || account.loanOpenDay != 0L
                || account.loanMaturityDay != 0L
                || account.loanAnnualRate != 0.0
                || account.loanStress != 0.0
                || account.loanRecoveryRate != 1.0
                || account.loanResolved
                || account.loanOutcome != EconomyEngine.LoanOutcome.REPAID) {
            throw new IOException("Inactive villager loan contains residual state for " + id);
        }

        for (Map.Entry<String, Double> holding : account.shares.entrySet()) {
            if (!knownTicker(holding.getKey())
                    || !Double.isFinite(holding.getValue())
                    || holding.getValue() < 0.0) {
                throw new IOException("Invalid holding for " + id);
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
