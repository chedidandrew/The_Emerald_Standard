package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.AccessTarget;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Bounds;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Cell;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Entrance;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.PaletteMaterial;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.PaletteSlot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Placement;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.ReservedClearance;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Role;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.ShapeCategory;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.ShellPolicy;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Stage;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.SupportExpectation;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.TargetKind;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.TraversalLink;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingValidator.Code;
import com.chedidandrew.emeraldstandard.core.WholeBuildingValidator.ValidationReport;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDistinctivenessValidator.CatalogReport;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDistinctivenessValidator.StructuralSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regression coverage for the authored-v2 whole-building admission gate. */
public final class WholeBuildingValidatorRegressionTest {
    private WholeBuildingValidatorRegressionTest() {
    }

    public static void main(String[] args) {
        testCoherentTwoLevelBlueprintPasses();
        testBlockedEntranceIsRejected();
        testBlockedBedAndWorkstationRoutesAreRejected();
        testWalkableInteriorCannotBeHiddenFromValidation();
        testRoofAndShellGapsAreRejected();
        testDisconnectedStructuralComponentIsRejected();
        testUpperFloorRequiresAuthoredTraversal();
        testStagesMustBeStrictlyAppendOnly();
        testPaletteSwapsMustPreserveShapeCategory();
        testOpenAirTemplatesDoNotRequireClosedWalls();
        testOnlyAuthoredV2SchemaIsAdmitted();
        testStructuralSimilarityIgnoresPaletteAndDressing();
        testRotatedMirroredAndScaledCopiesAreRejected();
        testThinShellScalingAndSingleOutlierCannotDisguiseACopy();
        testDifferentMassingPassesCatalogGate();
        RoofGeometryValidatorRegressionTest.runAll();
        System.out.println("PASS authored whole-building validator regressions");
    }

    private static void testCoherentTwoLevelBlueprintPasses() {
        ValidationReport report = WholeBuildingValidator.validate(validBlueprint());
        require(report.valid(), "Known-good whole building failed validation: " + report.issues());
    }

    private static void testBlockedEntranceIsRejected() {
        WholeBuildingBlueprint base = validBlueprint();
        WholeBuildingBlueprint blocked = withStages(base, appendStage(
                base,
                "blocked_entrance",
                List.of(new Placement(voxel(1, 1, -1), furniture())),
                Set.of()));
        ValidationReport report = WholeBuildingValidator.validate(blocked);
        require(report.has(Code.ENTRANCE_BLOCKED),
                "A furnishing across the exterior doorway did not block admission");
        require(report.has(Code.RESERVED_CLEARANCE_BLOCKED),
                "Doorway obstruction escaped its reserved-clearance contract");
    }

    private static void testBlockedBedAndWorkstationRoutesAreRejected() {
        WholeBuildingBlueprint base = validBlueprint();
        WholeBuildingBlueprint bedRouteBlocked = withStages(base, appendStage(
                base,
                "blocked_bed_aisle",
                List.of(new Placement(voxel(2, 1, 2), furniture())),
                Set.of()));
        ValidationReport bedReport = WholeBuildingValidator.validate(bedRouteBlocked);
        require(bedReport.has(Code.ACCESS_TARGET_UNREACHABLE),
                "A blocked bed aisle did not make its access target unreachable");
        require(bedReport.has(Code.RESERVED_CLEARANCE_BLOCKED),
                "A blocked bed aisle escaped its reserved-clearance contract");

        WholeBuildingBlueprint workstationBlocked = withStages(base, appendStage(
                base,
                "blocked_workstation_access",
                List.of(new Placement(voxel(1, 1, 3), furniture())),
                Set.of()));
        ValidationReport workstationReport = WholeBuildingValidator.validate(workstationBlocked);
        require(workstationReport.has(Code.ACCESS_TARGET_BLOCKED),
                "A furnishing on the workstation interaction cell was accepted");
    }

