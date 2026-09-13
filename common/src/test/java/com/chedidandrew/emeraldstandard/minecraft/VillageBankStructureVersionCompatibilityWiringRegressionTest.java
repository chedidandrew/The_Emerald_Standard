package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Locks frozen version-two-through-six Bank compatibility and version-seven build routing. */
public final class VillageBankStructureVersionCompatibilityWiringRegressionTest {
    private static final String BANK_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "VillageBankManager.java";

    private VillageBankStructureVersionCompatibilityWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new AssertionError("Expected repository root argument");
        }
        Path sourceFile = Path.of(args[0]).resolve(BANK_SOURCE);
        require(Files.isRegularFile(sourceFile), "Missing Village Bank source: " + sourceFile);
        String source = Files.readString(sourceFile);

        verifyVersionedPlanRouting(source);
        verifyLegacyPalettePrefixFrozen(source);
        verifyFrozenLegacyMethodBodies(source);
        verifyVersionSevenAdmissionGate(Path.of(args[0]), source);
        verifyVersionTwoMaintenanceIsNonDestructive(source);
        verifySevereDemolitionRetiresBeforeRemoteReplacement(source);
        System.out.println("PASS Village Bank structure-version compatibility wiring regression");
    }

    private static void verifyLegacyPalettePrefixFrozen(String source) {
        Map<String, List<String>> expectedPrefixes = Map.of(
                "DESERT", List.of(
                        "Blocks.SMOOTH_SANDSTONE", "Blocks.CUT_SANDSTONE",
                        "Blocks.CHISELED_SANDSTONE", "Blocks.SANDSTONE",
                        "Blocks.CHISELED_SANDSTONE", "Blocks.SANDSTONE_SLAB",
                        "Blocks.SANDSTONE", "Blocks.SANDSTONE_STAIRS",
                        "Blocks.ACACIA_FENCE", "Blocks.CUT_SANDSTONE",
                        "Blocks.CUT_RED_SANDSTONE", "Blocks.ACACIA_DOOR",
                        "Blocks.SANDSTONE_STAIRS", "Blocks.CUT_RED_SANDSTONE"),
                "SAVANNA", List.of(
                        "Blocks.STONE_BRICKS", "Blocks.ACACIA_PLANKS",
                        "Blocks.POLISHED_ANDESITE", "Blocks.ACACIA_PLANKS",
                        "Blocks.STRIPPED_ACACIA_LOG", "Blocks.ACACIA_SLAB",
                        "Blocks.ACACIA_PLANKS", "Blocks.DARK_OAK_STAIRS",
                        "Blocks.ACACIA_FENCE", "Blocks.SMOOTH_STONE",
                        "Blocks.MUD_BRICKS", "Blocks.ACACIA_DOOR",
                        "Blocks.STONE_BRICK_STAIRS", "Blocks.BRICKS"),
                "SNOWY", List.of(
                        "Blocks.STONE_BRICKS", "Blocks.SPRUCE_PLANKS",
                        "Blocks.POLISHED_DIORITE", "Blocks.SPRUCE_PLANKS",
                        "Blocks.STRIPPED_SPRUCE_LOG", "Blocks.SPRUCE_SLAB",
                        "Blocks.SPRUCE_PLANKS", "Blocks.DARK_OAK_STAIRS",
                        "Blocks.SPRUCE_FENCE", "Blocks.CHISELED_STONE_BRICKS",
                        "Blocks.POLISHED_DIORITE", "Blocks.SPRUCE_DOOR",
                        "Blocks.STONE_BRICK_STAIRS", "Blocks.STONE_BRICKS"),
                "TAIGA", List.of(
                        "Blocks.STONE_BRICKS", "Blocks.SPRUCE_PLANKS",
                        "Blocks.POLISHED_ANDESITE", "Blocks.SPRUCE_PLANKS",
                        "Blocks.STRIPPED_SPRUCE_LOG", "Blocks.SPRUCE_SLAB",
                        "Blocks.SPRUCE_PLANKS", "Blocks.SPRUCE_STAIRS",
                        "Blocks.SPRUCE_FENCE", "Blocks.CHISELED_STONE_BRICKS",
                        "Blocks.MOSSY_COBBLESTONE", "Blocks.SPRUCE_DOOR",
                        "Blocks.COBBLESTONE_STAIRS", "Blocks.BRICKS"),
                "PLAINS", List.of(
                        "Blocks.STONE_BRICKS", "Blocks.OAK_PLANKS",
                        "Blocks.POLISHED_ANDESITE", "Blocks.OAK_PLANKS",
                        "Blocks.STRIPPED_OAK_LOG", "Blocks.DARK_OAK_SLAB",
                        "Blocks.DARK_OAK_PLANKS", "Blocks.DARK_OAK_STAIRS",
                        "Blocks.OAK_FENCE", "Blocks.CHISELED_STONE_BRICKS",
                        "Blocks.BRICKS", "Blocks.OAK_DOOR",
                        "Blocks.STONE_BRICK_STAIRS", "Blocks.BRICKS"));
        for (Map.Entry<String, List<String>> entry : expectedPrefixes.entrySet()) {
            String marker = "case " + entry.getKey() + " -> new BankPalette(";
            int start = source.indexOf(marker);
            int end = source.indexOf(");", start);
            require(start >= 0 && end > start,
                    "Missing " + entry.getKey() + " Village Bank palette");
            List<String> arguments = source.substring(start + marker.length(), end).lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("Blocks."))
                    .map(line -> line.endsWith(",")
                            ? line.substring(0, line.length() - 1)
                            : line)
                    .toList();
            require(arguments.size() >= entry.getValue().size()
                            && arguments.subList(0, entry.getValue().size())
                                    .equals(entry.getValue()),
                    entry.getKey() + " legacy v2/v3/v4 Bank palette prefix changed");
        }

        String legacyPlans = methodBody(
                        source, "private static List<BankPlacement> legacyBankPlanV2(")
                + methodBody(source, "private static List<BankPlacement> legacyBankPlanV3(")
                + methodBody(source, "private static List<BankPlacement> legacyBankPlanV4(");
        for (String v5Field : new String[] {
                "facadePier", "civicPlinth", "civicCornice", "civicWall",
                "ledgerAccent", "forecourt", "roofCap", "cupolaBase"
        }) {
            require(!legacyPlans.contains("palette." + v5Field + "()"),
                    "Frozen legacy Bank plans started consuming v5-only palette field " + v5Field);
        }
    }

    private static void verifyFrozenLegacyMethodBodies(String source) throws Exception {
        Map<String, String> fingerprints = Map.ofEntries(
                Map.entry("private static BankPalette paletteFor(VillageArchitecture.BiomeDialect dialect)",
                        "7d770558b08fe733c3316f68e4e773588f74c7393b486113cc7e83bdf12663a3"),
                Map.entry("private static boolean isBankWindowCell(",
                        "34dcb3ede6f219962533103c08009a357a0e962511be6a02afe184934dd68262"),
                Map.entry("private static BlockState connectedBankPaneState(",
                        "0cc860f873e5c6e030545b4610ca8ac024544cc2e73cf3062c60f98e4cf0b14b"),
                Map.entry("private static List<BankPlacement> legacyBankPlanV2(",
                        "d65ed64f9c3a3eeaf781cdd7faa5c5884d6c7b99771a01343436f0e25df44b81"),
                Map.entry("private static List<BankPlacement> legacyBankPlanV3(",
                        "2d62623d9c6a177f8e1301382718ec2127683feb5d7eb2fca6ec18f286ee8383"),
                Map.entry("private static List<BankPlacement> legacyBankPlanV4(",
                        "f2cce5e34a457364e00f973c96a922802969ed97c0fca5459e9a820c5f0c64e2"),
                Map.entry("private static List<BankPlacement> legacyBankPlanV5(",
                        "a803ba304fbfbcfbb7b5c2a83797c58e0e42e0b426a684ac811f7c6598a756f6"),
                Map.entry("private static void appendBankV5SupportedCounterPendants(",
                        "bf67afa4e25241bb046f74a13c49681426347da181deb680a056810c568f7d5e"),
                Map.entry("private static void appendBankV5CivicEnvelope(",
                        "e1430b305d2143a2ab12c549e0854f74ded78f03c4bb3f147db69c306258c1e5"),
                Map.entry("private static boolean bankV5FacadePier(",
                        "fbf0aa188453001085a07c745bfd76486732d2e98e01939673c33ceb8472648d"),
                Map.entry("private static void appendBankV5WindowBays(",
                        "3b6e30f48650c36449e9a19e0fdc2e2cc08e5158ab4990b9b1d46f8deab29e47"),
                Map.entry("private static void appendBankV5Forecourt(",
                        "8383806604118f1542c4c33bedb0a8c71138aacf234ffd7231d622a522605ba0"),
                Map.entry("private static void appendBankV5RearLedgerElevation(",
                        "3bab29fa8876397ab7f9155aa956c8e09fba353c8f32c9ef3cd18ed769e166ee"),
                Map.entry("private static void appendBankV5DormerAndCupola(",
                        "cc3a3ae58bb726a5ad38fff6e5a21cf5d4c8a6eee1b5e65b5745a1f5d9b0f0f4"),
                Map.entry("private static List<BankPlacement> legacyBankPlanV6(",
                        "e9815d9d69e889953cc0694810e2b59059f2ea341161bb0bc156dd295e6e7ef3"),
                Map.entry("private static void appendBankV6Facades(",
                        "399b6a8778557648b85d6b62df2206bb5e3e93799bddbc2fccc9fb010a28dfbe"),
                Map.entry("private static void appendBankV6Belfry(",
                        "b19631dc815865f792e031c783306f650bc13b27d2ae6f1c715119ac3710cd0d"),
                Map.entry("private static void appendBankV6RecordRoom(",
                        "d548819f5e55360079aa5838a3d2642d6cae43ead501e71c41692eb36e0104c3"),
                Map.entry("private static BlockState bankCorniceStair(",
                        "883e6500112bdc6db05afbd377e700b83a48c42412948a13141de542f1aa527b"),
                Map.entry("private static void polishBankV6Materials(",
                        "dba7a44941946e17b0a065ef42325ac4cffd4b0ec2eb53dd90240acf6529a478"),
                Map.entry("private static Block bankV6RidgeBlock(",
                        "f322bb18054426f7cc45c944e7314ba262effb8c554a36b7d5a3d4f1bbaed52a"),
                Map.entry("private static BankCivicFinish bankCivicFinish(",
                        "a8586d8e88601954cf39960ab416bcb06ac8e40dcb1d0e377d71178da04af53a"));
        for (Map.Entry<String, String> entry : fingerprints.entrySet()) {
            String body = methodBody(source, entry.getKey()).replaceAll("\\s+", "");
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            require(actual.equals(entry.getValue()),
                    "A frozen legacy Bank plan was edited: " + entry.getKey());
        }
    }

    private static void verifyVersionSevenAdmissionGate(Path root, String source) throws Exception {
        String smoke = methodBody(source, "static void validateBankTemplate(ServerLevel level)");
        String production = methodBody(source, "private static void ensureBankTemplateValidated()");
        String dialects = methodBody(source, "static void validateBankDialectPaletteContract()");
        String dialectMatrix = methodBody(
                source, "private static void validateBankDialectPaletteContract(");
        String exact = methodBody(source, "private static void validateCurrentBankBlueprint(");
        String lighting = methodBody(source, "private static void validateBankInteriorLighting(");
        require(smoke.contains("validateBankDialectPaletteContract();")
                        && production.contains("validateBankDialectPaletteContract();")
                        && dialects.contains("validateBankDialectPaletteContract(false);")
                        && dialectMatrix.contains("VillageArchitecture.BiomeDialect.values()")
                        && dialectMatrix.contains("validateCurrentBankBlueprint(")
                        && exact.contains("validateBankV10Skyline(authored, palette);")
                        && exact.contains("validateBankV7ExteriorGardens(authored, palette);")
                        && exact.contains("validateBankInteriorZoning(")
                        && exact.contains("validateBankInteriorLighting(authored, snapshotId);")
                        && lighting.contains("AuthoredLightFixtureSupportValidator.validate("),
                "A v7 Bank dialect can bypass the shared geometry, zoning, attachment, or "
                        + "night-light production admission gate");

        Path selfTest = root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "VillageBankStructureSelfTest.java");
        String selfTestSource = Files.readString(selfTest);
        require(selfTestSource.contains(
                        "VillageBankManager.validateHeadlessBankDialectStructureContract();")
                        && selfTestSource.contains("VillageBankVersionSevenSelfTest.run();"),
                "The headless Bank structure verifier no longer executes the five-dialect gate");
        String fabricBuild = Files.readString(root.resolve("fabric/build.gradle"));
        require(fabricBuild.contains("tasks.register('verifyVillageBankStructure', JavaExec)")
                        && fabricBuild.contains(
                                "mainClass = 'com.chedidandrew.emeraldstandard.minecraft."
                                        + "VillageBankStructureSelfTest'")
                        && fabricBuild.contains(
                                "dependsOn tasks.named('verifyVillageBankStructure')"),
                "Fabric check no longer runs the headless Bank structure verifier");
    }

    private static void verifyVersionedPlanRouting(String source) {
        require(source.contains("private static final int LEGACY_BANK_STRUCTURE_VERSION = 2;")
                        && source.contains(
                                "private static final int PREVIOUS_BANK_STRUCTURE_VERSION = 3;")
                        && source.contains(
                                "private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V4 = 4;")
                        && source.contains(
                                "private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V5 = 5;")
                        && source.contains(
                                "private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V6 = 6;")
                        && source.contains("private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V8 = 8;")
                        && source.contains("private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V9 = 9;")
                        && source.contains("private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V10 = 10;")
                        && source.contains("private static final int BANK_STRUCTURE_VERSION = 11;"),
                "Village Bank structure-version constants drifted from the v2-v9 contract");

        String attempt = methodBody(source, "private static BankBuildAttempt attemptBankBuild(");
        require(attempt.contains("reserveProgressiveBank(") && !attempt.contains("= buildBank("),
                "A new or replacement Bank must use progressive construction");

        String build = methodBody(source, "private static BankBuildResult buildBank(");
        require(build.contains("terrainSupportedBankPlan(level, origin, palette)"),
                "Production Bank construction no longer resolves the terrain-supported plan");
        String supported = methodBody(
                source, "private static List<BankPlacement> terrainSupportedBankPlan(");
        require(supported.contains("List<BankPlacement> base = bankPlan(origin, palette);")
                        && !supported.contains("legacyBankPlanV2("),
                "New or replacement Bank construction can select the frozen v2 blueprint");

        String persist = methodBody(source, "private static boolean persistBuiltBank(");
        require(persist.contains("economy.markGeneratedBankRegion(")
                        && persist.contains("BANK_STRUCTURE_VERSION"),
                "A completed new or replacement Bank is not atomically marked as v8");

        String integrity = methodBody(
                source, "private static BankIntegrity inspectManagedBankIntegrity(");
        require(integrity.contains("structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V7")
                        && integrity.contains("? legacyBankPlanV7(origin, palette)"),
                "Existing v7 Banks must retain their frozen plan");
        require(integrity.contains("structureVersion >= BANK_STRUCTURE_VERSION")
                        && integrity.contains("? bankPlan(origin, palette)")
                        && integrity.contains(
                                "structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V6")
                        && integrity.contains("? legacyBankPlanV6(origin, palette)")
                        && integrity.contains(
                                "structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V5")
                        && integrity.contains("? legacyBankPlanV5(origin, palette)")
                        && integrity.contains(
                                "structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V4")
                        && integrity.contains("? legacyBankPlanV4(origin, palette)")
                        && integrity.contains("structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION")
                        && integrity.contains("? legacyBankPlanV3(origin, palette)")
                        && integrity.contains(": legacyBankPlanV2(origin, palette)"),
                "Bank integrity no longer dispatches v2-v6 to frozen plans and v7 to the "
                        + "current plan");
        require(!integrity.contains("setBlock(")
                        && !integrity.contains("destroyBlock(")
                        && !integrity.contains("removeBlock("),
                "The version-aware Bank integrity inspection gained a world mutation");

        String versionThree = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV3(");
        require(versionThree.contains("for (int jambX : new int[] {1, 3})")
                        && versionThree.contains("origin.offset(2, 1, counterZ - 1)")
                        && versionThree.contains("origin.offset(1, 1, counterZ)")
                        && !versionThree.contains("legacyBankPlanV2(")
                        && !versionThree.contains("bankPlan("),
                "The frozen v3 secure-room composition was rewritten or delegated");

        String versionFour = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV4(");
        require(versionFour.contains("legacyBankPlanV3(origin, palette)")
                        && versionFour.contains("for (int jambX : new int[] {1, 4})")
                        && versionFour.contains("origin.offset(1, 1, counterZ - 1)")
                        && versionFour.contains("origin.offset(1, 1, counterZ)")
                        && versionFour.contains("origin.offset(4, y, counterZ - 1)")
                        && !versionFour.contains("appendBankV5"),
                "Frozen v4 construction lost its bounded two-wide secure-room delta");

        String versionFive = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV5(");
        require(versionFive.contains("legacyBankPlanV4(origin, palette)")
                        && versionFive.contains("appendBankV5CivicEnvelope(")
                        && versionFive.contains("appendBankV5WindowBays(")
                        && versionFive.contains("appendBankV5Forecourt(")
                        && versionFive.contains("appendBankV5RearLedgerElevation(")
                        && versionFive.contains("appendBankV5SupportedCounterPendants(")
                        && versionFive.contains("appendBankV5DormerAndCupola("),
                "Frozen v5 construction no longer layers its bounded civic delta over frozen v4");
        String versionSix = methodBody(source, "private static List<BankPlacement> legacyBankPlanV6(");
        require(versionSix.contains("legacyBankPlanV5(origin, palette)")
                        && versionSix.contains("appendBankV6Facades(")
                        && versionSix.contains("appendBankV6Belfry(")
                        && versionSix.contains("appendBankV6RecordRoom("),
                "Frozen v6 construction lost its bounded finish, belfry or record-room delta");
        String versionSeven = methodBody(source, "private static List<BankPlacement> legacyBankPlanV7(");
        require(versionSeven.contains("bankV7Palette(legacyPalette)")
                        && versionSeven.contains("legacyBankPlanV6(origin, palette)")
                        && versionSeven.contains("appendBankV7ExteriorGardens("),
                "Frozen v7 construction lost its isolated material/planting composition");
        require(methodBody(source, "private static List<BankPlacement> legacyBankPlanV9(").contains("legacyBankPlanV8(origin, legacyPalette)")
                        && source.contains("? legacyBankPlanV8(origin, palette)"), "version eight is frozen, version nine sets back the doorway runner");
        require(methodBody(source, "private static List<BankPlacement> legacyBankPlanV10(").contains("legacyBankPlanV9(origin, legacyPalette)")
                        && integrity.contains("? legacyBankPlanV9(origin, palette)"), "v9 must remain frozen under the v10 roof correction");
        require(methodBody(source, "private static List<BankPlacement> bankPlan(").contains("legacyBankPlanV10(origin, legacyPalette)")
                        && integrity.contains("structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V10")
                        && integrity.contains("? legacyBankPlanV10(origin, palette)"),
                "v10 must remain frozen under the v11 bench correction");
        String versionEight = methodBody(source, "private static List<BankPlacement> legacyBankPlanV8(");
        require(versionEight.contains("legacyBankPlanV7(origin, legacyPalette)")
                        && versionEight.contains("Blocks.BRICK_WALL.defaultBlockState()"),
                "Current v8 Bank chimney pass must layer over the frozen v7 plan");
    }

    private static void verifyVersionTwoMaintenanceIsNonDestructive(String source) {
        String maintenance = methodBody(source, "private static void maintainManagedBank(");
        int versionGate = maintenance.indexOf(
                "if (structureVersion >= LEGACY_BANK_STRUCTURE_VERSION)");
        int entranceMutationPlan = maintenance.indexOf("bankEntranceUpgradePlan(");
        require(versionGate >= 0 && entranceMutationPlan > versionGate,
                "The frozen v2 early-return no longer precedes legacy entrance mutation planning");
        require(maintenance.substring(versionGate, entranceMutationPlan).contains("return;"),
                "An intact v2 Bank can fall through into an authored structure upgrade");
        require(!maintenance.contains("bankPlan(")
                        && !maintenance.contains("legacyBankPlanV2("),
                "Routine maintenance can rewrite a versioned Bank blueprint");
    }

    private static void verifySevereDemolitionRetiresBeforeRemoteReplacement(String source) {
        String tick = methodBody(source, "public static void tick(");
        int relocateDecision = tick.indexOf(
                "VillageMaterializationPolicy.IntegrityDecision.RELOCATE");
        int retire = tick.indexOf("economy.retireGeneratedBankAnchor(", relocateDecision);
        int replacement = tick.indexOf("attemptBankBuild(", retire);
        int persist = tick.indexOf("persistBuiltBank(", replacement);
        int unsafeDecision = tick.indexOf(
                "VillageMaterializationPolicy.IntegrityDecision.UNSAFE", persist);
        require(relocateDecision >= 0
                        && retire > relocateDecision
                        && replacement > retire
                        && persist > replacement
                        && unsafeDecision > persist,
                "Severe Bank demolition no longer retires the old anchor before replacement");
        String relocationBranch = tick.substring(relocateDecision, unsafeDecision);
        require(!relocationBranch.contains("setBlock(")
                        && !relocationBranch.contains("destroyBlock(")
                        && !relocationBranch.contains("removeBlock(")
                        && !relocationBranch.contains("rollbackBank("),
                "Severe-demolition routing gained an old-site world mutation");

        String search = methodBody(source, "private static BankPlotSearch findBankPlots(");
        require(search.contains("economy.generatedBankAnchorsSnapshot().values()")
                        && search.contains("economy.retiredBankAnchorsSnapshot()")
                        && search.contains(
                                "farEnoughFromRetiredBanks(bankerAnchor, excludedBanks)"),
                "Replacement search can reclaim the active or a retired Bank site");
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
}
