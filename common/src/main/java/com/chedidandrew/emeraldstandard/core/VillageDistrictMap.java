package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Read-only, bounded pages of saved sites. Never reads or loads Minecraft chunks. */
public final class VillageDistrictMap {
    public static final int PAGE_SIZE = 384;
    private static final int HEADER = 8, STRIDE = 11;
    public static final int DATA_SIZE = HEADER + PAGE_SIZE * STRIDE + 1;
    public static final int DISTRICT = 0, BANK = 1, PROJECT = 2, TERRITORY = 3;
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
    }
    public record Page(int number, int total, int districts, int unsited,
                       int focusX, int focusZ, List<Marker> markers) {
        public Page { markers = List.copyOf(markers); }
        public int pages() { return Math.max(1, (int) (((long) total + PAGE_SIZE - 1) / PAGE_SIZE)); }
    }
    public static final Page EMPTY = new Page(0, 0, 0, 0, 0, 0, List.of());

    public static Page collect(EconomyState state, UUID villageId, int page) {
        var selected = state.villages.get(villageId);
        if (selected == null) return EMPTY;
        UUID root = VillageExpansion.rootId(selected);
        Map<UUID, Integer> districts = new LinkedHashMap<>();
        for (var v : state.villages.values())
            if ((root.equals(VillageExpansion.rootId(v)) || selected.organicTerritory && v.organicTerritory
                    && Math.abs((long)x(v.centerPos)-x(selected.centerPos))<=2048
                    && Math.abs((long)z(v.centerPos)-z(selected.centerPos))<=2048)
                    && Objects.equals(selected.dimensionKey, v.dimensionKey))
                districts.put(v.villageId, districts.size() + 1);
        Collector out = new Collector(Math.max(0, page));
        for (var id : districts.keySet()) {
            var v = state.villages.get(id);
            var bounds = VillageDistrictCoverage.of(v);
            out.add(new Marker(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), DISTRICT, districts.get(id),
                    id.equals(villageId) ? CURRENT : BUILT,
                    VillageProsperityEngine.knownResidentCount(v) + v.pendingSettlers,
                    v.organicTerritory ? -1 : VillageProsperityEngine.effectiveHousingCapacity(v), x(v.centerPos), z(v.centerPos)));
            if(v.organicTerritory) for(var r:VillageTerritory.outlines(VillageTerritory.visible(v,state.villages.values())))
                out.add(new Marker(r[0],r[1],r[2],r[3],TERRITORY,districts.get(id),
                        id.equals(villageId)?CURRENT:BUILT,0,r[4],x(v.centerPos),z(v.centerPos)));
        }
        // One marker per owned Bank, including pending Banks not yet registered as generated.
        for (long region : state.generatedBankAnchors.keySet()) addBank(state, districts, out, region);
        for (long region : state.pendingBankConstructions.keySet())
            if (!state.generatedBankAnchors.containsKey(region)) addBank(state, districts, out, region);
        int unsited = 0;
        for (var id : districts.keySet()) {
            for (var project : state.villages.get(id).projects) {
                if (project.abstractOnly) continue;
                if (project.originPos == 0) { if (!project.materializedComplete) unsited++; continue; }
                if (!out.includesNext()) { out.total++; continue; }
                long min = project.boundsMinPos, max = project.boundsMaxPos;
                if (min == 0 && max == 0) min = max = project.originPos;
                int status = project.materializedComplete ? BUILT
                        : project.blocked || project.manualRepairRequired || project.relocationPending ? BLOCKED
                        : project.materializedBlocks > 0 ? BUILDING : PLANNED;
                int progress = project.materializedComplete ? 100 : project.totalBlocks <= 0 ? 0
                        : (int) Math.min(100, (long) project.materializedBlocks * 100 / project.totalBlocks);
                out.add(new Marker(Math.min(x(min), x(max)), Math.min(z(min), z(max)),
                        Math.max(x(min), x(max)), Math.max(z(min), z(max)), PROJECT, districts.get(id),
                        status, progress, project.type.ordinal()));
            }
        }
        int lastPage = Math.max(0, (out.total - 1) / PAGE_SIZE);
        if (out.page > lastPage) return collect(state, villageId, lastPage);
        return new Page(out.page, out.total, districts.size(), unsited,
                x(selected.centerPos), z(selected.centerPos), out.markers);
    }

    private static Marker point(long pos, int kind, int district, int status, int value, int extra) {
        return new Marker(x(pos), z(pos), x(pos), z(pos), kind, district, status, value, extra);
    }
    private static void addBank(EconomyState state, Map<UUID, Integer> districts, Collector out, long region) {
        var pending = state.pendingBankConstructions.get(region);
        UUID owner = pending == null ? state.bankRegionVillageIds.get(region) : pending.villageId();
        Integer district = districts.get(owner);
        if (district == null) return;
        Long anchor = pending == null ? state.generatedBankAnchors.get(region) : pending.bankerAnchor();
        if (anchor == null) return;
        if (!out.includesNext()) { out.total++; return; }
        out.add(point(anchor, BANK, district, pending == null ? BUILT : BUILDING, -1, 0));
    }
    private static int x(long pos) { return (int) (pos >> 38); }
    private static int z(long pos) { return (int) (pos << 26 >> 38); }
    private static final class Collector {
        final int page;
        int total;
        final List<Marker> markers = new ArrayList<>(PAGE_SIZE);
        Collector(int page) { this.page = page; }
        boolean includesNext() { return (long) total / PAGE_SIZE == page; }
        void add(Marker marker) {
            if ((long) total / PAGE_SIZE == page) markers.add(marker);
            total++;
        }
    }

    /** Revision brackets prevent rendering a partially delivered container-data update. */
    public static int[] encode(Page page, int revision) {
        int[] data = new int[DATA_SIZE];
        data[0] = data[DATA_SIZE - 1] = revision;
        data[1] = page.number; data[2] = page.total; data[3] = page.districts;
        data[4] = page.unsited; data[5] = page.focusX; data[6] = page.focusZ;
        data[7] = page.markers.size();
        for (int i = 0; i < page.markers.size(); i++) {
            var m = page.markers.get(i); int start = HEADER + i * STRIDE;
            int[] values = {m.minX, m.minZ, m.maxX, m.maxZ, m.kind, m.district, m.status, m.value, m.extra,
                    m.centerX, m.centerZ};
            System.arraycopy(values, 0, data, start, STRIDE);
        }
        return data;
    }

    public static Page decode(IntUnaryOperator data) {
        if (data.applyAsInt(0) == 0 || data.applyAsInt(0) != data.applyAsInt(DATA_SIZE - 1)) return null;
        int count = data.applyAsInt(7), total = data.applyAsInt(2), page = data.applyAsInt(1);
        if (count < 0 || count > PAGE_SIZE || total < count || page < 0
                || (long) page * PAGE_SIZE + count > total) return null;
        List<Marker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int j = HEADER + i * STRIDE;
            var m = new Marker(data.applyAsInt(j), data.applyAsInt(j + 1), data.applyAsInt(j + 2),
                    data.applyAsInt(j + 3), data.applyAsInt(j + 4), data.applyAsInt(j + 5),
                    data.applyAsInt(j + 6), data.applyAsInt(j + 7), data.applyAsInt(j + 8),
                    data.applyAsInt(j + 9), data.applyAsInt(j + 10));
            if (m.minX > m.maxX || m.minZ > m.maxZ || m.kind < DISTRICT || m.kind > TERRITORY
                    || m.status < BUILT || m.status > CURRENT || m.district < 1) return null;
            markers.add(m);
        }
        return new Page(page, total, data.applyAsInt(3), data.applyAsInt(4),
                data.applyAsInt(5), data.applyAsInt(6), markers);
    }
    private VillageDistrictMap() {}
}
