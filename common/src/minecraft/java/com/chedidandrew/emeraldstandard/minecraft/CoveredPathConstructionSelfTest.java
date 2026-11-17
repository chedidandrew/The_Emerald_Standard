package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Replays the actual flat-world 735/1282 adit, never a user's world. */
final class CoveredPathConstructionSelfTest {
    static void verify(ServerLevel level) {
        run(level,false,true,13);
        run(level,true,true,13);
        run(level,false,false,13);
        run(level,true,false,13);
        run(level,true,true,14);
        run(level,false,true,15);
        run(level,true,true,15);
    }
    private static void run(ServerLevel level,boolean resumed,boolean forced,int revision) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>(); ServerPlayer observer=null;
        String job=null;
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            var v=state.village(UUID.fromString("a0ce1794-404e-3d0c-8f5d-2e4f5389a01b"));
            BlockPos origin=new BlockPos(3200,level.getMaxY()-56,3200);
            v.centerPos=origin.asLong();v.dimensionKey="minecraft:overworld";
            v.architectureCharacter="agrarian";v.architectureDialect="plains";v.developmentTier=2;
            v.population=v.observedPopulation=8;v.housingCapacity=40;
            v.foodSupply=v.materialSupply=2000;v.treasury=1000;v.prosperity=v.safety=100;
            var p=new EconomyState.VillageProject();p.projectId=3;p.type=VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
            p.designSchema="blueprint_v2";p.designTemplateId="biome_plains_mine_adit_03";
            p.designTemplateRevision=revision;p.designPaletteId="balanced";p.designDressingId="prosperous";
            p.designSeed=5731313124783626193L;p.designStage=0;p.designRotation=3;p.designMirrored=true;
            p.designOutdoorLevel=3;p.designSignature=VillageArchitecture.blueprintSignature(p.type,p.designTemplateId,
                    revision,p.designPaletteId,p.designDressingId,true);
            p.originPos=origin.asLong();p.economicComplete=true;p.economicProgress=1;
            p.sitePreparationComplete=true;p.constructionStarted=true;
            p.entranceApproachComplete=true;p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            p.trailAnchorSet=true;p.trailAnchorPos=origin.west(25).asLong();p.trailMaterializedComplete=true;
            List<?> canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);
            p.totalBlocks=canonical.size();p.constructionOrderCuts.addAll(List.of(0,p.totalBlocks));
            List<?> ordered=(List<?>)invoke("constructionTemplate",canonical,p);
            Object stuck=revision==13?ordered.get(735):ordered.stream().filter(c->{
                try{return (boolean)field(c,"nativePathGround");}catch(Exception e){throw new IllegalStateException(e);}
            }).findFirst().orElseThrow();
            int stuckIndex=ordered.indexOf(stuck);BlockPos foot=target(origin,stuck);
            require(revision!=13||(p.totalBlocks==1282 && foot.equals(origin.offset(18,0,7))),"captured geometry/cursor remain exact");
            require(block(stuck).is(Blocks.DIRT_PATH)&&(boolean)field(stuck,"nativePathGround"),"captured native covered path recognized");
            require(((BlockState)invoke("constructionState",stuck)).is(Blocks.DIRT),"stable native interpretation, not missing-cell waiver");
            verifyInterpretationBoundaries(stuck);
            for(int x=-4;x<=28;x++)for(int z=-4;z<=26;z++)for(int y=-6;y<=12;y++)
                set(level,before,origin.offset(x,y,z),(y==-1?Blocks.GRASS_BLOCK:y<0?Blocks.DIRT:Blocks.AIR).defaultBlockState());
            p.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,v,p)).size();p.trailMaterializedBlocks=p.trailTotalBlocks;
            Object bounds=invoke("bounds",origin,canonical);
            p.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();p.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            p.designPlanHash=(String)invoke("blueprintPlanHash",v,p,invoke("blueprintPlacementPlan",level,origin,v,p));p.designPlanHashVersion=1;
            // The user's modpack adds snow_layers to fence states, so its native-state hash
            // differs from this isolated vanilla-state fixture. Never waive a runtime hash check.
            require(p.designPlanHash.equals((String)invoke("blueprintPlanHash",v,p,
                    invoke("blueprintPlacementPlan",level,origin,v,p))),"native ground interpretation preserves canonical hash");
            p.materializedBlocks=resumed?stuckIndex:0;v.projects.add(p);v.projectSerial=3;
            var directory=Files.createTempDirectory("tes-covered-adit-");state.save(directory.resolve("the_emerald_standard.properties"));
            var economy=load(directory,forced);
            var ledger=ConstructionOwnership.get(level);job=ConstructionOwnership.project(v.villageId,3,p.originPos);
            ledger.begin(economy,job,v.villageId,3,p.originPos,false);
            if(resumed) {
                // Real stale capture has later ironwork installed after its cursor was rewound.
                for(Object c:ordered) {BlockPos at=target(origin,c);BlockState expected=block(c);
                    set(level,before,at,expected);
                    if(!expected.isAir()) ledger.claim(job,at,expected,expected.hasBlockEntity());
                }
                for(Object c:ordered)if((boolean)field(c,"nativePathGround"))set(level,before,target(origin,c),Blocks.GRASS_BLOCK.defaultBlockState());
                require(!block(stuck).canSurvive(level,foot)&&level.getBlockState(foot.above()).is(Blocks.IRON_BARS),"real native conflict reproduced");
            }
            // No foreign or absent footing may pass merely because a path has a planned cover.
            for(BlockState forbidden:List.of(Blocks.AIR.defaultBlockState(),Blocks.CHEST.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState()))
                require(!(boolean)invoke("placementSatisfied",level,origin,foot,forbidden,stuck),"foreign/missing required footing rejected");
            var diagnostic=(String)invoke("pendingCellDetail",level,origin,ordered,stuckIndex);
            require(diagnostic.contains("plannedCoveredPath=true")&&diagnostic.contains("constructionState=minecraft:dirt"),"debug exposes exact native interpretation");
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.projectTag(v.villageId,3,p.originPos));
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"CoveredPathFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+80,origin.getY(),origin.getZ());level.players().add(observer);
            var props=new Properties();props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,Boolean.toString(forced));
            var config=EmeraldConfig.parse(props);var type=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget=type.getDeclaredConstructor(int.class,int.class);budget.setAccessible(true);
            if(resumed&&forced&&revision==13) {
                try(var guard=VillageDevelopmentProtection.register(context->!context.position().equals(foot))) {
                    ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                    invoke("materializeDevelopment",level,economy,config,20L,budget.newInstance(64,1),economy.developmentVillageSnapshot(v.villageId));
                    var waiting=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                    require(!waiting.materializedComplete&&waiting.materializedBlocks==stuckIndex
                            &&level.getBlockState(foot).is(Blocks.GRASS_BLOCK),"forced native-ground adoption respects external protection");
                }
            }
            boolean restart=false;
            for(int pulse=1;pulse<=4000;pulse++) {
                ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),economy.developmentVillageSnapshot(v.villageId));
                settle(level,origin,ordered);
                var current=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                if(!restart&&current.materializedBlocks>Math.max(64,p.materializedBlocks)&&!current.materializedComplete) {
                    economy=load(directory,forced);restart=true;
                    require(economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst().designPlanHash.equals(p.designPlanHash),"restart preserves authored identity");
                }
                if(current.materializedComplete)break;
                if(pulse==4000)throw new IllegalStateException("Adit stalled "+current.materializedBlocks+"/"+current.totalBlocks+" "+ConstructionDiagnostics.recent(v.villageId+"/3",pulse*20L));
            }
            require(load(directory,forced).developmentVillageSnapshot(v.villageId).village().projects.getFirst().materializedComplete,"durable physical handover");
            require(level.getBlockState(foot).is(Blocks.DIRT)||level.getBlockState(foot).is(Blocks.GRASS_BLOCK),"sound footing retained");
            require(level.getBlockState(foot.above()).is(Blocks.IRON_BARS),"cover kept intact");
            for(Object c:ordered) if(field(c,"role").toString().equals("STRUCTURE"))
                require((boolean)invoke("placementSatisfied",level,origin,target(origin,c),level.getBlockState(target(origin,c)),c),"every required cell verified");
            System.out.println("PASS adit "+stuckIndex+"/"+p.totalBlocks+": resumed="+resumed+" forced="+forced+" revision="+revision+" native footing, original hash/cuts, restart and handover");
        }catch(Exception e){throw new IllegalStateException("Captured covered-path construction",e);}
        finally {if(observer!=null){level.players().remove(observer);observer.discard();}if(job!=null)ConstructionOwnership.get(level).finish(job);
            VillageConstructionActivitySelfTest.cleanupCrews(level);before.forEach((at,s)->level.setBlock(at,s,18));
            // These isolated economies deliberately reuse the captured identity within one
            // server tick. A protection wait normally expires through the real tick hook;
            // it must not leak from the completed replay into the next fresh fixture.
            ConstructionWorkStatus.reset();ConstructionDiagnostics.reset();}
    }
    private static EconomyService load(java.nio.file.Path directory,boolean forced) throws java.io.IOException {
        var e=new EconomyService();e.configureVillageProsperity(true,true);e.start(directory,1234,0);e.configureForcedVillageDevelopment(forced);return e;
    }
    private static void verifyInterpretationBoundaries(Object original) throws Exception {
        Object role=field(original,"role");
        var ctor=original.getClass().getDeclaredConstructor(int.class,int.class,int.class,BlockState.class,role.getClass(),boolean.class,int.class);
        ctor.setAccessible(true);
        Object ground=ctor.newInstance(0,0,0,Blocks.DIRT_PATH.defaultBlockState(),role,false,7);
        for(BlockState allowed:List.of(Blocks.AIR.defaultBlockState(),Blocks.OAK_FENCE_GATE.defaultBlockState())) {
            Object cover=ctor.newInstance(0,1,0,allowed,role,false,7);
            List<?> interpreted=(List<?>)invoke("nativeCoveredPathGround",List.of(ground,cover));
            require(!(boolean)field(interpreted.getFirst(),"nativePathGround"),"open/gated paths must retain exact state");
        }
        Object cover=ctor.newInstance(0,1,0,Blocks.IRON_BARS.defaultBlockState(),role,false,7);
        Object floor=ctor.newInstance(0,0,0,Blocks.DIRT_PATH.defaultBlockState(),role,false,0);
        List<?> interpreted=(List<?>)invoke("nativeCoveredPathGround",List.of(floor,cover));
        require(!(boolean)field(interpreted.getFirst(),"nativePathGround"),"arbitrary required floor is not native ground");
        Object cosmetic=Arrays.stream(role.getClass().getEnumConstants()).filter(r->r.toString().equals("COSMETIC")).findFirst().orElseThrow();
        Object optional=ctor.newInstance(0,1,0,Blocks.IRON_BARS.defaultBlockState(),cosmetic,false,7);
        interpreted=(List<?>)invoke("nativeCoveredPathGround",List.of(ground,optional));
        require(!(boolean)field(interpreted.getFirst(),"nativePathGround"),"unsupplied optional cover grants no required-footing waiver");
    }
    private static void settle(ServerLevel level,BlockPos origin,List<?> cells)throws Exception {
        for(Object c:cells)if(block(c).is(Blocks.DIRT_PATH)) {BlockPos at=target(origin,c);var s=level.getBlockState(at);
            if(s.is(Blocks.DIRT_PATH)&&!s.canSurvive(level,at))s.tick(level,at,level.getRandom());}
    }
    private static BlockPos target(BlockPos o,Object c)throws Exception {return o.offset((int)field(c,"dx"),(int)field(c,"dy"),(int)field(c,"dz"));}
    private static BlockState block(Object c)throws Exception{return (BlockState)field(c,"state");}
    private static Object field(Object c,String n)throws Exception{return ConstructionSupportRecoverySelfTest.field(c,n);}
    private static Object invoke(String n,Object...a)throws Exception{return ConstructionSupportRecoverySelfTest.invoke(n,a);}
    private static Object method(Object c,String n)throws Exception{var m=c.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(c);}
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos at,BlockState s){level.getChunk(at);before.putIfAbsent(at,level.getBlockState(at));level.setBlock(at,s,18);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
