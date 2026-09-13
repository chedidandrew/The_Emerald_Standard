package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.minecraft.ForcedDevelopmentWorkBudget;
import java.nio.file.*;
import java.util.*;

public final class ForcedDevelopmentRegressionTest {
    private static long pack(int x,int y,int z) {
        return ((long)(x & 0x3FFFFFF)<<38)|((long)(z & 0x3FFFFFF)<<12)|(y & 0xFFF);
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        budgets(); service(); lots();
        System.out.println("PASS forced development opt-in, economic bypass, persistence, rollback-safe indexing and global budgets");
    }
    private static void budgets() {
        Object server = new Object(); var b = new ForcedDevelopmentWorkBudget();
        b.begin(server,1,100,false);
        check(b.claim(101)==64,"first grant");
        b.begin(server,1,102,false);
        check(b.claim(103)==64,"same-tick budget was reset");
        for(int i=0;i<10000;i++) check(b.claim(104)==0,"extra sites increase budget");
        b.begin(server,2,0,false); check(b.claim(4_000_000)==0,"deadline ignored");
        b.begin(server,3,0,true); check(b.claim(1)==16 && b.claim(2)==0,"lag throttling");
        b.reset(); check(b.claim(0)==0,"reset retained allowance");
        b.begin(new Object(),3,0,false); check(b.claim(1)==64,"new server starved");
        var rotation=new ForcedDevelopmentWorkBudget.Rotation();
        int[][] visits=new int[2][2];
        for(int tick=0;tick<80;tick++) {
            b.begin(server,tick,0,true);
            if(tick%2==0) b.claim(1); // Bank consumes the whole reduced allowance.
            if(b.claim(2)==0) continue;
            int dimension=rotation.next("work-level",2);
            int site=rotation.next("site:"+dimension,2);
            visits[dimension][site]++;
        }
        for(var dimension:visits) for(int total:dimension) check(total==10,"tick parity starved dimension/site");
        rotation.reset(); check(rotation.next("work-level",2)==0,"rotation leaked across worlds");
        var frontiers=new java.util.BitSet();
        for(int i=0;i<32;i++) frontiers.set(rotation.next("frontier",32));
        check(frontiers.cardinality()==32,"frontier failed to inspect all eight directions per district");
    }
    private static void service() throws Exception {
        Path dir=Files.createTempDirectory("tes-forced-development-");
        try {
            UUID id=new UUID(710,810); var state=EconomyState.fresh(14,0,0);
            var v=state.village(id); v.centerPos=pack(0,64,0);
            v.foodSupply=v.materialSupply=v.treasury=v.safety=v.prosperity=0;
            v.expansionMode=VillageExpansion.Mode.PAUSED;
            v.lifecycle=VillageProsperityEngine.Lifecycle.ABANDONED;
            state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService(); economy.startWithSeed(dir,14,0,0);
            check(!economy.forcedVillageDevelopment() && !economy.forceVillageDevelopment(id),"implicit debug opt-in");
            economy.configureVillageProsperity(false,false);
            economy.configureForcedVillageDevelopment(true);
            check(economy.expansionStatus(id).reason()==VillageExpansion.Reason.READY,"economic/pause gates not bypassed");
            check(economy.forceVillageDevelopment(id),"empty abandoned village cannot debug-build");
            var first=economy.developmentVillageSnapshot(id).village();
            check(first.projects.size()==1 && first.projects.getFirst().economicComplete
                    && !first.projects.getFirst().materializedComplete,"economic approval must not pretend physical completion");
            double housing=first.housingCapacity;
            check(!economy.forceVillageDevelopment(id),"duplicate pending project");
            check(economy.developmentVillageSnapshot(id).village().housingCapacity==housing,"duplicate benefit");
            check(first.treasury==0 && first.foodSupply==0 && first.materialSupply==0
                    && first.safety==0 && first.prosperity==0,"debug minted economy/stat balances");
            var restart=new EconomyService(); restart.startWithSeed(dir,14,0,0);
            check(!restart.forcedVillageDevelopment() && restart.developmentVillageSnapshot(id).village().projects.size()==1,
                    "restart lost progress or enabled debug implicitly");
            // New districts remain additive, identity-stable, and do not debit exhausted funds.
            for(int i=0;i<8;i++) {
                var draft=economy.draftVillageDistrict(id,pack(112*(i+1),64,0));
                draft.architectureDialect="plains";
                var p=draft.projects.getFirst();
                p.originPos=pack(112*(i+1),64,1);
                p.boundsMinPos=pack(112*(i+1)-4,60,-4); p.boundsMaxPos=pack(112*(i+1)+20,84,20);
                p.designPlanHash="a".repeat(64); p.trailAnchorSet=true; p.trailAnchorPos=draft.centerPos;
                p.trailTotalBlocks=12;
                economy.villageProjectLotExclusionsNear("minecraft:overworld",draft.centerPos,64); // build index first
                check(economy.commitVillageDistrict(id,i,draft),"debug charter failed: "+economy.lastError());
                check(!economy.commitVillageDistrict(id,i,draft),"duplicate debug charter");
                check(!economy.villageProjectLotExclusionsNear("minecraft:overworld",draft.centerPos,32).isEmpty(),
                        "new charter omitted from live protection index");
            }
            check(economy.expansionStatus(id).districts()==9,"debug district chain capped by economy");
            economy.configureForcedVillageDevelopment(false);
            check(!economy.forceVillageDevelopment(id) && economy.draftVillageDistrict(id,pack(2048,64,0))==null,
                    "disable kept bypass");
            check(economy.expansionStatus(id).districts()==9,"disable removed buildings/districts");
            var capped=v.copy(); capped.projects.clear(); capped.projectSerial=0;
            var kinds=new HashSet<VillageProsperityEngine.ProjectType>();
            for(int i=0;i<12;i++) {
                check(VillageProsperityEngine.forceDevelopment(capped,1),"bounded district catalog ended early");
                var p=capped.projects.getLast(); kinds.add(p.type); p.materializedComplete=true;
            }
            check(!VillageProsperityEngine.forceDevelopment(capped,1) && kinds.size()>=8,"district bound/diversity");
        } finally {
            try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    private static void lots() {
        var source=new LinkedHashMap<UUID,EconomyState.VillageRecord>();
        for(int i=0;i<10000;i++) {
            var v=new EconomyState.VillageRecord(); v.villageId=new UUID(22,i);
            var p=new EconomyState.VillageProject(); p.originPos=pack(i*256,64,0);
            p.boundsMinPos=pack(i*256-4,50,-4); p.boundsMaxPos=pack(i*256+20,80,20);
            v.projects.add(p); source.put(v.villageId,v);
        }
        var index=new VillageLotSpatialIndex(); index.rebuild(source);
        check(index.near("minecraft:overworld",pack(0,64,0),64).size()==1,"distant city lots leak into local query");
        var v=source.values().iterator().next(); var p=v.projects.getFirst();
        p.boundsMinPos=pack(-1000,50,-1000);p.boundsMaxPos=pack(-980,80,-980);
        index.upsert(v);
        check(index.near("minecraft:overworld",pack(0,64,0),64).isEmpty(),"old bounds remained");
        check(index.near("minecraft:overworld",pack(-995,64,-995),1).size()==1,"negative bounds query");
        check(index.near("minecraft:the_nether",pack(-995,64,-995),1).isEmpty(),"cross-dimension protection");
        // A lot far from its owner's center must still protect its actual location.
        v.centerPos=pack(500000,64,500000); index.upsert(v);
        check(index.near("minecraft:overworld",pack(-995,64,-995),1).size()==1,"index incorrectly follows hub");
        p.retiredLots.add(new EconomyState.RetiredProjectLot(pack(-4000,30,99),pack(-3970,99,131)));
        index.upsert(v);
        check(index.near("minecraft:overworld",pack(-3990,64,128),1).size()==1,"retired/cell-boundary lot omitted");
        p.originPos=0; index.upsert(v);
        check(index.near("minecraft:overworld",pack(-995,64,-995),1).isEmpty()
                && index.near("minecraft:overworld",pack(-3990,64,128),1).size()==1,"relocation removed retired protection");
        p.retiredLots.add(new EconomyState.RetiredProjectLot(pack(-100000,30,-100000),pack(100000,99,100000)));
        index.upsert(v);
        check(!index.near("minecraft:overworld",pack(-90000,64,90000),0).isEmpty(),"oversized conservative fallback");
        long started=System.nanoTime();
        for(int i=0;i<1000;i++) index.near("minecraft:overworld",pack(1000,64,0),1024);
        System.out.println("Lot index: 10,000 districts / 1,000 local queries in "+(System.nanoTime()-started)/1_000_000+" ms");
    }
}
