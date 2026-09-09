package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Optional loader config entry point, with separate client and active single-player world scope. */
public final class EmeraldSettingsScreen extends Screen {
    private final Screen parent;
    private final MinecraftServer server;
    private EmeraldConfig snapshot;
    private final Map<String, String> edits = new LinkedHashMap<>();
    private int percent = ReaderPreferences.DEFAULT_PERCENT;
    private int page;
    private int x, y, panelWidth, panelHeight, rows;
    private volatile boolean saving;
    private String status = "";
    private boolean error;
    private Button apply;

    public EmeraldSettingsScreen(Screen parent) {
        super(Component.literal("The Emerald Standard Settings"));
        this.parent = parent;
        server = Minecraft.getInstance().getSingleplayerServer();
        snapshot = server == null ? null : EmeraldConfig.current();
        try { percent = ReaderPreferences.load(HandbookScreen.preferencesPath()); }
        catch (java.io.IOException exception) { status = exception.getMessage(); error = true; }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(680, width - 20);
        panelHeight = Math.min(550, height - 20);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        button("A-", x + 116, y + 35, 30, () -> setPercent(percent - 10));
        button("A+", x + 150, y + 35, 30, () -> setPercent(percent + 10));
        button("Read handbook", x + panelWidth - 112, y + 35, 102,
                () -> minecraft.gui.setScreen(new HandbookScreen(this)));
        rows = Math.max(1, (panelHeight - 151) / 27);
        if (worldEditable()) {
            List<String> keys = new ArrayList<>(snapshot.values().keySet());
            page = Math.max(0, Math.min(page, (keys.size() - 1) / rows));
            for (int row = 0; row < rows && page * rows + row < keys.size(); row++) {
                String key = keys.get(page * rows + row);
                String value = edits.getOrDefault(key, snapshot.values().get(key));
                int rowY = y + 93 + row * 27;
                if (value.equals("true") || value.equals("false")) {
                    Button toggle = button(Boolean.parseBoolean(value) ? "On" : "Off",
                            x + panelWidth - 94, rowY, 82, () -> {
                                boolean next = !Boolean.parseBoolean(edits.getOrDefault(key,
                                        snapshot.values().get(key)));
                                edits.put(key, Boolean.toString(next));
                                rebuildWidgets();
                            });
                    toggle.active = !saving;
                    toggle.setTooltip(Tooltip.create(Component.literal(key)));
                } else {
                    EditBox input = new EditBox(font, x + panelWidth - 94, rowY, 82, 20,
                            Component.literal(label(key)));
                    input.setMaxLength(12); input.setValue(value); input.setEditable(!saving);
                    input.setResponder(v -> edits.put(key, v));
                    input.setTooltip(Tooltip.create(Component.literal(key + "; enter a whole number.")));
                    addRenderableWidget(input);
                }
            }
            int footerY = y + panelHeight - 31;
            button("<", x + 10, footerY, 28, () -> { page--; rebuildWidgets(); }).active = page > 0 && !saving;
            button(">", x + 42, footerY, 28, () -> { page++; rebuildWidgets(); })
                    .active = (page + 1) * rows < keys.size() && !saving;
        }
        apply = button("Apply", x + panelWidth - 162, y + panelHeight - 31, 72, this::save);
        apply.active = worldEditable() && !saving;
        button("Done", x + panelWidth - 84, y + panelHeight - 31, 72, this::onClose);
    }
    private Button button(String name, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(name), b -> action.run())
                .bounds(x, y, w, 20).build());
    }
    private boolean worldEditable() {
        return server != null && Minecraft.getInstance().getSingleplayerServer() == server
                && snapshot != null;
    }
    private void setPercent(int requested) {
        try {
            ReaderPreferences.save(HandbookScreen.preferencesPath(), requested);
            percent = ReaderPreferences.normalize(requested);
            status = "Reader size saved on this computer."; error = false;
        } catch (java.io.IOException exception) { status = "Reader save failed: " + exception.getMessage(); error = true; }
    }
    private void save() {
        if (!worldEditable() || saving || edits.isEmpty()) return;
        saving = true; apply.active = false; status = "Saving current-world settings..."; error = false;
        Map<String, String> changes = Map.copyOf(edits);
        EmeraldConfig expected = snapshot;
        server.execute(() -> {
            EmeraldConfig result = null;
            String failure = null;
            try {
                if (Minecraft.getInstance().getSingleplayerServer() != server)
                    throw new java.io.IOException("The world has closed; nothing was saved.");
                result = EmeraldConfig.update(server.getWorldPath(LevelResource.DATA), expected, changes);
            } catch (Exception exception) { failure = exception.getMessage(); }
            EmeraldConfig saved = result;
            String message = failure;
            minecraft.execute(() -> {
                saving = false;
                if (saved != null) {
                    snapshot = saved; edits.clear(); error = false;
                    status = "Saved and applied to this world.";
                } else { error = true; status = "Not saved: " + message; }
                if (minecraft.gui.screen() == this) rebuildWidgets();
            });
        });
        rebuildWidgets();
    }
    private static String label(String key) {
        String text = key.replace("village_prosperity.", "Village: ")
                .replace("village_banks.", "Bank: ").replace("economic_clock.", "Clock: ")
                .replace("onboarding.join_hint_enabled", "Starting book and discovery hint")
                .replace("banker.", "Banker: ").replace("transactions.", "Transactions: ")
                .replace("market.", "Market: ").replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE0101814);
        g.fill(x, y, x + panelWidth, y + panelHeight, 0xFF19392D);
        g.fill(x, y, x + panelWidth, y + 28, 0xFF10271F);
    }
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, font.plainSubstrByWidth(title.getString(), panelWidth - 20), x + 10, y + 10, 0xFFFFFFFF, false);
        g.text(font, "Reader text: " + percent + "%", x + 10, y + 42, 0xFFE7EDDF, false);
        String scope = worldEditable() ? "CURRENT WORLD (not global defaults)"
                : "World settings belong to the server";
        g.text(font, font.plainSubstrByWidth(scope, panelWidth - 20), x + 10, y + 66, 0xFFE3C579, false);
        if (worldEditable()) {
            List<String> keys = new ArrayList<>(snapshot.values().keySet());
            for (int row = 0; row < rows && page * rows + row < keys.size(); row++) {
                String key = keys.get(page * rows + row);
                String name = label(key);
                int rowY = y + 99 + row * 27;
                g.text(font, font.plainSubstrByWidth(name, panelWidth - 112), x + 10, rowY, 0xFFF0F1E6, false);
            }
            g.text(font, "Page " + (page + 1) + "/" + ((keys.size() + rows - 1) / rows),
                    x + 77, y + panelHeight - 25, 0xFFE7EDDF, false);
        } else {
            Component explanation = Component.literal(server == null && minecraft.player != null
                    ? "You are connected to a remote server. Reader size is local. Only the server "
                        + "administrator can change economy and village settings in the world's data folder."
                    : "Open a single-player world first to edit its economy and village settings. "
                        + "Reader size works here and is saved separately. No world files are changed from the title screen.");
            int lineY = y + 95;
            for (var line : font.split(explanation, panelWidth - 28)) {
                g.text(font, line, x + 14, lineY, 0xFFE7EDDF, false); lineY += 13;
            }
        }
        if (!status.isEmpty()) {
            String shortStatus = font.plainSubstrByWidth(status, panelWidth - 24);
            g.text(font, shortStatus, x + 12, y + panelHeight - 49, error ? 0xFFFFAC93 : 0xFFBBEAB5, false);
            if (my >= y + panelHeight - 53 && my <= y + panelHeight - 36)
                g.setTooltipForNextFrame(font, Component.literal(status), mx, my);
        }
    }
    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
