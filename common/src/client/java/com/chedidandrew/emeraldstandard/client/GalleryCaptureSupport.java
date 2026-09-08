package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import com.chedidandrew.emeraldstandard.core.StructureGalleryReviewPlan;
import com.chedidandrew.emeraldstandard.minecraft.StructureGallery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * Disposable-gallery-only screenshot runner used by the authored-structure quality review.
 *
 * <p>Merely loading this class does nothing. The server-side movement method independently checks
 * the gallery opt-in, the capture opt-in, the exact disposable save name, and the current gallery
 * completion signature before it moves a player. As a result, an accidentally copied client JVM
 * option cannot automate a normal world.</p>
 */
public final class GalleryCaptureSupport {
    public static final String CAPTURE_SCOPE_PROPERTY =
            "the_emerald_standard.structureGallery.capture.scope";
    public static final String CAPTURE_PASS_PROPERTY =
            "the_emerald_standard.structureGallery.capture.reviewPass";
    public static final String CAPTURE_LIMIT_PROPERTY =
            "the_emerald_standard.structureGallery.capture.limit";
    public static final String CAPTURE_START_SEQUENCE_PROPERTY =
            "the_emerald_standard.structureGallery.capture.startSequence";
    public static final String STOP_WHEN_COMPLETE_PROPERTY =
            "the_emerald_standard.structureGallery.capture.stopWhenComplete";

    private static final String COMPLETE_SCOPE = "complete";
    private static final String CANONICAL_SCOPE = "canonical";
    private static final String DOODAD_SCOPE = "doodads";
    // v3 adds audited per-row FOV/context/pixel dimensions plus decoded full-HD enforcement.
    // Keep this namespace distinct from older v2 manifests that used shorter, incompatible rows.
    private static final int CAPTURE_SCHEMA_REVISION = 3;
    private static final int CAPTURE_PIXEL_WIDTH = 1920;
    private static final int CAPTURE_PIXEL_HEIGHT = 1080;
    private static final long READY_TIMEOUT_SECONDS = 180L;
    private static final long PREFLIGHT_TIMEOUT_SECONDS = 300L;
    private static final long COMPLETE_PREFLIGHT_TIMEOUT_SECONDS = 900L;
    private static final long TELEPORT_TIMEOUT_SECONDS = 30L;
    private static final long SCREENSHOT_TIMEOUT_SECONDS = 30L;
    private static final long POSITION_TIMEOUT_MILLIS = 15_000L;
    private static final long SETTLE_MILLIS = 2_000L;
    private static final long CLEAN_VIEWPORT_FRAME_MILLIS = 50L;

    private static boolean started;

    private GalleryCaptureSupport() {
    }

    /** Starts at most one capture worker in this client process, and only under the capture flag. */
    public static synchronized void initialized(Logger logger) {
        if (started || !Boolean.getBoolean(StructureGallery.CAPTURE_PROPERTY)) {
            return;
        }
        started = true;
        Thread worker = new Thread(
                () -> capture(logger),
                "emerald-standard-gallery-capture");
        worker.setDaemon(true);
        worker.start();
    }

