package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.SpendingFunds;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Shared, server-authoritative banking actions used by the graphical Banker menu. */
public final class BankingOperations {
    private static final Map<UUID, Long> LAST_ACTION_TICK = new HashMap<>();
    static final int READY = 0;
    static final int DEPOSITED = 1;
    static final int WITHDREW = 2;
    static final int SAVED = 3;
    static final int UNSAVED = 4;
    static final int BOUGHT = 5;
    static final int SOLD = 6;
    static final int CD_OPENED = 7;
    static final int CD_CLOSED = 8;
    static final int LENDING_FUNDED = 9;
    static final int LENDING_COLLECTED = 10;
    static final int EXCHANGED = 11;
    static final int RECOVERED = 12;
    static final int RECOVERY_PENDING = 13;
    static final int VILLAGE_FUNDED = 14;
    static final int VILLAGE_RESTORATION_READY = 15;
    static final int CONFIRM_REQUIRED = 16;
    static final int VILLAGE_ENDOWED = 17;
    static final int VILLAGE_PROJECT_SPONSORED = 18;

    static final int BUSY = -1;
    static final int INSUFFICIENT = -2;
    static final int INVENTORY_FULL = -3;
    static final int PRODUCT_ACTIVE = -4;
    static final int NOT_READY = -5;
    static final int PERSISTENCE_FAILED = -6;
    static final int UNSUPPORTED = -7;
    static final int NO_VILLAGE = -8;
    static final int POSITION_LIMIT = -9;
    static final int NUMERIC_LIMIT = -10;

    private BankingOperations() {
    }

    /** Clears short-lived per-player cooldown state when a player leaves the server. */
    public static void forgetPlayer(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    static int recover(ServerPlayer player, EconomyService economy) {
        BankTransactionCoordinator.RecoveryResult result =
                BankTransactionCoordinator.reconcile(player, economy);
        if (!result.found()) {
            return READY;
        }
        return result.recovered() ? RECOVERED : PERSISTENCE_FAILED;
    }

    static int deposit(ServerPlayer player, EconomyService economy, int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        int inventoryBefore = BankInventory.countItems(player, Items.EMERALD);
        int amount = cappedInventoryAmount(requested, inventoryBefore);
        if (amount <= 0) {
            return INSUFFICIENT;
        }

        return BankTransactionCoordinator.creditInventory(player, economy,
                EconomyState.InventoryTransactionKind.DEPOSIT, "emerald", amount,
                amount * EconomyState.MICRO) ? DEPOSITED : PERSISTENCE_FAILED;
    }

    static int withdraw(ServerPlayer player, EconomyService economy, int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        long available = economy.portfolioSnapshot(player.getUUID()).account().cashMicro
                / EconomyState.MICRO;
        int amount = cappedInventoryAmount(requested, available);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        int delivered = BankTransactionCoordinator.withdrawInventory(player, economy, amount);
        return delivered < 0 ? PERSISTENCE_FAILED : delivered == 0 ? INVENTORY_FULL : WITHDREW;
    }

    static int moveSavings(
            ServerPlayer player,
            EconomyService economy,
            int requested,
            boolean intoSavings) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        long availableMicro = intoSavings ? spendingMicro(player, account.cashMicro) : account.savingsMicro;
        int amount = cappedFinancialAmount(requested, availableMicro / EconomyState.MICRO);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        if (intoSavings) return spend(player, economy, amount,
                () -> economy.moveSavings(player.getUUID(), amount, true) ? SAVED : PERSISTENCE_FAILED);
        return economy.moveSavings(player.getUUID(), amount, false) ? UNSAVED : PERSISTENCE_FAILED;
    }

    static int buy(
            ServerPlayer player,
            EconomyService economy,
            String ticker,
            int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        long cash = spendingMicro(player, economy.portfolioSnapshot(player.getUUID()).account().cashMicro)
                / EconomyState.MICRO;
        int amount = cappedFinancialAmount(requested, cash);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        if (ticker == null || com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS.stream()
                .noneMatch(asset -> asset.ticker().equalsIgnoreCase(ticker))) return UNSUPPORTED;
        String symbol = ticker.toUpperCase(java.util.Locale.ROOT);
        double price = economy.marketSnapshot().prices().getOrDefault(symbol, 0.0)
                * (1.0 + com.chedidandrew.emeraldstandard.core.EconomyEngine.TRADE_SPREAD);
        double held = economy.portfolioSnapshot(player.getUUID()).account().shares.getOrDefault(symbol, 0.0);
        if (!Double.isFinite(com.chedidandrew.emeraldstandard.core.InvestmentTradeMath.holdingAfter(
                held, amount / price, price, true))) return NUMERIC_LIMIT;
        return spend(player, economy, amount,
                () -> economy.buy(player.getUUID(), ticker, amount) ? BOUGHT : PERSISTENCE_FAILED);
    }

