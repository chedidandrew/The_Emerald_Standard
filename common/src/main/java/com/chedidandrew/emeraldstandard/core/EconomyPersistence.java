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
        state.bankRegionVillageIds.forEach((region, villageId) ->
                properties.setProperty(
                        "bank.village." + Long.toUnsignedString(region, 16),
                        villageId.toString()));
        state.villages.forEach((villageId, village) ->
                writeVillage(properties, villageId, village));
        state.villageMarketShadows.forEach((villageId, shadow) ->
                writeVillageMarketShadow(properties, villageId, shadow));

        for (Map.Entry<UUID, EconomyState.Account> entry : state.accounts.entrySet()) {
            writeAccount(properties, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, EconomyState.PendingInventoryTransaction> entry
                : state.pendingInventoryTransactions.entrySet()) {
            writeTransaction(properties, entry.getKey(), entry.getValue());
        }
        return properties;
    }

    private static void writeVillage(
            Properties properties,
            UUID villageId,
            EconomyState.VillageRecord village) {
        writeVillageRecord(properties, "village." + villageId + ".", village);
    }

    private static void writeVillageRecord(
            Properties properties,
            String prefix,
            EconomyState.VillageRecord village) {
        properties.setProperty(prefix + "dimension", village.dimensionKey);
        properties.setProperty(prefix + "center", Long.toString(village.centerPos));
        properties.setProperty(prefix + "bank_region", Long.toString(village.bankRegionKey));
        properties.setProperty(prefix + "bank_anchor", Long.toString(village.bankAnchorPos));
        properties.setProperty(prefix + "discovered", Long.toString(village.discoveredDay));
        properties.setProperty(prefix + "simulated", Long.toString(village.lastSimulatedDay));
        properties.setProperty(prefix + "census", Long.toString(village.lastCensusDay));
        properties.setProperty(prefix + "last_incident", Long.toString(village.lastIncidentDay));
        properties.setProperty(prefix + "recovery", Long.toString(village.recoveryEligibleDay));
        properties.setProperty(prefix + "abandoned", Long.toString(village.abandonedSinceDay));
        properties.setProperty(prefix + "last_collapse", Long.toString(village.lastCollapseDay));
        properties.setProperty(prefix + "market_suppressed", Long.toString(village.marketSuppressedUntilDay));
        properties.setProperty(prefix + "lifecycle", village.lifecycle.name());
        properties.setProperty(prefix + "incident_cause", village.lastIncidentCause.name());
        properties.setProperty(prefix + "population", Integer.toString(village.population));
        properties.setProperty(prefix + "observed_population", Integer.toString(village.observedPopulation));
        properties.setProperty(prefix + "housing", Integer.toString(village.housingCapacity));
        properties.setProperty(prefix + "pending_settlers", Integer.toString(village.pendingSettlers));
        properties.setProperty(prefix + "tier", Integer.toString(village.developmentTier));
        properties.setProperty(prefix + "collapse_count", Integer.toString(village.collapseCount));
        properties.setProperty(prefix + "casualties.hostile", Integer.toString(village.hostileCasualties));
        properties.setProperty(prefix + "casualties.player", Integer.toString(village.playerCasualties));
        properties.setProperty(prefix + "casualties.environmental", Integer.toString(village.environmentalCasualties));
        properties.setProperty(prefix + "food", Double.toString(village.foodSupply));
        properties.setProperty(prefix + "materials", Double.toString(village.materialSupply));
        properties.setProperty(prefix + "treasury", Double.toString(village.treasury));
        properties.setProperty(prefix + "prosperity", Double.toString(village.prosperity));
        properties.setProperty(prefix + "safety", Double.toString(village.safety));
        properties.setProperty(prefix + "output.agriculture", Double.toString(village.agricultureOutput));
        properties.setProperty(prefix + "output.mining", Double.toString(village.miningOutput));
        properties.setProperty(prefix + "output.trade", Double.toString(village.tradeOutput));
        properties.setProperty(prefix + "output.redstone", Double.toString(village.redstoneOutput));
        properties.setProperty(prefix + "output.alchemy", Double.toString(village.alchemyOutput));
        properties.setProperty(prefix + "output.transport", Double.toString(village.transportOutput));
        properties.setProperty(prefix + "output.security", Double.toString(village.securityOutput));
        properties.setProperty(prefix + "restoration_fund", Double.toString(village.restorationFund));
        properties.setProperty(prefix + "development_points", Double.toString(village.developmentPoints));
        properties.setProperty(prefix + "restoration_funded", Boolean.toString(village.restorationFunded));
        properties.setProperty(prefix + "project_serial", Long.toString(village.projectSerial));

        village.residents.forEach((residentId, resident) -> {
            String residentPrefix = prefix + "resident." + residentId + ".";
            properties.setProperty(residentPrefix + "profession", resident.profession);
            properties.setProperty(residentPrefix + "status", resident.status.name());
            properties.setProperty(residentPrefix + "last_seen", Long.toString(resident.lastSeenDay));
            properties.setProperty(residentPrefix + "pos", Long.toString(resident.lastKnownPos));
        });
        for (EconomyState.VillageProject project : village.projects) {
            String projectPrefix = prefix + "project." + project.projectId + ".";
            properties.setProperty(projectPrefix + "type", project.type.name());
            properties.setProperty(projectPrefix + "approved", Long.toString(project.approvedDay));
            properties.setProperty(projectPrefix + "completed", Long.toString(project.completedDay));
            properties.setProperty(projectPrefix + "progress", Double.toString(project.economicProgress));
            properties.setProperty(projectPrefix + "economic_complete", Boolean.toString(project.economicComplete));
            properties.setProperty(projectPrefix + "origin", Long.toString(project.originPos));
            properties.setProperty(projectPrefix + "bounds_min", Long.toString(project.boundsMinPos));
            properties.setProperty(projectPrefix + "bounds_max", Long.toString(project.boundsMaxPos));
            properties.setProperty(projectPrefix + "retry_after_tick", Long.toString(project.retryAfterGameTick));
            properties.setProperty(projectPrefix + "materialization_failures", Integer.toString(project.materializationFailures));
            properties.setProperty(projectPrefix + "blocks", Integer.toString(project.materializedBlocks));
            properties.setProperty(projectPrefix + "total_blocks", Integer.toString(project.totalBlocks));
            properties.setProperty(projectPrefix + "materialized_complete", Boolean.toString(project.materializedComplete));
            properties.setProperty(projectPrefix + "blocked", Boolean.toString(project.blocked));
            properties.setProperty(projectPrefix + "abstract_only", Boolean.toString(project.abstractOnly));
        }
        for (int index = 0; index < village.incidents.size(); index++) {
            EconomyState.VillageIncident incident = village.incidents.get(index);
            String incidentPrefix = prefix + "incident." + index + ".";
            properties.setProperty(incidentPrefix + "day", Long.toString(incident.day));
            properties.setProperty(incidentPrefix + "cause", incident.cause.name());
            properties.setProperty(incidentPrefix + "casualties", Integer.toString(incident.casualties));
            properties.setProperty(incidentPrefix + "player",
                    incident.responsiblePlayer == null ? "" : incident.responsiblePlayer.toString());
            properties.setProperty(incidentPrefix + "market", Boolean.toString(incident.marketEligible));
        }
    }

    private static void writeVillageMarketShadow(
            Properties properties,
            UUID villageId,
            EconomyState.VillageMarketShadow shadow) {
        String prefix = "market.shadow." + villageId + ".";
        properties.setProperty(prefix + "present", Boolean.toString(shadow.present));
        properties.setProperty(prefix + "formula_version", Integer.toString(shadow.formulaVersion));
        properties.setProperty(
                prefix + "contribution_eligible",
                Boolean.toString(shadow.contributionEligible));
        properties.setProperty(prefix + "captured_day", Long.toString(shadow.capturedDay));
        properties.setProperty(prefix + "minimum_release_day", Long.toString(shadow.minimumReleaseDay));
        properties.setProperty(prefix + "recovery_population", Integer.toString(shadow.recoveryPopulation));
        properties.setProperty(prefix + "weight", Double.toString(shadow.weight));
        properties.setProperty(prefix + "broad", Double.toString(shadow.broad));
        properties.setProperty(prefix + "mining", Double.toString(shadow.mining));
        properties.setProperty(prefix + "agriculture", Double.toString(shadow.agriculture));
        properties.setProperty(prefix + "trade", Double.toString(shadow.trade));
        properties.setProperty(prefix + "redstone", Double.toString(shadow.redstone));
        properties.setProperty(prefix + "alchemy", Double.toString(shadow.alchemy));
        properties.setProperty(prefix + "transport", Double.toString(shadow.transport));
        properties.setProperty(prefix + "security", Double.toString(shadow.security));
        writeVillageRecord(
                properties,
                prefix + "counterfactual.",
                shadow.counterfactualVillage);
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
            if (format >= 6) {
                loadVillages(state, properties);
                loadBankVillageAssociations(state, properties);
            }
            if (format >= 7) {
                loadVillageMarketShadows(state, properties);
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

    private static void loadVillages(EconomyState state, Properties properties)
            throws IOException {
        Map<UUID, Map<Long, EconomyState.VillageProject>> projects = new TreeMap<>();
        Map<UUID, Map<Integer, EconomyState.VillageIncident>> incidents = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("village.")) {
                continue;
            }
            int uuidEnd = key.indexOf('.', "village.".length());
            if (uuidEnd < 0) {
                throw new IOException("Invalid village property " + key);
            }
            UUID villageId = UUID.fromString(key.substring("village.".length(), uuidEnd));
            String field = key.substring(uuidEnd + 1);
            EconomyState.VillageRecord village = state.village(villageId);
            village.villageId = villageId;
            String value = properties.getProperty(key);
            if (field.startsWith("resident.")) {
                applyResidentField(village, field, value);
            } else if (field.startsWith("project.")) {
                applyProjectField(projects, villageId, field, value);
            } else if (field.startsWith("incident.")) {
                applyIncidentField(incidents, villageId, field, value);
            } else {
                applyVillageField(village, field, value);
            }
        }
        for (Map.Entry<UUID, Map<Long, EconomyState.VillageProject>> entry : projects.entrySet()) {
            EconomyState.VillageRecord village = state.villages.get(entry.getKey());
            if (village != null) {
                village.projects.addAll(entry.getValue().values());
            }
        }
        for (Map.Entry<UUID, Map<Integer, EconomyState.VillageIncident>> entry : incidents.entrySet()) {
            EconomyState.VillageRecord village = state.villages.get(entry.getKey());
            if (village != null) {
                village.incidents.addAll(entry.getValue().values());
            }
        }
    }

    private static void loadVillageMarketShadows(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "market.shadow.";
        Map<UUID, Map<Long, EconomyState.VillageProject>> projects = new TreeMap<>();
        Map<UUID, Map<Integer, EconomyState.VillageIncident>> incidents = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            int uuidEnd = key.indexOf('.', prefix.length());
            if (uuidEnd < 0) {
                throw new IOException("Invalid village market-shadow property " + key);
            }
            UUID villageId = UUID.fromString(key.substring(prefix.length(), uuidEnd));
            String field = key.substring(uuidEnd + 1);
            EconomyState.VillageMarketShadow shadow = state.villageMarketShadows.computeIfAbsent(
                    villageId, ignored -> new EconomyState.VillageMarketShadow());
            String value = properties.getProperty(key);
            if (field.startsWith("counterfactual.")) {
                String villageField = field.substring("counterfactual.".length());
                if (shadow.counterfactualVillage == null) {
                    shadow.counterfactualVillage = new EconomyState.VillageRecord();
                    shadow.counterfactualVillage.villageId = villageId;
                }
                if (villageField.startsWith("resident.")) {
                    applyResidentField(shadow.counterfactualVillage, villageField, value);
                } else if (villageField.startsWith("project.")) {
                    applyProjectField(projects, villageId, villageField, value);
                } else if (villageField.startsWith("incident.")) {
                    applyIncidentField(incidents, villageId, villageField, value);
                } else {
                    applyVillageField(shadow.counterfactualVillage, villageField, value);
                }
            } else {
                applyVillageMarketShadowField(shadow, field, value);
            }
        }
        for (Map.Entry<UUID, Map<Long, EconomyState.VillageProject>> entry : projects.entrySet()) {
            EconomyState.VillageMarketShadow shadow = state.villageMarketShadows.get(entry.getKey());
            if (shadow != null && shadow.counterfactualVillage != null) {
                shadow.counterfactualVillage.projects.addAll(entry.getValue().values());
            }
        }
        for (Map.Entry<UUID, Map<Integer, EconomyState.VillageIncident>> entry : incidents.entrySet()) {
            EconomyState.VillageMarketShadow shadow = state.villageMarketShadows.get(entry.getKey());
            if (shadow != null && shadow.counterfactualVillage != null) {
                shadow.counterfactualVillage.incidents.addAll(entry.getValue().values());
            }
        }
    }

    private static void applyVillageMarketShadowField(
            EconomyState.VillageMarketShadow shadow, String field, String value) {
        switch (field) {
            case "present" -> shadow.present = Boolean.parseBoolean(value);
            case "formula_version" -> shadow.formulaVersion = Integer.parseInt(value);
            case "contribution_eligible" ->
                    shadow.contributionEligible = Boolean.parseBoolean(value);
            case "captured_day" -> shadow.capturedDay = Long.parseLong(value);
            case "minimum_release_day" -> shadow.minimumReleaseDay = Long.parseLong(value);
            case "recovery_population" -> shadow.recoveryPopulation = Integer.parseInt(value);
            case "weight" -> shadow.weight = Double.parseDouble(value);
            case "broad" -> shadow.broad = Double.parseDouble(value);
            case "mining" -> shadow.mining = Double.parseDouble(value);
            case "agriculture" -> shadow.agriculture = Double.parseDouble(value);
            case "trade" -> shadow.trade = Double.parseDouble(value);
            case "redstone" -> shadow.redstone = Double.parseDouble(value);
            case "alchemy" -> shadow.alchemy = Double.parseDouble(value);
            case "transport" -> shadow.transport = Double.parseDouble(value);
            case "security" -> shadow.security = Double.parseDouble(value);
            default -> {
                // Ignore unknown optional fields from the current save format.
            }
        }
    }

    private static void applyVillageField(
            EconomyState.VillageRecord village, String field, String value) {
        switch (field) {
            case "dimension" -> village.dimensionKey = value;
            case "center" -> village.centerPos = Long.parseLong(value);
            case "bank_region" -> village.bankRegionKey = Long.parseLong(value);
            case "bank_anchor" -> village.bankAnchorPos = Long.parseLong(value);
            case "discovered" -> village.discoveredDay = Long.parseLong(value);
            case "simulated" -> village.lastSimulatedDay = Long.parseLong(value);
            case "census" -> village.lastCensusDay = Long.parseLong(value);
            case "last_incident" -> village.lastIncidentDay = Long.parseLong(value);
            case "recovery" -> village.recoveryEligibleDay = Long.parseLong(value);
            case "abandoned" -> village.abandonedSinceDay = Long.parseLong(value);
            case "last_collapse" -> village.lastCollapseDay = Long.parseLong(value);
            case "market_suppressed" -> village.marketSuppressedUntilDay = Long.parseLong(value);
            case "lifecycle" -> village.lifecycle = VillageProsperityEngine.Lifecycle.valueOf(value);
            case "incident_cause" -> village.lastIncidentCause =
                    VillageProsperityEngine.IncidentCause.valueOf(value);
            case "population" -> village.population = Integer.parseInt(value);
            case "observed_population" -> village.observedPopulation = Integer.parseInt(value);
            case "housing" -> village.housingCapacity = Integer.parseInt(value);
            case "pending_settlers" -> village.pendingSettlers = Integer.parseInt(value);
            case "tier" -> village.developmentTier = Integer.parseInt(value);
            case "collapse_count" -> village.collapseCount = Integer.parseInt(value);
            case "casualties.hostile" -> village.hostileCasualties = Integer.parseInt(value);
            case "casualties.player" -> village.playerCasualties = Integer.parseInt(value);
            case "casualties.environmental" -> village.environmentalCasualties = Integer.parseInt(value);
            case "food" -> village.foodSupply = Double.parseDouble(value);
            case "materials" -> village.materialSupply = Double.parseDouble(value);
            case "treasury" -> village.treasury = Double.parseDouble(value);
            case "prosperity" -> village.prosperity = Double.parseDouble(value);
            case "safety" -> village.safety = Double.parseDouble(value);
            case "output.agriculture" -> village.agricultureOutput = Double.parseDouble(value);
            case "output.mining" -> village.miningOutput = Double.parseDouble(value);
            case "output.trade" -> village.tradeOutput = Double.parseDouble(value);
            case "output.redstone" -> village.redstoneOutput = Double.parseDouble(value);
            case "output.alchemy" -> village.alchemyOutput = Double.parseDouble(value);
            case "output.transport" -> village.transportOutput = Double.parseDouble(value);
            case "output.security" -> village.securityOutput = Double.parseDouble(value);
            case "restoration_fund" -> village.restorationFund = Double.parseDouble(value);
            case "development_points" -> village.developmentPoints = Double.parseDouble(value);
            case "restoration_funded" -> village.restorationFunded = Boolean.parseBoolean(value);
            case "project_serial" -> village.projectSerial = Long.parseLong(value);
            default -> {
                // Ignore unknown fields from this supported format.
            }
        }
    }

    private static void applyResidentField(
            EconomyState.VillageRecord village, String field, String value) {
        String remainder = field.substring("resident.".length());
        int uuidEnd = remainder.indexOf('.');
        if (uuidEnd < 0) {
            throw new IllegalArgumentException("Invalid resident property " + field);
        }
        UUID residentId = UUID.fromString(remainder.substring(0, uuidEnd));
        String residentField = remainder.substring(uuidEnd + 1);
        EconomyState.ResidentRecord resident = village.residents.computeIfAbsent(
                residentId, ignored -> {
                    EconomyState.ResidentRecord record = new EconomyState.ResidentRecord();
                    record.residentId = residentId;
                    return record;
                });
        switch (residentField) {
            case "profession" -> resident.profession = value;
            case "status" -> resident.status = VillageProsperityEngine.ResidentStatus.valueOf(value);
            case "last_seen" -> resident.lastSeenDay = Long.parseLong(value);
            case "pos" -> resident.lastKnownPos = Long.parseLong(value);
            default -> {
            }
        }
    }

    private static void applyProjectField(
            Map<UUID, Map<Long, EconomyState.VillageProject>> all,
            UUID villageId,
            String field,
            String value) {
        String remainder = field.substring("project.".length());
        int idEnd = remainder.indexOf('.');
        if (idEnd < 0) {
            throw new IllegalArgumentException("Invalid project property " + field);
        }
        long projectId = Long.parseLong(remainder.substring(0, idEnd));
        String projectField = remainder.substring(idEnd + 1);
        EconomyState.VillageProject project = all
                .computeIfAbsent(villageId, ignored -> new TreeMap<>())
                .computeIfAbsent(projectId, ignored -> {
                    EconomyState.VillageProject created = new EconomyState.VillageProject();
                    created.projectId = projectId;
                    return created;
                });
        switch (projectField) {
            case "type" -> project.type = VillageProsperityEngine.ProjectType.valueOf(value);
            case "approved" -> project.approvedDay = Long.parseLong(value);
            case "completed" -> project.completedDay = Long.parseLong(value);
            case "progress" -> project.economicProgress = Double.parseDouble(value);
            case "economic_complete" -> project.economicComplete = Boolean.parseBoolean(value);
            case "origin" -> project.originPos = Long.parseLong(value);
            case "bounds_min" -> project.boundsMinPos = Long.parseLong(value);
            case "bounds_max" -> project.boundsMaxPos = Long.parseLong(value);
            case "retry_after_tick" -> project.retryAfterGameTick = Long.parseLong(value);
            case "materialization_failures" -> project.materializationFailures = Integer.parseInt(value);
            case "blocks" -> project.materializedBlocks = Integer.parseInt(value);
            case "total_blocks" -> project.totalBlocks = Integer.parseInt(value);
            case "materialized_complete" -> project.materializedComplete = Boolean.parseBoolean(value);
            case "blocked" -> project.blocked = Boolean.parseBoolean(value);
            case "abstract_only" -> project.abstractOnly = Boolean.parseBoolean(value);
            default -> {
            }
        }
    }

    private static void applyIncidentField(
            Map<UUID, Map<Integer, EconomyState.VillageIncident>> all,
            UUID villageId,
            String field,
            String value) {
        String remainder = field.substring("incident.".length());
        int indexEnd = remainder.indexOf('.');
        if (indexEnd < 0) {
            throw new IllegalArgumentException("Invalid incident property " + field);
        }
        int index = Integer.parseInt(remainder.substring(0, indexEnd));
        String incidentField = remainder.substring(indexEnd + 1);
        EconomyState.VillageIncident incident = all
                .computeIfAbsent(villageId, ignored -> new TreeMap<>())
                .computeIfAbsent(index, ignored -> new EconomyState.VillageIncident());
        switch (incidentField) {
            case "day" -> incident.day = Long.parseLong(value);
            case "cause" -> incident.cause = VillageProsperityEngine.IncidentCause.valueOf(value);
            case "casualties" -> incident.casualties = Integer.parseInt(value);
            case "player" -> incident.responsiblePlayer = value.isBlank() ? null : UUID.fromString(value);
            case "market" -> incident.marketEligible = Boolean.parseBoolean(value);
            default -> {
            }
        }
    }

    private static void loadBankVillageAssociations(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.village.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                UUID villageId = UUID.fromString(properties.getProperty(key));
                state.bankRegionVillageIds.put(region, villageId);
            } catch (RuntimeException exception) {
                throw new IOException("Invalid bank-village association " + encoded, exception);
            }
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
