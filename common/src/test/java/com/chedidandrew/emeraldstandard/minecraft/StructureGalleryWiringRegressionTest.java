package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Guards the deliberately isolated, opt-in Minecraft structure gallery.
 *
 * <p>This is a source-wiring test because the gallery itself depends on Minecraft classes and is
 * therefore not part of the loader-neutral common-test classpath. The checks focus on the safety
 * boundary: ordinary worlds must not be able to invoke gallery writes, and gallery generation
 * must reuse production blueprints without enrolling fixtures in the economy simulation.
 */
public final class StructureGalleryWiringRegressionTest {
    private static final String MINECRAFT_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/";
    private static final Pattern BUILD_CONFIRMATION = Pattern.compile(
            "literal\\(\\\"build\\\"\\)\\s*\\.then\\(Commands\\.literal\\(\\\"confirm\\\"\\)",
            Pattern.DOTALL);

    private StructureGalleryWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path root = Path.of(args[0]);
        String gallery = read(root, "StructureGallery.java");
        String commands = read(root, "EmeraldCommands.java");
        String prosperity = read(root, "VillageProsperityManager.java");
        String banks = read(root, "VillageBankManager.java");
        String capture = Files.readString(root.resolve(
                "common/src/client/java/com/chedidandrew/emeraldstandard/client/"
                        + "GalleryCaptureSupport.java"));
        String fabric = Files.readString(root.resolve(
                "fabric/src/main/java/com/chedidandrew/emeraldstandard/fabric/EmeraldStandardFabric.java"));
        String neoForge = Files.readString(root.resolve(
                "neoforge/src/main/java/com/chedidandrew/emeraldstandard/neoforge/EmeraldStandardNeoForge.java"));
        String launcher = Files.readString(root.resolve(
                "scripts/structure-gallery-client.init.gradle"));
        String captureLauncher = Files.readString(root.resolve(
                "scripts/structure-gallery-capture-client.init.gradle"));

