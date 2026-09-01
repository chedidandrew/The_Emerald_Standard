package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.client.BankerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = EmeraldStandardNeoForge.MOD_ID, value = Dist.CLIENT)
public final class EmeraldStandardNeoForgeClient {
    private EmeraldStandardNeoForgeClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EmeraldStandardNeoForge.BANKER_MENU.get(), BankerScreen::new);
    }
}
