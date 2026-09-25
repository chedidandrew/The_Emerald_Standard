package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Loader-neutral planning rules for terrain-safe authored-structure foundations. */
public final class TerrainFoundationPlan {
    /**
     * Maximum natural surface drop accepted across a generated structure lot.
     *
     * <p>Four blocks is enough to bridge ordinary village hills without turning the generator
     * into a terrain excavator. The placement preflight still rejects every solid occupied cell,
     * block entity, fluid, path, and non-natural foundation, so the extra tolerance only produces
     * deeper authored supports and entrance steps on otherwise untouched terrain.</p>
     */
    public static final int MAX_TERRAIN_DROP = 4;
    /** New lots may cut four blocks above their floor as well as fill four below it. */
    public static final int MAX_TERRAIN_CUT = 4;

    /** Median balances earthworks; the clamp guarantees existing four-block supports still reach. */
    public static java.util.OptionalInt levelledFloor(List<Integer> surfaces) {
        if (surfaces == null || surfaces.isEmpty()) return java.util.OptionalInt.empty();
        List<Integer> sorted = surfaces.stream().sorted().toList();
        int min = sorted.getFirst(), max = sorted.getLast();
        if ((long) max - min > MAX_TERRAIN_DROP + MAX_TERRAIN_CUT)
            return java.util.OptionalInt.empty();
        return java.util.OptionalInt.of(Math.clamp(sorted.get(sorted.size() / 2),
                max - MAX_TERRAIN_CUT, min + MAX_TERRAIN_DROP));
    }

    private static final Comparator<Column> COLUMN_ORDER = Comparator
            .comparingInt(Column::z)
            .thenComparingInt(Column::x);

    private TerrainFoundationPlan() {
    }

    /**
     * Returns the deterministic suffix cells needed beneath every ground-contact column.
     *
     * <p>A column beginning at y=1 receives the missing y=0 footing. Every ground-contact column
     * then receives contiguous support cells strictly beneath its lowest authored cell, down to
     * the configured negative depth. This also supports authored terrain stairs below y=0 without
     * filling their walkable space from above. Columns beginning at y=2 or higher are treated as
     * intentionally suspended roof/detail columns. Existing authored cells are never duplicated.
     * The returned cells are sorted and are intended to be appended after the legacy template
     * prefix.</p>
     */
    public static List<Cell> appendSupportCells(List<Cell> authored, int maximumDepth) {
        if (maximumDepth < 0 || maximumDepth > 16) {
            throw new IllegalArgumentException("Invalid foundation depth " + maximumDepth);
        }
        if (authored == null || authored.isEmpty()) {
            return List.of();
        }

        Set<Cell> occupied = new HashSet<>();
        Map<Column, Integer> lowestByColumn = new TreeMap<>(COLUMN_ORDER);
        for (Cell cell : authored) {
            if (cell == null) {
                throw new IllegalArgumentException("Authored foundation cell cannot be null");
            }
            occupied.add(cell);
            lowestByColumn.merge(new Column(cell.x, cell.z), cell.y, Math::min);
        }

        List<Cell> suffix = new ArrayList<>();
        for (Map.Entry<Column, Integer> entry : lowestByColumn.entrySet()) {
            int lowest = entry.getValue();
            if (lowest > 1) {
                continue;
            }
            Column column = entry.getKey();
            int firstSupportY = Math.min(0, lowest - 1);
            for (int y = firstSupportY; y >= -maximumDepth; y--) {
                addIfMissing(suffix, occupied, new Cell(column.x, y, column.z));
            }
        }
        return List.copyOf(suffix);
    }

    /**
     * Returns every authored column that actually reaches the structure's ground course.
     *
     * <p>The input is intentionally supplied by the runtime after mirror/rotation transforms and
     * after optional cells have been removed. That makes this the authoritative terrain footprint,
     * including annexes which extend beyond a catalog descriptor's nominal rectangle. Columns
     * whose lowest cell is above y=1 are roof projections or hanging detail and do not acquire a
     * synthetic foundation.</p>
     */
    public static List<Column> groundContactColumns(List<Cell> authored) {
        if (authored == null || authored.isEmpty()) {
            return List.of();
        }
        Map<Column, Integer> lowestByColumn = new TreeMap<>(COLUMN_ORDER);
        for (Cell cell : authored) {
            if (cell == null) {
                throw new IllegalArgumentException("Authored foundation cell cannot be null");
            }
            lowestByColumn.merge(new Column(cell.x, cell.z), cell.y, Math::min);
        }
        return lowestByColumn.entrySet().stream()
                .filter(entry -> entry.getValue() <= 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** True when a sampled natural lot can be bridged by the configured foundation depth. */
    public static boolean supportsTerrainRange(
            int minimumSurface, int maximumSurface, int maximumDepth) {
        if (maximumDepth < 0 || minimumSurface == Integer.MAX_VALUE
                || maximumSurface == Integer.MIN_VALUE || maximumSurface < minimumSurface) {
            return false;
        }
        return (long) maximumSurface - minimumSurface <= maximumDepth;
    }

    private static void addIfMissing(List<Cell> suffix, Set<Cell> occupied, Cell cell) {
        if (occupied.add(cell)) {
            suffix.add(cell);
        }
    }

    public record Cell(int x, int y, int z) {
    }

    public record Column(int x, int z) {
    }
}
