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
        budgets(); scheduling(); siteSearch(); wiring(args.length == 0 ? Path.of(".") : Path.of(args[0])); service(); lots(); arrivals();
        System.out.println("PASS forced development opt-in, economic bypass, persistence, rollback-safe indexing and global budgets");
    }
    private static void budgets() {
        Object server = new Object(); var b = new ForcedDevelopmentWorkBudget();
        b.begin(server,1,100,false);
        check(b.claim(101)==64,"first grant");
        b.begin(server,1,102,false);
        check(b.claim(103)==64,"same-tick budget was reset");
        for(int i=0;i<10000;i++) check(b.claim(104)==0,"extra sites increase budget");
        b.begin(server,2,0,false); check(b.hasTime(4_000_000),"healthy search window still only four ms");
        check(b.claim(8_000_000)==0,"deadline ignored");
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

    private static void arrivals() throws Exception {
        Path dir=Files.createTempDirectory("tes-forced-arrivals-");
        try {
            UUID id=new UUID(915,44); var state=EconomyState.fresh(15,0,0); var v=state.village(id);
            v.centerPos=pack(100,64,100); v.dimensionKey="minecraft:overworld"; v.organicTerritory=true;
            v.population=v.observedPopulation=12; v.housingCapacity=40; v.foodSupply=2000;
            v.prosperity=v.safety=90; v.lifecycle=VillageProsperityEngine.Lifecycle.ACTIVE;
            var homes=new ArrayList<Long>(); for(int i=0;i<40;i++)homes.add(pack(100+i,64,100));
            for (long home : homes) {
                long chunk=((long)(VillageTerritory.x(home)>>4)&0xffffffffL)|((long)(VillageTerritory.z(home)>>4)<<32);
                v.housingChunks.computeIfAbsent(chunk, unused -> new ArrayList<>()).add(home);
            }
            for(int i=0;i<6;i++) {
                var p=new EconomyState.VillageProject();p.projectId=i+1;
                p.type=VillageProsperityEngine.ProjectType.HOUSE;p.economicComplete=p.materializedComplete=true;
                p.economicProgress=1;p.materializedBlocks=p.totalBlocks=10;v.projects.add(p);
            }
            v.projectSerial=6; state.save(dir.resolve("the_emerald_standard.properties"));
            var service=new EconomyService();service.startWithSeed(dir,15,0,0);
            check(!service.prepareForcedSettlerArrival(id),"normal mode accelerated immigration");
            service.configureForcedVillageDevelopment(true);
            for(int i=12;i<28;i++) {
                check(service.prepareForcedSettlerArrival(id),"safe housing invitation stalled");
                check(!service.prepareForcedSettlerArrival(id),"duplicate queued invitation");
                var before=service.villageSnapshot(id).village();
                check(before.population==i,"invitation invented a physical resident");
                check(service.claimSettlerArrival(id,new UUID(916,i),homes.get(i),homes.get(i)),"legitimate arrival rejected");
            }
            var grown=service.villageSnapshot(id).village();
            check(grown.population==28 && grown.developmentTier==5,"actual arrivals failed to unlock tier five");
            check(Math.abs(grown.foodSupply-(2000-16*6))<.001,"accelerated arrivals created food");
            check(service.cancelSettlerArrival(id,new UUID(916,27)),"failed insertion could not release claim");
            var rolledBack=service.villageSnapshot(id).village();
            check(rolledBack.population==27 && rolledBack.pendingSettlers==1
                    && !rolledBack.residents.containsKey(new UUID(916,27)),
                    "failed insertion retained resident or lost invitation");
            check(service.claimSettlerArrival(id,new UUID(917,27),homes.get(27),homes.get(27)),
                    "released home could not receive replacement arrival");
            check(service.villageSnapshot(id).village().developmentTier==5,"replacement arrival lost tier eligibility");
            var empty=grown.copy();empty.housingChunks.clear();empty.pendingSettlers=0;
            check(!VillageImmigration.queueAcceleratedArrival(empty),"unsurveyed housing admitted");
            empty.housingChunks.put(0L,homes);empty.foodSupply=0;
            check(!VillageImmigration.queueAcceleratedArrival(empty),"starving town admitted");
            empty.foodSupply=2000;empty.safety=0;empty.observedGuards=0;
            check(!VillageImmigration.queueAcceleratedArrival(empty),"unsafe town admitted");
            empty.safety=90;empty.lifecycle=VillageProsperityEngine.Lifecycle.EXTINCT;
            check(!VillageImmigration.queueAcceleratedArrival(empty),"extinction cooldown bypassed");
            service.configureForcedVillageDevelopment(false);
            check(!service.prepareForcedSettlerArrival(id),"disable retained acceleration");
        } finally {RegressionTestSupport.deleteTree(dir);}
    }
    private static void wiring(Path root) throws Exception {
        for (String loader : List.of("fabric", "neoforge")) {
            String file = loader.equals("fabric") ? "EmeraldStandardFabric.java" : "EmeraldStandardNeoForge.java";
            String source = Files.readString(root.resolve(loader + "/src/main/java/com/chedidandrew/emeraldstandard/" + loader + "/" + file));
            check(source.contains("VillageDevelopmentRuntime.tick("), "loader bypasses shared queue scheduler: " + loader);
            check(!source.contains("VillageProsperityManager.tick(") && !source.contains("VillageBankManager.tick("),
                    "loader restored competing direct tick calls: " + loader);
        }
        String runtime = Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageDevelopmentRuntime.java"));
        check(runtime.indexOf("configureForcedVillageDevelopment(forced)") < runtime.indexOf("runQueues("),
                "first Bank tick may use stale debug mode");
        String lamps=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/WalkwayLighting.java"));
        check(lamps.contains("forcedVillageDevelopment() ? 1 : 20")
                        &&lamps.contains("instant?allowance:Math.min(2, allowance)")
                        &&lamps.contains("instant?32:1")&&lamps.contains("ForcedDevelopmentRuntime::hasTime")
                        &&lamps.contains("before.equals(ledger.jobs.getOrDefault(key,Job.fresh()))"),
                "forced lamps must shed normal pacing without unlimited or spinning work");
        String manager=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"));
        check(manager.contains("FORCED_ROTATION.next(\"search-priority:\"+village.villageId,2)==0")
                        &&manager.contains("forced ? searchFirst ? 0 : remainingBlockBudget")
                        &&manager.contains("ForcedSiteSearchPolicy.candidates(economy.forcedVillageDevelopment())")
                        &&manager.contains("ForcedSiteSearchPolicy.bankSetback(economy.forcedVillageDevelopment())")
                        &&manager.contains("site_reservation_rejected"),"forced search priority/fairness and diagnostic transition not wired");
        check(manager.contains("forced ? roadBudget - connectionWrites : Math.min(2, roadBudget - connectionWrites)")
                        &&manager.contains("if (forced) remainingBlockBudget -= lampWrites;"),
                "lamp bursts must consume remaining shared allowance, not a private instant budget");
    }

    private static void scheduling() {
        Object server = new Object();
        // Reproduce the former real-loader order: project work always claimed before Banks.
        var old = new ForcedDevelopmentWorkBudget();
        int starved = 0;
        for (int tick = 0; tick < 80; tick++) {
            old.begin(server, tick, 0, true);
            old.claim(1);
            if (tick % 2 == 0) starved += old.claim(2);
        }
        check(starved == 0, "fixture must reproduce Bank starvation under reduced budget");
        for (boolean lagging : List.of(false, true)) {
            var budget = new ForcedDevelopmentWorkBudget();
            var rotation = new ForcedDevelopmentWorkBudget.Rotation();
            int[] bankVisits = new int[3], projectVisits = new int[4], callbacks = new int[2];
            for (int tick = 0; tick < 240; tick++) {
                final int currentTick = tick;
                long[] now = {0};
                budget.begin(server, tick, 0, lagging);
                Runnable projects = () -> {
                    callbacks[0]++;
                    if (budget.claim(now[0]) > 0) {
                        projectVisits[rotation.next("projects", 4)]++;
                        now[0] = 9_000_000; // Slow preflight/placement exhausts the shared deadline.
                    }
                };
                Runnable banks = () -> {
                    callbacks[1]++;
                    if (currentTick % 2 == 0 && budget.claim(now[0]) > 0) {
                        bankVisits[rotation.next("banks", 3)]++;
                        now[0] = 9_000_000;
                    }
                };
                ForcedDevelopmentWorkBudget.runQueues(true, tick, projects, banks);
            }
            for (int visits : bankVisits) check(visits == 40, "Bank queue starvation");
            for (int visits : projectVisits) check(visits == 30, "project/dimension parity starvation");
            check(callbacks[0] == 240 && callbacks[1] == 240, "lifecycle callback skipped or doubled");
        }
        List<String> calls = new ArrayList<>();
        for (int tick = 0; tick < 4; tick++)
            ForcedDevelopmentWorkBudget.runQueues(false, tick, () -> calls.add("project"), () -> calls.add("bank"));
        check(calls.equals(List.of("project","bank","project","bank","project","bank","project","bank")),
                "normal mode callback order changed");
        var fallback = new ForcedDevelopmentWorkBudget();
        fallback.begin(server, 0, 0, true);
        int[] granted = {0};
        ForcedDevelopmentWorkBudget.runQueues(true, 0, () -> granted[0] = fallback.claim(1), () -> { });
        check(granted[0] == 16, "empty Bank queue wasted the project grant");
        System.out.println("PASS real queue priority: reduced budget, exhausted deadline, multiple Banks/projects, normal mode and empty queue");
    }

    private static void siteSearch() throws Exception {
        check(ForcedSiteSearchPolicy.candidates(true)==32 && ForcedSiteSearchPolicy.candidates(false)==1,"mode-specific candidate admission");
        check(ForcedSiteSearchPolicy.lotPadding(true)==2 && ForcedSiteSearchPolicy.lotPadding(false)==4,"spacing preference not safety bypass");
        check(!ForcedSiteSearchPolicy.bankSetback(true) && ForcedSiteSearchPolicy.bankSetback(false),"normal Bank setback unchanged");
        var state=EconomyState.fresh(77,0,0);var id=new UUID(77,1);var v=state.village(id);
        v.centerPos=pack(100,64,100);v.dimensionKey="minecraft:overworld";
        for(int i=1;i<=3;i++) {
            var p=new EconomyState.VillageProject();p.projectId=i;p.type=VillageProsperityEngine.ProjectType.COTTAGE;
            p.economicComplete=true;p.economicProgress=1;v.projects.add(p);
        }
        v.projectSerial=3;
        var p=v.projects.getFirst();
        for(int n=0;n<100;n++) {
            p.materializationFailures=n;
            check(ForcedSiteSearchPolicy.frontierDepth(v,p,false)==ArchitectureSiteFit.searchDepth(v,p),"normal frontier changed");
            check(ForcedSiteSearchPolicy.frontierDepth(v,p,true)==Math.min(32,2+n),"forced widening waits for unused decor sweeps");
        }
        check(ForcedSiteSearchPolicy.frontierDepth(v,p,true)<=32,"unbounded frontier");
        Path dir=Files.createTempDirectory("tes-fast-lot-");
        try {
            state.save(dir.resolve("the_emerald_standard.properties"));var e=new EconomyService();e.startWithSeed(dir,77,0,0);
            check(e.reserveVillageProjectSite(id,1,pack(100,64,100),pack(100,63,100),pack(110,74,110),100),"base lot");
            check(!e.reserveVillageProjectSite(id,2,pack(113,64,100),pack(113,63,100),pack(123,74,110),100),"normal four-block buffer");
            e.configureForcedVillageDevelopment(true);
            check(e.reserveVillageProjectSite(id,2,pack(113,64,100),pack(113,63,100),pack(123,74,110),100),"forced tighter spacing failed atomic reservation");
            check(!e.reserveVillageProjectSite(id,3,pack(110,64,100),pack(110,63,100),pack(120,74,110),100),"forced overlap admitted");
            check(!e.reserveVillageProjectSite(id,3,pack(125,64,100),pack(125,63,100),pack(135,74,110),100),"two-block buffer waived");
            check(!e.reserveVillageProjectSite(id,3,pack(110,85,100),pack(110,84,100),pack(120,95,110),100),"vertical overlap loophole");
            e.configureForcedVillageDevelopment(false);
            var reload=new EconomyService();reload.startWithSeed(dir,77,0,0);
            check(!reload.forcedVillageDevelopment() && reload.villageSnapshot(id).village().projects.get(1).originPos==pack(113,64,100),"toggle/restart moved closer legal lot");
            check(ProjectLotSafety.warnings(reload.villageSnapshot(id).village()).isEmpty(),"tighter lots physically overlap");
        } finally {RegressionTestSupport.deleteTree(dir);}
        System.out.println("PASS fast forced lots: candidate/frontier preferences, atomic spacing, overlap/vertical protection, toggle and restart");
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
