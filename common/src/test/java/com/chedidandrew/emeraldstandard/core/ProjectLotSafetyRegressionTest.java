package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

public final class ProjectLotSafetyRegressionTest {
    static long pos(int x,int y,int z) { return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|(y&4095L); }
    static EconomyState.VillageProject project(long id,boolean complete) {
        var p=new EconomyState.VillageProject();p.projectId=id;p.type=VillageProsperityEngine.ProjectType.COTTAGE;
        p.economicComplete=true;p.economicProgress=1;p.totalBlocks=802;
        p.materializedBlocks=complete?802:384;p.materializedComplete=complete;p.constructionStarted=true;
        p.originPos=pos(-237,-60,236);p.boundsMinPos=pos(-237,-64,236);p.boundsMaxPos=pos(-228,-52,251);
        return p;
    }
    public static void main(String[] args) throws Exception {
        var id=new UUID(66,1);var state=EconomyState.fresh(66,0,0);var v=state.village(id);
        v.centerPos=pos(-260,-60,200);v.projectSerial=11;
        var done=project(10,true);done.originPos=pos(-236,-60,236);
        done.boundsMinPos=pos(-236,-64,236);done.boundsMaxPos=pos(-229,-50,251);
        var stale=project(11,false);v.projects.add(done);v.projects.add(stale);
        var neighborId=new UUID(66,2);var neighbor=state.village(neighborId);
        neighbor.centerPos=pos(-200,-60,200);neighbor.projectSerial=1;
        var pending=project(1,false);pending.originPos=0;pending.boundsMinPos=0;pending.boundsMaxPos=0;
        pending.materializedBlocks=0;pending.constructionStarted=false;neighbor.projects.add(pending);
        check(ProjectLotSafety.warnings(v).size()==1,"captured overlap diagnosed");
        check(ProjectLotSafety.losesTo(stale,done)&&!ProjectLotSafety.losesTo(done,stale),"finished site always wins");
        var dir=Files.createTempDirectory("tes-lot-recovery-");state.save(dir.resolve("the_emerald_standard.properties"));
        var service=new EconomyService();service.startWithSeed(dir,66,0,0);service.configureForcedVillageDevelopment(true);
        check(!service.reserveVillageProjectSite(neighborId,1,stale.originPos,stale.boundsMinPos,stale.boundsMaxPos,802),
                "atomic guard includes neighboring district lots in the same dimension");
        check(service.recoverOverlappingVillageProject(id,11,200),"existing stale project recovered");
        var saved=service.villageSnapshot(id).village();var recovered=saved.projects.get(1);
        check(recovered.originPos==0&&!recovered.constructionStarted&&recovered.materializedBlocks==0
                &&recovered.retiredLots.size()==1&&recovered.relocationPending,"old lot retired, no false completion");
        check(saved.projects.getFirst().materializedComplete&&saved.projects.getFirst().originPos==done.originPos,"finished site unchanged");
        check(!service.recoverOverlappingVillageProject(id,11,201),"idempotent recovery");
        check(!service.reserveVillageProjectSite(id,11,stale.originPos,stale.boundsMinPos,stale.boundsMaxPos,802),"retired/finished lot cannot be reused");
        check(!service.reserveVillageProjectSite(id,11,pos(-237,80,236),pos(-237,80,236),pos(-228,90,251),802),"horizontal overlap cannot escape by height");
        long origin=pos(-180,-60,236);
        var prep=new SitePreparationPlan(List.of(new SitePreparationPlan.Cell(origin,"minecraft:grass_block[snowy=false]")));
        check(service.reserveVillageProjectSite(id,11,origin,origin,pos(-170,-50,251),802,
                "",0,0,0,0,0,0,"",prep),"different safe site reserved");
        check(service.recordSitePreparation(id,11,1,false),"partial terrain count updated");
        check(service.recordSitePreparation(id,11,0,false),"actual-world replay can correct stale progress hint");
        check(!service.recordSitePreparation(id,11,2,false),"terrain count cannot exceed plan");
        var reload=new EconomyService();reload.startWithSeed(dir,66,0,0);
        check(reload.villageSnapshot(id).village().projects.get(1).retiredLots.size()==1,"retirement persists");
        check(ProjectLotSafety.warnings(reload.villageSnapshot(id).village()).isEmpty(),"recovery removes active overlap warning");
        var a=project(1,false);var b=project(2,false);
        check(ProjectLotSafety.losesTo(b,a)&&!ProjectLotSafety.losesTo(a,b),"unfinished collision has one deterministic loser");
        // Ordinary mode cannot manufacture a free replacement from an underfunded consumed plan.
        v.materialSupply=0;v.treasury=0;var poor=Files.createTempDirectory("tes-lot-no-funds-");
        state.save(poor.resolve("the_emerald_standard.properties"));var ordinary=new EconomyService();ordinary.startWithSeed(poor,66,0,0);
        check(!ordinary.recoverOverlappingVillageProject(id,11,300),"ordinary replacement respects consumed resources");
        check(ordinary.villageSnapshot(id).village().projects.get(1).originPos==stale.originPos,"failed recovery is non-mutating");
        // Authored mines may use an anchor outside their exact native envelope.
        stale.originPos=pos(-250,-60,230);state.save(poor.resolve("the_emerald_standard.properties"));
        var mineAnchor=new EconomyService();mineAnchor.startWithSeed(poor,66,0,0);mineAnchor.configureForcedVillageDevelopment(true);
        check(mineAnchor.recoverOverlappingVillageProject(id,11,301),"collision recovery uses approved envelope, not outside anchor");
        System.out.println("PASS atomic lot guard and saved-overlap recovery: captured bounds, height, retirement, reload, cursor and funding");
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
