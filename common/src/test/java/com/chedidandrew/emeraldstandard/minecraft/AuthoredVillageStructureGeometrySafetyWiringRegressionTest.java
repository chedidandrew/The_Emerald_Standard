package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards authored geometry decisions that are easy to regress while adding visual detail.
 *
 * <p>The Minecraft-backed integration validator proves the finished block graph is navigable and
 * supported. This loader-neutral source check protects the construction intent that creates that
 * graph: inherited roofs are cleared before upper rooms are transferred, real upper rooms are
 * registered through the shared ladder/hatch helper, open-air terrace roofs cannot fall back to
 * disconnected bottom-slab ribbons, and role-critical openings remain explicitly authored.</p>
 */
public final class AuthoredVillageStructureGeometrySafetyWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";
    private static final String DESK_FACTORY =
            "BankerProfessionSupport.exchangeDeskOrLectern()";

    private AuthoredVillageStructureGeometrySafetyWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);

        verifyUpperRoomRoofReplacement(source);
        verifyUpperRoomAccess(source);
        verifyContinuousOpenAirRoofCourses(source);
        verifyQuarryOpeningAndGantry(source);
        verifyBasilicaNaveEndClosure(source);
        verifySingleExchangeDeskPerRoute(source);
        verifyCumulativeRouteAdmission(source);
        verifyLightFixtureAttachments(source);
        System.out.println("PASS authored village geometry-safety wiring regression");
    }

    private static void verifyUpperRoomRoofReplacement(String source) {
        String clearHelper = methodBody(source, "private static void clearVolume(");
        require(clearHelper.contains("b.remove(x, y, z)"),
                "Upper-volume clearing helper no longer removes every inherited cell");

        // These plans add a usable room after one or more lower roofs already occupy its volume.
        for (String builder : List.of(
                "houseTowercourt", "innCourtyard", "granarySiloComplex", "exchangeBourse")) {
            requireClearDeckAccessRoofOrder(source, builder);
        }

        // Citadel bastions share the same replacement primitive. Keep the destructive crop local
        // to the authored builder and complete it before laying the transfer floor and tower cap.
        String bastion = methodBody(source, "private static void addBastionTower(");
        int clear = bastion.indexOf("clearVolume(");
        int deck = bastion.indexOf("b.force(Phase.FOUNDATION", clear);
        int roof = bastion.indexOf("addPyramidRoof(", deck);
        require(clear >= 0 && deck > clear && roof > deck,
                "Bastion towers must clear inherited roofs before transfer deck and cap");

        String citadel = methodBody(source, "private static void guardCitadel(");
        require(citadel.indexOf("addRectGableRoof(") >= 0
                        && citadel.indexOf("addBastionTower(")
                        > citadel.indexOf("addRectGableRoof("),
                "Citadel no longer exercises bastion replacement over its lower range roofs");
    }

    private static void requireClearDeckAccessRoofOrder(String source, String builder) {
        String body = methodBody(source, "private static void " + builder + "(");
        int clear = body.indexOf("clearVolume(");
        int deck = body.indexOf("b.force(Phase.FOUNDATION", clear);
        int access = body.indexOf("addBackedLadderAndHatch(", deck);
        int roof = body.indexOf("addPyramidRoof(", access);
        require(clear >= 0 && deck > clear && access > deck && roof > access,
                builder + " must clear inherited roof volume before its transfer deck, "
                        + "then add access before sealing the upper roof");
    }

    private static void verifyUpperRoomAccess(String source) {
        String helper = methodBody(source, "private static void addBackedLadderAndHatch(");
        for (String requirement : List.of(
                "Blocks.LADDER",
                "Blocks.OAK_TRAPDOOR",
                "m.verticalAccess.add(",
                "m.accessTargets.add(")) {
            require(helper.contains(requirement),
                    "Upper-access helper lost required behavior: " + requirement);
        }

        requireCallCount(source, "houseTowercourt", "addBackedLadderAndHatch(", 1);
        requireCallCount(source, "innCourtyard", "addBackedLadderAndHatch(", 1);
        requireCallCount(source, "exchangeBourse", "addBackedLadderAndHatch(", 1);
        requireCallCount(source, "guardGatehouse", "addBackedLadderAndHatch(", 2);
        requireCallCount(source, "guardCitadel", "addBackedLadderAndHatch(", 5);

        String granary = methodBody(source, "private static void granarySiloComplex(");
        Matcher siloLoop = Pattern.compile(
                        "for\\s*\\(\\s*int\\[\\]\\s+silo\\s*:\\s*new\\s+int\\[\\]\\[\\]"
                                + "\\s*\\{(?<rows>.*?)\\}\\s*\\)",
                        Pattern.DOTALL)
                .matcher(granary);
        require(siloLoop.find(), "Granary lost its authored multi-silo loop");
        int siloCount = countMatches(siloLoop.group("rows"), Pattern.compile("\\{[^{}]+}"));
        require(siloCount == 2, "Granary must retain two independently accessible silos");
        String siloBody = bracedBlockAt(granary, granary.indexOf('{', siloLoop.end()));
        require(countOccurrences(siloBody, "addBackedLadderAndHatch(") == 1,
                "Each silo iteration must create exactly one ladder/hatch route");
    }

    private static void verifyContinuousOpenAirRoofCourses(String source) {
        String fullSlab = methodBody(source, "private static BlockState doubleRoofSlab(");
        require(fullSlab.contains("SlabType.DOUBLE"),
                "doubleRoofSlab must remain a full bearing, not a bottom slab");

        verifySteppedRoofCourse(source, "warehouseWharf", 1);
        verifySteppedRoofCourse(source, "marketLane", 2);
    }

    private static void verifySteppedRoofCourse(
            String source, String builder, int minimumSlopeCalculations) {
        String body = methodBody(source, "private static void " + builder + "(");
        require(!body.contains("p.roofSlab.defaultBlockState()")
                        && !body.contains("SlabType.BOTTOM")
                        && !body.contains("upperRoofSlab("),
                builder + " reintroduced disconnected bottom-slab terrace bands");
        require(countOccurrences(body, "p.roofStairs.defaultBlockState()") >= 1,
                builder + " lost its continuous stair-course roof");
        require(countMatches(body, Pattern.compile(
                        "int\\s+(?:top|y)\\s*=\\s*[^;\\n]+/\\s*2\\s*;"))
                        >= minimumSlopeCalculations,
                builder + " no longer authors its stepped roof as a continuous half-rate slope");
        require(body.contains("post(b, Phase.FRAME")
                        && Pattern.compile("post\\s*\\([^;]+\\btop\\s*,\\s*p\\.timber\\s*\\)")
                                .matcher(body)
                                .find(),
                builder + " roof course is no longer met by height-matched support posts");
    }

    private static void verifyQuarryOpeningAndGantry(String source) {
        String quarry = methodBody(source, "private static void mineQuarry(");
        String normalized = quarry.replaceAll("\\s+", " ");

        Pattern railPortal = Pattern.compile(
                "if \\(x >= 10 && x <= 12 && y <= 4\\) \\{ continue; \\}");
        require(railPortal.matcher(normalized).find(),
                "Mine quarry lost the real three-wide opening through its rear rock face");
        Pattern crusherPortal = Pattern.compile(
                "for \\(int z = 15; z <= 17; z\\+\\+\\) \\{"
                        + ".*?for \\(int y = 1; y <= 3; y\\+\\+\\) \\{"
                        + ".*?b\\.remove\\(6, y, z\\);",
                Pattern.DOTALL);
        require(crusherPortal.matcher(normalized).find(),
                "Mine quarry lost the explicit three-by-three crusher opening");

        require(normalized.contains("for (int z : new int[] {5, 8})")
                        && normalized.contains("beam(b, Phase.FRAME, 5, 17, 12, z, p.timber)"),
                "Mine quarry gantry lost its two separated longitudinal trusses");
        require(normalized.contains("for (int x = 5; x <= 17; x += 3)")
                        && normalized.contains("p.fence.defaultBlockState()"),
                "Mine quarry gantry lost its sparse cross-bracing rhythm");
        require(!quarry.contains("p.roofSlab.defaultBlockState()")
                        && !quarry.contains("doubleRoofSlab("),
                "Mine quarry gantry regressed into a solid slab deck");
        require(normalized.contains("for (int z = 5; z <= 8; z++)")
                        && normalized.contains("b.force(Phase.FRAME, 15, 12, z, p.timber.defaultBlockState()"),
                "Mine quarry gantry lost its single narrow trolley beam");
        require(normalized.contains("for (int y = 8; y <= 11; y++)")
                        && normalized.contains("Blocks.IRON_CHAIN.defaultBlockState()"),
                "Mine quarry hoist lost its grounded trolley-beam connection");
    }

    private static void verifyBasilicaNaveEndClosure(String source) {
        String basilica = methodBody(source, "private static void warehouseBasilica(");
        int finalGableRoof = basilica.lastIndexOf("addRectGableRoof(");
        int clerestories = basilica.indexOf("addBasilicaNaveEndClerestories(");
        require(countOccurrences(basilica, "addRectGableRoof(") == 3
                        && clerestories > finalGableRoof,
                "Warehouse basilica must close its nave ends after authoring all three roofs");

        String closure = methodBody(
                source, "private static void addBasilicaNaveEndClerestories(");
        String normalized = closure.replaceAll("\\s+", " ");
        require(normalized.contains("for (int z : new int[] {0, 16})")
                        && normalized.contains("for (int x = 8; x <= 16; x++)")
                        && normalized.contains("for (int y = 7; y <= 11; y++)"),
                "Basilica clerestories no longer close both complete y=7..11 nave-end voids");
        require(normalized.contains("x == 8 || x == 12 || x == 16")
                        && normalized.contains("y == 7 || y == 11")
                        && normalized.contains("Blocks.GLASS_PANE.defaultBlockState()")
                        && normalized.contains("frame ? Phase.FRAME : Phase.OPENING"),
                "Basilica nave ends lost their supported timber-and-glass clerestory topology");
        String basilicaNormalized = basilica.replaceAll("\\s+", " ");
        require(basilicaNormalized.contains(
                        "fixture(b, m, 6, 1, 15, Blocks.LOOM, new BlockPos(6, 1, 14))")
                        && basilicaNormalized.contains(
                        "fixture(b, m, 18, 1, 15, Blocks.STONECUTTER, new BlockPos(18, 1, 14))"),
                "Basilica rear workstations must use the open dispatch aisle, not window-frame cells");
    }

    private static void verifySingleExchangeDeskPerRoute(String source) {
        String selector = methodBody(source, "static Blueprint plan(");
        require(selector.contains(
                        "case \"exchange_hall_01\" -> exchangeCivicHall(base, metadata, materials)"),
                "Active exchange hall no longer selects exchangeCivicHall");
        require(selector.contains(
                        "case \"exchange_countinghouse_02\" -> exchangeCountinghouse("
                                + "base, metadata, materials)"),
                "Active countinghouse route changed without updating desk-safety coverage");
        require(selector.contains(
                        "case \"exchange_branch_03\" -> exchangeBranchCompact("
                                + "base, metadata, materials)"),
                "Active exchange branch route changed without updating desk-safety coverage");
        require(selector.contains(
                        "case \"exchange_loggia_04\" -> exchangeLoggia(base, metadata, materials)"),
                "Active exchange loggia route changed without updating desk-safety coverage");
        require(selector.contains(
                        "case \"exchange_bourse_05\" -> exchangeBourse(base, metadata, materials)"),
                "Active exchange bourse route changed without updating desk-safety coverage");

        for (String direct : List.of(
                "exchangeCivicHall", "exchangeCountinghouse", "exchangeLoggia", "exchangeBourse")) {
            requireCallCount(source, direct, DESK_FACTORY, 1);
        }
        requireCallCount(source, "exchangeBranchCompact", DESK_FACTORY, 0);
        requireCallCount(source, "exchangeBranchCompact", "exchangeHall(", 1);
        requireCallCount(source, "exchangeHall", DESK_FACTORY, 1);
        require(countOccurrences(source, DESK_FACTORY) == 5,
                "Authored source must expose exactly one desk factory per active exchange route");
    }

    private static void verifyCumulativeRouteAdmission(String source) {
        String ledge = methodBody(source, "private static void addCompactOpenWorkLedge(");
        int preflight = ledge.indexOf("solidPropsPreserveInteractionRoutes(b, m, cells)");
        int firstPlacement = ledge.indexOf("b.put(Phase.DECOR");
        require(ledge.contains("new BlockPos(minX, 1, z)")
                        && ledge.contains("new BlockPos(minX + 1, 1, z)")
                        && ledge.contains("new BlockPos(minX, 2, z)")
                        && preflight >= 0
                        && firstPlacement > preflight,
                "Compact open-work ledge must atomically prove all proposed cells preserve "
                        + "interaction and vertical routes before placement");

        String validation = methodBody(source, "private static void validate(Blueprint blueprint)");
        for (String stage : List.of("base", "throughStageOne", "complete")) {
            require(validation.contains("validateNavigation(blueprint, " + stage + ")")
                            && validation.contains(
                                    "validateVerticalAccess(blueprint, " + stage + ")"),
                    "Authored catalog admission no longer validates cumulative navigation and "
                            + "vertical access for " + stage);
        }
    }

    private static void verifyLightFixtureAttachments(String source) {
        String validation = methodBody(source, "private static void validate(Blueprint blueprint)");
        require(validation.contains("validateAttachedSupports(blueprint, base)")
                        && validation.contains(
                                "validateAttachedSupports(blueprint, throughStageOne)")
                        && validation.contains("validateAttachedSupports(blueprint, complete)"),
                "Authored catalog admission no longer validates fixture attachments at every "
                        + "cumulative visual stage");

        String support = methodBody(source, "private static void validateAttachedSupports(");
        require(support.contains(
                                "AuthoredLightFixtureSupportValidator.validate(cells, blueprint.id)")
                        && support.contains("state.getBlock() instanceof LadderBlock")
                        && support.contains("isFaceSturdy("),
                "Authored stages no longer share complete fixture-attachment admission or retain "
                        + "ladder backing validation");

        String market = methodBody(source, "private static void marketRotunda(");
        String normalized = market.replaceAll("\\s+", " ");
        int bearing = normalized.indexOf("for (int x : new int[] {6, 10})");
        int firstLantern = normalized.indexOf("addHangingLantern(b, 6, 3, 17)");
        require(bearing >= 0
                        && normalized.indexOf("b.force(Phase.FRAME, x, 4, 17", bearing) > bearing
                        && normalized.indexOf("RotatedPillarBlock.AXIS, Direction.Axis.X", bearing)
                                > bearing
                        && firstLantern > bearing,
                "Market-cloister rear lanterns lost their full timber soffit bearings");
    }

    private static void requireCallCount(
            String source, String method, String call, int expected) {
        String body = methodBody(source, "private static void " + method + "(");
        int actual = countOccurrences(body, call);
        require(actual == expected,
                method + " expected " + expected + " occurrence(s) of " + call
                        + " but found " + actual);
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0;
                index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }

    private static int countMatches(String text, Pattern pattern) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        require(method >= 0, "Missing source method: " + signature);
        int openingBrace = source.indexOf('{', method);
        require(openingBrace >= 0, "Missing method body: " + signature);
        return bracedBlockAt(source, openingBrace);
    }

    private static String bracedBlockAt(String source, int openingBrace) {
        require(openingBrace >= 0 && openingBrace < source.length()
                        && source.charAt(openingBrace) == '{',
                "Missing braced source block");
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError("Unterminated source block");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
