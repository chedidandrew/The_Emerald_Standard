package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Falling construction material may disappear, but must not land outside the site as harvestable blocks. */
@Mixin(FallingBlockEntity.class)
public abstract class ConstructionFallingBlockMixin {
    @Inject(method="fall",at=@At("RETURN"))
    private static void emerald$noFallingExport(Level level,BlockPos pos,BlockState state,
            CallbackInfoReturnable<FallingBlockEntity> ci) {
        if(ConstructionOwnership.owned(level,pos,state)) {
            ci.getReturnValue().disableDrop();
            ci.getReturnValue().dropItem=false;
        }
    }
}
