package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import com.chedidandrew.emeraldstandard.core.*;

/** A viable destination is inspected before crossings receive a reservation or any writes. */
final class BridgeFirstExpansion {
    record Candidate(Object site,EconomyState.VillageProject design,String dialect,long tick) { }
    private static final Map<ServerLevel,Map<String,Candidate>> CANDIDATES=new WeakHashMap<>();
    static String key(EconomyState.VillageRecord v,EconomyState.VillageProject p) {return v.villageId+"/"+p.projectId;}
    static Candidate candidate(ServerLevel level,String key) {
        var map=CANDIDATES.computeIfAbsent(level,k->new LinkedHashMap<>());
        map.entrySet().removeIf(e->level.getGameTime()<e.getValue().tick()||level.getGameTime()-e.getValue().tick()>1200);
        var candidate=map.get(key);
        if(candidate!=null)map.put(key,new Candidate(candidate.site(),candidate.design(),candidate.dialect(),level.getGameTime()));
        return candidate;
    }
    static boolean remember(ServerLevel level,String key,Object site,EconomyState.VillageProject p,String dialect) {
        var map=CANDIDATES.computeIfAbsent(level,k->new LinkedHashMap<>());
        if(map.size()>=8&&!map.containsKey(key))return false;
        map.put(key,new Candidate(site,p.copy(),dialect,level.getGameTime()));
        return true;
    }
    static void forget(ServerLevel level,String key,WalkwayConnections.Request r) {
        var map=CANDIDATES.get(level);if(map!=null)map.remove(key);
        WalkwayConnections.forgetPreview(level,r);
    }
    /** Cheap conservative trigger. The subsequent A* can choose a dry detour instead. */
    static boolean needsSurvey(ServerLevel level,WalkwayConnections.Request r) {
        int dx=r.destination().getX()-r.start().getX(),dz=r.destination().getZ()-r.start().getZ();
        int steps=Math.max(Math.abs(dx),Math.abs(dz));
        if(steps==0)return false;
        if(steps>384)return true;
        for(int i=0;i<=steps;i++) {
            int x=r.start().getX()+(int)Math.round((double)dx*i/steps);
            int z=r.start().getZ()+(int)Math.round((double)dz*i/steps);
            if(!level.hasChunk(x>>4,z>>4))return true;
            int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
            for(int h=0;h<=2;h++)if(VillageBridgeSurvey.water(level.getBlockState(new BlockPos(x,y-h,z))))return true;
        }
        return false;
    }
    static String dependencies(WalkwayConnections.Preview preview) {
        return String.join(",",preview.bridges().stream().map(VillageBridgeLedger.Plan::id).distinct().toList());
    }
    /** A checkpointed bridge reservation precedes the atomic economy lot/dependency save. */
    static boolean reserve(ServerLevel level,VillageBridges.Context c,WalkwayConnections.Preview preview) {
        if(!preview.ready()||!EconomyState.validBridgeDependencies(dependencies(preview)))return false;
        if(preview.bridges().isEmpty())return true;
        if(level.noSave()||!VillageBridges.reserveAll(level,c,preview.bridges().stream().map(VillageBridgeLedger.Plan::destinationBound).toList()))return false;
        try {level.getDataStorage().saveAndJoin();return true;}
        catch(RuntimeException failure) {return false;}
    }
    /** Orphan plans never spend: only the project with a durable dependency can advance them. */
    static void discardUnfunded(ServerLevel level,UUID village,long project) {
        var ledger=VillageBridgeLedger.get(level);
        var ids=ledger.jobs.values().stream().filter(j->j.plan().village().equals(village.toString())
                &&j.plan().project()==project).map(j->j.plan().id()).toList();
        ids.forEach(ledger::removeUnfunded);
    }
    static boolean open(ServerLevel level,EconomyState.VillageProject p) {
        if(p.bridgeFirstDependencies.isEmpty())return true;
        var ledger=VillageBridgeLedger.get(level);
        for(String id:p.bridgeFirstDependencies.split(",")) {
            var job=ledger.jobs.get(id);if(job==null||!job.done()
                    ||(job.plan().requiresDestination()&&job.plan().project()!=p.projectId))return false;
            // A completed receipt alone cannot waive a harvested/changed crossing.
            for(long at:job.plan().route())if(!VillageBridges.walkable(level,job.plan().owner(),BlockPos.of(at)))return false;
        }
        return true;
    }
    static int advance(ServerLevel level,EconomyState.VillageProject p,WalkwayConnections.Request r,
            VillageBridges.Context c,int budget,boolean forced) {
        long deadline=System.nanoTime()+2_000_000L;int writes=0;
        for(String id:p.bridgeFirstDependencies.split(",")) {
            var job=VillageBridgeLedger.get(level).jobs.get(id);
            if(job==null||job.altered())return writes;
            if(job.done())continue;
            for(int n=0;n<(forced?32:1)&&writes<budget;n++) {
                if(n>0&&System.nanoTime()>=deadline)break;
                writes+=VillageBridges.advance(level,r,c,id,budget-writes);
                if(VillageBridgeLedger.get(level).jobs.get(id).done())break;
            }
            return writes;
        }
        return writes;
    }
    static Map<String,Object> report(ServerLevel level,EconomyState.VillageProject p) {
        if(p.bridgeFirstDependencies.isEmpty())return Map.of("required",false);
        for(String id:p.bridgeFirstDependencies.split(",")) {
            var job=VillageBridgeLedger.get(level).jobs.get(id);
            if(job==null)return Map.of("required",true,"status","missing_saved_crossing","id",id);
            if(job.done())for(long at:job.plan().route()) {
                BlockPos pos=BlockPos.of(at);
                if(!VillageBridges.walkable(level,job.plan().owner(),pos))return Map.of("required",true,
                        "status",VillageBridges.loaded(level,pos)?"completed_crossing_changed":"chunks_not_loaded",
                        "id",id,"position",pos.toShortString(),"note","Preserving edits; destination remains closed");
            }
        }
        return Map.of("required",true,"crossings",Arrays.stream(p.bridgeFirstDependencies.split(","))
                .map(id->VillageBridges.report(level,id)).toList());
    }
}
