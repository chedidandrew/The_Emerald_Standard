package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.ConstructionContent;
import com.mojang.math.Transformation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;

import net.minecraft.world.level.block.FenceBlock;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Opt-in native model preview; never opens or changes a saved world. */
final class ConstructionFenceVisualChecks extends Screen {
    private final int view;
    ConstructionFenceVisualChecks(int view) {
        super(Component.literal("Construction fence geometry"));
        this.view = view;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xff192820);
        g.text(font, view == 0 ? "All 16 fence connections" : "Block / junction / item", 8, 8, 0xfff4ce56, false);
        int columns = view == 0 ? 4 : 3, rows = view == 0 ? 4 : 1;
        int cellW = width / columns, cellH = (height - 40) / rows;
        for (int i = 0; i < columns * rows; i++) {
            int x = i % columns * cellW, y = 24 + i / columns * cellH;
            float angle = view == 2 ? -.5F : view == 3 ? .42F : .35F;
            var rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(angle)
                    .rotateY(view == 3 ? .94F : .9F);
            float size = Math.min(cellW * .68F, cellH * .70F);
            if (view != 0 && i == 2) {
                var item = new ItemDisplayEntityRenderState();
                item.entityType = EntityTypes.ITEM_DISPLAY;
                item.boundingBoxHeight = 1; item.boundingBoxWidth = 1;
                var transform = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf());
                item.renderState = new Display.RenderState(f -> transform,
                        Display.BillboardConstraints.FIXED, 15728880, f -> 0, f -> 0, 0);
                new ItemModelResolver(minecraft.getModelManager()).updateForTopItem(item.item,
                        HandbookRecipes.previewStack(ConstructionContent.fence.asItem(), 1), ItemDisplayContext.NONE, minecraft.level, null, 0);
                if (item.item.isEmpty()) throw new IllegalStateException("Fence item model missing");
                g.entity(item, size, new Vector3f(), rotation, null, x, y, x + cellW, y + cellH - 12);
            } else {
                int mask = view == 0 ? i : i == 0 ? 5 : 15;
                var state = new BlockDisplayEntityRenderState();
                state.entityType = EntityTypes.BLOCK_DISPLAY;
                state.boundingBoxHeight = 1; state.boundingBoxWidth = 1;
                var transform = new Transformation(new Vector3f(-.5F, 0, -.5F), new Quaternionf(), new Vector3f(1), new Quaternionf());
                state.renderState = new Display.RenderState(f -> transform,
                        Display.BillboardConstraints.FIXED, 15728880, f -> 0, f -> 0, 0);
                var block = ConstructionContent.fence.defaultBlockState()
                        .setValue(FenceBlock.NORTH, (mask & 1) != 0).setValue(FenceBlock.EAST, (mask & 2) != 0)
                        .setValue(FenceBlock.SOUTH, (mask & 4) != 0).setValue(FenceBlock.WEST, (mask & 8) != 0);
                new BlockModelResolver(minecraft.getModelManager()).update(state.blockModel, block, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
                if (state.blockModel.isEmpty()) throw new IllegalStateException("Fence connection model missing " + mask);
                g.entity(state, size, new Vector3f(0, .5F, 0), rotation, null, x, y, x + cellW, y + cellH - 12);
            }
            g.text(font, view == 0 ? "Mask " + i : i == 0 ? "Straight" : i == 1 ? "Cross" : "Item",
                    x + 8, y + cellH - 10, 0xffeeeecc, false);
        }
        g.text(font, view == 2 ? "Top-surface inspection" : "Cap / rails / connected edges", 8, height - 12, 0xffeeeecc, false);
    }
}
