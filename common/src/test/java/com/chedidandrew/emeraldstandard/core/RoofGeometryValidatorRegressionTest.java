package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.Code;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.GeometryCell;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.Kind;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.Occupancy;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.RoofSnapshot;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.ValidationReport;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the geometry-aware authored-roof admission gate. */
public final class RoofGeometryValidatorRegressionTest {
    private RoofGeometryValidatorRegressionTest() {
    }

    public static void main(String[] args) {
        runAll();
        System.out.println("PASS authored roof geometry validator regressions");
    }

    static void runAll() {
        testContinuousSteppedGablePasses();
        testFullCapCannotSitAboveBottomSlab();
        testSideSupportCannotHideAVisibleSlabGap();
        testTopSlabCannotFloatAboveBottomSlab();
        testAbruptTwoBlockRoofJumpIsRejected();
        testDiagonalOrnamentIsNotMistakenForSupport();
        testSideConnectedRooftopFeatureStillRequiresDirectBearing();
        testHangingFeatureRequiresCeilingContact();
        testVerticalHangingFeatureChainPasses();
        testHangingFeatureChainRequiresSupportedLoadBearingTermination();
        testHangingFeatureChainRejectsFloatingLoadBearingTermination();
        testHangingFeatureChainCannotBridgeAPartialContactGap();
        testTopSlabMaySupportACapWhenItIsSideAnchored();
        testLaterStageCannotRetroactivelySupportEarlierRoof();
        testDecorationCannotBecomeARoofSupport();
    }

    private static void testContinuousSteppedGablePasses() {
        List<GeometryCell> cells = new ArrayList<>();
        addColumn(cells, 0, 0, 2);
        addColumn(cells, 4, 0, 2);
        cells.add(full(0, 3, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 4, 0, Kind.ROOF_COURSE));
        cells.add(lower(2, 5, 0, Kind.ROOF_COURSE));
        cells.add(full(3, 4, 0, Kind.ROOF_COURSE));
        cells.add(full(4, 3, 0, Kind.ROOF_COURSE));

        ValidationReport report = validate("continuous_gable", cells);
        require(report.valid(), "A continuous one-up/one-across gable failed: " + report.issues());
    }

