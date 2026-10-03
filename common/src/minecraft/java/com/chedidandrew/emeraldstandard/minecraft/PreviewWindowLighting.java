package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Final review composition: honest glazing and sparse, supported, night-safe lanterns. */
final class PreviewWindowLighting {
    record LightingEdits(Set<BlockPos> removed,Set<BlockPos> added) { }
    private static final Comparator<BlockPos> ORDER=Comparator.comparingInt((BlockPos at)->at.getY())
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);
    static boolean glass(BlockState state) {
        if(state==null) return false;
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(Blocks.GLASS)||PreviewDoorwayGlazing.pane(state)||id.endsWith("_stained_glass");
    }
    static boolean view(Map<BlockPos,BlockState> cells,BlockPos at) {
        // A wall window needs space on both sides of its normal. Checking all three axes
        // also retains genuine roof skylights and internal glazing between usable spaces.
        for(Direction axis:List.of(Direction.EAST,Direction.SOUTH,Direction.UP))
            if(space(cells,at,axis)&&space(cells,at,axis.getOpposite())) return true;
        return false;
    }
    private static boolean space(Map<BlockPos,BlockState> cells,BlockPos at,Direction direction) {
        BlockPos start=at.relative(direction);
        if(!viewSpace(cells,start)) return false;
        // A wall immediately across a one-block gap is still a blind view, even if
        // that gap runs sideways as a service corridor. Require actual viewing depth.
        if(!viewSpace(cells,at.relative(direction,2))) return false;
        // The open side must also spread into room/outdoor space, not a boxed slit.
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(start);
        while(!queue.isEmpty()&&seen.size()<4) {
            BlockPos next=queue.removeFirst();
            if(!viewSpace(cells,next)||!seen.add(next)) continue;
            for(Direction side:Direction.Plane.HORIZONTAL) queue.add(next.relative(side));
        }
        return seen.size()>=4;
    }
    private static boolean viewSpace(Map<BlockPos,BlockState> cells,BlockPos at) {
        BlockState state=cells.get(at);
        // Other panes along a window's plane are not a room behind that window. Partial
        // furniture, plants and lamps do not turn a real room into a solid backed wall.
        return !glass(state)&&(state==null||!Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at)));
    }
    static Set<BlockPos> sealBlindWindows(Plan p,Map<BlockPos,BlockState> cells) {
        Set<BlockPos> changed=new HashSet<>();Map<BlockPos,BlockState> before=Map.copyOf(cells);
        for(var entry:before.entrySet()) if(glass(entry.getValue())&&!view(before,entry.getKey())) {
            cells.put(entry.getKey(),infill(p,before,entry.getKey()));changed.add(entry.getKey());
        }
        return Set.copyOf(changed);
    }
    private static BlockState infill(Plan p,Map<BlockPos,BlockState> cells,BlockPos at) {
        Block fallback=new Builder(p.sample()).p.wall();
        Map<BlockState,Integer> counts=new LinkedHashMap<>();
        for(Direction side:Direction.values()) {
            BlockPos next=at.relative(side);BlockState state=cells.get(next);
            if(state==null||glass(state)||state.hasBlockEntity()||state.getBlock() instanceof DoorBlock
                    ||!Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,next))) continue;
            counts.merge(state,1,Integer::sum);
        }
        return counts.entrySet().stream().sorted(Comparator.<Map.Entry<BlockState,Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(e->e.getKey().toString())).map(Map.Entry::getKey).findFirst()
                .orElse(fallback.defaultBlockState());
    }
    static LightingEdits thinLanterns(Plan p,Map<BlockPos,BlockState> cells) {
        Set<BlockPos> removed=new HashSet<>(),added=new HashSet<>();
        Plan original=with(p,cells);
        var reference=BiomeArchitecturePreview.lighting(original);
        reference.requireSpawnSafe();
        var floors=BiomeArchitecturePreview.reachable(original).stream()
                .map(at->new com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel(at.getX(),at.getY(),at.getZ())).toList();
        var baseline=PreviewDoorwayAudit.baseline(p);
        List<BlockPos> lamps=lanterns(cells).stream().sorted(Comparator
                .comparingInt((BlockPos at)->baseline.get(at)!=null&&baseline.get(at).getBlock() instanceof LanternBlock?1:0)
                .thenComparing(ORDER.reversed())).toList();
        for(BlockPos lamp:lamps) {
            if(!cells.containsKey(lamp)||lanterns(cells).stream().noneMatch(other->!other.equals(lamp)&&crowded(cells,lamp,other))) continue;
            Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
            Set<BlockPos> omitted=removePendant(draft,lamp);
            var light=BiomeArchitecturePreview.lighting(with(p,draft));
            boolean safe=coverage(light,reference,floors);
            if(safe) { cells.clear();cells.putAll(draft);removed.addAll(omitted); }
        }
        // The last few task lights may be indispensable. Move them to real ceiling/desk
        // supports in the same space instead of accepting a cluster or darkening a room.
        for(BlockPos lamp:lanterns(cells)) {
            if(lanterns(cells).stream().noneMatch(other->!other.equals(lamp)&&crowded(cells,lamp,other))) continue;
            List<BlockPos> targets=new ArrayList<>();
            for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++) for(int y=-2;y<=2;y++)
                if(Math.abs(x)+Math.abs(z)+Math.abs(y)>0&&Math.abs(x)+Math.abs(z)+Math.abs(y)<=6) targets.add(lamp.offset(x,y,z));
            targets.sort(Comparator.comparingInt((BlockPos at)->Math.abs(at.getX()-lamp.getX())+Math.abs(at.getZ()-lamp.getZ())
                    +2*Math.abs(at.getY()-lamp.getY())).thenComparing(ORDER));
            for(BlockPos target:targets) {
                if(target.getX()<0||target.getZ()<0||target.getY()<1||target.getY()>=p.height()
                        ||target.getX()>=p.sample().width()||target.getZ()>=p.sample().depth()
                        ||original.cells().containsKey(target)||cells.containsKey(target)||!sight(cells,lamp,target,true)) continue;
                if(lanterns(cells).stream().anyMatch(other->!other.equals(lamp)&&crowded(cells,target,other))) continue;
                var routes=PreviewRoomLayout.reachable(cells,p.entrance());
                if(routes.contains(target)||routes.contains(target.below())) continue;
                Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
                BlockState state=cells.get(lamp);Set<BlockPos> omitted=removePendant(draft,lamp);
                Set<BlockPos> placed=new HashSet<>();
                if(!placeLamp(draft,target,state,placed)) continue;
                var light=BiomeArchitecturePreview.lighting(with(p,draft));
                if(!coverage(light,reference,floors)) continue;
                cells.clear();cells.putAll(draft);removed.addAll(omitted);added.removeAll(omitted);
                added.addAll(placed);removed.removeAll(placed);break;
            }
        }
        return new LightingEdits(Set.copyOf(removed),Set.copyOf(added));
    }
    private static boolean indoor(Plan p,BlockPos feet) {
        for(int y=feet.getY()+3;y<p.height();y++) {
            BlockPos top=new BlockPos(feet.getX(),y,feet.getZ());BlockState state=p.cells().get(top);
            if(state!=null&&!(state.getBlock() instanceof LanternBlock)&&!state.is(Blocks.IRON_CHAIN)
                    &&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,top,Direction.DOWN)) return true;
        }
        return false;
    }
    static Set<BlockPos> brightenRooms(Plan p,Map<BlockPos,BlockState> cells) {
        Set<BlockPos> added=new HashSet<>();
        for(int pass=0;pass<80;pass++) {
            Plan draftPlan=with(p,cells);var light=BiomeArchitecturePreview.lighting(draftPlan);
            var dim=PreviewRoomLayout.reachable(cells,p.entrance()).stream().filter(at->indoor(draftPlan,at)
                    &&light.blockLightAt(new com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel(at.getX(),at.getY(),at.getZ()))<7)
                    .sorted(ORDER).toList();
            if(dim.isEmpty()) return Set.copyOf(added);
            boolean changed=false;
            for(BlockPos feet:dim) {
                List<BlockPos> positions=new ArrayList<>();
                for(BlockPos candidate:PreviewRoomLayout.reachable(cells,p.entrance()))
                    if(candidate.getY()==feet.getY()&&candidate.distManhattan(feet)<=4) positions.add(candidate.above(2));
                positions.sort(Comparator.comparingInt((BlockPos at)->at.distManhattan(feet.above(2))).thenComparing(ORDER));
                for(BlockPos at:positions) {
                    if(cells.containsKey(at)||lanterns(cells).stream().anyMatch(other->crowded(cells,at,other))) continue;
                    Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);Set<BlockPos> placed=new HashSet<>();
                    if(!placeLamp(draft,at,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),placed)) continue;
                    var improved=BiomeArchitecturePreview.lighting(with(p,draft));
                    if(improved.blockLightAt(new com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel(feet.getX(),feet.getY(),feet.getZ()))<7) continue;
                    cells.clear();cells.putAll(draft);added.addAll(placed);changed=true;break;
                }
                if(changed) break;
            }
            if(!changed) throw new IllegalStateException("Cannot comfortably light review room: "+p.sample().id()+" "+dim.stream().limit(5).toList());
        }
        throw new IllegalStateException("Review room lighting did not converge: "+p.sample().id());
    }
    private static boolean coverage(com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.ValidationReport light,
            com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.ValidationReport reference,
            List<com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel> floors) {
        return light.spawnSafe()&&floors.stream().allMatch(at->light.blockLightAt(at)>=Math.min(7,reference.blockLightAt(at)));
    }
    private static boolean placeLamp(Map<BlockPos,BlockState> cells,BlockPos at,BlockState lamp,Set<BlockPos> placed) {
        if(!lamp.getValue(LanternBlock.HANGING)) {
            BlockState below=cells.get(at.below());
            if(below==null||!below.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.below(),Direction.UP)) {
                if(at.getY()<3) return false;
                lamp=lamp.setValue(LanternBlock.HANGING,true);
            }
        }
        if(lamp.getValue(LanternBlock.HANGING)) {
            int ceiling=-1;
            for(int y=1;y<=10;y++) {
                BlockPos top=at.above(y);BlockState state=cells.get(top);
                if(state!=null&&!state.isAir()) {
                    if(!glass(state)&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,top,Direction.DOWN)) ceiling=y;
                    break;
                }
            }
            if(ceiling<0) return false;
            for(int y=1;y<ceiling;y++) {cells.put(at.above(y),Blocks.IRON_CHAIN.defaultBlockState());placed.add(at.above(y));}
        } else {
            BlockState support=cells.get(at.below());
            if(support==null||!support.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.below(),Direction.UP)) return false;
        }
        cells.put(at,lamp);placed.add(at);return true;
    }
    private static Set<BlockPos> removePendant(Map<BlockPos,BlockState> cells,BlockPos lamp) {
        BlockState state=cells.remove(lamp);Set<BlockPos> removed=new HashSet<>();removed.add(lamp);
        if(state.getValue(LanternBlock.HANGING)) for(BlockPos at=lamp.above();;at=at.above()) {
            BlockState chain=cells.get(at);
            if(chain==null||!chain.is(Blocks.IRON_CHAIN)||chain.getValue(BlockStateProperties.AXIS)!=Direction.Axis.Y) break;
            boolean shared=false;
            for(Direction side:Direction.Plane.HORIZONTAL) {
                BlockState neighbor=cells.get(at.relative(side));
                if(neighbor!=null&&neighbor.is(Blocks.IRON_CHAIN)
                        &&neighbor.getValue(BlockStateProperties.AXIS)!=Direction.Axis.Y) shared=true;
            }
            if(shared) break;
            cells.remove(at);removed.add(at);
        }
        return removed;
    }
    static List<BlockPos> lanterns(Map<BlockPos,BlockState> cells) {
        return cells.entrySet().stream().filter(e->e.getValue().getBlock() instanceof LanternBlock)
                .map(Map.Entry::getKey).sorted(ORDER).toList();
    }
    static boolean crowded(Map<BlockPos,BlockState> cells,BlockPos a,BlockPos b) {
        int dx=a.getX()-b.getX(),dy=a.getY()-b.getY(),dz=a.getZ()-b.getZ();
        if(dx*dx+dy*dy+dz*dz>=16) return false;
        return sight(cells,a,b,false);
    }
    private static boolean sight(Map<BlockPos,BlockState> cells,BlockPos a,BlockPos b,boolean blockGlass) {
        int dx=a.getX()-b.getX(),dy=a.getY()-b.getY(),dz=a.getZ()-b.getZ();
        int steps=Math.max(Math.max(Math.abs(dx),Math.abs(dy)),Math.abs(dz))*4;
        for(int step=1;step<steps;step++) {
            double t=(double)step/steps;
            BlockPos at=BlockPos.containing(a.getX()+.5-dx*t,a.getY()+.5-dy*t,a.getZ()+.5-dz*t);
            if(at.equals(a)||at.equals(b)) continue;
            BlockState state=cells.get(at);
            if(state!=null&&(state.getLightDampening()>=15||blockGlass&&glass(state))) return false;
        }
        return true;
    }
    static int crowdedPairs(Plan p) {
        var lamps=lanterns(p.cells());int pairs=0;
        for(int i=0;i<lamps.size();i++) for(int j=i+1;j<lamps.size();j++)
            if(crowded(p.cells(),lamps.get(i),lamps.get(j))) pairs++;
        return pairs;
    }
    static void validate(Plan p) {
        var light=BiomeArchitecturePreview.lighting(p);
        for(BlockPos feet:PreviewRoomLayout.reachable(p.cells(),p.entrance())) if(indoor(p,feet)
                &&light.blockLightAt(new com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel(feet.getX(),feet.getY(),feet.getZ()))<7)
            throw new IllegalStateException("Dark review room: "+p.sample().id()+" "+feet);
        for(var entry:p.cells().entrySet()) if(glass(entry.getValue())&&!view(p.cells(),entry.getKey()))
            throw new IllegalStateException("Blind review window: "+p.sample().id()+" at "+entry.getKey());
        // Open markets retain indispensable task lights at separate covered stalls.
        // Enclosed building types must never regress to visible lantern clusters.
        if(!p.sample().role().equals("MARKET_SQUARE")&&crowdedPairs(p)>0)
            throw new IllegalStateException("Crowded review lanterns: "+p.sample().id());
    }
    private static Plan with(Plan p,Map<BlockPos,BlockState> cells) {
        return new Plan(p.sample(),Map.copyOf(cells),p.access(),p.entrance(),p.height());
    }
    static void report(List<Sample> samples) {
        int sealed=0,removed=0,remaining=0;
        for(Sample sample:samples) {
            Plan p=BiomeArchitecturePreview.plan(sample);validate(p);
            var audit=PreviewFacadePrograms.audit(p);
            sealed+=audit.windows().size();
            removed+=audit.removed().stream().filter(at->audit.before().get(at)!=null
                    &&audit.before().get(at).getBlock() instanceof LanternBlock).count();
            int close=crowdedPairs(p);remaining+=close;
            if(close>0) System.out.println("LIGHTING CLOSE "+sample.id()+": "+close+" pairs retained for coverage");
        }
        System.out.println("WINDOW/LIGHT SCOPE: "+sealed+" glazing infills; "+removed+" redundant lanterns removed; "+remaining+" close pairs; all "+samples.size()+" designs night-safe");
    }
}
