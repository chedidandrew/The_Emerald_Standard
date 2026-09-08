package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure deterministic planning for prosperity-building variants, upgrades, and trail routes. */
public final class VillageStructureProgression {
    public static final int VARIANT_COUNT = 3;
    public static final int MAX_VISUAL_STAGE = 2;

    private VillageStructureProgression() {
    }

    /** Selects a save-stable preset without consuming mutable world randomness. */
    public static int variant(
            UUID villageId, long projectId, VillageProsperityEngine.ProjectType type) {
        long villageSalt = villageId == null
                ? 0L
                : villageId.getMostSignificantBits()
                        ^ Long.rotateLeft(villageId.getLeastSignificantBits(), 29);
        long typeSalt = type == null ? 0L : (long) (type.ordinal() + 1) * 0xD1B54A32D192ED03L;
        return Math.floorMod((int) mix64(villageSalt ^ projectId ^ typeSalt), VARIANT_COUNT);
    }

    /** Tier 2 adds the established-village pass; tier 4 adds the town/city pass. */
    public static int desiredVisualStage(int developmentTier) {
        if (developmentTier >= 4) {
            return 2;
        }
        return developmentTier >= 2 ? 1 : 0;
    }

    /**
     * Keeps an already-authored stage after a temporary village-tier decline. The persisted
     * template size is the durable marker, so old saves need no new required field.
     */
    public static int targetVisualStage(
            int developmentTier,
            int persistedTemplateBlocks,
            int baselineBlocks,
            int stageOneBlocks,
            int stageTwoBlocks) {
        if (baselineBlocks < 0
                || stageOneBlocks < baselineBlocks
                || stageTwoBlocks < stageOneBlocks) {
            throw new IllegalArgumentException("Visual-stage template sizes must be monotonic");
        }
        int persistedStage = persistedTemplateBlocks >= stageTwoBlocks
                ? 2
                : persistedTemplateBlocks >= stageOneBlocks ? 1 : 0;
        return Math.max(persistedStage, desiredVisualStage(developmentTier));
    }

    /**
     * Freezes a reserved incomplete prefix. Only an unreserved plan or a completed structure may
     * expose a newly desired append-only stage for full preflight and atomic commitment.
     */
    public static int constructionVisualStage(
            int developmentTier,
            int persistedTemplateBlocks,
            int baselineBlocks,
            int stageOneBlocks,
            int stageTwoBlocks,
            boolean reserved,
            boolean materializedComplete) {
        return targetVisualStage(
                reserved && !materializedComplete ? 0 : developmentTier,
                persistedTemplateBlocks,
                baselineBlocks,
                stageOneBlocks,
                stageTwoBlocks);
    }

