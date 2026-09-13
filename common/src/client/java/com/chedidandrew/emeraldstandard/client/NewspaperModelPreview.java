package com.chedidandrew.emeraldstandard.client;

import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Direct native model rendering: never enlarge Minecraft's cached 16-GUI-pixel item atlas. */
final class NewspaperModelPreview {
    private NewspaperModelPreview() {}

    static ItemDisplayEntityRenderState model(Minecraft game, ItemStack stack, ItemDisplayContext context) {
        var state = new ItemDisplayEntityRenderState();
        state.entityType = EntityTypes.ITEM_DISPLAY;
        state.boundingBoxWidth = 1;
        state.boundingBoxHeight = 1;
        var transform = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf());
        state.renderState = new Display.RenderState(f -> transform,
                Display.BillboardConstraints.FIXED, 15728880, f -> 0, f -> 0, 0);
        new ItemModelResolver(game.getModelManager()).updateForTopItem(state.item, stack, context, game.level, null, 0);
        if (state.item.isEmpty()) throw new IllegalStateException("Newspaper native model is empty");
        return state;
    }

    static void draw(GuiGraphicsExtractor g, ItemDisplayEntityRenderState state, int x, int y, int size) {
        g.entity(state, size, new Vector3f(),
                new Quaternionf().rotateZ((float)Math.PI), null, x, y, x + size, y + size);
    }
}
