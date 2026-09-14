package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Read-only geographic snapshots. No chunk access or player-visible marker pagination. */
public final class VillageDistrictMap {
    public static final int MAX_MARKERS = 384, WORLD_LIMIT = 30_000_000;
    private static final int HEADER = 18, STRIDE = 11, MAX_DETAIL_CELLS = 2048;
    public static final int DATA_SIZE = HEADER + MAX_MARKERS * STRIDE + 1;
    public static final int DISTRICT = 0, BANK = 1, PROJECT = 2, TERRITORY = 3, SUMMARY = 4;
    public static final int BUILT = 0, BUILDING = 1, PLANNED = 2, BLOCKED = 3, CURRENT = 4;

    public record Marker(int minX, int minZ, int maxX, int maxZ, int kind,
                         int district, int status, int value, int extra, int centerX, int centerZ) {
        public Marker(int minX, int minZ, int maxX, int maxZ, int kind,
                      int district, int status, int value, int extra) {
            this(minX, minZ, maxX, maxZ, kind, district, status, value, extra,
                    (int) (((long) minX + maxX) / 2), (int) (((long) minZ + maxZ) / 2));
        }
        public double x() { return kind == DISTRICT ? centerX : ((double) minX + maxX) / 2; }
        public double z() { return kind == DISTRICT ? centerZ : ((double) minZ + maxZ) / 2; }
        Bounds bounds() { return new Bounds(minX,minZ,maxX,maxZ); }
    }
    public record Bounds(int minX,int minZ,int maxX,int maxZ) {
        public Bounds {
            if (minX>maxX || minZ>maxZ) throw new IllegalArgumentException("Reversed map bounds");
        }
        Bounds union(Bounds b) { return new Bounds(Math.min(minX,b.minX),Math.min(minZ,b.minZ),
                Math.max(maxX,b.maxX),Math.max(maxZ,b.maxZ)); }
        public boolean intersects(Bounds b) {
            return minX<=b.maxX && maxX>=b.minX && minZ<=b.maxZ && maxZ>=b.minZ;
        }
        Bounds clip(Bounds b) { return new Bounds(Math.max(minX,b.minX),Math.max(minZ,b.minZ),
                Math.min(maxX,b.maxX),Math.min(maxZ,b.maxZ)); }
    }
    /** Fixed logical aspect ratio, independent of window/GUI scale. */
    public record View(int centerX,int centerZ,int halfWidth) {
        public View {
            if (Math.abs((long)centerX)>WORLD_LIMIT || Math.abs((long)centerZ)>WORLD_LIMIT
                    || halfWidth<16 || halfWidth>WORLD_LIMIT) throw new IllegalArgumentException("Invalid map view");
        }
        public Bounds bounds() {
            int h=(int)Math.ceil(halfWidth*120.0/210.0);
            return new Bounds(clamp((long)centerX-halfWidth),clamp((long)centerZ-h),
                    clamp((long)centerX+halfWidth),clamp((long)centerZ+h));
        }
        public static View fit(Bounds b) {
            int x=(int)(((long)b.minX+b.maxX)/2),z=(int)(((long)b.minZ+b.maxZ)/2);
            double width=Math.max((long)b.maxX-b.minX+1,((long)b.maxZ-b.minZ+1)*210.0/120.0);
            return new View(clamp(x),clamp(z),(int)Math.clamp(Math.ceil(width*.6),16,WORLD_LIMIT));
        }
    }
    public record Snapshot(View view,int districts,int unsited,int sites,int banks,boolean summary,
                           Bounds focus,Bounds overview,List<Marker> markers) {
        public Snapshot { markers=List.copyOf(markers); }
    }
    private static final Bounds ORIGIN=new Bounds(-64,-64,64,64);
    public static final Snapshot EMPTY=new Snapshot(View.fit(ORIGIN),0,0,0,0,false,ORIGIN,ORIGIN,List.of());

    /** Shared lightweight index: no inventories, frozen blueprints, finances or full village copies. */
    public static final class Index {
        private final Map<UUID,Entry> entries=new LinkedHashMap<>();
        private final List<EconomyState.VillageRecord> neighbors=new ArrayList<>();

