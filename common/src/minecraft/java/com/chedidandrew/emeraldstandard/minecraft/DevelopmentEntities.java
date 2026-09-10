package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.npc.villager.Villager;

/** Session-local lifecycle index; no repeated whole-world scans during construction theatre. */
public final class DevelopmentEntities {
    private static final Map<ServerLevel,Map<UUID,Entity>> INDEX = new IdentityHashMap<>();
    private static final Set<ServerLevel> INITIALIZED = Collections.newSetFromMap(new IdentityHashMap<>());
    private DevelopmentEntities() { }
    public static void reset() { INDEX.clear(); INITIALIZED.clear(); }
    public static void loaded(Entity entity, ServerLevel level) {
        if (entity instanceof Villager || entity instanceof Display)
            INDEX.computeIfAbsent(level,k->new LinkedHashMap<>()).put(entity.getUUID(),entity);
    }
    public static void unloaded(Entity entity,ServerLevel level) {
        VillageConstructionActivity.unloaded(entity);
        var index = INDEX.get(level); if (index != null) index.remove(entity.getUUID());
    }
    static List<Entity> snapshot(ServerLevel level) {
        if (INITIALIZED.add(level)) level.getAllEntities().forEach(e->loaded(e,level));
        var index=INDEX.computeIfAbsent(level,k->new LinkedHashMap<>());
        index.values().removeIf(e->!e.isAlive());
        return new ArrayList<>(index.values());
    }
}
