package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.*;

/** Native per-dimension receipts prevent duplicate crews and rebuilding player-removed barriers. */
final class ConstructionSiteLedger extends SavedData {
    record Entry(List<Long> fences, List<String> workers, boolean fenceReady) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.listOf().fieldOf("fences").forGetter(Entry::fences),
                Codec.STRING.listOf().fieldOf("workers").forGetter(Entry::workers),
                Codec.BOOL.optionalFieldOf("fence_ready", false).forGetter(Entry::fenceReady)).apply(i, Entry::new));
        Entry(List<Long> fences, List<String> workers) { this(fences, workers, false); }
        Entry { fences = new ArrayList<>(fences); workers = new ArrayList<>(workers); }
    }
    static final Codec<ConstructionSiteLedger> CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC)
            .xmap(ConstructionSiteLedger::new, s -> s.entries);
    static final SavedDataType<ConstructionSiteLedger> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard", "construction_crews"),
            ConstructionSiteLedger::new, CODEC, DataFixTypes.LEVEL);
    final Map<String, Entry> entries = new LinkedHashMap<>();
    ConstructionSiteLedger() { }
    ConstructionSiteLedger(Map<String, Entry> entries) { this.entries.putAll(entries); }
    static ConstructionSiteLedger get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    Entry entry(String job) { return entries.computeIfAbsent(job, k -> { setDirty(); return new Entry(List.of(), List.of()); }); }
    Entry fencePrepared(String job) {
        Entry entry = entry(job);
        if (entry.fenceReady()) return entry;
        entry = new Entry(entry.fences(), entry.workers(), true);
        entries.put(job, entry); setDirty(); return entry;
    }
}
