package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps the executable detail-density gate on every active authored-plan variant. */
public final class AuthoredVillageStructureDetailDensityWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";

    private AuthoredVillageStructureDetailDensityWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);

        verifyEveryActivePlanRunsTheGate(source);
        verifyCompletePhaseSnapshot(source);
        System.out.println("PASS authored village detail-density wiring regression");
    }

    private static void verifyEveryActivePlanRunsTheGate(String source) {
        require(source.contains("WholeBuildingDetailDensityValidator"),
                "Authored catalog no longer imports the loader-neutral detail gate");
        String plan = methodBody(source, "static Blueprint plan(");
        int ordinaryValidation = plan.indexOf("validate(blueprint)");
        int detailValidation = plan.indexOf("validateDetailDensity(blueprint)");
        require(ordinaryValidation >= 0 && detailValidation > ordinaryValidation,
                "Active authored plans do not run detail admission after structural validation");
        require(plan.contains("templateRevision == LATEST_TEMPLATE_REVISION"),
                "Detail admission is not scoped to active revision-2 masters");

        String gate = methodBody(source, "private static void validateDetailDensity(");
        require(gate.contains("WholeBuildingDetailDensityValidator.validateSnapshots(")
                        && gate.contains("detailSnapshot(blueprint)")
                        && gate.contains(".requireDetailed()"),
                "Authored detail helper no longer invokes the executable admission report");
    }

    private static void verifyCompletePhaseSnapshot(String source) {
        String snapshot = methodBody(source, "private static DetailSnapshot detailSnapshot(");
        for (String layer : new String[] {
                "blueprint.base", "blueprint.stageOne", "blueprint.stageTwo"
        }) {
            require(snapshot.contains(layer),
                    "Detail snapshot omits maximum-stage layer " + layer);
        }
        require(snapshot.contains("DetailPhase.valueOf(cell.phase.name())"),
                "Authored phases are not mapped exhaustively into the loader-neutral detail gate");
        require(snapshot.contains("blueprint.metadata.enclosed()"),
                "Detail snapshot lost its enclosed/open-air facade policy");
        require(snapshot.contains("blueprint.width")
                        && snapshot.contains("blueprint.depth")
                        && snapshot.contains("blueprint.height"),
                "Detail density is no longer normalized by the complete authored envelope");
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
