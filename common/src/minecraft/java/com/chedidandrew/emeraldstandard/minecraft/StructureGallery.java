package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.GalleryCameraProjection;
import com.chedidandrew.emeraldstandard.core.GalleryCaptureIsolationPlan;
import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import com.chedidandrew.emeraldstandard.core.StructureGalleryReviewPlan;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in, isolated review gallery for the real production village and Bank blueprints.
 *
 * <p>This utility is intentionally unreachable in ordinary saves. Both the JVM switch and the
 * exact disposable world directory must match before the command tree grants access or writes a
 * single block.</p>
 */
public final class StructureGallery {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "the_emerald_standard_structure_gallery");
    static final String ENABLE_PROPERTY = "the_emerald_standard.structureGallery";
    public static final String AUTO_BUILD_PROPERTY =
            "the_emerald_standard.structureGallery.autoBuild";
    public static final String CAPTURE_PROPERTY =
            "the_emerald_standard.structureGallery.capture";
    static final String WORLD_DIRECTORY = "TES_Blueprint_V2_Gallery";

    // The spawn chunk is already synchronously loaded when commands become available. Querying a
    // distant unloaded heightmap can legally report the dimension floor instead of generating it.
    private static final int SURFACE_PROBE_X = -32;
    private static final int SURFACE_PROBE_Z = -32;
    private static final int HUB_X = StructureGalleryPlan.WIDTH_BLOCKS / 2;
    private static final int HUB_Z = StructureGalleryPlan.reviewHubZ();
    private static final int OVERVIEW_Y_OFFSET = Math.max(
            112,
            Math.max(
                    StructureGalleryPlan.WIDTH_BLOCKS,
                    StructureGalleryPlan.DEPTH_BLOCKS) / 2);
    private static final int COMPLETION_SIGNATURE_BITS = Long.SIZE;
    private static final int EXTERIOR_VIEWPORT_RAY_COLUMNS = 9;
    private static final int EXTERIOR_VIEWPORT_RAY_ROWS = 7;
    private static final double EXTERIOR_VIEWPORT_HORIZONTAL_SAMPLE = 0.97;
    private static final double EXTERIOR_VIEWPORT_VERTICAL_SAMPLE = 0.97;
    private static final double EXTERIOR_VIEWPORT_RAY_STEPS_PER_BLOCK = 6.0;
    private static final int EXTERIOR_VIEWPORT_MAX_FOREIGN_RAYS = 0;
    private static final double[] EXTERIOR_ZOOM_FALLBACK_FOV_DEGREES = {
            65.0, 60.0, 55.0, 50.0
    };
    private static final double[] EXTERIOR_ZOOM_AIM_TANGENT_OFFSETS = {
            0.0,
            -0.75, 0.75,
            -1.50, 1.50,
            -2.25, 2.25,
            -3.00, 3.00,
            -3.75, 3.75,
            -4.50, 4.50,
            -5.25, 5.25,
            -6.00, 6.00
    };
    private static final Comparator<BlockPos> POSITION_ORDER =
            Comparator.comparingInt((BlockPos position) -> position.getY())
                    .thenComparingInt(position -> position.getZ())
                    .thenComparingInt(position -> position.getX());
    private static volatile Map<StructureGalleryReviewPlan.Shot, CapturePose>
            validatedCapturePoses = Map.of();
    private static volatile int stagedIsolatedFixtureIndex = -1;

    private StructureGallery() {
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    /**
     * Moves one local reviewer to an exact, reproducible screenshot pose.
     *
     * <p>This entry point is intentionally guarded more tightly than the interactive gallery
     * commands: automated camera movement additionally requires the capture JVM property, the
     * exact disposable world directory, and a complete gallery marker. It is called only from the
     * client capture harness on the integrated-server thread.</p>
     */
    public static CapturePose teleportForCapture(
            MinecraftServer server,
            UUID playerId,
            StructureGalleryReviewPlan.Shot shot) {
        int galleryIndex = shot.galleryIndex();
        StructureGalleryReviewPlan.View view = shot.view();
        if (!Boolean.getBoolean(CAPTURE_PROPERTY)
                || !enabled()
                || !isExactGalleryWorld(server)) {
            throw new IllegalStateException("Automated gallery capture is not safely enabled");
        }
        ServerLevel level = server.overworld();
        if (!isBuilt(level)) {
            throw new IllegalStateException("Automated gallery capture requires a complete gallery");
        }
        if (galleryIndex < 1 || galleryIndex > StructureGalleryPlan.totalStructureCount()) {
            throw new IllegalArgumentException("Gallery capture index is out of range");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Gallery reviewer is not connected to the local server");
        }

        CapturePose pose = validatedCapturePoses.get(shot);
        if (pose == null) {
            pose = capturePose(level, shot);
        }
        if (GalleryCaptureIsolationPlan.ISOLATED_CLONE_CONTEXT.equals(
                pose.captureContext())) {
            stageIsolatedClone(level, shot);
        }
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                view.isInterior()
                        ? "time set midnight"
                        : "time set noon");
        level.getWeatherData().setClearWeatherTime(1_000_000);
        level.getWeatherData().setRainTime(0);
        level.getWeatherData().setThunderTime(0);
        level.getWeatherData().setRaining(false);
        level.getWeatherData().setThundering(false);
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(
                level,
                pose.x(),
                pose.y(),
                pose.z(),
                Set.of(),
                pose.yawDegrees(),
                pose.pitchDegrees(),
                true);
        return pose;
    }

    /**
     * Resolves the complete requested itinerary before capture creates any evidence files.
     *
     * <p>This deliberately performs no teleport, time, weather, or filesystem mutation. It catches
     * stale doodad signatures and cameras embedded in authored geometry as one atomic preflight,
     * so a catalog change cannot strand a nominal review pass after hundreds of screenshots.</p>
     */
    public static void validateCapturePlan(
            MinecraftServer server,
            List<StructureGalleryReviewPlan.Shot> shots) {
        if (!Boolean.getBoolean(CAPTURE_PROPERTY)
                || !enabled()
                || !isExactGalleryWorld(server)) {
            throw new IllegalStateException("Automated gallery capture is not safely enabled");
        }
        ServerLevel level = server.overworld();
        if (!isBuilt(level)) {
            throw new IllegalStateException("Automated gallery capture requires a complete gallery");
        }
        if (shots.isEmpty()) {
            throw new IllegalArgumentException("Automated gallery capture itinerary is empty");
        }
        validatedCapturePoses = Map.of();
        stagedIsolatedFixtureIndex = -1;
        List<String> failures = new ArrayList<>();
        Map<StructureGalleryReviewPlan.Shot, CapturePose> resolvedPoses = new HashMap<>();
        for (StructureGalleryReviewPlan.Shot shot : shots) {
            try {
                CapturePose pose = capturePose(level, shot);
                BlockPos feet = BlockPos.containing(pose.x(), pose.y() + 0.05, pose.z());
                BlockPos eyes = BlockPos.containing(pose.x(), pose.y() + 1.62, pose.z());
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(eyes).isAir()) {
                    failures.add(shot.sequence() + " " + shot.subject() + " " + shot.view().id()
                            + " camera intersects " + (!level.getBlockState(feet).isAir()
                                    ? feet.toShortString()
                                    : eyes.toShortString()));
                } else {
                    resolvedPoses.put(shot, pose);
                }
            } catch (RuntimeException failure) {
                failures.add(shot.sequence() + " " + shot.subject() + " " + shot.view().id()
                        + ": " + failure.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Gallery capture preflight rejected " + failures.size() + " shot(s): "
                            + String.join("; ", failures));
        }
        validateFragileGalleryAttachments(level, attachmentExpectationFixtures(surfaceY(level)));
        validatedCapturePoses = Map.copyOf(resolvedPoses);
    }

    private static CapturePose capturePose(
            ServerLevel level,
            StructureGalleryReviewPlan.Shot shot) {
        try {
            return capturePoseInGallery(level, shot);
        } catch (NoClearExteriorCameraException crowdedGallery) {
            return captureIsolatedExteriorPose(level, shot, crowdedGallery);
        }
    }

    private static CapturePose capturePoseInGallery(
            ServerLevel level,
            StructureGalleryReviewPlan.Shot shot) {
        int galleryIndex = shot.galleryIndex();
        StructureGalleryReviewPlan.View view = shot.view();
        StructureGalleryPlan.VisitTarget framing =
                StructureGalleryPlan.visitTargets().get(galleryIndex - 1);
        int baseY = surfaceY(level);
        int internalIndex = galleryIndex - 1;
        if (internalIndex >= StructureGalleryPlan.entries().size()) {
            StructureGalleryPlan.BankEntry bank = StructureGalleryPlan.bankEntries().get(
                    internalIndex - StructureGalleryPlan.entries().size());
            if (view == StructureGalleryReviewPlan.View.FRONT
                    || view == StructureGalleryReviewPlan.View.REAR_DOODADS) {
                return captureExteriorPose(
                        level,
                        framing,
                        bankExteriorSubjectBounds(baseY, bank),
                        view == StructureGalleryReviewPlan.View.REAR_DOODADS,
                        true);
            }
            return captureBankInteriorPose(
                    level,
                    framing,
                    baseY,
                    view == StructureGalleryReviewPlan.View.INTERIOR_SECONDARY);
        }

        StructureGalleryPlan.Entry entry = StructureGalleryPlan.entries().get(internalIndex);
        VillageArchitecture.BlueprintDescriptor descriptor = VillageArchitecture.requireBlueprint(
                entry.templateId(), entry.templateRevision());
        long seed = entry.blueprintSignature();
        AuthoredVillageStructures.Blueprint blueprint = AuthoredVillageStructures.plan(
                entry.type(),
                entry.templateId(),
                entry.templateRevision(),
                entry.paletteId(),
                entry.dressingId(),
                entry.character(),
                entry.dialect(),
                seed);
        if (view == StructureGalleryReviewPlan.View.FRONT
                || view == StructureGalleryReviewPlan.View.REAR_DOODADS) {
            boolean rear = view == StructureGalleryReviewPlan.View.REAR_DOODADS;
            return captureExteriorPose(
                    level,
                    framing,
                    authoredExteriorSubjectBounds(baseY, entry, blueprint, rear),
                    rear,
                    false);
        }
        if (view == StructureGalleryReviewPlan.View.DOODAD_DETAIL) {
            return captureDoodadDetailPose(
                    framing,
                    baseY,
                    entry,
                    blueprint,
                    shot.doodadDetail());
        }
        return captureAuthoredInteriorPose(
                level,
                baseY,
                entry,
                descriptor,
                blueprint,
                view == StructureGalleryReviewPlan.View.INTERIOR_SECONDARY);
    }

    /**
     * Last-resort capture-only staging for a fixture whose normal gallery row admits no clean
     * exterior viewport. The exact production-resolved block states are translated, without
     * rotation or material substitution, to one reusable flat-world annex. Every ordinary fit,
     * collision, line-of-sight, and zero-foreign-ray check is then rerun against the clone.
     */
    private static CapturePose captureIsolatedExteriorPose(
            ServerLevel level,
            StructureGalleryReviewPlan.Shot shot,
            NoClearExteriorCameraException crowdedGallery) {
        IsolatedCaptureFixture isolated = stageIsolatedClone(level, shot);
        try {
            return captureExteriorPose(
                            level,
                            isolated.framing(),
                            isolated.subject(),
                            isolated.rear(),
                            isolated.standaloneBank())
                    .withCaptureContext(
                            GalleryCaptureIsolationPlan.ISOLATED_CLONE_CONTEXT);
        } catch (NoClearExteriorCameraException isolatedFailure) {
            isolatedFailure.addSuppressed(crowdedGallery);
            throw isolatedFailure;
        }
    }

    private static IsolatedCaptureFixture stageIsolatedClone(
            ServerLevel level, StructureGalleryReviewPlan.Shot shot) {
        StructureGalleryReviewPlan.View view = shot.view();
        if (view != StructureGalleryReviewPlan.View.FRONT
                && view != StructureGalleryReviewPlan.View.REAR_DOODADS) {
            throw new IllegalStateException(
                    "Only exterior gallery evidence may use the isolated review annex");
        }
        int baseY = surfaceY(level);
        int internalIndex = shot.galleryIndex() - 1;
        StructureGalleryPlan.VisitTarget sourceFraming =
                StructureGalleryPlan.visitTargets().get(internalIndex);
        SubjectBounds sourceSubject;
        boolean standaloneBank = internalIndex >= StructureGalleryPlan.entries().size();
        if (standaloneBank) {
            StructureGalleryPlan.BankEntry bank = StructureGalleryPlan.bankEntries().get(
                    internalIndex - StructureGalleryPlan.entries().size());
            sourceSubject = bankExteriorSubjectBounds(baseY, bank);
        } else {
            StructureGalleryPlan.Entry entry = StructureGalleryPlan.entries().get(internalIndex);
            AuthoredVillageStructures.Blueprint blueprint = AuthoredVillageStructures.plan(
                    entry.type(),
                    entry.templateId(),
                    entry.templateRevision(),
                    entry.paletteId(),
                    entry.dressingId(),
                    entry.character(),
                    entry.dialect(),
                    entry.blueprintSignature());
            sourceSubject = authoredExteriorSubjectBounds(
                    baseY,
                    entry,
                    blueprint,
                    view == StructureGalleryReviewPlan.View.REAR_DOODADS);
        }

        GalleryCaptureIsolationPlan.Placement placement =
                GalleryCaptureIsolationPlan.placementFor(
                        sourceFraming.originX(), sourceFraming.originZ());
        if (stagedIsolatedFixtureIndex != internalIndex
                || !isolatedCloneMatches(
                        level, sourceFraming, placement, baseY)) {
            resetIsolatedReviewPad(level, baseY, placement);
            ResolvedFixture cloneFixture = resolveFixtureAt(
                    level,
                    baseY,
                    internalIndex,
                    new BlockPos(
                            placement.annexOriginX(),
                            baseY,
                            placement.annexOriginZ()));
            placeIsolatedClone(level, cloneFixture);
            freezeIsolatedCloneToSourceState(
                    level, sourceFraming, placement, baseY);
            validateFragileGalleryAttachments(level, List.of(cloneFixture));
            if (!isolatedCloneMatches(
                    level, sourceFraming, placement, baseY)) {
                throw new IllegalStateException(
                        "Isolated review annex did not reproduce the complete source fixture state");
            }
            stagedIsolatedFixtureIndex = internalIndex;
        }

        StructureGalleryPlan.VisitTarget isolatedFraming = new StructureGalleryPlan.VisitTarget(
                sourceFraming.index(),
                placement.annexOriginX(),
                placement.annexOriginZ(),
                sourceFraming.width(),
                sourceFraming.depth(),
                sourceFraming.height(),
                sourceFraming.rotation());
        LOGGER.info(
                "Gallery fixture {} staged as exact isolated clone at ({}, {}) after its normal row admitted no clean viewport",
                shot.galleryIndex(),
                placement.annexOriginX(),
                placement.annexOriginZ());
        return new IsolatedCaptureFixture(
                isolatedFraming,
                sourceSubject.translated(placement.translationX(), placement.translationZ()),
                view == StructureGalleryReviewPlan.View.REAR_DOODADS,
                standaloneBank);
    }

    private static void resetIsolatedReviewPad(
            ServerLevel level,
            int baseY,
            GalleryCaptureIsolationPlan.Placement placement) {
        int radius = GalleryCaptureIsolationPlan.clearRadiusBlocks();
        int minimumX = placement.annexOriginX() - radius;
        int maximumX = placement.annexOriginX() + radius;
        int minimumZ = placement.annexOriginZ() - radius;
        int maximumZ = placement.annexOriginZ() + radius;
        loadChunks(level, minimumX, maximumX, minimumZ, maximumZ);

        int controlX = maximumX + 16;
        int controlZ = maximumZ + 16;
        level.getChunk(Math.floorDiv(controlX, 16), Math.floorDiv(controlZ, 16));
        int baselineDepth = GalleryCaptureIsolationPlan.baselineDepthBlocks();
        List<BlockState> baseline = new ArrayList<>(baselineDepth);
        for (int offsetY = -baselineDepth; offsetY < 0; offsetY++) {
            baseline.add(level.getBlockState(new BlockPos(
                    controlX, baseY + offsetY, controlZ)));
        }

        int maximumY = Math.min(
                level.getMaxY() - 1,
                baseY + GalleryCaptureIsolationPlan.clearHeightBlocks());
        // This entire opt-in annex is being replaced, not demolished through gameplay. Clear
        // its known fixture without neighbor-shape cascades: bottom-up UPDATE_ALL used to drop
        // old rails/shrubs when their supports vanished, polluting the next subject's screenshot.
        // The new clone still uses UPDATE_ALL and must pass the normal runtime attachment audit.
        int resetFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int y = maximumY; y >= baseY; y--) {
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (!level.getBlockState(position).isAir()) {
                        level.setBlock(position, Blocks.AIR.defaultBlockState(), resetFlags);
                    }
                }
            }
        }
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                for (int offsetY = -baselineDepth; offsetY < 0; offsetY++) {
                    BlockPos position = new BlockPos(x, baseY + offsetY, z);
                    BlockState expected = baseline.get(offsetY + baselineDepth);
                    if (!level.getBlockState(position).equals(expected)) {
                        level.setBlock(position, expected, resetFlags);
                    }
                }
            }
        }
    }

    private static void placeIsolatedClone(
            ServerLevel level,
            ResolvedFixture clone) {
        for (StructureGalleryBlock block : clone.blocks()) {
            if (!level.getBlockState(block.position()).equals(block.state())) {
                level.setBlock(block.position(), block.state(), Block.UPDATE_ALL);
            }
        }
        VillageProsperityManager.normalizeGalleryConnections(level, clone.blocks());
        validateFragileGalleryAttachments(level, List.of(clone));
    }

    /**
     * Freezes the annex to the already production-built source fixture after resolving and placing
     * the same production blueprint there. This preserves legitimate post-placement state changes
     * (for example, a covered dirt path settling to dirt), exact connected-block orientations, and
     * every authored air cavity. It copies the entire active fixture frame without deleting,
     * replacing, or hiding any part of that fixture; the equality pass below remains the final
     * fail-closed proof.
     */
    private static void freezeIsolatedCloneToSourceState(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget sourceFraming,
            GalleryCaptureIsolationPlan.Placement placement,
            int baseY) {
        int minimumX = sourceFraming.originX() + sourceFraming.minimumFrameX();
        int maximumX = sourceFraming.originX() + sourceFraming.maximumFrameX();
        int minimumZ = sourceFraming.originZ() + sourceFraming.minimumFrameZ();
        int maximumZ = sourceFraming.originZ() + sourceFraming.maximumFrameZ();
        int maximumY = Math.min(
                level.getMaxY() - 1,
                baseY + GalleryCaptureIsolationPlan.clearHeightBlocks());
        for (int sourceX = minimumX; sourceX <= maximumX; sourceX++) {
            for (int sourceZ = minimumZ; sourceZ <= maximumZ; sourceZ++) {
                for (int y = baseY; y <= maximumY; y++) {
                    BlockPos sourcePosition = new BlockPos(sourceX, y, sourceZ);
                    BlockPos clonePosition = new BlockPos(
                            placement.translateX(sourceX),
                            y,
                            placement.translateZ(sourceZ));
                    BlockState sourceState = level.getBlockState(sourcePosition);
                    if (!level.getBlockState(clonePosition).equals(sourceState)) {
                        level.setBlock(
                                clonePosition,
                                sourceState,
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static boolean isolatedCloneMatches(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget sourceFraming,
            GalleryCaptureIsolationPlan.Placement placement,
            int baseY) {
        int minimumX = sourceFraming.originX() + sourceFraming.minimumFrameX();
        int maximumX = sourceFraming.originX() + sourceFraming.maximumFrameX();
        int minimumZ = sourceFraming.originZ() + sourceFraming.minimumFrameZ();
        int maximumZ = sourceFraming.originZ() + sourceFraming.maximumFrameZ();
        // The generated fixture begins at baseY. The flat-world support below it is reset from a
        // pristine control column, but is intentionally not part of exact fixture equality:
        // Minecraft may age covered grass into dirt after the original gallery was built. That
        // environmental tick is neither an authored block nor visible evidence, and requiring it
        // would make a freshly staged exact blueprint nondeterministically differ from the older
        // source plot. Everything at/above the authored origin—including air cavities—is still
        // compared state-for-state across the complete conservative fixture frame.
        int minimumY = baseY;
        int maximumY = Math.min(
                level.getMaxY() - 1,
                baseY + GalleryCaptureIsolationPlan.clearHeightBlocks());
        for (int sourceX = minimumX; sourceX <= maximumX; sourceX++) {
            for (int sourceZ = minimumZ; sourceZ <= maximumZ; sourceZ++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    BlockPos sourcePosition = new BlockPos(sourceX, y, sourceZ);
                    BlockPos clonePosition = new BlockPos(
                            placement.translateX(sourceX),
                            y,
                            placement.translateZ(sourceZ));
                    BlockState sourceState = level.getBlockState(sourcePosition);
                    BlockState cloneState = level.getBlockState(clonePosition);
                    if (!sourceState.equals(cloneState)) {
                        LOGGER.warn(
                                "Isolated gallery clone mismatch: source {}={} clone {}={} relative=({}, {}, {})",
                                sourcePosition.toShortString(),
                                sourceState,
                                clonePosition.toShortString(),
                                cloneState,
                                sourceX - sourceFraming.originX(),
                                y - baseY,
                                sourceZ - sourceFraming.originZ());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void loadChunks(
            ServerLevel level,
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ) {
        for (int chunkX = Math.floorDiv(minimumX, 16);
                chunkX <= Math.floorDiv(maximumX, 16);
                chunkX++) {
            for (int chunkZ = Math.floorDiv(minimumZ, 16);
                    chunkZ <= Math.floorDiv(maximumZ, 16);
                    chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static CapturePose captureAuthoredInteriorPose(
            ServerLevel level,
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            AuthoredVillageStructures.Blueprint blueprint,
            boolean secondary) {
        verifyPhotographyRouteSeparation();
        List<BlockPos> authoredSamples = blueprint.metadata().interiorSamples().stream()
                .sorted(POSITION_ORDER)
                .toList();
        LinkedHashSet<BlockPos> reviewPositions = new LinkedHashSet<>();
        if (blueprint.metadata().entranceInside() != null) {
            reviewPositions.add(blueprint.metadata().entranceInside());
        }
        authoredSamples.forEach(reviewPositions::add);
        blueprint.metadata().accessTargets().stream()
                .sorted(POSITION_ORDER)
                .forEach(reviewPositions::add);
        if (reviewPositions.isEmpty()) {
            reviewPositions.add(new BlockPos(
                    descriptor.width() / 2,
                    1,
                    descriptor.depth() / 2));
        }
        List<BlockPos> candidates = List.copyOf(reviewPositions);
        // entranceInside is a circulation invariant, not a good photography position. Prefer a
        // hand-authored room sample set back from the doorway; this turns formerly corridor-only
        // evidence into a readable view across the actual occupied room.
        BlockPos entrance = blueprint.metadata().entranceInside();
        BlockPos primary = selectPrimaryInterior(entrance, authoredSamples, candidates);
        BlockPos preferredLocal = secondary
                ? selectSecondaryInterior(
                        primary,
                        authoredSamples.size() > 1 ? authoredSamples : candidates)
                : primary;

        InteriorReviewZone reviewZone = authoredInteriorReviewZone(entry.templateId(), secondary);
        if (reviewZone != null) {
            preferredLocal = reviewZone.center();
        }
        List<BlockPos> clearFloor = clearInteriorFloor(
                level, baseY, entry, descriptor, preferredLocal.getY());
        List<BlockPos> routeFloor = reviewZone == null ? clearFloor : traversablePhotographyFloor(
                level, baseY, entry, descriptor, preferredLocal.getY());
        List<BlockPos> reachableFloor = reachableInteriorFloor(
                routeFloor, preferredLocal, entrance, blueprint.metadata().verticalAccess()).stream()
                .filter(clearFloor::contains)
                .toList();
        if (reviewZone != null) {
            // Restrict composition, never access: first flood the actual entrance-connected
            // floor, then photograph the named occupied room instead of its long access lane.
            reachableFloor = reachableFloor.stream().filter(reviewZone::contains).toList();
        }
        if (reachableFloor.isEmpty()) {
            throw new IllegalStateException(
                    "No supported two-block-clear camera cell for " + entry.templateId()
                            + " interior " + (secondary ? "secondary" : "primary")
                            + " zone=" + reviewZone + " all-clear=" + clearFloor
                            + " route-floor=" + routeFloor);
        }

        List<BlockPos> roleTargets = authoredRoleTargets(blueprint, candidates);
        if (reviewZone != null) {
            roleTargets = roleTargets.stream().filter(reviewZone::contains).toList();
        }
        if (entry.templateId().equals("guard_blockhouse_03")) {
            // The ladder's upper landing is a valid access target, but aiming at it turns the
            // compact room photograph into a ceiling crop. Keep this evidence view on the
            // occupied floor; the ladder remains visible in the background of a level room view.
            int occupiedFloorY = preferredLocal.getY();
            roleTargets = roleTargets.stream()
                    .filter(target -> target.getY() <= occupiedFloorY + 1)
                    .toList();
        }
        Map<BlockPos, Integer> visibleFloorCounts = new HashMap<>();
        for (BlockPos cameraLocal : reachableFloor) {
            visibleFloorCounts.put(
                    cameraLocal,
                    visibleInteriorFloorCount(
                            level,
                            baseY,
                            entry,
                            descriptor,
                            cameraLocal,
                            reachableFloor));
        }
        BlockPos cameraPreference = preferredLocal;
        if (secondary && preferredLocal.equals(primary)) {
            // A one-sample landmark still deserves a genuinely different evidence frame. Pick
            // the far end of its reachable room as the secondary zone rather than replaying the
            // broadest primary camera verbatim.
            cameraPreference = reachableFloor.stream()
                    .max(Comparator
                            .comparingDouble((BlockPos candidate) -> spatialDistanceSquared(
                                    primary, candidate))
                            .thenComparing(POSITION_ORDER))
                    .orElse(preferredLocal);
        }
        final BlockPos cameraPreferenceLocal = cameraPreference;
        Comparator<BlockPos> cameraPriority;
        if (secondary) {
            // Zone distinction outranks maximum room coverage for the second frame. Visibility
            // remains the next criterion and every candidate still has to pass the exact
            // reachability and sightline gates below.
            cameraPriority = Comparator
                    .comparingDouble((BlockPos candidate) -> spatialDistanceSquared(
                            cameraPreferenceLocal, candidate))
                    .thenComparing(Comparator
                            .comparingInt((BlockPos candidate) -> visibleFloorCounts.get(candidate))
                            .reversed())
                    .thenComparing(POSITION_ORDER);
        } else {
            cameraPriority = Comparator
                    .comparingInt((BlockPos candidate) -> visibleFloorCounts.get(candidate))
                    .reversed()
                    .thenComparingDouble(candidate -> spatialDistanceSquared(
                            cameraPreferenceLocal, candidate))
                    .thenComparing(POSITION_ORDER);
        }
        List<BlockPos> orderedCameras = reachableFloor.stream()
                .sorted(cameraPriority)
                .toList();
        for (BlockPos cameraLocal : orderedCameras) {
            BlockPos aimLocal = bestVisibleInteriorTarget(
                    level,
                    baseY,
                    entry,
                    descriptor,
                    cameraLocal,
                    roleTargets,
                    reachableFloor);
            if (aimLocal == null) {
                continue;
            }
            return lookAtLocal(
                    baseY, entry, descriptor, cameraLocal, aimLocal, 1.05);
        }
        throw new IllegalStateException(
                "No useful clear sightline for " + entry.templateId() + " interior "
                        + (secondary ? "secondary" : "primary"));
    }

    /**
     * Narrow photography zones grounded in the authored plan. These do not add, remove or move
     * any world block. A zone must still contain supported, entrance-connected clear floor and
     * an unobstructed useful sightline; failure rejects the evidence rather than choosing a hall.
     */
    private static InteriorReviewZone authoredInteriorReviewZone(String templateId, boolean secondary) {
        return switch (templateId) {
            case "house_hall_04" -> new InteriorReviewZone(1, 9, 6, 9, 1);
            case "warehouse_wharf_04" -> new InteriorReviewZone(1, 4, 3, 11, 1);
            case "mine_headframe_01" -> secondary
                    ? new InteriorReviewZone(11, 13, 9, 12, 1)
                    : new InteriorReviewZone(1, 4, 9, 12, 1);
            case "mine_drift_04" -> new InteriorReviewZone(1, 3, 6, 11, 1);
            case "guard_citadel_05" -> secondary
                    ? new InteriorReviewZone(16, 19, 6, 12, 1)
                    : new InteriorReviewZone(1, 4, 6, 12, 1);
            default -> null;
        };
    }

    private record InteriorReviewZone(int minX, int maxX, int minZ, int maxZ, int floorY) {
        BlockPos center() {
            return new BlockPos((minX + maxX) / 2, floorY, (minZ + maxZ) / 2);
        }

        boolean contains(BlockPos position) {
            return position.getX() >= minX && position.getX() <= maxX
                    && position.getZ() >= minZ && position.getZ() <= maxZ
                    && position.getY() >= floorY && position.getY() <= floorY + 1;
        }
    }

    /**
     * Derives screenshot composition from blocks actually authored for this visual stage. Front
     * views retain the shell, eaves and porch but omit long approach trails and isolated yard
     * doodads; rear views include every raised authored feature while ignoring flat path cells.
     * The conservative {@link StructureGalleryPlan.VisitTarget} remains authoritative for fixture
     * separation and camera collision, but never inflates visual occupancy with empty padding.
     */
    private static SubjectBounds authoredExteriorSubjectBounds(
            int baseY,
            StructureGalleryPlan.Entry entry,
            AuthoredVillageStructures.Blueprint blueprint,
            boolean rear) {
        List<AuthoredVillageStructures.Cell> cells = new ArrayList<>(blueprint.base());
        if (entry.visualStage() >= 1) {
            cells.addAll(blueprint.stageOne());
        }
        if (entry.visualStage() >= 2) {
            cells.addAll(blueprint.stageTwo());
        }
        List<BlockPos> structuralCore = new ArrayList<>(cells.size());
        List<BlockPos> raisedScene = new ArrayList<>(cells.size());
        List<BlockPos> doodadCluster = new ArrayList<>();
        List<BlockPos> activeFixtureBlocks = new ArrayList<>(cells.size());
        for (AuthoredVillageStructures.Cell cell : cells) {
            if (cell.state().isAir()) {
                continue;
            }
            boolean structuralPhase = switch (cell.phase()) {
                case FOUNDATION, FRAME, SHELL, ROOF, OPENING -> true;
                case FIXTURE, DECOR -> false;
            };
            boolean coreCell = structuralPhase
                    && cell.x() >= -1
                    && cell.x() <= blueprint.width()
                    && cell.z() >= -2
                    && cell.z() <= blueprint.depth();
            BlockPos position = transformedSubjectPosition(baseY, entry, blueprint, cell);
            activeFixtureBlocks.add(position);
            if (coreCell) {
                structuralCore.add(position);
            }
            if (rear && cell.y() > 0) {
                // Ground-only trails can run for many blocks without adding a reviewable subject.
                // Every raised block must still fit, while the non-core subset independently
                // proves that the rear doodad cluster remains visible.
                raisedScene.add(position);
                if (!coreCell) {
                    doodadCluster.add(position);
                }
            }
        }
        if (rear) {
            return subjectBounds(
                    raisedScene,
                    structuralCore,
                    raisedScene,
                    doodadCluster,
                    activeFixtureBlocks,
                    baseY,
                    entry.templateId(),
                    true);
        }
        return subjectBounds(
                structuralCore,
                structuralCore,
                structuralCore,
                List.of(),
                activeFixtureBlocks,
                baseY,
                entry.templateId(),
                false);
    }

    private static SubjectBounds bankExteriorSubjectBounds(
            int baseY, StructureGalleryPlan.BankEntry entry) {
        BlockPos origin = new BlockPos(entry.originX(), baseY, entry.originZ());
        List<BlockPos> occupied = VillageBankManager.galleryBankBlueprint(
                        origin, entry.dialect()).stream()
                .filter(block -> !block.state().isAir())
                .map(StructureGalleryBlock::position)
                .toList();
        return subjectBounds(
                occupied,
                occupied,
                occupied,
                List.of(),
                occupied,
                baseY,
                "bank-" + entry.dialect().id(),
                false);
    }

    private static BlockPos transformedSubjectPosition(
            int baseY,
            StructureGalleryPlan.Entry entry,
            AuthoredVillageStructures.Blueprint blueprint,
            AuthoredVillageStructures.Cell cell) {
        int x = entry.mirrored()
                ? blueprint.width() - 1 - cell.x()
                : cell.x();
        BlockPos transformed = rotateSubjectCell(
                x,
                cell.y(),
                cell.z(),
                blueprint.width(),
                blueprint.depth(),
                entry.rotation());
        return new BlockPos(
                entry.originX() + transformed.getX(),
                baseY + transformed.getY(),
                entry.originZ() + transformed.getZ());
    }

    private static BlockPos rotateSubjectCell(
            int x, int y, int z, int width, int depth, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new BlockPos(depth - 1 - z, y, x);
            case 2 -> new BlockPos(width - 1 - x, y, depth - 1 - z);
            case 3 -> new BlockPos(z, y, width - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    private static SubjectBounds subjectBounds(
            List<BlockPos> envelope,
            List<BlockPos> structuralCore,
            List<BlockPos> raisedScene,
            List<BlockPos> doodadCluster,
            List<BlockPos> activeFixtureBlocks,
            int baseY,
            String subjectId,
            boolean allowInwardSearch) {
        if (envelope.isEmpty() || structuralCore.isEmpty() || raisedScene.isEmpty()) {
            throw new IllegalStateException("No occupied exterior bounds for " + subjectId);
        }
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = baseY;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (BlockPos position : envelope) {
            minimumX = Math.min(minimumX, position.getX());
            maximumX = Math.max(maximumX, position.getX());
            maximumY = Math.max(maximumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
            maximumZ = Math.max(maximumZ, position.getZ());
        }
        return new SubjectBounds(
                minimumX,
                maximumX + 1.0,
                baseY,
                maximumY + 1.0,
                minimumZ,
                maximumZ + 1.0,
                projectionPoints(structuralCore),
                projectionPoints(raisedScene),
                projectionPoints(doodadCluster),
                Set.copyOf(activeFixtureBlocks),
                allowInwardSearch);
    }

    private static List<GalleryCameraProjection.Point> projectionPoints(
            List<BlockPos> blocks) {
        return blocks.stream()
                .map(position -> new GalleryCameraProjection.Point(
                        position.getX(), position.getY(), position.getZ()))
                .toList();
    }

    /**
     * Uses the authored shell rather than the intentionally conservative interactive-gallery
     * envelope. At a normal 70-degree FOV this places the visible building at roughly 60 percent
     * of the vertical frame, while leaving the side or rear yard close enough to review props.
     */
    private static CapturePose captureExteriorPose(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget framing,
            SubjectBounds subject,
            boolean rear,
            boolean standaloneBank) {
        double centerX = subject.centerX();
        double centerZ = subject.centerZ();
        double visibleWidth = framing.rotation() % 2 == 0
                ? subject.width()
                : subject.depth();
        double visibleDepth = framing.rotation() % 2 == 0
                ? subject.depth()
                : subject.width();
        double standoff = Math.max(
                7.0,
                Math.max(visibleWidth * 0.68, subject.height() * 1.15));
        double centerDistance = visibleDepth / 2.0 + standoff;
        double direction = rear ? -1.0 : 1.0;
        // A restrained three-quarter view reveals depth and avoids putting a windmill sail,
        // portcullis, or central loading beam directly between the reviewer and the facade.
        double tangent = standaloneBank ? 0.0 : Math.min(3.5, visibleWidth * 0.14);
        if (rear) {
            tangent = -tangent;
        }
        double cameraX = centerX;
        double cameraZ = centerZ;
        switch (framing.rotation()) {
            case 0 -> {
                cameraX += tangent;
                cameraZ -= centerDistance * direction;
            }
            case 1 -> {
                cameraX += centerDistance * direction;
                cameraZ += tangent;
            }
            case 2 -> {
                cameraX -= tangent;
                cameraZ += centerDistance * direction;
            }
            case 3 -> {
                cameraX -= centerDistance * direction;
                cameraZ -= tangent;
            }
            default -> throw new IllegalStateException("Invalid gallery camera rotation");
        }
        double targetY = subject.minimumY() + subject.height() * (rear ? 0.40 : 0.46);
        double cameraY = targetY - 1.62 + (rear ? 0.35 : 0.55);
        return clearExteriorPose(
                level,
                framing,
                subject,
                cameraX,
                cameraY,
                cameraZ,
                centerX,
                targetY,
                centerZ);
    }

    private static CapturePose captureBankInteriorPose(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget framing,
            int baseY,
            boolean secondary) {
        int preferredX = secondary ? 3 : 4;
        int preferredZ = secondary ? 5 : 3;
        int targetX = secondary ? 1 : framing.width() / 2;
        int targetZ = secondary ? framing.depth() - 4 : framing.depth() - 3;
        List<BlockPos> candidates = new ArrayList<>();
        int minimumZ = secondary ? 3 : 1;
        int maximumZ = secondary ? framing.depth() - 2 : framing.depth() - 4;
        for (int x = 1; x < framing.width() - 1; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                BlockPos camera = new BlockPos(
                        framing.originX() + x,
                        baseY + 1,
                        framing.originZ() + z);
                if (supportedCameraVolumeClear(level, camera)) {
                    candidates.add(new BlockPos(x, 1, z));
                }
            }
        }
        BlockPos preferred = new BlockPos(preferredX, 1, preferredZ);
        BlockPos target = new BlockPos(targetX, 1, targetZ);
        List<BlockPos> reachableCandidates = reachableInteriorFloor(
                candidates,
                preferred,
                secondary ? null : new BlockPos(framing.width() / 2, 1, 1),
                List.of());
        Map<BlockPos, Integer> visibleFloorCounts = new HashMap<>();
        for (BlockPos candidate : reachableCandidates) {
            BlockPos cameraWorld = new BlockPos(
                    framing.originX() + candidate.getX(),
                    baseY + candidate.getY(),
                    framing.originZ() + candidate.getZ());
            int visible = 0;
            for (BlockPos floor : reachableCandidates) {
                if (horizontalDistanceSquared(candidate, floor) > 0.0
                        && hasClearInteriorSight(
                                level,
                                cameraWorld,
                                new BlockPos(
                                        framing.originX() + floor.getX(),
                                        baseY + floor.getY(),
                                        framing.originZ() + floor.getZ()))) {
                    visible++;
                }
            }
            visibleFloorCounts.put(candidate, visible);
        }
        if (secondary) {
            // Photograph the compact vault diagonally through its two-wide walk-in opening rather
            // than from the teller-side grille. The camera stays in the verified connected aisle
            // immediately before the frame and uses an east-side shoulder whose leftward rays
            // pass through x=2..3. The near left jamb remains an iron zoning cue while the offset
            // chest, ledger, and lamp are jointly readable deeper in the room.
            BlockPos threshold = new BlockPos(preferredX, 1, targetZ - 2);
            if (!reachableCandidates.contains(threshold)) {
                throw new IllegalStateException(
                        "Bank secure review shoulder is not reachable from clear floor: "
                                + threshold);
            }
            BlockPos thresholdWorld = new BlockPos(
                    framing.originX() + threshold.getX(),
                    baseY + threshold.getY(),
                    framing.originZ() + threshold.getZ());
            double cameraX = thresholdWorld.getX() + 0.70;
            double cameraY = thresholdWorld.getY();
            double cameraZ = thresholdWorld.getZ() + 0.5;
            double focusX = framing.originX() + targetX + 0.5;
            double focusY = baseY + 2.20;
            double focusZ = framing.originZ() + targetZ + 1.0;
            double chestTopX = framing.originX() + 1.5;
            double chestTopY = baseY + 2.05;
            double chestTopZ = framing.originZ() + targetZ + 0.5;
            double ledgerLampX = framing.originX() + 1.5;
            double ledgerLampY = baseY + 2.35;
            double ledgerLampZ = framing.originZ() + targetZ + 1.5;
            double frameCueX = framing.originX() + 1.5;
            double frameCueY = baseY + 2.50;
            double frameCueZ = framing.originZ() + targetZ - 0.5;
            List<String> blockedRays = new ArrayList<>();
            if (!hasClearInteriorEyeRay(
                    level,
                    cameraX,
                    cameraY + 1.62,
                    cameraZ,
                    focusX,
                    focusY,
                    focusZ)) {
                blockedRays.add("composition-focus");
            }
            if (!hasClearInteriorEyeRay(
                    level,
                    cameraX,
                    cameraY + 1.62,
                    cameraZ,
                    chestTopX,
                    chestTopY,
                    chestTopZ)) {
                blockedRays.add("ender-chest");
            }
            if (!hasClearInteriorEyeRay(
                    level,
                    cameraX,
                    cameraY + 1.62,
                    cameraZ,
                    ledgerLampX,
                    ledgerLampY,
                    ledgerLampZ)) {
                blockedRays.add("ledger-lamp");
            }
            if (!hasClearInteriorEyeRay(
                    level,
                    cameraX,
                    cameraY + 1.62,
                    cameraZ,
                    frameCueX,
                    frameCueY,
                    frameCueZ)) {
                blockedRays.add("iron-frame");
            }
            if (!blockedRays.isEmpty()) {
                throw new IllegalStateException(
                        "Bank secure review shoulder cannot jointly see the v4 frame, chest, "
                                + "ledger, and lamp; blocked rays=" + blockedRays);
            }
            return lookAt(
                    cameraX,
                    cameraY,
                    cameraZ,
                    focusX,
                    focusY,
                    focusZ);
        }
        return reachableCandidates.stream()
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> visibleFloorCounts.get(candidate))
                        .reversed()
                        .thenComparingDouble(candidate -> spatialDistanceSquared(
                                preferred, candidate))
                        .thenComparing(POSITION_ORDER))
                .filter(candidate -> horizontalDistanceSquared(candidate, target) >= 6.25)
                .filter(candidate -> hasClearInteriorSight(
                        level,
                        new BlockPos(
                                framing.originX() + candidate.getX(),
                                baseY + candidate.getY(),
                                framing.originZ() + candidate.getZ()),
                        new BlockPos(
                                framing.originX() + target.getX(),
                                baseY + target.getY(),
                                framing.originZ() + target.getZ()),
                        secondary))
                .findFirst()
                .map(candidate -> lookAt(
                        framing.originX() + candidate.getX() + 0.5,
                        baseY + candidate.getY(),
                        framing.originZ() + candidate.getZ() + 0.5,
                        framing.originX() + target.getX() + 0.5,
                        baseY + target.getY() + 0.85,
                        framing.originZ() + target.getZ() + 0.5))
                .orElseThrow(() -> new IllegalStateException(
                        "No supported two-block-clear Bank "
                                + (secondary ? "secure-zone" : "public-zone")
                                + " camera with a useful sightline"));
    }

    /**
     * Keeps an exterior review on the authored front/rear axis while escaping a newly enlarged
     * projection. Direct outward steps are preferred; a small deterministic orbit is used only
     * when another gallery fixture occupies that ray. Every returned position remains outside the
     * visible occupied subject (with a safety buffer) and is checked against the real world at
     * both feet and eye height. Empty fixture-separation padding must not force an unreadably
     * distant photograph.
     */
    private static CapturePose clearExteriorPose(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget framing,
            SubjectBounds subject,
            double preferredX,
            double preferredY,
            double preferredZ,
            double targetX,
            double targetY,
            double targetZ) {
        double radialX = preferredX - targetX;
        double radialZ = preferredZ - targetZ;
        double length = Math.sqrt(radialX * radialX + radialZ * radialZ);
        if (length < 0.001) {
            throw new IllegalStateException("Exterior gallery camera has no view axis");
        }
        radialX /= length;
        radialZ /= length;
        double tangentX = -radialZ;
        double tangentZ = radialX;
        ExteriorSearchDiagnostics diagnostics = new ExteriorSearchDiagnostics();

        List<CapturePose> generated = new ArrayList<>();
        double[] directSteps = subject.allowInwardSearch()
                ? new double[] {
                        -8.0, -6.0, -4.0, -2.0,
                        0.0, 1.0, 2.0, 3.0, 5.0, 8.0, 12.0, 16.0, 20.0, 28.0, 36.0
                }
                : new double[] {
                        0.0, 1.0, 2.0, 3.0, 5.0, 8.0, 12.0, 16.0, 20.0, 28.0, 36.0
                };
        // Only after exhausting normal eye height do we rise by at most three blocks. This clears
        // tall carts/awnings while retaining a human-scale raised three-quarter composition.
        double[] elevationSteps = {0.0, 1.5, 3.0};
        for (double elevation : elevationSteps) {
            for (double outward : directSteps) {
                generated.add(exteriorCandidate(
                        preferredX,
                        preferredY + elevation,
                        preferredZ,
                        targetX,
                        targetY,
                        targetZ,
                        radialX,
                        radialZ,
                        tangentX,
                        tangentZ,
                        outward,
                        0.0));
            }
        }

        double[] tangentSteps = {
                1.0, -1.0, 2.0, -2.0, 3.5, -3.5, 5.0, -5.0, 7.5, -7.5, 10.0, -10.0,
                14.0, -14.0, 18.0, -18.0, 24.0, -24.0
        };
        double[] orbitOutwardSteps = subject.allowInwardSearch()
                ? new double[] {
                        -8.0, -6.0, -4.0, -2.0, 0.0, 1.0, 3.0, 6.0, 10.0, 14.0,
                        20.0, 28.0, 36.0, 44.0
                }
                : new double[] {
                        0.0, 1.0, 3.0, 6.0, 10.0, 14.0, 20.0, 28.0, 36.0, 44.0
                };
        for (double elevation : elevationSteps) {
            for (double tangent : tangentSteps) {
                for (double outward : orbitOutwardSteps) {
                    generated.add(exteriorCandidate(
                            preferredX,
                            preferredY + elevation,
                            preferredZ,
                            targetX,
                            targetY,
                            targetZ,
                            radialX,
                            radialZ,
                            tangentX,
                            tangentZ,
                            outward,
                            tangent));
                }
            }
        }

        List<ExteriorCameraCandidate> ranked = new ArrayList<>();
        double minimumCoverage = subject.allowInwardSearch() ? 0.48 : 0.55;
        double targetCoverage = subject.allowInwardSearch() ? 0.62 : 0.68;
        double maximumNormalCoverage = subject.allowInwardSearch() ? 0.90 : 0.80;
        for (int order = 0; order < generated.size(); order++) {
            CapturePose candidate = generated.get(order);
            if (!outsideSubjectEnvelope(subject, candidate)) {
                continue;
            }
            double coverage = exteriorSubjectViewportCoverage(
                    subject, candidate, targetX, targetY, targetZ);
            if (coverage < minimumCoverage || coverage > maximumNormalCoverage) {
                continue;
            }
            double departureX = candidate.x() - preferredX;
            double departureY = candidate.y() - preferredY;
            double departureZ = candidate.z() - preferredZ;
            ranked.add(new ExteriorCameraCandidate(
                    candidate,
                    Math.abs(coverage - targetCoverage),
                    departureX * departureX
                            + departureY * departureY
                            + departureZ * departureZ,
                    order));
        }
        ranked.sort(Comparator
                .comparingDouble(ExteriorCameraCandidate::framingPenalty)
                .thenComparingDouble(ExteriorCameraCandidate::departure)
                .thenComparingInt(ExteriorCameraCandidate::order));
        for (ExteriorCameraCandidate rankedCandidate : ranked) {
            CapturePose candidate = rankedCandidate.pose();
            if (cameraVolumeClear(level, candidate)
                    && exteriorViewportClear(
                            level,
                            framing,
                            subject,
                            candidate,
                            targetX,
                            targetY,
                            targetZ,
                            tangentX,
                            tangentZ,
                            diagnostics)) {
                if (framing.index() == 0) {
                    GalleryCameraProjection.Projection projection =
                            exteriorSubjectViewportProjection(
                                    subject, candidate, targetX, targetY, targetZ);
                    LOGGER.info(
                            "Gallery frame-001 projection: occupied={}x{}x{} ({} real blocks), pose=({}, {}, {}), horizontal={}%, vertical={}%, dominant={}%, verticalFov={} degrees",
                            subject.width(),
                            subject.height(),
                            subject.depth(),
                            subject.framingBlocks().size(),
                            candidate.x(),
                            candidate.y(),
                            candidate.z(),
                            Math.round(projection.horizontalCoverage() * 1_000.0) / 10.0,
                            Math.round(projection.verticalCoverage() * 1_000.0) / 10.0,
                            Math.round(projection.maximumCoverage() * 1_000.0) / 10.0,
                            candidate.verticalFovDegrees());
                }
                return candidate;
            }
        }
        // Dense gallery rows can leave a valid three-quarter camera with a neighbouring roof in
        // the outer screen wedge even though the intended structure itself is fully framed. Only
        // after every normal 70-degree candidate fails, progressively narrow the deterministic
        // vertical FOV and, if needed, make the smallest tangent aim correction. The pose,
        // collision apron, centre/shoulder LOS, full-subject margins, central composition, and
        // 9x7 zero-tolerance foreign-fixture grid remain mandatory; this is optical framing, not
        // a bypass. Re-rank the complete generated position set at each optical FOV: positions
        // that are too distant at 70 degrees may be exactly the clear, readable standoff at 50.
        for (double verticalFovDegrees : EXTERIOR_ZOOM_FALLBACK_FOV_DEGREES) {
            for (double aimOffset : EXTERIOR_ZOOM_AIM_TANGENT_OFFSETS) {
                double aimX = targetX + tangentX * aimOffset;
                double aimZ = targetZ + tangentZ * aimOffset;
                List<ExteriorCameraCandidate> zoomRanked = new ArrayList<>();
                for (int order = 0; order < generated.size(); order++) {
                    CapturePose base = generated.get(order);
                    CapturePose candidate = lookAt(
                                    base.x(),
                                    base.y(),
                                    base.z(),
                                    aimX,
                                    targetY,
                                    aimZ)
                            .withVerticalFov(verticalFovDegrees);
                    if (!outsideSubjectEnvelope(subject, candidate)) {
                        continue;
                    }
                    double coverage = exteriorSubjectViewportCoverage(
                            subject, candidate, aimX, targetY, aimZ);
                    if (coverage < minimumCoverage
                            || coverage > 0.90
                            || !exteriorSubjectCenteredInViewport(
                                    subject, candidate, aimX, targetY, aimZ)) {
                        continue;
                    }
                    double departureX = candidate.x() - preferredX;
                    double departureY = candidate.y() - preferredY;
                    double departureZ = candidate.z() - preferredZ;
                    zoomRanked.add(new ExteriorCameraCandidate(
                            candidate,
                            Math.abs(coverage - targetCoverage),
                            departureX * departureX
                                    + departureY * departureY
                                    + departureZ * departureZ,
                            order));
                }
                zoomRanked.sort(Comparator
                        .comparingDouble(ExteriorCameraCandidate::framingPenalty)
                        .thenComparingDouble(ExteriorCameraCandidate::departure)
                        .thenComparingInt(ExteriorCameraCandidate::order));
                for (ExteriorCameraCandidate rankedCandidate : zoomRanked) {
                    CapturePose candidate = rankedCandidate.pose();
                    if (cameraVolumeClear(level, candidate)
                            && exteriorViewportClear(
                                    level,
                                    framing,
                                    subject,
                                    candidate,
                                    aimX,
                                    targetY,
                                    aimZ,
                                    tangentX,
                                    tangentZ,
                                    diagnostics)) {
                        LOGGER.info(
                                "Gallery fixture {} uses a {}-degree vertical FOV and {}-block tangent aim offset to exclude a foreign viewport obstruction",
                                framing.index() + 1,
                                verticalFovDegrees,
                                aimOffset);
                        return candidate;
                    }
                }
            }
        }
        throw new NoClearExteriorCameraException(
                "No clear exterior camera outside gallery fixture " + (framing.index() + 1)
                        + " (" + diagnostics.summary() + ")");
    }

    private static CapturePose exteriorCandidate(
            double preferredX,
            double preferredY,
            double preferredZ,
            double targetX,
            double targetY,
            double targetZ,
            double radialX,
            double radialZ,
            double tangentX,
            double tangentZ,
            double outward,
            double tangent) {
        double cameraX = preferredX + radialX * outward + tangentX * tangent;
        double cameraZ = preferredZ + radialZ * outward + tangentZ * tangent;
        return lookAt(
                cameraX,
                preferredY,
                cameraZ,
                targetX,
                targetY,
                targetZ);
    }

    private static boolean outsideSubjectEnvelope(
            SubjectBounds subject, CapturePose candidate) {
        double buffer = 1.5;
        return candidate.x() < subject.minimumX() - buffer
                || candidate.x() > subject.maximumX() + buffer
                || candidate.z() < subject.minimumZ() - buffer
                || candidate.z() > subject.maximumZ() + buffer;
    }

    /**
     * Protects the actual viewport, not just the block occupied by the reviewer. A clear apron
     * keeps nearby roofs and awnings out of the lower frame. The complete authored envelope must
     * project inside a safe 16:9 viewport and retain useful occupancy, so an extreme orbit cannot
     * technically see the center while cropping most of the subject. A forward-only near-field
     * frustum rejects unrelated gallery rows in front of the lens; farther away, a mandatory center
     * ray plus at least three of four corner rays allow legitimate subject eaves and yard dressing.
     * Once a ray enters the authored envelope, walls and props are the evidence being photographed
     * and are intentionally allowed to occlude one another.
     */
    private static boolean exteriorViewportClear(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget framing,
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ,
            double tangentX,
            double tangentZ,
            ExteriorSearchDiagnostics diagnostics) {
        diagnostics.viewportCandidates++;
        BlockPos feet = BlockPos.containing(pose.x(), pose.y() + 0.05, pose.z());
        int minimumY = Math.max((int) subject.minimumY(), feet.getY() - 1);
        int maximumY = feet.getY() + 2;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!level.getBlockState(new BlockPos(
                            feet.getX() + dx, y, feet.getZ() + dz)).isAir()) {
                        diagnostics.apronRejected++;
                        return false;
                    }
                }
            }
        }

        if (!exteriorSubjectFitsViewport(
                subject, pose, targetX, targetY, targetZ)) {
            diagnostics.fitRejected++;
            return false;
        }
        double subjectScreenWidth = framing.rotation() % 2 == 0
                ? subject.width()
                : subject.depth();
        double lateralSpan = Math.min(4.0, Math.max(1.5, subjectScreenWidth * 0.22));
        double verticalSpan = Math.min(3.0, Math.max(1.25, subject.height() * 0.18));
        if (!clearExteriorApproach(
                        level, subject, pose, targetX, targetY, targetZ)
                || !clearExteriorNearFieldApproach(
                        level, subject, pose, targetX, targetY, targetZ)) {
            diagnostics.centerSightRejected++;
            return false;
        }
        double[][] shoulderOffsets = {
                {-lateralSpan, verticalSpan},
                {lateralSpan, verticalSpan},
                {-lateralSpan, -verticalSpan},
                {lateralSpan, -verticalSpan}
        };
        int clearShoulders = 0;
        for (double[] offset : shoulderOffsets) {
            double shoulderX = targetX + tangentX * offset[0];
            double shoulderY = targetY + offset[1];
            double shoulderZ = targetZ + tangentZ * offset[0];
            if (!clearExteriorNearFieldApproach(
                    level,
                    subject,
                    pose,
                    shoulderX,
                    shoulderY,
                    shoulderZ)) {
                diagnostics.shoulderNearFieldRejected++;
                return false;
            }
            if (clearExteriorApproach(
                    level,
                    subject,
                    pose,
                    shoulderX,
                    shoulderY,
                    shoulderZ)) {
                clearShoulders++;
            }
        }
        if (clearShoulders < 3) {
            diagnostics.shoulderSightRejected++;
            return false;
        }
        return clearExteriorViewportRayGrid(
                level,
                framing,
                subject,
                pose,
                targetX,
                targetY,
                targetZ,
                diagnostics);
    }

    /**
     * Samples the complete viewport rather than only its centre and four subject shoulders.
     * A close neighbouring gallery roof can occupy a large screen-edge wedge without crossing any
     * of those five rays. This deterministic 9x7 grid reaches 97 percent toward all four screen
     * edges and corners using the real vertical FOV and 16:9 projection. Every sampled ray must
     * remain clear of raised foreign detail, so even a contiguous screen-edge slice of a
     * neighbouring roof is rejected. Flat
     * gallery ground is ignored; once a ray enters the intended occupied XZ envelope or an exact
     * block owned by the active fixture (including its outlying doodads), the structure itself is
     * evidence and is no longer treated as an obstruction.
     */
    private static boolean clearExteriorViewportRayGrid(
            ServerLevel level,
            StructureGalleryPlan.VisitTarget framing,
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ,
            ExteriorSearchDiagnostics diagnostics) {
        double eyeX = pose.x();
        double eyeY = pose.y() + 1.62;
        double eyeZ = pose.z();
        double targetDistance = Math.sqrt(
                square(targetX - eyeX)
                        + square(targetY - eyeY)
                        + square(targetZ - eyeZ));
        double maximumDistance = Math.max(1.0, targetDistance - 0.50);
        BlockPos cameraFeet = BlockPos.containing(pose.x(), pose.y() + 0.05, pose.z());
        BlockPos cameraEyes = BlockPos.containing(eyeX, eyeY, eyeZ);
        int flatGroundY = (int) Math.floor(subject.minimumY());
        int foreignRays = 0;
        for (int row = 0; row < EXTERIOR_VIEWPORT_RAY_ROWS; row++) {
            double vertical = sampleViewportCoordinate(
                    row,
                    EXTERIOR_VIEWPORT_RAY_ROWS,
                    EXTERIOR_VIEWPORT_VERTICAL_SAMPLE);
            for (int column = 0; column < EXTERIOR_VIEWPORT_RAY_COLUMNS; column++) {
                double horizontal = sampleViewportCoordinate(
                        column,
                        EXTERIOR_VIEWPORT_RAY_COLUMNS,
                        EXTERIOR_VIEWPORT_HORIZONTAL_SAMPLE);
                GalleryCameraProjection.Point direction =
                        GalleryCameraProjection.viewportRayDirection(
                                eyeX,
                                eyeY,
                                eyeZ,
                                targetX,
                                targetY,
                                targetZ,
                                horizontal,
                                vertical,
                                pose.verticalFovDegrees());
                if (!clearExteriorViewportRay(
                        level,
                        subject,
                        eyeX,
                        eyeY,
                        eyeZ,
                        direction,
                        maximumDistance,
                        flatGroundY,
                        cameraFeet,
                        cameraEyes,
                        diagnostics)) {
                    foreignRays++;
                    diagnostics.observeForeignRays(foreignRays, pose);
                    return false;
                }
            }
        }
        diagnostics.observeForeignRays(foreignRays, pose);
        return foreignRays <= EXTERIOR_VIEWPORT_MAX_FOREIGN_RAYS;
    }

    private static boolean clearExteriorViewportRay(
            ServerLevel level,
            SubjectBounds subject,
            double eyeX,
            double eyeY,
            double eyeZ,
            GalleryCameraProjection.Point direction,
            double maximumDistance,
            int flatGroundY,
            BlockPos cameraFeet,
            BlockPos cameraEyes,
            ExteriorSearchDiagnostics diagnostics) {
        int steps = Math.max(
                2,
                (int) Math.ceil(maximumDistance * EXTERIOR_VIEWPORT_RAY_STEPS_PER_BLOCK));
        for (int step = 1; step <= steps; step++) {
            double distance = Math.min(
                    maximumDistance,
                    step / EXTERIOR_VIEWPORT_RAY_STEPS_PER_BLOCK);
            double sampleX = eyeX + direction.x() * distance;
            double sampleY = eyeY + direction.y() * distance;
            double sampleZ = eyeZ + direction.z() * distance;
            if (insideSubjectEnvelope(subject, sampleX, sampleZ)) {
                return true;
            }
            BlockPos sample = BlockPos.containing(sampleX, sampleY, sampleZ);
            if (subject.activeFixtureBlocks().contains(sample)) {
                // The front framing core intentionally excludes distant yard dressing so a lamp
                // or cargo prop cannot shrink the building. It is nevertheless authored evidence
                // from this exact fixture, not a foreign gallery obstruction.
                return true;
            }
            if (sample.equals(cameraFeet)
                    || sample.equals(cameraEyes)
                    || sample.getY() <= flatGroundY) {
                continue;
            }
            if (!level.getBlockState(sample).isAir()) {
                diagnostics.observeForeignBlock(sample);
                return false;
            }
        }
        return true;
    }

    private static double sampleViewportCoordinate(
            int index, int sampleCount, double extent) {
        if (sampleCount < 2 || index < 0 || index >= sampleCount) {
            throw new IllegalArgumentException("Invalid viewport sample index");
        }
        return -extent + extent * 2.0 * index / (sampleCount - 1.0);
    }

    private static double square(double value) {
        return value * value;
    }

    /**
     * Models the capture projection itself. The review client's configured 70-degree vertical FOV
     * derives a wider horizontal FOV at 16:9; the smaller limits below reserve a
     * border on every edge for roofs, towers and rear-yard props. The real occupied block union,
     * rather than its enclosing box or empty collision padding, must fill 55-80 percent of at
     * least one viewport dimension. Rear-doodad views use a 48-percent core minimum, independently
     * require their complete raised scene and non-core doodad cluster to fit and remain visible,
     * and may search inward to establish readable relationships between building and props.
     */
    private static boolean exteriorSubjectFitsViewport(
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        double coverage = exteriorSubjectViewportCoverage(
                subject, pose, targetX, targetY, targetZ);
        double minimumCoverage = subject.allowInwardSearch() ? 0.48 : 0.55;
        double maximumCoverage = subject.allowInwardSearch()
                        || pose.verticalFovDegrees()
                                < GalleryCameraProjection.REVIEW_VERTICAL_FOV_DEGREES
                ? 0.90
                : 0.80;
        return coverage >= minimumCoverage && coverage <= maximumCoverage;
    }

    private static double exteriorSubjectViewportCoverage(
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        GalleryCameraProjection.Projection projection = exteriorSubjectViewportProjection(
                subject, pose, targetX, targetY, targetZ);
        if (!projection.fits()) {
            return -1.0;
        }
        if (subject.allowInwardSearch()) {
            GalleryCameraProjection.Projection scene = projectExteriorBlocks(
                    subject.raisedSceneBlocks(), pose, targetX, targetY, targetZ);
            GalleryCameraProjection.Projection doodads = projectExteriorBlocks(
                    subject.doodadBlocks(), pose, targetX, targetY, targetZ);
            if (!scene.fits()
                    || !doodads.fits()
                    || scene.maximumCoverage() < 0.50
                    || doodads.maximumCoverage() < 0.10) {
                return -1.0;
            }
        }
        return projection.maximumCoverage();
    }

    private static boolean exteriorSubjectCenteredInViewport(
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        GalleryCameraProjection.Projection projection = exteriorSubjectViewportProjection(
                subject, pose, targetX, targetY, targetZ);
        return projection.fits() && Math.abs(projection.horizontalCenterOffset()) <= 0.15;
    }

    private static GalleryCameraProjection.Projection exteriorSubjectViewportProjection(
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        return projectExteriorBlocks(
                subject.framingBlocks(), pose, targetX, targetY, targetZ);
    }

    private static GalleryCameraProjection.Projection projectExteriorBlocks(
            List<GalleryCameraProjection.Point> blocks,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        return GalleryCameraProjection.projectOccupiedBlocks(
                blocks,
                pose.x(),
                pose.y() + 1.62,
                pose.z(),
                targetX,
                targetY,
                targetZ,
                pose.verticalFovDegrees());
    }

    /**
     * Requires every sampled viewport corner to be open for the first eight blocks in front of the
     * lens. Unlike the safety apron this does not inspect blocks behind or beside the reviewer, and
     * unlike the full sightline it deliberately stops before legitimate subject projections.
     */
    private static boolean clearExteriorNearFieldApproach(
            ServerLevel level,
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        double fromX = pose.x();
        double fromY = pose.y() + 1.62;
        double fromZ = pose.z();
        double dx = targetX - fromX;
        double dy = targetY - fromY;
        double dz = targetZ - fromZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(2, (int) Math.ceil(distance * 6.0));
        BlockPos cameraFeet = BlockPos.containing(pose.x(), pose.y() + 0.05, pose.z());
        for (int step = 1; step < steps; step++) {
            double progress = (double) step / steps;
            if (distance * progress > 8.0) {
                return true;
            }
            double sampleX = fromX + dx * progress;
            double sampleY = fromY + dy * progress;
            double sampleZ = fromZ + dz * progress;
            if (insideSubjectEnvelope(subject, sampleX, sampleZ)) {
                return true;
            }
            BlockPos sample = BlockPos.containing(sampleX, sampleY, sampleZ);
            if (!sample.equals(cameraFeet)
                    && !sample.equals(cameraFeet.above())
                    && !level.getBlockState(sample).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean clearExteriorApproach(
            ServerLevel level,
            SubjectBounds subject,
            CapturePose pose,
            double targetX,
            double targetY,
            double targetZ) {
        double fromX = pose.x();
        double fromY = pose.y() + 1.62;
        double fromZ = pose.z();
        double dx = targetX - fromX;
        double dy = targetY - fromY;
        double dz = targetZ - fromZ;
        int steps = Math.max(2, (int) Math.ceil(
                Math.sqrt(dx * dx + dy * dy + dz * dz) * 6.0));
        BlockPos cameraFeet = BlockPos.containing(pose.x(), pose.y() + 0.05, pose.z());
        for (int step = 1; step < steps; step++) {
            double progress = (double) step / steps;
            double sampleX = fromX + dx * progress;
            double sampleY = fromY + dy * progress;
            double sampleZ = fromZ + dz * progress;
            if (insideSubjectEnvelope(subject, sampleX, sampleZ)) {
                return true;
            }
            BlockPos sample = BlockPos.containing(sampleX, sampleY, sampleZ);
            if (!sample.equals(cameraFeet)
                    && !sample.equals(cameraFeet.above())
                    && !level.getBlockState(sample).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean insideSubjectEnvelope(
            SubjectBounds subject, double x, double z) {
        return x >= subject.minimumX()
                && x <= subject.maximumX()
                && z >= subject.minimumZ()
                && z <= subject.maximumZ();
    }

    private static boolean cameraVolumeClear(ServerLevel level, CapturePose pose) {
        BlockPos feet = BlockPos.containing(pose.x(), pose.y() + 0.05, pose.z());
        BlockPos eyes = BlockPos.containing(pose.x(), pose.y() + 1.62, pose.z());
        return level.getBlockState(feet).isAir() && level.getBlockState(eyes).isAir();
    }

    private static boolean supportedCameraVolumeClear(ServerLevel level, BlockPos feet) {
        if (!level.getBlockState(feet).isAir()
                || !level.getBlockState(feet.above()).isAir()) {
            return false;
        }
        BlockPos support = feet.below();
        return level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty()
                && level.getBlockState(support).isFaceSturdy(
                        level, support, net.minecraft.core.Direction.UP);
    }

    private static List<BlockPos> clearInteriorFloor(
            ServerLevel level,
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            int localY) {
        List<BlockPos> clear = new ArrayList<>();
        for (int x = 1; x < descriptor.width() - 1; x++) {
            for (int z = 1; z < descriptor.depth() - 1; z++) {
                BlockPos local = new BlockPos(x, localY, z);
                BlockPos transformed = transformLocal(
                        local,
                        descriptor.width(),
                        descriptor.depth(),
                        entry.mirrored(),
                        entry.rotation());
                BlockPos world = new BlockPos(
                        entry.originX() + transformed.getX(),
                        baseY + transformed.getY(),
                        entry.originZ() + transformed.getZ());
                if (supportedCameraVolumeClear(level, world)) {
                    clear.add(local);
                }
            }
        }
        return List.copyOf(clear);
    }

    /**
     * Routes can cross a rail, ordinary door or thin ladder boarding column without making
     * that position a camera perch. Production circulation joins a reachable ladder's boarding
     * sides; a same-level photography route must not treat that thin column as a solid wall.
     * This mirrors production circulation while actual support,
     * fluids, and the strict air-only camera selection remain independent requirements.
     */
    private static List<BlockPos> traversablePhotographyFloor(
            ServerLevel level,
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            int localY) {
        List<BlockPos> route = new ArrayList<>();
        for (int x = 1; x < descriptor.width() - 1; x++) {
            for (int z = 1; z < descriptor.depth() - 1; z++) {
                BlockPos local = new BlockPos(x, localY, z);
                BlockPos world = transformedWorldPosition(baseY, entry, descriptor, local);
                BlockState feet = level.getBlockState(world);
                BlockState head = level.getBlockState(world.above());
                BlockPos support = world.below();
                if (photographyRoutePassable(feet, head)
                        && level.getFluidState(world).isEmpty()
                        && level.getFluidState(world.above()).isEmpty()
                        && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                    route.add(local);
                }
            }
        }
        return List.copyOf(route);
    }

    private static boolean photographyRoutePassable(BlockState feet, BlockState head) {
        return (feet.isAir() || feet.getBlock() instanceof DoorBlock || feet.is(Blocks.RAIL)
                        || feet.is(Blocks.LADDER))
                && (head.isAir() || head.getBlock() instanceof DoorBlock || head.is(Blocks.LADDER));
    }

    /** Functional capture-time regression, using real block states and the actual route flood. */
    private static void verifyPhotographyRouteSeparation() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState rail = Blocks.RAIL.defaultBlockState();
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        BlockState ladder = Blocks.LADDER.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        if (!photographyRoutePassable(rail, air)
                || !photographyRoutePassable(door, door)
                || !photographyRoutePassable(ladder, ladder)
                || photographyRoutePassable(stone, air)
                || photographyRoutePassable(air, stone)
                || photographyRoutePassable(air, rail)) {
            throw new IllegalStateException("Photography route block-classification regression");
        }
        BlockPos entrance = new BlockPos(1, 1, 1);
        BlockPos railBridge = new BlockPos(2, 1, 1);
        BlockPos doorBridge = new BlockPos(3, 1, 1);
        BlockPos ladderBridge = new BlockPos(4, 1, 1);
        BlockPos room = new BlockPos(5, 1, 1);
        List<BlockPos> route = List.of(entrance, railBridge, doorBridge, ladderBridge, room);
        List<BlockPos> strictCameraCells = List.of(entrance, room);
        List<BlockPos> cameras = reachableInteriorFloor(route, room, entrance, List.of()).stream()
                .filter(strictCameraCells::contains).toList();
        List<BlockPos> blockedRoute = List.of(entrance, railBridge, doorBridge, room);
        if (!cameras.contains(room) || cameras.contains(railBridge) || cameras.contains(doorBridge)
                || cameras.contains(ladderBridge)
                || reachableInteriorFloor(blockedRoute, room, entrance, List.of()).contains(room)) {
            throw new IllegalStateException("Photography route/camera separation regression");
        }
    }

    /** Floods the clear floor from the actual entrance/dismount component, never through walls. */
    private static List<BlockPos> reachableInteriorFloor(
            List<BlockPos> clearFloor,
            BlockPos preferred,
            BlockPos entrance,
            List<AuthoredVillageStructures.VerticalAccess> verticalAccess) {
        if (clearFloor.isEmpty()) {
            return List.of();
        }
        int floorY = preferred.getY();
        List<BlockPos> anchors = new ArrayList<>();
        if (entrance != null && entrance.getY() == floorY) {
            anchors.add(entrance);
        }
        for (AuthoredVillageStructures.VerticalAccess access : verticalAccess) {
            if (access.from().getY() == floorY) {
                anchors.add(access.from());
            }
            if (access.to().getY() == floorY) {
                anchors.add(access.to());
            }
        }
        if (anchors.isEmpty()) {
            anchors.add(preferred);
        }

        BlockPos seed = clearFloor.stream()
                .min(Comparator
                        .comparingDouble((BlockPos candidate) -> anchors.stream()
                                .mapToDouble(anchor -> spatialDistanceSquared(anchor, candidate))
                                .min()
                                .orElse(Double.MAX_VALUE))
                        .thenComparing(POSITION_ORDER))
                .orElseThrow();
        Set<BlockPos> clear = new HashSet<>(clearFloor);
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        reached.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (BlockPos neighbor : List.of(
                    current.offset(1, 0, 0),
                    current.offset(-1, 0, 0),
                    current.offset(0, 0, 1),
                    current.offset(0, 0, -1))) {
                if (clear.contains(neighbor) && reached.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return reached.stream().sorted(POSITION_ORDER).toList();
    }

    private static List<BlockPos> authoredRoleTargets(
            AuthoredVillageStructures.Blueprint blueprint,
            List<BlockPos> reviewPositions) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        blueprint.metadata().accessTargets().stream()
                .sorted(POSITION_ORDER)
                .forEach(targets::add);
        for (List<AuthoredVillageStructures.Cell> layer
                : List.of(blueprint.base(), blueprint.stageOne(), blueprint.stageTwo())) {
            layer.stream()
                    .filter(cell -> cell.phase() == AuthoredVillageStructures.Phase.FIXTURE)
                    .filter(cell -> cell.state().getLightEmission() == 0)
                    .map(cell -> new BlockPos(cell.x(), cell.y(), cell.z()))
                    .sorted(POSITION_ORDER)
                    .forEach(targets::add);
        }
        reviewPositions.forEach(targets::add);
        return List.copyOf(targets);
    }

    private static BlockPos bestVisibleInteriorTarget(
            ServerLevel level,
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            BlockPos cameraLocal,
            List<BlockPos> roleTargets,
            List<BlockPos> reachableFloor) {
        BlockPos cameraWorld = transformedWorldPosition(
                baseY, entry, descriptor, cameraLocal);
        BlockPos target = roleTargets.stream()
                .filter(candidate -> horizontalDistanceSquared(cameraLocal, candidate) >= 6.25)
                .filter(candidate -> Math.abs(candidate.getY() - cameraLocal.getY()) <= 5)
                .filter(candidate -> !isInteriorPhotographyBarrier(
                        level.getBlockState(transformedWorldPosition(
                                baseY, entry, descriptor, candidate))))
                .filter(candidate -> hasClearInteriorSight(
                        level,
                        cameraWorld,
                        transformedWorldPosition(baseY, entry, descriptor, candidate)))
                .max(Comparator
                        .comparingDouble((BlockPos candidate) -> horizontalDistanceSquared(
                                cameraLocal, candidate))
                        .thenComparing(POSITION_ORDER))
                .orElse(null);
        if (target != null) {
            return target;
        }
        return reachableFloor.stream()
                .filter(candidate -> horizontalDistanceSquared(cameraLocal, candidate) >= 6.25)
                .filter(candidate -> hasClearInteriorSight(
                        level,
                        cameraWorld,
                        transformedWorldPosition(baseY, entry, descriptor, candidate)))
                .max(Comparator
                        .comparingDouble((BlockPos candidate) -> horizontalDistanceSquared(
                                cameraLocal, candidate))
                        .thenComparing(POSITION_ORDER))
                .orElse(null);
    }

    /** Prefers an actual room over a doorway, one-wide hall, or storage slot near the sample. */
    private static int visibleInteriorFloorCount(
            ServerLevel level,
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            BlockPos cameraLocal,
            List<BlockPos> reachableFloor) {
        BlockPos cameraWorld = transformedWorldPosition(
                baseY, entry, descriptor, cameraLocal);
        int visible = 0;
        for (BlockPos floor : reachableFloor) {
            double distance = horizontalDistanceSquared(cameraLocal, floor);
            if (distance > 0.0
                    && distance <= 100.0
                    && hasClearInteriorSight(
                            level,
                            cameraWorld,
                            transformedWorldPosition(baseY, entry, descriptor, floor))) {
                visible++;
            }
        }
        return visible;
    }

    private static boolean isInteriorPhotographyBarrier(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock;
    }

    private static BlockPos transformedWorldPosition(
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            BlockPos local) {
        BlockPos transformed = transformLocal(
                local,
                descriptor.width(),
                descriptor.depth(),
                entry.mirrored(),
                entry.rotation());
        return new BlockPos(
                entry.originX() + transformed.getX(),
                baseY + transformed.getY(),
                entry.originZ() + transformed.getZ());
    }

    private static CapturePose lookAtLocal(
            int baseY,
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            BlockPos cameraLocal,
            BlockPos aimLocal,
            double targetYOffset) {
        BlockPos camera = transformedWorldPosition(baseY, entry, descriptor, cameraLocal);
        BlockPos target = transformedWorldPosition(baseY, entry, descriptor, aimLocal);
        return lookAt(
                camera.getX() + 0.5,
                camera.getY(),
                camera.getZ() + 0.5,
                target.getX() + 0.5,
                target.getY() + targetYOffset,
                target.getZ() + 0.5);
    }

    /**
     * Strict voxel sightline for photography. Doors, glass, fences and partial blocks can be
     * navigationally or visually significant even when they do not report full light occlusion;
     * ordinary authored interiors therefore accept only air. The Bank-secure overload explicitly
     * permits its intentional iron grille, but still rejects every door and solid block.
     */
    private static boolean hasClearInteriorSight(
            ServerLevel level, BlockPos cameraFeet, BlockPos target) {
        return hasClearInteriorSight(level, cameraFeet, target, false);
    }

    private static boolean hasClearInteriorSight(
            ServerLevel level,
            BlockPos cameraFeet,
            BlockPos target,
            boolean allowIronBarGrille) {
        double fromX = cameraFeet.getX() + 0.5;
        double fromY = cameraFeet.getY() + 1.62;
        double fromZ = cameraFeet.getZ() + 0.5;
        double toX = target.getX() + 0.5;
        double toY = target.getY() + 0.85;
        double toZ = target.getZ() + 0.5;
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int steps = Math.max(2, (int) Math.ceil(
                Math.sqrt(dx * dx + dy * dy + dz * dz) * 6.0));
        for (int step = 1; step < steps; step++) {
            double progress = (double) step / steps;
            double sampleY = fromY + dy * progress;
            BlockPos sample = BlockPos.containing(
                    fromX + dx * progress,
                    sampleY,
                    fromZ + dz * progress);
            if (sample.equals(target) || sample.equals(cameraFeet)
                    || sample.equals(cameraFeet.above())) {
                continue;
            }
            BlockState state = level.getBlockState(sample);
            if (!state.isAir()
                    && !(allowIronBarGrille && state.is(Blocks.IRON_BARS))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks the actual continuous camera-to-focus rays used by the compact Bank vault shot.
     * The ordinary floor-cell sightline deliberately aims from block centers; that is too coarse
     * for a compact two-block doorway where a safe within-cell shoulder offset is the composition.
     * The target cell itself may contain the photographed lamp or furniture, but every
     * intervening sample must remain air, including iron bars.
     */
    private static boolean hasClearInteriorEyeRay(
            ServerLevel level,
            double fromX,
            double fromY,
            double fromZ,
            double toX,
            double toY,
            double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int steps = Math.max(2, (int) Math.ceil(
                Math.sqrt(dx * dx + dy * dy + dz * dz) * 8.0));
        BlockPos start = BlockPos.containing(fromX, fromY, fromZ);
        BlockPos target = BlockPos.containing(toX, toY, toZ);
        for (int step = 1; step < steps; step++) {
            double progress = (double) step / steps;
            double sampleY = fromY + dy * progress;
            BlockPos sample = BlockPos.containing(
                    fromX + dx * progress,
                    sampleY,
                    fromZ + dz * progress);
            if (sample.equals(start) || sample.equals(target)) {
                continue;
            }
            BlockState state = level.getBlockState(sample);
            // Pressure plates used as writing-paper detail occupy only the bottom sixteenth of
            // their block. The review ray travels at eye height well above that real geometry;
            // treating the whole cell as opaque incorrectly rejects an otherwise unobstructed
            // shoulder view. Bars and every other non-air block remain strict occluders.
            if (state.is(BlockTags.PRESSURE_PLATES)
                    && sampleY > sample.getY() + 0.125) {
                continue;
            }
            if (!state.isAir()) {
                LOGGER.info(
                        "Bank secure review eye ray blocked at {} by {} (target {})",
                        sample,
                        state,
                        target);
                return false;
            }
        }
        return true;
    }

    /**
     * Frames one concrete authored yard composition closely enough to score independently.
     *
     * <p>The review-plan metadata chooses a frozen host and stage. This method then locates the
     * archetype from its distinctive authored cells instead of copying its placement arithmetic.
     * If an author later changes the composition so that the signature disappears, capture fails
     * loudly rather than silently photographing an unrelated patch of yard.</p>
     */
    private static CapturePose captureDoodadDetailPose(
            StructureGalleryPlan.VisitTarget framing,
            int baseY,
            StructureGalleryPlan.Entry entry,
            AuthoredVillageStructures.Blueprint blueprint,
            StructureGalleryReviewPlan.DoodadDetail detail) {
        if (detail == null) {
            throw new IllegalArgumentException("Doodad detail capture lacks focus metadata");
        }
        if (framing.originX() != entry.originX() || framing.originZ() != entry.originZ()) {
            throw new IllegalStateException("Doodad detail host escaped its gallery fixture");
        }
        // Front/rear dressing is append-only over the authored base. D2 lives in that base
        // forecourt while the other exterior doodads live in the selected dressing stage, so
        // capture matching must inspect the same cumulative scene the player actually sees.
        List<AuthoredVillageStructures.Cell> scene = new ArrayList<>(blueprint.base());
        scene.addAll(detail.sourceView() == StructureGalleryReviewPlan.View.FRONT
                ? blueprint.stageOne()
                : blueprint.stageTwo());
        Map<BlockPos, AuthoredVillageStructures.Cell> sceneByPosition = new HashMap<>();
        for (AuthoredVillageStructures.Cell cell : scene) {
            sceneByPosition.put(new BlockPos(cell.x(), cell.y(), cell.z()), cell);
        }
        List<AuthoredVillageStructures.Cell> matches = scene.stream()
                .filter(cell -> matchesDoodadCell(
                        detail.doodad(), cell, blueprint, sceneByPosition))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "Gallery host " + entry.templateId() + " has no focus cells for "
                            + detail.doodad().id() + " in " + detail.sourceView().id());
        }

        // A stage can contain several props that happen to use the same block. Keep the camera on
        // one connected authored composition; never average separated planters or benches into an
        // empty midpoint. Lamps and one-block role anchors use their stable first match.
        List<AuthoredVillageStructures.Cell> focusCells = localizedDoodadCluster(
                matches, detail.doodad());
        LocalPoint focus = averageLocalPoint(focusCells);

        DoodadCameraProfile profile = doodadCameraProfile(detail.doodad());
        LocalPoint camera = profile == null
                ? defaultDoodadCamera(focus, blueprint, detail.sourceView())
                : motifAwareDoodadCamera(
                        focus, blueprint, detail.sourceView(), detail.doodad(), profile);
        double targetY = profile == null
                ? focus.y()
                : Math.max(0.85, focus.y() + profile.targetYOffset());

        LocalPoint transformedCamera = transformLocal(
                camera,
                blueprint.width(),
                blueprint.depth(),
                entry.mirrored(),
                entry.rotation());
        LocalPoint transformedFocus = transformLocal(
                new LocalPoint(focus.x(), targetY, focus.z()),
                blueprint.width(),
                blueprint.depth(),
                entry.mirrored(),
                entry.rotation());
        return lookAt(
                entry.originX() + transformedCamera.x(),
                baseY + transformedCamera.y(),
                entry.originZ() + transformedCamera.z(),
                entry.originX() + transformedFocus.x(),
                baseY + transformedFocus.y(),
                entry.originZ() + transformedFocus.z());
    }

    /** Preserves the established close-up for motifs whose silhouette was already unambiguous. */
    private static LocalPoint defaultDoodadCamera(
            LocalPoint focus,
            AuthoredVillageStructures.Blueprint blueprint,
            StructureGalleryReviewPlan.View sourceView) {
        double cameraX = focus.x();
        double cameraZ = focus.z();
        if (sourceView == StructureGalleryReviewPlan.View.REAR_DOODADS) {
            cameraZ += 6.5;
        } else if (focus.z() < 0.0) {
            cameraZ -= 6.5;
        } else if (focus.x() < 0.0) {
            cameraX -= 5.5;
            cameraZ -= 2.75;
        } else if (focus.x() > blueprint.width()) {
            cameraX += 5.5;
            cameraZ -= 2.75;
        } else {
            cameraZ -= 6.5;
        }
        return new LocalPoint(
                cameraX,
                Math.max(0.5, Math.min(2.0, focus.y() - 0.1)),
                cameraZ);
    }

    /**
     * Gives broad or layered yard props a low three-quarter view of their working face.
     *
     * <p>Square-on cameras made a bench back hide its seat, a cart load hide its axle, and a tool
     * rack header hide its ironwork. The camera remains on the exterior side of the motif but
     * moves tangentially away from the building centre. Side-yard cameras retain a north/front
     * bias so the structure remains a readable backdrop rather than becoming foreground
     * occlusion.</p>
     */
    private static LocalPoint motifAwareDoodadCamera(
            LocalPoint focus,
            AuthoredVillageStructures.Blueprint blueprint,
            StructureGalleryReviewPlan.View sourceView,
            StructureGalleryReviewPlan.Doodad doodad,
            DoodadCameraProfile profile) {
        double cameraX = focus.x();
        double cameraZ = focus.z();
        double lateralSign = doodadLateralSign(focus, blueprint.width(), doodad);
        if (sourceView == StructureGalleryReviewPlan.View.REAR_DOODADS
                && doodad == StructureGalleryReviewPlan.Doodad.SAFE_CAMPFIRE_NOOK) {
            // The former building-side corner looked through a tall stool and clipped the host
            // wall. Use the open outer diagonal above the seating: the campfire core and curb
            // stay visible while the bench, chopping block and host form the surrounding scene.
            cameraX -= lateralSign * profile.standoffDistance();
            cameraZ += profile.tangentDistance();
            return new LocalPoint(cameraX, profile.cameraFeetY(), cameraZ);
        }
        if (doodad == StructureGalleryReviewPlan.Doodad.TOOL_RACK
                && (focus.x() < 0.0 || focus.x() > blueprint.width())) {
            // Side-yard tool racks face inward.  Shoot from the shallow inward/front corner so
            // the hanging chain, iron tool faces, and work ledge remain in front of the lintel.
            double inwardSign = focus.x() < 0.0 ? 1.0 : -1.0;
            cameraX += inwardSign * profile.tangentDistance();
            cameraZ -= profile.standoffDistance();
            return new LocalPoint(cameraX, profile.cameraFeetY(), cameraZ);
        }
        if (sourceView == StructureGalleryReviewPlan.View.REAR_DOODADS) {
            cameraX += lateralSign * profile.tangentDistance();
            cameraZ += profile.standoffDistance();
        } else if (focus.z() < 0.0) {
            cameraX += lateralSign * profile.tangentDistance();
            cameraZ -= profile.standoffDistance();
        } else if (focus.x() < 0.0) {
            cameraX -= profile.standoffDistance();
            cameraZ -= profile.tangentDistance();
        } else if (focus.x() > blueprint.width()) {
            cameraX += profile.standoffDistance();
            cameraZ -= profile.tangentDistance();
        } else {
            cameraX += lateralSign * profile.tangentDistance();
            cameraZ -= profile.standoffDistance();
        }
        return new LocalPoint(cameraX, profile.cameraFeetY(), cameraZ);
    }

    /**
     * Chooses the unobstructed side of a north/south-yard prop without depending on its seed.
     * Centrally placed motifs alternate by stable archetype so adjacent detail shots do not all
     * collapse to the same composition.
     */
    private static double doodadLateralSign(
            LocalPoint focus,
            int blueprintWidth,
            StructureGalleryReviewPlan.Doodad doodad) {
        double centerDelta = focus.x() - blueprintWidth / 2.0;
        if (Math.abs(centerDelta) >= 0.5) {
            return centerDelta < 0.0 ? -1.0 : 1.0;
        }
        return (doodad.ordinal() & 1) == 0 ? -1.0 : 1.0;
    }

    /**
     * Eye height is {@code cameraFeetY + 1.62}; these profiles deliberately keep it between
     * roughly 2.2 and 2.6 blocks while aiming below each motif's former opaque top edge.
     */
    private static DoodadCameraProfile doodadCameraProfile(
            StructureGalleryReviewPlan.Doodad doodad) {
        return switch (doodad) {
            case FORECOURT_PLANT_PEDESTAL -> new DoodadCameraProfile(4.25, 1.75, 0.70, -0.10);
            case FORECOURT_CARGO_PEDESTAL -> new DoodadCameraProfile(4.60, 2.20, 0.78, -0.10);
            case RAIL_BOUND_LOG_RACK -> new DoodadCameraProfile(5.00, 3.00, 0.85, -0.45);
            case SLAB_AND_FENCE_BENCH -> new DoodadCameraProfile(5.00, 3.60, 0.75, -0.25);
            case SAFE_CAMPFIRE_NOOK -> new DoodadCameraProfile(5.10, 3.75, 2.60, -0.30);
            case GARDEN_WORK_CORNER -> new DoodadCameraProfile(5.25, 3.25, 2.50, 0.15);
            // Look under the shooting-line canopy rather than through its opaque upper face.
            case GUARD_TARGET_RACK -> new DoodadCameraProfile(5.75, 2.00, 0.10, 0.0);
            case HAND_CART -> new DoodadCameraProfile(5.15, 4.10, 0.90, -1.35);
            case MATERIAL_PILE -> new DoodadCameraProfile(5.20, 2.80, 1.10, 0.90);
            case HITCHING_RAIL -> new DoodadCameraProfile(5.00, 3.60, 0.75, -0.85);
            case TOOL_RACK -> new DoodadCameraProfile(4.90, 2.60, 0.55, -0.45);
            default -> null;
        };
    }

    private static boolean matchesDoodadCell(
            StructureGalleryReviewPlan.Doodad doodad,
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        BlockState state = cell.state();
        if (!isExteriorYardCell(cell, blueprint)) {
            return false;
        }
        return switch (doodad) {
            case FORECOURT_PLANT_PEDESTAL -> isPlantPedestalAnchor(
                    cell, blueprint, scene);
            case PLANTER_RUN -> isPlanterRunPlant(cell, scene);
            case FORECOURT_CARGO_PEDESTAL -> isForecourtCargoAnchor(
                    cell, blueprint, scene);
            case FREESTANDING_LAMP_POST -> isFreestandingLampAnchor(cell, blueprint, scene);
            case RAIL_BOUND_LOG_RACK -> state.is(Blocks.RAIL);
            case SLAB_AND_FENCE_BENCH -> isBenchCenter(cell, blueprint, scene);
            case SAFE_CAMPFIRE_NOOK -> state.is(Blocks.CAMPFIRE);
            case GARDEN_WORK_CORNER -> state.is(Blocks.MOSS_BLOCK);
            case CRATE_CLUSTER -> isCrateClusterAnchor(cell, blueprint, scene);
            case HAND_CART -> isHandCartCargoAnchor(cell, blueprint, scene);
            case HITCHING_RAIL -> isHitchingRailAnchor(cell, blueprint, scene);
            case FEED_TROUGH -> cell.phase() == AuthoredVillageStructures.Phase.DECOR
                    && cell.y() == 1
                    && state.is(blueprint.materials().roofStairs());
            case HAY_PILE -> state.is(Blocks.HAY_BLOCK);
            case MATERIAL_PILE -> isMaterialPileAnchor(cell, blueprint, scene);
            case TOOL_RACK -> isToolRackAnchor(cell, blueprint, scene);
            case GUARD_TARGET_RACK -> state.is(Blocks.TARGET);
        };
    }

    /** D3's hanging lamp, chain, arm and grounded post must form one connected authored fixture. */
    private static boolean isFreestandingLampAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (cell.y() != 2 || !cell.state().is(Blocks.LANTERN)
                || !cell.state().getOptionalValue(
                        net.minecraft.world.level.block.LanternBlock.HANGING).orElse(false)
                || !isBlock(scene, cell.x(), 3, cell.z(), Blocks.IRON_CHAIN)
                || !(isBlock(scene, cell.x(), 4, cell.z(), blueprint.materials().timber())
                        || isBlock(scene, cell.x(), 4, cell.z(), blueprint.materials().roofSlab()))) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int postX = cell.x() + direction.getStepX();
            int postZ = cell.z() + direction.getStepZ();
            AuthoredVillageStructures.Cell knee = scene.get(new BlockPos(postX, 3, postZ));
            if (isBlock(scene, postX, 4, postZ, blueprint.materials().timber())
                    && knee != null && knee.state().is(blueprint.materials().roofStairs())
                    && knee.state().getValue(net.minecraft.world.level.block.StairBlock.FACING)
                            == direction.getOpposite()
                    && isBlock(scene, postX, 2, postZ, blueprint.materials().fence())
                    && scene.containsKey(new BlockPos(postX, 0, postZ))
                    && (isBlock(scene, postX, 1, postZ, blueprint.materials().fence())
                            || isBlock(scene, postX, 1, postZ, Blocks.STONE_BRICK_WALL)
                            || isBlock(scene, postX, 1, postZ, Blocks.SANDSTONE_WALL))) {
                return true;
            }
        }
        return false;
    }

    /** D14 keeps its coal stock in grade, directly beneath the low stone fragment pile. */
    private static boolean isMaterialPileAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (!cell.state().is(Blocks.COAL_BLOCK)) {
            return false;
        }
        if (blueprint.revision() < 3) {
            return cell.y() == 1;
        }
        int outward = cell.x() < 0 ? -1 : 1;
        int pileX = cell.x() + outward;
        return cell.y() == 0
                && cell.phase() == AuthoredVillageStructures.Phase.FOUNDATION
                && isBlock(scene, cell.x(), 1, cell.z(), Blocks.ANDESITE_SLAB)
                && isBlock(scene, pileX, 0, cell.z() - 1, Blocks.GRAVEL)
                && isBlock(scene, pileX, 1, cell.z() - 1, Blocks.COBBLED_DEEPSLATE_STAIRS);
    }

    private static boolean isExteriorYardCell(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint) {
        return cell.x() < 0
                || cell.x() >= blueprint.width()
                || cell.z() < 0
                || cell.z() >= blueprint.depth();
    }

    private static boolean isForecourtCargoAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (cell.phase() != AuthoredVillageStructures.Phase.DECOR
                || cell.y() != 2
                || cell.z() >= 0
                || cell.x() < 0
                || cell.x() >= blueprint.width()
                || isPottedPlant(cell.state())
                || cell.state().is(Blocks.AZALEA)
                || cell.state().is(Blocks.FLOWERING_AZALEA)
                || cell.state().is(Blocks.LANTERN)) {
            return false;
        }
        if (!isBlock(scene, cell.x(), 1, cell.z(), blueprint.materials().roofSlab())
                || !isBlock(scene, cell.x(), 3, cell.z(), Blocks.RAIL)
                || !scene.containsKey(new BlockPos(cell.x(), 0, cell.z()))) {
            return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int parcelX = cell.x() + dx;
                int parcelZ = cell.z() + dz;
                if (isBlock(scene, parcelX, 1, parcelZ, blueprint.materials().roofSlab())
                        && isBlock(scene, parcelX, 3, parcelZ, Blocks.RAIL)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Identifies the deliberately isolated potted-plant vignette used by D1.
     *
     * <p>Compact cottages place this pedestal in a lateral yard at positive local Z, while larger
     * buildings can place the same motif in the north forecourt. Its topology is therefore the
     * stable contract: one supported plant without the adjacent plant/moss cadence of D8.</p>
     */
    private static boolean isPlantPedestalAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (cell.phase() != AuthoredVillageStructures.Phase.DECOR
                || cell.y() != 1
                || !isPottedPlant(cell.state())
                || isPlanterRunPlant(cell, scene)) {
            return false;
        }
        AuthoredVillageStructures.Cell support = scene.get(
                new BlockPos(cell.x(), 0, cell.z()));
        return support != null
                && support.phase() == AuthoredVillageStructures.Phase.FOUNDATION
                && (support.state().is(blueprint.materials().foundation())
                        || support.state().is(blueprint.materials().accent()));
    }

    /** Returns true only for a plant participating in D8's cardinal run or moss end marker. */
    private static boolean isPlanterRunPlant(
            AuthoredVillageStructures.Cell cell,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (cell.phase() != AuthoredVillageStructures.Phase.DECOR
                || cell.y() != 1
                || !isPottedPlant(cell.state())) {
            return false;
        }
        for (int[] offset : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            AuthoredVillageStructures.Cell neighbor = scene.get(new BlockPos(
                    cell.x() + offset[0], cell.y(), cell.z() + offset[1]));
            if (neighbor != null
                    && (isPottedPlant(neighbor.state())
                            || neighbor.state().is(Blocks.MOSS_CARPET))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBenchCenter(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (cell.phase() != AuthoredVillageStructures.Phase.DECOR
                || cell.y() != 1
                || !cell.state().is(blueprint.materials().roofSlab())) {
            return false;
        }
        boolean xBench = (isRoofSeat(scene, cell.x() - 1, 1, cell.z(), blueprint)
                        || isRoofSeat(scene, cell.x() + 1, 1, cell.z(), blueprint))
                && (hasBenchBack(scene, cell.x() - 1, cell.z() - 1, blueprint)
                        || hasBenchBack(scene, cell.x() + 1, cell.z() - 1, blueprint)
                        || hasBenchBack(scene, cell.x() - 1, cell.z() + 1, blueprint)
                        || hasBenchBack(scene, cell.x() + 1, cell.z() + 1, blueprint));
        boolean zBench = (isRoofSeat(scene, cell.x(), 1, cell.z() - 1, blueprint)
                        || isRoofSeat(scene, cell.x(), 1, cell.z() + 1, blueprint))
                && (hasBenchBack(scene, cell.x() - 1, cell.z() - 1, blueprint)
                        || hasBenchBack(scene, cell.x() - 1, cell.z() + 1, blueprint)
                        || hasBenchBack(scene, cell.x() + 1, cell.z() - 1, blueprint)
                        || hasBenchBack(scene, cell.x() + 1, cell.z() + 1, blueprint));
        return xBench || zBench;
    }

    private static boolean isRoofSeat(
            Map<BlockPos, AuthoredVillageStructures.Cell> scene,
            int x,
            int y,
            int z,
            AuthoredVillageStructures.Blueprint blueprint) {
        AuthoredVillageStructures.Cell candidate = scene.get(new BlockPos(x, y, z));
        return candidate != null
                && candidate.phase() == AuthoredVillageStructures.Phase.DECOR
                && (candidate.state().is(blueprint.materials().roofSlab())
                        || candidate.state().is(blueprint.materials().roofStairs()));
    }

    private static boolean hasBenchBack(
            Map<BlockPos, AuthoredVillageStructures.Cell> scene,
            int x,
            int z,
            AuthoredVillageStructures.Blueprint blueprint) {
        return isBlock(scene, x, 1, z, blueprint.materials().fence())
                && isBlock(scene, x, 2, z, blueprint.materials().timber());
    }

    private static boolean isCrateClusterAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        boolean exteriorCluster = cell.x() < 0
                || cell.x() >= blueprint.width()
                || cell.z() >= blueprint.depth();
        if (blueprint.revision() >= 3) {
            // Three distinguishable parcels share a thin pallet; the front-left note block is
            // the stable anchor, not its optional upper parcel or a nearby merchandise block.
            return exteriorCluster
                    && cell.phase() == AuthoredVillageStructures.Phase.DECOR
                    && cell.y() == 1
                    && cell.state().is(Blocks.NOTE_BLOCK)
                    && isBlock(scene, cell.x(), 0, cell.z(), blueprint.materials().roofSlab())
                    && isBlock(scene, cell.x() + 1, 0, cell.z(), blueprint.materials().roofSlab())
                    && isBlock(scene, cell.x() + 1, 1, cell.z(), blueprint.materials().wall())
                    && isBlock(scene, cell.x() + 1, 1, cell.z() + 1, Blocks.NOTE_BLOCK)
                    && isOpenTrapdoor(scene, cell.x(), 1, cell.z() - 1)
                    && isOpenTrapdoor(scene, cell.x() + 1, 1, cell.z() - 1);
        }
        return exteriorCluster
                && cell.phase() == AuthoredVillageStructures.Phase.DECOR
                && cell.y() == 2
                && cell.state().is(blueprint.materials().wall())
                && isBlock(scene, cell.x(), 1, cell.z(), blueprint.materials().roofSlab())
                && isBlock(scene, cell.x() + 1, 1, cell.z(),
                        blueprint.materials().roofSlab())
                && isBlock(scene, cell.x() + 1, 2, cell.z(),
                        blueprint.materials().timber())
                && scene.containsKey(new BlockPos(cell.x(), 0, cell.z()))
                && scene.containsKey(new BlockPos(cell.x() + 1, 0, cell.z()));
    }

    private static boolean isHitchingRailAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        return cell.phase() == AuthoredVillageStructures.Phase.FRAME
                && cell.y() == 1
                && cell.state().is(Blocks.IRON_CHAIN)
                && isBlock(scene, cell.x(), 2, cell.z(), blueprint.materials().fence())
                && isBlock(scene, cell.x(), 2, cell.z() - 1, blueprint.materials().fence())
                && isBlock(scene, cell.x(), 2, cell.z() + 1, blueprint.materials().fence());
    }

    /** Matches cart topology so safe non-container cargo can vary by project role. */
    private static boolean isHandCartCargoAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (blueprint.revision() >= 3) {
            if (cell.phase() != AuthoredVillageStructures.Phase.DECOR
                    || cell.y() != 2
                    || cell.state().is(Blocks.CHEST)
                    || cell.state().is(Blocks.BARREL)
                    || !isBlock(scene, cell.x(), 3, cell.z(), Blocks.RAIL)) {
                return false;
            }
            for (int dx = -1; dx <= 1; dx++) {
                AuthoredVillageStructures.Cell axle = scene.get(
                        new BlockPos(cell.x() + dx, 1, cell.z()));
                if (axle == null || !axle.state().is(BlockTags.LOGS)
                        || axle.state().getOptionalValue(RotatedPillarBlock.AXIS)
                                .orElse(null) != Direction.Axis.X) {
                    return false;
                }
            }
            return isBlock(scene, cell.x() - 2, 1, cell.z(), Blocks.STONE_BUTTON)
                    && isBlock(scene, cell.x() + 2, 1, cell.z(), Blocks.STONE_BUTTON)
                    && isOpenTrapdoor(scene, cell.x() - 1, 2, cell.z())
                    && isOpenTrapdoor(scene, cell.x() + 1, 2, cell.z())
                    && isBlock(scene, cell.x() - 1, 1, cell.z() + 2,
                            blueprint.materials().fence())
                    && isBlock(scene, cell.x() + 1, 1, cell.z() + 2,
                            blueprint.materials().fence());
        }
        if (cell.phase() != AuthoredVillageStructures.Phase.DECOR
                || cell.y() != 3
                || cell.state().is(Blocks.CHEST)
                || cell.state().is(Blocks.BARREL)
                || !isBlock(scene, cell.x(), 2, cell.z(), blueprint.materials().roofSlab())
                || !isBlock(scene, cell.x(), 1, cell.z(), blueprint.materials().timber())) {
            return false;
        }
        AuthoredVillageStructures.Cell leftWheel = scene.get(
                new BlockPos(cell.x() - 2, 1, cell.z()));
        AuthoredVillageStructures.Cell rightWheel = scene.get(
                new BlockPos(cell.x() + 2, 1, cell.z()));
        return leftWheel != null
                && rightWheel != null
                && leftWheel.state().getBlock() instanceof TrapDoorBlock
                && rightWheel.state().getBlock() instanceof TrapDoorBlock;
    }

    private static boolean isToolRackAnchor(
            AuthoredVillageStructures.Cell cell,
            AuthoredVillageStructures.Blueprint blueprint,
            Map<BlockPos, AuthoredVillageStructures.Cell> scene) {
        if (cell.phase() != AuthoredVillageStructures.Phase.FRAME
                || cell.y() != 1
                || (!cell.state().is(Blocks.IRON_BARS)
                        && !cell.state().is(Blocks.IRON_CHAIN))
                || !isToolRackHeader(scene, cell.x(), cell.z(), blueprint)) {
            return false;
        }
        boolean xRack = isBlock(scene, cell.x() - 1, 1, cell.z(),
                        blueprint.materials().fence())
                && isBlock(scene, cell.x() + 1, 1, cell.z(),
                        blueprint.materials().fence())
                && isToolRackHeader(scene, cell.x() - 1, cell.z(), blueprint)
                && isToolRackHeader(scene, cell.x() + 1, cell.z(), blueprint);
        boolean zRack = isBlock(scene, cell.x(), 1, cell.z() - 1,
                        blueprint.materials().fence())
                && isBlock(scene, cell.x(), 1, cell.z() + 1,
                        blueprint.materials().fence())
                && isToolRackHeader(scene, cell.x(), cell.z() - 1, blueprint)
                && isToolRackHeader(scene, cell.x(), cell.z() + 1, blueprint);
        return xRack || zRack;
    }

    private static boolean isToolRackHeader(
            Map<BlockPos, AuthoredVillageStructures.Cell> scene,
            int x,
            int z,
            AuthoredVillageStructures.Blueprint blueprint) {
        AuthoredVillageStructures.Cell highCap = scene.get(new BlockPos(x, 3, z));
        if (blueprint.revision() >= 3 && highCap != null
                && highCap.phase() == AuthoredVillageStructures.Phase.FRAME
                && highCap.state().is(blueprint.materials().roofSlab())
                && (isBlock(scene, x, 2, z, blueprint.materials().fence())
                        || isBlock(scene, x, 2, z, Blocks.IRON_BARS))) {
            return true;
        }
        // A rack directly beneath another authored feature intentionally retains its original
        // supported low lintel when no free high-cap cell exists. Match that exact form as well.
        AuthoredVillageStructures.Cell header = scene.get(new BlockPos(x, 2, z));
        return header != null
                && header.phase() == AuthoredVillageStructures.Phase.FRAME
                && (header.state().is(blueprint.materials().timber())
                        || header.state().is(blueprint.materials().roofSlab())
                        || header.state().is(blueprint.materials().roofStairs()));
    }

    private static boolean isOpenTrapdoor(
            Map<BlockPos, AuthoredVillageStructures.Cell> scene, int x, int y, int z) {
        AuthoredVillageStructures.Cell cell = scene.get(new BlockPos(x, y, z));
        return cell != null && cell.state().getBlock() instanceof TrapDoorBlock
                && cell.state().getValue(TrapDoorBlock.OPEN);
    }

    private static boolean isBlock(
            Map<BlockPos, AuthoredVillageStructures.Cell> scene,
            int x,
            int y,
            int z,
            Block block) {
        AuthoredVillageStructures.Cell candidate = scene.get(new BlockPos(x, y, z));
        return candidate != null && candidate.state().is(block);
    }

    private static boolean isPottedPlant(BlockState state) {
        return state.is(Blocks.POTTED_FERN)
                || state.is(Blocks.POTTED_DANDELION)
                || state.is(Blocks.POTTED_BLUE_ORCHID);
    }

    private static List<AuthoredVillageStructures.Cell> localizedDoodadCluster(
            List<AuthoredVillageStructures.Cell> matches,
            StructureGalleryReviewPlan.Doodad doodad) {
        if (doodad == StructureGalleryReviewPlan.Doodad.FREESTANDING_LAMP_POST
                || doodad == StructureGalleryReviewPlan.Doodad.FORECOURT_CARGO_PEDESTAL
                || doodad == StructureGalleryReviewPlan.Doodad.FORECOURT_PLANT_PEDESTAL) {
            return List.of(matches.getFirst());
        }
        List<AuthoredVillageStructures.Cell> remaining = new ArrayList<>(matches);
        List<AuthoredVillageStructures.Cell> best = List.of();
        while (!remaining.isEmpty()) {
            List<AuthoredVillageStructures.Cell> cluster = new ArrayList<>();
            cluster.add(remaining.removeFirst());
            for (int cursor = 0; cursor < cluster.size(); cursor++) {
                AuthoredVillageStructures.Cell current = cluster.get(cursor);
                for (int index = remaining.size() - 1; index >= 0; index--) {
                    AuthoredVillageStructures.Cell candidate = remaining.get(index);
                    if (cellDistance(current, candidate) == 1) {
                        cluster.add(remaining.remove(index));
                    }
                }
            }
            if (cluster.size() > best.size()) {
                best = List.copyOf(cluster);
            }
        }
        return best;
    }

    private static int cellDistance(
            AuthoredVillageStructures.Cell first,
            AuthoredVillageStructures.Cell second) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.y() - second.y())
                + Math.abs(first.z() - second.z());
    }

    private static LocalPoint averageLocalPoint(
            List<AuthoredVillageStructures.Cell> cells) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (AuthoredVillageStructures.Cell cell : cells) {
            x += cell.x() + 0.5;
            y += cell.y() + 0.5;
            z += cell.z() + 0.5;
        }
        double count = cells.size();
        return new LocalPoint(x / count, y / count, z / count);
    }

    private static BlockPos selectSecondaryInterior(
            BlockPos primary, List<BlockPos> candidates) {
        int highestDifferentFloor = candidates.stream()
                .mapToInt(BlockPos::getY)
                .filter(y -> y != primary.getY())
                .max()
                .orElse(primary.getY());
        List<BlockPos> preferred = candidates.stream()
                .filter(candidate -> highestDifferentFloor == primary.getY()
                        || candidate.getY() == highestDifferentFloor)
                .filter(candidate -> !candidate.equals(primary))
                .toList();
        return preferred.stream()
                .max(Comparator.comparingDouble(candidate -> spatialDistanceSquared(
                        primary, candidate)))
                .orElse(primary);
    }

    private static BlockPos selectPrimaryInterior(
            BlockPos entrance,
            List<BlockPos> authoredSamples,
            List<BlockPos> fallbacks) {
        if (authoredSamples.isEmpty()) {
            return entrance == null ? fallbacks.getFirst() : entrance;
        }
        int lowestFloor = authoredSamples.stream()
                .mapToInt(BlockPos::getY)
                .min()
                .orElse(authoredSamples.getFirst().getY());
        BlockPos reference = entrance == null ? authoredSamples.getFirst() : entrance;
        return authoredSamples.stream()
                .filter(sample -> sample.getY() == lowestFloor)
                .max(Comparator.comparingDouble(sample -> spatialDistanceSquared(
                        reference, sample)))
                .orElse(authoredSamples.getFirst());
    }

    private static BlockPos transformLocal(
            BlockPos local, int width, int depth, boolean mirrored, int rotation) {
        int x = mirrored ? width - 1 - local.getX() : local.getX();
        int z = local.getZ();
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new BlockPos(depth - 1 - z, local.getY(), x);
            case 2 -> new BlockPos(width - 1 - x, local.getY(), depth - 1 - z);
            case 3 -> new BlockPos(z, local.getY(), width - 1 - x);
            default -> new BlockPos(x, local.getY(), z);
        };
    }

    private static LocalPoint transformLocal(
            LocalPoint local, int width, int depth, boolean mirrored, int rotation) {
        double x = mirrored ? width - local.x() : local.x();
        double z = local.z();
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new LocalPoint(depth - z, local.y(), x);
            case 2 -> new LocalPoint(width - x, local.y(), depth - z);
            case 3 -> new LocalPoint(z, local.y(), width - x);
            default -> new LocalPoint(x, local.y(), z);
        };
    }

    private static CapturePose lookAt(
            double cameraX,
            double cameraY,
            double cameraZ,
            double targetX,
            double targetY,
            double targetZ) {
        double dx = targetX - cameraX;
        double dy = targetY - (cameraY + 1.62);
        double dz = targetZ - cameraZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = normalizeYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.001, horizontal)));
        return new CapturePose(
                cameraX,
                cameraY,
                cameraZ,
                yaw,
                pitch,
                GalleryCameraProjection.REVIEW_VERTICAL_FOV_DEGREES,
                GalleryCaptureIsolationPlan.GALLERY_CONTEXT);
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = second.getX() - first.getX();
        double dz = second.getZ() - first.getZ();
        return dx * dx + dz * dz;
    }

    private static double spatialDistanceSquared(BlockPos first, BlockPos second) {
        double dx = second.getX() - first.getX();
        double dy = second.getY() - first.getY();
        double dz = second.getZ() - first.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized > 180.0F ? normalized - 360.0F : normalized;
    }

    private record LocalPoint(double x, double y, double z) {
    }

    private record DoodadCameraProfile(
            double standoffDistance,
            double tangentDistance,
            double cameraFeetY,
            double targetYOffset) {
    }

    /** Builds the disposable review gallery before a quick-play client joins. */
    public static void autoBuildIfRequested(MinecraftServer server) {
        if (!enabled() || !Boolean.getBoolean(AUTO_BUILD_PROPERTY)) {
            return;
        }
        if (!isExactGalleryWorld(server)) {
            LOGGER.warn(
                    "Ignored gallery auto-build outside the exact disposable {} world",
                    WORLD_DIRECTORY);
            return;
        }

        ServerLevel level = server.overworld();
        try {
            int surfaceY = surfaceY(level);
            if (isBuilt(level)) {
                configureReviewWorld(server, level, surfaceY);
                LOGGER.info(
                        "Blueprint V2 gallery already contains all {} review structures",
                        StructureGalleryPlan.totalStructureCount());
                return;
            }
            LOGGER.info(
                    "Auto-building {} Blueprint V2 review structures; please wait",
                    StructureGalleryPlan.totalStructureCount());
            loadGalleryChunks(level);
            surfaceY = surfaceY(level);
            List<ResolvedFixture> fixtures = resolveFixtures(level, surfaceY);
            preflight(level, fixtures);
            int blocksPlaced = place(level, fixtures);
            writeCompletionMarker(level);
            configureReviewWorld(server, level, surfaceY);
            LOGGER.info(
                    "Blueprint V2 gallery ready: {} structures and {} block writes",
                    StructureGalleryPlan.totalStructureCount(),
                    blocksPlaced);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Gallery auto-build stopped safely; recreate the disposable flat world if partial blocks are visible",
                    exception);
        }
    }

    /** Allows gallery navigation without cheats, but only inside the opt-in disposable world. */
    static boolean hasCommandAccess(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        return enabled() && server != null && isExactGalleryWorld(server);
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("gallery")
                .requires(StructureGallery::hasCommandAccess)
                .then(Commands.literal("build")
                        .then(Commands.literal("confirm")
                                .executes(StructureGallery::build)))
                .then(Commands.literal("info")
                        .executes(StructureGallery::info))
                .then(Commands.literal("overview")
                        .executes(StructureGallery::overview))
                .then(Commands.literal("visit")
                        .then(Commands.argument(
                                        "index",
                                        IntegerArgumentType.integer(
                                                1,
                                                StructureGalleryPlan.totalStructureCount()))
                                .executes(StructureGallery::visit)));
    }

    private static int build(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!hasCommandAccess(source)) {
            return failure(source, "Gallery build refused: this is not the isolated gallery world.");
        }

        ServerLevel level = source.getServer().overworld();
        if (isBuilt(level)) {
            return success(
                    source,
                    "Gallery is already complete: "
                            + StructureGalleryPlan.totalStructureCount()
                            + " structures. Use /emerald gallery overview or visit <1-"
                            + StructureGalleryPlan.totalStructureCount() + ">.");
        }

        source.sendSuccess(
                () -> Component.literal(
                        "[Emerald Standard] Loading gallery plots and resolving "
                                + StructureGalleryPlan.totalStructureCount()
                                + " production blueprints; please wait..."),
                false);

        try {
            loadGalleryChunks(level);
            int surfaceY = surfaceY(level);
            List<ResolvedFixture> fixtures = resolveFixtures(level, surfaceY);
            preflight(level, fixtures);
            int blocksPlaced = place(level, fixtures);

            writeCompletionMarker(level);

            configureReviewWorld(source.getServer(), level, surfaceY);
            return success(
                    source,
                    "Built all " + StructureGalleryPlan.totalStructureCount()
                            + " production structures ("
                            + blocksPlaced
                            + " block writes). Use /emerald gallery visit <1-"
                            + StructureGalleryPlan.totalStructureCount() + "> or overview.");
        } catch (RuntimeException exception) {
            return failure(
                    source,
                    "Gallery build stopped safely: "
                            + safeMessage(exception)
                            + ". No completion marker was written; recreate this disposable flat world if any partial blocks are visible.");
        }
    }

    private static List<ResolvedFixture> resolveFixtures(ServerLevel level, int surfaceY) {
        List<ResolvedFixture> fixtures = new ArrayList<>(
                StructureGalleryPlan.totalStructureCount());
        for (int index = 0; index < StructureGalleryPlan.totalStructureCount(); index++) {
            fixtures.add(resolveFixture(level, surfaceY, index));
        }
        if (fixtures.size() != StructureGalleryPlan.totalStructureCount()) {
            throw new IllegalStateException("gallery plan resolved an unexpected fixture count");
        }
        return List.copyOf(fixtures);
    }

    /** Finished read-only expectations must never replay construction admission on an old layer. */
    private static List<ResolvedFixture> attachmentExpectationFixtures(int surfaceY) {
        List<ResolvedFixture> fixtures = new ArrayList<>(StructureGalleryPlan.totalStructureCount());
        for (StructureGalleryPlan.Entry entry : StructureGalleryPlan.entries()) {
            BlockPos origin = new BlockPos(entry.originX(), surfaceY, entry.originZ());
            fixtures.add(new ResolvedFixture(entry.index(), origin,
                    VillageProsperityManager.galleryProjectAttachmentExpectations(origin, entry)));
        }
        for (StructureGalleryPlan.BankEntry bank : StructureGalleryPlan.bankEntries()) {
            BlockPos origin = new BlockPos(bank.originX(), surfaceY, bank.originZ());
            fixtures.add(new ResolvedFixture(bank.index(), origin,
                    VillageProsperityManager.finalGalleryAttachmentExpectations(
                            VillageBankManager.galleryBankBlueprint(origin, bank.dialect()))));
        }
        return List.copyOf(fixtures);
    }

    private static ResolvedFixture resolveFixture(
            ServerLevel level, int surfaceY, int internalIndex) {
        if (internalIndex < 0
                || internalIndex >= StructureGalleryPlan.totalStructureCount()) {
            throw new IllegalArgumentException("Gallery fixture index is out of range");
        }
        StructureGalleryPlan.VisitTarget framing =
                StructureGalleryPlan.visitTargets().get(internalIndex);
        return resolveFixtureAt(
                level,
                surfaceY,
                internalIndex,
                new BlockPos(framing.originX(), surfaceY, framing.originZ()));
    }

    private static ResolvedFixture resolveFixtureAt(
            ServerLevel level,
            int surfaceY,
            int internalIndex,
            BlockPos origin) {
        if (internalIndex < 0
                || internalIndex >= StructureGalleryPlan.totalStructureCount()
                || origin.getY() != surfaceY) {
            throw new IllegalArgumentException("Invalid gallery fixture resolution request");
        }
        if (internalIndex < StructureGalleryPlan.entries().size()) {
            StructureGalleryPlan.Entry entry = StructureGalleryPlan.entries().get(internalIndex);
            return new ResolvedFixture(
                    entry.index(),
                    origin,
                    VillageProsperityManager.galleryProjectBlueprint(
                            level,
                            origin,
                            entry.type(),
                            entry.dialect(),
                            entry.character(),
                            entry.templateId(),
                            entry.templateRevision(),
                            entry.paletteId(),
                            entry.dressingId(),
                            entry.mirrored(),
                            entry.visualStage(),
                            entry.rotation()));
        }
        StructureGalleryPlan.BankEntry bank = StructureGalleryPlan.bankEntries().get(
                internalIndex - StructureGalleryPlan.entries().size());
        return new ResolvedFixture(
                bank.index(),
                origin,
                VillageBankManager.galleryBankBlueprint(origin, bank.dialect()));
    }

    private static void preflight(ServerLevel level, List<ResolvedFixture> fixtures) {
        Map<BlockPos, Integer> owners = new HashMap<>();
        for (ResolvedFixture fixture : fixtures) {
            for (StructureGalleryBlock block : fixture.blocks()) {
                Integer previousOwner = owners.putIfAbsent(block.position(), fixture.index());
                if (previousOwner != null && previousOwner != fixture.index()) {
                    throw new IllegalStateException(
                            "fixtures "
                                    + (previousOwner + 1)
                                    + " and "
                                    + (fixture.index() + 1)
                                    + " overlap at "
                                    + block.position().toShortString());
                }
                BlockState current = level.getBlockState(block.position());
                if (current.is(block.state().getBlock())) {
                    continue;
                }
                if (level.getBlockEntity(block.position()) != null
                        || !level.getFluidState(block.position()).isEmpty()
                        || (!current.isAir() && !current.canBeReplaced())) {
                    throw new IllegalStateException(
                            "plot is obstructed at " + block.position().toShortString());
                }
            }
        }
    }

    private static int place(ServerLevel level, List<ResolvedFixture> fixtures) {
        int writes = 0;
        for (ResolvedFixture fixture : fixtures) {
            for (StructureGalleryBlock block : fixture.blocks()) {
                BlockState current = level.getBlockState(block.position());
                if (current.is(block.state().getBlock())) {
                    continue;
                }
                level.setBlock(block.position(), block.state(), Block.UPDATE_ALL);
                if (!level.getBlockState(block.position()).is(block.state().getBlock())) {
                    throw new IllegalStateException(
                            "fixture "
                                    + (fixture.index() + 1)
                                    + " failed to place "
                                    + block.state()
                                    + " at "
                                    + block.position().toShortString());
                }
                writes++;
            }
        }
        for (ResolvedFixture fixture : fixtures) {
            VillageProsperityManager.normalizeGalleryConnections(level, fixture.blocks());
        }
        validateFragileGalleryAttachments(level, fixtures);
        return writes;
    }

    /** Read-only runtime check: decorative drops must fail review, never be hidden or respawned. */
    static boolean requiresAttachmentAudit(BlockState state) {
        return state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.RAIL);
    }

    private static void validateFragileGalleryAttachments(
            ServerLevel level, List<ResolvedFixture> fixtures) {
        int checked = 0;
        for (ResolvedFixture fixture : fixtures) {
            for (StructureGalleryBlock block : fixture.blocks()) {
                BlockState expected = block.state();
                if (!requiresAttachmentAudit(expected)) {
                    continue;
                }
                BlockState actual = level.getBlockState(block.position());
                validateAttachmentState(fixture.index(), block, actual,
                        actual.canSurvive(level, block.position()));
                checked++;
            }
        }
        LOGGER.info("Gallery runtime attachment audit passed: {} plants and rail bindings", checked);
    }

    static void validateAttachmentState(
            int fixtureIndex, StructureGalleryBlock expected, BlockState actual, boolean survives) {
        if (!actual.is(expected.state().getBlock()) || !survives) {
            throw new IllegalStateException("Gallery fixture " + (fixtureIndex + 1)
                    + " has a missing or unsupported authored attachment at "
                    + expected.position().toShortString() + ": expected " + expected.state()
                    + ", found " + actual);
        }
    }

    private static void configureReviewWorld(
            MinecraftServer server, ServerLevel level, int surfaceY) {
        server.getWorldData().setAllowCommands(true);
        server.getWorldData().setGameType(GameType.CREATIVE);
        server.getWorldData().setDifficulty(Difficulty.PEACEFUL);
        level.setRespawnData(LevelData.RespawnData.of(
                Level.OVERWORLD,
                new BlockPos(HUB_X, surfaceY, HUB_Z),
                (float) StructureGalleryPlan.reviewHubYawDegrees(),
                0.0F));
    }

    private static void writeCompletionMarker(ServerLevel level) {
        BlockPos marker = marker(level);
        setMarkerBlock(level, marker, Blocks.DIAMOND_BLOCK);
        long signature = StructureGalleryPlan.layoutSignature();
        for (int bit = 0; bit < COMPLETION_SIGNATURE_BITS; bit++) {
            Block block = (signature & (1L << bit)) == 0L
                    ? Blocks.GOLD_BLOCK
                    : Blocks.EMERALD_BLOCK;
            setMarkerBlock(level, marker.offset(bit + 1, 0, 0), block);
        }
    }

    private static void setMarkerBlock(ServerLevel level, BlockPos position, Block block) {
        if (!level.setBlock(position, block.defaultBlockState(), Block.UPDATE_ALL)
                && !level.getBlockState(position).is(block)) {
            throw new IllegalStateException("completion marker could not be saved");
        }
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int goldMasters = StructureGalleryPlan.goldMasterViewCount();
        int controlled = StructureGalleryPlan.controlledLabViewCount();
        success(source, "Blueprint V2 Gallery: "
                + StructureGalleryPlan.totalStructureCount() + " production structures.");
        success(source, "1-" + goldMasters
                + " full-stage, prosperous-dressed gold masters across all biome dialects; "
                + (goldMasters + 1) + "-" + (goldMasters + controlled)
                + " controlled stage/palette/dressing/mirror/rotation comparisons.");
        success(source, (goldMasters + controlled + 1) + "-"
                + StructureGalleryPlan.totalStructureCount() + " biome Bank variants.");
        success(source, "Use /emerald gallery visit <index> and /emerald gallery overview.");
        return 1;
    }

    private static int overview(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getServer().overworld();
        if (!isBuilt(level)) {
            return failure(context.getSource(), "Gallery has not been built yet.");
        }
        prepareReviewer(player);
        player.teleportTo(
                level,
                StructureGalleryPlan.WIDTH_BLOCKS / 2.0,
                Math.min(level.getMaxY() - 8.0, surfaceY(level) + OVERVIEW_Y_OFFSET),
                StructureGalleryPlan.DEPTH_BLOCKS / 2.0,
                Set.of(),
                90.0F,
                90.0F,
                true);
        return success(context.getSource(), "Overview: the full "
                + StructureGalleryPlan.WIDTH_BLOCKS + " x "
                + StructureGalleryPlan.DEPTH_BLOCKS + " block review grid.");
    }

    private static int visit(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getServer().overworld();
        if (!isBuilt(level)) {
            return failure(source, "Gallery has not been built yet.");
        }
        int userIndex = IntegerArgumentType.getInteger(context, "index");
        GalleryTarget target = target(userIndex);
        ServerPlayer player = source.getPlayerOrException();
        prepareReviewer(player);
        player.teleportTo(
                level,
                target.cameraX(),
                surfaceY(level) + target.cameraYOffset(),
                target.cameraZ(),
                Set.of(),
                target.yawDegrees(),
                target.pitchDegrees(),
                true);
        return success(source, target.description());
    }

    private static GalleryTarget target(int userIndex) {
        int internalIndex = userIndex - 1;
        StructureGalleryPlan.VisitTarget framing =
                StructureGalleryPlan.visitTargets().get(internalIndex);
        if (internalIndex < StructureGalleryPlan.entries().size()) {
            StructureGalleryPlan.Entry entry = StructureGalleryPlan.entries().get(internalIndex);
            return new GalleryTarget(
                    framing,
                    "#"
                            + userIndex
                            + " "
                            + entry.section()
                            + ": "
                            + display(entry.type().name())
                            + " / "
                            + display(entry.dialect().id())
                            + " / "
                            + display(entry.character().id())
                            + " / stage "
                            + entry.visualStage()
                            + " / rotation "
                            + entry.rotation()
                            + " / template "
                            + entry.templateId()
                            + "@"
                            + entry.templateRevision()
                            + " / palette "
                            + entry.paletteId()
                            + " / dressing "
                            + entry.dressingId()
                            + (entry.mirrored() ? " / mirrored" : ""));
        }
        StructureGalleryPlan.BankEntry bank = StructureGalleryPlan.bankEntries().get(
                internalIndex - StructureGalleryPlan.entries().size());
        return new GalleryTarget(
                framing,
                "#" + userIndex + " Bank / " + display(bank.dialect().id()));
    }

    private static void prepareReviewer(ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(0.12F);
        player.onUpdateAbilities();
    }

    private static boolean isBuilt(ServerLevel level) {
        BlockPos marker = marker(level);
        if (!level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)) {
            return false;
        }
        long signature = StructureGalleryPlan.layoutSignature();
        for (int bit = 0; bit < COMPLETION_SIGNATURE_BITS; bit++) {
            Block expected = (signature & (1L << bit)) == 0L
                    ? Blocks.GOLD_BLOCK
                    : Blocks.EMERALD_BLOCK;
            if (!level.getBlockState(marker.offset(bit + 1, 0, 0)).is(expected)) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos marker(ServerLevel level) {
        return new BlockPos(-64, level.getMinY() + 1, -64);
    }

    private static int surfaceY(ServerLevel level) {
        level.getChunk(
                Math.floorDiv(SURFACE_PROBE_X, 16),
                Math.floorDiv(SURFACE_PROBE_Z, 16));
        return level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SURFACE_PROBE_X,
                SURFACE_PROBE_Z);
    }

    private static void loadGalleryChunks(ServerLevel level) {
        int minimumChunk = -2;
        int maximumChunkX = Math.floorDiv(StructureGalleryPlan.WIDTH_BLOCKS + 31, 16);
        int maximumChunkZ = Math.floorDiv(StructureGalleryPlan.DEPTH_BLOCKS + 31, 16);
        for (int chunkX = minimumChunk; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunk; chunkZ <= maximumChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static boolean isExactGalleryWorld(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path fileName = worldRoot.getFileName();
        return fileName != null && WORLD_DIRECTORY.equals(fileName.toString());
    }

    private static int success(CommandSourceStack source, String text) {
        source.sendSuccess(
                () -> Component.literal("[Emerald Standard] " + text),
                false);
        return 1;
    }

    private static int failure(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal("[Emerald Standard] " + text));
        return 0;
    }

    private static String display(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record ResolvedFixture(
            int index, BlockPos origin, List<StructureGalleryBlock> blocks) {
        private ResolvedFixture {
            origin = origin.immutable();
            blocks = List.copyOf(blocks);
        }
    }

    private record IsolatedCaptureFixture(
            StructureGalleryPlan.VisitTarget framing,
            SubjectBounds subject,
            boolean rear,
            boolean standaloneBank) {
    }

    private static final class NoClearExteriorCameraException extends IllegalStateException {
        private NoClearExteriorCameraException(String message) {
            super(message);
        }
    }

    private record ExteriorCameraCandidate(
            CapturePose pose, double framingPenalty, double departure, int order) {
    }

    /** Compact fail-closed telemetry for diagnosing a gallery layout with no admissible pose. */
    private static final class ExteriorSearchDiagnostics {
        private int viewportCandidates;
        private int apronRejected;
        private int fitRejected;
        private int centerSightRejected;
        private int shoulderNearFieldRejected;
        private int shoulderSightRejected;
        private int rayGridCandidates;
        private int minimumForeignRays = Integer.MAX_VALUE;
        private CapturePose minimumForeignRayPose;
        private final Map<BlockPos, Integer> foreignBlocks = new HashMap<>();

        private void observeForeignRays(int foreignRays, CapturePose pose) {
            rayGridCandidates++;
            if (foreignRays < minimumForeignRays) {
                minimumForeignRays = foreignRays;
                minimumForeignRayPose = pose;
            }
        }

        private void observeForeignBlock(BlockPos position) {
            foreignBlocks.merge(position.immutable(), 1, Integer::sum);
        }

        private String summary() {
            String raySummary = rayGridCandidates == 0
                    ? "rayGrid=unreached"
                    : "rayGrid=" + rayGridCandidates
                            + ", minForeignRays=" + minimumForeignRays
                            + ", bestRayPose=" + minimumForeignRayPose
                            + ", commonForeign=" + foreignBlocks.entrySet().stream()
                                    .max(Map.Entry.comparingByValue())
                                    .map(entry -> entry.getKey().toShortString()
                                            + "x" + entry.getValue())
                                    .orElse("none");
            return "viewport=" + viewportCandidates
                    + ", apron=" + apronRejected
                    + ", fit=" + fitRejected
                    + ", centerSight=" + centerSightRejected
                    + ", shoulderNear=" + shoulderNearFieldRejected
                    + ", shoulderSight=" + shoulderSightRejected
                    + ", " + raySummary;
        }
    }

    private record SubjectBounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ,
            List<GalleryCameraProjection.Point> framingBlocks,
            List<GalleryCameraProjection.Point> raisedSceneBlocks,
            List<GalleryCameraProjection.Point> doodadBlocks,
            Set<BlockPos> activeFixtureBlocks,
            boolean allowInwardSearch) {
        private SubjectBounds {
            if (maximumX <= minimumX || maximumY <= minimumY || maximumZ <= minimumZ) {
                throw new IllegalArgumentException("Exterior subject bounds must have volume");
            }
            framingBlocks = List.copyOf(framingBlocks);
            raisedSceneBlocks = List.copyOf(raisedSceneBlocks);
            doodadBlocks = List.copyOf(doodadBlocks);
            activeFixtureBlocks = Set.copyOf(activeFixtureBlocks);
            if (framingBlocks.isEmpty()) {
                throw new IllegalArgumentException("Exterior subject must have framing blocks");
            }
            if (raisedSceneBlocks.isEmpty()) {
                throw new IllegalArgumentException("Exterior subject must have scene blocks");
            }
            if (allowInwardSearch && doodadBlocks.isEmpty()) {
                throw new IllegalArgumentException("Rear subject must have doodad blocks");
            }
            if (activeFixtureBlocks.isEmpty()) {
                throw new IllegalArgumentException("Exterior subject must own authored blocks");
            }
        }

        private double centerX() {
            return (minimumX + maximumX) / 2.0;
        }

        private double centerZ() {
            return (minimumZ + maximumZ) / 2.0;
        }

        private double width() {
            return maximumX - minimumX;
        }

        private double height() {
            return maximumY - minimumY;
        }

        private double depth() {
            return maximumZ - minimumZ;
        }

        private SubjectBounds translated(int translationX, int translationZ) {
            List<GalleryCameraProjection.Point> translatedFraming = framingBlocks.stream()
                    .map(point -> new GalleryCameraProjection.Point(
                            point.x() + translationX,
                            point.y(),
                            point.z() + translationZ))
                    .toList();
            List<GalleryCameraProjection.Point> translatedScene = raisedSceneBlocks.stream()
                    .map(point -> new GalleryCameraProjection.Point(
                            point.x() + translationX,
                            point.y(),
                            point.z() + translationZ))
                    .toList();
            List<GalleryCameraProjection.Point> translatedDoodads = doodadBlocks.stream()
                    .map(point -> new GalleryCameraProjection.Point(
                            point.x() + translationX,
                            point.y(),
                            point.z() + translationZ))
                    .toList();
            Set<BlockPos> translatedFixture = activeFixtureBlocks.stream()
                    .map(position -> position.offset(translationX, 0, translationZ).immutable())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new SubjectBounds(
                    minimumX + translationX,
                    maximumX + translationX,
                    minimumY,
                    maximumY,
                    minimumZ + translationZ,
                    maximumZ + translationZ,
                    translatedFraming,
                    translatedScene,
                    translatedDoodads,
                    translatedFixture,
                    allowInwardSearch);
        }
    }

    private record GalleryTarget(
            StructureGalleryPlan.VisitTarget framing, String description) {
        private double cameraX() {
            return framing.cameraX();
        }

        private double cameraYOffset() {
            return framing.cameraYOffset();
        }

        private double cameraZ() {
            return framing.cameraZ();
        }

        private float yawDegrees() {
            return framing.yawDegrees();
        }

        private float pitchDegrees() {
            return framing.pitchDegrees();
        }
    }

    /** Exact world-space camera pose returned to the isolated client capture harness. */
    public record CapturePose(
            double x,
            double y,
            double z,
            float yawDegrees,
            float pitchDegrees,
            double verticalFovDegrees,
            String captureContext) {
        public CapturePose {
            if (!Double.isFinite(verticalFovDegrees)
                    || verticalFovDegrees < 50.0
                    || verticalFovDegrees > GalleryCameraProjection.REVIEW_VERTICAL_FOV_DEGREES) {
                throw new IllegalArgumentException(
                        "Gallery capture vertical FOV must be between 50 and 70 degrees");
            }
            if (!GalleryCaptureIsolationPlan.GALLERY_CONTEXT.equals(captureContext)
                    && !GalleryCaptureIsolationPlan.ISOLATED_CLONE_CONTEXT.equals(
                            captureContext)) {
                throw new IllegalArgumentException("Unknown gallery capture context");
            }
        }

        private CapturePose withVerticalFov(double verticalFovDegrees) {
            return new CapturePose(
                    x,
                    y,
                    z,
                    yawDegrees,
                    pitchDegrees,
                    verticalFovDegrees,
                    captureContext);
        }

        private CapturePose withCaptureContext(String captureContext) {
            return new CapturePose(
                    x,
                    y,
                    z,
                    yawDegrees,
                    pitchDegrees,
                    verticalFovDegrees,
                    captureContext);
        }
    }
}
