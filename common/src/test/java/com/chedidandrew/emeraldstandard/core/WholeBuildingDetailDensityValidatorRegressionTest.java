package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailCell;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailPhase;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailProfile;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the scale-normalized authored-building detail gate. */
public final class WholeBuildingDetailDensityValidatorRegressionTest {
    private WholeBuildingDetailDensityValidatorRegressionTest() {
    }

    public static void main(String[] args) {
        testDetailedEnclosedMasterPasses();
        testDetailedFlatParapetMasterPasses();
        testDetailedOpenAirMasterPassesWithoutOpenings();
        testPlainBoxFailsDespiteValidShellVolume();
        testTokenDetailClusterFailsHorizontalCoverage();
        testDefaultVillageGradeFlushDetailFailsArchitecturalDepth();
        testSingleChimneyDoesNotCountAsDistributedRoofRelief();
        testDecorCannotSubstituteForRoleReadableFixtures();
        testDistinctivenessCeilingNeverWeakens();
        testCatalogOrderingIdentityAndFailureMessage();
        testSnapshotGuards();
        System.out.println("PASS whole-building detail-density validator regressions");
    }

    private static void testDetailedEnclosedMasterPasses() {
        DetailSnapshot snapshot = detailedSnapshot("detailed_house", true, 11, 9);
        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(snapshot);
        require(profile.detailed(), "A distributed detailed house failed: " + profile.failures());
        require(profile.requiredDetailCells() == 12,
                "Small-master detail threshold no longer respects its absolute floor");
        require(profile.requiredDetailColumns() == 6,
                "Small-master horizontal-detail threshold no longer respects its absolute floor");
        require(profile.detailRegions() >= 3
                        && profile.detailXAxisBands() >= 2
                        && profile.detailZAxisBands() >= 2,
                "Detailed house no longer proves distributed visual interest");
        require(profile.facadeColumns() >= 4
                        && profile.facadeSides() >= 2
                        && profile.facadeRegions() >= 3,
                "Detailed house no longer proves distributed facade articulation");
        require(profile.projectedArchitecturalColumns() >= 2,
                "Detailed house no longer proves layered architectural depth");
        require(profile.roofHeightRange() >= 1
                        && profile.roofArticulationColumns() >= 2
                        && profile.roofArticulationRegions() >= 2,
                "Detailed house no longer proves distributed roofline relief");
        require(profile.fixtureCells() >= 2 && profile.fixtureColumns() >= 2,
                "Detailed house no longer proves role-readable functional furnishing");
    }

