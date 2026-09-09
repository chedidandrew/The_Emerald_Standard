package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, loader-neutral layout for reviewing immutable Blueprint V2 structures.
 *
 * <p>The gallery intentionally does not manufacture combinations of independently selected
 * floorplans, roofs, frontages, or interiors. Its main matrix renders every complete authored
 * descriptor in every supported biome dialect. A much smaller controlled lab varies one approved
 * descriptor along one review axis at a time.</p>
 */
public final class StructureGalleryPlan {
    public static final String GOLD_MASTER_MATRIX = "gold_master_matrix";
    public static final String CONTROLLED_BLUEPRINT_LAB = "controlled_blueprint_lab";

    /**
     * Gallery render-schema revision. Increment whenever production geometry or presentation can
     * change without changing a persisted Blueprint descriptor identity.
     */
    public static final int GALLERY_CONTENT_REVISION = 17;

    private static final int MINIMUM_PITCH = 24;
    private static final int AUTHORED_FRONT_EXTENT = 7;
    private static final int AUTHORED_SIDE_EXTENT = 5;
    private static final int AUTHORED_REAR_EXTENT = 4;
    static final int PLOT_SAFETY_MARGIN =
            AUTHORED_FRONT_EXTENT + AUTHORED_REAR_EXTENT + 1;
    private static final int MAXIMUM_COLUMNS = 12;
    private static final int FULL_VISUAL_STAGE = 2;
    private static final int REVIEW_HUB_YAW_DEGREES = 180;
    private static final int CONTROLLED_VIEW_COUNT = 11;
    public static final int STANDALONE_BANK_WIDTH = 13;
    public static final int STANDALONE_BANK_DEPTH = 11;
    public static final int STANDALONE_BANK_HEIGHT = 11;
    public static final int STANDALONE_BANK_MIN_X = -1;
    public static final int STANDALONE_BANK_MAX_X = STANDALONE_BANK_WIDTH;
    public static final int STANDALONE_BANK_MIN_Z = -2;
    public static final int STANDALONE_BANK_MAX_Z = STANDALONE_BANK_DEPTH;
    private static final double MINIMUM_VISIT_STANDOFF = 10.0;
    private static final double VISIT_STANDOFF_SCALE = 0.75;
    private static final float VISIT_YAW_DEGREES = 0.0F;
    private static final float VISIT_PITCH_DEGREES = 20.0F;

    private static final List<VillageProsperityEngine.ProjectType> PROJECT_ORDER = List.of(
            VillageProsperityEngine.ProjectType.COTTAGE,
            VillageProsperityEngine.ProjectType.HOUSE,
            VillageProsperityEngine.ProjectType.INN,
            VillageProsperityEngine.ProjectType.WAREHOUSE,
            VillageProsperityEngine.ProjectType.GRANARY,
            VillageProsperityEngine.ProjectType.SMITHY,
            VillageProsperityEngine.ProjectType.MINE_ENTRANCE,
            VillageProsperityEngine.ProjectType.MARKET_SQUARE,
            VillageProsperityEngine.ProjectType.GUARD_POST,
            VillageProsperityEngine.ProjectType.EXCHANGE_HALL);

    private static final List<VillageArchitecture.BlueprintDescriptor> GOLD_MASTERS =
            buildGoldMasters();
    private static final int GOLD_MASTER_COLUMNS = Math.min(
            MAXIMUM_COLUMNS, Math.max(1, GOLD_MASTERS.size()));
    private static final int GOLD_MASTER_PAGES =
            (GOLD_MASTERS.size() + GOLD_MASTER_COLUMNS - 1) / GOLD_MASTER_COLUMNS;
    private static final int GOLD_MASTER_ROWS =
            GOLD_MASTER_PAGES * VillageArchitecture.BiomeDialect.values().length;
    private static final int CONTROLLED_ROW = GOLD_MASTER_ROWS + 1;
    private static final int BANK_ROW = CONTROLLED_ROW + 2;

