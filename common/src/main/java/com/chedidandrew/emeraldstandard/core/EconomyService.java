package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /** Runtime mirror of the persisted commandable Overworld-clock baseline. */
    private long lastOverworldClockTicks;
    /** Death observations retained in memory but not yet known to have reached durable storage. */
    private final Set<Long> unpersistedBankerDeathRegions = new HashSet<>();
    private boolean villageProsperitySimulationEnabled = true;
    private boolean villageVisualProgressionEnabled = true;
    private boolean villageMarketIntegrationEnabled = true;
    private boolean villageAutomaticRecoveryEnabled = true;
    private boolean peacefulVillageGrowth;
    private boolean marketEventsEnabled = true;
    private boolean offlineProgressionEnabled = true;
    private long maximumOfflineDays = MAX_TRUSTED_CATCH_UP_DAYS;
    private ProsperityFundPolicy prosperityFundPolicy = ProsperityFundPolicy.defaults();

    public enum BankerConversionStatus {
        APPLIED,
        ALREADY_APPLIED,
        RETRY_IO,
        STALE,
        CONFLICT
    }

    public record GeneratedBankerConversion(
            long regionKey,
            UUID rootCanonicalId,
            UUID immediateSourceId,
            UUID targetId,
            String targetDimension,
            long targetPos,
            EconomyState.BankerConversionPhase phase,
            EconomyState.BankerConversionDisposition disposition) {
        public boolean continuingBanker() {
            return disposition == EconomyState.BankerConversionDisposition.CONTINUE_BANKER;
        }
    }

    public record BankerConversionResult(
            BankerConversionStatus status, GeneratedBankerConversion conversion) {
        public boolean accepted() {
            return status == BankerConversionStatus.APPLIED
                    || status == BankerConversionStatus.ALREADY_APPLIED;
        }
    }

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

    /** Set from the authoritative world difficulty before startup catch-up and every server tick. */
    public synchronized void setPeacefulVillageGrowth(boolean peaceful) {
        peacefulVillageGrowth = peaceful;
    }

    public synchronized boolean villageVisualProgressionEnabled() {
        return villageVisualProgressionEnabled;
    }

    public synchronized void configureMarketEvents(boolean enabled) {
        marketEventsEnabled = enabled;
    }

    public synchronized boolean marketEventsEnabled() {
        return marketEventsEnabled;
    }

    /**
     * Configures wall-clock progression without weakening the absolute catch-up safety limit.
     * Game-time progression remains active when offline progression is disabled.
     */
    public synchronized void configureEconomicClock(
            boolean offlineEnabled, long maximumOfflineDays) {
        if (maximumOfflineDays < 1L || maximumOfflineDays > MAX_TRUSTED_CATCH_UP_DAYS) {
            throw new IllegalArgumentException(
                    "Maximum offline days must be between 1 and "
                            + MAX_TRUSTED_CATCH_UP_DAYS);
        }
        offlineProgressionEnabled = offlineEnabled;
        this.maximumOfflineDays = maximumOfflineDays;
    }

    public synchronized boolean offlineProgressionEnabled() {
        return offlineProgressionEnabled;
    }

    public synchronized long maximumOfflineDays() {
        return maximumOfflineDays;
    }

    public synchronized void configureProsperityFund(ProsperityFundPolicy policy) {
        prosperityFundPolicy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized void configureProsperityFund(
            boolean enabled,
            double endowmentAnnualPayoutRate,
            double emergencyReserveFraction,
            long dailySpendingCapEmeralds) {
        configureProsperityFund(
                enabled,
                endowmentAnnualPayoutRate,
                emergencyReserveFraction,
                dailySpendingCapEmeralds,
                false);
    }

    public synchronized void configureProsperityFund(
            boolean enabled,
            double endowmentAnnualPayoutRate,
            double emergencyReserveFraction,
            long dailySpendingCapEmeralds,
            boolean fastTrackCapitalEnabled) {
        Long capMicro = dailySpendingCapEmeralds <= 0L
                        || dailySpendingCapEmeralds > MAX_WHOLE_EMERALD_TRANSACTION
                ? null
                : wholeEmeraldsToMicro(dailySpendingCapEmeralds);
        if (capMicro == null) {
            throw new IllegalArgumentException("Daily prosperity-fund spending cap is invalid");
        }
        configureProsperityFund(new ProsperityFundPolicy(
                enabled,
                endowmentAnnualPayoutRate,
                emergencyReserveFraction,
                capMicro,
                fastTrackCapitalEnabled));
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
        startInternal(
                worldDataDirectory,
                newSeed,
                now,
                gameTicks,
                gameTicks,
                false);
    }

    public synchronized void start(
            Path worldDataDirectory,
            long worldSeed,
            long gameTicks,
            long overworldClockTicks) throws IOException {
        long now = System.currentTimeMillis();
        long newSeed = SECURE_RANDOM.nextLong()
                ^ Long.rotateLeft(worldSeed, 23)
                ^ Long.rotateLeft(now, 7);
        startInternal(
                worldDataDirectory,
                newSeed,
                now,
                gameTicks,
                overworldClockTicks,
                true);
    }

    synchronized void startWithSeed(
            Path worldDataDirectory,
            long economySeed,
            long now,
            long gameTicks) throws IOException {
        startInternal(
                worldDataDirectory,
                economySeed,
                now,
                gameTicks,
                gameTicks,
                false);
    }

    synchronized void startWithSeed(
            Path worldDataDirectory,
            long economySeed,
            long now,
            long gameTicks,
            long overworldClockTicks) throws IOException {
        startInternal(
                worldDataDirectory,
                economySeed,
                now,
                gameTicks,
                overworldClockTicks,
                true);
    }

    private void startInternal(
            Path worldDataDirectory,
            long fallbackSeed,
            long now,
            long gameTicks,
            long overworldClockTicks,
            boolean recoverPersistedOverworldClock) throws IOException {
        Objects.requireNonNull(worldDataDirectory, "worldDataDirectory");
        path = worldDataDirectory.resolve("the_emerald_standard.properties");
        unpersistedBankerDeathRegions.clear();
        try {
            state = EconomyState.load(
                    path,
                    fallbackSeed,
                    now,
                    gameTicks,
                    overworldClockTicks);
            if (!recoverPersistedOverworldClock) {
                // Compatibility overloads do not provide an independent world clock. Treat the
                // supplied game tick as a fresh baseline so it cannot be counted a second time.
                state.lastOverworldClockTicks = Math.max(0L, overworldClockTicks);
            }
            lastOverworldClockTicks = state.lastOverworldClockTicks;
            VillageExpansion.prepareDay(state);
            villageSpatialIndex.rebuild(state.villages);
            observeProgress(
                    now,
                    gameTicks,
                    overworldClockTicks,
                    STARTUP_CATCH_UP_BATCH_DAYS);
            saveState();
            dirty = false;
            resetSaveSchedule(now);
            lastError = "";
        } catch (IOException | RuntimeException exception) {
            String failure = message(exception);
            // This service instance can survive an integrated-server/world change. Never leave a
            // previously loaded world's state paired with the new world's path after startup
            // rejects corrupt or future-format data: the later server-stop save would otherwise
            // be able to overwrite the rejected file with unrelated state.
            state = null;
            path = null;
            dirty = false;
            nextAutomaticSaveMs = 0L;
            nextSaveRetryMs = 0L;
            saveRetryDelayMs = INITIAL_SAVE_RETRY_MS;
            lastOverworldClockTicks = 0L;
            unpersistedBankerDeathRegions.clear();
            villageSpatialIndex.rebuild(null);
            lastError = failure;
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not initialize the economy", exception);
        }
    }

    /**
     * Observes wall, server-game, and Overworld-clock time every server tick, preserves partial
     * days, advances bounded catch-up batches, and periodically persists progress. Failed automatic
     * saves use exponential backoff.
     */
    public synchronized boolean tick(long gameTicks) {
        return tickAt(gameTicks, lastOverworldClockTicks, System.currentTimeMillis());
    }

    public synchronized boolean tick(long gameTicks, long overworldClockTicks) {
        return tickAt(gameTicks, overworldClockTicks, System.currentTimeMillis());
    }

    synchronized boolean tickAt(long gameTicks, long now) {
        return tickAt(gameTicks, lastOverworldClockTicks, now);
    }

    synchronized boolean tickAt(long gameTicks, long overworldClockTicks, long now) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        try {
            dirty |= observeProgress(
                    now,
                    gameTicks,
                    overworldClockTicks,
                    TICK_CATCH_UP_BATCH_DAYS);
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
        return saveNowAt(
                state.lastGameTicks,
                lastOverworldClockTicks,
                System.currentTimeMillis());
    }

    /** Saves final partial progress during server shutdown. */
    public synchronized boolean saveNow(long gameTicks) {
        return saveNowAt(gameTicks, lastOverworldClockTicks, System.currentTimeMillis());
    }

    /** Saves final partial progress, including commandable Overworld-clock movement. */
    public synchronized boolean saveNow(long gameTicks, long overworldClockTicks) {
        return saveNowAt(gameTicks, overworldClockTicks, System.currentTimeMillis());
    }

    synchronized boolean saveNowAt(long gameTicks, long now) {
        return saveNowAt(gameTicks, lastOverworldClockTicks, now);
    }

    synchronized boolean saveNowAt(long gameTicks, long overworldClockTicks, long now) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        try {
            dirty |= observeProgress(
                    now,
                    gameTicks,
                    overworldClockTicks,
                    STARTUP_CATCH_UP_BATCH_DAYS);
            saveState();
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

    /** Quote history for an exchange item, derived without adding redundant save data. */
    public synchronized List<Double> resourceQuoteHistorySnapshot(String resourceId) {
        return state == null
                ? List.of()
                : EconomyEngine.resourceExchangeHistory(resourceId, state.commodityHistory);
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

    /** Returns zero for an unversioned legacy Bank or a region without a generated structure. */
    public synchronized int generatedBankStructureVersion(long regionKey) {
        return state == null ? 0 : state.bankStructureVersions.getOrDefault(regionKey, 0);
    }

    public synchronized UUID generatedBankerId(long regionKey) {
        return state == null ? null : state.bankRegionBankerIds.get(regionKey);
    }

    /** Returns the generated Bank currently owning a canonical Banker UUID, if any. */
    public synchronized Long generatedBankerRegion(UUID bankerId) {
        if (state == null || bankerId == null || state.bankRegionBankerIds.isEmpty()) {
            return null;
        }
        return state.bankRegionBankerIds.entrySet().stream()
                .filter(entry -> bankerId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .min(Long::compareUnsigned)
                .orElse(null);
    }

    /** Last Bank or fallback anchor durably assigned to the region's canonical Banker. */
    public synchronized Long generatedBankerAssignedAnchor(long regionKey) {
        return state == null ? null : state.bankRegionBankerAnchors.get(regionKey);
    }

    public synchronized GeneratedBankerConversion pendingGeneratedBankerConversion(
            long regionKey) {
        return state == null
                ? null
                : conversionSnapshot(regionKey, state.pendingBankerConversions.get(regionKey));
    }

    public synchronized GeneratedBankerConversion pendingGeneratedBankerConversionByTarget(
            UUID targetId) {
        if (state == null || targetId == null || state.pendingBankerConversions.isEmpty()) {
            return null;
        }
        return state.pendingBankerConversions.entrySet().stream()
                .filter(entry -> targetId.equals(entry.getValue().targetId))
                .map(entry -> conversionSnapshot(entry.getKey(), entry.getValue()))
                .min(java.util.Comparator.comparingLong(GeneratedBankerConversion::regionKey))
                .orElse(null);
    }

    public synchronized GeneratedBankerConversion pendingGeneratedBankerConversionBySource(
            UUID sourceId) {
        if (state == null || sourceId == null || state.pendingBankerConversions.isEmpty()) {
            return null;
        }
        return state.pendingBankerConversions.entrySet().stream()
                .filter(entry -> sourceId.equals(entry.getValue().immediateSourceId))
                .map(entry -> conversionSnapshot(entry.getKey(), entry.getValue()))
                .min(java.util.Comparator.comparingLong(GeneratedBankerConversion::regionKey))
                .orElse(null);
    }

    public synchronized Map<Long, GeneratedBankerConversion>
            pendingGeneratedBankerConversionsSnapshot() {
        if (state == null || state.pendingBankerConversions.isEmpty()) {
            return Map.of();
        }
        Map<Long, GeneratedBankerConversion> snapshot = new java.util.LinkedHashMap<>();
        state.pendingBankerConversions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Long::compareUnsigned))
                .forEach(entry -> snapshot.put(
                        entry.getKey(), conversionSnapshot(entry.getKey(), entry.getValue())));
        return Map.copyOf(snapshot);
    }

    public synchronized boolean hasPendingGeneratedBankerConversion(long regionKey) {
        return state != null && state.pendingBankerConversions.containsKey(regionKey);
    }

    /** True only for an explicit death observation known to have reached durable storage. */
    public synchronized boolean generatedBankerDeathPending(
            long regionKey, UUID expectedBankerId) {
        return state != null
                && expectedBankerId != null
                && expectedBankerId.equals(state.pendingBankerDeaths.get(regionKey))
                && !unpersistedBankerDeathRegions.contains(regionKey);
    }

    /**
     * Durably selects the one managed Banker for a generated region.
     *
     * <p>The UUID is never replaced merely because its entity is unloaded. This prevents Bank
     * relocation from spawning a second scoped villager and inflating the village census.</p>
     */
    public synchronized boolean rememberGeneratedBanker(long regionKey, UUID bankerId) {
        if (state == null || path == null || bankerId == null) {
            lastError = "Economy service or Banker identity is not available";
            return false;
        }
        if (!state.generatedBankRegions.contains(regionKey)
                || !state.generatedBankAnchors.containsKey(regionKey)) {
            lastError = "Generated Bank region is not available";
            return false;
        }
        if (state.pendingBankerConversions.containsKey(regionKey)) {
            lastError = "Generated Banker conversion is still pending";
            return false;
        }
        long assignedAnchor = state.generatedBankAnchors.get(regionKey);
        UUID existing = state.bankRegionBankerIds.get(regionKey);
        UUID pendingDeath = state.pendingBankerDeaths.get(regionKey);
        if (existing != null) {
            if (existing.equals(bankerId)) {
                if (existing.equals(pendingDeath)) {
                    lastError = "The canonical Banker is awaiting confirmed-death replacement";
                    return false;
                }
                if (state.bankRegionBankerAnchors.containsKey(regionKey)) {
                    return true;
                }
                // Repair an early format-13 in-memory state without silently changing a known
                // stale assignment that is still needed to finish a relocation.
                EconomyState before = state.copy();
                boolean dirtyBefore = dirty;
                try {
                    state.bankRegionBankerAnchors.put(regionKey, assignedAnchor);
                    saveState();
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
            if (!existing.equals(pendingDeath)) {
                lastError = "Generated Bank already has a canonical Banker";
                return false;
            }
            if (unpersistedBankerDeathRegions.contains(regionKey)) {
                lastError = "The canonical Banker death has not reached durable storage";
                return false;
            }
        }
        if (state.bankRegionBankerIds.containsValue(bankerId)) {
            lastError = "Banker is already canonical for another generated Bank";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            state.bankRegionBankerIds.put(regionKey, bankerId);
            state.bankRegionBankerAnchors.put(regionKey, assignedAnchor);
            state.pendingBankerDeaths.remove(regionKey);
            unpersistedBankerDeathRegions.remove(regionKey);
            saveState();
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

    /**
     * Durably hands canonical ownership to the entity created by a vanilla mob conversion.
     *
     * <p>Minecraft 26.2 assigns a new UUID when a Villager becomes a Zombie Villager and again
     * when it is cured. The integration layer first persists this PREPARED tuple before vanilla
     * can insert/save the outcome, then writes an exact typed durable phase only after an
     * entity-inclusive world barrier. The final canonical swap and removal of any matching death
     * tombstone are one economy save, so every failure retains fail-closed ownership.</p>
     */
    public synchronized BankerConversionResult prepareGeneratedBankerConversion(
            long regionKey,
            UUID expectedRootId,
            UUID expectedSourceId,
            UUID targetId,
            String targetDimension,
            long targetPos,
            boolean continuingBanker) {
        if (state == null
                || path == null
                || expectedRootId == null
                || expectedSourceId == null
                || targetId == null
                || targetDimension == null
                || targetDimension.isBlank()
                || targetDimension.indexOf('|') >= 0
                || expectedSourceId.equals(targetId)) {
            lastError = "Economy service or Banker conversion identity is not available";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, null);
        }
        UUID canonical = state.bankRegionBankerIds.get(regionKey);
        EconomyState.PendingBankerConversion existing =
                state.pendingBankerConversions.get(regionKey);
        UUID root;
        if (existing == null) {
            if (!expectedRootId.equals(canonical)) {
                lastError = "Canonical Banker identity changed before conversion preparation";
                return new BankerConversionResult(BankerConversionStatus.STALE, null);
            }
            root = canonical;
        } else {
            GeneratedBankerConversion snapshot = conversionSnapshot(regionKey, existing);
            if (!existing.rootCanonicalId.equals(canonical)
                    || !existing.rootCanonicalId.equals(expectedRootId)) {
                lastError = "Prepared Banker conversion no longer matches canonical ownership";
                return new BankerConversionResult(BankerConversionStatus.STALE, snapshot);
            }
            if (existing.immediateSourceId.equals(expectedSourceId)
                    && existing.targetId.equals(targetId)
                    && existing.targetDimension.equals(targetDimension)
                    && existing.targetPos == targetPos
                    && existing.disposition == (continuingBanker
                            ? EconomyState.BankerConversionDisposition.CONTINUE_BANKER
                            : EconomyState.BankerConversionDisposition.TERMINAL)) {
                lastError = "";
                return new BankerConversionResult(
                        BankerConversionStatus.ALREADY_APPLIED, snapshot);
            }
            UUID allowedSource = switch (existing.phase) {
                case PREPARED, TARGET_DURABLE -> existing.targetId;
                case SOURCE_DURABLE -> existing.immediateSourceId;
                case ROOT_DURABLE -> existing.rootCanonicalId;
                case RETIRE_DURABLE -> null;
            };
            if (!expectedSourceId.equals(allowedSource)) {
                lastError = "A different Banker conversion is already pending";
                return new BankerConversionResult(BankerConversionStatus.CONFLICT, snapshot);
            }
            root = existing.rootCanonicalId;
        }
        UUID pendingDeath = state.pendingBankerDeaths.get(regionKey);
        if (pendingDeath != null && !root.equals(pendingDeath)) {
            lastError = "Pending Banker death identity changed during conversion preparation";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, null);
        }
        if (bankerIdentityOwnedOutside(regionKey, targetId)) {
            lastError = "Converted Banker identity is already owned by another handoff";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, null);
        }
        if (!expectedSourceId.equals(root)
                && bankerIdentityOwnedOutside(regionKey, expectedSourceId)) {
            lastError = "Immediate conversion source is owned by another Bank lineage";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, null);
        }

        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        EconomyState.PendingBankerConversion prepared =
                new EconomyState.PendingBankerConversion();
        prepared.rootCanonicalId = root;
        prepared.immediateSourceId = expectedSourceId;
        prepared.targetId = targetId;
        prepared.targetDimension = targetDimension;
        prepared.targetPos = targetPos;
        prepared.phase = EconomyState.BankerConversionPhase.PREPARED;
        prepared.disposition = continuingBanker
                ? EconomyState.BankerConversionDisposition.CONTINUE_BANKER
                : EconomyState.BankerConversionDisposition.TERMINAL;
        try {
            state.pendingBankerConversions.put(regionKey, prepared);
            saveState();
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return new BankerConversionResult(
                    BankerConversionStatus.APPLIED,
                    conversionSnapshot(regionKey, prepared));
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return new BankerConversionResult(
                    BankerConversionStatus.RETRY_IO,
                    conversionSnapshot(regionKey, existing));
        }
    }

    public synchronized BankerConversionResult markGeneratedBankerConversionEntityDurable(
            GeneratedBankerConversion expected) {
        return markGeneratedBankerConversionResolution(
                expected, EconomyState.BankerConversionPhase.TARGET_DURABLE);
    }

    public synchronized BankerConversionResult markGeneratedBankerConversionSourceDurable(
            GeneratedBankerConversion expected) {
        if (expected == null
                || expected.immediateSourceId().equals(expected.rootCanonicalId())) {
            lastError = "Prepared conversion has no distinct immediate source";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        return markGeneratedBankerConversionResolution(
                expected, EconomyState.BankerConversionPhase.SOURCE_DURABLE);
    }

    public synchronized BankerConversionResult markGeneratedBankerConversionRetirementDurable(
            GeneratedBankerConversion expected) {
        return markGeneratedBankerConversionResolution(
                expected, EconomyState.BankerConversionPhase.RETIRE_DURABLE);
    }

    public synchronized BankerConversionResult markGeneratedBankerConversionRootDurable(
            GeneratedBankerConversion expected) {
        return markGeneratedBankerConversionResolution(
                expected, EconomyState.BankerConversionPhase.ROOT_DURABLE);
    }

    private BankerConversionResult markGeneratedBankerConversionResolution(
            GeneratedBankerConversion expected,
            EconomyState.BankerConversionPhase durablePhase) {
        EconomyState.PendingBankerConversion current = state == null || expected == null
                ? null : state.pendingBankerConversions.get(expected.regionKey());
        if (current != null
                && sameBankerConversionIdentity(expected, current)
                && current.phase == durablePhase) {
            return new BankerConversionResult(
                    BankerConversionStatus.ALREADY_APPLIED,
                    conversionSnapshot(expected.regionKey(), current));
        }
        if (current == null
                || !sameBankerConversionIdentity(expected, current)
                || !((current.phase == EconomyState.BankerConversionPhase.PREPARED)
                        || (durablePhase
                                        == EconomyState.BankerConversionPhase.RETIRE_DURABLE
                                && (current.phase
                                                == EconomyState.BankerConversionPhase.TARGET_DURABLE
                                        || current.phase
                                                == EconomyState.BankerConversionPhase.SOURCE_DURABLE
                                        || current.phase
                                                == EconomyState.BankerConversionPhase.ROOT_DURABLE)))) {
            lastError = "Prepared Banker conversion phase cannot make that transition";
            return new BankerConversionResult(
                    BankerConversionStatus.CONFLICT,
                    conversionSnapshot(expected == null ? 0L : expected.regionKey(), current));
        }
        return mutatePreparedBankerConversion(
                expected,
                false,
                conversion -> conversion.phase = durablePhase);
    }

    public synchronized BankerConversionResult commitGeneratedBankerConversion(
            GeneratedBankerConversion expected) {
        if (expected == null
                || expected.phase() != EconomyState.BankerConversionPhase.TARGET_DURABLE
                || expected.disposition()
                        != EconomyState.BankerConversionDisposition.CONTINUE_BANKER) {
            lastError = "Converted Banker entity is not durably saved";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        if (state != null
                && state.pendingBankerConversions.get(expected.regionKey()) == null
                && expected.targetId().equals(state.bankRegionBankerIds.get(expected.regionKey()))
                && !state.pendingBankerDeaths.containsKey(expected.regionKey())) {
            return new BankerConversionResult(
                    BankerConversionStatus.ALREADY_APPLIED, expected);
        }
        EconomyState.PendingBankerConversion current = state == null || expected == null
                ? null : state.pendingBankerConversions.get(expected.regionKey());
        if (current == null
                || current.phase != EconomyState.BankerConversionPhase.TARGET_DURABLE
                || current.disposition
                        != EconomyState.BankerConversionDisposition.CONTINUE_BANKER) {
            lastError = "Converted Banker outcome has not reached durable entity storage";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        return mutatePreparedBankerConversion(expected, false, conversion -> {
            state.bankRegionBankerIds.put(expected.regionKey(), expected.targetId());
            state.pendingBankerDeaths.remove(expected.regionKey());
            state.pendingBankerConversions.remove(expected.regionKey());
        });
    }

    /** Commits the exact immediate predecessor after a chained target failed to materialize. */
    public synchronized BankerConversionResult commitGeneratedBankerConversionSource(
            GeneratedBankerConversion expected) {
        if (expected == null
                || expected.immediateSourceId().equals(expected.rootCanonicalId())
                || expected.phase() != EconomyState.BankerConversionPhase.SOURCE_DURABLE) {
            lastError = "Prepared conversion has no distinct immediate source";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        if (state != null
                && state.pendingBankerConversions.get(expected.regionKey()) == null
                && expected.immediateSourceId().equals(
                        state.bankRegionBankerIds.get(expected.regionKey()))
                && !state.pendingBankerDeaths.containsKey(expected.regionKey())) {
            return new BankerConversionResult(
                    BankerConversionStatus.ALREADY_APPLIED, expected);
        }
        if (bankerIdentityOwnedOutside(expected.regionKey(), expected.immediateSourceId())) {
            lastError = "Immediate conversion source is owned by another Bank lineage";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        return mutatePreparedBankerConversion(expected, false, conversion -> {
            state.bankRegionBankerIds.put(
                    expected.regionKey(), expected.immediateSourceId());
            state.pendingBankerDeaths.remove(expected.regionKey());
            state.pendingBankerConversions.remove(expected.regionKey());
        });
    }

    /** Exact-root-live reconciliation; clears both conversion intent and a matching tombstone. */
    public synchronized BankerConversionResult abortGeneratedBankerConversion(
            GeneratedBankerConversion expected) {
        if (expected == null
                || expected.phase() != EconomyState.BankerConversionPhase.ROOT_DURABLE) {
            lastError = "Only a prepared conversion can be aborted to its live root";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        if (state != null
                && expected != null
                && state.pendingBankerConversions.get(expected.regionKey()) == null
                && expected.rootCanonicalId().equals(
                        state.bankRegionBankerIds.get(expected.regionKey()))
                && !state.pendingBankerDeaths.containsKey(expected.regionKey())) {
            return new BankerConversionResult(
                    BankerConversionStatus.ALREADY_APPLIED, expected);
        }
        return mutatePreparedBankerConversion(expected, false, conversion -> {
            state.pendingBankerConversions.remove(expected.regionKey());
            state.pendingBankerDeaths.remove(expected.regionKey());
        });
    }

    /** Exact terminal/death resolution after Minecraft durably saved the entity outcome. */
    public synchronized BankerConversionResult retireGeneratedBankerConversion(
            GeneratedBankerConversion expected) {
        if (expected == null
                || (expected.phase() != EconomyState.BankerConversionPhase.RETIRE_DURABLE
                        && !(expected.phase()
                                        == EconomyState.BankerConversionPhase.TARGET_DURABLE
                                && expected.disposition()
                                        == EconomyState.BankerConversionDisposition.TERMINAL))) {
            lastError = "Banker retirement outcome is not durably resolved";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        if (state != null
                && expected != null
                && state.pendingBankerConversions.get(expected.regionKey()) == null
                && expected.rootCanonicalId().equals(
                        state.pendingBankerDeaths.get(expected.regionKey()))) {
            return new BankerConversionResult(
                    BankerConversionStatus.ALREADY_APPLIED, expected);
        }
        return mutatePreparedBankerConversion(expected, false, conversion -> {
            state.pendingBankerDeaths.put(
                    expected.regionKey(), expected.rootCanonicalId());
            state.pendingBankerConversions.remove(expected.regionKey());
        });
    }

    /** Compatibility entry point; requires a matching durable format-17 handoff. */
    public synchronized boolean transferGeneratedBankerConversion(
            long regionKey, UUID expectedPreviousId, UUID convertedId) {
        GeneratedBankerConversion pending = pendingGeneratedBankerConversion(regionKey);
        return pending != null
                && pending.rootCanonicalId().equals(expectedPreviousId)
                && pending.targetId().equals(convertedId)
                && commitGeneratedBankerConversion(pending).accepted();
    }

    private BankerConversionResult mutatePreparedBankerConversion(
            GeneratedBankerConversion expected,
            boolean acceptAnyPhase,
            java.util.function.Consumer<EconomyState.PendingBankerConversion> mutation) {
        if (state == null || path == null || expected == null) {
            lastError = "Economy service or prepared Banker conversion is not available";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }
        EconomyState.PendingBankerConversion current =
                state.pendingBankerConversions.get(expected.regionKey());
        if (current == null
                || !sameBankerConversionIdentity(expected, current)
                || (!acceptAnyPhase && expected.phase() != current.phase)
                || !expected.rootCanonicalId().equals(
                        state.bankRegionBankerIds.get(expected.regionKey()))) {
            lastError = "Prepared Banker conversion identity changed";
            return new BankerConversionResult(
                    BankerConversionStatus.STALE,
                    conversionSnapshot(expected.regionKey(), current));
        }
        UUID pendingDeath = state.pendingBankerDeaths.get(expected.regionKey());
        if (pendingDeath != null && !expected.rootCanonicalId().equals(pendingDeath)) {
            lastError = "Pending Banker death identity changed during conversion";
            return new BankerConversionResult(BankerConversionStatus.CONFLICT, expected);
        }

        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        Set<Long> unpersistedDeathsBefore = new HashSet<>(unpersistedBankerDeathRegions);
        try {
            mutation.accept(current);
            saveState();
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            EconomyState.PendingBankerConversion after =
                    state.pendingBankerConversions.get(expected.regionKey());
            return new BankerConversionResult(
                    BankerConversionStatus.APPLIED,
                    after == null
                            ? expected
                            : conversionSnapshot(expected.regionKey(), after));
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            unpersistedBankerDeathRegions.clear();
            unpersistedBankerDeathRegions.addAll(unpersistedDeathsBefore);
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return new BankerConversionResult(
                    BankerConversionStatus.RETRY_IO,
                    conversionSnapshot(expected.regionKey(),
                            state.pendingBankerConversions.get(expected.regionKey())));
        }
    }

    private boolean bankerIdentityOwnedOutside(long regionKey, UUID identity) {
        if (identity == null || state == null) {
            return true;
        }
        for (Map.Entry<Long, UUID> entry : state.bankRegionBankerIds.entrySet()) {
            if (identity.equals(entry.getValue())) {
                return true;
            }
        }
        for (Map.Entry<Long, EconomyState.PendingBankerConversion> entry
                : state.pendingBankerConversions.entrySet()) {
            if (entry.getKey() == regionKey) {
                continue;
            }
            EconomyState.PendingBankerConversion conversion = entry.getValue();
            if (identity.equals(conversion.rootCanonicalId)
                    || identity.equals(conversion.immediateSourceId)
                    || identity.equals(conversion.targetId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameBankerConversionIdentity(
            GeneratedBankerConversion expected,
            EconomyState.PendingBankerConversion current) {
        return expected.rootCanonicalId().equals(current.rootCanonicalId)
                && expected.immediateSourceId().equals(current.immediateSourceId)
                && expected.targetId().equals(current.targetId)
                && expected.targetDimension().equals(current.targetDimension)
                && expected.targetPos() == current.targetPos
                && expected.disposition() == current.disposition;
    }

    private static GeneratedBankerConversion conversionSnapshot(
            long regionKey, EconomyState.PendingBankerConversion conversion) {
        return conversion == null
                ? null
                : new GeneratedBankerConversion(
                        regionKey,
                        conversion.rootCanonicalId,
                        conversion.immediateSourceId,
                        conversion.targetId,
                        conversion.targetDimension,
                        conversion.targetPos,
                        conversion.phase,
                        conversion.disposition);
    }

    /**
     * Records an entity death without first discarding canonical ownership.
     *
     * <p>The tombstone is the only authority that permits a null entity lookup to become a
     * replacement after restart. On an immediate save failure it deliberately remains in the
     * dirty in-memory state so the ordinary retry/shutdown save path can still make the observed
     * death durable; the old canonical UUID continues to block duplicates until then.</p>
     */
    public synchronized boolean recordGeneratedBankerDeath(
            long regionKey, UUID expectedBankerId) {
        if (state == null || path == null || expectedBankerId == null) {
            lastError = "Economy service or Banker identity is not available";
            return false;
        }
        UUID existing = state.bankRegionBankerIds.get(regionKey);
        if (existing == null) {
            return true;
        }
        if (!existing.equals(expectedBankerId)) {
            lastError = "Canonical Banker identity changed";
            return false;
        }
        UUID pendingDeath = state.pendingBankerDeaths.get(regionKey);
        if (pendingDeath != null && !pendingDeath.equals(expectedBankerId)) {
            lastError = "A different canonical Banker death is already pending";
            return false;
        }
        if (pendingDeath == null) {
            state.pendingBankerDeaths.put(regionKey, expectedBankerId);
            unpersistedBankerDeathRegions.add(regionKey);
            dirty = true;
        } else if (!unpersistedBankerDeathRegions.contains(regionKey)) {
            return true;
        }
        return persistDirtyState(state.lastWallClockMs);
    }

    /**
     * Cancels a stale death tombstone after the exact canonical entity is observed alive again.
     * This reconciles a crash in which the economy save reached disk before Minecraft saved the
     * entity removal.
     */
    public synchronized boolean confirmGeneratedBankerAlive(
            long regionKey, UUID expectedBankerId) {
        if (state == null || path == null || expectedBankerId == null) {
            lastError = "Economy service or Banker identity is not available";
            return false;
        }
        if (state.pendingBankerConversions.containsKey(regionKey)) {
            lastError = "Prepared Banker conversion requires an exact atomic abort";
            return false;
        }
        if (!expectedBankerId.equals(state.bankRegionBankerIds.get(regionKey))) {
            lastError = "Canonical Banker identity changed";
            return false;
        }
        UUID pendingDeath = state.pendingBankerDeaths.get(regionKey);
        if (pendingDeath == null) {
            return true;
        }
        if (!pendingDeath.equals(expectedBankerId)) {
            lastError = "Pending Banker death identity changed";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        boolean deathWasUnpersisted = unpersistedBankerDeathRegions.remove(regionKey);
        try {
            state.pendingBankerDeaths.remove(regionKey);
            saveState();
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            if (deathWasUnpersisted) {
                unpersistedBankerDeathRegions.add(regionKey);
            }
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    /**
     * Records that the expected canonical Banker has reached the region's current managed anchor.
     * The integration layer calls this only after Minecraft has synchronously saved the entity.
     */
    public synchronized boolean confirmGeneratedBankerAssignment(
            long regionKey, UUID expectedBankerId, long expectedPackedAnchor) {
        if (state == null || path == null || expectedBankerId == null) {
            lastError = "Economy service or Banker identity is not available";
            return false;
        }
        if (state.pendingBankerConversions.containsKey(regionKey)) {
            lastError = "Generated Banker conversion is still pending";
            return false;
        }
        if (!expectedBankerId.equals(state.bankRegionBankerIds.get(regionKey))) {
            lastError = "Canonical Banker identity changed";
            return false;
        }
        if (!Objects.equals(state.generatedBankAnchors.get(regionKey), expectedPackedAnchor)) {
            lastError = "Generated Bank anchor changed";
            return false;
        }
        if (Objects.equals(
                state.bankRegionBankerAnchors.get(regionKey), expectedPackedAnchor)) {
            return true;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        boolean deathWasUnpersisted = unpersistedBankerDeathRegions.remove(regionKey);
        try {
            state.bankRegionBankerAnchors.put(regionKey, expectedPackedAnchor);
            saveState();
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            if (deathWasUnpersisted) {
                unpersistedBankerDeathRegions.add(regionKey);
            }
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    /** Clears only an explicitly observed canonical entity; an unloaded lookup never calls this. */
    public synchronized boolean forgetGeneratedBanker(long regionKey, UUID expectedBankerId) {
        if (state == null || path == null || expectedBankerId == null) {
            lastError = "Economy service or Banker identity is not available";
            return false;
        }
        if (state.pendingBankerConversions.containsKey(regionKey)) {
            lastError = "Generated Banker conversion is still pending";
            return false;
        }
        UUID existing = state.bankRegionBankerIds.get(regionKey);
        if (existing == null) {
            return true;
        }
        if (!existing.equals(expectedBankerId)) {
            lastError = "Canonical Banker identity changed";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        boolean deathWasUnpersisted = unpersistedBankerDeathRegions.remove(regionKey);
        try {
            state.bankRegionBankerIds.remove(regionKey);
            state.bankRegionBankerAnchors.remove(regionKey);
            state.pendingBankerDeaths.remove(regionKey);
            saveState();
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state = before;
            dirty = dirtyBefore;
            if (deathWasUnpersisted) {
                unpersistedBankerDeathRegions.add(regionKey);
            }
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
        }
    }

    public synchronized List<Long> retiredBankAnchors(long regionKey) {
        if (state == null) {
            return List.of();
        }
        List<Long> anchors = state.retiredBankAnchors.get(regionKey);
        return anchors == null ? List.of() : List.copyOf(anchors);
    }

    public synchronized boolean isFallbackBankRegion(long regionKey) {
        return state != null && state.fallbackBankRegions.contains(regionKey);
    }

    /**
     * Persists the old anchor before replacement search starts. The current Bank marker remains
     * valid until a fully built replacement is durably committed, so a failed search loses no data.
     */
    public synchronized boolean retireGeneratedBankAnchor(
            long regionKey, long expectedPackedAnchor) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        if (!state.generatedBankRegions.contains(regionKey)
                || !Objects.equals(state.generatedBankAnchors.get(regionKey), expectedPackedAnchor)
                || state.bankStructureVersions.getOrDefault(regionKey, 0) <= 0
                || state.fallbackBankRegions.contains(regionKey)) {
            lastError = "Authored Bank or expected anchor is not available";
            return false;
        }
        List<Long> retired = state.retiredBankAnchors.get(regionKey);
        if (retired != null && retired.contains(expectedPackedAnchor)) {
            return true;
        }
        if (retired != null
                && retired.size() >= EconomyState.MAX_RETIRED_BANK_ANCHORS_PER_REGION) {
            lastError = "Retired Bank anchor history is full; the current Bank remains unsafe";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            state.retiredBankAnchors
                    .computeIfAbsent(regionKey, ignored -> new ArrayList<>())
                    .add(expectedPackedAnchor);
            saveState();
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

    /** Read-only anchors used to recognize generated bank counters without exposing mutable state. */
    public synchronized Map<Long, Long> generatedBankAnchorsSnapshot() {
        return state == null ? Map.of() : Map.copyOf(state.generatedBankAnchors);
    }

    /** Deep immutable snapshot used to reject every retired generated Bank workstation. */
    public synchronized Map<Long, List<Long>> retiredBankAnchorsSnapshot() {
        if (state == null) {
            return Map.of();
        }
        Map<Long, List<Long>> copy = new HashMap<>();
        state.retiredBankAnchors.forEach((region, anchors) ->
                copy.put(region, List.copyOf(anchors)));
        return Map.copyOf(copy);
    }

    public synchronized boolean markGeneratedBankRegion(long regionKey) {
        return markGeneratedBankRegion(regionKey, null);
    }

    public synchronized boolean markGeneratedBankRegion(long regionKey, Long packedAnchor) {
        return persistBankRegionMarker(regionKey, packedAnchor, false, null, null);
    }

    /** Atomically records a generated Bank and its stable village ownership when known. */
    public synchronized boolean markGeneratedBankRegion(
            long regionKey, long packedAnchor, UUID villageId) {
        return persistBankRegionMarker(regionKey, packedAnchor, false, villageId, null);
    }

    /** Atomically records a newly built Bank together with its authored-structure version. */
    public synchronized boolean markGeneratedBankRegion(
            long regionKey,
            long packedAnchor,
            UUID villageId,
            int structureVersion) {
        return persistBankRegionMarker(
                regionKey, packedAnchor, false, villageId, structureVersion);
    }

    /** Records a deliberate Banker-only fallback that may safely retry future lot searches. */
    public synchronized boolean markFallbackBankRegion(long regionKey, long packedAnchor) {
        return persistBankRegionMarker(regionKey, packedAnchor, true, null, null);
    }

    /** Atomically records a Banker-only fallback and its stable village ownership when known. */
    public synchronized boolean markFallbackBankRegion(
            long regionKey, long packedAnchor, UUID villageId) {
        return persistBankRegionMarker(regionKey, packedAnchor, true, villageId, null);
    }

    public synchronized Map<Long, BankConstruction> pendingBankConstructionsSnapshot() {
        return state == null ? Map.of() : Map.copyOf(state.pendingBankConstructions);
    }

    public synchronized boolean reserveBankConstruction(long key, BankConstruction plan) {
        if (state == null || path == null || isCatchingUp() || plan == null) return false;
        if (state.pendingBankConstructions.containsKey(key)) return false;
        if (plan.villageId() != null && (state.existingVillage(plan.villageId()) == null
                || state.pendingBankConstructions.values().stream()
                        .anyMatch(p -> plan.villageId().equals(p.villageId())))) return false;
        EconomyState before = state.copy(); boolean dirtyBefore = dirty;
        try {
            state.pendingBankConstructions.put(key, plan);
            saveState(); dirty = false; resetSaveSchedule(state.lastWallClockMs); lastError = "";
            return true;
        } catch (IOException | RuntimeException ex) {
            state = before; dirty = dirtyBefore; lastError = message(ex);
            scheduleSaveRetry(state.lastWallClockMs); return false;
        }
    }

    private boolean persistBankRegionMarker(
            long regionKey,
            Long packedAnchor,
            boolean fallback,
            UUID villageId,
            Integer structureVersion) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        if (structureVersion != null && structureVersion <= 0) {
            lastError = "Bank structure version must be positive";
            return false;
        }
        EconomyState.VillageRecord village = villageId == null
                ? null
                : state.existingVillage(villageId);
        if (villageId != null && (packedAnchor == null || village == null)) {
            lastError = "Bank village or anchor is not available";
            return false;
        }
        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            boolean changed = state.generatedBankRegions.add(regionKey);
            Long previousAnchor = state.generatedBankAnchors.get(regionKey);
            boolean anchorChanged = packedAnchor != null
                    && !Objects.equals(previousAnchor, packedAnchor);
            if (anchorChanged) {
                state.generatedBankAnchors.put(regionKey, packedAnchor);
                changed = true;
            }
            if (fallback) {
                changed |= state.fallbackBankRegions.add(regionKey);
                changed |= state.bankStructureVersions.remove(regionKey) != null;
                changed |= state.retiredBankAnchors.remove(regionKey) != null;
            } else {
                changed |= state.fallbackBankRegions.remove(regionKey);
                if (structureVersion != null) {
                    int nextVersion = anchorChanged
                            ? structureVersion
                            : Math.max(
                                    structureVersion,
                                    state.bankStructureVersions.getOrDefault(regionKey, 0));
                    if (!Objects.equals(
                            state.bankStructureVersions.put(regionKey, nextVersion),
                            nextVersion)) {
                        changed = true;
                    }
                } else if (anchorChanged) {
                    changed |= state.bankStructureVersions.remove(regionKey) != null;
                    changed |= state.retiredBankAnchors.remove(regionKey) != null;
                }
            }
            if (village != null) {
                if (!Objects.equals(state.bankRegionVillageIds.put(regionKey, villageId),
                        villageId)) {
                    changed = true;
                }
                if (village.bankRegionKey != regionKey
                        || village.bankAnchorPos != packedAnchor) {
                    village.bankRegionKey = regionKey;
                    village.bankAnchorPos = packedAnchor;
                    changed = true;
                }
            }
            if (!fallback && structureVersion != null) {
                BankConstruction pending = state.pendingBankConstructions.get(regionKey);
                if (pending != null && Objects.equals(pending.villageId(), villageId)
                        && Objects.equals(packedAnchor, pending.bankerAnchor()) && structureVersion == pending.version())
                    changed |= state.pendingBankConstructions.remove(regionKey) != null;
            }
            if (!changed) {
                return true;
            }
            saveState();
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

    /**
     * Durably marks a guarded in-world Bank upgrade complete without creating or moving a Bank.
     * Repeating an already-completed or newer version is a no-op.
     */
    public synchronized boolean completeBankStructureUpgrade(
            long regionKey, long expectedPackedAnchor, int structureVersion) {
        if (state == null || path == null) {
            lastError = "Economy service has not started";
            return false;
        }
        if (structureVersion <= 0) {
            lastError = "Bank structure version must be positive";
            return false;
        }
        if (!state.generatedBankRegions.contains(regionKey)
                || !Objects.equals(
                        state.generatedBankAnchors.get(regionKey), expectedPackedAnchor)
                || state.fallbackBankRegions.contains(regionKey)) {
            lastError = "Bank structure or expected anchor is not available";
            return false;
        }
        int currentVersion = state.bankStructureVersions.getOrDefault(regionKey, 0);
        if (currentVersion >= structureVersion) {
            return true;
        }

        EconomyState before = state.copy();
        boolean dirtyBefore = dirty;
        try {
            state.bankStructureVersions.put(regionKey, structureVersion);
            saveState();
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
                saveState();
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

    public record ExpansionStatus(UUID rootId, VillageExpansion.Mode mode, VillageExpansion.Reason reason,
            int districts, double dailyCityOverhead, long siteCursor, long serial) { }

    public synchronized void observeVillageLighting(UUID villageId, int covered, int total) {
        if (total < 8 || covered < 0 || covered > total || isCatchingUp()) return;
        mutateVillage(villageId, false, v -> {
            v.lightingCoveragePercent = (int) (100L * covered / total);
            v.lastLightingDay = state.economicDay;
            return true;
        });
    }

    private EconomyState.VillageRecord expansionRoot(UUID villageId) {
        if (state == null || villageId == null) return null;
        var village = state.villages.get(villageId);
        return village == null ? null : state.villages.get(VillageExpansion.rootId(village));
    }

    /** Loaded-world observations replace the old count, including a genuinely emptied field/pen. */
    public synchronized boolean observeVillageFoodSources(UUID villageId, double crops, double livestock) {
        if (!Double.isFinite(crops) || !Double.isFinite(livestock) || crops < 0 || livestock < 0
                || crops > 1_000_000 || livestock > 1_000_000 || isCatchingUp()) return false;
        boolean observed = mutateVillage(villageId, false,
                village -> { VillageFoodSupply.observe(village, crops, livestock, state.economicDay); return true; });
        if (observed) {
            // The casualty-isolation counterfactual experiences the same physical environment.
            var shadow = state.villageMarketShadows.get(villageId);
            if (shadow != null && shadow.counterfactualVillage != null)
                VillageFoodSupply.observe(shadow.counterfactualVillage, crops, livestock, state.economicDay);
        }
        return observed;
    }

    public synchronized ExpansionStatus expansionStatus(UUID villageId) {
        var root = expansionRoot(villageId);
        if (root == null) return null;
        return new ExpansionStatus(root.villageId, root.expansionMode,
                !villageProsperitySimulationEnabled || !villageVisualProgressionEnabled ? VillageExpansion.Reason.DISABLED
                        : isCatchingUp() ? VillageExpansion.Reason.CATCHING_UP
                        : VillageExpansion.reason(root, state.economicDay, peacefulVillageGrowth), root.cityDistrictCount,
                VillageExpansion.overhead(root, peacefulVillageGrowth) * root.cityDistrictCount,
                root.expansionSiteCursor, root.expansionSerial);
    }

    /** Authorization is enforced at the server-side player/command boundary. */
    public synchronized boolean setVillageExpansionMode(UUID villageId, VillageExpansion.Mode mode) {
        var root = expansionRoot(villageId);
        if (root == null || mode == null || isCatchingUp()) return false;
        boolean changed = mutateVillage(root.villageId, true, v -> {
            v.expansionMode = mode;
            v.expansionApproved = false;
            return true;
        });
        VillageExpansion.prepareDay(state);
        return changed;
    }

    public synchronized boolean approveVillageExpansion(UUID villageId) {
        var root = expansionRoot(villageId);
        if (root == null || root.expansionMode != VillageExpansion.Mode.APPROVAL || isCatchingUp()) return false;
        return mutateVillage(root.villageId, true, v -> { v.expansionApproved = true; return true; });
    }

    public synchronized EconomyState.VillageRecord draftVillageDistrict(UUID villageId, long center) {
        var status = expansionStatus(villageId);
        if (status == null || status.reason() != VillageExpansion.Reason.READY || isCatchingUp()
                || !villageProsperitySimulationEnabled || !villageVisualProgressionEnabled) return null;
        return VillageExpansion.draft(expansionRoot(villageId), center, state.seed, state.economicDay, true);
    }

    public synchronized void advanceDistrictSiteSearch(UUID villageId) {
        var root = expansionRoot(villageId);
        if (root != null) mutateVillage(root.villageId, false, v -> {
            v.expansionSiteCursor = v.expansionSiteCursor == Long.MAX_VALUE ? 0 : v.expansionSiteCursor + 1;
            return true;
        });
    }

    public synchronized boolean recordSitePreparation(UUID villageId, long projectId, int cursor, boolean complete) {
        return mutateVillage(villageId, complete, v -> {
            var p = findProject(v, projectId);
            if (p == null || p.sitePreparationComplete || cursor < p.sitePreparationCursor || cursor > 1_000_000) return false;
            p.sitePreparationCursor = cursor;
            p.sitePreparationComplete = complete;
            return true;
        });
    }

    /** Commits the municipal debit AND fully preflighted starter lot in a single durable save. */
    public synchronized boolean commitVillageDistrict(UUID villageId, long expectedSerial,
            EconomyState.VillageRecord planned) {
        var status = expansionStatus(villageId);
        var root = expansionRoot(villageId);
        if (status == null || status.reason() != VillageExpansion.Reason.READY || isCatchingUp()
                || !villageProsperitySimulationEnabled || !villageVisualProgressionEnabled
                || planned == null || root.expansionSerial != expectedSerial
                || !root.villageId.equals(planned.cityId) || state.villages.containsKey(planned.villageId)
                || !root.dimensionKey.equals(planned.dimensionKey) || planned.projects.size() != 1
                || planned.projects.getFirst().originPos == 0L || planned.projects.getFirst().designPlanHash.isEmpty())
            return false;
        var expected = VillageExpansion.draft(root, planned.centerPos, state.seed, state.economicDay, true);
        if (!expected.villageId.equals(planned.villageId) || planned.population != 0
                || planned.pendingSettlers != 4 || !planned.districtFounding
                || planned.foodSupply != expected.foodSupply || planned.materialSupply != expected.materialSupply
                || planned.treasury != expected.treasury || planned.projects.getFirst().type != expected.projects.getFirst().type)
            return false;
        var before = root.copy();
        boolean dirtyBefore = dirty;
        try {
            root.foodSupply -= VillageExpansion.CHARTER_FOOD;
            root.materialSupply -= VillageExpansion.CHARTER_MATERIALS;
            root.treasury -= VillageExpansion.CHARTER_TREASURY;
            root.lastExpansionDay = state.economicDay;
            root.expansionSerial++;
            root.expansionApproved = false;
            state.villages.put(planned.villageId, planned.copy());
            VillageExpansion.prepareDay(state);
            dirty = true;
            saveState();
            dirty = false;
            resetSaveSchedule(state.lastWallClockMs);
            lastError = "";
            return true;
        } catch (IOException | RuntimeException exception) {
            state.villages.remove(planned.villageId);
            state.villages.put(before.villageId, before);
            VillageExpansion.prepareDay(state);
            dirty = dirtyBefore;
            lastError = message(exception);
            scheduleSaveRetry(state.lastWallClockMs);
            return false;
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
     * Compact, dimension-scoped structure exclusions for placement searches.
     *
     * <p>Both current reserved bounds and every retained retired lot are included. Callers can
     * therefore keep unrelated villages and structure types out of player-edited ruins without
     * deep-copying the full village registry on each search pulse.</p>
     */
    public synchronized List<VillageProjectLot> villageProjectLotExclusions(
            String dimensionKey) {
        if (state == null) {
            return List.of();
        }
        String resolvedDimension = dimensionKey == null || dimensionKey.isBlank()
                ? "minecraft:overworld"
                : dimensionKey;
        LinkedHashSet<VillageProjectLot> lots = new LinkedHashSet<>();
        for (EconomyState.VillageRecord village : state.villages.values()) {
            if (!resolvedDimension.equals(village.dimensionKey)) {
                continue;
            }
            for (EconomyState.VillageProject project : village.projects) {
                if (project.originPos != 0L
                        && (project.boundsMinPos != 0L || project.boundsMaxPos != 0L)
                        && orderedBounds(project.boundsMinPos, project.boundsMaxPos)) {
                    VillageProjectLot lot = new VillageProjectLot(
                            project.boundsMinPos, project.boundsMaxPos);
                    lots.add(lot);
                }
                for (EconomyState.RetiredProjectLot retired : project.retiredLots) {
                    VillageProjectLot lot = new VillageProjectLot(
                            retired.boundsMinPos, retired.boundsMaxPos);
                    lots.add(lot);
                }
            }
        }
        return List.copyOf(lots);
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
            saveState();
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
                case RAID, PILLAGER, HOSTILE -> village.hostileCasualties =
                        saturatingIncrement(village.hostileCasualties);
                case PLAYER -> {
                    village.playerCasualties = saturatingIncrement(village.playerCasualties);
                    long suppressionDay = state.economicDay
                            > Long.MAX_VALUE - PLAYER_MARKET_SHADOW_DAYS
                                    ? Long.MAX_VALUE
                                    : state.economicDay + PLAYER_MARKET_SHADOW_DAYS;
                    village.marketSuppressedUntilDay = Math.max(
                            village.marketSuppressedUntilDay, suppressionDay);
                }
                case ENVIRONMENT, UNKNOWN -> village.environmentalCasualties =
                        saturatingIncrement(village.environmentalCasualties);
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
                        ? saturatingIncrement(village.collapseCount)
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
            saveState();
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
            case RAID, PILLAGER, HOSTILE -> village.hostileCasualties =
                    saturatingIncrement(village.hostileCasualties);
            case ENVIRONMENT, UNKNOWN -> village.environmentalCasualties =
                    saturatingIncrement(village.environmentalCasualties);
            case PLAYER -> village.playerCasualties =
                    saturatingIncrement(village.playerCasualties);
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
                    ? saturatingIncrement(village.collapseCount)
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
        if (catchUpDaysRemainingInternal() > 0L) {
            lastError = "Economy catch-up is still in progress";
            return VillageFundContributionResult.notContributed();
        }
        if (state.pendingInventoryTransactions.containsKey(playerId)) {
            lastError = "A pending inventory transaction must be recovered first";
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

        EconomyState.ProsperityFund fund = village.prosperityFund;
        EconomyState.DonorRecord existingDonor = state.donors.get(playerId);
        long reserve = type == EconomyState.ProsperityFundType.DIRECT_GRANT
                        && actualPurpose != EconomyState.DonationPurpose.RESTORATION
                ? Math.max(0L, Math.round(
                        amountMicro * prosperityFundPolicy.emergencyReserveFraction()))
                : 0L;
        boolean fundCreditFits = canAdd(fund.lifetimeReceivedMicro, amountMicro)
                && canAdd(fund.donorTotalsMicro.getOrDefault(playerId, 0L), amountMicro)
                && switch (type) {
                    case DIRECT_GRANT -> canAdd(fund.emergencyReserveMicro, reserve)
                            && canAdd(
                                    fund.spendableMicro.getOrDefault(actualPurpose, 0L),
                                    amountMicro - reserve)
                            && canAdd(
                                    fund.fastTrackSpendableMicro.getOrDefault(actualPurpose, 0L),
                                    amountMicro - reserve);
                    case ENDOWMENT -> canAdd(
                            fund.endowmentPrincipalMicro.getOrDefault(actualPurpose, 0L),
                            amountMicro);
                    case PROJECT_SPONSORSHIP -> canAdd(
                            fund.projectSponsorshipMicro.getOrDefault(projectId, 0L),
                            amountMicro);
                };
        boolean donorCreditFits = existingDonor == null
                || (canAdd(existingDonor.lifetimeContributionMicro, amountMicro)
                        && canAdd(existingDonor.byTypeMicro.getOrDefault(type, 0L), amountMicro)
                        && canAdd(
                                existingDonor.byPurposeMicro.getOrDefault(actualPurpose, 0L),
                                amountMicro));
        if (!fundCreditFits || !donorCreditFits) {
            lastError = "Prosperity Fund accounting capacity is exhausted";
            return VillageFundContributionResult.notContributed();
        }

        EconomyState before = state.copy();
        try {
            account.cashMicro -= amountMicro;
            switch (type) {
                case DIRECT_GRANT -> {
                    fund.emergencyReserveMicro = PortfolioAnalytics.add(
                            fund.emergencyReserveMicro, reserve);
                    fund.spendableMicro.merge(
                            actualPurpose, amountMicro - reserve, PortfolioAnalytics::add);
                    fund.fastTrackSpendableMicro.merge(
                            actualPurpose, amountMicro - reserve, PortfolioAnalytics::add);
                    fund.fastTrackProvenanceKnown = true;
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
            saveState();
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
                spent = EconomyState.applyProjectSponsorshipLabor(
                        village,
                        project,
                        requested,
                        state.economicDay,
                        villageVisualProgressionEnabled);
                if (spent <= 0L) return VillageFundSpendingResult.notSpent();
                subtractOrRemove(fund.projectSponsorshipMicro, sponsoredProjectId, spent);
            } else {
                spent = EconomyState.applyFundInputs(
                        village, purpose, requested, state.economicDay);
                if (spent <= 0L) return VillageFundSpendingResult.notSpent();
                EconomyState.debitSpendablePassiveFirst(fund, purpose, spent);
            }
            if (fund.lastSpendingDay != state.economicDay) {
                fund.lastSpendingDay = state.economicDay;
                fund.spentTodayMicro = 0L;
            }
            fund.spentTodayMicro = PortfolioAnalytics.add(fund.spentTodayMicro, spent);
            fund.lifetimeSpentMicro = PortfolioAnalytics.add(fund.lifetimeSpentMicro, spent);
            saveState();
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

    /** Selects one due project and advances the village's persistent fair-rotation cursor. */
    public synchronized Long claimNextDueVillageVisualProject(
            UUID villageId, long currentGameTick) {
        long[] claimedProjectId = {0L};
        boolean claimed = mutateVillage(villageId, false, village -> {
            EconomyState.VillageProject project = village.nextVisualProject(
                    currentGameTick, village.visualProjectSelectionCursor);
            if (project == null) {
                return false;
            }
            claimedProjectId[0] = project.projectId;
            village.visualProjectSelectionCursor = village.visualProjectSelectionCursor
                            == Long.MAX_VALUE
                    ? 0L
                    : village.visualProjectSelectionCursor + 1L;
            return true;
        });
        return claimed ? claimedProjectId[0] : null;
    }

    /** Records a monotonic checkpoint for one unreserved project's bounded site search. */
    public synchronized boolean recordVillageProjectSiteSearchProgress(
            UUID villageId,
            long projectId,
            int testedCandidates,
            boolean sawUnloadedCandidate) {
        if (testedCandidates < 0
                || testedCandidates > EconomyState.MAX_PROJECT_SITE_SEARCH_CANDIDATES
                || (sawUnloadedCandidate && testedCandidates == 0)) {
            return false;
        }
        return mutateVillage(villageId, false, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || project.materializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos != 0L
                    || testedCandidates < project.siteSearchCursor) {
                return false;
            }
            project.siteSearchCursor = testedCandidates;
            project.siteSearchSawUnloadedCandidate |= sawUnloadedCandidate;
            return true;
        });
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
        return reserveVillageProjectSite(
                villageId,
                projectId,
                originPos,
                boundsMinPos,
                boundsMaxPos,
                totalBlocks,
                "",
                0,
                0,
                0L,
                0);
    }

    /** Atomically freezes a managed project's site-facing recipe with its reserved bounds. */
    public synchronized boolean reserveVillageProjectSite(
            UUID villageId,
            long projectId,
            long originPos,
            long boundsMinPos,
            long boundsMaxPos,
            int totalBlocks,
            String architectureDialect,
            int designRotation,
            int designStage,
            long trailAnchorPos,
            int trailTotalBlocks) {
        return reserveVillageProjectSite(
                villageId,
                projectId,
                originPos,
                boundsMinPos,
                boundsMaxPos,
                totalBlocks,
                architectureDialect,
                designRotation,
                designStage,
                trailAnchorPos,
                trailTotalBlocks,
                0,
                0,
                "");
    }

    /**
     * Atomically freezes a managed project's site, road, and bounded entrance-approach recipe.
     * Blueprint V2 callers must use the hash-bearing overload.
     */
    public synchronized boolean reserveVillageProjectSite(
            UUID villageId,
            long projectId,
            long originPos,
            long boundsMinPos,
            long boundsMaxPos,
            int totalBlocks,
            String architectureDialect,
            int designRotation,
            int designStage,
            long trailAnchorPos,
            int trailTotalBlocks,
            int entranceApproachStepCount,
            int entranceApproachTotalCells) {
        return reserveVillageProjectSite(
                villageId,
                projectId,
                originPos,
                boundsMinPos,
                boundsMaxPos,
                totalBlocks,
                architectureDialect,
                designRotation,
                designStage,
                trailAnchorPos,
                trailTotalBlocks,
                entranceApproachStepCount,
                entranceApproachTotalCells,
                "");
    }

    /**
     * Atomically freezes a managed site and the canonical Blueprint V2 placement hash before its
     * origin becomes durable. The hash is retained across safe release, retry, and relocation so
     * an approved project can never adopt changed resource geometry midway through its life.
     */
    public synchronized boolean reserveVillageProjectSite(
            UUID villageId,
            long projectId,
            long originPos,
            long boundsMinPos,
            long boundsMaxPos,
            int totalBlocks,
            String architectureDialect,
            int designRotation,
            int designStage,
            long trailAnchorPos,
            int trailTotalBlocks,
            int entranceApproachStepCount,
            int entranceApproachTotalCells,
            String designPlanHash) {
        return reserveVillageProjectSite(villageId, projectId, originPos, boundsMinPos, boundsMaxPos,
                totalBlocks, architectureDialect, designRotation, designStage, trailAnchorPos,
                trailTotalBlocks, entranceApproachStepCount, entranceApproachTotalCells, designPlanHash, null);
    }

    /** The immutable clearance plan and building reservation are durable before removing anything. */
    public synchronized boolean reserveVillageProjectSite(
            UUID villageId, long projectId, long originPos, long boundsMinPos, long boundsMaxPos,
            int totalBlocks, String architectureDialect, int designRotation, int designStage,
            long trailAnchorPos, int trailTotalBlocks, int entranceApproachStepCount,
            int entranceApproachTotalCells, String designPlanHash, SitePreparationPlan preparation) {
        if (!validEntranceApproachPlan(
                entranceApproachStepCount, entranceApproachTotalCells)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || project.materializedComplete
                    || project.abstractOnly
                    || project.originPos != 0L
                    || totalBlocks <= 0) {
                return false;
            }
            boolean blueprint = VillageArchitecture.BLUEPRINT_SCHEMA.equals(
                    project.designSchema);
            if (blueprint) {
                if (project.designPlanHashVersion
                                != VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION
                        || designPlanHash == null
                        || designPlanHash.isEmpty()
                        || !VillageArchitecture.isValidBlueprintPlanHash(designPlanHash)
                        || (!project.designPlanHash.isEmpty()
                                && !project.designPlanHash.equals(designPlanHash))) {
                    return false;
                }
            } else if (designPlanHash != null && !designPlanHash.isEmpty()) {
                return false;
            }
            if (VillageArchitecture.isManagedStructureSchema(project.designSchema)) {
                if (architectureDialect == null
                        || architectureDialect.isBlank()
                        || designRotation < 0
                        || designRotation > 3
                        || designStage < 0
                        || designStage > 2
                        || trailTotalBlocks <= 0) {
                    return false;
                }
                try {
                    VillageArchitecture.BiomeDialect.fromId(architectureDialect);
                } catch (IllegalArgumentException exception) {
                    return false;
                }
                if (!village.architectureDialect.isBlank()
                        && !village.architectureDialect.equals(architectureDialect)) {
                    return false;
                }
                village.architectureDialect = architectureDialect;
                project.designRotation = designRotation;
                project.designStage = designStage;
                project.trailAnchorSet = true;
                project.trailAnchorPos = trailAnchorPos;
                project.trailMaterializedBlocks = 0;
                project.trailTotalBlocks = trailTotalBlocks;
                project.trailMaterializedComplete = false;
                // This reservation was created with the current center-surface rules. Only roads
                // loaded from older formats need the independent one-time world migration.
                project.trailCenterSurfaceVersion =
                        EconomyState.TRAIL_CENTER_SURFACE_VERSION;
                project.trailCenterSurfaceMigrationCursor = 0;
                project.trailCenterSurfaceMigrationTotalCells = 0;
                project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
                project.entranceApproachStepCount = entranceApproachStepCount;
                project.entranceApproachCursor = 0;
                project.entranceApproachTotalCells = entranceApproachTotalCells;
                project.entranceApproachComplete = entranceApproachTotalCells == 0;
            }
            if (blueprint) {
                project.designPlanHash = designPlanHash;
            }
            project.originPos = originPos;
            project.sitePreparationComplete = false;
            project.sitePreparationCursor = 0;
            project.sitePreparationPlan = preparation;
            project.constructionStarted = false;
            project.boundsMinPos = boundsMinPos;
            project.boundsMaxPos = boundsMaxPos;
            project.totalBlocks = totalBlocks;
            project.materializedBlocks = 0;
            project.blocked = false;
            project.manualRepairRequired = false;
            project.retryAfterGameTick = 0L;
            resetVillageProjectSiteSearch(project);
            return true;
        });
    }

    /** Releases a reserved project site when construction was blocked before any block was placed. */
    public synchronized boolean releaseVillageProjectSite(UUID villageId, long projectId) {
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !project.economicComplete
                    || project.materializedComplete
                    || project.materializedBlocks > 0
                    || project.abstractOnly) {
                return false;
            }
            project.originPos = 0L;
            project.boundsMinPos = 0L;
            project.boundsMaxPos = 0L;
            project.sitePreparationPlan = null;
            project.sitePreparationCursor = 0;
            project.sitePreparationComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            if (VillageArchitecture.isManagedStructureSchema(project.designSchema)) {
                project.designStage = 0;
                if (project.designQualityStage >= 0) {
                    project.designQualityStage = 0;
                }
                project.trailAnchorSet = false;
                project.trailAnchorPos = 0L;
                project.trailMaterializedBlocks = 0;
                project.trailTotalBlocks = 0;
                project.trailMaterializedComplete = false;
                project.trailCenterSurfaceVersion =
                        EconomyState.TRAIL_CENTER_SURFACE_VERSION;
                project.trailCenterSurfaceMigrationCursor = 0;
                project.trailCenterSurfaceMigrationTotalCells = 0;
            }
            resetVillageProjectEntranceApproach(project);
            project.blocked = false;
            project.manualRepairRequired = false;
            project.retryAfterGameTick = 0L;
            resetVillageProjectSiteSearch(project);
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

    /** Persist before the first world write; zero cursors alone never authorize relocation. */
    public synchronized boolean markVillageConstructionStarted(UUID villageId, long projectId) {
        return mutateVillage(villageId, true, village -> {
            var project = findProject(village, projectId);
            if (project == null || project.originPos == 0L || project.materializedComplete) return false;
            project.constructionStarted = true;
            return true;
        });
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
                    || project.materializedComplete
                    || project.manualRepairRequired
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
            if (project.materializedBlocks == 0 && !project.constructionStarted && !retainReservation) {
                project.sitePreparationPlan = null;
                project.sitePreparationCursor = 0;
                project.sitePreparationComplete = true;
                project.originPos = 0L;
                project.boundsMinPos = 0L;
                project.boundsMaxPos = 0L;
                project.totalBlocks = project.type.nominalBlocks();
                if (VillageArchitecture.isManagedStructureSchema(project.designSchema)) {
                    project.designStage = 0;
                    if (project.designQualityStage >= 0) {
                        project.designQualityStage = 0;
                    }
                    project.trailAnchorSet = false;
                    project.trailAnchorPos = 0L;
                    project.trailMaterializedBlocks = 0;
                    project.trailTotalBlocks = 0;
                    project.trailMaterializedComplete = false;
                    project.trailCenterSurfaceVersion =
                            EconomyState.TRAIL_CENTER_SURFACE_VERSION;
                    project.trailCenterSurfaceMigrationCursor = 0;
                    project.trailCenterSurfaceMigrationTotalCells = 0;
                }
                resetVillageProjectEntranceApproach(project);
            }
            resetVillageProjectSiteSearch(project);
            return true;
        });
    }

    /**
     * Atomically commits a fully preflighted append-only managed stage before any suffix block is
     * written. Construction then follows only this persisted target, never a live tier value.
     */
    public synchronized boolean commitVillageProjectVisualStageUpgrade(
            UUID villageId,
            long projectId,
            int designStage,
            int verifiedPrefix,
            int expectedTotal,
            long boundsMinPos,
            long boundsMaxPos) {
        if (designStage < 0
                || designStage > 2
                || verifiedPrefix <= 0
                || expectedTotal <= verifiedPrefix
                || !orderedBounds(boundsMinPos, boundsMaxPos)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !VillageArchitecture.isManagedStructureSchema(project.designSchema)
                    || !project.economicComplete
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || project.manualRepairRequired
                    || !project.materializedComplete
                    || project.materializedBlocks != project.totalBlocks
                    || verifiedPrefix != project.totalBlocks
                    || designStage <= project.designStage
                    || !boundsContainBounds(
                            boundsMinPos,
                            boundsMaxPos,
                            project.boundsMinPos,
                            project.boundsMaxPos)) {
                return false;
            }
            project.designStage = designStage;
            project.totalBlocks = expectedTotal;
            project.materializedBlocks = verifiedPrefix;
            project.boundsMinPos = boundsMinPos;
            project.boundsMaxPos = boundsMaxPos;
            project.materializedComplete = false;
            project.blocked = false;
            project.manualRepairRequired = false;
            project.retryAfterGameTick = 0L;
            project.materializationFailures = 0;
            return true;
        });
    }

    /**
     * Atomically adopts the first append-only quality suffix for a pre-retrofit modular project.
     * The insertion stage is persisted because an old stage-one or stage-two save must keep its
     * exact historical prefix even when a later visual stage is added.
     */
    public synchronized boolean commitVillageProjectQualityUpgrade(
            UUID villageId,
            long projectId,
            int designStage,
            int qualityInsertionStage,
            int verifiedPrefix,
            int expectedTotal,
            long boundsMinPos,
            long boundsMaxPos) {
        if (designStage < 0
                || designStage > 2
                || qualityInsertionStage != designStage
                || verifiedPrefix <= 0
                || expectedTotal <= verifiedPrefix
                || !orderedBounds(boundsMinPos, boundsMaxPos)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !VillageArchitecture.MODULAR_SCHEMA.equals(project.designSchema)
                    || !VillageArchitecture.hasQualityRetrofit(project.type)
                    || project.designQualityStage >= 0
                    || !project.economicComplete
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || project.manualRepairRequired
                    || !project.materializedComplete
                    || project.materializedBlocks != project.totalBlocks
                    || verifiedPrefix != project.totalBlocks
                    || designStage < project.designStage
                    || !boundsContainBounds(
                            boundsMinPos,
                            boundsMaxPos,
                            project.boundsMinPos,
                            project.boundsMaxPos)) {
                return false;
            }
            project.designStage = designStage;
            project.designQualityStage = qualityInsertionStage;
            project.totalBlocks = expectedTotal;
            project.materializedBlocks = verifiedPrefix;
            project.boundsMinPos = boundsMinPos;
            project.boundsMaxPos = boundsMaxPos;
            project.materializedComplete = false;
            project.blocked = false;
            project.manualRepairRequired = false;
            project.retryAfterGameTick = 0L;
            project.materializationFailures = 0;
            return true;
        });
    }

    /**
     * Atomically extends a frozen public-road target while retaining its exact persisted prefix.
     * Used for compatible infrastructure upgrades such as appending shoulder lanes; shrinking or
     * replacing an existing plan is deliberately rejected.
     */
    public synchronized boolean extendVillageProjectTrailTarget(
            UUID villageId,
            long projectId,
            int persistedTotal,
            int expandedTotal) {
        if (persistedTotal <= 0 || expandedTotal <= persistedTotal) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !VillageArchitecture.isManagedStructureSchema(project.designSchema)
                    || !project.economicComplete
                    || !project.materializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || project.trailTotalBlocks != persistedTotal
                    || project.trailMaterializedBlocks > persistedTotal) {
                return false;
            }
            project.trailTotalBlocks = expandedTotal;
            project.trailMaterializedComplete = false;
            return true;
        });
    }

    /** Advances the independent best-effort public-road cursor. */
    public synchronized boolean updateVillageProjectTrailMaterialization(
            UUID villageId,
            long projectId,
            int materializedBlocks,
            int expectedTotal,
            boolean complete) {
        if (materializedBlocks < 0
                || expectedTotal <= 0
                || materializedBlocks > expectedTotal) {
            return false;
        }
        return mutateVillage(villageId, complete, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !VillageArchitecture.isManagedStructureSchema(project.designSchema)
                    || !project.economicComplete
                    || !project.materializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || project.trailTotalBlocks != expectedTotal) {
                return false;
            }
            project.trailMaterializedBlocks = Math.max(
                    project.trailMaterializedBlocks, materializedBlocks);
            project.trailMaterializedComplete = complete
                    && project.trailMaterializedBlocks >= expectedTotal;
            return true;
        });
    }

    /**
     * Freezes the exact size of a pending versioned center-surface scan before any world write.
     * An empty deterministic scan completes the migration immediately.
     */
    public synchronized boolean initializeVillageProjectTrailCenterSurfaceMigration(
            UUID villageId,
            long projectId,
            int expectedVersion,
            int totalCells) {
        if (expectedVersion < 0
                || expectedVersion >= EconomyState.TRAIL_CENTER_SURFACE_VERSION
                || totalCells < 0
                || totalCells > EconomyState.MAX_TRAIL_CENTER_SURFACE_MIGRATION_CELLS) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !VillageArchitecture.MODULAR_SCHEMA.equals(project.designSchema)
                    || !project.economicComplete
                    || !project.materializedComplete
                    || !project.trailMaterializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || project.trailCenterSurfaceVersion != expectedVersion
                    || project.trailCenterSurfaceMigrationCursor != 0
                    || project.trailCenterSurfaceMigrationTotalCells != 0) {
                return false;
            }
            if (totalCells == 0) {
                project.trailCenterSurfaceVersion =
                        EconomyState.TRAIL_CENTER_SURFACE_VERSION;
            } else {
                project.trailCenterSurfaceMigrationTotalCells = totalCells;
            }
            return true;
        });
    }

    /**
     * Advances a versioned one-time center-surface scan independently of every historical road
     * target and cursor. Every cursor update is persisted immediately so a crash cannot cause a
     * previously inspected coordinate to be treated as repairable again.
     */
    public synchronized boolean updateVillageProjectTrailCenterSurfaceMigration(
            UUID villageId,
            long projectId,
            int expectedVersion,
            int expectedCursor,
            int inspectedCells,
            int expectedTotal,
            boolean complete) {
        if (expectedVersion < 0
                || expectedVersion >= EconomyState.TRAIL_CENTER_SURFACE_VERSION
                || expectedCursor < 0
                || inspectedCells < expectedCursor
                || (inspectedCells == expectedCursor && !complete)
                || expectedTotal <= 0
                || expectedTotal > EconomyState.MAX_TRAIL_CENTER_SURFACE_MIGRATION_CELLS
                || inspectedCells > expectedTotal
                || complete != (inspectedCells >= expectedTotal)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !VillageArchitecture.MODULAR_SCHEMA.equals(project.designSchema)
                    || !project.economicComplete
                    || !project.materializedComplete
                    || !project.trailMaterializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || project.trailCenterSurfaceVersion != expectedVersion
                    || project.trailCenterSurfaceMigrationCursor != expectedCursor
                    || project.trailCenterSurfaceMigrationTotalCells != expectedTotal) {
                return false;
            }
            if (complete) {
                project.trailCenterSurfaceVersion =
                        EconomyState.TRAIL_CENTER_SURFACE_VERSION;
                project.trailCenterSurfaceMigrationCursor = 0;
                project.trailCenterSurfaceMigrationTotalCells = 0;
            } else {
                project.trailCenterSurfaceMigrationCursor = inspectedCells;
            }
            return true;
        });
    }

    /** Freezes a bounded approach plan for a completed modular project from an older save. */
    public synchronized boolean initializeVillageProjectEntranceApproach(
            UUID villageId,
            long projectId,
            int expectedVersion,
            int stepCount,
            int totalCells) {
        if (expectedVersion < 0
                || expectedVersion >= EconomyState.ENTRANCE_APPROACH_VERSION
                || !validEntranceApproachPlan(stepCount, totalCells)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (!entranceApproachReady(project)
                    || project.entranceApproachVersion != expectedVersion
                    || project.entranceApproachStepCount != 0
                    || project.entranceApproachCursor != 0
                    || project.entranceApproachTotalCells != 0
                    || project.entranceApproachComplete) {
                return false;
            }
            project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            project.entranceApproachStepCount = stepCount;
            project.entranceApproachCursor = 0;
            project.entranceApproachTotalCells = totalCells;
            project.entranceApproachComplete = totalCells == 0;
            return true;
        });
    }

    /**
     * Advances the frozen entrance-approach cursor. Every successful advance is persisted so an
     * inspected player-owned coordinate can never be reconsidered after a restart.
     */
    public synchronized boolean updateVillageProjectEntranceApproach(
            UUID villageId,
            long projectId,
            int expectedVersion,
            int expectedCursor,
            int completedCells,
            int totalCells,
            boolean complete) {
        if (expectedVersion != EconomyState.ENTRANCE_APPROACH_VERSION
                || expectedCursor < 0
                || completedCells <= expectedCursor
                || totalCells <= 0
                || totalCells > EconomyState.MAX_ENTRANCE_APPROACH_CELLS
                || completedCells > totalCells
                || complete != (completedCells == totalCells)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (!entranceApproachReady(project)
                    || project.entranceApproachVersion != expectedVersion
                    || project.entranceApproachCursor != expectedCursor
                    || project.entranceApproachTotalCells != totalCells
                    || project.entranceApproachComplete) {
                return false;
            }
            project.entranceApproachCursor = completedCells;
            project.entranceApproachComplete = complete;
            return true;
        });
    }

    /**
     * Safely completes an unavailable or obstructed approach without leaving a replayable suffix.
     */
    public synchronized boolean waiveVillageProjectEntranceApproach(
            UUID villageId,
            long projectId,
            int expectedVersion,
            int expectedCursor,
            int totalCells) {
        if (expectedVersion < 0
                || expectedVersion > EconomyState.ENTRANCE_APPROACH_VERSION
                || expectedCursor < 0
                || totalCells < 0
                || totalCells > EconomyState.MAX_ENTRANCE_APPROACH_CELLS) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (!entranceApproachReady(project)
                    || project.entranceApproachVersion != expectedVersion
                    || project.entranceApproachCursor != expectedCursor
                    || project.entranceApproachTotalCells != totalCells
                    || project.entranceApproachComplete) {
                return false;
            }
            if (expectedVersion < EconomyState.ENTRANCE_APPROACH_VERSION) {
                if (expectedCursor != 0
                        || totalCells != 0
                        || project.entranceApproachStepCount != 0) {
                    return false;
                }
                project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            } else if (totalCells == 0) {
                return false;
            }
            project.entranceApproachCursor = totalCells;
            project.entranceApproachComplete = true;
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
            if (project == null
                    || project.originPos == 0L
                    || project.abstractOnly
                    || project.manualRepairRequired
                    || (!project.economicComplete
                            && materializedBlocks >= project.totalBlocks)) {
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
            if (project.materializedComplete) {
                project.relocationPending = false;
            }
            if (project.materializedBlocks > previousBlocks || project.materializedComplete) {
                project.retryAfterGameTick = 0L;
                project.materializationFailures = 0;
            }
            return true;
        });
    }

    /** Records progress only against the already committed template size. */
    public synchronized boolean updateVillageProjectMaterialization(
            UUID villageId,
            long projectId,
            int materializedBlocks,
            int expectedTotal,
            boolean complete,
            boolean blocked) {
        if (materializedBlocks < 0
                || expectedTotal <= 0
                || materializedBlocks > expectedTotal) {
            return false;
        }
        return mutateVillage(villageId, complete || blocked, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || project.originPos == 0L
                    || project.abstractOnly
                    || project.manualRepairRequired
                    || project.totalBlocks != expectedTotal
                    || (!project.economicComplete
                            && materializedBlocks >= expectedTotal)) {
                return false;
            }
            int previousBlocks = project.materializedBlocks;
            project.materializedBlocks = Math.max(project.materializedBlocks, materializedBlocks);
            project.materializedComplete = complete
                    && project.materializedBlocks >= expectedTotal;
            project.blocked = blocked && !project.materializedComplete;
            if (project.materializedComplete) {
                project.relocationPending = false;
            }
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
        return reconcileVillageProjectMaterializationInternal(
                villageId,
                projectId,
                verifiedPrefix,
                expectedTotal,
                structurallyComplete,
                false,
                0L,
                0L);
    }

    /**
     * Atomically reconciles physical progress and the authoritative authored-template bounds.
     * This is used when an append-only template grows or an older save is found with stale bounds.
     */
    public synchronized boolean reconcileVillageProjectMaterializationAndBounds(
            UUID villageId,
            long projectId,
            int verifiedPrefix,
            int expectedTotal,
            boolean structurallyComplete,
            long boundsMinPos,
            long boundsMaxPos) {
        if (!orderedBounds(boundsMinPos, boundsMaxPos)) {
            return false;
        }
        return reconcileVillageProjectMaterializationInternal(
                villageId,
                projectId,
                verifiedPrefix,
                expectedTotal,
                structurallyComplete,
                true,
                boundsMinPos,
                boundsMaxPos);
    }

    /**
     * Suspends a damaged completed structure until its authored blocks are restored in-world.
     *
     * <p>Completed structures deliberately do not regenerate missing collectible blocks. This
     * prevents an integrity audit from turning chests, workstations, or other authored blocks
     * into a renewable item source. A later audit clears this state after the complete template
     * is present again.</p>
     */
    public synchronized boolean requireManualVillageProjectRepair(
            UUID villageId,
            long projectId,
            int verifiedPrefix,
            int expectedTotal,
            long boundsMinPos,
            long boundsMaxPos) {
        if (expectedTotal <= 0
                || verifiedPrefix < 0
                || verifiedPrefix >= expectedTotal
                || !orderedBounds(boundsMinPos, boundsMaxPos)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || project.originPos == 0L
                    || project.abstractOnly
                    || !project.economicComplete
                    || !positionWithinBounds(
                            project.originPos, boundsMinPos, boundsMaxPos)) {
                return false;
            }
            project.totalBlocks = expectedTotal;
            project.materializedBlocks = Math.min(expectedTotal, verifiedPrefix);
            project.boundsMinPos = boundsMinPos;
            project.boundsMaxPos = boundsMaxPos;
            project.materializedComplete = false;
            project.blocked = true;
            project.manualRepairRequired = true;
            // A player change during replacement construction turns that new site into an
            // ordinary unsafe authored building. It must not clear relocation as "complete" or
            // keep trying to fill the player's edit on subsequent construction pulses.
            project.relocationPending = false;
            project.retryAfterGameTick = 0L;
            project.materializationFailures = 0;
            return true;
        });
    }

    /**
     * Retires a severely demolished authored lot and queues the same economic project elsewhere.
     *
     * <p>No world blocks are changed here. The old bounds remain as a permanent exclusion zone so
     * later materialization cannot overwrite ruins, landscaping, or anything a player builds in
     * that space. The project's benefits stay suspended until its replacement is fully built.</p>
     */
    public synchronized boolean relocateDestroyedVillageProject(
            UUID villageId, long projectId, long currentGameTick) {
        if (state == null) {
            return false;
        }
        EconomyState.VillageRecord village = state.existingVillage(villageId);
        EconomyState.VillageProject project = village == null
                ? null
                : findProject(village, projectId);
        if (project == null) {
            return false;
        }
        return relocateDestroyedVillageProject(
                villageId,
                projectId,
                currentGameTick,
                project.boundsMinPos,
                project.boundsMaxPos);
    }

    /** Retires the fully audited bounds, including legacy projects that did not persist them. */
    public synchronized boolean relocateDestroyedVillageProject(
            UUID villageId,
            long projectId,
            long currentGameTick,
            long auditedBoundsMinPos,
            long auditedBoundsMaxPos) {
        if (!orderedBounds(auditedBoundsMinPos, auditedBoundsMaxPos)) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || !project.economicComplete
                    || project.abstractOnly
                    || project.originPos == 0L
                    || (!project.materializedComplete && !project.manualRepairRequired)
                    || !positionWithinBounds(
                            project.originPos,
                            auditedBoundsMinPos,
                            auditedBoundsMaxPos)) {
                return false;
            }
            boolean alreadyRetired = project.retiredLots.stream().anyMatch(lot ->
                    lot.boundsMinPos == auditedBoundsMinPos
                            && lot.boundsMaxPos == auditedBoundsMaxPos);
            if (!alreadyRetired
                    && project.retiredLots.size() >= EconomyState.MAX_RETIRED_PROJECT_LOTS) {
                return false;
            }
            if (!alreadyRetired) {
                project.retiredLots.add(new EconomyState.RetiredProjectLot(
                        auditedBoundsMinPos, auditedBoundsMaxPos));
            }
            project.originPos = 0L;
            project.boundsMinPos = 0L;
            project.boundsMaxPos = 0L;
            project.materializedBlocks = 0;
            project.sitePreparationPlan = null;
            project.sitePreparationCursor = 0;
            project.sitePreparationComplete = true;
            project.totalBlocks = project.type.nominalBlocks();
            project.materializedComplete = false;
            project.blocked = false;
            project.manualRepairRequired = false;
            project.relocationPending = true;
            project.retryAfterGameTick = Math.max(0L, currentGameTick);
            project.materializationFailures = 0;
            if (VillageArchitecture.isManagedStructureSchema(project.designSchema)) {
                // The retired lot owns the historical prefix. Its replacement has no placed
                // cells, so it can restart at stage zero while retaining an adopted quality pass.
                project.designStage = 0;
                if (project.designQualityStage >= 0) {
                    project.designQualityStage = 0;
                }
            }
            project.trailAnchorSet = false;
            project.trailAnchorPos = 0L;
            project.trailMaterializedBlocks = 0;
            project.trailTotalBlocks = 0;
            project.trailMaterializedComplete = false;
            project.trailCenterSurfaceVersion =
                    EconomyState.TRAIL_CENTER_SURFACE_VERSION;
            project.trailCenterSurfaceMigrationCursor = 0;
            project.trailCenterSurfaceMigrationTotalCells = 0;
            resetVillageProjectEntranceApproach(project);
            resetVillageProjectSiteSearch(project);
            return true;
        });
    }

    private boolean reconcileVillageProjectMaterializationInternal(
            UUID villageId,
            long projectId,
            int verifiedPrefix,
            int expectedTotal,
            boolean structurallyComplete,
            boolean refreshBounds,
            long boundsMinPos,
            long boundsMaxPos) {
        if (expectedTotal <= 0 || verifiedPrefix < 0) {
            return false;
        }
        return mutateVillage(villageId, true, village -> {
            EconomyState.VillageProject project = findProject(village, projectId);
            if (project == null
                    || project.originPos == 0L
                    || project.abstractOnly
                    || (refreshBounds
                            && !positionWithinBounds(
                                    project.originPos, boundsMinPos, boundsMaxPos))) {
                return false;
            }
            project.totalBlocks = expectedTotal;
            project.materializedBlocks = Math.min(expectedTotal, verifiedPrefix);
            if (refreshBounds) {
                project.boundsMinPos = boundsMinPos;
                project.boundsMaxPos = boundsMaxPos;
            }
            project.materializedComplete = structurallyComplete
                    && project.materializedBlocks >= expectedTotal;
            project.manualRepairRequired = false;
            project.blocked = false;
            if (project.materializedComplete) {
                project.relocationPending = false;
                project.retryAfterGameTick = 0L;
                project.materializationFailures = 0;
            }
            return true;
        });
    }

    private static boolean validEntranceApproachPlan(int stepCount, int totalCells) {
        return stepCount >= 0
                && stepCount <= EconomyState.MAX_ENTRANCE_APPROACH_CELLS
                && totalCells >= 0
                && totalCells <= EconomyState.MAX_ENTRANCE_APPROACH_CELLS
                && (totalCells > 0 || stepCount == 0);
    }

    private static boolean entranceApproachReady(EconomyState.VillageProject project) {
        return project != null
                && VillageArchitecture.isManagedStructureSchema(project.designSchema)
                && project.economicComplete
                && project.materializedComplete
                && !project.manualRepairRequired
                && !project.abstractOnly
                && project.originPos != 0L
                && project.trailAnchorSet;
    }

    private static void resetVillageProjectEntranceApproach(
            EconomyState.VillageProject project) {
        project.entranceApproachVersion = 0;
        project.entranceApproachStepCount = 0;
        project.entranceApproachCursor = 0;
        project.entranceApproachTotalCells = 0;
        project.entranceApproachComplete = false;
    }

    private static void resetVillageProjectSiteSearch(EconomyState.VillageProject project) {
        project.siteSearchCursor = 0;
        project.siteSearchSawUnloadedCandidate = false;
    }

    private static boolean orderedBounds(long minimum, long maximum) {
        return unpackX(minimum) <= unpackX(maximum)
                && unpackY(minimum) <= unpackY(maximum)
                && unpackZ(minimum) <= unpackZ(maximum);
    }

    private static boolean boundsContainBounds(
            long outerMinimum, long outerMaximum, long innerMinimum, long innerMaximum) {
        return unpackX(outerMinimum) <= unpackX(innerMinimum)
                && unpackY(outerMinimum) <= unpackY(innerMinimum)
                && unpackZ(outerMinimum) <= unpackZ(innerMinimum)
                && unpackX(outerMaximum) >= unpackX(innerMaximum)
                && unpackY(outerMaximum) >= unpackY(innerMaximum)
                && unpackZ(outerMaximum) >= unpackZ(innerMaximum);
    }

    private static boolean positionWithinBounds(long position, long minimum, long maximum) {
        int x = unpackX(position);
        int y = unpackY(position);
        int z = unpackZ(position);
        return x >= unpackX(minimum)
                && x <= unpackX(maximum)
                && y >= unpackY(minimum)
                && y <= unpackY(maximum)
                && z >= unpackZ(minimum)
                && z <= unpackZ(maximum);
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
                saveState();
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
        VillageProsperityEngine.observeHousingCensus(
                village,
                observation.bedCount(),
                Math.max(4, village.population + 2));
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
        VillageProsperityEngine.observeHousingCensus(
                village,
                observation.bedCount(),
                0);
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
            if (proceeds <= 0L || !canAdd(account.cashMicro, proceeds)) {
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
                    transaction.kind == EconomyState.InventoryTransactionKind.EXCHANGE
                            ? "EXCHANGE:" + transaction.itemKey
                            : transaction.kind.name(),
                    0L,
                    transaction.itemCount,
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

    /**
     * Durably clears a committed journal after the caller has verified the matching Minecraft
     * inventory. The cleanup must reach disk before gameplay resumes: otherwise a later legitimate
     * inventory change could be mistaken for transaction drift if a stale journal were recovered.
     */
    public synchronized boolean completeInventoryTransactionAfterVerifiedPlayerSave(
            UUID playerId,
            UUID transactionId) {
        return completeInventoryTransaction(playerId, transactionId);
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

    private boolean observeProgress(
            long now,
            long gameTicks,
            long overworldClockTicks,
            long maximumDaysToAdvance) {
        long safeNow = Math.max(0L, now);
        long safeGameTicks = Math.max(0L, gameTicks);
        long safeOverworldClockTicks = Math.max(0L, overworldClockTicks);
        long trustedNow = Math.max(safeNow, state.lastWallClockMs);
        long wallDeltaMs = trustedNow - state.lastWallClockMs;
        long gameDeltaTicks = safeGameTicks >= state.lastGameTicks
                ? safeGameTicks - state.lastGameTicks
                : 0L;
        long overworldClockDeltaTicks = safeOverworldClockTicks >= lastOverworldClockTicks
                ? safeOverworldClockTicks - lastOverworldClockTicks
                : 0L;
        long gameDeltaMs = ticksToEconomicMillis(gameDeltaTicks);
        long overworldClockDeltaMs = ticksToEconomicMillis(overworldClockDeltaTicks);
        boolean changed = wallDeltaMs > 0L
                || safeGameTicks != state.lastGameTicks
                || safeOverworldClockTicks != lastOverworldClockTicks;

        state.lastWallClockMs = trustedNow;
        state.lastGameTicks = safeGameTicks;
        state.lastOverworldClockTicks = safeOverworldClockTicks;
        lastOverworldClockTicks = safeOverworldClockTicks;
        long maximumOfflineMs = maximumOfflineDays * MILLIS_PER_MINECRAFT_DAY;
        long trustedWallDeltaMs = offlineProgressionEnabled
                ? Math.min(wallDeltaMs, maximumOfflineMs)
                : 0L;
        long elapsedEconomicMs = Math.max(
                trustedWallDeltaMs,
                Math.max(gameDeltaMs, overworldClockDeltaMs));
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

    private static long ticksToEconomicMillis(long ticks) {
        return ticks > Long.MAX_VALUE / MILLIS_PER_GAME_TICK
                ? Long.MAX_VALUE
                : ticks * MILLIS_PER_GAME_TICK;
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
                    prosperityFundPolicy.dailySpendingCapMicro(),
                    prosperityFundPolicy.fastTrackCapitalEnabled(),
                    marketEventsEnabled,
                    peacefulVillageGrowth);
        }
    }

    private long catchUpDaysRemainingInternal() {
        return state.pendingEconomicMillis / MILLIS_PER_MINECRAFT_DAY;
    }

    /** A successful full-state save makes every retained death observation durable. */
    private void saveState() throws IOException {
        state.save(path);
        unpersistedBankerDeathRegions.clear();
    }

    private boolean persistDirtyState(long now) {
        try {
            saveState();
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
            saveState();
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

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
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

    /** Exact active or retired village-project bounds without the surrounding village state. */
    public record VillageProjectLot(long boundsMinPos, long boundsMaxPos) {
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
            long dailySpendingCapMicro,
            boolean fastTrackCapitalEnabled) {
        /** Retains the pre-fast-track capped behavior for direct programmatic callers. */
        public ProsperityFundPolicy(
                boolean enabled,
                double endowmentAnnualPayoutRate,
                double emergencyReserveFraction,
                long dailySpendingCapMicro) {
            this(
                    enabled,
                    endowmentAnnualPayoutRate,
                    emergencyReserveFraction,
                    dailySpendingCapMicro,
                    false);
        }

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
            return new ProsperityFundPolicy(
                    true, 0.04, 0.10, 64L * EconomyState.MICRO, true);
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
