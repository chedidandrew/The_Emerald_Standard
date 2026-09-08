package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Geometry-aware admission gate for authored roofs and their skyline details.
 *
 * <p>A block-position graph alone is too permissive for roofs. A full block in the cell above a
 * bottom-half slab is adjacent in that graph even though Minecraft renders a half-block air gap
 * between them. Likewise, a 26-neighbour flood fill lets unrelated pieces load-bear through a
 * diagonal corner. This validator models the occupied vertical span of each cell and limits the
 * one-up/one-across exception to a visually continuous roof slope.</p>
 *
 * <p>The Minecraft adapter should map double slabs and ordinary full-height blocks to
 * {@link Occupancy#FULL}, slab halves from their actual block state, roof stairs to
 * {@code FULL} (the deliberately conservative stair approximation), structural roof material to
 * {@link Kind#ROOF_COURSE}, non-course caps/chimneys/cupolas to
 * {@link Kind#ROOFTOP_FEATURE}, and ceiling-mounted details plus every vertical link in their
 * suspension chains to {@link Kind#HANGING_FEATURE}. Foundation cells at terrain level are the
 * anchors.</p>
 */
public final class RoofGeometryValidator {
    private static final int[][] HORIZONTAL_DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private RoofGeometryValidator() {
    }

    public enum Code {
        INVALID_GEOMETRY,
        FLOATING_ABOVE_PARTIAL_SUPPORT,
        UNSUPPORTED_ROOF_TIER,
        DISCONNECTED_ROOFTOP_FEATURE,
        DISCONNECTED_HANGING_FEATURE
    }

    /** Coarse vertical collision profile, measured in Minecraft's sixteen-pixel block grid. */
    public enum Occupancy {
        FULL(0, 16),
        LOWER_HALF(0, 8),
        UPPER_HALF(8, 16);

        private final int bottom;
        private final int top;

        Occupancy(int bottom, int top) {
            this.bottom = bottom;
            this.top = top;
        }

        int bottom() {
            return bottom;
        }

        int top() {
            return top;
        }
    }

    public enum Kind {
        FOUNDATION(true),
        STRUCTURE(true),
        ROOF_COURSE(true),
        ROOFTOP_FEATURE(true),
        NON_LOAD_BEARING(false),
        HANGING_FEATURE(false);

        private final boolean loadBearing;

        Kind(boolean loadBearing) {
            this.loadBearing = loadBearing;
        }

        boolean loadBearing() {
            return loadBearing;
        }

        boolean roofRelated() {
            return this == ROOF_COURSE || this == ROOFTOP_FEATURE;
        }
    }

    public record GeometryCell(
            Voxel position,
            Kind kind,
            Occupancy occupancy,
            boolean foundationAnchor) {
        public GeometryCell {
            position = Objects.requireNonNull(position, "position");
            kind = Objects.requireNonNull(kind, "kind");
            occupancy = Objects.requireNonNull(occupancy, "occupancy");
        }

        public static GeometryCell anchor(Voxel position) {
            return new GeometryCell(position, Kind.FOUNDATION, Occupancy.FULL, true);
        }

        public static GeometryCell full(Voxel position, Kind kind) {
            return new GeometryCell(position, kind, Occupancy.FULL, false);
        }
    }

    public record RoofSnapshot(String id, List<GeometryCell> cells) {
        public RoofSnapshot {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("Roof snapshot id may not be blank");
            }
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        }
    }

    public record Issue(Code code, Voxel position, String message) {
        public Issue {
            code = Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record ValidationReport(String snapshotId, List<Issue> issues) {
        public ValidationReport {
            snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        }

        public boolean valid() {
            return issues.isEmpty();
        }

        public boolean has(Code code) {
            return issues.stream().anyMatch(issue -> issue.code() == code);
        }

        public long count(Code code) {
            return issues.stream().filter(issue -> issue.code() == code).count();
        }

        public void requireValid() {
            if (valid()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Invalid roof geometry in '").append(snapshotId).append("':");
            for (Issue issue : issues) {
                message.append(System.lineSeparator())
                        .append(" - ")
                        .append(issue.code())
                        .append(" at ")
                        .append(issue.position())
                        .append(": ")
                        .append(issue.message());
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    public static ValidationReport validate(RoofSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<Issue> issues = new ArrayList<>();
        Map<Voxel, GeometryCell> cells = assemble(snapshot, issues);
        validateLocalAttachments(cells, issues);

        Map<Voxel, Set<Voxel>> supportGraph = buildSupportGraph(cells);
        Set<Voxel> anchors = new LinkedHashSet<>();
        for (GeometryCell cell : cells.values()) {
            if (!cell.foundationAnchor()) {
                continue;
            }
            if (!cell.kind().loadBearing()) {
                issues.add(new Issue(
                        Code.INVALID_GEOMETRY,
                        cell.position(),
                        "A foundation anchor must be load-bearing"));
            } else {
                anchors.add(cell.position());
            }
        }
        if (anchors.isEmpty()) {
            issues.add(new Issue(
                    Code.INVALID_GEOMETRY,
                    null,
                    "At least one load-bearing foundation anchor is required"));
        }

        Set<Voxel> supported = traverse(supportGraph, anchors, null);
        reportUnsupportedRoofComponents(cells, supportGraph, supported, issues);
        for (GeometryCell cell : sortedCells(cells)) {
            if (cell.kind() == Kind.ROOFTOP_FEATURE && !supported.contains(cell.position())) {
                addIssueIfAbsent(issues, new Issue(
                        Code.DISCONNECTED_ROOFTOP_FEATURE,
                        cell.position(),
                        "Rooftop feature has no physical load path to a foundation anchor"));
            }
            if (cell.kind() == Kind.HANGING_FEATURE) {
                if (!hasSupportedHangingPath(cell, cells, supported)) {
                    addIssueIfAbsent(issues, new Issue(
                            Code.DISCONNECTED_HANGING_FEATURE,
                            cell.position(),
                            "Hanging feature chain has no vertical, face-contact path to a "
                                    + "supported load-bearing ceiling"));
                }
            }
        }

        issues.sort(Comparator
                .comparing((Issue issue) -> issue.code().ordinal())
                .thenComparing(issue -> issue.position() == null
                        ? new Voxel(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE)
                        : issue.position()));
        return new ValidationReport(snapshot.id(), issues);
    }

    private static Map<Voxel, GeometryCell> assemble(
            RoofSnapshot snapshot,
            List<Issue> issues) {
        Map<Voxel, GeometryCell> cells = new LinkedHashMap<>();
        for (GeometryCell cell : snapshot.cells()) {
            GeometryCell previous = cells.putIfAbsent(cell.position(), cell);
            if (previous != null) {
                issues.add(new Issue(
                        Code.INVALID_GEOMETRY,
                        cell.position(),
                        "Multiple geometry cells occupy one block position"));
            }
        }
        return cells;
    }

    private static void validateLocalAttachments(
            Map<Voxel, GeometryCell> cells,
            List<Issue> issues) {
        for (GeometryCell cell : sortedCells(cells)) {
            GeometryCell below = cells.get(cell.position().offset(0, -1, 0));
            if (below != null
                    && (cell.kind().roofRelated() || below.kind().roofRelated())
                    && !verticalContact(below, cell)) {
                issues.add(new Issue(
                        Code.FLOATING_ABOVE_PARTIAL_SUPPORT,
                        cell.position(),
                        "The lower cell ends at " + below.occupancy().top()
                                + "/16 and the upper cell begins at "
                                + cell.occupancy().bottom()
                                + "/16, leaving a visible vertical gap"));
            }

            if (cell.kind() == Kind.ROOFTOP_FEATURE
                    && (below == null || !below.kind().loadBearing()
                            || !verticalContact(below, cell))) {
                // A partial-support gap already names the more useful local cause. Missing and
                // non-load-bearing supports still need an explicit attachment diagnostic here.
                if (below == null || verticalContact(below, cell)) {
                    addIssueIfAbsent(issues, new Issue(
                            Code.DISCONNECTED_ROOFTOP_FEATURE,
                            cell.position(),
                            "Rooftop feature requires direct, face-contact support below"));
                }
            }

            if (cell.kind() == Kind.HANGING_FEATURE) {
                GeometryCell above = cells.get(cell.position().offset(0, 1, 0));
                if (above == null
                        || (!above.kind().loadBearing()
                                && above.kind() != Kind.HANGING_FEATURE)
                        || !verticalContact(cell, above)) {
                    addIssueIfAbsent(issues, new Issue(
                            Code.DISCONNECTED_HANGING_FEATURE,
                            cell.position(),
                            "Hanging feature requires direct, face-contact support above from "
                                    + "another hanging feature or a load-bearing ceiling"));
                }
            }
        }
    }

    private static boolean hasSupportedHangingPath(
            GeometryCell hanging,
            Map<Voxel, GeometryCell> cells,
            Set<Voxel> supported) {
        GeometryCell lower = hanging;
        // Positions are unique and every step rises exactly one block, so a valid walk must
        // terminate within the number of cells in the snapshot. The bound also keeps malformed
        // integer-extreme snapshots from cycling through coordinate overflow.
        for (int remaining = cells.size(); remaining > 0; remaining--) {
            GeometryCell above = cells.get(lower.position().offset(0, 1, 0));
            if (above == null || !verticalContact(lower, above)) {
                return false;
            }
            if (above.kind().loadBearing()) {
                return supported.contains(above.position());
            }
            if (above.kind() != Kind.HANGING_FEATURE) {
                return false;
            }
            lower = above;
        }
        return false;
    }

    private static Map<Voxel, Set<Voxel>> buildSupportGraph(
            Map<Voxel, GeometryCell> cells) {
        Map<Voxel, Set<Voxel>> graph = new HashMap<>();
        for (GeometryCell cell : cells.values()) {
            if (cell.kind().loadBearing()) {
                graph.put(cell.position(), new LinkedHashSet<>());
            }
        }

        for (GeometryCell cell : cells.values()) {
            if (!cell.kind().loadBearing()) {
                continue;
            }
            Voxel position = cell.position();
            GeometryCell above = cells.get(position.offset(0, 1, 0));
            if (above != null && above.kind().loadBearing() && verticalContact(cell, above)) {
                connect(graph, position, above.position());
            }
            for (int[] direction : HORIZONTAL_DIRECTIONS) {
                GeometryCell side = cells.get(position.offset(direction[0], 0, direction[1]));
                if (side != null && side.kind().loadBearing()
                        && horizontalContact(cell, side)) {
                    connect(graph, position, side.position());
                }

                GeometryCell higherSlope = cells.get(
                        position.offset(direction[0], 1, direction[1]));
                if (higherSlope != null
                        && cell.kind() == Kind.ROOF_COURSE
                        && higherSlope.kind() == Kind.ROOF_COURSE
                        && slopeContact(cell, higherSlope)) {
                    connect(graph, position, higherSlope.position());
                }
            }
        }
        return graph;
    }

    private static boolean verticalContact(GeometryCell lower, GeometryCell upper) {
        return lower.occupancy().top() == 16 && upper.occupancy().bottom() == 0;
    }

    private static boolean horizontalContact(GeometryCell first, GeometryCell second) {
        return Math.min(first.occupancy().top(), second.occupancy().top())
                > Math.max(first.occupancy().bottom(), second.occupancy().bottom());
    }

    private static boolean slopeContact(GeometryCell lower, GeometryCell upper) {
        return lower.occupancy().top() == 16 && upper.occupancy().bottom() == 0;
    }

    private static void connect(Map<Voxel, Set<Voxel>> graph, Voxel first, Voxel second) {
        graph.get(first).add(second);
        graph.get(second).add(first);
    }

    private static void addIssueIfAbsent(List<Issue> issues, Issue candidate) {
        boolean duplicate = issues.stream().anyMatch(issue -> issue.code() == candidate.code()
                && Objects.equals(issue.position(), candidate.position()));
        if (!duplicate) {
            issues.add(candidate);
        }
    }

    private static void reportUnsupportedRoofComponents(
            Map<Voxel, GeometryCell> cells,
            Map<Voxel, Set<Voxel>> graph,
            Set<Voxel> supported,
            List<Issue> issues) {
        Set<Voxel> unseenRoof = new HashSet<>();
        for (GeometryCell cell : cells.values()) {
            if (cell.kind() == Kind.ROOF_COURSE && !supported.contains(cell.position())) {
                unseenRoof.add(cell.position());
            }
        }
        while (!unseenRoof.isEmpty()) {
            Voxel sample = unseenRoof.stream().min(Voxel::compareTo).orElseThrow();
            Set<Voxel> component = traverse(graph, Set.of(sample), supported);
            List<Voxel> roofCells = component.stream()
                    .filter(position -> cells.get(position).kind() == Kind.ROOF_COURSE)
                    .sorted()
                    .toList();
            unseenRoof.removeAll(roofCells);
            issues.add(new Issue(
                    Code.UNSUPPORTED_ROOF_TIER,
                    roofCells.getFirst(),
                    "A roof component of " + roofCells.size()
                            + " course cell(s) has no face-contact or one-step sloped path "
                            + "to the foundation"));
        }
    }

    private static Set<Voxel> traverse(
            Map<Voxel, Set<Voxel>> graph,
            Set<Voxel> seeds,
            Set<Voxel> excluded) {
        Set<Voxel> visited = new HashSet<>();
        ArrayDeque<Voxel> pending = new ArrayDeque<>();
        for (Voxel seed : seeds.stream().sorted().toList()) {
            if (graph.containsKey(seed)
                    && (excluded == null || !excluded.contains(seed))
                    && visited.add(seed)) {
                pending.add(seed);
            }
        }
        while (!pending.isEmpty()) {
            Voxel position = pending.removeFirst();
            for (Voxel neighbor : graph.getOrDefault(position, Set.of())) {
                if ((excluded == null || !excluded.contains(neighbor))
                        && visited.add(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        return visited;
    }

    private static List<GeometryCell> sortedCells(Map<Voxel, GeometryCell> cells) {
        return cells.values().stream()
                .sorted(Comparator.comparing(GeometryCell::position))
                .toList();
    }
}
