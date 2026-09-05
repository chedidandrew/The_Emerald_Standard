package com.chedidandrew.emeraldstandard.core;

import java.util.List;
import java.util.UUID;

/**
 * Save-stable architectural choices for prosperity buildings.
 *
 * <p>The physical Minecraft integration turns these compact choices into block placements. Keeping
 * selection here makes the procedural contract loader-neutral, deterministic, and easy to test.
 * A released schema is immutable: future art changes must use a new schema identifier.</p>
 */
public final class VillageArchitecture {
    public static final String LEGACY_SCHEMA = "legacy_v1";
    public static final String MODULAR_SCHEMA = "modular_v1";
    public static final int SILHOUETTE_COUNT = 3;
    public static final int ROOF_COUNT = 3;
    public static final int FRONTAGE_COUNT = 3;

    private static final long TYPE_SALT = 0xD1B54A32D192ED03L;
    private static final long SILHOUETTE_SALT = 0x9E3779B97F4A7C15L;
    private static final long ROOF_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long FRONTAGE_SALT = 0x165667B19E3779F9L;

    private VillageArchitecture() {
    }

    public enum Character {
        AGRARIAN("agrarian"),
        MERCANTILE("mercantile"),
        RUSTIC("rustic"),
        FORMAL("formal");

        private final String id;

        Character(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Character fromId(String id) {
            for (Character character : values()) {
                if (character.id.equals(id)) {
                    return character;
                }
            }
            throw new IllegalArgumentException("Unknown village architectural character: " + id);
        }
    }

    public enum BiomeDialect {
        PLAINS("plains"),
        DESERT("desert"),
        SAVANNA("savanna"),
        TAIGA("taiga"),
        SNOWY("snowy");

        private final String id;

        BiomeDialect(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static BiomeDialect fromId(String id) {
            for (BiomeDialect dialect : values()) {
                if (dialect.id.equals(id)) {
                    return dialect;
                }
            }
            throw new IllegalArgumentException("Unknown village architectural dialect: " + id);
        }
    }

    /** A previous saved choice used to keep a village from visibly repeating itself. */
    public record ExistingDesign(
            VillageProsperityEngine.ProjectType type,
            int silhouette,
            int roof,
            int frontage,
            boolean mirrored,
            long signature) {
    }

    /** Compact immutable choice expanded by the Minecraft-side authored module grammar. */
    public record Recipe(
            long seed,
            int silhouette,
            int roof,
            int frontage,
            boolean mirrored,
            long signature) {
        public Recipe {
            if (silhouette < 0 || silhouette >= SILHOUETTE_COUNT
                    || roof < 0 || roof >= ROOF_COUNT
                    || frontage < 0 || frontage >= FRONTAGE_COUNT) {
                throw new IllegalArgumentException("Architectural recipe choice out of range");
            }
        }
    }

    /** Selects one shared visual temperament for the whole settlement. */
    public static Character character(UUID villageId) {
        long seed = villageSalt(villageId) ^ 0xA24BAED4963EE407L;
        return Character.values()[Math.floorMod((int) mix64(seed), Character.values().length)];
    }

    /**
     * Chooses a deterministic authored composition. Silhouettes form a shuffle bag per building
     * type: every least-used family is consumed before a more-used family can repeat.
     */
    public static Recipe choose(
            UUID villageId,
            long projectId,
            VillageProsperityEngine.ProjectType type,
            List<ExistingDesign> existingDesigns) {
        if (type == null) {
            throw new IllegalArgumentException("Project type is required");
        }
        List<ExistingDesign> existing = existingDesigns == null ? List.of() : existingDesigns;
        long seed = mix64(villageSalt(villageId)
                ^ projectId
                ^ (long) stableTypeId(type) * TYPE_SALT);

        int[] uses = new int[SILHOUETTE_COUNT];
        ExistingDesign mostRecent = null;
        for (ExistingDesign design : existing) {
            if (design == null) {
                continue;
            }
            mostRecent = design;
            if (design.type == type
                    && design.silhouette >= 0
                    && design.silhouette < SILHOUETTE_COUNT) {
                uses[design.silhouette]++;
            }
        }
        int minimumUses = Math.min(uses[0], Math.min(uses[1], uses[2]));
        int silhouetteStart = Math.floorMod((int) mix64(seed ^ SILHOUETTE_SALT),
                SILHOUETTE_COUNT);
        int silhouette = firstLeastUsed(uses, minimumUses, silhouetteStart);

        int roof = Math.floorMod((int) mix64(seed ^ ROOF_SALT), ROOF_COUNT);
        int frontage = Math.floorMod((int) mix64(seed ^ FRONTAGE_SALT), FRONTAGE_COUNT);
        boolean mirrored = (mix64(seed ^ 0x94D049BB133111EBL) & 1L) != 0L;
        long signature = signature(type, silhouette, roof, frontage, mirrored);

        // The newest projects are normally nearest in the finished village. Never allow their
        // complete visual signatures to match, even when building types differ.
        if (mostRecent != null
                && mostRecent.silhouette == silhouette
                && mostRecent.roof == roof
                && mostRecent.frontage == frontage
                && mostRecent.mirrored == mirrored) {
            frontage = (frontage + 1) % FRONTAGE_COUNT;
            signature = signature(type, silhouette, roof, frontage, mirrored);
        }
        return new Recipe(seed, silhouette, roof, frontage, mirrored, signature);
    }

    /** Local north is quarter-turn 0; rotations proceed clockwise toward the village/road. */
    public static int rotationToward(
            int siteCenterX, int siteCenterZ, int targetX, int targetZ) {
        long dx = (long) targetX - siteCenterX;
        long dz = (long) targetZ - siteCenterZ;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0L ? 1 : 3;
        }
        return dz >= 0L ? 2 : 0;
    }

    public static long signature(
            VillageProsperityEngine.ProjectType type,
            int silhouette,
            int roof,
            int frontage,
            boolean mirrored) {
        long packed = ((long) stableTypeId(type) << 32)
                ^ ((long) silhouette << 24)
                ^ ((long) roof << 16)
                ^ ((long) frontage << 8)
                ^ (mirrored ? 1L : 0L);
        return mix64(packed ^ 0xDB4F0B9175AE2165L);
    }

    public static boolean isKnownSchema(String schema) {
        return LEGACY_SCHEMA.equals(schema) || MODULAR_SCHEMA.equals(schema);
    }

    public static boolean isKnownCharacter(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (Character character : Character.values()) {
            if (character.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isKnownDialect(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (BiomeDialect dialect : BiomeDialect.values()) {
            if (dialect.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static int firstLeastUsed(int[] uses, int minimumUses, int start) {
        for (int offset = 0; offset < uses.length; offset++) {
            int candidate = (start + offset) % uses.length;
            if (uses[candidate] == minimumUses) {
                return candidate;
            }
        }
        throw new IllegalStateException("No architectural silhouette candidate");
    }

    private static long villageSalt(UUID villageId) {
        return villageId == null
                ? 0L
                : villageId.getMostSignificantBits()
                        ^ Long.rotateLeft(villageId.getLeastSignificantBits(), 29);
    }

    private static int stableTypeId(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE -> 1;
            case HOUSE -> 2;
            case INN -> 3;
            case WAREHOUSE -> 4;
            case MINE_ENTRANCE -> 5;
            case MARKET_SQUARE -> 6;
            case SMITHY -> 7;
            case GRANARY -> 8;
            case GUARD_POST -> 9;
            case EXCHANGE_HALL -> 10;
        };
    }

    /** Stable avalanche mixer; its output is part of the modular-v1 world contract. */
    public static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
