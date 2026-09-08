package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.VillageComparisonGallery;
import com.chedidandrew.emeraldstandard.minecraft.VillageComparisonGallery.ViewPose;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/** Opt-in real-game screenshots of the separate vanilla/mod comparison save. No aesthetic ratings. */
public final class VillageComparisonCaptureSupport {
    private static final String PROPERTY = VillageComparisonGallery.ENABLE_PROPERTY + ".capture";
    private static boolean started;

    private VillageComparisonCaptureSupport() { }

    public static synchronized void initialized(Logger logger) {
        if (started || !Boolean.getBoolean(PROPERTY) || !VillageComparisonGallery.enabled()) return;
        started = true;
        Thread worker = new Thread(() -> capture(logger), "emerald-standard-comparison-capture");
        worker.setDaemon(true);
        worker.start();
    }

    private static void capture(Logger logger) {
        Minecraft client = Minecraft.getInstance();
        boolean restore = false;
        boolean oldHud = false;
        int oldFov = 70;
        CameraType oldCameraType = CameraType.FIRST_PERSON;
        Ready activeSession = null;
        try {
            Ready ready = null;
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20);
            while (System.nanoTime() < deadline) {
                Ready candidate = onClient(client, () -> client.getSingleplayerServer() != null
                        && client.player != null && client.level != null
                        ? new Ready(client.getSingleplayerServer(), client.player.getUUID()) : null);
                if (candidate != null) {
                    GenerationStatus status = onServer(candidate.server(), () -> new GenerationStatus(
                            VillageComparisonGallery.isReady(candidate.server()),
                            VillageComparisonGallery.failureReason(candidate.server())));
                    if (status.failure() != null) {
                        // failureReason only returns a reason for the exact opted-in save. Keep
                        // that proven session for graceful, identity-checked shutdown in finally.
                        activeSession = candidate;
                        throw new IllegalStateException("Comparison generation failed: " + status.failure());
                    }
                    if (status.ready()) {
                        ready = candidate;
                        break;
                    }
                }
                Thread.sleep(1000);
            }
            if (ready == null) throw new IllegalStateException("Comparison save was not ready before timeout");
            Ready context = ready;
            activeSession = context;
            Set<Integer> selected = Arrays.stream(System.getProperty(PROPERTY + ".pairs", "1,12,52,53,54,65,105,106,107,118,158,159,160,171,211,212,213,224,264,265")
                            .split(","))
                    .map(String::trim).map(Integer::parseInt).collect(Collectors.toSet());
            var allViews = onServer(context.server(), () -> VillageComparisonGallery.captureViews(context.server()));
            var shots = allViews.stream().filter(p -> selected.contains(p.pairIndex())).toList();
            if (shots.isEmpty() || shots.size() != selected.size() * 3) {
                throw new IllegalArgumentException("Comparison capture pair selection is invalid");
            }
            String batch = "tes-village-comparison-" + Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID();
            Path directory = client.gameDirectory.toPath().resolve("screenshots").resolve(batch);
            Files.createDirectories(directory);
            Path manifest = directory.resolve("manifest.csv");
            Files.writeString(manifest, "pair,view,x,y,z,eye_x,eye_y,eye_z,yaw,pitch,fov,player,dimension,file,sha256\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            oldHud = onClient(client, () -> client.gui.hud.isHidden());
            oldFov = onClient(client, () -> client.options.fov().get());
            oldCameraType = onClient(client, () -> client.options.getCameraType());
            restore = true;
            onClient(client, () -> {
                client.getWindow().setWindowed(1920, 1080);
                client.options.setCameraType(CameraType.FIRST_PERSON);
                if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
                return null;
            });
            for (ViewPose shot : shots) {
                onServer(context.server(), () -> {
                    VillageComparisonGallery.teleportForCapture(context.server(), context.player(), shot);
                    return null;
                });
                onClient(client, () -> { client.options.fov().set(shot.verticalFovDegrees()); return null; });
                awaitPose(client, context, shot);
                Thread.sleep(3000);
                String filename = String.format(java.util.Locale.ROOT, "%03d-%s.png", shot.pairIndex(), shot.view());
                CompletableFuture<CapturedPose> saved = new CompletableFuture<>();
                client.execute(() -> {
                    try {
                        CapturedPose actual = capturePose(client, context, shot);
                        if (actual == null) {
                            throw new IllegalStateException("Comparison camera moved after settling: " + shot);
                        }
                        if (client.gui.screen() != null || client.gui.overlay() != null || !client.gui.hud.isHidden()
                                || client.gameRenderer.mainRenderTarget().width != 1920
                                || client.gameRenderer.mainRenderTarget().height != 1080) {
                            throw new IllegalStateException("Comparison screenshot viewport is not clean 1920x1080");
                        }
                        Screenshot.grab(client.gameDirectory, batch + "/" + filename,
                                client.gameRenderer.mainRenderTarget(), 1, ignored -> saved.complete(actual));
                    } catch (Throwable failure) { saved.completeExceptionally(failure); }
                });
                CapturedPose actual = saved.get(30, TimeUnit.SECONDS);
                Path screenshot = directory.resolve(filename);
                var decoded = javax.imageio.ImageIO.read(screenshot.toFile());
                if (decoded == null || decoded.getWidth() != 1920 || decoded.getHeight() != 1080) {
                    throw new IllegalStateException("Invalid comparison screenshot: " + filename);
                }
                String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(screenshot)));
                Files.writeString(manifest, shot.pairIndex() + "," + shot.view() + "," + actual.x() + ","
                                + actual.y() + "," + actual.z() + "," + actual.eyeX() + "," + actual.eyeY() + ","
                                + actual.eyeZ() + "," + actual.yaw() + "," + actual.pitch() + ","
                                + actual.fov() + "," + actual.player() + "," + actual.dimension() + ","
                                + filename + "," + checksum + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
                logger.info("Comparison screenshot saved: {}", screenshot);
            }
            Files.writeString(directory.resolve("complete.txt"), "Actual Minecraft screenshots; "
                    + shots.size() + " views of " + selected.size() + " selected pairs.\n"
                    + "Curated template comparison, not naturally generated villages or Carol ratings.\n"
                    + "Completed " + Instant.now() + "\n", StandardOpenOption.CREATE_NEW);
            logger.info("Comparison capture complete: {}", directory);
        } catch (Exception failure) {
            logger.error("Comparison capture failed; incomplete batches are not final evidence", failure);
        } finally {
            if (restore) {
                boolean hud = oldHud;
                int fov = oldFov;
                CameraType cameraType = oldCameraType;
                try { onClient(client, () -> {
                    if (client.gui.hud.isHidden() != hud) client.gui.hud.toggle();
                    client.options.fov().set(fov);
                    client.options.setCameraType(cameraType);
                    return null;
                }); }
                catch (Exception failure) { logger.error("Could not restore comparison HUD/FOV", failure); }
            }
            Ready completedSession = activeSession;
            if (Boolean.getBoolean(PROPERTY + ".stopWhenComplete") && completedSession != null) {
                client.execute(() -> {
                    // A failed capture must never close a different world the user has since opened.
                    if (sameSession(client, completedSession)) client.stop();
                });
            }
        }
    }

    private static void awaitPose(Minecraft client, Ready context, ViewPose pose) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (onClient(client, () -> capturePose(client, context, pose) != null)) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Comparison camera did not reach " + pose);
    }

    /** On the render thread: metadata describes the actual frame, never just intended coordinates. */
    private static CapturedPose capturePose(Minecraft client, Ready context, ViewPose expected) {
        if (!sameSession(client, context)) {
            throw new IllegalStateException("Comparison capture left its exact server/player/Overworld session");
        }
        if (client.gui.screen() != null || client.gui.overlay() != null) return null;
        if (client.getCameraEntity() != client.player
                || client.options.getCameraType() != CameraType.FIRST_PERSON) return null;
        var camera = client.gameRenderer.mainCamera();
        var eye = camera.position();
        CapturedPose actual = new CapturedPose(client.player.getUUID(),
                client.level.dimension().identifier().toString(),
                client.player.getX(), client.player.getY(), client.player.getZ(),
                eye.x(), eye.y(), eye.z(), camera.yRot(), camera.xRot(), client.options.fov().get());
        return Math.abs(actual.x() - expected.x()) < 0.1
                && Math.abs(actual.y() - expected.y()) < 0.1
                && Math.abs(actual.z() - expected.z()) < 0.1
                && Math.abs(actual.eyeX() - expected.x()) < 0.1
                && Math.abs(actual.eyeY() - expected.y() - client.player.getEyeHeight()) < 0.15
                && Math.abs(actual.eyeZ() - expected.z()) < 0.1
                && Math.abs(Math.IEEEremainder(client.player.getYRot() - expected.yaw(), 360.0)) < 0.1
                && Math.abs(client.player.getXRot() - expected.pitch()) < 0.1
                && Math.abs(Math.IEEEremainder(actual.yaw() - expected.yaw(), 360.0)) < 0.1
                && Math.abs(actual.pitch() - expected.pitch()) < 0.1
                && actual.fov() == expected.verticalFovDegrees() ? actual : null;
    }

    private static boolean sameSession(Minecraft client, Ready context) {
        return client.getSingleplayerServer() == context.server()
                && client.player != null && client.level != null
                && client.player.getUUID().equals(context.player())
                && client.level.dimension().equals(Level.OVERWORLD);
    }

    private static <T> T onClient(Minecraft client, Supplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        client.execute(() -> complete(future, supplier));
        return future.get(30, TimeUnit.SECONDS);
    }

    private static <T> T onServer(MinecraftServer server, Supplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> complete(future, supplier));
        return future.get(60, TimeUnit.SECONDS);
    }

    private static <T> void complete(CompletableFuture<T> future, Supplier<T> supplier) {
        try { future.complete(supplier.get()); }
        catch (Throwable failure) { future.completeExceptionally(failure); }
    }

    private record Ready(MinecraftServer server, UUID player) { }

    private record GenerationStatus(boolean ready, String failure) { }

    private record CapturedPose(UUID player, String dimension, double x, double y, double z,
            double eyeX, double eyeY, double eyeZ, float yaw, float pitch, int fov) { }
}
