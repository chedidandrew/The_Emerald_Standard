package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.SitePreparationPlan;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Fresh-site, snapshot-owned access work; never reconstructs or repairs a saved staircase. */
final class EntranceStaircase {
    record Plan(SitePreparationPlan receipt, Set<BlockPos> occupied) { }

    static Plan survey(ServerLevel level, SitePreparationPlan base, BlockPos exit, Direction outward,
            Map<BlockPos, BlockState> authored, BlockState stairs, UUID village, long project) {
        if (!outward.getAxis().isHorizontal()) return null;
        var survey = new VillageSitePreparation.Survey(level);
        Direction sideways = outward.getClockWise();
        int floorY = exit.getY() - 1;
        int steps = -1;
        // Use the actual outside standing cell, not a template's bounding-box midpoint.
        // Road bends start AFTER the straight flight, never in the middle of its treads.
        for (int i = 0; i <= TerrainFoundationPlan.MAX_TERRAIN_DROP; i++) {
            boolean seated = true;
            for (int side = -1; side <= 1; side++) {
                BlockPos column = exit.relative(outward, i + 1).relative(sideways, side);
                Integer surface = survey.surface(column.getX(), column.getZ());
                if (surface == null || surface < exit.getY() - TerrainFoundationPlan.MAX_TERRAIN_DROP)
                    return fail("bad surface "+column+" surface="+surface+" exit="+exit);
                seated &= surface > floorY - i;
            }
            if (seated) { steps = i; break; }
        }
        if (steps < 0) return fail("no seated row "+exit);
        if (steps == 0) return new Plan(base, Set.of()); // No apron or gratuitous step at grade.

        BlockState backing = backing(stairs);
        Map<BlockPos, BlockState> desired = new LinkedHashMap<>();
        Set<BlockPos> clearance = new HashSet<>();
        for (int i = 0; i <= steps; i++) for (int side = -1; side <= 1; side++) {
            BlockPos top = exit.relative(outward, i + 1).relative(sideways, side).atY(floorY - i);
            // The final row is a grounded landing, not one more unsupported stair.
            BlockState tread = i < steps ? stairs.setValue(StairBlock.FACING, outward.getOpposite()) : backing;
            Integer surface = survey.surface(top.getX(), top.getZ());
            if (surface == null) return fail("no support surface "+top);
            BlockPos bearing = top.atY(surface - 1);
            // Read-only bearing belongs to the frozen contract too: a later excavation
            // must not hand over a flight whose native ground disappeared during building.
            desired.put(bearing, level.getBlockState(bearing));
            for (int y = surface; y < top.getY(); y++)
                desired.put(top.atY(y), backing);
            if (i < steps || surface - 1 != top.getY()) desired.put(top, tread);
            else if (!VillageDevelopmentProtection.mayPlace(level, village, project, top,
                    level.getBlockState(top), level.getBlockState(top))) return fail("protected landing "+top);
            for (int y = top.getY() + 1; y <= Math.max(top.getY() + 2, surface); y++) {
                BlockPos at = top.atY(y);
                desired.put(at, Blocks.AIR.defaultBlockState());
            }
        }
        // Required backing must join known natural bearing, and every changed cell remains
        // within the same bounded cut/fill envelope as ordinary development foundations.
        for (var e : desired.entrySet()) {
            BlockPos pos = e.getKey();
            if (!survey.loaded(pos) || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) return null;
            BlockState original = level.getBlockState(pos), template = authored.get(pos);
            if (template != null && !template.isAir()) {
                if (!template.equals(e.getValue())) return null;
                continue;
            }
            if (original.hasBlockEntity() || !original.getFluidState().isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(level, village, project, pos, original, e.getValue())) return fail("protected cell "+pos+" "+original);
            if (original.equals(e.getValue()) && VillageSitePreparation.dryNaturalGround(original)
                    && original.isFaceSturdy(level, pos, Direction.UP)) continue;
            if (!original.isAir() && !survey.clearable(pos)
                    && !survey.excavatable(pos, floorY - steps)) return fail("unclearable cell "+pos+" "+original);
            if (!original.isAir()) clearance.add(pos);
        }
        var removal = survey.freeze(clearance, floorY - steps, village, project);
        if (removal == null || removal.cells().stream().anyMatch(c -> {
            var state = authored.get(BlockPos.of(c.position()));
            return state != null && !state.isAir();
        })) return fail("clearance overlaps authored cells or failed survey "+exit);
        Map<Long, SitePreparationPlan.Cell> cells = new LinkedHashMap<>();
        base.cells().forEach(c -> cells.put(c.position(), c));
        removal.cells().forEach(c -> cells.put(c.position(), c));
        for (var e : desired.entrySet()) {
            if (authored.containsKey(e.getKey()) && !authored.get(e.getKey()).isAir()) continue;
            cells.put(e.getKey().asLong(), new SitePreparationPlan.Cell(e.getKey().asLong(),
                    BlockStateParser.serialize(level.getBlockState(e.getKey())), BlockStateParser.serialize(e.getValue())));
        }
        if (cells.size() > SitePreparationPlan.MAX_CELLS) return null;
        var receipt = new SitePreparationPlan(cells.values().stream().sorted(Comparator
                .<SitePreparationPlan.Cell>comparingInt(c -> c.after().equals("minecraft:air") ? 0 : 1)
                .thenComparingInt(c -> c.after().equals("minecraft:air") ? -BlockPos.of(c.position()).getY() : BlockPos.of(c.position()).getY())
                .thenComparingLong(SitePreparationPlan.Cell::position)).toList());
        return new Plan(receipt, Set.copyOf(desired.keySet()));
    }

