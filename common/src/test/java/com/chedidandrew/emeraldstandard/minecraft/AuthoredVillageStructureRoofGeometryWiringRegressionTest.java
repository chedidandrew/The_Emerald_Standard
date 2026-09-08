package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the Minecraft block-state adapter for the loader-neutral roof geometry gate. */
public final class AuthoredVillageStructureRoofGeometryWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";

    private AuthoredVillageStructureRoofGeometryWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);

        verifyCatalogAdmission(source);
        verifyCumulativeStageSnapshots(source);
        verifyStructuralPhaseMapping(source);
        verifyMinecraftOccupancyMapping(source);
        verifyHangingFeatureMapping(source);
        verifyRooftopFeatureMapping(source);
        System.out.println("PASS authored village roof geometry wiring regression");
    }

    private static void verifyCatalogAdmission(String source) {
        String validation = methodBody(source, "private static void validate(Blueprint blueprint)");
        require(validation.contains("validateRoofGeometry(blueprint);"),
                "Authored catalog admission no longer invokes the roof geometry gate");
    }

    private static void verifyCumulativeStageSnapshots(String source) {
        String validation = methodBody(source, "private static void validateRoofGeometry(");
        for (String stage : new String[] {
                "blueprint.base", "blueprint.stageOne", "blueprint.stageTwo"
        }) {
            require(validation.contains(stage),
                    "Roof geometry validation omits authored stage " + stage);
        }
        for (String stageId : new String[] {"\"base\"", "\"stage-one\"", "\"stage-two\""}) {
            require(validation.contains(stageId),
                    "Roof geometry validation omits cumulative prefix " + stageId);
        }
        require(count(validation, "RoofGeometryValidator.validate(") == 3,
                "Every cumulative authored stage must receive an independent roof validation");
    }

    private static void verifyStructuralPhaseMapping(String source) {
        String adapter = methodBody(source, "private static RoofSnapshot roofGeometrySnapshot(");
        require(adapter.contains("cell.phase == Phase.FOUNDATION")
                        && adapter.contains("Kind.FOUNDATION"),
                "Foundation cells are not identified for roof load paths");
        require(adapter.contains("cell.phase == Phase.ROOF")
                        && adapter.contains("Kind.ROOF_COURSE"),
                "Authored roof cells are not exported as structural roof courses");
        require(adapter.contains("cell.y == 0 && kind == Kind.FOUNDATION"),
                "Only terrain-level foundation cells may be marked as support anchors");
        require(adapter.contains("cell.phase == Phase.FRAME")
                        && adapter.contains("cell.phase == Phase.SHELL")
                        && adapter.contains("Kind.STRUCTURE"),
                "Structural phases are no longer mapped to load-bearing roof geometry");
        require(adapter.contains("Kind.NON_LOAD_BEARING"),
                "Openings, fixtures, and decoration can still masquerade as roof supports");
    }

    private static void verifyMinecraftOccupancyMapping(String source) {
        String occupancy = methodBody(source, "private static Occupancy roofOccupancy(");
        require(occupancy.contains("instanceof SlabBlock"),
                "Slab geometry is no longer distinguished from full blocks");
        require(occupancy.contains("state.getValue(SlabBlock.TYPE)"),
                "Slab occupancy does not derive from the placed block state's TYPE property");
        require(occupancy.contains("case BOTTOM -> Occupancy.LOWER_HALF")
                        && occupancy.contains("case TOP -> Occupancy.UPPER_HALF")
                        && occupancy.contains("case DOUBLE -> Occupancy.FULL"),
                "Minecraft slab halves are not mapped to their physical vertical spans");
    }

    private static void verifyHangingFeatureMapping(String source) {
        String hanging = methodBody(source, "private static boolean isHangingFeature(");
        require(hanging.contains("state.is(Blocks.IRON_CHAIN)"),
                "Vertical iron chains no longer participate in hanging attachment validation");
        require(hanging.contains("LanternBlock.HANGING"),
                "Hanging lanterns no longer require ceiling attachment");
        require(hanging.contains("BellBlock.ATTACHMENT")
                        && hanging.contains("BellAttachType.CEILING"),
                "Ceiling-mounted bells no longer require ceiling attachment");
    }

    private static void verifyRooftopFeatureMapping(String source) {
        String adapter = methodBody(source, "private static RoofSnapshot roofGeometrySnapshot(");
        require(adapter.contains("isRooftopFeature(blueprint, cell)")
                        && adapter.contains("Kind.ROOFTOP_FEATURE"),
                "Authored roof accents are no longer mapped to direct-bearing rooftop features");

        String feature = methodBody(source, "private static boolean isRooftopFeature(");
        require(feature.contains("cell.phase == Phase.ROOF"),
                "Rooftop feature classification is no longer limited to authored roof cells");
        require(feature.contains("blueprint.materials.chimney")
                        && feature.contains("blueprint.materials.accent"),
                "Chimney and finial/merlon materials no longer receive direct-bearing semantics");
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int count(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
