package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.client.BankerScreen;
import com.chedidandrew.emeraldstandard.client.ClientSmokeSupport;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmeraldStandardFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldStandardFabric.MOD_ID);
    @Override
    public void onInitializeClient() {
        MenuScreens.register(BankerMenus.type(), BankerScreen::new);
        ClientSmokeSupport.initialized(LOGGER);
    }
}
