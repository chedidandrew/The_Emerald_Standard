package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.Plan;

/** Explicit release corrections, never applied to historical saved plans. */
final class PreviewBuildingUsability {
    private static final Set<Plan> FINISHED=new HashSet<>();
    static Plan apply(Plan source) {
        if(FINISHED.contains(source))return source;
        if(source.sample().style()!=com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.SNOWY)
            return finished(PreviewNaturalBuildingFinish.apply(compose(source)));
        // Roof projection must not reroll the existing coordinate-sensitive circulation
        // and lighting layout. Compose the old core first, then attach its new eaves.
        source=PreviewSnowyOverhang.before(source);
        var envelope=source.sample();String id=envelope.id();
        int margin=id.startsWith("catalog_snowy_")||id.equals("snowy_village_bank")||id.equals("snowy_hearth_home")?1:0;
        if(margin!=0)source=new Plan(new BiomeArchitecturePreview.Sample(envelope.style(),envelope.role(),id,
                envelope.width()-2,envelope.depth()-2),PreviewPlanTranslation.cells(source.cells(),-1,-1),
                PreviewPlanTranslation.positions(source.access(),-1,-1),source.entrance().offset(-1,0,-1),source.height());
        Plan composed=PreviewOutdoorLampOrientation.apply(compose(source));
        if(margin!=0)composed=new Plan(envelope,PreviewPlanTranslation.cells(composed.cells(),1,1),
                PreviewPlanTranslation.positions(composed.access(),1,1),composed.entrance().offset(1,0,1),composed.height());
        return finished(PreviewNaturalBuildingFinish.apply(PreviewSnowyOverhang.afterUsability(composed)));
    }
    private static Plan finished(Plan plan) {FINISHED.add(plan);return plan;}
    private static Plan compose(Plan source) {
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(source.cells());
        Set<BlockPos> access=new HashSet<>(source.access()),candidates=new HashSet<>();
        cells.forEach((at,state)-> {
            if(state.getBlock() instanceof StairBlock&&state.getValue(StairBlock.HALF)==Half.BOTTOM
                    &&at.getY()>=1&&full(cells,at.below())&&clear(cells,at.above())) candidates.add(at);
        });
        cells.putAll(PreviewSeatingAudit.corrected(cells,candidates));
        reverseBeds(cells);turnLamps(cells,access);
        for(var room:upperRooms(cells,source.sample().width(),source.sample().depth(),source.height())) {
            if(!Collections.disjoint(room,PreviewRoomLayout.usableReachable(cells,source.entrance()))) continue;
            if(!connect(source,cells,access,room)) throw new IllegalStateException("NO SAFE ACCESS "+source.sample().id()+" room="+room+" evidence="+roomEvidence(cells,room,source.entrance()));
        }
        PreviewRoomLayout.lightUsableRooms(source,cells);
        return new Plan(source.sample(),Map.copyOf(cells),Set.copyOf(access),source.entrance(),source.height());
    }
    private static boolean connect(Plan source,Map<BlockPos,BlockState> cells,Set<BlockPos> access,Set<BlockPos> room) {
        var reach=PreviewRoomLayout.usableReachable(cells,source.entrance());
        var ordered=room.stream().sorted(Comparator.comparingInt((BlockPos at)->-at.getZ()).thenComparingInt(BlockPos::getX)).toList();
        for(int bearingPass=0;bearingPass<2;bearingPass++) for(BlockPos upper:ordered) for(Direction facing:Direction.Plane.HORIZONTAL) {
            BlockPos behind=upper.relative(facing.getOpposite());
            BlockPos lower=reach.stream().filter(at->at.getX()==upper.getX()&&at.getZ()==upper.getZ()&&at.getY()<upper.getY())
                    .max(Comparator.comparingInt(BlockPos::getY)).orElse(null);
            if(lower==null||!PreviewRoomLayout.standingSupport(cells,lower.below())) continue;
            boolean safe=true;
            for(int y=lower.getY();y<upper.getY();y++) {
                BlockPos at=new BlockPos(upper.getX(),y,upper.getZ());var state=cells.get(at);
                // Pierce only the upper floor. Never remove furniture or intermediate frames.
                safe&=(state==null||state.isAir()||state.getBlock() instanceof CarpetBlock||state.getBlock() instanceof LadderBlock
                        ||y==upper.getY()-1&&full(cells,at)&&!state.hasBlockEntity())
                        &&(sturdy(cells,new BlockPos(behind.getX(),y,behind.getZ()),facing)
                            ||bearingPass==1&&clear(cells,new BlockPos(behind.getX(),y,behind.getZ()))
                                &&!access.contains(new BlockPos(behind.getX(),y,behind.getZ())));
            }
            safe&=full(cells,new BlockPos(behind.getX(),lower.getY()-1,behind.getZ()));
            if(!safe) continue;
            var draft=new LinkedHashMap<>(cells);
            for(int y=lower.getY();y<upper.getY();y++) {
                var bearing=new BlockPos(behind.getX(),y,behind.getZ());
                if(!sturdy(cells,bearing,facing)) draft.put(bearing,new BiomeArchitecturePreview.Builder(source.sample()).p.wall().defaultBlockState());
                draft.put(new BlockPos(upper.getX(),y,upper.getZ()),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,facing));
            }
            var after=PreviewRoomLayout.usableReachable(draft,source.entrance());
            if(!after.containsAll(access)||Collections.disjoint(after,room)) continue;
            cells.clear();cells.putAll(draft);access.add(lower);access.add(upper);return true;
        }
        // A divided upper level may need an internal doorway, not another shaft.
        for(var upper:ordered) for(Direction d:Direction.Plane.HORIZONTAL) {
            var wall=upper.relative(d);var other=wall.relative(d);
            if(!reach.contains(other)||!PreviewDoorwayAudit.clear(cells,upper)||!PreviewDoorwayAudit.clear(cells,other)
                    ||!plankWall(cells,wall)||!plankWall(cells,wall.above())
                    ||!full(cells,wall.below())||access.contains(wall)||access.contains(wall.above())) continue;
            var draft=new LinkedHashMap<>(cells);
            var door=new BiomeArchitecturePreview.Builder(source.sample()).p.door().defaultBlockState().setValue(DoorBlock.FACING,d);
            draft.put(wall,door);draft.put(wall.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            var after=PreviewRoomLayout.usableReachable(draft,source.entrance());
            if(!after.containsAll(access)||Collections.disjoint(after,room)) continue;
            cells.clear();cells.putAll(draft);access.add(upper);return true;
        }
        return false;
    }
    private static void reverseBeds(Map<BlockPos,BlockState> cells) {
        for(var entry:List.copyOf(cells.entrySet())) {
            var state=entry.getValue();
            if(!(state.getBlock() instanceof BedBlock)||state.getValue(BedBlock.PART)!=BedPart.FOOT) continue;
            Direction facing=state.getValue(BedBlock.FACING);BlockPos foot=entry.getKey(),head=foot.relative(facing);
            var other=cells.get(head);
            if(other==null||other.getBlock()!=state.getBlock()||other.getValue(BedBlock.PART)!=BedPart.HEAD||other.getValue(BedBlock.FACING)!=facing) throw new IllegalStateException("Invalid bed pair "+foot);
            // Keep already-correct pairs; pillow goes beside the nightstand/headboard.
            if(!nightstand(cells.get(foot.relative(facing.getOpposite())))||nightstand(cells.get(head.relative(facing)))) continue;
            cells.put(foot,state.setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,facing.getOpposite()));
            cells.put(head,other.setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,facing.getOpposite()));
        }
    }
    private static boolean nightstand(BlockState state) {return state!=null&&(state.is(Blocks.BARREL)||state.is(Blocks.BOOKSHELF));}
    private static void turnLamps(Map<BlockPos,BlockState> cells,Set<BlockPos> access) {
        for(var entry:List.copyOf(cells.entrySet())) {
            BlockPos lamp=entry.getKey();var state=entry.getValue();
            if(!(state.getBlock() instanceof LanternBlock)||!state.getValue(LanternBlock.HANGING)||lamp.getY()!=2||!is(cells,lamp.above(),Blocks.IRON_CHAIN)) continue;
            for(Direction towardPost:Direction.Plane.HORIZONTAL) {
                BlockPos post=lamp.relative(towardPost);var knee=cells.get(post.above());var cap=cells.get(post.above(2));
                if(cells.get(post)==null||!(cells.get(post).getBlock() instanceof FenceBlock||cells.get(post).getBlock() instanceof WallBlock)||knee==null||!(knee.getBlock() instanceof StairBlock)||cap==null||!full(cells,post.above(2))) continue;
                var arm=cells.get(lamp.above(2));
                if(arm==null||arm.hasBlockEntity()||!(arm.getBlock() instanceof RotatedPillarBlock||arm.getBlock() instanceof SlabBlock||full(cells,lamp.above(2)))) continue;
                boolean wall=false;
                for(Direction d:Direction.Plane.HORIZONTAL) if(d!=towardPost) wall|=full(cells,lamp.relative(d))&&full(cells,lamp.relative(d).above());
                if(!wall) break;
                for(Direction outward:List.of(towardPost,towardPost.getClockWise(),towardPost.getCounterClockWise())) {
                    BlockPos target=post.relative(outward);
                    if(access.contains(target)||access.contains(target.above())||access.contains(target.below())||!clear(cells,target)||!clear(cells,target.above())||!clear(cells,target.above(2))) continue;
                    boolean clearSides=true;
                    for(Direction d:Direction.Plane.HORIZONTAL) if(d!=outward.getOpposite()) clearSides&=!full(cells,target.relative(d))&&!full(cells,target.relative(d).above());
                    if(!clearSides) continue;
                    cells.remove(lamp);cells.remove(lamp.above());
                    // Keep a shared beam/eave bearing; only a standalone arm may be removed.
                    if(clear(cells,lamp.above(3))) cells.remove(lamp.above(2));
                    var newArm=arm.getBlock() instanceof RotatedPillarBlock?arm.setValue(RotatedPillarBlock.AXIS,outward.getAxis()):arm;
                    cells.put(target,state);cells.put(target.above(),Blocks.IRON_CHAIN.defaultBlockState());cells.put(target.above(2),newArm);
                    cells.put(post.above(),knee.setValue(StairBlock.FACING,outward));break;
                }
                break;
            }
        }
    }
    private static boolean is(Map<BlockPos,BlockState> cells,BlockPos at,Block block) {return cells.get(at)!=null&&cells.get(at).is(block);}
    private static boolean plankWall(Map<BlockPos,BlockState> cells,BlockPos at) {return cells.get(at)!=null&&cells.get(at).is(net.minecraft.tags.BlockTags.PLANKS);}
    private static List<String> roomEvidence(Map<BlockPos,BlockState> cells,Set<BlockPos> room,BlockPos entrance) {
        var reach=PreviewRoomLayout.usableReachable(cells,entrance);var evidence=new ArrayList<String>();
        for(var upper:room) {
            for(int y=0;y<=upper.getY()+2;y++) {
                var at=new BlockPos(upper.getX(),y,upper.getZ());
                evidence.add(at+"="+cells.get(at)+" reachable="+reach.contains(at));
            }
            for(var direction:Direction.Plane.HORIZONTAL) {
                var side=upper.relative(direction);
                evidence.add("side "+side+"="+cells.get(side)+" head="+cells.get(side.above())+" other="+cells.get(side.relative(direction))+" reach="+reach.contains(side.relative(direction)));
            }
        }
        return evidence;
    }
    private static boolean clear(Map<BlockPos,BlockState> cells,BlockPos at) {return cells.get(at)==null||cells.get(at).isAir();}
    private static boolean full(Map<BlockPos,BlockState> cells,BlockPos at) {var state=cells.get(at);return state!=null&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP);}
    private static boolean sturdy(Map<BlockPos,BlockState> cells,BlockPos at,Direction face) {var state=cells.get(at);return state!=null&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,face);}
    static List<Set<BlockPos>> upperRooms(Map<BlockPos,BlockState> cells,int width,int depth,int height) {
        List<Set<BlockPos>> result=new ArrayList<>();
        for(int y=4;y<height-1;y++) {
            Set<BlockPos> outside=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();
            queue.add(new BlockPos(-1,y+1,-1));
            while(!queue.isEmpty()) {
                BlockPos at=queue.removeFirst();
                if(at.getX()<-1||at.getX()>width||at.getZ()<-1||at.getZ()>depth||!outside.add(at)) continue;
                BlockState state=cells.get(at);
                if(state!=null&&!state.isAir()) continue;
                for(Direction d:Direction.Plane.HORIZONTAL) queue.add(at.relative(d));
            }
            Set<BlockPos> candidates=new HashSet<>();
            for(int x=0;x<width;x++) for(int z=0;z<depth;z++) {
                BlockPos feet=new BlockPos(x,y,z);BlockState floor=cells.get(feet.below());
                if(outside.contains(feet.above())||floor==null||floor.hasBlockEntity()
                        ||!floor.isFaceSturdy(EmptyBlockGetter.INSTANCE,feet.below(),Direction.UP)
                        ||!PreviewDoorwayAudit.clear(cells,feet.below(3))
                        ||!PreviewDoorwayAudit.clear(cells,feet)) continue;
                boolean roof=false;
                for(int yy=y+2;yy<=height;yy++) {
                    var at=new BlockPos(x,yy,z);var state=cells.get(at);
                    if(state!=null&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.DOWN)) {roof=true;break;}
                }
                if(roof) candidates.add(feet);
            }
            while(!candidates.isEmpty()) {
                Set<BlockPos> room=new HashSet<>();queue.add(candidates.iterator().next());
                while(!queue.isEmpty()) {
                    BlockPos at=queue.removeFirst();if(!candidates.remove(at)) continue;
                    room.add(at);for(Direction d:Direction.Plane.HORIZONTAL) queue.add(at.relative(d));
                }
                // Single shelves/eave pockets are not occupiable rooms.
                boolean square=room.stream().anyMatch(at->room.contains(at.east())&&room.contains(at.south())&&room.contains(at.east().south()));
                if(room.size()>=4&&square) result.add(Set.copyOf(room));
            }
        }
        return List.copyOf(result);
    }
}
