package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

/** Repeated flat-world growth: actual candidate ranking and territory admission, synthetic lot clearance. */
public final class CompactVillageGrowthRegressionTest {
    private static long pos(int x,int z) { return ((long)x&0x3ffffffL)<<38 | ((long)z&0x3ffffffL)<<12 | 64; }
    private static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private static EconomyState.VillageRecord village(int x,int z,long seed) {
        var v=new EconomyState.VillageRecord();v.villageId=new UUID(seed,seed+1);
        v.organicTerritory=true;v.centerPos=pos(x,z);return v;
    }
    public static void main(String[] args)throws Exception {
        infill(); shape(); neighborsAndObstacles(); cacheAndLimits(); reservedLots(); persistence(); wiring();
        System.out.println("PASS compact growth: whole-district infill, multi-seed flat growth, neighbors, obstacles, cache, saved cursor and mode wording");
    }
    private static void infill() {
        var v=village(1000,1000,11);VillageTerritory.seed(v,List.of(v));
        for(int i=0;i<40;i++)v.territoryCells.add(VillageTerritory.key(63+i,62));
        var order=VillageSiteCandidates.order(v);
        check(order.infillCandidates()==v.territoryCells.size()*5,"only a prefix of owned parcels is checked");
        Set<Long> visited=new HashSet<>();
        for(int i=0;i<order.size();i++){
            var p=order.get(i);long cell=VillageTerritory.key((int)p[0]>>4,(int)p[1]>>4);
            check(v.territoryCells.contains(cell)==(i<order.infillCandidates()),"frontier before full infill");
            if(i<order.infillCandidates())visited.add(cell);
        }
        check(visited.equals(v.territoryCells),"outer owned gap omitted");
        var gap=order.get(order.infillCandidates()-1);
        int selected=-1;
        for(int i=0;i<order.size();i++)if(Arrays.equals(gap,order.get(i))||i>=order.infillCandidates()){selected=i;break;}
        check(selected==order.infillCandidates()-1,"available late infill lost to frontier");
    }
    private static List<int[]> grow(EconomyState.VillageRecord v,List<EconomyState.VillageRecord> districts,
            int count, boolean obstructEast, boolean mixed) {
        int x=VillageTerritory.x(v.centerPos),z=VillageTerritory.z(v.centerPos);
        var lots=new ArrayList<int[]>();lots.add(new int[]{x-32,z-32,x+32,z+32});
        for(int n=0;n<count;n++){
            boolean placed=false;int width=mixed?12+(n%3)*4:16,depth=mixed?12+(n%4)*4:16;
            for(var center:VillageTerritory.candidates(v,0)){
                int cx=(int)center[0],cz=(int)center[1];
                if(obstructEast&&cx>x+16)continue;
                int[] box={cx-width/2,cz-depth/2,cx+(width-1)/2,cz+(depth-1)/2};
                if(lots.stream().anyMatch(b->box[0]<=b[2]+4&&box[2]>=b[0]-4&&box[1]<=b[3]+4&&box[3]>=b[1]-4))continue;
                var held=VillageTerritory.plan(v,districts,pos(box[0],box[1]),pos(box[2],box[3]));
                if(held==null)continue;
                v.territoryCells.addAll(held);lots.add(box);
                var p=new EconomyState.VillageProject();p.projectId=n+1;p.originPos=pos(box[0],box[1]);
                p.materializedComplete=p.economicComplete=p.trailMaterializedComplete=p.trailAnchorSet=true;
                p.trailAnchorPos=v.centerPos;v.projects.add(p);placed=true;break;
            }
            check(placed,"could not grow on available flat ground at "+n);
        }
        return lots;
    }
    private static void shape() {
        for(int base:new int[]{-1000,0,1000}) for(int seed=0;seed<4;seed++){
            var v=village(base,base,seed);VillageTerritory.seed(v,List.of(v));
            var lots=grow(v,List.of(v),60,false,seed%2==1);
            int minX=lots.stream().mapToInt(b->b[0]).min().orElseThrow(),maxX=lots.stream().mapToInt(b->b[2]).max().orElseThrow();
            int minZ=lots.stream().mapToInt(b->b[1]).min().orElseThrow(),maxZ=lots.stream().mapToInt(b->b[3]).max().orElseThrow();
            int w=maxX-minX+1,d=maxZ-minZ+1;
            check(Math.max(w,d)<360,"runaway tendril: "+w+"x"+d);
            check(Math.max(w,d)<2*Math.min(w,d),"flat town still a narrow chain");
            long west=lots.stream().skip(1).filter(b->(b[0]+b[2])/2<base).count();
            long north=lots.stream().skip(1).filter(b->(b[1]+b[3])/2<base).count();
            check(west>=15&&west<=45&&north>=15&&north<=45,"coordinate-direction bias");
            System.out.println("Flat seed="+seed+" center="+base+": "+w+"x"+d+", west="+west+", north="+north);
        }
    }
    private static void neighborsAndObstacles() {
        var a=village(-96,0,5);var b=village(96,0,6);var districts=List.of(a,b);
        VillageTerritory.seed(a,districts);VillageTerritory.seed(b,districts);
        grow(a,districts,45,false,true);grow(b,districts,45,false,true);
        Set<Long> common=new HashSet<>(a.territoryCells);common.retainAll(b.territoryCells);
        check(common.isEmpty(),"compact ranking crossed a district boundary");
        var c=village(0,0,9);VillageTerritory.seed(c,List.of(c));
        var lots=grow(c,List.of(c),45,true,true);
        check(lots.stream().skip(1).allMatch(l->(l[0]+l[2])/2<=16),"blocked side used");
        check(lots.size()==46,"compact preference prevented terrain-driven asymmetric growth");
    }
    private static void cacheAndLimits() {
        var v=village(0,0,10);
        for(int x=-64;x<64;x++)for(int z=-64;z<64;z++)v.territoryCells.add(VillageTerritory.key(x,z));
        var order=VillageSiteCandidates.order(v);
        check(order.size()<=EconomyState.MAX_PROJECT_SITE_SEARCH_CANDIDATES,"cursor bound too small");
        check(VillageSiteCandidates.order(v.copy())==order,"unchanged snapshot rebuilt ranking");
        long signature=order.signature();
        VillageSiteCandidates.reset();
        check(VillageSiteCandidates.order(v).signature()==signature,"restart changed ordering");
        var anchor=new EconomyState.VillageProject();anchor.originPos=pos(128,128);anchor.materializedComplete=true;v.projects.add(anchor);
        check(VillageSiteCandidates.order(v).signature()!=signature,"changed anchor failed to invalidate cursor");
        check(Arrays.equals(order.get(order.size()-1),order.get(order.size()-1)),"lazy coordinates unstable");
    }
    private static void reservedLots() {
        var v=village(0,0,17);var p=new EconomyState.VillageProject();p.projectId=1;
        p.boundsMinPos=pos(-20,-10);p.boundsMaxPos=pos(-5,10);v.projects.add(p);
        check(!VillageSiteCandidates.reservedCenter(v,2,-10,0),"unreserved lot guessed");
        p.originPos=pos(-20,-10);
        check(VillageSiteCandidates.reservedCenter(v,2,-10,0),"known lot not skipped");
        check(!VillageSiteCandidates.reservedCenter(v,1,-10,0),"own project rejected");
        check(!VillageSiteCandidates.reservedCenter(v,2,-4,0),"clear adjacent gap skipped");
        p.boundsMinPos=p.boundsMaxPos=0;
        check(!VillageSiteCandidates.reservedCenter(v,2,-10,0),"unknown legacy footprint guessed");
    }
    private static void persistence()throws Exception {
        var service=new EconomyService();Path dir=Files.createTempDirectory("tes-compact-growth-");
        service.startWithSeed(dir,99,0,0);
        var observation=new EconomyService.VillageObservation("minecraft:overworld",pos(0,0),0,0,4,6,0,false,List.of());
        var v=service.observeNaturalVillage(new UUID(1,2),observation).village();
        service.configureForcedVillageDevelopment(true);check(service.forceVillageDevelopment(v.villageId),"fixture project");
        long projectId=service.villageSnapshot(v.villageId).village().projects.getFirst().projectId;
        check(service.beginVillageProjectSiteSearch(v.villageId,projectId,12),"begin search");
        check(service.recordVillageProjectSiteSearchProgress(v.villageId,projectId,1200,true),"extended cursor rejected");
        check(service.beginVillageProjectSiteSearch(v.villageId,projectId,12),"same layout");
        var saved=service.villageSnapshot(v.villageId).village().projects.getFirst();
        check(saved.siteSearchCursor==1200&&saved.siteSearchLayoutKey==12,"same layout restarted cursor");
        // Use the real persistence codec, including snapshot copy and fields beyond the old 256 bound.
        var state=EconomyState.fresh(99,0,0);state.villages.put(v.villageId,service.villageSnapshot(v.villageId).village());
        Path file=dir.resolve("roundtrip.properties");state.save(file);
        var reload=EconomyState.load(file,99,0,0).villages.get(v.villageId).projects.getFirst();
        check(reload.siteSearchCursor==1200&&reload.siteSearchLayoutKey==12&&reload.siteSearchSawUnloadedCandidate,"reload lost search");
        check(service.beginVillageProjectSiteSearch(v.villageId,projectId,13),"changed layout");
        saved=service.villageSnapshot(v.villageId).village().projects.getFirst();
        check(saved.siteSearchCursor==0&&!saved.siteSearchSawUnloadedCandidate,"changed layout skipped fresh candidates");
        service.configureForcedVillageDevelopment(false);
        check(service.villageSnapshot(v.villageId).village().projects.getFirst().siteSearchLayoutKey==13,"mode toggle changed search identity");
    }
    private static void wiring()throws Exception {
        String root="common/src/";
        String manager=Files.readString(Path.of(root+"minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"));
        check(manager.contains("PROJECT_SITE_CANDIDATES_PER_PULSE = 1")&&manager.contains("beginVillageProjectSiteSearch")
                &&manager.contains("offsets = new java.util.AbstractList<>")&&manager.contains("skipped < 128"),"bounded production search not wired");
        String screen=Files.readString(Path.of(root+"client/java/com/chedidandrew/emeraldstandard/client/EmeraldSettingsScreen.java"));
        check(screen.contains("return \"Forced instant development\"")&&!screen.contains("DEBUG:"),"setting label");
        String help=Files.readString(Path.of(root+"client/java/com/chedidandrew/emeraldstandard/client/SettingsHelp.java"));
        check(help.contains("Optional accelerated development.")&&!help.contains("DEBUG ONLY"),"setting help");
        String guide=Files.readString(Path.of(root+"main/resources/assets/the_emerald_standard/lang/en_us.json"));
        check(guide.contains("checks the whole owned district")&&guide.contains("Existing buildings stay where they are"),"handbook growth explanation");
    }
}
