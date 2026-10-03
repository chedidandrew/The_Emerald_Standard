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
        require(preview.contains("REVISION = 5")&&preview.contains("b.details()")&&preview.contains("b.regionalCraft()")
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
                    &&!Files.readString(root.resolve(path + name)).contains("PreviewSeatingAudit"),
                    "Review helpers leaked into production: " + name);
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
