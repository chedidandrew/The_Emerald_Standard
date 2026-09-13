package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Native inventory contracts reject insertion as well as extraction; never swallow supplied items. */
@Mixin(Container.class)
public interface ConstructionContainerSlotsMixin {
    @Inject(method="canPlaceItem",at=@At("HEAD"),cancellable=true)
    default void emerald$noInsert(int slot,ItemStack stack,CallbackInfoReturnable<Boolean> ci) {
        if((Object)this instanceof BlockEntity be && be.getLevel()!=null
                &&ConstructionOwnership.locked(be.getLevel(),be.getBlockPos())) ci.setReturnValue(false);
    }
    @Inject(method="canTakeItem",at=@At("HEAD"),cancellable=true)
    default void emerald$noExtract(Container destination,int slot,ItemStack stack,CallbackInfoReturnable<Boolean> ci) {
        if((Object)this instanceof BlockEntity be && be.getLevel()!=null
                &&ConstructionOwnership.locked(be.getLevel(),be.getBlockPos())) ci.setReturnValue(false);
    }
}
