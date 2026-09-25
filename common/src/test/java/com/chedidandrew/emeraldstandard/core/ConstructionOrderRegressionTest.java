package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

public final class ConstructionOrderRegressionTest {
    public static void main(String[] args) throws Exception {
        require(ConstructionOrderState.extend(List.of(),25,100).equals(List.of(25,100)),"preserve old consumed prefix");
        require(ConstructionOrderState.extend(List.of(25,100),100,140).equals(List.of(25,100,140)),"append a frozen upgrade range");
        require(ConstructionOrderState.extend(List.of(),100,100).isEmpty(),"completed historical building remains canonical");
        for(String bad:List.of("1","-1,4","4,3","0,4,4","0,,5","0,2147483648")) {
            try {ConstructionOrderState.decode(bad);throw new AssertionError("accepted malformed order "+bad);}
            catch(IllegalArgumentException expected) { }
        }
        require(!ConstructionOrderState.valid(List.of(0,101),100),"out-of-plan boundary rejected");
        Path dir=Files.createTempDirectory("tes-construction-order-");
        try {
            UUID id=new UUID(21,35); Path file=dir.resolve("the_emerald_standard.properties");
            var state=EconomyState.fresh(11,0,0); var v=state.village(id); v.dimensionKey="minecraft:overworld";
            v.centerPos=pack(100,64,100);v.housingCapacity=4;
            var p=new EconomyState.VillageProject();p.projectId=1;p.type=VillageProsperityEngine.ProjectType.COTTAGE;
            p.totalBlocks=100;p.materializedBlocks=25;p.originPos=v.centerPos;
            p.boundsMinPos=pack(98,60,98);p.boundsMaxPos=pack(112,80,112);p.economicComplete=true;p.economicProgress=1;
            v.projects.add(p);v.projectSerial=1;state.save(file);
            // Load a genuine old-format snapshot without sequence fields.
            var old=RegressionTestSupport.readProperties(file);old.setProperty("format","34");
            old.remove("village."+id+".project.1.construction_order_v1");
            RegressionTestSupport.refreshChecksum(old);RegressionTestSupport.writeProperties(file,old);
            var service=new EconomyService();service.startWithSeed(dir,11,0,0);
            Path journal=file.resolveSibling(file.getFileName()+".village-journal");
            Path savedJournal=dir.resolve("fixture-original-journal");
            if(Files.exists(journal)) Files.move(journal,savedJournal);
            Files.createDirectory(journal); // Only this disposable fixture's journal target is obstructed.
            try {
                require(!service.prepareVillageConstructionOrder(id,1,100),"failed save cannot authorize a new order");
                require(service.villageSnapshot(id).village().projects.getFirst().constructionOrderCuts.isEmpty(),"failed save rolls order back");
            } finally {Files.delete(journal);if(Files.exists(savedJournal))Files.move(savedJournal,journal);}
            require(service.prepareVillageConstructionOrder(id,1,100),"first order frozen durably");
            var loaded=EconomyState.load(file,11,0,0); var restored=loaded.villages.get(id).projects.getFirst();
            require(restored.constructionOrderCuts.equals(List.of(25,100)) && restored.materializedBlocks==25,"journal reload preserves cursor and order");
            require(restored.copy().constructionOrderCuts.equals(restored.constructionOrderCuts),"deep copies retain order");
            require(!service.prepareVillageConstructionOrder(id,1,100),"repeat preparation does not reroll");
            require(service.villageSnapshot(id).village().projects.getFirst().economicProgress==1,"labor unchanged");
            require(service.saveNow(0),"checkpoint");
            require(EconomyState.load(file,11,0,0).villages.get(id).projects.getFirst().constructionOrderCuts.equals(List.of(25,100)),"checkpoint reload");
            var corrupt=RegressionTestSupport.readProperties(file);
            corrupt.setProperty("village."+id+".project.1.construction_order_v1","25,101");
            RegressionTestSupport.refreshChecksum(corrupt);
            Path separate=dir.resolve("invalid.properties");RegressionTestSupport.writeProperties(separate,corrupt);
            try {EconomyState.load(separate,11,0,0);throw new AssertionError("invalid saved boundary accepted");}
            catch(java.io.IOException expected) { }
            corrupt.remove("village."+id+".project.1.construction_order_v1");
            RegressionTestSupport.refreshChecksum(corrupt);RegressionTestSupport.writeProperties(separate,corrupt);
            try {EconomyState.load(separate,11,0,0);throw new AssertionError("missing current order accepted");}
            catch(java.io.IOException expected) { }
            // Real old-format full journal records have no order field. Replay them as canonical,
            // not as corrupt format-35 records; no block prefix is lost or guessed.
            // saveNow may compact the journal, so create an ordinary full record first.
            require(service.markVillageConstructionStarted(id,1),"legacy journal fixture");
            var legacyRecords=new ArrayList<byte[]>();
            for(byte[] bytes:new DurableJournal(journal).read()) {
                var legacy=new Properties();legacy.load(new java.io.ByteArrayInputStream(bytes));legacy.setProperty("format","34");
                legacy.remove("village."+id+".project.1.construction_order_v1");
                var output=new java.io.ByteArrayOutputStream();legacy.store(output,"legacy construction fixture");legacyRecords.add(output.toByteArray());
            }
            new DurableJournal(journal).replace(legacyRecords);
            require(EconomyState.load(file,11,0,0).villages.get(id).projects.getFirst().materializedBlocks==25,"old journal still preserves block progress");
        } finally {RegressionTestSupport.deleteTree(dir);}
        System.out.println("PASS construction sequence state: migration prefix, journal/copy/checkpoint replay, append-only ranges, malformed input and unchanged labor");
    }
    private static long pack(int x,int y,int z) {return ((long)x&0x3ffffff)<<38|((long)z&0x3ffffff)<<12|(y&0xfff);}
    private static void require(boolean pass,String message) {if(!pass)throw new AssertionError(message);}
}
