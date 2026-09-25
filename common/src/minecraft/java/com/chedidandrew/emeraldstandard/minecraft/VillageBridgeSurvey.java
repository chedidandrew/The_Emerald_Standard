package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import static com.chedidandrew.emeraldstandard.minecraft.VillageBridgeLedger.*;

/** A straight crossing with six dry approach rows on each shore; four columns inspected per slice. */
final class VillageBridgeSurvey {
    static final int APPROACH=6;
    private final WalkwayConnections.Request request;
    private final VillageBridges.Context context;
    private final BlockPos start;
    private final Direction direction;
    private final List<Row> rows=new ArrayList<>();
    private int column, waterY=Integer.MIN_VALUE, wet, dryAfter;
    private boolean failed, finished;
    private final BlockPos[] tops=new BlockPos[5];
    private final int[] floors=new int[5];
    private Boolean rowWater;
    Plan result, draft;
    private Iterator<Map.Entry<BlockPos,Target>> pending;
    private final List<Piece> snapshots=new ArrayList<>();
    record Row(List<BlockPos> tops,List<Integer> floors,boolean water) {}
    VillageBridgeSurvey(WalkwayConnections.Request r,VillageBridges.Context c,BlockPos start,Direction d) {
        request=r; context=c;this.start=start;direction=d;
    }
    static boolean possible(ServerLevel level,BlockPos start,Direction d) {
        BlockPos p=start.relative(d,APPROACH);
        if(!VillageBridges.loaded(level,p))return false;
        // Near-shore A* nodes are not six-row approaches. Reject them before creating a job.
        for(int row=0;row<APPROACH;row++) {
            BlockPos bank=start.relative(d,row);
            if(!VillageBridges.loaded(level,bank))return false;
            boolean dry=false;
            for(int dy=-2;dy<=2;dy++) {
                BlockPos top=bank.above(dy);
                if(VillageBridges.natural(level,top,level.getBlockState(top))
                        &&VillageBridges.clear(level.getBlockState(top.above()))
                        &&VillageBridges.clear(level.getBlockState(top.above(2)))) {dry=true;break;}
            }
            if(!dry)return false;
        }
        for(int dy=-2;dy<=2;dy++)if(water(level.getBlockState(p.above(dy)))
                &&level.getBlockState(p.above(dy+1)).isAir())return true;
        return false;
    }
    boolean advance(ServerLevel level) {
        long deadline=System.nanoTime()+1_500_000;
        for(int n=0;n<4&&!finished&&!failed;n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            inspect(level);
        }
        if(failed)return true;
        if(!finished)return false;
        if(draft==null) { draft=design();if(draft==null)return true; }
        // Snapshot/protection checks are bounded too, not one full scan on the render/server tick.
        deadline=System.nanoTime()+1_500_000;
        for(int n=0;n<24&&pending.hasNext();n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            var entry=pending.next();BlockPos p=entry.getKey();Target t=entry.getValue();
            if(!VillageBridges.loaded(level,p)||!context.mayOwn(p))return reject();
            BlockState before=level.getBlockState(p);
            if(t.state==null) {
                if(!VillageBridges.natural(level,p,before))return reject();
                t=new Target(before,5);
            }
            if(!VillageBridges.replaceable(level,p,before,t.state)
                    ||!VillageBridges.permitted(level,request,p,before,t.state,null)
                    ||t.state.isAir()&&!before.getFluidState().isEmpty())return reject();
            snapshots.add(new Piece(p.asLong(),before,t.state,t.state.isAir()&&!before.isAir()?0:t.phase));
        }
        if(pending.hasNext())return false;
        snapshots.sort(Comparator.comparingInt(Piece::phase).thenComparingInt(p->BlockPos.of(p.pos()).getY())
                .thenComparingLong(Piece::pos));
        result=new Plan(draft.id(),draft.village(),draft.project(),draft.start(),draft.end(),draft.style(),
                draft.waterLength(),draft.waterY(),draft.low(),draft.high(),draft.route(),snapshots);
        return true;
    }
    private boolean reject() { failed=true;return true; }
    private void inspect(ServerLevel level) {
        int row=rows.size(),side=column-2;
        if(row>context.maxLength()+APPROACH*2-1) { failed=true;return; }
        BlockPos ref=start.relative(direction,row).relative(direction.getClockWise(),side);
        if(!VillageBridges.loaded(level,ref)||!context.mayOwn(ref)) {failed=true;return;}
        BlockPos top=null;boolean isWater=false;
        int referenceY=waterY==Integer.MIN_VALUE?start.getY():waterY;
        for(int dy:new int[]{0,1,-1,2,-2}) {
            BlockPos p=new BlockPos(ref.getX(),referenceY+dy,ref.getZ());
            BlockState s=level.getBlockState(p);
            if((water(s)||VillageBridges.natural(level,p,s))
                    &&VillageBridges.clear(level.getBlockState(p.above()))
                    &&VillageBridges.clear(level.getBlockState(p.above(2)))) {
                top=p;isWater=water(s);break;
            }
        }
        if(top==null||(row<APPROACH&&isWater)||(row==APPROACH&&!isWater)
                ||(dryAfter>0&&isWater)||(rowWater!=null&&rowWater!=isWater)) {failed=true;return;}
        if(isWater) {
            if(waterY==Integer.MIN_VALUE)waterY=top.getY();
            if(top.getY()!=waterY||wet>=context.maxLength()) {failed=true;return;}
        } else if(Math.abs(top.getY()-start.getY())>2) {failed=true;return;}
        rowWater=isWater;tops[column]=top;floors[column]=Integer.MIN_VALUE;
        // Only natural, undisturbed riverbed can carry a pier. Deep columns can remain open spans.
        if(isWater&&(column==0||column==4)) {
            for(int depth=1;depth<=context.maxDepth();depth++) {
                BlockPos p=top.below(depth);BlockState s=level.getBlockState(p);
                if(water(s))continue;
                if(VillageBridges.natural(level,p,s)&&context.mayOwn(p)
                        &&VillageBridges.permitted(level,request,p,s,s,null))floors[column]=p.getY();
                break;
            }
        }
        if(!VillageBridges.permitted(level,request,top,level.getBlockState(top),level.getBlockState(top),null)) {
            failed=true;return;
        }
        if(++column==5) {
            rows.add(new Row(List.of(tops.clone()),Arrays.stream(floors).boxed().toList(),isWater));
            if(isWater)wet++;else if(wet>0)dryAfter++;
            column=0;rowWater=null;
            if(dryAfter==APPROACH) {
                finished=true;
                if(wet<2)failed=true;
            }
        }
    }
    private Plan design() {
        if(failed)return null;
        int last=rows.size()-1,beginY=rows.getFirst().tops.get(2).getY(),endY=rows.getLast().tops.get(2).getY();
        int deck=Math.max(waterY+3,Math.max(beginY,endY));
        if(deck-beginY>4||deck-endY>4)return null;
        var m=context.materials();
        boolean stone=request.desert()||context.masonry();
        String style=request.desert()?"sandstone arcade":stone?"stone arch":"timber trestle";
        Block floor=stone?m.foundation():m.floor();
        Block stairs=stone?m.entryStairs():m.roofStairs();
        LinkedHashMap<BlockPos,Target> targets=new LinkedHashMap<>();
        List<Long> route=new ArrayList<>();
        Set<BlockPos> anchors=new LinkedHashSet<>();
        int middle=APPROACH+(wet-1)/2;
        Set<Integer> lamps=new HashSet<>();
        lamps.add(APPROACH-2);lamps.add(last-APPROACH+2);
        for(int i=APPROACH+8;i<last-APPROACH;i+=12)lamps.add(i);
        for(int i=0;i<=last;i++) {
            Row row=rows.get(i);
            int height=Math.min(deck,Math.min(beginY+i,endY+last-i));
            int previous=i==0?height:Math.min(deck,Math.min(beginY+i-1,endY+last-i+1));
            int next=i==last?height:Math.min(deck,Math.min(beginY+i+1,endY+last-i-1));
            // The stair's high half points uphill. Flat landings join the raised main span.
            Direction uphill=height>previous?direction:height>next?direction.getOpposite():null;
            boolean stair=uphill!=null;
            BlockPos center=start.relative(direction,i).atY(height);
            route.add(center.asLong());
            boolean channel=Math.abs(i-middle)<=2;
            boolean pier=row.water&&(i==APPROACH||i==APPROACH+wet-1||(i-APPROACH)%8==0)&&!channel;
            if(pier&&(row.floors.get(0)==Integer.MIN_VALUE||row.floors.get(4)==Integer.MIN_VALUE))return null;
            for(int side=-2;side<=2;side++) {
                BlockPos surface=center.relative(direction.getClockWise(),side);
                BlockPos top=row.tops.get(side+2);
                if(!row.water) {
                    anchors.add(top);anchors.add(top.below());
                    if(top.getY()>height)return null; // Never excavate a bank, tree root or player's terrace.
                    for(int y=top.getY()+1;y<height;y++)put(targets,surface.atY(y),m.foundation().defaultBlockState(),1);
                } else {
                    if(pier&&Math.abs(side)==2) {
                        anchors.add(surface.atY(row.floors.get(side+2)));
                        for(int y=row.floors.get(side+2)+1;y<deck-1;y++) {
                            BlockState support=m.fence().defaultBlockState();
                            if(support.hasProperty(BlockStateProperties.WATERLOGGED))
                                support=support.setValue(BlockStateProperties.WATERLOGGED,y<=waterY);
                            put(targets,surface.atY(y),support,1);
                        }
                    }
                    if(!channel&&(Math.abs(side)==2||pier)) {
                        BlockState beam=stone?m.foundation().defaultBlockState():m.timber().defaultBlockState();
                        if(beam.hasProperty(RotatedPillarBlock.AXIS))beam=beam.setValue(RotatedPillarBlock.AXIS,
                                pier?direction.getClockWise().getAxis():direction.getAxis());
                        put(targets,surface.below(),beam,1);
                        // Shallow corbels turn the masonry bays into modest segmented arches.
                        if(stone&&Math.abs(side)==2&&((i-APPROACH)%8==1||(i-APPROACH)%8==7))
                            put(targets,surface.below(2),stairs.defaultBlockState()
                                    .setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,
                                            (i-APPROACH)%8==1?direction.getOpposite():direction),1);
                    }
                }
                BlockState deckState=stair&&Math.abs(side)<=1
                        ? stairs.defaultBlockState().setValue(StairBlock.FACING,uphill)
                        : floor.defaultBlockState();
                put(targets,surface,deckState,2);
                if(Math.abs(side)<=1) {
                    put(targets,surface.above(),Blocks.AIR.defaultBlockState(),5);
                    put(targets,surface.above(2),Blocks.AIR.defaultBlockState(),5);
                } else if(lamps.contains(i)&&side==(((i/4)&1)==0?-2:2)) {
                    Direction inward=side<0?direction.getClockWise():direction.getCounterClockWise();
                    for(var cell:AuthoredVillageStructures.walkwayLamp(m,inward)) {
                        // Yard lamps include a sideways ground dressing; bridge parapets own that
                        // strip. Keep the same post, knee, arm, chain and lantern without the pad.
                        if(cell.x()*direction.getStepX()+cell.z()*direction.getStepZ()!=0)continue;
                        put(targets,surface.above().offset(cell.x(),cell.y(),cell.z()),cell.state(),
                                cell.state().is(Blocks.LANTERN)?5:cell.state().is(Blocks.IRON_CHAIN)?4:3);
                    }
                } else {
                    boolean post=i%4==0||i==last;
                    put(targets,surface.above(),post?m.timber().defaultBlockState():m.fence().defaultBlockState(),3);
                    if(post)put(targets,surface.above(2),m.roofSlab().defaultBlockState(),3);
                }
            }
        }
        // Reserved navigation opening: five blocks wide and two full air blocks above the water.
        for(int i=Math.max(APPROACH,middle-2);i<=Math.min(APPROACH+wet-1,middle+2);i++)
            for(int side=-2;side<=2;side++)for(int y=waterY+1;y<deck;y++)
                put(targets,start.relative(direction,i).relative(direction.getClockWise(),side).atY(y),Blocks.AIR.defaultBlockState(),5);
        for(BlockPos anchor:anchors)targets.putIfAbsent(anchor,new Target(null,5));
        if(targets.isEmpty()||targets.size()>MAX_PIECES)return null;
        pending=targets.entrySet().iterator();
        int minX=targets.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX=targets.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ=targets.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ=targets.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        int minY=targets.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maxY=targets.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
        long a=route.getFirst(),b=route.getLast();
        String id=UUID.nameUUIDFromBytes((request.village()+"/bridge/"+Math.min(a,b)+"/"+Math.max(a,b))
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new Plan(id,request.village().toString(),request.project(),a,b,style,wet,waterY,
                new BlockPos(minX,minY,minZ).asLong(),new BlockPos(maxX,maxY,maxZ).asLong(),route,List.of());
    }
    private record Target(BlockState state,int phase) {}
    private static void put(Map<BlockPos,Target> targets,BlockPos p,BlockState s,int phase) {
        Target old=targets.get(p);
        // Headroom reservations must not overwrite a deliberate lamp that hangs three blocks up.
        if(old==null)targets.put(p,new Target(s,phase));
        else if(!old.state.equals(s))throw new IllegalStateException("Bridge design overlap at "+p+": "+old+" / "+s);
    }
    static boolean water(BlockState s) {
        return s.is(Blocks.WATER)&&s.getFluidState().is(FluidTags.WATER)&&s.getFluidState().isSource();
    }
}
