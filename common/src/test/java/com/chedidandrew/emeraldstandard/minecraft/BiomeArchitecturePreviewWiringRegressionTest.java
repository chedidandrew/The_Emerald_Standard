package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Preview opt-in and production-isolation boundaries; geometry has a separate native gate. */
public final class BiomeArchitecturePreviewWiringRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String path = "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/";
        String preview = Files.readString(root.resolve(path + "BiomeArchitecturePreview.java"));
        String gallery = Files.readString(root.resolve(path + "VillageComparisonGallery.java"));
        String init = Files.readString(root.resolve("scripts/village-comparison-client.init.gradle"));
        String launcher = Files.readString(root.resolve("scripts/open-village-comparison.ps1"));
        String legacy=Files.readString(root.resolve(path+"PlainsLegacyArchitecturePreview.java"));
        String catalog=Files.readString(root.resolve(path+"BiomeArchitectureCatalogPreview.java"));
        String exteriors=Files.readString(root.resolve(path+"PreviewExteriorPrograms.java"));
        String seating=Files.readString(root.resolve(path+"PreviewSeatingAudit.java"));
        String roof=Files.readString(root.resolve(path+"PreviewRoofEnvelope.java"));
        String doorway=Files.readString(root.resolve(path+"PreviewDoorwayAudit.java"));
        String rooms=Files.readString(root.resolve(path+"PreviewRoomLayout.java"));
        String tiers=Files.readString(root.resolve(path+"PreviewArchitectureTiers.java"));
        String compact=Files.readString(root.resolve(path+"PreviewCompactBuildings.java"));
        String outdoors=Files.readString(root.resolve(path+"PreviewOutdoorPrograms.java"));
        String support=Files.readString(root.resolve(path+"PreviewSupportAudit.java"));
        String taiga=Files.readString(root.resolve(path+"PreviewTaigaCraft.java"));
        String facade=Files.readString(root.resolve(path+"PreviewFacadePrograms.java"));
        String glazing=Files.readString(root.resolve(path+"PreviewDoorwayGlazing.java"));
        require(rooms.contains("widenCorridors(source,cells,access)")&&rooms.contains("Math.abs(at.getZ()-z)<4")
                &&rooms.contains("guardUpperEdges(source,cells,access)")&&rooms.contains("unguarded upper-floor drop")
                &&facade.contains("PreviewWindowLighting.brightenRooms(source,cells)"),
                "Review circulation widens cramped enclosed passages, guards loft drops and restores comfortable night lighting");
        require(rooms.contains("ceilingRooms(source,cells,floors)")&&rooms.contains("validateHeights(p)")
                &&rooms.contains("clearThird(cells,new BlockPos(x+dx,y+1,z))")
                &&rooms.contains("layout.dividerTops()")&&rooms.contains("room divider does not meet its ceiling")
                &&rooms.contains("closeLowAtticEdges(source,cells,access)")
                &&compact.contains("dividerTops")&&preview.contains("PreviewRoomLayout.validate(p)"),
                "Finished review rooms close partitions at their ceilings and enforce three-block headroom on final composition");
        require(facade.contains("jambs=PreviewDoorwayGlazing.frame(source.sample(),cells)")&&facade.contains("skin.addAll(jambs)")
                &&facade.contains("PreviewDoorwayGlazing.validate(p)")&&preview.contains("PreviewDoorwayGlazing.validate(p)")
                &&glazing.contains("Map.copyOf(cells)")&&glazing.contains("Unframed doorway glazing")
                &&glazing.contains("if(pane(before.get(at)))"),
                "All final review facades frame existing door-adjacent panes without filling air or overriding native pane arms");
        String composition=Files.readString(root.resolve(path+"PreviewWindowLighting.java"));
        require(facade.contains("PreviewWindowLighting.sealBlindWindows(source,cells)")
                &&facade.contains("PreviewWindowLighting.thinLanterns(source,cells)")
                &&composition.contains("reference.requireSpawnSafe()")&&composition.contains("light.spawnSafe()")
                &&rooms.contains("if(state.getBlock() instanceof LanternBlock) fixtures.add(at)")
                &&composition.contains("Blind review window"),
                "Final windows have meaningful views and room lighting considers authored lanterns while preserving night coverage");
        require(taiga.contains("first-1,last+1")&&taiga.contains("b.cells.putIfAbsent(at,state)")
                &&preview.contains("PreviewTaigaCraft.course(this")&&exteriors.contains("PreviewTaigaCraft.course(b")
                &&compact.contains("PreviewTaigaCraft.course(b"),"Taiga courses explicitly project roofs without overwriting occupied geometry");
        require(gallery.contains("PREVIEW_HALF_PITCH = 56")&&gallery.contains("PREVIEW_ROW_PITCH = 72")
                &&gallery.contains("(district%3)*districtWidth")&&gallery.contains("(district/3)*districtDepth")
                &&gallery.contains("validatePreviewParcel(plan,size.getX(),size.getZ())"),
                "Compact review districts reserve complete yards and real reference template bounds");
        require(gallery.contains("!state.reviewOpened")&&gallery.contains("state.reviewOpened=openCatalogReview(server)")
                &&gallery.contains("server.getPlayerList().getPlayers().isEmpty()) return false")
                &&gallery.contains("\"time set noon\""),
                "Fresh and reopened review worlds open the exterior once after a player is present");
        require(outdoors.contains("PreviewSupportAudit.validate(p,all)")
                &&support.contains(".getShape(EmptyBlockGetter.INSTANCE")&&support.contains("Floating review geometry")
                &&support.contains("at.getY()<=0"),"All review sites reject disconnected native-shape geometry before placement");
        require(outdoors.contains("GROUND_Y=-1")&&outdoors.contains("lowerToGrade(new Site")
                &&outdoors.contains("routes.add(at.below())")&&outdoors.contains("Raised yard path")
                &&support.contains("at.getY()<=PreviewOutdoorPrograms.GROUND_Y"),
                "Complete outdoor layouts and route coordinates lower to native grade without grounding props as soil");
        require(preview.contains("PreviewOutdoorPrograms.blocks(this,origin)")
                &&outdoors.contains("Outdoor addition inside protected building bounds")
                &&outdoors.contains("Uncontained water")&&outdoors.contains("Disconnected outdoor circulation")
                &&gallery.contains("actual.canSurvive(level,at)"),"Reserved review landscapes preserve building envelopes and use native survival");
        require(preview.contains("PreviewFacadePrograms.apply")&&catalog.contains("PreviewFacadePrograms.apply")
                &&legacy.contains("PreviewFacadePrograms.apply")&&compact.contains("PreviewFacadePrograms.apply"),
                "360-degree facade programs cover all preview routes, including compact and legacy designs");
        require(catalog.contains("PreviewCompactBuildings.samples(style)")&&compact.contains("PreviewRoomLayout.register")
                &&rooms.contains("LadderBlock")&&rooms.contains("covered(cells")&&tiers.contains("TINY(1), SMALL(1), MEDIUM(2)")
                &&tiers.contains("new Random(projectSeed)"),"Compact staged progression and reachable enclosed upper storeys");
        require(catalog.contains("PreviewDoorwayAudit.correct")&&legacy.contains("PreviewDoorwayAudit.correct")
                &&preview.contains("PreviewDoorwayAudit.validate(p)")&&doorway.contains("disconnected doorway")
                &&doorway.contains("orphan door upper")&&doorway.contains("DoorBlock.OPEN,true"),
                "All review doors have collision, complete-half and attached-room admission");
        require(preview.contains("Boolean.getBoolean(CATALOG_PROPERTY)")&&catalog.contains("StructureGalleryPlan.goldMasters()")
                &&gallery.contains("BiomeArchitecturePreview.reviewSamples()")&&init.contains("tesArchitectureFullCatalog")
                &&launcher.contains("[switch]$FullCatalog"),"Full catalog remains separately opted in");
        require(catalog.contains("Blocks.COBBLESTONE")&&catalog.contains("At most two low")
                &&!catalog.contains("Blocks.MOSSY_COBBLESTONE_STAIRS"),"Taiga cobblestone with sparse weathering only");
        require(legacy.contains("AuthoredVillageStructures.plan")&&legacy.contains("galleryBankBlueprint")
                &&legacy.contains("AuthoredVillageStructures.Phase.ROOF")&&legacy.contains("target.withPropertiesOf(state)"),
                "Plains must copy current native designs with a shape-preserving roof palette override");
        require(legacy.contains("PreviewSeatingAudit.corrected(original.cells(),original.seats())")
                &&seating.contains("towardTable.getOpposite()")&&preview.contains("facing.getOpposite()"),
                "Chair backs point away from the table without changing stair material or shape");
        require(catalog.contains("PreviewExteriorPrograms.roof")&&catalog.contains("PreviewExteriorPrograms.appendages")
                &&exteriors.contains("case COURT")&&exteriors.contains("case OFFSET")&&exteriors.contains("case TWIN_BAYS"),
                "Catalog composes explicit roof and attached-wing programs, not one resized shell");
        require(catalog.contains("PreviewRoofEnvelope.seal(b)")&&exteriors.contains("PreviewRoofEnvelope.carry")
                &&roof.contains("Block.isShapeFullBlock")&&roof.contains("for(int y=wallTop+1;y<roofBase;y++)"),
                "Every raised roof range has its own full-height weather curb above the occupied storey");
        require(preview.contains("REVISION = 10")&&preview.contains("b.details()")&&preview.contains("b.regionalCraft()")
                &&preview.contains("walkable(cells,at)")&&preview.contains("CarpetBlock"),
                "Revision-three regional craft and rug admission");
        require(preview.contains("validateFurnitureSupport(p)")&&preview.contains("Reversed counter endpoints"),
                "Floating fixture and counter admission");
        require(preview.contains("TES_Biome_Architecture_Preview")
                && gallery.contains("BiomeArchitecturePreview.WORLD.equals(name.toString())")
                && gallery.contains("WORLD_DIRECTORY.equals(name.toString())"), "Exact save isolation");
        require(gallery.contains("if (Boolean.getBoolean(BiomeArchitecturePreview.PROPERTY))")
                && gallery.contains("resolvePreviewPairs(level, surfaceY)")
                && gallery.contains("pair.preview.blocks(modOrigin)"), "Preview-only placement routing");
        require(gallery.contains("pair.preview.blocks(BlockPos.ZERO)")
                && gallery.contains("block.state().toString()"), "Save signature includes actual prototype blocks");
        require(gallery.contains("Preview interior camera intersects a solid block")
                && gallery.contains("noCollision(player,"), "Eye-level interior camera collision gate");
        for (String name : new String[]{"AuthoredVillageStructures.java", "ConstructionBuilder.java",
                "VillageBankManager.java", "VillageDevelopmentRuntime.java"}) {
            require(!Files.readString(root.resolve(path + name)).contains("BiomeArchitecturePreview"),
                    "Prototype leaked into production: " + name);
            require(!Files.readString(root.resolve(path + name)).contains("PreviewExteriorPrograms")
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewSeatingAudit")
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewRoofEnvelope")
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewDoorwayAudit")
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewArchitectureTiers")
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewCompactBuildings")
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewRoomLayout"),
                    "Review helpers leaked into production: " + name);
            require(!Files.readString(root.resolve(path+name)).contains("PreviewFacadePrograms"),"Facades leaked into production");
            require(!Files.readString(root.resolve(path+name)).contains("PreviewOutdoorPrograms"),"Outdoor site programs leaked into production");
            require(!Files.readString(root.resolve(path+name)).contains("PreviewSupportAudit"),"Review support audit leaked into production");
            require(!Files.readString(root.resolve(path+name)).contains("PreviewDoorwayGlazing"),"Review glazing audit leaked into production");
            require(!Files.readString(root.resolve(path+name)).contains("PreviewWindowLighting"),"Review window/light composition leaked into production");
        }
        require(init.contains("tesArchitecturePreview") && init.contains("TES_Biome_Architecture_Preview")
                && init.contains("'1,2,3,4,5,6,7,8,9,10,11,12,13'"), "Preview launcher coverage");
        require(launcher.contains("[switch]$ArchitecturePreview") && launcher.contains("level.dat")
                && !launcher.contains("Remove-Item") && !launcher.contains("Copy-Item"), "No user-save replacement");
        System.out.println("PASS biome architecture preview isolation and wiring regression");
    }
    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
