package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Narrow source-boundary checks; real vanilla template existence is verified by verifyPlan(level). */
public final class VillageComparisonGalleryWiringRegressionTest {
    private VillageComparisonGalleryWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root is required");
        }
        Path root = Path.of(args[0]);
        String base = "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/";
        String source = Files.readString(root.resolve(base + "VillageComparisonGallery.java"));
        String commands = Files.readString(root.resolve(base + "EmeraldCommands.java"));
        String fabric = Files.readString(root.resolve(
                "fabric/src/main/java/com/chedidandrew/emeraldstandard/fabric/EmeraldStandardFabric.java"));
        String neo = Files.readString(root.resolve(
                "neoforge/src/main/java/com/chedidandrew/emeraldstandard/neoforge/EmeraldStandardNeoForge.java"));
        String init = Files.readString(root.resolve("scripts/village-comparison-client.init.gradle"));
        String launcher = Files.readString(root.resolve("scripts/open-village-comparison.ps1"));
        String docs = Files.readString(root.resolve("docs/VILLAGE_COMPARISON_GALLERY.md"));
        String clientCapture = Files.readString(root.resolve(
                "common/src/client/java/com/chedidandrew/emeraldstandard/client/"
                        + "VillageComparisonCaptureSupport.java"));

        String enabled = body(source, "public static boolean enabled(");
        String exact = body(source, "private static boolean isExactWorld(");
        require(enabled.contains("Boolean.getBoolean(ENABLE_PROPERTY) && StructureGallery.enabled()")
                        && exact.contains("if (!enabled() || server == null)")
                        && exact.contains("WORLD_DIRECTORY.equals(name.toString())")
                        && source.contains("WORLD_DIRECTORY = \"TES_Village_Comparison\""),
                "Comparison must require both JVM opt-ins and the separate exact save");
        require(body(source, "public static boolean hasCommandAccess(").contains("isExactWorld(source.getServer())")
                        && body(source, "public static LiteralArgumentBuilder<CommandSourceStack> command(")
                                .contains(".requires(VillageComparisonGallery::hasCommandAccess)")
                        && commands.contains("VillageComparisonGallery.command()"),
                "No comparison command may bypass the isolated world boundary");
        for (String loader : new String[] {fabric, neo}) {
            require(loader.contains("VillageComparisonGallery.autoBuildIfRequested(server)")
                            && (loader.contains("VillageComparisonGallery.tick(server)")
                                    || loader.contains("VillageComparisonGallery.tick(event.getServer())")),
                    "Both loaders must use incremental comparison hooks");
        }

        String begin = body(source, "private static void begin(");
        String tick = body(source, "public static void tick(");
        require(begin.contains("!server.overworld().isFlat()")
                        && begin.contains("hasHeader && !signatureBitsMatch(level, signature)")
                        && begin.contains("requireEmptyMarkers(level, pairs.size() + courts.size())")
                        && begin.contains("if (!state.ready) {\n            configureWorld(server, surfaceY);"),
                "Reopen must refuse stale catalogs and never repaint/reconfigure completed saves");
        require(tick.contains("state.ready || state.failure != null || !isExactWorld(server)")
                        && tick.contains("placePair(server.overworld(), pair)")
                        && !tick.contains("while (") && !tick.contains("for (")
                        && tick.indexOf("writeIndex(server, state)") < tick.indexOf("writeSignature(server.overworld()"),
                "Work must be incrementally bounded; index must precede final completion marker");
        require(tick.contains("Blocks.REDSTONE_BLOCK")
                        && tick.contains("was interrupted. Preserve this save"),
                "Interrupted pairs must fail closed, not be cleared and recreated");
        String empty = body(source, "private static void requireEmptyBox(");
        require(empty.contains("level.getBlockEntity(pos) != null")
                        && empty.contains("!level.getFluidState(pos).isEmpty()")
                        && empty.contains("(!current.isAir() && !untouchedGround)"),
                "Unrelated blocks, inventories and fluids must reject placement");
        String ground = body(source, "private static void prepareGround(");
        require(ground.contains("VillageComparisonBiomePlan.slices(")
                        && ground.contains("slice.minY()") && ground.contains("slice.maxY()")
                        && !ground.contains("gamerule") && !ground.contains("COMMAND_MODIFICATION_BLOCK_LIMIT"),
                "Biome fill must use bounded 3D slices without raising Minecraft's modification limit");
        String failureReason = body(source, "public static String failureReason(");
        require(failureReason.contains("if (!isExactWorld(server)) return null")
                        && failureReason.contains("state == null ? null : state.failure")
                        && !failureReason.contains("setBlock") && !failureReason.contains("begin("),
                "Failure status must be a read-only exact-world query");

        String pair = body(source, "private static void placePair(");
        require(pair.contains("VillageProsperityManager.galleryProjectBlueprint(")
                        && pair.contains("VillageBankManager.galleryBankBlueprint(")
                        && pair.contains("normalizeGalleryConnections(level, blocks)")
                        && pair.contains("finalGalleryAttachmentExpectations(blocks)")
                        && pair.contains("StructureGallery.validateAttachmentState("),
                "Comparison must reuse production construction and real fragile-attachment checks");
        require(source.contains("getStructureManager().listTemplates()")
                        && source.contains("getStructureManager().get(vanillaId)")
                        && pair.contains("pair.template.placeInWorld(")
                        && pair.contains("setIgnoreEntities(true)")
                        && pair.contains("JigsawReplacementProcessor.INSTANCE")
                        && pair.contains("BlockIgnoreProcessor.STRUCTURE_BLOCK")
                        && source.contains("VillageBankManager.galleryBankStructureVersion()"),
                "Vanilla references must be real templates; Bank identity must follow the active version");
        String candidates = body(source, "private static List<Identifier> vanillaCandidates(");
        require(candidates.contains("startsWith(prefix + \"houses/\")")
                        && candidates.contains("startsWith(prefix + \"town_centers/\")")
                        && !candidates.contains("contains(\"/houses/\")")
                        && body(source, "private static List<VillageCourt> resolveCourts(")
                                .contains("dialect.id() + \"/town_centers/\""),
                "Normal comparison candidates must exclude zombie folders; courts need real town centers");
        String originY = body(source, "private static int vanillaOriginY(");
        require(originY.contains("return surfaceY;") && !originY.contains("surfaceY - 1")
                        && body(source, "private static long signature(").contains("pair.vanillaOrigin"),
                "Complete reference NBT must remain above grade without carving trenches; origin belongs to save identity");
        require(source.contains("From the front: mod right, real vanilla template left")
                        && docs.contains("on the right when viewed from the front"),
                "Comparison side labels must match the actual south-facing visit/capture view");
        require(!source.contains("new EconomyState") && !source.contains(".villages.put(")
                        && !source.contains("registerVillage(") && !source.contains("removeBlock(")
                        && !source.contains("destroyBlock("),
                "Comparison may not enroll fixtures in the economy or clear old structures");

        String capture = body(source, "public static void teleportForCapture(");
        require(capture.contains("Boolean.getBoolean(ENABLE_PROPERTY + \".capture\")")
                        && capture.contains("!isReady(server)") && capture.contains("!captureViews(server).contains(pose)")
                        && body(source, "public static List<ComparisonEntry> entries(").contains("requireReady(server)")
                        && body(source, "public static List<ViewPose> captureViews(").contains("requireReady(server)"),
                "Capture metadata/movement must require exact completed evidence and validated poses");
        String contextVisit = body(source, "private static int visitContext(");
        require(contextVisit.contains("if (!isReady(server))")
                        && contextVisit.contains("contextCourts(server).get(district - 1)")
                        && contextVisit.contains("not natural worldgen"),
                "Village-context visits must require the completed isolated catalog and disclose curation");
        String framing = body(source, "private static ViewPose pose(");
        String previousRow = body(source, "private static ViewPose clearPreviousRow(");
        require(framing.contains("(16.0 / 9.0) * 0.65")
                        && framing.contains("Math.max(horizontalDistance, verticalDistance)")
                        && previousRow.contains("e.plotZ() == entry.plotZ() - ROW_PITCH")
                        && previousRow.contains("exitFraction >= 1.0")
                        && previousRow.contains("roofClearance - targetY * exitFraction")
                        && body(source, "public static List<ViewPose> captureViews(").contains("clearPreviousRow("),
                "Comparison framing must respect vertical FOV, height fit and previous-row roof clearance");
        require(init.contains("structureGallery.autoBuild=false")
                        && init.contains("structureGallery.capture=false")
                        && init.contains("villageComparison.autoBuild=true")
                        && init.contains("TES_Village_Comparison"),
                "New launcher must not accidentally trigger the old gallery tooling");
        require(!launcher.contains("Remove-Item") && !launcher.contains("Copy-Item")
                        && !launcher.contains("Move-Item") && launcher.contains("level.dat"),
                "Open helper may not transplant player data or overwrite/delete saves");
        require(docs.contains("not natural village generation") && docs.contains("No numerical review counter resets")
                        && source.contains("vanilla has no financial building")
                        && source.contains("not natural world generation"),
                "Comparison must disclose its curated analogues and never reset review limits");
        verifyClientCaptureEvidence(clientCapture);
        System.out.println("PASS village comparison safety and wiring regression");
    }

    private static void verifyClientCaptureEvidence(String source) {
        String session = body(source, "private static boolean sameSession(");
        require(session.contains("client.getSingleplayerServer() == context.server()")
                        && session.contains("client.player.getUUID().equals(context.player())")
                        && session.contains("client.level.dimension().equals(Level.OVERWORLD)"),
                "Comparison capture may not switch server, player or dimension");
        String actual = body(source, "private static CapturedPose capturePose(");
        for (String marker : new String[] {"!sameSession(client, context)", "client.getCameraEntity() != client.player",
                "CameraType.FIRST_PERSON", "client.gameRenderer.mainCamera()", "camera.position()",
                "client.player.getX()", "client.player.getY()", "client.player.getZ()",
                "camera.yRot()", "camera.xRot()", "client.options.fov().get()",
                "actual.eyeX() - expected.x()", "actual.eyeZ() - expected.z()",
                "actual.yaw() - expected.yaw()", "actual.pitch() - expected.pitch()",
                "actual.fov() == expected.verticalFovDegrees()"}) {
            require(actual.contains(marker), "Actual capture-pose proof lost " + marker);
        }
        require(body(source, "private static void awaitPose(")
                        .contains("capturePose(client, context, pose) != null"),
                "Comparison settle must verify actual render pose, not only target feet coordinates");
        String capture = body(source, "private static void capture(");
        int failurePoll = capture.indexOf("VillageComparisonGallery.failureReason(candidate.server())");
        int wait = capture.indexOf("Thread.sleep(1000)");
        require(failurePoll >= 0 && failurePoll < wait
                        && capture.contains("if (status.failure() != null)")
                        && capture.contains("activeSession = candidate;")
                        && capture.contains("Comparison generation failed: "),
                "Known exact-world generation failures must abort readiness promptly and retain scoped shutdown identity");
        int finalPose = capture.indexOf("CapturedPose actual = capturePose(client, context, shot)");
        int screenshot = capture.indexOf("Screenshot.grab(");
        require(finalPose >= 0 && finalPose < screenshot
                        && capture.substring(finalPose, screenshot).contains("if (actual == null)")
                        && capture.contains("saved.complete(actual)")
                        && capture.contains("CapturedPose actual = saved.get(")
                        && capture.contains("actual.eyeX()") && capture.contains("actual.yaw()")
                        && capture.contains("MessageDigest.getInstance(\"SHA-256\")")
                        && capture.contains("Files.readAllBytes(screenshot)")
                        && capture.contains("filename + \",\" + checksum")
                        && capture.contains("if (sameSession(client, completedSession)) client.stop()"),
                "Comparison manifest must use validated actual frame metadata/checksums and scoped shutdown");
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        require(start >= 0, "Missing method: " + signature);
        int opening = source.indexOf('{', start);
        int depth = 1;
        int end = opening + 1;
        for (; end < source.length() && depth > 0; end++) {
            if (source.charAt(end) == '{') {
                depth++;
            } else if (source.charAt(end) == '}') {
                depth--;
            }
        }
        require(depth == 0, "Unbalanced method: " + signature);
        return source.substring(opening + 1, end - 1).replace("\r\n", "\n");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
