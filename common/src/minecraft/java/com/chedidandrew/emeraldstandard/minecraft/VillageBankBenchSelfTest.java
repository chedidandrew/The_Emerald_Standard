package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Exact six-seat delta plus native collision proof; uses production plans, not copied furniture. */
final class VillageBankBenchSelfTest {
    private static final List<Integer> SEAT_X = List.of(0, 1, 3, 9, 11, 12);

    static void run() throws Exception {
        int checks = 0;
        for (BiomeDialect dialect : BiomeDialect.values()) {
            Object palette = method("paletteFor", BiomeDialect.class).invoke(null, dialect);
            Map<BlockPos, BlockState> original = plan("legacyBankPlanV10", BlockPos.ZERO, palette);
            Map<BlockPos, BlockState> before = plan("legacyBankPlanV11", BlockPos.ZERO, palette);
            Map<BlockPos, BlockState> current = plan("bankPlan", BlockPos.ZERO, palette);
            require(original.equals(current), "Restored plan differs from the original terrace composition");
            require(List.copyOf(before.keySet()).equals(List.copyOf(current.keySet())),
                    "Bench correction changed footprint or placement order: " + dialect);
            int changed = 0;
            for (var entry : before.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState old = entry.getValue(), now = current.get(pos);
                boolean seat = pos.getY() == 1 && pos.getZ() == -4 && SEAT_X.contains(pos.getX());
                if (!seat) {
                    require(old.equals(now), "Non-seat changed: " + dialect + "/" + pos);
                    continue;
                }
                require(old.getBlock() instanceof StairBlock
                                && old.getValue(StairBlock.FACING) == Direction.SOUTH,
                        "Frozen v11 seating changed");
                require(now.equals(old.setValue(StairBlock.FACING, Direction.NORTH)),
                        "Bench correction must change facing only: " + dialect + "/" + pos);
                require(current.get(pos.below()).isFaceSturdy(EmptyBlockGetter.INSTANCE,
                                pos.below(), Direction.UP),
                        "Seat lost its foundation");
                for (Rotation rotation : Rotation.values()) {
                    for (Mirror mirror : Mirror.values()) {
                        BlockState transformed = now.mirror(mirror).rotate(rotation);
                        Direction back = rotation.rotate(mirror.mirror(Direction.NORTH));
                        require(transformed.getValue(StairBlock.FACING) == back, "Bench transform");
                        // Original terrace: high back toward the front path; seat opens toward the Bank.
                        require(occupied(transformed, back, 0.75), "Missing raised bench back");
                        require(!occupied(transformed, back.getOpposite(), 0.75), "Seat opens backwards");
                        require(occupied(transformed, back.getOpposite(), 0.25), "Missing low seat");
                        checks++;
                    }
                }
                changed++;
            }
            require(changed == 6, "Expected six forecourt seat corrections");

            // Production plans use absolute positions. Do not accidentally target world y=1/z=-4.
            BlockPos origin = new BlockPos(-213, 74, 387);
            Map<BlockPos, BlockState> translated = plan("bankPlan", origin, palette);
            for (var entry : current.entrySet()) {
                require(entry.getValue().equals(translated.get(entry.getKey().offset(origin))),
                        "Bench placement is not translation invariant");
            }

            Object projected = method("bankV7Palette", palette.getClass()).invoke(null, palette);
            Method gate = method("validateBankForecourtSeats", Map.class, projected.getClass());
            gate.invoke(null, current, projected);
            var backwards = new LinkedHashMap<>(current);
            BlockPos singleSeat = new BlockPos(3, 1, -4);
            backwards.put(singleSeat, before.get(singleSeat));
            try {
                gate.invoke(null, backwards, projected);
                throw new IllegalStateException("Admission accepted the reported backwards seat");
            } catch (InvocationTargetException rejected) {
                if (!(rejected.getCause() instanceof IllegalStateException)) throw rejected;
            }
        }
        System.out.println("PASS Bank v12 restored terrace benches: six exact seats x five dialects; " + checks
                + " native rotation/mirror seat-shape checks; v10 restored, v11 frozen, all other cells unchanged");
    }

    private static boolean occupied(BlockState state, Direction side, double y) {
        double x = 0.5 + side.getStepX() * 0.25;
        double z = 0.5 + side.getStepZ() * 0.25;
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()
                .stream().anyMatch(box -> box.contains(x, y, z));
    }

    private static Map<BlockPos, BlockState> plan(String name, BlockPos origin, Object palette) throws Exception {
        List<?> cells = (List<?>) method(name, BlockPos.class, palette.getClass()).invoke(null, origin, palette);
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (Object cell : cells) {
            Method position = cell.getClass().getDeclaredMethod("position");
            Method state = cell.getClass().getDeclaredMethod("state");
            position.setAccessible(true);
            state.setAccessible(true);
            result.put((BlockPos) position.invoke(cell), (BlockState) state.invoke(cell));
        }
        return result;
    }

    private static Method method(String name, Class<?>... args) throws Exception {
        Method method = VillageBankManager.class.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