    private static void testDetailedFlatParapetMasterPasses() {
        DetailSnapshot source = detailedSnapshot("flat_parapet_hall", true, 13, 11);
        List<DetailCell> cells = new ArrayList<>(source.cells());
        cells.removeIf(cell -> cell.phase() == DetailPhase.ROOF
                && cell.position().y() > 5);
        for (int z = 0; z < 11; z++) {
            cells.add(cell(0, 6, z, DetailPhase.FRAME));
        }
        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("flat_parapet_hall", 13, 11, 10, true, cells));
        require(profile.detailed(),
                "A distributed parapet/industrial roof was forced into a gable: "
                        + profile.failures());
        require(profile.roofArticulationRegions() >= 2,
                "Flat-parapet fixture does not actually exercise distributed roof relief");
    }

    private static void testDetailedOpenAirMasterPassesWithoutOpenings() {
        List<DetailCell> cells = baseShell(17, 17);
        for (int index = 0; index < 20; index++) {
            replace(cells, cell(index % 10, 1 + index / 10, 1 + (index * 3) % 15,
                    DetailPhase.FRAME));
        }
        for (int index = 0; index < 12; index++) {
            cells.add(cell(11 + index % 3, 1 + index / 8, 1 + (index * 2) % 15,
                    index < 6 ? DetailPhase.FIXTURE : DetailPhase.DECOR));
        }
        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("open_market", 17, 17, 10, false, cells));
        require(profile.openingCells() == 0,
                "Synthetic open-air plan unexpectedly contains an opening");
        require(profile.detailed(),
                "A detailed open-air plan was forced to manufacture windows: "
                        + profile.failures());
        require(profile.roofHeightRange() == 0,
                "Open-air fixture no longer proves the flat-canopy exemption");
    }

    private static void testPlainBoxFailsDespiteValidShellVolume() {
        List<DetailCell> cells = baseShell(11, 9);
        replace(cells, cell(0, 1, 0, DetailPhase.FRAME));
        replace(cells, cell(10, 1, 0, DetailPhase.FRAME));
        replace(cells, cell(0, 1, 8, DetailPhase.FRAME));
        replace(cells, cell(10, 1, 8, DetailPhase.FRAME));
        replace(cells, cell(5, 1, 0, DetailPhase.OPENING));
        replace(cells, cell(5, 2, 0, DetailPhase.OPENING));

        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("plain_box", 11, 9, 8, true, cells));
        require(!profile.detailed(), "A plain rectangular shell passed the visual-quality gate");
        require(profile.failures().stream().anyMatch(failure -> failure.contains("detail cells"))
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("fixtures and decor")),
                "Plain-box report did not explain its missing articulation and lived detail");
    }

    private static void testTokenDetailClusterFailsHorizontalCoverage() {
        List<DetailCell> cells = baseShell(20, 20);
        DetailPhase[] phases = {
                DetailPhase.FRAME,
                DetailPhase.OPENING,
                DetailPhase.FIXTURE,
                DetailPhase.DECOR
        };
        for (int y = 6; y <= 15; y++) {
            for (int x = 1; x <= 4; x++) {
                cells.add(cell(x, y, 1, phases[(x + y) % phases.length]));
            }
        }
        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("token_corner", 20, 20, 16, true, cells));
        require(profile.detailCells() >= profile.requiredDetailCells(),
                "Synthetic cluster no longer isolates horizontal coverage from raw count");
        require(!profile.detailed()
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("horizontal columns")),
                "A dense token detail cluster in one corner passed the distribution gate");
    }

    private static void testDefaultVillageGradeFlushDetailFailsArchitecturalDepth() {
        List<DetailCell> cells = baseShell(13, 11);
        for (int x : new int[] {0, 3, 6, 9, 12}) {
            replace(cells, cell(x, 1, 0, DetailPhase.FRAME));
            replace(cells, cell(x, 2, 10, DetailPhase.FRAME));
        }
        for (int z : new int[] {2, 8}) {
            replace(cells, cell(0, 2, z, DetailPhase.OPENING));
            replace(cells, cell(12, 2, z, DetailPhase.OPENING));
        }
        cells.add(cell(3, 1, 3, DetailPhase.FIXTURE));
        cells.add(cell(9, 1, 7, DetailPhase.FIXTURE));
        cells.add(cell(6, 2, 5, DetailPhase.DECOR));
        // Detached yard lamps/frames may enrich a scene but cannot manufacture facade depth.
        for (int y = 1; y <= 3; y++) {
            cells.add(cell(-4, y, 1, DetailPhase.FRAME));
            cells.add(cell(16, y, 9, DetailPhase.FRAME));
        }
        addRidge(cells, 13, 11);

        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("flush_default_house", 13, 11, 10, true, cells));
        require(profile.detailCells() >= profile.requiredDetailCells()
                        && profile.roofArticulationRegions() >= 2
                        && profile.fixtureColumns() >= 2,
                "Flush-shell fixture no longer isolates architectural depth");
        require(profile.projectedArchitecturalColumns() == 0
                        && !profile.detailed()
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("architectural frame/opening depth")),
                "Detached yard props made a furnished but flush default-village shell pass depth");
    }

    private static void testSingleChimneyDoesNotCountAsDistributedRoofRelief() {
        DetailSnapshot source = detailedSnapshot("single_chimney_box", true, 13, 11);
        List<DetailCell> cells = new ArrayList<>(source.cells());
        cells.removeIf(cell -> cell.phase() == DetailPhase.ROOF
                && cell.position().y() > 5);
        cells.add(cell(6, 6, 5, DetailPhase.DECOR));
        cells.add(cell(6, 7, 5, DetailPhase.DECOR));

        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("single_chimney_box", 13, 11, 10, true, cells));
        require(profile.roofHeightRange() >= 1
                        && profile.roofArticulationColumns() == 1
                        && profile.roofArticulationRegions() == 1,
                "Single-chimney fixture no longer isolates roof-detail distribution");
        require(!profile.detailed()
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("roofline relief")),
                "One token chimney made an otherwise flat roof count as richly articulated");
    }

    private static void testDecorCannotSubstituteForRoleReadableFixtures() {
        DetailSnapshot source = detailedSnapshot("decorated_empty_market", false, 13, 11);
        List<DetailCell> cells = source.cells().stream()
                .map(cell -> cell.phase() == DetailPhase.FIXTURE
                        ? new DetailCell(cell.position(), DetailPhase.DECOR)
                        : cell)
                .toList();
        DetailProfile profile = WholeBuildingDetailDensityValidator.profile(
                new DetailSnapshot("decorated_empty_market", 13, 11, 10, false, cells));
        require(profile.decorCells() >= 3 && profile.fixtureCells() == 0,
                "Decor-only fixture no longer isolates functional furnishing");
        require(!profile.detailed()
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("role is visually readable")),
                "Decorative clutter substituted for role-readable functional fixtures");
    }

    private static void testDistinctivenessCeilingNeverWeakens() {
        require(WholeBuildingDistinctivenessValidator.DEFAULT_MAX_SIMILARITY <= 0.900,
                "Catalog shape-distinctiveness ceiling weakened above 0.900");
    }

    private static void testCatalogOrderingIdentityAndFailureMessage() {
        DetailSnapshot detailed = detailedSnapshot("z_detailed", true, 11, 9);
        List<DetailCell> plainCells = baseShell(9, 9);
        DetailSnapshot plain = new DetailSnapshot("a_plain", 9, 9, 8, true, plainCells);
        WholeBuildingDetailDensityValidator.CatalogReport report =
                WholeBuildingDetailDensityValidator.validateSnapshots(List.of(detailed, plain));
        require(report.profiles().getFirst().id().equals("a_plain")
                        && report.profiles().getLast().id().equals("z_detailed"),
                "Detail catalog report is not stable-id ordered");
        require(report.insufficientProfiles().size() == 1
                        && report.insufficientProfiles().getFirst().id().equals("a_plain"),
                "Detail catalog report did not isolate the under-detailed master");
        try {
            report.requireDetailed();
            throw new AssertionError("Under-detailed catalog did not fail admission");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("a_plain")
                            && expected.getMessage().contains("detail cells/columns"),
                    "Catalog failure omitted actionable profile measurements");
        }

        expectIllegal(() -> WholeBuildingDetailDensityValidator.validateSnapshots(
                List.of(detailed, detailed)), "Duplicate detail snapshot id");
    }

    private static void testSnapshotGuards() {
        expectIllegal(() -> new DetailSnapshot(" ", 1, 1, 1, false,
                List.of(cell(0, 0, 0, DetailPhase.FRAME))), "must not be blank");
        expectIllegal(() -> new DetailSnapshot("bad_size", 0, 1, 1, false,
                List.of(cell(0, 0, 0, DetailPhase.FRAME))), "must be positive");
        DetailCell duplicate = cell(0, 0, 0, DetailPhase.FRAME);
        expectIllegal(() -> new DetailSnapshot("duplicate", 1, 1, 1, false,
                List.of(duplicate, duplicate)), "Duplicate detail snapshot cell");
    }

    private static DetailSnapshot detailedSnapshot(
            String id, boolean enclosed, int width, int depth) {
        List<DetailCell> cells = baseShell(width, depth);
        for (int x = 0; x < width; x += 2) {
            replace(cells, cell(x, 1, 1, DetailPhase.FRAME));
            replace(cells, cell(x, 2, depth - 2, DetailPhase.FRAME));
        }
        replace(cells, cell(1, 1, 0, DetailPhase.OPENING));
        replace(cells, cell(1, 2, 0, DetailPhase.OPENING));
        replace(cells, cell(width - 2, 1, depth - 1, DetailPhase.OPENING));
        replace(cells, cell(width - 2, 2, depth - 1, DetailPhase.OPENING));
        cells.add(cell(2, 1, 3, DetailPhase.FIXTURE));
        cells.add(cell(width - 3, 1, 3, DetailPhase.FIXTURE));
        cells.add(cell(width / 2, 2, depth / 2, DetailPhase.DECOR));
        cells.add(cell(width / 2, 3, depth / 2, DetailPhase.DECOR));
        // Two exterior frame piers create a genuinely layered facade rather than flush recoloring.
        cells.add(cell(-1, 1, 2, DetailPhase.FRAME));
        cells.add(cell(width, 1, depth - 3, DetailPhase.FRAME));
        addRidge(cells, width, depth);
        return new DetailSnapshot(id, width, depth, 10, enclosed, cells);
    }

    private static void addRidge(List<DetailCell> cells, int width, int depth) {
        int centerX = width / 2;
        for (int z = 0; z < depth; z++) {
            cells.add(cell(centerX, 6, z, DetailPhase.ROOF));
        }
    }

    private static List<DetailCell> baseShell(int width, int depth) {
        List<DetailCell> cells = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                cells.add(cell(x, 0, z, DetailPhase.FOUNDATION));
                cells.add(cell(x, 5, z, DetailPhase.ROOF));
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 1; y < 5; y++) {
                cells.add(cell(x, y, 0, DetailPhase.SHELL));
                cells.add(cell(x, y, depth - 1, DetailPhase.SHELL));
            }
        }
        for (int z = 1; z < depth - 1; z++) {
            for (int y = 1; y < 5; y++) {
                cells.add(cell(0, y, z, DetailPhase.SHELL));
                cells.add(cell(width - 1, y, z, DetailPhase.SHELL));
            }
        }
        return cells;
    }

    private static DetailCell cell(int x, int y, int z, DetailPhase phase) {
        return new DetailCell(new Voxel(x, y, z), phase);
    }

    private static void replace(List<DetailCell> cells, DetailCell replacement) {
        cells.removeIf(cell -> cell.position().equals(replacement.position()));
        cells.add(replacement);
    }

    private static void expectIllegal(Runnable action, String fragment) {
        try {
            action.run();
            throw new AssertionError("Invalid detail input was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(fragment),
                    "Validation error omitted '" + fragment + "': " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