    /** Plot pitch grows with the largest active master and includes authored prop margins. */
    public static final int PITCH = galleryPitch();
    public static final int WIDTH_BLOCKS = Math.max(
            GOLD_MASTER_COLUMNS,
            Math.max(CONTROLLED_VIEW_COUNT, VillageArchitecture.BiomeDialect.values().length))
            * PITCH;
    public static final int DEPTH_BLOCKS = (BANK_ROW + 1) * PITCH;

    private static final List<Entry> ENTRIES = buildEntries();
    private static final List<BankEntry> BANK_ENTRIES = buildBankEntries(ENTRIES.size());
    private static final List<VisitTarget> VISIT_TARGETS = buildVisitTargets();

    private StructureGalleryPlan() {
    }

    /** The stable catalog order paged across the main matrix. */
    public static List<VillageArchitecture.BlueprintDescriptor> goldMasters() {
        return GOLD_MASTERS;
    }

    /** All Blueprint V2 entries, ordered by section and then by gallery position. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** One authored standalone Bank entry for each biome dialect. */
    public static List<BankEntry> bankEntries() {
        return BANK_ENTRIES;
    }

    /** Descriptor- and rotation-aware screenshot framing for every indexed gallery structure. */
    public static List<VisitTarget> visitTargets() {
        return VISIT_TARGETS;
    }

    /** Number of complete descriptor-by-dialect reference renders. */
    public static int goldMasterViewCount() {
        return GOLD_MASTERS.size() * VillageArchitecture.BiomeDialect.values().length;
    }

    /** Number of deliberately isolated comparison renders. */
    public static int controlledLabViewCount() {
        return ENTRIES.size() - goldMasterViewCount();
    }

    /** Total Blueprint V2 structures and standalone Banks in the complete gallery. */
    public static int totalStructureCount() {
        return ENTRIES.size() + BANK_ENTRIES.size();
    }

    /** Empty row between the paged master matrix and controlled comparison lab. */
    public static int reviewHubZ() {
        return GOLD_MASTER_ROWS * PITCH;
    }

    /** North-facing yaw from the review hub toward the complete gold-master matrix. */
    public static int reviewHubYawDegrees() {
        return REVIEW_HUB_YAW_DEGREES;
    }

    /**
     * Stable marker payload for the disposable world. A catalog, layout, or explicit gallery
     * render-schema revision changes this value, so an older partially reviewed gallery cannot be
     * mistaken for the current build.
     */
    public static long layoutSignature() {
        return layoutSignature(GALLERY_CONTENT_REVISION, ENTRIES, BANK_ENTRIES);
    }

    static long layoutSignature(List<Entry> entries, List<BankEntry> bankEntries) {
        return layoutSignature(GALLERY_CONTENT_REVISION, entries, bankEntries);
    }

    static long layoutSignature(
            int contentRevision, List<Entry> entries, List<BankEntry> bankEntries) {
        if (contentRevision <= 0) {
            throw new IllegalArgumentException("Gallery content revision must be positive");
        }
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(bankEntries, "bankEntries");
        long hash = 0xCBF29CE484222325L;
        hash = signatureWord(hash, contentRevision);
        hash = signatureWord(hash, PITCH);
        hash = signatureWord(hash, WIDTH_BLOCKS);
        hash = signatureWord(hash, DEPTH_BLOCKS);
        hash = signatureWord(hash, GOLD_MASTERS.size());
        for (VillageArchitecture.BlueprintDescriptor descriptor : GOLD_MASTERS) {
            for (int index = 0; index < descriptor.templateId().length(); index++) {
                hash = signatureWord(hash, descriptor.templateId().charAt(index));
            }
            hash = signatureWord(hash, descriptor.templateRevision());
            hash = signatureWord(hash, descriptor.type().ordinal());
            hash = signatureWord(hash, descriptor.scale().ordinal());
            hash = signatureWord(hash, descriptor.width());
            hash = signatureWord(hash, descriptor.depth());
            hash = signatureWord(hash, descriptor.height());
        }
        hash = signatureWord(hash, entries.size());
        for (Entry entry : entries) {
            hash = signatureWord(hash, entry.index());
            hash = signatureText(hash, entry.section());
            hash = signatureWord(hash, entry.originX());
            hash = signatureWord(hash, entry.originZ());
            hash = signatureWord(hash, entry.type().ordinal());
            hash = signatureWord(hash, entry.dialect().ordinal());
            hash = signatureWord(hash, entry.character().ordinal());
            hash = signatureText(hash, entry.templateId());
            hash = signatureWord(hash, entry.templateRevision());
            hash = signatureText(hash, entry.paletteId());
            hash = signatureText(hash, entry.dressingId());
            hash = signatureWord(hash, entry.mirrored() ? 1 : 0);
            hash = signatureWord(hash, entry.visualStage());
            hash = signatureWord(hash, entry.rotation());
        }
        hash = signatureWord(hash, bankEntries.size());
        for (BankEntry bank : bankEntries) {
            hash = signatureWord(hash, bank.index());
            hash = signatureWord(hash, bank.originX());
            hash = signatureWord(hash, bank.originZ());
            hash = signatureWord(hash, bank.dialect().ordinal());
        }
        return VillageArchitecture.mix64(hash);
    }

