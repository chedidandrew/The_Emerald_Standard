package com.chedidandrew.emeraldstandard.neoforge;

import com.chedidandrew.emeraldstandard.client.BankerScreen;
import com.chedidandrew.emeraldstandard.client.HandbookScreen;
import com.chedidandrew.emeraldstandard.client.EmeraldSettingsScreen;
import com.chedidandrew.emeraldstandard.minecraft.HandbookReaderItem;
import com.chedidandrew.emeraldstandard.client.ClientSmokeSupport;
import com.chedidandrew.emeraldstandard.client.GalleryCaptureSupport;
import com.chedidandrew.emeraldstandard.client.VillageComparisonCaptureSupport;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = EmeraldStandardNeoForge.MOD_ID, value = Dist.CLIENT)
public final class EmeraldStandardNeoForgeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private EmeraldStandardNeoForgeClient() { }
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        ModList.get().getModContainerById(EmeraldStandardNeoForge.MOD_ID).orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class,
                        (container, parent) -> new EmeraldSettingsScreen(parent));
        event.register(EmeraldStandardNeoForge.BANKER_MENU.get(), BankerScreen::new);
        HandbookReaderItem.registerReader(() -> Minecraft.getInstance().gui.setScreen(new HandbookScreen(null)));
        ClientSmokeSupport.initialized(LOGGER, () -> {
            var container = ModList.get().getModContainerById(EmeraldStandardNeoForge.MOD_ID).orElseThrow();
            var factory = container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
            if (!(factory.createScreen(container, null) instanceof EmeraldSettingsScreen))
                throw new IllegalStateException("Wrong NeoForge config screen");
            LOGGER.info("The Emerald Standard NeoForge configuration factory verified");
        });
        GalleryCaptureSupport.initialized(LOGGER);
        VillageComparisonCaptureSupport.initialized(LOGGER);
    }
}
