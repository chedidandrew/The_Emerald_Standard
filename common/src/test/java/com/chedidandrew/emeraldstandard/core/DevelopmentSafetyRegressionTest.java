package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

public final class DevelopmentSafetyRegressionTest {
    private static final UUID OWNER=new UUID(10,20);
    public static void main(String[] args) throws Exception {
        zonesAndJournal(); recoveryAndFood(); checkpointEpochs(); presentationIndex();
        System.out.println("PASS DevelopmentSafetyRegressionTest: zones, ownership, torn journals, durable district updates, funded one-shot recovery, partial food census");
    }
    private static void presentationIndex() {
        var state = EconomyState.fresh(1, 0, 0); var v = state.village(OWNER);
        v.dimensionKey = "minecraft:overworld"; v.population = 4;
        for (int i = 1; i <= 10000; i++) {
            var p = new EconomyState.VillageProject(); p.projectId = i; p.originPos = pack(i, 64, 0);
            p.materializedComplete = i != 10000; v.projects.add(p);
        }
        var index = new VillageConstructionSites();
        var sites = index.collect(state, v.dimensionKey);
        require(sites.size() == 1 && sites.getFirst().working(), "only unfinished site presented");
        v.expansionMode = VillageExpansion.Mode.PAUSED;
        require(!index.collect(state, v.dimensionKey).getFirst().working(), "paused site stays known without workers hammering");
        v.projects.getLast().materializedComplete = true; index.changed(OWNER);
        require(index.collect(state, v.dimensionKey).isEmpty(), "completed site removed after incremental invalidation");
        // Historical entries may not be traversed on an unchanged presentation pass.
        var historical = v.projects.set(0, null);
        require(index.collect(state, v.dimensionKey).isEmpty(), "cached presentation avoids historical projects");
        v.projects.set(0, historical);
        var added = new EconomyState.VillageProject(); added.projectId = 10001; added.originPos = pack(100,64,100);
        v.projects.add(added);
        require(index.collect(state, v.dimensionKey).size() == 1, "direct project admission invalidates metadata cache");
    }
    private static void zonesAndJournal() throws Exception {
        Path directory=Files.createTempDirectory("tes-development-land-");
        try {
            var land=new DevelopmentLand(directory);
            land.add("home",OWNER,"minecraft:overworld",20,40,-10,-30);
            require(land.excluded("minecraft:overworld",-10,-30,-10,-30),"inclusive reversed negative corner");
            require(land.excluded("minecraft:overworld",-100,0,100,1),"crossing footprint overlaps even if corners outside");
            require(!land.excluded("minecraft:overworld",21,0,40,10),"outside boundary free");
            require(!land.excluded("minecraft:the_nether",0,0,0,0),"dimension scoped");
            long pos=pack(-2,64,-3); land.observe("minecraft:overworld",pos,"minecraft:oak_log[axis=y]");
            land=new DevelopmentLand(directory);
            require(land.nearby("minecraft:overworld",0,0,4).containsKey(pos),"negative chunk evidence survives reload");
            try { land.remove("home",new UUID(1,2),false); throw new AssertionError("non-owner removed zone"); }
            catch(IllegalArgumentException expected) { }
            land.remove("home",OWNER,false);
            require(new DevelopmentLand(directory).zones().isEmpty(),"removal durable");
            Path file=directory.resolve("torn.journal"); var journal=new DurableJournal(file);
            journal.append(new byte[]{1,2,3});
            Files.write(file,new byte[]{0,0,0},StandardOpenOption.APPEND);
            journal=new DurableJournal(file); require(journal.read().size()==1,"incomplete trailing frame ignored");
            journal.append(new byte[]{4}); require(new DurableJournal(file).read().size()==2,"torn tail truncated before append");
            journal.replace(List.of(new byte[]{5})); require(new DurableJournal(file).read().size()==1,"atomic compaction");
            byte[] corrupt=Files.readAllBytes(file); corrupt[8]^=1; Files.write(file,corrupt);
            try { new DurableJournal(file).read(); throw new AssertionError("corruption accepted"); }
            catch(java.io.IOException expected) { }
        } finally { cleanup(directory); }
    }
    private static void recoveryAndFood() throws Exception {
        Path directory=Files.createTempDirectory("tes-development-recovery-");
        try {
            Path file=directory.resolve("the_emerald_standard.properties");
            var state=EconomyState.fresh(11,0,0); var village=state.village(OWNER);
            village.dimensionKey="minecraft:overworld"; village.centerPos=pack(100,64,100);
            village.districtFounding=true; village.pendingSettlers=4; village.housingCapacity=4;
            village.materialSupply=500; village.treasury=100;
            var project=new EconomyState.VillageProject(); project.projectId=1;
            project.type=VillageProsperityEngine.ProjectType.COTTAGE;
            project.totalBlocks=100; project.materializedBlocks=25;
            project.originPos=pack(100,64,100); project.boundsMinPos=pack(98,60,98); project.boundsMaxPos=pack(112,80,112);
            project.economicComplete=true; project.economicProgress=1;
            village.projects.add(project); village.projectSerial=1;
            state.save(file);
            var service=new EconomyService(); service.startWithSeed(directory,11,0,0);
            byte[] snapshot=Files.readAllBytes(file);
            require(service.markVillageConstructionStarted(OWNER,1),"first district journal record");
            require(!service.recoverObstructedFoundingHome(OWNER,1,0),"not relocated on first obstruction");
            for(int i=0;i<299;i++) require(service.observeConstructionObstruction(OWNER,1,20),"observed loaded time");
            require(!service.recoverObstructedFoundingHome(OWNER,1,5980),"full five minute threshold");
            require(service.observeConstructionObstruction(OWNER,1,20),"threshold reached");
            require(service.recoverObstructedFoundingHome(OWNER,1,6000),"funded recovery: "+service.lastError());
            var recovered=service.villageSnapshot(OWNER).village(); var p=recovered.projects.getFirst();
            require(p.originPos==0 && p.materializedBlocks==0 && p.foundingRecoveryUsed && p.retiredLots.size()==1,"partial lot retired, one replacement");
            require(Math.abs(recovered.materialSupply-(500-project.type.materialCost()*.25))<.000001,"consumed materials charged again");
            require(Math.abs(recovered.treasury-(100-project.type.treasuryCost()*.25))<.000001,"no refund for spent construction");
            require(Arrays.equals(snapshot,Files.readAllBytes(file)),"construction update journals district without rewriting snapshot");
            var records=new DurableJournal(file.resolveSibling(file.getFileName()+".village-journal")).read();
            require(records.size()==2 && records.get(1).length<records.get(0).length,"later journal records store only changed fields");
            var replayed=EconomyState.load(file,11,0,0);
            require(replayed.villages.get(OWNER).projects.getFirst().foundingRecoveryUsed,"journal replays without checkpoint");
            require(!service.recoverObstructedFoundingHome(OWNER,1,12000),"no chain of abandoned foundations");
            require(service.observeVillageFoodChunks(OWNER,Map.of(1L,new VillageFoodSupply.ChunkObservation(100,3),2L,new VillageFoodSupply.ChunkObservation(50,1))),"initial chunks");
            require(service.observeVillageFoodChunks(OWNER,Map.of(1L,new VillageFoodSupply.ChunkObservation(0,0))),"observed stripped field");
            recovered=service.villageSnapshot(OWNER).village();
            require(recovered.observedCropUnits==50 && recovered.observedLivestockUnits==1,"unloaded chunk retained, loaded empty chunk removed");
            require(service.saveNow(0),"checkpoint succeeds");
            replayed=EconomyState.load(file,11,0,0);
            require(replayed.villages.get(OWNER).foodChunks.size()==2,"chunk observations persist");
            // The backup snapshot still needs its matching journal epoch after a corrupt primary.
            Files.writeString(file,"corrupt");
            replayed=EconomyState.load(file,11,0,0);
            require(replayed.villages.get(OWNER).projects.getFirst().foundingRecoveryUsed,"backup plus matching journal recovers committed construction");
            var paused=recovered.copy(); paused.expansionMode=VillageExpansion.Mode.PAUSED;
            require(!VillageConstructionPolicy.eligible(paused,p),"paused workers and blocks share eligibility");
            p.manualRepairRequired=true;
            require(!VillageConstructionPolicy.eligible(recovered,p),"repair-required excludes workers");
        } finally { cleanup(directory); }
    }
    private static void checkpointEpochs() throws Exception {
        Path directory=Files.createTempDirectory("tes-checkpoint-epochs-");
        try {
            Path file=directory.resolve("economy.properties");
            var state=EconomyState.fresh(12,0,0); var village=state.village(OWNER);
            village.dimensionKey="minecraft:overworld"; village.treasury=100;
            state.save(file);
            byte[] first=state.persistedFileFingerprint.clone();
            village.treasury=75;
            EconomyPersistence.journalVillage(state,file,village);
            village.treasury=100;
            state.save(file);
            require(!Arrays.equals(first,state.persistedFileFingerprint),"identical state has a fresh checkpoint epoch");
            require(EconomyState.load(file,12,0,0).villages.get(OWNER).treasury==100,"old delta cannot replay over a newer equivalent snapshot");
        } finally { cleanup(directory); }
    }
    private static long pack(int x,int y,int z) { return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|((long)y&4095); }
    private static void cleanup(Path directory) throws Exception {
        try(var paths=Files.walk(directory)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
