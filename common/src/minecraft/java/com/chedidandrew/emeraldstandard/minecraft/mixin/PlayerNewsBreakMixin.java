package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.minecraft.NewsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerNewsBreakMixin {
    @Shadow @Final protected ServerPlayer player;
    @Unique private NewsRuntime.BreakEvidence emerald$newsBreak;
    @Inject(method="destroyBlock",at=@At("HEAD"))
    private void emerald$before(BlockPos pos,CallbackInfoReturnable<Boolean> ci) { emerald$newsBreak=NewsRuntime.beforeBreak(player,pos); }
    @Inject(method="destroyBlock",at=@At("RETURN"))
    private void emerald$after(BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
        var observation=emerald$newsBreak;emerald$newsBreak=null;NewsRuntime.afterBreak(player,observation,ci.getReturnValue());
    }
}
