package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Registers the command interface used before the Banker GUI is introduced. */
public final class EmeraldCommands {
    private EmeraldCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            EconomyService economy) {
        dispatcher.register(Commands.literal("emerald")
                .then(Commands.literal("help")
                        .executes(EmeraldCommandHandlers::help))
                .then(Commands.literal("market")
                        .executes(context -> EmeraldCommandHandlers.market(context, economy)))
                .then(Commands.literal("commodities")
                        .executes(context -> EmeraldCommandHandlers.commodities(context, economy)))
                .then(Commands.literal("portfolio")
                        .executes(context -> EmeraldCommandHandlers.portfolio(context, economy)))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> EmeraldCommandHandlers.deposit(context, economy))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> EmeraldCommandHandlers.withdraw(context, economy))))
                .then(Commands.literal("savings")
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> EmeraldCommandHandlers.savings(
                                                context, economy, true))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> EmeraldCommandHandlers.savings(
                                                context, economy, false)))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("ticker", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> EmeraldCommandHandlers.buy(context, economy)))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("ticker", StringArgumentType.word())
                                .then(Commands.argument(
                                                "shares",
                                                DoubleArgumentType.doubleArg(0.000001))
                                        .executes(context -> EmeraldCommandHandlers.sell(context, economy)))))
                .then(Commands.literal("cd")
                        .then(Commands.literal("open")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument(
                                                        "term",
                                                        IntegerArgumentType.integer(30, 365))
                                                .executes(context -> EmeraldCommandHandlers.openCd(
                                                        context, economy)))))
                        .then(Commands.literal("close")
                                .executes(context -> EmeraldCommandHandlers.closeCd(context, economy))))
                .then(Commands.literal("loan")
                        .then(Commands.literal("fund")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument(
                                                        "term",
                                                        IntegerArgumentType.integer(30, 365))
                                                .executes(context -> EmeraldCommandHandlers.fundLoan(
                                                        context, economy)))))
                        .then(Commands.literal("collect")
                                .executes(context -> EmeraldCommandHandlers.collectLoan(
                                        context, economy))))
                .then(Commands.literal("exchange")
                        .then(Commands.argument("resource", StringArgumentType.word())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> EmeraldCommandHandlers.exchange(
                                                context, economy))))));
    }
}
