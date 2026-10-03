package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Native blueprint admission, deliberately independent of normal project selection. */
public final class BiomeArchitecturePreviewSelfTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var desk = AuthoredVillageStructuresSelfTest.class.getDeclaredMethod("registerExactDeskFixture");
        desk.setAccessible(true); desk.invoke(null);
        String galleryBefore=System.getProperty(StructureGallery.ENABLE_PROPERTY);
        System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
        try {
        if (BiomeArchitecturePreview.samples().size()!=13) throw new AssertionError("Preview scope changed");
        for(var sample:BiomeArchitecturePreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(sample);
            if(!plan.equals(BiomeArchitecturePreview.plan(sample))) throw new AssertionError("Nondeterministic sample");
            long beds=plan.cells().values().stream().filter(s->s.getBlock() instanceof BedBlock
                    &&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
            long expectedBeds=sample.style()==VillageArchitecture.BiomeDialect.PLAINS
                    ? PlainsLegacyArchitecturePreview.source(sample.role()).cells().values().stream()
                            .filter(s->s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD).count()
                    : sample.role().equals("HOUSE")?2:sample.role().equals("INN")?4:0;
            if(beds!=expectedBeds)
                throw new AssertionError("Unexpected beds in "+sample.id());
            if(sample.style()==VillageArchitecture.BiomeDialect.PLAINS) verifyLegacyCopy(plan);
            else {
                verifyFurnitureRejections(plan);
                if(plan.cells().values().stream().noneMatch(s->s.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock))
                    throw new AssertionError("Missing deliberate rug in "+sample.id());
                if(plan.cells().values().stream().noneMatch(s->s.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock))
                    throw new AssertionError("Missing planting in "+sample.id());
            }
            plan.reverseView();
            if(sample.style()==VillageArchitecture.BiomeDialect.DESERT && plan.cells().values().stream()
                    .anyMatch(s->s.is(Blocks.ACACIA_STAIRS)||s.is(Blocks.ACACIA_PLANKS)||s.is(Blocks.ACACIA_LOG)))
                throw new AssertionError("Orange timber recolor leaked into desert architecture");
            System.out.println("PASS preview "+sample.id()+": "+plan.cells().size()
                    +" cells; "+plan.access().size()+" reachable declared approaches; "+beds+" beds; lighting admitted");
        }
        verifyFullCatalog();
        if(VillageArchitecture.activeBlueprints().size()!=52
                ||VillageArchitecture.activeBlueprints().stream().anyMatch(b->b.templateRevision()!=11)
                ||VillageBankManager.galleryBankStructureVersion()!=12)
            throw new AssertionError("Preview altered the production catalog");
        System.out.println("PASS production catalog unchanged; 52 revision-11 designs and version-12 Bank");
        } finally {
            if(galleryBefore==null) System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            else System.setProperty(StructureGallery.ENABLE_PROPERTY,galleryBefore);
        }
    }
    private static void verifyFullCatalog() {
        var samples=BiomeArchitectureCatalogPreview.samples();
        if(samples.size()!=265||samples.stream().map(BiomeArchitecturePreview.Sample::id).distinct().count()!=265)
            throw new AssertionError("Full review requires 52 designs and Bank in each of five styles");
        var failures=new java.util.ArrayList<String>();
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            var region=samples.stream().filter(s->s.style()==style).toList();
            if(region.size()!=53) throw new AssertionError("Incomplete style: "+style);
            var unique=new java.util.HashSet<java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>>();
            for(var sample:region) {
                try {
                var plan=BiomeArchitecturePreview.plan(sample);
                if(!plan.equals(BiomeArchitecturePreview.plan(sample))) throw new AssertionError("Unstable catalog plan");
                if(!unique.add(plan.cells())) throw new AssertionError("Duplicate design geometry: "+sample.id());
                if(sample.width()>50||sample.depth()>74) throw new AssertionError("Catalog exceeds review plot");
                if(style==VillageArchitecture.BiomeDialect.PLAINS) {
                    var source=sample.role().equals("BANK")?PlainsLegacyArchitecturePreview.source("BANK")
                            :PlainsLegacyArchitecturePreview.source(sample.role(),BiomeArchitectureCatalogPreview.masterId(sample));
                    verifyLegacyCopy(plan,source);
                } else {
                    BiomeArchitecturePreview.validate(plan);
                    long beds=plan.cells().values().stream().filter(s->s.getBlock() instanceof BedBlock
                            &&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
                    long expected=sample.role().equals("COTTAGE")?2:sample.role().equals("HOUSE")?4:sample.role().equals("INN")?8:0;
                    if(beds!=expected) throw new AssertionError("Catalog bed capacity: "+sample.id()+" "+beds+" expected "+expected);
                }
                if(style==VillageArchitecture.BiomeDialect.TAIGA) {
                    long moss=plan.cells().values().stream().filter(s->s.is(Blocks.MOSSY_COBBLESTONE)).count();
                    long plain=plan.cells().values().stream().filter(s->s.is(Blocks.COBBLESTONE)).count();
                    if(moss>3||plain<=moss) throw new AssertionError("Excess Taiga moss: "+sample.id());
                }
                if(style==VillageArchitecture.BiomeDialect.DESERT&&plan.cells().values().stream()
                        .anyMatch(s->s.is(Blocks.ACACIA_STAIRS)||s.is(Blocks.ACACIA_PLANKS)||s.is(Blocks.ACACIA_LOG)))
                    throw new AssertionError("Desert timber recolor: "+sample.id());
                for(var cell:plan.cells().entrySet()) if(cell.getValue().is(Blocks.SNOW)) {
                    var below=plan.cells().get(cell.getKey().below());
                    if(below==null||!below.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                            cell.getKey().below(),net.minecraft.core.Direction.UP))
                        throw new AssertionError("Snow decoration lost its final roof bearing: "+sample.id()+" "+cell.getKey());
                }
                System.out.println("PASS catalog "+sample.id()+": "+plan.cells().size()+" cells; "+plan.access().size()+" approaches");
                } catch(RuntimeException|AssertionError error) {
                    failures.add(sample.id()+": "+error.getMessage());
                    System.err.println("CATALOG FAILURE "+failures.getLast());
                }
            }
        }
        if(!failures.isEmpty()) throw new AssertionError("Catalog admission failures ("+failures.size()+"): "+failures);
    }
    private static void verifyFurnitureRejections(BiomeArchitecturePreview.Plan plan) {
        var at=new net.minecraft.core.BlockPos(plan.entrance().getX(),1,plan.entrance().getZ()+1);
        for(var state:java.util.List.of(Blocks.SANDSTONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP),Blocks.POTTED_CACTUS.defaultBlockState(),
                        Blocks.OAK_PRESSURE_PLATE.defaultBlockState())) {
            var cells=new java.util.HashMap<>(plan.cells());
            if(!state.is(Blocks.SANDSTONE_SLAB)) cells.remove(at.below());
            cells.put(at,state);
            var broken=new BiomeArchitecturePreview.Plan(plan.sample(),java.util.Map.copyOf(cells),
                    plan.access(),plan.entrance(),plan.height());
            try {
                BiomeArchitecturePreview.validateFurnitureSupport(broken);
                throw new AssertionError("Floating fixture admitted: "+state);
            } catch(IllegalStateException expected) {
                if(!expected.getMessage().contains("unsupported interior fixture")) throw expected;
            }
        }
        var cells=new java.util.HashMap<>(plan.cells());
        cells.remove(at.below()); cells.put(at,Blocks.BARREL.defaultBlockState());
        cells.put(at.above(),Blocks.POTTED_CACTUS.defaultBlockState());
        try {
            BiomeArchitecturePreview.validateFurnitureSupport(new BiomeArchitecturePreview.Plan(plan.sample(),
                    java.util.Map.copyOf(cells),plan.access(),plan.entrance(),plan.height()));
            throw new AssertionError("Floating cabinet with pot admitted");
        } catch(IllegalStateException expected) {
            if(!expected.getMessage().contains("unsupported interior fixture")) throw expected;
        }
    }
    private static void verifyLegacyCopy(BiomeArchitecturePreview.Plan plan) {
        var original=PlainsLegacyArchitecturePreview.source(plan.sample().role());
        verifyLegacyCopy(plan,original);
    }
    private static void verifyLegacyCopy(BiomeArchitecturePreview.Plan plan,PlainsLegacyArchitecturePreview.Source original) {
        int minX=original.cells().keySet().stream().mapToInt(net.minecraft.core.BlockPos::getX).min().orElseThrow();
        int minZ=original.cells().keySet().stream().mapToInt(net.minecraft.core.BlockPos::getZ).min().orElseThrow();
        if(original.cells().size()!=plan.cells().size()) throw new AssertionError("Plains geometry changed");
        int changed=0;
        for(var entry:original.cells().entrySet()) {
            var actual=plan.cells().get(entry.getKey().offset(-minX,0,-minZ));
            var expected=original.roofCells().contains(entry.getKey())
                    ? PlainsLegacyArchitecturePreview.oakRoof(entry.getValue(),entry.getKey().getY()) : entry.getValue();
            if(!expected.equals(actual)) throw new AssertionError("Non-roof legacy change: "+entry.getKey());
            if(!actual.equals(entry.getValue())) {
                changed++;
                for(var property:entry.getValue().getProperties())
                    if(!actual.hasProperty(property)||!actual.getValue(property).equals(entry.getValue().getValue(property)))
                        throw new AssertionError("Roof state property changed: "+property.getName());
            }
        }
        // Some open-market or already-oak legacy masters do not contain dark roof cells.
        if(changed==0&&!plan.sample().id().startsWith("catalog_")) throw new AssertionError("No oak roof substitution in "+plan.sample().id());
        System.out.println("PASS exact legacy Plains copy: "+changed+" oak roof cells; all other cells unchanged");
    }
}