    private static List<VillageArchitecture.BlueprintDescriptor> buildGoldMasters() {
        List<VillageArchitecture.BlueprintDescriptor> descriptors =
                new ArrayList<>(VillageArchitecture.activeBlueprints().size());
        for (VillageProsperityEngine.ProjectType type : PROJECT_ORDER) {
            descriptors.addAll(VillageArchitecture.blueprints(type));
        }
        if (descriptors.size() != VillageArchitecture.activeBlueprints().size()) {
            throw new IllegalStateException(
                    "Blueprint V2 gallery omitted an active gold master");
        }
        return List.copyOf(descriptors);
    }

    private static List<Entry> buildEntries() {
        int matrixViews = GOLD_MASTERS.size()
                * VillageArchitecture.BiomeDialect.values().length;
        List<Entry> entries = new ArrayList<>(matrixViews + 11);
        appendGoldMasterMatrix(entries);
        appendControlledBlueprintLab(entries);
        return List.copyOf(entries);
    }

    private static void appendGoldMasterMatrix(List<Entry> entries) {
        VillageArchitecture.BiomeDialect[] dialects =
                VillageArchitecture.BiomeDialect.values();
        for (int page = 0; page < GOLD_MASTER_PAGES; page++) {
            int firstDescriptor = page * GOLD_MASTER_COLUMNS;
            int descriptorCount = Math.min(
                    GOLD_MASTER_COLUMNS, GOLD_MASTERS.size() - firstDescriptor);
            for (int dialectRow = 0; dialectRow < dialects.length; dialectRow++) {
                int galleryRow = page * dialects.length + dialectRow;
                for (int column = 0; column < descriptorCount; column++) {
                    VillageArchitecture.BlueprintDescriptor descriptor =
                            GOLD_MASTERS.get(firstDescriptor + column);
                    entries.add(entry(
                            entries.size(),
                            GOLD_MASTER_MATRIX,
                            column,
                            galleryRow,
                            descriptor,
                            dialects[dialectRow],
                            VillageArchitecture.Character.MERCANTILE,
                            VillageArchitecture.PALETTE_BALANCED,
                            VillageArchitecture.DRESSING_PROSPEROUS,
                            false,
                            FULL_VISUAL_STAGE,
                            0));
                }
            }
        }
    }

    private static int galleryPitch() {
        int maximumMasterEnvelope = GOLD_MASTERS.stream()
                .mapToInt(descriptor -> Math.max(descriptor.width(), descriptor.depth()))
                .max()
                .orElse(MINIMUM_PITCH - PLOT_SAFETY_MARGIN);
        int bankEnvelope = Math.max(
                STANDALONE_BANK_MAX_X - STANDALONE_BANK_MIN_X + 1,
                STANDALONE_BANK_MAX_Z - STANDALONE_BANK_MIN_Z + 1);
        int maximumEnvelope = Math.max(maximumMasterEnvelope, bankEnvelope);
        int required = Math.max(MINIMUM_PITCH, maximumEnvelope + PLOT_SAFETY_MARGIN);
        return (required + 7) / 8 * 8;
    }

