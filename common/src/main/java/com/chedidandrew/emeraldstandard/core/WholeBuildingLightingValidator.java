package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Loader-neutral block-light safety gate for authored building interiors.
 *
 * <p>Modern hostile-spawn rules require block light level zero. Consequently, an eligible floor
 * position is spawn-safe when authored emitted block light reaches it at level one or greater.
 * This validator deliberately supplies no skylight: an enclosed building must remain safe at
 * midnight and during storms, regardless of windows or biome.</p>
 *
 * <p>The adapter supplies the actual light emission and light dampening of every authored block
 * state. Propagation follows Minecraft's conservative integer rule: light loses at least one level
 * per orthogonal step and additionally respects the destination block's dampening. Shape-specific
 * shortcuts are intentionally omitted, so a template accepted here cannot depend on light leaking
 * through an uncertain stair, slab, door, or decorative collision shape.</p>
 */
public final class WholeBuildingLightingValidator {
    public static final int MINIMUM_SPAWN_SAFE_BLOCK_LIGHT = 1;
    /** Warm, readable interiors should not merely sit one level above the spawn threshold. */
    public static final int MINIMUM_COMFORTABLE_BLOCK_LIGHT = 7;
    public static final int MAXIMUM_BLOCK_LIGHT = 15;

    private static final int[][] DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private WholeBuildingLightingValidator() {
    }

    public enum Code {
        INVALID_SNAPSHOT,
        SPAWN_UNSAFE_TRAVERSABLE_FLOOR,
        DIM_TRAVERSABLE_FLOOR
    }

    /** Inclusive propagation envelope. */
    public record Bounds(Voxel minimum, Voxel maximum) {
        public Bounds {
            minimum = Objects.requireNonNull(minimum, "minimum");
            maximum = Objects.requireNonNull(maximum, "maximum");
            if (minimum.x() > maximum.x()
                    || minimum.y() > maximum.y()
                    || minimum.z() > maximum.z()) {
                throw new IllegalArgumentException("Lighting bounds are inverted");
            }
        }

        public boolean contains(Voxel position) {
            return position != null
                    && position.x() >= minimum.x() && position.x() <= maximum.x()
                    && position.y() >= minimum.y() && position.y() <= maximum.y()
                    && position.z() >= minimum.z() && position.z() <= maximum.z();
        }
    }

    public record LightEmitter(Voxel position, int emission) {
        public LightEmitter {
            position = Objects.requireNonNull(position, "position");
            if (emission < 1 || emission > MAXIMUM_BLOCK_LIGHT) {
                throw new IllegalArgumentException(
                        "Light emission must be between 1 and " + MAXIMUM_BLOCK_LIGHT);
            }
        }
    }

