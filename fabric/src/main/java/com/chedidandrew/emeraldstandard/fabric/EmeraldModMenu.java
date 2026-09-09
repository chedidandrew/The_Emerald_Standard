package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.client.EmeraldSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional Mod Menu entry point. No common or server code references this class. */
public final class EmeraldModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<EmeraldSettingsScreen> getModConfigScreenFactory() {
        return EmeraldSettingsScreen::new;
    }
}