    private static long signatureWord(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * 0x100000001B3L;
    }

    private static long signatureText(long hash, String value) {
        hash = signatureWord(hash, value.length());
        for (int index = 0; index < value.length(); index++) {
            hash = signatureWord(hash, value.charAt(index));
        }
        return hash;
    }

    private static void appendControlledBlueprintLab(List<Entry> entries) {
        VillageArchitecture.BlueprintDescriptor descriptor =
                VillageArchitecture.requireBlueprint("house_cross_01", 9);
        int column = 0;

        // Stage axis. The final entry is the shared reference for every later comparison.
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_RESTRAINED, false, 0, 0));
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_RESTRAINED, false, 1, 0));
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_RESTRAINED, false, 2, 0));

        // Palette axis (balanced is the shared reference above).
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_TIMBER_FORWARD,
                VillageArchitecture.DRESSING_RESTRAINED, false, 2, 0));
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_MASONRY_FORWARD,
                VillageArchitecture.DRESSING_RESTRAINED, false, 2, 0));

        // Dressing axis (restrained is the shared reference above).
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_LIVED_IN, false, 2, 0));
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_PROSPEROUS, false, 2, 0));

        // Mirror axis. This descriptor explicitly declares mirroring safe.
        entries.add(controlled(entries.size(), column++, descriptor,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_RESTRAINED, true, 2, 0));

        // Rotation axis (north/zero is the shared reference above).
        for (int rotation = 1; rotation <= 3; rotation++) {
            entries.add(controlled(entries.size(), column++, descriptor,
                    VillageArchitecture.PALETTE_BALANCED,
                    VillageArchitecture.DRESSING_RESTRAINED, false, 2, rotation));
        }
    }

    private static Entry controlled(
            int index,
            int column,
            VillageArchitecture.BlueprintDescriptor descriptor,
            String paletteId,
            String dressingId,
            boolean mirrored,
            int visualStage,
            int rotation) {
        return entry(
                index,
                CONTROLLED_BLUEPRINT_LAB,
                column,
                CONTROLLED_ROW,
                descriptor,
                VillageArchitecture.BiomeDialect.PLAINS,
                VillageArchitecture.Character.MERCANTILE,
                paletteId,
                dressingId,
                mirrored,
                visualStage,
                rotation);
    }

    private static Entry entry(
            int index,
            String section,
            int column,
            int row,
            VillageArchitecture.BlueprintDescriptor descriptor,
            VillageArchitecture.BiomeDialect dialect,
            VillageArchitecture.Character character,
            String paletteId,
            String dressingId,
            boolean mirrored,
            int visualStage,
            int rotation) {
        return new Entry(
                index,
                section,
                column * PITCH,
                row * PITCH,
                descriptor.type(),
                dialect,
                character,
                descriptor.templateId(),
                descriptor.templateRevision(),
                paletteId,
                dressingId,
                mirrored,
                visualStage,
                rotation);
    }

    private static List<BankEntry> buildBankEntries(int firstIndex) {
        VillageArchitecture.BiomeDialect[] dialects =
                VillageArchitecture.BiomeDialect.values();
        List<BankEntry> banks = new ArrayList<>(dialects.length);
        for (int column = 0; column < dialects.length; column++) {
            banks.add(new BankEntry(
                    firstIndex + column,
                    column * PITCH,
                    BANK_ROW * PITCH,
                    dialects[column]));
        }
        return List.copyOf(banks);
    }

    private static List<VisitTarget> buildVisitTargets() {
        List<VisitTarget> targets = new ArrayList<>(ENTRIES.size() + BANK_ENTRIES.size());
        for (Entry entry : ENTRIES) {
            VillageArchitecture.BlueprintDescriptor descriptor =
                    VillageArchitecture.requireBlueprint(
                            entry.templateId(), entry.templateRevision());
            targets.add(new VisitTarget(
                    entry.index(),
                    entry.originX(),
                    entry.originZ(),
                    descriptor.width(),
                    descriptor.depth(),
                    descriptor.height(),
                    entry.rotation()));
        }
        for (BankEntry bank : BANK_ENTRIES) {
            targets.add(new VisitTarget(
                    bank.index(),
                    bank.originX(),
                    bank.originZ(),
                    STANDALONE_BANK_WIDTH,
                    STANDALONE_BANK_DEPTH,
                    STANDALONE_BANK_HEIGHT,
                    0));
        }
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).index() != index) {
                throw new IllegalStateException(
                        "Gallery visit targets are not globally contiguous");
            }
        }
        return List.copyOf(targets);
    }

    /** One explicit Blueprint V2 view; no structural recipe can be recombined here. */
    public record Entry(
            int index,
            String section,
            int originX,
            int originZ,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.BiomeDialect dialect,
            VillageArchitecture.Character character,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            boolean mirrored,
            int visualStage,
            int rotation) {
        public Entry {
            if (index < 0 || originX < 0 || originZ < 0) {
                throw new IllegalArgumentException(
                        "Gallery entry index and origin must be non-negative");
            }
            section = Objects.requireNonNull(section, "section");
            type = Objects.requireNonNull(type, "type");
            dialect = Objects.requireNonNull(dialect, "dialect");
            character = Objects.requireNonNull(character, "character");
            templateId = Objects.requireNonNull(templateId, "templateId");
            paletteId = Objects.requireNonNull(paletteId, "paletteId");
            dressingId = Objects.requireNonNull(dressingId, "dressingId");
            if (visualStage < 0 || visualStage > FULL_VISUAL_STAGE) {
                throw new IllegalArgumentException("Invalid gallery visual stage " + visualStage);
            }
            if (rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("Invalid gallery rotation " + rotation);
            }

            VillageArchitecture.BlueprintDescriptor descriptor =
                    VillageArchitecture.requireBlueprint(templateId, templateRevision);
            if (descriptor.type() != type
                    || !descriptor.paletteIds().contains(paletteId)
                    || !descriptor.dressingIds().contains(dressingId)
                    || (mirrored && !descriptor.mirrorable())) {
                throw new IllegalArgumentException(
                        "Gallery entry does not match Blueprint V2 descriptor "
                                + templateId + "@" + templateRevision);
            }
        }

        /** Stable persisted design identity represented by this review entry. */
        public long blueprintSignature() {
            return VillageArchitecture.blueprintSignature(
                    type,
                    templateId,
                    templateRevision,
                    paletteId,
                    dressingId,
                    mirrored);
        }
    }

    /** One authored standalone Bank view using a specific biome dialect. */
    public record BankEntry(
            int index,
            int originX,
            int originZ,
            VillageArchitecture.BiomeDialect dialect) {
        public BankEntry {
            if (index < 0 || originX < 0 || originZ < 0) {
                throw new IllegalArgumentException(
                        "Gallery Bank index and origin must be non-negative");
            }
            dialect = Objects.requireNonNull(dialect, "dialect");
        }
    }

    /** Camera geometry for one indexed structure, expressed in gallery/world XZ coordinates. */
    public record VisitTarget(
            int index,
            int originX,
            int originZ,
            int width,
            int depth,
            int height,
            int rotation) {
        public VisitTarget {
            if (index < 0 || originX < 0 || originZ < 0
                    || width <= 0 || depth <= 0 || height <= 0
                    || rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("Invalid gallery visit target geometry");
            }
        }

        /** Width of the descriptor footprint after its persisted quarter-turn rotation. */
        public int rotatedWidth() {
            return rotation % 2 == 0 ? width : depth;
        }

        /** Depth of the descriptor footprint after its persisted quarter-turn rotation. */
        public int rotatedDepth() {
            return rotation % 2 == 0 ? depth : width;
        }

        /** Inclusive minimum X of the rotated, conservatively expanded authored fixture. */
        public int minimumFrameX() {
            return rotatedCornerExtreme(true, true);
        }

        /** Inclusive maximum X of the rotated, conservatively expanded authored fixture. */
        public int maximumFrameX() {
            return rotatedCornerExtreme(true, false);
        }

        /** Inclusive minimum Z of the rotated, conservatively expanded authored fixture. */
        public int minimumFrameZ() {
            return rotatedCornerExtreme(false, true);
        }

        /** Inclusive maximum Z of the rotated, conservatively expanded authored fixture. */
        public int maximumFrameZ() {
            return rotatedCornerExtreme(false, false);
        }

        /** Horizontal screenshot span when viewing the structure's rotated authored front. */
        public int framingWidth() {
            return rotation % 2 == 0
                    ? maximumFrameX() - minimumFrameX() + 1
                    : maximumFrameZ() - minimumFrameZ() + 1;
        }

        /** Fixture depth along the screenshot view axis, including exterior authored work. */
        public int framingDepth() {
            return rotation % 2 == 0
                    ? maximumFrameZ() - minimumFrameZ() + 1
                    : maximumFrameX() - minimumFrameX() + 1;
        }

        /** Centers the reviewer on the complete transformed fixture, including exterior work. */
        public double cameraX() {
            double centerX = originX + frameCenter(minimumFrameX(), maximumFrameX());
            return switch (rotation) {
                case 1 -> centerX + cameraDistance();
                case 3 -> centerX - cameraDistance();
                default -> centerX;
            };
        }

        /** Orbits around the complete fixture so every rotation is reviewed from its front. */
        public double cameraZ() {
            double centerZ = originZ + frameCenter(minimumFrameZ(), maximumFrameZ());
            return switch (rotation) {
                case 0 -> centerZ - cameraDistance();
                case 2 -> centerZ + cameraDistance();
                default -> centerZ;
            };
        }

        /** Y offset from the gallery surface which aims the fixed pitch at the volume center. */
        public double cameraYOffset() {
            double centerY = (height + 1.0) / 2.0;
            return centerY + Math.tan(Math.toRadians(VISIT_PITCH_DEGREES))
                    * cameraDistance();
        }

        public float yawDegrees() {
            return VISIT_YAW_DEGREES + rotation * 90.0F;
        }

        public float pitchDegrees() {
            return VISIT_PITCH_DEGREES;
        }

        private double cameraDistance() {
            double physicalHeight = height + 1.0;
            double framingSpan = Math.max(framingWidth(), physicalHeight);
            double standoff = Math.max(
                    MINIMUM_VISIT_STANDOFF,
                    framingSpan * VISIT_STANDOFF_SCALE);
            return framingDepth() / 2.0 + standoff;
        }

        private int rotatedCornerExtreme(boolean xAxis, boolean minimum) {
            int minimumX = -AUTHORED_SIDE_EXTENT;
            int minimumZ = -AUTHORED_FRONT_EXTENT;
            int maximumX = width + AUTHORED_REAR_EXTENT;
            int maximumZ = depth + AUTHORED_REAR_EXTENT;
            int extreme = minimum ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            for (int x : new int[] {minimumX, maximumX}) {
                for (int z : new int[] {minimumZ, maximumZ}) {
                    int coordinate = xAxis ? rotateX(x, z) : rotateZ(x, z);
                    extreme = minimum
                            ? Math.min(extreme, coordinate)
                            : Math.max(extreme, coordinate);
                }
            }
            return extreme;
        }

        private int rotateX(int x, int z) {
            return switch (rotation) {
                case 1 -> depth - 1 - z;
                case 2 -> width - 1 - x;
                case 3 -> z;
                default -> x;
            };
        }

        private int rotateZ(int x, int z) {
            return switch (rotation) {
                case 1 -> x;
                case 2 -> depth - 1 - z;
                case 3 -> width - 1 - x;
                default -> z;
            };
        }

        private static double frameCenter(int minimum, int maximum) {
            return (minimum + maximum + 1) / 2.0;
        }
    }
}
