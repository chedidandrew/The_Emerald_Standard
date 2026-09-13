package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Exact frozen-history, shape preservation and exterior planting regression; no world writes. */
final class VillageBankVersionSevenSelfTest {
    private VillageBankVersionSevenSelfTest() {
    }

    // Captured before v7 implementation from ordered v2-v6 production plans at (0,0,0).
    private static final Map<BiomeDialect, List<String>> FROZEN = Map.of(
            BiomeDialect.PLAINS, List.of(
                    "6febc093929deda79e62a1812d3f9a194ae666eb5732c3b2808d5bf3256ce2b6",
                    "3eef2b639d64f9f9189b6d2292996ef2a26ef8af0207a0a72a7963ed0b925edc",
                    "dd8be3d0b3826578717d51bc6293a7e8a2e6dbf44f058f4ca91e7e8fa9efd3c1",
                    "ca8d5d42a50c57cd56ac8cbff34fa0d12a78f92dce96f63b6567f464075be5c8",
                    "f303569eb92cd3e976bf02d8fd686c64424d41a0a0f389c52423efd6c393b052"),
            BiomeDialect.DESERT, List.of(
                    "e0f6388f8800533a608a42b43712694c470e969a3ede829895a4e0ca6e500d1b",
                    "6f1d3e5abe54a7855bc5e17cf99ea5cf674b2a7481314b0e8ad157f78b01c416",
                    "f3bd0287625e4ea935c88e0db00dd4cf03efa87797941b90c57f7df710db5c91",
                    "bda2b67b245174302dc53562dc0da2d6910e04e4580584b1ea660ee643ce64c6",
                    "3ae660ad121a50e48e46dba624184f0c5ff750d47f995ad0c94feb697f84a0a0"),
            BiomeDialect.SAVANNA, List.of(
                    "da289e71ca86fc6f31e80eb08d4c6d33216b863d1790f7ed1386a356d631b81d",
                    "9554f7105008ecc67f22db22880fbe3d16a0e4c7955d3278d2ccb0093f1f1f44",
                    "8559174a74eec05125f7a003e573cc3d82c5b2345071d4c326fb3d8a7d2726ad",
                    "48a93536bfe8b4ba30269a27bee2fac35a7c5c79e1f812b73e6ffb337bdba606",
                    "59ce66467c148121984c62c132ae68ce5734277171cee5147f8b2a6dbf59010d"),
            BiomeDialect.TAIGA, List.of(
                    "be19e11978aa5fa01d9fe5a1a0fcf0b069d79ac7d1cb33f8ebfebc00c69224d6",
                    "7af1576ad990544e3c17d70de0dd250c7f3d82fce5341d8e82f0b5d884822d19",
                    "6a995b57181906706f7db4d34c1344069be69e3728015500e89b66d16c80450f",
                    "1f75a913fdb5e9166da4f1ac459bed4e3ab01de1c0c38e17e796d2ecc6a0e249",
                    "abc7d8b32d70de13a7a5932a8b4d6ddda95f976d89d8ab01518a0cd171348e15"),
            BiomeDialect.SNOWY, List.of(
                    "ac421ae8dfe44d5459633d23266138a442a1aba82b5bb999afa0866a8d6494e7",
                    "30973b2c6d89190f0a304852f13c9a1ca6fbdf90bdf2b56039b00747e10e6d5f",
                    "74f544db6fc207d520ea19a6a2453770d2bdebb00fa26cee301e79ec1cb38fad",
                    "9e35e2fe316d6e3184824e238a4aed9e575172d9eb0eb12ab27bb06fc19aa2df",
                    "926c1e20023ca0b836ea8ac9b497458ff6f82e176ab2a5cfc305e748ed8dc437"));

