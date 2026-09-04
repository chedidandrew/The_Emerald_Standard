package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe application service shared by Fabric and NeoForge. */
public final class EconomyService {
    public static final long MILLIS_PER_MINECRAFT_DAY = 1_200_000L;
    public static final long TICKS_PER_MINECRAFT_DAY = 24_000L;
    public static final long MILLIS_PER_GAME_TICK = 50L;
    public static final long MAX_TRUSTED_CATCH_UP_DAYS = 25_000L;
    public static final long MAX_WHOLE_EMERALD_TRANSACTION = 1_000_000L;
    public static final int MAX_INVENTORY_ITEM_TRANSACTION =
            EconomyState.MAX_PENDING_INVENTORY_ITEMS;

    private static final long STARTUP_CATCH_UP_BATCH_DAYS = 2_000L;
    private static final long TICK_CATCH_UP_BATCH_DAYS = 250L;
    private static final long STARTUP_CATCH_UP_WORK_BUDGET = 16_000L;
    private static final long TICK_CATCH_UP_WORK_BUDGET = 2_000L;
    private static final long INITIAL_PROJECT_RETRY_TICKS = 600L;
    private static final long MAX_PROJECT_RETRY_TICKS = 24_000L;
    private static final long PLAYER_MARKET_SHADOW_DAYS = 60L;
    private static final long AUTO_SAVE_INTERVAL_MS = 30_000L;
    private static final long INITIAL_SAVE_RETRY_MS = 2_000L;
    private static final long MAX_SAVE_RETRY_MS = 60_000L;
    private static final long MAX_PENDING_ECONOMIC_MS =
            MAX_TRUSTED_CATCH_UP_DAYS * MILLIS_PER_MINECRAFT_DAY
                    + MILLIS_PER_MINECRAFT_DAY - 1L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Rebuildable acceleration only; {@link EconomyState#villages} remains authoritative. */
    private final VillageSpatialIndex villageSpatialIndex = new VillageSpatialIndex();
    private EconomyState state;
    private Path path;
    private String lastError = "";
    private boolean dirty;
    private long nextAutomaticSaveMs;
    private long nextSaveRetryMs;
    private long saveRetryDelayMs = INITIAL_SAVE_RETRY_MS;
    private boolean villageProsperitySimulationEnabled = true;
    private boolean villageVisualProgressionEnabled = true;
    private boolean villageMarketIntegrationEnabled = true;
    private boolean villageAutomaticRecoveryEnabled = true;
    private ProsperityFundPolicy prosperityFundPolicy = ProsperityFundPolicy.defaults();

    public synchronized void configureVillageProsperity(
            boolean simulationEnabled, boolean visualProgressionEnabled) {
        configureVillageProsperity(simulationEnabled, visualProgressionEnabled, true, true);
    }

    public synchronized void configureVillageProsperity(
            boolean simulationEnabled,
            boolean visualProgressionEnabled,
            boolean marketIntegrationEnabled,
            boolean automaticRecoveryEnabled) {
        villageProsperitySimulationEnabled = simulationEnabled;
        villageVisualProgressionEnabled = visualProgressionEnabled;
        villageMarketIntegrationEnabled = marketIntegrationEnabled;
        villageAutomaticRecoveryEnabled = automaticRecoveryEnabled;
    }

    public synchronized boolean villageProsperitySimulationEnabled() {
        return villageProsperitySimulationEnabled;
    }

    public synchronized boolean villageVisualProgressionEnabled() {
        return villageVisualProgressionEnabled;
    }

    public synchronized void configureProsperityFund(ProsperityFundPolicy policy) {
        prosperityFundPolicy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized void configureProsperityFund(
            boolean enabled,
            double endowmentAnnualPayoutRate,
            double emergencyReserveFraction,
            long dailySpendingCapEmeralds) {
        Long capMicro = dailySpendingCapEmeralds <= 0L
                        || dailySpendingCapEmeralds > MAX_WHOLE_EMERALD_TRANSACTION
                ? null
                : wholeEmeraldsToMicro(dailySpendingCapEmeralds);
        if (capMicro == null) {
            throw new IllegalArgumentException("Daily prosperity-fund spending cap is invalid");
        }
        configureProsperityFund(new ProsperityFundPolicy(
                enabled, endowmentAnnualPayoutRate, emergencyReserveFraction, capMicro));
    }

    public synchronized ProsperityFundPolicy prosperityFundPolicy() {
        return prosperityFundPolicy;
    }

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
            villageSpatialIndex.rebuild(state.villages);
            observeProgress(now, gameTicks, STARTUP_CATCH_UP_BATCH_DAYS);
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
        } catch (IOException | RuntimeException exception) {
            lastError = message(exception);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not initialize the economy", exception);
        }
    }

    /**
     * Observes wall and game time every server tick, preserves partial days, advances bounded catch-up
     * batches, and periodically persists progress. Failed automatic saves use exponential backoff.
     */
    public synchronized boolean tick(long gameTicks) {
        return tickAt(gameTicks, System.currentTimeMillis());
    }

