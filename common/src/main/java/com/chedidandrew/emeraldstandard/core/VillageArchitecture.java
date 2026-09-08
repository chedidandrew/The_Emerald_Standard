package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    public static final String BLUEPRINT_SCHEMA = "blueprint_v2";
    /** SHA-256 over the complete maximum-stage canonical authored placement stream. */
    public static final int BLUEPRINT_PLAN_HASH_VERSION = 1;
    public static final int SILHOUETTE_COUNT = 3;
    public static final int ROOF_COUNT = 3;
    public static final int FRONTAGE_COUNT = 3;

    public static final String PALETTE_BALANCED = "balanced";
    public static final String PALETTE_TIMBER_FORWARD = "timber_forward";
    public static final String PALETTE_MASONRY_FORWARD = "masonry_forward";
    public static final String DRESSING_RESTRAINED = "restrained";
    public static final String DRESSING_LIVED_IN = "lived_in";
    public static final String DRESSING_PROSPEROUS = "prosperous";

    private static final long TYPE_SALT = 0xD1B54A32D192ED03L;
    private static final long SILHOUETTE_SALT = 0x9E3779B97F4A7C15L;
    private static final long ROOF_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long FRONTAGE_SALT = 0x165667B19E3779F9L;
    private static final long BLUEPRINT_TEMPLATE_SALT = 0x8CB92BA72F3D8DD7L;
    private static final long BLUEPRINT_SCALE_SALT = 0xDB4F0B9175AE2165L;
    private static final long BLUEPRINT_DRESSING_SALT = 0xD6E8FEB86659FD93L;
    private static final long BLUEPRINT_MIRROR_SALT = 0xA5A3564E27F8862BL;
    private static final List<String> BLUEPRINT_PALETTES = List.of(
            PALETTE_BALANCED,
            PALETTE_TIMBER_FORWARD,
            PALETTE_MASONRY_FORWARD);
    private static final List<String> BLUEPRINT_DRESSINGS = List.of(
            DRESSING_RESTRAINED,
            DRESSING_LIVED_IN,
            DRESSING_PROSPEROUS);
    /**
     * Immutable catalog revisions. Once a descriptor ships, retain it forever; changed geometry
     * receives either a new revision or a new template id so saved projects never silently reroll.
     */
    private static final List<BlueprintDescriptor> BLUEPRINT_CATALOG = validateActiveCatalog(List.of(
            blueprint(ProjectType.COTTAGE, "cottage_hearth_01", 2, 11, 10, 10, false,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.COTTAGE, "cottage_garden_02", 2, 13, 11, 10, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.HOUSE, "house_cross_01", 2, 15, 13, 14, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.HOUSE, "house_dormer_02", 2, 11, 15, 15, false,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.INN, "inn_gallery_01", 2, 17, 13, 15, false,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.WAREHOUSE, "warehouse_bay_01", 2, 17, 11, 11, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.GRANARY, "granary_loft_01", 2, 13, 11, 17, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.SMITHY, "smithy_courtyard_01", 2, 17, 13, 12, false,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.MINE_ENTRANCE, "mine_headframe_01", 2, 15, 15, 15, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.MARKET_SQUARE, "market_cloister_01", 2, 17, 17, 12, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.GUARD_POST, "guard_watch_01", 2, 11, 11, 18, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.EXCHANGE_HALL, "exchange_hall_01", 2, 19, 15, 16, true,
                    BlueprintScale.LARGE),

            blueprint(ProjectType.COTTAGE, "cottage_courtyard_03", 2, 15, 13, 12, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.HOUSE, "house_arcade_03", 2, 17, 15, 16, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.INN, "inn_coachhouse_02", 2, 21, 17, 16, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.WAREHOUSE, "warehouse_crane_02", 2, 21, 15, 15, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.GRANARY, "granary_windmill_02", 2, 17, 15, 20, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.SMITHY, "smithy_hammerhall_02", 2, 19, 15, 15, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.MINE_ENTRANCE, "mine_winding_house_02", 2, 19, 17, 18, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.MARKET_SQUARE, "market_guildcourt_02", 2, 21, 19, 14, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.GUARD_POST, "guard_bastion_02", 2, 15, 15, 20, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.EXCHANGE_HALL, "exchange_countinghouse_02", 2, 23, 17, 19, true,
                    BlueprintScale.LANDMARK),

            blueprint(ProjectType.COTTAGE, "cottage_bay_04", 2, 9, 9, 10, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.HOUSE, "house_hall_04", 2, 11, 11, 12, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.INN, "inn_wayfarer_03", 2, 13, 11, 12, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.WAREHOUSE, "warehouse_gabled_03", 2, 13, 9, 10, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.GRANARY, "granary_cruck_03", 2, 11, 9, 13, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.SMITHY, "smithy_lane_03", 2, 13, 9, 11, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.MINE_ENTRANCE, "mine_adit_03", 2, 11, 11, 11, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.MARKET_SQUARE, "market_crossroads_03", 2, 15, 15, 10, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.GUARD_POST, "guard_blockhouse_03", 2, 9, 9, 14, true,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.EXCHANGE_HALL, "exchange_branch_03", 2, 15, 11, 13, true,
                    BlueprintScale.MEDIUM),

            blueprint(ProjectType.COTTAGE, "cottage_longhouse_05", 2, 15, 9, 11, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.COTTAGE, "cottage_orchardstead_06", 2, 19, 15, 14, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.HOUSE, "house_splitwing_05", 2, 15, 13, 14, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.HOUSE, "house_towercourt_06", 2, 21, 19, 20, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.INN, "inn_tavern_04", 2, 15, 13, 13, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.INN, "inn_courtyard_05", 2, 25, 21, 19, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.WAREHOUSE, "warehouse_wharf_04", 2, 15, 13, 11, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.WAREHOUSE, "warehouse_basilica_05", 2, 25, 17, 18, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.GRANARY, "granary_stilt_04", 2, 13, 11, 15, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.GRANARY, "granary_silocomplex_05", 2, 21, 17, 19, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.SMITHY, "smithy_corner_04", 2, 15, 13, 12, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.SMITHY, "smithy_foundry_05", 2, 23, 19, 19, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.MINE_ENTRANCE, "mine_drift_04", 2, 15, 13, 13, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.MINE_ENTRANCE, "mine_quarry_05", 2, 23, 21, 17, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.MARKET_SQUARE, "market_lane_04", 2, 17, 13, 10, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.MARKET_SQUARE, "market_bazaar_05", 2, 25, 23, 17, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.GUARD_POST, "guard_gatehouse_04", 2, 15, 11, 16, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.GUARD_POST, "guard_citadel_05", 2, 21, 19, 23, true,
                    BlueprintScale.LANDMARK),
            blueprint(ProjectType.EXCHANGE_HALL, "exchange_loggia_04", 2, 19, 15, 16, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.EXCHANGE_HALL, "exchange_bourse_05", 2, 27, 21, 22, true,
                    BlueprintScale.LANDMARK)));

    /**
     * Shipped revision-1 envelopes remain resolvable forever so an in-progress or relocated
     * project can reproduce its original canonical plan. Selection only reads the active catalog
     * above; these retired masters therefore cannot appear in a newly approved project.
     */
    private static final List<BlueprintDescriptor> RETIRED_BLUEPRINT_CATALOG = List.of(
            blueprint(ProjectType.COTTAGE, "cottage_hearth_01", 1, 9, 9, 9, false,
                    BlueprintScale.SMALL),
            blueprint(ProjectType.COTTAGE, "cottage_garden_02", 1, 9, 11, 9, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.HOUSE, "house_cross_01", 1, 11, 11, 12, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.HOUSE, "house_dormer_02", 1, 11, 11, 11, false,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.INN, "inn_gallery_01", 1, 13, 11, 12, false,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.WAREHOUSE, "warehouse_bay_01", 1, 13, 9, 10, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.GRANARY, "granary_loft_01", 1, 11, 9, 11, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.SMITHY, "smithy_courtyard_01", 1, 13, 9, 10, false,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.MINE_ENTRANCE, "mine_headframe_01", 1, 11, 11, 10, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.MARKET_SQUARE, "market_cloister_01", 1, 15, 15, 8, true,
                    BlueprintScale.LARGE),
            blueprint(ProjectType.GUARD_POST, "guard_watch_01", 1, 9, 9, 14, true,
                    BlueprintScale.MEDIUM),
            blueprint(ProjectType.EXCHANGE_HALL, "exchange_hall_01", 1, 15, 11, 12, true,
                    BlueprintScale.LARGE));

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

    /**
     * Authored footprint class used to pace visual ambition with village development.
     *
     * <p>Small and medium buildings are always eligible so a young settlement never loses a
     * required economic role. Large plans join the catalog at tier 2 and true landmarks at tier
     * 4. The weights are intentionally broad preferences rather than hard one-off quotas.</p>
     */
    public enum BlueprintScale {
        SMALL(0),
        MEDIUM(0),
        LARGE(2),
        LANDMARK(4);

        private final int minimumDevelopmentTier;

        BlueprintScale(int minimumDevelopmentTier) {
            this.minimumDevelopmentTier = minimumDevelopmentTier;
        }

        public int minimumDevelopmentTier() {
            return minimumDevelopmentTier;
        }

        public boolean isAvailableAt(int developmentTier) {
            return normalizedDevelopmentTier(developmentTier) >= minimumDevelopmentTier;
        }

        /** Relative deterministic selection weight at a normalized village tier. */
        public int selectionWeight(int developmentTier) {
            int tier = normalizedDevelopmentTier(developmentTier);
            if (tier < minimumDevelopmentTier) {
                return 0;
            }
            return switch (this) {
                case SMALL -> switch (tier) {
                    case 0 -> 7;
                    case 1 -> 6;
                    case 2 -> 4;
                    case 3 -> 3;
                    case 4 -> 2;
                    default -> 1;
                };
                case MEDIUM -> switch (tier) {
                    case 0 -> 4;
                    case 1 -> 5;
                    case 2 -> 6;
                    case 3 -> 5;
                    case 4 -> 4;
                    default -> 3;
                };
                case LARGE -> switch (tier) {
                    case 2 -> 2;
                    case 3 -> 5;
                    case 4 -> 7;
                    default -> 6;
                };
                case LANDMARK -> tier == 4 ? 3 : 7;
            };
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

    /**
     * Loader-neutral envelope and compatible cosmetic sockets for one coherent authored building.
     * Dimensions include the complete maximum-stage structure but exclude terrain foundations and
     * the public approach. Geometry, dimensions, and mirror safety are immutable per revision.
     */
    public record BlueprintDescriptor(
            ProjectType type,
            String templateId,
            int templateRevision,
            int width,
            int depth,
            int height,
            boolean mirrorable,
            BlueprintScale scale,
            List<String> paletteIds,
            List<String> dressingIds) {
        public BlueprintDescriptor {
            if (type == null
                    || !isStableId(templateId)
                    || templateRevision <= 0
                    || width <= 0
                    || depth <= 0
                    || height <= 0
                    || width > 64
                    || depth > 64
                    || height > 64
                    || scale == null) {
                throw new IllegalArgumentException("Invalid authored blueprint descriptor");
            }
            paletteIds = immutableStableIds(paletteIds, "palette");
            dressingIds = immutableStableIds(dressingIds, "dressing");
        }
    }

    /** A prior persisted selection used by the deterministic per-type shuffle bag. */
    public record ExistingBlueprint(
            long projectId,
            ProjectType type,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            boolean mirrored,
            long signature) {
    }

    /** Immutable Blueprint V2 identity persisted on a project at approval time. */
    public record BlueprintSelection(
            long seed,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            boolean mirrored,
            long signature) {
        public BlueprintSelection {
            if (!isStableId(templateId)
                    || templateRevision <= 0
                    || !isStableId(paletteId)
                    || !isStableId(dressingId)) {
                throw new IllegalArgumentException("Invalid authored blueprint selection");
            }
        }

        public BlueprintDescriptor descriptor() {
            return requireBlueprint(templateId, templateRevision);
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

    /** Backward-compatible low-tier selection entry point for loader integrations and old tests. */
    public static BlueprintSelection chooseBlueprint(
            UUID villageId,
            long projectId,
            ProjectType type,
            Character character,
            List<ExistingBlueprint> existingBlueprints) {
        return chooseBlueprint(
                villageId, projectId, type, character, 0, existingBlueprints);
    }

    /**
     * Chooses one coherent authored building without recombining its floorplan, roof, or interior.
     * Scale is selected with deterministic tier weights; templates within that scale form a
     * least-used shuffle bag. A different eligible template is used instead of an immediate
     * repeat whenever one exists. Palette ids are semantic and are resolved through the village's
     * biome dialect without changing the saved selection.
     */
    public static BlueprintSelection chooseBlueprint(
            UUID villageId,
            long projectId,
            ProjectType type,
            Character character,
            int developmentTier,
            List<ExistingBlueprint> existingBlueprints) {
        if (type == null || character == null) {
            throw new IllegalArgumentException("Project type and village character are required");
        }
        int tier = normalizedDevelopmentTier(developmentTier);
        List<BlueprintDescriptor> candidates = blueprints(type, tier);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No authored blueprint for " + type + " at development tier " + tier);
        }
        List<ExistingBlueprint> existing = existingBlueprints == null
                ? List.of()
                : existingBlueprints;
        long seed = mix64(villageSalt(villageId)
                ^ projectId
                ^ (long) stableTypeId(type) * TYPE_SALT
                ^ BLUEPRINT_TEMPLATE_SALT);

        int[] uses = new int[candidates.size()];
        ExistingBlueprint mostRecent = null;
        for (ExistingBlueprint design : existing) {
            if (design == null || design.type != type) {
                continue;
            }
            if (mostRecent == null || design.projectId > mostRecent.projectId) {
                mostRecent = design;
            }
            for (int index = 0; index < candidates.size(); index++) {
                BlueprintDescriptor candidate = candidates.get(index);
                if (candidate.templateId.equals(design.templateId)
                        && candidate.templateRevision == design.templateRevision) {
                    uses[index]++;
                    break;
                }
            }
        }
        int chosenIndex = chooseBlueprintIndex(
                candidates, uses, mostRecent, tier, seed);
        if (chosenIndex < 0) {
            throw new IllegalStateException("No authored blueprint candidate for " + type);
        }

        BlueprintDescriptor descriptor = candidates.get(chosenIndex);
        String paletteId = preferredPalette(character);
        if (!descriptor.paletteIds.contains(paletteId)) {
            paletteId = descriptor.paletteIds.get(Math.floorMod(
                    (int) mix64(seed ^ 0xC13FA9A902A6328FL),
                    descriptor.paletteIds.size()));
        }
        int dressingIndex = Math.floorMod(
                (int) mix64(seed ^ BLUEPRINT_DRESSING_SALT),
                descriptor.dressingIds.size());
        String dressingId = descriptor.dressingIds.get(dressingIndex);
        boolean mirrored = descriptor.mirrorable
                && (mix64(seed ^ BLUEPRINT_MIRROR_SALT) & 1L) != 0L;
        long signature = blueprintSignature(
                type,
                descriptor.templateId,
                descriptor.templateRevision,
                paletteId,
                dressingId,
                mirrored);

        // If the catalog currently has a single template for this role, rotate a safe cosmetic
        // socket before repeating the latest complete appearance.
        if (mostRecent != null && signature == mostRecent.signature) {
            dressingId = descriptor.dressingIds.get(
                    (dressingIndex + 1) % descriptor.dressingIds.size());
            signature = blueprintSignature(
                    type,
                    descriptor.templateId,
                    descriptor.templateRevision,
                    paletteId,
                    dressingId,
                    mirrored);
        }
        return new BlueprintSelection(
                seed,
                descriptor.templateId,
                descriptor.templateRevision,
                paletteId,
                dressingId,
                mirrored,
                signature);
    }

    public static List<BlueprintDescriptor> blueprints(ProjectType type) {
        if (type == null) {
            return List.of();
        }
        return BLUEPRINT_CATALOG.stream()
                .filter(descriptor -> descriptor.type == type)
                .toList();
    }

    /** Active descriptors eligible to start at the supplied development tier. */
    public static List<BlueprintDescriptor> blueprints(
            ProjectType type, int developmentTier) {
        int tier = normalizedDevelopmentTier(developmentTier);
        return blueprints(type).stream()
                .filter(descriptor -> descriptor.scale.isAvailableAt(tier))
                .toList();
    }

    /** Immutable active catalog in stable release order, used by review tooling. */
    public static List<BlueprintDescriptor> activeBlueprints() {
        return BLUEPRINT_CATALOG;
    }

    public static BlueprintDescriptor blueprint(String templateId, int templateRevision) {
        return java.util.stream.Stream.concat(
                        BLUEPRINT_CATALOG.stream(), RETIRED_BLUEPRINT_CATALOG.stream())
                .filter(descriptor -> descriptor.templateId.equals(templateId)
                        && descriptor.templateRevision == templateRevision)
                .findFirst()
                .orElse(null);
    }

    public static BlueprintDescriptor requireBlueprint(
            String templateId, int templateRevision) {
        BlueprintDescriptor descriptor = blueprint(templateId, templateRevision);
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "Unknown authored blueprint " + templateId + "@" + templateRevision);
        }
        return descriptor;
    }

    public static long blueprintSignature(
            ProjectType type,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            boolean mirrored) {
        if (type == null
                || !isStableId(templateId)
                || templateRevision <= 0
                || !isStableId(paletteId)
                || !isStableId(dressingId)) {
            throw new IllegalArgumentException("Invalid authored blueprint identity");
        }
        long identity = stableString64(templateId)
                ^ Long.rotateLeft(stableString64(paletteId), 11)
                ^ Long.rotateLeft(stableString64(dressingId), 27)
                ^ ((long) stableTypeId(type) << 48)
                ^ ((long) templateRevision << 1)
                ^ (mirrored ? 1L : 0L);
        return mix64(identity ^ 0xF1357AEA2E62A9C5L);
    }

    public static boolean isKnownBlueprintSelection(
            ProjectType type,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            boolean mirrored,
            long signature) {
        BlueprintDescriptor descriptor = blueprint(templateId, templateRevision);
        return descriptor != null
                && descriptor.type == type
                && descriptor.paletteIds.contains(paletteId)
                && descriptor.dressingIds.contains(dressingId)
                && (!mirrored || descriptor.mirrorable)
                && signature == blueprintSignature(
                        type,
                        templateId,
                        templateRevision,
                        paletteId,
                        dressingId,
                        mirrored);
    }

    /** Blank is the valid pre-materialization state; populated values are lowercase SHA-256. */
    public static boolean isValidBlueprintPlanHash(String hash) {
        if (hash == null) {
            return false;
        }
        if (hash.isEmpty()) {
            return true;
        }
        if (hash.length() != 64) {
            return false;
        }
        for (int index = 0; index < hash.length(); index++) {
            char value = hash.charAt(index);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                return false;
            }
        }
        return true;
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
        return LEGACY_SCHEMA.equals(schema)
                || MODULAR_SCHEMA.equals(schema)
                || BLUEPRINT_SCHEMA.equals(schema);
    }

    /** Schemas that use the managed site, trail, and entrance-approach lifecycle. */
    public static boolean isManagedStructureSchema(String schema) {
        return MODULAR_SCHEMA.equals(schema) || BLUEPRINT_SCHEMA.equals(schema);
    }

    /** Building families that currently have a migration-safe post-release quality layer. */
    public static boolean hasQualityRetrofit(VillageProsperityEngine.ProjectType type) {
        return type == VillageProsperityEngine.ProjectType.GUARD_POST
                || type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
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

    private static List<BlueprintDescriptor> validateActiveCatalog(
            List<BlueprintDescriptor> descriptors) {
        List<BlueprintDescriptor> catalog = List.copyOf(descriptors);
        Set<String> identities = new HashSet<>();
        for (BlueprintDescriptor descriptor : catalog) {
            String identity = descriptor.templateId + "@" + descriptor.templateRevision;
            if (!identities.add(identity)) {
                throw new IllegalStateException(
                        "Duplicate active authored blueprint " + identity);
            }
        }
        for (ProjectType type : ProjectType.values()) {
            List<BlueprintDescriptor> role = catalog.stream()
                    .filter(descriptor -> descriptor.type == type)
                    .toList();
            if (role.size() < 3) {
                throw new IllegalStateException(
                        "Authored blueprint role requires at least three masters: " + type);
            }
            boolean youngChoice = role.stream()
                    .anyMatch(descriptor -> descriptor.scale.isAvailableAt(0));
            boolean developedChoice = role.stream()
                    .anyMatch(descriptor -> descriptor.scale == BlueprintScale.LARGE
                            || descriptor.scale == BlueprintScale.LANDMARK);
            if (!youngChoice || !developedChoice) {
                throw new IllegalStateException(
                        "Authored blueprint role lacks young/developed scale coverage: " + type);
            }
        }
        for (BlueprintScale scale : BlueprintScale.values()) {
            if (catalog.stream().noneMatch(descriptor -> descriptor.scale == scale)) {
                throw new IllegalStateException(
                        "Authored blueprint catalog lacks " + scale + " coverage");
            }
        }
        return catalog;
    }

    private static int chooseBlueprintIndex(
            List<BlueprintDescriptor> candidates,
            int[] uses,
            ExistingBlueprint mostRecent,
            int developmentTier,
            long seed) {
        int totalWeight = 0;
        for (BlueprintScale scale : BlueprintScale.values()) {
            if (containsScale(candidates, scale)) {
                totalWeight += scale.selectionWeight(developmentTier);
            }
        }
        if (totalWeight <= 0) {
            return -1;
        }

        int firstBucket = Math.floorMod(
                mix64(seed ^ BLUEPRINT_SCALE_SALT), totalWeight);
        for (int offset = 0; offset < totalWeight; offset++) {
            BlueprintScale scale = scaleAtBucket(
                    candidates,
                    developmentTier,
                    (firstBucket + offset) % totalWeight);
            int index = leastUsedBlueprint(
                    candidates, uses, scale, seed, mostRecent, true);
            if (index >= 0) {
                return index;
            }
        }

        // A one-template eligible catalog is valid; only then permit an immediate repeat.
        BlueprintScale preferred = scaleAtBucket(
                candidates, developmentTier, firstBucket);
        return leastUsedBlueprint(candidates, uses, preferred, seed, mostRecent, false);
    }

    private static int leastUsedBlueprint(
            List<BlueprintDescriptor> candidates,
            int[] uses,
            BlueprintScale scale,
            long seed,
            ExistingBlueprint mostRecent,
            boolean avoidImmediateRepeat) {
        int minimumUses = Integer.MAX_VALUE;
        for (int index = 0; index < candidates.size(); index++) {
            BlueprintDescriptor candidate = candidates.get(index);
            if (candidate.scale != scale
                    || (avoidImmediateRepeat && sameBlueprint(candidate, mostRecent))) {
                continue;
            }
            minimumUses = Math.min(minimumUses, uses[index]);
        }
        if (minimumUses == Integer.MAX_VALUE) {
            return -1;
        }

        long scaleSalt = (long) (scale.ordinal() + 1) * 0x9E3779B97F4A7C15L;
        int start = Math.floorMod((int) mix64(seed ^ scaleSalt), candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            int index = (start + offset) % candidates.size();
            BlueprintDescriptor candidate = candidates.get(index);
            if (candidate.scale == scale
                    && uses[index] == minimumUses
                    && (!avoidImmediateRepeat || !sameBlueprint(candidate, mostRecent))) {
                return index;
            }
        }
        return -1;
    }

    private static BlueprintScale scaleAtBucket(
            List<BlueprintDescriptor> candidates,
            int developmentTier,
            int bucket) {
        for (BlueprintScale scale : BlueprintScale.values()) {
            if (!containsScale(candidates, scale)) {
                continue;
            }
            int weight = scale.selectionWeight(developmentTier);
            if (bucket < weight) {
                return scale;
            }
            bucket -= weight;
        }
        throw new IllegalStateException("Blueprint scale bucket escaped its deterministic range");
    }

    private static boolean containsScale(
            List<BlueprintDescriptor> candidates, BlueprintScale scale) {
        return candidates.stream().anyMatch(candidate -> candidate.scale == scale);
    }

    private static boolean sameBlueprint(
            BlueprintDescriptor candidate, ExistingBlueprint existing) {
        return existing != null
                && candidate.templateId.equals(existing.templateId)
                && candidate.templateRevision == existing.templateRevision;
    }

    private static int normalizedDevelopmentTier(int developmentTier) {
        return Math.max(0, Math.min(5, developmentTier));
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

    private static BlueprintDescriptor blueprint(
            ProjectType type,
            String templateId,
            int templateRevision,
            int width,
            int depth,
            int height,
            boolean mirrorable,
            BlueprintScale scale) {
        return new BlueprintDescriptor(
                type,
                templateId,
                templateRevision,
                width,
                depth,
                height,
                mirrorable,
                scale,
                BLUEPRINT_PALETTES,
                BLUEPRINT_DRESSINGS);
    }

    private static String preferredPalette(Character character) {
        return switch (character) {
            case AGRARIAN, RUSTIC -> PALETTE_TIMBER_FORWARD;
            case MERCANTILE -> PALETTE_BALANCED;
            case FORMAL -> PALETTE_MASONRY_FORWARD;
        };
    }

    private static List<String> immutableStableIds(List<String> ids, String kind) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Blueprint " + kind + " ids are required");
        }
        List<String> immutable = List.copyOf(ids);
        if (immutable.stream().anyMatch(id -> !isStableId(id))
                || immutable.stream().distinct().count() != immutable.size()) {
            throw new IllegalArgumentException("Invalid blueprint " + kind + " ids");
        }
        return immutable;
    }

    private static boolean isStableId(String id) {
        if (id == null || id.isEmpty() || id.length() > 96) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char value = id.charAt(index);
            boolean valid = value >= 'a' && value <= 'z'
                    || value >= '0' && value <= '9'
                    || value == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static long stableString64(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001B3L;
        }
        return hash;
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
