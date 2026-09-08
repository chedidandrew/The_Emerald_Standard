package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Guards the V2 industrial production-detail pass requested by the visual review. */
public final class AuthoredVillageStructureIndustryCraftWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";

    private AuthoredVillageStructureIndustryCraftWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        String source = Files.readString(Path.of(args[0]).resolve(AUTHORED_SOURCE));

        requireCalls(source, "addIndustryWarehouseProgram", List.of(
                "sawtooth", "crane", "gabled", "wharf", "basilica"));
        requireCalls(source, "addIndustryGranaryProgram", List.of(
                "raised_barn", "windmill", "cruck", "stilt", "silo_complex"));
        requireCalls(source, "addIndustrySmithyProgram", List.of(
                "open_forge", "hammerhall", "lane", "corner", "foundry"));
        requireCalls(source, "addIndustryMineProgram", List.of(
                "headframe", "winding_house", "adit", "drift", "quarry"));

        require(methodBody(source, "private static void addIndustryWarehouseProgram(")
                        .contains("addIndustryMonumentalWarehouseHoist")
                        && methodBody(source, "private static void addIndustryWarehouseProgram(")
                                .contains("addIndustryWarehouseCatwalk"),
                "Warehouse pass lost its crane or basilica vertical-storage program");
        require(methodBody(source, "private static void addIndustryWindmillMachinery(")
                        .contains("Blocks.OAK_TRAPDOOR")
                        && methodBody(source, "private static void addIndustryWindmillMachinery(")
                                .contains("Blocks.GRINDSTONE")
                        && methodBody(source, "private static void addIndustryWindmillMachinery(")
                                .contains("Blocks.COMPOSTER"),
                "Windmill no longer exposes sails, millstones and grain flow");
        require(methodBody(source, "private static void addIndustryHammerhallDropHammer(")
                        .contains("Blocks.ANVIL")
                        && methodBody(source, "private static void addIndustryHammerhallDropHammer(")
                                .contains("Blocks.IRON_CHAIN"),
                "Hammerhall lost its supported drop-hammer station");
        String smithyProgram = methodBody(
                source, "private static void addIndustrySmithyProgram(");
        require(smithyProgram.contains("BlockPos chainSupport")
                        && smithyProgram.contains("RotatedPillarBlock.AXIS, Direction.Axis.X")
                        && smithyProgram.contains("isFaceSturdy(")
                        && smithyProgram.indexOf("BlockPos chainSupport")
                                < smithyProgram.indexOf("Blocks.IRON_CHAIN.defaultBlockState()"),
                "Smithy tool chains must hang from wall-tied sturdy corbels");
        String windingDrum = methodBody(source, "private static void addIndustryWindingDrum(");
        require(windingDrum.contains("RotatedPillarBlock.AXIS")
                        && windingDrum.contains("Blocks.OAK_TRAPDOOR")
                        && windingDrum.contains("BlockPos driveChainSupport")
                        && windingDrum.contains("isFaceSturdy(")
                        && windingDrum.indexOf("BlockPos driveChainSupport")
                                < windingDrum.lastIndexOf("Blocks.IRON_CHAIN.defaultBlockState()"),
                "Winding house lost its drum and geared cheeks");

        String solidPropGuard = methodBody(source,
                "private static boolean solidPropsPreserveInteractionRoutes(\n"
                        + "            Map<BlockPos, BlockState> occupied,");
        require(solidPropGuard.contains("metadata.reservedAir.contains(position)"),
                "Solid-prop admission no longer rejects reserved circulation cells");
        String cargoPallet = methodBody(source, "private static void addIndustryCargoPallet(");
        require(cargoPallet.contains("solidPropsPreserveInteractionRoutes(b, m, routeSolids)"),
                "Warehouse cargo pallet bypasses systemic circulation admission");
        String catwalk = methodBody(source, "private static void addIndustryWarehouseCatwalk(");
        require(countOccurrences(catwalk, "for (int z = 13; z < ladderZ; z++)") == 2,
                "Basilica landing must stop before the five-block ladder shaft");
        require(catwalk.contains("for (int y = 1; y <= 5; y++)")
                        && catwalk.contains("b.putIfFree(Phase.FIXTURE, ladderX, y, ladderZ,")
                        && catwalk.contains("Blocks.LADDER.defaultBlockState()"),
                "Basilica catwalk no longer authors the complete five-block ladder column");
        require(catwalk.contains("BlockPos hatch = new BlockPos(ladderX, 6, ladderZ)")
                        && catwalk.contains("Blocks.OAK_TRAPDOOR.defaultBlockState()")
                        && catwalk.indexOf("Blocks.OAK_TRAPDOOR.defaultBlockState()")
                                < catwalk.indexOf("m.verticalAccess.add(new VerticalAccess("),
                "Basilica vertical route must place its usable hatch before registration");
        require(catwalk.contains(
                        "m.accessTargets.add(new BlockPos(ladderX, 6, ladderZ - 1))"),
                "Basilica catwalk lost its adjacent upper dismount target");

        for (String helper : List.of(
                "private static void addIndustryWarehouseProgram(",
                "private static void addIndustryGranaryProgram(",
                "private static void addIndustrySmithyProgram(",
                "private static void addIndustryMineProgram(")) {
            require(!methodBody(source, helper).contains("Blocks.CHEST")
                            && !methodBody(source, helper).contains("Blocks.BARREL"),
                    "Industrial cosmetic program added loot-bearing storage: " + helper);
        }
        System.out.println("PASS authored village industrial-craft wiring regression");
    }

    private static void requireCalls(String source, String helper, List<String> variants) {
        for (String variant : variants) {
            require(source.contains(helper + "(b, m, p, \"" + variant + "\")"),
                    "Missing V2 industrial program " + helper + " / " + variant);
        }
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
