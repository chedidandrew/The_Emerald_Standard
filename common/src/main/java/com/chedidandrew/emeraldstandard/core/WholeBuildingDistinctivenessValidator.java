package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Placement;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Role;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Catalog-level admission check for authored buildings that are too geometrically similar.
 *
 * <p>The comparison is deliberately palette-blind. It retains only load-bearing/exterior roles,
 * projects their occupied volume from above and both horizontal axes, and compares the normalized
 * roof-height massing. All eight horizontal rotations/reflections are considered, so recoloring,
 * mirroring, rotating, or uniformly scaling the same building cannot manufacture a new design.</p>
 *
 * <p>Fixtures and decorative dressing are excluded because moving a bed, lantern, or flower pot
 * does not make a repeated shell visually distinctive. Minecraft-backed catalogs can use
 * {@link StructuralSnapshot} directly and translate their structural block coordinates to the
 * loader-neutral {@link Voxel} type.</p>
 */
public final class WholeBuildingDistinctivenessValidator {
    /** Conservative catalog gate: only very close massing/silhouette pairs are rejected. */
    public static final double DEFAULT_MAX_SIMILARITY = 0.90;

    private static final int RASTER_SIZE = 32;
    private static final int RASTER_PADDING = 1;
    private static final double BOUNDS_TRIM_FRACTION = 0.01;
    private static final Set<Role> STRUCTURAL_ROLES = EnumSet.of(
            Role.FOUNDATION,
            Role.FLOOR,
            Role.WALL,
            Role.ROOF,
            Role.WINDOW,
            Role.DOOR,
            Role.SUPPORT,
            Role.STAIR);

    private WholeBuildingDistinctivenessValidator() {
    }

