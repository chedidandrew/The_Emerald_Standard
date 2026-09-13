package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.VillageDistrictMap;

/** Double precision world-to-map projection; UI work is bounded by the current page. */
public final class DistrictMapViewport {
    public static final int X = 12, Y = 73, WIDTH = 210, HEIGHT = 120;
    private double centerX, centerZ, scale = 1;
    public void fit(VillageDistrictMap.Page page) {
        double minX = page.focusX(), maxX = minX, minZ = page.focusZ(), maxZ = minZ;
        if (!page.markers().isEmpty()) {
            minX = minZ = Double.POSITIVE_INFINITY; maxX = maxZ = Double.NEGATIVE_INFINITY;
            for (var m : page.markers()) {
                minX = Math.min(minX, m.minX()); maxX = Math.max(maxX, m.maxX());
                minZ = Math.min(minZ, m.minZ()); maxZ = Math.max(maxZ, m.maxZ());
            }
        }
        centerX = (minX + maxX) / 2; centerZ = (minZ + maxZ) / 2;
        scale = Math.min(4, Math.min((WIDTH - 24) / Math.max(32, maxX - minX + 1),
                (HEIGHT - 24) / Math.max(32, maxZ - minZ + 1)));
    }
    public double x(double worldX) { return X + WIDTH / 2.0 + (worldX - centerX) * scale; }
    public double y(double worldZ) { return Y + HEIGHT / 2.0 + (worldZ - centerZ) * scale; }
    public double worldX(double x) { return centerX + (x - X - WIDTH / 2.0) / scale; }
    public double worldZ(double y) { return centerZ + (y - Y - HEIGHT / 2.0) / scale; }
    public void focus(VillageDistrictMap.Page page) {
        var current = page.markers().stream().filter(m -> m.kind() == VillageDistrictMap.DISTRICT
                && m.status() == VillageDistrictMap.CURRENT).toList();
        if (current.isEmpty()) {
            int x = page.focusX(), z = page.focusZ();
            current = java.util.List.of(new VillageDistrictMap.Marker(x - 64, z - 64, x + 64, z + 64,
                    VillageDistrictMap.DISTRICT, 1, VillageDistrictMap.CURRENT, 0, 0, x, z));
        }
        fit(new VillageDistrictMap.Page(0, current.size(), 1, 0, page.focusX(), page.focusZ(), current));
    }
    public void zoom(double factor) {
        if (Double.isFinite(factor) && factor > 0) scale = Math.clamp(scale * factor, 0.000001, 8);
    }
    public void zoomAt(double factor, double x, double y) {
        double beforeX = worldX(x), beforeZ = worldZ(y);
        zoom(factor);
        centerX += beforeX - worldX(x); centerZ += beforeZ - worldZ(y);
    }
    public void pan(double x, double y) { centerX -= x / scale; centerZ -= y / scale; }
    public double scale() { return scale; }
    public static boolean contains(double x, double y) {
        return x >= X && x < X + WIDTH && y >= Y && y < Y + HEIGHT;
    }
}
