package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Live worksite safety, deliberately separate from permanent lot-admission/protection checks. */
final class VillageConstructionOccupancy {
    private static final double EPSILON = 0.001;
    private VillageConstructionOccupancy() { }

    static boolean mayChange(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
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
        for (LivingEntity entity : occupants) {
            AABB body = entity.getBoundingBox();
            if (newShape.stream().anyMatch(box -> box.intersects(body.deflate(EPSILON)))) return false;
            if (!after.getFluidState().isEmpty() && new AABB(pos).intersects(body.deflate(EPSILON))) return false;
            // Do not excavate/lower a floor underneath somebody's feet. Ordinary adjacent work is fine.
            for (AABB oldFloor : oldShape)
                if (supports(oldFloor, body) && newShape.stream().noneMatch(box -> supports(box, body)
                        && box.maxY >= oldFloor.maxY - EPSILON)) return false;
        }
        return true;
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
