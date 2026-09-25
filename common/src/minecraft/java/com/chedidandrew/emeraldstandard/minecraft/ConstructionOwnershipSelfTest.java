package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Production mixins, real world destruction, one-shot loot and save-codec coverage. Disposable world only. */
final class ConstructionOwnershipSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin=new BlockPos(864,level.getMaxY()-32,864);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        List<net.minecraft.world.entity.Entity> cleanup=new ArrayList<>();
        String job=ConstructionOwnership.bank(99117,origin.asLong());
        var ledger=ConstructionOwnership.get(level);
        try {
            for(int x=-2;x<=12;x++) for(int z=-2;z<=2;z++) for(int y=-1;y<=5;y++) {
                BlockPos p=origin.offset(x,y,z); level.getChunk(p);
                before.put(p,level.getBlockState(p));
                level.setBlock(p,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            }
            var dir=Files.createTempDirectory("tes-owned-construction-");
            var economy=new EconomyService(); economy.start(dir,711,0);
            var plan=new BankConstruction(origin.asLong(),origin.asLong(),null,8,List.of(
                    new BankConstruction.Cell(origin.asLong(),"minecraft:air","minecraft:oak_planks")));
            require(economy.reserveBankConstruction(99117,plan),"fixture authority");
            ledger.begin(economy,job,null,99117,origin.asLong(),true);
            BlockPos mine=origin,blast=origin.east(2),chest=origin.east(4),fall=origin.east(7).above(3);
            for(var p:List.of(mine,blast)) {
                ledger.claim(job,p,Blocks.OAK_PLANKS.defaultBlockState(),false);
                level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),Block.UPDATE_ALL);
            }
            var pick=new ItemStack(Items.DIAMOND_PICKAXE);
            pick.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
            require(Block.getDrops(level.getBlockState(mine),level,mine,null,null,pick).isEmpty(),"silk touch cannot harvest unfinished blocks");
            var area=new AABB(Vec3.atLowerCornerOf(origin.offset(-2,-1,-2)),Vec3.atLowerCornerOf(origin.offset(13,6,3)));
            int initial=level.getEntitiesOfClass(ItemEntity.class,area).size();
            require(level.destroyBlock(mine,true)&&level.getBlockState(mine).isAir(),"breaking actually removes the block");
            require(level.getEntitiesOfClass(ItemEntity.class,area).size()==initial,"breaking emits no items");
            var explosion=new ServerExplosion(level,null,null,null,Vec3.atCenterOf(blast),3,false,Explosion.BlockInteraction.DESTROY);
            var drops=new ArrayList<ItemStack>();
            level.getBlockState(blast).onExplosionHit(level,blast,explosion,(stack,p)->drops.add(stack));
            require(level.getBlockState(blast).isAir()&&drops.isEmpty(),"explosion destroys construction without drops");

            ledger.claim(job,chest,Blocks.CHEST.defaultBlockState(),true);
            level.setBlock(chest,Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);
            var storage=(RandomizableContainerBlockEntity)level.getBlockEntity(chest);
            require(storage!=null&&storage.getLootTable()==null&&!storage.canOpen(null),"new chest is empty and locked");
            require(!storage.canPlaceItem(0,new ItemStack(Items.APPLE)),"insertion rejected, not swallowed");
            require(HopperBlockEntity.getContainerAt(level,chest)==null,"hopper cannot insert or extract");
            require(level.destroyBlock(chest,true)&&level.getEntitiesOfClass(ItemEntity.class,area).size()==initial,"empty construction chest breaks without items");
            ledger.claim(job,chest,Blocks.CHEST.defaultBlockState(),true);
            level.setBlock(chest,Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);
            require(!PistonBaseBlock.isPushable(Blocks.OAK_PLANKS.defaultBlockState(),level,mine,Direction.EAST,true,Direction.EAST),"piston cannot move owned blocks");
            require(!PistonBaseBlock.isPushable(Blocks.STONE.defaultBlockState(),level,mine.west(),Direction.EAST,true,Direction.EAST),"piston cannot insert unrelated blocks into reserved cells");

            ledger.claim(job,fall,Blocks.SAND.defaultBlockState(),false);
            level.setBlock(fall,Blocks.SAND.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            var falling=FallingBlockEntity.fall(level,fall,Blocks.SAND.defaultBlockState()); cleanup.add(falling);
            require(!falling.dropItem,"falling construction material cannot export items");
            for(int tick=0;tick<100&&falling.isAlive();tick++) falling.tick();
            require(!level.getBlockState(origin.east(7)).is(Blocks.SAND)
                    &&level.getEntitiesOfClass(ItemEntity.class,area).size()==initial,"falling block neither lands as harvestable sand nor drops");

            BlockPos ore=origin.east(11);
            ledger.claim(job,ore,Blocks.COAL_ORE.defaultBlockState(),false);
            level.setBlock(ore,Blocks.COAL_ORE.defaultBlockState(),Block.UPDATE_ALL);
            int xp=level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,area).size();
            level.getBlockState(ore).spawnAfterBreak(level,ore,new ItemStack(Items.DIAMOND_PICKAXE),true);
            require(level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,area).size()==xp,"unfinished ore emits no mining XP");
            ledger.protectPrefix(job,17);
            var tag=ConstructionOwnership.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,ledger).getOrThrow();
            var decoded=ConstructionOwnership.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow();
            require(decoded.sites.get(job).equals(ledger.sites.get(job)) && decoded.handoverFloor(job)==17,
                    "ownership/reservations/loot eligibility and immutable handover prefix survive codec reload");
            require(ledger.prepareHandover(level,job,VillageStructureLoot.key("bank")),"one-shot handover loot prepared");
            storage=(RandomizableContainerBlockEntity)level.getBlockEntity(chest);
            require(storage.getLootTable()==null,"even a prepared handover keeps storage empty until durable completion");
            require(economy.markGeneratedBankRegion(99117,origin.asLong(),null,8),"durable handover authority");
            ledger.grantHandoverLoot(level,job,VillageStructureLoot.key("bank"));
            require(storage.getLootTable()!=null,"initial loot arrives after durable handover");
            storage.getItem(0); storage.clearContent(); // Simulate a previously issued roll, never refill.
            require(ledger.prepareHandover(level,job,VillageStructureLoot.key("bank")),"repeated preparation remains valid");
            ledger.grantHandoverLoot(level,job,VillageStructureLoot.key("bank"));
            require(storage.getLootTable()==null&&storage.isEmpty(),"repeated/restarted handover cannot refill");
            ledger.finish(job);
            level.setBlock(mine,Blocks.OAK_PLANKS.defaultBlockState(),Block.UPDATE_ALL);
            require(!Block.getDrops(level.getBlockState(mine),level,mine,null).isEmpty(),"ordinary drops return after handover");
            require(storage.canPlaceItem(0,new ItemStack(Items.APPLE))&&HopperBlockEntity.getContainerAt(level,chest)!=null,
                    "normal container use and automation return after handover");
            BlockPos foreign=origin.east(10);
            level.setBlock(foreign,Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);
            var supplied=(RandomizableContainerBlockEntity)level.getBlockEntity(foreign);
            supplied.setItem(0,new ItemStack(Items.APPLE,3));
            require(!ConstructionOwnership.owned(level,foreign,level.getBlockState(foreign))
                    &&supplied.getItem(0).getCount()==3,"unrelated player property never enrolled or erased");
            System.out.println("PASS ConstructionOwnershipSelfTest: mining/silk touch, explosions, chest break, hoppers, piston import/export, falling blocks, codec reload, one-shot handover and ordinary drops");
        } catch(Exception e) {throw new IllegalStateException("Construction ownership regression",e);}
        finally {
            ledger.finish(job); cleanup.forEach(net.minecraft.world.entity.Entity::discard);
            before.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static void require(boolean ok,String detail) {if(!ok)throw new IllegalStateException(detail);}
}
