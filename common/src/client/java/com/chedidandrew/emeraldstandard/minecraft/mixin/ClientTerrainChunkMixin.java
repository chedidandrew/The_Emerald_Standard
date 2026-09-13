package com.chedidandrew.emeraldstandard.minecraft.mixin;
import com.chedidandrew.emeraldstandard.client.ExploredTerrainRuntime;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ClientChunkCache.class)
public abstract class ClientTerrainChunkMixin {
    @Shadow @Final private ClientLevel level;
    @Inject(method="replaceWithPacketData",at=@At("RETURN"))
    private void emeraldStandard$observed(CallbackInfoReturnable<LevelChunk> result) {
        ExploredTerrainRuntime.observed(level,result.getReturnValue());
    }
    @Inject(method="drop",at=@At("HEAD"))
    private void emeraldStandard$departing(ChunkPos pos,CallbackInfo info) {
        ExploredTerrainRuntime.departing(level,pos.x(),pos.z());
    }
}
