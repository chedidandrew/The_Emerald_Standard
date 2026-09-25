package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.client.ExploredTerrainRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ClientTerrainTickMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void emeraldStandard$terrainTick(CallbackInfo info) {ExploredTerrainRuntime.tick();}
    @Inject(method="clearClientLevel",at=@At("TAIL"))
    private void emeraldStandard$terrainDisconnect(CallbackInfo info) {ExploredTerrainRuntime.tick();}
}
