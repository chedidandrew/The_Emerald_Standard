package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import static com.chedidandrew.emeraldstandard.core.ConstructionWorkBudget.Lane.*;

public final class DistrictGrowthReviewRegressionTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        budget(); finishing(); placement(); housing(); guidance();
        System.out.println("PASS district growth: shared caps/fairness/cold preflight, finishing queue, subparcel sites, residential mix, truthful advice");
    }
    private static void budget() {
        var budget = new ConstructionWorkBudget();
        int banks=0,villages=0;
        // Actual 3 blocks/sec cadence, deliberately not evenly spaced or fixed tick parity.
        for (long tick=1;tick<=4000;tick++) {
            boolean pulse=((tick%20)*3/20)!=(((tick-1)%20)*3/20);
            budget.begin(tick,pulse);
            if (!pulse) continue;
            long now=tick*100_000_000L;
            if(budget.enter(BANK,now)) { banks++; check(budget.claim(100,now)==16,"per-job cap"); }
            if(budget.enter(VILLAGE,now+10_000_000L)) { villages++; budget.claim(100,now+10_000_000L); }
        }
        check(banks>100&&villages>100&&Math.abs(banks-villages)<5,"a fixed first caller starves the other family");
        budget.reset();
        for(int n=1;n<=100;n++) {
            budget.begin(n,true);
            var first=budget.preferred();var second=first==BANK?VILLAGE:BANK;
            check(budget.enter(first,100)&&budget.claim(5,101)==5,"first inexpensive family");
            check(budget.enter(second,102)&&budget.claim(5,103)==5,"idle capacity denied to second family");
        }
        budget.reset();budget.begin(1,true);check(budget.enter(BANK,100),"first admission");
        check(budget.claim(100,10_000_000)==16,"cold preflight must make bounded progress");
        check(budget.claim(100,10_000_001)==0,"late subsequent work must yield");
        budget.begin(2,true);check(budget.enter(BANK,100),"idle other family must not stall work");
        int total=0;for(int n=0;n<10000;n++) total+=budget.claim(100,101);
        check(total==64,"many active sites exceeded shared ceiling");
        budget.begin(3,false);check(!budget.enter(BANK,100),"work outside configured cadence");
        budget.begin(1,true);check(budget.enter(VILLAGE,100),"rewound clock must reset fairness state");
    }
    private static EconomyState.VillageRecord village() {
        var v=new EconomyState.VillageRecord();v.villageId=new UUID(123,456);v.centerPos=packed(0,0);
        v.architectureDialect="taiga";v.architectureCharacter="";v.developmentTier=4;
        v.population=24;v.housingCapacity=24;v.foodSupply=10000;v.safety=v.prosperity=100;
        v.organicTerritory=true;return v;
    }
    private static EconomyState.VillageProject finished(long id) {
        var p=new EconomyState.VillageProject();p.projectId=id;p.originPos=packed((int)id*2,0);
        p.designSchema=VillageArchitecture.MODULAR_SCHEMA;p.economicComplete=p.materializedComplete=true;
        p.trailAnchorSet=p.trailMaterializedComplete=p.entranceApproachComplete=true;
        p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
        p.trailCenterSurfaceVersion=EconomyState.TRAIL_CENTER_SURFACE_VERSION;return p;
    }
    private static void finishing() {
        var v=village();for(int i=1;i<=1000;i++)v.projects.add(finished(i));
        var queue=new VillageFinishingQueue();
        for(int i=1;i<=1000;i++)check(queue.next(v,0).projectId==i,"initial legacy audit order");
        check(!queue.hasWork(v,100),"completed history requeued without outstanding work");
        var p=v.projects.get(79);p.entranceApproachComplete=false;
        check(queue.next(v,200)==p,"unfinished entrance not resumed");
        p.entranceApproachComplete=true;
        check(!queue.hasWork(v,300),"finished entrance polled indefinitely");
        p.manualRepairRequired=true;p.trailMaterializedComplete=false;
        check(!queue.hasWork(v,400),"player-edit protection ignored");
        var view=VillageFinishingQueue.view(v,p);
        check(view.projects.size()==1&&view.projects.getFirst()==p&&view.residents.isEmpty()
                &&view.territoryCells.isEmpty()&&view.architectureDialect.equals(v.architectureDialect),
                "finishing view copied unrelated village history or lost palette");
        queue.reset();check(queue.hasWork(v,0),"restart failed to reconstruct saved work");
    }
    private static long packed(int x,int z) { return ((long)x&0x3ffffffL)<<38 | ((long)z&0x3ffffffL)<<12 | 64; }
    private static void placement() {
        var v=village();VillageTerritory.seed(v,List.of(v));
        var candidates=VillageTerritory.candidates(v,0);
        check(candidates.size()<=256,"unbounded candidate sweep");
        check(candidates.stream().anyMatch(p->Math.floorMod(p[0],16)!=8||Math.floorMod(p[1],16)!=8),"still parcel centers only");
        var second=VillageTerritory.candidates(v,0);
        for(int i=0;i<candidates.size();i++)check(Arrays.equals(candidates.get(i),second.get(i)),"nondeterministic sites");
        var other=village();other.villageId=new UUID(123,457);other.centerPos=packed(96,0);
        check(VillageTerritory.plan(v,List.of(v,other),packed(80,0),packed(96,16))==null,"offsets bypass foreign territory");
    }
    private static void housing() {
        var v=village();int cottages=0,houses=0,inns=0;
        for(int n=0;n<3000;n++) {
            v.projectSerial=n;
            var type=VillageProsperityEngine.residentialChoice(v,199);
            if(type==VillageProsperityEngine.ProjectType.COTTAGE)cottages++;
            if(type==VillageProsperityEngine.ProjectType.HOUSE)houses++;
            if(type==VillageProsperityEngine.ProjectType.INN)inns++;
        }
        check(houses>2000&&cottages>300&&inns==0,"mature nontrading village needs ordinary homes");
        v.projects.add(finished(1));v.projects.getLast().type=VillageProsperityEngine.ProjectType.HOUSE;
        v.projects.add(finished(2));v.projects.getLast().type=VillageProsperityEngine.ProjectType.COTTAGE;
        v.tradeOutput=25;
        check(VillageProsperityEngine.nextProjectPlan(v,199,15).type()==VillageProsperityEngine.residentialChoice(v,199)
                && VillageProsperityEngine.nextProjectPlan(v,199,15).equals(VillageProsperityEngine.nextProjectPlan(v,199,16)),
                "actual crowding selection is not stable across waiting days");
        for(int n=0;n<1000;n++){v.projectSerial=n;if(VillageProsperityEngine.residentialChoice(v,199)==VillageProsperityEngine.ProjectType.INN)inns++;}
        check(inns>50&&inns<250,"trade should permit occasional, not dominant Inns");
        var inn=finished(3);inn.type=VillageProsperityEngine.ProjectType.INN;v.projects.add(inn);
        for(int n=0;n<100;n++){v.projectSerial=n;check(VillageProsperityEngine.residentialChoice(v,199)!=inn.type,"too many Inns for homes");}
        v.developmentTier=1;check(VillageProsperityEngine.residentialChoice(v,199)==VillageProsperityEngine.ProjectType.COTTAGE,"starter home changed");
    }
    private static void guidance() {
        var p=finished(1);p.materializedComplete=false;p.sitePreparationComplete=true;p.totalBlocks=100;p.materializedBlocks=99;
        check(!ConstructionGuidance.project(p,"").contains("inspection"),"99% alone is not evidence of final inspection");
        p.materializedBlocks=100;check(ConstructionGuidance.project(p,"").contains("final inspection"),"handover missing");
        check(ConstructionGuidance.project(p,"blocked").contains("Final inspection pending"),"handover wait hidden by obstruction");
        check(ConstructionGuidance.project(p,"waiting_for_entities").contains("automatically"),"occupancy advice");
        p.manualRepairRequired=true;check(ConstructionGuidance.project(p,"waiting_for_entities").contains("your attention"),"manual repairs take precedence");
        p.manualRepairRequired=false;p.originPos=0;check(ConstructionGuidance.project(p,"blocked").contains("Surveyors"),"old block reason contaminates site search");
    }
}
