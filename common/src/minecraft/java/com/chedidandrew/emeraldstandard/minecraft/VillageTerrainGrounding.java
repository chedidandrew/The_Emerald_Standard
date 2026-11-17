package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.SitePreparationPlan;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** New-lot earthworks only: freeze sound backing before an authored grassy shelf can hide the gap. */
final class VillageTerrainGrounding {
    static final int BLEND_RADIUS = TerrainFoundationPlan.MAX_TERRAIN_DROP;
    private VillageTerrainGrounding() { }

    static boolean earthFill(SitePreparationPlan.Cell cell) {
        boolean earth=cell.after().equals("minecraft:dirt") || cell.after().startsWith("minecraft:grass_block[")
                || cell.after().equals("minecraft:sandstone") || cell.after().equals("minecraft:red_sandstone")
                || cell.after().equals("minecraft:sand") || cell.after().equals("minecraft:red_sand")
                || cell.after().startsWith("minecraft:podzol[")||cell.after().startsWith("minecraft:mycelium[")
                ||cell.after().equals("minecraft:snow_block")||cell.after().equals("minecraft:stone")
                ||cell.after().equals("minecraft:andesite")||cell.after().equals("minecraft:diorite")
                ||cell.after().equals("minecraft:granite")||cell.after().equals("minecraft:tuff")
                ||cell.after().equals("minecraft:deepslate");
        if(!earth)return false;
        // A pocket's garden cap repaints existing soil; it is not the exclusive foundation skirt.
        // Fill receipts replace air/clearable cover above surveyed ground, never native soil.
        String id=cell.before().split("\\[",2)[0];
        var parsed=Identifier.tryParse(id);
        return parsed!=null&&!VillageSitePreparation.dryNaturalGround(BuiltInRegistries.BLOCK.getValue(parsed).defaultBlockState());
    }