    private static void testFullCapCannotSitAboveBottomSlab() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 3);
        cells.add(lower(0, 4, 0, Kind.ROOF_COURSE));
        cells.add(full(0, 5, 0, Kind.ROOFTOP_FEATURE));

        ValidationReport report = validate("floating_full_cap", cells);
        require(report.has(Code.FLOATING_ABOVE_PARTIAL_SUPPORT),
                "A full cap above a bottom slab did not expose its half-block air gap");
        require(report.has(Code.DISCONNECTED_ROOFTOP_FEATURE),
                "The floating full cap was still treated as foundation-supported");
    }

    private static void testTopSlabCannotFloatAboveBottomSlab() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 3);
        cells.add(lower(0, 4, 0, Kind.ROOF_COURSE));
        cells.add(top(0, 5, 0, Kind.ROOF_COURSE));

        ValidationReport report = validate("floating_slab_cap", cells);
        require(report.has(Code.FLOATING_ABOVE_PARTIAL_SUPPORT),
                "A top roof slab above a bottom slab escaped the physical gap check");
        require(report.has(Code.UNSUPPORTED_ROOF_TIER),
                "A visually separated slab tier remained connected through grid adjacency");
    }

    private static void testSideSupportCannotHideAVisibleSlabGap() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 5);
        cells.add(GeometryCell.anchor(voxel(1, 0, 0)));
        for (int y = 1; y <= 3; y++) {
            cells.add(full(1, y, 0, Kind.STRUCTURE));
        }
        cells.add(lower(1, 4, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 5, 0, Kind.ROOF_COURSE));

        ValidationReport report = validate("side_supported_visible_gap", cells);
        require(report.has(Code.FLOATING_ABOVE_PARTIAL_SUPPORT),
                "Horizontal support hid a visible gap above a bottom roof slab");
        require(!report.has(Code.UNSUPPORTED_ROOF_TIER),
                "The test setup should isolate the local gap from component support failures");
    }

    private static void testAbruptTwoBlockRoofJumpIsRejected() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 2);
        cells.add(full(0, 3, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 5, 0, Kind.ROOF_COURSE));

        ValidationReport report = validate("abrupt_tier_jump", cells);
        require(report.has(Code.UNSUPPORTED_ROOF_TIER),
                "A two-block one-across roof jump was accepted as a continuous slope");
    }

    private static void testDiagonalOrnamentIsNotMistakenForSupport() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 2);
        cells.add(full(0, 3, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 4, 0, Kind.ROOFTOP_FEATURE));

        ValidationReport report = validate("corner_touching_ornament", cells);
        require(report.has(Code.DISCONNECTED_ROOFTOP_FEATURE),
                "A rooftop ornament load-bore through a diagonal corner touch");
    }

    private static void testSideConnectedRooftopFeatureStillRequiresDirectBearing() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 3);
        cells.add(full(0, 4, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 4, 0, Kind.ROOFTOP_FEATURE));

        ValidationReport report = validate("side_connected_rooftop_feature", cells);
        require(report.has(Code.DISCONNECTED_ROOFTOP_FEATURE),
                "A rooftop feature load-bore sideways without direct support below");
    }

    private static void testHangingFeatureRequiresCeilingContact() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 3);
        cells.add(full(0, 4, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 3, 0, Kind.HANGING_FEATURE));

        ValidationReport report = validate("orphaned_hanging_feature", cells);
        require(report.has(Code.DISCONNECTED_HANGING_FEATURE),
                "A hanging detail without a block directly above passed admission");
    }

    private static void testVerticalHangingFeatureChainPasses() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 4);
        cells.add(full(1, 4, 0, Kind.STRUCTURE));
        cells.add(full(1, 3, 0, Kind.HANGING_FEATURE));
        cells.add(full(1, 2, 0, Kind.HANGING_FEATURE));

        ValidationReport report = validate("supported_hanging_chain", cells);
        require(report.valid(),
                "A vertical hanging chain attached to a supported ceiling failed: "
                        + report.issues());
    }

    private static void testHangingFeatureChainRequiresSupportedLoadBearingTermination() {
        List<GeometryCell> cells = new ArrayList<>();
        cells.add(GeometryCell.anchor(voxel(0, 0, 0)));
        cells.add(full(1, 4, 0, Kind.NON_LOAD_BEARING));
        cells.add(full(1, 3, 0, Kind.HANGING_FEATURE));
        cells.add(full(1, 2, 0, Kind.HANGING_FEATURE));

        ValidationReport report = validate("decoratively_terminated_hanging_chain", cells);
        require(report.count(Code.DISCONNECTED_HANGING_FEATURE) == 2,
                "Every link in a chain ending at decoration must remain disconnected: "
                        + report.issues());
    }

    private static void testHangingFeatureChainRejectsFloatingLoadBearingTermination() {
        List<GeometryCell> cells = new ArrayList<>();
        cells.add(GeometryCell.anchor(voxel(0, 0, 0)));
        cells.add(full(1, 4, 0, Kind.STRUCTURE));
        cells.add(full(1, 3, 0, Kind.HANGING_FEATURE));
        cells.add(full(1, 2, 0, Kind.HANGING_FEATURE));

        ValidationReport report = validate("floating_ceiling_hanging_chain", cells);
        require(report.count(Code.DISCONNECTED_HANGING_FEATURE) == 2,
                "A load-bearing but foundation-disconnected ceiling supported a hanging chain: "
                        + report.issues());
    }

    private static void testHangingFeatureChainCannotBridgeAPartialContactGap() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 4);
        cells.add(full(1, 4, 0, Kind.STRUCTURE));
        cells.add(top(1, 3, 0, Kind.HANGING_FEATURE));
        cells.add(full(1, 2, 0, Kind.HANGING_FEATURE));

        ValidationReport report = validate("gapped_hanging_chain", cells);
        require(report.count(Code.DISCONNECTED_HANGING_FEATURE) == 1
                        && report.issues().stream().anyMatch(issue -> issue.position().equals(
                                voxel(1, 2, 0))),
                "A hanging chain bridged a visible partial-occupancy gap: " + report.issues());
    }

    private static void testTopSlabMaySupportACapWhenItIsSideAnchored() {
        List<GeometryCell> cells = anchoredColumnThrough(0, 0, 3);
        cells.add(full(0, 4, 0, Kind.ROOF_COURSE));
        // This upper-half ridge slab is cantilevered from the full course beside it. Its upper
        // face reaches the block boundary, so a full cap in the next cell genuinely touches it.
        cells.add(top(1, 4, 0, Kind.ROOF_COURSE));
        cells.add(full(1, 5, 0, Kind.ROOFTOP_FEATURE));

        ValidationReport report = validate("supported_top_slab_cap", cells);
        require(report.valid(), "A physically connected slab cap failed: " + report.issues());
    }

    private static void testLaterStageCannotRetroactivelySupportEarlierRoof() {
        List<GeometryCell> base = new ArrayList<>();
        base.add(GeometryCell.anchor(voxel(0, 0, 0)));
        base.add(full(2, 2, 0, Kind.ROOF_COURSE));

        ValidationReport baseReport = validate("stage_prefix", base);
        require(baseReport.has(Code.UNSUPPORTED_ROOF_TIER),
                "A roof that floats during its authored stage escaped prefix validation");

        List<GeometryCell> complete = new ArrayList<>(base);
        complete.add(GeometryCell.anchor(voxel(2, 0, 0)));
        complete.add(full(2, 1, 0, Kind.STRUCTURE));
        require(validate("stage_complete", complete).valid(),
                "The regression fixture's later support should make only the combined graph valid");
    }

    private static void testDecorationCannotBecomeARoofSupport() {
        List<GeometryCell> decorated = new ArrayList<>();
        decorated.add(GeometryCell.anchor(voxel(2, 0, 0)));
        decorated.add(full(0, 0, 0, Kind.NON_LOAD_BEARING));
        decorated.add(full(0, 1, 0, Kind.ROOF_COURSE));
        ValidationReport decoratedReport = validate("decorative_fake_support", decorated);
        require(decoratedReport.has(Code.UNSUPPORTED_ROOF_TIER),
                "A non-load-bearing decoration was accepted as a roof support");
        require(!decoratedReport.has(Code.INVALID_GEOMETRY),
                "A ground-level decoration was incorrectly marked as a foundation anchor");

        List<GeometryCell> structural = new ArrayList<>();
        structural.add(GeometryCell.anchor(voxel(0, 0, 0)));
        structural.add(full(0, 1, 0, Kind.STRUCTURE));
        structural.add(full(0, 2, 0, Kind.ROOF_COURSE));
        require(validate("structural_support", structural).valid(),
                "A solid structural post no longer supports the roof above it");
    }

    private static ValidationReport validate(String id, List<GeometryCell> cells) {
        return RoofGeometryValidator.validate(new RoofSnapshot(id, cells));
    }

    private static List<GeometryCell> anchoredColumnThrough(int x, int z, int topY) {
        List<GeometryCell> cells = new ArrayList<>();
        addColumn(cells, x, z, topY);
        return cells;
    }

    private static void addColumn(List<GeometryCell> cells, int x, int z, int topY) {
        cells.add(GeometryCell.anchor(voxel(x, 0, z)));
        for (int y = 1; y <= topY; y++) {
            cells.add(full(x, y, z, Kind.STRUCTURE));
        }
    }

    private static GeometryCell full(int x, int y, int z, Kind kind) {
        return new GeometryCell(voxel(x, y, z), kind, Occupancy.FULL, false);
    }

    private static GeometryCell lower(int x, int y, int z, Kind kind) {
        return new GeometryCell(voxel(x, y, z), kind, Occupancy.LOWER_HALF, false);
    }

    private static GeometryCell top(int x, int y, int z, Kind kind) {
        return new GeometryCell(voxel(x, y, z), kind, Occupancy.UPPER_HALF, false);
    }

    private static Voxel voxel(int x, int y, int z) {
        return new Voxel(x, y, z);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
