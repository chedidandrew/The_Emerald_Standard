package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Live worksite safety, deliberately separate from permanent lot-admission/protection checks. */
final class VillageConstructionOccupancy {
    private static final double EPSILON = 0.001;
    private record Key(ServerLevel level, BlockPos pos, BlockState before, BlockState after, UUID entity) { }
    private static final class Wait {
        long first, last, moved;
        Wait(long now) { first = last = moved = now; }
    }
    private static final Map<Key, Wait> WAITING = new LinkedHashMap<>();
    private VillageConstructionOccupancy() { }

    static boolean mayChange(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        return check(level, pos, before, after, false, level.getGameTime());
    }

    /** Only call on an authorized write path, never from a survey or site admission check. */
    static boolean mayBuild(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        return mayBuildAt(level, pos, before, after, level.getGameTime());
    }

    static void reset() { WAITING.clear(); }

    static boolean mayBuildAt(ServerLevel level, BlockPos pos, BlockState before, BlockState after, long now) {
        return check(level, pos, before, after, true, now);
    }

    private static boolean check(ServerLevel level, BlockPos pos, BlockState before, BlockState after,
                                 boolean building, long now) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        List<AABB> oldShape = before.getCollisionShape(level, pos).toAabbs().stream()
                .map(box -> box.move(pos)).toList();
        List<AABB> newShape = after.getCollisionShape(level, pos).toAabbs().stream()
                .map(box -> box.move(pos)).toList();
        if (oldShape.isEmpty() && newShape.isEmpty() && after.getFluidState().isEmpty()) return true;
        // Includes tall fence shapes and entities standing on the block's upper surface.
        AABB workArea = new AABB(pos).inflate(EPSILON, 1.0, EPSILON);
        List<LivingEntity> occupants = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                workArea, e -> e.isAlive() && !e.isSpectator()));
        // Also cover players during entity-tracking transitions (login/teleport).
        for (var player : level.players())
            if (player.isAlive() && !player.isSpectator() && workArea.intersects(player.getBoundingBox())
                    && !occupants.contains(player)) occupants.add(player);
        List<LivingEntity> blockers = new ArrayList<>();
        for (LivingEntity entity : occupants) {
            AABB body = entity.getBoundingBox();
            boolean blocked = newShape.stream().anyMatch(box -> box.intersects(body.deflate(EPSILON)))
                    || (!after.getFluidState().isEmpty() && new AABB(pos).intersects(body.deflate(EPSILON)));
            // Do not excavate/lower a floor underneath somebody's feet. Ordinary adjacent work is fine.
            for (AABB oldFloor : oldShape)
                if (supports(oldFloor, body) && newShape.stream().noneMatch(box -> supports(box, body)
                        && box.maxY >= oldFloor.maxY - EPSILON)) blocked = true;
            if (blocked) blockers.add(entity);
        }
        if (!building) return blockers.isEmpty();
        WAITING.entrySet().removeIf(e -> e.getKey().level == level && (now < e.getValue().last
                || now - e.getValue().last > 200
                || (e.getKey().pos.equals(pos) && blockers.stream().noneMatch(b -> b.getUUID().equals(e.getKey().entity)))));
        // Check every player before moving anything, including vehicles with player passengers.
        if (blockers.stream().anyMatch(VillageConstructionOccupancy::carriesPlayer)) return false;
        boolean clear = true;
        for (LivingEntity entity : blockers) {
            Key key = new Key(level, pos.immutable(), before, after, entity.getUUID());
            if (WAITING.size() >= 4096 && !WAITING.containsKey(key)) WAITING.remove(WAITING.keySet().iterator().next());
            Wait wait = WAITING.computeIfAbsent(key, ignored -> new Wait(now));
            wait.last = now;
            if (now - wait.moved >= 20) {
                wait.moved = now;
                moveAside(level, pos, entity);
            }
            // Time waiting never authorizes suffocation or removing occupied footing.
            // A successful safe move may clear this cell immediately; otherwise keep waiting.
            if (!mayChange(level, pos, before, after)) clear = false;
        }
        return clear;
    }

    private static boolean carriesPlayer(Entity entity) {
        Entity root = entity.getRootVehicle();
        if (root instanceof Player) return true;
        for (Entity passenger : root.getIndirectPassengers()) if (passenger instanceof Player) return true;
        return false;
    }

    private static void moveAside(ServerLevel level, BlockPos work, LivingEntity entity) {
        // Moving a ridden/leashed group blindly risks dragging another entity into the work area.
        if (entity.isPassenger() || entity.isVehicle()
                || entity instanceof Leashable leash && leash.isLeashed()) return;
        // Dry ground is not a safe rescue destination for water-dependent creatures.
        if(entity instanceof net.minecraft.world.entity.animal.fish.WaterAnimal
                ||entity instanceof net.minecraft.world.entity.animal.AgeableWaterCreature
                ||entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl)return;
        for (double distance : new double[]{1.25, 2.25, 3.25}) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double x = entity.getX() + Math.cos(angle) * distance;
                double z = entity.getZ() + Math.sin(angle) * distance;
                if (land(level, work, entity, x, entity.getY(), z)) return;
            }
        }
        // A creature can be trapped in a sunken unfinished floor: horizontal-only moves
        // cannot find the surrounding ground. Bounded, loaded-only fallback, nearest first.
        // Still called at most once per second per unchanged occupied work cell.
        for (int distance : new int[]{4, 6, 8, 12, 16, 24}) for (int i=0; i<8; i++) {
            double angle=i*Math.PI/4;
            double x=Math.floor(entity.getX()+Math.cos(angle)*distance)+0.5;
            double z=Math.floor(entity.getZ()+Math.sin(angle)*distance)+0.5;
            int baseY=(int)Math.floor(entity.getY());
            for (int step=0; step<=12; step++) {
                int dy=step==0?0:(step%2==1?(step+1)/2:-step/2);
                if (land(level,work,entity,x,baseY+dy,z)) return;
            }
        }
    }

    private static boolean land(ServerLevel level, BlockPos work, LivingEntity entity,
                                double x, double y, double z) {
        AABB destination=entity.getBoundingBox().move(x-entity.getX(),y-entity.getY(),z-entity.getZ());
        if(destination.intersects(new AABB(work).inflate(0.1,1,0.1)))return false;
        // Check the complete footprint and body, not only a center block. Never load a chunk.
        int minX=(int)Math.floor(destination.minX+EPSILON),maxX=(int)Math.floor(destination.maxX-EPSILON);
        int minZ=(int)Math.floor(destination.minZ+EPSILON),maxZ=(int)Math.floor(destination.maxZ-EPSILON);
        int floorY=(int)Math.floor(destination.minY-EPSILON),topY=(int)Math.floor(destination.maxY-EPSILON);
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)
            if(level.getChunkSource().getChunkNow(cx,cz)==null)return false;
        for(int bx=minX;bx<=maxX;bx++)for(int bz=minZ;bz<=maxZ;bz++) {
            BlockPos floor=new BlockPos(bx,floorY,bz);
            if(!level.isInWorldBounds(floor)||!level.getWorldBorder().isWithinBounds(floor)
                    ||!level.getBlockState(floor).isSolidRender()||hazard(level.getBlockState(floor))
                    ||ConstructionOwnership.reserved(level,floor))return false;
            // A partially supported large mob must not be left hanging over a pit.
            if(Math.abs(destination.minY-(floorY+1))>EPSILON)return false;
            for(int by=floorY+1;by<=topY;by++) {
                BlockPos body=new BlockPos(bx,by,bz);
                if(!level.isInWorldBounds(body)||!level.getWorldBorder().isWithinBounds(body)
                        ||!level.getFluidState(body).isEmpty()||hazard(level.getBlockState(body))
                        ||ConstructionOwnership.reserved(level,body))return false;
            }
        }
        if(!level.noCollision(entity,destination)
                ||!level.getEntitiesOfClass(LivingEntity.class,destination,e->e!=entity&&e.isAlive()&&!e.isSpectator()).isEmpty()
                ||level.players().stream().anyMatch(p->p!=entity&&p.isAlive()&&!p.isSpectator()
                        &&destination.intersects(p.getBoundingBox())))return false;
        if(entity instanceof Mob mob)mob.getNavigation().stop();
        entity.setPos(x,y,z);
        entity.setDeltaMovement(0,0,0);
        entity.resetFallDistance();
        return true;
    }

    private static boolean hazard(BlockState state) {
        return !state.getFluidState().isEmpty()||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)
                ||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.CAMPFIRE)
                ||state.is(Blocks.SOUL_CAMPFIRE)||state.is(Blocks.SWEET_BERRY_BUSH)
                ||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.WITHER_ROSE);
    }

    private static boolean supports(AABB block, AABB body) {
        return Math.abs(body.minY - block.maxY) <= 0.08
                && body.maxX > block.minX + EPSILON && body.minX < block.maxX - EPSILON
                && body.maxZ > block.minZ + EPSILON && body.minZ < block.maxZ - EPSILON;
    }

    static boolean setBlock(ServerLevel level, BlockPos pos, BlockState after, int flags) {
        return mayChange(level, pos, level.getBlockState(pos), after) && level.setBlock(pos, after, flags);
    }
}
