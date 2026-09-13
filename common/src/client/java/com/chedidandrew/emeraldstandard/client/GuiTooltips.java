package com.chedidandrew.emeraldstandard.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Shared width bounds for every mod text hover, including native button/edit-box tooltips. */
final class GuiTooltips {
    static int maxWidth(int screenWidth) {
        return Math.max(1, Math.min(240, screenWidth - 24));
    }
    static List<FormattedCharSequence> lines(Font font, Component text, int screenWidth) {
        return font.split(text, maxWidth(screenWidth));
    }
    static Tooltip widget(Component text) {
        Minecraft game=Minecraft.getInstance();
        // Native widgets retain their hover delay, keyboard positioning and full narration.
        // Store actual line breaks as well, so no widget receives an unbounded description.
        return Tooltip.create(widgetText(game.font,text,game.getWindow().getGuiScaledWidth()),text);
    }
    static Component widgetText(Font font,Component text,int screenWidth) {
        MutableComponent wrapped=Component.empty();
        boolean first=true;
        // Match vanilla's 170px widget width, further constrained by a narrow viewport.
        for(var line:font.getSplitter().splitLines(text,Math.min(170,maxWidth(screenWidth)),Style.EMPTY)) {
            if(!first)wrapped.append("\n");
            first=false;
            line.visit((style,part)-> {
                wrapped.append(Component.literal(part).withStyle(style));
                return java.util.Optional.empty();
            },Style.EMPTY);
        }
        return wrapped;
    }
    static void show(GuiGraphicsExtractor g, Font font, Component text, int x, int y) {
        g.setTooltipForNextFrame(font, lines(font,text,g.guiWidth()),x,y);
    }
    private GuiTooltips() {}
}