    /**
     * Complete final-state lighting input. Floor positions are actor foot cells, not floor blocks.
     * Dampening zero is equivalent to air; values at or above fifteen are fully opaque to a
     * neighboring source under the conservative propagation model.
     */
    public record LightingSnapshot(
            String id,
            Bounds bounds,
            Set<Voxel> traversableInteriorFloors,
            Map<Voxel, Integer> lightDampening,
            List<LightEmitter> emitters) {
        public LightingSnapshot {
            id = Objects.requireNonNull(id, "id").trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Lighting snapshot id may not be blank");
            }
            bounds = Objects.requireNonNull(bounds, "bounds");
            traversableInteriorFloors = Set.copyOf(Objects.requireNonNull(
                    traversableInteriorFloors, "traversableInteriorFloors"));
            lightDampening = Map.copyOf(Objects.requireNonNull(
                    lightDampening, "lightDampening"));
            emitters = List.copyOf(Objects.requireNonNull(emitters, "emitters"));
        }
    }

    public record Issue(Code code, Voxel position, int blockLight, String message) {
        public Issue {
            code = Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record ValidationReport(
            String snapshotId,
            Map<Voxel, Integer> blockLight,
            List<Issue> issues) {
        public ValidationReport {
            snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
            blockLight = Map.copyOf(Objects.requireNonNull(blockLight, "blockLight"));
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        }

        public boolean spawnSafe() {
            return issues.stream().noneMatch(issue -> issue.code() == Code.INVALID_SNAPSHOT
                    || issue.code() == Code.SPAWN_UNSAFE_TRAVERSABLE_FLOOR);
        }

        public boolean comfortablyLit() {
            return issues.isEmpty();
        }

        public int blockLightAt(Voxel position) {
            return blockLight.getOrDefault(position, 0);
        }

        public long count(Code code) {
            return issues.stream().filter(issue -> issue.code() == code).count();
        }

        public void requireSpawnSafe() {
            if (spawnSafe()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Spawn-unsafe authored lighting in '").append(snapshotId).append("':");
            for (Issue issue : issues) {
                message.append(System.lineSeparator())
                        .append(" - ")
                        .append(issue.code());
                if (issue.position() != null) {
                    message.append(" at ").append(issue.position());
                }
                message.append(": ").append(issue.message());
            }
            throw new IllegalArgumentException(message.toString());
        }

        public void requireComfortablyLit() {
            if (comfortablyLit()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Insufficient authored interior lighting in '")
                    .append(snapshotId)
                    .append("':");
            for (Issue issue : issues) {
                message.append(System.lineSeparator())
                        .append(" - ")
                        .append(issue.code());
                if (issue.position() != null) {
                    message.append(" at ").append(issue.position());
                }
                message.append(": ").append(issue.message());
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    public static ValidationReport validate(LightingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<Issue> issues = new ArrayList<>();
        validateEnvelope(snapshot, issues);

        Map<Voxel, Integer> light = propagate(snapshot);
        List<Voxel> floors = new ArrayList<>(snapshot.traversableInteriorFloors());
        floors.sort(Comparator.naturalOrder());
        for (Voxel floor : floors) {
            int blockLight = light.getOrDefault(floor, 0);
            if (blockLight < MINIMUM_SPAWN_SAFE_BLOCK_LIGHT) {
                issues.add(new Issue(
                        Code.SPAWN_UNSAFE_TRAVERSABLE_FLOOR,
                        floor,
                        blockLight,
                        "Eligible interior floor receives block light " + blockLight
                                + "; hostile spawning requires block light zero, so authored "
                                + "coverage must reach at least "
                                + MINIMUM_SPAWN_SAFE_BLOCK_LIGHT));
            } else if (blockLight < MINIMUM_COMFORTABLE_BLOCK_LIGHT) {
                issues.add(new Issue(
                        Code.DIM_TRAVERSABLE_FLOOR,
                        floor,
                        blockLight,
                        "Eligible interior floor is spawn-safe at block light " + blockLight
                                + " but remains visually dim; authored comfort coverage must "
                                + "reach at least " + MINIMUM_COMFORTABLE_BLOCK_LIGHT));
            }
        }
        issues.sort(Comparator
                .comparing((Issue issue) -> issue.code().ordinal())
                .thenComparing(issue -> issue.position() == null
                        ? new Voxel(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE)
                        : issue.position()));
        return new ValidationReport(snapshot.id(), light, issues);
    }

    private static void validateEnvelope(LightingSnapshot snapshot, List<Issue> issues) {
        for (Voxel floor : snapshot.traversableInteriorFloors()) {
            if (!snapshot.bounds().contains(floor)) {
                issues.add(new Issue(
                        Code.INVALID_SNAPSHOT,
                        floor,
                        0,
                        "Traversable floor lies outside the propagation bounds"));
            }
        }
        for (Map.Entry<Voxel, Integer> entry : snapshot.lightDampening().entrySet()) {
            Integer dampening = entry.getValue();
            if (!snapshot.bounds().contains(entry.getKey())
                    || dampening == null
                    || dampening < 0
                    || dampening > MAXIMUM_BLOCK_LIGHT) {
                issues.add(new Issue(
                        Code.INVALID_SNAPSHOT,
                        entry.getKey(),
                        0,
                        "Light dampening must be 0..15 and lie inside the propagation bounds"));
            }
        }
        for (LightEmitter emitter : snapshot.emitters()) {
            if (!snapshot.bounds().contains(emitter.position())) {
                issues.add(new Issue(
                        Code.INVALID_SNAPSHOT,
                        emitter.position(),
                        0,
                        "Light emitter lies outside the propagation bounds"));
            }
        }
    }

    private static Map<Voxel, Integer> propagate(LightingSnapshot snapshot) {
        Map<Voxel, Integer> light = new HashMap<>();
        PriorityQueue<LightNode> frontier = new PriorityQueue<>(Comparator
                .comparingInt(LightNode::level)
                .reversed()
                .thenComparing(LightNode::position));
        for (LightEmitter emitter : snapshot.emitters()) {
            if (!snapshot.bounds().contains(emitter.position())) {
                continue;
            }
            int previous = light.getOrDefault(emitter.position(), 0);
            if (emitter.emission() > previous) {
                light.put(emitter.position(), emitter.emission());
                frontier.add(new LightNode(emitter.position(), emitter.emission()));
            }
        }

        while (!frontier.isEmpty()) {
            LightNode current = frontier.remove();
            if (current.level() != light.getOrDefault(current.position(), 0)) {
                continue;
            }
            for (int[] direction : DIRECTIONS) {
                Voxel next = current.position().offset(direction[0], direction[1], direction[2]);
                if (!snapshot.bounds().contains(next)) {
                    continue;
                }
                int loss = Math.max(1, snapshot.lightDampening().getOrDefault(next, 0));
                int nextLevel = current.level() - loss;
                if (nextLevel <= light.getOrDefault(next, 0)) {
                    continue;
                }
                light.put(next, nextLevel);
                frontier.add(new LightNode(next, nextLevel));
            }
        }
        return light;
    }

    private record LightNode(Voxel position, int level) {
    }
}