    /** Palette-free occupied cells used by the catalog comparison. */
    public record StructuralSnapshot(String id, Set<Voxel> structuralCells) {
        public StructuralSnapshot {
            Objects.requireNonNull(id, "id");
            id = id.trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Structural snapshot id must not be blank");
            }
            structuralCells = Set.copyOf(
                    Objects.requireNonNull(structuralCells, "structuralCells"));
            if (structuralCells.isEmpty()) {
                throw new IllegalArgumentException(
                        "Structural snapshot must contain at least one cell: " + id);
            }
        }
    }

    /** Similarity of one catalog pair after choosing its closest rotation/reflection. */
    public record PairSimilarity(
            String firstId,
            String secondId,
            double similarity,
            double topSimilarity,
            double frontSimilarity,
            double sideSimilarity,
            double heightMapSimilarity) {
        public PairSimilarity {
            Objects.requireNonNull(firstId, "firstId");
            Objects.requireNonNull(secondId, "secondId");
            requireUnitInterval(similarity, "similarity");
            requireUnitInterval(topSimilarity, "topSimilarity");
            requireUnitInterval(frontSimilarity, "frontSimilarity");
            requireUnitInterval(sideSimilarity, "sideSimilarity");
            requireUnitInterval(heightMapSimilarity, "heightMapSimilarity");
        }
    }

    /** Deterministic all-pairs report, sorted from most similar to least similar. */
    public record CatalogReport(
            double maximumAllowedSimilarity,
            List<PairSimilarity> comparisons) {
        public CatalogReport {
            requireUnitInterval(maximumAllowedSimilarity, "maximumAllowedSimilarity");
            comparisons = List.copyOf(Objects.requireNonNull(comparisons, "comparisons"));
        }

        public List<PairSimilarity> excessivePairs() {
            return comparisons.stream()
                    .filter(pair -> pair.similarity() > maximumAllowedSimilarity)
                    .toList();
        }

        public boolean distinct() {
            return excessivePairs().isEmpty();
        }

        /** Throws one catalog-admission error naming every pair above the similarity ceiling. */
        public void requireDistinct() {
            List<PairSimilarity> excessive = excessivePairs();
            if (excessive.isEmpty()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Authored building catalog contains excessively similar structures:");
            for (PairSimilarity pair : excessive) {
                message.append(System.lineSeparator())
                        .append(" - ")
                        .append(pair.firstId())
                        .append(" <> ")
                        .append(pair.secondId())
                        .append(": ")
                        .append(String.format(Locale.ROOT, "%.3f", pair.similarity()))
                        .append(" (maximum ")
                        .append(String.format(Locale.ROOT, "%.3f", maximumAllowedSimilarity))
                        .append("; top/front/side/height ")
                        .append(String.format(
                                Locale.ROOT,
                                "%.3f/%.3f/%.3f/%.3f",
                                pair.topSimilarity(),
                                pair.frontSimilarity(),
                                pair.sideSimilarity(),
                                pair.heightMapSimilarity()))
                        .append(')');
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    /**
     * Extracts structural geometry from every append-only stage without consulting material ids
     * or palette slots. Furnishings and cosmetic roles remain excluded even when a later stage
     * adds them; genuine structural additions remain part of the finished silhouette.
     */
    public static StructuralSnapshot snapshot(WholeBuildingBlueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        Set<Voxel> structuralCells = new HashSet<>();
        for (WholeBuildingBlueprint.Stage stage : blueprint.stages()) {
            for (Placement placement : stage.additions()) {
                if (STRUCTURAL_ROLES.contains(placement.cell().role())) {
                    structuralCells.add(placement.position());
                }
            }
        }
        return new StructuralSnapshot(blueprint.id(), structuralCells);
    }

    public static CatalogReport validateBlueprints(
            Collection<WholeBuildingBlueprint> blueprints) {
        Objects.requireNonNull(blueprints, "blueprints");
        return validateSnapshots(
                blueprints.stream().map(WholeBuildingDistinctivenessValidator::snapshot).toList(),
                DEFAULT_MAX_SIMILARITY);
    }

    public static CatalogReport validateSnapshots(Collection<StructuralSnapshot> snapshots) {
        return validateSnapshots(snapshots, DEFAULT_MAX_SIMILARITY);
    }

    public static CatalogReport validateSnapshots(
            Collection<StructuralSnapshot> snapshots,
            double maximumAllowedSimilarity) {
        Objects.requireNonNull(snapshots, "snapshots");
        requireUnitInterval(maximumAllowedSimilarity, "maximumAllowedSimilarity");
        List<StructuralSnapshot> catalog = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (StructuralSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!ids.add(snapshot.id())) {
                throw new IllegalArgumentException(
                        "Duplicate structural snapshot id: " + snapshot.id());
            }
            catalog.add(snapshot);
        }
        catalog.sort(Comparator.comparing(StructuralSnapshot::id));

        List<PairSimilarity> comparisons = new ArrayList<>();
        for (int first = 0; first < catalog.size(); first++) {
            for (int second = first + 1; second < catalog.size(); second++) {
                comparisons.add(compare(catalog.get(first), catalog.get(second)));
            }
        }
        comparisons.sort(Comparator
                .comparingDouble(PairSimilarity::similarity)
                .reversed()
                .thenComparing(PairSimilarity::firstId)
                .thenComparing(PairSimilarity::secondId));
        return new CatalogReport(maximumAllowedSimilarity, comparisons);
    }

    public static PairSimilarity compare(
            StructuralSnapshot first,
            StructuralSnapshot second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        RenderedSilhouette firstRendered = render(first.structuralCells(), Orientation.IDENTITY);
        PairSimilarity best = null;
        for (Orientation orientation : Orientation.values()) {
            RenderedSilhouette secondRendered = render(second.structuralCells(), orientation);
            double top = jaccard(firstRendered.top(), secondRendered.top());
            double front = jaccard(firstRendered.front(), secondRendered.front());
            double side = jaccard(firstRendered.side(), secondRendered.side());
            double heightMap = heightMapSimilarity(
                    firstRendered.heightMap(), secondRendered.heightMap());
            double projection = (top + front + side) / 3.0;
            double combined = projection * 0.65 + heightMap * 0.35;
            PairSimilarity candidate = new PairSimilarity(
                    first.id(), second.id(), combined, top, front, side, heightMap);
            if (best == null || candidate.similarity() > best.similarity()) {
                best = candidate;
            }
        }
        return best;
    }

    private static RenderedSilhouette render(
            Set<Voxel> cells,
            Orientation orientation) {
        List<Voxel> oriented = cells.stream().map(orientation::apply).toList();
        VolumeBounds bounds = robustBounds(oriented);
        int minX = bounds.minimumX();
        int maxX = bounds.maximumX();
        int minY = bounds.minimumY();
        int maxY = bounds.maximumY();
        int minZ = bounds.minimumZ();
        int maxZ = bounds.maximumZ();
        int spanX = maxX - minX + 1;
        int spanY = maxY - minY + 1;
        int spanZ = maxZ - minZ + 1;
        int largestSpan = Math.max(spanX, Math.max(spanY, spanZ));
        double scale = (RASTER_SIZE - RASTER_PADDING * 2.0) / largestSpan;
        double offsetX = (RASTER_SIZE - spanX * scale) / 2.0;
        double offsetY = (RASTER_SIZE - spanY * scale) / 2.0;
        double offsetZ = (RASTER_SIZE - spanZ * scale) / 2.0;

        BitSet top = new BitSet(RASTER_SIZE * RASTER_SIZE);
        BitSet front = new BitSet(RASTER_SIZE * RASTER_SIZE);
        BitSet side = new BitSet(RASTER_SIZE * RASTER_SIZE);
        double[] heightMap = new double[RASTER_SIZE * RASTER_SIZE];
        java.util.Arrays.fill(heightMap, -1.0);
        for (Voxel cell : oriented) {
            PixelRange x = pixels(offsetX, cell.x() - minX, scale);
            PixelRange y = pixels(offsetY, cell.y() - minY, scale);
            PixelRange z = pixels(offsetZ, cell.z() - minZ, scale);
            fill(top, x, z);
            fill(front, x, y);
            fill(side, z, y);
            double normalizedTop = Math.max(
                    0.0,
                    Math.min(1.0, (cell.y() - minY + 1.0) / largestSpan));
            for (int px = x.minimum(); px <= x.maximum(); px++) {
                for (int pz = z.minimum(); pz <= z.maximum(); pz++) {
                    int index = pixelIndex(px, pz);
                    heightMap[index] = Math.max(heightMap[index], normalizedTop);
                }
            }
        }
        return new RenderedSilhouette(top, front, side, heightMap);
    }

    private static PixelRange pixels(double offset, int coordinate, double scale) {
        int minimum = Math.max(
                0,
                Math.min(RASTER_SIZE - 1, (int) Math.floor(offset + coordinate * scale)));
        int maximum = Math.max(
                0,
                Math.min(
                        RASTER_SIZE - 1,
                        (int) Math.ceil(offset + (coordinate + 1) * scale) - 1));
        return new PixelRange(minimum, Math.max(minimum, maximum));
    }

    private static VolumeBounds robustBounds(List<Voxel> cells) {
        int[] x = new int[cells.size()];
        int[] y = new int[cells.size()];
        int[] z = new int[cells.size()];
        for (int index = 0; index < cells.size(); index++) {
            Voxel cell = cells.get(index);
            x[index] = cell.x();
            y[index] = cell.y();
            z[index] = cell.z();
        }
        java.util.Arrays.sort(x);
        java.util.Arrays.sort(y);
        java.util.Arrays.sort(z);
        int trim = (int) Math.floor(cells.size() * BOUNDS_TRIM_FRACTION);
        trim = Math.min(trim, (cells.size() - 1) / 2);
        int upper = cells.size() - 1 - trim;
        return new VolumeBounds(
                x[trim], x[upper],
                y[trim], y[upper],
                z[trim], z[upper]);
    }

    private static void fill(BitSet projection, PixelRange first, PixelRange second) {
        for (int a = first.minimum(); a <= first.maximum(); a++) {
            for (int b = second.minimum(); b <= second.maximum(); b++) {
                projection.set(pixelIndex(a, b));
            }
        }
    }

    private static int pixelIndex(int first, int second) {
        return second * RASTER_SIZE + first;
    }

    private static double jaccard(BitSet first, BitSet second) {
        BitSet intersection = (BitSet) first.clone();
        intersection.and(second);
        BitSet union = (BitSet) first.clone();
        union.or(second);
        return union.isEmpty() ? 1.0 : intersection.cardinality() / (double) union.cardinality();
    }

    private static double heightMapSimilarity(double[] first, double[] second) {
        double similarity = 0.0;
        int compared = 0;
        for (int index = 0; index < first.length; index++) {
            if (first[index] < 0.0 && second[index] < 0.0) {
                continue;
            }
            compared++;
            if (first[index] >= 0.0 && second[index] >= 0.0) {
                similarity += 1.0 - Math.min(1.0, Math.abs(first[index] - second[index]));
            }
        }
        return compared == 0 ? 1.0 : similarity / compared;
    }

    private static void requireUnitInterval(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between zero and one");
        }
    }

    private enum Orientation {
        IDENTITY {
            @Override
            Voxel apply(Voxel cell) {
                return cell;
            }
        },
        MIRROR_X {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(-cell.x(), cell.y(), cell.z());
            }
        },
        MIRROR_Z {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(cell.x(), cell.y(), -cell.z());
            }
        },
        ROTATE_180 {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(-cell.x(), cell.y(), -cell.z());
            }
        },
        SWAP_XZ {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(cell.z(), cell.y(), cell.x());
            }
        },
        ROTATE_90 {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(cell.z(), cell.y(), -cell.x());
            }
        },
        ROTATE_270 {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(-cell.z(), cell.y(), cell.x());
            }
        },
        SWAP_AND_MIRROR {
            @Override
            Voxel apply(Voxel cell) {
                return new Voxel(-cell.z(), cell.y(), -cell.x());
            }
        };

        abstract Voxel apply(Voxel cell);
    }

    private record PixelRange(int minimum, int maximum) {
    }

    private record VolumeBounds(
            int minimumX,
            int maximumX,
            int minimumY,
            int maximumY,
            int minimumZ,
            int maximumZ) {
    }

    private record RenderedSilhouette(
            BitSet top,
            BitSet front,
            BitSet side,
            double[] heightMap) {
    }
}
