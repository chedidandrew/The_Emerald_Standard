package com.chedidandrew.emeraldstandard.core;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Locks the deterministic Carol-review itinerary and its stable artifact identities. */
public final class StructureGalleryReviewPlanRegressionTest {
    private StructureGalleryReviewPlanRegressionTest() {
    }

    public static void main(String[] args) {
        List<StructureGalleryReviewPlan.Shot> shots = StructureGalleryReviewPlan.shots();
        require(StructureGalleryReviewPlan.MULTI_ZONE_MASTER_COUNT == 25,
                "Large/landmark review count changed; reconcile the evidence contract");
        require(StructureGalleryReviewPlan.MASTER_GEOMETRY_SHOT_COUNT == 181,
                "Master review shot count changed; reconcile the evidence contract");
        require(StructureGalleryReviewPlan.TOTAL_SHOT_COUNT == 267,
                "Complete Carol review count changed; reconcile the evidence contract");
        require(shots.size() == StructureGalleryReviewPlan.TOTAL_SHOT_COUNT,
                "Review itinerary has an unexpected total");

        Set<String> filenames = new HashSet<>();
        for (int index = 0; index < shots.size(); index++) {
            StructureGalleryReviewPlan.Shot shot = shots.get(index);
            require(shot.sequence() == index + 1, "Review sequence is not contiguous");
            require(shot.galleryIndex() <= StructureGalleryPlan.totalStructureCount(),
                    "Review shot points outside the production gallery");
            require(filenames.add(shot.filename()),
                    "Review filenames are not unique: " + shot.filename());
            require(shot.filename().matches("[a-z0-9_.-]+\\.png"),
                    "Review filename is not filesystem-stable: " + shot.filename());
        }

        verifyMasterCoverage(shots);
        verifyBankCoverage(shots);
        verifyDoodadDetailCoverage(shots);
        EnumSet<StructureGalleryReviewPlan.Doodad> doodads = EnumSet.noneOf(
                StructureGalleryReviewPlan.Doodad.class);
        shots.stream()
                .filter(shot -> shot.coverage()
                        == StructureGalleryReviewPlan.Coverage.MASTER_GEOMETRY)
                .filter(shot -> !shot.view().isInterior())
                .forEach(shot -> doodads.addAll(shot.doodads()));
        require(doodads.equals(EnumSet.allOf(StructureGalleryReviewPlan.Doodad.class)),
                "Front/rear master frames do not explicitly cover all 16 doodad archetypes: "
                        + doodads);

        List<StructureGalleryReviewPlan.Shot> cohesion = shots.stream()
                .filter(shot -> shot.coverage()
                        == StructureGalleryReviewPlan.Coverage.BIOME_COHESION)
                .toList();
        require(cohesion.size() == StructureGalleryReviewPlan.BIOME_COHESION_COUNT,
                "Biome-cohesion matrix is incomplete");
        require(cohesion.stream().allMatch(
                        shot -> shot.view() == StructureGalleryReviewPlan.View.FRONT),
                "Biome-cohesion sampling must remain front-view only");

        Map<String, Set<String>> dialectsByRepresentative = new HashMap<>();
        for (StructureGalleryReviewPlan.Shot shot : cohesion) {
            dialectsByRepresentative.computeIfAbsent(shot.subject(), ignored -> new HashSet<>())
                    .add(shot.dialect());
        }
        require(dialectsByRepresentative.size()
                        == VillageProsperityEngine.ProjectType.values().length,
                "Biome matrix no longer has one representative for every role");
        int expectedDialects = VillageArchitecture.BiomeDialect.values().length;
        require(dialectsByRepresentative.values().stream()
                        .allMatch(dialects -> dialects.size() == expectedDialects),
                "A role representative no longer covers all biome dialects");

        System.out.println("PASS structure gallery review plan regression");
    }

