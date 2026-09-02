package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.client.BankerScreen;
import com.chedidandrew.emeraldstandard.client.ClientSmokeSupport;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = EmeraldStandardNeoForge.MOD_ID, value = Dist.CLIENT)
public final class EmeraldStandardNeoForgeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private EmeraldStandardNeoForgeClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EmeraldStandardNeoForge.BANKER_MENU.get(), BankerScreen::new);
        ClientSmokeSupport.initialized(LOGGER);
    }
}
