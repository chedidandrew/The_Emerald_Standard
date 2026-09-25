package com.chedidandrew.emeraldstandard.core;

import java.util.*;
import java.util.function.BiConsumer;

/** Session-only guard ownership; one UUID earns credit in at most one district.
 * Retained observations expire, so unloaded historical districts cannot grow this index forever. */
final class VillageGuardCensus {
    record Observation(Set<UUID> guards, int perGuard, int cap, long day) {}
    private final Map<UUID, Observation> villages = new HashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();
    private long prunedDay = -1;
    void clear() { villages.clear(); owners.clear(); prunedDay = -1; }
    void observe(UUID id, Collection<UUID> guards, int perGuard, int cap, long day,
                 BiConsumer<UUID, Observation> changed) {
        if (prunedDay != day) {
            for (var entry : new ArrayList<>(villages.entrySet())) {
                var old = entry.getValue();
                if (day - old.day() < VillageGuardSecurity.STALE_DAYS) continue;
                old.guards().forEach(owners::remove); villages.remove(entry.getKey());
                changed.accept(entry.getKey(), new Observation(Set.of(), old.perGuard(), old.cap(), old.day()));
            }
            prunedDay = day;
        }
        var old = villages.remove(id);
        if (old != null) old.guards().forEach(owners::remove);
        Set<UUID> selected = new LinkedHashSet<>();
        guards.stream().filter(Objects::nonNull).distinct().sorted().limit(VillageGuardSecurity.MAX_COUNT).forEach(selected::add);
        for (UUID guard : selected) {
            UUID previous = owners.put(guard, id);
            if (previous == null || previous.equals(id)) continue;
            var prior = villages.get(previous);
            Set<UUID> remaining = new LinkedHashSet<>(prior.guards()); remaining.remove(guard);
            var replacement = new Observation(Set.copyOf(remaining), prior.perGuard(), prior.cap(), prior.day());
            if (remaining.isEmpty()) villages.remove(previous); else villages.put(previous, replacement);
            changed.accept(previous, replacement); // Removing a visitor does not renew its former neighbors.
        }
        var current = new Observation(Set.copyOf(selected), perGuard, cap, day);
        if (!selected.isEmpty()) villages.put(id, current);
        changed.accept(id, current);
    }
}
