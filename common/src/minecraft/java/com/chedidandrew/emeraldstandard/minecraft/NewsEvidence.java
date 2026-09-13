package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.*;

/** Bounded durable observation windows and conservative player-property exclusions. */
final class NewsEvidence extends SavedData {
    static final int MAX_TOUCHED=65536, MAX_PENDING=2048;
    record Pending(String village,String player,String name,String kind,long pos,long due,int count) {
        static final Codec<Pending> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("village").forGetter(Pending::village),Codec.STRING.fieldOf("player").forGetter(Pending::player),
            Codec.STRING.fieldOf("name").forGetter(Pending::name),Codec.STRING.fieldOf("kind").forGetter(Pending::kind),
            Codec.LONG.fieldOf("pos").forGetter(Pending::pos),Codec.LONG.fieldOf("due").forGetter(Pending::due),
            Codec.INT.fieldOf("count").forGetter(Pending::count)).apply(i,Pending::new));
    }
    static final Codec<NewsEvidence> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.listOf(0,MAX_TOUCHED).fieldOf("touched").forGetter(s->new ArrayList<>(s.touched)),
            Codec.BOOL.fieldOf("saturated").forGetter(s->s.saturated),
            Codec.LONG.listOf(0,16384).optionalFieldOf("community",List.of()).forGetter(s->new ArrayList<>(s.community)),
            Codec.unboundedMap(Codec.STRING,Pending.CODEC).fieldOf("pending").forGetter(s->s.pending))
            .apply(i,NewsEvidence::new));
    static final SavedDataType<NewsEvidence> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard","news_evidence"),NewsEvidence::new,CODEC,DataFixTypes.LEVEL);
    final Set<Long> touched=new HashSet<>();
    final Set<Long> community=new HashSet<>();
    final Map<String,Pending> pending=new LinkedHashMap<>();
    boolean saturated;
    NewsEvidence() {}
    NewsEvidence(List<Long> touched,boolean saturated,Map<String,Pending> pending) {
        this(touched,saturated,List.of(),pending);
    }
    NewsEvidence(List<Long> touched,boolean saturated,List<Long> community,Map<String,Pending> pending) {
        this.community.addAll(community);
        this.touched.addAll(touched);this.saturated=saturated;
        if(pending.size()<=MAX_PENDING) this.pending.putAll(pending); else this.saturated=true;
    }
    static NewsEvidence get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    void touched(long pos) {
        if(touched.size()<MAX_TOUCHED) touched.add(pos); else saturated=true;
        setDirty();
    }
}
