package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Reproduces thin superflat soil and changing unfinished foundations in a disposable world. */
final class ForcedGrowthSelfTest {
    static void verify(ServerLevel level) {
        verifyMemoryAndSoil(level);
        try {
            for (String name : List.of("plains_fisher_cottage_1", "plains_medium_house_1"))
                for (int rotation=0;rotation<4;rotation++)
                    VanillaConstructionSelfTest.verifyOne(level,name,rotation,true,true);
        } catch (Exception e) { throw new IllegalStateException("Forced growth native fixture",e); }
        VillagePopulationSelfTest.verify(level);
        verifyArrival(level);
        ForcedDevelopmentSchedulingSelfTest.verify(level);
        System.out.println("PASS forced growth: thin bedrock backing, all rotations, natural soil changes, cache invalidation and scheduling");
    }

    private static void verifyMemoryAndSoil(ServerLevel level) {
        BlockPos origin=new BlockPos(7168,level.getMaxY()-48,7168);
        level.getChunk(origin);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        try {
            for (int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) for(int y=-4;y<=3;y++) {
                BlockPos at=origin.offset(x,y,z);level.getChunk(at);
                before.put(at,level.getBlockState(at));
                level.setBlock(at,y==-4 ? Blocks.BEDROCK.defaultBlockState()
                        : y<0 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(),18);
            }
            var survey=new VillageSitePreparation.Survey(level);
            require(survey.excavatable(origin.below(2),origin.getY()-2),"shallow bedrock vetoed safe soil");
            require(!survey.excavatable(origin.below(4),origin.getY()-4),"bedrock became excavatable");
            Object key="blocked-test", rejection=new Object();
            SiteSurveyRejections.remember(level,key,origin.getX(),origin.getZ(),2,rejection);
            require(SiteSurveyRejections.get(level,key,origin.getX(),origin.getZ(),2,100)==rejection,"unchanged rejection forgotten");
            level.setBlock(origin,Blocks.STONE.defaultBlockState(),18);
            require(SiteSurveyRejections.get(level,key,origin.getX(),origin.getZ(),2,100)==null,"block write retained stale survey");
            SiteSurveyRejections.remember(level,key,origin.getX(),origin.getZ(),2,rejection);
            require(SiteSurveyRejections.get(level,key,origin.getX(),origin.getZ(),2,0)==null,"expired claim rejection retained");
            SiteSurveyRejections.remember(level,key,origin.getX(),origin.getZ(),2,rejection);
            SiteSurveyRejections.reset();
            require(SiteSurveyRejections.get(level,key,origin.getX(),origin.getZ(),2,100)==null,"cache leaked across world reset");
            require(VillageProsperityManager.naturalSoilEquivalent(Blocks.GRASS_BLOCK.defaultBlockState(),Blocks.DIRT.defaultBlockState()),
                    "spreading grass invalidated supplied dirt");
            require(VillageProsperityManager.naturalSoilEquivalent(Blocks.DIRT.defaultBlockState(),Blocks.GRASS_BLOCK.defaultBlockState()),
                    "shaded grass invalidated supplied ground");
            require(!VillageProsperityManager.naturalSoilEquivalent(Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState())
                    && !VillageProsperityManager.naturalSoilEquivalent(Blocks.STONE.defaultBlockState(),Blocks.DIRT.defaultBlockState()),
                    "missing or replaced required ground was waived");
        } finally {
            before.forEach((p,s)->level.setBlock(p,s,18));SiteSurveyRejections.reset();
        }
    }

    private static void verifyArrival(ServerLevel level) {
        var clock=(net.minecraft.world.level.storage.ServerLevelData)level.getLevelData();
        long originalTime=level.getGameTime();
        BlockPos head=new BlockPos(7232,level.getMaxY()-48,7232);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        var added=new ArrayList<net.minecraft.world.entity.Entity>();
        try {
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=3;y++) {
                BlockPos at=head.offset(x,y,z);level.getChunk(at);before.put(at,level.getBlockState(at));
                level.setBlock(at,y==-1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),18);
            }
            var bed=Blocks.BED.white().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,
                    net.minecraft.core.Direction.NORTH);
            level.setBlock(head,bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
            level.setBlock(head.south(),bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.FOOT),3);
            before.keySet().forEach(level.getPathTypeCache()::invalidate);
            var state=com.chedidandrew.emeraldstandard.core.EconomyState.fresh(441,0,0);
            UUID id=UUID.randomUUID();var v=state.village(id);v.centerPos=head.asLong();
            v.dimensionKey=level.dimension().identifier().toString();v.housingCapacity=1;
            v.foodSupply=500;v.prosperity=v.safety=90;
            v.lifecycle=com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.Lifecycle.ACTIVE;
            long chunk=((long)(head.getX()>>4)&0xffffffffL)|((long)(head.getZ()>>4)<<32);
            v.housingChunks.put(chunk,List.of(head.asLong()));
            var dir=java.nio.file.Files.createTempDirectory("tes-forced-arrival-native-");
            state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=new com.chedidandrew.emeraldstandard.core.EconomyService();
            economy.configureEconomicClock(false,30);economy.start(dir,441,0);economy.configureForcedVillageDevelopment(true);
            // An occupied bed must never turn a queued invitation into a spawned resident.
            level.setBlock(head,level.getBlockState(head).setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED,true),18);
            VillagePopulationEnvironment.reset();
            VillagePopulationEnvironment.attemptArrival(level,economy,economy.villageSnapshot(id).village(),true);
            require(economy.villageSnapshot(id).village().population==0,"occupied home received settler");
            level.setBlock(head,level.getBlockState(head).setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED,false),18);
            clock.setGameTime(originalTime+20);
            VillagePopulationEnvironment.attemptArrival(level,economy,economy.villageSnapshot(id).village(),true);
            var arrived=economy.villageSnapshot(id).village();
            for(UUID resident:arrived.residents.keySet()) {
                var entity=level.getEntity(resident);if(entity!=null)added.add(entity);
            }
            require(arrived.population==1 && added.size()==1,"verified home did not receive a real settler: "+VillagePopulationEnvironment.status(id));
            require(arrived.pendingSettlers==0 && arrived.residents.values().iterator().next().homePos==head.asLong(),
                    "arrival lost its saved exclusive bed claim");
            VillagePopulationEnvironment.attemptArrival(level,economy,arrived,true);
            require(economy.villageSnapshot(id).village().population==1,"same-tick duplicate arrival");
            System.out.println("PASS forced arrival: occupied home waits, one real resident in verified home, saved bed claim and no duplicate");
        } catch(Exception e) {throw new IllegalStateException("Forced arrival fixture",e);}
        finally {
            added.forEach(net.minecraft.world.entity.Entity::discard);
            before.forEach((p,s)->level.setBlock(p,s,18));clock.setGameTime(originalTime);
            VillagePopulationEnvironment.reset();
        }
    }
    private static void require(boolean ok,String why) {if(!ok)throw new IllegalStateException(why);}
}
