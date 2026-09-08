package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageComparisonBiomePlan;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in, curated A/B exhibition. Vanilla references are real bundled village templates, not
 * procedurally assembled villages or redesigned/recolored imitations. No economy is registered.
 * A new exact-name flat save is required; existing or partially obstructed plots fail closed.
 */
public final class VillageComparisonGallery {
    public static final String ENABLE_PROPERTY = "the_emerald_standard.villageComparison";
    public static final String AUTO_BUILD_PROPERTY = ENABLE_PROPERTY + ".autoBuild";
    public static final String WORLD_DIRECTORY = "TES_Village_Comparison";
    public static final int COMPARISON_SCHEMA = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("the_emerald_standard_comparison");
    private static final int COLUMNS = 8;
    private static final int HALF_PITCH = 56;
    private static final int PAIR_PITCH = HALF_PITCH * 2;
    private static final int ROW_PITCH = 96;
    private static final int DISTRICT_PITCH = COLUMNS * PAIR_PITCH + 128;
    private static final int FOUNDATION_DEPTH = 6;
    private static final Map<MinecraftServer, BuildState> STATES = new WeakHashMap<>();

    private VillageComparisonGallery() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY) && StructureGallery.enabled();
    }

    public static boolean hasCommandAccess(CommandSourceStack source) {
        return source.getServer() != null && isExactWorld(source.getServer());
    }

    /** Safe loader hook: starts an incremental build, never writes to the old review gallery. */
    public static void autoBuildIfRequested(MinecraftServer server) {
        if (enabled() && Boolean.getBoolean(AUTO_BUILD_PROPERTY) && isExactWorld(server)) {
            begin(server);
        }
    }

    /** Call on the server thread once per end tick; at most one complete pair is placed. */
    public static void tick(MinecraftServer server) {
        BuildState state = STATES.get(server);
        if (state == null || state.ready || state.failure != null || !isExactWorld(server)) {
            return;
        }
        try {
            if (state.next < state.pairs.size()) {
                ResolvedPair pair = state.pairs.get(state.next);
                BlockPos marker = pairMarker(server.overworld(), pair.entry.index());
                BlockState status = server.overworld().getBlockState(marker);
                if (status.is(Blocks.EMERALD_BLOCK)) {
                    state.next++;
                    return;
                }
                if (status.is(Blocks.REDSTONE_BLOCK)) {
                    throw new IllegalStateException("Pair " + pair.entry.index()
                            + " was interrupted. Preserve this save and create a fresh comparison save.");
                }
                placePair(server.overworld(), pair);
                state.next++;
                if (state.next % 10 == 0 || state.next == state.pairs.size()) {
                    LOGGER.info("Village comparison: {}/{} pairs ready", state.next, state.pairs.size());
                }
            } else if (state.nextCourt < state.courts.size()) {
                VillageCourt court = state.courts.get(state.nextCourt);
                BlockPos marker = pairMarker(server.overworld(), expectedPairCount() + state.nextCourt + 1);
                if (!server.overworld().getBlockState(marker).is(Blocks.EMERALD_BLOCK)) {
                    if (server.overworld().getBlockState(marker).is(Blocks.REDSTONE_BLOCK)) {
                        throw new IllegalStateException("An interrupted context court requires a fresh comparison save.");
                    }
                    placeCourt(server.overworld(), court, marker);
                }
                state.nextCourt++;
            } else {
                writeIndex(server, state);
                writeSignature(server.overworld(), state.signature, true);
                state.ready = true;
                LOGGER.info("Village comparison ready: {} pairs plus {} vanilla context courts; {} actual structures",
                        state.pairs.size(), state.courts.size(), state.pairs.size() * 2 + state.courts.size() * 4);
            }
        } catch (RuntimeException exception) {
            state.failure = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            LOGGER.error("Comparison build stopped without clearing or rebuilding existing blocks", exception);
        }
    }

    /** Exact readiness for optional capture harnesses. No screenshot batch should start earlier. */
    public static boolean isReady(MinecraftServer server) {
        BuildState state = STATES.get(server);
        return isExactWorld(server) && state != null && state.ready
                && signatureMatches(server.overworld(), state.signature);
    }

    /** Read-only on the server thread; failures are disclosed only for the exact opted-in save. */
    public static String failureReason(MinecraftServer server) {
        if (!isExactWorld(server)) return null;
        BuildState state = STATES.get(server);
        return state == null ? null : state.failure;
    }

    /** Immutable metadata suitable for a capture manifest or an interactive index. */
    public static List<ComparisonEntry> entries(MinecraftServer server) {
        requireReady(server);
        return STATES.get(server).pairs.stream().map(ResolvedPair::entry).toList();
    }

    public static List<ContextCourt> contextCourts(MinecraftServer server) {
        requireReady(server);
        return STATES.get(server).courts.stream().map(VillageCourt::metadata).toList();
    }

    /** Three reproducible exterior views per pair: shared A/B context, mod detail, vanilla detail. */
    public static List<ViewPose> captureViews(MinecraftServer server) {
        requireReady(server);
        List<ViewPose> views = new ArrayList<>();
        for (ResolvedPair pair : STATES.get(server).pairs) {
            ComparisonEntry e = pair.entry;
            double midX = (e.modX() + e.modWidth() / 2.0
                    + e.vanillaX() + e.vanillaWidth() / 2.0) / 2.0;
            views.add(clearPreviousRow(STATES.get(server).pairs, e,
                    pose(e.index(), "pair", midX, e.surfaceY(), e.modZ(),
                            PAIR_PITCH - 16, Math.max(e.modHeight(), e.vanillaHeight())),
                    e.modZ(), Math.max(e.modHeight(), e.vanillaHeight())));
            views.add(clearPreviousRow(STATES.get(server).pairs, e,
                    pose(e.index(), "mod", e.modX() + e.modWidth() / 2.0,
                            e.surfaceY(), e.modZ() - 7, e.modWidth() + 14, e.modHeight()),
                    e.modZ() - 7, e.modHeight()));
            views.add(clearPreviousRow(STATES.get(server).pairs, e,
                    pose(e.index(), "vanilla", e.vanillaX() + e.vanillaWidth() / 2.0,
                            e.surfaceY(), e.vanillaZ() - 2, e.vanillaWidth() + 6, e.vanillaHeight()),
                    e.vanillaZ() - 2, e.vanillaHeight()));
        }
        return List.copyOf(views);
    }

    /** Capture movement is separately opted in and accepts only this ready catalog's poses. */
    public static void teleportForCapture(MinecraftServer server, UUID playerId, ViewPose pose) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY + ".capture") || !isReady(server)
                || !captureViews(server).contains(pose)) {
            throw new IllegalStateException("Comparison capture movement is not authorized for this pose/world");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Comparison capture player is unavailable");
        }
        player.setGameMode(GameType.SPECTATOR);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(server.overworld(), pose.x(), pose.y(), pose.z(), Set.of(),
                pose.yaw(), pose.pitch(), true);
    }

    /** Read-only resource/metadata admission for real-loader tests; creates no blocks/economy. */
    public static int verifyPlan(ServerLevel level) {
        List<ResolvedPair> pairs = resolvePairs(level, 0);
        List<VillageCourt> courts = resolveCourts(level, 0);
        if (courts.size() != VillageArchitecture.BiomeDialect.values().length
                || courts.stream().anyMatch(court -> court.buildings.size() != 4)) {
            throw new IllegalStateException("Comparison village context coverage is incomplete");
        }
        return pairs.size();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("comparison")
                .requires(VillageComparisonGallery::hasCommandAccess)
                .then(Commands.literal("build").then(Commands.literal("confirm").executes(context -> {
                    begin(context.getSource().getServer());
                    return info(context);
                })))
                .then(Commands.literal("info").executes(VillageComparisonGallery::info))
                .then(Commands.literal("context")
                        .then(Commands.argument("district", IntegerArgumentType.integer(1,
                                        VillageArchitecture.BiomeDialect.values().length))
                                .executes(VillageComparisonGallery::visitContext)))
                .then(Commands.literal("visit")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, expectedPairCount()))
                                .executes(VillageComparisonGallery::visit)));
    }

    public static int expectedPairCount() {
        return (StructureGalleryPlan.goldMasters().size() + 1)
                * VillageArchitecture.BiomeDialect.values().length;
    }

    private static void begin(MinecraftServer server) {
        if (!isExactWorld(server) || !server.overworld().isFlat()) {
            throw new IllegalStateException("Comparison requires its exact opt-in disposable flat save.");
        }
        if (STATES.containsKey(server)) {
            return;
        }
        ServerLevel level = server.overworld();
        level.getChunk(-2, -2);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -32, -32);
        List<ResolvedPair> pairs = resolvePairs(level, surfaceY);
        List<VillageCourt> courts = resolveCourts(level, surfaceY);
        long signature = signature(pairs) ^ courts.stream()
                .map(VillageCourt::metadata).toList().toString().hashCode();
        BlockState existingMarker = level.getBlockState(signatureMarker(level));
        boolean hasHeader = existingMarker.is(Blocks.DIAMOND_BLOCK) || existingMarker.is(Blocks.LAPIS_BLOCK);
        if (hasHeader && !signatureBitsMatch(level, signature)) {
            throw new IllegalStateException("Comparison catalog changed. Keep this save as history; use a fresh save.");
        }
        BuildState state = new BuildState(pairs, courts, signature);
        if (signatureMatches(level, signature)) {
            for (ResolvedPair pair : pairs) {
                if (!level.getBlockState(pairMarker(level, pair.entry.index())).is(Blocks.EMERALD_BLOCK)) {
                    throw new IllegalStateException("Comparison completion marker has an incomplete pair.");
                }
            }
            for (int i = 0; i < courts.size(); i++) {
                if (!level.getBlockState(pairMarker(level, expectedPairCount() + i + 1)).is(Blocks.EMERALD_BLOCK)) {
                    throw new IllegalStateException("Comparison completion marker has an incomplete village court.");
                }
            }
            state.next = pairs.size();
            state.nextCourt = courts.size();
            state.ready = true;
        } else if (!hasHeader) {
            requireEmptyMarkers(level, pairs.size() + courts.size());
            writeSignature(level, signature, false);
        }
        STATES.put(server, state);
        if (!state.ready) {
            configureWorld(server, surfaceY);
        }
        LOGGER.info("Comparison queued: {} deterministic mod/vanilla pairs in five dialect districts",
                pairs.size());
    }

    private static List<ResolvedPair> resolvePairs(ServerLevel level, int surfaceY) {
        List<Identifier> available = vanillaTemplates(level);
        List<ResolvedPair> result = new ArrayList<>();
        int district = 0;
        for (VillageArchitecture.BiomeDialect dialect : VillageArchitecture.BiomeDialect.values()) {
            List<StructureGalleryPlan.Entry> masters = StructureGalleryPlan.entries().stream()
                    .filter(e -> e.section().equals(StructureGalleryPlan.GOLD_MASTER_MATRIX))
                    .filter(e -> e.dialect() == dialect).toList();
            if (masters.size() != StructureGalleryPlan.goldMasters().size()) {
                throw new IllegalStateException("Missing master in " + dialect.id() + " comparison district");
            }
            for (int local = 0; local <= masters.size(); local++) {
                StructureGalleryPlan.Entry source = local < masters.size() ? masters.get(local) : null;
                VillageArchitecture.BlueprintDescriptor descriptor = source == null ? null
                        : VillageArchitecture.requireBlueprint(source.templateId(), source.templateRevision());
                String role = source == null ? "BANK" : source.type().name();
                List<Identifier> candidates = vanillaCandidates(available, dialect.id(), role);
                if (candidates.isEmpty()) {
                    throw new IllegalStateException("No real vanilla analogue for " + dialect.id() + "/" + role);
                }
                Identifier vanillaId = candidates.get(local % candidates.size());
                StructureTemplate template = level.getStructureManager().get(vanillaId)
                        .orElseThrow(() -> new IllegalStateException("Missing bundled template " + vanillaId));
                Rotation rotation = entranceRotation(template);
                Vec3i size = template.getSize(rotation);
                int modWidth = descriptor == null ? StructureGalleryPlan.STANDALONE_BANK_WIDTH : descriptor.width();
                int modDepth = descriptor == null ? StructureGalleryPlan.STANDALONE_BANK_DEPTH : descriptor.depth();
                int modHeight = descriptor == null ? StructureGalleryPlan.STANDALONE_BANK_HEIGHT : descriptor.height();
                if (Math.max(modWidth + 14, size.getX() + 6) >= HALF_PITCH
                        || Math.max(modDepth + 16, size.getZ() + 8) >= ROW_PITCH
                        || size.getY() <= 0 || size.getY() > 64) {
                    throw new IllegalStateException("Comparison plot envelope exceeded by " + vanillaId);
                }
                int plotX = district * DISTRICT_PITCH + (local % COLUMNS) * PAIR_PITCH;
                int plotZ = (local / COLUMNS) * ROW_PITCH;
                int modX = plotX + (HALF_PITCH - modWidth) / 2;
                int modZ = plotZ + 18;
                int vanillaX = plotX + HALF_PITCH + (HALF_PITCH - size.getX()) / 2;
                int vanillaZ = plotZ + 18;
                BlockPos vanillaOrigin = template.getZeroPositionWithTransform(
                        new BlockPos(vanillaX, vanillaOriginY(surfaceY), vanillaZ),
                        Mirror.NONE, rotation);
                ComparisonEntry entry = new ComparisonEntry(result.size() + 1, dialect.id(), role,
                        source == null ? "standalone_bank" : source.templateId(),
                        source == null ? VillageBankManager.galleryBankStructureVersion()
                                : source.templateRevision(), vanillaId.toString(),
                        analogueDescription(role), surfaceY, plotX, plotZ,
                        modX, modZ, modWidth, modDepth, modHeight,
                        vanillaX, vanillaZ, size.getX(), size.getZ(), size.getY());
                result.add(new ResolvedPair(entry, source, dialect, template, vanillaOrigin, rotation));
            }
            district++;
        }
        if (result.size() != expectedPairCount()) {
            throw new IllegalStateException("Comparison omitted active templates");
        }
        return List.copyOf(result);
    }

    private static List<Identifier> vanillaTemplates(ServerLevel level) {
        return level.getStructureManager().listTemplates()
                .filter(id -> id.getNamespace().equals("minecraft"))
                .filter(id -> id.getPath().startsWith("village/"))
                .sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    private static List<VillageCourt> resolveCourts(ServerLevel level, int surfaceY) {
        List<VillageCourt> courts = new ArrayList<>();
        List<Identifier> available = vanillaTemplates(level);
        int district = 0;
        int rows = (StructureGalleryPlan.goldMasters().size() + 1 + COLUMNS - 1) / COLUMNS;
        for (VillageArchitecture.BiomeDialect dialect : VillageArchitecture.BiomeDialect.values()) {
            int x = district++ * DISTRICT_PITCH;
            int z = rows * ROW_PITCH + 32;
            List<Identifier> homes = vanillaCandidates(available, dialect.id(), "COTTAGE");
            List<Identifier> large = vanillaCandidates(available, dialect.id(), "HOUSE");
            List<Identifier> centers = vanillaCandidates(available, dialect.id(), "MARKET_SQUARE").stream()
                    .filter(id -> id.getPath().startsWith("village/" + dialect.id() + "/town_centers/"))
                    .toList();
            if (homes.size() < 2 || large.isEmpty() || centers.isEmpty()) {
                throw new IllegalStateException("Incomplete vanilla context templates for " + dialect.id());
            }
            List<Identifier> ids = List.of(centers.getFirst(), homes.getFirst(), homes.get(1), large.getFirst());
            int[][] offsets = {{88, 68}, {24, 16}, {152, 16}, {88, 128}};
            List<ReferenceBuilding> buildings = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                Identifier id = ids.get(i);
                StructureTemplate template = level.getStructureManager().get(id).orElseThrow();
                Rotation rotation = entranceRotation(template, i == 1 || i == 2 ? Direction.SOUTH : Direction.NORTH);
                Vec3i size = template.getSize(rotation);
                if (size.getX() > 40 || size.getZ() > 32 || size.getY() > 64) {
                    throw new IllegalStateException("Vanilla context template exceeds its reserved parcel: " + id);
                }
                BlockPos origin = template.getZeroPositionWithTransform(new BlockPos(
                        x + offsets[i][0], vanillaOriginY(surfaceY), z + offsets[i][1]),
                        Mirror.NONE, rotation);
                buildings.add(new ReferenceBuilding(id, template, origin, rotation));
            }
            ContextCourt metadata = new ContextCourt(dialect.id(), x, z, surfaceY,
                    ids.stream().map(Identifier::toString).toList());
            courts.add(new VillageCourt(metadata, List.copyOf(buildings)));
        }
        return List.copyOf(courts);
    }

    private static List<Identifier> vanillaCandidates(List<Identifier> ids, String dialect, String role) {
        String prefix = "village/" + dialect + "/";
        List<String> tokens = switch (role) {
            case "COTTAGE" -> List.of("small_house");
            case "HOUSE" -> List.of("medium_house", "big_house");
            case "INN" -> List.of("butcher", "fisher");
            case "WAREHOUSE" -> List.of("tannery", "shepherd");
            case "GRANARY" -> List.of("farm");
            case "SMITHY" -> List.of("weaponsmith", "tool_smith", "armorer");
            case "MINE_ENTRANCE" -> List.of("mason");
            case "MARKET_SQUARE" -> List.of("meeting_point", "fountain");
            case "GUARD_POST" -> List.of("temple");
            case "EXCHANGE_HALL", "BANK" -> List.of("library", "cartographer");
            default -> throw new IllegalArgumentException("Unmapped comparison role: " + role);
        };
        // Direct folders only: the biome prefix also contains abandoned/zombie variants.
        return ids.stream().filter(id -> id.getPath().startsWith(prefix + "houses/")
                        || id.getPath().startsWith(prefix + "town_centers/"))
                .filter(id -> tokens.stream().anyMatch(token -> id.getPath().contains(token))).toList();
    }

    private static String analogueDescription(String role) {
        return switch (role) {
            case "COTTAGE", "HOUSE" -> "Vanilla dwelling comparison";
            case "SMITHY" -> "Vanilla smithing workplace comparison";
            case "INN" -> "Hospitality/scale analogue; vanilla has no inn";
            case "WAREHOUSE" -> "Storage/workshop analogue; vanilla has no warehouse";
            case "GRANARY" -> "Agricultural analogue; vanilla has no granary";
            case "MINE_ENTRANCE" -> "Stoneworking analogue; vanilla has no village mine";
            case "MARKET_SQUARE" -> "Public meeting-space analogue; vanilla has no market";
            case "GUARD_POST" -> "Civic tower analogue; vanilla has no guard post";
            default -> "Library/cartography analogue; vanilla has no financial building";
        };
    }

    private static Rotation entranceRotation(StructureTemplate template) {
        return entranceRotation(template, Direction.NORTH);
    }

    private static int vanillaOriginY(int surfaceY) {
        // Display the complete NBT above the supported flat grade. Burying y=0 also
        // buried door bottoms and let template air carve misleading perimeter trenches.
        // Do not repair that artifact by changing the vanilla reference's authored cells.
        return surfaceY;
    }

    private static Rotation entranceRotation(StructureTemplate template, Direction direction) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)
                .stream().filter(info -> JigsawBlock.getFrontFacing(info.state()).getAxis().isHorizontal())
                .min(Comparator.comparingInt(info -> info.pos().getY()))
                .map(info -> {
                    Direction front = JigsawBlock.getFrontFacing(info.state());
                    for (Rotation rotation : Rotation.values()) {
                        if (rotation.rotate(front) == direction) {
                            return rotation;
                        }
                    }
                    return Rotation.NONE;
                }).orElse(Rotation.NONE);
    }

    private static void placePair(ServerLevel level, ResolvedPair pair) {
        ComparisonEntry e = pair.entry;
        requireEmptyPlot(level, e);
        BlockPos modOrigin = new BlockPos(e.modX(), e.surfaceY(), e.modZ());
        List<StructureGalleryBlock> blocks = pair.source == null
                ? VillageBankManager.galleryBankBlueprint(modOrigin, pair.dialect)
                : VillageProsperityManager.galleryProjectBlueprint(level, modOrigin,
                        pair.source.type(), pair.source.dialect(), pair.source.character(),
                        pair.source.templateId(), pair.source.templateRevision(), pair.source.paletteId(),
                        pair.source.dressingId(), pair.source.mirrored(), pair.source.visualStage(),
                        pair.source.rotation());
        for (StructureGalleryBlock block : blocks) {
            BlockPos p = block.position();
            if (p.getX() < e.plotX() + 2 || p.getX() >= e.plotX() + HALF_PITCH - 2
                    || p.getZ() < e.plotZ() + 6 || p.getZ() >= e.plotZ() + ROW_PITCH - 2
                    || p.getY() < e.surfaceY() - FOUNDATION_DEPTH) {
                throw new IllegalStateException("Production block leaves its allocated comparison plot at "
                        + p.toShortString());
            }
        }
        setChecked(level, pairMarker(level, e.index()), Blocks.REDSTONE_BLOCK.defaultBlockState());
        prepareGround(level, e.dialect(), e.plotX() + 1, e.plotZ() + 1,
                PAIR_PITCH - 2, ROW_PITCH - 2, e.surfaceY());
        for (StructureGalleryBlock block : blocks) {
            if (!level.getBlockState(block.position()).equals(block.state())) {
                level.setBlock(block.position(), block.state(), Block.UPDATE_ALL);
                if (!level.getBlockState(block.position()).is(block.state().getBlock())) {
                    throw new IllegalStateException("Production comparison placement failed at "
                            + block.position().toShortString());
                }
            }
        }
        VillageProsperityManager.normalizeGalleryConnections(level, blocks);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(pair.rotation).setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        if (!pair.template.placeInWorld(level, pair.vanillaOrigin, pair.vanillaOrigin,
                settings, RandomSource.create(0x544553434f4d50L + e.index()), Block.UPDATE_ALL)) {
            throw new IllegalStateException("Vanilla reference failed to place: " + e.vanillaTemplate());
        }
        for (StructureGalleryBlock block : VillageProsperityManager.finalGalleryAttachmentExpectations(blocks)) {
            StructureGallery.validateAttachmentState(e.index(), block,
                    level.getBlockState(block.position()),
                    level.getBlockState(block.position()).canSurvive(level, block.position()));
        }
        placeLabel(level, new BlockPos(e.plotX() + 8, e.surfaceY(), e.plotZ() + 4),
                "#" + e.index() + " " + e.dialect(), "MOD: " + e.role(), e.modTemplate(), "Use /emerald");
        placeLabel(level, new BlockPos(e.plotX() + HALF_PITCH + 8, e.surfaceY(), e.plotZ() + 4),
                "#" + e.index() + " VANILLA", shortName(e.vanillaTemplate()), "Actual village NBT", "Curated, not worldgen");
        setChecked(level, pairMarker(level, e.index()), Blocks.EMERALD_BLOCK.defaultBlockState());
    }

    private static void requireEmptyPlot(ServerLevel level, ComparisonEntry e) {
        requireEmptyBox(level, e.plotX() + 1, e.plotZ() + 1, PAIR_PITCH - 2, ROW_PITCH - 2,
                e.surfaceY(), Math.max(e.modHeight(), e.vanillaHeight()) + 6);
    }

    private static void requireEmptyBox(ServerLevel level, int minX, int minZ,
            int width, int depth, int surfaceY, int height) {
        for (int x = minX; x < minX + width; x++) {
            for (int z = minZ; z < minZ + depth; z++) {
                level.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                for (int y = Math.max(level.getMinY() + 1, surfaceY - FOUNDATION_DEPTH);
                        y <= surfaceY + height; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState current = level.getBlockState(pos);
                    boolean untouchedGround = y < surfaceY
                            && (current.is(Blocks.DIRT) || current.is(Blocks.GRASS_BLOCK)
                                    || current.is(Blocks.BEDROCK));
                    if (level.getBlockEntity(pos) != null || !level.getFluidState(pos).isEmpty()
                            || (!current.isAir() && !untouchedGround)) {
                        throw new IllegalStateException("Comparison plot is not empty at " + pos.toShortString());
                    }
                }
            }
        }
    }

    private static void prepareGround(ServerLevel level, String dialect, int minX, int minZ,
            int width, int depth, int surfaceY) {
        String biome = dialect.equals("snowy") ? "snowy_plains" : dialect;
        // Vanilla checks block-space volume after quart quantization. Even a 32x32
        // column over full world height exceeds its limit; these 3D slices max out at 29^3.
        for (var slice : VillageComparisonBiomePlan.slices(minX, level.getMinY(), minZ,
                minX + width - 1, level.getMaxY() - 1, minZ + depth - 1)) {
            String command = "fillbiome " + slice.minX() + " " + slice.minY() + " " + slice.minZ()
                    + " " + slice.maxX() + " " + slice.maxY() + " " + slice.maxZ() + " minecraft:" + biome;
            try {
                level.getServer().getCommands().getDispatcher().execute(command,
                        level.getServer().createCommandSourceStack().withSuppressedOutput());
            } catch (CommandSyntaxException exception) {
                throw new IllegalStateException("Comparison biome placement failed", exception);
            }
        }
        for (int x = minX; x < minX + width; x++) {
            for (int z = minZ; z < minZ + depth; z++) {
                Block ground = switch (dialect) {
                    case "desert" -> Blocks.SAND;
                    case "snowy" -> Blocks.SNOW_BLOCK;
                    case "taiga" -> Math.floorMod(x * 31 + z * 17, 7) < 3 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
                    default -> Blocks.GRASS_BLOCK;
                };
                level.setBlock(new BlockPos(x, surfaceY - 1, z), ground.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void placeCourt(ServerLevel level, VillageCourt court, BlockPos marker) {
        ContextCourt m = court.metadata;
        requireEmptyBox(level, m.x(), m.z(), 216, 166, m.surfaceY(), 70);
        setChecked(level, marker, Blocks.REDSTONE_BLOCK.defaultBlockState());
        prepareGround(level, m.dialect(), m.x(), m.z(), 216, 166, m.surfaceY());
        for (ReferenceBuilding building : court.buildings) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(building.rotation)
                    .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                    .addProcessor(JigsawReplacementProcessor.INSTANCE);
            if (!building.template.placeInWorld(level, building.origin, building.origin, settings,
                    RandomSource.create(building.id.toString().hashCode()), Block.UPDATE_ALL)) {
                throw new IllegalStateException("Vanilla context placement failed: " + building.id);
            }
        }
        // A thin connector around, never through, the preserved vanilla template footprints.
        for (int x = 20; x <= 192; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(m.x() + x, m.surfaceY() - 1, m.z() + 112 + dz);
                if (level.getBlockState(p.above()).isAir()) {
                    level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        placeLabel(level, new BlockPos(m.x() + 4, m.surfaceY(), m.z() + 4),
                m.dialect() + " context", "1 center + 3 homes", "Actual vanilla NBT", "Curated arrangement");
        setChecked(level, marker, Blocks.EMERALD_BLOCK.defaultBlockState());
    }

    private static void placeLabel(ServerLevel level, BlockPos pos, String... lines) {
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, 8), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            throw new IllegalStateException("Comparison label could not be placed");
        }
        SignText text = new SignText();
        for (int i = 0; i < Math.min(4, lines.length); i++) {
            text = text.setMessage(i, Component.literal(lines[i]));
        }
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
    }

    private static void configureWorld(MinecraftServer server, int y) {
        ServerLevel level = server.overworld();
        server.getWorldData().setAllowCommands(true);
        server.getWorldData().setGameType(GameType.CREATIVE);
        server.getWorldData().setDifficulty(Difficulty.PEACEFUL);
        level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD,
                new BlockPos(HALF_PITCH, y, -8), 0, 0));
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
    }

    private static boolean isExactWorld(MinecraftServer server) {
        if (!enabled() || server == null) {
            return false;
        }
        Path name = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return name != null && WORLD_DIRECTORY.equals(name.toString());
    }

    private static void requireReady(MinecraftServer server) {
        if (!isReady(server)) {
            throw new IllegalStateException("Exact comparison world is not complete for this catalog");
        }
    }

    private static BlockPos pairMarker(ServerLevel level, int oneBasedIndex) {
        return new BlockPos(oneBasedIndex * 2, level.getMinY() + 1, -64);
    }

    private static BlockPos signatureMarker(ServerLevel level) {
        return new BlockPos(-64, level.getMinY() + 1, -64);
    }

    private static long signature(List<ResolvedPair> pairs) {
        long value = StructureGalleryPlan.layoutSignature() ^ COMPARISON_SCHEMA;
        for (ResolvedPair pair : pairs) {
            String stable = pair.entry.toString() + "/" + pair.rotation + "/" + pair.vanillaOrigin;
            for (int i = 0; i < stable.length(); i++) {
                value = (value ^ stable.charAt(i)) * 0x100000001b3L;
            }
        }
        return value;
    }

    private static void requireEmptyMarkers(ServerLevel level, int count) {
        List<BlockPos> positions = new ArrayList<>();
        for (int bit = 0; bit <= Long.SIZE; bit++) {
            positions.add(signatureMarker(level).offset(bit, 0, 0));
        }
        for (int index = 1; index <= count; index++) {
            positions.add(pairMarker(level, index));
        }
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null || !level.getFluidState(pos).isEmpty()
                    || !(state.isAir() || state.is(Blocks.DIRT) || state.is(Blocks.BEDROCK))) {
                throw new IllegalStateException("Comparison marker area is occupied at " + pos.toShortString());
            }
        }
    }

    private static void writeSignature(ServerLevel level, long signature, boolean complete) {
        BlockPos origin = signatureMarker(level);
        for (int bit = 0; bit < Long.SIZE; bit++) {
            setChecked(level, origin.offset(bit + 1, 0, 0),
                    ((signature & (1L << bit)) == 0 ? Blocks.GOLD_BLOCK : Blocks.EMERALD_BLOCK).defaultBlockState());
        }
        setChecked(level, origin, (complete ? Blocks.DIAMOND_BLOCK : Blocks.LAPIS_BLOCK).defaultBlockState());
    }

    private static boolean signatureMatches(ServerLevel level, long signature) {
        BlockPos origin = signatureMarker(level);
        if (!level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK)) {
            return false;
        }
        return signatureBitsMatch(level, signature);
    }

    private static boolean signatureBitsMatch(ServerLevel level, long signature) {
        BlockPos origin = signatureMarker(level);
        for (int bit = 0; bit < Long.SIZE; bit++) {
            if (!level.getBlockState(origin.offset(bit + 1, 0, 0))
                    .is((signature & (1L << bit)) == 0 ? Blocks.GOLD_BLOCK : Blocks.EMERALD_BLOCK)) {
                return false;
            }
        }
        return true;
    }

    private static void setChecked(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!level.getBlockState(pos).equals(state)) {
            throw new IllegalStateException("Comparison marker write failed at " + pos.toShortString());
        }
    }

    private static void writeIndex(MinecraftServer server, BuildState state) {
        Path index = server.getWorldPath(LevelResource.ROOT).resolve("comparison-index.md");
        StringBuilder out = new StringBuilder("# Curated mod / vanilla village comparison\n\n");
        out.append("Not natural world generation. Vanilla references are unchanged bundled NBT templates.\n\n")
                .append("Signature: ").append(Long.toUnsignedString(state.signature, 16))
                .append("; content revision: ").append(StructureGalleryPlan.GALLERY_CONTENT_REVISION)
                .append("; comparison schema: ").append(COMPARISON_SCHEMA).append(".\n\n")
                .append("| Pair | District | Production master | Vanilla template | Relationship |\n")
                .append("| ---: | --- | --- | --- | --- |\n");
        for (ResolvedPair pair : state.pairs) {
            ComparisonEntry e = pair.entry;
            out.append("| ").append(e.index()).append(" | ").append(e.dialect())
                    .append(" | ").append(e.modTemplate()).append("@").append(e.modRevision())
                    .append(" | ").append(e.vanillaTemplate()).append(" | ")
                    .append(e.relationship()).append(" |\n");
        }
        out.append("\n## Curated vanilla context courts\n\n");
        for (VillageCourt court : state.courts) {
            ContextCourt m = court.metadata;
            out.append("- ").append(m.dialect()).append(" at ").append(m.x()).append(", ")
                    .append(m.surfaceY()).append(", ").append(m.z()).append(": ")
                    .append(String.join(", ", m.templates())).append("\n");
        }
        try {
            if (Files.exists(index)) {
                if (!Files.readString(index).equals(out.toString())) {
                    throw new IllegalStateException("Existing comparison index differs; refusing overwrite");
                }
            } else {
                Files.writeString(index, out.toString(), StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist comparison index", exception);
        }
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        BuildState state = STATES.get(context.getSource().getServer());
        String text = state == null ? "Comparison not started; use /emerald comparison build confirm."
                : state.failure != null ? "Stopped safely: " + state.failure
                : "Curated comparison: " + state.next + "/" + state.pairs.size() + " pairs and "
                        + state.nextCourt + "/" + state.courts.size() + " context courts ready. "
                        + "From the front: mod right, real vanilla template left. "
                        + "Use /emerald comparison visit <index>. "
                        + "This is not natural world generation.";
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int visit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        BuildState state = STATES.get(server);
        int index = IntegerArgumentType.getInteger(context, "index");
        if (state == null || index > state.next) {
            context.getSource().sendFailure(Component.literal("That comparison pair has not finished yet."));
            return 0;
        }
        ComparisonEntry e = state.pairs.get(index - 1).entry;
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        ViewPose pose = clearPreviousRow(state.pairs, e,
                pose(index, "pair", e.plotX() + HALF_PITCH, e.surfaceY(), e.modZ(),
                        PAIR_PITCH - 16, Math.max(e.modHeight(), e.vanillaHeight())),
                e.modZ(), Math.max(e.modHeight(), e.vanillaHeight()));
        player.teleportTo(server.overworld(), pose.x(), pose.y(), pose.z(), Set.of(),
                pose.yaw(), pose.pitch(), true);
        context.getSource().sendSuccess(() -> Component.literal("#" + index + " " + e.dialect()
                + ": " + e.modTemplate() + "@" + e.modRevision() + " / " + e.vanillaTemplate()
                + ". " + e.relationship()), false);
        return 1;
    }

    private static int visitContext(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        if (!isReady(server)) {
            context.getSource().sendFailure(Component.literal("The comparison and village courts are still building."));
            return 0;
        }
        int district = IntegerArgumentType.getInteger(context, "district");
        ContextCourt court = contextCourts(server).get(district - 1);
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(server.overworld(), court.x() + 108.0, court.surfaceY() + 64.0,
                court.z() - 52.0, Set.of(), 0, 25, true);
        context.getSource().sendSuccess(() -> Component.literal(court.dialect()
                + " context: actual vanilla town center + three homes in a curated arrangement, not natural worldgen."),
                false);
        return 1;
    }

    private static ViewPose pose(int index, String view, double x, int ground, double front,
            int width, int height) {
        // Capture is 16:9. Minecraft's configured FOV is vertical, so horizontal fit must
        // account for aspect ratio; treating it as horizontal made small houses tiny.
        double halfVerticalTangent = Math.tan(Math.toRadians(70.0 / 2.0));
        double horizontalDistance = width / (2.0 * halfVerticalTangent * (16.0 / 9.0) * 0.65);
        double verticalDistance = (height + 3.0) / (2.0 * halfVerticalTangent * 0.65);
        double standoff = Math.max(12, Math.max(horizontalDistance, verticalDistance));
        double y = ground + height * 0.5 + standoff * Math.tan(Math.toRadians(18)) - 1.62;
        return new ViewPose(index, view, x, y, front - standoff, 0, 18, 70);
    }

    private static ViewPose clearPreviousRow(List<ResolvedPair> pairs, ComparisonEntry entry,
            ViewPose camera, double front, int height) {
        if (entry.plotZ() == 0) {
            return camera;
        }
        ComparisonEntry previous = pairs.stream().map(ResolvedPair::entry)
                .filter(e -> e.plotX() == entry.plotX() && e.plotZ() == entry.plotZ() - ROW_PITCH)
                .findFirst().orElseThrow();
        double previousRear = Math.max(previous.modZ() + previous.modDepth() + 8,
                previous.vanillaZ() + previous.vanillaDepth()) + 2.0;
        if (camera.z() > previousRear) {
            return camera;
        }
        double distance = front - camera.z();
        double exitFraction = (previousRear - camera.z()) / distance;
        if (exitFraction >= 1.0) {
            throw new IllegalStateException("Comparison camera has no clear inter-row sightline");
        }
        double targetY = entry.surfaceY() + height * 0.5;
        double roofClearance = previous.surfaceY()
                + Math.max(previous.modHeight(), previous.vanillaHeight()) + 4.0;
        // Keep the whole initial sightline above the previous parcel, not merely the eye cell.
        double eyeY = Math.max(camera.y() + 1.62,
                (roofClearance - targetY * exitFraction) / (1.0 - exitFraction));
        float pitch = (float) Math.toDegrees(Math.atan2(eyeY - targetY, distance));
        return new ViewPose(camera.pairIndex(), camera.view(), camera.x(), eyeY - 1.62,
                camera.z(), camera.yaw(), pitch, camera.verticalFovDegrees());
    }

    private static String shortName(String path) {
        return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
    }

    public record ComparisonEntry(int index, String dialect, String role, String modTemplate,
            int modRevision, String vanillaTemplate, String relationship, int surfaceY,
            int plotX, int plotZ, int modX, int modZ, int modWidth, int modDepth, int modHeight,
            int vanillaX, int vanillaZ, int vanillaWidth, int vanillaDepth, int vanillaHeight) {
    }

    public record ViewPose(int pairIndex, String view, double x, double y, double z,
            float yaw, float pitch, int verticalFovDegrees) {
    }

    public record ContextCourt(String dialect, int x, int z, int surfaceY, List<String> templates) {
        public ContextCourt {
            templates = List.copyOf(templates);
        }
    }

    private record ReferenceBuilding(Identifier id, StructureTemplate template,
            BlockPos origin, Rotation rotation) {
    }

    private record VillageCourt(ContextCourt metadata, List<ReferenceBuilding> buildings) {
    }

    private record ResolvedPair(ComparisonEntry entry, StructureGalleryPlan.Entry source,
            VillageArchitecture.BiomeDialect dialect, StructureTemplate template,
            BlockPos vanillaOrigin, Rotation rotation) {
    }

    private static final class BuildState {
        private final List<ResolvedPair> pairs;
        private final List<VillageCourt> courts;
        private final long signature;
        private int next;
        private int nextCourt;
        private boolean ready;
        private String failure;

        private BuildState(List<ResolvedPair> pairs, List<VillageCourt> courts, long signature) {
            this.pairs = pairs;
            this.courts = courts;
            this.signature = signature;
        }
    }
}
