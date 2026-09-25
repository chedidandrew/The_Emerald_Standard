package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.*;

/** Frozen bridge plans and one-way work receipts, shared by all roads in this dimension. */
final class VillageBridgeLedger extends SavedData {
    static final int MAX_JOBS = 512, MAX_PIECES = 8192;
    record Piece(long pos, BlockState before, BlockState after, int phase) {
        static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Piece::pos),
                BlockState.CODEC.fieldOf("before").forGetter(Piece::before),
                BlockState.CODEC.fieldOf("after").forGetter(Piece::after),
                Codec.intRange(0,5).fieldOf("phase").forGetter(Piece::phase)).apply(i, Piece::new));
    }
    record Plan(String id, String village, long project, long start, long end, String style,
            int waterLength, int waterY, long low, long high, List<Long> route, List<Piece> pieces) {
        static final Codec<Plan> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Plan::id),
                Codec.STRING.fieldOf("village").forGetter(Plan::village),
                Codec.LONG.fieldOf("project").forGetter(Plan::project),
                Codec.LONG.fieldOf("start").forGetter(Plan::start),
                Codec.LONG.fieldOf("end").forGetter(Plan::end),
                Codec.STRING.fieldOf("style").forGetter(Plan::style),
                Codec.intRange(2,64).fieldOf("water_length").forGetter(Plan::waterLength),
                Codec.INT.fieldOf("water_y").forGetter(Plan::waterY),
                Codec.LONG.fieldOf("low").forGetter(Plan::low),
                Codec.LONG.fieldOf("high").forGetter(Plan::high),
                Codec.LONG.listOf(2,80).fieldOf("route").forGetter(Plan::route),
                Piece.CODEC.listOf(1,MAX_PIECES).fieldOf("pieces").forGetter(Plan::pieces)).apply(i,Plan::new));
        Plan { route=List.copyOf(route); pieces=List.copyOf(pieces); }
        UUID owner() { return UUID.fromString(village); }
        int operations() { return (int)pieces.stream().filter(p -> !p.before.equals(p.after)).count(); }
        boolean contains(BlockPos p) {
            BlockPos a=BlockPos.of(low),b=BlockPos.of(high);
            return p.getX()>=a.getX()&&p.getX()<=b.getX()&&p.getZ()>=a.getZ()&&p.getZ()<=b.getZ()
                    &&p.getY()>=a.getY()&&p.getY()<=b.getY();
        }
    }
    record Job(Plan plan, int cursor, int verify, int audit, boolean funded, String state, String reason) {
        static final Codec<Job> CODEC=RecordCodecBuilder.create(i -> i.group(
                Plan.CODEC.fieldOf("plan").forGetter(Job::plan),
                Codec.intRange(0,MAX_PIECES).fieldOf("cursor").forGetter(Job::cursor),
                Codec.intRange(0,MAX_PIECES).fieldOf("verify").forGetter(Job::verify),
                Codec.intRange(0,MAX_PIECES).fieldOf("audit").forGetter(Job::audit),
                Codec.BOOL.fieldOf("funded").forGetter(Job::funded),
                Codec.STRING.fieldOf("state").forGetter(Job::state),
                Codec.STRING.fieldOf("reason").forGetter(Job::reason)).apply(i,Job::new));
        static Job fresh(Plan p) { return new Job(p,0,0,0,false,"surveyed","Surveyed crossing"); }
        boolean done() { return state.equals("complete"); }
        boolean altered() { return state.equals("altered"); }
        Job status(String state,String reason) { return new Job(plan,cursor,verify,audit,funded,state,reason); }
    }
    static final Codec<VillageBridgeLedger> CODEC = Codec.unboundedMap(Codec.STRING,Job.CODEC)
            .xmap(VillageBridgeLedger::new, l -> l.jobs);
    static final SavedDataType<VillageBridgeLedger> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard","village_bridges"),
            VillageBridgeLedger::new,CODEC,DataFixTypes.LEVEL);
    final Map<String,Job> jobs=new LinkedHashMap<>();
    private final Map<Long,Set<String>> chunks=new HashMap<>();
    private final Map<Long,Set<String>> ends=new HashMap<>();
    private final Map<String,Map<Long,Piece>> pieces=new HashMap<>();
    VillageBridgeLedger() {}
    VillageBridgeLedger(Map<String,Job> saved) {
        if(saved.size()>MAX_JOBS)throw new IllegalArgumentException("Too many bridges");
        saved.forEach((id,j) -> {
            UUID.fromString(id); UUID.fromString(j.plan.village);
            if(!id.equals(j.plan.id)||j.cursor>j.plan.pieces.size()||j.verify>j.plan.pieces.size())
                throw new IllegalArgumentException("Invalid bridge receipt");
            if(j.audit>j.plan.pieces.size()||j.plan.route.size()!=j.plan.waterLength+12
                    ||j.plan.start!=j.plan.route.getFirst()||j.plan.end!=j.plan.route.getLast())
                throw new IllegalArgumentException("Invalid bridge route");
            for(int i=1;i<j.plan.route.size();i++) {
                BlockPos a=BlockPos.of(j.plan.route.get(i-1)),b=BlockPos.of(j.plan.route.get(i));
                if(Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())!=1||Math.abs(a.getY()-b.getY())>1)
                    throw new IllegalArgumentException("Disconnected bridge route");
            }
            jobs.put(id,j); index(j.plan);
        });
    }
    static VillageBridgeLedger get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    void put(Job job) {
        if(!jobs.containsKey(job.plan.id))index(job.plan);
        jobs.put(job.plan.id,job);setDirty();
    }
    private void index(Plan p) {
        BlockPos a=BlockPos.of(p.low),b=BlockPos.of(p.high);
        if(a.getX()>b.getX()||a.getY()>b.getY()||a.getZ()>b.getZ()
                ||(long)(b.getX()-a.getX()+1)*(b.getZ()-a.getZ()+1)>640)
            throw new IllegalArgumentException("Oversized bridge reservation");
        for(int x=a.getX()>>4;x<=b.getX()>>4;x++)for(int z=a.getZ()>>4;z<=b.getZ()>>4;z++)
            chunks.computeIfAbsent(chunk(x,z),k->new LinkedHashSet<>()).add(p.id);
        for(long end:List.of(p.start,p.end))ends.computeIfAbsent(end,k->new LinkedHashSet<>()).add(p.id);
        Map<Long,Piece> byPos=new HashMap<>();
        for(Piece cell:p.pieces)if(!p.contains(BlockPos.of(cell.pos))||byPos.put(cell.pos,cell)!=null)
            throw new IllegalArgumentException("Invalid bridge piece");
        pieces.put(p.id,byPos);
    }
    private static long chunk(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    String reservation(BlockPos p) {
        for(String id:chunks.getOrDefault(chunk(p.getX()>>4,p.getZ()>>4),Set.of()))
            if(jobs.get(id).plan.contains(p))return id;
        return null;
    }
    Piece piece(String id,long pos) { return pieces.getOrDefault(id,Map.of()).get(pos); }
    List<Job> atEnd(UUID village, BlockPos p) {
        return ends.getOrDefault(p.asLong(),Set.of()).stream().map(jobs::get)
                .filter(j->j.plan.village.equals(village.toString())&&!j.altered()).toList();
    }
    boolean capacity(int concurrent) { return jobs.size()<MAX_JOBS; }
    boolean crewAvailable(int concurrent) {
        return jobs.values().stream().filter(j->j.funded()&&!j.done()&&!j.altered()).count()<concurrent;
    }
    boolean nearby(Plan p) {
        return jobs.values().stream().anyMatch(j -> parallel(p,j.plan()));
    }
    static boolean parallel(Plan p,Plan other) {
        // Compare wet spans, not approach endpoints: separate rivers across an island are not
        // parallel crossings. Opposite traversal of the same reach must still share one bridge.
        BlockPos a=BlockPos.of(p.route().get(VillageBridgeSurvey.APPROACH));
        BlockPos b=BlockPos.of(p.route().get(VillageBridgeSurvey.APPROACH+p.waterLength()-1));
        BlockPos c=BlockPos.of(other.route().get(VillageBridgeSurvey.APPROACH));
        BlockPos d=BlockPos.of(other.route().get(VillageBridgeSurvey.APPROACH+other.waterLength()-1));
        boolean east=a.getX()!=b.getX();
        if(east!=(c.getX()!=d.getX()))return false;
        int lateral=Math.abs(east?a.getZ()-c.getZ():a.getX()-c.getX());
        int a0=east?a.getX():a.getZ(),a1=east?b.getX():b.getZ();
        int b0=east?c.getX():c.getZ(),b1=east?d.getX():d.getZ();
        return lateral<=24&&Math.max(Math.min(a0,a1),Math.min(b0,b1))
                <=Math.min(Math.max(a0,a1),Math.max(b0,b1));
    }
}
