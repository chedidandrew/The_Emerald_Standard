package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BlueprintScale;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailCell;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailPhase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Scale-aware presentation gate for authored whole-building masters.
 *
 * <p>The ordinary detail-density gate rejects an empty box. This complementary gate asks whether
 * the detail reads as architecture from village-view distance: attached projections break up the
 * facade, multiple roof features punctuate increasingly important silhouettes, and the two
 * append-only dressing stages create a deliberate exterior scene. The requirements rise with
 * {@link BlueprintScale}, so a small cottage can remain restrained while a landmark must earn its
 * visual prominence.</p>
 *
 * <p>Snapshots retain the authored base/stage boundary from {@link WholeBuildingBlueprint.Stage}.
 * Consequently, interior furniture cannot masquerade as site dressing and optional yard props
 * cannot make a flush base shell appear architecturally deep. All measurements use loader-neutral
 * {@link Voxel} coordinates and semantic detail phases.</p>
 */
public final class WholeBuildingPresentationValidator {
    /** Keeps a distant prop field from being credited as part of the building's exterior scene. */
    private static final int MAXIMUM_SITE_DETAIL_DISTANCE = 10;

    private WholeBuildingPresentationValidator() {
    }

    /** Immutable maximum-stage presentation input for one authored master. */
    public record PresentationSnapshot(
            String id,
            BlueprintScale scale,
            int width,
            int depth,
            int height,
            boolean enclosed,
            Collection<DetailCell> baseCells,
            Collection<DetailCell> stageOneCells,
            Collection<DetailCell> stageTwoCells) {
        public PresentationSnapshot {
            id = Objects.requireNonNull(id, "id").trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Presentation snapshot id must not be blank");
            }
            scale = Objects.requireNonNull(scale, "scale");
            if (width <= 0 || depth <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "Presentation snapshot dimensions must be positive: " + id);
            }
            baseCells = immutableCells(baseCells, "baseCells", id);
            stageOneCells = immutableCells(stageOneCells, "stageOneCells", id);
            stageTwoCells = immutableCells(stageTwoCells, "stageTwoCells", id);
            if (baseCells.isEmpty()) {
                throw new IllegalArgumentException(
                        "Presentation snapshot base must not be empty: " + id);
            }

