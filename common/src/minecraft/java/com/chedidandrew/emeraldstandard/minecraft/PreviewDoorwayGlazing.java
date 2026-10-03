package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Review-only solid jambs: native panes cannot terminate cleanly against a thin door. */
final class PreviewDoorwayGlazing {
    static Set<BlockPos> frame(Sample sample, Map<BlockPos,BlockState> cells) {
        Set<BlockPos> changed=new HashSet<>();
        // Snapshot first: a double door remains a double door, and edits cannot grow their scope.
        Map<BlockPos,BlockState> before=Map.copyOf(cells);
        for(var entry:before.entrySet()) {
            BlockState door=entry.getValue();
            if(!(door.getBlock() instanceof DoorBlock)||door.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER) continue;
            Direction facing=door.getValue(DoorBlock.FACING);
            for(Direction side:List.of(facing.getClockWise(),facing.getCounterClockWise())) {
                BlockPos jamb=entry.getKey().relative(side);
                if(!pane(before.get(jamb))&&!pane(before.get(jamb.above()))) continue;
                // Only existing glazing changes. Retain masonry sills, opaque frames, lintels,
                // neighbouring doors and both clear approaches; never fill interior/outdoor air.
                for(int y=0;y<=2;y++) {
                    BlockPos at=jamb.above(y);
                    if(pane(before.get(at))) {
                        cells.put(at,jamb(sample).defaultBlockState());
                        changed.add(at);
                    }
                }
            }
        }
        return Set.copyOf(changed);
    }
    static boolean pane(BlockState state) {
        if(state==null) return false;
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.equals("glass_pane")||id.endsWith("_stained_glass_pane");
    }
    static Block jamb(Sample sample) {
        return switch(sample.style()) {
            case DESERT->Blocks.CUT_SANDSTONE;
            case SAVANNA->Blocks.ACACIA_LOG;
            case TAIGA,SNOWY->Blocks.SPRUCE_LOG;
            default->Blocks.OAK_LOG;
        };
    }
    static void validate(Plan plan) {
        for(var entry:plan.cells().entrySet()) {
            BlockState door=entry.getValue();
            if(!(door.getBlock() instanceof DoorBlock)) continue;
            Direction facing=door.getValue(DoorBlock.FACING);
            for(Direction side:List.of(facing.getClockWise(),facing.getCounterClockWise())) {
                BlockPos at=entry.getKey().relative(side);
                if(pane(plan.cells().get(at))) throw new IllegalStateException(
                        "Unframed doorway glazing: "+plan.sample().id()+" at "+at);
            }
        }
    }
    static void report(List<Sample> samples) {
        int designs=0,frames=0;
        for(int i=0;i<samples.size();i++) {
            Plan plan=BiomeArchitecturePreview.plan(samples.get(i));
            validate(plan);
            var audit=PreviewFacadePrograms.audit(plan);
            long edits=audit.skin().stream().filter(at->pane(audit.before().get(at))).count();
            if(edits>0) {
                designs++;frames+=Math.toIntExact(edits);
                System.out.println("GLAZING FRAMED #"+(i+1)+" "+plan.sample().id()+": "+edits+" existing pane cells");
            }
        }
        if(designs==0) throw new IllegalStateException("Doorway glazing refinement did not cover any designs");
        System.out.println("GLAZING SCOPE: "+designs+" designs; "+frames+" pane-to-jamb edits; zero unframed door/pane joins across "+samples.size()+" designs");
    }
}
