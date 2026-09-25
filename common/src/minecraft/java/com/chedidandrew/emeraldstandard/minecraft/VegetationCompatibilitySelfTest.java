package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded, disposable-world compatibility/protection tests. Optional real Wilder Flowers runtime. */
final class VegetationCompatibilitySelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin=new BlockPos(936,level.getMaxY()-90,936);
        Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        var air=Blocks.AIR.defaultBlockState();
        var land=DevelopmentLandProtection.land(level.getServer());
        String dimension=level.dimension().identifier().toString();
        String prior=land.placement(dimension,origin.asLong());
        try {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=-1;y<=72;y++) {
                BlockPos pos=origin.offset(x,y,z);level.getChunk(pos);saved.put(pos,level.getBlockState(pos));
                level.setBlock(pos,(y<0?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18);
            }
            for(Block block:List.of(Blocks.SHORT_GRASS,Blocks.FERN,Blocks.DANDELION,Blocks.OAK_SAPLING)) {
                var state=block.defaultBlockState();level.setBlock(origin,state,18);
                check(VegetationCompatibility.open(state)&&VillageBridges.clear(state)
                        &&ConstructionSitePresentation.replaceablePlant(state),"shared ground cover "+block);
                check(VillageSitePreparation.clearable(level,origin),"site ground cover "+block);
            }
            for(Block block:List.of(Blocks.WHEAT,Blocks.FARMLAND,Blocks.CHEST,Blocks.POTTED_DANDELION,
                    Blocks.WATER,Blocks.OAK_PLANKS,Blocks.BEDROCK)) {
                var state=block.defaultBlockState();level.setBlock(origin,state,18);
                check(!VegetationCompatibility.open(state)&&!VillageSitePreparation.clearable(level,origin),"unsafe category "+block);
            }
            var leaf=Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
            level.setBlock(origin,leaf,18);
            check(!VillageSitePreparation.clearable(level,origin)
                    &&!VillageDevelopmentProtection.mayPlace(level,null,0,origin,leaf,air),"persistent leaves");
            var flower=Blocks.DANDELION.defaultBlockState();level.setBlock(origin,flower,18);
            DevelopmentLandProtection.placed(level,origin,flower);
            check(!VillageDevelopmentProtection.mayPlace(level,null,0,origin,flower,air),"pending player garden");
            DevelopmentLandProtection.tick(level.getServer());
            check(!VillageSitePreparation.clearable(level,origin),"saved player garden");
            land.observe(dimension,origin.asLong(),"");
            try(var claim=VillageDevelopmentProtection.register(c->!c.position().equals(origin))) {
                check(new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0)==null,
                        "compatibility bypassed claim");
            }
            level.setBlock(origin,Blocks.OAK_LOG.defaultBlockState(),18);
            check(!VillageSitePreparation.clearable(level,origin),"isolated log lacks canopy");
            for(int y=1;y<4;y++)level.setBlock(origin.above(y),Blocks.OAK_LOG.defaultBlockState(),18);
            level.setBlock(origin.above(4),Blocks.OAK_LEAVES.defaultBlockState(),18);
            check(VillageSitePreparation.clearable(level,origin),"bounded natural tree");
            land.observe(dimension,origin.asLong(),"minecraft:oak_sapling[stage=0]");
            check(!VillageSitePreparation.clearable(level,origin),"planted tree lost sapling provenance");
            land.observe(dimension,origin.asLong(),"");
            for(int y=4;y<70;y++)level.setBlock(origin.above(y),Blocks.OAK_LOG.defaultBlockState(),18);
            level.setBlock(origin.above(70),Blocks.OAK_LEAVES.defaultBlockState(),18);
            check(!VillageSitePreparation.clearable(level,origin),"oversized tree exceeded bounds");
            for(int y=0;y<=70;y++)level.setBlock(origin.above(y),air,18);
            for(int x=0;x<=2;x++)level.setBlock(origin.offset(x,2,0),Blocks.OAK_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X),18);
            for(int x:new int[]{0,2})for(int y=0;y<2;y++)
                level.setBlock(origin.offset(x,y,0),Blocks.OAK_LOG.defaultBlockState(),18);
            level.setBlock(origin.above(3),Blocks.OAK_LEAVES.defaultBlockState(),18);
            check(!VillageSitePreparation.clearable(level,origin),"old timber frame misclassified as tree");
            Object key="vegetation-reload",failure=new Object();
            SiteSurveyRejections.remember(level,key,origin.getX(),origin.getZ(),2,failure);
            check(SiteSurveyRejections.get(level,key,origin.getX(),origin.getZ(),2,100)==failure,"cache miss");
            VegetationCompatibility.resourcesReloaded(level.getServer());
            check(SiteSurveyRejections.get(level,key,origin.getX(),origin.getZ(),2,100)==null,"reload retained rejection");
            var columnKey=new SiteSurveyRejections.ColumnKey(origin.getX(),origin.getZ());
            SiteSurveyRejections.remember(level,columnKey,origin.getX(),origin.getZ(),34,failure);
            check(SiteSurveyRejections.get(level,columnKey,origin.getX(),origin.getZ(),34,1200)==failure,
                    "shared negative column cache");
            level.setBlock(origin.east(),Blocks.STONE.defaultBlockState(),18);
            check(SiteSurveyRejections.get(level,columnKey,origin.getX(),origin.getZ(),34,1200)==null,
                    "column cache ignored terrain change");
            var clover=BuiltInRegistries.BLOCK.getValue(Identifier.parse("wilderflowers:clovers"));
            if(System.getenv("TES_TEST_VEGETATION_JAR")!=null)check(clover!=Blocks.AIR,"real Wilder Flowers not loaded");
            if(clover!=null&&clover!=Blocks.AIR) {
                var state=clover.defaultBlockState();
                level.setBlock(origin,state,18);
                check(VegetationCompatibility.open(state)&&VillageBridges.clear(state)
                        &&VillageSitePreparation.clearable(level,origin),"actual Wilder Flowers clover");
                System.out.println("PASS actual Wilder Flowers clovers: loaded registry, tags and site/bridge classification");
            }
            System.out.println("PASS vegetation: shared plants, crops/containers/fluids/persistent leaves, player gardens, planted trees, bounded trees, claims and reload cache");
        } catch(Exception ex) { throw new IllegalStateException("Vegetation compatibility fixture",ex); }
        finally {
            try {land.observe(dimension,origin.asLong(),prior==null?"":prior);}
            catch(Exception ex){throw new IllegalStateException(ex);}
            saved.forEach((pos,state)->level.setBlock(pos,state,18));
        }
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
