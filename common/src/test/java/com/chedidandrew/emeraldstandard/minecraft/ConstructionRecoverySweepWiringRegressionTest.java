package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.*;

/** Guards the production integration; scan hints must not become completion authority. */
public final class ConstructionRecoverySweepWiringRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]);
        String manager=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"));
        int start=manager.indexOf("boolean resumedSweep = false;");
        int end=manager.indexOf("if (placedThisTick > 0)",start);
        String work=manager.substring(start,end);
        int resume=work.indexOf("index = recovery.resumeAfter(firstPending, constructionTarget)");
        int protection=work.indexOf("VillageDevelopmentProtection.mayPlace");
        int occupancy=work.indexOf("VillageConstructionOccupancy.mayBuild");
        int place=work.indexOf("level.setBlock(target, constructionState, 3)");
        require(resume>=0&&resume<protection&&protection<occupancy&&occupancy<place,"resumed cells retain protection and occupancy before writes");
        require(work.contains("++inspected>4096")&&work.contains("remainingBlockBudget > 0")
                &&work.contains("inspected > 1")&&work.contains("ConstructionTimeRuntime.hasTime()"),"bounded scans retain shared budgets even with no writes");
        int scan=work.indexOf("recovery.scannedThrough(firstPending, index, constructionTarget)");
        int rewind=work.indexOf("if(firstPending>=0) index=firstPending");
        int observe=work.indexOf("recovery.observe(gameTime,index,!blocked&&!entityWait&&index<constructionTarget,false)");
        require(scan>=0&&scan<rewind&&rewind<observe,"scan position cannot be saved as completed prefix");
        require(work.contains("int verifyEnd=complete ? placements.size()")
                &&work.contains("ownership.prepareHandover")&&work.contains("ConstructionRecovery.finish"),"mandatory full finish verification and handover retained");
        require(work.contains("recovery=" )&&work.contains("next scan="),"debug explains support recovery scan");
        System.out.println("PASS recovery sweep wiring: mandatory prefix, protection, occupancy, budgets, full inspection and diagnostics");
    }
    private static void require(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
}