        public Index(EconomyState state) {
            if (state==null) return;
            Map<UUID,List<Long>> banks=new HashMap<>();
            state.generatedBankAnchors.forEach((region,anchor)-> {
                if (!state.pendingBankConstructions.containsKey(region))
                    banks.computeIfAbsent(state.bankRegionVillageIds.get(region),k->new ArrayList<>()).add(region);
            });
            state.pendingBankConstructions.forEach((region,p)->banks.computeIfAbsent(p.villageId(),k->new ArrayList<>()).add(region));
            int number=0;
            for (var v:state.villages.values()) {
                int district=++number;
                var copy=new EconomyState.VillageRecord();
                copy.villageId=v.villageId;copy.dimensionKey=v.dimensionKey;copy.centerPos=v.centerPos;
                copy.organicTerritory=v.organicTerritory;copy.territoryCells.addAll(v.territoryCells);
                neighbors.add(copy);
                var coverage=VillageDistrictCoverage.of(v);
                Bounds bounds=new Bounds(coverage.minX(),coverage.minZ(),coverage.maxX(),coverage.maxZ());
                var center=new Marker(bounds.minX,bounds.minZ,bounds.maxX,bounds.maxZ,DISTRICT,district,BUILT,
                        VillageProsperityEngine.knownResidentCount(v)+v.pendingSettlers,
                        v.organicTerritory?-1:VillageProsperityEngine.effectiveHousingCapacity(v),x(v.centerPos),z(v.centerPos));
                List<Marker> sites=new ArrayList<>();int unsited=0;
                for (var p:v.projects) {
                    if (p.abstractOnly) continue;
                    if (p.originPos==0) { if (!p.materializedComplete) unsited++;continue; }
                    long low=p.boundsMinPos,high=p.boundsMaxPos;
                    if (low==0 && high==0) low=high=p.originPos;
                    int status=p.materializedComplete?BUILT:p.blocked||p.manualRepairRequired||p.relocationPending?BLOCKED
                            :p.materializedBlocks>0?BUILDING:PLANNED;
                    int progress=p.materializedComplete?100:p.totalBlocks<=0?0:(int)Math.min(100,(long)p.materializedBlocks*100/p.totalBlocks);
                    var site=new Marker(Math.min(x(low),x(high)),Math.min(z(low),z(high)),Math.max(x(low),x(high)),
                            Math.max(z(low),z(high)),PROJECT,district,status,progress,
                            p.vanillaPlan==null?p.type.ordinal():p.vanillaPlan.labelCode());
                    sites.add(site);bounds=bounds.union(site.bounds());
                }
                for (long region:banks.getOrDefault(v.villageId,List.of())) {
                    var pending=state.pendingBankConstructions.get(region);
                    long pos=pending==null?state.generatedBankAnchors.get(region):pending.bankerAnchor();
                    var bank=new Marker(x(pos),z(pos),x(pos),z(pos),BANK,district,pending==null?BUILT:BUILDING,-1,0);
                    sites.add(bank);bounds=bounds.union(bank.bounds());
                }
                entries.put(v.villageId,new Entry(copy,VillageExpansion.rootId(v),center,bounds,List.copyOf(sites),unsited));
            }
        }

        public Snapshot collect(UUID id,View requested) {
            Entry selected=entries.get(id);
            if (selected==null) return EMPTY;
            List<Entry> eligible=new ArrayList<>();Bounds overview=selected.bounds;
            for (var e:entries.values()) if (e.village.dimensionKey.equals(selected.village.dimensionKey)
                    && (e.root.equals(selected.root) || selected.village.organicTerritory && e.village.organicTerritory)) {
                eligible.add(e);overview=overview.union(e.bounds);
            }
            View view=requested==null?View.fit(selected.bounds):requested;
            Bounds window=view.bounds();List<Marker> detail=new ArrayList<>(),summaries=new ArrayList<>();
            List<Entry> visible=new ArrayList<>();int unsited=0,siteCount=0,bankCount=0;
            boolean summary=view.halfWidth>1024;
            for (var e:eligible) {
                if (!e.bounds.intersects(window)) continue;
                visible.add(e);unsited+=e.unsited;
                int sites=0;
                for (var m:e.sites) if (m.bounds().intersects(window)) {
                    if (m.kind==PROJECT) sites++;else bankCount++;
                    if (detail.size()<MAX_MARKERS+1) detail.add(m);
                }
                siteCount+=sites;
                var m=e.center;
                if (detail.size()<MAX_MARKERS+1) detail.add(new Marker(m.minX,m.minZ,m.maxX,m.maxZ,m.kind,m.district,
                        e==selected?CURRENT:BUILT,m.value,m.extra,m.centerX,m.centerZ));
                Bounds b=e.bounds.clip(window);
                summaries.add(new Marker(b.minX,b.minZ,b.maxX,b.maxZ,SUMMARY,m.district,e==selected?CURRENT:BUILT,sites,1));
            }
            summary|=detail.size()>MAX_MARKERS;
            // Include one parcel of halo: clipping must never invent a district boundary at the camera edge.
            int cellsVisited=0;
            if (!summary) outer: for (var e:visible) {
                if (!e.village.organicTerritory) continue;
                Set<Long> cells=new HashSet<>();
                for (long c:e.village.territoryCells) {
                    int px=VillageTerritory.cx(c)*16,pz=VillageTerritory.cz(c)*16;
                    if ((long)px+31<window.minX || (long)px-16>window.maxX
                            || (long)pz+31<window.minZ || (long)pz-16>window.maxZ) continue;
                    if (++cellsVisited>MAX_DETAIL_CELLS) { summary=true;break outer; }
                    if (VillageTerritory.mayOwn(e.village,neighbors,c)) cells.add(c);
                }
                for (var r:VillageTerritory.outlines(cells)) {
                    if (!new Bounds(r[0],r[1],r[2],r[3]).intersects(window)) continue;
                    detail.add(new Marker(r[0],r[1],r[2],r[3],TERRITORY,e.center.district,
                            e==selected?CURRENT:BUILT,0,r[4]));
                    if (detail.size()>MAX_MARKERS) { summary=true;break outer; }
                }
            }
            return new Snapshot(view,visible.size(),unsited,siteCount,bankCount,summary,selected.bounds,overview,
                    summary?cluster(summaries,window):detail);
        }
    }
    private record Entry(EconomyState.VillageRecord village,UUID root,Marker center,Bounds bounds,List<Marker> sites,int unsited) {}

