package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BlueprintScale;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailCell;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailPhase;
import com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidator.CatalogReport;
import com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidator.PresentationProfile;
import com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidator.PresentationSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidator.Requirements;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression coverage for scale-aware facade, roof, and site presentation admission. */
public final class WholeBuildingPresentationValidatorRegressionTest {
    private WholeBuildingPresentationValidatorRegressionTest() {
    }

    public static void main(String[] args) {
        testEveryScaleHasAValidPresentation();
        testSmallMasterMayRemainRestrained();
        testRequirementsRiseWithVillageImportance();
        testFlushFacadeFailsDespiteRichRoofAndYard();
        testShellConnectedInteriorBeamsDoNotCountAsFacade();
        testRecessedExteriorFrontStillCountsAsFacadeDepth();
        testProjectedOpeningFrameCountsAsFacadeDepth();
        testGroundedOpenFrameCountsAsFacadeDepth();
        testGroundedSidePoleDoesNotInflateEnclosedFacade();
        testExteriorFrameTendrilCannotMasqueradeAsFacade();
        testRemoteOpenFrameTendrilCannotMasqueradeAsFacade();
        testSingleGableFailsLargeRoofPunctuation();
        testSameRoofFeatureCannotCountAsPeakAndAccent();
        testBroadRoofPlateauCannotInflatePunctuationCredit();
        testClusteredRoofPlateausFailRegionalDistribution();
        testContinuousEaveTrimCannotBridgeDistinctRoofMasses();
        testFloatingRoofAccentCannotCountAsPunctuation();
        testInteriorClutterCannotMasqueradeAsSiteContext();
        testFloatingStageDetailsCannotMasqueradeAsGroundedSite();
        testDistantScatterCannotMasqueradeAsSiteContext();
        testLandmarkRequiresAComposedSiteOnMultipleSides();
        testCatalogOrderingIdentityAndFailureMessage();
        testSnapshotGuards();
        System.out.println("PASS whole-building presentation validator regressions");
    }

