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
    public static final int FORMAT_VERSION = 4;
    public static final int HISTORY_DAYS = 180;
    public static final long MICRO = 1_000_000L;
    public static final int MAX_PENDING_INVENTORY_ITEMS = 100_000;

    public long seed;
    public long economicDay;
    public long lastWallClockMs;
    public long lastGameTicks;
    public long pendingEconomicMillis;
    public EconomyEngine.Regime regime;

    public final Map<String, Double> prices = new LinkedHashMap<>();
    public final Map<String, Double> commodityPrices = new LinkedHashMap<>();
    public final Map<String, List<Double>> priceHistory = new LinkedHashMap<>();
    public final Set<Long> generatedBankRegions = new HashSet<>();
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
        copy.prices.putAll(prices);
        copy.commodityPrices.putAll(commodityPrices);
        priceHistory.forEach((ticker, values) ->
                copy.priceHistory.put(ticker, new ArrayList<>(values)));
        copy.generatedBankRegions.addAll(generatedBankRegions);
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
        economicDay++;
        regime = EconomyEngine.nextRegime(regime, seed, economicDay);
        double marketReturn = EconomyEngine.marketReturn(regime, seed, economicDay);

        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            double current = prices.getOrDefault(asset.ticker(), 100.0);
            double next = current * (1.0 + EconomyEngine.assetReturn(
                    asset, marketReturn, seed, economicDay));
            prices.put(asset.ticker(), boundedPrice(next));
        }
        normalizeHighPrices();
        recordCurrentPrices();

        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            double current = commodityPrices.getOrDefault(commodity.id(), commodity.anchorPrice());
            commodityPrices.put(commodity.id(), EconomyEngine.nextCommodityPrice(
                    commodity, current, regime, seed, economicDay));
        }

        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            advanceAccount(entry.getKey(), entry.getValue());
        }
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
        double value = (account.cashMicro
                + account.savingsMicro
                + account.cdValueMicro
                + account.loanValueMicro) / (double) MICRO;
        for (Map.Entry<String, Double> holding : account.shares.entrySet()) {
            value += holding.getValue() * prices.getOrDefault(holding.getKey(), 0.0);
        }
        return value;
    }

    public void validate() throws IOException {
        if (regime == null
                || economicDay < 0L
                || lastWallClockMs < 0L
                || lastGameTicks < 0L
                || pendingEconomicMillis < 0L) {
            throw new IOException("Economy clock or regime is invalid");
        }
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            validatePrice("asset " + asset.ticker(), prices.get(asset.ticker()));
        }
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            validatePrice("commodity " + commodity.id(), commodityPrices.get(commodity.id()));
        }
        validateHistory();
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
