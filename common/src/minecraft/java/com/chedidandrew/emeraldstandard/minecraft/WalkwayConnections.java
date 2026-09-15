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
            List<Long> banks,boolean desert,String style) {
        Request(UUID village,long project,long origin,BlockPos start,BlockPos destination,
                boolean streetGoal,Set<Long> oldColumns,List<EconomyService.VillageProjectLot> lots,
                List<Long> banks,boolean desert) {
            this(village,project,origin,start,destination,streetGoal,oldColumns,lots,banks,desert,"");
        }
        Request withStyle(String style) {
            return new Request(village,project,origin,start,destination,streetGoal,oldColumns,lots,banks,desert,style);
        }
        String key() { return WalkwayConnectionLedger.key(village,project,origin); }
    }
    private record Node(BlockPos pos,int cost,int score) {}
    private record Crossing(BlockPos from,int cost,VillageBridgeSurvey survey) {}
    private static final Map<ServerLevel,Map<String,Search>> SEARCHES=new WeakHashMap<>();
    private static final class Search {
        final Request request;
        final VillageBridges.Context bridgeContext;
        final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::score)
                .thenComparingInt(Node::cost).thenComparingLong(n->n.pos().asLong()));
        final Map<BlockPos,Integer> costs=new HashMap<>();
        final Map<BlockPos,BlockPos> parents=new HashMap<>();
        final Map<BlockPos,VillageBridgeLedger.Plan> arrivals=new HashMap<>();
        final Map<BlockPos,VillageBridgeLedger.Plan> bridgeAt=new HashMap<>();
        final ArrayDeque<Crossing> crossings=new ArrayDeque<>();
        final List<VillageBridgeLedger.Plan> surveyed=new ArrayList<>();
        int bridgeAttempts;
        boolean sawUnloaded;
        int expanded, freezeCursor;
        long lastUsed;
        List<BlockPos> route;
        final List<Step> plan=new ArrayList<>();
        final Set<Long> columns=new HashSet<>();
        final List<BlockPos> streetTargets=new ArrayList<>();
        int targetCursor;
        Search(Request r,BlockPos start,VillageBridges.Context bridges) {
            bridgeContext=bridges;
            request=r; costs.put(start,0); open.add(new Node(start,0,heuristic(r,start)));
        }
    }
    static boolean due(ServerLevel level,long tick) {
        var ledger=get(level);
        int interval = EmeraldConfig.current().forcedVillageDevelopment() ? 2 : 20;
        return ledger.lastTick==Long.MIN_VALUE || tick<ledger.lastTick || tick-ledger.lastTick>=interval;
    }
    static boolean acquire(ServerLevel level,long tick) {
        if(!due(level,tick))return false;
        var ledger=get(level);
        ledger.lastTick=tick; return true;
    }
    static long column(BlockPos p) { return new BlockPos(p.getX(),0,p.getZ()).asLong(); }
    /** Resume admitted searches before opening more. Pending history must not dilute active turns. */
    static List<String> runnable(ServerLevel level, List<String> keys, long tick) {
        prune(level, tick);
        keys=keys.stream().filter(k->tick>=get(level).nextReview.getOrDefault(k,Long.MIN_VALUE)).toList();
        var searches = SEARCHES.getOrDefault(level, Map.of());
        var active = keys.stream().filter(k -> searches.containsKey(k) || !get(level).job(k).plan().isEmpty()).toList();
        if (!active.isEmpty()) return active;
        if (searches.size() < 8) return keys;
        DebugWork.count("walkway.waitingForSearchSlot");
        return List.of();
    }
    private static void prune(ServerLevel level, long tick) {
        var searches = SEARCHES.get(level);
        if (searches == null) return;
        int range = EmeraldConfig.current().villageDevelopmentRadius() + MAX_LENGTH;
        searches.entrySet().removeIf(e -> {
            Search s = e.getValue();
            boolean remove = tick < s.lastUsed || tick - s.lastUsed > 1200
                    && level.players().stream().noneMatch(p -> p.blockPosition().distSqr(s.request.start()) <= (double)range*range
                            || p.blockPosition().distSqr(s.request.destination()) <= (double)range*range);
            if (remove) DebugWork.count("walkway.inactiveSearchEvicted");
            return remove;
        });
    }
    static int advance(ServerLevel level,Request r,long tick,int allowance) {
        return advance(level,r,tick,allowance,null);
    }
    static int advance(ServerLevel level,Request r,long tick,int allowance,VillageBridges.Context bridges) {
        try (var ignored = DebugWork.scope("walkway")) {
            DebugWork.job("walkway:"+r.key(),tick,allowance <= 0 ? "shared_budget_exhausted" : "selected",false);
            int changed = advanceMeasured(level,r,tick,allowance,bridges);
            if(changed>0) DebugWork.job("walkway:"+r.key(),tick,"placed_blocks",true);
            return changed;
        }
    }
    private static int advanceMeasured(ServerLevel level,Request r,long tick,int allowance,VillageBridges.Context bridges) {
        if(allowance<=0)return 0;
        var ledger=get(level); Job job=ledger.job(r.key());
        r=r.withStyle(ledger.freezeStyle(r.key(),r.style(),r.desert()));
        if(job.done()||tick<job.retry())return 0;
        if (ledger.failures(r.key()) >= 3) {
            if (tick < ledger.nextReview.getOrDefault(r.key(),Long.MIN_VALUE)) return 0;
            ledger.nextReview.put(r.key(),tick+100);
            if (terrainStamp(level,r)==ledger.attempts.get(r.key()).terrain()) return 0;
            ledger.attempts.remove(r.key()); ledger.setDirty();
            DebugWork.count("walkway.deferredWorldChanged");
        }
        if(!job.plan().isEmpty())return pave(level,r,job,tick,allowance,bridges);
        var searches=SEARCHES.computeIfAbsent(level,k->new LinkedHashMap<>());
        prune(level, tick);
        Search search=searches.get(r.key());
        if(search!=null && (!search.request.destination().equals(r.destination())
                || !search.request.start().equals(r.start()) || !Objects.equals(search.bridgeContext,bridges))) {
            DebugWork.count("walkway.requestRestart");
            searches.remove(r.key()); search=null;
        }
        if(search==null) {
            if(searches.size()>=8) { DebugWork.count("walkway.waitingForSearchSlot"); return 0; }
            if(Math.abs((long)r.start().getX()-r.destination().getX())
                    +Math.abs((long)r.start().getZ()-r.destination().getZ())>384) {
                failed(level,r,job,tick,"Connection exceeds the bounded 384-block survey; add a nearer district connection");
                return 0;
            }
            BlockPos start=surface(level,r,r.start(),job);
            if(start==null) {
                failed(level,r,job,tick,"Entrance ground or clearance is blocked/unloaded at "+r.start().toShortString());
                return 0;
            }
            search=new Search(r,start,bridges); searches.put(r.key(),search);
            // Later attempts can join a verified, already-connected route instead of insisting
            // on the original destination. Never use this site's own unfinished stub as a goal.
            if (ledger.failures(r.key()) > 0) {
                String prefix=r.village()+"/";
                for (var entry:ledger.jobs.entrySet()) {
                    if (search.streetTargets.size()>=64) break;
                    Job other=entry.getValue();
                    if (!entry.getKey().startsWith(prefix)||entry.getKey().equals(r.key())||!other.done()
                            ||other.centers()<1||other.centers()>other.plan().size()) continue;
                    for (int end:new int[]{0,other.centers()-1}) {
                        BlockPos target=BlockPos.of(other.plan().get(end).pos());
                        if (within(r,target)&&loaded(level,target)&&surface(level,r,target,job)!=null)
                            search.streetTargets.add(target);
                    }
                }
            }
            DebugWork.count("walkway.searchStarted");
        }
        search.lastUsed=tick;
        if(r.streetGoal() && search.targetCursor<49*49) {
            surveyStreetTargets(level,r,job,search);
            if(search.targetCursor>=49*49) {
                if(search.streetTargets.isEmpty()) {
                    searches.remove(r.key());
                    failed(level,r,job,tick,"No loaded village road found near the district center");
                } else {
                    BlockPos start=search.open.peek().pos();
                    search.open.clear();
                    search.open.add(new Node(start,0,heuristic(search,r,start)));
                }
            }
            return 0;
        }
        if(search.route!=null) {
            if(freeze(level,r,job,search,tick,bridges))searches.remove(r.key());
            return 0;
        }
        if(!search.crossings.isEmpty()) {
            Crossing crossing=search.crossings.getFirst();
            if(crossing.survey().advance(level)) {
                search.crossings.removeFirst();
                var plan=crossing.survey().result;
                if(plan!=null&&!VillageBridgeLedger.get(level).nearby(plan)) {
                    search.surveyed.add(plan);
                    bridgeEdge(search,r,crossing.from(),crossing.cost(),plan,false);
                }
            }
            return 0;
        }
        long deadline=System.nanoTime()+2_000_000L;
        for(int n=0;n<SLICE&&!search.open.isEmpty()&&search.expanded<NODE_LIMIT;n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            Node next=search.open.remove();
            if(next.cost()!=search.costs.getOrDefault(next.pos(),Integer.MAX_VALUE))continue;
            search.expanded++;
            if(goal(level,r,next.pos(),job)||search.streetTargets.contains(next.pos())) {
                List<BlockPos> route=new ArrayList<>();
                for(BlockPos p=next.pos();p!=null&&route.size()<=MAX_LENGTH;p=search.parents.get(p)) {
                    route.add(p);
                    var bridge=search.arrivals.get(p);
                    if(bridge!=null) {
                        List<Long> span=new ArrayList<>(bridge.route());
                        if(span.getLast()!=p.asLong())Collections.reverse(span);
                        search.bridgeAt.put(p,bridge);
                        for(int i=span.size()-2;i>0;i--) {
                            BlockPos part=BlockPos.of(span.get(i));route.add(part);search.bridgeAt.put(part,bridge);
                        }
                        search.bridgeAt.put(BlockPos.of(span.getFirst()),bridge);
                    }
                }
                Collections.reverse(route);
                if(route.size()>MAX_LENGTH) {
                    searches.remove(r.key());
                    failed(level,r,job,tick,"Safe route exceeds 512 cells; waiting for a nearer connection");return 0;
                }
                search.route=List.copyOf(route);
                return 0;
            }
            if(bridges!=null) {
                for(var shared:VillageBridgeLedger.get(level).atEnd(r.village(),next.pos()))
                    bridgeEdge(search,r,next.pos(),next.cost(),shared.plan(),true);
                if(search.bridgeAttempts<24&&VillageBridgeLedger.get(level).capacity(bridges.concurrent())) {
                    for(Direction direction:Direction.Plane.HORIZONTAL)
                        if(search.bridgeAttempts<24&&usefulSurvey(search,next.pos(),direction)
                                &&VillageBridgeSurvey.possible(level,next.pos(),direction)) {
                            search.bridgeAttempts++;
                            search.crossings.add(new Crossing(next.pos(),next.cost(),
                                    new VillageBridgeSurvey(r,bridges,next.pos(),direction)));
                        }
                }
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
                search.arrivals.remove(p);
                search.open.add(new Node(p,cost,cost+heuristic(search,r,p)));
            }
            if(!search.crossings.isEmpty())break;
        }
        if(search.crossings.isEmpty()&&(search.open.isEmpty()||search.expanded>=NODE_LIMIT)) {
            searches.remove(r.key());
            failed(level,r,job,tick,search.sawUnloaded
                    ? "No loaded connection yet; load the route toward the village"
                    : "No safe connected route in survey bounds; trees, terrain, water or protected land block it");
        } else ledger.put(r.key(),new Job(List.of(),0,0,job.supplied(),false,0,
                "Surveying safe connection: "+search.expanded+" / "+NODE_LIMIT+" nodes"));
        return 0;
    }
    private static void failed(ServerLevel level,Request r,Job job,long tick,String reason) {
        var ledger=get(level);
        int failures=Math.min(3,ledger.failures(r.key())+1);
        ledger.attempts.put(r.key(),new Attempts(failures,terrainStamp(level,r)));
        ledger.put(r.key(),job.retry(tick,failures>=3
                ? "Route deferred after three unsuccessful surveys; waiting for changed terrain or a new connection. "+reason
                : reason));
        DebugWork.count(failures>=3 ? "walkway.deferred" : "walkway.failedSurvey");
    }
    /** Chunk revisions, load identity and connection topology only; no block scans or chunk loads. */
    private static long terrainStamp(ServerLevel level,Request r) {
        long hash=Objects.hash(r.start(),r.destination(),r.lots(),r.banks(),r.oldColumns());
        // Invalid distant requests also defer, but must not produce an unbounded rectangle.
        int endX=r.start().getX()+Math.clamp((long)r.destination().getX()-r.start().getX(),-384,384);
        int endZ=r.start().getZ()+Math.clamp((long)r.destination().getZ()-r.start().getZ(),-384,384);
        for(int x=(Math.min(r.start().getX(),endX)-24)>>4;
                x<=(Math.max(r.start().getX(),endX)+24)>>4;x++)
            for(int z=(Math.min(r.start().getZ(),endZ)-24)>>4;
                    z<=(Math.max(r.start().getZ(),endZ)+24)>>4;z++) {
                var chunk=level.getChunkSource().getChunkNow(x,z);
                hash=31*hash+System.identityHashCode(chunk);
                hash=31*hash+(chunk instanceof SurveyChunkRevision revision ? revision.emeraldSurveyRevision() : 0);
            }
        for(var entry:get(level).jobs.entrySet())
            if(entry.getKey().startsWith(r.village()+"/")&&entry.getValue().done()) hash=31*hash+entry.getKey().hashCode();
        return hash;
    }
    private static boolean usefulSurvey(Search search,BlockPos start,Direction direction) {
        BlockPos water=start.relative(direction,VillageBridgeSurvey.APPROACH);
        int alternatives=0;
        for(var p:search.surveyed) {
            BlockPos a=BlockPos.of(p.route().get(VillageBridgeSurvey.APPROACH));
            BlockPos b=BlockPos.of(p.route().get(VillageBridgeSurvey.APPROACH+p.waterLength()-1));
            boolean east=a.getX()!=b.getX();
            if(east!=(direction.getAxis()==Direction.Axis.X))continue;
            int along=east?water.getX():water.getZ(),a0=east?a.getX():a.getZ(),a1=east?b.getX():b.getZ();
            int lateral=Math.abs(east?water.getZ()-a.getZ():water.getX()-a.getX());
            if(along<Math.min(a0,a1)-4||along>Math.max(a0,a1)+4||lateral>24)continue;
            if(lateral<8||++alternatives>=2)return false;
        }
        return true;
    }
    private static void bridgeEdge(Search search,Request r,BlockPos from,int fromCost,
            VillageBridgeLedger.Plan plan,boolean shared) {
        BlockPos end=BlockPos.of(plan.start()==from.asLong()?plan.end():plan.start());
        if(!within(r,end))return;
        int cost=fromCost+(plan.route().size()-1)*(shared?10:18)+(shared?0:80);
        if(cost>=search.costs.getOrDefault(end,Integer.MAX_VALUE)||search.costs.size()>=NODE_LIMIT)return;
        search.costs.put(end,cost);search.parents.put(end,from);search.arrivals.put(end,plan);
        search.open.add(new Node(end,cost,cost+heuristic(search,r,end)));
    }
    /** Find real target roads before spending A* nodes on empty ground near a village center.
     * Only 32 columns per pulse; the node/memory cap and route bounds stay unchanged. */
    private static void surveyStreetTargets(ServerLevel level,Request r,Job job,Search search) {
        long deadline=System.nanoTime()+2_000_000L;
        for(int n=0;n<32&&search.targetCursor<49*49;n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            int i=search.targetCursor++;
            BlockPos column=r.destination().offset(i%49-24,0,i/49-24);
            if(!loaded(level,column)) {search.sawUnloaded=true;continue;}
            int top=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    column.getX(),column.getZ())-1;
            BlockPos surface=new BlockPos(column.getX(),top,column.getZ());
            if(goal(level,r,surface,job)&&clear(level.getBlockState(surface.above()))
                    &&clear(level.getBlockState(surface.above(2)))) search.streetTargets.add(surface);
            else {
                // A canopy/porch can hide a clear road from the heightmap.
                for(int dy=-16;dy<=8;dy++) {
                    BlockPos p=column.above(dy);
                    if(p.getY()<level.getMinY()||p.getY()>level.getMaxY()-3)continue;
                    if(goal(level,r,p,job)&&clear(level.getBlockState(p.above()))
                            &&clear(level.getBlockState(p.above(2)))) {search.streetTargets.add(p);break;}
                }
            }
        }
        get(level).put(r.key(),new Job(List.of(),0,0,job.supplied(),false,0,
                "Surveying village roads: "+search.targetCursor+" / 2401 columns"));
    }
    private static int heuristic(Search search,Request r,BlockPos p) {
        if(search.streetTargets.isEmpty())return heuristic(r,p);
        int distance=Integer.MAX_VALUE;
        for(BlockPos target:search.streetTargets)
            distance=Math.min(distance,Math.abs(p.getX()-target.getX())+Math.abs(p.getZ()-target.getZ()));
        return 10*distance;
    }
    private static int heuristic(Request r,BlockPos p) {
        int dx=Math.abs(p.getX()-r.destination().getX()), dz=Math.abs(p.getZ()-r.destination().getZ());
        // Distance to the actual 49x49 goal square, not a much larger Manhattan diamond.
        // This remains a lower bound and saves scarce survey nodes on distant Bank routes.
        return 10*(r.streetGoal()?Math.max(0,dx-24)+Math.max(0,dz-24):dx+dz);
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
            if(VillageBridges.walkable(level,r.village(),p))return p;
            boolean rail=retainedRail(level,r,p);
            // Existing clear paving can be walked through a completed lot without changing a cell.
            // No new road, plant clearance or furniture is allowed inside the excluded footprint.
            if(WalkwayLighting.excluded(p,r.lots(),r.banks()) && (!existingSurface(level,r,p,ground)
                    ||(!rail&&!level.getBlockState(p.above()).isAir())||!level.getBlockState(p.above(2)).isAir()))continue;
            if(!existingSurface(level,r,p,ground)&&!VillageProsperityManager.isPaveableTrailGround(ground))continue;
            if(job.supplied().contains(p.asLong())&&!road(level,p,ground))continue; // Never regenerate this pass's removed paving.
            if(!ground.getFluidState().isEmpty()||level.getBlockEntity(p)!=null
                    ||(!existingSurface(level,r,p,ground)
                        &&!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)))continue;
            if((!rail&&!clear(level.getBlockState(p.above())))||!clear(level.getBlockState(p.above(2))))continue;
            if(!safeChange(level,r,p,ground,existingSurface(level,r,p,ground)?ground:surfaceState(r,p,false)))continue;
            boolean permitted=true;
            for(int y=1;y<=2;y++) {
                BlockPos air=p.above(y); BlockState s=level.getBlockState(air);
                if(!s.isAir()&&!safeChange(level,r,air,s,rail&&y==1?s:Blocks.AIR.defaultBlockState()))permitted=false;
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
    private static boolean freeze(ServerLevel level,Request r,Job job,Search search,long tick,VillageBridges.Context bridges) {
        List<BlockPos> route=search.route;
        long deadline=System.nanoTime()+2_000_000L;
        int total=route.size()+Math.max(0,route.size()-1);
        for(int n=0;n<32&&search.freezeCursor<total;n++) {
            if(n>0&&System.nanoTime()>=deadline)break;
            int i=search.freezeCursor++;
            if(i<route.size()) {
                BlockPos p=route.get(i);
                var bridge=search.bridgeAt.get(p);
                if(bridge==null&&VillageBridges.walkable(level,r.village(),p)) {
                    bridge=VillageBridgeLedger.get(level).jobs.get(VillageBridgeLedger.get(level).reservation(p)).plan();
                    search.bridgeAt.put(p,bridge);
                }
                if(bridge!=null) {
                    if(!loaded(level,p)) {failed(level,r,job,tick,"Bridge route chunks unloaded");return true;}
                    Step s=snapshot(level,p);
                    search.plan.add(new Step(s.pos(),s.ground(),s.lower(),s.upper(),bridge.id()));
                    search.columns.add(column(p));continue;
                }
                if(!p.equals(surface(level,r,p,job))) {
                    failed(level,r,job,tick,"Route changed during survey; replanning");return true;
                }
                search.plan.add(snapshot(level,p));search.columns.add(column(p));
            } else {
                int edge=i-route.size()+1;
                BlockPos a=route.get(edge-1),b=route.get(edge);
                if(search.bridgeAt.containsKey(a)||search.bridgeAt.containsKey(b))continue;
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
        if(finished&&!search.bridgeAt.isEmpty()) {
            if(!VillageBridges.reserveAll(level,bridges,new LinkedHashSet<>(search.bridgeAt.values()))) {
                get(level).put(r.key(),job.retry(tick,"Waiting for a shared crossing or available bridge land"));return true;
            }
        }
        get(level).put(r.key(),finished
                ? new Job(search.plan,route.size(),0,job.supplied(),false,0,
                    "Safe route found; paving "+route.size()+" connected center cells")
                : new Job(List.of(),0,0,job.supplied(),false,0,"Checking route clearance and shoulders"));
        return finished;
    }
    private static int pave(ServerLevel level,Request r,Job job,long tick,int budget,VillageBridges.Context bridges) {
        var ledger=get(level);
        for(String id:job.plan().stream().map(Step::bridge).filter(s->!s.isEmpty()).distinct().toList()) {
            var bridge=VillageBridgeLedger.get(level).jobs.get(id);
            if(bridge==null||bridge.altered()) {
                failed(level,r,job,tick,"Crossing changed; seeking another route");return 0;
            }
            if(!bridge.done())return VillageBridges.advance(level,r,bridges,id,budget);
        }
        budget=Math.min(2,budget);
        if(job.centers()<1||job.centers()>job.plan().size()||job.plan().size()>4096
                ||job.cursor()>job.plan().size()+job.centers()) {
            failed(level,r,job,tick,"Invalid saved connection plan; resurveying");return 0;
        }
        int cursor=job.cursor(),writes=0,inspected=0;
        Set<Long> supplied=new LinkedHashSet<>(job.supplied());
        while(cursor<job.plan().size()&&writes<budget&&inspected++<32) {
            Step s=job.plan().get(cursor); BlockPos p=BlockPos.of(s.pos()); boolean center=cursor<job.centers();
            if(!loaded(level,p)) {
                ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,
                        "Waiting for loaded path at "+p.toShortString()));return writes;
            }
            if(!s.bridge().isEmpty()||VillageBridges.walkable(level,r.village(),p)) {
                if(!VillageBridges.walkable(level,r.village(),p)) {
                    failed(level,r,job,tick,"Crossing changed before connection; resurveying");return writes;
                }
                cursor++;continue;
            }
            BlockState ground=level.getBlockState(p);
            boolean stable=(ground.equals(s.ground())||road(level,p,ground))
                    &&(level.getBlockState(p.above()).equals(s.lower())||level.getBlockState(p.above()).isAir())
                    &&(level.getBlockState(p.above(2)).equals(s.upper())||level.getBlockState(p.above(2)).isAir())
                    &&p.equals(surface(level,r,p,new Job(List.of(),0,0,supplied,false,0,"")));
            if(!stable) {
                if(!center) {cursor++;continue;}
                failed(level,r,new Job(List.of(),0,0,supplied,false,0,""),tick,
                        "Path changed/protected at "+p.toShortString()+"; seeking a safe detour");return writes;
            }
            boolean occupied=false;
            for(int y=2;y>=0&&writes<budget;y--) {
                BlockPos cell=p.above(y); BlockState current=level.getBlockState(cell);
                BlockState after=y>0?Blocks.AIR.defaultBlockState():surfaceState(r,p,!center);
                if((y==0&&existingSurface(level,r,p,current))||(y==1&&retainedRail(level,r,p))||current.equals(after))continue;
                if(!safeChange(level,r,cell,current,after)
                        ||!VillageConstructionOccupancy.mayChange(level,cell,current,after)) {occupied=true;break;}
                if(y==0) { supplied.add(p.asLong()); ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,"Paving")); }
                if(!level.setBlock(cell,after,y>0?18:Block.UPDATE_ALL)) {occupied=true;break;}
                if(y==0&&!road(after)) ledger.recordPaving(cell,after);
                writes++;
            }
            if(occupied) {
                ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,
                        "Waiting for clear path/footing at "+p.toShortString()));return writes;
            }
            if(existingSurface(level,r,p,level.getBlockState(p))&&(level.getBlockState(p.above()).isAir()||retainedRail(level,r,p))
                    &&level.getBlockState(p.above(2)).isAir())cursor++;
        }
        // Verification is also budgeted. Keep its progress in the same durable cursor.
        while(cursor>=job.plan().size()&&cursor<job.plan().size()+job.centers()&&inspected++<32) {
            BlockPos p=BlockPos.of(job.plan().get(cursor-job.plan().size()).pos());
            if(!loaded(level,p)) {
                ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,false,0,
                        "Waiting for loaded route verification at "+p.toShortString()));return writes;
            }
            if(!(VillageBridges.walkable(level,r.village(),p)||existingSurface(level,r,p,level.getBlockState(p)))
                    ||(!retainedRail(level,r,p)&&!clear(level.getBlockState(p.above())))
                    ||!clear(level.getBlockState(p.above(2)))) {
                failed(level,r,new Job(List.of(),0,0,supplied,false,0,""),tick,
                        "Connection changed before verification; resurveying");return writes;
            }
            cursor++;
        }
        boolean done=cursor==job.plan().size()+job.centers();
        if(done&&!connectedGoal(level,r,BlockPos.of(job.plan().get(job.centers()-1).pos()),job)) {
            failed(level,r,new Job(List.of(),0,0,supplied,false,0,""),tick,
                    "Destination changed before connection completed; resurveying");return writes;
        }
        ledger.put(r.key(),new Job(job.plan(),job.centers(),cursor,supplied,done,0,
                done?"Connected to village walkway":"Paving connection: "+Math.min(cursor,job.centers())+" / "+job.centers()));
        return writes;
    }
    private static boolean connectedGoal(ServerLevel level,Request r,BlockPos p,Job job) {
        if(goal(level,r,p,job)) return true;
        if(!loaded(level,p)||surface(level,r,p,job)==null) return false;
        for(var entry:get(level).jobs.entrySet()) {
            Job other=entry.getValue();
            if(entry.getKey().equals(r.key())||!entry.getKey().startsWith(r.village()+"/")||!other.done()
                    ||other.centers()<1||other.centers()>other.plan().size()) continue;
            if(other.plan().getFirst().pos()==p.asLong()||other.plan().get(other.centers()-1).pos()==p.asLong()) return true;
        }
        return false;
    }
    /** Mine throats carry rails over their entry paving. Cross these fixtures read-only:
     * never clear the rail, change its bearing, waive headroom, or admit an arbitrary block. */
    private static boolean retainedRail(ServerLevel level,Request r,BlockPos p) {
        BlockState ground=level.getBlockState(p),rail=level.getBlockState(p.above());
        return rail.getBlock() instanceof BaseRailBlock&&rail.getFluidState().isEmpty()
                &&level.getBlockEntity(p.above())==null&&rail.getCollisionShape(level,p.above()).isEmpty()
                &&existingSurface(level,r,p,ground)&&ground.isFaceSturdy(level,p,Direction.UP);
    }
    static BlockState surfaceState(Request r,BlockPos p,boolean shoulder) {
        if(!r.style().isEmpty()&&!r.style().equals("legacy_desert")&&!r.style().equals("legacy_temperate"))
            return WalkwayStyle.surface(r.style(),r.village(),p,shoulder);
        boolean desert=r.style().isEmpty()?r.desert():r.style().equals("legacy_desert");
        var surface=VillageMaterializationPolicy.plannedTrailSurface(desert,shoulder,
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
    static boolean road(ServerLevel level,BlockPos p,BlockState s) {
        return road(s)||get(level).matchesPaving(p,s);
    }
    private static boolean existingSurface(ServerLevel level,Request r,BlockPos p,BlockState s) {
        return get(level).matchesPaving(p,s)||existingSurface(r,p,s);
    }
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
        Search search = SEARCHES.getOrDefault(level, Map.of()).get(key);
        var report = new LinkedHashMap<String,Object>(Map.of("connected",j.done(),"centerCells",j.centers(),"cursor",j.cursor(),"reason",j.reason(),
                "activeSearch", search != null,
                "lastSurveyVisitGameTick", search == null ? -1L : search.lastUsed,
                "surveyNodes", search == null ? 0 : search.expanded,
                "targetColumnsChecked", search == null ? 0 : search.targetCursor,
                "retryAfterGameTick", j.retry(),
                "bridges",j.plan().stream().map(Step::bridge).filter(s->!s.isEmpty()).distinct()
                        .map(id->VillageBridges.report(level,id)).toList()));
        report.put("failedSurveys",get(level).failures(key));
        report.put("deferred",get(level).failures(key)>=3);
        report.put("surfaceStyle",get(level).styles.getOrDefault(key,""));
        return report;
    }
}
