package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory, rebuildable spatial index for persistent village centers.
 *
 * <p>The index is deliberately not part of the save format: the village map remains authoritative,
 * while this object only narrows candidates for frequent proximity lookups. Nearest-village identity
 * queries use exact three-dimensional distance, while multi-origin development activation uses
 * horizontal distance so player altitude does not suspend a loaded settlement. Oversized searches
 * fall back to walking the villages in one dimension instead of expanding an unbounded cell range.</p>
 */
final class VillageSpatialIndex {
    static final int CELL_SIZE_BLOCKS = 64;
    private static final long MAX_CELL_PROBES_PER_QUERY = 16_384L;

    private final Map<String, Map<Cell, LinkedHashSet<UUID>>> cellsByDimension =
            new HashMap<>();
    private final Map<UUID, IndexedLocation> locationsByVillage = new HashMap<>();
    private final Map<String, LinkedHashSet<UUID>> villagesByDimension = new HashMap<>();
    private long nextOrdinal;

    void rebuild(Map<UUID, EconomyState.VillageRecord> villages) {
        cellsByDimension.clear();
        locationsByVillage.clear();
        villagesByDimension.clear();
        nextOrdinal = 0L;
        if (villages == null) {
            return;
        }
        villages.values().forEach(this::upsert);
    }

    void upsert(EconomyState.VillageRecord village) {
        if (village == null || village.villageId == null || village.dimensionKey == null) {
            return;
        }
        IndexedLocation previous = locationsByVillage.remove(village.villageId);
        long ordinal = previous == null ? nextOrdinal++ : previous.ordinal();
        if (previous != null) {
            removeFromBuckets(village.villageId, previous);
        }

        Cell cell = cellFor(village.centerPos);
        IndexedLocation location = new IndexedLocation(village.dimensionKey, cell, ordinal);
        locationsByVillage.put(village.villageId, location);
        cellsByDimension
                .computeIfAbsent(village.dimensionKey, ignored -> new HashMap<>())
                .computeIfAbsent(cell, ignored -> new LinkedHashSet<>())
                .add(village.villageId);
        villagesByDimension
                .computeIfAbsent(village.dimensionKey, ignored -> new LinkedHashSet<>())
                .add(village.villageId);
    }

    void remove(UUID villageId) {
        IndexedLocation previous = locationsByVillage.remove(villageId);
        if (previous != null) {
            removeFromBuckets(villageId, previous);
        }
    }

    int size() {
        return locationsByVillage.size();
    }

    EconomyState.VillageRecord nearest(
            Map<UUID, EconomyState.VillageRecord> villages,
            String dimensionKey,
            long packedPosition,
            double maximumDistance) {
        if (villages == null
                || dimensionKey == null
                || !Double.isFinite(maximumDistance)
                || maximumDistance < 0.0) {
            return null;
        }
        Collection<UUID> candidates = candidatesNear(dimensionKey, packedPosition, maximumDistance);
        if (candidates.isEmpty()) {
            return null;
        }

        double maximumDistanceSquared = squareDistanceLimit(maximumDistance);
        EconomyState.VillageRecord best = null;
        double bestDistance = maximumDistanceSquared;
        long bestOrdinal = Long.MIN_VALUE;
        for (UUID villageId : candidates) {
            EconomyState.VillageRecord village = villages.get(villageId);
            IndexedLocation indexed = locationsByVillage.get(villageId);
            if (village == null
                    || indexed == null
                    || !Objects.equals(village.dimensionKey, dimensionKey)) {
                continue;
            }
            double distance = distanceSquared(village.centerPos, packedPosition);
            if (distance < bestDistance
                    || (distance == bestDistance && indexed.ordinal() > bestOrdinal)) {
                best = village;
                bestDistance = distance;
                bestOrdinal = indexed.ordinal();
            }
        }
        return best;
    }

    List<EconomyState.VillageRecord> nearAny(
            Map<UUID, EconomyState.VillageRecord> villages,
            String dimensionKey,
            Collection<Long> packedPositions,
            double maximumDistance) {
        if (villages == null
                || dimensionKey == null
                || packedPositions == null
                || packedPositions.isEmpty()
                || !Double.isFinite(maximumDistance)
                || maximumDistance < 0.0) {
            return List.of();
        }
        double maximumDistanceSquared = squareDistanceLimit(maximumDistance);
        Set<UUID> matches = new HashSet<>();
        for (Long packedPosition : packedPositions) {
            if (packedPosition == null) {
                continue;
            }
            for (UUID villageId : candidatesNear(
                    dimensionKey, packedPosition, maximumDistance)) {
                if (matches.contains(villageId)) {
                    continue;
                }
                EconomyState.VillageRecord village = villages.get(villageId);
                if (village != null
                        && Objects.equals(village.dimensionKey, dimensionKey)
                        && horizontalDistanceSquared(village.centerPos, packedPosition)
                                <= maximumDistanceSquared) {
                    matches.add(villageId);
                }
            }
        }
        if (matches.isEmpty()) {
            return List.of();
        }

        List<UUID> ordered = new ArrayList<>(matches);
        ordered.sort(Comparator.comparingLong(this::ordinal));
        List<EconomyState.VillageRecord> result = new ArrayList<>(ordered.size());
        for (UUID villageId : ordered) {
            EconomyState.VillageRecord village = villages.get(villageId);
            if (village != null) {
                result.add(village);
            }
        }
        return List.copyOf(result);
    }

