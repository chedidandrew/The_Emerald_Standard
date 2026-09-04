package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-command, time-bounded diagnostic flight recorder for mod testing.
 *
 * <p>When disabled this class performs only a constant-time map lookup from the loader tick hook.
 * During a capture it writes incremental JSON Lines so a crash still leaves useful evidence, then
 * packages a privacy-conscious ZIP report when the timer expires or the command is run again.</p>
 */
public final class DebugFlightRecorder {
    public static final int DEFAULT_MINUTES = 5;
    public static final int MAX_MINUTES = 15;
    public static final String MOD_VERSION = "0.4.0-beta.1";

    private static final Logger LOGGER = LoggerFactory.getLogger("the_emerald_standard_debug");
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static final long SAMPLE_INTERVAL_TICKS = 20L;
    private static final long SNAPSHOT_INTERVAL_TICKS = 600L;
    private static final long STATUS_INTERVAL_TICKS = 400L;
    private static final int MAX_EVENTS = 50_000;
    private static final long MAX_TIMELINE_BYTES = 25L * 1024L * 1024L;
    private static final int RETAINED_REPORTS = 5;

    private DebugFlightRecorder() {
    }

    /** Recovers any crash-interrupted capture folders from a previous server process. */
    public static synchronized void initialize(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Path root = reportRoot(server);
            Files.createDirectories(root);
            recoverInterruptedCaptures(root);
            rotateReports(root);
        } catch (IOException exception) {
            LOGGER.warn("Could not initialize The Emerald Standard debug reports", exception);
        }
    }

    /**
     * Starts a full capture when none is active. Running the same command again stops and packages
     * the current capture, so ordinary testers only need to remember /emerald debug.
     */
    public static synchronized CommandResult toggle(
            ServerPlayer player, EconomyService economy, int requestedMinutes) {
        if (player == null || economy == null || player.level().getServer() == null) {
            return CommandResult.failure("A server-side player and active economy are required.");
        }
        MinecraftServer server = player.level().getServer();
        Session existing = SESSIONS.get(server);
        if (existing != null) {
            return finish(server, economy, existing, "manual_stop", false);
        }

        int minutes = Math.max(1, Math.min(MAX_MINUTES, requestedMinutes));
        try {
            Path root = reportRoot(server);
            Files.createDirectories(root);
            recoverInterruptedCaptures(root);
            rotateReports(root);

            String id = FILE_TIME.format(Instant.now())
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            Path sessionDirectory = root.resolve(".active-" + id);
            Files.createDirectories(sessionDirectory);
            Path timeline = sessionDirectory.resolve("timeline.jsonl");
            BufferedWriter writer = Files.newBufferedWriter(
                    timeline,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
            long now = System.currentTimeMillis();
            long gameTick = server.overworld().getGameTime();
            Session session = new Session(
                    id,
                    player.getUUID(),
                    player.getGameProfile().name(),
                    now,
                    now + minutes * 60_000L,
                    gameTick,
                    sessionDirectory,
                    timeline,
                    writer,
                    dimensionKey(player),
                    player.blockPosition());
            SESSIONS.put(server, session);
            session.event("capture", "started", fields(
                    "durationMinutes", minutes,
                    "tester", session.ownerName,
                    "playerUuid", session.ownerId,
                    "modVersion", MOD_VERSION,
                    "reportId", id));
            session.sample(server, economy, true);
            LOGGER.info(
                    "The Emerald Standard debug capture {} started for {} for {} minute(s)",
                    id,
                    session.ownerName,
                    minutes);
            return new CommandResult(
                    true,
                    true,
                    null,
                    "Full debug capture started for " + minutes
                            + " minute(s). Reproduce the issue now. Run /emerald debug again to stop early; "
                            + "/emerald debug mark adds an optional moment marker.");
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not start The Emerald Standard debug capture", exception);
            return CommandResult.failure("Debug capture could not start: " + message(exception));
        }
    }

    public static synchronized CommandResult stop(
            ServerPlayer player, EconomyService economy) {
        if (player == null || player.level().getServer() == null) {
            return CommandResult.failure("No server debug capture is available.");
        }
        MinecraftServer server = player.level().getServer();
        Session session = SESSIONS.get(server);
        if (session == null) {
            return CommandResult.failure("No debug capture is active. Run /emerald debug to start one.");
        }
        return finish(server, economy, session, "manual_stop", false);
    }

    public static synchronized CommandResult mark(
            ServerPlayer player, EconomyService economy) {
        if (player == null || player.level().getServer() == null) {
            return CommandResult.failure("No server debug capture is available.");
        }
        MinecraftServer server = player.level().getServer();
        Session session = SESSIONS.get(server);
        if (session == null) {
            return CommandResult.failure("No debug capture is active. Run /emerald debug first.");
        }
        try {
            session.markerCount++;
            session.lastDimension = dimensionKey(player);
            session.lastPosition = player.blockPosition().immutable();
            session.event("marker", "manual_marker", fields(
                    "marker", session.markerCount,
                    "player", player.getGameProfile().name(),
                    "dimension", session.lastDimension,
                    "position", positionMap(session.lastPosition)));
            session.sample(server, economy, true);
            writeText(
                    session.sessionDirectory.resolve(
                            String.format(Locale.ROOT, "marker-%02d.txt", session.markerCount)),
                    session.markerSummary(economy));
            return new CommandResult(
                    true,
                    false,
                    null,
                    "Marker " + session.markerCount
                            + " recorded. Mention this marker number when sharing the report.");
        } catch (IOException | RuntimeException exception) {
            session.captureErrors.add("Marker failed: " + message(exception));
            return CommandResult.failure("Could not record marker: " + message(exception));
        }
    }

    /** Called once per server tick by each loader. It is nearly free when no capture is active. */
    public static synchronized void tick(MinecraftServer server, EconomyService economy) {
        Session session = SESSIONS.get(server);
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long gameTick = server.overworld().getGameTime();
        try {
            if (now >= session.endsAtMs) {
                ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerId);
                CommandResult result = finish(server, economy, session, "timer_expired", false);
                notifyPlayer(owner, result.message());
                return;
            }
            if (session.eventCount >= MAX_EVENTS
                    || (Files.exists(session.timeline)
                            && Files.size(session.timeline) >= MAX_TIMELINE_BYTES)) {
                ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerId);
                CommandResult result = finish(server, economy, session, "report_limit_reached", false);
                notifyPlayer(owner, result.message());
                return;
            }
            if (gameTick >= session.nextSampleTick) {
                session.sample(server, economy, false);
                session.nextSampleTick = gameTick + SAMPLE_INTERVAL_TICKS;
            }
            if (gameTick >= session.nextSnapshotTick) {
                session.event("capture", "periodic_snapshot", session.fullSnapshot(server, economy));
                session.nextSnapshotTick = gameTick + SNAPSHOT_INTERVAL_TICKS;
            }
            if (gameTick >= session.nextStatusTick) {
                ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerId);
                if (owner != null) {
                    long seconds = Math.max(0L, (session.endsAtMs - now + 999L) / 1_000L);
                    owner.sendSystemMessage(Component.literal(String.format(
                            Locale.ROOT,
                            "[TES Debug] %02d:%02d remaining | %d events | /emerald debug to stop",
                            seconds / 60L,
                            seconds % 60L,
                            session.eventCount)));
                }
                session.nextStatusTick = gameTick + STATUS_INTERVAL_TICKS;
            }
        } catch (IOException | RuntimeException exception) {
            session.captureErrors.add("Tick capture failed: " + message(exception));
            LOGGER.error("The Emerald Standard debug capture failed", exception);
            ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerId);
            CommandResult result = finish(server, economy, session, "capture_error", true);
            notifyPlayer(owner, result.message());
        }
    }

    public static synchronized void onPlayerDisconnect(
            ServerPlayer player, EconomyService economy) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Session session = SESSIONS.get(server);
        if (session != null && session.ownerId.equals(player.getUUID())) {
            finish(server, economy, session, "tester_disconnected", false);
        }
    }

    public static synchronized void stopForShutdown(
            MinecraftServer server, EconomyService economy) {
        Session session = SESSIONS.get(server);
        if (session != null) {
            finish(server, economy, session, "server_stopping", false);
        }
    }

    /** Records one GUI action for the active tester without exposing any unrelated account. */
    public static synchronized void recordBankAction(
            ServerPlayer player, int actionId, int requestedAmount, int statusCode) {
        Session session = sessionFor(player);
        if (session == null || !session.ownerId.equals(player.getUUID())) {
            return;
        }
        try {
            session.event("banking", "gui_action", fields(
                    "action", bankActionName(actionId),
                    "actionId", actionId,
                    "requestedAmount", requestedAmount,
                    "statusCode", statusCode,
                    "dimension", dimensionKey(player),
                    "position", positionMap(player.blockPosition())));
        } catch (IOException exception) {
            session.captureErrors.add("Banking event failed: " + message(exception));
        }
    }

    public static synchronized void recordVillageObservation(
            MinecraftServer server, EconomyService.VillageSnapshot snapshot) {
        Session session = SESSIONS.get(server);
        if (session == null || snapshot == null || snapshot.village() == null) {
            return;
        }
        EconomyState.VillageRecord village = snapshot.village();
        if (session.watchedVillageId != null
                && !session.watchedVillageId.equals(village.villageId)) {
            return;
        }
        try {
            session.event("village", "loaded_census", villageFields(village));
        } catch (IOException exception) {
            session.captureErrors.add("Village census event failed: " + message(exception));
        }
    }

    public static synchronized void recordVillageIncident(
            ServerLevel level,
            UUID villageId,
            UUID residentId,
            VillageProsperityEngine.IncidentCause cause,
            UUID responsiblePlayer,
            BlockPos position) {
        Session session = sessionFor(level);
        if (session == null) {
            return;
        }
        try {
            session.event("village", "resident_death", fields(
                    "villageId", villageId,
                    "residentId", residentId,
                    "cause", cause,
                    "responsiblePlayer", responsiblePlayer,
                    "dimension", level.dimension().identifier().toString(),
                    "position", positionMap(position)));
        } catch (IOException exception) {
            session.captureErrors.add("Village incident event failed: " + message(exception));
        }
    }

    public static synchronized void recordConstruction(
            ServerLevel level,
            UUID villageId,
            long projectId,
            String projectType,
            String event,
            BlockPos position,
            int completedBlocks,
            int totalBlocks,
            String reason) {
        Session session = sessionFor(level);
        if (session == null) {
            return;
        }
        String milestoneKey = villageId + ":" + projectId;
        if ("progress".equals(event) && totalBlocks > 0) {
            int milestone = Math.min(100, completedBlocks * 100 / totalBlocks) / 25 * 25;
            Integer previous = session.projectMilestones.put(milestoneKey, milestone);
            if (previous != null && milestone <= previous) {
                return;
            }
        }
        try {
            session.event("construction", event, fields(
                    "villageId", villageId,
                    "projectId", projectId,
                    "projectType", projectType,
                    "position", position == null ? null : positionMap(position),
                    "completedBlocks", completedBlocks,
                    "totalBlocks", totalBlocks,
                    "percent", totalBlocks <= 0 ? 0.0 : completedBlocks * 100.0 / totalBlocks,
                    "reason", reason));
        } catch (IOException exception) {
            session.captureErrors.add("Construction event failed: " + message(exception));
        }
    }

    public static synchronized void recordSettler(
            ServerLevel level, UUID villageId, UUID settlerId, BlockPos position) {
        Session session = sessionFor(level);
        if (session == null) {
            return;
        }
        try {
            session.event("village", "settler_spawned", fields(
                    "villageId", villageId,
                    "settlerId", settlerId,
                    "dimension", level.dimension().identifier().toString(),
                    "position", positionMap(position)));
        } catch (IOException exception) {
            session.captureErrors.add("Settler event failed: " + message(exception));
        }
    }

    private static Session sessionFor(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) {
            return null;
        }
        return SESSIONS.get(player.level().getServer());
    }

    private static Session sessionFor(ServerLevel level) {
        return level == null || level.getServer() == null ? null : SESSIONS.get(level.getServer());
    }

    private static CommandResult finish(
            MinecraftServer server,
            EconomyService economy,
            Session session,
            String reason,
            boolean captureFailed) {
        if (session == null) {
            return CommandResult.failure("No debug capture is active.");
        }
        SESSIONS.remove(server);
        Path report = null;
        try {
            session.event("capture", "stopping", fields("reason", reason));
            session.sample(server, economy, true);
            session.writeReportFiles(server, economy, reason, captureFailed);
            session.writer.flush();
            session.writer.close();
            report = reportRoot(server).resolve("TES-debug-" + session.id
                    + (captureFailed ? "-INCOMPLETE" : "") + ".zip");
            zipDirectory(session.sessionDirectory, report);
            deleteTree(session.sessionDirectory);
            rotateReports(reportRoot(server));
            LOGGER.info(
                    "The Emerald Standard debug capture {} finished: {}",
                    session.id,
                    report.toAbsolutePath());
            return new CommandResult(
                    true,
                    false,
                    report,
                    "Debug capture saved: " + report.toAbsolutePath());
        } catch (IOException | RuntimeException exception) {
            try {
                session.writer.close();
            } catch (IOException ignored) {
                // Preserve the incremental session directory for next-start recovery.
            }
            LOGGER.error("Could not package The Emerald Standard debug capture", exception);
            return CommandResult.failure(
                    "Debug capture stopped, but packaging failed. Raw files remain at "
                            + session.sessionDirectory.toAbsolutePath()
                            + ". Reason: " + message(exception));
        }
    }

    private static void notifyPlayer(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal("[Emerald Standard] " + message));
        } else {
            LOGGER.info(message);
        }
    }

    private static Path reportRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve("the_emerald_standard_debug");
    }

    private static void recoverInterruptedCaptures(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            for (Path directory : stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(".active-"))
                    .toList()) {
                String id = directory.getFileName().toString().substring(".active-".length());
                writeText(
                        directory.resolve("INCOMPLETE-CRASH.txt"),
                        "This capture was interrupted before a normal stop, most likely by a crash or forced process exit.\n"
                                + "The incremental timeline remains valid up to the last flushed event.\n");
                Path destination = root.resolve("TES-debug-" + id + "-INCOMPLETE-CRASH.zip");
                zipDirectory(directory, destination);
                deleteTree(directory);
                LOGGER.info("Recovered interrupted debug capture {}", destination.toAbsolutePath());
            }
        }
    }

    private static void rotateReports(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> reports;
        try (var stream = Files.list(root)) {
            reports = stream
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(DebugFlightRecorder::lastModified).reversed())
                    .toList();
        }
        for (int index = RETAINED_REPORTS; index < reports.size(); index++) {
            Files.deleteIfExists(reports.get(index));
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static void zipDirectory(Path directory, Path destination) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
            try (var stream = Files.walk(directory)) {
                for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                    String name = directory.relativize(path).toString().replace('\\', '/');
                    output.putNextEntry(new ZipEntry(name));
                    Files.copy(path, output);
                    output.closeEntry();
                }
            }
        }
        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                    throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writeText(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    private static String dimensionKey(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private static Map<String, Object> positionMap(BlockPos position) {
        if (position == null) {
            return Map.of();
        }
        return fields("x", position.getX(), "y", position.getY(), "z", position.getZ());
    }

    private static Map<String, Object> fields(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static String bankActionName(int actionId) {
        return switch (actionId) {
            case BankerMenu.ACTION_DEPOSIT -> "deposit";
            case BankerMenu.ACTION_WITHDRAW -> "withdraw";
            case BankerMenu.ACTION_SAVINGS_DEPOSIT -> "savings_deposit";
            case BankerMenu.ACTION_SAVINGS_WITHDRAW -> "savings_withdraw";
            case BankerMenu.ACTION_BUY -> "buy";
            case BankerMenu.ACTION_SELL_QUARTER -> "sell_25_percent";
            case BankerMenu.ACTION_SELL_ALL -> "sell_all";
            case BankerMenu.ACTION_OPEN_CD -> "open_cd";
            case BankerMenu.ACTION_CLOSE_CD -> "close_cd";
            case BankerMenu.ACTION_FUND_LENDING -> "fund_lending";
            case BankerMenu.ACTION_COLLECT_LENDING -> "collect_lending";
            case BankerMenu.ACTION_EXCHANGE -> "exchange";
            case BankerMenu.ACTION_RECOVER -> "recover";
            case BankerMenu.ACTION_SUPPORT_VILLAGE -> "support_village";
            default -> "unknown_" + actionId;
        };
    }

    private static Map<String, Object> marketFields(EconomyService.MarketSnapshot market) {
        if (market == null) {
            return fields("ready", false);
        }
        return fields(
                "ready", true,
                "economicDay", market.economicDay(),
                "regime", market.regime(),
                "lastMarketEvent", market.lastMarketEvent(),
                "lastMarketEventDay", market.lastMarketEventDay(),
                "catchUpDays", market.catchUpDaysRemaining(),
                "dirty", market.dirty(),
                "savingsAnnualRate", EconomyEngine.savingsAnnualRate(market.regime()),
                "cd90AnnualRate", EconomyEngine.cdAnnualRate(market.regime(), 90),
                "prices", market.prices(),
                "commodities", market.commodityPrices());
    }

    private static Map<String, Object> portfolioFields(EconomyService.PortfolioSnapshot portfolio) {
        if (portfolio == null) {
            return fields("ready", false);
        }
        EconomyState.Account account = portfolio.account();
        Map<String, Object> pending = portfolio.pendingTransaction() == null
                ? Map.of()
                : fields(
                        "transactionId", portfolio.pendingTransaction().transactionId,
                        "kind", portfolio.pendingTransaction().kind,
                        "stage", portfolio.pendingTransaction().stage,
                        "itemKey", portfolio.pendingTransaction().itemKey,
                        "itemCount", portfolio.pendingTransaction().itemCount,
                        "bankDeltaEmeralds",
                        portfolio.pendingTransaction().bankDeltaMicro / (double) EconomyState.MICRO);
        return fields(
                "ready", true,
                "economicDay", portfolio.economicDay(),
                "cashEmeralds", account.cashMicro / (double) EconomyState.MICRO,
                "savingsEmeralds", account.savingsMicro / (double) EconomyState.MICRO,
                "netWorthEmeralds", portfolio.netWorth(),
                "shares", account.shares,
                "cd", account.hasCd() ? fields(
                        "valueEmeralds", account.cdValueMicro / (double) EconomyState.MICRO,
                        "principalEmeralds", account.cdPrincipalMicro / (double) EconomyState.MICRO,
                        "annualRate", account.cdAnnualRate,
                        "openDay", account.cdOpenDay,
                        "maturityDay", account.cdMaturityDay) : Map.of(),
                "lending", account.hasLoan() ? fields(
                        "valueEmeralds", account.loanValueMicro / (double) EconomyState.MICRO,
                        "principalEmeralds", account.loanPrincipalMicro / (double) EconomyState.MICRO,
                        "annualRate", account.loanAnnualRate,
                        "openDay", account.loanOpenDay,
                        "maturityDay", account.loanMaturityDay,
                        "resolved", account.loanResolved,
                        "outcome", account.loanOutcome,
                        "recoveryRate", account.loanRecoveryRate) : Map.of(),
                "pendingInventoryTransaction", pending,
                "catchUpDays", portfolio.catchUpDaysRemaining());
    }

    private static Map<String, Object> villageFields(EconomyState.VillageRecord village) {
        if (village == null) {
            return fields("present", false);
        }
        EconomyState.VillageProject active = village.projects.stream()
                .filter(project -> !project.materializedComplete)
                .findFirst()
                .orElse(null);
        Map<String, Object> project = active == null
                ? Map.of()
                : fields(
                        "projectId", active.projectId,
                        "type", active.type,
                        "economicProgress", active.economicProgress,
                        "economicComplete", active.economicComplete,
                        "materializedBlocks", active.materializedBlocks,
                        "totalBlocks", active.totalBlocks,
                        "materializedComplete", active.materializedComplete,
                        "blocked", active.blocked,
                        "retryAfterGameTick", active.retryAfterGameTick,
                        "origin", active.originPos == 0L
                                ? Map.of()
                                : positionMap(BlockPos.of(active.originPos)));
        return fields(
                "present", true,
                "villageId", village.villageId,
                "dimension", village.dimensionKey,
                "center", positionMap(BlockPos.of(village.centerPos)),
                "lifecycle", village.lifecycle,
                "population", village.population,
                "observedPopulation", village.observedPopulation,
                "housingCapacity", village.housingCapacity,
                "pendingSettlers", village.pendingSettlers,
                "developmentTier", village.developmentTier,
                "prosperity", village.prosperity,
                "safety", village.safety,
                "food", village.foodSupply,
                "materials", village.materialSupply,
                "treasury", village.treasury,
                "agricultureOutput", village.agricultureOutput,
                "miningOutput", village.miningOutput,
                "tradeOutput", village.tradeOutput,
                "redstoneOutput", village.redstoneOutput,
                "alchemyOutput", village.alchemyOutput,
                "transportOutput", village.transportOutput,
                "securityOutput", village.securityOutput,
                "restorationFund", village.restorationFund,
                "marketSuppressedUntilDay", village.marketSuppressedUntilDay,
                "residentsTracked", village.residents.size(),
                "incidentsTracked", village.incidents.size(),
                "projectBacklog", village.visualBacklog(),
                "activeProject", project);
    }

    private static String json(Map<String, Object> value) {
        return jsonValue(value);
    }

    @SuppressWarnings("unchecked")
    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Float number) {
            return Float.isFinite(number) ? number.toString() : quote(number.toString());
        }
        if (value instanceof Double number) {
            return Double.isFinite(number) ? number.toString() : quote(number.toString());
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(quote(String.valueOf(entry.getKey())))
                        .append(':')
                        .append(jsonValue(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(jsonValue(item));
            }
            return builder.append(']').toString();
        }
        if (value.getClass().isArray()) {
            if (value instanceof Object[] array) {
                return jsonValue(List.of(array));
            }
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private static String message(Throwable exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank()
                ? exception.getClass().getSimpleName()
                : value;
    }

    public record CommandResult(boolean success, boolean started, Path report, String message) {
        static CommandResult failure(String message) {
            return new CommandResult(false, false, null, message);
        }
    }

    private static final class Session {
        final String id;
        final UUID ownerId;
        final String ownerName;
        final long startedAtMs;
        long endsAtMs;
        final long startedGameTick;
        final Path sessionDirectory;
        final Path timeline;
        final BufferedWriter writer;
        final List<String> captureErrors = new ArrayList<>();
        final List<String> validationWarnings = new ArrayList<>();
        final Map<String, Integer> projectMilestones = new HashMap<>();
        long nextSampleTick;
        long nextSnapshotTick;
        long nextStatusTick;
        int eventCount;
        int markerCount;
        long sampleCount;
        long sampleTotalNanos;
        long sampleMaximumNanos;
        long previousEconomicDay = Long.MIN_VALUE;
        EconomyEngine.Regime previousRegime;
        EconomyEngine.MarketEvent previousMarketEvent;
        Map<String, Double> previousPrices = Map.of();
        String previousPortfolioFingerprint = "";
        String previousVillageFingerprint = "";
        UUID watchedVillageId;
        String lastDimension;
        BlockPos lastPosition;
        EconomyService.MarketSnapshot latestMarket;
        EconomyService.PortfolioSnapshot latestPortfolio;
        EconomyService.VillageSnapshot latestVillage;

        Session(
                String id,
                UUID ownerId,
                String ownerName,
                long startedAtMs,
                long endsAtMs,
                long startedGameTick,
                Path sessionDirectory,
                Path timeline,
                BufferedWriter writer,
                String lastDimension,
                BlockPos lastPosition) {
            this.id = id;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.startedAtMs = startedAtMs;
            this.endsAtMs = endsAtMs;
            this.startedGameTick = startedGameTick;
            this.sessionDirectory = sessionDirectory;
            this.timeline = timeline;
            this.writer = writer;
            this.lastDimension = lastDimension;
            this.lastPosition = lastPosition.immutable();
            this.nextSampleTick = startedGameTick;
            this.nextSnapshotTick = startedGameTick;
            this.nextStatusTick = startedGameTick;
        }

        void event(String category, String event, Map<String, Object> details) throws IOException {
            LinkedHashMap<String, Object> line = new LinkedHashMap<>();
            line.put("timestamp", Instant.now().toString());
            line.put("elapsedMs", Math.max(0L, System.currentTimeMillis() - startedAtMs));
            line.put("category", category);
            line.put("event", event);
            line.putAll(details);
            writer.write(json(line));
            writer.newLine();
            writer.flush();
            eventCount++;
        }

        void sample(MinecraftServer server, EconomyService economy, boolean force) throws IOException {
            long started = System.nanoTime();
            try {
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
                if (owner != null) {
                    lastDimension = dimensionKey(owner);
                    lastPosition = owner.blockPosition().immutable();
                }
                latestMarket = economy.marketSnapshot();
                latestPortfolio = economy.portfolioSnapshot(ownerId);
                latestVillage = lastPosition == null
                        ? null
                        : economy.nearestVillageSnapshot(lastDimension, lastPosition.asLong(), 256.0);
                if (latestVillage != null) {
                    watchedVillageId = latestVillage.village().villageId;
                }

                if (latestMarket != null
                        && (force || latestMarket.economicDay() != previousEconomicDay)) {
                    LinkedHashMap<String, Object> returns = new LinkedHashMap<>();
                    for (Map.Entry<String, Double> entry : latestMarket.prices().entrySet()) {
                        Double previous = previousPrices.get(entry.getKey());
                        if (previous != null && previous > 0.0) {
                            returns.put(entry.getKey(), entry.getValue() / previous - 1.0);
                        }
                    }
                    event("market", "economic_day", fields(
                            "economicDay", latestMarket.economicDay(),
                            "regime", latestMarket.regime(),
                            "regimeChanged", previousRegime != null
                                    && previousRegime != latestMarket.regime(),
                            "marketEvent", latestMarket.lastMarketEvent(),
                            "marketEventChanged", previousMarketEvent != null
                                    && previousMarketEvent != latestMarket.lastMarketEvent(),
                            "returns", returns,
                            "prices", latestMarket.prices(),
                            "commodities", latestMarket.commodityPrices(),
                            "catchUpDays", latestMarket.catchUpDaysRemaining()));
                    previousEconomicDay = latestMarket.economicDay();
                    previousRegime = latestMarket.regime();
                    previousMarketEvent = latestMarket.lastMarketEvent();
                    previousPrices = Map.copyOf(latestMarket.prices());
                }

                String portfolioFingerprint = json(portfolioFields(latestPortfolio));
                if (force || !portfolioFingerprint.equals(previousPortfolioFingerprint)) {
                    event("portfolio", "account_state_changed", portfolioFields(latestPortfolio));
                    previousPortfolioFingerprint = portfolioFingerprint;
                }

                String villageFingerprint = latestVillage == null
                        ? "none"
                        : json(villageFields(latestVillage.village()));
                if (force || !villageFingerprint.equals(previousVillageFingerprint)) {
                    event("village", "village_state_changed", latestVillage == null
                            ? fields(
                                    "present", false,
                                    "dimension", lastDimension,
                                    "playerPosition", positionMap(lastPosition))
                            : villageFields(latestVillage.village()));
                    previousVillageFingerprint = villageFingerprint;
                }
            } finally {
                long elapsed = System.nanoTime() - started;
                sampleCount++;
                sampleTotalNanos += elapsed;
                sampleMaximumNanos = Math.max(sampleMaximumNanos, elapsed);
            }
        }

        Map<String, Object> fullSnapshot(MinecraftServer server, EconomyService economy) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            return fields(
                    "testerOnline", owner != null,
                    "tester", ownerName,
                    "dimension", lastDimension,
                    "position", positionMap(lastPosition),
                    "market", marketFields(latestMarket == null ? economy.marketSnapshot() : latestMarket),
                    "portfolio", portfolioFields(
                            latestPortfolio == null
                                    ? economy.portfolioSnapshot(ownerId)
                                    : latestPortfolio),
                    "village", latestVillage == null
                            ? fields("present", false)
                            : villageFields(latestVillage.village()),
                    "knownVillages", economy.snapshot() == null
                            ? 0
                            : economy.snapshot().villages.size());
        }

        String markerSummary(EconomyService economy) {
            StringBuilder builder = new StringBuilder();
            builder.append("The Emerald Standard debug marker ")
                    .append(markerCount)
                    .append('\n');
            builder.append("Report ID: ").append(id).append('\n');
            builder.append("Tester: ").append(ownerName).append('\n');
            builder.append("Dimension: ").append(lastDimension).append('\n');
            builder.append("Position: ").append(lastPosition).append('\n');
            builder.append("Economic day: ")
                    .append(latestMarket == null ? "unavailable" : latestMarket.economicDay())
                    .append('\n');
            builder.append("Regime: ")
                    .append(latestMarket == null ? "unavailable" : latestMarket.regime())
                    .append('\n');
            builder.append("Nearest village: ")
                    .append(latestVillage == null ? "none" : latestVillage.village().villageId)
                    .append('\n');
            builder.append("Events recorded: ").append(eventCount).append('\n');
            return builder.toString();
        }

        void writeReportFiles(
                MinecraftServer server,
                EconomyService economy,
                String reason,
                boolean captureFailed) throws IOException {
            Validation validation = validate(economy);
            validationWarnings.clear();
            validationWarnings.addAll(validation.warnings);
            long durationMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
            writeText(sessionDirectory.resolve("README.txt"),
                    "The Emerald Standard diagnostic flight-recorder report\n\n"
                            + "Share this ZIP together with a short explanation of what looked wrong.\n"
                            + "When markers were created, mention their marker numbers.\n"
                            + "The report intentionally excludes the private economy seed, world seed, chat, server address, and unrelated player accounts.\n");
            writeText(sessionDirectory.resolve("summary.txt"),
                    "Report ID: " + id + "\n"
                            + "Mod version: " + MOD_VERSION + "\n"
                            + "Tester: " + ownerName + "\n"
                            + "Started UTC: " + Instant.ofEpochMilli(startedAtMs) + "\n"
                            + "Duration seconds: " + String.format(Locale.ROOT, "%.1f", durationMs / 1000.0) + "\n"
                            + "Stop reason: " + reason + "\n"
                            + "Incomplete: " + captureFailed + "\n"
                            + "Events: " + eventCount + "\n"
                            + "Markers: " + markerCount + "\n"
                            + "Watched village: " + Objects.toString(watchedVillageId, "none") + "\n"
                            + "Capture errors: " + captureErrors.size() + "\n"
                            + "Validation warnings: " + validationWarnings.size() + "\n");
            writeText(sessionDirectory.resolve("validation.txt"), validation.text);
            writeText(sessionDirectory.resolve("environment.txt"),
                    "Mod version: " + MOD_VERSION + "\n"
                            + "Java version: " + System.getProperty("java.version", "unknown") + "\n"
                            + "Java vendor: " + System.getProperty("java.vendor", "unknown") + "\n"
                            + "Operating system: " + System.getProperty("os.name", "unknown") + " "
                            + System.getProperty("os.version", "unknown") + "\n"
                            + "Available processors: " + Runtime.getRuntime().availableProcessors() + "\n"
                            + "Maximum JVM memory bytes: " + Runtime.getRuntime().maxMemory() + "\n"
                            + "Dimension: " + lastDimension + "\n");
            writeText(sessionDirectory.resolve("market-snapshot.json"),
                    json(marketFields(economy.marketSnapshot())) + "\n");
            writeText(sessionDirectory.resolve("player-snapshot.json"),
                    json(portfolioFields(economy.portfolioSnapshot(ownerId))) + "\n");
            EconomyService.VillageSnapshot village = lastPosition == null
                    ? null
                    : economy.nearestVillageSnapshot(lastDimension, lastPosition.asLong(), 256.0);
            writeText(sessionDirectory.resolve("village-snapshot.json"),
                    json(village == null
                            ? fields("present", false)
                            : villageFields(village.village())) + "\n");
            double averageMs = sampleCount == 0
                    ? 0.0
                    : sampleTotalNanos / 1_000_000.0 / sampleCount;
            writeText(sessionDirectory.resolve("performance-summary.json"),
                    json(fields(
                            "samples", sampleCount,
                            "averageSampleMs", averageMs,
                            "maximumSampleMs", sampleMaximumNanos / 1_000_000.0,
                            "events", eventCount,
                            "timelineBytes", Files.exists(timeline) ? Files.size(timeline) : 0L)) + "\n");
            if (!captureErrors.isEmpty()) {
                writeText(
                        sessionDirectory.resolve("capture-errors.txt"),
                        String.join("\n", captureErrors) + "\n");
            }
            Path config = server.getWorldPath(LevelResource.DATA)
                    .resolve("the_emerald_standard-config.properties");
            if (Files.isRegularFile(config)) {
                Files.copy(
                        config,
                        sessionDirectory.resolve("config.properties"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private Validation validate(EconomyService economy) {
            List<String> warnings = new ArrayList<>();
            StringBuilder text = new StringBuilder("The Emerald Standard validation\n\n");
            EconomyState state = economy.snapshot();
            if (state == null) {
                text.append("Economy state: FAIL - unavailable\n");
                return new Validation(false, warnings, text.toString());
            }
            boolean valid = true;
            try {
                state.validate();
                text.append("Core persisted-state invariants: PASS\n");
            } catch (IOException exception) {
                valid = false;
                text.append("Core persisted-state invariants: FAIL - ")
                        .append(message(exception))
                        .append('\n');
            }
            for (Map.Entry<String, Double> entry : state.prices.entrySet()) {
                if (!Double.isFinite(entry.getValue()) || entry.getValue() <= 0.0) {
                    valid = false;
                    warnings.add("Invalid market price for " + entry.getKey());
                }
            }
            for (EconomyState.VillageRecord village : state.villages.values()) {
                if (village.population <= 0
                        && (village.agricultureOutput > 1.0e-9
                                || village.miningOutput > 1.0e-9
                                || village.tradeOutput > 1.0e-9)) {
                    warnings.add("Village " + village.villageId
                            + " has zero productive population but nonzero output.");
                }
                if (village.observedPopulation > village.housingCapacity) {
                    warnings.add("Village " + village.villageId
                            + " has more observed residents than housing capacity.");
                }
                for (EconomyState.VillageProject project : village.projects) {
                    if (project.materializedBlocks > project.totalBlocks) {
                        valid = false;
                        warnings.add("Village " + village.villageId + " project "
                                + project.projectId + " exceeds its block total.");
                    }
                }
            }
            if (!state.pendingInventoryTransactions.isEmpty()) {
                warnings.add(state.pendingInventoryTransactions.size()
                        + " inventory transaction journal(s) remain pending.");
            }
            if (economy.catchUpDaysRemaining() > 0L) {
                warnings.add("The economy still has " + economy.catchUpDaysRemaining()
                        + " catch-up day(s) remaining.");
            }
            text.append("Market prices: ").append(valid ? "PASS" : "CHECK WARNINGS").append('\n');
            text.append("No-debt and account invariants: ").append(valid ? "PASS" : "CHECK WARNINGS").append('\n');
            text.append("Villages and projects: ").append(valid ? "PASS" : "CHECK WARNINGS").append('\n');
            text.append("Warnings: ").append(warnings.size()).append("\n\n");
            for (String warning : warnings) {
                text.append("- ").append(warning).append('\n');
            }
            return new Validation(valid, warnings, text.toString());
        }
    }

    private record Validation(boolean valid, List<String> warnings, String text) {
    }
}
