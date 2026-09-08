package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.GeometryCell;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.Kind;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.Occupancy;
import com.chedidandrew.emeraldstandard.core.RoofGeometryValidator.RoofSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailCell;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailPhase;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDetailDensityValidator.DetailSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDistinctivenessValidator;
import com.chedidandrew.emeraldstandard.core.WholeBuildingDistinctivenessValidator.StructuralSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.Bounds;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.Code;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.LightEmitter;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.LightingSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.ValidationReport;
import com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidator;
import com.chedidandrew.emeraldstandard.core.WholeBuildingPresentationValidator.PresentationSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.BuildingRole;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.FixtureSignal;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.RoleSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Immutable, whole-building Blueprint V2 gold masters.
 *
 * <p>Unlike {@link ModularVillageStructures}, these plans never combine an independently selected
 * mass, roof, frontage, or interior. Each template method owns its complete shell, roof,
 * circulation, furnishings, and entrance. Runtime variation is restricted to a semantic material
 * palette, an approved dressing, and transforms performed after this plan passes validation.</p>
 *
 * <p>The catalog boundary is deliberately independent of its eventual storage format. These first
 * reviewed gold masters are code-authored fixed templates; a later native Structure Block NBT
 * loader can provide the same immutable cells without changing project persistence.</p>
 */
final class AuthoredVillageStructures {
    static final int LATEST_TEMPLATE_REVISION = 2;
    private static final Map<String, List<Cell>> LIGHTING_COMPOSITION_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<String, Object> LIGHTING_COMPOSITION_LOCKS =
            new ConcurrentHashMap<>();
    private static final Map<LightingAdmissionKey, Boolean> VALIDATED_LIGHTING_SNAPSHOTS =
            new ConcurrentHashMap<>();

    private AuthoredVillageStructures() {
    }

    static Blueprint plan(
            VillageProsperityEngine.ProjectType type,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            VillageArchitecture.Character character,
            VillageArchitecture.BiomeDialect dialect,
            long doodadSeed) {
        if (templateRevision < 1 || templateRevision > LATEST_TEMPLATE_REVISION) {
            throw new IllegalArgumentException(
                    "Unknown Blueprint V2 revision " + templateRevision + " for " + templateId);
        }
        Materials materials = palette(materials(character, dialect), paletteId);
        Builder base = new Builder(Set.of());
        Metadata metadata = new Metadata();
        metadata.doodadSeed = doodadSeed;
        if (templateRevision == 1) {
            switch (templateId) {
                case "cottage_hearth_01" -> cottageHearth(base, metadata, materials);
                case "cottage_garden_02" -> cottageGarden(base, metadata, materials);
                case "house_cross_01" -> houseCross(base, metadata, materials);
                case "house_dormer_02" -> houseDormer(base, metadata, materials);
                case "inn_gallery_01" -> innGallery(base, metadata, materials);
                case "warehouse_bay_01" -> warehouseBay(base, metadata, materials);
                case "granary_loft_01" -> granaryLoft(base, metadata, materials);
                case "smithy_courtyard_01" -> smithyCourtyard(base, metadata, materials);
                case "mine_headframe_01" -> mineHeadframe(base, metadata, materials);
                case "market_cloister_01" -> marketCloister(base, metadata, materials);
                case "guard_watch_01" -> guardWatch(base, metadata, materials);
                case "exchange_hall_01" -> exchangeHall(base, metadata, materials);
                default -> throw new IllegalArgumentException(
                        "Unknown Blueprint V2 template " + templateId + "@1");
            }
        } else {
            switch (templateId) {
                case "cottage_hearth_01" -> cottageHearthLandmark(base, metadata, materials);
                case "cottage_garden_02" -> cottageGlasshouse(base, metadata, materials);
                case "cottage_courtyard_03" -> cottageCourtyard(base, metadata, materials);
                case "cottage_bay_04" -> cottageBayCompact(base, metadata, materials);
                case "cottage_longhouse_05" -> cottageLonghouse(base, metadata, materials);
                case "cottage_orchardstead_06" -> cottageOrchardstead(base, metadata, materials);
                case "house_cross_01" -> houseCrossGabled(base, metadata, materials);
                case "house_dormer_02" -> houseMansard(base, metadata, materials);
                case "house_arcade_03" -> houseArcade(base, metadata, materials);
                case "house_hall_04" -> houseHallCompact(base, metadata, materials);
                case "house_splitwing_05" -> houseSplitwing(base, metadata, materials);
                case "house_towercourt_06" -> houseTowercourt(base, metadata, materials);
                case "inn_gallery_01" -> innBalcony(base, metadata, materials);
                case "inn_coachhouse_02" -> innCoachhouse(base, metadata, materials);
                case "inn_wayfarer_03" -> innWayfarerCompact(base, metadata, materials);
                case "inn_tavern_04" -> innTavern(base, metadata, materials);
                case "inn_courtyard_05" -> innCourtyard(base, metadata, materials);
                case "warehouse_bay_01" -> warehouseSawtooth(base, metadata, materials);
                case "warehouse_crane_02" -> warehouseCraneHall(base, metadata, materials);
                case "warehouse_gabled_03" -> warehouseGabledCompact(base, metadata, materials);
                case "warehouse_wharf_04" -> warehouseWharf(base, metadata, materials);
                case "warehouse_basilica_05" -> warehouseBasilica(base, metadata, materials);
                case "granary_loft_01" -> granaryRaisedBarn(base, metadata, materials);
                case "granary_windmill_02" -> granaryWindmill(base, metadata, materials);
                case "granary_cruck_03" -> granaryCruckCompact(base, metadata, materials);
                case "granary_stilt_04" -> granaryStilt(base, metadata, materials);
                case "granary_silocomplex_05" -> granarySiloComplex(base, metadata, materials);
                case "smithy_courtyard_01" -> smithyOpenForge(base, metadata, materials);
                case "smithy_hammerhall_02" -> smithyHammerhall(base, metadata, materials);
                case "smithy_lane_03" -> smithyLaneCompact(base, metadata, materials);
                case "smithy_corner_04" -> smithyCorner(base, metadata, materials);
                case "smithy_foundry_05" -> smithyFoundry(base, metadata, materials);
                case "mine_headframe_01" -> mineHeadframeLandmark(base, metadata, materials);
                case "mine_winding_house_02" -> mineWindingHouse(base, metadata, materials);
                case "mine_adit_03" -> mineAditCompact(base, metadata, materials);
                case "mine_drift_04" -> mineDrift(base, metadata, materials);
                case "mine_quarry_05" -> mineQuarry(base, metadata, materials);
                case "market_cloister_01" -> marketRotunda(base, metadata, materials);
                case "market_guildcourt_02" -> marketGuildcourt(base, metadata, materials);
                case "market_crossroads_03" -> marketCrossroadsCompact(base, metadata, materials);
                case "market_lane_04" -> marketLane(base, metadata, materials);
                case "market_bazaar_05" -> marketBazaar(base, metadata, materials);
                case "guard_watch_01" -> guardGateTower(base, metadata, materials);
                case "guard_bastion_02" -> guardBastion(base, metadata, materials);
                case "guard_blockhouse_03" -> guardBlockhouseCompact(base, metadata, materials);
                case "guard_gatehouse_04" -> guardGatehouse(base, metadata, materials);
                case "guard_citadel_05" -> guardCitadel(base, metadata, materials);
                case "exchange_hall_01" -> exchangeCivicHall(base, metadata, materials);
                case "exchange_countinghouse_02" -> exchangeCountinghouse(base, metadata, materials);
                case "exchange_branch_03" -> exchangeBranchCompact(base, metadata, materials);
                case "exchange_loggia_04" -> exchangeLoggia(base, metadata, materials);
                case "exchange_bourse_05" -> exchangeBourse(base, metadata, materials);
                default -> throw new IllegalArgumentException(
                        "Unknown Blueprint V2 template " + templateId + "@2");
            }
            VillageArchitecture.BlueprintScale scale = VillageArchitecture
                    .requireBlueprint(templateId, templateRevision)
                    .scale();
            addRegionalIdentity(base, metadata, materials);
            addArchitecturalPresentation(base, metadata, materials, templateId, scale);
            addCompactCraftLayer(base, metadata, materials, templateId, scale);
            requireInteractionRoutesBeforePocketSealing(base, metadata, templateId);
            sealDisconnectedInteriorPockets(base, metadata, materials, templateId);
            ensureComfortableInteriorLighting(base, metadata, templateId);
        }
        if (metadata.type != type) {
            throw new IllegalArgumentException(
                    "Blueprint " + templateId + " belongs to " + metadata.type + ", not " + type);
        }

        Builder stageOne = new Builder(base.positions());
        appendDressingStageOne(stageOne, metadata, materials, dressingId);
        if (templateRevision == LATEST_TEMPLATE_REVISION) {
            appendPresentationStageOne(
                    stageOne,
                    metadata,
                    materials,
                    templateId,
                    VillageArchitecture.requireBlueprint(templateId, templateRevision).scale(),
                    dressingId);
        }
        if (templateRevision == LATEST_TEMPLATE_REVISION) {
            ensureCumulativeComfortableInteriorLighting(
                    stageOne,
                    metadata,
                    templateId,
                    "stage-one",
                    dressingId,
                    List.of(base.values(), stageOne.values()));
        }
        Set<BlockPos> throughStageOne = new HashSet<>(base.positions());
        throughStageOne.addAll(stageOne.positions());
        Builder stageTwo = new Builder(throughStageOne);
        appendDressingStageTwo(stageTwo, metadata, materials, dressingId);
        if (templateRevision == LATEST_TEMPLATE_REVISION) {
            appendPresentationStageTwo(
                    stageTwo,
                    base,
                    stageOne,
                    metadata,
                    materials,
                    templateId,
                    VillageArchitecture.requireBlueprint(templateId, templateRevision).scale(),
                    dressingId);
        }
        if (templateRevision == LATEST_TEMPLATE_REVISION) {
            ensureCumulativeComfortableInteriorLighting(
                    stageTwo,
                    metadata,
                    templateId,
                    "stage-two",
                    dressingId,
                    List.of(base.values(), stageOne.values(), stageTwo.values()));
        }

        Blueprint blueprint = new Blueprint(
                templateId,
                templateRevision,
                paletteId,
                dressingId,
                metadata.width,
                metadata.depth,
                metadata.height,
                base.values(),
                stageOne.values(),
                stageTwo.values(),
                materials,
                metadata.freeze());
        validate(blueprint);
        if (templateRevision == LATEST_TEMPLATE_REVISION) {
            validateDetailDensity(blueprint);
            validatePresentation(blueprint);
            validateRoleReadability(blueprint);
        }
        return blueprint;
    }

    static Blueprint plan(
            VillageProsperityEngine.ProjectType type,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            VillageArchitecture.Character character,
            VillageArchitecture.BiomeDialect dialect) {
        return plan(
                type,
                templateId,
                templateRevision,
                paletteId,
                dressingId,
                character,
                dialect,
                0L);
    }

    private static Materials materials(
            VillageArchitecture.Character character,
            VillageArchitecture.BiomeDialect dialect) {
        ModularVillageStructures.Materials base = ModularVillageStructures.materials(character, dialect);
        Block floor = switch (dialect) {
            case DESERT -> Blocks.SMOOTH_SANDSTONE;
            // A neutral floor keeps the selective acacia frame readable instead of turning the
            // entire savanna building into one orange volume.
            case SAVANNA -> Blocks.SMOOTH_STONE;
            case TAIGA, SNOWY -> Blocks.SPRUCE_PLANKS;
            case PLAINS -> Blocks.OAK_PLANKS;
        };
        Block foundation = switch (dialect) {
            case SAVANNA -> Blocks.STONE_BRICKS;
            case TAIGA -> Blocks.MOSSY_COBBLESTONE;
            case SNOWY -> Blocks.POLISHED_ANDESITE;
            default -> base.foundation();
        };
        Block wall = switch (dialect) {
            case SAVANNA -> Blocks.MUD_BRICKS;
            case TAIGA -> Blocks.SPRUCE_PLANKS;
            case SNOWY -> Blocks.POLISHED_DIORITE;
            default -> base.wall();
        };
        Block timber = switch (dialect) {
            case SNOWY -> Blocks.STRIPPED_DARK_OAK_LOG;
            default -> base.timber();
        };
        Block roofStairs = switch (dialect) {
            case SAVANNA -> Blocks.DARK_OAK_STAIRS;
            case TAIGA -> Blocks.DEEPSLATE_TILE_STAIRS;
            case SNOWY -> Blocks.POLISHED_DIORITE_STAIRS;
            default -> base.roofStairs();
        };
        Block roofSlab = switch (dialect) {
            case SAVANNA -> Blocks.DARK_OAK_SLAB;
            case TAIGA -> Blocks.DEEPSLATE_TILE_SLAB;
            case SNOWY -> Blocks.POLISHED_DIORITE_SLAB;
            default -> base.roofSlab();
        };
        Block fence = dialect == VillageArchitecture.BiomeDialect.SNOWY
                ? Blocks.DARK_OAK_FENCE : base.fence();
        Block door = dialect == VillageArchitecture.BiomeDialect.SNOWY
                ? Blocks.DARK_OAK_DOOR : base.door();
        Block chimney = dialect == VillageArchitecture.BiomeDialect.DESERT
                ? Blocks.CUT_SANDSTONE
                : Blocks.BRICKS;
        return new Materials(
                dialect,
                foundation,
                floor,
                wall,
                timber,
                roofStairs,
                roofSlab,
                fence,
                base.accent(),
                door,
                base.entryStairs(),
                chimney);
    }

    private static Materials palette(Materials base, String paletteId) {
        return switch (paletteId) {
            case "balanced" -> base;
            case "timber_forward" -> new Materials(
                    base.dialect,
                    base.foundation,
                    base.floor,
                    base.wall,
                    base.timber,
                    base.roofStairs,
                    base.roofSlab,
                    base.fence,
                    base.timber,
                    base.door,
                    base.entryStairs,
                    base.chimney);
            case "masonry_forward" -> new Materials(
                    base.dialect,
                    base.foundation,
                    base.floor,
                    base.foundation,
                    base.timber,
                    base.roofStairs,
                    base.roofSlab,
                    base.fence,
                    base.accent,
                    base.door,
                    base.entryStairs,
                    base.chimney);
            default -> throw new IllegalArgumentException("Unknown Blueprint V2 palette " + paletteId);
        };
    }

    /*
     * Revision-2 landmark masters.  These are intentionally composed here one building at a
     * time.  The helpers below are drafting primitives, not independently selected modules: a
     * saved master always owns its complete footprint, roof, frontage, circulation and interior.
     */

    private static void cottageHearthLandmark(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 11, 10, 10, true);
        Footprint footprint = (x, z) -> (x <= 8 && z <= 7) || (x >= 7 && z >= 4);
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 2, 0, 2, 2);
        windowColumn(windows, 0, 4, 2, 2);
        windowColumn(windows, 4, 7, 2, 2);
        windowColumn(windows, 10, 7, 2, 2);
        addFootprintShell(b, m, p, footprint, 3, windows);
        addFootprintTimberBand(b, m, p, footprint, 3);
        addSaltboxRoof(b, m, p, 0, 8, 0, 7, 3);
        addShedRoof(b, m, p, footprint, 7, 10, 4, 9, 4, Direction.NORTH);
        addIntegratedChimney(b, m, p, 9, 7);
        // The oversized stepped masonry breast is the visual and functional heart of this
        // cottage. It is deliberately wider at the floor than at the flue, like a real hearth.
        for (int y = 1; y <= 2; y++) {
            for (int x = 8; x <= 10; x++) {
                b.force(Phase.FRAME, x, y, 7, p.chimney.defaultBlockState());
            }
        }
        beam(b, Phase.FRAME, 8, 10, 3, 7, p.accent);
        addLayeredEntryPorch(b, p, 5, 2, 4);
        addDeepWindowFrame(b, p, 2, 0, 2, 2, Direction.NORTH);
        addDeepWindowFrame(b, p, 0, 4, 2, 2, Direction.WEST);
        addCoveredWoodBay(b, p, 11, 5, 7);
        fixture(b, m, 9, 1, 6, Blocks.SMOKER, new BlockPos(8, 1, 6));
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 1, 6, Direction.EAST, 3, 6);
        fixture(b, m, 6, 1, 6, Blocks.CHEST, new BlockPos(5, 1, 6));
        addTable(b, p, 4, 4);
        m.reserveCentralAisle(5, 1, 6);
        m.interiorSamples.add(new BlockPos(8, 1, 6));
        addCottageInteriorProgram(b, m, p, "hearth");
    }

    private static void cottageGlasshouse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 13, 11, 10, true);
        Footprint footprint = (x, z) -> (x >= 2 && x <= 10 && z <= 7)
                || (x <= 4 && z >= 5);
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 3, 0, 2, 2);
        windowColumn(windows, 9, 0, 2, 2);
        windowColumn(windows, 2, 3, 2, 2);
        windowColumn(windows, 10, 4, 2, 2);
        addFootprintShell(b, m, p, footprint, 3, windows);
        addFootprintTimberBand(b, m, p, footprint, 3);
        addClippedGableRoof(b, m, p, 2, 10, 0, 7, 3);

        // A glazed kitchen conservatory makes the second cottage unmistakable from a distance.
        for (int y = 1; y <= 3; y++) {
            for (int z = 6; z <= 9; z++) {
                b.force(Phase.OPENING, 0, y, z, Blocks.GLASS_PANE.defaultBlockState());
            }
            for (int x = 0; x <= 3; x++) {
                b.force(Phase.OPENING, x, y, 10, Blocks.GLASS_PANE.defaultBlockState());
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 5; z <= 10; z++) {
                int y = 4 + Math.max(0, x - 1) / 2;
                b.force(Phase.ROOF, x, y, z, Blocks.GLASS.defaultBlockState());
            }
        }
        // Close the one-block triangular reveals below the rising glass roof.  Without these
        // panes the conservatory looked complete from ground level, but its attic volume was
        // open to the sky along the east edge and rear gable.
        for (int z = 8; z <= 10; z++) {
            b.force(Phase.OPENING, 4, 4, z, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int x = 3; x <= 4; x++) {
            b.force(Phase.OPENING, x, 4, 10, Blocks.GLASS_PANE.defaultBlockState());
        }
        // Seal every remaining riser beneath the sloped glass roof. The authored gable already
        // owns a handful of these cells, so fill only empty ones; this closes the conservatory
        // without replacing its timber-and-wall junction at z=7.
        for (int x = 3; x <= 4; x++) {
            for (int z = 5; z <= 10; z++) {
                BlockPos reveal = new BlockPos(x, 4, z);
                if (!b.contains(reveal)) {
                    b.put(Phase.OPENING, x, 4, z, Blocks.GLASS_PANE.defaultBlockState());
                }
            }
        }
        addGlasshouseRibs(b, p);
        addGableTrussX(b, p, 6, 0, 3, Math.min(8, m.roofPeak));
        addLayeredEntryPorch(b, p, 6, 2, 4);
        addDeepWindowFrame(b, p, 3, 0, 2, 2, Direction.NORTH);
        addDeepWindowFrame(b, p, 9, 0, 2, 2, Direction.NORTH);
        fixture(b, m, 1, 1, 8, Blocks.COMPOSTER, new BlockPos(2, 1, 8));
        fixture(b, m, 3, 1, 8, Blocks.LOOM, new BlockPos(3, 1, 7));
        fixture(b, m, 1, 1, 6, Blocks.SMOKER, new BlockPos(2, 1, 6));
        addBed(b, m, 3, 3, Direction.EAST, 5, 3);
        addBed(b, m, 9, 3, Direction.WEST, 7, 3);
        fixture(b, m, 8, 1, 6, Blocks.CRAFTING_TABLE, new BlockPos(7, 1, 6));
        addTable(b, p, 5, 4);
        addGardenPergola(b, p, 9, 12, 8, 10);
        // A little potting pavilion terminates the pergola walk. Its detached gable is a genuine
        // secondary garden-room silhouette, not another ornament pasted onto the main ridge.
        addGardenPottingPavilion(b, p, 13, 15, 7, 10);
        m.reserveCentralAisle(6, 1, 6);
        m.interiorSamples.add(new BlockPos(2, 1, 8));
        addCottageInteriorProgram(b, m, p, "glasshouse");
    }

    private static void houseCrossGabled(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 15, 13, 14, true);
        Footprint footprint = (x, z) -> (x >= 4 && x <= 10) || (z >= 4 && z <= 9);
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 7, 12, 2, 3);
        windowColumn(windows, 0, 6, 2, 3);
        windowColumn(windows, 14, 6, 2, 3);
        windowColumn(windows, 5, 0, 2, 2);
        windowColumn(windows, 9, 0, 2, 2);
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 5);
        addRectGableRoof(b, m, p, 4, 10, 0, 12, 5, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 14, 4, 9, 5, Direction.Axis.Z);
        // Raised crossing lantern and side bay keep the plus plan legible above both ridges.
        // A full curb keys the lantern into the crossing; bottom slabs here used to leave a
        // visible half-block gap both below the deck and below its two diagonal corner posts.
        // Fill the two lower crossed-ridge courses as well, so the full curb never bears on the
        // empty upper half of a bottom slab.
        for (int x = 6; x <= 8; x++) {
            for (int z = 6; z <= 7; z++) {
                b.force(Phase.ROOF, x, 9, z, doubleRoofSlab(p));
            }
        }
        for (int x = 6; x <= 8; x++) {
            for (int z = 5; z <= 8; z++) {
                b.force(Phase.ROOF, x, 10, z, doubleRoofSlab(p));
            }
        }
        for (int x : new int[] {6, 8}) {
            for (int z : new int[] {5, 8}) {
                post(b, Phase.FRAME, x, 11, z, 12, p.timber);
            }
        }
        for (int y = 11; y <= 12; y++) {
            b.force(Phase.OPENING, 7, y, 5, Blocks.GLASS_PANE.defaultBlockState());
            b.force(Phase.OPENING, 7, y, 8, Blocks.GLASS_PANE.defaultBlockState());
            for (int z = 6; z <= 7; z++) {
                b.force(Phase.OPENING, 6, y, z, Blocks.GLASS_PANE.defaultBlockState());
                b.force(Phase.OPENING, 8, y, z, Blocks.GLASS_PANE.defaultBlockState());
            }
        }
        for (int x = 5; x <= 9; x++) {
            for (int z = 4; z <= 9; z++) {
                b.force(Phase.ROOF, x, 13, z,
                        edgeInRect(x, z, 5, 9, 4, 9)
                                ? inwardRoofStair(p, x, z, 5, 9, 4, 9)
                                : p.roofSlab.defaultBlockState());
            }
        }
        // Two low corner lanterns preserve the cupola's two-block-tall center passage while
        // lighting its occupied transfer deck. Upper panes remain intact on every elevation.
        b.force(Phase.DECOR, 6, 11, 6, Blocks.LANTERN.defaultBlockState());
        b.force(Phase.DECOR, 8, 11, 7, Blocks.LANTERN.defaultBlockState());
        m.roofPeak = 13;
        addIntegratedChimney(b, m, p, 12, 7);
        addGableTrussX(b, p, 7, 0, 5, 10);
        addGableTrussX(b, p, 7, 12, 5, 10);
        addTwinRidgeGableTrussZ(b, p, 0, 6, 7, 5, 9);
        addTwinRidgeGableTrussZ(b, p, 14, 6, 7, 5, 9);
        // A seven-wide porch lands its posts on the two window jambs instead of masking the
        // centered panes, while retaining a generous three-wide approach to the door.
        addLayeredEntryPorch(b, p, 7, 3, 4);
        addDeepWindowFrame(b, p, 5, 0, 2, 2, Direction.NORTH);
        addDeepWindowFrame(b, p, 9, 0, 2, 2, Direction.NORTH);
        addBayWindow(b, p, 14, 6, Direction.EAST);
        addBed(b, m, 5, 3, Direction.EAST, 7, 3);
        addBed(b, m, 9, 3, Direction.WEST, 7, 3);
        addBed(b, m, 2, 7, Direction.EAST, 4, 7);
        addBed(b, m, 13, 6, Direction.WEST, 11, 6);
        fixture(b, m, 2, 1, 5, Blocks.LOOM, new BlockPos(3, 1, 5));
        fixture(b, m, 12, 1, 5, Blocks.CRAFTING_TABLE, new BlockPos(11, 1, 5));
        fixture(b, m, 7, 1, 10, Blocks.CHEST, new BlockPos(7, 1, 9));
        addTable(b, p, 7, 6);
        m.reserveCentralAisle(7, 1, 5);
        m.interiorSamples.add(new BlockPos(2, 1, 6));
        m.interiorSamples.add(new BlockPos(12, 1, 6));
        addHouseInteriorProgram(b, m, p, "cross");
    }

    private static void houseMansard(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 11, 15, 15, true);
        Footprint footprint = (x, z) -> x >= 1 && x <= 9 && z <= 13;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 8}) {
            windowColumn(windows, x, 0, 2, 3);
            windowColumn(windows, x, 13, 2, 3);
        }
        for (int z : new int[] {3, 8, 11}) {
            windowColumn(windows, 1, z, 2, 3);
            windowColumn(windows, 9, z, 2, 3);
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        addMansardRoof(b, m, p, 1, 9, 0, 13, 6);
        addFrontDormer(b, m, p, 3, 8);
        addFrontDormer(b, m, p, 7, 8);
        addBayWindow(b, p, 9, 7, Direction.EAST);
        addIntegratedChimney(b, m, p, 2, 11);
        addLayeredEntryPorch(b, p, 5, 2, 5);
        addDeepWindowFrame(b, p, 2, 0, 2, 3, Direction.NORTH);
        addDeepWindowFrame(b, p, 8, 0, 2, 3, Direction.NORTH);
        addFrontEaveBrackets(b, p, 6, 1, 5, 9);
        addMerchantMezzanine(b, m, p);
        addBed(b, m, 2, 4, Direction.EAST, 4, 4);
        addBed(b, m, 8, 4, Direction.WEST, 6, 4);
        addBed(b, m, 2, 10, Direction.EAST, 4, 10);
        fixture(b, m, 8, 1, 11, Blocks.BOOKSHELF, new BlockPos(7, 1, 11));
        fixture(b, m, 5, 1, 12, Blocks.CHEST, new BlockPos(5, 1, 11));
        addTable(b, p, 4, 7);
        m.reserveCentralAisle(5, 1, 11);
        addHouseInteriorProgram(b, m, p, "mansard");
    }

    private static void innBalcony(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.INN, 17, 13, 15, true);
        Footprint footprint = (x, z) -> z >= 4 || x <= 4 || x >= 12
                || (x >= 7 && x <= 9);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 14}) {
            windowColumn(windows, x, 0, 2, 3);
            windowColumn(windows, x, 12, 2, 3);
        }
        for (int x : new int[] {6, 10}) {
            windowColumn(windows, x, 12, 2, 3);
        }
        windowColumn(windows, 0, 7, 2, 3);
        windowColumn(windows, 16, 7, 2, 3);
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        // A narrow gatehouse preserves the centered public threshold while the two wings form a
        // sheltered arrival court on either side.
        addHipRoof(b, m, p, 0, 16, 4, 12, 6);
        addRectGableRoof(b, m, p, 0, 4, 0, 7, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 12, 16, 0, 7, 6, Direction.Axis.X);
        // The centered threshold is a real gatehouse, not an uncovered connector between wings.
        // Its own steep cross-gable closes the arrival hall and creates a third skyline mass.
        addRectGableRoof(b, m, p, 7, 9, 0, 4, 6, Direction.Axis.X);
        addInnGallery(b, p);
        addIntegratedChimney(b, m, p, 16, 10);
        addLayeredEntryPorch(b, p, 8, 2, 4);
        addDeepWindowFrame(b, p, 2, 0, 2, 3, Direction.NORTH);
        addDeepWindowFrame(b, p, 14, 0, 2, 3, Direction.NORTH);
        addFrontEaveBrackets(b, p, 6, 0, 4, 8, 12, 16);
        addInnUpperFloor(b, m, p);
        for (int z : new int[] {6, 10}) {
            addBed(b, m, 1, z, Direction.EAST, 3, z);
            addBed(b, m, 15, z, Direction.WEST, 13, z);
        }
        fixture(b, m, 3, 1, 11, Blocks.BREWING_STAND, new BlockPos(4, 1, 11));
        fixture(b, m, 5, 1, 11, Blocks.SMOKER, new BlockPos(5, 1, 10));
        fixture(b, m, 11, 1, 11, Blocks.CAKE, new BlockPos(11, 1, 10));
        // Keep the east-loft ladder's x=14 lower boarding bay open; the former chest at x=13
        // boxed that bay in between the rear wall and guest bed despite the ladder looking valid.
        fixture(b, m, 13, 1, 9, Blocks.CHEST, new BlockPos(12, 1, 9));
        addLongTable(b, p, 5, 7, 3);
        m.reserveCentralAisle(8, 5, 11);
        m.interiorSamples.add(new BlockPos(3, 1, 2));
        m.interiorSamples.add(new BlockPos(13, 1, 2));
        addInnInteriorProgram(b, m, p, "gallery");
    }

    private static void warehouseSawtooth(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.WAREHOUSE, 17, 11, 11, true);
        Footprint footprint = (x, z) -> true;
        Set<BlockPos> windows = new HashSet<>();
        for (int z : new int[] {2, 5, 8}) {
            windowColumn(windows, 0, z, 3, 4);
            windowColumn(windows, 16, z, 3, 4);
        }
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 5);
        // Three industrial bays, including a full-height central cart opening.
        for (int x = 6; x <= 10; x++) {
            for (int y = 1; y <= 4; y++) {
                b.remove(x, y, 0);
            }
        }
        for (int x : new int[] {1, 5, 11, 15}) {
            post(b, Phase.FRAME, x, 1, 0, 5, p.accent);
        }
        beam(b, Phase.FRAME, 1, 15, 5, 0, p.accent);
        addMonitorRoof(b, m, p);
        addLoadingDock(b, p, 2, 14);
        addWarehouseFrameRhythm(b, p);
        addWarehouseRacks(b, p);
        // The later rear apron sits under the monitor's long south eave. Paired wall sconces make
        // that loading threshold usable at night without putting posts in the cart lane.
        addRearWallSconce(b, 6, 2, 11);
        addRearWallSconce(b, 10, 2, 11);
        for (int x : new int[] {1, 3, 13, 15}) {
            fixture(b, m, x, 1, 9, Blocks.BARREL,
                    new BlockPos(x < 8 ? x + 1 : x - 1, 1, 9));
        }
        for (int z : new int[] {3, 6}) {
            fixture(b, m, 3, 1, z, Blocks.CHEST, new BlockPos(4, 1, z));
            fixture(b, m, 13, 1, z, Blocks.CHEST, new BlockPos(12, 1, z));
        }
        fixture(b, m, 8, 1, 9, Blocks.CRAFTING_TABLE, new BlockPos(8, 1, 8));
        m.reserveCentralAisle(8, 1, 8);
        m.interiorSamples.add(new BlockPos(4, 1, 5));
        m.enclosed = false;
        addIndustryWarehouseProgram(b, m, p, "sawtooth");
    }

    private static void granaryRaisedBarn(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GRANARY, 13, 11, 17, false);
        // An open undercroft and four massive piers make the raised grain body readable at once.
        for (int x = 4; x <= 8; x++) {
            for (int z = 0; z <= 9; z++) {
                b.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int x : new int[] {1, 5, 7, 11}) {
            for (int z : new int[] {2, 9}) {
                post(b, Phase.FRAME, x, 0, z, 4, p.foundation);
            }
        }
        for (int x = 1; x <= 11; x++) {
            for (int z = 1; z <= 9; z++) {
                b.force(Phase.FOUNDATION, x, 4, z,
                        edgeInRect(x, z, 1, 11, 1, 9)
                                ? p.timber.defaultBlockState()
                                : p.floor.defaultBlockState());
            }
        }
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 3, 1, 6, 7);
        windowColumn(windows, 9, 1, 6, 7);
        windowColumn(windows, 1, 5, 6, 7);
        windowColumn(windows, 11, 5, 6, 7);
        for (int y = 5; y <= 8; y++) {
            for (int x = 1; x <= 11; x++) {
                for (int z = 1; z <= 9; z++) {
                    if (!edgeInRect(x, z, 1, 11, 1, 9)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = windows.contains(pos)
                            ? Blocks.GLASS_PANE
                            : (cornerInRect(x, z, 1, 11, 1, 9) ? p.timber : p.wall);
                    b.put(windows.contains(pos) ? Phase.OPENING : Phase.SHELL,
                            x, y, z, block.defaultBlockState());
                }
            }
        }
        addPyramidRoof(b, m, p, 1, 11, 1, 9, 8);
        addRectTimberBand(b, p, 1, 11, 1, 9, 5);
        addRectTimberBand(b, p, 1, 11, 1, 9, 8);
        addDeepWindowFrame(b, p, 3, 1, 6, 7, Direction.NORTH);
        addDeepWindowFrame(b, p, 9, 1, 6, 7, Direction.NORTH);
        addGranaryUndercroftBraces(b, p);
        for (int x = 5; x <= 7; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }

        // Straight backed ladder and sealed hatch provide a tested route into the working loft.
        for (int y = 1; y <= 3; y++) {
            b.put(Phase.FRAME, 6, y, 9, p.timber.defaultBlockState());
            b.put(Phase.FIXTURE, 6, y, 8,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.force(Phase.FIXTURE, 6, 4, 8, Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(new BlockPos(6, 1, 8), new BlockPos(6, 3, 8)));
        m.accessTargets.add(new BlockPos(6, 5, 7));
        fixture(b, m, 3, 5, 7, Blocks.HAY_BLOCK, new BlockPos(4, 5, 7));
        fixture(b, m, 9, 5, 7, Blocks.BARREL, new BlockPos(8, 5, 7));
        fixture(b, m, 3, 5, 3, Blocks.COMPOSTER, new BlockPos(4, 5, 3));
        fixture(b, m, 9, 5, 3, Blocks.CHEST, new BlockPos(8, 5, 3));
        addGrainChute(b, p, 11, 6);
        addRaisedGranaryHoistHouse(b, m, p);
        m.wallHeight = 8;
        m.entranceInside = new BlockPos(6, 1, 1);
        m.interiorSamples.add(new BlockPos(6, 5, 5));
        m.reserveCentralAisle(6, 1, 7);
        addIndustryGranaryProgram(b, m, p, "raised_barn");
    }

    private static void smithyOpenForge(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.SMITHY, 17, 13, 12, false);
        addCourtyardFloor(b, p, 17, 13);
        // Heavy L-shaped masonry wings frame an open, visible working court.
        addOpenWorkshopWing(b, p, 0, 5, 3, 12, Direction.EAST);
        addOpenWorkshopWing(b, p, 11, 16, 6, 12, Direction.WEST);
        addOpenWorkshopWing(b, p, 5, 11, 9, 12, Direction.NORTH);
        addForgeTimberArches(b, p);
        for (int x : new int[] {0, 5, 11, 16}) {
            post(b, Phase.FRAME, x, 1, x < 8 ? 3 : 6, 4, p.timber);
        }
        // Twin flues and the hanging hood are a skyline landmark, not a house chimney.
        for (int x : new int[] {13, 15}) {
            b.force(Phase.FRAME, x, 0, 10, p.foundation.defaultBlockState());
            post(b, Phase.ROOF, x, 1, 10, 10, p.chimney);
        }
        for (int x = 12; x <= 16; x++) {
            for (int z = 9; z <= 11; z++) {
                if (!(z == 10 && (x == 13 || x == 15))) {
                    // Thicken the workshop canopy wherever it bears the masonry forge hood. A
                    // full hood block above a bottom slab otherwise leaves a half-block air seam.
                    b.force(Phase.ROOF, x, 5, z, doubleRoofSlab(p));
                }
                b.force(Phase.ROOF, x, 6, z, p.foundation.defaultBlockState());
            }
        }
        for (int x = 13; x <= 15; x++) {
            b.force(Phase.ROOF, x, 7, 10, p.foundation.defaultBlockState());
        }
        addForgeWorkDetails(b, p);
        fixture(b, m, 2, 1, 9, Blocks.ANVIL, new BlockPos(3, 1, 9));
        fixture(b, m, 4, 1, 9, Blocks.GRINDSTONE, new BlockPos(4, 1, 8));
        fixture(b, m, 12, 1, 9, Blocks.SMITHING_TABLE, new BlockPos(12, 1, 8));
        fixture(b, m, 14, 1, 9, Blocks.BLAST_FURNACE, new BlockPos(13, 1, 9));
        fixture(b, m, 2, 1, 5, Blocks.CAULDRON, new BlockPos(3, 1, 5));
        fixture(b, m, 14, 1, 7, Blocks.CHEST, new BlockPos(13, 1, 7));
        addWoodRack(b, p, 4, 11);
        m.entranceInside = new BlockPos(8, 1, 1);
        m.accessTargets.add(new BlockPos(8, 1, 8));
        m.reserveCentralAisle(8, 1, 8);
        m.wallHeight = 5;
        m.roofPeak = 10;
        addIndustrySmithyProgram(b, m, p, "open_forge");
    }

    private static void mineHeadframeLandmark(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MINE_ENTRANCE, 15, 15, 15, false);
        for (int z = 0; z <= 14; z++) {
            for (int x = 6; x <= 8; x++) {
                b.put(Phase.FOUNDATION, x, 0, z,
                        (x == 7 ? p.foundation : p.accent).defaultBlockState());
            }
            b.put(Phase.FIXTURE, 7, 1, z, Blocks.RAIL.defaultBlockState());
        }
        // Rear retaining wall and dark adit replace the former house-shaped shell.
        for (int x = 3; x <= 11; x++) {
            for (int y = 1; y <= 6; y++) {
                if (x >= 6 && x <= 8 && y <= 4) {
                    continue;
                }
                b.put(Phase.SHELL, x, y, 14, p.foundation.defaultBlockState());
            }
        }
        for (int z = 11; z <= 14; z++) {
            for (int x : new int[] {5, 9}) {
                post(b, Phase.FRAME, x, 1, z, 5, p.timber);
            }
        }
        // Tall freestanding A-frame with a chain suspended above the rail line.
        for (int z : new int[] {5, 9}) {
            post(b, Phase.FRAME, 3, 1, z, 8, p.timber);
            post(b, Phase.FRAME, 11, 1, z, 8, p.timber);
            beam(b, Phase.FRAME, 3, 11, 8, z, p.timber);
            for (int step = 0; step <= 4; step++) {
                b.force(Phase.FRAME, 3 + step, 5 + step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, 11 - step, 5 + step, z, p.timber.defaultBlockState());
            }
        }
        for (int z = 5; z <= 9; z++) {
            b.force(Phase.FRAME, 7, 9, z, p.timber.defaultBlockState());
        }
        addMineCrossBracing(b, p);
        // Hang the chain from the crown beam.  The old y=11..14 run climbed unsupported into the
        // sky and made the lantern below it look like the structural anchor.
        for (int y = 6; y <= 8; y++) {
            b.put(Phase.FRAME, 7, y, 7, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.put(Phase.DECOR, 7, 5, 7, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        addMineToolShed(b, p, 0, 5, 8, 13, Direction.EAST);
        // Keep the east shed clear of the rear headframe braces.  Its earlier west edge sat on
        // the x=9 brace line, visually offering an opening that was actually impassable.
        addMineToolShed(b, p, 10, 14, 8, 13, Direction.WEST);
        addMineAditArch(b, p);
        b.put(Phase.FOUNDATION, 9, 0, 10, p.foundation.defaultBlockState());
        fixture(b, m, 2, 1, 11, Blocks.STONECUTTER, new BlockPos(3, 1, 11));
        fixture(b, m, 12, 1, 12, Blocks.BLAST_FURNACE, new BlockPos(11, 1, 12));
        // Approach the chest from the rear; the headframe's west A-leg intentionally occupies
        // (3,1,9) and should read as structure, not masquerade as fixture circulation.
        fixture(b, m, 2, 1, 9, Blocks.CHEST, new BlockPos(2, 1, 10));
        m.entranceInside = new BlockPos(7, 1, 1);
        for (int z = 1; z <= 13; z++) {
            m.reservedAir.add(new BlockPos(7, 2, z));
        }
        m.wallHeight = 6;
        m.roofPeak = 9;
        addIndustryMineProgram(b, m, p, "headframe");
    }

    private static void marketRotunda(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MARKET_SQUARE, 17, 17, 12, false);
        addMarketFloor(b, p, 17, 17);
        addMarketArcade(b, p);
        addMarketArcadeTrim(b, p);
        // Full timber corbels replace the two partial stair cells above these lamps. They retain
        // the stepped fascia rhythm while giving each hanging lantern a real center-bearing
        // underside, so neither fixture can pop when its roof neighbor updates.
        for (int x : new int[] {6, 10}) {
            b.force(Phase.FRAME, x, 4, 17, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        // Lanterns hang directly from the rear arcade corbels and illuminate the full three-wide
        // stage apron without introducing freestanding obstacles into the public cross aisle.
        addHangingLantern(b, 6, 3, 17);
        addHangingLantern(b, 10, 3, 17);
        addDistinctMarketBay(b, m, p, 3, 3, Direction.SOUTH, Blocks.COMPOSTER, Blocks.WOOL.yellow());
        addDistinctMarketBay(b, m, p, 13, 3, Direction.SOUTH, Blocks.LOOM, Blocks.WOOL.green());
        addDistinctMarketBay(b, m, p, 3, 13, Direction.NORTH, Blocks.FLETCHING_TABLE, Blocks.WOOL.red());
        addDistinctMarketBay(b, m, p, 13, 13, Direction.NORTH, Blocks.STONECUTTER, Blocks.WOOL.blue());
        addBellRotunda(b, p, 8, 8);
        addBellRotundaBracing(b, p, 8, 8);
        for (int x = 7; x <= 9; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.accent.defaultBlockState());
            b.put(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        m.entranceInside = new BlockPos(8, 1, 1);
        m.accessTargets.add(new BlockPos(8, 1, 8));
        reserveCrossAisle(m, 8, 1, 15, 3);
        m.wallHeight = 5;
        m.roofPeak = 11;
        addMarketInteriorProgram(b, m, p, "rotunda");
    }

    private static void guardGateTower(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GUARD_POST, 11, 11, 18, true);
        addFloor(b, p, 11, 11);
        int center = 5;
        // Battered lower keep with projecting corner buttresses.
        for (int y = 1; y <= 4; y++) {
            int inset = y >= 4 ? 1 : 0;
            for (int x = inset; x <= 10 - inset; x++) {
                for (int z = inset; z <= 10 - inset; z++) {
                    if (!edgeInRect(x, z, inset, 10 - inset, inset, 10 - inset)
                            || (z == 0 && x == center && y <= 2)) {
                        continue;
                    }
                    boolean slit = y == 3 && ((x == center && z == 10)
                            || (z == center && (x == inset || x == 10 - inset)));
                    b.put(slit ? Phase.OPENING : Phase.SHELL, x, y, z,
                            slit ? Blocks.IRON_BARS.defaultBlockState()
                                    : p.foundation.defaultBlockState());
                }
            }
        }
        // A complete transfer deck seals the lower guard room and carries the narrower shaft.
        // The backed ladder below is applied afterward and deliberately cuts its one-cell route.
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                b.force(Phase.FOUNDATION, x, 4, z, p.foundation.defaultBlockState());
            }
        }
        addDoor(b, p, center, 0);
        // Narrow stone shaft, then an overhanging timber watch room on brackets.
        for (int y = 5; y <= 11; y++) {
            for (int x = 2; x <= 8; x++) {
                for (int z = 2; z <= 9; z++) {
                    if (edgeInRect(x, z, 2, 8, 2, 9)) {
                        boolean slit = y == 7 && ((x == 5 && z == 2)
                                || (z == 5 && (x == 2 || x == 8)));
                        b.put(slit ? Phase.OPENING : Phase.SHELL, x, y, z,
                                slit ? Blocks.IRON_BARS.defaultBlockState()
                                        : p.foundation.defaultBlockState());
                    }
                }
            }
        }
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                b.force(Phase.FOUNDATION, x, 12, z, p.timber.defaultBlockState());
            }
        }
        for (int y = 13; y <= 14; y++) {
            for (int x = 1; x <= 9; x++) {
                for (int z = 1; z <= 9; z++) {
                    if (!edgeInRect(x, z, 1, 9, 1, 9)) {
                        continue;
                    }
                    boolean window = y == 13 && (x == 5 || z == 5);
                    b.put(window ? Phase.OPENING : Phase.SHELL, x, y, z,
                            window ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.timber.defaultBlockState());
                }
            }
        }
        addGuardTowerDetail(b, p);
        addFlaredWatchRoof(b, m, p);
        addWatchSignalArch(b, p, 5);
        for (int y = 1; y <= 11; y++) {
            b.force(Phase.FRAME, center, y, 9, p.foundation.defaultBlockState());
            b.force(Phase.FIXTURE, center, y, 8,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.force(Phase.FIXTURE, center, 12, 8, Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(
                new BlockPos(center, 1, 8), new BlockPos(center, 11, 8)));
        m.accessTargets.add(new BlockPos(center, 13, 7));
        fixture(b, m, 2, 1, 7, Blocks.FLETCHING_TABLE, new BlockPos(3, 1, 7));
        fixture(b, m, 8, 1, 7, Blocks.GRINDSTONE, new BlockPos(7, 1, 7));
        fixture(b, m, 2, 1, 4, Blocks.CHEST, new BlockPos(3, 1, 4));
        m.reserveCentralAisle(center, 1, 7);
        m.wallHeight = 14;
        m.enclosed = true;
        m.entranceInside = new BlockPos(center, 1, 1);
        m.interiorSamples.add(new BlockPos(center, 1, 5));
        addGuardInteriorProgram(b, m, p, "watchtower");
    }

    private static void exchangeCivicHall(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.EXCHANGE_HALL, 19, 15, 16, true);
        addFloor(b, p, 19, 15);
        int center = 9;
        // Four-block side aisles support a nine-block clerestory nave.
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x <= 18; x++) {
                for (int z = 0; z <= 14; z++) {
                    if (!edge(x, z, 19, 15) || (z == 0 && x == center && y <= 2)) {
                        continue;
                    }
                    boolean window = y >= 2 && y <= 3
                            && ((z == 0 && (x == 3 || x == 15))
                                    || (z == 14 && (x == 3 || x == 15))
                                    || ((x == 0 || x == 18) && (z == 4 || z == 10)));
                    b.put(window ? Phase.OPENING : Phase.SHELL, x, y, z,
                            window ? Blocks.GLASS_PANE.defaultBlockState()
                                    : (corner(x, z, 19, 15) ? p.accent : p.foundation)
                                            .defaultBlockState());
                }
            }
        }
        addDoor(b, p, center, 0);
        for (int x = 5; x <= 13; x++) {
            for (int z : new int[] {0, 14}) {
                for (int y = 5; y <= 9; y++) {
                    boolean glass = y == 6 && (x == 6 || x == 9 || x == 12);
                    b.put(glass ? Phase.OPENING : Phase.SHELL, x, y, z,
                            glass ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.wall.defaultBlockState());
                }
            }
        }
        for (int z = 1; z <= 13; z++) {
            for (int x : new int[] {5, 13}) {
                for (int y = 5; y <= 9; y++) {
                    boolean glass = y == 6 && z % 3 == 1;
                    b.put(glass ? Phase.OPENING : Phase.SHELL, x, y, z,
                            glass ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.wall.defaultBlockState());
                }
            }
        }
        addCivicClerestoryFrame(b, p);
        addCivicLowerFacadeDetail(b, p);
        addCivicAisleRoofs(b, p);
        addRectGableRoof(b, m, p, 5, 13, 0, 14, 9, Direction.Axis.X);
        addCivicPortico(b, m, p);
        addCivicPorticoDetail(b, p);
        addCivicCupola(b, m, p);
        addCivicCupolaDetail(b, p);
        fixture(b, m, center, 1, 10, BankerProfessionSupport.exchangeDeskOrLectern(),
                new BlockPos(center, 1, 9));
        for (int x : new int[] {2, 4, 14, 16}) {
            fixture(b, m, x, 1, 12, Blocks.BOOKSHELF, new BlockPos(x, 1, 11));
        }
        fixture(b, m, 3, 1, 9, Blocks.ENDER_CHEST, new BlockPos(4, 1, 9));
        fixture(b, m, 15, 1, 9, Blocks.CHEST, new BlockPos(14, 1, 9));
        addCivicTellerRail(b, p, 6, 12, 11, center);
        addCivicInteriorDetail(b, p);
        addRearWallSconce(b, 7, 2, 15);
        addRearWallSconce(b, 11, 2, 15);
        m.reserveCentralAisle(center, 1, 9);
        m.wallHeight = 9;
        m.enclosed = true;
        m.entranceInside = new BlockPos(center, 1, 1);
        m.interiorSamples.add(new BlockPos(center, 1, 7));
        m.interiorSamples.add(new BlockPos(3, 1, 7));
        addExchangeInteriorProgram(b, m, p, "civic_hall");
    }

    /*
     * Compact revision-2 masters. These retain village-scale footprints but add a composed
     * secondary mass, useful interior and unmistakable roofline instead of serving as palette
     * aliases of the retired revision-1 buildings they build upon.
     */

    private static void cottageBayCompact(Builder b, Metadata m, Materials p) {
        cottageHearth(b, m, p);
        m.height = 10;
        addBayWindow(b, p, 8, 4, Direction.EAST);
        addFrontDormer(b, m, p, 4, 6);
        addCatslideHearthWing(b, p);
        // The broad entry porch already hangs a lantern beside the front window; frame the rear
        // bay instead so both details remain readable and collision-free.
        addDeepWindowFrame(b, p, 2, 8, 2, 3, Direction.SOUTH);
        // Revision one intentionally stays byte-for-byte stable; this rev2 derivative owns the
        // permanent domestic hearth that turns its inherited chimney into a usable feature.
        fixture(b, m, 1, 1, 6, Blocks.SMOKER, new BlockPos(2, 1, 6));
        addCottageInteriorProgram(b, m, p, "bay");
    }

    private static void houseHallCompact(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 11, 11, 12, true);
        // A true T-plan: the narrow entrance hall meets a broad rear family wing. Keeping the
        // floor as the authored footprint (instead of hiding a rectangle below it) makes this
        // compact house read differently from the cottage from every elevation.
        Footprint footprint = (x, z) -> (x >= 3 && x <= 7) || z >= 5;
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 4, 0, 2, 3);
        windowColumn(windows, 6, 0, 2, 3);
        windowColumn(windows, 1, 5, 2, 3);
        windowColumn(windows, 9, 5, 2, 3);
        windowColumn(windows, 0, 8, 2, 3);
        windowColumn(windows, 10, 8, 2, 3);
        windowColumn(windows, 2, 10, 2, 3);
        windowColumn(windows, 8, 10, 2, 3);
        addFootprintShell(b, m, p, footprint, 4, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addRectGableRoof(b, m, p, 3, 7, 0, 10, 4, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 10, 5, 10, 4, Direction.Axis.Z);
        addLayeredEntryPorch(b, p, 5, 2, 4);
        addBayWindow(b, p, 0, 8, Direction.WEST);
        addBayWindow(b, p, 10, 8, Direction.EAST);
        addBed(b, m, 1, 7, Direction.EAST, 3, 7);
        addBed(b, m, 9, 7, Direction.WEST, 7, 7);
        addBed(b, m, 1, 9, Direction.EAST, 3, 9);
        fixture(b, m, 2, 1, 6, Blocks.LOOM, new BlockPos(3, 1, 6));
        fixture(b, m, 8, 1, 9, Blocks.CHEST, new BlockPos(7, 1, 9));
        fixture(b, m, 8, 1, 6, Blocks.CRAFTING_TABLE, new BlockPos(7, 1, 6));
        addTable(b, p, 4, 8);
        m.reserveCentralAisle(5, 1, 9);
        m.interiorSamples.add(new BlockPos(2, 1, 8));
        m.interiorSamples.add(new BlockPos(8, 1, 8));
        addHouseInteriorProgram(b, m, p, "hall");
    }

    private static void innWayfarerCompact(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.INN, 13, 11, 12, true);
        // The wayfarer's inn wraps an open forecourt: a west lodging range, rear taproom and
        // narrow central entry spine form an asymmetric hooked plan rather than another shed.
        Footprint footprint = (x, z) -> x <= 4 || z >= 5 || (x >= 5 && x <= 7);
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 2, 0, 2, 3);
        windowColumn(windows, 6, 0, 2, 3);
        windowColumn(windows, 4, 3, 2, 3);
        windowColumn(windows, 9, 5, 2, 3);
        windowColumn(windows, 12, 7, 2, 3);
        windowColumn(windows, 12, 9, 2, 3);
        windowColumn(windows, 2, 10, 2, 3);
        windowColumn(windows, 9, 10, 2, 3);
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addRectGableRoof(b, m, p, 0, 4, 0, 10, 5, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 12, 5, 10, 5, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 5, 7, 0, 6, 5, Direction.Axis.X);
        for (int x = 5; x <= 7; x++) {
            for (int z = 7; z <= 8; z++) {
                b.force(Phase.ROOF, x, 8, z, doubleRoofSlab(p));
            }
        }
        addLayeredEntryPorch(b, p, 6, 2, 4);
        addCoachhouseBalcony(b, p, 0, 4, 5);
        addBayWindow(b, p, 12, 8, Direction.EAST);
        addRearServiceCanopy(b, p, 7, 11, 10, 5);
        addIntegratedChimney(b, m, p, 11, 9);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 1, 7, Direction.EAST, 3, 7);
        addBed(b, m, 11, 7, Direction.WEST, 9, 7);
        fixture(b, m, 2, 1, 9, Blocks.SMOKER, new BlockPos(3, 1, 9));
        fixture(b, m, 8, 1, 9, Blocks.BREWING_STAND, new BlockPos(7, 1, 9));
        fixture(b, m, 10, 1, 9, Blocks.CHEST, new BlockPos(9, 1, 9));
        fixture(b, m, 10, 1, 6, Blocks.CAKE, new BlockPos(9, 1, 6));
        addLongTable(b, p, 2, 6, 3);
        m.reserveCentralAisle(6, 1, 9);
        m.interiorSamples.add(new BlockPos(2, 1, 6));
        m.interiorSamples.add(new BlockPos(10, 1, 8));
        addInnInteriorProgram(b, m, p, "wayfarer");
    }

    private static void warehouseGabledCompact(Builder b, Metadata m, Materials p) {
        warehouseBay(b, m, p);
        m.height = 10;
        // The taller dock replaces the retired hall's low porch lamps.
        b.remove(5, 2, -1);
        b.remove(7, 2, -1);
        addTallWarehousePortal(b, p, 6);
        addLoadingDock(b, p, 2, 10);
        addCoveredWoodBay(b, p, 13, 3, 7);
        addWarehouseRoofMonitor(b, m, p, 3, 9);
        addPortalHoist(b, p, 6);
        // This is deliberately a working cart hall rather than a sealed dwelling: the three-wide
        // loading throat stays open so villagers and cargo can pass directly onto the dock.
        m.enclosed = false;
        addIndustryWarehouseProgram(b, m, p, "gabled");
    }

    private static void granaryCruckCompact(Builder b, Metadata m, Materials p) {
        granaryLoft(b, m, p);
        m.height = 13;
        addCruckCrossGable(b, m, p);
        addGrainChute(b, p, 10, 4);
        addProjectingGrainBin(b, p, 11, 5);
        addIndustryGranaryProgram(b, m, p, "cruck");
    }

    private static void smithyLaneCompact(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.SMITHY, 13, 9, 11, true);
        // An L-plan separates the enclosed lane workshop from its lower forge wing. The latter
        // has its own roof and broad masonry stack, so the smithy cannot collapse visually into
        // the warehouse's long rectangular loading shed.
        Footprint footprint = (x, z) -> x <= 7 || z >= 4;
        Set<BlockPos> windows = new HashSet<>();
        windowColumn(windows, 2, 0, 2, 3);
        windowColumn(windows, 7, 2, 2, 3);
        windowColumn(windows, 10, 4, 2, 3);
        windowColumn(windows, 12, 6, 2, 3);
        windowColumn(windows, 2, 8, 2, 3);
        windowColumn(windows, 9, 8, 2, 3);
        addFootprintShell(b, m, p, footprint, 4, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addRectGableRoof(b, m, p, 0, 7, 0, 8, 4, Direction.Axis.X);
        addRectGableRoof(b, m, p, 7, 12, 4, 8, 4, Direction.Axis.Z);
        addLayeredEntryPorch(b, p, 6, 2, 4);
        addForgeGantry(b, p, 8, 12, 5);
        // The forge gantry meets the east gable end here; use a full bearing instead of leaving
        // the gable wall hovering over the gantry's lower slab.
        b.force(Phase.ROOF, 12, 5, 5, doubleRoofSlab(p));
        addCoveredWoodBay(b, p, -1, 3, 7);
        addBroadForgeStack(b, m, p);
        fixture(b, m, 2, 1, 7, Blocks.ANVIL, new BlockPos(3, 1, 7));
        fixture(b, m, 5, 1, 7, Blocks.BLAST_FURNACE, new BlockPos(5, 1, 6));
        fixture(b, m, 9, 1, 7, Blocks.SMITHING_TABLE, new BlockPos(8, 1, 7));
        fixture(b, m, 11, 1, 7, Blocks.GRINDSTONE, new BlockPos(10, 1, 7));
        fixture(b, m, 2, 1, 4, Blocks.CAULDRON, new BlockPos(3, 1, 4));
        fixture(b, m, 10, 1, 5, Blocks.CHEST, new BlockPos(9, 1, 5));
        addWoodRack(b, p, 11, 3);
        // The outer rack cap projects beyond the L-shaped workshop. Give that end a genuine
        // ground-bearing post; the rack contents remain fixtures and cannot masquerade as roof
        // support in the geometry adapter.
        b.force(Phase.FOUNDATION, 13, 0, 3, p.foundation.defaultBlockState());
        b.force(Phase.FRAME, 13, 1, 3, p.timber.defaultBlockState());
        m.reserveCentralAisle(6, 1, 7);
        m.interiorSamples.add(new BlockPos(9, 1, 6));
        addIndustrySmithyProgram(b, m, p, "lane");
    }

    private static void mineAditCompact(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MINE_ENTRANCE, 11, 11, 11, false);
        // A rail throat with unequal machinery sheds leaves a deliberately broken footprint.
        // This is an open industrial adit, not a house shell wearing a headframe ornament.
        Footprint footprint = (x, z) -> (x >= 4 && x <= 6)
                || (x <= 4 && z >= 5)
                || (x >= 6 && z >= 7);
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (footprint.contains(x, z)) {
                    b.put(Phase.FOUNDATION, x, 0, z,
                            (footprintBoundary(m, footprint, x, z)
                                    ? p.foundation : p.floor).defaultBlockState());
                }
            }
        }
        for (int z = 5; z <= 10; z++) {
            post(b, Phase.SHELL, 0, 1, z, 4, Blocks.DEEPSLATE_BRICKS);
        }
        for (int z = 7; z <= 10; z++) {
            post(b, Phase.SHELL, 10, 1, z, 4, Blocks.DEEPSLATE_BRICKS);
        }
        for (int x = 0; x <= 10; x++) {
            post(b, Phase.SHELL, x, 1, 10, 4, Blocks.DEEPSLATE_BRICKS);
        }
        for (int x = -1; x <= 4; x++) {
            for (int z = 4; z <= 11; z++) {
                b.force(Phase.ROOF, x, 5 + Math.max(0, x) / 3, z,
                        p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        for (int x = 6; x <= 11; x++) {
            for (int z = 6; z <= 11; z++) {
                b.force(Phase.ROOF, x, 5 + Math.max(0, 10 - x) / 3, z,
                        p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            }
        }
        for (int z = -2; z <= 9; z++) {
            b.force(Phase.FOUNDATION, 5, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.FIXTURE, 5, 1, z, Blocks.RAIL.defaultBlockState());
            m.reservedAir.add(new BlockPos(5, 2, z));
        }
        for (int x : new int[] {2, 8}) {
            b.force(Phase.FOUNDATION, x, 0, 2, p.foundation.defaultBlockState());
        }
        addCompactHeadframe(b, p, 5, 2, 3, 9);
        addFortifiedAditPortal(b, m, p);
        fixture(b, m, 1, 1, 8, Blocks.CHEST, new BlockPos(2, 1, 8));
        fixture(b, m, 3, 1, 8, Blocks.STONECUTTER, new BlockPos(4, 1, 8));
        fixture(b, m, 9, 1, 9, Blocks.BLAST_FURNACE, new BlockPos(8, 1, 9));
        m.entranceInside = new BlockPos(5, 1, 1);
        m.wallHeight = 4;
        m.roofPeak = Math.max(m.roofPeak, 9);
        m.interiorSamples.add(new BlockPos(2, 1, 7));
        m.interiorSamples.add(new BlockPos(8, 1, 8));
        addIndustryMineProgram(b, m, p, "adit");
    }

    private static void marketCrossroadsCompact(Builder b, Metadata m, Materials p) {
        marketCloister(b, m, p);
        m.height = 10;
        addMarketCrossroadsArms(b, p, 7, 7);
        addCrossroadsBellLantern(b, p, 7, 7);
        // Keep the inherited south arch as a lit gateway; the central rotunda now owns the bell.
        b.force(Phase.DECOR, 7, 3, 12, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        addMarketCornerSign(b, p, 2, 12);
        addMarketCornerSign(b, p, 12, 2);
        m.roofPeak = 10;
        addMarketInteriorProgram(b, m, p, "crossroads");
    }

    private static void guardBlockhouseCompact(Builder b, Metadata m, Materials p) {
        guardWatch(b, m, p);
        addGuardButtress(b, p, -1, 2, 6);
        addGuardButtress(b, p, 9, 2, 6);
        addGateMachicolation(b, p, 4, 9);
        addBlockhouseFightingGallery(b, p);
        // Rear gallery sconces illuminate the narrow patrol shelf without occupying its route.
        addRearWallSconce(b, 2, 5, 9);
        addRearWallSconce(b, 6, 5, 9);
        addGuardInteriorProgram(b, m, p, "blockhouse");
    }

    private static void exchangeBranchCompact(Builder b, Metadata m, Materials p) {
        exchangeHall(b, m, p);
        m.height = 13;
        addBayWindow(b, p, 0, 5, Direction.WEST);
        addBayWindow(b, p, 14, 5, Direction.EAST);
        addFrontDormer(b, m, p, 4, 8);
        addFrontDormer(b, m, p, 10, 8);
        addBranchCivicFrontispiece(b, m, p);
        addRearServiceCanopy(b, p, 4, 10, 10, 4);
        // The two dormer shoulders are real sheltered service ledges. Low standing lanterns sit
        // on their full roof bearings and remove the otherwise spawnable shadow below the caps.
        b.put(Phase.DECOR, 2, 8, -1, Blocks.LANTERN.defaultBlockState());
        b.put(Phase.DECOR, 12, 8, -1, Blocks.LANTERN.defaultBlockState());
        addExchangeInteriorProgram(b, m, p, "branch");
    }

    /*
     * Grand revision-2 masters. Their larger envelopes are not scaled-up copies: each owns a
     * different plan family, skyline, public threshold and role-readable working interior.
     */

    private static void cottageCourtyard(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 15, 13, 12, true);
        // An asymmetrical L-shaped dwelling wraps a deep garden court.  This deliberately avoids
        // the coachhouse's scaled U plan: the east forecourt remains an open domestic work garden
        // instead of becoming a second full-height residential wing.
        Footprint footprint = (x, z) -> z >= 5 || x <= 4 || (x >= 6 && x <= 8);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 12}) {
            windowColumn(windows, x, 0, 2, 3);
            windowColumn(windows, x, 12, 2, 3);
        }
        for (int z : new int[] {3, 9}) {
            windowColumn(windows, 0, z, 2, 3);
            if (z >= 5) {
                windowColumn(windows, 14, z, 2, 3);
            }
        }
        windowColumn(windows, 4, 3, 2, 3);
        addFootprintShell(b, m, p, footprint, 4, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addRectGableRoof(b, m, p, 0, 4, 0, 12, 4, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 14, 5, 12, 4, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 6, 8, 0, 6, 4, Direction.Axis.X);
        addLayeredEntryPorch(b, p, 7, 2, 4);
        // A lit garden trellis spans the two narrow forecourts and makes this domestic court
        // unmistakable from above and at street level. Its corner posts are ground-bearing, the
        // center entrance retains two clear blocks, and the low open lattice stays below the
        // four surrounding roof masses rather than introducing another coachhouse-like gable.
        // The paired one-bay walks occupy the two genuine open forecourts. Keeping the lattice
        // out of x=6..8 avoids intersecting the inhabited central entry wing while retaining a
        // strong twin-trellis top silhouette that the coachhouse does not share.
        addGardenPergola(b, p, 5, 5, 1, 4);
        addGardenPergola(b, p, 9, 14, 1, 4);
        addDeepWindowFrame(b, p, 2, 0, 2, 3, Direction.NORTH);
        addCoveredWoodBay(b, p, 15, 7, 10);
        addIntegratedChimney(b, m, p, 13, 10);
        // Cross-gable intersections need full bearing cells beneath the next roof course. Bottom
        // slabs here would leave the half-block daylight gaps that motivated the authored pass.
        b.force(Phase.ROOF, 7, 7, 7, doubleRoofSlab(p));
        for (int x : new int[] {2}) {
            for (int z = 8; z <= 9; z++) {
                b.force(Phase.ROOF, x, 8, z, doubleRoofSlab(p));
            }
        }
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 1, 9, Direction.EAST, 3, 9);
        addBed(b, m, 13, 9, Direction.WEST, 11, 9);
        fixture(b, m, 4, 1, 10, Blocks.SMOKER, new BlockPos(5, 1, 10));
        fixture(b, m, 10, 1, 10, Blocks.CRAFTING_TABLE, new BlockPos(9, 1, 10));
        fixture(b, m, 7, 1, 11, Blocks.CHEST, new BlockPos(7, 1, 10));
        addLongTable(b, p, 4, 7, 3);
        m.reserveCentralAisle(7, 1, 10);
        m.interiorSamples.add(new BlockPos(2, 1, 7));
        m.interiorSamples.add(new BlockPos(12, 1, 7));
        addCottageInteriorProgram(b, m, p, "courtyard");
    }

    private static void houseArcade(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 17, 15, 16, true);
        Footprint footprint = (x, z) -> z >= 4 || (x >= 4 && x <= 12);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {5, 11}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 14, 2, 4);
        }
        for (int z : new int[] {6, 10, 13}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 16, z, 2, 4);
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        addRectGableRoof(b, m, p, 0, 16, 4, 14, 6, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 4, 12, 0, 8, 6, Direction.Axis.X);
        // The crossing gables meet at different slab heights; make the center bearing solid so
        // the upper ridge cannot visibly hover a half block above the lower roof course.
        b.force(Phase.ROOF, 8, 12, 9, doubleRoofSlab(p));
        addLayeredEntryPorch(b, p, 8, 3, 5);
        addDeepWindowFrame(b, p, 5, 0, 2, 4, Direction.NORTH);
        addDeepWindowFrame(b, p, 11, 0, 2, 4, Direction.NORTH);
        addBayWindow(b, p, 16, 9, Direction.EAST);
        addBayWindow(b, p, 0, 9, Direction.WEST);
        addRearServiceCanopy(b, p, 5, 11, 14, 5);
        addIntegratedChimney(b, m, p, 14, 10);
        for (int[] bed : new int[][] {{1, 7, 3, 7}, {15, 7, 13, 7},
                {1, 12, 3, 12}, {15, 12, 13, 12}}) {
            Direction facing = bed[0] < 8 ? Direction.EAST : Direction.WEST;
            addBed(b, m, bed[0], bed[1], facing, bed[2], bed[3]);
        }
        fixture(b, m, 3, 1, 13, Blocks.LOOM, new BlockPos(4, 1, 13));
        fixture(b, m, 13, 1, 13, Blocks.CRAFTING_TABLE, new BlockPos(12, 1, 13));
        fixture(b, m, 6, 1, 12, Blocks.CHEST, new BlockPos(7, 1, 12));
        addLongTable(b, p, 8, 13, 5);
        m.reserveCentralAisle(8, 1, 12);
        m.interiorSamples.add(new BlockPos(2, 1, 9));
        m.interiorSamples.add(new BlockPos(14, 1, 9));
        addHouseInteriorProgram(b, m, p, "arcade");
    }

    private static void innCoachhouse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.INN, 21, 17, 16, true);
        Footprint footprint = (x, z) -> z >= 6 || x <= 5 || x >= 15
                || (x >= 9 && x <= 11);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 18}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 16, 2, 4);
        }
        for (int x : new int[] {7, 13}) {
            windowColumn(windows, x, 16, 2, 4);
        }
        for (int z : new int[] {3, 9, 13}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 20, z, 2, 4);
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        addRectGableRoof(b, m, p, 0, 5, 0, 16, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 15, 20, 0, 16, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 20, 6, 16, 6, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 9, 11, 0, 7, 6, Direction.Axis.X);
        // Solid valley bearings close the half-slab seams where the coach wings, rear hall and
        // narrow entrance gable overlap. They also keep each upper roof course load-bearing.
        b.force(Phase.ROOF, 10, 9, 8, doubleRoofSlab(p));
        for (int x : new int[] {2, 3, 17, 18}) {
            for (int z : new int[] {9, 13}) {
                b.force(Phase.ROOF, x, 10, z, doubleRoofSlab(p));
            }
        }
        addLayeredEntryPorch(b, p, 10, 2, 5);
        addCoachhouseBalcony(b, p, 1, 5, 5);
        addCoachhouseBalcony(b, p, 15, 19, 5);
        addIntegratedChimney(b, m, p, 19, 14);
        for (int z : new int[] {3, 9, 13}) {
            addBed(b, m, 1, z, Direction.EAST, 3, z);
            addBed(b, m, 19, z, Direction.WEST, 17, z);
        }
        fixture(b, m, 4, 1, 15, Blocks.SMOKER, new BlockPos(5, 1, 15));
        fixture(b, m, 7, 1, 15, Blocks.BREWING_STAND, new BlockPos(8, 1, 15));
        fixture(b, m, 13, 1, 15, Blocks.CAKE, new BlockPos(12, 1, 15));
        fixture(b, m, 16, 1, 15, Blocks.CHEST, new BlockPos(15, 1, 15));
        addLongTable(b, p, 5, 10, 5);
        m.reserveCentralAisle(10, 1, 15);
        m.interiorSamples.add(new BlockPos(3, 1, 4));
        m.interiorSamples.add(new BlockPos(17, 1, 4));
        addInnInteriorProgram(b, m, p, "coachhouse");
    }

    private static void warehouseCraneHall(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.WAREHOUSE, 21, 15, 15, true);
        addGabledShell(b, m, p, 5, Direction.Axis.Z, Porch.OPEN);
        int center = 10;
        for (int x = center - 2; x <= center + 2; x++) {
            for (int y = 1; y <= 4; y++) {
                b.remove(x, y, 0);
            }
        }
        for (int x : new int[] {center - 3, center + 3}) {
            post(b, Phase.FRAME, x, 1, 0, 5, p.timber);
        }
        beam(b, Phase.FRAME, center - 3, center + 3, 5, 0, p.timber);
        addLoadingDock(b, p, 3, 17);
        addFrontLoadingHoistHouse(b, m, p, center);
        addWarehouseQuayCrane(b, p, 21, 8, 12);
        addRearServiceCanopy(b, p, 2, 7, 14, 5);
        addRearServiceCanopy(b, p, 13, 18, 14, 5);
        // A grounded transverse inspection beam keeps the broad loading floor naturally lit
        // without suspending an implausibly long chain from the crane-hall ridge.
        addInteriorLightingBeamX(b, p, 1, 19, 5, 7);
        for (int z : new int[] {4, 8, 12}) {
            fixture(b, m, 2, 1, z, Blocks.CHEST, new BlockPos(3, 1, z));
            fixture(b, m, 18, 1, z, Blocks.CHEST, new BlockPos(17, 1, z));
        }
        fixture(b, m, 5, 1, 13, Blocks.LOOM, new BlockPos(6, 1, 13));
        fixture(b, m, 15, 1, 13, Blocks.STONECUTTER, new BlockPos(14, 1, 13));
        fixture(b, m, center - 2, 1, 13, Blocks.CRAFTING_TABLE,
                new BlockPos(center - 1, 1, 13));
        m.reserveCentralAisle(center, 1, 13);
        m.enclosed = false;
        addIndustryWarehouseProgram(b, m, p, "crane");
    }

    private static void granaryWindmill(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GRANARY, 17, 15, 20, true);
        addGabledShell(b, m, p, 7, Direction.Axis.X, Porch.OPEN);
        addLayeredEntryPorch(b, p, 8, 2, 5);
        addFootprintTimberBand(b, m, p, (x, z) -> true, 4);
        addFootprintTimberBand(b, m, p, (x, z) -> true, 7);
        addWindmillGrainIntake(b, m, p);
        addWindmillSailCross(b, p, 17, 9, 11, 5);
        addGrainChute(b, p, 16, 12);
        // The chute cap lies beneath the broad sail and is therefore weather-covered. Treat it as
        // a tiny service landing with its own grounded lamp instead of leaving a dark spawn shelf.
        b.put(Phase.DECOR, 17, 9, 12, Blocks.LANTERN.defaultBlockState());
        addWindmillRoofedGrainBin(b, m, p);
        // A low tie beam carries a short central work lantern without dangling a long chain from
        // the windmill's high ridge or wallpapering the perimeter with torches.
        addInteriorLightingBeamX(b, p, 1, 15, 5, 7);
        addIntegratedChimney(b, m, p, 2, 12);
        for (int x : new int[] {2, 5, 11, 14}) {
            fixture(b, m, x, 1, 13, Blocks.HAY_BLOCK,
                    new BlockPos(x < 8 ? x + 1 : x - 1, 1, 13));
            b.put(Phase.FIXTURE, x, 2, 13, Blocks.HAY_BLOCK.defaultBlockState());
        }
        fixture(b, m, 3, 1, 10, Blocks.SMOKER, new BlockPos(4, 1, 10));
        fixture(b, m, 13, 1, 10, Blocks.CHEST, new BlockPos(12, 1, 10));
        fixture(b, m, 6, 1, 12, Blocks.CRAFTING_TABLE, new BlockPos(7, 1, 12));
        // Paired wall-backed sconces keep the rear service apron safe without obstructing grain.
        addRearWallSconce(b, 7, 2, 15);
        addRearWallSconce(b, 11, 2, 15);
        m.reserveCentralAisle(8, 1, 13);
        m.interiorSamples.add(new BlockPos(8, 1, 8));
        addIndustryGranaryProgram(b, m, p, "windmill");
    }

    private static void smithyHammerhall(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.SMITHY, 19, 15, 15, true);
        addGabledShell(b, m, p, 5, Direction.Axis.Z, Porch.WORKSHOP);
        // Open the east forge loggia while retaining a fully enclosed hammer hall to the west.
        for (int z = 4; z <= 10; z++) {
            for (int y = 1; y <= 3; y++) {
                b.remove(18, y, z);
            }
        }
        for (int z : new int[] {4, 7, 10}) {
            post(b, Phase.FRAME, 18, 1, z, 5, p.timber);
            post(b, Phase.FRAME, 20, 1, z, 5, p.timber);
        }
        for (int z = 4; z <= 10; z++) {
            b.force(Phase.FOUNDATION, 19, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.FOUNDATION, 20, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.ROOF, 18, 6, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 19, 6, z, p.roofSlab.defaultBlockState());
            b.force(Phase.ROOF, 20, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        addForgeGantry(b, p, 2, 8, 5);
        addHammerhallForgeHood(b, p);
        // The hammer floor needs its own low, grounded bearing: the forge glow does not reach the
        // center of this long hall, and a short work pendant reads naturally above the anvils.
        addInteriorLightingBeamX(b, p, 1, 17, 5, 7);
        addIntegratedChimney(b, m, p, 15, 12);
        addIntegratedChimney(b, m, p, 17, 12);
        beam(b, Phase.FRAME, 14, 18, 10, 12, p.chimney);
        fixture(b, m, 2, 1, 12, Blocks.ANVIL, new BlockPos(3, 1, 12));
        fixture(b, m, 5, 1, 12, Blocks.GRINDSTONE, new BlockPos(5, 1, 11));
        fixture(b, m, 13, 1, 12, Blocks.SMITHING_TABLE, new BlockPos(12, 1, 12));
        fixture(b, m, 16, 1, 11, Blocks.BLAST_FURNACE, new BlockPos(15, 1, 11));
        fixture(b, m, 3, 1, 8, Blocks.CAULDRON, new BlockPos(4, 1, 8));
        fixture(b, m, 15, 1, 8, Blocks.CHEST, new BlockPos(14, 1, 8));
        addWoodRack(b, p, 1, 13);
        addRearWallSconce(b, 7, 2, 15);
        addRearWallSconce(b, 11, 2, 15);
        m.reserveCentralAisle(9, 1, 13);
        m.enclosed = false;
        addIndustrySmithyProgram(b, m, p, "hammerhall");
    }

    private static void mineWindingHouse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MINE_ENTRANCE, 19, 17, 18, true);
        addGabledShell(b, m, p, 5, Direction.Axis.X, Porch.OPEN);
        int center = 9;
        for (int x = center - 1; x <= center + 1; x++) {
            for (int y = 1; y <= 4; y++) {
                b.remove(x, y, 0);
            }
        }
        for (int z = 0; z <= 15; z++) {
            b.force(Phase.FOUNDATION, center, 0, z, p.foundation.defaultBlockState());
            b.put(Phase.FIXTURE, center, 1, z, Blocks.RAIL.defaultBlockState());
            m.reservedAir.add(new BlockPos(center, 2, z));
        }
        for (int x : new int[] {center - 3, center + 3}) {
            post(b, Phase.FRAME, x, 1, 5, 12, p.timber);
            post(b, Phase.FRAME, x, 1, 10, 12, p.timber);
            for (int step = 0; step <= 3; step++) {
                b.force(Phase.FRAME,
                        x < center ? x + step : x - step,
                        9 + step,
                        5,
                        p.timber.defaultBlockState());
                b.force(Phase.FRAME,
                        x < center ? x + step : x - step,
                        9 + step,
                        10,
                        p.timber.defaultBlockState());
            }
        }
        // Face-connected crown beams tie the winding drum back to all four grounded posts. The
        // stepped diagonal braces are visual reinforcement, but edge contact alone is not a safe
        // load path for the hanging chain.
        beam(b, Phase.FRAME, center - 3, center + 3, 12, 5, p.timber);
        beam(b, Phase.FRAME, center - 3, center + 3, 12, 10, p.timber);
        for (int z = 5; z <= 10; z++) {
            b.force(Phase.FRAME, center, 12, z, p.timber.defaultBlockState());
        }
        for (int y = 8; y <= 11; y++) {
            b.force(Phase.FRAME, center, y, 7, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.DECOR, center, 7, 7, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        // A roof monitor exposes the grounded winding frame as part of the skyline, while four
        // separated engine flues mark the front/rear machinery zones. Their corner distribution
        // keeps the landmark legible from every village approach rather than only from one side.
        addWindingHeadframeMonitor(b, m, p);
        addWindingRoofVentilator(b, p, 4, 2, 12, 3);
        addWindingRoofVentilator(b, p, 14, 2, 12, 3);
        addWindingRoofVentilator(b, p, 4, 13, 12, 2);
        addWindingRoofVentilator(b, p, 14, 13, 12, 2);
        addMineMachineBay(b, p, 0, 4, 11, 16, Direction.EAST);
        addMineMachineBay(b, p, 14, 18, 11, 16, Direction.WEST);
        for (int x = center - 2; x <= center + 2; x++) {
            for (int y = 2; y <= 4; y++) {
                b.force(Phase.SHELL, x, y, 16, Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        fixture(b, m, 3, 1, 14, Blocks.STONECUTTER, new BlockPos(4, 1, 14));
        fixture(b, m, 15, 1, 14, Blocks.BLAST_FURNACE, new BlockPos(14, 1, 14));
        fixture(b, m, 3, 1, 10, Blocks.CHEST, new BlockPos(4, 1, 10));
        // The deepslate machinery wall provides sturdy bearings for the rear transfer lights.
        addRearWallSconce(b, 7, 2, 17);
        addRearWallSconce(b, 11, 2, 17);
        m.enclosed = false;
        addIndustryMineProgram(b, m, p, "winding_house");
    }

    /**
     * Gives every active warehouse master a readable receiving, storage and dispatch sequence.
     * These are open display racks and solid cargo props rather than loot-bearing containers, so
     * the added craft cannot duplicate inventory or change the warehouse economy.
     */
    private static void addIndustryWarehouseProgram(
            Builder b, Metadata m, Materials p, String variant) {
        switch (variant) {
            case "wharf" -> {
                addIndustryTieredRack(b, m, p, 1, 5, 1, 4);
                addIndustryTieredRack(b, m, p, 10, 11, 1, 3);
                addIndustryCargoPallet(b, m, p, 7, 9, Direction.Axis.Z);
                m.interiorSamples.add(new BlockPos(10, 1, 9));
            }
            case "basilica" -> {
                for (int z : new int[] {3, 8, 13}) {
                    addIndustryTieredRack(b, m, p, 1, z, 1, 5);
                    addIndustryTieredRack(b, m, p, 23, z, -1, 5);
                }
                addIndustryWarehouseCatwalk(b, m, p);
                addIndustryCargoPallet(b, m, p, 9, 12, Direction.Axis.X);
                addIndustryCargoPallet(b, m, p, 14, 7, Direction.Axis.Z);
            }
            default -> {
                int rear = Math.max(3, m.depth - 3);
                addIndustryTieredRack(b, m, p, 1, 3, 1, Math.min(4, m.wallHeight - 1));
                addIndustryTieredRack(b, m, p, m.width - 2, rear, -1,
                        Math.min(4, m.wallHeight - 1));
                addIndustryCargoPallet(b, m, p, 2, rear, Direction.Axis.X);
                addIndustryCargoPallet(b, m, p, m.width - 4, 3, Direction.Axis.Z);
            }
        }
        if ("crane".equals(variant)) {
            addIndustryMonumentalWarehouseHoist(b, m, p);
        }
    }

    private static void addIndustryTieredRack(
            Builder b, Metadata m, Materials p, int anchorX, int z, int stepX, int height) {
        int farX = anchorX + stepX * 2;
        List<BlockPos> routeSolids = new ArrayList<>();
        for (int offset = 0; offset <= 2; offset++) {
            routeSolids.add(new BlockPos(anchorX + stepX * offset, 1, z));
            routeSolids.add(new BlockPos(anchorX + stepX * offset, 2, z));
        }
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        for (int x : new int[] {anchorX, farX}) {
            for (int y = 1; y <= height; y++) {
                b.putIfFree(Phase.FRAME, x, y, z, p.fence.defaultBlockState());
            }
        }
        for (int level : new int[] {2, 4}) {
            if (level > height) {
                continue;
            }
            for (int offset = 0; offset <= 2; offset++) {
                b.putIfFree(Phase.FIXTURE, anchorX + stepX * offset, level, z,
                        upperRoofSlab(p));
            }
        }
        b.putIfFree(Phase.FIXTURE, anchorX + stepX, 1, z,
                p.timber.defaultBlockState().setValue(
                        RotatedPillarBlock.AXIS, Direction.Axis.X));
        if (height >= 4) {
            b.putIfFree(Phase.FIXTURE, farX, 3, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
    }

    private static void addIndustryCargoPallet(
            Builder b, Metadata m, Materials p, int x, int z, Direction.Axis axis) {
        List<BlockPos> routeSolids = new ArrayList<>();
        for (int offset = 0; offset < 3; offset++) {
            routeSolids.add(new BlockPos(
                    x + (axis == Direction.Axis.X ? offset : 0),
                    1,
                    z + (axis == Direction.Axis.Z ? offset : 0)));
        }
        routeSolids.add(new BlockPos(x, 2, z));
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        for (int offset = 0; offset < 3; offset++) {
            int dx = axis == Direction.Axis.X ? offset : 0;
            int dz = axis == Direction.Axis.Z ? offset : 0;
            b.putIfFree(Phase.FIXTURE, x + dx, 1, z + dz,
                    p.timber.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
        }
        b.putIfFree(Phase.DECOR, x, 2, z, Blocks.OAK_TRAPDOOR.defaultBlockState());
    }

    private static void addIndustryMonumentalWarehouseHoist(
            Builder b, Metadata m, Materials p) {
        List<BlockPos> routeSolids = new ArrayList<>();
        for (int x : new int[] {5, 15}) {
            for (int z : new int[] {4, 10}) {
                routeSolids.add(new BlockPos(x, 1, z));
                routeSolids.add(new BlockPos(x, 2, z));
            }
        }
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        for (int x : new int[] {5, 15}) {
            for (int z : new int[] {4, 10}) {
                for (int y = 1; y <= 7; y++) {
                    b.putIfFree(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
            for (int z = 4; z <= 10; z++) {
                b.putIfFree(Phase.FRAME, x, 7, z, p.timber.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
        }
        for (int x = 5; x <= 15; x++) {
            for (int z : new int[] {4, 10}) {
                b.putIfFree(Phase.FRAME, x, 7, z, p.timber.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
        }
        for (int z = 4; z <= 10; z++) {
            b.putIfFree(Phase.FRAME, 10, 7, z, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        // A five-block drum, paired gear cheeks and a low hook turn the frame into machinery.
        for (int x = 7; x <= 13; x++) {
            b.putIfFree(Phase.FIXTURE, x, 4, 10, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        for (int x : new int[] {6, 14}) {
            b.putIfFree(Phase.FRAME, x, 4, 10, p.accent.defaultBlockState());
            b.putIfFree(Phase.DECOR, x, 5, 10, Blocks.OAK_TRAPDOOR.defaultBlockState());
            b.putIfFree(Phase.DECOR, x, 3, 10, Blocks.OAK_TRAPDOOR.defaultBlockState());
        }
        for (int y = 4; y <= 6; y++) {
            b.putIfFree(Phase.FRAME, 10, y, 7, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.putIfFree(Phase.FIXTURE, 10, 3, 7, Blocks.GRINDSTONE.defaultBlockState());
        m.interiorSamples.add(new BlockPos(7, 1, 7));
        m.interiorSamples.add(new BlockPos(14, 1, 9));
    }

    private static void addIndustryWarehouseCatwalk(Builder b, Metadata m, Materials p) {
        int ladderX = 10;
        int ladderZ = 15;
        BlockPos hatch = new BlockPos(ladderX, 6, ladderZ);
        List<BlockPos> catwalkSolids = new ArrayList<>();
        for (int z = 2; z <= 14; z++) {
            catwalkSolids.add(new BlockPos(7, 5, z));
            catwalkSolids.add(new BlockPos(17, 5, z));
            catwalkSolids.add(new BlockPos(6, 6, z));
            catwalkSolids.add(new BlockPos(18, 6, z));
        }
        for (int x = 7; x <= 17; x++) {
            catwalkSolids.add(new BlockPos(x, 5, 12));
        }
        // Stop the landing one cell before the ladder shaft. A slab at y=5 in the shaft would
        // win putIfFree ordering and silently remove the final ladder segment.
        for (int z = 13; z < ladderZ; z++) {
            catwalkSolids.add(new BlockPos(ladderX, 5, z));
        }
        catwalkSolids.add(hatch);
        if (!solidPropsPreserveInteractionRoutes(b, m, catwalkSolids)) {
            return;
        }
        // The rear wall is the only honest full-height ladder bearing. Place the climb between
        // window bays, then bridge its upper landing into the transverse catwalk. This avoids the
        // earlier floating ladder and gives the raised inspection route a visible, usable entry.
        if (b.isOccupied(hatch)) {
            return;
        }
        for (int y = 1; y <= 5; y++) {
            BlockPos ladder = new BlockPos(ladderX, y, ladderZ);
            BlockPos backing = ladder.relative(Direction.SOUTH);
            Cell backingCell = b.cellAt(backing);
            if (b.isOccupied(ladder)
                    || backingCell == null
                    || !backingCell.state.isFaceSturdy(
                            EmptyBlockGetter.INSTANCE, backing, Direction.NORTH)) {
                return;
            }
        }
        for (int z = 2; z <= 14; z++) {
            for (int x : new int[] {7, 17}) {
                b.putIfFree(Phase.FOUNDATION, x, 5, z, upperRoofSlab(p));
            }
            b.putIfFree(Phase.FRAME, 6, 6, z, p.fence.defaultBlockState());
            b.putIfFree(Phase.FRAME, 18, 6, z, p.fence.defaultBlockState());
        }
        for (int x = 7; x <= 17; x++) {
            b.putIfFree(Phase.FOUNDATION, x, 5, 12, upperRoofSlab(p));
        }
        for (int z = 13; z < ladderZ; z++) {
            b.putIfFree(Phase.FOUNDATION, ladderX, 5, z, upperRoofSlab(p));
        }
        for (int y = 1; y <= 5; y++) {
            b.putIfFree(Phase.FIXTURE, ladderX, y, ladderZ,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.putIfFree(Phase.FIXTURE, hatch.getX(), hatch.getY(), hatch.getZ(),
                Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(
                new BlockPos(ladderX, 1, ladderZ),
                new BlockPos(ladderX, 5, ladderZ)));
        m.accessTargets.add(new BlockPos(ladderX, 6, ladderZ - 1));
    }

    /** Adds grain handling in a scale-appropriate place for each V2 granary silhouette. */
    private static void addIndustryGranaryProgram(
            Builder b, Metadata m, Materials p, String variant) {
        int floorY = ("raised_barn".equals(variant) || "stilt".equals(variant)) ? 5 : 1;
        int rearZ = Math.max(3, m.depth - 3);
        int farX = Math.max(2, m.width - 3);
        int processingZ = Math.max(2, rearZ - 2);
        List<BlockPos> routeSolids = List.of(
                new BlockPos(2, floorY, rearZ),
                new BlockPos(2, floorY + 1, rearZ),
                new BlockPos(farX, floorY, rearZ),
                new BlockPos(farX, floorY + 1, rearZ),
                new BlockPos(2, floorY, processingZ),
                new BlockPos(Math.max(3, m.width - 4), floorY, processingZ));
        if (solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            for (int x : new int[] {2, farX}) {
                b.putIfFree(Phase.FIXTURE, x, floorY, rearZ,
                        Blocks.HAY_BLOCK.defaultBlockState());
                b.putIfFree(Phase.FIXTURE, x, floorY + 1, rearZ,
                        Blocks.HAY_BLOCK.defaultBlockState().setValue(
                                RotatedPillarBlock.AXIS, Direction.Axis.X));
                b.putIfFree(Phase.DECOR, x, floorY + 2, rearZ,
                        Blocks.OAK_TRAPDOOR.defaultBlockState());
            }
            b.putIfFree(Phase.FIXTURE, 2, floorY, processingZ,
                    Blocks.COMPOSTER.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, Math.max(3, m.width - 4), floorY,
                    processingZ, Blocks.SMOKER.defaultBlockState());
        }
        if ("windmill".equals(variant)) {
            addIndustryWindmillMachinery(b, m, p);
        } else if ("silo_complex".equals(variant)) {
            List<BlockPos> siloSolids = new ArrayList<>();
            for (int x : new int[] {3, 17}) {
                siloSolids.add(new BlockPos(x, 1, 11));
                siloSolids.add(new BlockPos(x, 2, 11));
                siloSolids.add(new BlockPos(x + (x < 10 ? 1 : -1), 3, 11));
            }
            if (!solidPropsPreserveInteractionRoutes(b, m, siloSolids)) {
                return;
            }
            for (int x : new int[] {3, 17}) {
                for (int y = 1; y <= 4; y++) {
                    b.putIfFree(Phase.FRAME, x, y, 11, p.fence.defaultBlockState());
                }
                b.putIfFree(Phase.FIXTURE, x + (x < 10 ? 1 : -1), 3, 11,
                        Blocks.HAY_BLOCK.defaultBlockState());
            }
        }
    }

    private static void addIndustryWindmillMachinery(Builder b, Metadata m, Materials p) {
        // Broaden the existing cardinal sail spars into tapered cloth-and-timber paddles.
        for (int distance = 2; distance <= 5; distance++) {
            for (int side : new int[] {-1, 1}) {
                b.putIfFree(Phase.DECOR, 18, 11 + side * distance, 8,
                        Blocks.OAK_TRAPDOOR.defaultBlockState());
                b.putIfFree(Phase.DECOR, 18, 11 + side * distance, 10,
                        Blocks.OAK_TRAPDOOR.defaultBlockState());
                b.putIfFree(Phase.DECOR, 18, 10, 9 + side * distance,
                        Blocks.OAK_TRAPDOOR.defaultBlockState());
                b.putIfFree(Phase.DECOR, 18, 12, 9 + side * distance,
                        Blocks.OAK_TRAPDOOR.defaultBlockState());
            }
        }
        b.putIfFree(Phase.FRAME, 18, 11, 9, p.accent.defaultBlockState());
        b.putIfFree(Phase.FRAME, 19, 11, 9, p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));

        // Interior crown wheel, axle, paired millstones and chutes make grain flow explicit.
        List<BlockPos> millSolids = new ArrayList<>();
        millSolids.add(new BlockPos(5, 1, 8));
        millSolids.add(new BlockPos(5, 2, 8));
        for (int x : new int[] {3, 7}) {
            millSolids.add(new BlockPos(x, 1, 8));
            millSolids.add(new BlockPos(x, 2, 8));
            millSolids.add(new BlockPos(x, 1, 9));
        }
        if (!solidPropsPreserveInteractionRoutes(b, m, millSolids)) {
            return;
        }
        for (int y = 1; y <= 4; y++) {
            b.putIfFree(Phase.FRAME, 5, y, 8, p.fence.defaultBlockState());
        }
        b.putIfFree(Phase.FRAME, 5, 3, 8, p.accent.defaultBlockState());
        for (int[] offset : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            b.putIfFree(Phase.DECOR, 5, 3 + offset[0], 8 + offset[1],
                    Blocks.OAK_TRAPDOOR.defaultBlockState());
        }
        for (int x : new int[] {3, 7}) {
            b.putIfFree(Phase.FIXTURE, x, 1, 8, p.foundation.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, x, 2, 8, Blocks.GRINDSTONE.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, x, 1, 9, Blocks.COMPOSTER.defaultBlockState());
        }
        m.interiorSamples.add(new BlockPos(5, 1, 7));
        m.interiorSamples.add(new BlockPos(11, 1, 9));
    }

    /** Places a coherent heat-work-finish sequence without adding any cosmetic inventories. */
    private static void addIndustrySmithyProgram(
            Builder b, Metadata m, Materials p, String variant) {
        int workZ = Math.max(4, m.depth - 4);
        List<BlockPos> lineSolids = List.of(
                new BlockPos(1, 1, workZ),
                new BlockPos(2, 1, workZ),
                new BlockPos(Math.max(3, m.width - 4), 1, workZ),
                new BlockPos(Math.max(4, m.width - 3), 1, workZ),
                new BlockPos(1, 2, workZ),
                new BlockPos(Math.max(2, m.width - 2), 2, workZ));
        if (solidPropsPreserveInteractionRoutes(b, m, lineSolids)) {
            b.putIfFree(Phase.FIXTURE, 1, 1, workZ, Blocks.RAW_IRON_BLOCK.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, 2, 1, workZ, Blocks.COAL_BLOCK.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, Math.max(3, m.width - 4), 1, workZ,
                    Blocks.CAULDRON.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, Math.max(4, m.width - 3), 1, workZ,
                    Blocks.GRINDSTONE.defaultBlockState());
            for (int x : new int[] {1, Math.max(2, m.width - 2)}) {
                b.putIfFree(Phase.FRAME, x, 2, workZ, Blocks.IRON_BARS.defaultBlockState());
                // Tie the short tool-chain to a wall-connected timber corbel. A chain perched on
                // the iron-bar post below looked plausible from floor level, but had no direct
                // bearing above and became a detached hanging feature in tall foundry bays.
                BlockPos chainSupport = new BlockPos(x, 4, workZ);
                b.putIfFree(Phase.FRAME, chainSupport.getX(), chainSupport.getY(),
                        chainSupport.getZ(), p.timber.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                Cell support = b.cellAt(chainSupport);
                if (support != null && support.state.isFaceSturdy(
                        EmptyBlockGetter.INSTANCE, chainSupport, Direction.DOWN)) {
                    b.putIfFree(Phase.DECOR, x, 3, workZ,
                            Blocks.IRON_CHAIN.defaultBlockState());
                }
            }
        }
        if ("hammerhall".equals(variant)) {
            addIndustryHammerhallDropHammer(b, m, p);
        } else if ("corner".equals(variant)) {
            addIndustryCornerForgeCourt(b, m, p);
        } else if ("foundry".equals(variant)) {
            List<BlockPos> castingSolids = List.of(
                    new BlockPos(8, 1, 12), new BlockPos(8, 2, 12),
                    new BlockPos(14, 1, 12), new BlockPos(14, 2, 12));
            if (solidPropsPreserveInteractionRoutes(b, m, castingSolids)) {
                for (int x : new int[] {8, 14}) {
                    b.putIfFree(Phase.FIXTURE, x, 1, 12,
                            Blocks.RAW_IRON_BLOCK.defaultBlockState());
                    b.putIfFree(Phase.FIXTURE, x, 2, 12,
                            Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
    }

    private static void addIndustryHammerhallDropHammer(Builder b, Metadata m, Materials p) {
        List<BlockPos> routeSolids = List.of(
                new BlockPos(4, 1, 8), new BlockPos(4, 2, 8),
                new BlockPos(8, 1, 8), new BlockPos(8, 2, 8),
                new BlockPos(6, 1, 8), new BlockPos(6, 2, 8),
                new BlockPos(5, 1, 9), new BlockPos(7, 1, 9));
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        for (int x : new int[] {4, 8}) {
            for (int y = 1; y <= 5; y++) {
                b.putIfFree(Phase.FRAME, x, y, 8, p.timber.defaultBlockState());
            }
        }
        for (int x = 4; x <= 8; x++) {
            b.putIfFree(Phase.FRAME, x, 5, 8, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        for (int y = 3; y <= 4; y++) {
            b.putIfFree(Phase.FRAME, 6, y, 8, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.putIfFree(Phase.FIXTURE, 6, 2, 8, p.accent.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 6, 1, 8, Blocks.ANVIL.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 5, 1, 9, Blocks.RAW_IRON_BLOCK.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 7, 1, 9, Blocks.COAL_BLOCK.defaultBlockState());
        m.interiorSamples.add(new BlockPos(6, 1, 7));
        m.interiorSamples.add(new BlockPos(15, 1, 9));
    }

    private static void addIndustryCornerForgeCourt(Builder b, Metadata m, Materials p) {
        List<BlockPos> routeSolids = List.of(
                new BlockPos(7, 1, 7), new BlockPos(10, 1, 7),
                new BlockPos(8, 1, 8), new BlockPos(10, 1, 9),
                new BlockPos(7, 1, 10));
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        for (int x : new int[] {7, 10}) {
            b.putIfFree(Phase.FRAME, x, 1, 7, p.fence.defaultBlockState());
            b.putIfFree(Phase.FRAME, x, 3, 7, p.fence.defaultBlockState());
        }
        for (int x = 7; x <= 10; x++) {
            b.putIfFree(Phase.FRAME, x, 4, 7, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        b.putIfFree(Phase.FIXTURE, 8, 1, 8, Blocks.ANVIL.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 10, 1, 9, Blocks.CAULDRON.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 7, 1, 10, Blocks.RAW_IRON_BLOCK.defaultBlockState());
        m.interiorSamples.add(new BlockPos(8, 1, 7));
    }

    /** Adds ore receiving, sorting and mechanical haulage cues to every active mine master. */
    private static void addIndustryMineProgram(
            Builder b, Metadata m, Materials p, String variant) {
        int rear = Math.max(4, m.depth - 4);
        List<BlockPos> receivingSolids = List.of(
                new BlockPos(1, 1, rear), new BlockPos(2, 1, rear),
                new BlockPos(Math.max(3, m.width - 3), 1, rear),
                new BlockPos(1, 2, rear), new BlockPos(Math.max(2, m.width - 2), 2, rear));
        if (solidPropsPreserveInteractionRoutes(b, m, receivingSolids)) {
            b.putIfFree(Phase.FIXTURE, 1, 1, rear, Blocks.RAW_IRON_BLOCK.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, 2, 1, rear, Blocks.COAL_BLOCK.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, Math.max(3, m.width - 3), 1, rear,
                    Blocks.STONECUTTER.defaultBlockState());
            for (int x : new int[] {1, Math.max(2, m.width - 2)}) {
                b.putIfFree(Phase.FRAME, x, 2, rear, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        if ("winding_house".equals(variant)) {
            addIndustryWindingDrum(b, m, p);
        } else if ("quarry".equals(variant)) {
            addIndustryQuarryCrusherLine(b, m, p);
        } else if ("drift".equals(variant)) {
            addIndustryOreSortingTable(b, m, p, 11, 6);
            m.interiorSamples.add(new BlockPos(11, 1, 7));
        } else if ("headframe".equals(variant) || "adit".equals(variant)) {
            addIndustryOreSortingTable(b, m, p, 1, Math.max(5, rear - 1));
        }
    }

    private static void addIndustryWindingDrum(Builder b, Metadata m, Materials p) {
        List<BlockPos> routeSolids = new ArrayList<>();
        for (int supportX : new int[] {5, 13}) {
            for (int supportZ : new int[] {6, 9}) {
                routeSolids.add(new BlockPos(supportX, 1, supportZ));
                routeSolids.add(new BlockPos(supportX, 2, supportZ));
            }
        }
        routeSolids.add(new BlockPos(7, 1, 9));
        routeSolids.add(new BlockPos(11, 1, 9));
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        // The broad horizontal drum spans between two geared cheeks above the unobstructed rail.
        for (int x = 7; x <= 11; x++) {
            b.putIfFree(Phase.FIXTURE, x, 5, 8, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        for (int x : new int[] {6, 12}) {
            b.putIfFree(Phase.FRAME, x, 5, 8, p.accent.defaultBlockState());
            for (int[] offset : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                b.putIfFree(Phase.DECOR, x, 5 + offset[0], 8 + offset[1],
                        Blocks.OAK_TRAPDOOR.defaultBlockState());
            }
        }
        for (int supportX : new int[] {5, 13}) {
            for (int supportZ : new int[] {6, 9}) {
                for (int y = 1; y <= 6; y++) {
                    b.putIfFree(Phase.FRAME, supportX, y, supportZ,
                            p.timber.defaultBlockState());
                }
            }
        }
        for (int z = 6; z <= 9; z++) {
            for (int x : new int[] {5, 13}) {
                b.putIfFree(Phase.FRAME, x, 6, z, p.timber.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
        }
        // Suspend the drive chain from a genuine crosshead carried by both gantry cheeks. The
        // former lone chain sat on top of the drum with open air above, which read as floating and
        // correctly failed the exact hanging-feature support gate.
        for (int x = 5; x <= 13; x++) {
            b.putIfFree(Phase.FRAME, x, 7, 8, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        BlockPos driveChainSupport = new BlockPos(9, 7, 8);
        Cell driveSupport = b.cellAt(driveChainSupport);
        if (driveSupport != null && driveSupport.state.isFaceSturdy(
                EmptyBlockGetter.INSTANCE, driveChainSupport, Direction.DOWN)) {
            b.putIfFree(Phase.FRAME, 9, 6, 8, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.putIfFree(Phase.FIXTURE, 7, 1, 9, Blocks.GRINDSTONE.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 11, 1, 9, Blocks.STONECUTTER.defaultBlockState());
        m.interiorSamples.add(new BlockPos(6, 1, 8));
        m.interiorSamples.add(new BlockPos(12, 1, 8));
    }

    private static void addIndustryOreSortingTable(
            Builder b, Metadata m, Materials p, int x, int z) {
        List<BlockPos> routeSolids = new ArrayList<>();
        for (int dx = 0; dx <= 2; dx++) {
            routeSolids.add(new BlockPos(x + dx, 1, z));
            routeSolids.add(new BlockPos(x + dx, 2, z));
        }
        routeSolids.add(new BlockPos(x, 3, z));
        routeSolids.add(new BlockPos(x + 2, 3, z));
        if (!solidPropsPreserveInteractionRoutes(b, m, routeSolids)) {
            return;
        }
        for (int dx = 0; dx <= 2; dx++) {
            b.putIfFree(Phase.FRAME, x + dx, 1, z, p.fence.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, x + dx, 2, z, upperRoofSlab(p));
        }
        b.putIfFree(Phase.FIXTURE, x, 3, z, Blocks.RAW_IRON_BLOCK.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, x + 2, 3, z, Blocks.COAL_BLOCK.defaultBlockState());
    }

    private static void addIndustryQuarryCrusherLine(Builder b, Metadata m, Materials p) {
        List<BlockPos> crusherSolids = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            crusherSolids.add(new BlockPos(x, 2, 16));
        }
        for (int x : new int[] {1, 5}) {
            crusherSolids.add(new BlockPos(x, 1, 15));
            crusherSolids.add(new BlockPos(x, 2, 15));
        }
        if (!solidPropsPreserveInteractionRoutes(b, m, crusherSolids)) {
            return;
        }
        for (int x = 1; x <= 5; x++) {
            b.putIfFree(Phase.FIXTURE, x, 2, 16, p.foundation.defaultBlockState());
        }
        for (int x : new int[] {1, 5}) {
            for (int y = 1; y <= 5; y++) {
                b.putIfFree(Phase.FRAME, x, y, 15, p.timber.defaultBlockState());
            }
        }
        for (int x = 1; x <= 5; x++) {
            b.putIfFree(Phase.FRAME, x, 5, 15, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        b.putIfFree(Phase.FRAME, 3, 4, 15, Blocks.IRON_CHAIN.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, 3, 3, 15, Blocks.GRINDSTONE.defaultBlockState());
        addIndustryOreSortingTable(b, m, p, 17, 14);
        m.interiorSamples.add(new BlockPos(4, 1, 15));
        m.interiorSamples.add(new BlockPos(18, 1, 14));
    }

    private static void marketGuildcourt(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MARKET_SQUARE, 21, 19, 14, false);
        addMarketFloor(b, p, 21, 19);
        addMarketStall(b, m, p, 4, 4, Direction.SOUTH,
                Blocks.FLETCHING_TABLE);
        addMarketStall(b, m, p, 16, 4, Direction.SOUTH,
                Blocks.LOOM);
        addMarketStall(b, m, p, 4, 14, Direction.NORTH,
                Blocks.STONECUTTER);
        addMarketStall(b, m, p, 16, 14, Direction.NORTH,
                Blocks.GRINDSTONE);
        addGuildArcade(b, p, 21, 19, 10);
        addOpenPavilion(b, p, 10, 9, 4, 8);
        b.force(Phase.FIXTURE, 10, 7, 9, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        m.accessTargets.add(new BlockPos(10, 1, 9));
        for (int x = 9; x <= 11; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.accent.defaultBlockState());
            b.force(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        reserveCrossAisle(m, 10, 1, 17, 3);
        m.entranceInside = new BlockPos(10, 1, 1);
        m.wallHeight = 5;
        m.roofPeak = 9;
        addMarketInteriorProgram(b, m, p, "guildcourt");
    }

    private static void guardBastion(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GUARD_POST, 15, 15, 20, true);
        addGabledShell(b, m, p, 6, Direction.Axis.Z, Porch.FORMAL);
        addBastionTower(b, m, p, 0, 4, 0, 5, 12);
        addBastionTower(b, m, p, 10, 14, 0, 5, 12);
        // The barracks hall is broad enough that wall sconces leave a dark center. This grounded
        // cross-beam gives the fixture composer an intentional, symmetrical pendant bearing.
        addInteriorLightingBeamX(b, p, 1, 13, 5, 7);
        for (int x = 4; x <= 10; x++) {
            b.force(Phase.FRAME, x, 8, 0, p.foundation.defaultBlockState());
            if (x % 2 == 0) {
                b.force(Phase.DECOR, x, 7, -1, p.accent.defaultBlockState());
            }
        }
        // Tie the two isolated front-eave cells back into the supported bridge above. These
        // short inner brackets preserve the open gate while giving each roof cell a face-contact
        // load path through the tower-to-tower beam.
        for (int x : new int[] {7, 9}) {
            b.force(Phase.FRAME, x, 7, 0, p.timber.defaultBlockState());
        }
        addGuardButtress(b, p, -1, 5, 11);
        addGuardButtress(b, p, 15, 5, 11);
        fixture(b, m, 2, 1, 12, Blocks.FLETCHING_TABLE, new BlockPos(3, 1, 12));
        fixture(b, m, 12, 1, 12, Blocks.GRINDSTONE, new BlockPos(11, 1, 12));
        fixture(b, m, 2, 1, 8, Blocks.CHEST, new BlockPos(3, 1, 8));
        fixture(b, m, 12, 1, 8, Blocks.CHEST, new BlockPos(11, 1, 8));
        addLongTable(b, p, 4, 10, 3);
        addRearWallSconce(b, 6, 2, 15);
        addRearWallSconce(b, 8, 2, 15);
        m.reserveCentralAisle(7, 1, 12);
        m.interiorSamples.add(new BlockPos(7, 1, 7));
        addGuardInteriorProgram(b, m, p, "bastion");
    }

    private static void exchangeCountinghouse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.EXCHANGE_HALL, 23, 17, 19, true);
        int center = 11;
        // A cruciform civic plan replaces the scaled rectangular hall: a narrow counting nave
        // intersects a broad transept below the tower, leaving four articulated re-entrant
        // corners. The foundation follows that cross, so its silhouette remains distinct even
        // when palette, rotation and mirror are deliberately ignored by catalog admission.
        Footprint footprint = (x, z) -> (x >= 8 && x <= 14) || (z >= 5 && z <= 11);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {9, 13}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 16, 2, 4);
        }
        for (int z : new int[] {7, 9}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 22, z, 2, 4);
        }
        windowColumn(windows, 8, 3, 2, 4);
        windowColumn(windows, 14, 3, 2, 4);
        windowColumn(windows, 5, 5, 2, 4);
        windowColumn(windows, 17, 5, 2, 4);
        windowColumn(windows, 5, 11, 2, 4);
        windowColumn(windows, 17, 11, 2, 4);
        addFootprintShell(b, m, p, footprint, 7, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 7);
        addRectGableRoof(b, m, p, 8, 14, 0, 16, 7, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 22, 5, 11, 7, Direction.Axis.Z);
        // Full crossing bearings close all four valleys under the central tower plinth.
        for (int x = 9; x <= 13; x++) {
            for (int z = 6; z <= 10; z++) {
                b.force(Phase.ROOF, x, 12, z, doubleRoofSlab(p));
            }
        }
        addLayeredEntryPorch(b, p, center, 4, 6);
        addFrontDormer(b, m, p, center, 13);
        // The countinghouse dormer rises from the tall crossing rather than a conventional
        // low eave. Give its first upright a full ridge bearing so no stair/slab ever supports
        // a floating full block in stricter structure validation.
        b.force(Phase.ROOF, center, 12, 0, doubleRoofSlab(p));
        addBayWindow(b, p, 0, 8, Direction.WEST);
        addBayWindow(b, p, 22, 8, Direction.EAST);
        addRearServiceCanopy(b, p, 9, 13, 16, 6);
        addCountinghouseTower(b, m, p);
        fixture(b, m, center, 1, 15, BankerProfessionSupport.exchangeDeskOrLectern(),
                new BlockPos(center, 1, 14));
        for (int x : new int[] {9, 13}) {
            fixture(b, m, x, 1, 15, Blocks.BOOKSHELF,
                    new BlockPos(x == 9 ? 10 : 12, 1, 15));
        }
        fixture(b, m, 2, 1, 9, Blocks.ENDER_CHEST, new BlockPos(3, 1, 9));
        fixture(b, m, 20, 1, 9, Blocks.CHEST, new BlockPos(19, 1, 9));
        addCountinghouseTellerRail(b, p, 9, 13, 13, center);
        addLongTable(b, p, 5, 8, 5);
        addLongTable(b, p, 17, 8, 5);
        m.reserveCentralAisle(center, 1, 14);
        m.interiorSamples.add(new BlockPos(4, 1, 8));
        m.interiorSamples.add(new BlockPos(18, 1, 8));
        addExchangeInteriorProgram(b, m, p, "countinghouse");
    }

    /*
     * Wave-three masters. Each plan below owns a new footprint family and skyline rather than
     * decorating an earlier shell. The smaller member of each role remains practical in young
     * villages; the second is an intentionally ambitious civic/industrial landmark.
     */

    private static void cottageLonghouse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 15, 9, 11, true);
        // An inhabited long room shares a lower saltbox with a recessed eastern byre.
        Footprint footprint = (x, z) -> x <= 10 || z >= 3;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 5, 9}) {
            windowColumn(windows, x, 0, 2, 3);
            windowColumn(windows, x, 8, 2, 3);
        }
        windowColumn(windows, 12, 8, 2, 3);
        for (int z : new int[] {3, 6}) {
            windowColumn(windows, 0, z, 2, 3);
        }
        windowColumn(windows, 14, 5, 2, 3);
        windowColumn(windows, 14, 7, 2, 3);
        addFootprintShell(b, m, p, footprint, 4, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addSaltboxRoof(b, m, p, 0, 10, 0, 8, 4);
        // Continue the east pitch as a supported lean-to instead of laying a north-facing shed
        // through the saltbox. The old crossing produced two unrelated roof planes at the join.
        // A full timber ledger now grows out of the longhouse roof and carries paired descending
        // courses over the recessed byre, with closed end cheeks at both exposed gables.
        for (int z = 3; z <= 8; z++) {
            b.force(Phase.FRAME, 11, 5, z, p.timber.defaultBlockState());
            b.force(Phase.FRAME, 11, 6, z, p.timber.defaultBlockState());
        }
        for (int z : new int[] {3, 8}) {
            post(b, Phase.FRAME, 11, 1, z, 6, p.timber);
            for (int x = 12; x <= 13; x++) {
                int roofY = 6;
                for (int y = 5; y < roofY; y++) {
                    b.force(Phase.SHELL, x, y, z, p.wall.defaultBlockState());
                }
            }
        }
        for (int z = 2; z <= 9; z++) {
            for (int x = 10; x <= 15; x++) {
                int roofY = x <= 11 ? 7 : x <= 13 ? 6 : 5;
                b.force(Phase.ROOF, x, roofY, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        for (int z : new int[] {4, 7}) {
            b.force(Phase.FRAME, 14, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        addLayeredEntryPorch(b, p, 7, 2, 4);
        addDeepWindowFrame(b, p, 2, 0, 2, 3, Direction.NORTH);
        addBayWindow(b, p, 14, 6, Direction.EAST);
        addCoveredWoodBay(b, p, -1, 5, 8);
        addIntegratedChimney(b, m, p, 1, 7);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 9, 3, Direction.WEST, 7, 3);
        fixture(b, m, 12, 1, 7, Blocks.CHEST, new BlockPos(11, 1, 7));
        fixture(b, m, 13, 1, 5, Blocks.CRAFTING_TABLE, new BlockPos(12, 1, 5));
        fixture(b, m, 2, 1, 7, Blocks.SMOKER, new BlockPos(3, 1, 7));
        addLongTable(b, p, 5, 6, 3);
        m.reserveCentralAisle(7, 1, 7);
        m.interiorSamples.add(new BlockPos(12, 1, 6));
        addCottageInteriorProgram(b, m, p, "longhouse");
    }

    private static void cottageOrchardstead(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 19, 15, 14, true);
        // Three domestic ranges wrap an open orchard court; the rear remains deliberately open
        // rather than reading as another large rectangular house.
        Footprint footprint = (x, z) -> z <= 6 || x <= 5 || x >= 13;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {3, 15}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 14, 2, 4);
        }
        for (int z : new int[] {4, 9, 12}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 18, z, 2, 4);
        }
        for (int z : new int[] {9, 12}) {
            windowColumn(windows, 5, z, 2, 4);
            windowColumn(windows, 13, z, 2, 4);
        }
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 3);
        addFootprintTimberBand(b, m, p, footprint, 5);
        addRectGableRoof(b, m, p, 0, 18, 0, 6, 5, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 5, 5, 14, 5, Direction.Axis.X);
        addRectGableRoof(b, m, p, 13, 18, 5, 14, 5, Direction.Axis.X);
        // The two rear valley intersections are full-depth bearings beneath crossing stairs.
        for (int x : new int[] {2, 3, 15, 16}) {
            for (int z = 5; z <= 7; z++) {
                b.force(Phase.ROOF, x, 8, z, doubleRoofSlab(p));
            }
        }
        addLayeredEntryPorch(b, p, 9, 3, 5);
        addDeepWindowFrame(b, p, 3, 0, 2, 4, Direction.NORTH);
        addDeepWindowFrame(b, p, 15, 0, 2, 4, Direction.NORTH);
        addBayWindow(b, p, 0, 10, Direction.WEST);
        addBayWindow(b, p, 18, 10, Direction.EAST);
        addGardenPergola(b, p, 7, 11, 9, 13);
        addIntegratedChimney(b, m, p, 2, 12);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 17, 3, Direction.WEST, 15, 3);
        addBed(b, m, 1, 10, Direction.EAST, 3, 10);
        addBed(b, m, 17, 10, Direction.WEST, 15, 10);
        fixture(b, m, 4, 1, 13, Blocks.SMOKER, new BlockPos(4, 1, 12));
        fixture(b, m, 14, 1, 13, Blocks.CRAFTING_TABLE, new BlockPos(14, 1, 12));
        fixture(b, m, 3, 1, 6, Blocks.CHEST, new BlockPos(4, 1, 6));
        addLongTable(b, p, 5, 4, 3);
        m.reserveCentralAisle(9, 1, 5);
        m.interiorSamples.add(new BlockPos(3, 1, 9));
        m.interiorSamples.add(new BlockPos(15, 1, 9));
        addCottageInteriorProgram(b, m, p, "orchardstead");
    }

    private static void houseSplitwing(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 15, 13, 14, true);
        // Two offset rectangles overlap at a compact stair-hall, producing an S-plan whose
        // front and rear rooms never share a single stretched roof.
        Footprint footprint = (x, z) -> (x <= 9 && z <= 7) || (x >= 5 && z >= 5);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 7}) {
            windowColumn(windows, x, 0, 2, 4);
        }
        for (int x : new int[] {7, 12}) {
            windowColumn(windows, x, 12, 2, 4);
        }
        for (int z : new int[] {3, 6}) {
            windowColumn(windows, 0, z, 2, 4);
        }
        for (int z : new int[] {7, 10}) {
            windowColumn(windows, 14, z, 2, 4);
        }
        windowColumn(windows, 5, 10, 2, 4);
        windowColumn(windows, 9, 2, 2, 4);
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 3);
        addFootprintTimberBand(b, m, p, footprint, 5);
        addRectGableRoof(b, m, p, 0, 9, 0, 7, 5, Direction.Axis.X);
        addRectGableRoof(b, m, p, 5, 14, 5, 12, 5, Direction.Axis.Z);
        // Resolve the two gables as a real stepped valley. Each crossing cell retains only the
        // higher exposed plane; matching courses become a narrow full-depth valley gutter. A
        // top-half slab here would hover half a block above the supporting course below. This
        // removes the former flat five-by-three cap without leaving buried roofs in the attic.
        for (int x = 4; x <= 10; x++) {
            int frontRoofY = x <= 5 ? 11 : 16 - x;
            for (int z = 4; z <= 8; z++) {
                int rearRoofY = z + 2;
                if (frontRoofY < rearRoofY) {
                    b.remove(x, frontRoofY, z);
                } else if (rearRoofY < frontRoofY) {
                    b.remove(x, rearRoofY, z);
                } else {
                    b.force(Phase.ROOF, x, frontRoofY, z, doubleRoofSlab(p));
                }
            }
        }
        // Close the two exposed elbows where the stepped valley meets the S-plan perimeter.
        // These are not buried crossing courses: x4/z7 is the front wing's south gable frame,
        // and x5/z8 is the rear wing's west ridge termination. Removing either opens the shared
        // attic to the exterior and consequently leaks both otherwise enclosed rooms.
        b.force(Phase.FRAME, 4, 9, 7, p.timber.defaultBlockState());
        b.force(Phase.ROOF, 5, 10, 8, doubleRoofSlab(p));
        addLayeredEntryPorch(b, p, 7, 3, 5);
        addDeepWindowFrame(b, p, 2, 0, 2, 4, Direction.NORTH);
        addBayWindow(b, p, 0, 5, Direction.WEST);
        addBayWindow(b, p, 14, 9, Direction.EAST);
        addRearServiceCanopy(b, p, 7, 12, 12, 5);
        addIntegratedChimney(b, m, p, 13, 11);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        // Turn the east-room bed along the room depth; its former west-facing head occupied
        // x=7/z=3, which is the public stair-hall reserved below.
        addBed(b, m, 8, 2, Direction.SOUTH, 7, 2);
        // Match the front room's rotation in the rear-left bedroom. Keeping both bed blocks at
        // x=6 leaves the complete x=7 stair-hall clear while retaining hall-side access.
        addBed(b, m, 6, 9, Direction.SOUTH, 7, 9);
        addBed(b, m, 13, 9, Direction.WEST, 11, 9);
        fixture(b, m, 2, 1, 6, Blocks.LOOM, new BlockPos(3, 1, 6));
        fixture(b, m, 12, 1, 11, Blocks.CRAFTING_TABLE, new BlockPos(11, 1, 11));
        fixture(b, m, 10, 1, 7, Blocks.CHEST, new BlockPos(9, 1, 7));
        // Keep both the public stair-hall and the loom's east-side access clear. The table sits
        // one bay forward in the west room instead of sharing the loom's fixture row.
        addLongTable(b, p, 3, 4, 3);
        m.reserveCentralAisle(7, 1, 11);
        m.interiorSamples.add(new BlockPos(3, 1, 5));
        m.interiorSamples.add(new BlockPos(11, 1, 9));
        addHouseInteriorProgram(b, m, p, "splitwing");
    }

    private static void houseTowercourt(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 21, 19, 20, true);
        // Four ranges surround a true open court. A tall rear corner tower shifts the skyline
        // decisively off-axis and gives the manor a readable public and private side.
        Footprint footprint = (x, z) -> z <= 6 || z >= 14 || x <= 6 || x >= 14;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {3, 8, 12, 17}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 18, 2, 4);
        }
        for (int z : new int[] {4, 9, 15}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 20, z, 2, 4);
        }
        for (int x : new int[] {8, 12}) {
            windowColumn(windows, x, 6, 2, 4);
            windowColumn(windows, x, 14, 2, 4);
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        addRectGableRoof(b, m, p, 0, 20, 0, 6, 6, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 6, 5, 18, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 14, 20, 5, 18, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 20, 14, 18, 6, Direction.Axis.Z);
        for (int x : new int[] {2, 3, 17, 18}) {
            for (int z : new int[] {5, 6, 14, 15}) {
                b.force(Phase.ROOF, x, 10, z, doubleRoofSlab(p));
            }
        }
        // The lower rear ridge crosses beneath both taller side-wing ridges. Full-depth bearing
        // cells at those two intersections close the half-block seam under the upper ridge slabs.
        for (int x : new int[] {3, 17}) {
            b.force(Phase.ROOF, x, 10, 16, doubleRoofSlab(p));
        }
        // Crop the crossing roofs before the north-west tower rises through them. A complete
        // transfer deck and backed ladder make the upper room real circulation, not scenery.
        clearVolume(b, 0, 6, 7, 12, 12, 18);
        for (int x = 0; x <= 6; x++) {
            for (int z = 12; z <= 18; z++) {
                b.force(Phase.FOUNDATION, x, 7, z, p.floor.defaultBlockState());
            }
        }
        for (int y = 8; y <= 12; y++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 12; z <= 18; z++) {
                    if (edgeInRect(x, z, 0, 6, 12, 18)) {
                        b.force(y == 9 && ((x == 3 && (z == 12 || z == 18))
                                        || (z == 15 && (x == 0 || x == 6)))
                                        ? Phase.OPENING : Phase.SHELL,
                                x, y, z,
                                y == 9 && ((x == 3 && (z == 12 || z == 18))
                                        || (z == 15 && (x == 0 || x == 6)))
                                        ? Blocks.GLASS_PANE.defaultBlockState()
                                        : (cornerInRect(x, z, 0, 6, 12, 18)
                                                ? p.timber : p.wall).defaultBlockState());
                    }
                }
            }
        }
        addBackedLadderAndHatch(b, m, p, 2, 17, 18, 1, 6, 7,
                new BlockPos(3, 8, 17));
        addPyramidRoof(b, m, p, 0, 6, 12, 18, 12);
        addLayeredEntryPorch(b, p, 10, 4, 6);
        addDeepWindowFrame(b, p, 8, 0, 2, 4, Direction.NORTH);
        addDeepWindowFrame(b, p, 12, 0, 2, 4, Direction.NORTH);
        addBayWindow(b, p, 20, 9, Direction.EAST);
        addGardenPergola(b, p, 8, 12, 8, 12);
        // Two real doors make the landscaped court part of the circulation, not a sealed void.
        addDoorAt(b, p, 10, 6, Direction.NORTH);
        addDoorAt(b, p, 10, 14, Direction.SOUTH);
        for (int z = 7; z <= 13; z++) {
            for (int x = 9; x <= 11; x++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.accent.defaultBlockState());
            }
        }
        addIntegratedChimney(b, m, p, 18, 16);
        for (int[] bed : new int[][] {{1, 3, 3, 3}, {19, 3, 17, 3},
                {1, 10, 3, 10}, {19, 10, 17, 10}, {9, 16, 11, 16}}) {
            Direction facing = bed[0] < 10 ? Direction.EAST : Direction.WEST;
            addBed(b, m, bed[0], bed[1], facing, bed[2], bed[3]);
        }
        fixture(b, m, 4, 1, 17, Blocks.BOOKSHELF, new BlockPos(5, 1, 17));
        fixture(b, m, 16, 1, 17, Blocks.LOOM, new BlockPos(15, 1, 17));
        fixture(b, m, 3, 1, 5, Blocks.CHEST, new BlockPos(4, 1, 5));
        fixture(b, m, 17, 1, 5, Blocks.CRAFTING_TABLE, new BlockPos(16, 1, 5));
        addLongTable(b, p, 5, 4, 3);
        m.reserveCentralAisle(10, 1, 5);
        m.interiorSamples.add(new BlockPos(3, 1, 9));
        m.interiorSamples.add(new BlockPos(17, 1, 9));
        m.interiorSamples.add(new BlockPos(10, 1, 16));
        addHouseInteriorProgram(b, m, p, "towercourt");
    }

    private static void innTavern(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.INN, 15, 13, 13, true);
        // A narrow public taproom opens into a broad transverse lodging range: a true T-plan
        // with two independently roofed social zones.
        Footprint footprint = (x, z) -> z >= 6 || (x >= 3 && x <= 11);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {4, 10}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 12, 2, 4);
        }
        windowColumn(windows, 2, 12, 2, 4);
        windowColumn(windows, 12, 12, 2, 4);
        for (int z : new int[] {3, 8, 10}) {
            if (z <= 5) {
                windowColumn(windows, 3, z, 2, 4);
                windowColumn(windows, 11, z, 2, 4);
            } else {
                windowColumn(windows, 0, z, 2, 4);
                windowColumn(windows, 14, z, 2, 4);
            }
        }
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 3);
        addFootprintTimberBand(b, m, p, footprint, 5);
        addRectGableRoof(b, m, p, 3, 11, 0, 12, 5, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 14, 6, 12, 5, Direction.Axis.Z);
        // Cut the transverse lodging roof into the taller taproom gable. Keeping only the upper
        // envelope creates two readable valleys; full-depth gutter blocks finish the equal-height
        // diagonals without either the hovering half-gap or rectangular cap of the first draft.
        for (int x = 2; x <= 12; x++) {
            int taproomRoofY = 11 - Math.abs(7 - x);
            for (int z = 5; z <= 13; z++) {
                int lodgingRoofY = 10 - Math.abs(9 - z);
                if (taproomRoofY < lodgingRoofY) {
                    b.remove(x, taproomRoofY, z);
                } else if (lodgingRoofY < taproomRoofY) {
                    b.remove(x, lodgingRoofY, z);
                } else {
                    b.force(Phase.ROOF, x, taproomRoofY, z, doubleRoofSlab(p));
                }
            }
        }
        // The lodging roof crosses the taproom's rear gable at z=12. Valley pruning must not
        // erase that exterior envelope: restore its clipped y=7 infill between the two retained
        // valley shoulders, with the central timber mullion continuing the taproom frame.
        for (int x = 4; x <= 10; x++) {
            boolean mullion = x == 7;
            b.force(mullion ? Phase.FRAME : Phase.SHELL, x, 7, 12,
                    (mullion ? p.timber : p.wall).defaultBlockState());
        }
        addLayeredEntryPorch(b, p, 7, 3, 5);
        addDeepWindowFrame(b, p, 4, 0, 2, 4, Direction.NORTH);
        addDeepWindowFrame(b, p, 10, 0, 2, 4, Direction.NORTH);
        addBayWindow(b, p, 0, 9, Direction.WEST);
        addRearServiceCanopy(b, p, 4, 10, 12, 5);
        addIntegratedChimney(b, m, p, 13, 10);
        addBed(b, m, 4, 3, Direction.EAST, 6, 3);
        addBed(b, m, 10, 3, Direction.WEST, 8, 3);
        addBed(b, m, 1, 9, Direction.EAST, 3, 9);
        addBed(b, m, 13, 9, Direction.WEST, 11, 9);
        addBed(b, m, 1, 11, Direction.EAST, 3, 11);
        addBed(b, m, 13, 11, Direction.WEST, 11, 11);
        fixture(b, m, 4, 1, 11, Blocks.SMOKER, new BlockPos(5, 1, 11));
        fixture(b, m, 10, 1, 11, Blocks.BREWING_STAND, new BlockPos(9, 1, 11));
        fixture(b, m, 12, 1, 7, Blocks.CHEST, new BlockPos(11, 1, 7));
        fixture(b, m, 2, 1, 7, Blocks.CAKE, new BlockPos(3, 1, 7));
        // Keep the cake counter and its customer tile readable at x2..3 while preserving the
        // tavern's three-wide processional aisle. The dining table occupies the intervening bay.
        addLongTable(b, p, 5, 7, 3);
        m.reserveCentralAisle(7, 1, 11);
        m.interiorSamples.add(new BlockPos(3, 1, 9));
        m.interiorSamples.add(new BlockPos(11, 1, 9));
        addInnInteriorProgram(b, m, p, "tavern");
    }

    private static void innCourtyard(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.INN, 25, 21, 19, true);
        // Four offset ranges turn clockwise around a sheltered coach court. Their unequal eaves
        // and rear lantern tower make this read as an evolved inn complex, not a scaled U-shell.
        Footprint footprint = (x, z) -> (x >= 7 && x <= 17 && z <= 8)
                || (x <= 8 && z >= 5)
                || (z >= 14 && x >= 5)
                || (x >= 17 && z >= 9);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {8, 11, 13, 16}) {
            windowColumn(windows, x, 0, 2, 4);
        }
        for (int x : new int[] {2, 7, 12, 18, 22}) {
            windowColumn(windows, x, 20, 2, 4);
        }
        for (int z : new int[] {7, 11, 17}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 24, z, 2, 4);
        }
        windowColumn(windows, 8, 11, 2, 4);
        windowColumn(windows, 17, 11, 2, 4);
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        addRectGableRoof(b, m, p, 7, 17, 0, 8, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 8, 5, 20, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 5, 24, 14, 20, 6, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 17, 24, 9, 20, 6, Direction.Axis.X);
        // Finish only the three real range intersections.  The earlier Cartesian product also
        // drew a false north-east ridge branch at x17..18/21,z6..7 even though the east range
        // does not begin until z9; those six caps either floated or read as an unrelated stub.
        for (int x : new int[] {7, 8}) {
            for (int z : new int[] {6, 7, 14, 15}) {
                b.force(Phase.ROOF, x, 11, z, doubleRoofSlab(p));
            }
        }
        for (int x : new int[] {17, 18, 21}) {
            for (int z : new int[] {14, 15}) {
                b.force(Phase.ROOF, x, 11, z, doubleRoofSlab(p));
            }
        }
        // The west range's y12 ridge crosses one block above the rear range's y11 ridge at this
        // single coordinate.  A full-depth lower course gives the upper ridge continuous bearing
        // instead of stacking two bottom slabs with an exposed half-block gap between them.
        b.force(Phase.ROOF, 4, 11, 17, doubleRoofSlab(p));
        addLayeredEntryPorch(b, p, 12, 4, 6);
        addCoachhouseBalcony(b, p, 7, 11, 5);
        addCoachhouseBalcony(b, p, 13, 17, 5);
        addBayWindow(b, p, 0, 11, Direction.WEST);
        addBayWindow(b, p, 24, 11, Direction.EAST);
        addGardenPergola(b, p, 10, 15, 9, 12);
        addIntegratedChimney(b, m, p, 2, 18);

        // Crop the banquet roof below the lantern, then give it a complete transfer deck and a
        // backed ladder so the upper room is sealed, supported and usable.
        clearVolume(b, 10, 14, 7, 12, 15, 19);
        for (int x = 10; x <= 14; x++) {
            for (int z = 15; z <= 19; z++) {
                b.force(Phase.FOUNDATION, x, 9, z, p.floor.defaultBlockState());
            }
        }
        for (int x : new int[] {10, 14}) {
            for (int z : new int[] {15, 19}) {
                post(b, Phase.FRAME, x, 1, z, 12, p.timber);
            }
        }
        for (int y = 10; y <= 12; y++) {
            for (int x = 10; x <= 14; x++) {
                for (int z = 15; z <= 19; z++) {
                    if (edgeInRect(x, z, 10, 14, 15, 19)) {
                        boolean glazed = y == 11 && !cornerInRect(x, z, 10, 14, 15, 19);
                        b.force(glazed ? Phase.OPENING : Phase.SHELL, x, y, z,
                                glazed ? Blocks.GLASS_PANE.defaultBlockState()
                                        : p.wall.defaultBlockState());
                    }
                }
            }
        }
        for (int y = 1; y <= 8; y++) {
            b.force(Phase.FRAME, 13, y, 19, p.timber.defaultBlockState());
        }
        addBackedLadderAndHatch(b, m, p, 13, 18, 19, 1, 8, 9,
                new BlockPos(12, 10, 18));
        addPyramidRoof(b, m, p, 10, 14, 15, 19, 12);
        // The coach court is entered through a proper three-wide arch rather than a domestic
        // single door, with a durable paved lane joining the rear banquet range.
        for (int x = 11; x <= 13; x++) {
            for (int y = 1; y <= 3; y++) {
                b.remove(x, y, 8);
            }
            for (int z = 8; z <= 14; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.accent.defaultBlockState());
            }
        }
        for (int z : new int[] {3, 7, 17}) {
            addBed(b, m, 8, z, Direction.EAST, 10, z);
        }
        for (int z : new int[] {11, 17}) {
            addBed(b, m, 1, z, Direction.EAST, 3, z);
            addBed(b, m, 23, z, Direction.WEST, 21, z);
        }
        fixture(b, m, 6, 1, 19, Blocks.SMOKER, new BlockPos(7, 1, 19));
        fixture(b, m, 9, 1, 19, Blocks.BREWING_STAND, new BlockPos(9, 1, 18));
        fixture(b, m, 16, 1, 19, Blocks.CAKE, new BlockPos(16, 1, 18));
        fixture(b, m, 20, 1, 19, Blocks.CHEST, new BlockPos(19, 1, 19));
        addLongTable(b, p, 12, 16, 7);
        m.reserveCentralAisle(12, 1, 13);
        m.interiorSamples.add(new BlockPos(4, 1, 11));
        m.interiorSamples.add(new BlockPos(20, 1, 13));
        m.interiorSamples.add(new BlockPos(12, 1, 17));
        addInnInteriorProgram(b, m, p, "courtyard");
        m.enclosed = false;
    }

    private static void warehouseWharf(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.WAREHOUSE, 15, 13, 11, false);
        // A narrow landing lane turns into a broad rear wharf. The store shed occupies only the
        // west bank while an exposed crane and roofed sorting apron balance it to the east.
        Footprint deck = (x, z) -> (x >= 5 && x <= 9) || z >= 7 || (x <= 4 && z >= 2);
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (deck.contains(x, z)) {
                    b.put(Phase.FOUNDATION, x, 0, z,
                            ((x + z) % 4 == 0 ? p.accent : p.foundation).defaultBlockState());
                }
            }
        }
        for (int x = 6; x <= 8; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        // Enclosed-on-three-sides west shed with a deep open loading face.
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x <= 5; x++) {
                for (int z = 2; z <= 12; z++) {
                    if (!edgeInRect(x, z, 0, 5, 2, 12)) {
                        continue;
                    }
                    boolean loadingFace = x == 5 && z >= 4 && z <= 10;
                    if (!loadingFace) {
                        b.force(Phase.SHELL, x, y, z,
                                cornerInRect(x, z, 0, 5, 2, 12)
                                        ? p.timber.defaultBlockState()
                                        : p.wall.defaultBlockState());
                    }
                }
            }
        }
        for (int z : new int[] {2, 4, 7, 10, 12}) {
            post(b, Phase.FRAME, 5, 1, z, 4, p.timber);
        }
        addRectGableRoof(b, m, p, 0, 5, 2, 12, 4, Direction.Axis.X);

        // The sorting apron is one continuous stair-course lean-to. Its center post is offset
        // from the three-wide public lane, while each cross-frame reaches the roof above it.
        for (int x : new int[] {6, 10, 14}) {
            int top = 4 + (14 - x) / 2;
            for (int z : new int[] {7, 12}) {
                post(b, Phase.FRAME, x, 1, z, top, p.timber);
            }
            for (int z = 7; z <= 12; z++) {
                b.force(Phase.FRAME, x, top, z, p.timber.defaultBlockState());
            }
        }
        for (int z = 7; z <= 12; z++) {
            for (int x = 6; x <= 14; x++) {
                int y = 5 + (14 - x) / 2;
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
            }
        }
        addWarehouseQuayCrane(b, p, 13, 5, 9);
        addLoadingDock(b, p, 5, 9);
        // The wharf's south sorting apron is sheltered by its lean-to but open to the yard. A
        // single post-backed sconce lights the threshold without narrowing the loading route.
        addRearWallSconce(b, 6, 2, 13);
        fixture(b, m, 2, 1, 10, Blocks.CHEST, new BlockPos(3, 1, 10));
        fixture(b, m, 2, 1, 6, Blocks.LOOM, new BlockPos(3, 1, 6));
        fixture(b, m, 11, 1, 10, Blocks.CHEST, new BlockPos(10, 1, 10));
        fixture(b, m, 13, 1, 8, Blocks.STONECUTTER, new BlockPos(12, 1, 8));
        m.entranceInside = new BlockPos(7, 1, 1);
        m.reserveCentralAisle(7, 1, 11);
        m.interiorSamples.add(new BlockPos(3, 1, 8));
        m.wallHeight = 4;
        m.roofPeak = Math.max(m.roofPeak, 9);
        addIndustryWarehouseProgram(b, m, p, "wharf");
    }

    private static void warehouseBasilica(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.WAREHOUSE, 25, 17, 18, false);
        Footprint footprint = (x, z) -> true;
        Set<BlockPos> windows = new HashSet<>();
        for (int z : new int[] {3, 7, 11, 14}) {
            windowColumn(windows, 0, z, 3, 5);
            windowColumn(windows, 24, z, 3, 5);
        }
        for (int x : new int[] {3, 7, 17, 21}) {
            windowColumn(windows, x, 16, 3, 5);
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 6);
        // Remove the domestic doorway and cut a five-wide cart portal beneath the tall nave.
        for (int x = 10; x <= 14; x++) {
            for (int y = 1; y <= 5; y++) {
                b.remove(x, y, 0);
            }
        }
        for (int x : new int[] {8, 16}) {
            for (int z = 0; z <= 16; z++) {
                for (int y = 1; y <= 11; y++) {
                    boolean clerestoryGlass = y >= 8 && y <= 9 && z % 4 == 2;
                    if (y <= 6 && z % 4 != 0) {
                        continue;
                    }
                    b.force(clerestoryGlass ? Phase.OPENING : Phase.FRAME,
                            x, y, z, clerestoryGlass
                                    ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.timber.defaultBlockState());
                }
            }
        }
        addRectGableRoof(b, m, p, 0, 8, 0, 16, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 16, 24, 0, 16, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 8, 16, 0, 16, 11, Direction.Axis.X);
        addBasilicaNaveEndClerestories(b, p);
        // Full-depth ridge spines can safely carry any approved rooftop dressing. Ordinary
        // bottom slabs here leave a visible half-block gap beneath chimneys or hoist details.
        for (int z = -1; z <= 17; z++) {
            b.force(Phase.ROOF, 4, 12, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 20, 12, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 12, 17, z, doubleRoofSlab(p));
        }
        for (int z = 0; z <= 16; z++) {
            b.force(Phase.ROOF, 8, 12, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 16, 12, z, doubleRoofSlab(p));
        }
        // Express the basilica section on the exterior. Repeated two-stage masonry buttresses
        // line up with deep-framed side bays, while bracketed clerestory lintels key the tall nave
        // back into the lower aisles. The original broad wall now reads as load-bearing bays.
        for (int z : new int[] {3, 7, 11, 14}) {
            for (int side = 0; side < 2; side++) {
                int wallX = side == 0 ? 0 : 24;
                int innerX = side == 0 ? -1 : 25;
                int outerX = side == 0 ? -2 : 26;
                Direction outward = side == 0 ? Direction.WEST : Direction.EAST;
                b.force(Phase.FOUNDATION, innerX, 0, z, p.foundation.defaultBlockState());
                b.force(Phase.FOUNDATION, outerX, 0, z, p.foundation.defaultBlockState());
                post(b, Phase.FRAME, innerX, 1, z, 5, p.foundation);
                post(b, Phase.FRAME, outerX, 1, z, 2, p.foundation);
                b.force(Phase.FRAME, innerX, 6, z, p.entryStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, outward));
                b.force(Phase.FRAME, outerX, 3, z, p.roofSlab.defaultBlockState());
                addDeepWindowFrame(b, p, wallX, z, 3, 5, outward);
            }
        }
        for (int x : new int[] {3, 7, 17, 21}) {
            addDeepWindowFrame(b, p, x, 16, 3, 5, Direction.SOUTH);
        }
        for (int z : new int[] {2, 6, 10, 14}) {
            for (int x : new int[] {7, 17}) {
                Direction outward = x < 12 ? Direction.WEST : Direction.EAST;
                for (int dz = -1; dz <= 1; dz++) {
                    b.force(Phase.FRAME, x, 10, z + dz, p.timber.defaultBlockState());
                }
                for (int dz : new int[] {-1, 1}) {
                    b.force(Phase.FRAME, x, 11, z + dz, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, outward)
                            .setValue(StairBlock.HALF, Half.TOP));
                }
            }
        }
        // Massive portal piers and a timber transom give the cart entrance a civic scale.
        for (int x : new int[] {9, 15}) {
            post(b, Phase.FRAME, x, 1, 0, 6, p.foundation);
            b.force(Phase.FRAME, x, 5, -1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        beam(b, Phase.FRAME, 9, 15, 6, 0, p.timber);
        for (int x : new int[] {8, 16}) {
            post(b, Phase.FRAME, x, 1, 0, 11, p.timber);
        }
        beam(b, Phase.FRAME, 8, 16, 11, 0, p.timber);
        addLoadingDock(b, p, 9, 15);
        addRearServiceCanopy(b, p, 2, 7, 16, 6);
        addRearServiceCanopy(b, p, 17, 22, 16, 6);
        addRearWallSconce(b, 10, 2, 17);
        addRearWallSconce(b, 14, 2, 17);
        addWarehouseQuayCrane(b, p, 24, 9, 13);
        addBasilicaDispatchOffice(b, m, p);
        for (int z : new int[] {3, 7, 11, 14}) {
            fixture(b, m, 3, 1, z, Blocks.CHEST, new BlockPos(4, 1, z));
            fixture(b, m, 21, 1, z, Blocks.CHEST, new BlockPos(20, 1, z));
        }
        // The rear deep-window frames project one block into z=15 at x=7 and x=17. Approach
        // these two workstations from the open dispatch aisle instead of registering those framed
        // cells as walkable targets.
        fixture(b, m, 6, 1, 15, Blocks.LOOM, new BlockPos(6, 1, 14));
        fixture(b, m, 18, 1, 15, Blocks.STONECUTTER, new BlockPos(18, 1, 14));
        fixture(b, m, 12, 1, 15, Blocks.CRAFTING_TABLE, new BlockPos(12, 1, 14));
        m.entranceInside = new BlockPos(12, 1, 1);
        m.reserveCentralAisle(12, 1, 14);
        m.interiorSamples.add(new BlockPos(5, 1, 8));
        m.interiorSamples.add(new BlockPos(19, 1, 8));
        m.enclosed = false;
        addIndustryWarehouseProgram(b, m, p, "basilica");
    }

    /**
     * Closes the tall nave where it rises above the six-block aisle shell. The warehouse remains
     * intentionally open at its five-wide ground-level cart portal, but the former y=7..11 void
     * made both gable ends read as an unfinished roof and exposed the hall directly to the sky.
     * Two timber-framed, three-high clerestory lights preserve the basilica scale and daylight
     * without changing that loading entrance or the independent aisle roofs.
     */
    private static void addBasilicaNaveEndClerestories(Builder b, Materials p) {
        for (int z : new int[] {0, 16}) {
            for (int x = 8; x <= 16; x++) {
                for (int y = 7; y <= 11; y++) {
                    boolean frame = x == 8 || x == 12 || x == 16 || y == 7 || y == 11;
                    b.force(frame ? Phase.FRAME : Phase.OPENING, x, y, z,
                            frame
                                    ? p.timber.defaultBlockState()
                                    : Blocks.GLASS_PANE.defaultBlockState());
                }
            }
        }
    }

    private static void granaryStilt(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GRANARY, 13, 11, 15, false);
        // Two separate grain pods stand on staddles with a central hoist bridge. Unlike the
        // broad raised barn, daylight remains visible between and beneath both bins.
        for (int z = 0; z <= 9; z++) {
            for (int x = 5; x <= 7; x++) {
                b.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int x : new int[] {1, 4, 8, 11}) {
            for (int z : new int[] {2, 8}) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                post(b, Phase.FRAME, x, 1, z, 4, p.foundation);
            }
        }
        for (int x = 0; x <= 12; x++) {
            for (int z = 1; z <= 9; z++) {
                b.force(Phase.FOUNDATION, x, 4, z,
                        (x == 6 ? p.timber : p.floor).defaultBlockState());
            }
        }
        // Independent upper shells leave the center bay open as a covered loading bridge.
        for (int[] range : new int[][] {{0, 5}, {7, 12}}) {
            int minX = range[0];
            int maxX = range[1];
            for (int y = 5; y <= 8; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = 1; z <= 9; z++) {
                        if (!edgeInRect(x, z, minX, maxX, 1, 9)) {
                            continue;
                        }
                        boolean glazed = y == 6
                                && ((z == 1 || z == 9) && x == (minX + maxX) / 2);
                        b.force(glazed ? Phase.OPENING : Phase.SHELL, x, y, z,
                                glazed ? Blocks.GLASS_PANE.defaultBlockState()
                                        : (cornerInRect(x, z, minX, maxX, 1, 9)
                                                ? p.timber : p.wall).defaultBlockState());
                    }
                }
            }
            addRectGableRoof(b, m, p, minX, maxX, 1, 9, 8, Direction.Axis.X);
        }
        addDoorAtLevel(b, p, 5, 5, 6, Direction.EAST);
        addDoorAtLevel(b, p, 7, 5, 6, Direction.WEST);
        for (int z = 4; z <= 7; z++) {
            beam(b, Phase.FRAME, 5, 7, 9, z, p.timber);
            b.force(Phase.ROOF, 6, 10, z, doubleRoofSlab(p));
        }
        // Ground-backed ladder and hatch serve both bins from the open undercroft.
        post(b, Phase.FRAME, 6, 1, 9, 4, p.timber);
        for (int y = 1; y <= 3; y++) {
            b.force(Phase.FIXTURE, 6, y, 8,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.force(Phase.FIXTURE, 6, 4, 8, Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(new BlockPos(6, 1, 8), new BlockPos(6, 3, 8)));
        for (int x : new int[] {5, 7}) {
            post(b, Phase.FRAME, x, 5, 2, 12, p.timber);
        }
        beam(b, Phase.FRAME, 5, 7, 12, 2, p.timber);
        b.force(Phase.FRAME, 6, 11, 2, Blocks.IRON_CHAIN.defaultBlockState());
        b.force(Phase.DECOR, 6, 10, 2, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        for (int x = 5; x <= 7; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        fixture(b, m, 2, 5, 7, Blocks.HAY_BLOCK, new BlockPos(3, 5, 7));
        fixture(b, m, 4, 5, 7, Blocks.CHEST, new BlockPos(3, 5, 7));
        fixture(b, m, 8, 5, 7, Blocks.HAY_BLOCK, new BlockPos(9, 5, 7));
        fixture(b, m, 10, 5, 7, Blocks.CRAFTING_TABLE, new BlockPos(9, 5, 7));
        addGrainChute(b, p, 12, 5);
        m.entranceInside = new BlockPos(6, 1, 1);
        m.accessTargets.add(new BlockPos(6, 5, 7));
        m.reserveCentralAisle(6, 1, 7);
        m.interiorSamples.add(new BlockPos(3, 5, 5));
        m.interiorSamples.add(new BlockPos(9, 5, 5));
        m.wallHeight = 8;
        addIndustryGranaryProgram(b, m, p, "stilt");
    }

    private static void granarySiloComplex(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GRANARY, 21, 17, 19, true);
        // A central threshing nave enters a transverse bank of three tall bins. Side silos rise
        // above the crossing at unequal heights, making the industrial purpose legible in profile.
        Footprint footprint = (x, z) -> (x >= 6 && x <= 14) || z >= 8;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {7, 10, 13}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 16, 2, 4);
        }
        for (int z : new int[] {5, 10, 14}) {
            if (z <= 7) {
                windowColumn(windows, 6, z, 2, 4);
                windowColumn(windows, 14, z, 2, 4);
            } else {
                windowColumn(windows, 0, z, 2, 4);
                windowColumn(windows, 20, z, 2, 4);
            }
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);
        addRectGableRoof(b, m, p, 6, 14, 0, 16, 6, Direction.Axis.X);
        addRectGableRoof(b, m, p, 0, 20, 8, 16, 6, Direction.Axis.Z);

        int crossingRoofPeak = m.roofPeak;
        for (int[] silo : new int[][] {{0, 6, 9, 16, 10}, {14, 20, 9, 16, 11}}) {
            int minX = silo[0];
            int maxX = silo[1];
            int minZ = silo[2];
            int maxZ = silo[3];
            int top = silo[4];
            clearVolume(b, minX, maxX, 7, Math.max(top, crossingRoofPeak), minZ, maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    b.force(Phase.FOUNDATION, x, 7, z, p.floor.defaultBlockState());
                }
            }
            for (int x : new int[] {minX, maxX}) {
                for (int z : new int[] {minZ, maxZ}) {
                    post(b, Phase.FRAME, x, 1, z, top, p.timber);
                }
            }
            for (int y = 8; y <= top; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (edgeInRect(x, z, minX, maxX, minZ, maxZ)) {
                            boolean vent = y == top - 1
                                    && ((x == (minX + maxX) / 2 && (z == minZ || z == maxZ))
                                            || (z == (minZ + maxZ) / 2
                                                    && (x == minX || x == maxX)));
                            b.force(vent ? Phase.OPENING : Phase.SHELL, x, y, z,
                                    vent ? Blocks.IRON_BARS.defaultBlockState()
                                            : p.wall.defaultBlockState());
                        }
                    }
                }
            }
            int ladderX = maxX - 1;
            int ladderZ = maxZ - 1;
            addBackedLadderAndHatch(b, m, p, ladderX, ladderZ, maxZ, 1, 6, 7,
                    new BlockPos(ladderX - 1, 8, ladderZ));
            addPyramidRoof(b, m, p, minX, maxX, minZ, maxZ, top);
        }
        addLayeredEntryPorch(b, p, 10, 3, 6);
        addDeepWindowFrame(b, p, 7, 0, 2, 4, Direction.NORTH);
        addDeepWindowFrame(b, p, 13, 0, 2, 4, Direction.NORTH);
        addGrainChute(b, p, 20, 12);
        addCoveredWoodBay(b, p, -1, 11, 15);
        for (int x : new int[] {2, 5, 15, 18}) {
            fixture(b, m, x, 1, 14, Blocks.HAY_BLOCK,
                    new BlockPos(x < 10 ? x + 1 : x - 1, 1, 14));
            b.put(Phase.FIXTURE, x, 2, 14, Blocks.HAY_BLOCK.defaultBlockState());
        }
        fixture(b, m, 8, 1, 15, Blocks.SMOKER, new BlockPos(9, 1, 15));
        fixture(b, m, 12, 1, 15, Blocks.CHEST, new BlockPos(11, 1, 15));
        fixture(b, m, 12, 1, 11, Blocks.CRAFTING_TABLE, new BlockPos(11, 1, 11));
        m.reserveCentralAisle(10, 1, 14);
        m.interiorSamples.add(new BlockPos(4, 1, 12));
        m.interiorSamples.add(new BlockPos(16, 1, 12));
        addIndustryGranaryProgram(b, m, p, "silo_complex");
    }

    private static void smithyCorner(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.SMITHY, 15, 13, 12, false);
        // A roofed street-front range turns into a deep western workshop, leaving an exposed
        // hammer court and a masonry bloomery in the opposite rear corner.
        Footprint footprint = (x, z) -> z <= 4 || x <= 5;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 11}) {
            windowColumn(windows, x, 0, 2, 3);
        }
        for (int z : new int[] {3, 7, 10}) {
            windowColumn(windows, 0, z, 2, 3);
        }
        windowColumn(windows, 5, 9, 2, 3);
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 5);
        // Replace the rear wall of the entry range with a three-wide court arch.
        for (int x = 6; x <= 8; x++) {
            for (int y = 1; y <= 3; y++) {
                b.remove(x, y, 4);
            }
        }
        addRectGableRoof(b, m, p, 0, 14, 0, 4, 5, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 5, 3, 12, 5, Direction.Axis.X);
        // Form the corner as a proper diagonal valley rather than hiding both crossing roofs
        // under a flat cap. Buried lower courses are removed and equal courses become solid
        // valley boards: the full-depth bearing keeps every crossing tier physically joined.
        for (int x = -1; x <= 6; x++) {
            int workshopRoofY = x <= 2 ? x + 7 : 12 - x;
            for (int z = 2; z <= 5; z++) {
                int streetRoofY = 11 - z;
                if (streetRoofY < workshopRoofY) {
                    b.remove(x, streetRoofY, z);
                } else if (workshopRoofY < streetRoofY) {
                    b.remove(x, workshopRoofY, z);
                } else {
                    b.force(Phase.ROOF, x, streetRoofY, z, doubleRoofSlab(p));
                }
            }
        }
        for (int x = 6; x <= 14; x++) {
            for (int z = 5; z <= 12; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        ((x + z) % 3 == 0 ? p.accent : p.foundation).defaultBlockState());
            }
        }
        addOpenWorkshopWing(b, p, 9, 14, 7, 12, Direction.WEST);
        for (int x : new int[] {6, 9, 14}) {
            post(b, Phase.FRAME, x, 1, 5, 4, p.timber);
            post(b, Phase.FRAME, x, 1, 12, 4, p.timber);
        }
        beam(b, Phase.FRAME, 6, 14, 4, 5, p.timber);
        addLayeredEntryPorch(b, p, 7, 3, 5);
        addDeepWindowFrame(b, p, 2, 0, 2, 3, Direction.NORTH);
        addForgeGantry(b, p, 7, 10, 5);
        // The gantry tucks immediately beneath the street-roof eave. Use a solid lintel/canopy
        // course here so the four eave stairs bear on the forge frame instead of hovering over
        // bottom slabs with a visible half-block seam.
        for (int x = 7; x <= 10; x++) {
            b.force(Phase.ROOF, x, 5, 5, doubleRoofSlab(p));
        }
        // A ground-bearing masonry bloomery replaces the former two-post chimney goal. Its
        // arched firebox, broad rear mass and tapered flue read as one piece of forge equipment.
        for (int x = 11; x <= 13; x++) {
            for (int z = 10; z <= 12; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 11; x <= 13; x++) {
                for (int z = 10; z <= 12; z++) {
                    boolean firebox = z == 10 && x == 12 && y <= 3;
                    if (!firebox && edgeInRect(x, z, 11, 13, 10, 12)) {
                        b.force(Phase.FRAME, x, y, z, p.chimney.defaultBlockState());
                    }
                }
            }
        }
        beam(b, Phase.FRAME, 11, 13, 4, 10, p.chimney);
        // Close the hollow center beneath the first flue course. The bloomery remains open at
        // the firebox, but its chimney now has direct face-contact support instead of bridging
        // over an interior air cell.
        b.force(Phase.FRAME, 12, 4, 11, p.chimney.defaultBlockState());
        for (int y = 5; y <= 6; y++) {
            for (int x = 11; x <= 13; x++) {
                for (int z = 11; z <= 12; z++) {
                    b.force(Phase.ROOF, x, y, z, p.chimney.defaultBlockState());
                }
            }
        }
        for (int y = 7; y <= 10; y++) {
            b.force(Phase.ROOF, 12, y, 11, p.chimney.defaultBlockState());
            b.force(Phase.ROOF, 12, y, 12, p.chimney.defaultBlockState());
        }
        b.force(Phase.FRAME, 12, 2, 10, Blocks.IRON_BARS.defaultBlockState());
        b.force(Phase.FRAME, 12, 3, 10, Blocks.IRON_BARS.defaultBlockState());
        fixture(b, m, 2, 1, 10, Blocks.ANVIL, new BlockPos(3, 1, 10));
        fixture(b, m, 4, 1, 8, Blocks.GRINDSTONE, new BlockPos(4, 1, 7));
        fixture(b, m, 12, 1, 10, Blocks.BLAST_FURNACE, new BlockPos(12, 1, 9));
        fixture(b, m, 13, 1, 8, Blocks.SMITHING_TABLE, new BlockPos(12, 1, 8));
        fixture(b, m, 2, 1, 3, Blocks.CHEST, new BlockPos(3, 1, 3));
        addWoodRack(b, p, 6, 11);
        // The paved court already grounds this end of the rack. Promote only its outer leg to
        // structural framing so the cap above bears on a post instead of on fixture contents.
        b.force(Phase.FRAME, 8, 1, 11, p.timber.defaultBlockState());
        addRearWallSconce(b, 6, 2, 13);
        m.entranceInside = new BlockPos(7, 1, 1);
        // Only the front court is axial; the forge gantry deliberately owns the rear court.
        m.reserveCentralAisle(7, 1, 3);
        m.interiorSamples.add(new BlockPos(3, 1, 7));
        m.wallHeight = 5;
        m.roofPeak = Math.max(m.roofPeak, 10);
        m.enclosed = false;
        addIndustrySmithyProgram(b, m, p, "corner");
    }

    private static void smithyFoundry(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.SMITHY, 23, 19, 19, false);
        // A tall casting nave drives into a transverse furnace hall. Twin masonry stacks and
        // lower side sheds produce an unmistakably industrial stepped skyline.
        Footprint footprint = (x, z) -> (x >= 7 && x <= 15) || z >= 9;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {8, 11, 14}) {
            windowColumn(windows, x, 0, 3, 5);
            windowColumn(windows, x, 18, 3, 5);
        }
        for (int z : new int[] {4, 11, 15}) {
            if (z <= 8) {
                windowColumn(windows, 7, z, 3, 5);
                windowColumn(windows, 15, z, 3, 5);
            } else {
                windowColumn(windows, 0, z, 3, 5);
                windowColumn(windows, 22, z, 3, 5);
            }
        }
        addFootprintShell(b, m, p, footprint, 7, windows);
        addFootprintTimberBand(b, m, p, footprint, 5);
        addFootprintTimberBand(b, m, p, footprint, 7);
        for (int x = 9; x <= 13; x++) {
            for (int y = 1; y <= 5; y++) {
                b.remove(x, y, 0);
            }
        }
        addRectGableRoof(b, m, p, 7, 15, 0, 18, 9, Direction.Axis.X);
        // Continuous nave posts raise the roof above the lower transverse hall.
        for (int x : new int[] {7, 15}) {
            for (int z = 0; z <= 18; z += 3) {
                post(b, Phase.FRAME, x, 1, z, 9, p.timber);
            }
        }
        // Lower furnace-hall roofs terminate against the nave instead of passing through it.
        // This leaves the framed y=8..9 clerestory exposed and removes the old flat crossing cap.
        addRectGableRoof(b, m, p, 0, 6, 9, 18, 7, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 16, 22, 9, 18, 7, Direction.Axis.Z);
        // Complete the raised nave as an iron-louvered clerestory rather than leaving a
        // two-block open band between the seven-high shell and nine-high roof spring. Continuous
        // sill/header transoms frame both gable ends; matching side plates and repeated mullions
        // make the lower furnace roofs terminate against a load-bearing wall plane.
        for (int endZ : new int[] {0, 18}) {
            beam(b, Phase.FRAME, 7, 15, 7, endZ, p.timber);
            beam(b, Phase.FRAME, 7, 15, 10, endZ, p.timber);
            for (int x = 7; x <= 15; x++) {
                Block clerestory = (x == 7 || x == 11 || x == 15)
                        ? p.timber
                        : Blocks.IRON_BARS;
                b.force(Phase.FRAME, x, 8, endZ, clerestory.defaultBlockState());
                b.force(Phase.FRAME, x, 9, endZ, clerestory.defaultBlockState());
            }
        }
        for (int sideX : new int[] {7, 15}) {
            for (int z = 1; z < 18; z++) {
                Block clerestory = z % 3 == 0 ? p.timber : Blocks.IRON_BARS;
                b.force(Phase.FRAME, sideX, 8, z, clerestory.defaultBlockState());
                b.force(Phase.FRAME, sideX, 9, z, clerestory.defaultBlockState());
                b.force(Phase.FRAME, sideX, 10, z, p.timber.defaultBlockState());
            }
        }
        // Each furnace now owns a broad vented bloomery base, a tapered shoulder and a single
        // coherent stack. They frame the casting hall without the suspended cross-chimney beam.
        for (int centerX : new int[] {3, 19}) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                for (int z = 15; z <= 17; z++) {
                    b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                }
            }
            for (int y = 1; y <= 5; y++) {
                for (int x = centerX - 1; x <= centerX + 1; x++) {
                    for (int z = 15; z <= 17; z++) {
                        boolean vent = x == centerX && z == 15 && (y == 2 || y == 3);
                        if (vent) {
                            b.force(Phase.FRAME, x, y, z, Blocks.IRON_BARS.defaultBlockState());
                        } else if (edgeInRect(x, z, centerX - 1, centerX + 1, 15, 17)) {
                            b.force(Phase.FRAME, x, y, z, p.chimney.defaultBlockState());
                        }
                    }
                }
            }
            b.force(Phase.FIXTURE, centerX, 1, 16, Blocks.CAMPFIRE.defaultBlockState());
            // The tapered stack begins over a solid hood crown, not over the bloomery's hollow
            // fire chamber. Side-contact to the masonry ring carries this center bearing.
            b.force(Phase.FRAME, centerX, 5, 16, p.chimney.defaultBlockState());
            for (int y = 6; y <= 10; y++) {
                for (int x = centerX - 1; x <= centerX + 1; x++) {
                    b.force(Phase.ROOF, x, y, 16, p.chimney.defaultBlockState());
                }
            }
            for (int y = 11; y <= 17; y++) {
                b.force(Phase.ROOF, centerX, y, 16, p.chimney.defaultBlockState());
            }
        }
        addFoundryCastingVentilator(b, p);
        addLoadingDock(b, p, 8, 14);
        addForgeGantry(b, p, 8, 14, 7);
        addRearServiceCanopy(b, p, 7, 15, 18, 7);
        for (int x : new int[] {2, 5, 17, 20}) {
            fixture(b, m, x, 1, 14, x % 2 == 0 ? Blocks.ANVIL : Blocks.GRINDSTONE,
                    new BlockPos(x < 11 ? x + 1 : x - 1, 1, 14));
        }
        fixture(b, m, 8, 1, 17, Blocks.BLAST_FURNACE, new BlockPos(9, 1, 17));
        fixture(b, m, 14, 1, 17, Blocks.SMITHING_TABLE, new BlockPos(13, 1, 17));
        fixture(b, m, 6, 1, 11, Blocks.CAULDRON, new BlockPos(7, 1, 11));
        fixture(b, m, 16, 1, 11, Blocks.CHEST, new BlockPos(15, 1, 11));
        addRearWallSconce(b, 9, 2, 19);
        addRearWallSconce(b, 13, 2, 19);
        m.entranceInside = new BlockPos(11, 1, 1);
        m.reserveCentralAisle(11, 1, 17);
        m.interiorSamples.add(new BlockPos(4, 1, 13));
        m.interiorSamples.add(new BlockPos(18, 1, 13));
        m.wallHeight = 9;
        m.roofPeak = Math.max(m.roofPeak, 17);
        m.enclosed = false;
        addIndustrySmithyProgram(b, m, p, "foundry");
    }

    private static void mineDrift(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MINE_ENTRANCE, 15, 13, 13, false);
        // A low covered rail drift runs between one enclosed tool shed and an open sorting
        // trestle. Repeated transverse frames distinguish it from the tall A-frame mines.
        addMineToolShed(b, p, 0, 4, 5, 12, Direction.EAST);
        for (int x = 9; x <= 14; x++) {
            for (int z = 2; z <= 9; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int z = 0; z <= 12; z++) {
            for (int x = 6; x <= 8; x++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        (x == 7 ? p.accent : p.foundation).defaultBlockState());
            }
            b.force(Phase.FIXTURE, 7, 1, z, Blocks.RAIL.defaultBlockState());
        }
        for (int z : new int[] {1, 4, 7, 10}) {
            post(b, Phase.FRAME, 5, 1, z, 5, p.timber);
            post(b, Phase.FRAME, 9, 1, z, 5, p.timber);
            beam(b, Phase.FRAME, 5, 9, 5, z, p.timber);
        }
        // One floor tile bridges the tool shed's open east bay to the rail deck. Without it,
        // x=5 was an unwalkable trench and both shed workstations were isolated from the entrance.
        b.force(Phase.FOUNDATION, 5, 0, 8, p.accent.defaultBlockState());
        // Roof every bay, not only the transverse frames. The paired eave courses bear directly
        // on the repeated posts and carry a continuous steep cover all the way to the portal.
        for (int z = 0; z <= 12; z++) {
            b.force(Phase.ROOF, 4, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, 5, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, 6, 7, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, 7, 8, z, p.roofSlab.defaultBlockState());
            b.force(Phase.ROOF, 8, 7, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
            b.force(Phase.ROOF, 9, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
            b.force(Phase.ROOF, 10, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        // Sorting trestle: a short sloped canopy on six posts, open to the rail line.
        for (int x : new int[] {10, 14}) {
            for (int z : new int[] {2, 5, 9}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        for (int x = 10; x <= 14; x++) {
            int roofY = 5 + (14 - x) / 2;
            for (int z = 2; z <= 9; z++) {
                b.force(Phase.ROOF, x, roofY, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        // Stepped rear cutting with a genuinely deep, rock-lined three-wide adit.
        for (int x = 3; x <= 11; x++) {
            int top = 5 + Math.max(0, 4 - Math.abs(7 - x)) / 2;
            for (int y = 1; y <= top; y++) {
                if (x >= 6 && x <= 8 && y <= 4) {
                    continue;
                }
                b.force(Phase.SHELL, x, y, 12, Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int z = 12; z <= 14; z++) {
            for (int x = 6; x <= 8; x++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                b.force(Phase.SHELL, x, 5, z, Blocks.DEEPSLATE.defaultBlockState());
            }
            if (z < 14) {
                b.force(Phase.FIXTURE, 7, 1, z, Blocks.RAIL.defaultBlockState());
            }
            for (int y = 1; y <= 5; y++) {
                b.force(Phase.SHELL, 5, y, z, Blocks.DEEPSLATE.defaultBlockState());
                b.force(Phase.SHELL, 9, y, z, Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int z : new int[] {12, 13}) {
            post(b, Phase.FRAME, 5, 1, z, 5, p.timber);
            post(b, Phase.FRAME, 9, 1, z, 5, p.timber);
            beam(b, Phase.FRAME, 5, 9, 5, z, p.timber);
        }
        for (int x = 6; x <= 8; x++) {
            for (int y = 1; y <= 4; y++) {
                b.force(Phase.SHELL, x, y, 14, Blocks.BLACKSTONE.defaultBlockState());
            }
        }
        for (int x = 6; x <= 8; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        fixture(b, m, 2, 1, 10, Blocks.STONECUTTER, new BlockPos(3, 1, 10));
        fixture(b, m, 2, 1, 7, Blocks.CHEST, new BlockPos(3, 1, 7));
        fixture(b, m, 11, 1, 8, Blocks.BLAST_FURNACE, new BlockPos(10, 1, 8));
        fixture(b, m, 13, 1, 4, Blocks.CHEST, new BlockPos(12, 1, 4));
        m.entranceInside = new BlockPos(7, 1, 1);
        for (int z = 1; z <= 13; z++) {
            m.reservedAir.add(new BlockPos(7, 2, z));
        }
        m.interiorSamples.add(new BlockPos(3, 1, 8));
        m.wallHeight = 5;
        m.roofPeak = 8;
        addIndustryMineProgram(b, m, p, "drift");
    }

    private static void mineQuarry(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MINE_ENTRANCE, 23, 21, 17, false);
        // Terraced working pads form a horseshoe around the rail throat instead of a building
        // footprint. A crusher tower and long cable gantry dominate opposite sides.
        Footprint yard = (x, z) -> (x >= 9 && x <= 13) || z >= 12
                || (x <= 6 && z >= 7) || (x >= 16 && z >= 5);
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (yard.contains(x, z)) {
                    b.put(Phase.FOUNDATION, x, 0, z,
                            ((x + 2 * z) % 5 == 0 ? p.accent : p.foundation)
                                    .defaultBlockState());
                }
            }
        }
        for (int z = 0; z <= 20; z++) {
            for (int x = 10; x <= 12; x++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
            b.force(Phase.FIXTURE, 11, 1, z, Blocks.RAIL.defaultBlockState());
        }
        // Rear rock face and portal, with a visibly battered top course.
        for (int x = 2; x <= 20; x++) {
            int top = 5 + (x % 3 == 0 ? 1 : 0);
            for (int y = 1; y <= top; y++) {
                if (x >= 10 && x <= 12 && y <= 4) {
                    continue;
                }
                b.force(Phase.SHELL, x, y, 20, Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int x : new int[] {9, 13}) {
            post(b, Phase.FRAME, x, 1, 19, 6, p.timber);
        }
        beam(b, Phase.FRAME, 9, 13, 6, 19, p.timber);

        // West crusher tower is a compact stone mass with a clipped roof.
        for (int x = 0; x <= 6; x++) {
            for (int z = 12; z <= 20; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int y = 1; y <= 8; y++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 12; z <= 20; z++) {
                    if (edgeInRect(x, z, 0, 6, 12, 20)) {
                        boolean slit = y == 5 && ((x == 3 && z == 12)
                                || (z == 16 && (x == 0 || x == 6)));
                        b.force(slit ? Phase.OPENING : Phase.SHELL, x, y, z,
                                slit ? Blocks.IRON_BARS.defaultBlockState()
                                        : p.foundation.defaultBlockState());
                    }
                }
            }
        }
        addClippedGableRoof(b, m, p, 0, 6, 12, 20, 8);
        addMineMachineBay(b, p, 16, 22, 9, 18, Direction.WEST);
        addQuarryWeighShelter(b, m, p);

        // Long open cable gantry spans the cut as two trussed rails, not a solid timber plate.
        for (int x : new int[] {5, 17}) {
            for (int z : new int[] {5, 8}) {
                post(b, Phase.FRAME, x, 1, z, 12, p.timber);
            }
        }
        for (int z : new int[] {5, 8}) {
            beam(b, Phase.FRAME, 5, 17, 12, z, p.timber);
        }
        for (int x = 5; x <= 17; x += 3) {
            b.force(Phase.FRAME, x, 12, 6, p.fence.defaultBlockState());
            b.force(Phase.FRAME, x, 12, 7, p.fence.defaultBlockState());
            b.force(Phase.FRAME, x, 11, 5, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
            b.force(Phase.FRAME, x, 11, 8, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH));
        }
        // A longitudinal trolley beam joins both grounded gantry rails and gives the hoist chain
        // a real load path instead of suspending it from an isolated fence detail.
        for (int z = 5; z <= 8; z++) {
            b.force(Phase.FRAME, 15, 12, z, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        // A low scale lamp beside the rail throat illuminates the covered loading apron while
        // keeping the three-wide track and its reserved headroom unobstructed.
        b.force(Phase.FRAME, 9, 1, 5, p.fence.defaultBlockState());
        b.force(Phase.DECOR, 9, 2, 5, Blocks.LANTERN.defaultBlockState());
        for (int y = 8; y <= 11; y++) {
            b.force(Phase.FRAME, 15, y, 6, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.DECOR, 15, 7, 6, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        for (int x = 10; x <= 12; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        fixture(b, m, 3, 1, 17, Blocks.STONECUTTER, new BlockPos(4, 1, 17));
        // A three-wide crusher portal opens directly onto a paved sorting apron.
        for (int z = 15; z <= 17; z++) {
            for (int y = 1; y <= 3; y++) {
                b.remove(6, y, z);
            }
            for (int x = 6; x <= 10; x++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.accent.defaultBlockState());
            }
        }
        fixture(b, m, 4, 1, 14, Blocks.CHEST, new BlockPos(5, 1, 14));
        fixture(b, m, 18, 1, 16, Blocks.BLAST_FURNACE, new BlockPos(17, 1, 16));
        fixture(b, m, 20, 1, 12, Blocks.CHEST, new BlockPos(19, 1, 12));
        m.entranceInside = new BlockPos(11, 1, 1);
        for (int z = 1; z <= 19; z++) {
            m.reservedAir.add(new BlockPos(11, 2, z));
        }
        m.interiorSamples.add(new BlockPos(4, 1, 16));
        m.wallHeight = 8;
        m.roofPeak = Math.max(m.roofPeak, 12);
        addIndustryMineProgram(b, m, p, "quarry");
    }

    private static void marketLane(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MARKET_SQUARE, 17, 13, 10, false);
        addMarketFloor(b, p, 17, 13);
        // Two continuous lean-to arcades frame a deliberately roofless three-wide market street.
        for (int x : new int[] {1, 5, 11, 15}) {
            int top = x < 8 ? 4 + x / 2 : 4 + (16 - x) / 2;
            for (int z : new int[] {1, 4, 7, 10, 12}) {
                post(b, Phase.FRAME, x, 1, z, top, p.timber);
            }
        }
        for (int z = 0; z <= 12; z++) {
            for (int x = 0; x <= 6; x++) {
                int y = 5 + x / 2;
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
            for (int x = 10; x <= 16; x++) {
                int y = 5 + (16 - x) / 2;
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
            }
        }
        for (int z : new int[] {1, 4, 7, 10, 12}) {
            beam(b, Phase.FRAME, 1, 5, 4, z, p.timber);
            beam(b, Phase.FRAME, 11, 15, 4, z, p.timber);
        }
        Block[] trades = {
                Blocks.LOOM, Blocks.FLETCHING_TABLE, Blocks.STONECUTTER,
                Blocks.CRAFTING_TABLE, Blocks.GRINDSTONE, Blocks.CHEST
        };
        int trade = 0;
        for (int z : new int[] {3, 7, 10}) {
            fixture(b, m, 3, 1, z, trades[trade++], new BlockPos(4, 1, z));
            fixture(b, m, 13, 1, z, trades[trade++], new BlockPos(12, 1, z));
            b.force(Phase.FIXTURE, 2, 1, z, p.accent.defaultBlockState());
            b.force(Phase.FIXTURE, 14, 1, z, p.accent.defaultBlockState());
        }
        // A tall closing arch and bell terminate the lane without blocking its route.
        for (int x : new int[] {7, 9}) {
            post(b, Phase.FRAME, x, 1, 11, 7, p.timber);
        }
        beam(b, Phase.FRAME, 7, 9, 7, 11, p.timber);
        b.force(Phase.FIXTURE, 8, 6, 11, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        for (int x = 7; x <= 9; x++) {
            b.force(Phase.ROOF, x, 8, 11, doubleRoofSlab(p));
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        m.entranceInside = new BlockPos(8, 1, 1);
        m.accessTargets.add(new BlockPos(8, 1, 11));
        m.reserveCentralAisle(8, 1, 11);
        m.wallHeight = 4;
        m.roofPeak = 8;
        addMarketInteriorProgram(b, m, p, "lane");
    }

    private static void marketBazaar(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MARKET_SQUARE, 25, 23, 17, false);
        addMarketFloor(b, p, 25, 23);
        // Four deliberately unequal corner halls orbit a tall open central exchange pavilion.
        // Their hip, gable, lean-to and flat silhouettes prevent a repeated stall-grid reading.
        int[][] halls = {{1, 7, 2, 7}, {17, 23, 2, 8}, {2, 8, 15, 21}, {16, 23, 16, 21}};
        for (int[] hall : halls) {
            for (int x : new int[] {hall[0], hall[1]}) {
                for (int z : new int[] {hall[2], hall[3]}) {
                    post(b, Phase.FRAME, x, 1, z, 4, p.timber);
                }
            }
        }
        addHipRoof(b, m, p, 1, 7, 2, 7, 4);
        addClippedGableRoof(b, m, p, 17, 23, 2, 8, 4);
        Footprint southwestHall = (x, z) -> x >= 2 && x <= 8 && z >= 15 && z <= 21;
        addShedRoof(b, m, p, southwestHall, 2, 8, 15, 21, 4, Direction.NORTH);
        // The local footprint closes the rake beneath the shed plane; a three-post high-side
        // truss carries that wall and roof to ground instead of leaving it above four short posts.
        for (int x : new int[] {2, 5, 8}) {
            post(b, Phase.FRAME, x, 1, 15, 6, p.timber);
        }
        beam(b, Phase.FRAME, 2, 8, 6, 15, p.timber);
        for (int x = 16; x <= 23; x++) {
            for (int z = 16; z <= 21; z++) {
                b.force(Phase.ROOF, x, 5, z, doubleRoofSlab(p));
            }
        }
        for (int x : new int[] {18, 21}) {
            for (int z : new int[] {16, 21}) {
                post(b, Phase.FRAME, x, 1, z, 5, p.timber);
            }
        }
        // Give every hall a complete architectural base: continuous eave fascia, paired
        // brackets, and low outer enclosures. The stalls remain open toward the central square,
        // but no roof now reads as a canopy balanced on four unrelated posts.
        for (int hallIndex = 0; hallIndex < halls.length; hallIndex++) {
            int[] hall = halls[hallIndex];
            int minX = hall[0];
            int maxX = hall[1];
            int minZ = hall[2];
            int maxZ = hall[3];
            beam(b, Phase.FRAME, minX, maxX, 4, minZ, p.timber);
            beam(b, Phase.FRAME, minX, maxX, 4, maxZ, p.timber);
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.FRAME, minX, 4, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, maxX, 4, z, p.timber.defaultBlockState());
            }
            boolean westHall = hallIndex == 0 || hallIndex == 2;
            boolean northHall = hallIndex == 0 || hallIndex == 1;
            int outerX = westHall ? minX : maxX;
            int outerZ = northHall ? minZ : maxZ;
            for (int z = minZ + 1; z < maxZ; z++) {
                b.force(Phase.SHELL, outerX, 1, z,
                        (z & 1) == 0 ? p.wall.defaultBlockState() : p.fence.defaultBlockState());
            }
            for (int x = minX + 1; x < maxX; x++) {
                b.force(Phase.SHELL, x, 1, outerZ,
                        (x & 1) == 0 ? p.wall.defaultBlockState() : p.fence.defaultBlockState());
            }
            int inwardX = westHall ? 1 : -1;
            int inwardZ = northHall ? 1 : -1;
            for (int[] corner : new int[][] {{minX, minZ}, {minX, maxZ},
                    {maxX, minZ}, {maxX, maxZ}}) {
                b.force(Phase.FRAME, corner[0] + inwardX, 3, corner[1],
                        p.roofStairs.defaultBlockState()
                                .setValue(StairBlock.FACING,
                                        westHall ? Direction.WEST : Direction.EAST)
                                .setValue(StairBlock.HALF, Half.TOP));
                b.force(Phase.FRAME, corner[0], 3, corner[1] + inwardZ,
                        p.roofStairs.defaultBlockState()
                                .setValue(StairBlock.FACING,
                                        northHall ? Direction.NORTH : Direction.SOUTH)
                                .setValue(StairBlock.HALF, Half.TOP));
            }
        }

        // Four corner piers ground the central pavilion. Its former edge-midpoint posts crossed
        // both three-wide market streets at walking height; a continuous header ring now carries
        // those four upper king posts while preserving two full blocks of clear archway below.
        for (int x : new int[] {9, 15}) {
            for (int z : new int[] {8, 14}) {
                post(b, Phase.FRAME, x, 1, z, 8, p.timber);
            }
        }
        for (int z : new int[] {8, 14}) {
            beam(b, Phase.FRAME, 9, 15, 3, z, p.timber);
            post(b, Phase.FRAME, 12, 3, z, 8, p.timber);
        }
        for (int z = 8; z <= 14; z++) {
            b.force(Phase.FRAME, 9, 3, z, p.timber.defaultBlockState());
            b.force(Phase.FRAME, 15, 3, z, p.timber.defaultBlockState());
        }
        for (int x : new int[] {9, 15}) {
            post(b, Phase.FRAME, x, 3, 11, 8, p.timber);
        }
        addPyramidRoof(b, m, p, 9, 15, 8, 14, 8);
        // Suspend the market bell from the pyramid's supported center course. The chain stops at
        // y12 directly beneath the y13 hip apex, leaving the cross-aisle clear at y1 and y2.
        for (int y = 8; y <= 12; y++) {
            b.force(Phase.FRAME, 12, y, 11, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.FIXTURE, 12, 7, 11, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        Block[] trades = {
                Blocks.LOOM, Blocks.FLETCHING_TABLE, Blocks.STONECUTTER, Blocks.GRINDSTONE,
                Blocks.CRAFTING_TABLE, Blocks.CHEST, Blocks.SMOKER, Blocks.CHEST
        };
        int[][] counters = {
                {4, 4, 5, 4}, {20, 4, 19, 4}, {4, 18, 5, 18}, {20, 18, 19, 18},
                {10, 10, 11, 10}, {14, 10, 13, 10}, {10, 16, 11, 16}, {14, 16, 13, 16}
        };
        for (int i = 0; i < counters.length; i++) {
            int[] counter = counters[i];
            fixture(b, m, counter[0], 1, counter[1], trades[i],
                    new BlockPos(counter[2], 1, counter[3]));
            b.force(Phase.FIXTURE, counter[0], 2, counter[1],
                    p.roofSlab.defaultBlockState());
            // A compact three-deep awning and side counter turn each profession block into a
            // legible merchant stall while leaving its declared horizontal access tile clear.
            int counterWingX = counter[0] < 12 ? counter[0] - 1 : counter[0] + 1;
            b.force(Phase.FIXTURE, counterWingX, 1, counter[1], upperRoofSlab(p));
            boolean northOfCrossAisle = i == 4 || i == 5;
            int awningMinZ = northOfCrossAisle ? counter[1] - 2 : counter[1] - 1;
            int awningMaxZ = northOfCrossAisle ? counter[1] : counter[1] + 1;
            int[] supports = northOfCrossAisle
                    ? new int[] {awningMinZ, awningMinZ + 1}
                    : new int[] {awningMinZ, awningMaxZ};
            for (int awningZ : supports) {
                post(b, Phase.FRAME, counter[0], 1, awningZ, 3, p.fence);
            }
            for (int awningZ = awningMinZ; awningZ <= awningMaxZ; awningZ++) {
                // Full-depth fascia is required wherever a hall roof begins in the cell above;
                // applying it to every stall keeps the awning family structurally consistent.
                b.force(Phase.ROOF, counter[0], 4, awningZ, doubleRoofSlab(p));
            }
        }
        // Perimeter lantern frames and radial paths visually bind the four halls. Each frame sits
        // immediately outside a cross-aisle edge rather than narrowing its three clear blocks.
        for (int[] post : new int[][] {{0, 11}, {24, 11}, {10, 2}, {14, 20}}) {
            post(b, Phase.FRAME, post[0], 1, post[1], 4, p.fence);
            b.force(Phase.DECOR, post[0], 5, post[1], Blocks.LANTERN.defaultBlockState());
        }
        for (int x = 11; x <= 13; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        m.entranceInside = new BlockPos(12, 1, 1);
        m.accessTargets.add(new BlockPos(12, 1, 11));
        reserveCrossAisle(m, 12, 1, 21, 3);
        m.wallHeight = 5;
        m.roofPeak = Math.max(m.roofPeak, 15);
        addMarketInteriorProgram(b, m, p, "bazaar");
    }

    private static void guardGatehouse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GUARD_POST, 15, 11, 16, false);
        Footprint footprint = (x, z) -> true;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 12}) {
            windowColumn(windows, x, 0, 3, 3);
            windowColumn(windows, x, 10, 3, 3);
        }
        for (int z : new int[] {3, 7}) {
            windowColumn(windows, 0, z, 3, 3);
            windowColumn(windows, 14, z, 3, 3);
        }
        addFootprintShell(b, m, p, footprint, 6, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 6);

        // A genuine three-wide fortified gate runs through the building. The paired guard rooms
        // remain enclosed to either side and support two unequal roofed watch towers.
        for (int z : new int[] {0, 10}) {
            for (int x = 6; x <= 8; x++) {
                for (int y = 1; y <= 4; y++) {
                    b.remove(x, y, z);
                }
            }
        }
        for (BlockPos opening : windows) {
            b.force(Phase.OPENING, opening.getX(), opening.getY(), opening.getZ(),
                    Blocks.IRON_BARS.defaultBlockState());
        }
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 10; z++) {
                b.force(Phase.FOUNDATION, x, 6, z, p.foundation.defaultBlockState());
            }
        }
        // The east watch room carries a narrower, set-back turret rather than cloning the west.
        for (int x = 10; x <= 14; x++) {
            for (int z = 1; z <= 9; z++) {
                b.force(Phase.FOUNDATION, x, 6, z, p.foundation.defaultBlockState());
            }
        }
        for (int y = 7; y <= 9; y++) {
            for (int x = 0; x <= 5; x++) {
                for (int z = 0; z <= 10; z++) {
                    if (edgeInRect(x, z, 0, 5, 0, 10)) {
                        boolean slit = y == 8 && ((x == 0 || x == 5) && z == 5
                                || (z == 0 || z == 10) && (x == 2 || x == 3));
                        b.force(slit ? Phase.OPENING : Phase.SHELL, x, y, z,
                                slit ? Blocks.IRON_BARS.defaultBlockState()
                                        : p.foundation.defaultBlockState());
                    }
                }
            }
        }
        for (int y = 7; y <= 8; y++) {
            for (int x = 10; x <= 14; x++) {
                for (int z = 1; z <= 9; z++) {
                    if (edgeInRect(x, z, 10, 14, 1, 9)) {
                        boolean slit = y == 8 && ((x == 10 || x == 14) && z == 5
                                || (z == 1 || z == 9) && x == 12);
                        b.force(slit ? Phase.OPENING : Phase.SHELL, x, y, z,
                                slit ? Blocks.IRON_BARS.defaultBlockState()
                                        : p.accent.defaultBlockState());
                    }
                }
            }
        }
        addPyramidRoof(b, m, p, 0, 5, 0, 10, 9);
        addPyramidRoof(b, m, p, 10, 14, 1, 9, 8);

        addBackedLadderAndHatch(b, m, p, 4, 9, 10, 1, 5, 6,
                new BlockPos(3, 7, 9));
        addBackedLadderAndHatch(b, m, p, 13, 8, 9, 1, 5, 6,
                new BlockPos(12, 7, 8));

        // The bridge and machicolated lintels tie both towers together without lowering the gate.
        for (int z = 3; z <= 7; z++) {
            beam(b, Phase.FRAME, 5, 9, 6, z, p.foundation);
        }
        for (int x = 5; x <= 9; x++) {
            b.force(Phase.FRAME, x, 5, 0, p.foundation.defaultBlockState());
            b.force(Phase.FRAME, x, 5, 10, p.foundation.defaultBlockState());
        }
        for (int x : new int[] {1, 4, 10, 13}) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, -1, 4, p.foundation);
        }
        beam(b, Phase.FRAME, 1, 13, 5, -1, p.foundation);
        for (int x = 6; x <= 8; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }

        fixture(b, m, 2, 1, 7, Blocks.FLETCHING_TABLE, new BlockPos(3, 1, 7));
        fixture(b, m, 4, 1, 4, Blocks.TARGET, new BlockPos(4, 1, 5));
        fixture(b, m, 10, 1, 4, Blocks.GRINDSTONE, new BlockPos(10, 1, 5));
        fixture(b, m, 12, 1, 7, Blocks.CHEST, new BlockPos(11, 1, 7));
        m.entranceInside = new BlockPos(7, 1, 1);
        m.reserveCentralAisle(7, 1, 9);
        m.interiorSamples.add(new BlockPos(3, 1, 5));
        m.interiorSamples.add(new BlockPos(11, 1, 5));
        m.wallHeight = 9;
        m.enclosed = false;
    }

    private static void guardCitadel(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GUARD_POST, 21, 19, 23, false);
        // Four barrack ranges enclose a real parade court. A fifth, taller command tower grows
        // from the rear range, so the citadel reads as a defended complex rather than a big box.
        Footprint footprint = (x, z) -> z <= 5 || z >= 13 || x <= 5 || x >= 15;
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {3, 7, 13, 17}) {
            windowColumn(windows, x, 0, 3, 3);
            windowColumn(windows, x, 18, 3, 3);
        }
        for (int z : new int[] {4, 9, 14}) {
            windowColumn(windows, 0, z, 3, 3);
            windowColumn(windows, 20, z, 3, 3);
        }
        addFootprintShell(b, m, p, footprint, 7, windows);
        addFootprintTimberBand(b, m, p, footprint, 5);
        for (BlockPos opening : windows) {
            b.force(Phase.OPENING, opening.getX(), opening.getY(), opening.getZ(),
                    Blocks.IRON_BARS.defaultBlockState());
        }
        // Open the public gate and both inner court thresholds at a full three-block width.
        for (int z : new int[] {0, 5, 13}) {
            for (int x = 9; x <= 11; x++) {
                for (int y = 1; y <= (z == 0 ? 4 : 3); y++) {
                    b.remove(x, y, z);
                }
            }
        }

        addRectGableRoof(b, m, p, 0, 20, 0, 5, 7, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 20, 13, 18, 7, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 5, 5, 13, 7, Direction.Axis.X);
        addRectGableRoof(b, m, p, 15, 20, 5, 13, 7, Direction.Axis.X);
        addBastionTower(b, m, p, 0, 5, 0, 5, 13);
        addBastionTower(b, m, p, 15, 20, 0, 5, 12);
        addBastionTower(b, m, p, 0, 5, 13, 18, 12);
        addBastionTower(b, m, p, 15, 20, 13, 18, 13);
        addBastionTower(b, m, p, 8, 12, 13, 18, 16);
        // Every hollow bastion is a usable guard room. Back each ladder against an existing
        // range wall, hatch the transfer deck, and leave a clear landing within the tower.
        addBackedLadderAndHatch(b, m, p, 4, 4, 5, 1, 6, 7,
                new BlockPos(3, 8, 4));
        addBackedLadderAndHatch(b, m, p, 16, 4, 5, 1, 6, 7,
                new BlockPos(17, 8, 4));
        addBackedLadderAndHatch(b, m, p, 4, 14, 13, 1, 6, 7,
                new BlockPos(3, 8, 14));
        addBackedLadderAndHatch(b, m, p, 16, 14, 13, 1, 6, 7,
                new BlockPos(17, 8, 14));
        addBackedLadderAndHatch(b, m, p, 11, 17, 18, 1, 6, 7,
                new BlockPos(10, 8, 17));

        // Deep front gate piers, wall-walk brackets and a stone parade route establish scale.
        for (int x : new int[] {6, 8, 12, 14}) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, -1, 6, p.foundation);
        }
        beam(b, Phase.FRAME, 6, 14, 7, -1, p.foundation);
        for (int x = 9; x <= 11; x++) {
            for (int z = -2; z <= 17; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        ((x + z) & 1) == 0 ? p.foundation.defaultBlockState()
                                : p.accent.defaultBlockState());
            }
        }
        for (int[] buttress : new int[][] {{-1, 8}, {-1, 11}, {21, 8}, {21, 11}}) {
            b.force(Phase.FOUNDATION, buttress[0], 0, buttress[1],
                    p.foundation.defaultBlockState());
            post(b, Phase.FRAME, buttress[0], 1, buttress[1], 6, p.foundation);
        }
        // The front bastion ladders occupy x4/x16,z4.  Approach these fixtures from their
        // opposite clear sides so the workstation targets remain ordinary walkable floor tiles
        // without sacrificing any of the five upper-room routes.
        fixture(b, m, 3, 1, 4, Blocks.FLETCHING_TABLE, new BlockPos(2, 1, 4));
        fixture(b, m, 17, 1, 4, Blocks.TARGET, new BlockPos(18, 1, 4));
        fixture(b, m, 3, 1, 15, Blocks.GRINDSTONE, new BlockPos(4, 1, 15));
        fixture(b, m, 17, 1, 15, Blocks.CHEST, new BlockPos(16, 1, 15));
        addRearWallSconce(b, 10, 2, 19);
        m.entranceInside = new BlockPos(10, 1, 1);
        m.reserveCentralAisle(10, 1, 17);
        m.accessTargets.add(new BlockPos(10, 1, 9));
        m.interiorSamples.add(new BlockPos(3, 1, 9));
        m.interiorSamples.add(new BlockPos(17, 1, 9));
        m.wallHeight = 16;
        m.enclosed = false;
        addGuardInteriorProgram(b, m, p, "citadel");
    }

    private static void exchangeLoggia(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.EXCHANGE_HALL, 19, 15, 16, false);
        // Narrow side counting rooms, a deep rear chamber and a three-wide open loggia form an
        // unmistakable E-plan. The front procession has two sheltered garden courts beside it.
        Footprint footprint = (x, z) -> z >= 7 || x <= 4 || x >= 14 || (x >= 8 && x <= 10);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {2, 16}) {
            windowColumn(windows, x, 0, 2, 4);
            windowColumn(windows, x, 14, 2, 4);
        }
        for (int z : new int[] {3, 10}) {
            windowColumn(windows, 0, z, 2, 4);
            windowColumn(windows, 18, z, 2, 4);
        }
        for (int x : new int[] {6, 12}) {
            windowColumn(windows, x, 7, 2, 4);
        }
        addFootprintShell(b, m, p, footprint, 5, windows);
        addFootprintTimberBand(b, m, p, footprint, 3);
        addFootprintTimberBand(b, m, p, footprint, 5);

        // Open both long faces of the central loggia but retain three pairs of grounded columns.
        for (int z = 1; z <= 6; z++) {
            if (z == 3 || z == 6) {
                continue;
            }
            for (int x : new int[] {8, 10}) {
                for (int y = 1; y <= 4; y++) {
                    b.remove(x, y, z);
                }
            }
        }
        addRectGableRoof(b, m, p, 0, 4, 0, 14, 5, Direction.Axis.X);
        addRectGableRoof(b, m, p, 14, 18, 0, 14, 5, Direction.Axis.X);
        addHipRoof(b, m, p, 4, 14, 7, 14, 5);
        // A shallow gabled loggia canopy gives the processional front a real silhouette.
        for (int z = -1; z <= 7; z++) {
            b.force(Phase.ROOF, 7, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, 8, 7, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, 9, 8, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 10, 7, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
            b.force(Phase.ROOF, 11, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        for (int x : new int[] {7, 11}) {
            for (int z : new int[] {0, 3, 6}) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                post(b, Phase.FRAME, x, 1, z, 5, p.timber);
            }
        }
        addDeepWindowFrame(b, p, 2, 0, 2, 4, Direction.NORTH);
        addDeepWindowFrame(b, p, 16, 0, 2, 4, Direction.NORTH);
        addBayWindow(b, p, 0, 10, Direction.WEST);
        addBayWindow(b, p, 18, 10, Direction.EAST);
        for (int x = 8; x <= 10; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x = 6; x <= 12; x++) {
            if (x == 9) {
                continue;
            }
            b.put(Phase.FRAME, x, 1, 11, p.fence.defaultBlockState());
            b.put(Phase.FIXTURE, x, 2, 11, p.roofSlab.defaultBlockState());
        }
        fixture(b, m, 9, 1, 13, BankerProfessionSupport.exchangeDeskOrLectern(),
                new BlockPos(9, 1, 12));
        fixture(b, m, 6, 1, 13, Blocks.BOOKSHELF, new BlockPos(7, 1, 13));
        fixture(b, m, 12, 1, 13, Blocks.BOOKSHELF, new BlockPos(11, 1, 13));
        fixture(b, m, 2, 1, 11, Blocks.ENDER_CHEST, new BlockPos(3, 1, 11));
        fixture(b, m, 16, 1, 11, Blocks.CHEST, new BlockPos(15, 1, 11));
        addRearWallSconce(b, 7, 2, 15);
        addRearWallSconce(b, 11, 2, 15);
        m.entranceInside = new BlockPos(9, 1, 1);
        m.reserveCentralAisle(9, 1, 12);
        m.interiorSamples.add(new BlockPos(2, 1, 6));
        m.interiorSamples.add(new BlockPos(16, 1, 6));
        m.wallHeight = 5;
        m.roofPeak = Math.max(m.roofPeak, 12);
        m.enclosed = false;
    }

    private static void exchangeBourse(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.EXCHANGE_HALL, 27, 21, 22, true);
        // A double-court bourse combines a perimeter ring, central processional spine and a
        // seven-block-wide rear tower. Its five roof axes are intentionally unlike either civic
        // hall: the public route is legible while side galleries remain independently zoned.
        Footprint footprint = (x, z) -> z <= 5 || z >= 15 || x <= 5 || x >= 21
                || (x >= 12 && x <= 14)
                || (x >= 10 && x <= 16 && z >= 12);
        Set<BlockPos> windows = new HashSet<>();
        for (int x : new int[] {3, 8, 18, 23}) {
            windowColumn(windows, x, 0, 2, 5);
            windowColumn(windows, x, 20, 2, 5);
        }
        for (int z : new int[] {4, 9, 16}) {
            windowColumn(windows, 0, z, 2, 5);
            windowColumn(windows, 26, z, 2, 5);
        }
        for (int z : new int[] {8, 12}) {
            windowColumn(windows, 5, z, 2, 5);
            windowColumn(windows, 21, z, 2, 5);
        }
        addFootprintShell(b, m, p, footprint, 7, windows);
        addFootprintTimberBand(b, m, p, footprint, 4);
        addFootprintTimberBand(b, m, p, footprint, 7);
        addRectGableRoof(b, m, p, 0, 26, 0, 5, 7, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 26, 15, 20, 7, Direction.Axis.Z);
        addRectGableRoof(b, m, p, 0, 5, 5, 15, 7, Direction.Axis.X);
        addRectGableRoof(b, m, p, 21, 26, 5, 15, 7, Direction.Axis.X);
        addRectGableRoof(b, m, p, 12, 14, 5, 15, 7, Direction.Axis.X);

        // The rear bourse tower rises from a complete transfer floor and terminates in a sealed
        // pyramidal roof. Every upper corner has a continuous ground-bearing frame below it.
        clearVolume(b, 10, 16, 8, 14, 12, 20);
        for (int x = 10; x <= 16; x++) {
            for (int z = 12; z <= 20; z++) {
                b.force(Phase.FOUNDATION, x, 7, z, p.foundation.defaultBlockState());
            }
        }
        for (int y = 8; y <= 14; y++) {
            for (int x = 10; x <= 16; x++) {
                for (int z = 12; z <= 20; z++) {
                    if (!edgeInRect(x, z, 10, 16, 12, 20)) {
                        continue;
                    }
                    boolean window = y >= 10 && y <= 12
                            && ((x == 13 && (z == 12 || z == 20))
                                    || (z == 16 && (x == 10 || x == 16)));
                    b.force(window ? Phase.OPENING : Phase.SHELL, x, y, z,
                            window ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.wall.defaultBlockState());
                }
            }
        }
        for (int x : new int[] {10, 16}) {
            for (int z : new int[] {12, 20}) {
                post(b, Phase.FRAME, x, 1, z, 14, p.timber);
            }
        }
        addBackedLadderAndHatch(b, m, p, 12, 19, 20, 1, 6, 7,
                new BlockPos(11, 8, 19));
        addPyramidRoof(b, m, p, 10, 16, 12, 20, 14);

        // A three-deep ceremonial portico and paired side bays make the frontage architectural,
        // not merely decorated. The exact center stays clear from stairs to the exchange desk.
        for (int x : new int[] {8, 10, 16, 18}) {
            for (int z : new int[] {-1, -2}) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
            post(b, Phase.FRAME, x, 1, -2, 7, p.accent);
        }
        beam(b, Phase.FRAME, 8, 18, 8, -2, p.timber);
        for (int x = 7; x <= 19; x++) {
            b.force(Phase.ROOF, x, 9, -2, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, 9, -1, doubleRoofSlab(p));
        }
        for (int x = 12; x <= 14; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            b.force(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        addBayWindow(b, p, 0, 9, Direction.WEST);
        addBayWindow(b, p, 26, 9, Direction.EAST);
        addDeepWindowFrame(b, p, 3, 0, 2, 5, Direction.NORTH);
        addDeepWindowFrame(b, p, 23, 0, 2, 5, Direction.NORTH);
        addBourseLedgerPavilion(b, m, p);

        // Each formal court has doors on both its spine and gallery side, plus a stone crosswalk.
        addDoorAt(b, p, 12, 9, Direction.EAST);
        addDoorAt(b, p, 5, 9, Direction.WEST);
        addDoorAt(b, p, 14, 9, Direction.WEST);
        addDoorAt(b, p, 21, 9, Direction.EAST);
        for (int x = 6; x <= 11; x++) {
            b.force(Phase.FOUNDATION, x, 0, 9, p.accent.defaultBlockState());
        }
        for (int x = 15; x <= 20; x++) {
            b.force(Phase.FOUNDATION, x, 0, 9, p.accent.defaultBlockState());
        }

        for (int x = 10; x <= 16; x++) {
            if (x == 13) {
                continue;
            }
            b.put(Phase.FRAME, x, 1, 17, p.fence.defaultBlockState());
            b.put(Phase.FIXTURE, x, 2, 17, p.roofSlab.defaultBlockState());
        }
        fixture(b, m, 13, 1, 19, BankerProfessionSupport.exchangeDeskOrLectern(),
                new BlockPos(13, 1, 18));
        // The rear floor is deliberately zoned: teller line, archive stacks and a caged vault
        // all read independently while the axial route and sole exchange desk remain clear.
        for (int x : new int[] {10, 11, 15, 16}) {
            fixture(b, m, x, 1, 19, Blocks.BOOKSHELF,
                    new BlockPos(x, 1, 18));
        }
        fixture(b, m, 3, 1, 17, Blocks.ENDER_CHEST, new BlockPos(4, 1, 17));
        fixture(b, m, 23, 1, 17, Blocks.CHEST, new BlockPos(22, 1, 17));
        fixture(b, m, 24, 1, 19, Blocks.CHEST, new BlockPos(23, 1, 19));
        addRearWallSconce(b, 11, 2, 21);
        addRearWallSconce(b, 15, 2, 21);
        for (int z = 18; z <= 19; z++) {
            b.put(Phase.FRAME, 22, 1, z, Blocks.IRON_BARS.defaultBlockState());
            b.put(Phase.FRAME, 22, 2, z, Blocks.IRON_BARS.defaultBlockState());
        }
        // Waiting benches line both side galleries without intruding into either court door.
        for (int z : new int[] {7, 12}) {
            b.put(Phase.FIXTURE, 2, 1, z, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.put(Phase.FIXTURE, 24, 1, z, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        addLongTable(b, p, 6, 3, 5);
        addLongTable(b, p, 20, 3, 5);
        m.entranceInside = new BlockPos(13, 1, 1);
        m.reserveCentralAisle(13, 1, 18);
        m.interiorSamples.add(new BlockPos(3, 1, 10));
        m.interiorSamples.add(new BlockPos(23, 1, 10));
        m.wallHeight = 14;
        addExchangeInteriorProgram(b, m, p, "bourse");
    }

    private static void addRearServiceCanopy(
            Builder b, Materials p, int minX, int maxX, int wallZ, int roofY) {
        int outsideZ = wallZ + 1;
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.FOUNDATION, x, 0, outsideZ, p.foundation.defaultBlockState());
            b.force(Phase.ROOF, x, roofY, outsideZ, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, roofY, outsideZ + 1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH));
        }
        for (int x : new int[] {minX, maxX}) {
            post(b, Phase.FRAME, x, 1, outsideZ, roofY - 1, p.timber);
        }
        beam(b, Phase.FRAME, minX, maxX, roofY - 1, outsideZ, p.timber);
    }

    private static void addCoachhouseBalcony(
            Builder b, Materials p, int minX, int maxX, int roofY) {
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.floor.defaultBlockState());
            b.force(Phase.ROOF, x, roofY, -1, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, roofY, -2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x = minX; x <= maxX; x += 2) {
            post(b, Phase.FRAME, x, 1, -1, roofY - 1, p.timber);
        }
        beam(b, Phase.FRAME, minX, maxX, roofY - 1, -1, p.timber);
        for (int x = minX + 1; x < maxX; x += 2) {
            b.force(Phase.DECOR, x, roofY - 2, -1, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
    }

    private static void addWarehouseQuayCrane(
            Builder b, Materials p, int outsideX, int centerZ, int crownY) {
        for (int z : new int[] {centerZ - 2, centerZ + 2}) {
            b.force(Phase.FOUNDATION, outsideX, 0, z, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, outsideX, 1, z, crownY - 1, p.timber);
        }
        for (int z = centerZ - 2; z <= centerZ + 2; z++) {
            b.force(Phase.FRAME, outsideX, crownY, z, p.timber.defaultBlockState());
        }
        for (int x = outsideX - 5; x <= outsideX + 2; x++) {
            b.force(Phase.FRAME, x, crownY, centerZ, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        for (int y = crownY - 4; y < crownY; y++) {
            b.force(Phase.FRAME, outsideX + 2, y, centerZ,
                    Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.DECOR, outsideX + 2, crownY - 5, centerZ,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /**
     * A small weigh-and-dispatch office gives the basilica warehouse a fourth working roof mass.
     * It is deliberately separated from the three parallel hall ridges, but its paved scale lane
     * joins the loading dock so the pavilion reads as part of the freight complex rather than a
     * detached ornamental hut.
     */
    private static void addBasilicaDispatchOffice(Builder b, Metadata m, Materials p) {
        int minX = 3;
        int maxX = 7;
        int frontZ = -6;
        int rearZ = -4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = frontZ; z <= rearZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z = frontZ; z <= rearZ; z++) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = 1; y <= 4; y++) {
                boolean frontDoor = x == 5 && y <= 2;
                if (!frontDoor) {
                    b.force(y == 2 && x != 5 ? Phase.OPENING : Phase.SHELL,
                            x, y, frontZ,
                            y == 2 && x != 5
                                    ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.wall.defaultBlockState());
                }
                b.force(y == 2 && x != 5 ? Phase.OPENING : Phase.SHELL,
                        x, y, rearZ,
                        y == 2 && x != 5
                                ? Blocks.GLASS_PANE.defaultBlockState()
                                : p.wall.defaultBlockState());
            }
        }
        addDoorAt(b, p, 5, frontZ, Direction.NORTH);
        beam(b, Phase.FRAME, minX, maxX, 4, frontZ, p.timber);
        beam(b, Phase.FRAME, minX, maxX, 4, rearZ, p.timber);
        addRectGableRoof(b, m, p, minX, maxX, frontZ, rearZ, 4, Direction.Axis.X);

        // A stone-edged weigh lane grounds the office and terminates against the main dock stair.
        for (int z = rearZ + 1; z <= -2; z++) {
            for (int x = 5; x <= 8; x++) {
                b.putIfFree(Phase.FOUNDATION, x, 0, z,
                        (x == 5 || x == 8 ? p.foundation : p.floor).defaultBlockState());
            }
        }
        b.force(Phase.FIXTURE, 4, 1, -5, Blocks.BARREL.defaultBlockState());
        b.force(Phase.FIXTURE, 6, 1, -5, Blocks.LECTERN.defaultBlockState());
        b.force(Phase.DECOR, 5, 3, rearZ, Blocks.BELL.defaultBlockState());
    }

    /**
     * A raised, open-sided hoist house gives the loft granary a second working roof instead of a
     * decorative rooftop token. Its stone-edged loading spur meets the undercroft beside the
     * central stair, while the hanging chain, barrel and hay make the projection read as grain
     * handling from the normal north approach.
     */
    private static void addRaisedGranaryHoistHouse(Builder b, Metadata m, Materials p) {
        int minX = 0;
        int maxX = 4;
        int frontZ = -5;
        int rearZ = -3;
        for (int x = minX; x <= maxX; x++) {
            for (int z = frontZ; z <= rearZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        (x == minX || x == maxX ? p.foundation : p.floor).defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {frontZ, rearZ}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        beam(b, Phase.FRAME, minX, maxX, 4, frontZ, p.timber);
        beam(b, Phase.FRAME, minX, maxX, 4, rearZ, p.timber);
        addRectGableRoof(b, m, p, minX, maxX, frontZ, rearZ, 4, Direction.Axis.X);

        for (int z = rearZ + 1; z <= 0; z++) {
            for (int x = 3; x <= 5; x++) {
                b.putIfFree(Phase.FOUNDATION, x, 0, z,
                        (x == 3 ? p.foundation : p.floor).defaultBlockState());
            }
        }
        // Continue the hoist chain to the gable ridge at y=8. Stopping at y=3 left the barrel
        // assembly visibly detached from the roof despite the grounded hoist house around it.
        for (int y = 2; y <= 7; y++) {
            b.force(Phase.FRAME, 2, y, -4, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.FIXTURE, 2, 1, -4, Blocks.BARREL.defaultBlockState());
        b.force(Phase.FIXTURE, 1, 1, -4, Blocks.HAY_BLOCK.defaultBlockState());
        b.force(Phase.DECOR, 3, 3, frontZ, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /**
     * A low grain-intake house balances the windmill's exposed sail with a visibly grounded
     * process building. The offset north-east gable forms its own roof anchor, and a paved feed
     * lane joins it to the mill without narrowing the central entrance route.
     */
    private static void addWindmillGrainIntake(Builder b, Metadata m, Materials p) {
        int minX = 11;
        int maxX = 15;
        int frontZ = -5;
        int rearZ = -3;
        for (int x = minX; x <= maxX; x++) {
            for (int z = frontZ; z <= rearZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        (z == frontZ ? p.foundation : p.floor).defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {frontZ, rearZ}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = minX + 1; x < maxX; x++) {
                b.force(Phase.SHELL, x, y, rearZ, p.wall.defaultBlockState());
            }
        }
        beam(b, Phase.FRAME, minX, maxX, 4, frontZ, p.timber);
        beam(b, Phase.FRAME, minX, maxX, 4, rearZ, p.timber);
        addRectGableRoof(b, m, p, minX, maxX, frontZ, rearZ, 4, Direction.Axis.X);

        for (int z = rearZ + 1; z <= 0; z++) {
            for (int x = 11; x <= 13; x++) {
                b.putIfFree(Phase.FOUNDATION, x, 0, z,
                        (x == 13 ? p.foundation : p.floor).defaultBlockState());
            }
        }
        b.force(Phase.FIXTURE, 12, 1, -4, Blocks.COMPOSTER.defaultBlockState());
        b.force(Phase.FIXTURE, 14, 1, -4, Blocks.BARREL.defaultBlockState());
        b.force(Phase.FIXTURE, 12, 2, -4, Blocks.HAY_BLOCK.defaultBlockState());
        b.force(Phase.DECOR, 14, 3, frontZ, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /**
     * Two connected flue columns over the casting-nave ridge form a real north ventilator. The
     * ridge cells are made full-height before the masonry rises, preventing the half-slab roof
     * gap that previously produced floating caps while adding the foundry's fourth skyline zone.
     */
    private static void addFoundryCastingVentilator(Builder b, Materials p) {
        for (int z = 2; z <= 3; z++) {
            b.force(Phase.ROOF, 11, 15, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 11, 16, z, p.chimney.defaultBlockState());
            b.force(Phase.ROOF, 11, 17, z, p.chimney.defaultBlockState());
            b.force(Phase.ROOF, 11, 18, z, p.accent.defaultBlockState());
        }
    }

    /**
     * A small masonry ledger pavilion gives the bourse a fourth, front-left civic roof mass. It
     * remains open toward the processional forecourt, but a backed archive wall and connected
     * stone walk make it useful exchange infrastructure rather than a detached garden folly.
     */
    private static void addBourseLedgerPavilion(Builder b, Metadata m, Materials p) {
        int minX = 1;
        int maxX = 5;
        int frontZ = -6;
        int rearZ = -4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = frontZ; z <= rearZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        (edgeInRect(x, z, minX, maxX, frontZ, rearZ)
                                ? p.foundation
                                : p.floor).defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {frontZ, rearZ}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.accent);
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = 1; y <= 4; y++) {
                Block block = y == 2 && x != 3 ? Blocks.GLASS_PANE : p.wall;
                b.force(block == Blocks.GLASS_PANE ? Phase.OPENING : Phase.SHELL,
                        x, y, rearZ, block.defaultBlockState());
            }
        }
        beam(b, Phase.FRAME, minX, maxX, 4, frontZ, p.timber);
        beam(b, Phase.FRAME, minX, maxX, 4, rearZ, p.timber);
        addRectGableRoof(b, m, p, minX, maxX, frontZ, rearZ, 4, Direction.Axis.X);

        for (int z = rearZ + 1; z <= 0; z++) {
            b.putIfFree(Phase.FOUNDATION, 5, 0, z, p.accent.defaultBlockState());
        }
        b.force(Phase.FIXTURE, 2, 1, -5, Blocks.BOOKSHELF.defaultBlockState());
        b.force(Phase.FIXTURE, 4, 1, -5, Blocks.LECTERN.defaultBlockState());
        b.force(Phase.SHELL, 3, 2, rearZ, Blocks.EMERALD_BLOCK.defaultBlockState());
        b.force(Phase.DECOR, 3, 3, frontZ, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    private static void addWindmillSailCross(
            Builder b,
            Materials p,
            int outsideX,
            int centerZ,
            int centerY,
            int radius) {
        for (int offset = -radius; offset <= radius; offset++) {
            b.force(Phase.FRAME, outsideX, centerY + offset, centerZ,
                    p.timber.defaultBlockState());
            b.force(Phase.FRAME, outsideX, centerY, centerZ + offset,
                    p.timber.defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        b.force(Phase.FRAME, outsideX, centerY, centerZ, p.accent.defaultBlockState());
        for (int offset : new int[] {-radius, -radius + 1, radius - 1, radius}) {
            b.force(Phase.DECOR, outsideX + 1, centerY + offset, centerZ,
                    Blocks.OAK_TRAPDOOR.defaultBlockState());
            b.force(Phase.DECOR, outsideX + 1, centerY, centerZ + offset,
                    Blocks.OAK_TRAPDOOR.defaultBlockState());
        }
    }

    private static void addMineMachineBay(
            Builder b,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Direction openToward) {
        int openX = openToward == Direction.EAST ? maxX : minX;
        int closedX = openToward == Direction.EAST ? minX : maxX;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        (x == closedX ? p.foundation : p.floor).defaultBlockState());
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            post(b, Phase.FRAME, closedX, 1, z, 4, p.foundation);
        }
        for (int z : new int[] {minZ, maxZ}) {
            post(b, Phase.FRAME, openX, 1, z, 4, p.timber);
        }
        int highRoofY = 5 + (maxX - minX) / 2;
        // Close the tall lean-to edge and both triangular end rakes all the way to the stepped
        // roof. Without this infill, wider machine bays expose a floating three-block roof band.
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = 5; y < highRoofY; y++) {
                b.force(Phase.SHELL, closedX, y, z, p.foundation.defaultBlockState());
            }
        }
        for (int z : new int[] {minZ, maxZ}) {
            for (int x = minX; x <= maxX; x++) {
                int roofY = 5
                        + (openToward == Direction.EAST ? maxX - x : x - minX) / 2;
                for (int y = 5; y < roofY; y++) {
                    b.force(Phase.SHELL, x, y, z, p.wall.defaultBlockState());
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            int roofY = 5 + (openToward == Direction.EAST ? maxX - x : x - minX) / 2;
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.ROOF, x, roofY, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, openToward.getOpposite()));
            }
        }
    }

    private static void addGuildArcade(
            Builder b, Materials p, int width, int depth, int center) {
        int roofY = 6;
        for (int z : new int[] {1, 5, depth - 6, depth - 2}) {
            post(b, Phase.FRAME, 1, 1, z, roofY - 1, p.timber);
            post(b, Phase.FRAME, width - 2, 1, z, roofY - 1, p.timber);
        }
        for (int x : new int[] {1, 5, width - 6, width - 2}) {
            if (x < center - 1 || x > center + 1) {
                post(b, Phase.FRAME, x, 1, 1, roofY - 1, p.timber);
            }
            post(b, Phase.FRAME, x, 1, depth - 2, roofY - 1, p.timber);
        }
        for (int z = 0; z < depth; z++) {
            for (int x : new int[] {0, 1, 2, width - 3, width - 2, width - 1}) {
                b.force(Phase.ROOF, x, roofY, z, doubleRoofSlab(p));
            }
        }
        for (int x = 0; x < width; x++) {
            if (x >= center - 1 && x <= center + 1) {
                continue;
            }
            for (int z : new int[] {0, 1, 2, depth - 3, depth - 2, depth - 1}) {
                b.force(Phase.ROOF, x, roofY, z, doubleRoofSlab(p));
            }
        }
    }

    private static void addBastionTower(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight) {
        // Lower range roofs must terminate at the tower walls. Remove every retained roof/gable
        // cell inside the upper volume before laying the continuous transfer deck.
        clearVolume(b, minX, maxX, 7, wallHeight, minZ, maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.FOUNDATION, x, 7, z, p.foundation.defaultBlockState());
            }
        }
        for (int y = 8; y <= wallHeight; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!edgeInRect(x, z, minX, maxX, minZ, maxZ)) {
                        continue;
                    }
                    boolean slit = y == 10
                            && ((x == (minX + maxX) / 2 && (z == minZ || z == maxZ))
                                    || (z == (minZ + maxZ) / 2
                                            && (x == minX || x == maxX)));
                    b.force(slit ? Phase.OPENING : Phase.SHELL, x, y, z,
                            slit ? Blocks.IRON_BARS.defaultBlockState()
                                    : p.foundation.defaultBlockState());
                }
            }
        }
        addPyramidRoof(b, m, p, minX, maxX, minZ, maxZ, wallHeight);
    }

    private static void clearVolume(
            Builder b,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    b.remove(x, y, z);
                }
            }
        }
    }

    private static void addBackedLadderAndHatch(
            Builder b,
            Metadata m,
            Materials p,
            int x,
            int ladderZ,
            int supportZ,
            int fromY,
            int toY,
            int hatchY,
            BlockPos upperAccess) {
        if (Math.abs(supportZ - ladderZ) != 1 || hatchY != toY + 1) {
            throw new IllegalArgumentException("Invalid authored ladder geometry");
        }
        Direction facing = supportZ > ladderZ ? Direction.NORTH : Direction.SOUTH;
        for (int y = fromY; y <= toY; y++) {
            b.force(Phase.FRAME, x, y, supportZ, p.timber.defaultBlockState());
            b.force(Phase.FIXTURE, x, y, ladderZ,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        }
        b.force(Phase.FIXTURE, x, hatchY, ladderZ, Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(
                new BlockPos(x, fromY, ladderZ), new BlockPos(x, toY, ladderZ)));
        m.accessTargets.add(upperAccess);
    }

    private static void addCountinghouseWing(
            Builder b,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int canopyY) {
        int outsideZ = -1;
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.FOUNDATION, x, 0, outsideZ, p.foundation.defaultBlockState());
            b.force(Phase.ROOF, x, canopyY, outsideZ, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, canopyY, outsideZ - 1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[] {minX, (minX + maxX) / 2, maxX}) {
            post(b, Phase.FRAME, x, 1, outsideZ, canopyY - 1, p.timber);
        }
        beam(b, Phase.FRAME, minX, maxX, canopyY - 1, outsideZ, p.timber);
        for (int z = minZ; z <= maxZ; z += 4) {
            // Full pilasters, rather than isolated accent blocks, articulate the counting wing
            // and carry its upper trim into the continuous floor foundation.
            post(b, Phase.FRAME, minX, 1, z, 3, p.accent);
            post(b, Phase.FRAME, maxX, 1, z, 3, p.accent);
        }
    }

    /** A rear clerestory hall gives the compact house a cross-axis, windowed roof mass. */
    private static void addRaisedRearHall(Builder b, Metadata m, Materials p) {
        int minX = 2;
        int maxX = m.width - 3;
        int wallZ = m.depth - 1;
        int deckY = 9;
        for (int x : new int[] {minX, maxX}) {
            post(b, Phase.FRAME, x, 1, wallZ, deckY - 1, p.timber);
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = 5; y < deckY; y++) {
                boolean clerestory = (x == minX + 2 || x == maxX - 2)
                        && (y == 6 || y == 7);
                b.force(clerestory ? Phase.OPENING : Phase.SHELL, x, y, wallZ,
                        clerestory ? Blocks.GLASS_PANE.defaultBlockState()
                                : p.wall.defaultBlockState());
            }
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = wallZ - 1; z <= wallZ + 1; z++) {
                b.force(Phase.ROOF, x, deckY, z, doubleRoofSlab(p));
            }
            b.force(Phase.ROOF, x, deckY + 1, wallZ - 1,
                    p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
            b.force(Phase.ROOF, x, deckY + 1, wallZ + 1,
                    p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            b.force(Phase.ROOF, x, deckY + 2, wallZ, doubleRoofSlab(p));
        }
        m.roofPeak = Math.max(m.roofPeak, deckY + 2);
    }

    /** Broad tapered masonry stack: the compact smithy reads as a forge, not a storage shed. */
    private static void addBroadForgeStack(Builder b, Metadata m, Materials p) {
        int wallZ = m.depth - 1;
        int centerX = m.width - 3;
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            post(b, Phase.FRAME, x, 1, wallZ, 7, p.chimney);
        }
        post(b, Phase.FRAME, centerX, 8, wallZ, 9, p.chimney);
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            b.force(Phase.ROOF, x, 10, wallZ, doubleRoofSlab(p));
        }
        m.roofPeak = Math.max(m.roofPeak, 10);
    }

    /** Grounded stone adit arch with a retained three-wide rail mouth and stepped crown. */
    private static void addFortifiedAditPortal(Builder b, Metadata m, Materials p) {
        int frontZ = -2;
        for (int x = 1; x <= m.width - 2; x++) {
            b.force(Phase.FOUNDATION, x, 0, frontZ, p.foundation.defaultBlockState());
        }
        for (int z = frontZ + 1; z <= 0; z++) {
            for (int x : new int[] {1, m.width - 2}) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                post(b, Phase.SHELL, x, 1, z, 4, Blocks.DEEPSLATE_BRICKS);
            }
        }
        for (int x = 1; x <= 3; x++) {
            post(b, Phase.SHELL, x, 1, frontZ, 5, Blocks.DEEPSLATE_BRICKS);
            post(b, Phase.SHELL, m.width - 1 - x, 1, frontZ, 5, Blocks.DEEPSLATE_BRICKS);
        }
        beam(b, Phase.FRAME, 1, m.width - 2, 5, frontZ, p.timber);
        beam(b, Phase.SHELL, 2, m.width - 3, 6, frontZ, Blocks.DEEPSLATE_BRICKS);
        beam(b, Phase.SHELL, 3, m.width - 4, 7, frontZ, Blocks.DEEPSLATE_BRICKS);
        beam(b, Phase.SHELL, 4, m.width - 5, 8, frontZ, Blocks.DEEPSLATE_BRICKS);
        for (int z = frontZ; z < 0; z++) {
            b.force(Phase.FOUNDATION, m.width / 2, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.FIXTURE, m.width / 2, 1, z, Blocks.RAIL.defaultBlockState());
            m.reservedAir.add(new BlockPos(m.width / 2, 2, z));
        }
        m.roofPeak = Math.max(m.roofPeak, 8);
    }

    /** Windowed counting tower and stepped civic crown for the landmark exchange hall. */
    private static void addCountinghouseTower(Builder b, Metadata m, Materials p) {
        int minX = 8;
        int maxX = 14;
        int minZ = 5;
        int maxZ = 11;
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {minZ, maxZ}) {
                post(b, Phase.FRAME, x, 1, z, 15, p.timber);
            }
        }
        for (int y = 10; y <= 15; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!edgeInRect(x, z, minX, maxX, minZ, maxZ)
                            || cornerInRect(x, z, minX, maxX, minZ, maxZ)) {
                        continue;
                    }
                    boolean window = y >= 11 && y <= 13
                            && ((x == 11 && (z == minZ || z == maxZ))
                                    || (z == 8 && (x == minX || x == maxX)));
                    b.force(window ? Phase.OPENING : Phase.SHELL, x, y, z,
                            window ? Blocks.GLASS_PANE.defaultBlockState()
                                    : p.wall.defaultBlockState());
                }
            }
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                b.force(Phase.ROOF, x, 16, z, doubleRoofSlab(p));
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.ROOF, x, 17, z, doubleRoofSlab(p));
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                b.force(Phase.ROOF, x, 18, z, p.roofSlab.defaultBlockState());
            }
        }
        m.roofPeak = Math.max(m.roofPeak, 18);
    }

    private static void addCountinghouseTellerRail(
            Builder b, Materials p, int minX, int maxX, int z, int aisleX) {
        for (int x = minX; x <= maxX; x++) {
            if (x == aisleX) {
                continue;
            }
            b.put(Phase.FIXTURE, x, 1, z, p.accent.defaultBlockState());
            b.put(Phase.FIXTURE, x, 2, z, Blocks.IRON_BARS.defaultBlockState());
        }
    }

    private static void addCompactHoist(
            Builder b, Materials p, int centerX, int z, int baseY) {
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            post(b, Phase.FRAME, x, baseY, z, baseY + 3, p.timber);
        }
        beam(b, Phase.FRAME, centerX - 2, centerX + 2, baseY + 3, z, p.timber);
        b.put(Phase.FRAME, centerX, baseY + 2, z, Blocks.IRON_CHAIN.defaultBlockState());
        b.put(Phase.DECOR, centerX, baseY + 1, z, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    private static void addForgeGantry(
            Builder b, Materials p, int minX, int maxX, int z) {
        for (int x : new int[] {minX, maxX}) {
            post(b, Phase.FRAME, x, 1, z, 4, p.timber);
        }
        beam(b, Phase.FRAME, minX, maxX, 4, z, p.timber);
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.ROOF, x, 5, z, p.roofSlab.defaultBlockState());
        }
        b.put(Phase.DECOR, (minX + maxX) / 2, 3, z, Blocks.IRON_CHAIN.defaultBlockState());
    }

    /**
     * Grounds the open forge loggia with a broad masonry hood and a two-flue stack. The stack is
     * deliberately forward of the paired rear furnace chimneys, giving the hammer hall a third
     * readable skyline region instead of merely thickening the existing rear cluster.
     */
    private static void addHammerhallForgeHood(Builder b, Materials p) {
        for (int z = 4; z <= 6; z++) {
            b.force(Phase.FOUNDATION, 19, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.FIXTURE, 19, 1, z,
                    z == 5 ? Blocks.BLAST_FURNACE.defaultBlockState()
                            : p.chimney.defaultBlockState());
        }
        for (int x = 18; x <= 20; x++) {
            for (int z = 4; z <= 6; z++) {
                b.force(Phase.FRAME, x, 3, z, p.chimney.defaultBlockState());
            }
        }
        for (int z = 4; z <= 5; z++) {
            for (int y = 4; y <= 14; y++) {
                b.force(y <= 5 ? Phase.FRAME : Phase.ROOF,
                        19, y, z, p.chimney.defaultBlockState());
            }
            b.force(Phase.ROOF, 19, 15, z, p.accent.defaultBlockState());
        }
    }

    private static void addCompactHeadframe(
            Builder b, Materials p, int centerX, int z, int halfSpan, int crownY) {
        for (int x : new int[] {centerX - halfSpan, centerX + halfSpan}) {
            post(b, Phase.FRAME, x, 1, z, crownY - 1, p.timber);
        }
        beam(b, Phase.FRAME, centerX - halfSpan, centerX + halfSpan, crownY, z, p.timber);
        for (int y = crownY - 3; y < crownY; y++) {
            b.force(Phase.FRAME, centerX, y, z, Blocks.IRON_CHAIN.defaultBlockState());
        }
    }

    private static void addOpenPavilion(
            Builder b, Materials p, int centerX, int centerZ, int radius, int roofY) {
        for (int x : new int[] {centerX - radius, centerX + radius}) {
            for (int z : new int[] {centerZ - radius, centerZ + radius}) {
                post(b, Phase.FRAME, x, 1, z, roofY - 1, p.timber);
            }
        }
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                b.force(Phase.ROOF, x, roofY, z, doubleRoofSlab(p));
            }
        }
        for (int x = centerX - radius + 1; x < centerX + radius; x++) {
            for (int z = centerZ - radius + 1; z < centerZ + radius; z++) {
                b.force(Phase.ROOF, x, roofY + 1, z, p.roofSlab.defaultBlockState());
            }
        }
    }

    private static void addMarketCornerSign(Builder b, Materials p, int x, int z) {
        post(b, Phase.FRAME, x, 1, z, 3, p.fence);
        // Key the sign arm into an existing stall canopy when the compact crossroads inherits
        // one at this corner; a solid accent beam is a stronger support than its replaced slab.
        b.force(Phase.FRAME, x, 4, z, p.accent.defaultBlockState());
        b.force(Phase.FRAME, x, 4, z - 1, p.accent.defaultBlockState());
        b.force(Phase.DECOR, x, 3, z - 1, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /**
     * Extends the compact cottage's main pitch into a real catslide service wing. The wing is
     * intentionally open-fronted: it adds a useful wood-and-hearth yard without pretending that
     * a one-room cottage contains another sealed room behind decorative wall blocks.
     */
    private static void addCatslideHearthWing(Builder b, Materials p) {
        for (int x = -2; x <= -1; x++) {
            for (int z = 4; z <= 8; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int z : new int[] {4, 8}) {
            post(b, Phase.FRAME, -2, 1, z, 2, p.timber);
            b.force(Phase.SHELL, -1, 1, z, p.wall.defaultBlockState());
            b.force(Phase.FRAME, -1, 2, z, p.timber.defaultBlockState());
        }
        for (int z = 4; z <= 8; z++) {
            // The high course keys into the existing x=-1 eave; the low course forms the
            // characteristic long catslide rather than a detached flat canopy.
            b.force(Phase.ROOF, -1, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, -2, 3, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
        }
        for (int z = 5; z <= 7; z++) {
            b.force(Phase.FIXTURE, -2, 1, z, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        b.force(Phase.FRAME, -1, 3, 5, Blocks.IRON_CHAIN.defaultBlockState());
        b.force(Phase.DECOR, -1, 2, 5, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /** Converts the inherited warehouse door into a framed, three-wide working cart portal. */
    private static void addTallWarehousePortal(Builder b, Materials p, int centerX) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int y = 1; y <= 4; y++) {
                b.remove(x, y, 0);
            }
        }
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            post(b, Phase.FRAME, x, 1, 0, 5, p.timber);
        }
        beam(b, Phase.FRAME, centerX - 2, centerX + 2, 5, 0, p.timber);
    }

    /**
     * Replaces the compact warehouse's anonymous ridge with a low glazed roof monitor. It stays
     * inside the descriptor's ten-block height while giving the long brown roof a second, clearly
     * supported silhouette and useful clerestory light.
     */
    private static void addWarehouseRoofMonitor(
            Builder b, Metadata m, Materials p, int minX, int maxX) {
        clearVolume(b, minX, maxX, 7, 10, 2, 6);
        for (int y = 7; y <= 8; y++) {
            for (int x = minX; x <= maxX; x++) {
                boolean mullion = x == minX || x == maxX || (x - minX) % 2 == 0;
                for (int z : new int[] {2, 6}) {
                    b.force(mullion ? Phase.FRAME : Phase.OPENING, x, y, z,
                            (mullion ? p.timber : Blocks.GLASS_PANE).defaultBlockState());
                }
            }
            for (int z = 3; z <= 5; z++) {
                b.force(Phase.FRAME, minX, y, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, maxX, y, z, p.timber.defaultBlockState());
            }
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = 1; z <= 7; z++) {
                b.force(Phase.ROOF, x, 9, z, doubleRoofSlab(p));
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = 3; z <= 5; z++) {
                b.force(Phase.ROOF, x, 10, z, p.roofSlab.defaultBlockState());
            }
        }
        m.roofPeak = 10;
    }

    /** A portal-aligned timber jib makes the loading mechanism legible from the public approach. */
    private static void addPortalHoist(Builder b, Materials p, int x) {
        for (int z = 0; z >= -4; z--) {
            b.force(Phase.FRAME, x, 6, z, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        for (int y = 3; y <= 5; y++) {
            b.force(Phase.FRAME, x, y, -4, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.DECOR, x, 2, -4, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /**
     * A grounded loading dormer makes the warehouse crane visible from the public approach.
     * Its short cross-gable grows out of the cart portal and shelters the hoist beam; this is a
     * third working mass, separate from both the long warehouse ridge and the quay crane.
     */
    private static void addFrontLoadingHoistHouse(
            Builder b, Metadata m, Materials p, int centerX) {
        int minX = centerX - 2;
        int maxX = centerX + 2;
        int frontZ = -3;
        for (int x = minX - 1; x <= maxX + 1; x++) {
            b.force(Phase.FOUNDATION, x, 0, frontZ, p.foundation.defaultBlockState());
        }
        for (int x : new int[] {minX - 1, maxX + 1}) {
            post(b, Phase.FRAME, x, 1, frontZ, 5, p.timber);
            post(b, Phase.FRAME, x, 1, 0, 5, p.timber);
            for (int z = frontZ; z <= 0; z++) {
                b.force(Phase.FRAME, x, 5, z, p.timber.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
        }
        beam(b, Phase.FRAME, minX - 1, maxX + 1, 5, frontZ, p.timber);
        beam(b, Phase.FRAME, minX - 1, maxX + 1, 5, 0, p.timber);
        addRectGableRoof(b, m, p, minX, maxX, frontZ, 0, 5, Direction.Axis.X);
        // Where the dormer dies into the main roof, turn the ridge bearing into a full block so
        // its upper slab never floats over the main gable's partial stair course.
        b.force(Phase.ROOF, centerX, 8, 1, doubleRoofSlab(p));
        addPortalHoist(b, p, centerX);
    }

    /**
     * Cross-gabled hood carried by the four grounded winding posts. The hood intersects the main
     * pitch at its spring line, so it reads as an engine-house monitor rather than a floating cap.
     */
    private static void addWindingHeadframeMonitor(Builder b, Metadata m, Materials p) {
        for (int x : new int[] {6, 12}) {
            for (int z : new int[] {5, 10}) {
                b.force(Phase.FRAME, x, 13, z, p.timber.defaultBlockState());
            }
        }
        beam(b, Phase.FRAME, 6, 12, 13, 5, p.timber);
        beam(b, Phase.FRAME, 6, 12, 13, 10, p.timber);
        addRectGableRoof(b, m, p, 6, 12, 5, 10, 13, Direction.Axis.X);
        for (int x : new int[] {7, 9, 11}) {
            b.force(Phase.DECOR, x, 14, 5, Blocks.OAK_TRAPDOOR.defaultBlockState());
            b.force(Phase.DECOR, x, 14, 10, Blocks.OAK_TRAPDOOR.defaultBlockState());
        }
    }

    /** A two-column, directly roof-bearing engine flue with a solid contrasting cap course. */
    private static void addWindingRoofVentilator(
            Builder b, Materials p, int x, int z, int baseY, int height) {
        for (int dz = 0; dz <= 1; dz++) {
            for (int y = baseY; y < baseY + height - 1; y++) {
                b.force(Phase.ROOF, x, y, z + dz, p.chimney.defaultBlockState());
            }
            b.force(Phase.ROOF, x, baseY + height - 1, z + dz,
                    p.accent.defaultBlockState());
        }
    }

    /** A front-facing, roof-supported signal arch distinguishes the public side of the watch. */
    private static void addWatchSignalArch(Builder b, Materials p, int centerX) {
        for (int x : new int[] {centerX - 1, centerX + 1}) {
            b.force(Phase.FRAME, x, 16, 0, p.accent.defaultBlockState());
        }
        beam(b, Phase.FRAME, centerX - 1, centerX + 1, 17, 0, p.timber);
        // A directly borne weather cap turns the arch into a legible signal cupola and gives the
        // otherwise single-mass tower a real front roof punctuation rather than a floating token.
        b.force(Phase.ROOF, centerX - 1, 18, 0, p.roofStairs.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST));
        b.force(Phase.ROOF, centerX, 18, 0, doubleRoofSlab(p));
        b.force(Phase.ROOF, centerX + 1, 18, 0, p.roofStairs.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST));
        b.force(Phase.DECOR, centerX, 16, 0, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /**
     * A shallow, gabled receiving bin gives the windmill a distinct rear-east service mass. The
     * open timber frame keeps it readable as grain handling rather than a miniature second house.
     */
    private static void addWindmillRoofedGrainBin(Builder b, Metadata m, Materials p) {
        int minX = 18;
        int maxX = 20;
        int frontZ = 9;
        int rearZ = 10;
        for (int x = minX; x <= maxX; x++) {
            for (int z = frontZ; z <= rearZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {frontZ, rearZ}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        for (int z = frontZ; z <= rearZ; z++) {
            beam(b, Phase.FRAME, minX, maxX, 4, z, p.timber);
        }
        addRectGableRoof(b, m, p, minX, maxX, frontZ, rearZ, 4, Direction.Axis.X);
        b.force(Phase.FIXTURE, 19, 1, rearZ, Blocks.BARREL.defaultBlockState());
        b.force(Phase.FIXTURE, 19, 2, rearZ, Blocks.HAY_BLOCK.defaultBlockState());
    }

    /**
     * A grounded weigh shelter gives the quarry's otherwise rear-heavy roofscape a functional
     * front-yard mass while leaving the three-wide central rail throat completely unobstructed.
     */
    private static void addQuarryWeighShelter(Builder b, Metadata m, Materials p) {
        int minX = 0;
        int maxX = 4;
        int frontZ = 1;
        int rearZ = 3;
        for (int x = minX; x <= maxX; x++) {
            for (int z = frontZ; z <= rearZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        ((x + z) & 1) == 0
                                ? p.foundation.defaultBlockState()
                                : p.accent.defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {frontZ, rearZ}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        beam(b, Phase.FRAME, minX, maxX, 4, frontZ, p.timber);
        beam(b, Phase.FRAME, minX, maxX, 4, rearZ, p.timber);
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = 1; y <= 3; y++) {
                boolean scaleWindow = x == 2 && y == 2;
                b.force(scaleWindow ? Phase.OPENING : Phase.SHELL, x, y, rearZ,
                        scaleWindow ? Blocks.IRON_BARS.defaultBlockState()
                                : p.wall.defaultBlockState());
            }
        }
        addRectGableRoof(b, m, p, minX, maxX, frontZ, rearZ, 4, Direction.Axis.X);
        b.force(Phase.FIXTURE, 1, 1, 2, Blocks.BARREL.defaultBlockState());
        b.force(Phase.FIXTURE, 3, 1, 2, Blocks.STONECUTTER.defaultBlockState());
        // The one-wide scale lane joins the shelter to the main paved rail approach at x=10.
        for (int x = maxX + 1; x <= 9; x++) {
            b.force(Phase.FOUNDATION, x, 0, 2, p.accent.defaultBlockState());
        }
    }

    /**
     * Builds a cross-gabled cruck porch whose exposed stepped blades carry the roof directly to
     * grounded front posts. This is a massing change, not a decorative A pasted onto a barn wall.
     */
    private static void addCruckCrossGable(Builder b, Metadata m, Materials p) {
        for (int x : new int[] {1, 9}) {
            b.force(Phase.FOUNDATION, x, 0, -2, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, -2, 5, p.timber);
        }
        beam(b, Phase.FRAME, 1, 9, 5, -2, p.timber);
        addRectGableRoof(b, m, p, 1, 9, -1, 1, 5, Direction.Axis.X);
        for (int step = 0; step <= 4; step++) {
            int y = 6 + step;
            b.force(Phase.FRAME, step + 1, y, -1, p.timber.defaultBlockState());
            b.force(Phase.FRAME, 9 - step, y, -1, p.timber.defaultBlockState());
        }
        post(b, Phase.FRAME, 5, 5, -1, 10, p.timber);
        for (int x : new int[] {1, 3, 7, 9}) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            b.force(Phase.FRAME, x, 1, -1, p.accent.defaultBlockState());
        }
    }

    /** A roofed side bin makes the grain chute terminate in a believable storage composition. */
    private static void addProjectingGrainBin(Builder b, Materials p, int x, int centerZ) {
        for (int z = centerZ - 2; z <= centerZ + 2; z++) {
            b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.ROOF, x, 4, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, x + 1, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        for (int z : new int[] {centerZ - 2, centerZ + 2}) {
            post(b, Phase.FRAME, x, 1, z, 3, p.timber);
        }
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            b.force(Phase.FIXTURE, x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            b.force(Phase.FIXTURE, x, 2, z,
                    z == centerZ ? Blocks.BARREL.defaultBlockState()
                            : Blocks.HAY_BLOCK.defaultBlockState());
        }
    }

    /**
     * Roofs the four trading arms while retaining the reserved three-wide cardinal walkways.
     * Deliberate colored center stripes make orientation readable without noisy random blocks.
     */
    private static void addMarketCrossroadsArms(
            Builder b, Materials p, int centerX, int centerZ) {
        for (int z : new int[] {1, 2, 3, 4, 5, 9, 10, 11, 12, 13}) {
            beam(b, Phase.FRAME, centerX - 2, centerX + 2, 4, z, p.timber);
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                Block roof = x == centerX
                        ? (z < centerZ ? Blocks.EMERALD_BLOCK : Blocks.GOLD_BLOCK)
                        : p.roofSlab;
                b.force(Phase.ROOF, x, 5, z,
                        roof == p.roofSlab ? doubleRoofSlab(p) : roof.defaultBlockState());
            }
        }
        for (int x : new int[] {1, 2, 3, 4, 5, 9, 10, 11, 12, 13}) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                b.force(Phase.FRAME, x, 4, z, p.timber.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                Block roof = z == centerZ
                        ? (x < centerX ? Blocks.LAPIS_BLOCK : Blocks.REDSTONE_BLOCK)
                        : p.roofSlab;
                b.force(Phase.ROOF, x, 5, z,
                        roof == p.roofSlab ? doubleRoofSlab(p) : roof.defaultBlockState());
            }
        }
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            for (int z : new int[] {1, 5, 9, 13}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
        for (int z : new int[] {centerZ - 2, centerZ + 2}) {
            for (int x : new int[] {1, 5, 9, 13}) {
                post(b, Phase.FRAME, x, 1, z, 4, p.timber);
            }
        }
    }

    /** A compact ten-block-high bell lantern anchors the crossroads at normal view distance. */
    private static void addCrossroadsBellLantern(
            Builder b, Materials p, int centerX, int centerZ) {
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            for (int z : new int[] {centerZ - 2, centerZ + 2}) {
                post(b, Phase.FRAME, x, 1, z, 7, p.timber);
            }
        }
        beam(b, Phase.FRAME, centerX - 2, centerX + 2, 6, centerZ - 2, p.timber);
        beam(b, Phase.FRAME, centerX - 2, centerX + 2, 6, centerZ + 2, p.timber);
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            b.force(Phase.FRAME, centerX - 2, 6, z, p.timber.defaultBlockState());
            b.force(Phase.FRAME, centerX + 2, 6, z, p.timber.defaultBlockState());
        }
        for (int layer = 0; layer < 3; layer++) {
            int radius = 3 - layer;
            int y = 7 + layer;
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (edgeInRect(x, z,
                            centerX - radius, centerX + radius,
                            centerZ - radius, centerZ + radius)) {
                        b.force(Phase.ROOF, x, y, z, inwardRoofStair(
                                p,
                                x,
                                z,
                                centerX - radius,
                                centerX + radius,
                                centerZ - radius,
                                centerZ + radius));
                    }
                }
            }
        }
        b.force(Phase.ROOF, centerX, 9, centerZ, doubleRoofSlab(p));
        b.force(Phase.ROOF, centerX, 10, centerZ, p.accent.defaultBlockState());
        // A supported cross-tie carries the bell yoke below; the chains above remain tension
        // hangers and are never treated as load-bearing structure.
        beam(b, Phase.FRAME, centerX - 2, centerX + 2, 6, centerZ, p.timber);
        for (int y = 7; y <= 8; y++) {
            b.force(Phase.FRAME, centerX, y, centerZ, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.force(Phase.FIXTURE, centerX, 5, centerZ, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
    }

    /** A continuous cantilever gallery gives the compact blockhouse a defended silhouette. */
    private static void addBlockhouseFightingGallery(Builder b, Materials p) {
        for (int x = 0; x <= 8; x++) {
            b.remove(x, 10, 0);
            b.remove(x, 10, 8);
        }
        for (int z = 1; z < 8; z++) {
            b.remove(0, 10, z);
            b.remove(8, 10, z);
        }
        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 9; z++) {
                if (!edgeInRect(x, z, -1, 9, -1, 9)) {
                    continue;
                }
                b.force(Phase.ROOF, x, 9, z, doubleRoofSlab(p));
                if ((x + z) % 2 == 0 || (x == -1 || x == 9) && (z == -1 || z == 9)) {
                    b.force(Phase.FRAME, x, 10, z, p.foundation.defaultBlockState());
                }
            }
        }
        for (int x = 1; x <= 7; x += 2) {
            b.force(Phase.FRAME, x, 8, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.force(Phase.FRAME, x, 8, 9, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int z = 1; z <= 7; z += 2) {
            b.force(Phase.FRAME, -1, 8, z, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.force(Phase.FRAME, 9, 8, z, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int x : new int[] {2, 6}) {
            b.force(Phase.OPENING, x, 6, 0, Blocks.IRON_BARS.defaultBlockState());
            b.force(Phase.OPENING, x, 6, 8, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int z : new int[] {2, 6}) {
            b.force(Phase.OPENING, 0, 6, z, Blocks.IRON_BARS.defaultBlockState());
            b.force(Phase.OPENING, 8, 6, z, Blocks.IRON_BARS.defaultBlockState());
        }
    }

    /** A masonry portico and compact pediment distinguish the branch exchange from a house. */
    private static void addBranchCivicFrontispiece(Builder b, Metadata m, Materials p) {
        for (int x : new int[] {3, 5, 9, 11}) {
            b.force(Phase.FOUNDATION, x, 0, -2, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, -2, 6, p.accent);
        }
        beam(b, Phase.FRAME, 2, 12, 6, -2, p.timber);
        for (int x = 2; x <= 12; x++) {
            b.force(Phase.ROOF, x, 7, -2, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, 7, -1, doubleRoofSlab(p));
        }
        addRectGableRoof(b, m, p, 4, 10, -2, -1, 7, Direction.Axis.X);
        b.force(Phase.SHELL, 6, 8, -2, Blocks.EMERALD_BLOCK.defaultBlockState());
        b.force(Phase.SHELL, 7, 8, -2, Blocks.GOLD_BLOCK.defaultBlockState());
        b.force(Phase.SHELL, 8, 8, -2, Blocks.EMERALD_BLOCK.defaultBlockState());
        b.force(Phase.FIXTURE, 7, 5, -2, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        m.roofPeak = Math.max(m.roofPeak, 12);
    }

    private static void addGuardButtress(
            Builder b, Materials p, int x, int minZ, int maxZ) {
        for (int z : new int[] {minZ, maxZ}) {
            b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, z, 5, p.foundation);
            b.force(Phase.FRAME, x, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 0 ? Direction.WEST : Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private static void addGateMachicolation(
            Builder b, Materials p, int centerX, int width) {
        int half = width / 2;
        for (int x = centerX - half; x <= centerX + half; x++) {
            b.force(Phase.FRAME, x, 5, -1, p.foundation.defaultBlockState());
            if ((x - centerX) % 2 == 0) {
                b.force(Phase.DECOR, x, 4, -1, p.accent.defaultBlockState());
            }
        }
    }

    @FunctionalInterface
    private interface Footprint {
        boolean contains(int x, int z);
    }

    private static void windowColumn(
            Set<BlockPos> windows, int x, int z, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            windows.add(new BlockPos(x, y, z));
        }
    }

    private static void addFootprintShell(
            Builder b,
            Metadata m,
            Materials p,
            Footprint footprint,
            int wallHeight,
            Set<BlockPos> windows) {
        int center = m.width / 2;
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (!footprint.contains(x, z)) {
                    continue;
                }
                boolean boundary = footprintBoundary(m, footprint, x, z);
                b.put(Phase.FOUNDATION, x, 0, z,
                        (boundary ? p.foundation : p.floor).defaultBlockState());
                if (!boundary) {
                    continue;
                }
                int exposed = exposedSides(m, footprint, x, z);
                for (int y = 1; y <= wallHeight; y++) {
                    if (z == 0 && x == center && y <= 2) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    boolean window = windows.contains(position);
                    // Corners express the load-bearing frame.  The old modulo rule peppered the
                    // wall-top course with unrelated log blocks, which read as procedural noise
                    // and made otherwise coherent facades look unfinished.  Each revision-2
                    // master now adds its own deliberate sill, lintel and storey bands instead.
                    Block block = window
                            ? Blocks.GLASS_PANE
                            : exposed >= 2 ? p.timber : p.wall;
                    b.put(window ? Phase.OPENING : Phase.SHELL,
                            x, y, z, block.defaultBlockState());
                }
            }
        }
        if (!insideFootprint(m, footprint, center, 0)
                || !insideFootprint(m, footprint, center, 1)) {
            throw new IllegalStateException("Authored footprint lost its centered public threshold");
        }
        addDoor(b, p, center, 0);
        m.wallHeight = wallHeight;
        m.enclosed = true;
        m.entranceInside = new BlockPos(center, 1, 1);
        int sampleZ = Math.max(2, m.depth / 2);
        if (insideFootprint(m, footprint, center, sampleZ)) {
            m.interiorSamples.add(new BlockPos(center, 1, sampleZ));
        }
    }

    private static boolean footprintBoundary(
            Metadata m, Footprint footprint, int x, int z) {
        return !insideFootprint(m, footprint, x - 1, z)
                || !insideFootprint(m, footprint, x + 1, z)
                || !insideFootprint(m, footprint, x, z - 1)
                || !insideFootprint(m, footprint, x, z + 1);
    }

    private static int exposedSides(
            Metadata m, Footprint footprint, int x, int z) {
        int count = 0;
        count += insideFootprint(m, footprint, x - 1, z) ? 0 : 1;
        count += insideFootprint(m, footprint, x + 1, z) ? 0 : 1;
        count += insideFootprint(m, footprint, x, z - 1) ? 0 : 1;
        count += insideFootprint(m, footprint, x, z + 1) ? 0 : 1;
        return count;
    }

    private static boolean insideFootprint(
            Metadata m, Footprint footprint, int x, int z) {
        return x >= 0 && x < m.width && z >= 0 && z < m.depth
                && footprint.contains(x, z);
    }

    private static void addFootprintTimberBand(
            Builder b, Metadata m, Materials p, Footprint footprint, int y) {
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (insideFootprint(m, footprint, x, z)
                        && footprintBoundary(m, footprint, x, z)
                        && b.contains(pos)) {
                    b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
        }
    }

    /** A real projecting porch: deck, broad stair, paired posts, beam, brackets and lean-to roof. */
    private static void addLayeredEntryPorch(
            Builder b, Materials p, int centerX, int halfWidth, int roofY) {
        for (int x = centerX - halfWidth; x <= centerX + halfWidth; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.floor.defaultBlockState());
            // The porch keys back into several different primary roof silhouettes.  A full
            // slab course is both a substantial fascia and a safe bearing wherever a descending
            // main-roof stair lands directly above it.
            b.force(Phase.ROOF, x, roofY, -1, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, roofY, -2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            b.force(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[] {centerX - halfWidth, centerX + halfWidth}) {
            post(b, Phase.FRAME, x, 1, -1, roofY - 1, p.timber);
            b.force(Phase.FRAME, x, roofY - 1, -2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        beam(b, Phase.FRAME, centerX - halfWidth, centerX + halfWidth, roofY - 1, -1, p.timber);
    }

    /** Builds a three-wide projecting sill/lintel frame around a flush wall pane. */
    private static void addDeepWindowFrame(
            Builder b,
            Materials p,
            int windowX,
            int windowZ,
            int minY,
            int maxY,
            Direction outward) {
        int outerX = windowX + outward.getStepX();
        int outerZ = windowZ + outward.getStepZ();
        int sideX = -outward.getStepZ();
        int sideZ = outward.getStepX();
        for (int side = -1; side <= 1; side++) {
            int x = outerX + sideX * side;
            int z = outerZ + sideZ * side;
            BlockPos sill = new BlockPos(x, minY - 1, z);
            BlockPos lintel = new BlockPos(x, maxY + 1, z);
            // A projecting porch may already own an endpoint of this three-wide frame. Preserve
            // its continuous timber post rather than asking Builder.put to replace it with a
            // half slab (or silently severing the post with force).
            if (!b.contains(sill)) {
                b.put(Phase.FRAME, x, minY - 1, z, upperRoofSlab(p));
            }
            if (!b.contains(lintel)) {
                // A full-depth header keeps the frame physically connected even when the roof
                // is authored after this facade detail (as it is on the guard keep and exchange
                // hall), and matches the substantial projecting lintels in the reference build.
                b.put(Phase.FRAME, x, maxY + 1, z, doubleRoofSlab(p));
            }
        }
        for (int y = minY; y <= maxY; y++) {
            b.put(Phase.FRAME, outerX - sideX, y, outerZ - sideZ, p.timber.defaultBlockState());
            b.put(Phase.FRAME, outerX + sideX, y, outerZ + sideZ, p.timber.defaultBlockState());
        }
    }

    private static void addGableTrussX(
            Builder b, Materials p, int centerX, int z, int wallHeight, int peakY) {
        beam(b, Phase.FRAME, centerX - 3, centerX + 3, wallHeight, z, p.timber);
        post(b, Phase.FRAME, centerX, wallHeight + 1, z, peakY, p.timber);
        int trace = Math.min(3, Math.max(0, peakY - wallHeight - 1));
        for (int step = 1; step <= trace; step++) {
            int y = peakY - step;
            b.force(Phase.FRAME, centerX - step, y, z, p.timber.defaultBlockState());
            b.force(Phase.FRAME, centerX + step, y, z, p.timber.defaultBlockState());
        }
    }

    private static void addTwinRidgeGableTrussZ(
            Builder b,
            Materials p,
            int x,
            int ridgeLeftZ,
            int ridgeRightZ,
            int wallHeight,
            int peakY) {
        // The cross-gabled house has an even-width side wing, so its roof has a two-block
        // ridge. Mirroring braces from both ridge cells keeps the timber fan aligned with the
        // actual roof instead of leaving one half visually short.
        for (int z = ridgeLeftZ - 3; z <= ridgeRightZ + 3; z++) {
            b.force(Phase.FRAME, x, wallHeight, z, p.timber.defaultBlockState());
        }
        post(b, Phase.FRAME, x, wallHeight + 1, ridgeLeftZ, peakY, p.timber);
        post(b, Phase.FRAME, x, wallHeight + 1, ridgeRightZ, peakY, p.timber);
        int trace = Math.min(3, Math.max(0, peakY - wallHeight - 1));
        for (int step = 1; step <= trace; step++) {
            int y = peakY - step;
            b.force(Phase.FRAME, x, y, ridgeLeftZ - step, p.timber.defaultBlockState());
            b.force(Phase.FRAME, x, y, ridgeRightZ + step, p.timber.defaultBlockState());
        }
    }

    private static void addFrontEaveBrackets(
            Builder b, Materials p, int wallHeight, int... xCoordinates) {
        for (int x : xCoordinates) {
            b.put(Phase.FRAME, x, wallHeight, -1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private static void addCoveredWoodBay(
            Builder b, Materials p, int outsideX, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            b.force(Phase.FOUNDATION, outsideX, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.ROOF, outsideX, 3, z, doubleRoofSlab(p));
        }
        for (int z : new int[] {minZ, maxZ}) {
            post(b, Phase.FRAME, outsideX, 1, z, 2, p.timber);
        }
        b.force(Phase.FIXTURE, outsideX, 1, minZ + 1, p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        b.force(Phase.FIXTURE, outsideX, 2, minZ + 1, p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
    }

    private static void addGlasshouseRibs(Builder b, Materials p) {
        for (int z : new int[] {6, 8, 10}) {
            post(b, Phase.FRAME, 0, 1, z, 3, p.timber);
            for (int x = 0; x <= 4; x++) {
                int y = 4 + Math.max(0, x - 1) / 2;
                b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
            }
        }
        for (int x : new int[] {0, 2, 4}) {
            post(b, Phase.FRAME, x, 1, 10, 3, p.timber);
        }
        for (int z = 6; z <= 10; z++) {
            b.put(Phase.FRAME, -1, 3, z, p.roofSlab.defaultBlockState());
        }
    }

    private static void addMerchantMezzanine(Builder b, Metadata m, Materials p) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 8; z <= 12; z++) {
                BlockPos floorCell = new BlockPos(x, 4, z);
                // Preserve the full-height masonry chimney where it rises through the loft.
                if (!b.contains(floorCell)) {
                    b.put(Phase.FOUNDATION, x, 4, z, p.floor.defaultBlockState());
                }
            }
        }
        for (int y = 1; y <= 3; y++) {
            b.force(Phase.FIXTURE, 8, y, 12,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        }
        b.force(Phase.FIXTURE, 8, 4, 12, Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(new BlockPos(8, 1, 12), new BlockPos(8, 3, 12)));
        addBedAtLevel(b, m, 3, 5, 10, Direction.EAST, 5, 5, 10);
        fixture(b, m, 7, 5, 10, Blocks.BOOKSHELF, new BlockPos(6, 5, 10));
    }

    /**
     * Gives every cottage a small, legible sequence of domestic spaces rather than a bed and a
     * workstation scattered through an otherwise empty shell.  The coordinates deliberately
     * remain owned by each gold master: this is a catalog of room programs, not a procedural room
     * generator.  All props use the non-overwriting path and stay clear of authored circulation.
     */
    private static void addCottageInteriorProgram(
            Builder b, Metadata m, Materials p, String program) {
        switch (program) {
            case "hearth" -> {
                addCottageRafterX(b, p, 1, 7, 3, 2);
                addCottagePantry(b, m, p, 7, 5);
                addCottageSettledBench(b, m, p, 4, 6, Direction.NORTH);
            }
            case "glasshouse" -> {
                addCottageRafterX(b, p, 2, 10, 3, 5);
                addCottagePottingRun(b, m, p, 1, 3, 9);
                addCottagePantry(b, m, p, 9, 5);
            }
            case "bay" -> {
                addCottageRafterX(b, p, 1, 7, 3, 4);
                addCottagePantry(b, m, p, 7, 6);
                addCottageSettledBench(b, m, p, 6, 2, Direction.WEST);
            }
            case "courtyard" -> {
                addCottageRafterX(b, p, 1, 13, 4, 6);
                addCottagePantry(b, m, p, 2, 11);
                addCottagePottingRun(b, m, p, 11, 13, 11);
                addCottageSettledBench(b, m, p, 11, 7, Direction.WEST);
            }
            case "longhouse" -> {
                addCottageRafterX(b, p, 1, 9, 4, 5);
                addCottagePantry(b, m, p, 8, 7);
                addCottagePottingRun(b, m, p, 12, 13, 4);
                addCottageSettledBench(b, m, p, 9, 6, Direction.WEST);
            }
            case "orchardstead" -> {
                // Distinct food-processing, preserving and sleeping ranges answer the landmark
                // plan's scale; the former version furnished only the north-east corner.
                addCottageRafterX(b, p, 1, 17, 4, 2);
                addCottageRafterX(b, p, 1, 4, 4, 8);
                addCottageRafterX(b, p, 14, 17, 4, 8);
                addCottagePottingRun(b, m, p, 3, 4, 11);
                addCottagePottingRun(b, m, p, 14, 15, 11);
                addCottagePantry(b, m, p, 4, 8);
                addCottagePantry(b, m, p, 14, 8);
                addCottageSettledBench(b, m, p, 7, 4, Direction.EAST);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown cottage interior program " + program);
        }
    }

    private static void addCottageRafterX(
            Builder b, Materials p, int minX, int maxX, int y, int z) {
        BlockState rafter = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= maxX; x++) {
            b.putIfFree(Phase.FRAME, x, y, z, rafter);
        }
    }

    private static void addCottagePantry(
            Builder b, Metadata m, Materials p, int x, int z) {
        if (!cottageFloorPropClear(b, m, x, z)) {
            return;
        }
        b.put(Phase.FIXTURE, x, 1, z, Blocks.BARREL.defaultBlockState());
        b.putIfFree(Phase.DECOR, x, 2, z, p.roofSlab.defaultBlockState());
        b.putIfFree(Phase.DECOR, x, 3, z, Blocks.FLOWER_POT.defaultBlockState());
    }

    private static void addCottagePottingRun(
            Builder b, Metadata m, Materials p, int minX, int maxX, int z) {
        for (int x = minX; x <= maxX; x++) {
            if (!cottageFloorPropClear(b, m, x, z)) {
                continue;
            }
            Block block = x == minX ? Blocks.COMPOSTER : Blocks.BARREL;
            b.put(Phase.FIXTURE, x, 1, z, block.defaultBlockState());
            b.putIfFree(Phase.DECOR, x, 2, z, p.roofSlab.defaultBlockState());
        }
    }

    private static void addCottageSettledBench(
            Builder b, Metadata m, Materials p, int x, int z, Direction facing) {
        if (!cottageFloorPropClear(b, m, x, z)) {
            return;
        }
        b.put(Phase.FIXTURE, x, 1, z, p.roofStairs.defaultBlockState()
                .setValue(StairBlock.FACING, facing));
        int sideX = x + (facing.getAxis() == Direction.Axis.Z ? 1 : 0);
        int sideZ = z + (facing.getAxis() == Direction.Axis.X ? 1 : 0);
        if (cottageFloorPropClear(b, m, sideX, sideZ)) {
            b.put(Phase.FIXTURE, sideX, 1, sideZ, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, facing));
        }
    }

    private static boolean cottageFloorPropClear(
            Builder b, Metadata m, int x, int z) {
        BlockPos feet = new BlockPos(x, 1, z);
        return b.contains(feet.below())
                && !b.contains(feet)
                && !b.contains(feet.above())
                && !m.reservedAir.contains(feet)
                && !m.accessTargets.contains(feet)
                && floorPropPreservesInteractionRoutes(b, m, feet);
    }

    /** Adds family-room, study and upper-landing purpose to every house master. */
    private static void addHouseInteriorProgram(
            Builder b, Metadata m, Materials p, String program) {
        switch (program) {
            case "cross" -> {
                addHouseCeilingTieX(b, p, 1, 13, 4, 6);
                addHouseWritingNook(b, m, p, 3, 9, Direction.EAST);
                addHouseCabinetPair(b, m, p, 11, 9, Direction.WEST);
            }
            case "mansard" -> {
                addHouseCeilingTieX(b, p, 2, 8, 4, 6);
                addHouseWritingNook(b, m, p, 7, 6, Direction.WEST);
                addHouseCabinetPair(b, m, p, 2, 7, Direction.EAST);
            }
            case "hall" -> {
                addHouseCeilingTieX(b, p, 1, 9, 3, 6);
                addHouseWritingNook(b, m, p, 6, 8, Direction.WEST);
                addHouseCabinetPair(b, m, p, 6, 9, Direction.NORTH);
            }
            case "arcade" -> {
                // The long transverse range is now a sequence of library, family table and
                // workshop bays, divided overhead without blocking the through hall.
                addHouseCeilingTieX(b, p, 1, 15, 4, 6);
                addHouseCeilingTieX(b, p, 1, 15, 4, 10);
                addHouseWritingNook(b, m, p, 3, 10, Direction.EAST);
                addHouseWritingNook(b, m, p, 13, 10, Direction.WEST);
                addHouseCabinetPair(b, m, p, 4, 12, Direction.EAST);
            }
            case "splitwing" -> {
                addHouseCeilingTieX(b, p, 1, 8, 4, 5);
                addHouseCeilingTieX(b, p, 6, 13, 4, 9);
                addHouseWritingNook(b, m, p, 11, 9, Direction.WEST);
                addHouseCabinetPair(b, m, p, 3, 5, Direction.EAST);
            }
            case "towercourt" -> {
                addHouseCeilingTieX(b, p, 1, 19, 4, 5);
                addHouseCeilingTieX(b, p, 1, 5, 4, 10);
                addHouseCeilingTieX(b, p, 15, 19, 4, 10);
                addHouseWritingNook(b, m, p, 4, 16, Direction.EAST);
                addHouseWritingNook(b, m, p, 16, 12, Direction.WEST);
                addHouseTowerStudy(b, m, p);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown house interior program " + program);
        }
    }

    private static void addHouseCeilingTieX(
            Builder b, Materials p, int minX, int maxX, int y, int z) {
        BlockState tie = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= maxX; x++) {
            b.putIfFree(Phase.FRAME, x, y, z, tie);
        }
    }

    private static void addHouseWritingNook(
            Builder b, Metadata m, Materials p, int x, int z, Direction chairFacing) {
        if (houseFloorPropClear(b, m, x, z)) {
            b.put(Phase.FIXTURE, x, 1, z, Blocks.LECTERN.defaultBlockState());
        }
        int chairX = x - chairFacing.getStepX();
        int chairZ = z - chairFacing.getStepZ();
        if (houseFloorPropClear(b, m, chairX, chairZ)) {
            b.put(Phase.FIXTURE, chairX, 1, chairZ, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, chairFacing));
        }
        int shelfX = x + (chairFacing.getAxis() == Direction.Axis.Z ? 1 : 0);
        int shelfZ = z + (chairFacing.getAxis() == Direction.Axis.X ? 1 : 0);
        if (houseFloorPropClear(b, m, shelfX, shelfZ)) {
            b.put(Phase.FIXTURE, shelfX, 1, shelfZ, Blocks.BOOKSHELF.defaultBlockState());
            b.putIfFree(Phase.DECOR, shelfX, 2, shelfZ, p.roofSlab.defaultBlockState());
        }
    }

    private static void addHouseCabinetPair(
            Builder b, Metadata m, Materials p, int x, int z, Direction run) {
        for (int offset = 0; offset < 2; offset++) {
            int cabinetX = x + run.getStepX() * offset;
            int cabinetZ = z + run.getStepZ() * offset;
            if (!houseFloorPropClear(b, m, cabinetX, cabinetZ)) {
                continue;
            }
            b.put(Phase.FIXTURE, cabinetX, 1, cabinetZ,
                    (offset == 0 ? Blocks.BARREL : Blocks.BOOKSHELF).defaultBlockState());
            b.putIfFree(Phase.DECOR, cabinetX, 2, cabinetZ,
                    p.roofSlab.defaultBlockState());
        }
    }

    private static void addHouseTowerStudy(Builder b, Metadata m, Materials p) {
        // The tower's existing backed ladder and hatch remain the circulation device; furnishing
        // the landing as a map-and-ledger room makes the upper volume a destination rather than
        // an empty decorative box.
        if (houseFloorPropClearAtLevel(b, m, 1, 8, 13)) {
            b.put(Phase.FIXTURE, 1, 8, 13, Blocks.BOOKSHELF.defaultBlockState());
        }
        if (houseFloorPropClearAtLevel(b, m, 4, 8, 13)) {
            b.put(Phase.FIXTURE, 4, 8, 13, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        }
        if (houseFloorPropClearAtLevel(b, m, 4, 8, 16)) {
            b.put(Phase.FIXTURE, 4, 8, 16, Blocks.LECTERN.defaultBlockState());
        }
        if (houseFloorPropClearAtLevel(b, m, 3, 8, 15)) {
            b.put(Phase.FIXTURE, 3, 8, 15, p.fence.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, 3, 9, 15,
                    Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
        m.interiorSamples.add(new BlockPos(3, 8, 14));
    }

    private static boolean houseFloorPropClear(
            Builder b, Metadata m, int x, int z) {
        return houseFloorPropClearAtLevel(b, m, x, 1, z);
    }

    private static boolean houseFloorPropClearAtLevel(
            Builder b, Metadata m, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return b.contains(feet.below())
                && !b.contains(feet)
                && !b.contains(feet.above())
                && !m.reservedAir.contains(feet)
                && !m.accessTargets.contains(feet)
                && floorPropPreservesInteractionRoutes(b, m, feet);
    }

    /** Adds a visible service spine, grouped seating and lodging/coach support to every inn. */
    private static void addInnInteriorProgram(
            Builder b, Metadata m, Materials p, String program) {
        switch (program) {
            case "gallery" -> {
                addInnCeilingTieX(b, p, 1, 15, 4, 8);
                addInnServiceCounter(b, m, p, 7, 11, 10);
                addInnDiningSet(b, m, p, 10, 7);
                addInnGuestChest(b, m, p, 3, 9);
            }
            case "wayfarer" -> {
                addInnCeilingTieX(b, p, 1, 11, 4, 6);
                addInnServiceCounter(b, m, p, 7, 10, 8);
                addInnDiningSet(b, m, p, 5, 7);
                addInnGuestChest(b, m, p, 3, 4);
            }
            case "coachhouse" -> {
                addInnCeilingTieX(b, p, 1, 19, 4, 8);
                addInnServiceCounter(b, m, p, 8, 13, 14);
                addInnDiningSet(b, m, p, 15, 10);
                addInnTackBay(b, m, p, 3, 5, Direction.EAST);
                addInnTackBay(b, m, p, 17, 5, Direction.WEST);
                addInnGuestChest(b, m, p, 3, 12);
                addInnGuestChest(b, m, p, 17, 12);
            }
            case "tavern" -> {
                addInnCeilingTieX(b, p, 4, 10, 4, 6);
                addInnServiceCounter(b, m, p, 4, 10, 10);
                addInnDiningSet(b, m, p, 7, 4);
                addInnGuestChest(b, m, p, 3, 10);
                addInnGuestChest(b, m, p, 11, 10);
            }
            case "courtyard" -> {
                addInnCeilingTieX(b, p, 1, 7, 4, 10);
                addInnCeilingTieX(b, p, 18, 23, 4, 12);
                addInnCeilingTieX(b, p, 6, 23, 4, 17);
                addInnServiceCounter(b, m, p, 7, 10, 18);
                addInnDiningSet(b, m, p, 5, 16);
                addInnDiningSet(b, m, p, 19, 16);
                addInnTackBay(b, m, p, 7, 11, Direction.EAST);
                addInnGuestChest(b, m, p, 3, 16);
                addInnGuestChest(b, m, p, 21, 16);
                addInnLanternRoom(b, m, p);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown inn interior program " + program);
        }
    }

    private static void addInnCeilingTieX(
            Builder b, Materials p, int minX, int maxX, int y, int z) {
        BlockState tie = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= maxX; x++) {
            b.putIfFree(Phase.FRAME, x, y, z, tie);
        }
    }

    private static void addInnServiceCounter(
            Builder b, Metadata m, Materials p, int minX, int maxX, int z) {
        for (int x = minX; x <= maxX; x++) {
            if (!innFloorPropClear(b, m, x, 1, z)) {
                continue;
            }
            BlockState counter = p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP);
            b.put(Phase.FIXTURE, x, 1, z, counter);
        }
        int barrelZ = z + 1;
        for (int x : new int[] {minX, maxX}) {
            if (innFloorPropClear(b, m, x, 1, barrelZ)) {
                b.put(Phase.FIXTURE, x, 1, barrelZ, Blocks.BARREL.defaultBlockState());
                b.putIfFree(Phase.DECOR, x, 2, barrelZ,
                        p.roofSlab.defaultBlockState());
            }
        }
    }

    private static void addInnDiningSet(
            Builder b, Metadata m, Materials p, int centerX, int centerZ) {
        if (innFloorPropClear(b, m, centerX, 1, centerZ)) {
            b.put(Phase.FIXTURE, centerX, 1, centerZ, p.fence.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, centerX, 2, centerZ,
                    Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int chairX = centerX + side.getStepX();
            int chairZ = centerZ + side.getStepZ();
            if (innFloorPropClear(b, m, chairX, 1, chairZ)) {
                b.put(Phase.FIXTURE, chairX, 1, chairZ,
                        p.roofStairs.defaultBlockState()
                                .setValue(StairBlock.FACING, side.getOpposite()));
            }
        }
    }

    private static void addInnTackBay(
            Builder b, Metadata m, Materials p, int x, int z, Direction facing) {
        if (innFloorPropClear(b, m, x, 1, z)) {
            b.put(Phase.FIXTURE, x, 1, z, Blocks.HAY_BLOCK.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, facing.getAxis()));
            b.putIfFree(Phase.DECOR, x, 2, z, Blocks.RAIL.defaultBlockState());
        }
        int supplyX = x - facing.getStepX();
        int supplyZ = z - facing.getStepZ();
        if (innFloorPropClear(b, m, supplyX, 1, supplyZ)) {
            b.put(Phase.FIXTURE, supplyX, 1, supplyZ,
                    Blocks.CAULDRON.defaultBlockState());
        }
    }

    private static void addInnGuestChest(
            Builder b, Metadata m, Materials p, int x, int z) {
        if (!innFloorPropClear(b, m, x, 1, z)) {
            return;
        }
        b.put(Phase.FIXTURE, x, 1, z, Blocks.BARREL.defaultBlockState());
        b.putIfFree(Phase.DECOR, x, 2, z, p.roofSlab.defaultBlockState());
    }

    private static void addInnLanternRoom(Builder b, Metadata m, Materials p) {
        if (innFloorPropClear(b, m, 11, 10, 16)) {
            b.put(Phase.FIXTURE, 11, 10, 16, Blocks.BOOKSHELF.defaultBlockState());
        }
        if (innFloorPropClear(b, m, 13, 10, 16)) {
            b.put(Phase.FIXTURE, 13, 10, 16, Blocks.LECTERN.defaultBlockState());
        }
        if (innFloorPropClear(b, m, 12, 10, 18)) {
            b.put(Phase.FIXTURE, 12, 10, 18, p.fence.defaultBlockState());
            b.putIfFree(Phase.FIXTURE, 12, 11, 18,
                    Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
        m.interiorSamples.add(new BlockPos(12, 10, 17));
    }

    private static boolean innFloorPropClear(
            Builder b, Metadata m, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return b.contains(feet.below())
                && !b.contains(feet)
                && !b.contains(feet.above())
                && !m.reservedAir.contains(feet)
                && !m.accessTargets.contains(feet)
                && floorPropPreservesInteractionRoutes(b, m, feet);
    }

    /** Authored vendor programs keep every market legible beyond its shared counter geometry. */
    private static void addMarketInteriorProgram(
            Builder b, Metadata m, Materials p, String program) {
        switch (program) {
            case "rotunda" -> {
                addMarketVendorDisplay(b, m, p, 5, 4, Blocks.HAY_BLOCK,
                        Blocks.COMPOSTER);
                addMarketVendorDisplay(b, m, p, 11, 4, Blocks.WOOL.green(),
                        Blocks.LOOM);
                addMarketVendorDisplay(b, m, p, 5, 12, Blocks.TARGET,
                        Blocks.FLETCHING_TABLE);
                addMarketVendorDisplay(b, m, p, 11, 12, p.foundation,
                        Blocks.STONECUTTER);
                addMarketPennantPost(b, m, p, 1, 8, Blocks.WOOL.yellow());
                addMarketPennantPost(b, m, p, 15, 8, Blocks.WOOL.blue());
            }
            case "guildcourt" -> {
                addMarketVendorDisplay(b, m, p, 7, 4, Blocks.TARGET,
                        Blocks.FLETCHING_TABLE);
                addMarketVendorDisplay(b, m, p, 13, 4, Blocks.WOOL.green(),
                        Blocks.LOOM);
                addMarketVendorDisplay(b, m, p, 7, 14, p.foundation,
                        Blocks.STONECUTTER);
                addMarketVendorDisplay(b, m, p, 13, 14, Blocks.RAW_IRON_BLOCK,
                        Blocks.GRINDSTONE);
                addMarketGuildDesk(b, m, p, 7, 11);
                addMarketGuildDesk(b, m, p, 13, 7);
            }
            case "crossroads" -> {
                addMarketVendorDisplay(b, m, p, 3, 3, Blocks.HAY_BLOCK,
                        Blocks.COMPOSTER);
                addMarketVendorDisplay(b, m, p, 11, 3, Blocks.WOOL.red(),
                        Blocks.LOOM);
                addMarketVendorDisplay(b, m, p, 3, 9, Blocks.TARGET,
                        Blocks.FLETCHING_TABLE);
                addMarketVendorDisplay(b, m, p, 11, 9, p.foundation,
                        Blocks.STONECUTTER);
            }
            case "lane" -> {
                for (int z : new int[] {3, 7, 10}) {
                    addMarketVendorDisplay(b, m, p, 4, z,
                            z == 3 ? Blocks.HAY_BLOCK
                                    : z == 7 ? Blocks.WOOL.green() : Blocks.TARGET,
                            z == 3 ? Blocks.COMPOSTER
                                    : z == 7 ? Blocks.LOOM : Blocks.FLETCHING_TABLE);
                    addMarketVendorDisplay(b, m, p, 12, z,
                            z == 3 ? p.foundation
                                    : z == 7 ? Blocks.RAW_IRON_BLOCK : Blocks.TERRACOTTA,
                            z == 3 ? Blocks.STONECUTTER
                                    : z == 7 ? Blocks.GRINDSTONE : Blocks.CRAFTING_TABLE);
                }
                addMarketPennantPost(b, m, p, 1, 5, Blocks.WOOL.yellow());
                addMarketPennantPost(b, m, p, 15, 9, Blocks.WOOL.red());
            }
            case "bazaar" -> {
                addMarketVendorDisplay(b, m, p, 5, 5, Blocks.HAY_BLOCK,
                        Blocks.COMPOSTER);
                addMarketVendorDisplay(b, m, p, 19, 5, Blocks.WOOL.red(),
                        Blocks.LOOM);
                addMarketVendorDisplay(b, m, p, 5, 17, Blocks.TARGET,
                        Blocks.FLETCHING_TABLE);
                addMarketVendorDisplay(b, m, p, 19, 17, p.foundation,
                        Blocks.STONECUTTER);
                addMarketVendorDisplay(b, m, p, 7, 11, Blocks.TERRACOTTA,
                        Blocks.CRAFTING_TABLE);
                addMarketVendorDisplay(b, m, p, 17, 11, Blocks.RAW_IRON_BLOCK,
                        Blocks.GRINDSTONE);
                addMarketPennantPost(b, m, p, 2, 11, Blocks.WOOL.green());
                addMarketPennantPost(b, m, p, 22, 11, Blocks.WOOL.blue());
            }
            default -> throw new IllegalArgumentException(
                    "Unknown market interior program " + program);
        }
    }

    private static void addMarketVendorDisplay(
            Builder b,
            Metadata m,
            Materials p,
            int x,
            int z,
            Block wares,
            Block trade) {
        if (marketFloorPropClear(b, m, x, 1, z)) {
            b.put(Phase.FIXTURE, x, 1, z, upperRoofSlab(p));
            b.putIfFree(Phase.DECOR, x, 2, z, wares.defaultBlockState());
        }
        int tradeX = x < m.width / 2 ? x - 1 : x + 1;
        if (marketFloorPropClear(b, m, tradeX, 1, z)) {
            b.put(Phase.FIXTURE, tradeX, 1, z, trade.defaultBlockState());
        }
    }

    private static void addMarketPennantPost(
            Builder b, Metadata m, Materials p, int x, int z, Block color) {
        if (!marketFloorPropClear(b, m, x, 1, z)) {
            return;
        }
        b.put(Phase.FRAME, x, 1, z, p.fence.defaultBlockState());
        b.putIfFree(Phase.FRAME, x, 2, z, p.fence.defaultBlockState());
        b.putIfFree(Phase.DECOR, x, 3, z, color.defaultBlockState());
        // A full timber masthead is intentional: lane pennants sit directly beneath the arcade
        // roof at y=5, where a bottom-slab finial leaves the roof stair floating by half a block.
        b.putIfFree(Phase.FRAME, x, 4, z, p.timber.defaultBlockState());
    }

    private static void addMarketGuildDesk(
            Builder b, Metadata m, Materials p, int x, int z) {
        if (marketFloorPropClear(b, m, x, 1, z)) {
            b.put(Phase.FIXTURE, x, 1, z, Blocks.LECTERN.defaultBlockState());
        }
        if (marketFloorPropClear(b, m, x + 1, 1, z)) {
            b.put(Phase.FIXTURE, x + 1, 1, z,
                    Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            b.putIfFree(Phase.DECOR, x + 1, 2, z,
                    Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
    }

    private static boolean marketFloorPropClear(
            Builder b, Metadata m, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return b.contains(feet.below())
                && !b.contains(feet)
                && !b.contains(feet.above())
                && !m.reservedAir.contains(feet)
                && !m.accessTargets.contains(feet)
                && floorPropPreservesInteractionRoutes(b, m, feet);
    }

    /** Role-readable guard rooms: briefing, armory, barracks and upper patrol stations. */
    private static void addGuardInteriorProgram(
            Builder b, Metadata m, Materials p, String program) {
        switch (program) {
            case "watchtower" -> {
                addGuardMapTable(b, m, p, 7, 4, Direction.WEST);
                addGuardArmoryRack(b, m, p, 2, 6, Direction.EAST);
                addGuardPatrolStation(b, m, p, 3, 13, 5, Direction.EAST);
                addGuardPatrolStation(b, m, p, 7, 13, 5, Direction.WEST);
            }
            case "blockhouse" -> {
                addGuardMapTable(b, m, p, 6, 4, Direction.WEST);
                addGuardArmoryRack(b, m, p, 1, 6, Direction.EAST);
                addGuardBarracksBench(b, m, p, 6, 7, Direction.WEST);
            }
            case "bastion" -> {
                addGuardMapTable(b, m, p, 10, 10, Direction.WEST);
                addGuardArmoryRack(b, m, p, 11, 5, Direction.WEST);
                addGuardArmoryRack(b, m, p, 3, 5, Direction.EAST);
                addGuardBarracksBench(b, m, p, 5, 8, Direction.SOUTH);
                addGuardBarracksBench(b, m, p, 9, 8, Direction.SOUTH);
            }
            case "citadel" -> {
                addGuardMapTable(b, m, p, 7, 9, Direction.EAST);
                addGuardMapTable(b, m, p, 13, 9, Direction.WEST);
                addGuardArmoryRack(b, m, p, 3, 12, Direction.EAST);
                addGuardArmoryRack(b, m, p, 17, 12, Direction.WEST);
                addGuardBarracksBench(b, m, p, 6, 16, Direction.NORTH);
                addGuardBarracksBench(b, m, p, 14, 16, Direction.NORTH);
                addGuardPatrolStation(b, m, p, 2, 8, 2, Direction.EAST);
                addGuardPatrolStation(b, m, p, 18, 8, 2, Direction.WEST);
                addGuardPatrolStation(b, m, p, 2, 8, 16, Direction.EAST);
                addGuardPatrolStation(b, m, p, 18, 8, 16, Direction.WEST);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown guard interior program " + program);
        }
    }

    private static void addGuardMapTable(
            Builder b, Metadata m, Materials p, int x, int z, Direction facing) {
        if (guardFloorPropClear(b, m, x, 1, z)) {
            b.put(Phase.FIXTURE, x, 1, z, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        }
        int lecternX = x + facing.getStepX();
        int lecternZ = z + facing.getStepZ();
        if (guardFloorPropClear(b, m, lecternX, 1, lecternZ)) {
            b.put(Phase.FIXTURE, lecternX, 1, lecternZ,
                    Blocks.LECTERN.defaultBlockState());
        }
        int chairX = x - facing.getStepX();
        int chairZ = z - facing.getStepZ();
        if (guardFloorPropClear(b, m, chairX, 1, chairZ)) {
            b.put(Phase.FIXTURE, chairX, 1, chairZ, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, facing));
        }
    }

    private static void addGuardArmoryRack(
            Builder b, Metadata m, Materials p, int x, int z, Direction facing) {
        if (!guardFloorPropClear(b, m, x, 1, z)) {
            return;
        }
        b.put(Phase.FRAME, x, 1, z, Blocks.IRON_BARS.defaultBlockState());
        b.putIfFree(Phase.FRAME, x, 2, z, p.roofSlab.defaultBlockState());
        int toolX = x - facing.getStepX();
        int toolZ = z - facing.getStepZ();
        if (guardFloorPropClear(b, m, toolX, 1, toolZ)) {
            b.put(Phase.FIXTURE, toolX, 1, toolZ,
                    Blocks.GRINDSTONE.defaultBlockState());
        }
    }

    private static void addGuardBarracksBench(
            Builder b, Metadata m, Materials p, int x, int z, Direction facing) {
        for (int offset = 0; offset < 2; offset++) {
            int benchX = x + (facing.getAxis() == Direction.Axis.Z ? offset : 0);
            int benchZ = z + (facing.getAxis() == Direction.Axis.X ? offset : 0);
            if (guardFloorPropClear(b, m, benchX, 1, benchZ)) {
                b.put(Phase.FIXTURE, benchX, 1, benchZ,
                        p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, facing));
            }
        }
    }

    private static void addGuardPatrolStation(
            Builder b,
            Metadata m,
            Materials p,
            int x,
            int y,
            int z,
            Direction facing) {
        if (guardFloorPropClear(b, m, x, y, z)) {
            b.put(Phase.FIXTURE, x, y, z, Blocks.TARGET.defaultBlockState());
        }
        int deskX = x - facing.getStepX();
        int deskZ = z - facing.getStepZ();
        if (guardFloorPropClear(b, m, deskX, y, deskZ)) {
            b.put(Phase.FIXTURE, deskX, y, deskZ, Blocks.LECTERN.defaultBlockState());
        }
    }

    private static boolean guardFloorPropClear(
            Builder b, Metadata m, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return b.contains(feet.below())
                && !b.contains(feet)
                && !b.contains(feet.above())
                && !m.reservedAir.contains(feet)
                && !m.accessTargets.contains(feet)
                && floorPropPreservesInteractionRoutes(b, m, feet);
    }

    /** Distinct public, teller, ledger-office and trading-floor programs for exchange masters. */
    private static void addExchangeInteriorProgram(
            Builder b, Metadata m, Materials p, String program) {
        switch (program) {
            case "civic_hall" -> {
                addExchangeLedgerCarrel(b, m, p, 3, 6, Direction.EAST);
                addExchangeLedgerCarrel(b, m, p, 15, 6, Direction.WEST);
                addExchangeCountingTable(b, m, p, 5, 8);
                addExchangeCountingTable(b, m, p, 13, 8);
            }
            case "branch" -> {
                addExchangeLedgerCarrel(b, m, p, 3, 5, Direction.EAST);
                addExchangeLedgerCarrel(b, m, p, 11, 5, Direction.WEST);
                addExchangeCountingTable(b, m, p, 4, 8);
                addExchangeCountingTable(b, m, p, 10, 8);
            }
            case "countinghouse" -> {
                addExchangeLedgerCarrel(b, m, p, 4, 5, Direction.EAST);
                addExchangeLedgerCarrel(b, m, p, 18, 5, Direction.WEST);
                addExchangeLedgerCarrel(b, m, p, 4, 12, Direction.EAST);
                addExchangeLedgerCarrel(b, m, p, 18, 12, Direction.WEST);
                addExchangeCountingTable(b, m, p, 8, 8);
                addExchangeCountingTable(b, m, p, 14, 8);
                addExchangeOfficeScreen(b, m, p, 7, 13, Direction.EAST);
                addExchangeOfficeScreen(b, m, p, 15, 13, Direction.WEST);
            }
            case "bourse" -> {
                addExchangeTradingPod(b, m, p, 7, 8);
                addExchangeTradingPod(b, m, p, 19, 8);
                addExchangeTradingPod(b, m, p, 7, 14);
                addExchangeTradingPod(b, m, p, 19, 14);
                addExchangeLedgerCarrel(b, m, p, 3, 5, Direction.EAST);
                addExchangeLedgerCarrel(b, m, p, 23, 5, Direction.WEST);
                addExchangeOfficeScreen(b, m, p, 9, 18, Direction.EAST);
                addExchangeOfficeScreen(b, m, p, 17, 18, Direction.WEST);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown exchange interior program " + program);
        }
    }

    private static void addExchangeLedgerCarrel(
            Builder b, Metadata m, Materials p, int x, int z, Direction facing) {
        if (exchangeFloorPropClear(b, m, x, 1, z)) {
            b.put(Phase.FIXTURE, x, 1, z, Blocks.LECTERN.defaultBlockState());
        }
        int shelfX = x + (facing.getAxis() == Direction.Axis.Z ? 1 : 0);
        int shelfZ = z + (facing.getAxis() == Direction.Axis.X ? 1 : 0);
        if (exchangeFloorPropClear(b, m, shelfX, 1, shelfZ)) {
            b.put(Phase.FIXTURE, shelfX, 1, shelfZ, Blocks.BOOKSHELF.defaultBlockState());
            b.putIfFree(Phase.DECOR, shelfX, 2, shelfZ,
                    p.roofSlab.defaultBlockState());
        }
        int chairX = x - facing.getStepX();
        int chairZ = z - facing.getStepZ();
        if (exchangeFloorPropClear(b, m, chairX, 1, chairZ)) {
            b.put(Phase.FIXTURE, chairX, 1, chairZ,
                    p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, facing));
        }
    }

    private static void addExchangeCountingTable(
            Builder b, Metadata m, Materials p, int x, int z) {
        if (!exchangeFloorPropClear(b, m, x, 1, z)) {
            return;
        }
        b.put(Phase.FIXTURE, x, 1, z, p.fence.defaultBlockState());
        b.putIfFree(Phase.FIXTURE, x, 2, z,
                Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        for (Direction side : new Direction[] {Direction.EAST, Direction.WEST}) {
            int chairX = x + side.getStepX();
            int chairZ = z + side.getStepZ();
            if (exchangeFloorPropClear(b, m, chairX, 1, chairZ)) {
                b.put(Phase.FIXTURE, chairX, 1, chairZ,
                        p.roofStairs.defaultBlockState()
                                .setValue(StairBlock.FACING, side.getOpposite()));
            }
        }
    }

    private static void addExchangeOfficeScreen(
            Builder b, Metadata m, Materials p, int x, int z, Direction opening) {
        for (int offset = -1; offset <= 1; offset++) {
            int screenX = x + (opening.getAxis() == Direction.Axis.Z ? offset : 0);
            int screenZ = z + (opening.getAxis() == Direction.Axis.X ? offset : 0);
            if (!exchangeFloorPropClear(b, m, screenX, 1, screenZ)) {
                continue;
            }
            b.put(Phase.FRAME, screenX, 1, screenZ,
                    offset == 0 ? Blocks.IRON_BARS.defaultBlockState()
                            : p.fence.defaultBlockState());
            b.putIfFree(Phase.FRAME, screenX, 2, screenZ,
                    p.roofSlab.defaultBlockState());
        }
    }

    private static void addExchangeTradingPod(
            Builder b, Metadata m, Materials p, int centerX, int centerZ) {
        if (exchangeFloorPropClear(b, m, centerX, 1, centerZ)) {
            b.put(Phase.FIXTURE, centerX, 1, centerZ,
                    Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            b.putIfFree(Phase.DECOR, centerX, 2, centerZ,
                    Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int lecternX = centerX + side.getStepX();
            int lecternZ = centerZ + side.getStepZ();
            if (exchangeFloorPropClear(b, m, lecternX, 1, lecternZ)) {
                b.put(Phase.FIXTURE, lecternX, 1, lecternZ,
                        Blocks.LECTERN.defaultBlockState());
            }
        }
    }

    private static boolean exchangeFloorPropClear(
            Builder b, Metadata m, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return b.contains(feet.below())
                && !b.contains(feet)
                && !b.contains(feet.above())
                && !m.reservedAir.contains(feet)
                && !m.accessTargets.contains(feet)
                && floorPropPreservesInteractionRoutes(b, m, feet);
    }

    /**
     * Proves a proposed solid floor prop cannot turn a narrow room into a dead end. Merely
     * avoiding the exact access tile is insufficient: a barrel two steps away can still close the
     * only route around a bed, smoker, desk, or ladder. Running the same entrance flood used by
     * final blueprint admission after each candidate keeps authored furnishing decorative rather
     * than functional obstruction.
     */
    private static boolean floorPropPreservesInteractionRoutes(
            Builder builder, Metadata metadata, BlockPos proposed) {
        return solidPropsPreserveInteractionRoutes(builder, metadata, List.of(proposed));
    }

    private static boolean solidPropsPreserveInteractionRoutes(
            Builder builder, Metadata metadata, Collection<BlockPos> proposed) {
        Map<BlockPos, BlockState> occupied = stateMap(cellMap(builder.values()));
        return solidPropsPreserveInteractionRoutes(occupied, metadata, proposed);
    }

    private static boolean solidPropsPreserveInteractionRoutes(
            Builder stage,
            Builder base,
            Builder stageOne,
            Metadata metadata,
            Collection<BlockPos> proposed) {
        Map<BlockPos, BlockState> occupied = stateMap(cellMap(base.values()));
        occupied.putAll(stateMap(cellMap(stageOne.values())));
        occupied.putAll(stateMap(cellMap(stage.values())));
        return solidPropsPreserveInteractionRoutes(occupied, metadata, proposed);
    }

    private static boolean solidPropsPreserveInteractionRoutes(
            Map<BlockPos, BlockState> occupied,
            Metadata metadata,
            Collection<BlockPos> proposed) {
        for (BlockPos position : proposed) {
            if (position.equals(metadata.entranceInside)
                    || metadata.reservedAir.contains(position)) {
                return false;
            }
            occupied.put(position, Blocks.STONE.defaultBlockState());
        }
        Set<BlockPos> reachable = interiorCirculation(
                metadata.width,
                metadata.depth,
                occupied,
                metadata.entranceInside,
                metadata.verticalAccess);
        if (!reachable.containsAll(metadata.accessTargets)) {
            return false;
        }
        for (VerticalAccess access : metadata.verticalAccess) {
            if (!walkable(occupied, access.to.above(2))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fails before the pocket normalizer can conceal the original obstruction with wall blocks.
     * This is also a permanent admission boundary: architectural dressing must never depend on
     * the normalizer to erase a bed or workstation route.
     */
    private static void requireInteractionRoutesBeforePocketSealing(
            Builder builder, Metadata metadata, String templateId) {
        Map<BlockPos, BlockState> occupied = stateMap(cellMap(builder.values()));
        Set<BlockPos> reachable = interiorCirculation(
                metadata.width,
                metadata.depth,
                occupied,
                metadata.entranceInside,
                metadata.verticalAccess);
        for (BlockPos target : metadata.accessTargets) {
            if (!reachable.contains(target)) {
                throw new IllegalStateException(
                        "Architectural detail obstructs an interaction route at " + target
                                + " in " + templateId
                                + navigationFailureContext(occupied, reachable, target));
            }
        }
    }

    private static void addBedAtLevel(
            Builder b,
            Metadata m,
            int footX,
            int y,
            int z,
            Direction facing,
            int accessX,
            int accessY,
            int accessZ) {
        int headX = footX + facing.getStepX();
        int headZ = z + facing.getStepZ();
        BlockState foot = Blocks.BED.white().defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        b.put(Phase.FIXTURE, footX, y, z, foot);
        b.put(Phase.FIXTURE, headX, y, headZ, foot.setValue(BedBlock.PART, BedPart.HEAD));
        BlockPos access = new BlockPos(accessX, accessY, accessZ);
        if (!adjacent(access, new BlockPos(footX, y, z), new BlockPos(headX, y, headZ))) {
            throw new IllegalStateException("Upper bed access tile is not adjacent to its feature");
        }
        m.accessTargets.add(access);
    }

    private static BlockState upperRoofSlab(Materials p) {
        return p.roofSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static void addRectGableRoof(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight,
            Direction.Axis slopeAxis) {
        int span = slopeAxis == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int layers = (span + 1) / 2;
        int roofY = wallHeight + 1;
        for (int layer = 0; layer < layers; layer++) {
            int first = (slopeAxis == Direction.Axis.X ? minX : minZ) - 1 + layer;
            int second = (slopeAxis == Direction.Axis.X ? maxX : maxZ) + 1 - layer;
            int y = roofY + layer;
            int runMin = (slopeAxis == Direction.Axis.X ? minZ : minX) - 1;
            int runMax = (slopeAxis == Direction.Axis.X ? maxZ : maxX) + 1;
            for (int along = runMin; along <= runMax; along++) {
                if (slopeAxis == Direction.Axis.X) {
                    b.force(Phase.ROOF, first, y, along, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST));
                    b.force(Phase.ROOF, second, y, along, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.WEST));
                } else {
                    b.force(Phase.ROOF, along, y, first, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.SOUTH));
                    b.force(Phase.ROOF, along, y, second, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH));
                }
            }
            int fillMin = first + 1;
            int fillMax = second - 1;
            for (int coordinate = fillMin; coordinate <= fillMax; coordinate++) {
                if (slopeAxis == Direction.Axis.X) {
                    if (coordinate >= minX && coordinate <= maxX) {
                        b.force(Phase.SHELL, coordinate, y, minZ,
                                coordinate == (minX + maxX) / 2
                                        ? p.timber.defaultBlockState()
                                        : p.wall.defaultBlockState());
                        b.force(Phase.SHELL, coordinate, y, maxZ,
                                coordinate == (minX + maxX) / 2
                                        ? p.timber.defaultBlockState()
                                        : p.wall.defaultBlockState());
                    }
                } else if (coordinate >= minZ && coordinate <= maxZ) {
                    b.force(Phase.SHELL, minX, y, coordinate,
                            coordinate == (minZ + maxZ) / 2
                                    ? p.timber.defaultBlockState()
                                    : p.wall.defaultBlockState());
                    b.force(Phase.SHELL, maxX, y, coordinate,
                            coordinate == (minZ + maxZ) / 2
                                    ? p.timber.defaultBlockState()
                                    : p.wall.defaultBlockState());
                }
            }
        }
        int ridge = slopeAxis == Direction.Axis.X
                ? (minX + maxX) / 2
                : (minZ + maxZ) / 2;
        int ridgeEnd = span % 2 == 0 ? ridge + 1 : ridge;
        int ridgeY = roofY + layers;
        int runMin = (slopeAxis == Direction.Axis.X ? minZ : minX) - 1;
        int runMax = (slopeAxis == Direction.Axis.X ? maxZ : maxX) + 1;
        for (int ridgeCoordinate = ridge; ridgeCoordinate <= ridgeEnd; ridgeCoordinate++) {
            for (int along = runMin; along <= runMax; along++) {
                if (slopeAxis == Direction.Axis.X) {
                    b.force(Phase.ROOF, ridgeCoordinate, ridgeY, along,
                            p.roofSlab.defaultBlockState());
                } else {
                    b.force(Phase.ROOF, along, ridgeY, ridgeCoordinate,
                            p.roofSlab.defaultBlockState());
                }
            }
        }
        m.roofPeak = Math.max(m.roofPeak, ridgeY);
    }

    private static void addSaltboxRoof(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight) {
        int ridgeLeft = minX + 3;
        int ridgeRight = ridgeLeft + 1;
        for (int x = minX - 1; x <= maxX + 1; x++) {
            int y;
            Direction facing;
            if (x <= ridgeLeft) {
                y = wallHeight + 1 + (x - (minX - 1));
                facing = Direction.EAST;
            } else {
                y = wallHeight + 1 + Math.max(0, (maxX + 1 - x + 1) / 2);
                facing = Direction.WEST;
            }
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, facing));
            }
            if (x >= minX && x <= maxX) {
                for (int fillY = wallHeight + 1; fillY < y; fillY++) {
                    b.force(Phase.SHELL, x, fillY, minZ, p.wall.defaultBlockState());
                    b.force(Phase.SHELL, x, fillY, maxZ, p.wall.defaultBlockState());
                }
            }
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            // This ridge is directly above the lower right-hand stair course. A bottom slab only
            // touches half of that stair and leaves a visible notch against the taller left pitch.
            b.force(Phase.ROOF, ridgeRight, wallHeight + 5, z, doubleRoofSlab(p));
        }
        m.roofPeak = Math.max(m.roofPeak, wallHeight + 5);
    }

    private static void addShedRoof(
            Builder b,
            Metadata m,
            Materials p,
            Footprint footprint,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int baseY,
            Direction highSide) {
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            int distance = highSide == Direction.NORTH ? maxZ + 1 - z : z - (minZ - 1);
            int y = baseY + Math.max(0, distance) / 2;
            Direction facing = highSide == Direction.NORTH ? Direction.NORTH : Direction.SOUTH;
            for (int x = minX - 1; x <= maxX + 1; x++) {
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, facing));
            }
            if (z >= minZ && z <= maxZ) {
                for (int x = minX; x <= maxX; x++) {
                    if (!footprintBoundary(m, footprint, x, z)) {
                        continue;
                    }
                    for (int fillY = baseY; fillY < y; fillY++) {
                        b.force(Phase.SHELL, x, fillY, z, p.wall.defaultBlockState());
                    }
                }
            }
            m.roofPeak = Math.max(m.roofPeak, y);
        }
    }

    private static void addClippedGableRoof(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight) {
        addRectGableRoof(b, m, p, minX, maxX, minZ, maxZ, wallHeight, Direction.Axis.X);
        int center = (minX + maxX) / 2;
        int clipY = m.roofPeak;
        for (int z : new int[] {minZ - 1, maxZ + 1}) {
            for (int x = center - 1; x <= center + 1; x++) {
                b.force(Phase.ROOF, x, clipY, z, p.roofSlab.defaultBlockState());
            }
        }
    }

    private static void addHipRoof(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight) {
        // The outside stair ring is an eave, not a bearing course. Tie it back into the top of
        // the wall with a full-height slab ring at the same elevation. Using DOUBLE is important:
        // the next inset stair course occupies the cell above this ring, so a lower-half slab here
        // would leave a visible half-block air gap and would not transfer its load to that course.
        int bearingY = wallHeight + 1;
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.ROOF, x, bearingY, minZ, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, bearingY, maxZ, doubleRoofSlab(p));
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            b.force(Phase.ROOF, minX, bearingY, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, maxX, bearingY, z, doubleRoofSlab(p));
        }
        int layer = 0;
        int left = minX - 1;
        int right = maxX + 1;
        int front = minZ - 1;
        int back = maxZ + 1;
        while (left <= right && front <= back) {
            int y = wallHeight + 1 + layer;
            for (int x = left; x <= right; x++) {
                b.force(Phase.ROOF, x, y, front, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
                b.force(Phase.ROOF, x, y, back, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH));
            }
            for (int z = front + 1; z < back; z++) {
                b.force(Phase.ROOF, left, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
                b.force(Phase.ROOF, right, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
            m.roofPeak = Math.max(m.roofPeak, y);
            left++;
            right--;
            front++;
            back--;
            layer++;
        }
        int capY = wallHeight + 1 + layer;
        for (int x = Math.max(minX, left - 1); x <= Math.min(maxX, right + 1); x++) {
            for (int z = Math.max(minZ, front - 1); z <= Math.min(maxZ, back + 1); z++) {
                b.force(Phase.ROOF, x, capY, z, p.roofSlab.defaultBlockState());
            }
        }
        m.roofPeak = Math.max(m.roofPeak, capY);
    }

    private static void addPyramidRoof(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight) {
        addHipRoof(b, m, p, minX, maxX, minZ, maxZ, wallHeight);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int hipWidth = maxX - minX + 3;
        int hipDepth = maxZ - minZ + 3;
        int hipLayers = (Math.min(hipWidth, hipDepth) + 1) / 2;
        int localPeak = wallHeight + 1 + hipLayers;
        // The finial is a full cube. Give it a full-height center pedestal instead of asking it to
        // sit on a bottom slab whose upper face ends half a block below the next cell. Use this
        // roof's local peak rather than Metadata's catalog-wide maximum: paired towers otherwise
        // make the second finial climb one unsupported block above its own cap.
        b.force(Phase.ROOF, centerX, localPeak, centerZ, doubleRoofSlab(p));
        b.force(Phase.ROOF, centerX, localPeak + 1, centerZ, p.accent.defaultBlockState());
        m.roofPeak = Math.max(m.roofPeak, localPeak + 1);
    }

    private static void addMansardRoof(
            Builder b,
            Metadata m,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int wallHeight) {
        for (int layer = 0; layer < 3; layer++) {
            int left = minX - 1 + layer;
            int right = maxX + 1 - layer;
            int front = minZ - 1 + layer;
            int back = maxZ + 1 - layer;
            int y = wallHeight + 1 + layer;
            for (int x = left; x <= right; x++) {
                b.force(Phase.ROOF, x, y, front, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
                b.force(Phase.ROOF, x, y, back, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH));
            }
            for (int z = front + 1; z < back; z++) {
                b.force(Phase.ROOF, left, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
                b.force(Phase.ROOF, right, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        for (int x = minX + 2; x <= maxX - 2; x++) {
            for (int z = minZ + 2; z <= maxZ - 2; z++) {
                b.force(Phase.ROOF, x, wallHeight + 4, z, p.roofSlab.defaultBlockState());
            }
        }
        m.roofPeak = Math.max(m.roofPeak, wallHeight + 4);
    }

    private static void addFrontDormer(
            Builder b, Metadata m, Materials p, int centerX, int baseY) {
        // Carry the dormer's paired mullions down as continuous facade pilasters. Besides adding
        // the deep half-timber detail expected of a high-tier master, this gives elevated dormers
        // a real load path instead of balancing their caps on a partial roof stair/slab below.
        post(b, Phase.FRAME, centerX - 1, 1, 0, baseY + 1, p.timber);
        post(b, Phase.FRAME, centerX + 1, 1, 0, baseY + 1, p.timber);
        b.force(Phase.OPENING, centerX, baseY, 0, Blocks.GLASS_PANE.defaultBlockState());
        b.force(Phase.OPENING, centerX, baseY + 1, 0, Blocks.GLASS_PANE.defaultBlockState());
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            b.force(Phase.ROOF, x, baseY + 2, -1, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, baseY + 2, 0, doubleRoofSlab(p));
        }
        m.roofPeak = Math.max(m.roofPeak, baseY + 2);
    }

    private static void addBayWindow(
            Builder b, Materials p, int wallX, int centerZ, Direction outward) {
        int outsideX = wallX + outward.getStepX();
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            b.force(Phase.FOUNDATION, outsideX, 0, z, p.foundation.defaultBlockState());
            b.force(Phase.OPENING, outsideX, 2, z, Blocks.GLASS_PANE.defaultBlockState());
            b.force(Phase.ROOF, outsideX, 3, z, doubleRoofSlab(p));
        }
        b.force(Phase.FRAME, outsideX, 1, centerZ - 1, p.timber.defaultBlockState());
        b.force(Phase.FRAME, outsideX, 1, centerZ + 1, p.timber.defaultBlockState());
        b.remove(wallX, 2, centerZ);
    }

    private static void addGardenPergola(
            Builder b, Materials p, int minX, int maxX, int minZ, int maxZ) {
        int centerX = (minX + maxX) / 2;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // A pergola may bridge an already-authored threshold or courtyard. Preserve that
                // load-bearing floor verbatim and only infill genuinely open garden cells; using
                // put unconditionally makes a valid trellis collide with the parent building.
                BlockPos ground = new BlockPos(x, 0, z);
                if (!b.isOccupied(ground)) {
                    b.put(Phase.FOUNDATION, x, 0, z,
                            ((x + z) % 2 == 0 ? Blocks.DIRT_PATH : p.floor).defaultBlockState());
                }
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {minZ, maxZ}) {
                post(b, Phase.FRAME, x, 1, z, 3, p.fence);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            b.put(Phase.ROOF, x, 3, minZ, p.fence.defaultBlockState());
            b.put(Phase.ROOF, x, 3, maxZ, p.fence.defaultBlockState());
        }
        for (int z = minZ; z <= maxZ; z++) {
            b.put(Phase.ROOF, minX, 3, z, p.fence.defaultBlockState());
            b.put(Phase.ROOF, maxX, 3, z, p.fence.defaultBlockState());
        }
        BlockState beam = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.FRAME, x, 4, minZ, beam);
            b.force(Phase.FRAME, x, 4, maxZ, beam);
        }
        // Twin short pendants make the orchard walk feel intentionally inhabited at night. The
        // timber rails provide sturdy bearings and the corner posts keep the assembly grounded.
        for (int z : new int[] {minZ, maxZ}) {
            b.force(Phase.DECOR, centerX, 3, z, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
    }

    private static void addGardenPottingPavilion(
            Builder b, Materials p, int minX, int maxX, int minZ, int maxZ) {
        int centerX = (minX + maxX) / 2;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.FOUNDATION, x, 0, z,
                        (z == maxZ ? Blocks.DIRT_PATH : p.floor).defaultBlockState());
            }
        }
        for (int x : new int[] {minX, maxX}) {
            for (int z : new int[] {minZ, maxZ}) {
                post(b, Phase.FRAME, x, 1, z, 3, p.timber);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            beam(b, Phase.FRAME, minX, maxX, 3, z, p.timber);
            b.force(Phase.ROOF, minX, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.force(Phase.ROOF, centerX, 4, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, centerX, 5, z, p.roofSlab.defaultBlockState());
            b.force(Phase.ROOF, maxX, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        b.put(Phase.FIXTURE, centerX, 1, maxZ - 1, Blocks.COMPOSTER.defaultBlockState());
        b.put(Phase.DECOR, minX, 1, minZ + 1, Blocks.POTTED_FERN.defaultBlockState());
    }

    private static void addInnGallery(Builder b, Materials p) {
        for (int x = 4; x <= 12; x++) {
            for (int z = 2; z <= 3; z++) {
                b.force(Phase.FOUNDATION, x, 4, z, p.floor.defaultBlockState());
            }
            b.force(Phase.FRAME, x, 5, 2, p.fence.defaultBlockState());
        }
        // Tie the balcony into the two gatehouse side walls and keep its centerline open as the
        // inn's public circulation spine.
        for (int x : new int[] {4, 7, 9, 12}) {
            b.force(Phase.FOUNDATION, x, 0, 2, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, 2, 4, p.timber);
        }
        for (int x : new int[] {6, 10}) {
            b.force(Phase.DECOR, x, 3, 2, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
    }

    /**
     * Gives the inn a real second storey instead of a tall, empty shell.  The landing aligns with
     * the exterior gallery while the guest rooms stay in the broad rear mass, leaving the public
     * ground-floor aisle completely unobstructed.
     */
    private static void addInnUpperFloor(Builder b, Metadata m, Materials p) {
        for (int x = 1; x <= 15; x++) {
            for (int z = 5; z <= 11; z++) {
                b.put(Phase.FOUNDATION, x, 4, z, p.floor.defaultBlockState());
            }
        }
        for (int x : new int[] {4, 8, 12}) {
            for (int z = 4; z <= 12; z++) {
                b.force(Phase.FRAME, x, 4, z, p.timber.defaultBlockState());
            }
        }

        // The east wall is the ladder backing.  Its hatch doubles as a safe upper-floor dismount.
        for (int y = 1; y <= 3; y++) {
            b.force(Phase.FIXTURE, 15, y, 11,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        }
        b.force(Phase.FIXTURE, 15, 4, 11, Blocks.OAK_TRAPDOOR.defaultBlockState());
        m.verticalAccess.add(new VerticalAccess(new BlockPos(15, 1, 11), new BlockPos(15, 3, 11)));

        addBedAtLevel(b, m, 1, 5, 6, Direction.EAST, 3, 5, 6);
        addBedAtLevel(b, m, 15, 5, 6, Direction.WEST, 13, 5, 6);
        addBedAtLevel(b, m, 1, 5, 10, Direction.EAST, 3, 5, 10);
        addBedAtLevel(b, m, 15, 5, 10, Direction.WEST, 13, 5, 10);
        fixture(b, m, 6, 5, 10, Blocks.BARREL, new BlockPos(7, 5, 10));
        fixture(b, m, 10, 5, 10, Blocks.BOOKSHELF, new BlockPos(9, 5, 10));
    }

    private static void addMonitorRoof(Builder b, Metadata m, Materials p) {
        for (int x = -1; x <= 17; x++) {
            if (x >= 7 && x <= 9) {
                continue;
            }
            int edgeDistance = Math.min(x + 1, 17 - x);
            int y = 6 + Math.max(0, edgeDistance) / 3;
            Direction facing = x < 8 ? Direction.EAST : Direction.WEST;
            for (int z = -1; z <= 11; z++) {
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, facing));
            }
        }
        for (int z = 0; z <= 10; z++) {
            for (int y = 7; y <= 8; y++) {
                b.force(Phase.OPENING, 7, y, z, Blocks.GLASS_PANE.defaultBlockState());
                b.force(Phase.OPENING, 9, y, z, Blocks.GLASS_PANE.defaultBlockState());
            }
            if (z == 0 || z == 10) {
                b.force(Phase.FRAME, 8, 7, z, p.timber.defaultBlockState());
                b.force(Phase.OPENING, 8, 8, z, Blocks.GLASS_PANE.defaultBlockState());
            }
        }
        for (int z = -1; z <= 11; z++) {
            for (int x = 7; x <= 9; x++) {
                b.force(Phase.ROOF, x, 9, z, p.roofSlab.defaultBlockState());
            }
        }
        beam(b, Phase.FRAME, 7, 9, 6, 0, p.timber);
        beam(b, Phase.FRAME, 7, 9, 6, 10, p.timber);
        m.roofPeak = 9;
    }

    private static void addLoadingDock(Builder b, Materials p, int minX, int maxX) {
        for (int x = minX; x <= maxX; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            b.force(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[] {minX, maxX}) {
            post(b, Phase.FRAME, x, 1, -1, 5, p.timber);
            b.force(Phase.FRAME, x, 5, -2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        beam(b, Phase.FRAME, minX, maxX, 5, -1, p.timber);
        for (int x = minX; x <= maxX; x++) {
            // The main sawtooth descends onto both ends of this canopy.  Use a full ledger so
            // those upper courses bear without the half-block daylight seam seen in the gallery.
            b.force(Phase.ROOF, x, 6, -1, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, 6, -2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[] {minX + 3, maxX - 3}) {
            // A taller loading canopy intentionally supersedes any low porch sheet authored by
            // the base hall. Replace those cells so the chain and lamp hang from the new beam.
            b.force(Phase.DECOR, x, 4, -1, Blocks.IRON_CHAIN.defaultBlockState());
            b.force(Phase.DECOR, x, 3, -1, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
    }

    private static void addWarehouseFrameRhythm(Builder b, Materials p) {
        for (int z : new int[] {1, 4, 7, 9}) {
            post(b, Phase.FRAME, 0, 1, z, 5, p.timber);
            post(b, Phase.FRAME, 16, 1, z, 5, p.timber);
            b.put(Phase.FRAME, 1, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, 15, 4, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int x : new int[] {1, 5, 11, 15}) {
            post(b, Phase.FRAME, x, 1, 10, 5, p.timber);
            for (int z = 1; z <= 9; z++) {
                b.put(Phase.FRAME, x, 5, z, p.timber.defaultBlockState());
            }
        }
    }

    private static void addWarehouseRacks(Builder b, Materials p) {
        // Keep the rear z=8 service strip clear: it is the approach to the z=9 barrel row.
        for (int z : new int[] {2, 5, 7}) {
            addWarehouseRack(b, p, 1, 4, z, true);
            addWarehouseRack(b, p, 12, 15, z, false);
        }
    }

    private static void addWarehouseRack(
            Builder b, Materials p, int minX, int maxX, int z, boolean cargoAtMin) {
        post(b, Phase.FRAME, minX, 1, z, 3, p.fence);
        post(b, Phase.FRAME, maxX, 1, z, 3, p.fence);
        for (int x = minX + 1; x < maxX; x++) {
            b.put(Phase.FIXTURE, x, 2, z, p.roofSlab.defaultBlockState());
            b.put(Phase.FIXTURE, x, 3, z, upperRoofSlab(p));
        }
        int cargoX = cargoAtMin ? minX + 1 : maxX - 1;
        b.put(Phase.FIXTURE, cargoX, 1, z, Blocks.BARREL.defaultBlockState());
    }

    private static boolean edgeInRect(
            int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return x == minX || x == maxX || z == minZ || z == maxZ;
    }

    private static boolean cornerInRect(
            int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return (x == minX || x == maxX) && (z == minZ || z == maxZ);
    }

    private static BlockState doubleRoofSlab(Materials p) {
        return p.roofSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static BlockState inwardRoofStair(
            Materials p,
            int x,
            int z,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        Direction facing;
        if (x == minX) {
            facing = Direction.EAST;
        } else if (x == maxX) {
            facing = Direction.WEST;
        } else if (z == minZ) {
            facing = Direction.SOUTH;
        } else if (z == maxZ) {
            facing = Direction.NORTH;
        } else {
            throw new IllegalArgumentException("Roof stair is not on the requested perimeter");
        }
        return p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private static void post(
            Builder b, Phase phase, int x, int minY, int z, int maxY, Block block) {
        for (int y = minY; y <= maxY; y++) {
            b.force(phase, x, y, z, block.defaultBlockState());
        }
    }

    private static void beam(
            Builder b, Phase phase, int minX, int maxX, int y, int z, Block block) {
        for (int x = minX; x <= maxX; x++) {
            b.force(phase, x, y, z, block.defaultBlockState());
        }
    }

    private static void addInteriorLightingBeamX(
            Builder builder,
            Materials materials,
            int minX,
            int maxX,
            int y,
            int z) {
        BlockState beam = materials.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= maxX; x++) {
            builder.put(Phase.FRAME, x, y, z, beam);
        }
    }

    private static void addGrainChute(Builder b, Materials p, int wallX, int centerZ) {
        int outsideX = wallX + 1;
        b.force(Phase.FOUNDATION, outsideX, 0, centerZ, p.foundation.defaultBlockState());
        post(b, Phase.FRAME, outsideX, 1, centerZ, 5, p.timber);
        for (int y = 5; y <= 7; y++) {
            b.force(Phase.DECOR, outsideX, y, centerZ, Blocks.OAK_TRAPDOOR.defaultBlockState());
        }
        // This cap also bears the adjacent pyramid eave one cell above it. A lower slab would
        // leave the eave visibly hovering by half a block.
        b.force(Phase.ROOF, outsideX, 8, centerZ, doubleRoofSlab(p));
    }

    /** Replaces a complete wall course with a deliberate timber tie beam. */
    private static void addRectTimberBand(
            Builder b,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z : new int[] {minZ, maxZ}) {
                BlockPos pos = new BlockPos(x, y, z);
                if (b.contains(pos)) {
                    b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            for (int x : new int[] {minX, maxX}) {
                BlockPos pos = new BlockPos(x, y, z);
                if (b.contains(pos)) {
                    b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
        }
    }

    private static void addGranaryUndercroftBraces(Builder b, Materials p) {
        // Four X-braced bays make the elevated grain box look genuinely carried by its piers.
        for (int z : new int[] {2, 9}) {
            for (int step = 0; step < 3; step++) {
                int leftX = 2 + step;
                int rightX = 10 - step;
                b.force(Phase.FRAME, leftX, 1 + step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, leftX, 3 - step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, rightX, 1 + step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, rightX, 3 - step, z, p.timber.defaultBlockState());
            }
        }
        for (int x : new int[] {1, 5, 7, 11}) {
            for (int z : new int[] {2, 9}) {
                b.force(Phase.FRAME, x, 4, z, p.timber.defaultBlockState());
            }
        }
    }

    private static void addCourtyardFloor(Builder b, Materials p, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Block block = (x + z) % 4 == 0 ? p.foundation : p.accent;
                b.put(Phase.FOUNDATION, x, 0, z, block.defaultBlockState());
            }
        }
        int center = width / 2;
        for (int x = center - 1; x <= center + 1; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.accent.defaultBlockState());
            b.put(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
    }

    private static void addOpenWorkshopWing(
            Builder b,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Direction openToward) {
        int openX = openToward == Direction.EAST ? maxX : minX;
        int openZ = openToward == Direction.NORTH ? minZ : maxZ;
        for (int y = 1; y <= 4; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!edgeInRect(x, z, minX, maxX, minZ, maxZ)) {
                        continue;
                    }
                    boolean openFace = (openToward == Direction.EAST || openToward == Direction.WEST)
                            ? x == openX && z > minZ && z < maxZ
                            : z == openZ && x > minX && x < maxX;
                    if (!openFace) {
                        b.force(Phase.SHELL, x, y, z,
                                cornerInRect(x, z, minX, maxX, minZ, maxZ)
                                        ? p.timber.defaultBlockState()
                                        : p.foundation.defaultBlockState());
                    }
                }
            }
        }
        if (openToward == Direction.EAST || openToward == Direction.WEST) {
            for (int z : new int[] {minZ, maxZ}) {
                post(b, Phase.FRAME, openX, 1, z, 4, p.timber);
            }
        } else {
            for (int x : new int[] {minX, maxX}) {
                post(b, Phase.FRAME, x, 1, openZ, 4, p.timber);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.ROOF, x, 5, z, p.roofSlab.defaultBlockState());
            }
        }
    }

    private static void addForgeTimberArches(Builder b, Materials p) {
        // Each open workshop face gets a complete post-and-beam frame.  This turns the three
        // masonry boxes into one legible forge court and gives the canopies honest support.
        for (int z = 3; z <= 12; z++) {
            b.force(Phase.FRAME, 5, 4, z, p.timber.defaultBlockState());
        }
        for (int z = 6; z <= 12; z++) {
            b.force(Phase.FRAME, 11, 4, z, p.timber.defaultBlockState());
        }
        for (int x = 5; x <= 11; x++) {
            b.force(Phase.FRAME, x, 4, 9, p.timber.defaultBlockState());
        }
        for (int[] bracket : new int[][] {
                {5, 7, Direction.WEST.get2DDataValue()},
                {5, 10, Direction.WEST.get2DDataValue()},
                {11, 8, Direction.EAST.get2DDataValue()},
                {11, 11, Direction.EAST.get2DDataValue()}
        }) {
            Direction facing = Direction.from2DDataValue(bracket[2]);
            int x = bracket[0] + facing.getStepX();
            b.put(Phase.FRAME, x, 3, bracket[1], p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, facing)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int[] light : new int[][] {{5, 7}, {11, 8}}) {
            b.put(Phase.DECOR, light[0], 3, light[1], Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
    }

    private static void addForgeWorkDetails(Builder b, Materials p) {
        // Bind the twin flues into a tapered industrial crown rather than two unrelated stacks.
        beam(b, Phase.FRAME, 13, 15, 8, 10, p.chimney);
        b.force(Phase.FRAME, 14, 9, 10, p.chimney.defaultBlockState());

        // Raised stone aprons and short tool shelves make the working bays readable from the
        // courtyard without occupying the central three-wide approach.
        for (int x : new int[] {1, 2, 3, 13, 14, 15}) {
            int z = 11;
            b.put(Phase.FRAME, x, 1, z, p.accent.defaultBlockState());
            b.put(Phase.FIXTURE, x, 2, z, p.roofSlab.defaultBlockState());
        }
        b.put(Phase.FRAME, 12, 2, 10, Blocks.IRON_BARS.defaultBlockState());
        b.put(Phase.FRAME, 1, 4, 10, Blocks.IRON_CHAIN.defaultBlockState());
        b.put(Phase.DECOR, 1, 3, 10, Blocks.IRON_CHAIN.defaultBlockState());
    }

    private static void addWoodRack(Builder b, Materials p, int x, int z) {
        for (int dx = 0; dx <= 2; dx++) {
            b.force(Phase.FIXTURE, x + dx, 1, z, p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        b.force(Phase.ROOF, x, 2, z, p.roofSlab.defaultBlockState());
        b.force(Phase.ROOF, x + 2, 2, z, p.roofSlab.defaultBlockState());
    }

    private static void addMineToolShed(
            Builder b,
            Materials p,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Direction openToward) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            }
        }
        int openX = openToward == Direction.EAST ? maxX : minX;
        for (int y = 1; y <= 3; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.SHELL, openToward == Direction.EAST ? minX : maxX, y, z,
                        p.foundation.defaultBlockState());
            }
            for (int x = minX; x <= maxX; x++) {
                b.force(Phase.SHELL, x, y, maxZ, p.wall.defaultBlockState());
            }
        }
        for (int z : new int[] {minZ, maxZ}) {
            post(b, Phase.FRAME, openX, 1, z, 3, p.timber);
        }
        for (int x = minX; x <= maxX; x++) {
            int roofY = 4 + (openToward == Direction.EAST ? maxX - x : x - minX) / 2;
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.ROOF, x, roofY, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, openToward.getOpposite()));
            }
            // Close the two shed gables up to the stepped roof. The previous three-block wall
            // stopped as much as two blocks below the high course, leaving daylight under it.
            for (int y = 4; y < roofY; y++) {
                b.force(Phase.SHELL, x, y, minZ, p.wall.defaultBlockState());
                b.force(Phase.SHELL, x, y, maxZ, p.wall.defaultBlockState());
            }
        }
        int closedX = openToward == Direction.EAST ? minX : maxX;
        int highRoofY = 4 + (openToward == Direction.EAST
                ? maxX - closedX
                : closedX - minX) / 2;
        for (int y = 4; y < highRoofY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                b.force(Phase.SHELL, closedX, y, z, p.foundation.defaultBlockState());
            }
        }
    }

    private static void addMineCrossBracing(Builder b, Materials p) {
        for (int z : new int[] {5, 9}) {
            for (int step = 0; step < 3; step++) {
                b.force(Phase.FRAME, 4 + step, 2 + step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, 4 + step, 4 - step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, 10 - step, 2 + step, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, 10 - step, 4 - step, z, p.timber.defaultBlockState());
            }
            beam(b, Phase.FRAME, 3, 11, 5, z, p.timber);
            for (int x : new int[] {4, 10}) {
                b.force(Phase.FRAME, x, 6, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, x < 7 ? Direction.EAST : Direction.WEST)
                        .setValue(StairBlock.HALF, Half.TOP));
            }
        }
    }

    private static void addMineAditArch(Builder b, Materials p) {
        // A projecting stone-and-timber portal clearly identifies the mine entrance and braces
        // the retaining wall without narrowing the three-wide rail opening.
        for (int x : new int[] {5, 9}) {
            b.force(Phase.FOUNDATION, x, 0, 13, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, 13, 5, p.foundation);
        }
        beam(b, Phase.FRAME, 5, 9, 5, 13, p.timber);
        for (int x : new int[] {6, 8}) {
            b.force(Phase.FRAME, x, 4, 13, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 7 ? Direction.EAST : Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        b.put(Phase.DECOR, 5, 6, 13, Blocks.LANTERN.defaultBlockState());
        b.put(Phase.DECOR, 9, 6, 13, Blocks.LANTERN.defaultBlockState());
    }

    private static void addMarketArcade(Builder b, Materials p) {
        for (int coordinate : new int[] {1, 4, 12, 15}) {
            for (int x : new int[] {0, 16}) {
                post(b, Phase.FRAME, x, 1, coordinate, 4, p.timber);
            }
            for (int z : new int[] {0, 16}) {
                if (coordinate < 7 || coordinate > 9 || z == 16) {
                    post(b, Phase.FRAME, coordinate, 1, z, 4, p.timber);
                }
            }
        }
        for (int z = 0; z <= 16; z++) {
            for (int x = 0; x <= 2; x++) {
                b.force(Phase.ROOF, x, 5, z, p.roofSlab.defaultBlockState());
            }
            for (int x = 14; x <= 16; x++) {
                b.force(Phase.ROOF, x, 5, z, p.roofSlab.defaultBlockState());
            }
        }
        for (int x = 0; x <= 16; x++) {
            if (x >= 7 && x <= 9) {
                continue;
            }
            for (int z = 0; z <= 2; z++) {
                b.force(Phase.ROOF, x, 5, z, p.roofSlab.defaultBlockState());
            }
            for (int z = 14; z <= 16; z++) {
                b.force(Phase.ROOF, x, 5, z, p.roofSlab.defaultBlockState());
            }
        }
    }

    /** Adds a supported double-eave fascia without narrowing the market's three-wide cross aisle. */
    private static void addMarketArcadeTrim(Builder b, Materials p) {
        for (int coordinate = 1; coordinate <= 15; coordinate++) {
            b.put(Phase.FRAME, -1, 4, coordinate, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, 17, 4, coordinate, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int x = 1; x <= 15; x++) {
            if (x >= 7 && x <= 9) {
                continue;
            }
            b.put(Phase.FRAME, x, 4, -1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, x, 4, 17, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }

        // These eight knees sit above every dressing socket and directly under the new fascia.
        for (int coordinate : new int[] {4, 12}) {
            b.put(Phase.FRAME, -1, 3, coordinate, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, 17, 3, coordinate, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, coordinate, 3, -1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, coordinate, 3, 17, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private static void addDistinctMarketBay(
            Builder b,
            Metadata m,
            Materials p,
            int centerX,
            int centerZ,
            Direction facing,
            Block workstation,
            Block awning) {
        int outward = facing.getStepZ();
        if (outward == 0) {
            throw new IllegalArgumentException("Market bays must face north or south");
        }
        int workZ = centerZ - outward;
        int backZ = centerZ - 2 * outward;
        int frontZ = centerZ + outward;
        fixture(b, m, centerX, 1, workZ, workstation, new BlockPos(centerX, 1, centerZ));
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z : new int[] {workZ, centerZ, frontZ}) {
                b.force(Phase.ROOF, x, 4, z, awning.defaultBlockState());
            }
            b.force(Phase.FRAME, x, 3, workZ, p.timber.defaultBlockState());
            b.force(Phase.FRAME, x, 3, frontZ, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, facing)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            post(b, Phase.FRAME, x, 1, workZ, 2, p.fence);
            post(b, Phase.FRAME, x, 1, frontZ, 2, p.fence);
        }
        b.force(Phase.DECOR, centerX, 3, centerZ, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        addMarketBayIdentity(b, p, centerX, workZ, backZ, workstation, awning);
    }

    /** Keeps each trade readable from its own stall while leaving its customer tile untouched. */
    private static void addMarketBayIdentity(
            Builder b,
            Materials p,
            int centerX,
            int workZ,
            int backZ,
            Block workstation,
            Block awning) {
        for (int x : new int[] {centerX - 1, centerX + 1}) {
            b.put(Phase.FIXTURE, x, 1, workZ, p.roofSlab.defaultBlockState());
        }

        Block leftProp;
        Block rightProp;
        if (workstation == Blocks.COMPOSTER) {
            leftProp = Blocks.HAY_BLOCK;
            rightProp = Blocks.BARREL;
        } else if (workstation == Blocks.LOOM) {
            leftProp = awning;
            rightProp = Blocks.CHEST;
        } else if (workstation == Blocks.FLETCHING_TABLE) {
            leftProp = Blocks.TARGET;
            rightProp = Blocks.BARREL;
        } else {
            leftProp = p.foundation;
            rightProp = p.accent;
        }
        b.put(Phase.DECOR, centerX - 1, 1, backZ, leftProp.defaultBlockState());
        b.put(Phase.DECOR, centerX + 1, 1, backZ, rightProp.defaultBlockState());
    }

    private static void addBellRotunda(Builder b, Materials p, int centerX, int centerZ) {
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            for (int z : new int[] {centerZ - 2, centerZ + 2}) {
                // The extra top block keys each pier sideways into the first stepped roof ring.
                post(b, Phase.FRAME, x, 1, z, 8, p.timber);
            }
        }
        for (int layer = 0; layer < 3; layer++) {
            int radius = 3 - layer;
            int y = 8 + layer;
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (edgeInRect(x, z,
                            centerX - radius, centerX + radius,
                            centerZ - radius, centerZ + radius)) {
                        b.force(Phase.ROOF, x, y, z, inwardRoofStair(
                                p,
                                x,
                                z,
                                centerX - radius,
                                centerX + radius,
                                centerZ - radius,
                                centerZ + radius));
                    }
                }
            }
        }
        // Complete the roof core before adding its full-block finial. A chain is intentionally
        // slender and made the accent cube read as a hovering artifact from gallery distance.
        b.force(Phase.ROOF, centerX, 10, centerZ, doubleRoofSlab(p));
        b.force(Phase.ROOF, centerX, 11, centerZ, p.accent.defaultBlockState());
        // Carry the bell yoke into the grounded perimeter rather than asking the tension-only
        // chains above it to support a structural timber block.
        beam(b, Phase.FRAME, centerX - 2, centerX + 2, 7, centerZ, p.timber);
        for (int y = 8; y <= 9; y++) {
            b.force(Phase.FRAME, centerX, y, centerZ, Blocks.IRON_CHAIN.defaultBlockState());
        }
        b.put(Phase.FIXTURE, centerX, 6, centerZ, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
    }

    /** Exposed ring beams and knees make the bell canopy read as carpentry, not a floating roof. */
    private static void addBellRotundaBracing(
            Builder b, Materials p, int centerX, int centerZ) {
        int min = centerX - 2;
        int max = centerX + 2;
        beam(b, Phase.FRAME, min, max, 7, centerZ - 2, p.timber);
        beam(b, Phase.FRAME, min, max, 7, centerZ + 2, p.timber);
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            b.force(Phase.FRAME, centerX - 2, 7, z, p.timber.defaultBlockState());
            b.force(Phase.FRAME, centerX + 2, 7, z, p.timber.defaultBlockState());
        }
        for (int x : new int[] {centerX - 1, centerX + 1}) {
            b.put(Phase.FRAME, x, 6, centerZ - 2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, x < centerX ? Direction.WEST : Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, x, 6, centerZ + 2, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, x < centerX ? Direction.WEST : Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int z : new int[] {centerZ - 1, centerZ + 1}) {
            b.put(Phase.FRAME, centerX - 2, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, z < centerZ ? Direction.NORTH : Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, centerX + 2, 6, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, z < centerZ ? Direction.NORTH : Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private static void reserveCrossAisle(
            Metadata m, int center, int min, int max, int halfWidth) {
        for (int coordinate = min; coordinate <= max; coordinate++) {
            for (int offset = -halfWidth / 2; offset <= halfWidth / 2; offset++) {
                m.reservedAir.add(new BlockPos(center + offset, 1, coordinate));
                m.reservedAir.add(new BlockPos(center + offset, 2, coordinate));
                m.reservedAir.add(new BlockPos(coordinate, 1, center + offset));
                m.reservedAir.add(new BlockPos(coordinate, 2, center + offset));
            }
        }
    }

    /**
     * Gives the keep a defended gate, framed arrow slits and visibly supported watch-room
     * overhang. All detail stays outside the center aisle and the x=5 ladder/hatch column.
     */
    private static void addGuardTowerDetail(Builder b, Materials p) {
        // The paired front piers and high lintel read as a gate without occupying its two-block
        // walking clearance.
        for (int x : new int[] {2, 8}) {
            b.put(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, -1, 3, p.foundation);
        }
        beam(b, Phase.FRAME, 2, 8, 4, -1, p.accent);
        for (int x : new int[] {3, 7}) {
            b.put(Phase.FRAME, x, 3, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 5 ? Direction.EAST : Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }

        // Projecting side piers deliberately flank, rather than cover, the lower arrow slits.
        for (int x : new int[] {-1, 11}) {
            Direction inward = x < 0 ? Direction.EAST : Direction.WEST;
            for (int z : new int[] {4, 6}) {
                b.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                post(b, Phase.FRAME, x, 1, z, 3, p.foundation);
                b.put(Phase.FRAME, x, 4, z, p.accent.defaultBlockState());
            }
            b.put(Phase.FRAME, x, 2, 5, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, inward));
            b.put(Phase.FRAME, x, 4, 5, p.accent.defaultBlockState());
        }

        // A stone stringcourse breaks the tall shaft into believable load-bearing stages. The
        // ladder backing at (5,10,9) remains the original full foundation block.
        for (int x = 2; x <= 8; x++) {
            b.force(Phase.FRAME, x, 10, 2, p.accent.defaultBlockState());
            if (x != 5) {
                b.force(Phase.FRAME, x, 10, 9, p.accent.defaultBlockState());
            }
        }
        for (int z = 3; z <= 8; z++) {
            b.force(Phase.FRAME, 2, 10, z, p.accent.defaultBlockState());
            b.force(Phase.FRAME, 8, 10, z, p.accent.defaultBlockState());
        }

        addDeepWindowFrame(b, p, 5, 2, 7, 7, Direction.NORTH);
        addDeepWindowFrame(b, p, 2, 5, 7, 7, Direction.WEST);
        addDeepWindowFrame(b, p, 8, 5, 7, 7, Direction.EAST);

        // Timber eave ring and deep watch windows distinguish the occupied lookout from the
        // masonry shaft below it.
        beam(b, Phase.FRAME, 1, 9, 14, 1, p.timber);
        beam(b, Phase.FRAME, 1, 9, 14, 9, p.timber);
        for (int z = 2; z <= 8; z++) {
            b.force(Phase.FRAME, 1, 14, z, p.timber.defaultBlockState());
            b.force(Phase.FRAME, 9, 14, z, p.timber.defaultBlockState());
        }
        addDeepWindowFrame(b, p, 5, 1, 13, 13, Direction.NORTH);
        addDeepWindowFrame(b, p, 5, 9, 13, 13, Direction.SOUTH);
        addDeepWindowFrame(b, p, 1, 5, 13, 13, Direction.WEST);
        addDeepWindowFrame(b, p, 9, 5, 13, 13, Direction.EAST);

        for (int z : new int[] {3, 5, 7}) {
            b.put(Phase.FRAME, 1, 11, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, 9, 11, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int x : new int[] {3, 7}) {
            b.put(Phase.FRAME, x, 11, 1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
            b.put(Phase.FRAME, x, 11, 10, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private static void addFlaredWatchRoof(Builder b, Metadata m, Materials p) {
        // The flared x/z=0/10 ring is an overhanging eave. A full-height inner ring bears it on
        // the watch-room wall and also supports the raised x/z=1/9 stair course above.
        for (int x = 1; x <= 9; x++) {
            b.force(Phase.ROOF, x, 15, 1, doubleRoofSlab(p));
            b.force(Phase.ROOF, x, 15, 9, doubleRoofSlab(p));
        }
        for (int z = 2; z < 9; z++) {
            b.force(Phase.ROOF, 1, 15, z, doubleRoofSlab(p));
            b.force(Phase.ROOF, 9, 15, z, doubleRoofSlab(p));
        }
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                if (edgeInRect(x, z, 0, 10, 0, 10)) {
                    b.force(Phase.ROOF, x, 15, z,
                            inwardRoofStair(p, x, z, 0, 10, 0, 10));
                }
            }
        }
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                if (edgeInRect(x, z, 1, 9, 1, 9)) {
                    b.force(Phase.ROOF, x, 16, z,
                            inwardRoofStair(p, x, z, 1, 9, 1, 9));
                }
            }
        }
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                b.force(Phase.ROOF, x, 17, z, p.roofSlab.defaultBlockState());
            }
        }
        m.roofPeak = 17;
    }

    /** Half-timbered clerestory bands make the nave legible above the two lower aisle roofs. */
    private static void addCivicClerestoryFrame(Builder b, Materials p) {
        for (int x : new int[] {5, 13}) {
            for (int z = 1; z <= 13; z++) {
                b.force(Phase.FRAME, x, 5, z, p.timber.defaultBlockState());
                b.force(Phase.FRAME, x, 9, z, p.timber.defaultBlockState());
            }
            for (int z : new int[] {2, 5, 8, 11}) {
                for (int y = 6; y <= 8; y++) {
                    b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
        }
        for (int z : new int[] {0, 14}) {
            beam(b, Phase.FRAME, 5, 13, 5, z, p.timber);
            beam(b, Phase.FRAME, 5, 13, 9, z, p.timber);
            for (int x : new int[] {5, 8, 10, 13}) {
                for (int y = 6; y <= 8; y++) {
                    b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
        }
    }

    /** Deep lower windows and roof-bearing side buttresses give the civic base real depth. */
    private static void addCivicLowerFacadeDetail(Builder b, Materials p) {
        addDeepWindowFrame(b, p, 3, 0, 2, 3, Direction.NORTH);
        addDeepWindowFrame(b, p, 15, 0, 2, 3, Direction.NORTH);
        addDeepWindowFrame(b, p, 3, 14, 2, 3, Direction.SOUTH);
        addDeepWindowFrame(b, p, 15, 14, 2, 3, Direction.SOUTH);
        addDeepWindowFrame(b, p, 0, 10, 2, 3, Direction.WEST);
        addDeepWindowFrame(b, p, 18, 10, 2, 3, Direction.EAST);

        for (int x : new int[] {-1, 19}) {
            Direction inward = x < 0 ? Direction.EAST : Direction.WEST;
            for (int z : new int[] {6, 12}) {
                b.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
                post(b, Phase.FRAME, x, 1, z, 3, p.foundation);
                b.put(Phase.FRAME, x, 4, z, p.entryStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, inward)
                        .setValue(StairBlock.HALF, Half.TOP));
            }
        }
    }

    private static void addCivicAisleRoofs(Builder b, Materials p) {
        for (int x = -1; x <= 5; x++) {
            for (int z = -1; z <= 15; z++) {
                int y = 5 + Math.max(0, x + 1) / 3;
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
            }
        }
        for (int x = 13; x <= 19; x++) {
            for (int z = -1; z <= 15; z++) {
                int y = 5 + Math.max(0, 19 - x) / 3;
                b.force(Phase.ROOF, x, y, z, p.roofStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        // The nave rises above both aisles, so each front/rear aisle end needs a stepped
        // spandrel beneath its roof.  These are real gable walls, not cosmetic trim: omitting
        // them left the exchange hall open to weather behind an apparently complete facade.
        for (int x = 0; x <= 4; x++) {
            int roofY = 5 + Math.max(0, x + 1) / 3;
            for (int y = 5; y < roofY; y++) {
                b.force(Phase.SHELL, x, y, 0, p.wall.defaultBlockState());
                b.force(Phase.SHELL, x, y, 14, p.wall.defaultBlockState());
            }
        }
        for (int x = 14; x <= 18; x++) {
            int roofY = 5 + Math.max(0, 19 - x) / 3;
            for (int y = 5; y < roofY; y++) {
                b.force(Phase.SHELL, x, y, 0, p.wall.defaultBlockState());
                b.force(Phase.SHELL, x, y, 14, p.wall.defaultBlockState());
            }
        }
    }

    private static void addCivicPortico(Builder b, Metadata m, Materials p) {
        for (int x : new int[] {5, 7, 11, 13}) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            post(b, Phase.FRAME, x, 1, -1, 6, p.accent);
        }
        for (int x = 4; x <= 14; x++) {
            b.force(Phase.FRAME, x, 7, -1, p.accent.defaultBlockState());
            b.force(Phase.ROOF, x, 8, -2, p.roofSlab.defaultBlockState());
            b.force(Phase.ROOF, x, 8, -1, p.roofSlab.defaultBlockState());
        }
        for (int x = 8; x <= 10; x++) {
            b.force(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            b.force(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        b.force(Phase.DECOR, 9, 6, -1, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        m.roofPeak = Math.max(m.roofPeak, 8);
    }

    /** Adds a second portico beam, stone plinths and carved knees outside the clear threshold. */
    private static void addCivicPorticoDetail(Builder b, Materials p) {
        for (int x : new int[] {5, 7, 11, 13}) {
            b.force(Phase.FRAME, x, 1, -1, p.foundation.defaultBlockState());
            b.put(Phase.FOUNDATION, x, 0, -2, p.foundation.defaultBlockState());
            b.put(Phase.FRAME, x, 6, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
        beam(b, Phase.FRAME, 4, 14, 7, -2, p.timber);
    }

    private static void addCivicCupola(Builder b, Metadata m, Materials p) {
        // The civic gable ridge below the cupola is normally a lower slab. Fill the two exposed
        // ridge bearings to full height so the cupola roof does not hover half a block above it;
        // the center remains open for the ceiling-hung bell.
        for (int z : new int[] {6, 8}) {
            b.force(Phase.ROOF, 9, 15, z, doubleRoofSlab(p));
        }
        for (int x : new int[] {8, 10}) {
            for (int z : new int[] {6, 8}) {
                post(b, Phase.FRAME, x, 14, z, 15, p.timber);
            }
        }
        for (int x = 8; x <= 10; x++) {
            for (int z = 6; z <= 8; z++) {
                b.force(Phase.ROOF, x, 16, z, p.roofSlab.defaultBlockState());
            }
        }
        b.force(Phase.FIXTURE, 9, 15, 7, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        m.roofPeak = Math.max(m.roofPeak, 16);
    }

    /** A full timber ring and paired louvers visually seat the belfry on its ridge bearings. */
    private static void addCivicCupolaDetail(Builder b, Materials p) {
        for (int x = 8; x <= 10; x++) {
            b.force(Phase.FRAME, x, 15, 6, p.timber.defaultBlockState());
            b.force(Phase.FRAME, x, 15, 8, p.timber.defaultBlockState());
        }
        b.force(Phase.FRAME, 8, 15, 7, p.timber.defaultBlockState());
        b.force(Phase.FRAME, 10, 15, 7, p.timber.defaultBlockState());
        b.put(Phase.FRAME, 9, 14, 6, p.fence.defaultBlockState());
        b.put(Phase.FRAME, 9, 14, 8, p.fence.defaultBlockState());
    }

    private static void addCivicTellerRail(
            Builder b, Materials p, int minX, int maxX, int z, int openingX) {
        for (int x = minX; x <= maxX; x++) {
            if (x == openingX) {
                continue;
            }
            b.force(Phase.FIXTURE, x, 1, z, p.accent.defaultBlockState());
            b.force(Phase.FIXTURE, x, 2, z, Blocks.IRON_BARS.defaultBlockState());
        }
    }

    /** Side-wall seating and a framed teller canopy furnish the hall without entering its aisle. */
    private static void addCivicInteriorDetail(Builder b, Materials p) {
        for (int z : new int[] {5, 6}) {
            b.put(Phase.FIXTURE, 1, 1, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST));
            b.put(Phase.FIXTURE, 17, 1, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST));
        }
        post(b, Phase.FRAME, 6, 1, 10, 4, p.timber);
        post(b, Phase.FRAME, 12, 1, 10, 4, p.timber);
        beam(b, Phase.FRAME, 6, 12, 4, 10, p.timber);
        for (int x : new int[] {7, 11}) {
            b.put(Phase.DECOR, x, 3, 10, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
    }

    private static void addDoorAt(
            Builder b, Materials p, int x, int z, Direction facing) {
        addDoorAtLevel(b, p, x, 1, z, facing);
    }

    private static void addDoorAtLevel(
            Builder b, Materials p, int x, int baseY, int z, Direction facing) {
        BlockState lower = p.door.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        b.force(Phase.OPENING, x, baseY, z, lower);
        b.force(Phase.OPENING, x, baseY + 1, z,
                lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void addRegionalIdentity(Builder b, Metadata m, Materials p) {
        // Geometry remains role-authored. Regional identity is deliberately limited to replacing
        // existing facade cells so no biome can turn a validated floorplan into a different plan.
        // The courses are broad enough to read at village distance, unlike isolated texture noise.
        int center = m.width / 2;
        Block accent = switch (p.dialect) {
            case PLAINS -> Blocks.MOSSY_STONE_BRICKS;
            case DESERT -> Blocks.CUT_RED_SANDSTONE;
            case SAVANNA -> Blocks.SMOOTH_STONE;
            case TAIGA -> Blocks.MOSSY_STONE_BRICKS;
            case SNOWY -> Blocks.CHISELED_STONE_BRICKS;
        };
        int bandY = Math.max(1, Math.min(3, m.wallHeight - 1));
        for (int z : new int[] {0, m.depth - 1}) {
            for (int x = 1; x < m.width - 1; x++) {
                if ((x + center) % 3 == 0) {
                    replaceRegionalWallCell(b, p, x, bandY, z, accent);
                }
            }
        }
        for (int x : new int[] {0, m.width - 1}) {
            for (int z = 1; z < m.depth - 1; z++) {
                if ((z + center) % 4 == 0) {
                    replaceRegionalWallCell(b, p, x, bandY, z, accent);
                }
            }
        }

        // Door-side piers establish a deliberate palette hierarchy on the principal elevation.
        // Only authored wall cells are replaced; doors, windows and circulation remain untouched.
        for (int x : new int[] {Math.max(0, center - 2), Math.min(m.width - 1, center + 2)}) {
            for (int y = 1; y <= Math.min(m.wallHeight, 4); y++) {
                replaceRegionalWallCell(b, p, x, y, 0, accent);
            }
        }
    }

    private static void replaceRegionalWallCell(
            Builder b, Materials p, int x, int y, int z, Block replacement) {
        BlockPos position = new BlockPos(x, y, z);
        Cell existing = b.cellAt(position);
        if (existing != null
                && existing.phase == Phase.SHELL
                && existing.state.is(p.wall)) {
            b.force(Phase.FRAME, x, y, z, replacement.defaultBlockState());
        }
    }

    /**
     * A restrained, catalog-wide presentation pass. It reads the already-authored shell instead
     * of assuming a rectangle, so a cross plan, tower or courtyard keeps its identity. The pass
     * adds complete structural rhythms (bays, belts, framed openings and eave knees), never
     * isolated texture noise. Individual masters remain responsible for their primary massing.
     */
    private static void addArchitecturalPresentation(
            Builder b,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale) {
        if (!m.enclosed) {
            addScaleAwareRoleCraft(b, m, p, templateId, scale);
            return;
        }
        int variant = Math.floorMod(templateId.hashCode(), 4);
        addFacadeBayRhythm(b, m, p, scale, variant);
        addGableLights(b, m, p, scale);
        addProjectedOpeningFrames(b, m, p, scale, variant);
        addFrontEaveKnees(b, m, p, scale, variant);
        addRearEaveKnees(b, m, p, scale, variant);
        // Finish both visible roof ends wherever a master exposes real gable infill. The helper
        // replaces wall cells rather than laying a second roof over the authored silhouette, so
        // intersecting wings and hipped roofs remain untouched.
        addCompactRoofEndCraft(b, m, p);
        addRearRoofEndCraft(b, m, p);
        addScaleAwareRoleCraft(b, m, p, templateId, scale);
    }

    private static void addFacadeBayRhythm(
            Builder b,
            Metadata m,
            Materials p,
            VillageArchitecture.BlueprintScale scale,
            int variant) {
        int[] frontBays = scale == VillageArchitecture.BlueprintScale.SMALL
                ? new int[] {2, m.width - 3}
                : scale == VillageArchitecture.BlueprintScale.MEDIUM
                ? new int[] {3, m.width - 4}
                : scale == VillageArchitecture.BlueprintScale.LARGE
                        ? new int[] {3, m.width / 3, m.width * 2 / 3, m.width - 4}
                        : new int[] {2, 5, m.width / 3, m.width * 2 / 3, m.width - 6, m.width - 3};
        int center = m.width / 2;
        for (int x : frontBays) {
            if (x <= 0 || x >= m.width - 1 || Math.abs(x - center) <= 1) {
                continue;
            }
            replaceWallPilaster(b, p, x, 0, 1, m.wallHeight);
        }

        int beltY = Math.max(3, Math.min(m.wallHeight, scale == VillageArchitecture.BlueprintScale.MEDIUM
                ? 4
                : scale == VillageArchitecture.BlueprintScale.SMALL
                        ? 3
                        : 5 + (variant & 1)));
        replaceWallBeltX(b, p, 0, beltY, 1, m.width - 2);
        if (scale == VillageArchitecture.BlueprintScale.SMALL
                || scale == VillageArchitecture.BlueprintScale.LARGE
                || scale == VillageArchitecture.BlueprintScale.LANDMARK) {
            replaceWallBeltX(b, p, m.depth - 1, beltY, 1, m.width - 2);
        }

        if (scale == VillageArchitecture.BlueprintScale.SMALL) {
            int sideX = (variant & 2) == 0 ? 0 : m.width - 1;
            replaceWallBeltZ(b, p, sideX, beltY, 1, m.depth - 2);
            for (int z : new int[] {2, m.depth - 3}) {
                if (z > 0 && z < m.depth - 1) {
                    replaceWallPilaster(b, p, sideX, z, 1, m.wallHeight);
                }
            }
        }

        if (scale == VillageArchitecture.BlueprintScale.LARGE
                || scale == VillageArchitecture.BlueprintScale.LANDMARK) {
            int[] sideBays = scale == VillageArchitecture.BlueprintScale.LARGE
                    ? new int[] {3, m.depth - 4}
                    : new int[] {2, m.depth / 3, m.depth * 2 / 3, m.depth - 3};
            for (int sideX : new int[] {0, m.width - 1}) {
                for (int z : sideBays) {
                    if (z > 0 && z < m.depth - 1) {
                        replaceWallPilaster(b, p, sideX, z, 1, m.wallHeight);
                    }
                }
            }
        }
    }

    private static void replaceWallPilaster(
            Builder b, Materials p, int x, int z, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            BlockPos position = new BlockPos(x, y, z);
            Cell cell = b.cellAt(position);
            if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                b.force(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
            }
        }
    }

    private static void replaceWallBeltX(
            Builder b, Materials p, int z, int y, int minX, int maxX) {
        BlockState beamState = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= maxX; x++) {
            BlockPos position = new BlockPos(x, y, z);
            Cell cell = b.cellAt(position);
            if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                b.force(Phase.FRAME, x, y, z, beamState);
            }
        }
    }

    private static void replaceWallBeltZ(
            Builder b, Materials p, int x, int y, int minZ, int maxZ) {
        BlockState beamState = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z = minZ; z <= maxZ; z++) {
            BlockPos position = new BlockPos(x, y, z);
            Cell cell = b.cellAt(position);
            if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                b.force(Phase.FRAME, x, y, z, beamState);
            }
        }
    }

    private static void addGableLights(
            Builder b,
            Metadata m,
            Materials p,
            VillageArchitecture.BlueprintScale scale) {
        if (scale != VillageArchitecture.BlueprintScale.LARGE
                && scale != VillageArchitecture.BlueprintScale.LANDMARK) {
            return;
        }
        int center = m.width / 2;
        Block glazing = m.type == VillageProsperityEngine.ProjectType.GUARD_POST
                        || m.type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                ? Blocks.IRON_BARS
                : Blocks.GLASS_PANE;
        for (int y = m.wallHeight + 1; y < Math.min(m.roofPeak, m.height); y++) {
            boolean added = false;
            for (int offset : new int[] {-2, 2}) {
                int x = center + offset;
                BlockPos position = new BlockPos(x, y, 0);
                Cell cell = b.cellAt(position);
                if (cell == null || cell.phase != Phase.SHELL
                        || !b.isOccupied(position.east()) || !b.isOccupied(position.west())) {
                    continue;
                }
                b.force(Phase.OPENING, x, y, 0, glazing.defaultBlockState());
                added = true;
            }
            if (added) {
                return;
            }
        }
    }

    private static void addProjectedOpeningFrames(
            Builder b,
            Metadata m,
            Materials p,
            VillageArchitecture.BlueprintScale scale,
            int variant) {
        int frontLimit = switch (scale) {
            case SMALL -> 1;
            case MEDIUM -> 2;
            case LARGE -> 3;
            case LANDMARK -> 4;
        };
        framePaneRunsX(b, m, p, 0, Direction.NORTH, frontLimit);
        if (scale == VillageArchitecture.BlueprintScale.SMALL
                || scale == VillageArchitecture.BlueprintScale.MEDIUM) {
            framePaneRunsX(b, m, p, m.depth - 1, Direction.SOUTH, 1);
            int sideX = (variant & 1) == 0 ? 0 : m.width - 1;
            Direction outward = sideX == 0 ? Direction.WEST : Direction.EAST;
            framePaneRunsZ(b, m, p, sideX, outward, 1);
        } else if (scale == VillageArchitecture.BlueprintScale.LARGE
                || scale == VillageArchitecture.BlueprintScale.LANDMARK) {
            int rearLimit = scale == VillageArchitecture.BlueprintScale.LANDMARK ? 3 : 2;
            int sideLimit = scale == VillageArchitecture.BlueprintScale.LANDMARK ? 2 : 1;
            framePaneRunsX(b, m, p, m.depth - 1, Direction.SOUTH, rearLimit);
            // Major plans are meant to hold up from more than the gallery's front camera. Frame
            // both side elevations, but retain small limits so the primary authored mass still
            // controls the silhouette.
            framePaneRunsZ(b, m, p, 0, Direction.WEST, sideLimit);
            framePaneRunsZ(b, m, p, m.width - 1, Direction.EAST, sideLimit);
        }
    }

    private static void framePaneRunsX(
            Builder b,
            Metadata m,
            Materials p,
            int z,
            Direction outward,
            int limit) {
        int framed = 0;
        for (int x = 1; x < m.width - 1 && framed < limit; x++) {
            if (!hasPaneColumn(b, x, z, m.wallHeight)) {
                continue;
            }
            int start = x;
            while (x + 1 < m.width - 1 && hasPaneColumn(b, x + 1, z, m.wallHeight)) {
                x++;
            }
            int[] vertical = paneVerticalRange(b, start, x, z, true, m.wallHeight);
            if (vertical != null && tryProjectedFrameX(
                    b, p, start, x, z, vertical[0], vertical[1], outward)) {
                framed++;
            }
        }
    }

    private static void framePaneRunsZ(
            Builder b,
            Metadata m,
            Materials p,
            int x,
            Direction outward,
            int limit) {
        int framed = 0;
        for (int z = 1; z < m.depth - 1 && framed < limit; z++) {
            if (!hasPaneColumn(b, x, z, m.wallHeight)) {
                continue;
            }
            int start = z;
            while (z + 1 < m.depth - 1 && hasPaneColumn(b, x, z + 1, m.wallHeight)) {
                z++;
            }
            int[] vertical = paneVerticalRange(b, start, z, x, false, m.wallHeight);
            if (vertical != null && tryProjectedFrameZ(
                    b, p, x, start, z, vertical[0], vertical[1], outward)) {
                framed++;
            }
        }
    }

    private static boolean hasPaneColumn(Builder b, int x, int z, int wallHeight) {
        for (int y = 1; y <= Math.max(wallHeight + 3, 4); y++) {
            Cell cell = b.cellAt(new BlockPos(x, y, z));
            if (cell != null && (cell.state.is(Blocks.GLASS_PANE)
                    || cell.state.is(Blocks.IRON_BARS))) {
                return true;
            }
        }
        return false;
    }

    private static int[] paneVerticalRange(
            Builder b,
            int runStart,
            int runEnd,
            int fixed,
            boolean runAlongX,
            int wallHeight) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int coordinate = runStart; coordinate <= runEnd; coordinate++) {
            for (int y = 1; y <= Math.max(wallHeight + 3, 4); y++) {
                int x = runAlongX ? coordinate : fixed;
                int z = runAlongX ? fixed : coordinate;
                Cell cell = b.cellAt(new BlockPos(x, y, z));
                if (cell != null && (cell.state.is(Blocks.GLASS_PANE)
                        || cell.state.is(Blocks.IRON_BARS))) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return minY == Integer.MAX_VALUE ? null : new int[] {minY, maxY};
    }

    private static boolean tryProjectedFrameX(
            Builder b,
            Materials p,
            int minX,
            int maxX,
            int wallZ,
            int minY,
            int maxY,
            Direction outward) {
        int outerZ = wallZ + outward.getStepZ();
        Set<BlockPos> required = new HashSet<>();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            required.add(new BlockPos(x, minY - 1, outerZ));
            required.add(new BlockPos(x, maxY + 1, outerZ));
        }
        for (int y = minY; y <= maxY; y++) {
            required.add(new BlockPos(minX - 1, y, outerZ));
            required.add(new BlockPos(maxX + 1, y, outerZ));
        }
        if (required.stream().anyMatch(b::isOccupied)) {
            return false;
        }
        // Do not place a top-slab sill one block above an existing partial roof course. Even
        // when its side posts are connected, that stack leaves a visible half-block air seam.
        for (int x = minX - 1; x <= maxX + 1; x++) {
            Cell belowSill = b.cellAt(new BlockPos(x, minY - 2, outerZ));
            if (belowSill != null && (belowSill.state.getBlock() instanceof SlabBlock
                    || belowSill.state.getBlock() instanceof StairBlock)) {
                return false;
            }
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            b.put(Phase.FRAME, x, minY - 1, outerZ, upperRoofSlab(p));
            b.put(Phase.FRAME, x, maxY + 1, outerZ, doubleRoofSlab(p));
        }
        for (int y = minY; y <= maxY; y++) {
            b.put(Phase.FRAME, minX - 1, y, outerZ, p.timber.defaultBlockState());
            b.put(Phase.FRAME, maxX + 1, y, outerZ, p.timber.defaultBlockState());
        }
        return true;
    }

    private static boolean tryProjectedFrameZ(
            Builder b,
            Materials p,
            int wallX,
            int minZ,
            int maxZ,
            int minY,
            int maxY,
            Direction outward) {
        int outerX = wallX + outward.getStepX();
        Set<BlockPos> required = new HashSet<>();
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            required.add(new BlockPos(outerX, minY - 1, z));
            required.add(new BlockPos(outerX, maxY + 1, z));
        }
        for (int y = minY; y <= maxY; y++) {
            required.add(new BlockPos(outerX, y, minZ - 1));
            required.add(new BlockPos(outerX, y, maxZ + 1));
        }
        if (required.stream().anyMatch(b::isOccupied)) {
            return false;
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            Cell belowSill = b.cellAt(new BlockPos(outerX, minY - 2, z));
            if (belowSill != null && (belowSill.state.getBlock() instanceof SlabBlock
                    || belowSill.state.getBlock() instanceof StairBlock)) {
                return false;
            }
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            b.put(Phase.FRAME, outerX, minY - 1, z, upperRoofSlab(p));
            b.put(Phase.FRAME, outerX, maxY + 1, z, doubleRoofSlab(p));
        }
        for (int y = minY; y <= maxY; y++) {
            b.put(Phase.FRAME, outerX, y, minZ - 1, p.timber.defaultBlockState());
            b.put(Phase.FRAME, outerX, y, maxZ + 1, p.timber.defaultBlockState());
        }
        return true;
    }

    private static void addFrontEaveKnees(
            Builder b,
            Metadata m,
            Materials p,
            VillageArchitecture.BlueprintScale scale,
            int variant) {
        int spacing = scale == VillageArchitecture.BlueprintScale.MEDIUM ? 4 : 3;
        int center = m.width / 2;
        int offset = 1 + variant % 2;
        for (int x = offset; x < m.width - 1; x += spacing) {
            if (Math.abs(x - center) <= 1) {
                continue;
            }
            BlockPos target = new BlockPos(x, m.wallHeight, -1);
            Cell bearing = b.cellAt(target.above());
            Cell below = b.cellAt(target.below());
            if (b.isOccupied(target) || bearing == null || bearing.phase != Phase.ROOF
                    // A hanging knee can bear from the eave above, but placing one immediately
                    // over a partial porch/aisle roof creates a visible half-block seam below it.
                    || (below != null && below.phase == Phase.ROOF)) {
                continue;
            }
            b.put(Phase.FRAME, x, m.wallHeight, -1, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    /** Mirrors the supported eave-knee rhythm onto the rear elevation when a real eave exists. */
    private static void addRearEaveKnees(
            Builder b,
            Metadata m,
            Materials p,
            VillageArchitecture.BlueprintScale scale,
            int variant) {
        int spacing = scale == VillageArchitecture.BlueprintScale.MEDIUM ? 4 : 3;
        int center = m.width / 2;
        int offset = 1 + ((variant + 1) % 2);
        for (int x = offset; x < m.width - 1; x += spacing) {
            if (Math.abs(x - center) <= 1) {
                continue;
            }
            BlockPos target = new BlockPos(x, m.wallHeight, m.depth);
            Cell bearing = b.cellAt(target.above());
            Cell below = b.cellAt(target.below());
            if (b.isOccupied(target) || bearing == null || bearing.phase != Phase.ROOF
                    || (below != null && below.phase == Phase.ROOF)) {
                continue;
            }
            b.put(Phase.FRAME, x, m.wallHeight, m.depth, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH)
                    .setValue(StairBlock.HALF, Half.TOP));
        }
    }

    /** Adds a south-facing sconce whose support is the authored wall or post immediately north. */
    private static void addRearWallSconce(Builder b, int x, int y, int z) {
        b.put(Phase.DECOR, x, y, z, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.SOUTH));
    }

    /** Adds a ceiling-mounted lantern beneath an already-authored beam, fascia or roof course. */
    private static void addHangingLantern(Builder b, int x, int y, int z) {
        b.put(Phase.DECOR, x, y, z, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    /**
     * Adds several small, role-native room or covered-work zones without assuming a rectangular
     * footprint. Candidates are discovered from real supported floor cells and solid backing
     * walls; open buildings may use a roof-covered freestanding bay as a fallback. Every occupied
     * cell stays clear of authored circulation, workstation access, entrances and vertical travel.
     */
    private static void addScaleAwareRoleCraft(
            Builder b,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale) {
        // The landmark headframe's two narrow tool sheds already contain complete work scenes.
        // A generic three-cell bay can bisect their only route to the west workstation target.
        if (!m.enclosed && "mine_headframe_01".equals(templateId)) {
            return;
        }
        int target = switch (scale) {
            case SMALL -> 1;
            case MEDIUM -> 2;
            case LARGE -> 3;
            case LANDMARK -> 4;
        };
        if (!m.enclosed) {
            target = Math.max(1, target - 1);
        }
        int placed = 0;
        Direction[] wallOrder = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        int rotation = Math.floorMod(templateId.hashCode(), wallOrder.length);
        for (int pass = 0; pass < wallOrder.length && placed < target; pass++) {
            Direction wall = wallOrder[(pass + rotation) % wallOrder.length];
            boolean alongX = wall.getAxis() == Direction.Axis.Z;
            if (alongX) {
                int startZ = wall == Direction.NORTH ? 1 : m.depth - 2;
                int endZ = wall == Direction.NORTH ? m.depth - 2 : 1;
                int zStep = wall == Direction.NORTH ? 1 : -1;
                for (int z = startZ; z != endZ + zStep && placed < target; z += zStep) {
                    for (int x = 1; x + 2 < m.width - 1 && placed < target; x++) {
                        if (!canPlaceRoleCraftBay(b, m, x, z, true, wall, true)) {
                            continue;
                        }
                        addRoleCraftBay(b, m, p, x, z, true, wall.getOpposite(),
                                placed + rotation);
                        placed++;
                        x += 3;
                    }
                }
            } else {
                int startX = wall == Direction.WEST ? 1 : m.width - 2;
                int endX = wall == Direction.WEST ? m.width - 2 : 1;
                int xStep = wall == Direction.WEST ? 1 : -1;
                for (int x = startX; x != endX + xStep && placed < target; x += xStep) {
                    for (int z = 1; z + 2 < m.depth - 1 && placed < target; z++) {
                        if (!canPlaceRoleCraftBay(b, m, x, z, false, wall, true)) {
                            continue;
                        }
                        addRoleCraftBay(b, m, p, x, z, false, wall.getOpposite(),
                                placed + rotation);
                        placed++;
                        z += 3;
                    }
                }
            }
        }

        // Arcades, open forges, yards and mine sheds do not always provide three consecutive wall
        // cells. They still receive a deliberate scene, but only under existing weather cover.
        if (!m.enclosed) {
            for (int z = 1; z < m.depth - 1 && placed < target; z++) {
                for (int x = 1; x + 2 < m.width - 1 && placed < target; x++) {
                    if (!canPlaceRoleCraftBay(b, m, x, z, true, Direction.NORTH, false)
                            || !hasAuthoredCover(b, m, x + 1, z)) {
                        continue;
                    }
                    Direction facing = ((placed + rotation) & 1) == 0
                            ? Direction.NORTH : Direction.SOUTH;
                    addRoleCraftBay(b, m, p, x, z, true, facing, placed + rotation);
                    placed++;
                    x += 3;
                }
            }
        }
    }

    private static boolean canPlaceRoleCraftBay(
            Builder b,
            Metadata m,
            int x,
            int z,
            boolean alongX,
            Direction backingDirection,
            boolean requireBacking) {
        int backing = 0;
        List<BlockPos> proposedSolids = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int cellX = x + (alongX ? index : 0);
            int cellZ = z + (alongX ? 0 : index);
            BlockPos floor = new BlockPos(cellX, 0, cellZ);
            Cell support = b.cellAt(floor);
            if (support == null
                    || support.state.getBlock() instanceof SlabBlock
                    || support.state.getBlock() instanceof StairBlock) {
                return false;
            }
            for (int y = 1; y <= 2; y++) {
                BlockPos position = new BlockPos(cellX, y, cellZ);
                if (b.isOccupied(position) || isProtectedCraftCell(m, position)) {
                    return false;
                }
            }
            proposedSolids.add(new BlockPos(cellX, 1, cellZ));
            BlockPos behind = new BlockPos(cellX, 1, cellZ).relative(backingDirection);
            Cell backingCell = b.cellAt(behind);
            if (backingCell != null
                    && (backingCell.phase == Phase.SHELL || backingCell.phase == Phase.FRAME)) {
                backing++;
            }
        }
        return (!requireBacking || backing >= 2)
                && solidPropsPreserveInteractionRoutes(b, m, proposedSolids);
    }

    private static boolean isProtectedCraftCell(Metadata m, BlockPos position) {
        if (m.reservedAir.contains(position)
                || m.interiorSamples.contains(position)
                || horizontalDistance(position, m.entranceInside) <= 2) {
            return true;
        }
        for (BlockPos access : m.accessTargets) {
            if (horizontalDistance(position, access) <= 1) {
                return true;
            }
        }
        for (VerticalAccess access : m.verticalAccess) {
            if (horizontalDistance(position, access.from) <= 1
                    || horizontalDistance(position, access.to) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        if (second == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getZ() - second.getZ());
    }

    private static boolean hasAuthoredCover(Builder b, Metadata m, int x, int z) {
        for (int y = 3; y <= m.height; y++) {
            if (b.isOccupied(new BlockPos(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static void addRoleCraftBay(
            Builder b,
            Metadata m,
            Materials p,
            int x,
            int z,
            boolean alongX,
            Direction facing,
            int variant) {
        BlockPos left = roleBayPosition(x, z, alongX, 0, 1);
        BlockPos middle = roleBayPosition(x, z, alongX, 1, 1);
        BlockPos right = roleBayPosition(x, z, alongX, 2, 1);
        BlockPos upperLeft = left.above();
        BlockPos upperMiddle = middle.above();
        BlockPos upperRight = right.above();
        BlockState seat = p.roofStairs.defaultBlockState()
                .setValue(StairBlock.FACING, facing);
        Direction.Axis runAxis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        switch (m.type) {
            case COTTAGE -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(), seat);
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        p.accent.defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.POTTED_FERN.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(), upperRoofSlab(p));
                b.put(Phase.DECOR, upperRight.getX(), upperRight.getY(), upperRight.getZ(),
                        Blocks.LANTERN.defaultBlockState());
            }
            case HOUSE -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(), seat);
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.CHISELED_BOOKSHELF.defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.LANTERN.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(), seat);
            }
            case INN -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(), seat);
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.BARREL.defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.LANTERN.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(), seat);
            }
            case WAREHOUSE -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(),
                        Blocks.BARREL.defaultBlockState());
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        p.timber.defaultBlockState().setValue(RotatedPillarBlock.AXIS, runAxis));
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.LANTERN.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(),
                        ((variant & 1) == 0 ? Blocks.BARREL : p.accent).defaultBlockState());
            }
            case GRANARY -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(),
                        Blocks.COMPOSTER.defaultBlockState());
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.HAY_BLOCK.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, runAxis));
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.HAY_BLOCK.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, runAxis));
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(),
                        Blocks.BARREL.defaultBlockState());
                b.put(Phase.DECOR, upperRight.getX(), upperRight.getY(), upperRight.getZ(),
                        Blocks.LANTERN.defaultBlockState());
            }
            case SMITHY -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(),
                        Blocks.CAULDRON.defaultBlockState());
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.ANVIL.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(), upperRoofSlab(p));
                b.put(Phase.DECOR, upperLeft.getX(), upperLeft.getY(), upperLeft.getZ(),
                        Blocks.IRON_BARS.defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.LANTERN.defaultBlockState());
            }
            case MINE_ENTRANCE -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(),
                        p.foundation.defaultBlockState());
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        ((variant & 1) == 0 ? Blocks.RAW_IRON_BLOCK : Blocks.COAL_BLOCK)
                                .defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.IRON_BARS.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(),
                        p.foundation.defaultBlockState());
                b.put(Phase.DECOR, upperLeft.getX(), upperLeft.getY(), upperLeft.getZ(),
                        Blocks.LANTERN.defaultBlockState());
            }
            case MARKET_SQUARE -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(),
                        Blocks.COMPOSTER.defaultBlockState());
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.LOOM.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(),
                        Blocks.BARREL.defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        dressingPlant(Math.floorMod(variant, 3)).defaultBlockState());
                b.put(Phase.DECOR, upperRight.getX(), upperRight.getY(), upperRight.getZ(),
                        Blocks.LANTERN.defaultBlockState());
            }
            case GUARD_POST -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(), seat);
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.TARGET.defaultBlockState());
                b.put(Phase.DECOR, upperMiddle.getX(), upperMiddle.getY(), upperMiddle.getZ(),
                        Blocks.LANTERN.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(), seat);
            }
            case EXCHANGE_HALL -> {
                b.put(Phase.DECOR, left.getX(), left.getY(), left.getZ(),
                        Blocks.CHISELED_BOOKSHELF.defaultBlockState());
                b.put(Phase.DECOR, upperLeft.getX(), upperLeft.getY(), upperLeft.getZ(),
                        Blocks.POTTED_FERN.defaultBlockState());
                b.put(Phase.DECOR, middle.getX(), middle.getY(), middle.getZ(),
                        Blocks.LECTERN.defaultBlockState());
                b.put(Phase.DECOR, right.getX(), right.getY(), right.getZ(),
                        Blocks.CHISELED_BOOKSHELF.defaultBlockState());
                b.put(Phase.DECOR, upperRight.getX(), upperRight.getY(), upperRight.getZ(),
                        Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    private static BlockPos roleBayPosition(
            int x, int z, boolean alongX, int offset, int y) {
        return new BlockPos(x + (alongX ? offset : 0), y, z + (alongX ? 0 : offset));
    }

    /**
     * Gives the smallest masters the hand-crafted finish that a material swap cannot supply.
     * The pass never changes descriptor dimensions. Exterior work replaces existing shell cells,
     * while the small interior vignette is admitted only against a wall on supported, unreserved
     * floor cells. The ordinary connectivity and lighting validators still judge the result.
     */
    private static void addCompactCraftLayer(
            Builder b,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale) {
        boolean reviewPriority = scale == VillageArchitecture.BlueprintScale.SMALL
                || switch (templateId) {
                    case "cottage_garden_02", "warehouse_bay_01", "granary_loft_01",
                            "market_crossroads_03", "exchange_branch_03" -> true;
                    default -> false;
                };
        if (!reviewPriority) {
            return;
        }
        if (m.enclosed) {
            addCompactRoofEndCraft(b, m, p);
            addCompactInteriorStory(b, m, p, templateId);
        } else {
            addCompactOpenWorkLedge(b, m, p, templateId);
        }
    }

    /** Replaces a short run of plain gable infill with a real collar and king post. */
    private static void addCompactRoofEndCraft(Builder b, Metadata m, Materials p) {
        int center = m.width / 2;
        BlockState collar = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int y = m.wallHeight + 1; y < Math.min(m.roofPeak, m.height); y++) {
            Cell middle = b.cellAt(new BlockPos(center, y, 0));
            if (middle == null || middle.phase != Phase.SHELL || !middle.state.is(p.wall)) {
                continue;
            }
            int replaced = 0;
            for (int x = Math.max(1, center - 2); x <= Math.min(m.width - 2, center + 2); x++) {
                BlockPos position = new BlockPos(x, y, 0);
                Cell cell = b.cellAt(position);
                if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                    b.force(Phase.FRAME, x, y, 0, collar);
                    replaced++;
                }
            }
            if (replaced >= 3) {
                for (int upperY = y + 1; upperY < Math.min(m.roofPeak, m.height); upperY++) {
                    BlockPos position = new BlockPos(center, upperY, 0);
                    Cell cell = b.cellAt(position);
                    if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                        b.force(Phase.FRAME, center, upperY, 0,
                                p.timber.defaultBlockState());
                    }
                }
                return;
            }
        }
    }

    /** Rear counterpart to the collar-and-king-post treatment used on compact front gables. */
    private static void addRearRoofEndCraft(Builder b, Metadata m, Materials p) {
        int center = m.width / 2;
        int rearZ = m.depth - 1;
        BlockState collar = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int y = m.wallHeight + 1; y < Math.min(m.roofPeak, m.height); y++) {
            Cell middle = b.cellAt(new BlockPos(center, y, rearZ));
            if (middle == null || middle.phase != Phase.SHELL || !middle.state.is(p.wall)) {
                continue;
            }
            int replaced = 0;
            for (int x = Math.max(1, center - 2); x <= Math.min(m.width - 2, center + 2); x++) {
                BlockPos position = new BlockPos(x, y, rearZ);
                Cell cell = b.cellAt(position);
                if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                    b.force(Phase.FRAME, x, y, rearZ, collar);
                    replaced++;
                }
            }
            if (replaced >= 3) {
                for (int upperY = y + 1; upperY < Math.min(m.roofPeak, m.height); upperY++) {
                    BlockPos position = new BlockPos(center, upperY, rearZ);
                    Cell cell = b.cellAt(position);
                    if (cell != null && cell.phase == Phase.SHELL && cell.state.is(p.wall)) {
                        b.force(Phase.FRAME, center, upperY, rearZ,
                                p.timber.defaultBlockState());
                    }
                }
                return;
            }
        }
    }

    private static void addCompactInteriorStory(
            Builder b, Metadata m, Materials p, String templateId) {
        int variant = Math.floorMod(templateId.hashCode(), 4);
        int rearZ = Math.max(2, m.depth - 2);
        int frontZ = Math.min(m.depth - 2, 2);
        int[][] anchors = (variant & 1) == 0
                ? new int[][] {{1, rearZ}, {m.width - 4, rearZ}, {1, frontZ},
                        {m.width - 4, frontZ}}
                : new int[][] {{m.width - 4, rearZ}, {1, rearZ},
                        {m.width - 4, frontZ}, {1, frontZ}};
        for (int[] anchor : anchors) {
            if (!canPlaceInteriorStory(b, m, anchor[0], anchor[1])) {
                continue;
            }
            int x = anchor[0];
            int z = anchor[1];
            BlockState seat = p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, z >= m.depth / 2
                            ? Direction.NORTH : Direction.SOUTH);
            b.put(Phase.DECOR, x, 1, z, seat);
            b.put(Phase.DECOR, x + 1, 1, z, p.accent.defaultBlockState());
            b.put(Phase.DECOR, x + 1, 2, z, compactStoryTop(m.type, p, variant));
            b.put(Phase.DECOR, x + 2, 1, z, upperRoofSlab(p));
            return;
        }
    }

    private static BlockState compactStoryTop(
            VillageProsperityEngine.ProjectType type, Materials p, int variant) {
        return switch (type) {
            case COTTAGE, HOUSE -> dressingPlant(variant % 3).defaultBlockState();
            case INN -> Blocks.CAKE.defaultBlockState();
            case WAREHOUSE -> p.timber.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            case GRANARY -> Blocks.HAY_BLOCK.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            case SMITHY, MINE_ENTRANCE -> Blocks.IRON_BARS.defaultBlockState();
            case MARKET_SQUARE -> Blocks.FLOWERING_AZALEA.defaultBlockState();
            case GUARD_POST -> Blocks.TARGET.defaultBlockState();
            case EXCHANGE_HALL -> Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        };
    }

    private static boolean canPlaceInteriorStory(
            Builder b, Metadata m, int minX, int z) {
        if (minX < 1 || minX + 2 >= m.width - 1 || z < 1 || z >= m.depth - 1) {
            return false;
        }
        boolean againstWall = false;
        for (int x = minX; x <= minX + 2; x++) {
            BlockPos floor = new BlockPos(x, 0, z);
            if (!b.isOccupied(floor)) {
                return false;
            }
            for (int y = 1; y <= 2; y++) {
                BlockPos cell = new BlockPos(x, y, z);
                if (b.isOccupied(cell)
                        || m.reservedAir.contains(cell)
                        || m.accessTargets.contains(cell)) {
                    return false;
                }
            }
            againstWall |= b.isOccupied(new BlockPos(x, 1,
                    z >= m.depth / 2 ? z + 1 : z - 1));
        }
        return againstWall && solidPropsPreserveInteractionRoutes(
                b,
                m,
                List.of(
                        new BlockPos(minX, 1, z),
                        new BlockPos(minX + 1, 1, z),
                        new BlockPos(minX + 2, 1, z)));
    }

    /** Adds one low role-native work surface to covered open compact structures. */
    private static void addCompactOpenWorkLedge(
            Builder b, Metadata m, Materials p, String templateId) {
        int z = Math.max(1, m.depth - 2);
        int[][] anchors = Math.floorMod(templateId.hashCode(), 2) == 0
                ? new int[][] {{1, z}, {m.width - 3, z}}
                : new int[][] {{m.width - 3, z}, {1, z}};
        for (int[] anchor : anchors) {
            int minX = anchor[0];
            if (minX < 1 || minX + 1 >= m.width - 1) {
                continue;
            }
            List<BlockPos> cells = List.of(
                    new BlockPos(minX, 1, z), new BlockPos(minX + 1, 1, z),
                    new BlockPos(minX, 2, z));
            boolean supportedAndClear = cells.stream().allMatch(position ->
                    !b.isOccupied(position)
                            && !m.reservedAir.contains(position)
                            && !m.accessTargets.contains(position))
                    && b.isOccupied(new BlockPos(minX, 0, z))
                    && b.isOccupied(new BlockPos(minX + 1, 0, z))
                    && solidPropsPreserveInteractionRoutes(b, m, cells);
            if (!supportedAndClear) {
                continue;
            }
            b.put(Phase.DECOR, minX, 1, z, p.accent.defaultBlockState());
            b.put(Phase.DECOR, minX + 1, 1, z, upperRoofSlab(p));
            b.put(Phase.DECOR, minX, 2, z,
                    m.type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                            ? Blocks.IRON_BARS.defaultBlockState()
                            : p.timber.defaultBlockState()
                                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            return;
        }
    }

    private static void cottageHearth(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 9, 9, 9, true);
        addGabledShell(b, m, p, 3, Direction.Axis.X, Porch.BROAD);
        addIntegratedChimney(b, m, p, 8, 6);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 7, 3, Direction.WEST, 5, 3);
        fixture(b, m, 2, 1, 7, Blocks.COMPOSTER, new BlockPos(2, 1, 6));
        fixture(b, m, 6, 1, 7, Blocks.CRAFTING_TABLE, new BlockPos(6, 1, 6));
        fixture(b, m, 7, 1, 6, Blocks.CHEST, new BlockPos(6, 1, 6));
        addTable(b, p, 3, 5);
        m.reserveCentralAisle(4, 1, 7);
    }

    private static void cottageGarden(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.COTTAGE, 9, 11, 9, true);
        addGabledShell(b, m, p, 3, Direction.Axis.X, Porch.SMALL);
        addBed(b, m, 1, 4, Direction.EAST, 3, 4);
        addBed(b, m, 7, 4, Direction.WEST, 5, 4);
        fixture(b, m, 2, 1, 9, Blocks.LOOM, new BlockPos(2, 1, 8));
        fixture(b, m, 6, 1, 9, Blocks.CHEST, new BlockPos(6, 1, 8));
        fixture(b, m, 7, 1, 8, Blocks.COMPOSTER, new BlockPos(6, 1, 8));
        addTable(b, p, 3, 6);
        m.reserveCentralAisle(4, 1, 9);
    }

    private static void houseCross(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 11, 11, 12, true);
        addGabledShell(b, m, p, 4, Direction.Axis.X, Porch.BROAD);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 9, 3, Direction.WEST, 7, 3);
        addBed(b, m, 1, 7, Direction.EAST, 3, 7);
        fixture(b, m, 2, 1, 9, Blocks.LOOM, new BlockPos(2, 1, 8));
        fixture(b, m, 5, 1, 9, Blocks.CRAFTING_TABLE, new BlockPos(5, 1, 8));
        fixture(b, m, 8, 1, 9, Blocks.CHEST, new BlockPos(8, 1, 8));
        addTable(b, p, 4, 5);
        m.reserveCentralAisle(5, 1, 8);
    }

    private static void houseDormer(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.HOUSE, 11, 11, 11, true);
        addGabledShell(b, m, p, 4, Direction.Axis.Z, Porch.SMALL);
        addBed(b, m, 1, 3, Direction.EAST, 3, 3);
        addBed(b, m, 9, 3, Direction.WEST, 7, 3);
        addBed(b, m, 1, 7, Direction.EAST, 3, 7);
        fixture(b, m, 2, 1, 9, Blocks.BOOKSHELF, new BlockPos(2, 1, 8));
        fixture(b, m, 5, 1, 9, Blocks.CRAFTING_TABLE, new BlockPos(5, 1, 8));
        fixture(b, m, 8, 1, 9, Blocks.CHEST, new BlockPos(8, 1, 8));
        addTable(b, p, 4, 6);
        m.reserveCentralAisle(5, 1, 8);
    }

    private static void innGallery(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.INN, 13, 11, 12, true);
        addGabledShell(b, m, p, 4, Direction.Axis.X, Porch.BROAD);
        addIntegratedChimney(b, m, p, 12, 8);
        for (int z : new int[] {3, 7}) {
            addBed(b, m, 1, z, Direction.EAST, 3, z);
            addBed(b, m, 11, z, Direction.WEST, 9, z);
        }
        fixture(b, m, 2, 1, 9, Blocks.BREWING_STAND, new BlockPos(2, 1, 8));
        fixture(b, m, 4, 1, 9, Blocks.SMOKER, new BlockPos(4, 1, 8));
        fixture(b, m, 8, 1, 9, Blocks.CAKE, new BlockPos(8, 1, 8));
        fixture(b, m, 10, 1, 9, Blocks.CHEST, new BlockPos(10, 1, 8));
        addLongTable(b, p, 3, 5, 3);
        m.reserveCentralAisle(6, 1, 9);
    }

    private static void warehouseBay(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.WAREHOUSE, 13, 9, 10, true);
        addGabledShell(b, m, p, 4, Direction.Axis.Z, Porch.LOADING);
        for (int x : new int[] {1, 3, 9, 11}) {
            fixture(b, m, x, 1, 7, Blocks.CHEST, new BlockPos(x, 1, 6));
        }
        fixture(b, m, 2, 1, 5, Blocks.LOOM, new BlockPos(3, 1, 5));
        fixture(b, m, 10, 1, 5, Blocks.STONECUTTER, new BlockPos(9, 1, 5));
        fixture(b, m, 6, 1, 7, Blocks.CRAFTING_TABLE, new BlockPos(6, 1, 6));
        m.reserveCentralAisle(6, 1, 6);
    }

    private static void granaryLoft(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GRANARY, 11, 9, 11, true);
        addGabledShell(b, m, p, 5, Direction.Axis.Z, Porch.SMALL);
        for (int x : new int[] {1, 3, 7, 9}) {
            fixture(b, m, x, 1, 7, Blocks.HAY_BLOCK, new BlockPos(x, 1, 6));
            b.put(Phase.FIXTURE, x, 2, 7, Blocks.HAY_BLOCK.defaultBlockState());
        }
        fixture(b, m, 2, 1, 5, Blocks.SMOKER, new BlockPos(3, 1, 5));
        fixture(b, m, 8, 1, 5, Blocks.COMPOSTER, new BlockPos(7, 1, 5));
        fixture(b, m, 5, 1, 7, Blocks.CHEST, new BlockPos(5, 1, 6));
        m.reserveCentralAisle(5, 1, 6);
    }

    private static void smithyCourtyard(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.SMITHY, 13, 9, 10, true);
        addGabledShell(b, m, p, 4, Direction.Axis.Z, Porch.WORKSHOP);
        addIntegratedChimney(b, m, p, 12, 6);
        fixture(b, m, 2, 1, 7, Blocks.ANVIL, new BlockPos(2, 1, 6));
        fixture(b, m, 4, 1, 7, Blocks.BLAST_FURNACE, new BlockPos(4, 1, 6));
        fixture(b, m, 8, 1, 7, Blocks.SMITHING_TABLE, new BlockPos(8, 1, 6));
        fixture(b, m, 10, 1, 7, Blocks.GRINDSTONE, new BlockPos(10, 1, 6));
        fixture(b, m, 2, 1, 4, Blocks.CAULDRON, new BlockPos(3, 1, 4));
        fixture(b, m, 10, 1, 4, Blocks.CHEST, new BlockPos(9, 1, 4));
        m.reserveCentralAisle(6, 1, 7);
    }

    private static void mineHeadframe(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MINE_ENTRANCE, 11, 11, 10, false);
        addGabledShell(b, m, p, 3, Direction.Axis.X, Porch.OPEN);
        // Replace the ordinary single door with a supported three-wide rail portal.
        for (int x = 4; x <= 6; x++) {
            b.remove(x, 1, 0);
            b.remove(x, 2, 0);
            b.remove(x, 3, 0);
        }
        for (int z = 0; z <= 9; z++) {
            b.put(Phase.FIXTURE, 5, 1, z, Blocks.RAIL.defaultBlockState());
        }
        for (int x : new int[] {3, 7}) {
            for (int y = 1; y <= 4; y++) {
                b.force(Phase.FRAME, x, y, 1, p.timber.defaultBlockState());
            }
        }
        for (int x = 3; x <= 7; x++) {
            b.force(Phase.FRAME, x, 4, 1, p.timber.defaultBlockState());
        }
        for (int x = 4; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                b.force(Phase.SHELL, x, y, 10, Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        fixture(b, m, 2, 1, 8, Blocks.STONECUTTER, new BlockPos(3, 1, 8));
        fixture(b, m, 8, 1, 8, Blocks.BLAST_FURNACE, new BlockPos(7, 1, 8));
        fixture(b, m, 2, 1, 5, Blocks.CHEST, new BlockPos(3, 1, 5));
        m.enclosed = false;
        for (int z = 2; z <= 9; z++) {
            m.reservedAir.add(new BlockPos(5, 2, z));
        }
    }

    private static void marketCloister(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.MARKET_SQUARE, 15, 15, 8, false);
        addMarketFloor(b, p, 15, 15);
        addMarketStall(b, m, p, 3, 3, Direction.SOUTH, Blocks.COMPOSTER);
        addMarketStall(b, m, p, 11, 3, Direction.SOUTH, Blocks.LOOM);
        addMarketStall(b, m, p, 3, 11, Direction.NORTH, Blocks.FLETCHING_TABLE);
        addMarketStall(b, m, p, 11, 11, Direction.NORTH, Blocks.STONECUTTER);
        addMarketBellArch(b, p, 7, 12);
        for (int x = 6; x <= 8; x++) {
            b.put(Phase.FOUNDATION, x, 0, -1, p.accent.defaultBlockState());
            b.put(Phase.FOUNDATION, x, 0, -2, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        m.entranceInside = new BlockPos(7, 1, 1);
        m.accessTargets.add(new BlockPos(7, 1, 7));
        for (int coordinate = 1; coordinate <= 13; coordinate++) {
            for (int x = 6; x <= 8; x++) {
                m.reservedAir.add(new BlockPos(x, 1, coordinate));
                m.reservedAir.add(new BlockPos(x, 2, coordinate));
            }
            for (int z = 6; z <= 8; z++) {
                m.reservedAir.add(new BlockPos(coordinate, 1, z));
                m.reservedAir.add(new BlockPos(coordinate, 2, z));
            }
        }
    }

    private static void guardWatch(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.GUARD_POST, 9, 9, 14, true);
        addTowerShell(b, m, p);
        // Four post-aligned merlons crown the lookout canopy. They read as one defensive family
        // while breaking the broad flat cap into deliberate, fully supported skyline points.
        for (int x : new int[] {2, 6}) {
            for (int z : new int[] {2, 6}) {
                // The tower cap is normally a bottom slab; make each merlon bearing full-height
                // so the accent cannot float above the slab's empty upper half.
                b.force(Phase.ROOF, x, 13, z, doubleRoofSlab(p));
                b.force(Phase.ROOF, x, 14, z, p.accent.defaultBlockState());
            }
        }
        fixture(b, m, 2, 1, 6, Blocks.FLETCHING_TABLE, new BlockPos(2, 1, 5));
        fixture(b, m, 6, 1, 6, Blocks.GRINDSTONE, new BlockPos(6, 1, 5));
        fixture(b, m, 2, 1, 3, Blocks.CHEST, new BlockPos(3, 1, 3));
        m.reserveCentralAisle(4, 1, 6);
    }

    private static void exchangeHall(Builder b, Metadata m, Materials p) {
        m.begin(VillageProsperityEngine.ProjectType.EXCHANGE_HALL, 15, 11, 12, true);
        addGabledShell(b, m, p, 5, Direction.Axis.Z, Porch.FORMAL);
        fixture(b, m, 7, 1, 7, BankerProfessionSupport.exchangeDeskOrLectern(),
                new BlockPos(7, 1, 6));
        for (int x : new int[] {2, 4, 10, 12}) {
            fixture(b, m, x, 1, 9, Blocks.BOOKSHELF, new BlockPos(x, 1, 8));
        }
        fixture(b, m, 3, 1, 7, Blocks.ENDER_CHEST, new BlockPos(3, 1, 6));
        fixture(b, m, 11, 1, 7, Blocks.BELL, new BlockPos(11, 1, 6));
        addTellerRail(b, p, 5, 9, 8);
        m.reserveCentralAisle(7, 1, 6);
    }

    private static void addGabledShell(
            Builder b,
            Metadata m,
            Materials p,
            int wallHeight,
            Direction.Axis slopeAxis,
            Porch porch) {
        addFloor(b, p, m.width, m.depth);
        int center = m.width / 2;
        Set<BlockPos> windows = commonWindows(m.width, m.depth, wallHeight);
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = 0; x < m.width; x++) {
                for (int z = 0; z < m.depth; z++) {
                    if (!edge(x, z, m.width, m.depth)) {
                        continue;
                    }
                    if (z == 0 && x == center && y <= 2) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    Block block = windows.contains(position)
                            ? Blocks.GLASS_PANE
                            : corner(x, z, m.width, m.depth) ? p.timber : p.wall;
                    b.put(windows.contains(position) ? Phase.OPENING : Phase.SHELL,
                            x, y, z, block.defaultBlockState());
                }
            }
        }
        addDoor(b, p, center, 0);
        addGableRoof(b, m, p, wallHeight, slopeAxis);
        addPorch(b, m, p, porch);
        m.wallHeight = wallHeight;
        m.enclosed = true;
        m.entranceInside = new BlockPos(center, 1, 1);
        m.interiorSamples.add(new BlockPos(center, 1, Math.max(2, m.depth / 2)));
    }

    private static void addFloor(Builder b, Materials p, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Block block = edge(x, z, width, depth) ? p.foundation : p.floor;
                b.put(Phase.FOUNDATION, x, 0, z, block.defaultBlockState());
            }
        }
    }

    private static void addMarketFloor(Builder b, Materials p, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                Block block = (x + z) % 5 == 0 ? p.accent : p.foundation;
                b.put(Phase.FOUNDATION, x, 0, z, block.defaultBlockState());
            }
        }
    }

    private static Set<BlockPos> commonWindows(int width, int depth, int wallHeight) {
        Set<BlockPos> windows = new HashSet<>();
        int[] frontX = {2, width - 3};
        int[] sideZ = {2, depth - 3};
        for (int y = 2; y <= Math.min(3, wallHeight - 1); y++) {
            for (int x : frontX) {
                windows.add(new BlockPos(x, y, 0));
                windows.add(new BlockPos(x, y, depth - 1));
            }
            for (int z : sideZ) {
                windows.add(new BlockPos(0, y, z));
                windows.add(new BlockPos(width - 1, y, z));
            }
        }
        return windows;
    }

    private static void addDoor(Builder b, Materials p, int x, int z) {
        BlockState lower = p.door.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        b.put(Phase.OPENING, x, 1, z, lower);
        b.put(Phase.OPENING, x, 2, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void addGableRoof(
            Builder b,
            Metadata m,
            Materials p,
            int wallHeight,
            Direction.Axis slopeAxis) {
        int span = slopeAxis == Direction.Axis.X ? m.width : m.depth;
        int run = slopeAxis == Direction.Axis.X ? m.depth : m.width;
        int roofY = wallHeight + 1;
        int layers = (span + 1) / 2;
        for (int layer = 0; layer < layers; layer++) {
            int first = -1 + layer;
            int second = span - layer;
            int y = roofY + layer;
            for (int along = -1; along <= run; along++) {
                if (slopeAxis == Direction.Axis.X) {
                    b.put(Phase.ROOF, first, y, along, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST));
                    b.put(Phase.ROOF, second, y, along, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.WEST));
                } else {
                    b.put(Phase.ROOF, along, y, first, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.SOUTH));
                    b.put(Phase.ROOF, along, y, second, p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH));
                }
            }
            fillGableEnds(b, m, p, slopeAxis, first + 1, second - 1, y);
        }
        int ridge = span / 2;
        int ridgeY = roofY + layers;
        for (int along = -1; along <= run; along++) {
            if (slopeAxis == Direction.Axis.X) {
                b.put(Phase.ROOF, ridge, ridgeY, along, p.roofSlab.defaultBlockState());
            } else {
                b.put(Phase.ROOF, along, ridgeY, ridge, p.roofSlab.defaultBlockState());
            }
        }
        m.roofPeak = ridgeY;
    }

    private static void fillGableEnds(
            Builder b,
            Metadata m,
            Materials p,
            Direction.Axis slopeAxis,
            int first,
            int second,
            int y) {
        if (first > second) {
            return;
        }
        for (int coordinate = Math.max(0, first);
                coordinate <= Math.min((slopeAxis == Direction.Axis.X ? m.width : m.depth) - 1, second);
                coordinate++) {
            if (slopeAxis == Direction.Axis.X) {
                b.put(Phase.SHELL, coordinate, y, 0,
                        coordinate == m.width / 2 ? p.timber.defaultBlockState() : p.wall.defaultBlockState());
                b.put(Phase.SHELL, coordinate, y, m.depth - 1,
                        coordinate == m.width / 2 ? p.timber.defaultBlockState() : p.wall.defaultBlockState());
            } else {
                b.put(Phase.SHELL, 0, y, coordinate,
                        coordinate == m.depth / 2 ? p.timber.defaultBlockState() : p.wall.defaultBlockState());
                b.put(Phase.SHELL, m.width - 1, y, coordinate,
                        coordinate == m.depth / 2 ? p.timber.defaultBlockState() : p.wall.defaultBlockState());
            }
        }
    }

    private static void addPorch(Builder b, Metadata m, Materials p, Porch style) {
        int center = m.width / 2;
        if (style == Porch.OPEN) {
            for (int x = center - 1; x <= center + 1; x++) {
                b.put(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
            }
            return;
        }
        int reach = switch (style) {
            case SMALL -> 2;
            case BROAD, FORMAL -> 3;
            case LOADING, WORKSHOP -> 4;
            default -> 2;
        };
        for (int x = center - 1; x <= center + 1; x++) {
            b.put(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[] {center - reach, center + reach}) {
            b.put(Phase.FOUNDATION, x, 0, -1, p.foundation.defaultBlockState());
            b.put(Phase.FRAME, x, 1, -1, p.timber.defaultBlockState());
            b.put(Phase.FRAME, x, 2, -1, p.timber.defaultBlockState());
        }
        for (int x = center - reach; x <= center + reach; x++) {
            int porchBack = style == Porch.SMALL ? -1 : -2;
            for (int z = porchBack; z <= -1; z++) {
                b.put(Phase.ROOF, x, 3, z, p.roofSlab.defaultBlockState());
            }
        }
        for (int x : new int[] {center - 1, center + 1}) {
            b.put(Phase.DECOR, x, 2, -1, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
        if (style == Porch.FORMAL) {
            for (int x : new int[] {center - 2, center + 2}) {
                b.force(Phase.FRAME, x, 1, -1, p.accent.defaultBlockState());
                b.force(Phase.FRAME, x, 2, -1, p.accent.defaultBlockState());
            }
        }
    }

    private static void addIntegratedChimney(
            Builder b, Metadata m, Materials p, int x, int z) {
        b.force(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
        for (int y = 1; y <= Math.min(m.roofPeak + 1, m.height); y++) {
            b.force(y <= m.wallHeight ? Phase.FRAME : Phase.ROOF,
                    x, y, z, p.chimney.defaultBlockState());
        }
    }

    private static void addBed(
            Builder b,
            Metadata m,
            int footX,
            int z,
            Direction facing,
            int accessX,
            int accessZ) {
        int headX = footX + facing.getStepX();
        int headZ = z + facing.getStepZ();
        BlockState foot = Blocks.BED.white().defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        b.put(Phase.FIXTURE, footX, 1, z, foot);
        b.put(Phase.FIXTURE, headX, 1, headZ, foot.setValue(BedBlock.PART, BedPart.HEAD));
        if (!adjacent(new BlockPos(accessX, 1, accessZ),
                new BlockPos(footX, 1, z), new BlockPos(headX, 1, headZ))) {
            throw new IllegalStateException("Bed access tile is not adjacent to its feature");
        }
        m.accessTargets.add(new BlockPos(accessX, 1, accessZ));
    }

    private static void fixture(
            Builder b,
            Metadata m,
            int x,
            int y,
            int z,
            Block block,
            BlockPos access) {
        if (!adjacent(access, new BlockPos(x, y, z))) {
            throw new IllegalStateException(
                    "Fixture access tile is not adjacent to " + block);
        }
        b.put(Phase.FIXTURE, x, y, z, block.defaultBlockState());
        m.accessTargets.add(access);
    }

    private static boolean adjacent(BlockPos access, BlockPos... features) {
        for (BlockPos feature : features) {
            int distance = Math.abs(access.getX() - feature.getX())
                    + Math.abs(access.getY() - feature.getY())
                    + Math.abs(access.getZ() - feature.getZ());
            if (distance == 1) {
                return true;
            }
        }
        return false;
    }

    private static void addTable(Builder b, Materials p, int x, int z) {
        b.put(Phase.FIXTURE, x, 1, z, p.fence.defaultBlockState());
        b.put(Phase.FIXTURE, x, 2, z, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
    }

    private static void addLongTable(Builder b, Materials p, int centerX, int z, int length) {
        for (int x = centerX - length / 2; x <= centerX + length / 2; x++) {
            b.put(Phase.FIXTURE, x, 1, z, p.fence.defaultBlockState());
            b.put(Phase.FIXTURE, x, 2, z, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
    }

    private static void addMarketStall(
            Builder b,
            Metadata m,
            Materials p,
            int centerX,
            int centerZ,
            Direction facing,
            Block workstation) {
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            for (int z : new int[] {centerZ - 2, centerZ + 2}) {
                b.put(Phase.FRAME, x, 1, z, p.timber.defaultBlockState());
                b.put(Phase.FRAME, x, 2, z, p.timber.defaultBlockState());
                b.put(Phase.FRAME, x, 3, z, p.timber.defaultBlockState());
            }
        }
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                b.put(Phase.ROOF, x, 4, z, p.roofSlab.defaultBlockState());
            }
        }
        int workZ = centerZ + (facing == Direction.NORTH ? 1 : -1);
        int accessZ = centerZ;
        fixture(b, m, centerX, 1, workZ, workstation, new BlockPos(centerX, 1, accessZ));
        b.put(Phase.DECOR, centerX, 3, centerZ, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    private static void addMarketBellArch(Builder b, Materials p, int x, int z) {
        for (int postX : new int[] {x - 2, x + 2}) {
            for (int y = 1; y <= 4; y++) {
                b.force(Phase.FRAME, postX, y, z, p.timber.defaultBlockState());
            }
        }
        for (int crossX = x - 2; crossX <= x + 2; crossX++) {
            b.force(Phase.FRAME, crossX, 4, z, p.timber.defaultBlockState());
        }
        b.put(Phase.FIXTURE, x, 3, z, Blocks.BELL.defaultBlockState()
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
    }

    private static void addTowerShell(Builder b, Metadata m, Materials p) {
        addFloor(b, p, m.width, m.depth);
        int center = m.width / 2;
        for (int y = 1; y <= 8; y++) {
            for (int x = 0; x < m.width; x++) {
                for (int z = 0; z < m.depth; z++) {
                    if (!edge(x, z, m.width, m.depth)) {
                        continue;
                    }
                    if (z == 0 && x == center && y <= 2) {
                        continue;
                    }
                    boolean arrowSlit = y == 3 && !corner(x, z, m.width, m.depth)
                            && ((x == center && (z == 0 || z == m.depth - 1))
                                    || (z == center && (x == 0 || x == m.width - 1)))
                            // The rear-center face is the ladder's structural backing. An iron-bar
                            // arrow slit here lets the ladder pop as soon as neighbour updates run.
                            && !(x == center && z == m.depth - 1);
                    b.put(arrowSlit ? Phase.OPENING : Phase.SHELL,
                            x, y, z, arrowSlit
                                    ? Blocks.IRON_BARS.defaultBlockState()
                                    : (corner(x, z, m.width, m.depth) ? p.accent : p.foundation)
                                            .defaultBlockState());
                }
            }
        }
        addDoor(b, p, center, 0);
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (!(x == center && z == m.depth - 2)) {
                    // This is the occupied lookout floor, not a rooftop ornament. Keeping it in
                    // FRAME also avoids palettes where foundation and accent intentionally alias
                    // from reclassifying the broad structural deck as unsupported finials.
                    b.put(Phase.FRAME, x, 9, z, p.foundation.defaultBlockState());
                }
            }
        }
        for (int x = 0; x < m.width; x++) {
            for (int z = 0; z < m.depth; z++) {
                if (edge(x, z, m.width, m.depth) && (x + z) % 2 == 0) {
                    b.put(Phase.ROOF, x, 10, z, p.accent.defaultBlockState());
                }
            }
        }
        for (int y = 1; y <= 8; y++) {
            b.put(Phase.FIXTURE, center, y, m.depth - 2,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.put(Phase.FIXTURE, center, 9, m.depth - 2,
                Blocks.OAK_TRAPDOOR.defaultBlockState());
        for (int x : new int[] {2, 6}) {
            for (int z : new int[] {2, 6}) {
                for (int y = 10; y <= 12; y++) {
                    b.put(Phase.FRAME, x, y, z, p.timber.defaultBlockState());
                }
            }
        }
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                b.put(Phase.ROOF, x, 13, z, p.roofSlab.defaultBlockState());
            }
        }
        for (int x = center - 1; x <= center + 1; x++) {
            b.put(Phase.FOUNDATION, x, 0, -1, p.entryStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[] {2, 6}) {
            b.put(Phase.DECOR, x, 12, 4, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true));
        }
        m.wallHeight = 8;
        m.roofPeak = 13;
        m.enclosed = true;
        m.entranceInside = new BlockPos(center, 1, 1);
        m.interiorSamples.add(new BlockPos(center, 1, 4));
        m.verticalAccess.add(new VerticalAccess(
                new BlockPos(center, 1, m.depth - 2),
                new BlockPos(center, 8, m.depth - 2)));
        m.accessTargets.add(new BlockPos(center, 10, m.depth - 3));
    }

    private static void addTellerRail(Builder b, Materials p, int minX, int maxX, int z) {
        for (int x = minX; x <= maxX; x++) {
            if (x == 7) {
                continue;
            }
            b.put(Phase.FIXTURE, x, 1, z, p.accent.defaultBlockState());
            b.put(Phase.FIXTURE, x, 2, z, Blocks.IRON_BARS.defaultBlockState());
        }
    }

    /**
     * Every current master receives a three-wide dirt-path approach. Larger civic and industrial
     * buildings then gain stone shoulders, gate lamps and one role-readable forecourt anchor.
     * This context remains legible at ordinary play distance and scales with the authored plan.
     */
    private static void appendPresentationStageOne(
            Builder stage,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale,
            String dressingId) {
        int length = switch (scale) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case LARGE -> 4;
            case LANDMARK -> 5;
        };
        int center = m.width / 2;
        // Authored thresholds project by different amounts. Begin at the nearest unoccupied
        // center tile, then back-fill the side tiles toward the door so an ordinary one-step
        // entrance never acquires a detached strip with a grass gap at z=-2.
        int firstFreeZ = -1;
        while (firstFreeZ >= -3
                && stage.isOccupied(new BlockPos(center, 0, firstFreeZ))) {
            firstFreeZ--;
        }
        // The gallery's reviewed plot pitch reserves seven blocks to the north. Keep every
        // contextual composition inside that envelope even if a future threshold projects more.
        int lastZ = Math.max(-7, firstFreeZ - length + 1);
        for (int z = -1; z >= lastZ; z--) {
            for (int x = center - 1; x <= center + 1; x++) {
                stage.putIfFree(Phase.FOUNDATION, x, 0, z,
                        Blocks.DIRT_PATH.defaultBlockState());
            }
            if (scale == VillageArchitecture.BlueprintScale.LARGE
                    || scale == VillageArchitecture.BlueprintScale.LANDMARK) {
                for (int x : new int[] {center - 2, center + 2}) {
                    stage.putIfFree(Phase.FOUNDATION, x, 0, z,
                            p.foundation.defaultBlockState());
                }
            }
            if (scale == VillageArchitecture.BlueprintScale.LANDMARK
                    && z >= firstFreeZ - 1) {
                for (int x : new int[] {center - 3, center + 3}) {
                    stage.putIfFree(Phase.FOUNDATION, x, 0, z,
                            p.accent.defaultBlockState());
                }
            }
        }

        int variant = Math.floorMod(templateId.hashCode() + dressingRichness(dressingId), 4);
        if (scale == VillageArchitecture.BlueprintScale.MEDIUM) {
            tryDressingLamp(stage,
                    center + ((variant & 1) == 0 ? -3 : 3), firstFreeZ, p);
        } else if (scale == VillageArchitecture.BlueprintScale.LARGE) {
            tryDressingLamp(stage, center - 3, firstFreeZ, p);
            tryDressingLamp(stage, center + 3, firstFreeZ, p);
            addForecourtRoleAnchor(stage, m, p, dressingId, variant, false, firstFreeZ);
        } else if (scale == VillageArchitecture.BlueprintScale.LANDMARK) {
            tryDressingLamp(stage, center - 4, firstFreeZ, p);
            tryDressingLamp(stage, center + 4, firstFreeZ, p);
            tryDressingLamp(stage, center - 3, lastZ, p);
            tryDressingLamp(stage, center + 3, lastZ, p);
            addForecourtRoleAnchor(stage, m, p, dressingId, variant, true, firstFreeZ);
        }
    }

    private static void appendPresentationStageTwo(
            Builder stage,
            Builder base,
            Builder stageOne,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale,
            String dressingId) {
        addScaleAwareProsperousInterior(stage, base, stageOne, m, p, templateId, scale);
        int apronDepth = switch (scale) {
            case SMALL -> 0;
            case MEDIUM -> 2;
            case LARGE -> 3;
            case LANDMARK -> 4;
        };
        if (apronDepth == 0) {
            return;
        }
        int halfWidth = switch (scale) {
            case SMALL, MEDIUM -> 1;
            case LARGE -> 2;
            case LANDMARK -> 3;
        };
        Block surface = switch (m.type) {
            case COTTAGE, HOUSE, INN, GRANARY -> Blocks.DIRT_PATH;
            case WAREHOUSE, SMITHY, MINE_ENTRANCE -> Blocks.GRAVEL;
            case MARKET_SQUARE, GUARD_POST, EXCHANGE_HALL -> p.foundation;
        };
        int center = m.width / 2;
        // Begin immediately outside the rear shell (depth - 1). putIfFree preserves any authored
        // canopy or service step while preventing a detached strip of grass at z=depth.
        for (int z = m.depth; z < m.depth + apronDepth; z++) {
            for (int x = center - halfWidth; x <= center + halfWidth; x++) {
                Block tile = ((x + z + templateId.hashCode()) & 3) == 0
                        && scale == VillageArchitecture.BlueprintScale.LANDMARK
                        ? p.accent
                        : surface;
                stage.putIfFree(Phase.FOUNDATION, x, 0, z, tile.defaultBlockState());
            }
        }
        int richness = dressingRichness(dressingId);
        if (scale == VillageArchitecture.BlueprintScale.LANDMARK && richness >= 1) {
            int apronEnd = m.depth + apronDepth - 1;
            tryDressingLamp(stage, center - halfWidth - 1, apronEnd, p);
            tryDressingLamp(stage, center + halfWidth + 1, apronEnd, p);
        }
    }

    /**
     * The final prosperity layer gives broad industrial and civic shells several readable work
     * zones instead of repeating one token workstation against a distant wall. The clusters are
     * discovered from the completed base and both dressing layers, so they never overwrite an
     * authored fixture or narrow a required route. A supported lantern on each admitted island
     * makes the craft legible at night while the exact lighting validator remains authoritative.
     */
    private static void addScaleAwareProsperousInterior(
            Builder stage,
            Builder base,
            Builder stageOne,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale) {
        if ("guard_gatehouse_04".equals(templateId)
                || "exchange_loggia_04".equals(templateId)
                || m.type == VillageProsperityEngine.ProjectType.COTTAGE
                || m.type == VillageProsperityEngine.ProjectType.HOUSE
                || m.type == VillageProsperityEngine.ProjectType.INN) {
            return;
        }
        int target = switch (scale) {
            case SMALL -> 1;
            case MEDIUM -> 2;
            case LARGE -> 3;
            case LANDMARK -> 5;
        };
        int placed = 0;
        int xOffset = 2 + Math.floorMod(templateId.hashCode(), 3);
        int zOffset = 2 + Math.floorMod(templateId.hashCode() >>> 3, 3);
        for (int pass = 0; pass < 2 && placed < target; pass++) {
            for (int z = 2 + ((zOffset + pass) % 3);
                    z <= m.depth - 3 && placed < target;
                    z += 3) {
                for (int x = 2 + ((xOffset + pass * 2) % 3);
                        x <= m.width - 3 && placed < target;
                        x += 3) {
                    if (!canPlaceProsperousIsland(stage, base, stageOne, m, x, z)) {
                        continue;
                    }
                    addProsperousRoleIsland(stage, m, p, x, z, placed);
                    placed++;
                }
            }
        }
        addScaleAwareInteriorBeams(stage, base, m, p, templateId, scale);
    }

    private static boolean canPlaceProsperousIsland(
            Builder stage,
            Builder base,
            Builder stageOne,
            Metadata m,
            int centerX,
            int centerZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                Cell floor = base.cellAt(new BlockPos(x, 0, z));
                if (floor == null
                        || floor.state.getBlock() instanceof SlabBlock
                        || floor.state.getBlock() instanceof StairBlock
                        || !hasAuthoredCover(base, m, x, z)) {
                    return false;
                }
                for (int y = 1; y <= 2; y++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (stage.isOccupied(position) || isProtectedCraftCell(m, position)) {
                        return false;
                    }
                }
            }
        }
        // The five role blocks and their lantern are admitted as one atomic cluster. Testing the
        // whole cluster matters: each individual crate can look harmless while the completed
        // island quietly closes the final neighbor of a barrel, bed, ladder, or workstation.
        List<BlockPos> proposedSolids = List.of(
                new BlockPos(centerX, 1, centerZ),
                new BlockPos(centerX, 2, centerZ),
                new BlockPos(centerX, 1, centerZ - 1),
                new BlockPos(centerX, 1, centerZ + 1),
                new BlockPos(centerX - 1, 1, centerZ),
                new BlockPos(centerX + 1, 1, centerZ));
        return solidPropsPreserveInteractionRoutes(
                stage, base, stageOne, m, proposedSolids);
    }

    private static void addProsperousRoleIsland(
            Builder stage, Metadata m, Materials p, int centerX, int centerZ, int variant) {
        BlockPos north = new BlockPos(centerX, 1, centerZ - 1);
        BlockPos south = new BlockPos(centerX, 1, centerZ + 1);
        BlockPos west = new BlockPos(centerX - 1, 1, centerZ);
        BlockPos east = new BlockPos(centerX + 1, 1, centerZ);
        BlockPos center = new BlockPos(centerX, 1, centerZ);
        Block rug = switch (m.type) {
            case WAREHOUSE -> Blocks.CARPET.gray();
            case GRANARY -> Blocks.CARPET.yellow();
            case SMITHY, MINE_ENTRANCE -> Blocks.CARPET.black();
            case MARKET_SQUARE -> Blocks.CARPET.green();
            case GUARD_POST -> Blocks.CARPET.red();
            case EXCHANGE_HALL -> Blocks.CARPET.blue();
            default -> Blocks.CARPET.brown();
        };
        for (int dx : new int[] {-1, 1}) {
            for (int dz : new int[] {-1, 1}) {
                stage.put(Phase.DECOR, centerX + dx, 1, centerZ + dz,
                        rug.defaultBlockState());
            }
        }

        // The full center block is both a visual anchor and a guaranteed sturdy lantern mount.
        stage.put(Phase.FIXTURE, center.getX(), center.getY(), center.getZ(),
                p.accent.defaultBlockState());
        stage.put(Phase.DECOR, centerX, 2, centerZ,
                Blocks.LANTERN.defaultBlockState());
        switch (m.type) {
            case WAREHOUSE -> {
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        p.wall.defaultBlockState());
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        Blocks.HAY_BLOCK.defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        p.timber.defaultBlockState().setValue(
                                RotatedPillarBlock.AXIS, Direction.Axis.Z));
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        ((variant & 1) == 0 ? Blocks.HAY_BLOCK : Blocks.BOOKSHELF)
                                .defaultBlockState());
            }
            case GRANARY -> {
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        Blocks.HAY_BLOCK.defaultBlockState().setValue(
                                RotatedPillarBlock.AXIS, Direction.Axis.X));
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        p.wall.defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        Blocks.HAY_BLOCK.defaultBlockState());
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        Blocks.HAY_BLOCK.defaultBlockState().setValue(
                                RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
            case SMITHY -> {
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        Blocks.IRON_BLOCK.defaultBlockState());
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        Blocks.RAW_IRON_BLOCK.defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        Blocks.ANVIL.defaultBlockState());
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState());
            }
            case MINE_ENTRANCE -> {
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        Blocks.RAW_IRON_BLOCK.defaultBlockState());
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        Blocks.COAL_BLOCK.defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        Blocks.RAW_COPPER_BLOCK.defaultBlockState());
            }
            case MARKET_SQUARE -> {
                Block marketTrade = switch (Math.floorMod(variant, 4)) {
                    case 0 -> Blocks.CRAFTING_TABLE;
                    case 1 -> Blocks.HAY_BLOCK;
                    case 2 -> Blocks.BOOKSHELF;
                    default -> Blocks.NOTE_BLOCK;
                };
                Block leftBale = switch (Math.floorMod(variant, 4)) {
                    case 0 -> Blocks.WOOL.red();
                    case 1 -> Blocks.WOOL.green();
                    case 2 -> Blocks.WOOL.gray();
                    default -> Blocks.WOOL.blue();
                };
                Block rightBale = switch (Math.floorMod(variant, 4)) {
                    case 0 -> Blocks.WOOL.yellow();
                    case 1 -> Blocks.WOOL.white();
                    case 2 -> Blocks.WOOL.black();
                    default -> Blocks.WOOL.brown();
                };
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        marketTrade.defaultBlockState());
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        p.wall.defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        leftBale.defaultBlockState());
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        rightBale.defaultBlockState());
            }
            case GUARD_POST -> {
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        ((variant & 1) == 0
                                        ? Blocks.CHISELED_BOOKSHELF
                                        : Blocks.TARGET)
                                .defaultBlockState());
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        ((variant & 1) == 0 ? Blocks.TARGET : Blocks.IRON_BLOCK)
                                .defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        p.roofStairs.defaultBlockState().setValue(
                                StairBlock.FACING, Direction.EAST));
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        p.roofStairs.defaultBlockState().setValue(
                                StairBlock.FACING, Direction.WEST));
            }
            case EXCHANGE_HALL -> {
                stage.put(Phase.DECOR, north.getX(), north.getY(), north.getZ(),
                        ((variant & 1) == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF)
                                .defaultBlockState());
                stage.put(Phase.DECOR, south.getX(), south.getY(), south.getZ(),
                        ((variant & 1) == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF)
                                .defaultBlockState());
                stage.put(Phase.DECOR, west.getX(), west.getY(), west.getZ(),
                        p.roofStairs.defaultBlockState().setValue(
                                StairBlock.FACING, Direction.EAST));
                stage.put(Phase.DECOR, east.getX(), east.getY(), east.getZ(),
                        p.roofStairs.defaultBlockState().setValue(
                                StairBlock.FACING, Direction.WEST));
            }
            default -> {
                // Residential roles use individually authored programs above, never this branch.
            }
        }
    }

    private static void addScaleAwareInteriorBeams(
            Builder stage,
            Builder base,
            Metadata m,
            Materials p,
            String templateId,
            VillageArchitecture.BlueprintScale scale) {
        int target = switch (scale) {
            case SMALL -> 0;
            case MEDIUM -> 1;
            case LARGE -> 2;
            case LANDMARK -> 3;
        };
        int beamY = Math.max(3, Math.min(m.wallHeight, 5));
        int placed = 0;
        for (int z = 2; z <= m.depth - 3 && placed < target; z += 3) {
            if (!canPlaceInteriorBeamX(stage, base, m, beamY, z)) {
                continue;
            }
            for (int x = 1; x < m.width - 1; x++) {
                stage.put(Phase.FRAME, x, beamY, z,
                        p.timber.defaultBlockState().setValue(
                                RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
            tryAddBeamPendant(stage, m, m.width / 2, beamY, z);
            placed++;
        }
    }

    private static boolean canPlaceInteriorBeamX(
            Builder stage, Builder base, Metadata m, int beamY, int z) {
        Cell leftBearing = base.cellAt(new BlockPos(0, beamY, z));
        Cell rightBearing = base.cellAt(new BlockPos(m.width - 1, beamY, z));
        if (!isStructuralBearing(leftBearing) || !isStructuralBearing(rightBearing)) {
            return false;
        }
        for (int x = 1; x < m.width - 1; x++) {
            if (stage.isOccupied(new BlockPos(x, beamY, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStructuralBearing(Cell cell) {
        return cell != null
                && (cell.phase == Phase.SHELL
                        || cell.phase == Phase.FRAME
                        || cell.phase == Phase.ROOF);
    }

    private static void tryAddBeamPendant(
            Builder stage, Metadata m, int x, int beamY, int z) {
        if (beamY < 4) {
            return;
        }
        BlockPos chain = new BlockPos(x, beamY - 1, z);
        BlockPos lantern = new BlockPos(x, beamY - 2, z);
        if (stage.isOccupied(chain)
                || stage.isOccupied(lantern)
                || isProtectedCraftCell(m, chain)
                || isProtectedCraftCell(m, lantern)) {
            return;
        }
        stage.put(Phase.FRAME, chain.getX(), chain.getY(), chain.getZ(),
                Blocks.IRON_CHAIN.defaultBlockState());
        stage.put(Phase.DECOR, lantern.getX(), lantern.getY(), lantern.getZ(),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void addForecourtRoleAnchor(
            Builder stage,
            Metadata m,
            Materials p,
            String dressingId,
            int variant,
            boolean paired,
            int forecourtZ) {
        int richness = dressingRichness(dressingId);
        Block left = switch (m.type) {
            case COTTAGE, HOUSE -> dressingPlant(richness);
            case INN, GRANARY -> Blocks.HAY_BLOCK;
            case WAREHOUSE -> p.timber;
            case SMITHY, MINE_ENTRANCE -> Blocks.COAL_BLOCK;
            case MARKET_SQUARE -> Blocks.MELON;
            case GUARD_POST -> Blocks.TARGET;
            case EXCHANGE_HALL -> Blocks.BOOKSHELF;
        };
        Block right = switch (m.type) {
            case COTTAGE, HOUSE, EXCHANGE_HALL -> dressingPlant(richness);
            case INN, GRANARY -> Blocks.HAY_BLOCK;
            case WAREHOUSE, MARKET_SQUARE -> p.wall;
            case SMITHY -> p.chimney;
            case MINE_ENTRANCE -> Blocks.DEEPSLATE;
            case GUARD_POST -> Blocks.TARGET;
        };
        int leftX = 1 + (variant & 1);
        int rightX = m.width - 2 - (variant & 1);
        boolean leftPlant = m.type == VillageProsperityEngine.ProjectType.COTTAGE
                || m.type == VillageProsperityEngine.ProjectType.HOUSE;
        tryForecourtMicroScene(
                stage, m, p, leftX, forecourtZ, left, leftPlant, variant);
        if (paired) {
            boolean rightPlant = m.type == VillageProsperityEngine.ProjectType.COTTAGE
                    || m.type == VillageProsperityEngine.ProjectType.HOUSE
                    || m.type == VillageProsperityEngine.ProjectType.EXCHANGE_HALL;
            tryForecourtMicroScene(
                    stage, m, p, rightX, forecourtZ, right, rightPlant, variant + 1);
        }
    }

    /**
     * A forecourt cue is a two-piece vignette rather than a valuable or job-site block dropped on
     * a pedestal. The secondary piece always grows away from the centered three-wide approach.
     */
    private static boolean tryForecourtMicroScene(
            Builder stage,
            Metadata m,
            Materials p,
            int x,
            int z,
            Block prop,
            boolean planted,
            int variant) {
        int outward = x < m.width / 2 ? -1 : 1;
        int companionX = x + outward;
        int companionZ = z + ((variant & 2) == 0 ? -1 : 1);
        int outerX = companionX + outward;
        int wearZ = Math.min(z, companionZ) - 1;
        int minSceneX = Math.min(x, outerX);
        int maxSceneX = Math.max(x, outerX);
        List<BlockPos> occupied = new ArrayList<>();
        for (int sceneX = minSceneX; sceneX <= maxSceneX; sceneX++) {
            occupied.add(new BlockPos(sceneX, 0, wearZ));
            occupied.add(new BlockPos(sceneX, 1, wearZ));
        }
        for (int[] footprint : new int[][] {
                {x, z}, {companionX, companionZ}, {outerX, companionZ}
        }) {
            occupied.add(new BlockPos(footprint[0], 0, footprint[1]));
            occupied.add(new BlockPos(footprint[0], 1, footprint[1]));
            occupied.add(new BlockPos(footprint[0], 2, footprint[1]));
        }
        if (planted) {
            // The tall inner planter, middle pot, and low shrub make a deliberate three-step bed.
            occupied.add(new BlockPos(x, 3, z));
        } else {
            // Cargo rises from one coherent pallet and every full parcel receives a top strap.
            occupied.add(new BlockPos(x, 3, z));
            occupied.add(new BlockPos(companionX, 3, companionZ));
            occupied.add(new BlockPos(outerX, 3, companionZ));
        }
        if (!canPlaceDoodad(stage, m, occupied)) {
            return false;
        }
        Direction edgingFacing = companionZ < z ? Direction.SOUTH : Direction.NORTH;
        for (int sceneX = minSceneX; sceneX <= maxSceneX; sceneX++) {
            stage.put(Phase.FOUNDATION, sceneX, 0, wearZ,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.put(Phase.DECOR, sceneX, 1, wearZ,
                    sceneX == companionX
                            ? p.roofStairs.defaultBlockState().setValue(
                                    StairBlock.FACING, edgingFacing)
                            : p.roofSlab.defaultBlockState());
        }
        stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
        stage.put(Phase.FOUNDATION, companionX, 0, companionZ,
                ((variant & 1) == 0 ? p.accent : p.foundation).defaultBlockState());
        stage.put(Phase.FOUNDATION, outerX, 0, companionZ,
                (planted ? Blocks.MOSS_BLOCK : p.foundation).defaultBlockState());
        if (planted) {
            stage.put(Phase.DECOR, x, 1, z, p.wall.defaultBlockState());
            stage.put(Phase.DECOR, x, 2, z, p.accent.defaultBlockState());
            stage.put(Phase.DECOR, x, 3, z, prop.defaultBlockState());
            stage.put(Phase.DECOR, companionX, 1, companionZ,
                    p.wall.defaultBlockState());
            stage.put(Phase.DECOR, companionX, 2, companionZ,
                    dressingPlant(Math.floorMod(variant + 1, 3)).defaultBlockState());
            stage.put(Phase.DECOR, outerX, 1, companionZ,
                    ((variant & 1) == 0
                                    ? Blocks.FLOWERING_AZALEA
                                    : Blocks.AZALEA)
                            .defaultBlockState());
        } else {
            for (int[] pallet : new int[][] {
                    {x, z}, {companionX, companionZ}, {outerX, companionZ}
            }) {
                stage.put(Phase.FRAME, pallet[0], 1, pallet[1], upperRoofSlab(p));
            }
            stage.put(Phase.DECOR, x, 2, z, prop.defaultBlockState());
            stage.put(Phase.DECOR, x, 3, z, Blocks.RAIL.defaultBlockState());
            stage.put(Phase.DECOR, companionX, 2, companionZ,
                    p.wall.defaultBlockState());
            stage.put(Phase.DECOR, companionX, 3, companionZ,
                    Blocks.RAIL.defaultBlockState());
            stage.put(Phase.DECOR, outerX, 2, companionZ,
                    p.timber.defaultBlockState().setValue(
                            RotatedPillarBlock.AXIS, Direction.Axis.Z));
            stage.put(Phase.DECOR, outerX, 3, companionZ,
                    Blocks.RAIL.defaultBlockState());
        }
        return true;
    }

    private static boolean tryDressingPedestal(
            Builder stage, int x, int z, Block foundation, Block prop) {
        BlockPos floor = new BlockPos(x, 0, z);
        BlockPos feature = floor.above();
        if (stage.isOccupied(floor) || stage.isOccupied(feature)) {
            return false;
        }
        stage.put(Phase.FOUNDATION, x, 0, z, foundation.defaultBlockState());
        stage.put(Phase.DECOR, x, 1, z, prop.defaultBlockState());
        return true;
    }

    private static boolean tryDressingLamp(Builder stage, int x, int z, Materials p) {
        int armStepX = ((x + z) & 1) == 0 ? (x < 0 ? 1 : -1) : 0;
        int armStepZ = armStepX == 0 ? -1 : 0;
        int armX = x + armStepX;
        int armZ = z + armStepZ;
        for (int y = 0; y <= 4; y++) {
            if (stage.isOccupied(new BlockPos(x, y, z))) {
                return false;
            }
        }
        if (stage.isOccupied(new BlockPos(armX, 2, armZ))
                || stage.isOccupied(new BlockPos(armX, 3, armZ))
                || stage.isOccupied(new BlockPos(armX, 4, armZ))) {
            return false;
        }
        stage.put(Phase.FOUNDATION, x, 0, z, p.accent.defaultBlockState());
        stage.put(Phase.FRAME, x, 1, z, p.fence.defaultBlockState());
        stage.put(Phase.FRAME, x, 2, z, p.fence.defaultBlockState());
        Direction armDirection = armStepX < 0
                ? Direction.WEST
                : armStepX > 0 ? Direction.EAST : Direction.NORTH;
        Direction.Axis armAxis = armX == x ? Direction.Axis.Z : Direction.Axis.X;
        stage.put(Phase.FRAME, x, 3, z, p.roofStairs.defaultBlockState()
                .setValue(StairBlock.FACING, armDirection));
        // The stair knee keeps the post slim; a full vertical timber cap gives the single one-cell log
        // arm a real load path before it carries the hanging chain. The earlier top slab looked light,
        // but its half-height contact could leave the arm structurally disconnected.
        stage.put(Phase.FRAME, x, 4, z, p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        stage.put(Phase.FRAME, armX, 4, armZ, p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, armAxis));
        stage.put(Phase.FRAME, armX, 3, armZ, Blocks.IRON_CHAIN.defaultBlockState());
        stage.put(Phase.DECOR, armX, 2, armZ, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        int footX = x - armStepZ;
        int footZ = z + armStepX;
        stage.putIfFree(Phase.FOUNDATION, footX, 0, footZ,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, footX, 1, footZ,
                p.roofSlab.defaultBlockState());
        return true;
    }

    private static void appendDressingStageOne(
            Builder stage, Metadata m, Materials p, String dressingId) {
        // These are deliberate exterior scenes, not unbounded scatter. Side-yard placement leaves
        // the three-wide centered public approach untouched and is selected entirely from frozen
        // blueprint metadata plus the persisted dressing id.
        switch (m.type) {
            case COTTAGE -> addCottageYardDoodads(stage, m, p, dressingId, false);
            case HOUSE -> addHouseYardDoodads(stage, m, p, dressingId, false);
            case INN -> addInnYardDoodads(stage, m, p, dressingId, false);
            case WAREHOUSE -> addWarehouseYardDoodads(stage, m, p, dressingId, false);
            case GRANARY -> addGranaryYardDoodads(stage, m, p, dressingId, false);
            case SMITHY -> addSmithyYardDoodads(stage, m, p, dressingId, false);
            case MINE_ENTRANCE -> addMineYardDoodads(stage, m, p, dressingId, false);
            case MARKET_SQUARE -> addMarketYardDoodads(stage, m, p, dressingId, false);
            case GUARD_POST -> addGuardYardDoodads(stage, m, p, dressingId, false);
            case EXCHANGE_HALL -> addExchangeYardDoodads(stage, m, p, dressingId, false);
        }
    }

    private static void appendDressingStageTwo(
            Builder stage, Metadata m, Materials p, String dressingId) {
        // The second append-only layer grows a rear service yard. It is intentionally separated
        // from doors, interior access targets, ladders and the front trail connection.
        switch (m.type) {
            case COTTAGE -> addCottageYardDoodads(stage, m, p, dressingId, true);
            case HOUSE -> addHouseYardDoodads(stage, m, p, dressingId, true);
            case INN -> addInnYardDoodads(stage, m, p, dressingId, true);
            case WAREHOUSE -> addWarehouseYardDoodads(stage, m, p, dressingId, true);
            case GRANARY -> addGranaryYardDoodads(stage, m, p, dressingId, true);
            case SMITHY -> addSmithyYardDoodads(stage, m, p, dressingId, true);
            case MINE_ENTRANCE -> addMineYardDoodads(stage, m, p, dressingId, true);
            case MARKET_SQUARE -> addMarketYardDoodads(stage, m, p, dressingId, true);
            case GUARD_POST -> addGuardYardDoodads(stage, m, p, dressingId, true);
            case EXCHANGE_HALL -> addExchangeYardDoodads(stage, m, p, dressingId, true);
        }
    }

    private static int dressingRichness(String dressingId) {
        return switch (dressingId) {
            case "restrained" -> 0;
            case "lived_in" -> 1;
            case "prosperous" -> 2;
            default -> throw new IllegalArgumentException("Unknown Blueprint V2 dressing " + dressingId);
        };
    }

    private static int doodadSideX(Metadata m, boolean right) {
        // Grand masters may project bays, loggias or machinery two blocks beyond their nominal
        // shell. Keep the optional yard scene one clear cell beyond that architectural envelope.
        return right ? m.width + 3 : -4;
    }

    private static Block dressingPlant(int richness) {
        return switch (richness) {
            case 0 -> Blocks.POTTED_FERN;
            case 1 -> Blocks.POTTED_DANDELION;
            case 2 -> Blocks.POTTED_BLUE_ORCHID;
            default -> throw new IllegalArgumentException("Unknown Blueprint V2 dressing richness " + richness);
        };
    }

    private static void putDressingPedestal(
            Builder stage, int x, int z, Block foundation, Block prop) {
        stage.put(Phase.FOUNDATION, x, 0, z, foundation.defaultBlockState());
        stage.put(Phase.DECOR, x, 1, z, prop.defaultBlockState());
    }

    private static void putDressingPile(
            Builder stage,
            int x,
            int z,
            Block foundation,
            Block prop,
            boolean stacked) {
        putDressingPedestal(stage, x, z, foundation, prop);
        if (stacked) {
            stage.put(Phase.DECOR, x, 2, z, prop.defaultBlockState());
        }
    }

    private static void putDressingLamp(Builder stage, int x, int z, Materials p) {
        tryDressingLamp(stage, x, z, p);
    }

    private static void putDressingBenchX(
            Builder stage, int minX, int z, Materials p) {
        for (int x = minX; x <= minX + 1; x++) {
            stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            stage.put(Phase.DECOR, x, 1, z, p.roofSlab.defaultBlockState());
        }
    }

    private static void putDressingBenchZ(
            Builder stage, int x, int minZ, Materials p) {
        for (int z = minZ; z <= minZ + 1; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            stage.put(Phase.DECOR, x, 1, z, p.roofSlab.defaultBlockState());
        }
    }

    private static void addCottageYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 103 : 101, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            if ((variant & 2) == 0) {
                addStackedLogRackZ(stage, p, side, 1, 3, richness >= 1 ? 2 : 1, true);
            } else {
                addDoodadBenchZ(stage, p, side, 1, 3, side < 0 ? -1 : 1);
                putDressingPedestal(stage, otherSide, 1, p.accent, dressingPlant(richness));
            }
            if (richness >= 1 && (variant & 2) == 0) {
                addPlanterRunZ(stage, p, otherSide, 1, 2, richness);
            }
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        if ((variant & 1) == 0) {
            addSafeCampfireNook(stage, p, m.width / 2, m.depth + 2, richness);
        } else {
            addGardenWorkCorner(stage, p, m.width / 2, m.depth + 1, richness);
        }
    }

    private static void addHouseYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 113 : 109, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addDoodadBenchZ(stage, p, side, 1, 3, side < 0 ? -1 : 1);
            addPlanterRunZ(stage, p, otherSide, 1, richness >= 1 ? 2 : 1, richness);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        if ((variant & 2) == 0) {
            addSafeCampfireNook(stage, p, m.width / 2, m.depth + 2, richness);
        } else {
            addRearHandCart(stage, m, p, dressingId, 149, Blocks.PUMPKIN);
            if (richness >= 1) {
                addPlanterRunX(stage, p, 1, m.depth + 1, 2, richness);
            }
            if (richness >= 2) {
                putDressingLamp(stage, m.width - 2, m.depth + 1, p);
            }
        }
    }

    private static void addInnYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 127 : 119, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addHitchingRailZ(stage, p, side, 1, 3);
            addCrateCluster(stage, p, otherSide, 1, richness >= 1);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        addSafeCampfireNook(stage, p, m.width / 2, m.depth + 2, richness);
        addCrateCluster(stage, p, 2 + (variant & 1), m.depth + 1, richness >= 1);
        if (richness >= 2) {
            addPlanterRunX(stage, p, m.width - 4, m.depth + 1, 2, richness);
        }
    }

    private static void addWarehouseYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 139 : 131, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addStackedLogRackZ(stage, p, side, 1, 4, richness >= 1 ? 2 : 1, true);
            addCrateCluster(stage, p, otherSide, 1, richness >= 1);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        addRearHandCart(stage, m, p, dressingId, 157,
                (variant & 2) == 0 ? Blocks.PUMPKIN : Blocks.HAY_BLOCK);
        addCrateCluster(stage, p, 1, m.depth + 1, richness >= 1);
        if (richness >= 2) {
            addCrateCluster(stage, p, m.width - 3, m.depth + 1, true);
        }
    }

    private static void addGranaryYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 151 : 149, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addFeedTroughZ(stage, p, side, 1, richness >= 1 ? 3 : 2);
            addHayPile(stage, p, otherSide, 1, richness);
            if (richness >= 2) {
                putDressingLamp(stage, side, 4, p);
            }
            return;
        }
        addRearHandCart(stage, m, p, dressingId, 163, Blocks.HAY_BLOCK);
        addHayPile(stage, p, 1 + (variant & 1), m.depth + 1, richness);
        if (richness >= 1) {
            addCrateCluster(stage, p, m.width - 3, m.depth + 1, richness >= 2);
        }
    }

    private static void addSmithyYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 163 : 157, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addToolRackZ(stage, p, side, 1, 3);
            addMaterialPile(stage, p, otherSide, 1, Blocks.COAL_BLOCK, richness);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        addRearHandCart(stage, m, p, dressingId, 173, Blocks.COAL_BLOCK);
        addMaterialPile(stage, p, 2 + (variant & 1), m.depth + 1, p.chimney, richness);
        if (richness >= 1) {
            addCrateCluster(stage, p, m.width - 3, m.depth + 1, richness >= 2);
        }
    }

    private static void addMineYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 179 : 167, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            // The rail-bound stack echoes the reference log pile while remaining a grounded,
            // one-cell-wide side-yard composition outside the mine's reserved track aisle.
            addStackedLogRackZ(stage, p, side, 1, 4, richness >= 1 ? 2 : 1, true);
            addMaterialPile(stage, p, otherSide, 1, Blocks.COAL_BLOCK, richness);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        addToolRackX(stage, p, 1, m.depth + 1, 3);
        addRearHandCart(stage, m, p, dressingId, 181,
                (variant & 2) == 0 ? Blocks.RAW_IRON_BLOCK : Blocks.COAL_BLOCK);
        if (richness >= 2) {
            addCrateCluster(stage, p, m.width - 3, m.depth + 1, true);
        }
    }

    private static void addMarketYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 193 : 181, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addFeedTroughZ(stage, p, side, 1, 2);
            addCrateCluster(stage, p, otherSide, 1, richness >= 1);
            if (richness >= 1) {
                putDressingPedestal(stage, otherSide, 4, p.accent, dressingPlant(richness));
            }
            if (richness >= 2) {
                putDressingLamp(stage, side, 4, p);
            }
            return;
        }
        addDoodadBenchX(stage, p, 1, m.depth + 1, 3, 1);
        addRearHandCart(stage, m, p, dressingId, 191,
                (variant & 2) == 0 ? Blocks.HAY_BLOCK : Blocks.MELON);
        if (richness >= 1) {
            addPlanterRunX(stage, p, m.width - 4, m.depth + 1, 2, richness);
        }
    }

    private static void addGuardYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 211 : 197, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addTargetRackZ(stage, p, side, 1);
            addCrateCluster(stage, p, otherSide, 1, richness >= 1);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 4, p);
            }
            return;
        }
        if ((variant & 2) == 0) {
            addSafeCampfireNook(stage, p, m.width / 2, m.depth + 2, richness);
        } else {
            addTargetRackX(stage, p, 1, m.depth + 1);
            addToolRackX(stage, p, m.width - 4, m.depth + 1, 3);
            if (richness >= 1) {
                putDressingLamp(stage, m.width / 2, m.depth + 2, p);
            }
        }
    }

    private static void addExchangeYardDoodads(
            Builder stage, Metadata m, Materials p, String dressingId, boolean rear) {
        int richness = dressingRichness(dressingId);
        int variant = deterministicDoodadVariant(m, dressingId, rear ? 227 : 223, 4);
        int side = doodadSideX(m, (variant & 1) != 0);
        int otherSide = doodadSideX(m, side < 0);
        if (!rear) {
            addDoodadBenchZ(stage, p, side, 1, 3, side < 0 ? -1 : 1);
            addPlanterRunZ(stage, p, otherSide, 1, 3, richness);
            if (richness >= 2) {
                putDressingLamp(stage, otherSide, 5, p);
            }
            return;
        }
        if ((variant & 2) == 0) {
            addDoodadBenchX(stage, p, m.width / 2 - 1, m.depth + 1, 3, 1);
            addPlanterRunX(stage, p, 2, m.depth + 1, 2, richness);
            addPlanterRunX(stage, p, m.width - 4, m.depth + 1, 2, richness);
        } else {
            addRearHandCart(stage, m, p, dressingId, 211, Blocks.BOOKSHELF);
            addPlanterRunX(stage, p, 2, m.depth + 1, 2, richness);
        }
        if (richness >= 2) {
            putDressingLamp(stage, m.width - 2, m.depth + 1, p);
        }
    }

    private static int deterministicDoodadVariant(
            Metadata m, String dressingId, int salt, int variants) {
        if (variants <= 0) {
            throw new IllegalArgumentException("Doodad variant count must be positive");
        }
        int seed = 17;
        seed = seed * 31 + m.type.ordinal();
        seed = seed * 31 + m.width;
        seed = seed * 31 + m.depth;
        seed = seed * 31 + m.height;
        seed = seed * 31 + Long.hashCode(m.doodadSeed);
        seed = seed * 31 + dressingRichness(dressingId);
        seed = seed * 31 + dressingId.hashCode();
        seed ^= salt * 0x45d9f3b;
        return Math.floorMod(seed, variants);
    }

    private static void addStackedLogRackZ(
            Builder stage,
            Materials p,
            int x,
            int minZ,
            int length,
            int tiers,
            boolean railBound) {
        // Crosswise billets expose their cut ends on both long faces; the previous lengthwise logs
        // read as an undifferentiated wall from the yard camera.
        BlockState log = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        int top = Math.max(1, tiers);
        for (int z = minZ; z < minZ + length; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z,
                    ((z - minZ) & 1) == 0
                            ? p.foundation.defaultBlockState()
                            : Blocks.DIRT_PATH.defaultBlockState());
            int stackHeight = top > 1 && z > minZ && z < minZ + length - 1
                    ? top
                    : 1;
            for (int y = 1; y <= stackHeight; y++) {
                stage.put(Phase.FRAME, x, y, z, log);
            }
            if (railBound) {
                // The supported rails rise and fall over the staggered upper course like broad
                // iron binding straps instead of becoming an unrelated floating track.
                stage.put(Phase.DECOR, x, stackHeight + 1, z,
                        Blocks.RAIL.defaultBlockState());
            }
        }
        int outwardX = x < 0 ? x - 1 : x + 1;
        int inwardX = x < 0 ? x + 1 : x - 1;
        Direction inwardFacing = x < 0 ? Direction.EAST : Direction.WEST;
        // Paired end stops and unequal stair knees create a readable diagonal rack frame without
        // enclosing the exposed log ends behind a broad solid side wall.
        for (int z : new int[] {minZ, minZ + length - 1}) {
            stage.putIfFree(Phase.FOUNDATION, outwardX, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, outwardX, 1, z, p.fence.defaultBlockState());
            stage.putIfFree(Phase.DECOR, outwardX, 2, z,
                    p.roofStairs.defaultBlockState().setValue(
                            StairBlock.FACING, inwardFacing));
            stage.putIfFree(Phase.FOUNDATION, inwardX, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, inwardX, 1, z,
                    p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING, inwardFacing.getOpposite()));
        }
        int firstStrapZ = minZ + Math.min(1, length - 1);
        int lastStrapZ = minZ + Math.max(0, length - 2);
        for (int strapZ : new int[] {firstStrapZ, lastStrapZ}) {
            stage.putIfFree(Phase.FOUNDATION, outwardX, 0, strapZ,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, outwardX, 1, strapZ,
                    Blocks.IRON_BARS.defaultBlockState());
            if (top > 1) {
                stage.putIfFree(Phase.FRAME, outwardX, 2, strapZ,
                        Blocks.IRON_BARS.defaultBlockState());
            }
        }
    }

    private static void addDoodadBenchX(
            Builder stage, Materials p, int minX, int z, int length, int backOffset) {
        Direction facing = backOffset > 0 ? Direction.NORTH : Direction.SOUTH;
        for (int x = minX; x < minX + length; x++) {
            stage.put(Phase.FOUNDATION, x, 0, z,
                    x == minX || x == minX + length - 1
                            ? p.foundation.defaultBlockState()
                            : Blocks.DIRT_PATH.defaultBlockState());
            stage.put(Phase.DECOR, x, 1, z,
                    x == minX || x == minX + length - 1
                            ? p.roofStairs.defaultBlockState().setValue(
                                    StairBlock.FACING, facing)
                            : p.roofSlab.defaultBlockState());
        }
        for (int x : new int[] {minX, minX + length - 1}) {
            stage.put(Phase.FOUNDATION, x, 0, z + backOffset,
                    p.foundation.defaultBlockState());
            stage.put(Phase.FRAME, x, 1, z + backOffset, p.fence.defaultBlockState());
            stage.put(Phase.FRAME, x, 2, z, p.fence.defaultBlockState());
            // Keep the light bench cap in open yards, but close its upper half when an authored
            // canopy already occupies the cell above. This prevents a half-block daylight seam
            // (and the corresponding unsupported-roof rejection) where the two details meet.
            BlockState cap = stage.isOccupied(new BlockPos(x, 4, z))
                    ? doubleRoofSlab(p)
                    : p.roofSlab.defaultBlockState();
            stage.putIfFree(Phase.DECOR, x, 3, z, cap);
        }
        BlockState back = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x : new int[] {minX, minX + length - 1}) {
            stage.put(Phase.FRAME, x, 2, z + backOffset, back);
        }
        for (int x = minX + 1; x < minX + length - 1; x++) {
            stage.putIfFree(Phase.FRAME, x, 2, z + backOffset,
                    openDoodadTrapdoor(p, facing));
        }
        int armOffset = backOffset > 0 ? -1 : 1;
        stage.putIfFree(Phase.FOUNDATION, minX - 1, 0, z,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX - 1, 1, z,
                p.wall.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX - 1, 2, z,
                dressingPlant(Math.floorMod(minX + z, 3)).defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, minX + length, 0, z + armOffset,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.FRAME, minX + length, 1, z + armOffset,
                p.fence.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX + length, 2, z + armOffset,
                upperRoofSlab(p));
    }

    private static void addDoodadBenchZ(
            Builder stage, Materials p, int x, int minZ, int length, int backOffset) {
        Direction facing = backOffset > 0 ? Direction.WEST : Direction.EAST;
        for (int z = minZ; z < minZ + length; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z,
                    z == minZ || z == minZ + length - 1
                            ? p.foundation.defaultBlockState()
                            : Blocks.DIRT_PATH.defaultBlockState());
            stage.put(Phase.DECOR, x, 1, z,
                    z == minZ || z == minZ + length - 1
                            ? p.roofStairs.defaultBlockState().setValue(
                                    StairBlock.FACING, facing)
                            : p.roofSlab.defaultBlockState());
        }
        for (int z : new int[] {minZ, minZ + length - 1}) {
            stage.put(Phase.FOUNDATION, x + backOffset, 0, z,
                    p.foundation.defaultBlockState());
            stage.put(Phase.FRAME, x + backOffset, 1, z, p.fence.defaultBlockState());
            stage.put(Phase.FRAME, x, 2, z, p.fence.defaultBlockState());
            BlockState cap = stage.isOccupied(new BlockPos(x, 4, z))
                    ? doubleRoofSlab(p)
                    : p.roofSlab.defaultBlockState();
            stage.putIfFree(Phase.DECOR, x, 3, z, cap);
        }
        BlockState back = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z : new int[] {minZ, minZ + length - 1}) {
            stage.put(Phase.FRAME, x + backOffset, 2, z, back);
        }
        for (int z = minZ + 1; z < minZ + length - 1; z++) {
            stage.putIfFree(Phase.FRAME, x + backOffset, 2, z,
                    openDoodadTrapdoor(p, facing));
        }
        int armOffset = backOffset > 0 ? -1 : 1;
        stage.putIfFree(Phase.FOUNDATION, x, 0, minZ - 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 1, minZ - 1,
                p.wall.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 2, minZ - 1,
                dressingPlant(Math.floorMod(x + minZ, 3)).defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, x + armOffset, 0, minZ + length,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.FRAME, x + armOffset, 1, minZ + length,
                p.fence.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x + armOffset, 2, minZ + length,
                upperRoofSlab(p));
    }

    private static void addSafeCampfireNook(
            Builder stage, Materials p, int centerX, int fireZ, int richness) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = centerX + dx;
                int z = fireZ + dz;
                stage.put(Phase.FOUNDATION, x, 0, z,
                        dx == 0 || dz == 0
                                ? p.foundation.defaultBlockState()
                                : Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        for (int[] curb : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            Direction facing = curb[0] < 0
                    ? Direction.EAST
                    : curb[0] > 0
                            ? Direction.WEST
                            : curb[1] < 0 ? Direction.SOUTH : Direction.NORTH;
            stage.put(Phase.DECOR, centerX + curb[0], 1, fireZ + curb[1],
                    p.entryStairs.defaultBlockState().setValue(StairBlock.FACING, facing));
        }
        for (int[] corner : new int[][] {{-1, -1}, {1, 1}}) {
            stage.put(Phase.DECOR, centerX + corner[0], 1, fireZ + corner[1],
                    p.roofSlab.defaultBlockState());
        }
        stage.put(Phase.DECOR, centerX, 1, fireZ, Blocks.CAMPFIRE.defaultBlockState());
        // Two offset timber stools preserve open sightlines across the fire while the backed rear
        // bench makes the nook feel furnished rather than ringed by anonymous slabs.
        for (int stoolX : new int[] {centerX - 3, centerX + 3}) {
            stage.putIfFree(Phase.FOUNDATION, stoolX, 0, fireZ,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.DECOR, stoolX, 1, fireZ,
                    p.timber.defaultBlockState().setValue(
                            RotatedPillarBlock.AXIS, Direction.Axis.Y));
            stage.putIfFree(Phase.DECOR, stoolX, 2, fireZ,
                    p.roofSlab.defaultBlockState());
        }
        if (richness >= 1) {
            addDoodadBenchX(stage, p, centerX - 1, fireZ + 3, 3, 1);
        }
        if (richness >= 2) {
            putDressingLamp(stage, centerX + 4, fireZ + 3, p);
        }
        // A strapped chopping block, split-log remnant, and worn tiles make this a working
        // gathering nook rather than a lone fire icon.
        stage.putIfFree(Phase.FOUNDATION, centerX + 2, 0, fireZ + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, centerX + 2, 1, fireZ + 1,
                p.timber.defaultBlockState().setValue(
                        RotatedPillarBlock.AXIS, Direction.Axis.Y));
        stage.putIfFree(Phase.DECOR, centerX + 2, 2, fireZ + 1,
                Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, centerX + 3, 0, fireZ + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, centerX + 3, 1, fireZ + 1,
                p.roofStairs.defaultBlockState().setValue(
                        StairBlock.FACING, Direction.WEST));
    }

    private static void addGardenWorkCorner(
            Builder stage, Materials p, int centerX, int z, int richness) {
        for (int x = centerX - 3; x <= centerX; x++) {
            stage.putIfFree(Phase.FOUNDATION, x, 0, z,
                    x == centerX - 3 || x == centerX
                            ? p.accent.defaultBlockState()
                            : p.foundation.defaultBlockState());
        }
        // Preserve D7's moss anchor while surrounding it with a coherent, edged planting bed.
        stage.put(Phase.DECOR, centerX - 2, 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
        stage.putIfFree(Phase.DECOR, centerX - 3, 1, z,
                Blocks.AZALEA.defaultBlockState());
        stage.putIfFree(Phase.DECOR, centerX - 1, 1, z,
                Blocks.FLOWERING_AZALEA.defaultBlockState());
        stage.putIfFree(Phase.DECOR, centerX, 1, z,
                p.roofSlab.defaultBlockState());
        putDressingPedestal(stage, centerX, z + 1, p.accent, dressingPlant(richness));
        addCrateCluster(stage, p, centerX + 2, z, richness >= 1);
        for (int x = centerX - 3; x <= centerX + 1; x++) {
            stage.putIfFree(Phase.FOUNDATION, x, 0, z + 1,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x, 1, z + 1,
                    x == centerX + 1
                            ? p.roofSlab.defaultBlockState()
                            : Blocks.MOSS_CARPET.defaultBlockState());
        }
        if (richness >= 1) {
            addDoodadBenchX(stage, p, centerX - 2, z + 3, 3, 1);
            stage.putIfFree(Phase.FOUNDATION, centerX + 1, 0, z + 3,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, centerX + 1, 1, z + 3,
                    p.fence.defaultBlockState());
            stage.putIfFree(Phase.DECOR, centerX + 1, 2, z + 3,
                    Blocks.IRON_TRAPDOOR.defaultBlockState());
        }
        if (richness >= 2) {
            putDressingLamp(stage, centerX + 3, z + 3, p);
        }
    }

    private static void addPlanterRunX(
            Builder stage, Materials p, int minX, int z, int length, int richness) {
        for (int x = minX; x < minX + length; x++) {
            Block plant = dressingPlant(Math.floorMod(richness + x - minX, 3));
            putDressingPedestal(stage, x, z,
                    ((x - minX) & 1) == 0 ? p.accent : p.foundation, plant);
            stage.putIfFree(Phase.FOUNDATION, x, 0, z + 1, upperRoofSlab(p));
            if (x == minX + length - 1 && length > 1) {
                stage.putIfFree(Phase.DECOR, x, 1, z + 1, p.wall.defaultBlockState());
                stage.putIfFree(Phase.DECOR, x, 2, z + 1,
                        dressingPlant(Math.floorMod(richness + 2, 3)).defaultBlockState());
            } else {
                stage.putIfFree(Phase.DECOR, x, 1, z + 1,
                        Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
        stage.putIfFree(Phase.FOUNDATION, minX - 1, 0, z,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX - 1, 1, z,
                Blocks.MOSS_CARPET.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, minX + length, 0, z + 1,
                Blocks.MOSS_BLOCK.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX + length, 1, z + 1,
                Blocks.FLOWERING_AZALEA.defaultBlockState());
    }

    private static void addPlanterRunZ(
            Builder stage, Materials p, int x, int minZ, int length, int richness) {
        for (int z = minZ; z < minZ + length; z++) {
            Block plant = dressingPlant(Math.floorMod(richness + z - minZ, 3));
            putDressingPedestal(stage, x, z,
                    ((z - minZ) & 1) == 0 ? p.accent : p.foundation, plant);
            int edgeX = x < 0 ? x - 1 : x + 1;
            stage.putIfFree(Phase.FOUNDATION, edgeX, 0, z, upperRoofSlab(p));
            if (z == minZ + length - 1 && length > 1) {
                stage.putIfFree(Phase.DECOR, edgeX, 1, z, p.wall.defaultBlockState());
                stage.putIfFree(Phase.DECOR, edgeX, 2, z,
                        dressingPlant(Math.floorMod(richness + 2, 3)).defaultBlockState());
            } else {
                stage.putIfFree(Phase.DECOR, edgeX, 1, z,
                        Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
        stage.putIfFree(Phase.FOUNDATION, x, 0, minZ - 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 1, minZ - 1,
                Blocks.MOSS_CARPET.defaultBlockState());
        int edgeX = x < 0 ? x - 1 : x + 1;
        stage.putIfFree(Phase.FOUNDATION, edgeX, 0, minZ + length,
                Blocks.MOSS_BLOCK.defaultBlockState());
        stage.putIfFree(Phase.DECOR, edgeX, 1, minZ + length,
                Blocks.FLOWERING_AZALEA.defaultBlockState());
    }

    private static Block doodadTrapdoor(Materials p) {
        return switch (p.dialect) {
            case DESERT, SAVANNA -> Blocks.ACACIA_TRAPDOOR;
            case TAIGA, SNOWY -> Blocks.SPRUCE_TRAPDOOR;
            case PLAINS -> Blocks.OAK_TRAPDOOR;
        };
    }

    private static BlockState openDoodadTrapdoor(Materials p, Direction facing) {
        return doodadTrapdoor(p).defaultBlockState()
                .setValue(TrapDoorBlock.FACING, facing)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.OPEN, true);
    }

    private static void addCrateCluster(
            Builder stage, Materials p, int x, int z, boolean stacked) {
        // Cosmetic crates are deliberately non-container forms: the village cannot regenerate
        // loot-bearing storage when a player edits the scene. A four-slat pallet unifies three
        // differently sized parcels; open trapdoors frame the faces and top rails read as straps.
        for (int dx = 0; dx <= 1; dx++) {
            stage.put(Phase.FOUNDATION, x + dx, 0, z,
                    p.foundation.defaultBlockState());
            stage.put(Phase.FRAME, x + dx, 1, z, upperRoofSlab(p));
            stage.putIfFree(Phase.FOUNDATION, x + dx, 0, z + 1,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, x + dx, 1, z + 1, upperRoofSlab(p));
        }
        stage.put(Phase.DECOR, x, 2, z, p.wall.defaultBlockState());
        stage.put(Phase.DECOR, x + 1, 2, z, p.timber.defaultBlockState().setValue(
                RotatedPillarBlock.AXIS, Direction.Axis.Y));
        stage.putIfFree(Phase.DECOR, x + 1, 2, z + 1,
                p.roofStairs.defaultBlockState().setValue(
                        StairBlock.FACING, Direction.NORTH));
        stage.putIfFree(Phase.DECOR, x, 2, z - 1,
                openDoodadTrapdoor(p, Direction.SOUTH));
        stage.putIfFree(Phase.DECOR, x + 1, 2, z - 1,
                openDoodadTrapdoor(p, Direction.SOUTH));
        stage.putIfFree(Phase.DECOR, x - 1, 2, z,
                openDoodadTrapdoor(p, Direction.EAST));
        if (stacked) {
            stage.put(Phase.DECOR, x, 3, z, p.accent.defaultBlockState());
            stage.put(Phase.DECOR, x, 4, z, Blocks.RAIL.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x - 1, 3, z,
                    openDoodadTrapdoor(p, Direction.EAST));
        } else {
            stage.put(Phase.DECOR, x, 3, z, Blocks.RAIL.defaultBlockState());
        }
        stage.put(Phase.DECOR, x + 1, 3, z, Blocks.RAIL.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x + 1, 3, z + 1,
                doodadTrapdoor(p).defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, x - 1, 0, z - 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x - 1, 1, z - 1,
                p.roofSlab.defaultBlockState());
    }

    private static void addHandCart(
            Builder stage, Materials p, int centerX, int z, Block cargo) {
        int axleZ = z + 1;
        BlockState axle = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int dz = 0; dz <= 2; dz++) {
                stage.put(Phase.FOUNDATION, x, 0, z + dz,
                        (Math.abs(x - centerX) + dz) % 2 == 0
                                ? p.foundation.defaultBlockState()
                                : Blocks.DIRT_PATH.defaultBlockState());
                stage.put(Phase.DECOR, x, 2, z + dz, upperRoofSlab(p));
            }
            stage.put(Phase.FRAME, x, 1, axleZ, axle);
        }
        // Upright palette-matched trapdoors make two separated wheels around a clearly exposed
        // crosswise axle. The three-deep pallet bed, framed sideboards, twin raised handles, and
        // strapped cargo remain independently readable from a three-quarter yard view.
        for (int wheelX : new int[] {centerX - 2, centerX + 2}) {
            stage.put(Phase.FOUNDATION, wheelX, 0, axleZ,
                    Blocks.DIRT_PATH.defaultBlockState());
        }
        stage.put(Phase.FRAME, centerX - 2, 1, axleZ,
                openDoodadTrapdoor(p, Direction.EAST));
        stage.put(Phase.FRAME, centerX + 2, 1, axleZ,
                openDoodadTrapdoor(p, Direction.WEST));
        for (int dz = 0; dz <= 2; dz++) {
            stage.put(Phase.FRAME, centerX - 2, 2, z + dz,
                    openDoodadTrapdoor(p, Direction.EAST));
            stage.put(Phase.FRAME, centerX + 2, 2, z + dz,
                    openDoodadTrapdoor(p, Direction.WEST));
        }
        for (int rimZ : new int[] {z, z + 2}) {
            stage.put(Phase.FRAME, centerX - 1, 3, rimZ, p.fence.defaultBlockState());
            stage.put(Phase.FRAME, centerX + 1, 3, rimZ, p.fence.defaultBlockState());
        }
        stage.put(Phase.DECOR, centerX, 3, axleZ, cargo.defaultBlockState());
        stage.put(Phase.DECOR, centerX, 4, axleZ, Blocks.RAIL.defaultBlockState());
        for (int dz = 3; dz <= 4; dz++) {
            for (int handleX : new int[] {centerX - 1, centerX + 1}) {
                stage.put(Phase.FOUNDATION, handleX, 0, z + dz,
                        Blocks.DIRT_PATH.defaultBlockState());
                stage.put(Phase.FRAME, handleX, 2, z + dz, p.fence.defaultBlockState());
                if (dz == 3) {
                    stage.put(Phase.FRAME, handleX, 1, z + dz,
                            p.roofStairs.defaultBlockState().setValue(
                                    StairBlock.FACING, Direction.SOUTH));
                }
            }
        }
        stage.put(Phase.FOUNDATION, centerX, 0, z + 4,
                Blocks.DIRT_PATH.defaultBlockState());
    }

    /**
     * Places a rear-yard cart at the first deterministic collision-free anchor.
     *
     * <p>Authored masters may project portals, apses, or loading throats beyond their nominal
     * descriptor depth. A fixed rear coordinate therefore becomes invalid as the catalog grows.
     * This bounded search keeps the scene save-stable while protecting base cells, earlier
     * dressing cells, reserved circulation, and explicit interaction targets.</p>
     */
    private static void addRearHandCart(
            Builder stage,
            Metadata m,
            Materials p,
            String dressingId,
            int salt,
            Block cargo) {
        int center = m.width / 2;
        int left = 2;
        int right = m.width - 3;
        int variant = deterministicDoodadVariant(m, dressingId, salt, 2);
        int[] centers = variant == 0
                ? new int[] {center, left, right}
                : new int[] {center, right, left};
        int firstZ = m.depth + 2;
        for (int dz = 0; dz <= 4; dz++) {
            for (int candidateX : centers) {
                int candidateZ = firstZ + dz;
                if (canPlaceHandCart(stage, m, candidateX, candidateZ)) {
                    addHandCart(stage, p, candidateX, candidateZ, cargo);
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "No collision-free rear handcart anchor for " + m.type
                        + " within the bounded authored yard");
    }

    private static boolean canPlaceHandCart(
            Builder stage, Metadata m, int centerX, int z) {
        if (centerX - 2 < 0 || centerX + 2 >= m.width) {
            return false;
        }
        List<BlockPos> footprint = new ArrayList<>(72);
        int axleZ = z + 1;
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int dz = 0; dz <= 2; dz++) {
                footprint.add(new BlockPos(x, 0, z + dz));
                footprint.add(new BlockPos(x, 2, z + dz));
            }
            footprint.add(new BlockPos(x, 1, axleZ));
        }
        for (int wheelX : new int[] {centerX - 2, centerX + 2}) {
            footprint.add(new BlockPos(wheelX, 0, axleZ));
            footprint.add(new BlockPos(wheelX, 1, axleZ));
            for (int dz = 0; dz <= 2; dz++) {
                footprint.add(new BlockPos(wheelX, 2, z + dz));
            }
        }
        for (int rimZ : new int[] {z, z + 2}) {
            footprint.add(new BlockPos(centerX - 1, 3, rimZ));
            footprint.add(new BlockPos(centerX + 1, 3, rimZ));
        }
        footprint.add(new BlockPos(centerX, 3, axleZ));
        footprint.add(new BlockPos(centerX, 4, axleZ));
        for (int dz = 3; dz <= 4; dz++) {
            for (int handleX : new int[] {centerX - 1, centerX + 1}) {
                footprint.add(new BlockPos(handleX, 0, z + dz));
                footprint.add(new BlockPos(handleX, 2, z + dz));
                if (dz == 3) {
                    footprint.add(new BlockPos(handleX, 1, z + dz));
                }
            }
        }
        footprint.add(new BlockPos(centerX, 0, z + 4));
        return canPlaceDoodad(stage, m, footprint);
    }

    private static boolean canPlaceDoodad(
            Builder stage, Metadata m, List<BlockPos> footprint) {
        for (BlockPos position : footprint) {
            if (stage.isOccupied(position)
                    || m.reservedAir.contains(position)
                    || m.accessTargets.contains(position)) {
                return false;
            }
        }
        return true;
    }

    private static void addHitchingRailZ(
            Builder stage, Materials p, int x, int minZ, int length) {
        int trampledX = x < 0 ? x - 1 : x + 1;
        int outerWearX = x < 0 ? x - 2 : x + 2;
        int lastZ = minZ + length - 1;
        int middleZ = minZ + length / 2;
        for (int z = minZ; z < minZ + length; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z,
                    ((z - minZ) & 1) == 0
                            ? Blocks.DIRT_PATH.defaultBlockState()
                            : p.foundation.defaultBlockState());
            stage.put(Phase.FRAME, x, 1, z,
                    z == minZ || z == lastZ
                            ? p.fence.defaultBlockState()
                            : Blocks.IRON_CHAIN.defaultBlockState());
            // A connected fence rail reads as a thin open hitch rather than a shoulder-high log
            // wall; the suspended center chain remains a clearly separate tie point.
            stage.put(Phase.FRAME, x, 2, z, p.fence.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, trampledX, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, outerWearX, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
        }
        for (int endZ : new int[] {minZ, lastZ}) {
            stage.putIfFree(Phase.DECOR, x, 3, endZ, p.roofSlab.defaultBlockState());
            stage.putIfFree(Phase.FRAME, trampledX, 2, endZ,
                    Blocks.IRON_BARS.defaultBlockState());
            stage.putIfFree(Phase.FRAME, trampledX, 1, endZ,
                    Blocks.IRON_CHAIN.defaultBlockState());
        }
        int companionX = x < 0 ? x - 2 : x + 2;
        stage.putIfFree(Phase.FOUNDATION, companionX, 0, middleZ,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, companionX, 1, middleZ,
                p.roofStairs.defaultBlockState().setValue(
                        StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
        stage.putIfFree(Phase.FOUNDATION, companionX, 0, middleZ + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, companionX, 1, middleZ + 1,
                Blocks.HAY_BLOCK.defaultBlockState().setValue(
                        RotatedPillarBlock.AXIS, Direction.Axis.Z));
        for (int wearZ : new int[] {minZ - 1, lastZ + 1}) {
            stage.putIfFree(Phase.FOUNDATION, trampledX, 0, wearZ,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, outerWearX, 0, wearZ,
                    Blocks.DIRT_PATH.defaultBlockState());
        }
    }

    private static void addFeedTroughZ(
            Builder stage, Materials p, int x, int minZ, int length) {
        int outward = x < 0 ? -1 : 1;
        Direction basinFacing = outward < 0 ? Direction.WEST : Direction.EAST;
        for (int z = minZ; z < minZ + length; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
            stage.put(Phase.DECOR, x, 1, z, p.roofStairs.defaultBlockState()
                    .setValue(StairBlock.FACING, basinFacing));
            for (int lipX : new int[] {x - 1, x + 1}) {
                stage.putIfFree(Phase.FOUNDATION, lipX, 0, z,
                        Blocks.DIRT_PATH.defaultBlockState());
                stage.putIfFree(Phase.DECOR, lipX, 1, z,
                        p.roofSlab.defaultBlockState());
            }
            stage.putIfFree(Phase.DECOR, x, 2, z,
                    ((z - minZ) & 1) == 0
                            ? Blocks.CARPET.yellow().defaultBlockState()
                            : Blocks.CARPET.brown().defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, x + outward * 2, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
        }
        for (int endZ : new int[] {minZ - 1, minZ + length}) {
            stage.putIfFree(Phase.FOUNDATION, x, 0, endZ,
                    p.foundation.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x, 1, endZ,
                    p.roofStairs.defaultBlockState().setValue(
                            StairBlock.FACING,
                            endZ < minZ ? Direction.SOUTH : Direction.NORTH));
            stage.putIfFree(Phase.FOUNDATION, x + outward, 0, endZ,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, x + outward, 1, endZ,
                    p.fence.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x + outward, 2, endZ, upperRoofSlab(p));
        }
        if (length > 1) {
            stage.putIfFree(Phase.FOUNDATION, x + outward * 2, 0, minZ + length,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x + outward * 2, 1, minZ + length,
                    Blocks.HAY_BLOCK.defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
    }

    private static void addHayPile(
            Builder stage, Materials p, int x, int z, int richness) {
        int outward = x < 0 ? -1 : 1;
        int outerX = x + outward;
        int innerX = x - outward;
        BlockState crosswiseBale = Blocks.HAY_BLOCK.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState lengthwiseBale = Blocks.HAY_BLOCK.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
        stage.put(Phase.DECOR, x, 1, z, crosswiseBale);
        stage.putIfFree(Phase.DECOR, x, 2, z, Blocks.RAIL.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, outerX, 0, z + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, outerX, 1, z + 1, lengthwiseBale);
        stage.putIfFree(Phase.DECOR, outerX, 2, z + 1,
                Blocks.RAIL.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, x, 0, z + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 1, z + 1, crosswiseBale);
        stage.putIfFree(Phase.FOUNDATION, innerX, 0, z + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, innerX, 1, z + 1,
                Blocks.CARPET.yellow().defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, x, 0, z + 2,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 1, z + 2,
                Blocks.CARPET.yellow().defaultBlockState());
        if (richness >= 1) {
            stage.putIfFree(Phase.DECOR, x, 2, z + 1, lengthwiseBale);
            stage.putIfFree(Phase.DECOR, x, 3, z + 1,
                    Blocks.RAIL.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, outerX, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.FRAME, outerX, 1, z,
                    p.fence.defaultBlockState());
            stage.putIfFree(Phase.DECOR, outerX, 2, z,
                    Blocks.IRON_BARS.defaultBlockState());
        }
        if (richness >= 2) {
            stage.putIfFree(Phase.FOUNDATION, innerX, 0, z + 2,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.DECOR, innerX, 1, z + 2, crosswiseBale);
            stage.putIfFree(Phase.DECOR, innerX, 2, z + 2,
                    Blocks.RAIL.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, outerX, 0, z + 2,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.DECOR, outerX, 1, z + 2,
                    Blocks.CARPET.yellow().defaultBlockState());
        }
    }

    private static void addMaterialPile(
            Builder stage, Materials p, int x, int z, Block material, int richness) {
        int outward = x < 0 ? -1 : 1;
        int stockX = x - outward;
        Direction slopeFacing = outward < 0 ? Direction.WEST : Direction.EAST;
        // Graded rubble and ash own the silhouette. The raw stock block is recessed at the back
        // edge as material context rather than standing alone as a square black centerpiece.
        stage.put(Phase.FOUNDATION, x, 0, z, Blocks.GRAVEL.defaultBlockState());
        stage.put(Phase.DECOR, x, 1, z, p.roofStairs.defaultBlockState().setValue(
                StairBlock.FACING, slopeFacing));
        stage.putIfFree(Phase.FOUNDATION, x + outward, 0, z,
                Blocks.GRAVEL.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x + outward, 1, z,
                Blocks.COBBLESTONE_WALL.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, stockX, 0, z + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, stockX, 1, z + 1, material.defaultBlockState());
        stage.putIfFree(Phase.DECOR, stockX, 2, z + 1,
                p.roofSlab.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, x, 0, z + 1,
                Blocks.GRAVEL.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 1, z + 1,
                p.roofSlab.defaultBlockState());
        stage.putIfFree(Phase.FOUNDATION, x + outward, 0, z + 1,
                Blocks.DIRT_PATH.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x + outward, 1, z + 1,
                Blocks.CARPET.black().defaultBlockState());
        if (richness >= 1) {
            stage.putIfFree(Phase.DECOR, x, 2, z,
                    p.roofSlab.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, stockX, 0, z,
                    Blocks.GRAVEL.defaultBlockState());
            stage.putIfFree(Phase.DECOR, stockX, 1, z,
                    p.roofStairs.defaultBlockState().setValue(
                            StairBlock.FACING, slopeFacing.getOpposite()));
            stage.putIfFree(Phase.FOUNDATION, x + outward, 0, z + 2,
                    Blocks.GRAVEL.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x + outward, 1, z + 2,
                    Blocks.COBBLESTONE_WALL.defaultBlockState());
        }
        if (richness >= 2) {
            stage.putIfFree(Phase.FOUNDATION, stockX, 0, z + 2,
                    Blocks.GRAVEL.defaultBlockState());
            stage.putIfFree(Phase.DECOR, stockX, 1, z + 2,
                    p.roofStairs.defaultBlockState().setValue(
                            StairBlock.FACING, slopeFacing.getOpposite()));
            stage.putIfFree(Phase.FOUNDATION, x, 0, z + 2,
                    Blocks.DIRT_PATH.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x, 1, z + 2,
                    Blocks.COBBLESTONE_WALL.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x, 2, z + 2,
                    p.roofSlab.defaultBlockState());
        }
    }

    private static void addToolRackZ(
            Builder stage, Materials p, int x, int minZ, int length) {
        int ledgeX = x < 0 ? x - 1 : x + 1;
        for (int z = minZ; z < minZ + length; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z,
                    z == minZ || z == minZ + length - 1
                            ? p.foundation.defaultBlockState()
                            : Blocks.DIRT_PATH.defaultBlockState());
            // A shallow stair lintel is visually open but still face-connects the hanging center
            // tool to both grounded end posts; thin slabs here leave the chain unsupported.
            stage.put(Phase.FRAME, x, 2, z, p.roofStairs.defaultBlockState().setValue(
                    StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
            stage.put(Phase.FRAME, x, 1, z,
                    z == minZ || z == minZ + length - 1
                            ? p.fence.defaultBlockState()
                            : z == minZ + length / 2
                                    ? Blocks.IRON_CHAIN.defaultBlockState()
                                    : Blocks.IRON_BARS.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, ledgeX, 0, z,
                    Blocks.DIRT_PATH.defaultBlockState());
        }
        stage.putIfFree(Phase.DECOR, ledgeX, 1, minZ,
                p.roofStairs.defaultBlockState().setValue(
                        StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
        stage.putIfFree(Phase.DECOR, ledgeX, 1, minZ + length / 2,
                Blocks.IRON_TRAPDOOR.defaultBlockState());
        stage.putIfFree(Phase.DECOR, ledgeX, 1, minZ + length - 1,
                upperRoofSlab(p));
        stage.putIfFree(Phase.DECOR, ledgeX, 2, minZ + length - 1,
                Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
    }

    private static void addToolRackX(
            Builder stage, Materials p, int minX, int z, int length) {
        for (int x = minX; x < minX + length; x++) {
            stage.put(Phase.FOUNDATION, x, 0, z,
                    x == minX || x == minX + length - 1
                            ? p.foundation.defaultBlockState()
                            : Blocks.DIRT_PATH.defaultBlockState());
            stage.put(Phase.FRAME, x, 2, z, p.roofStairs.defaultBlockState().setValue(
                    StairBlock.FACING, Direction.NORTH));
            stage.put(Phase.FRAME, x, 1, z,
                    x == minX || x == minX + length - 1
                            ? p.fence.defaultBlockState()
                            : x == minX + length / 2
                                    ? Blocks.IRON_CHAIN.defaultBlockState()
                                    : Blocks.IRON_BARS.defaultBlockState());
            stage.putIfFree(Phase.FOUNDATION, x, 0, z + 1,
                    Blocks.DIRT_PATH.defaultBlockState());
        }
        stage.putIfFree(Phase.DECOR, minX, 1, z + 1,
                p.roofStairs.defaultBlockState().setValue(
                        StairBlock.FACING, Direction.NORTH));
        stage.putIfFree(Phase.DECOR, minX + length / 2, 1, z + 1,
                Blocks.IRON_TRAPDOOR.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX + length - 1, 1, z + 1,
                upperRoofSlab(p));
        stage.putIfFree(Phase.DECOR, minX + length - 1, 2, z + 1,
                Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
    }

    private static void addTargetRackZ(
            Builder stage, Materials p, int x, int minZ) {
        for (int z = minZ; z <= minZ + 2; z++) {
            stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
        }
        stage.put(Phase.FRAME, x, 1, minZ, p.fence.defaultBlockState());
        stage.put(Phase.DECOR, x, 1, minZ + 1, Blocks.TARGET.defaultBlockState());
        stage.put(Phase.FRAME, x, 1, minZ + 2, p.fence.defaultBlockState());
        BlockState beamState = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z = minZ; z <= minZ + 2; z++) {
            stage.put(Phase.FRAME, x, 2, z, beamState);
        }
        // A second, higher target breaks the uniform silhouette while retaining the low primary
        // target for close-up recognition.
        stage.force(Phase.DECOR, x, 2, minZ + 2, Blocks.TARGET.defaultBlockState());
        stage.putIfFree(Phase.DECOR, x, 3, minZ + 1, p.roofSlab.defaultBlockState());
        int outward = x < 0 ? -1 : 1;
        int backstopX = x - outward;
        for (int z = minZ; z <= minZ + 2; z++) {
            stage.putIfFree(Phase.FOUNDATION, backstopX, 0, z,
                    p.foundation.defaultBlockState());
            stage.putIfFree(Phase.FRAME, backstopX, 1, z,
                    z == minZ || z == minZ + 2
                            ? p.fence.defaultBlockState()
                            : p.roofStairs.defaultBlockState().setValue(
                                    StairBlock.FACING,
                                    outward < 0 ? Direction.WEST : Direction.EAST));
            stage.putIfFree(Phase.FRAME, backstopX, 2, z, upperRoofSlab(p));
        }
        for (int distance = 1; distance <= 4; distance++) {
            int rangeX = x + outward * distance;
            for (int z = minZ; z <= minZ + 2; z++) {
                stage.putIfFree(Phase.FOUNDATION, rangeX, 0, z,
                        distance == 4 && z == minZ + 1
                                ? p.accent.defaultBlockState()
                                : Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        int firingX = x + outward * 4;
        stage.putIfFree(Phase.FRAME, firingX, 1, minZ,
                p.fence.defaultBlockState());
        stage.putIfFree(Phase.FRAME, firingX, 1, minZ + 1,
                Blocks.IRON_BARS.defaultBlockState());
        stage.putIfFree(Phase.FRAME, firingX, 1, minZ + 2,
                p.fence.defaultBlockState());
        stage.putIfFree(Phase.DECOR, firingX, 2, minZ,
                upperRoofSlab(p));
        stage.putIfFree(Phase.DECOR, firingX, 2, minZ + 2,
                upperRoofSlab(p));
    }

    private static void addTargetRackX(
            Builder stage, Materials p, int minX, int z) {
        for (int x = minX; x <= minX + 2; x++) {
            stage.put(Phase.FOUNDATION, x, 0, z, p.foundation.defaultBlockState());
        }
        stage.put(Phase.FRAME, minX, 1, z, p.fence.defaultBlockState());
        stage.put(Phase.DECOR, minX + 1, 1, z, Blocks.TARGET.defaultBlockState());
        stage.put(Phase.FRAME, minX + 2, 1, z, p.fence.defaultBlockState());
        BlockState beamState = p.timber.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = minX; x <= minX + 2; x++) {
            stage.put(Phase.FRAME, x, 2, z, beamState);
        }
        stage.force(Phase.DECOR, minX + 2, 2, z, Blocks.TARGET.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX + 1, 3, z, p.roofSlab.defaultBlockState());
        int backstopZ = z - 1;
        for (int x = minX; x <= minX + 2; x++) {
            stage.putIfFree(Phase.FOUNDATION, x, 0, backstopZ,
                    p.foundation.defaultBlockState());
            stage.putIfFree(Phase.FRAME, x, 1, backstopZ,
                    x == minX || x == minX + 2
                            ? p.fence.defaultBlockState()
                            : p.roofStairs.defaultBlockState().setValue(
                                    StairBlock.FACING, Direction.SOUTH));
            stage.putIfFree(Phase.FRAME, x, 2, backstopZ, upperRoofSlab(p));
        }
        for (int distance = 1; distance <= 4; distance++) {
            int rangeZ = z + distance;
            for (int x = minX; x <= minX + 2; x++) {
                stage.putIfFree(Phase.FOUNDATION, x, 0, rangeZ,
                        distance == 4 && x == minX + 1
                                ? p.accent.defaultBlockState()
                                : Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        int firingZ = z + 4;
        stage.putIfFree(Phase.FRAME, minX, 1, firingZ,
                p.fence.defaultBlockState());
        stage.putIfFree(Phase.FRAME, minX + 1, 1, firingZ,
                Blocks.IRON_BARS.defaultBlockState());
        stage.putIfFree(Phase.FRAME, minX + 2, 1, firingZ,
                p.fence.defaultBlockState());
        stage.putIfFree(Phase.DECOR, minX, 2, firingZ, upperRoofSlab(p));
        stage.putIfFree(Phase.DECOR, minX + 2, 2, firingZ, upperRoofSlab(p));
    }

    static void validateCatalog() {
        List<VillageArchitecture.BlueprintDescriptor> activeDescriptors = new ArrayList<>();
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            for (VillageArchitecture.BlueprintDescriptor descriptor
                    : VillageArchitecture.blueprints(type)) {
                if (descriptor.templateRevision() == LATEST_TEMPLATE_REVISION) {
                    activeDescriptors.add(descriptor);
                }
            }
        }

        // This smoke-only gate intentionally exercises the entire palette/dressing matrix. Each
        // descriptor is self-contained and all Minecraft block states are immutable, so validate
        // independent masters concurrently and then consume results in stable catalog order. A
        // broken master still produces the same deterministic aggregate admission failure without
        // monopolizing the server thread long enough to trip its watchdog.
        List<CatalogValidationResult> results = activeDescriptors.parallelStream()
                .map(AuthoredVillageStructures::validateCatalogDescriptor)
                .toList();
        List<StructuralSnapshot> activeStructuralSnapshots = new ArrayList<>();
        List<String> catalogFailures = new ArrayList<>();
        for (CatalogValidationResult result : results) {
            if (result.failure != null) {
                catalogFailures.add(result.failure);
            } else {
                activeStructuralSnapshots.add(result.structuralSnapshot);
            }
        }
        if (!catalogFailures.isEmpty()) {
            throw new IllegalStateException(
                    "Authored catalog validation failures:\n - "
                            + String.join("\n - ", catalogFailures));
        }
        if (activeStructuralSnapshots.size() != 52) {
            throw new IllegalStateException(
                    "Blueprint V2 requires exactly 52 active authored masters, found "
                            + activeStructuralSnapshots.size());
        }
        WholeBuildingDistinctivenessValidator.validateSnapshots(activeStructuralSnapshots)
                .requireDistinct();
    }

    private static CatalogValidationResult validateCatalogDescriptor(
            VillageArchitecture.BlueprintDescriptor descriptor) {
        try {
            Blueprint canonical = plan(
                    descriptor.type(),
                    descriptor.templateId(),
                    descriptor.templateRevision(),
                    VillageArchitecture.PALETTE_BALANCED,
                    VillageArchitecture.DRESSING_RESTRAINED,
                    VillageArchitecture.Character.RUSTIC,
                    VillageArchitecture.BiomeDialect.PLAINS);
            StructuralSnapshot snapshot = structuralSnapshot(canonical);
            // Consecutive seeds exercise all four modulo-4 scene selections at every richness
            // level while keeping the full palette/dialect matrix compact.
            for (String dressing : descriptor.dressingIds()) {
                for (long doodadSeed = 0L; doodadSeed < 4L; doodadSeed++) {
                    plan(
                            descriptor.type(),
                            descriptor.templateId(),
                            descriptor.templateRevision(),
                            VillageArchitecture.PALETTE_BALANCED,
                            dressing,
                            VillageArchitecture.Character.RUSTIC,
                            VillageArchitecture.BiomeDialect.PLAINS,
                            doodadSeed);
                }
            }
            for (VillageArchitecture.BiomeDialect dialect
                    : VillageArchitecture.BiomeDialect.values()) {
                for (String palette : descriptor.paletteIds()) {
                    for (String dressing : descriptor.dressingIds()) {
                        for (VillageArchitecture.Character character
                                : VillageArchitecture.Character.values()) {
                            Blueprint blueprint = plan(
                                    descriptor.type(),
                                    descriptor.templateId(),
                                    descriptor.templateRevision(),
                                    palette,
                                    dressing,
                                    character,
                                    dialect);
                            if (blueprint.width != descriptor.width()
                                    || blueprint.depth != descriptor.depth()
                                    || blueprint.height != descriptor.height()) {
                                throw new IllegalStateException(
                                        "Blueprint descriptor size mismatch for "
                                                + descriptor.templateId());
                            }
                        }
                    }
                }
            }
            return new CatalogValidationResult(snapshot, null);
        } catch (RuntimeException failure) {
            return new CatalogValidationResult(
                    null,
                    descriptor.templateId() + "@" + descriptor.templateRevision() + ": "
                            + failure.getMessage());
        }
    }

    private record CatalogValidationResult(
            StructuralSnapshot structuralSnapshot, String failure) {
    }

    private static StructuralSnapshot structuralSnapshot(Blueprint blueprint) {
        Set<Voxel> structuralCells = new HashSet<>();
        for (Cell cell : blueprint.base) {
            boolean structural = switch (cell.phase) {
                case FOUNDATION, FRAME, SHELL, ROOF, OPENING -> true;
                case FIXTURE, DECOR -> false;
            };
            if (structural) {
                structuralCells.add(new Voxel(cell.x, cell.y, cell.z));
            }
        }
        return new StructuralSnapshot(blueprint.id + "@" + blueprint.revision, structuralCells);
    }

    private static DetailSnapshot detailSnapshot(Blueprint blueprint) {
        List<DetailCell> detailCells = new ArrayList<>(
                blueprint.base.size() + blueprint.stageOne.size() + blueprint.stageTwo.size());
        for (List<Cell> layer : List.of(blueprint.base, blueprint.stageOne, blueprint.stageTwo)) {
            for (Cell cell : layer) {
                detailCells.add(new DetailCell(
                        new Voxel(cell.x, cell.y, cell.z),
                        DetailPhase.valueOf(cell.phase.name())));
            }
        }
        return new DetailSnapshot(
                blueprint.id + "@" + blueprint.revision,
                blueprint.width,
                blueprint.depth,
                blueprint.height,
                blueprint.metadata.enclosed(),
                detailCells);
    }

    private static void validateDetailDensity(Blueprint blueprint) {
        WholeBuildingDetailDensityValidator.validateSnapshots(List.of(detailSnapshot(blueprint)))
                .requireDetailed();
    }

    private static PresentationSnapshot presentationSnapshot(Blueprint blueprint) {
        VillageArchitecture.BlueprintScale scale = VillageArchitecture
                .requireBlueprint(blueprint.id, blueprint.revision)
                .scale();
        return new PresentationSnapshot(
                blueprint.id + "@" + blueprint.revision,
                scale,
                blueprint.width,
                blueprint.depth,
                blueprint.height,
                blueprint.metadata.enclosed(),
                presentationCells(blueprint.base),
                presentationCells(blueprint.stageOne),
                presentationCells(blueprint.stageTwo));
    }

    /** Retains base/stage provenance so yard dressing cannot masquerade as facade architecture. */
    private static List<DetailCell> presentationCells(List<Cell> cells) {
        List<DetailCell> translated = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            translated.add(new DetailCell(
                    new Voxel(cell.x, cell.y, cell.z),
                    DetailPhase.valueOf(cell.phase.name())));
        }
        return translated;
    }

    private static void validatePresentation(Blueprint blueprint) {
        WholeBuildingPresentationValidator
                .validateSnapshots(List.of(presentationSnapshot(blueprint)))
                .requirePresentable();
    }

    private static RoleSnapshot roleReadabilitySnapshot(Blueprint blueprint) {
        List<FixtureSignal> signals = new ArrayList<>();
        for (Cell cell : blueprint.base) {
            BlockState state = cell.state;
            if (state.is(Blocks.BED.white())) {
                signals.add(FixtureSignal.SLEEPING);
            }
            if (state.is(Blocks.SMOKER) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.FURNACE)) {
                signals.add(FixtureSignal.DOMESTIC_HEARTH);
            }
            if (state.is(Blocks.SMOKER) || state.is(Blocks.BREWING_STAND) || state.is(Blocks.CAKE)) {
                signals.add(FixtureSignal.HOSPITALITY_SERVICE);
            }
            if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
                signals.add(FixtureSignal.BULK_STORAGE);
            }
            if (state.is(Blocks.HAY_BLOCK) || state.is(Blocks.COMPOSTER)) {
                signals.add(FixtureSignal.AGRICULTURAL_STORAGE);
            }
            if (state.is(Blocks.ANVIL)
                    || state.is(Blocks.BLAST_FURNACE)
                    || state.is(Blocks.SMITHING_TABLE)
                    || state.is(Blocks.GRINDSTONE)) {
                signals.add(FixtureSignal.FORGE_WORKS);
            }
            if (state.is(Blocks.RAIL) || state.is(Blocks.STONECUTTER)) {
                signals.add(FixtureSignal.MINE_WORKS);
            }
            if (state.is(Blocks.BELL)) {
                signals.add(FixtureSignal.MARKET_ANCHOR);
            }
            if (state.is(Blocks.TARGET)
                    || state.is(Blocks.IRON_BARS)
                    || state.is(Blocks.FLETCHING_TABLE)) {
                signals.add(FixtureSignal.DEFENSIVE_EQUIPMENT);
            }
            if (BankerProfessionSupport.isExchangeDesk(state)) {
                signals.add(FixtureSignal.EXCHANGE_DESK);
            }
        }
        return new RoleSnapshot(
                blueprint.id + "@" + blueprint.revision,
                BuildingRole.valueOf(blueprint.metadata.type().name()),
                signals);
    }

    private static void validateRoleReadability(Blueprint blueprint) {
        WholeBuildingRoleReadabilityValidator
                .validateSnapshots(List.of(roleReadabilitySnapshot(blueprint)))
                .requireReadable();
    }

    private static void validate(Blueprint blueprint) {
        validateAuthoredEnvelope(blueprint);
        Map<BlockPos, BlockState> base = map(blueprint.base);
        Map<BlockPos, BlockState> throughStageOne = new HashMap<>(base);
        appendDisjoint(throughStageOne, blueprint.stageOne, blueprint.id);
        Map<BlockPos, BlockState> complete = new HashMap<>(throughStageOne);
        appendDisjoint(complete, blueprint.stageTwo, blueprint.id);
        if (base.isEmpty() || blueprint.stageOne.isEmpty() || blueprint.stageTwo.isEmpty()) {
            throw new IllegalStateException("Blueprint lost a required stage: " + blueprint.id);
        }
        for (BlockPos reserved : blueprint.metadata.reservedAir) {
            if (complete.containsKey(reserved)) {
                throw new IllegalStateException(
                        "Blueprint occupies reserved circulation at " + reserved + " in " + blueprint.id);
            }
        }
        validateNavigation(blueprint, base);
        validateVerticalAccess(blueprint, base);
        validateNavigation(blueprint, throughStageOne);
        validateVerticalAccess(blueprint, throughStageOne);
        validateNavigation(blueprint, complete);
        validateVerticalAccess(blueprint, complete);
        validateAttachedSupports(blueprint, base);
        validateAttachedSupports(blueprint, throughStageOne);
        validateAttachedSupports(blueprint, complete);
        validateRoofGeometry(blueprint);
        validateSupport(blueprint, complete);
        validatePaneConnections(blueprint, complete);
        if (blueprint.metadata.enclosed) {
            validateEnclosure(blueprint, base);
            validateInteriorConnectivity(blueprint, base, complete);
        }
        // BASE is the first materialized visual stage, Stage 1 is observable for an arbitrary
        // amount of play time, and Stage 2 is the completed building. Admit each exact cumulative
        // state independently so an intermediate cosmetic layer cannot occlude a base fixture and
        // rely on a later-stage lamp to become safe again.
        validateCoveredFloorLighting(blueprint, "base", base);
        validateCoveredFloorLighting(blueprint, "stage-one", throughStageOne);
        validateCoveredFloorLighting(blueprint, "stage-two", complete);
    }

    /**
     * Adds restrained, structurally supported light wherever a completed authored roof covers a
     * usable structural floor. Existing hearths and forges are evaluated first. Homes favor short
     * warm pendants and wall sconces, work buildings favor low wall lamps, and large civic rooms
     * favor balanced pendants. Open markets, loading bays, mine sheds, and undercrofts are included:
     * exterior-connected air is not an exemption from nighttime hostile-spawn safety.
     */
    private static void ensureComfortableInteriorLighting(
            Builder builder, Metadata metadata, String templateId) {
        String cacheKey = templateId + "@" + LATEST_TEMPLATE_REVISION;
        List<Cell> cached = LIGHTING_COMPOSITION_CACHE.get(cacheKey);
        if (cached != null) {
            replayLightingComposition(builder, cached);
            return;
        }
        Object lock = LIGHTING_COMPOSITION_LOCKS.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            cached = LIGHTING_COMPOSITION_CACHE.get(cacheKey);
            if (cached != null) {
                replayLightingComposition(builder, cached);
                return;
            }
            Set<BlockPos> before = builder.positions();
            composeComfortableInteriorLighting(builder, metadata, templateId);
            List<Cell> composition = builder.values().stream()
                    .filter(cell -> !before.contains(new BlockPos(cell.x, cell.y, cell.z)))
                    .toList();
            LIGHTING_COMPOSITION_CACHE.put(cacheKey, List.copyOf(composition));
            LIGHTING_COMPOSITION_LOCKS.remove(cacheKey, lock);
        }
    }

    /**
     * Re-runs the same authored-light composer against each cumulative visual stage. Late dressing
     * can create a roofed work platform or shade a previously safe floor, so proving only the base
     * shell is insufficient. New fixtures are written solely into the current append-only stage;
     * the earlier layers are copied into a temporary builder and remain immutable.
     */
    private static void ensureCumulativeComfortableInteriorLighting(
            Builder target,
            Metadata metadata,
            String templateId,
            String stageId,
            String dressingId,
            List<List<Cell>> layers) {
        String cacheKey = templateId + "@" + LATEST_TEMPLATE_REVISION
                + "/" + stageId
                + "/" + dressingId
                + "/scene-" + Math.floorMod(metadata.doodadSeed, 4L);
        List<Cell> cached = LIGHTING_COMPOSITION_CACHE.get(cacheKey);
        if (cached != null) {
            replayLightingComposition(target, cached);
            return;
        }
        Object lock = LIGHTING_COMPOSITION_LOCKS.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            cached = LIGHTING_COMPOSITION_CACHE.get(cacheKey);
            if (cached != null) {
                replayLightingComposition(target, cached);
                return;
            }
            Builder cumulative = new Builder(Set.of());
            for (List<Cell> layer : layers) {
                for (Cell cell : layer) {
                    cumulative.put(cell.phase, cell.x, cell.y, cell.z, cell.state);
                }
            }
            Set<BlockPos> before = cumulative.positions();
            composeComfortableInteriorLighting(
                    cumulative, metadata, templateId + "/" + stageId);
            List<Cell> composition = cumulative.values().stream()
                    .filter(cell -> !before.contains(new BlockPos(cell.x, cell.y, cell.z)))
                    .toList();
            replayLightingComposition(target, composition);
            LIGHTING_COMPOSITION_CACHE.put(cacheKey, List.copyOf(composition));
            LIGHTING_COMPOSITION_LOCKS.remove(cacheKey, lock);
        }
    }

    private static void replayLightingComposition(Builder builder, List<Cell> composition) {
        for (Cell cell : composition) {
            builder.put(cell.phase, cell.x, cell.y, cell.z, cell.state);
        }
    }

    private static void composeComfortableInteriorLighting(
            Builder builder, Metadata metadata, String templateId) {
        Map<BlockPos, Map<Voxel, Integer>> fixtureLightCache = new HashMap<>();
        for (int fixtureCount = 0; fixtureCount < 48; fixtureCount++) {
            List<Cell> authored = builder.values();
            Map<BlockPos, Cell> authoredCells = cellMap(authored);
            Map<BlockPos, BlockState> states = stateMap(authoredCells);
            LightingSnapshot snapshot = lightingSnapshot(
                    templateId + "@" + LATEST_TEMPLATE_REVISION + "/base-lighting",
                    metadata.height,
                    states);
            ValidationReport report = WholeBuildingLightingValidator.validate(snapshot);
            if (report.comfortablyLit()) {
                return;
            }
            if (fixtureCount == 47) {
                throw new IllegalStateException(
                        "Interior lighting exceeded the authored fixture limit in " + templateId
                                + " (floors=" + snapshot.traversableInteriorFloors().size()
                                + ", emitters=" + snapshot.emitters().size()
                                + ", deficient=" + report.issues().size()
                                + ", first=" + report.issues().stream().limit(3).toList() + ")");
            }

            LightingMount mount = bestLightingMount(
                    authoredCells,
                    snapshot,
                    report,
                    metadata,
                    fixtureLightCache);
            if (mount == null) {
                throw new IllegalStateException(
                        "No naturally supported, well-spaced fixture can improve dark interior in "
                                + templateId + " (emitters=" + snapshot.emitters().size() + "): "
                                + report.issues().stream().limit(8).toList());
            }
            installLightingMount(builder, mount);
            addCivicLightingPartner(builder, metadata, snapshot, mount);
        }
        throw new IllegalStateException("Unreachable lighting iteration limit in " + templateId);
    }

    private static LightingMount bestLightingMount(
            Map<BlockPos, Cell> cells,
            LightingSnapshot snapshot,
            ValidationReport currentReport,
            Metadata metadata,
            Map<BlockPos, Map<Voxel, Integer>> fixtureLightCache) {
        List<Voxel> deficientFloors = currentReport.issues().stream()
                .filter(issue -> issue.position() != null
                        && (issue.code() == Code.SPAWN_UNSAFE_TRAVERSABLE_FLOOR
                                || issue.code() == Code.DIM_TRAVERSABLE_FLOOR))
                .map(WholeBuildingLightingValidator.Issue::position)
                .distinct()
                .sorted()
                .toList();
        LightingMount local = bestLightingMount(
                cells,
                snapshot,
                currentReport,
                metadata,
                fixtureLightCache,
                deficientFloors);
        if (local != null) {
            return local;
        }
        // A high void may not have a support immediately over the dark tile. Search neighboring
        // walkable bays only after direct dark-zone mounts have been exhausted.
        return bestLightingMount(
                cells,
                snapshot,
                currentReport,
                metadata,
                fixtureLightCache,
                snapshot.traversableInteriorFloors().stream().sorted().toList());
    }

    private static LightingMount bestLightingMount(
            Map<BlockPos, Cell> cells,
            LightingSnapshot snapshot,
            ValidationReport currentReport,
            Metadata metadata,
            Map<BlockPos, Map<Voxel, Integer>> fixtureLightCache,
            List<Voxel> candidateFloors) {
        LightingMount best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voxel floorVoxel : candidateFloors) {
            BlockPos floor = blockPos(floorVoxel);
            Map<BlockPos, LightingMount> candidates = new HashMap<>();
            LightingMount pendant = supportedLightingMount(
                    cells, floor, metadata.height, maximumLightingChain(metadata.type));
            if (pendant != null) {
                candidates.put(pendant.emitter(), pendant);
            }
            for (LightingMount wall : supportedWallLightingMounts(cells, floor, metadata.height)) {
                candidates.putIfAbsent(wall.emitter(), wall);
            }
            for (LightingMount candidate : candidates.values()) {
                if (!lightingMountPreservesReservedAir(metadata, candidate)
                        || !lightingFixtureSpaced(cells, candidate)) {
                    continue;
                }
                Map<Voxel, Integer> candidateLight = fixtureLightCache.computeIfAbsent(
                        candidate.emitter(),
                        ignored -> WholeBuildingLightingValidator.validate(
                                singleMountSnapshot(snapshot, candidate)).blockLight());
                int resolved = resolvedLightingDeficiencies(
                        currentReport, candidateLight, snapshot.traversableInteriorFloors());
                if (resolved <= 0) {
                    continue;
                }
                int score = resolved * 100_000
                        + comfortableLightGain(currentReport, candidateLight,
                                snapshot.traversableInteriorFloors()) * 100
                        + lightingAestheticScore(metadata, candidate)
                        - candidate.chains().size();
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static int lightingDeficiencyCount(ValidationReport report) {
        int count = 0;
        for (WholeBuildingLightingValidator.Issue issue : report.issues()) {
            if (issue.code() == Code.SPAWN_UNSAFE_TRAVERSABLE_FLOOR
                    || issue.code() == Code.DIM_TRAVERSABLE_FLOOR) {
                count++;
            }
        }
        return count;
    }

    private static int comfortableLightGain(
            ValidationReport before,
            Map<Voxel, Integer> candidateLight,
            Set<Voxel> floors) {
        int gain = 0;
        for (Voxel floor : floors) {
            int oldLight = Math.min(
                    WholeBuildingLightingValidator.MINIMUM_COMFORTABLE_BLOCK_LIGHT,
                    before.blockLightAt(floor));
            int newLight = Math.min(
                    WholeBuildingLightingValidator.MINIMUM_COMFORTABLE_BLOCK_LIGHT,
                    Math.max(before.blockLightAt(floor), candidateLight.getOrDefault(floor, 0)));
            gain += Math.max(0, newLight - oldLight);
        }
        return gain;
    }

    private static int resolvedLightingDeficiencies(
            ValidationReport before,
            Map<Voxel, Integer> candidateLight,
            Set<Voxel> floors) {
        int resolved = 0;
        for (Voxel floor : floors) {
            if (before.blockLightAt(floor)
                            < WholeBuildingLightingValidator.MINIMUM_COMFORTABLE_BLOCK_LIGHT
                    && Math.max(before.blockLightAt(floor), candidateLight.getOrDefault(floor, 0))
                            >= WholeBuildingLightingValidator.MINIMUM_COMFORTABLE_BLOCK_LIGHT) {
                resolved++;
            }
        }
        return resolved;
    }

    private static LightingSnapshot singleMountSnapshot(
            LightingSnapshot snapshot, LightingMount mount) {
        return new LightingSnapshot(
                snapshot.id() + "/single-candidate",
                snapshot.bounds(),
                snapshot.traversableInteriorFloors(),
                snapshot.lightDampening(),
                List.of(new LightEmitter(
                        voxel(mount.emitter()), mount.light().getLightEmission())));
    }

    private static LightingSnapshot snapshotWithMount(
            LightingSnapshot snapshot, LightingMount mount) {
        List<LightEmitter> emitters = new ArrayList<>(snapshot.emitters());
        emitters.add(new LightEmitter(voxel(mount.emitter()), mount.light().getLightEmission()));
        return new LightingSnapshot(
                snapshot.id() + "/candidate",
                snapshot.bounds(),
                snapshot.traversableInteriorFloors(),
                snapshot.lightDampening(),
                emitters);
    }

    private static LightingMount supportedLightingMount(
            Map<BlockPos, Cell> cells,
            BlockPos floor,
            int height,
            int maximumChainLength) {
        BlockPos lowestLantern = floor.above(2);
        for (int ceilingY = lowestLantern.getY() + 1; ceilingY <= height; ceilingY++) {
            BlockPos ceiling = new BlockPos(floor.getX(), ceilingY, floor.getZ());
            Cell support = cells.get(ceiling);
            if (support == null) {
                continue;
            }
            if (support.phase != Phase.FRAME
                    && support.phase != Phase.SHELL
                    && support.phase != Phase.ROOF) {
                return null;
            }
            if (!support.state.isFaceSturdy(
                    EmptyBlockGetter.INSTANCE, ceiling, Direction.DOWN)) {
                return null;
            }
            int lanternY = Math.max(
                    lowestLantern.getY(), ceilingY - maximumChainLength - 1);
            BlockPos lantern = new BlockPos(floor.getX(), lanternY, floor.getZ());
            if (cells.containsKey(lantern)) {
                return null;
            }
            List<BlockPos> chains = new ArrayList<>();
            for (int y = lanternY + 1; y < ceilingY; y++) {
                BlockPos chain = new BlockPos(floor.getX(), y, floor.getZ());
                if (cells.containsKey(chain)) {
                    return null;
                }
                chains.add(chain);
            }
            return new LightingMount(
                    floor,
                    lantern,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
                    List.copyOf(chains),
                    LightingMountStyle.PENDANT);
        }
        return null;
    }

    private static List<LightingMount> supportedWallLightingMounts(
            Map<BlockPos, Cell> cells, BlockPos floor, int height) {
        List<LightingMount> mounts = new ArrayList<>();
        for (int fixtureY = Math.min(height, floor.getY() + 2);
                fixtureY >= floor.getY() + 1;
                fixtureY--) {
            BlockPos fixture = new BlockPos(floor.getX(), fixtureY, floor.getZ());
            if (cells.containsKey(fixture)) {
                continue;
            }
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos supportPosition = fixture.relative(facing.getOpposite());
                Cell support = cells.get(supportPosition);
                if (support == null
                        || (support.phase != Phase.FRAME
                                && support.phase != Phase.SHELL
                                && support.phase != Phase.FOUNDATION)
                        || !support.state.isFaceSturdy(
                                EmptyBlockGetter.INSTANCE, supportPosition, facing)) {
                    continue;
                }
                BlockState sconce = Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, facing);
                mounts.add(new LightingMount(
                        floor, fixture, sconce, List.of(), LightingMountStyle.WALL_SCONCE));
            }
            if (!mounts.isEmpty()) {
                break;
            }
        }
        return List.copyOf(mounts);
    }

    private static int maximumLightingChain(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE, HOUSE, INN -> 2;
            case WAREHOUSE, GRANARY, SMITHY -> 1;
            case EXCHANGE_HALL, GUARD_POST -> 4;
            case MINE_ENTRANCE, MARKET_SQUARE -> 2;
        };
    }

    private static int lightingAestheticScore(Metadata metadata, LightingMount mount) {
        BlockPos floor = mount.floor();
        int edgeDistance = Math.min(
                Math.min(floor.getX(), metadata.width - 1 - floor.getX()),
                Math.min(floor.getZ(), metadata.depth - 1 - floor.getZ()));
        int centerOffset = Math.abs(floor.getX() - metadata.width / 2);
        return switch (metadata.type) {
            case COTTAGE, HOUSE, INN -> mount.style() == LightingMountStyle.PENDANT
                    && mount.chains().size() <= 1 ? 24 : edgeDistance <= 2 ? 16 : 0;
            case WAREHOUSE, GRANARY, SMITHY -> mount.style() == LightingMountStyle.WALL_SCONCE
                    ? 24 : edgeDistance <= 3 ? 10 : 0;
            case EXCHANGE_HALL, GUARD_POST -> mount.style() == LightingMountStyle.PENDANT
                    && centerOffset >= 2 && centerOffset <= 4 ? 32 : 0;
            case MINE_ENTRANCE -> mount.style() == LightingMountStyle.WALL_SCONCE
                    ? 28 : edgeDistance <= 2 ? 14 : 0;
            case MARKET_SQUARE -> mount.style() == LightingMountStyle.PENDANT
                    && mount.chains().size() <= 2 ? 24 : edgeDistance <= 2 ? 12 : 0;
        };
    }

    private static boolean lightingFixtureSpaced(
            Map<BlockPos, Cell> cells, LightingMount candidate) {
        for (Map.Entry<BlockPos, Cell> entry : cells.entrySet()) {
            if (entry.getValue().state.getLightEmission() <= 0) {
                continue;
            }
            BlockPos source = entry.getKey();
            int distance = Math.abs(source.getX() - candidate.emitter().getX())
                    + Math.abs(source.getY() - candidate.emitter().getY())
                    + Math.abs(source.getZ() - candidate.emitter().getZ());
            if (distance < 3) {
                return false;
            }
        }
        return true;
    }

    private static boolean lightingMountPreservesReservedAir(
            Metadata metadata, LightingMount candidate) {
        if (metadata.reservedAir.contains(candidate.emitter())) {
            return false;
        }
        for (BlockPos chain : candidate.chains()) {
            if (metadata.reservedAir.contains(chain)) {
                return false;
            }
        }
        return true;
    }

    private static void installLightingMount(Builder builder, LightingMount mount) {
        builder.put(
                Phase.DECOR,
                mount.emitter().getX(),
                mount.emitter().getY(),
                mount.emitter().getZ(),
                mount.light());
        for (BlockPos chain : mount.chains()) {
            builder.put(
                    Phase.DECOR,
                    chain.getX(),
                    chain.getY(),
                    chain.getZ(),
                    Blocks.IRON_CHAIN.defaultBlockState());
        }
    }

    private static void addCivicLightingPartner(
            Builder builder,
            Metadata metadata,
            LightingSnapshot snapshot,
            LightingMount first) {
        if ((metadata.type != VillageProsperityEngine.ProjectType.EXCHANGE_HALL
                        && metadata.type != VillageProsperityEngine.ProjectType.GUARD_POST)
                || first.style() != LightingMountStyle.PENDANT) {
            return;
        }
        int partnerX = metadata.width - 1 - first.emitter().getX();
        if (partnerX == first.emitter().getX()) {
            return;
        }
        BlockPos partnerFloor = new BlockPos(
                partnerX,
                first.floor().getY(),
                first.emitter().getZ());
        if (!snapshot.traversableInteriorFloors().contains(voxel(partnerFloor))) {
            return;
        }
        Map<BlockPos, Cell> cells = cellMap(builder.values());
        LightingMount partner = supportedLightingMount(
                cells,
                partnerFloor,
                metadata.height,
                maximumLightingChain(metadata.type));
        LightingSnapshot afterFirst = snapshotWithMount(snapshot, first);
        ValidationReport afterFirstReport = WholeBuildingLightingValidator.validate(afterFirst);
        if (partner != null
                && lightingMountPreservesReservedAir(metadata, partner)
                && lightingFixtureSpaced(cells, partner)
                && lightingDeficiencyCount(WholeBuildingLightingValidator.validate(
                        snapshotWithMount(afterFirst, partner)))
                        < lightingDeficiencyCount(afterFirstReport)) {
            installLightingMount(builder, partner);
        }
    }

    private static void validateCoveredFloorLighting(
            Blueprint blueprint,
            String stageId,
            Map<BlockPos, BlockState> authored) {
        LightingSnapshot snapshot = lightingSnapshot(
                blueprint.id + "@" + blueprint.revision + "/"
                        + blueprint.paletteId + "/" + blueprint.dressingId + "/" + stageId,
                blueprint.height,
                authored);
        LightingAdmissionKey key = new LightingAdmissionKey(
                snapshot.bounds(),
                snapshot.traversableInteriorFloors(),
                snapshot.lightDampening(),
                Set.copyOf(snapshot.emitters()));
        VALIDATED_LIGHTING_SNAPSHOTS.computeIfAbsent(key, ignored -> {
            WholeBuildingLightingValidator.validate(snapshot).requireComfortablyLit();
            return Boolean.TRUE;
        });
    }

    private static LightingSnapshot lightingSnapshot(
            String id,
            int height,
            Map<BlockPos, BlockState> authored) {
        Bounds bounds = authoredLightingBounds(height, authored);
        Set<Voxel> floors = coveredSpawnableFloors(height, authored, bounds);
        Map<Voxel, Integer> dampening = new HashMap<>();
        List<LightEmitter> emitters = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            Voxel position = voxel(entry.getKey());
            if (!bounds.contains(position)) {
                continue;
            }
            BlockState state = entry.getValue();
            int stateDampening = conservativeLightDampening(state);
            if (stateDampening > 0) {
                dampening.put(position, stateDampening);
            }
            int emission = state.getLightEmission();
            if (emission > 0) {
                emitters.add(new LightEmitter(position, emission));
            }
        }
        return new LightingSnapshot(id, bounds, floors, dampening, emitters);
    }

    /**
     * Uses the real cumulative authored footprint rather than the nominal descriptor rectangle.
     * Tight bounds are conservative: light may not leave the authored envelope and re-enter around
     * an eave, so every accepted floor remains safe without depending on exterior ambient space.
     */
    private static Bounds authoredLightingBounds(
            int height, Map<BlockPos, BlockState> authored) {
        if (authored.isEmpty()) {
            throw new IllegalStateException("Cannot derive lighting bounds from an empty stage");
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos position : authored.keySet()) {
            minX = Math.min(minX, position.getX());
            maxX = Math.max(maxX, position.getX());
            minZ = Math.min(minZ, position.getZ());
            maxZ = Math.max(maxZ, position.getZ());
        }
        return new Bounds(new Voxel(minX, 1, minZ), new Voxel(maxX, height, maxZ));
    }

    private static Set<Voxel> interiorSpawnableFloors(
            int width,
            int depth,
            int height,
            Map<BlockPos, Cell> baseCells,
            Map<BlockPos, BlockState> base,
            Map<BlockPos, BlockState> complete) {
        Set<BlockPos> exteriorAir = exteriorAir(width, depth, height, base);
        Set<Voxel> floors = new HashSet<>();
        for (int y = 1; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    BlockPos feet = new BlockPos(x, y, z);
                    Cell floor = baseCells.get(feet.below());
                    if (floor == null || !structuralFloor(floor.phase)
                            || complete.containsKey(feet)
                            || complete.containsKey(feet.above())
                            || exteriorAir.contains(feet)
                            || !weatherCovered(base, feet, height)) {
                        continue;
                    }
                    floors.add(voxel(feet));
                }
            }
        }
        return Set.copyOf(floors);
    }

    /**
     * Returns every weather-covered, two-block-clear standing cell backed by an authored block
     * state that Minecraft accepts as a hostile-spawn support. Unlike
     * {@link #interiorSpawnableFloors}, this deliberately retains cells connected to exterior air:
     * an open smithy bay or roofed market stall is still a valid hostile spawn surface at night and
     * must receive the same comfortable block-light guarantee.
     */
    private static Set<Voxel> coveredSpawnableFloors(
            int height,
            Map<BlockPos, BlockState> authored,
            Bounds bounds) {
        Set<Voxel> floors = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            BlockPos support = entry.getKey();
            BlockPos feet = support.above();
            if (feet.getY() < 1
                    || feet.getY() >= height
                    || !bounds.contains(voxel(feet))
                    || !entry.getValue().isValidSpawn(
                            EmptyBlockGetter.INSTANCE, support, EntityTypes.ZOMBIE)
                    || authored.containsKey(feet)
                    || authored.containsKey(feet.above())
                    || !weatherCovered(authored, feet, height)) {
                continue;
            }
            floors.add(voxel(feet));
        }
        return Set.copyOf(floors);
    }

    private static boolean structuralFloor(Phase phase) {
        return phase == Phase.FOUNDATION || phase == Phase.FRAME || phase == Phase.SHELL;
    }

    private static boolean weatherCovered(
            Map<BlockPos, BlockState> authored, BlockPos feet, int height) {
        for (int y = feet.getY() + 2; y <= height; y++) {
            if (authored.containsKey(new BlockPos(feet.getX(), y, feet.getZ()))) {
                return true;
            }
        }
        return false;
    }

    private static Set<BlockPos> exteriorAir(
            int width, int depth, int height, Map<BlockPos, BlockState> base) {
        int minX = -1;
        int maxX = width;
        int minZ = -1;
        int maxZ = depth;
        int maxY = height + 1;
        Set<BlockPos> outside = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos seed = new BlockPos(minX, 1, minZ);
        outside.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getX() < minX || next.getX() > maxX
                        || next.getZ() < minZ || next.getZ() > maxZ
                        || next.getY() < 1 || next.getY() > maxY
                        || base.containsKey(next) || !outside.add(next)) {
                    continue;
                }
                queue.addLast(next);
            }
        }
        return outside;
    }

    private static int conservativeLightDampening(BlockState state) {
        int dampening = state.getLightDampening();
        if (dampening > 0) {
            return Math.min(WholeBuildingLightingValidator.MAXIMUM_BLOCK_LIGHT, dampening);
        }
        // Minecraft's light engine also considers face shapes. Treat uncertain occluding shapes as
        // fully opaque instead of allowing an admission decision to depend on a palette-specific
        // stair, slab, door or trim gap.
        return state.canOcclude() || state.useShapeForLightOcclusion()
                ? WholeBuildingLightingValidator.MAXIMUM_BLOCK_LIGHT
                : 0;
    }

    private static Map<BlockPos, Cell> cellMap(List<Cell> cells) {
        Map<BlockPos, Cell> result = new HashMap<>();
        for (Cell cell : cells) {
            BlockPos position = new BlockPos(cell.x, cell.y, cell.z);
            if (result.put(position, cell) != null) {
                throw new IllegalStateException("Duplicate authored blueprint cell at " + position);
            }
        }
        return result;
    }

    private static Map<BlockPos, BlockState> stateMap(Map<BlockPos, Cell> cells) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (Map.Entry<BlockPos, Cell> entry : cells.entrySet()) {
            result.put(entry.getKey(), entry.getValue().state);
        }
        return result;
    }

    private static Voxel voxel(BlockPos position) {
        return new Voxel(position.getX(), position.getY(), position.getZ());
    }

    private static BlockPos blockPos(Voxel position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private enum LightingMountStyle {
        PENDANT,
        WALL_SCONCE
    }

    private record LightingMount(
            BlockPos floor,
            BlockPos emitter,
            BlockState light,
            List<BlockPos> chains,
            LightingMountStyle style) {
    }

    /** Exact topology key: cached acceptance can never cross a changed floor, wall, or emitter. */
    private record LightingAdmissionKey(
            Bounds bounds,
            Set<Voxel> floors,
            Map<Voxel, Integer> dampening,
            Set<LightEmitter> emitters) {
    }

    /** Descriptor height is inclusive: a value of 15 may use y=15, but never y=16. */
    private static void validateAuthoredEnvelope(Blueprint blueprint) {
        for (List<Cell> cells : List.of(blueprint.base, blueprint.stageOne, blueprint.stageTwo)) {
            for (Cell cell : cells) {
                if (cell.y < 0 || cell.y > blueprint.height) {
                    throw new IllegalStateException(
                            "Authored cell exceeds descriptor height in " + blueprint.id
                                    + "@" + blueprint.revision + ": "
                                    + new BlockPos(cell.x, cell.y, cell.z));
                }
            }
        }
    }

    private static void validateRoofGeometry(Blueprint blueprint) {
        List<Cell> cumulative = new ArrayList<>(blueprint.base);
        RoofGeometryValidator.validate(
                roofGeometrySnapshot(blueprint, cumulative, "base")).requireValid();
        cumulative.addAll(blueprint.stageOne);
        RoofGeometryValidator.validate(
                roofGeometrySnapshot(blueprint, cumulative, "stage-one")).requireValid();
        cumulative.addAll(blueprint.stageTwo);
        RoofGeometryValidator.validate(
                roofGeometrySnapshot(blueprint, cumulative, "stage-two")).requireValid();
    }

    private static RoofSnapshot roofGeometrySnapshot(
            Blueprint blueprint, List<Cell> authoredCells, String stageId) {
        List<GeometryCell> geometry = new ArrayList<>(authoredCells.size());
        for (Cell cell : authoredCells) {
            Kind kind;
            if (isHangingFeature(cell.state)) {
                kind = Kind.HANGING_FEATURE;
            } else if (cell.phase == Phase.FOUNDATION) {
                kind = Kind.FOUNDATION;
            } else if (cell.phase == Phase.ROOF && isRooftopFeature(blueprint, cell)) {
                kind = Kind.ROOFTOP_FEATURE;
            } else if (cell.phase == Phase.ROOF) {
                // Authored roof phases include conventional slopes, flat sheets, pergola beams,
                // chimneys and isolated caps. Treat the phase as one structural course graph:
                // isolated details still fail when they lack a load path, without misclassifying
                // a long pergola beam as a series of ornaments that each need a post below.
                kind = Kind.ROOF_COURSE;
            } else if (cell.phase == Phase.FRAME || cell.phase == Phase.SHELL) {
                kind = Kind.STRUCTURE;
            } else {
                // Openings, furniture, signs, standing lights, plants, and other dressing may
                // touch a roof in the block grid, but they are not architectural load paths.
                kind = Kind.NON_LOAD_BEARING;
            }
            geometry.add(new GeometryCell(
                    new Voxel(cell.x, cell.y, cell.z),
                    kind,
                    roofOccupancy(cell.state),
                    cell.y == 0 && kind == Kind.FOUNDATION));
        }
        return new RoofSnapshot(
                blueprint.id + "@" + blueprint.revision + "/"
                        + blueprint.paletteId + "/" + blueprint.dressingId + "/" + stageId,
                geometry);
    }

    private static boolean isRooftopFeature(Blueprint blueprint, Cell cell) {
        return cell.phase == Phase.ROOF
                && (cell.state.is(blueprint.materials.chimney)
                        || cell.state.is(blueprint.materials.accent));
    }

    private static Occupancy roofOccupancy(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)) {
            return Occupancy.FULL;
        }
        return switch (state.getValue(SlabBlock.TYPE)) {
            case BOTTOM -> Occupancy.LOWER_HALF;
            case TOP -> Occupancy.UPPER_HALF;
            case DOUBLE -> Occupancy.FULL;
        };
    }

    private static boolean isHangingFeature(BlockState state) {
        if (state.is(Blocks.IRON_CHAIN)) {
            return true;
        }
        if (state.getBlock() instanceof LanternBlock) {
            return state.getValue(LanternBlock.HANGING);
        }
        return state.getBlock() instanceof BellBlock
                && state.getValue(BellBlock.ATTACHMENT) == BellAttachType.CEILING;
    }

    private static Map<BlockPos, BlockState> map(List<Cell> cells) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (Cell cell : cells) {
            BlockPos position = new BlockPos(cell.x, cell.y, cell.z);
            if (result.put(position, cell.state) != null) {
                throw new IllegalStateException("Duplicate authored blueprint cell at " + position);
            }
        }
        return result;
    }

    private static void appendDisjoint(
            Map<BlockPos, BlockState> occupied, List<Cell> cells, String id) {
        for (Cell cell : cells) {
            BlockPos position = new BlockPos(cell.x, cell.y, cell.z);
            if (occupied.put(position, cell.state) != null) {
                throw new IllegalStateException("Blueprint stage overlap in " + id + " at " + position);
            }
        }
    }

    private static void validateNavigation(Blueprint blueprint, Map<BlockPos, BlockState> base) {
        BlockPos start = blueprint.metadata.entranceInside;
        if (start == null || !walkable(base, start)) {
            throw new IllegalStateException("Blueprint entrance is not walkable: " + blueprint.id);
        }
        Set<BlockPos> visited = new HashSet<>(interiorCirculation(
                blueprint.width,
                blueprint.depth,
                base,
                start,
                blueprint.metadata.verticalAccess));
        for (VerticalAccess access : blueprint.metadata.verticalAccess) {
            BlockPos upperExit = access.to.above(2);
            if (!walkable(base, upperExit)) {
                throw new IllegalStateException(
                        "Blueprint vertical route has no clear upper dismount at "
                                + upperExit + " in " + blueprint.id);
            }
        }
        for (BlockPos target : blueprint.metadata.accessTargets) {
            if (!visited.contains(target)) {
                throw new IllegalStateException(
                        "Blueprint has an unreachable bed/workstation access tile at "
                            + target + " in " + blueprint.id
                            + navigationFailureContext(base, visited, target));
            }
        }
    }

    private static String navigationFailureContext(
            Map<BlockPos, BlockState> base,
            Set<BlockPos> visited,
            BlockPos target) {
        StringBuilder context = new StringBuilder(" [walkable=")
                .append(walkable(base, target))
                .append(", visited=")
                .append(visited.size())
                .append(", feet=")
                .append(base.get(target))
                .append(", head=")
                .append(base.get(target.above()))
                .append(", neighbors=");
        boolean first = true;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = target.relative(direction);
            if (!first) {
                context.append(';');
            }
            first = false;
            context.append(direction.getSerializedName())
                    .append(':')
                    .append(visited.contains(neighbor) ? "visited" : "unvisited")
                    .append('/')
                    .append(walkable(base, neighbor) ? "walkable" : "blocked");
        }
        return context.append(']').toString();
    }

    private static void validateInteriorConnectivity(
            Blueprint blueprint,
            Map<BlockPos, BlockState> base,
            Map<BlockPos, BlockState> complete) {
        Set<BlockPos> circulation = interiorCirculation(
                blueprint.width,
                blueprint.depth,
                base,
                blueprint.metadata.entranceInside,
                blueprint.metadata.verticalAccess);
        Map<BlockPos, Cell> baseCells = cellMap(blueprint.base);
        List<BlockPos> closedPockets = interiorSpawnableFloors(
                        blueprint.width,
                        blueprint.depth,
                        blueprint.height,
                        baseCells,
                        base,
                        complete)
                .stream()
                .map(AuthoredVillageStructures::blockPos)
                .filter(position -> !circulation.contains(position))
                .sorted()
                .toList();
        if (!closedPockets.isEmpty()) {
            throw new IllegalStateException(
                    "Blueprint contains enclosed spawnable floor disconnected from authored "
                            + "circulation in " + blueprint.id + ": "
                            + closedPockets.stream().limit(12).toList());
        }
    }

    /**
     * Returns actor-reachable standing cells on the entrance and declared upper floor levels. The
     * admission gate compares this set with every covered spawnable floor, so a sealed room or roof
     * pocket cannot disappear from either connectivity or lighting validation.
     */
    private static Set<BlockPos> interiorCirculation(
            int width,
            int depth,
            Map<BlockPos, BlockState> base,
            BlockPos entranceInside,
            List<VerticalAccess> verticalAccess) {
        if (entranceInside == null || !walkable(base, entranceInside)) {
            return Set.of();
        }
        Set<BlockPos> visited = new HashSet<>();
        floodWalkableLevel(width, depth, base, entranceInside, visited);
        List<VerticalAccess> pending = new ArrayList<>(verticalAccess);
        boolean advanced;
        do {
            advanced = false;
            for (Iterator<VerticalAccess> iterator = pending.iterator(); iterator.hasNext();) {
                VerticalAccess access = iterator.next();
                if (!reachableLowerBoarding(base, visited, access)) {
                    continue;
                }
                for (int y = access.from.getY(); y <= access.to.getY() + 2; y++) {
                    BlockPos ladderColumn = new BlockPos(
                            access.from.getX(), y, access.from.getZ());
                    if (walkable(base, ladderColumn)) {
                        floodWalkableLevel(width, depth, base, ladderColumn, visited);
                    }
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos landing = ladderColumn.relative(direction);
                        if (walkable(base, landing)) {
                            floodWalkableLevel(width, depth, base, landing, visited);
                        }
                    }
                }
                BlockPos upperExit = access.to.above(2);
                if (walkable(base, upperExit)) {
                    floodWalkableLevel(width, depth, base, upperExit, visited);
                }
                iterator.remove();
                advanced = true;
            }
        } while (advanced && !pending.isEmpty());
        return Set.copyOf(visited);
    }

    /** A declared ladder is usable only after the entrance flood reaches its lower boarding bay. */
    private static boolean reachableLowerBoarding(
            Map<BlockPos, BlockState> base,
            Set<BlockPos> visited,
            VerticalAccess access) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos boarding = access.from.relative(direction);
            if (visited.contains(boarding) && walkable(base, boarding)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes genuinely inaccessible sealed voids from a gold master before lighting is composed.
     * The fill is deliberately solid rather than a hidden lamp: hostile-spawn safety must not
     * preserve an accidental room that players can never enter. The final admission gate repeats
     * the connectivity proof after dressing, so this normalization cannot silently miss a pocket.
     */
    private static void sealDisconnectedInteriorPockets(
            Builder builder,
            Metadata metadata,
            Materials materials,
            String templateId) {
        if (!metadata.enclosed) {
            return;
        }
        for (int pass = 0; pass <= metadata.height; pass++) {
            Map<BlockPos, Cell> cells = cellMap(builder.values());
            Map<BlockPos, BlockState> states = stateMap(cells);
            Set<BlockPos> circulation = interiorCirculation(
                    metadata.width,
                    metadata.depth,
                    states,
                    metadata.entranceInside,
                    metadata.verticalAccess);
            List<BlockPos> closedPockets = interiorSpawnableFloors(
                            metadata.width,
                            metadata.depth,
                            metadata.height,
                            cells,
                            states,
                            states)
                    .stream()
                    .map(AuthoredVillageStructures::blockPos)
                    .filter(position -> !circulation.contains(position))
                    .sorted()
                    .toList();
            if (closedPockets.isEmpty()) {
                return;
            }
            boolean changed = false;
            for (BlockPos pocket : closedPockets) {
                for (int y = pocket.getY(); y <= Math.min(metadata.height, pocket.getY() + 1); y++) {
                    BlockPos fill = new BlockPos(pocket.getX(), y, pocket.getZ());
                    if (!builder.contains(fill)) {
                        builder.put(
                                Phase.SHELL,
                                fill.getX(),
                                fill.getY(),
                                fill.getZ(),
                                materials.wall.defaultBlockState());
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        throw new IllegalStateException(
                "Unable to eliminate disconnected enclosed floor pockets in " + templateId);
    }

    private static void floodWalkableLevel(
            int width,
            int depth,
            Map<BlockPos, BlockState> base,
            BlockPos start,
            Set<BlockPos> visited) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (visited.add(start)) {
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (next.getX() < 0 || next.getX() >= width
                        || next.getZ() < 0 || next.getZ() >= depth
                        || next.getY() != start.getY()
                        || visited.contains(next)
                        || !walkable(base, next)) {
                    continue;
                }
                visited.add(next);
                queue.addLast(next);
            }
        }
    }

    private static boolean walkable(Map<BlockPos, BlockState> cells, BlockPos feet) {
        BlockState feetState = cells.get(feet);
        BlockState headState = cells.get(feet.above());
        boolean feetClear = feetState == null
                || feetState.getBlock() instanceof DoorBlock
                || feetState.is(Blocks.RAIL);
        boolean headClear = headState == null || headState.getBlock() instanceof DoorBlock;
        return cells.containsKey(feet.below()) && feetClear && headClear;
    }

    private static void validateVerticalAccess(Blueprint blueprint, Map<BlockPos, BlockState> base) {
        for (VerticalAccess access : blueprint.metadata.verticalAccess) {
            if (access.from.getX() != access.to.getX()
                    || access.from.getZ() != access.to.getZ()
                    || access.to.getY() <= access.from.getY()) {
                throw new IllegalStateException("Invalid vertical access metadata in " + blueprint.id);
            }
            for (int y = access.from.getY(); y <= access.to.getY(); y++) {
                BlockState state = base.get(new BlockPos(access.from.getX(), y, access.from.getZ()));
                if (state == null || !state.is(Blocks.LADDER)) {
                    throw new IllegalStateException(
                            "Blueprint vertical route is incomplete at y=" + y + " in " + blueprint.id);
                }
            }
            BlockState hatch = base.get(access.to.above());
            if (hatch == null || !(hatch.getBlock() instanceof TrapDoorBlock)) {
                throw new IllegalStateException(
                        "Blueprint vertical route is missing its weather-sealing hatch in "
                                + blueprint.id);
            }
        }
    }

    private static void validateAttachedSupports(
            Blueprint blueprint, Map<BlockPos, BlockState> cells) {
        AuthoredLightFixtureSupportValidator.validate(cells, blueprint.id);
        for (Map.Entry<BlockPos, BlockState> entry : cells.entrySet()) {
            BlockState state = entry.getValue();
            if (!(state.getBlock() instanceof LadderBlock)) {
                continue;
            }
            Direction facing = state.getValue(LadderBlock.FACING);
            BlockPos supportPosition = entry.getKey().relative(facing.getOpposite());
            BlockState support = cells.get(supportPosition);
            if (support == null
                    || !support.isFaceSturdy(
                            EmptyBlockGetter.INSTANCE, supportPosition, facing)) {
                throw new IllegalStateException(
                        "Blueprint ladder has no sturdy backing at "
                                + supportPosition
                                + " in "
                                + blueprint.id);
            }
        }
    }

    private static void validateSupport(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
        Set<BlockPos> unvisited = new HashSet<>(cells.keySet());
        while (!unvisited.isEmpty()) {
            BlockPos first = unvisited.iterator().next();
            boolean anchored = false;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(first);
            unvisited.remove(first);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                anchored |= current.getY() == 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos next = current.offset(dx, dy, dz);
                            if (unvisited.remove(next)) {
                                queue.addLast(next);
                            }
                        }
                    }
                }
            }
            if (!anchored) {
                throw new IllegalStateException(
                        "Blueprint contains a floating component beginning at "
                                + first + " in " + blueprint.id);
            }
        }
    }

    private static void validatePaneConnections(Blueprint blueprint, Map<BlockPos, BlockState> cells) {
        for (Map.Entry<BlockPos, BlockState> entry : cells.entrySet()) {
            if (!entry.getValue().is(Blocks.GLASS_PANE)
                    && !entry.getValue().is(Blocks.IRON_BARS)) {
                continue;
            }
            int neighbors = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (cells.containsKey(entry.getKey().relative(direction))) {
                    neighbors++;
                }
            }
            if (neighbors == 0) {
                throw new IllegalStateException(
                        "Blueprint contains an orphaned pane/bar at "
                                + entry.getKey() + " in " + blueprint.id);
            }
        }
    }

    private static void validateEnclosure(Blueprint blueprint, Map<BlockPos, BlockState> base) {
        Set<BlockPos> outside = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        int minX = -2;
        int maxX = blueprint.width + 1;
        int minZ = -3;
        int maxZ = blueprint.depth + 1;
        int maxY = blueprint.height + 1;
        BlockPos seed = new BlockPos(minX, 1, minZ);
        outside.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getX() < minX || next.getX() > maxX
                        || next.getZ() < minZ || next.getZ() > maxZ
                        || next.getY() < 1 || next.getY() > maxY
                        || base.containsKey(next) || !outside.add(next)) {
                    continue;
                }
                queue.addLast(next);
            }
        }
        for (BlockPos sample : blueprint.metadata.interiorSamples) {
            if (outside.contains(sample)) {
                throw new IllegalStateException(
                        "Blueprint shell/roof leaks into its interior at "
                                + sample + " in " + blueprint.id);
            }
            boolean covered = false;
            for (int y = sample.getY() + 1; y <= blueprint.height; y++) {
                if (base.containsKey(new BlockPos(sample.getX(), y, sample.getZ()))) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                throw new IllegalStateException(
                        "Blueprint interior has no weather cover at " + sample + " in " + blueprint.id);
            }
        }
    }

    private static boolean edge(int x, int z, int width, int depth) {
        return x == 0 || x == width - 1 || z == 0 || z == depth - 1;
    }

    private static boolean corner(int x, int z, int width, int depth) {
        return (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
    }

    enum Phase {
        FOUNDATION,
        FRAME,
        SHELL,
        ROOF,
        OPENING,
        FIXTURE,
        DECOR
    }

    enum Porch {
        OPEN,
        SMALL,
        BROAD,
        FORMAL,
        LOADING,
        WORKSHOP
    }

    record Cell(int x, int y, int z, BlockState state, Phase phase) {
    }

    record Blueprint(
            String id,
            int revision,
            String paletteId,
            String dressingId,
            int width,
            int depth,
            int height,
            List<Cell> base,
            List<Cell> stageOne,
            List<Cell> stageTwo,
            Materials materials,
            FrozenMetadata metadata) {
    }

    record FrozenMetadata(
            VillageProsperityEngine.ProjectType type,
            boolean enclosed,
            BlockPos entranceInside,
            Set<BlockPos> accessTargets,
            Set<BlockPos> reservedAir,
            Set<BlockPos> interiorSamples,
            List<VerticalAccess> verticalAccess) {
    }

    record VerticalAccess(BlockPos from, BlockPos to) {
    }

    record Materials(
            VillageArchitecture.BiomeDialect dialect,
            Block foundation,
            Block floor,
            Block wall,
            Block timber,
            Block roofStairs,
            Block roofSlab,
            Block fence,
            Block accent,
            Block door,
            Block entryStairs,
            Block chimney) {
    }

    private static final class Metadata {
        private VillageProsperityEngine.ProjectType type;
        private int width;
        private int depth;
        private int height;
        private long doodadSeed;
        private int wallHeight;
        private int roofPeak;
        private boolean enclosed;
        private BlockPos entranceInside;
        private final Set<BlockPos> accessTargets = new HashSet<>();
        private final Set<BlockPos> reservedAir = new HashSet<>();
        private final Set<BlockPos> interiorSamples = new HashSet<>();
        private final List<VerticalAccess> verticalAccess = new ArrayList<>();

        private void begin(
                VillageProsperityEngine.ProjectType type,
                int width,
                int depth,
                int height,
                boolean enclosed) {
            this.type = type;
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.enclosed = enclosed;
        }

        private void reserveCentralAisle(int x, int minZ, int maxZ) {
            for (int z = minZ; z <= maxZ; z++) {
                reservedAir.add(new BlockPos(x, 1, z));
                reservedAir.add(new BlockPos(x, 2, z));
            }
        }

        private FrozenMetadata freeze() {
            return new FrozenMetadata(
                    type,
                    enclosed,
                    entranceInside,
                    Set.copyOf(accessTargets),
                    Set.copyOf(reservedAir),
                    Set.copyOf(interiorSamples),
                    List.copyOf(verticalAccess));
        }
    }

    private static final class Builder {
        private static final Comparator<Cell> ORDER = Comparator
                .comparing(Cell::phase)
                .thenComparingInt(Cell::y)
                .thenComparingInt(Cell::z)
                .thenComparingInt(Cell::x);

        private final Set<BlockPos> blocked;
        private final LinkedHashMap<BlockPos, Cell> cells = new LinkedHashMap<>();

        private Builder(Set<BlockPos> blocked) {
            this.blocked = Set.copyOf(blocked);
        }

        private void put(Phase phase, int x, int y, int z, BlockState state) {
            BlockPos position = new BlockPos(x, y, z);
            if (blocked.contains(position)) {
                throw new IllegalStateException("Blueprint stage overlaps prior cell at " + position);
            }
            Cell previous = cells.putIfAbsent(position, new Cell(x, y, z, state, phase));
            if (previous != null && !previous.state.equals(state)) {
                throw new IllegalStateException(
                        "Blueprint cell collision at " + position + ": "
                                + previous.state + " -> " + state);
            }
        }

        private void force(Phase phase, int x, int y, int z, BlockState state) {
            BlockPos position = new BlockPos(x, y, z);
            if (blocked.contains(position)) {
                throw new IllegalStateException("Blueprint stage overlaps prior cell at " + position);
            }
            cells.put(position, new Cell(x, y, z, state, phase));
        }

        private void remove(int x, int y, int z) {
            cells.remove(new BlockPos(x, y, z));
        }

        private boolean contains(BlockPos position) {
            return cells.containsKey(position);
        }

        private Cell cellAt(BlockPos position) {
            return cells.get(position);
        }

        private boolean putIfFree(Phase phase, int x, int y, int z, BlockState state) {
            BlockPos position = new BlockPos(x, y, z);
            if (isOccupied(position)) {
                return false;
            }
            cells.put(position, new Cell(x, y, z, state, phase));
            return true;
        }

        private boolean isOccupied(BlockPos position) {
            return blocked.contains(position) || cells.containsKey(position);
        }

        private Set<BlockPos> positions() {
            return Set.copyOf(cells.keySet());
        }

        private List<Cell> values() {
            return cells.values().stream().sorted(ORDER).toList();
        }
    }
}
