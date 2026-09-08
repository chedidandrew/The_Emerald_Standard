package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prevents the exhaustive integration-smoke catalog gate from returning to huge text keys. */
public final class CatalogAdmissionPerformanceWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";
    private static final String PROSPERITY_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "VillageProsperityManager.java";

    private CatalogAdmissionPerformanceWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path root = Path.of(args[0]);
        String authored = Files.readString(root.resolve(AUTHORED_SOURCE));
        String prosperity = Files.readString(root.resolve(PROSPERITY_SOURCE));

        verifyParallelButDeterministicMasterAdmission(authored);
        verifyExactCompactLegacySignatures(prosperity);
        System.out.println("PASS catalog admission performance wiring regression");
    }

    private static void verifyParallelButDeterministicMasterAdmission(String source) {
        String catalog = methodBody(source, "static void validateCatalog()");
        String descriptor = methodBody(
                source,
                "private static CatalogValidationResult validateCatalogDescriptor(");
        require(catalog.contains("activeDescriptors.parallelStream()")
                        && catalog.contains(
                                ".map(AuthoredVillageStructures::validateCatalogDescriptor)")
                        && catalog.contains("List<CatalogValidationResult> results")
                        && catalog.contains("for (CatalogValidationResult result : results)"),
                "Authored masters are no longer checked concurrently and consumed deterministically");
        for (String dimension : new String[] {
                "descriptor.dressingIds()",
                "VillageArchitecture.BiomeDialect.values()",
                "descriptor.paletteIds()",
                "VillageArchitecture.Character.values()"
        }) {
            require(descriptor.contains(dimension),
                    "Parallel descriptor admission dropped matrix dimension " + dimension);
        }
        require(descriptor.contains("doodadSeed < 4L"),
                "Parallel descriptor admission dropped deterministic doodad variants");
        require(source.contains("LIGHTING_COMPOSITION_CACHE")
                        && source.contains("VALIDATED_LIGHTING_SNAPSHOTS")
                        && source.contains("new LightingAdmissionKey(")
                        && source.contains("VALIDATED_LIGHTING_SNAPSHOTS.computeIfAbsent("),
                "Exact interior-light admission lost its bounded topology/composition caches");
    }

    private static void verifyExactCompactLegacySignatures(String source) {
        String validation = methodBody(source, "private static void validateModularProjectTemplates()");
        String signature = methodBody(
                source,
                "private static ModularCellSignature modularCellSignature(");
        require(validation.contains("Set<ModularCellSignature> baseRecipeSignatures")
                        && validation.contains(
                                "Set<ModularLayerSignature> completeRecipeSignatures"),
                "Legacy clone admission no longer uses compact structural keys");
        require(signature.contains("new ArrayList<>(cells)")
                        && signature.contains("canonical.sort(MODULAR_CELL_ORDER)")
                        && signature.contains("List.copyOf(canonical)"),
                "Legacy structural key is not canonical and immutable");
        require(!signature.contains("StringBuilder")
                        && !signature.contains(".append(")
                        && !signature.contains("toString()"),
                "Legacy admission returned to allocation-heavy textual block-state signatures");
        require(source.contains(
                        "private record ModularCellSignature(List<ModularVillageStructures.Cell> cells)")
                        && source.contains("private record ModularLayerSignature("),
                "Exact coordinate/state equality keys were removed from legacy admission");
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
