package com.chedidandrew.emeraldstandard.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Versioned, atomic persistence implementation for {@link EconomyState}. */
final class EconomyPersistence {
    private EconomyPersistence() {
    }

    static EconomyState load(Path path, long fallbackSeed, long now, long ticks)
            throws IOException {
        Path backup = backupPath(path);
        if (!Files.exists(path)) {
            return Files.exists(backup)
                    ? read(backup, fallbackSeed, now, ticks)
                    : EconomyState.fresh(fallbackSeed, now, ticks);
        }
        try {
            return read(path, fallbackSeed, now, ticks);
        } catch (IOException primaryFailure) {
            if (!Files.exists(backup)) {
                throw primaryFailure;
            }
            try {
                return read(backup, fallbackSeed, now, ticks);
            } catch (IOException backupFailure) {
                primaryFailure.addSuppressed(backupFailure);
                throw primaryFailure;
            }
        }
    }

    static void save(EconomyState state, Path path) throws IOException {
        state.validate();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(24_576);
        toProperties(state).store(
                output, "The Emerald Standard data format " + EconomyState.FORMAT_VERSION);
        ByteBuffer buffer = ByteBuffer.wrap(output.toByteArray());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");

        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }

        preserveValidPrimary(state, path);
        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        forceDirectory(path.getParent());
    }

    private static void preserveValidPrimary(EconomyState state, Path path) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return;
        }
        try {
            read(path, state.seed, state.lastWallClockMs, state.lastGameTicks);
            Files.copy(path, backupPath(path), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Never replace a known-good backup with a corrupt or future-format primary file.
        }
    }

    private static void forceDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory fsync is not available on every platform or filesystem.
        }
    }

    private static Properties toProperties(EconomyState state) {
        Properties properties = new Properties();
        properties.setProperty("format", Integer.toString(EconomyState.FORMAT_VERSION));
        properties.setProperty("seed", Long.toString(state.seed));
        properties.setProperty("day", Long.toString(state.economicDay));
        properties.setProperty("wall", Long.toString(state.lastWallClockMs));
        properties.setProperty("ticks", Long.toString(state.lastGameTicks));
        properties.setProperty("pending.wall_ms", Long.toString(state.pendingWallClockMs));
        properties.setProperty("pending.game_ticks", Long.toString(state.pendingGameTicks));
        properties.setProperty("regime", state.regime.name());

        state.prices.forEach((key, value) ->
                properties.setProperty("price." + key, Double.toString(value)));
        state.commodityPrices.forEach((key, value) ->
                properties.setProperty("commodity." + key, Double.toString(value)));

        for (Map.Entry<UUID, EconomyState.Account> entry : state.accounts.entrySet()) {
            writeAccount(properties, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, EconomyState.PendingInventoryTransaction> entry
                : state.pendingInventoryTransactions.entrySet()) {
            writeTransaction(properties, entry.getKey(), entry.getValue());
        }
        return properties;
    }

    private static void writeAccount(
            Properties properties,
            UUID id,
            EconomyState.Account account) {
        String prefix = "account." + id + ".";
        properties.setProperty(prefix + "cash", Long.toString(account.cashMicro));
        properties.setProperty(prefix + "savings", Long.toString(account.savingsMicro));
        properties.setProperty(prefix + "cd.principal", Long.toString(account.cdPrincipalMicro));
        properties.setProperty(prefix + "cd.value", Long.toString(account.cdValueMicro));
        properties.setProperty(prefix + "cd.open", Long.toString(account.cdOpenDay));
        properties.setProperty(prefix + "cd.maturity", Long.toString(account.cdMaturityDay));
        properties.setProperty(prefix + "cd.rate", Double.toString(account.cdAnnualRate));
        properties.setProperty(prefix + "loan.principal", Long.toString(account.loanPrincipalMicro));
        properties.setProperty(prefix + "loan.value", Long.toString(account.loanValueMicro));
        properties.setProperty(prefix + "loan.open", Long.toString(account.loanOpenDay));
        properties.setProperty(prefix + "loan.maturity", Long.toString(account.loanMaturityDay));
        properties.setProperty(prefix + "loan.serial", Long.toString(account.loanSerial));
        properties.setProperty(prefix + "loan.rate", Double.toString(account.loanAnnualRate));
        properties.setProperty(prefix + "loan.stress", Double.toString(account.loanStress));
        properties.setProperty(prefix + "loan.recovery", Double.toString(account.loanRecoveryRate));
        properties.setProperty(prefix + "loan.resolved", Boolean.toString(account.loanResolved));
        properties.setProperty(prefix + "loan.outcome", account.loanOutcome.name());
        account.shares.forEach((ticker, shares) ->
                properties.setProperty(prefix + "share." + ticker, Double.toString(shares)));
    }

    private static void writeTransaction(
            Properties properties,
            UUID playerId,
            EconomyState.PendingInventoryTransaction transaction) {
        String prefix = "transaction." + playerId + ".";
        properties.setProperty(prefix + "id", transaction.transactionId.toString());
        properties.setProperty(prefix + "kind", transaction.kind.name());
        properties.setProperty(prefix + "stage", transaction.stage.name());
        properties.setProperty(prefix + "item", transaction.itemKey);
        properties.setProperty(prefix + "count", Integer.toString(transaction.itemCount));
        properties.setProperty(
                prefix + "inventory_before", Integer.toString(transaction.inventoryCountBefore));
        properties.setProperty(
                prefix + "bank_delta", Long.toString(transaction.bankDeltaMicro));
        properties.setProperty(
                prefix + "created_day", Long.toString(transaction.createdEconomicDay));
        properties.setProperty(
                prefix + "created_wall", Long.toString(transaction.createdWallClockMs));
    }

    private static EconomyState read(Path path, long fallbackSeed, long now, long ticks)
            throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }

        try {
            int format = integer(properties, "format", 1);
            if (format < 1) {
                throw new IOException("Unsupported economy save format " + format);
            }
            if (format > EconomyState.FORMAT_VERSION) {
                throw new IOException(
                        "Economy save format " + format
                                + " is newer than supported format "
                                + EconomyState.FORMAT_VERSION);
            }

            EconomyState state = new EconomyState();
            state.seed = longValue(properties, "seed", fallbackSeed);
            state.economicDay = longValue(properties, "day", 0L);
            state.lastWallClockMs = longValue(properties, "wall", now);
            state.lastGameTicks = longValue(properties, "ticks", ticks);
            if (format >= 3) {
                state.pendingWallClockMs = longValue(properties, "pending.wall_ms", 0L);
                state.pendingGameTicks = longValue(properties, "pending.game_ticks", 0L);
            }
            state.regime = EconomyEngine.Regime.valueOf(
                    properties.getProperty("regime", EconomyEngine.Regime.EXPANSION.name()));

            for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
                state.prices.put(asset.ticker(),
                        doubleValue(properties, "price." + asset.ticker(), 100.0));
            }
            for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
                state.commodityPrices.put(commodity.id(), doubleValue(
                        properties,
                        "commodity." + commodity.id(),
                        commodity.anchorPrice()));
            }

            if (format >= 2) {
                loadCurrentAccounts(state, properties);
            } else {
                loadLegacyAccounts(state, properties);
            }
            if (format >= 3) {
                loadTransactions(state, properties);
            }
            state.validate();
            return state;
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid economy save data in " + path.getFileName(), exception);
        }
    }

    private static void loadCurrentAccounts(EconomyState state, Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("account.")) {
                continue;
            }
            int uuidEnd = key.indexOf('.', "account.".length());
            if (uuidEnd < 0) {
                continue;
            }
            UUID id = UUID.fromString(key.substring("account.".length(), uuidEnd));
            String field = key.substring(uuidEnd + 1);
            applyCurrentField(state.account(id), field, properties.getProperty(key));
        }
    }

    private static void applyCurrentField(
            EconomyState.Account account,
            String field,
            String value) {
        switch (field) {
            case "cash" -> account.cashMicro = Long.parseLong(value);
            case "savings" -> account.savingsMicro = Long.parseLong(value);
            case "cd.principal" -> account.cdPrincipalMicro = Long.parseLong(value);
            case "cd.value" -> account.cdValueMicro = Long.parseLong(value);
            case "cd.open" -> account.cdOpenDay = Long.parseLong(value);
            case "cd.maturity" -> account.cdMaturityDay = Long.parseLong(value);
            case "cd.rate" -> account.cdAnnualRate = Double.parseDouble(value);
            case "loan.principal" -> account.loanPrincipalMicro = Long.parseLong(value);
            case "loan.value" -> account.loanValueMicro = Long.parseLong(value);
            case "loan.open" -> account.loanOpenDay = Long.parseLong(value);
            case "loan.maturity" -> account.loanMaturityDay = Long.parseLong(value);
            case "loan.serial" -> account.loanSerial = Long.parseLong(value);
            case "loan.rate" -> account.loanAnnualRate = Double.parseDouble(value);
            case "loan.stress" -> account.loanStress = Double.parseDouble(value);
            case "loan.recovery" -> account.loanRecoveryRate = Double.parseDouble(value);
            case "loan.resolved" -> account.loanResolved = Boolean.parseBoolean(value);
            case "loan.outcome" -> account.loanOutcome = EconomyEngine.LoanOutcome.valueOf(value);
            default -> {
                if (field.startsWith("share.")) {
                    account.shares.put(
                            field.substring("share.".length()).toUpperCase(Locale.ROOT),
                            Double.parseDouble(value));
                }
            }
        }
    }

    private static void loadTransactions(EconomyState state, Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("transaction.")) {
                continue;
            }
            int uuidEnd = key.indexOf('.', "transaction.".length());
            if (uuidEnd < 0) {
                continue;
            }
            UUID playerId = UUID.fromString(key.substring("transaction.".length(), uuidEnd));
            String field = key.substring(uuidEnd + 1);
            EconomyState.PendingInventoryTransaction transaction =
                    state.pendingInventoryTransactions.computeIfAbsent(playerId, ignored -> {
                        EconomyState.PendingInventoryTransaction value =
                                new EconomyState.PendingInventoryTransaction();
                        value.playerId = playerId;
                        return value;
                    });
            applyTransactionField(transaction, field, properties.getProperty(key));
        }
    }

    private static void applyTransactionField(
            EconomyState.PendingInventoryTransaction transaction,
            String field,
            String value) {
        switch (field) {
            case "id" -> transaction.transactionId = UUID.fromString(value);
            case "kind" -> transaction.kind =
                    EconomyState.InventoryTransactionKind.valueOf(value);
            case "stage" -> transaction.stage =
                    EconomyState.InventoryTransactionStage.valueOf(value);
            case "item" -> transaction.itemKey = value;
            case "count" -> transaction.itemCount = Integer.parseInt(value);
            case "inventory_before" -> transaction.inventoryCountBefore = Integer.parseInt(value);
            case "bank_delta" -> transaction.bankDeltaMicro = Long.parseLong(value);
            case "created_day" -> transaction.createdEconomicDay = Long.parseLong(value);
            case "created_wall" -> transaction.createdWallClockMs = Long.parseLong(value);
            default -> {
                // Ignore unknown fields from the same supported format for forward-compatible additions.
            }
        }
    }

    private static void loadLegacyAccounts(EconomyState state, Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("acct.")) {
                continue;
            }
            int uuidEnd = key.indexOf('.', "acct.".length());
            if (uuidEnd < 0) {
                continue;
            }
            UUID id = UUID.fromString(key.substring("acct.".length(), uuidEnd));
            String field = key.substring(uuidEnd + 1);
            applyLegacyField(state.account(id), field, properties.getProperty(key));
        }
        for (EconomyState.Account account : state.accounts.values()) {
            completeLegacyProducts(state, account);
        }
    }

    private static void applyLegacyField(
            EconomyState.Account account,
            String field,
            String value) {
        switch (field) {
            case "cash" -> account.cashMicro = Long.parseLong(value);
            case "savings" -> account.savingsMicro = Long.parseLong(value);
            case "cd" -> account.cdValueMicro = Long.parseLong(value);
            case "cdmat" -> account.cdMaturityDay = Long.parseLong(value);
            case "loan" -> account.loanValueMicro = Long.parseLong(value);
            case "loanmat" -> account.loanMaturityDay = Long.parseLong(value);
            default -> {
                if (field.startsWith("share.")) {
                    account.shares.put(
                            field.substring("share.".length()).toUpperCase(Locale.ROOT),
                            Double.parseDouble(value));
                }
            }
        }
    }

    private static void completeLegacyProducts(
            EconomyState state,
            EconomyState.Account account) {
        if (account.cdValueMicro > 0L) {
            account.cdPrincipalMicro = account.cdValueMicro;
            account.cdOpenDay = Math.max(0L, state.economicDay - 90L);
            account.cdMaturityDay = Math.max(state.economicDay + 1L, account.cdMaturityDay);
            account.cdAnnualRate = EconomyEngine.cdAnnualRate(state.regime, 90);
        }
        if (account.loanValueMicro > 0L) {
            account.loanPrincipalMicro = account.loanValueMicro;
            account.loanOpenDay = Math.max(0L, state.economicDay - 180L);
            account.loanMaturityDay = Math.max(state.economicDay + 1L, account.loanMaturityDay);
            account.loanAnnualRate = EconomyEngine.villagerLoanAnnualYield(state.regime, 180);
            account.loanSerial = 1L;
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }

    private static long longValue(Properties properties, String key, long fallback) {
        return Long.parseLong(properties.getProperty(key, Long.toString(fallback)));
    }

    private static double doubleValue(Properties properties, String key, double fallback) {
        return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
