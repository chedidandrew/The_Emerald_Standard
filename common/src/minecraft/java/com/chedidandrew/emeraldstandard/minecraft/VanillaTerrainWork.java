package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Additive, snapshot-checked earthworks. Never changes an imported template or its cursor. */
final class VanillaTerrainWork {
    record Survey(SitePreparationPlan plan, String failure, boolean unloaded) {}
    private static final int DROP = TerrainFoundationPlan.MAX_TERRAIN_DROP;
    private static final int MAX_WORK = 8192;
    private static final Map<SitePreparationPlan,Map<Long,BlockState>> OVERLAYS=new IdentityHashMap<>();
    static boolean suppliedOverlay(ServerLevel level,EconomyState.VillageProject project,BlockPos p,BlockState current,BlockState original) {
        if(project.vanillaPlan==null||project.vanillaTerrainPlan==null||(!original.isAir()&&!original.is(Blocks.DIRT_PATH)))return false;
        if(OVERLAYS.size()>128)OVERLAYS.clear();
        var cells=OVERLAYS.computeIfAbsent(project.vanillaTerrainPlan,plan->{
            Map<Long,BlockState> out=new HashMap<>();
            for(var c:plan.cells())out.put(c.position(),VillageTerrainFinishing.state(level,c.after()));return out;
        });
        var expected=cells.get(p.asLong());
        return expected!=null&&!expected.isAir()&&same(current,expected);
    }

