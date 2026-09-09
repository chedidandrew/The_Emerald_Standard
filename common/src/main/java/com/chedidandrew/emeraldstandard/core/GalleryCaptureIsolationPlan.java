package com.chedidandrew.emeraldstandard.core;

/**
 * Loader-neutral placement for the capture-only isolated review annex.
 *
 * <p>The production gallery remains the preferred evidence source. When its fixed row spacing
 * makes every otherwise valid camera include another fixture, the capture harness may translate
 * the exact active fixture to this single distant flat-world pad and rerun every normal camera
 * safety check. The deterministic translation keeps the fallback reproducible without changing
 * production generation or the 267-shot itinerary.</p>
 */
public final class GalleryCaptureIsolationPlan {
    public static final String GALLERY_CONTEXT = "gallery";
    public static final String ISOLATED_CLONE_CONTEXT = "isolated-clone";

    private static final int ANNEX_GAP_PITCHES = 6;
    private static final int MINIMUM_CLEAR_RADIUS_BLOCKS = 96;
    private static final int CLEAR_HEIGHT_BLOCKS = 48;
    private static final int BASELINE_DEPTH_BLOCKS = 4;

    private GalleryCaptureIsolationPlan() {
    }

    /** The one reusable annex origin, well beyond both axes of the authored gallery. */
    public static Placement placementFor(int sourceOriginX, int sourceOriginZ) {
        int annexOriginX = StructureGalleryPlan.WIDTH_BLOCKS
                + StructureGalleryPlan.PITCH * ANNEX_GAP_PITCHES;
        int annexOriginZ = StructureGalleryPlan.DEPTH_BLOCKS
                + StructureGalleryPlan.PITCH * ANNEX_GAP_PITCHES;
        return new Placement(
                sourceOriginX,
                sourceOriginZ,
                annexOriginX,
                annexOriginZ,
                annexOriginX - sourceOriginX,
                annexOriginZ - sourceOriginZ);
    }

    /** Radius cleared around the annex before staging an exact clone. */
    public static int clearRadiusBlocks() {
        return Math.max(
                MINIMUM_CLEAR_RADIUS_BLOCKS,
                StructureGalleryPlan.PITCH * 2 + 16);
    }

    public static int clearHeightBlocks() {
        return CLEAR_HEIGHT_BLOCKS;
    }

    public static int baselineDepthBlocks() {
        return BASELINE_DEPTH_BLOCKS;
    }

    public record Placement(
            int sourceOriginX,
            int sourceOriginZ,
            int annexOriginX,
            int annexOriginZ,
            int translationX,
            int translationZ) {
        public Placement {
            if (annexOriginX - clearRadiusBlocks() <= StructureGalleryPlan.WIDTH_BLOCKS
                    || annexOriginZ - clearRadiusBlocks() <= StructureGalleryPlan.DEPTH_BLOCKS) {
                throw new IllegalArgumentException(
                        "Capture annex clearance overlaps the authored gallery");
            }
            if (sourceOriginX + translationX != annexOriginX
                    || sourceOriginZ + translationZ != annexOriginZ) {
                throw new IllegalArgumentException("Capture annex translation is inconsistent");
            }
        }

        public int translateX(int sourceX) {
            return sourceX + translationX;
        }

        public int translateZ(int sourceZ) {
            return sourceZ + translationZ;
        }
    }
}
