package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

public final class ArchitectureSiteFitRegressionTest {
    private static long pos(int x,int z) { return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|64; }
    public static void main(String[] args) throws Exception {
        for(int sweep=0;sweep<128;sweep++) {
            check(ArchitectureSiteFit.outdoorLevel(sweep)==sweep%4,"ordered fallback cycle");
            check(ArchitectureSiteFit.frontierDepth(sweep)==1+sweep/4,"full decor restarts at each wider frontier");
        }
        check(ArchitectureSiteFit.frontierDepth(Integer.MAX_VALUE)==32,"bounded overflow-safe expansion");
        var state=EconomyState.fresh(60,0,0);var id=new UUID(60,1);var v=state.village(id);
        v.dimensionKey="minecraft:overworld";v.centerPos=pos(100,100);v.organicTerritory=true;
        v.architectureCharacter="rustic";v.architectureDialect="plains";
        VillageTerritory.seed(v,List.of(v));var held=Set.copyOf(v.territoryCells);
        var old=VillageSiteCandidates.order(v,4);var expanded=VillageSiteCandidates.order(v,8);
        check(expanded.size()>old.size()&&expanded.size()<=VillageSiteCandidates.MAX_CANDIDATES,"search actually exceeds old four-parcel radius");
        check(held.equals(v.territoryCells),"search cannot claim land");
        check(expanded.signature()==VillageSiteCandidates.order(v,8).signature(),"stable search checkpoints");
        check(VillageTerritory.plan(v,List.of(v),pos(-48,100),pos(-32,116))!=null,"distant searched lot has connected extension");
        var foreign=new EconomyState.VillageRecord();foreign.villageId=new UUID(60,2);foreign.dimensionKey=v.dimensionKey;
        foreign.organicTerritory=true;foreign.centerPos=pos(-48,100);
        check(VillageTerritory.plan(v,List.of(v,foreign),pos(-48,100),pos(-32,116))==null,"expanded search cannot cross neighbor boundary");
        var p=new EconomyState.VillageProject();p.projectId=1;p.type=VillageProsperityEngine.ProjectType.COTTAGE;
        int rare=0;
        for(int i=0;i<10000;i++) {
            p.designSeed=i;
            boolean decorated=ArchitectureSiteFit.decorated(v,p);
            if(decorated)rare++;
            check(decorated==ArchitectureSiteFit.decorated(v.copy(),p.copy()),"rare choice stable after copy/reload");
            p.materializationFailures=5;
            check(ArchitectureSiteFit.searchLevel(v,p)==(decorated?1:3),"rare fit versus core-only retries");
            check(ArchitectureSiteFit.searchDepth(v,p)==(decorated?2:6),"core-only search expands without unused yard sweeps");
        }
        check(rare>800&&rare<1200,"rare decoration distribution");
        p.designSeed=0;p.materializationFailures=0;
        p.designSchema=VillageArchitecture.BLUEPRINT_SCHEMA;p.designTemplateId="biome_plains_cottage_hearth_01";
        p.designTemplateRevision=13;p.designPaletteId="balanced";p.designDressingId="prosperous";
        p.designPlanHashVersion=VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION;
        p.designSignature=VillageArchitecture.blueprintSignature(p.type,p.designTemplateId,13,"balanced","prosperous",false);
        p.economicComplete=true;p.economicProgress=1;v.projectSerial=1;v.projects.add(p);
        var terraced=p.copy();terraced.projectId=2;terraced.designTemplateRevision=14;
        terraced.designSignature=VillageArchitecture.blueprintSignature(terraced.type,terraced.designTemplateId,14,"balanced","prosperous",false);
        v.projects.add(terraced);v.projectSerial=2;
        var dir=Files.createTempDirectory("tes-outdoor-fit-");
        try {
            state.save(dir.resolve("the_emerald_standard.properties"));var service=new EconomyService();service.startWithSeed(dir,60,0,0);
            check(!reserve(service,id,-1)&&!reserve(service,id,4),"invalid fits rejected");
            check(service.villageSnapshot(id).village().projects.getFirst().designOutdoorLevel==0,"rejected reservation leaves full plan");
            check(reserve(service,id,3),"core fit can be reserved atomically");
            var saved=service.villageSnapshot(id).village().projects.getFirst();
            check(saved.designOutdoorLevel==3&&saved.designPlanHash.equals("a".repeat(64))&&saved.originPos!=0,"fit and hash frozen together");
            check(!reserve(service,id,0),"cannot enlarge/shrink a placed reservation");
            var reload=new EconomyService();reload.startWithSeed(dir,60,0,0);
            var restored=reload.villageSnapshot(id).village().projects.getFirst();
            service=reload; // Restart owns the new journal epoch; never continue a retired service instance.
            check(restored.designOutdoorLevel==3&&restored.copy().designOutdoorLevel==3&&restored.designPlanHash.equals(saved.designPlanHash),"restart/copy retain chosen fit");
            check(EconomyState.FORMAT_VERSION>=44,"older JAR cannot reinterpret a trimmed approval");
            check(EconomyState.validBridgeDependencies(""),"no crossing default");
            check(!EconomyState.validBridgeDependencies("invalid")&&!EconomyState.validBridgeDependencies(id+","+id),"invalid/duplicate dependencies rejected");
            var terrain=Map.of(pos(5,7)&~4095L,2,pos(6,7)&~4095L,-1);
            check(OutdoorTerrainOffsets.decode(OutdoorTerrainOffsets.encode(terrain)).equals(terrain),"canonical terrain offset codec");
            check(!OutdoorTerrainOffsets.valid(Map.of(pos(5,7)&~4095L,5)),"cliff offsets rejected");
            check(!OutdoorTerrainOffsets.valid(Map.of(pos(5,7),1)),"world-height/noncanonical columns rejected");
            String bridge=new UUID(65,2).toString();
            check(!service.reserveVillageProjectSite(id,2,pos(132,100),pos(130,98),pos(146,114),100,
                    "plains",0,0,pos(124,100),10,0,0,"b".repeat(64),null,3,bridge,terrain),"core cannot acquire yard offsets");
            check(service.villageSnapshot(id).village().projects.get(1).originPos==0L,"rejected terrain/dependencies leave lot untouched");
            check(service.reserveVillageProjectSite(id,2,pos(132,100),pos(130,98),pos(146,114),100,
                    "plains",0,0,pos(124,100),10,0,0,"b".repeat(64),null,0,bridge,terrain),"terrain, lot, hash and bridge dependencies commit together");
            reload=new EconomyService();reload.startWithSeed(dir,60,0,0);
            var recovered=reload.villageSnapshot(id).village().projects.get(1);
            check(recovered.outdoorTerrainOffsets.equals(terrain)&&recovered.bridgeFirstDependencies.equals(bridge)
                    &&recovered.designPlanHash.equals("b".repeat(64)),"terrain and dependency restart/copy: offsets="
                    +recovered.outdoorTerrainOffsets+" expected="+terrain+" dependencies="+recovered.bridgeFirstDependencies
                    +" hash="+recovered.designPlanHash);
            check(EconomyState.FORMAT_VERSION>=45,"older JAR cannot reinterpret terrain/dependencies");
            check(recovered.designTemplateRevision==14&&restored.designTemplateRevision==13,"Both frozen revisions survive reload");
            for(var entry:ApprovedArchitectureCatalog.entries()) for(int revision:List.of(13,14,15)) for(int level=1;level<=3;level++) {
                var full=ApprovedArchitectureCatalog.entry(entry.id(),revision);
                var fit=ApprovedArchitectureCatalog.fittedEntry(entry.id(),revision,level);
                check(fit.beds()==full.beds()&&fit.role().equals(full.role())&&fit.dialect().equals(full.dialect())
                        &&fit.minimumTier()==full.minimumTier()&&fit.width()<=full.width()&&fit.depth()<=full.depth(),"fit preserves services/tier/biome "+entry.id());
            }
        } finally {RegressionTestSupport.deleteTree(dir);}
        System.out.println("PASS outdoor-fit cycles, wider bounded connected search, neighbor protection, atomic selection, restart, immutable reservations and 1125 identities");
    }
    private static boolean reserve(EconomyService service,UUID id,int fit) {
        return service.reserveVillageProjectSite(id,1,pos(100,100),pos(98,98),pos(114,114),100,
                "plains",0,0,pos(90,100),10,0,0,"a".repeat(64),null,fit);
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
