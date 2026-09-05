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
    /** Maximum natural surface drop accepted across a generated structure lot. */
    public static final int MAX_TERRAIN_DROP = 2;

    private static final Comparator<Column> COLUMN_ORDER = Comparator
            .comparingInt(Column::z)
            .thenComparingInt(Column::x);

    private TerrainFoundationPlan() {
    }

    /**
     * Returns the deterministic suffix cells needed beneath every ground-contact column.
     *
     * <p>A column beginning at y=1 receives the missing y=0 footing. Every column whose lowest
     * authored cell is at or below y=1 then receives the configured negative-depth support cells.
     * Columns beginning at y=2 or higher are treated as intentionally suspended roof/detail
     * columns. Existing authored cells are never duplicated. The returned cells are sorted and
     * are intended to be appended after the legacy template prefix.</p>
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
            if (lowest == 1) {
                addIfMissing(suffix, occupied, new Cell(column.x, 0, column.z));
            }
            for (int depth = 1; depth <= maximumDepth; depth++) {
                addIfMissing(suffix, occupied, new Cell(column.x, -depth, column.z));
            }
        }
        return List.copyOf(suffix);
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

    private record Column(int x, int z) {
    }
}
