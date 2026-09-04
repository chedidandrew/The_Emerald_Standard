package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.PortfolioAnalytics;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Server-side handlers for The Emerald Standard alpha commands. */
final class EmeraldCommandHandlers {
    private EmeraldCommandHandlers() {
    }

    static int help(CommandContext<CommandSourceStack> context) {
        success(context, "The Emerald Standard alpha commands");
        success(context, "/emerald open | market | commodities | portfolio | recover");
        success(context, "/emerald debug starts or stops a five-minute full diagnostic capture");
        success(context, "/emerald debug mark adds an optional moment marker");
        success(context, "/emerald config show|reload");
        success(context, "/emerald deposit <emeralds> | withdraw <emeralds>");
        success(context, "/emerald savings deposit|withdraw <emeralds>");
        success(context, "/emerald buy <ticker> <emeralds> | sell <ticker> <shares>");
        success(context, "/emerald cd open <emeralds> <30|90|180|365> | cd close <position>");
        success(context, "/emerald loan fund <emeralds> <30|90|180|365> | loan collect <position>");
        success(context, "/emerald exchange <resource> <count>");
        success(context, "Players fund villager businesses. Players can never borrow or enter debt.");
        return 1;
    }

    static int debug(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            int minutes) throws CommandSyntaxException {
        DebugFlightRecorder.CommandResult result =
                DebugFlightRecorder.toggle(player(context), economy, minutes);
        return result.success()
                ? success(context, result.message())
                : failure(context, result.message());
    }

    static int debugMark(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        DebugFlightRecorder.CommandResult result =
                DebugFlightRecorder.mark(player(context), economy);
        return result.success()
                ? success(context, result.message())
                : failure(context, result.message());
    }

    static int debugStop(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        DebugFlightRecorder.CommandResult result =
                DebugFlightRecorder.stop(player(context), economy);
        return result.success()
                ? success(context, result.message())
                : failure(context, result.message());
    }

    static int showConfig(CommandContext<CommandSourceStack> context) {
        return success(context, "Configuration: " + EmeraldConfig.current().summary());
    }

    static int reloadConfig(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) {
        try {
            EmeraldConfig config = EmeraldConfig.reload();
            config.applyTo(economy);
            return success(context, "Reloaded configuration: " + config.summary());
        } catch (IOException exception) {
            return failure(context, "Configuration reload failed: " + exception.getMessage());
        }
    }

    static int open(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        return BankerAccess.open(player, economy)
                ? success(context, "Opened the Banker dashboard.")
                : failure(context, "The Banker dashboard could not be opened.");
    }

