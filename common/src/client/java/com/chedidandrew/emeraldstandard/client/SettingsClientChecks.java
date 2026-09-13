package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

/** Exercises actual paginated settings widgets, without any server or world save. */
final class SettingsClientChecks {
    static void verify(Minecraft game) throws Exception {
        EmeraldConfig active = EmeraldConfig.current();
        java.util.Set<String> hovered = new java.util.HashSet<>();
        for (int scale : new int[] {2, 4}) {
            game.options.guiScale().set(scale);
            game.resizeGui();
            EmeraldSettingsScreen screen = EmeraldSettingsScreen.preview(EmeraldConfig.defaults());
            game.gui.setScreen(screen);
            do {
                for (var child : screen.children()) {
                    if (child instanceof EditBox input) input.setValue("987");
                    if (child instanceof AbstractWidget widget) {
                        require(widget.getX() >= 0 && widget.getY() >= 0
                                && widget.getX() + widget.getWidth() <= screen.width
                                && widget.getY() + widget.getHeight() <= screen.height, "settings widget out of bounds");
                        String key = screen.settingAt((screen.width - Math.min(680, screen.width - 20)) / 2 + 12,
                                widget.getY() + 8);
                        if (key != null) {
                            hovered.add(key);
                            require(SettingsHelp.description(key).length() > 70, "missing effect description");
                            require(screen.settingAt(widget.getX() + 1, widget.getY() + 8) == null,
                                    "label hover overlaps input control");
                        }
                    }
                }
                if (!button(screen, ">").active) break;
                ReaderClientChecks.press(screen, ">");
            } while (true);
            require(!screen.draftValues().equals(EmeraldConfig.defaults().values()), "multi-page edits not exercised");
            ReaderClientChecks.press(screen, "A+");
            ReaderClientChecks.press(screen, "Reset");
            require(screen.draftValues().equals(EmeraldConfig.defaults().values()), "Reset missed off-page edits");
            require(screen.textPercent() == ReaderPreferences.DEFAULT_PERCENT, "Reset missed reader size");
            Button reset = button(screen, "Reset"), apply = button(screen, "Apply"), done = button(screen, "Done");
            require(reset.getX() + reset.getWidth() < apply.getX()
                    && apply.getX() + apply.getWidth() < done.getX(), "footer buttons overlap");
            require(!apply.active, "preview can write world settings");
            require(EmeraldConfig.current() == active, "preview changed active configuration");
            screen.changeToggle(EmeraldConfig.FORCED_DEVELOPMENT_KEY,true);
            require(game.gui.screen() instanceof ForcedDevelopmentConfirmationScreen,"debug mode bypassed confirmation");
            var warning = (ForcedDevelopmentConfirmationScreen)game.gui.screen();
            require(warning.warningFits(),"debug warning overlaps buttons at GUI scale "+scale);
            ReaderClientChecks.press(warning,"Cancel");
            require(screen.draftValues().get(EmeraldConfig.FORCED_DEVELOPMENT_KEY).equals("false"),"Cancel enabled debug");
            screen.changeToggle(EmeraldConfig.FORCED_DEVELOPMENT_KEY,true);
            game.gui.screen().onClose();
            require(screen.draftValues().get(EmeraldConfig.FORCED_DEVELOPMENT_KEY).equals("false"),"Escape enabled debug");
            screen.changeToggle(EmeraldConfig.FORCED_DEVELOPMENT_KEY,true);
            ReaderClientChecks.press(game.gui.screen(),"I understand");
            require(screen.draftValues().get(EmeraldConfig.FORCED_DEVELOPMENT_KEY).equals("true"),"consent not retained");
            require(EmeraldConfig.current()==active && !active.forcedVillageDevelopment(),"confirmation applied without Apply");
            ReaderClientChecks.press(screen,"Reset");
            require(screen.draftValues().get(EmeraldConfig.FORCED_DEVELOPMENT_KEY).equals("false"),"Reset missed debug");
        }
        require(hovered.equals(EmeraldConfig.defaults().values().keySet()), "some labels have no hover descriptions");
        game.options.guiScale().set(2);
        game.resizeGui();
        game.gui.setScreen(new EmeraldSettingsScreen(null));
        ReaderClientChecks.press(game.gui.screen(), "Reset");
        require(!button((EmeraldSettingsScreen) game.gui.screen(), "Apply").active, "title screen can edit world");
    }

    static EmeraldSettingsScreen fixture(Minecraft game) {
        EmeraldSettingsScreen screen = EmeraldSettingsScreen.preview(EmeraldConfig.defaults());
        game.gui.setScreen(screen);
        ReaderClientChecks.press(screen, ">");
        return screen;
    }
    static void warningFixture(Minecraft game) {
        var screen=EmeraldSettingsScreen.preview(EmeraldConfig.defaults());
        game.gui.setScreen(screen);
        screen.changeToggle(EmeraldConfig.FORCED_DEVELOPMENT_KEY,true);
    }
    private static Button button(EmeraldSettingsScreen screen, String text) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(text)).findFirst().orElseThrow();
    }
    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
