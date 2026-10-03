package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;

/** Authored review geometry only: native shape contact, not Minecraft's gravity rules. */
final class PreviewSupportAudit {
    private static final double EPS=1.0e-7;
    private static final List<BlockPos> STEPS=steps();
    private static List<BlockPos> steps() {
        List<BlockPos> result=new ArrayList<>();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
            if(x!=0||y!=0||z!=0)result.add(new BlockPos(x,y,z));
        return List.copyOf(result);
    }
    static Set<BlockPos> floating(Map<BlockPos,BlockState> cells) {
        Map<BlockPos,List<AABB>> shapes=new HashMap<>();
        cells.forEach((at,state)-> {
            var boxes=new ArrayList<>(connectedState(cells,at,state).getShape(EmptyBlockGetter.INSTANCE,at).toAabbs());
            // The outline omits the rendered attachment hook/foot. The added narrow segment
            // must still touch a grounded bearing; it does not ground a free-floating lamp.
            if(state.is(Blocks.LANTERN)) {
                boolean hanging=state.getValue(LanternBlock.HANGING);
                boxes.add(new AABB(.4375,hanging?.875:0,.4375,.5625,hanging?1:.125,.5625));
            }
            // Rooted crops and floating lily pads have intentional native placement offsets.
            // Their survival is independently checked in the live placement gate.
            if(state.getBlock() instanceof CropBlock&&cells.get(at.below())!=null&&cells.get(at.below()).is(Blocks.FARMLAND))
                boxes.add(new AABB(.4375,-.0625,.4375,.5625,.125,.5625));
            if(state.is(Blocks.LILY_PAD)&&cells.get(at.below())!=null&&cells.get(at.below()).is(Blocks.WATER))
                boxes.add(new AABB(.4375,-1,.4375,.5625,.125,.5625));
            if(state.is(Blocks.WATER)) boxes.add(new AABB(0,0,0,1,1,1));
            if(!boxes.isEmpty()) shapes.put(at,boxes);
        });
        Set<BlockPos> grounded=new HashSet<>();
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        shapes.keySet().stream().filter(at->at.getY()<=0).forEach(at->{grounded.add(at);queue.add(at);});
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();
            for(BlockPos step:STEPS) {
                BlockPos next=at.offset(step);
                if(grounded.contains(next)||!shapes.containsKey(next)) continue;
                if(touch(shapes.get(at),shapes.get(next),step)) {grounded.add(next);queue.add(next);}
            }
        }
        Set<BlockPos> floating=new HashSet<>(shapes.keySet());floating.removeAll(grounded);
        return Set.copyOf(floating);
    }
    private static BlockState connectedState(Map<BlockPos,BlockState> cells,BlockPos at,BlockState state) {
        // Blueprint properties precede neighbor updates in the live world. Resolve native
        // fence/pane/wall arms rather than treating their unconnected defaults as real gaps.
        for(Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos next=at.relative(side);BlockState neighbor=cells.get(next);
            boolean sturdy=neighbor!=null&&neighbor.isFaceSturdy(EmptyBlockGetter.INSTANCE,next,side.getOpposite());
            if(state.getBlock() instanceof FenceBlock||state.getBlock() instanceof IronBarsBlock) {
                boolean connects=sturdy||(neighbor!=null&&(state.getBlock() instanceof FenceBlock
                        ?neighbor.getBlock() instanceof FenceBlock||neighbor.getBlock() instanceof FenceGateBlock
                        :neighbor.getBlock() instanceof IronBarsBlock));
                state=state.setValue(switch(side){case NORTH->BlockStateProperties.NORTH;case SOUTH->BlockStateProperties.SOUTH;
                    case EAST->BlockStateProperties.EAST;default->BlockStateProperties.WEST;},connects);
            } else if(state.getBlock() instanceof WallBlock) {
                boolean connects=sturdy||(neighbor!=null&&(neighbor.getBlock() instanceof WallBlock||neighbor.getBlock() instanceof FenceGateBlock));
                state=state.setValue(switch(side){case NORTH->BlockStateProperties.NORTH_WALL;case SOUTH->BlockStateProperties.SOUTH_WALL;
                    case EAST->BlockStateProperties.EAST_WALL;default->BlockStateProperties.WEST_WALL;},connects?WallSide.LOW:WallSide.NONE);
            }
        }
        return state;
    }
    private static boolean touch(List<AABB> first,List<AABB> second,BlockPos step) {
        for(AABB a:first) for(AABB raw:second) {
            AABB b=raw.move(step.getX(),step.getY(),step.getZ());
            double x=Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX);
            double y=Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY);
            double z=Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ);
            // Stepped Minecraft roofs legitimately meet along an edge. A gap or a sole
            // diagonal point is not a visual bearing; touching faces/edges are.
            if(x>=-EPS&&y>=-EPS&&z>=-EPS&&(x>EPS||y>EPS||z>EPS)) return true;
        }
        return false;
    }
    static void validate(Map<BlockPos,BlockState> cells,String id) {
        var disconnected=floating(cells);
        if(!disconnected.isEmpty()) {
            var at=disconnected.stream().min(Comparator.comparingInt((BlockPos p)->p.getY())
                    .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).orElseThrow();
            throw new IllegalStateException("Floating review geometry "+id+": "+disconnected.size()+" cells; "+at+" "+cells.get(at));
        }
    }
    static void report(List<BiomeArchitecturePreview.Sample> samples) {
        int total=0;Map<String,Integer> counts=new TreeMap<>();
        for(var sample:samples) {
            var p=BiomeArchitecturePreview.plan(sample);
            Map<BlockPos,BlockState> all=new HashMap<>(p.cells());all.putAll(PreviewOutdoorPrograms.site(p).cells());
            var floating=floating(all);total+=floating.size();
            floating.forEach(at->counts.merge(all.get(at).toString(),1,Integer::sum));
            if(!floating.isEmpty()) {
                System.out.println("FLOATING "+sample.id()+" "+floating.size()+" cells");
                floating.stream().sorted(Comparator.comparingInt((BlockPos at)->at.getY()).thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ)).limit(18).forEach(at->System.out.println("  "+at+" "+all.get(at)));
            }
        }
        System.out.println("SUPPORT AUDIT "+samples.size()+" plans; "+total+" disconnected shape cells");
        counts.forEach((state,count)->System.out.println("FLOATING TYPE "+count+" "+state));
        if(total!=0)throw new IllegalStateException("Disconnected review geometry: "+total);
    }
}
