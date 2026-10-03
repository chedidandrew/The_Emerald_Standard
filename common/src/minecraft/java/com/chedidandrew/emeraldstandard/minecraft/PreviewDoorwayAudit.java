package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/** Native, two-block-high doorway clearance; review-only admission. */
final class PreviewDoorwayAudit {
    private static final Map<String,Map<BlockPos,BlockState>> BASELINES=new HashMap<>();
    private static final Map<String,Set<BlockPos>> EDITS=new HashMap<>();

    /** Only the disposable review plan is edited; never the frozen production blueprint. */
    static BiomeArchitecturePreview.Plan correct(BiomeArchitecturePreview.Plan plan) {
        Map<BlockPos,BlockState> original=plan.cells(),cells=new LinkedHashMap<>(original);
        Set<BlockPos> allowed=new HashSet<>();
        if(plan.sample().id().startsWith("catalog_")
                &&plan.sample().style()!=com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.PLAINS
                &&!plan.sample().role().equals("BANK")) {
            var program=PreviewExteriorPrograms.program(BiomeArchitectureCatalogPreview.masterId(plan.sample()));
            int w=plan.sample().width()-6,d=plan.sample().depth()-6,mid=w/2;
            int front=plan.sample().style()==com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.DESERT?1:2;
            // Front bays must connect through the original facade, including offset/twin bays.
            // A north-facing door in a detached box is not an entrance to the main room.
            switch(program.wing()) {
                case BAY -> connector(cells,allowed,(program.position()==0?mid:Math.max(2,mid-3))+3,3,front+4);
                case TWIN_BAYS -> {
                    connector(cells,allowed,5,3,front+4);
                    connector(cells,allowed,w,3,front+4);
                }
                case APSE -> connector(cells,allowed,(program.position()==0?mid:Math.max(2,mid-3))+3,d+1,d+2);
                case TURRET -> {
                    // Side towers need a public approach around the porch end posts/rails.
                    BlockState floor=cells.get(plan.entrance().below());
                    for(int x=plan.entrance().getX();x<=w+4;x++) {
                        BlockPos at=new BlockPos(x,0,2);
                        if(cells.get(at)==null) { cells.put(at,floor); allowed.add(at); }
                    }
                    connector(cells,allowed,plan.entrance().getX(),2,front+2);
                }
                default -> { }
            }
        }
        for(BlockPos door:doors(cells)) {
            Direction axis=cells.get(door).getValue(DoorBlock.FACING);
            for(Direction side:new Direction[]{axis,axis.getOpposite()}) {
                BlockPos at=door.relative(side);
                if(!clear(cells,at)) clearLane(cells,allowed,at);
            }
        }
        Set<BlockPos> changes=new HashSet<>();
        Set<BlockPos> keys=new HashSet<>(original.keySet()); keys.addAll(cells.keySet());
        for(BlockPos key:keys) if(!Objects.equals(original.get(key),cells.get(key))) {
            if(!allowed.contains(key)) throw new IllegalStateException("Unscoped doorway edit: "+key);
            changes.add(key);
        }
        BASELINES.put(plan.sample().id(),original); EDITS.put(plan.sample().id(),Set.copyOf(changes));
        var fixed=new BiomeArchitecturePreview.Plan(plan.sample(),Map.copyOf(cells),plan.access(),plan.entrance(),plan.height());
        validate(fixed); return fixed;
    }
    static void alias(BiomeArchitecturePreview.Plan source,String id) {
        BASELINES.put(id,baseline(source)); EDITS.put(id,EDITS.getOrDefault(source.sample().id(),Set.of()));
    }
    static Map<BlockPos,BlockState> baseline(BiomeArchitecturePreview.Plan plan) {
        return BASELINES.getOrDefault(plan.sample().id(),plan.cells());
    }
    static int edits(BiomeArchitecturePreview.Plan plan) { return EDITS.getOrDefault(plan.sample().id(),Set.of()).size(); }
    private static void connector(Map<BlockPos,BlockState> cells,Set<BlockPos> allowed,int x,int z0,int z1) {
        for(int z=z0;z<=z1;z++) {
            BlockPos feet=new BlockPos(x,1,z);
            clearLane(cells,allowed,feet);
            if(cells.get(feet.below())==null) {
                // Continue the existing bay floor, not a new material or a floating access tile.
                BlockState floor=cells.get(new BlockPos(x,0,z0-1));
                if(floor==null) floor=cells.get(new BlockPos(x,0,z1+1));
                if(floor==null) throw new IllegalStateException("Missing connector floor bearing");
                cells.put(feet.below(),floor); allowed.add(feet.below());
            }
        }
    }
    private static void clearLane(Map<BlockPos,BlockState> cells,Set<BlockPos> allowed,BlockPos feet) {
        for(int y=0;y<2;y++) {
            BlockPos at=feet.above(y); BlockState state=cells.get(at);
            if(state!=null&&!(state.getBlock() instanceof DoorBlock)) {
                // Thin rugs/plates can remain. Structural walls, planters and fences cannot.
                if(!clear(Map.of(at,state),feet)) { cells.remove(at); allowed.add(at); }
            }
        }
        BlockPos cap=feet.above(2); BlockState state=cells.get(cap);
        if(state!=null&&(state.getBlock() instanceof SlabBlock||state.getBlock() instanceof FlowerPotBlock)
                &&cells.get(cap.below())==null) { cells.remove(cap); allowed.add(cap); }
    }
    static int validate(BiomeArchitecturePreview.Plan plan) {
        var cells=plan.cells(); List<BlockPos> doors=doors(cells);
        for(var entry:cells.entrySet()) if(entry.getValue().getBlock() instanceof DoorBlock
                &&entry.getValue().getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER) {
            BlockState below=cells.get(entry.getKey().below());
            if(below==null||!below.is(entry.getValue().getBlock())||below.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER)
                throw new IllegalStateException(plan.sample().id()+" orphan door upper at "+entry.getKey());
        }
        for(BlockPos door:doors) {
            BlockState lower=cells.get(door),upper=cells.get(door.above());
            if(upper==null||!upper.is(lower.getBlock())||upper.getValue(DoorBlock.HALF)!=DoubleBlockHalf.UPPER
                    ||upper.getValue(DoorBlock.FACING)!=lower.getValue(DoorBlock.FACING)
                    ||upper.getValue(DoorBlock.HINGE)!=lower.getValue(DoorBlock.HINGE))
                throw new IllegalStateException(plan.sample().id()+" incomplete doorway at "+door);
            Direction facing=lower.getValue(DoorBlock.FACING);
            for(Direction side:new Direction[]{facing,facing.getOpposite()})
                if(!clear(cells,door.relative(side)))
                    throw new IllegalStateException(plan.sample().id()+" blocked door "+door+" "+side);
            for(BlockPos at:new BlockPos[]{door,door.above()}) {
                var opened=cells.get(at).setValue(DoorBlock.OPEN,true);
                if(opened.getCollisionShape(EmptyBlockGetter.INSTANCE,at).toAabbs().stream()
                        .anyMatch(new AABB(.2,0,.2,.8,1,.8)::intersects))
                    throw new IllegalStateException("Open door obstructs crossing: "+at);
            }
        }
        // Audit every added wing, not just furniture targets. Plains upper-floor traversal
        // remains covered by its original native admission; its doors get clearance checks above.
        if(plan.sample().style()!=com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.PLAINS) {
            Set<BlockPos> reach=reach(cells,plan.entrance());
            for(BlockPos door:doors) if(!reach.contains(door))
                throw new IllegalStateException(plan.sample().id()+" disconnected doorway: "+door);
        }
        return doors.size();
    }
    private static Set<BlockPos> reach(Map<BlockPos,BlockState> cells,BlockPos start) {
        Set<BlockPos> seen=new HashSet<>(); ArrayDeque<BlockPos> todo=new ArrayDeque<>(); todo.add(start);
        while(!todo.isEmpty()) {
            BlockPos pos=todo.removeFirst(); BlockState floor=cells.get(pos.below());
            if(floor==null||!floor.isFaceSturdy(EmptyBlockGetter.INSTANCE,pos.below(),Direction.UP)
                    ||!clear(cells,pos)||!seen.add(pos)) continue;
            for(Direction side:Direction.Plane.HORIZONTAL) todo.add(pos.relative(side));
        }
        return seen;
    }
    static boolean clear(Map<BlockPos,BlockState> cells,BlockPos feet) {
        for(int y=0;y<2;y++) {
            BlockPos at=feet.above(y); BlockState state=cells.get(at);
            if(state==null||state.getBlock() instanceof DoorBlock) continue;
            var body=new AABB(0.2,y==0?0.08:0,0.2,0.8,y==0?1:0.8,0.8);
            if(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at).toAabbs().stream().anyMatch(body::intersects)) return false;
        }
        return true;
    }
    static List<BlockPos> doors(Map<BlockPos,BlockState> cells) {
        return cells.entrySet().stream().filter(e->e.getValue().getBlock() instanceof DoorBlock
                &&e.getValue().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER).map(Map.Entry::getKey)
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ)).toList();
    }
    static void report() {
        int count=0,blocked=0,edits=0,changed=0;
        for(var sample:BiomeArchitectureCatalogPreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(sample);
            count+=validate(plan);
            int delta=edits(plan); edits+=delta; if(delta>0) changed++;
            for(BlockPos door:doors(plan.cells())) {
                Direction facing=plan.cells().get(door).getValue(DoorBlock.FACING);
                for(Direction side:new Direction[]{facing,facing.getOpposite()}) {
                    BlockPos at=door.relative(side);
                    if(!clear(plan.cells(),at)) {
                        blocked++;
                        System.out.println("BLOCKED DOOR "+sample.id()+" "+door+" side="+side+" feet="+plan.cells().get(at)+" head="+plan.cells().get(at.above()));
                    }
                }
            }
        }
        System.out.println("DOOR CENSUS "+count+" doors; "+blocked+" blocked sides");
        System.out.println("DOORWAY SCOPE "+changed+" plans changed; "+edits+" route-scoped cells; other cells retained");
    }
}
