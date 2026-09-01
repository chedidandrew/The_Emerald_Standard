package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Server-side handlers for The Emerald Standard alpha commands. */
final class EmeraldCommandHandlers {
    private EmeraldCommandHandlers() {
    }

    static int help(CommandContext<CommandSourceStack> context) {
        send(context, "The Emerald Standard alpha commands");
        send(context, "/emerald market | commodities | portfolio");
        send(context, "/emerald deposit <emeralds> | withdraw <emeralds>");
        send(context, "/emerald savings deposit|withdraw <emeralds>");
        send(context, "/emerald buy <ticker> <emeralds> | sell <ticker> <shares>");
        send(context, "/emerald cd open <emeralds> <30|90|180|365> | cd close");
        send(context, "/emerald loan fund <emeralds> <30|90|180|365> | loan collect");
        send(context, "/emerald exchange <resource> <count>");
        send(context, "Players fund villager businesses. Players can never borrow or enter debt.");
        return 1;
    }

    static int market(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) {
        EconomyState state = economy.snapshot();
        if (state == null) {
            return send(context, "The economy is not ready yet.");
        }
        send(context, String.format(
                Locale.ROOT,
                "Economic day %d | Regime %s | Savings %.2f%% | 90-day CD %.2f%%",
                state.economicDay,
                state.regime,
                EconomyEngine.savingsAnnualRate(state.regime) * 100.0,
                EconomyEngine.cdAnnualRate(state.regime, 90) * 100.0));
        for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
            send(context, String.format(
                    Locale.ROOT,
                    "%s | %s | %.2f emeralds",
                    asset.ticker(),
                    asset.name(),
                    state.prices.get(asset.ticker())));
        }
        send(context, String.format(
                Locale.ROOT,
                "Trading spread %.2f%% per side",
                EconomyEngine.TRADE_SPREAD * 100.0));
        return 1;
    }

    static int commodities(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) {
        EconomyState state = economy.snapshot();
        if (state == null) {
            return send(context, "The economy is not ready yet.");
        }
        send(context, "Current bank exchange values");
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            send(context, String.format(
                    Locale.ROOT,
                    "%s | %s | %.3f emeralds",
                    commodity.id(),
                    commodity.name(),
                    state.commodityPrices.get(commodity.id())));
        }
        send(context, "Gold blocks use 9 gold. Netherite ingots use 4 scrap and 4 gold.");
        return 1;
    }

    static int portfolio(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        EconomyState state = economy.snapshot();
        if (state == null) {
            return send(context, "The economy is not ready yet.");
        }
        EconomyState.Account account = state.account(player.getUUID());
        send(context, String.format(
                Locale.ROOT,
                "Cash %.3f | Savings %.3f | Net worth %.3f emeralds",
                emeralds(account.cashMicro),
                emeralds(account.savingsMicro),
                state.netWorth(player.getUUID())));

        if (account.hasCd()) {
            send(context, String.format(
                    Locale.ROOT,
                    "CD %.3f | Locked rate %.2f%% | %d days remaining",
                    emeralds(account.cdValueMicro),
                    account.cdAnnualRate * 100.0,
                    Math.max(0L, account.cdMaturityDay - state.economicDay)));
        }
        if (account.hasLoan()) {
            String status = account.loanResolved
                    ? account.loanOutcome + String.format(
                            Locale.ROOT,
                            " at %.1f%% recovery",
                            account.loanRecoveryRate * 100.0)
                    : Math.max(0L, account.loanMaturityDay - state.economicDay)
                            + " days remaining";
            send(context, String.format(
                    Locale.ROOT,
                    "Villager loan %.3f | Locked yield %.2f%% | %s",
                    emeralds(account.loanValueMicro),
                    account.loanAnnualRate * 100.0,
                    status));
        }

        if (account.shares.isEmpty()) {
            send(context, "Holdings none");
        } else {
            send(context, "Holdings");
            account.shares.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> send(context, String.format(
                            Locale.ROOT,
                            "%s | %.6f shares | %.3f emeralds",
                            entry.getKey(),
                            entry.getValue(),
                            entry.getValue() * state.prices.getOrDefault(entry.getKey(), 0.0))));
        }
        return 1;
    }

    static int deposit(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        ServerPlayer player = player(context);
        if (!BankInventory.removeItems(player, Items.EMERALD, amount)) {
            return send(context, "Not enough emeralds in your inventory.");
        }
        if (!economy.deposit(player.getUUID(), amount)) {
            BankInventory.giveOrDrop(player, Items.EMERALD, amount);
            return send(context, "Deposit failed and your emeralds were restored. "
                    + errorSuffix(economy));
        }
        return send(context, "Deposited " + amount + " emeralds.");
    }

    static int withdraw(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        ServerPlayer player = player(context);
        if (economy.withdraw(player.getUUID(), amount) == 0L) {
            return send(context, "Insufficient bank cash or the withdrawal could not be saved.");
        }

        int remainder = BankInventory.insertItems(player, Items.EMERALD, amount);
        if (remainder > 0) {
            if (!economy.deposit(player.getUUID(), remainder)) {
                BankInventory.giveOrDrop(player, Items.EMERALD, remainder);
                return send(context, "Inventory filled. Remaining emeralds were dropped safely.");
            }
            return send(context, "Withdrew " + (amount - remainder)
                    + " emeralds. " + remainder + " remained in bank cash.");
        }
        return send(context, "Withdrew " + amount + " emeralds.");
    }

    static int savings(
            CommandContext<CommandSourceStack> context,
            EconomyService economy,
            boolean intoSavings) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        boolean success = economy.moveSavings(player(context).getUUID(), amount, intoSavings);
        return send(context, success
                ? "Savings transfer complete."
                : "Savings transfer failed because the source balance was insufficient.");
    }

    static int buy(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        boolean success = economy.buy(
                player(context).getUUID(),
                StringArgumentType.getString(context, "ticker"),
                IntegerArgumentType.getInteger(context, "amount"));
        return send(context, success
                ? "Investment purchased."
                : "Purchase failed. Check the ticker and bank cash balance.");
    }

    static int sell(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        boolean success = economy.sell(
                player(context).getUUID(),
                StringArgumentType.getString(context, "ticker"),
                DoubleArgumentType.getDouble(context, "shares"));
        return send(context, success
                ? "Investment sold."
                : "Sale failed. Check the ticker and shares held.");
    }

    static int openCd(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int term = IntegerArgumentType.getInteger(context, "term");
        if (!supportedTerm(term)) {
            return send(context, "CD term must be 30, 90, 180, or 365 days.");
        }
        ServerPlayer player = player(context);
        if (!economy.openCd(player.getUUID(), amount, term)) {
            return send(context, "CD open failed. Only one CD can be active in this alpha.");
        }
        EconomyState.Account account = economy.snapshot().account(player.getUUID());
        return send(context, String.format(
                Locale.ROOT,
                "CD opened at a locked %.2f%% annual rate through economic day %d.",
                account.cdAnnualRate * 100.0,
                account.cdMaturityDay));
    }

    static int closeCd(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        EconomyService.CdCloseResult result = economy.closeCd(player(context).getUUID());
        if (!result.closed()) {
            return send(context, "No CD could be closed.");
        }
        return result.matured()
                ? send(context, String.format(
                        Locale.ROOT,
                        "Mature CD collected %.3f emeralds.",
                        emeralds(result.payoutMicro())))
                : send(context, String.format(
                        Locale.ROOT,
                        "CD closed early. Returned %.3f after a %.3f emerald penalty.",
                        emeralds(result.payoutMicro()),
                        emeralds(result.penaltyMicro())));
    }

    static int fundLoan(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int term = IntegerArgumentType.getInteger(context, "term");
        if (!supportedTerm(term)) {
            return send(context, "Loan term must be 30, 90, 180, or 365 days.");
        }
        ServerPlayer player = player(context);
        if (!economy.fundLoan(player.getUUID(), amount, term)) {
            return send(context, "Loan funding failed. Only one can be active in this alpha.");
        }
        EconomyState.Account account = economy.snapshot().account(player.getUUID());
        return send(context, String.format(
                Locale.ROOT,
                "Villagers received the investment at a locked %.2f%% yield. Principal is at risk, but you can never owe more.",
                account.loanAnnualRate * 100.0));
    }

    static int collectLoan(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        EconomyService.LoanCollectionResult result = economy.collectLoan(
                player(context).getUUID());
        if (!result.collected()) {
            return send(context, "The villager loan is absent or has not matured.");
        }
        return switch (result.outcome()) {
            case REPAID -> send(context, String.format(
                    Locale.ROOT,
                    "Villager loan repaid in full %.3f emeralds.",
                    emeralds(result.payoutMicro())));
            case PARTIAL_DEFAULT -> send(context, String.format(
                    Locale.ROOT,
                    "Villager business partially defaulted. Recovered %.3f emeralds at %.1f%% recovery.",
                    emeralds(result.payoutMicro()),
                    result.recoveryRate() * 100.0));
            case FULL_DEFAULT -> send(context,
                    "Villager business fully defaulted. It returned 0 emeralds, and you owe nothing.");
        };
    }

    static int exchange(
            CommandContext<CommandSourceStack> context,
            EconomyService economy) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "resource")
                .toLowerCase(Locale.ROOT);
        int count = IntegerArgumentType.getInteger(context, "count");
        BankInventory.ExchangeResource resource = BankInventory.exchangeResource(name);
        if (resource == null) {
            return send(context, "Unknown resource. Use /emerald commodities for base markets.");
        }
        long proceeds = economy.quoteResourceValueMicro(resource.quoteId(), count);
        if (proceeds < 0L) {
            return send(context, "That resource is not accepted by the bank.");
        }
        ServerPlayer player = player(context);
        if (!BankInventory.removeItems(player, resource.item(), count)) {
            return send(context, "Not enough matching resources in your inventory.");
        }
        if (!economy.creditMicro(player.getUUID(), proceeds)) {
            BankInventory.giveOrDrop(player, resource.item(), count);
            return send(context, "Exchange failed and your resources were restored. "
                    + errorSuffix(economy));
        }
        return send(context, String.format(
                Locale.ROOT,
                "Exchanged %d items for %.3f emeralds in bank cash.",
                count,
                emeralds(proceeds)));
    }

    private static boolean supportedTerm(int term) {
        return term == 30 || term == 90 || term == 180 || term == 365;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static int send(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(
                () -> Component.literal("[Emerald Standard] " + text),
                false);
        return 1;
    }

    private static String errorSuffix(EconomyService economy) {
        String error = economy.lastError();
        return error == null || error.isBlank() ? "" : "Reason: " + error;
    }

    private static double emeralds(long microEmeralds) {
        return microEmeralds / (double) EconomyState.MICRO;
    }
}
