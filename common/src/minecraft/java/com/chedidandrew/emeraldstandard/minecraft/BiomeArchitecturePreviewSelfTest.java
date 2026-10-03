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
        if(changed==0) throw new AssertionError("No oak roof substitution in "+plan.sample().id());
        System.out.println("PASS exact legacy Plains copy: "+changed+" oak roof cells; all other cells unchanged");
    }
}
