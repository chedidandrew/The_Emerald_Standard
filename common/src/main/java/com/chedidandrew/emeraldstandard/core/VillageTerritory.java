package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Saved 16-block parcels. A village grows only when a connected, preflighted lot is reserved. */
public final class VillageTerritory {
    public static final int CELL = 16, MAX_CELLS = 16384;
    private VillageTerritory() {}
    public static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    public static int cx(long key) { return (int)(key >> 32); }
    public static int cz(long key) { return (int)key; }
    public static int x(long pos) { return (int)(pos >> 38); }
    public static int z(long pos) { return (int)(pos << 26 >> 38); }
    public static long parcel(long pos) { return key(x(pos)>>4,z(pos)>>4); }

    /** Stable watershed: ties are resolved by UUID, independently of discovery/iteration order. */
    public static boolean mayOwn(EconomyState.VillageRecord v, Collection<EconomyState.VillageRecord> neighbors, long cell) {
        int x=cx(cell)*CELL+8,z=cz(cell)*CELL+8;
        long distance=distance(v.centerPos,x,z);
        for(var other:neighbors) {
            if(other.villageId.equals(v.villageId) || !other.dimensionKey.equals(v.dimensionKey)) continue;
            if(!other.organicTerritory) {
                if(other.territoryCells.contains(cell)) return false;
                continue;
            }
            long d=distance(other.centerPos,x,z);
            if(d<distance || (d==distance && other.villageId.compareTo(v.villageId)<0)) return false;
        }
        return true;
    }
    private static long distance(long pos,int x,int z) {
        long dx=(long)x-x(pos),dz=(long)z-z(pos); return dx*dx+dz*dz;
    }
    public static void seed(EconomyState.VillageRecord v,Collection<EconomyState.VillageRecord> neighbors) {
        int x=x(v.centerPos)>>4,z=z(v.centerPos)>>4;
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++)
            if(dx*dx+dz*dz<=10 && mayOwn(v,neighbors,key(x+dx,z+dz))) v.territoryCells.add(key(x+dx,z+dz));
    }
    /** Join original structure-piece parcels to the starter area without disconnected islands. */
    public static void seedNatural(EconomyState.VillageRecord v,Collection<EconomyState.VillageRecord> neighbors,Set<Long> footprint) {
        seed(v,neighbors);
        int centerX=x(v.centerPos)>>4,centerZ=z(v.centerPos)>>4;
        for(long target:new TreeSet<>(footprint)) {
            List<Long> path=new ArrayList<>();
            int px=cx(target),pz=cz(target);
            for(int step=0;step<512;step++) {
                long cell=key(px,pz);
                if(!mayOwn(v,neighbors,cell)) break;
                if(v.territoryCells.contains(cell)) {
                    if((long)v.territoryCells.size()+path.size()<=MAX_CELLS) v.territoryCells.addAll(path);
                    break;
                }
                path.add(cell);
                if(Math.abs((long)px-centerX)>=Math.abs((long)pz-centerZ)) px+=Integer.compare(centerX,px);
                else pz+=Integer.compare(centerZ,pz);
            }
        }
    }
    public static Set<Long> visible(EconomyState.VillageRecord v,Collection<EconomyState.VillageRecord> neighbors) {
        Set<Long> result=new TreeSet<>();
        for(long c:v.territoryCells) if(mayOwn(v,neighbors,c)) result.add(c);
        return result;
    }
    public static boolean contains(EconomyState.VillageRecord v,int x,int z) {
        return v.territoryCells.contains(key(x>>4,z>>4));
    }
    /** All footprint parcels must be on our side; a short connected extension is reserved atomically. */
    public static Set<Long> plan(EconomyState.VillageRecord v,Collection<EconomyState.VillageRecord> neighbors,long low,long high) {
        if(!v.organicTerritory) return Set.of();
        int ax=Math.min(x(low),x(high))>>4,bx=Math.max(x(low),x(high))>>4;
        int az=Math.min(z(low),z(high))>>4,bz=Math.max(z(low),z(high))>>4;
        if((long)(bx-ax+1)*(bz-az+1)>256) return null;
        Set<Long> required=new TreeSet<>();
        for(int x=ax;x<=bx;x++) for(int z=az;z<=bz;z++) {
            long c=key(x,z); if(!mayOwn(v,neighbors,c)) return null; required.add(c);
        }
        Set<Long> held=visible(v,neighbors);
        if(held.isEmpty()) return null;
        // At most 64 blocks beyond existing territory; never create a distant isolated outpost.
        Map<Long,Long> previous=new HashMap<>();
        ArrayDeque<Long> queue=new ArrayDeque<>();
        for(long c:required) { queue.add(c); previous.put(c,c); }
        long meeting=0; boolean found=false;
        for(int depth=0;depth<=4 && !queue.isEmpty() && !found;depth++) {
            int count=queue.size();
            for(int i=0;i<count;i++) {
                long c=queue.remove();
                if(held.contains(c)) { meeting=c; found=true; break; }
                if(depth==4) continue;
                for(long n:neighbors(c)) if(!previous.containsKey(n)&&mayOwn(v,neighbors,n)) {
                    previous.put(n,c); queue.add(n);
                }
            }
        }
        if(!found) return null;
        while(previous.get(meeting)!=meeting) { required.add(meeting); meeting=previous.get(meeting); }
        if((long)v.territoryCells.size()+required.stream().filter(c->!v.territoryCells.contains(c)).count()>MAX_CELLS) return null;
        return required;
    }
    public static long[] neighbors(long c) {
        int x=cx(c),z=cz(c); return new long[]{key(x-1,z),key(x+1,z),key(x,z-1),key(x,z+1)};
    }
    /** Each sweep tries a rotating infill batch before adjacent frontier candidates. */
    public static List<long[]> candidates(EconomyState.VillageRecord v,int failures) {
        Set<Long> held=v.territoryCells;
        Set<Long> frontier=new TreeSet<>();
        for(long c:held) for(long n:neighbors(c)) if(!held.contains(n)) frontier.add(n);
        List<Long> inside=new ArrayList<>(held); inside.sort(Comparator.comparingLong(c->distance(v.centerPos,cx(c)*16+8,cz(c)*16+8)));
        List<Long> outside=new ArrayList<>(frontier);
        List<long[]> out=new ArrayList<>();
        append(out,inside,failures,128); append(out,outside,failures,128);
        return out;
    }
    /** Compact row fills and merged outer edges; no internal parcel grid is drawn. */
    public static List<int[]> outlines(Set<Long> cells) {
        Map<Integer,TreeSet<Integer>> rows=new TreeMap<>(),horizontal=new TreeMap<>(),vertical=new TreeMap<>();
        for(long c:cells) {
            int x=cx(c),z=cz(c);
            rows.computeIfAbsent(z,k->new TreeSet<>()).add(x);
            if(!cells.contains(key(x,z-1))) horizontal.computeIfAbsent(z,k->new TreeSet<>()).add(x);
            if(!cells.contains(key(x,z+1))) horizontal.computeIfAbsent(z+1,k->new TreeSet<>()).add(x);
            if(!cells.contains(key(x-1,z))) vertical.computeIfAbsent(x,k->new TreeSet<>()).add(z);
            if(!cells.contains(key(x+1,z))) vertical.computeIfAbsent(x+1,k->new TreeSet<>()).add(z);
        }
        List<int[]> result=new ArrayList<>();
        runs(rows,0,result);runs(horizontal,1,result);runs(vertical,2,result);
        return result;
    }
    private static void runs(Map<Integer,TreeSet<Integer>> rows,int kind,List<int[]> out) {
        rows.forEach((row,values)-> {
            int start=values.first(),last=start;
            for(int value:values) { if(value>last+1) { run(out,row,start,last,kind);start=value; } last=value; }
            run(out,row,start,last,kind);
        });
    }
    private static void run(List<int[]> out,int row,int start,int last,int kind) {
        if(kind==0) out.add(new int[]{start*16,row*16,last*16+15,row*16+15,0});
        else if(kind==1) out.add(new int[]{start*16,row*16,(last+1)*16,row*16,1});
        else out.add(new int[]{row*16,start*16,row*16,(last+1)*16,1});
    }
    private static void append(List<long[]> out,List<Long> cells,int sweep,int limit) {
        if(cells.isEmpty()) return;
        int start=(int)Math.floorMod((long)sweep*limit,cells.size());
        for(int i=0;i<Math.min(limit,cells.size());i++) {
            long c=cells.get((start+i)%cells.size()); out.add(new long[]{cx(c)*16L+8,cz(c)*16L+8});
        }
    }
}
