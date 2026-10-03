package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only copies of CURRENT Plains designs, not new production descriptors or migrations. */
final class PlainsLegacyArchitecturePreview {
    record Source(Map<BlockPos, BlockState> cells, BlockPos entrance, Set<BlockPos> access,
            Set<BlockPos> roofCells, Set<BlockPos> seats) { }
    private static final Map<String, BiomeArchitecturePreview.Plan> CACHE = new HashMap<>();

    static synchronized BiomeArchitecturePreview.Plan plan(String role) {
        return CACHE.computeIfAbsent(role, PlainsLegacyArchitecturePreview::build);
    }

    static Source source(String role) {
        return source(role,role.equals("INN")?"inn_gallery_01":"house_cross_01");
    }
    static Source source(String role,String id) {
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        Set<BlockPos> roofCells = new HashSet<>();
        if (role.equals("BANK")) {
            // Uses the existing opt-in gallery boundary, never asks Bank construction to change.
            for (var cell : VillageBankManager.galleryBankBlueprint(BlockPos.ZERO, BiomeDialect.PLAINS))
                cells.put(cell.position(), cell.state());
            // The frozen Bank uses these dark-wood pieces in its upper roof and portico canopies;
            // its seats and low forecourt accents stay below this roof palette band.
            // The separate chair audit may correct a seat's direction, never its material/shape.
            cells.forEach((pos,state)-> { if(!oakRoof(state,pos.getY()).equals(state)) roofCells.add(pos); });
            return new Source(Map.copyOf(cells), new BlockPos(6, 1, 1), Set.of(new BlockPos(6, 1, 6)),
                    Set.copyOf(roofCells),PreviewSeatingAudit.lowStairs(cells));
        }
        var blueprint = AuthoredVillageStructures.plan(ProjectType.valueOf(role),
                id, 11, VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                VillageArchitecture.Character.MERCANTILE, BiomeDialect.PLAINS);
        Set<BlockPos> seats=new HashSet<>();
        for (var stage : List.of(blueprint.base(), blueprint.stageOne(), blueprint.stageTwo()))
            for (var cell : stage) {
                BlockPos pos=new BlockPos(cell.x(),cell.y(),cell.z());
                cells.put(pos,cell.state());
                if(cell.phase()==AuthoredVillageStructures.Phase.ROOF) roofCells.add(pos);
                else roofCells.remove(pos);
                seats.remove(pos);
                if(cell.state().getBlock() instanceof StairBlock
                        &&(cell.phase()==AuthoredVillageStructures.Phase.FIXTURE||cell.phase()==AuthoredVillageStructures.Phase.DECOR))
                    seats.add(pos);
            }
        // The original native admission handles upper floors, ladders and their protected routes.
        return new Source(Map.copyOf(cells), blueprint.metadata().entranceInside(),
                blueprint.metadata().accessTargets(), Set.copyOf(roofCells),Set.copyOf(seats));
    }

    private static BiomeArchitecturePreview.Plan build(String role) {
        return copy(source(role),role,"plains_legacy_" + role.toLowerCase(Locale.ROOT) + "_oak_roof");
    }
    static synchronized BiomeArchitecturePreview.Plan catalog(String role,String id) {
        return CACHE.computeIfAbsent("catalog_"+id,key->copy(source(role,id),role,"catalog_plains_"+id));
    }
    private static BiomeArchitecturePreview.Plan copy(Source original,String role,String id) {
        int minX = original.cells().keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minZ = original.cells().keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = original.cells().keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxZ = original.cells().keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        BlockPos offset = new BlockPos(-minX, 0, -minZ);
        Map<BlockPos, BlockState> copy = new LinkedHashMap<>();
        var corrected=PreviewSeatingAudit.corrected(original.cells(),original.seats());
        corrected.forEach((pos, state) -> copy.put(pos.offset(offset),
                original.roofCells().contains(pos) ? oakRoof(state,pos.getY()) : state));
        var sample = new BiomeArchitecturePreview.Sample(BiomeDialect.PLAINS, role,
                id, maxX-minX+1, maxZ-minZ+1);
        Set<BlockPos> access = new HashSet<>();
        original.access().forEach(pos -> access.add(pos.offset(offset)));
        return PreviewRoomLayout.apply(PreviewDoorwayAudit.correct(new BiomeArchitecturePreview.Plan(sample, Map.copyOf(copy), Set.copyOf(access),
                original.entrance().offset(offset),
                copy.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow()+1)));
    }

    /** Upper roof/awnings only: keep furniture and dark accent timber below eaves unchanged. */
    static BlockState oakRoof(BlockState state, int y) {
        if (y < 4) return state;
        Block target = state.is(Blocks.DARK_OAK_STAIRS) ? Blocks.OAK_STAIRS
                : state.is(Blocks.DARK_OAK_SLAB) ? Blocks.OAK_SLAB
                : state.is(Blocks.DARK_OAK_PLANKS) ? Blocks.OAK_PLANKS : null;
        // Facing, half, shape, waterlogging and slab type must survive the palette-only copy.
        return target == null ? state : target.withPropertiesOf(state);
    }
}
