package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.minecraft.SiteSearchDiagnostics;
import java.nio.file.*;
import java.util.*;

public final class ProjectSiteRetryRegressionTest {
    public static void main(String[] args) throws Exception {
        require(ProjectSiteRetry.deadline(100,1)==300, "first retry is ten seconds");
        require(ProjectSiteRetry.deadline(100,2)==500, "second retry is twenty seconds");
        require(ProjectSiteRetry.deadline(100,Integer.MAX_VALUE)==700, "retry caps at thirty seconds");
        require(ProjectSiteRetry.deadline(Long.MAX_VALUE-1,3)==Long.MAX_VALUE, "no overflow");
        require(ProjectSiteRetry.boundedDeadline(200,700)==700, "polling does not slide a bounded deadline");
        require(ProjectSiteRetry.boundedDeadline(10,24000)==610, "legacy/future deadline is bounded");
        Path dir=Files.createTempDirectory("tes-site-retry-");
        try {
            var state=EconomyState.fresh(17,0,0); UUID id=new UUID(17,7); var v=state.village(id);
            v.dimensionKey="minecraft:overworld"; v.centerPos=pack(100,64,100); v.housingCapacity=4;
            for(int i=1;i<=4;i++) {
                var p=new EconomyState.VillageProject(); p.projectId=i; p.type=VillageProsperityEngine.ProjectType.INN;
                p.economicProgress=1;p.economicComplete=true;p.totalBlocks=360;p.retryAfterGameTick=24000;
                p.materializationFailures=6;p.siteSearchCursor=3;p.constructionStarted=true;
                if(i==2 || i==3) {p.siteSearchCursor=0;p.originPos=pack(120+i*30,64,120);p.materializedBlocks=1;p.boundsMinPos=pack(118+i*30,62,118);p.boundsMaxPos=pack(138+i*30,80,138);}
                if(i==3) p.manualRepairRequired=true;
                if(i==4) {p.abstractOnly=true;p.siteSearchCursor=0;}
                v.projects.add(p);
            }
            v.projectSerial=4; state.save(dir.resolve("the_emerald_standard.properties"));
            var service=new EconomyService();service.startWithSeed(dir,17,0,0);
            require(service.boundVillageProjectSiteRetries(id,100),"legacy search bounded");
            var jobs=service.villageSnapshot(id).village().projects;
            require(jobs.getFirst().retryAfterGameTick==700 && jobs.getFirst().siteSearchCursor==3
                    && jobs.getFirst().materializationFailures==6 && jobs.getFirst().constructionStarted,"clamp preserves search/provenance");
            require(jobs.subList(1,4).stream().allMatch(p->p.retryAfterGameTick==24000),"reserved/repair/abstract jobs untouched");
            require(!service.boundVillageProjectSiteRetries(id,200),"does not reset due time on each pulse");
            var active=jobs.get(1);
            require(service.queueUnfinishedVillageProjectRepair(id,2,0,active.totalBlocks,active.boundsMinPos,active.boundsMaxPos),
                    "unfinished repair is automatic without a player transaction");
            require(!service.queueUnfinishedVillageProjectRepair(id,3,0,jobs.get(2).totalBlocks,
                    jobs.get(2).boundsMinPos,jobs.get(2).boundsMaxPos),"ambiguous/handed-over manual repair cannot gain regeneration authority");
            require(service.updateVillageProjectMaterialization(id,2,active.totalBlocks,active.totalBlocks,true,false),
                    "handover after physical verification");
            require(!service.queueUnfinishedVillageProjectRepair(id,2,0,active.totalBlocks,active.boundsMinPos,active.boundsMaxPos),
                    "completed buildings can never enter finishing repair");
            require(service.saveNow(0),"save bounded retry");
            var reload=new EconomyService();reload.startWithSeed(dir,17,0,0);
            require(reload.villageSnapshot(id).village().projects.getFirst().retryAfterGameTick==700,"restart preserves deadline");
            require(reload.deferVillageProjectMaterialization(id,1,700,true),"failed search deferred");
            var retry=reload.villageSnapshot(id).village().projects.getFirst();
            require(retry.retryAfterGameTick==1300 && retry.siteSearchCursor==0
                    && retry.materializationFailures==7 && retry.originPos==0 && retry.constructionStarted,"short delay preserves old unplaced job authority");
            require(reload.deferVillageProjectMaterialization(id,1,700,true)
                    && reload.villageSnapshot(id).village().projects.getFirst().retryAfterGameTick==1300,"same-tick deferrals cannot accumulate a long delay");
        } finally {RegressionTestSupport.deleteTree(dir);}
        SiteSearchDiagnostics.reset();
        require(Boolean.FALSE.equals(SiteSearchDiagnostics.report("missing").get("observed")),"missing evidence is explicitly unknown");
        for(int i=0;i<40;i++) SiteSearchDiagnostics.record("inn",new SiteSearchDiagnostics.Trial(i,6,i,100,0,10,0,20,
                SiteSearchDiagnostics.Reason.UNLOADED_FOOTPRINT,"unloaded columns"));
        require(((List<?>)SiteSearchDiagnostics.report("inn").get("recentTrials")).size()==16,"bounded trial history");
        SiteSearchDiagnostics.record("inn",new SiteSearchDiagnostics.Trial(45,7,1,120,0,10,64,20,
                SiteSearchDiagnostics.Reason.AVAILABLE,"accepted"));
        require(((Map<?,?>)SiteSearchDiagnostics.report("inn").get("orientationOutcomeCounts")).size()==1,"new sweep resets counts");
        for(int i=0;i<300;i++) SiteSearchDiagnostics.record("other"+i,new SiteSearchDiagnostics.Trial(i,0,1,64,0,0,0,0,
                SiteSearchDiagnostics.Reason.TERRAIN_HEIGHT,"height limit"));
        require(SiteSearchDiagnostics.last("inn")==null,"bounded job retention");
        SiteSearchDiagnostics.reset();require(SiteSearchDiagnostics.last("other299")==null,"no cross-world evidence");
        System.out.println("PASS site-search retries: legacy waits, no sliding/stacking, restart, reserved-site safety and bounded diagnostics");
    }
    private static long pack(int x,int y,int z) {return ((long)x&0x3ffffff)<<38|((long)z&0x3ffffff)<<12|(y&0xfff);}
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
