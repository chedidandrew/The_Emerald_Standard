package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Disposable-world test: movement, bounded fallback, fresh waits and unconditional player protection. */
final class ConstructionEntityClearanceSelfTest {
    static void verify(ServerLevel level) {
        // Reuse the native-ready chunk admitted by the smoke sequence's creative fixture.
        BlockPos origin=new BlockPos(936,level.getMaxY()-20,936);
        Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        net.minecraft.world.entity.animal.cow.Cow cow=null;
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"ClearanceFixture"),
                ClientInformation.createDefault());
        try {
            VillageConstructionOccupancy.reset();
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=-1;y<=3;y++) {
                BlockPos pos=origin.offset(x,y,z);level.getChunk(pos);saved.put(pos,level.getBlockState(pos));
                level.setBlock(pos,(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
            check(cow!=null,"cow fixture");
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            check(level.addFreshEntity(cow),"cow added");
            check(!level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(origin)).isEmpty(),
                    "cow not tracked in loaded fixture: "+cow.getBoundingBox());
            var air=Blocks.AIR.defaultBlockState();var stone=Blocks.STONE.defaultBlockState();
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0),"immediate bypass");
            VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20);
            check(VillageConstructionOccupancy.mayChange(level,origin,air,stone),"safe movement did not clear work cell");
            // Remove alternative footing: do not teleport into unsupported air.
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)
                if(x!=0||z!=0)level.setBlock(origin.offset(x,-1,z),air,18);
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            VillageConstructionOccupancy.reset();
            for(int tick=0;tick<200;tick+=20)
                check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,tick),"early bypass");
            check(VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,200),"persistent creature never released");
            check(cow.isAlive(),"clearance explicitly killed creature");
            check(!VillageConstructionOccupancy.mayChange(level,origin,air,stone),"survey inherited build override");
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin.above(),air,stone,200),"new cell inherited override");
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,500),"inactive wait inherited override");
            cow.discard();
            player.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);level.players().add(player);
            for(int tick=600;tick<=1000;tick+=20)
                check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,tick),"player bypassed");
            check(player.getX()==origin.getX()+.5,"player moved");
            System.out.println("PASS construction creature clearance, timeout, fresh-cell and player protection");
        } finally {
            level.players().remove(player);
            if(cow!=null)cow.discard();
            saved.forEach((pos,state)->level.setBlock(pos,state,18));
            VillageConstructionOccupancy.reset();
        }
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
