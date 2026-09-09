package com.chedidandrew.emeraldstandard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Exercises the real widgets and real font after resource loading, only in smoke launches. */
final class ReaderClientChecks {
    private ReaderClientChecks() { }

    static void verify(Minecraft game) throws Exception {
        HandbookScreen reader = new HandbookScreen(null);
        game.gui.setScreen(reader);
        int expected = Boolean.getBoolean("the_emerald_standard.clientSmokeRestart") ? 110 : 90;
        check(reader.textPercent() == expected, "reader preference did not survive process restart");
        press(reader, "Next");
        check(reader.selectedChapter() == 1, "Next failed");
        press(reader, "Previous");
        check(reader.selectedChapter() == 0, "Previous failed");
        EditBox search = reader.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("zzzz-no-such-topic");
        check(reader.matchingChapters() == 0, "search did not filter");
        search = reader.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("deposit");
        check(reader.matchingChapters() > 0 && reader.matchingChapters() < 16, "topic search failed");
        search = reader.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("");
        check(reader.matchingChapters() == 16, "search reset lost chapters");
        reader.setFocused(null);
        key(reader, GLFW.GLFW_KEY_RIGHT);
        check(reader.selectedChapter() == 1, "keyboard chapter navigation failed");
        key(reader, GLFW.GLFW_KEY_LEFT);
        check(reader.selectedChapter() == 0, "keyboard previous chapter failed");
        for (int i = 0; i < 8; i++) press(reader, "A-");
        check(reader.textPercent() == 80, "A- minimum failed");
        for (int i = 0; i < 8; i++) press(reader, "A+");
        check(reader.textPercent() == 120, "A+ maximum failed");
        boolean longChapter = false;
        for (int chapter = 0; chapter < 16; chapter++) {
            reader.setFocused(null);
            key(reader, GLFW.GLFW_KEY_END);
            check(reader.scrollPosition() == reader.scrollLimit(), "last line unreachable in " + chapter);
            longChapter |= reader.scrollLimit() > 0;
            key(reader, GLFW.GLFW_KEY_HOME);
            check(reader.scrollPosition() == 0, "Home failed");
            if (reader.scrollLimit() > 0) {
                reader.mouseScrolled(reader.width - 30, 100, 0, -1);
                check(reader.scrollPosition() > 0, "mouse wheel failed");
                key(reader, GLFW.GLFW_KEY_PAGE_DOWN);
                check(reader.scrollPosition() <= reader.scrollLimit(), "page scroll overflow");
            }
            if (chapter < 15) press(reader, "Next");
        }
        check(longChapter, "real-font scrolling was not exercised");
        press(reader, "Settings");
        EmeraldSettingsScreen settings = (EmeraldSettingsScreen) game.gui.screen();
        check(!button(settings, "Apply").active, "title screen can write unopened world");
        press(settings, "A-");
        check(settings.textPercent() == 110, "title-screen text preference failed");
        press(settings, "Done");
        check(game.gui.screen() == reader && reader.textPercent() == 110, "return from Settings stale");
        press(reader, "Settings");
        settings = (EmeraldSettingsScreen) game.gui.screen();
        press(settings, "Read handbook");
        HandbookScreen nested = (HandbookScreen) game.gui.screen();
        press(nested, "A-");
        press(nested, "Done");
        check(game.gui.screen() == settings && settings.textPercent() == 100, "nested reader return stale");
        press(settings, "Done");
        game.gui.setScreen(new HandbookScreen(null));
        check(((HandbookScreen) game.gui.screen()).textPercent() == 100, "reopened reader preference stale");
        ReaderPreferences.save(HandbookScreen.preferencesPath(), 90);
        game.gui.setScreen(new HandbookScreen(null));
    }

    static void press(Screen screen, String label) {
        Button button = button(screen, label);
        check(button.active, "inactive button: " + label);
        var click = new net.minecraft.client.input.MouseButtonEvent(
                button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0,
                new net.minecraft.client.input.MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        check(screen.mouseClicked(click, false), "click was not handled: " + label);
        screen.mouseReleased(click);
    }
    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void key(Screen screen, int key) { screen.keyPressed(new KeyEvent(key, 0, 0)); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
