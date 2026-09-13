package com.chedidandrew.emeraldstandard.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Native item rendering honors resource packs; nothing here crafts or consumes items. */
record HandbookRecipeCard(int titleLine, int line, float scale, List<HandbookRecipes.Recipe> recipes) {
    static final int WIDTH = 156;
    static final int HEIGHT = 91;
    int rows() { return (int) Math.ceil(HEIGHT * scale / 12.0) + 1; }
    boolean contains(double x, double y) { return x >= 0 && x < WIDTH * scale && y >= 0 && y < HEIGHT * scale; }

    ItemStack draw(GuiGraphicsExtractor g, Font font, int y, long frame, double mouseX, double mouseY) {
        var recipe = recipes.get((int) Math.floorMod(frame, recipes.size()));
        long variant = frame / recipes.size();
        g.pose().pushMatrix();
        g.pose().translate(0, y);
        g.pose().scale(scale, scale);
        double mx = mouseX / scale, my = (mouseY - y) / scale;
        g.fill(0, 0, WIDTH, HEIGHT, 0xFFCBC9BA);
        g.text(font, recipe.shapeless() ? "Shapeless crafting" : "Crafting table", 5, 5, 0xFF26352D, false);
        ItemStack hovered = ItemStack.EMPTY;
        ItemStack[] grid = recipe.frame(variant);
        for (int slot = 0; slot < 9; slot++) {
            int sx = 5 + slot % 3 * 20, sy = 19 + slot / 3 * 20;
            slot(g, font, grid[slot], sx, sy);
            if (mx >= sx && mx < sx + 20 && my >= sy && my < sy + 20) hovered = grid[slot];
        }
        // Pixel arrow; no external image asset and no dependency on vanilla texture coordinates.
        g.fill(76, 45, 98, 51, 0xFF626D62);
        for (int i = 0; i < 10; i++) g.fill(98 + i, 39 + i, 99 + i, 57 - i, 0xFF626D62);
        ItemStack output = recipe.result(variant);
        slot(g, font, output, 121, 39);
        if (mx >= 121 && mx < 141 && my >= 39 && my < 59) hovered = output;
        String caption = recipes.size() > 1 ? "Recipe " + (frame % recipes.size() + 1) + "/" + recipes.size()
                : recipe.shapeless() ? "Any arrangement"
                : recipe.variants() > 1 ? "Ingredient alternatives" : "Fixed ingredients";
        g.text(font, caption, 5, 81, 0xFF3E5745, false);
        g.pose().popMatrix();
        return hovered;
    }

    private static void slot(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        g.fill(x, y, x + 20, y + 20, 0xFFF7F6EB);
        g.fill(x, y, x + 19, y + 19, 0xFF484D48);
        g.fill(x + 1, y + 1, x + 19, y + 19, 0xFF92978E);
        if (!stack.isEmpty()) {
            g.fakeItem(stack, x + 2, y + 2);
            g.itemDecorations(font, stack, x + 2, y + 2);
        }
    }
}
