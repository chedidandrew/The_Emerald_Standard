package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Scale gating, weighted variety, determinism, and old-selection compatibility coverage. */
public final class BlueprintTierSelectionRegressionTest {
    private static final int ACTIVE_MASTER_COUNT = 52;

    private BlueprintTierSelectionRegressionTest() {
    }

    public static void main(String[] args) {
        testCatalogScaleCoverage();
        testTierEligibility();
        testEveryMasterIsReachableAtItsScaleUnlock();
        testTierPreferencesAndDeterminism();
        testImmediateRepeatAvoidance();
        testPersistedSelectionCompatibility();
        System.out.println("PASS blueprint tier-selection regressions");
    }

    private static void testCatalogScaleCoverage() {
        require(VillageArchitecture.activeBlueprints().size() == ACTIVE_MASTER_COUNT,
                "Expanded active catalog does not contain all 52 authored masters");

        Map<VillageArchitecture.BlueprintScale, Integer> counts =
                new EnumMap<>(VillageArchitecture.BlueprintScale.class);
        for (VillageArchitecture.BlueprintDescriptor descriptor
                : VillageArchitecture.activeBlueprints()) {
            counts.merge(descriptor.scale(), 1, Integer::sum);
        }
        require(counts.getOrDefault(VillageArchitecture.BlueprintScale.SMALL, 0) == 9
                        && counts.getOrDefault(VillageArchitecture.BlueprintScale.MEDIUM, 0) == 18
                        && counts.getOrDefault(VillageArchitecture.BlueprintScale.LARGE, 0) == 14
                        && counts.getOrDefault(
                                VillageArchitecture.BlueprintScale.LANDMARK, 0) == 11,
                "Expanded catalog lost its deliberate 9/18/14/11 scale distribution");

        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            List<VillageArchitecture.BlueprintDescriptor> descriptors =
                    VillageArchitecture.blueprints(type);
            int expectedRoleCount = switch (type) {
                case COTTAGE, HOUSE -> 6;
                default -> 5;
            };
            require(descriptors.size() == expectedRoleCount,
                    type + " did not receive exactly two additional authored choices");
            require(descriptors.stream().anyMatch(descriptor -> descriptor.scale()
                            == VillageArchitecture.BlueprintScale.SMALL)
                            || descriptors.stream().anyMatch(descriptor -> descriptor.scale()
                            == VillageArchitecture.BlueprintScale.MEDIUM),
                    type + " has no young-village-safe authored choice");
            require(descriptors.stream().anyMatch(descriptor -> descriptor.scale()
                            == VillageArchitecture.BlueprintScale.LARGE)
                            || descriptors.stream().anyMatch(descriptor -> descriptor.scale()
                            == VillageArchitecture.BlueprintScale.LANDMARK),
                    type + " has no developed-village authored choice");
        }
    }

    private static void testTierEligibility() {
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            List<VillageArchitecture.BlueprintDescriptor> tierZero =
                    VillageArchitecture.blueprints(type, 0);
            require(!tierZero.isEmpty(), type + " cannot be built by a tier-zero village");
            require(tierZero.stream().allMatch(descriptor -> descriptor.scale()
                            == VillageArchitecture.BlueprintScale.SMALL
                            || descriptor.scale() == VillageArchitecture.BlueprintScale.MEDIUM),
                    type + " exposes a large or landmark plan before its unlock tier");
            require(identities(tierZero).equals(identities(
                            VillageArchitecture.blueprints(type, 1))),
                    "Tier one unexpectedly changed the young-village catalog for " + type);
            require(VillageArchitecture.blueprints(type, 2).stream()
                            .noneMatch(descriptor -> descriptor.scale()
                                    == VillageArchitecture.BlueprintScale.LANDMARK),
                    "Tier two exposed a landmark plan for " + type);
            require(identities(VillageArchitecture.blueprints(type, 4))
                            .equals(identities(VillageArchitecture.blueprints(type))),
                    "Tier four did not unlock the complete active catalog for " + type);
            require(identities(VillageArchitecture.blueprints(type, -100))
                            .equals(identities(tierZero))
                            && identities(VillageArchitecture.blueprints(type, 100))
                                    .equals(identities(VillageArchitecture.blueprints(type, 5))),
                    "Out-of-range development tiers were not safely normalized");
        }
    }

    private static void testTierPreferencesAndDeterminism() {
        Map<VillageArchitecture.BlueprintScale, Integer> young = selectionsByScale(
                VillageProsperityEngine.ProjectType.COTTAGE, 0, 8_192);
        require(young.getOrDefault(VillageArchitecture.BlueprintScale.SMALL, 0)
                        > young.getOrDefault(VillageArchitecture.BlueprintScale.MEDIUM, 0),
                "Young villages no longer favor compact cottage plans");
        require(young.getOrDefault(VillageArchitecture.BlueprintScale.LARGE, 0) == 0
                        && young.getOrDefault(
                                VillageArchitecture.BlueprintScale.LANDMARK, 0) == 0,
                "Young-village selection escaped its eligible catalog");

        Map<VillageArchitecture.BlueprintScale, Integer> developed = selectionsByScale(
                VillageProsperityEngine.ProjectType.COTTAGE, 5, 8_192);
        require(developed.getOrDefault(VillageArchitecture.BlueprintScale.LARGE, 0)
                        > developed.getOrDefault(VillageArchitecture.BlueprintScale.MEDIUM, 0)
                        && developed.getOrDefault(VillageArchitecture.BlueprintScale.LARGE, 0)
                                > developed.getOrDefault(
                                        VillageArchitecture.BlueprintScale.SMALL, 0),
                "Developed villages no longer favor grand cottage plans");

        Map<VillageArchitecture.BlueprintScale, Integer> cityExchange = selectionsByScale(
                VillageProsperityEngine.ProjectType.EXCHANGE_HALL, 5, 8_192);
        require(cityExchange.getOrDefault(VillageArchitecture.BlueprintScale.LANDMARK, 0)
                        > cityExchange.getOrDefault(
                                VillageArchitecture.BlueprintScale.MEDIUM, 0),
                "City selection does not meaningfully favor landmark Exchange Halls");

        UUID villageId = UUID.fromString("507b283c-bb7a-4606-9ce2-5961d4774806");
        VillageArchitecture.BlueprintSelection explicitTierZero =
                VillageArchitecture.chooseBlueprint(
                        villageId,
                        17L,
                        VillageProsperityEngine.ProjectType.HOUSE,
                        VillageArchitecture.Character.RUSTIC,
                        0,
                        List.of());
        require(explicitTierZero.equals(VillageArchitecture.chooseBlueprint(
                        villageId,
                        17L,
                        VillageProsperityEngine.ProjectType.HOUSE,
                        VillageArchitecture.Character.RUSTIC,
                        List.of())),
                "Backward-compatible chooser no longer delegates to stable tier-zero policy");
        require(explicitTierZero.equals(VillageArchitecture.chooseBlueprint(
                        villageId,
                        17L,
                        VillageProsperityEngine.ProjectType.HOUSE,
                        VillageArchitecture.Character.RUSTIC,
                        0,
                        List.of())),
                "Identical tier-aware selection rerolled");
    }

    private static void testEveryMasterIsReachableAtItsScaleUnlock() {
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            List<VillageArchitecture.BlueprintDescriptor> role =
                    VillageArchitecture.blueprints(type);
            for (VillageArchitecture.BlueprintScale scale
                    : VillageArchitecture.BlueprintScale.values()) {
                Set<String> expected = role.stream()
                        .filter(descriptor -> descriptor.scale() == scale)
                        .map(BlueprintTierSelectionRegressionTest::identity)
                        .collect(java.util.stream.Collectors.toSet());
                if (expected.isEmpty()) {
                    continue;
                }
                Set<String> reached = new HashSet<>();
                int tier = scale.minimumDevelopmentTier();
                for (int sample = 0; sample < 4_096; sample++) {
                    UUID villageId = new UUID(
                            0xBB67AE8584CAA73BL ^ type.ordinal(),
                            VillageArchitecture.mix64(
                                    sample * 0xD1B54A32D192ED03L ^ scale.ordinal()));
                    VillageArchitecture.BlueprintSelection selection =
                            VillageArchitecture.chooseBlueprint(
                                    villageId,
                                    1L,
                                    type,
                                    VillageArchitecture.Character.FORMAL,
                                    tier,
                                    List.of());
                    reached.add(identity(selection.descriptor()));
                }
                require(reached.containsAll(expected),
                        type + " has unreachable " + scale + " masters at tier " + tier
                                + ": " + difference(expected, reached));
            }
        }
    }

    private static void testImmediateRepeatAvoidance() {
        UUID villageId = UUID.fromString("ed55ae2b-b8fc-4828-8fea-bd24d8f2924b");
        List<VillageArchitecture.ExistingBlueprint> existing = new ArrayList<>();
        String previousTemplate = "";
        for (long projectId = 1L; projectId <= 64L; projectId++) {
            VillageArchitecture.BlueprintSelection selection =
                    VillageArchitecture.chooseBlueprint(
                            villageId,
                            projectId,
                            VillageProsperityEngine.ProjectType.COTTAGE,
                            VillageArchitecture.Character.AGRARIAN,
                            5,
                            existing);
            require(!selection.templateId().equals(previousTemplate),
                    "Tier-weighted catalog immediately repeated an eligible Cottage master");
            previousTemplate = selection.templateId();
            existing.add(new VillageArchitecture.ExistingBlueprint(
                    projectId,
                    VillageProsperityEngine.ProjectType.COTTAGE,
                    selection.templateId(),
                    selection.templateRevision(),
                    selection.paletteId(),
                    selection.dressingId(),
                    selection.mirrored(),
                    selection.signature()));
        }
    }

    private static void testPersistedSelectionCompatibility() {
        VillageArchitecture.BlueprintDescriptor retired =
                VillageArchitecture.requireBlueprint("mine_headframe_01", 1);
        require(retired.templateRevision() == 1,
                "Retired persisted blueprint revision is no longer resolvable");

        String templateId = "exchange_countinghouse_02";
        long signature = VillageArchitecture.blueprintSignature(
                VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                templateId,
                2,
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_PROSPEROUS,
                true);
        require(VillageArchitecture.isKnownBlueprintSelection(
                        VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                        templateId,
                        2,
                        VillageArchitecture.PALETTE_BALANCED,
                        VillageArchitecture.DRESSING_PROSPEROUS,
                        true,
                        signature),
                "A persisted landmark identity depends on the village's current tier");
    }

    private static Map<VillageArchitecture.BlueprintScale, Integer> selectionsByScale(
            VillageProsperityEngine.ProjectType type, int developmentTier, int samples) {
        Map<VillageArchitecture.BlueprintScale, Integer> counts =
                new EnumMap<>(VillageArchitecture.BlueprintScale.class);
        for (int sample = 0; sample < samples; sample++) {
            UUID villageId = new UUID(
                    0x6A09E667F3BCC909L,
                    VillageArchitecture.mix64(sample * 0x9E3779B97F4A7C15L));
            VillageArchitecture.BlueprintSelection selection =
                    VillageArchitecture.chooseBlueprint(
                            villageId,
                            1L,
                            type,
                            VillageArchitecture.Character.MERCANTILE,
                            developmentTier,
                            List.of());
            VillageArchitecture.BlueprintScale scale = selection.descriptor().scale();
            require(scale.isAvailableAt(developmentTier),
                    "Tier-aware chooser selected a locked " + scale + " master");
            counts.merge(scale, 1, Integer::sum);
        }
        return counts;
    }

    private static List<String> identities(
            List<VillageArchitecture.BlueprintDescriptor> descriptors) {
        return descriptors.stream()
                .map(BlueprintTierSelectionRegressionTest::identity)
                .toList();
    }

    private static String identity(VillageArchitecture.BlueprintDescriptor descriptor) {
        return descriptor.templateId() + "@" + descriptor.templateRevision();
    }

    private static Set<String> difference(Set<String> expected, Set<String> reached) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(reached);
        return missing;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
