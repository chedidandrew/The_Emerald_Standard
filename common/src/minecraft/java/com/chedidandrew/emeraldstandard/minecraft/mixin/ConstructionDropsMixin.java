package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.ConstructionOwnership;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Covers mining (including silk touch), explosions and support-loss loot dispatch on both loaders. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ConstructionDropsMixin {
    @Inject(method="getDrops",at=@At("HEAD"),cancellable=true)
    private void emerald$noConstructionLoot(LootParams.Builder builder,CallbackInfoReturnable<List<ItemStack>> ci) {
        var origin=builder.getOptionalParameter(LootContextParams.ORIGIN);
        if(origin!=null && ConstructionOwnership.suppressDrops(builder.getLevel(),BlockPos.containing(origin),(BlockState)(Object)this))
            ci.setReturnValue(List.of());
    }
    @Inject(method="spawnAfterBreak",at=@At("HEAD"),cancellable=true)
    private void emerald$noConstructionXp(ServerLevel level,BlockPos pos,ItemStack tool,boolean xp,CallbackInfo ci) {
        if(ConstructionOwnership.owned(level,pos,(BlockState)(Object)this)) ci.cancel();
    }
    @Inject(method="useWithoutItem",at=@At("HEAD"),cancellable=true)
    private void emerald$lockedUse(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit,CallbackInfoReturnable<net.minecraft.world.InteractionResult> ci) {
        if(ConstructionOwnership.locked(level,hit.getBlockPos())) {
            ConstructionOwnership.explain(player); ci.setReturnValue(net.minecraft.world.InteractionResult.FAIL);
        }
    }
    @Inject(method="useItemOn",at=@At("HEAD"),cancellable=true)
    private void emerald$lockedItem(ItemStack stack,net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit,CallbackInfoReturnable<net.minecraft.world.InteractionResult> ci) {
        if(ConstructionOwnership.locked(level,hit.getBlockPos())) {
            ConstructionOwnership.explain(player); ci.setReturnValue(net.minecraft.world.InteractionResult.FAIL);
        }
    }
}