    synchronized boolean tickAt(long gameTicks, long now) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        try {
            dirty |= observeProgress(now, gameTicks, TICK_CATCH_UP_BATCH_DAYS);
            if (!dirty || now < nextAutomaticSaveMs || now < nextSaveRetryMs) {
                return true;
            }
            return persistDirtyState(now);
        } catch (RuntimeException exception) {
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    public synchronized boolean saveNow() {
        if (state == null) {
            lastError = "Economy service has not started";
            return false;
        }
        return saveNowAt(state.lastGameTicks, System.currentTimeMillis());
    }

    /** Saves final partial progress during server shutdown. */
    public synchronized boolean saveNow(long gameTicks) {
        return saveNowAt(gameTicks, System.currentTimeMillis());
    }

    synchronized boolean saveNowAt(long gameTicks, long now) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        try {
            dirty |= observeProgress(now, gameTicks, STARTUP_CATCH_UP_BATCH_DAYS);
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            dirty = true;
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    /** Full copy retained for tests and administrative diagnostics. */
    public synchronized EconomyState snapshot() {
        return state == null ? null : state.copy();
    }

    public synchronized MarketSnapshot marketSnapshot() {
        if (state == null) {
            return null;
        }
        return new MarketSnapshot(
                state.economicDay,
                state.regime,
                state.lastMarketEvent,
                state.lastMarketEventDay,
                Map.copyOf(state.prices),
                Map.copyOf(state.commodityPrices),
                copyHistory(state.priceHistory),
                catchUpDaysRemainingInternal(),
                dirty);
    }

    public synchronized PortfolioSnapshot portfolioSnapshot(UUID id) {
        if (state == null) {
            return null;
        }
        EconomyState.Account account = state.existingAccount(id);
        EconomyState.PendingInventoryTransaction transaction =
                state.pendingInventoryTransactions.get(id);
        return new PortfolioSnapshot(
                state.economicDay,
                account == null ? new EconomyState.Account() : account.copy(),
                Map.copyOf(state.prices),
                state.netWorth(id),
                transaction == null ? null : transaction.copy(),
                catchUpDaysRemainingInternal());
    }

    public synchronized PortfolioAnalytics.PortfolioSnapshot portfolioAnalyticsSnapshot(UUID id) {
        if (state == null) {
            return PortfolioAnalytics.PortfolioSnapshot.empty();
        }
        EconomyState.Account account = state.existingAccount(id);
        return account == null
                ? PortfolioAnalytics.PortfolioSnapshot.empty()
                : PortfolioAnalytics.snapshot(account.copy(), state);
    }

    public synchronized Map<String, List<Double>> commodityHistorySnapshot() {
        return state == null ? Map.of() : copyHistory(state.commodityHistory);
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized long catchUpDaysRemaining() {
        return state == null ? 0L : catchUpDaysRemainingInternal();
    }

    public synchronized boolean isCatchingUp() {
        return catchUpDaysRemaining() > 0L;
    }

    public synchronized boolean hasGeneratedBankRegion(long regionKey) {
        return state != null && state.generatedBankRegions.contains(regionKey);
    }

    public synchronized Long generatedBankAnchor(long regionKey) {
        return state == null ? null : state.generatedBankAnchors.get(regionKey);
    }

    /** Read-only anchors used to recognize generated bank counters without exposing mutable state. */
    public synchronized Map<Long, Long> generatedBankAnchorsSnapshot() {
        return state == null ? Map.of() : Map.copyOf(state.generatedBankAnchors);
    }

    public synchronized boolean markGeneratedBankRegion(long regionKey) {
        return markGeneratedBankRegion(regionKey, null);
    }

    public synchronized boolean markGeneratedBankRegion(long regionKey, Long packedAnchor) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            boolean changed = state.generatedBankRegions.add(regionKey);
            if (packedAnchor != null
                    && !Objects.equals(state.generatedBankAnchors.put(regionKey, packedAnchor),
                            packedAnchor)) {
                changed = true;
            }
            if (!changed) {
                return true;
            }
            state.save(path);
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    /** Registers or refreshes one loaded village without scanning or loading any chunks here. */
    public synchronized VillageSnapshot observeVillage(VillageObservation observation) {
        return observeVillage(null, observation);
    }

    /**
     * Registers or refreshes a loaded village, preferring an already-tagged stable village identity
     * when one is supplied by the Minecraft integration layer.
     */
    public synchronized VillageSnapshot observeVillage(
            UUID preferredVillageId, VillageObservation observation) {
        if (state == null || path == null || observation == null) {
            lastError = "Economy service has not started";
            return null;
        }
        UUID villageId = resolveVillageId(preferredVillageId, observation);
        EconomyState.VillageRecord existing = state.existingVillage(villageId);
        boolean created = existing == null;
        EconomyState.VillageRecord before = existing == null ? null : existing.copy();
        UUID previousRegionAssociation = observation.bankRegionKey() == 0L
                ? null
                : state.bankRegionVillageIds.get(observation.bankRegionKey());
        boolean dirtyBefore = dirty;
        try {
            EconomyState.VillageRecord village = state.village(villageId);
            if (created) {
                initializeVillage(village, observation);
            }
            updateVillageObservation(village, observation);
            if (observation.bankRegionKey() != 0L
                    && state.generatedBankRegions.contains(observation.bankRegionKey())) {
                UUID associatedVillage = state.bankRegionVillageIds.get(observation.bankRegionKey());
                if (associatedVillage == null || associatedVillage.equals(villageId)) {
                    state.bankRegionVillageIds.put(observation.bankRegionKey(), villageId);
                    village.bankRegionKey = observation.bankRegionKey();
                    Long anchor = state.generatedBankAnchors.get(observation.bankRegionKey());
                    if (anchor != null) {
                        village.bankAnchorPos = anchor;
                    }
                } else if (created) {
                    // A coarse legacy region may contain multiple distinct tagged villages.
                    village.bankRegionKey = 0L;
                    village.bankAnchorPos = 0L;
                }
            }
            dirty = true;
            if (created) {
                state.save(path);
                dirty = false;
                resetSaveSchedule(state.lastWallClockMs);
            }
            lastError = "";
            villageSpatialIndex.upsert(village);
            return villageSnapshot(village);
        } catch (IOException | RuntimeException exception) {
            if (before == null) {
                state.villages.remove(villageId);
            } else {
                state.villages.put(villageId, before);
            }
            if (observation.bankRegionKey() != 0L) {
                if (previousRegionAssociation == null) {
                    state.bankRegionVillageIds.remove(observation.bankRegionKey());
                } else {
                    state.bankRegionVillageIds.put(
                            observation.bankRegionKey(), previousRegionAssociation);
                }
            }
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return null;
        }
    }

    public synchronized VillageSnapshot villageSnapshot(UUID villageId) {
        if (state == null || villageId == null) {
            return null;
        }
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        return village == null ? null : villageSnapshot(village);
    }

    public synchronized VillageSnapshot nearestVillageSnapshot(
            String dimensionKey, long packedPosition, double maximumDistance) {
        if (state == null) {
            return null;
        }
        EconomyState.VillageRecord village = nearestVillage(
                dimensionKey, packedPosition, maximumDistance);
        return village == null ? null : villageSnapshot(village);
    }

    public synchronized List<VillageSnapshot> villageSnapshots() {
        if (state == null) {
            return List.of();
        }
        VillageProsperityEngine.VillageFundamentals fundamentals =
                villageMarketIntegrationEnabled
                        ? state.villageFundamentals()
                        : VillageProsperityEngine.VillageFundamentals.neutral();
        List<VillageSnapshot> snapshots = new ArrayList<>(state.villages.size());
        for (EconomyState.VillageRecord village : state.villages.values()) {
            snapshots.add(new VillageSnapshot(
                    village.copy(),
                    fundamentals,
                    villageProsperitySimulationEnabled,
                    villageVisualProgressionEnabled));
        }
        return List.copyOf(snapshots);
    }

    /**
     * Returns only villages near one of the supplied loaded-world positions. This lets the
     * integration layer avoid copying and walking the entire persistent village registry.
     */
    public synchronized List<VillageSnapshot> villageSnapshotsNear(
            String dimensionKey,
            Collection<Long> packedPositions,
            double maximumDistance) {
        if (state == null
                || packedPositions == null
                || packedPositions.isEmpty()
                || !Double.isFinite(maximumDistance)
                || maximumDistance < 0.0) {
            return List.of();
        }
        ensureVillageSpatialIndex();
        VillageProsperityEngine.VillageFundamentals fundamentals =
                villageMarketIntegrationEnabled
                        ? state.villageFundamentals()
                        : VillageProsperityEngine.VillageFundamentals.neutral();
        List<EconomyState.VillageRecord> nearby = villageSpatialIndex.nearAny(
                state.villages, dimensionKey, packedPositions, maximumDistance);
        List<VillageSnapshot> snapshots = new ArrayList<>(nearby.size());
        for (EconomyState.VillageRecord village : nearby) {
            snapshots.add(new VillageSnapshot(
                    village.copy(),
                    fundamentals,
                    villageProsperitySimulationEnabled,
                    villageVisualProgressionEnabled));
        }
        return List.copyOf(snapshots);
    }

    public synchronized UUID villageIdForBankRegion(long regionKey) {
        return state == null ? null : state.bankRegionVillageIds.get(regionKey);
    }

    public synchronized boolean associateBankRegionWithVillage(
            long regionKey, UUID villageId, long packedAnchor) {
        if (state == null || path == null || villageId == null) {
            return false;
        }
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        if (village == null || !state.generatedBankRegions.contains(regionKey)) {
            return false;
        }
        if (Objects.equals(state.bankRegionVillageIds.get(regionKey), villageId)
                && village.bankRegionKey == regionKey
                && village.bankAnchorPos == packedAnchor) {
            lastError = "";
            return true;
        }
        EconomyState.VillageRecord before = village.copy();
        UUID previous = state.bankRegionVillageIds.get(regionKey);
        try {
            state.bankRegionVillageIds.put(regionKey, villageId);
            village.bankRegionKey = regionKey;
            village.bankAnchorPos = packedAnchor;
            state.save(path);
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state.villages.put(villageId, before);
            if (previous == null) {
                state.bankRegionVillageIds.remove(regionKey);
            } else {
                state.bankRegionVillageIds.put(regionKey, previous);
            }
            lastError = message(exception);
            return false;
        }
    }

    /** Records a loaded-world resident state transition such as zombie infection. */
    public synchronized boolean recordResidentStatus(
            UUID villageId,
            UUID residentId,
            String profession,
            long packedPosition,
            VillageProsperityEngine.ResidentStatus status) {
        if (state == null || path == null || villageId == null || residentId == null || status == null) {
            return false;
        }
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        if (village == null) {
            return false;
        }
        EconomyState.VillageRecord villageBefore = village.copy();
        EconomyState.VillageMarketShadow shadow = state.villageMarketShadows.get(villageId);
        EconomyState.VillageMarketShadow shadowBefore = shadow == null ? null : shadow.copy();
        boolean dirtyBefore = dirty;
        try {
            boolean applied = applyResidentStatus(
                    village,
                    residentId,
                    profession,
                    packedPosition,
                    status,
                    state.economicDay);
            if (!applied) {
                lastError = "";
                return true;
            }
            trimResidentHistory(village, residentId);
            if (shadow != null && shadow.counterfactualVillage != null) {
                if (applyResidentStatus(
                        shadow.counterfactualVillage,
                        residentId,
                        profession,
                        packedPosition,
                        status,
                        state.economicDay)) {
                    trimResidentHistory(shadow.counterfactualVillage, residentId);
                    VillageProsperityEngine.refreshMarketShadow(shadow, state.economicDay);
                }
            }
            // Status changes and their market counterfactual form one in-memory transaction.
            // Validate before accepting it so a later autosave cannot be wedged by bounded history.
            state.validate();
            dirty = true;
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state.villages.put(villageId, villageBefore);
            if (shadowBefore == null) {
                state.villageMarketShadows.remove(villageId);
            } else {
                state.villageMarketShadows.put(villageId, shadowBefore);
            }
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    /** Records an actual loaded-world villager casualty. Missing or unloaded villagers are not deaths. */
    public synchronized boolean recordVillagerDeath(
            UUID villageId,
            UUID residentId,
            String profession,
            long packedPosition,
            VillageProsperityEngine.IncidentCause cause,
            UUID responsiblePlayer) {
        if (state == null || path == null || villageId == null || cause == null) {
            return false;
        }
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        if (village == null) {
            return false;
        }
        EconomyState.VillageRecord before = village.copy();
        EconomyState.VillageMarketShadow existingShadow =
                state.villageMarketShadows.get(villageId);
        EconomyState.VillageMarketShadow shadowBefore = existingShadow == null
                ? null
                : existingShadow.copy();
        try {
            EconomyState.ResidentRecord resident = residentId == null
                    ? null
                    : village.residents.get(residentId);
            if (resident == null) {
                resident = nearestResidentWithStatus(
                        village,
                        packedPosition,
                        16.0,
                        VillageProsperityEngine.ResidentStatus.INFECTED);
            }
            if (resident != null
                    && resident.status == VillageProsperityEngine.ResidentStatus.DEAD) {
                return true;
            }
            VillageProsperityEngine.ResidentStatus previousStatus = resident == null
                    ? null
                    : resident.status;
            if (cause == VillageProsperityEngine.IncidentCause.PLAYER
                    && existingShadow == null) {
                EconomyState.VillageMarketShadow captured =
                        VillageProsperityEngine.captureMarketShadow(
                                village, state.economicDay, PLAYER_MARKET_SHADOW_DAYS);
                if (captured != null) {
                    state.villageMarketShadows.put(villageId, captured);
                }
            }
            if (resident == null && residentId != null) {
                resident = village.residents.computeIfAbsent(residentId, ignored -> {
                    EconomyState.ResidentRecord created = new EconomyState.ResidentRecord();
                    created.residentId = residentId;
                    return created;
                });
            }
            if (resident != null) {
                resident.profession = profession == null || profession.isBlank()
                        ? "minecraft:none"
                        : profession;
                resident.status = VillageProsperityEngine.ResidentStatus.DEAD;
                resident.lastSeenDay = state.economicDay;
                resident.lastKnownPos = packedPosition;
            }
            trimResidentHistory(village, residentId);
            boolean alreadyOutsideProductivePopulation =
                    previousStatus == VillageProsperityEngine.ResidentStatus.INFECTED
                            || previousStatus == VillageProsperityEngine.ResidentStatus.EMIGRATED;
            if (!alreadyOutsideProductivePopulation) {
                village.population = Math.max(0, village.population - 1);
                village.observedPopulation = Math.max(0, village.observedPopulation - 1);
            }
            village.lastIncidentDay = state.economicDay;
            village.lastIncidentCause = cause;
            double safetyLoss = casualtySafetyLoss(cause);
            village.safety = Math.max(0.0, village.safety - safetyLoss);
            village.prosperity = Math.max(0.0, village.prosperity - safetyLoss * 0.55);
            switch (cause) {
                case RAID, PILLAGER, HOSTILE -> village.hostileCasualties++;
                case PLAYER -> {
                    village.playerCasualties++;
                    long suppressionDay = state.economicDay
                            > Long.MAX_VALUE - PLAYER_MARKET_SHADOW_DAYS
                                    ? Long.MAX_VALUE
                                    : state.economicDay + PLAYER_MARKET_SHADOW_DAYS;
                    village.marketSuppressedUntilDay = Math.max(
                            village.marketSuppressedUntilDay, suppressionDay);
                }
                case ENVIRONMENT, UNKNOWN -> village.environmentalCasualties++;
                case NONE -> {
                }
            }
            EconomyState.VillageIncident incident = new EconomyState.VillageIncident();
            incident.day = state.economicDay;
            incident.cause = cause;
            incident.casualties = 1;
            incident.responsiblePlayer = responsiblePlayer;
            incident.marketEligible = cause != VillageProsperityEngine.IncidentCause.PLAYER;
            village.incidents.add(incident);
            while (village.incidents.size() > VillageProsperityEngine.INCIDENT_HISTORY_LIMIT) {
                village.incidents.remove(0);
            }

            if (village.population == 0) {
                village.collapseCount = state.economicDay - village.lastCollapseDay <= 30L
                        ? village.collapseCount + 1
                        : 1;
                village.lastCollapseDay = state.economicDay;
                int delay = VillageProsperityEngine.recoveryDelayDays(village, cause);
                boolean deliberate = cause == VillageProsperityEngine.IncidentCause.PLAYER;
                boolean repeatedCollapse = village.collapseCount >= 3;
                village.lifecycle = deliberate || repeatedCollapse
                        ? VillageProsperityEngine.Lifecycle.ABANDONED
                        : VillageProsperityEngine.Lifecycle.EXTINCT;
                village.abandonedSinceDay = village.lifecycle
                                == VillageProsperityEngine.Lifecycle.ABANDONED
                        ? state.economicDay
                        : 0L;
                village.recoveryEligibleDay = delay == Integer.MAX_VALUE
                        ? state.economicDay + 3L
                        : state.economicDay + delay;
            } else if (village.population <= 2 || village.prosperity < 25.0) {
                village.lifecycle = VillageProsperityEngine.Lifecycle.DEVASTATED;
            } else {
                village.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
            }
            if (cause != VillageProsperityEngine.IncidentCause.PLAYER
                    && existingShadow != null
                    && existingShadow.counterfactualVillage != null) {
                applyCounterfactualCasualty(
                        existingShadow.counterfactualVillage,
                        residentId,
                        profession,
                        packedPosition,
                        cause,
                        responsiblePlayer,
                        state.economicDay);
                trimResidentHistory(existingShadow.counterfactualVillage, residentId);
                VillageProsperityEngine.refreshMarketShadow(
                        existingShadow, state.economicDay);
            }
            state.save(path);
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state.villages.put(villageId, before);
            if (shadowBefore == null) {
                state.villageMarketShadows.remove(villageId);
            } else {
                state.villageMarketShadows.put(villageId, shadowBefore);
            }
            lastError = message(exception);
            return false;
        }
    }

    private static void applyCounterfactualCasualty(
            EconomyState.VillageRecord village,
            UUID residentId,
            String profession,
            long packedPosition,
            VillageProsperityEngine.IncidentCause cause,
            UUID responsiblePlayer,
            long day) {
        EconomyState.ResidentRecord resident = residentId == null
                ? null
                : village.residents.get(residentId);
        if (resident == null) {
            resident = nearestResidentWithStatus(
                    village,
                    packedPosition,
                    16.0,
                    VillageProsperityEngine.ResidentStatus.INFECTED);
        }
        VillageProsperityEngine.ResidentStatus previousStatus = resident == null
                ? null
                : resident.status;
        if (resident != null
                && resident.status == VillageProsperityEngine.ResidentStatus.DEAD) {
            // The live record accepted a distinct later casualty after diverging from this
            // counterfactual. Apply its economic effect without overwriting old resident history.
            resident = null;
            previousStatus = null;
        }
        if (resident == null
                && residentId != null
                && !village.residents.containsKey(residentId)) {
            resident = new EconomyState.ResidentRecord();
            resident.residentId = residentId;
            village.residents.put(residentId, resident);
        }
        if (resident != null) {
            resident.profession = profession == null || profession.isBlank()
                    ? "minecraft:none"
                    : profession;
            resident.status = VillageProsperityEngine.ResidentStatus.DEAD;
            resident.lastSeenDay = day;
            resident.lastKnownPos = packedPosition;
        }
        boolean alreadyOutsideProductivePopulation =
                previousStatus == VillageProsperityEngine.ResidentStatus.INFECTED
                        || previousStatus == VillageProsperityEngine.ResidentStatus.EMIGRATED;
        if (!alreadyOutsideProductivePopulation) {
            village.population = Math.max(0, village.population - 1);
            village.observedPopulation = Math.max(0, village.observedPopulation - 1);
        }
        village.lastIncidentDay = day;
        village.lastIncidentCause = cause;
        double safetyLoss = casualtySafetyLoss(cause);
        village.safety = Math.max(0.0, village.safety - safetyLoss);
        village.prosperity = Math.max(0.0, village.prosperity - safetyLoss * 0.55);
        switch (cause) {
            case RAID, PILLAGER, HOSTILE -> village.hostileCasualties++;
            case ENVIRONMENT, UNKNOWN -> village.environmentalCasualties++;
            case PLAYER -> village.playerCasualties++;
            case NONE -> {
            }
        }
        EconomyState.VillageIncident incident = new EconomyState.VillageIncident();
        incident.day = day;
        incident.cause = cause;
        incident.casualties = 1;
        incident.responsiblePlayer = responsiblePlayer;
        incident.marketEligible = true;
        village.incidents.add(incident);
        while (village.incidents.size() > VillageProsperityEngine.INCIDENT_HISTORY_LIMIT) {
            village.incidents.remove(0);
        }
        if (village.population == 0) {
            village.collapseCount = day - village.lastCollapseDay <= 30L
                    ? village.collapseCount + 1
                    : 1;
            village.lastCollapseDay = day;
            int delay = VillageProsperityEngine.recoveryDelayDays(village, cause);
            boolean repeatedCollapse = village.collapseCount >= 3;
            village.lifecycle = repeatedCollapse
                    ? VillageProsperityEngine.Lifecycle.ABANDONED
                    : VillageProsperityEngine.Lifecycle.EXTINCT;
            village.abandonedSinceDay = repeatedCollapse ? day : 0L;
            village.recoveryEligibleDay = delay == Integer.MAX_VALUE
                    ? day + 3L
                    : day + delay;
        } else if (village.population <= 2 || village.prosperity < 25.0) {
            village.lifecycle = VillageProsperityEngine.Lifecycle.DEVASTATED;
        } else {
            village.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
        }
    }

    private static boolean applyResidentStatus(
            EconomyState.VillageRecord village,
            UUID residentId,
            String profession,
            long packedPosition,
            VillageProsperityEngine.ResidentStatus status,
            long day) {
        EconomyState.ResidentRecord resident = village.residents.get(residentId);
        if (resident == null && status == VillageProsperityEngine.ResidentStatus.INFECTED) {
            resident = nearestResidentWithStatus(
                    village,
                    packedPosition,
                    16.0,
                    VillageProsperityEngine.ResidentStatus.INFECTED,
                    VillageProsperityEngine.ResidentStatus.ACTIVE);
        }
        if (resident == null) {
            resident = new EconomyState.ResidentRecord();
            resident.residentId = residentId;
            village.residents.put(residentId, resident);
        }
        if (resident.status == VillageProsperityEngine.ResidentStatus.DEAD) {
            return false;
        }
        VillageProsperityEngine.ResidentStatus previous = resident.status;
        if (profession != null && !profession.isBlank()) {
            resident.profession = profession;
        }
        resident.status = status;
        resident.lastSeenDay = day;
        resident.lastKnownPos = packedPosition;
        if (status == VillageProsperityEngine.ResidentStatus.INFECTED
                && previous != VillageProsperityEngine.ResidentStatus.INFECTED) {
            village.population = Math.max(0, village.population - 1);
            village.observedPopulation = Math.max(0, village.observedPopulation - 1);
            village.safety = Math.max(0.0, village.safety - 5.0);
            village.prosperity = Math.max(0.0, village.prosperity - 2.0);
            if (village.population <= 0) {
                village.lifecycle = VillageProsperityEngine.Lifecycle.DEVASTATED;
            } else if (village.population <= 2) {
                village.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
            }
        }
        return true;
    }

    /**
     * Bounds stale resident history without discarding the record for the transition currently
     * being processed. If no historical record can be removed, validation rejects the transaction
     * instead of accepting an untracked casualty that could be counted again later.
     */
    private static void trimResidentHistory(
            EconomyState.VillageRecord village, UUID protectedResidentId) {
        while (village.residents.size() > VillageProsperityEngine.RESIDENT_HISTORY_LIMIT) {
            UUID removable = village.residents.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(protectedResidentId))
                    .filter(entry -> isEvictableResidentHistory(entry.getValue().status))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (removable == null) {
                break;
            }
            village.residents.remove(removable);
        }
    }

    private static boolean isEvictableResidentHistory(
            VillageProsperityEngine.ResidentStatus status) {
        return status == VillageProsperityEngine.ResidentStatus.AWAY
                || status == VillageProsperityEngine.ResidentStatus.EMIGRATED
                || status == VillageProsperityEngine.ResidentStatus.DEAD;
    }

    private static double casualtySafetyLoss(
            VillageProsperityEngine.IncidentCause cause) {
        return switch (cause) {
            case RAID, PILLAGER -> 18.0;
            case HOSTILE -> 12.0;
            case PLAYER -> 24.0;
            case ENVIRONMENT, UNKNOWN -> 7.0;
            case NONE -> 0.0;
        };
    }

    public synchronized VillageFundingResult fundVillage(
            UUID playerId, UUID villageId, long emeralds) {
        VillageFundContributionResult result = contributeToVillageFund(
                playerId,
                villageId,
                emeralds,
                EconomyState.ProsperityFundType.DIRECT_GRANT,
                EconomyState.DonationPurpose.GENERAL);
        if (!result.contributed()) return VillageFundingResult.notFunded();
        boolean restorationActivated = false;
        if (result.purpose() == EconomyState.DonationPurpose.RESTORATION) {
            spendVillageFund(villageId, result.purpose(), result.amountMicro(), 0L);
            EconomyState.VillageRecord village = state == null ? null : state.existingVillage(villageId);
            restorationActivated = village != null && village.restorationFunded;
        }
        return new VillageFundingResult(true, restorationActivated, result.amountMicro());
    }

    public synchronized VillageFundContributionResult contributeToVillageFund(
            UUID playerId,
            UUID villageId,
            long wholeEmeralds,
            EconomyState.ProsperityFundType type,
            EconomyState.DonationPurpose purpose) {
        long projectId = 0L;
        if (type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP && state != null) {
            EconomyState.VillageRecord village = state.existingVillage(villageId);
            if (village != null) {
                projectId = village.projects.stream()
                        .filter(project -> !project.economicComplete)
                        .mapToLong(project -> project.projectId)
                        .findFirst()
                        .orElse(0L);
            }
        }
        return contributeToVillageFund(
                playerId, villageId, wholeEmeralds, type, purpose, projectId);
    }

    /** Atomically debits the donor and credits an irreversible, village-owned fund balance. */
    public synchronized VillageFundContributionResult contributeToVillageFund(
            UUID playerId,
            UUID villageId,
            long wholeEmeralds,
            EconomyState.ProsperityFundType type,
            EconomyState.DonationPurpose purpose,
            long projectId) {
        Long amountMicro = wholeEmeraldsToMicro(wholeEmeralds);
        if (!prosperityFundPolicy.enabled()
                || state == null
                || path == null
                || playerId == null
                || villageId == null
                || amountMicro == null
                || type == null
                || purpose == null) {
            return VillageFundContributionResult.notContributed();
        }
        EconomyState.Account account = state.existingAccount(playerId);
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        if (account == null || village == null || account.cashMicro < amountMicro) {
            return VillageFundContributionResult.notContributed();
        }
        EconomyState.VillageProject sponsoredProject = type
                        == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP
                ? findProject(village, projectId)
                : null;
        if (type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP
                && (projectId <= 0L
                        || sponsoredProject == null
                        || sponsoredProject.economicComplete)) {
            return VillageFundContributionResult.notContributed();
        }
        EconomyState.DonationPurpose actualPurpose =
                type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP
                        ? EconomyState.donationPurposeForProject(sponsoredProject)
                        : type == EconomyState.ProsperityFundType.DIRECT_GRANT
                                && (village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                                        || village.lifecycle
                                                == VillageProsperityEngine.Lifecycle.EXTINCT)
                        ? EconomyState.DonationPurpose.RESTORATION
                        : purpose;

        EconomyState before = state.copy();
        try {
            account.cashMicro -= amountMicro;
            EconomyState.ProsperityFund fund = village.prosperityFund;
            switch (type) {
                case DIRECT_GRANT -> {
                    long reserve = actualPurpose == EconomyState.DonationPurpose.RESTORATION
                            ? 0L
                            : Math.max(0L, Math.round(
                                    amountMicro * prosperityFundPolicy.emergencyReserveFraction()));
                    fund.emergencyReserveMicro = PortfolioAnalytics.add(
                            fund.emergencyReserveMicro, reserve);
                    fund.spendableMicro.merge(
                            actualPurpose, amountMicro - reserve, PortfolioAnalytics::add);
                }
                case ENDOWMENT -> fund.endowmentPrincipalMicro.merge(
                        actualPurpose, amountMicro, PortfolioAnalytics::add);
                case PROJECT_SPONSORSHIP -> fund.projectSponsorshipMicro.merge(
                        projectId, amountMicro, PortfolioAnalytics::add);
            }
            fund.lifetimeReceivedMicro = PortfolioAnalytics.add(
                    fund.lifetimeReceivedMicro, amountMicro);
            fund.donorTotalsMicro.merge(playerId, amountMicro, PortfolioAnalytics::add);
            EconomyState.FundContribution contribution = new EconomyState.FundContribution();
            contribution.day = state.economicDay;
            contribution.donorId = playerId;
            contribution.type = type;
            contribution.purpose = actualPurpose;
            contribution.projectId = projectId;
            contribution.amountMicro = amountMicro;
            fund.contributions.add(contribution);
            trimFront(fund.contributions, EconomyState.MAX_FUND_LEDGER_ENTRIES);

            EconomyState.DonorRecord donor = state.donors.computeIfAbsent(
                    playerId, ignored -> new EconomyState.DonorRecord());
            donor.lifetimeContributionMicro = PortfolioAnalytics.add(
                    donor.lifetimeContributionMicro, amountMicro);
            if (donor.contributionCount < Integer.MAX_VALUE) donor.contributionCount++;
            donor.byTypeMicro.merge(type, amountMicro, PortfolioAnalytics::add);
            donor.byPurposeMicro.merge(actualPurpose, amountMicro, PortfolioAnalytics::add);

            EconomyState.PortfolioTransactionKind transactionKind = switch (type) {
                case DIRECT_GRANT -> EconomyState.PortfolioTransactionKind.DIRECT_GRANT;
                case ENDOWMENT -> EconomyState.PortfolioTransactionKind.ENDOWMENT;
                case PROJECT_SPONSORSHIP ->
                        EconomyState.PortfolioTransactionKind.PROJECT_SPONSORSHIP;
            };
            PortfolioAnalytics.recordTransaction(
                    account,
                    state.economicDay,
                    transactionKind,
                    actualPurpose.name(),
                    projectId,
                    0.0,
                    amountMicro,
                    0L,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, state, state.economicDay);
            state.save(path);
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return new VillageFundContributionResult(
                    true,
                    amountMicro,
                    type,
                    actualPurpose,
                    projectId,
                    donor.lifetimeContributionMicro,
                    donor.title());
        } catch (IOException | RuntimeException exception) {
            state = before;
            lastError = message(exception);
            return VillageFundContributionResult.notContributed();
        }
    }

    /**
     * Releases a bounded amount into ordinary village resources. It never writes market output
     * directly, so donations influence markets only through sustained village simulation.
     */
    public synchronized VillageFundSpendingResult spendVillageFund(
            UUID villageId,
            EconomyState.DonationPurpose purpose,
            long requestedMicro,
            long sponsoredProjectId) {
        if (!prosperityFundPolicy.enabled()
                || state == null
                || path == null
                || villageId == null
                || purpose == null
                || requestedMicro <= 0L) {
            return VillageFundSpendingResult.notSpent();
        }
        EconomyState before = state.copy();
        try {
            EconomyState.VillageRecord village = state.existingVillage(villageId);
            if (village == null) return VillageFundSpendingResult.notSpent();
            EconomyState.ProsperityFund fund = village.prosperityFund;
            long spentToday = fund.lastSpendingDay == state.economicDay
                    ? fund.spentTodayMicro
                    : 0L;
            long capRemaining = Math.max(
                    0L, prosperityFundPolicy.dailySpendingCapMicro() - spentToday);
            long source = sponsoredProjectId > 0L
                    ? fund.projectSponsorshipMicro.getOrDefault(sponsoredProjectId, 0L)
                    : fund.spendableMicro.getOrDefault(purpose, 0L);
            long requested = Math.min(requestedMicro, Math.min(source, capRemaining));
            if (requested <= 0L) return VillageFundSpendingResult.notSpent();
            long spent;
            EconomyState.DonationPurpose actualPurpose = purpose;
            if (sponsoredProjectId > 0L) {
                EconomyState.VillageProject project = findProject(village, sponsoredProjectId);
                if (project == null || project.economicComplete) {
                    return VillageFundSpendingResult.notSpent();
                }
                actualPurpose = EconomyState.donationPurposeForProject(project);
                spent = EconomyState.applyProjectSponsorshipInputs(
                        village, project, requested, state.economicDay);
                if (spent <= 0L) return VillageFundSpendingResult.notSpent();
                subtractOrRemove(fund.projectSponsorshipMicro, sponsoredProjectId, spent);
            } else {
                spent = EconomyState.applyFundInputs(
                        village, purpose, requested, state.economicDay);
                if (spent <= 0L) return VillageFundSpendingResult.notSpent();
                subtractOrRemove(fund.spendableMicro, purpose, spent);
            }
            if (fund.lastSpendingDay != state.economicDay) {
                fund.lastSpendingDay = state.economicDay;
                fund.spentTodayMicro = 0L;
            }
            fund.spentTodayMicro = PortfolioAnalytics.add(fund.spentTodayMicro, spent);
            fund.lifetimeSpentMicro = PortfolioAnalytics.add(fund.lifetimeSpentMicro, spent);
            state.save(path);
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return new VillageFundSpendingResult(
                    true, spent, actualPurpose, sponsoredProjectId);
        } catch (IOException | RuntimeException exception) {
            state = before;
            lastError = message(exception);
            return VillageFundSpendingResult.notSpent();
        }
    }

    public synchronized VillageFundSnapshot villageFundSnapshot(UUID villageId) {
        if (state == null || villageId == null) return VillageFundSnapshot.empty();
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        if (village == null) return VillageFundSnapshot.empty();
        EconomyState.ProsperityFund fund = village.prosperityFund.copy();
        return new VillageFundSnapshot(
                fund.spendableTotalMicro(),
                fund.endowmentPrincipalTotalMicro(),
                fund.emergencyReserveMicro,
                fund.lifetimeReceivedMicro,
                fund.lifetimeSpentMicro,
                Map.copyOf(fund.spendableMicro),
                Map.copyOf(fund.endowmentPrincipalMicro),
                Map.copyOf(fund.projectSponsorshipMicro),
                Map.copyOf(fund.donorTotalsMicro),
                List.copyOf(fund.contributions));
    }

    public synchronized DonorSnapshot donorSnapshot(UUID playerId) {
        if (state == null || playerId == null) return DonorSnapshot.empty();
        EconomyState.DonorRecord donor = state.donors.get(playerId);
        if (donor == null) return DonorSnapshot.empty();
        return new DonorSnapshot(
                donor.lifetimeContributionMicro,
                donor.contributionCount,
                donor.title(),
                Map.copyOf(donor.byTypeMicro),
                Map.copyOf(donor.byPurposeMicro));
    }

    public synchronized boolean reserveVillageProjectSite(
            UUID villageId, long projectId, long originPos, int totalBlocks) {
        return reserveVillageProjectSite(
                villageId, projectId, originPos, 0L, 0L, totalBlocks);
    }

    public synchronized boolean reserveVillageProjectSite(
            UUID villageId,
            long projectId,
            long originPos,
            long boundsMinPos,
            long boundsMaxPos,
            int totalBlocks) {
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !project.economicComplete
                    || project.materializedComplete
                    || project.abstractOnly
                    || project.originPos != 0L
                    || totalBlocks <= 0) {
                return false;
            }
            project.originPos = originPos;
            project.boundsMinPos = boundsMinPos;
            project.boundsMaxPos = boundsMaxPos;
            project.totalBlocks = totalBlocks;
            project.materializedBlocks = 0;
            project.blocked = false;
            project.retryAfterGameTick = 0L;
            return true;
        });
    }

    /** Releases a reserved project site when construction was blocked before any block was placed. */
    public synchronized boolean releaseVillageProjectSite(UUID villageId, long projectId) {
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || project.materializedComplete
                    || project.materializedBlocks > 0
                    || project.abstractOnly) {
                return false;
            }
            project.originPos = 0L;
            project.boundsMinPos = 0L;
            project.boundsMaxPos = 0L;
            project.totalBlocks = project.type.nominalBlocks();
            project.blocked = false;
            project.retryAfterGameTick = 0L;
            return true;
        });
    }

    /**
     * Defers an unsafe or obstructed physical project using a persistent exponential retry gate.
     * Unstarted reservations are released so the next attempt can select a different safe site;
     * partial deterministic template prefixes retain their original bounds for safe continuation.
     */
    public synchronized boolean deferVillageProjectMaterialization(
            UUID villageId, long projectId, long currentGameTick) {
        return deferVillageProjectMaterialization(
                villageId, projectId, currentGameTick, false);
    }

    /**
     * Defers materialization and optionally retains an unverified reservation. Retention is used
     * when a chunk is unloaded: clearing a persisted origin in that case could orphan blocks that
     * reached the chunk save before their progress reached the economy save.
     */
    public synchronized boolean deferVillageProjectMaterialization(
            UUID villageId,
            long projectId,
            long currentGameTick,
            boolean retainReservation) {
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !project.economicComplete
                    || project.materializedComplete
                    || project.abstractOnly) {
                return false;
            }
            if (project.materializationFailures < Integer.MAX_VALUE) {
                project.materializationFailures++;
            }
            int exponent = Math.min(6, Math.max(0, project.materializationFailures - 1));
            long retryDelay = Math.min(
                    MAX_PROJECT_RETRY_TICKS, INITIAL_PROJECT_RETRY_TICKS << exponent);
            project.retryAfterGameTick = saturatingAdd(
                    Math.max(Math.max(0L, currentGameTick), project.retryAfterGameTick),
                    retryDelay);
            project.blocked = false;
            if (project.materializedBlocks == 0 && !retainReservation) {
                project.originPos = 0L;
                project.boundsMinPos = 0L;
                project.boundsMaxPos = 0L;
                project.totalBlocks = project.type.nominalBlocks();
            }
            return true;
        });
    }

    public synchronized boolean updateVillageProjectMaterialization(
            UUID villageId,
            long projectId,
            int materializedBlocks,
            boolean complete,
            boolean blocked) {
        return mutateVillage(villageId, complete || blocked, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null || project.originPos == 0L || project.abstractOnly) {
                return false;
            }
            int previousBlocks = project.materializedBlocks;
            project.totalBlocks = Math.max(project.totalBlocks, materializedBlocks);
            project.materializedBlocks = Math.max(
                    project.materializedBlocks,
                    Math.min(project.totalBlocks, materializedBlocks));
            project.materializedComplete = complete
                    || project.materializedBlocks >= project.totalBlocks;
            project.blocked = blocked && !project.materializedComplete;
            if (project.materializedBlocks > previousBlocks || project.materializedComplete) {
                project.retryAfterGameTick = 0L;
                project.materializationFailures = 0;
            }
            return true;
        });
    }

    /**
     * Reconciles persisted visual progress with a fully loaded, verified authored template.
     * Unlike normal construction progress this may move the verified prefix backwards after
     * player damage or save recovery.
     */
    public synchronized boolean reconcileVillageProjectMaterialization(
            UUID villageId,
            long projectId,
            int verifiedPrefix,
            int expectedTotal,
            boolean structurallyComplete) {
        if (expectedTotal <= 0 || verifiedPrefix < 0) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null || project.originPos == 0L || project.abstractOnly) {
                return false;
            }
            project.totalBlocks = expectedTotal;
            project.materializedBlocks = Math.min(expectedTotal, verifiedPrefix);
            project.materializedComplete = structurallyComplete
                    && project.materializedBlocks >= expectedTotal;
            if (project.materializedComplete) {
                project.blocked = false;
                project.retryAfterGameTick = 0L;
                project.materializationFailures = 0;
            }
            return true;
        });
    }

    public synchronized boolean consumePendingSettler(UUID villageId) {
        return mutateVillage(villageId, false, village -> {
            if (village.pendingSettlers <= 0) {
                return false;
            }
            village.pendingSettlers--;
            return true;
        });
    }

    public synchronized boolean allowBankerReplacementAt(long packedAnchor) {
        return allowBankerReplacementForRegion(null, packedAnchor);
    }

    /** Uses the persisted bank association before falling back to legacy proximity lookup. */
    public synchronized boolean allowBankerReplacementForRegion(
            Long regionKey, long packedAnchor) {
        if (!villageProsperitySimulationEnabled || state == null) {
            return true;
        }
        EconomyState.VillageRecord village = regionKey == null
                ? null
                : state.existingVillage(state.bankRegionVillageIds.get(regionKey));
        if (village == null) {
            village = nearestVillage("minecraft:overworld", packedAnchor, 160.0);
        }
        if (village == null) {
            return true;
        }
        if (village.population <= 0
                || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                || village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED) {
            return false;
        }
        return village.lastIncidentCause == VillageProsperityEngine.IncidentCause.NONE
                || state.economicDay - village.lastIncidentDay >= 3L;
    }

    private boolean mutateVillage(
            UUID villageId, boolean persistImmediately, VillageMutation mutation) {
        if (state == null || path == null || villageId == null) {
            return false;
        }
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        if (village == null) {
            return false;
        }
        EconomyState.VillageRecord before = village.copy();
        boolean dirtyBefore = dirty;
        try {
            if (!mutation.apply(village)) {
                return false;
            }
            dirty = true;
            if (persistImmediately) {
                state.save(path);
                dirty = false;
                resetSaveSchedule(state.lastWallClockMs);
            }
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state.villages.put(villageId, before);
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    private UUID resolveVillageId(UUID preferredVillageId, VillageObservation observation) {
        if (preferredVillageId != null) {
            EconomyState.VillageRecord preferred = state.existingVillage(preferredVillageId);
            if (preferred != null
                    && Objects.equals(preferred.dimensionKey, observation.dimensionKey())
                    && distanceSquared(preferred.centerPos, observation.centerPos()) <= 72.0 * 72.0) {
                return preferredVillageId;
            }
        }
        UUID byRegion = state.bankRegionVillageIds.get(observation.bankRegionKey());
        if (byRegion != null) {
            EconomyState.VillageRecord mapped = state.existingVillage(byRegion);
            if (mapped != null
                    && Objects.equals(mapped.dimensionKey, observation.dimensionKey())
                    && distanceSquared(mapped.centerPos, observation.centerPos()) <= 96.0 * 96.0) {
                return byRegion;
            }
        }
        EconomyState.VillageRecord nearby = nearestVillage(
                observation.dimensionKey(), observation.centerPos(), 48.0);
        if (nearby != null) {
            return nearby.villageId;
        }
        long first = mix64(state.seed ^ observation.centerPos() ^ observation.bankRegionKey());
        long second = mix64(~state.seed
                ^ Long.rotateLeft(observation.centerPos(), 19)
                ^ Long.rotateLeft(observation.bankRegionKey(), 7));
        UUID candidate = new UUID(first, second);
        while (state.villages.containsKey(candidate)) {
            first = mix64(first + 0x9E3779B97F4A7C15L);
            second = mix64(second + 0xD1B54A32D192ED03L);
            candidate = new UUID(first, second);
        }
        return candidate;
    }

    private EconomyState.VillageRecord nearestVillage(
            String dimensionKey, long packedPosition, double maximumDistance) {
        if (state == null) {
            return null;
        }
        ensureVillageSpatialIndex();
        return villageSpatialIndex.nearest(
                state.villages, dimensionKey, packedPosition, maximumDistance);
    }

    private void ensureVillageSpatialIndex() {
        if (state != null && villageSpatialIndex.size() != state.villages.size()) {
            villageSpatialIndex.rebuild(state.villages);
        }
    }

    private void initializeVillage(
            EconomyState.VillageRecord village, VillageObservation observation) {
        village.dimensionKey = observation.dimensionKey();
        village.centerPos = observation.centerPos();
        village.bankRegionKey = observation.bankRegionKey();
        village.bankAnchorPos = observation.bankAnchorPos();
        village.discoveredDay = state.economicDay;
        village.lastSimulatedDay = state.economicDay;
        village.lastCensusDay = state.economicDay;
        village.population = Math.min(
                VillageProsperityEngine.MAX_ABSTRACT_POPULATION,
                Math.max(0, observation.observedPopulation()));
        village.observedPopulation = village.population;
        village.housingCapacity = Math.max(
                Math.max(4, observation.bedCount()), village.population + 2);
        village.foodSupply = Math.max(60.0, village.population * 24.0);
        village.materialSupply = Math.max(24.0, village.population * 12.0);
        village.treasury = Math.max(8.0, village.population * 2.0);
        village.developmentPoints = Math.max(4.0, village.population * 0.8);
        village.prosperity = 50.0;
        village.safety = observation.raidActive() ? 38.0 : 65.0;
        if (village.population <= 0) {
            village.lifecycle = VillageProsperityEngine.Lifecycle.ABANDONED;
            village.abandonedSinceDay = state.economicDay;
            village.recoveryEligibleDay = saturatingAdd(state.economicDay, 3L);
        } else {
            village.lifecycle = observation.raidActive()
                    ? VillageProsperityEngine.Lifecycle.THREATENED
                    : VillageProsperityEngine.Lifecycle.ACTIVE;
        }
    }

    private void updateVillageObservation(
            EconomyState.VillageRecord village, VillageObservation observation) {
        long previousCensusDay = village.lastCensusDay;
        village.observedPopulation = Math.max(0, observation.observedPopulation());
        village.housingCapacity = Math.max(
                village.housingCapacity,
                Math.max(observation.bedCount(), village.observedPopulation + 1));
        UUID observedRegionOwner = observation.bankRegionKey() == 0L
                ? null
                : state.bankRegionVillageIds.get(observation.bankRegionKey());
        boolean regionAvailable = observedRegionOwner == null
                || observedRegionOwner.equals(village.villageId);
        if (observation.bankAnchorPos() != 0L
                && regionAvailable
                && (observation.bankRegionKey() == 0L
                        || village.bankRegionKey == 0L
                        || village.bankRegionKey == observation.bankRegionKey())) {
            village.bankAnchorPos = observation.bankAnchorPos();
        }

        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ResidentObservation observed : observation.residents()) {
            if (observed == null || observed.residentId() == null) {
                continue;
            }
            seen.add(observed.residentId());
            EconomyState.ResidentRecord resident = village.residents.get(observed.residentId());
            if (resident == null) {
                EconomyState.ResidentRecord infected = nearestResidentWithStatus(
                        village,
                        observed.packedPosition(),
                        16.0,
                        VillageProsperityEngine.ResidentStatus.INFECTED);
                if (infected != null) {
                    village.residents.remove(infected.residentId);
                }
                resident = new EconomyState.ResidentRecord();
                resident.residentId = observed.residentId();
                village.residents.put(observed.residentId(), resident);
            }
            resident.profession = observed.profession() == null || observed.profession().isBlank()
                    ? "minecraft:none"
                    : observed.profession();
            resident.status = VillageProsperityEngine.ResidentStatus.ACTIVE;
            resident.lastSeenDay = state.economicDay;
            resident.lastKnownPos = observed.packedPosition();
        }
        int emigrated = 0;
        for (EconomyState.ResidentRecord resident : village.residents.values()) {
            if (seen.contains(resident.residentId)) {
                continue;
            }
            long missingDays = state.economicDay - resident.lastSeenDay;
            if (resident.status == VillageProsperityEngine.ResidentStatus.ACTIVE
                    && missingDays >= 3L) {
                resident.status = VillageProsperityEngine.ResidentStatus.AWAY;
            }
            if (resident.status == VillageProsperityEngine.ResidentStatus.AWAY
                    && missingDays >= 30L) {
                resident.status = VillageProsperityEngine.ResidentStatus.EMIGRATED;
                emigrated++;
            }
        }
        if (emigrated > 0) {
            village.population = Math.max(
                    village.observedPopulation,
                    Math.max(0, village.population - emigrated));
            if (village.population == 0) {
                // Emigration is not an invented casualty and must not trigger free refugees.
                village.lifecycle = VillageProsperityEngine.Lifecycle.ABANDONED;
                village.abandonedSinceDay = state.economicDay;
                village.recoveryEligibleDay = saturatingAdd(state.economicDay, 3L);
                village.pendingSettlers = 0;
                clearVillageOutputs(village);
            }
        }
        trimResidentHistory(village, null);

        if (village.observedPopulation > village.population) {
            int arrivals = village.observedPopulation - village.population;
            village.population = Math.min(
                    VillageProsperityEngine.MAX_ABSTRACT_POPULATION,
                    village.observedPopulation);
            village.pendingSettlers = Math.max(0, village.pendingSettlers - arrivals);
        }
        if (village.observedPopulation > 0
                && (village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                        || village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED)) {
            village.population = Math.max(village.population, village.observedPopulation);
            village.lifecycle = VillageProsperityEngine.Lifecycle.RECOVERING;
            village.restorationFunded = false;
            village.restorationFund = 0.0;
        }
        if (village.population > 0
                && (observation.raidActive() || observation.hostileCount() >= 4)) {
            village.lifecycle = VillageProsperityEngine.Lifecycle.THREATENED;
            village.safety = Math.max(0.0, village.safety - 1.5);
            village.lastIncidentDay = state.economicDay;
            village.lastIncidentCause = observation.raidActive()
                    ? VillageProsperityEngine.IncidentCause.RAID
                    : VillageProsperityEngine.IncidentCause.HOSTILE;
        }
        if (!villageProsperitySimulationEnabled
                && villageVisualProgressionEnabled
                && previousCensusDay < state.economicDay) {
            if (village.population > 0 || villageAutomaticRecoveryEnabled) {
                VillageProsperityEngine.advanceVisualOnlyPulse(
                        village, state.seed, state.economicDay);
            }
        }
        village.lastCensusDay = state.economicDay;
    }

    private VillageSnapshot villageSnapshot(EconomyState.VillageRecord village) {
        VillageProsperityEngine.VillageFundamentals fundamentals =
                villageMarketIntegrationEnabled
                        ? state.villageFundamentals()
                        : VillageProsperityEngine.VillageFundamentals.neutral();
        return new VillageSnapshot(
                village.copy(),
                fundamentals,
                villageProsperitySimulationEnabled,
                villageVisualProgressionEnabled);
    }

    private static EconomyState.ResidentRecord nearestResidentWithStatus(
            EconomyState.VillageRecord village,
            long packedPosition,
            double maximumDistance,
            VillageProsperityEngine.ResidentStatus... statuses) {
        if (village == null || statuses == null || statuses.length == 0) {
            return null;
        }
        java.util.Set<VillageProsperityEngine.ResidentStatus> accepted =
                java.util.Set.of(statuses);
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        EconomyState.ResidentRecord best = null;
        double bestDistance = maximumDistanceSquared;
        for (EconomyState.ResidentRecord resident : village.residents.values()) {
            if (!accepted.contains(resident.status)) {
                continue;
            }
            double distance = distanceSquared(resident.lastKnownPos, packedPosition);
            if (distance <= bestDistance) {
                best = resident;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static EconomyState.VillageProject findProject(
            EconomyState.VillageRecord village, long projectId) {
        return village.projects.stream()
                .filter(project -> project.projectId == projectId)
                .findFirst()
                .orElse(null);
    }

    private static void clearVillageOutputs(EconomyState.VillageRecord village) {
        village.agricultureOutput = 0.0;
        village.miningOutput = 0.0;
        village.tradeOutput = 0.0;
        village.redstoneOutput = 0.0;
        village.alchemyOutput = 0.0;
        village.transportOutput = 0.0;
        village.securityOutput = 0.0;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double distanceSquared(long first, long second) {
        double dx = unpackX(first) - unpackX(second);
        double dy = unpackY(first) - unpackY(second);
        double dz = unpackZ(first) - unpackZ(second);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    public synchronized String transactionBlockReason(UUID id) {
        if (state == null) {
            return "The economy is not ready";
        }
        long catchUpDays = catchUpDaysRemainingInternal();
        if (catchUpDays > 0L) {
            return "The economy is still processing " + catchUpDays + " catch-up days";
        }
        if (state.pendingInventoryTransactions.containsKey(id)) {
            return "A previous inventory transaction is waiting for recovery";
        }
        return "";
    }

    public synchronized boolean deposit(UUID id, long emeralds) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        return micro != null && creditMicro(id, micro);
    }

    public synchronized boolean creditMicro(UUID id, long microEmeralds) {
        if (microEmeralds <= 0L) {
            return false;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (!canAdd(account.cashMicro, microEmeralds)) {
                return false;
            }
            account.cashMicro += microEmeralds;
            account.totalContributionsMicro = PortfolioAnalytics.add(
                    account.totalContributionsMicro, microEmeralds);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CASH_IN,
                    "CASH",
                    0L,
                    0.0,
                    microEmeralds,
                    0L,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            return true;
        });
    }

    public synchronized long withdraw(UUID id, long emeralds) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return 0L;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (account.cashMicro < micro) {
                return false;
            }
            account.cashMicro -= micro;
            account.totalWithdrawalsMicro = PortfolioAnalytics.add(
                    account.totalWithdrawalsMicro, micro);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CASH_OUT,
                    "CASH",
                    0L,
                    0.0,
                    micro,
                    0L,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            return true;
        }) ? emeralds : 0L;
    }

    public synchronized boolean moveSavings(UUID id, long emeralds, boolean intoSavings) {
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return false;
        }
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            if (intoSavings) {
                if (account.cashMicro < micro || !canAdd(account.savingsMicro, micro)) {
                    return false;
                }
                account.cashMicro -= micro;
                account.savingsMicro += micro;
                PortfolioAnalytics.recordTransaction(
                        account,
                        current.economicDay,
                        EconomyState.PortfolioTransactionKind.SAVINGS_DEPOSIT,
                        "SAVINGS",
                        0L,
                        0.0,
                        micro,
                        0L,
                        0L);
            } else {
                if (account.savingsMicro < micro || !canAdd(account.cashMicro, micro)) {
                    return false;
                }
                account.savingsMicro -= micro;
                account.cashMicro += micro;
                PortfolioAnalytics.recordTransaction(
                        account,
                        current.economicDay,
                        EconomyState.PortfolioTransactionKind.SAVINGS_WITHDRAW,
                        "SAVINGS",
                        0L,
                        0.0,
                        micro,
                        0L,
                        0L);
            }
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            return true;
        });
    }

    public synchronized boolean openCd(UUID id, long emeralds, int termDays) {
        return openCdPosition(id, emeralds, termDays) > 0L;
    }

    /** Opens another independently maturing CD and returns its stable position id, or zero. */
    public synchronized long openCdPosition(UUID id, long emeralds, int termDays) {
        if (!supportedTerm(termDays)) {
            return 0L;
        }
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return 0L;
        }
        long[] openedId = {0L};
        boolean opened = mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            EconomyState.ensurePositionCollections(account);
            if (account.cashMicro < micro
                    || account.cdPositions.size() >= EconomyState.MAX_TERM_POSITIONS) {
                return false;
            }
            account.cashMicro -= micro;
            EconomyState.CdPosition position = new EconomyState.CdPosition();
            position.positionId = EconomyState.nextPositionId(account);
            position.principalMicro = micro;
            position.valueMicro = micro;
            position.openDay = current.economicDay;
            position.maturityDay = current.economicDay + termDays;
            position.annualRate = EconomyEngine.cdAnnualRate(current.regime, termDays);
            account.cdPositions.put(position.positionId, position);
            EconomyState.syncLegacyProductViews(account);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CD_OPEN,
                    "CD",
                    position.positionId,
                    0.0,
                    micro,
                    micro,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            openedId[0] = position.positionId;
            return true;
        });
        return opened ? openedId[0] : 0L;
    }

    public synchronized CdCloseResult closeCd(UUID id) {
        if (state == null) {
            return CdCloseResult.notClosed();
        }
        EconomyState.Account account = state.existingAccount(id);
        if (account == null) {
            return CdCloseResult.notClosed();
        }
        EconomyState.ensurePositionCollections(account);
        Long positionId = account.cdPositions.keySet().stream().findFirst().orElse(null);
        return positionId == null ? CdCloseResult.notClosed() : closeCd(id, positionId);
    }

    public synchronized CdCloseResult closeCd(UUID id, long positionId) {
        CdCloseResult[] result = {CdCloseResult.notClosed()};
        boolean success = mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            EconomyState.ensurePositionCollections(account);
            EconomyState.CdPosition position = account.cdPositions.get(positionId);
            if (position == null) {
                return false;
            }
            boolean matured = current.economicDay >= position.maturityDay;
            long penalty = matured
                    ? 0L
                    : Math.max(1L, Math.round(position.principalMicro * 0.01));
            long payout = matured
                    ? position.valueMicro
                    : Math.max(0L, position.principalMicro - penalty);
            if (!canAdd(account.cashMicro, payout)) {
                return false;
            }
            account.cashMicro += payout;
            result[0] = new CdCloseResult(true, payout, penalty, matured);
            long realized = PortfolioAnalytics.subtract(payout, position.principalMicro);
            account.realizedGainMicro = PortfolioAnalytics.add(
                    account.realizedGainMicro, realized);
            account.cdPositions.remove(positionId);
            EconomyState.syncLegacyProductViews(account);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CD_CLOSE,
                    "CD",
                    positionId,
                    0.0,
                    payout,
                    position.principalMicro,
                    realized);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            return true;
        });
        return success ? result[0] : CdCloseResult.notClosed();
    }

    public synchronized boolean fundLoan(UUID id, long emeralds, int termDays) {
        return openLoanPosition(id, emeralds, termDays) > 0L;
    }

    /** Funds another independent villager loan and returns its stable position id, or zero. */
    public synchronized long openLoanPosition(UUID id, long emeralds, int termDays) {
        if (!supportedTerm(termDays)) {
            return 0L;
        }
        Long micro = wholeEmeraldsToMicro(emeralds);
        if (micro == null) {
            return 0L;
        }
        long[] openedId = {0L};
        boolean opened = mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            EconomyState.ensurePositionCollections(account);
            if (account.cashMicro < micro
                    || account.loanPositions.size() >= EconomyState.MAX_TERM_POSITIONS) {
                return false;
            }
            account.cashMicro -= micro;
            EconomyState.LoanPosition position = new EconomyState.LoanPosition();
            position.positionId = EconomyState.nextPositionId(account);
            position.principalMicro = micro;
            position.valueMicro = micro;
            position.openDay = current.economicDay;
            position.maturityDay = current.economicDay + termDays;
            position.serial = account.loanSerial == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : account.loanSerial + 1L;
            account.loanSerial = position.serial;
            position.annualRate = EconomyEngine.villagerLoanAnnualYield(
                    current.regime, termDays);
            account.loanPositions.put(position.positionId, position);
            EconomyState.syncLegacyProductViews(account);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.LENDING_OPEN,
                    "LENDING",
                    position.positionId,
                    0.0,
                    micro,
                    micro,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            openedId[0] = position.positionId;
            return true;
        });
        return opened ? openedId[0] : 0L;
    }

    public synchronized LoanCollectionResult collectLoan(UUID id) {
        if (state == null) {
            return LoanCollectionResult.notCollected();
        }
        EconomyState.Account account = state.existingAccount(id);
        if (account == null) {
            return LoanCollectionResult.notCollected();
        }
        EconomyState.ensurePositionCollections(account);
        Long positionId = account.loanPositions.values().stream()
                .filter(position -> position.resolved && state.economicDay >= position.maturityDay)
                .map(position -> position.positionId)
                .findFirst()
                .orElse(null);
        return positionId == null
                ? LoanCollectionResult.notCollected()
                : collectLoan(id, positionId);
    }

    public synchronized LoanCollectionResult collectLoan(UUID id, long positionId) {
        LoanCollectionResult[] result = {LoanCollectionResult.notCollected()};
        boolean success = mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            EconomyState.ensurePositionCollections(account);
            EconomyState.LoanPosition position = account.loanPositions.get(positionId);
            if (position == null
                    || current.economicDay < position.maturityDay
                    || !position.resolved) {
                return false;
            }
            if (!canAdd(account.cashMicro, position.valueMicro)) {
                return false;
            }
            account.cashMicro += position.valueMicro;
            result[0] = new LoanCollectionResult(
                    true,
                    position.valueMicro,
                    position.principalMicro,
                    position.recoveryRate,
                    position.outcome);
            long realized = PortfolioAnalytics.subtract(
                    position.valueMicro, position.principalMicro);
            account.realizedGainMicro = PortfolioAnalytics.add(
                    account.realizedGainMicro, realized);
            account.loanPositions.remove(positionId);
            EconomyState.syncLegacyProductViews(account);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.LENDING_CLOSE,
                    "LENDING",
                    positionId,
                    0.0,
                    position.valueMicro,
                    position.principalMicro,
                    realized);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
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
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            PortfolioAnalytics.migrateLegacyBasis(account, current);
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
            account.shareCostBasisMicro.merge(
                    normalized, micro, PortfolioAnalytics::add);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.BUY,
                    normalized,
                    0L,
                    purchasedShares,
                    micro,
                    micro,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            return true;
        });
    }

    public synchronized boolean sell(UUID id, String ticker, double shares) {
        if (ticker == null || !Double.isFinite(shares) || shares <= 0.0) {
            return false;
        }
        String normalized = ticker.toUpperCase(Locale.ROOT);
        return mutatePlayer(id, false, current -> {
            EconomyState.Account account = current.account(id);
            PortfolioAnalytics.migrateLegacyBasis(account, current);
            double held = account.shares.getOrDefault(normalized, 0.0);
            Double marketPrice = current.prices.get(normalized);
            if (marketPrice == null || shares > held) {
                return false;
            }
            long proceeds = emeraldsToMicro(
                    shares * marketPrice * (1.0 - EconomyEngine.TRADE_SPREAD));
            if (proceeds < 0L || !canAdd(account.cashMicro, proceeds)) {
                return false;
            }
            long oldBasis = Math.max(
                    0L, account.shareCostBasisMicro.getOrDefault(normalized, 0L));
            long removedBasis = shares >= held - Math.ulp(held)
                    ? oldBasis
                    : Math.min(oldBasis, Math.round(oldBasis * (shares / held)));
            double remaining = held - shares;
            if (remaining <= Math.ulp(held)) {
                account.shares.remove(normalized);
                account.shareCostBasisMicro.remove(normalized);
            } else {
                account.shares.put(normalized, remaining);
                account.shareCostBasisMicro.put(normalized, oldBasis - removedBasis);
            }
            account.cashMicro += proceeds;
            long realized = PortfolioAnalytics.subtract(proceeds, removedBasis);
            account.realizedGainMicro = PortfolioAnalytics.add(
                    account.realizedGainMicro, realized);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.SELL,
                    normalized,
                    0L,
                    shares,
                    proceeds,
                    removedBasis,
                    realized);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            return true;
        });
    }

    public synchronized long quoteResourceValueMicro(String resourceId, int count) {
        return state == null
                ? -1L
                : EconomyEngine.resourceExchangeValueMicro(
                        resourceId, count, state.commodityPrices);
    }

    /** Creates a durable PREPARED journal record before items leave the inventory. */
    public synchronized EconomyState.PendingInventoryTransaction prepareInventoryCredit(
            UUID playerId,
            EconomyState.InventoryTransactionKind kind,
            String itemKey,
            int itemCount,
            int inventoryCountBefore,
            long creditMicro) {
        if (kind == null
                || kind == EconomyState.InventoryTransactionKind.WITHDRAWAL
                || itemKey == null
                || itemKey.isBlank()
                || itemCount <= 0
                || itemCount > MAX_INVENTORY_ITEM_TRANSACTION
                || inventoryCountBefore < itemCount
                || creditMicro <= 0L) {
            return null;
        }
        UUID transactionId = UUID.randomUUID();
        boolean success = mutatePlayer(playerId, true, current -> {
            if (current.pendingInventoryTransactions.containsKey(playerId)) {
                return false;
            }
            EconomyState.Account account = current.account(playerId);
            if (!canAdd(account.cashMicro, creditMicro)) {
                return false;
            }
            EconomyState.PendingInventoryTransaction transaction =
                    new EconomyState.PendingInventoryTransaction();
            transaction.transactionId = transactionId;
            transaction.playerId = playerId;
            transaction.kind = kind;
            transaction.stage = EconomyState.InventoryTransactionStage.PREPARED;
            transaction.itemKey = itemKey;
            transaction.itemCount = itemCount;
            transaction.inventoryCountBefore = inventoryCountBefore;
            transaction.bankDeltaMicro = creditMicro;
            transaction.createdEconomicDay = current.economicDay;
            transaction.createdWallClockMs = current.lastWallClockMs;
            current.pendingInventoryTransactions.put(playerId, transaction);
            return true;
        });
        return success ? pendingInventoryTransaction(playerId) : null;
    }

    /** Applies a prepared bank credit after the corresponding items have left the inventory. */
    public synchronized boolean commitPreparedInventoryCredit(
            UUID playerId,
            UUID transactionId) {
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.stage != EconomyState.InventoryTransactionStage.PREPARED
                    || !transaction.creditsBank()) {
                return false;
            }
            EconomyState.Account account = current.account(playerId);
            if (!canAdd(account.cashMicro, transaction.bankDeltaMicro)) {
                return false;
            }
            account.cashMicro += transaction.bankDeltaMicro;
            account.totalContributionsMicro = PortfolioAnalytics.add(
                    account.totalContributionsMicro, transaction.bankDeltaMicro);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CASH_IN,
                    transaction.kind.name(),
                    0L,
                    0.0,
                    transaction.bankDeltaMicro,
                    0L,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            transaction.stage = EconomyState.InventoryTransactionStage.BANK_COMMITTED;
            return true;
        });
    }

    /** Debits bank cash and records a committed withdrawal before items enter the inventory. */
    public synchronized EconomyState.PendingInventoryTransaction beginInventoryWithdrawal(
            UUID playerId,
            int itemCount,
            int inventoryCountBefore) {
        Long debitMicro = wholeEmeraldsToMicro(itemCount);
        if (debitMicro == null
                || itemCount > MAX_INVENTORY_ITEM_TRANSACTION
                || inventoryCountBefore < 0) {
            return null;
        }
        UUID transactionId = UUID.randomUUID();
        boolean success = mutatePlayer(playerId, true, current -> {
            if (current.pendingInventoryTransactions.containsKey(playerId)) {
                return false;
            }
            EconomyState.Account account = current.account(playerId);
            if (account.cashMicro < debitMicro) {
                return false;
            }
            account.cashMicro -= debitMicro;
            account.totalWithdrawalsMicro = PortfolioAnalytics.add(
                    account.totalWithdrawalsMicro, debitMicro);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CASH_OUT,
                    "WITHDRAWAL",
                    0L,
                    0.0,
                    debitMicro,
                    0L,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            EconomyState.PendingInventoryTransaction transaction =
                    new EconomyState.PendingInventoryTransaction();
            transaction.transactionId = transactionId;
            transaction.playerId = playerId;
            transaction.kind = EconomyState.InventoryTransactionKind.WITHDRAWAL;
            transaction.stage = EconomyState.InventoryTransactionStage.BANK_COMMITTED;
            transaction.itemKey = "emerald";
            transaction.itemCount = itemCount;
            transaction.inventoryCountBefore = inventoryCountBefore;
            transaction.bankDeltaMicro = -debitMicro;
            transaction.createdEconomicDay = current.economicDay;
            transaction.createdWallClockMs = current.lastWallClockMs;
            current.pendingInventoryTransactions.put(playerId, transaction);
            return true;
        });
        return success ? pendingInventoryTransaction(playerId) : null;
    }

    /** Returns an undelivered portion of a committed withdrawal to bank cash. */
    public synchronized boolean reducePendingWithdrawal(
            UUID playerId,
            UUID transactionId,
            int undeliveredCount) {
        if (undeliveredCount <= 0) {
            return true;
        }
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.kind != EconomyState.InventoryTransactionKind.WITHDRAWAL
                    || transaction.stage != EconomyState.InventoryTransactionStage.BANK_COMMITTED
                    || undeliveredCount > transaction.itemCount) {
                return false;
            }
            long refundMicro = undeliveredCount * EconomyState.MICRO;
            EconomyState.Account account = current.account(playerId);
            if (!canAdd(account.cashMicro, refundMicro)) {
                return false;
            }
            account.cashMicro += refundMicro;
            account.totalWithdrawalsMicro = Math.max(
                    0L, account.totalWithdrawalsMicro - refundMicro);
            PortfolioAnalytics.recordTransaction(
                    account,
                    current.economicDay,
                    EconomyState.PortfolioTransactionKind.CASH_IN,
                    "WITHDRAWAL_REFUND",
                    0L,
                    0.0,
                    refundMicro,
                    0L,
                    0L);
            PortfolioAnalytics.recordNetWorth(account, current, current.economicDay);
            transaction.itemCount -= undeliveredCount;
            if (transaction.itemCount == 0) {
                current.pendingInventoryTransactions.remove(playerId);
            } else {
                transaction.bankDeltaMicro = -transaction.itemCount * EconomyState.MICRO;
            }
            return true;
        });
    }

    public synchronized boolean completeInventoryTransaction(
            UUID playerId,
            UUID transactionId) {
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.stage != EconomyState.InventoryTransactionStage.BANK_COMMITTED) {
                return false;
            }
            current.pendingInventoryTransactions.remove(playerId);
            return true;
        });
    }

    public synchronized boolean cancelPreparedInventoryTransaction(
            UUID playerId,
            UUID transactionId) {
        return mutatePlayer(playerId, true, current -> {
            EconomyState.PendingInventoryTransaction transaction =
                    matchingTransaction(current, playerId, transactionId);
            if (transaction == null
                    || transaction.stage != EconomyState.InventoryTransactionStage.PREPARED) {
                return false;
            }
            current.pendingInventoryTransactions.remove(playerId);
            return true;
        });
    }

    public synchronized EconomyState.PendingInventoryTransaction pendingInventoryTransaction(
            UUID playerId) {
        if (state == null) {
            return null;
        }
        EconomyState.PendingInventoryTransaction transaction =
                state.pendingInventoryTransactions.get(playerId);
        return transaction == null ? null : transaction.copy();
    }

    private boolean observeProgress(long now, long gameTicks, long maximumDaysToAdvance) {
        long safeNow = Math.max(0L, now);
        long safeGameTicks = Math.max(0L, gameTicks);
        long trustedNow = Math.max(safeNow, state.lastWallClockMs);
        long wallDeltaMs = trustedNow - state.lastWallClockMs;
        long gameDeltaTicks = safeGameTicks >= state.lastGameTicks
                ? safeGameTicks - state.lastGameTicks
                : 0L;
        long gameDeltaMs = gameDeltaTicks > Long.MAX_VALUE / MILLIS_PER_GAME_TICK
                ? Long.MAX_VALUE
                : gameDeltaTicks * MILLIS_PER_GAME_TICK;
        boolean changed = wallDeltaMs > 0L || safeGameTicks != state.lastGameTicks;

        state.lastWallClockMs = trustedNow;
        state.lastGameTicks = safeGameTicks;
        long elapsedEconomicMs = Math.max(wallDeltaMs, gameDeltaMs);
        state.pendingEconomicMillis = cappedAdd(
                state.pendingEconomicMillis,
                elapsedEconomicMs,
                MAX_PENDING_ECONOMIC_MS);

        long availableDays = state.pendingEconomicMillis / MILLIS_PER_MINECRAFT_DAY;
        long days = Math.min(adaptiveCatchUpBatchDays(maximumDaysToAdvance), availableDays);
        if (days <= 0L) {
            return changed;
        }

        advance(days);
        state.pendingEconomicMillis -= days * MILLIS_PER_MINECRAFT_DAY;
        return true;
    }

    private long adaptiveCatchUpBatchDays(long requestedMaximum) {
        long workBudget = requestedMaximum >= STARTUP_CATCH_UP_BATCH_DAYS
                ? STARTUP_CATCH_UP_WORK_BUDGET
                : TICK_CATCH_UP_WORK_BUDGET;
        long workUnitsPerDay = 1L
                + (long) state.villages.size()
                + state.villageMarketShadows.size()
                + state.accounts.size();
        return Math.min(requestedMaximum, Math.max(1L, workBudget / workUnitsPerDay));
    }

    private void advance(long days) {
        for (long day = 0L; day < days; day++) {
            state.advanceOneDay(
                    villageProsperitySimulationEnabled,
                    villageVisualProgressionEnabled,
                    villageMarketIntegrationEnabled,
                    villageAutomaticRecoveryEnabled,
                    prosperityFundPolicy.enabled(),
                    prosperityFundPolicy.endowmentAnnualPayoutRate(),
                    prosperityFundPolicy.emergencyReserveFraction(),
                    prosperityFundPolicy.dailySpendingCapMicro());
        }
    }

    private long catchUpDaysRemainingInternal() {
        return state.pendingEconomicMillis / MILLIS_PER_MINECRAFT_DAY;
    }

    private boolean persistDirtyState(long now) {
        try {
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            dirty = true;
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    private boolean mutatePlayer(UUID playerId, boolean allowPending, Mutation mutation) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        Objects.requireNonNull(playerId, "playerId");

        long now = state.lastWallClockMs;
        if (catchUpDaysRemainingInternal() > 0L) {
            lastError = "Economy catch-up is still in progress";
            return false;
        }
        if (!allowPending && state.pendingInventoryTransactions.containsKey(playerId)) {
            lastError = "A pending inventory transaction must be recovered first";
            return false;
        }

        EconomyState.Account existingAccount = state.existingAccount(playerId);
        EconomyState.Account accountBefore = existingAccount == null
                ? null
                : existingAccount.copy();
        EconomyState.PendingInventoryTransaction existingTransaction =
                state.pendingInventoryTransactions.get(playerId);
        EconomyState.PendingInventoryTransaction transactionBefore =
                existingTransaction == null ? null : existingTransaction.copy();
        boolean dirtyBefore = dirty;
        try {
            if (!mutation.apply(state)) {
                restorePlayerState(playerId, accountBefore, transactionBefore);
                dirty = dirtyBefore;
                lastError = "";
                return false;
            }
            state.save(path);
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            restorePlayerState(playerId, accountBefore, transactionBefore);
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(now);
            return false;
        }
    }

    private void restorePlayerState(
            UUID playerId,
            EconomyState.Account account,
            EconomyState.PendingInventoryTransaction transaction) {
        if (account == null) {
            state.accounts.remove(playerId);
        } else {
            state.accounts.put(playerId, account);
        }
        if (transaction == null) {
            state.pendingInventoryTransactions.remove(playerId);
        } else {
            state.pendingInventoryTransactions.put(playerId, transaction);
        }
    }

    private static Map<String, java.util.List<Double>> copyHistory(
            Map<String, java.util.List<Double>> history) {
        Map<String, java.util.List<Double>> copy = new java.util.LinkedHashMap<>();
        history.forEach((ticker, values) -> copy.put(ticker, java.util.List.copyOf(values)));
        return Map.copyOf(copy);
    }

    private static EconomyState.PendingInventoryTransaction matchingTransaction(
            EconomyState current,
            UUID playerId,
            UUID transactionId) {
        EconomyState.PendingInventoryTransaction transaction =
                current.pendingInventoryTransactions.get(playerId);
        return transaction != null && transaction.transactionId.equals(transactionId)
                ? transaction
                : null;
    }

    private void resetSaveSchedule(long now) {
        nextAutomaticSaveMs = saturatingAdd(now, AUTO_SAVE_INTERVAL_MS);
        nextSaveRetryMs = 0L;
        saveRetryDelayMs = INITIAL_SAVE_RETRY_MS;
    }

    private void scheduleSaveRetry(long now) {
        nextSaveRetryMs = saturatingAdd(now, saveRetryDelayMs);
        nextAutomaticSaveMs = nextSaveRetryMs;
        saveRetryDelayMs = Math.min(MAX_SAVE_RETRY_MS, saveRetryDelayMs * 2L);
    }

    private static long cappedAdd(long current, long addition, long cap) {
        if (addition <= 0L || current >= cap) {
            return Math.min(current, cap);
        }
        return addition > cap - current ? cap : current + addition;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE
                : left + right;
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
        return emeralds <= 0L
                        || emeralds > MAX_WHOLE_EMERALD_TRANSACTION
                        || emeralds > Long.MAX_VALUE / EconomyState.MICRO
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

    private static <K> void subtractOrRemove(Map<K, Long> values, K key, long amount) {
        long remaining = values.getOrDefault(key, 0L) - amount;
        if (remaining <= 0L) values.remove(key);
        else values.put(key, remaining);
    }

    private static <T> void trimFront(List<T> values, int maximumSize) {
        int excess = values.size() - maximumSize;
        if (excess > 0) values.subList(0, excess).clear();
    }

    private static String message(Exception exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank()
                ? exception.getClass().getSimpleName()
                : value;
    }

    @FunctionalInterface
    private interface VillageMutation {
        boolean apply(EconomyState.VillageRecord village) throws IOException;
    }

    public record ResidentObservation(
            UUID residentId, String profession, long packedPosition) {
    }

    public record VillageObservation(
            String dimensionKey,
            long centerPos,
            long bankRegionKey,
            long bankAnchorPos,
            int observedPopulation,
            int bedCount,
            int hostileCount,
            boolean raidActive,
            List<ResidentObservation> residents) {
        public VillageObservation {
            dimensionKey = dimensionKey == null || dimensionKey.isBlank()
                    ? "minecraft:overworld"
                    : dimensionKey;
            observedPopulation = Math.max(0, observedPopulation);
            bedCount = Math.max(0, bedCount);
            hostileCount = Math.max(0, hostileCount);
            residents = residents == null ? List.of() : List.copyOf(residents);
        }
    }

    public record VillageSnapshot(
            EconomyState.VillageRecord village,
            VillageProsperityEngine.VillageFundamentals fundamentals,
            boolean simulationEnabled,
            boolean visualProgressionEnabled) {
    }

    public record VillageFundingResult(
            boolean funded, boolean restorationActivated, long contributionMicro) {
        static VillageFundingResult notFunded() {
            return new VillageFundingResult(false, false, 0L);
        }
    }

    public record ProsperityFundPolicy(
            boolean enabled,
            double endowmentAnnualPayoutRate,
            double emergencyReserveFraction,
            long dailySpendingCapMicro) {
        public ProsperityFundPolicy {
            if (!Double.isFinite(endowmentAnnualPayoutRate)
                    || endowmentAnnualPayoutRate < 0.0
                    || endowmentAnnualPayoutRate > 1.0
                    || !Double.isFinite(emergencyReserveFraction)
                    || emergencyReserveFraction < 0.0
                    || emergencyReserveFraction > 0.90
                    || dailySpendingCapMicro <= 0L) {
                throw new IllegalArgumentException("Invalid prosperity-fund policy");
            }
        }

        public static ProsperityFundPolicy defaults() {
            return new ProsperityFundPolicy(true, 0.04, 0.10, 64L * EconomyState.MICRO);
        }
    }

    public record VillageFundContributionResult(
            boolean contributed,
            long amountMicro,
            EconomyState.ProsperityFundType type,
            EconomyState.DonationPurpose purpose,
            long projectId,
            long donorLifetimeMicro,
            EconomyState.DonorTitle donorTitle) {
        static VillageFundContributionResult notContributed() {
            return new VillageFundContributionResult(
                    false,
                    0L,
                    EconomyState.ProsperityFundType.DIRECT_GRANT,
                    EconomyState.DonationPurpose.GENERAL,
                    0L,
                    0L,
                    EconomyState.DonorTitle.NONE);
        }
    }

    public record VillageFundSpendingResult(
            boolean spent,
            long amountMicro,
            EconomyState.DonationPurpose purpose,
            long projectId) {
        static VillageFundSpendingResult notSpent() {
            return new VillageFundSpendingResult(
                    false, 0L, EconomyState.DonationPurpose.GENERAL, 0L);
        }
    }

    public record VillageFundSnapshot(
            long spendableTotalMicro,
            long endowmentPrincipalMicro,
            long emergencyReserveMicro,
            long lifetimeReceivedMicro,
            long lifetimeSpentMicro,
            Map<EconomyState.DonationPurpose, Long> spendableByPurposeMicro,
            Map<EconomyState.DonationPurpose, Long> endowmentByPurposeMicro,
            Map<Long, Long> projectSponsorshipMicro,
            Map<UUID, Long> donorTotalsMicro,
            List<EconomyState.FundContribution> contributions) {
        static VillageFundSnapshot empty() {
            return new VillageFundSnapshot(
                    0L, 0L, 0L, 0L, 0L,
                    Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        }
    }

    public record DonorSnapshot(
            long lifetimeContributionMicro,
            int contributionCount,
            EconomyState.DonorTitle title,
            Map<EconomyState.ProsperityFundType, Long> byTypeMicro,
            Map<EconomyState.DonationPurpose, Long> byPurposeMicro) {
        static DonorSnapshot empty() {
            return new DonorSnapshot(
                    0L, 0, EconomyState.DonorTitle.NONE, Map.of(), Map.of());
        }
    }

    @FunctionalInterface
    private interface Mutation {
        boolean apply(EconomyState state) throws IOException;
    }

    public record MarketSnapshot(
            long economicDay,
            EconomyEngine.Regime regime,
            EconomyEngine.MarketEvent lastMarketEvent,
            long lastMarketEventDay,
            Map<String, Double> prices,
            Map<String, Double> commodityPrices,
            Map<String, java.util.List<Double>> priceHistory,
            long catchUpDaysRemaining,
            boolean dirty) {
    }

    public record PortfolioSnapshot(
            long economicDay,
            EconomyState.Account account,
            Map<String, Double> prices,
            double netWorth,
            EconomyState.PendingInventoryTransaction pendingTransaction,
            long catchUpDaysRemaining) {
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
