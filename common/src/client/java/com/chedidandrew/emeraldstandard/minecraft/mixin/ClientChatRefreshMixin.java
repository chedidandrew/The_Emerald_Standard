package com.chedidandrew.emeraldstandard.minecraft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2 integrated-server teardown calls this client UI operation from its server thread. */
@Mixin(LocalPlayer.class)
public abstract class ClientChatRefreshMixin {
    @Inject(method="refreshChatAbilities", at=@At("HEAD"), cancellable=true)
    private void emeraldStandard$clientThreadChatRefresh(CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) return;
        LocalPlayer player = (LocalPlayer)(Object)this;
        com.chedidandrew.emeraldstandard.client.ChatRefreshClientChecks.deferred();
        // Never wait for the render thread: it may be joining the server during disconnect.
        client.execute(() -> {
            // A delayed old-world callback must not refresh the next world's chat filter.
            if (client.player == player) {
                com.chedidandrew.emeraldstandard.client.ChatRefreshClientChecks.dispatched(client);
                player.refreshChatAbilities();
            }
        });
        info.cancel();
    }
}
