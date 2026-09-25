package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.*;

/** Per-dimension, one-shot road furniture receipts; removed lamps are never regenerated. */
final class WalkwayLightingLedger extends SavedData {
    record Piece(long position, BlockState before, BlockState after) {
        static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Piece::position),
                BlockState.CODEC.fieldOf("before").forGetter(Piece::before),
                BlockState.CODEC.fieldOf("after").forGetter(Piece::after)).apply(i, Piece::new));
    }
    record Job(int station, List<Piece> pending, int placed, boolean done) {
        static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 256).fieldOf("station").forGetter(Job::station),
                Piece.CODEC.listOf().fieldOf("pending").forGetter(Job::pending),
                Codec.intRange(0, 32).fieldOf("placed").forGetter(Job::placed),
                Codec.BOOL.fieldOf("done").forGetter(Job::done)).apply(i, Job::new));
        Job { pending = List.copyOf(pending); }
        static Job fresh() { return new Job(0, List.of(), 0, false); }
        Job next() { return new Job(station + 1, List.of(), 0, false); }
    }
    static final Codec<WalkwayLightingLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Job.CODEC).fieldOf("jobs").forGetter(s -> s.jobs),
            Codec.LONG.listOf().fieldOf("sites").forGetter(s -> new ArrayList<>(s.sites)))
            .apply(i, WalkwayLightingLedger::new));
    static final SavedDataType<WalkwayLightingLedger> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard", "walkway_lighting"),
            WalkwayLightingLedger::new, CODEC, DataFixTypes.LEVEL);
    final Map<String, Job> jobs = new LinkedHashMap<>();
    final Set<Long> sites = new LinkedHashSet<>();
    private final Map<Long, Set<BlockPos>> spatial = new HashMap<>();
    // Session-only pacing; persistence, not this timer, is the anti-regeneration boundary.
    long lastWorkTick = Long.MIN_VALUE;
    final Map<UUID, Integer> rotation = new HashMap<>();
    WalkwayLightingLedger() {}
    WalkwayLightingLedger(Map<String, Job> jobs, List<Long> sites) {
        this.jobs.putAll(jobs);
        sites.forEach(this::index);
    }
    static WalkwayLightingLedger get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    static String key(UUID village, long project, long origin) { return village + "/" + project + "/" + origin; }
    boolean done(String key) { return jobs.getOrDefault(key, Job.fresh()).done; }
    void record(String key, Job job) { jobs.put(key, job); setDirty(); }
    void reserve(BlockPos pos) { index(pos.asLong()); setDirty(); }
    private void index(long packed) {
        sites.add(packed); BlockPos p = BlockPos.of(packed);
        spatial.computeIfAbsent(column(p.getX() >> 4, p.getZ() >> 4), k -> new HashSet<>()).add(p);
    }
    boolean nearby(BlockPos p) {
        for (int x = (p.getX() - 8) >> 4; x <= (p.getX() + 8) >> 4; x++)
            for (int z = (p.getZ() - 8) >> 4; z <= (p.getZ() + 8) >> 4; z++)
                for (BlockPos other : spatial.getOrDefault(column(x, z), Set.of()))
                    if (Math.abs(p.getY() - other.getY()) <= 6
                            && squared(p.getX() - other.getX()) + squared(p.getZ() - other.getZ()) < 64) return true;
        return false;
    }
    private static long squared(long n) { return n * n; }
    private static long column(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
}
