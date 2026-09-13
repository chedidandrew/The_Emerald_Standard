package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;

/** Disposable smoke-world metadata fixture; never places or generates village buildings. */
final class NaturalVillageIdentitySelfTest {
    static void run(ServerLevel level) {
        var type = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).stream()
                .filter(s -> level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(s).is(StructureTags.VILLAGE))
                .findFirst().orElseThrow();
        BlockPos a = new BlockPos(8192, 80, 8192), b = a.offset(32, 0, 0);
        var ca = level.getChunk(a);
        var cb = level.getChunk(b);
        var oldA = new HashMap<>(ca.getAllStarts());
        var oldB = new HashMap<>(cb.getAllStarts());
        try {
            ca.setStartForStructure(type, new StructureStart(type, ca.getPos(), 0,
                    new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(a)))));
            cb.setStartForStructure(type, new StructureStart(type, cb.getPos(), 0,
                    new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(b)))));
            var one = NaturalVillageIdentity.near(level, a);
            var two = NaturalVillageIdentity.near(level, b);
            if (one == null || two == null || one.id().equals(two.id())) throw new AssertionError("Neighboring natural starts merged");
            if (!one.parcels().contains(com.chedidandrew.emeraldstandard.core.VillageTerritory.key(a.getX()>>4,a.getZ()>>4)))
                throw new AssertionError("Original structure footprint missing");
            if (!one.equals(NaturalVillageIdentity.near(level, a))) throw new AssertionError("Unstable loaded structure identity");
            var midpoint = NaturalVillageIdentity.near(level, a.offset(16, 0, 0));
            if (!midpoint.id().equals(one.id().compareTo(two.id()) < 0 ? one.id() : two.id()))
                throw new AssertionError("Nondeterministic equidistant natural identity");
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var atLimit = NaturalVillageIdentity.withinDevelopmentRadius(level, List.of(a.offset(-256, 200, 0)), 256);
            if (atLimit.size() != 1 || !atLimit.getFirst().id().equals(one.id()))
                throw new AssertionError("Configured discovery boundary or altitude ignored");
            if (!NaturalVillageIdentity.withinDevelopmentRadius(level, List.of(a.offset(-257, 0, 0)), 256).isEmpty())
                throw new AssertionError("Village outside discovery radius admitted");
            if (!NaturalVillageIdentity.withinDevelopmentRadius(level, List.of(a.offset(-192, 0, -192)), 256).isEmpty())
                throw new AssertionError("Discovery treated circular radius as a square");
            if (NaturalVillageIdentity.withinDevelopmentRadius(level, List.of(a, a, b), 512).size() != 2)
                throw new AssertionError("Multiple villages or overlapping player discovery lost/doubled");
            if (!NaturalVillageIdentity.withinDevelopmentRadius(level, List.of(a.offset(-256, 0, 0)), 128).isEmpty()
                    || NaturalVillageIdentity.withinDevelopmentRadius(level, List.of(a.offset(-512, 0, 0)), 512).size() != 1)
                throw new AssertionError("Changing the setting did not change first discovery range");
            if (!NaturalVillageIdentity.withinDevelopmentRadius(level,
                    List.of(new BlockPos(-1_000_000, 80, -1_000_000)), 512).isEmpty()
                    || loaded != level.getChunkSource().getLoadedChunksCount())
                throw new AssertionError("Radius discovery loaded terrain or invented a settlement");
            if (NaturalVillageIdentity.near(level, new BlockPos(1_000_000, 80, 1_000_000)) != null
                    || loaded != level.getChunkSource().getLoadedChunksCount())
                throw new AssertionError("Discovery loaded terrain or invented a village");
            verifyDistantCensus(level, a, b, one.id(), two.id());
            System.out.println("PASS natural village discovery: configurable 128/256/512 radius, boundary, height, multiple villages, no forced chunk loading");
        } finally {
            ca.setAllStarts(oldA); cb.setAllStarts(oldB);
        }
    }

    private static void verifyDistantCensus(ServerLevel level, BlockPos a, BlockPos b, UUID first, UUID second) {
        var observer = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DiscoveryFixture"),
                net.minecraft.server.level.ClientInformation.createDefault());
        List<net.minecraft.world.entity.npc.villager.Villager> residents = new ArrayList<>();
        try {
            // Fixture setup only: production discovery is forbidden to request these chunks.
            for (int x = a.getX() - 48; x <= b.getX() + 48; x += 16)
                for (int z = a.getZ() - 48; z <= a.getZ() + 48; z += 16) level.getChunk(new BlockPos(x, 80, z));
            for (BlockPos home : List.of(a, b)) {
                var villager = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level,
                        net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                villager.setPos(home.getX(), home.getY(), home.getZ());
                level.addFreshEntity(villager); residents.add(villager);
            }
            observer.setPos(a.getX() - 224, a.getY() + 120, a.getZ());
            level.players().add(observer);
            var economy = new com.chedidandrew.emeraldstandard.core.EconomyService();
            economy.start(java.nio.file.Files.createTempDirectory("tes-distant-discovery-"), 912, 0);
            var config = EmeraldConfig.parse(new java.util.Properties());
            int loaded = level.getChunkSource().getLoadedChunksCount();
            if (level.isVillage(observer.blockPosition())) throw new AssertionError("Fixture is inside village POI boundary");
            VillageProsperityManager.scanLoadedVillages(level, economy, config);
            var one = economy.villageSnapshot(first); var two = economy.villageSnapshot(second);
            if (one == null || two == null || one.village().centerPos != a.asLong() || two.village().centerPos != b.asLong())
                throw new AssertionError("Distant first census failed or used the player's position as the village center");
            if (!first.equals(VillageProsperityManager.villageId(residents.get(0)))
                    || !second.equals(VillageProsperityManager.villageId(residents.get(1))))
                throw new AssertionError("Overlapping first censuses mixed neighboring natural village residents");
            if (VillageBankManager.activeBankVillages(level, economy, 256).size() != 2)
                throw new AssertionError("Newly discovered villages do not reach automatic Bank planning");
            VillageProsperityManager.scanLoadedVillages(level, economy, config);
            if (VillageBankManager.activeBankVillages(level, economy, 256).size() != 2
                    || loaded != level.getChunkSource().getLoadedChunksCount())
                throw new AssertionError("Repeat census duplicated identity or loaded terrain");
            System.out.println("PASS distant first census: 224/256 blocks, outside POIs, distinct residents/centers, automatic Bank candidates");
        } catch (Exception exception) {
            throw new IllegalStateException("Distant first village census fixture failed", exception);
        } finally {
            level.players().remove(observer); observer.discard(); residents.forEach(v -> v.discard());
            VillageFoodEnvironment.reset(); VillagePopulationEnvironment.reset();
        }
    }
}
