package com.chedidandrew.emeraldstandard.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** No enable action occurs until explicit consent; Escape/Cancel return unchanged. */
final class ForcedDevelopmentConfirmationScreen extends Screen {
    private final Screen parent;
    private final Runnable confirm;
    private int x, y, panelWidth, panelHeight;
    static final String WARNING = "DEBUG: skips food, safety, resources, upkeep, tiers and growth approval. "
            + "Rapid endless growth in loaded areas; protection stays on.\n"
            + "PERMANENT: disabling does NOT remove buildings or restore terrain. Pending work may finish normally.\n"
            + "Back up first! Buildings and loot can increase lag/save size. Confirm, then Apply to enable.";
    boolean warningFits() { return font.split(Component.literal(WARNING),panelWidth-24).size()*10 <= panelHeight-72; }
    ForcedDevelopmentConfirmationScreen(Screen parent, Runnable confirm) {
        super(Component.literal("Enable forced development?"));
        this.parent = parent; this.confirm = confirm;
    }
    @Override protected void init() {
        panelWidth = Math.min(460, width - 20);
        panelHeight = Math.min(height - 16, Math.max(156,
                font.split(Component.literal(WARNING),panelWidth-24).size()*10+76));
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        int w = (panelWidth - 36) / 2;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 12, y + panelHeight - 32, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("I understand"), b -> confirm.run())
                .bounds(x + 24 + w, y + panelHeight - 32, w, 20).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xEE101814);
        g.fill(x, y, x + panelWidth, y + panelHeight, 0xFF19392D);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.text(font, title, x + 12, y + 12, 0xFFFFCB70, false);
        int lineY = y + 32;
        for (var line : font.split(Component.literal(WARNING), panelWidth - 24)) {
            g.text(font, line, x + 12, lineY, 0xFFF3E7CF, false);
            lineY += 10;
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
