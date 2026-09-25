package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.VillageDistrictMap;

/** Double precision projection with bounded, geographic server requests. */
public final class DistrictMapViewport {
    public static final int X=12,Y=73,WIDTH=210,HEIGHT=120;
    private double centerX,centerZ,scale=1;
    public void fit(VillageDistrictMap.Snapshot map) { show(VillageDistrictMap.View.fit(map.overview())); }
    public void focus(VillageDistrictMap.Snapshot map) { show(VillageDistrictMap.View.fit(map.focus())); }
    public void show(VillageDistrictMap.View view) {
        centerX=view.centerX();centerZ=view.centerZ();scale=WIDTH/(2.0*view.halfWidth());
    }
    public VillageDistrictMap.View request() {
        // Four logical pixels of overscan include a landmark whose point just touches an edge.
        int half=(int)Math.clamp(Math.ceil((WIDTH/2.0+4)/scale),16,VillageDistrictMap.WORLD_LIMIT);
        return new VillageDistrictMap.View((int)Math.round(centerX),(int)Math.round(centerZ),half);
    }
    public double x(double worldX) { return X+WIDTH/2.0+(worldX-centerX)*scale; }
    public double y(double worldZ) { return Y+HEIGHT/2.0+(worldZ-centerZ)*scale; }
    public double worldX(double x) { return centerX+(x-X-WIDTH/2.0)/scale; }
    public double worldZ(double y) { return centerZ+(y-Y-HEIGHT/2.0)/scale; }
    public void zoom(double factor) {
        if (Double.isFinite(factor) && factor>0)
            scale=Math.clamp(scale*factor,(WIDTH/2.0+4)/VillageDistrictMap.WORLD_LIMIT,8);
    }
    public void zoomAt(double factor,double x,double y) {
        double beforeX=worldX(x),beforeZ=worldZ(y);
        zoom(factor);centerX+=beforeX-worldX(x);centerZ+=beforeZ-worldZ(y);clamp();
    }
    public void pan(double x,double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        centerX-=x/scale;centerZ-=y/scale;clamp();
    }
    private void clamp() {
        centerX=Math.clamp(centerX,-VillageDistrictMap.WORLD_LIMIT,VillageDistrictMap.WORLD_LIMIT);
        centerZ=Math.clamp(centerZ,-VillageDistrictMap.WORLD_LIMIT,VillageDistrictMap.WORLD_LIMIT);
    }
    public double scale() { return scale; }
    public static boolean contains(double x,double y) {
        return x>=X && x<X+WIDTH && y>=Y && y<Y+HEIGHT;
    }
}
