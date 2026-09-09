package com.chedidandrew.emeraldstandard.core;

import java.util.List;

/** Loader-neutral perspective math shared by the deterministic structure-review camera. */
public final class GalleryCameraProjection {
    public static final double REVIEW_VERTICAL_FOV_DEGREES = 70.0;
    public static final double REVIEW_ASPECT_RATIO = 16.0 / 9.0;
    private static final double HORIZONTAL_MARGIN_DEGREES = 4.0;
    private static final double VERTICAL_MARGIN_DEGREES = 3.5;

    private GalleryCameraProjection() {
    }

    /** Projects all eight occupied-bounds corners into the fixed review viewport. */
    public static Projection project(
            Bounds bounds,
            double eyeX,
            double eyeY,
            double eyeZ,
            double targetX,
            double targetY,
            double targetZ) {
        return project(
                bounds,
                eyeX,
                eyeY,
                eyeZ,
                targetX,
                targetY,
                targetZ,
                REVIEW_VERTICAL_FOV_DEGREES);
    }

    /** Projects occupied bounds into a deterministic viewport using an explicit vertical FOV. */
    public static Projection project(
            Bounds bounds,
            double eyeX,
            double eyeY,
            double eyeZ,
            double targetX,
            double targetY,
            double targetZ,
            double verticalFovDegrees) {
        validateVerticalFov(verticalFovDegrees);
        CameraProjection camera = CameraProjection.create(
                eyeX, eyeY, eyeZ, targetX, targetY, targetZ);
        if (camera == null) {
            return Projection.invalid();
        }
        for (double x : new double[] {bounds.minimumX(), bounds.maximumX()}) {
            for (double y : new double[] {bounds.minimumY(), bounds.maximumY()}) {
                for (double z : new double[] {bounds.minimumZ(), bounds.maximumZ()}) {
                    if (!camera.include(x, y, z)) {
                        return Projection.invalid();
                    }
                }
            }
        }
        return camera.finish(verticalFovDegrees);
    }

    /**
     * Projects the real union of occupied unit blocks rather than the corners of their enclosing
     * AABB. Sparse porches and facade accents therefore cannot invent a near-and-wide corner that
     * does not exist and falsely claim that a distant building fills the review frame.
     */
    public static Projection projectOccupiedBlocks(
            List<Point> blockOrigins,
            double eyeX,
            double eyeY,
            double eyeZ,
            double targetX,
            double targetY,
            double targetZ) {
        return projectOccupiedBlocks(
                blockOrigins,
                eyeX,
                eyeY,
                eyeZ,
                targetX,
                targetY,
                targetZ,
                REVIEW_VERTICAL_FOV_DEGREES);
    }

