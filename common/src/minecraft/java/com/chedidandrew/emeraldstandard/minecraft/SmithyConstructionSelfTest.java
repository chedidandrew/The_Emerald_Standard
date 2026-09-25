package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Replays the beta.33 Smithy at 1180/1975, without loading or changing a player save. */
final class SmithyConstructionSelfTest {
    static void verify(ServerLevel level) {
        run(level,true,true);
        run(level,false,true);
        run(level,true,false);
        run(level,false,false);
    }
    private static void run(ServerLevel level,boolean forced,boolean resumed) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        ServerPlayer observer=null;
        String job=null;
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            var v=state.village(UUID.fromString("785a5083-1196-329d-b859-ccee143cd16a"));
            v.architectureCharacter="rustic";v.architectureDialect="plains";v.developmentTier=3;
            v.dimensionKey="minecraft:overworld";v.population=v.observedPopulation=20;v.housingCapacity=40;
            v.foodSupply=v.materialSupply=2000;v.treasury=1000;v.prosperity=v.safety=100;
            var p=new EconomyState.VillageProject();
            p.projectId=7;p.type=VillageProsperityEngine.ProjectType.SMITHY;
            p.designSchema="blueprint_v2";p.designTemplateId="smithy_corner_04";p.designTemplateRevision=10;
            p.designPaletteId="timber_forward";p.designDressingId="prosperous";p.designSeed=732078529326529451L;
            p.designStage=1;p.designRotation=1;p.designSignature=4649262346917402276L;
            p.originPos=-87411173417020L;p.materializedBlocks=resumed?1180:0;p.totalBlocks=1975;
            p.constructionOrderCuts.addAll(List.of(0,1975));
            var origin=new BlockPos(1728,level.getMaxY()-40,1728);v.centerPos=origin.asLong();
            var canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);
            var ordered=(List<?>)invoke("constructionTemplate",canonical,p);
            require(ordered.size()==1975,"exact reported Smithy operation count");
            require(target(origin,ordered.get(1180)).equals(origin.offset(10,0,-4))
                    &&block(ordered.get(1180)).is(Blocks.DIRT_PATH),"exact reported path repair cell");
            for(Object c:ordered){
                var pos=target(origin,c);
                for(int y=-5;y<0;y++) set(level,before,new BlockPos(pos.getX(),origin.getY()+y,pos.getZ()),Blocks.DIRT.defaultBlockState());
                set(level,before,pos,Blocks.AIR.defaultBlockState());
            }
            p.originPos=origin.asLong();p.economicComplete=true;p.economicProgress=1;
            p.sitePreparationComplete=true;p.constructionStarted=true;
            p.entranceApproachComplete=true;p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            p.trailAnchorSet=true;p.trailAnchorPos=origin.west(25).asLong();
            p.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,v,p)).size();
            p.trailMaterializedBlocks=p.trailTotalBlocks;p.trailMaterializedComplete=true;
            Object bounds=invoke("bounds",origin,canonical);
            p.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();
            p.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            p.designPlanHash=(String)invoke("blueprintPlanHash",v,p,invoke("blueprintPlacementPlan",level,origin,v,p));
            p.designPlanHashVersion=1;v.projects.add(p);v.projectSerial=7;
            var dir=Files.createTempDirectory("tes-smithy-repair-");
            state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService();economy.configureEconomicClock(false,30);
            economy.configureVillageProsperity(true,true);economy.start(dir,1234,0);
            economy.configureForcedVillageDevelopment(forced);
            var ownership=ConstructionOwnership.get(level);
            job=ConstructionOwnership.project(v.villageId,p.projectId,p.originPos);
            ownership.begin(economy,job,v.villageId,p.projectId,p.originPos,false);
            if(resumed) {
                // The capture's cursor was rewound after completing later work. Keep those saved
                // supply receipts: omitting them would hide the broken finish-pass behavior.
                for(Object c:ordered){
                    var pos=target(origin,c);var block=block(c);set(level,before,pos,block);
                    if(!block.isAir()) ownership.claim(job,pos,block,block.hasBlockEntity());
                }
                settlePaths(level,origin,ordered);
                var foot=origin.offset(10,0,-4);
                require(level.getBlockState(foot).is(Blocks.DIRT),
                        "vanilla changes the reported covered path into dirt");
                require(ConstructionOwnership.settledPathGround(level,foot,Blocks.DIRT.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState())
                        &&Block.getDrops(level.getBlockState(foot),level,foot,null).isEmpty(),
                        "supplied settled ground satisfies its receipt without renewable dirt drops");
                require(!ConstructionOwnership.settledPathGround(level,foot,Blocks.AIR.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState())
                        &&!ConstructionOwnership.settledPathGround(level,foot,Blocks.CHEST.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState())
                        &&!ConstructionOwnership.settledPathGround(level,foot,Blocks.STONE.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState())
                        &&!ConstructionOwnership.settledPathGround(level,foot,Blocks.DIRT.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState())
                        &&!ConstructionOwnership.settledPathGround(level,origin.west(40),Blocks.DIRT.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState()),
                        "missing blocks, foreign blocks, required masonry and unowned terrain are not path settlement");
                var encoded=ConstructionOwnership.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,ownership).getOrThrow();
                var decoded=ConstructionOwnership.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();
                require(decoded.sites.get(job).equals(ownership.sites.get(job)),"original path receipt survives reload");
                var path=(BlockState)field(ordered.get(1180),"state");
                require((boolean)invoke("placementSatisfied",level,origin,foot,level.getBlockState(foot),ordered.get(1180)),
                        "optional yard ground accepts vanilla settlement");
                // The same block in a required floor must not gain this cosmetic waiver.
                Object role=field(ordered.get(1180),"role");
                Object structure=Arrays.stream(role.getClass().getEnumConstants()).filter(r->r.toString().equals("STRUCTURE")).findFirst().orElseThrow();
                var ctor=ordered.get(1180).getClass().getDeclaredConstructor(int.class,int.class,int.class,
                        BlockState.class,role.getClass(),boolean.class,int.class);
                ctor.setAccessible(true);
                Object required=ctor.newInstance(10,0,-4,path,structure,false,0);
                require(!(boolean)invoke("placementSatisfied",level,origin,foot,level.getBlockState(foot),required),
                        "required path floors retain their exact physical inspection");
            }
            VillageConstructionActivitySelfTest.prepareFences(level,economy,
                    VillageConstructionActivity.projectTag(v.villageId,p.projectId,p.originPos));
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"SmithyFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+60,origin.getY(),origin.getZ());level.players().add(observer);
            var props=new Properties();props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,Boolean.toString(forced));
            props.setProperty("village_prosperity.construction_blocks_per_second",forced?"100":"2");
            var config=EmeraldConfig.parse(props);
            var type=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget=type.getDeclaredConstructor(int.class,int.class);budget.setAccessible(true);
            for(int pulse=1;pulse<=3000;pulse++){
                ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),
                        economy.developmentVillageSnapshot(v.villageId));
                settlePaths(level,origin,ordered);
                var current=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                if(current.materializedComplete)break;
                if(pulse==3000)throw new IllegalStateException("Smithy stalled: forced="+forced+" resumed="+resumed+" "
                        +current.materializedBlocks+"/"+current.totalBlocks+" "+ConstructionDiagnostics.recent(v.villageId+"/7",pulse*20L));
            }
            require(level.getBlockState(origin.offset(10,0,-4)).is(Blocks.DIRT),"settled ground is kept");
            require(level.getBlockState(origin.offset(10,1,-4)).is(Blocks.IRON_BARS),"ironwork is kept");
            var restarted=new EconomyService();restarted.configureEconomicClock(false,30);
            restarted.configureVillageProsperity(true,true);restarted.start(dir,1234,0);
            require(restarted.developmentVillageSnapshot(v.villageId).village().projects.getFirst().materializedComplete,"handover saved across restart");
            System.out.println("PASS exact Smithy 1180/1975: forced="+forced+" resumed="+resumed+" completes with vanilla path settling");
        }catch(Exception e){throw new IllegalStateException("Smithy construction fixture",e);}
        finally{
            if(observer!=null){level.players().remove(observer);observer.discard();}
            if(job!=null)ConstructionOwnership.get(level).finish(job);
            VillageConstructionActivitySelfTest.cleanupCrews(level);
            before.forEach((p,s)->level.setBlock(p,s,18));ConstructionDiagnostics.reset();
        }
    }
    private static void settlePaths(ServerLevel level,BlockPos origin,List<?> ordered)throws Exception{
        for(Object c:ordered)if(block(c).is(Blocks.DIRT_PATH)){
            var pos=target(origin,c);var actual=level.getBlockState(pos);
            if(actual.is(Blocks.DIRT_PATH)&&!actual.canSurvive(level,pos))actual.tick(level,pos,level.getRandom());
        }
    }
    private static BlockPos target(BlockPos origin,Object c)throws Exception{
        return origin.offset((int)field(c,"dx"),(int)field(c,"dy"),(int)field(c,"dz"));
    }
    private static BlockState block(Object c)throws Exception{return (BlockState)field(c,"state");}
    private static Object field(Object c,String name)throws Exception{return ConstructionSupportRecoverySelfTest.field(c,name);}
    private static Object invoke(String name,Object...args)throws Exception{return ConstructionSupportRecoverySelfTest.invoke(name,args);}
    private static Object method(Object c,String name)throws Exception{var m=c.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(c);}
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos p,BlockState s){
        level.getChunk(p);before.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,s,18);
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
