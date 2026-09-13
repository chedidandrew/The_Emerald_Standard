package com.chedidandrew.emeraldstandard.minecraft.mixin;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Vanilla's obfuscated seed separates local derivative map caches, not game state. */
@Mixin(BiomeManager.class)
public interface BiomeSeedAccess {
    @Accessor("biomeZoomSeed") long emeraldStandard$biomeZoomSeed();
}