            Map<Voxel, String> occupied = new HashMap<>();
            checkDisjoint(occupied, baseCells, "base", id);
            checkDisjoint(occupied, stageOneCells, "stage one", id);
            checkDisjoint(occupied, stageTwoCells, "stage two", id);
            checkVerticalEnvelope(baseCells, "base", id, height);
            checkVerticalEnvelope(stageOneCells, "stage one", id, height);
            checkVerticalEnvelope(stageTwoCells, "stage two", id, height);
        }

        private static List<DetailCell> immutableCells(
                Collection<DetailCell> cells, String field, String id) {
            Objects.requireNonNull(cells, field);
            List<DetailCell> copy = List.copyOf(cells);
            for (DetailCell cell : copy) {
                Objects.requireNonNull(cell, field + " cell in " + id);
            }
            return copy;
        }

        private static void checkDisjoint(
                Map<Voxel, String> occupied,
                Collection<DetailCell> cells,
                String layer,
                String id) {
            for (DetailCell cell : cells) {
                String previous = occupied.putIfAbsent(cell.position(), layer);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate presentation cell at " + cell.position() + " in " + id
                                    + " (" + previous + " and " + layer + ')');
                }
            }
        }

        private static void checkVerticalEnvelope(
                Collection<DetailCell> cells,
                String layer,
                String id,
                int maximumY) {
            for (DetailCell cell : cells) {
                if (cell.position().y() < 0 || cell.position().y() > maximumY) {
                    throw new IllegalArgumentException(
                            "Presentation cell exceeds the inclusive vertical envelope at "
                                    + cell.position() + " in " + id + " (" + layer + ')');
                }
            }
        }
    }

    /** Minimum visible composition required for one footprint class. */
    public record Requirements(
            int facadeProjectionColumns,
            int facadeProjectionRegions,
            int facadeArticulationColumns,
            int facadeArticulationRegions,
            int roofPunctuationFeatures,
            int roofPunctuationRegions,
            int siteDetailCells,
            int siteDetailColumns,
            int siteCompositions,
            int siteSides) {
    }

    /** Deterministic measurements and actionable failures for one master. */
    public record PresentationProfile(
            String id,
            BlueprintScale scale,
            int facadeProjectionColumns,
            int facadeProjectionRegions,
            int facadeArticulationColumns,
            int facadeArticulationRegions,
            int roofPeakFeatures,
            int roofAccentFeatures,
            int roofPunctuationFeatures,
            int roofPunctuationColumns,
            int roofPunctuationRegions,
            int siteDetailCells,
            int siteDetailColumns,
            int siteCompositions,
            int siteSides,
            int stageOneSiteDetailCells,
            int stageTwoSiteDetailCells,
            Requirements requirements,
            List<String> failures) {
        public PresentationProfile {
            id = Objects.requireNonNull(id, "id");
            scale = Objects.requireNonNull(scale, "scale");
            requirements = Objects.requireNonNull(requirements, "requirements");
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        }

        public boolean presentable() {
            return failures.isEmpty();
        }
    }

    /** Stable-id-ordered report for catalog admission. */
    public record CatalogReport(List<PresentationProfile> profiles) {
        public CatalogReport {
            profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        }

        public List<PresentationProfile> insufficientProfiles() {
            return profiles.stream().filter(profile -> !profile.presentable()).toList();
        }

        public boolean presentable() {
            return insufficientProfiles().isEmpty();
        }

        public void requirePresentable() {
            List<PresentationProfile> insufficient = insufficientProfiles();
            if (insufficient.isEmpty()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Authored building catalog contains visually under-presented structures:");
            for (PresentationProfile profile : insufficient) {
                message.append(System.lineSeparator())
                        .append(" - ").append(profile.id())
                        .append(" [").append(profile.scale()).append("]: ")
                        .append(String.join("; ", profile.failures()))
                        .append(" [facade projected columns/regions ")
                        .append(profile.facadeProjectionColumns()).append('/')
                        .append(profile.facadeProjectionRegions())
                        .append("; facade articulated columns/regions ")
                        .append(profile.facadeArticulationColumns()).append('/')
                        .append(profile.facadeArticulationRegions())
                        .append("; roof peak/accent candidates/physical features/footprint columns/anchor regions ")
                        .append(profile.roofPeakFeatures()).append('/')
                        .append(profile.roofAccentFeatures()).append('/')
                        .append(profile.roofPunctuationFeatures()).append('/')
                        .append(profile.roofPunctuationColumns()).append('/')
                        .append(profile.roofPunctuationRegions())
                        .append("; site cells/columns/compositions/sides/stages ")
                        .append(profile.siteDetailCells()).append('/')
                        .append(profile.siteDetailColumns()).append('/')
                        .append(profile.siteCompositions()).append('/')
                        .append(profile.siteSides()).append('/')
                        .append(profile.stageOneSiteDetailCells()).append('-')
                        .append(profile.stageTwoSiteDetailCells()).append(']');
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    /** Public so catalog-policy regressions can prevent later threshold erosion. */
    public static Requirements requirements(BlueprintScale scale) {
        Objects.requireNonNull(scale, "scale");
        return switch (scale) {
            case SMALL -> new Requirements(2, 1, 4, 3, 1, 1, 3, 2, 1, 2);
            case MEDIUM -> new Requirements(3, 2, 6, 3, 2, 2, 5, 3, 2, 2);
            case LARGE -> new Requirements(5, 3, 9, 4, 3, 3, 8, 5, 2, 3);
            case LANDMARK -> new Requirements(8, 4, 14, 5, 4, 4, 12, 8, 3, 3);
        };
    }

    public static PresentationProfile profile(PresentationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Requirements required = requirements(snapshot.scale());

        Set<Voxel> baseStructural = positions(snapshot.baseCells(),
                Set.of(DetailPhase.FOUNDATION, DetailPhase.FRAME, DetailPhase.SHELL,
                        DetailPhase.ROOF, DetailPhase.OPENING));
        Set<Voxel> shell = positions(snapshot.baseCells(), Set.of(DetailPhase.SHELL));
        Set<Voxel> architecture = positions(snapshot.baseCells(),
                Set.of(DetailPhase.FRAME, DetailPhase.OPENING));
        Set<Voxel> facadeStructure = positions(snapshot.baseCells(),
                Set.of(DetailPhase.SHELL, DetailPhase.FRAME, DetailPhase.OPENING));
        Set<Voxel> foundations = positions(snapshot.baseCells(),
                Set.of(DetailPhase.FOUNDATION));
        Set<Voxel> attachedArchitecture = snapshot.enclosed()
                ? attachedToShell(architecture, shell)
                : architecture;
        Set<HorizontalColumn> groundedLocalArchitecture = snapshot.enclosed()
                ? Set.of()
                : groundedLocalArchitectureColumns(
                        snapshot, architecture, facadeStructure, foundations);
        Set<HorizontalColumn> projectedColumns = new HashSet<>();
        Set<HorizontalRegion> projectedRegions = new HashSet<>();
        Set<HorizontalColumn> articulatedColumns = new HashSet<>();
        Set<HorizontalRegion> articulatedRegions = new HashSet<>();
        Map<Integer, Set<HorizontalColumn>> exteriorAirByHeight = exteriorAirByHeight(
                snapshot, baseStructural, attachedArchitecture);
        for (Voxel cell : attachedArchitecture) {
            HorizontalColumn column = column(cell);
            Set<HorizontalColumn> exteriorAir = exteriorAirByHeight.getOrDefault(
                    cell.y(), Set.of());
            int exteriorFaces = exteriorHorizontalFaces(
                    cell, exteriorAir);
            if (exteriorFaces >= 2
                    && (hasOpposingFacadeFace(
                            snapshot, cell, facadeStructure, exteriorAir)
                    || groundedLocalArchitecture.contains(column))) {
                projectedColumns.add(column);
                projectedRegions.add(region(snapshot, column));
            }
            if (exteriorFaces >= 1) {
                articulatedColumns.add(column);
                articulatedRegions.add(region(snapshot, column));
            }
        }

        RoofProfile roof = roofProfile(snapshot);
        SiteProfile stageOne = siteProfile(snapshot, snapshot.stageOneCells());
        SiteProfile stageTwo = siteProfile(snapshot, snapshot.stageTwoCells());
        SiteProfile completeSite = siteProfile(
                snapshot, concatenate(snapshot.stageOneCells(), snapshot.stageTwoCells()));

        List<String> failures = new ArrayList<>();
        if (projectedColumns.size() < required.facadeProjectionColumns()
                || projectedRegions.size() < required.facadeProjectionRegions()) {
            failures.add("facade depth projects into " + projectedColumns.size()
                    + " columns across " + projectedRegions.size() + " regions; "
                    + snapshot.scale() + " requires at least "
                    + required.facadeProjectionColumns() + '/'
                    + required.facadeProjectionRegions());
        }
        if (articulatedColumns.size() < required.facadeArticulationColumns()
                || articulatedRegions.size() < required.facadeArticulationRegions()) {
            failures.add("facade articulation reaches " + articulatedColumns.size()
                    + " exposed columns across " + articulatedRegions.size() + " regions; "
                    + snapshot.scale() + " requires at least "
                    + required.facadeArticulationColumns() + '/'
                    + required.facadeArticulationRegions());
        }
        if (roof.features() < required.roofPunctuationFeatures()
                || roof.regions().size() < required.roofPunctuationRegions()) {
            failures.add("roof punctuation provides " + roof.features()
                    + " physical features across " + roof.regions().size()
                    + " anchor regions (" + roof.columns().size()
                    + " footprint columns); "
                    + snapshot.scale() + " requires at least "
                    + required.roofPunctuationFeatures() + '/'
                    + required.roofPunctuationRegions()
                    + " (separate ridges, towers, dormers, monitors, chimneys, or signs qualify)");
        }
        if (completeSite.visualCells() < required.siteDetailCells()
                || completeSite.visualColumns().size() < required.siteDetailColumns()
                || completeSite.compositions() < required.siteCompositions()
                || completeSite.sides().size() < required.siteSides()) {
            failures.add("contextual site dressing reaches " + completeSite.visualCells()
                    + " cells in " + completeSite.visualColumns().size() + " columns, "
                    + completeSite.compositions() + " compositions, and "
                    + completeSite.sides().size() + " sides; " + snapshot.scale()
                    + " requires at least " + required.siteDetailCells() + '/'
                    + required.siteDetailColumns() + '/'
                    + required.siteCompositions() + '/' + required.siteSides());
        }
        if (stageOne.visualCells() == 0 || stageTwo.visualCells() == 0) {
            failures.add("both append-only dressing stages must contribute exterior site detail; "
                    + "stage one/two provide " + stageOne.visualCells() + '/'
                    + stageTwo.visualCells() + " cells");
        }

        return new PresentationProfile(
                snapshot.id(),
                snapshot.scale(),
                projectedColumns.size(),
                projectedRegions.size(),
                articulatedColumns.size(),
                articulatedRegions.size(),
                roof.peakFeatures(),
                roof.accentFeatures(),
                roof.features(),
                roof.columns().size(),
                roof.regions().size(),
                completeSite.visualCells(),
                completeSite.visualColumns().size(),
                completeSite.compositions(),
                completeSite.sides().size(),
                stageOne.visualCells(),
                stageTwo.visualCells(),
                required,
                failures);
    }

    public static CatalogReport validateSnapshots(Collection<PresentationSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        List<PresentationProfile> profiles = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (PresentationSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!ids.add(snapshot.id())) {
                throw new IllegalArgumentException(
                        "Duplicate presentation snapshot id: " + snapshot.id());
            }
            profiles.add(profile(snapshot));
        }
        profiles.sort(Comparator.comparing(PresentationProfile::id));
        return new CatalogReport(profiles);
    }

    private static RoofProfile roofProfile(PresentationSnapshot snapshot) {
        Map<HorizontalColumn, Integer> roofTop = new HashMap<>();
        Set<Voxel> roofCells = new HashSet<>();
        int minimumRoofY = Integer.MAX_VALUE;
        for (DetailCell cell : snapshot.baseCells()) {
            if (cell.phase() == DetailPhase.ROOF) {
                roofCells.add(cell.position());
                roofTop.merge(column(cell.position()), cell.position().y(), Math::max);
                minimumRoofY = Math.min(minimumRoofY, cell.position().y());
            }
        }
        if (roofCells.isEmpty()) {
            return new RoofProfile(0, 0, 0, Set.of(), Set.of());
        }

        Set<HorizontalColumn> localMaxima = new HashSet<>();
        for (Map.Entry<HorizontalColumn, Integer> entry : roofTop.entrySet()) {
            HorizontalColumn column = entry.getKey();
            int top = entry.getValue();
            if (top <= minimumRoofY) {
                continue;
            }
            boolean higherNeighbor = false;
            for (int dx = -1; dx <= 1 && !higherNeighbor; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    Integer neighbor = roofTop.get(new HorizontalColumn(
                            column.x() + dx, column.z() + dz));
                    if (neighbor != null && neighbor > top) {
                        higherNeighbor = true;
                        break;
                    }
                }
            }
            if (!higherNeighbor) {
                localMaxima.add(column);
            }
        }
        List<Set<HorizontalColumn>> peakFootprints = horizontalComponentSets(localMaxima);

        Set<Voxel> upperAccents = new HashSet<>();
        for (DetailCell cell : snapshot.baseCells()) {
            if (isRoofAccentPhase(cell.phase())
                    && protrudesAboveLocalRoof(cell.position(), roofTop)) {
                upperAccents.add(cell.position());
            }
        }
        List<Set<Voxel>> accentComponents = components(upperAccents);
        List<Set<HorizontalColumn>> accentFootprints = new ArrayList<>();
        int accentFeatures = 0;
        for (Set<Voxel> component : accentComponents) {
            if (component.size() >= 2
                    && component.stream().anyMatch(cell -> touchesRoof(cell, roofCells))) {
                accentFeatures++;
                accentFootprints.add(columns(component));
            }
        }

        List<Set<HorizontalColumn>> physicalFeatures = mergeNearbyFootprints(
                accentFootprints, 1);
        for (Set<HorizontalColumn> peak : peakFootprints) {
            mergePeakFootprint(physicalFeatures, peak);
        }

        Set<HorizontalColumn> punctuationColumns = new HashSet<>();
        Set<HorizontalRegion> punctuationRegions = new HashSet<>();
        for (Set<HorizontalColumn> feature : physicalFeatures) {
            punctuationColumns.addAll(feature);
            punctuationRegions.add(region(snapshot, featureAnchor(feature)));
        }
        return new RoofProfile(
                peakFootprints.size(),
                accentFeatures,
                physicalFeatures.size(),
                Set.copyOf(punctuationColumns),
                Set.copyOf(punctuationRegions));
    }

    private static SiteProfile siteProfile(
            PresentationSnapshot snapshot, Collection<DetailCell> stageCells) {
        Map<Voxel, DetailPhase> outsideCells = new HashMap<>();
        Set<Voxel> visible = new HashSet<>();
        Set<SiteSide> sides = new HashSet<>();
        for (DetailCell cell : stageCells) {
            if (!isContextualSitePosition(snapshot, cell.position())) {
                continue;
            }
            outsideCells.put(cell.position(), cell.phase());
            if (isSiteDetailPhase(cell.phase())) {
                visible.add(cell.position());
            }
        }
        int compositions = 0;
        Set<Voxel> groundedVisible = new HashSet<>();
        for (Set<Voxel> component : components(outsideCells.keySet())) {
            boolean grounded = component.stream().anyMatch(cell ->
                    cell.y() <= 0 && outsideCells.get(cell) == DetailPhase.FOUNDATION);
            if (!grounded) {
                continue;
            }
            Set<Voxel> componentVisible = new HashSet<>();
            for (Voxel cell : component) {
                if (visible.contains(cell)) {
                    componentVisible.add(cell);
                    sides.add(primarySide(snapshot, cell));
                }
            }
            if (!componentVisible.isEmpty()) {
                groundedVisible.addAll(componentVisible);
                compositions++;
            }
        }
        return new SiteProfile(
                groundedVisible.size(), columns(groundedVisible), compositions, Set.copyOf(sides));
    }

    private static boolean isContextualSitePosition(
            PresentationSnapshot snapshot, Voxel cell) {
        int west = Math.max(0, -cell.x());
        int east = Math.max(0, cell.x() - (snapshot.width() - 1));
        int north = Math.max(0, -cell.z());
        int south = Math.max(0, cell.z() - (snapshot.depth() - 1));
        int distance = Math.max(Math.max(west, east), Math.max(north, south));
        return distance > 0 && distance <= MAXIMUM_SITE_DETAIL_DISTANCE;
    }

    /** A corner prop belongs to its dominant setback and cannot manufacture two dressed sides. */
    private static SiteSide primarySide(PresentationSnapshot snapshot, Voxel cell) {
        Map<SiteSide, Integer> setbacks = new LinkedHashMap<>();
        setbacks.put(SiteSide.WEST, Math.max(0, -cell.x()));
        setbacks.put(SiteSide.EAST, Math.max(0, cell.x() - (snapshot.width() - 1)));
        setbacks.put(SiteSide.NORTH, Math.max(0, -cell.z()));
        setbacks.put(SiteSide.SOUTH, Math.max(0, cell.z() - (snapshot.depth() - 1)));
        return setbacks.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow()
                .getKey();
    }

    private static boolean isSiteDetailPhase(DetailPhase phase) {
        return phase == DetailPhase.FRAME
                || phase == DetailPhase.OPENING
                || phase == DetailPhase.FIXTURE
                || phase == DetailPhase.DECOR;
    }

    private static boolean isRoofAccentPhase(DetailPhase phase) {
        return phase == DetailPhase.FRAME
                || phase == DetailPhase.OPENING
                || phase == DetailPhase.FIXTURE
                || phase == DetailPhase.DECOR;
    }

    /** Continuous eave trim is not punctuation; a credited accent must clear nearby roof mass. */
    private static boolean protrudesAboveLocalRoof(
            Voxel cell, Map<HorizontalColumn, Integer> roofTop) {
        int localRoof = Integer.MIN_VALUE;
        HorizontalColumn column = column(cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Integer top = roofTop.get(new HorizontalColumn(
                        column.x() + dx, column.z() + dz));
                if (top != null) {
                    localRoof = Math.max(localRoof, top);
                }
            }
        }
        return localRoof != Integer.MIN_VALUE && cell.y() > localRoof;
    }

    private static boolean touchesRoof(Voxel cell, Set<Voxel> roofCells) {
        return neighbors(cell).stream().anyMatch(roofCells::contains);
    }

    /**
     * A projected frame may bear against any in-footprint facade structure, not only wall infill.
     * This credits window surrounds and timber frames while preventing one projected ribbon from
     * recursively legitimizing another ribbon farther into the yard.
     */
    private static boolean hasOpposingFacadeFace(
            PresentationSnapshot snapshot,
            Voxel cell,
            Set<Voxel> facadeStructure,
            Set<HorizontalColumn> exteriorAir) {
        HorizontalColumn origin = column(cell);
        for (HorizontalColumn outside : horizontalNeighbors(origin)) {
            if (!exteriorAir.contains(outside)) {
                continue;
            }
            int dx = outside.x() - origin.x();
            int dz = outside.z() - origin.z();
            Voxel bearing = cell.offset(-dx, 0, -dz);
            if (insideFootprint(snapshot, bearing)
                    && facadeStructure.contains(bearing)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exposed post-and-beam buildings have no wall plane for a post to oppose. A column is still
     * real facade depth when it is within one block of the authored footprint and has a continuous
     * vertical load path through facade structure to an authored foundation. The per-column load
     * path deliberately rejects remote, one-layer frame tendrils even when they touch a legitimate
     * beam elsewhere in the component.
     */
    private static Set<HorizontalColumn> groundedLocalArchitectureColumns(
            PresentationSnapshot snapshot,
            Set<Voxel> architecture,
            Set<Voxel> facadeStructure,
            Set<Voxel> foundations) {
        Set<HorizontalColumn> grounded = new HashSet<>();
        for (Voxel cell : architecture) {
            HorizontalColumn candidate = column(cell);
            if (grounded.contains(candidate) || !withinFacadeSetback(snapshot, candidate)) {
                continue;
            }
            Voxel cursor = cell;
            while (cursor.y() > 0) {
                cursor = cursor.offset(0, -1, 0);
                if (foundations.contains(cursor)) {
                    grounded.add(candidate);
                    break;
                }
                if (!facadeStructure.contains(cursor)) {
                    break;
                }
            }
        }
        return Set.copyOf(grounded);
    }

    private static boolean insideFootprint(PresentationSnapshot snapshot, Voxel cell) {
        return cell.x() >= 0 && cell.x() < snapshot.width()
                && cell.z() >= 0 && cell.z() < snapshot.depth();
    }

    private static boolean withinFacadeSetback(
            PresentationSnapshot snapshot, HorizontalColumn column) {
        return column.x() >= -1 && column.x() <= snapshot.width()
                && column.z() >= -1 && column.z() <= snapshot.depth();
    }

    /**
     * Finds horizontal air that is actually reachable from outside on every occupied facade slice.
     * A missing neighbor in a sealed room is therefore not mistaken for visible articulation.
     */
    private static Map<Integer, Set<HorizontalColumn>> exteriorAirByHeight(
            PresentationSnapshot snapshot,
            Set<Voxel> structural,
            Set<Voxel> facadeCandidates) {
        Map<Integer, Set<HorizontalColumn>> structuralByHeight = new HashMap<>();
        for (Voxel cell : structural) {
            structuralByHeight.computeIfAbsent(cell.y(), ignored -> new HashSet<>())
                    .add(column(cell));
        }

        Map<Integer, Set<HorizontalColumn>> exteriorByHeight = new HashMap<>();
        Set<Integer> heights = new HashSet<>();
        for (Voxel candidate : facadeCandidates) {
            heights.add(candidate.y());
        }
        for (int y : heights) {
            Set<HorizontalColumn> obstacles = structuralByHeight.getOrDefault(y, Set.of());
            int minimumX = -1;
            int maximumX = snapshot.width();
            int minimumZ = -1;
            int maximumZ = snapshot.depth();
            for (HorizontalColumn obstacle : obstacles) {
                minimumX = Math.min(minimumX, obstacle.x() - 1);
                maximumX = Math.max(maximumX, obstacle.x() + 1);
                minimumZ = Math.min(minimumZ, obstacle.z() - 1);
                maximumZ = Math.max(maximumZ, obstacle.z() + 1);
            }

            Set<HorizontalColumn> exterior = new HashSet<>();
            ArrayDeque<HorizontalColumn> frontier = new ArrayDeque<>();
            for (int x = minimumX; x <= maximumX; x++) {
                addExteriorSeed(new HorizontalColumn(x, minimumZ), obstacles, exterior, frontier);
                addExteriorSeed(new HorizontalColumn(x, maximumZ), obstacles, exterior, frontier);
            }
            for (int z = minimumZ + 1; z < maximumZ; z++) {
                addExteriorSeed(new HorizontalColumn(minimumX, z), obstacles, exterior, frontier);
                addExteriorSeed(new HorizontalColumn(maximumX, z), obstacles, exterior, frontier);
            }
            while (!frontier.isEmpty()) {
                HorizontalColumn current = frontier.removeFirst();
                for (HorizontalColumn neighbor : horizontalNeighbors(current)) {
                    if (neighbor.x() < minimumX || neighbor.x() > maximumX
                            || neighbor.z() < minimumZ || neighbor.z() > maximumZ
                            || obstacles.contains(neighbor) || !exterior.add(neighbor)) {
                        continue;
                    }
                    frontier.addLast(neighbor);
                }
            }
            exteriorByHeight.put(y, Set.copyOf(exterior));
        }
        return Map.copyOf(exteriorByHeight);
    }

    private static void addExteriorSeed(
            HorizontalColumn seed,
            Set<HorizontalColumn> obstacles,
            Set<HorizontalColumn> exterior,
            ArrayDeque<HorizontalColumn> frontier) {
        if (!obstacles.contains(seed) && exterior.add(seed)) {
            frontier.addLast(seed);
        }
    }

    private static int exteriorHorizontalFaces(
            Voxel cell, Set<HorizontalColumn> exteriorAir) {
        int faces = 0;
        for (HorizontalColumn neighbor : horizontalNeighbors(column(cell))) {
            if (exteriorAir.contains(neighbor)) {
                faces++;
            }
        }
        return faces;
    }

    private static Set<Voxel> attachedToShell(Set<Voxel> architecture, Set<Voxel> shell) {
        Set<Voxel> attached = new HashSet<>();
        ArrayDeque<Voxel> frontier = new ArrayDeque<>();
        for (Voxel cell : architecture) {
            if (neighbors(cell).stream().anyMatch(shell::contains) && attached.add(cell)) {
                frontier.addLast(cell);
            }
        }
        while (!frontier.isEmpty()) {
            Voxel current = frontier.removeFirst();
            for (Voxel neighbor : neighbors(current)) {
                if (architecture.contains(neighbor) && attached.add(neighbor)) {
                    frontier.addLast(neighbor);
                }
            }
        }
        return attached;
    }

    private static Set<Voxel> positions(
            Collection<DetailCell> cells, Set<DetailPhase> phases) {
        Set<Voxel> positions = new HashSet<>();
        for (DetailCell cell : cells) {
            if (phases.contains(cell.phase())) {
                positions.add(cell.position());
            }
        }
        return positions;
    }

    private static Set<HorizontalColumn> columns(Collection<Voxel> cells) {
        Set<HorizontalColumn> columns = new HashSet<>();
        for (Voxel cell : cells) {
            columns.add(column(cell));
        }
        return columns;
    }

    private static List<Set<Voxel>> components(Set<Voxel> cells) {
        Set<Voxel> remaining = new HashSet<>(cells);
        List<Set<Voxel>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Voxel seed = remaining.iterator().next();
            remaining.remove(seed);
            Set<Voxel> component = new HashSet<>();
            component.add(seed);
            ArrayDeque<Voxel> frontier = new ArrayDeque<>();
            frontier.addLast(seed);
            while (!frontier.isEmpty()) {
                Voxel current = frontier.removeFirst();
                for (Voxel neighbor : neighbors(current)) {
                    if (remaining.remove(neighbor)) {
                        component.add(neighbor);
                        frontier.addLast(neighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private static List<Set<HorizontalColumn>> horizontalComponentSets(
            Set<HorizontalColumn> columns) {
        Set<HorizontalColumn> remaining = new HashSet<>(columns);
        List<Set<HorizontalColumn>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            HorizontalColumn seed = remaining.iterator().next();
            remaining.remove(seed);
            Set<HorizontalColumn> component = new HashSet<>();
            component.add(seed);
            ArrayDeque<HorizontalColumn> frontier = new ArrayDeque<>();
            frontier.addLast(seed);
            while (!frontier.isEmpty()) {
                HorizontalColumn current = frontier.removeFirst();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        HorizontalColumn neighbor = new HorizontalColumn(
                                current.x() + dx, current.z() + dz);
                        if (remaining.remove(neighbor)) {
                            component.add(neighbor);
                            frontier.addLast(neighbor);
                        }
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private static List<Set<HorizontalColumn>> mergeNearbyFootprints(
            Collection<Set<HorizontalColumn>> footprints, int distance) {
        List<Set<HorizontalColumn>> merged = new ArrayList<>();
        for (Set<HorizontalColumn> footprint : footprints) {
            merged.add(new HashSet<>(footprint));
        }
        for (int first = 0; first < merged.size(); first++) {
            int second = first + 1;
            while (second < merged.size()) {
                if (footprintsNear(merged.get(first), merged.get(second), distance)) {
                    merged.get(first).addAll(merged.remove(second));
                    second = first + 1;
                } else {
                    second++;
                }
            }
        }
        return merged;
    }

    /**
     * A dormer/chimney whose footprint coincides with a roof maximum is one physical feature.
     * A broad ridge is deliberately prevented from bridging several otherwise distinct accents.
     */
    private static void mergePeakFootprint(
            List<Set<HorizontalColumn>> physicalFeatures,
            Set<HorizontalColumn> peak) {
        List<Integer> nearby = new ArrayList<>();
        for (int index = 0; index < physicalFeatures.size(); index++) {
            if (footprintsNear(peak, physicalFeatures.get(index), 1)) {
                nearby.add(index);
            }
        }
        if (nearby.isEmpty()) {
            physicalFeatures.add(new HashSet<>(peak));
            return;
        }
        if (nearby.size() == 1) {
            physicalFeatures.get(nearby.getFirst()).addAll(peak);
            return;
        }
        if (!localizedFootprint(peak)) {
            return;
        }

        Set<HorizontalColumn> merged = new HashSet<>(peak);
        for (int position = nearby.size() - 1; position >= 0; position--) {
            merged.addAll(physicalFeatures.remove((int) nearby.get(position)));
        }
        physicalFeatures.add(merged);
    }

    private static boolean localizedFootprint(Set<HorizontalColumn> footprint) {
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (HorizontalColumn column : footprint) {
            minimumX = Math.min(minimumX, column.x());
            maximumX = Math.max(maximumX, column.x());
            minimumZ = Math.min(minimumZ, column.z());
            maximumZ = Math.max(maximumZ, column.z());
        }
        return maximumX - minimumX <= 4 && maximumZ - minimumZ <= 4;
    }

    private static boolean footprintsNear(
            Set<HorizontalColumn> first,
            Set<HorizontalColumn> second,
            int distance) {
        for (HorizontalColumn left : first) {
            for (HorizontalColumn right : second) {
                if (Math.abs(left.x() - right.x()) <= distance
                        && Math.abs(left.z() - right.z()) <= distance) {
                    return true;
                }
            }
        }
        return false;
    }

    private static HorizontalColumn featureAnchor(Set<HorizontalColumn> footprint) {
        long x = 0;
        long z = 0;
        for (HorizontalColumn column : footprint) {
            x += column.x();
            z += column.z();
        }
        return new HorizontalColumn(
                (int) Math.round((double) x / footprint.size()),
                (int) Math.round((double) z / footprint.size()));
    }

    private static List<HorizontalColumn> horizontalNeighbors(HorizontalColumn column) {
        return List.of(
                new HorizontalColumn(column.x() - 1, column.z()),
                new HorizontalColumn(column.x() + 1, column.z()),
                new HorizontalColumn(column.x(), column.z() - 1),
                new HorizontalColumn(column.x(), column.z() + 1));
    }

    private static List<Voxel> neighbors(Voxel cell) {
        return List.of(
                cell.offset(-1, 0, 0),
                cell.offset(1, 0, 0),
                cell.offset(0, -1, 0),
                cell.offset(0, 1, 0),
                cell.offset(0, 0, -1),
                cell.offset(0, 0, 1));
    }

    private static HorizontalColumn column(Voxel cell) {
        return new HorizontalColumn(cell.x(), cell.z());
    }

    private static HorizontalRegion region(
            PresentationSnapshot snapshot, HorizontalColumn column) {
        return new HorizontalRegion(
                band(column.x(), snapshot.width()),
                band(column.z(), snapshot.depth()));
    }

    private static int band(int coordinate, int dimension) {
        long normalized = Math.max(0L, Math.min((long) dimension - 1L, coordinate));
        return Math.min(2, (int) (normalized * 3L / dimension));
    }

    private static List<DetailCell> concatenate(
            Collection<DetailCell> first, Collection<DetailCell> second) {
        List<DetailCell> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    private record HorizontalColumn(int x, int z) {
    }

    private record HorizontalRegion(int xBand, int zBand) {
    }

    private enum SiteSide {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    private record RoofProfile(
            int peakFeatures,
            int accentFeatures,
            int features,
            Set<HorizontalColumn> columns,
            Set<HorizontalRegion> regions) {
    }

    private record SiteProfile(
            int visualCells,
            Set<HorizontalColumn> visualColumns,
            int compositions,
            Set<SiteSide> sides) {
    }
}
