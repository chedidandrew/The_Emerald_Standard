package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(PistonBaseBlock.class)
public abstract class ConstructionPistonMixin {
    @Inject(method="isPushable",at=@At("HEAD"),cancellable=true)
    private static void emerald$noExport(BlockState state,Level level,BlockPos pos,Direction direction,
            boolean destroy,Direction pistonDirection,CallbackInfoReturnable<Boolean> ci) {
        if(ConstructionOwnership.reserved(level,pos)
                ||ConstructionOwnership.reserved(level,pos.relative(direction))) ci.setReturnValue(false);
    }
}