    /** At continent scale group into at most 12 x 8 summaries; never silently drop a district. */
    private static List<Marker> cluster(List<Marker> summaries,Bounds view) {
        // Group nearby summary points even with few districts, so distant zooms do not stack labels.
        Map<Integer,Marker> bins=new TreeMap<>();
        for (var m:summaries) {
            int x=(int)Math.clamp((m.x()-view.minX)*12/((long)view.maxX-view.minX+1),0,11);
            int z=(int)Math.clamp((m.z()-view.minZ)*8/((long)view.maxZ-view.minZ+1),0,7);
            int key=z*12+x;var previous=bins.get(key);
            if (previous==null) bins.put(key,m);
            else {
                Bounds b=previous.bounds().union(m.bounds());
                bins.put(key,new Marker(b.minX,b.minZ,b.maxX,b.maxZ,SUMMARY,previous.district,
                        previous.status==CURRENT||m.status==CURRENT?CURRENT:BUILT,previous.value+m.value,previous.extra+m.extra));
            }
        }
        return List.copyOf(bins.values());
    }
    public static Snapshot collect(EconomyState state,UUID id,View view) { return new Index(state).collect(id,view); }
    private static int clamp(long v) { return (int)Math.clamp(v,-WORLD_LIMIT,WORLD_LIMIT); }
    private static int x(long pos) { return (int)(pos>>38); }
    private static int z(long pos) { return (int)(pos<<26>>38); }

    /** Revision brackets keep each viewport and its markers atomic over container-data updates. */
    public static int[] encode(Snapshot s,int revision) {
        if (s.markers.size()>MAX_MARKERS) throw new IllegalArgumentException("Unbounded map snapshot");
        int[] data=new int[DATA_SIZE];data[0]=data[DATA_SIZE-1]=revision;
        data[1]=s.view.centerX;data[2]=s.view.centerZ;data[3]=s.view.halfWidth;
        data[4]=s.districts;data[5]=s.unsited;data[6]=s.sites;data[7]=s.banks;data[8]=s.summary?1:0;
        data[9]=s.focus.minX;data[10]=s.focus.minZ;data[11]=s.focus.maxX;data[12]=s.focus.maxZ;
        data[13]=s.overview.minX;data[14]=s.overview.minZ;data[15]=s.overview.maxX;data[16]=s.overview.maxZ;
        data[17]=s.markers.size();
        for (int i=0;i<s.markers.size();i++) {
            var m=s.markers.get(i);
            int[] values={m.minX,m.minZ,m.maxX,m.maxZ,m.kind,m.district,m.status,m.value,m.extra,m.centerX,m.centerZ};
            System.arraycopy(values,0,data,HEADER+i*STRIDE,STRIDE);
        }
        return data;
    }
    public static Snapshot decode(IntUnaryOperator data) {
        if (data.applyAsInt(0)==0 || data.applyAsInt(0)!=data.applyAsInt(DATA_SIZE-1)) return null;
        int count=data.applyAsInt(17);
        if (count<0 || count>MAX_MARKERS) return null;
        for (int i=4;i<=7;i++) if (data.applyAsInt(i)<0) return null;
        if (data.applyAsInt(8)<0 || data.applyAsInt(8)>1) return null;
        try {
            var view=new View(data.applyAsInt(1),data.applyAsInt(2),data.applyAsInt(3));
            var focus=new Bounds(data.applyAsInt(9),data.applyAsInt(10),data.applyAsInt(11),data.applyAsInt(12));
            var overview=new Bounds(data.applyAsInt(13),data.applyAsInt(14),data.applyAsInt(15),data.applyAsInt(16));
            List<Marker> markers=new ArrayList<>(count);
            for (int i=0;i<count;i++) {
                int j=HEADER+i*STRIDE;
                var m=new Marker(data.applyAsInt(j),data.applyAsInt(j+1),data.applyAsInt(j+2),data.applyAsInt(j+3),
                        data.applyAsInt(j+4),data.applyAsInt(j+5),data.applyAsInt(j+6),data.applyAsInt(j+7),
                        data.applyAsInt(j+8),data.applyAsInt(j+9),data.applyAsInt(j+10));
                if (m.minX>m.maxX || m.minZ>m.maxZ || m.kind<DISTRICT || m.kind>SUMMARY
                        || m.status<BUILT || m.status>CURRENT || m.district<1 || m.kind==SUMMARY && (m.value<0 || m.extra<1)) return null;
                markers.add(m);
            }
            return new Snapshot(view,data.applyAsInt(4),data.applyAsInt(5),data.applyAsInt(6),data.applyAsInt(7),
                    data.applyAsInt(8)==1,focus,overview,markers);
        } catch (IllegalArgumentException invalid) { return null; }
    }
    private VillageDistrictMap() {}
}
