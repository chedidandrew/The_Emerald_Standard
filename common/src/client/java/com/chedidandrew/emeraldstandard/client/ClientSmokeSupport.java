package com.chedidandrew.emeraldstandard.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/** CI-only client bootstrap helper. Normal players never enable the system property. */
public final class ClientSmokeSupport {
    private static final String SMOKE_PROPERTY = "the_emerald_standard.clientSmoke";

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
            minecraft.execute(minecraft::stop);
        }, "emerald-standard-client-smoke-stop");
        shutdown.setDaemon(true);
        shutdown.start();
    }
}
