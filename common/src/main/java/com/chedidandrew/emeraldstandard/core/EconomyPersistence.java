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
import java.security.DigestInputStream;
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

    static EconomyState load(
            Path path,
            long fallbackSeed,
            long now,
            long ticks,
            long overworldClockTicks)
            throws IOException {
        Path backup = backupPath(path);
        if (!Files.exists(path)) {
            return Files.exists(backup)
                    ? read(backup, fallbackSeed, now, ticks, overworldClockTicks)
                    : EconomyState.fresh(
                            fallbackSeed, now, ticks, overworldClockTicks);
        }
        try {
            return read(path, fallbackSeed, now, ticks, overworldClockTicks);
        } catch (UnsupportedFutureFormatException futureFormat) {
            throw futureFormat;
        } catch (IOException primaryFailure) {
            if (!Files.exists(backup)) {
                throw primaryFailure;
            }
            try {
                return read(backup, fallbackSeed, now, ticks, overworldClockTicks);
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
        byte[] serialized = output.toByteArray();
        byte[] persistedFingerprint = rawFingerprint(serialized);
        ByteBuffer buffer = ByteBuffer.wrap(serialized);
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
        state.rememberPersistedFile(path, persistedFingerprint);
    }

    private static void preserveValidPrimary(EconomyState state, Path path) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return;
        }
        try {
            boolean knownValid = state.isKnownPersistedFile(path)
                    && state.matchesKnownPersistedFile(path, rawFingerprint(path));
            if (!knownValid) {
                read(
                        path,
                        state.seed,
                        state.lastWallClockMs,
                        state.lastGameTicks,
                        state.lastOverworldClockTicks);
            }
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

    private static Properties toProperties(EconomyState state) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("magic", MAGIC);
        properties.setProperty("format", Integer.toString(EconomyState.FORMAT_VERSION));
        for (var entry : state.pendingBankConstructions.entrySet())
            properties.setProperty("bank_build." + entry.getKey(), entry.getValue().encode());
        properties.setProperty("seed", Long.toString(state.seed));
        properties.setProperty("day", Long.toString(state.economicDay));
        properties.setProperty("wall", Long.toString(state.lastWallClockMs));
        properties.setProperty("ticks", Long.toString(state.lastGameTicks));
        properties.setProperty(
                "overworld.clock_ticks",
                Long.toString(state.lastOverworldClockTicks));
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
        state.commodityHistory.forEach((commodity, values) ->
                properties.setProperty("commodity.history." + commodity, encodeHistory(values)));
        state.generatedBankRegions.forEach(region ->
                properties.setProperty(
                        "bank.region." + Long.toUnsignedString(region, 16), "true"));
        state.generatedBankAnchors.forEach((region, anchor) ->
                properties.setProperty(
                        "bank.anchor." + Long.toUnsignedString(region, 16),
                        Long.toString(anchor)));
        state.bankStructureVersions.forEach((region, version) ->
                properties.setProperty(
                        "bank.structure_version." + Long.toUnsignedString(region, 16),
                        Integer.toString(version)));
        state.retiredBankAnchors.forEach((region, anchors) ->
                properties.setProperty(
                        "bank.retired_anchors." + Long.toUnsignedString(region, 16),
                        encodeLongList(anchors)));
        state.fallbackBankRegions.forEach(region ->
                properties.setProperty(
                        "bank.fallback." + Long.toUnsignedString(region, 16), "true"));
        state.bankRegionVillageIds.forEach((region, villageId) ->
                properties.setProperty(
                        "bank.village." + Long.toUnsignedString(region, 16),
                        villageId.toString()));
        state.bankRegionBankerIds.forEach((region, bankerId) ->
                properties.setProperty(
                        "bank.banker." + Long.toUnsignedString(region, 16),
                        bankerId.toString()));
        state.bankRegionBankerAnchors.forEach((region, anchor) ->
                properties.setProperty(
                        "bank.banker_anchor." + Long.toUnsignedString(region, 16),
                        Long.toString(anchor)));
        state.pendingBankerDeaths.forEach((region, bankerId) ->
                properties.setProperty(
                        "bank.banker_death." + Long.toUnsignedString(region, 16),
                        bankerId.toString()));
        state.pendingBankerConversions.forEach((region, conversion) ->
                properties.setProperty(
                        "bank.banker_conversion." + Long.toUnsignedString(region, 16),
                        conversion.rootCanonicalId
                                + "|" + conversion.immediateSourceId
                                + "|" + conversion.targetId
                                + "|" + conversion.targetDimension
                                + "|" + conversion.targetPos
                                + "|" + conversion.phase
                                + "|" + conversion.disposition));
        state.villages.forEach((villageId, village) ->
                writeVillage(properties, villageId, village));
        state.villageMarketShadows.forEach((villageId, shadow) ->
                writeVillageMarketShadow(properties, villageId, shadow));

        for (Map.Entry<UUID, EconomyState.Account> entry : state.accounts.entrySet()) {
            writeAccount(properties, entry.getKey(), entry.getValue());
        }
        state.donors.forEach((donorId, donor) -> writeDonor(properties, donorId, donor));
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
        properties.setProperty(
                prefix + "observed_housing",
                Integer.toString(village.observedHousingCapacity));
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
        if (village.cityId != null) properties.setProperty(prefix + "city_id", village.cityId.toString());
        properties.setProperty(prefix + "expansion_mode", village.expansionMode.name());
        properties.setProperty(prefix + "expansion_approved", Boolean.toString(village.expansionApproved));
        properties.setProperty(prefix + "district_founding", Boolean.toString(village.districtFounding));
        properties.setProperty(prefix + "expansion_healthy_days", Integer.toString(village.expansionHealthyDays));
        properties.setProperty(prefix + "last_expansion_day", Long.toString(village.lastExpansionDay));
        properties.setProperty(prefix + "expansion_serial", Long.toString(village.expansionSerial));
        properties.setProperty(prefix + "expansion_site_cursor", Long.toString(village.expansionSiteCursor));
        properties.setProperty(prefix + "expansion_upkeep_shortfalls", Integer.toString(village.expansionUpkeepShortfalls));
        properties.setProperty(prefix + "lighting_coverage_percent", Integer.toString(village.lightingCoveragePercent));
        properties.setProperty(prefix + "last_lighting_day", Long.toString(village.lastLightingDay));
        properties.setProperty(prefix + "food_sources.crops", Double.toString(village.observedCropUnits));
        properties.setProperty(prefix + "food_sources.livestock", Double.toString(village.observedLivestockUnits));
        properties.setProperty(prefix + "food_sources.day", Long.toString(village.lastFoodSourcesDay));
        properties.setProperty(
                prefix + "visual_project_selection_cursor",
                Long.toString(village.visualProjectSelectionCursor));
        properties.setProperty(prefix + "architecture.character", village.architectureCharacter);
        properties.setProperty(prefix + "architecture.dialect", village.architectureDialect);
        writeProsperityFund(properties, prefix + "fund.", village.prosperityFund);

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
            properties.setProperty(
                    projectPrefix + "site_search_cursor",
                    Integer.toString(project.siteSearchCursor));
            properties.setProperty(
                    projectPrefix + "site_search_saw_unloaded",
                    Boolean.toString(project.siteSearchSawUnloadedCandidate));
            properties.setProperty(projectPrefix + "blocks", Integer.toString(project.materializedBlocks));
            properties.setProperty(projectPrefix + "total_blocks", Integer.toString(project.totalBlocks));
            properties.setProperty(projectPrefix + "materialized_complete", Boolean.toString(project.materializedComplete));
            properties.setProperty(projectPrefix + "blocked", Boolean.toString(project.blocked));
            properties.setProperty(projectPrefix + "manual_repair_required", Boolean.toString(project.manualRepairRequired));
            properties.setProperty(projectPrefix + "relocation_pending", Boolean.toString(project.relocationPending));
            if (!project.retiredLots.isEmpty()) {
                properties.setProperty(
                        projectPrefix + "retired_bounds",
                        encodeRetiredProjectLots(project.retiredLots));
            }
            properties.setProperty(projectPrefix + "abstract_only", Boolean.toString(project.abstractOnly));
            properties.setProperty(projectPrefix + "design.schema", project.designSchema);
            properties.setProperty(projectPrefix + "design.seed", Long.toString(project.designSeed));
            properties.setProperty(projectPrefix + "design.silhouette", Integer.toString(project.designSilhouette));
            properties.setProperty(projectPrefix + "design.roof", Integer.toString(project.designRoof));
            properties.setProperty(projectPrefix + "design.frontage", Integer.toString(project.designFrontage));
            properties.setProperty(projectPrefix + "design.mirrored", Boolean.toString(project.designMirrored));
            properties.setProperty(projectPrefix + "design.rotation", Integer.toString(project.designRotation));
            properties.setProperty(projectPrefix + "design.signature", Long.toString(project.designSignature));
            properties.setProperty(projectPrefix + "design.stage", Integer.toString(project.designStage));
            properties.setProperty(
                    projectPrefix + "design.quality_stage",
                    Integer.toString(project.designQualityStage));
            properties.setProperty(
                    projectPrefix + "design.template_id",
                    project.designTemplateId);
            properties.setProperty(
                    projectPrefix + "design.template_revision",
                    Integer.toString(project.designTemplateRevision));
            properties.setProperty(
                    projectPrefix + "design.palette_id",
                    project.designPaletteId);
            properties.setProperty(
                    projectPrefix + "design.dressing_id",
                    project.designDressingId);
            properties.setProperty(
                    projectPrefix + "design.plan_hash_version",
                    Integer.toString(project.designPlanHashVersion));
            properties.setProperty(
                    projectPrefix + "design.plan_hash",
                    project.designPlanHash);
            properties.setProperty(projectPrefix + "site_preparation_complete", Boolean.toString(project.sitePreparationComplete));
            properties.setProperty(projectPrefix + "site_preparation_cursor", Integer.toString(project.sitePreparationCursor));
            properties.setProperty(projectPrefix + "construction_started", Boolean.toString(project.constructionStarted));
            if (project.sitePreparationPlan != null)
                properties.setProperty(projectPrefix + "site_preparation_plan", project.sitePreparationPlan.encode());
            properties.setProperty(projectPrefix + "trail.anchor_set", Boolean.toString(project.trailAnchorSet));
            properties.setProperty(projectPrefix + "trail.anchor", Long.toString(project.trailAnchorPos));
            properties.setProperty(projectPrefix + "trail.blocks", Integer.toString(project.trailMaterializedBlocks));
            properties.setProperty(projectPrefix + "trail.total_blocks", Integer.toString(project.trailTotalBlocks));
            properties.setProperty(projectPrefix + "trail.complete", Boolean.toString(project.trailMaterializedComplete));
            properties.setProperty(
                    projectPrefix + "trail.center_surface_version",
                    Integer.toString(project.trailCenterSurfaceVersion));
            properties.setProperty(
                    projectPrefix + "trail.center_surface_cursor",
                    Integer.toString(project.trailCenterSurfaceMigrationCursor));
            properties.setProperty(
                    projectPrefix + "trail.center_surface_total_cells",
                    Integer.toString(project.trailCenterSurfaceMigrationTotalCells));
            properties.setProperty(
                    projectPrefix + "entrance.approach_version",
                    Integer.toString(project.entranceApproachVersion));
            properties.setProperty(
                    projectPrefix + "entrance.approach_step_count",
                    Integer.toString(project.entranceApproachStepCount));
            properties.setProperty(
                    projectPrefix + "entrance.approach_cursor",
                    Integer.toString(project.entranceApproachCursor));
            properties.setProperty(
                    projectPrefix + "entrance.approach_total_cells",
                    Integer.toString(project.entranceApproachTotalCells));
            properties.setProperty(
                    projectPrefix + "entrance.approach_complete",
                    Boolean.toString(project.entranceApproachComplete));
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
        properties.setProperty(
                prefix + "position.next", Long.toString(account.nextTermPositionId));
        account.shares.forEach((ticker, shares) ->
                properties.setProperty(prefix + "share." + ticker, Double.toString(shares)));
        account.cdPositions.forEach((positionId, position) -> {
            String positionPrefix = prefix + "cdpos." + positionId + ".";
            properties.setProperty(positionPrefix + "principal", Long.toString(position.principalMicro));
            properties.setProperty(positionPrefix + "value", Long.toString(position.valueMicro));
            properties.setProperty(positionPrefix + "open", Long.toString(position.openDay));
            properties.setProperty(positionPrefix + "maturity", Long.toString(position.maturityDay));
            properties.setProperty(positionPrefix + "rate", Double.toString(position.annualRate));
        });
        account.loanPositions.forEach((positionId, position) -> {
            String positionPrefix = prefix + "loanpos." + positionId + ".";
            properties.setProperty(positionPrefix + "principal", Long.toString(position.principalMicro));
            properties.setProperty(positionPrefix + "value", Long.toString(position.valueMicro));
            properties.setProperty(positionPrefix + "open", Long.toString(position.openDay));
            properties.setProperty(positionPrefix + "maturity", Long.toString(position.maturityDay));
            properties.setProperty(positionPrefix + "serial", Long.toString(position.serial));
            properties.setProperty(positionPrefix + "rate", Double.toString(position.annualRate));
            properties.setProperty(positionPrefix + "stress", Double.toString(position.stress));
            properties.setProperty(positionPrefix + "recovery", Double.toString(position.recoveryRate));
            properties.setProperty(positionPrefix + "resolved", Boolean.toString(position.resolved));
            properties.setProperty(positionPrefix + "outcome", position.outcome.name());
        });
        account.shareCostBasisMicro.forEach((ticker, basis) ->
                properties.setProperty(prefix + "portfolio.basis." + ticker, Long.toString(basis)));
        properties.setProperty(prefix + "portfolio.realized", Long.toString(account.realizedGainMicro));
        properties.setProperty(prefix + "portfolio.contributions", Long.toString(account.totalContributionsMicro));
        properties.setProperty(prefix + "portfolio.withdrawals", Long.toString(account.totalWithdrawalsMicro));
        properties.setProperty(prefix + "portfolio.inferred", Boolean.toString(account.costBasisInferred));
        for (int index = 0; index < account.transactionLedger.size(); index++) {
            EconomyState.PortfolioTransaction transaction = account.transactionLedger.get(index);
            String transactionPrefix = prefix + "portfolio.ledger." + index + ".";
            properties.setProperty(transactionPrefix + "day", Long.toString(transaction.day));
            properties.setProperty(transactionPrefix + "kind", transaction.kind.name());
            properties.setProperty(transactionPrefix + "symbol", transaction.symbol);
            properties.setProperty(transactionPrefix + "reference", Long.toString(transaction.referenceId));
            properties.setProperty(transactionPrefix + "quantity", Double.toString(transaction.quantity));
            properties.setProperty(transactionPrefix + "amount", Long.toString(transaction.amountMicro));
            properties.setProperty(transactionPrefix + "basis", Long.toString(transaction.costBasisMicro));
            properties.setProperty(transactionPrefix + "realized", Long.toString(transaction.realizedGainMicro));
        }
        for (int index = 0; index < account.netWorthHistory.size(); index++) {
            EconomyState.PortfolioValuePoint point = account.netWorthHistory.get(index);
            String historyPrefix = prefix + "portfolio.history." + index + ".";
            properties.setProperty(historyPrefix + "day", Long.toString(point.day));
            properties.setProperty(historyPrefix + "value", Long.toString(point.valueMicro));
        }
    }

    private static void writeProsperityFund(
            Properties properties, String prefix, EconomyState.ProsperityFund fund) {
        properties.setProperty(prefix + "reserve", Long.toString(fund.emergencyReserveMicro));
        properties.setProperty(prefix + "lifetime_received", Long.toString(fund.lifetimeReceivedMicro));
        properties.setProperty(prefix + "lifetime_spent", Long.toString(fund.lifetimeSpentMicro));
        properties.setProperty(prefix + "last_spending_day", Long.toString(fund.lastSpendingDay));
        properties.setProperty(prefix + "spent_today", Long.toString(fund.spentTodayMicro));
        properties.setProperty(prefix + "fast_track_known", "true");
        fund.spendableMicro.forEach((purpose, amount) ->
                properties.setProperty(prefix + "spendable." + purpose.name(), Long.toString(amount)));
        fund.fastTrackSpendableMicro.forEach((purpose, amount) ->
                properties.setProperty(
                        prefix + "fast_track_spendable." + purpose.name(),
                        Long.toString(amount)));
        fund.endowmentPrincipalMicro.forEach((purpose, amount) ->
                properties.setProperty(prefix + "endowment." + purpose.name(), Long.toString(amount)));
        fund.projectSponsorshipMicro.forEach((projectId, amount) ->
                properties.setProperty(prefix + "sponsorship." + projectId, Long.toString(amount)));
        fund.donorTotalsMicro.forEach((donorId, amount) ->
                properties.setProperty(prefix + "donor." + donorId, Long.toString(amount)));
        for (int index = 0; index < fund.contributions.size(); index++) {
            EconomyState.FundContribution contribution = fund.contributions.get(index);
            String contributionPrefix = prefix + "contribution." + index + ".";
            properties.setProperty(contributionPrefix + "day", Long.toString(contribution.day));
            properties.setProperty(contributionPrefix + "donor", contribution.donorId.toString());
            properties.setProperty(contributionPrefix + "type", contribution.type.name());
            properties.setProperty(contributionPrefix + "purpose", contribution.purpose.name());
            properties.setProperty(contributionPrefix + "project", Long.toString(contribution.projectId));
            properties.setProperty(contributionPrefix + "amount", Long.toString(contribution.amountMicro));
        }
    }

    private static void writeDonor(
            Properties properties, UUID donorId, EconomyState.DonorRecord donor) {
        String prefix = "donor." + donorId + ".";
        properties.setProperty(prefix + "lifetime", Long.toString(donor.lifetimeContributionMicro));
        properties.setProperty(prefix + "count", Integer.toString(donor.contributionCount));
        donor.byTypeMicro.forEach((type, amount) ->
                properties.setProperty(prefix + "type." + type.name(), Long.toString(amount)));
        donor.byPurposeMicro.forEach((purpose, amount) ->
                properties.setProperty(prefix + "purpose." + purpose.name(), Long.toString(amount)));
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

    private static EconomyState read(
            Path path,
            long fallbackSeed,
            long now,
            long ticks,
            long overworldClockTicks)
            throws IOException {
        Properties properties = new Properties();
        MessageDigest persistedDigest = newSha256Digest();
        try (InputStream input = new DigestInputStream(
                Files.newInputStream(path), persistedDigest)) {
            properties.load(input);
        }
        byte[] persistedFingerprint = persistedDigest.digest();

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
                // Early format-17 development saves did not yet carry this baseline. Initialize
                // those and all older formats from the live world clock to avoid replaying an
                // unknowable historical jump; every subsequent observation is persisted.
                state.lastOverworldClockTicks = format >= 17
                        ? longValue(
                                properties,
                                "overworld.clock_ticks",
                                Math.max(0L, overworldClockTicks))
                        : Math.max(0L, overworldClockTicks);
                state.regime = EconomyEngine.Regime.valueOf(requireValue(properties, "regime"));
            } else {
                state.seed = longValue(properties, "seed", fallbackSeed);
                state.economicDay = longValue(properties, "day", 0L);
                state.lastWallClockMs = longValue(properties, "wall", now);
                state.lastGameTicks = longValue(properties, "ticks", ticks);
                state.lastOverworldClockTicks = Math.max(0L, overworldClockTicks);
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
                if (format >= 9) {
                    state.commodityHistory.put(
                            commodity.id(),
                            decodeHistory(requireValue(
                                    properties, "commodity.history." + commodity.id())));
                } else {
                    state.commodityHistory.put(
                            commodity.id(), new ArrayList<>(List.of(price)));
                }
            }
            if (format >= 4) {
                loadGeneratedBankRegions(state, properties);
            }
            if (format >= 5) {
                loadGeneratedBankAnchors(state, properties);
            }
            if (format >= 11) {
                loadFallbackBankRegions(state, properties);
            }
            if (format >= 12) {
                loadBankStructureVersions(state, properties);
            }
            if (format >= 13) {
                loadRetiredBankAnchors(state, properties);
                loadBankerEntityIds(state, properties);
                loadBankerAssignedAnchors(state, properties);
            }
            if (format >= 17) {
                // Format-16 death observations preceded Minecraft's entity-removal save barrier.
                // Importing one could resurrect the old entity remotely while also authorizing a
                // replacement, so migration deliberately keeps the canonical UUID fail-closed.
                loadPendingBankerDeaths(state, properties);
            }
            if (format >= 17) {
                loadPendingBankerConversions(state, properties);
            }
            if (format >= 6) {
                loadVillages(state, properties, format);
                loadBankVillageAssociations(state, properties);
            }
            if (format >= 7) {
                loadVillageMarketShadows(state, properties, format);
            }
            if (format >= 21) {
                for (String key : properties.stringPropertyNames()) {
                    if (key.startsWith("bank_build."))
                        state.pendingBankConstructions.put(Long.parseLong(key.substring(11)),
                                BankConstruction.decode(properties.getProperty(key)));
                }
            }

            if (format >= 2) {
                loadCurrentAccounts(state, properties);
            } else {
                loadLegacyAccounts(state, properties);
            }
            if (format >= 3) {
                loadTransactions(state, properties);
            }
            if (format >= 9) {
                loadDonors(state, properties);
            }
            for (EconomyState.Account account : state.accounts.values()) {
                EconomyState.ensurePositionCollections(account);
                PortfolioAnalytics.migrateLegacyBasis(account, state);
            }
            state.validate();
            state.rememberPersistedFile(path, persistedFingerprint);
            return state;
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid economy save data in " + path.getFileName(), exception);
        }
    }

    private static void loadVillages(
            EconomyState state, Properties properties, int format)
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
                applyProjectField(projects, villageId, field, value, format);
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
        state.villages.values().forEach(village -> {
            normalizeArchitecture(village, format);
            normalizeTrailCenterSurfaceMigration(village, format);
            normalizeHousingAccounting(village, format);
            normalizeProsperityFund(village.prosperityFund);
        });
    }

    private static void loadVillageMarketShadows(
            EconomyState state, Properties properties, int format) throws IOException {
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
                    applyProjectField(projects, villageId, villageField, value, format);
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
        state.villageMarketShadows.values().stream()
                .map(shadow -> shadow.counterfactualVillage)
                .filter(java.util.Objects::nonNull)
                .forEach(village -> {
                    normalizeArchitecture(village, format);
                    normalizeTrailCenterSurfaceMigration(village, format);
                    normalizeHousingAccounting(village, format);
                    normalizeProsperityFund(village.prosperityFund);
                });
    }

    private static void normalizeProsperityFund(EconomyState.ProsperityFund fund) {
        if (fund == null || fund.fastTrackProvenanceKnown) {
            return;
        }
        fund.fastTrackSpendableMicro.clear();
        long unavailable = fund.lifetimeSpentMicro > Long.MAX_VALUE - fund.emergencyReserveMicro
                ? Long.MAX_VALUE
                : fund.lifetimeSpentMicro + fund.emergencyReserveMicro;
        for (EconomyState.DonationPurpose purpose : EconomyState.DonationPurpose.values()) {
            long retainedDirectGrant = 0L;
            for (EconomyState.FundContribution contribution : fund.contributions) {
                if (contribution != null
                        && contribution.type == EconomyState.ProsperityFundType.DIRECT_GRANT
                        && contribution.purpose == purpose
                        && contribution.amountMicro > 0L) {
                    retainedDirectGrant = retainedDirectGrant
                                    > Long.MAX_VALUE - contribution.amountMicro
                            ? Long.MAX_VALUE
                            : retainedDirectGrant + contribution.amountMicro;
                }
            }
            long attributable = retainedDirectGrant <= unavailable
                    ? 0L
                    : retainedDirectGrant - unavailable;
            long current = Math.max(0L, fund.spendableMicro.getOrDefault(purpose, 0L));
            long reconstructed = Math.min(current, attributable);
            if (reconstructed > 0L) {
                fund.fastTrackSpendableMicro.put(purpose, reconstructed);
            }
        }
        fund.fastTrackProvenanceKnown = true;
    }

    private static void normalizeHousingAccounting(
            EconomyState.VillageRecord village, int format) {
        if (village == null || format >= 13) {
            return;
        }
        village.observedHousingCapacity =
                VillageProsperityEngine.migrateLegacyObservedHousingCapacity(village);
    }

    private static void normalizeArchitecture(
            EconomyState.VillageRecord village, int format) {
        if (village == null) {
            return;
        }
        if (format < 18) {
            // Existing legacy-v1 and modular-v1 projects keep their complete historical recipe
            // and construction cursor. Blueprint metadata belongs only to approvals made by the
            // new format and must never be inferred for an in-progress structure.
            for (EconomyState.VillageProject project : village.projects) {
                project.designTemplateId = "";
                project.designTemplateRevision = 0;
                project.designPaletteId = "";
                project.designDressingId = "";
                project.designPlanHashVersion = 0;
                project.designPlanHash = "";
            }
        }
        if (format >= 10) {
            return;
        }
        village.architectureCharacter = "";
        village.architectureDialect = "";
        for (EconomyState.VillageProject project : village.projects) {
            project.designSchema = VillageArchitecture.LEGACY_SCHEMA;
            project.designSeed = 0L;
            project.designSilhouette = 0;
            project.designRoof = 0;
            project.designFrontage = 0;
            project.designMirrored = false;
            project.designRotation = 0;
            project.designSignature = 0L;
            project.designStage = 0;
            project.designQualityStage = -1;
            project.trailAnchorSet = false;
            project.trailAnchorPos = 0L;
            project.trailMaterializedBlocks = 0;
            project.trailTotalBlocks = 0;
            project.trailMaterializedComplete = false;
        }
    }

    /** Format 13 and earlier had no independent cursor and therefore migrate eligible roads once. */
    private static void normalizeTrailCenterSurfaceMigration(
            EconomyState.VillageRecord village, int format) {
        if (village == null || format >= 14) {
            return;
        }
        for (EconomyState.VillageProject project : village.projects) {
            boolean eligiblePhysicalRoad =
                    VillageArchitecture.MODULAR_SCHEMA.equals(project.designSchema)
                            && !project.abstractOnly
                            && project.originPos != 0L
                            && project.trailAnchorSet
                            && project.trailTotalBlocks > 0;
            project.trailCenterSurfaceVersion = eligiblePhysicalRoad
                    ? 0
                    : EconomyState.TRAIL_CENTER_SURFACE_VERSION;
            project.trailCenterSurfaceMigrationCursor = 0;
            project.trailCenterSurfaceMigrationTotalCells = 0;
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
        if (field.startsWith("fund.")) {
            applyProsperityFundField(
                    village.prosperityFund, field.substring("fund.".length()), value);
            return;
        }
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
            case "observed_housing" ->
                    village.observedHousingCapacity = Integer.parseInt(value);
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
            case "city_id" -> village.cityId = UUID.fromString(value);
            case "expansion_mode" -> village.expansionMode = VillageExpansion.Mode.valueOf(value);
            case "expansion_approved" -> village.expansionApproved = Boolean.parseBoolean(value);
            case "district_founding" -> village.districtFounding = Boolean.parseBoolean(value);
            case "expansion_healthy_days" -> village.expansionHealthyDays = Integer.parseInt(value);
            case "last_expansion_day" -> village.lastExpansionDay = Long.parseLong(value);
            case "expansion_serial" -> village.expansionSerial = Long.parseLong(value);
            case "expansion_site_cursor" -> village.expansionSiteCursor = Long.parseLong(value);
            case "expansion_upkeep_shortfalls" -> village.expansionUpkeepShortfalls = Integer.parseInt(value);
            case "lighting_coverage_percent" -> village.lightingCoveragePercent = Integer.parseInt(value);
            case "last_lighting_day" -> village.lastLightingDay = Long.parseLong(value);
            case "food_sources.crops" -> village.observedCropUnits = Double.parseDouble(value);
            case "food_sources.livestock" -> village.observedLivestockUnits = Double.parseDouble(value);
            case "food_sources.day" -> village.lastFoodSourcesDay = Long.parseLong(value);
            case "visual_project_selection_cursor" ->
                    village.visualProjectSelectionCursor = Long.parseLong(value);
            case "architecture.character" -> village.architectureCharacter = value;
            case "architecture.dialect" -> village.architectureDialect = value;
            default -> {
                // Ignore unknown fields from this supported format.
            }
        }
    }

    private static void applyProsperityFundField(
            EconomyState.ProsperityFund fund, String field, String value) {
        switch (field) {
            case "reserve" -> fund.emergencyReserveMicro = Long.parseLong(value);
            case "lifetime_received" -> fund.lifetimeReceivedMicro = Long.parseLong(value);
            case "lifetime_spent" -> fund.lifetimeSpentMicro = Long.parseLong(value);
            case "last_spending_day" -> fund.lastSpendingDay = Long.parseLong(value);
            case "spent_today" -> fund.spentTodayMicro = Long.parseLong(value);
            case "fast_track_known" ->
                    fund.fastTrackProvenanceKnown |= Boolean.parseBoolean(value);
            default -> {
                if (field.startsWith("spendable.")) {
                    EconomyState.DonationPurpose purpose = EconomyState.DonationPurpose.valueOf(
                            field.substring("spendable.".length()));
                    fund.spendableMicro.put(purpose, Long.parseLong(value));
                } else if (field.startsWith("fast_track_spendable.")) {
                    EconomyState.DonationPurpose purpose = EconomyState.DonationPurpose.valueOf(
                            field.substring("fast_track_spendable.".length()));
                    fund.fastTrackSpendableMicro.put(purpose, Long.parseLong(value));
                    fund.fastTrackProvenanceKnown = true;
                } else if (field.startsWith("endowment.")) {
                    EconomyState.DonationPurpose purpose = EconomyState.DonationPurpose.valueOf(
                            field.substring("endowment.".length()));
                    fund.endowmentPrincipalMicro.put(purpose, Long.parseLong(value));
                } else if (field.startsWith("sponsorship.")) {
                    fund.projectSponsorshipMicro.put(
                            Long.parseLong(field.substring("sponsorship.".length())),
                            Long.parseLong(value));
                } else if (field.startsWith("donor.")) {
                    fund.donorTotalsMicro.put(
                            UUID.fromString(field.substring("donor.".length())),
                            Long.parseLong(value));
                } else if (field.startsWith("contribution.")) {
                    applyFundContributionField(fund, field, value);
                }
            }
        }
    }

    private static void applyFundContributionField(
            EconomyState.ProsperityFund fund, String field, String value) {
        int indexStart = "contribution.".length();
        int indexEnd = field.indexOf('.', indexStart);
        if (indexEnd < 0) {
            return;
        }
        int index = Integer.parseInt(field.substring(indexStart, indexEnd));
        if (index < 0 || index >= EconomyState.MAX_FUND_LEDGER_ENTRIES) {
            throw new IllegalArgumentException("Invalid fund contribution index");
        }
        while (fund.contributions.size() <= index) {
            fund.contributions.add(new EconomyState.FundContribution());
        }
        EconomyState.FundContribution contribution = fund.contributions.get(index);
        switch (field.substring(indexEnd + 1)) {
            case "day" -> contribution.day = Long.parseLong(value);
            case "donor" -> contribution.donorId = UUID.fromString(value);
            case "type" -> contribution.type = EconomyState.ProsperityFundType.valueOf(value);
            case "purpose" -> contribution.purpose = EconomyState.DonationPurpose.valueOf(value);
            case "project" -> contribution.projectId = Long.parseLong(value);
            case "amount" -> contribution.amountMicro = Long.parseLong(value);
            default -> {
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
            String value,
            int format) throws IOException {
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
            case "site_search_cursor" -> project.siteSearchCursor = Integer.parseInt(value);
            case "site_preparation_complete" -> project.sitePreparationComplete = Boolean.parseBoolean(value);
            case "site_preparation_cursor" -> project.sitePreparationCursor = Integer.parseInt(value);
            case "construction_started" -> project.constructionStarted = Boolean.parseBoolean(value);
            case "site_preparation_plan" -> project.sitePreparationPlan = SitePreparationPlan.decode(value);
            case "site_search_saw_unloaded" ->
                    project.siteSearchSawUnloadedCandidate = Boolean.parseBoolean(value);
            case "blocks" -> project.materializedBlocks = Integer.parseInt(value);
            case "total_blocks" -> project.totalBlocks = Integer.parseInt(value);
            case "materialized_complete" -> project.materializedComplete = Boolean.parseBoolean(value);
            case "blocked" -> project.blocked = Boolean.parseBoolean(value);
            case "manual_repair_required" -> project.manualRepairRequired = Boolean.parseBoolean(value);
            case "relocation_pending" -> {
                if (format >= 13) {
                    project.relocationPending = Boolean.parseBoolean(value);
                }
            }
            case "retired_bounds" -> {
                if (format >= 13) {
                    project.retiredLots.clear();
                    project.retiredLots.addAll(decodeRetiredProjectLots(value));
                }
            }
            case "abstract_only" -> project.abstractOnly = Boolean.parseBoolean(value);
            case "design.schema" -> project.designSchema = value;
            case "design.seed" -> project.designSeed = Long.parseLong(value);
            case "design.silhouette" -> project.designSilhouette = Integer.parseInt(value);
            case "design.roof" -> project.designRoof = Integer.parseInt(value);
            case "design.frontage" -> project.designFrontage = Integer.parseInt(value);
            case "design.mirrored" -> project.designMirrored = Boolean.parseBoolean(value);
            case "design.rotation" -> project.designRotation = Integer.parseInt(value);
            case "design.signature" -> project.designSignature = Long.parseLong(value);
            case "design.stage" -> project.designStage = Integer.parseInt(value);
            case "design.quality_stage" -> project.designQualityStage = Integer.parseInt(value);
            case "design.template_id" -> {
                if (format >= 18) {
                    project.designTemplateId = value;
                }
            }
            case "design.template_revision" -> {
                if (format >= 18) {
                    project.designTemplateRevision = Integer.parseInt(value);
                }
            }
            case "design.palette_id" -> {
                if (format >= 18) {
                    project.designPaletteId = value;
                }
            }
            case "design.dressing_id" -> {
                if (format >= 18) {
                    project.designDressingId = value;
                }
            }
            case "design.plan_hash_version" -> {
                if (format >= 18) {
                    project.designPlanHashVersion = Integer.parseInt(value);
                }
            }
            case "design.plan_hash" -> {
                if (format >= 18) {
                    project.designPlanHash = value;
                }
            }
            case "trail.anchor_set" -> project.trailAnchorSet = Boolean.parseBoolean(value);
            case "trail.anchor" -> project.trailAnchorPos = Long.parseLong(value);
            case "trail.blocks" -> project.trailMaterializedBlocks = Integer.parseInt(value);
            case "trail.total_blocks" -> project.trailTotalBlocks = Integer.parseInt(value);
            case "trail.complete" -> project.trailMaterializedComplete = Boolean.parseBoolean(value);
            case "trail.center_surface_version" -> {
                if (format >= 14) {
                    project.trailCenterSurfaceVersion = Integer.parseInt(value);
                }
            }
            case "trail.center_surface_cursor" -> {
                if (format >= 14) {
                    project.trailCenterSurfaceMigrationCursor = Integer.parseInt(value);
                }
            }
            case "trail.center_surface_total_cells" -> {
                if (format >= 14) {
                    project.trailCenterSurfaceMigrationTotalCells = Integer.parseInt(value);
                }
            }
            case "entrance.approach_version" -> {
                if (format >= 15) {
                    project.entranceApproachVersion = Integer.parseInt(value);
                }
            }
            case "entrance.approach_step_count" -> {
                if (format >= 15) {
                    project.entranceApproachStepCount = Integer.parseInt(value);
                }
            }
            case "entrance.approach_cursor" -> {
                if (format >= 15) {
                    project.entranceApproachCursor = Integer.parseInt(value);
                }
            }
            case "entrance.approach_total_cells" -> {
                if (format >= 15) {
                    project.entranceApproachTotalCells = Integer.parseInt(value);
                }
            }
            case "entrance.approach_complete" -> {
                if (format >= 15) {
                    project.entranceApproachComplete = Boolean.parseBoolean(value);
                }
            }
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
        if (field.startsWith("cdpos.")) {
            applyCdPositionField(account, field, value);
            return;
        }
        if (field.startsWith("loanpos.")) {
            applyLoanPositionField(account, field, value);
            return;
        }
        if (field.startsWith("portfolio.")) {
            applyPortfolioField(account, field.substring("portfolio.".length()), value);
            return;
        }
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
            case "position.next" -> account.nextTermPositionId = Long.parseLong(value);
            default -> {
                if (field.startsWith("share.")) {
                    account.shares.put(
                            field.substring("share.".length()).toUpperCase(Locale.ROOT),
                            Double.parseDouble(value));
                }
            }
        }
    }

    private static void applyCdPositionField(
            EconomyState.Account account, String field, String value) {
        int idStart = "cdpos.".length();
        int idEnd = field.indexOf('.', idStart);
        if (idEnd < 0) return;
        long id = Long.parseLong(field.substring(idStart, idEnd));
        EconomyState.CdPosition position = account.cdPositions.computeIfAbsent(id, ignored -> {
            EconomyState.CdPosition created = new EconomyState.CdPosition();
            created.positionId = id;
            return created;
        });
        switch (field.substring(idEnd + 1)) {
            case "principal" -> position.principalMicro = Long.parseLong(value);
            case "value" -> position.valueMicro = Long.parseLong(value);
            case "open" -> position.openDay = Long.parseLong(value);
            case "maturity" -> position.maturityDay = Long.parseLong(value);
            case "rate" -> position.annualRate = Double.parseDouble(value);
            default -> {
            }
        }
    }

    private static void applyLoanPositionField(
            EconomyState.Account account, String field, String value) {
        int idStart = "loanpos.".length();
        int idEnd = field.indexOf('.', idStart);
        if (idEnd < 0) return;
        long id = Long.parseLong(field.substring(idStart, idEnd));
        EconomyState.LoanPosition position = account.loanPositions.computeIfAbsent(id, ignored -> {
            EconomyState.LoanPosition created = new EconomyState.LoanPosition();
            created.positionId = id;
            return created;
        });
        switch (field.substring(idEnd + 1)) {
            case "principal" -> position.principalMicro = Long.parseLong(value);
            case "value" -> position.valueMicro = Long.parseLong(value);
            case "open" -> position.openDay = Long.parseLong(value);
            case "maturity" -> position.maturityDay = Long.parseLong(value);
            case "serial" -> position.serial = Long.parseLong(value);
            case "rate" -> position.annualRate = Double.parseDouble(value);
            case "stress" -> position.stress = Double.parseDouble(value);
            case "recovery" -> position.recoveryRate = Double.parseDouble(value);
            case "resolved" -> position.resolved = Boolean.parseBoolean(value);
            case "outcome" -> position.outcome = EconomyEngine.LoanOutcome.valueOf(value);
            default -> {
            }
        }
    }

    private static void applyPortfolioField(
            EconomyState.Account account, String field, String value) {
        switch (field) {
            case "realized" -> account.realizedGainMicro = Long.parseLong(value);
            case "contributions" -> account.totalContributionsMicro = Long.parseLong(value);
            case "withdrawals" -> account.totalWithdrawalsMicro = Long.parseLong(value);
            case "inferred" -> account.costBasisInferred = Boolean.parseBoolean(value);
            default -> {
                if (field.startsWith("basis.")) {
                    account.shareCostBasisMicro.put(
                            field.substring("basis.".length()).toUpperCase(Locale.ROOT),
                            Long.parseLong(value));
                } else if (field.startsWith("ledger.")) {
                    applyPortfolioLedgerField(account, field, value);
                } else if (field.startsWith("history.")) {
                    applyPortfolioHistoryField(account, field, value);
                }
            }
        }
    }

    private static void applyPortfolioLedgerField(
            EconomyState.Account account, String field, String value) {
        int indexStart = "ledger.".length();
        int indexEnd = field.indexOf('.', indexStart);
        if (indexEnd < 0) return;
        int index = Integer.parseInt(field.substring(indexStart, indexEnd));
        if (index < 0 || index >= EconomyState.MAX_PORTFOLIO_LEDGER_ENTRIES) {
            throw new IllegalArgumentException("Invalid portfolio ledger index");
        }
        while (account.transactionLedger.size() <= index) {
            account.transactionLedger.add(new EconomyState.PortfolioTransaction());
        }
        EconomyState.PortfolioTransaction transaction = account.transactionLedger.get(index);
        switch (field.substring(indexEnd + 1)) {
            case "day" -> transaction.day = Long.parseLong(value);
            case "kind" -> transaction.kind = EconomyState.PortfolioTransactionKind.valueOf(value);
            case "symbol" -> transaction.symbol = value;
            case "reference" -> transaction.referenceId = Long.parseLong(value);
            case "quantity" -> transaction.quantity = Double.parseDouble(value);
            case "amount" -> transaction.amountMicro = Long.parseLong(value);
            case "basis" -> transaction.costBasisMicro = Long.parseLong(value);
            case "realized" -> transaction.realizedGainMicro = Long.parseLong(value);
            default -> {
            }
        }
    }

    private static void applyPortfolioHistoryField(
            EconomyState.Account account, String field, String value) {
        int indexStart = "history.".length();
        int indexEnd = field.indexOf('.', indexStart);
        if (indexEnd < 0) return;
        int index = Integer.parseInt(field.substring(indexStart, indexEnd));
        if (index < 0 || index >= EconomyState.HISTORY_DAYS) {
            throw new IllegalArgumentException("Invalid net-worth history index");
        }
        while (account.netWorthHistory.size() <= index) {
            account.netWorthHistory.add(new EconomyState.PortfolioValuePoint());
        }
        EconomyState.PortfolioValuePoint point = account.netWorthHistory.get(index);
        switch (field.substring(indexEnd + 1)) {
            case "day" -> point.day = Long.parseLong(value);
            case "value" -> point.valueMicro = Long.parseLong(value);
            default -> {
            }
        }
    }

    private static void loadDonors(EconomyState state, Properties properties) {
        String prefix = "donor.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            int uuidEnd = key.indexOf('.', prefix.length());
            if (uuidEnd < 0) continue;
            UUID donorId = UUID.fromString(key.substring(prefix.length(), uuidEnd));
            EconomyState.DonorRecord donor = state.donors.computeIfAbsent(
                    donorId, ignored -> new EconomyState.DonorRecord());
            String field = key.substring(uuidEnd + 1);
            String value = properties.getProperty(key);
            if (field.equals("lifetime")) {
                donor.lifetimeContributionMicro = Long.parseLong(value);
            } else if (field.equals("count")) {
                donor.contributionCount = Integer.parseInt(value);
            } else if (field.startsWith("type.")) {
                donor.byTypeMicro.put(
                        EconomyState.ProsperityFundType.valueOf(field.substring("type.".length())),
                        Long.parseLong(value));
            } else if (field.startsWith("purpose.")) {
                donor.byPurposeMicro.put(
                        EconomyState.DonationPurpose.valueOf(field.substring("purpose.".length())),
                        Long.parseLong(value));
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

    private static String encodeLongList(List<Long> values) {
        StringBuilder builder = new StringBuilder(values.size() * 14);
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private static List<Long> decodeLongList(String encoded, int maximum, String description)
            throws IOException {
        String[] pieces = encoded.split(",", -1);
        if (pieces.length == 0 || pieces.length > maximum) {
            throw new IOException(description + " history length is invalid");
        }
        List<Long> values = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            if (piece.isBlank()) {
                throw new IOException(description + " history contains a blank value");
            }
            try {
                values.add(Long.parseLong(piece));
            } catch (NumberFormatException exception) {
                throw new IOException(description + " history contains an invalid value", exception);
            }
        }
        return values;
    }

    private static String encodeRetiredProjectLots(
            List<EconomyState.RetiredProjectLot> lots) {
        StringBuilder builder = new StringBuilder(lots.size() * 30);
        for (int index = 0; index < lots.size(); index++) {
            if (index > 0) {
                builder.append(';');
            }
            EconomyState.RetiredProjectLot lot = lots.get(index);
            builder.append(lot.boundsMinPos).append(':').append(lot.boundsMaxPos);
        }
        return builder.toString();
    }

    private static List<EconomyState.RetiredProjectLot> decodeRetiredProjectLots(String encoded)
            throws IOException {
        String[] pieces = encoded.split(";", -1);
        if (pieces.length == 0 || pieces.length > EconomyState.MAX_RETIRED_PROJECT_LOTS) {
            throw new IOException("Retired project-lot history length is invalid");
        }
        List<EconomyState.RetiredProjectLot> lots = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            String[] bounds = piece.split(":", -1);
            if (bounds.length != 2 || bounds[0].isBlank() || bounds[1].isBlank()) {
                throw new IOException("Retired project-lot history contains invalid bounds");
            }
            try {
                lots.add(new EconomyState.RetiredProjectLot(
                        Long.parseLong(bounds[0]), Long.parseLong(bounds[1])));
            } catch (NumberFormatException exception) {
                throw new IOException(
                        "Retired project-lot history contains invalid bounds", exception);
            }
        }
        return lots;
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

    private static void loadFallbackBankRegions(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.fallback.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix) || !Boolean.parseBoolean(properties.getProperty(key))) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                if (!state.generatedBankRegions.contains(region)
                        || !state.generatedBankAnchors.containsKey(region)) {
                    throw new IOException("Fallback bank has no matching region anchor " + encoded);
                }
                state.fallbackBankRegions.add(region);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid fallback bank key " + encoded, exception);
            }
        }
    }

    private static void loadBankStructureVersions(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.structure_version.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                int version = Integer.parseInt(properties.getProperty(key));
                if (version <= 0
                        || !state.generatedBankRegions.contains(region)
                        || !state.generatedBankAnchors.containsKey(region)
                        || state.fallbackBankRegions.contains(region)) {
                    throw new IOException(
                            "Bank structure version has no matching generated structure "
                                    + encoded);
                }
                state.bankStructureVersions.put(region, version);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid bank structure version " + encoded, exception);
            }
        }
    }

    private static void loadRetiredBankAnchors(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.retired_anchors.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                List<Long> anchors = decodeLongList(
                        properties.getProperty(key),
                        EconomyState.MAX_RETIRED_BANK_ANCHORS_PER_REGION,
                        "Retired Bank anchor");
                if (!state.generatedBankRegions.contains(region)
                        || !state.generatedBankAnchors.containsKey(region)
                        || !state.bankStructureVersions.containsKey(region)
                        || state.fallbackBankRegions.contains(region)) {
                    throw new IOException(
                            "Retired Bank anchor has no current authored Bank " + encoded);
                }
                state.retiredBankAnchors.put(region, anchors);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid retired Bank anchor " + encoded, exception);
            }
        }
    }

    private static void loadBankerEntityIds(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.banker.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                UUID bankerId = UUID.fromString(properties.getProperty(key));
                if (!state.generatedBankRegions.contains(region)
                        || !state.generatedBankAnchors.containsKey(region)
                        || state.bankRegionBankerIds.containsValue(bankerId)) {
                    throw new IOException(
                            "Canonical Banker has no unique generated region " + encoded);
                }
                state.bankRegionBankerIds.put(region, bankerId);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid canonical Banker " + encoded, exception);
            }
        }
    }

    private static void loadBankerAssignedAnchors(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.banker_anchor.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                long anchor = Long.parseLong(properties.getProperty(key));
                if (!state.bankRegionBankerIds.containsKey(region)
                        || !state.generatedBankRegions.contains(region)
                        || !state.generatedBankAnchors.containsKey(region)) {
                    throw new IOException(
                            "Canonical Banker anchor has no matching identity " + encoded);
                }
                state.bankRegionBankerAnchors.put(region, anchor);
            } catch (NumberFormatException exception) {
                throw new IOException(
                        "Invalid canonical Banker anchor " + encoded, exception);
            }
        }

        // Early format-13 development builds persisted the canonical UUID before the assigned
        // anchor was added. Prefer the newest retired anchor so a Banker still standing at the
        // abandoned Bank can converge to the current site; otherwise the current anchor is safe.
        for (Long region : state.bankRegionBankerIds.keySet()) {
            if (state.bankRegionBankerAnchors.containsKey(region)) {
                continue;
            }
            List<Long> retired = state.retiredBankAnchors.get(region);
            Long inferred = retired == null || retired.isEmpty()
                    ? state.generatedBankAnchors.get(region)
                    : retired.get(retired.size() - 1);
            state.bankRegionBankerAnchors.put(region, inferred);
        }
    }

    private static void loadPendingBankerDeaths(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.banker_death.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encoded = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encoded, 16);
                UUID bankerId = UUID.fromString(properties.getProperty(key));
                if (!bankerId.equals(state.bankRegionBankerIds.get(region))
                        || !state.bankRegionBankerAnchors.containsKey(region)
                        || !state.generatedBankRegions.contains(region)
                        || !state.generatedBankAnchors.containsKey(region)) {
                    throw new IOException(
                            "Pending Banker death has no matching canonical identity " + encoded);
                }
                state.pendingBankerDeaths.put(region, bankerId);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid pending Banker death " + encoded, exception);
            }
        }
    }

    private static void loadPendingBankerConversions(
            EconomyState state, Properties properties) throws IOException {
        String prefix = "bank.banker_conversion.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String encodedRegion = key.substring(prefix.length());
            try {
                long region = Long.parseUnsignedLong(encodedRegion, 16);
                String[] fields = requireValue(properties, key).split("\\|", -1);
                if (fields.length != 7) {
                    throw new IllegalArgumentException("wrong field count");
                }
                EconomyState.PendingBankerConversion conversion =
                        new EconomyState.PendingBankerConversion();
                conversion.rootCanonicalId = UUID.fromString(fields[0]);
                conversion.immediateSourceId = UUID.fromString(fields[1]);
                conversion.targetId = UUID.fromString(fields[2]);
                conversion.targetDimension = fields[3];
                conversion.targetPos = Long.parseLong(fields[4]);
                conversion.phase = EconomyState.BankerConversionPhase.valueOf(fields[5]);
                conversion.disposition =
                        EconomyState.BankerConversionDisposition.valueOf(fields[6]);
                state.pendingBankerConversions.put(region, conversion);
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Invalid pending Banker conversion " + encodedRegion, exception);
            }
        }
    }

    private static String checksum(Properties properties) throws IOException {
        MessageDigest digest = newSha256Digest();
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
    }

    private static byte[] rawFingerprint(byte[] bytes) throws IOException {
        return newSha256Digest().digest(bytes);
    }

    private static byte[] rawFingerprint(Path path) throws IOException {
        MessageDigest digest = newSha256Digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    private static MessageDigest newSha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
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
