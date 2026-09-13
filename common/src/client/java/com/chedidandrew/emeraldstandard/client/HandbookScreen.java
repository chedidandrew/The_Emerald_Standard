package com.chedidandrew.emeraldstandard.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Wide, chapter-based reader; all body content remains reachable at every supported GUI scale. */
public final class HandbookScreen extends Screen {
    private final Screen parent;
    private HandbookLayout layout;
    private int chapter;
    private int chapterOffset;
    private int scroll;
    private int percent = ReaderPreferences.DEFAULT_PERCENT;
    private String query = "";
    private String status = "";
    private EditBox search;
    private List<Integer> matches = List.of();
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private record SectionStart(String id, int line) { }
    private final List<SectionStart> sectionStarts = new ArrayList<>();
    private Button previous;
    private Button next;
    private final java.util.Map<String, List<HandbookRecipes.Recipe>> recipeCache = new java.util.HashMap<>();
    private final List<HandbookRecipeCard> recipeCards = new ArrayList<>();
    private final RecipeAnimation recipeAnimation = new RecipeAnimation();
    private boolean recipesPaused;
    private long recipeFrame;

    public HandbookScreen(Screen parent) {
        super(Component.literal("The Emerald Standard: Starter Handbook"));
        this.parent = parent;
        try { percent = ReaderPreferences.load(preferencesPath()); }
        catch (java.io.IOException exception) { status = "Could not read preferences; using 90%."; }
    }

