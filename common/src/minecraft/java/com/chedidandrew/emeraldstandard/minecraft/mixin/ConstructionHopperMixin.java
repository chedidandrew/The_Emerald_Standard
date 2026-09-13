package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(HopperBlockEntity.class)
public abstract class ConstructionHopperMixin {
    @Inject(method="getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDD)Lnet/minecraft/world/Container;",at=@At("HEAD"),cancellable=true)
    private static void emerald$lockedAutomation(Level level,BlockPos pos,BlockState state,double x,double y,double z,
            CallbackInfoReturnable<Container> ci) {
        if(ConstructionOwnership.locked(level,pos)) ci.setReturnValue(null);
    }
}
