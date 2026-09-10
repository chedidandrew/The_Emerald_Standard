package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Opt-in smoke checks: a real villager navigates and physically travels, never teleports mid-route. */
final class VillageWalkingSelfTest {
    static Villager walker(ServerLevel level, BlockPos start) {
        Villager walker = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (walker == null) throw new IllegalStateException("Cannot create walking fixture");
        walker.setPos(start.getX() + .5, start.getY(), start.getZ() + .5);
        walker.setOnGround(true);
        return walker;
    }

    static void walk(ServerLevel level, Villager walker, BlockPos goal, String label) {
        var path = walker.getNavigation().createPath(goal, 0);
        if (path == null || !path.canReach()) throw new IllegalStateException("No villager path: " + label + " to " + goal);
        double startX = walker.getX(), startZ = walker.getZ();
        walker.getBrain().stopAll(level, walker);
        walker.getNavigation().stop();
        walker.getBrain().eraseMemory(MemoryModuleType.PATH);
        walker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goal, .65f, 0));
        long originalTime = level.getGameTime();
        var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        try {
        for (int tick = 0; tick < 1000; tick++) {
            // Isolated synchronous server fixture: run the entity's real AI/navigation/physics.
            clock.setGameTime(originalTime + tick + 1);
            if (tick > 0 && tick % 80 == 0) {
                walker.getBrain().stopAll(level, walker);
                walker.getNavigation().stop();
                walker.getBrain().eraseMemory(MemoryModuleType.PATH);
                walker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goal, .65f, 0));
            }
            walker.tick();
            double dx = walker.getX() - goal.getX() - .5, dz = walker.getZ() - goal.getZ() - .5;
            if (dx * dx + dz * dz < .65 * .65 && Math.abs(walker.getY() - goal.getY()) < 1.1) {
                System.out.println("PASS VillageWalkingSelfTest: " + label + " steps=" + (tick + 1)
                        + " route_retries=" + tick / 80
                        + " displacement=" + Math.hypot(walker.getX() - startX, walker.getZ() - startZ));
                return;
            }
        }
        throw new IllegalStateException("Villager did not physically reach " + label + ": " + walker.position() + " -> " + goal);
        } finally {
            walker.getBrain().stopAll(level, walker);
            clock.setGameTime(originalTime);
        }
    }

    static void building(ServerLevel level, BlockPos min, BlockPos max, String label) {
        List<BlockPos> doors = new ArrayList<>(), beds = new ArrayList<>(), jobs = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                doors.add(pos.immutable());
            if (state.getBlock() instanceof BedBlock) beds.add(pos.immutable());
            if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.SMITHING_TABLE)
                    || state.is(Blocks.LECTERN) || state.is(Blocks.FLETCHING_TABLE)
                    || BankerProfessionSupport.isBankWorkstation(state)) jobs.add(pos.immutable());
        }
        if (doors.isEmpty()) throw new IllegalStateException(label + " missing testable doorway");
        BlockPos door = doors.getFirst(), start = null, across = null;
        Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
        for (Direction direction : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos inside = door.relative(direction.getOpposite());
            if (!feet(level, inside)) continue;
            for (int dy = -2; dy <= 1; dy++) {
                BlockPos candidate = door.relative(direction, 3).above(dy);
                if (feet(level, candidate)) { start = candidate; across = inside; break; }
            }
            if (start != null) break;
        }
        if (start == null) throw new IllegalStateException(label + " no approach to doorway " + door
                + " facing=" + facing + " inside=" + level.getBlockState(door.relative(facing.getOpposite()))
                + " outside=" + level.getBlockState(door.relative(facing, 3)));
        Villager walker = walker(level, start);
        try {
            // Navigate through the door to air, not to the closed door block (which target
            // normalization can incorrectly project upward). The villager must open it itself.
            walk(level, walker, across, label + " through doorway");
            if (!beds.isEmpty()) visit(level, walker, beds.getFirst(), label + " bed");
            if (!jobs.isEmpty()) visit(level, walker, jobs.getFirst(), label + " workstation");
            walk(level, walker, start, label + " return to street");
        } finally { walker.discard(); }
    }

    private static void visit(ServerLevel level, Villager walker, BlockPos object, String label) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = object.relative(direction);
            var path = feet(level, beside) ? walker.getNavigation().createPath(beside, 0) : null;
            if (path != null && path.canReach()) { walk(level, walker, beside, label); return; }
        }
        throw new IllegalStateException("No accessible standing cell beside " + label + " at " + object);
    }

    private static boolean feet(ServerLevel level, BlockPos pos) {
        return (level.getBlockState(pos).isAir() || level.getBlockState(pos).getBlock() instanceof CarpetBlock)
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
