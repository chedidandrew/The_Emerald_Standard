package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Runs only in explicitly requested disposable smoke servers. */
final class NewsRuntimeSelfTest {
    static void verify(ServerLevel level) {
        require(BankerMenu.BUTTON_NEWSPAPER>BankerMenu.BUTTON_MAP_NEXT
                &&BankerMenu.BUTTON_NEWSPAPER<BankerMenu.BUTTON_ASSET_BASE,"newspaper button has an unambiguous ID");
        var origin=new BlockPos(1032,level.getMaxY()-26,1032);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        var evidence=NewsEvidence.get(level);
        var savedPending=new LinkedHashMap<>(evidence.pending);
        var savedTouched=new HashSet<>(evidence.touched);boolean saturated=evidence.saturated;
        var savedCommunity=new HashSet<>(evidence.community);
        try {
            evidence.pending.clear();evidence.touched.clear();evidence.saturated=false;
            evidence.community.clear();
            UUID village=UUID.randomUUID();
            var state=EconomyState.fresh(933,System.currentTimeMillis(),0);
            var v=state.village(village);v.centerPos=origin.asLong();
            var project=new EconomyState.VillageProject();
            project.projectId=1;project.originPos=origin.asLong();
            project.boundsMinPos=origin.offset(-5,-1,-5).asLong();project.boundsMaxPos=origin.offset(5,3,5).asLong();
            project.economicProgress=1;project.economicComplete=true;
            project.totalBlocks=1;project.materializedBlocks=1;project.materializedComplete=true;
            v.projectSerial=1;v.projects.add(project);
            var dir=Files.createTempDirectory("tes-native-news-");
            state.save(dir.resolve("the_emerald_standard.properties"));
            var service=new EconomyService();service.start(dir,933,0,0);
            NewsRuntime.start(level.getServer(),service);
            for(int x=-4;x<=4;x++) for(int y=-1;y<=2;y++) {
                BlockPos pos=origin.offset(x,y,0);level.getChunk(pos);before.put(pos,level.getBlockState(pos));
                level.setBlock(pos,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            }
            var p=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"NewsFixture"),ClientInformation.createDefault());
            p.setPos(origin.getX(),origin.getY(),origin.getZ()+2);
            require(village.equals(NewsRuntime.owner(level,origin)),"completed project ownership");
            level.setBlock(origin,Blocks.STONE.defaultBlockState(),18);
            var observed=NewsRuntime.beforeBreak(p,origin);require(observed!=null,"observed building");
            NewsRuntime.afterBreak(p,observed,false);require(evidence.pending.isEmpty(),"canceled break reported");
            for(int x=0;x<4;x++) {
                var pos=origin.east(x);level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
                require(p.gameMode.destroyBlock(pos),"native break");
            }
            require(evidence.pending.size()==1&&evidence.pending.values().iterator().next().count()==4,"native mixin aggregates damage");
            expire(evidence);NewsRuntime.process(level);
            require(service.newspaper().getLast().kind()==NewsWire.Kind.DAMAGE,"damage report");
            BlockPos crop=origin.west();
            level.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),18);
            level.setBlock(crop,Blocks.WHEAT.defaultBlockState(),18);
            require(p.gameMode.destroyBlock(crop),"crop removal");
            require(evidence.pending.values().iterator().next().due()>level.getGameTime(),"replant window");
            var other=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"Replanter"),ClientInformation.createDefault());
            level.setBlock(crop,Blocks.WHEAT.defaultBlockState(),18);
            NewsRuntime.placed(level,crop,Blocks.WHEAT.defaultBlockState(),other);
            expire(evidence);NewsRuntime.process(level);
            require(service.newspaper().getLast().kind()==NewsWire.Kind.REPLANTED
                &&service.newspaper().getLast().actor().equals("Replanter"),"replant credits actual player");
            BlockPos chestPos=origin.west(3);
            level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),18);
            var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);
            chest.setItem(0,new ItemStack(Items.BREAD,16));
            var chestMenu=ChestMenu.threeRows(2,p.getInventory(),chest);
            chestMenu.clicked(0,0,ContainerInput.PICKUP,p);
            chestMenu.clicked(0,0,ContainerInput.PICKUP,p);
            require(evidence.pending.values().iterator().next().count()==0,"take/return net zero");
            expire(evidence);int oldNews=service.newspaper().size();NewsRuntime.process(level);
            require(service.newspaper().size()==oldNews,"net zero accused player");
            chestMenu.clicked(0,0,ContainerInput.QUICK_MOVE,p);
            expire(evidence);NewsRuntime.process(level);
            require(service.newspaper().getLast().kind()==NewsWire.Kind.FOOD_REMOVED
                &&service.newspaper().getLast().quantity()==16,"native shift-click food");
            NewsRuntime.placed(level,chestPos,Blocks.CHEST.defaultBlockState(),p);
            require(NewsRuntime.owner(level,chestPos)==null,"player property excluded");
            p.getAbilities().instabuild=true;
            require(NewsRuntime.beforeBreak(p,origin.east(4))==null&&NewsRuntime.beforeClick(chestMenu,p).isEmpty(),"creative excluded");
            p.getAbilities().instabuild=false;
            var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            dispatcher.register(NewsPropertyCommands.command());
            String point=origin.getX()+" "+origin.getY()+" "+origin.getZ();
            var commandSource=level.getServer().createCommandSourceStack();
            evidence.touched(origin.asLong());
            require(NewsRuntime.owner(level,origin)==null,"automatic player property exclusion");
            require(dispatcher.execute("news property add "+point+" "+point,commandSource)==1
                    &&village.equals(NewsRuntime.owner(level,origin)),"explicit community designation overrides inference");
            require(dispatcher.execute("news property add 0 70 0 100000 70 100000",commandSource)==0,"oversized designation rejected");
            // Inaccessible/unloaded observations cannot starve a later ready entry.
            for(int i=0;i<256;i++) evidence.pending.put("unloaded"+i,new NewsEvidence.Pending(village.toString(),
                p.getUUID().toString(),"NewsFixture","CROPS",new BlockPos(900000+i*16,70,900000).asLong(),0,1));
            evidence.pending.put("ready",new NewsEvidence.Pending(village.toString(),p.getUUID().toString(),"NewsFixture","FOOD_RETURNED",origin.asLong(),0,8));
            NewsRuntime.process(level);NewsRuntime.process(level);
            require(!evidence.pending.containsKey("ready"),"unloaded entries starved ready news");
            var encoded=NewsEvidence.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,evidence).getOrThrow();
            var decoded=NewsEvidence.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();
            require(decoded.pending.equals(evidence.pending)&&decoded.touched.equals(evidence.touched)
                    &&decoded.community.equals(evidence.community),"native durable evidence codec");
            require(dispatcher.execute("news property remove "+point+" "+point,commandSource)==1
                    &&NewsRuntime.owner(level,origin)==null,"community designation removal");
            evidence.pending.clear();
            for(int i=0;i<256;i++) service.reportPlayerNews(NewsWire.Kind.DONATION,village,UUID.randomUUID(),"ReaderFixture",64);
            var menu=new NewspaperMenu(3,p.getInventory(),service);p.containerMenu=menu;
            require(menu.total()==256&&menu.articles().size()==256,"full archive");
            var document=menu.slots.getFirst().getItem().copy();
            var buffer=RegistryFriendlyByteBuf.decorator(level.registryAccess()).apply(Unpooled.buffer());
            try {
                ItemStack.STREAM_CODEC.encode(buffer,document);
                var wire=ItemStack.STREAM_CODEC.decode(buffer);
                require(wire.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().size()==64,"real item packet");
            } finally {buffer.release();}
            for(var input:ContainerInput.values()) for(int slot:new int[]{-999,0,1,999}) menu.clicked(slot,0,input,p);
            require(menu.getCarried().isEmpty()&&ItemStack.matches(document,menu.slots.getFirst().getItem())
                &&menu.quickMoveStack(p,0).isEmpty(),"document cannot duplicate, drop, swap or transfer");
            require(!menu.clickMenuButton(p,999)&&menu.clickMenuButton(p,1)&&menu.offset()==0
                &&!menu.clickMenuButton(p,1),"archive navigation validation/cooldown");
            var item=BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(BankerProfessionSupport.MOD_ID,"newspaper"));
            require(item instanceof NewspaperItem&&EmeraldCreativeContent.items().contains(item),"creative newspaper");
            var recipe=(CraftingRecipe)level.getServer().getRecipeManager().byKey(ResourceKey.<Recipe<?>>create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(BankerProfessionSupport.MOD_ID,"newspaper"))).orElseThrow().value();
            var input=CraftingInput.of(2,1,List.of(new ItemStack(Items.INK_SAC),new ItemStack(Items.PAPER)));
            require(recipe.matches(input,level)&&recipe.assemble(input).is(item)&&recipe.assemble(input).getCount()==1,"native shapeless newspaper recipe");
            System.out.println("PASS native news: property, canceled/actual breaks, real container clicks, replant attribution, fairness, persistence, readonly packet and recipe");
        } catch(Exception error) {throw new IllegalStateException("News native regression",error);}
        finally {
            before.forEach((pos,state)->level.setBlock(pos,state,18));
            evidence.pending.clear();evidence.pending.putAll(savedPending);
            evidence.touched.clear();evidence.touched.addAll(savedTouched);evidence.saturated=saturated;evidence.setDirty();
            evidence.community.clear();evidence.community.addAll(savedCommunity);
        }
    }
    private static void expire(NewsEvidence e) {
        e.pending.replaceAll((k,p)->new NewsEvidence.Pending(p.village(),p.player(),p.name(),p.kind(),p.pos(),0,p.count()));
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
}
