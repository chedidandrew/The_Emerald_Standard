package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Disposable-world test: safe movement, persistent waits and unconditional player protection. */
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
            // Final neighbor-shape expansion is an authorized write, and must use the same
            // safe creature clearance instead of waiting forever on a stationary occupant.
            var fence=Blocks.OAK_FENCE.defaultBlockState();
            level.setBlock(origin,fence,18);level.setBlock(origin.east(),stone,18);
            cow.setPos(origin.getX()+.8,origin.getY(),origin.getZ()+.5);
            var joined=net.minecraft.world.level.block.Block.updateFromNeighbourShapes(fence,level,origin);
            VillageConstructionOccupancy.reset();
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,fence,joined,level.getGameTime()-20),"occupied final connection bypassed wait");
            check(VillageProsperityManager.normalizeModularConnectionState(level,origin,fence,null,66)
                    &&level.getBlockState(origin).equals(joined)&&cow.isAlive(),"final connection did not safely clear creature");
            level.setBlock(origin,air,18);level.setBlock(origin.east(),air,18);
            // Remove alternative footing: do not teleport into unsupported air.
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)
                if(x!=0||z!=0)level.setBlock(origin.offset(x,-1,z),air,18);
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            VillageConstructionOccupancy.reset();
            for(int tick=0;tick<200;tick+=20)
                check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,tick),"early bypass");
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,200),"timeout authorized occupied placement");
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,1000),"long wait authorized occupied placement");
            check(cow.isAlive(),"clearance explicitly killed creature");
            check(!VillageConstructionOccupancy.mayChange(level,origin,air,stone),"survey inherited build override");
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin.above(),air,stone,200),"new cell inherited override");
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,500),"inactive wait inherited override");
            // Reproduce a mob below surrounding ground: no same-height landing exists.
            BlockPos landing=origin.offset(6,2,0);
            set(level,saved,landing,stone);
            cow.setDeltaMovement(1,-2,1);
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            VillageConstructionOccupancy.reset();
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0),"fallback skipped initial grace");
            check(VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20),"sunken mob fallback did not clear cell");
            check(cow.blockPosition().equals(landing.above())&&cow.isAlive()
                    &&cow.getDeltaMovement().lengthSqr()==0,"raised landing or motion unsafe");
            check(VillageConstructionOccupancy.setBlock(level,origin,stone,18)
                    &&level.getBlockState(origin).is(Blocks.STONE),"cleared cell still could not be built");
            level.setBlock(origin,air,18);
            set(level,saved,landing,air);
            landing=origin.offset(12,-4,0);
            set(level,saved,landing,stone);
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            VillageConstructionOccupancy.reset();
            VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0);
            check(VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20)
                    &&cow.blockPosition().equals(landing.above()),"far lower landing not found");
            set(level,saved,landing,air);
            landing=origin.offset(6,2,0);
            for(var unsafe:List.of(Blocks.MAGMA_BLOCK,Blocks.CACTUS,Blocks.CAMPFIRE,Blocks.POWDER_SNOW,Blocks.WATER)) {
                set(level,saved,landing,unsafe.defaultBlockState());
                cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
                VillageConstructionOccupancy.reset();
                VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0);
                check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20),"hazard landing accepted: "+unsafe);
            }
            set(level,saved,landing,stone);
            // A lone ceiling block has a safe top within the vertical search. Fill the
            // entire tested height so this negative fixture has no alternative rooftop.
            for(int dy=1;dy<=5;dy++)set(level,saved,landing.above(dy),stone);
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            VillageConstructionOccupancy.reset();
            VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0);
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20),"blocked body landing accepted");
            for(int dy=1;dy<=5;dy++)set(level,saved,landing.above(dy),air);
            try {
                var land=VillageConstructionOccupancy.class.getDeclaredMethod("land",ServerLevel.class,BlockPos.class,
                        LivingEntity.class,double.class,double.class,double.class);land.setAccessible(true);
                int unloadedX=25000,unloadedZ=25000;
                check(level.getChunkSource().getChunkNow(unloadedX>>4,unloadedZ>>4)==null,"fixture target already loaded");
                check(!(boolean)land.invoke(null,level,origin,cow,unloadedX+.5,(double)origin.getY(),unloadedZ+.5)
                        &&level.getChunkSource().getChunkNow(unloadedX>>4,unloadedZ>>4)==null,"fallback loaded a chunk");
                player.setPos(landing.getX()+.5,landing.getY()+1,landing.getZ()+.5);level.players().add(player);
                check(!(boolean)land.invoke(null,level,origin,cow,landing.getX()+.5,(double)landing.getY()+1,landing.getZ()+.5),
                        "landing occupied by transitioning player accepted");
                level.players().remove(player);
                // The broad mob footprint must be wholly supported, not just its center.
                var broad=EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.COMMAND);
                check(broad!=null,"large mob fixture");broad.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
                check(!(boolean)land.invoke(null,level,origin,broad,landing.getX()+.5,(double)landing.getY()+1,landing.getZ()+.5),
                        "large mob placed on center-only footing");
                broad.discard();
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            for(var aquaticType:List.of(EntityTypes.COD,EntityTypes.SQUID,EntityTypes.DOLPHIN,EntityTypes.AXOLOTL)) {
                var aquatic=aquaticType.create(level,EntitySpawnReason.COMMAND);
                check(aquatic!=null,"aquatic fixture");
                try {
                    cow.setPos(origin.getX()+20,origin.getY(),origin.getZ()+20);
                    aquatic.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
                    check(level.addFreshEntity(aquatic),"aquatic fixture not tracked");
                    VillageConstructionOccupancy.reset();
                    VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0);
                    check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20)
                            &&aquatic.blockPosition().equals(origin),"water-dependent creature moved onto dry ground");
                }finally {aquatic.discard();}
            }
            cow.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
            cow.setLeashedTo(player,true);
            VillageConstructionOccupancy.reset();
            VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,0);
            check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,20)
                    &&cow.blockPosition().equals(origin),"leashed mob moved away from its holder");
            cow.discard();
            player.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);level.players().add(player);
            for(int tick=600;tick<=1000;tick+=20)
                check(!VillageConstructionOccupancy.mayBuildAt(level,origin,air,stone,tick),"player bypassed");
            check(player.getX()==origin.getX()+.5,"player moved");
            System.out.println("PASS construction safe creature clearance: sunken/far/lower landings, hazards, full body/footprint, loaded-only fallback, aquatic/leash safety, no timeout bypass and player protection");
        } finally {
            level.players().remove(player);
            if(cow!=null)cow.discard();
            saved.forEach((pos,state)->level.setBlock(pos,state,18));
            VillageConstructionOccupancy.reset();
        }
    }
    private static void set(ServerLevel level,Map<BlockPos,BlockState> saved,BlockPos at,BlockState state) {
        level.getChunk(at);saved.putIfAbsent(at,level.getBlockState(at));level.setBlock(at,state,18);
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
