package com.chedidandrew.emeraldstandard.core;

import java.util.List;

/** Numeric regression for full-frame gallery subject projection and FOV normalization. */
public final class GalleryCameraProjectionRegressionTest {
    private GalleryCameraProjectionRegressionTest() {
    }

    public static void main(String[] args) {
        GalleryCameraProjection.Bounds subject = new GalleryCameraProjection.Bounds(
                -5.0, 5.0, 0.0, 10.0, 0.0, 1.0);
        GalleryCameraProjection.Projection readable = GalleryCameraProjection.project(
                subject, 0.0, 5.0, -10.0, 0.0, 5.0, 0.5);
        require(readable.fits(), "Centered readable subject should fit the safe viewport");
        require(readable.verticalCoverage() > 0.70 && readable.verticalCoverage() < 0.80,
                "Vertical coverage is not normalized by the full 70-degree vertical FOV");
        require(readable.maximumCoverage() > 0.70,
                "Readable subject no longer occupies a dominant viewport dimension");
        requireClose(readable.horizontalCenterOffset(), 0.0, 1.0E-9,
                "Centered subject projection gained a horizontal composition offset");

        GalleryCameraProjection.Projection normalAtFallbackPose = GalleryCameraProjection.project(
                subject, 0.0, 5.0, -14.0, 0.0, 5.0, 0.5);
        GalleryCameraProjection.Projection zoomed = GalleryCameraProjection.project(
                subject, 0.0, 5.0, -14.0, 0.0, 5.0, 0.5, 55.0);
        require(zoomed.fits(), "Bounded 55-degree fallback cropped a centered subject");
        require(zoomed.maximumCoverage() > normalAtFallbackPose.maximumCoverage() + 0.12,
                "Narrower per-shot FOV no longer increases deterministic subject coverage");

        GalleryCameraProjection.Projection distant = GalleryCameraProjection.project(
                subject, 0.0, 5.0, -40.0, 0.0, 5.0, 0.5);
        require(distant.maximumCoverage() < 0.25,
                "Distant subject incorrectly passes as contact-sheet-readable");

        GalleryCameraProjection.Projection shiftedAim = GalleryCameraProjection.project(
                subject, 0.0, 5.0, -14.0, -3.0, 5.0, 0.5, 55.0);
        require(Math.abs(shiftedAim.horizontalCenterOffset()) > 0.02,
                "Projection no longer reports an optical aim composition shift");

        GalleryCameraProjection.Bounds cropped = new GalleryCameraProjection.Bounds(
                -15.0, 15.0, 0.0, 10.0, 0.0, 1.0);
        require(!GalleryCameraProjection.project(
                        cropped, 0.0, 5.0, -10.0, 0.0, 5.0, 0.5).fits(),
                "Subject outside the safe horizontal margins was not rejected");

        GalleryCameraProjection.Bounds sparseEnvelope = new GalleryCameraProjection.Bounds(
                -6.0, 6.0, 0.0, 10.0, 0.0, 11.0);
        GalleryCameraProjection.Projection enclosingBox = GalleryCameraProjection.project(
                sparseEnvelope, 0.0, 5.0, -14.0, 0.0, 5.0, 5.5);
        GalleryCameraProjection.Projection occupiedUnion =
                GalleryCameraProjection.projectOccupiedBlocks(
                        List.of(
                                new GalleryCameraProjection.Point(-6.0, 0.0, 10.0),
                                new GalleryCameraProjection.Point(5.0, 9.0, 0.0)),
                        0.0, 5.0, -14.0, 0.0, 5.0, 5.5);
        require(enclosingBox.maximumCoverage() - occupiedUnion.maximumCoverage() > 0.10,
                "Sparse occupied blocks still inherit phantom enclosing-box corners");

        GalleryCameraProjection.Point centreRay = GalleryCameraProjection.viewportRayDirection(
                0.0, 2.0, 0.0,
                0.0, 2.0, 10.0,
                0.0, 0.0);
        requireClose(centreRay.x(), 0.0, 1.0E-9,
                "Centre viewport ray drifted horizontally");
        requireClose(centreRay.y(), 0.0, 1.0E-9,
                "Centre viewport ray drifted vertically");
        requireClose(centreRay.z(), 1.0, 1.0E-9,
                "Centre viewport ray no longer follows the camera axis");

        GalleryCameraProjection.Point horizontalEdge =
                GalleryCameraProjection.viewportRayDirection(
                        0.0, 2.0, 0.0,
                        0.0, 2.0, 10.0,
                        1.0, 0.0);
        double expectedHorizontalHalfFov = Math.atan(
                Math.tan(Math.toRadians(
                        GalleryCameraProjection.REVIEW_VERTICAL_FOV_DEGREES) / 2.0)
                        * GalleryCameraProjection.REVIEW_ASPECT_RATIO);
        requireClose(
                Math.atan2(Math.abs(horizontalEdge.x()), horizontalEdge.z()),
                expectedHorizontalHalfFov,
                1.0E-9,
                "Viewport ray treated Minecraft's vertical FOV as horizontal");

        GalleryCameraProjection.Point verticalEdge =
                GalleryCameraProjection.viewportRayDirection(
                        0.0, 2.0, 0.0,
                        0.0, 2.0, 10.0,
                        0.0, 1.0);
        requireClose(
                Math.atan2(Math.abs(verticalEdge.y()), verticalEdge.z()),
                Math.toRadians(GalleryCameraProjection.REVIEW_VERTICAL_FOV_DEGREES) / 2.0,
                1.0E-9,
                "Viewport ray does not cover the configured vertical half-FOV");

        GalleryCameraProjection.Point zoomedHorizontalEdge =
                GalleryCameraProjection.viewportRayDirection(
                        0.0, 2.0, 0.0,
                        0.0, 2.0, 10.0,
                        1.0, 0.0, 55.0);
        require(Math.abs(zoomedHorizontalEdge.x()) < Math.abs(horizontalEdge.x()),
                "Per-shot viewport grid did not narrow with its planned FOV");
        System.out.println("PASS gallery camera projection regression");
    }

    private static void requireClose(
            double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(
                    message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
