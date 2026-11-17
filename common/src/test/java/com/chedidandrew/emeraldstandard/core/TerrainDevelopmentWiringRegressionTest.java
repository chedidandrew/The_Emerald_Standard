package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;

/** Complements live fixtures with guards on admission, cache identity and handbook wiring. */
public final class TerrainDevelopmentWiringRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]);
        Path nativeRoot=root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft");
        String manager=Files.readString(nativeRoot.resolve("VillageProsperityManager.java")).replace("\r","");
        String bridges=Files.readString(nativeRoot.resolve("VillageBridges.java"));
        String expansion=Files.readString(nativeRoot.resolve("BridgeFirstExpansion.java"));
        String language=Files.readString(root.resolve("common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        require(manager.contains("project.designOutdoorLevel,\n                        Map.copyOf(project.outdoorTerrainOffsets)"),"outer template cache includes fit and offsets");
        require(manager.contains("ArchitectureSiteFit.searchLevel(village,project)")&&manager.contains("plotSearch.metadataFootprintSkip"),"new-fit choice and cheap lot skips wired");
        int gate=manager.indexOf("if(!project.bridgeFirstDependencies.isEmpty()&&!BridgeFirstExpansion.open");
        require(gate>=0&&gate<manager.indexOf("if (DevelopmentLandProtection.excludes",gate),"bridge gate precedes destination preparation/building");
        require(bridges.contains("p.requiresDestination()&&!j.funded()")&&bridges.contains("project.originPos!=0L")
                &&bridges.contains("project.bridgeFirstDependencies.split"),"durable destination checked before bridge spending");
        require(expansion.contains("destinationBound")&&expansion.contains("saveAndJoin")&&expansion.contains("walkable(level"),"checkpoint plus verified native deck gate");
        require(manager.contains("Bridge approach overlaps the proposed destination building"),"future building cannot overlap bridge approach");
        require(manager.contains("Destination was reserved by another project during route survey")
                &&manager.contains("recoverOverlappingVillageProject"),"delayed approval recheck and old-save collision recovery wired");
        require(!expansion.contains("steps<=24"),"short creeks are not exempt from crossing inspection");
        String stairs=Files.readString(nativeRoot.resolve("EntranceStaircase.java"));
        require(manager.contains("EntranceStaircase.survey")&&language.contains("straight three-block-wide staircase")
                &&stairs.contains("side = -1; side <= 1")&&stairs.contains("outward.getOpposite()")
                &&stairs.contains("survey.freeze(clearance")&&stairs.contains("VillageDevelopmentProtection.mayPlace"),
                "three-wide actual-exit stairs must freeze protected terrain work");
        String terrain=Files.readString(nativeRoot.resolve("VillageTerrainFinishing.java"));
        String grounding=Files.readString(nativeRoot.resolve("VillageTerrainGrounding.java"));
        String banks=Files.readString(nativeRoot.resolve("VillageBankManager.java"));
        require(manager.contains("VillageTerrainGrounding.freeze")&&banks.contains("VillageTerrainGrounding.freeze"),"new projects and Banks both freeze earthworks");
        require(manager.contains("groundingBounds")&&manager.contains("earthworksBounds")&&banks.contains("VillageTerrainGrounding.BLEND_RADIUS"),"earthworks included in durable reservations and collision checks");
        require(grounding.contains("naturalSurroundings(bearing)")&&grounding.contains("survey.loaded(neighbor)")
                &&grounding.contains("VillageDevelopmentProtection.mayPlace")&&grounding.contains("authored.containsKey(BlockPos.of(key))"),"sound protected backing, loaded edges and authored underground air preserved");
        require(language.contains("Fill under yards.")&&language.contains("Wide entry stairs.")
                &&language.contains("continuous soil backing"),"both handbook formats explain new grounded yards");
        require(terrain.contains("naturalSoilEquivalent")&&terrain.contains("usableRoad"),"soil and existing usable road reconciliation wired");
        require(language.contains("Lot clashes recover.")&&language.contains("does not remove old blocks")
                &&language.contains("Waiting longer never permits building through an animal"),"both handbook forms explain safe recovery");
        require(manager.contains("if(!BridgeFirstExpansion.remember")&&manager.contains("no lot committed"),"full preview slots cannot bypass destination dependencies");
        require(language.contains("Roughly one in ten")&&language.contains("Rare yards: ~10%%.")&&language.contains("96 water default.")
                &&language.contains("terrain offsets")&&language.contains("Wide entry stairs.")
                &&language.contains("Bridge before house."),"both handbook forms teach rarity, terrain, debug and bridge-first limits");
        System.out.println("PASS terrain/rare-fit cache identity, cheap search, destination-before-spending/building, crossing verification and both handbook forms");
    }
    private static void require(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
}