    private static void testWalkableInteriorCannotBeHiddenFromValidation() {
        WholeBuildingBlueprint base = validBlueprint();
        Set<Voxel> incompleteCirculation = new LinkedHashSet<>(base.circulationCells());
        incompleteCirculation.remove(voxel(1, 1, 3));
        WholeBuildingBlueprint underDeclared = new WholeBuildingBlueprint(
                base.schema(),
                base.id(),
                base.bounds(),
                base.shellPolicy(),
                base.stages(),
                base.interiorCells(),
                incompleteCirculation,
                base.traversalLinks(),
                base.entrances(),
                base.accessTargets(),
                base.reservedClearances(),
                base.supportAnchors(),
                base.supportExpectations(),
                base.paletteSlots());
        require(WholeBuildingValidator.validate(underDeclared)
                        .has(Code.WALKABLE_INTERIOR_UNDECLARED),
                "A clear interior floor cell could be hidden by omitting its route metadata");
    }

    private static void testRoofAndShellGapsAreRejected() {
        WholeBuildingBlueprint base = validBlueprint();
        Voxel missingRoof = voxel(2, 5, 2);
        List<Stage> stages = new ArrayList<>();
        for (Stage stage : base.stages()) {
            List<Placement> filtered = stage.additions().stream()
                    .filter(placement -> !placement.position().equals(missingRoof))
                    .toList();
            stages.add(new Stage(stage.id(), filtered, stage.removals()));
        }
        ValidationReport report = WholeBuildingValidator.validate(withStages(base, stages));
        require(report.has(Code.SHELL_GAP),
                "A missing central roof cell did not expose the declared interior");
    }

    private static void testDisconnectedStructuralComponentIsRejected() {
        WholeBuildingBlueprint base = validBlueprint();
        WholeBuildingBlueprint floatingRoof = withStages(base, appendStage(
                base,
                "floating_roof_piece",
                List.of(new Placement(voxel(2, 3, -2), roof())),
                Set.of()));
        ValidationReport report = WholeBuildingValidator.validate(floatingRoof);
        require(report.has(Code.UNSUPPORTED_COMPONENT),
                "A detached roof component had no foundation but passed validation");
    }

    private static void testUpperFloorRequiresAuthoredTraversal() {
        WholeBuildingBlueprint base = validBlueprint();
        WholeBuildingBlueprint missingStairs = copy(
                base,
                base.stages(),
                List.of(),
                base.paletteSlots(),
                base.reservedClearances(),
                base.supportExpectations());
        ValidationReport report = WholeBuildingValidator.validate(missingStairs);
        require(report.has(Code.UPPER_FLOOR_UNREACHABLE),
                "An upper circulation region without stairs passed validation");
        require(report.has(Code.ACCESS_TARGET_UNREACHABLE),
                "The upper-floor access target remained reachable without a vertical link");
    }

    private static void testStagesMustBeStrictlyAppendOnly() {
        WholeBuildingBlueprint base = validBlueprint();
        Voxel frozenRoof = voxel(2, 5, 2);
        WholeBuildingBlueprint overlap = withStages(base, appendStage(
                base,
                "rewrite_roof",
                List.of(new Placement(frozenRoof, roof())),
                Set.of()));
        require(WholeBuildingValidator.validate(overlap).has(Code.STAGE_OVERLAP),
                "A later stage rewrote an earlier cell without an overlap error");

        WholeBuildingBlueprint removal = withStages(base, appendStage(
                base,
                "remove_roof",
                List.of(),
                Set.of(frozenRoof)));
        require(WholeBuildingValidator.validate(removal).has(Code.NON_ADDITIVE_STAGE),
                "A destructive stage removal was accepted");
    }

    private static void testPaletteSwapsMustPreserveShapeCategory() {
        WholeBuildingBlueprint base = validBlueprint();
        Map<String, PaletteSlot> invalidPalette = new LinkedHashMap<>(base.paletteSlots());
        invalidPalette.put("stair", new PaletteSlot(
                "stair",
                ShapeCategory.STAIR,
                List.of(new PaletteMaterial("minecraft:oak_planks", ShapeCategory.FULL_BLOCK))));
        WholeBuildingBlueprint invalid = copy(
                base,
                base.stages(),
                base.traversalLinks(),
                invalidPalette,
                base.reservedClearances(),
                base.supportExpectations());
        require(WholeBuildingValidator.validate(invalid).has(Code.INVALID_PALETTE_SLOT),
                "A full block was accepted as a stair palette replacement");
    }

