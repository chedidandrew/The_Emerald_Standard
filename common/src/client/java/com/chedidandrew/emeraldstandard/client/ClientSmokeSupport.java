package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldHandbook;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/** CI-only client bootstrap helper. Normal players never enable the system property. */
public final class ClientSmokeSupport {
    private static final String SMOKE_PROPERTY = "the_emerald_standard.clientSmoke";
    private static final int WRITTEN_BOOK_TEXT_WIDTH = 114;
    private static final int WRITTEN_BOOK_VISIBLE_LINES = 14;

    private ClientSmokeSupport() {
    }

    public static void initialized(Logger logger) {
        logger.info("The Emerald Standard client initialized");
        if (!Boolean.getBoolean(SMOKE_PROPERTY)) {
            return;
        }

        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(8_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                try {
                    verifyHandbookPagesFit(minecraft);
                    logger.info(
                            "Emerald Handbook page layout verified for {} pages",
                            EmeraldHandbook.PAGE_COUNT);
                } finally {
                    minecraft.stop();
                }
            });
        }, "emerald-standard-client-smoke-stop");
        shutdown.setDaemon(true);
        shutdown.start();
    }

    /**
     * Mirrors {@code BookViewScreen}: written books wrap at 114 pixels and render only the first
     * fourteen lines. Keeping this in the real client smoke path catches translated handbook text
     * that would otherwise load successfully but leave its navigation or final instructions clipped.
     */
    private static void verifyHandbookPagesFit(Minecraft minecraft) {
        var pages = EmeraldHandbook.content().getPages(false);
        StringBuilder overflow = new StringBuilder();
        for (int index = 0; index < pages.size(); index++) {
            var wrapped = minecraft.font.split(pages.get(index), WRITTEN_BOOK_TEXT_WIDTH);
            int lines = wrapped.size();
            if (lines > WRITTEN_BOOK_VISIBLE_LINES) {
                if (!overflow.isEmpty()) {
                    overflow.append(';');
                }
                overflow.append("\npage ").append(index + 1).append(" (")
                        .append(lines).append(" lines): ");
                for (int line = 0; line < wrapped.size(); line++) {
                    if (line > 0) {
                        overflow.append(" / ");
                    }
                    wrapped.get(line).accept((ignored, style, codePoint) -> {
                        overflow.appendCodePoint(codePoint);
                        return true;
                    });
                }
            }
        }
        if (!overflow.isEmpty()) {
            throw new IllegalStateException(
                    "Emerald Handbook pages exceed the written-book limit of "
                            + WRITTEN_BOOK_VISIBLE_LINES + " lines:" + overflow);
        }
    }
}
