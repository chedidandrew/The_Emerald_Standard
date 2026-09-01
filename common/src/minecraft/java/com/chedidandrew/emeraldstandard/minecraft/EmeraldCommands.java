package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

/** Registers the command interface used before the Banker GUI is introduced. */
public final class EmeraldCommands {
    private static final List<String> TICKERS = EconomyEngine.ASSETS.stream()
            .map(EconomyEngine.Asset::ticker)
            .toList();
    private static final List<String> TERMS = List.of("30", "90", "180", "365");

    private EmeraldCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            EconomyService economy) {
        int maxFinancialAmount = (int) EconomyService.MAX_WHOLE_EMERALD_TRANSACTION;
        int maxInventoryAmount = EconomyService.MAX_INVENTORY_ITEM_TRANSACTION;

        dispatcher.register(Commands.literal("emerald")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("help")
                        .executes(EmeraldCommandHandlers::help))
                .then(Commands.literal("open")
                        .executes(context -> EmeraldCommandHandlers.open(context, economy)))
                .then(Commands.literal("market")
                        .executes(context -> EmeraldCommandHandlers.market(context, economy)))
                .then(Commands.literal("commodities")
                        .executes(context -> EmeraldCommandHandlers.commodities(context, economy)))
                .then(Commands.literal("portfolio")
                        .executes(context -> EmeraldCommandHandlers.portfolio(context, economy)))
                .then(Commands.literal("recover")
                        .executes(context -> EmeraldCommandHandlers.recover(context, economy)))
                .then(Commands.literal("deposit")
                        .then(Commands.argument(
                                        "amount",
                                        IntegerArgumentType.integer(1, maxInventoryAmount))
                                .executes(context -> EmeraldCommandHandlers.deposit(context, economy))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument(
                                        "amount",
                                        IntegerArgumentType.integer(1, maxInventoryAmount))
                                .executes(context -> EmeraldCommandHandlers.withdraw(context, economy))))
                .then(Commands.literal("savings")
                        .then(Commands.literal("deposit")
                                .then(Commands.argument(
                                                "amount",
                                                IntegerArgumentType.integer(1, maxFinancialAmount))
                                        .executes(context -> EmeraldCommandHandlers.savings(
                                                context, economy, true))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument(
                                                "amount",
                                                IntegerArgumentType.integer(1, maxFinancialAmount))
                                        .executes(context -> EmeraldCommandHandlers.savings(
                                                context, economy, false)))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("ticker", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TICKERS, builder))
                                .then(Commands.argument(
                                                "amount",
                                                IntegerArgumentType.integer(1, maxFinancialAmount))
                                        .executes(context -> EmeraldCommandHandlers.buy(context, economy)))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("ticker", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TICKERS, builder))
                                .then(Commands.argument(
                                                "shares",
                                                DoubleArgumentType.doubleArg(0.000001, 1.0e12))
                                        .executes(context -> EmeraldCommandHandlers.sell(context, economy)))))
                .then(Commands.literal("cd")
                        .then(Commands.literal("open")
                                .then(Commands.argument(
                                                "amount",
                                                IntegerArgumentType.integer(1, maxFinancialAmount))
                                        .then(Commands.argument(
                                                        "term",
                                                        IntegerArgumentType.integer(30, 365))
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(TERMS, builder))
                                                .executes(context -> EmeraldCommandHandlers.openCd(
                                                        context, economy)))))
                        .then(Commands.literal("close")
                                .executes(context -> EmeraldCommandHandlers.closeCd(context, economy))))
                .then(Commands.literal("loan")
                        .then(Commands.literal("fund")
                                .then(Commands.argument(
                                                "amount",
                                                IntegerArgumentType.integer(1, maxFinancialAmount))
                                        .then(Commands.argument(
                                                        "term",
                                                        IntegerArgumentType.integer(30, 365))
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(TERMS, builder))
                                                .executes(context -> EmeraldCommandHandlers.fundLoan(
                                                        context, economy)))))
                        .then(Commands.literal("collect")
                                .executes(context -> EmeraldCommandHandlers.collectLoan(
                                        context, economy))))
                .then(Commands.literal("exchange")
                        .then(Commands.argument("resource", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        BankInventory.exchangeResourceNames(), builder))
                                .then(Commands.argument(
                                                "count",
                                                IntegerArgumentType.integer(1, maxInventoryAmount))
                                        .executes(context -> EmeraldCommandHandlers.exchange(
                                                context, economy))))));
    }
}