    /**
     * Produces a connected shortest trail with deterministic, irregular turns and sparse shoulders.
     * Coordinates are caller-defined (world or structure-relative) and no world state is read.
     */
    public static List<TrailCell> trail(
            int startX,
            int startZ,
            int targetX,
            int targetZ,
            long seed,
            int maximumPrimarySteps) {
        if (maximumPrimarySteps < 0) {
            throw new IllegalArgumentException("maximumPrimarySteps must be non-negative");
        }
        long distance = Math.abs((long) targetX - startX)
                + Math.abs((long) targetZ - startZ);
        if (distance > maximumPrimarySteps) {
            return List.of();
        }

        List<TrailCell> primary = new ArrayList<>((int) distance + 1);
        int x = startX;
        int z = startZ;
        primary.add(new TrailCell(x, z, false));
        int step = 0;
        while (x != targetX || z != targetZ) {
            int remainingX = Math.abs(targetX - x);
            int remainingZ = Math.abs(targetZ - z);
            boolean moveX;
            if (remainingX == 0) {
                moveX = false;
            } else if (remainingZ == 0) {
                moveX = true;
            } else {
                long choice = mix64(seed
                        ^ (long) x * 0x9E3779B97F4A7C15L
                        ^ (long) z * 0xC2B2AE3D27D4EB4FL
                        ^ (long) step * 0x165667B19E3779F9L);
                moveX = Math.floorMod(choice, remainingX + remainingZ) < remainingX;
            }
            if (moveX) {
                x += Integer.signum(targetX - x);
            } else {
                z += Integer.signum(targetZ - z);
            }
            primary.add(new TrailCell(x, z, false));
            step++;
        }

        Set<Long> primaryPositions = new HashSet<>();
        for (TrailCell cell : primary) {
            primaryPositions.add(pack(cell.x, cell.z));
        }
        Set<Long> emitted = new HashSet<>();
        List<TrailCell> result = new ArrayList<>(primary.size() + primary.size() / 7);
        for (int index = 0; index < primary.size(); index++) {
            TrailCell cell = primary.get(index);
            if (emitted.add(pack(cell.x, cell.z))) {
                result.add(cell);
            }
            if (index < 2 || index >= primary.size() - 2) {
                continue;
            }
            long detail = mix64(seed ^ (long) index * 0x94D049BB133111EBL);
            if (Math.floorMod(detail, 7) != 0) {
                continue;
            }
            TrailCell previous = primary.get(index - 1);
            int shoulderX = cell.x;
            int shoulderZ = cell.z;
            int side = (detail & 8L) == 0L ? -1 : 1;
            if (previous.x != cell.x) {
                shoulderZ += side;
            } else {
                shoulderX += side;
            }
            long packed = pack(shoulderX, shoulderZ);
            if (!primaryPositions.contains(packed) && emitted.add(packed)) {
                result.add(new TrailCell(shoulderX, shoulderZ, true));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Plans the two shoulder lanes that make a primary route three blocks wide.
     *
     * <p>Each primary edge contributes the perpendicular cells on both sides of both endpoints.
     * Taking the stable union fills straight sections, endpoints, and inside/outside turns without
     * changing the primary route. Callers append these cells after their legacy trail plan so an
     * already-persisted cursor remains an immutable prefix.</p>
     */
    public static List<TrailCell> threeWideShoulders(List<TrailCell> trail) {
        if (trail == null) {
            throw new IllegalArgumentException("trail must not be null");
        }
        List<TrailCell> primary = trail.stream()
                .filter(cell -> !cell.shoulder())
                .toList();
        if (primary.size() < 2) {
            return List.of();
        }

        Set<Long> primaryPositions = new HashSet<>();
        for (TrailCell cell : primary) {
            primaryPositions.add(pack(cell.x, cell.z));
        }
        Set<Long> emitted = new LinkedHashSet<>();
        List<TrailCell> shoulders = new ArrayList<>(primary.size() * 2 + 4);
        for (int index = 1; index < primary.size(); index++) {
            TrailCell previous = primary.get(index - 1);
            TrailCell current = primary.get(index);
            int dx = current.x - previous.x;
            int dz = current.z - previous.z;
            if (Math.abs(dx) + Math.abs(dz) != 1) {
                throw new IllegalArgumentException(
                        "Primary trail must contain connected cardinal steps");
            }
            int shoulderX = -dz;
            int shoulderZ = dx;
            appendShoulder(
                    shoulders,
                    emitted,
                    primaryPositions,
                    previous.x + shoulderX,
                    previous.z + shoulderZ);
            appendShoulder(
                    shoulders,
                    emitted,
                    primaryPositions,
                    previous.x - shoulderX,
                    previous.z - shoulderZ);
            appendShoulder(
                    shoulders,
                    emitted,
                    primaryPositions,
                    current.x + shoulderX,
                    current.z + shoulderZ);
            appendShoulder(
                    shoulders,
                    emitted,
                    primaryPositions,
                    current.x - shoulderX,
                    current.z - shoulderZ);
        }
        return List.copyOf(shoulders);
    }

    private static void appendShoulder(
            List<TrailCell> result,
            Set<Long> emitted,
            Set<Long> primaryPositions,
            int x,
            int z) {
        long packed = pack(x, z);
        if (!primaryPositions.contains(packed) && emitted.add(packed)) {
            result.add(new TrailCell(x, z, true));
        }
    }

    /** Stable avalanche mixer; its result is part of the generated-world compatibility contract. */
    static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    static long trailDetail(long seed, int x, int z) {
        return mix64(
                seed ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
    }

    public record TrailCell(int x, int z, boolean shoulder) {
    }
}
