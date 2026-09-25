package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Cheap, bounded world-free ranking. Native footprint checks use their own work allowance. */
public final class VillageSiteCandidates {
    public static final int SITES_PER_PARCEL = 5;
    // Hard cap shared by ordinary and wider forced searches; owned parcels always fit first.
    public static final int MAX_CANDIDATES = VillageTerritory.MAX_CELLS * 25;
    private static final int CACHE_LIMIT = 4;
    private static final Map<UUID, Order> CACHE = new LinkedHashMap<>(8, .75f, true);
    private VillageSiteCandidates() {}

    public static synchronized void reset() { CACHE.clear(); }

    /** Positive evidence only: an unreserved/legacy unknown footprint is never guessed. */
    public static boolean reservedCenter(EconomyState.VillageRecord village, long projectId, int x, int z) {
        for (var other : village.projects) {
            if (other.projectId == projectId || other.originPos == 0
                    || (other.boundsMinPos == 0 && other.boundsMaxPos == 0)) continue;
            if (x >= VillageTerritory.x(other.boundsMinPos) && x <= VillageTerritory.x(other.boundsMaxPos)
                    && z >= VillageTerritory.z(other.boundsMinPos) && z <= VillageTerritory.z(other.boundsMaxPos))
                return true;
        }
        return false;
    }

    public static synchronized Order order(EconomyState.VillageRecord village) {
        return order(village, 1);
    }

    public static synchronized Order order(EconomyState.VillageRecord village, int frontierDepth) {
        frontierDepth = Math.max(1, Math.min(4, frontierDepth));
        Set<Long> anchors = new TreeSet<>();
        anchors.add(VillageTerritory.parcel(village.centerPos));
        if (village.bankAnchorPos != 0) anchors.add(VillageTerritory.parcel(village.bankAnchorPos));
        for (var project : village.projects) {
            if (project.originPos == 0 || !project.materializedComplete || project.abstractOnly
                    || project.blocked || project.manualRepairRequired || project.relocationPending) continue;
            anchors.add(VillageTerritory.parcel(project.originPos));
            if (project.trailAnchorSet && project.trailMaterializedComplete)
                anchors.add(VillageTerritory.parcel(project.trailAnchorPos));
        }
        Order cached = CACHE.get(village.villageId);
        if (cached != null && cached.center == village.centerPos
                && cached.frontierDepth == frontierDepth
                && cached.held.equals(village.territoryCells) && cached.anchors.equals(anchors)) return cached;
        Order result = new Order(village, anchors, frontierDepth);
        CACHE.put(village.villageId, result);
        if (CACHE.size() > CACHE_LIMIT) CACHE.remove(CACHE.keySet().iterator().next());
        return result;
    }

    /** Immutable metadata; micro-site coordinates are generated lazily, not allocated per server tick. */
    public static final class Order extends AbstractList<long[]> implements RandomAccess {
        private final long center, signature;
        private final Set<Long> held, anchors;
        private final long[] parcels;
        private final int insideCount;
        private final int frontierDepth;
        private Order(EconomyState.VillageRecord village, Set<Long> anchors, int frontierDepth) {
            if (village.territoryCells.size() > VillageTerritory.MAX_CELLS)
                throw new IllegalArgumentException("Territory exceeds saved parcel limit");
            this.center = village.centerPos;
            this.held = new TreeSet<>(village.territoryCells);
            this.anchors = Set.copyOf(anchors);
            this.frontierDepth = frontierDepth;
            Set<Long> frontier = new HashSet<>();
            Set<Long> edge = held;
            for (int depth = 0; depth < frontierDepth; depth++) {
                Set<Long> nextEdge = new HashSet<>();
                for (long cell : edge) for (long next : VillageTerritory.neighbors(cell))
                    if (!held.contains(next) && frontier.add(next)) nextEdge.add(next);
                edge = nextEdge;
            }
            long seed = mix(village.villageId.getMostSignificantBits() ^ village.villageId.getLeastSignificantBits());
            List<Rank> inside = new ArrayList<>(), outside = new ArrayList<>();
            for (long cell : held) inside.add(rank(cell, seed));
            for (long cell : frontier) outside.add(rank(cell, seed));
            Comparator<Rank> byCost = Comparator.comparingLong(Rank::cost)
                    .thenComparingLong(Rank::tie).thenComparingLong(Rank::cell);
            inside.sort(byCost); outside.sort(byCost);
            int outsideLimit = MAX_CANDIDATES / SITES_PER_PARCEL - inside.size();
            if (outside.size() > outsideLimit) outside.subList(outsideLimit, outside.size()).clear();
            insideCount = inside.size() * SITES_PER_PARCEL;
            parcels = new long[inside.size() + outside.size()];
            int index = 0;
            long hash = mix(center ^ seed ^ 0x54455346494C4C02L);
            for (var group : List.of(inside, outside)) for (Rank ranked : group) {
                parcels[index++] = ranked.cell;
                hash = mix(hash ^ ranked.cell);
            }
            // Includes ranking inputs even when their change happens not to move a parcel.
            for (long anchor : new TreeSet<>(anchors)) hash = mix(hash ^ anchor);
            signature = hash == 0 ? 1 : hash;
        }
        private Rank rank(long cell, long seed) {
            int x = VillageTerritory.cx(cell), z = VillageTerritory.cz(cell);
            long dx = (long)x * 16 + 8 - VillageTerritory.x(center);
            long dz = (long)z * 16 + 8 - VillageTerritory.z(center);
            int adjacent = 0;
            for (long next : VillageTerritory.neighbors(cell)) if (held.contains(next)) adjacent++;
            // A bounded neighborhood lookup rewards nearby buildings/known connection anchors.
            // It is a preference, never evidence that a road or footprint is physically safe.
            int connection = 8;
            for (int ox = -3; ox <= 3; ox++) for (int oz = -3; oz <= 3; oz++)
                if (anchors.contains(VillageTerritory.key(x + ox, z + oz)))
                    connection = Math.min(connection, Math.abs(ox) + Math.abs(oz));
            long cost = dx * dx + dz * dz + (4 - adjacent) * 1024L + connection * 128L;
            long relative = VillageTerritory.key(x - (VillageTerritory.x(center) >> 4),
                    z - (VillageTerritory.z(center) >> 4));
            return new Rank(cell, cost, mix(seed ^ relative));
        }
        public long signature() { return signature; }
        public int infillCandidates() { return insideCount; }
        @Override public int size() { return parcels.length * SITES_PER_PARCEL; }
        @Override public long[] get(int index) {
            Objects.checkIndex(index, size());
            long cell = parcels[index / SITES_PER_PARCEL];
            int x = VillageTerritory.cx(cell) * 16 + 8, z = VillageTerritory.cz(cell) * 16 + 8;
            int rotation = Math.floorMod(Long.hashCode(cell), 4);
            int slot = index % SITES_PER_PARCEL;
            if (slot == 4) return new long[]{x, z};
            return switch ((slot + rotation) % 4) {
                case 0 -> new long[]{x - 4, z};
                case 1 -> new long[]{x + 4, z};
                case 2 -> new long[]{x, z - 4};
                default -> new long[]{x, z + 4};
            };
        }
    }
    private record Rank(long cell, long cost, long tie) {}
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