    private static Plan fail(String reason) {
        org.slf4j.LoggerFactory.getLogger("the_emerald_standard_construction")
                .debug("Entrance staircase declined: {}",reason);
        // No mutation or narrow fallback: the caller declines this lot and tries another.
        return null;
    }

    /** Only complete three-wide flight columns, not optional single-lane roads, gate handover. */
    static BlockPos obstruction(ServerLevel level, SitePreparationPlan receipt) {
        if(receipt==null)return null;
        Map<BlockPos,BlockState> treads=new HashMap<>();
        for(var c:receipt.cells()) if(c.after().contains("_stairs["))
            treads.put(BlockPos.of(c.position()),VillageTerrainFinishing.state(level,c.after()));
        Map<Long,Integer> columns=new HashMap<>();
        for(var e:treads.entrySet()) {
            var face=e.getValue().getValue(StairBlock.FACING);var side=face.getClockWise();var at=e.getKey();
            if(!e.getValue().equals(treads.get(at.relative(side)))
                    ||!e.getValue().equals(treads.get(at.relative(side.getOpposite()))))continue;
            for(int lane=-1;lane<=1;lane++)for(int row=0;row<=1;row++) {
                var p=at.relative(side,lane).relative(face.getOpposite(),row);
                columns.merge(BlockPos.asLong(p.getX(),0,p.getZ()),at.getY()+2,Math::max);
            }
        }
        for(var cell:receipt.cells()) {
            var at=BlockPos.of(cell.position());
            var ceiling=columns.get(BlockPos.asLong(at.getX(),0,at.getZ()));
            if(ceiling!=null&&at.getY()<=ceiling&&(!level.hasChunk(at.getX()>>4,at.getZ()>>4)
                    ||!VillageTerrainFinishing.satisfied(level,level.getBlockState(at),cell.after()))) return at;
        }
        return null;
    }

    /** Non-falling full blocks matching the treads, not a polished road palette or wood stilt. */
    static BlockState backing(BlockState stairs) {
        for (var pair : List.of(new Block[]{Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE},
                new Block[]{Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS},
                new Block[]{Blocks.STONE_STAIRS, Blocks.STONE},
                new Block[]{Blocks.ANDESITE_STAIRS, Blocks.ANDESITE},
                new Block[]{Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE},
                new Block[]{Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE},
                new Block[]{Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS},
                new Block[]{Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE},
                new Block[]{Blocks.OAK_STAIRS, Blocks.OAK_PLANKS},
                new Block[]{Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS}))
            if (stairs.is(pair[0])) return pair[1].defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }
}
