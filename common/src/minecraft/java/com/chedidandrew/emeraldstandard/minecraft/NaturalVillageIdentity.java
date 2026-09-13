package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;

/** Reads loaded structure references only. Generated homes/Bank bells cannot found another village. */
final class NaturalVillageIdentity {
    record Village(UUID id, BlockPos center, Set<Long> parcels) {}

    /** Periodic discovery, not a block scan. Overlapping players share loaded-chunk probes. */
    static List<Village> withinDevelopmentRadius(ServerLevel level, Collection<BlockPos> players, int radius) {
        if (players.isEmpty()) return List.of();
        if (radius < 1 || radius > EmeraldConfig.MAX_VILLAGE_DEVELOPMENT_RADIUS)
            throw new IllegalArgumentException("Invalid village discovery radius " + radius);
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<Long> chunks = new HashSet<>(), starts = new HashSet<>();
        Map<UUID, Village> villages = new TreeMap<>();
        for (BlockPos player : players) {
            for (int cx = (player.getX() - radius) >> 4; cx <= (player.getX() + radius) >> 4; cx++) {
                for (int cz = (player.getZ() - radius) >> 4; cz <= (player.getZ() + radius) >> 4; cz++) {
                    long key = (cx & 0xFFFFFFFFL) | ((long) cz << 32);
                    if (!chunks.add(key)) continue;
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    starts.add(key);
                    chunk.getAllReferences().forEach((type, refs) -> {
                        if (registry.wrapAsHolder(type).is(StructureTags.VILLAGE)) starts.addAll(refs);
                    });
                }
            }
        }
        for (long packed : starts) {
            var chunk = level.getChunkSource().getChunkNow((int) packed, (int) (packed >> 32));
            if (chunk == null) continue;
            for (var entry : chunk.getAllStarts().entrySet()) {
                var start = entry.getValue();
                if (!registry.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE) || !start.isValid()) continue;
                BlockPos center = start.getBoundingBox().getCenter();
                if (players.stream().noneMatch(p -> VillageBankPlacementPolicy.recoveryActive(
                        center.getX(), center.getZ(), p.getX(), p.getZ(), radius))) continue;
                Village village = describe(level, packed, start);
                villages.putIfAbsent(village.id(), village);
            }
        }
        return List.copyOf(villages.values());
    }

    private static Village describe(ServerLevel level, long packed,
            net.minecraft.world.level.levelgen.structure.StructureStart start) {
        String identity = level.dimension().identifier() + ":natural_village:" + packed;
        UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        Set<Long> parcels = new TreeSet<>();
        for (var piece : start.getPieces()) {
            var footprint = piece.getBoundingBox();
            int ax = footprint.minX() >> 4, bx = footprint.maxX() >> 4;
            int az = footprint.minZ() >> 4, bz = footprint.maxZ() >> 4;
            if ((long) (bx - ax + 1) * (bz - az + 1) > 1024) continue;
            for (int px = ax; px <= bx && parcels.size() < 4096; px++)
                for (int pz = az; pz <= bz && parcels.size() < 4096; pz++)
                    parcels.add(com.chedidandrew.emeraldstandard.core.VillageTerritory.key(px, pz));
        }
        return new Village(id, start.getBoundingBox().getCenter(), Set.copyOf(parcels));
    }
    static Village near(ServerLevel level,BlockPos position) {
        var registry=level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<Long> checked=new HashSet<>();
        Village best=null; double distance=Double.MAX_VALUE;
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            var chunk=level.getChunkSource().getChunkNow((position.getX()>>4)+dx,(position.getZ()>>4)+dz);
            if(chunk==null) continue;
            Set<Long> starts=new HashSet<>();
            chunk.getAllReferences().forEach((type,refs)-> { if(registry.wrapAsHolder(type).is(StructureTags.VILLAGE)) starts.addAll(refs); });
            starts.add(chunk.getPos().pack());
            for(long packed:starts) {
                if(!checked.add(packed)) continue;
                var startChunk=level.getChunkSource().getChunkNow((int)packed,(int)(packed>>32));
                if(startChunk==null) continue;
                for(var entry:startChunk.getAllStarts().entrySet()) {
                    var start=entry.getValue();
                    if(!registry.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE)||!start.isValid()) continue;
                    var bounds=start.getBoundingBox();
                    double x=Math.max(bounds.minX(),Math.min(bounds.maxX(),position.getX()));
                    double z=Math.max(bounds.minZ(),Math.min(bounds.maxZ(),position.getZ()));
                    double d=(x-position.getX())*(x-position.getX())+(z-position.getZ())*(z-position.getZ());
                    // Permit approaching the outskirts, never associate a faraway field.
                    if(d>64*64 || d>distance) continue;
                    String identity=level.dimension().identifier()+":natural_village:"+packed;
                    UUID id=UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
                    if(d==distance && best!=null && id.compareTo(best.id())>=0) continue;
                    best=describe(level,packed,start);
                    distance=d;
                }
            }
        }
        return best;
    }
}