    public static Path preferencesPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("the_emerald_standard-client.properties");
    }

    @Override
    protected void init() {
        try { percent = ReaderPreferences.load(preferencesPath()); }
        catch (java.io.IOException exception) { status = "Could not read reader preferences."; }
        layout = HandbookLayout.fit(width, height);
        int x = layout.x(), y = layout.y();
        search = new EditBox(font, x + 10, y + 35, layout.sidebar() - 12, 20,
                Component.literal("Search handbook"));
        search.setMaxLength(100);
        search.setValue(query);
        search.setHint(Component.literal("Search topics..."));
        search.setResponder(value -> {
            query = value;
            chapterOffset = 0;
            rebuildWidgets();
            search.setFocused(true);
            setFocused(search);
        });
        addRenderableWidget(search);
        matches = java.util.stream.IntStream.range(0, HandbookChapters.ALL.size())
                .filter(i -> HandbookChapters.matches(HandbookChapters.ALL.get(i), query))
                .boxed().toList();
        chapterOffset = Math.max(0, Math.min(chapterOffset,
                Math.max(0, matches.size() - layout.visibleChapters())));
        for (int row = 0; row < layout.visibleChapters() && chapterOffset + row < matches.size(); row++) {
            int index = matches.get(chapterOffset + row);
            String name = HandbookChapters.ALL.get(index).name();
            Button button = button(font.plainSubstrByWidth(name, layout.sidebar() - 22),
                    x + 8, y + 61 + row * 23, layout.sidebar() - 8,
                    () -> select(index));
            button.setTooltip(GuiTooltips.widget(Component.literal(name)));
            if (chapter == index) button.active = false;
        }
        button("Up", x + 8, y + layout.height() - 32, (layout.sidebar() - 12) / 2,
                () -> { chapterOffset -= layout.visibleChapters(); rebuildWidgets(); }).active = chapterOffset > 0;
        button("Down", x + 12 + (layout.sidebar() - 12) / 2, y + layout.height() - 32,
                (layout.sidebar() - 12) / 2,
                () -> { chapterOffset += layout.visibleChapters(); rebuildWidgets(); })
                .active = chapterOffset + layout.visibleChapters() < matches.size();
        button("A-", x + layout.width() - 142, y + 8, 28, () -> resizeText(-10));
        button("A+", x + layout.width() - 110, y + 8, 28, () -> resizeText(10));
        button("Settings", x + layout.width() - 78, y + 8, 68,
                () -> minecraft.gui.setScreen(new EmeraldSettingsScreen(this)));
        int bodyX = layout.bodyX(), footer = y + layout.height() - 32;
        int available = layout.width() - layout.sidebar() - 24;
        int bw = Math.min(74, (available - 12) / 3);
        previous = button("Previous", bodyX, footer, bw, () -> select(chapter - 1));
        next = button("Next", bodyX + bw + 5, footer, bw, () -> select(chapter + 1));
        button("Done", bodyX + 2 * (bw + 5), footer, bw, this::onClose);
        previous.active = chapter > 0;
        next.active = chapter + 1 < HandbookChapters.ALL.size();
        rebuildText();
        if (!recipeCards.isEmpty()) {
            Button pause = button(recipesPaused ? "Resume recipes" : "Pause recipes",
                    x + layout.width() - 101, y + 33, 90, () -> {
                        recipesPaused = !recipesPaused;
                        rebuildWidgets();
                    });
            pause.setTooltip(GuiTooltips.widget(Component.literal(
                    "Cycle every 0.9 seconds. Hover over a recipe to pause and read item names. No items are consumed.")));
        }
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, Math.max(20, w), 20).build());
    }

    private void select(int index) {
        chapter = Math.max(0, Math.min(HandbookChapters.ALL.size() - 1, index));
        scroll = 0;
        rebuildWidgets();
        // A long chapter should open at the searched topic, preferring a heading match.
        String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
        if (!needle.isEmpty()) {
            var target = sectionStarts.stream().filter(s -> HandbookChapters.title(s.id()).getString()
                    .toLowerCase(java.util.Locale.ROOT).contains(needle)).findFirst()
                    .orElseGet(() -> sectionStarts.stream().filter(s -> HandbookChapters.body(s.id()).getString()
                            .toLowerCase(java.util.Locale.ROOT).contains(needle)).findFirst().orElse(null));
            if (target != null) scroll = Math.min(maxScroll(), target.line());
        }
    }

    private void resizeText(int difference) {
        int selected = ReaderPreferences.normalize(percent + difference);
        try {
            ReaderPreferences.save(preferencesPath(), selected);
            percent = selected;
            status = "";
            rebuildText();
        } catch (java.io.IOException exception) { status = "Text setting was not saved."; }
    }

    private void rebuildText() {
        lines.clear();
        sectionStarts.clear();
        recipeCards.clear();
        int wrapWidth = Math.max(24, (int) (layout.bodyWidth() / textScale()));
        for (String section : HandbookChapters.ALL.get(chapter).sections()) {
            int titleLine = lines.size();
            sectionStarts.add(new SectionStart(section, titleLine));
            var heading = font.split(HandbookChapters.title(section).copy()
                    .withStyle(style -> style.withBold(true).withColor(0x244C38)), wrapWidth);
            lines.addAll(heading);
            lines.add(FormattedCharSequence.EMPTY);
            var recipes = recipeCache.computeIfAbsent(section, HandbookRecipes::load);
            if (!recipes.isEmpty()) {
                HandbookRecipeCard card = new HandbookRecipeCard(titleLine, lines.size(),
                        Math.min(1.5f, Math.min(wrapWidth / (float) HandbookRecipeCard.WIDTH,
                                Math.max(12, (visibleLines() - heading.size() - 1) * 12f)
                                        / HandbookRecipeCard.HEIGHT)), recipes);
                recipeCards.add(card);
                for (int i = 0; i < card.rows(); i++) lines.add(FormattedCharSequence.EMPTY);
                String hint = recipes.stream().allMatch(HandbookRecipes.Recipe::serverRecipe)
                        ? "Known server recipes. " : "Default recipe (datapacks may change it). ";
                hint += "Hover for item names and to pause.";
                hint += recipes.stream().allMatch(HandbookRecipes.Recipe::serverRecipe)
                        ? " Matching ingredient alternatives cycle automatically."
                        : switch (section) {
                            case "recipe_desk" -> " Any matching planks work, including mixed wood types. Place the desk and right-click to open your account.";
                            case "recipe_fence" -> " Four sticks, yellow dye and black dye make four manual caution fences.";
                            default -> " Book + Emerald, in any two slots. Craft again if lost; use the handbook to open it.";
                        };
                lines.addAll(font.split(Component.literal(hint), wrapWidth));
                lines.add(FormattedCharSequence.EMPTY);
                lines.addAll(font.split(HandbookChapters.body(section), wrapWidth));
            } else lines.addAll(font.split(HandbookChapters.body(section), wrapWidth));
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(FormattedCharSequence.EMPTY);
        }
        scroll = Math.min(scroll, maxScroll());
    }
    private float textScale() { return percent / 100.0f; }
    private int visibleLines() { return Math.max(1, (int) ((layout.bodyHeight() - 10) / (12 * textScale()))); }
    private int maxScroll() { return HandbookLayout.maximumScroll(lines.size(), visibleLines()); }
    private void moveScroll(int delta) { scroll = Math.max(0, Math.min(maxScroll(), scroll + delta)); }

    // Read-only observations for the opt-in real-client regression, not alternative UI logic.
    int selectedChapter() { return chapter; }
    int scrollPosition() { return scroll; }
    int scrollLimit() { return maxScroll(); }
    int textPercent() { return percent; }
    int matchingChapters() { return matches.size(); }
    boolean textFitsBody() {
        return lines.stream().allMatch(line -> font.width(line) * textScale() <= layout.bodyWidth() + 1);
    }
    int recipeCardCount() { return recipeCards.size(); }
    boolean recipesPaused() { return recipesPaused; }
    long recipeFrame() { return recipeFrame; }
    int recipeTitleLine(int index) { return recipeCards.get(index).titleLine(); }
    boolean recipeCardsFitViewport() {
        return recipeCards.stream().allMatch(card ->
                card.scale() * HandbookRecipeCard.HEIGHT + (card.line() - card.titleLine()) * 12
                        <= visibleLines() * 12 + 0.01
                && card.scale() * HandbookRecipeCard.WIDTH * textScale() <= layout.bodyWidth() + 0.01);
    }
    int[] firstRecipeIngredientCenter() {
        var card = recipeCards.getFirst();
        return new int[] {(int) (layout.bodyX() + 35 * card.scale() * textScale()),
                (int) (layout.bodyY() + ((card.line() - scroll) * 12 + 29 * card.scale()) * textScale())};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xE0101814);
        g.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), 0xFFEBE6D5);
        g.fill(layout.x(), layout.y(), layout.x() + layout.sidebar() + 4,
                layout.y() + layout.height(), 0xFF19392D);
        g.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + 30, 0xFF10271F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, font.plainSubstrByWidth("Starter Handbook", layout.width() - 166),
                layout.x() + 10, layout.y() + 13, 0xFFF4F1DF, false);
        String name = HandbookChapters.ALL.get(chapter).name();
        if (!recipeCards.isEmpty() && font.width(name) > layout.bodyWidth() - 95) name = "Recipes";
        g.text(font, font.plainSubstrByWidth(name, layout.bodyWidth() - (recipeCards.isEmpty() ? 0 : 95)),
                layout.bodyX(), layout.y() + 40, 0xFF19392D, false);
        double localMouseX = (mouseX - layout.bodyX()) / textScale();
        double localMouseY = (mouseY - layout.bodyY()) / textScale();
        boolean insideBody = mouseX >= layout.bodyX() && mouseX < layout.bodyX() + layout.bodyWidth()
                && mouseY >= layout.bodyY() && mouseY < layout.bodyY() + visibleLines() * 12 * textScale();
        boolean hoveringRecipe = insideBody && recipeCards.stream().anyMatch(card ->
                card.contains(localMouseX, localMouseY - (card.line() - scroll) * 12));
        recipeFrame = recipeAnimation.frame(System.nanoTime() / 1_000_000,
                recipesPaused || hoveringRecipe || recipeCards.isEmpty());
        ItemStack hoveredItem = ItemStack.EMPTY;
        g.enableScissor(layout.bodyX(), layout.bodyY(), layout.bodyX() + layout.bodyWidth(),
                layout.bodyY() + (int) (visibleLines() * 12 * textScale()));
        g.pose().pushMatrix();
        g.pose().translate(layout.bodyX(), layout.bodyY());
        g.pose().scale(textScale(), textScale());
        for (int i = 0; i < visibleLines() && scroll + i < lines.size(); i++) {
            g.text(font, lines.get(scroll + i), 0, i * 12, 0xFF26352D, false);
        }
        for (var card : recipeCards) {
            int cardY = (card.line() - scroll) * 12;
            if (cardY + card.rows() * 12 < 0 || cardY >= visibleLines() * 12) continue;
            ItemStack item = card.draw(g, font, cardY, recipeFrame, localMouseX, localMouseY);
            if (insideBody && !item.isEmpty()) hoveredItem = item;
        }
        g.pose().popMatrix();
        g.disableScissor();
        if (!hoveredItem.isEmpty()) g.setTooltipForNextFrame(font, hoveredItem, mouseX, mouseY);
        if (maxScroll() > 0) {
            int trackX = layout.x() + layout.width() - 11;
            int trackHeight = layout.bodyHeight();
            int thumb = Math.max(12, trackHeight * visibleLines() / Math.max(1, lines.size()));
            int thumbY = layout.bodyY() + (trackHeight - thumb) * scroll / maxScroll();
            g.fill(trackX, layout.bodyY(), trackX + 3, layout.bodyY() + trackHeight, 0xFFC9C7B5);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumb, 0xFF397657);
        }
        String foot = status.isEmpty() ? "Chapter " + (chapter + 1) + "/" + HandbookChapters.ALL.size()
                + " | " + percent + "% | Scroll to read" : status;
        g.text(font, font.plainSubstrByWidth(foot, layout.bodyWidth()), layout.bodyX(),
                layout.y() + layout.height() - 45, 0xFF4F6253, false);
        if (matches.isEmpty()) g.text(font, "No topics found", layout.x() + 10,
                layout.y() + 67, 0xFFE3EADB, false);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false;
        if (x < layout.bodyX()) {
            chapterOffset += vertical > 0 ? -1 : 1;
            rebuildWidgets();
        } else moveScroll(vertical > 0 ? -3 : 3);
        return true;
    }
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused()) return super.keyPressed(event);
        switch (event.key()) {
            case GLFW.GLFW_KEY_DOWN -> moveScroll(1);
            case GLFW.GLFW_KEY_UP -> moveScroll(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> moveScroll(visibleLines() - 1);
            case GLFW.GLFW_KEY_PAGE_UP -> moveScroll(1 - visibleLines());
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
            case GLFW.GLFW_KEY_LEFT -> select(chapter - 1);
            case GLFW.GLFW_KEY_RIGHT -> select(chapter + 1);
            default -> { return super.keyPressed(event); }
        }
        return true;
    }
    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
