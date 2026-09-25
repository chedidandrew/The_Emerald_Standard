package com.chedidandrew.emeraldstandard.core;
import java.util.*;
public final class DashboardEdgeRegressionTest {
    public static void main(String[] args) {
        var simulated=new EconomyState.VillageProject(); simulated.abstractOnly=true; simulated.economicComplete=true;
        check(!VillageConstructionPolicy.needsAttention(simulated),"completed abstract project is not unfinished");
        var building=new EconomyState.VillageProject(); building.originPos=123;
        var search=new EconomyState.VillageProject();
        var repair=new EconomyState.VillageProject(); repair.materializedComplete=true; repair.manualRepairRequired=true;
        var relocating=new EconomyState.VillageProject(); relocating.materializedComplete=true; relocating.relocationPending=true;
        var finished=new EconomyState.VillageProject(); finished.materializedComplete=true;
        var pending=List.of(search,simulated,building,repair,finished,relocating).stream()
            .filter(VillageConstructionPolicy::needsAttention)
            .sorted(Comparator.comparingInt(VillageConstructionPolicy::attentionPriority)).toList();
        check(pending.equals(List.of(repair,relocating,building,search)),"repairs, relocation, active work before searches");
        check(!VillageConstructionPolicy.needsAttention(null),"null is not a project");
        System.out.println("PASS dashboard completed/simulated, repair, relocation and active-work priority");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
