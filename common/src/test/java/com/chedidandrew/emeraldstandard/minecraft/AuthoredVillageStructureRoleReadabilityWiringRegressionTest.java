package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Guards the Minecraft-to-neutral boundary for ProjectType-specific visual fixture identity.
 *
 * <p>Block ids belong in the Minecraft bridge; role admission belongs in the common validator.
 * This source contract proves that every active plan exports base-master signals without letting
 * optional yard dressing manufacture a building's economic purpose.</p>
 */
public final class AuthoredVillageStructureRoleReadabilityWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";

    private AuthoredVillageStructureRoleReadabilityWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);

        verifyBaseOnlySemanticSnapshot(source);
        verifyEveryRoleSignalHasMinecraftEvidence(source);
        verifyEveryActivePlanRunsAdmission(source);
        System.out.println("PASS authored village role-readability wiring regression");
    }

    private static void verifyBaseOnlySemanticSnapshot(String source) {
        require(source.contains("WholeBuildingRoleReadabilityValidator.RoleSnapshot")
                        || source.contains("import com.chedidandrew.emeraldstandard.core."
                                + "WholeBuildingRoleReadabilityValidator.RoleSnapshot;"),
                "Authored plans are not exported as loader-neutral role snapshots");
        String snapshot = methodBody(source, "RoleSnapshot roleReadabilitySnapshot(");
        require(snapshot.contains("blueprint.base"),
                "Role identity is not derived from immutable base-master fixtures");
        require(!snapshot.contains("stageOne")
                        && !snapshot.contains("stageTwo")
                        && !snapshot.contains("dressingId"),
                "Optional dressing can manufacture a building's role identity");
        require(Pattern.compile("List\\s*<\\s*(?:FixtureSignal|"
                        + "WholeBuildingRoleReadabilityValidator\\.FixtureSignal)\\s*>")
                        .matcher(snapshot)
                        .find(),
                "Role snapshot does not preserve repeated signals such as duplicate desks");
        require(snapshot.contains("blueprint.metadata.type")
                        && snapshot.contains("BuildingRole")
                        && snapshot.contains(".name()"),
                "ProjectType is not translated explicitly to the neutral BuildingRole enum");
    }

    private static void verifyEveryRoleSignalHasMinecraftEvidence(String source) {
        String snapshot = methodBody(source, "RoleSnapshot roleReadabilitySnapshot(");
        for (String signal : List.of(
                "SLEEPING",
                "DOMESTIC_HEARTH",
                "HOSPITALITY_SERVICE",
                "BULK_STORAGE",
                "AGRICULTURAL_STORAGE",
                "FORGE_WORKS",
                "MINE_WORKS",
                "MARKET_ANCHOR",
                "DEFENSIVE_EQUIPMENT",
                "EXCHANGE_DESK")) {
            require(Pattern.compile("\\b" + signal + "\\b").matcher(snapshot).find(),
                    "Minecraft bridge never emits role signal " + signal);
        }
        require(snapshot.contains("BlockTags.BEDS")
                        || snapshot.contains("Blocks.BED.white()"),
                "Role-readability bridge lost the Minecraft bed classifier");
        for (String evidence : List.of(
                "Blocks.SMOKER",
                "Blocks.BREWING_STAND",
                "Blocks.CHEST",
                "Blocks.HAY_BLOCK",
                "Blocks.ANVIL",
                "Blocks.RAIL",
                "Blocks.BELL",
                "Blocks.IRON_BARS",
                "BankerProfessionSupport.isExchangeDesk")) {
            require(snapshot.contains(evidence),
                    "Role-readability bridge lost Minecraft evidence classifier " + evidence);
        }
    }

    private static void verifyEveryActivePlanRunsAdmission(String source) {
        String plan = methodBody(source, "static Blueprint plan(");
        Pattern activeGate = Pattern.compile(
                "if\\s*\\(\\s*templateRevision\\s*==\\s*LATEST_TEMPLATE_REVISION\\s*\\)"
                        + "\\s*\\{(?s:.*?)validateDetailDensity\\s*\\(\\s*blueprint\\s*\\)"
                        + "(?s:.*?)validateRoleReadability\\s*\\(\\s*blueprint\\s*\\)");
        require(activeGate.matcher(plan).find(),
                "Every active revision-2 plan does not run both visual-density and role gates");

        String admission = methodBody(source, "void validateRoleReadability(");
        require(Pattern.compile("WholeBuildingRoleReadabilityValidator\\s*\\.\\s*validateSnapshots")
                                .matcher(admission)
                                .find()
                        && admission.contains("roleReadabilitySnapshot(blueprint)")
                        && admission.contains(".requireReadable()"),
                "Authored plan admission bypasses the neutral role-readability validator");
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
