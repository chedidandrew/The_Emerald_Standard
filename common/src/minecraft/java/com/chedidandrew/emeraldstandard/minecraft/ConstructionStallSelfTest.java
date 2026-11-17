package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Production terrain replay with a persisted approval, partial progress and foreign edits. */
final class ConstructionStallSelfTest {
    static void verify(ServerLevel level) throws Exception {
        BlockPos origin=new BlockPos(4600,level.getMaxY()-40,4600);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        try {
            ConstructionDiagnostics.reset();
            for(int x=0;x<4;x++)for(int y=-1;y<=2;y++) {
                BlockPos at=origin.offset(x,y,0);level.getChunk(at);before.put(at,level.getBlockState(at));
                level.setBlock(at,(y==0?Blocks.DIRT:y<0?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            level.setBlock(origin.east(),Blocks.COARSE_DIRT.defaultBlockState(),18);
            var state=EconomyState.fresh(66,0,0);var id=new UUID(66,66);var v=state.village(id);v.centerPos=origin.asLong();
            var p=new EconomyState.VillageProject();p.projectId=1;p.type=VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
            p.economicProgress=1;p.economicComplete=true;p.totalBlocks=100;v.projectSerial=1;v.projects.add(p);
            var dir=Files.createTempDirectory("tes-native-stall-");state.save(dir.resolve("the_emerald_standard.properties"));
            var service=new EconomyService();service.start(dir,66,0);
            var cells=List.of(new SitePreparationPlan.Cell(origin.asLong(),"minecraft:grass_block[snowy=false]","minecraft:air"),
                    new SitePreparationPlan.Cell(origin.east().asLong(),"minecraft:grass_block[snowy=false]","minecraft:gravel"),
                    new SitePreparationPlan.Cell(origin.east(2).asLong(),"minecraft:grass_block[snowy=false]","minecraft:stone_bricks"),
                    new SitePreparationPlan.Cell(origin.east(3).above().asLong(),"minecraft:vine[east=false,north=false,south=false,up=false,west=true]","minecraft:air"));
            var regrowth=origin.east(3).above();level.setBlock(regrowth,Blocks.SNOW.defaultBlockState(),18);
            require(VillageSitePreparation.matchesRemoval(Blocks.SNOW.defaultBlockState(),cells.getLast().before()),"captured vine-to-snow rejected");
            for(var foreign:List.of(Blocks.DIRT,Blocks.STONE,Blocks.OAK_PLANKS,Blocks.CHEST,Blocks.WHEAT,Blocks.WATER))
                require(!VillageSitePreparation.matchesRemoval(foreign.defaultBlockState(),cells.getLast().before()),"plant removal gained foreign authority "+foreign);
            require(service.reserveVillageProjectSite(id,1,origin.asLong(),origin.asLong(),origin.offset(3,3,3).asLong(),100,
                    "",0,0,0,0,0,0,"",new SitePreparationPlan(cells)),"fixture approval failed");
            var replay=VillageProsperityManager.class.getDeclaredMethod("prepareNewSite",ServerLevel.class,
                    EconomyService.class,EconomyState.VillageRecord.class,EconomyState.VillageProject.class,int.class);
            replay.setAccessible(true);
            v=service.villageSnapshot(id).village();p=v.projects.getFirst();
            require((int)replay.invoke(null,level,service,v,p,1)==1,"bounded partial terrain work stalled");
            v=service.villageSnapshot(id).village();p=v.projects.getFirst();
            require(p.sitePreparationCursor==2&&!p.sitePreparationComplete,"partial count hidden or false completion");
            require(level.getBlockState(origin.east()).is(Blocks.COARSE_DIRT),"existing road overwritten");
            level.setBlock(origin.east(2),Blocks.OAK_PLANKS.defaultBlockState(),18);
            require((int)replay.invoke(null,level,service,v,p,1)==0,"foreign structural edit overwritten");
            var observed=ConstructionDiagnostics.recent(id+"/1",level.getGameTime());
            require(observed!=null&&observed.reason().contains("actual=minecraft:oak_planks")&&observed.done()==2,"exact terrain blocker omitted");
            require(((Number)ConstructionDiagnostics.workloadSnapshot(System.nanoTime()).get("recentlyWaitingProjects")).intValue()==1,
                    "terrain wait missing from debug workload counts");
            level.setBlock(origin.east(2),Blocks.DIRT.defaultBlockState(),18); // Fixture clears its own foreign obstruction.
            v=service.villageSnapshot(id).village();p=v.projects.getFirst();
            require((int)replay.invoke(null,level,service,v,p,1)==1,"cleared native terrain did not resume");
            v=service.villageSnapshot(id).village();p=v.projects.getFirst();
            try(var claim=VillageDevelopmentProtection.register(c->!c.position().equals(regrowth))) {
                require((int)replay.invoke(null,level,service,v,p,1)==0&&level.getBlockState(regrowth).is(Blocks.SNOW),"snow-regrowth claim overwritten");
            }
            require((int)replay.invoke(null,level,service,v,p,1)==1,"approved natural snow regrowth stalled");
            var reload=new EconomyService();reload.start(dir,66,0);p=reload.villageSnapshot(id).village().projects.getFirst();
            require(p.sitePreparationComplete&&p.sitePreparationCursor==4&&level.getBlockState(origin.east(2)).is(Blocks.STONE_BRICKS)&&level.getBlockState(regrowth).isAir(),"complete terrain checkpoint lost on reload");
            System.out.println("PASS native stale terrain: captured soil/road conflicts, partial progress, foreign edit protection, exact blocker, resume and reload");
        } finally { before.forEach((at,s)->level.setBlock(at,s,18)); ConstructionDiagnostics.reset(); }
    }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
}
