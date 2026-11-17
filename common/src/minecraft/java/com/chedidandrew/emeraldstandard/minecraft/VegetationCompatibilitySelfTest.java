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
            var crown=new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0);
            check(crown!=null&&crown.cells().stream().anyMatch(c->c.position()==origin.above(4).asLong()),
                    "whole tree plan left its canopy to random decay");
            var clipped=new VillageSitePreparation.Survey(level).freeze(List.of(origin.above(4)),origin.getY(),null,0);
            check(clipped!=null&&clipped.cells().stream().anyMatch(c->c.position()==origin.asLong()),
                    "leaf-only collision left a clipped trunk");
            level.setBlock(origin.above(5),Blocks.SNOW.defaultBlockState(),18);
            crown=new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0);
            check(crown!=null&&crown.cells().stream().anyMatch(c->c.position()==origin.above(5).asLong()),
                    "affected canopy left floating snow");
            try(var claim=VillageDevelopmentProtection.register(c->!c.position().equals(origin.above(4)))) {
                check(new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0)==null,
                        "whole-canopy clearing bypassed leaf claim");
            }
            // Touching crowns do not authorize clearing the neighboring trunk or its nearer leaves.
            for(int y=0;y<4;y++)level.setBlock(origin.offset(4,y,0),Blocks.OAK_LOG.defaultBlockState(),18);
            for(int x=1;x<=4;x++)level.setBlock(origin.offset(x,4,0),Blocks.OAK_LEAVES.defaultBlockState(),18);
            crown=new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0);
            check(crown!=null&&crown.cells().stream().noneMatch(c->BlockPos.of(c.position()).getX()>=origin.getX()+2),
                    "canopy flood consumed untouched neighboring tree");
            for(int x=1;x<=4;x++)for(int y=0;y<=5;y++)level.setBlock(origin.offset(x,y,0),air,18);
            level.setBlock(origin.above(5),air,18);
            crown=new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0);
            check(crown!=null&&com.chedidandrew.emeraldstandard.core.SitePreparationPlan.decode(crown.encode()).cells().equals(crown.cells()),
                    "affected crown lost frozen identity on restart");
            var job=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageProject();job.projectId=1;
            job.sitePreparationPlan=crown;job.constructionStarted=true;
            var district=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageRecord();district.villageId=UUID.randomUUID();
            var replay=VillageProsperityManager.class.getDeclaredMethod("prepareNewSite",ServerLevel.class,
                    com.chedidandrew.emeraldstandard.core.EconomyService.class,
                    com.chedidandrew.emeraldstandard.core.EconomyState.VillageRecord.class,
                    com.chedidandrew.emeraldstandard.core.EconomyState.VillageProject.class,int.class);
            replay.setAccessible(true);
            var temp=java.nio.file.Files.createTempDirectory("tes-affected-crown-");
            try {
                var initial=com.chedidandrew.emeraldstandard.core.EconomyState.fresh(78,0,0);
                var registered=initial.village(district.villageId);registered.projects.add(job.copy());registered.projectSerial=1;
                initial.save(temp.resolve("the_emerald_standard.properties"));
                var economy=new com.chedidandrew.emeraldstandard.core.EconomyService();economy.start(temp,78,0);
                check((int)replay.invoke(null,level,economy,district,job,128)==crown.cells().size(),
                        "complete-tree receipt did not replay in production preparation");
                for(var cell:crown.cells())check(level.getBlockState(BlockPos.of(cell.position())).isAir(),"tree remnant after preparation");
                check(economy.villageSnapshot(district.villageId).village().projects.getFirst().sitePreparationComplete,
                        "tree preparation did not commit completion");
                var restart=new com.chedidandrew.emeraldstandard.core.EconomyService();restart.start(temp,78,0);
                check(restart.villageSnapshot(district.villageId).village().projects.getFirst().sitePreparationComplete,
                        "complete crown receipt lost completion after restart");
            } finally {
                try(var files=java.nio.file.Files.walk(temp)) {
                    for(var file:files.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(file);
                }
            }
            for(int y=0;y<4;y++)level.setBlock(origin.above(y),Blocks.OAK_LOG.defaultBlockState(),18);
            level.setBlock(origin.above(4),Blocks.OAK_LEAVES.defaultBlockState(),18);
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
            verifyModdedPlants(level,origin,land,dimension);
            System.out.println("PASS vegetation: shared plants, crops/containers/fluids/persistent leaves, player gardens, planted trees, bounded trees, complete affected canopy, leaf-only clipping, snow caps, untouched shared crowns, claims and reload cache");
        } catch(Exception ex) { throw new IllegalStateException("Vegetation compatibility fixture",ex); }
        finally {
            try {land.observe(dimension,origin.asLong(),prior==null?"":prior);}
            catch(Exception ex){throw new IllegalStateException(ex);}
            saved.forEach((pos,state)->level.setBlock(pos,state,18));
        }
    }
    private static void verifyModdedPlants(ServerLevel level,BlockPos origin,
            com.chedidandrew.emeraldstandard.core.DevelopmentLand land,String dimension) throws Exception {
        var weed=BuiltInRegistries.BLOCK.getValue(Identifier.parse("wilderwild:tumbleweed_plant"));
        if(System.getenv("TES_TEST_VEGETATION_DIR")!=null)check(weed!=null&&weed!=Blocks.AIR,"real Wilder Wild not loaded");
        if(weed==null||weed==Blocks.AIR)return;
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=0;y<=4;y++)
            level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
        var placementType=Class.forName(VillageProsperityManager.class.getName()+"$Placement");
        var constructor=placementType.getDeclaredConstructor(int.class,int.class,int.class,BlockState.class);
        constructor.setAccessible(true);
        var placement=constructor.newInstance(0,1,0,Blocks.STONE.defaultBlockState());
        var mayApply=VillageProsperityManager.class.getDeclaredMethod("mayApplyPlacement",BlockState.class,placementType);
        mayApply.setAccessible(true);
        int states=0,solid=0;
        for(Block block:BuiltInRegistries.BLOCK) {
            if(!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("wilderwild"))continue;
            if(!(block instanceof VegetationBlock||block instanceof GrowingPlantBlock
                    ||block instanceof VineBlock||block instanceof CactusBlock
                    ||block instanceof SugarCaneBlock||block instanceof BambooStalkBlock
                    ||block.defaultBlockState().is(VegetationCompatibility.GROUND_COVER)))continue;
            for(var state:block.getStateDefinition().getPossibleStates()) {
                if(VegetationCompatibility.forbidden(state)) {
                    check(!VegetationCompatibility.open(state),"unsafe modded plant accepted "+state);continue;
                }
                states++;
                if(!state.getCollisionShape(level,origin).isEmpty())solid++;
                level.setBlock(origin,state,18);
                check(VegetationCompatibility.open(state)&&VillageBridges.clear(state)
                        &&VillageSitePreparation.clearable(level,origin),"modded plant blocks development "+state);
                check(new VillageSitePreparation.Survey(level).surface(origin.getX(),origin.getZ())==origin.getY(),
                        "plant canopy mistaken for ground "+state);
                var plan=new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0);
                check(plan!=null&&plan.cells().size()==1,"plant rejected at final freeze "+state);
                check(VillageSitePreparation.matchesRemoval(state,"minecraft:short_grass")
                        &&!VillageSitePreparation.matchesRemoval(state,"minecraft:oak_planks")
                        &&!VillageSitePreparation.matchesRemoval(state,"minecraft:stone"),"regrowth escaped approved clearing cell "+state);
                check(VillageDevelopmentProtection.mayPlace(level,null,0,origin,state,Blocks.STONE.defaultBlockState()),
                        "cleared plant cannot become building "+state);
                check((Boolean)mayApply.invoke(null,state,placement),"materializer rejects modded regrowth "+state);
                level.setBlock(origin,Blocks.STONE.defaultBlockState(),18);
                check(level.getBlockState(origin).is(Blocks.STONE),"plant override did not persist "+state);
            }
        }
        check(states>=weed.getStateDefinition().getPossibleStates().size()&&solid>0,"no mature colliding weed coverage");
        var state=weed.defaultBlockState();level.setBlock(origin,state,18);
        DevelopmentLandProtection.placed(level,origin,state);
        check(!VillageDevelopmentProtection.mayPlace(level,null,0,origin,state,Blocks.AIR.defaultBlockState()),
                "automatic vegetation override removed recorded player garden");
        DevelopmentLandProtection.tick(level.getServer());
        check(!VillageSitePreparation.clearable(level,origin),"modded garden provenance lost");
        land.observe(dimension,origin.asLong(),"");
        try(var claim=VillageDevelopmentProtection.register(c->!c.position().equals(origin))) {
            check(new VillageSitePreparation.Survey(level).freeze(List.of(origin),origin.getY(),null,0)==null,
                    "modded plant clearing bypassed claim");
        }
        System.out.println("PASS real Wilder Wild vegetation: "+states+" states, "+solid
                +" collision-bearing states; ground survey/freeze/override, bounded regrowth, garden and claim protection");
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