    static void run() throws Exception {
        VillageBankBenchSelfTest.run();
        Method paletteFor = method("paletteFor", BiomeDialect.class);
        for (BiomeDialect dialect : BiomeDialect.values()) {
            Object oldPalette = paletteFor.invoke(null, dialect);
            for (int version = 2; version <= 6; version++) {
                List<?> old = plan("legacyBankPlanV" + version, oldPalette);
                require(fingerprint(old).equals(FROZEN.get(dialect).get(version - 2)),
                        "Frozen Bank " + version + '/' + dialect + " generated-state hash changed");
            }
            Object palette = method("bankV7Palette", oldPalette.getClass()).invoke(null, oldPalette);
            require(palette.equals(method("bankV7Palette", palette.getClass()).invoke(null, palette)),
                    "V7 palette projection is not idempotent: " + dialect);
            Map<BlockPos, BlockState> before = cells(plan("legacyBankPlanV6", oldPalette));
            Map<BlockPos, BlockState> after = cells(plan("legacyBankPlanV7", oldPalette));
            Map<BlockPos, BlockState> latest = cells(plan("legacyBankPlanV8", oldPalette));
            require(after.keySet().equals(latest.keySet()), "Bank v8 changed its footprint");
            int slimCourses = 0;
            for (var entry : after.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState next = latest.get(pos);
                boolean brickTip = pos.getX() == 2 && pos.getZ() == 7
                        && pos.getY() >= 9 && pos.getY() <= 10 && entry.getValue().is(Blocks.BRICKS);
                require(brickTip ? next.is(Blocks.BRICK_WALL) : entry.getValue().equals(next),
                        "Bank v8 chimney-only contract failed: " + dialect + '/' + pos);
                if (brickTip) slimCourses++;
            }
            require(slimCourses == (dialect == BiomeDialect.DESERT || dialect == BiomeDialect.SNOWY ? 0 : 2),
                    "Bank brick chimney upper-half coverage changed: " + dialect);
            System.out.println("PASS Bank v8 " + dialect + ": " + slimCourses + " slim chimney courses; all other cells unchanged");
            Map<BlockPos, BlockState> versionNine = cells(plan("legacyBankPlanV9", oldPalette));
            Map<BlockPos, BlockState> versionTen = cells(plan("legacyBankPlanV10", oldPalette));
            require(versionNine.keySet().equals(versionTen.keySet()), "Bank roof fix changed the footprint");
            int roofChanges = 0;
            for (var entry : versionNine.entrySet()) {
                BlockState next = versionTen.get(entry.getKey()), previous = entry.getValue();
                if (previous.equals(next)) continue;
                roofChanges++;
                require(entry.getKey().getY() >= 5 && previous.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                        && previous.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM
                        && next.equals(previous.setValue(net.minecraft.world.level.block.StairBlock.FACING,
                                previous.getValue(net.minecraft.world.level.block.StairBlock.FACING).getOpposite())),
                        "Bank v10 altered a non-roof cell: " + entry.getKey());
            }
            require(roofChanges > 100, "Bank slope coverage missing");
            System.out.println("PASS Bank v10 " + dialect + ": " + roofChanges + " roof stairs corrected; all other cells unchanged");
            Map<BlockPos, BlockState> expectedNine = new LinkedHashMap<>(latest);
            for (int x = 5; x <= 7; x++) {
                BlockState removed = expectedNine.remove(new BlockPos(x, 1, 1));
                require(removed != null && removed.is(Blocks.CARPET.green()),
                        "Bank v8 original doorway carpet missing: " + dialect);
            }
            require(versionNine.equals(expectedNine),
                    "Bank v9 must change only the three doorway carpet cells: " + dialect);
            System.out.println("PASS Bank v9 " + dialect + ": three doorway carpets set back; every other cell unchanged");
            require(after.size() == before.size() + 16, "Four small planters did not fit: " + dialect);
            require(after.get(new BlockPos(-1, 5, -1)).is(switch (dialect) {
                case DESERT -> Blocks.ACACIA_STAIRS;
                case TAIGA -> Blocks.DEEPSLATE_TILE_STAIRS;
                default -> Blocks.DARK_OAK_STAIRS;
            }), "Bank complementary roof finish was not emitted: " + dialect);
            require(after.get(new BlockPos(-1, 4, -1)).is(switch (dialect) {
                case DESERT -> Blocks.ACACIA_PLANKS;
                case TAIGA -> Blocks.DEEPSLATE_TILES;
                default -> Blocks.DARK_OAK_PLANKS;
            }), "Bank roof deck/eave does not match its roof: " + dialect);
            int recoloured = 0;
            for (var entry : before.entrySet()) {
                BlockPos position = entry.getKey();
                BlockState old = entry.getValue(), current = after.get(position);
                require(current != null && old.getCollisionShape(EmptyBlockGetter.INSTANCE, position)
                                .toAabbs().equals(current.getCollisionShape(EmptyBlockGetter.INSTANCE, position).toAabbs())
                                && old.getLightEmission() == current.getLightEmission(),
                        "Bank v7 changed existing geometry or emission at " + dialect + '/' + position);
                if (!old.equals(current)) {
                    recoloured++;
                }
            }
            require(dialect == BiomeDialect.PLAINS || recoloured > 50,
                    "Complementary roof palette was not actually emitted: " + dialect);
            Method plantingGate = method("validateBankV7ExteriorGardens", Map.class, palette.getClass());
            plantingGate.invoke(null, after, palette);
            var broken = new LinkedHashMap<>(after);
            broken.put(new BlockPos(-1, 0, 0), Blocks.STONE_BRICK_SLAB.defaultBlockState());
            expectRejected(plantingGate, broken, palette, "half-slab planter footing");
            broken = new LinkedHashMap<>(after);
            broken.remove(new BlockPos(-1, 0, 0));
            expectRejected(plantingGate, broken, palette, "floating planter");
            broken = new LinkedHashMap<>(after);
            broken.put(new BlockPos(-1, 1, 1), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false));
            expectRejected(plantingGate, broken, palette, "decaying leaf decoration");
            broken = new LinkedHashMap<>(after);
            broken.put(new BlockPos(6, 1, -3), Blocks.TARGET.defaultBlockState());
            expectRejected(plantingGate, broken, palette, "non-training target");
            verifyOccupiedCandidateSkip(palette);
            System.out.println("PASS Bank v7 " + dialect + ": " + recoloured
                    + " material changes, 4 grounded paired planters, unchanged existing shapes/emission");
        }
        System.out.println("PASS Bank v2-v6 exact ordered production snapshots (25 version/dialect plans)");
    }

    private static void verifyOccupiedCandidateSkip(Object palette) throws Exception {
        Map<BlockPos, BlockState> occupied = cells(plan("legacyBankPlanV6", palette));
        BlockPos obstacle = new BlockPos(-1, 2, 0);
        occupied.put(obstacle, Blocks.STONE_BRICKS.defaultBlockState());
        method("appendBankV7ExteriorGardens", Map.class, BlockPos.class, palette.getClass())
                .invoke(null, occupied, BlockPos.ZERO, palette);
        require(occupied.get(obstacle).is(Blocks.STONE_BRICKS)
                        && !occupied.containsKey(new BlockPos(-1, 0, 0))
                        && !occupied.containsKey(new BlockPos(-1, 1, 1)),
                "An occupied planter candidate was overwritten or partially installed");
    }

    private static List<?> plan(String name, Object palette) throws Exception {
        return (List<?>) method(name, BlockPos.class, palette.getClass()).invoke(null, BlockPos.ZERO, palette);
    }

    private static Map<BlockPos, BlockState> cells(List<?> plan) throws Exception {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (Object cell : plan) {
            Method position = cell.getClass().getDeclaredMethod("position");
            Method state = cell.getClass().getDeclaredMethod("state");
            position.setAccessible(true);
            state.setAccessible(true);
            result.put((BlockPos) position.invoke(cell), (BlockState) state.invoke(cell));
        }
        return result;
    }

    private static String fingerprint(List<?> plan) throws Exception {
        StringBuilder serialized = new StringBuilder();
        // Preserve ordered placements rather than collapsing duplicate cells in older plans.
        for (Object cell : plan) {
            var entry = cells(List.of(cell)).entrySet().iterator().next();
            BlockPos position = entry.getKey();
            BlockState state = entry.getValue();
            if (BankerProfessionSupport.isExchangeDesk(state)) {
                // The sole registry-sensitive slot is equivalent to the baseline's lectern proxy.
                state = Blocks.LECTERN.defaultBlockState();
            }
            serialized.append(position.getX()).append(',').append(position.getY()).append(',')
                    .append(position.getZ()).append('=').append(state).append('\n');
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(serialized.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static Method method(String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = VillageBankManager.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static void expectRejected(Method gate, Map<BlockPos, BlockState> cells, Object palette,
            String issue) throws Exception {
        try {
            gate.invoke(null, cells, palette);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof IllegalStateException) {
                return;
            }
            throw failure;
        }
        throw new IllegalStateException("Bank planting gate accepted " + issue);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
