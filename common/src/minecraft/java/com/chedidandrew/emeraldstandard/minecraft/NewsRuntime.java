package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Records explicit player actions on loaded, evidenced village property. Never changes markets. */
public final class NewsRuntime {
    private static MinecraftServer server;
    private static EconomyService economy;
    private static final Map<UUID,Long> lastOpen=new HashMap<>();
    public record BreakEvidence(UUID village,BlockPos pos,boolean crop,BlockState original) {}
    public record ContainerBefore(Container container,UUID village,int count) {}
    private NewsRuntime() {}
    public static void start(MinecraftServer s,EconomyService e) {
        server=s;economy=e;lastOpen.clear();
        NewsTemplates.reset();NewsTemplates.refresh(s,e);
        e.newsPlayerNames(id -> {
            var player=s.getPlayerList().getPlayer(id);
            return player==null ? "Player "+id.toString().substring(0,8) : player.getName().getString();
        });
    }
    public static void stop(MinecraftServer s) {
        if (server!=s) return;
        economy.newsPlayerNames(id -> "Player "+id.toString().substring(0,8));
        server=null; economy=null; lastOpen.clear();
    }
    public static void open(ServerPlayer player) { open(player,false); }
    public static void open(ServerPlayer player,boolean browser) { open(player,browser,null); }
    public static void openItem(ServerPlayer player,net.minecraft.world.InteractionHand hand) {
        if(!(player.getItemInHand(hand).getItem() instanceof NewspaperItem))return;
        open(player,false,hand);
    }
    private static void open(ServerPlayer player,boolean browser,net.minecraft.world.InteractionHand hand) {
        if(server!=player.level().getServer()||economy==null||player.connection==null||!player.isAlive()) return;
        long now=server.overworld().getGameTime(),old=lastOpen.getOrDefault(player.getUUID(),Long.MIN_VALUE);
        if(old!=Long.MIN_VALUE && now-old<10) return;
        if(lastOpen.size()>1024) lastOpen.clear();
        lastOpen.put(player.getUUID(),now);
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new NewspaperMenu(id,inventory,economy,browser,hand),
                Component.literal("The Emerald Wire")));
        if(player.containerMenu instanceof NewspaperMenu menu)menu.beginReading(player);
    }
    private static boolean enabled(ServerPlayer p) { return server==p.level().getServer()&&economy!=null
            &&EmeraldConfig.current().newsPolicy().publicPlayers()&&!p.isCreative()&&!p.isSpectator(); }
    public static BreakEvidence beforeBreak(ServerPlayer p,BlockPos pos) {
        if(!enabled(p)) return null;
        var level=(ServerLevel)p.level();
        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) return null;
        var state=level.getBlockState(pos);
        if(state.isAir()) return null;
        boolean crop=state.getBlock() instanceof CropBlock;
        if(!crop&&!state.isSolidRender()&&!state.hasBlockEntity()) return null;
        UUID village=owner(level,pos);
        return village==null?null:new BreakEvidence(village,pos.immutable(),crop,state);
    }
    public static void afterBreak(ServerPlayer p,BreakEvidence observation,boolean success) {
        if(!success||observation==null||!enabled(p)) return;
        ServerLevel level=(ServerLevel)p.level();
        if(level.getBlockState(observation.pos).equals(observation.original)) return;
        NewsEvidence data=NewsEvidence.get(level);
        String key=observation.crop?"crop:"+observation.pos.asLong():
                "damage:"+p.getUUID()+":"+observation.village;
        int count=observation.crop?1:1+Optional.ofNullable(data.pending.get(key)).map(NewsEvidence.Pending::count).orElse(0);
        queue(data,key,new NewsEvidence.Pending(observation.village.toString(),p.getUUID().toString(),
                p.getName().getString(),observation.crop?"CROPS":"DAMAGE",observation.pos.asLong(),
                level.getGameTime()+(observation.crop?2400:200),Math.min(count,1000000)));
    }
    public static void placed(ServerLevel level,BlockPos pos,BlockState expected,Player player) {
        if(server!=level.getServer()) return;
        // The placement mixin fires only on a successful placement; pending crops remain identifiable.
        var data=NewsEvidence.get(level);
        String key="crop:"+pos.asLong();
        var crop=data.pending.get(key);
        if(expected.getBlock() instanceof CropBlock && crop!=null) {
            if(player instanceof ServerPlayer p&&enabled(p))
                queue(data,key,new NewsEvidence.Pending(crop.village(),p.getUUID().toString(),p.getName().getString(),
                        "REPLANTED",pos.asLong(),crop.due(),1));
            else { data.pending.remove(key); data.setDirty(); }
            return;
        }
        data.touched(pos.asLong());
    }
    private static void queue(NewsEvidence data,String key,NewsEvidence.Pending pending) {
        if(data.pending.size()>=NewsEvidence.MAX_PENDING&&!data.pending.containsKey(key)) return;
        data.pending.put(key,pending);data.setDirty();
    }
    public static List<ContainerBefore> beforeClick(AbstractContainerMenu menu,Player player) {
        if(!(player instanceof ServerPlayer p)||!enabled(p)||menu instanceof NewspaperMenu||menu.slots.size()>128) return List.of();
        List<ContainerBefore> result=new ArrayList<>();
        Set<Container> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Slot slot:menu.slots) {
            Container c=slot.container;
            if(!seen.add(c)||c instanceof net.minecraft.world.entity.player.Inventory||c.getContainerSize()>108) continue;
            BlockPos pos=containerPos(p,c);
            if(pos==null) continue;
            UUID village=owner((ServerLevel)p.level(),pos);
            if(village!=null) result.add(new ContainerBefore(c,village,food(c)));
            if(result.size()>=2) break;
        }
        return result;
    }
    public static void afterClick(Player player,List<ContainerBefore> before) {
        if(!(player instanceof ServerPlayer p)||!enabled(p)) return;
        var level=(ServerLevel)p.level();var data=NewsEvidence.get(level);
        for(var b:before) {
            int delta=b.count-food(b.container);
            if(delta==0) continue;
            String key="food:"+p.getUUID()+":"+b.village;
            var old=data.pending.get(key);
            int count=Math.max(-1000000,Math.min(1000000,(old==null?0:old.count())+delta));
            queue(data,key,new NewsEvidence.Pending(b.village.toString(),p.getUUID().toString(),p.getName().getString(),
                    "FOOD_REMOVED",p.blockPosition().asLong(),old==null?level.getGameTime()+200:old.due(),count));
        }
    }
    static int food(Container c) {
        int n=0;
        for(int i=0;i<c.getContainerSize();i++) {
            ItemStack s=c.getItem(i);
            if(s.has(DataComponents.FOOD)) n+=s.getCount();
        }
        return n;
    }
    private static BlockPos containerPos(ServerPlayer p,Container container) {
        if(container instanceof BaseContainerBlockEntity block) return block.getBlockPos();
        if(!(container instanceof CompoundContainer compound)) return null;
        var level=(ServerLevel)p.level();
        // Two halves must BOTH have ownership evidence; a player-added half is not village storage.
        List<BlockPos> halves=new ArrayList<>();
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            var chunk=level.getChunkSource().getChunkNow((p.blockPosition().getX()>>4)+x,(p.blockPosition().getZ()>>4)+z);
            if(chunk==null) continue;
            for(var block:chunk.getBlockEntities().values())
                if(block instanceof Container c&&compound.contains(c)) halves.add(block.getBlockPos());
        }
        if(halves.size()!=2) return null;
        UUID a=owner(level,halves.getFirst()),b=owner(level,halves.getLast());
        return a!=null&&a.equals(b)?halves.getFirst():null;
    }
    static UUID owner(ServerLevel level,BlockPos pos) {
        if(economy==null||server!=level.getServer()||level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) return null;
        var evidence=NewsEvidence.get(level);
        if(evidence.community.contains(pos.asLong())) {
            var explicit=economy.newsOwnership(level.dimension().identifier().toString(),pos.asLong());
            return explicit==null?null:explicit.villageId();
        }
        if(EmeraldConfig.current().newsExplicitPropertyOnly())return null;
        if(evidence.saturated||evidence.touched.contains(pos.asLong())) return null;
        try {
            var land=DevelopmentLandProtection.land(server);
            if(land.excluded(level.dimension().identifier().toString(),pos.getX(),pos.getZ(),pos.getX(),pos.getZ())
                    ||land.nearby(level.dimension().identifier().toString(),pos.getX(),pos.getZ(),0).containsKey(pos.asLong())) return null;
        } catch(IllegalStateException unavailable) { return null; }
        var ownership=economy.newsOwnership(level.dimension().identifier().toString(),pos.asLong());
        if(ownership==null) return null;
        if(ownership.completedBuilding()) return ownership.villageId();
        // Only already-loaded native structure metadata; no locate calls or chunk generation.
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        int checked=0;
        for(var refs:chunk.getAllReferences().entrySet()) {
            var key=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(refs.getKey());
            if(key==null||!key.getNamespace().equals("minecraft")||!key.getPath().startsWith("village_")) continue;
            for(long packed:refs.getValue()) {
                if(++checked>32) return null;
                var origin=new ChunkPos((int)packed,(int)(packed>>32));
                var loaded=level.getChunkSource().getChunkNow(origin.x(),origin.z());
                if(loaded==null) continue;
                var start=loaded.getAllStarts().get(refs.getKey());
                if(start!=null) for(var piece:start.getPieces())
                    if(piece.getBoundingBox().isInside(pos)) return ownership.villageId();
            }
        }
        return null;
    }
    private static boolean inside(BlockPos p,BlockPos a,BlockPos b) {
        return p.getX()>=a.getX()&&p.getX()<=b.getX()&&p.getY()>=a.getY()&&p.getY()<=b.getY()
                &&p.getZ()>=a.getZ()&&p.getZ()<=b.getZ();
    }
    public static void tick(MinecraftServer s) {
        if(server!=s||economy==null||s.overworld().getGameTime()%20!=0) return;
        NewsTemplates.refresh(s,economy);
        for(ServerLevel level:s.getAllLevels()) process(level);
    }
    static void verifyForSmoke(ServerLevel level) {
        var real=economy;
        try { NewsRuntimeSelfTest.verify(level); }
        finally { if(real!=null) start(level.getServer(),real); }
    }
    static void process(ServerLevel level) {
            var data=NewsEvidence.get(level);
            if(!EmeraldConfig.current().newsPolicy().publicPlayers()) {
                if(!data.pending.isEmpty()){data.pending.clear();data.setDirty();}
                return;
            }
            List<String> done=new ArrayList<>();
            List<String> deferred=new ArrayList<>();
            int checked=0;
            for(var entry:data.pending.entrySet()) {
                if(++checked>256) break;
                var p=entry.getValue();
                if(level.getGameTime()<p.due()) { deferred.add(entry.getKey()); continue; }
                try {
                    var kind=NewsWire.Kind.valueOf(p.kind()); int quantity=p.count();
                    if(kind==NewsWire.Kind.FOOD_REMOVED && quantity<0) { kind=NewsWire.Kind.FOOD_RETURNED; quantity=-quantity; }
                    if(kind==NewsWire.Kind.CROPS||kind==NewsWire.Kind.REPLANTED) {
                        var pos=BlockPos.of(p.pos());
                        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) {
                            deferred.add(entry.getKey()); continue;
                        }
                        boolean present=level.getBlockState(pos).getBlock() instanceof CropBlock;
                        // Never credit a harvester for somebody else's unobserved replanting.
                        if(kind==NewsWire.Kind.CROPS&&present||kind==NewsWire.Kind.REPLANTED&&!present) quantity=0;
                    }
                    if(quantity>0 && ((kind!=NewsWire.Kind.FOOD_REMOVED&&kind!=NewsWire.Kind.FOOD_RETURNED)||quantity>=8)
                            && (kind!=NewsWire.Kind.DAMAGE||quantity>=4))
                        economy.reportPlayerNews(kind,UUID.fromString(p.village()),UUID.fromString(p.player()),p.name(),quantity);
                } catch(IllegalArgumentException invalid) { /* Invalid old evidence cannot name an offender. */ }
                done.add(entry.getKey());
            }
            done.forEach(data.pending::remove); if(!done.isEmpty()) data.setDirty();
            // Round robin: unloaded crops must not starve later ready observations.
            for(String key:deferred) {
                var pending=data.pending.remove(key); data.pending.put(key,pending);
            }
    }
}
