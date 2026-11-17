package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class TerrainDevelopmentSelfTest {
    static void verify(ServerLevel level) throws Exception {
        var v=new EconomyState.VillageRecord();
        v.villageId=UUID.fromString("30bbb084-0e75-39e4-8bc6-a591308bc5ef");
        v.architectureCharacter="rustic";v.architectureDialect="plains";v.developmentTier=2;
        var p=new EconomyState.VillageProject();p.projectId=3;p.type=VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
        p.designSchema="blueprint_v2";p.designTemplateId="biome_plains_mine_headframe_01";p.designTemplateRevision=13;
        p.designPaletteId="balanced";p.designDressingId="prosperous";p.designSeed=4518264180380142599L;
        p.designStage=1;p.designRotation=1;p.designMirrored=true;p.designOutdoorLevel=3;
        p.designSignature=-2117584051366243523L;p.designPlanHashVersion=1;
        p.designPlanHash="";
        p.originPos=new BlockPos(-1114,76,748).asLong();p.totalBlocks=1728;p.materializedBlocks=1338;
        p.constructionOrderCuts.addAll(List.of(0,1728));
        var method=VillageProsperityManager.class.getDeclaredMethod("projectTemplate",ServerLevel.class,
                BlockPos.class,EconomyState.VillageRecord.class,EconomyState.VillageProject.class);
        method.setAccessible(true);
        var canonical=(List<?>)method.invoke(null,level,BlockPos.of(p.originPos),v,p);
        var orderMethod=VillageProsperityManager.class.getDeclaredMethod("constructionTemplate",List.class,EconomyState.VillageProject.class);
        orderMethod.setAccessible(true);
        var ordered=(List<?>)orderMethod.invoke(null,canonical,p);
        require(ordered.size()==1728,"Capture plan size changed");
        Object rail=ordered.get(1338);
        var satisfies=VillageProsperityManager.class.getDeclaredMethod("placementSatisfied",ServerLevel.class,
                BlockPos.class,BlockPos.class,BlockState.class,rail.getClass());satisfies.setAccessible(true);
        BlockPos fixture=new BlockPos(4200,level.getMaxY()-40,4200),target=fixture.offset(20,2,1);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        try {
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=3;y++) {
                BlockPos at=target.offset(x,y,z);level.getChunk(at);before.put(at,level.getBlockState(at));
                level.setBlock(at,(y<0?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            level.setBlock(target.west(),Blocks.OAK_LOG.defaultBlockState(),3);
            level.setBlock(target.west().above(),Blocks.RAIL.defaultBlockState(),3);
            level.setBlock(target,Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST),3);
            require(level.getBlockState(target).getValue(RailBlock.SHAPE)==RailShape.ASCENDING_WEST,"Vanilla slope fixture did not resolve");
            require((Boolean)satisfies.invoke(null,level,fixture,target,level.getBlockState(target),rail),"Native sloped rail still blocks frozen flat intent");
            require(!(Boolean)satisfies.invoke(null,level,fixture,target,Blocks.COBBLESTONE.defaultBlockState(),rail),"Foreign obstruction waived");
            level.setBlock(target.below(),Blocks.AIR.defaultBlockState(),18);
            require(!(Boolean)satisfies.invoke(null,level,fixture,target,level.getBlockState(target),rail),"Unsupported rail accepted");
            require(VegetationCompatibility.naturalGround(Blocks.COAL_ORE.defaultBlockState())
                    &&VegetationCompatibility.naturalGround(Blocks.IRON_ORE.defaultBlockState())
                    &&VegetationCompatibility.naturalGround(Blocks.COPPER_ORE.defaultBlockState()),"Natural rocky terrain misclassified");
            require(!VegetationCompatibility.naturalGround(Blocks.CHEST.defaultBlockState()),"Contents treated as terrain");
        } finally {before.forEach((at,state)->level.setBlock(at,state,18));}
        terrain(level);
        ApprovedArchitectureRuntimeSelfTest.mineCapture(level);
        ApprovedArchitectureRuntimeSelfTest.yardSlope(level);
        System.out.println("PASS terrain development: captured mine rail mismatch, native slopes/support/obstruction, ores, terrain-fitted yards and full mine restart/handover");
    }
    private static void terrain(ServerLevel level) {
        var p=new EconomyState.VillageProject();p.designTemplateRevision=13;p.designTemplateId="biome_plains_cottage_hearth_01";
        var data=ApprovedVillageStructures.data(p.designTemplateId,13);var core=ApprovedVillageStructures.data(p.designTemplateId,13,3);
        BlockPos origin=new BlockPos(4300,level.getMaxY()-40,4300);Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        try {
            for(int x=-2;x<=data.identity().width()+2;x++)for(int z=-2;z<=data.identity().depth()+2;z++)for(int y=-6;y<=8;y++) {
                BlockPos at=origin.offset(x,y,z);level.getChunk(at);before.put(at,level.getBlockState(at));
                int surface=x<data.identity().width()/2?-1:0;
                level.setBlock(at,(y<surface?Blocks.DIRT:y==surface?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),18);
            }
            var shifts=OutdoorTerrainFitting.freeze(level,origin,p);
            require(shifts!=null&&!shifts.isEmpty(),"Gently stepped yard was not fitted to actual ground");
            int cx=data.entrance().getX()-core.entrance().getX(),cz=data.entrance().getZ()-core.entrance().getZ();
            for(long key:shifts.keySet()) {
                var at=BlockPos.of(key);
                require(at.getX()<cx||at.getX()>=cx+core.identity().width()||at.getZ()<cz||at.getZ()>=cz+core.identity().depth(),
                        "Terrain fitting moved the main building");
            }
            p.designOutdoorLevel=3;
            require(OutdoorTerrainFitting.freeze(level,origin,p).isEmpty(),"Core-only building must remain rigid");
        } finally {before.forEach((at,state)->level.setBlock(at,state,18));}
    }
    private static void require(boolean value,String detail) {if(!value)throw new IllegalStateException(detail);}
}
