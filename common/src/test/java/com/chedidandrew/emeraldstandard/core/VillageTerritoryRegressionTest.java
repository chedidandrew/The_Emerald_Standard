package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

public final class VillageTerritoryRegressionTest {
    static long pos(int x,int z){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|64;}
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var state=EconomyState.fresh(99,0,0);
        var a=state.village(new UUID(0,1));var b=state.village(new UUID(0,2));
        a.organicTerritory=b.organicTerritory=true;a.centerPos=pos(-80,0);b.centerPos=pos(80,0);
        VillageTerritory.seed(a,state.villages.values());VillageTerritory.seed(b,state.villages.values());
        check(a.territoryCells.size()>20,"starter territory missing");
        for(int x=-20;x<20;x++)for(int z=-20;z<20;z++)
            check(!(VillageTerritory.mayOwn(a,state.villages.values(),VillageTerritory.key(x,z))
                    &&VillageTerritory.mayOwn(b,state.villages.values(),VillageTerritory.key(x,z))),"overlapping ownership");
        check(VillageTerritory.plan(a,state.villages.values(),pos(32,0),pos(48,16))==null,"cross-boundary lot admitted");
        check(VillageTerritory.plan(a,state.villages.values(),pos(-1500,0),pos(-1480,16))==null,"disconnected outpost admitted");
        VillageTerritory.seedNatural(a,state.villages.values(),Set.of(VillageTerritory.key(-12,0),VillageTerritory.key(3,0)));
        check(a.territoryCells.contains(VillageTerritory.key(-12,0)), "original distant village footprint excluded");
        check(!a.territoryCells.contains(VillageTerritory.key(3,0)), "original footprint crosses neighbor boundary");
        int previous=a.territoryCells.size();
        for(int i=0;i<10;i++) {
            int x=-128-i*32;
            var extension=VillageTerritory.plan(a,state.villages.values(),pos(x,0),pos(x+18,20));
            check(extension!=null,"connected outward growth failed "+i);
            a.territoryCells.addAll(extension);
        }
        check(a.territoryCells.size()>previous+10,"territory did not grow");
        var visible=VillageTerritory.visible(a,state.villages.values());
        Set<Long> reachable=new HashSet<>();ArrayDeque<Long> q=new ArrayDeque<>();
        q.add(visible.iterator().next());
        while(!q.isEmpty()){long c=q.remove();if(!reachable.add(c))continue;for(long n:VillageTerritory.neighbors(c))if(visible.contains(n)&&!reachable.contains(n))q.add(n);}
        check(reachable.size()==visible.size(),"disconnected territory");
        var bounds=VillageDistrictCoverage.of(a);
        check((long)(bounds.maxX()-bounds.minX()+1)*(bounds.maxZ()-bounds.minZ()+1)>visible.size()*256L,"territory still a bounding rectangle");
        check(VillageTerritory.outlines(visible).stream().anyMatch(r->r[4]==1),"no border edges");
        var page=VillageDistrictMap.collect(state,a.villageId,0);
        check(page.districts()==2 && page.markers().stream().anyMatch(m->m.kind()==VillageDistrictMap.TERRITORY),"neighbor map missing territories");
        int[] encoded=VillageDistrictMap.encode(page,1);
        check(page.equals(VillageDistrictMap.decode(i->encoded[i])),"territory packet round trip");
        check(VillageTerritory.candidates(a,0).size()<=256,"unbounded planning sweep");
        a.projects.clear();
        check(VillageProsperityEngine.forceDevelopment(a,1), "first debug project missing");
        check(VillageProsperityEngine.forceDevelopment(a,1), "one stuck lot blocks independent work");
        check(!VillageProsperityEngine.forceDevelopment(a,1), "unbounded debug backlog");
        a.projects.clear();
        for(int i=0;i<20;i++){
            check(VillageProsperityEngine.forceDevelopment(a,1),"single village stopped at old project cap");
            a.projects.getLast().materializedComplete=true;
        }
        check(a.projects.size()==20 && state.villages.size()==2,"growth created districts");
        a.population=80;a.housingCapacity=100;a.foodSupply=2000;a.safety=a.prosperity=100;
        VillageImmigration.advance(a,2,false,false);
        check(a.population>80,"immigration stopped at old 64-resident cap");
        // The growth probe above bypasses world placement; do not serialize its synthetic completions.
        a.projects.clear();
        var normal = new EconomyState.VillageRecord();
        normal.villageId = new UUID(8, 8); normal.organicTerritory = true;
        normal.population = 120; normal.housingCapacity = 120;
        normal.prosperity = normal.safety = 100; normal.lifecycle = VillageProsperityEngine.Lifecycle.ACTIVE;
        for (int day = 1; day <= 400; day++) {
            normal.foodSupply = 10000; normal.materialSupply = normal.treasury = 10000;
            VillageProsperityEngine.advanceOneDay(normal, 79, day, true, false, true);
        }
        check(normal.projects.size() > 12, "ordinary growth stopped at old project cap: " + normal.projects.size());
        check(normal.projects.stream().filter(p -> p.type == VillageProsperityEngine.ProjectType.WAREHOUSE).count() > 1,
                "population-scaled utility still treated as unique");
        state.economicDay=2;state.liveMarket=LiveMarket.adopt(state);
        var shadow=VillageProsperityEngine.captureMarketShadow(a,2,14);
        check(shadow!=null && shadow.recoveryPopulation>64,"large natural village market shadow");
        state.villageMarketShadows.put(a.villageId,shadow);
        var file=Files.createTempDirectory("tes-territory-").resolve("economy.properties");
        state.save(file);
        var reloaded=EconomyState.load(file,99,0,0);
        check(reloaded.villages.get(a.villageId).territoryCells.equals(a.territoryCells),"territory lost on restart");
        check(reloaded.villages.get(a.villageId).organicTerritory,"identity mode lost on restart");
        check(reloaded.villageMarketShadows.get(a.villageId).recoveryPopulation>64,"large-village market shadow lost");
        var service=new EconomyService();var dir=Files.createTempDirectory("tes-natural-");
        service.startWithSeed(dir,99,0,0);
        var observation=new EconomyService.VillageObservation("minecraft:overworld",pos(0,0),0,0,4,6,0,false,List.of());
        var one=service.observeNaturalVillage(new UUID(1,2),observation);
        var two=service.observeNaturalVillage(new UUID(1,3),new EconomyService.VillageObservation("minecraft:overworld",pos(32,0),0,0,4,6,0,false,List.of()));
        check(!one.village().villageId.equals(two.village().villageId),"nearby natural villages merged");
        check(service.observeNaturalVillage(new UUID(1,2),observation).village().villageId.equals(one.village().villageId),"natural identity not stable");
        service.configureForcedVillageDevelopment(true);
        check(service.forceVillageDevelopment(one.village().villageId), "natural project admission failed");
        var before = service.villageSnapshot(one.village().villageId).village();
        check(!service.reserveVillageProjectSite(before.villageId, before.projects.getFirst().projectId,
                pos(500,0),pos(500,0),pos(520,20),100), "foreign reservation accepted");
        var after = service.villageSnapshot(before.villageId).village();
        check(after.territoryCells.equals(before.territoryCells) && after.projects.getFirst().originPos==0
                && after.architectureDialect.equals(before.architectureDialect), "rejected reservation mutated territory/project");
        check(service.draftVillageDistrict(one.village().villageId,pos(128,0))==null,"artificial district creation retained");
        check(service.markGeneratedBankRegion(71, pos(-16,16), before.villageId), "initial Bank registration");
        var bank = new BankConstruction(pos(-32,16),pos(-32,16),before.villageId,1,
                List.of(new BankConstruction.Cell(pos(-32,16),"minecraft:air","minecraft:stone")));
        check(!service.reserveBankConstruction(72,bank), "second generated Bank admitted for one village");
        var foreignBank = new BankConstruction(pos(200,0),pos(200,0),two.village().villageId,1,
                List.of(new BankConstruction.Cell(pos(-200,0),"minecraft:air","minecraft:stone")));
        check(!service.reserveBankConstruction(73,foreignBank), "Bank terrain crosses another village's boundary");
        String guide=Files.readString(Path.of("common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        check(guide.contains("One village, growing territory") && guide.contains("one growing territory and Bank")
                && guide.contains("512 simulated residents") && guide.contains("Legacy multi-district"), "Territory handbook drift");
        System.out.println("PASS natural village identity, non-overlapping freeform connected territory, infill/frontier limits, map codec, growth beyond 12 projects/64 residents and restart");
    }
}