    private static void testOpenAirTemplatesDoNotRequireClosedWalls() {
        WholeBuildingBlueprint market = new WholeBuildingBlueprint(
                WholeBuildingBlueprint.AUTHORED_V2_SCHEMA,
                "market/open_pavilion",
                new Bounds(voxel(0, 0, 0), voxel(2, 2, 2)),
                ShellPolicy.OPEN_AIR,
                List.of(new Stage(
                        "base",
                        List.of(new Placement(voxel(1, 0, 1), foundation())),
                        Set.of())),
                Set.of(),
                Set.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(voxel(1, 0, 1)),
                List.of(),
                palettes());
        ValidationReport report = WholeBuildingValidator.validate(market);
        require(!report.has(Code.SHELL_GAP),
                "An explicitly open-air pavilion was treated as a broken enclosed shell");
    }

    private static void testOnlyAuthoredV2SchemaIsAdmitted() {
        WholeBuildingBlueprint base = validBlueprint();
        WholeBuildingBlueprint legacy = new WholeBuildingBlueprint(
                "modular_v1",
                base.id(),
                base.bounds(),
                base.shellPolicy(),
                base.stages(),
                base.interiorCells(),
                base.circulationCells(),
                base.traversalLinks(),
                base.entrances(),
                base.accessTargets(),
                base.reservedClearances(),
                base.supportAnchors(),
                base.supportExpectations(),
                base.paletteSlots());
        require(WholeBuildingValidator.validate(legacy).has(Code.INVALID_METADATA),
                "A legacy modular recipe entered the authored-v2 catalog gate");
    }

