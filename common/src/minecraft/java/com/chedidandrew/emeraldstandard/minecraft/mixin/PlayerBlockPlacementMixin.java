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
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void emerald$reserved(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> result) {
        if(context.getPlayer()==null) return;
        var level=context.getLevel(); var pos=context.getClickedPos();
        boolean blocked=com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership.reserved(level,pos);
        if(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
            blocked|=com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership.reserved(level,pos.above());
        if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
            blocked|=com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership.reserved(level,
                    pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING)));
        if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
            for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)
                blocked|=com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership.locked(level,pos.relative(d));
        if(blocked) {
            com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership.explain(context.getPlayer());
            result.setReturnValue(false);
        }
    }
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void emerald$placed(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && context.getPlayer() != null && context.getLevel() instanceof ServerLevel level) {
            DevelopmentLandProtection.placed(level,context.getClickedPos(),state);
            com.chedidandrew.emeraldstandard.minecraft.NewsRuntime.placed(level,context.getClickedPos(),state,context.getPlayer());
        }
    }
}
