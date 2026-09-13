package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

final class VillagePopulationSelfTest {
    static void verify(ServerLevel level) {
        BlockPos center=new BlockPos(6608,level.getMaxY()-100,6608);
        BlockPos bed=center.offset(100,40,0);
        level.getChunk(bed); // Disposable fixture only. Production code never loads chunks.
        var village=new EconomyState.VillageRecord();village.villageId=new UUID(818,1);village.centerPos=center.asLong();
        var home=new EconomyState.VillageProject();home.type=VillageProsperityEngine.ProjectType.HOUSE;
        home.originPos=bed.asLong();home.boundsMinPos=bed.offset(-5,-1,-5).asLong();home.boundsMaxPos=bed.offset(5,5,5).asLong();
        home.materializedBlocks=1;village.projects.add(home);
        var neighbor=new EconomyState.VillageRecord();neighbor.villageId=new UUID(818,2);neighbor.centerPos=bed.east(3).asLong();
        require(VillagePopulationEnvironment.owner(bed,List.of(village,neighbor)).equals(village.villageId),"registered home outranks neighboring center");
        require(VillagePopulationEnvironment.owner(center.west(500),List.of(village,neighbor))==null,"unrelated land not owned");
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        Villager settler=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
        Monster monster=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        require(settler!=null && monster!=null,"entity factories");
        try {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                level.getChunk(bed.offset(x,0,z));
                set(level,before,bed.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for(int y=0;y<=4;y++)set(level,before,bed.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            set(level,before,bed,Blocks.BED.white().defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,Direction.NORTH));
            set(level,before,bed.south(),Blocks.BED.white().defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,Direction.NORTH));
            require(VillagePopulationEnvironment.intactBed(level,bed),"two matching bed halves");
            var scan=new VillagePopulationEnvironment.Scan(level,village,List.of(village,neighbor));
            int pulses=0;while(!scan.advance(257))require(++pulses<10000,"bounded survey completes");
            require(scan.completed.values().stream().flatMap(List::stream).filter(p -> p==bed.asLong()).count()==1,
                    "distant upper-floor head counted exactly once");
            var otherScan=new VillagePopulationEnvironment.Scan(level,neighbor,List.of(village,neighbor));
            while(!otherScan.advance(2048)){}
            require(otherScan.completed.values().stream().flatMap(List::stream).noneMatch(p -> p==bed.asLong()),"overlap cannot count home twice");
            village.projects.clear();
            var residentHome=new EconomyState.ResidentRecord();
            residentHome.residentId=new UUID(818,3);residentHome.homePos=bed.asLong();
            residentHome.status=VillageProsperityEngine.ResidentStatus.ACTIVE;
            village.residents.put(residentHome.residentId,residentHome);
            var homeScan=new VillagePopulationEnvironment.Scan(level,village,List.of(village,neighbor));
            while(!homeScan.advance(2048)) {}
            require(homeScan.completed.values().stream().flatMap(List::stream).anyMatch(p -> p==bed.asLong()),
                    "registered resident home beyond developed rectangle is surveyed without enlarging an enormous rectangle");
            int unknownX=(center.getX()>>4)-4,unknownZ=(center.getZ()>>4)-4;
            if(!level.hasChunk(unknownX,unknownZ))require(!scan.completed.containsKey(
                    ((long)unknownX&0xffffffffL)|((long)unknownZ<<32)),"unknown chunks not written as empty");
            BlockPos arrival=VillagePopulationEnvironment.findArrival(level,bed,settler);
            require(arrival!=null && arrival.getY()==bed.getY(),"usable landing on bed floor, not roof");
            monster.setNoAi(true);monster.teleportTo(arrival.getX()+4.5,arrival.getY(),arrival.getZ()+.5);
            require(level.addFreshEntity(monster),"monster fixture added");
            // This distant fixture is a loaded, non-ticking chunk: supply its real entity
            // directly to the production LOS evaluator instead of relying on entity visibility.
            require(VillagePopulationEnvironment.dangerous(level,arrival,settler,List.of(monster)),"exposed nearby monster blocks landing");
            for(int z=-3;z<=3;z++)for(int y=0;y<=3;y++)
                set(level,before,arrival.offset(2,y,z),Blocks.STONE.defaultBlockState());
            require(!VillagePopulationEnvironment.dangerous(level,arrival,settler,List.of(monster)),"solid wall shelters landing");
            monster.teleportTo(arrival.getX()+.5,arrival.getY()-4,arrival.getZ()+.5);
            require(!VillagePopulationEnvironment.dangerous(level,arrival,settler,List.of(monster)),"underground monster cannot block upstairs arrival");
            set(level,before,bed.south(),Blocks.AIR.defaultBlockState());
            require(!VillagePopulationEnvironment.intactBed(level,bed),"broken half-bed rejected");
            System.out.println("PASS VillagePopulationSelfTest: full-height distant homes, exclusive ownership, bounded unknown chunks, bed access, wall/underground/exposed threats");
        } finally {
            settler.discard();monster.discard();
            before.forEach((pos,state)->level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE));
            VillagePopulationEnvironment.reset();
        }
    }
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos p,BlockState state) {
        before.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
