package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Disposable native hillside fixture. Never runs against a user's world without smoke opt-in. */
final class VillageTerrainGroundingSelfTest {
    static void verify(ServerLevel level) throws Exception {
        BlockPos origin=new BlockPos(-1880,level.getMaxY()-60,-1880);
        Map<BlockPos,BlockState> restore=new LinkedHashMap<>();
        var base=new SitePreparationPlan(List.of());
        Map<BlockPos,BlockState> pads=new LinkedHashMap<>();
        for(int x=0;x<3;x++)for(int z=0;z<3;z++) pads.put(origin.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
        Set<BlockPos> occupied=new HashSet<>(pads.keySet());
        var route=List.of(origin.offset(-1,0,1));
        try {
            for(int x=-7;x<=9;x++)for(int z=-7;z<=9;z++) {
                level.getChunk(origin.offset(x,0,z)); // Explicit test-only fixture loading.
                for(int y=-10;y<=3;y++) set(level,restore,origin.offset(x,y,z),
                        y<-4?Blocks.STONE.defaultBlockState():y==-4?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState());
            }
            var plan=VillageTerrainGrounding.freeze(level,base,origin,pads,occupied,route,null,1);
            require(plan!=null&&!plan.cells().isEmpty(),"floating shelf has a frozen earthwork plan");
            var finalCells=new HashMap<BlockPos,BlockState>();
            for(var c:plan.cells())finalCells.put(BlockPos.of(c.position()),VillageTerrainFinishing.state(level,c.after()));
            for(var pad:pads.keySet())for(int y=-3;y<-1;y++)
                require(finalCells.containsKey(new BlockPos(pad.getX(),origin.getY()+y,pad.getZ())),"continuous soil beneath every yard column");
            require(finalCells.get(origin.offset(-1,-2,0)).is(Blocks.GRASS_BLOCK),"first skirt terrace is one block below pad");
            require(finalCells.get(origin.offset(-2,-3,0)).is(Blocks.GRASS_BLOCK),"second skirt terrace meets native ground");
            require(finalCells.keySet().stream().noneMatch(p->p.getX()==route.getFirst().getX()&&p.getZ()==route.getFirst().getZ()),"public route not buried");
            require(finalCells.keySet().stream().noneMatch(occupied::contains),"authored pad states retained");
            require(SitePreparationPlan.decode(plan.encode()).cells().equals(plan.cells()),"earthworks survive save/restart exactly");
            for(var ground:List.of(Blocks.PODZOL,Blocks.MYCELIUM,Blocks.ANDESITE)) {
                for(int x=-7;x<=9;x++)for(int z=-7;z<=9;z++)set(level,restore,origin.offset(x,-4,z),ground.defaultBlockState());
                SiteSurveyRejections.reset();
                var matched=VillageTerrainGrounding.freeze(level,base,origin,pads,occupied,route,null,1);
                require(matched!=null&&VillageTerrainFinishing.state(level,matched.cells().stream()
                        .filter(c->c.position()==origin.offset(-1,-2,0).asLong()).findFirst().orElseThrow().after()).is(ground),
                        "hill skirt does not blend with local natural ground: "+ground);
            }
            for(int x=-7;x<=9;x++)for(int z=-7;z<=9;z++)set(level,restore,origin.offset(x,-4,z),Blocks.GRASS_BLOCK.defaultBlockState());
            SiteSurveyRejections.reset();
            require(!VillageTerrainGrounding.earthFill(new SitePreparationPlan.Cell(origin.asLong(),"minecraft:grass_block[snowy=false]","minecraft:grass_block[snowy=false]")),"public pocket garden caps do not enlarge exclusive building reservations");
            var boundsType=Class.forName(VillageProsperityManager.class.getName()+"$ProjectBounds");
            var constructor=boundsType.getDeclaredConstructor(BlockPos.class,BlockPos.class);constructor.setAccessible(true);
            var expand=VillageProsperityManager.class.getDeclaredMethod("groundingBounds",boundsType,SitePreparationPlan.class);expand.setAccessible(true);
            Object footprint=constructor.newInstance(origin.offset(0,-4,0),origin.offset(2,8,2));
            Object expanded=expand.invoke(null,footprint,plan);
            var minimum=boundsType.getDeclaredMethod("minimum");minimum.setAccessible(true);
            var maximum=boundsType.getDeclaredMethod("maximum");maximum.setAccessible(true);
            require(((BlockPos)minimum.invoke(expanded)).getX()<=origin.getX()-2
                    &&((BlockPos)maximum.invoke(expanded)).getX()>=origin.getX()+4,"saved lot contains its complete sloping earth skirt");
            require(expand.invoke(null,expanded,SitePreparationPlan.decode(plan.encode())).equals(expanded),"saved earthwork boundaries never shrink after reload");
            Map<BlockPos,BlockState> tree=new LinkedHashMap<>();
            for(int y=-3;y<=0;y++)tree.put(origin.offset(-1,y,0),Blocks.OAK_LOG.defaultBlockState());
            for(int x=-2;x<=0;x++)for(int z=-1;z<=1;z++)tree.put(origin.offset(x,1,z),Blocks.OAK_LEAVES.defaultBlockState());
            tree.forEach((p,s)->set(level,restore,p,s));
            var forestEdge=VillageTerrainGrounding.freeze(level,base,origin,pads,occupied,route,null,1);
            require(forestEdge!=null&&forestEdge.cells().stream().noneMatch(c->tree.containsKey(BlockPos.of(c.position()))),"optional skirt cannot sever a tree without whole-tree clearing receipts");
            tree.keySet().forEach(p->set(level,restore,p,Blocks.AIR.defaultBlockState()));SiteSurveyRejections.reset();
            var balcony=new LinkedHashMap<>(pads);balcony.put(origin.offset(1,3,1),Blocks.DIRT.defaultBlockState());
            require(VillageTerrainGrounding.freeze(level,base,origin,balcony,occupied,route,null,1).cells().equals(plan.cells()),"upper soil planter never raises landscaping above the ground floor");
            BlockPos terrace=origin.offset(7,0,7);
            for(int y=-3;y<=-1;y++)set(level,restore,terrace.above(y),(y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
            var raisedYard=VillageTerrainGrounding.freeze(level,base,origin,Map.of(terrace.above(2),Blocks.GRASS_BLOCK.defaultBlockState()),Set.of(terrace.above(2)),List.of(),null,1);
            require(raisedYard!=null&&raisedYard.cells().stream().anyMatch(c->c.position()==terrace.above().asLong()),"raised rigid outdoor soil gets backing too");
            for(int y=-3;y<=-1;y++)set(level,restore,terrace.above(y),Blocks.AIR.defaultBlockState());
            BlockPos blocked=origin.offset(1,-3,1);
            for(Block block:new Block[]{Blocks.WATER,Blocks.CHEST,Blocks.OAK_PLANKS}) {
                set(level,restore,blocked,block.defaultBlockState());
                require(VillageTerrainGrounding.freeze(level,base,origin,pads,occupied,route,null,1)==null,"required footing rejects "+block);
                set(level,restore,blocked,Blocks.AIR.defaultBlockState());
                SiteSurveyRejections.reset();
            }
            try(var guard=VillageDevelopmentProtection.register(context->false)) {
                require(VillageTerrainGrounding.freeze(level,base,origin,pads,occupied,route,null,1)==null,"claim guard vetoes fill");
            }
            set(level,restore,origin.offset(1,-5,1),Blocks.AIR.defaultBlockState());
            require(VillageTerrainGrounding.freeze(level,base,origin,pads,occupied,route,null,1)!=null,"natural cave roof is not tunnelled through");
            set(level,restore,origin.offset(1,-5,1),Blocks.STONE.defaultBlockState());
            var shaft=new LinkedHashMap<>(pads);shaft.put(blocked,Blocks.AIR.defaultBlockState());
            var withShaft=VillageTerrainGrounding.freeze(level,base,origin,shaft,occupied,route,null,1);
            require(withShaft!=null&&withShaft.cells().stream().noneMatch(c->c.position()==blocked.asLong()),"authored mine shaft stays open");
            var highPads=new LinkedHashMap<BlockPos,BlockState>();
            highPads.put(origin.above(8),Blocks.GRASS_BLOCK.defaultBlockState());
            require(VillageTerrainGrounding.freeze(level,base,origin.above(8),highPads,Set.of(),List.of(),null,1)==null,"deep chasm cannot become a giant platform");
            var remote=new BlockPos(1000000,origin.getY(),1000000);
            require(!level.hasChunk(remote.getX()>>4,remote.getZ()>>4),"unloaded fixture column");
            require(VillageTerrainGrounding.freeze(level,base,remote,Map.of(remote,Blocks.GRASS_BLOCK.defaultBlockState()),Set.of(),List.of(),null,1)==null,"unloaded backing waits without loading");
            require(!level.hasChunk(remote.getX()>>4,remote.getZ()>>4),"survey did not force-load chunks");
            // Production replay uses the same instant-development allowance, then becomes a no-op.
            var project=new EconomyState.VillageProject();project.projectId=1;project.sitePreparationPlan=plan;project.constructionStarted=true;
            var village=new EconomyState.VillageRecord();village.villageId=UUID.randomUUID();
            var economy=new EconomyService();
            var replay=VillageProsperityManager.class.getDeclaredMethod("prepareNewSite",ServerLevel.class,EconomyService.class,
                    EconomyState.VillageRecord.class,EconomyState.VillageProject.class,int.class); replay.setAccessible(true);
            for(var cell:plan.cells())restore.putIfAbsent(BlockPos.of(cell.position()),level.getBlockState(BlockPos.of(cell.position())));
            int changed=(int)replay.invoke(null,level,economy,village,project,32768);
            require(changed==plan.cells().size(),"instant terrain preparation finishes in one loaded pulse");
            for(var cell:plan.cells())require(VillageTerrainFinishing.satisfied(level,level.getBlockState(BlockPos.of(cell.position())),cell.after()),"solid final earthwork "+cell);
            require((int)replay.invoke(null,level,economy,village,project,32768)==0,"no repeated landscaping writes");
            System.out.println("PASS VillageTerrainGroundingSelfTest: filled shelf, stepped skirts, routes, shaft, cave, fluid/storage/claims, chunks, restart, instant replay");
        } finally { restore.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)); SiteSurveyRejections.reset(); }
    }
    private static void set(ServerLevel level,Map<BlockPos,BlockState> restore,BlockPos pos,BlockState state) {
        restore.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
