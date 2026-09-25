package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.DistrictTerrainColors;
import com.chedidandrew.emeraldstandard.minecraft.mixin.BiomeSeedAccess;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

/** Main-thread, loaded-chunk-only sampling; the store alone handles background I/O. */
public final class ExploredTerrainRuntime {
private static final int TRACKED_LIMIT=8192, SAMPLES_PER_TICK=128;
    private static final long TICK_BUDGET_NS=1_500_000;
    private static final LinkedHashMap<Long,Boolean> loaded=new LinkedHashMap<>();
    private static ClientLevel level;
    private static ExploredTerrainStore store;

    private static void attach(ClientLevel next) {
        if(level==next)return;
        if(store!=null)store.close();
        store=null;loaded.clear();level=next;
        if(next==null)return;
        Minecraft mc=Minecraft.getInstance();
        var integrated=mc.getSingleplayerServer();
        var remote=mc.getCurrentServer();
        // Never reuse a generic multiplayer scope; an unidentified connection stays session-only.
        if(integrated==null&&remote==null)return;
        String world=integrated!=null
                ? "save:"+integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : "server:"+remote.ip;
        long seed=((BiomeSeedAccess)next.getBiomeManager()).emeraldStandard$biomeZoomSeed();
        store=new ExploredTerrainStore(mc.gameDirectory.toPath().resolve("the_emerald_standard/map-cache/v1"),
                world+"|"+seed,next.dimension().toString());
    }
    public static void observed(ClientLevel owner,LevelChunk chunk) {
        if(chunk==null||owner!=Minecraft.getInstance().level)return;
        attach(owner);
        long key=ExploredTerrainStore.key(chunk.getPos().x(),chunk.getPos().z());
        // Freshly received terrain takes priority; old loaded terrain is refreshed round-robin.
        loaded.putFirst(key,true);
        if(loaded.size()>TRACKED_LIMIT)loaded.pollLastEntry();
    }
    public static void departing(ClientLevel owner,int x,int z) {
        if(owner!=level)return;
        long key=ExploredTerrainStore.key(x,z);
        Boolean unsampled=loaded.remove(key);
        if(!Boolean.TRUE.equals(unsampled)||store==null)return;
        var chunk=owner.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
        // Capture a fresh tile missed by the tick budget. Already sampled tiles need no unload work.
        if(chunk!=null)capture(chunk);
    }
    public static void tick() {
        attach(Minecraft.getInstance().level);
        if(level==null||store==null)return;
        long deadline=System.nanoTime()+TICK_BUDGET_NS;
        int tiles=Math.min(loaded.size(),SAMPLES_PER_TICK/ExploredTerrainStore.COLORS);
        for(int n=0;n<tiles;n++) {
            var entry=loaded.pollFirstEntry();
            if(entry==null)break;
            long key=entry.getKey();
            var chunk=level.getChunkSource().getChunk((int)key,(int)(key>>32),ChunkStatus.FULL,false);
            if(chunk!=null){capture(chunk);loaded.putLast(key,false);}
            if(System.nanoTime()>=deadline)break;
        }
        store.flush();
    }
    private static void capture(LevelChunk chunk) {
        int[] colors=new int[ExploredTerrainStore.COLORS];
        int x=chunk.getPos().getMinBlockX(),z=chunk.getPos().getMinBlockZ();
        for(int row=0;row<8;row++)for(int col=0;col<8;col++)
            colors[row*8+col]=DistrictTerrainColors.sample(chunk,chunk,x+col*2+1,z+row*2+1);
        store.record(chunk.getPos().x(),chunk.getPos().z(),colors);
    }
    static int color(ClientLevel owner,int x,int z) {
        if(owner==null)return 0;
        attach(owner);
        return store==null?0:store.color(x,z);
    }
    static String error(){return store==null?"":store.error();}
    private ExploredTerrainRuntime(){}
}
