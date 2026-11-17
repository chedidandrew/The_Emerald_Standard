package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Real world placement and mode switching in the disposable integration world only. */
final class ForcedWalkwayLightingSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin=new BlockPos(1376,level.getMaxY()-24,1248);
        UUID village=UUID.fromString("ee069827-19dc-4cdf-bb73-756593acaa64");
        var materials=AuthoredVillageStructures.walkwayMaterials(VillageArchitecture.Character.RUSTIC,
                VillageArchitecture.BiomeDialect.PLAINS);
        var savedConfig=EmeraldConfig.current();
        var savedLights=WalkwayLightingLedger.get(level);
        var savedRoads=WalkwayConnectionLedger.get(level);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        net.minecraft.world.entity.Entity cow=null;
        try {
            var field=EmeraldConfig.class.getDeclaredField("current");field.setAccessible(true);
            var properties=new Properties();properties.putAll(savedConfig.values());
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"false");
            field.set(null,EmeraldConfig.parse(properties));
            var lights=new WalkwayLightingLedger();var roads=new WalkwayConnectionLedger();
            level.getDataStorage().set(WalkwayLightingLedger.TYPE,lights);
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,roads);
            for(int lane=0;lane<3;lane++) for(int x=-2;x<=32;x++) for(int z=-5;z<=5;z++) for(int y=-1;y<=7;y++) {
                BlockPos p=origin.offset(x,y,lane*24+z);level.getChunk(p);
                before.put(p,level.getBlockState(p));
                level.setBlock(p,(y<0?Blocks.STONE:y==0?(z==0&&x>=0&&x<=30?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK)
                        :Blocks.AIR).defaultBlockState(),Block.UPDATE_ALL);
            }
            List<BlockPos> route=java.util.stream.IntStream.range(0,31).mapToObj(origin::east).toList();
            String baseKey=WalkwayLightingLedger.key(village,1,origin.asLong());
            String key=baseKey+"/connection-v1";
            require(WalkwayLighting.acquire(level,1000)&&!WalkwayLighting.due(level,1001),"normal cooldown");
            require(WalkwayLighting.advance(level,village,1,key,route,materials,List.of(),List.of(),64)==0,
                    "normal mode must survey without an instant burst");
            require(WalkwayLighting.advance(level,village,1,key,route,materials,List.of(),List.of(),64)==2,
                    "normal two-write cap changed");
            var pending=lights.jobs.get(key);
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"true");
            field.set(null,EmeraldConfig.parse(properties));
            require(WalkwayLighting.acquire(level,1001)&&!WalkwayLighting.acquire(level,1001)
                    &&WalkwayLighting.due(level,1002),"forced timer retained one-second hold or duplicate admission");
            // Completed-route fixture exercises the production ordinary/Bank-shared dispatcher.
            var project=new EconomyState.VillageProject();project.projectId=1;project.originPos=origin.asLong();
            project.designSchema=VillageArchitecture.MODULAR_SCHEMA;
            project.economicComplete=project.materializedComplete=project.trailAnchorSet=true;
            var district=new EconomyState.VillageRecord();district.villageId=village;district.architectureDialect="plains";
            district.architectureCharacter=VillageArchitecture.Character.RUSTIC.id();district.projects.add(project);
            var steps=route.stream().map(p->new WalkwayConnectionLedger.Step(p.asLong(),Blocks.DIRT_PATH.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState())).toList();
            roads.put(baseKey,new WalkwayConnectionLedger.Job(steps,steps.size(),steps.size(),Set.of(),true,0,"Complete"));
            var economy=new EconomyService();
            economy.start(java.nio.file.Files.createTempDirectory("tes-forced-lamps-"),771,0);
            int writes=VillageProsperityManager.materializeOneWalkwayLamp(level,economy,district,List.of(),List.of(),1002,64);
            require(writes>=8&&writes<=64&&pending.pending().stream().allMatch(p->
                    level.getBlockState(BlockPos.of(p.position())).is(p.after().getBlock())),
                    "production forced dispatcher did not finish the old partial lamp in one visit");
            // Deterministic cooperative-window fixture drains remaining stations without sleeps.
            String connector=key+"/connection-v1";
            WalkwayLighting.advanceBatch(level,village,1,key,route,materials,List.of(),List.of(),64,true,()->true);
            WalkwayLighting.advanceBatch(level,village,1,connector,route,materials,List.of(),List.of(),64,true,()->true);
            require(lights.done(key)&&lights.done(connector),"forced stations did not drain or shared route spun");
            long lanterns=before.keySet().stream().filter(p->level.getBlockState(p).is(Blocks.LANTERN)).count();
            require(lanterns==3,"forced route must have three complete spaced lamps, not duplicates: "+lanterns);
            var decoded=WalkwayLightingLedger.CODEC.parse(NbtOps.INSTANCE,
                    WalkwayLightingLedger.CODEC.encodeStart(NbtOps.INSTANCE,lights).getOrThrow()).getOrThrow();
            level.getDataStorage().set(WalkwayLightingLedger.TYPE,decoded);lights=decoded;
            require(WalkwayLighting.acquire(level,1002),"reload must reset session-only admission timer");
            BlockPos lamp=before.keySet().stream().filter(p->level.getBlockState(p).is(Blocks.LANTERN)).findFirst().orElseThrow();
            level.setBlock(lamp,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            require(WalkwayLighting.advanceBatch(level,village,1,key,route,materials,List.of(),List.of(),64,true,()->true)==0
                    &&level.getBlockState(lamp).isAir(),"forced reload regenerated harvested lamp");

            List<BlockPos> shortRoute=java.util.stream.IntStream.range(0,9).mapToObj(x->origin.offset(x,0,24)).toList();
            String shortKey=WalkwayLightingLedger.key(village,2,origin.asLong());
            // A small grant still cannot exceed the server allowance, and resuming after disable stays slow.
            require(WalkwayLighting.advanceBatch(level,village,2,shortKey,shortRoute,materials,List.of(),List.of(),3,true,()->true)==3,
                    "forced write allowance ignored");
            properties.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"false");field.set(null,EmeraldConfig.parse(properties));
            require(!WalkwayLighting.due(level,1003),"disable retained forced timer");
            require(WalkwayLighting.advance(level,village,2,shortKey,shortRoute,materials,List.of(),List.of(),64)==2,
                    "disable retained instant writes");
            require(WalkwayLighting.advanceBatch(level,village,2,shortKey,shortRoute,materials,List.of(),List.of(),64,true,()->false)==5,
                    "one admitted tiny assembly must finish, then respect expired deadline");

            List<BlockPos> guardedRoute=java.util.stream.IntStream.range(0,9).mapToObj(x->origin.offset(x,0,48)).toList();
            String guardKey=WalkwayLightingLedger.key(village,3,origin.asLong());
            try(var ignored=VillageDevelopmentProtection.register(c->false)) {
                require(WalkwayLighting.advanceBatch(level,village,3,guardKey,guardedRoute,materials,List.of(),List.of(),64,true,()->true)==0,
                        "forced mode bypassed claims");
            }
            BlockPos foot=origin.offset(4,1,51);
            var search=WalkwayLighting.preflight(level,village,4,foot,
                    AuthoredVillageStructures.walkwayLamp(materials,Direction.NORTH),guardedRoute,List.of(),List.of());
            require(search.pieces().size()==10,"guarded fixture");
            String occupied="forced-occupied";
            lights.record(occupied,new WalkwayLightingLedger.Job(0,search.pieces(),0,false));
            cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);require(cow!=null,"cow fixture");
            cow.setPos(foot.getX()+.5,foot.getY(),foot.getZ()+.5);level.addFreshEntity(cow);
            require(WalkwayLighting.advanceBatch(level,village,4,occupied,guardedRoute,materials,List.of(),List.of(),64,true,()->true)==0
                    &&lights.jobs.get(occupied).placed()==0,"forced mode ignored occupancy or spun on it");
            cow.discard();cow=null;
            require(WalkwayLighting.advanceBatch(level,village,4,occupied,guardedRoute,materials,List.of(),List.of(),10,true,()->true)==10,
                    "clear occupied lamp failed to finish instantly");
            BlockPos remote=origin.east(160000);
            var remoteRoute=java.util.stream.IntStream.range(0,9).mapToObj(remote::east).toList();
            require(!level.hasChunk(remote.getX()>>4,remote.getZ()>>4)
                    &&WalkwayLighting.advanceBatch(level,village,5,"forced-unloaded",remoteRoute,materials,List.of(),List.of(),64,true,()->true)==0
                    &&!level.hasChunk(remote.getX()>>4,remote.getZ()>>4),"forced mode loaded remote chunks");
            System.out.println("PASS forced street lamps: production partial-job finish, immediate timer, complete assemblies,"
                    +" route drain, budgets/deadline, disable/reload, no regeneration, claims, occupancy and unloaded chunks");
        } catch(Exception ex) {throw new IllegalStateException("Forced street lamp regression",ex);}
        finally {
            if(cow!=null)cow.discard();
            try {var field=EmeraldConfig.class.getDeclaredField("current");field.setAccessible(true);field.set(null,savedConfig);}
            catch(Exception ex) {throw new IllegalStateException(ex);}
            level.getDataStorage().set(WalkwayLightingLedger.TYPE,savedLights);
            level.getDataStorage().set(WalkwayConnectionLedger.TYPE,savedRoads);
            before.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static void require(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);}
}
