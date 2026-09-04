package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable portfolio accounting derived from server-authoritative executions and balances. */
public final class PortfolioAnalytics {
    private static final double SHARE_EPSILON = 1.0e-9;

    private PortfolioAnalytics() {
    }

    /** Initializes basis for holdings created by an older save without pretending it is exact. */
    static void migrateLegacyBasis(EconomyState.Account account, EconomyState state) {
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            String ticker = asset.ticker();
            double shares = account.shares.getOrDefault(ticker, 0.0);
            if (shares <= SHARE_EPSILON || account.shareCostBasisMicro.containsKey(ticker)) {
                continue;
            }
            account.shareCostBasisMicro.put(
                    ticker,
                    multiplyMicro(priceMicro(state.prices.getOrDefault(ticker, 0.0)), shares));
            account.costBasisInferred = true;
        }
    }

    static void recordTransaction(
            EconomyState.Account account,
            long day,
            EconomyState.PortfolioTransactionKind kind,
            String symbol,
            long referenceId,
            double quantity,
            long amountMicro,
            long costBasisMicro,
            long realizedGainMicro) {
        if (kind == EconomyState.PortfolioTransactionKind.INTEREST) {
            recordCoalescedInterest(
                    account,
                    day,
                    symbol,
                    referenceId,
                    quantity,
                    amountMicro,
                    costBasisMicro,
                    realizedGainMicro);
            return;
        }
        EconomyState.PortfolioTransaction entry = new EconomyState.PortfolioTransaction();
        entry.day = Math.max(0L, day);
        entry.kind = kind;
        entry.symbol = symbol == null ? "" : symbol;
        entry.referenceId = Math.max(0L, referenceId);
        entry.quantity = Math.max(0.0, quantity);
        entry.amountMicro = Math.max(0L, amountMicro);
        entry.costBasisMicro = Math.max(0L, costBasisMicro);
        entry.realizedGainMicro = realizedGainMicro;
        account.transactionLedger.add(entry);
        trim(account.transactionLedger, EconomyState.MAX_PORTFOLIO_LEDGER_ENTRIES);
    }

    private static void recordCoalescedInterest(
            EconomyState.Account account,
            long day,
            String symbol,
            long referenceId,
            double quantity,
            long amountMicro,
            long costBasisMicro,
            long realizedGainMicro) {
        String safeSymbol = symbol == null ? "" : symbol;
        long safeReference = Math.max(0L, referenceId);
        EconomyState.PortfolioTransaction aggregate = new EconomyState.PortfolioTransaction();
        aggregate.day = Math.max(0L, day);
        aggregate.kind = EconomyState.PortfolioTransactionKind.INTEREST;
        aggregate.symbol = safeSymbol;
        aggregate.referenceId = safeReference;
        aggregate.quantity = normalizedInterestCount(quantity);
        aggregate.amountMicro = Math.max(0L, amountMicro);
        aggregate.costBasisMicro = Math.max(0L, costBasisMicro);
        aggregate.realizedGainMicro = realizedGainMicro;

        for (int index = account.transactionLedger.size() - 1; index >= 0; index--) {
            EconomyState.PortfolioTransaction existing = account.transactionLedger.get(index);
            if (existing.kind != EconomyState.PortfolioTransactionKind.INTEREST
                    || !safeSymbol.equals(existing.symbol)
                    || safeReference != existing.referenceId) {
                continue;
            }
            aggregate.day = Math.max(aggregate.day, existing.day);
            aggregate.quantity += normalizedInterestCount(existing.quantity);
            aggregate.amountMicro = add(aggregate.amountMicro, existing.amountMicro);
            aggregate.costBasisMicro = add(
                    aggregate.costBasisMicro, existing.costBasisMicro);
            aggregate.realizedGainMicro = add(
                    aggregate.realizedGainMicro, existing.realizedGainMicro);
            account.transactionLedger.remove(index);
        }
        account.transactionLedger.add(aggregate);
        trim(account.transactionLedger, EconomyState.MAX_PORTFOLIO_LEDGER_ENTRIES);
    }

    private static double normalizedInterestCount(double quantity) {
        return Double.isFinite(quantity) && quantity > 0.0 ? quantity : 1.0;
    }

    static void recordNetWorth(
            EconomyState.Account account, EconomyState state, long economicDay) {
        long value = netWorthMicro(account, state);
        if (!account.netWorthHistory.isEmpty()) {
            EconomyState.PortfolioValuePoint latest =
                    account.netWorthHistory.get(account.netWorthHistory.size() - 1);
            if (latest.day == economicDay) {
                latest.valueMicro = value;
                return;
            }
        }
        EconomyState.PortfolioValuePoint point = new EconomyState.PortfolioValuePoint();
        point.day = Math.max(0L, economicDay);
        point.valueMicro = value;
        account.netWorthHistory.add(point);
        trim(account.netWorthHistory, EconomyState.HISTORY_DAYS);
    }

    public static PortfolioSnapshot snapshot(
            EconomyState.Account account, EconomyState state) {
        if (account == null || state == null) {
            return PortfolioSnapshot.empty();
        }
        EconomyState.ensurePositionCollections(account);
        migrateLegacyBasis(account, state);

        Map<String, PositionSnapshot> positions = new LinkedHashMap<>();
        Map<String, Long> allocationValues = new LinkedHashMap<>();
        long marketValue = 0L;
        long marketBasis = 0L;
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            String ticker = asset.ticker();
            double shares = Math.max(0.0, account.shares.getOrDefault(ticker, 0.0));
            long value = multiplyMicro(
                    priceMicro(state.prices.getOrDefault(ticker, 0.0)), shares);
            long basis = Math.max(0L, account.shareCostBasisMicro.getOrDefault(ticker, 0L));
            long unrealized = subtract(value, basis);
            double averagePrice = shares <= SHARE_EPSILON
                    ? 0.0
                    : basis / (double) EconomyState.MICRO / shares;
            marketValue = add(marketValue, value);
            marketBasis = add(marketBasis, basis);
            allocationValues.put(ticker, value);
            positions.put(ticker, new PositionSnapshot(
                    ticker, shares, value, basis, averagePrice, unrealized, 0.0));
        }

        long cdBasis = 0L;
        long cdGain = 0L;
        for (EconomyState.CdPosition cd : account.cdPositions.values()) {
            cdBasis = add(cdBasis, cd.principalMicro);
            cdGain = add(cdGain, subtract(cd.valueMicro, cd.principalMicro));
        }
        long loanBasis = 0L;
        long loanGain = 0L;
        for (EconomyState.LoanPosition loan : account.loanPositions.values()) {
            loanBasis = add(loanBasis, loan.principalMicro);
            loanGain = add(loanGain, subtract(loan.valueMicro, loan.principalMicro));
        }
        long totalBasis = add(marketBasis, add(cdBasis, loanBasis));
        long unrealizedGain = add(subtract(marketValue, marketBasis), add(cdGain, loanGain));
        long netWorth = netWorthMicro(account, state);
        allocationValues.put("CASH", account.cashMicro);
        allocationValues.put("SAVINGS", account.savingsMicro);
        allocationValues.put("CD", account.totalCdValueMicro());
        allocationValues.put("LENDING", account.totalLoanValueMicro());
        Map<String, Double> allocations = new LinkedHashMap<>();
        allocationValues.forEach((key, value) -> allocations.put(
                key, netWorth <= 0L ? 0.0 : value / (double) netWorth));

        Map<String, PositionSnapshot> weighted = new LinkedHashMap<>();
        positions.forEach((ticker, position) -> weighted.put(ticker, new PositionSnapshot(
                position.ticker,
                position.shares,
                position.marketValueMicro,
                position.costBasisMicro,
                position.averagePurchasePrice,
                position.unrealizedGainMicro,
                netWorth <= 0L ? 0.0 : position.marketValueMicro / (double) netWorth)));

        return new PortfolioSnapshot(
                netWorth,
                marketValue,
                totalBasis,
                unrealizedGain,
                account.realizedGainMicro,
                account.totalContributionsMicro,
                account.totalWithdrawalsMicro,
                Map.copyOf(weighted),
                Map.copyOf(allocations),
                List.copyOf(account.cdPositions.values().stream()
                        .map(EconomyState.CdPosition::copy).toList()),
                List.copyOf(account.loanPositions.values().stream()
                        .map(EconomyState.LoanPosition::copy).toList()),
                List.copyOf(account.transactionLedger.stream()
                        .map(EconomyState.PortfolioTransaction::copy).toList()),
                List.copyOf(account.netWorthHistory.stream()
                        .map(EconomyState.PortfolioValuePoint::copy).toList()),
                account.costBasisInferred);
    }

    static long netWorthMicro(EconomyState.Account account, EconomyState state) {
        EconomyState.ensurePositionCollections(account);
        long total = add(account.cashMicro, account.savingsMicro);
        total = add(total, account.totalCdValueMicro());
        total = add(total, account.totalLoanValueMicro());
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            total = add(total, multiplyMicro(
                    priceMicro(state.prices.getOrDefault(asset.ticker(), 0.0)),
                    account.shares.getOrDefault(asset.ticker(), 0.0)));
        }
        return Math.max(0L, total);
    }

    static long priceMicro(double price) {
        if (!Double.isFinite(price) || price <= 0.0) {
            return 0L;
        }
        double value = price * EconomyState.MICRO;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }

    static long multiplyMicro(long unitMicro, double quantity) {
        if (unitMicro <= 0L || !Double.isFinite(quantity) || quantity <= 0.0) {
            return 0L;
        }
        double value = unitMicro * quantity;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, Math.round(value));
    }

    static long add(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        if (second < 0L && first < Long.MIN_VALUE - second) return Long.MIN_VALUE;
        return first + second;
    }

    static long subtract(long first, long second) {
        return second == Long.MIN_VALUE ? Long.MAX_VALUE : add(first, -second);
    }

    private static <T> void trim(List<T> values, int limit) {
        int excess = values.size() - limit;
        if (excess > 0) {
            values.subList(0, excess).clear();
        }
    }

    public record PositionSnapshot(
            String ticker,
            double shares,
            long marketValueMicro,
            long costBasisMicro,
            double averagePurchasePrice,
            long unrealizedGainMicro,
            double portfolioWeight) {
    }

    public record PortfolioSnapshot(
            long netWorthMicro,
            long marketValueMicro,
            long totalCostBasisMicro,
            long unrealizedGainMicro,
            long realizedGainMicro,
            long totalContributionsMicro,
            long totalWithdrawalsMicro,
            Map<String, PositionSnapshot> positions,
            Map<String, Double> allocations,
            List<EconomyState.CdPosition> cds,
            List<EconomyState.LoanPosition> loans,
            List<EconomyState.PortfolioTransaction> transactions,
            List<EconomyState.PortfolioValuePoint> netWorthHistory,
            boolean inferredCostBasis) {
        static PortfolioSnapshot empty() {
            return new PortfolioSnapshot(
                    0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), false);
        }
    }
}
