package com.chedidandrew.emeraldstandard.minecraft.mixin;

import com.chedidandrew.emeraldstandard.minecraft.DevelopmentLandProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe successful player placement on both loaders; verify after claim-event cancellation. */
@Mixin(BlockItem.class)
public abstract class PlayerBlockPlacementMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void emerald$placed(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && context.getPlayer() != null && context.getLevel() instanceof ServerLevel level)
            DevelopmentLandProtection.placed(level,context.getClickedPos(),state);
    }
}
