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
        var desk = HandbookRecipes.load("recipe_desk");
        var book = HandbookRecipes.load("recipe_book");
        var fence = HandbookRecipes.load("recipe_fence");
        check(fence.size() == 1 && !fence.getFirst().shapeless(), "missing fence visual");
        check(fence.getFirst().frame(0)[1].is(net.minecraft.world.item.Items.DYE.yellow())
                && fence.getFirst().frame(0)[4].is(net.minecraft.world.item.Items.DYE.black())
                && fence.getFirst().frame(0)[6].isEmpty()
                && fence.getFirst().result(0).getCount() == 4, "wrong fence recipe diagram");
        check(desk.size() == 1 && !desk.getFirst().shapeless(), "missing desk visual");
        check(desk.getFirst().variants() >= 10, "plank alternatives did not resolve");
        check(desk.getFirst().frame(0)[1].is(net.minecraft.world.item.Items.EMERALD), "wrong emerald slot");
        check(desk.getFirst().frame(0)[4].is(net.minecraft.world.item.Items.BOOK), "wrong book slot");
        check(!desk.getFirst().frame(0)[6].is(desk.getFirst().frame(1)[6].getItem()), "planks do not cycle");
        check(book.size() == 1 && book.getFirst().shapeless(), "missing shapeless visual");
        check(book.getFirst().frame(0)[1].is(net.minecraft.world.item.Items.EMERALD)
                && book.getFirst().frame(1)[2].is(net.minecraft.world.item.Items.EMERALD), "shapeless slots do not cycle");
        press(reader, "Previous"); press(reader, "Previous");
        check(reader.recipeCardCount() == 4, "crafting chapter must contain all four cards");
        var newspaper=HandbookRecipes.load("recipe_newspaper");
        check(newspaper.size()==1&&newspaper.getFirst().shapeless()
                &&newspaper.getFirst().result(0).getItem() instanceof com.chedidandrew.emeraldstandard.minecraft.NewspaperItem,
                "missing newspaper visual");
        press(reader, "Pause recipes");
        check(reader.recipesPaused(), "recipe pause did not apply");
        press(reader, "Resume recipes");
        check(!reader.recipesPaused(), "recipe resume did not apply");
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
    static HandbookScreen craftingFixture(Minecraft game) {
        HandbookScreen reader = new HandbookScreen(null);
        game.gui.setScreen(reader);
        for (int i = 0; i < 13; i++) press(reader, "Next");
        check(reader.recipeCardCount() == 4, "crafting cards missing at GUI scale");
        check(reader.recipeCardsFitViewport(), "complete recipe and title do not fit the viewport");
        return reader;
    }
    static HandbookScreen guideFixture(Minecraft game, int chapter) {
        HandbookScreen reader = new HandbookScreen(null);
        game.gui.setScreen(reader);
        for (int i = 0; i < chapter; i++) press(reader, "Next");
        check(reader.textFitsBody(), "Long-form text exceeds its body width");
        return reader;
    }
    static void verifyLongForm(Minecraft game) throws Exception {
        for (int percent : new int[] {80, 120}) {
            ReaderPreferences.save(HandbookScreen.preferencesPath(), percent);
            HandbookScreen reader = guideFixture(game, 0);
            for (int chapter = 0; chapter < HandbookChapters.ALL.size(); chapter++) {
                check(reader.textFitsBody(), "Long-form text overflows chapter " + chapter + " at " + percent);
                reader.setFocused(null);
                key(reader, GLFW.GLFW_KEY_END);
                check(reader.scrollPosition() == reader.scrollLimit(), "Long chapter end unreachable");
                key(reader, GLFW.GLFW_KEY_HOME);
                check(reader.scrollPosition() == 0, "Long chapter Home failed");
                if (chapter + 1 < HandbookChapters.ALL.size()) press(reader, "Next");
            }
        }
        HandbookScreen reader = guideFixture(game, 0);
        EditBox search = reader.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("Security:");
        check(reader.matchingChapters() == 1, "Detailed purpose not found by search");
        Button result = reader.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b -> b.getMessage().getString().startsWith("The Village"))
                .findFirst().orElseThrow();
        press(reader, result.getMessage().getString());
        check(reader.selectedChapter() == 10 && reader.scrollPosition() > 0,
                "Purpose search must jump inside the Fund chapter");
        check(reader.textFitsBody(), "Search landing text overflows");
        System.out.println("PASS long-form handbook real-font wrapping, all chapter ends, 80/120 text and topic search");
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
