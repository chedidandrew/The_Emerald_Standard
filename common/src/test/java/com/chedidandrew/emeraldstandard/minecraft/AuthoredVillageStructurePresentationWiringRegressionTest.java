package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps the loader-neutral presentation gate wired to every active revision-2 authored plan. */
public final class AuthoredVillageStructurePresentationWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";

    private AuthoredVillageStructurePresentationWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);

        verifyRevisionTwoPlanRunsPresentationAfterDensity(source);
        verifyPresentationHelperInvokesLoaderNeutralGate(source);
        verifySnapshotPreservesScaleEnvelopeAndLayerProvenance(source);
        verifyCellTranslationPreservesCoordinatesAndPhase(source);
        System.out.println("PASS authored village presentation wiring regression");
    }

    private static void verifyRevisionTwoPlanRunsPresentationAfterDensity(String source) {
        require(source.contains("WholeBuildingPresentationValidator"),
                "Authored catalog no longer imports the loader-neutral presentation gate");
        String plan = methodBody(source, "static Blueprint plan(");
        String revisionHeader = "if (templateRevision == LATEST_TEMPLATE_REVISION)";
        int densityCall = plan.indexOf("validateDetailDensity(blueprint)");
        require(densityCall >= 0,
                "Active authored plan no longer runs detail-density admission");
        int revisionBranch = plan.lastIndexOf(revisionHeader, densityCall);
        require(revisionBranch >= 0,
                "Presentation admission is not scoped to active revision-2 masters");
        String activeRevision = bracedBody(
                plan, plan.indexOf('{', revisionBranch), revisionHeader);
        int density = activeRevision.indexOf("validateDetailDensity(blueprint)");
        int presentation = activeRevision.indexOf("validatePresentation(blueprint)");
        require(density >= 0 && presentation > density,
                "Revision-2 plans do not directly run presentation admission after density");
    }

    private static void verifyPresentationHelperInvokesLoaderNeutralGate(String source) {
        String helper = compact(methodBody(
                source, "private static void validatePresentation("));
        require(helper.contains("WholeBuildingPresentationValidator")
                        && helper.contains(".validateSnapshots("
                                + "List.of(presentationSnapshot(blueprint)))")
                        && helper.contains(".requirePresentable()"),
                "Authored presentation helper no longer invokes the executable admission report");
    }

    private static void verifySnapshotPreservesScaleEnvelopeAndLayerProvenance(String source) {
        String snapshot = compact(methodBody(
                source, "private static PresentationSnapshot presentationSnapshot("));
        require(snapshot.contains(
                        "VillageArchitecture.requireBlueprint("
                                + "blueprint.id,blueprint.revision).scale()"),
                "Presentation snapshot no longer derives its scale from catalog metadata");
        require(snapshot.contains("blueprint.width")
                        && snapshot.contains("blueprint.depth")
                        && snapshot.contains("blueprint.height")
                        && snapshot.contains("blueprint.metadata.enclosed()"),
                "Presentation snapshot lost envelope or enclosed/open-air provenance");

        int base = snapshot.indexOf("presentationCells(blueprint.base)");
        int stageOne = snapshot.indexOf("presentationCells(blueprint.stageOne)");
        int stageTwo = snapshot.indexOf("presentationCells(blueprint.stageTwo)");
        require(base >= 0 && stageOne > base && stageTwo > stageOne,
                "Presentation snapshot no longer preserves base/stage-one/stage-two provenance");
    }

    private static void verifyCellTranslationPreservesCoordinatesAndPhase(String source) {
        String translation = compact(methodBody(
                source, "private static List<DetailCell> presentationCells("));
        require(translation.contains("newVoxel(cell.x,cell.y,cell.z)"),
                "Presentation translation no longer preserves authored coordinates");
        require(translation.contains("DetailPhase.valueOf(cell.phase.name())"),
                "Presentation translation no longer preserves semantic detail phase");
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        require(method >= 0, "Missing source method: " + signature);
        return bracedBody(source, source.indexOf('{', method), signature);
    }

    private static String bracedBody(String source, int openingBrace, String description) {
        require(openingBrace >= 0, "Missing body: " + description);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError("Unterminated body: " + description);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
