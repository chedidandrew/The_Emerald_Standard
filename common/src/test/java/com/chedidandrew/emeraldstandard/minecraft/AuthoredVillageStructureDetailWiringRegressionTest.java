package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Protects the semantic detail motifs that make the revision-2 masters visually identifiable.
 *
 * <p>The executable catalog validators prove support, enclosure, access and broad silhouette
 * distinctiveness. Those gates intentionally cannot tell whether a valid building has lost its
 * porch depth, timber rhythm, working clutter or signature secondary space. This source-boundary
 * check keeps those high-level motif calls wired without freezing their block coordinates or
 * palette choices.</p>
 */
public final class AuthoredVillageStructureDetailWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";

    private AuthoredVillageStructureDetailWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);

        Map<String, List<String>> motifs = expectedMotifs();
        String catalogSelection = methodBody(source, "static Blueprint plan(");
        for (Map.Entry<String, List<String>> master : motifs.entrySet()) {
            require(catalogSelection.contains(
                            "-> " + master.getKey() + "(base, metadata, materials)"),
                    "Detailed master is no longer selected by the active catalog: "
                            + master.getKey());
            String body = methodBody(source, "private static void " + master.getKey() + "(");
            for (String helper : master.getValue()) {
                require(body.contains(helper + "("),
                        master.getKey() + " lost its " + helper + " detail motif");
                require(helperDefined(source, helper),
                        "Detail motif is called but has no authored helper: " + helper);
                require(helperPlacesDetail(source, helper),
                        "Detail helper became an empty/no-op motif: " + helper);
            }
        }

        for (Map.Entry<String, List<String>> owner : expectedNestedMotifs().entrySet()) {
            String body = methodBody(source, "private static void " + owner.getKey() + "(");
            for (String helper : owner.getValue()) {
                require(body.contains(helper + "("),
                        owner.getKey() + " lost its nested " + helper + " detail motif");
                require(helperDefined(source, helper),
                        "Nested detail motif is called but has no authored helper: " + helper);
                require(helperPlacesDetail(source, helper),
                        "Nested detail helper became an empty/no-op motif: " + helper);
            }
        }

        for (Map.Entry<String, String> master : expectedDomesticPrograms().entrySet()) {
            String body = methodBody(source, "private static void " + master.getKey() + "(");
            String expectedCall = master.getValue() + "(b, m, p,";
            require(body.contains(expectedCall),
                    master.getKey() + " lost its role-specific interior program");
            require(helperDefined(source, master.getValue()),
                    "Domestic interior program is called but not defined: " + master.getValue());
            require(helperPlacesDetail(source, master.getValue()),
                    "Domestic interior program became an empty/no-op helper: "
                            + master.getValue());
        }
        for (Map.Entry<String, String> master : expectedCivicPrograms().entrySet()) {
            String body = methodBody(source, "private static void " + master.getKey() + "(");
            require(body.contains(master.getValue() + "(b, m, p,"),
                    master.getKey() + " lost its role-specific civic interior program");
            require(helperDefined(source, master.getValue()),
                    "Civic interior program is called but not defined: " + master.getValue());
            require(helperPlacesDetail(source, master.getValue()),
                    "Civic interior program became an empty/no-op helper: "
                            + master.getValue());
        }
        require(!methodBody(source, "private static void guardGatehouse(")
                        .contains("addGuardInteriorProgram("),
                "Already-passing guard_gatehouse_04 was changed by the civic detail pass");
        require(!methodBody(source, "private static void exchangeLoggia(")
                        .contains("addExchangeInteriorProgram("),
                "Already-passing exchange_loggia_04 was changed by the civic detail pass");

        for (String helper : List.of(
                "addCottageRafterX", "addCottagePantry", "addCottagePottingRun",
                "addCottageSettledBench", "addHouseCeilingTieX", "addHouseWritingNook",
                "addHouseCabinetPair", "addHouseTowerStudy", "addInnCeilingTieX",
                "addInnServiceCounter", "addInnDiningSet", "addInnTackBay",
                "addInnGuestChest", "addInnLanternRoom")) {
            require(helperDefined(source, helper),
                    "Role-specific interior motif is not defined: " + helper);
            require(helperPlacesDetail(source, helper),
                    "Role-specific interior motif became an empty/no-op helper: " + helper);
        }
        for (String helper : List.of(
                "addMarketVendorDisplay", "addMarketPennantPost", "addMarketGuildDesk",
                "addGuardMapTable", "addGuardArmoryRack", "addGuardBarracksBench",
                "addGuardPatrolStation", "addExchangeLedgerCarrel",
                "addExchangeCountingTable", "addExchangeOfficeScreen",
                "addExchangeTradingPod")) {
            require(helperDefined(source, helper),
                    "Role-specific civic motif is not defined: " + helper);
            require(helperPlacesDetail(source, helper),
                    "Role-specific civic motif became an empty/no-op helper: " + helper);
        }
        String marketProgram = methodBody(source, "private static void addMarketInteriorProgram(");
        require(!Pattern.compile(
                        "addMarketVendorDisplay\\s*\\([^;]*Blocks\\.IRON_BARS",
                        Pattern.DOTALL)
                        .matcher(marketProgram)
                        .find(),
                "Market vendor display reintroduced an orphan-prone iron-bar wares block");
        require(countOccurrences(marketProgram, "Blocks.RAW_IRON_BLOCK") >= 3,
                "Metal vendors lost their naturally standalone raw-iron stock displays");
        String pennantPost = methodBody(source, "private static void addMarketPennantPost(");
        require(pennantPost.contains(
                        "b.putIfFree(Phase.FRAME, x, 4, z, p.timber.defaultBlockState())")
                        && !pennantPost.contains("p.roofSlab.defaultBlockState()"),
                "Market pennant masthead no longer provides full support beneath arcade roofs");

        require(catalogSelection.contains("addRegionalIdentity(base, metadata, materials)"),
                "Revision-2 masters no longer receive their shared regional identity pass");
        String materials = methodBody(source, "private static Materials materials(");
        for (String hierarchy : List.of(
                "case SAVANNA -> Blocks.MUD_BRICKS",
                "case TAIGA -> Blocks.MOSSY_COBBLESTONE",
                "case TAIGA -> Blocks.DEEPSLATE_TILE_STAIRS",
                "case SNOWY -> Blocks.POLISHED_DIORITE",
                "case SNOWY -> Blocks.STRIPPED_DARK_OAK_LOG")) {
            require(materials.contains(hierarchy),
                    "Biome material hierarchy lost: " + hierarchy);
        }
        String regionalIdentity = methodBody(
                source, "private static void addRegionalIdentity(");
        require(regionalIdentity.contains("Blocks.CUT_RED_SANDSTONE")
                        && regionalIdentity.contains("Blocks.MOSSY_STONE_BRICKS")
                        && regionalIdentity.contains("replaceRegionalWallCell"),
                "Regional facade identity collapsed back to isolated texture punctuation");
        require(catalogSelection.contains(
                        "addCompactCraftLayer(base, metadata, materials, templateId, scale)"),
                "Below-target compact masters no longer receive the shared craft pass");
        require(catalogSelection.contains(
                        "appendPresentationStageTwo(\n                    stageTwo,\n                    base,"),
                "Final presentation layer no longer receives the authored base for safe zoning");
        String compactCraft = methodBody(source, "private static void addCompactCraftLayer(");
        for (String motif : List.of(
                "addCompactRoofEndCraft", "addCompactInteriorStory",
                "addCompactOpenWorkLedge")) {
            require(compactCraft.contains(motif + "("),
                    "Compact craft pass lost its " + motif + " motif");
            require(helperDefined(source, motif),
                    "Compact craft motif is called but not defined: " + motif);
            require(helperPlacesDetail(source, motif),
                    "Compact craft motif became an empty/no-op helper: " + motif);
        }
        String finalPresentation = methodBody(
                source, "private static void appendPresentationStageTwo(");
        require(finalPresentation.contains("addScaleAwareProsperousInterior("),
                "Final presentation layer lost scale-aware interior zoning");
        String prosperousInterior = methodBody(
                source, "private static void addScaleAwareProsperousInterior(");
        for (String motif : List.of(
                "canPlaceProsperousIsland", "addProsperousRoleIsland",
                "addScaleAwareInteriorBeams")) {
            require(prosperousInterior.contains(motif + "("),
                    "Prosperous interior pass lost its " + motif + " motif");
        }
        require(prosperousInterior.contains("\"guard_gatehouse_04\".equals(templateId)")
                        && prosperousInterior.contains(
                                "\"exchange_loggia_04\".equals(templateId)"),
                "Already-passing geometry masters are no longer frozen from shared zoning");
        System.out.println("PASS authored village detail-motif wiring regression");
    }

    private static Map<String, List<String>> expectedMotifs() {
        Map<String, List<String>> motifs = new LinkedHashMap<>();
        motifs.put("cottageHearthLandmark", List.of(
                "addLayeredEntryPorch", "addDeepWindowFrame", "addCoveredWoodBay"));
        motifs.put("cottageGlasshouse", List.of(
                "addGlasshouseRibs", "addGableTrussX", "addGardenPergola"));
        motifs.put("houseCrossGabled", List.of(
                "addGableTrussX", "addTwinRidgeGableTrussZ", "addBayWindow"));
        motifs.put("houseMansard", List.of(
                "addFrontDormer", "addFrontEaveBrackets", "addMerchantMezzanine"));
        motifs.put("innBalcony", List.of(
                "addInnGallery", "addFrontEaveBrackets", "addInnUpperFloor"));
        motifs.put("warehouseSawtooth", List.of(
                "addMonitorRoof", "addLoadingDock", "addWarehouseFrameRhythm",
                "addWarehouseRacks"));
        motifs.put("granaryRaisedBarn", List.of(
                "addRectTimberBand", "addGranaryUndercroftBraces", "addGrainChute"));
        motifs.put("smithyOpenForge", List.of(
                "addForgeTimberArches", "addForgeWorkDetails", "addWoodRack"));
        motifs.put("mineHeadframeLandmark", List.of(
                "addMineCrossBracing", "addMineToolShed", "addMineAditArch"));
        motifs.put("marketRotunda", List.of(
                "addMarketArcade", "addMarketArcadeTrim", "addDistinctMarketBay",
                "addBellRotunda", "addBellRotundaBracing"));
        motifs.put("guardGateTower", List.of(
                "addGuardTowerDetail", "addFlaredWatchRoof"));
        motifs.put("exchangeCivicHall", List.of(
                "addCivicLowerFacadeDetail", "addCivicPortico", "addCivicPorticoDetail",
                "addCivicCupola", "addCivicCupolaDetail", "addCivicTellerRail",
                "addCivicInteriorDetail"));
        return motifs;
    }

    private static Map<String, List<String>> expectedNestedMotifs() {
        Map<String, List<String>> motifs = new LinkedHashMap<>();
        motifs.put("addDistinctMarketBay", List.of("addMarketBayIdentity"));
        return motifs;
    }

    private static Map<String, String> expectedDomesticPrograms() {
        Map<String, String> programs = new LinkedHashMap<>();
        for (String master : List.of(
                "cottageHearthLandmark", "cottageGlasshouse", "cottageBayCompact",
                "cottageCourtyard", "cottageLonghouse", "cottageOrchardstead")) {
            programs.put(master, "addCottageInteriorProgram");
        }
        for (String master : List.of(
                "houseCrossGabled", "houseMansard", "houseHallCompact", "houseArcade",
                "houseSplitwing", "houseTowercourt")) {
            programs.put(master, "addHouseInteriorProgram");
        }
        for (String master : List.of(
                "innBalcony", "innWayfarerCompact", "innCoachhouse", "innTavern",
                "innCourtyard")) {
            programs.put(master, "addInnInteriorProgram");
        }
        return programs;
    }

    private static Map<String, String> expectedCivicPrograms() {
        Map<String, String> programs = new LinkedHashMap<>();
        for (String master : List.of(
                "marketRotunda", "marketGuildcourt", "marketCrossroadsCompact",
                "marketLane", "marketBazaar")) {
            programs.put(master, "addMarketInteriorProgram");
        }
        for (String master : List.of(
                "guardGateTower", "guardBastion", "guardBlockhouseCompact",
                "guardCitadel")) {
            programs.put(master, "addGuardInteriorProgram");
        }
        for (String master : List.of(
                "exchangeCivicHall", "exchangeCountinghouse", "exchangeBranchCompact",
                "exchangeBourse")) {
            programs.put(master, "addExchangeInteriorProgram");
        }
        return programs;
    }

    private static boolean helperDefined(String source, String helper) {
        return Pattern.compile("private\\s+static\\s+void\\s+" + Pattern.quote(helper)
                        + "\\s*\\(")
                .matcher(source)
                .find();
    }

    private static boolean helperPlacesDetail(String source, String helper) {
        String body = methodBody(source, "private static void " + helper + "(");
        return Pattern.compile("(?m)^\\s*(?:b\\s*\\.\\s*(?:put|putIfFree|force)\\s*\\(|"
                        + "(?:post|beam|fixture|add[A-Z][A-Za-z0-9_]*)\\s*\\()")
                .matcher(body)
                .find();
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

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
