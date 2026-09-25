package com.chedidandrew.emeraldstandard.minecraft.mixin;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class VegetationReloadMixin {
    @Inject(method="reloadResources",at=@At("RETURN"))
    private void emerald$vegetationRules(Collection<String> packs,
            CallbackInfoReturnable<CompletableFuture<Void>> result) {
        var server=(MinecraftServer)(Object)this;
        result.getReturnValue().thenRun(() -> server.execute(() ->
                com.chedidandrew.emeraldstandard.minecraft.VegetationCompatibility.resourcesReloaded(server)));
    }
}
