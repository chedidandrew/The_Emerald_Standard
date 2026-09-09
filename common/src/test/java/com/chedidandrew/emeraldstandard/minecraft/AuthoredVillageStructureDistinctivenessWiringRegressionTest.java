package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Guards the Minecraft-to-loader-neutral boundary for gold-master shape distinctiveness.
 *
 * <p>The production catalog depends on Minecraft block states and cannot be loaded by the common
 * test classpath. These checks make sure its catalog admission path still exports one palette-free
 * structural silhouette per active revision-7 master to the executable neutral validator.</p>
 */
public final class AuthoredVillageStructureDistinctivenessWiringRegressionTest {
    private static final int ACTIVE_MASTER_TARGET = 52;
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";
    private static final Pattern ACTIVE_REVISION = Pattern.compile(
            "LATEST_TEMPLATE_REVISION\\s*=\\s*9\\s*;");
    private static final Pattern SNAPSHOT_COLLECTION = Pattern.compile(
            "List\\s*<\\s*(?:WholeBuildingDistinctivenessValidator\\s*\\.\\s*)?"
                    + "StructuralSnapshot\\s*>\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*"
                    + "new\\s+ArrayList\\s*<>\\s*\\(\\s*\\)");

    private AuthoredVillageStructureDistinctivenessWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);
        String catalog = methodBody(source, "static void validateCatalog()");
        String descriptorValidation = methodBody(
                source,
                "private static CatalogValidationResult validateCatalogDescriptor(");
        String snapshotCollection = snapshotCollection(catalog);

        verifyActiveCatalog(
                source, catalog, descriptorValidation, snapshotCollection);
        verifyPaletteBlindStructuralSnapshot(source);
        verifyCatalogAdmissionInvokesDistinctivenessGate(catalog, snapshotCollection);
        verifyScopedInteriorFinishing(Path.of(args[0]), source);
        System.out.println("PASS authored village structure distinctiveness wiring regression");
    }

    private static void verifyActiveCatalog(
            String source,
            String catalog,
            String descriptorValidation,
            String snapshotCollection) {
        require(ACTIVE_REVISION.matcher(source).find(),
                "The active authored gold masters are not revision 7");
        require(catalog.contains(
                        "descriptor.templateRevision() == LATEST_TEMPLATE_REVISION")
                        && catalog.contains("activeDescriptors.add(descriptor)"),
                "Only active-revision descriptors may enter the distinctiveness snapshot set");
        require(descriptorValidation.contains(
                        "StructuralSnapshot snapshot = structuralSnapshot(canonical)")
                        && catalog.contains(
                                snapshotCollection + ".add(result.structuralSnapshot)"),
                "Validated active masters no longer enter the distinctiveness snapshot set");

        Pattern exactCount = Pattern.compile(
                "if\\s*\\(\\s*" + Pattern.quote(snapshotCollection)
                        + "\\s*\\.\\s*size\\s*\\(\\s*\\)\\s*!=\\s*"
                        + ACTIVE_MASTER_TARGET + "\\s*\\)"
                        + "\\s*\\{(?s:.*?)throw\\s+new\\s+");
        require(exactCount.matcher(catalog).find(),
                "Catalog validation no longer proves that all " + ACTIVE_MASTER_TARGET
                        + " active masters were compared");
    }

    private static void verifyScopedInteriorFinishing(Path root, String source) throws Exception {
        String interiors = Files.readString(root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "AuthoredInteriorRefinements.java"));
        require(source.contains("AuthoredInteriorRefinements.apply(base, metadata, materials, templateId)"),
                "Interior finishing is no longer part of production blueprint authoring");
        require(interiors.contains("ROOM_FINISH_TARGETS.contains(id)")
                        && interiors.contains("FROZEN_MASTERS.contains(id)"),
                "The reviewed furnishing pass must remain scoped to explicit master identities");
        String mat = methodBody(interiors, "private static boolean inlaidRoomMat(");
        require(mat.contains("claimed.contains(pos)") && mat.contains("claimed.addAll(cells)")
                        && mat.indexOf("return false;") < mat.indexOf("claimed.addAll(cells)"),
                "Room inlays must preflight complete non-overlapping footprints before writing");
        String rooms = methodBody(interiors, "private static void finishReviewedRooms(");
        require(rooms.contains("new BlockPos(x, room.getY(), z)")
                        && rooms.contains("wallBacked(b, origin.relative(along).relative(facing.getOpposite()))")
                        && rooms.contains("prior.distManhattan(origin) < 5"),
                "Compact groups must respect sampled room levels, backing and minimum separation");
        String placement = methodBody(interiors, "private static boolean placeScene(");
        require(placement.contains("m.reservedAir.contains(pos)")
                        && placement.contains("m.accessTargets.contains(pos)")
                        && placement.contains("solidPropsPreserveInteractionRoutes")
                        && placement.contains("support.state().isFaceSturdy"),
                "Furnishing must not bypass support or existing workstation circulation checks");
    }

    private static void verifyPaletteBlindStructuralSnapshot(String source) {
        require(source.contains("WholeBuildingDistinctivenessValidator.StructuralSnapshot")
                        || source.contains("import com.chedidandrew.emeraldstandard.core."
                                + "WholeBuildingDistinctivenessValidator.StructuralSnapshot;"),
                "Authored masters are not exported as loader-neutral structural snapshots");
        require(source.contains("WholeBuildingBlueprint.Voxel")
                        || source.contains("import com.chedidandrew.emeraldstandard.core."
                                + "WholeBuildingBlueprint.Voxel;"),
                "Authored master geometry is not translated to loader-neutral voxels");

        String snapshot = methodBody(source, "StructuralSnapshot structuralSnapshot(");
        require(snapshot.contains("blueprint.base"),
                "Distinctiveness snapshots do not derive from frozen base-building geometry");
        require(snapshot.contains("switch"),
                "Snapshot phase selection is not explicit and exhaustive");
        for (String phase : new String[] {"FOUNDATION", "FRAME", "SHELL", "ROOF", "OPENING"}) {
            require(caseReturns(snapshot, phase, true),
                    "Distinctiveness snapshots omit structural phase " + phase);
        }
        for (String phase : new String[] {"FIXTURE", "DECOR"}) {
            require(caseReturns(snapshot, phase, false),
                    "Distinctiveness snapshots include non-structural phase " + phase);
        }
        require(!snapshot.contains("paletteId")
                        && !snapshot.contains("dressingId")
                        && !snapshot.contains(".state")
                        && !snapshot.contains("getBlock("),
                "Palette, dressing, or block material leaked into structural similarity snapshots");
    }

    private static void verifyCatalogAdmissionInvokesDistinctivenessGate(
            String catalog,
            String snapshotCollection) {
        Pattern invocation = Pattern.compile(
                "WholeBuildingDistinctivenessValidator\\s*\\.\\s*validateSnapshots\\s*\\(\\s*"
                        + Pattern.quote(snapshotCollection) + "\\s*\\)\\s*\\.\\s*requireDistinct"
                        + "\\s*\\(\\s*\\)");
        require(invocation.matcher(catalog).find(),
                "Catalog admission does not invoke the loader-neutral distinctiveness validator");
    }

    private static String snapshotCollection(String catalog) {
        java.util.regex.Matcher matcher = SNAPSHOT_COLLECTION.matcher(catalog);
        require(matcher.find(),
                "Catalog validation does not declare a structural-snapshot collection");
        return matcher.group(1);
    }

    private static boolean caseReturns(String method, String phase, boolean expected) {
        Pattern phaseCase = Pattern.compile(
                "case\\s+(?:(?!->)[\\s\\S])*?\\b" + Pattern.quote(phase)
                        + "\\b(?:(?!->)[\\s\\S])*?->\\s*" + expected + "\\b");
        return phaseCase.matcher(method).find();
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
