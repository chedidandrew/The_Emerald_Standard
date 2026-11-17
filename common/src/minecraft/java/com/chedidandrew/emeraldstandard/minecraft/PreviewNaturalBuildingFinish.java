package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.Plan;

/** New authored releases only; vanilla imports and historical world blueprints never use this. */
final class PreviewNaturalBuildingFinish {
    static Plan apply(Plan source) {
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(source.cells());
        for(var entry:List.copyOf(cells.entrySet())) {
            var at=entry.getKey();var s=entry.getValue();
            if(!(s.getBlock() instanceof DoorBlock)||s.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER
                    ||!openRoom(source,source.cells(),at))continue;
            var id=BuiltInRegistries.BLOCK.getKey(s.getBlock());
            if(!id.getNamespace().equals("minecraft")||id.getPath().equals("iron_door"))continue;
            var gate=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:"
                    +id.getPath().replace("_door","_fence_gate")));
            if(!(gate instanceof FenceGateBlock))throw new IllegalStateException("No matching gate: "+id);
            var upper=cells.get(at.above());
            if(upper==null||upper.getBlock()!=s.getBlock())throw new IllegalStateException("Unpaired door: "+at);
            cells.put(at,gate.defaultBlockState().setValue(FenceGateBlock.FACING,s.getValue(DoorBlock.FACING))
                    .setValue(FenceGateBlock.OPEN,true)); // Villagers cannot operate a closed gate.
            cells.remove(at.above());
        }
        if(source.sample().style()==BiomeDialect.SNOWY) {
            // Each exposed wooden roof course gets a small, deterministic drift. Never cover
            // a chimney/campfire, lantern, hatch, window or standing/access coordinate.
            for(var entry:List.copyOf(cells.entrySet())) {
                var at=entry.getKey();var s=entry.getValue();var snow=at.above();
                if(at.getY()<4||!woodRoof(s)||!s.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP)
                        ||source.access().contains(snow))continue;
                var cap=cells.get(snow);
                if(cap!=null&&!cap.is(Blocks.SNOW))continue;
                boolean exposed=true;
                for(int y=at.getY()+2;y<=source.height();y++)if(cells.containsKey(new BlockPos(at.getX(),y,at.getZ()))) {exposed=false;break;}
                long adjacent=Direction.Plane.HORIZONTAL.stream().filter(d->{var n=cells.get(at.relative(d));return n!=null&&woodRoof(n);}).count();
                if(!exposed||adjacent<2)continue;
                int layers=1+Math.floorMod(31*(at.getX()/2)+17*(at.getZ()/3)+source.sample().id().hashCode(),4);
                cells.put(snow,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers));
            }
        }
        int height=cells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0)+1;
        return new Plan(source.sample(),Map.copyOf(cells),source.access(),source.entrance(),height);
    }
    private static boolean woodRoof(BlockState s) {
        var id=BuiltInRegistries.BLOCK.getKey(s.getBlock());
        // Explicit authoring exports bootstrap the vanilla registry without datapack tags.
        // These are known authored roof states, never a world-clearing classification.
        return id.getNamespace().equals("minecraft")&&(id.getPath().endsWith("_planks")
                ||id.getPath().endsWith("_log")||id.getPath().endsWith("_wood")
                ||s.is(Blocks.SPRUCE_STAIRS)||s.is(Blocks.SPRUCE_SLAB))
                ||s.is(net.minecraft.tags.BlockTags.PLANKS)||s.is(net.minecraft.tags.BlockTags.LOGS)
                ||s.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS)||s.is(net.minecraft.tags.BlockTags.WOODEN_SLABS);
    }
    static boolean openRoom(Plan p,Map<BlockPos,BlockState> cells,BlockPos door) {
        var facing=cells.get(door).getValue(DoorBlock.FACING);
        var sides=List.of(door.relative(facing),door.relative(facing.getOpposite()));
        // Both sides must connect outside without passing through a door. This avoids
        // guessing which side is inside for rotated entrances, annexes or internal doors.
        return sides.stream().allMatch(side->connectedOutside(p,cells,side.above()));
    }
    private static boolean connectedOutside(Plan p,Map<BlockPos,BlockState> cells,BlockPos inside) {
        // Flood the head-height compartment, treating all doors and glass as closed. An
        // ordinary front porch therefore cannot turn a sealed room's entrance into a gate.
        if(!empty(cells,inside))return false;
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();queue.add(inside);
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();
            if(at.getX()<-1||at.getZ()<-1||at.getX()>p.sample().width()||at.getZ()>p.sample().depth())return true;
            if(!empty(cells,at)||!seen.add(at))continue;
            for(var d:Direction.Plane.HORIZONTAL)queue.add(at.relative(d));
        }
        return false;
    }
    private static boolean empty(Map<BlockPos,BlockState> cells,BlockPos at) {var s=cells.get(at);return s==null||s.isAir();}
    private PreviewNaturalBuildingFinish() { }
}
