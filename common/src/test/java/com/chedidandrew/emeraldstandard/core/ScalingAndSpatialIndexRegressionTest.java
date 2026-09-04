package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Correctness and measured 100/500/1,000-record scale coverage for spatial queries and saves. */
public final class ScalingAndSpatialIndexRegressionTest {
    private static final int[] SCALES = {100, 500, 1_000};
    private static final int QUERY_COUNT = 2_000;
    private static final long MAX_SUITE_MILLIS = 30_000L;

    private ScalingAndSpatialIndexRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        long suiteStart = System.nanoTime();
        verifyBoundaryTieUpdateAndRemoval();
        verifyServiceRebuildsPersistedIndex();
        for (int scale : SCALES) {
            runScale(scale);
        }
        long suiteMillis = elapsedMillis(suiteStart);
        require(suiteMillis <= MAX_SUITE_MILLIS,
                "Scaling suite exceeded generous CI ceiling: " + suiteMillis + " ms");
        System.out.println("PASS indexed query and persistence scale guard in "
                + suiteMillis + " ms");
    }

    private static void runScale(int scale) throws Exception {
        EconomyState state = populatedState(scale);
        VillageSpatialIndex index = new VillageSpatialIndex();
        long indexStart = System.nanoTime();
        index.rebuild(state.villages);
        long indexMillis = elapsedMillis(indexStart);
        require(index.size() == scale, "Index omitted villages at scale " + scale);

        Random random = new Random(0x454D4552414C44L + scale);
        List<Query> queries = new ArrayList<>(QUERY_COUNT);
        List<Long> loadedPositions = new ArrayList<>();
        for (int query = 0; query < QUERY_COUNT; query++) {
            String dimension = query % 7 == 0
                    ? "minecraft:the_nether"
                    : "minecraft:overworld";
            long position = pack(
                    random.nextInt(scale * 40 + 1) - scale * 20,
                    48 + random.nextInt(80),
                    random.nextInt(scale * 40 + 1) - scale * 20);
            double radius = 16.0 + random.nextInt(241);
            queries.add(new Query(dimension, position, radius));
            if (query < 64) {
                loadedPositions.add(position);
            }
        }
        for (int query = 0; query < Math.min(250, queries.size()); query++) {
            Query sample = queries.get(query);
            EconomyState.VillageRecord expected = bruteNearest(
                    state.villages, sample.dimension(), sample.position(), sample.radius());
            EconomyState.VillageRecord actual = index.nearest(
                    state.villages, sample.dimension(), sample.position(), sample.radius());
            require(id(expected).equals(id(actual)),
                    "Indexed nearest lookup diverged at scale " + scale);
        }

        long queryStart = System.nanoTime();
        int queryHits = 0;
        for (Query query : queries) {
            EconomyState.VillageRecord found = index.nearest(
                    state.villages, query.dimension(), query.position(), query.radius());
            if (found != null) {
                queryHits++;
            }
        }
        long queryMillis = elapsedMillis(queryStart);

        List<UUID> expectedNear = bruteNearAny(
                state.villages, "minecraft:overworld", loadedPositions, 128.0);
        List<UUID> actualNear = index.nearAny(
                        state.villages,
                        "minecraft:overworld",
                        loadedPositions,
                        128.0)
                .stream()
                .map(village -> village.villageId)
                .toList();
        require(expectedNear.equals(actualNear),
                "Indexed multi-origin lookup diverged at scale " + scale);

        Path directory = Files.createTempDirectory("emerald-standard-scale-");
        Path save = directory.resolve("the_emerald_standard.properties");
        long saveMillis;
        long loadMillis;
        long bytes;
        try {
            long saveStart = System.nanoTime();
            state.save(save);
            saveMillis = elapsedMillis(saveStart);
            bytes = Files.size(save);

            long loadStart = System.nanoTime();
            EconomyState loaded = EconomyState.load(save, -1L, 0L, 0L);
            loadMillis = elapsedMillis(loadStart);
            require(loaded.villages.size() == scale,
                    "Save/reload lost villages at scale " + scale);
            require(loaded.accounts.size() == scale,
                    "Save/reload lost accounts at scale " + scale);
            UUID sampleAccount = accountId(scale - 1);
            require(loaded.accounts.get(sampleAccount).cashMicro
                            == (long) scale * EconomyState.MICRO,
                    "Save/reload changed account data at scale " + scale);
        } finally {
            deleteIfExists(save.resolveSibling(save.getFileName() + ".tmp"));
            deleteIfExists(save.resolveSibling(save.getFileName() + ".bak"));
            deleteIfExists(save);
            deleteIfExists(directory);
        }

        System.out.printf(
                "SCALE records=%d index_ms=%d queries=%d hits=%d query_ms=%d save_ms=%d load_ms=%d bytes=%d%n",
                scale,
                indexMillis,
                QUERY_COUNT,
                queryHits,
                queryMillis,
                saveMillis,
                loadMillis,
                bytes);
    }

    private static EconomyState populatedState(int scale) {
        EconomyState state = EconomyState.fresh(0x95A00000L + scale, 10_000L, 20_000L);
        for (int index = 0; index < scale; index++) {
            UUID villageId = villageId(index);
            EconomyState.VillageRecord village = new EconomyState.VillageRecord();
            village.villageId = villageId;
            village.dimensionKey = index % 11 == 0
                    ? "minecraft:the_nether"
                    : "minecraft:overworld";
            int gridWidth = Math.max(10, (int) Math.ceil(Math.sqrt(scale)));
            int x = (index % gridWidth) * 96 - gridWidth * 48;
            int z = (index / gridWidth) * 96 - gridWidth * 48;
            village.centerPos = pack(x, 64 + index % 8, z);
            village.population = 4 + index % 24;
            village.observedPopulation = village.population;
            village.housingCapacity = village.population + 4;
            village.foodSupply = 200.0 + index % 100;
            village.materialSupply = 150.0 + index % 100;
            village.treasury = 50.0 + index % 50;
            state.villages.put(villageId, village);

            EconomyState.Account account = state.account(accountId(index));
            account.cashMicro = (long) (index + 1) * EconomyState.MICRO;
            account.savingsMicro = (long) (index % 100) * EconomyState.MICRO;
            account.shares.put("VILX", index / 10.0);
        }
        return state;
    }

    private static void verifyBoundaryTieUpdateAndRemoval() {
        Map<UUID, EconomyState.VillageRecord> villages = new LinkedHashMap<>();
        EconomyState.VillageRecord west = village(1, "minecraft:overworld", -1, 64, 0);
        EconomyState.VillageRecord east = village(2, "minecraft:overworld", 1, 64, 0);
        EconomyState.VillageRecord boundary = village(3, "minecraft:overworld", -65, 64, -65);
        villages.put(west.villageId, west);
        villages.put(east.villageId, east);
        villages.put(boundary.villageId, boundary);

        VillageSpatialIndex index = new VillageSpatialIndex();
        index.rebuild(villages);
        require(index.nearest(villages, "minecraft:overworld", pack(0, 64, 0), 2.0)
                        == east,
                "Equal-distance lookup did not retain legacy last-insertion tie behavior");
        require(index.nearest(villages, "minecraft:overworld", pack(-64, 64, -64), 2.0)
                        == boundary,
                "Negative cell boundary lookup failed");

        east.centerPos = pack(1_000, 64, 1_000);
        index.upsert(east);
        require(index.nearest(villages, "minecraft:overworld", pack(0, 64, 0), 2.0)
                        == west,
                "Moved village remained in its old spatial bucket");
        villages.remove(west.villageId);
        index.remove(west.villageId);
        require(index.nearest(villages, "minecraft:overworld", pack(0, 64, 0), 2.0)
                        == null,
                "Removed village remained queryable");
    }

    private static void verifyServiceRebuildsPersistedIndex() throws Exception {
        Path directory = Files.createTempDirectory("emerald-standard-index-reload-");
        Path save = directory.resolve("the_emerald_standard.properties");
        try {
            EconomyState state = EconomyState.fresh(95L, 0L, 0L);
            EconomyState.VillageRecord persisted =
                    village(95, "minecraft:overworld", 640, 70, -640);
            persisted.population = 6;
            persisted.observedPopulation = 6;
            persisted.housingCapacity = 10;
            state.villages.put(persisted.villageId, persisted);
            state.save(save);

            EconomyService service = new EconomyService();
            service.startWithSeed(directory, 95L, 0L, 0L);
            EconomyService.VillageSnapshot found = service.nearestVillageSnapshot(
                    "minecraft:overworld", pack(650, 70, -650), 32.0);
            require(found != null && found.village().villageId.equals(persisted.villageId),
                    "Service did not rebuild its spatial index from persisted villages");
        } finally {
            deleteIfExists(save.resolveSibling(save.getFileName() + ".tmp"));
            deleteIfExists(save.resolveSibling(save.getFileName() + ".bak"));
            deleteIfExists(save);
            deleteIfExists(directory);
        }
    }

    private static EconomyState.VillageRecord bruteNearest(
            Map<UUID, EconomyState.VillageRecord> villages,
            String dimension,
            long position,
            double radius) {
        EconomyState.VillageRecord best = null;
        double bestDistance = radius * radius;
        for (EconomyState.VillageRecord village : villages.values()) {
            if (!dimension.equals(village.dimensionKey)) {
                continue;
            }
            double distance = distanceSquared(village.centerPos, position);
            if (distance <= bestDistance) {
                best = village;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static List<UUID> bruteNearAny(
            Map<UUID, EconomyState.VillageRecord> villages,
            String dimension,
            List<Long> positions,
            double radius) {
        double radiusSquared = radius * radius;
        List<UUID> result = new ArrayList<>();
        for (EconomyState.VillageRecord village : villages.values()) {
            if (!dimension.equals(village.dimensionKey)) {
                continue;
            }
            for (long position : positions) {
                if (distanceSquared(village.centerPos, position) <= radiusSquared) {
                    result.add(village.villageId);
                    break;
                }
            }
        }
        return result;
    }

    private static EconomyState.VillageRecord village(
            int id, String dimension, int x, int y, int z) {
        EconomyState.VillageRecord village = new EconomyState.VillageRecord();
        village.villageId = villageId(id);
        village.dimensionKey = dimension;
        village.centerPos = pack(x, y, z);
        return village;
    }

    private static String id(EconomyState.VillageRecord village) {
        return village == null ? "" : village.villageId.toString();
    }

    private static UUID villageId(int index) {
        return new UUID(0x56494C4C41474500L, index + 1L);
    }

    private static UUID accountId(int index) {
        return new UUID(0x4143434F554E5400L, index + 1L);
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    private static double distanceSquared(long first, long second) {
        double dx = (double) unpackX(first) - unpackX(second);
        double dy = (double) unpackY(first) - unpackY(second);
        double dz = (double) unpackZ(first) - unpackZ(second);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Query(String dimension, long position, double radius) {
    }
}
