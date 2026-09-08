package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Locks frozen version-two/three/four Bank compatibility and version-five build routing. */
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
        verifyVersionFiveAdmissionGate(Path.of(args[0]), source);
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

    private static void verifyVersionFiveAdmissionGate(Path root, String source) throws Exception {
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
                        && exact.contains("validateBankV5Skyline(authored, palette);")
                        && exact.contains("validateBankInteriorZoning(")
                        && exact.contains("validateBankInteriorLighting(authored, snapshotId);")
                        && lighting.contains("AuthoredLightFixtureSupportValidator.validate("),
                "A v5 Bank dialect can bypass the shared geometry, zoning, attachment, or "
                        + "night-light production admission gate");

        Path selfTest = root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "VillageBankStructureSelfTest.java");
        String selfTestSource = Files.readString(selfTest);
        require(selfTestSource.contains(
                        "VillageBankManager.validateHeadlessBankDialectStructureContract();"),
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
                        && source.contains("private static final int BANK_STRUCTURE_VERSION = 5;"),
                "Village Bank structure-version constants drifted from the v2/v3/v4/v5 contract");

        String attempt = methodBody(source, "private static BankBuildAttempt attemptBankBuild(");
        require(attempt.contains("BankBuildResult build = buildBank("),
                "A new or replacement Bank can bypass the current production builder");

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
                "A completed new or replacement Bank is not atomically marked as v5");

        String integrity = methodBody(
                source, "private static BankIntegrity inspectManagedBankIntegrity(");
        require(integrity.contains("structureVersion >= BANK_STRUCTURE_VERSION")
                        && integrity.contains("? bankPlan(origin, palette)")
                        && integrity.contains(
                                "structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V4")
                        && integrity.contains("? legacyBankPlanV4(origin, palette)")
                        && integrity.contains("structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION")
                        && integrity.contains("? legacyBankPlanV3(origin, palette)")
                        && integrity.contains(": legacyBankPlanV2(origin, palette)"),
                "Bank integrity no longer dispatches v2/v3/v4 to frozen plans and v5 to the "
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
                source, "private static List<BankPlacement> bankPlan(");
        require(versionFive.contains("legacyBankPlanV4(origin, palette)")
                        && versionFive.contains("appendBankV5CivicEnvelope(")
                        && versionFive.contains("appendBankV5WindowBays(")
                        && versionFive.contains("appendBankV5Forecourt(")
                        && versionFive.contains("appendBankV5RearLedgerElevation(")
                        && versionFive.contains("appendBankV5SupportedCounterPendants(")
                        && versionFive.contains("appendBankV5DormerAndCupola("),
                "Current v5 construction no longer layers its bounded civic delta over frozen v4");
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
