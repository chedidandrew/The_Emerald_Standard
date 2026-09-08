package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, loader-neutral screenshot itinerary for reviewing the production structure
 * catalog without manually driving the gallery client.
 *
 * <p>The itinerary gives every structurally distinct revision-2 master and every Bank dialect a
 * front, rear/doodad, and primary interior view. Large/landmark masters and Banks also receive a
 * second interior view so multi-floor or multi-zone work is reviewable. It then samples the first
 * master for each project role in every biome dialect. That verifies material cohesion without
 * pretending that 260 palette renders are 260 independently authored buildings.</p>
 */
public final class StructureGalleryReviewPlan {
    public static final int MASTER_GEOMETRY_COUNT = 52;
    public static final int BANK_DIALECT_COUNT = 5;
    public static final int BIOME_COHESION_COUNT = 50;
    public static final int DOODAD_DETAIL_COUNT = Doodad.values().length;
    public static final int MULTI_ZONE_MASTER_COUNT = countMultiZoneMasters();
    public static final int MASTER_GEOMETRY_SHOT_COUNT =
            MASTER_GEOMETRY_COUNT * 3 + MULTI_ZONE_MASTER_COUNT;
    public static final int BANK_DIALECT_SHOT_COUNT = BANK_DIALECT_COUNT * 4;
    public static final int TOTAL_SHOT_COUNT =
            MASTER_GEOMETRY_SHOT_COUNT
                    + BANK_DIALECT_SHOT_COUNT
                    + DOODAD_DETAIL_COUNT
                    + BIOME_COHESION_COUNT;

    private static final List<View> STANDARD_VIEWS = List.of(
            View.FRONT,
            View.REAR_DOODADS,
            View.INTERIOR_PRIMARY);
    private static final List<View> MULTI_ZONE_VIEWS = List.of(
            View.FRONT,
            View.REAR_DOODADS,
            View.INTERIOR_PRIMARY,
            View.INTERIOR_SECONDARY);

    private static final List<Shot> SHOTS = buildShots();

    private StructureGalleryReviewPlan() {
    }

    public static List<Shot> shots() {
        return SHOTS;
    }

    private static List<Shot> buildShots() {
        List<Shot> shots = new ArrayList<>(TOTAL_SHOT_COUNT);
        List<StructureGalleryPlan.Entry> plainsMasters = StructureGalleryPlan.entries().stream()
                .filter(entry -> StructureGalleryPlan.GOLD_MASTER_MATRIX.equals(entry.section()))
                .filter(entry -> entry.dialect() == VillageArchitecture.BiomeDialect.PLAINS)
                .toList();
        if (plainsMasters.size() != MASTER_GEOMETRY_COUNT) {
            throw new IllegalStateException(
                    "Review itinerary expected " + MASTER_GEOMETRY_COUNT
                            + " Plains gold masters, found " + plainsMasters.size());
        }

        for (StructureGalleryPlan.Entry entry : plainsMasters) {
            for (View view : viewsFor(entry)) {
                add(shots, Coverage.MASTER_GEOMETRY, entry.index() + 1, view,
                        entry.templateId(), entry.dialect().id(), doodads(entry, view));
            }
        }
        for (StructureGalleryPlan.BankEntry bank : StructureGalleryPlan.bankEntries()) {
            for (View view : MULTI_ZONE_VIEWS) {
                add(shots, Coverage.BANK_DIALECT, bank.index() + 1, view,
                        "bank", bank.dialect().id(), List.of());
            }
        }

        // Give every reusable prop archetype its own close evidence frame. The representative
        // host and front/rear source are selected from the already-frozen master rows, so catalog
        // order changes cannot silently point a detail shot at a scene that lacks that doodad.
        List<Shot> masterViews = List.copyOf(shots);
        for (Doodad doodad : Doodad.values()) {
            Shot host = masterViews.stream()
                    .filter(shot -> shot.coverage() == Coverage.MASTER_GEOMETRY)
                    .filter(shot -> shot.view() == View.FRONT
                            || shot.view() == View.REAR_DOODADS)
                    .filter(shot -> shot.doodads().contains(doodad))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No deterministic gallery host for doodad " + doodad.id()));
            add(shots, Coverage.DOODAD_DETAIL, host.galleryIndex(), View.DOODAD_DETAIL,
                    host.subject(), host.dialect(), List.of(doodad),
                    new DoodadDetail(doodad, host.view()));
        }

        Map<VillageProsperityEngine.ProjectType, String> representativeByRole =
                new EnumMap<>(VillageProsperityEngine.ProjectType.class);
        for (StructureGalleryPlan.Entry entry : plainsMasters) {
            representativeByRole.putIfAbsent(entry.type(), entry.templateId());
        }
        if (representativeByRole.size() != VillageProsperityEngine.ProjectType.values().length) {
            throw new IllegalStateException("Review itinerary omitted a production project role");
        }
        for (VillageProsperityEngine.ProjectType role
                : VillageProsperityEngine.ProjectType.values()) {
            String representative = representativeByRole.get(role);
            for (VillageArchitecture.BiomeDialect dialect
                    : VillageArchitecture.BiomeDialect.values()) {
                StructureGalleryPlan.Entry entry = StructureGalleryPlan.entries().stream()
                        .filter(candidate -> StructureGalleryPlan.GOLD_MASTER_MATRIX.equals(
                                candidate.section()))
                        .filter(candidate -> candidate.type() == role)
                        .filter(candidate -> candidate.templateId().equals(representative))
                        .filter(candidate -> candidate.dialect() == dialect)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Review itinerary cannot find " + role + " / " + dialect));
                add(shots, Coverage.BIOME_COHESION, entry.index() + 1, View.FRONT,
                        entry.templateId(), entry.dialect().id(), doodads(entry, View.FRONT));
            }
        }

        if (shots.size() != TOTAL_SHOT_COUNT) {
            throw new IllegalStateException(
                    "Review itinerary expected " + TOTAL_SHOT_COUNT
                            + " shots, found " + shots.size());
        }
        return List.copyOf(shots);
    }

