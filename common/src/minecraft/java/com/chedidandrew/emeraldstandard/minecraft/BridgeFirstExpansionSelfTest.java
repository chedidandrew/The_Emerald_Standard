package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** A large loaded river, read-only route preflight, frozen dependency and protected handover. */
final class BridgeFirstExpansionSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin=new BlockPos(4600,level.getMaxY()-48,4600);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        var old=VillageBridgeLedger.CODEC.encodeStart(NbtOps.INSTANCE,VillageBridgeLedger.get(level)).getOrThrow();
        var oldRoad=WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE,WalkwayConnectionLedger.get(level)).getOrThrow();
        try {
            var dir=Files.createTempDirectory("tes-bridge-first-");
            var economy=new EconomyService();economy.start(dir,9965,0);UUID owner=new UUID(9965,1);
            economy.observeNaturalVillage(owner,new EconomyService.VillageObservation("minecraft:overworld",origin.asLong(),0,0,8,12,0,false,List.of()));
            economy.configureForcedVillageDevelopment(true);
            var saved=EconomyState.fresh(9965,System.currentTimeMillis(),0);
            var village=economy.developmentVillageSnapshot(owner).village();
            var destination=new EconomyState.VillageProject();destination.projectId=65;
            destination.type=VillageProsperityEngine.ProjectType.COTTAGE;
            destination.economicComplete=true;destination.economicProgress=1;
            village.projects.add(destination);village.projectSerial=65;saved.villages.put(owner,village);
            saved.save(dir.resolve("the_emerald_standard.properties"));
            economy=new EconomyService();economy.start(dir,9965,0);economy.configureForcedVillageDevelopment(true);
            level.getDataStorage().set(VillageBridgeLedger.TYPE,new VillageBridgeLedger());
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,new WalkwayConnectionLedger());
            for(int x=-8;x<=155;x++)for(int z=-30;z<=30;z++)for(int y=-4;y<=9;y++) {
                BlockPos at=origin.offset(x,y,z);level.getChunk(at);before.put(at,level.getBlockState(at));
                boolean water=x>=6&&x<134&&y>=-2&&y<=0;
                level.setBlock(at,(water?Blocks.WATER:y<0?Blocks.STONE:y==0?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),18);
            }
            var request=new WalkwayConnections.Request(owner,65,origin.east(147).asLong(),origin,origin.east(147),false,Set.of(),List.of(),List.of(),false);
            var materials=AuthoredVillageStructures.walkwayMaterials(VillageArchitecture.Character.RUSTIC,VillageArchitecture.BiomeDialect.PLAINS);
            var context=new VillageBridges.Context(economy,owner,materials,false,128,12,2);
            require(BridgeFirstExpansion.needsSurvey(level,request),"Large river did not trigger bridge-first survey");
            var shortRequest=new WalkwayConnections.Request(owner,66,origin.east(18).asLong(),origin,origin.east(18),false,Set.of(),List.of(),List.of(),false);
            require(BridgeFirstExpansion.needsSurvey(level,shortRequest),"short crossing bypassed bridge-first inspection");
            WalkwayConnections.Preview preview=null;
            for(int n=0;n<6000;n++) {
                preview=WalkwayConnections.preview(level,request,level.getGameTime()+n,context);
                if(preview.ready()||preview.failed())break;
            }
            require(preview!=null&&preview.ready()&&preview.bridges().size()==1,"Large crossing route not found: "+preview);
            var plan=preview.bridges().getFirst();
            require(plan.waterLength()==128,"Large water length changed");
            var changedRequest=new WalkwayConnections.Request(owner,65,request.origin(),request.start(),origin.east(5),false,Set.of(),List.of(),List.of(),false);
            var changedPreview=WalkwayConnections.preview(level,changedRequest,level.getGameTime()+7000,context);
            require(!changedPreview.ready()||changedPreview.bridges().isEmpty(),"changed destination reused stale crossing approval");
            WalkwayConnections.forgetPreview(level,changedRequest);
            for(int n=0;n<8;n++)require(BridgeFirstExpansion.remember(level,"slot-fixture/"+n,plan,destination,"plains"),"Available destination slot rejected");
            require(!BridgeFirstExpansion.remember(level,"slot-fixture/overflow",plan,destination,"plains"),"Destination slot cap bypassed");
            for(int n=0;n<8;n++)BridgeFirstExpansion.forget(level,"slot-fixture/"+n,request);
            require(VillageBridgeLedger.get(level).jobs.isEmpty()&&economy.developmentVillageSnapshot(owner).village().bridgeFundingReceipts.isEmpty(),
                    "Survey reserved/funded work before destination acceptance");
            require(level.getBlockState(origin.east(16)).is(Blocks.WATER)&&level.getBlockState(origin.east(16).above(3)).isAir(),"Preview wrote bridge blocks");
            require(BridgeFirstExpansion.reserve(level,context,preview),"Checkpointed crossing reservation failed");
            var p=new EconomyState.VillageProject();p.projectId=65;p.bridgeFirstDependencies=BridgeFirstExpansion.dependencies(preview);
            require(!BridgeFirstExpansion.open(level,p),"Unbuilt crossing opened destination");
            // An unspent provisional plan can be rolled back without a debit or block change.
            BridgeFirstExpansion.discardUnfunded(level,owner,65);
            require(VillageBridgeLedger.get(level).jobs.isEmpty(),"Orphan reservation survived rollback");
            require(BridgeFirstExpansion.reserve(level,context,preview),"Crossing could not be re-reserved");
            // Crash window: another ordinary connector cannot fund this provisional crossing.
            var provisional=VillageBridgeLedger.CODEC.encodeStart(NbtOps.INSTANCE,VillageBridgeLedger.get(level)).getOrThrow();
            level.getDataStorage().set(VillageBridgeLedger.TYPE,VillageBridgeLedger.CODEC.parse(NbtOps.INSTANCE,provisional).getOrThrow());
            for(int n=0;n<30;n++) require(VillageBridges.advance(level,request,context,plan.id(),32)==0,"Orphan bridge wrote blocks");
            require(economy.developmentVillageSnapshot(owner).village().bridgeFundingReceipts.isEmpty()
                    &&VillageBridgeLedger.get(level).jobs.get(plan.id()).state().equals("destination_pending"),"Orphan bridge spent before destination save");
            BlockPos lot=origin.east(147).above();
            require(economy.reserveVillageProjectSite(owner,65,lot.asLong(),lot.asLong(),lot.offset(4,4,4).asLong(),100,
                    "",0,0,0,0,0,0,"",null,0,p.bridgeFirstDependencies,Map.of()),"Destination/dependency atomic reservation failed");
            var restarted=new EconomyService();restarted.start(dir,9965,0);restarted.configureForcedVillageDevelopment(true);
            require(restarted.developmentVillageSnapshot(owner).village().projects.stream().anyMatch(project ->
                    project.projectId==65&&project.bridgeFirstDependencies.equals(p.bridgeFirstDependencies)),"Destination dependency lost on restart");
            economy=restarted;context=new VillageBridges.Context(economy,owner,materials,false,128,12,2);
            boolean reloaded=false;
            for(int n=0;n<6000&&!BridgeFirstExpansion.open(level,p);n++) {
                int writes=BridgeFirstExpansion.advance(level,p,request,context,32,true);
                require(writes<=32,"Forced bridge exceeded allowance");
                var job=VillageBridgeLedger.get(level).jobs.get(plan.id());
                require(!job.altered(),"Crossing incorrectly changed: "+job.reason());
                if(!reloaded&&job.cursor()>64) {
                    var encoded=VillageBridgeLedger.CODEC.encodeStart(NbtOps.INSTANCE,VillageBridgeLedger.get(level)).getOrThrow();
                    level.getDataStorage().set(VillageBridgeLedger.TYPE,VillageBridgeLedger.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow());
                    reloaded=true;
                }
            }
            require(reloaded&&BridgeFirstExpansion.open(level,p),"Large crossing did not verify and release destination");
            require(economy.developmentVillageSnapshot(owner).village().bridgeFundingReceipts.size()==1,"Duplicate bridge payment receipt");
            BlockPos removed=BlockPos.of(plan.route().get(60));level.setBlock(removed,Blocks.AIR.defaultBlockState(),18);
            require(!BridgeFirstExpansion.open(level,p),"Changed deck still opened unbuilt destination");
            require(BridgeFirstExpansion.advance(level,p,request,context,32,true)==0&&level.getBlockState(removed).isAir(),"Completed bridge regenerated harvested blocks");
            WalkwayConnections.forgetPreview(level,request);
            System.out.println("PASS bridge-first: 128-block river, read-only viable route, no premature spend/write, rollback, frozen reload, shared allowance, one receipt, verified destination gate and edits");
        } catch(Exception failure) {throw new IllegalStateException("Bridge-first fixture",failure);}
        finally {
            level.getDataStorage().set(VillageBridgeLedger.TYPE,VillageBridgeLedger.CODEC.parse(NbtOps.INSTANCE,old).getOrThrow());
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,oldRoad).getOrThrow());
            before.forEach((at,state)->level.setBlock(at,state,18));
        }
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
