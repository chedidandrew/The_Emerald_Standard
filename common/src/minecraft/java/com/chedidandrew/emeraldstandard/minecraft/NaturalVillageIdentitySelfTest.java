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
            if (NaturalVillageIdentity.near(level, new BlockPos(1_000_000, 80, 1_000_000)) != null
                    || loaded != level.getChunkSource().getLoadedChunksCount())
                throw new AssertionError("Discovery loaded terrain or invented a village");
            System.out.println("PASS natural village discovery: distinct loaded structure starts, stable tie, no forced chunk loading");
        } finally {
            ca.setAllStarts(oldA); cb.setAllStarts(oldB);
        }
    }
}
