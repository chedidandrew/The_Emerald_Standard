package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locks production wiring for authored footprint, access, and live site-search semantics. */
public final class VillageRuntimeStructureSafetyWiringRegressionTest {
    private static final String MANAGER =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "VillageProsperityManager.java";

    private VillageRuntimeStructureSafetyWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        String source = Files.readString(Path.of(args[0]).resolve(MANAGER));
        verifyAuthoritativeTerrainFootprint(source);
        verifyRuntimeSemanticAccess(source);
        verifyRequiredSafetyFixtures(source);
        verifyLiveSiteSearch(source);
        verifyFailureIsolation(source);
        System.out.println("PASS village runtime structure safety wiring regression");
    }

    private static void verifyAuthoritativeTerrainFootprint(String source) {
        String search = methodBody(source, "private static ProjectSiteSearch findProjectOrigin(");
        require(search.contains("blueprintPlacementPlan(level, provisionalOrigin, village, project).base()")
                        && search.contains("authoritativeGroundContactColumns(")
                        && search.contains("safeOrigin(")
                        && search.contains("authoritativeGroundContact"),
                "Production reservation no longer derives its terrain footprint from transformed base placements");
        String footprint = methodBody(
                source,
                "private static List<TerrainFoundationPlan.Column> authoritativeGroundContactColumns(");
        require(footprint.contains("TerrainFoundationPlan.groundContactColumns(authoritative)")
                        && footprint.contains("placement.isGroundAuthoritative()")
                        && footprint.contains("placement.isAccessClearance()"),
                "Optional dressing or semantic air leaked into the authoritative terrain footprint");
        String origin = methodBody(source, "private static ProjectSiteSearch safeOrigin(");
        require(origin.contains("authoritativeGroundContact")
                        && origin.contains("areaColumnsLoaded(")
                        && origin.contains("isNaturalProjectGround(")
                        && origin.contains("TerrainFoundationPlan.supportsTerrainRange("),
                "Site preflight no longer proves every transformed ground column loaded, natural, and bridgeable");
    }

    private static void verifyRuntimeSemanticAccess(String source) {
        String conversion = methodBody(source, "private static List<Placement> toBlueprintPlacements(");
        require(conversion.contains("AuthoredVillageStructures.Phase.DECOR")
                        && conversion.contains("PlacementRole.COSMETIC")
                        && conversion.contains(": role"),
                "Nonessential base decoration became structural repair authority, or doors/workstations lost authored criticality");
        String clearances = methodBody(source, "private static List<Placement> blueprintAccessClearances(");
        require(clearances.contains("reservedAir()")
                        && clearances.contains("entranceInside()")
                        && clearances.contains("accessTargets()")
                        && clearances.contains("verticalAccess()"),
                "Authored navigation metadata is not preserved in the runtime plan");
        String satisfied = methodBody(source, "private static boolean placementSatisfied(");
        require(satisfied.contains("placement.isAccessClearance()")
                        && satisfied.contains("current.isAir()"),
                "A blocked semantic aisle can still satisfy integrity");
        String construction = methodBody(
                source, "private static MaterializationBudget materializeDevelopment(");
        require(construction.contains("if (placement.isAccessClearance())")
                        && construction.contains("Semantic air is an integrity assertion"),
                "Construction can overwrite a player obstruction in reserved access space");
    }

    private static void verifyRequiredSafetyFixtures(String source) {
        String conversion = methodBody(source, "private static List<Placement> toBlueprintPlacements(");
        require(conversion.contains("requiredSafetyFixtures.contains(position)")
                        && conversion.contains("persistedRole"),
                "Validated Blueprint V2 lights can still be reduced to ordinary optional decor");
        String cumulative = methodBody(
                source, "private static Set<BlockPos> cumulativeRequiredSafetyFixturePositions(");
        require(cumulative.contains("List.of(base, stageOne, stageTwo)")
                        && cumulative.contains("requiredSafetyFixturePositions(authored)"),
                "Required lighting authority is no longer derived from each cumulative visual stage");
        String fixtures = methodBody(
                source, "private static Set<BlockPos> requiredSafetyFixturePositions(");
        require(fixtures.contains("state.getLightEmission() <= 0")
                        && fixtures.contains("LanternBlock.HANGING")
                        && fixtures.contains("Blocks.IRON_CHAIN")
                        && fixtures.contains("support = support.above()")
                        && fixtures.contains("Blocks.WALL_TORCH")
                        && fixtures.contains("HorizontalDirectionalBlock.FACING")
                        && fixtures.contains("emitter.below()"),
                "Required lantern/torch emitters no longer retain cross-phase attachment support");
        String placement = recordBody(source, "private record Placement(");
        require(source.contains("boolean requiredSafetyFixture) {")
                        && placement.contains("!requiredSafetyFixture")
                        && placement.contains("isStructuralAuthority()")
                        && placement.contains("role == PlacementRole.STRUCTURE || requiredSafetyFixture")
                        && placement.contains("isGroundAuthoritative()")
                        && placement.contains("role != PlacementRole.COSMETIC"),
                "Safety fixtures no longer behave as required cells while preserving v1 roles");
        String footprint = methodBody(
                source,
                "private static List<TerrainFoundationPlan.Column> authoritativeGroundContactColumns(");
        String supports = methodBody(
                source, "private static List<Placement> withFoundationSupports(");
        require(footprint.contains("!placement.isGroundAuthoritative()")
                        && supports.contains("placement.isGroundAuthoritative()"),
                "Hanging safety fixtures can distort the structural terrain footprint");
        String hash = methodBody(source, "private static String blueprintPlanHash(");
        require(hash.contains("placement.role")
                        && !hash.contains("requiredSafetyFixture"),
                "Runtime lighting enforcement changed persisted Blueprint V2 hash identity");
    }

    private static void verifyLiveSiteSearch(String source) {
        String search = methodBody(source, "private static ProjectSiteSearch findProjectOrigin(");
        require(search.contains("projectSiteOffsets(project.materializationFailures)")
                        || (search.contains("projectSiteOffsets(")
                                && search.contains("project.materializationFailures")),
                "Repeated failures cannot expand the deterministic site frontier");
        String materialization = methodBody(
                source, "private static MaterializationBudget materializeDevelopment(");
        require(materialization.contains("INCOMPLETE_UNLOADED")
                        && materialization.contains(
                                "deferVillageProjectMaterialization(")
                        && materialization.contains("gameTime, true"),
                "An unloaded low-view-distance frontier can still hot-loop and starve later work");
        require(materialization.contains("claimNextDueVillageVisualProject(")
                        && materialization.contains("selectedProjectId"),
                "Due projects are no longer claimed through the persisted per-village rotation");
        require(search.contains("project.siteSearchCursor")
                        && search.contains("project.siteSearchSawUnloadedCandidate")
                        && search.contains("checkpointProjectSiteSearch("),
                "Candidate progress can restart at candidate zero after a server restart");
        String checkpoint = methodBody(source,
                "private static void checkpointProjectSiteSearch(");
        require(checkpoint.contains("recordVillageProjectSiteSearchProgress("),
                "Live site search no longer publishes its persistent monotonic checkpoint");
    }

    private static void verifyFailureIsolation(String source) {
        String tick = methodBody(source, "public static void tick(");
        require(tick.contains("mismatch.remainingBudget")
                        && tick.contains("gameTime, true"),
                "A blueprint mismatch can discard prior work budget or release a valid reservation");
        String materialization = methodBody(
                source, "private static MaterializationBudget materializeDevelopment(");
        require(materialization.contains("withRemainingBudget(")
                        && materialization.contains("processedVillages"),
                "A blueprint mismatch no longer reports already-consumed per-pass budgets");
    }

    private static String methodBody(String source, String signature) {
        int declaration = source.indexOf(signature);
        require(declaration >= 0, "Missing source method: " + signature);
        int openingBrace = source.indexOf('{', declaration);
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
        throw new AssertionError("Unterminated method: " + signature);
    }

    private static String recordBody(String source, String signature) {
        return methodBody(source, signature);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
