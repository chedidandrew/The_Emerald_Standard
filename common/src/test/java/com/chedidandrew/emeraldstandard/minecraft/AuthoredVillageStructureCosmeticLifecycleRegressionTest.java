package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Protects the one-shot, non-authoritative lifecycle of Blueprint V2 yard dressing. */
public final class AuthoredVillageStructureCosmeticLifecycleRegressionTest {
    private static final String SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "VillageProsperityManager.java";

    private AuthoredVillageStructureCosmeticLifecycleRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        String source = Files.readString(Path.of(args[0]).resolve(SOURCE));

        verifyLayerRoleBoundary(source);
        verifyOptionalConstruction(source);
        verifyNonAuthoritativeAudits(source);
        verifyGroundedOptionalSupports(source);
        verifyAttachmentOrdering(source);
        verifyBoundsAndHashOwnership(source);
        System.out.println("PASS authored village cosmetic lifecycle regression");
    }

    private static void verifyLayerRoleBoundary(String source) {
        String roles = enumBody(source, "private enum PlacementRole");
        require(roles.contains("STRUCTURE")
                        && roles.contains("COSMETIC")
                        && roles.contains("COSMETIC_SUPPORT"),
                "Blueprint placement roles lost the structure/cosmetic boundary");

        String plan = methodBody(source,
                "private static BlueprintPlacementPlan blueprintPlacementPlan(");
        require(plan.contains(
                        "blueprint.base(), PlacementRole.STRUCTURE, requiredSafetyFixtures"),
                "Authored base cells are no longer economically authoritative structure");
        require(plan.contains(
                        "blueprint.stageOne(), PlacementRole.COSMETIC, requiredSafetyFixtures"),
                "Authored stage-one dressing is no longer cosmetic");
        require(plan.contains(
                        "blueprint.stageTwo(), PlacementRole.COSMETIC, requiredSafetyFixtures"),
                "Authored stage-two dressing is no longer cosmetic");
        require(plan.contains("project.designSeed"),
                "Production doodad selection no longer uses the persisted design seed");

        String gallery = methodBody(source,
                "static List<StructureGalleryBlock> galleryProjectBlueprint(");
        String galleryPlan = methodBody(source,
                "private static List<Placement> galleryProjectPlacements(");
        require(gallery.contains("galleryProjectPlacements(")
                        && galleryPlan.contains("project.designSeed = signature"),
                "The review gallery no longer exercises per-project doodad variants");
    }

    private static void verifyOptionalConstruction(String source) {
        String construction = methodBody(source,
                "private static MaterializationBudget materializeDevelopment(");
        require(construction.contains("if (placement.isCosmetic())")
                        && construction.contains("index++"),
                "An unloaded cosmetic can block or retry construction");
        require(construction.contains(
                        "placement.isCosmetic() && project.manualRepairRequired"),
                "A structural integrity rewind can regenerate destroyed cosmetics");
        require(occurrences(construction,
                        "placement.isTrail() || placement.isCosmetic()") >= 3,
                "Protected, occupied, or failed cosmetics no longer become permanent safe gaps");
        require(construction.contains("cosmeticPlacementSupported(level, origin, target, placement)"),
                "Cosmetics can be written without their bounded support check");
        int verificationLoop = construction.indexOf("for (int verifyIndex");
        require(verificationLoop >= 0
                        && construction.substring(verificationLoop).contains(
                                "if (placement.isTrail() || placement.isCosmetic())"),
                "Completion verification can make a skipped or removed cosmetic authoritative");
    }

    private static void verifyNonAuthoritativeAudits(String source) {
        String audit = methodBody(source,
                "private static void reconcileOneMaterializedProject(");
        require(audit.contains("if (placement.isTrail() || placement.isCosmetic())"),
                "Integrity audits no longer ignore missing cosmetics");
        require(audit.contains("if (placement.isCosmetic())")
                        && audit.contains("firstRequiredAddition")
                        && audit.contains("!placement.isCosmetic()"),
                "Cosmetic-only suffixes can re-enter the guarded repair queue");

        String site = methodBody(source,
                "private static VillageMaterializationPolicy.SiteAvailability mayUseProjectSite(");
        require(site.contains("!placement.isCosmetic()")
                        && site.contains("placement.isTrail() || placement.isCosmetic()"),
                "Optional yard occupancy can reject an otherwise safe required building site");
    }

    private static void verifyGroundedOptionalSupports(String source) {
        String supports = methodBody(source,
                "private static List<Placement> withFoundationSupports(");
        require(supports.contains("authoritativeGroundContactColumns")
                        && supports.contains("TerrainFoundationPlan")
                        && supports.contains(".groundContactColumns(authoritative)")
                        && supports.contains("placement.isGroundAuthoritative()")
                        && supports.contains("Placement.cosmeticSupport("),
                "Only genuine non-cosmetic ground contact may make a synthesized support structural");

        String satisfied = methodBody(source, "private static boolean placementSatisfied(");
        require(satisfied.contains("PlacementRole.COSMETIC_SUPPORT"),
                "Natural ground no longer satisfies an optional generated support cell");

        String grounded = methodBody(source,
                "private static boolean cosmeticPlacementSupported(");
        require(grounded.contains("TerrainFoundationPlan.MAX_TERRAIN_DROP + 1")
                        && grounded.contains("isNaturalProjectGround(")
                        && grounded.contains("placement.state.canSurvive(level, target)")
                        && grounded.contains("Direction.values()")
                        && grounded.contains("target.relative(direction)")
                        && grounded.contains("positionColumnLoaded("),
                "Cosmetic placement no longer proves bounded terrain and valid face/chain support");
    }

    private static void verifyAttachmentOrdering(String source) {
        String conversion = methodBody(source,
                "private static List<Placement> toBlueprintPlacements(");
        require(conversion.contains("cosmeticAttachmentPriority")
                        && conversion.contains("-placement.dy"),
                "Optional hanging runs are no longer ordered top-down before their lamps");
        String priority = methodBody(source,
                "private static int cosmeticAttachmentPriority(");
        require(priority.contains("Blocks.IRON_CHAIN")
                        && priority.contains("LanternBlock.HANGING"),
                "Chain and hanging-lantern attachment priority disappeared");
    }

    private static void verifyBoundsAndHashOwnership(String source) {
        String bounds = methodBody(source, "private static ProjectBounds bounds(");
        require(bounds.contains("if (placement.isTrail())")
                        && !bounds.contains("placement.isCosmetic()"),
                "Cosmetics must remain inside the project's reserved bounds");

        String hash = methodBody(source, "private static String blueprintPlanHash(");
        require(hash.contains("plan.canonicalStageOne")
                        && hash.contains("plan.canonicalStageTwo")
                        && hash.contains("placement.role"),
                "Cosmetic cells or their role disappeared from the immutable blueprint hash");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String enumBody(String source, String signature) {
        return blockBody(source, signature, "enum");
    }

    private static String methodBody(String source, String signature) {
        return blockBody(source, signature, "method");
    }

    private static String blockBody(String source, String signature, String kind) {
        int declaration = source.indexOf(signature);
        require(declaration >= 0, "Missing source " + kind + ": " + signature);
        int openingBrace = source.indexOf('{', declaration);
        require(openingBrace >= 0, "Missing " + kind + " body: " + signature);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError("Unterminated " + kind + " body: " + signature);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
