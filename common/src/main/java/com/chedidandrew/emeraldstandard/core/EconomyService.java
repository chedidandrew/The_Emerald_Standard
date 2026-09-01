package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe application service shared by Fabric and NeoForge. */
public final class EconomyService {
    public static final long MILLIS_PER_MINECRAFT_DAY = 1_200_000L;
    public static final long TICKS_PER_MINECRAFT_DAY = 24_000L;
    public static final long MILLIS_PER_GAME_TICK = 50L;
    public static final long MAX_TRUSTED_CATCH_UP_DAYS = 25_000L;
    public static final long MAX_WHOLE_EMERALD_TRANSACTION = 1_000_000L;
    public static final int MAX_INVENTORY_ITEM_TRANSACTION =
            EconomyState.MAX_PENDING_INVENTORY_ITEMS;

    private static final long STARTUP_CATCH_UP_BATCH_DAYS = 2_000L;
    private static final long TICK_CATCH_UP_BATCH_DAYS = 250L;
    private static final long AUTO_SAVE_INTERVAL_MS = 30_000L;
    private static final long INITIAL_SAVE_RETRY_MS = 2_000L;
    private static final long MAX_SAVE_RETRY_MS = 60_000L;
    private static final long MAX_PENDING_ECONOMIC_MS =
            MAX_TRUSTED_CATCH_UP_DAYS * MILLIS_PER_MINECRAFT_DAY
                    + MILLIS_PER_MINECRAFT_DAY - 1L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EconomyState state;
    private Path path;
    private String lastError = "";
    private boolean dirty;
    private long nextAutomaticSaveMs;
    private long nextSaveRetryMs;
    private long saveRetryDelayMs = INITIAL_SAVE_RETRY_MS;

    public synchronized void start(Path worldDataDirectory, long worldSeed, long gameTicks)
            throws IOException {
        long now = System.currentTimeMillis();
        long newSeed = SECURE_RANDOM.nextLong()
                ^ Long.rotateLeft(worldSeed, 23)
                ^ Long.rotateLeft(now, 7);
        startInternal(worldDataDirectory, newSeed, now, gameTicks);
    }

    synchronized void startWithSeed(
            Path worldDataDirectory,
            long economySeed,
            long now,
            long gameTicks) throws IOException {
        startInternal(worldDataDirectory, economySeed, now, gameTicks);
    }

    private void startInternal(
            Path worldDataDirectory,
            long fallbackSeed,
            long now,
            long gameTicks) throws IOException {
        Objects.requireNonNull(worldDataDirectory, "worldDataDirectory");
        path = worldDataDirectory.resolve("the_emerald_standard.properties");
        try {
            state = EconomyState.load(path, fallbackSeed, now, gameTicks);
            observeProgress(now, gameTicks, STARTUP_CATCH_UP_BATCH_DAYS);
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
        } catch (IOException | RuntimeException exception) {
            lastError = message(exception);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not initialize the economy", exception);
        }
    }

    /**
     * Observes wall and game time every server tick, preserves partial days, advances bounded catch-up
     * batches, and periodically persists progress. Failed automatic saves use exponential backoff.
     */
    public synchronized boolean tick(long gameTicks) {
        return tickAt(gameTicks, System.currentTimeMillis());
    }

