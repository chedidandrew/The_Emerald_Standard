package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.AccessTarget;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Cell;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Entrance;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.PaletteMaterial;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.PaletteSlot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Placement;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.ReservedClearance;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Role;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Stage;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.SupportExpectation;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.TargetKind;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.TraversalLink;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Admission validator for authored-v2 whole-building templates. */
public final class WholeBuildingValidator {
    private static final int[][] ORTHOGONAL_DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][] HORIZONTAL_DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private WholeBuildingValidator() {
    }

    public enum Code {
        INVALID_METADATA,
        OUT_OF_BOUNDS,
        STAGE_OVERLAP,
        NON_ADDITIVE_STAGE,
        INVALID_PALETTE_SLOT,
        RESERVED_CLEARANCE_BLOCKED,
        SHELL_GAP,
        WALKABLE_INTERIOR_UNDECLARED,
        CIRCULATION_BLOCKED,
        CIRCULATION_DISCONNECTED,
        ENTRANCE_BLOCKED,
        ACCESS_TARGET_BLOCKED,
        ACCESS_TARGET_UNREACHABLE,
        ACCESS_TARGET_FEATURE_MISSING,
        UPPER_FLOOR_UNREACHABLE,
        UNSUPPORTED_COMPONENT,
        SUPPORT_EXPECTATION_UNMET
    }

    public record Issue(Code code, String subject, Voxel position, String message) {
        public Issue {
            code = Objects.requireNonNull(code, "code");
            subject = subject == null ? "" : subject;
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record ValidationReport(List<Issue> issues) {
        public ValidationReport {
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        }

        public boolean valid() {
            return issues.isEmpty();
        }

        public boolean has(Code code) {
            return issues.stream().anyMatch(issue -> issue.code() == code);
        }

        public long count(Code code) {
            return issues.stream().filter(issue -> issue.code() == code).count();
        }

        /** Throws one deterministic catalog-admission error containing every discovered defect. */
        public void requireValid() {
            if (!valid()) {
                StringBuilder message = new StringBuilder("Invalid authored building blueprint:");
                for (Issue issue : issues) {
                    message.append(System.lineSeparator())
                            .append(" - ")
                            .append(issue.code())
                            .append(" [")
                            .append(issue.subject())
                            .append("] ")
                            .append(issue.message());
                }
                throw new IllegalArgumentException(message.toString());
            }
        }
    }

    public static ValidationReport validate(WholeBuildingBlueprint blueprint) {
        if (blueprint == null) {
            return new ValidationReport(List.of(new Issue(
                    Code.INVALID_METADATA,
                    "blueprint",
                    null,
                    "Blueprint is null")));
        }

        List<Issue> issues = new ArrayList<>();
        if (!WholeBuildingBlueprint.AUTHORED_V2_SCHEMA.equals(blueprint.schema())) {
            issues.add(new Issue(
                    Code.INVALID_METADATA,
                    "schema",
                    null,
                    "Whole-building validation requires schema '"
                            + WholeBuildingBlueprint.AUTHORED_V2_SCHEMA + "'"));
        }
        Map<Voxel, Cell> cells = assembleStages(blueprint, issues);
        validateMetadataBounds(blueprint, cells, issues);
        validatePalette(blueprint, cells, issues);
        validateReservedClearances(blueprint, cells, issues);
        validateShell(blueprint, cells, issues);
        validateNavigation(blueprint, cells, issues);
        validateSupport(blueprint, cells, issues);
        issues.sort(Comparator
                .comparing((Issue issue) -> issue.code().ordinal())
                .thenComparing(Issue::subject)
                .thenComparing(issue -> issue.position() == null
                        ? new Voxel(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE)
                        : issue.position()));
        return new ValidationReport(issues);
    }

    private static Map<Voxel, Cell> assembleStages(
            WholeBuildingBlueprint blueprint,
            List<Issue> issues) {
        Map<Voxel, Cell> cells = new LinkedHashMap<>();
        Set<String> stageIds = new HashSet<>();
        if (blueprint.stages().isEmpty()) {
            issues.add(new Issue(
                    Code.INVALID_METADATA,
                    "stages",
                    null,
                    "At least one authored stage is required"));
            return cells;
        }

        for (Stage stage : blueprint.stages()) {
            if (!stageIds.add(stage.id())) {
                issues.add(new Issue(
                        Code.INVALID_METADATA,
                        stage.id(),
                        null,
                        "Stage id is duplicated"));
            }
            for (Voxel removal : sorted(stage.removals())) {
                issues.add(new Issue(
                        Code.NON_ADDITIVE_STAGE,
                        stage.id(),
                        removal,
                        "Authored-v2 stages may not remove or replace earlier cells"));
            }
            for (Placement placement : stage.additions()) {
                Cell previous = cells.putIfAbsent(placement.position(), placement.cell());
                if (previous != null) {
                    issues.add(new Issue(
                            Code.STAGE_OVERLAP,
                            stage.id(),
                            placement.position(),
                            "Stage writes a cell already owned by an earlier addition"));
                }
            }
        }
        return cells;
    }

    private static void validateMetadataBounds(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            List<Issue> issues) {
        cells.keySet().forEach(position -> checkBounds(
                blueprint, position, "placement", issues));
        blueprint.interiorCells().forEach(position -> checkBounds(
                blueprint, position, "interior", issues));
        blueprint.circulationCells().forEach(position -> checkBounds(
                blueprint, position, "circulation", issues));
        blueprint.supportAnchors().forEach(position -> checkBounds(
                blueprint, position, "support anchor", issues));
        for (TraversalLink link : blueprint.traversalLinks()) {
            checkBounds(blueprint, link.from(), "traversal link", issues);
            checkBounds(blueprint, link.to(), "traversal link", issues);
        }
        for (Entrance entrance : blueprint.entrances()) {
            checkBounds(blueprint, entrance.interiorAccess(), entrance.id(), issues);
            checkBounds(blueprint, entrance.portal(), entrance.id(), issues);
            checkBounds(blueprint, entrance.exteriorAccess(), entrance.id(), issues);
        }
        for (AccessTarget target : blueprint.accessTargets()) {
            checkBounds(blueprint, target.accessCell(), target.id(), issues);
            target.featureCells().forEach(position -> checkBounds(
                    blueprint, position, target.id(), issues));
        }
        for (ReservedClearance clearance : blueprint.reservedClearances()) {
            clearance.cells().forEach(position -> checkBounds(
                    blueprint, position, clearance.id(), issues));
        }
        for (SupportExpectation expectation : blueprint.supportExpectations()) {
            checkBounds(blueprint, expectation.subject(), expectation.id(), issues);
            expectation.permittedAnchors().forEach(position -> checkBounds(
                    blueprint, position, expectation.id(), issues));
        }
    }

    private static void checkBounds(
            WholeBuildingBlueprint blueprint,
            Voxel position,
            String subject,
            List<Issue> issues) {
        if (!blueprint.bounds().contains(position)) {
            issues.add(new Issue(
                    Code.OUT_OF_BOUNDS,
                    subject,
                    position,
                    "Coordinate lies outside the declared template bounds"));
        }
    }

    private static void validatePalette(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            List<Issue> issues) {
        List<Map.Entry<String, PaletteSlot>> slots = new ArrayList<>(
                blueprint.paletteSlots().entrySet());
        slots.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, PaletteSlot> entry : slots) {
            PaletteSlot slot = entry.getValue();
            if (!entry.getKey().equals(slot.id())) {
                issues.add(new Issue(
                        Code.INVALID_PALETTE_SLOT,
                        entry.getKey(),
                        null,
                        "Palette map key does not match slot id '" + slot.id() + "'"));
            }
            if (slot.materials().isEmpty()) {
                issues.add(new Issue(
                        Code.INVALID_PALETTE_SLOT,
                        slot.id(),
                        null,
                        "Palette slot has no compatible materials"));
            }
            Set<String> materialIds = new HashSet<>();
            for (PaletteMaterial material : slot.materials()) {
                if (!materialIds.add(material.id())) {
                    issues.add(new Issue(
                            Code.INVALID_PALETTE_SLOT,
                            slot.id(),
                            null,
                            "Palette material id '" + material.id() + "' is duplicated"));
                }
                if (material.shape() != slot.requiredShape()) {
                    issues.add(new Issue(
                            Code.INVALID_PALETTE_SLOT,
                            slot.id(),
                            null,
                            "Material '" + material.id() + "' is " + material.shape()
                                    + " but the slot requires " + slot.requiredShape()));
                }
            }
        }

        for (Map.Entry<Voxel, Cell> entry : cells.entrySet()) {
            Cell cell = entry.getValue();
            PaletteSlot slot = blueprint.paletteSlots().get(cell.paletteSlot());
            if (slot == null) {
                issues.add(new Issue(
                        Code.INVALID_PALETTE_SLOT,
                        cell.paletteSlot(),
                        entry.getKey(),
                        "Placement references an undefined palette slot"));
            } else if (slot.requiredShape() != cell.shape()) {
                issues.add(new Issue(
                        Code.INVALID_PALETTE_SLOT,
                        slot.id(),
                        entry.getKey(),
                        "Placement is " + cell.shape() + " but its slot requires "
                                + slot.requiredShape()));
            }
        }
    }

    private static void validateReservedClearances(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            List<Issue> issues) {
        for (ReservedClearance clearance : blueprint.reservedClearances()) {
            for (Voxel position : sorted(clearance.cells())) {
                Cell cell = cells.get(position);
                if (cell != null && cell.blocksMovement()) {
                    issues.add(new Issue(
                            Code.RESERVED_CLEARANCE_BLOCKED,
                            clearance.id(),
                            position,
                            "A movement-blocking " + cell.role()
                                    + " occupies reserved clearance"));
                }
            }
        }
    }

    private static void validateShell(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            List<Issue> issues) {
        if (blueprint.shellPolicy() == WholeBuildingBlueprint.ShellPolicy.OPEN_AIR) {
            return;
        }
        if (blueprint.interiorCells().isEmpty()) {
            issues.add(new Issue(
                    Code.INVALID_METADATA,
                    "interior",
                    null,
                    "An enclosed blueprint must declare its complete interior volume"));
            return;
        }

        Set<Voxel> gaps = new LinkedHashSet<>();
        for (Voxel interior : sorted(blueprint.interiorCells())) {
            for (int[] direction : ORTHOGONAL_DIRECTIONS) {
                Voxel neighbor = interior.offset(direction[0], direction[1], direction[2]);
                if (blueprint.interiorCells().contains(neighbor)) {
                    continue;
                }
                Cell shell = cells.get(neighbor);
                if (shell == null || !shell.sealsExterior()) {
                    gaps.add(neighbor);
                }
            }
        }
        for (Voxel gap : sorted(gaps)) {
            issues.add(new Issue(
                    Code.SHELL_GAP,
                    "shell",
                    gap,
                    "Declared interior is exposed through a missing or non-sealing shell cell"));
        }
    }

    private static void validateNavigation(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            List<Issue> issues) {
        for (Voxel position : sorted(blueprint.interiorCells())) {
            if (isAuthoredFloorPosition(blueprint, cells, position)
                    && !blueprint.circulationCells().contains(position)) {
                issues.add(new Issue(
                        Code.WALKABLE_INTERIOR_UNDECLARED,
                        "interior",
                        position,
                        "A clear authored floor position is omitted from circulation metadata"));
            }
        }

        Set<Voxel> walkable = new HashSet<>();
        for (Voxel position : sorted(blueprint.circulationCells())) {
            boolean inside = blueprint.interiorCells().contains(position)
                    && blueprint.interiorCells().contains(position.offset(0, 1, 0));
            if (!inside || !hasHeadroom(cells, position)) {
                issues.add(new Issue(
                        Code.CIRCULATION_BLOCKED,
                        "circulation",
                        position,
                        inside
                                ? "Circulation cell lacks two-block movement clearance"
                                : "Circulation foot/head cells are not both in the declared interior"));
            } else {
                walkable.add(position);
            }
        }

        Map<Voxel, Set<Voxel>> graph = new HashMap<>();
        walkable.forEach(position -> graph.put(position, new HashSet<>()));
        for (Voxel position : walkable) {
            for (int[] direction : HORIZONTAL_DIRECTIONS) {
                Voxel neighbor = position.offset(direction[0], 0, direction[2]);
                if (walkable.contains(neighbor)) {
                    graph.get(position).add(neighbor);
                }
            }
        }
        for (TraversalLink link : blueprint.traversalLinks()) {
            if (!validTraversalLink(link) || !walkable.contains(link.from())
                    || !walkable.contains(link.to())) {
                issues.add(new Issue(
                        Code.INVALID_METADATA,
                        "traversal link",
                        link.from(),
                        "Vertical traversal endpoints must be adjacent walkable cells"));
                continue;
            }
            graph.get(link.from()).add(link.to());
            graph.get(link.to()).add(link.from());
        }

        Set<Voxel> entranceSeeds = new HashSet<>();
        if (blueprint.entrances().isEmpty()) {
            issues.add(new Issue(
                    Code.INVALID_METADATA,
                    "entrances",
                    null,
                    "At least one entrance is required"));
        }
        for (Entrance entrance : blueprint.entrances()) {
            if (!validEntranceGeometry(entrance)
                    || !walkable.contains(entrance.interiorAccess())
                    || !hasHeadroom(cells, entrance.portal())
                    || !hasHeadroom(cells, entrance.exteriorAccess())) {
                issues.add(new Issue(
                        Code.ENTRANCE_BLOCKED,
                        entrance.id(),
                        entrance.portal(),
                        "Entrance route is misaligned, obstructed, or disconnected from circulation"));
                continue;
            }
            Cell portalCell = cells.get(entrance.portal());
            if (blueprint.shellPolicy() == WholeBuildingBlueprint.ShellPolicy.ENCLOSED
                    && (portalCell == null || !portalCell.sealsExterior())) {
                issues.add(new Issue(
                        Code.ENTRANCE_BLOCKED,
                        entrance.id(),
                        entrance.portal(),
                        "An enclosed building entrance must use a passable weather-sealing portal"));
                continue;
            }
            entranceSeeds.add(entrance.interiorAccess());
        }

        Set<Voxel> reachable = traverse(graph, entranceSeeds);
        validateAccessTargets(blueprint, cells, walkable, reachable, issues);

        int entranceLevel = blueprint.entrances().stream()
                .mapToInt(entrance -> entrance.interiorAccess().y())
                .min()
                .orElse(Integer.MIN_VALUE);
        Voxel firstUpper = null;
        Voxel firstOther = null;
        for (Voxel position : sorted(walkable)) {
            if (reachable.contains(position)) {
                continue;
            }
            if (position.y() > entranceLevel && firstUpper == null) {
                firstUpper = position;
            } else if (position.y() <= entranceLevel && firstOther == null) {
                firstOther = position;
            }
        }
        if (firstUpper != null) {
            issues.add(new Issue(
                    Code.UPPER_FLOOR_UNREACHABLE,
                    "circulation",
                    firstUpper,
                    "At least one elevated circulation region has no route from an entrance"));
        }
        if (firstOther != null) {
            issues.add(new Issue(
                    Code.CIRCULATION_DISCONNECTED,
                    "circulation",
                    firstOther,
                    "At least one circulation region has no route from an entrance"));
        }
    }

    private static void validateAccessTargets(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            Set<Voxel> walkable,
            Set<Voxel> reachable,
            List<Issue> issues) {
        for (AccessTarget target : blueprint.accessTargets()) {
            if (!walkable.contains(target.accessCell())) {
                issues.add(new Issue(
                        Code.ACCESS_TARGET_BLOCKED,
                        target.id(),
                        target.accessCell(),
                        target.kind() + " access cell is not a clear circulation position"));
            } else if (!reachable.contains(target.accessCell())) {
                issues.add(new Issue(
                        Code.ACCESS_TARGET_UNREACHABLE,
                        target.id(),
                        target.accessCell(),
                        target.kind() + " has no traversable route from an entrance"));
            }

            Role requiredRole = switch (target.kind()) {
                case BED -> Role.BED;
                case WORKSTATION -> Role.WORKSTATION;
                default -> null;
            };
            if (requiredRole == null) {
                continue;
            }
            boolean found = false;
            for (Voxel featureCell : target.featureCells()) {
                Cell cell = cells.get(featureCell);
                if (cell != null
                        && cell.role() == requiredRole
                        && featureCell.y() == target.accessCell().y()
                        && horizontalDistance(featureCell, target.accessCell()) == 1) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                issues.add(new Issue(
                        Code.ACCESS_TARGET_FEATURE_MISSING,
                        target.id(),
                        target.featureCells().stream().sorted().findFirst().orElse(null),
                        "Target does not reference an adjacent authored " + requiredRole
                                + " cell"));
            }
        }
    }

    private static void validateSupport(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            List<Issue> issues) {
        Map<Voxel, Cell> structural = new HashMap<>();
        cells.forEach((position, cell) -> {
            if (cell.transmitsSupport()) {
                structural.put(position, cell);
            }
        });

        Set<Voxel> validAnchors = new HashSet<>();
        for (Voxel anchor : blueprint.supportAnchors()) {
            if (structural.containsKey(anchor)) {
                validAnchors.add(anchor);
            } else {
                issues.add(new Issue(
                        Code.SUPPORT_EXPECTATION_UNMET,
                        "support anchor",
                        anchor,
                        "Support anchor does not contain a load-bearing cell"));
            }
        }
        Set<Voxel> grounded = traverseStructural(structural, validAnchors);

        Set<Voxel> remaining = new HashSet<>(structural.keySet());
        remaining.removeAll(grounded);
        while (!remaining.isEmpty()) {
            Voxel sample = remaining.stream().min(Voxel::compareTo).orElseThrow();
            Set<Voxel> component = traverseStructural(structural, Set.of(sample));
            component.retainAll(remaining);
            remaining.removeAll(component);
            issues.add(new Issue(
                    Code.UNSUPPORTED_COMPONENT,
                    "structure",
                    sample,
                    "A disconnected load-bearing component of " + component.size()
                            + " cell(s) has no foundation anchor"));
        }

        for (SupportExpectation expectation : blueprint.supportExpectations()) {
            Set<Voxel> anchors = expectation.permittedAnchors().isEmpty()
                    ? validAnchors
                    : intersection(expectation.permittedAnchors(), validAnchors);
            Set<Voxel> supported = traverseStructural(structural, anchors);
            if (!structural.containsKey(expectation.subject())
                    || !supported.contains(expectation.subject())) {
                issues.add(new Issue(
                        Code.SUPPORT_EXPECTATION_UNMET,
                        expectation.id(),
                        expectation.subject(),
                        "Expected component has no load-bearing route to an allowed anchor"));
            }
        }
    }

    private static boolean hasHeadroom(Map<Voxel, Cell> cells, Voxel foot) {
        Cell feet = cells.get(foot);
        Cell head = cells.get(foot.offset(0, 1, 0));
        return (feet == null || !feet.blocksMovement())
                && (head == null || !head.blocksMovement());
    }

    private static boolean isAuthoredFloorPosition(
            WholeBuildingBlueprint blueprint,
            Map<Voxel, Cell> cells,
            Voxel foot) {
        if (!blueprint.interiorCells().contains(foot.offset(0, 1, 0))
                || !hasHeadroom(cells, foot)) {
            return false;
        }
        Cell support = cells.get(foot.offset(0, -1, 0));
        return support != null && switch (support.role()) {
            case FOUNDATION, FLOOR, STAIR -> true;
            default -> false;
        };
    }

    private static boolean validEntranceGeometry(Entrance entrance) {
        return horizontalDistance(entrance.interiorAccess(), entrance.portal()) == 1
                && horizontalDistance(entrance.portal(), entrance.exteriorAccess()) == 1
                && entrance.interiorAccess().y() == entrance.portal().y()
                && entrance.portal().y() == entrance.exteriorAccess().y();
    }

    private static int horizontalDistance(Voxel first, Voxel second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
    }

    private static boolean validTraversalLink(TraversalLink link) {
        int dx = Math.abs(link.from().x() - link.to().x());
        int dy = Math.abs(link.from().y() - link.to().y());
        int dz = Math.abs(link.from().z() - link.to().z());
        return dy == 1 && dx + dz <= 1;
    }

    private static Set<Voxel> traverse(Map<Voxel, Set<Voxel>> graph, Set<Voxel> seeds) {
        Set<Voxel> visited = new HashSet<>();
        ArrayDeque<Voxel> pending = new ArrayDeque<>();
        for (Voxel seed : sorted(seeds)) {
            if (graph.containsKey(seed) && visited.add(seed)) {
                pending.add(seed);
            }
        }
        while (!pending.isEmpty()) {
            Voxel position = pending.removeFirst();
            for (Voxel neighbor : graph.getOrDefault(position, Set.of())) {
                if (visited.add(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        return visited;
    }

    private static Set<Voxel> traverseStructural(
            Map<Voxel, Cell> structural,
            Set<Voxel> seeds) {
        Set<Voxel> visited = new HashSet<>();
        ArrayDeque<Voxel> pending = new ArrayDeque<>();
        for (Voxel seed : sorted(seeds)) {
            if (structural.containsKey(seed) && visited.add(seed)) {
                pending.add(seed);
            }
        }
        while (!pending.isEmpty()) {
            Voxel position = pending.removeFirst();
            for (int[] direction : ORTHOGONAL_DIRECTIONS) {
                Voxel neighbor = position.offset(direction[0], direction[1], direction[2]);
                if (structural.containsKey(neighbor) && visited.add(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
            // Stair/slab roof courses commonly meet along a one-up, one-across slope rather than
            // through full block faces. Treat only roof-like cells as connected this way; broad
            // diagonal connectivity would let an actually floating chimney pass admission.
            Cell current = structural.get(position);
            if (isSlopedRoofCell(current)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    for (int[] direction : HORIZONTAL_DIRECTIONS) {
                        Voxel neighbor = position.offset(direction[0], dy, direction[2]);
                        if (isSlopedRoofCell(structural.get(neighbor))
                                && visited.add(neighbor)) {
                            pending.addLast(neighbor);
                        }
                    }
                }
            }
        }
        return visited;
    }

    private static boolean isSlopedRoofCell(Cell cell) {
        return cell != null
                && (cell.role() == Role.ROOF
                        || cell.shape() == WholeBuildingBlueprint.ShapeCategory.STAIR
                        || cell.shape() == WholeBuildingBlueprint.ShapeCategory.SLAB);
    }

    private static Set<Voxel> intersection(Set<Voxel> first, Set<Voxel> second) {
        Set<Voxel> result = new HashSet<>(first);
        result.retainAll(second);
        return result;
    }

    private static List<Voxel> sorted(Set<Voxel> positions) {
        return positions.stream().sorted().toList();
    }
}
