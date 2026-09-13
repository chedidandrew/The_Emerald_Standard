package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import static com.chedidandrew.emeraldstandard.minecraft.WalkwayConnectionLedger.*;

/** Loaded-only, incremental road connectivity pass; historical construction plans stay frozen. */
final class WalkwayConnections {
    static final int NODE_LIMIT=8192, SLICE=96, MAX_LENGTH=512;
    record Request(UUID village,long project,long origin,BlockPos start,BlockPos destination,
            boolean streetGoal,Set<Long> oldColumns,List<EconomyService.VillageProjectLot> lots,
            List<Long> banks,boolean desert) {
        String key() { return WalkwayConnectionLedger.key(village,project,origin); }
    }
    private record Node(BlockPos pos,int cost,int score) {}
    private static final Map<ServerLevel,Map<String,Search>> SEARCHES=new WeakHashMap<>();
    private static final class Search {
        final Request request;
        final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::score)
                .thenComparingInt(Node::cost).thenComparingLong(n->n.pos().asLong()));
        final Map<BlockPos,Integer> costs=new HashMap<>();
        final Map<BlockPos,BlockPos> parents=new HashMap<>();
        boolean sawUnloaded;
        int expanded, freezeCursor;
        long lastUsed;
        List<BlockPos> route;
        final List<Step> plan=new ArrayList<>();
        final Set<Long> columns=new HashSet<>();
        Search(Request r,BlockPos start) {
            request=r; costs.put(start,0); open.add(new Node(start,0,heuristic(r,start)));
        }
    }
    static boolean acquire(ServerLevel level,long tick) {
        var ledger=get(level);
        if(ledger.lastTick!=Long.MIN_VALUE && tick>=ledger.lastTick && tick-ledger.lastTick<20) return false;
        ledger.lastTick=tick; return true;
    }
    static long column(BlockPos p) { return new BlockPos(p.getX(),0,p.getZ()).asLong(); }
    static int advance(ServerLevel level,Request r,long tick,int allowance) {
        if(allowance<=0)return 0;
        var ledger=get(level); Job job=ledger.job(r.key());
        if(job.done()||tick<job.retry())return 0;
        if(!job.plan().isEmpty())return pave(level,r,job,tick,Math.min(2,allowance));
        var searches=SEARCHES.computeIfAbsent(level,k->new LinkedHashMap<>());
        searches.entrySet().removeIf(e -> tick < e.getValue().lastUsed || tick-e.getValue().lastUsed>1200);
        Search search=searches.get(r.key());
        if(search!=null && (!search.request.destination().equals(r.destination())
                || !search.request.start().equals(r.start()))) { searches.remove(r.key()); search=null; }
        if(search==null) {
            if(searches.size()>=8)return 0;
            if(Math.abs((long)r.start().getX()-r.destination().getX())
                    +Math.abs((long)r.start().getZ()-r.destination().getZ())>384) {
                ledger.put(r.key(),job.retry(tick,"Connection exceeds the bounded 384-block survey; add a nearer district connection"));
                return 0;
            }
            BlockPos start=surface(level,r,r.start(),job);
            if(start==null) {
                ledger.put(r.key(),job.retry(tick,"Entrance ground or clearance is blocked/unloaded at "+r.start().toShortString()));
                return 0;
            }
            search=new Search(r,start); searches.put(r.key(),search);
        }
        search.lastUsed=tick;
        if(search.route!=null) {
            if(freeze(level,r,job,search,tick))searches.remove(r.key());
            return 0;
        }
        long deadline=System.nanoTime()+2_000_000L;
        for(int n=0;n<SLICE&&!search.open.isEmpty()&&search.expanded<NODE_LIMIT;n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            Node next=search.open.remove();
            if(next.cost()!=search.costs.getOrDefault(next.pos(),Integer.MAX_VALUE))continue;
            search.expanded++;
            if(goal(level,r,next.pos(),job)) {
                List<BlockPos> route=new ArrayList<>();
                for(BlockPos p=next.pos();p!=null&&route.size()<=MAX_LENGTH;p=search.parents.get(p))route.add(p);
                Collections.reverse(route);
                if(route.size()>MAX_LENGTH) {
                    searches.remove(r.key());
                    ledger.put(r.key(),job.retry(tick,"Safe route exceeds 512 cells; waiting for a nearer connection"));return 0;
                }
                search.route=List.copyOf(route);
                return 0;
            }
            for(Direction d:Direction.Plane.HORIZONTAL) {
                BlockPos trial=next.pos().relative(d);
                if(!within(r,trial))continue;
                if(!loaded(level,trial)) { search.sawUnloaded=true;continue; }
                BlockPos p=surface(level,r,trial,job); if(p==null)continue;
                int cost=next.cost()+(road(level.getBlockState(p))?10:14)+4*Math.abs(p.getY()-next.pos().getY());
                if(cost>=search.costs.getOrDefault(p,Integer.MAX_VALUE))continue;
                // Bound discovered nodes as well as expanded nodes.
                if(!search.costs.containsKey(p)&&search.costs.size()>=NODE_LIMIT)continue;
                search.costs.put(p,cost); search.parents.put(p,next.pos());
                search.open.add(new Node(p,cost,cost+heuristic(r,p)));
            }
        }
        if(search.open.isEmpty()||search.expanded>=NODE_LIMIT) {
            searches.remove(r.key());
            ledger.put(r.key(),job.retry(tick,search.sawUnloaded
                    ? "No loaded connection yet; load the route toward the village"
                    : "No safe connected route in survey bounds; trees, terrain, water or protected land block it"));
        } else ledger.put(r.key(),new Job(List.of(),0,0,job.supplied(),false,0,
                "Surveying safe connection: "+search.expanded+" / "+NODE_LIMIT+" nodes"));
        return 0;
    }
    private static int heuristic(Request r,BlockPos p) {
        int d=Math.abs(p.getX()-r.destination().getX())+Math.abs(p.getZ()-r.destination().getZ());
        return 10*Math.max(0,d-(r.streetGoal()?48:0));
    }
    private static boolean within(Request r,BlockPos p) {
        return p.getX()>=Math.min(r.start().getX(),r.destination().getX())-24
                &&p.getX()<=Math.max(r.start().getX(),r.destination().getX())+24
                &&p.getZ()>=Math.min(r.start().getZ(),r.destination().getZ())-24
                &&p.getZ()<=Math.max(r.start().getZ(),r.destination().getZ())+24;
    }
    private static boolean goal(ServerLevel level,Request r,BlockPos p,Job job) {
        if(!r.streetGoal())return p.getX()==r.destination().getX()&&p.getZ()==r.destination().getZ()
                &&Math.abs(p.getY()-r.destination().getY())<=1;
        if(Math.abs(p.getX()-r.destination().getX())>24||Math.abs(p.getZ()-r.destination().getZ())>24
                || !level.getBlockState(p).is(Blocks.DIRT_PATH)||job.supplied().contains(p.asLong()))return false;
        // Do not mistake this house's own truncated path or its shoulders for the village network.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(r.oldColumns().contains(column(p.offset(dx,0,dz))))return false;
        return true;
    }
    static BlockPos surface(ServerLevel level,Request r,BlockPos reference,Job job) {
        for(int dy:new int[]{0,1,-1}) {
            BlockPos p=reference.above(dy);
            if(p.getY()<level.getMinY()||p.getY()>level.getMaxY()-3||!loaded(level,p))continue;
            if(!level.getWorldBorder().isWithinBounds(p)||ConstructionOwnership.reserved(level,p))continue;
            BlockState ground=level.getBlockState(p);
            // Existing clear paving can be walked through a completed lot without changing a cell.
            // No new road, plant clearance or furniture is allowed inside the excluded footprint.
            if(WalkwayLighting.excluded(p,r.lots(),r.banks()) && (!existingSurface(r,p,ground)
                    ||!level.getBlockState(p.above()).isAir()||!level.getBlockState(p.above(2)).isAir()))continue;
            if(!existingSurface(r,p,ground)&&!VillageProsperityManager.isPaveableTrailGround(ground))continue;
            if(job.supplied().contains(p.asLong())&&!road(ground))continue; // Never regenerate this pass's removed paving.
            if(!ground.getFluidState().isEmpty()||level.getBlockEntity(p)!=null
                    ||(!existingSurface(r,p,ground)
                        &&!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)))continue;
            if(!clear(level.getBlockState(p.above()))||!clear(level.getBlockState(p.above(2))))continue;
            if(!safeChange(level,r,p,ground,existingSurface(r,p,ground)?ground:surfaceState(r,p,false)))continue;
            boolean permitted=true;
            for(int y=1;y<=2;y++) {
                BlockPos air=p.above(y); BlockState s=level.getBlockState(air);
                if(!s.isAir()&&!safeChange(level,r,air,s,Blocks.AIR.defaultBlockState()))permitted=false;
            }
            if(permitted)return p;
        }
        return null;
    }
    private static boolean safeChange(ServerLevel level,Request r,BlockPos p,BlockState before,BlockState after) {
        return level.getBlockEntity(p)==null && before.getFluidState().isEmpty()
                &&!ConstructionOwnership.reserved(level,p)
                &&VillageDevelopmentProtection.mayPlace(level,r.village(),r.project(),p,before,after);
    }
    private static Step snapshot(ServerLevel level,BlockPos p) {
        return new Step(p.asLong(),level.getBlockState(p),level.getBlockState(p.above()),level.getBlockState(p.above(2)));
    }
    /** Snapshot at most 32 cells/edges per pulse; never scan the entire route on a game tick. */
    private static boolean freeze(ServerLevel level,Request r,Job job,Search search,long tick) {
        List<BlockPos> route=search.route;
        long deadline=System.nanoTime()+2_000_000L;
        int total=route.size()+Math.max(0,route.size()-1);
        for(int n=0;n<32&&search.freezeCursor<total;n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            int i=search.freezeCursor++;
            if(i<route.size()) {
                BlockPos p=route.get(i);
                if(!p.equals(surface(level,r,p,job))) {
                    get(level).put(r.key(),job.retry(tick,"Route changed during survey; replanning"));return true;
                }
                search.plan.add(snapshot(level,p));search.columns.add(column(p));
            } else {
                int edge=i-route.size()+1;
                BlockPos a=route.get(edge-1),b=route.get(edge);
                int dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ();
                // Width is best effort; preserve a narrow detour instead of an unconnected broad stub.
                for(BlockPos end:List.of(a,b))for(int side:new int[]{-1,1}) {
                    BlockPos candidate=end.offset(-dz*side,0,dx*side);
                    if(!search.columns.add(column(candidate)))continue;
                    BlockPos p=surface(level,r,candidate,job);
                    if(p!=null&&p.getY()==end.getY())search.plan.add(snapshot(level,p));
                }
            }
        }
        boolean finished=search.freezeCursor==total;
        get(level).put(r.key(),finished
                ? new Job(search.plan,route.size(),0,job.supplied(),false,0,
                    "Safe route found; paving "+route.size()+" connected center cells")
                : new Job(List.of(),0,0,job.supplied(),false,0,"Checking route clearance and shoulders"));
        return finished;
    }
    private static int pave(ServerLevel level,Request r,Job job,long tick,int budget) {
        var ledger=get(level);
        if(job.centers()<1||job.centers()>job.plan().size()||job.plan().size()>4096
                ||job.cursor()>job.plan().size()+job.centers()) {
            ledger.put(r.key(),job.retry(tick,"Invalid saved connection plan; resurveying"));return 0;
        }
        int cursor=job.cursor(),writes=0,inspected=0;
        Set<Long> supplied=new LinkedHashSet<>(job.supplied());
        while(cursor<job.plan().size()&&writes<budget&&inspected++<32) {
            Step s=job.plan().get(cursor); BlockPos p=BlockPos.of(s.pos()); boolean center=cursor<job.centers();
            if(!loaded(level,p)) {
                ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,
                        "Waiting for loaded path at "+p.toShortString()));return writes;
            }
            BlockState ground=level.getBlockState(p);
            boolean stable=(ground.equals(s.ground())||road(ground))
                    &&(level.getBlockState(p.above()).equals(s.lower())||level.getBlockState(p.above()).isAir())
                    &&(level.getBlockState(p.above(2)).equals(s.upper())||level.getBlockState(p.above(2)).isAir())
                    &&p.equals(surface(level,r,p,new Job(List.of(),0,0,supplied,false,0,"")));
            if(!stable) {
                if(!center) {cursor++;continue;}
                ledger.put(r.key(),new Job(List.of(),0,0,supplied,false,tick+100,
                        "Path changed/protected at "+p.toShortString()+"; seeking a safe detour"));return writes;
            }
            boolean occupied=false;
            for(int y=2;y>=0&&writes<budget;y--) {
                BlockPos cell=p.above(y); BlockState current=level.getBlockState(cell);
                BlockState after=y>0?Blocks.AIR.defaultBlockState():surfaceState(r,p,!center);
                if((y==0&&existingSurface(r,p,current))||current.equals(after))continue;
                if(!safeChange(level,r,cell,current,after)
                        ||!VillageConstructionOccupancy.mayChange(level,cell,current,after)) {occupied=true;break;}
                if(y==0) { supplied.add(p.asLong()); ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,"Paving")); }
                if(!level.setBlock(cell,after,y>0?18:Block.UPDATE_ALL)) {occupied=true;break;}
                writes++;
            }
            if(occupied) {
                ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,
                        "Waiting for clear path/footing at "+p.toShortString()));return writes;
            }
            if(existingSurface(r,p,level.getBlockState(p))&&level.getBlockState(p.above()).isAir()
                    &&level.getBlockState(p.above(2)).isAir())cursor++;
        }
        // Verification is also budgeted. Keep its progress in the same durable cursor.
        while(cursor>=job.plan().size()&&cursor<job.plan().size()+job.centers()&&inspected++<32) {
            BlockPos p=BlockPos.of(job.plan().get(cursor-job.plan().size()).pos());
            if(!loaded(level,p)) {
                ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,
                        "Waiting for loaded route verification at "+p.toShortString()));return writes;
            }
            if(!existingSurface(r,p,level.getBlockState(p))||!clear(level.getBlockState(p.above()))
                    ||!clear(level.getBlockState(p.above(2)))) {
                ledger.put(r.key(),new Job(List.of(),0,0,supplied,false,tick+100,
                        "Connection changed before verification; resurveying"));return writes;
            }
            cursor++;
        }
        boolean done=cursor==job.plan().size()+job.centers();
        if(done&&!goal(level,r,BlockPos.of(job.plan().get(job.centers()-1).pos()),job)) {
            ledger.put(r.key(),new Job(List.of(),0,0,supplied,false,tick+100,
                    "Destination changed before connection completed; resurveying"));return writes;
        }
        ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,done,0,
                done?"Connected to village walkway":"Paving connection: "+Math.min(cursor,job.centers())+" / "+job.centers()));
        return writes;
    }
    static BlockState surfaceState(Request r,BlockPos p,boolean shoulder) {
        var surface=VillageMaterializationPolicy.plannedTrailSurface(r.desert(),shoulder,
                VillageStructureProgression.trailDetail(r.project(),p.getX(),p.getZ()));
        return switch(surface) {
            case DIRT_PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
        };
    }
    // Authored entrances include stone steps, paving and wooden porches. They are read-only
    // access surfaces, not new road targets or permission to pave through a building.
    static boolean existingSurface(Request r,BlockPos p,BlockState s) {
        if(road(s))return true;
        int fromStart=Math.abs(p.getX()-r.start().getX())+Math.abs(p.getZ()-r.start().getZ());
        int fromEnd=Math.abs(p.getX()-r.destination().getX())+Math.abs(p.getZ()-r.destination().getZ());
        if(fromStart>8&&(r.streetGoal()||fromEnd>8))return false;
        return s.getBlock() instanceof StairBlock||s.getBlock() instanceof SlabBlock
                ||s.is(net.minecraft.tags.BlockTags.PLANKS)||s.is(net.minecraft.tags.BlockTags.STONE_BRICKS)
                ||s.is(Blocks.COBBLESTONE)||s.is(Blocks.MOSSY_COBBLESTONE)||s.is(Blocks.SANDSTONE)
                ||s.is(Blocks.SMOOTH_SANDSTONE)||s.is(Blocks.CUT_SANDSTONE)||s.is(Blocks.COBBLED_DEEPSLATE)
                ||s.is(Blocks.DEEPSLATE_BRICKS)||s.is(Blocks.BRICKS)||s.is(Blocks.STONE)||s.is(Blocks.SMOOTH_STONE);
    }
    static boolean road(BlockState s) { return s.is(Blocks.DIRT_PATH)||s.is(Blocks.GRAVEL)||s.is(Blocks.COARSE_DIRT); }
    private static boolean clear(BlockState s) {
        return s.isAir()||s.getFluidState().isEmpty()&&s.canBeReplaced()&&!s.hasBlockEntity()
                &&!(s.getBlock() instanceof LeavesBlock);
    }
    private static boolean loaded(ServerLevel level,BlockPos p) {
        for(int x=(p.getX()-1)>>4;x<=(p.getX()+1)>>4;x++)
            for(int z=(p.getZ()-1)>>4;z<=(p.getZ()+1)>>4;z++)if(!level.hasChunk(x,z))return false;
        return true;
    }
    static Map<String,Object> report(ServerLevel level,String key) {
        Job j=get(level).job(key);
        return Map.of("connected",j.done(),"centerCells",j.centers(),"cursor",j.cursor(),"reason",j.reason());
    }
}
