package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.util.datafix.DataFixTypes;

/**
 * Exact-cell construction receipts. Reserved cells are NOT drop ownership; only blocks actually
 * supplied by a builder are owned. The economy's pending project is the handover authority.
 * No completed building, retired lot or unrelated inventory is ever enrolled in repair.
 */
public final class ConstructionOwnership extends SavedData {
    record Cell(long position, String block, boolean owned, boolean loot) {
        static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Cell::position),
                Codec.STRING.fieldOf("block").forGetter(Cell::block),
                Codec.BOOL.fieldOf("owned").forGetter(Cell::owned),
                Codec.BOOL.fieldOf("loot").forGetter(Cell::loot)).apply(i, Cell::new));
    }
    record Site(String village, long project, long origin, boolean bank, List<Cell> cells, int handoverFloor) {
        static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("village").forGetter(Site::village),
                Codec.LONG.fieldOf("project").forGetter(Site::project),
                Codec.LONG.fieldOf("origin").forGetter(Site::origin),
                Codec.BOOL.fieldOf("bank").forGetter(Site::bank),
                Cell.CODEC.listOf().fieldOf("cells").forGetter(Site::cells),
                Codec.INT.optionalFieldOf("handover_floor",0).forGetter(Site::handoverFloor)).apply(i, Site::new));
        Site { cells = new ArrayList<>(cells); handoverFloor=Math.max(0,handoverFloor); }
    }
    static final Codec<ConstructionOwnership> CODEC = Codec.unboundedMap(Codec.STRING, Site.CODEC)
            .xmap(ConstructionOwnership::new, s -> s.sites);
    static final SavedDataType<ConstructionOwnership> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard", "construction_ownership"),
            ConstructionOwnership::new, CODEC, DataFixTypes.LEVEL);
    private static MinecraftServer server;
    private static EconomyService service;
    private EconomyService authority;
    private ServerLevel context;
    final Map<String, Site> sites = new LinkedHashMap<>();
    private final Map<Long, String> at = new HashMap<>();
    private final Map<String, Map<Long, Integer>> indices = new HashMap<>();
    private final Map<String, Boolean> activeCache = new HashMap<>();
    private final Map<String, Integer> registeredSize = new HashMap<>();
    private final Map<String,List<BlockPos>> pendingLoot = new HashMap<>();
    private long cacheTick = Long.MIN_VALUE;
    public ConstructionOwnership() {}
    ConstructionOwnership(Map<String, Site> saved) { sites.putAll(saved); rebuild(); }
    public static void start(MinecraftServer s, EconomyService e) { server=s; service=e; }
    public static void stop(MinecraftServer s) { if(server==s) { server=null; service=null; } }
    static ConstructionOwnership get(ServerLevel level) {
        var data=level.getDataStorage().computeIfAbsent(TYPE); data.context=level; return data;
    }
    private void rebuild() {
        at.clear(); indices.clear();
        sites.forEach((job, site) -> {
            Map<Long,Integer> idx=new HashMap<>();
            for(int i=0;i<site.cells.size();i++) { long p=site.cells.get(i).position; idx.put(p,i); at.put(p,job); }
            indices.put(job,idx);
        });
    }
    static String project(UUID village,long id,long origin) { return village+"/"+id+"/"+origin; }
    static String bank(long id,long origin) { return "bank/"+id+"/"+origin; }
    void begin(EconomyService economy,String job,UUID village,long id,long origin,boolean bank) {
        if(authority!=economy) activeCache.clear();
        authority=economy;
        if(!sites.containsKey(job)) {
            sites.put(job,new Site(village==null?"":village.toString(),id,origin,bank,List.of(),0));
            indices.put(job,new HashMap<>()); setDirty();
        }
        activeCache.remove(job);
    }
    boolean needsRegistration(String job,int size) { return registeredSize.getOrDefault(job,-1)!=size; }
    void registered(String job,int size) { registeredSize.put(job,size); }
    int handoverFloor(String job) { Site s=sites.get(job); return s==null?0:s.handoverFloor; }
    void protectPrefix(String job,int floor) {
        Site s=sites.get(job);
        if(s!=null&&floor>s.handoverFloor) {
            sites.put(job,new Site(s.village,s.project,s.origin,s.bank,s.cells,floor)); setDirty();
        }
    }
    void completeProject(String job,int total) {
        Site old=sites.get(job); if(old==null) return;
        finish(job);
        // Retain only a small irreversible handover marker, never a repairable old block list.
        sites.put(job,new Site(old.village,old.project,old.origin,false,List.of(),Math.max(total,old.handoverFloor)));
        indices.put(job,new HashMap<>()); setDirty();
    }
    void reserve(String job,BlockPos pos,BlockState state) {
        Site site=sites.get(job); if(site==null) return;
        Map<Long,Integer> idx=indices.get(job);
        if(idx.containsKey(pos.asLong())) return; // Frozen ownership; later authored mutations claim explicitly.
        String existing=at.get(pos.asLong());
        if(existing!=null&&!existing.equals(job)) {
            if(context==null||active(context,existing)) return; // Never steal a live neighboring site.
            finish(existing);
        }
        idx.put(pos.asLong(),site.cells.size()); at.put(pos.asLong(),job);
        site.cells.add(new Cell(pos.asLong(),id(state),false,false)); setDirty();
    }
    void claim(String job,BlockPos pos,BlockState state,boolean freshLoot) {
        reserve(job,pos,state);
        if(!job.equals(at.get(pos.asLong()))) return;
        Site site=sites.get(job); int i=indices.get(job).get(pos.asLong()); Cell old=site.cells.get(i);
        Cell next=new Cell(pos.asLong(),id(state),!state.isAir(),old.owned ? old.loot : freshLoot);
        if(!next.equals(old)) { site.cells.set(i,next); setDirty(); }
    }
    private static String id(BlockState state) { return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); }
    private boolean active(ServerLevel level,String job) {
        if(job==null) return false;
        EconomyService e=authority!=null ? authority : server==level.getServer() ? service : null;
        if(e==null) return true; // Startup / unknown authority: never unlock a saved active receipt speculatively.
        long tick=level.getGameTime();
        if(cacheTick!=tick) { cacheTick=tick; activeCache.clear(); }
        Boolean cached=activeCache.get(job); if(cached!=null) return cached;
        Site s=sites.get(job); if(s==null) return false;
        boolean pending=false;
        if(s.bank) {
            var p=e.pendingBankConstructionsSnapshot().get(s.project);
            pending=p!=null&&p.origin()==s.origin;
        } else {
            try {
                var v=e.developmentVillageSnapshot(UUID.fromString(s.village));
                pending=v!=null&&v.village().projects.stream().anyMatch(p->p.projectId==s.project
                        &&p.originPos==s.origin&&!p.materializedComplete&&!p.manualRepairRequired&&!p.abstractOnly);
            } catch(IllegalArgumentException ignored) { /* malformed receipts never gain repair authority */ }
        }
        activeCache.put(job,pending);
        return pending;
    }
    private Cell cell(ServerLevel level,BlockPos pos) {
        String job=at.get(pos.asLong());
        if(!active(level,job)) return null;
        return sites.get(job).cells.get(indices.get(job).get(pos.asLong()));
    }
    public static boolean reserved(Level world,BlockPos pos) {
        return world instanceof ServerLevel level && get(level).cell(level,pos)!=null;
    }
    public static boolean owned(Level world,BlockPos pos,BlockState state) {
        if(!(world instanceof ServerLevel level)) return false;
        Cell c=get(level).cell(level,pos);
        return c!=null&&c.owned&&c.block.equals(id(state));
    }
    /** Contents supplied by an administrator/another mod are never discarded. */
    public static boolean hasStoredContents(ServerLevel level,BlockPos pos) {
        var be=level.getBlockEntity(pos);
        if(be instanceof RandomizableContainerBlockEntity r && r.getLootTable()!=null) return true;
        return be instanceof Container c&&!c.isEmpty();
    }
    public static boolean suppressDrops(Level world,BlockPos pos,BlockState state) {
        return owned(world,pos,state)&&(!(world instanceof ServerLevel l)||!hasStoredContents(l,pos));
    }
    public static boolean locked(Level world,BlockPos pos) {
        if(!(world instanceof ServerLevel level)) return false;
        if(owned(level,pos,level.getBlockState(pos))) return true;
        // A double chest must not expose a locked half through its unowned neighboring half.
        if(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)
            for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n=pos.relative(d);
                if(level.hasChunk(n.getX()>>4,n.getZ()>>4)
                        &&level.getBlockEntity(n) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                        &&owned(level,n,level.getBlockState(n))) return true;
            }
        return false;
    }
    public static void explain(Player player) {
        if(player instanceof net.minecraft.server.level.ServerPlayer p && p.connection!=null)
            p.sendSystemMessage(Component.translatable("message.the_emerald_standard.construction_reserved"),true);
    }
    /** Resolve fresh loot only once, just before the irreversible handover, never on replacement. */
    boolean prepareHandover(ServerLevel level,String job,net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {
        Site s=sites.get(job); if(s==null||level.noSave()) return false;
        List<BlockPos> grants=new ArrayList<>();
        for(int i=0;i<s.cells.size();i++) {
            Cell c=s.cells.get(i);
            if(!c.loot) continue;
            BlockPos p=BlockPos.of(c.position);
            if(!level.hasChunk(p.getX()>>4,p.getZ()>>4)) return false;
            grants.add(p);
        }
        // Write the one-shot receipts before assigning loot. A interrupted grant may be empty,
        // but it can never replay a loot roll or erase an existing inventory.
        for(BlockPos p:grants) {
            int i=indices.get(job).get(p.asLong()); Cell c=s.cells.get(i);
            s.cells.set(i,new Cell(c.position,c.block,c.owned,false));
        }
        setDirty();
        try {
            level.getDataStorage().saveAndJoin();
            if(!grants.isEmpty()) pendingLoot.put(job,List.copyOf(grants));
            level.getChunkSource().save(true);
            return true;
        } catch(RuntimeException failedSave) { return false; }
    }
    /** Only call after the economy has durably accepted physical completion. */
    void grantHandoverLoot(ServerLevel level,String job,net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {
        var grants=pendingLoot.remove(job);
        if(grants!=null) for(BlockPos pos:grants) VillageStructureLoot.assignNewStorage(level,pos,table);
    }
    void finish(String job) {
        Site removed=sites.remove(job); if(removed==null) return;
        for(Cell c:removed.cells) at.remove(c.position,job);
        indices.remove(job); activeCache.remove(job); registeredSize.remove(job); pendingLoot.remove(job); setDirty();
    }
}