    static SitePreparationPlan freeze(ServerLevel level, SitePreparationPlan base, BlockPos origin,
            Map<BlockPos, BlockState> authored, Set<BlockPos> occupied, List<BlockPos> route,
            UUID village, long project) {
        var survey = new VillageSitePreparation.Survey(level);
        Map<Long, BlockPos> pads = new TreeMap<>();
        for (var entry : authored.entrySet()) {
            BlockPos pos = entry.getKey(); BlockState state = entry.getValue();
            boolean yardSoil=state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.SAND)
                    ||state.is(Blocks.RED_SAND)||state.is(Blocks.FARMLAND)||state.is(Blocks.DIRT_PATH);
            // Terrace-offset soil may lie above/below the entry plane. Upper storeys and roof
            // masonry must not be mistaken for ground-contact slabs or filled from underneath.
            if (pos.getY() < origin.getY() - 1 - (yardSoil ? TerrainFoundationPlan.MAX_TERRAIN_DROP : 0)
                    || pos.getY() > origin.getY() + (yardSoil ? TerrainFoundationPlan.MAX_TERRAIN_CUT : 0)
                    || state.isAir() || !state.getFluidState().isEmpty()
                    || !state.isFaceSturdy(level, pos, Direction.UP)) continue;
            long key = column(pos);
            BlockPos previous=pads.get(key);
            // An ordinary ground floor outranks a soil planter on its balcony. For isolated
            // terrace soil above the entry plane, use the lowest bearing rather than a stacked cap.
            if (previous==null || pos.getY()<=origin.getY()
                        && (previous.getY()>origin.getY() || previous.getY()<pos.getY())
                    || previous.getY()>origin.getY() && pos.getY()<previous.getY()) pads.put(key,pos);
        }
        Map<Long, SitePreparationPlan.Cell> cells = new LinkedHashMap<>();
        for (var cell : base.cells()) cells.put(cell.position(), cell);
        Set<Long> approvedRemovals=new HashSet<>();
        for(var cell:base.cells())if(cell.after().equals("minecraft:air"))approvedRemovals.add(cell.position());
        Set<Long> excluded = new HashSet<>();
        occupied.forEach(p -> excluded.add(column(p)));
        route.forEach(p -> excluded.add(column(p)));
        Map<Long, BlockPos> skirt = new TreeMap<>();
        for (BlockPos pad : pads.values()) {
            Integer surface = survey.surface(pad.getX(), pad.getZ());
            if (surface == null || pad.getY() - surface > TerrainFoundationPlan.MAX_TERRAIN_DROP) return null;
            // Foundation is mandatory, the sloping landscape outside it is optional.
            var fill = fill(level, survey, pad.below(), surface, village, project, false,approvedRemovals);
            if (fill == null) return null;
            // Underground rooms/shaft air and their walls remain building work, not earthworks.
            // Ordinary synthetic TERRAIN_SUPPORT cells are excluded from authored by the caller.
            fill.keySet().removeIf(key -> authored.containsKey(BlockPos.of(key)));
            cells.putAll(fill);
            for (int dx = -BLEND_RADIUS; dx <= BLEND_RADIUS; dx++)
                for (int dz = -BLEND_RADIUS; dz <= BLEND_RADIUS; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dz);
                    if (distance == 0 || distance > BLEND_RADIUS) continue;
                    BlockPos edge = pad.offset(dx, -distance, dz);
                    long key = column(edge);
                    if (excluded.contains(key)) continue;
                    if (!skirt.containsKey(key) || skirt.get(key).getY() < edge.getY()) skirt.put(key, edge);
                }
        }
        for (BlockPos edge : skirt.values()) {
            Integer surface = survey.surface(edge.getX(), edge.getZ());
            if (surface == null || surface > edge.getY() || edge.getY() - surface >= BLEND_RADIUS) continue;
            var fill = fill(level, survey, edge, surface, village, project, true,approvedRemovals);
            if (fill != null) cells.putAll(fill);
        }
        if (cells.size() > SitePreparationPlan.MAX_CELLS) return null;
        return new SitePreparationPlan(cells.values().stream().sorted(Comparator
                .<SitePreparationPlan.Cell>comparingInt(c -> c.after().equals("minecraft:air") ? 0 : 1)
                .thenComparingInt(c -> c.after().equals("minecraft:air")
                        ? -BlockPos.of(c.position()).getY() : BlockPos.of(c.position()).getY())
                .thenComparingLong(SitePreparationPlan.Cell::position)).toList());
    }

    private static Map<Long, SitePreparationPlan.Cell> fill(ServerLevel level, VillageSitePreparation.Survey survey,
            BlockPos top, int surface, UUID village, long project, boolean grassCap,Set<Long> approvedRemovals) {
        if (surface > top.getY()) return Map.of(); // Already seated; never tunnel through a natural cave roof.
        BlockPos bearing = new BlockPos(top.getX(), surface - 1, top.getZ());
        if (!survey.loaded(bearing) || !survey.naturalSurroundings(bearing)) return null;
        BlockState soil = level.getBlockState(bearing);
        if (!VillageSitePreparation.dryNaturalGround(soil) || !soil.isFaceSturdy(level, bearing, Direction.UP)
                || !soil.getFluidState().isEmpty() || soil.hasBlockEntity()
                || !VillageDevelopmentProtection.mayPlace(level, village, project, bearing, soil, soil)) return null;
        var fill = soil.is(Blocks.SAND) ? Blocks.SANDSTONE : soil.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE : Blocks.DIRT;
        var cap = soil.is(Blocks.SAND) ? Blocks.SAND : soil.is(Blocks.RED_SAND) ? Blocks.RED_SAND : Blocks.GRASS_BLOCK;
        if(soil.is(Blocks.PODZOL))cap=Blocks.PODZOL;
        else if(soil.is(Blocks.MYCELIUM))cap=Blocks.MYCELIUM;
        else if(soil.is(Blocks.SNOW_BLOCK))cap=Blocks.SNOW_BLOCK;
        for(var rock:List.of(Blocks.STONE,Blocks.ANDESITE,Blocks.DIORITE,Blocks.GRANITE,Blocks.TUFF,Blocks.DEEPSLATE))
            if(soil.is(rock)){fill=rock;cap=rock;break;}
        Map<Long, SitePreparationPlan.Cell> result = new LinkedHashMap<>();
        for (int y = surface; y <= top.getY(); y++) {
            BlockPos pos = new BlockPos(top.getX(), y, top.getZ());
            if (!survey.loaded(pos)) return null;
            BlockState before = level.getBlockState(pos);
            BlockState after = (grassCap && y == top.getY() ? cap : fill).defaultBlockState();
            // A whole-tree clearance receipt may authorize the root; a decorative skirt alone
            // must not sever a trunk and leave floating timber above its new grassy edge.
            if(VillageSitePreparation.naturalLog(before)&&!approvedRemovals.contains(pos.asLong()))return null;
            if ((!before.isAir() && !survey.clearable(pos)) || before.hasBlockEntity()
                    || !before.getFluidState().isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(level, village, project, pos, before, after)) return null;
            for (Direction face : Direction.values()) {
                BlockPos neighbor = pos.relative(face);
                if (!survey.loaded(neighbor) || !level.getFluidState(neighbor).isEmpty()) return null;
            }
            result.put(pos.asLong(), new SitePreparationPlan.Cell(pos.asLong(),
                    BlockStateParser.serialize(before), BlockStateParser.serialize(after)));
        }
        return result;
    }

    private static long column(BlockPos pos) { return BlockPos.asLong(pos.getX(), 0, pos.getZ()); }
}
