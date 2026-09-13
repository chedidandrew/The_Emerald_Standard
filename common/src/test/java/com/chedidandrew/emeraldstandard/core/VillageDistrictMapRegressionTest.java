package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.client.DistrictMapViewport;
import java.util.*;

public final class VillageDistrictMapRegressionTest {
    public static void main(String[] args) {
        coverageAndTerrain();
        var state = new EconomyState();
        var root = village(state, null, -120000, 240000);
        root.population = 18; root.housingCapacity = 20; root.foodSupply = 99;
        var district = village(state, root.villageId, -119900, 240100);
        village(state, null, -120001, 240001); // Close but a genuinely different village.
        var otherDimension = village(state, root.villageId, 0, 0);
        otherDimension.dimensionKey = "minecraft:the_nether";
        state.generatedBankAnchors.put(1L, pack(-120100, 240010));
        state.bankRegionVillageIds.put(1L, root.villageId);
        for (int i = 0; i < 401; i++) {
            var p = new EconomyState.VillageProject();
            p.projectId = i + 1; p.originPos = pack(-119900 + i * 20, 240110);
            p.boundsMinPos = p.originPos; p.boundsMaxPos = pack(-119890 + i * 20, 240120);
            p.materializedBlocks = 25; p.totalBlocks = 100;
            p.materializedComplete = i == 0; p.manualRepairRequired = i == 1;
            district.projects.add(p);
        }
        district.projects.add(new EconomyState.VillageProject());
        Set<VillageDistrictMap.Marker> seen = new HashSet<>();
        var first = VillageDistrictMap.collect(state, root.villageId, 0);
        var center = first.markers().getFirst();
        check(center.maxX() - center.minX() == 128 && center.x() == -120000, "large coverage, stable actual center");
        check(first.markers().get(1).x() == -119900, "asymmetric growth never moves district center");
        check(first.districts() == 2 && first.total() == 404 && first.unsited() == 1, "city membership/counts");
        for (int page = 0; page < first.pages(); page++) {
            var view = VillageDistrictMap.collect(state, root.villageId, page);
            check(VillageDistrictMap.PAGE_SIZE <= 384 && view.markers().size() <= VillageDistrictMap.PAGE_SIZE, "bounded page");
            seen.addAll(view.markers());
            var encoded = VillageDistrictMap.encode(view, 65536 + page);
            check(view.equals(VillageDistrictMap.decode(i -> encoded[i])), "signed coordinate wire round trip");
            encoded[0]++;
            check(VillageDistrictMap.decode(i -> encoded[i]) == null, "reject partial update");
            var viewport = new DistrictMapViewport(); viewport.fit(view);
            for (var m : view.markers())
                check(DistrictMapViewport.contains(viewport.x(m.x()), viewport.y(m.z())), "fit all page markers");
            double before = viewport.x(-120000);
            viewport.pan(25, 0);
            check(Math.abs(viewport.x(-120000) - before - 25) < 0.00001, "pan projection");
            for (int n = 0; n < 1000; n++) viewport.zoom(1.25);
            check(Double.isFinite(viewport.scale()) && viewport.scale() <= 8, "zoom bounded");
        }
        check(seen.size() == 404, "no omissions/duplicates across pages");
        check(seen.stream().anyMatch(m -> m.status() == VillageDistrictMap.BLOCKED), "repair state");
        check(VillageDistrictMap.collect(state, root.villageId, Integer.MAX_VALUE).number() == first.pages() - 1,
                "clamp hostile or stale page");
        check(VillageDistrictMap.collect(state, UUID.randomUUID(), 0).equals(VillageDistrictMap.EMPTY), "unknown owner");
        check(root.population == 18 && root.foodSupply == 99 && district.projects.size() == 402
                && state.villages.size() == 4, "read-only");
        System.out.println("PASS district map: ownership, dimensions, pagination, states, transport, projection, read-only");
    }
    private static EconomyState.VillageRecord village(EconomyState state, UUID city, int x, int z) {
        var v = new EconomyState.VillageRecord(); v.villageId = UUID.randomUUID();
        v.cityId = city; v.centerPos = pack(x, z); state.villages.put(v.villageId, v); return v;
    }
    private static void coverageAndTerrain() {
        var state = new EconomyState();
        var v = village(state, null, -1000, -2000);
        var base = VillageDistrictCoverage.of(v);
        check(base.minX() == -1064 && base.maxZ() == -1936, "starter radius is 64 each way");
        v.bankAnchorPos = pack(-1250, -2200);
        var bank = VillageDistrictCoverage.of(v);
        check(bank.minX() == -1282 && bank.minZ() == -2238, "distant owned Bank included");
        var p = new EconomyState.VillageProject(); p.originPos = pack(-700, -2000); v.projects.add(p);
        check(VillageDistrictCoverage.of(v).equals(bank), "unbuilt plans do not expand developed coverage");
        p.materializedBlocks = 1;
        check(VillageDistrictCoverage.of(v).maxX() == -652, "legacy origin fallback and field margin, no world origin");
        var view = new DistrictMapViewport();
        var page = VillageDistrictMap.collect(state, v.villageId, 0); view.focus(page);
        double wx = view.worldX(60), wz = view.worldZ(90);
        view.zoomAt(1.25, 60, 90);
        check(Math.abs(view.worldX(60) - wx) < .00001 && Math.abs(view.worldZ(90) - wz) < .00001,
                "cursor anchored zoom");
        view.zoom(Double.NaN); check(Double.isFinite(view.scale()), "reject invalid zoom");
        var raster = new com.chedidandrew.emeraldstandard.client.DistrictTerrainRaster();
        raster.view(view);
        int[] calls = {0};
        for (int t = 0; t < 150; t++) {
            int n = raster.update(t, (x, z) -> { calls[0]++; return 0xFF589238; });
            check(n <= 512, "strict per-tick terrain sample budget");
            check(raster.update(t, (x, z) -> { throw new AssertionError("resampled same tick"); }) == 0,
                    "frame rate cannot multiply sample budget");
        }
        check(calls[0] > 0 && raster.pixel(30, 30) == 0xFF589238, "terrain fills progressively");
        for (int t = 150; t < 220; t++) raster.update(t, (x, z) -> 0);
        check(raster.pixel(30, 30) == 0xFF589238, "unloaded terrain retains last-seen color");
        for (int t = 220; t < 360; t++) {
            view.pan(300, 200); raster.view(view); raster.update(t, (x, z) -> 0xFF385EC4);
        }
        check(raster.cachedTiles() <= 32768, "cache stays bounded after extensive panning");
        for (int n = 0; n < 1000; n++) view.zoom(.8);
        raster.view(view);
        raster.update(400, (x, z) -> { check(Math.abs((long)x) < 30000000, "no reads past world bounds"); return 0; });
    }
    private static long pack(int x, int z) { return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | 70; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
