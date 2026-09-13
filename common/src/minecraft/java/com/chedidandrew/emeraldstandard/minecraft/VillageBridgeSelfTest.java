package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static com.chedidandrew.emeraldstandard.minecraft.VillageBridgeLedger.*;

/** Disposable native river fixtures. No user world is touched by these opt-in checks. */
final class VillageBridgeSelfTest {
    static void verify(ServerLevel level) {
        try { verifyChecked(level); } catch(Exception e) {throw new IllegalStateException("Bridge fixture",e);}
    }
    private static void verifyChecked(ServerLevel level) throws Exception {
        var defaults=EmeraldConfig.parse(new Properties()).bridgeSettings();
        require(defaults.enabled()&&defaults.length()==48&&defaults.depth()==12&&defaults.concurrent()==2,"bridge defaults");
        for(String key:List.of(EmeraldConfig.BRIDGE_LENGTH,EmeraldConfig.BRIDGE_DEPTH,EmeraldConfig.BRIDGE_JOBS)) {
            Properties invalid=new Properties();invalid.setProperty(key,"999");
            try {EmeraldConfig.parse(invalid);throw new IllegalStateException("unbounded bridge setting "+key);}
            catch(java.io.IOException expected) {}
        }
        BlockPos origin=new BlockPos(3600,level.getMaxY()-42,3600);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        var old=VillageBridgeLedger.CODEC.encodeStart(NbtOps.INSTANCE,get(level)).getOrThrow();
        var oldRoad=WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE,WalkwayConnectionLedger.get(level)).getOrThrow();
        var dir=Files.createTempDirectory("tes-bridge-fixture-");
        EconomyService economy=new EconomyService();economy.start(dir,559,0);
        UUID owner=new UUID(559,37);
        economy.observeNaturalVillage(owner,new EconomyService.VillageObservation("minecraft:overworld",
                origin.asLong(),0,0,8,12,0,false,List.of()));
        economy.configureForcedVillageDevelopment(true);
        Entity occupant=null;
        try {
            for(int x=-10;x<=88;x++)for(int z=-10;z<=88;z++)for(int y=-4;y<=11;y++) {
                BlockPos p=origin.offset(x,y,z);level.getChunk(p);before.put(p,level.getBlockState(p));
                level.setBlock(p,(y<0?Blocks.STONE:y==0?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),18);
            }
            var materials=AuthoredVillageStructures.walkwayMaterials(VillageArchitecture.Character.RUSTIC,
                    VillageArchitecture.BiomeDialect.PLAINS);
            var context=new VillageBridges.Context(economy,owner,materials,false,48,12,2);
            var request=new WalkwayConnections.Request(owner,700,origin.asLong(),origin,origin.east(35),
                    false,Set.of(),List.of(),List.of(),false);
            int designs=0;
            for(Direction d:Direction.Plane.HORIZONTAL)for(int length:new int[]{2,3,7,8,13,24,47,48}) {
                reset(level,origin,before);
                level.getDataStorage().set(TYPE,new VillageBridgeLedger());
                BlockPos anchor=d==Direction.WEST?origin.east(60):d==Direction.NORTH?origin.south(60):origin;
                river(level,anchor,d,length,0);
                Plan plan=survey(level,request,context,anchor,d);
                require(plan!=null,"missing "+d+" bridge length "+length);
                require(plan.waterLength()==length&&plan.route().size()==length+12,"length-adjustable end sections");
                geometry(plan,d);
                require(plan.equals(Plan.CODEC.parse(NbtOps.INSTANCE,Plan.CODEC.encodeStart(NbtOps.INSTANCE,plan).getOrThrow()).getOrThrow()),
                        "frozen plan round trip");
                designs++;
            }
            for(boolean desert:new boolean[]{false,true}) {
                reset(level,origin,before);level.getDataStorage().set(TYPE,new VillageBridgeLedger());
                river(level,origin,Direction.EAST,13,1);
                var stone=new VillageBridges.Context(economy,owner,AuthoredVillageStructures.walkwayMaterials(
                        VillageArchitecture.Character.RUSTIC,desert?VillageArchitecture.BiomeDialect.DESERT:
                                VillageArchitecture.BiomeDialect.PLAINS),true,48,12,2);
                var r=new WalkwayConnections.Request(owner,701,origin.asLong(),origin,origin.east(24).above(),
                        false,Set.of(),List.of(),List.of(),desert);
                Plan plan=survey(level,r,stone,origin,Direction.EAST);
                require(plan!=null&&plan.style().contains(desert?"sandstone":"stone"),"styled unequal-bank crossing");
                geometry(plan,Direction.EAST);designs++;
            }
            reset(level,origin,before);level.getDataStorage().set(TYPE,new VillageBridgeLedger());
            river(level,origin,Direction.EAST,24,0);
            Plan plan=survey(level,request,context,origin,Direction.EAST);
            require(plan!=null&&VillageBridges.reserve(level,context,plan),"reserve complete footprint");
            require(!VillageDevelopmentProtection.mayPlace(level,owner,999,BlockPos.of(plan.route().get(10)),
                    Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState()),"foreign project entered reservation");
            // Reach building, then pause without changing the plan or charging twice.
            for(int n=0;n<200&&!get(level).jobs.get(plan.id()).funded();n++)
                VillageBridges.advance(level,request,context,plan.id(),2);
            require(get(level).jobs.get(plan.id()).funded(),"funding receipt");
            BlockPos first=BlockPos.of(plan.pieces().getFirst().pos());
            occupant=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
            require(occupant!=null,"bridge occupant fixture");
            occupant.setPos(first.getX()+.5,first.getY()+.1,first.getZ()+.5);level.addFreshEntity(occupant);
            require(VillageBridges.advance(level,request,context,plan.id(),2)==0
                    &&get(level).jobs.get(plan.id()).cursor()==0,"occupied bridge foundation overwritten");
            occupant.discard();occupant=null;
            int cursor=get(level).jobs.get(plan.id()).cursor();
            require(VillageBridges.advance(level,request,null,plan.id(),16)==0
                    &&get(level).jobs.get(plan.id()).cursor()==cursor,"disabled bridges must pause");
            economy.configureForcedVillageDevelopment(false);
            boolean reloaded=false;
            for(int n=0;n<2500&&!get(level).jobs.get(plan.id()).done();n++) {
                int writes=VillageBridges.advance(level,request,context,plan.id(),2);
                require(writes<=2,"construction write cap");
                Job j=get(level).jobs.get(plan.id());
                require(!j.altered(),"unexpected bridge alteration: "+j.reason());
                if(!reloaded&&j.cursor()>30) {
                    level.getDataStorage().set(TYPE,CODEC.parse(NbtOps.INSTANCE,CODEC.encodeStart(NbtOps.INSTANCE,get(level)).getOrThrow()).getOrThrow());
                    reloaded=true;
                }
            }
            Job completed=get(level).jobs.get(plan.id());
            require(completed.done()&&reloaded,"bridge never completed: "+completed.cursor()+" / "+completed.reason());
            require(economy.developmentVillageSnapshot(owner).village().bridgeFundingReceipts.size()==1,"duplicate Infrastructure charge");
            for(long packed:plan.route())require(VillageBridges.walkable(level,owner,BlockPos.of(packed)),"broken deck passage");
            var walker=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
            require(walker!=null,"villager navigation fixture");
            BlockPos feet=BlockPos.of(plan.start()).above();
            walker.setPos(feet.getX()+.5,feet.getY(),feet.getZ()+.5);walker.setOnGround(true);
            var path=walker.getNavigation().createPath(BlockPos.of(plan.end()).above(),0);
            require(path!=null&&path.canReach(),"villager cannot navigate bridge approaches");
            BlockPos midpoint=BlockPos.of(plan.route().get(plan.route().size()/2)).above();
            boolean crossesDeck=false;
            for(int i=0;i<path.getNodeCount();i++) {
                BlockPos node=path.getNode(i).asBlockPos();
                if(Math.abs(node.getX()-midpoint.getX())<=1&&Math.abs(node.getZ()-midpoint.getZ())<=1
                        &&node.getY()==midpoint.getY())crossesDeck=true;
            }
            require(crossesDeck,"villager path detoured instead of crossing the deck");
            VillageWalkingSelfTest.walk(level,walker,BlockPos.of(plan.end()).above(),"footbridge stairs and raised deck");
            walker.discard();
            BlockPos harvested=BlockPos.of(plan.route().get(14));
            level.setBlock(harvested,Blocks.AIR.defaultBlockState(),18);
            VillageBridges.advance(level,request,context,plan.id(),16);
            require(level.getBlockState(harvested).isAir(),"completed bridge regenerated player edit");
            // New roads may reuse an intact saved crossing, but never treat an altered deck as usable.
            require(!VillageBridges.walkable(level,owner,harvested),"damaged crossing treated as walkable");
            reset(level,origin,before);level.getDataStorage().set(TYPE,new VillageBridgeLedger());
            river(level,origin,Direction.EAST,24,0);
            try(var guard=VillageDevelopmentProtection.register(c -> !c.position().equals(origin.east(35)))) {
                require(survey(level,request,context,origin,Direction.EAST)==null,"protected far shore admitted");
            }
            level.setBlock(origin.east(10),Blocks.ICE.defaultBlockState(),18);
            require(survey(level,request,context,origin,Direction.EAST)==null,"ice accepted as water");
            level.setBlock(origin.east(10),Blocks.WATER.defaultBlockState(),18);
            level.setBlock(origin.east(12).below(),Blocks.CHEST.defaultBlockState(),18);
            // Center-channel underwater storage is outside the pier writes; it must stay untouched.
            Plan safe=survey(level,request,context,origin,Direction.EAST);
            require(safe!=null&&safe.pieces().stream().noneMatch(p->p.pos()==origin.east(12).below().asLong()),
                    "underwater channel structure altered");
            BlockPos remote=new BlockPos(9000000,origin.getY(),9000000);
            require(!VillageBridgeSurvey.possible(level,remote,Direction.EAST)
                    &&!level.hasChunk(remote.getX()>>4,remote.getZ()>>4),"bridge survey loaded remote chunks");
            edgeCases(level,origin,owner,economy,context,request,before);
            routing(level,origin,owner,economy,context,before);
            System.out.println("PASS bridges: "+designs+" rotated/variable-length designs, three-wide passage, navigation opening, "
                    +"village materials, unequal shores, frozen codecs, shared reservation, mode pause/reload, one-shot cost, edits, claims, ice, storage and no chunk loads");
        } finally {
            if(occupant!=null)occupant.discard();
            level.getDataStorage().set(TYPE,CODEC.parse(NbtOps.INSTANCE,old).getOrThrow());
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,oldRoad).getOrThrow());
            before.forEach((p,s)->level.setBlock(p,s,18));
        }
    }
    private static void edgeCases(ServerLevel level,BlockPos o,UUID owner,EconomyService economy,
            VillageBridges.Context c,WalkwayConnections.Request r,Map<BlockPos,BlockState> before) {
        reset(level,o,before);level.getDataStorage().set(TYPE,new VillageBridgeLedger());
        river(level,o,Direction.EAST,13,2);
        require(survey(level,r,c,o,Direction.EAST)!=null,"two-block unequal banks rejected");
        for(int x=19;x<=24;x++)for(int side=-4;side<=4;side++)
            level.setBlock(o.offset(x,3,side),Blocks.GRASS_BLOCK.defaultBlockState(),18);
        require(survey(level,r,c,o,Direction.EAST)==null,"steep cliff accepted");
        reset(level,o,before);river(level,o,Direction.EAST,13,0);
        var shallow=new VillageBridges.Context(economy,owner,c.materials(),false,48,2,1);
        require(survey(level,r,shallow,o,Direction.EAST)==null,"pier exceeded configured depth");
        level.setBlock(o.east(8),Blocks.LAVA.defaultBlockState(),18);
        require(survey(level,r,c,o,Direction.EAST)==null,"lava crossed");
        reset(level,o,before);river(level,o,Direction.EAST,13,0);
        level.setBlock(o.east(6).south(2).below(),Blocks.CHEST.defaultBlockState(),18);
        require(survey(level,r,c,o,Direction.EAST)==null,"pier replaced underwater inventory");
        reset(level,o,before);river(level,o,Direction.EAST,7,0);
        river(level,o.east(30),Direction.EAST,7,0);
        Plan first=survey(level,r,c,o,Direction.EAST);
        Plan second=survey(level,r,c,o.east(30),Direction.EAST);
        require(first!=null&&second!=null&&!VillageBridgeLedger.parallel(first,second),"island confused with parallel crossing");
        Plan conflict=new Plan(UUID.randomUUID().toString(),first.village(),first.project(),first.start(),first.end(),
                first.style(),first.waterLength(),first.waterY(),first.low(),first.high(),first.route(),first.pieces());
        require(!VillageBridges.reserveAll(level,c,List.of(first,conflict))&&get(level).jobs.isEmpty(),
                "failed batch left orphan reservations");
        var one=new VillageBridges.Context(economy,owner,c.materials(),false,48,12,1);
        require(VillageBridges.reserveAll(level,one,List.of(first,second)),"multi-crossing route could not queue");
        economy.configureForcedVillageDevelopment(true);
        for(int n=0;n<200&&!get(level).jobs.get(first.id()).funded();n++)
            VillageBridges.advance(level,r,one,first.id(),16);
        for(int n=0;n<200;n++)VillageBridges.advance(level,r,one,second.id(),16);
        require(get(level).jobs.get(first.id()).funded()&&!get(level).jobs.get(second.id()).funded(),
                "queued crossing exceeded crew limit or charged early");
        for(Plan p:List.of(first,second))for(int n=0;n<2000&&!get(level).jobs.get(p.id()).done();n++)
            VillageBridges.advance(level,r,one,p.id(),16);
        require(get(level).jobs.values().stream().allMatch(Job::done),"queued island crossings deadlocked");
        System.out.println("PASS bridge edge cases: steep banks, two-block rise, deep piers, lava, underwater inventory, atomic admission and one-crew island queue");
    }
    private static void routing(ServerLevel level,BlockPos origin,UUID owner,EconomyService economy,
            VillageBridges.Context context,Map<BlockPos,BlockState> before) throws Exception {
        reset(level,origin,before);level.getDataStorage().set(TYPE,new VillageBridgeLedger());
        level.getDataStorage().set(WalkwayConnectionLedger.TYPE,new WalkwayConnectionLedger());
        economy.configureForcedVillageDevelopment(true);
        // A river reaches beyond the loaded search corridor: no arbitrary dry target or partial stub can win.
        for(int x=6;x<19;x++)for(int z=-10;z<=42;z++)for(int y=-2;y<=0;y++)
            level.setBlock(origin.offset(x,y,z),Blocks.WATER.defaultBlockState(),18);
        var r=new WalkwayConnections.Request(owner,800,origin.asLong(),origin,origin.east(30),
                false,Set.of(),List.of(),List.of(),false);
        boolean reloaded=false;int surveyPulses=0;
        for(int n=0;n<8000&&!WalkwayConnectionLedger.get(level).job(r.key()).done();n++) {
            surveyPulses=n+1;
            int writes=WalkwayConnections.advance(level,r,100000+n*20L,16,context);
            require(writes<=16,"bridge escaped connector allowance");
            var j=WalkwayConnectionLedger.get(level).job(r.key());
            if(!reloaded&&!j.plan().isEmpty()) {
                level.getDataStorage().set(WalkwayConnectionLedger.TYPE,WalkwayConnectionLedger.CODEC.parse(NbtOps.INSTANCE,
                        WalkwayConnectionLedger.CODEC.encodeStart(NbtOps.INSTANCE,WalkwayConnectionLedger.get(level)).getOrThrow()).getOrThrow());
                level.getDataStorage().set(TYPE,CODEC.parse(NbtOps.INSTANCE,CODEC.encodeStart(NbtOps.INSTANCE,get(level)).getOrThrow()).getOrThrow());
                reloaded=true;
            }
        }
        var j=WalkwayConnectionLedger.get(level).job(r.key());
        require(surveyPulses<1000,"near-identical shore surveys delayed a simple river route: "+surveyPulses);
        require(j.done()&&reloaded&&get(level).jobs.size()==1,"route never connected through bridge: "+j.reason());
        require(get(level).jobs.values().iterator().next().done(),"road completed before crossing verification");
        // Another entrance joins the same bridge, including the reverse direction.
        var reuse=new WalkwayConnections.Request(owner,801,origin.asLong(),origin.east(30).south(4),origin.south(4),
                false,Set.of(),List.of(),List.of(),false);
        for(int n=0;n<8000&&!WalkwayConnectionLedger.get(level).job(reuse.key()).done();n++)
            WalkwayConnections.advance(level,reuse,300000+n*20L,16,null);
        require(WalkwayConnectionLedger.get(level).job(reuse.key()).done()&&get(level).jobs.size()==1,
                "second road did not reuse crossing: "+WalkwayConnectionLedger.get(level).job(reuse.key()).reason());
        // A short dry detour should beat a new bridge.
        reset(level,origin,before);level.getDataStorage().set(TYPE,new VillageBridgeLedger());
        level.getDataStorage().set(WalkwayConnectionLedger.TYPE,new WalkwayConnectionLedger());
        river(level,origin,Direction.EAST,7,0);
        var dry=new WalkwayConnections.Request(owner,802,origin.asLong(),origin,origin.east(25),
                false,Set.of(),List.of(),List.of(),false);
        for(int n=0;n<8000&&!WalkwayConnectionLedger.get(level).job(dry.key()).done();n++)
            WalkwayConnections.advance(level,dry,500000+n*20L,16,context);
        require(WalkwayConnectionLedger.get(level).job(dry.key()).done()&&get(level).jobs.isEmpty(),
                "unnecessary bridge beat short land detour");
        // A protected onward destination must not leave a paid bridge to nowhere.
        level.getDataStorage().set(WalkwayConnectionLedger.TYPE,new WalkwayConnectionLedger());
        var unreachable=new WalkwayConnections.Request(owner,803,origin.asLong(),origin,origin.east(30),
                false,Set.of(),List.of(),List.of(),false);
        try(var guard=VillageDevelopmentProtection.register(c->c.position().getX()<origin.getX()+27)) {
            for(int n=0;n<4000;n++)WalkwayConnections.advance(level,unreachable,700000+n*20L,16,context);
        }
        require(!WalkwayConnectionLedger.get(level).job(unreachable.key()).done()&&get(level).jobs.isEmpty(),
                "bridge funded before reaching destination");
        System.out.println("PASS bridge route integration: complete onward path, two-ledger reload, reverse shared crossing, land-detour cost and unreachable destination; initial crossing pulses="+surveyPulses);
    }
    private static Plan survey(ServerLevel level,WalkwayConnections.Request r,VillageBridges.Context c,BlockPos p,Direction d) {
        var survey=new VillageBridgeSurvey(r,c,p,d);
        for(int n=0;n<500;n++)if(survey.advance(level))return survey.result;
        throw new IllegalStateException("Unbounded bridge survey");
    }
    private static void reset(ServerLevel level,BlockPos origin,Map<BlockPos,BlockState> before) {
        // Only the small test patch, never saved player terrain.
        for(int x=-5;x<=65;x++)for(int z=-5;z<=65;z++)for(int y=-3;y<=9;y++)
            level.setBlock(origin.offset(x,y,z),(y<0?Blocks.STONE:y==0?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),18);
    }
    private static void river(ServerLevel level,BlockPos origin,Direction d,int length,int farHeight) {
        for(int i=6;i<6+length;i++)for(int side=-4;side<=4;side++)for(int y=-2;y<=0;y++)
            level.setBlock(origin.relative(d,i).relative(d.getClockWise(),side).above(y),Blocks.WATER.defaultBlockState(),18);
        if(farHeight>0)for(int i=6+length;i<12+length;i++)for(int side=-4;side<=4;side++)
            for(int height=1;height<=farHeight;height++)
                level.setBlock(origin.relative(d,i).relative(d.getClockWise(),side).above(height),Blocks.GRASS_BLOCK.defaultBlockState(),18);
    }
    private static void geometry(Plan plan,Direction d) {
        Map<Long,Piece> pieces=new HashMap<>();plan.pieces().forEach(p->pieces.put(p.pos(),p));
        for(int i=0;i<plan.route().size();i++) {
            BlockPos center=BlockPos.of(plan.route().get(i));
            for(int s=-1;s<=1;s++) {
                BlockPos p=center.relative(d.getClockWise(),s);
                require(pieces.get(p.asLong()).phase()==2,"three-block deck missing");
                require(pieces.get(p.above().asLong()).after().isAir()&&pieces.get(p.above(2).asLong()).after().isAir(),
                        "lamp or railing narrows passage");
            }
            if(i>0) {
                BlockPos prev=BlockPos.of(plan.route().get(i-1));
                require(Math.abs(prev.getY()-center.getY())<=1,"unclimbable approach");
                if(center.getY()>prev.getY())require(pieces.get(center.asLong()).after().getValue(StairBlock.FACING)==d,
                        "uphill stair backwards");
            }
        }
        int mid=6+(plan.waterLength()-1)/2;
        for(int i=Math.max(6,mid-2);i<=Math.min(5+plan.waterLength(),mid+2);i++)
            for(int s=-2;s<=2;s++)for(int y=plan.waterY()+1;y<=plan.waterY()+2;y++) {
                BlockPos p=BlockPos.of(plan.route().get(i)).relative(d.getClockWise(),s).atY(y);
                require(pieces.get(p.asLong()).after().isAir(),"navigation channel obstructed");
            }
        require(plan.pieces().stream().anyMatch(p->p.after().is(Blocks.LANTERN)),"village lamps missing");
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