    static int market(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) {
        EconomyService.MarketSnapshot state = economy.marketSnapshot();
        if (state == null) {
            return failure(context, "The economy is not ready yet.");
        }
        success(context, String.format(
                Locale.ROOT,
                "Economic day %d | Regime %s | Savings %.2f%% | 90-day CD %.2f%%",
                state.economicDay(),
                state.regime(),
                EconomyEngine.savingsAnnualRate(state.regime()) * 100.0,
                EconomyEngine.cdAnnualRate(state.regime(), 90) * 100.0));
        if (state.catchUpDaysRemaining() > 0L) {
            success(context,
                    "Catch-up in progress: " + state.catchUpDaysRemaining()
                            + " economic day(s) remain. Trading is temporarily paused.");
        }
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            success(context, String.format(
                    Locale.ROOT,
                    "%s | %s | %.2f emeralds",
                    asset.ticker(),
                    asset.name(),
                    state.prices().get(asset.ticker())));
        }
        success(context, String.format(
                Locale.ROOT,
                "Trading spread %.2f%% per side",
                EconomyEngine.TRADE_SPREAD * 100.0));
        return 1;
    }

    static int commodities(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) {
        EconomyService.MarketSnapshot state = economy.marketSnapshot();
        if (state == null) {
            return failure(context, "The economy is not ready yet.");
        }
        success(context, "Current bank exchange values");
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            success(context, String.format(
                    Locale.ROOT,
                    "%s | %s | %.3f emeralds",
                    commodity.id(),
                    commodity.name(),
                    state.commodityPrices().get(commodity.id())));
        }
        success(context, "Gold blocks use 9 gold. Netherite ingots use 4 scrap and 4 gold.");
        return 1;
    }

    static int portfolio(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        EconomyService.PortfolioSnapshot portfolio = economy.portfolioSnapshot(player.getUUID());
        if (portfolio == null) {
            return failure(context, "The economy is not ready yet.");
        }
        EconomyState.Account account = portfolio.account();
        PortfolioAnalytics.PortfolioSnapshot analytics =
                economy.portfolioAnalyticsSnapshot(player.getUUID());
        success(context, String.format(
                Locale.ROOT,
                "Cash %.3f | Savings %.3f | Net worth %.3f | Contributions %.3f emeralds",
                emeralds(account.cashMicro),
                emeralds(account.savingsMicro),
                portfolio.netWorth(),
                emeralds(analytics.totalContributionsMicro())));
        success(context, String.format(
                Locale.ROOT,
                "Stock basis %.3f | Unrealized %+.3f | Realized %+.3f emeralds",
                emeralds(analytics.totalCostBasisMicro()),
                emeralds(analytics.unrealizedGainMicro()),
                emeralds(analytics.realizedGainMicro())));

        if (portfolio.catchUpDaysRemaining() > 0L) {
            success(context,
                    "Trading paused while " + portfolio.catchUpDaysRemaining()
                            + " catch-up day(s) are processed.");
        }
        if (portfolio.pendingTransaction() != null) {
            success(context,
                    "Recovery journal active for transaction "
                            + portfolio.pendingTransaction().transactionId + ". Use /emerald recover.");
        }

        if (!analytics.cds().isEmpty()) {
            success(context, "CD positions " + analytics.cds().size()
                    + "/" + EconomyState.MAX_TERM_POSITIONS);
            for (EconomyState.CdPosition position : analytics.cds()) {
                success(context, String.format(
                        Locale.ROOT,
                        "CD #%d | %.3f | %.2f%% | %d days remaining",
                        position.positionId,
                        emeralds(position.valueMicro),
                        position.annualRate * 100.0,
                        Math.max(0L, position.maturityDay - portfolio.economicDay())));
            }
        }
        if (!analytics.loans().isEmpty()) {
            success(context, "Villager lending positions " + analytics.loans().size()
                    + "/" + EconomyState.MAX_TERM_POSITIONS);
            for (EconomyState.LoanPosition position : analytics.loans()) {
                String status = position.resolved
                    ? position.outcome + String.format(
                            Locale.ROOT,
                            " at %.1f%% recovery",
                            position.recoveryRate * 100.0)
                    : Math.max(0L, position.maturityDay - portfolio.economicDay())
                            + " days remaining";
                success(context, String.format(
                        Locale.ROOT,
                        "Loan #%d | %.3f | %.2f%% | %s",
                        position.positionId,
                        emeralds(position.valueMicro),
                        position.annualRate * 100.0,
                        status));
            }
        }

        if (account.shares.isEmpty()) {
            success(context, "Holdings none");
        } else {
            success(context, "Holdings");
            analytics.positions().entrySet().stream()
                    .filter(entry -> entry.getValue().shares() > 0.0)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> success(context, String.format(
                            Locale.ROOT,
                            "%s | %.6f shares | %.3f value | %.3f average | %+.3f unrealized | %.1f%% allocation",
                            entry.getKey(),
                            entry.getValue().shares(),
                            emeralds(entry.getValue().marketValueMicro()),
                            entry.getValue().averagePurchasePrice(),
                            emeralds(entry.getValue().unrealizedGainMicro()),
                            entry.getValue().portfolioWeight() * 100.0)));
        }
        if (!analytics.transactions().isEmpty()) {
            success(context, "Recent activity");
            int start = Math.max(0, analytics.transactions().size() - 5);
            analytics.transactions().subList(start, analytics.transactions().size())
                    .forEach(entry -> success(context, String.format(
                            Locale.ROOT,
                            "Day %d | %s | %s | %.3f E",
                            entry.day,
                            entry.kind,
                            entry.symbol,
                            emeralds(entry.amountMicro))));
        }
        return 1;
    }

    static int recover(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        BankTransactionCoordinator.RecoveryResult result =
                BankTransactionCoordinator.reconcile(player, economy);
        if (!result.found()) {
            return success(context, "No pending inventory transaction was found.");
        }
        if (!result.recovered()) {
            return failure(context, "Recovery could not complete: " + result.error());
        }
        return success(context,
                "Recovery complete for transaction " + result.transactionId() + ".");
    }

    static int deposit(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int inventoryBefore = BankInventory.countItems(player, Items.EMERALD);
        if (inventoryBefore < amount) {
            return failure(context, "Not enough emeralds in your inventory.");
        }

        long creditMicro = amount * EconomyState.MICRO;
        EconomyState.PendingInventoryTransaction transaction =
                economy.prepareInventoryCredit(
                        player.getUUID(),
                        EconomyState.InventoryTransactionKind.DEPOSIT,
                        "emerald",
                        amount,
                        inventoryBefore,
                        creditMicro);
        if (transaction == null) {
            return failure(context, "Could not prepare the deposit. " + errorSuffix(economy));
        }

        if (!BankInventory.removeItems(player, Items.EMERALD, amount)) {
            economy.cancelPreparedInventoryTransaction(
                    player.getUUID(), transaction.transactionId);
            return failure(context, "The deposit was canceled before any bank credit was applied.");
        }
        if (!economy.commitPreparedInventoryCredit(
                player.getUUID(), transaction.transactionId)) {
            int remainder = BankInventory.restoreItems(player, Items.EMERALD, amount);
            if (remainder == 0) {
                economy.cancelPreparedInventoryTransaction(
                        player.getUUID(), transaction.transactionId);
            }
            return failure(context,
                    remainder == 0
                            ? "Deposit failed and your emeralds were restored. " + errorSuffix(economy)
                            : "Deposit failed; " + remainder
                                    + " emerald(s) remain protected by recovery until inventory space is available.");
        }
        if (!BankTransactionCoordinator.savePlayerAndComplete(
                player, economy, transaction.transactionId)) {
            return success(context,
                    "Deposited " + amount
                            + " emeralds. A recovery journal remains until player data is saved.");
        }
        return success(context, "Deposited " + amount + " emeralds.");
    }

    static int withdraw(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        int requested = IntegerArgumentType.getInteger(context, "amount");
        int inventoryBefore = BankInventory.countItems(player, Items.EMERALD);
        EconomyState.PendingInventoryTransaction transaction =
                economy.beginInventoryWithdrawal(
                        player.getUUID(), requested, inventoryBefore);
        if (transaction == null) {
            return failure(context,
                    "Insufficient bank cash or the withdrawal could not be journaled. "
                            + errorSuffix(economy));
        }

        int remainder = BankInventory.insertItems(player, Items.EMERALD, requested);
        int delivered = requested - remainder;
        if (remainder > 0
                && !economy.reducePendingWithdrawal(
                        player.getUUID(), transaction.transactionId, remainder)) {
            return success(context,
                    "Withdrew " + delivered
                            + " emeralds. The recovery journal will deliver or refund the remaining "
                            + remainder + " after relogging.");
        }

        EconomyState.PendingInventoryTransaction adjusted =
                economy.pendingInventoryTransaction(player.getUUID());
        if (adjusted == null) {
            return delivered == 0
                    ? failure(context, "Your inventory had no room, so nothing was withdrawn.")
                    : success(context, "Withdrew " + delivered + " emeralds.");
        }
        if (!BankTransactionCoordinator.savePlayerAndComplete(
                player, economy, adjusted.transactionId)) {
            return success(context,
                    "Withdrew " + delivered
                            + " emeralds. A recovery journal remains until player data is saved.");
        }
        return remainder == 0
                ? success(context, "Withdrew " + delivered + " emeralds.")
                : success(context,
                        "Withdrew " + delivered + " emeralds. " + remainder
                                + " remained in bank cash because your inventory was full.");
    }

    static int savings(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            boolean intoSavings) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        boolean success = economy.moveSavings(player.getUUID(), amount, intoSavings);
        return success
                ? success(context, "Savings transfer complete.")
                : failure(context,
                        "Savings transfer failed because the source balance was insufficient. "
                                + errorSuffix(economy));
    }

    static int buy(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        boolean success = economy.buy(
                player.getUUID(),
                StringArgumentType.getString(context, "ticker"),
                IntegerArgumentType.getInteger(context, "amount"));
        return success
                ? success(context, "Investment purchased.")
                : failure(context,
                        "Purchase failed. Check the ticker and bank cash balance. "
                                + errorSuffix(economy));
    }

    static int sell(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        boolean success = economy.sell(
                player.getUUID(),
                StringArgumentType.getString(context, "ticker"),
                DoubleArgumentType.getDouble(context, "shares"));
        return success
                ? success(context, "Investment sold.")
                : failure(context,
                        "Sale failed. Check the ticker and shares held. "
                                + errorSuffix(economy));
    }

    static int openCd(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int term = IntegerArgumentType.getInteger(context, "term");
        if (!supportedTerm(term)) {
            return failure(context, "CD term must be 30, 90, 180, or 365 days.");
        }
        long positionId = economy.openCdPosition(player.getUUID(), amount, term);
        if (positionId <= 0L) {
            return failure(context,
                    "CD open failed. Check the bank cash balance and eight-position limit. "
                            + errorSuffix(economy));
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        EconomyState.CdPosition position = account.cdPositions.get(positionId);
        return success(context, String.format(
                Locale.ROOT,
                "CD #%d opened at a locked %.2f%% annual rate through economic day %d.",
                positionId,
                position == null ? 0.0 : position.annualRate * 100.0,
                position == null ? 0L : position.maturityDay));
    }

    static int closeCd(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        if (account.cdPositions.size() != 1) {
            String available = account.cdPositions.keySet().stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            return failure(context, account.cdPositions.isEmpty()
                    ? "No CD could be closed."
                    : "More than one CD is active. Choose an exact position: " + available);
        }
        return closeCdPrepared(
                context, economy, player, account.cdPositions.keySet().iterator().next());
    }

    static int closeCd(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            long positionId) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        return closeCdPrepared(context, economy, player, positionId);
    }

    private static int closeCdPrepared(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            ServerPlayer player,
            long positionId) {
        EconomyService.CdCloseResult result = economy.closeCd(player.getUUID(), positionId);
        if (!result.closed()) {
            return failure(context,
                    "CD #" + positionId + " could not be closed. " + errorSuffix(economy));
        }
        return result.matured()
                ? success(context, String.format(
                        Locale.ROOT,
                        "Mature CD #%d collected %.3f emeralds.",
                        positionId,
                        emeralds(result.payoutMicro())))
                : success(context, String.format(
                        Locale.ROOT,
                        "CD #%d closed early. Returned %.3f after a %.3f emerald penalty.",
                        positionId,
                        emeralds(result.payoutMicro()),
                        emeralds(result.penaltyMicro())));
    }

    static int fundLoan(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int term = IntegerArgumentType.getInteger(context, "term");
        if (!supportedTerm(term)) {
            return failure(context, "Loan term must be 30, 90, 180, or 365 days.");
        }
        long positionId = economy.openLoanPosition(player.getUUID(), amount, term);
        if (positionId <= 0L) {
            return failure(context,
                    "Loan funding failed. Check the bank cash balance and eight-position limit. "
                            + errorSuffix(economy));
        }
        EconomyState.Account account = economy.portfolioSnapshot(player.getUUID()).account();
        EconomyState.LoanPosition position = account.loanPositions.get(positionId);
        return success(context, String.format(
                Locale.ROOT,
                "Loan #%d funded at a locked %.2f%% yield. Principal is at risk, but you can never owe more.",
                positionId,
                position == null ? 0.0 : position.annualRate * 100.0));
    }

    static int collectLoan(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        EconomyService.PortfolioSnapshot snapshot = economy.portfolioSnapshot(player.getUUID());
        java.util.List<Long> ready = snapshot.account().loanPositions.values().stream()
                .filter(position -> position.resolved
                        && snapshot.economicDay() >= position.maturityDay)
                .map(position -> position.positionId)
                .toList();
        if (ready.size() != 1) {
            String available = ready.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            return failure(context, ready.isEmpty()
                    ? "No villager loan is ready to collect."
                    : "More than one loan is ready. Choose an exact position: " + available);
        }
        return collectLoanPrepared(context, economy, player, ready.getFirst());
    }

    static int collectLoan(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            long positionId) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        return collectLoanPrepared(context, economy, player, positionId);
    }

    private static int collectLoanPrepared(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            ServerPlayer player,
            long positionId) {
        EconomyService.LoanCollectionResult result = economy.collectLoan(
                player.getUUID(), positionId);
        if (!result.collected()) {
            return failure(context,
                    "Villager loan #" + positionId
                            + " is absent or has not matured. " + errorSuffix(economy));
        }
        return switch (result.outcome()) {
            case REPAID -> success(context, String.format(
                    Locale.ROOT,
                    "Villager loan #%d repaid in full %.3f emeralds.",
                    positionId,
                    emeralds(result.payoutMicro())));
            case PARTIAL_DEFAULT -> success(context, String.format(
                    Locale.ROOT,
                    "Villager loan #%d partially defaulted. Recovered %.3f emeralds at %.1f%% recovery.",
                    positionId,
                    emeralds(result.payoutMicro()),
                    result.recoveryRate() * 100.0));
            case FULL_DEFAULT -> success(context,
                    "Villager loan #" + positionId
                            + " fully defaulted. It returned 0 emeralds, and you owe nothing.");
        };
    }

    static int exchange(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        if (!preparePlayerForBanking(context, economy, player)) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "resource")
                .toLowerCase(Locale.ROOT);
        int count = IntegerArgumentType.getInteger(context, "count");
        BankInventory.ExchangeResource resource = BankInventory.exchangeResource(name);
        if (resource == null) {
            return failure(context, "Unknown resource. Use /emerald commodities for base markets.");
        }
        long proceeds = economy.quoteResourceValueMicro(resource.quoteId(), count);
        if (proceeds <= 0L) {
            return failure(context, "That resource is not currently accepted by the bank.");
        }
        int inventoryBefore = BankInventory.countItems(player, resource.item());
        if (inventoryBefore < count) {
            return failure(context, "Not enough matching resources in your inventory.");
        }

        EconomyState.PendingInventoryTransaction transaction =
                economy.prepareInventoryCredit(
                        player.getUUID(),
                        EconomyState.InventoryTransactionKind.EXCHANGE,
                        resource.journalKey(),
                        count,
                        inventoryBefore,
                        proceeds);
        if (transaction == null) {
            return failure(context, "Could not prepare the exchange. " + errorSuffix(economy));
        }
        if (!BankInventory.removeItems(player, resource.item(), count)) {
            economy.cancelPreparedInventoryTransaction(
                    player.getUUID(), transaction.transactionId);
            return failure(context, "The exchange was canceled before bank credit was applied.");
        }
        if (!economy.commitPreparedInventoryCredit(
                player.getUUID(), transaction.transactionId)) {
            int remainder = BankInventory.restoreItems(player, resource.item(), count);
            if (remainder == 0) {
                economy.cancelPreparedInventoryTransaction(
                        player.getUUID(), transaction.transactionId);
            }
            return failure(context,
                    remainder == 0
                            ? "Exchange failed and your resources were restored. " + errorSuffix(economy)
                            : "Exchange failed; " + remainder
                                    + " item(s) remain protected by recovery until inventory space is available.");
        }
        if (!BankTransactionCoordinator.savePlayerAndComplete(
                player, economy, transaction.transactionId)) {
            return success(context, String.format(
                    Locale.ROOT,
                    "Exchanged %d item(s) for %.3f emeralds. A recovery journal remains until player data is saved.",
                    count,
                    emeralds(proceeds)));
        }
        return success(context, String.format(
                Locale.ROOT,
                "Exchanged %d item(s) for %.3f emeralds in bank cash.",
                count,
                emeralds(proceeds)));
    }

    private static boolean preparePlayerForBanking(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            ServerPlayer player) {
        BankTransactionCoordinator.RecoveryResult recovery =
                BankTransactionCoordinator.reconcile(player, economy);
        if (recovery.found() && !recovery.recovered()) {
            failure(context, "A previous transaction could not be recovered: " + recovery.error());
            return false;
        }
        String reason = economy.transactionBlockReason(player.getUUID());
        if (!reason.isBlank()) {
            failure(context, reason + ".");
            return false;
        }
        return true;
    }

    private static boolean supportedTerm(int term) {
        return term == 30 || term == 90 || term == 180 || term == 365;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static int success(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(
                () -> Component.literal("[Emerald Standard] " + text),
                false);
        return 1;
    }

    private static int failure(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendFailure(Component.literal("[Emerald Standard] " + text));
        return 0;
    }

    private static String errorSuffix(EconomyService economy) {
        String error = economy.lastError();
        return error == null || error.isBlank() ? "" : "Reason: " + error;
    }

    private static double emeralds(long microEmeralds) {
        return microEmeralds / (double) EconomyState.MICRO;
    }
}