    static Survey survey(ServerLevel level, UUID village, long project, BlockPos origin,
            Map<BlockPos, BlockState> authored, List<BlockPos> route, BlockState foundation,
            BlockState stair, Direction inward, Map<BlockPos, BlockState> legacyApproach,
            SitePreparationPlan preparation, boolean retrofit) {
        try {
            var work = new Planner(level, village, project, authored, legacyApproach, preparation);
            // Only actual below/at-grade bearing surfaces, not roofs, foliage or upper walls.
            Map<Long, BlockPos> feet = new LinkedHashMap<>();
            for (var e : authored.entrySet()) {
                BlockPos p = e.getKey();
                if (p.getY() > origin.getY() || !bearing(level, p, e.getValue())) continue;
                long column = BlockPos.asLong(p.getX(), 0, p.getZ());
                feet.merge(column, p, (a,b) -> a.getY() < b.getY() ? a : b);
            }
            if (feet.isEmpty() || route.size() < DROP + 2) throw new Unsafe("No verified ground contacts or entrance route");
            for (BlockPos p : feet.values()) {
                if (retrofit && !same(work.current(p), authored.get(p)))
                    throw new Unsafe("Existing building footing changed at " + p.toShortString());
                work.support(p, foundation);
            }
            // The normalized importer centers the jigsaw entrance, but it may be recessed.
            // Walk inward from the first road cell to the first actual, clear authored floor.
            BlockPos first = route.getFirst();
            List<BlockPos> doors = authored.entrySet().stream()
                    .filter(e->e.getValue().getBlock() instanceof DoorBlock
                            && e.getValue().getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                    .map(e->e.getKey().below()).sorted(Comparator.comparingInt(p->p.distManhattan(first))).limit(8).toList();
            // Start at the normalized street level. Native porch steps may lead to a
            // lower doorway; flattening everything to that door would excavate a trench.
            int floorY=!doors.isEmpty()&&doors.getFirst().getY()==origin.getY()-1
                    ? origin.getY()-1 : origin.getY();
            List<BlockPos> landing=List.of();
            for(BlockPos door:doors) {
                landing=landing(authored,first.relative(inward).atY(floorY),door,level);
                if(!landing.isEmpty())break;
            }
            if(doors.isEmpty()) landing=landing(authored,first.relative(inward).atY(floorY),null,level);
            if(landing.isEmpty()&&!doors.isEmpty()&&doors.getFirst().getY()==floorY-1) {
                floorY--;
                landing=landing(authored,first.relative(inward).atY(floorY),doors.getFirst(),level);
            }
            if(landing.isEmpty()) throw new Unsafe("No clear landing joins the authored entrance to the exterior stairs: "+first+" doors="+doors);
            for (BlockPos p:landing) {
                if(authored.containsKey(p)&&!authored.get(p).isAir()) {
                    // Retain actual porch materials, sand floors and pre-authored entrance stairs.
                } else if(work.ground(p)) work.remember(p,work.current(p));
                else { work.put(p, foundation); work.support(p, foundation); }
                // Authored ceiling slabs and native steps were checked geometrically.
                // Preserve those details, including doors and animal-pen gates.
                if(!authored.containsKey(p.above())||authored.get(p.above()).isAir())work.clear(p.above());
                if(!authored.containsKey(p.above(2))||authored.get(p.above(2)).isAir())work.clear(p.above(2));
            }
            // Floor blocks are at origin Y, hence their WALKING height is origin Y + 1.
            // Start the first stair at floor Y, not Y-1 (the old generic approach assumption).
            int steps = -1;
            for (int n=0; n<=DROP+1; n++) {
                BlockPos end=route.get(n).atY(floorY-n);
                if (work.ground(end) && work.clearable(end.above()) && work.clearable(end.above(2))) { steps=n; break; }
            }
            if (steps < 0) throw new Unsafe("Entrance descent cannot reach safe ground within the slope limit");
            BlockPos previous=first.relative(inward);
            for (int i=0; i<steps; i++) {
                BlockPos p=route.get(i).atY(floorY-i);
                Direction face=direction(previous.getX()-p.getX(), previous.getZ()-p.getZ());
                work.put(p, stair.setValue(StairBlock.FACING,face));
                work.clear(p.above()); work.clear(p.above(2)); work.support(p,foundation); previous=p;
            }
            BlockPos end=route.get(steps).atY(floorY-steps);
            direction(previous.getX()-end.getX(),previous.getZ()-end.getZ());
            work.remember(end,work.current(end)); // Persist and verify the landing's natural bearing.
            work.clear(end.above());work.clear(end.above(2));
            return new Survey(new SitePreparationPlan(work.cells()), "", false);
        } catch (Unloaded e) { return new Survey(null,"Waiting for loaded foundation/access columns",true); }
        catch (Unsafe e) { return new Survey(null,e.getMessage(),false); }
    }

    /** Short deterministic landing route: jigsaw markers need not line up with a real door. */
    private static List<BlockPos> landing(Map<BlockPos,BlockState> authored,BlockPos start,BlockPos goal,ServerLevel level) {
        int minX=Math.min(start.getX(),authored.keySet().stream().mapToInt(BlockPos::getX).min().orElse(start.getX())-1);
        int maxX=Math.max(start.getX(),authored.keySet().stream().mapToInt(BlockPos::getX).max().orElse(start.getX())+1);
        int minZ=Math.min(start.getZ(),authored.keySet().stream().mapToInt(BlockPos::getZ).min().orElse(start.getZ())-1);
        int maxZ=Math.max(start.getZ(),authored.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(start.getZ())+1);
        ArrayDeque<BlockPos> todo=new ArrayDeque<>();Map<BlockPos,BlockPos> parent=new HashMap<>();
        todo.add(start);parent.put(start,start);
        while(!todo.isEmpty()&&parent.size()<=4096) {
            var p=todo.removeFirst();
            var floor=authored.getOrDefault(p,Blocks.AIR.defaultBlockState());
            if(!floor.isAir()&&!walkFloor(level,p,floor))continue;
            if(floor.isAir()&&p.getY()!=start.getY())continue; // Never invent floating interior steps.
            if(!headroom(authored,p,level))continue;
            if(p.equals(goal)||(goal==null&&!floor.isAir())) {
                List<BlockPos> result=new ArrayList<>();
                for(var at=p;;at=parent.get(at)){result.add(at);if(at.equals(start))break;}
                Collections.reverse(result);return result.size()<=32?List.copyOf(result):List.of();
            }
            for(var d:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) for(int dy:new int[]{0,-1,1}) {
                var n=p.relative(d).above(dy);
                if(n.getX()<minX||n.getX()>maxX||n.getZ()<minZ||n.getZ()>maxZ)continue;
                var next=authored.getOrDefault(n,Blocks.AIR.defaultBlockState());
                double rise=n.getY()+surface(level,n,next)-p.getY()-surface(level,p,floor);
                if(Math.abs(rise)>1.001)continue;
                if(n.distManhattan(start)<=24&&Math.abs(n.getY()-start.getY())<=DROP&&!parent.containsKey(n)) {
                    parent.put(n,p);todo.add(n);
                }
            }
        }
        return List.of();
    }

    private static boolean walkFloor(ServerLevel level,BlockPos p,BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity()
                && !(s.getBlock() instanceof LeavesBlock) && !(s.getBlock() instanceof SnowLayerBlock)
                && !s.getCollisionShape(level,p).isEmpty() && surface(level,p,s)>=.5 && surface(level,p,s)<=1;
    }
    private static double surface(ServerLevel level,BlockPos p,BlockState s) {
        return s.isAir()?1:s.getCollisionShape(level,p).isEmpty()?0:s.getCollisionShape(level,p).max(Direction.Axis.Y);
    }
    private static boolean headroom(Map<BlockPos,BlockState> authored,BlockPos p,ServerLevel level) {
        var air=Blocks.AIR.defaultBlockState();
        double feet=surface(level,p,authored.getOrDefault(p,air));
        for(int dy=1;dy<=2;dy++) {
            var at=p.above(dy);var s=authored.getOrDefault(at,air);
            if(open(s))continue;
            var shape=s.getCollisionShape(level,at);
            // Two mirrored upside-down arch stairs leave a full-width passage across
            // their shared boundary above a half-slab floor, not two blocked cells.
            if(dy==2&&feet<=.5&&archPair(authored,at,s))continue;
            if(!s.getFluidState().isEmpty()||(!shape.isEmpty()&&dy+shape.min(Direction.Axis.Y)<feet+1.95))return false;
        }
        return true;
    }
    private static boolean archPair(Map<BlockPos,BlockState> authored,BlockPos p,BlockState s) {
        if(!(s.getBlock() instanceof StairBlock)||s.getValue(StairBlock.HALF)!=net.minecraft.world.level.block.state.properties.Half.TOP)return false;
        var facing=s.getValue(StairBlock.FACING);var neighbor=p.relative(facing.getOpposite());
        var other=authored.get(neighbor);
        var floor=authored.get(neighbor.below(2));
        return other!=null&&other.getBlock() instanceof StairBlock
                &&other.getValue(StairBlock.HALF)==net.minecraft.world.level.block.state.properties.Half.TOP
                &&other.getValue(StairBlock.FACING)==facing.getOpposite()
                &&other.getValue(StairBlock.SHAPE)==net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT
                &&s.getValue(StairBlock.SHAPE)==net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT
                &&floor!=null&&floor.getBlock() instanceof SlabBlock
                &&floor.getValue(SlabBlock.TYPE)==net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
                &&open(authored.getOrDefault(neighbor.below(),Blocks.AIR.defaultBlockState()));
    }

    // Kept out of world writes: exact cardinal adjacency is required, including route turns.
    private static Direction direction(int x,int z) {
        if (Math.abs(x)+Math.abs(z)!=1) throw new Unsafe("Entrance route is not cardinally continuous");
        return x==1?Direction.EAST:x==-1?Direction.WEST:z==1?Direction.SOUTH:Direction.NORTH;
    }
    private static boolean open(BlockState state) {
        return state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.is(Blocks.SNOW)&&state.getValue(SnowLayerBlock.LAYERS)==1 || VegetationCompatibility.open(state);
    }
    private static boolean bearing(ServerLevel level, BlockPos p, BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity()
                && !(s.getBlock() instanceof LeavesBlock)
                && s.isFaceSturdy(level,p,Direction.DOWN);
    }
    static boolean same(BlockState actual, BlockState planned) {
        if (actual.getBlock()==planned.getBlock() && actual.getBlock() instanceof StairBlock)
            return actual.setValue(StairBlock.SHAPE,planned.getValue(StairBlock.SHAPE)).equals(planned);
        return actual.equals(planned) || ((planned.is(Blocks.GRASS_BLOCK)||planned.is(Blocks.DIRT))
                && (actual.is(Blocks.GRASS_BLOCK)||actual.is(Blocks.DIRT)));
    }
    private static final class Unsafe extends RuntimeException { Unsafe(String s) { super(s); } }
    private static final class Unloaded extends RuntimeException {}

    private static final class Planner {
        final ServerLevel level; final UUID village; final long project;
        final Map<BlockPos,BlockState> authored,legacy,prepared=new HashMap<>(),desired=new LinkedHashMap<>();
        Planner(ServerLevel l,UUID v,long id,Map<BlockPos,BlockState> a,Map<BlockPos,BlockState> old,SitePreparationPlan prep) {
            level=l;village=v;project=id;authored=a;legacy=old;
            if(prep!=null) for(var c:prep.cells()) prepared.put(BlockPos.of(c.position()),VillageTerrainFinishing.state(l,c.after()));
        }
        BlockState current(BlockPos p) {
            if(p.getY()<level.getMinY()||p.getY()>level.getMaxY()) throw new Unsafe("Foundation reaches world height limit");
            if(!level.hasChunk(p.getX()>>4,p.getZ()>>4)) throw new Unloaded();
            return prepared.getOrDefault(p,level.getBlockState(p));
        }
        boolean ground(BlockPos p) {
            BlockState s=current(p);
            return s.is(Blocks.DIRT_PATH) || (VillageSitePreparation.dryNaturalGround(s)||s.is(Blocks.BEDROCK))
                    && s.isFaceSturdy(level,p,Direction.UP);
        }
        boolean clearable(BlockPos p) {
            BlockState s=current(p);
            return s.isAir() || VillageSitePreparation.clearable(level,p);
        }
        void clear(BlockPos p) { put(p,Blocks.AIR.defaultBlockState()); }
        void remember(BlockPos p,BlockState s) { desired.putIfAbsent(p,s); }
        void put(BlockPos p,BlockState after) {
            BlockState before=current(p), template=authored.get(p);
            boolean buriedPath=template!=null&&template.is(Blocks.DIRT_PATH)&&after.is(Blocks.DIRT);
            if(template!=null && !template.isAir() && !buriedPath) {
                if(after.isAir()&&open(template))return; // Doors, torches and harmless authored detail remain.
                if(!template.equals(after)) throw new Unsafe("Access would overwrite the authored building at "+p.toShortString()
                        +"; authored="+BlockStateParser.serialize(template)+"; proposed="+BlockStateParser.serialize(after));
                return;
            }
            if(!before.equals(after) && ((!clearable(p) && !before.equals(legacy.get(p))
                    && !(buriedPath&&(VillageSitePreparation.dryNaturalGround(before)||before.is(Blocks.DIRT_PATH))))
                    || before.hasBlockEntity() || !before.getFluidState().isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(level,village,project,p,before,after)))
                throw new Unsafe("Protected or occupied foundation/access at "+p.toShortString()+": "+BlockStateParser.serialize(before));
            BlockState prior=desired.putIfAbsent(p,after);
            if(prior!=null&&!prior.equals(after)) throw new Unsafe("Conflicting entrance geometry at "+p.toShortString());
            if(desired.size()>MAX_WORK) throw new Unsafe("Foundation work exceeds bounded plan size");
        }
        void support(BlockPos foot,BlockState material) {
            for(int depth=1;depth<=DROP+1;depth++) {
                BlockPos p=foot.below(depth);
                // Authored underground air is deliberate (cellar), never infill permission.
                if(authored.containsKey(p)) {
                    if(authored.get(p).is(Blocks.DIRT_PATH)) { put(p,Blocks.DIRT.defaultBlockState()); return; }
                    if(!bearing(level,p,authored.get(p))) {
                        if(!authored.containsKey(foot)||authored.get(foot).isAir())
                            throw new Unsafe("A new landing would need support inside an authored opening at "+p.toShortString());
                        return; // Native cellar spans remain intact, but new landings cannot waive support.
                    }
                    return;
                }
                if(ground(p)) { remember(p,current(p)); return; }
                if(depth>DROP) throw new Unsafe("No safe ground within foundation depth at "+foot.toShortString());
                put(p,material);
            }
        }
        List<SitePreparationPlan.Cell> cells() {
            return desired.entrySet().stream().sorted(Comparator
                    .<Map.Entry<BlockPos,BlockState>>comparingInt(e->e.getValue().isAir()?0:1)
                    .thenComparingInt(e->e.getKey().getY()).thenComparingLong(e->e.getKey().asLong()))
                    .map(e->new SitePreparationPlan.Cell(e.getKey().asLong(),BlockStateParser.serialize(current(e.getKey())),
                            BlockStateParser.serialize(e.getValue()))).toList();
        }
    }
}
