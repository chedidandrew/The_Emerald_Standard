package com.chedidandrew.emeraldstandard.minecraft.mixin;

import com.chedidandrew.emeraldstandard.minecraft.SurveyChunkRevision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class SurveyChunkRevisionMixin implements SurveyChunkRevision {
    @Unique private long emerald$surveyRevision;
    public long emeraldSurveyRevision() { return emerald$surveyRevision; }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void emerald$changed(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> result) {
        if (result.getReturnValue() != null && result.getReturnValue() != state) {
            emerald$surveyRevision++;
            // Level.sendBlockUpdated only invalidates this cache in BLOCK_TICKING chunks.
            // TES can build in loaded chunks outside simulation distance. Their previous
            // air/wall classification must not survive until villagers arrive there.
            if (((LevelChunk) (Object) this).getLevel() instanceof net.minecraft.server.level.ServerLevel server)
                server.getPathTypeCache().invalidate(pos);
        }
    }
}
