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

/** Separate connector progress: never rewrite frozen historical paving/building cursors. */
final class WalkwayConnectionLedger extends SavedData {
    record Step(long pos,BlockState ground,BlockState lower,BlockState upper) {
        static final Codec<Step> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.LONG.fieldOf("pos").forGetter(Step::pos),
                BlockState.CODEC.fieldOf("ground").forGetter(Step::ground),
                BlockState.CODEC.fieldOf("lower").forGetter(Step::lower),
                BlockState.CODEC.fieldOf("upper").forGetter(Step::upper)).apply(i,Step::new));
    }
    record Job(List<Step> plan,int centers,int cursor,Set<Long> supplied,boolean done,long retry,String reason) {
        static final Codec<Job> CODEC=RecordCodecBuilder.create(i->i.group(
                Step.CODEC.listOf().fieldOf("plan").forGetter(Job::plan),
                Codec.intRange(0,512).fieldOf("centers").forGetter(Job::centers),
                Codec.intRange(0,8192).fieldOf("cursor").forGetter(Job::cursor),
                Codec.LONG.listOf().fieldOf("supplied").forGetter(j->new ArrayList<>(j.supplied())),
                Codec.BOOL.fieldOf("done").forGetter(Job::done),
                Codec.LONG.fieldOf("retry").forGetter(Job::retry),
                Codec.STRING.fieldOf("reason").forGetter(Job::reason)).apply(i,
                        (p,n,c,s,d,r,t)->new Job(p,n,c,new LinkedHashSet<>(s),d,r,t)));
        Job { plan=List.copyOf(plan); supplied=Set.copyOf(supplied); }
        static Job fresh() { return new Job(List.of(),0,0,Set.of(),false,0,"Awaiting loaded route survey"); }
        Job retry(long tick,String why) { return new Job(List.of(),0,0,supplied,false,tick+100,why); }
    }
    static final Codec<WalkwayConnectionLedger> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Job.CODEC).fieldOf("jobs").forGetter(s->s.jobs))
            .apply(i,WalkwayConnectionLedger::new));
    static final SavedDataType<WalkwayConnectionLedger> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard","walkway_connections"),
            WalkwayConnectionLedger::new,CODEC,DataFixTypes.LEVEL);
    final Map<String,Job> jobs=new LinkedHashMap<>();
    final Map<UUID,Integer> rotation=new HashMap<>();
    long lastTick=Long.MIN_VALUE;
    WalkwayConnectionLedger() {}
    WalkwayConnectionLedger(Map<String,Job> jobs) { this.jobs.putAll(jobs); }
    static WalkwayConnectionLedger get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    static String key(UUID village,long project,long origin) { return village+"/"+project+"/"+origin; }
    Job job(String key) { return jobs.getOrDefault(key,Job.fresh()); }
    void put(String key,Job job) { jobs.put(key,job);setDirty(); }
    List<BlockPos> route(String key) {
        Job j=job(key);
        return j.done() ? j.plan().stream().limit(j.centers()).map(s->BlockPos.of(s.pos())).toList() : List.of();
    }
}