    /** Projects real occupied blocks using an explicit deterministic vertical FOV. */
    public static Projection projectOccupiedBlocks(
            List<Point> blockOrigins,
            double eyeX,
            double eyeY,
            double eyeZ,
            double targetX,
            double targetY,
            double targetZ,
            double verticalFovDegrees) {
        validateVerticalFov(verticalFovDegrees);
        if (blockOrigins.isEmpty()) {
            return Projection.invalid();
        }
        CameraProjection camera = CameraProjection.create(
                eyeX, eyeY, eyeZ, targetX, targetY, targetZ);
        if (camera == null) {
            return Projection.invalid();
        }
        for (Point origin : blockOrigins) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        if (!camera.include(
                                origin.x() + dx,
                                origin.y() + dy,
                                origin.z() + dz)) {
                            return Projection.invalid();
                        }
                    }
                }
            }
        }
        return camera.finish(verticalFovDegrees);
    }

    /**
     * Returns a unit world-space ray through a normalized point in the fixed review viewport.
     * Horizontal and vertical coordinates use {@code -1..1}, with zero at the image centre.
     * Minecraft's configured FOV is vertical, so the horizontal span is derived from the 16:9
     * capture aspect rather than treating 70 degrees as the horizontal field of view.
     */
    public static Point viewportRayDirection(
            double eyeX,
            double eyeY,
            double eyeZ,
            double targetX,
            double targetY,
            double targetZ,
            double normalizedHorizontal,
            double normalizedVertical) {
        return viewportRayDirection(
                eyeX,
                eyeY,
                eyeZ,
                targetX,
                targetY,
                targetZ,
                normalizedHorizontal,
                normalizedVertical,
                REVIEW_VERTICAL_FOV_DEGREES);
    }

    /** Returns a normalized viewport ray using an explicit deterministic vertical FOV. */
    public static Point viewportRayDirection(
            double eyeX,
            double eyeY,
            double eyeZ,
            double targetX,
            double targetY,
            double targetZ,
            double normalizedHorizontal,
            double normalizedVertical,
            double verticalFovDegrees) {
        validateVerticalFov(verticalFovDegrees);
        if (!Double.isFinite(normalizedHorizontal)
                || !Double.isFinite(normalizedVertical)
                || normalizedHorizontal < -1.0
                || normalizedHorizontal > 1.0
                || normalizedVertical < -1.0
                || normalizedVertical > 1.0) {
            throw new IllegalArgumentException(
                    "Viewport coordinates must be finite and within -1..1");
        }
        CameraProjection camera = CameraProjection.create(
                eyeX, eyeY, eyeZ, targetX, targetY, targetZ);
        if (camera == null) {
            throw new IllegalArgumentException(
                    "Viewport ray requires a non-degenerate camera axis");
        }
        double verticalTangent = Math.tan(
                Math.toRadians(verticalFovDegrees) / 2.0);
        double horizontalTangent = verticalTangent * REVIEW_ASPECT_RATIO;
        double x = camera.forwardX
                + camera.rightX * normalizedHorizontal * horizontalTangent
                + camera.upX * normalizedVertical * verticalTangent;
        double y = camera.forwardY
                + camera.upY * normalizedVertical * verticalTangent;
        double z = camera.forwardZ
                + camera.rightZ * normalizedHorizontal * horizontalTangent
                + camera.upZ * normalizedVertical * verticalTangent;
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Point(x / length, y / length, z / length);
    }

    private static void validateVerticalFov(double verticalFovDegrees) {
        if (!Double.isFinite(verticalFovDegrees)
                || verticalFovDegrees <= 8.0
                || verticalFovDegrees >= 170.0) {
            throw new IllegalArgumentException(
                    "Vertical FOV must be finite and between 8 and 170 degrees");
        }
    }

    private static final class CameraProjection {
        private final double eyeX;
        private final double eyeY;
        private final double eyeZ;
        private final double forwardX;
        private final double forwardY;
        private final double forwardZ;
        private final double rightX;
        private final double rightZ;
        private final double upX;
        private final double upY;
        private final double upZ;
        private double minimumHorizontal = Double.POSITIVE_INFINITY;
        private double maximumHorizontal = Double.NEGATIVE_INFINITY;
        private double minimumVertical = Double.POSITIVE_INFINITY;
        private double maximumVertical = Double.NEGATIVE_INFINITY;

        private CameraProjection(
                double eyeX,
                double eyeY,
                double eyeZ,
                double forwardX,
                double forwardY,
                double forwardZ,
                double rightX,
                double rightZ,
                double upX,
                double upY,
                double upZ) {
            this.eyeX = eyeX;
            this.eyeY = eyeY;
            this.eyeZ = eyeZ;
            this.forwardX = forwardX;
            this.forwardY = forwardY;
            this.forwardZ = forwardZ;
            this.rightX = rightX;
            this.rightZ = rightZ;
            this.upX = upX;
            this.upY = upY;
            this.upZ = upZ;
        }

        private static CameraProjection create(
                double eyeX,
                double eyeY,
                double eyeZ,
                double targetX,
                double targetY,
                double targetZ) {
            double viewX = targetX - eyeX;
            double viewY = targetY - eyeY;
            double viewZ = targetZ - eyeZ;
            double forwardLength = Math.sqrt(
                    viewX * viewX + viewY * viewY + viewZ * viewZ);
            double horizontalLength = Math.sqrt(viewX * viewX + viewZ * viewZ);
            if (forwardLength < 0.001 || horizontalLength < 0.001) {
                return null;
            }
            double forwardX = viewX / forwardLength;
            double forwardY = viewY / forwardLength;
            double forwardZ = viewZ / forwardLength;
            double rightX = -viewZ / horizontalLength;
            double rightZ = viewX / horizontalLength;
            double upX = -rightZ * forwardY;
            double upY = rightZ * forwardX - rightX * forwardZ;
            double upZ = rightX * forwardY;
            return new CameraProjection(
                    eyeX,
                    eyeY,
                    eyeZ,
                    forwardX,
                    forwardY,
                    forwardZ,
                    rightX,
                    rightZ,
                    upX,
                    upY,
                    upZ);
        }

        private boolean include(double x, double y, double z) {
            double offsetX = x - eyeX;
            double offsetY = y - eyeY;
            double offsetZ = z - eyeZ;
            double depth = offsetX * forwardX
                    + offsetY * forwardY
                    + offsetZ * forwardZ;
            if (depth <= 0.5) {
                return false;
            }
            double horizontal = Math.atan2(
                    offsetX * rightX + offsetZ * rightZ, depth);
            double vertical = Math.atan2(
                    offsetX * upX + offsetY * upY + offsetZ * upZ, depth);
            minimumHorizontal = Math.min(minimumHorizontal, horizontal);
            maximumHorizontal = Math.max(maximumHorizontal, horizontal);
            minimumVertical = Math.min(minimumVertical, vertical);
            maximumVertical = Math.max(maximumVertical, vertical);
            return true;
        }

        private Projection finish(double verticalFovDegrees) {
            double verticalFov = Math.toRadians(verticalFovDegrees);
            double horizontalFov = 2.0 * Math.atan(
                    Math.tan(verticalFov / 2.0) * REVIEW_ASPECT_RATIO);
            double safeHorizontalHalf = horizontalFov / 2.0
                    - Math.toRadians(HORIZONTAL_MARGIN_DEGREES);
            double safeVerticalHalf = verticalFov / 2.0
                    - Math.toRadians(VERTICAL_MARGIN_DEGREES);
            boolean fits = minimumHorizontal >= -safeHorizontalHalf
                    && maximumHorizontal <= safeHorizontalHalf
                    && minimumVertical >= -safeVerticalHalf
                    && maximumVertical <= safeVerticalHalf;
            double horizontalCoverage = (maximumHorizontal - minimumHorizontal) / horizontalFov;
            double verticalCoverage = (maximumVertical - minimumVertical) / verticalFov;
            double horizontalCenterOffset =
                    (maximumHorizontal + minimumHorizontal) / (2.0 * horizontalFov);
            double verticalCenterOffset =
                    (maximumVertical + minimumVertical) / (2.0 * verticalFov);
            return new Projection(
                    fits,
                    horizontalCoverage,
                    verticalCoverage,
                    horizontalCenterOffset,
                    verticalCenterOffset);
        }
    }

    /** Minimum corner of one occupied unit block. */
    public record Point(double x, double y, double z) {
    }

    public record Bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {
        public Bounds {
            if (maximumX <= minimumX || maximumY <= minimumY || maximumZ <= minimumZ) {
                throw new IllegalArgumentException("Projected bounds must have positive volume");
            }
        }
    }

    public record Projection(
            boolean fits,
            double horizontalCoverage,
            double verticalCoverage,
            double horizontalCenterOffset,
            double verticalCenterOffset) {
        private static Projection invalid() {
            return new Projection(false, -1.0, -1.0, Double.NaN, Double.NaN);
        }

        public double maximumCoverage() {
            return Math.max(horizontalCoverage, verticalCoverage);
        }
    }
}
