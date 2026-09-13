package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Real loaded-block routing/persistence tests, invoked only in a disposable server smoke world. */
final class WalkwayConnectionsSelfTest {
    static void verify(ServerLevel level) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        var old=WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE,WalkwayConnectionLedger.get(level)).getOrThrow();
        BlockPos origin=new BlockPos(1760,level.getMaxY()-24,1760);
        UUID village=UUID.fromString("eceb55c3-af62-45fe-a30a-2fe9b07ae3a6");
        Entity animal=null;
        try {
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,new WalkwayConnectionLedger());
            for(int x=-2;x<=50;x++)for(int z=-14;z<=14;z++)for(int y=-1;y<=4;y++) {
                BlockPos p=origin.offset(x,y,z);level.getChunk(p);before.put(p,level.getBlockState(p));
                level.setBlock(p,(y<0?Blocks.STONE:y==0?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),18);
            }
            Set<Long> legacy=new HashSet<>();
            for(int x=0;x<=25;x++) {
                BlockPos p=origin.east(x);legacy.add(WalkwayConnections.column(p));
                level.setBlock(p,Blocks.DIRT_PATH.defaultBlockState(),3);
            }
            for(int z=-6;z<=6;z++)level.setBlock(origin.offset(44,0,z),Blocks.DIRT_PATH.defaultBlockState(),3);
            for(int z=-3;z<=3;z++)for(int y=1;y<=3;y++)
                level.setBlock(origin.offset(15,y,z),Blocks.BIRCH_LOG.defaultBlockState(),3);
            BlockPos chestPos=origin.offset(16,1,4);
            level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
            var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);chest.setItem(0,new ItemStack(Items.DIAMOND,3));
            for(int z=-2;z<=2;z++)level.setBlock(origin.offset(30,0,z),Blocks.WATER.defaultBlockState(),3);
            var request=new WalkwayConnections.Request(village,1,origin.asLong(),origin,origin.east(44),
                    true,legacy,List.of(),List.of(),false);
            require(WalkwayConnections.acquire(level,1000)&&!WalkwayConnections.acquire(level,1019)
                    &&WalkwayConnections.acquire(level,1020),"dimension work gate");
            BlockPos changed=null;
            boolean reload=false;
            int writes=0;
            try(var claim=VillageDevelopmentProtection.register(c ->
                    !(c.position().getX()==origin.getX()+25&&Math.abs(c.position().getZ()-origin.getZ())<=2))) {
                for(int i=0;i<1600&&!WalkwayConnectionLedger.get(level).job(request.key()).done();i++) {
                    var j=WalkwayConnectionLedger.get(level).job(request.key());
                    if(changed==null&&!j.plan().isEmpty()&&j.cursor()>=2&&j.cursor()<j.centers()) {
                        changed=BlockPos.of(j.plan().get(j.cursor()).pos()).above();
                        level.setBlock(changed,Blocks.OAK_PLANKS.defaultBlockState(),3);
                        level.setBlock(changed.above(),Blocks.OAK_PLANKS.defaultBlockState(),3);
                    }
                    int n=WalkwayConnections.advance(level,request,2000+i*20L,2);
                    require(n>=0&&n<=2,"paving exceeded two-write budget");writes+=n;
                    j=WalkwayConnectionLedger.get(level).job(request.key());
                    if(!reload&&j.cursor()>3&&!j.plan().isEmpty()) {
                        var decoded=WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,
                                WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE,WalkwayConnectionLedger.get(level)).getOrThrow()).getOrThrow();
                        require(decoded.job(request.key()).equals(j),"partial connection did not round-trip");
                        level.getDataStorage().set(WalkwayConnectionLedger.TYPE,decoded);reload=true;
                    }
                }
            }
            var ledger=WalkwayConnectionLedger.get(level);
            require(ledger.job(request.key()).done(),"route stalled: "+ledger.job(request.key()).reason());
            List<BlockPos> route=ledger.route(request.key());
            require(reload&&changed!=null&&writes>0,"did not exercise saved partial paving/replanning");
            require(route.getFirst().equals(origin)&&route.getLast().getX()==origin.getX()+44,
                    "connected to own stub instead of the real village road: "+route);
            for(int i=0;i<route.size();i++) {
                BlockPos p=route.get(i);
                require(WalkwayConnections.road(level.getBlockState(p))&&level.getBlockState(p.above()).isAir()
                        &&level.getBlockState(p.above(2)).isAir(),"gap or blocked headroom");
                if(i>0) { BlockPos a=route.get(i-1);
                    require(Math.abs(p.getX()-a.getX())+Math.abs(p.getZ()-a.getZ())==1
                            &&Math.abs(p.getY()-a.getY())<=1,"non-walkable route segment");
                }
            }
            require(level.getBlockState(changed).is(Blocks.OAK_PLANKS)&&chest.getItem(0).getCount()==3
                    &&level.getBlockState(origin.offset(15,1,0)).is(Blocks.BIRCH_LOG)
                    &&level.getBlockState(origin.offset(30,0,0)).is(Blocks.WATER),"obstacle/property overwritten");
            BlockPos supplied=BlockPos.of(ledger.job(request.key()).supplied().iterator().next());
            level.setBlock(supplied,Blocks.AIR.defaultBlockState(),3);
            var decoded=WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,
                    WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE,ledger).getOrThrow()).getOrThrow();
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,decoded);
            require(WalkwayConnections.advance(level,request,60000,2)==0&&level.getBlockState(supplied).isAir(),
                    "completed path regenerated after reload");
            // Routing never treats arbitrary flat land as a successful destination.
            var noRoad=new WalkwayConnections.Request(village,2,origin.asLong(),origin.offset(0,0,10),
                    origin.offset(44,0,10),true,legacy,List.of(),List.of(),false);
            try(var claim=VillageDevelopmentProtection.register(c -> c.position().getX()<origin.getX()+10)) {
                for(int i=0;i<300;i++)WalkwayConnections.advance(level,noRoad,70000+i*20L,2);
            }
            require(!decoded.job(noRoad.key()).done()&&decoded.job(noRoad.key()).plan().isEmpty(),
                    "unreachable destination was marked connected");
            BlockPos foot=origin.offset(3,0,-10);
            var direct=new WalkwayConnections.Request(village,3,origin.asLong(),foot,foot.east(8),
                    false,Set.of(),List.of(),List.of(),true);
            BlockPos p=foot.east(2);
            level.setBlock(p.above(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            require(p.above().equals(WalkwayConnections.surface(level,direct,p,WalkwayConnectionLedger.Job.fresh())),
                    "one-block grade was rejected");
            level.setBlock(p.above(2),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            require(WalkwayConnections.surface(level,direct,p,WalkwayConnectionLedger.Job.fresh())==null,
                    "two-block cliff was accepted");
            level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(p.above(2),Blocks.AIR.defaultBlockState(),3);
            var lot=new EconomyService.VillageProjectLot(foot.offset(-1,0,-1).asLong(),foot.offset(1,4,1).asLong());
            var protectedRoute=new WalkwayConnections.Request(village,4,origin.asLong(),foot,foot.east(8),
                    false,Set.of(),List.of(lot),List.of(),false);
            require(WalkwayConnections.surface(level,protectedRoute,foot,WalkwayConnectionLedger.Job.fresh())==null,
                    "new paving intruded into a reserved lot");
            level.setBlock(foot,Blocks.DIRT_PATH.defaultBlockState(),3);
            require(foot.equals(WalkwayConnections.surface(level,protectedRoute,foot,WalkwayConnectionLedger.Job.fresh())),
                    "could not walk over existing unchanged entrance paving in its own lot");
            for(int i=0;i<100&&decoded.job(direct.key()).plan().isEmpty();i++)
                WalkwayConnections.advance(level,direct,80000+i*20L,2);
            var plan=decoded.job(direct.key());
            require(!plan.plan().isEmpty(),"direct destination not surveyed");
            BlockPos next=BlockPos.of(plan.plan().get(1).pos());
            animal=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
            // Slightly intersect the work cell: a same-height material swap underneath an entity
            // is intentionally safe, whereas building into its body must always wait.
            require(animal!=null,"animal fixture");animal.setPos(next.getX()+.5,next.getY()+.95,next.getZ()+.5);level.addFreshEntity(animal);
            WalkwayConnections.advance(level,direct,84000,2);
            require(!decoded.job(direct.key()).done()&&!WalkwayConnections.road(level.getBlockState(next)),
                    "occupied footing changed");
            animal.discard();animal=null;
            for(int i=0;i<100&&!decoded.job(direct.key()).done();i++)WalkwayConnections.advance(level,direct,85000+i*20L,2);
            require(decoded.job(direct.key()).done(),"occupancy did not resume");
            BlockPos unloaded=new BlockPos(8000000,foot.getY(),8000000);
            var remote=new WalkwayConnections.Request(village,5,origin.asLong(),unloaded,unloaded.east(20),
                    false,Set.of(),List.of(),List.of(),false);
            require(!level.hasChunk(unloaded.getX()>>4,unloaded.getZ()>>4),"unloaded fixture");
            WalkwayConnections.advance(level,remote,90000,2);
            require(!decoded.job(remote.key()).done()&&!level.hasChunk(unloaded.getX()>>4,unloaded.getZ()>>4),
                    "connection forced a chunk load");
            // A finished entrance can be stone stairs, not just dirt-path terrain.
            BlockPos stair=origin.offset(0,0,-13);
            level.setBlock(stair,Blocks.STONE_BRICK_STAIRS.defaultBlockState(),3);
            var entrance=new WalkwayConnections.Request(village,6,origin.asLong(),stair,stair.east(6),
                    false,Set.of(),List.of(),List.of(),false);
            require(stair.equals(WalkwayConnections.surface(level,entrance,stair,WalkwayConnectionLedger.Job.fresh())),
                    "authored entrance stairs rejected");
            for(int i=0;i<150&&!decoded.job(entrance.key()).done();i++)WalkwayConnections.advance(level,entrance,91000+i*20L,2);
            require(decoded.job(entrance.key()).done()&&level.getBlockState(stair).is(Blocks.STONE_BRICK_STAIRS),
                    "entrance steps were not preserved by connection pass");
            managerEndpoints(origin);
            System.out.println("PASS walkway connections: truncated-road detour, real endpoint, trees, water, claims, chest, edits,"
                    +" grades, lots, occupancy, budgets, partial reload, no regeneration, unloaded chunks and manager endpoints");
            chest.clearContent();
        } catch(Exception ex) { throw new IllegalStateException("Walkway connection regression",ex); }
        finally {
            if(animal!=null)animal.discard();
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,old).getOrThrow());
            before.forEach((p,s)->level.setBlock(p,s,18));
        }
    }
    private static void managerEndpoints(BlockPos origin) {
        var village=new EconomyState.VillageRecord();village.villageId=UUID.randomUUID();village.centerPos=origin.east(60).asLong();
        village.architectureCharacter=VillageArchitecture.Character.RUSTIC.id();
        village.architectureDialect=VillageArchitecture.BiomeDialect.PLAINS.id();
        var a=new EconomyState.VillageProject();a.projectId=1;a.originPos=origin.asLong();
        a.type=VillageProsperityEngine.ProjectType.HOUSE;a.designSchema=VillageArchitecture.MODULAR_SCHEMA;
        a.economicComplete=a.materializedComplete=a.trailMaterializedComplete=a.trailAnchorSet=true;
        a.trailAnchorPos=origin.east(14).asLong();village.projects.add(a);
        var request=VillageProsperityManager.walkwayRequest(village,a,List.of(),List.of());
        require(request.streetGoal()&&request.destination().equals(BlockPos.of(village.centerPos).below()),
                "first project still uses arbitrary outskirts anchor");
        var b=a.copy();b.projectId=2;b.originPos=origin.east(35).asLong();village.projects.add(b);
        var branch=VillageProsperityManager.walkwayRequest(village,b,List.of(),List.of());
        require(!branch.streetGoal()&&branch.destination().equals(request.start()),"later house lacks entrance connection");
        b.trailMaterializedComplete=false;b.trailAnchorPos=BlockPos.of(b.originPos).north(800).asLong();
        require(VillageProsperityManager.walkwayReady(village,b),"empty overlong legacy trail stranded the connectivity pass");
    }
    private static void require(boolean value,String reason) {if(!value)throw new IllegalStateException(reason);}
}
