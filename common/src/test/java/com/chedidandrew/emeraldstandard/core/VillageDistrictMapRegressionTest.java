package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.client.DistrictMapViewport;
import java.util.*;

public final class VillageDistrictMapRegressionTest {
    public static void main(String[] args) {
        coverageAndTerrain();
        ContinuousDistrictMapRegressionTest.verify();
        System.out.println("PASS continuous district map and terrain projection");
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
        var page = VillageDistrictMap.collect(state, v.villageId, null); view.focus(page);
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
