package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Room composition and real climbable upper levels for the isolated art-review catalog. */
final class PreviewRoomLayout {
    record Layout(int partitions,int upperLevels,Set<BlockPos> floors,Set<BlockPos> upperTargets,Set<BlockPos> dividerTops) { }
    private static final Map<String,Layout> LAYOUTS=new HashMap<>();
    static Layout layout(Plan p) { return LAYOUTS.getOrDefault(p.sample().id(),new Layout(0,0,Set.of(),Set.of(),Set.of())); }
    static void alias(Plan p,String id) { LAYOUTS.put(id,layout(p)); }
    static boolean floorBearing(Plan p,BlockPos at) {
        return layout(p).floors().contains(at)&&full(p.cells(),at);
    }
    static void validate(Plan p) {
        Layout layout=layout(p);
        validateHeights(p);
        if(!enclosedCorridors(p).isEmpty())
            throw new IllegalStateException(p.sample().id()+" cramped enclosed corridor: "+enclosedCorridors(p));
        for(BlockPos at:layout.dividerTops()) if(!full(p.cells(),at)||!structural(p.cells().get(at.above()),at.above()))
            throw new IllegalStateException(p.sample().id()+" room divider does not meet its ceiling: "+at);
        if(!upperDrops(p.cells(),p.entrance()).isEmpty())
            throw new IllegalStateException(p.sample().id()+" unguarded upper-floor drop: "+upperDrops(p.cells(),p.entrance()));
        if(layout.partitions()>0||layout.upperLevels()>0) {
            Set<BlockPos> reach=reachable(p.cells(),p.entrance());
            if(!reach.containsAll(p.access())||!reach.containsAll(layout.upperTargets()))
                throw new IllegalStateException(p.sample().id()+" inaccessible room or upper level");
        }
        // A floor plate must connect through full structural members to the ground floor.
        // Merely declaring an elevated slab/full-block island as a floor grants no bearing.
        if(layout.floors().isEmpty()) return;
        Set<BlockPos> anchored=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        p.cells().keySet().stream().filter(at->at.getY()==0).forEach(queue::add);
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst(); BlockState state=p.cells().get(at);
            if(state==null||!Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at))||!anchored.add(at)) continue;
            for(Direction d:Direction.values()) queue.add(at.relative(d));
        }
        if(!anchored.containsAll(layout.floors())) {
            var missing=new HashSet<>(layout.floors()); missing.removeAll(anchored);
            throw new IllegalStateException(p.sample().id()+" unanchored upper-floor plate: "+missing.stream()
                    .limit(8).map(at->at+"="+p.cells().get(at)).toList());
        }
    }
    static Plan apply(Plan source) {
        if(source.sample().id().startsWith("compact_")) return source;
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(source.cells());
        Set<BlockPos> access=new HashSet<>(source.access());
        Set<BlockPos> floors=new HashSet<>(),upperTargets=new HashSet<>(),dividerTops=new HashSet<>();
        int partitions=0,levels=0;
        liftLowBeams(source,cells);
        ceilingRooms(source,cells,floors);
        widenCorridors(source,cells,access);
        if(!source.sample().role().equals("MARKET_SQUARE")) {
            // Back sleeping rooms or work/storage offices: doors and lintels, not knee-high logs.
            List<Integer> bedRows=cells.entrySet().stream().filter(e->e.getValue().getBlock() instanceof BedBlock
                    &&e.getValue().getValue(BedBlock.PART)==BedPart.FOOT&&e.getKey().getY()==1)
                    .map(e->e.getKey().getZ()-2).distinct().sorted(Comparator.reverseOrder()).toList();
            List<Integer> rows=new ArrayList<>(bedRows);
            int back=backWall(source,cells);
            rows.addAll(List.of(back-4,back-6,source.entrance().getZ()+3));
            for(int row=back-3;row>source.entrance().getZ()+2;row--) if(!rows.contains(row)) rows.add(row);
            for(int row:rows) if(row>source.entrance().getZ()+1
                    &&partitions<2&&partition(source,cells,access,row,dividerTops)) partitions++;
            if(addAttic(source,cells,access,floors,upperTargets)) levels++;
        }
        closeLowAtticEdges(source,cells,access);
        guardUpperEdges(source,cells,access);
        floors.removeIf(at->!full(cells,at)); // A later ladder hatch is not a floor bearing.
        if(partitions>0||levels>0||!floors.isEmpty()) lightRooms(source,cells);
        LAYOUTS.put(source.sample().id(),new Layout(partitions,levels,Set.copyOf(floors),Set.copyOf(upperTargets),Set.copyOf(dividerTops)));
        Plan result=new Plan(source.sample(),Map.copyOf(cells),Set.copyOf(access),source.entrance(),source.height());
        PreviewDoorwayAudit.validate(result);
        validate(result);
        return result;
    }
    private static int backWall(Plan p,Map<BlockPos,BlockState> cells) {
        int x=p.entrance().getX();
        return cells.keySet().stream().filter(at->at.getY()==0&&at.getX()==x)
                .mapToInt(BlockPos::getZ).max().orElse(p.sample().depth()-1);
    }
    private static boolean partition(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access,int z,Set<BlockPos> tops) {
        // Two parallel screens must not manufacture a one-block-wide passage.
        if(tops.stream().anyMatch(at->Math.abs(at.getZ()-z)<4)) return false;
        int center=p.entrance().getX(),left=center,right=center;
        while(full(cells,new BlockPos(left-1,0,z))) left--;
        while(full(cells,new BlockPos(right+1,0,z))) right++;
        while(left<center&&!full(cells,new BlockPos(left,1,z))) left++;
        while(right>center&&!full(cells,new BlockPos(right,1,z))) right--;
        if(right-left<5||!full(cells,new BlockPos(left,1,z))||!full(cells,new BlockPos(right,1,z))) return false;
        // Do not carve furniture, beds, existing doors or a service approach to manufacture a room.
        for(int x=left+1;x<right;x++) for(int y=1;y<=2;y++) {
            BlockPos at=new BlockPos(x,y,z); BlockState state=cells.get(at);
            if(access.contains(at)||state!=null&&!(state.getBlock() instanceof CarpetBlock)
                    &&!state.isAir()&&!(state.getBlock() instanceof RotatedPillarBlock)) return false;
        }
        Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
        Set<BlockPos> proposedTops=new HashSet<>();
        Block wall=p.sample().style()==BiomeDialect.DESERT?Blocks.SMOOTH_SANDSTONE
                :p.sample().style()==BiomeDialect.SAVANNA?Blocks.ACACIA_PLANKS
                :p.sample().style()==BiomeDialect.PLAINS?Blocks.OAK_PLANKS:Blocks.SPRUCE_PLANKS;
        for(int x=left+1;x<right;x++) {
            int top=ceiling(draft,new BlockPos(x,1,z),p.height());
            if(top<4) return false;
            proposedTops.add(new BlockPos(x,top-1,z));
            for(int y=1;y<top;y++) {
                BlockPos at=new BlockPos(x,y,z);
                // A partition must meet its ceiling, without burying an authored fixture.
                BlockState existing=draft.get(at);
                if(y>3&&existing!=null&&!existing.isAir()&&!existing.is(Blocks.IRON_CHAIN)) return false;
                draft.put(at,wall.defaultBlockState());
            }
        }
        Block door=new BiomeArchitecturePreview.Builder(p.sample()).p.door();
        BlockState lower=door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH);
        draft.put(new BlockPos(center,1,z),lower);
        draft.put(new BlockPos(center,2,z),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        if(!reachable(draft,p.entrance()).containsAll(access)) return false;
        if(narrowCorridors(draft,p.entrance()).size()>narrowCorridors(cells,p.entrance()).size()) return false;
        var test=new Plan(p.sample(),Map.copyOf(draft),Set.copyOf(access),p.entrance(),p.height());
        try { PreviewDoorwayAudit.validate(test); }
        catch(IllegalStateException error) { return false; }
        cells.clear(); cells.putAll(draft);tops.addAll(proposedTops); return true;
    }
    private static boolean addAttic(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access,
            Set<BlockPos> floors,Set<BlockPos> targets) {
        int back=backWall(p,cells),z1=back-1,z0=Math.max(p.entrance().getZ()+3,back-5);
        if(z1-z0<2) return false;
        for(int y:new int[]{4,5,6}) for(int x:new int[]{p.entrance().getX(),p.entrance().getX()-1,p.entrance().getX()+1}) {
            BlockPos ladder=new BlockPos(x,1,z1);
            if(!PreviewDoorwayAudit.clear(cells,ladder)||!full(cells,ladder.below())) continue;
            boolean bearing=true;
            for(int yy=1;yy<=y;yy++) bearing&=full(cells,new BlockPos(x,yy,back));
            if(!bearing) continue;
            int left=x,right=x;
            while(full(cells,new BlockPos(left-1,0,z0))) left--;
            while(full(cells,new BlockPos(right+1,0,z0))) right++;
            if(right-left<5) continue;
            boolean space=true;
            for(int z=z0;z<=z1;z++) for(int dx=-1;dx<=1;dx++)
                space&=PreviewDoorwayAudit.clear(cells,new BlockPos(x+dx,y+1,z))
                        &&clearThird(cells,new BlockPos(x+dx,y+1,z))
                        &&covered(cells,new BlockPos(x+dx,y+1,z));
            if(!space) continue;
            // A full wall-to-wall floor plate is carried by the existing perimeter, not a
            // floating slab island. The ladder hatch is inside the weather envelope.
            Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
            Set<BlockPos> plate=new HashSet<>();
            Block floor=p.sample().style()==BiomeDialect.DESERT?Blocks.SMOOTH_SANDSTONE
                    :p.sample().style()==BiomeDialect.SAVANNA?Blocks.ACACIA_PLANKS
                    :p.sample().style()==BiomeDialect.PLAINS?Blocks.OAK_PLANKS:Blocks.SPRUCE_PLANKS;
            for(int xx=left;xx<=right;xx++) for(int z=z0;z<=back;z++) {
                BlockPos at=new BlockPos(xx,y,z);
                // Retain the authored roof curb, chimney and perimeter material.
                if(!draft.containsKey(at)||draft.get(at).isAir()||draft.get(at).is(Blocks.IRON_CHAIN)
                        ||draft.get(at).getBlock() instanceof LanternBlock) {
                    draft.put(at,floor.defaultBlockState()); plate.add(at);
                    removeChainsAbove(draft,at);
                }
            }
            BlockState climbing=Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);
            for(int yy=1;yy<=y;yy++) draft.put(new BlockPos(x,yy,z1),climbing);
            plate.remove(new BlockPos(x,y,z1));
            BlockPos upper=new BlockPos(x,y+1,z0+1);
            BlockPos cabinet=new BlockPos(x-1,y+1,z0);
            draft.put(cabinet,Blocks.BARREL.defaultBlockState());
            draft.put(cabinet.above(),Blocks.LANTERN.defaultBlockState());
            draft.put(new BlockPos(x+1,y+1,z0),Blocks.BOOKSHELF.defaultBlockState());
            Set<BlockPos> required=new HashSet<>(access); required.add(ladder); required.add(upper);
            if(!reachable(draft,p.entrance()).containsAll(required)) continue;
            var test=new Plan(p.sample(),Map.copyOf(draft),Set.copyOf(required),p.entrance(),p.height());
            try { PreviewDoorwayAudit.validate(test); }
            catch(IllegalStateException error) { continue; }
            cells.clear(); cells.putAll(draft); access.addAll(required); floors.addAll(plate); targets.add(upper);
            return true;
        }
        return false;
    }
    private static boolean covered(Map<BlockPos,BlockState> cells,BlockPos feet) {
        for(int y=feet.getY()+2;y<=feet.getY()+12;y++) {
            BlockPos at=new BlockPos(feet.getX(),y,feet.getZ()); BlockState state=cells.get(at);
            if(state!=null&&!(state.getBlock() instanceof LanternBlock)&&!state.is(Blocks.IRON_CHAIN)
                    &&!state.getCollisionShape(EmptyBlockGetter.INSTANCE,at).isEmpty()) return true;
        }
        return false;
    }
    private static Block finishMaterial(Plan p) {
        return p.sample().style()==BiomeDialect.DESERT?Blocks.SMOOTH_SANDSTONE
                :p.sample().style()==BiomeDialect.SAVANNA?Blocks.ACACIA_PLANKS
                :p.sample().style()==BiomeDialect.PLAINS?Blocks.OAK_PLANKS:Blocks.SPRUCE_PLANKS;
    }
    private static boolean structural(BlockState state,BlockPos at) {
        return state!=null&&!state.hasBlockEntity()&&!(state.getBlock() instanceof DoorBlock)
                &&!(state.getBlock() instanceof LadderBlock)&&!(state.getBlock() instanceof LanternBlock)
                &&!state.is(Blocks.IRON_CHAIN)&&(Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at))
                        ||state.getBlock() instanceof SlabBlock||state.getBlock() instanceof StairBlock
                        ||state.getBlock() instanceof IronBarsBlock);
    }
    static boolean clearThird(Map<BlockPos,BlockState> cells,BlockPos feet) {
        return !structural(cells.get(feet.above(2)),feet.above(2));
    }
    static void validateHeights(Plan p) {
        // Beds are not walkable air, but their sleeping space is still part of a room.
        for(var entry:p.cells().entrySet()) if(entry.getValue().getBlock() instanceof BedBlock
                &&!clearThird(p.cells(),entry.getKey()))
            throw new IllegalStateException(p.sample().id()+" ceiling below three clear blocks over bed: "+entry.getKey());
        for(BlockPos feet:reachable(p.cells(),p.entrance())) {
            BlockState foot=p.cells().get(feet);
            // Vanilla doors remain two blocks tall; a ladder hatch is a transition,
            // not the ceiling of a habitable room. Usable floor areas get three blocks.
            if(foot!=null&&(foot.getBlock() instanceof DoorBlock||foot.getBlock() instanceof LadderBlock)
                    ||ladder(p.cells().get(feet.below()))) continue;
            if(!clearThird(p.cells(),feet)) throw new IllegalStateException(p.sample().id()+" ceiling below three clear blocks: "+feet);
        }
    }
    private static int ceiling(Map<BlockPos,BlockState> cells,BlockPos feet,int height) {
        for(int y=feet.getY()+2;y<=height;y++) {
            BlockPos at=new BlockPos(feet.getX(),y,feet.getZ());
            if(structural(cells.get(at),at)) return y;
        }
        return -1;
    }
    private static void liftLowBeams(Plan p,Map<BlockPos,BlockState> cells) {
        // Move low ground-floor ties/canopies up, retaining any already higher roof.
        Set<BlockPos> lifted=new HashSet<>();
        Set<BlockPos> occupied=new HashSet<>(reachable(cells,p.entrance()));
        cells.forEach((at,state)-> {if(state.getBlock() instanceof BedBlock) occupied.add(at);});
        for(BlockPos feet:occupied) {
            BlockState foot=cells.get(feet);
            if(feet.getY()!=1||foot!=null&&(foot.getBlock() instanceof DoorBlock||foot.getBlock() instanceof LadderBlock)) continue;
            BlockPos at=feet.above(2);BlockState state=cells.get(at);
            if(!structural(state,at)) continue;
            BlockState above=cells.get(at.above());
            if(above!=null&&above.hasBlockEntity()) throw new IllegalStateException("Low beam carries protected fixture: "+p.sample().id()+" "+at);
            cells.remove(at);
            if(!structural(above,at.above())) {
                // A partial decorative strip must become a carried full ceiling course.
                cells.put(at.above(),state.getBlock() instanceof SlabBlock||state.getBlock() instanceof StairBlock
                        ||state.getBlock() instanceof IronBarsBlock
                        ?finishMaterial(p).defaultBlockState():state);
                lifted.add(at.above());removeChainsAbove(cells,at.above());
            }
            BlockState lamp=cells.get(at.below());
            if(lamp!=null&&lamp.getBlock() instanceof LanternBlock&&lamp.getValue(LanternBlock.HANGING)) {
                cells.remove(at.below());cells.put(at,lamp);
            }
            for(Direction side:Direction.Plane.HORIZONTAL) {
                BlockPos fixture=at.relative(side);BlockState attached=cells.get(fixture);
                if(attached!=null&&attached.getBlock() instanceof WallTorchBlock&&attached.getValue(WallTorchBlock.FACING)==side) {
                    cells.remove(fixture);
                    if(!cells.containsKey(fixture.above())) cells.put(fixture.above(),attached);
                }
            }
        }
        for(BlockPos at:lifted) for(Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos bearing=at.relative(side);
            if(cells.containsKey(bearing)) continue;
            boolean post=true;
            for(int y=0;y<4;y++) post&=full(cells,new BlockPos(bearing.getX(),y,bearing.getZ()));
            if(post) cells.put(bearing,cells.get(bearing.below()));
        }
    }
    private static void removeChainsAbove(Map<BlockPos,BlockState> cells,BlockPos at) {
        for(BlockPos pos=at.above();;pos=pos.above()) {
            BlockState state=cells.get(pos);
            if(state==null||!state.is(Blocks.IRON_CHAIN)||state.getValue(BlockStateProperties.AXIS)!=Direction.Axis.Y) break;
            cells.remove(pos);
        }
    }
    private static void ceilingRooms(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> floors) {
        if(p.sample().role().equals("MARKET_SQUARE")) return;
        // Flood the outside at window height with walls, panes and closed doors as barriers.
        Set<BlockPos> outside=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(new BlockPos(-1,2,-1));
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();
            if(at.getX()<-1||at.getZ()<-1||at.getX()>p.sample().width()||at.getZ()>p.sample().depth()||!outside.add(at)) continue;
            BlockState state=cells.get(at);
            if(state!=null&&!state.isAir()) continue;
            for(Direction side:Direction.Plane.HORIZONTAL) queue.add(at.relative(side));
        }
        Set<BlockPos> hatches=new HashSet<>();
        for(BlockPos at:reachable(cells,p.entrance())) if(at.getY()>1) {
            hatches.add(at);hatches.add(at.above());hatches.add(at.above(2));
        }
        var before=Map.copyOf(cells);
        for(int x=0;x<p.sample().width();x++) for(int z=0;z<p.sample().depth();z++) {
            BlockPos at=new BlockPos(x,4,z),feet=new BlockPos(x,1,z);
            if(outside.contains(feet.above())||!full(cells,feet.below())||!covered(before,feet)||hatches.contains(at)) continue;
            BlockState current=cells.get(at);
            if(current!=null&&!current.isAir()&&!(current.getBlock() instanceof LanternBlock)&&!current.is(Blocks.IRON_CHAIN)) continue;
            cells.put(at,finishMaterial(p).defaultBlockState());floors.add(at);removeChainsAbove(cells,at);
        }
        // Narrow appendages may meet only partial roof shapes, which cannot carry a
        // floor plate. Retain their authored ceiling instead of adding a floating cap.
        Set<BlockPos> carried=new HashSet<>();queue.clear();
        cells.keySet().stream().filter(at->at.getY()==0).forEach(queue::add);
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();BlockState state=cells.get(at);
            if(state==null||!Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at))||!carried.add(at)) continue;
            for(Direction side:Direction.values()) queue.add(at.relative(side));
        }
        for(BlockPos at:new HashSet<>(floors)) if(!carried.contains(at)) {
            cells.remove(at);floors.remove(at);
        }
    }
    private static void closeLowAtticEdges(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access) {
        // Finished knee walls exclude the shallow eaves from the usable loft, not a
        // hidden two-block-high walking zone. Existing functional targets stay clear.
        for(BlockPos feet:reachable(cells,p.entrance())) {
            BlockState foot=cells.get(feet);
            if(feet.getY()<=1||ladder(cells.get(feet.below()))||foot!=null&&(foot.getBlock() instanceof DoorBlock||foot.getBlock() instanceof LadderBlock)
                    ||clearThird(cells,feet)) continue;
            if(access.contains(feet)) throw new IllegalStateException("Low occupied upper room: "+p.sample().id()+" "+feet);
            cells.put(feet,finishMaterial(p).defaultBlockState());
            cells.put(feet.above(),finishMaterial(p).defaultBlockState());
        }
    }
    static Set<BlockPos> narrowCorridors(Map<BlockPos,BlockState> cells,BlockPos entrance) {
        Set<BlockPos> reach=reachable(cells,entrance),result=new HashSet<>();
        for(BlockPos feet:reach) for(Direction along:List.of(Direction.EAST,Direction.SOUTH)) {
            Direction side=along.getClockWise();List<BlockPos> run=new ArrayList<>();
            for(BlockPos at=feet;reach.contains(at)&&covered(cells,at)&&wallColumn(cells,at.relative(side))
                    &&wallColumn(cells,at.relative(side.getOpposite()));at=at.relative(along)) run.add(at);
            if(run.size()>=4) result.addAll(run);
        }
        return Set.copyOf(result);
    }
    static Set<BlockPos> enclosedCorridors(Plan p) {
        Set<BlockPos> outside=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(new BlockPos(-1,2,-1));
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();
            if(at.getX()<-1||at.getZ()<-1||at.getX()>p.sample().width()||at.getZ()>p.sample().depth()||!outside.add(at)) continue;
            BlockState state=p.cells().get(at);
            if(state!=null&&!state.isAir()) continue;
            for(Direction side:Direction.Plane.HORIZONTAL) queue.add(at.relative(side));
        }
        var result=new HashSet<>(narrowCorridors(p.cells(),p.entrance()));
        result.removeIf(at->outside.contains(at.above())||p.cells().get(at)!=null&&p.cells().get(at).getBlock() instanceof DoorBlock);
        Set<BlockPos> longRuns=new HashSet<>();
        for(BlockPos at:result) for(Direction along:List.of(Direction.EAST,Direction.SOUTH)) {
            List<BlockPos> run=new ArrayList<>();
            for(BlockPos next=at;result.contains(next);next=next.relative(along)) run.add(next);
            if(run.size()>=4) longRuns.addAll(run);
        }
        return Set.copyOf(longRuns);
    }
    private static boolean wallColumn(Map<BlockPos,BlockState> cells,BlockPos at) {
        for(int y=0;y<2;y++) {
            BlockState state=cells.get(at.above(y));
            if(state==null||state.hasBlockEntity()||!(Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at.above(y)))
                    ||state.getBlock() instanceof IronBarsBlock)) return false;
        }
        return true;
    }
    private static void widenCorridors(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access) {
        // Open a divider into the neighboring room, never an exterior wall or a cabinet.
        Set<BlockPos> outside=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(new BlockPos(-1,2,-1));
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();
            if(at.getX()<-1||at.getZ()<-1||at.getX()>p.sample().width()||at.getZ()>p.sample().depth()||!outside.add(at)) continue;
            BlockState state=cells.get(at);
            if(state!=null&&!state.isAir()) continue;
            for(Direction side:Direction.Plane.HORIZONTAL) queue.add(at.relative(side));
        }
        for(int pass=0;pass<4;pass++) {
            var cramped=narrowCorridors(cells,p.entrance()).stream().sorted(Comparator.comparingInt((BlockPos b)->b.getY())
                    .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).toList();
            boolean changed=false;
            for(BlockPos feet:cramped) for(Direction side:Direction.Plane.HORIZONTAL) {
                if(feet.getY()!=1) continue;
                BlockPos wall=feet.relative(side),room=wall.relative(side);
                if(!wallColumn(cells,wall)||outside.contains(feet.above())) continue;
                List<BlockPos> columns=new ArrayList<>();columns.add(wall);
                for(int depth=0;depth<3&&wallColumn(cells,room)&&!outside.contains(room.above());depth++) {
                    columns.add(room);room=room.relative(side);
                }
                boolean intoRoom=walkable(cells,room)&&covered(cells,room)&&!outside.contains(room.above());
                // A one-cell enclosed connecting wing has no adjacent room to borrow.
                // Move its side wall out by one cell, carrying its foundation and ceiling.
                boolean extend=columns.size()==1&&full(cells,wall.below())&&!full(cells,room.below())&&PreviewDoorwayAudit.clear(cells,room)
                        &&structural(cells.get(feet.above(3)),feet.above(3))
                        &&structural(cells.get(wall.above(3)),wall.above(3))
                        &&(!cells.containsKey(room.above(2))||structural(cells.get(room.above(2)),room.above(2)))
                        &&(!cells.containsKey(room.above(3))||structural(cells.get(room.above(3)),room.above(3)))
                        &&room.getX()>0&&room.getZ()>0&&room.getX()<p.sample().width()-1&&room.getZ()<p.sample().depth()-1;
                if(!intoRoom&&!extend) continue;
                boolean safe=true;
                for(BlockPos column:columns) for(int y=1;y<=3;y++) {
                    BlockPos at=new BlockPos(column.getX(),y,column.getZ());BlockState state=cells.get(at);
                    safe&=state!=null&&!state.hasBlockEntity()
                            &&(Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at))
                                    ||state.getBlock() instanceof IronBarsBlock||state.getBlock() instanceof FenceBlock);
                    for(Direction face:Direction.Plane.HORIZONTAL) {
                        BlockState fixture=cells.get(at.relative(face));
                        if(fixture!=null&&fixture.getBlock() instanceof WallTorchBlock) safe=false;
                    }
                }
                if(!safe) continue;
                Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
                if(extend) {
                    draft.put(room.below(),cells.get(wall.below()));
                    draft.put(wall.below(),cells.get(feet.below()));
                    for(int y=0;y<3;y++) draft.put(room.above(y),cells.get(wall.above(y)));
                    draft.putIfAbsent(room.above(3),cells.get(wall.above(3)));
                }
                for(BlockPos column:columns) for(int y=1;y<=3;y++) draft.remove(new BlockPos(column.getX(),y,column.getZ()));
                if(!reachable(draft,p.entrance()).containsAll(access)||!PreviewSupportAudit.floating(draft).isEmpty()) continue;
                cells.clear();cells.putAll(draft);changed=true;break;
            }
            if(!changed) break;
        }
    }
    static Set<BlockPos> upperDrops(Map<BlockPos,BlockState> cells,BlockPos entrance) {
        Set<BlockPos> result=new HashSet<>();
        for(BlockPos feet:reachable(cells,entrance)) {
            if(feet.getY()<5||ladder(cells.get(feet))||ladder(cells.get(feet.below()))||!full(cells,feet.below())) continue;
            for(Direction side:Direction.Plane.HORIZONTAL) {
                BlockPos gap=feet.relative(side);
                if(full(cells,gap.below())||ladder(cells.get(gap))||ladder(cells.get(gap.below()))
                        ||!full(cells,new BlockPos(gap.getX(),0,gap.getZ()))
                        ||!PreviewDoorwayAudit.clear(cells,gap)||!covered(cells,gap)) continue;
                result.add(feet);
            }
        }
        return Set.copyOf(result);
    }
    static void guardUpperEdges(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access) {
        Block rail=p.sample().style()==BiomeDialect.DESERT?Blocks.SANDSTONE_WALL
                :p.sample().style()==BiomeDialect.SAVANNA?Blocks.ACACIA_FENCE
                :p.sample().style()==BiomeDialect.PLAINS?Blocks.OAK_FENCE:Blocks.SPRUCE_FENCE;
        var edges=upperDrops(cells,p.entrance()).stream().sorted(Comparator.comparingInt((BlockPos b)->b.getY())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).toList();
        for(BlockPos at:edges) {
            BlockState old=cells.get(at);
            if(access.contains(at)||old!=null&&!old.isAir()&&!(old.getBlock() instanceof CarpetBlock)) continue;
            Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);draft.put(at,rail.defaultBlockState());
            if(!reachable(draft,p.entrance()).containsAll(access)) continue;
            cells.clear();cells.putAll(draft);
        }
    }
    private static void lightRooms(Plan p,Map<BlockPos,BlockState> cells) {
        // Real roof-attached pendants on a regular room grid, never a solid lamp pedestal
        // in a circulation route. Partitions and floor plates change light propagation.
        Set<BlockPos> routes=reachable(cells,p.entrance());
        List<BlockPos> ordered=routes.stream().sorted(Comparator.comparingInt((BlockPos at)->at.getY())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).toList();
        Set<BlockPos> fixtures=new HashSet<>();
        cells.forEach((at,state)-> { if(state.getBlock() instanceof LanternBlock) fixtures.add(at); });
        for(BlockPos feet:ordered) {
            if(fixtures.stream().anyMatch(at->Math.abs(at.getX()-feet.getX())+Math.abs(at.getZ()-feet.getZ())<=4
                    &&Math.abs(at.getY()-(feet.getY()+2))<=1)) continue;
            BlockPos lamp=feet.above(2);
            if(routes.contains(lamp)||routes.contains(lamp.below())) continue;
            if(cells.containsKey(lamp)&&!cells.get(lamp).isAir()) continue;
            int ceiling=-1;
            for(int y=lamp.getY()+1;y<=lamp.getY()+10;y++) {
                BlockPos at=new BlockPos(lamp.getX(),y,lamp.getZ()); BlockState state=cells.get(at);
                if(state!=null&&!state.isAir()) {
                    if(state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.DOWN)) ceiling=y;
                    break;
                }
            }
            if(ceiling<0) continue;
            for(int y=lamp.getY()+1;y<ceiling;y++) cells.put(new BlockPos(lamp.getX(),y,lamp.getZ()),Blocks.IRON_CHAIN.defaultBlockState());
            cells.put(lamp,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true)); fixtures.add(lamp);
        }
    }
    static Set<BlockPos> reachable(Map<BlockPos,BlockState> cells,BlockPos start) {
        Set<BlockPos> seen=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>(); queue.add(start);
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();
            if(!walkable(cells,at)||!seen.add(at)) continue;
            for(Direction d:Direction.Plane.HORIZONTAL) {
                queue.add(at.relative(d));
            }
            if(ladder(cells.get(at))) { queue.add(at.above()); queue.add(at.below()); }
            else if(ladder(cells.get(at.below()))) queue.add(at.below());
        }
        return Set.copyOf(seen);
    }
    private static boolean walkable(Map<BlockPos,BlockState> cells,BlockPos at) {
        if(at.getY()>1&&!ladder(cells.get(at))&&!covered(cells,at)) return false;
        return (full(cells,at.below())||ladder(cells.get(at))||ladder(cells.get(at.below())))
                &&PreviewDoorwayAudit.clear(cells,at);
    }
    private static boolean ladder(BlockState state) { return state!=null&&state.getBlock() instanceof LadderBlock; }
    private static boolean full(Map<BlockPos,BlockState> cells,BlockPos at) {
        BlockState state=cells.get(at);
        return state!=null&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP);
    }
    static void register(Plan p,int partitions,int upperLevels,Set<BlockPos> floors,Set<BlockPos> targets) {
        register(p,partitions,upperLevels,floors,targets,Set.of());
    }
    static void register(Plan p,int partitions,int upperLevels,Set<BlockPos> floors,Set<BlockPos> targets,Set<BlockPos> tops) {
        LAYOUTS.put(p.sample().id(),new Layout(partitions,upperLevels,Set.copyOf(floors),Set.copyOf(targets),Set.copyOf(tops)));
    }
}