    private static void verifyMasterCoverage(List<StructureGalleryReviewPlan.Shot> shots) {
        require(StructureGalleryReviewPlan.MULTI_ZONE_MASTER_COUNT > 0,
                "Large/landmark masters lost their second-interior review class");
        Map<Integer, EnumSet<StructureGalleryReviewPlan.View>> viewsByGalleryIndex =
                new HashMap<>();
        for (StructureGalleryReviewPlan.Shot shot : shots) {
            if (shot.coverage()
                    == StructureGalleryReviewPlan.Coverage.MASTER_GEOMETRY) {
                viewsByGalleryIndex.computeIfAbsent(
                                shot.galleryIndex(),
                                ignored -> EnumSet.noneOf(StructureGalleryReviewPlan.View.class))
                        .add(shot.view());
            }
        }
        require(viewsByGalleryIndex.size() == StructureGalleryReviewPlan.MASTER_GEOMETRY_COUNT,
                "Master subject count changed");
        for (Map.Entry<Integer, EnumSet<StructureGalleryReviewPlan.View>> review
                : viewsByGalleryIndex.entrySet()) {
            StructureGalleryPlan.Entry galleryEntry = StructureGalleryPlan.entries().get(
                    review.getKey() - 1);
            VillageArchitecture.BlueprintScale scale = VillageArchitecture.requireBlueprint(
                    galleryEntry.templateId(), galleryEntry.templateRevision()).scale();
            boolean multiZone = scale == VillageArchitecture.BlueprintScale.LARGE
                    || scale == VillageArchitecture.BlueprintScale.LANDMARK;
            EnumSet<StructureGalleryReviewPlan.View> expected = EnumSet.of(
                    StructureGalleryReviewPlan.View.FRONT,
                    StructureGalleryReviewPlan.View.REAR_DOODADS,
                    StructureGalleryReviewPlan.View.INTERIOR_PRIMARY);
            if (multiZone) {
                expected.add(StructureGalleryReviewPlan.View.INTERIOR_SECONDARY);
            }
            require(review.getValue().equals(expected),
                    "Wrong master views for " + galleryEntry.templateId() + ": "
                            + review.getValue());
        }
    }

    private static void verifyBankCoverage(List<StructureGalleryReviewPlan.Shot> shots) {
        Map<Integer, EnumSet<StructureGalleryReviewPlan.View>> viewsByGalleryIndex =
                new HashMap<>();
        for (StructureGalleryReviewPlan.Shot shot : shots) {
            if (shot.coverage() == StructureGalleryReviewPlan.Coverage.BANK_DIALECT) {
                viewsByGalleryIndex.computeIfAbsent(
                                shot.galleryIndex(),
                                ignored -> EnumSet.noneOf(StructureGalleryReviewPlan.View.class))
                        .add(shot.view());
            }
        }
        require(viewsByGalleryIndex.size() == StructureGalleryReviewPlan.BANK_DIALECT_COUNT,
                "Bank subject count changed");
        EnumSet<StructureGalleryReviewPlan.View> expected = EnumSet.of(
                StructureGalleryReviewPlan.View.FRONT,
                StructureGalleryReviewPlan.View.REAR_DOODADS,
                StructureGalleryReviewPlan.View.INTERIOR_PRIMARY,
                StructureGalleryReviewPlan.View.INTERIOR_SECONDARY);
        require(viewsByGalleryIndex.values().stream()
                        .allMatch(views -> views.equals(expected)),
                "Banks no longer have front, rear/doodad, and two interior views");
    }

    private static void verifyDoodadDetailCoverage(
            List<StructureGalleryReviewPlan.Shot> shots) {
        List<StructureGalleryReviewPlan.Shot> details = shots.stream()
                .filter(shot -> shot.coverage()
                        == StructureGalleryReviewPlan.Coverage.DOODAD_DETAIL)
                .toList();
        require(details.size() == StructureGalleryReviewPlan.DOODAD_DETAIL_COUNT,
                "Doodad close-shot count changed");
        EnumSet<StructureGalleryReviewPlan.Doodad> covered = EnumSet.noneOf(
                StructureGalleryReviewPlan.Doodad.class);
        for (StructureGalleryReviewPlan.Shot detail : details) {
            require(detail.view() == StructureGalleryReviewPlan.View.DOODAD_DETAIL,
                    "Doodad coverage is not an independent close shot");
            require(detail.doodadDetail() != null,
                    "Doodad close shot lacks focus metadata");
            require(detail.doodads().size() == 1,
                    "Doodad close shot is not independently scoreable");
            require(covered.add(detail.doodads().getFirst()),
                    "Doodad archetype receives duplicate close shots");
            boolean hostExists = shots.stream()
                    .anyMatch(host -> host.coverage()
                            == StructureGalleryReviewPlan.Coverage.MASTER_GEOMETRY
                            && host.galleryIndex() == detail.galleryIndex()
                            && host.view() == detail.doodadDetail().sourceView()
                            && host.doodads().contains(detail.doodadDetail().doodad()));
            require(hostExists,
                    "Doodad close shot no longer points at a matching frozen master scene");
        }
        require(covered.equals(EnumSet.allOf(StructureGalleryReviewPlan.Doodad.class)),
                "Doodad close shots omit an independently scored archetype: " + covered);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
