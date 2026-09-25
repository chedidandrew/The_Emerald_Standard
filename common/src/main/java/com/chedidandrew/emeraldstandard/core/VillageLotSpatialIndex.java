package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Rebuildable horizontal lot index. Retired lots are indexed at their own position, not the hub. */
final class VillageLotSpatialIndex {
    private record Cell(String dimension, int x, int z) { }
    private record Entry(UUID village, String dimension, EconomyService.VillageProjectLot lot) { }
    private final Map<Cell, Set<Entry>> cells = new HashMap<>();
    private final Map<UUID, List<Entry>> villages = new HashMap<>();
    private final Set<Entry> oversized = new HashSet<>();
    private static int x(long p) { return (int)(p >> 38); }
    private static int z(long p) { return (int)(p << 26 >> 38); }
    private static boolean large(Entry e) {
        var l = e.lot;
        return ((long)Math.floorDiv(x(l.boundsMaxPos()),128)-Math.floorDiv(x(l.boundsMinPos()),128)+1)
                * ((long)Math.floorDiv(z(l.boundsMaxPos()),128)-Math.floorDiv(z(l.boundsMinPos()),128)+1) > 4096;
    }
    void rebuild(Map<UUID, EconomyState.VillageRecord> source) {
        cells.clear(); villages.clear(); oversized.clear(); source.values().forEach(this::upsert);
    }
    void upsert(EconomyState.VillageRecord v) {
        Set<EconomyService.VillageProjectLot> lots = new LinkedHashSet<>();
        for (var p : v.projects) {
            if (p.originPos != 0 && (p.boundsMinPos != 0 || p.boundsMaxPos != 0))
                lots.add(new EconomyService.VillageProjectLot(p.boundsMinPos,p.boundsMaxPos));
            for (var r : p.retiredLots) lots.add(new EconomyService.VillageProjectLot(r.boundsMinPos,r.boundsMaxPos));
        }
        var next = lots.stream().map(l -> new Entry(v.villageId,v.dimensionKey,l)).toList();
        var previous = villages.get(v.villageId);
        if (next.equals(previous)) return;
        if (previous != null) previous.forEach(e -> change(e,false));
        next.forEach(e -> change(e,true)); villages.put(v.villageId,next);
    }
    private void change(Entry e, boolean add) {
        if (large(e)) { if(add) oversized.add(e); else oversized.remove(e); return; }
        var l=e.lot;
        for(int cx=Math.floorDiv(x(l.boundsMinPos()),128);cx<=Math.floorDiv(x(l.boundsMaxPos()),128);cx++)
            for(int cz=Math.floorDiv(z(l.boundsMinPos()),128);cz<=Math.floorDiv(z(l.boundsMaxPos()),128);cz++) {
                var key=new Cell(e.dimension,cx,cz);
                if(add) cells.computeIfAbsent(key,k->new HashSet<>()).add(e);
                else { var bucket=cells.get(key); if(bucket!=null){bucket.remove(e);if(bucket.isEmpty())cells.remove(key);} }
            }
    }
    List<EconomyService.VillageProjectLot> near(String dimension,long position,int radius) {
        if(radius<0 || radius>4096) throw new IllegalArgumentException("Lot query radius must be 0..4096");
        int minX=x(position)-radius,maxX=x(position)+radius,minZ=z(position)-radius,maxZ=z(position)+radius;
        Set<Entry> matches=new HashSet<>(oversized);
        for(int cx=Math.floorDiv(minX,128);cx<=Math.floorDiv(maxX,128);cx++)
            for(int cz=Math.floorDiv(minZ,128);cz<=Math.floorDiv(maxZ,128);cz++)
                matches.addAll(cells.getOrDefault(new Cell(dimension,cx,cz),Set.of()));
        return matches.stream().filter(e->e.dimension.equals(dimension))
                .map(Entry::lot).filter(l->x(l.boundsMinPos())<=maxX && x(l.boundsMaxPos())>=minX
                        && z(l.boundsMinPos())<=maxZ && z(l.boundsMaxPos())>=minZ).distinct().toList();
    }
}
