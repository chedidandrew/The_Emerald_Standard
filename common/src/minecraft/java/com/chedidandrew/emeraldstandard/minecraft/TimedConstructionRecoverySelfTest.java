package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.authlib.GameProfile;
import static com.chedidandrew.emeraldstandard.minecraft.ConstructionSupportRecoverySelfTest.*;

/** Replays the two beta.49 frozen plans from D044A9C2 in a disposable world. */
final class TimedConstructionRecoverySelfTest {
    static void verify(ServerLevel level, boolean inn) {
        verify(level,inn,true);
    }
    static void verify(ServerLevel level, boolean inn, boolean forced) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>(); ServerPlayer observer=null;
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            UUID id=UUID.nameUUIDFromBytes(("timed-recovery-"+inn+forced).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var village=state.village(id); village.villageId=id;
            village.architectureCharacter="agrarian"; village.architectureDialect="desert"; village.developmentTier=3;
            village.dimensionKey="minecraft:overworld"; village.population=village.observedPopulation=30;
            village.housingCapacity=50; village.foodSupply=village.materialSupply=2000;
            village.treasury=1000; village.prosperity=village.safety=100;
            var project=new EconomyState.VillageProject(); project.projectId=inn?5:2;
            project.type=inn?VillageProsperityEngine.ProjectType.INN:VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
            project.designSchema="blueprint_v2"; project.designTemplateId=inn?"inn_coachhouse_02":"mine_adit_03";
            project.designTemplateRevision=10; project.designPaletteId="timber_forward"; project.designDressingId="prosperous";
            project.designSeed=inn?2802597014391160418L:6441931907272661547L;
            project.designSignature=inn?-5032398693630211070L:5620633266650567618L;
            project.designStage=1; project.designRotation=0; project.designMirrored=true;
            project.totalBlocks=inn?3749:959; project.materializedBlocks=inn?2335:648;
            project.constructionOrderCuts.addAll(List.of(0,project.totalBlocks));
            var origin=new BlockPos(inn?2080:2000,level.getMaxY()-48,2000);
            village.centerPos=project.originPos=origin.asLong();
            List<?> canonical=(List<?>)invoke("projectTemplate",level,origin,village,project);
            List<?> ordered=(List<?>)invoke("constructionTemplate",canonical,project);
            require(ordered.size()==project.totalBlocks,"exact frozen operation count for "+project.designTemplateId);
            for(Object cell:ordered) {
                BlockPos pos=target(origin,cell);
                for(int y=-5;y<0;y++) set(level,before,new BlockPos(pos.getX(),origin.getY()+y,pos.getZ()),Blocks.DIRT.defaultBlockState());
                set(level,before,pos,Blocks.AIR.defaultBlockState());
            }
            for(int i=0;i<project.materializedBlocks;i++) {
                Object cell=ordered.get(i); set(level,before,target(origin,cell),(BlockState)field(cell,"state"));
            }
            BlockPos stalled=target(origin,ordered.get(project.materializedBlocks));
            require(stalled.equals(origin.offset(-3,4,4)),"reported lamp arm location");
            set(level,before,stalled.below(),Blocks.AIR.defaultBlockState());
            set(level,before,stalled.west(),Blocks.AIR.defaultBlockState());
            project.economicComplete=true; project.economicProgress=1; project.sitePreparationComplete=true; project.constructionStarted=true;
            project.entranceApproachComplete=true; project.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            project.trailAnchorSet=true; project.trailAnchorPos=origin.east(20).asLong();
            project.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,village,project)).size();
            project.trailMaterializedBlocks=project.trailTotalBlocks; project.trailMaterializedComplete=true;
            Object bounds=invoke("bounds",origin,canonical);
            project.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong(); project.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            project.designPlanHash=(String)invoke("blueprintPlanHash",village,project,invoke("blueprintPlacementPlan",level,origin,village,project));
            project.designPlanHashVersion=1; village.projects.add(project); village.projectSerial=project.projectId;
            var dir=Files.createTempDirectory("tes-timed-recovery-"); state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService(); economy.configureEconomicClock(false,30); economy.configureVillageProsperity(true,true); economy.start(dir,1234,0);
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"TimedFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+100,origin.getY(),origin.getZ()); level.players().add(observer);
            var props=new Properties(); props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,Boolean.toString(forced)); var config=EmeraldConfig.parse(props);
            if(!forced) VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.projectTag(id,project.projectId,project.originPos));
            var budget=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget").getDeclaredConstructor(int.class,int.class); budget.setAccessible(true);
            boolean finished=false;
            for(int pulse=1;pulse<=1500;pulse++) {
                ForcedDevelopmentRuntime.reset(); ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(128,1),economy.developmentVillageSnapshot(id));
                var current=economy.developmentVillageSnapshot(id).village().projects.getFirst();
                if(current.materializedComplete) { finished=true; break; }
                if(pulse==1500) throw new IllegalStateException(current.materializedBlocks+"/"+current.totalBlocks+" "+ConstructionDiagnostics.recent(id+"/"+project.projectId,pulse*20L));
            }
            require(finished,"reported build hands over");
            var reload=new EconomyService(); reload.start(dir,1234,0);
            require(reload.developmentVillageSnapshot(id).village().projects.getFirst().materializedComplete,"handover survives restart");
            require(ConstructionRecovery.site(level,"brand-new-site").mode()==ConstructionRecoveryWindow.Mode.NORMAL,"next build starts normally");
            System.out.println("PASS timed support recovery: exact "+project.designTemplateId+" "+project.materializedBlocks+"/"+project.totalBlocks+" finishes and persists; forced="+forced);
        } catch(Exception e) { throw new IllegalStateException("Timed support recovery "+inn,e); }
        finally { if(observer!=null) {level.players().remove(observer);observer.discard();} before.forEach((pos,s)->level.setBlock(pos,s,18)); ConstructionDiagnostics.reset(); }
    }
    private static Object method(Object o,String name) throws Exception {var m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}
    private static BlockPos target(BlockPos origin,Object cell) throws Exception {return origin.offset((int)field(cell,"dx"),(int)field(cell,"dy"),(int)field(cell,"dz"));}
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos pos,BlockState state) {level.getChunk(pos);before.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,18);}
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