    private static void testEveryScaleHasAValidPresentation() {
        for (BlueprintScale scale : BlueprintScale.values()) {
            Requirements required = WholeBuildingPresentationValidator.requirements(scale);
            PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                    authoredSnapshot(
                            "valid_" + scale.name().toLowerCase(),
                            scale,
                            required.facadeProjectionColumns(),
                            required.roofPunctuationFeatures(),
                            required.siteDetailCells(),
                            required.siteSides()));
            require(profile.presentable(),
                    scale + " authored presentation failed: " + profile.failures());
        }
    }

    private static void testSmallMasterMayRemainRestrained() {
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                authoredSnapshot("restrained_cottage", BlueprintScale.SMALL, 2, 1, 3, 2));
        require(profile.presentable(),
                "A modest but composed small cottage was forced to landmark density: "
                        + profile.failures());
        require(profile.roofPunctuationFeatures() == 1
                        && profile.siteDetailCells() == 3,
                "Restrained fixture no longer proves the deliberately low small-building floor");
    }

    private static void testRequirementsRiseWithVillageImportance() {
        Requirements small = WholeBuildingPresentationValidator.requirements(BlueprintScale.SMALL);
        Requirements medium = WholeBuildingPresentationValidator.requirements(BlueprintScale.MEDIUM);
        Requirements large = WholeBuildingPresentationValidator.requirements(BlueprintScale.LARGE);
        Requirements landmark = WholeBuildingPresentationValidator.requirements(
                BlueprintScale.LANDMARK);

        require(strictlyRises(
                        small.facadeProjectionColumns(), medium.facadeProjectionColumns(),
                        large.facadeProjectionColumns(), landmark.facadeProjectionColumns()),
                "Facade-depth budget no longer rises from small to landmark");
        require(strictlyRises(
                        small.facadeArticulationColumns(), medium.facadeArticulationColumns(),
                        large.facadeArticulationColumns(), landmark.facadeArticulationColumns()),
                "Facade-articulation budget no longer rises from small to landmark");
        require(strictlyRises(
                        small.roofPunctuationFeatures(), medium.roofPunctuationFeatures(),
                        large.roofPunctuationFeatures(), landmark.roofPunctuationFeatures()),
                "Roof-punctuation budget no longer rises from small to landmark");
        require(strictlyRises(
                        small.roofPunctuationRegions(), medium.roofPunctuationRegions(),
                        large.roofPunctuationRegions(), landmark.roofPunctuationRegions()),
                "Roof-distribution budget no longer rises from small to landmark");
        require(strictlyRises(
                        small.siteDetailCells(), medium.siteDetailCells(),
                        large.siteDetailCells(), landmark.siteDetailCells()),
                "Contextual-site budget no longer rises from small to landmark");
    }

    private static void testFlushFacadeFailsDespiteRichRoofAndYard() {
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                authoredSnapshot("flush_large_hall", BlueprintScale.LARGE, 0, 4, 14, 3));
        require(!profile.presentable()
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("facade depth")),
                "A flush large shell passed because its roof and yard were richly decorated");
    }

    private static void testShellConnectedInteriorBeamsDoNotCountAsFacade() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        for (int x = 1; x < width - 1; x++) {
            base.put(x, 2, 3, DetailPhase.FRAME);
        }
        addRoofPunctuation(base, width, depth, 4);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 14, 3);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "sealed_interior_beams", BlueprintScale.LARGE,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns() == 0
                        && profile.facadeArticulationColumns() == 0,
                "Shell-connected beams inside a sealed room were credited as visible facade");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("facade depth")),
                "Interior beam clutter bypassed the exterior facade gate");
    }

    private static void testRecessedExteriorFrontStillCountsAsFacadeDepth() {
        int width = 9;
        int depth = 9;
        Layer base = baseShell(width, depth);
        for (int x = 1; x <= width - 2; x++) {
            base.remove(x, 2, 0);
            base.put(x, 2, 2, DetailPhase.SHELL);
        }
        for (int x : new int[] {1, 2, width - 3, width - 2}) {
            base.put(x, 2, 1, DetailPhase.FRAME);
            base.put(x, 3, 1, DetailPhase.FRAME);
        }
        base.put(width / 2, 2, depth - 1, DetailPhase.OPENING);
        base.put(0, 2, depth / 2, DetailPhase.OPENING);
        base.put(width - 1, 2, depth / 2, DetailPhase.OPENING);
        addRoofPunctuation(base, width, depth, 1);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 3, 2);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "recessed_open_front", BlueprintScale.SMALL,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns()
                        >= profile.requirements().facadeProjectionColumns(),
                "Exterior-air reachability erased legitimate recessed facade depth");
        require(profile.presentable(),
                "A restrained building with a visible recessed front failed: "
                        + profile.failures());
    }

    private static void testExteriorFrameTendrilCannotMasqueradeAsFacade() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        for (int x = 0; x < width; x++) {
            base.put(x, 2, -2, DetailPhase.FRAME);
        }
        for (int x = 1; x < width - 1; x += 3) {
            base.put(x, 2, -1, DetailPhase.FRAME);
        }
        addRoofPunctuation(base, width, depth, 4);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 14, 3);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "exterior_frame_tendril", BlueprintScale.LARGE,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns()
                        < profile.requirements().facadeProjectionColumns(),
                "A one-layer exterior frame network was credited as composed facade depth");
        require(!profile.presentable(),
                "An attached beam/eave tendril replaced real facade composition");
    }

    private static void testProjectedOpeningFrameCountsAsFacadeDepth() {
        int width = 9;
        int depth = 9;
        Layer base = baseShell(width, depth);
        for (int x : new int[] {1, width - 2}) {
            base.put(x, 2, 0, DetailPhase.OPENING);
            base.put(x, 3, 0, DetailPhase.OPENING);
            base.put(x, 2, -1, DetailPhase.FRAME);
            base.put(x, 3, -1, DetailPhase.FRAME);
        }
        base.put(width / 2, 2, depth - 1, DetailPhase.OPENING);
        base.put(0, 2, depth / 2, DetailPhase.OPENING);
        base.put(width - 1, 2, depth / 2, DetailPhase.OPENING);
        addRoofPunctuation(base, width, depth, 1);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 3, 2);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "projected_opening_frames", BlueprintScale.SMALL,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns()
                        >= profile.requirements().facadeProjectionColumns(),
                "Window frames backed by in-footprint openings were not credited as facade depth");
        require(profile.presentable(),
                "A supported projected window surround failed presentation: "
                        + profile.failures());
    }

    private static void testGroundedOpenFrameCountsAsFacadeDepth() {
        int width = 9;
        int depth = 9;
        Layer base = new Layer();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                base.put(x, 0, z, DetailPhase.FOUNDATION);
                base.put(x, 5, z, DetailPhase.ROOF);
            }
        }
        for (int[] post : new int[][] {
                {1, 0}, {width - 2, 0}, {0, depth - 2}, {width - 1, depth - 2}
        }) {
            for (int y = 1; y <= 4; y++) {
                base.put(post[0], y, post[1], DetailPhase.FRAME);
            }
        }
        addRoofPunctuation(base, width, depth, 1);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 3, 2);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "grounded_open_frame", BlueprintScale.SMALL,
                        width, depth, 14, false,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns()
                        >= profile.requirements().facadeProjectionColumns(),
                "Grounded local open-frame posts were not credited as facade depth");
        require(profile.presentable(),
                "A composed post-and-beam structure failed presentation: "
                        + profile.failures());
    }

    private static void testRemoteOpenFrameTendrilCannotMasqueradeAsFacade() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        for (int x = 0; x < width; x++) {
            base.put(x, 2, -3, DetailPhase.FRAME);
        }
        addRoofPunctuation(base, width, depth, 4);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 14, 3);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "remote_open_frame_tendril", BlueprintScale.LARGE,
                        width, depth, 14, false,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns() == 0,
                "A remote one-layer open-frame ribbon was credited as grounded facade depth");
        require(!profile.presentable(),
                "A remote unsupported frame ribbon replaced composed open-frame architecture");
    }

    private static void testGroundedSidePoleDoesNotInflateEnclosedFacade() {
        int width = 9;
        int depth = 9;
        Layer base = baseShell(width, depth);
        for (int z : new int[] {-1, 0}) {
            base.put(-1, 0, z, DetailPhase.FOUNDATION);
            for (int y = 1; y <= 4; y++) {
                base.put(-1, y, z, DetailPhase.FRAME);
            }
        }
        addRoofPunctuation(base, width, depth, 1);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 3, 2);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "enclosed_grounded_side_pole", BlueprintScale.SMALL,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.facadeProjectionColumns() == 1,
                "A grounded but unbacked side pole inflated an enclosed facade");
        require(!profile.presentable(),
                "Grounded open-frame logic let a flat enclosed shell pass presentation");
    }

    private static void testSingleGableFailsLargeRoofPunctuation() {
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                authoredSnapshot("one_note_large_roof", BlueprintScale.LARGE, 8, 1, 14, 3));
        require(profile.facadeProjectionColumns()
                        >= profile.requirements().facadeProjectionColumns()
                        && profile.siteDetailCells() >= profile.requirements().siteDetailCells(),
                "Roof rejection fixture no longer isolates the roof requirement");
        require(!profile.presentable()
                        && profile.failures().stream()
                                .anyMatch(failure -> failure.contains("roof punctuation")),
                "One uninterrupted roof feature passed the large-building skyline gate");
    }

    private static void testSameRoofFeatureCannotCountAsPeakAndAccent() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        addFacade(base, width, depth, 6);
        base.put(3, 6, 3, DetailPhase.ROOF);
        base.put(4, 6, 3, DetailPhase.ROOF);
        base.put(3, 7, 3, DetailPhase.FRAME);
        base.put(3, 8, 3, DetailPhase.FRAME);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 5, 2);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "one_dormer_two_signals", BlueprintScale.MEDIUM,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.roofPeakFeatures() == 1 && profile.roofAccentFeatures() == 1,
                "Double-count fixture no longer exposes both raw roof signals");
        require(profile.roofPunctuationFeatures() == 1,
                "One physical dormer/chimney counted separately as peak and accent");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("roof punctuation")),
                "One physical roof feature bypassed the medium-building skyline gate");
    }

    private static void testBroadRoofPlateauCannotInflatePunctuationCredit() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        addFacade(base, width, depth, 8);
        for (int x = 6; x <= 13; x++) {
            for (int z = 5; z <= 10; z++) {
                base.put(x, 6, z, DetailPhase.ROOF);
            }
        }
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 14, 3);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "one_broad_roof_plateau", BlueprintScale.LARGE,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.roofPunctuationColumns() >= 40,
                "Broad plateau fixture no longer exceeds the former raw-column threshold");
        require(profile.roofPunctuationFeatures() == 1
                        && profile.roofPunctuationRegions() == 1,
                "One broad plateau manufactured several physical or regional roof features");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("roof punctuation")),
                "A broad plateau bypassed roof admission by inflating its footprint columns");
    }

    private static void testClusteredRoofPlateausFailRegionalDistribution() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        addFacade(base, width, depth, 8);
        for (int[] anchor : new int[][] {{1, 1}, {5, 1}, {1, 4}}) {
            base.put(anchor[0], 6, anchor[1], DetailPhase.ROOF);
            base.put(anchor[0] + 1, 6, anchor[1], DetailPhase.ROOF);
        }
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 14, 3);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "clustered_roof_plateaus", BlueprintScale.LARGE,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.roofPunctuationFeatures() == 3
                        && profile.roofPunctuationColumns() >= 5,
                "Clustered plateau fixture no longer meets the old count-based gate");
        require(profile.roofPunctuationRegions() == 1,
                "Nearby plateaus manufactured distributed skyline credit");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("anchor regions")),
                "Clustered roof plateaus bypassed the scale-aware regional gate");
    }

    private static void testContinuousEaveTrimCannotBridgeDistinctRoofMasses() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        addFacade(base, width, depth, 8);
        for (int x : new int[] {2, width / 2, width - 3}) {
            base.put(x, 6, 0, DetailPhase.ROOF);
        }
        for (int x = 0; x < width; x++) {
            base.put(x, 5, -1, DetailPhase.FRAME);
        }
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 14, 3);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "three_gables_with_one_eave", BlueprintScale.LARGE,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.roofAccentFeatures() == 0,
                "Continuous eave trim was treated as roofline punctuation");
        require(profile.roofPunctuationFeatures() == 3
                        && profile.roofPunctuationRegions() == 3,
                "A broad eave network merged three genuinely distinct roof masses");
        require(profile.presentable(),
                "Distributed roof masses with ordinary eave trim failed: " + profile.failures());
    }

    private static void testFloatingRoofAccentCannotCountAsPunctuation() {
        int width = 21;
        int depth = 17;
        Layer base = baseShell(width, depth);
        addFacade(base, width, depth, 6);
        base.put(4, 6, 4, DetailPhase.ROOF);
        base.put(4, 8, 4, DetailPhase.FRAME);
        base.put(4, 9, 4, DetailPhase.FRAME);
        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, 5, 2);

        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "floating_roof_accent", BlueprintScale.MEDIUM,
                        width, depth, 14, true,
                        base.cells(), stageOne.cells(), stageTwo.cells()));
        require(profile.roofAccentFeatures() == 0,
                "A roof accent separated by a full air block received physical punctuation credit");

        Layer attached = baseShell(width, depth);
        addFacade(attached, width, depth, 6);
        attached.put(4, 6, 4, DetailPhase.ROOF);
        attached.put(4, 7, 4, DetailPhase.FRAME);
        attached.put(4, 8, 4, DetailPhase.FRAME);
        PresentationProfile attachedProfile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        "attached_roof_accent", BlueprintScale.MEDIUM,
                        width, depth, 14, true,
                        attached.cells(), stageOne.cells(), stageTwo.cells()));
        require(attachedProfile.roofAccentFeatures() == 1,
                "A face-attached roof accent stopped receiving physical punctuation credit");
    }

    private static void testInteriorClutterCannotMasqueradeAsSiteContext() {
        PresentationSnapshot source = authoredSnapshot(
                "interior_clutter", BlueprintScale.LARGE, 8, 4, 14, 3);
        List<DetailCell> first = List.of(
                cell(4, 1, 4, DetailPhase.DECOR),
                cell(5, 1, 4, DetailPhase.FIXTURE),
                cell(6, 1, 4, DetailPhase.DECOR));
        List<DetailCell> second = List.of(
                cell(4, 1, 6, DetailPhase.DECOR),
                cell(5, 1, 6, DetailPhase.FIXTURE),
                cell(6, 1, 6, DetailPhase.DECOR));
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        source.id(), source.scale(), source.width(), source.depth(), source.height(),
                        source.enclosed(), source.baseCells(), first, second));
        require(profile.siteDetailCells() == 0,
                "Interior furniture was counted as exterior contextual dressing");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("contextual site dressing")),
                "Interior clutter substituted for a composed exterior site");
    }

    private static void testFloatingStageDetailsCannotMasqueradeAsGroundedSite() {
        PresentationSnapshot source = authoredSnapshot(
                "floating_stage_detail", BlueprintScale.MEDIUM, 6, 3, 5, 2);
        List<DetailCell> first = List.of(
                cell(-3, 1, 1, DetailPhase.FOUNDATION),
                cell(-3, 2, 1, DetailPhase.FRAME),
                cell(-3, 3, 1, DetailPhase.DECOR));
        List<DetailCell> second = List.of(
                cell(1, 2, source.depth() + 2, DetailPhase.FRAME),
                cell(1, 3, source.depth() + 2, DetailPhase.DECOR));
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        source.id(), source.scale(), source.width(), source.depth(), source.height(),
                        source.enclosed(), source.baseCells(), first, second));
        require(profile.siteDetailCells() == 0
                        && profile.stageOneSiteDetailCells() == 0
                        && profile.stageTwoSiteDetailCells() == 0,
                "Floating stage props were credited without a ground-level foundation anchor");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("contextual site dressing")),
                "Floating props bypassed the grounded site-composition gate");
    }

    private static void testLandmarkRequiresAComposedSiteOnMultipleSides() {
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                authoredSnapshot("isolated_landmark", BlueprintScale.LANDMARK, 12, 5, 3, 2));
        require(profile.facadeProjectionColumns()
                        >= profile.requirements().facadeProjectionColumns()
                        && profile.roofPunctuationFeatures()
                                >= profile.requirements().roofPunctuationFeatures(),
                "Landmark site rejection fixture no longer isolates contextual composition");
        require(!profile.presentable()
                        && profile.failures().stream().anyMatch(
                                failure -> failure.contains("contextual site dressing")),
                "A monumental building with a token yard prop passed presentation admission");
    }

    private static void testDistantScatterCannotMasqueradeAsSiteContext() {
        PresentationSnapshot source = authoredSnapshot(
                "distant_scatter", BlueprintScale.MEDIUM, 6, 3, 5, 2);
        List<DetailCell> first = List.of(
                cell(-40, 0, 1, DetailPhase.FOUNDATION),
                cell(-40, 1, 1, DetailPhase.DECOR),
                cell(-40, 0, 2, DetailPhase.FOUNDATION),
                cell(-40, 1, 2, DetailPhase.DECOR),
                cell(-40, 0, 3, DetailPhase.FOUNDATION),
                cell(-40, 1, 3, DetailPhase.DECOR));
        List<DetailCell> second = List.of(
                cell(1, 0, source.depth() + 40, DetailPhase.FOUNDATION),
                cell(1, 1, source.depth() + 40, DetailPhase.DECOR),
                cell(2, 0, source.depth() + 40, DetailPhase.FOUNDATION),
                cell(2, 1, source.depth() + 40, DetailPhase.DECOR));
        PresentationProfile profile = WholeBuildingPresentationValidator.profile(
                new PresentationSnapshot(
                        source.id(), source.scale(), source.width(), source.depth(), source.height(),
                        source.enclosed(), source.baseCells(), first, second));
        require(profile.siteDetailCells() == 0 && !profile.presentable(),
                "Distant scattered props were credited as contextual building presentation");
    }

    private static void testCatalogOrderingIdentityAndFailureMessage() {
        PresentationSnapshot good = authoredSnapshot(
                "z_good", BlueprintScale.SMALL, 2, 1, 3, 2);
        PresentationSnapshot bad = authoredSnapshot(
                "a_plain", BlueprintScale.LANDMARK, 0, 1, 3, 2);
        CatalogReport report = WholeBuildingPresentationValidator.validateSnapshots(
                List.of(good, bad));
        require(report.profiles().getFirst().id().equals("a_plain")
                        && report.profiles().getLast().id().equals("z_good"),
                "Presentation catalog report is not stable-id ordered");
        require(report.insufficientProfiles().size() == 1
                        && report.insufficientProfiles().getFirst().id().equals("a_plain"),
                "Presentation report did not isolate the weak master");
        expectIllegal(report::requirePresentable, "facade projected columns/regions");
        expectIllegal(
                () -> WholeBuildingPresentationValidator.validateSnapshots(List.of(good, good)),
                "Duplicate presentation snapshot id");
    }

    private static void testSnapshotGuards() {
        List<DetailCell> oneCell = List.of(cell(0, 0, 0, DetailPhase.FOUNDATION));
        expectIllegal(() -> new PresentationSnapshot(
                " ", BlueprintScale.SMALL, 1, 1, 1, true,
                oneCell, List.of(), List.of()), "must not be blank");
        expectIllegal(() -> new PresentationSnapshot(
                "bad_size", BlueprintScale.SMALL, 0, 1, 1, true,
                oneCell, List.of(), List.of()), "must be positive");
        expectIllegal(() -> new PresentationSnapshot(
                "empty", BlueprintScale.SMALL, 1, 1, 1, true,
                List.of(), List.of(), List.of()), "base must not be empty");
        expectIllegal(() -> new PresentationSnapshot(
                "overlap", BlueprintScale.SMALL, 1, 1, 1, true,
                oneCell, oneCell, List.of()), "Duplicate presentation cell");
        expectIllegal(() -> new PresentationSnapshot(
                "above_envelope", BlueprintScale.SMALL, 1, 1, 1, true,
                List.of(cell(0, 2, 0, DetailPhase.FOUNDATION)), List.of(), List.of()),
                "vertical envelope");
        expectIllegal(() -> new PresentationSnapshot(
                "below_envelope", BlueprintScale.SMALL, 1, 1, 1, true,
                List.of(cell(0, -1, 0, DetailPhase.FOUNDATION)), List.of(), List.of()),
                "vertical envelope");
    }

    private static PresentationSnapshot authoredSnapshot(
            String id,
            BlueprintScale scale,
            int facadeProjectionColumns,
            int roofFeatures,
            int siteDetailCells,
            int siteSides) {
        int width = scale == BlueprintScale.SMALL ? 9 : 21;
        int depth = scale == BlueprintScale.SMALL ? 9 : 17;
        Layer base = baseShell(width, depth);
        addFacade(base, width, depth, facadeProjectionColumns);
        addRoofPunctuation(base, width, depth, roofFeatures);

        Layer stageOne = new Layer();
        Layer stageTwo = new Layer();
        addSite(stageOne, stageTwo, width, depth, siteDetailCells, siteSides);
        return new PresentationSnapshot(
                id, scale, width, depth, 14, true,
                base.cells(), stageOne.cells(), stageTwo.cells());
    }

    private static Layer baseShell(int width, int depth) {
        Layer layer = new Layer();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                layer.put(x, 0, z, DetailPhase.FOUNDATION);
                layer.put(x, 5, z, DetailPhase.ROOF);
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 1; y <= 4; y++) {
                layer.put(x, y, 0, DetailPhase.SHELL);
                layer.put(x, y, depth - 1, DetailPhase.SHELL);
            }
        }
        for (int z = 1; z < depth - 1; z++) {
            for (int y = 1; y <= 4; y++) {
                layer.put(0, y, z, DetailPhase.SHELL);
                layer.put(width - 1, y, z, DetailPhase.SHELL);
            }
        }
        return layer;
    }

    private static void addFacade(
            Layer base, int width, int depth, int projectedColumnCount) {
        int[][] projected = {
                {-1, 1}, {-1, 2}, {-1, depth - 2}, {-1, depth - 3},
                {width, 1}, {width, 2}, {width, depth - 2}, {width, depth - 3},
                {1, -1}, {2, -1}, {width - 2, -1}, {width - 3, -1},
                {1, depth}, {2, depth}, {width - 2, depth}, {width - 3, depth}
        };
        for (int index = 0; index < projectedColumnCount; index++) {
            int[] column = projected[index];
            base.put(column[0], 2, column[1], DetailPhase.FRAME);
            base.put(column[0], 3, column[1], DetailPhase.FRAME);
        }

        for (int x : new int[] {1, width / 2, width - 2}) {
            base.put(x, 2, 0, DetailPhase.OPENING);
            base.put(x, 2, depth - 1, DetailPhase.OPENING);
        }
        for (int z : new int[] {depth / 2}) {
            base.put(0, 2, z, DetailPhase.OPENING);
            base.put(width - 1, 2, z, DetailPhase.OPENING);
        }
    }

    private static void addRoofPunctuation(
            Layer base, int width, int depth, int featureCount) {
        int[][] anchors = {
                {2, 2}, {width - 4, 2}, {2, depth - 3}, {width - 4, depth - 3},
                {width / 2 - 1, depth / 2}
        };
        for (int index = 0; index < featureCount; index++) {
            int[] anchor = anchors[index];
            base.put(anchor[0], 6, anchor[1], DetailPhase.ROOF);
            base.put(anchor[0] + 1, 6, anchor[1], DetailPhase.ROOF);
        }
    }

    private static void addSite(
            Layer first,
            Layer second,
            int width,
            int depth,
            int visualCellCount,
            int requestedSides) {
        int west = 0;
        int east = 0;
        int south = 0;
        for (int index = 0; index < visualCellCount; index++) {
            int selector = index % requestedSides;
            int x;
            int z;
            Layer layer;
            if (selector == 0) {
                x = -3;
                z = 1 + west++;
                layer = first;
            } else if (requestedSides >= 3 && selector == 1) {
                x = width + 2;
                z = 1 + east++;
                layer = first;
            } else {
                x = 1 + south++;
                z = depth + 2;
                layer = second;
            }
            layer.put(x, 0, z, DetailPhase.FOUNDATION);
            layer.put(x, 1, z,
                    index % 2 == 0 ? DetailPhase.DECOR : DetailPhase.FRAME);
        }
        // Even the most restrained snapshot exercises both append-only dressing stages.
        if (second.cells().isEmpty()) {
            int x = 1;
            second.put(x, 0, depth + 2, DetailPhase.FOUNDATION);
            second.put(x, 1, depth + 2, DetailPhase.DECOR);
        }
    }

    private static DetailCell cell(int x, int y, int z, DetailPhase phase) {
        return new DetailCell(new Voxel(x, y, z), phase);
    }

    private static boolean strictlyRises(int first, int second, int third, int fourth) {
        return first < second && second < third && third < fourth;
    }

    private static void expectIllegal(Runnable action, String fragment) {
        try {
            action.run();
            throw new AssertionError("Invalid presentation input was accepted");
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

    private static final class Layer {
        private final Map<Voxel, DetailPhase> cells = new LinkedHashMap<>();

        private void put(int x, int y, int z, DetailPhase phase) {
            cells.put(new Voxel(x, y, z), phase);
        }

        private void remove(int x, int y, int z) {
            cells.remove(new Voxel(x, y, z));
        }

        private Collection<DetailCell> cells() {
            return cells.entrySet().stream()
                    .map(entry -> new DetailCell(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }
}
