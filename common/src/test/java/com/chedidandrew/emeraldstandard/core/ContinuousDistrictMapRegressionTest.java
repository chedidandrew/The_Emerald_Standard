package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import com.chedidandrew.emeraldstandard.client.DistrictMapViewport;
import com.chedidandrew.emeraldstandard.minecraft.BankerAmountSelection;

final class ContinuousDistrictMapRegressionTest {
    static void verify() { geography(); borders(); continent(); requests(); }
    private static void geography() {
        var state=new EconomyState();
        var root=village(state,null,-120000,240000,false);
        root.population=18;root.foodSupply=99;
        var district=village(state,root.villageId,-119900,240100,false);
        village(state,null,-120001,240001,false);
        village(state,root.villageId,0,0,false).dimensionKey="minecraft:the_nether";
        state.generatedBankAnchors.put(1L,pack(-120100,240010));
        state.bankRegionVillageIds.put(1L,root.villageId);
        for(int i=0;i<401;i++) project(district,-119900+i*20,240110);
        district.projects.get(1).materializedComplete=false;district.projects.get(1).manualRepairRequired=true;
        district.projects.add(new EconomyState.VillageProject());
        var index=new VillageDistrictMap.Index(state);
        var first=index.collect(root.villageId,null);
        var wide=index.collect(root.villageId,VillageDistrictMap.View.fit(first.overview()));
        check(wide.summary() && wide.sites()==401 && wide.banks()==1 && wide.districts()==2,"lossless broad view and ownership");
        check(wide.markers().stream().mapToInt(VillageDistrictMap.Marker::value).sum()==401,"summary counts every site");
        Set<Integer> seen=new HashSet<>();boolean blocked=false;
        for(int i=0;i<401;i++) {
            var view=new VillageDistrictMap.View(-119900+i*20,240115,64);
            var map=index.collect(root.villageId,view);
            check(!map.summary() && map.markers().size()<=VillageDistrictMap.MAX_MARKERS,"bounded detailed view");
            long expected=district.projects.stream().filter(p->p.originPos!=0 && bounds(p).intersects(view.bounds())).count();
            var markers=map.markers().stream().filter(m->m.kind()==VillageDistrictMap.PROJECT).toList();
            check(map.sites()==expected && markers.size()==expected,"no visible site omitted while panning");
            markers.forEach(m->seen.add(m.minX()));
            blocked|=markers.stream().anyMatch(m->m.status()==VillageDistrictMap.BLOCKED);
            int[] data=VillageDistrictMap.encode(map,65536+i);
            check(map.equals(VillageDistrictMap.decode(n->data[n])),"signed-coordinate atomic round trip");
            data[0]++;
            check(VillageDistrictMap.decode(n->data[n])==null,"reject partial update");
        }
        check(seen.size()==401 && blocked,"every former page reachable; repair status preserved");
        var empty=index.collect(root.villageId,new VillageDistrictMap.View(1000000,0,64));
        check(empty.markers().isEmpty() && empty.districts()==0,"empty area never displays stale sites");
        check(index.collect(UUID.randomUUID(),null).equals(VillageDistrictMap.EMPTY),"unknown owner");
        district.projects.clear();
        check(index.collect(root.villageId,wide.view()).sites()==401,"index owns stable lightweight snapshot");
        check(new VillageDistrictMap.Index(state).collect(root.villageId,wide.view()).sites()==0,"refresh reflects removals");
        check(root.population==18 && root.foodSupply==99 && state.villages.size()==4,"no economic mutations");
    }
    private static void borders() {
        var state=new EconomyState();var v=village(state,null,0,0,true);
        for(int i=0;i<180;i++) {
            v.territoryCells.add(VillageTerritory.key(i,i));v.territoryCells.add(VillageTerritory.key(i+1,i));
            project(v,i*16+2,i*16+2);
        }
        var index=new VillageDistrictMap.Index(state);
        var wide=index.collect(v.villageId,null);
        check(wide.summary() && wide.sites()==180,"complex district represented, never truncated");
        // Force outline-count overflow even below the wide-zoom threshold.
        var crowded=index.collect(v.villageId,new VillageDistrictMap.View(800,800,1024));
        check(crowded.summary() && crowded.markers().stream().mapToInt(VillageDistrictMap.Marker::value).sum()==crowded.sites(),
                "border detail overflow changes representation without losing sites");
        for(int i=0;i<180;i++) {
            var map=index.collect(v.villageId,new VillageDistrictMap.View(i*16+8,i*16+8,80));
            check(!map.summary(),"nearby irregular border fits in detail");
            check(map.markers().stream().filter(m->m.kind()==VillageDistrictMap.PROJECT).count()==map.sites(),"borders cannot starve sites");
            check(map.markers().stream().anyMatch(m->m.kind()==VillageDistrictMap.TERRITORY),"border travels with buildings");
        }
        var strip=new EconomyState();var s=village(strip,null,0,0,true);
        for(int x=-30;x<=30;x++)for(int z=-1;z<=1;z++)s.territoryCells.add(VillageTerritory.key(x,z));
        var clipped=VillageDistrictMap.collect(strip,s.villageId,new VillageDistrictMap.View(0,0,64));
        check(clipped.markers().stream().noneMatch(m->m.kind()==VillageDistrictMap.TERRITORY
                && m.extra()==1 && m.minX()==m.maxX()),"camera clipping never invents vertical border edges");
    }
    private static void continent() {
        var state=new EconomyState();UUID selected=null;
        for(int i=0;i<500;i++) {
            var v=village(state,null,(i%25)*3000,(i/25)*3000,true);
            if(selected==null)selected=v.villageId;
            project(v,(i%25)*3000+8,(i/25)*3000+8);
        }
        var map=VillageDistrictMap.collect(state,selected,new VillageDistrictMap.View(35000,28000,100000));
        check(map.summary() && map.districts()==500 && map.sites()==500 && map.markers().size()<=96,"bounded geographic clusters");
        check(map.markers().stream().mapToInt(VillageDistrictMap.Marker::extra).sum()==500,"every district counted");
        check(map.markers().stream().mapToInt(VillageDistrictMap.Marker::value).sum()==500,"every building counted");
        check(map.markers().stream().filter(m->m.status()==VillageDistrictMap.CURRENT).count()==1,"current identity survives grouping");
        var near=VillageDistrictMap.collect(state,selected,new VillageDistrictMap.View(72000,57000,80));
        check(!near.summary() && near.sites()==1,"pan beyond old 2048-block window");
    }
    private static void requests() {
        var decoder=new DistrictMapRequest();
        for(int x:new int[]{-30000000,-1,0,30000000}) for(int z:new int[]{-30000000,1,30000000})
            for(int w:new int[]{16,64,30000000}) {
                var view=new VillageDistrictMap.View(x,z,w);int[] buttons=DistrictMapRequest.encode(view);
                check(Arrays.stream(buttons).allMatch(DistrictMapRequest::matches),"request namespace");
                check(decoder.accept(buttons[0])==null && decoder.accept(buttons[1])==null,"partial request cannot move view");
                check(view.equals(decoder.accept(buttons[2])),"complete request round trip");
                check(decoder.accept(buttons[2])==null,"replayed commit rejected");
                for(int b:buttons)check(BankerAmountSelection.decodeButtonId(b)==0
                        && BankerAmountSelection.decodeFundButtonId(b)==0,"cannot become a financial action");
            }
        int[] buttons=DistrictMapRequest.encode(new VillageDistrictMap.View(0,0,64));
        check(decoder.accept(buttons[1])==null && decoder.accept(buttons[2])==null,"out of order rejected");
        decoder.accept(buttons[0]);decoder.reset();
        check(decoder.accept(buttons[1])==null && decoder.accept(buttons[2])==null,"close clears partial request");
        decoder.accept(buttons[0]);decoder.accept(buttons[1]);
        check(decoder.accept(0x48000000)==null,"zero span rejected");
        decoder.accept(0x43ffffff);
        check(decoder.accept(buttons[1])==null && decoder.accept(buttons[2])==null,"out of world coordinate rejected");
        var viewport=new DistrictMapViewport();viewport.pan(Double.MAX_VALUE,-Double.MAX_VALUE);
        check(viewport.request().centerX()==-30000000 && viewport.request().centerZ()==30000000,"camera stays in world");
        viewport.pan(Double.NaN,Double.POSITIVE_INFINITY);
        for(int n=0;n<1000;n++)viewport.zoom(.8);
        check(viewport.request().halfWidth()<=30000000,"world-scale zoom bounded");
        for(int n=0;n<1000;n++)viewport.zoom(1.25);
        check(Double.isFinite(viewport.scale()) && viewport.scale()<=8,"close zoom bounded");
    }
    private static VillageDistrictMap.Bounds bounds(EconomyState.VillageProject p) {
        return new VillageDistrictMap.Bounds(VillageTerritory.x(p.boundsMinPos),VillageTerritory.z(p.boundsMinPos),
                VillageTerritory.x(p.boundsMaxPos),VillageTerritory.z(p.boundsMaxPos));
    }
    private static void project(EconomyState.VillageRecord v,int x,int z) {
        var p=new EconomyState.VillageProject();p.originPos=pack(x,z);
        p.boundsMinPos=p.originPos;p.boundsMaxPos=pack(x+8,z+8);p.materializedComplete=true;v.projects.add(p);
    }
    private static EconomyState.VillageRecord village(EconomyState state,UUID city,int x,int z,boolean organic) {
        var v=new EconomyState.VillageRecord();v.villageId=UUID.randomUUID();v.cityId=city;
        v.centerPos=pack(x,z);v.organicTerritory=organic;state.villages.put(v.villageId,v);return v;
    }
    private static long pack(int x,int z) { return ((long)x&0x3FFFFFF)<<38 | ((long)z&0x3FFFFFF)<<12 | 70; }
    private static void check(boolean b,String message) { if(!b)throw new AssertionError(message); }
}
