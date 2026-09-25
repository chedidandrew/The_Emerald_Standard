package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

public final class VillageImmigrationRegressionTest {
    public static void main(String[] args) throws Exception {
        var small=healthy(8);var large=healthy(48);
        require(Math.abs(VillageImmigration.dailyRate(small,false)-1.18)<1e-9
                && Math.abs(VillageImmigration.dailyRate(large,false)-3.18)<1e-9,
                "handbook perfect-condition examples match the implementation");
        require(VillageImmigration.dailyRate(large,false)>VillageImmigration.dailyRate(small,false)*2,"larger districts attract groups");
        for(int day=1;day<=10;day++) {
            VillageImmigration.advance(small,day,true,false);
            require(small.pendingSettlers<=8,"offline queue bounded");
        }
        require(small.pendingSettlers==8 && small.population==8,"steady approvals, never synthetic physical residents");
        double progress=small.immigrationProgress;
        VillageImmigration.advance(small,10,true,false);VillageImmigration.advance(small,0,true,false);
        require(small.pendingSettlers==8 && small.immigrationProgress==progress,"duplicate/backward day does not reroll");
        var crowded=healthy(63);
        VillageImmigration.advance(crowded,1,true,true);
        require(crowded.pendingSettlers==1,"economic cap");
        var hungry=healthy(8);hungry.foodSupply=89;
        VillageImmigration.advance(hungry,1,true,false);
        require(hungry.pendingSettlers==0,"food must support the additional resident");
        var paused=healthy(48);paused.expansionMode=VillageExpansion.Mode.PAUSED;
        for(int day=1;day<=100;day++)VillageImmigration.advance(paused,day,true,true);
        require(paused.pendingSettlers==0 && paused.immigrationProgress<1,"paused districts cannot bank a burst");

        Path root=Files.createTempDirectory("tes-immigration-");
        try {
            var state=EconomyState.fresh(811,0,0);
            var v=healthy(8);v.villageId=new UUID(88,1);v.centerPos=pack(8,64,8);v.pendingSettlers=3;
            v.immigrationProgress=.73;state.villages.put(v.villageId,v);
            long home=pack(54,100,8),chunk=chunk(home);
            v.housingChunks.put(chunk,List.of(home));
            var resident=new EconomyState.ResidentRecord();resident.residentId=new UUID(88,2);
            resident.status=VillageProsperityEngine.ResidentStatus.ACTIVE;resident.lastKnownPos=v.centerPos;
            v.residents.put(resident.residentId,resident);
            state.save(root.resolve("the_emerald_standard.properties"));
            var service=new EconomyService();service.configureEconomicClock(false,30);service.startWithSeed(root,811,0,0);
            UUID arrival=new UUID(88,3);
            require(service.claimSettlerArrival(v.villageId,arrival,home,pack(53,100,8)),"durable arrival claim");
            require(!service.claimSettlerArrival(v.villageId,arrival,home,home),"same identity cannot claim twice");
            require(!service.claimSettlerArrival(v.villageId,new UUID(88,4),home,home),"unverified arrival reserves its bed");
            var restarted=new EconomyService();restarted.configureEconomicClock(false,30);restarted.startWithSeed(root,811,0,0);
            var loaded=restarted.villageSnapshot(v.villageId).village();
            require(loaded.population==9 && loaded.pendingSettlers==2 && loaded.immigrationProgress==.73,"journal/restart conserves residents, queue and fractional progress");
            require(loaded.residents.get(arrival).homePos==home && loaded.residents.get(arrival).immigrant,"identity/home persisted");
            require(restarted.observeDistrictResidents(v.villageId,List.of()),"empty partial census");
            loaded=restarted.villageSnapshot(v.villageId).village();
            require(loaded.population==9 && loaded.residents.get(resident.residentId).status==VillageProsperityEngine.ResidentStatus.UNVERIFIED,"absence retains resident; no departure/replacement");
            require(restarted.cancelSettlerArrival(v.villageId,arrival),"definite insertion failure restores claim");
            require(!restarted.cancelSettlerArrival(v.villageId,arrival),"failure rollback idempotent");
            require(restarted.villageSnapshot(v.villageId).village().pendingSettlers==3,"failed arrival restores queue exactly once");
            require(restarted.observeDistrictHousing(v.villageId,Map.of()),"unknown housing scan");
            require(restarted.villageSnapshot(v.villageId).village().housingChunks.get(chunk).contains(home),"unloaded beds retained");
            require(restarted.observeDistrictHousing(v.villageId,Map.of(chunk,List.of())),"loaded empty housing scan");
            require(restarted.villageSnapshot(v.villageId).village().housingChunks.get(chunk).isEmpty(),"confirmed removed bed not retained");

            var other=restarted.observeVillage(new EconomyService.VillageObservation("minecraft:overworld",
                    pack(300,64,8),999L,0,1,4,0,false,List.of()));
            require(restarted.transferResidentHome(v.villageId,other.village().villageId,resident.residentId,pack(301,64,8)),"confirmed home transfer");
            require(!restarted.villageSnapshot(v.villageId).village().residents.containsKey(resident.residentId)
                    && restarted.villageSnapshot(other.village().villageId).village().residents.containsKey(resident.residentId),"one resident has one owner");
            require(!restarted.transferResidentHome(v.villageId,other.village().villageId,resident.residentId,home),"transfer cannot repeat");
        } finally {
            try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
        System.out.println("PASS VillageImmigrationRegressionTest: pacing, caps, clock replay, saved claims, unloaded residents/homes, failure rollback and exclusive transfers");
    }
    private static EconomyState.VillageRecord healthy(int population) {
        var v=new EconomyState.VillageRecord();v.villageId=new UUID(88,population);
        v.population=population;v.observedPopulation=population;v.housingCapacity=v.observedHousingCapacity=64;
        v.foodSupply=10000;v.prosperity=v.safety=100;v.lifecycle=VillageProsperityEngine.Lifecycle.ACTIVE;
        return v;
    }
    private static long pack(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|(y&0xfffL);}
    private static long chunk(long p){return ((long)((int)(p>>38)>>4)&0xffffffffL)|((long)((int)(p<<26>>38)>>4)<<32);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
