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
        if (BiomeArchitecturePreview.samples().size()!=13) throw new AssertionError("Preview scope changed");
        for(var sample:BiomeArchitecturePreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(sample);
            if(!plan.equals(BiomeArchitecturePreview.plan(sample))) throw new AssertionError("Nondeterministic sample");
            long beds=plan.cells().values().stream().filter(s->s.getBlock() instanceof BedBlock
                    &&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
            if(beds!=(sample.role().equals("HOUSE")?2:sample.role().equals("INN")?4:0))
                throw new AssertionError("Unexpected beds in "+sample.id());
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
    }
}
