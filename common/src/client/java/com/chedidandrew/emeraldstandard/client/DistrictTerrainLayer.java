package com.chedidandrew.emeraldstandard.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.concurrent.atomic.AtomicLong;

/** One small disposable view texture over the shared, persistent explored-terrain store. */
final class DistrictTerrainLayer implements AutoCloseable {
    private static final AtomicLong IDS = new AtomicLong();
    private final Identifier id = Identifier.fromNamespaceAndPath("the_emerald_standard", "district_terrain/" + IDS.incrementAndGet());
    private DistrictTerrainRaster raster = new DistrictTerrainRaster();
    private ClientLevel level;
    private NativeImage pixels;
    private DynamicTexture texture;
    private DistrictTerrainRaster.Source fixture;
    void fixture(DistrictTerrainRaster.Source source) { fixture = source; }

    void draw(GuiGraphicsExtractor graphics, DistrictMapViewport view) {
        var mc = Minecraft.getInstance();
        if (level != mc.level) { level = mc.level; raster = new DistrictTerrainRaster(); }
        raster.view(view);
        long tick = System.nanoTime() / 50_000_000;
        raster.update(tick, fixture != null ? fixture : (x, z) -> ExploredTerrainRuntime.color(level, x, z));
        if (texture == null) {
            pixels = new NativeImage(DistrictTerrainRaster.WIDTH, DistrictTerrainRaster.HEIGHT, true);
            texture = new DynamicTexture(() -> "Emerald Standard district terrain", pixels);
            mc.getTextureManager().register(id, texture);
        }
        if (raster.consumeDirty()) {
            for (int y = 0; y < DistrictTerrainRaster.HEIGHT; y++)
                for (int x = 0; x < DistrictTerrainRaster.WIDTH; x++)
                    pixels.setPixel(x, y, raster.pixel(x, y));
            texture.upload();
        }
        // Exact screen rectangle, with UVs preserving sub-pixel panning alignment.
        float u0 = (float) ((view.worldX(DistrictMapViewport.X) - raster.originX()) / raster.step() / DistrictTerrainRaster.WIDTH);
        float v0 = (float) ((view.worldZ(DistrictMapViewport.Y) - raster.originZ()) / raster.step() / DistrictTerrainRaster.HEIGHT);
        float u1 = u0 + (float) (DistrictMapViewport.WIDTH / view.scale() / raster.step() / DistrictTerrainRaster.WIDTH);
        float v1 = v0 + (float) (DistrictMapViewport.HEIGHT / view.scale() / raster.step() / DistrictTerrainRaster.HEIGHT);
        graphics.blit(id, DistrictMapViewport.X, DistrictMapViewport.Y,
                DistrictMapViewport.X + DistrictMapViewport.WIDTH, DistrictMapViewport.Y + DistrictMapViewport.HEIGHT,
                u0, u1, v0, v1);
    }
    @Override public void close() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; pixels = null; raster = new DistrictTerrainRaster(); level = null;
    }
}
