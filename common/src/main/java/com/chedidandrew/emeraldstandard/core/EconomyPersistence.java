package com.chedidandrew.emeraldstandard.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/** Versioned, atomic persistence implementation for {@link EconomyState}. */
final class EconomyPersistence {
    private static final String MAGIC = "THE_EMERALD_STANDARD";
    private static final String CHECKSUM_KEY = "checksum";

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
        } catch (UnsupportedFutureFormatException futureFormat) {
            throw futureFormat;
        } catch (IOException primaryFailure) {
            if (!Files.exists(backup)) {
                throw primaryFailure;
            }
            try {
                return read(backup, fallbackSeed, now, ticks);
            } catch (UnsupportedFutureFormatException futureFormat) {
                primaryFailure.addSuppressed(futureFormat);
                throw primaryFailure;
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

        Properties properties = toProperties(state);
        properties.setProperty(CHECKSUM_KEY, checksum(properties));
        ByteArrayOutputStream output = new ByteArrayOutputStream(65_536);
        properties.store(
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
        properties.setProperty("magic", MAGIC);
        properties.setProperty("format", Integer.toString(EconomyState.FORMAT_VERSION));
        properties.setProperty("seed", Long.toString(state.seed));
        properties.setProperty("day", Long.toString(state.economicDay));
        properties.setProperty("wall", Long.toString(state.lastWallClockMs));
        properties.setProperty("ticks", Long.toString(state.lastGameTicks));
        properties.setProperty("pending.economic_ms", Long.toString(state.pendingEconomicMillis));
        properties.setProperty("regime", state.regime.name());
        properties.setProperty("event", state.lastMarketEvent.name());
        properties.setProperty("event.day", Long.toString(state.lastMarketEventDay));

        state.prices.forEach((key, value) ->
                properties.setProperty("price." + key, Double.toString(value)));
        state.commodityPrices.forEach((key, value) ->
                properties.setProperty("commodity." + key, Double.toString(value)));
        state.priceHistory.forEach((ticker, values) ->
                properties.setProperty("history." + ticker, encodeHistory(values)));
        state.generatedBankRegions.forEach(region ->
                properties.setProperty(
                        "bank.region." + Long.toUnsignedString(region, 16), "true"));
        state.generatedBankAnchors.forEach((region, anchor) ->
                properties.setProperty(
                        "bank.anchor." + Long.toUnsignedString(region, 16),
                        Long.toString(anchor)));

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
            int format = determineFormat(properties);
            if (format < 1) {
                throw new IOException("Unsupported economy save format " + format);
            }
            if (format > EconomyState.FORMAT_VERSION) {
                throw new UnsupportedFutureFormatException(
                        "Economy save format " + format
                                + " is newer than supported format "
                                + EconomyState.FORMAT_VERSION);
            }
            if (format >= 4) {
                requireValue(properties, "magic");
                if (!MAGIC.equals(properties.getProperty("magic"))) {
                    throw new IOException("Economy save magic identifier is invalid");
                }
                String expected = requireValue(properties, CHECKSUM_KEY);
                String actual = checksum(properties);
                if (!expected.equalsIgnoreCase(actual)) {
                    throw new IOException("Economy save checksum does not match");
                }
            }

            EconomyState state = new EconomyState();
            if (format >= 2) {
                state.seed = requiredLong(properties, "seed");
                state.economicDay = requiredLong(properties, "day");
                state.lastWallClockMs = requiredLong(properties, "wall");
                state.lastGameTicks = requiredLong(properties, "ticks");
                state.regime = EconomyEngine.Regime.valueOf(requireValue(properties, "regime"));
            } else {
                state.seed = longValue(properties, "seed", fallbackSeed);
                state.economicDay = longValue(properties, "day", 0L);
                state.lastWallClockMs = longValue(properties, "wall", now);
                state.lastGameTicks = longValue(properties, "ticks", ticks);
                state.regime = EconomyEngine.Regime.valueOf(
                        properties.getProperty("regime", EconomyEngine.Regime.EXPANSION.name()));
            }
            if (format >= 4) {
                state.pendingEconomicMillis = requiredLong(
                        properties, "pending.economic_ms");
            } else if (format >= 3) {
                long pendingWall = longValue(properties, "pending.wall_ms", 0L);
                long pendingTicks = longValue(properties, "pending.game_ticks", 0L);
                long pendingGameMs = pendingTicks > Long.MAX_VALUE / EconomyService.MILLIS_PER_GAME_TICK
                        ? Long.MAX_VALUE
                        : pendingTicks * EconomyService.MILLIS_PER_GAME_TICK;
                state.pendingEconomicMillis = Math.max(pendingWall, pendingGameMs);
            }
            if (format >= 5) {
                state.lastMarketEvent = EconomyEngine.MarketEvent.valueOf(
                        requireValue(properties, "event"));
                state.lastMarketEventDay = requiredLong(properties, "event.day");
            }

            for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
                double price = format >= 2
                        ? requiredDouble(properties, "price." + asset.ticker())
                        : doubleValue(properties, "price." + asset.ticker(), 100.0);
                state.prices.put(asset.ticker(), price);
                if (format >= 4) {
                    state.priceHistory.put(
                            asset.ticker(),
                            decodeHistory(requireValue(properties, "history." + asset.ticker())));
                } else {
                    state.priceHistory.put(asset.ticker(), new ArrayList<>(List.of(price)));
                }
            }
            for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
                double price = format >= 2
                        ? requiredDouble(properties, "commodity." + commodity.id())
                        : doubleValue(
                                properties,
                                "commodity." + commodity.id(),
                                commodity.anchorPrice());
                state.commodityPrices.put(commodity.id(), price);
            }
            if (format >= 4) {
                loadGeneratedBankRegions(state, properties);
            }
            if (format >= 5) {
                loadGeneratedBankAnchors(state, properties);
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

    private static int determineFormat(Properties properties) throws IOException {
        String raw = properties.getProperty("format");
        if (raw != null) {
            return Integer.parseInt(raw);
        }
        boolean legacy = properties.stringPropertyNames().stream()
                .anyMatch(key -> key.startsWith("acct."));
        if (legacy) {
            return 1;
        }
        throw new IOException("Economy save is missing its format identifier");
    }

    private static String requireValue(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Economy save is missing required field " + key);
        }
        return value;
    }

    private static long requiredLong(Properties properties, String key) throws IOException {
        return Long.parseLong(requireValue(properties, key));
    }

    private static double requiredDouble(Properties properties, String key) throws IOException {
        return Double.parseDouble(requireValue(properties, key));
    }

    private static String encodeHistory(List<Double> values) {
        StringBuilder builder = new StringBuilder(values.size() * 12);
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Double.toString(values.get(index)));
        }
        return builder.toString();
    }

    private static List<Double> decodeHistory(String encoded) throws IOException {
        String[] pieces = encoded.split(",", -1);
        if (pieces.length == 0 || pieces.length > EconomyState.HISTORY_DAYS) {
            throw new IOException("Economy price history length is invalid");
        }
        List<Double> values = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            if (piece.isBlank()) {
                throw new IOException("Economy price history contains a blank value");
            }
            values.add(Double.parseDouble(piece));
        }
        return values;
    }

    private static void loadGeneratedBankRegions(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.region.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            if (!Boolean.parseBoolean(properties.getProperty(key))) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                state.generatedBankRegions.add(Long.parseUnsignedLong(encoded, 16));
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid bank region key " + encoded, exception);
            }
        }
    }

    private static void loadGeneratedBankAnchors(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.anchor.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                long anchor = Long.parseLong(properties.getProperty(key));
                if (!state.generatedBankRegions.contains(region)) {
                    throw new IOException("Bank anchor has no matching region " + encoded);
                }
                state.generatedBankAnchors.put(region, anchor);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid bank anchor " + encoded, exception);
            }
        }
    }

    private static String checksum(Properties properties) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            TreeMap<String, String> sorted = new TreeMap<>();
            for (String key : properties.stringPropertyNames()) {
                if (!CHECKSUM_KEY.equals(key)) {
                    sorted.put(key, properties.getProperty(key));
                }
            }
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '=');
                digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class UnsupportedFutureFormatException extends IOException {
        UnsupportedFutureFormatException(String message) {
            super(message);
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
