package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.authlib.GameProfile;

/** Replays the reported 944/948 cottage in a disposable fixture, never the player's world. */
final class ConstructionFinishSelfTest {
    static void verify(ServerLevel level) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        ServerPlayer observer=null;
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            var v=state.village(UUID.fromString("04967698-48f9-99e1-38ff-bdb7906c4260"));
            v.dimensionKey="minecraft:overworld";v.architectureCharacter="mercantile";v.architectureDialect="taiga";
            v.population=v.observedPopulation=20;v.housingCapacity=40;
            v.foodSupply=v.materialSupply=2000;v.treasury=1000;v.prosperity=v.safety=100;
            var p=new EconomyState.VillageProject();
            p.projectId=1;p.type=VillageProsperityEngine.ProjectType.COTTAGE;
            p.designSchema="blueprint_v2";p.designTemplateId="cottage_hearth_01";p.designTemplateRevision=10;
            p.designPaletteId="balanced";p.designDressingId="lived_in";p.designSeed=-7022177140377160153L;
            p.designStage=0;p.designRotation=0;p.designMirrored=false;p.designSignature=5692111586254340069L;
            p.originPos=516220714201153L;p.materializedBlocks=944;p.totalBlocks=948;
            p.constructionOrderCuts.addAll(List.of(0,948));
            BlockPos origin=new BlockPos(1536,level.getMaxY()-40,1536);v.centerPos=origin.asLong();
            List<?> canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);
            List<?> ordered=(List<?>)invoke("constructionTemplate",canonical,p);
            require(ordered.size()==948,"reported frozen cottage operation count");
            for(int i=0;i<ordered.size();i++) {
                Object cell=ordered.get(i);BlockPos target=target(origin,cell);
                for(int y=-5;y<0;y++) set(level,before,new BlockPos(target.getX(),origin.getY()+y,target.getZ()),Blocks.DIRT.defaultBlockState());
                set(level,before,target,Blocks.AIR.defaultBlockState());
            }
            for(int i=0;i<944;i++) { Object c=ordered.get(i);set(level,before,target(origin,c),(BlockState)field(c,"state")); }
            // Read-only region probe: the barrel below remains, but the optional double-slab
            // base at 1885,67,1216 and flowerpot at 1885,68,1216 are both absent.
            BlockPos flower=target(origin,ordered.get(944)),missingBase=flower.below();
            set(level,before,missingBase,Blocks.AIR.defaultBlockState());
            p.originPos=origin.asLong();p.economicComplete=true;p.economicProgress=1;
            p.sitePreparationComplete=true;p.constructionStarted=true;
            p.entranceApproachComplete=true;p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            p.trailAnchorSet=true;p.trailAnchorPos=origin.west(25).asLong();
            p.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,v,p)).size();
            p.trailMaterializedBlocks=p.trailTotalBlocks;p.trailMaterializedComplete=true;
            Object bounds=invoke("bounds",origin,canonical);
            p.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();p.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            p.designPlanHash=(String)invoke("blueprintPlanHash",v,p,invoke("blueprintPlacementPlan",level,origin,v,p));
            p.designPlanHashVersion=1;v.projects.add(p);v.projectSerial=1;
            v.organicTerritory=true;v.centerPos=origin.offset(2048,0,0).asLong();
            VillageTerritory.seedNatural(v,state.villages.values(),Set.of(VillageTerritory.parcel(origin.asLong())));
            require(level.getChunkSource().getChunkNow((origin.getX()+2048)>>4,origin.getZ()>>4)==null,"original center must be unloaded");
            var dir=Files.createTempDirectory("tes-cottage-finish-");
            state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService();economy.configureEconomicClock(false,30);economy.configureVillageProsperity(true,true);
            economy.start(dir,1234,0);economy.configureForcedVillageDevelopment(true);
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"FinishFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+60,origin.getY(),origin.getZ());level.players().add(observer);
            var props=new Properties();props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"true");var config=EmeraldConfig.parse(props);
            var type=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget=type.getDeclaredConstructor(int.class,int.class);budget.setAccessible(true);
            for(int pulse=1;pulse<=200;pulse++) {
                ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),economy.developmentVillageSnapshot(v.villageId));
                var current=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                if(current.materializedComplete) break;
                if(pulse==200) throw new IllegalStateException("Cottage stalled "+current.materializedBlocks+"/"+current.totalBlocks
                        +" "+ConstructionDiagnostics.recent(v.villageId+"/1",pulse*20L));
            }
            require(economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst().materializedComplete,"cottage handover");
            require(level.getBlockState(flower).isAir() && level.getBlockState(missingBase).isAir(),"unsupported optional pot/base stay waived");
            System.out.println("PASS exact 944/948 cottage with missing optional base and unloaded original center finishes without floating flowerpot, terrain overwrite or false handover");
        } catch(Exception e) {throw new IllegalStateException("Construction finish fixture",e);}
        finally {
            if(observer!=null){level.players().remove(observer);observer.discard();}
            before.forEach((p,s)->level.setBlock(p,s,18));ConstructionDiagnostics.reset();
        }
    }
    private static BlockPos target(BlockPos origin,Object cell)throws Exception{
        return origin.offset((int)field(cell,"dx"),(int)field(cell,"dy"),(int)field(cell,"dz"));
    }
    private static Object invoke(String name,Object...args)throws Exception{return ConstructionSupportRecoverySelfTest.invoke(name,args);}
    private static Object field(Object o,String n)throws Exception{return ConstructionSupportRecoverySelfTest.field(o,n);}
    private static Object method(Object o,String n)throws Exception{var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos p,BlockState s){
        level.getChunk(p);before.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,s,18);
    }
}
