package com.chedidandrew.emeraldstandard.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-neutral contract for a complete, authored building.
 *
 * <p>The Minecraft adapters are expected to translate structure-template markers and block states
 * into this vocabulary before a template is admitted to the production catalog. Coordinates are
 * local to the template. {@code interiorCells} describe the complete volume enclosed by the outer
 * shell, including cells occupied by furniture or internal partitions; {@code circulationCells}
 * are the foot positions a villager must be able to traverse.</p>
 *
 * <p>Stages are ordered append-only layers. A later stage may add detail outside or inside the
 * original building, but may never rewrite or remove a cell from an earlier stage. This preserves
 * the materialization cursor used by saved projects.</p>
 */
public record WholeBuildingBlueprint(
        String schema,
        String id,
        Bounds bounds,
        ShellPolicy shellPolicy,
        List<Stage> stages,
        Set<Voxel> interiorCells,
        Set<Voxel> circulationCells,
        List<TraversalLink> traversalLinks,
        List<Entrance> entrances,
        List<AccessTarget> accessTargets,
        List<ReservedClearance> reservedClearances,
        Set<Voxel> supportAnchors,
        List<SupportExpectation> supportExpectations,
        Map<String, PaletteSlot> paletteSlots) {
    public static final String AUTHORED_V2_SCHEMA = "authored_v2";

    public WholeBuildingBlueprint {
        schema = requireName(schema, "schema");
        id = requireName(id, "blueprint id");
        bounds = Objects.requireNonNull(bounds, "bounds");
        shellPolicy = Objects.requireNonNull(shellPolicy, "shellPolicy");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
        interiorCells = Set.copyOf(Objects.requireNonNull(interiorCells, "interiorCells"));
        circulationCells = Set.copyOf(
                Objects.requireNonNull(circulationCells, "circulationCells"));
        traversalLinks = List.copyOf(
                Objects.requireNonNull(traversalLinks, "traversalLinks"));
        entrances = List.copyOf(Objects.requireNonNull(entrances, "entrances"));
        accessTargets = List.copyOf(
                Objects.requireNonNull(accessTargets, "accessTargets"));
        reservedClearances = List.copyOf(
                Objects.requireNonNull(reservedClearances, "reservedClearances"));
        supportAnchors = Set.copyOf(
                Objects.requireNonNull(supportAnchors, "supportAnchors"));
        supportExpectations = List.copyOf(
                Objects.requireNonNull(supportExpectations, "supportExpectations"));
        paletteSlots = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(paletteSlots, "paletteSlots")));
    }

    /** Inclusive local-coordinate bounds, including any reserved exterior entrance approach. */
    public record Bounds(Voxel minimum, Voxel maximum) {
        public Bounds {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            if (minimum.x() > maximum.x()
                    || minimum.y() > maximum.y()
                    || minimum.z() > maximum.z()) {
                throw new IllegalArgumentException("Blueprint bounds are inverted");
            }
        }

        public boolean contains(Voxel voxel) {
            return voxel != null
                    && voxel.x() >= minimum.x() && voxel.x() <= maximum.x()
                    && voxel.y() >= minimum.y() && voxel.y() <= maximum.y()
                    && voxel.z() >= minimum.z() && voxel.z() <= maximum.z();
        }
    }

    public record Voxel(int x, int y, int z) implements Comparable<Voxel> {
        public Voxel offset(int dx, int dy, int dz) {
            return new Voxel(x + dx, y + dy, z + dz);
        }

        @Override
        public int compareTo(Voxel other) {
            int compared = Integer.compare(y, other.y);
            if (compared != 0) {
                return compared;
            }
            compared = Integer.compare(z, other.z);
            return compared != 0 ? compared : Integer.compare(x, other.x);
        }
    }

    public enum ShellPolicy {
        /** A house-like template whose declared interior must be fully weather-sealed. */
        ENCLOSED,
        /** A market, pavilion, mine mouth, or other deliberately open-air template. */
        OPEN_AIR
    }

    /** Shape/property family that must survive a biome-palette substitution. */
    public enum ShapeCategory {
        FULL_BLOCK,
        STAIR,
        SLAB,
        PANE,
        DOOR,
        FENCE,
        WALL,
        LIGHT,
        BED,
        WORKSTATION,
        DECORATION
    }

    public enum Role {
        FOUNDATION,
        FLOOR,
        WALL,
        ROOF,
        WINDOW,
        DOOR,
        SUPPORT,
        STAIR,
        BED,
        WORKSTATION,
        FURNITURE,
        LIGHT,
        DECORATION
    }

    public enum TargetKind {
        BED,
        WORKSTATION,
        UPPER_FLOOR,
        ESSENTIAL,
        OPTIONAL
    }

    /**
     * Authored semantic cell. Collision and weather sealing intentionally remain independent:
     * a closed door seals the shell but is still a permitted navigation portal.
     */
    public record Cell(
            Role role,
            ShapeCategory shape,
            String paletteSlot,
            boolean blocksMovement,
            boolean sealsExterior,
            boolean transmitsSupport) {
        public Cell {
            role = Objects.requireNonNull(role, "role");
            shape = Objects.requireNonNull(shape, "shape");
            paletteSlot = requireName(paletteSlot, "palette slot reference");
        }
    }

    public record Placement(Voxel position, Cell cell) {
        public Placement {
            position = Objects.requireNonNull(position, "position");
            cell = Objects.requireNonNull(cell, "cell");
        }
    }

    /** An append-only layer. Any removal is retained as invalid input so validation can report it. */
    public record Stage(String id, List<Placement> additions, Set<Voxel> removals) {
        public Stage {
            id = requireName(id, "stage id");
            additions = List.copyOf(Objects.requireNonNull(additions, "additions"));
            removals = Set.copyOf(Objects.requireNonNull(removals, "removals"));
        }
    }

    /** A deliberately authored vertical transition; horizontal movement is inferred. */
    public record TraversalLink(Voxel from, Voxel to) {
        public TraversalLink {
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }
    }

    /**
     * Three-cell entrance route. The portal normally contains a passable, weather-sealing door;
     * the exterior cell is inside template bounds so terrain adaptation can reserve it.
     */
    public record Entrance(
            String id,
            Voxel interiorAccess,
            Voxel portal,
            Voxel exteriorAccess) {
        public Entrance {
            id = requireName(id, "entrance id");
            interiorAccess = Objects.requireNonNull(interiorAccess, "interiorAccess");
            portal = Objects.requireNonNull(portal, "portal");
            exteriorAccess = Objects.requireNonNull(exteriorAccess, "exteriorAccess");
        }
    }

    /** The access cell is where an actor stands; feature cells contain the bed or workstation. */
    public record AccessTarget(
            String id,
            TargetKind kind,
            Voxel accessCell,
            Set<Voxel> featureCells) {
        public AccessTarget {
            id = requireName(id, "access target id");
            kind = Objects.requireNonNull(kind, "kind");
            accessCell = Objects.requireNonNull(accessCell, "accessCell");
            featureCells = Set.copyOf(Objects.requireNonNull(featureCells, "featureCells"));
        }
    }

    /** Explicit volume that furnishings and palette replacements may not obstruct. */
    public record ReservedClearance(String id, Set<Voxel> cells) {
        public ReservedClearance {
            id = requireName(id, "reserved-clearance id");
            cells = Set.copyOf(Objects.requireNonNull(cells, "cells"));
        }
    }

    /** A key authored component that must load-bear to the specified foundation anchors. */
    public record SupportExpectation(
            String id,
            Voxel subject,
            Set<Voxel> permittedAnchors) {
        public SupportExpectation {
            id = requireName(id, "support-expectation id");
            subject = Objects.requireNonNull(subject, "subject");
            permittedAnchors = Set.copyOf(
                    Objects.requireNonNull(permittedAnchors, "permittedAnchors"));
        }
    }

    public record PaletteMaterial(String id, ShapeCategory shape) {
        public PaletteMaterial {
            id = requireName(id, "palette material id");
            shape = Objects.requireNonNull(shape, "shape");
        }
    }

    /** A semantic palette slot and all block-state families that may fill it. */
    public record PaletteSlot(
            String id,
            ShapeCategory requiredShape,
            List<PaletteMaterial> materials) {
        public PaletteSlot {
            id = requireName(id, "palette slot id");
            requiredShape = Objects.requireNonNull(requiredShape, "requiredShape");
            materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        }
    }

    private static String requireName(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
