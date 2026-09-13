package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Opt-in disposable-server checks, including actual egg placement and native dispenser behavior. */
final class CreativeContentSelfTest {
    static void verify(ServerLevel level) {
        CreativeModeTabs.tryRebuildTabContents(level.getServer().getWorldData().enabledFeatures(), false, level.registryAccess());
        var tab=BuiltInRegistries.CREATIVE_MODE_TAB.getValue(EmeraldCreativeContent.TAB_ID);
        require(tab!=null, "dedicated creative tab registered");
        var catalog=EmeraldCreativeContent.items();
        require(catalog.size()>=5, "all five original custom items registered");
        for (var item: catalog) {
            require(tab.contains(new ItemStack(item)) && CreativeModeTabs.searchTab().contains(new ItemStack(item)),
                    "tab/search include "+BuiltInRegistries.ITEM.getKey(item));
        }
        require(tab.getDisplayItems().size()==catalog.size(), "catalog contains each registered mod item exactly once");
        BuiltInRegistries.BLOCK.forEach(block -> {
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(BankerProfessionSupport.MOD_ID))
                require(block.asItem()!=Items.AIR && catalog.contains(block.asItem()), "custom block has a catalog item");
        });
        var bankerEgg=BuiltInRegistries.ITEM.getValue(EmeraldCreativeContent.BANKER_EGG_ID);
        var builderEgg=BuiltInRegistries.ITEM.getValue(EmeraldCreativeContent.BUILDER_EGG_ID);
        for (var egg: List.of(bankerEgg,builderEgg)) {
            require(egg instanceof SpawnEggItem, "native spawn egg");
            require(BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.SPAWN_EGGS).orElseThrow().value()
                    .contains(new ItemStack(egg)), "egg in vanilla category too");
            require(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                    BuiltInRegistries.ITEM.getKey(egg))).isEmpty(), "spawn eggs remain creative-only");
        }
        require(SpawnEggItem.getType(new ItemStack(builderEgg))==ConstructionContent.builder
                && SpawnEggItem.getType(new ItemStack(bankerEgg))==EntityTypes.VILLAGER, "egg entity types");
        BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
            if(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(BankerProfessionSupport.MOD_ID))
                require(catalog.stream().anyMatch(item -> item instanceof SpawnEggItem
                        && SpawnEggItem.getType(new ItemStack(item))==type),"every custom mob has its own egg");
        });
        verifyRecipe(level);

        BlockPos origin=new BlockPos(936,level.getMaxY()-30,936);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        List<Entity> actors=new ArrayList<>();
        try {
            for(int x=-3;x<=12;x++) for(int z=-3;z<=3;z++) for(int y=-1;y<=3;y++) {
                BlockPos p=origin.offset(x,y,z); level.getChunk(p);
                before.put(p,level.getBlockState(p));
                level.setBlock(p,y==-1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),18);
            }
            var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"CreativeFixture"),
                    ClientInformation.createDefault());
            player.getAbilities().instabuild=true;
            player.setPos(origin.getX(),origin.getY(),origin.getZ()-3);
            var banker=spawn(level,origin,player,bankerEgg,Villager.class,actors);
            var builder=spawn(level,origin.east(4),player,builderEgg,ConstructionBuilder.class,actors);
            require(banker.getVillagerXp()==1 && BankerAccess.isBanker(banker)
                    && !banker.entityTags().contains(BankerAccess.BANKER_TAG) && BankerAccess.bankRegionKey(banker)==null
                    && !BankerAccess.isLegacyUnscopedBanker(banker), "egg Banker is personal, never a managed/canonical claim");
            require(builder.job.isEmpty() && !builder.leaving, "egg builder starts unassigned");
            DevelopmentEntities.loaded(builder,level);
            var site=new VillageConstructionActivity.Site("creative-fixture-site",UUID.randomUUID(),1,
                    origin,origin,origin.offset(6,3,2),true);
            ConstructionSiteLedger.get(level).fencePrepared(site.tag());
            VillageConstructionActivity.update(level,List.of(site));
            require(builder.job.isEmpty() && !builder.leaving
                    && !ConstructionSiteLedger.get(level).entries.get(site.tag()).workers().contains(builder.getStringUUID()),
                    "nearby active worksite never adopts manual builder");
            VillageConstructionActivity.update(level,List.of());
            for(int i=0;i<260;i++) { builder.tick(); banker.tick(); }
            require(!builder.isRemoved() && !builder.leaving && !builder.hammering()
                    && ConstructionSiteLedger.get(level).entries.values().stream()
                            .noneMatch(e->e.workers().contains(builder.getStringUUID())),
                    "unassigned builder survives empty census and AI without joining a crew");
            require(BankerAccess.isBanker(banker), "unemployed-reset AI does not erase egg profession");
            var restoredBuilder=new ConstructionBuilder(ConstructionContent.builder,level);
            restoredBuilder.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),save(level,builder)));
            actors.add(restoredBuilder); restoredBuilder.tick();
            require(restoredBuilder.job.isEmpty() && !restoredBuilder.leaving && !restoredBuilder.isRemoved(),
                    "unassigned builder survives native save/reload");
            var restoredBanker=EntityTypes.VILLAGER.create(level,EntitySpawnReason.LOAD);
            require(restoredBanker!=null,"reload villager factory");
            restoredBanker.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),save(level,banker)));
            actors.add(restoredBanker);
            require(BankerAccess.isBanker(restoredBanker) && !restoredBanker.entityTags().contains(BankerAccess.BANKER_TAG)
                    && restoredBanker.getVillagerXp()==1, "personal Banker identity survives native reload");

            BlockPos dispenserPos=origin.east(8);
            var dispenserState=Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST);
            level.setBlock(dispenserPos,dispenserState,18);
            var source=new BlockSource(level,dispenserPos,dispenserState,(DispenserBlockEntity)level.getBlockEntity(dispenserPos));
            for (var egg:List.of(bankerEgg,builderEgg)) {
                var stack=new ItemStack(egg,2);
                SpawnEggItemBehavior.INSTANCE.execute(source,stack);
                require(stack.getCount()==1,"dispenser consumes exactly one egg");
            }
            var dispensed=level.getEntitiesOfClass(Mob.class,new AABB(dispenserPos.east()).inflate(1));
            actors.addAll(dispensed);
            require(dispensed.stream().anyMatch(e->e instanceof Villager && BankerAccess.isBanker(e)
                            && !e.entityTags().contains(BankerAccess.BANKER_TAG))
                    && dispensed.stream().anyMatch(e->e instanceof ConstructionBuilder b && b.job.isEmpty()),
                    "dispenser retains distinct egg data: "+dispensed.stream().map(e->e.getType()+" "+e.position()
                            +" banker="+BankerAccess.isBanker(e)+" tags="+e.entityTags()
                            +(e instanceof ConstructionBuilder b ? " job="+b.job : "")).toList());
            level.setBlock(dispenserPos,Blocks.SPAWNER.defaultBlockState(),18);
            var refused=new ItemStack(bankerEgg,2);
            require(bankerEgg.useOn(context(level,dispenserPos,null,refused))==InteractionResult.FAIL
                    && refused.getCount()==2,"Banker egg refuses lossy spawner configuration without consuming egg");

            BlockPos fencePos=origin.south(2);
            var fenceState=ConstructionContent.fence.defaultBlockState();
            level.setBlock(fencePos,fenceState,18);
            var receipt=(ConstructionFenceBlock.Receipt)level.getBlockEntity(fencePos);
            var drops=Block.getDrops(fenceState,level,fencePos,receipt);
            require(drops.size()==1 && drops.getFirst().is(ConstructionContent.fence.asItem())
                    && drops.getFirst().getCount()==1,"manual fence yields exactly one recoverable item");
            receipt.own("test-owned-fence",Blocks.AIR.defaultBlockState());
            require(Block.getDrops(fenceState,level,fencePos,receipt).isEmpty()
                    && Block.getDrops(fenceState,level,fencePos,null).isEmpty(),
                    "automatic and missing-receipt fence loot fail closed");
            System.out.println("PASS CreativeContentSelfTest: complete tab/search, native eggs, creative placement, distinct personal Banker, unassigned builder AI/reload, dispensers, spawner refusal, fence recipe and non-farmable automatic loot");
        } finally {
            actors.forEach(Entity::discard);
            before.forEach((p,state)->level.setBlock(p,state,18));
        }
    }
    private static <T extends Mob> T spawn(ServerLevel level,BlockPos pos,ServerPlayer player,Item egg,Class<T> type,List<Entity> actors) {
        var stack=new ItemStack(egg,2);
        require(egg.useOn(context(level,pos.below(),player,stack))==InteractionResult.SUCCESS
                && stack.getCount()==2,"creative placement succeeds without consuming egg");
        var found=level.getEntitiesOfClass(type,new AABB(pos).inflate(1));
        require(found.size()==1,"exactly one intended mob spawned"); actors.add(found.getFirst()); return found.getFirst();
    }
    private static UseOnContext context(ServerLevel level,BlockPos pos,ServerPlayer player,ItemStack stack) {
        return new UseOnContext(level,player,InteractionHand.MAIN_HAND,stack,
                new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    private static net.minecraft.nbt.CompoundTag save(ServerLevel level,Entity entity) {
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
        entity.saveWithoutId(output); return output.buildResult();
    }
    private static void verifyRecipe(ServerLevel level) {
        var key=ResourceKey.<Recipe<?>>create(Registries.RECIPE,Identifier.fromNamespaceAndPath(BankerProfessionSupport.MOD_ID,"construction_fence"));
        var recipe=(CraftingRecipe)level.getServer().getRecipeManager().byKey(key).orElseThrow().value();
        var input=CraftingInput.of(3,2,List.of(new ItemStack(Items.STICK),new ItemStack(Items.DYE.yellow()),
                new ItemStack(Items.STICK),new ItemStack(Items.STICK),new ItemStack(Items.DYE.black()),new ItemStack(Items.STICK)));
        require(recipe.matches(input,level),"four sticks and two dyes match fence recipe");
        var output=recipe.assemble(input);
        require(output.is(ConstructionContent.fence.asItem()) && output.getCount()==4,"recipe makes four fences");
        require(!recipe.matches(CraftingInput.of(1,1,List.of(new ItemStack(Items.STICK))),level),"incomplete fence recipe rejected");
        require(level.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(BankerProfessionSupport.MOD_ID,
                "recipes/building_blocks/construction_fence"))!=null,"recipe discovery advancement loaded");
    }
    private static void require(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
}