        verifyOptInBoundary(gallery);
        verifyAutoBuildBoundary(gallery, fabric, neoForge, launcher);
        verifyCommandConfirmation(gallery, commands);
        verifyProductionBlueprintReuse(gallery, prosperity, banks);
        verifyNoEconomyEnrollment(gallery);
        verifyExpansionSafeCompletionAndNavigation(gallery);
        verifyDescriptorAwareVisitFraming(gallery);
        verifyAutomatedReviewCapture(gallery, capture, captureLauncher);
        verifyCollisionSafeCapturePhotography(gallery);
        verifyMotifAwareDoodadPhotography(gallery);
        System.out.println("PASS structure gallery wiring regression");
    }

    private static void verifyAutoBuildBoundary(
            String gallery, String fabric, String neoForge, String launcher) {
        require(gallery.contains("the_emerald_standard.structureGallery.autoBuild"),
                "Gallery auto-build is not gated by its own explicit property");
        String autoBuild = methodBody(gallery, "static void autoBuildIfRequested");
        require(autoBuild.contains("enabled()")
                        && autoBuild.contains("AUTO_BUILD_PROPERTY")
                        && autoBuild.contains("isExactGalleryWorld(server)"),
                "Gallery auto-build does not enforce opt-in, auto-build, and exact-world gates");
        require(autoBuild.contains("isBuilt(level)"),
                "Gallery auto-build is not idempotent through the completion marker");
        require(fabric.contains("StructureGallery.autoBuildIfRequested(server)"),
                "Fabric server startup does not invoke isolated gallery auto-build");
        require(neoForge.contains("StructureGallery.autoBuildIfRequested(server)"),
                "NeoForge server startup does not invoke isolated gallery auto-build");
        require(launcher.contains("structureGallery.autoBuild")
                        && launcher.contains("TES_Blueprint_V2_Gallery"),
                "Gallery launcher does not explicitly request auto-build for the exact save");
    }

    private static void verifyOptInBoundary(String gallery) {
        require(gallery.contains(
                        "\"the_emerald_standard.structureGallery\""),
                "Gallery JVM opt-in property changed or is missing");
        require(gallery.contains("\"TES_Blueprint_V2_Gallery\""),
                "Gallery exact world-folder identity changed or is missing");
        require(gallery.contains("Boolean.getBoolean("),
                "Gallery opt-in is not read as an explicit boolean JVM property");
        require(gallery.contains("getWorldPath(LevelResource.ROOT)"),
                "Gallery does not resolve the server's actual save-directory path");
        require(gallery.contains("getFileName()"),
                "Gallery world check does not compare the exact save-directory name");

        String commandAccess = methodBody(gallery, "static boolean hasCommandAccess");
        require(commandAccess.contains("enabled()"),
                "Gallery command access is not gated by the JVM opt-in");
        require(commandAccess.toLowerCase().contains("galleryworld"),
                "Gallery command access is not gated by exact gallery-world identity");

        String build = methodBody(gallery, "static int build");
        require(build.contains("requireGalleryWorld(") || build.contains("hasCommandAccess("),
                "Gallery writes do not re-check the opt-in and exact-world safety boundary");
    }

    private static void verifyCommandConfirmation(String gallery, String commands) {
        require(BUILD_CONFIRMATION.matcher(gallery).find(),
                "Gallery build is not nested behind the literal `build confirm`");
        require(gallery.contains("Commands.literal(\"info\")"),
                "Gallery is missing its non-mutating info command");
        require(gallery.contains("Commands.literal(\"overview\")"),
                "Gallery is missing its overview navigation command");
        require(gallery.contains("Commands.literal(\"visit\")"),
                "Gallery is missing indexed variant navigation");

        require(commands.contains("StructureGallery.hasCommandAccess(source)"),
                "The /emerald root does not grant isolated gallery-world command access");
        require(commands.contains("if (StructureGallery.enabled())"),
                "Gallery commands are registered without the JVM opt-in");
        require(commands.contains("root.then(StructureGallery.command())"),
                "The opt-in gallery command tree is not attached to /emerald");
    }

    private static void verifyProductionBlueprintReuse(
            String gallery, String prosperity, String banks) {
        require(gallery.contains("VillageProsperityManager.galleryProjectBlueprint("),
                "Gallery projects do not use the production-project bridge");
        require(gallery.contains("VillageBankManager.galleryBankBlueprint("),
                "Gallery Banks do not use the production-Bank bridge");

        String projectBridge = methodBody(prosperity, "galleryProjectBlueprint(");
        require(projectBridge.contains("blueprintProjectTemplate("),
                "Gallery project bridge bypasses the production Blueprint V2 template");
        String bankBridge = methodBody(banks, "galleryBankBlueprint(");
        require(bankBridge.contains("bankPlan("),
                "Gallery Bank bridge bypasses the production Bank plan");
    }

    private static void verifyNoEconomyEnrollment(String gallery) {
        require(!gallery.contains("EconomyState"),
                "Gallery driver references EconomyState and could enroll synthetic fixtures");
        require(!gallery.contains("EconomyService"),
                "Gallery driver references EconomyService and could mutate the live simulation");
        require(!gallery.contains("VillageRecord") && !gallery.contains("VillageProject"),
                "Gallery driver creates simulated village/project records");
    }

    private static void verifyExpansionSafeCompletionAndNavigation(String gallery) {
        String isBuilt = methodBody(gallery, "private static boolean isBuilt");
        String completion = methodBody(gallery, "private static void writeCompletionMarker");
        String build = methodBody(gallery, "private static int build");
        require(isBuilt.contains("StructureGalleryPlan.layoutSignature()")
                        && completion.contains("StructureGalleryPlan.layoutSignature()")
                        && isBuilt.contains("COMPLETION_SIGNATURE_BITS")
                        && completion.contains("COMPLETION_SIGNATURE_BITS"),
                "Gallery completion marker does not invalidate stale pre-expansion worlds");
        require(isBuilt.contains("Blocks.DIAMOND_BLOCK")
                        && isBuilt.contains("Blocks.GOLD_BLOCK")
                        && isBuilt.contains("Blocks.EMERALD_BLOCK"),
                "Gallery completion marker no longer verifies its versioned bit payload");
        require(build.contains("isBuilt(level)"),
                "Manual gallery build bypasses the catalog-sensitive completion marker");

        require(gallery.contains("SURFACE_PROBE_X = -32")
                        && gallery.contains("SURFACE_PROBE_Z = -32"),
                "Gallery surface probe can be raised by structure plot zero after relaunch");
        String overview = methodBody(gallery, "private static int overview");
        require(overview.contains("level.getMaxY()")
                        && gallery.contains("StructureGalleryPlan.DEPTH_BLOCKS) / 2"),
                "Expanded gallery overview is not dynamically framed below the world ceiling");

        String configure = methodBody(gallery, "private static void configureReviewWorld");
        require(configure.contains("StructureGalleryPlan.reviewHubYawDegrees()"),
                "New gallery reviewers are not oriented by the loader-neutral review plan");
    }

    private static void verifyDescriptorAwareVisitFraming(String gallery) {
        String visit = methodBody(gallery, "private static int visit");
        String target = methodBody(gallery, "private static GalleryTarget target");
        require(target.contains("StructureGalleryPlan.visitTargets().get(internalIndex)"),
                "Indexed gallery navigation does not select its matching camera plan");
        require(gallery.contains("StructureGalleryPlan.VisitTarget framing"),
                "GalleryTarget does not carry descriptor- and rotation-aware framing data");
        require(visit.contains("target.cameraX()")
                        && visit.contains("target.cameraYOffset()")
                        && visit.contains("target.cameraZ()")
                        && visit.contains("target.yawDegrees()")
                        && visit.contains("target.pitchDegrees()"),
                "Gallery visit does not apply every coordinate from the camera plan");
        require(!gallery.contains("VISIT_Y_OFFSET")
                        && !gallery.contains("VISIT_Z_OFFSET")
                        && !gallery.contains("target.originX() + 7.5"),
                "Gallery visit still relies on the old fixed-size camera offsets");
    }

    private static void verifyAutomatedReviewCapture(
            String gallery, String capture, String captureLauncher) {
        String teleport = methodBody(gallery, "public static CapturePose teleportForCapture");
        String preflight = methodBody(gallery, "public static void validateCapturePlan");
        String doodadMatcher = methodBody(gallery, "private static boolean matchesDoodadCell");
        require(teleport.contains("CAPTURE_PROPERTY")
                        && teleport.contains("enabled()")
                        && teleport.contains("isExactGalleryWorld(server)")
                        && teleport.contains("isBuilt(level)"),
                "Automated camera movement escaped a gallery-only safety gate");
        require(capture.contains("DOODAD_SCOPE")
                        && capture.contains("Coverage.DOODAD_DETAIL"),
                "Capture harness cannot run the independent D1-D16 evidence scope");
        require(capture.contains("doodad_focus,doodad_source_view")
                        && capture.contains("shot.doodadDetail().doodad().id()")
                        && capture.contains("shot.doodadDetail().sourceView().id()"),
                "Capture manifest no longer maps close evidence to each doodad archetype");
        require(capture.contains("vertical_fov")
                        && capture.contains("Double.toString(pose.verticalFovDegrees())"),
                "Capture manifest no longer records the exact per-shot vertical FOV");
        require(capture.contains("capture_context")
                        && capture.contains("pose.captureContext()")
                        && gallery.contains("ISOLATED_CLONE_CONTEXT")
                        && gallery.contains("stageIsolatedClone(level, shot)"),
                "Capture manifest no longer identifies exact isolated-clone evidence");
        require(capture.contains("CAPTURE_PIXEL_WIDTH = 1920")
                        && capture.contains("CAPTURE_PIXEL_HEIGHT = 1080")
                        && capture.contains("ensureCaptureDimensions(minecraft)")
                        && capture.contains("decoded.getWidth() < CAPTURE_PIXEL_WIDTH")
                        && capture.contains("pixel_width,pixel_height")
                        && capture.contains("pixel_width=\"")
                        && capture.contains("pixel_height=\""),
                "Capture no longer forces, decodes, and records full-HD evidence");
        require(capture.contains("CAPTURE_SCHEMA_REVISION = 3")
                        && capture.contains("capture-v%d"),
                "Capture output can collide with evidence from an older harness schema");
        require(capture.contains("StructureGallery.validateCapturePlan(ready.server(), shots)")
                        && capture.contains("PREFLIGHT_TIMEOUT_SECONDS = 300L")
                        && capture.contains("COMPLETE_PREFLIGHT_TIMEOUT_SECONDS = 900L")
                        && capture.contains("COMPLETE_SCOPE.equals(configuration.scope())")
                        && capture.contains("onServer(ready.server(), preflightTimeoutSeconds")
                        && preflight.contains("capturePose(level, shot)")
                        && preflight.contains("failures.isEmpty()"),
                "Capture does not preflight the complete itinerary before writing evidence");
        require(gallery.contains("validatedCapturePoses.get(shot)")
                        && preflight.contains("validatedCapturePoses = Map.of()")
                        && preflight.contains("resolvedPoses.put(shot, pose)")
                        && preflight.contains("validatedCapturePoses = Map.copyOf(resolvedPoses)"),
                "Capture no longer reuses the exact fail-closed pose resolved by preflight");
        require(gallery.contains("resetIsolatedReviewPad(")
                        && gallery.contains("placeIsolatedClone(")
                        && gallery.contains("freezeIsolatedCloneToSourceState(")
                        && gallery.contains("same production blueprint")
                        && gallery.contains("isolatedCloneMatches(")
                        && gallery.contains("int minimumY = baseY;")
                        && gallery.contains("Everything at/above the authored origin")
                        && gallery.contains("normalizeGalleryConnections(level, clone.blocks())")
                        && gallery.contains("Only exterior gallery evidence may use"),
                "Crowded exterior fallback no longer stages and verifies one exact isolated clone without treating incidental subsurface aging as authored geometry");
        String bankInterior = methodBody(gallery, "private static CapturePose captureBankInteriorPose");
        require(bankInterior.contains("BlockPos threshold = new BlockPos(preferredX, 1, targetZ - 2)")
                        && bankInterior.contains("double cameraX = thresholdWorld.getX() + 0.70")
                        && bankInterior.contains("int targetX = secondary ? 1")
                        && bankInterior.contains("hasClearInteriorEyeRay(")
                        && bankInterior.contains("chestTopX")
                        && bankInterior.contains("ledgerLampX")
                        && bankInterior.contains("frameCueX")
                        && bankInterior.contains("blockedRays.add(\"ender-chest\")")
                        && bankInterior.contains("blockedRays.add(\"ledger-lamp\")")
                        && bankInterior.contains("blockedRays.add(\"iron-frame\")")
                        && bankInterior.contains("near left jamb")
                        && gallery.contains("private static boolean hasClearInteriorEyeRay(")
                        && gallery.contains("state.is(BlockTags.PRESSURE_PLATES)")
                        && gallery.contains("sampleY > sample.getY() + 0.125"),
                "Bank secure evidence no longer uses the clear stepped-back walk-in composition");
        require(capture.contains("CAPTURE_START_SEQUENCE_PROPERTY")
                        && capture.contains("shotsFromSequence(")
                        && capture.contains("startSequence <= 0")
                        && capture.contains("shots.getFirst().sequence()")
                        && captureLauncher.contains("tesGalleryCaptureStartSequence")
                        && captureLauncher.contains("structureGallery.capture.startSequence"),
                "Capture harness cannot prove one exact sequence without replaying earlier shots");
        require(doodadMatcher.contains("isExteriorYardCell(cell, blueprint)")
                        && doodadMatcher.contains("isPlantPedestalAnchor")
                        && doodadMatcher.contains("isPlanterRunPlant")
                        && doodadMatcher.contains("isCrateClusterAnchor")
                        && doodadMatcher.contains("isHandCartCargoAnchor")
                        && doodadMatcher.contains("isHitchingRailAnchor")
                        && doodadMatcher.contains("cell.y() == 3")
                        && doodadMatcher.contains("isToolRackAnchor"),
                "Doodad capture selectors are not exterior-only topology signatures");
        String doodadCapture = methodBody(
                gallery, "private static CapturePose captureDoodadDetailPose");
        require(doodadCapture.contains("new ArrayList<>(blueprint.base())")
                        && doodadCapture.contains("scene.addAll(")
                        && doodadCapture.contains("blueprint.stageOne()")
                        && doodadCapture.contains("blueprint.stageTwo()"),
                "Doodad capture no longer matches the cumulative authored-and-dressed scene");
        String plantPedestal = methodBody(gallery, "private static boolean isPlantPedestalAnchor");
        String planterRun = methodBody(gallery, "private static boolean isPlanterRunPlant");
        require(!plantPedestal.contains("cell.z() < 0")
                        && plantPedestal.contains("isPlanterRunPlant(cell, scene)")
                        && plantPedestal.contains("materials().foundation()")
                        && plantPedestal.contains("materials().accent()")
                        && planterRun.contains("Blocks.MOSS_CARPET")
                        && planterRun.contains("isPottedPlant(neighbor.state())"),
                "D1/D8 capture matching no longer distinguishes a supported isolated pedestal "
                        + "from an oriented planter run");
        require(gallery.contains("Blocks.IRON_BARS")
                        && !doodadMatcher.contains("CRATE_CLUSTER -> state.is(Blocks.CHEST)"),
                "Doodad capture still uses a stale crate/tool-rack material signature");
        String cartMatcher = methodBody(gallery, "private static boolean isHandCartCargoAnchor");
        require(cartMatcher.contains("instanceof TrapDoorBlock")
                        && cartMatcher.contains("Blocks.CHEST")
                        && cartMatcher.contains("Blocks.BARREL")
                        && cartMatcher.contains("materials().roofSlab()"),
                "D10 capture no longer recognizes varied non-container cargo by cart topology");
        String cargoMatcher = methodBody(gallery, "private static boolean isForecourtCargoAnchor");
        require(cargoMatcher.contains("cell.y() != 2")
                        && cargoMatcher.contains("Blocks.RAIL")
                        && cargoMatcher.contains("materials().roofSlab()"),
                "D2 capture no longer recognizes the current strapped forecourt pallet");
        String crateMatcher = methodBody(gallery, "private static boolean isCrateClusterAnchor");
        require(crateMatcher.contains("cell.y() == 2")
                        && crateMatcher.contains("materials().wall()")
                        && crateMatcher.contains("materials().timber()")
                        && crateMatcher.contains("materials().roofSlab()"),
                "D9 capture no longer recognizes the current framed crate cluster");
        String hitchMatcher = methodBody(gallery, "private static boolean isHitchingRailAnchor");
        require(hitchMatcher.contains("Blocks.IRON_CHAIN")
                        && countOccurrences(hitchMatcher, "materials().fence()") >= 3,
                "D11 capture no longer recognizes the center tie and connected fence rail");
        String toolMatcher = methodBody(gallery, "private static boolean isToolRackHeader");
        require(toolMatcher.contains("materials().timber()")
                        && toolMatcher.contains("materials().roofSlab()")
                        && toolMatcher.contains("materials().roofStairs()"),
                "D15 capture no longer recognizes the slim open rack header");
        require(capture.contains("hud.isHidden()")
                        && capture.contains("hud.toggle()")
                        && capture.contains("getChat().clearMessages(true)")
                        && capture.contains("dismissTransientScreen(minecraft)"),
                "Review screenshots no longer suppress and restore HUD/chat noise");
        String viewport = methodBody(capture, "private static void dismissTransientScreen");
        String captureShot = methodBody(capture, "private static void captureShot");
        String prepareViewport = methodBody(
                capture, "private static void prepareCleanViewportForCapture");
        String screenshot = methodBody(capture, "private static void takeScreenshot");
        require(viewport.contains("minecraft.gui.screen() != null")
                        && viewport.contains("minecraft.gui.setScreen(null)")
                        && viewport.contains("minecraft.gui.setOverlay(null)")
                        && viewport.contains("Gallery onboarding screen could not be dismissed"),
                "First-login handbook UI can obscure the first review frame");
        require(captureShot.contains("prepareCleanViewportForCapture(minecraft, pose)")
                        && prepareViewport.contains("minecraft.gui.setScreen(null)")
                        && prepareViewport.contains("minecraft.gui.setOverlay(null)")
                        && prepareViewport.contains("pose.verticalFovDegrees()")
                        && prepareViewport.contains("minecraft.options.fov().set(")
                        && prepareViewport.contains("minecraft.gui.hud.toggle()")
                        && prepareViewport.contains("CLEAN_VIEWPORT_FRAME_MILLIS")
                        && screenshot.contains("minecraft.gui.screen() != null")
                        && screenshot.contains("minecraft.gui.overlay() != null")
                        && screenshot.contains("!minecraft.gui.hud.isHidden()")
                        && screenshot.contains("minecraft.options.fov().get() != expectedFov")
                        && screenshot.contains("FOV changed after preflight")
                        && screenshot.contains("saved.completeExceptionally")
                        && screenshot.indexOf("viewport is obscured")
                                < screenshot.indexOf("Screenshot.grab("),
                "Each screenshot no longer reasserts and atomically proves a stable, clean viewport");
    }

    private static void verifyMotifAwareDoodadPhotography(String gallery) {
        String capture = methodBody(gallery, "private static CapturePose captureDoodadDetailPose");
        String camera = methodBody(gallery, "private static LocalPoint motifAwareDoodadCamera");
        String lateral = methodBody(gallery, "private static double doodadLateralSign");
        String profiles = methodBody(
                gallery, "private static DoodadCameraProfile doodadCameraProfile");
        require(capture.contains("doodadCameraProfile(detail.doodad())")
                        && capture.contains("motifAwareDoodadCamera(")
                        && capture.contains("focus.y() + profile.targetYOffset()"),
                "Doodad detail capture no longer applies motif-specific camera and aim profiles");
        require(camera.contains("profile.tangentDistance()")
                        && camera.contains("profile.standoffDistance()")
                        && camera.contains("profile.cameraFeetY()")
                        && camera.contains("sourceView == StructureGalleryReviewPlan.View.REAR_DOODADS")
                        && camera.contains("cameraZ -= profile.tangentDistance()")
                        && camera.contains("doodad == StructureGalleryReviewPlan.Doodad.SAFE_CAMPFIRE_NOOK")
                        && camera.contains("doodad == StructureGalleryReviewPlan.Doodad.TOOL_RACK")
                        && camera.contains("double inwardSign"),
                "Motif-aware cameras are not low exterior three-quarter views for "
                        + "rear/front/side yards");
        require(lateral.contains("blueprintWidth / 2.0")
                        && lateral.contains("doodad.ordinal()"),
                "Motif camera side selection is not stable or building-aware");
        for (String requiredProfile : new String[] {
                "FORECOURT_PLANT_PEDESTAL",
                "FORECOURT_CARGO_PEDESTAL",
                "RAIL_BOUND_LOG_RACK",
                "SLAB_AND_FENCE_BENCH",
                "SAFE_CAMPFIRE_NOOK",
                "GARDEN_WORK_CORNER",
                "HAND_CART",
                "HITCHING_RAIL",
                "TOOL_RACK"
        }) {
            require(profiles.contains("case " + requiredProfile + " ->"),
                    "Missing Carol camera profile for " + requiredProfile);
        }
        require(profiles.contains(
                        "HAND_CART -> new DoodadCameraProfile(5.15, 4.10, 0.90, -1.35)"),
                "Hand-cart camera no longer aims below the load to expose bed, axle, and wheels");
        require(profiles.contains(
                        "SAFE_CAMPFIRE_NOOK -> new DoodadCameraProfile(5.10, 1.25, 1.15, -0.45)")
                        && profiles.contains(
                                "GARDEN_WORK_CORNER -> new DoodadCameraProfile(4.75, 4.00, 0.65, -0.25)"),
                "Rear campfire camera no longer clears its bench or the garden camera regressed");
        require(profiles.contains(
                        "TOOL_RACK -> new DoodadCameraProfile(4.90, 2.60, 0.55, -0.45)"),
                "Tool-rack camera no longer shows the inward tool face from a low angle");
    }

    private static void verifyCollisionSafeCapturePhotography(String gallery) {
        String preflight = methodBody(gallery, "public static void validateCapturePlan");
        String exterior = methodBody(gallery, "private static CapturePose clearExteriorPose");
        String authoredBounds = methodBody(
                gallery, "private static SubjectBounds authoredExteriorSubjectBounds");
        String interior = methodBody(
                gallery, "private static CapturePose captureAuthoredInteriorPose");
        String reachable = methodBody(
                gallery, "private static List<BlockPos> reachableInteriorFloor");
        String sightline = methodBody(gallery, "boolean allowIronBarGrille");
        String viewport = methodBody(
                gallery, "private static boolean exteriorViewportClear");
        String projectedFit = methodBody(
                gallery, "private static boolean exteriorSubjectFitsViewport");
        String projectedCoverage = methodBody(
                gallery, "private static double exteriorSubjectViewportCoverage");
        String nearField = methodBody(
                gallery, "private static boolean clearExteriorNearFieldApproach");
        String viewportGrid = methodBody(
                gallery, "private static boolean clearExteriorViewportRayGrid");
        String viewportRay = methodBody(
                gallery, "private static boolean clearExteriorViewportRay(");
        String approach = methodBody(
                gallery, "private static boolean clearExteriorApproach");
        String visibility = methodBody(
                gallery, "private static int visibleInteriorFloorCount");
        String target = methodBody(
                gallery, "private static BlockPos bestVisibleInteriorTarget");
        String bank = methodBody(
                gallery, "private static CapturePose captureBankInteriorPose");

        require(preflight.contains("!level.getBlockState(feet).isAir()")
                        && preflight.contains("!level.getBlockState(eyes).isAir()")
                        && preflight.contains("failures.add("),
                "Camera collision preflight was weakened instead of fixing camera placement");
        require(exterior.contains("directSteps")
                        && exterior.contains("tangentSteps")
                        && exterior.contains("elevationSteps")
                        && exterior.contains("preferredY + elevation")
                        && exterior.contains("outsideSubjectEnvelope(subject, candidate)")
                        && exterior.contains("cameraVolumeClear(level, candidate)")
                        && exterior.contains("exteriorViewportClear(")
                        && exterior.contains("ExteriorCameraCandidate")
                        && exterior.contains("framingPenalty")
                        && exterior.contains("EXTERIOR_ZOOM_FALLBACK_FOV_DEGREES")
                        && exterior.contains("EXTERIOR_ZOOM_AIM_TANGENT_OFFSETS")
                        && gallery.contains("-6.00, 6.00")
                        && exterior.contains("withVerticalFov(")
                        && exterior.contains("exteriorSubjectCenteredInViewport(")
                        && exterior.contains("List<ExteriorCameraCandidate> zoomRanked")
                        && exterior.contains("order < generated.size()")
                        && exterior.contains("coverage > 0.90")
                        && exterior.contains("maximumNormalCoverage")
                        && exterior.contains("subject.allowInwardSearch() ? 0.90 : 0.80")
                        && exterior.contains("36.0, 44.0")
                        && exterior.contains("thenComparingDouble(ExteriorCameraCandidate::departure)")
                        && exterior.contains("throw new IllegalStateException"),
                "Exterior capture lacks a fit-ranked bounded outward-orbit search with a fail-closed end");
        require(viewport.contains("dx * dx + dz * dz > 5")
                        && viewport.contains("shoulderOffsets")
                        && viewport.contains("exteriorSubjectFitsViewport(")
                        && viewport.contains("clearExteriorViewportRayGrid(")
                        && viewport.contains("clearExteriorNearFieldApproach(")
                        && viewport.contains("clearShoulders < 3")
                        && viewport.contains("clearExteriorApproach(")
                        && projectedFit.contains("subject.allowInwardSearch() ? 0.48 : 0.55")
                        && projectedFit.contains("subject.allowInwardSearch()")
                        && projectedFit.contains("? 0.90")
                        && projectedFit.contains(": 0.80")
                        && gallery.contains("GalleryCameraProjection.projectOccupiedBlocks(")
                        && gallery.contains("subject.raisedSceneBlocks()")
                        && gallery.contains("subject.doodadBlocks()")
                        && gallery.contains("scene.maximumCoverage() < 0.50")
                        && gallery.contains("doodads.maximumCoverage() < 0.10")
                        && projectedCoverage.contains("projection.fits()")
                        && projectedCoverage.contains("projection.maximumCoverage()")
                        && nearField.contains("distance * progress > 8.0")
                        && nearField.contains("insideSubjectEnvelope(")
                        && nearField.contains("!level.getBlockState(sample).isAir()")
                        && viewportGrid.contains("EXTERIOR_VIEWPORT_RAY_ROWS")
                        && viewportGrid.contains("EXTERIOR_VIEWPORT_RAY_COLUMNS")
                        && gallery.contains("EXTERIOR_VIEWPORT_RAY_COLUMNS = 9")
                        && gallery.contains("EXTERIOR_VIEWPORT_RAY_ROWS = 7")
                        && gallery.contains("EXTERIOR_VIEWPORT_HORIZONTAL_SAMPLE = 0.97")
                        && gallery.contains("EXTERIOR_VIEWPORT_VERTICAL_SAMPLE = 0.97")
                        && viewportGrid.contains("EXTERIOR_VIEWPORT_MAX_FOREIGN_RAYS")
                        && gallery.contains("EXTERIOR_VIEWPORT_MAX_FOREIGN_RAYS = 0")
                        && viewportGrid.contains("GalleryCameraProjection.viewportRayDirection(")
                        && viewportGrid.contains("pose.verticalFovDegrees()")
                        && viewportGrid.contains("targetDistance - 0.50")
                        && viewportRay.contains("insideSubjectEnvelope(")
                        && viewportRay.contains("subject.activeFixtureBlocks().contains(sample)")
                        && viewportRay.contains("diagnostics.observeForeignBlock(sample)")
                        && viewportRay.contains("sample.getY() <= flatGroundY")
                        && viewportRay.contains("!level.getBlockState(sample).isAir()")
                        && approach.contains("insideSubjectEnvelope(")
                        && approach.contains("!level.getBlockState(sample).isAir()"),
                "Exterior photography no longer enforces framing, occupancy, or foreground clearance");
        require(authoredBounds.contains("blueprint.base()")
                        && authoredBounds.contains("entry.visualStage() >= 1")
                        && authoredBounds.contains("entry.visualStage() >= 2")
                        && authoredBounds.contains("cell.y() > 0")
                        && authoredBounds.contains("case FOUNDATION, FRAME, SHELL, ROOF, OPENING")
                        && authoredBounds.contains("activeFixtureBlocks.add(position)")
                        && authoredBounds.contains("transformedSubjectPosition(")
                        && gallery.contains("rotateSubjectCell(")
                        && gallery.contains("bankExteriorSubjectBounds(baseY, bank)")
                        && gallery.contains("empty collision padding"),
                "Exterior occupancy is no longer derived from real stage-specific subject blocks");
        require(interior.contains("clearInteriorFloor(")
                        && interior.contains("reachableInteriorFloor(")
                        && interior.contains("visibleInteriorFloorCount(")
                        && interior.contains("visibleFloorCounts")
                        && interior.contains("if (secondary && preferredLocal.equals(primary))")
                        && interior.contains("Comparator<BlockPos> cameraPriority")
                        && interior.contains("if (secondary)")
                        && interior.contains("cameraPreferenceLocal")
                        && interior.contains("bestVisibleInteriorTarget(")
                        && interior.contains("No useful clear sightline"),
                "Authored interiors no longer choose distinct clear reachable primary/secondary "
                        + "zones with readable spans");
        require(reachable.contains("ArrayDeque<BlockPos>")
                        && reachable.contains("verticalAccess")
                        && reachable.contains("clear.contains(neighbor)"),
                "Interior camera reachability is not flood-filled from entrance/dismount anchors");
        require(sightline.contains("!state.isAir()")
                        && sightline.contains("allowIronBarGrille && state.is(Blocks.IRON_BARS)")
                        && visibility.contains("distance <= 100.0")
                        && visibility.contains("hasClearInteriorSight(")
                        && target.contains("isInteriorPhotographyBarrier(")
                        && gallery.contains("state.getBlock() instanceof DoorBlock"),
                "Interior photography no longer rejects doors/partial occluders or favors open rooms");
        require(bank.contains("supportedCameraVolumeClear(level, camera)")
                        && bank.contains("preferredX = secondary ? 3 : 4")
                        && bank.contains("preferredZ = secondary ? 5 : 3")
                        && bank.contains("targetX = secondary ? 1")
                        && bank.contains("targetZ = secondary ? framing.depth() - 4")
                        && bank.contains("reachableInteriorFloor(")
                        && bank.contains("visibleFloorCounts")
                        && bank.contains("BlockPos threshold = new BlockPos(preferredX, 1, targetZ - 2)")
                        && bank.contains("ledgerLampX")
                        && bank.contains("frameCueX")
                        && bank.contains("hasClearInteriorEyeRay("),
                "Bank capture reverted to the carpeted/furnished centerline or lost zone framing");
    }

    private static String read(Path root, String file) throws Exception {
        Path source = root.resolve(MINECRAFT_SOURCE + file);
        require(Files.isRegularFile(source), "Missing gallery integration source: " + source);
        return Files.readString(source);
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        require(method >= 0, "Missing source method: " + signature);
        int openingBrace = source.indexOf('{', method);
        require(openingBrace >= 0, "Missing method body: " + signature);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError("Unterminated method body: " + signature);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
