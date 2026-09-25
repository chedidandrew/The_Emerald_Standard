package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin({BaseContainerBlockEntity.class,RandomizableContainerBlockEntity.class})
public abstract class ConstructionContainerMixin {
    @Inject(method="canOpen",at=@At("HEAD"),cancellable=true)
    private void emerald$locked(Player player,CallbackInfoReturnable<Boolean> ci) {
        BlockEntity be=(BlockEntity)(Object)this;
        if(be.getLevel()!=null&&ConstructionOwnership.locked(be.getLevel(),be.getBlockPos())) {
            ConstructionOwnership.explain(player); ci.setReturnValue(false);
        }
    }
}
