package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Real collision/navigation and receipt checks in disposable opt-in worlds only. */
final class EntranceStaircaseSelfTest {
    static void verify(ServerLevel level) throws Exception {
        BlockPos exit=new BlockPos(2260,level.getMaxY()-40,2260);
        Map<BlockPos,BlockState> restore=new LinkedHashMap<>();
        try {
            for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=-9;y<=5;y++) {
                var p=exit.offset(x,y,z);level.getChunk(p);restore.put(p,level.getBlockState(p));
            }
            for(Direction out:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))
                for(int drop=0;drop<=4;drop++) {
                    reset(level,restore,exit,drop);
                    Map<BlockPos,BlockState> authored=new HashMap<>();
                    for(int depth=0;depth<=3;depth++)for(int side=-1;side<=1;side++)
                        authored.put(exit.relative(out.getOpposite(),depth).relative(out.getClockWise(),side).below(),Blocks.OAK_PLANKS.defaultBlockState());
                    authored.forEach((p,s)->level.setBlock(p,s,18));
                    var access=EntranceStaircase.survey(level,new SitePreparationPlan(List.of()),exit,out,authored,
                            Blocks.COBBLESTONE_STAIRS.defaultBlockState(),null,1);
                    require(access!=null,"flight rejected "+out+" drop="+drop);
                    var plan=SitePreparationPlan.decode(access.receipt().encode());
                    require(plan.equals(access.receipt()),"snapshot reload rerolled geometry");
                    if(drop==0) {require(plan.cells().isEmpty(),"flat entrance grew an apron");continue;}
                    long treads=plan.cells().stream().filter(c->c.after().contains("cobblestone_stairs[")).count();
                    require(treads==drop*3,"three complete treads per row: "+treads);
                    for(var c:plan.cells())level.setBlock(BlockPos.of(c.position()),VillageTerrainFinishing.state(level,c.after()),3);
                    require(EntranceStaircase.obstruction(level,plan)==null,"intact access blocks handover");
                    var bottom=exit.relative(out,drop+1).atY(exit.getY()-drop-1);
                    var bearing=level.getBlockState(bottom);
                    level.setBlock(bottom,Blocks.AIR.defaultBlockState(),18);
                    require(bottom.equals(EntranceStaircase.obstruction(level,plan)),"missing final footing can pass handover");
                    level.setBlock(bottom,bearing,18);
                    for(int row=0;row<drop;row++)for(int side=-1;side<=1;side++) {
                        var p=exit.relative(out,row+1).relative(out.getClockWise(),side).atY(exit.getY()-1-row);
                        var tread=level.getBlockState(p);
                        require(tread.is(Blocks.COBBLESTONE_STAIRS)&&tread.getValue(StairBlock.FACING)==out.getOpposite(),"wrong ascent direction");
                        require(tread.getValue(StairBlock.SHAPE)==net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT,"flight bent into a corner");
                        for(int y=exit.getY()-drop-1;y<p.getY();y++)
                            require(level.getBlockState(p.atY(y)).isFaceSturdy(level,p.atY(y),Direction.UP),"floating tread/backing gap");
                    }
                    var villager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
                    require(villager!=null,"native villager missing");
                    try {
                        var foot=exit.relative(out,drop+2).atY(exit.getY()-drop);
                        villager.setPos(foot.getX()+.5,foot.getY(),foot.getZ()+.5);
                        villager.setOnGround(true);
                        var path=villager.getNavigation().createPath(exit.relative(out.getOpposite(),2),0);
                        require(path!=null&&path.canReach(),"native navigation cannot reach doorway "+out+" drop="+drop);
                        VillageWalkingSelfTest.walk(level,villager,exit.relative(out.getOpposite(),2),"three-wide entrance "+out+" drop="+drop);
                        VillageWalkingSelfTest.walk(level,villager,foot,"three-wide return "+out+" drop="+drop);
                    } finally {villager.discard();}
                    reset(level,restore,exit,drop);authored.forEach((p,s)->level.setBlock(p,s,18));
                    var obstruct=exit.relative(out).relative(out.getClockWise()).below();
                    for(Block block:List.of(Blocks.CHEST,Blocks.OAK_PLANKS,Blocks.WATER)) {
                        level.setBlock(obstruct,block.defaultBlockState(),18);
                        require(EntranceStaircase.survey(level,new SitePreparationPlan(List.of()),exit,out,authored,
                                Blocks.COBBLESTONE_STAIRS.defaultBlockState(),null,1)==null,"foreign side obstruction bypassed: "+block);
                        level.setBlock(obstruct,Blocks.AIR.defaultBlockState(),18);SiteSurveyRejections.reset();
                    }
                    try(var claim=VillageDevelopmentProtection.register(c->!c.position().equals(obstruct))) {
                        require(EntranceStaircase.survey(level,new SitePreparationPlan(List.of()),exit,out,authored,
                                Blocks.COBBLESTONE_STAIRS.defaultBlockState(),null,1)==null,"side-lane claim ignored");
                    }
                }
            var remote=exit.offset(1000000,0,1000000);
            require(EntranceStaircase.survey(level,new SitePreparationPlan(List.of()),remote,Direction.NORTH,Map.of(),
                    Blocks.COBBLESTONE_STAIRS.defaultBlockState(),null,1)==null,"unloaded access admitted");
            require(!level.hasChunk(remote.getX()>>4,remote.getZ()>>4),"stair survey force-loaded chunks");
            System.out.println("PASS entrance staircases: 16 raised/4 flat rotations, three-wide straight flush treads, solid backing, native villager paths, reload, storage/player/fluid/claim safety and unloaded chunks");
        } finally {restore.forEach((p,s)->level.setBlock(p,s,18));SiteSurveyRejections.reset();}
    }
    private static void reset(ServerLevel level,Map<BlockPos,BlockState> saved,BlockPos exit,int drop) {
        saved.keySet().forEach(p->level.setBlock(p,(p.getY()<exit.getY()-drop?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18));
        SiteSurveyRejections.reset();
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