    private Collection<UUID> candidatesNear(
            String dimensionKey, long packedPosition, double maximumDistance) {
        LinkedHashSet<UUID> dimensionVillages = villagesByDimension.get(dimensionKey);
        if (dimensionVillages == null || dimensionVillages.isEmpty()) {
            return Set.of();
        }
        long cellRadius = (long) Math.ceil(maximumDistance / CELL_SIZE_BLOCKS);
        long diameter = cellRadius > (Long.MAX_VALUE - 1L) / 2L
                ? Long.MAX_VALUE
                : cellRadius * 2L + 1L;
        long cellProbes = diameter > 0L && diameter <= Long.MAX_VALUE / diameter
                ? diameter * diameter
                : Long.MAX_VALUE;
        if (cellProbes > MAX_CELL_PROBES_PER_QUERY
                || cellProbes > Math.max(64L, dimensionVillages.size() * 4L)) {
            return dimensionVillages;
        }

        int centerCellX = Math.floorDiv(unpackX(packedPosition), CELL_SIZE_BLOCKS);
        int centerCellZ = Math.floorDiv(unpackZ(packedPosition), CELL_SIZE_BLOCKS);
        Map<Cell, LinkedHashSet<UUID>> cells = cellsByDimension.get(dimensionKey);
        if (cells == null) {
            return Set.of();
        }
        LinkedHashSet<UUID> candidates = new LinkedHashSet<>();
        int boundedRadius = (int) cellRadius;
        for (int dx = -boundedRadius; dx <= boundedRadius; dx++) {
            for (int dz = -boundedRadius; dz <= boundedRadius; dz++) {
                LinkedHashSet<UUID> bucket = cells.get(new Cell(centerCellX + dx, centerCellZ + dz));
                if (bucket != null) {
                    candidates.addAll(bucket);
                }
            }
        }
        return candidates;
    }

    private long ordinal(UUID villageId) {
        IndexedLocation location = locationsByVillage.get(villageId);
        return location == null ? Long.MAX_VALUE : location.ordinal();
    }

    private void removeFromBuckets(UUID villageId, IndexedLocation location) {
        Map<Cell, LinkedHashSet<UUID>> cells = cellsByDimension.get(location.dimensionKey());
        if (cells != null) {
            LinkedHashSet<UUID> bucket = cells.get(location.cell());
            if (bucket != null) {
                bucket.remove(villageId);
                if (bucket.isEmpty()) {
                    cells.remove(location.cell());
                }
            }
            if (cells.isEmpty()) {
                cellsByDimension.remove(location.dimensionKey());
            }
        }
        LinkedHashSet<UUID> dimensionVillages = villagesByDimension.get(location.dimensionKey());
        if (dimensionVillages != null) {
            dimensionVillages.remove(villageId);
            if (dimensionVillages.isEmpty()) {
                villagesByDimension.remove(location.dimensionKey());
            }
        }
    }

    private static Cell cellFor(long packedPosition) {
        return new Cell(
                Math.floorDiv(unpackX(packedPosition), CELL_SIZE_BLOCKS),
                Math.floorDiv(unpackZ(packedPosition), CELL_SIZE_BLOCKS));
    }

    private static double squareDistanceLimit(double distance) {
        double squared = distance * distance;
        return Double.isFinite(squared) ? squared : Double.MAX_VALUE;
    }

    private static double distanceSquared(long first, long second) {
        double dx = (double) unpackX(first) - unpackX(second);
        double dy = (double) unpackY(first) - unpackY(second);
        double dz = (double) unpackZ(first) - unpackZ(second);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double horizontalDistanceSquared(long first, long second) {
        double dx = (double) unpackX(first) - unpackX(second);
        double dz = (double) unpackZ(first) - unpackZ(second);
        return dx * dx + dz * dz;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private record Cell(int x, int z) {
    }

    private record IndexedLocation(String dimensionKey, Cell cell, long ordinal) {
    }
}
