package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-neutral admission gate for the visible detail density of an authored building.
 *
 * <p>Weather sealing, support, navigation, and silhouette uniqueness are separate concerns. A
 * perfectly valid rectangular shell can still be visually unfinished, so this validator measures
 * the authored phases that create readable depth and use: exposed framing, openings, permanent
 * fixtures, decorative accents, facade distribution, and roofline relief. Counts are normalized
 * against footprint area so the same gate works for a compact roadside building and a civic
 * landmark.</p>
 *
 * <p>The thresholds are intentionally conservative. They reject a furnished-or-unfurnished plain
 * box while leaving authors room to express an open market, mine headframe, cottage, or monumental
 * hall differently. Minecraft-backed catalogs should translate their final, disjoint placement
 * stream to {@link DetailCell}s and invoke {@link #validateSnapshots(Collection)} during catalog
 * admission.</p>
 */
public final class WholeBuildingDetailDensityValidator {
    /** At least ten percent of one footprint layer must carry authored visual articulation. */
    public static final double MINIMUM_DETAIL_CELLS_PER_FOOTPRINT = 0.10;
    /** Details must occupy multiple horizontal columns instead of one token decorated corner. */
    public static final double MINIMUM_DETAIL_COLUMN_COVERAGE = 0.05;
    /** At least half the total density budget must be facade/massing articulation, not clutter. */
    public static final double MINIMUM_ARCHITECTURAL_CELLS_PER_FOOTPRINT = 0.05;

    private static final int ABSOLUTE_MINIMUM_DETAIL_CELLS = 12;
    private static final int ABSOLUTE_MINIMUM_DETAIL_COLUMNS = 6;
    private static final int ABSOLUTE_MINIMUM_ARCHITECTURAL_CELLS = 8;
    private static final int ABSOLUTE_MINIMUM_FRAME_CELLS = 4;
    private static final int ABSOLUTE_MINIMUM_LIVED_DETAIL_CELLS = 3;
    private static final int ABSOLUTE_MINIMUM_OPENING_CELLS = 2;
    private static final int ABSOLUTE_MINIMUM_FIXTURE_CELLS = 2;
    private static final int ABSOLUTE_MINIMUM_FIXTURE_COLUMNS = 2;
    private static final int ABSOLUTE_MINIMUM_DETAIL_REGIONS = 3;
    private static final int ABSOLUTE_MINIMUM_AXIS_BANDS = 2;
    private static final int ABSOLUTE_MINIMUM_FACADE_COLUMNS = 4;
    private static final int ABSOLUTE_MINIMUM_FACADE_SIDES = 2;
    private static final int ABSOLUTE_MINIMUM_FACADE_REGIONS = 3;
    private static final int ABSOLUTE_MINIMUM_PROJECTED_COLUMNS = 2;
    private static final int ABSOLUTE_MINIMUM_ROOF_HEIGHT_RANGE = 1;
    private static final int ABSOLUTE_MINIMUM_ROOF_ARTICULATION_COLUMNS = 2;
    private static final int ABSOLUTE_MINIMUM_ROOF_ARTICULATION_REGIONS = 2;

    private WholeBuildingDetailDensityValidator() {
    }

    /** Authored placement categories relevant to visual detail. */
    public enum DetailPhase {
        FOUNDATION,
        FRAME,
        SHELL,
        ROOF,
        OPENING,
        FIXTURE,
        DECOR
    }

    /** One palette-independent authored placement. */
    public record DetailCell(Voxel position, DetailPhase phase) {
        public DetailCell {
            position = Objects.requireNonNull(position, "position");
            phase = Objects.requireNonNull(phase, "phase");
        }
    }

    /** Complete maximum-stage geometry for one immutable master. */
    public record DetailSnapshot(
            String id,
            int width,
            int depth,
            int height,
            boolean enclosed,
            Collection<DetailCell> cells) {
        public DetailSnapshot {
            id = Objects.requireNonNull(id, "id").trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Detail snapshot id must not be blank");
            }
            if (width <= 0 || depth <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "Detail snapshot dimensions must be positive: " + id);
            }
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException(
                        "Detail snapshot must contain at least one cell: " + id);
            }
            Map<Voxel, DetailPhase> occupied = new HashMap<>();
            for (DetailCell cell : cells) {
                Objects.requireNonNull(cell, "detail cell");
                DetailPhase previous = occupied.putIfAbsent(cell.position(), cell.phase());
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate detail snapshot cell at " + cell.position() + " in " + id);
                }
            }
        }
    }

    /** Deterministic measurements and any failed quality requirements for one master. */
    public record DetailProfile(
            String id,
            int footprintArea,
            int detailCells,
            int detailColumns,
            int detailRegions,
            int detailXAxisBands,
            int detailZAxisBands,
            int frameCells,
            int openingCells,
            int fixtureCells,
            int fixtureColumns,
            int decorCells,
            int facadeColumns,
            int facadeSides,
            int facadeRegions,
            int projectedArchitecturalColumns,
            int roofCells,
            int roofHeightRange,
            int roofArticulationColumns,
            int roofArticulationRegions,
            int requiredDetailCells,
            int requiredDetailColumns,
            int requiredArchitecturalCells,
            List<String> failures) {
        public DetailProfile {
            id = Objects.requireNonNull(id, "id");
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        }

        public boolean detailed() {
            return failures.isEmpty();
        }
    }

    /** Frozen catalog report, sorted by stable master id. */
    public record CatalogReport(List<DetailProfile> profiles) {
        public CatalogReport {
            profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        }

        public List<DetailProfile> insufficientProfiles() {
            return profiles.stream().filter(profile -> !profile.detailed()).toList();
        }

        public boolean detailed() {
            return insufficientProfiles().isEmpty();
        }

        /** Throws a catalog-admission error with concrete measurements for every weak master. */
        public void requireDetailed() {
            List<DetailProfile> insufficient = insufficientProfiles();
            if (insufficient.isEmpty()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Authored building catalog contains under-detailed structures:");
            for (DetailProfile profile : insufficient) {
                message.append(System.lineSeparator())
                        .append(" - ")
                        .append(profile.id())
                        .append(": ")
                        .append(String.join("; ", profile.failures()))
                        .append(" [detail cells/columns/regions/x-bands/z-bands ")
                        .append(profile.detailCells()).append('/')
                        .append(profile.detailColumns()).append('/')
                        .append(profile.detailRegions()).append('/')
                        .append(profile.detailXAxisBands()).append('/')
                        .append(profile.detailZAxisBands())
                        .append("; frame/opening/fixture-columns/decor ")
                        .append(profile.frameCells()).append('/')
                        .append(profile.openingCells()).append('/')
                        .append(profile.fixtureCells()).append('-')
                        .append(profile.fixtureColumns()).append('/')
                        .append(profile.decorCells())
                        .append("; facade columns/sides/regions ")
                        .append(profile.facadeColumns()).append('/')
                        .append(profile.facadeSides()).append('/')
                        .append(profile.facadeRegions())
                        .append("; projected columns ")
                        .append(profile.projectedArchitecturalColumns())
                        .append("; roof cells/range/articulated-columns/regions ")
                        .append(profile.roofCells()).append('/')
                        .append(profile.roofHeightRange()).append('/')
                        .append(profile.roofArticulationColumns()).append('/')
                        .append(profile.roofArticulationRegions()).append(']');
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    public static DetailProfile profile(DetailSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<DetailPhase, Integer> counts = new EnumMap<>(DetailPhase.class);
        Set<HorizontalColumn> detailColumns = new HashSet<>();
        Set<HorizontalRegion> detailRegions = new HashSet<>();
        Set<Integer> detailXBands = new HashSet<>();
        Set<Integer> detailZBands = new HashSet<>();
        Set<HorizontalColumn> frameColumns = new HashSet<>();
        Set<HorizontalColumn> fixtureColumns = new HashSet<>();
        Set<HorizontalColumn> shellColumns = new HashSet<>();
        Set<Voxel> structuralCells = new HashSet<>();
        Set<Voxel> shellCells = new HashSet<>();
        Set<Voxel> architecturalCells = new HashSet<>();
        Map<HorizontalColumn, Integer> roofTopByColumn = new HashMap<>();
        int minimumRoofY = Integer.MAX_VALUE;
        int detailCells = 0;
        for (DetailCell cell : snapshot.cells()) {
            counts.merge(cell.phase(), 1, Integer::sum);
            HorizontalColumn column = column(cell.position());
            if (isStructural(cell.phase())) {
                structuralCells.add(cell.position());
            }
            if (cell.phase() == DetailPhase.SHELL) {
                shellColumns.add(column);
                shellCells.add(cell.position());
            }
            if (cell.phase() == DetailPhase.FRAME) {
                frameColumns.add(column);
            }
            if (isArchitecturalDetail(cell.phase())) {
                architecturalCells.add(cell.position());
            }
            if (cell.phase() == DetailPhase.FIXTURE) {
                fixtureColumns.add(column);
            }
            if (cell.phase() == DetailPhase.ROOF) {
                minimumRoofY = Math.min(minimumRoofY, cell.position().y());
            }
            if (isVisualDetail(cell.phase())) {
                detailCells++;
                detailColumns.add(column);
                HorizontalRegion region = region(snapshot, column);
                detailRegions.add(region);
                detailXBands.add(region.xBand());
                detailZBands.add(region.zBand());
            }
        }

        Set<HorizontalColumn> facadeColumns = new HashSet<>();
        Set<FacadeSide> facadeSides = new HashSet<>();
        Set<HorizontalRegion> facadeRegions = new HashSet<>();
        Set<HorizontalColumn> projectedArchitecturalColumns = new HashSet<>();
        Set<Voxel> attachedArchitecture = attachedArchitecture(architecturalCells, shellCells);
        for (DetailCell cell : snapshot.cells()) {
            if (!isArchitecturalDetail(cell.phase())
                    || (snapshot.enclosed()
                            && !attachedArchitecture.contains(cell.position()))) {
                continue;
            }
            HorizontalColumn column = column(cell.position());
            if (!shellColumns.contains(column)) {
                projectedArchitecturalColumns.add(column);
            }
            for (FacadeSide side : FacadeSide.values()) {
                Voxel neighbor = new Voxel(
                        cell.position().x() + side.dx,
                        cell.position().y(),
                        cell.position().z() + side.dz);
                if (!structuralCells.contains(neighbor)) {
                    facadeColumns.add(column);
                    facadeSides.add(side);
                    facadeRegions.add(region(snapshot, column));
                }
            }
        }

        if (minimumRoofY != Integer.MAX_VALUE) {
            for (DetailCell cell : snapshot.cells()) {
                if (isRooflinePhase(cell.phase()) && cell.position().y() >= minimumRoofY) {
                    roofTopByColumn.merge(
                            column(cell.position()), cell.position().y(), Math::max);
                }
            }
        }
        int maximumRooflineY = roofTopByColumn.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(minimumRoofY == Integer.MAX_VALUE ? 0 : minimumRoofY);
        int roofHeightRange = minimumRoofY == Integer.MAX_VALUE
                ? 0
                : maximumRooflineY - minimumRoofY;
        Set<HorizontalColumn> roofArticulationColumns = new HashSet<>();
        Set<HorizontalRegion> roofArticulationRegions = new HashSet<>();
        if (minimumRoofY != Integer.MAX_VALUE) {
            for (Map.Entry<HorizontalColumn, Integer> entry : roofTopByColumn.entrySet()) {
                if (entry.getValue() > minimumRoofY) {
                    roofArticulationColumns.add(entry.getKey());
                    roofArticulationRegions.add(region(snapshot, entry.getKey()));
                }
            }
        }

        int area = Math.multiplyExact(snapshot.width(), snapshot.depth());
        int requiredDetailCells = Math.max(
                ABSOLUTE_MINIMUM_DETAIL_CELLS,
                (int) Math.ceil(area * MINIMUM_DETAIL_CELLS_PER_FOOTPRINT));
        int requiredDetailColumns = Math.max(
                ABSOLUTE_MINIMUM_DETAIL_COLUMNS,
                (int) Math.ceil(area * MINIMUM_DETAIL_COLUMN_COVERAGE));
        int requiredArchitecturalCells = Math.max(
                ABSOLUTE_MINIMUM_ARCHITECTURAL_CELLS,
                (int) Math.ceil(area * MINIMUM_ARCHITECTURAL_CELLS_PER_FOOTPRINT));
        int frame = counts.getOrDefault(DetailPhase.FRAME, 0);
        int opening = counts.getOrDefault(DetailPhase.OPENING, 0);
        int architecturalDetail = frame + opening;
        int fixture = counts.getOrDefault(DetailPhase.FIXTURE, 0);
        int decor = counts.getOrDefault(DetailPhase.DECOR, 0);
        int livedDetail = fixture + decor;

        List<String> failures = new ArrayList<>();
        if (detailCells < requiredDetailCells) {
            failures.add("only " + detailCells + " detail cells; requires "
                    + requiredDetailCells + " for a " + snapshot.width() + 'x'
                    + snapshot.depth() + " footprint");
        }
        if (detailColumns.size() < requiredDetailColumns) {
            failures.add("detail reaches only " + detailColumns.size()
                    + " horizontal columns; requires " + requiredDetailColumns);
        }
        if (detailRegions.size() < ABSOLUTE_MINIMUM_DETAIL_REGIONS
                || detailXBands.size() < ABSOLUTE_MINIMUM_AXIS_BANDS
                || detailZBands.size() < ABSOLUTE_MINIMUM_AXIS_BANDS) {
            failures.add("detail distribution reaches " + detailRegions.size()
                    + " horizontal regions and " + detailXBands.size() + '/'
                    + detailZBands.size() + " x/z bands; requires at least "
                    + ABSOLUTE_MINIMUM_DETAIL_REGIONS + " regions and "
                    + ABSOLUTE_MINIMUM_AXIS_BANDS + " bands on each axis");
        }
        if (architecturalDetail < requiredArchitecturalCells) {
            failures.add("frame and opening articulation totals " + architecturalDetail
                    + " cells; requires " + requiredArchitecturalCells);
        }
        if (frame < ABSOLUTE_MINIMUM_FRAME_CELLS) {
            failures.add("exposed frame has " + frame + " cells; requires "
                    + ABSOLUTE_MINIMUM_FRAME_CELLS);
        }
        if (snapshot.enclosed() && opening < ABSOLUTE_MINIMUM_OPENING_CELLS) {
            failures.add("enclosed facade has " + opening + " opening cells; requires "
                    + ABSOLUTE_MINIMUM_OPENING_CELLS);
        }
        if (fixture < ABSOLUTE_MINIMUM_FIXTURE_CELLS
                || fixtureColumns.size() < ABSOLUTE_MINIMUM_FIXTURE_COLUMNS) {
            failures.add("functional fixtures occupy " + fixture + " cells in "
                    + fixtureColumns.size() + " columns; requires at least "
                    + ABSOLUTE_MINIMUM_FIXTURE_CELLS + " cells in "
                    + ABSOLUTE_MINIMUM_FIXTURE_COLUMNS
                    + " columns so the building's role is visually readable");
        }
        if (livedDetail < ABSOLUTE_MINIMUM_LIVED_DETAIL_CELLS) {
            failures.add("fixtures and decor total " + livedDetail + " cells; requires "
                    + ABSOLUTE_MINIMUM_LIVED_DETAIL_CELLS);
        }
        if (snapshot.enclosed()) {
            if (facadeColumns.size() < ABSOLUTE_MINIMUM_FACADE_COLUMNS
                    || facadeSides.size() < ABSOLUTE_MINIMUM_FACADE_SIDES
                    || facadeRegions.size() < ABSOLUTE_MINIMUM_FACADE_REGIONS) {
                failures.add("facade articulation reaches " + facadeColumns.size()
                        + " columns, " + facadeSides.size() + " exposed sides, and "
                        + facadeRegions.size() + " regions; requires at least "
                        + ABSOLUTE_MINIMUM_FACADE_COLUMNS + '/'
                        + ABSOLUTE_MINIMUM_FACADE_SIDES + '/'
                        + ABSOLUTE_MINIMUM_FACADE_REGIONS);
            }
            if (projectedArchitecturalColumns.size()
                    < ABSOLUTE_MINIMUM_PROJECTED_COLUMNS) {
                failures.add("architectural frame/opening depth projects into only "
                        + projectedArchitecturalColumns.size()
                        + " non-shell columns; requires at least "
                        + ABSOLUTE_MINIMUM_PROJECTED_COLUMNS);
            }
            if (roofHeightRange < ABSOLUTE_MINIMUM_ROOF_HEIGHT_RANGE
                    || roofArticulationColumns.size()
                            < ABSOLUTE_MINIMUM_ROOF_ARTICULATION_COLUMNS
                    || roofArticulationRegions.size()
                            < ABSOLUTE_MINIMUM_ROOF_ARTICULATION_REGIONS) {
                failures.add("roofline relief spans " + roofHeightRange
                        + " vertical blocks across " + roofArticulationColumns.size()
                        + " articulated columns and " + roofArticulationRegions.size()
                        + " regions; requires at least "
                        + ABSOLUTE_MINIMUM_ROOF_HEIGHT_RANGE + '/'
                        + ABSOLUTE_MINIMUM_ROOF_ARTICULATION_COLUMNS + '/'
                        + ABSOLUTE_MINIMUM_ROOF_ARTICULATION_REGIONS
                        + " (slopes, stepped/monitor roofs, or distributed parapets qualify)");
            }
        } else if (frameColumns.size() < ABSOLUTE_MINIMUM_FACADE_COLUMNS) {
            failures.add("open-air structure has framing in only " + frameColumns.size()
                    + " columns; requires at least " + ABSOLUTE_MINIMUM_FACADE_COLUMNS
                    + " distributed frame columns instead of enclosed-building facade rules");
        }

        return new DetailProfile(
                snapshot.id(),
                area,
                detailCells,
                detailColumns.size(),
                detailRegions.size(),
                detailXBands.size(),
                detailZBands.size(),
                frame,
                opening,
                fixture,
                fixtureColumns.size(),
                decor,
                facadeColumns.size(),
                facadeSides.size(),
                facadeRegions.size(),
                projectedArchitecturalColumns.size(),
                counts.getOrDefault(DetailPhase.ROOF, 0),
                roofHeightRange,
                roofArticulationColumns.size(),
                roofArticulationRegions.size(),
                requiredDetailCells,
                requiredDetailColumns,
                requiredArchitecturalCells,
                failures);
    }

    public static CatalogReport validateSnapshots(Collection<DetailSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        List<DetailProfile> profiles = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (DetailSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!ids.add(snapshot.id())) {
                throw new IllegalArgumentException("Duplicate detail snapshot id: " + snapshot.id());
            }
            profiles.add(profile(snapshot));
        }
        profiles.sort(Comparator.comparing(DetailProfile::id));
        return new CatalogReport(profiles);
    }

    private static boolean isVisualDetail(DetailPhase phase) {
        return phase == DetailPhase.FRAME
                || phase == DetailPhase.OPENING
                || phase == DetailPhase.FIXTURE
                || phase == DetailPhase.DECOR;
    }

    private static boolean isArchitecturalDetail(DetailPhase phase) {
        return phase == DetailPhase.FRAME || phase == DetailPhase.OPENING;
    }

    private static boolean isStructural(DetailPhase phase) {
        return phase == DetailPhase.FOUNDATION
                || phase == DetailPhase.FRAME
                || phase == DetailPhase.SHELL
                || phase == DetailPhase.ROOF
                || phase == DetailPhase.OPENING;
    }

    private static boolean isRooflinePhase(DetailPhase phase) {
        return phase == DetailPhase.ROOF
                || phase == DetailPhase.FRAME
                || phase == DetailPhase.SHELL
                || phase == DetailPhase.DECOR;
    }

    private static HorizontalColumn column(Voxel position) {
        return new HorizontalColumn(position.x(), position.z());
    }

    private static HorizontalRegion region(
            DetailSnapshot snapshot, HorizontalColumn column) {
        return new HorizontalRegion(
                band(column.x(), snapshot.width()),
                band(column.z(), snapshot.depth()));
    }

    private static int band(int coordinate, int dimension) {
        long normalized = Math.max(0L, Math.min((long) dimension - 1L, coordinate));
        return Math.min(2, (int) (normalized * 3L / dimension));
    }

    /**
     * Keeps detached yard lamps and prop frames from masquerading as facade depth. Window jambs,
     * porches, bays, and arcades remain eligible because their frame/opening graph touches the
     * authored shell and is face-connected back to it.
     */
    private static Set<Voxel> attachedArchitecture(
            Set<Voxel> architecturalCells, Set<Voxel> shellCells) {
        Set<Voxel> attached = new HashSet<>();
        ArrayDeque<Voxel> frontier = new ArrayDeque<>();
        for (Voxel cell : architecturalCells) {
            if (neighbors(cell).stream().anyMatch(shellCells::contains) && attached.add(cell)) {
                frontier.addLast(cell);
            }
        }
        while (!frontier.isEmpty()) {
            Voxel current = frontier.removeFirst();
            for (Voxel neighbor : neighbors(current)) {
                if (architecturalCells.contains(neighbor) && attached.add(neighbor)) {
                    frontier.addLast(neighbor);
                }
            }
        }
        return attached;
    }

    private static List<Voxel> neighbors(Voxel cell) {
        return List.of(
                new Voxel(cell.x() - 1, cell.y(), cell.z()),
                new Voxel(cell.x() + 1, cell.y(), cell.z()),
                new Voxel(cell.x(), cell.y() - 1, cell.z()),
                new Voxel(cell.x(), cell.y() + 1, cell.z()),
                new Voxel(cell.x(), cell.y(), cell.z() - 1),
                new Voxel(cell.x(), cell.y(), cell.z() + 1));
    }

    private record HorizontalColumn(int x, int z) {
    }

    private record HorizontalRegion(int xBand, int zBand) {
    }

    private enum FacadeSide {
        NORTH(0, -1),
        SOUTH(0, 1),
        WEST(-1, 0),
        EAST(1, 0);

        private final int dx;
        private final int dz;

        FacadeSide(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }
}
