package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Curated default resources only. Does not invoke jigsaw worldgen, processors or entity spawning. */
final class VanillaVillageBuildings {
    record Entry(String id, String hash, String role) {}
    private static ResourceManager resources;
    private static List<Entry> pending = List.of();
    private static final List<VanillaConstructionPlan> resolved = new ArrayList<>();
    private static final Map<String, String> rejected = new TreeMap<>();
    private static int cursor;
    private static final Map<VanillaConstructionPlan, String> unavailable = new LinkedHashMap<>();
    private static final Map<VanillaConstructionPlan, List<ResolvedCell>> decoded =
            new LinkedHashMap<>();
    record ResolvedCell(BlockPos pos, BlockState state) {}

    static List<Entry> manifest() {
        try (var stream = VanillaVillageBuildings.class.getResourceAsStream(
                "/data/the_emerald_standard/vanilla_buildings.txt")) {
            if (stream == null) throw new IOException("Missing vanilla catalog manifest");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(s -> !s.isBlank() && !s.startsWith("#")).map(s -> {
                        String[] p = s.split("\\|");
                        if (p.length != 3) throw new IllegalArgumentException("Invalid catalog entry");
                        return new Entry(p[0], p[1], p[2]);
                    }).toList();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** One small template per server tick, no chunk reads and no force loading. */
    static void tick(MinecraftServer server) {
        ResourceManager current = server.getResourceManager();
        if (resources != current) {
            resources = current; pending = manifest(); cursor = 0;
            resolved.clear(); rejected.clear(); decoded.clear(); unavailable.clear(); VanillaBuildingCatalog.clear();
        }
        if (cursor >= pending.size()) return;
        Entry entry = pending.get(cursor++);
        try {
            var id = Identifier.parse(entry.id().replace("minecraft:", "minecraft:structure/") + ".nbt");
            byte[] bytes;
            try (var in = current.getResourceOrThrow(id).open()) { bytes = in.readNBytes(1000001); }
            resolved.add(convert(entry, bytes));
        } catch (Exception e) {
            rejected.put(entry.id(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (cursor == pending.size()) VanillaBuildingCatalog.publish(resolved);
    }

    static Map<String, String> diagnostics() { return Map.copyOf(rejected); }

    static boolean canResolve(VanillaConstructionPlan plan) {
        if (plan == null) return true;
        if (unavailable.containsKey(plan)) return false;
        try { cells(plan); return true; }
        catch (RuntimeException failure) {
            if (unavailable.size() >= 64) unavailable.remove(unavailable.keySet().iterator().next());
            unavailable.put(plan, failure.getMessage());
            rejected.put("frozen:" + plan.templateId(), failure.getMessage());
            return false;
        }
    }

    /** Cache immutable decoded states, not entire village records. */
    static List<ResolvedCell> cells(VanillaConstructionPlan plan) {
        var cached = decoded.get(plan);
        if (cached != null) return cached;
        try {
            var result = plan.cells().stream().map(c -> new ResolvedCell(new BlockPos(c.x(), c.y(), c.z()),
                    state(c.state()))).toList();
            if (decoded.size() >= 64) decoded.remove(decoded.keySet().iterator().next());
            decoded.put(plan, result);
            return result;
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Cannot resolve frozen vanilla plan " + plan.templateId(), invalid);
        }
    }

    private static BlockState state(String value) {
        try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, value, false).blockState(); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalArgumentException(value, e); }
    }

    static VanillaConstructionPlan convert(Entry entry, byte[] bytes) throws Exception {
        if (bytes.length > 1000000 || !HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)).equals(entry.hash()))
            throw new IOException("Resource differs from the audited Minecraft 26.2 default");
        CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(4000000L));
        ListTag size = root.getListOrEmpty("size");
        int w = size.getIntOr(0, 0), h = size.getIntOr(1, 0), d = size.getIntOr(2, 0);
        if (w < 1 || w > 32 || h < 1 || h > 32 || d < 1 || d > 32)
            throw new IOException("Unsupported dimensions");
        List<BlockState> palette = new ArrayList<>();
        for (Tag item : root.getListOrEmpty("palette")) {
            CompoundTag p = (CompoundTag) item;
            String name = p.getStringOr("Name", "");
            CompoundTag properties = p.getCompoundOrEmpty("Properties");
            String props = properties.keySet().stream().sorted()
                    .map(k -> k + "=" + properties.getStringOr(k, "")).collect(java.util.stream.Collectors.joining(","));
            palette.add(state(name + (props.isEmpty() ? "" : "[" + props + "]")));
        }
        boolean civicCenter = entry.id().contains("/town_centers/");
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockPos entrance = null; Direction outward = null;
        for (Tag item : root.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag) item;
            ListTag pos = block.getListOrEmpty("pos");
            BlockPos at = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
            int index = block.getIntOr("state", -1);
            if (index < 0 || index >= palette.size()) throw new IOException("Invalid block palette index");
            BlockState value = palette.get(index);
            if (value.is(Blocks.JIGSAW)) {
                CompoundTag data = block.getCompoundOrEmpty("nbt");
                if (data.getStringOr("name", "").equals(civicCenter ? "minecraft:street" : "minecraft:building_entrance")
                        // This misspelling is in the audited vanilla snowy shepherd template.
                        || (entry.id().endsWith("/snowy_shepherds_house_1")
                            && data.getStringOr("name", "").equals("minecraft:buidling_entrance"))) {
                    if (entrance != null && !civicCenter) throw new IOException("Ambiguous building entrance");
                    if (entrance == null) {
                    entrance = at;
                    String direction = value.getValue(net.minecraft.world.level.block.JigsawBlock.ORIENTATION)
                            .front().getSerializedName();
                    outward = Direction.byName(direction);
                    }
                }
                String pool = data.getStringOr("pool", "");
                if (pool.contains("well_bottom") || (!civicCenter && pool.endsWith("/houses")))
                    throw new IOException("Required connected structure not supported");
                value = state(data.getStringOr("final_state", "minecraft:air"));
            }
            // No imported block-entity payload survives. Containers are empty; no loot refresh.
            if (value.is(Blocks.STRUCTURE_VOID) || value.is(Blocks.STRUCTURE_BLOCK)) continue;
            blocks.put(at, value);
        }
        if (entrance == null || outward == null || outward.getAxis().isVertical())
            throw new IOException("No unambiguous horizontal entrance");
        int turns = switch (outward) {
            case NORTH -> 0; case EAST -> 3; case SOUTH -> 2; case WEST -> 1; default -> 0;
        };
        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE;
        };
        int rw = (turns % 2 == 0) ? w : d, rd = (turns % 2 == 0) ? d : w;
        BlockPos front = rotate(entrance, w, d, turns);
        if (front.getZ() > 2) throw new IOException("Entrance needs a separately surveyed approach");
        // Center the real entrance without moving any relative room geometry.
        int half = Math.max(front.getX(), rw - 1 - front.getX());
        int shift = half - front.getX(), width = half * 2 + 1;
        int floor = entrance.getY();
        for (int step = 1; step <= 2; step++) {
            BlockPos inside = entrance.relative(outward.getOpposite(), step);
            BlockState below = blocks.get(inside.below()), here = blocks.get(inside);
            if (floor > 0 && below != null && !below.isAir()
                    && (here == null || here.isAir() || here.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
                floor--; break;
            }
        }
        List<VanillaConstructionPlan.Cell> result = new ArrayList<>();
        int beds = 0;
        for (var b : blocks.entrySet()) {
            BlockPos at = rotate(b.getKey(), w, d, turns).offset(shift, -floor, 0);
            BlockState value = b.getValue().rotate(rotation);
            // Worldgen lets buried paths turn into dirt. Freeze the stable result instead of
            // continually trying to restore an impossible path below a civic foundation.
            BlockState above = blocks.get(b.getKey().above());
            if (value.is(Blocks.DIRT_PATH) && above != null && above.isSolid()
                    && !(above.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock))
                value = Blocks.DIRT.defaultBlockState();
            if (value.isAir()) value = Blocks.AIR.defaultBlockState();
            // Construction supplies new plants, not a free fully grown harvest. Preserve authored
            // leaf decoration like player-placed leaves even when optional worldgen trees are absent.
            if (value.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
                value = value.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            if (value.getBlock() instanceof net.minecraft.world.level.block.CropBlock)
                for (var property : value.getProperties())
                    if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age
                            && age.getName().equals("age")) value = value.setValue(age, 0);
            // Keep below-grade air: cellars need their surveyed interior cleared as well.
            if (value.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    && value.getValue(net.minecraft.world.level.block.BedBlock.PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD) beds++;
            result.add(new VanillaConstructionPlan.Cell(at.getX(), at.getY(), at.getZ(), BlockStateParser.serialize(value)));
        }
        result.sort(Comparator.comparingInt(VanillaConstructionPlan.Cell::y)
                .thenComparingInt(VanillaConstructionPlan.Cell::z).thenComparingInt(VanillaConstructionPlan.Cell::x));
        return new VanillaConstructionPlan(entry.id(), entry.id().split("/")[1], entry.role(),
                width, h - floor, rd, beds, result);
    }

    private static BlockPos rotate(BlockPos p, int w, int d, int turns) {
        return switch (turns) {
            case 1 -> new BlockPos(d - 1 - p.getZ(), p.getY(), p.getX());
            case 2 -> new BlockPos(w - 1 - p.getX(), p.getY(), d - 1 - p.getZ());
            case 3 -> new BlockPos(p.getZ(), p.getY(), w - 1 - p.getX());
            default -> p;
        };
    }
}
