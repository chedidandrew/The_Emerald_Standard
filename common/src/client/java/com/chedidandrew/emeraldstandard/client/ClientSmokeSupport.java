package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldHandbook;
import com.chedidandrew.emeraldstandard.minecraft.HandbookReaderItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/** Explicitly opt-in CI helper. Normal game launches never run or stop through this path. */
public final class ClientSmokeSupport {
    private static final String SMOKE_PROPERTY = "the_emerald_standard.clientSmoke";
    private static final int WRITTEN_BOOK_TEXT_WIDTH = 114;
    private static final int WRITTEN_BOOK_VISIBLE_LINES = 14;
    private ClientSmokeSupport() { }

    public static void initialized(Logger logger) {
        initialized(logger, () -> { });
    }

    public static void initialized(Logger logger, Runnable loaderConfigurationCheck) {
        logger.info("The Emerald Standard client initialized");
        if (!Boolean.getBoolean(SMOKE_PROPERTY)) return;
        Thread smoke = new Thread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
                boolean ready = false;
                while (System.nanoTime() < deadline) {
                    ready = onClient(minecraft, () -> minecraft.gui.overlay() == null
                            && !Component.translatable(HandbookChapters.PREFIX + "first_steps.title")
                                    .getString().equals(HandbookChapters.PREFIX + "first_steps.title"));
                    if (ready) break;
                    Thread.sleep(200);
                }
                if (!ready) throw new IllegalStateException("Client resources/language never became ready");
                onClient(minecraft, () -> {
                    loaderConfigurationCheck.run();
                    // Probe the platform, not the mod UI: Minecraft initializes these standard
                    // shapes (including NOT_ALLOWED) when its ordinary widgets first render.
                    for (int shape : new int[] {0x36001, 0x36002, 0x36003, 0x36004,
                            0x36005, 0x36006, 0x36009, 0x3600A}) {
                        long cursor = org.lwjgl.glfw.GLFW.glfwCreateStandardCursor(shape);
                        if (cursor == 0) throw new IllegalStateException("Standard cursor unavailable: " + shape);
                        org.lwjgl.glfw.GLFW.glfwDestroyCursor(cursor);
                    }
                    logger.info("The Emerald Standard standard cursor platform probe passed");
                    verifyHandbookPagesFit(minecraft);
                    if (!(BuiltInRegistries.ITEM.getValue(EmeraldHandbook.HANDBOOK_ID) instanceof HandbookReaderItem))
                        throw new IllegalStateException("Custom handbook item was not registered");
                    for (var chapter : HandbookChapters.ALL) for (String section : chapter.sections()) {
                        String key = HandbookChapters.READER_PREFIX + section;
                        if (HandbookChapters.title(section).getString().equals(key + ".title")
                                || HandbookChapters.body(section).getString().equals(key + ".body"))
                            throw new IllegalStateException("Missing handbook section: " + section);
                    }
                    logger.info("Emerald Handbook page layout verified for {} pages", EmeraldHandbook.PAGE_COUNT);
                    minecraft.getWindow().setWindowed(1280, 800);
                    minecraft.gui.setScreen(new HandbookScreen(null));
                    return null;
                });
                if (Boolean.getBoolean("the_emerald_standard.clientTooltipsOnly")) {
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verify(minecraft); ReaderClientChecks.verifyLongForm(minecraft); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        return null;
                    });
                    for (int scale : new int[] {2,4}) for (String mode : new String[] {"label","field","focus"}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280,1000);
                            minecraft.options.guiScale().set(scale);minecraft.resizeGui();
                            minecraft.gui.setScreen(new TooltipClientChecks(mode));return null;
                        });
                        Thread.sleep(500);
                        onClient(minecraft, () -> {((TooltipClientChecks)minecraft.gui.screen()).verifyObserved();return null;});
                        capture(minecraft,"tooltip-"+mode+"-scale-"+scale+".png");
                    }
                    logger.info("The Emerald Standard settings label, field and keyboard tooltip checks passed");
                    onClient(minecraft, () -> {minecraft.stop();return null;});return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientFencesOnly")) {
                    onClient(minecraft, () -> {
                        com.chedidandrew.emeraldstandard.minecraft.ConstructionFenceModelSelfTest.verify();
                        try { ReaderClientChecks.verify(minecraft); ReaderClientChecks.verifyLongForm(minecraft); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        return null;
                    });
                    for (int scale : new int[] {2, 4}) for (int view : new int[] {0, 1, 2, 3}) {
                        final int preview = view;
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280, 1000);
                            minecraft.options.guiScale().set(scale); minecraft.resizeGui();
                            minecraft.gui.setScreen(new ConstructionFenceVisualChecks(preview)); return null;
                        });
                        Thread.sleep(400); capture(minecraft, "fence-scale-" + scale + "-view-" + view + ".png");
                    }
                    logger.info("The Emerald Standard fence models, 16 native connections, item and handbook checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; }); return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientCrewsOnly")) {
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verify(minecraft); ReaderClientChecks.verifyLongForm(minecraft); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        return null;
                    });
                    for (int scale : new int[] {2,4}) for (int frame : new int[] {-1,0,3,7,12}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280,1000);
                            minecraft.options.guiScale().set(scale); minecraft.resizeGui();
                            ConstructionVisualChecks.verifyAnimation();
                            minecraft.gui.setScreen(new ConstructionVisualChecks(frame)); return null;
                        });
                        Thread.sleep(400); capture(minecraft,"crew-scale-"+scale+"-frame-"+frame+".png");
                    }
                    logger.info("The Emerald Standard stable crew renderer, phased hammer and handbook checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; }); return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientPrioritiesOnly")) {
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verify(minecraft); ReaderClientChecks.verifyLongForm(minecraft); }
                        catch(Exception e) {throw new IllegalStateException(e);}
                        return null;
                    });
                    for(int scale:new int[]{2,4}) for(String kind:new String[]{"browser","compare","live","yesterday","report","town"}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280,1000);
                            minecraft.options.guiScale().set(scale);minecraft.resizeGui();
                            minecraft.gui.setScreen(PriorityClientChecks.fixture(kind));return null;
                        });
                        Thread.sleep(500);capture(minecraft,"priority-"+kind+"-scale-"+scale+".png");
                    }
                    logger.info("The Emerald Standard priority browser, comparison, reports and handbook checks passed");
                    onClient(minecraft, () -> {minecraft.stop();return null;});return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientNewsOnly")) {
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verify(minecraft); ReaderClientChecks.verifyLongForm(minecraft); }
                        catch(Exception error) { throw new IllegalStateException(error); }
                        return null;
                    });
                    for(int scale:new int[]{2,4}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280,1000);
                            minecraft.options.guiScale().set(scale); minecraft.resizeGui();
                            NewspaperClientChecks.verify(minecraft); return null;
                        });
                        Thread.sleep(500);capture(minecraft,"newspaper-scale-"+scale+".png");
                        for(String part:new String[]{"contents","article","browser","item-states"}) {
                            onClient(minecraft,()->{NewspaperClientChecks.showPart(minecraft,part);return null;});
                            Thread.sleep(500);capture(minecraft,"newspaper-"+part+"-scale-"+scale+".png");
                        }
                        onClient(minecraft, () -> {
                            var reader=ReaderClientChecks.craftingFixture(minecraft);
                            for(int line=0;line<reader.recipeTitleLine(3);line++)
                                reader.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,0,0));
                            return null;
                        });
                        Thread.sleep(500);capture(minecraft,"newspaper-recipe-scale-"+scale+".png");
                    }
                    logger.info("The Emerald Standard newspaper reader, controls and four handbook recipes passed");
                    onClient(minecraft, () -> {minecraft.stop(); return null;}); return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientCreativeOnly")) {
                    Thread.sleep(750);
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verify(minecraft); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        return null;
                    });
                    for(int scale:new int[] {2,4}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280,1000);
                            minecraft.options.guiScale().set(scale); minecraft.resizeGui();
                            try { ReaderClientChecks.verifyLongForm(minecraft); }
                            catch (Exception error) { throw new IllegalStateException(error); }
                            minecraft.gui.setScreen(new CreativeCatalogPreview()); return null;
                        });
                        Thread.sleep(350); capture(minecraft,"creative-catalog-scale-"+scale+".png");
                        for(int recipe=0;recipe<4;recipe++) {
                            final int index=recipe;
                            onClient(minecraft, () -> {
                                var reader=ReaderClientChecks.craftingFixture(minecraft);
                                for(int line=0;line<reader.recipeTitleLine(index);line++)
                                    reader.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,0,0));
                                return null;
                            });
                            Thread.sleep(350); capture(minecraft,"creative-recipe-"+recipe+"-scale-"+scale+".png");
                        }
                    }
                    logger.info("The Emerald Standard creative catalog, all three recipes and handbook layout checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; }); return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientCommoditiesOnly")) {
                    onClient(minecraft, () -> { BankerClientChecks.verifyTextFits(minecraft); return null; });
                    for (int guiScale : new int[] {2, 4}) {
                        for (var asset : com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS) {
                            onClient(minecraft, () -> {
                                minecraft.getWindow().setWindowed(1280, 1000);
                                minecraft.options.guiScale().set(guiScale); minecraft.resizeGui();
                                minecraft.gui.setScreen(BankerClientChecks.fixture(false, asset.ticker(), true));
                                return null;
                            });
                            Thread.sleep(300);
                            capture(minecraft, "commodity-market-" + asset.ticker().toLowerCase(java.util.Locale.ROOT)
                                    + "-scale-" + guiScale + ".png");
                        }
                    }
                    logger.info("The Emerald Standard commodity investment GUI and type-label checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; });
                    return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientGuardsOnly")) {
                    for (int guiScale : new int[] {2, 4}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280, 1000);
                            minecraft.options.guiScale().set(guiScale); minecraft.resizeGui();
                            try { ReaderClientChecks.verifyLongForm(minecraft); }
                            catch (Exception error) { throw new IllegalStateException(error); }
                            minecraft.gui.setScreen(BankerClientChecks.guardFixture()); return null;
                        });
                        Thread.sleep(500);
                        capture(minecraft, "guard-town-scale-" + guiScale + ".png");
                        onClient(minecraft, () -> {
                            var settings = EmeraldSettingsScreen.preview(
                                    com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig.defaults());
                            minecraft.gui.setScreen(settings);
                            int rows = Math.max(1, (Math.min(550, minecraft.getWindow().getGuiScaledHeight() - 20) - 151) / 27);
                            var keys = new java.util.ArrayList<>(com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig.defaults().values().keySet());
                            int page = keys.indexOf(com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig.GUARDS_ENABLED_KEY) / rows;
                            for (int i = 0; i < page; i++) ReaderClientChecks.press(settings, ">");
                            settings.changeToggle(com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig.GUARDS_ENABLED_KEY, false);
                            if (!settings.draftValues().get(com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig.GUARDS_ENABLED_KEY).equals("false"))
                                throw new IllegalStateException("Guard toggle draft");
                            settings.resetDefaults();
                            if (!settings.draftValues().equals(com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig.defaults().values()))
                                throw new IllegalStateException("Guard settings Reset");
                            return null;
                        });
                        Thread.sleep(500);
                        capture(minecraft, "guard-settings-scale-" + guiScale + ".png");
                    }
                    logger.info("The Emerald Standard optional guard GUI, handbook and settings checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; }); return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientDistrictMapOnly")) {
                    for (int guiScale : new int[] {2, 4}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280, 1000);
                            minecraft.options.guiScale().set(guiScale); minecraft.resizeGui();
                            minecraft.gui.setScreen(BankerClientChecks.districtMapFixture(false)); return null;
                        });
                        Thread.sleep(500);
                        Thread.sleep(2000);
                        capture(minecraft, "district-map-scale-" + guiScale + ".png");
                        onClient(minecraft, () -> {
                            minecraft.gui.setScreen(BankerClientChecks.districtMapFixture(true)); return null;
                        });
                        Thread.sleep(500);
                        Thread.sleep(2000);
                        capture(minecraft, "district-map-hover-scale-" + guiScale + ".png");
                        onClient(minecraft, () -> {
                            minecraft.gui.setScreen(BankerClientChecks.districtMapFixture(2)); return null;
                        });
                        Thread.sleep(2500);
                        capture(minecraft, "district-map-boundary-scale-" + guiScale + ".png");
                    }
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verifyLongForm(minecraft); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                        return null;
                    });
                    logger.info("The Emerald Standard district map rendering and navigation checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; });
                    return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientHandbookOnly")) {
                    Thread.sleep(750);
                    onClient(minecraft, () -> {
                        try { ReaderClientChecks.verify(minecraft); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                        return null;
                    });
                    for (int guiScale : new int[] {2, 4}) {
                        onClient(minecraft, () -> {
                            minecraft.getWindow().setWindowed(1280, 1000);
                            minecraft.options.guiScale().set(guiScale); minecraft.resizeGui();
                            if (minecraft.getWindow().getGuiScale() != guiScale)
                                throw new IllegalStateException("Handbook GUI scale was clamped");
                            try { ReaderClientChecks.verifyLongForm(minecraft); }
                            catch (Exception exception) { throw new IllegalStateException(exception); }
                            return null;
                        });
                        Thread.sleep(300);
                        capture(minecraft, "guide-security-search-scale-" + guiScale + ".png");
                        for (int percent : new int[] {80, 120}) {
                            ReaderPreferences.save(HandbookScreen.preferencesPath(), percent);
                            for (int chapter : new int[] {0, 7, 9, 10}) {
                                onClient(minecraft, () -> {
                                    ReaderClientChecks.guideFixture(minecraft, chapter); return null;
                                });
                                Thread.sleep(250);
                                capture(minecraft, "guide-chapter-" + chapter + "-scale-" + guiScale + "-text-" + percent + ".png");
                            }
                        }
                    }
                    logger.info("The Emerald Standard expanded handbook content, search and layout checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; });
                    return;
                }
                if (Boolean.getBoolean("the_emerald_standard.clientFundsOnly")) {
                    for (int guiScale : new int[] {2, 4}) {
                        final int scale = guiScale;
                        for (int fundsTab : new int[] {
                                com.chedidandrew.emeraldstandard.minecraft.BankerMenu.TAB_FUND,
                                com.chedidandrew.emeraldstandard.minecraft.BankerMenu.TAB_MARKET,
                                com.chedidandrew.emeraldstandard.minecraft.BankerMenu.TAB_BANKING}) {
                            onClient(minecraft, () -> {
                                minecraft.getWindow().setWindowed(1280, 1000);
                                minecraft.options.guiScale().set(scale); minecraft.resizeGui();
                                minecraft.gui.setScreen(BankerClientChecks.fundsFixture(fundsTab)); return null;
                            });
                            Thread.sleep(500);
                            capture(minecraft, "inventory-funds-tab-" + fundsTab + "-scale-" + scale + ".png");
                        }
                    }
                    logger.info("The Emerald Standard inventory-only funding GUI checks passed");
                    onClient(minecraft, () -> { minecraft.stop(); return null; });
                    return;
                }
                Thread.sleep(1000);
                onClient(minecraft, () -> {
                    try { ReaderClientChecks.verify(minecraft); }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                    logger.info("The Emerald Standard reader navigation and persistence checks passed");
                    return null;
                });
                Thread.sleep(300);
                capture(minecraft, "handbook.png");
                onClient(minecraft, () -> {
                    ReaderClientChecks.press(minecraft.gui.screen(), "A-"); return null;
                });
                Thread.sleep(250);
                capture(minecraft, "handbook-80.png");
                onClient(minecraft, () -> {
                    for (int i = 0; i < 4; i++) ReaderClientChecks.press(minecraft.gui.screen(), "A+");
                    return null;
                });
                Thread.sleep(250);
                capture(minecraft, "handbook-120.png");
                onClient(minecraft, () -> {
                    minecraft.gui.screen().setFocused(null);
                    minecraft.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                            org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0));
                    return null;
                });
                Thread.sleep(250);
                capture(minecraft, "handbook-120-bottom.png");
                // Height >= 960 is needed for Minecraft to honor an actual GUI scale of four.
                onClient(minecraft, () -> { minecraft.getWindow().setWindowed(1280, 1000); return null; });
                Thread.sleep(400);
                for (int guiScale : new int[] {2, 4}) {
                    onClient(minecraft, () -> {
                        minecraft.options.guiScale().set(guiScale); minecraft.resizeGui();
                        if (minecraft.getWindow().getGuiScale() != guiScale)
                            throw new IllegalStateException("Recipe GUI scale was clamped");
                        ReaderClientChecks.craftingFixture(minecraft);
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), 5, 5);
                        return null;
                    });
                    Thread.sleep(350);
                    capture(minecraft, "recipes-scale-" + guiScale + "-frame-a.png");
                    long before = onClient(minecraft, () -> ((HandbookScreen) minecraft.gui.screen()).recipeFrame());
                    Thread.sleep(1050);
                    long after = onClient(minecraft, () -> ((HandbookScreen) minecraft.gui.screen()).recipeFrame());
                    if (after <= before) throw new IllegalStateException("Recipe animation did not advance");
                    capture(minecraft, "recipes-scale-" + guiScale + "-frame-b.png");
                    onClient(minecraft, () -> {
                        int[] point = ((HandbookScreen) minecraft.gui.screen()).firstRecipeIngredientCenter();
                        var window = minecraft.getWindow();
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.handle(),
                                point[0] * window.getScreenWidth() / (double) window.getGuiScaledWidth(),
                                point[1] * window.getScreenHeight() / (double) window.getGuiScaledHeight());
                        return null;
                    });
                    Thread.sleep(250);
                    before = onClient(minecraft, () -> ((HandbookScreen) minecraft.gui.screen()).recipeFrame());
                    Thread.sleep(1000);
                    after = onClient(minecraft, () -> ((HandbookScreen) minecraft.gui.screen()).recipeFrame());
                    if (after != before) throw new IllegalStateException("Hover did not pause recipe animation");
                    capture(minecraft, "recipes-scale-" + guiScale + "-hover.png");
                    onClient(minecraft, () -> {
                        HandbookScreen reader = (HandbookScreen) minecraft.gui.screen();
                        reader.setFocused(null);
                        reader.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0));
                        for (int i = 0; i < reader.recipeTitleLine(1); i++)
                            reader.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0, 0));
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), 5, 5);
                        return null;
                    });
                    Thread.sleep(250);
                    capture(minecraft, "recipes-scale-" + guiScale + "-shapeless.png");
                    onClient(minecraft, () -> {
                        minecraft.gui.screen().setFocused(null);
                        minecraft.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                                org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0));
                        return null;
                    });
                    Thread.sleep(250);
                    capture(minecraft, "recipes-scale-" + guiScale + "-bottom.png");
                }
                logger.info("The Emerald Standard animated recipe render, variant, hover and layout checks passed");
                onClient(minecraft, () -> { minecraft.getWindow().setWindowed(1280, 800); return null; });
                Thread.sleep(300);
                ReaderPreferences.save(HandbookScreen.preferencesPath(), 90);
                onClient(minecraft, () -> { minecraft.gui.setScreen(new EmeraldSettingsScreen(null)); return null; });
                Thread.sleep(700);
                capture(minecraft, "settings.png");
                onClient(minecraft, () -> {
                    try { SettingsClientChecks.verify(minecraft); }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                    SettingsClientChecks.fixture(minecraft);
                    logger.info("The Emerald Standard settings all-page reset and speed editor checks passed");
                    return null;
                });
                Thread.sleep(500);
                capture(minecraft, "settings-speed-reset.png");
                onClient(minecraft, () -> { SettingsClientChecks.warningFixture(minecraft); return null; });
                Thread.sleep(400);
                capture(minecraft,"forced-development-warning.png");
                if ("present".equals(System.getenv("TES_TEST_MODMENU"))) {
                    onClient(minecraft, () -> {
                        try {
                            Class<?> menu = Class.forName("com.terraformersmc.modmenu.ModMenu");
                            if (!Boolean.TRUE.equals(menu.getMethod("hasConfigScreen", String.class)
                                    .invoke(null, "the_emerald_standard")))
                                throw new IllegalStateException("Mod Menu Configure action is unavailable");
                            Object screen = menu.getMethod("getConfigScreen", String.class, Screen.class)
                                    .invoke(null, "the_emerald_standard", null);
                            if (!(screen instanceof EmeraldSettingsScreen)) throw new IllegalStateException("Wrong Mod Menu screen");
                            minecraft.gui.setScreen((Screen) screen);
                            logger.info("The Emerald Standard Mod Menu configuration factory verified");
                        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                        return null;
                    });
                } else {
                    try {
                        Class.forName("com.terraformersmc.modmenu.ModMenu");
                        throw new IllegalStateException("Absent-Mod-Menu smoke unexpectedly contains Mod Menu");
                    } catch (ClassNotFoundException expected) {
                        logger.info("The Emerald Standard optional Mod Menu absence verified");
                    }
                }
                logger.info("The Emerald Standard reader and settings screen smoke checks passed");
                onClient(minecraft, () -> { BankerClientChecks.verifyTextFits(minecraft); return null; });
                for (int guiScale : new int[] {2, 4}) {
                    onClient(minecraft, () -> {
                        minecraft.options.guiScale().set(guiScale);
                        minecraft.resizeGui();
                        minecraft.gui.setScreen(BankerClientChecks.fixture(true, "VILX", false));
                        return null;
                    });
                    Thread.sleep(400);
                    capture(minecraft, "town-expansion-scale-" + guiScale + ".png");
                    for (int fundsTab : new int[] {
                            com.chedidandrew.emeraldstandard.minecraft.BankerMenu.TAB_FUND,
                            com.chedidandrew.emeraldstandard.minecraft.BankerMenu.TAB_MARKET,
                            com.chedidandrew.emeraldstandard.minecraft.BankerMenu.TAB_BANKING}) {
                        onClient(minecraft, () -> {
                            minecraft.gui.setScreen(BankerClientChecks.fundsFixture(fundsTab)); return null;
                        });
                        Thread.sleep(300);
                        capture(minecraft, "inventory-funds-tab-" + fundsTab + "-scale-" + guiScale + ".png");
                    }
                    for (String ticker : new String[] {"VILX", "IRNG", "TREA", "AURM", "BRCK", "FISH", "VENT",
                            "GOLD", "IRON", "COAL", "DIAM", "COPR", "RDST", "LAPS", "NETH"}) {
                        onClient(minecraft, () -> {
                            minecraft.gui.setScreen(BankerClientChecks.fixture(false, ticker, !ticker.equals("IRNG")));
                            return null;
                        });
                        Thread.sleep(300);
                        capture(minecraft, "market-" + ticker.toLowerCase(java.util.Locale.ROOT) + "-scale-" + guiScale + ".png");
                    }
                }
                logger.info("The Emerald Standard dashboard render and text-fit checks passed");
                for(int frame : new int[] {-1, 0, 3}) {
                    onClient(minecraft, () -> {
                        minecraft.options.guiScale().set(2); minecraft.resizeGui();
                        ConstructionVisualChecks.verifyAnimation();
                        minecraft.gui.setScreen(new ConstructionVisualChecks(frame)); return null;
                    });
                    Thread.sleep(400); capture(minecraft,"construction-crew-frame-"+frame+".png");
                }
                logger.info("The Emerald Standard construction crew render and animation checks passed");
                ReaderPreferences.save(HandbookScreen.preferencesPath(), 110);
                onClient(minecraft, () -> { minecraft.stop(); return null; });
            } catch (Exception exception) {
                logger.error("The Emerald Standard reader/config smoke failed", exception);
                minecraft.execute(minecraft::stop);
            }
        }, "emerald-standard-client-smoke");
        smoke.setDaemon(true);
        smoke.start();
    }

    private static <T> T onClient(Minecraft minecraft, Supplier<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        minecraft.execute(() -> {
            try { result.complete(work.get()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }
    private static void capture(Minecraft minecraft, String name) throws Exception {
        Path output = minecraft.gameDirectory.toPath().resolve("screenshots/tes-reader-ci").resolve(name);
        Files.createDirectories(output.getParent());
        CompletableFuture<Void> saved = new CompletableFuture<>();
        minecraft.execute(() -> Screenshot.grab(minecraft.gameDirectory, "tes-reader-ci/" + name,
                minecraft.gameRenderer.mainRenderTarget(), 1, ignored -> saved.complete(null)));
        saved.get(15, TimeUnit.SECONDS);
        if (!Files.isRegularFile(output) || Files.size(output) < 100)
            throw new IllegalStateException("Reader screenshot was not saved: " + name);
    }

    /** Retain real-font coverage for the vanilla/lectern fallback after resource readiness. */
    private static void verifyHandbookPagesFit(Minecraft minecraft) {
        var pages = EmeraldHandbook.content().getPages(false);
        StringBuilder overflow = new StringBuilder();
        for (int index = 0; index < pages.size(); index++) {
            var wrapped = minecraft.font.split(pages.get(index), WRITTEN_BOOK_TEXT_WIDTH);
            int lines = wrapped.size();
            if (lines > WRITTEN_BOOK_VISIBLE_LINES) {
                if (!overflow.isEmpty()) overflow.append(';');
                overflow.append("\npage ").append(index + 1).append(" (")
                        .append(lines).append(" lines): ");
                for (int line = 0; line < wrapped.size(); line++) {
                    if (line > 0) overflow.append(" / ");
                    wrapped.get(line).accept((ignored, style, codePoint) -> {
                        overflow.appendCodePoint(codePoint);
                        return true;
                    });
                }
            }
        }
        if (!overflow.isEmpty()) throw new IllegalStateException(
                "Emerald Handbook pages exceed the written-book limit of "
                        + WRITTEN_BOOK_VISIBLE_LINES + " lines:" + overflow);
    }
}
