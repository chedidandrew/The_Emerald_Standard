package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Protects deterministic, role-authored yard dressing for the revision-2 gold masters.
 *
 * <p>This is intentionally a semantic source-boundary test. Minecraft block states are not on the
 * loader-neutral test classpath, while exact block-coordinate snapshots would make harmless art
 * iteration painful. The checks instead prove that every active master reaches a role-specific,
 * non-empty doodad pass through the persisted dressing stages and that the review gallery still
 * resolves those same production plans.</p>
 */
public final class AuthoredVillageStructureDoodadWiringRegressionTest {
    private static final int ACTIVE_MASTER_TARGET = 52;
    private static final String MINECRAFT_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/";

    private AuthoredVillageStructureDoodadWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path root = Path.of(args[0]);
        String authored = read(root, "AuthoredVillageStructures.java");
        String refinements = read(root, "AuthoredDoodadRefinements.java");
        String prosperity = read(root, "VillageProsperityManager.java");
        String gallery = read(root, "StructureGallery.java");

        verifyAllActiveMastersUseRoleDoodads(authored);
        verifyDoodadsUsePersistedStages(authored);
        verifyDeterministicNonEmptyHelpers(authored);
        verifyCraftedMicroScenes(authored);
        verifyBenchCapsAdaptBelowExistingCanopies(authored);
        verifyCompactCottageBenchScopeAndSupports(authored, refinements);
        verifyDoodadRegionAvoidsUnintendedPois(authored);
        verifyCatalogAndGalleryUseProduction(authored, prosperity, gallery);
        System.out.println("PASS authored village deterministic doodad wiring regression");
    }

    private static void verifyCompactCottageBenchScopeAndSupports(
            String authored, String refinements) {
        String plan = methodBody(authored, "static Blueprint plan(");
        Pattern gatedHook = Pattern.compile(
                "if\\s*\\(\\s*templateRevision\\s*>=\\s*3\\s*\\)\\s*\\{\\s*"
                        + "AuthoredDoodadRefinements\\.refineCompactCottageRearBench\\s*\\(\\s*"
                        + "stageTwo\\s*,\\s*metadata\\s*,\\s*materials\\s*,\\s*templateId\\s*\\)");
        require(gatedHook.matcher(plan).find(),
                "Compact cottage bench must only run on revision-3 stage two with its stable ID");
        int hook = plan.indexOf("AuthoredDoodadRefinements.refineCompactCottageRearBench(");
        require(hook > plan.indexOf("appendPresentationStageTwo(")
                        && hook < plan.indexOf("ensureCumulativeComfortableInteriorLighting(", hook),
                "Cottage bench refinement must see presentation supports before final lighting");

        String scope = methodBody(refinements, "private static boolean hasCompactCottageRearBench(");
        List<String> ids = List.of("cottage_hearth_01", "cottage_garden_02",
                "cottage_courtyard_03", "cottage_bay_04");
        for (String id : ids) {
            require(scope.contains("\"" + id + "\""), "Missing compact-cottage bench ID: " + id);
        }
        require(Pattern.compile("\"[^\"]+\"").matcher(scope).results().count() == ids.size()
                        && scope.contains("default -> false"),
                "The cottage refinement must not silently expand beyond its four reviewed IDs");
        String dispatch = methodBody(refinements, "static void refineCompactCottageRearBench(");
        require(dispatch.contains("stage.templateRevision < 3")
                        && dispatch.contains("!hasCompactCottageRearBench(templateId)"),
                "Direct bench refinement calls must preserve old revisions and unrelated motifs");
        for (String bench : List.of("addDoodadBenchX", "addDoodadBenchZ")) {
            require(!methodBody(authored, "private static void " + bench + "(")
                            .contains("refineCompactCottageRearBench"),
                    "Compact cottage composition must not change the global public bench motif");
        }

        String lower = methodBody(refinements, "private static void lowerExactRearBench(");
        require(lower.contains("hasState(stage") && lower.contains("newBackCenter")
                        && lower.contains("metadata.reservedAir.contains(newBackCenter)")
                        && lower.contains("metadata.accessTargets.contains(newBackCenter)"),
                "Only the exact original bench may be lowered, with its new backrest cell clear");
        require(lower.indexOf("hasSharedBenchSupport(stage, tallAssembly)")
                        < lower.indexOf("stage.remove(")
                        && !lower.contains("Phase.FOUNDATION")
                        && !lower.contains("dressingPlant"),
                "Shared supports must be checked before removal; seats, base and plant stay intact");
        String supports = methodBody(refinements, "private static boolean hasSharedBenchSupport(");
        require(supports.contains("Direction.UP") && supports.contains("Direction.NORTH")
                        && supports.contains("Direction.SOUTH") && supports.contains("Direction.WEST")
                        && supports.contains("Direction.EAST")
                        && supports.contains("stage.isOccupied(contact)")
                        && supports.contains("neighbor == null")
                        && supports.contains("Phase.ROOF") && supports.contains("Phase.FRAME"),
                "The bench must preserve overhead, lateral and previous-stage architectural supports");
        String bases = methodBody(refinements, "private static void preserveCottageSeatBases(");
        require(bases.contains("Blocks.DIRT_PATH") && bases.contains("Phase.FOUNDATION")
                        && bases.contains("seat.state().is(p.timber())")
                        && bases.contains("seat.state().is(p.wall())")
                        && bases.contains("p.foundation().defaultBlockState()"),
                "Solid cottage seat/planter supports must not leave decaying path blocks below them");
    }

    private static void verifyAllActiveMastersUseRoleDoodads(String source) {
        require(Pattern.compile("(?:LATEST_)?TEMPLATE_REVISION\\s*=\\s*3\\s*;")
                        .matcher(source)
                        .find(),
                "The active authored gold masters are not revision 3");

        String plan = methodBody(source, "static Blueprint plan(");
        for (MasterExpectation master : expectedMasters()) {
            if (master.templateId().equals("market_lane_04")) {
                String market = methodBody(plan, "case \"market_lane_04\" ->");
                require(invokes(market, "marketLane", "base", "metadata", "materials")
                                && Pattern.compile("if\\s*\\(\\s*templateRevision\\s*==\\s*3\\s*\\)\\s*\\{\\s*"
                                        + "AuthoredMarketRefinements\\.finishLane\\(base, metadata, materials\\);")
                                        .matcher(market).find(),
                        "Market lane must retain its original production recipe and scoped revision-3 refinement");
                continue;
            }
            Pattern activeSelection = Pattern.compile(
                    "case\\s+\\\"" + Pattern.quote(master.templateId()) + "\\\"\\s*->\\s*"
                            + Pattern.quote(master.masterMethod())
                            + "\\s*\\(\\s*base\\s*,\\s*metadata\\s*,\\s*materials\\s*\\)");
            require(activeSelection.matcher(plan).find(),
                    "Active revision-2 master is no longer selected by production: "
                            + master.masterMethod());
        }

        Map<String, String> roles = expectedRoleHelpers();
        require(roles.size() == 10,
                "Regression contract must cover the ten production project roles");
        require(expectedMasters().size() == ACTIVE_MASTER_TARGET,
                "Regression contract must cover all " + ACTIVE_MASTER_TARGET
                        + " revision-2 masters");
    }

    private static void verifyDoodadsUsePersistedStages(String source) {
        String plan = methodBody(source, "static Blueprint plan(");
        require(invokes(plan, "appendDressingStageOne", "stageOne", "metadata", "materials",
                        "dressingId"),
                "Production plan no longer passes persisted dressingId into dressing stage one");
        require(invokes(plan, "appendDressingStageTwo", "stageTwo", "metadata", "materials",
                        "dressingId"),
                "Production plan no longer passes persisted dressingId into dressing stage two");
        require(plan.contains("stageOne.values()") && plan.contains("stageTwo.values()"),
                "Production blueprint no longer stores both doodad-bearing dressing stages");

        String stageOne = methodBody(source, "private static void appendDressingStageOne(");
        String stageTwo = methodBody(source, "private static void appendDressingStageTwo(");
        require(stageOne.contains("switch") && stageOne.contains("m.type"),
                "Dressing stage one no longer dispatches doodads by project role");
        require(stageTwo.contains("switch") && stageTwo.contains("m.type"),
                "Dressing stage two no longer dispatches doodads by project role");
        for (Map.Entry<String, String> role : expectedRoleHelpers().entrySet()) {
            require(caseInvokes(stageOne, role.getKey(), role.getValue(), false),
                    "Dressing stage one does not add front/primary doodads for " + role.getKey());
            require(caseInvokes(stageTwo, role.getKey(), role.getValue(), true),
                    "Dressing stage two does not add rear/secondary doodads for " + role.getKey());
        }
    }

    private static void verifyDeterministicNonEmptyHelpers(String source) {
        for (String helper : expectedRoleHelpers().values()) {
            require(Pattern.compile("private\\s+static\\s+void\\s+" + Pattern.quote(helper)
                            + "\\s*\\(")
                            .matcher(source)
                            .find(),
                    "Missing role-specific doodad helper: " + helper);
            String body = methodBody(source, "private static void " + helper + "(");
            require(body.contains("deterministicDoodadVariant("),
                    helper + " no longer selects a deterministic doodad variant");
            require(body.contains("dressingId"),
                    helper + " no longer derives its selection from persisted dressingId");
            require(Pattern.compile("(?:stage\\s*\\.\\s*(?:put|force)\\s*\\(|"
                            + "\\b(?:put|add)[A-Z][A-Za-z0-9_]*\\s*\\()")
                            .matcher(body)
                            .find(),
                    helper + " became an empty/no-op doodad pass");
        }

        String selector = methodBody(source, "private static int deterministicDoodadVariant(");
        for (String stableInput : new String[] {"m", "dressingId", "salt", "variants"}) {
            require(Pattern.compile("\\b" + Pattern.quote(stableInput) + "\\b")
                            .matcher(selector)
                            .find(),
                    "Deterministic doodad selector ignores stable input: " + stableInput);
        }
        require(selector.contains("return"),
                "Deterministic doodad selector does not return a variant");
        require(!Pattern.compile("(?:ThreadLocalRandom|new\\s+Random|Math\\s*\\.\\s*random|"
                        + "nanoTime|currentTimeMillis)")
                        .matcher(selector)
                        .find(),
                "Doodad selector uses runtime randomness/time instead of persisted design data");
    }

    private static void verifyDoodadRegionAvoidsUnintendedPois(String source) {
        String doodads = sourceRegion(
                source,
                "private static void appendPresentationStageOne(",
                "static void validateCatalog()");
        for (String block : List.of(
                "BARREL",
                "BED",
                "BELL",
                "BLAST_FURNACE",
                "BREWING_STAND",
                "CARTOGRAPHY_TABLE",
                "CAULDRON",
                "COMPOSTER",
                "FLETCHING_TABLE",
                "GRINDSTONE",
                "LECTERN",
                "LOOM",
                "SMITHING_TABLE",
                "SMOKER",
                "STONECUTTER")) {
            require(!Pattern.compile("\\bBlocks\\s*\\.\\s*" + block + "\\b")
                            .matcher(doodads)
                            .find(),
                    "Cosmetic doodad region introduces the villager POI Blocks." + block);
        }
        for (String currencyBlock : List.of("EMERALD_BLOCK", "GOLD_BLOCK")) {
            require(!Pattern.compile("\\bBlocks\\s*\\.\\s*" + currencyBlock + "\\b")
                            .matcher(doodads)
                            .find(),
                    "Cosmetic doodad region uses reserved currency scenery Blocks."
                            + currencyBlock);
        }
    }

    private static void verifyCraftedMicroScenes(String source) {
        String forecourt = methodBody(source, "private static void addForecourtRoleAnchor(");
        require(forecourt.contains("tryForecourtMicroScene(")
                        && !forecourt.contains("tryDressingPedestal("),
                "Forecourt cues regressed to isolated one-block pedestals");
        String safeForecourt = methodBody(source, "private static boolean tryForecourtMicroScene(");
        require(safeForecourt.contains("canPlaceDoodad(")
                        && safeForecourt.contains("Blocks.DIRT_PATH")
                        && safeForecourt.contains("Blocks.FLOWERING_AZALEA")
                        && safeForecourt.contains("Blocks.RAIL")
                        && safeForecourt.contains("minSceneX")
                        && safeForecourt.contains("maxSceneX")
                        && safeForecourt.contains("upperRoofSlab(p)")
                        && safeForecourt.contains("new BlockPos(x, 3, z)"),
                "Forecourt micro-scenes lost safe placement, stepped planting, a coherent pallet, "
                        + "continuous wear, or top binding");

        Map<String, List<String>> craft = new LinkedHashMap<>();
        craft.put("tryDressingLamp", List.of(
                "LanternBlock.HANGING", "Blocks.IRON_CHAIN", "single one-cell log",
                "Direction.Axis.Y", "p.roofStairs", "RotatedPillarBlock.AXIS"));
        craft.put("addStackedLogRackZ", List.of(
                "Direction.Axis.X", "firstStrapZ", "lastStrapZ", "Blocks.IRON_BARS",
                "p.roofStairs"));
        craft.put("addDoodadBenchX", List.of(
                "openDoodadTrapdoor", "dressingPlant", "new int[] {minX, minX + length - 1}",
                "Blocks.DIRT_PATH"));
        craft.put("addDoodadBenchZ", List.of(
                "openDoodadTrapdoor", "dressingPlant", "new int[] {minZ, minZ + length - 1}",
                "Blocks.DIRT_PATH"));
        craft.put("addSafeCampfireNook", List.of(
                "p.entryStairs", "HEAVY_WEIGHTED_PRESSURE_PLATE", "RotatedPillarBlock.AXIS"));
        craft.put("addGardenWorkCorner", List.of(
                "Blocks.FLOWERING_AZALEA", "Blocks.MOSS_CARPET", "Blocks.IRON_TRAPDOOR"));
        craft.put("addPlanterRunX", List.of(
                "Math.floorMod", "upperRoofSlab", "Blocks.FLOWERING_AZALEA"));
        craft.put("addPlanterRunZ", List.of(
                "Math.floorMod", "upperRoofSlab", "Blocks.FLOWERING_AZALEA"));
        craft.put("addCrateCluster", List.of(
                "non-container", "four-slat pallet", "openDoodadTrapdoor", "Blocks.RAIL",
                "z + 1"));
        craft.put("addHandCart", List.of(
                "openDoodadTrapdoor", "axleZ", "centerX, 4, axleZ", "dz <= 2", "dz <= 4"));
        craft.put("addHitchingRailZ", List.of(
                "p.fence.defaultBlockState()", "Blocks.IRON_CHAIN", "Blocks.IRON_BARS",
                "outerWearX", "companionX", "Blocks.HAY_BLOCK"));
        craft.put("addFeedTroughZ", List.of(
                "Blocks.CARPET.yellow()", "Blocks.CARPET.brown()", "endZ"));
        craft.put("addHayPile", List.of(
                "crosswiseBale", "lengthwiseBale", "Blocks.RAIL", "Blocks.IRON_BARS",
                "Blocks.CARPET.yellow()"));
        craft.put("addMaterialPile", List.of(
                "stockX", "Blocks.COBBLESTONE_WALL", "Blocks.GRAVEL", "p.roofStairs",
                "Blocks.CARPET.black()"));
        craft.put("addToolRackZ", List.of(
                "Blocks.IRON_BARS", "upperRoofSlab", "Blocks.IRON_TRAPDOOR"));
        craft.put("addTargetRackZ", List.of(
                "backstopX", "firingX", "Blocks.IRON_BARS", "Blocks.TARGET"));
        for (Map.Entry<String, List<String>> entry : craft.entrySet()) {
            String signature = entry.getKey().equals("tryDressingLamp")
                    ? "private static boolean " + entry.getKey() + "("
                    : "private static void " + entry.getKey() + "(";
            String body = methodBody(source, signature);
            for (String feature : entry.getValue()) {
                require(body.contains(feature),
                        entry.getKey() + " lost crafted micro-scene feature: " + feature);
            }
        }
        require(!methodBody(source, "private static void addCrateCluster(")
                        .contains("Blocks.CHEST"),
                "Cosmetic crate clusters must remain non-container scenery");
        require(!methodBody(source, "private static void addMaterialPile(")
                        .contains("putDressingPedestal("),
                "Material piles must not regress to a single square stock pedestal");
        require(!methodBody(source, "private static boolean tryDressingLamp(")
                        .contains("armStepX * 2"),
                "Freestanding lamp must retain its short one-cell bracket");
        String doodadRegion = sourceRegion(
                source,
                "private static void appendPresentationStageOne(",
                "static void validateCatalog()");
        require(!doodadRegion.contains("Blocks.CHEST")
                        && !doodadRegion.contains("Blocks.BARREL"),
                "Production yard scenes must not regenerate loot-bearing cargo containers");

        String handCartSafety = methodBody(source, "private static boolean canPlaceHandCart(");
        require(handCartSafety.contains("centerX, 4, axleZ")
                        && handCartSafety.contains("dz <= 2")
                        && handCartSafety.contains("dz <= 4")
                        && handCartSafety.contains("wheelX")
                        && handCartSafety.contains("canPlaceDoodad(stage, m, footprint)"),
                "Detailed cart footprint is no longer collision/reserved-air preflighted");
    }

    private static void verifyCatalogAndGalleryUseProduction(
            String authored, String prosperity, String gallery) {
        String catalog = methodBody(authored, "static void validateCatalog()");
        String descriptorValidation = methodBody(
                authored,
                "private static CatalogValidationResult validateCatalogDescriptor(");
        require(catalog.contains("descriptor.templateRevision() == LATEST_TEMPLATE_REVISION"),
                "Catalog validation no longer filters to the active revision");
        require(descriptorValidation.contains("Blueprint blueprint = plan(")
                        && descriptorValidation.contains(
                                "for (String dressing : descriptor.dressingIds())"),
                "Catalog validation no longer exercises every production dressing through plan()");
        require(Pattern.compile("activeStructuralSnapshots\\s*\\.\\s*size\\s*\\(\\s*\\)"
                        + "\\s*!=\\s*" + ACTIVE_MASTER_TARGET)
                        .matcher(catalog)
                        .find(),
                "Catalog validation no longer requires exactly " + ACTIVE_MASTER_TARGET
                        + " active masters");

        require(gallery.contains("VillageProsperityManager.galleryProjectBlueprint("),
                "Structure gallery bypasses the production project bridge");
        String galleryBridge = methodBody(prosperity, "static List<StructureGalleryBlock> "
                + "galleryProjectBlueprint(");
        require(galleryBridge.contains("galleryProjectPlacements("),
                "Gallery placement bridge must use the shared exact production identity");
        galleryBridge = methodBody(prosperity, "private static List<Placement> galleryProjectPlacements(");
        require(galleryBridge.contains("project.designDressingId = dressingId"),
                "Gallery does not persist the selected dressingId on its production project");
        require(galleryBridge.contains("blueprintProjectTemplate("),
                "Gallery project bridge bypasses the production placement pipeline");

        String placementPlan = methodBody(
                prosperity, "private static BlueprintPlacementPlan blueprintPlacementPlan(");
        require(invokes(
                        placementPlan,
                        "AuthoredVillageStructures.plan",
                        "project.type",
                        "project.designTemplateId",
                        "project.designTemplateRevision",
                        "project.designPaletteId",
                        "project.designDressingId",
                        "character",
                        "dialect",
                        "project.designSeed"),
                "Production placement pipeline must pass the project's persisted designSeed "
                        + "into the authored doodad selector");

        require(Pattern.compile("long\\s+doodadSeed\\s*\\)")
                        .matcher(authored)
                        .find()
                        && authored.contains("metadata.doodadSeed = doodadSeed"),
                "Authored plan no longer captures the per-project doodad seed");
        String selector = methodBody(authored, "private static int deterministicDoodadVariant(");
        require(selector.contains("m.doodadSeed"),
                "Deterministic doodad selector ignores the project's persisted designSeed");

        require(galleryBridge.contains("project.designSeed = signature"),
                "Gallery no longer supplies a stable project-specific doodad seed");

        require(descriptorValidation.contains(
                                "for (long doodadSeed = 0L; doodadSeed < 4L; doodadSeed++)")
                        && descriptorValidation.contains("doodadSeed);"),
                "Catalog validation no longer exercises all four deterministic doodad variants");
    }

    private static List<MasterExpectation> expectedMasters() {
        return List.of(
                new MasterExpectation("cottage_hearth_01", "cottageHearthLandmark"),
                new MasterExpectation("cottage_garden_02", "cottageGlasshouse"),
                new MasterExpectation("cottage_courtyard_03", "cottageCourtyard"),
                new MasterExpectation("cottage_bay_04", "cottageBayCompact"),
                new MasterExpectation("cottage_longhouse_05", "cottageLonghouse"),
                new MasterExpectation("cottage_orchardstead_06", "cottageOrchardstead"),
                new MasterExpectation("house_cross_01", "houseCrossGabled"),
                new MasterExpectation("house_dormer_02", "houseMansard"),
                new MasterExpectation("house_arcade_03", "houseArcade"),
                new MasterExpectation("house_hall_04", "houseHallCompact"),
                new MasterExpectation("house_splitwing_05", "houseSplitwing"),
                new MasterExpectation("house_towercourt_06", "houseTowercourt"),
                new MasterExpectation("inn_gallery_01", "innBalcony"),
                new MasterExpectation("inn_coachhouse_02", "innCoachhouse"),
                new MasterExpectation("inn_wayfarer_03", "innWayfarerCompact"),
                new MasterExpectation("inn_tavern_04", "innTavern"),
                new MasterExpectation("inn_courtyard_05", "innCourtyard"),
                new MasterExpectation("warehouse_bay_01", "warehouseSawtooth"),
                new MasterExpectation("warehouse_crane_02", "warehouseCraneHall"),
                new MasterExpectation("warehouse_gabled_03", "warehouseGabledCompact"),
                new MasterExpectation("warehouse_wharf_04", "warehouseWharf"),
                new MasterExpectation("warehouse_basilica_05", "warehouseBasilica"),
                new MasterExpectation("granary_loft_01", "granaryRaisedBarn"),
                new MasterExpectation("granary_windmill_02", "granaryWindmill"),
                new MasterExpectation("granary_cruck_03", "granaryCruckCompact"),
                new MasterExpectation("granary_stilt_04", "granaryStilt"),
                new MasterExpectation("granary_silocomplex_05", "granarySiloComplex"),
                new MasterExpectation("smithy_courtyard_01", "smithyOpenForge"),
                new MasterExpectation("smithy_hammerhall_02", "smithyHammerhall"),
                new MasterExpectation("smithy_lane_03", "smithyLaneCompact"),
                new MasterExpectation("smithy_corner_04", "smithyCorner"),
                new MasterExpectation("smithy_foundry_05", "smithyFoundry"),
                new MasterExpectation("mine_headframe_01", "mineHeadframeLandmark"),
                new MasterExpectation("mine_winding_house_02", "mineWindingHouse"),
                new MasterExpectation("mine_adit_03", "mineAditCompact"),
                new MasterExpectation("mine_drift_04", "mineDrift"),
                new MasterExpectation("mine_quarry_05", "mineQuarry"),
                new MasterExpectation("market_cloister_01", "marketRotunda"),
                new MasterExpectation("market_guildcourt_02", "marketGuildcourt"),
                new MasterExpectation("market_crossroads_03", "marketCrossroadsCompact"),
                new MasterExpectation("market_lane_04", "marketLane"),
                new MasterExpectation("market_bazaar_05", "marketBazaar"),
                new MasterExpectation("guard_watch_01", "guardGateTower"),
                new MasterExpectation("guard_bastion_02", "guardBastion"),
                new MasterExpectation("guard_blockhouse_03", "guardBlockhouseCompact"),
                new MasterExpectation("guard_gatehouse_04", "guardGatehouse"),
                new MasterExpectation("guard_citadel_05", "guardCitadel"),
                new MasterExpectation("exchange_hall_01", "exchangeCivicHall"),
                new MasterExpectation("exchange_countinghouse_02", "exchangeCountinghouse"),
                new MasterExpectation("exchange_branch_03", "exchangeBranchCompact"),
                new MasterExpectation("exchange_loggia_04", "exchangeLoggia"),
                new MasterExpectation("exchange_bourse_05", "exchangeBourse"));
    }

    private static Map<String, String> expectedRoleHelpers() {
        Map<String, String> helpers = new LinkedHashMap<>();
        helpers.put("COTTAGE", "addCottageYardDoodads");
        helpers.put("HOUSE", "addHouseYardDoodads");
        helpers.put("INN", "addInnYardDoodads");
        helpers.put("WAREHOUSE", "addWarehouseYardDoodads");
        helpers.put("GRANARY", "addGranaryYardDoodads");
        helpers.put("SMITHY", "addSmithyYardDoodads");
        helpers.put("MINE_ENTRANCE", "addMineYardDoodads");
        helpers.put("MARKET_SQUARE", "addMarketYardDoodads");
        helpers.put("GUARD_POST", "addGuardYardDoodads");
        helpers.put("EXCHANGE_HALL", "addExchangeYardDoodads");
        return helpers;
    }

    private static boolean invokes(String body, String method, String... arguments) {
        StringBuilder expression = new StringBuilder(Pattern.quote(method)).append("\\s*\\(\\s*");
        for (int index = 0; index < arguments.length; index++) {
            if (index > 0) {
                expression.append("\\s*,\\s*");
            }
            expression.append(Pattern.quote(arguments[index]));
        }
        expression.append("\\s*\\)");
        return Pattern.compile(expression.toString()).matcher(body).find();
    }

    private static boolean caseInvokes(
            String body, String projectType, String helper, boolean rear) {
        Pattern mapping = Pattern.compile(
                "case\\s+" + Pattern.quote(projectType) + "\\s*->"
                        + "(?:(?!\\bcase\\b)[\\s\\S])*?"
                        + Pattern.quote(helper) + "\\s*\\(\\s*stage\\s*,\\s*m\\s*,\\s*p\\s*,"
                        + "\\s*dressingId\\s*,\\s*" + rear + "\\s*\\)");
        return mapping.matcher(body).find();
    }

    private static String read(Path root, String file) throws Exception {
        Path source = root.resolve(MINECRAFT_SOURCE + file);
        require(Files.isRegularFile(source), "Missing Minecraft integration source: " + source);
        return Files.readString(source);
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

    private static String sourceRegion(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        require(startIndex >= 0, "Missing source region start: " + start);
        int endIndex = source.indexOf(end, startIndex);
        require(endIndex >= 0, "Missing source region end: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * A late dressing-stage bench may sit directly below a roof cell authored in the base stage.
     * Its default lower slab cap leaves half a block of air below that roof, which the production
     * roof admission correctly rejects as floating geometry. Both bench orientations must promote
     * only those covered endpoint caps to a full double slab while preserving the lighter cap on
     * freestanding benches.
     */
    private static void verifyBenchCapsAdaptBelowExistingCanopies(String source) {
        String exchangeBranch = methodBody(
                source, "private static void exchangeBranchCompact(");
        require(invokes(exchangeBranch, "addRearServiceCanopy", "b", "p", "4", "10", "10", "4"),
                "Compact exchange branch no longer owns the rear canopy involved in the "
                        + "covered-bench support contract");

        String exchangeYard = methodBody(source, "private static void addExchangeYardDoodads(");
        require(Pattern.compile(
                        "addDoodadBenchX\\s*\\(\\s*stage\\s*,\\s*p\\s*,"
                                + "\\s*m\\.width\\s*/\\s*2\\s*-\\s*1\\s*,"
                                + "\\s*m\\.depth\\s*\\+\\s*1\\s*,\\s*3\\s*,\\s*1\\s*\\)")
                        .matcher(exchangeYard)
                        .find(),
                "Rear exchange dressing no longer exercises a bench beneath the compact branch "
                        + "canopy edge");

        for (String helper : List.of("addDoodadBenchX", "addDoodadBenchZ")) {
            String body = methodBody(source, "private static void " + helper + "(");
            require(body.contains("stage.isOccupied(new BlockPos(x, 4, z))")
                            && body.contains("doubleRoofSlab(p)")
                            && body.contains("p.roofSlab.defaultBlockState()"),
                    helper + " no longer promotes a lower-slab endpoint cap when a prior-stage "
                            + "canopy occupies the cell above it");
        }
    }

    private record MasterExpectation(String templateId, String masterMethod) {
    }
}
