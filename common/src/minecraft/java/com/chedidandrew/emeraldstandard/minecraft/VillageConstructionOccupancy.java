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
import net.minecraft.world.entity.player.Player;
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
            // Ten seconds at this particular work cell; no permission carries over to another cell/entity.
            // Do not explicitly damage or delete entities. Normal block collision handles the fallback.
            if (now - wait.first < 200) clear = false;
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
        if (entity.isPassenger() || entity.isVehicle()) return;
        for (double distance : new double[]{1.25, 2.25, 3.25}) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double x = entity.getX() + Math.cos(angle) * distance;
                double z = entity.getZ() + Math.sin(angle) * distance;
                AABB destination = entity.getBoundingBox().move(x - entity.getX(), 0, z - entity.getZ());
                if (destination.intersects(new AABB(work).inflate(0.1, 1, 0.1))) continue;
                BlockPos feet = BlockPos.containing(x, entity.getY(), z);
                if (!level.hasChunksAt(BlockPos.containing(destination.minX, destination.minY - 1, destination.minZ),
                        BlockPos.containing(destination.maxX, destination.maxY, destination.maxZ))) continue;
                if (!level.getBlockState(feet.below()).isSolidRender()
                        || level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        || !level.getFluidState(feet).isEmpty()
                        || !level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()
                        || !level.noCollision(entity, destination)) continue;
                entity.setPos(x, entity.getY(), z);
                return;
            }
        }
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