    private static void add(
            List<Shot> shots,
            Coverage coverage,
            int galleryIndex,
            View view,
            String subject,
            String dialect,
            List<Doodad> doodads) {
        add(shots, coverage, galleryIndex, view, subject, dialect, doodads, null);
    }

    private static void add(
            List<Shot> shots,
            Coverage coverage,
            int galleryIndex,
            View view,
            String subject,
            String dialect,
            List<Doodad> doodads,
            DoodadDetail doodadDetail) {
        int sequence = shots.size() + 1;
        String filename = String.format(
                Locale.ROOT,
                "%03d_%s_gallery-%03d_%s_%s_%s.png",
                sequence,
                coverage.id(),
                galleryIndex,
                slug(subject),
                slug(dialect),
                view.id());
        shots.add(new Shot(
                sequence,
                coverage,
                galleryIndex,
                view,
                subject,
                dialect,
                doodads,
                doodadDetail,
                filename));
    }

    /**
     * Mirrors the authored scene selector so the manifest identifies what the corresponding
     * front/rear frame actually contains. This is review metadata only; it never influences the
     * production blueprint or randomizes a gallery fixture.
     */
    private static List<Doodad> doodads(StructureGalleryPlan.Entry entry, View view) {
        if (view.isInterior()) {
            return List.of();
        }
        VillageArchitecture.BlueprintDescriptor descriptor = VillageArchitecture.requireBlueprint(
                entry.templateId(), entry.templateRevision());
        LinkedHashSet<Doodad> result = new LinkedHashSet<>();
        if (view == View.FRONT) {
            switch (entry.type()) {
                case COTTAGE -> {
                    if ((doodadVariant(entry, descriptor, 101) & 2) == 0) {
                        result.add(Doodad.RAIL_BOUND_LOG_RACK);
                        result.add(Doodad.PLANTER_RUN);
                    } else {
                        result.add(Doodad.SLAB_AND_FENCE_BENCH);
                        result.add(Doodad.FORECOURT_PLANT_PEDESTAL);
                    }
                }
                case HOUSE -> {
                    result.add(Doodad.SLAB_AND_FENCE_BENCH);
                    result.add(Doodad.PLANTER_RUN);
                }
                case INN -> {
                    result.add(Doodad.HITCHING_RAIL);
                    result.add(Doodad.CRATE_CLUSTER);
                }
                case WAREHOUSE -> {
                    result.add(Doodad.RAIL_BOUND_LOG_RACK);
                    result.add(Doodad.CRATE_CLUSTER);
                }
                case GRANARY -> {
                    result.add(Doodad.FEED_TROUGH);
                    result.add(Doodad.HAY_PILE);
                }
                case SMITHY -> {
                    result.add(Doodad.TOOL_RACK);
                    result.add(Doodad.MATERIAL_PILE);
                }
                case MINE_ENTRANCE -> {
                    result.add(Doodad.RAIL_BOUND_LOG_RACK);
                    result.add(Doodad.MATERIAL_PILE);
                }
                case MARKET_SQUARE -> {
                    result.add(Doodad.FEED_TROUGH);
                    result.add(Doodad.CRATE_CLUSTER);
                    result.add(Doodad.FORECOURT_PLANT_PEDESTAL);
                }
                case GUARD_POST -> {
                    result.add(Doodad.GUARD_TARGET_RACK);
                    result.add(Doodad.CRATE_CLUSTER);
                }
                case EXCHANGE_HALL -> {
                    result.add(Doodad.SLAB_AND_FENCE_BENCH);
                    result.add(Doodad.PLANTER_RUN);
                }
            }
            if (entry.type() != VillageProsperityEngine.ProjectType.COTTAGE
                    || descriptor.scale() != VillageArchitecture.BlueprintScale.SMALL
                    || (doodadVariant(entry, descriptor, 101) & 2) == 0) {
                result.add(Doodad.FREESTANDING_LAMP_POST);
            }
            if (descriptor.scale() == VillageArchitecture.BlueprintScale.LARGE
                    || descriptor.scale() == VillageArchitecture.BlueprintScale.LANDMARK) {
                if (entry.type() == VillageProsperityEngine.ProjectType.COTTAGE
                        || entry.type() == VillageProsperityEngine.ProjectType.HOUSE) {
                    result.add(Doodad.FORECOURT_PLANT_PEDESTAL);
                } else {
                    result.add(Doodad.FORECOURT_CARGO_PEDESTAL);
                    if (entry.type() == VillageProsperityEngine.ProjectType.EXCHANGE_HALL
                            && descriptor.scale()
                            == VillageArchitecture.BlueprintScale.LANDMARK) {
                        result.add(Doodad.FORECOURT_PLANT_PEDESTAL);
                    }
                }
            }
            return List.copyOf(result);
        }

        switch (entry.type()) {
            case COTTAGE -> result.add(
                    (doodadVariant(entry, descriptor, 103) & 1) == 0
                            ? Doodad.SAFE_CAMPFIRE_NOOK
                            : Doodad.GARDEN_WORK_CORNER);
            case HOUSE -> {
                if ((doodadVariant(entry, descriptor, 113) & 2) == 0) {
                    result.add(Doodad.SAFE_CAMPFIRE_NOOK);
                } else {
                    result.add(Doodad.HAND_CART);
                    result.add(Doodad.PLANTER_RUN);
                }
            }
            case INN -> {
                result.add(Doodad.SAFE_CAMPFIRE_NOOK);
                result.add(Doodad.CRATE_CLUSTER);
                result.add(Doodad.PLANTER_RUN);
            }
            case WAREHOUSE -> {
                result.add(Doodad.HAND_CART);
                result.add(Doodad.CRATE_CLUSTER);
            }
            case GRANARY -> {
                result.add(Doodad.HAND_CART);
                result.add(Doodad.HAY_PILE);
                result.add(Doodad.CRATE_CLUSTER);
            }
            case SMITHY -> {
                result.add(Doodad.HAND_CART);
                result.add(Doodad.MATERIAL_PILE);
                result.add(Doodad.CRATE_CLUSTER);
            }
            case MINE_ENTRANCE -> {
                result.add(Doodad.HAND_CART);
                result.add(Doodad.TOOL_RACK);
                result.add(Doodad.CRATE_CLUSTER);
            }
            case MARKET_SQUARE -> {
                result.add(Doodad.HAND_CART);
                result.add(Doodad.SLAB_AND_FENCE_BENCH);
                result.add(Doodad.PLANTER_RUN);
            }
            case GUARD_POST -> {
                if ((doodadVariant(entry, descriptor, 211) & 2) == 0) {
                    result.add(Doodad.SAFE_CAMPFIRE_NOOK);
                } else {
                    result.add(Doodad.GUARD_TARGET_RACK);
                    result.add(Doodad.TOOL_RACK);
                    result.add(Doodad.FREESTANDING_LAMP_POST);
                }
            }
            case EXCHANGE_HALL -> {
                if ((doodadVariant(entry, descriptor, 227) & 2) == 0) {
                    result.add(Doodad.SLAB_AND_FENCE_BENCH);
                } else {
                    result.add(Doodad.HAND_CART);
                }
                result.add(Doodad.PLANTER_RUN);
                result.add(Doodad.FREESTANDING_LAMP_POST);
            }
        }
        return List.copyOf(result);
    }

