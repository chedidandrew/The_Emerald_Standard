package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.client.BankerScreen;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class EmeraldStandardFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(BankerMenus.type(), BankerScreen::new);
    }
}
