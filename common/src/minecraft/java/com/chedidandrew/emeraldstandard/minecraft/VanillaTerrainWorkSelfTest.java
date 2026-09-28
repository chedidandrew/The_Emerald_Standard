package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.SitePreparationPlan;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Disposable-world regressions for raised imported floors and their recessed entrances. */
final class VanillaTerrainWorkSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin=new BlockPos(1104,level.getMaxY()-50,1104);
        Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        UUID village=UUID.fromString("fb078a5a-1bca-4788-beb8-76e280b192fb");
        try {
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-8;y<=5;y++) {
                var p=origin.offset(x,y,z);level.getChunk(p);saved.put(p,level.getBlockState(p));
            }
            for(int turn=0;turn<4;turn++)for(int drop=1;drop<=4;drop++) {
                for(var p:saved.keySet()) level.setBlock(p,(p.getY()<=origin.getY()-drop?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18);
                Map<BlockPos,BlockState> authored=new LinkedHashMap<>();
                for(int x=-1;x<=1;x++)for(int z=1;z<=3;z++) {
                    var p=rotate(origin,x,0,z,turn);authored.put(p,Blocks.COBBLESTONE.defaultBlockState());
                    level.setBlock(p,Blocks.COBBLESTONE.defaultBlockState(),18);
                }
                // Roof overhang must not turn into a solid pillar.
                authored.put(rotate(origin,3,3,1,turn),Blocks.OAK_PLANKS.defaultBlockState());
                List<BlockPos> route=new ArrayList<>();
                for(int i=0;i<6;i++)route.add(rotate(origin,0,0,-2-i,turn));
                Direction inward=Direction.SOUTH;
                for(int i=0;i<turn;i++)inward=inward.getClockWise();
                var plan=VanillaTerrainWork.survey(level,village,1,origin,authored,route,
                        Blocks.COBBLESTONE.defaultBlockState(),Blocks.STONE_BRICK_STAIRS.defaultBlockState(),inward,Map.of(),null,true);
                check(plan.plan()!=null,"rotation "+turn+" drop "+drop+": "+plan.failure());
                var decoded=SitePreparationPlan.decode(plan.plan().encode());
                check(decoded.equals(plan.plan()),"save/reload preserves plan");
                for(var cell:decoded.cells())level.setBlock(BlockPos.of(cell.position()),VillageTerrainFinishing.state(level,cell.after()),3);
                check(level.getBlockState(rotate(origin,0,0,-1,turn)).is(Blocks.COBBLESTONE),"missing outside landing");
                check(level.getBlockState(rotate(origin,0,0,0,turn)).is(Blocks.COBBLESTONE),"missing recessed landing");
                var first=level.getBlockState(route.getFirst());
                check(first.is(Blocks.STONE_BRICK_STAIRS)&&first.getValue(StairBlock.FACING)==inward,"stair floor elevation/facing");
                check(level.getBlockState(rotate(origin,3,-1,1,turn)).isAir() || drop==1,"roof not supported as ground floor");
                // Re-survey after player modification must not silently replace that block.
                for(var p:saved.keySet())level.setBlock(p,(p.getY()<=origin.getY()-drop?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18);
                authored.forEach((p,s)->level.setBlock(p,s,18));
                var landing=rotate(origin,0,0,-1,turn);level.setBlock(landing,Blocks.CHEST.defaultBlockState(),3);
                var blocked=VanillaTerrainWork.survey(level,village,1,origin,authored,route,
                        Blocks.COBBLESTONE.defaultBlockState(),Blocks.STONE_BRICK_STAIRS.defaultBlockState(),inward,Map.of(),null,true);
                check(blocked.plan()==null&&blocked.failure().contains(landing.toShortString()),"inventory itself must cause the rejection");
                check(level.getBlockState(landing).is(Blocks.CHEST),"survey never removes inventory");
                for(var p:saved.keySet())level.setBlock(p,(p.getY()<=origin.getY()-6?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18);
                authored.forEach((p,s)->level.setBlock(p,s,18));
                var deep=VanillaTerrainWork.survey(level,village,1,origin,authored,route,
                        Blocks.COBBLESTONE.defaultBlockState(),Blocks.STONE_BRICK_STAIRS.defaultBlockState(),inward,Map.of(),null,true);
                check(deep.plan()==null&&deep.failure().contains("foundation depth"),"excessive fill must be rejected, not waived");
            }
            // Use actual packaged templates too: geometry may differ from the small regression fixture.
            for (String style:List.of("plains","desert","savanna","taiga","snowy")) {
                var entry=VanillaVillageBuildings.manifest().stream()
                        .filter(e->e.id().contains("/"+style+"/houses/"+style+"_small_house_1")).findFirst().orElseThrow();
                com.chedidandrew.emeraldstandard.core.VanillaConstructionPlan imported;
                try(var in=Blocks.class.getResourceAsStream("/data/minecraft/structure/"+entry.id().substring(10)+".nbt")) {
                    imported=VanillaVillageBuildings.convert(entry,in.readAllBytes());
                } catch(java.io.IOException e) {throw new IllegalStateException(e);}
                var villageRecord=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageRecord();
                villageRecord.villageId=village;villageRecord.architectureDialect=style;
                villageRecord.architectureCharacter="agrarian";villageRecord.centerPos=origin.north(25).asLong();
                var project=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageProject();
                project.projectId=1;project.vanillaPlan=imported;
                project.designSchema=com.chedidandrew.emeraldstandard.core.VanillaConstructionPlan.SCHEMA;
                project.type=com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType.HOUSE;
                project.trailAnchorSet=true;project.trailAnchorPos=origin.north(25).asLong();
                for(int turn=0;turn<4;turn++) {
                    for(var p:saved.keySet()) level.setBlock(p,(p.getY()<=origin.getY()-2?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18);
                    project.designRotation=turn;
                    var actual=(VanillaTerrainWork.Survey)ConstructionSupportRecoverySelfTest.invoke("vanillaTerrainSurvey",
                            level,origin,villageRecord,project,null,false);
                    check(actual.plan()!=null,style+" imported turn "+turn+": "+actual.failure());
                }
            }
            System.out.println("PASS imported terrain: 16 rotated slope/recessed-entry plans, 20 real five-style plans, no roof pillars, frozen reload and inventory protection");
        } catch(Exception e) { throw new IllegalStateException("Imported terrain fixture",e); }
        finally { for(var e:saved.entrySet())level.setBlock(e.getKey(),e.getValue(),18); }
    }
    private static BlockPos rotate(BlockPos o,int x,int y,int z,int turns) {
        for(int i=0;i<turns;i++){int old=x;x=-z;z=old;}return o.offset(x,y,z);
    }
    private static void check(boolean yes,String why) {if(!yes)throw new IllegalStateException("Imported foundation/access: "+why);}
}
