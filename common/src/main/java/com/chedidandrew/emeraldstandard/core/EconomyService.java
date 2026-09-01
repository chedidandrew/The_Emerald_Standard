package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe application service shared by Fabric and NeoForge. */
public final class EconomyService {
    public static final long MILLIS_PER_MINECRAFT_DAY = 1_200_000L;
    private static final long MAX_CATCH_UP_DAYS = 1_000_000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EconomyState state;
    private Path path;
    private String lastError = "";

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
            catchUpInMemory(now, gameTicks);
            state.save(path);
            lastError = "";
        } catch (IOException exception) {
            lastError = message(exception);
            throw exception;
        }
    }

    public synchronized boolean tick(long gameTicks) {
        if (state == null) {
            lastError = "Economy service has not started";
            return false;
        }
        long elapsedDays = Math.max(0L, (gameTicks - state.lastGameTicks) / 24_000L);
        if (elapsedDays == 0L) {
            return true;
        }

        EconomyState before = state.copy();
        try {
            advance(elapsedDays);
            state.lastGameTicks += elapsedDays * 24_000L;
            state.lastWallClockMs = laterWallClock(System.currentTimeMillis());
            state.save(path);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            lastError = message(exception);
            return false;
        }
    }

    public synchronized boolean saveNow() {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        EconomyState before = state.copy();
        try {
            state.lastWallClockMs = laterWallClock(System.currentTimeMillis());
            state.save(path);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            lastError = message(exception);
            return false;
        }
    }

    public synchronized EconomyState snapshot() {
        return state == null ? null : state.copy();
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized boolean deposit(UUID id, long emeralds) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        return micro != null && creditMicro(id, micro);
    }

    public synchronized boolean creditMicro(UUID id, long microEmeralds) {
        if (microEmeralds <= 0L) {
            return false;
        }
        return mutate(current -> {
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
        return mutate(current -> {
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
        return mutate(current -> {
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
        return mutate(current -> {
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
        boolean success = mutate(current -> {
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
        return mutate(current -> {
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
        boolean success = mutate(current -> {
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
        return mutate(current -> {
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
        return mutate(current -> {
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

    private void catchUpInMemory(long now, long gameTicks) {
        long trustedNow = Math.max(now, state.lastWallClockMs);
        long offlineDays = Math.max(
                0L, (trustedNow - state.lastWallClockMs) / MILLIS_PER_MINECRAFT_DAY);
        long gameDays = Math.max(0L, (gameTicks - state.lastGameTicks) / 24_000L);
        advance(Math.min(MAX_CATCH_UP_DAYS, Math.max(offlineDays, gameDays)));
        state.lastWallClockMs = trustedNow;
        state.lastGameTicks = gameTicks;
    }

    private void advance(long days) {
        for (long day = 0L; day < days; day++) {
            state.advanceOneDay();
        }
    }

    private boolean mutate(Mutation mutation) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        EconomyState before = state.copy();
        try {
            if (!mutation.apply(state)) {
                state = before;
                lastError = "";
                return false;
            }
            state.lastWallClockMs = laterWallClock(System.currentTimeMillis());
            state.save(path);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            lastError = message(exception);
            return false;
        }
    }

    private long laterWallClock(long now) {
        return Math.max(now, state.lastWallClockMs);
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
        return emeralds <= 0L || emeralds > Long.MAX_VALUE / EconomyState.MICRO
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