    static int sellFraction(
            ServerPlayer player,
            EconomyService economy,
            String ticker,
            double fraction) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        double held = economy.portfolioSnapshot(player.getUUID()).account().shares
                .getOrDefault(ticker, 0.0);
        if (!Double.isFinite(held) || held <= 0.0) {
            return INSUFFICIENT;
        }
        double shares = fraction >= 0.999999 ? held : held * fraction;
        double price = economy.marketSnapshot().prices().getOrDefault(ticker, 0.0)
                * (1.0 - com.chedidandrew.emeraldstandard.core.EconomyEngine.TRADE_SPREAD);
        double next = com.chedidandrew.emeraldstandard.core.InvestmentTradeMath.holdingAfter(held, shares, price, false);
        double micro = (held - next) * price * EconomyState.MICRO;
        if (!Double.isFinite(next) || !Double.isFinite(micro) || micro >= Long.MAX_VALUE || Math.round(micro) <= 0)
            return NUMERIC_LIMIT;
        return economy.sell(player.getUUID(), ticker, shares) ? SOLD : PERSISTENCE_FAILED;
    }

    static int openCd(
            ServerPlayer player,
            EconomyService economy,
            int requested,
            int termDays) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        if (account.cdPositions.size() >= EconomyState.MAX_TERM_POSITIONS) {
            return POSITION_LIMIT;
        }
        if (termDays != 30 && termDays != 90 && termDays != 180 && termDays != 365) return UNSUPPORTED;
        int amount = cappedFinancialAmount(requested, spendingMicro(player, account.cashMicro) / EconomyState.MICRO);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        return spend(player, economy, amount,
                () -> economy.openCdPosition(player.getUUID(), amount, termDays) > 0L
                        ? CD_OPENED : PERSISTENCE_FAILED);
    }

    static int closeCd(ServerPlayer player, EconomyService economy) {
        return closeCd(player, economy, 0L);
    }

    static int closeCd(ServerPlayer player, EconomyService economy, long positionId) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyService.CdCloseResult result = positionId > 0L
                ? economy.closeCd(player.getUUID(), positionId)
                : economy.closeCd(player.getUUID());
        return result.closed() ? CD_CLOSED : NOT_READY;
    }

    static int fundLending(
            ServerPlayer player,
            EconomyService economy,
            int requested,
            int termDays) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        if (account.loanPositions.size() >= EconomyState.MAX_TERM_POSITIONS) {
            return POSITION_LIMIT;
        }
        if (termDays != 30 && termDays != 90 && termDays != 180 && termDays != 365) return UNSUPPORTED;
        int amount = cappedFinancialAmount(requested, spendingMicro(player, account.cashMicro) / EconomyState.MICRO);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        return spend(player, economy, amount,
                () -> economy.openLoanPosition(player.getUUID(), amount, termDays) > 0L
                        ? LENDING_FUNDED : PERSISTENCE_FAILED);
    }

    static int collectLending(ServerPlayer player, EconomyService economy) {
        return collectLending(player, economy, 0L);
    }

    static int collectLending(
            ServerPlayer player, EconomyService economy, long positionId) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        EconomyService.LoanCollectionResult result = positionId > 0L
                ? economy.collectLoan(player.getUUID(), positionId)
                : economy.collectLoan(player.getUUID());
        return result.collected() ? LENDING_COLLECTED : NOT_READY;
    }

    static int exchange(
            ServerPlayer player,
            EconomyService economy,
            BankInventory.ExchangeResource resource,
            int requested) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        if (resource == null) {
            return UNSUPPORTED;
        }
        int inventoryBefore = BankInventory.countItems(player, resource.item());
        int amount = cappedInventoryAmount(requested, inventoryBefore);
        if (amount <= 0) {
            return INSUFFICIENT;
        }
        long proceeds = economy.quoteResourceValueMicro(resource.quoteId(), amount);
        if (proceeds <= 0L) {
            return UNSUPPORTED;
        }

        return BankTransactionCoordinator.creditInventory(player, economy,
                EconomyState.InventoryTransactionKind.EXCHANGE, resource.journalKey(), amount, proceeds)
                ? EXCHANGED : PERSISTENCE_FAILED;
    }

    static int supportVillage(
            ServerPlayer player,
            EconomyService economy,
            java.util.UUID villageId,
            int requested,
            EconomyState.ProsperityFundType type,
            EconomyState.DonationPurpose purpose) {
        int readiness = prepare(player, economy);
        if (readiness != READY) {
            return readiness;
        }
        if (villageId == null) {
            return NO_VILLAGE;
        }
        EmeraldConfig config = EmeraldConfig.current();
        var eligibility = FundContributionChecks.assess(config, economy.villageSnapshot(villageId), type, purpose);
        if (!eligibility.allowed()) return eligibility.status();
        long cash = spendingMicro(player, economy.portfolioSnapshot(player.getUUID()).account().cashMicro)
                / EconomyState.MICRO;
        if (requested <= 0 || requested > cash
                || requested > EconomyService.MAX_WHOLE_EMERALD_TRANSACTION) return INSUFFICIENT;
        SpendingFunds.Payment payment = SpendingFunds.plan(
                economy.portfolioSnapshot(player.getUUID()).account().cashMicro,
                BankInventory.countItems(player, Items.EMERALD), requested);
        return spend(player, economy, requested, () -> {
            long oldReserve = economy.villageFundSnapshot(villageId).emergencyReserveMicro();
            EconomyService.VillageFundContributionResult result =
                    economy.contributeToVillageFund(player.getUUID(), villageId, requested, type, purpose);
            if (!result.contributed()) {
                return type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP
                        ? NOT_READY : PERSISTENCE_FAILED;
            }
            if (payment != null && player.containerMenu instanceof BankerMenu menu) {
                long addedReserve = economy.villageFundSnapshot(villageId).emergencyReserveMicro() - oldReserve;
                menu.acceptFundReceipt(result, addedReserve, payment);
            }
            return switch (type) {
                case ENDOWMENT -> VILLAGE_ENDOWED;
                case PROJECT_SPONSORSHIP -> VILLAGE_PROJECT_SPONSORED;
                case DIRECT_GRANT -> VILLAGE_FUNDED;
            };
        });
    }

    static long spendingMicro(ServerPlayer player, long cashMicro) {
        return SpendingFunds.availableMicro(cashMicro, BankInventory.countItems(player, Items.EMERALD));
    }

    /** The small inventory top-up settles durably before the normal, atomic bank mutation. */
    private static int spend(ServerPlayer player, EconomyService economy, int amount, IntSupplier action) {
        SpendingFunds.Payment payment = SpendingFunds.plan(
                economy.portfolioSnapshot(player.getUUID()).account().cashMicro,
                BankInventory.countItems(player, Items.EMERALD), amount);
        if (payment == null) return INSUFFICIENT;
        if (payment.inventoryEmeralds() > 0 && !BankTransactionCoordinator.creditInventory(
                player, economy, EconomyState.InventoryTransactionKind.DEPOSIT, "emerald",
                payment.inventoryEmeralds(), payment.inventoryEmeralds() * EconomyState.MICRO)) {
            return PERSISTENCE_FAILED; // Never perform a purchase while inventory recovery is pending.
        }
        int result = action.getAsInt();
        if (result < 0 && payment.inventoryEmeralds() > 0) {
            player.sendSystemMessage(Component.literal("[Emerald Standard] The payment did not complete. "
                    + payment.inventoryEmeralds() + " inventory emerald(s) were safely deposited into Bank Cash; "
                    + "they remain available to spend or withdraw."));
        }
        return result;
    }

    private static int prepare(ServerPlayer player, EconomyService economy) {
        BankTransactionCoordinator.RecoveryResult recovery =
                BankTransactionCoordinator.reconcile(player, economy);
        if (recovery.found() && !recovery.recovered()) {
            return PERSISTENCE_FAILED;
        }
        if (!economy.transactionBlockReason(player.getUUID()).isBlank()) {
            return BUSY;
        }
        int cooldown = EmeraldConfig.current().transactionCooldownTicks();
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.get(player.getUUID());
        if (cooldown > 0
                && previous != null
                && now >= previous
                && now - previous < cooldown) {
            return BUSY;
        }
        LAST_ACTION_TICK.put(player.getUUID(), now);
        return READY;
    }

    private static int cappedInventoryAmount(int requested, long available) {
        if (requested <= 0 || available <= 0L) {
            return 0;
        }
        return (int) Math.min(
                Math.min((long) requested, available),
                EconomyService.MAX_INVENTORY_ITEM_TRANSACTION);
    }

    private static int cappedFinancialAmount(int requested, long available) {
        if (requested <= 0 || available <= 0L) {
            return 0;
        }
        return (int) Math.min(
                Math.min((long) requested, available),
                EconomyService.MAX_WHOLE_EMERALD_TRANSACTION);
    }
}
