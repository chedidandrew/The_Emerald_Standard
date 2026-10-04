package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Only runs in the disposable dedicated-server fixture. Uses actual managers and saved plans. */
final class ApprovedArchitectureRuntimeSelfTest {
    static List<Runnable> checks(ServerLevel level) {
        var checks=new ArrayList<Runnable>();
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            checks.add(()->ordinary(level,style));
            checks.add(()->bank(level,style));
        }
        checks.add(()->ConstructionFinishSelfTest.verify(level));
        checks.add(()->ConstructionSupportRecoverySelfTest.verify(level));
        return List.copyOf(checks);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();ServerPlayer observer=null;
        BlockPos origin=new BlockPos(1808+style.ordinal()*96,level.getMaxY()-64,1808);
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            var v=state.village(UUID.randomUUID());v.dimensionKey="minecraft:overworld";v.centerPos=origin.asLong();
            v.architectureDialect=style.id();v.architectureCharacter="rustic";v.developmentTier=5;
            v.population=v.observedPopulation=30;v.housingCapacity=50;v.foodSupply=v.materialSupply=2000;
            v.treasury=1000;v.prosperity=v.safety=100;
            var p=new EconomyState.VillageProject();p.projectId=1;
            p.type=style==VillageArchitecture.BiomeDialect.TAIGA?VillageProsperityEngine.ProjectType.INN:VillageProsperityEngine.ProjectType.COTTAGE;
            p.designSchema="blueprint_v2";p.designTemplateRevision=12;
            p.designTemplateId="biome_"+style.id()+(p.type==VillageProsperityEngine.ProjectType.INN?"_inn_coachhouse_02":"_cottage_hearth_01");
            p.designPaletteId="balanced";p.designDressingId="prosperous";p.designSeed=1234;
            p.designStage=2;p.designRotation=style.ordinal()%4;p.designMirrored=style.ordinal()%2==1;
            p.designSignature=VillageArchitecture.blueprintSignature(p.type,p.designTemplateId,12,p.designPaletteId,p.designDressingId,p.designMirrored);
            p.originPos=origin.asLong();p.economicComplete=true;p.economicProgress=1;
            p.sitePreparationComplete=true;p.constructionStarted=true;
            p.entranceApproachComplete=true;p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            p.trailAnchorSet=true;p.trailAnchorPos=origin.west(25).asLong();p.trailMaterializedComplete=true;
            List<?> canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);
            p.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,v,p)).size();
            p.trailMaterializedBlocks=p.trailTotalBlocks;
            p.totalBlocks=canonical.size();p.constructionOrderCuts.addAll(List.of(0,p.totalBlocks));
            var descriptor=VillageArchitecture.requireBlueprint(p.designTemplateId,12);
            int radius=Math.max(descriptor.width(),descriptor.depth());
            for(int x=-6;x<=radius+6;x++) for(int z=-6;z<=radius+6;z++)
                for(int y=-6;y<0;y++) set(level,before,origin.offset(x,y,z),(y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
            for(Object c:canonical) {
                BlockPos at=target(origin,c);
                if(at.getY()>=origin.getY()) set(level,before,at,Blocks.AIR.defaultBlockState());
            }
            p.sitePreparationPlan=(SitePreparationPlan)invoke("prepareProjectSitePlan",level,origin,v,p,canonical);
            require(p.sitePreparationPlan!=null,"Approved ordinary terrain preflight rejected "+style);
            p.sitePreparationComplete=false;
            Object bounds=invoke("bounds",origin,canonical);
            p.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();p.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            p.designPlanHash=(String)invoke("blueprintPlanHash",v,p,invoke("blueprintPlacementPlan",level,origin,v,p));p.designPlanHashVersion=1;
            v.projects.add(p);v.projectSerial=1;
            var directory=Files.createTempDirectory("tes-approved-building-");state.save(directory.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService();economy.configureVillageProsperity(true,true);economy.start(directory,1234,0);economy.configureForcedVillageDevelopment(true);
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"ReleaseFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+80,origin.getY(),origin.getZ());level.players().add(observer);
            var props=new Properties();props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"true");var config=EmeraldConfig.parse(props);
            var budgetType=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget=budgetType.getDeclaredConstructor(int.class,int.class);budget.setAccessible(true);
            boolean restarted=false;
            for(int pulse=1;pulse<=20000;pulse++) {
                ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),economy.developmentVillageSnapshot(v.villageId));
                var current=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                if(!restarted&&current.materializedBlocks>64&&!current.materializedComplete) {
                    restarted=true;
                    var reload=new EconomyService();reload.configureVillageProsperity(true,true);reload.start(directory,1234,0);reload.configureForcedVillageDevelopment(true);economy=reload;
                    require(current.designPlanHash.equals(economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst().designPlanHash),"Restart changed frozen geometry");
                }
                if(current.materializedComplete) break;
                if(pulse==20000) {
                    List<?> ordered=(List<?>)invoke("constructionTemplate",canonical,current);
                    Object cell=ordered.get(current.materializedBlocks);BlockPos at=target(origin,cell);
                    throw new IllegalStateException("Release building stalled "+current.materializedBlocks+"/"+current.totalBlocks
                            +" at "+at+" expected "+field(cell,"state")+" actual "+level.getBlockState(at)
                            +" role "+field(cell,"role")+" phase "+field(cell,"constructionPhase")
                            +" "+ConstructionDiagnostics.recent(v.villageId+"/1",pulse*20L));
                }
            }
            for(Object c:canonical) {
                BlockState expected=(BlockState)field(c,"state");if(expected.isAir()) continue;
                if(field(c,"role").toString().endsWith("SUPPORT")) continue; // Stable natural substrate satisfies adaptive footing.
                BlockPos at=target(origin,c);
                if(VillageProsperityManager.naturalSoilEquivalent(level.getBlockState(at),expected)) continue; // Native grass spreads or decays.
                require(level.getBlockState(at).is(expected.getBlock()),"Missing approved block "+p.designTemplateId+" "+at+" expected "+expected+" actual "+level.getBlockState(at));
                if(expected.getBlock() instanceof LadderBlock) require(level.getBlockState(at).canSurvive(level,at),"Ladder lost its bearing");
            }
            var reload=new EconomyService();reload.start(directory,1234,0);
            require(reload.developmentVillageSnapshot(v.villageId).village().projects.getFirst().materializedComplete,"Handover not persisted");
            var data=ApprovedVillageStructures.data(p.designTemplateId);var at=data.door();
            int doorX=p.designMirrored?data.identity().width()-1-at.getX():at.getX();
            BlockPos primaryDoor=origin.offset((BlockPos)invoke("rotateRelative",doorX,at.getY(),at.getZ(),invoke("projectSize",p),p.designRotation));
            VillageWalkingSelfTest.building(level,BlockPos.of(p.boundsMinPos),BlockPos.of(p.boundsMaxPos),"approved "+style,primaryDoor);
            System.out.println("PASS live approved "+p.designTemplateId+": "+canonical.size()+" operations, rotation/mirror, partial restart and durable handover");
        } catch(Exception failure) {throw new IllegalStateException("Approved ordinary runtime "+style,failure);}
        finally {if(observer!=null){level.players().remove(observer);observer.discard();}before.forEach((at,s)->level.setBlock(at,s,18));ConstructionDiagnostics.reset();}
    }
    private static void bank(ServerLevel level,VillageArchitecture.BiomeDialect style) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();ServerPlayer observer=null;
        BlockPos origin=new BlockPos(1808+style.ordinal()*96,level.getMaxY()-64,2000);long key=8800+style.ordinal();
        try {
            var directory=Files.createTempDirectory("tes-approved-bank-");var economy=new EconomyService();economy.start(directory,1234,0);economy.configureForcedVillageDevelopment(true);
            BankStyleLedger.get(level).remember(origin.asLong(),style.id());
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);var v=state.village(UUID.randomUUID());
            v.dimensionKey="minecraft:overworld";v.centerPos=origin.asLong();v.architectureDialect=style.id();v.developmentTier=5;v.population=v.observedPopulation=30;v.housingCapacity=50;v.foodSupply=v.materialSupply=2000;v.treasury=1000;v.prosperity=v.safety=100;
            state.save(directory.resolve("the_emerald_standard.properties"));economy=new EconomyService();economy.start(directory,1234,0);economy.configureForcedVillageDevelopment(true);
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"BankReleaseFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+80,origin.getY(),origin.getZ());level.players().add(observer);
            int version=13+style.ordinal()%3;var nativePlan=ApprovedBankStructures.plan(style,version);
            for(int x=nativePlan.minX()-5;x<=nativePlan.maxX()+5;x++) for(int z=nativePlan.minZ()-10;z<=nativePlan.maxZ()+5;z++)
                for(int y=-6;y<0;y++) set(level,before,origin.offset(x,y,z),(y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
            var prepare=VillageBankManager.class.getDeclaredMethod("prepareApprovedBank",ServerLevel.class,EconomyService.class,BlockPos.class,UUID.class,long.class,int.class);prepare.setAccessible(true);
            var plan=(BankConstruction)prepare.invoke(null,level,economy,origin,v.villageId,key,version);
            require(plan!=null&&economy.reserveBankConstruction(key,plan),"Bank parcel reservation rejected "+style);
            for(var cell:plan.cells()) before.putIfAbsent(BlockPos.of(cell.position()),level.getBlockState(BlockPos.of(cell.position())));
            require(VillageBankManager.approvedBankLots(level,economy).size()==1,"Pending Bank yard not protected");
            for(int pulse=0;pulse<plan.cells().size()+40&&economy.pendingBankConstructionsSnapshot().containsKey(key);pulse++) {
                VillageBankManager.advanceBankConstruction(level,economy,key,plan);
                if(pulse==2) {var reload=new EconomyService();reload.start(directory,1234,0);reload.configureForcedVillageDevelopment(true);economy=reload;require(plan.cells().equals(economy.pendingBankConstructionsSnapshot().get(key).cells()),"Bank plan changed on restart");}
            }
            require(economy.hasGeneratedBankRegion(key),"Bank did not finish "+style+" "+ConstructionDiagnostics.recent("bank:"+key,level.getGameTime()));
            require(VillageBankManager.bankOperationProblem(level,economy,key).isEmpty(),"Approved Bank incorrectly damaged: "+VillageBankManager.bankOperationProblem(level,economy,key));
            BlockPos floor=nativePlan.air().stream().filter(at->nativePlan.air().contains(at.above()))
                    .filter(at->{var bearing=nativePlan.cells().get(at.below());return bearing!=null
                            &&bearing.isFaceSturdy(level,origin.offset(at.below()),net.minecraft.core.Direction.UP);})
                    .min(Comparator.comparingDouble(at->at.distSqr(new BlockPos(6,1,8)))).orElseThrow().below();
            BlockPos actualFloor=origin.offset(floor);var intactFloor=level.getBlockState(actualFloor);
            level.setBlock(actualFloor,Blocks.AIR.defaultBlockState(),18);
            require(VillageBankManager.bankOperationProblem(level,economy,key).contains("floor"),"Missing circulation floor remained operational "+style);
            level.setBlock(actualFloor,intactFloor,18);
            require(VillageBankManager.bankOperationProblem(level,economy,key).isEmpty(),"Restored Bank floor did not resume service "+style);
            BlockPos start=new BankWalkways.Bank(key,plan.bankerAnchor(),version,style).start();
            require(BankWalkways.candidates(level,economy,v).stream().anyMatch(bank->bank.key()==key),
                    "Approved Bank walkway missing at "+start+" ground="+level.getBlockState(start)
                            +" feet="+level.getBlockState(start.above())+" head="+level.getBlockState(start.above(2))
                            +" active="+VillageBankManager.bankWorkActive(level,economy,v.villageId,start,256));
            for(var cell:nativePlan.cells().entrySet()) require(level.getBlockState(origin.offset(cell.getKey())).is(cell.getValue().getBlock()),"Bank lost authored cell "+cell);
            VillageWalkingSelfTest.building(level,origin.offset(nativePlan.minX(),-3,nativePlan.minZ()),
                    origin.offset(nativePlan.maxX(),nativePlan.height()+3,nativePlan.maxZ()),"approved Bank "+style,
                    origin.offset(nativePlan.entrance()));
            var reload=new EconomyService();reload.start(directory,1234,0);require(reload.generatedBankStructureVersion(key)==version,"Bank version not persisted");
            System.out.println("PASS live approved Bank "+style+" v"+version+": full terrain reservation, progressive build, restart, operational desk and road connection");
        } catch(Exception failure){throw new IllegalStateException("Approved Bank runtime "+style,failure);}
        finally {
            if(observer!=null){level.players().remove(observer);observer.discard();}
            level.getEntitiesOfClass(Villager.class,new AABB(origin).inflate(64),v->BankerAccess.isBankerForRegion(v,key)).forEach(Villager::discard);
            before.forEach((at,s)->level.setBlock(at,s,18));ConstructionDiagnostics.reset();
        }
    }
    private static Object invoke(String name,Object...args)throws Exception{return ConstructionSupportRecoverySelfTest.invoke(name,args);}
    private static Object field(Object o,String name)throws Exception{return ConstructionSupportRecoverySelfTest.field(o,name);}
    private static Object method(Object o,String name)throws Exception{var m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}
    private static BlockPos target(BlockPos origin,Object c)throws Exception{return origin.offset((int)field(c,"dx"),(int)field(c,"dy"),(int)field(c,"dz"));}
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos at,BlockState state){level.getChunk(at);before.putIfAbsent(at,level.getBlockState(at));level.setBlock(at,state,18);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
