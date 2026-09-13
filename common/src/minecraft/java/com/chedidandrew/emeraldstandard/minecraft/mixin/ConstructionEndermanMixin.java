package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Do not allow mobs to carry supplied terrain out of the no-drop site. */
@Mixin(targets="net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class ConstructionEndermanMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState emerald$noCarry(Level level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        return ConstructionOwnership.owned(level,pos,state)?Blocks.AIR.defaultBlockState():state;
    }
}