    synchronized boolean tickAt(long gameTicks, long now) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        try {
            dirty |= observeProgress(now, gameTicks, TICK_CATCH_UP_BATCH_DAYS);
            if (!dirty || now < nextAutomaticSaveMs || now < nextSaveRetryMs) {
                return true;
            }
            return persistDirtyState(now);
        } catch (RuntimeException exception) {
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    public synchronized boolean saveNow() {
        if (state == null) {
            lastError = "Economy service has not started";
            return false;
        }
        return saveNowAt(state.lastGameTicks, System.currentTimeMillis());
    }

    /** Saves final partial progress during server shutdown. */
    public synchronized boolean saveNow(long gameTicks) {
        return saveNowAt(gameTicks, System.currentTimeMillis());
    }

    synchronized boolean saveNowAt(long gameTicks, long now) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        try {
            dirty |= observeProgress(now, gameTicks, STARTUP_CATCH_UP_BATCH_DAYS);
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            dirty = true;
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    /** Full copy retained for tests and administrative diagnostics. */
    public synchronized EconomyState snapshot() {
        return state == null ? null : state.copy();
    }

    public synchronized MarketSnapshot marketSnapshot() {
        if (state == null) {
            return null;
        }
        return new MarketSnapshot(
                state.economicDay,
                state.regime,
                Map.copyOf(state.prices),
                Map.copyOf(state.commodityPrices),
                copyHistory(state.priceHistory),
                catchUpDaysRemainingInternal(),
                dirty);
    }

    public synchronized PortfolioSnapshot portfolioSnapshot(UUID id) {
        if (state == null) {
            return null;
        }
        EconomyState.Account account = state.existingAccount(id);
        EconomyState.PendingInventoryTransaction transaction =
                state.pendingInventoryTransactions.get(id);
        return new PortfolioSnapshot(
                state.economicDay,
                account == null ? new EconomyState.Account() : account.copy(),
                Map.copyOf(state.prices),
                state.netWorth(id),
                transaction == null ? null : transaction.copy(),
                catchUpDaysRemainingInternal());
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized long catchUpDaysRemaining() {
        return state == null ? 0L : catchUpDaysRemainingInternal();
    }

    public synchronized boolean isCatchingUp() {
        return catchUpDaysRemaining() > 0L;
    }

    public synchronized boolean hasGeneratedBankRegion(long regionKey) {
        return state != null && state.generatedBankRegions.contains(regionKey);
    }

    public synchronized boolean markGeneratedBankRegion(long regionKey) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            if (!state.generatedBankRegions.add(regionKey)) {
                return true;
            }
            state.save(path);
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    public synchronized String transactionBlockReason(UUID id) {
        if (state == null) {
            return "The economy is not ready";
        }
        long catchUpDays = catchUpDaysRemainingInternal();
        if (catchUpDays > 0L) {
            return "The economy is still processing " + catchUpDays + " catch-up days";
        }
        if (state.pendingInventoryTransactions.containsKey(id)) {
            return "A previous inventory transaction is waiting for recovery";
        }
        return "";
    }

    public synchronized boolean deposit(UUID id, long emeralds) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        return micro != null && creditMicro(id, micro);
    }

    public synchronized boolean creditMicro(UUID id, long microEmeralds) {
        if (microEmeralds <= 0L) {
            return false;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (!canAdd(account.cashMicro, microEmeralds)) {
                return false;
            }
            account.cashMicro += microEmeralds;
            return true;
        });
    }

    public synchronized long withdraw(UUID id, long emeralds) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return 0L;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (account.cashMicro < micro) {
                return false;
            }
            account.cashMicro -= micro;
            return true;
        }) ? emeralds : 0L;
    }

    public synchronized boolean moveSavings(UUID id, long emeralds, boolean intoSavings) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return false;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (intoSavings) {
                if (account.cashMicro < micro || !canAdd(account.savingsMicro, micro)) {
                    return false;
                }
                account.cashMicro -= micro;
                account.savingsMicro += micro;
            } else {
                if (account.savingsMicro < micro || !canAdd(account.cashMicro, micro)) {
                    return false;
                }
                account.savingsMicro -= micro;
                account.cashMicro += micro;
            }
            return true;
        });
    }

    public synchronized boolean openCd(UUID id, long emeralds, int termDays) {
        if (!supportedTerm(termDays)) {
            return false;
        }
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return false;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (account.cashMicro < micro || account.hasCd()) {
                return false;
            }
            account.cashMicro -= micro;
            account.cdPrincipalMicro = micro;
            account.cdValueMicro = micro;
            account.cdOpenDay = current.economicDay;
            account.cdMaturityDay = current.economicDay + termDays;
            account.cdAnnualRate = EconomyEngine.cdAnnualRate(current.regime, termDays);
            return true;
        });
    }

    public synchronized CdCloseResult closeCd(UUID id) {
        CdCloseResult[] result = {CdCloseResult.notClosed()};
        boolean success = mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (!account.hasCd()) {
                return false;
            }
            boolean matured = current.economicDay >= account.cdMaturityDay;
            long penalty = matured
                    ? 0L
                    : Math.max(1L, Math.round(account.cdPrincipalMicro * 0.01));
            long payout = matured
                    ? account.cdValueMicro
                    : Math.max(0L, account.cdPrincipalMicro - penalty);
            if (!canAdd(account.cashMicro, payout)) {
                return false;
            }
            account.cashMicro += payout;
            result[0] = new CdCloseResult(true, payout, penalty, matured);
            clearCd(account);
            return true;
        });
        return success ? result[0] : CdCloseResult.notClosed();
    }

    public synchronized boolean fundLoan(UUID id, long emeralds, int termDays) {
        if (!supportedTerm(termDays)) {
            return false;
        }
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return false;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (account.cashMicro < micro || account.hasLoan()) {
                return false;
            }
            account.cashMicro -= micro;
            account.loanPrincipalMicro = micro;
            account.loanValueMicro = micro;
            account.loanOpenDay = current.economicDay;
            account.loanMaturityDay = current.economicDay + termDays;
            account.loanSerial++;
            account.loanAnnualRate = EconomyEngine.villagerLoanAnnualYield(
                    current.regime, termDays);
            account.loanStress = 0.0;
            account.loanRecoveryRate = 1.0;
            account.loanResolved = false;
            account.loanOutcome = EconomyEngine.LoanOutcome.REPAID;
            return true;
        });
    }

    public synchronized LoanCollectionResult collectLoan(UUID id) {
        LoanCollectionResult[] result = {LoanCollectionResult.notCollected()};
        boolean success = mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (!account.hasLoan()
                    || current.economicDay < account.loanMaturityDay
                    || !account.loanResolved) {
                return false;
            }
            if (!canAdd(account.cashMicro, account.loanValueMicro)) {
                return false;
            }
            account.cashMicro += account.loanValueMicro;
            result[0] = new LoanCollectionResult(
                    true,
                    account.loanValueMicro,
                    account.loanPrincipalMicro,
                    account.loanRecoveryRate,
                    account.loanOutcome);
            clearLoan(account);
            return true;
        });
        return success ? result[0] : LoanCollectionResult.notCollected();
    }

    public synchronized boolean buy(UUID id, String ticker, long emeralds) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null || ticker == null) {
            return false;
        }
        String normalized = ticker.toUpperCase(Locale.ROOT);
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            Double marketPrice = current.prices.get(normalized);
            if (marketPrice == null || account.cashMicro < micro) {
                return false;
            }
            double executionPrice = marketPrice * (1.0 + EconomyEngine.TRADE_SPREAD);
            double purchasedShares = (micro / (double) EconomyState.MICRO) / executionPrice;
            if (!Double.isFinite(purchasedShares) || purchasedShares <= 0.0) {
                return false;
            }
            account.cashMicro -= micro;
            account.shares.merge(normalized, purchasedShares, Double::sum);
            return true;
        });
    }

    public synchronized boolean sell(UUID id, String ticker, double shares) {
        if (ticker == null || !Double.isFinite(shares) || shares <= 0.0) {
            return false;
        }
        String normalized = ticker.toUpperCase(Locale.ROOT);
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            double held = account.shares.getOrDefault(normalized, 0.0);
            Double marketPrice = current.prices.get(normalized);
            if (marketPrice == null || held + 1.0e-9 < shares) {
                return false;
            }
            long proceeds = emeraldsToMicro(
                    shares * marketPrice * (1.0 - EconomyEngine.TRADE_SPREAD));
            if (proceeds < 0L || !canAdd(account.cashMicro, proceeds)) {
                return false;
            }
            double remaining = held - shares;
            if (remaining <= 1.0e-9) {
                account.shares.remove(normalized);
            } else {
                account.shares.put(normalized, remaining);
            }
            account.cashMicro += proceeds;
            return true;
        });
    }

    public synchronized long quoteResourceValueMicro(String resourceId, int count) {
        return state == null
                ? -1L
                : EconomyEngine.resourceExchangeValueMicro(
                        resourceId, count, state.commodityPrices);
    }

    /** Creates a durable PREPARED journal record before items leave the inventory. */
    public synchronized EconomyState.PendingInventoryTransaction prepareInventoryCredit(
            UUID playerId,
            EconomyState.InventoryTransactionKind kind,
            String itemKey,
            int itemCount,
            int inventoryCountBefore,
            long creditMicro) {
        if (kind == null
                || kind == EconomyState.InventoryTransactionKind.WITHDRAWAL
                || itemKey == null
                || itemKey.isBlank()
                || itemCount <= 0
                || itemCount > MAX_INVENTORY_ITEM_TRANSACTION
                || inventoryCountBefore < itemCount
                || creditMicro <= 0L) {
            return null;
        }
        UUID transactionId = UUID.randomUUID();
        boolean success = mutatePlayer(playerId, true, current -> {
            if (current.pendingInventoryTransactions.containsKey(playerId)) {
                return false;
            }
            EconomyState.Account account = current.account(playerId);
            if (!canAdd(account.cashMicro, creditMicro)) {
                return false;
            }
            EconomyState.PendingInventoryTransaction transaction =
                    new EconomyState.PendingInventoryTransaction();
            transaction.transactionId = transactionId;
            transaction.playerId = playerId;
            transaction.kind = kind;
            transaction.stage = EconomyState.InventoryTransactionStage.PREPARED;
            transaction.itemKey = itemKey;
            transaction.itemCount = itemCount;
            transaction.inventoryCountBefore = inventoryCountBefore;
            transaction.bankDeltaMicro = creditMicro;
            transaction.createdEconomicDay = current.economicDay;
            transaction.createdWallClockMs = current.lastWallClockMs;
            current.pendingInventoryTransactions.put(playerId, transaction);
            return true;
        });
        return success ? pendingInventoryTransaction(playerId) : null;
    }

    /** Applies a prepared bank credit after the corresponding items have left the inventory. */
    public synchronized boolean commitPreparedInventoryCredit(
            UUID playerId,
            UUID transactionId) {
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.stage != EconomyState.InventoryTransactionStage.PREPARED
                    || !transaction.creditsBank()) {
                return false;
            }
            EconomyState.Account account = current.account(playerId);
            if (!canAdd(account.cashMicro, transaction.bankDeltaMicro)) {
                return false;
            }
            account.cashMicro += transaction.bankDeltaMicro;
            transaction.stage = EconomyState.InventoryTransactionStage.BANK_COMMITTED;
            return true;
        });
    }

    /** Debits bank cash and records a committed withdrawal before items enter the inventory. */
    public synchronized EconomyState.PendingInventoryTransaction beginInventoryWithdrawal(
            UUID playerId,
            int itemCount,
            int inventoryCountBefore) {
        Long debitMicro = wholeEmeraldsToMicro(itemCount);
        if (debitMicro == null
                || itemCount > MAX_INVENTORY_ITEM_TRANSACTION
                || inventoryCountBefore < 0) {
            return null;
        }
        UUID transactionId = UUID.randomUUID();
        boolean success = mutatePlayer(playerId, true, current -> {
            if (current.pendingInventoryTransactions.containsKey(playerId)) {
                return false;
            }
            EconomyState.Account account = current.account(playerId);
            if (account.cashMicro < debitMicro) {
                return false;
            }
            account.cashMicro -= debitMicro;
            EconomyState.PendingInventoryTransaction transaction =
                    new EconomyState.PendingInventoryTransaction();
            transaction.transactionId = transactionId;
            transaction.playerId = playerId;
            transaction.kind = EconomyState.InventoryTransactionKind.WITHDRAWAL;
            transaction.stage = EconomyState.InventoryTransactionStage.BANK_COMMITTED;
            transaction.itemKey = "emerald";
            transaction.itemCount = itemCount;
            transaction.inventoryCountBefore = inventoryCountBefore;
            transaction.bankDeltaMicro = -debitMicro;
            transaction.createdEconomicDay = current.economicDay;
            transaction.createdWallClockMs = current.lastWallClockMs;
            current.pendingInventoryTransactions.put(playerId, transaction);
            return true;
        });
        return success ? pendingInventoryTransaction(playerId) : null;
    }

    /** Returns an undelivered portion of a committed withdrawal to bank cash. */
    public synchronized boolean reducePendingWithdrawal(
            UUID playerId,
            UUID transactionId,
            int undeliveredCount) {
        if (undeliveredCount <= 0) {
            return true;
        }
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.kind != EconomyState.InventoryTransactionKind.WITHDRAWAL
                    || transaction.stage != EconomyState.InventoryTransactionStage.BANK_COMMITTED
                    || undeliveredCount > transaction.itemCount) {
                return false;
            }
            long refundMicro = undeliveredCount * EconomyState.MICRO;
            EconomyState.Account account = current.account(playerId);
            if (!canAdd(account.cashMicro, refundMicro)) {
                return false;
            }
            account.cashMicro += refundMicro;
            transaction.itemCount -= undeliveredCount;
            if (transaction.itemCount == 0) {
                current.pendingInventoryTransactions.remove(playerId);
            } else {
                transaction.bankDeltaMicro = -transaction.itemCount * EconomyState.MICRO;
            }
            return true;
        });
    }

    public synchronized boolean completeInventoryTransaction(
            UUID playerId,
            UUID transactionId) {
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.stage != EconomyState.InventoryTransactionStage.BANK_COMMITTED) {
                return false;
            }
            current.pendingInventoryTransactions.remove(playerId);
            return true;
        });
    }

    public synchronized boolean cancelPreparedInventoryTransaction(
            UUID playerId,
            UUID transactionId) {
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.stage != EconomyState.InventoryTransactionStage.PREPARED) {
                return false;
            }
            current.pendingInventoryTransactions.remove(playerId);
            return true;
        });
    }

    public synchronized EconomyState.PendingInventoryTransaction pendingInventoryTransaction(
            UUID playerId) {
        if (state == null) {
            return null;
        }
        EconomyState.PendingInventoryTransaction transaction =
                state.pendingInventoryTransactions.get(playerId);
        return transaction == null ? null : transaction.copy();
    }

    private boolean observeProgress(long now, long gameTicks, long maximumDaysToAdvance) {
        long safeNow = Math.max(0L, now);
        long safeGameTicks = Math.max(0L, gameTicks);
        long trustedNow = Math.max(safeNow, state.lastWallClockMs);
        long wallDeltaMs = trustedNow - state.lastWallClockMs;
        long gameDeltaTicks = safeGameTicks >= state.lastGameTicks
                ? safeGameTicks - state.lastGameTicks
                : 0L;
        long gameDeltaMs = gameDeltaTicks > Long.MAX_VALUE / MILLIS_PER_GAME_TICK
                ? Long.MAX_VALUE
                : gameDeltaTicks * MILLIS_PER_GAME_TICK;
        boolean changed = wallDeltaMs > 0L || safeGameTicks != state.lastGameTicks;

        state.lastWallClockMs = trustedNow;
        state.lastGameTicks = safeGameTicks;
        long elapsedEconomicMs = Math.max(wallDeltaMs, gameDeltaMs);
        state.pendingEconomicMillis = cappedAdd(
                state.pendingEconomicMillis,
                elapsedEconomicMs,
                MAX_PENDING_ECONOMIC_MS);

        long availableDays = state.pendingEconomicMillis / MILLIS_PER_MINECRAFT_DAY;
        long days = Math.min(maximumDaysToAdvance, availableDays);
        if (days <= 0L) {
            return changed;
        }

        advance(days);
        state.pendingEconomicMillis -= days * MILLIS_PER_MINECRAFT_DAY;
        return true;
    }

    private void advance(long days) {
        for (long day = 0L; day < days; day++) {
            state.advanceOneDay();
        }
    }

    private long catchUpDaysRemainingInternal() {
        return state.pendingEconomicMillis / MILLIS_PER_MINECRAFT_DAY;
    }

    private boolean persistDirtyState(long now) {
        try {
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            dirty = true;
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    private boolean mutatePlayer(UUID playerId, boolean allowPending, Mutation mutation) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        Objects.requireNonNull(playerId, "playerId");

        long now = state.lastWallClockMs;
        if (catchUpDaysRemainingInternal() > 0L) {
            lastError = "Economy catch-up is still in progress";
            return false;
        }
        if (!allowPending && state.pendingInventoryTransactions.containsKey(playerId)) {
            lastError = "A pending inventory transaction must be recovered first";
            return false;
        }

        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            if (!mutation.apply(state)) {
                state = before;
                dirty = dirtyBefore;
                lastError = "";
                return false;
            }
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    private static Map<String, java.util.List<Double>> copyHistory(
            Map<String, java.util.List<Double>> history) {
        Map<String, java.util.List<Double>> copy = new java.util.LinkedHashMap<>();
        history.forEach((ticker, values) -> copy.put(ticker, java.util.List.copyOf(values)));
        return Map.copyOf(copy);
    }

    private static EconomyState.PendingInventoryTransaction matchingTransaction(
            EconomyState current,
            UUID playerId,
            UUID transactionId) {
        EconomyState.PendingInventoryTransaction transaction =
                current.pendingInventoryTransactions.get(playerId);
        return transaction != null && transaction.transactionId.equals(transactionId)
                ? transaction
                : null;
    }

    private void resetSaveSchedule(long now) {
        nextAutomaticSaveMs = saturatingAdd(now, AUTO_SAVE_INTERVAL_MS);
        nextSaveRetryMs = 0L;
        saveRetryDelayMs = INITIAL_SAVE_RETRY_MS;
    }

    private void scheduleSaveRetry(long now) {
        nextSaveRetryMs = saturatingAdd(now, saveRetryDelayMs);
        nextAutomaticSaveMs = nextSaveRetryMs;
        saveRetryDelayMs = Math.min(MAX_SAVE_RETRY_MS, saveRetryDelayMs * 2L);
    }

    private static long cappedAdd(long current, long addition, long cap) {
        if (addition <= 0L || current >= cap) {
            return Math.min(current, cap);
        }
        return addition > cap - current ? cap : current + addition;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE
                : left + right;
    }

    private static void clearCd(EconomyState.Account account) {
        account.cdPrincipalMicro = 0L;
        account.cdValueMicro = 0L;
        account.cdOpenDay = 0L;
        account.cdMaturityDay = 0L;
        account.cdAnnualRate = 0.0;
    }

    private static void clearLoan(EconomyState.Account account) {
        account.loanPrincipalMicro = 0L;
        account.loanValueMicro = 0L;
        account.loanOpenDay = 0L;
        account.loanMaturityDay = 0L;
        account.loanAnnualRate = 0.0;
        account.loanStress = 0.0;
        account.loanRecoveryRate = 1.0;
        account.loanResolved = false;
        account.loanOutcome = EconomyEngine.LoanOutcome.REPAID;
    }

    private static boolean supportedTerm(int termDays) {
        return termDays == 30 || termDays == 90 || termDays == 180 || termDays == 365;
    }

    private static Long wholeEmeraldsToMicro(long emeralds) {
        return emeralds <= 0L
                        || emeralds > MAX_WHOLE_EMERALD_TRANSACTION
                        || emeralds > Long.MAX_VALUE / EconomyState.MICRO
                ? null
                : emeralds * EconomyState.MICRO;
    }

    private static long emeraldsToMicro(double emeralds) {
        double micro = emeralds * EconomyState.MICRO;
        return !Double.isFinite(micro) || micro < 0.0 || micro > Long.MAX_VALUE
                ? -1L
                : Math.round(micro);
    }

    private static boolean canAdd(long current, long amount) {
        return amount >= 0L && current <= Long.MAX_VALUE - amount;
    }

    private static String message(Exception exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank()
                ? exception.getClass().getSimpleName()
                : value;
    }

    @FunctionalInterface
    private interface Mutation {
        boolean apply(EconomyState state) throws IOException;
    }

    public record MarketSnapshot(
            long economicDay,
            EconomyEngine.Regime regime,
            Map<String, Double> prices,
            Map<String, Double> commodityPrices,
            Map<String, java.util.List<Double>> priceHistory,
            long catchUpDaysRemaining,
            boolean dirty) {
    }

    public record PortfolioSnapshot(
            long economicDay,
            EconomyState.Account account,
            Map<String, Double> prices,
            double netWorth,
            EconomyState.PendingInventoryTransaction pendingTransaction,
            long catchUpDaysRemaining) {
    }

    public record CdCloseResult(
            boolean closed,
            long payoutMicro,
            long penaltyMicro,
            boolean matured) {
        static CdCloseResult notClosed() {
            return new CdCloseResult(false, 0L, 0L, false);
        }
    }

    public record LoanCollectionResult(
            boolean collected,
            long payoutMicro,
            long principalMicro,
            double recoveryRate,
            EconomyEngine.LoanOutcome outcome) {
        static LoanCollectionResult notCollected() {
            return new LoanCollectionResult(
                    false, 0L, 0L, 0.0, EconomyEngine.LoanOutcome.REPAID);
        }
    }
}
