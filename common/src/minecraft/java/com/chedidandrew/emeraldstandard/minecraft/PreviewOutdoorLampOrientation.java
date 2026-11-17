package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.Plan;

/** Release-only orientation of structure decor. WalkwayLighting never calls this. */
final class PreviewOutdoorLampOrientation {
    record Lamp(BlockPos lantern,BlockPos post) { }
    static List<Lamp> lamps(Map<BlockPos,BlockState> cells) {
        List<Lamp> result=new ArrayList<>();
        for(var e:cells.entrySet()) {
            var at=e.getKey();var s=e.getValue();
            if(!(s.getBlock() instanceof LanternBlock)||!s.getValue(LanternBlock.HANGING)
                    ||!is(cells,at.above(),Blocks.IRON_CHAIN))continue;
            search: for(var d:Direction.Plane.HORIZONTAL) for(int length=1;length<=3;length++) {
                var post=at.relative(d,length);var p=cells.get(post);var knee=cells.get(post.above());
                // A higher fence-column roof pendant is not a street lamp. Actual yard
                // posts have a low pole or a masonry socket immediately beneath it.
                var socket=cells.get(post.below());
                if(post.getY()>2&&(socket==null||socket.getBlock() instanceof FenceBlock))continue;
                if(p==null||!(p.getBlock() instanceof FenceBlock||p.getBlock() instanceof WallBlock)
                        ||knee==null||!(knee.getBlock() instanceof StairBlock)||!full(cells,post.above(2))
                        ||!arm(cells,at.above(2)))continue;
                for(int i=1;i<length;i++)if(!arm(cells,at.relative(d,i).above(2)))continue search;
                result.add(new Lamp(at,post));break search;
            }
        }
        result.sort(Comparator.comparingLong(l->l.post().asLong()));return List.copyOf(result);
    }
    static List<Direction> outward(Map<BlockPos,BlockState> cells,Lamp lamp) {
        var post=lamp.post();List<BlockPos> walls=new ArrayList<>();long closest=Long.MAX_VALUE;
        for(var e:cells.entrySet()) {
            var at=e.getKey();
            if(at.getY()!=post.getY()||!full(cells,at)||!full(cells,at.above()))continue;
            long dx=(long)post.getX()-at.getX(),dz=(long)post.getZ()-at.getZ(),distance=dx*dx+dz*dz;
            if(distance<closest){walls.clear();closest=distance;}
            if(distance==closest)walls.add(at);
        }
        if(walls.isEmpty())throw new IllegalStateException("Structure lamp has no building facade: "+post);
        var result=new ArrayList<Direction>();
        // Never choose a tangent or turn toward an equally near facade in a corner.
        for(var d:Direction.Plane.HORIZONTAL)if(walls.stream().allMatch(w->projection(post,w,d)>0))result.add(d);
        result.sort(Comparator.comparingLong((Direction d)->-walls.stream().mapToLong(w->projection(post,w,d)).sum())
                .thenComparingInt(Direction::ordinal));
        if(result.isEmpty())throw new IllegalStateException("No outward facade direction for lamp "+post+" walls="+walls);
        return List.copyOf(result);
    }
    private static long projection(BlockPos post,BlockPos wall,Direction d) {
        return ((long)post.getX()-wall.getX())*d.getStepX()+((long)post.getZ()-wall.getZ())*d.getStepZ();
    }
    static Plan apply(Plan source) {
        var cells=new LinkedHashMap<>(source.cells());
        for(var lamp:lamps(source.cells())) {
            var directions=outward(source.cells(),lamp);var post=lamp.post();var old=lamp.lantern();
            if(directions.stream().anyMatch(d->onArm(post,old,d)))continue;
            boolean placed=false;
            placement: for(var d:directions) for(int length=1;length<=3;length++) {
                var target=post.relative(d,length);
                if(source.access().contains(target)||source.access().contains(target.below())||source.access().contains(target.above()))continue;
                if(!clear(cells,target)||!clear(cells,target.above())||!clear(cells,target.above(2)))continue;
                for(int i=1;i<=length;i++)if(!clear(cells,post.relative(d,i).above(2))||source.access().contains(post.relative(d,i).above(2)))continue placement;
                var arm=cells.get(old.above(2));
                cells.put(target,cells.remove(old));cells.put(target.above(),cells.remove(old.above()));
                // A shared roof bearing is structural, not part of the removable arm.
                for(var oldDirection:Direction.Plane.HORIZONTAL)if(onArm(post,old,oldDirection))
                    for(int i=1;i<=post.distManhattan(old);i++)if(clear(cells,post.relative(oldDirection,i).above(3)))cells.remove(post.relative(oldDirection,i).above(2));
                var oriented=arm.getBlock() instanceof RotatedPillarBlock?arm.setValue(RotatedPillarBlock.AXIS,d.getAxis()):arm;
                for(int i=1;i<=length;i++)cells.put(post.relative(d,i).above(2),oriented);
                cells.put(post.above(),cells.get(post.above()).setValue(StairBlock.FACING,d));
                placed=true;break placement;
            }
            if(!placed)throw new IllegalStateException("No clear outward structure lamp arm: "+source.sample().id()+" "+post
                    +" below="+cells.get(post.below())+" base="+cells.get(post.below(2))+" directions="+directions
                    +" targets="+directions.stream().flatMap(d->java.util.stream.IntStream.rangeClosed(1,3).mapToObj(n->{var at=post.relative(d,n);return d+"/"+n+":"+cells.get(at)+","+cells.get(at.above())+","+cells.get(at.above(2))+" access="+source.access().contains(at);})).toList());
        }
        var result=new Plan(source.sample(),Map.copyOf(cells),source.access(),source.entrance(),source.height());
        validate(result.cells());return result;
    }
    static int validate(Map<BlockPos,BlockState> cells) {
        var lamps=lamps(cells);
        for(var lamp:lamps)if(outward(cells,lamp).stream().noneMatch(d->onArm(lamp.post(),lamp.lantern(),d)))
            throw new IllegalStateException("Structure decor lamp faces toward/tangent to facade: "+lamp);
        return lamps.size();
    }
    private static boolean is(Map<BlockPos,BlockState> cells,BlockPos at,Block block){return cells.get(at)!=null&&cells.get(at).is(block);}
    private static boolean onArm(BlockPos post,BlockPos lamp,Direction d){return post.getY()==lamp.getY()
            &&(d.getAxis()==Direction.Axis.X?post.getZ()==lamp.getZ():post.getX()==lamp.getX())
            &&projection(lamp,post,d)>0;}
    private static boolean arm(Map<BlockPos,BlockState> cells,BlockPos at){var s=cells.get(at);return s!=null&&!s.hasBlockEntity()&&(s.getBlock() instanceof SlabBlock||full(cells,at));}
    private static boolean full(Map<BlockPos,BlockState> cells,BlockPos at){var s=cells.get(at);return s!=null&&s.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP);}
    private static boolean clear(Map<BlockPos,BlockState> cells,BlockPos at){return cells.get(at)==null||cells.get(at).isAir();}
}