    private static void testStructuralSimilarityIgnoresPaletteAndDressing() {
        WholeBuildingBlueprint original = validBlueprint();
        Map<String, PaletteSlot> recoloredPalette = new LinkedHashMap<>();
        for (Map.Entry<String, PaletteSlot> entry : original.paletteSlots().entrySet()) {
            PaletteSlot slot = entry.getValue();
            recoloredPalette.put(entry.getKey(), new PaletteSlot(
                    slot.id(),
                    slot.requiredShape(),
                    List.of(new PaletteMaterial(
                            "test:unrelated_" + entry.getKey(), slot.requiredShape()))));
        }
        List<Stage> baseCosmetics = addToFirstStage(
                original,
                List.of(
                        new Placement(voxel(3, 1, -2), furniture()),
                        new Placement(voxel(3, 2, -2), light())));
        WholeBuildingBlueprint cosmeticBase = copyWithIdentity(
                original,
                "house/cosmetic_base",
                baseCosmetics,
                recoloredPalette);
        List<Stage> dressedStages = appendStage(
                cosmeticBase,
                "cosmetic_dressing",
                List.of(
                        new Placement(voxel(3, 1, -1), furniture()),
                        new Placement(voxel(3, 2, -1), light())),
                Set.of());
        WholeBuildingBlueprint recoloredAndDressed = copyWithIdentity(
                original,
                "house/recolored_and_dressed",
                dressedStages,
                recoloredPalette);

        StructuralSnapshot originalShape = WholeBuildingDistinctivenessValidator.snapshot(original);
        StructuralSnapshot cosmeticShape =
                WholeBuildingDistinctivenessValidator.snapshot(recoloredAndDressed);
        require(originalShape.structuralCells().equals(cosmeticShape.structuralCells()),
                "Base or staged fixtures leaked into structural geometry");

        WholeBuildingBlueprint structurallyChanged = copyWithIdentity(
                original,
                "house/structural_change",
                addToFirstStage(original, List.of(
                        new Placement(voxel(3, 3, -2), wall()))),
                original.paletteSlots());
        require(!originalShape.structuralCells().equals(
                        WholeBuildingDistinctivenessValidator.snapshot(structurallyChanged)
                                .structuralCells()),
                "A base-stage wall was discarded by structural role filtering");

        CatalogReport report = WholeBuildingDistinctivenessValidator.validateBlueprints(
                List.of(original, recoloredAndDressed));
        require(!report.distinct(),
                "Palette and cosmetic dressing disguised a repeated structural shell");
        require(report.excessivePairs().getFirst().similarity() == 1.0,
                "Palette-blind copies did not retain exact structural similarity");
        try {
            report.requireDistinct();
            throw new AssertionError("A repeated structural shell passed catalog admission");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(original.id())
                            && expected.getMessage().contains(recoloredAndDressed.id()),
                    "Distinctiveness failure did not identify the repeated pair");
        }
    }

    private static void testRotatedMirroredAndScaledCopiesAreRejected() {
        Set<Voxel> asymmetricHall = asymmetricMassing();
        Set<Voxel> transformed = new LinkedHashSet<>();
        for (Voxel cell : asymmetricHall) {
            // Rotate 90 degrees, mirror, scale every occupied cube uniformly, and translate it.
            int transformedX = -cell.z();
            int transformedZ = -cell.x();
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        transformed.add(voxel(
                                transformedX * 2 + dx + 40,
                                cell.y() * 2 + dy - 12,
                                transformedZ * 2 + dz + 70));
                    }
                }
            }
        }
        CatalogReport report = WholeBuildingDistinctivenessValidator.validateSnapshots(List.of(
                new StructuralSnapshot("hall/original", asymmetricHall),
                new StructuralSnapshot("hall/transformed_copy", transformed)));
        require(!report.distinct(),
                "Rotating, mirroring, and scaling manufactured a supposedly distinct building");
        require(report.comparisons().getFirst().similarity() > 0.97,
                "A uniform transformed copy scored unexpectedly low: "
                        + report.comparisons().getFirst());
    }

    private static void testDifferentMassingPassesCatalogGate() {
        Set<Voxel> longLowHall = solidCuboid(10, 3, 4);
        Set<Voxel> steppedTower = new LinkedHashSet<>();
        steppedTower.addAll(solidCuboid(3, 9, 3));
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 3; z++) {
                steppedTower.add(voxel(x, 7, z));
            }
        }
        CatalogReport report = WholeBuildingDistinctivenessValidator.validateSnapshots(List.of(
                new StructuralSnapshot("hall/long_low", longLowHall),
                new StructuralSnapshot("tower/stepped", steppedTower)));
        require(report.distinct(),
                "Clearly different hall and tower massing were rejected: " + report.comparisons());
    }

    private static void testThinShellScalingAndSingleOutlierCannotDisguiseACopy() {
        StructuralSnapshot small = new StructuralSnapshot(
                "house/thin_small", hollowGabledShell(7, 5, 3));
        Set<Voxel> largerCells = hollowGabledShell(13, 9, 6);
        // A lone distant trim/support must not redefine the normalization envelope for the shell.
        largerCells.add(voxel(60, 2, 4));
        StructuralSnapshot larger = new StructuralSnapshot("house/thin_large", largerCells);
        double similarity = WholeBuildingDistinctivenessValidator.compare(small, larger).similarity();
        require(similarity > WholeBuildingDistinctivenessValidator.DEFAULT_MAX_SIMILARITY,
                "A realistic one-block-thick scaled shell or tiny outlier evaded similarity: "
                        + similarity);
    }

    private static Set<Voxel> asymmetricMassing() {
        Set<Voxel> cells = new LinkedHashSet<>(solidCuboid(7, 4, 4));
        for (int x = 0; x < 3; x++) {
            for (int y = 4; y < 7; y++) {
                for (int z = 0; z < 3; z++) {
                    cells.add(voxel(x, y, z));
                }
            }
        }
        for (int x = 5; x < 9; x++) {
            for (int z = 1; z < 3; z++) {
                cells.add(voxel(x, 0, z));
                cells.add(voxel(x, 1, z));
            }
        }
        return cells;
    }

    private static Set<Voxel> solidCuboid(int width, int height, int depth) {
        Set<Voxel> cells = new LinkedHashSet<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    cells.add(voxel(x, y, z));
                }
            }
        }
        return cells;
    }

    private static Set<Voxel> hollowGabledShell(int width, int depth, int wallHeight) {
        Set<Voxel> cells = new LinkedHashSet<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                cells.add(voxel(x, 0, z));
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
                    for (int y = 1; y <= wallHeight; y++) {
                        cells.add(voxel(x, y, z));
                    }
                }
                int roofY = wallHeight + 1 + Math.min(x, width - 1 - x);
                cells.add(voxel(x, roofY, z));
            }
        }
        return cells;
    }

    private static WholeBuildingBlueprint validBlueprint() {
        Map<Voxel, Cell> baseCells = new LinkedHashMap<>();

        // A continuous foundation and roof also tie all outer-wall components together.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                baseCells.put(voxel(x, 0, z), foundation());
                baseCells.put(voxel(x, 5, z), roof());
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                baseCells.put(voxel(x, y, 0), wall());
                baseCells.put(voxel(x, y, 4), wall());
            }
            for (int z = 1; z <= 3; z++) {
                baseCells.put(voxel(0, y, z), wall());
                baseCells.put(voxel(4, y, z), wall());
            }
        }

        // Weather-sealing but passable two-block door.
        baseCells.put(voxel(1, 1, 0), door());
        baseCells.put(voxel(1, 2, 0), door());

        // Deliberate rising interior route with load-bearing blocks below every elevated step.
        baseCells.put(voxel(2, 1, 1), stair());
        baseCells.put(voxel(3, 1, 1), foundation());
        baseCells.put(voxel(3, 2, 1), stair());
        baseCells.put(voxel(3, 1, 3), bed());
        baseCells.put(voxel(2, 1, 3), workstation());

        Set<Voxel> interior = new LinkedHashSet<>();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 3; z++) {
                    interior.add(voxel(x, y, z));
                }
            }
        }

        Set<Voxel> circulation = Set.of(
                voxel(1, 1, 1),
                voxel(1, 1, 2),
                voxel(1, 1, 3),
                voxel(2, 1, 2),
                voxel(3, 1, 2),
                voxel(2, 2, 1),
                voxel(3, 3, 1));
        List<TraversalLink> links = List.of(
                new TraversalLink(voxel(1, 1, 1), voxel(2, 2, 1)),
                new TraversalLink(voxel(2, 2, 1), voxel(3, 3, 1)));

        List<ReservedClearance> clearances = List.of(
                new ReservedClearance("entrance", Set.of(
                        voxel(1, 1, -1), voxel(1, 2, -1),
                        voxel(1, 1, 0), voxel(1, 2, 0),
                        voxel(1, 1, 1), voxel(1, 2, 1))),
                new ReservedClearance("bed aisle", Set.of(
                        voxel(2, 1, 2), voxel(2, 2, 2),
                        voxel(3, 1, 2), voxel(3, 2, 2))),
                new ReservedClearance("workstation access", Set.of(
                        voxel(1, 1, 3), voxel(1, 2, 3))));

        List<Placement> basePlacements = baseCells.entrySet().stream()
                .map(entry -> new Placement(entry.getKey(), entry.getValue()))
                .toList();
        List<Stage> stages = List.of(
                new Stage("base", basePlacements, Set.of()),
                new Stage("prosperity_detail", List.of(
                        new Placement(voxel(2, 1, -2), light())), Set.of()));
        Voxel foundationAnchor = voxel(0, 0, 0);
        return new WholeBuildingBlueprint(
                WholeBuildingBlueprint.AUTHORED_V2_SCHEMA,
                "house/gold_master_01",
                new Bounds(voxel(0, 0, -2), voxel(4, 5, 4)),
                ShellPolicy.ENCLOSED,
                stages,
                interior,
                circulation,
                links,
                List.of(new Entrance(
                        "front",
                        voxel(1, 1, 1),
                        voxel(1, 1, 0),
                        voxel(1, 1, -1))),
                List.of(
                        new AccessTarget(
                                "bed-side",
                                TargetKind.BED,
                                voxel(3, 1, 2),
                                Set.of(voxel(3, 1, 3))),
                        new AccessTarget(
                                "workstation-front",
                                TargetKind.WORKSTATION,
                                voxel(1, 1, 3),
                                Set.of(voxel(2, 1, 3))),
                        new AccessTarget(
                                "upper-landing",
                                TargetKind.UPPER_FLOOR,
                                voxel(3, 3, 1),
                                Set.of())),
                clearances,
                Set.of(foundationAnchor),
                List.of(new SupportExpectation(
                        "main roof",
                        voxel(2, 5, 2),
                        Set.of(foundationAnchor))),
                palettes());
    }

    private static List<Stage> appendStage(
            WholeBuildingBlueprint blueprint,
            String id,
            List<Placement> additions,
            Set<Voxel> removals) {
        List<Stage> stages = new ArrayList<>(blueprint.stages());
        stages.add(new Stage(id, additions, removals));
        return stages;
    }

    private static List<Stage> addToFirstStage(
            WholeBuildingBlueprint blueprint,
            List<Placement> additions) {
        List<Stage> stages = new ArrayList<>(blueprint.stages());
        Stage first = stages.getFirst();
        List<Placement> expanded = new ArrayList<>(first.additions());
        expanded.addAll(additions);
        stages.set(0, new Stage(first.id(), expanded, first.removals()));
        return stages;
    }

    private static WholeBuildingBlueprint withStages(
            WholeBuildingBlueprint blueprint,
            List<Stage> stages) {
        return copy(
                blueprint,
                stages,
                blueprint.traversalLinks(),
                blueprint.paletteSlots(),
                blueprint.reservedClearances(),
                blueprint.supportExpectations());
    }

    private static WholeBuildingBlueprint copy(
            WholeBuildingBlueprint source,
            List<Stage> stages,
            List<TraversalLink> links,
            Map<String, PaletteSlot> paletteSlots,
            List<ReservedClearance> clearances,
            List<SupportExpectation> supportExpectations) {
        return new WholeBuildingBlueprint(
                source.schema(),
                source.id(),
                source.bounds(),
                source.shellPolicy(),
                stages,
                source.interiorCells(),
                source.circulationCells(),
                links,
                source.entrances(),
                source.accessTargets(),
                clearances,
                source.supportAnchors(),
                supportExpectations,
                paletteSlots);
    }

    private static WholeBuildingBlueprint copyWithIdentity(
            WholeBuildingBlueprint source,
            String id,
            List<Stage> stages,
            Map<String, PaletteSlot> paletteSlots) {
        return new WholeBuildingBlueprint(
                source.schema(),
                id,
                source.bounds(),
                source.shellPolicy(),
                stages,
                source.interiorCells(),
                source.circulationCells(),
                source.traversalLinks(),
                source.entrances(),
                source.accessTargets(),
                source.reservedClearances(),
                source.supportAnchors(),
                source.supportExpectations(),
                paletteSlots);
    }

    private static Map<String, PaletteSlot> palettes() {
        Map<String, PaletteSlot> slots = new LinkedHashMap<>();
        addSlot(slots, "foundation", ShapeCategory.FULL_BLOCK, "minecraft:stone_bricks");
        addSlot(slots, "wall", ShapeCategory.FULL_BLOCK, "minecraft:oak_planks");
        addSlot(slots, "roof", ShapeCategory.FULL_BLOCK, "minecraft:spruce_planks");
        addSlot(slots, "stair", ShapeCategory.STAIR, "minecraft:oak_stairs");
        addSlot(slots, "door", ShapeCategory.DOOR, "minecraft:oak_door");
        addSlot(slots, "bed", ShapeCategory.BED, "minecraft:white_bed");
        addSlot(slots, "workstation", ShapeCategory.WORKSTATION, "minecraft:barrel");
        addSlot(slots, "furniture", ShapeCategory.FULL_BLOCK, "minecraft:barrel");
        addSlot(slots, "light", ShapeCategory.LIGHT, "minecraft:lantern");
        return slots;
    }

    private static void addSlot(
            Map<String, PaletteSlot> slots,
            String id,
            ShapeCategory shape,
            String material) {
        slots.put(id, new PaletteSlot(
                id,
                shape,
                List.of(new PaletteMaterial(material, shape))));
    }

    private static Cell foundation() {
        return new Cell(
                Role.FOUNDATION,
                ShapeCategory.FULL_BLOCK,
                "foundation",
                true,
                true,
                true);
    }

    private static Cell wall() {
        return new Cell(
                Role.WALL,
                ShapeCategory.FULL_BLOCK,
                "wall",
                true,
                true,
                true);
    }

    private static Cell roof() {
        return new Cell(
                Role.ROOF,
                ShapeCategory.FULL_BLOCK,
                "roof",
                true,
                true,
                true);
    }

    private static Cell stair() {
        return new Cell(
                Role.STAIR,
                ShapeCategory.STAIR,
                "stair",
                true,
                false,
                true);
    }

    private static Cell door() {
        return new Cell(
                Role.DOOR,
                ShapeCategory.DOOR,
                "door",
                false,
                true,
                false);
    }

    private static Cell bed() {
        return new Cell(
                Role.BED,
                ShapeCategory.BED,
                "bed",
                true,
                false,
                false);
    }

    private static Cell workstation() {
        return new Cell(
                Role.WORKSTATION,
                ShapeCategory.WORKSTATION,
                "workstation",
                true,
                false,
                false);
    }

    private static Cell furniture() {
        return new Cell(
                Role.FURNITURE,
                ShapeCategory.FULL_BLOCK,
                "furniture",
                true,
                false,
                false);
    }

    private static Cell light() {
        return new Cell(
                Role.LIGHT,
                ShapeCategory.LIGHT,
                "light",
                false,
                false,
                false);
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
