package com.chedidandrew.emeraldstandard.core;

/** Regression for the deterministic, capture-only isolated review annex. */
public final class GalleryCaptureIsolationPlanRegressionTest {
    private GalleryCaptureIsolationPlanRegressionTest() {
    }

    public static void main(String[] args) {
        GalleryCaptureIsolationPlan.Placement first =
                GalleryCaptureIsolationPlan.placementFor(120, 800);
        GalleryCaptureIsolationPlan.Placement repeated =
                GalleryCaptureIsolationPlan.placementFor(120, 800);
        GalleryCaptureIsolationPlan.Placement another =
                GalleryCaptureIsolationPlan.placementFor(40, 320);

        require(first.equals(repeated), "Annex placement is not deterministic");
        require(first.annexOriginX() == another.annexOriginX()
                        && first.annexOriginZ() == another.annexOriginZ(),
                "Fallback no longer reuses one isolated review pad");
        require(first.translateX(119) == first.annexOriginX() - 1
                        && first.translateZ(838) == first.annexOriginZ() + 38,
                "Annex translation no longer preserves fixture-relative geometry");
        require(first.annexOriginX() - GalleryCaptureIsolationPlan.clearRadiusBlocks()
                        > StructureGalleryPlan.WIDTH_BLOCKS,
                "Annex clear envelope overlaps the gallery X extent");
        require(first.annexOriginZ() - GalleryCaptureIsolationPlan.clearRadiusBlocks()
                        > StructureGalleryPlan.DEPTH_BLOCKS,
                "Annex clear envelope overlaps the gallery Z extent");
        require(GalleryCaptureIsolationPlan.clearHeightBlocks() >= 48,
                "Annex clear height no longer protects exterior camera rays");
        require(GalleryCaptureIsolationPlan.baselineDepthBlocks() >= 4,
                "Annex does not restore enough flat-world foundation depth");
        require("isolated-clone".equals(
                        GalleryCaptureIsolationPlan.ISOLATED_CLONE_CONTEXT),
                "Manifest capture context identity changed");
        System.out.println("PASS gallery capture isolation plan regression");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