    private static int doodadVariant(
            StructureGalleryPlan.Entry entry,
            VillageArchitecture.BlueprintDescriptor descriptor,
            int salt) {
        int seed = 17;
        seed = seed * 31 + entry.type().ordinal();
        seed = seed * 31 + descriptor.width();
        seed = seed * 31 + descriptor.depth();
        seed = seed * 31 + descriptor.height();
        seed = seed * 31 + Long.hashCode(entry.blueprintSignature());
        seed = seed * 31 + 2; // Every gold-master review entry uses prosperous dressing.
        seed = seed * 31 + entry.dressingId().hashCode();
        seed ^= salt * 0x45d9f3b;
        return Math.floorMod(seed, 4);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static int countMultiZoneMasters() {
        return (int) StructureGalleryPlan.entries().stream()
                .filter(entry -> StructureGalleryPlan.GOLD_MASTER_MATRIX.equals(entry.section()))
                .filter(entry -> entry.dialect() == VillageArchitecture.BiomeDialect.PLAINS)
                .filter(StructureGalleryReviewPlan::isMultiZone)
                .count();
    }

    private static List<View> viewsFor(StructureGalleryPlan.Entry entry) {
        return isMultiZone(entry) ? MULTI_ZONE_VIEWS : STANDARD_VIEWS;
    }

    private static boolean isMultiZone(StructureGalleryPlan.Entry entry) {
        VillageArchitecture.BlueprintScale scale = VillageArchitecture.requireBlueprint(
                entry.templateId(), entry.templateRevision()).scale();
        return scale == VillageArchitecture.BlueprintScale.LARGE
                || scale == VillageArchitecture.BlueprintScale.LANDMARK;
    }

    public enum View {
        FRONT("front-day"),
        REAR_DOODADS("rear-doodads-day"),
        INTERIOR_PRIMARY("interior-primary-night"),
        INTERIOR_SECONDARY("interior-secondary-night"),
        DOODAD_DETAIL("doodad-detail-day");

        private final String id;

        View(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public boolean isInterior() {
            return this == INTERIOR_PRIMARY || this == INTERIOR_SECONDARY;
        }
    }

    public enum Coverage {
        MASTER_GEOMETRY("master"),
        BANK_DIALECT("bank"),
        DOODAD_DETAIL("doodad"),
        BIOME_COHESION("cohesion");

        private final String id;

        Coverage(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** Stable IDs matching Carol's independently scored doodad ledger. */
    public enum Doodad {
        FORECOURT_PLANT_PEDESTAL("D1"),
        FORECOURT_CARGO_PEDESTAL("D2"),
        FREESTANDING_LAMP_POST("D3"),
        RAIL_BOUND_LOG_RACK("D4"),
        SLAB_AND_FENCE_BENCH("D5"),
        SAFE_CAMPFIRE_NOOK("D6"),
        GARDEN_WORK_CORNER("D7"),
        PLANTER_RUN("D8"),
        CRATE_CLUSTER("D9"),
        HAND_CART("D10"),
        HITCHING_RAIL("D11"),
        FEED_TROUGH("D12"),
        HAY_PILE("D13"),
        MATERIAL_PILE("D14"),
        TOOL_RACK("D15"),
        GUARD_TARGET_RACK("D16");

        private final String id;

        Doodad(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Shot(
            int sequence,
            Coverage coverage,
            int galleryIndex,
            View view,
            String subject,
            String dialect,
            List<Doodad> doodads,
            DoodadDetail doodadDetail,
            String filename) {
        public Shot {
            if (sequence <= 0 || galleryIndex <= 0) {
                throw new IllegalArgumentException("Review shot indices must be positive");
            }
            coverage = Objects.requireNonNull(coverage, "coverage");
            view = Objects.requireNonNull(view, "view");
            subject = Objects.requireNonNull(subject, "subject");
            dialect = Objects.requireNonNull(dialect, "dialect");
            doodads = List.copyOf(doodads);
            filename = Objects.requireNonNull(filename, "filename");
            if ((view == View.DOODAD_DETAIL) != (doodadDetail != null)) {
                throw new IllegalArgumentException(
                        "Only a doodad-detail shot may carry doodad focus metadata");
            }
            if (doodadDetail != null
                    && (doodads.size() != 1 || doodads.getFirst() != doodadDetail.doodad())) {
                throw new IllegalArgumentException(
                        "Doodad-detail shot must identify exactly its focused archetype");
            }
        }
    }

    public record DoodadDetail(Doodad doodad, View sourceView) {
        public DoodadDetail {
            doodad = Objects.requireNonNull(doodad, "doodad");
            sourceView = Objects.requireNonNull(sourceView, "sourceView");
            if (sourceView != View.FRONT && sourceView != View.REAR_DOODADS) {
                throw new IllegalArgumentException(
                        "Doodad focus source must be a front or rear master view");
            }
        }
    }
}