    private static void capture(Logger logger) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean restoreHud = false;
        try {
            CaptureConfiguration configuration = CaptureConfiguration.fromSystemProperties();
            ReadyClient ready = awaitReadyClient(minecraft);
            CaptureDimensions dimensions = ensureCaptureDimensions(minecraft);
            List<StructureGalleryReviewPlan.Shot> scopeShots = reviewShots(configuration.scope());
            List<StructureGalleryReviewPlan.Shot> eligibleShots = shotsFromSequence(
                    scopeShots, configuration.startSequence());
            List<StructureGalleryReviewPlan.Shot> shots = eligibleShots.stream()
                    .limit(Math.min(configuration.limit(), scopeShots.size()))
                    .toList();
            long preflightTimeoutSeconds = COMPLETE_SCOPE.equals(configuration.scope())
                    ? COMPLETE_PREFLIGHT_TIMEOUT_SECONDS
                    : PREFLIGHT_TIMEOUT_SECONDS;

            // Resolve the whole itinerary before creating even an output directory. This catches
            // stale authored-prop signatures and cameras embedded in newly expanded structures
            // atomically instead of abandoning a nominal pass after hundreds of valid frames.
            onServer(ready.server(), preflightTimeoutSeconds, () -> {
                StructureGallery.validateCapturePlan(ready.server(), shots);
                return null;
            });

            // A first-time gallery player receives the handbook just like an ordinary player.
            // Its book screen can otherwise cover frame one even though the HUD is hidden. Close
            // the transient screen explicitly and prove it stayed closed before moving the first
            // camera; later frames independently fail closed if any screen appears again.
            dismissTransientScreen(minecraft);

            // The first teleport crosses the same safety boundary again and establishes the
            // initial camera pose without weakening the server-side guard.
            StructureGalleryReviewPlan.Shot first = shots.getFirst();
            StructureGallery.CapturePose firstPose = onServer(
                    ready.server(),
                    () -> StructureGallery.teleportForCapture(
                            ready.server(), ready.playerId(), first));

            // Preserve the reviewer's setting, but ensure the evidence contains only the world.
            // The direct HUD API is deterministic and avoids depending on a user's F1 binding.
            boolean hudWasHidden = onClient(minecraft, () -> minecraft.gui.hud.isHidden());
            restoreHud = !hudWasHidden;
            onClient(minecraft, () -> {
                minecraft.gui.hud.getChat().clearMessages(true);
                if (!minecraft.gui.hud.isHidden()) {
                    minecraft.gui.hud.toggle();
                }
                return null;
            });

            CaptureOutput output = CaptureOutput.create(
                    minecraft,
                    configuration,
                    shots.size(),
                    shots.size() < scopeShots.size(),
                    shots.getFirst().sequence(),
                    shots.getLast().sequence(),
                    dimensions);
            logger.info(
                    "Starting isolated structure review capture: {} shots, scope {}, pass {}, output {}",
                    shots.size(),
                    configuration.scope(),
                    configuration.reviewPass(),
                    output.directory());

            captureShot(minecraft, first, firstPose, output);
            for (int index = 1; index < shots.size(); index++) {
                StructureGalleryReviewPlan.Shot shot = shots.get(index);
                StructureGallery.CapturePose pose = onServer(
                        ready.server(),
                        () -> StructureGallery.teleportForCapture(
                                ready.server(),
                                ready.playerId(),
                                shot));
                captureShot(minecraft, shot, pose, output);
                if ((index + 1) % 10 == 0 || index + 1 == shots.size()) {
                    logger.info(
                            "Structure review capture progress: {}/{}",
                            index + 1,
                            shots.size());
                }
            }

            output.complete();
            logger.info(
                    "Structure review capture complete: {} screenshots and manifest {}",
                    shots.size(),
                    output.manifest());
        } catch (Throwable failure) {
            logger.error("Isolated structure review capture failed", failure);
        } finally {
            if (restoreHud) {
                try {
                    onClient(minecraft, () -> {
                        if (minecraft.gui.hud.isHidden()) {
                            minecraft.gui.hud.toggle();
                        }
                        return null;
                    });
                } catch (Exception restoreFailure) {
                    logger.warn("Could not restore the gallery reviewer's HUD", restoreFailure);
                }
            }
            if (Boolean.getBoolean(STOP_WHEN_COMPLETE_PROPERTY)) {
                minecraft.execute(minecraft::stop);
            }
        }
    }

    private static List<StructureGalleryReviewPlan.Shot> reviewShots(String scope) {
        if (COMPLETE_SCOPE.equals(scope)) {
            return StructureGalleryReviewPlan.shots();
        }
        if (DOODAD_SCOPE.equals(scope)) {
            return StructureGalleryReviewPlan.shots().stream()
                    .filter(shot -> shot.coverage()
                            == StructureGalleryReviewPlan.Coverage.DOODAD_DETAIL)
                    .toList();
        }
        return StructureGalleryReviewPlan.shots().stream()
                .filter(shot -> shot.coverage()
                        != StructureGalleryReviewPlan.Coverage.BIOME_COHESION)
                .toList();
    }

    /** Selects an exact stable sequence for bounded camera proofs without replaying earlier shots. */
    private static List<StructureGalleryReviewPlan.Shot> shotsFromSequence(
            List<StructureGalleryReviewPlan.Shot> scopeShots, int startSequence) {
        if (startSequence == 0) {
            return scopeShots;
        }
        for (int index = 0; index < scopeShots.size(); index++) {
            if (scopeShots.get(index).sequence() == startSequence) {
                return scopeShots.subList(index, scopeShots.size());
            }
        }
        throw new IllegalArgumentException(
                "Gallery capture start sequence " + startSequence
                        + " is not present in the selected scope");
    }

    private static ReadyClient awaitReadyClient(Minecraft minecraft) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READY_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            ReadyClient ready = onClient(minecraft, () -> {
                MinecraftServer server = minecraft.getSingleplayerServer();
                if (minecraft.player == null || minecraft.level == null || server == null) {
                    return null;
                }
                return new ReadyClient(server, minecraft.player.getUUID());
            });
            if (ready != null) {
                return ready;
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("Gallery client did not join its integrated server in time");
    }

    private static void captureShot(
            Minecraft minecraft,
            StructureGalleryReviewPlan.Shot shot,
            StructureGallery.CapturePose pose,
            CaptureOutput output) throws Exception {
        awaitClientPose(minecraft, pose);
        Thread.sleep(SETTLE_MILLIS);
        prepareCleanViewportForCapture(minecraft, pose);
        takeScreenshot(
                minecraft,
                pose,
                output.relativeScreenshotName(shot.filename()),
                output.file(shot),
                output.dimensions());
        output.record(shot, pose, "captured");
    }

    /** Reasserts the capture-only presentation state, then gives the renderer one stable frame. */
    private static void prepareCleanViewportForCapture(
            Minecraft minecraft, StructureGallery.CapturePose pose) throws Exception {
        onClient(minecraft, () -> {
            if (minecraft.gui.screen() != null) {
                minecraft.gui.setScreen(null);
            }
            if (minecraft.gui.overlay() != null) {
                minecraft.gui.setOverlay(null);
            }
            // Saved gallery options must not silently widen the evidence viewport. Minecraft's
            // FOV option is vertical degrees. The server-side preflight normally uses 70 and may
            // select a bounded 65/60/55/50-degree optical fallback when a dense gallery neighbour
            // otherwise enters the viewport; render exactly the pose that passed preflight.
            minecraft.options.fov().set((int) Math.round(pose.verticalFovDegrees()));
            minecraft.gui.hud.getChat().clearMessages(true);
            if (!minecraft.gui.hud.isHidden()) {
                minecraft.gui.hud.toggle();
            }
            return null;
        });
        Thread.sleep(CLEAN_VIEWPORT_FRAME_MILLIS);
    }

    private static void dismissTransientScreen(Minecraft minecraft) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            Boolean clear = onClient(minecraft, () -> {
                if (minecraft.gui.screen() != null) {
                    minecraft.gui.setScreen(null);
                }
                if (minecraft.gui.overlay() != null) {
                    minecraft.gui.setOverlay(null);
                }
                return minecraft.gui.screen() == null && minecraft.gui.overlay() == null;
            });
            if (Boolean.TRUE.equals(clear)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new IllegalStateException("Gallery onboarding screen could not be dismissed");
    }

    /** Forces and proves a stable 16:9 evidence framebuffer before server-side camera preflight. */
    private static CaptureDimensions ensureCaptureDimensions(Minecraft minecraft) throws Exception {
        onClient(minecraft, () -> {
            minecraft.getWindow().setWindowed(CAPTURE_PIXEL_WIDTH, CAPTURE_PIXEL_HEIGHT);
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15L);
        while (System.nanoTime() < deadline) {
            CaptureDimensions dimensions = onClient(minecraft, () -> new CaptureDimensions(
                    minecraft.gameRenderer.mainRenderTarget().width,
                    minecraft.gameRenderer.mainRenderTarget().height));
            if (dimensions.width() == CAPTURE_PIXEL_WIDTH
                    && dimensions.height() == CAPTURE_PIXEL_HEIGHT) {
                return dimensions;
            }
            Thread.sleep(50L);
        }
        CaptureDimensions actual = onClient(minecraft, () -> new CaptureDimensions(
                minecraft.gameRenderer.mainRenderTarget().width,
                minecraft.gameRenderer.mainRenderTarget().height));
        throw new IllegalStateException(
                "Gallery capture framebuffer did not reach "
                        + CAPTURE_PIXEL_WIDTH + "x" + CAPTURE_PIXEL_HEIGHT
                        + "; actual " + actual.width() + "x" + actual.height());
    }

    private static void awaitClientPose(
            Minecraft minecraft, StructureGallery.CapturePose expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(POSITION_TIMEOUT_MILLIS);
        while (System.nanoTime() < deadline) {
            Boolean ready = onClient(minecraft, () -> {
                if (minecraft.player == null || minecraft.level == null) {
                    return false;
                }
                double dx = minecraft.player.getX() - expected.x();
                double dy = minecraft.player.getY() - expected.y();
                double dz = minecraft.player.getZ() - expected.z();
                BlockPos expectedBlock = BlockPos.containing(
                        expected.x(), expected.y(), expected.z());
                return dx * dx + dy * dy + dz * dz < 0.25
                        && minecraft.level.hasChunk(
                                Math.floorDiv(expectedBlock.getX(), 16),
                                Math.floorDiv(expectedBlock.getZ(), 16));
            });
            if (Boolean.TRUE.equals(ready)) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("Client camera did not reach the gallery capture pose");
    }

    private static void takeScreenshot(
            Minecraft minecraft,
            StructureGallery.CapturePose pose,
            String relativeName,
            Path expectedFile,
            CaptureDimensions expectedDimensions) throws Exception {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        minecraft.execute(() -> {
            // Check in the same render-thread task that starts the PNG write. A screen or HUD
            // appearing after the one-frame settle is a rejected frame, never consumed evidence.
            if (minecraft.gui.screen() != null
                    || minecraft.gui.overlay() != null
                    || !minecraft.gui.hud.isHidden()) {
                saved.completeExceptionally(new IllegalStateException(
                        "Gallery capture viewport is obscured by a screen or visible HUD"));
                return;
            }
            int expectedFov = (int) Math.round(pose.verticalFovDegrees());
            if (minecraft.options.fov().get() != expectedFov) {
                saved.completeExceptionally(new IllegalStateException(
                        "Gallery capture FOV changed after preflight: expected "
                                + expectedFov + " but was " + minecraft.options.fov().get()));
                return;
            }
            int actualWidth = minecraft.gameRenderer.mainRenderTarget().width;
            int actualHeight = minecraft.gameRenderer.mainRenderTarget().height;
            if (actualWidth != expectedDimensions.width()
                    || actualHeight != expectedDimensions.height()) {
                saved.completeExceptionally(new IllegalStateException(
                        "Gallery capture framebuffer changed after preflight: expected "
                                + expectedDimensions.width() + "x" + expectedDimensions.height()
                                + " but was " + actualWidth + "x" + actualHeight));
                return;
            }
            Screenshot.grab(
                    minecraft.gameDirectory,
                    relativeName,
                    minecraft.gameRenderer.mainRenderTarget(),
                    1,
                    ignored -> saved.complete(null));
        });
        saved.get(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!Files.isRegularFile(expectedFile) || Files.size(expectedFile) <= 0L) {
            throw new IOException("Screenshot was not written: " + expectedFile);
        }
        java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(expectedFile.toFile());
        if (decoded == null
                || decoded.getWidth() != expectedDimensions.width()
                || decoded.getHeight() != expectedDimensions.height()
                || decoded.getWidth() < CAPTURE_PIXEL_WIDTH
                || decoded.getHeight() < CAPTURE_PIXEL_HEIGHT) {
            throw new IOException(
                    "Screenshot dimensions are not valid 1920x1080 evidence: " + expectedFile);
        }
    }

    private static <T> T onClient(Minecraft minecraft, Supplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        minecraft.execute(() -> complete(future, supplier));
        return future.get(TELEPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> supplier) throws Exception {
        return onServer(server, TELEPORT_TIMEOUT_SECONDS, supplier);
    }

    private static <T> T onServer(
            MinecraftServer server,
            long timeoutSeconds,
            Supplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> complete(future, supplier));
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private static <T> void complete(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
    }

    private record ReadyClient(MinecraftServer server, UUID playerId) {
    }

    private record CaptureDimensions(int width, int height) {
    }

    private record CaptureConfiguration(
            String scope, int reviewPass, int limit, int startSequence) {
        private static CaptureConfiguration fromSystemProperties() {
            String scope = System.getProperty(CAPTURE_SCOPE_PROPERTY, COMPLETE_SCOPE)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!COMPLETE_SCOPE.equals(scope)
                    && !CANONICAL_SCOPE.equals(scope)
                    && !DOODAD_SCOPE.equals(scope)) {
                throw new IllegalArgumentException(
                        "Gallery capture scope must be 'canonical', 'complete', or 'doodads'");
            }
            int reviewPass;
            try {
                reviewPass = Integer.parseInt(System.getProperty(CAPTURE_PASS_PROPERTY, "1"));
            } catch (NumberFormatException invalidPass) {
                throw new IllegalArgumentException("Gallery review pass must be an integer", invalidPass);
            }
            if (reviewPass < 1 || reviewPass > 5) {
                throw new IllegalArgumentException("Gallery review pass must be between 1 and 5");
            }
            int limit;
            try {
                limit = Integer.parseInt(System.getProperty(
                        CAPTURE_LIMIT_PROPERTY,
                        Integer.toString(Integer.MAX_VALUE)));
            } catch (NumberFormatException invalidLimit) {
                throw new IllegalArgumentException("Gallery capture limit must be an integer", invalidLimit);
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("Gallery capture limit must be positive");
            }
            int startSequence = 0;
            String requestedStart = System.getProperty(
                    CAPTURE_START_SEQUENCE_PROPERTY, "").trim();
            if (!requestedStart.isEmpty()) {
                try {
                    startSequence = Integer.parseInt(requestedStart);
                } catch (NumberFormatException invalidStart) {
                    throw new IllegalArgumentException(
                            "Gallery capture start sequence must be an integer", invalidStart);
                }
                if (startSequence <= 0) {
                    throw new IllegalArgumentException(
                            "Gallery capture start sequence must be positive");
                }
            }
            return new CaptureConfiguration(scope, reviewPass, limit, startSequence);
        }
    }

    private static final class CaptureOutput {
        private static final String MANIFEST_HEADER =
                "sequence,coverage,gallery_index,view,vertical_fov,capture_context,"
                        + "pixel_width,pixel_height,subject,dialect,doodads,"
                        + "doodad_focus,doodad_source_view,filename,status\n";

        private final String relativeDirectory;
        private final Path directory;
        private final Path manifest;
        private final int expectedShots;
        private final CaptureDimensions dimensions;

        private CaptureOutput(
                String relativeDirectory,
                Path directory,
                Path manifest,
                int expectedShots,
                CaptureDimensions dimensions) {
            this.relativeDirectory = relativeDirectory;
            this.directory = directory;
            this.manifest = manifest;
            this.expectedShots = expectedShots;
            this.dimensions = dimensions;
        }

        private static CaptureOutput create(
                Minecraft minecraft,
                CaptureConfiguration configuration,
                int expectedShots,
                boolean partial,
                int firstSequence,
                int lastSequence,
                CaptureDimensions dimensions) throws IOException {
            String signature = Long.toUnsignedString(StructureGalleryPlan.layoutSignature(), 16);
            String relativeDirectory = String.format(
                    Locale.ROOT,
                    "tes-structure-review-%s-capture-v%d/%s%s-pass-%02d",
                    signature,
                    CAPTURE_SCHEMA_REVISION,
                    configuration.scope(),
                    partial
                            ? String.format(
                                    Locale.ROOT,
                                    "-smoke-%03d-%03d",
                                    firstSequence,
                                    lastSequence)
                            : "",
                    configuration.reviewPass());
            Path directory = minecraft.gameDirectory.toPath()
                    .resolve("screenshots")
                    .resolve(relativeDirectory)
                    .toAbsolutePath()
                    .normalize();
            Path manifest = directory.resolve("manifest.csv");
            boolean directoryHasOutput = false;
            if (Files.isDirectory(directory)) {
                try (Stream<Path> contents = Files.list(directory)) {
                    directoryHasOutput = contents.findAny().isPresent();
                }
            }
            if (Files.exists(manifest) || directoryHasOutput) {
                throw new IOException(
                        "Capture pass already contains output; choose the next review pass: "
                                + directory);
            }
            Files.createDirectories(directory);
            Files.writeString(
                    manifest,
                    MANIFEST_HEADER,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            return new CaptureOutput(
                    relativeDirectory, directory, manifest, expectedShots, dimensions);
        }

        private String relativeScreenshotName(String filename) {
            return Path.of(relativeDirectory, filename).toString();
        }

        private Path file(StructureGalleryReviewPlan.Shot shot) {
            return directory.resolve(shot.filename());
        }

        private void record(
                StructureGalleryReviewPlan.Shot shot,
                StructureGallery.CapturePose pose,
                String status)
                throws IOException {
            String row = String.join(",",
                    Integer.toString(shot.sequence()),
                    shot.coverage().id(),
                    Integer.toString(shot.galleryIndex()),
                    shot.view().id(),
                    Double.toString(pose.verticalFovDegrees()),
                    csv(pose.captureContext()),
                    Integer.toString(dimensions.width()),
                    Integer.toString(dimensions.height()),
                    csv(shot.subject()),
                    csv(shot.dialect()),
                    csv(shot.doodads().stream()
                            .map(StructureGalleryReviewPlan.Doodad::id)
                            .reduce((left, right) -> left + ";" + right)
                            .orElse("")),
                    csv(shot.doodadDetail() == null
                            ? ""
                            : shot.doodadDetail().doodad().id()),
                    csv(shot.doodadDetail() == null
                            ? ""
                            : shot.doodadDetail().sourceView().id()),
                    csv(shot.filename()),
                    csv(status)) + "\n";
            Files.writeString(
                    manifest,
                    row,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        }

        private void complete() throws IOException {
            Files.writeString(
                    directory.resolve("capture-complete.txt"),
                    "layout_signature="
                            + Long.toUnsignedString(StructureGalleryPlan.layoutSignature(), 16)
                            + "\ngallery_content_revision="
                            + StructureGalleryPlan.GALLERY_CONTENT_REVISION
                            + "\ncapture_schema_revision=" + CAPTURE_SCHEMA_REVISION
                            + "\nshot_count=" + expectedShots
                            + "\npixel_width=" + dimensions.width()
                            + "\npixel_height=" + dimensions.height()
                            + "\ncompleted_at=" + Instant.now() + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }

        private Path directory() {
            return directory;
        }

        private Path manifest() {
            return manifest;
        }

        private CaptureDimensions dimensions() {
            return dimensions;
        }

        private static String csv(String value) {
            if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                    && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
                return value;
            }
            return '"' + value.replace("\"", "\"\"") + '"';
        }
    }
}
