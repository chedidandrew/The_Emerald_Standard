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
                    verifyHandbookPagesFit(minecraft);
                    if (!(BuiltInRegistries.ITEM.getValue(EmeraldHandbook.HANDBOOK_ID) instanceof HandbookReaderItem))
                        throw new IllegalStateException("Custom handbook item was not registered");
                    for (var chapter : HandbookChapters.ALL) for (String section : chapter.sections()) {
                        String key = HandbookChapters.PREFIX + section + ".title";
                        if (HandbookChapters.title(section).getString().equals(key))
                            throw new IllegalStateException("Missing handbook section: " + section);
                    }
                    logger.info("Emerald Handbook page layout verified for {} pages", EmeraldHandbook.PAGE_COUNT);
                    minecraft.getWindow().setWindowed(1280, 800);
                    minecraft.gui.setScreen(new HandbookScreen(null));
                    return null;
                });
                Thread.sleep(1000);
                capture(minecraft, "handbook.png");
                onClient(minecraft, () -> { minecraft.gui.setScreen(new EmeraldSettingsScreen(null)); return null; });
                Thread.sleep(700);
                capture(minecraft, "settings.png");
                if ("present".equals(System.getenv("TES_TEST_MODMENU"))) {
                    onClient(minecraft, () -> {
                        try {
                            Class<?> entry = Class.forName("com.chedidandrew.emeraldstandard.fabric.EmeraldModMenu");
                            Object factory = entry.getMethod("getModConfigScreenFactory").invoke(entry.getConstructor().newInstance());
                            Object screen = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory")
                                    .getMethod("create", Screen.class).invoke(factory, new Object[] {null});
                            if (!(screen instanceof EmeraldSettingsScreen)) throw new IllegalStateException("Wrong Mod Menu screen");
                            minecraft.gui.setScreen((Screen) screen);
                            logger.info("The Emerald Standard Mod Menu configuration factory verified");
                        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                        return null;
                    });
                }
                logger.info("The Emerald Standard reader and settings screen smoke checks passed");
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
